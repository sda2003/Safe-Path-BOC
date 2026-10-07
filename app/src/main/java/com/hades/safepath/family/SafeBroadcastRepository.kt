package com.hades.safepath.family

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hades.safepath.auth.VerificationRepository
import com.hades.safepath.data.dao.PendingSafeBroadcastDao
import com.hades.safepath.data.entity.PendingSafeBroadcastEntity
import com.hades.safepath.location.LastLocationCache
import com.hades.safepath.util.NetworkMonitor
import com.hades.safepath.worker.SafeBroadcastWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SafeBroadcastRecord(
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_phone") val senderPhone: String,
    val status: String = "safe",
    val latitude: Double,
    val longitude: Double,
    val message: String = "I'm safe"
)

sealed class ImSafeResult {
    data object Sent : ImSafeResult()
    data object Queued : ImSafeResult()
    data class Error(val message: String) : ImSafeResult()
}

@Singleton
class SafeBroadcastRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseClient: SupabaseClient,
    private val verificationRepository: VerificationRepository,
    private val pendingDao: PendingSafeBroadcastDao,
    private val lastLocationCache: LastLocationCache,
    private val networkMonitor: NetworkMonitor
) {

    suspend fun sendImSafe(): ImSafeResult {
        val profile = verificationRepository.getVerifiedProfileForBroadcast()
            ?: return ImSafeResult.Error("Verify your phone to send I'm Safe updates.")

        val (lat, lon) = lastLocationCache.getLastKnownCoordinates()
        val pending = PendingSafeBroadcastEntity(
            senderId = profile.id,
            senderPhone = profile.phone,
            latitude = lat,
            longitude = lon,
            status = "safe",
            message = "I'm safe"
        )

        val localId = pendingDao.insert(pending)

        if (!com.hades.safepath.data.remote.SupabaseConfig.isConfigured()) {
            scheduleFlushWorker()
            return ImSafeResult.Queued
        }

        if (networkMonitor.isOnline()) {
            val sent = trySendPending(pending.copy(localId = localId))
            if (sent) {
                pendingDao.delete(localId)
                return ImSafeResult.Sent
            }
        }

        scheduleFlushWorker()
        return ImSafeResult.Queued
    }

    suspend fun flushPendingQueue(): Int {
        if (!networkMonitor.isOnline()) return 0
        var sentCount = 0
        val pending = pendingDao.getAllPending()
        for (item in pending) {
            if (trySendPending(item)) {
                pendingDao.delete(item.localId)
                sentCount++
            } else {
                pendingDao.incrementAttempt(item.localId)
            }
        }
        return sentCount
    }

    private suspend fun trySendPending(item: PendingSafeBroadcastEntity): Boolean {
        return runCatching {
            supabaseClient.postgrest["safe_broadcasts"].insert(
                SafeBroadcastRecord(
                    senderId = item.senderId,
                    senderPhone = item.senderPhone,
                    status = item.status,
                    latitude = item.latitude,
                    longitude = item.longitude,
                    message = item.message
                )
            )
            true
        }.getOrDefault(false)
    }

    fun scheduleFlushWorker() {
        val request = OneTimeWorkRequestBuilder<SafeBroadcastWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SafeBroadcastWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
