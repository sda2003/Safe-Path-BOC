package com.hades.safepath.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.hades.safepath.data.model.ChatMessage
import com.hades.safepath.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    val currentUserId: String?
        get() = supabaseClient.auth.currentSessionOrNull()?.user?.id
            ?: supabaseClient.auth.currentUserOrNull()?.id

    fun observeMessages(roomId: String): LiveData<List<ChatMessage>> {
        return chatRepository.observeMessages(roomId).asLiveData()
    }

    fun sendMessage(
        roomId: String,
        message: String,
        senderName: String,
        isAnonymous: Boolean,
        imageUrl: String? = null
    ): LiveData<Result<Unit>> {
        val result = MutableLiveData<Result<Unit>>()
        viewModelScope.launch {
            result.value = chatRepository.sendMessage(roomId, message, senderName, isAnonymous, imageUrl)
        }
        return result
    }

    fun sendImageMessage(
        roomId: String,
        imageBytes: ByteArray,
        senderName: String,
        isAnonymous: Boolean
    ): LiveData<Result<Unit>> {
        val result = MutableLiveData<Result<Unit>>()
        viewModelScope.launch {
            val uploadResult = chatRepository.uploadImage(imageBytes)
            uploadResult.onSuccess { url ->
                result.value = chatRepository.sendMessage(roomId, "", senderName, isAnonymous, url)
            }.onFailure { e ->
                result.value = Result.failure(e)
            }
        }
        return result
    }
}
