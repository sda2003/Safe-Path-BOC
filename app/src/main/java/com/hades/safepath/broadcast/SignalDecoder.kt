package com.hades.safepath.broadcast

import android.util.Log
import com.hades.safepath.data.model.DisasterEvent
import com.hades.safepath.data.model.DisasterType
import com.hades.safepath.data.model.SeverityLevel

/**
 * Decodes a raw SafePath 3-digit signal string into a [DisasterEvent].
 *
 * Encoding scheme:
 *   Disaster type  = floor(code / 10): 11=FLOOD, 12=LANDSLIDE, 13=CYCLONE, 14=DROUGHT
 *   Severity level = code mod 10:      1=ADVISORY, 2=ALERT, 3=IMMINENT
 *
 * [android.util.Log] calls are no-ops in JVM unit tests when
 * `testOptions { unitTests.isReturnDefaultValues = true }` is set in build.gradle.kts.
 */
object SignalDecoder {

    private const val TAG = "SignalDecoder"

    fun decode(rawMessage: String): DisasterEvent? {
        log("Decode attempt: '$rawMessage'")

        val code = rawMessage.trim().toIntOrNull()
        if (code == null) {
            log("Decode failed — not a valid integer: '$rawMessage'")
            return null
        }

        // Must be exactly 3 digits (100–999)
        if (code < 100 || code > 999) {
            log("Decode failed — out of 3-digit range: $code")
            return null
        }

        val typeCode = code / 10
        val severityCode = code % 10

        val disasterType = when (typeCode) {
            11 -> DisasterType.FLOOD
            12 -> DisasterType.LANDSLIDE
            13 -> DisasterType.CYCLONE
            14 -> DisasterType.DROUGHT
            else -> {
                log("Decode failed — unknown disaster type code: $typeCode")
                return null
            }
        }

        val severity = when (severityCode) {
            1 -> SeverityLevel.ADVISORY
            2 -> SeverityLevel.ALERT
            3 -> SeverityLevel.IMMINENT
            else -> {
                log("Decode failed — unknown severity code: $severityCode")
                return null
            }
        }

        val event = DisasterEvent(
            disasterType = disasterType,
            severity = severity,
            rawCode = code
        )
        log("Decode success: $event")
        return event
    }

    // Wraps android.util.Log so the call site is mockable in unit tests
    // (isReturnDefaultValues = true makes Log.d return 0 instead of throwing).
    private fun log(msg: String) {
        Log.d(TAG, msg)
    }
}
