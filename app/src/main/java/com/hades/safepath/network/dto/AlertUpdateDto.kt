package com.hades.safepath.network.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for alert updates from the SafePath backend API.
 * Maps to GET v1/alerts/latest response items.
 */
data class AlertUpdateDto(
    @SerializedName("signal_code")
    val signalCode: Int,

    @SerializedName("issued_at")
    val issuedAt: Long,

    @SerializedName("district")
    val district: String
)
