package com.mixpanel.android.sessionreplay.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.mixpanel.android.sessionreplay.logging.Logger

interface NetworkMonitoring {
    val isUsingWiFi: Boolean
}

class NetworkMonitor(
    private val connectivityManager: ConnectivityManager
) : NetworkMonitoring {
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateConnectionType(network)
            }

            override fun onLost(network: Network) {
                isConnected = false
                isUsingWiFi = false
                Logger.debug(message = "Internet Connected: $isConnected, Using WiFi: $isUsingWiFi")
            }
        }

    var isConnected = false
        private set
    override var isUsingWiFi = false
        private set

    init {
        val networkRequestCapability =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (networkRequestCapability != null) {
            connectivityManager.registerNetworkCallback(
                networkRequestCapability.build(),
                networkCallback
            )
        } else {
            Logger.warn(message = "Failed to register network callback, isUsingWiFi will always be false")
        }
    }

    private fun updateConnectionType(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
        isUsingWiFi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        Logger.debug(message = "Internet Connected: $isConnected, Using WiFi: $isUsingWiFi")
    }
}
