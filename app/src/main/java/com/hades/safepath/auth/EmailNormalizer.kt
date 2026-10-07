package com.hades.safepath.auth

object EmailNormalizer {

    private val emailRegex = Regex(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    fun normalize(raw: String): String? {
        val candidate = raw.trim().lowercase()
        return candidate.takeIf { emailRegex.matches(it) }
    }
}
