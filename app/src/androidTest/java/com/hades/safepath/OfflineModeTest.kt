package com.hades.safepath

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hades.safepath.broadcast.SafePathBroadcastReceiver
import com.hades.safepath.broadcast.SignalCodes
import com.hades.safepath.data.SafePathDatabase
import com.hades.safepath.data.model.DisasterType
import com.hades.safepath.data.model.SeverityLevel
import androidx.room.Room
import com.hades.safepath.data.local.AppDatabase
import com.hades.safepath.data.repository.AlertRepository
import com.hades.safepath.data.repository.GuidanceRepository
import com.hades.safepath.data.repository.ShelterRepository
import com.hades.safepath.guidance.DecisionEngine
import com.hades.safepath.location.SafePathLocationManager
import com.hades.safepath.network.SafePathApi
import com.hades.safepath.network.dto.AlertUpdateDto
import com.hades.safepath.network.dto.CloudAlertDto
import com.hades.safepath.network.dto.ShelterStatusDto
import com.hades.safepath.routing.RoutingEngine
import retrofit2.Response
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Offline-first guarantee tests (R12).
 *
 * Verifies that a complete SafePathDecision can be produced with zero
 * outbound network calls, using only on-device resources.
 *
 * R12 criterion 3: dedicated integration test asserting SafePathDecision
 * is produced without NetworkOnMainThreadException or UnknownHostException.
 *
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class OfflineModeTest {

    private val TAG = "OfflineModeTest"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var db: SafePathDatabase
    private lateinit var routingEngine: RoutingEngine
    private lateinit var decisionEngine: DecisionEngine

    @Before
    fun setUp() {
        SafePathBroadcastReceiver.resetEventFlowForTesting()

        db = SafePathDatabase.getInstance(context)
        val appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

        // Client required by AlertRepository constructor; offline tests only use local Room saves.
        val supabase = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://example.supabase.co" },
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "test-anon-key" }
        ) {
            install(Auth)
            install(Postgrest)
        }

        val guidanceRepo = GuidanceRepository(db.guidanceStepDao(), db.packItemDao())
        val alertRepo = AlertRepository(appDb.alertDao(), supabase, offlineSafePathApi())
        val locationManager = SafePathLocationManager(context)

        routingEngine = RoutingEngine(context, db)
        decisionEngine = DecisionEngine(guidanceRepo, routingEngine, locationManager, alertRepo)

        runBlocking {
            try {
                routingEngine.initialise()
                Log.d(TAG, "RoutingEngine initialised successfully")
            } catch (e: Exception) {
                Log.w(TAG, "RoutingEngine initialise failed (expected if GeoJSON empty): ${e.message}")
            }
        }
    }

    @After
    fun tearDown() {
        SafePathBroadcastReceiver.resetEventFlowForTesting()
    }

    /**
     * R12 criterion 1 and 3: complete SafePathDecision from on-device resources only.
     * Must not throw NetworkOnMainThreadException or UnknownHostException.
     */
    @Test
    fun testOfflineMode_floodImminent_producesDecision() = runBlocking {
        Log.d(TAG, "Testing offline SafePathDecision for FLOOD/IMMINENT")

        val event = com.hades.safepath.data.model.DisasterEvent(
            disasterType = DisasterType.FLOOD,
            severity = SeverityLevel.IMMINENT,
            rawCode = SignalCodes.FLOOD_IMMINENT
        )

        var exception: Exception? = null
        var decision: com.hades.safepath.data.model.SafePathDecision? = null

        try {
            decision = decisionEngine.processEvent(event)
        } catch (e: Exception) {
            exception = e
            Log.e(TAG, "processEvent threw: ${e.javaClass.simpleName}: ${e.message}")
        }

        assertFalse(
            "Must not throw NetworkOnMainThreadException",
            exception?.javaClass?.simpleName == "NetworkOnMainThreadException"
        )
        assertFalse(
            "Must not throw UnknownHostException",
            exception?.javaClass?.simpleName == "UnknownHostException"
        )

        assertNotNull(
            "SafePathDecision must be produced offline (exception: ${exception?.message})",
            decision
        )

        decision?.let {
            assertNotNull("Shelter must be non-null", it.shelter)
            assertTrue("Shelter name must be non-empty", it.shelter.name.isNotBlank())
            assertTrue("At least 1 guidance step must be present", it.guidanceSteps.isNotEmpty())
            Log.d(TAG, "Offline decision: shelter=${it.shelter.name}, steps=${it.guidanceSteps.size}")
        }
    }

    /**
     * R12 criterion 4a: CB signal injection requires no network.
     */
    @Test
    fun testOfflineMode_signalInjection_requiresNoNetwork() {
        val received = TestHelper.injectSignalAndWait(
            signalCode = SignalCodes.FLOOD_IMMINENT,
            context = context,
            timeoutMs = 5_000L
        )
        assertTrue("CB signal injection must work without network (R12 criterion 4a)", received)
        assertNotNull("Event must be decoded without network", SafePathBroadcastReceiver.eventFlow.value)
    }

    /**
     * R12 criterion 4c: guidance retrieval requires no network.
     */
    @Test
    fun testOfflineMode_guidanceRetrieval_requiresNoNetwork() = runBlocking {
        val guidanceRepo = GuidanceRepository(db.guidanceStepDao(), db.packItemDao())
        var exception: Exception? = null
        var steps: List<com.hades.safepath.data.entity.GuidanceStep>? = null

        try {
            steps = guidanceRepo.getStepsForEvent("FLOOD", "IMMINENT")
        } catch (e: Exception) {
            exception = e
        }

        assertFalse(
            "Guidance retrieval must not throw NetworkOnMainThreadException",
            exception?.javaClass?.simpleName == "NetworkOnMainThreadException"
        )
        assertNotNull("Steps must be returned without network", steps)
        assertTrue("At least 1 FLOOD/IMMINENT step must be available offline", (steps?.size ?: 0) >= 1)
    }

    /**
     * Shelter lookup requires no network.
     */
    @Test
    fun testOfflineMode_shelterLookup_requiresNoNetwork() = runBlocking {
        val shelterRepo = ShelterRepository(db.shelterDao())
        var exception: Exception? = null
        var shelters: List<com.hades.safepath.data.entity.Shelter>? = null

        try {
            shelters = shelterRepo.getAllShelters()
        } catch (e: Exception) {
            exception = e
        }

        assertFalse(
            "Shelter lookup must not throw NetworkOnMainThreadException",
            exception?.javaClass?.simpleName == "NetworkOnMainThreadException"
        )
        assertNotNull("Shelters must be returned without network", shelters)
        assertTrue("At least 1 shelter must be available offline", (shelters?.size ?: 0) >= 1)
    }

    private fun offlineSafePathApi(): SafePathApi = object : SafePathApi {
        override suspend fun getLiveShelterStatus(district: String): Response<List<ShelterStatusDto>> =
            Response.success(emptyList())

        override suspend fun getLatestAlerts(district: String): Response<List<AlertUpdateDto>> =
            Response.success(emptyList())

        override suspend fun postAlert(body: CloudAlertDto): Response<CloudAlertDto> =
            Response.success(body)

        override suspend fun getLatestCloudAlerts(
            district: String?,
            limit: Int
        ): Response<List<CloudAlertDto>> = Response.success(emptyList())
    }
}
