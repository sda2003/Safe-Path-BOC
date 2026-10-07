package com.hades.safepath.auth

import android.util.Log
import com.hades.safepath.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.OTP
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class UserVerificationRecord(
    val id: String,
    val phone: String? = null,
    val email: String? = null,
    val verified: Boolean,
    @SerialName("verified_at") val verifiedAt: String,
    val tier: String = "free",
    val name: String = "SafePath User",
    val district: String = ""
)

@Singleton
class SupabaseVerificationService @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val sessionSecureStorage: SessionSecureStorage,
    private val pendingSignupStore: PendingSignupStore
) : VerificationService {

    private val auth get() = supabaseClient.auth

    override suspend fun requestOtp(destination: VerificationDestination): Result<Unit> {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Supabase is not configured. Add keys to local.properties."))
        }

        return runCatching {
            // Keep pending signup data; only clear an existing auth session.
            runCatching { auth.signOut() }
            sessionSecureStorage.clear()

            when (destination) {
                is VerificationDestination.Phone -> {
                    auth.signInWith(OTP) {
                        phone = destination.e164
                    }
                }
                is VerificationDestination.Email -> {
                    auth.signInWith(OTP) {
                        email = destination.address
                    }
                }
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error ->
                Log.e(TAG, "requestOtp failed for ${destination.displayValue}", error)
                Result.failure(error)
            }
        )
    }

    override suspend fun verifyOtp(
        destination: VerificationDestination,
        code: String
    ): Result<VerifiedUser> {
        val token = code.trim()
        if (token.length < 4) {
            return Result.failure(InvalidOtpException())
        }

        return runCatching {
            when (destination) {
                is VerificationDestination.Phone -> {
                    auth.verifyPhoneOtp(
                        type = OtpType.Phone.SMS,
                        phone = destination.e164,
                        token = token
                    )
                }
                is VerificationDestination.Email -> {
                    verifyEmailToken(destination.address, token)
                }
            }

            val session = auth.currentSessionOrNull()
                ?: error("Verification succeeded but no session was created.")
            val user = session.user ?: error("Verification succeeded but user is missing.")

            sessionSecureStorage.saveSession(session)

            val pending = pendingSignupStore.take()
            val verifiedAt = System.currentTimeMillis()
            val verifiedUser = when (destination) {
                is VerificationDestination.Phone -> VerifiedUser(
                    id = user.id,
                    phone = user.phone ?: destination.e164,
                    email = user.email.orEmpty().ifBlank { pending?.email.orEmpty() },
                    verifiedAt = verifiedAt,
                    tier = "free",
                    channel = VerificationChannel.PHONE
                )
                is VerificationDestination.Email -> VerifiedUser(
                    id = user.id,
                    phone = user.phone.orEmpty(),
                    email = user.email ?: destination.address,
                    verifiedAt = verifiedAt,
                    tier = "free",
                    channel = VerificationChannel.EMAIL
                )
            }

            syncVerifiedUserToSupabase(verifiedUser, pending)
            verifiedUser
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Log.e(TAG, "verifyOtp failed for ${destination.displayValue}", error)
                Result.failure(error)
            }
        )
    }

    /**
     * Supabase may issue email OTP under different types depending on template/config.
     * Try EMAIL, then MAGICLINK, then SIGNUP.
     */
    private suspend fun verifyEmailToken(email: String, token: String) {
        val types = listOf(
            OtpType.Email.EMAIL,
            OtpType.Email.MAGIC_LINK,
            OtpType.Email.SIGNUP
        )
        var lastError: Throwable? = null
        for (type in types) {
            try {
                auth.verifyEmailOtp(type = type, email = email, token = token)
                return
            } catch (e: Throwable) {
                lastError = e
                Log.w(TAG, "verifyEmailOtp type=$type failed: ${e.message}")
            }
        }
        throw lastError ?: InvalidOtpException()
    }

    private suspend fun syncVerifiedUserToSupabase(
        user: VerifiedUser,
        pending: PendingSignupStore.Pending?
    ) {
        runCatching {
            supabaseClient.postgrest["users"].upsert(
                UserVerificationRecord(
                    id = user.id,
                    phone = user.phone.takeIf { it.isNotBlank() },
                    email = user.email.takeIf { it.isNotBlank() } ?: pending?.email,
                    verified = true,
                    verifiedAt = Instant.ofEpochMilli(user.verifiedAt).toString(),
                    tier = user.tier,
                    name = pending?.name?.takeIf { it.isNotBlank() } ?: "SafePath User",
                    district = pending?.district.orEmpty()
                )
            )
        }.onFailure { Log.e(TAG, "users upsert failed", it) }
    }

    private companion object {
        const val TAG = "SupabaseVerification"
    }
}
