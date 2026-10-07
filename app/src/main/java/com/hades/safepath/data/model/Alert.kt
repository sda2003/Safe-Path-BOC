package com.hades.safepath.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "alerts")
@Serializable
data class Alert(
    @PrimaryKey val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val type: String = "",
    val severity: String = "MODERATE",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val district: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String = ""
)
