package com.hades.safepath.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hades.safepath.camera.SafePathCameraNode
import com.hades.safepath.camera.SafePathCameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NearbyCameraNodesViewModel @Inject constructor(
    private val repository: SafePathCameraRepository
) : ViewModel() {

    val nodes: StateFlow<List<SafePathCameraNode>> = combine(
        repository.discoveredNodes,
        repository.classicNodes
    ) { ble, classic ->
        (classic + ble).distinctBy { node ->
            node.bluetoothAddress.ifBlank { node.deviceName }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scanning: StateFlow<Boolean> = combine(
        repository.scanning,
        repository.classicScanning
    ) { ble, classic -> ble || classic }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val scanError: StateFlow<String?> = combine(
        repository.classicScanError,
        repository.scanError
    ) { classic, ble -> classic ?: ble }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun isBluetoothEnabled(): Boolean = repository.isBluetoothEnabled()
    fun hasScanPermission(): Boolean = repository.hasScanPermission()

    fun startScan() = repository.startScan()
    fun stopScan() = repository.stopScan()
    fun clearError() = repository.clearScanError()

    fun selectNode(node: SafePathCameraNode) = repository.selectNode(node)

    override fun onCleared() {
        repository.stopScan()
        super.onCleared()
    }
}
