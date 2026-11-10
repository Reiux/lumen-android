package com.reiux.lumen.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectivityObserver(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val validatedNetworks = java.util.concurrent.ConcurrentHashMap.newKeySet<Network>()

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (hasInternet(network)) {
                validatedNetworks.add(network)
            }
            refreshConnectivity()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (isCapsUsable(networkCapabilities)) {
                validatedNetworks.add(network)
            } else {
                validatedNetworks.remove(network)
            }
            refreshConnectivity()
        }

        override fun onLost(network: Network) {
            validatedNetworks.remove(network)
            refreshConnectivity()
        }

        override fun onUnavailable() {
            validatedNetworks.clear()
            refreshConnectivity()
        }
    }

    private val allNetworksCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (hasInternet(network)) {
                validatedNetworks.add(network)
            }
            refreshConnectivity()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (isCapsUsable(networkCapabilities)) {
                validatedNetworks.add(network)
            } else {
                validatedNetworks.remove(network)
            }
            refreshConnectivity()
        }

        override fun onLost(network: Network) {
            validatedNetworks.remove(network)
            refreshConnectivity()
        }
    }

    @Suppress("DEPRECATION")
    private val connectivityReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            refreshConnectivity()
        }
    }

    init {
        try {
            cm.registerDefaultNetworkCallback(defaultCallback)
        } catch (_: Exception) {}

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, allNetworksCallback)
        } catch (_: Exception) {}

        try {
            @Suppress("DEPRECATION")
            val filter = android.content.IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION).apply {
                addAction(android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED)
            }
            appContext.registerReceiver(connectivityReceiver, filter)
        } catch (_: Exception) {}
    }

    fun unregister() {
        try { cm.unregisterNetworkCallback(defaultCallback) } catch (_: Exception) {}
        try { cm.unregisterNetworkCallback(allNetworksCallback) } catch (_: Exception) {}
        try { appContext.unregisterReceiver(connectivityReceiver) } catch (_: Exception) {}
    }

    private fun refreshConnectivity() {
        // Always re-verify tracked networks against ConnectivityManager so a newly turned-off
        // radio immediately drops out even before onLost finishes firing.
        validatedNetworks.removeAll { !hasInternet(it) }
        _isConnected.value = checkCurrentConnectivity() || validatedNetworks.isNotEmpty()
    }

    private fun isCapsUsable(caps: NetworkCapabilities): Boolean {
        val notSuspended = if ( android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        } else true
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            notSuspended
    }

    private fun hasInternet(network: Network): Boolean {
        val caps = try { cm.getNetworkCapabilities(network) } catch (_: Exception) { null } ?: return false
        return isCapsUsable(caps)
    }

    private fun checkCurrentConnectivity(): Boolean {
        val activeNetwork = try { cm.activeNetwork } catch (_: Exception) { null } ?: return false
        return hasInternet(activeNetwork)
    }
}
