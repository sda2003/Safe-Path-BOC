package com.hades.safepath.routing

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies hazard-aware graph construction: edges intersecting flood/landslide
 * polygons are flagged [Edge.isHazardous] (infinite cost in Dijkstra).
 */
class GraphBuilderHazardTest {

    @Test
    fun edgeInsideFloodPolygon_isMarkedHazardous() {
        val roads = JSONArray().put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject().put("name", "test-road"))
                .put(
                    "geometry",
                    JSONObject()
                        .put("type", "LineString")
                        .put(
                            "coordinates",
                            JSONArray()
                                .put(JSONArray().put(80.05).put(6.05))
                                .put(JSONArray().put(80.06).put(6.06))
                        )
                )
        )

        val floods = JSONArray().put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject().put("name", "flood"))
                .put(
                    "geometry",
                    JSONObject()
                        .put("type", "Polygon")
                        .put(
                            "coordinates",
                            JSONArray().put(
                                JSONArray()
                                    .put(JSONArray().put(80.0).put(6.0))
                                    .put(JSONArray().put(80.2).put(6.0))
                                    .put(JSONArray().put(80.2).put(6.2))
                                    .put(JSONArray().put(80.0).put(6.2))
                                    .put(JSONArray().put(80.0).put(6.0))
                            )
                        )
                )
        )

        val graph = GraphBuilder.buildFromGeoJson(roads, floods, JSONArray())
        val edges = graph.nodes.keys.flatMap { graph.getNeighbours(it) }
        assertTrue(edges.isNotEmpty())
        assertTrue("Edge inside flood zone must be hazardous", edges.any { it.isHazardous })
    }

    @Test
    fun edgeOutsideAllHazards_isSafe() {
        val roads = JSONArray().put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject().put("name", "safe-road"))
                .put(
                    "geometry",
                    JSONObject()
                        .put("type", "LineString")
                        .put(
                            "coordinates",
                            JSONArray()
                                .put(JSONArray().put(79.0).put(5.0))
                                .put(JSONArray().put(79.01).put(5.01))
                        )
                )
        )

        val floods = JSONArray().put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject().put("name", "flood"))
                .put(
                    "geometry",
                    JSONObject()
                        .put("type", "Polygon")
                        .put(
                            "coordinates",
                            JSONArray().put(
                                JSONArray()
                                    .put(JSONArray().put(80.0).put(6.0))
                                    .put(JSONArray().put(80.2).put(6.0))
                                    .put(JSONArray().put(80.2).put(6.2))
                                    .put(JSONArray().put(80.0).put(6.2))
                                    .put(JSONArray().put(80.0).put(6.0))
                            )
                        )
                )
        )

        val graph = GraphBuilder.buildFromGeoJson(roads, floods, JSONArray())
        val edges = graph.nodes.keys.flatMap { graph.getNeighbours(it) }
        assertTrue(edges.isNotEmpty())
        assertTrue("Edge outside flood zone must be safe", edges.none { it.isHazardous })
    }

    @Test
    fun multiLineString_createsEdges() {
        val roads = JSONArray().put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject().put("name", "multi"))
                .put(
                    "geometry",
                    JSONObject()
                        .put("type", "MultiLineString")
                        .put(
                            "coordinates",
                            JSONArray()
                                .put(
                                    JSONArray()
                                        .put(JSONArray().put(79.0).put(5.0))
                                        .put(JSONArray().put(79.01).put(5.01))
                                )
                                .put(
                                    JSONArray()
                                        .put(JSONArray().put(79.02).put(5.02))
                                        .put(JSONArray().put(79.03).put(5.03))
                                )
                        )
                )
        )

        val graph = GraphBuilder.buildFromGeoJson(roads, JSONArray(), JSONArray())
        assertEquals(4, graph.nodes.size)
    }
}
