package com.hades.safepath.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

object PersonalizedPackConstants {
    const val DISASTER_TYPE = "PERSONALIZED"
    const val TIPS_SEVERITY = "TIPS"
}

/** One row — tracks that Gemini ran once and stores the household profile snapshot. */
@Entity(tableName = "personalized_pack_meta")
data class PersonalizedPackMetaEntity(
    @PrimaryKey val id: Int = 1,
    val householdSize: Int,
    val hasKids: Boolean,
    val kidsAges: String,
    val hasElderly: Boolean,
    val hasPets: Boolean,
    val petType: String,
    val medicalConditions: String,
    val generatedAt: Long,
    val usedDefaultFallback: Boolean = false
)
