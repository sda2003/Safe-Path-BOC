package com.hades.safepath.data.repository

import android.util.Log
import com.hades.safepath.data.local.AlertDao
import com.hades.safepath.data.model.Alert
import com.hades.safepath.data.remote.SupabaseConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepository @Inject constructor(
    private val alertDao: AlertDao,
    private val supabaseClient: SupabaseClient
) {
    private val db get() = supabaseClient.postgrest
    private val realtime get() = supabaseClient.realtime
    private val auth get() = supabaseClient.auth

    fun getCurrentAlert(): Flow<Alert?> = alertDao.getCurrentAlert()
    fun getLastAlert(): Flow<Alert?> = alertDao.getLastAlert()

    suspend fun saveAlert(alert: Alert) {
        alertDao.insertAlert(alert)
    }

    @kotlinx.serialization.Serializable
    private data class AlertInsert(
        val id: String,
        val type: String,
        val severity: String,
        val description: String,
        val latitude: Double,
        val longitude: Double,
        val district: String,
        @kotlinx.serialization.SerialName("user_id") val userId: String,
        @kotlinx.serialization.SerialName("is_active") val isActive: Boolean,
        @kotlinx.serialization.SerialName("created_at") val createdAt: String
    )

    /**
     * Always persists locally so the map/home work without Supabase.
     * Attempts cloud sync when credentials are real; never fails the user
     * solely because the backend is misconfigured.
     */
    suspend fun reportAlert(
        type: String,
        severity: String,
        description: String,
        latitude: Double,
        longitude: Double,
        district: String
    ): Result<Unit> {
        val id = UUID.randomUUID().toString()
        val createdAt = isoNow()
        val userId = auth.currentSessionOrNull()?.user?.id ?: "local"
        val local = Alert(
            id = id,
            userId = userId,
            type = type,
            severity = severity,
            description = description,
            latitude = latitude,
            longitude = longitude,
            district = district,
            isActive = true,
            createdAt = createdAt
        )

        return try {
            alertDao.insertAlert(local)

            if (!SupabaseConfig.isConfigured()) {
                Log.w(TAG, "Supabase not configured — alert saved locally only")
                return Result.success(Unit)
            }

            runCatching {
                db["alerts"].insert(
                    AlertInsert(
                        id = id,
                        type = type,
                        severity = severity,
                        description = description,
                        latitude = latitude,
                        longitude = longitude,
                        district = district,
                        userId = userId,
                        isActive = true,
                        createdAt = createdAt
                    )
                )
            }.onFailure { e ->
                Log.e(TAG, "Cloud alert sync failed; kept local copy", e)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "reportAlert failed", e)
            Result.failure(e)
        }
    }

    /** Remote alerts (if any) merged with on-device reports. */
    suspend fun getActiveAlerts(): List<Alert> {
        val local = runCatching { alertDao.getActiveAlertsList() }.getOrDefault(emptyList())
        val remote = if (SupabaseConfig.isConfigured()) {
            try {
                db["alerts"].select {
                    filter { eq("is_active", true) }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(50)
                }.decodeList<Alert>()
            } catch (e: Exception) {
                Log.e(TAG, "getActiveAlerts remote failed", e)
                emptyList()
            }
        } else {
            emptyList()
        }

        val byId = LinkedHashMap<String, Alert>()
        // Prefer local first so just-reported alerts always show
        local.forEach { byId[it.id] = it }
        remote.forEach { if (!byId.containsKey(it.id)) byId[it.id] = it }
        return byId.values.toList()
    }

    fun observeAlerts(): Flow<List<Alert>> = kotlinx.coroutines.flow.flow {
        emit(getActiveAlerts())
        if (!SupabaseConfig.isConfigured()) return@flow
        try {
            val channel = realtime.channel("alerts-channel")
            val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "alerts"
            }
            channel.subscribe(blockUntilSubscribed = true)
            changes.collect {
                emit(getActiveAlerts())
            }
        } catch (e: Exception) {
            Log.e(TAG, "observeAlerts failed", e)
        }
    }

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    companion object {
        private const val TAG = "AlertRepository"
    }
}
