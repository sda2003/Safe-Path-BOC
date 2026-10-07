package com.hades.safepath.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hazard_nodes")
data class HazardNodeEntity(
    @PrimaryKey
    val nodeId: String,
    val latitude: Double,
    val longitude: Double,
    val temperature: Double,
    val humidity: Double,
    val pressure: Double,
    val rainfall: Double,
    val waterLevel: Double,
    val soilMoisture: Double,
    val battery: Int,
    val riskLevel: String, // E.g., "LOW", "MODERATE", "HIGH", "CRITICAL"
    val timestamp: Long,
    val cameraAvailable: Boolean
)
