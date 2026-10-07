package com.hades.safepath.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.hades.safepath.auth.VerificationRepository
import com.hades.safepath.data.entity.Shelter
import com.hades.safepath.data.model.Alert
import com.hades.safepath.data.repository.AlertRepository
import com.hades.safepath.data.repository.ShelterRepository
import com.hades.safepath.family.ImSafeResult
import com.hades.safepath.family.SafeBroadcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val verificationRepository: VerificationRepository,
    private val safeBroadcastRepository: SafeBroadcastRepository,
    private val shelterRepository: ShelterRepository
) : ViewModel() {

    val currentAlert: LiveData<Alert?> = alertRepository.getCurrentAlert().asLiveData()
    val lastAlert: LiveData<Alert?> = alertRepository.getLastAlert().asLiveData()

    private val _shelters = MutableLiveData<List<Shelter>>(emptyList())
    val shelters: LiveData<List<Shelter>> = _shelters

    val isFamilyCircleVerified = verificationRepository.observeCachedProfile()
        .map { it?.verified == true }
        .asLiveData()

    init {
        viewModelScope.launch {
            _shelters.value = shelterRepository.getAllShelters()
        }
    }

    suspend fun isVerifiedOnServer(): Boolean = verificationRepository.isVerifiedOnServer()

    fun signOut() {
        viewModelScope.launch {
            verificationRepository.signOut()
        }
    }

    fun sendImSafe(onResult: (ImSafeResult) -> Unit) {
        viewModelScope.launch {
            onResult(safeBroadcastRepository.sendImSafe())
        }
    }
}
