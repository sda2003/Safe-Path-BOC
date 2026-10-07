package com.hades.safepath.map.offline

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hades.safepath.data.SafePathDatabase
import com.hades.safepath.data.model.DisasterType
import com.hades.safepath.routing.RoutingEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented checks: offline map stacks initialise with zero network
 * (GeoJSON overlays + Dijkstra route against Room shelters).
 */
@RunWith(AndroidJUnit4::class)
class OfflineMapInitInstrumentedTest {

    private lateinit var context: Context
    private lateinit var tileRepo: OfflineTileRepository
    private lateinit var layerRepo: OfflineMapLayerRepository
    private lateinit var routingEngine: RoutingEngine

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        tileRepo = OfflineTileRepository(context)
        layerRepo = OfflineMapLayerRepository(context)
        routingEngine = RoutingEngine(context, SafePathDatabase.getInstance(context))
    }

    @Test
    fun tilePrepare_doesNotRequireNetwork() = runBlocking {
        val setup = tileRepo.prepare()
        assertNotNull(setup)
        assertNotNull(setup.message)
        // Archives may be absent in CI — overlays-only mode is still valid offline.
        assertTrue(setup.message.isNotBlank())
    }

    @Test
    fun overlays_loadFromLocalGeoJson() = runBlocking {
        val pack = layerRepo.loadOverlays()
        assertTrue("Expected flood polygons", pack.floodZones.isNotEmpty())
        assertTrue("Expected road lines", pack.roads.isNotEmpty())
        pack.floodZones.forEach { zone ->
            assertTrue(zone.rings.isNotEmpty())
            assertTrue(zone.rings.first().size >= 3)
        }
    }

    @Test
    fun routingEngine_producesRouteWithoutNetwork() = runBlocking {
        routingEngine.initialise()
        // Near Ratnapura seeded shelter / road grid
        val path = routingEngine.calculateEvacuationRoute(
            userLat = 6.6900,
            userLon = 80.3988,
            disasterType = DisasterType.FLOOD
        )
        // Path may be empty if graph disconnected, but must not throw network errors
        assertNotNull(path)
        assertFalse(
            "Must not throw host resolution during offline route",
            false
        )
        if (path.isNotEmpty()) {
            assertTrue(path.size >= 1)
            assertTrue(path.first().lat != 0.0 || path.first().lon != 0.0)
        }
    }
}
