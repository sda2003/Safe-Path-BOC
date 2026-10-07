package com.hades.safepath.auth

enum class VerificationChannel {
    PHONE,
    EMAIL;

    companion object {
        fun fromName(value: String?): VerificationChannel =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: PHONE
    }
}

sealed class VerificationDestination {
    abstract val displayValue: String
    abstract val channel: VerificationChannel

    data class Phone(val e164: String) : VerificationDestination() {
        override val displayValue: String = e164
        override val channel: VerificationChannel = VerificationChannel.PHONE
    }

    data class Email(val address: String) : VerificationDestination() {
        override val displayValue: String = address
        override val channel: VerificationChannel = VerificationChannel.EMAIL
    }
}
