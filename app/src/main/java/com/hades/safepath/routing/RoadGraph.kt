package com.hades.safepath.routing

import android.util.Log

/** A single geographic node in the road network. */
data class Node(
    val id: Long,
    val lat: Double,
    val lon: Double
)

/**
 * A directed edge between two nodes.
 *
 * @param fromId       Source node ID.
 * @param toId         Destination node ID.
 * @param weightMeters Haversine distance in metres.
 * @param isHazardous  True if this segment intersects a flood or landslide zone.
 *                     Hazardous edges are SKIPPED entirely by the routing engine —
 *                     they are never traversed regardless of weight.
 */
data class Edge(
    val fromId: Long,
    val toId: Long,
    val weightMeters: Double,
    val isHazardous: Boolean
)

/**
 * In-memory road graph for the SafePath routing engine.
 *
 * Nodes are keyed by a coordinate-derived Long ID.
 * Edges are stored as a bidirectional adjacency list.
 */
class RoadGraph {

    private val TAG = "RoadGraph"

    val nodes: MutableMap<Long, Node> = mutableMapOf()
    val adjacency: MutableMap<Long, MutableList<Edge>> = mutableMapOf()

    /** Adds a node to the graph. Silently replaces if the ID already exists. */
    fun addNode(node: Node) {
        nodes[node.id] = node
    }

    /**
     * Adds a bidirectional edge (from→to AND to→from).
     * Both directions share the same weight and hazard flag.
     */
    fun addEdge(edge: Edge) {
        adjacency.getOrPut(edge.fromId) { mutableListOf() }.add(edge)
        // Reverse direction
        adjacency.getOrPut(edge.toId) { mutableListOf() }.add(
            Edge(
                fromId = edge.toId,
                toId = edge.fromId,
                weightMeters = edge.weightMeters,
                isHazardous = edge.isHazardous
            )
        )
    }

    /** Returns all outgoing edges from [nodeId], or an empty list if none. */
    fun getNeighbours(nodeId: Long): List<Edge> =
        adjacency[nodeId] ?: emptyList()

    /**
     * Finds the node ID nearest to (lat, lon) using a linear scan.
     *
     * Acceptable for prototype graph sizes (≤10 000 nodes).
     * Returns -1L if the graph is empty.
     */
    fun findNearestNodeId(lat: Double, lon: Double): Long {
        if (nodes.isEmpty()) return -1L

        var nearestId = -1L
        var nearestDist = Double.MAX_VALUE

        nodes.values.forEach { node ->
            val dist = GeoUtils.distanceMeters(lat, lon, node.lat, node.lon)
            if (dist < nearestDist) {
                nearestDist = dist
                nearestId = node.id
            }
        }

        Log.d(TAG, "findNearestNodeId($lat,$lon) → id=$nearestId dist=${nearestDist.toInt()}m")
        return nearestId
    }
}
