package com.hades.safepath.network.dto

import com.google.gson.annotations.SerializedName
import com.hades.safepath.data.model.Alert

data class CloudAlertDto(
    val id: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    val type: String = "",
    val severity: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val district: String = "",
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("risk_score") val riskScore: Int? = null,
    val source: String? = null,
) {
    fun toAlert(): Alert = Alert(
        id = id.orEmpty(),
        userId = userId.orEmpty(),
        type = type.replaceFirstChar { it.uppercase() },
        severity = severity,
        description = description,
        latitude = latitude,
        longitude = longitude,
        district = district,
        isActive = isActive,
        createdAt = createdAt.orEmpty(),
    )

    companion object {
        fun from(alert: Alert): CloudAlertDto = CloudAlertDto(
            id = alert.id,
            userId = alert.userId,
            type = alert.type,
            severity = alert.severity,
            description = alert.description,
            latitude = alert.latitude,
            longitude = alert.longitude,
            district = alert.district.ifBlank { "Unknown" },
            isActive = alert.isActive,
            createdAt = alert.createdAt,
        )
    }
}
