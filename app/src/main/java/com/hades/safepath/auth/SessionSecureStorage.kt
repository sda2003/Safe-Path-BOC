package com.hades.safepath.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.gotrue.user.UserSession

/**
 * Persists Supabase Auth sessions in EncryptedSharedPreferences.
 * Never logs token values.
 */
class SessionSecureStorage(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSession(session: UserSession) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_IN, session.expiresIn ?: 0L)
            .putString(KEY_TOKEN_TYPE, session.tokenType ?: "bearer")
            .putString(KEY_USER_ID, session.user?.id.orEmpty())
            .putString(KEY_PHONE, session.user?.phone.orEmpty())
            .apply()
    }

    fun loadSession(): UserSession? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        val expiresInRaw = prefs.getLong(KEY_EXPIRES_IN, 0L)
        val tokenType = prefs.getString(KEY_TOKEN_TYPE, null) ?: "bearer"
        return UserSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = if (expiresInRaw > 0L) expiresInRaw else 3600L,
            tokenType = tokenType,
            user = null
        )
    }

    fun getCachedUserId(): String? = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }

    fun getCachedPhone(): String? = prefs.getString(KEY_PHONE, null)?.takeIf { it.isNotBlank() }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "safepath_secure_session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_IN = "expires_in"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PHONE = "phone"
    }
}
