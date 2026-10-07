package com.hades.safepath.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shelters")
data class Shelter(
    @PrimaryKey
    val id: Long,
    val name: String,
    val district: String,
    val lat: Double,
    val lon: Double,
    val capacityPersons: Int,
    val isVerified: Boolean,
    /** Local emergency contact — shown on map bottom sheet; never requires network. */
    val contactPhone: String = ""
)
