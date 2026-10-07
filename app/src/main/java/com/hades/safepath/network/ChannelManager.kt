package com.hades.safepath.network

import android.content.Context
import android.util.Log
import com.hades.safepath.broadcast.SafePathBroadcastReceiver

/**
 * Determines the active communication channel available to SafePath.
 *
 * Channel priority: CELL_BROADCAST > MOBILE_DATA > PEER_TO_PEER > NONE
 */
class ChannelManager(private val context: Context) {

    private val TAG = "ChannelManager"

    private val connectivityMonitor = ConnectivityMonitor(context)

    /** Maximum age of a CB signal to be considered "active" (60 seconds). */
    private val CB_ACTIVE_WINDOW_MS = 60_000L

    /**
     * Returns the highest-priority active channel.
     *
     * - [Channel.CELL_BROADCAST] if a CB signal was received within the last 60 seconds.
     * - [Channel.MOBILE_DATA] if internet connectivity is currently available.
     * - [Channel.PEER_TO_PEER] when offline (mesh notes may still hop nearby).
     */
    fun getActiveChannel(): Channel {
        val lastEvent = SafePathBroadcastReceiver.eventFlow.value
        if (lastEvent != null) {
            val ageMs = System.currentTimeMillis() - lastEvent.receivedAt
            if (ageMs <= CB_ACTIVE_WINDOW_MS) {
                Log.d(TAG, "Active channel: CELL_BROADCAST (signal age ${ageMs}ms)")
                return Channel.CELL_BROADCAST
            }
        }

        if (connectivityMonitor.connectivityState.value) {
            Log.d(TAG, "Active channel: MOBILE_DATA")
            return Channel.MOBILE_DATA
        }

        Log.d(TAG, "Active channel: PEER_TO_PEER")
        return Channel.PEER_TO_PEER
    }

    /** Releases the [ConnectivityMonitor] callback. Call from owning component's onDestroy(). */
    fun release() {
        connectivityMonitor.release()
    }
}
