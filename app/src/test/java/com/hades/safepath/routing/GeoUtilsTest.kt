package com.hades.safepath.routing

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GeoUtils].
 *
 * All tests are pure JVM — no Android context required.
 * Polygon coordinates match the placeholder flood_zones.geojson
 * (Ratnapura Flood Zone A) so these tests double as fixture validation.
 */
class GeoUtilsTest {

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    /**
     * Ratnapura Flood Zone A polygon from flood_zones.geojson:
     * SW corner (80.385, 6.675) → NE corner (80.400, 6.690)
     * Coordinates are [lon, lat] pairs per GeoJSON spec.
     */
    private val ratnapuraFloodPolygon: JSONArray = JSONArray(
        """[
            [80.385, 6.675],
            [80.400, 6.675],
            [80.400, 6.690],
            [80.385, 6.690],
            [80.385, 6.675]
        ]"""
    )

    /**
     * A minimal FeatureCollection-style features array wrapping the flood polygon,
     * used for isPointInFloodZone tests.
     */
    private val floodFeaturesArray: JSONArray = JSONArray(
        """[
            {
                "type": "Feature",
                "properties": { "name": "Ratnapura Flood Zone A" },
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [
                        [
                            [80.385, 6.675],
                            [80.400, 6.675],
                            [80.400, 6.690],
                            [80.385, 6.690],
                            [80.385, 6.675]
                        ]
                    ]
                }
            }
        ]"""
    )

    /** Three shelter coordinates matching shelters_ratnapura.geojson */
    private val shelterCandidates = listOf(
        Pair(6.6831, 80.3988),  // index 0 — Ratnapura Central School
        Pair(6.6824, 80.3972),  // index 1 — Ratnapura District Hospital
        Pair(6.6841, 80.3994)   // index 2 — Ratnapura Divisional Secretariat
    )

    // -------------------------------------------------------------------------
    // pointInPolygon tests
    // -------------------------------------------------------------------------

    /**
     * Centre of the Ratnapura Flood Zone A polygon is inside.
     * Polygon bounds: lon [80.385–80.400], lat [6.675–6.690]
     * Centre: lon=80.3925, lat=6.6825 — both within bounds.
     */
    @Test
    fun testPointInsidePolygon() {
        val insideLat = 6.682   // within [6.675, 6.690]
        val insideLon = 80.392  // within [80.385, 80.400]

        assertTrue(
            "Point ($insideLat, $insideLon) should be inside the flood polygon",
            GeoUtils.pointInPolygon(insideLat, insideLon, ratnapuraFloodPolygon)
        )
    }

    /**
     * Colombo city centre (6.9271, 79.8612) is clearly outside the polygon.
     */
    @Test
    fun testPointOutsidePolygon() {
        val outsideLat = 6.9271  // Colombo
        val outsideLon = 79.8612

        assertFalse(
            "Point ($outsideLat, $outsideLon) should be outside the flood polygon",
            GeoUtils.pointInPolygon(outsideLat, outsideLon, ratnapuraFloodPolygon)
        )
    }

    /**
     * A point just outside the polygon boundary should return false.
     */
    @Test
    fun testPointJustOutsidePolygonBoundary() {
        val lat = 6.674   // just south of the polygon's southern edge (6.675)
        val lon = 80.392

        assertFalse(
            "Point just outside polygon boundary should return false",
            GeoUtils.pointInPolygon(lat, lon, ratnapuraFloodPolygon)
        )
    }

    // -------------------------------------------------------------------------
    // distanceMeters (Haversine) tests
    // -------------------------------------------------------------------------

    /**
     * Ratnapura town centre (6.6831, 80.3988) to Colombo Fort (6.9344, 79.8428).
     * Haversine gives ~67.4 km straight-line. Assert within ±5 000 m of 67 500 m.
     */
    @Test
    fun testHaversineKnownDistance() {
        val ratnapuraLat = 6.6831
        val ratnapuraLon = 80.3988
        val colomboLat = 6.9344
        val colomboLon = 79.8428

        val distanceM = GeoUtils.distanceMeters(ratnapuraLat, ratnapuraLon, colomboLat, colomboLon)

        val expectedM = 67_500.0
        val toleranceM = 5_000.0

        assertTrue(
            "Ratnapura→Colombo distance should be ~67.5 km ±5 km, got ${distanceM.toInt()}m",
            distanceM in (expectedM - toleranceM)..(expectedM + toleranceM)
        )
    }

    /**
     * Distance from a point to itself must be 0.
     */
    @Test
    fun testHaversineSamePoint() {
        val dist = GeoUtils.distanceMeters(6.6831, 80.3988, 6.6831, 80.3988)
        assertEquals("Distance from point to itself should be 0", 0.0, dist, 0.001)
    }

    // -------------------------------------------------------------------------
    // findNearestPoint tests
    // -------------------------------------------------------------------------

    /**
     * Query point very close to Ratnapura District Hospital (index 1).
     * Expected: index 1 is returned.
     */
    @Test
    fun testFindNearest_returnsClosestShelter() {
        // Slightly offset from Ratnapura District Hospital (6.6824, 80.3972)
        val queryLat = 6.6825
        val queryLon = 80.3973

        val nearest = GeoUtils.findNearestPoint(queryLat, queryLon, shelterCandidates)

        assertEquals(
            "Nearest shelter to ($queryLat, $queryLon) should be index 1 (District Hospital)",
            1,
            nearest
        )
    }

    /**
     * Query point very close to Ratnapura Central School (index 0).
     */
    @Test
    fun testFindNearest_returnsFirstShelterWhenClosest() {
        // Slightly offset from Ratnapura Central School (6.6831, 80.3988)
        val queryLat = 6.6832
        val queryLon = 80.3989

        val nearest = GeoUtils.findNearestPoint(queryLat, queryLon, shelterCandidates)

        assertEquals(
            "Nearest shelter to ($queryLat, $queryLon) should be index 0 (Central School)",
            0,
            nearest
        )
    }

    /**
     * Empty candidate list returns -1.
     */
    @Test
    fun testFindNearest_emptyListReturnsMinusOne() {
        val result = GeoUtils.findNearestPoint(6.6831, 80.3988, emptyList())
        assertEquals("Empty candidate list should return -1", -1, result)
    }

    // -------------------------------------------------------------------------
    // isPointInFloodZone tests
    // -------------------------------------------------------------------------

    @Test
    fun testIsPointInFloodZone_insideReturnsTrue() {
        // lat=6.682 within [6.675,6.690], lon=80.392 within [80.385,80.400]
        assertTrue(
            GeoUtils.isPointInFloodZone(6.682, 80.392, floodFeaturesArray)
        )
    }

    @Test
    fun testIsPointInFloodZone_outsideReturnsFalse() {
        assertFalse(
            GeoUtils.isPointInFloodZone(6.9271, 79.8612, floodFeaturesArray)
        )
    }
}
