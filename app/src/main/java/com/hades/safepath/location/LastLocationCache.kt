package com.hades.safepath.location

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the last known coordinates for offline I'm Safe broadcasts.
 */
@Singleton
class LastLocationCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationManager: SafePathLocationManager
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun getLastKnownCoordinates(): Pair<Double, Double> {
        val live = locationManager.getLastKnownLocation()
        if (live != null) {
            save(live.latitude, live.longitude)
            return live.latitude to live.longitude
        }
        val lat = prefs.getFloat(KEY_LAT, Float.NaN)
        val lon = prefs.getFloat(KEY_LON, Float.NaN)
        if (!lat.isNaN() && !lon.isNaN()) {
            val dLat = lat.toDouble()
            val dLon = lon.toDouble()
            // Older builds cached shelter centre as "last location", which breaks routing.
            if (
                kotlin.math.abs(dLat - LEGACY_SHELTER_LAT) < 0.00015 &&
                kotlin.math.abs(dLon - LEGACY_SHELTER_LON) < 0.00015
            ) {
                save(DEMO_LAT, DEMO_LON)
                return DEMO_LAT to DEMO_LON
            }
            return dLat to dLon
        }
        // Demo start on Ratnapura road grid (NE corner) — NOT the shelter coords.
        // Shelter centre (6.6831, 80.3988) collapses Dijkstra to a 1-node path.
        return DEMO_LAT to DEMO_LON
    }

    fun save(latitude: Double, longitude: Double) {
        prefs.edit()
            .putFloat(KEY_LAT, latitude.toFloat())
            .putFloat(KEY_LON, longitude.toFloat())
            .apply()
    }

    companion object {
        private const val PREFS = "safepath_last_location"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val DEMO_LAT = 6.6950
        private const val DEMO_LON = 80.4050
        private const val LEGACY_SHELTER_LAT = 6.6831
        private const val LEGACY_SHELTER_LON = 80.3988
    }
}
