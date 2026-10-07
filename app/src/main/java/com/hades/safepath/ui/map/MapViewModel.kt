package com.hades.safepath.ui.map

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hades.safepath.data.model.Alert
import com.hades.safepath.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.hades.safepath.data.repository.ShelterRepository
import com.hades.safepath.data.entity.Shelter
import com.hades.safepath.data.model.DisasterType
import com.hades.safepath.data.model.RouteNode
import com.hades.safepath.routing.RoutingEngine

import com.hades.safepath.data.repository.HazardRepository

@HiltViewModel
class MapViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val shelterRepository: ShelterRepository,
    private val hazardRepository: HazardRepository,
    private val routingEngine: RoutingEngine
) : ViewModel() {

    private val _activeAlerts = MutableLiveData<List<Alert>>(emptyList())
    val activeAlerts: LiveData<List<Alert>> = _activeAlerts

    private val _shelters = MutableLiveData<List<Shelter>>(emptyList())
    val shelters: LiveData<List<Shelter>> = _shelters

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _route = MutableLiveData<List<RouteNode>>(emptyList())
    val route: LiveData<List<RouteNode>> = _route

    private var lastCalculatedLat: Double? = null
    private var lastCalculatedLon: Double? = null

    init {
        viewModelScope.launch {
            try {
                routingEngine.initialise()
            } catch (e: Exception) {
                Log.e("MapViewModel", "Failed to init routing engine", e)
            }
        }

        // Trigger route recalculation on hazard updates
        viewModelScope.launch {
            hazardRepository.observeActiveHazards(System.currentTimeMillis()).collect { hazards ->
                Log.d("MapViewModel", "Hazards updated: ${hazards.size} active hazards. Recalculating route...")
                val lat = lastCalculatedLat
                val lon = lastCalculatedLon
                if (lat != null && lon != null) {
                    calculateRouteToNearestShelter(lat, lon)
                }
            }
        }
    }

    fun loadActiveAlerts() {
        viewModelScope.launch {
            _loading.value = true
            try {
                // Shelters are local Room data — load even if cloud alerts fail
                val dbShelters = shelterRepository.getAllShelters()
                Log.d("MapViewModel", "Loaded ${dbShelters.size} shelters")
                _shelters.value = dbShelters

                val alerts = alertRepository.getActiveAlerts()
                Log.d("MapViewModel", "Loaded ${alerts.size} alerts")
                alerts.forEach { a ->
                    Log.d("MapViewModel", "Alert ${a.id} at ${a.latitude}, ${a.longitude}")
                }
                _activeAlerts.value = alerts
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error loading data: ${e.message}", e)
                _activeAlerts.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    fun calculateRouteToNearestShelter(lat: Double, lon: Double) {
        lastCalculatedLat = lat
        lastCalculatedLon = lon
        viewModelScope.launch {
            try {
                val path = routingEngine.calculateEvacuationRoute(lat, lon, DisasterType.FLOOD)
                _route.value = path
            } catch (e: Exception) {
                Log.e("MapViewModel", "Failed to calculate route", e)
                _route.value = emptyList()
            }
        }
    }
}
