package com.hades.safepath.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wraps [FusedLocationProviderClient] with coroutine-friendly APIs.
 *
 * All methods are safe to call from any coroutine context.
 * Permission checks are the caller's responsibility before invoking
 * [startLiveUpdates] or [getLastKnownLocation].
 *
 * @param context     Android context used for permission checks and looper access.
 * @param fusedClient Optional override for the [FusedLocationProviderClient] —
 *                    inject a mock in unit tests to avoid the GMS static call.
 */
class SafePathLocationManager(
    private val context: Context,
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
) {

    private val TAG = "SafePathLocationManager"

    // Held so stopLiveUpdates() can remove the same callback instance
    private var activeCallback: LocationCallback? = null

    companion object {
        private const val ACCURACY_THRESHOLD_METRES = 50f

        /**
         * Shared [LocationState] observable for ViewModel consumption.
         *
         * Starts as [LocationState.Loading].
         * Updated by [startLiveUpdates] on every location result:
         *   - accuracy ≤ 50m → [LocationState.Available]
         *   - accuracy > 50m → [LocationState.LocationAccuracyWarning]
         *
         * Also updated to [LocationState.PermissionDenied] or
         * [LocationState.LocationFallback] by the owning Service/ViewModel
         * as appropriate.
         */
        val locationState: MutableStateFlow<LocationState> =
            MutableStateFlow(LocationState.Loading)

        /** Read-only view for external observers (ViewModels). */
        val locationStateFlow: StateFlow<LocationState> = locationState.asStateFlow()
    }

    // -------------------------------------------------------------------------
    // Permission check
    // -------------------------------------------------------------------------

    /**
     * Returns true if both ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION
     * are currently granted.
     */
    fun checkPermissionsGranted(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine && coarse
    }

    // -------------------------------------------------------------------------
    // Last known location
    // -------------------------------------------------------------------------

    /**
     * Returns the last known [Location] from the fused provider, or null if
     * unavailable or permissions are not granted.
     *
     * Wraps the GMS Task in [suspendCancellableCoroutine] so it integrates
     * cleanly with coroutine callers.
     */
    @Suppress("MissingPermission")   // Caller must check checkPermissionsGranted() first
    suspend fun getLastKnownLocation(): Location? {
        if (!checkPermissionsGranted()) {
            Log.d(TAG, "getLastKnownLocation: permissions not granted — returning null")
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    Log.d(TAG, "getLastKnownLocation: $location")
                    continuation.resume(location)
                }
                .addOnFailureListener { e ->
                    Log.d(TAG, "getLastKnownLocation failed: ${e.message}")
                    continuation.resume(null)
                }

            continuation.invokeOnCancellation {
                // GMS Tasks cannot be cancelled; nothing to clean up here
            }
        }
    }

    // -------------------------------------------------------------------------
    // Live updates
    // -------------------------------------------------------------------------

    /**
     * Returns a [Flow] that emits [Location] updates at [intervalMs] milliseconds.
     *
     * Uses [callbackFlow] so the [LocationCallback] is automatically removed
     * when the collecting coroutine is cancelled.
     *
     * @param intervalMs  Minimum update interval in milliseconds (default 5 000 ms).
     */
    @Suppress("MissingPermission")   // Caller must check checkPermissionsGranted() first
    fun startLiveUpdates(intervalMs: Long = 5_000L): Flow<Location> = callbackFlow {
        if (!checkPermissionsGranted()) {
            Log.d(TAG, "startLiveUpdates: permissions not granted — closing flow")
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val accuracy = location.accuracy

                    if (accuracy <= ACCURACY_THRESHOLD_METRES) {
                        // Good fix — emit normally
                        Log.d(TAG, "Location update: lat=${location.latitude} lon=${location.longitude} acc=${accuracy}m")
                        locationState.value = LocationState.Available(location, accuracy)
                    } else {
                        // Poor fix — warn but still emit (ViewModel decides what to show)
                        Log.w(TAG, "Low accuracy fix: ${accuracy}m")
                        locationState.value = LocationState.LocationAccuracyWarning(location, accuracy)
                    }

                    // Always forward the raw Location to Flow<Location> callers
                    trySend(location)
                }
            }
        }

        activeCallback = callback

        fusedClient.requestLocationUpdates(request, callback, context.mainLooper)
            .addOnFailureListener { e ->
                Log.d(TAG, "requestLocationUpdates failed: ${e.message}")
                close(e)
            }

        awaitClose {
            Log.d(TAG, "startLiveUpdates: flow cancelled — removing callback")
            fusedClient.removeLocationUpdates(callback)
            activeCallback = null
        }
    }

    // -------------------------------------------------------------------------
    // Stop updates
    // -------------------------------------------------------------------------

    /**
     * Removes the active location callback if one is registered.
     * Prefer cancelling the collecting coroutine scope; call this only for
     * imperative teardown (e.g. from a Service.onDestroy()).
     */
    fun stopLiveUpdates() {
        activeCallback?.let {
            fusedClient.removeLocationUpdates(it)
            activeCallback = null
            Log.d(TAG, "stopLiveUpdates: callback removed")
        }
    }

    // -------------------------------------------------------------------------
    // Accuracy helper
    // -------------------------------------------------------------------------

    /**
     * Returns the horizontal accuracy of [location] in metres.
     * Delegates directly to [Location.accuracy].
     */
    fun getLocationAccuracyMeters(location: Location): Float = location.accuracy
}
