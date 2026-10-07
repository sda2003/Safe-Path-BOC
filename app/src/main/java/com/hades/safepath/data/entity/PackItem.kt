package com.hades.safepath.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An item the user should gather before evacuating.
 * priority: 1 = Critical, 2 = Important, 3 = Optional
 */
@Entity(tableName = "pack_items")
data class PackItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val disasterType: String,
    val itemNameEn: String,
    val priority: Int
)
