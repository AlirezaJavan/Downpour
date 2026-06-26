package io.github.alirezajavan10.downpour.internal.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import io.github.alirezajavan10.downpour.api.NetworkType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class NetworkStatus(
    val isConnected: Boolean,
    val isMetered: Boolean,
    val isNotRoaming: Boolean,
) {
    fun satisfies(networkType: NetworkType): Boolean =
        when (networkType) {
            NetworkType.ANY -> isConnected
            NetworkType.UNMETERED -> isConnected && !isMetered
            NetworkType.NOT_ROAMING -> isConnected && isNotRoaming
        }
}

internal class NetworkMonitor(
    context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun snapshot(): NetworkStatus {
        val capabilities =
            connectivityManager.activeNetwork
                ?.let { connectivityManager.getNetworkCapabilities(it) }
        return capabilities.toStatus()
    }

    val changes: Flow<NetworkStatus> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(snapshot())
                    }

                    override fun onLost(network: Network) {
                        trySend(snapshot())
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        trySend(capabilities.toStatus())
                    }
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                val request =
                    NetworkRequest
                        .Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                connectivityManager.registerNetworkCallback(request, callback)
            }

            trySend(snapshot())
            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()

    private fun NetworkCapabilities?.toStatus(): NetworkStatus {
        if (this == null) return NetworkStatus(false, isMetered = true, isNotRoaming = false)
        val connected = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val unmetered = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val notRoaming =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            } else {
                @Suppress("DEPRECATION")
                connectivityManager.activeNetworkInfo?.isRoaming == false
            }
        return NetworkStatus(connected, isMetered = !unmetered, isNotRoaming = notRoaming)
    }
}
