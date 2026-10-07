package com.hades.safepath.camera

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafePathCameraApiParseTest {

    private val api = SafePathCameraApi(Gson())

    @Test
    fun parsesFullStatusJson() {
        val json = """
            {
              "nodeId": "SP-CAM-001",
              "deviceName": "SafePath-CAM-001",
              "nodeType": "CAMERA",
              "wifiSsid": "SafePath-CAM-001",
              "ip": "192.168.4.1",
              "camera": true,
              "status": "online"
            }
        """.trimIndent()
        val status = api.parseStatus(json)
        assertEquals("SP-CAM-001", status.nodeId)
        assertEquals("SafePath-CAM-001", status.deviceName)
        assertEquals("CAMERA", status.nodeType)
        assertTrue(status.camera == true)
        assertTrue(status.isOnline)
        assertNull(status.latitude)
        assertNull(status.longitude)
    }

    @Test
    fun missingFieldsDoNotCrash() {
        val status = api.parseStatus("""{"status":"online"}""")
        assertNull(status.nodeId)
        assertTrue(status.isOnline)
    }

    @Test
    fun invalidJsonReturnsSafeDefault() {
        val status = api.parseStatus("not-json")
        assertEquals("unknown", status.status)
    }

    @Test
    fun futureGpsFieldsAreParsedWhenPresent() {
        val status = api.parseStatus(
            """{"status":"online","latitude":6.9271,"longitude":79.8612,"temperature":29.4}"""
        )
        assertEquals(6.9271, status.latitude!!, 0.0001)
        assertEquals(79.8612, status.longitude!!, 0.0001)
        assertEquals(29.4, status.temperature!!, 0.01)
    }
}

class SafePathCameraNodeNameTest {

    @Test
    fun matchesCameraNames() {
        assertTrue(SafePathCameraNode.matchesCameraDeviceName("SafePath-CAM-001"))
        assertTrue(SafePathCameraNode.matchesCameraDeviceName("SafePath-CAM-002"))
        assertTrue(SafePathCameraNode.matchesCameraDeviceName("safepath-cam-99"))
        assertFalse(SafePathCameraNode.matchesCameraDeviceName("SafePath-NODE-001"))
        assertFalse(SafePathCameraNode.matchesCameraDeviceName(null))
        assertFalse(SafePathCameraNode.matchesCameraDeviceName(""))
    }
}
