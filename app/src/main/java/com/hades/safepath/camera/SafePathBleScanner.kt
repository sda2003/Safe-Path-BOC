package com.hades.safepath.camera

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLE discovery for SafePath-CAM-* disaster observation nodes.
 * JPEG frames are sent over Classic Bluetooth Serial, not over BLE.
 */
@Singleton
class SafePathBleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private val discovered = ConcurrentHashMap<String, SafePathCameraNode>()
    private val _nodes = MutableStateFlow<List<SafePathCameraNode>>(emptyList())
    val nodes: StateFlow<List<SafePathCameraNode>> = _nodes.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile
    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            handleResult(result)
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed code=$errorCode")
            isScanning = false
            _scanning.value = false
            _error.value = "Bluetooth scan failed (code $errorCode)."
        }
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        _error.value = null
        if (adapter == null) {
            _error.value = "This device does not support Bluetooth."
            return
        }
        if (!isBluetoothEnabled()) {
            _error.value = "Turn on Bluetooth to scan for SafePath nodes."
            return
        }
        if (!hasScanPermission()) {
            _error.value = "Bluetooth permission is required to scan for SafePath nodes."
            return
        }
        val bleScanner = scanner
        if (bleScanner == null) {
            _error.value = "Bluetooth LE scanner is unavailable."
            return
        }
        if (isScanning) return

        discovered.clear()
        publish()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            // Unfiltered scan so name-only SafePath-CAM-00N advertisements are found;
            // results are matched by device name and/or camera service UUID in the callback.
            bleScanner.startScan(null, settings, scanCallback)
            isScanning = true
            _scanning.value = true
            Log.i(TAG, "Started BLE scan for SafePath-CAM nodes")
        } catch (se: SecurityException) {
            Log.e(TAG, "Missing BLE permission", se)
            _error.value = "Bluetooth permission is required to scan for SafePath nodes."
            isScanning = false
            _scanning.value = false
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start BLE scan", t)
            _error.value = "Could not start Bluetooth scan."
            isScanning = false
            _scanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) {
            _scanning.value = false
            return
        }
        try {
            scanner?.stopScan(scanCallback)
        } catch (t: Throwable) {
            Log.w(TAG, "stopScan", t)
        }
        isScanning = false
        _scanning.value = false
    }

    fun clearError() {
        _error.value = null
    }

    @SuppressLint("MissingPermission")
    private fun handleResult(result: ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord
        val rawName = try {
            result.scanRecord?.deviceName
                ?: device.name
                ?: record?.deviceName
        } catch (_: SecurityException) {
            record?.deviceName
        }

        val hasCameraUuid = record?.serviceUuids?.any {
            it?.uuid == CAMERA_SERVICE_UUID.uuid || it?.uuid == LEGACY_SERVICE_UUID.uuid
        } == true

        val name = rawName?.trim().orEmpty()
        if (name.isEmpty() && !hasCameraUuid) return

        val resolvedName = when {
            SafePathCameraNode.matchesCameraDeviceName(name) -> name
            hasCameraUuid && name.isNotEmpty() -> name
            hasCameraUuid -> "SafePath-CAM-001" // UUID match without name — single placeholder
            else -> return
        }

        if (!SafePathCameraNode.matchesCameraDeviceName(resolvedName) && !hasCameraUuid) return

        val node = SafePathCameraNode.fromBleAdvertisement(device, resolvedName, result.rssi)
            ?: return

        val key = node.bluetoothAddress.ifBlank { node.deviceName }
        val previous = discovered[key]
        discovered[key] = if (previous != null) {
            node.copy(
                connectionState = previous.connectionState,
                latitude = previous.latitude,
                longitude = previous.longitude
            )
        } else {
            node
        }
        publish()
    }

    private fun publish() {
        _nodes.value = discovered.values
            .sortedByDescending { it.rssi }
            .toList()
    }

    companion object {
        private const val TAG = "SafePathBleScanner"

        /** Dedicated SafePath camera-node service UUID (ESP32-CAM firmware). */
        val CAMERA_SERVICE_UUID: ParcelUuid =
            ParcelUuid(UUID.fromString("0000FFC1-0000-1000-8000-00805F9B34FB"))

        /** Also accept legacy SafePath hazard UUID if CAM firmware reuses it. */
        val LEGACY_SERVICE_UUID: ParcelUuid =
            ParcelUuid(UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB"))
    }
}
