package com.hades.safepath.auth

/**
 * Represents a verified SafePath user (phone or email).
 * Verification is authoritative when backed by a live Supabase Auth session.
 */
data class VerifiedUser(
    val id: String,
    val phone: String = "",
    val email: String = "",
    val verifiedAt: Long,
    val tier: String = "free",
    val channel: VerificationChannel = VerificationChannel.PHONE
)
