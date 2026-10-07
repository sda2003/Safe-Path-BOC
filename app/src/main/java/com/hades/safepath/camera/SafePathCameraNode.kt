package com.hades.safepath.camera

import android.bluetooth.BluetoothDevice

/**
 * Nearby SafePath disaster observation camera node discovered over BLE.
 * JPEG frames travel over Classic Bluetooth Serial, not over BLE.
 */
data class SafePathCameraNode(
    val nodeId: String,
    val deviceName: String,
    val bluetoothAddress: String,
    val wifiSsid: String,
    val wifiPassword: String = DEFAULT_WIFI_PASSWORD,
    val wifiIp: String = DEFAULT_WIFI_IP,
    val nodeType: String = NODE_TYPE_CAMERA,
    val rssi: Int,
    val connectionState: CameraConnectionState = CameraConnectionState.AVAILABLE,
    /** Reserved for future GPS from the node — never invent values. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Extensible sensor fields (unused until firmware provides them). */
    val temperature: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null,
    val battery: Int? = null,
    val waterLevel: Double? = null,
    val accelerometer: String? = null,
    val camera: Boolean = true,
    /** Transient — not persisted; may be null after process death. */
    @Transient val bluetoothDevice: BluetoothDevice? = null
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null

    val signalLabel: String
        get() = when {
            rssi >= -55 -> "Strong"
            rssi >= -70 -> "Good"
            rssi >= -85 -> "Fair"
            else -> "Weak"
        }

    companion object {
        const val DEFAULT_WIFI_IP = "192.168.4.1"
        const val DEFAULT_WIFI_PASSWORD = ""
        const val NODE_TYPE_CAMERA = "CAMERA"
        const val NODE_TYPE_DISASTER = "DISASTER_NODE"

        private val NAME_REGEX = Regex("""^SafePath-CAM-(\d+)$""", RegexOption.IGNORE_CASE)

        fun matchesCameraDeviceName(name: String?): Boolean {
            val n = name?.trim().orEmpty()
            return NAME_REGEX.matches(n)
        }

        fun fromBleAdvertisement(
            device: BluetoothDevice,
            deviceName: String,
            rssi: Int
        ): SafePathCameraNode? {
            val match = NAME_REGEX.matchEntire(deviceName.trim()) ?: return null
            val ordinal = match.groupValues[1]
            val nodeId = "SP-CAM-$ordinal"
            return SafePathCameraNode(
                nodeId = nodeId,
                deviceName = deviceName.trim(),
                bluetoothAddress = device.address.orEmpty(),
                wifiSsid = deviceName.trim(),
                wifiIp = DEFAULT_WIFI_IP,
                rssi = rssi,
                bluetoothDevice = device
            )
        }
    }
}

enum class CameraConnectionState {
    AVAILABLE,
    CONNECTING_WIFI,
    WIFI_CONNECTED,
    STATUS_OK,
    IMAGE_READY,
    ERROR,
    DISCONNECTED
}
