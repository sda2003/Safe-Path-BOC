package com.hades.safepath.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class WifiConnectResult {
    data class Connected(val network: Network) : WifiConnectResult()
    data class NeedsManualJoin(val ssid: String, val password: String, val message: String) :
        WifiConnectResult()

    data class Failed(val message: String) : WifiConnectResult()
}

private data class VisibleAp(
    val ssid: String,
    val bssid: String,
    val open: Boolean,
    val level: Int
)

/**
 * Joins the ESP32-CAM SoftAP without internet.
 *
 * Android's "Unable to select device" dialog means the system Wi-Fi picker
 * found zero networks matching the request. The usual cause is asking only
 * for a WPA2 hotspot named exactly SafePath-CAM-001 while the ESP32 is
 * open, uses a slightly different name, or is invisible because Location is off.
 */
@Singleton
class SafePathCameraWifiConnector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile
    private var boundNetwork: Network? = null

    @Volatile
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var onNetworkLost: (() -> Unit)? = null

    fun setOnNetworkLost(listener: (() -> Unit)?) {
        onNetworkLost = listener
    }

    fun currentNetwork(): Network? = boundNetwork

    fun isLocationEnabled(): Boolean = locationManager.isLocationEnabled

    fun canScanWifi(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            has(android.Manifest.permission.NEARBY_WIFI_DEVICES) ||
                has(android.Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            has(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    suspend fun connect(ssid: String, password: String): WifiConnectResult {
        if (!wifiManager.isWifiEnabled) {
            return WifiConnectResult.NeedsManualJoin(
                ssid, password,
                "Turn on Wi-Fi, then connect to $ssid. Password: $password"
            )
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return WifiConnectResult.NeedsManualJoin(
                ssid, password,
                "Connect to $ssid in Wi-Fi settings. Password: $password"
            )
        }

        suggestNetwork(ssid)
        findCameraNetwork()?.let { network ->
            boundNetwork = network
            bindProcess(network)
            return WifiConnectResult.Connected(network)
        }

        // Firmware calls WiFi.softAP(ssid) with no password — the hotspot is OPEN.
        // Asking for WPA2 (SafePath123) makes Android report "Unable to select device"
        // and the ESP32 station count stays 0.
        return withContext(Dispatchers.Main) {
            disconnectInternal()
            val joined = withTimeoutOrNull(35_000L) { requestSpecifier(ssid, null) }
            val announced = (joined as? WifiConnectResult.Connected)?.network
            val camera = awaitCameraNetwork(announced)
            if (camera != null) {
                boundNetwork = camera
                bindProcess(camera)
                WifiConnectResult.Connected(camera)
            } else {
                WifiConnectResult.NeedsManualJoin(
                    ssid,
                    "",
                    "Tap Connect on the phone Wi-Fi popup for $ssid. This hotspot has no password. " +
                        "If Android asks for a password, forget $ssid in Wi-Fi settings first. " +
                        "The ESP32 must say Connected stations: 1."
                )
            }
        }
    }

    /**
     * Phone is already associated with the ESP32 hotspot (has a 192.168.4.x address).
     */
    suspend fun bindExistingLocalWifi(): WifiConnectResult {
        findCameraNetwork()?.let { network ->
            boundNetwork = network
            bindProcess(network)
            return WifiConnectResult.Connected(network)
        }
        return WifiConnectResult.NeedsManualJoin(
            "SafePath-CAM-001",
            SafePathCameraNode.DEFAULT_WIFI_PASSWORD,
            "The phone still has no 192.168.4.x address, so the ESP32 station count stays 0. " +
                "Open Wi-Fi settings, tap SafePath-CAM-001 (no password), then come back and tap this again."
        )
    }

    fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun disconnect() {
        disconnectInternal()
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanAps(): List<VisibleAp> = withContext(Dispatchers.IO) {
        if (!canScanWifi()) return@withContext emptyList()
        try {
            wifiManager.startScan()
        } catch (t: Throwable) {
            Log.w(TAG, "startScan", t)
        }
        delay(1600)
        val results = try {
            wifiManager.scanResults
        } catch (t: SecurityException) {
            Log.w(TAG, "scanResults denied", t)
            emptyList()
        }
        results.mapNotNull { it.toVisibleAp() }
            .distinctBy { it.bssid }
            .sortedByDescending { it.level }
    }

    private fun pickAp(aps: List<VisibleAp>, preferredSsid: String): VisibleAp? {
        val exact = aps.filter { it.ssid.equals(preferredSsid, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact.maxByOrNull { it.level }
        return aps.filter { it.ssid.contains("SafePath", ignoreCase = true) }
            .maxByOrNull { it.level }
    }

    private fun suggestNetwork(ssid: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val previousSecured = WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase("SafePath123")
                .setIsAppInteractionRequired(false)
                .build()
            wifiManager.removeNetworkSuggestions(listOf(previousSecured))
            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setIsAppInteractionRequired(false)
                .build()
            val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
            Log.i(TAG, "open network suggestion status=$status for $ssid")
        } catch (t: Throwable) {
            Log.w(TAG, "addNetworkSuggestions", t)
        }
    }

    /** The ESP32 SoftAP hands out 192.168.4.x. Anything else is not this camera. */
    private fun findCameraNetwork(): Network? {
        return connectivityManager.allNetworks.firstOrNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return@firstOrNull false
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@firstOrNull false
            val props = connectivityManager.getLinkProperties(network) ?: return@firstOrNull false
            props.linkAddresses.any { address ->
                address.address.hostAddress?.startsWith("192.168.4.") == true
            }
        }
    }

    private suspend fun awaitCameraNetwork(hint: Network?): Network? {
        if (hint != null) {
            boundNetwork = hint
            bindProcess(hint)
        }
        repeat(20) {
            findCameraNetwork()?.let { return it }
            delay(500)
        }
        return findCameraNetwork() ?: hint
    }

    private suspend fun requestSpecifier(
        ssid: String,
        password: String?
    ): WifiConnectResult = suspendCancellableCoroutine { cont ->
        try {
            val builder = WifiNetworkSpecifier.Builder().setSsid(ssid)
            if (!password.isNullOrBlank()) {
                builder.setWpa2Passphrase(password)
            }
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(builder.build())
                .build()
            register(request, cont, timeoutMs = null, ssid, password ?: "")
        } catch (t: Throwable) {
            Log.e(TAG, "requestNetwork failed", t)
            if (cont.isActive) {
                cont.resume(
                    WifiConnectResult.NeedsManualJoin(
                        ssid,
                        password.orEmpty(),
                        "Could not request $ssid. Connect to it in Wi-Fi settings. Password: ${password.orEmpty()}"
                    )
                )
            }
        }
    }

    private suspend fun requestAnyLocalWifi(): WifiConnectResult = suspendCancellableCoroutine { cont ->
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        register(request, cont, timeoutMs = 8_000, ssid = "", password = "")
    }

    private fun register(
        request: NetworkRequest,
        cont: kotlinx.coroutines.CancellableContinuation<WifiConnectResult>,
        timeoutMs: Int?,
        ssid: String,
        password: String
    ) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (cont.isActive) cont.resume(WifiConnectResult.Connected(network))
            }

            override fun onUnavailable() {
                if (cont.isActive) {
                    cont.resume(
                        WifiConnectResult.NeedsManualJoin(
                            ssid,
                            password,
                            "Android could not select the camera Wi-Fi. Open Wi-Fi settings, join $ssid (password $password), then tap “Use connected camera Wi-Fi”."
                        )
                    )
                }
            }

            override fun onLost(network: Network) {
                if (boundNetwork == null || boundNetwork == network) {
                    boundNetwork = null
                    Log.i(TAG, "Camera Wi-Fi lost")
                    try {
                        connectivityManager.bindProcessToNetwork(null)
                    } catch (_: Exception) {
                    }
                    onNetworkLost?.invoke()
                }
            }
        }
        callback = cb
        if (timeoutMs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            connectivityManager.requestNetwork(request, cb, timeoutMs)
        } else {
            connectivityManager.requestNetwork(request, cb)
        }
        cont.invokeOnCancellation { /* keep callback while connected; caller disconnects */ }
    }

    private fun bindProcess(network: Network) {
        try {
            connectivityManager.bindProcessToNetwork(network)
        } catch (t: Throwable) {
            Log.w(TAG, "bindProcessToNetwork", t)
        }
    }

    private fun disconnectInternal() {
        try {
            callback?.let { connectivityManager.unregisterNetworkCallback(it) }
        } catch (_: Exception) {
        }
        callback = null
        boundNetwork = null
        try {
            connectivityManager.bindProcessToNetwork(null)
        } catch (_: Exception) {
        }
    }

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("NewApi")
    private fun ScanResult.toVisibleAp(): VisibleAp? {
        val name = readSsid().trim().trim('"')
        if (name.isBlank() || name == "<unknown ssid>") return null
        val caps = capabilities.uppercase()
        val secured = caps.contains("WPA") || caps.contains("WEP") ||
            caps.contains("PSK") || caps.contains("SAE") || caps.contains("EAP")
        return VisibleAp(ssid = name, bssid = BSSID ?: "", open = !secured, level = level)
    }

    @Suppress("DEPRECATION")
    private fun ScanResult.readSsid(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wifiSsid?.toString().orEmpty()
        } else {
            SSID.orEmpty()
        }
    }

    companion object {
        private const val TAG = "CameraWifiConnector"
    }
}
