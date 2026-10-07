package com.hades.safepath.map.offline

import android.content.Context
import android.util.Log
import com.hades.safepath.routing.HazardMapLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads local GeoJSON (roads, flood, landslide) into overlay-ready models.
 * All I/O on [Dispatchers.IO] — zero network.
 */
@Singleton
class OfflineMapLayerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class PolygonZone(
        val name: String,
        val kind: Kind,
        val rings: List<List<GeoPoint>>
    ) {
        enum class Kind { FLOOD, LANDSLIDE }
    }

    data class RoadLine(
        val name: String,
        val points: List<GeoPoint>
    )

    data class OverlayPack(
        val floodZones: List<PolygonZone>,
        val landslideZones: List<PolygonZone>,
        val roads: List<RoadLine>
    )

    suspend fun loadOverlays(): OverlayPack = withContext(Dispatchers.IO) {
        val flood = parsePolygons(HazardMapLoader.getHazardRoads(context), PolygonZone.Kind.FLOOD)
        val slide = parsePolygons(HazardMapLoader.getLandslideZones(context), PolygonZone.Kind.LANDSLIDE)
        val roads = parseRoads(HazardMapLoader.getRoadNetwork(context))
        Log.d(TAG, "Overlays: flood=${flood.size} landslide=${slide.size} roads=${roads.size}")
        OverlayPack(flood, slide, roads)
    }

    private fun parsePolygons(features: JSONArray, kind: PolygonZone.Kind): List<PolygonZone> {
        val out = mutableListOf<PolygonZone>()
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val props = feature.optJSONObject("properties")
            val name = props?.optString("name")?.ifBlank { "$kind-$i" } ?: "$kind-$i"
            val geometry = feature.optJSONObject("geometry") ?: continue
            when (geometry.optString("type")) {
                "Polygon" -> {
                    val coords = geometry.optJSONArray("coordinates") ?: continue
                    val rings = parsePolygonCoords(coords)
                    if (rings.isNotEmpty()) out += PolygonZone(name, kind, rings)
                }
                "MultiPolygon" -> {
                    val multi = geometry.optJSONArray("coordinates") ?: continue
                    for (p in 0 until multi.length()) {
                        val poly = multi.optJSONArray(p) ?: continue
                        val rings = parsePolygonCoords(poly)
                        if (rings.isNotEmpty()) out += PolygonZone("$name-$p", kind, rings)
                    }
                }
            }
        }
        return out
    }

    private fun parsePolygonCoords(coords: JSONArray): List<List<GeoPoint>> {
        val rings = mutableListOf<List<GeoPoint>>()
        for (r in 0 until coords.length()) {
            val ring = coords.optJSONArray(r) ?: continue
            val pts = mutableListOf<GeoPoint>()
            for (c in 0 until ring.length()) {
                val pair = ring.optJSONArray(c) ?: continue
                val lon = pair.optDouble(0, Double.NaN)
                val lat = pair.optDouble(1, Double.NaN)
                if (!lat.isNaN() && !lon.isNaN()) pts += GeoPoint(lat, lon)
            }
            if (pts.size >= 3) rings += pts
        }
        return rings
    }

    private fun parseRoads(features: JSONArray): List<RoadLine> {
        val out = mutableListOf<RoadLine>()
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val props = feature.optJSONObject("properties")
            val name = props?.optString("name")?.ifBlank { "road-$i" } ?: "road-$i"
            val geometry = feature.optJSONObject("geometry") ?: continue
            when (geometry.optString("type")) {
                "LineString" -> {
                    val coords = geometry.optJSONArray("coordinates") ?: continue
                    val pts = linePoints(coords)
                    if (pts.size >= 2) out += RoadLine(name, pts)
                }
                "MultiLineString" -> {
                    val multi = geometry.optJSONArray("coordinates") ?: continue
                    for (l in 0 until multi.length()) {
                        val line = multi.optJSONArray(l) ?: continue
                        val pts = linePoints(line)
                        if (pts.size >= 2) out += RoadLine("$name-$l", pts)
                    }
                }
            }
        }
        return out
    }

    private fun linePoints(coords: JSONArray): List<GeoPoint> {
        val pts = mutableListOf<GeoPoint>()
        for (c in 0 until coords.length()) {
            val pair = coords.optJSONArray(c) ?: continue
            val lon = pair.optDouble(0, Double.NaN)
            val lat = pair.optDouble(1, Double.NaN)
            if (!lat.isNaN() && !lon.isNaN()) pts += GeoPoint(lat, lon)
        }
        return pts
    }

    companion object {
        private const val TAG = "OfflineMapLayerRepo"
    }
}
