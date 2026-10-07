package com.hades.safepath.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hades.safepath.data.model.DisasterEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Receives local CB simulation broadcasts and exposes decoded [DisasterEvent]s
 * via [eventFlow] for observation by ViewModels.
 *
 * NOTE: Direct CellBroadcastReceiver registration is a protected system API
 * from Android 9+ (API 28) and is not used here. All signals are injected
 * via [CellBroadcastSimulator] for prototype purposes.
 */
class SafePathBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CB_RECEIVED = "com.hades.safepath.ACTION_CB_RECEIVED"
        const val EXTRA_SIGNAL_CODE = "signal_code"

        private const val TAG = "SafePathBroadcastRcvr"

        private val _eventFlow = MutableStateFlow<DisasterEvent?>(null)

        /** Observe this from ViewModels to react to incoming CB signals. */
        val eventFlow: StateFlow<DisasterEvent?> = _eventFlow.asStateFlow()

        /**
         * Resets the event flow to null.
         * For use in unit tests only — do not call in production code.
         */
        @androidx.annotation.VisibleForTesting
        fun resetEventFlowForTesting() {
            _eventFlow.value = null
        }

        /**
         * Posts a [DisasterEvent] directly to the flow.
         * For use in unit tests only — production code uses the broadcast path.
         */
        @androidx.annotation.VisibleForTesting
        fun postEventForTesting(event: DisasterEvent?) {
            _eventFlow.value = event
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CB_RECEIVED) return

        val rawCode = intent.getStringExtra(EXTRA_SIGNAL_CODE)
        Log.d(TAG, "Received broadcast with signal_code='$rawCode'")

        val event = rawCode?.let { SignalDecoder.decode(it) }

        if (event != null) {
            Log.d(TAG, "Posting event to flow: $event")
            _eventFlow.value = event
        } else {
            Log.d(TAG, "Signal decode returned null — ignoring broadcast")
        }
    }
}
