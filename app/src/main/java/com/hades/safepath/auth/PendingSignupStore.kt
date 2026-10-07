package com.hades.safepath.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds profile fields collected during Sign Up until email OTP confirms the account.
 */
@Singleton
class PendingSignupStore @Inject constructor() {

    data class Pending(
        val name: String,
        val email: String,
        val district: String
    )

    @Volatile
    private var pending: Pending? = null

    fun save(name: String, email: String, district: String) {
        pending = Pending(name = name.trim(), email = email.trim().lowercase(), district = district.trim())
    }

    fun peek(): Pending? = pending

    fun take(): Pending? {
        val value = pending
        pending = null
        return value
    }

    fun clear() {
        pending = null
    }
}
