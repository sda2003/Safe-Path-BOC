package com.hades.safepath.hardware

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "camera_events")
data class CameraEvent(
    @PrimaryKey val eventId: String,
    val nodeId: String,
    val imagePath: String, // Local URI or File Path
    val timestamp: Long,
    val triggerReason: String // e.g., "VIBRATION_SPIKE", "WATER_LEVEL_CRITICAL"
)
