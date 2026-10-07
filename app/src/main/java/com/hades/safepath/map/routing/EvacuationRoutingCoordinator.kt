package com.hades.safepath.map.routing

import android.util.Log
import com.hades.safepath.data.entity.Shelter
import com.hades.safepath.data.model.DisasterType
import com.hades.safepath.data.model.RouteNode
import com.hades.safepath.map.mode.EffectiveMapMode
import com.hades.safepath.map.mode.MapModeController
import com.hades.safepath.routing.RoutingEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chooses online OSRM vs offline Dijkstra. Never launches an external maps app.
 */
@Singleton
class EvacuationRoutingCoordinator @Inject constructor(
    private val mapModeController: MapModeController,
    private val onlineOsrmRouter: OnlineOsrmRouter,
    private val routingEngine: RoutingEngine
) {

    suspend fun routeToShelter(
        originLat: Double,
        originLon: Double,
        shelter: Shelter
    ): EvacuationRouteResult {
        val mode = mapModeController.effective.value
        Log.i(TAG, "[ROUTE] Origin=$originLat,$originLon dest=${shelter.name}")
        Log.i(TAG, "[ROUTE] Effective mode=$mode")

        if (mode == EffectiveMapMode.ONLINE) {
            val online = onlineOsrmRouter.route(originLat, originLon, shelter.lat, shelter.lon)
            if (online != null) return online
            Log.w(TAG, "[ROUTE] Online failed — falling back to offline Dijkstra")
        }

        if (!routingEngine.isInitialised()) {
            routingEngine.initialise()
        }
        val nodes: List<RouteNode> = routingEngine.calculateRouteToShelter(
            originLat, originLon, shelter
        )
        if (nodes.size >= 2) {
            val dist = pathLengthMeters(nodes)
            return EvacuationRouteResult(
                points = nodes.map { EvacuationRouteResult.LatLon(it.lat, it.lon) },
                distanceMeters = dist,
                durationSeconds = estimateWalkSeconds(dist),
                providerLabel = "Offline · SafePath roads",
                isDirectFallback = false
            )
        }

        // Explicit direct fallback — never pretend it is road navigation.
        Log.w(TAG, "[ROUTE] Offline road route unavailable — direct fallback")
        return EvacuationRouteResult(
            points = listOf(
                EvacuationRouteResult.LatLon(originLat, originLon),
                EvacuationRouteResult.LatLon(shelter.lat, shelter.lon)
            ),
            distanceMeters = haversine(originLat, originLon, shelter.lat, shelter.lon),
            durationSeconds = null,
            providerLabel = "Direct bearing (not a road route)",
            isDirectFallback = true,
            message = "Offline route unavailable for this area."
        )
    }

    suspend fun routeToNearest(
        originLat: Double,
        originLon: Double
    ): Pair<Shelter, EvacuationRouteResult> {
        if (!routingEngine.isInitialised()) routingEngine.initialise()
        val shelter = routingEngine.findNearestShelter(originLat, originLon)
        return shelter to routeToShelter(originLat, originLon, shelter)
    }

    /** Kept for DecisionEngine compatibility — FLOOD-aware offline graph. */
    suspend fun offlineEvacuation(
        originLat: Double,
        originLon: Double,
        disasterType: DisasterType
    ): List<RouteNode> {
        if (!routingEngine.isInitialised()) routingEngine.initialise()
        return routingEngine.calculateEvacuationRoute(originLat, originLon, disasterType)
    }

    private fun pathLengthMeters(nodes: List<RouteNode>): Double {
        var sum = 0.0
        for (i in 0 until nodes.size - 1) {
            sum += haversine(nodes[i].lat, nodes[i].lon, nodes[i + 1].lat, nodes[i + 1].lon)
        }
        return sum
    }

    private fun estimateWalkSeconds(meters: Double): Double =
        meters / 1.2 // ~4.3 km/h walking

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2) +
            Math.cos(p1) * Math.cos(p2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    companion object {
        private const val TAG = "EvacRoutingCoordinator"
    }
}
