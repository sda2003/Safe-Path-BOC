package com.hades.safepath.ui.personalize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hades.safepath.ai.GeneratePackOutcome
import com.hades.safepath.ai.HouseholdProfile
import com.hades.safepath.ai.PersonalizedPackRepository
import com.hades.safepath.data.entity.GuidanceStep
import com.hades.safepath.data.entity.PackItem
import com.hades.safepath.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PersonalizeUiState {
    data object Idle : PersonalizeUiState()
    data object Loading : PersonalizeUiState()
    data class FormError(val message: String) : PersonalizeUiState()
    data class Generated(val usedDefaultFallback: Boolean) : PersonalizeUiState()
    data object AlreadyGenerated : PersonalizeUiState()
}

data class PersonalizedPackDisplay(
    val items: List<PackItem> = emptyList(),
    val tips: List<GuidanceStep> = emptyList(),
    val usedDefaultFallback: Boolean = false
)

@HiltViewModel
class PersonalizePackViewModel @Inject constructor(
    private val repository: PersonalizedPackRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow<PersonalizeUiState>(PersonalizeUiState.Idle)
    val uiState: StateFlow<PersonalizeUiState> = _uiState.asStateFlow()

    private val _display = MutableStateFlow(PersonalizedPackDisplay())
    val display: StateFlow<PersonalizedPackDisplay> = _display.asStateFlow()

    fun checkExisting() {
        viewModelScope.launch {
            if (repository.hasPersonalizedPack()) {
                loadDisplay()
                _uiState.value = PersonalizeUiState.AlreadyGenerated
            }
        }
    }

    fun submit(
        householdSize: Int,
        hasKids: Boolean,
        kidsAges: String,
        hasElderly: Boolean,
        hasPets: Boolean,
        petType: String,
        medicalConditions: String
    ) {
        if (!networkMonitor.isOnline()) {
            _uiState.value = PersonalizeUiState.FormError(
                "Internet required to personalize your pack. Your offline checklist is still available."
            )
            return
        }
        if (householdSize < 1) {
            _uiState.value = PersonalizeUiState.FormError("Enter a valid household size.")
            return
        }

        viewModelScope.launch {
            _uiState.value = PersonalizeUiState.Loading
            val profile = HouseholdProfile(
                householdSize = householdSize,
                hasKids = hasKids,
                kidsAges = kidsAges.trim(),
                hasElderly = hasElderly,
                hasPets = hasPets,
                petType = petType.trim(),
                medicalConditions = medicalConditions.trim()
            )
            when (val outcome = repository.generateAndCache(profile)) {
                is GeneratePackOutcome.AlreadyExists -> {
                    loadDisplay()
                    _uiState.value = PersonalizeUiState.AlreadyGenerated
                }
                is GeneratePackOutcome.Success -> {
                    loadDisplay()
                    _uiState.value = PersonalizeUiState.Generated(outcome.usedDefaultFallback)
                }
                is GeneratePackOutcome.Error -> {
                    _uiState.value = PersonalizeUiState.FormError(outcome.message)
                }
            }
        }
    }

    fun loadDisplay() {
        viewModelScope.launch {
            val meta = repository.getMeta()
            _display.value = PersonalizedPackDisplay(
                items = repository.getPackItems(),
                tips = repository.getTips(),
                usedDefaultFallback = meta?.usedDefaultFallback == true
            )
        }
    }
}
