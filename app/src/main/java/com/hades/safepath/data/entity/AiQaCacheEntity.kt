package com.hades.safepath.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_qa_cache",
    indices = [Index(value = ["questionKey"], unique = true)]
)
data class AiQaCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Normalized question key for cache hits. */
    val questionKey: String,
    val question: String,
    val answer: String,
    val topic: String = "general",
    val createdAt: Long = System.currentTimeMillis(),
    val fromNetwork: Boolean = true
)
