package com.hades.safepath.map.mode

/**
 * User / system selection for the dual-mode map.
 *
 * AUTO — use online when the network is usable, otherwise offline archives.
 * ONLINE — force live tiles + OSRM (fails gracefully if offline).
 * OFFLINE — force bundled Sri Lanka MBTiles + local Dijkstra (never hits the network).
 */
enum class MapMode {
    AUTO,
    ONLINE,
    OFFLINE
}

enum class EffectiveMapMode {
    ONLINE,
    OFFLINE
}

enum class NetworkAvailability {
    ONLINE,
    OFFLINE,
    UNKNOWN
}
