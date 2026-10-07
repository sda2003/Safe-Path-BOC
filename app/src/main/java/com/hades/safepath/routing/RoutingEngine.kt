package com.hades.safepath.routing

import android.content.Context
import android.util.Log
import com.hades.safepath.data.SafePathDatabase
import com.hades.safepath.data.entity.Shelter
import com.hades.safepath.data.model.DisasterType
import com.hades.safepath.data.model.RouteNode
import com.hades.safepath.data.repository.ShelterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline-first evacuation routing engine.
 *
 * Requires [initialise] to be called once before any route calculations.
 * All operations run on [Dispatchers.IO] and produce zero outbound network calls.
 *
 * Hazardous GeoJSON edges are assigned infinite cost (skipped) by Dijkstra;
 * dynamic Room hazards receive multiplicative penalties.
 *
 * @param context Android context for asset loading.
 * @param db      SafePath Room database for shelter queries.
 */
class RoutingEngine(
    private val context: Context,
    private val db: SafePathDatabase
) {

    private val TAG = "RoutingEngine"

    private lateinit var graph: RoadGraph
    private val shelterRepository = ShelterRepository(db.shelterDao())
    private val hazardRepository = com.hades.safepath.data.repository.HazardRepository(db.hazardEventDao())

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Loads GeoJSON assets and builds the in-memory road graph.
     *
     * Must be called before [calculateEvacuationRoute] or [findNearestShelter].
     * Safe to call multiple times — rebuilds the graph each time.
     */
    suspend fun initialise() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Initialising routing engine...")

        val roadFeatures = HazardMapLoader.getRoadNetwork(context)
        val floodFeatures = HazardMapLoader.getHazardRoads(context)
        val landslideFeatures = HazardMapLoader.getLandslideZones(context)

        graph = GraphBuilder.buildFromGeoJson(roadFeatures, floodFeatures, landslideFeatures)

        Log.d(TAG, "Routing engine ready: ${graph.nodes.size} nodes")
    }

    fun isInitialised(): Boolean = ::graph.isInitialized

    // -------------------------------------------------------------------------
    // Route calculation
    // -------------------------------------------------------------------------

    /**
     * Calculates the safest evacuation route from the user's position to the
     * nearest verified shelter, avoiding all hazardous edges.
     */
    suspend fun calculateEvacuationRoute(
        userLat: Double,
        userLon: Double,
        disasterType: DisasterType
    ): List<RouteNode> = withContext(Dispatchers.IO) {
        check(::graph.isInitialized) { "RoutingEngine not initialised — call initialise() first" }

        val shelter = findNearestShelter(userLat, userLon)
        Log.d(TAG, "Routing to nearest shelter: ${shelter.name} (disaster=$disasterType)")
        pathToShelter(userLat, userLon, shelter)
    }

    /**
     * Calculates a hazard-aware route from the user to a specific shelter.
     */
    suspend fun calculateRouteToShelter(
        userLat: Double,
        userLon: Double,
        shelter: Shelter
    ): List<RouteNode> = withContext(Dispatchers.IO) {
        check(::graph.isInitialized) { "RoutingEngine not initialised — call initialise() first" }
        Log.d(TAG, "Routing to selected shelter: ${shelter.name}")
        pathToShelter(userLat, userLon, shelter)
    }

    private suspend fun pathToShelter(
        userLat: Double,
        userLon: Double,
        shelter: Shelter
    ): List<RouteNode> {
        var originLat = userLat
        var originLon = userLon

        // If the user is already on/near the shelter, shift to the demo approach
        // node so evacuation UI still shows a real multi-node path.
        val distToShelter = GeoUtils.distanceMeters(userLat, userLon, shelter.lat, shelter.lon)
        if (distToShelter < COLOCATED_SHELTER_METERS) {
            originLat = DEMO_ROUTE_LAT
            originLon = DEMO_ROUTE_LON
            Log.i(
                TAG,
                "Origin coincident with ${shelter.name} (${distToShelter.toInt()}m) — " +
                    "using demo approach ($DEMO_ROUTE_LAT,$DEMO_ROUTE_LON)"
            )
        }

        var startId = graph.findNearestNodeId(originLat, originLon)
        val endId = graph.findNearestNodeId(shelter.lat, shelter.lon)

        if (startId == -1L || endId == -1L) {
            Log.w(TAG, "Graph is empty — cannot compute route")
            return emptyList()
        }

        // Still snapped to the same graph node (e.g. dense shelter cluster) — force demo origin.
        if (startId == endId) {
            startId = graph.findNearestNodeId(DEMO_ROUTE_LAT, DEMO_ROUTE_LON)
            originLat = DEMO_ROUTE_LAT
            originLon = DEMO_ROUTE_LON
            if (startId == endId) {
                Log.w(TAG, "Demo origin still equals destination — graph too sparse")
                return emptyList()
            }
        }

        val activeHazards = hazardRepository.getActiveHazards(System.currentTimeMillis())
        val path = Dijkstra.shortestPath(graph, startId, endId, activeHazards)

        if (path.isEmpty()) {
            Log.w(TAG, "No safe path found from ($originLat,$originLon) to shelter ${shelter.name}")
            return emptyList()
        }

        // Anchor polyline at true origin + shelter coordinates for clearer map drawing.
        val nodes = path.map { node -> RouteNode(nodeId = node.id, lat = node.lat, lon = node.lon) }
            .toMutableList()
        if (nodes.firstOrNull()?.let {
                GeoUtils.distanceMeters(it.lat, it.lon, originLat, originLon) > 5.0
            } == true
        ) {
            nodes.add(0, RouteNode(nodeId = -1L, lat = originLat, lon = originLon))
        }
        if (nodes.lastOrNull()?.let {
                GeoUtils.distanceMeters(it.lat, it.lon, shelter.lat, shelter.lon) > 5.0
            } == true
        ) {
            nodes.add(RouteNode(nodeId = -2L, lat = shelter.lat, lon = shelter.lon))
        }
        return nodes
    }

    // -------------------------------------------------------------------------
    // Shelter lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the nearest verified shelter to (userLat, userLon) using
     * Haversine distance.
     *
     * Falls back to the first available shelter if the DB is empty.
     *
     * @throws IllegalStateException if no shelters are seeded in the database.
     */
    suspend fun findNearestShelter(userLat: Double, userLon: Double): Shelter =
        withContext(Dispatchers.IO) {
            val shelters = shelterRepository.getAllActiveShelters().ifEmpty {
                shelterRepository.getAllShelters()
            }
            check(shelters.isNotEmpty()) {
                "No shelters in database — ensure DB seed ran on first launch"
            }

            val candidates = shelters.map { Pair(it.lat, it.lon) }
            val nearestIndex = GeoUtils.findNearestPoint(userLat, userLon, candidates)

            shelters[nearestIndex].also {
                Log.d(TAG, "Nearest shelter: ${it.name} (${it.lat}, ${it.lon})")
            }
        }

    companion object {
        /** Safe approach point on Ratnapura grid (outside flood/landslide placeholders). */
        const val DEMO_ROUTE_LAT = 6.6950
        const val DEMO_ROUTE_LON = 80.4050
        private const val COLOCATED_SHELTER_METERS = 80.0
    }
}
