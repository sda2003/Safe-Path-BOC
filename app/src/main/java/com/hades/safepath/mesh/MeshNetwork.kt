package com.hades.safepath.mesh

import android.util.Log
import com.google.gson.Gson
import com.hades.safepath.p2p.P2pManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forwards ESP32 / hazard-node alerts to nearby phones via Nearby Connections.
 */
@Singleton
class MeshNetwork @Inject constructor(
    private val p2pManager: P2pManager,
    private val gson: Gson
) {
    data class NodeAlertPayload(
        val type: String = "HAZARD_NODE",
        val node: String,
        val temp: Double,
        val hum: Double,
        val risk: Int,
        val lat: Double,
        val lon: Double,
        val ts: Long = System.currentTimeMillis()
    )

    /**
     * Broadcast a compact JSON alert to the local mesh when risk > 0.
     */
    fun forwardAlert(
        nodeId: String,
        temp: Double,
        humidity: Double,
        risk: Int,
        lat: Double,
        lon: Double
    ) {
        if (risk <= 0) return
        val payload = NodeAlertPayload(
            node = nodeId,
            temp = temp,
            hum = humidity,
            risk = risk,
            lat = lat,
            lon = lon
        )
        val json = gson.toJson(payload)
        Log.i(TAG, "forwardAlert → mesh: $json")
        // Reuse mesh note channel as a typed JSON blob peers can display
        p2pManager.broadcastRawJson(json)
    }

    companion object {
        private const val TAG = "MeshNetwork"
    }
}
