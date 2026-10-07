package com.hades.safepath.camera

import android.net.Network
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class CameraApiResult<out T> {
    data class Ok<T>(val value: T) : CameraApiResult<T>()
    data class Err(val message: String, val cause: Throwable? = null) : CameraApiResult<Nothing>()
}

/**
 * Local SoftAP HTTP client for ESP32-CAM.
 * Prefer [Network.openConnection] so traffic stays on the camera Wi‑Fi
 * without requiring global internet.
 */
@Singleton
class SafePathCameraApi @Inject constructor(
    private val gson: Gson
) {
    suspend fun fetchStatus(
        network: Network?,
        ip: String = SafePathCameraNode.DEFAULT_WIFI_IP
    ): CameraApiResult<CameraStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ip/status")
            val first = getText(network, url)
            if (first is CameraApiResult.Ok) return@withContext CameraApiResult.Ok(parseStatus(first.value))
            val info = getText(network, URL("http://$ip/info"))
            if (info is CameraApiResult.Ok) return@withContext CameraApiResult.Ok(parseStatus(info.value))
            val failed = when {
                info is CameraApiResult.Err -> info
                first is CameraApiResult.Err -> first
                else -> CameraApiResult.Err("The camera Wi-Fi is up, but http://$ip did not answer.")
            }
            CameraApiResult.Err(failed.message, failed.cause)
        } catch (t: java.net.SocketTimeoutException) {
            CameraApiResult.Err("The camera did not respond.", t)
        } catch (t: Throwable) {
            Log.w(TAG, "status failed", t)
            CameraApiResult.Err("The camera Wi-Fi is up, but http://$ip did not answer.", t)
        }
    }

    private fun getText(network: Network?, url: URL): CameraApiResult<String> {
        return try {
            val conn = open(network, url)
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json, */*")
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return CameraApiResult.Err("Camera node is unavailable. (HTTP $code)")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            CameraApiResult.Ok(body)
        } catch (t: java.net.SocketTimeoutException) {
            CameraApiResult.Err("The camera did not respond.", t)
        } catch (t: Throwable) {
            CameraApiResult.Err("The camera Wi-Fi is up, but ${url.path} did not answer.", t)
        }
    }

    suspend fun captureJpeg(
        network: Network?,
        ip: String = SafePathCameraNode.DEFAULT_WIFI_IP,
        maxDecodeEdge: Int = 1280
    ): CameraApiResult<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ip/capture")
            val conn = open(network, url)
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = CAPTURE_TIMEOUT_MS
            conn.setRequestProperty("Accept", "image/jpeg")
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return@withContext CameraApiResult.Err("Camera node is unavailable. (HTTP $code)")
            }
            val bytes = BufferedInputStream(conn.inputStream).use { it.readBytes() }
            conn.disconnect()
            if (bytes.isEmpty()) {
                return@withContext CameraApiResult.Err("Camera returned an invalid image.")
            }
            val bitmap = decodeSampled(bytes, maxDecodeEdge)
                ?: return@withContext CameraApiResult.Err("Camera returned an invalid image.")
            CameraApiResult.Ok(bitmap)
        } catch (t: java.net.SocketTimeoutException) {
            CameraApiResult.Err("The camera did not respond.", t)
        } catch (t: Throwable) {
            Log.w(TAG, "capture failed", t)
            CameraApiResult.Err("Camera node is unavailable.", t)
        }
    }

    fun parseStatus(json: String): CameraStatusResponse {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java) ?: return CameraStatusResponse()
            CameraStatusResponse(
                nodeId = obj.str("nodeId"),
                deviceName = obj.str("deviceName"),
                nodeType = obj.str("nodeType"),
                wifiSsid = obj.str("wifiSsid"),
                ip = obj.str("ip"),
                camera = obj.bool("camera"),
                status = obj.str("status"),
                latitude = obj.dbl("latitude"),
                longitude = obj.dbl("longitude"),
                temperature = obj.dbl("temperature"),
                humidity = obj.dbl("humidity"),
                pressure = obj.dbl("pressure"),
                battery = obj.int("battery"),
                waterLevel = obj.dbl("waterLevel")
            )
        } catch (t: Throwable) {
            Log.w(TAG, "parseStatus", t)
            CameraStatusResponse(status = "unknown")
        }
    }

    private fun open(network: Network?, url: URL): HttpURLConnection {
        val conn = if (network != null) {
            network.openConnection(url) as HttpURLConnection
        } else {
            url.openConnection() as HttpURLConnection
        }
        conn.instanceFollowRedirects = false
        return conn
    }

    private fun decodeSampled(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (w / sample > maxEdge || h / sample > maxEdge) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun JsonObject.str(key: String): String? =
        runCatching { if (has(key) && !get(key).isJsonNull) get(key).asString else null }.getOrNull()

    private fun JsonObject.bool(key: String): Boolean? =
        runCatching { if (has(key) && !get(key).isJsonNull) get(key).asBoolean else null }.getOrNull()

    private fun JsonObject.dbl(key: String): Double? =
        runCatching { if (has(key) && !get(key).isJsonNull) get(key).asDouble else null }.getOrNull()

    private fun JsonObject.int(key: String): Int? =
        runCatching { if (has(key) && !get(key).isJsonNull) get(key).asInt else null }.getOrNull()

    companion object {
        private const val TAG = "SafePathCameraApi"
        private val TIMEOUT_MS = TimeUnit.SECONDS.toMillis(15).toInt()
        private val CAPTURE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20).toInt()
    }
}
