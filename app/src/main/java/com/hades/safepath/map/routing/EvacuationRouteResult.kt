package com.hades.safepath.map.routing

/**
 * Road-following route produced by online OSRM or offline Dijkstra.
 * Never a bare two-point straight line unless [isDirectFallback] is true.
 */
data class EvacuationRouteResult(
    val points: List<LatLon>,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val providerLabel: String,
    val isDirectFallback: Boolean = false,
    val message: String? = null
) {
    data class LatLon(val lat: Double, val lon: Double)

    val nodeCount: Int get() = points.size
}
