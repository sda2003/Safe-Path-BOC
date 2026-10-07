package com.hades.safepath.network

/**
 * Represents the active communication channel for SafePath.
 *
 * Priority order (highest to lowest):
 *   CELL_BROADCAST → MOBILE_DATA → PEER_TO_PEER → NONE
 */
enum class Channel {
    /** Cell Broadcast signal received within the last 60 seconds. */
    CELL_BROADCAST,

    /** Internet connectivity available via mobile data or Wi-Fi. */
    MOBILE_DATA,

    /** Bluetooth / Wi-Fi Direct mesh notes via Nearby Connections. */
    PEER_TO_PEER,

    /** No connectivity channel available. App operates fully offline. */
    NONE
}
