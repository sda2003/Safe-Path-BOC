package com.hades.safepath.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors internet connectivity using [ConnectivityManager.registerDefaultNetworkCallback].
 *
 * Emits true when a network with INTERNET capability is available, false otherwise.
 * Call [release] when the owning component is destroyed to unregister the callback.
 */
class ConnectivityMonitor(private val context: Context) {

    private val TAG = "ConnectivityMonitor"

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _connectivityState = MutableStateFlow(isCurrentlyConnected())

    /** Emits true when internet is available, false otherwise. */
    val connectivityState: StateFlow<Boolean> = _connectivityState.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            _connectivityState.value = true
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            // Re-check in case another network is still active
            _connectivityState.value = isCurrentlyConnected()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) && networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
            Log.d(TAG, "Network capabilities changed — internet=$hasInternet")
            _connectivityState.value = hasInternet
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        Log.d(TAG, "Network callback registered, initial state=${_connectivityState.value}")
    }

    /** Unregisters the network callback. Call from the owning component's onDestroy(). */
    fun release() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "Network callback unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Network callback was not registered: ${e.message}")
        }
    }

    private fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
