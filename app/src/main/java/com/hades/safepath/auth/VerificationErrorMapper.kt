package com.hades.safepath.auth

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object VerificationErrorMapper {

    fun mapRequestOtpError(throwable: Throwable): String {
        val message = throwable.message.orEmpty()
        val errorCode = throwable.extractSupabaseErrorCode()

        return when {
            throwable is InvalidPhoneException ->
                "Enter a valid phone number in international format (e.g. +94771234567)."
            throwable is InvalidEmailException ->
                "Enter a valid email address."
            errorCode == "phone_provider_disabled" || message.contains("phone_provider_disabled", true) ->
                "SMS is not available. Try email verification instead."
            message.contains("Unsupported phone provider", ignoreCase = true) ->
                "SMS is not configured. Try email verification instead."
            message.contains("sms_send_failed", ignoreCase = true) ||
                message.contains("Error sending", ignoreCase = true) ->
                "SMS could not be sent. Try email verification instead."
            throwable.isNetworkError() ->
                "No internet connection. Check your network and try again."
            message.contains("rate", ignoreCase = true) ||
                errorCode == "over_sms_send_rate_limit" ->
                "Too many attempts. Please wait a moment and try again."
            message.contains("invalid phone", ignoreCase = true) ||
                errorCode == "validation_failed" ->
                "Enter a valid phone number in international format (e.g. +94771234567)."
            errorCode == "email_address_invalid" ||
                message.contains("email_address_invalid", true) ||
                message.contains("Email address", true) && message.contains("invalid", true) ->
                "That email address is not accepted. Use a real inbox (not example.com)."
            errorCode == "email_provider_disabled" ||
                message.contains("Email logins are disabled", true) ->
                "Email login is disabled in Supabase. Enable Email under Authentication → Providers."
            else ->
                "Could not send verification code. Please try again."
        }
    }

    fun suggestsEmailFallback(throwable: Throwable): Boolean {
        val message = throwable.message.orEmpty()
        val errorCode = throwable.extractSupabaseErrorCode()
        return errorCode == "phone_provider_disabled" ||
            message.contains("phone_provider_disabled", true) ||
            message.contains("Unsupported phone provider", true) ||
            message.contains("sms_send_failed", true) ||
            message.contains("Error sending", true)
    }

    fun mapVerifyOtpError(throwable: Throwable, channel: VerificationChannel = VerificationChannel.PHONE): String = when {
        throwable is InvalidOtpException ->
            if (channel == VerificationChannel.EMAIL) {
                "Enter the 6-digit code from your email."
            } else {
                "Enter the 6-digit code from your SMS."
            }
        throwable.isExpiredOtp() ->
            "This code has expired. Request a new one."
        throwable.isWrongOtp() ->
            "Incorrect code. Please try again."
        throwable.isNetworkError() ->
            "No internet connection. Check your network and try again."
        else ->
            "Verification failed. Please try again."
    }

    private fun Throwable.isNetworkError(): Boolean =
        this is IOException ||
            this is UnknownHostException ||
            this is SocketTimeoutException ||
            message?.contains("network", ignoreCase = true) == true ||
            message?.contains("unable to resolve host", ignoreCase = true) == true

    private fun Throwable.isExpiredOtp(): Boolean {
        val msg = message?.lowercase().orEmpty()
        return msg.contains("expired") || msg.contains("otp_expired")
    }

    private fun Throwable.isWrongOtp(): Boolean {
        val msg = message?.lowercase().orEmpty()
        return msg.contains("invalid") ||
            msg.contains("token") ||
            msg.contains("otp") ||
            msg.contains("incorrect")
    }

    private fun Throwable.extractSupabaseErrorCode(): String? {
        val msg = message.orEmpty()
        Regex("""error_code["']?\s*[:=]\s*["']?([a-z0-9_]+)""", RegexOption.IGNORE_CASE)
            .find(msg)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        return Regex("""\b([a-z_]+_disabled|[a-z_]+_failed|over_[a-z_]+)\b""", RegexOption.IGNORE_CASE)
            .find(msg)
            ?.groupValues
            ?.getOrNull(1)
    }
}

class InvalidPhoneException : Exception("invalid_phone")
class InvalidEmailException : Exception("invalid_email")
class InvalidOtpException : Exception("invalid_otp")
