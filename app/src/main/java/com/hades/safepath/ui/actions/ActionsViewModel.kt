package com.hades.safepath.ui.actions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class ActionItem(val id: Int, val text: String, var isCompleted: Boolean = false)

@HiltViewModel
class ActionsViewModel @Inject constructor() : ViewModel() {

    private val _actions = MutableLiveData<List<ActionItem>>()
    val actions: LiveData<List<ActionItem>> = _actions

    init {
        _actions.value = listOf(
            ActionItem(1, "Turn off main power switch"),
            ActionItem(2, "Move essential items to upper floor"),
            ActionItem(3, "Grab your Emergency Pack"),
            ActionItem(4, "Lock all doors and windows")
        )
    }

    fun updateAction(item: ActionItem) {
        val currentList = _actions.value?.toMutableList() ?: return
        val index = currentList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            currentList[index] = item
            _actions.value = currentList
        }
    }

    fun markSafe() {
        // Dummy implementation for marking safe
    }
}
