package com.hades.safepath.map.mode

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves [MapMode] preference + live network into [EffectiveMapMode].
 * Offline preference is sticky — never silently overridden by network regain.
 */
@Singleton
class MapModeController @Inject constructor(
    @ApplicationContext context: Context,
    private val networkStateManager: NetworkStateManager
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _preference = MutableStateFlow(loadPreference())
    val preference: StateFlow<MapMode> = _preference.asStateFlow()

    private val _effective = MutableStateFlow(resolve(_preference.value, networkStateManager.isOnline()))
    val effective: StateFlow<EffectiveMapMode> = _effective.asStateFlow()

    private val _hint = MutableStateFlow<String?>(null)
    val hint: StateFlow<String?> = _hint.asStateFlow()

    init {
        // One-time migration: prior builds defaulted to AUTO and crashed on API 34
        // when OsmDroid registered receivers without RECEIVER_NOT_EXPORTED.
        if (!prefs.getBoolean(KEY_SAFE_RECEIVER_FIX, false)) {
            prefs.edit()
                .putString(KEY_MODE, MapMode.OFFLINE.name)
                .putBoolean(KEY_SAFE_RECEIVER_FIX, true)
                .apply()
            _preference.value = MapMode.OFFLINE
            _effective.value = EffectiveMapMode.OFFLINE
        }
        scope.launch {
            combine(_preference, networkStateManager.availability) { pref, net ->
                pref to (net == NetworkAvailability.ONLINE)
            }.collect { (pref, online) ->
                val next = resolve(pref, online)
                if (_effective.value != next) {
                    Log.i(TAG, "[MAP] Mode = $next (pref=$pref online=$online)")
                }
                _effective.value = next
                _hint.value = when {
                    pref == MapMode.ONLINE && !online ->
                        "Online maps unavailable. Switch to Offline Mode."
                    else -> null
                }
            }
        }
    }

    fun setPreference(mode: MapMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _preference.value = mode
        Log.i(TAG, "[MAP] Preference = $mode")
    }

    fun cyclePreference(): MapMode {
        val next = when (_preference.value) {
            MapMode.AUTO -> MapMode.ONLINE
            MapMode.ONLINE -> MapMode.OFFLINE
            MapMode.OFFLINE -> MapMode.AUTO
        }
        setPreference(next)
        return next
    }

    private fun resolve(pref: MapMode, online: Boolean): EffectiveMapMode = when (pref) {
        MapMode.OFFLINE -> EffectiveMapMode.OFFLINE
        MapMode.ONLINE -> if (online) EffectiveMapMode.ONLINE else EffectiveMapMode.OFFLINE
        MapMode.AUTO -> if (online) EffectiveMapMode.ONLINE else EffectiveMapMode.OFFLINE
    }

    private fun loadPreference(): MapMode {
        // Default OFFLINE for crash-safe demos on API 34+; user can switch to AUTO/ONLINE.
        val raw = prefs.getString(KEY_MODE, MapMode.OFFLINE.name) ?: MapMode.OFFLINE.name
        return runCatching { MapMode.valueOf(raw) }.getOrDefault(MapMode.OFFLINE)
    }

    companion object {
        private const val TAG = "MapModeController"
        private const val PREFS = "safepath_map_mode"
        private const val KEY_MODE = "mode"
        private const val KEY_SAFE_RECEIVER_FIX = "safe_receiver_fix_v1"
    }
}
