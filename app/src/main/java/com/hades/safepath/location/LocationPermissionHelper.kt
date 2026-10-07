package com.hades.safepath.location

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

/**
 * Utility for requesting and checking location permissions.
 *
 * Usage in Activity:
 * ```kotlin
 * if (!LocationPermissionHelper.isGranted(this)) {
 *     LocationPermissionHelper.requestPermissions(this)
 * }
 *
 * override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
 *     val granted = LocationPermissionHelper.onRequestPermissionsResult(requestCode, grantResults)
 * }
 * ```
 */
object LocationPermissionHelper {

    const val REQUEST_CODE = 1001

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /** Returns true if both location permissions are currently granted. */
    fun isGranted(activity: Activity): Boolean =
        REQUIRED_PERMISSIONS.all { permission ->
            ActivityCompat.checkSelfPermission(activity, permission) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** Launches the system permission dialog for both location permissions. */
    fun requestPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            REQUIRED_PERMISSIONS,
            REQUEST_CODE
        )
    }

    /**
     * Call from [Activity.onRequestPermissionsResult].
     * @return true if both permissions were granted by the user.
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != REQUEST_CODE) return false
        return grantResults.size >= 2 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED &&
            grantResults[1] == PackageManager.PERMISSION_GRANTED
    }
}
