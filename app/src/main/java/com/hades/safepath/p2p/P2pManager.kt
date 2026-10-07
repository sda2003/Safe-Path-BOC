package com.hades.safepath.p2p

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.gson.Gson
import com.hades.safepath.mesh.MeshNotePayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nearby Connections mesh transport for short disaster notes.
 * Uses P2P_CLUSTER so phones can form a local mesh without internet.
 */
@Singleton
class P2pManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val connections: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val connectedEndpoints = ConcurrentHashMap.newKeySet<String>()
    private val connectingEndpoints = ConcurrentHashMap.newKeySet<String>()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    @Volatile
    private var onNoteReceived: ((MeshNotePayload) -> Unit)? = null

    private val localEndpointName: String =
        "SafePath-${UUID.randomUUID().toString().take(8)}"

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            runCatching {
                val json = String(bytes, StandardCharsets.UTF_8)
                val note = gson.fromJson(json, MeshNotePayload::class.java)
                if (note != null && note.noteId.isNotBlank() && note.text.isNotBlank()) {
                    onNoteReceived?.invoke(note)
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to parse mesh payload", e)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // no-op for BYTES payloads
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connections.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            connectingEndpoints.remove(endpointId)
            when (resolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpoints.add(endpointId)
                    _peerCount.value = connectedEndpoints.size
                    Log.d(TAG, "Connected to $endpointId (peers=${connectedEndpoints.size})")
                }
                else -> {
                    Log.w(TAG, "Connection failed: ${resolution.status}")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            connectingEndpoints.remove(endpointId)
            _peerCount.value = connectedEndpoints.size
            Log.d(TAG, "Disconnected $endpointId (peers=${connectedEndpoints.size})")
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (connectedEndpoints.contains(endpointId) || connectingEndpoints.contains(endpointId)) {
                return
            }
            connectingEndpoints.add(endpointId)
            connections.requestConnection(localEndpointName, endpointId, connectionLifecycleCallback)
                .addOnFailureListener {
                    connectingEndpoints.remove(endpointId)
                    Log.w(TAG, "requestConnection failed: ${it.message}")
                }
        }

        override fun onEndpointLost(endpointId: String) {
            // Peer left discovery range; connection callback handles disconnect.
        }
    }

    fun setOnNoteReceived(listener: ((MeshNotePayload) -> Unit)?) {
        onNoteReceived = listener
    }

    fun start() {
        if (_isActive.value) return
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connections.startAdvertising(
            localEndpointName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started")
        }.addOnFailureListener {
            Log.w(TAG, "Advertising failed: ${it.message}")
        }

        connections.startDiscovery(
            SERVICE_ID,
            discoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
            _isActive.value = true
        }.addOnFailureListener {
            Log.w(TAG, "Discovery failed: ${it.message}")
        }
    }

    fun stop() {
        connections.stopAllEndpoints()
        connections.stopAdvertising()
        connections.stopDiscovery()
        connectedEndpoints.clear()
        connectingEndpoints.clear()
        _peerCount.value = 0
        _isActive.value = false
    }

    fun broadcast(note: MeshNotePayload) {
        if (connectedEndpoints.isEmpty()) return
        val json = gson.toJson(note)
        if (json.length > MAX_PAYLOAD_BYTES) {
            Log.w(TAG, "Payload too large (${json.length} bytes), skipping broadcast")
            return
        }
        val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
        for (endpointId in connectedEndpoints) {
            connections.sendPayload(endpointId, payload)
                .addOnFailureListener {
                    Log.w(TAG, "sendPayload to $endpointId failed: ${it.message}")
                }
        }
    }

    /** Broadcast an arbitrary JSON string (hazard-node alerts, etc.). */
    fun broadcastRawJson(json: String) {
        if (connectedEndpoints.isEmpty()) {
            Log.d(TAG, "No peers — alert queued locally only: ${json.take(80)}")
            return
        }
        if (json.length > MAX_PAYLOAD_BYTES) {
            Log.w(TAG, "Raw payload too large (${json.length}), skipping")
            return
        }
        val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
        for (endpointId in connectedEndpoints) {
            connections.sendPayload(endpointId, payload)
        }
    }

    companion object {
        private const val TAG = "P2pManager"
        const val SERVICE_ID = "com.hades.safepath.MESH"
        const val MAX_PAYLOAD_BYTES = 900
    }
}
