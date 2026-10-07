package com.hades.safepath.data.repository

import android.util.Log
import com.hades.safepath.data.model.ChatMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val db get() = supabaseClient.postgrest
    private val realtime get() = supabaseClient.realtime
    private val auth get() = supabaseClient.auth

    @kotlinx.serialization.Serializable
    private data class ChatMessageInsert(
        @kotlinx.serialization.SerialName("room_id") val roomId: String,
        @kotlinx.serialization.SerialName("user_id") val userId: String,
        @kotlinx.serialization.SerialName("sender_name") val senderName: String,
        @kotlinx.serialization.SerialName("is_anonymous") val isAnonymous: Boolean,
        val message: String,
        @kotlinx.serialization.SerialName("image_url") val imageUrl: String? = null
    )

    suspend fun sendMessage(
        roomId: String,
        message: String,
        senderName: String,
        isAnonymous: Boolean,
        imageUrl: String? = null
    ): Result<Unit> {
        return try {
            val currentUser = auth.currentSessionOrNull()?.user
                ?: auth.currentUserOrNull()
            val finalUserId = currentUser?.id
                ?: return Result.failure(
                    IllegalStateException("Sign in to send messages. Guest sessions cannot post to chat.")
                )

            val insertModel = ChatMessageInsert(
                roomId = roomId,
                userId = finalUserId,
                senderName = if (isAnonymous) "Anonymous" else senderName.ifBlank { "SafePath User" },
                isAnonymous = isAnonymous,
                message = message.ifBlank { if (imageUrl != null) " " else "" },
                imageUrl = imageUrl
            )
            db["chat_messages"].insert(insertModel)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed", e)
            Result.failure(e)
        }
    }

    suspend fun uploadImage(byteArray: ByteArray): Result<String> {
        return try {
            val userId = auth.currentSessionOrNull()?.user?.id
                ?: return Result.failure(IllegalStateException("Sign in to upload images."))
            val timestamp = System.currentTimeMillis()
            val path = "$userId/$timestamp.jpg"

            // Matches schema.sql bucket name
            val bucket = supabaseClient.storage[CHAT_MEDIA_BUCKET]
            bucket.upload(path, byteArray, upsert = true)

            val publicUrl = bucket.publicUrl(path)
            Result.success(publicUrl)
        } catch (e: Exception) {
            Log.e(TAG, "uploadImage failed", e)
            Result.failure(e)
        }
    }

    suspend fun getMessages(roomId: String): List<ChatMessage> {
        return try {
            db["chat_messages"].select {
                filter { eq("room_id", roomId) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                limit(100)
            }.decodeList<ChatMessage>()
        } catch (e: Exception) {
            Log.e(TAG, "getMessages failed for room=$roomId", e)
            emptyList()
        }
    }

    fun observeMessages(roomId: String): Flow<List<ChatMessage>> = callbackFlow {
        trySend(getMessages(roomId))

        val channel = realtime.channel("chat-$roomId")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "chat_messages"
            filter = "room_id=eq.$roomId"
        }

        val job = launch {
            try {
                channel.subscribe(blockUntilSubscribed = true)
                changes.collect {
                    trySend(getMessages(roomId))
                }
            } catch (e: Exception) {
                Log.e(TAG, "realtime chat subscribe failed", e)
            }
        }

        awaitClose {
            job.cancel()
            launch {
                runCatching { channel.unsubscribe() }
            }
        }
    }

    companion object {
        private const val TAG = "ChatRepository"
        /** Must match bucket created in schema.sql */
        const val CHAT_MEDIA_BUCKET = "chat_media"
    }
}
