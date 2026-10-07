package com.hades.safepath.routing

import android.content.Context
import android.util.Log
import com.hades.safepath.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Loads GeoJSON hazard and shelter data from res/raw/ resources.
 *
 * All files are placeholder data for the Ratnapura/Kegalle/Kalutara pilot
 * districts. Replace with authoritative NBRO/DMC data before production.
 */
object HazardMapLoader {

    private const val TAG = "HazardMapLoader"

    /**
     * Reads a GeoJSON FeatureCollection from a raw resource and returns its
     * "features" array.
     *
     * @param context       Android context for resource access.
     * @param rawResourceId The R.raw.* resource ID to load.
     * @return              The "features" [JSONArray] from the FeatureCollection.
     * @throws IOException  If the resource is missing, unreadable, or not a
     *                      valid GeoJSON FeatureCollection.
     */
    @Throws(IOException::class)
    fun loadFeatures(context: Context, rawResourceId: Int): JSONArray {
        val resourceName = try {
            context.resources.getResourceEntryName(rawResourceId)
        } catch (e: Exception) {
            "unknown"
        }

        Log.d(TAG, "Loading GeoJSON resource: $resourceName")

        val raw = try {
            context.resources.openRawResource(rawResourceId)
                .bufferedReader()
                .use { it.readText() }
        } catch (e: Exception) {
            throw IOException(
                "GeoJSON resource '$resourceName' is missing or unreadable: ${e.message}", e
            )
        }

        return try {
            val json = JSONObject(raw)
            val type = json.optString("type")
            if (type != "FeatureCollection") {
                throw IOException(
                    "GeoJSON resource '$resourceName' is not a FeatureCollection (found type='$type')"
                )
            }
            json.getJSONArray("features").also {
                Log.d(TAG, "Loaded $resourceName: ${it.length()} features")
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(
                "Failed to parse GeoJSON resource '$resourceName': ${e.message}", e
            )
        }
    }

    /**
     * Loads the base road network (R.raw.road_network).
     */
    @Throws(IOException::class)
    fun getRoadNetwork(context: Context): JSONArray =
        loadFeatures(context, R.raw.road_network)

    /**
     * Loads flood hazard zone polygons (R.raw.flood_zones).
     * Source: DMC/NBRO flood data — PLACEHOLDER for prototype.
     */
    @Throws(IOException::class)
    fun getHazardRoads(context: Context): JSONArray =
        loadFeatures(context, R.raw.flood_zones)

    /**
     * Loads landslide hazard zone polygons (R.raw.landslide_zones).
     * Source: NBRO landslide data — PLACEHOLDER for prototype.
     */
    @Throws(IOException::class)
    fun getLandslideZones(context: Context): JSONArray =
        loadFeatures(context, R.raw.landslide_zones)

    /**
     * Loads shelter Point features for the Ratnapura pilot district
     * (R.raw.shelters_ratnapura).
     */
    @Throws(IOException::class)
    fun getShelterPoints(context: Context): JSONArray =
        loadFeatures(context, R.raw.shelters_ratnapura)
}
