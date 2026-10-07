package com.hades.safepath.camera

import android.graphics.Bitmap
import android.net.Network
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class CameraSessionUi(
    val node: SafePathCameraNode? = null,
    val status: CameraStatusResponse? = null,
    val bitmap: Bitmap? = null,
    val lastUpdatedMs: Long? = null,
    val busy: Boolean = false,
    val message: String? = null,
    /** Shown under the image when Classic Bluetooth capture fails. */
    val imageError: String? = null,
    val connected: Boolean = false,
    val wifiHint: String? = null
)

/**
 * Nearby nodes come from BLE or Classic Bluetooth. Photos are requested over Classic Bluetooth Serial.
 */
@Singleton
class SafePathCameraRepository @Inject constructor(
    private val bleScanner: SafePathBleScanner,
    private val wifiConnector: SafePathCameraWifiConnector,
    private val api: SafePathCameraApi,
    private val classicBt: SafePathClassicBtCapture
) {
    val discoveredNodes = bleScanner.nodes
    val classicNodes = classicBt.nodes
    val scanning = bleScanner.scanning
    val classicScanning = classicBt.scanning
    val classicScanError = classicBt.error
    val scanError = bleScanner.error

    private val _session = MutableStateFlow(CameraSessionUi())
    val session: StateFlow<CameraSessionUi> = _session.asStateFlow()

    fun startScan() {
        // Classic inquiry and a BLE scan share one radio. This node has no BLE advert.
        bleScanner.stopScan()
        classicBt.startInquiry()
    }

    fun stopScan() {
        bleScanner.stopScan()
        classicBt.stopInquiry()
    }
    fun isBluetoothEnabled() = bleScanner.isBluetoothEnabled()
    fun hasScanPermission() = bleScanner.hasScanPermission()
    fun clearScanError() {
        bleScanner.clearError()
        classicBt.clearError()
    }

    fun selectNode(node: SafePathCameraNode) {
        _session.value = CameraSessionUi(
            node = node.copy(connectionState = CameraConnectionState.AVAILABLE),
            message = "SafePath-CAM-001 is on Classic Bluetooth. Tap Connect / Retry, then Capture New Image."
        )
    }

    suspend fun connectSelected(): CameraSessionUi {
        val node = _session.value.node
            ?: return _session.value.copy(message = "No camera node selected.")
        _session.value = _session.value.copy(
            busy = true,
            message = "Looking for ${node.deviceName} over Bluetooth…",
            wifiHint = null,
            imageError = null
        )
        val found = classicBt.capture(node.deviceName, node.bluetoothAddress)
        return when (found) {
            is CameraApiResult.Ok -> {
                val previous = _session.value.bitmap
                _session.value = _session.value.copy(
                    bitmap = found.value,
                    lastUpdatedMs = System.currentTimeMillis(),
                    busy = false,
                    connected = true,
                    message = "Bluetooth connected. Image captured.",
                    imageError = null,
                    node = node.copy(connectionState = CameraConnectionState.IMAGE_READY)
                )
                if (previous != null && previous !== found.value && !previous.isRecycled) {
                    try {
                        previous.recycle()
                    } catch (_: Exception) {
                    }
                }
                _session.value
            }
            is CameraApiResult.Err -> {
                val missing = found.message.contains("not found", ignoreCase = true)
                _session.value = _session.value.copy(
                    busy = false,
                    connected = !missing && _session.value.connected,
                    message = if (missing) {
                        "SafePath-CAM-001 is not in the phone's Bluetooth list yet. Open Bluetooth settings, pair SafePath-CAM-001, then tap Connect / Retry."
                    } else {
                        found.message
                    },
                    imageError = if (missing) null else found.message,
                    node = node.copy(
                        connectionState = if (missing) {
                            CameraConnectionState.AVAILABLE
                        } else {
                            node.connectionState
                        }
                    )
                )
                _session.value
            }
        }
    }

    private suspend fun applyStatus(node: SafePathCameraNode, network: Network): CameraSessionUi {
        var statusResult: CameraApiResult<CameraStatusResponse> =
            CameraApiResult.Err("The camera did not respond.")
        repeat(6) { attempt ->
            statusResult = api.fetchStatus(network, node.wifiIp)
            if (statusResult is CameraApiResult.Ok) return@repeat
            if (attempt < 5) delay(1500)
        }
        return when (val result = statusResult) {
            is CameraApiResult.Ok -> {
                val status = result.value
                val merged = mergeStatus(node, status).copy(
                    connectionState = CameraConnectionState.STATUS_OK
                )
                _session.value = CameraSessionUi(
                    node = merged,
                    status = status,
                    connected = true,
                    busy = false,
                    message = if (status.isOnline) "Camera Online" else "Camera Connected",
                    wifiHint = null
                )
                _session.value
            }
            is CameraApiResult.Err -> {
                _session.value = _session.value.copy(
                    connected = true,
                    busy = false,
                    message = "Wi-Fi is joined (the ESP32 shows a station) but http://192.168.4.1 has not answered yet. Tap Refresh Status, then Capture New Image.",
                    wifiHint = null,
                    node = node.copy(connectionState = CameraConnectionState.WIFI_CONNECTED)
                )
                _session.value
            }
        }
    }

    fun openWifiSettings() = wifiConnector.openWifiSettings()

    fun openLocationSettings() = wifiConnector.openLocationSettings()

    fun isLocationEnabled() = wifiConnector.isLocationEnabled()

    /**
     * User already joined the ESP32 hotspot from system Wi-Fi settings.
     * Pin HTTP to that local network so mobile data does not steal the request.
     */
    suspend fun useJoinedWifi(): CameraSessionUi {
        val node = _session.value.node
            ?: return _session.value.copy(message = "No camera node selected.")
        _session.value = _session.value.copy(busy = true, message = "Using the camera Wi-Fi…")
        return when (val wifi = wifiConnector.bindExistingLocalWifi()) {
            is WifiConnectResult.Connected -> applyStatus(node, wifi.network)
            is WifiConnectResult.NeedsManualJoin -> {
                _session.value = _session.value.copy(
                    busy = false,
                    connected = false,
                    message = wifi.message,
                    wifiHint = wifi.message
                )
                _session.value
            }
            is WifiConnectResult.Failed -> {
                _session.value = _session.value.copy(busy = false, message = wifi.message)
                _session.value
            }
        }
    }

    suspend fun refreshStatus(): CameraSessionUi {
        val node = _session.value.node ?: return _session.value
        _session.value = _session.value.copy(busy = true, message = "Checking camera status…")
        val network = wifiConnector.currentNetwork()
        return when (val result = api.fetchStatus(network, node.wifiIp)) {
            is CameraApiResult.Ok -> {
                val status = result.value
                val merged = mergeStatus(node, status).copy(
                    connectionState = CameraConnectionState.STATUS_OK
                )
                _session.value = _session.value.copy(
                    node = merged,
                    status = status,
                    connected = status.isOnline || true,
                    busy = false,
                    message = if (status.isOnline) "Camera Online" else "Camera status received",
                    wifiHint = null
                )
                _session.value
            }
            is CameraApiResult.Err -> {
                _session.value = _session.value.copy(
                    busy = false,
                    message = result.message
                )
                _session.value
            }
        }
    }

    fun noteCaptureBlocked(message: String) {
        _session.value = _session.value.copy(
            busy = false,
            message = message,
            imageError = message
        )
    }

    suspend fun captureImage(): CameraSessionUi {
        val node = _session.value.node ?: return _session.value
        _session.value = _session.value.copy(
            busy = true,
            message = "Capturing over Bluetooth…",
            imageError = null
        )
        return when (val result = classicBt.capture(node.deviceName, node.bluetoothAddress)) {
            is CameraApiResult.Ok -> {
                val previous = _session.value.bitmap
                _session.value = _session.value.copy(
                    bitmap = result.value,
                    lastUpdatedMs = System.currentTimeMillis(),
                    busy = false,
                    message = "Image captured",
                    imageError = null,
                    node = node.copy(connectionState = CameraConnectionState.IMAGE_READY)
                )
                if (previous != null && previous !== result.value && !previous.isRecycled) {
                    try {
                        previous.recycle()
                    } catch (_: Exception) {
                    }
                }
                _session.value
            }
            is CameraApiResult.Err -> {
                Log.w(TAG, "capture: ${result.message}")
                _session.value = _session.value.copy(
                    busy = false,
                    message = result.message,
                    imageError = result.message
                )
                _session.value
            }
        }
    }

    fun disconnect() {
        wifiConnector.disconnect()
        val node = _session.value.node
        _session.value = CameraSessionUi(
            node = node?.copy(connectionState = CameraConnectionState.DISCONNECTED),
            connected = false,
            message = "Node disconnected",
            wifiHint = node?.let { "Connect to ${it.wifiSsid} Wi-Fi to access this camera." }
        )
    }

    fun clearSessionMessage() {
        _session.value = _session.value.copy(message = null)
    }

    private fun mergeStatus(
        node: SafePathCameraNode,
        status: CameraStatusResponse
    ): SafePathCameraNode {
        return node.copy(
            nodeId = status.nodeId?.takeIf { it.isNotBlank() } ?: node.nodeId,
            deviceName = status.deviceName?.takeIf { it.isNotBlank() } ?: node.deviceName,
            wifiSsid = status.wifiSsid?.takeIf { it.isNotBlank() } ?: node.wifiSsid,
            wifiIp = status.ip?.takeIf { it.isNotBlank() } ?: node.wifiIp,
            nodeType = status.nodeType?.takeIf { it.isNotBlank() } ?: node.nodeType,
            camera = status.camera ?: node.camera,
            latitude = status.latitude ?: node.latitude,
            longitude = status.longitude ?: node.longitude,
            temperature = status.temperature ?: node.temperature,
            humidity = status.humidity ?: node.humidity,
            pressure = status.pressure ?: node.pressure,
            battery = status.battery ?: node.battery,
            waterLevel = status.waterLevel ?: node.waterLevel
        )
    }

    companion object {
        private const val TAG = "CameraRepository"
        private const val NOT_ON_AP =
            "Not on the camera Wi-Fi yet. Tap Connect / Retry and press Connect on the phone popup. SafePath-CAM-001 has no password. The ESP32 must show Connected stations: 1 before a photo can load."
    }
}
