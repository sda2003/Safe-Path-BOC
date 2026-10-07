package com.hades.safepath.routing

import android.util.Log
import org.json.JSONArray

/**
 * Builds a [RoadGraph] from GeoJSON feature arrays.
 *
 * Road network features must be LineString or MultiLineString geometry.
 * Hazard zones (flood and landslide) flag edges as [Edge.isHazardous]
 * when either endpoint **or the edge midpoint** intersects a hazard polygon
 * (infinite / skip cost in Dijkstra).
 */
object GraphBuilder {

    private const val TAG = "GraphBuilder"

    /**
     * Converts a geographic coordinate to a stable Long node ID.
     * Uses 6 decimal places of precision (~0.1m accuracy).
     */
    fun coordToId(lat: Double, lon: Double): Long {
        val latPart = (lat * 1_000_000).toLong()
        val lonPart = (lon * 1_000_000).toLong()
        return latPart * 1_000_000L + lonPart
    }

    /**
     * Builds a [RoadGraph] from GeoJSON feature arrays.
     *
     * @param roadFeatures      Features array from road_network.geojson.
     * @param floodFeatures     Features array from flood_zones.geojson.
     * @param landslideFeatures Features array from landslide_zones.geojson.
     * @return A fully populated [RoadGraph] with hazard flags set.
     */
    fun buildFromGeoJson(
        roadFeatures: JSONArray,
        floodFeatures: JSONArray,
        landslideFeatures: JSONArray
    ): RoadGraph {
        val graph = RoadGraph()
        var edgeCount = 0
        var hazardousCount = 0
        var skippedFeatures = 0

        for (i in 0 until roadFeatures.length()) {
            val feature = roadFeatures.optJSONObject(i)
            if (feature == null) {
                skippedFeatures++
                continue
            }

            val geometry = feature.optJSONObject("geometry")
            if (geometry == null) {
                Log.w(TAG, "Feature $i missing geometry — skipping")
                skippedFeatures++
                continue
            }

            when (geometry.optString("type")) {
                "LineString" -> {
                    val coords = geometry.optJSONArray("coordinates")
                    if (coords == null) {
                        skippedFeatures++
                        continue
                    }
                    val added = addLineEdges(graph, coords, floodFeatures, landslideFeatures)
                    edgeCount += added.first
                    hazardousCount += added.second
                }
                "MultiLineString" -> {
                    val multi = geometry.optJSONArray("coordinates")
                    if (multi == null) {
                        skippedFeatures++
                        continue
                    }
                    for (l in 0 until multi.length()) {
                        val line = multi.optJSONArray(l) ?: continue
                        val added = addLineEdges(graph, line, floodFeatures, landslideFeatures)
                        edgeCount += added.first
                        hazardousCount += added.second
                    }
                }
                else -> {
                    Log.d(TAG, "Feature $i is ${geometry.optString("type")} — skipping")
                    skippedFeatures++
                }
            }
        }

        Log.d(
            TAG,
            "Graph built: ${graph.nodes.size} nodes, $edgeCount edges " +
                "($hazardousCount hazardous), $skippedFeatures features skipped"
        )

        return graph
    }

    /**
     * @return Pair(edgeCount, hazardousCount) for the LineString segment.
     */
    private fun addLineEdges(
        graph: RoadGraph,
        coords: JSONArray,
        floodFeatures: JSONArray,
        landslideFeatures: JSONArray
    ): Pair<Int, Int> {
        var edgeCount = 0
        var hazardousCount = 0

        for (j in 0 until coords.length() - 1) {
            val fromCoord = coords.optJSONArray(j) ?: continue
            val toCoord = coords.optJSONArray(j + 1) ?: continue

            // GeoJSON coordinates are [lon, lat]
            val fromLon = fromCoord.optDouble(0, Double.NaN)
            val fromLat = fromCoord.optDouble(1, Double.NaN)
            val toLon = toCoord.optDouble(0, Double.NaN)
            val toLat = toCoord.optDouble(1, Double.NaN)

            if (fromLat.isNaN() || fromLon.isNaN() || toLat.isNaN() || toLon.isNaN()) {
                continue
            }

            val fromId = coordToId(fromLat, fromLon)
            val toId = coordToId(toLat, toLon)

            graph.addNode(Node(fromId, fromLat, fromLon))
            graph.addNode(Node(toId, toLat, toLon))

            val weight = GeoUtils.distanceMeters(fromLat, fromLon, toLat, toLon)
            val midLat = (fromLat + toLat) / 2.0
            val midLon = (fromLon + toLon) / 2.0

            // Infinite-cost flag if endpoints OR midpoint intersect hazard polygons
            val hazardous =
                GeoUtils.isPointInFloodZone(fromLat, fromLon, floodFeatures) ||
                    GeoUtils.isPointInFloodZone(toLat, toLon, floodFeatures) ||
                    GeoUtils.isPointInFloodZone(midLat, midLon, floodFeatures) ||
                    GeoUtils.isPointInLandslideZone(fromLat, fromLon, landslideFeatures) ||
                    GeoUtils.isPointInLandslideZone(toLat, toLon, landslideFeatures) ||
                    GeoUtils.isPointInLandslideZone(midLat, midLon, landslideFeatures)

            graph.addEdge(
                Edge(
                    fromId = fromId,
                    toId = toId,
                    weightMeters = weight,
                    isHazardous = hazardous
                )
            )

            edgeCount++
            if (hazardous) hazardousCount++
        }

        return edgeCount to hazardousCount
    }
}
