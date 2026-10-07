package com.hades.safepath.map.organic

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.hades.safepath.data.entity.Shelter
import java.util.Locale

/**
 * Integration with [Organic Maps](https://github.com/organicmaps/organicmaps) via the
 * official deep-link API (`om://map?…`) documented in
 * [organicmaps/api-android](https://github.com/organicmaps/api-android).
 *
 * Organic Maps is a full offline map *application* (vector `.mwm` engine), not an
 * embeddable MapView. SafePath keeps hazard/shelter/Dijkstra overlays in-app and
 * hands basemap + turn-by-turn navigation to Organic Maps when installed.
 *
 * Attribution: Organic Maps Project — https://organicmaps.app
 */
object OrganicMapsLauncher {

    private const val TAG = "OrganicMapsLauncher"
    private const val API_SCHEME = "om://"
    private const val PLAY_STORE =
        "https://play.google.com/store/apps/details?id=app.organicmaps"
    private const val GET_PAGE = "https://organicmaps.app/get/"

    private val KNOWN_PACKAGES = listOf(
        "app.organicmaps",
        "app.organicmaps.beta",
        "app.organicmaps.debug",
        "app.organicmaps.web"
    )

    data class MapPoint(
        val lat: Double,
        val lon: Double,
        val name: String,
        val id: String? = null
    )

    fun isInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return KNOWN_PACKAGES.any { pkg ->
            runCatching {
                pm.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        } || canHandleDeepLink(context)
    }

    fun canHandleDeepLink(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${API_SCHEME}map?"))
        return intent.resolveActivity(context.packageManager) != null
    }

    /**
     * Opens Organic Maps centered on [points] (shelters, route ends, etc.).
     * If OM is missing, offers Play Store / download page.
     */
    fun openPoints(
        activity: Activity,
        title: String,
        points: List<MapPoint>,
        zoom: Double = 13.0
    ): Boolean {
        if (points.isEmpty()) return false
        val intent = buildMapIntent(title, points, zoom)
        return launchOrPromptInstall(activity, intent)
    }

    fun openShelters(
        activity: Activity,
        shelters: List<Shelter>,
        title: String = "SafePath shelters"
    ): Boolean {
        val points = shelters.map {
            MapPoint(it.lat, it.lon, it.name, id = "shelter-${it.id}")
        }
        return openPoints(activity, title, points, zoom = 11.0)
    }

    fun openShelter(
        activity: Activity,
        shelter: Shelter
    ): Boolean = openPoints(
        activity,
        title = "SafePath · ${shelter.name}",
        points = listOf(MapPoint(shelter.lat, shelter.lon, shelter.name, "shelter-${shelter.id}")),
        zoom = 15.0
    )

    fun openRouteEndpoints(
        activity: Activity,
        startLat: Double,
        startLon: Double,
        shelter: Shelter
    ): Boolean = openPoints(
        activity,
        title = "SafePath evacuation",
        points = listOf(
            MapPoint(startLat, startLon, "You", id = "user"),
            MapPoint(shelter.lat, shelter.lon, shelter.name, id = "shelter-${shelter.id}")
        ),
        zoom = 14.0
    )

    fun promptInstall(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Organic Maps required")
            .setMessage(
                "SafePath uses Organic Maps for full offline basemaps and navigation.\n\n" +
                    "Install Organic Maps, then download the Sri Lanka map inside that app " +
                    "(Download maps → Sri Lanka). Hazard overlays stay inside SafePath.\n\n" +
                    "Organic Maps Project — https://organicmaps.app"
            )
            .setPositiveButton("Install") { _, _ -> openInstallPage(activity) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun openInstallPage(context: Context) {
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=app.organicmaps")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val getPage = Intent(Intent.ACTION_VIEW, Uri.parse(GET_PAGE))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(web)
            } catch (_: ActivityNotFoundException) {
                context.startActivity(getPage)
            }
        }
    }

    private fun buildMapIntent(
        appName: String,
        points: List<MapPoint>,
        zoom: Double
    ): Intent {
        val builder = StringBuilder("${API_SCHEME}map?")
        builder.append("appname=").append(Uri.encode(appName)).append('&')
        if (zoom > 0) {
            builder.append("z=").append(zoom).append('&')
        }
        for (point in points) {
            builder.append("ll=")
                .append(String.format(Locale.US, "%f,%f&", point.lat, point.lon))
            builder.append("n=").append(Uri.encode(point.name)).append('&')
            if (!point.id.isNullOrBlank()) {
                builder.append("id=").append(Uri.encode(point.id)).append('&')
            }
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(builder.toString()))
    }

    private fun launchOrPromptInstall(activity: Activity, intent: Intent): Boolean {
        return try {
            if (canHandleDeepLink(activity) || isInstalled(activity)) {
                // Prefer a known OM package when multiple viewers exist
                val preferred = KNOWN_PACKAGES.firstOrNull { pkg ->
                    runCatching {
                        activity.packageManager.getPackageInfo(pkg, 0)
                        true
                    }.getOrDefault(false)
                }
                if (preferred != null) intent.setPackage(preferred)
                activity.startActivity(intent)
                true
            } else {
                promptInstall(activity)
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch Organic Maps", e)
            promptInstall(activity)
            false
        }
    }
}
