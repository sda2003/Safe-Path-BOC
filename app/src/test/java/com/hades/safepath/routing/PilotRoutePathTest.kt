package com.hades.safepath.routing

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Uses the real pilot GeoJSON to prove Dijkstra can produce a multi-node
 * evacuation path (the bug that showed "1 nodes" on device).
 */
class PilotRoutePathTest {

    @Test
    fun demoApproach_toRatnapuraSchool_hasMultipleNodes() {
        val rawDir = findRawDir()
        val roads = JSONObject(File(rawDir, "road_network.geojson").readText())
            .getJSONArray("features")
        val floods = JSONObject(File(rawDir, "flood_zones.geojson").readText())
            .getJSONArray("features")
        val slides = JSONObject(File(rawDir, "landslide_zones.geojson").readText())
            .getJSONArray("features")

        val graph = GraphBuilder.buildFromGeoJson(roads, floods, slides)
        assertTrue("Expected road graph nodes", graph.nodes.isNotEmpty())

        val startId = graph.findNearestNodeId(
            RoutingEngine.DEMO_ROUTE_LAT,
            RoutingEngine.DEMO_ROUTE_LON
        )
        val endId = graph.findNearestNodeId(6.6831, 80.3988) // Ratnapura Central School
        assertTrue(startId != -1L && endId != -1L)
        assertTrue("Demo start must not equal shelter node", startId != endId)

        val path = Dijkstra.shortestPath(graph, startId, endId)
        assertTrue(
            "Expected multi-node safe path, got ${path.size}",
            path.size >= 3
        )
    }

    @Test
    fun shelterColocatedStart_sameNodeWithoutOffset() {
        val rawDir = findRawDir()
        val roads = JSONObject(File(rawDir, "road_network.geojson").readText())
            .getJSONArray("features")
        val graph = GraphBuilder.buildFromGeoJson(roads, org.json.JSONArray(), org.json.JSONArray())
        val id = graph.findNearestNodeId(6.6831, 80.3988)
        val path = Dijkstra.shortestPath(graph, id, id)
        assertTrue("Colocated start/end collapses to 1 node", path.size == 1)
    }

    private fun findRawDir(): File {
        val candidates = listOf(
            File("app/src/main/res/raw"),
            File("../app/src/main/res/raw"),
            File("src/main/res/raw")
        )
        return candidates.firstOrNull { File(it, "road_network.geojson").exists() }
            ?: error("road_network.geojson not found — run tests from SafePath root")
    }
}
