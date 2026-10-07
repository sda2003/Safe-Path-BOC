package com.hades.safepath.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [Dijkstra].
 *
 * Graph topology used in path tests:
 *
 *   1 --10m-- 2 --10m-- 3
 *   |                   |
 *  30m                 10m
 *   |                   |
 *   4 ------30m-------- 5
 *
 * Shortest safe path 1→2→3→5 = 30m
 * Alternate safe path 1→4→5  = 60m
 */
class DijkstraTest {

    private val N1 = 1L
    private val N2 = 2L
    private val N3 = 3L
    private val N4 = 4L
    private val N5 = 5L

    private lateinit var graph: RoadGraph

    @Before
    fun setUp() {
        graph = RoadGraph()
        graph.addNode(Node(N1, 6.680, 80.390))
        graph.addNode(Node(N2, 6.681, 80.391))
        graph.addNode(Node(N3, 6.682, 80.392))
        graph.addNode(Node(N4, 6.680, 80.393))
        graph.addNode(Node(N5, 6.682, 80.394))
    }

    private fun safe(from: Long, to: Long, w: Double) =
        graph.addEdge(Edge(from, to, w, isHazardous = false))

    private fun hazard(from: Long, to: Long, w: Double) =
        graph.addEdge(Edge(from, to, w, isHazardous = true))

    // -------------------------------------------------------------------------
    // testFindsShortestPath
    // -------------------------------------------------------------------------

    /**
     * All edges safe — Dijkstra must return the shortest path 1→2→3→5 (30m),
     * not the longer 1→4→5 (60m).
     */
    @Test
    fun testFindsShortestPath() {
        safe(N1, N2, 10.0)
        safe(N2, N3, 10.0)
        safe(N3, N5, 10.0)
        safe(N1, N4, 30.0)
        safe(N4, N5, 30.0)

        val path = Dijkstra.shortestPath(graph, N1, N5)

        assertEquals("Path should have 4 nodes", 4, path.size)
        assertEquals("Start node", N1, path.first().id)
        assertEquals("End node", N5, path.last().id)
        assertEquals("Second node should be 2", N2, path[1].id)
        assertEquals("Third node should be 3", N3, path[2].id)
    }

    // -------------------------------------------------------------------------
    // testAvoidsHazardousEdges
    // -------------------------------------------------------------------------

    /**
     * Direct path 1→2→3→5 is blocked by hazardous edges.
     * Dijkstra MUST return the longer safe detour 1→4→5.
     *
     * Verifies R3 criterion 7: hazardous edges are SKIPPED entirely,
     * not penalised with a higher weight.
     */
    @Test
    fun testAvoidsHazardousEdges() {
        hazard(N1, N2, 10.0)   // direct path is hazardous
        hazard(N2, N3, 10.0)
        safe(N3, N5, 10.0)
        safe(N1, N4, 30.0)     // safe detour
        safe(N4, N5, 30.0)

        val path = Dijkstra.shortestPath(graph, N1, N5)

        assertTrue("Safe detour must be found", path.isNotEmpty())
        assertEquals("Start node", N1, path.first().id)
        assertEquals("End node", N5, path.last().id)

        val ids = path.map { it.id }
        assertTrue("Hazardous node 2 must NOT appear in path", N2 !in ids)
        assertTrue("Hazardous node 3 must NOT appear in path", N3 !in ids)
        assertTrue("Safe node 4 MUST appear in path", N4 in ids)
    }

    // -------------------------------------------------------------------------
    // testNoPathReturnsEmpty
    // -------------------------------------------------------------------------

    /**
     * When the only available path is hazardous, Dijkstra must return emptyList().
     */
    @Test
    fun testNoPathReturnsEmpty() {
        hazard(N1, N2, 10.0)
        hazard(N2, N5, 10.0)
        // No safe alternative exists

        val path = Dijkstra.shortestPath(graph, N1, N5)

        assertTrue("Must return empty list when all paths are hazardous", path.isEmpty())
    }

    /**
     * When the destination is completely disconnected, return emptyList().
     */
    @Test
    fun testDisconnectedGraphReturnsEmpty() {
        safe(N1, N2, 10.0)
        // N5 has no edges — unreachable

        val path = Dijkstra.shortestPath(graph, N1, N5)

        assertTrue("Must return empty list when destination is unreachable", path.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    fun testStartEqualsEnd() {
        safe(N1, N2, 10.0)

        val path = Dijkstra.shortestPath(graph, N1, N1)

        assertEquals("Path from node to itself should have 1 node", 1, path.size)
        assertEquals(N1, path.first().id)
    }

    @Test
    fun testUnknownStartReturnsEmpty() {
        safe(N1, N2, 10.0)
        assertTrue(Dijkstra.shortestPath(graph, 999L, N2).isEmpty())
    }

    @Test
    fun testUnknownEndReturnsEmpty() {
        safe(N1, N2, 10.0)
        assertTrue(Dijkstra.shortestPath(graph, N1, 999L).isEmpty())
    }
}
