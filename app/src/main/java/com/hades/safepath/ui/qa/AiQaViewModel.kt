package com.hades.safepath.ui.qa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hades.safepath.ai.AiQaRepository
import com.hades.safepath.ai.AiQaResult
import com.hades.safepath.data.entity.AiQaCacheEntity
import com.hades.safepath.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiQaViewModel @Inject constructor(
    private val repository: AiQaRepository,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val history: StateFlow<List<AiQaCacheEntity>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isOnline: StateFlow<Boolean> = networkMonitor.observeOnline()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), networkMonitor.isOnline())

    val suggestions: List<String> = repository.suggestedQuestions

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val _latestAnswer = MutableStateFlow<AiQaResult.Success?>(null)
    val latestAnswer = _latestAnswer.asStateFlow()

    fun ask(question: String) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            when (val result = repository.ask(question)) {
                is AiQaResult.Success -> {
                    _latestAnswer.value = result
                    if (result.fromCache) {
                        _events.emit("Loaded from offline cache")
                    }
                }
                is AiQaResult.Error -> _events.emit(result.message)
            }
            _loading.value = false
        }
    }
}
