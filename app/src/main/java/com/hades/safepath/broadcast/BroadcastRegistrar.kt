package com.hades.safepath.broadcast

import android.content.Context
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Manages registration of [SafePathBroadcastReceiver] with [LocalBroadcastManager].
 *
 * Call [register] in MainActivity.onCreate() and [unregister] in MainActivity.onDestroy().
 *
 * Example:
 * ```kotlin
 * private val broadcastRegistrar = BroadcastRegistrar(this)
 *
 * override fun onCreate(...) {
 *     broadcastRegistrar.register()
 * }
 *
 * override fun onDestroy() {
 *     broadcastRegistrar.unregister()
 *     super.onDestroy()
 * }
 * ```
 */
class BroadcastRegistrar(private val context: Context) {

    private val receiver = SafePathBroadcastReceiver()
    private val localBroadcastManager = LocalBroadcastManager.getInstance(context)

    fun register() {
        val filter = IntentFilter(SafePathBroadcastReceiver.ACTION_CB_RECEIVED)
        localBroadcastManager.registerReceiver(receiver, filter)
    }

    fun unregister() {
        localBroadcastManager.unregisterReceiver(receiver)
    }
}
