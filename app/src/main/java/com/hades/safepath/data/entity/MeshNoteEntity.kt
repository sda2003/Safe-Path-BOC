package com.hades.safepath.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mesh_notes",
    indices = [Index(value = ["noteId"], unique = true)]
)
data class MeshNoteEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    /** Stable UUID shared across devices for dedupe. */
    val noteId: String,
    val text: String,
    /** hazard | need | safe | info */
    val type: String = "info",
    val senderName: String = "Anonymous",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** How many phones this note has hopped through. */
    val hops: Int = 0,
    val ttlMs: Long = 48L * 60L * 60L * 1000L,
    /** Origin: local | mesh | cloud */
    val source: String = "local",
    val syncedToCloud: Boolean = false,
    val receivedAt: Long = System.currentTimeMillis()
)
