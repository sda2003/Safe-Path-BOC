package com.hades.safepath.ui.pack

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.hades.safepath.data.model.PackItem
import com.hades.safepath.data.repository.PackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PackViewModel @Inject constructor(
    private val packRepository: PackRepository
) : ViewModel() {

    val packItems: LiveData<List<PackItem>> = packRepository.getAllItems().asLiveData()

    init {
        viewModelScope.launch {
            packRepository.seedPackItems()
        }
    }

    fun toggleItem(item: PackItem) {
        val updated = item.copy(isChecked = !item.isChecked)
        viewModelScope.launch {
            packRepository.updateItem(updated)
        }
    }

    fun updateItem(item: PackItem) {
        viewModelScope.launch {
            packRepository.updateItem(item)
        }
    }

    fun loadItems(items: List<PackItem>) {
        viewModelScope.launch {
            // Keep checked state for matching ids when switching disaster packs.
            val current = packItems.value.orEmpty().associateBy { it.id }
            val merged = items.map { fresh ->
                fresh.copy(isChecked = current[fresh.id]?.isChecked ?: false)
            }
            packRepository.replaceAll(merged)
        }
    }

    fun getCheckedCount(): Int {
        return packItems.value?.filter { it.section == "critical" }?.count { it.isChecked } ?: 0
    }

    fun getCriticalCount(): Int {
        return packItems.value?.count { it.section == "critical" } ?: 0
    }
}
