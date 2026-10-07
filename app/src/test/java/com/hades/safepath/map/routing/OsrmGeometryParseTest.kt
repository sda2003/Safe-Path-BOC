package com.hades.safepath.map.routing

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsrmGeometryParseTest {

    @Test
    fun parsesGeoJsonLineString_lonLatOrder() {
        val json = """
            {
              "code":"Ok",
              "routes":[{
                "distance":1234.5,
                "duration":456.0,
                "geometry":{
                  "type":"LineString",
                  "coordinates":[[80.405,6.695],[80.402,6.690],[80.3988,6.6831]]
                }
              }]
            }
        """.trimIndent()
        val root = JSONObject(json)
        val route = root.getJSONArray("routes").getJSONObject(0)
        val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
        val points = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until coords.length()) {
            val c = coords.getJSONArray(i)
            val lon = c.getDouble(0)
            val lat = c.getDouble(1)
            points.add(lat to lon)
        }
        assertEquals(3, points.size)
        assertEquals(6.695, points[0].first, 1e-6)
        assertEquals(80.405, points[0].second, 1e-6)
        assertTrue(route.getDouble("distance") > 1000)
    }
}
