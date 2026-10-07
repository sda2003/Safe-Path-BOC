package com.hades.safepath.routing

import android.util.Log
import com.hades.safepath.data.entity.HazardEvent
import java.util.PriorityQueue

/**
 * Standard Dijkstra shortest-path algorithm for [RoadGraph].
 *
 * Safety invariant:
 *   Static GeoJSON edges with [Edge.isHazardous] == true are skipped entirely
 *   (infinite cost). Dynamic Room hazards apply multiplicative penalties;
 *   ROAD_BLOCK / BUILDING_COLLAPSE use [Double.POSITIVE_INFINITY].
 */
object Dijkstra {

    private const val TAG = "Dijkstra"
    
    private const val BASE_HAZARD_PENALTY = 100.0 // Multiplier for base weight

    /**
     * Finds the shortest safe path from [startId] to [endId] in [graph].
     *
     * @param graph   The road graph to search.
     * @param startId Node ID of the origin.
     * @param endId   Node ID of the destination.
     * @param activeHazards List of currently active dynamic hazards.
     * @return Ordered list of [Node]s from start to end (inclusive),
     *         or an empty list if no safe path exists.
     */
    fun shortestPath(
        graph: RoadGraph, 
        startId: Long, 
        endId: Long, 
        activeHazards: List<HazardEvent> = emptyList()
    ): List<Node> {
        if (!graph.nodes.containsKey(startId)) {
            Log.w(TAG, "Start node $startId not in graph — returning empty path")
            return emptyList()
        }
        if (!graph.nodes.containsKey(endId)) {
            Log.w(TAG, "End node $endId not in graph — returning empty path")
            return emptyList()
        }
        if (startId == endId) {
            return listOf(graph.nodes[startId]!!)
        }

        // dist[nodeId] = best known distance from start
        val dist = mutableMapOf<Long, Double>().withDefault { Double.MAX_VALUE }
        // prev[nodeId] = predecessor node ID on the best path
        val prev = mutableMapOf<Long, Long>()

        dist[startId] = 0.0

        // Min-heap: (nodeId, distanceFromStart)
        val queue = PriorityQueue<Pair<Long, Double>>(compareBy { it.second })
        queue.add(Pair(startId, 0.0))

        val visited = mutableSetOf<Long>()

        while (queue.isNotEmpty()) {
            val (currentId, currentDist) = queue.poll()!!

            if (currentId in visited) continue
            visited.add(currentId)

            if (currentId == endId) break  // early exit

            for (edge in graph.getNeighbours(currentId)) {
                // Static GeoJSON hazard edges are skipped entirely (infinite cost)
                if (edge.isHazardous) continue

                var penalty = 1.0

                // Dynamic hazards from Room (community / live events)
                val destNode = graph.nodes[edge.toId]
                if (destNode != null) {
                    for (hazard in activeHazards) {
                        val distToHazard = GeoUtils.distanceMeters(
                            destNode.lat, destNode.lon, hazard.latitude, hazard.longitude
                        )
                        if (distToHazard < 500) { // 500m radius
                            val typePenalty = when (hazard.type) {
                                "FLOOD" -> 500.0
                                "LANDSLIDE" -> 1000.0
                                "FIRE" -> 800.0
                                "ROAD_BLOCK", "BUILDING_COLLAPSE" -> Double.POSITIVE_INFINITY
                                else -> BASE_HAZARD_PENALTY
                            }

                            val severityMultiplier = when (hazard.severity) {
                                "CRITICAL", "IMMINENT" -> 2.0
                                "HIGH", "ALERT" -> 1.5
                                "MODERATE" -> 1.0
                                else -> 0.5
                            }

                            penalty += (typePenalty * severityMultiplier)
                        }
                    }
                }

                if (!penalty.isFinite()) continue

                val neighbourId = edge.toId
                if (neighbourId in visited) continue

                val newDist = currentDist + (edge.weightMeters * penalty)
                if (newDist < dist.getValue(neighbourId)) {
                    dist[neighbourId] = newDist
                    prev[neighbourId] = currentId
                    queue.add(Pair(neighbourId, newDist))
                }
            }
        }

        // Check if destination was reached
        if (!prev.containsKey(endId) && startId != endId) {
            Log.w(TAG, "No safe path from $startId to $endId — all paths may be hazardous or disconnected")
            return emptyList()
        }

        // Reconstruct path by walking prev map backwards
        val path = mutableListOf<Node>()
        var current: Long? = endId
        while (current != null) {
            graph.nodes[current]?.let { path.add(0, it) }
            current = prev[current]
        }

        Log.d(TAG, "Path found: ${path.size} nodes, total dist=${dist.getValue(endId).toInt()}m")
        return path
    }
}
