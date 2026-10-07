package com.hades.safepath.map.mode

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes ConnectivityManager capabilities (validated internet, not just Wi‑Fi on).
 */
@Singleton
class NetworkStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _availability = MutableStateFlow(NetworkAvailability.OFFLINE)
    val availability: StateFlow<NetworkAvailability> = _availability.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) = refresh()
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
        }.onFailure {
            Log.w(TAG, "Network callback registration failed", it)
        }
        refresh()
    }

    fun refresh() {
        val next = probe()
        if (_availability.value != next) {
            Log.i(TAG, "[MAP] Network available = ${next == NetworkAvailability.ONLINE}")
        }
        _availability.value = next
    }

    fun isOnline(): Boolean = probe() == NetworkAvailability.ONLINE

    private fun probe(): NetworkAvailability {
        return runCatching {
            val network = cm.activeNetwork ?: return@runCatching NetworkAvailability.OFFLINE
            val caps = cm.getNetworkCapabilities(network) ?: return@runCatching NetworkAvailability.OFFLINE
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            // VALIDATED is ideal; fall back to INTERNET for emulators / captive portals.
            if (hasInternet && (validated || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            ) {
                NetworkAvailability.ONLINE
            } else {
                NetworkAvailability.OFFLINE
            }
        }.getOrElse {
            Log.w(TAG, "probe failed", it)
            NetworkAvailability.OFFLINE
        }
    }

    companion object {
        private const val TAG = "NetworkStateManager"
    }
}
