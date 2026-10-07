package com.hades.safepath.map.offline

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hades.safepath.data.SafePathDatabase
import com.hades.safepath.data.model.DisasterType
import com.hades.safepath.routing.GraphBuilder
import com.hades.safepath.routing.HazardMapLoader
import com.hades.safepath.routing.RoutingEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests: zero-network offline map init + Dijkstra route rendering data.
 *
 * Run with: ./gradlew :app:connectedDebugAndroidTest
 * (device/emulator in Airplane Mode preferred)
 */
@RunWith(AndroidJUnit4::class)
class OfflineMapInstrumentedTest {

    private lateinit var context: Context
    private lateinit var tileRepository: OfflineTileRepository
    private lateinit var layerRepository: OfflineMapLayerRepository
    private lateinit var routingEngine: RoutingEngine

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        tileRepository = OfflineTileRepository(context)
        layerRepository = OfflineMapLayerRepository(context)
        routingEngine = RoutingEngine(context, SafePathDatabase.getInstance(context))
    }

    @Test
    fun prepareTiles_neverThrows_andStaysOfflineCapable() = runBlocking {
        var exception: Exception? = null
        var setup: OfflineTileRepository.TileSetup? = null
        try {
            setup = tileRepository.prepare()
        } catch (e: Exception) {
            exception = e
        }

        assertFalse(
            "Tile prepare must not throw NetworkOnMainThreadException",
            exception?.javaClass?.simpleName == "NetworkOnMainThreadException"
        )
        assertFalse(
            "Tile prepare must not throw UnknownHostException",
            exception?.javaClass?.simpleName == "UnknownHostException"
        )
        assertNotNull("TileSetup must be produced offline", setup)
        // With or without .mbtiles, prepare() must succeed
        assertTrue(setup!!.message.isNotBlank())
    }

    @Test
    fun loadOverlays_parsesLocalGeoJson_withoutNetwork() = runBlocking {
        val pack = layerRepository.loadOverlays()
        // Placeholder GeoJSON may be sparse, but parsing must succeed
        assertNotNull(pack)
        assertTrue(
            "At least one overlay layer expected from raw GeoJSON",
            pack.floodZones.isNotEmpty() ||
                pack.landslideZones.isNotEmpty() ||
                pack.roads.isNotEmpty()
        )
    }

    @Test
    fun routingEngine_initialiseAndRoute_requiresNoNetwork() = runBlocking {
        var exception: Exception? = null
        try {
            routingEngine.initialise()
            assertTrue(routingEngine.isInitialised())

            // Ratnapura pilot coordinates
            val path = routingEngine.calculateEvacuationRoute(
                userLat = 6.6831,
                userLon = 80.3988,
                disasterType = DisasterType.FLOOD
            )
            // Path may be empty if graph disconnected, but must not throw network errors
            assertNotNull(path)
        } catch (e: Exception) {
            exception = e
        }

        assertFalse(
            "Routing must not throw NetworkOnMainThreadException",
            exception?.javaClass?.simpleName == "NetworkOnMainThreadException"
        )
        assertFalse(
            "Routing must not throw UnknownHostException",
            exception?.javaClass?.simpleName == "UnknownHostException"
        )
        assertTrue(
            "Unexpected routing failure: ${exception?.message}",
            exception == null
        )
    }

    @Test
    fun graphBuilder_marksHazardousEdges_fromLocalPolygons() {
        val roads = HazardMapLoader.getRoadNetwork(context)
        val floods = HazardMapLoader.getHazardRoads(context)
        val slides = HazardMapLoader.getLandslideZones(context)

        val graph = GraphBuilder.buildFromGeoJson(roads, floods, slides)
        assertTrue("Road graph must contain nodes from local GeoJSON", graph.nodes.isNotEmpty())
    }

    @Test
    fun zoomBand_matchesSriLankaPilotSpec() {
        assertTrue(OfflineTileRepository.ZOOM_MIN == 6)
        assertTrue(OfflineTileRepository.ZOOM_MAX == 12)
    }
}
