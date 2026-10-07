package com.hades.safepath.location

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * JVM unit tests for [SafePathLocationManager].
 *
 * Uses Mockito to mock [Context] and [FusedLocationProviderClient] so no
 * Android emulator is required.
 *
 * [android.util.Log] and other Android stubs return default values (null/0/false)
 * because `testOptions { unitTests.isReturnDefaultValues = true }` is set
 * in app/build.gradle.kts.
 *
 * [FusedLocationProviderClient] is injected via the secondary constructor
 * parameter to avoid the static GMS call in the default constructor.
 */
class SafePathLocationManagerTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a mock [Context] where every permission check returns
     * [PackageManager.PERMISSION_DENIED].
     */
    private fun buildDeniedPermissionContext(): Context {
        val context = mock<Context>()

        // ContextCompat.checkSelfPermission calls context.checkPermission(perm, pid, uid)
        whenever(context.checkPermission(any(), any(), any()))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        // Direct checkSelfPermission on Context (used by some API paths)
        whenever(context.checkSelfPermission(any()))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        return context
    }

    /**
     * Builds a [SafePathLocationManager] with a mocked [FusedLocationProviderClient]
     * injected so the GMS static initialiser is bypassed entirely.
     */
    private fun buildManager(context: Context): SafePathLocationManager {
        val mockFusedClient = mock<FusedLocationProviderClient>()
        return SafePathLocationManager(context, mockFusedClient)
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * R4 criterion 1 — checkPermissionsGranted() must return false when
     * ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION are denied.
     */
    @Test
    fun testCheckPermissionsGranted_returnsFalseWithNoPermissions() {
        val context = buildDeniedPermissionContext()
        val manager = buildManager(context)

        assertFalse(
            "checkPermissionsGranted() should return false when permissions are denied",
            manager.checkPermissionsGranted()
        )
    }

    /**
     * R4 criterion 5 — getLastKnownLocation() must return null without
     * throwing when permissions are not granted.
     */
    @Test
    fun testGetLastKnownLocation_returnsNullWithoutPermissions() = runBlocking {
        val context = buildDeniedPermissionContext()
        val manager = buildManager(context)

        val result = manager.getLastKnownLocation()

        assertNull(
            "getLastKnownLocation() should return null when permissions are denied",
            result
        )
    }
}
