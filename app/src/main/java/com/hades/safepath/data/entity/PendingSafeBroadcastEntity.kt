package com.hades.safepath.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_safe_broadcasts")
data class PendingSafeBroadcastEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val senderId: String,
    val senderPhone: String,
    val latitude: Double,
    val longitude: Double,
    val status: String = "safe",
    val message: String = "I'm safe",
    val createdAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0
)
