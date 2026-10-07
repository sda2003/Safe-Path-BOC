package com.hades.safepath.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_log")
data class AlertLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val signalCode: Int,
    val receivedAt: Long,
    val disasterType: String,
    val severityLevel: String,
    val lat: Double,
    val lon: Double
)
