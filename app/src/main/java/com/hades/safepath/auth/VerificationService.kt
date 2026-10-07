package com.hades.safepath.auth

interface VerificationService {
    suspend fun requestOtp(destination: VerificationDestination): Result<Unit>
    suspend fun verifyOtp(destination: VerificationDestination, code: String): Result<VerifiedUser>
}
