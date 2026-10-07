package com.hades.safepath.location

import android.location.Location

/**
 * Represents the current state of the GPS location subsystem.
 * Observed by ViewModels via SafePathLocationManager.
 *
 * State transitions:
 *   Loading → Available | PermissionDenied | Unavailable | LocationFallback
 *   Available → LocationAccuracyWarning (when accuracy > 50m)
 */
sealed class LocationState {

    /** Initial state — waiting for first fix or permission check. */
    object Loading : LocationState()

    /**
     * A valid location fix is available.
     * @param location       The Android [Location] object.
     * @param accuracyMeters Horizontal accuracy radius in metres (from [Location.accuracy]).
     */
    data class Available(
        val location: Location,
        val accuracyMeters: Float
    ) : LocationState()

    /** The user has denied ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION. */
    object PermissionDenied : LocationState()

    /**
     * Location could not be obtained.
     * @param reason Human-readable explanation (e.g. "Timeout after 30s", "GPS disabled").
     */
    data class Unavailable(val reason: String) : LocationState()

    // -------------------------------------------------------------------------
    // R4 criterion 6 — GPS fallback
    // -------------------------------------------------------------------------

    /**
     * Emitted when no GPS fix is available and the app falls back to a
     * pre-configured default coordinate.
     *
     * The ViewModel MUST display a visible warning to the user that a fallback
     * location is in use (R4 criterion 6).
     *
     * Default fallback: Ratnapura town centre (lat 6.6831, lon 80.3988).
     *
     * @param fallbackLat  Latitude of the fallback coordinate.
     * @param fallbackLon  Longitude of the fallback coordinate.
     * @param reason       Human-readable explanation shown to the user,
     *                     e.g. "No GPS fix — using Ratnapura fallback".
     */
    data class LocationFallback(
        val fallbackLat: Double,
        val fallbackLon: Double,
        val reason: String
    ) : LocationState()

    // -------------------------------------------------------------------------
    // R4 criterion 7 — accuracy warning
    // -------------------------------------------------------------------------

    /**
     * Emitted when a location fix is obtained but its horizontal accuracy is
     * worse than 50 metres.
     *
     * The ViewModel MUST surface a warning indicator to the user (R4 criterion 7).
     *
     * @param location       The [Location] fix that triggered the warning.
     * @param accuracyMeters The reported horizontal accuracy in metres (> 50m).
     */
    data class LocationAccuracyWarning(
        val location: Location,
        val accuracyMeters: Float
    ) : LocationState()
}

