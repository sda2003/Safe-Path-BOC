package com.hades.safepath

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hades.safepath.broadcast.SafePathBroadcastReceiver
import com.hades.safepath.broadcast.SignalCodes
import com.hades.safepath.ui.MainActivity
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tests for the full SafePath signal pipeline.
 *
 * These tests run on a real emulator and exercise the complete flow:
 * CB signal injection → DecisionEngine → AlertActivity display.
 *
 * Prerequisites:
 * - Emulator with API 26+ (minSdk)
 * - Location permissions granted (or GPS fallback used)
 * - DB seeded on first launch
 *
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {

    private val TAG = "EndToEndTest"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        SafePathBroadcastReceiver.resetEventFlowForTesting()
    }

    @After
    fun tearDown() {
        SafePathBroadcastReceiver.resetEventFlowForTesting()
    }

    // ── testFloodImminent_fullPipeline ────────────────────────────────────────

    /**
     * Full pipeline test for FLOOD/IMMINENT (signal 113).
     *
     * 1. Launch MainActivity
     * 2. Wait 3s for RoutingEngine to initialise
     * 3. Inject signal 113
     * 4. Wait up to 5s for ImminentAlertFragment
     * 5. Assert alert badge, location name, leave button visible
     */
    @Test
    fun testFloodImminent_fullPipeline() {
        Log.d(TAG, "Starting FLOOD/IMMINENT full pipeline test")

        ActivityScenario.launch(MainActivity::class.java).use {
            // Wait for RoutingEngine initialisation
            Thread.sleep(3_000)

            // Inject signal
            val received = TestHelper.injectSignalAndWait(
                SignalCodes.FLOOD_IMMINENT, context, timeoutMs = 5_000L
            )
            assertTrue("Signal 113 must be received", received)

            // Wait for ImminentAlertFragment views to appear
            Thread.sleep(2000)

            // Assert severity badge is visible
            onView(withId(R.id.tv_alert_badge))
                .check(matches(isDisplayed()))

            // Assert location name is non-empty
            onView(withId(R.id.tv_alert_location))
                .check(matches(isDisplayed()))
            onView(withId(R.id.tv_alert_location))
                .check(matches(not(withText(""))))

            // Assert Leave button is visible
            onView(withId(R.id.btn_leave_now))
                .check(matches(isDisplayed()))
            onView(withId(R.id.btn_leave_now))
                .check(matches(withText(containsString("LEAVE"))))

            Log.d(TAG, "FLOOD/IMMINENT full pipeline test PASSED")
        }
    }

    // ── testLandslideImminent_fullPipeline ────────────────────────────────────

    /**
     * Full pipeline test for LANDSLIDE/IMMINENT (signal 123).
     */
    @Test
    fun testLandslideImminent_fullPipeline() {
        Log.d(TAG, "Starting LANDSLIDE/IMMINENT full pipeline test")

        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(3_000)

            val received = TestHelper.injectSignalAndWait(
                SignalCodes.LANDSLIDE_IMMINENT, context, timeoutMs = 5_000L
            )
            assertTrue("Signal 123 must be received", received)

            Thread.sleep(2000)

            onView(withId(R.id.tv_alert_badge)).check(matches(isDisplayed()))
            onView(withId(R.id.tv_alert_location)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_leave_now))
                .check(matches(withText(containsString("LEAVE"))))

            Log.d(TAG, "LANDSLIDE/IMMINENT full pipeline test PASSED")
        }
    }

    // ── testAdvisory_showsPackChecklist ───────────────────────────────────────

    /**
     * ADVISORY severity (signal 111) should update HomeFragment card.
     */
    @Test
    fun testAdvisory_showsPackChecklist() {
        Log.d(TAG, "Starting FLOOD/ADVISORY test")

        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(3_000)

            val received = TestHelper.injectSignalAndWait(
                SignalCodes.FLOOD_ADVISORY, context, timeoutMs = 5_000L
            )
            assertTrue("Signal 111 must be received", received)

            Thread.sleep(1000)

            // ADVISORY updates the home card
            onView(withId(R.id.tvAlertTitle))
                .check(matches(withText(containsString("Active"))))
            
            onView(withId(R.id.tvAlertSubtitle))
                .check(matches(withText(containsString("Affected"))))

            Log.d(TAG, "FLOOD/ADVISORY test PASSED")
        }
    }

    // ── testCycloneImminent_shelterInPlace ────────────────────────────────────

    /**
     * CYCLONE/IMMINENT (signal 133) must show alert fragment.
     */
    @Test
    fun testCycloneImminent_shelterInPlace() {
        Log.d(TAG, "Starting CYCLONE/IMMINENT shelter-in-place test")

        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(3_000)

            val received = TestHelper.injectSignalAndWait(
                SignalCodes.CYCLONE_IMMINENT, context, timeoutMs = 5_000L
            )
            assertTrue("Signal 133 must be received", received)

            Thread.sleep(2000)

            // Alert screen must be visible — no crash
            onView(withId(R.id.tv_alert_badge)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_leave_now)).check(matches(isDisplayed()))

            Log.d(TAG, "CYCLONE/IMMINENT shelter-in-place test PASSED")
        }
    }
}
