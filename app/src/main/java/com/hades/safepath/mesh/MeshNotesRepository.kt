package com.hades.safepath.mesh

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hades.safepath.data.dao.MeshNoteDao
import com.hades.safepath.data.entity.MeshNoteEntity
import com.hades.safepath.location.LastLocationCache
import com.hades.safepath.p2p.P2pManager
import com.hades.safepath.util.NetworkMonitor
import com.hades.safepath.worker.MeshSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class MeshPostResult {
    data object SavedOnline : MeshPostResult()
    data object SavedMesh : MeshPostResult()
    data class Error(val message: String) : MeshPostResult()
}

@Singleton
class MeshNotesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meshNoteDao: MeshNoteDao,
    private val p2pManager: P2pManager,
    private val networkMonitor: NetworkMonitor,
    private val lastLocationCache: LastLocationCache,
    private val supabaseClient: SupabaseClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        p2pManager.setOnNoteReceived { payload ->
            scope.launch { ingestFromMesh(payload) }
        }
    }

    fun observeNotes(): Flow<List<MeshNoteEntity>> = meshNoteDao.observeAll()

    fun peerCount() = p2pManager.peerCount

    fun isMeshActive() = p2pManager.isActive

    fun startMesh() {
        p2pManager.start()
    }

    fun stopMesh() {
        p2pManager.stop()
    }

    suspend fun postNote(
        text: String,
        type: String = "info",
        senderName: String = "Anonymous",
        includeLocation: Boolean = true
    ): MeshPostResult {
        val trimmed = text.trim().take(MeshNotePayload.MAX_TEXT_CHARS)
        if (trimmed.isEmpty()) {
            return MeshPostResult.Error("Enter a short note.")
        }

        meshNoteDao.deleteExpired(System.currentTimeMillis())

        val (lat, lon) = if (includeLocation) {
            lastLocationCache.getLastKnownCoordinates()
        } else {
            null to null
        }

        val payload = MeshNotePayload(
            noteId = UUID.randomUUID().toString(),
            text = trimmed,
            type = type.ifBlank { "info" },
            senderName = senderName.ifBlank { "Anonymous" },
            latitude = lat,
            longitude = lon,
            createdAt = System.currentTimeMillis(),
            hops = 0
        )

        val entity = payload.toEntity(source = "local", syncedToCloud = false)
        meshNoteDao.insertIgnore(entity)

        return if (networkMonitor.isOnline()) {
            val synced = trySyncToCloud(entity)
            if (synced) {
                meshNoteDao.markSynced(payload.noteId)
                MeshPostResult.SavedOnline
            } else {
                // Still share locally via mesh while cloud is flaky
                p2pManager.broadcast(payload)
                scheduleSyncWorker()
                MeshPostResult.SavedMesh
            }
        } else {
            p2pManager.broadcast(payload)
            scheduleSyncWorker()
            MeshPostResult.SavedMesh
        }
    }

    suspend fun flushUnsyncedToCloud(): Int {
        if (!networkMonitor.isOnline()) return 0
        meshNoteDao.deleteExpired(System.currentTimeMillis())
        var sent = 0
        for (note in meshNoteDao.getUnsynced()) {
            if (trySyncToCloud(note)) {
                meshNoteDao.markSynced(note.noteId)
                sent++
            }
        }
        return sent
    }

    fun scheduleSyncWorker() {
        val request = OneTimeWorkRequestBuilder<MeshSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MeshSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private suspend fun ingestFromMesh(payload: MeshNotePayload) {
        if (payload.isExpired()) return
        if (meshNoteDao.getByNoteId(payload.noteId) != null) return

        val entity = payload.toEntity(source = "mesh", syncedToCloud = false)
        val inserted = meshNoteDao.insertIgnore(entity)
        if (inserted == -1L) return

        Log.d(TAG, "Ingested mesh note ${payload.noteId} hops=${payload.hops}")

        if (payload.canForward()) {
            p2pManager.broadcast(payload.forwarded())
        }

        if (networkMonitor.isOnline()) {
            if (trySyncToCloud(entity)) {
                meshNoteDao.markSynced(payload.noteId)
            } else {
                scheduleSyncWorker()
            }
        } else {
            scheduleSyncWorker()
        }
    }

    private suspend fun trySyncToCloud(note: MeshNoteEntity): Boolean {
        return runCatching {
            val auth = supabaseClient.auth
            val userId = auth.currentSessionOrNull()?.user?.id
                ?: "mesh-${note.noteId.take(8)}"
            val message = buildCloudMessage(note)
            supabaseClient.postgrest["chat_messages"].insert(
                MeshCloudInsert(
                    roomId = MeshNotePayload.CLOUD_ROOM_ID,
                    userId = userId,
                    senderName = note.senderName,
                    isAnonymous = false,
                    message = message
                )
            )
            true
        }.onFailure {
            Log.w(TAG, "Cloud sync failed for ${note.noteId}: ${it.message}")
        }.getOrDefault(false)
    }

    private fun buildCloudMessage(note: MeshNoteEntity): String {
        val loc = if (note.latitude != null && note.longitude != null) {
            " @${"%.4f".format(note.latitude)},${"%.4f".format(note.longitude)}"
        } else {
            ""
        }
        return "[mesh:${note.type}] ${note.text}$loc | id=${note.noteId}"
    }

    private fun MeshNotePayload.toEntity(source: String, syncedToCloud: Boolean) =
        MeshNoteEntity(
            noteId = noteId,
            text = text,
            type = type,
            senderName = senderName,
            latitude = latitude,
            longitude = longitude,
            createdAt = createdAt,
            hops = hops,
            ttlMs = ttlMs,
            source = source,
            syncedToCloud = syncedToCloud
        )

    @Serializable
    private data class MeshCloudInsert(
        @SerialName("room_id") val roomId: String,
        @SerialName("user_id") val userId: String,
        @SerialName("sender_name") val senderName: String,
        @SerialName("is_anonymous") val isAnonymous: Boolean,
        val message: String
    )

    companion object {
        private const val TAG = "MeshNotesRepository"
    }
}
