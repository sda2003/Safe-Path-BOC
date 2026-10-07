package com.hades.safepath.mesh

/**
 * Wire format for mesh note payloads (&lt;1KB JSON).
 * Shared across Nearby Connections hops and Room persistence.
 */
data class MeshNotePayload(
    val noteId: String,
    val text: String,
    val type: String = "info",
    val senderName: String = "Anonymous",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val hops: Int = 0,
    val ttlMs: Long = DEFAULT_TTL_MS
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        createdAt + ttlMs < now

    fun canForward(): Boolean = hops < MAX_HOPS && !isExpired()

    fun forwarded(): MeshNotePayload = copy(hops = hops + 1)

    companion object {
        const val DEFAULT_TTL_MS = 48L * 60L * 60L * 1000L
        const val MAX_HOPS = 5
        const val MAX_TEXT_CHARS = 280
        const val CLOUD_ROOM_ID = "safepath_mesh"
    }
}
