package com.hades.safepath.broadcast

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Injects synthetic CB signals for testing and demonstration.
 *
 * Usage:
 *   val simulator = CellBroadcastSimulator(context)
 *   simulator.inject(SignalCodes.FLOOD_IMMINENT)
 *   // ... later ...
 *   simulator.release()
 *
 * NOTE: This is the sole signal injection mechanism for the prototype.
 * Real CB reception requires OEM pre-installation or telecom operator
 * integration and is out of scope (Android API 28+ restriction).
 */
class CellBroadcastSimulator(private val context: Context) {

    private val TAG = "CellBroadcastSimulator"
    private val receiver = SafePathBroadcastReceiver()
    private val localBroadcastManager = LocalBroadcastManager.getInstance(context)

    init {
        val filter = IntentFilter(SafePathBroadcastReceiver.ACTION_CB_RECEIVED)
        localBroadcastManager.registerReceiver(receiver, filter)
        Log.d(TAG, "Receiver registered")
    }

    /**
     * Sends a local broadcast with the given [signalCode].
     * The registered [SafePathBroadcastReceiver] will decode and emit the event.
     */
    fun inject(signalCode: Int) {
        Log.d(TAG, "Injecting signal code: $signalCode")
        val intent = Intent(SafePathBroadcastReceiver.ACTION_CB_RECEIVED).apply {
            putExtra(SafePathBroadcastReceiver.EXTRA_SIGNAL_CODE, signalCode.toString())
        }
        localBroadcastManager.sendBroadcast(intent)
    }

    /** Unregisters the receiver. Call from the owning component's onDestroy(). */
    fun release() {
        localBroadcastManager.unregisterReceiver(receiver)
        Log.d(TAG, "Receiver unregistered")
    }
}
