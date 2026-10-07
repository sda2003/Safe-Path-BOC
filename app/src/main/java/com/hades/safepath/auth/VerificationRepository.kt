package com.hades.safepath.auth

import com.hades.safepath.data.dao.UserProfileDao
import com.hades.safepath.data.entity.UserProfileEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

data class ServerUserProfile(
    val id: String,
    val phone: String,
    val email: String,
    val verified: Boolean,
    val tier: String
)

@Serializable
private data class UserVerificationRead(
    val id: String,
    val phone: String? = null,
    val email: String? = null,
    val verified: Boolean = false,
    @SerialName("verified_at") val verifiedAt: String? = null,
    val tier: String? = null
)

@Singleton
class VerificationRepository @Inject constructor(
    private val verificationService: VerificationService,
    private val sessionSecureStorage: SessionSecureStorage,
    private val userProfileDao: UserProfileDao,
    private val supabaseClient: SupabaseClient
) {

    suspend fun requestOtp(destination: VerificationDestination): Result<Unit> {
        return verificationService.requestOtp(destination)
    }

    suspend fun verifyOtp(destination: VerificationDestination, code: String): Result<VerifiedUser> {
        return verificationService.verifyOtp(destination, code).fold(
            onSuccess = { user ->
                cacheVerifiedUser(user)
                Result.success(user)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun cacheVerifiedUser(user: VerifiedUser) {
        userProfileDao.upsert(
            UserProfileEntity(
                id = user.id,
                phone = user.phone,
                email = user.email,
                verified = true,
                verifiedAt = user.verifiedAt,
                tier = user.tier,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun isVerifiedOnServer(): Boolean {
        return getServerUserProfile()?.verified == true
    }

    suspend fun getServerUserProfile(): ServerUserProfile? {
        return runCatching {
            val session = supabaseClient.auth.currentSessionOrNull() ?: return null
            val userId = session.user?.id ?: return null
            val row = supabaseClient.postgrest["users"].select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<UserVerificationRead>() ?: return null

            val phone = row.phone.orEmpty().ifBlank { session.user?.phone.orEmpty() }
            val email = row.email.orEmpty().ifBlank { session.user?.email.orEmpty() }
            val hasContact = phone.isNotBlank() || email.isNotBlank()

            ServerUserProfile(
                id = userId,
                phone = phone,
                email = email,
                verified = row.verified && hasContact,
                tier = row.tier?.lowercase() ?: "free"
            )
        }.getOrNull()
    }

    fun observeCachedProfile(): Flow<UserProfileEntity?> = userProfileDao.observeCurrent()

    suspend fun getVerifiedProfileForBroadcast(): ServerUserProfile? {
        getServerUserProfile()?.takeIf { it.verified }?.let { return it }
        val cached = userProfileDao.getCurrent() ?: return null
        if (!cached.verified) return null
        val contactId = cached.phone.ifBlank { cached.email }
        if (contactId.isBlank()) return null
        return ServerUserProfile(
            id = cached.id,
            phone = cached.phone.ifBlank { cached.email },
            email = cached.email,
            verified = true,
            tier = cached.tier
        )
    }

    suspend fun restoreSessionIfNeeded() {
        val saved = sessionSecureStorage.loadSession() ?: return
        runCatching {
            supabaseClient.auth.importSession(saved, autoRefresh = true)
        }
    }

    suspend fun signOut() {
        runCatching { supabaseClient.auth.signOut() }
        sessionSecureStorage.clear()
    }
}
