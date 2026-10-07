package com.hades.safepath.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "guidance_steps")
data class GuidanceStep(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val disasterType: String,
    val severityLevel: String,
    val stepOrder: Int,
    val stepTextEn: String,
    val stepTextSi: String,
    val stepTextTa: String
)
