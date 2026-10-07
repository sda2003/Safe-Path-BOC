package com.hades.safepath.ui.hardware

import android.app.Application
import android.graphics.Bitmap
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hades.safepath.camera.SafePathCameraRepository
import com.hades.safepath.mesh.MeshNetwork
import com.hades.safepath.p2p.P2pManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class NodeTelemetryUi(
    val online: Boolean = false,
    val nodeId: String = "—",
    val temperatureC: Double? = null,
    val humidityPct: Double? = null,
    val risk: Int = 0,
    val riskLabel: String = "Normal",
    val lat: Double? = null,
    val lon: Double? = null,
    val placeName: String = "Unknown location",
    val camIp: String? = null,
    val cameraUrl: String? = null,
    /** Photo already received over Classic Bluetooth. Dashboard image only. */
    val cameraBitmap: Bitmap? = null,
    val updatedAtMs: Long = 0L
)

/**
 * Parses ESP32 Bluetooth SPP JSON packets and drives the Node Dashboard UI.
 *
 * Example packet:
 * `{"node":"SP-01","temp":30.2,"hum":85.0,"risk":1,"lat":6.927,"lon":79.861,"cam_ip":"192.168.4.1"}`
 */
@HiltViewModel
class NodeDashboardViewModel @Inject constructor(
    application: Application,
    private val meshNetwork: MeshNetwork,
    private val p2pManager: P2pManager,
    private val cameraRepository: SafePathCameraRepository
) : AndroidViewModel(application) {

    private val _telemetry = MutableLiveData(NodeTelemetryUi())
    val telemetry: LiveData<NodeTelemetryUi> = _telemetry

    private val _statusMessage = MutableLiveData("Listening for ESP32…")
    val statusMessage: LiveData<String> = _statusMessage

    init {
        // Keep mesh ready so risk>0 can forward immediately
        runCatching { p2pManager.start() }
    }

    fun onBluetoothJson(raw: String) {
        viewModelScope.launch { applyPacket(raw, bluetoothPhoto = null) }
    }

    /**
     * Demo readings stay Normal (risk 0). Colombo coordinates stay.
     * The camera card uses the JPEG already received over Classic Bluetooth.
     */
    fun injectDemoPacket() {
        val demo = """
            {"node":"SP-01","temp":28.4,"hum":64.0,"risk":0,"lat":6.927,"lon":79.861}
        """.trimIndent()
        val shot = cameraRepository.session.value.bitmap
        val copy = shot?.takeUnless { it.isRecycled }?.let { source ->
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        }
        viewModelScope.launch { applyPacket(demo, bluetoothPhoto = copy) }
    }

    private suspend fun applyPacket(raw: String, bluetoothPhoto: Bitmap?) {
        try {
            val json = JSONObject(raw.trim())
            val node = json.optString("node", "UNKNOWN")
            val temp = json.optDouble("temp", Double.NaN)
            val hum = json.optDouble("hum", Double.NaN)
            val risk = json.optInt("risk", 0)
            val lat = json.optDouble("lat", Double.NaN)
            val lon = json.optDouble("lon", Double.NaN)
            val camIp = json.optString("cam_ip", "").ifBlank { null }

            val place = if (!lat.isNaN() && !lon.isNaN()) {
                reverseGeocode(lat, lon)
            } else {
                "Coordinates unavailable"
            }

            val cameraUrl = if (bluetoothPhoto != null) null else camIp?.let { "http://$it/capture" }
            val now = System.currentTimeMillis()

            _telemetry.value = NodeTelemetryUi(
                online = true,
                nodeId = node,
                temperatureC = temp.takeUnless { it.isNaN() },
                humidityPct = hum.takeUnless { it.isNaN() },
                risk = risk,
                riskLabel = riskLabel(risk),
                lat = lat.takeUnless { it.isNaN() },
                lon = lon.takeUnless { it.isNaN() },
                placeName = place,
                camIp = camIp,
                cameraUrl = cameraUrl,
                cameraBitmap = bluetoothPhoto,
                updatedAtMs = now
            )
            _statusMessage.value = "Live from $node"

            if (risk > 0 && !lat.isNaN() && !lon.isNaN()) {
                meshNetwork.forwardAlert(
                    nodeId = node,
                    temp = temp.takeUnless { it.isNaN() } ?: 0.0,
                    humidity = hum.takeUnless { it.isNaN() } ?: 0.0,
                    risk = risk,
                    lat = lat,
                    lon = lon
                )
                _statusMessage.value = "Risk alert forwarded to mesh"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ESP32 JSON: $raw", e)
            _statusMessage.value = "Bad packet: ${e.message}"
        }
    }

    fun markOfflineAfterTimeout(timeoutMs: Long = 30_000L) {
        viewModelScope.launch {
            while (true) {
                delay(5_000L)
                val last = _telemetry.value?.updatedAtMs ?: 0L
                if (last > 0L && System.currentTimeMillis() - last > timeoutMs) {
                    _telemetry.value = (_telemetry.value ?: NodeTelemetryUi()).copy(online = false)
                    _statusMessage.value = "Node: OFFLINE (no packets)"
                }
            }
        }
    }

    fun formatLastUpdated(updatedAtMs: Long): String {
        if (updatedAtMs <= 0L) return "Last updated: —"
        val mins = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - updatedAtMs)
        return when {
            mins < 1 -> "Last updated: just now"
            mins == 1L -> "Last updated: 1 min ago"
            else -> "Last updated: $mins mins ago"
        }
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): String =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) {
                    return@withContext String.format(Locale.US, "%.4f, %.4f", lat, lon)
                }
                val geocoder = Geocoder(getApplication(), Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(lat, lon, 1)
                val addr = results?.firstOrNull()
                when {
                    addr == null -> String.format(Locale.US, "%.4f, %.4f (offline)", lat, lon)
                    !addr.locality.isNullOrBlank() -> addr.locality!!
                    !addr.subAdminArea.isNullOrBlank() -> addr.subAdminArea!!
                    !addr.adminArea.isNullOrBlank() -> addr.adminArea!!
                    else -> addr.getAddressLine(0) ?: "Sri Lanka"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Geocoder failed (offline?)", e)
                String.format(Locale.US, "%.4f, %.4f", lat, lon)
            }
        }

    private fun riskLabel(risk: Int): String = when {
        risk >= 2 -> "Hazard"
        risk == 1 -> "Warning"
        else -> "Normal"
    }

    override fun onCleared() {
        super.onCleared()
        // Leave P2P running for the rest of the app session
    }

    companion object {
        private const val TAG = "NodeDashboardVM"
    }
}
