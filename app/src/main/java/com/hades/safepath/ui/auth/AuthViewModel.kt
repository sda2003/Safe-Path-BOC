package com.hades.safepath.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hades.safepath.auth.AuthDeepLink
import com.hades.safepath.auth.EmailNormalizer
import com.hades.safepath.auth.PendingSignupStore
import com.hades.safepath.auth.SessionSecureStorage
import com.hades.safepath.auth.VerificationDestination
import com.hades.safepath.auth.VerificationService
import com.hades.safepath.data.remote.SupabaseConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.user.UserSession
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Success(val user: UserInfo) : AuthState()
    /** Account created — confirm via email link and/or OTP code. */
    data class AwaitingEmailOtp(val email: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val email: String? = null,
    val district: String,
    val verified: Boolean = false,
    val tier: String = "free"
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val pendingSignupStore: PendingSignupStore,
    private val verificationService: VerificationService,
    private val sessionSecureStorage: SessionSecureStorage
) : ViewModel() {

    private val auth = supabaseClient.auth
    private val db = supabaseClient.postgrest

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun signIn(email: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                ensureSupabaseConfigured()
                val normalized = EmailNormalizer.normalize(email)
                    ?: throw IllegalArgumentException("Enter a valid email address.")

                auth.signInWith(Email) {
                    this.email = normalized
                    this.password = pass
                }
                val user = auth.currentUserOrNull()
                    ?: throw IllegalStateException("Sign in failed. Check your email and password.")

                auth.currentSessionOrNull()?.let { sessionSecureStorage.saveSession(it) }
                completePendingProfileIfNeeded(user)
                _authState.value = AuthState.Success(user)
            } catch (e: Exception) {
                Log.e(TAG, "signIn failed", e)
                _authState.value = AuthState.Error(mapAuthError(e, isSignUp = false))
            }
        }
    }

    fun signUp(name: String, email: String, pass: String, district: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                ensureSupabaseConfigured()
                val normalized = EmailNormalizer.normalize(email)
                    ?: throw IllegalArgumentException("Enter a valid email address.")
                if (name.isBlank()) throw IllegalArgumentException("Name is required.")
                if (pass.length < 8) throw IllegalArgumentException("Password must be at least 8 characters.")

                pendingSignupStore.save(name = name, email = normalized, district = district)

                auth.signUpWith(Email) {
                    this.email = normalized
                    this.password = pass
                }

                val user = auth.currentUserOrNull()
                val session = auth.currentSessionOrNull()

                if (user != null && session != null) {
                    sessionSecureStorage.saveSession(session)
                    upsertProfile(
                        id = user.id,
                        name = name,
                        email = normalized,
                        district = district,
                        verified = true
                    )
                    pendingSignupStore.clear()
                    _authState.value = AuthState.Success(user)
                    return@launch
                }

                // Signup created the user; confirmation email was already sent by Supabase.
                // Do NOT send a second OTP email — that hits free-tier rate limits.
                _authState.value = AuthState.AwaitingEmailOtp(normalized)
            } catch (e: Exception) {
                Log.e(TAG, "signUp failed", e)
                pendingSignupStore.clear()
                _authState.value = AuthState.Error(mapAuthError(e, isSignUp = true))
            }
        }
    }

    fun resendSignupEmailOtp(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val normalized = EmailNormalizer.normalize(email) ?: email.trim().lowercase()
            verificationService.requestOtp(VerificationDestination.Email(normalized)).fold(
                onSuccess = { _authState.value = AuthState.AwaitingEmailOtp(normalized) },
                onFailure = {
                    _authState.value = AuthState.Error(
                        it.message ?: "Could not resend email. Try again in a minute."
                    )
                }
            )
        }
    }

    /**
     * Called when a magic-link deep link establishes a session.
     */
    fun onDeepLinkSession(session: UserSession) {
        viewModelScope.launch {
            try {
                sessionSecureStorage.saveSession(session)
                val user = session.user ?: auth.currentUserOrNull()
                    ?: throw IllegalStateException("No user after email confirmation.")
                completePendingProfileIfNeeded(user)
                _authState.value = AuthState.Success(user)
            } catch (e: Exception) {
                Log.e(TAG, "deep link session handling failed", e)
                _authState.value = AuthState.Error(e.message ?: "Email confirmation failed.")
            }
        }
    }

    fun continueAnonymously() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                _authState.value = AuthState.Success(
                    UserInfo(
                        id = "anon",
                        aud = "authenticated",
                        createdAt = null,
                        email = "anon@safepath.local",
                        phone = "",
                        appMetadata = null,
                        userMetadata = null,
                        updatedAt = null
                    )
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Anonymous login failed")
            }
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }

    private suspend fun completePendingProfileIfNeeded(user: UserInfo) {
        val pending = pendingSignupStore.peek()
        val email = user.email.orEmpty().ifBlank { pending?.email.orEmpty() }
        if (pending != null && pending.email.equals(email, ignoreCase = true)) {
            upsertProfile(
                id = user.id,
                name = pending.name,
                email = pending.email,
                district = pending.district,
                verified = true
            )
            pendingSignupStore.clear()
            return
        }
        // Ensure a users row exists even without pending signup data.
        if (email.isNotBlank()) {
            upsertProfile(
                id = user.id,
                name = pending?.name ?: "SafePath User",
                email = email,
                district = pending?.district.orEmpty(),
                verified = true
            )
        }
    }

    private suspend fun upsertProfile(
        id: String,
        name: String,
        email: String,
        district: String,
        verified: Boolean
    ) {
        runCatching {
            db["users"].upsert(
                UserProfile(
                    id = id,
                    name = name,
                    email = email,
                    district = district,
                    verified = verified,
                    tier = "free"
                )
            )
        }.onFailure { Log.e(TAG, "profile upsert failed", it) }
    }

    private fun ensureSupabaseConfigured() {
        if (!SupabaseConfig.isConfigured()) {
            throw IllegalStateException(
                "Supabase is not configured. Replace example.supabase.co / placeholder in local.properties with your real project URL and anon key, then rebuild."
            )
        }
    }

    private fun mapAuthError(e: Exception, isSignUp: Boolean): String {
        val msg = e.message.orEmpty()
        val lower = msg.lowercase()
        return when {
            lower.contains("supabase is not configured") -> msg
            lower.contains("over_email_send_rate_limit") ||
                lower.contains("email rate limit") ||
                lower.contains("rate limit") ->
                "Email rate limit reached (Supabase free tier). Wait about 1 hour, or turn OFF “Confirm email” in Supabase → Authentication → Providers → Email, then try again."
            lower.contains("user already registered") ||
                lower.contains("already been registered") ||
                lower.contains("email_exists") ||
                lower.contains("already registered") ->
                "An account with this email already exists. Switch to Sign In."
            lower.contains("invalid login credentials") ||
                lower.contains("invalid_credentials") ->
                "Wrong email or password."
            lower.contains("email not confirmed") ||
                lower.contains("email_not_confirmed") ->
                "Confirm your email first (open the link we sent), then sign in."
            lower.contains("password") && lower.contains("weak") ->
                "Password is too weak. Use at least 8 characters."
            lower.contains("network") ||
                lower.contains("unable to resolve host") ||
                lower.contains("failed to connect") ->
                "No internet connection. Check your network and try again."
            msg.contains("Enter a valid") || msg.contains("required") || msg.contains("at least") ->
                msg
            msg.isNotBlank() ->
                msg.take(180)
            else ->
                if (isSignUp) "Could not create account. Please try again."
                else "Sign in failed. Please try again."
        }
    }

    companion object {
        private const val TAG = "AuthViewModel"
        val redirectUrl: String = AuthDeepLink.REDIRECT_URL
    }
}
