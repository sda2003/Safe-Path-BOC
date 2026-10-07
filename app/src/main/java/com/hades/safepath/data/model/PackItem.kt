package com.hades.safepath.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pack_items")
data class PackItem(
    @PrimaryKey val id: String,
    val label: String,
    val section: String,   // "critical", "important", "optional"
    val isChecked: Boolean = false
)
