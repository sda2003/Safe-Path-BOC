package com.hades.safepath.ui.mesh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hades.safepath.data.entity.MeshNoteEntity
import com.hades.safepath.mesh.MeshNotesRepository
import com.hades.safepath.mesh.MeshPostResult
import com.hades.safepath.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MeshNotesViewModel @Inject constructor(
    private val repository: MeshNotesRepository,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val notes: StateFlow<List<MeshNoteEntity>> = repository.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isOnline: StateFlow<Boolean> = networkMonitor.observeOnline()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), networkMonitor.isOnline())

    val peerCount: StateFlow<Int> = repository.peerCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val meshActive: StateFlow<Boolean> = repository.isMeshActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun startMesh() {
        repository.startMesh()
    }

    fun stopMesh() {
        repository.stopMesh()
    }

    fun postNote(text: String, type: String, senderName: String) {
        viewModelScope.launch {
            when (val result = repository.postNote(text, type, senderName)) {
                is MeshPostResult.SavedOnline ->
                    _events.emit("Note posted online and saved locally.")
                is MeshPostResult.SavedMesh ->
                    _events.emit("Note shared over mesh — will sync when online.")
                is MeshPostResult.Error ->
                    _events.emit(result.message)
            }
        }
    }

    fun flushNow() {
        viewModelScope.launch {
            val n = repository.flushUnsyncedToCloud()
            if (n > 0) {
                _events.emit("Synced $n note(s) to cloud.")
            }
        }
    }

    override fun onCleared() {
        // Keep mesh running while app is alive; fragment stops when leaving if desired.
        super.onCleared()
    }
}
