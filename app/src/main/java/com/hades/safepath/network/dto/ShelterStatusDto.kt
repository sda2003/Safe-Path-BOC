package com.hades.safepath.network.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for live shelter status from the SafePath backend API.
 * Maps to GET v1/shelters/{district}/live response items.
 */
data class ShelterStatusDto(
    @SerializedName("shelter_name")
    val shelterName: String,

    @SerializedName("available_capacity")
    val availableCapacity: Int,

    @SerializedName("is_open")
    val isOpen: Boolean
)
