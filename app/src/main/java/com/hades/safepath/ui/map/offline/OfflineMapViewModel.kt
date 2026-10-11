package com.hades.safepath.ui.map.offline

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.hades.safepath.data.SafePathDatabase
import com.hades.safepath.data.entity.Shelter
import com.hades.safepath.data.model.Alert
import com.hades.safepath.data.repository.AlertRepository
import com.hades.safepath.data.repository.ShelterRepository
import com.hades.safepath.data.seeding.DatabaseSeeder
import com.hades.safepath.map.mode.EffectiveMapMode
import com.hades.safepath.map.mode.MapMode
import com.hades.safepath.map.mode.MapModeController
import com.hades.safepath.map.mode.NetworkStateManager
import com.hades.safepath.map.offline.OfflineMapLayerRepository
import com.hades.safepath.map.offline.OfflineTileRepository
import com.hades.safepath.map.routing.EvacuationRouteResult
import com.hades.safepath.map.routing.EvacuationRoutingCoordinator
import com.hades.safepath.routing.GeoUtils
import com.hades.safepath.routing.RoutingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShelterMapItem(
    val shelter: Shelter,
    val distanceMeters: Double?
)

@HiltViewModel
class OfflineMapViewModel @Inject constructor(
    private val tileRepository: OfflineTileRepository,
    private val layerRepository: OfflineMapLayerRepository,
    private val shelterRepository: ShelterRepository,
    private val alertRepository: AlertRepository,
    private val routingEngine: RoutingEngine,
    private val routingCoordinator: EvacuationRoutingCoordinator,
    private val mapModeController: MapModeController,
    private val networkStateManager: NetworkStateManager,
    private val database: SafePathDatabase
) : ViewModel() {

    private val _tileSetup = MutableLiveData<OfflineTileRepository.TileSetup?>()
    val tileSetup: LiveData<OfflineTileRepository.TileSetup?> = _tileSetup

    private val _overlays = MutableLiveData<OfflineMapLayerRepository.OverlayPack?>()
    val overlays: LiveData<OfflineMapLayerRepository.OverlayPack?> = _overlays

    private val _shelters = MutableLiveData<List<ShelterMapItem>>(emptyList())
    val shelters: LiveData<List<ShelterMapItem>> = _shelters

    private val _alerts = MutableLiveData<List<Alert>>(emptyList())
    val alerts: LiveData<List<Alert>> = _alerts

    private val _route = MutableLiveData<EvacuationRouteResult?>()
    val route: LiveData<EvacuationRouteResult?> = _route

    private val _selectedShelter = MutableLiveData<ShelterMapItem?>()
    val selectedShelter: LiveData<ShelterMapItem?> = _selectedShelter

    private val _status = MutableLiveData("Preparing map…")
    val status: LiveData<String> = _status

    private val _ready = MutableLiveData(false)
    val ready: LiveData<Boolean> = _ready

    private val _focusPilotArea = MutableLiveData(false)
    val focusPilotArea: LiveData<Boolean> = _focusPilotArea

    val effectiveMode = mapModeController.effective
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EffectiveMapMode.OFFLINE)
        .asLiveData()

    val mapModePreference = mapModeController.preference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapMode.AUTO)
        .asLiveData()

    val modeHint = mapModeController.hint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        .asLiveData()

    private var displayLat: Double = RoutingEngine.DEMO_ROUTE_LAT
    private var displayLon: Double = RoutingEngine.DEMO_ROUTE_LON

    fun initializeOfflineMap() {
        viewModelScope.launch {
            try {
                networkStateManager.refresh()
                _status.value = "Seeding offline shelters…"
                DatabaseSeeder.ensureSeeded(database)

                _status.value = "Configuring tile cache…"
                val tiles = tileRepository.prepare()
                _tileSetup.value = tiles

                _status.value = "Loading hazard & road layers…"
                val layers = layerRepository.loadOverlays()
                _overlays.value = layers

                _status.value = "Initialising offline routing…"
                routingEngine.initialise()

                refreshShelters()
                refreshAlerts()
                _focusPilotArea.value = true
                val shelterCount = shelterRepository.getAllShelters().size
                val mode = mapModeController.effective.value
                _status.value = when (mode) {
                    EffectiveMapMode.ONLINE -> "● ONLINE · $shelterCount shelters"
                    EffectiveMapMode.OFFLINE ->
                        "● OFFLINE · ${tiles.message} · $shelterCount shelters"
                }
                _ready.value = shelterCount > 0
            } catch (e: Exception) {
                Log.e(TAG, "Map init failed", e)
                _status.value = "Map init error: ${e.message}"
                _ready.value = false
            }
        }
    }

    fun refreshAlerts() {
        viewModelScope.launch {
            _alerts.value = runCatching { alertRepository.getActiveAlerts() }.getOrElse { error ->
                Log.e(TAG, "Alert refresh failed", error)
                emptyList()
            }
        }
    }

    fun onUserLocation(lat: Double, lon: Double) {
        displayLat = lat
        displayLon = lon
        refreshShelters()
    }

    fun cycleMapMode() {
        val next = mapModeController.cyclePreference()
        _status.value = "Map mode: ${next.name}"
    }

    fun setMapMode(mode: MapMode) {
        mapModeController.setPreference(mode)
    }

    private fun routingOrigin(): Pair<Double, Double> {
        return if (isInSriLanka(displayLat, displayLon)) {
            displayLat to displayLon
        } else {
            RoutingEngine.DEMO_ROUTE_LAT to RoutingEngine.DEMO_ROUTE_LON
        }
    }

    private fun isInSriLanka(lat: Double, lon: Double): Boolean =
        lat in 5.8..10.0 && lon in 79.4..82.0

    fun selectShelter(item: ShelterMapItem) {
        _selectedShelter.value = item
    }

    fun clearShelterSelection() {
        _selectedShelter.value = null
    }

    fun calculateEvacuationRoute() {
        viewModelScope.launch {
            try {
                DatabaseSeeder.ensureSeeded(database)
                val (lat, lon) = routingOrigin()
                _status.value = "Calculating evacuation route…"
                val (shelter, result) = routingCoordinator.routeToNearest(lat, lon)
                _route.value = result
                _selectedShelter.value = ShelterMapItem(
                    shelter = shelter,
                    distanceMeters = GeoUtils.distanceMeters(lat, lon, shelter.lat, shelter.lon)
                )
                _status.value = formatRouteStatus(shelter.name, result)
            } catch (e: Exception) {
                Log.e(TAG, "Route failed", e)
                _route.value = null
                _status.value = "Routing failed: ${e.message}"
            }
        }
    }

    fun routeToShelter(shelter: Shelter) {
        viewModelScope.launch {
            try {
                DatabaseSeeder.ensureSeeded(database)
                val (lat, lon) = routingOrigin()
                _status.value = "Routing to ${shelter.name}…"
                val result = routingCoordinator.routeToShelter(lat, lon, shelter)
                _route.value = result
                _selectedShelter.value = ShelterMapItem(
                    shelter = shelter,
                    distanceMeters = GeoUtils.distanceMeters(lat, lon, shelter.lat, shelter.lon)
                )
                _status.value = formatRouteStatus(shelter.name, result)
            } catch (e: Exception) {
                Log.e(TAG, "Route to shelter failed", e)
                _status.value = "Routing failed: ${e.message}"
            }
        }
    }

    private fun formatRouteStatus(name: String, result: EvacuationRouteResult): String {
        val km = result.distanceMeters / 1000.0
        val eta = result.durationSeconds?.let { sec ->
            val min = (sec / 60.0).toInt().coerceAtLeast(1)
            " · ~$min min"
        }.orEmpty()
        val flag = if (result.isDirectFallback) " · DIRECT (not roads)" else ""
        return "→ $name · ${"%.1f".format(km)} km$eta · ${result.providerLabel}$flag"
    }

    private fun refreshShelters() {
        viewModelScope.launch {
            var list = shelterRepository.getAllActiveShelters().ifEmpty {
                shelterRepository.getAllShelters()
            }
            if (list.isEmpty()) {
                DatabaseSeeder.ensureSeeded(database)
                list = shelterRepository.getAllActiveShelters().ifEmpty {
                    shelterRepository.getAllShelters()
                }
            }
            val lat = displayLat
            val lon = displayLon
            _shelters.value = list.map { s ->
                ShelterMapItem(
                    shelter = s,
                    distanceMeters = GeoUtils.distanceMeters(lat, lon, s.lat, s.lon)
                )
            }.sortedBy { it.distanceMeters }
        }
    }

    companion object {
        private const val TAG = "OfflineMapViewModel"
        const val PILOT_LAT = 6.6831
        const val PILOT_LON = 80.3988
    }
}
