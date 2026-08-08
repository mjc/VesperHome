package com.sergioasenjo.ltvlauncher.status

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkTransport {
    WIFI,
    ETHERNET,
    OTHER,
    NONE
}

data class NetworkStatus(
    val transport: NetworkTransport = NetworkTransport.NONE,
    val validated: Boolean = false,
    val wifiLevel: Int? = null
)

class NetworkStatusRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val connectivityManager = applicationContext.getSystemService(ConnectivityManager::class.java)

    fun observeStatus(): Flow<NetworkStatus> = callbackFlow {
        fun sendCapabilities(capabilities: NetworkCapabilities?) {
            trySend(capabilities.toStatus())
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                sendCapabilities(capabilities)
            }

            override fun onLost(network: Network) {
                trySend(NetworkStatus())
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } else {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback
            )
        }
        sendCapabilities(connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork))
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun NetworkCapabilities?.toStatus(): NetworkStatus {
        if (this == null) return NetworkStatus()
        val validated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return when {
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus(
                transport = NetworkTransport.WIFI,
                validated = validated,
                wifiLevel = wifiSignalLevel()
            )

            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkStatus(
                transport = NetworkTransport.ETHERNET,
                validated = validated
            )

            else -> NetworkStatus(NetworkTransport.OTHER, validated)
        }
    }

    private fun NetworkCapabilities.wifiSignalLevel(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rssi = signalStrength
        if (rssi == SIGNAL_STRENGTH_UNSPECIFIED || rssi <= -127) return null
        return when {
            rssi >= -55 -> 4
            rssi >= -65 -> 3
            rssi >= -75 -> 2
            rssi >= -85 -> 1
            else -> 0
        }
    }

    private companion object {
        const val SIGNAL_STRENGTH_UNSPECIFIED = Int.MIN_VALUE
    }
}
