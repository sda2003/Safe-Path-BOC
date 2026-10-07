package com.hades.safepath.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.hades.safepath.data.entity.HazardNodeEntity
import com.hades.safepath.data.repository.HazardNodeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleHazardScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hazardNodeRepository: HazardNodeRepository
) {
    // Official SafePath service UUID we will use on the ESP32-S3
    private val SAFE_PATH_SERVICE_UUID = ParcelUuid(UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB"))
    
    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private var isScanning = false
    private val scope = CoroutineScope(Dispatchers.IO)

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BleScanner", "Scan failed with error: $errorCode")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (bleScanner == null) {
            Log.e("BleScanner", "BLE not supported on this device")
            return
        }
        
        if (isScanning) return
        
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(SAFE_PATH_SERVICE_UUID)
            .build()
            
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            Log.d("BleScanner", "Started scanning for Hazard Nodes...")
        } catch (e: Exception) {
            Log.e("BleScanner", "Failed to start BLE scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) return
        
        try {
            bleScanner?.stopScan(scanCallback)
            isScanning = false
            Log.d("BleScanner", "Stopped scanning for Hazard Nodes")
        } catch (e: Exception) {
            Log.e("BleScanner", "Failed to stop BLE scan", e)
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val serviceData = record.getServiceData(SAFE_PATH_SERVICE_UUID)
        
        if (serviceData != null && serviceData.isNotEmpty()) {
            parseAndStoreTelemetry(result.device.address, serviceData)
        }
    }

    /**
     * Parses the custom byte array from ESP32-S3 advertisement payload.
     * Byte structure (Example):
     * [0]: Battery (0-100)
     * [1-2]: Water Level in cm (Int16)
     * [3-4]: Soil Moisture % (Int16)
     * [5]: Risk Level (0: LOW, 1: MODERATE, 2: HIGH, 3: CRITICAL)
     */
    private fun parseAndStoreTelemetry(macAddress: String, data: ByteArray) {
        try {
            if (data.size >= 6) {
                val battery = data[0].toInt() and 0xFF
                val waterLevelCm = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
                val soilMoisture = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
                val riskInt = data[5].toInt() and 0xFF
                
                val riskLevel = when (riskInt) {
                    0 -> "LOW"
                    1 -> "MODERATE"
                    2 -> "HIGH"
                    3 -> "CRITICAL"
                    else -> "UNKNOWN"
                }

                // Default location to 0.0 (in real app, use mapped location or GPS from ESP32 if available)
                val entity = HazardNodeEntity(
                    nodeId = macAddress,
                    latitude = 0.0,
                    longitude = 0.0,
                    temperature = 25.0, // Default if not in packet
                    humidity = 50.0,
                    pressure = 1013.0,
                    rainfall = 0.0,
                    waterLevel = waterLevelCm / 100.0,
                    soilMoisture = soilMoisture.toDouble(),
                    battery = battery,
                    riskLevel = riskLevel,
                    timestamp = System.currentTimeMillis(),
                    cameraAvailable = false
                )
                
                scope.launch {
                    hazardNodeRepository.insertOrUpdateNode(entity)
                    Log.d("BleScanner", "Stored BLE Telemetry from $macAddress")
                }
            }
        } catch (e: Exception) {
            Log.e("BleScanner", "Failed to parse BLE telemetry", e)
        }
    }
}
