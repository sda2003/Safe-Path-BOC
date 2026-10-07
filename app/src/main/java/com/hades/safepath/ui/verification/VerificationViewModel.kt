package com.hades.safepath.ui.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hades.safepath.auth.VerificationChannel
import com.hades.safepath.auth.VerificationDestination
import com.hades.safepath.auth.VerificationErrorMapper
import com.hades.safepath.auth.VerificationRepository
import com.hades.safepath.auth.VerifiedUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class VerificationUiState {
    data object Idle : VerificationUiState()
    data object Loading : VerificationUiState()
    data class OtpSent(
        val destination: String,
        val channel: VerificationChannel
    ) : VerificationUiState()
    data class Verified(val user: VerifiedUser) : VerificationUiState()
    data class Error(
        val message: String,
        val suggestEmailFallback: Boolean = false
    ) : VerificationUiState()
}

@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val verificationRepository: VerificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<VerificationUiState>(VerificationUiState.Idle)
    val uiState: StateFlow<VerificationUiState> = _uiState.asStateFlow()

    private var pendingDestination: VerificationDestination? = null

    fun requestOtp(destination: VerificationDestination) {
        viewModelScope.launch {
            _uiState.value = VerificationUiState.Loading
            verificationRepository.requestOtp(destination).fold(
                onSuccess = {
                    pendingDestination = destination
                    _uiState.value = VerificationUiState.OtpSent(
                        destination = destination.displayValue,
                        channel = destination.channel
                    )
                },
                onFailure = { error ->
                    val suggestEmail = destination.channel == VerificationChannel.PHONE &&
                        VerificationErrorMapper.suggestsEmailFallback(error)
                    _uiState.value = VerificationUiState.Error(
                        message = VerificationErrorMapper.mapRequestOtpError(error),
                        suggestEmailFallback = suggestEmail
                    )
                }
            )
        }
    }

    fun verifyOtp(
        code: String,
        destinationValue: String? = null,
        channel: VerificationChannel? = null
    ) {
        val destination = resolveDestination(destinationValue, channel) ?: return
        pendingDestination = destination
        viewModelScope.launch {
            _uiState.value = VerificationUiState.Loading
            verificationRepository.verifyOtp(destination, code).fold(
                onSuccess = { user ->
                    _uiState.value = VerificationUiState.Verified(user)
                },
                onFailure = { error ->
                    _uiState.value = VerificationUiState.Error(
                        message = VerificationErrorMapper.mapVerifyOtpError(error, destination.channel)
                    )
                }
            )
        }
    }

    fun resendOtp(destinationValue: String? = null, channel: VerificationChannel? = null) {
        resolveDestination(destinationValue, channel)?.let { requestOtp(it) }
    }

    fun clearError() {
        if (_uiState.value is VerificationUiState.Error) {
            pendingDestination?.let { pending ->
                _uiState.value = VerificationUiState.OtpSent(
                    destination = pending.displayValue,
                    channel = pending.channel
                )
            } ?: run { _uiState.value = VerificationUiState.Idle }
        }
    }

    suspend fun isVerifiedOnServer(): Boolean = verificationRepository.isVerifiedOnServer()

    private fun resolveDestination(
        destinationValue: String?,
        channel: VerificationChannel?
    ): VerificationDestination? {
        destinationValue?.takeIf { it.isNotBlank() }?.let { value ->
            val resolvedChannel = channel ?: pendingDestination?.channel ?: VerificationChannel.PHONE
            return when (resolvedChannel) {
                VerificationChannel.PHONE -> VerificationDestination.Phone(value)
                VerificationChannel.EMAIL -> VerificationDestination.Email(value)
            }
        }
        return pendingDestination
    }
}
