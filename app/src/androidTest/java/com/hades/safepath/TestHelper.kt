package com.hades.safepath

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import com.hades.safepath.broadcast.CellBroadcastSimulator
import com.hades.safepath.broadcast.SafePathBroadcastReceiver

/**
 * Shared helpers for SafePath instrumented tests.
 */
object TestHelper {

    /**
     * Injects a CB signal and waits until [SafePathBroadcastReceiver.eventFlow]
     * becomes non-null, or until [timeoutMs] elapses.
     *
     * @param signalCode  One of the 9 valid SafePath signal codes (111–133).
     * @param context     Application context.
     * @param timeoutMs   Maximum wait time in milliseconds (default 5 000).
     * @return true if the event was received within the timeout, false otherwise.
     */
    fun injectSignalAndWait(
        signalCode: Int,
        context: Context,
        timeoutMs: Long = 5_000L
    ): Boolean {
        SafePathBroadcastReceiver.resetEventFlowForTesting()

        val simulator = CellBroadcastSimulator(context)
        simulator.inject(signalCode)
        simulator.release()

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (SafePathBroadcastReceiver.eventFlow.value != null) return true
            Thread.sleep(100)
        }
        return false
    }

    /**
     * Polls until an Activity of [activityClass] is in the foreground,
     * or until [timeoutMs] elapses.
     *
     * Uses [ActivityManager.getRunningTasks] — acceptable for test code.
     *
     * @return true if the activity appeared within the timeout.
     */
    @Suppress("DEPRECATION")
    fun waitForActivity(
        activityClass: Class<out Activity>,
        context: Context,
        timeoutMs: Long = 5_000L
    ): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val tasks = am.getRunningTasks(1)
            val topActivity = tasks?.firstOrNull()?.topActivity
            if (topActivity?.className == activityClass.name) return true
            Thread.sleep(200)
        }
        return false
    }
}
