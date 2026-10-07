package com.hades.safepath.ui.survival

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.hades.safepath.data.model.SurvivalTopic
import com.hades.safepath.data.repository.SurvivalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import androidx.lifecycle.MutableLiveData

@HiltViewModel
class SurvivalViewModel @Inject constructor(
    private val survivalRepository: SurvivalRepository
) : ViewModel() {

    val topics: LiveData<List<SurvivalTopic>> = MutableLiveData(survivalRepository.getTopics())

}
