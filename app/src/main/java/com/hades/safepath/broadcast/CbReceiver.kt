package com.hades.safepath.broadcast

/**
 * Placeholder retained for package completeness.
 *
 * Direct CellBroadcastReceiver registration is a protected system API from
 * Android 9+ (API 28) and cannot be used by Play Store apps. All CB signal
 * injection for this prototype is handled by [CellBroadcastSimulator] via
 * [SafePathBroadcastReceiver] and LocalBroadcastManager.
 */
@Suppress("unused")
object CbReceiver
