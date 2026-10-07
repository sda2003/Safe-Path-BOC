package com.hades.safepath.routing

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geometry utilities for hazard zone checks and distance calculations.
 *
 * All functions are stateless and safe to call from any thread.
 */
object GeoUtils {

    private const val TAG = "GeoUtils"
    private const val EARTH_RADIUS_METRES = 6_371_000.0

    // -------------------------------------------------------------------------
    // Point-in-polygon (ray-casting)
    // -------------------------------------------------------------------------

    /**
     * Determines whether a point (lat, lon) lies inside a GeoJSON polygon ring.
     *
     * Uses the ray-casting algorithm. Works correctly for convex and concave
     * polygons; does not handle anti-meridian crossing.
     *
     * @param lat           Latitude of the test point.
     * @param lon           Longitude of the test point.
     * @param polygonCoords GeoJSON coordinates array — an array of [lon, lat] pairs
     *                      representing a single polygon ring (first == last).
     * @return true if the point is inside the polygon ring.
     */
    fun pointInPolygon(lat: Double, lon: Double, polygonCoords: JSONArray): Boolean {
        val n = polygonCoords.length()
        if (n < 3) return false

        var inside = false
        var j = n - 1

        for (i in 0 until n) {
            val vi = polygonCoords.getJSONArray(i)
            val vj = polygonCoords.getJSONArray(j)

            val xi = vi.getDouble(0)  // lon
            val yi = vi.getDouble(1)  // lat
            val xj = vj.getDouble(0)
            val yj = vj.getDouble(1)

            val intersects = ((yi > lat) != (yj > lat)) &&
                (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)

            if (intersects) inside = !inside
            j = i
        }

        return inside
    }

    // -------------------------------------------------------------------------
    // Haversine distance
    // -------------------------------------------------------------------------

    /**
     * Calculates the great-circle distance between two geographic points using
     * the Haversine formula.
     *
     * @return Distance in metres.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METRES * c
    }

    // -------------------------------------------------------------------------
    // Nearest point
    // -------------------------------------------------------------------------

    /**
     * Returns the index of the nearest candidate point to (lat, lon) using
     * the Haversine distance.
     *
     * @param lat        Latitude of the query point.
     * @param lon        Longitude of the query point.
     * @param candidates List of (latitude, longitude) pairs to search.
     * @return Index of the nearest candidate, or -1 if the list is empty.
     */
    fun findNearestPoint(lat: Double, lon: Double, candidates: List<Pair<Double, Double>>): Int {
        if (candidates.isEmpty()) return -1

        var nearestIndex = 0
        var nearestDist = Double.MAX_VALUE

        candidates.forEachIndexed { index, (cLat, cLon) ->
            val dist = distanceMeters(lat, lon, cLat, cLon)
            if (dist < nearestDist) {
                nearestDist = dist
                nearestIndex = index
            }
        }

        Log.d(TAG, "findNearestPoint: nearest index=$nearestIndex dist=${nearestDist.toInt()}m")
        return nearestIndex
    }

    // -------------------------------------------------------------------------
    // Hazard zone checks
    // -------------------------------------------------------------------------

    /**
     * Returns true if (lat, lon) falls inside any flood zone polygon in
     * [floodFeatures].
     *
     * Supports GeoJSON geometry types "Polygon" and "MultiPolygon".
     */
    fun isPointInFloodZone(lat: Double, lon: Double, floodFeatures: JSONArray): Boolean =
        isPointInZoneFeatures(lat, lon, floodFeatures, "flood")

    /**
     * Returns true if (lat, lon) falls inside any landslide zone polygon in
     * [landslideFeatures].
     *
     * Supports GeoJSON geometry types "Polygon" and "MultiPolygon".
     */
    fun isPointInLandslideZone(lat: Double, lon: Double, landslideFeatures: JSONArray): Boolean =
        isPointInZoneFeatures(lat, lon, landslideFeatures, "landslide")

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun isPointInZoneFeatures(
        lat: Double,
        lon: Double,
        features: JSONArray,
        label: String
    ): Boolean {
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue

            if (checkGeometry(lat, lon, geometry)) {
                Log.d(TAG, "Point ($lat,$lon) is inside $label zone feature $i")
                return true
            }
        }
        return false
    }

    /**
     * Checks whether (lat, lon) is inside a GeoJSON geometry object.
     * Handles Polygon and MultiPolygon types.
     */
    private fun checkGeometry(lat: Double, lon: Double, geometry: JSONObject): Boolean {
        return when (val type = geometry.optString("type")) {
            "Polygon" -> {
                // coordinates = [ outerRing, ...holes ]
                val rings = geometry.optJSONArray("coordinates") ?: return false
                val outerRing = rings.optJSONArray(0) ?: return false
                pointInPolygon(lat, lon, outerRing)
            }
            "MultiPolygon" -> {
                // coordinates = [ [ [ outerRing, ...holes ] ], ... ]
                val polygons = geometry.optJSONArray("coordinates") ?: return false
                for (p in 0 until polygons.length()) {
                    val rings = polygons.optJSONArray(p) ?: continue
                    val outerRing = rings.optJSONArray(0) ?: continue
                    if (pointInPolygon(lat, lon, outerRing)) return true
                }
                false
            }
            else -> {
                Log.d(TAG, "Unsupported geometry type for hazard check: $type")
                false
            }
        }
    }
}
