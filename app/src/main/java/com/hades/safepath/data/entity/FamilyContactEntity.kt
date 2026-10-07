package com.hades.safepath.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_contacts")
data class FamilyContactEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val contactName: String,
    val contactPhone: String,
    val syncedAt: Long = System.currentTimeMillis()
)
