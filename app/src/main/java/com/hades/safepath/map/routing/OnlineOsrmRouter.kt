package com.hades.safepath.map.routing

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online road routing via the public OSRM demo server.
 * Geometry is GeoJSON LineString (lon, lat) — converted to lat/lon for OsmDroid.
 *
 * No API key required for light demo use. For production, set a self-hosted
 * OSRM base URL via BuildConfig / local.properties (see README).
 */
@Singleton
class OnlineOsrmRouter @Inject constructor() {

    suspend fun route(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        baseUrl: String = DEFAULT_BASE
    ): EvacuationRouteResult? = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=false",
            baseUrl.trimEnd('/'),
            originLon,
            originLat,
            destLon,
            destLat
        )
        Log.i(TAG, "[ROUTE] Provider = online OSRM")
        Log.i(TAG, "[ROUTE] Request started")
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            conn.disconnect()
            if (code !in 200..299) {
                Log.w(TAG, "[ROUTE] OSRM HTTP $code")
                return@withContext null
            }
            parseOsrm(body)
        } catch (e: Exception) {
            Log.e(TAG, "[ROUTE] OSRM failed", e)
            null
        }
    }

    private fun parseOsrm(json: String): EvacuationRouteResult? {
        val root = JSONObject(json)
        if (root.optString("code") != "Ok") {
            Log.w(TAG, "[ROUTE] OSRM code=${root.optString("code")}")
            return null
        }
        val route = root.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val coords = route.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val points = ArrayList<EvacuationRouteResult.LatLon>(coords.length())
        for (i in 0 until coords.length()) {
            val c = coords.optJSONArray(i) ?: continue
            val lon = c.optDouble(0, Double.NaN)
            val lat = c.optDouble(1, Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            points.add(EvacuationRouteResult.LatLon(lat, lon))
        }
        if (points.size < 2) return null
        val distance = route.optDouble("distance", 0.0)
        val duration = route.optDouble("duration", Double.NaN)
        Log.i(TAG, "[ROUTE] Response received points=${points.size} dist=${distance.toInt()}m")
        return EvacuationRouteResult(
            points = points,
            distanceMeters = distance,
            durationSeconds = duration.takeIf { it.isFinite() },
            providerLabel = "Online · OSRM",
            isDirectFallback = false
        )
    }

    companion object {
        private const val TAG = "OnlineOsrmRouter"
        const val DEFAULT_BASE = "https://router.project-osrm.org"
        private const val USER_AGENT = "SafePath/2.0 (disaster evacuation; educational)"
    }
}
