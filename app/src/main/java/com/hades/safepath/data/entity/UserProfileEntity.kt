package com.hades.safepath.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Offline cache of the server-verified user profile.
 * Used for read-only offline display — gates must re-check Supabase session online.
 */
@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val phone: String = "",
    val email: String = "",
    val verified: Boolean,
    val verifiedAt: Long?,
    val tier: String = "free",
    val lastSyncedAt: Long = System.currentTimeMillis()
)
