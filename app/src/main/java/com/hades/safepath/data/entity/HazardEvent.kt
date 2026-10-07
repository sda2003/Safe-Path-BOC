package com.hades.safepath.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hades.safepath.data.model.DisasterType

@Entity(tableName = "hazard_events")
data class HazardEvent(
    @PrimaryKey
    val id: String,
    val type: String, // E.g. "FLOOD", "LANDSLIDE"
    val latitude: Double,
    val longitude: Double,
    val severity: String,
    val timestamp: Long,
    val source: String,
    val confidence: Double,
    val expirationTime: Long,
    val description: String,
    val affectedRoadIds: String // Comma-separated list for simplicity in Room
)
