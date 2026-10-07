package com.hades.safepath.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hades.safepath.auth.ServerUserProfile
import com.hades.safepath.auth.VerificationRepository
import com.hades.safepath.data.entity.FamilyContactEntity
import com.hades.safepath.family.FamilyCircleLimits
import com.hades.safepath.family.FamilyCircleRepository
import com.hades.safepath.family.ImSafeResult
import com.hades.safepath.family.SafeBroadcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamilyCircleUiState(
    val loading: Boolean = false,
    val verified: Boolean = false,
    val tier: String = "free",
    val maxContacts: Int = FamilyCircleLimits.FREE_MAX_CONTACTS,
    val contacts: List<FamilyContactEntity> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class FamilyCircleViewModel @Inject constructor(
    private val familyCircleRepository: FamilyCircleRepository,
    private val safeBroadcastRepository: SafeBroadcastRepository,
    private val verificationRepository: VerificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FamilyCircleUiState())
    val uiState: StateFlow<FamilyCircleUiState> = _uiState.asStateFlow()

    private var profile: ServerUserProfile? = null

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val serverProfile = verificationRepository.getServerUserProfile()
            profile = serverProfile
            if (serverProfile == null || !serverProfile.verified) {
                _uiState.value = FamilyCircleUiState(
                    loading = false,
                    verified = false,
                    error = "Verify your phone to use Family Circle."
                )
                return@launch
            }

            val refresh = familyCircleRepository.refreshContactsFromServer()
            val contacts = refresh.getOrElse {
                emptyList()
            }

            _uiState.value = FamilyCircleUiState(
                loading = false,
                verified = true,
                tier = serverProfile.tier,
                maxContacts = FamilyCircleLimits.maxContactsForTier(serverProfile.tier),
                contacts = contacts,
                message = if (refresh.isFailure) "Showing cached contacts. Connect to sync." else null
            )

            familyCircleRepository.observeContacts(serverProfile.id).collect { cached ->
                _uiState.value = _uiState.value.copy(contacts = cached)
            }
        }
    }

    fun addContact(name: String, phone: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null, message = null)
            familyCircleRepository.addContact(name, phone).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        message = "Contact added."
                    )
                    load()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = it.message ?: "Could not add contact."
                    )
                }
            )
        }
    }

    fun removeContact(contactId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            familyCircleRepository.removeContact(contactId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        message = "Contact removed."
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = it.message
                    )
                }
            )
        }
    }

    fun sendImSafe(onResult: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = safeBroadcastRepository.sendImSafe()) {
                is ImSafeResult.Sent -> onResult("I'm Safe sent to your Family Circle.")
                is ImSafeResult.Queued -> onResult("I'm Safe saved — will send when you're back online.")
                is ImSafeResult.Error -> onResult(result.message)
            }
        }
    }
}
