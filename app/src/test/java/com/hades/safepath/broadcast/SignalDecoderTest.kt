package com.hades.safepath.broadcast

import com.hades.safepath.data.model.DisasterType
import com.hades.safepath.data.model.SeverityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

class SignalDecoderTest {

    // -------------------------------------------------------------------------
    // Named single-case tests
    // -------------------------------------------------------------------------

    @Test
    fun testValidFloodImminent() {
        val event = SignalDecoder.decode("113")
        assertNotNull(event)
        assertEquals(DisasterType.FLOOD, event!!.disasterType)
        assertEquals(SeverityLevel.IMMINENT, event.severity)
        assertEquals(113, event.rawCode)
    }

    @Test
    fun testValidLandslideAlert() {
        val event = SignalDecoder.decode("122")
        assertNotNull(event)
        assertEquals(DisasterType.LANDSLIDE, event!!.disasterType)
        assertEquals(SeverityLevel.ALERT, event.severity)
        assertEquals(122, event.rawCode)
    }

    @Test
    fun testInvalidCode() {
        assertNull(SignalDecoder.decode("999"))
    }

    @Test
    fun testNonNumeric() {
        assertNull(SignalDecoder.decode("abc"))
    }

    @Test
    fun testEmptyString() {
        assertNull(SignalDecoder.decode(""))
    }

    @Test
    fun testCodeZero() {
        assertNull(SignalDecoder.decode("000"))
    }

    @Test
    fun testInvalidSeverityDigit() {
        // Type 11 (FLOOD) is valid, but severity 4 is not defined
        assertNull(SignalDecoder.decode("114"))
    }

    @Test
    fun testWhitespaceIsTrimmed() {
        val event = SignalDecoder.decode("  111  ")
        assertNotNull(event)
        assertEquals(DisasterType.FLOOD, event!!.disasterType)
        assertEquals(SeverityLevel.ADVISORY, event.severity)
    }
}

// -------------------------------------------------------------------------
// Parameterized test — all nine valid Signal_Codes (R11 criterion 5)
// -------------------------------------------------------------------------

@RunWith(Parameterized::class)
class SignalDecoderAllValidCodesTest(
    private val code: Int,
    private val expectedType: DisasterType,
    private val expectedSeverity: SeverityLevel
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "code={0} → {1}/{2}")
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf(111, DisasterType.FLOOD,      SeverityLevel.ADVISORY),
            arrayOf(112, DisasterType.FLOOD,      SeverityLevel.ALERT),
            arrayOf(113, DisasterType.FLOOD,      SeverityLevel.IMMINENT),
            arrayOf(121, DisasterType.LANDSLIDE,  SeverityLevel.ADVISORY),
            arrayOf(122, DisasterType.LANDSLIDE,  SeverityLevel.ALERT),
            arrayOf(123, DisasterType.LANDSLIDE,  SeverityLevel.IMMINENT),
            arrayOf(131, DisasterType.CYCLONE,    SeverityLevel.ADVISORY),
            arrayOf(132, DisasterType.CYCLONE,    SeverityLevel.ALERT),
            arrayOf(133, DisasterType.CYCLONE,    SeverityLevel.IMMINENT)
        )
    }

    @Test
    fun testAllNineCodes() {
        val event = SignalDecoder.decode(code.toString())
        assertNotNull("Expected non-null for code $code", event)
        assertEquals("DisasterType mismatch for code $code", expectedType, event!!.disasterType)
        assertEquals("SeverityLevel mismatch for code $code", expectedSeverity, event.severity)
        assertEquals("rawCode mismatch for code $code", code, event.rawCode)
    }
}
