package com.hades.safepath.ui.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hades.safepath.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertViewModel @Inject constructor(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _reportResult = MutableLiveData<Result<Unit>>()
    val reportResult: LiveData<Result<Unit>> = _reportResult

    fun reportAlert(
        type: String,
        severity: String,
        description: String,
        latitude: Double,
        longitude: Double,
        district: String
    ) {
        viewModelScope.launch {
            val result = alertRepository.reportAlert(type, severity, description, latitude, longitude, district)
            _reportResult.postValue(result)
        }
    }
}
