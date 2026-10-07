package com.hades.safepath.camera

/**
 * Parsed GET /status JSON from an ESP32-CAM SoftAP.
 * All fields optional so missing keys never crash the app.
 */
data class CameraStatusResponse(
    val nodeId: String? = null,
    val deviceName: String? = null,
    val nodeType: String? = null,
    val wifiSsid: String? = null,
    val ip: String? = null,
    val camera: Boolean? = null,
    val status: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val temperature: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null,
    val battery: Int? = null,
    val waterLevel: Double? = null
) {
    val isOnline: Boolean
        get() = status.equals("online", ignoreCase = true) || camera == true
}
