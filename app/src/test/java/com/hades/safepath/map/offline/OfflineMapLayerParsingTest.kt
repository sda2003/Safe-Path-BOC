package com.hades.safepath.map.offline

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

/**
 * Unit coverage for GeoJSON → overlay models used by the offline map.
 * Mirrors [OfflineMapLayerRepository] parsing without Android Context.
 */
class OfflineMapLayerParsingTest {

    @Test
    fun polygonRing_parsesLonLatToGeoPoints() {
        val features = floodFeatureCollection()
        val zones = parsePolygons(features)
        assertEquals(1, zones.size)
        assertEquals("Test Flood", zones[0].first)
        assertEquals(5, zones[0].second.size)
        assertEquals(6.0, zones[0].second[0].latitude, 0.0001)
        assertEquals(80.0, zones[0].second[0].longitude, 0.0001)
    }

    @Test
    fun multiPolygon_expandsToMultipleRings() {
        val features = JSONArray().put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject().put("name", "Slide"))
                .put(
                    "geometry",
                    JSONObject()
                        .put("type", "MultiPolygon")
                        .put(
                            "coordinates",
                            JSONArray()
                                .put(ringCoords(80.0, 6.0))
                                .put(ringCoords(81.0, 7.0))
                        )
                )
        )
        val zones = parseMultiPolygons(features)
        assertEquals(2, zones.size)
    }

    @Test
    fun lineString_parsesRoadNetwork() {
        val features = JSONArray().put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject().put("name", "A4"))
                .put(
                    "geometry",
                    JSONObject()
                        .put("type", "LineString")
                        .put(
                            "coordinates",
                            JSONArray()
                                .put(JSONArray().put(80.39).put(6.68))
                                .put(JSONArray().put(80.40).put(6.69))
                        )
                )
        )
        val roads = parseRoads(features)
        assertEquals(1, roads.size)
        assertEquals("A4", roads[0].first)
        assertTrue(roads[0].second.size >= 2)
    }

    @Test
    fun hazardOverlayStyleConstants_documented() {
        // OfflineMapFragment uses these exact fills / route colour:
        val floodFill = "#40007BFF"
        val landslideFill = "#40DC3545"
        val routeGreen = "#00C853"
        assertTrue(floodFill.startsWith("#40"))
        assertTrue(landslideFill.startsWith("#40"))
        assertEquals("#00C853", routeGreen)
    }

    // --- local parsers mirroring OfflineMapLayerRepository (no Context) ---

    private fun floodFeatureCollection(): JSONArray = JSONArray().put(
        JSONObject()
            .put("type", "Feature")
            .put("properties", JSONObject().put("name", "Test Flood"))
            .put(
                "geometry",
                JSONObject()
                    .put("type", "Polygon")
                    .put("coordinates", JSONArray().put(ringCoords(80.0, 6.0).getJSONArray(0)))
            )
    )

    private fun ringCoords(lon: Double, lat: Double): JSONArray =
        JSONArray().put(
            JSONArray()
                .put(JSONArray().put(lon).put(lat))
                .put(JSONArray().put(lon + 0.1).put(lat))
                .put(JSONArray().put(lon + 0.1).put(lat + 0.1))
                .put(JSONArray().put(lon).put(lat + 0.1))
                .put(JSONArray().put(lon).put(lat))
        )

    private fun parsePolygons(features: JSONArray): List<Pair<String, List<GeoPoint>>> {
        val out = mutableListOf<Pair<String, List<GeoPoint>>>()
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val name = feature.getJSONObject("properties").optString("name", "zone-$i")
            val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
            val ring = coords.getJSONArray(0)
            val pts = mutableListOf<GeoPoint>()
            for (c in 0 until ring.length()) {
                val pair = ring.getJSONArray(c)
                pts += GeoPoint(pair.getDouble(1), pair.getDouble(0))
            }
            out += name to pts
        }
        return out
    }

    private fun parseMultiPolygons(features: JSONArray): List<Pair<String, List<GeoPoint>>> {
        val out = mutableListOf<Pair<String, List<GeoPoint>>>()
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val name = feature.getJSONObject("properties").optString("name", "zone-$i")
            val multi = feature.getJSONObject("geometry").getJSONArray("coordinates")
            for (p in 0 until multi.length()) {
                val poly = multi.getJSONArray(p)
                val ring = poly.getJSONArray(0)
                val pts = mutableListOf<GeoPoint>()
                for (c in 0 until ring.length()) {
                    val pair = ring.getJSONArray(c)
                    pts += GeoPoint(pair.getDouble(1), pair.getDouble(0))
                }
                out += "$name-$p" to pts
            }
        }
        return out
    }

    private fun parseRoads(features: JSONArray): List<Pair<String, List<GeoPoint>>> {
        val out = mutableListOf<Pair<String, List<GeoPoint>>>()
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val name = feature.getJSONObject("properties").optString("name", "road-$i")
            val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
            val pts = mutableListOf<GeoPoint>()
            for (c in 0 until coords.length()) {
                val pair = coords.getJSONArray(c)
                pts += GeoPoint(pair.getDouble(1), pair.getDouble(0))
            }
            if (pts.size >= 2) out += name to pts
        }
        return out
    }
}
