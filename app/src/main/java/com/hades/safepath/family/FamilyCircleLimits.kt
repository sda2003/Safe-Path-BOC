package com.hades.safepath.family

object FamilyCircleLimits {
    const val FREE_MAX_CONTACTS = 1
    const val PRO_MAX_CONTACTS = 5

    fun maxContactsForTier(tier: String): Int = when (tier.lowercase()) {
        "pro", "premium" -> PRO_MAX_CONTACTS
        else -> FREE_MAX_CONTACTS
    }
}
