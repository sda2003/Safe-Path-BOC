package com.hades.safepath.camera

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * One JPEG over Classic Bluetooth Serial (SPP). BLE is not used for the picture.
 *
 * Protocol, after this side writes "c\n":
 *   SPIC,<length>\n
 *   <exactly length JPEG bytes>
 *   \nSPEND\n
 */
@Singleton
class SafePathClassicBtCapture @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val seen = ConcurrentHashMap<String, SafePathCameraNode>()
    private val _nodes = MutableStateFlow<List<SafePathCameraNode>>(emptyList())
    val nodes: StateFlow<List<SafePathCameraNode>> = _nodes.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var inquiryReceiver: BroadcastReceiver? = null
    private var keepInquiry = false
    private var reportedEmpty = false
    private val heardNames = LinkedHashSet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun clearError() {
        _error.value = null
    }

    /** Classic Bluetooth inquiry. This firmware does not advertise over BLE. */
    @SuppressLint("MissingPermission")
    fun startInquiry() {
        val adapter = bluetoothAdapter() ?: return
        if (!adapter.isEnabled) {
            _error.value = "Turn on Bluetooth, then tap Scan."
            return
        }
        stopInquiry()
        keepInquiry = true
        reportedEmpty = false
        seen.clear()
        heardNames.clear()
        _nodes.value = emptyList()
        _error.value = null
        publishBonded(adapter)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = deviceExtra(intent)
                        val advertised = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                        if (!advertised.isNullOrBlank()) heardNames.add(advertised.trim())
                        val cachedName = try {
                            device?.name
                        } catch (_: SecurityException) {
                            null
                        }
                        if (device != null && advertised.isNullOrBlank() && cachedName.isNullOrBlank()) {
                            try {
                                device.fetchUuidsWithSdp()
                            } catch (_: SecurityException) {
                            }
                        }
                        remember(device, advertised)
                    }
                    BluetoothDevice.ACTION_NAME_CHANGED -> {
                        val device = deviceExtra(intent)
                        val advertised = intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: device?.name
                        if (!advertised.isNullOrBlank()) heardNames.add(advertised.trim())
                        remember(device, advertised)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        if (seen.isEmpty()) noteEmptyPass()
                        if (!keepInquiry) return
                        mainHandler.postDelayed({
                            if (!keepInquiry) return@postDelayed
                            val again = try {
                                adapter.startDiscovery()
                            } catch (_: SecurityException) {
                                false
                            }
                            if (!again && keepInquiry && seen.isEmpty()) {
                                _error.value = "Classic Bluetooth scan stopped. Turn Bluetooth off and on, then tap Scan."
                            }
                        }, 400)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        inquiryReceiver = receiver
        val started = try {
            adapter.startDiscovery()
        } catch (_: SecurityException) {
            false
        }
        _scanning.value = started
        if (!started) {
            keepInquiry = false
            _error.value = "Classic Bluetooth scan did not start. Turn Location on, then tap Scan again."
            stopInquiry()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopInquiry() {
        keepInquiry = false
        _scanning.value = false
        mainHandler.removeCallbacksAndMessages(null)
        val adapter = bluetoothAdapter()
        try {
            adapter?.cancelDiscovery()
        } catch (_: Exception) {
        }
        inquiryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        inquiryReceiver = null
    }

    private fun noteEmptyPass() {
        if (reportedEmpty || seen.isNotEmpty()) return
        reportedEmpty = true
        val locationOn = try {
            (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
        } catch (_: Exception) {
            true
        }
        _error.value = when {
            !locationOn ->
                "Turn on Location. This phone hides Classic Bluetooth devices while Location is off."
            heardNames.isEmpty() ->
                "No Bluetooth device answered. Keep the ESP32 serial log on “pair it now”, turn phone Bluetooth off and on, then tap Scan."
            else ->
                "Heard ${heardNames.joinToString()}. None is named SafePath-CAM-001."
        }
    }

    @SuppressLint("MissingPermission")
    private fun publishBonded(adapter: BluetoothAdapter) {
        val bonded = try {
            adapter.bondedDevices
        } catch (_: SecurityException) {
            emptySet()
        }
        bonded.forEach { remember(it, null) }
    }

    @SuppressLint("MissingPermission")
    private fun remember(device: BluetoothDevice?, advertisedName: String?) {
        device ?: return
        val cached = try {
            device.name
        } catch (_: SecurityException) {
            null
        }
        val name = advertisedName?.trim()?.trim('"')?.takeIf { it.isNotBlank() }
            ?: cached?.trim()?.trim('"')?.takeIf { it.isNotBlank() }
            ?: return
        val matches = SafePathCameraNode.matchesCameraDeviceName(name) ||
            name.contains("SafePath", ignoreCase = true)
        if (!matches) return
        val node = SafePathCameraNode.fromBleAdvertisement(device, name, rssi = -70)
            ?: SafePathCameraNode(
                nodeId = "SP-CAM-001",
                deviceName = name,
                bluetoothAddress = device.address.orEmpty(),
                wifiSsid = name,
                rssi = -70,
                bluetoothDevice = device
            )
        seen[device.address] = node
        _nodes.value = seen.values.sortedBy { it.deviceName }
        _error.value = null
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @SuppressLint("MissingPermission")
    suspend fun capture(deviceName: String, address: String): CameraApiResult<Bitmap> =
        withContext(Dispatchers.IO) {
            stopInquiry()
            val adapter = bluetoothAdapter()
                ?: return@withContext CameraApiResult.Err("Bluetooth is not available on this phone.")
            if (!adapter.isEnabled) {
                return@withContext CameraApiResult.Err("Turn on Bluetooth, then tap Capture New Image.")
            }
            val name = deviceName.ifBlank { DEFAULT_NAME }
            val addressed = deviceForAddress(adapter, address)
            val addressedResult = addressed?.let { readPhoto(adapter, it) }
            if (addressedResult is CameraApiResult.Ok) return@withContext addressedResult
            if (addressedResult != null && address.isBlank()) return@withContext addressedResult
            val named = findNamedDevice(adapter, name, skipAddress = addressed?.address)
            if (named == null) {
                return@withContext addressedResult
                    ?: CameraApiResult.Err(
                        "$DEFAULT_NAME was not found over Bluetooth. Turn the camera on and tap Capture again."
                    )
            }
            readPhoto(adapter, named)
        }

    @SuppressLint("MissingPermission")
    private fun deviceForAddress(adapter: BluetoothAdapter, address: String): BluetoothDevice? {
        if (address.isBlank()) return null
        return runCatching { adapter.getRemoteDevice(address) }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun readPhoto(adapter: BluetoothAdapter, device: BluetoothDevice): CameraApiResult<Bitmap> {
        var socket: BluetoothSocket? = null
        try {
            try {
                adapter.cancelDiscovery()
            } catch (_: SecurityException) {
            }
            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
            val watchdog = Thread {
                try {
                    Thread.sleep(HEADER_TIMEOUT_MS.toLong())
                    timedOut.set(true)
                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }
                } catch (_: InterruptedException) {
                }
            }
            watchdog.isDaemon = true
            watchdog.start()
            try {
                socket.connect()
                socket.outputStream.apply {
                    write("c\n".toByteArray(Charsets.US_ASCII))
                    flush()
                }
                val input = socket.inputStream
                val header = readHeader(input)
                    ?: return CameraApiResult.Err("The camera did not send a photo header.")
                val length = header.substringAfter("SPIC,", "").trim().toIntOrNull()
                if (length == null || length <= 0 || length > MAX_JPEG_BYTES) {
                    return CameraApiResult.Err("The camera did not send a photo header.")
                }
                val bytes = readExactly(input, length)
                consumeTrailer(input)
                if (bytes.size < 2 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) {
                    return CameraApiResult.Err("Camera returned an invalid image.")
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return CameraApiResult.Err("Camera returned an invalid image.")
                return CameraApiResult.Ok(bitmap)
            } catch (t: IOException) {
                Log.w(TAG, "classic bt capture", t)
                val message = if (timedOut.get() || t.message?.contains("header", ignoreCase = true) == true) {
                    "The camera did not send a photo header."
                } else {
                    "Bluetooth connection failed."
                }
                return CameraApiResult.Err(message, t)
            } finally {
                watchdog.interrupt()
            }
        } catch (t: SecurityException) {
            return CameraApiResult.Err("Allow Bluetooth to capture a photo from $DEFAULT_NAME.", t)
        } catch (t: Throwable) {
            Log.w(TAG, "classic bt capture", t)
            return CameraApiResult.Err("Bluetooth connection failed.", t)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun findNamedDevice(
        adapter: BluetoothAdapter,
        name: String,
        skipAddress: String?
    ): BluetoothDevice? {
        val bonded = try {
            adapter.bondedDevices
        } catch (_: SecurityException) {
            emptySet()
        }
        bonded.firstOrNull {
            it.address != skipAddress &&
                (it.name.equals(name, ignoreCase = true) || SafePathCameraNode.matchesCameraDeviceName(it.name))
        }?.let { return it }
        val found = discover(adapter, name) ?: return null
        return if (found.address == skipAddress) null else found
    }

    @SuppressLint("MissingPermission")
    private suspend fun discover(adapter: BluetoothAdapter, name: String): BluetoothDevice? =
        suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (!cont.isActive) return
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = deviceExtra(intent) ?: return
                            val found = device.name
                            val match = found.equals(name, ignoreCase = true) ||
                                SafePathCameraNode.matchesCameraDeviceName(found)
                            if (!match) return
                            finish(this)
                            try {
                                adapter.cancelDiscovery()
                            } catch (_: SecurityException) {
                            }
                            cont.resume(device)
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            finish(this)
                            cont.resume(null)
                        }
                    }
                }

                private fun finish(receiver: BroadcastReceiver) {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (_: Exception) {
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            val started = try {
                adapter.startDiscovery()
            } catch (_: SecurityException) {
                false
            }
            if (!started) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation {
                try {
                    adapter.cancelDiscovery()
                } catch (_: Exception) {
                }
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }
        }

    private fun deviceExtra(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun readHeader(input: InputStream): String? {
        val lines = ArrayList<String>(4)
        repeat(8) {
            val line = readAsciiLine(input) ?: return null
            if (line.startsWith("SPIC,")) return line
            lines += line
        }
        Log.w(TAG, "no SPIC header in $lines")
        return null
    }

    private fun readAsciiLine(input: InputStream): String? {
        val out = StringBuilder()
        while (out.length < 80) {
            val b = input.read()
            if (b < 0) return if (out.isEmpty()) null else out.toString()
            if (b == '\n'.code) return out.toString().trimEnd('\r')
            out.append(b.toChar())
        }
        return null
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var off = 0
        while (off < length) {
            val n = input.read(buf, off, length - off)
            if (n < 0) throw IOException("The camera did not send a photo header.")
            off += n
        }
        return buf
    }

    private fun consumeTrailer(input: InputStream) {
        val deadline = System.currentTimeMillis() + TRAILER_TIMEOUT_MS
        val window = StringBuilder()
        while (System.currentTimeMillis() < deadline && !window.contains("SPEND")) {
            if (input.available() <= 0) {
                Thread.sleep(40)
                continue
            }
            val b = input.read()
            if (b < 0) return
            window.append(b.toChar())
            if (window.length > 32) return
        }
    }

    companion object {
        private const val TAG = "SafePathClassicBt"
        private const val DEFAULT_NAME = "SafePath-CAM-001"
        private const val HEADER_TIMEOUT_MS = 25_000
        private const val TRAILER_TIMEOUT_MS = 3_000
        private const val MAX_JPEG_BYTES = 250_000
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
