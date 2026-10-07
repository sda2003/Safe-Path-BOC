package com.hades.safepath.map.offline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import org.osmdroid.tileprovider.IRegisterReceiver

/**
 * API-34-safe [IRegisterReceiver]. OsmDroid's [org.osmdroid.tileprovider.util.SimpleRegisterReceiver]
 * calls [Context.registerReceiver] without [ContextCompat.RECEIVER_NOT_EXPORTED], which crashes
 * on Android 14+ (targetSdk 34) when the map opens.
 */
class SafePathRegisterReceiver(
    private val context: Context
) : IRegisterReceiver {

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
        if (receiver == null || filter == null) return null
        return ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun unregisterReceiver(receiver: BroadcastReceiver?) {
        if (receiver == null) return
        runCatching {
            context.applicationContext.unregisterReceiver(receiver)
        }
    }

    override fun destroy() {
        // no-op — receivers are unregistered by OsmDroid modules
    }
}
