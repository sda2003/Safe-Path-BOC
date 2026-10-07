package com.hades.safepath.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hades.safepath.camera.CameraSessionUi
import com.hades.safepath.camera.SafePathCameraNode
import com.hades.safepath.camera.SafePathCameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SafePathCameraViewModel @Inject constructor(
    private val repository: SafePathCameraRepository
) : ViewModel() {

    val session: StateFlow<CameraSessionUi> = repository.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CameraSessionUi())

    fun ensureNode(node: SafePathCameraNode) {
        if (repository.session.value.node?.nodeId != node.nodeId) {
            repository.selectNode(node)
        }
    }

    fun connect() {
        viewModelScope.launch { repository.connectSelected() }
    }

    fun refreshStatus() {
        viewModelScope.launch { repository.refreshStatus() }
    }

    fun capture() {
        viewModelScope.launch { repository.captureImage() }
    }

    fun captureBlocked(message: String) = repository.noteCaptureBlocked(message)

    fun openWifiSettings() = repository.openWifiSettings()

    fun openLocationSettings() = repository.openLocationSettings()

    fun useJoinedWifi() {
        viewModelScope.launch { repository.useJoinedWifi() }
    }

    fun disconnect() = repository.disconnect()
}
