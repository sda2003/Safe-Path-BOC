package com.hades.safepath

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hades.safepath.broadcast.SafePathBroadcastReceiver
import com.hades.safepath.broadcast.SignalCodes
import com.hades.safepath.data.model.DisasterType
import com.hades.safepath.data.model.SeverityLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests all 9 valid SafePath signal codes (111-133).
 *
 * For each code:
 * - Injects via CellBroadcastSimulator
 * - Verifies the event is received within 10 seconds
 * - Verifies DisasterType and SeverityLevel match expected values
 *
 * R11 criterion 4: all nine valid Signal_Codes must map to a defined response.
 *
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class SignalPipelineTest {

    private val TAG = "SignalPipelineTest"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        SafePathBroadcastReceiver.resetEventFlowForTesting()
    }

    @After
    fun tearDown() {
        SafePathBroadcastReceiver.resetEventFlowForTesting()
    }

    @Test
    fun test111_floodAdvisory() {
        verifySignal(SignalCodes.FLOOD_ADVISORY, DisasterType.FLOOD, SeverityLevel.ADVISORY)
    }

    @Test
    fun test112_floodAlert() {
        verifySignal(SignalCodes.FLOOD_ALERT, DisasterType.FLOOD, SeverityLevel.ALERT)
    }

    @Test
    fun test113_floodImminent() {
        verifySignal(SignalCodes.FLOOD_IMMINENT, DisasterType.FLOOD, SeverityLevel.IMMINENT)
    }

    @Test
    fun test121_landslideAdvisory() {
        verifySignal(SignalCodes.LANDSLIDE_ADVISORY, DisasterType.LANDSLIDE, SeverityLevel.ADVISORY)
    }

    @Test
    fun test122_landslideAlert() {
        verifySignal(SignalCodes.LANDSLIDE_ALERT, DisasterType.LANDSLIDE, SeverityLevel.ALERT)
    }

    @Test
    fun test123_landslideImminent() {
        verifySignal(SignalCodes.LANDSLIDE_IMMINENT, DisasterType.LANDSLIDE, SeverityLevel.IMMINENT)
    }

    @Test
    fun test131_cycloneAdvisory() {
        verifySignal(SignalCodes.CYCLONE_ADVISORY, DisasterType.CYCLONE, SeverityLevel.ADVISORY)
    }

    @Test
    fun test132_cycloneAlert() {
        verifySignal(SignalCodes.CYCLONE_ALERT, DisasterType.CYCLONE, SeverityLevel.ALERT)
    }

    @Test
    fun test133_cycloneImminent() {
        verifySignal(SignalCodes.CYCLONE_IMMINENT, DisasterType.CYCLONE, SeverityLevel.IMMINENT)
    }

    private fun verifySignal(code: Int, expectedType: DisasterType, expectedSeverity: SeverityLevel) {
        Log.d(TAG, "Testing signal $code -> $expectedType/$expectedSeverity")

        val received = TestHelper.injectSignalAndWait(
            signalCode = code,
            context = context,
            timeoutMs = 10_000L
        )

        assertTrue("Signal $code must be received within 10 seconds", received)

        val event = SafePathBroadcastReceiver.eventFlow.value
        assertNotNull("eventFlow must be non-null after signal $code", event)
        assertEquals("DisasterType mismatch for signal $code", expectedType, event!!.disasterType)
        assertEquals("SeverityLevel mismatch for signal $code", expectedSeverity, event.severity)
        assertEquals("rawCode mismatch for signal $code", code, event.rawCode)

        Log.d(TAG, "Signal $code PASSED: ${event.disasterType}/${event.severity}")
    }
}
