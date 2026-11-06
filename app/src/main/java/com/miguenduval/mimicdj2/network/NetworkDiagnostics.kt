package com.miguenduval.mimicdj2.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Enumeration

/**
 * Network diagnostics and monitoring for the diagnostic app.
 * Tracks active Wi-Fi network, local IPs, interface details, and connectivity state.
 */
class NetworkDiagnostics(context: Context) {

    // Keep a process-lifetime context so diagnostics never retain an Activity.
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            log("Network available: $network")
            updateNetworkInfo(network)
        }

        override fun onLost(network: Network) {
            log("Network lost: $network")
            _networkInfo.value = NetworkInfoData(
                isConnected = false,
                interfaceName = "none",
                ipv4Addresses = emptyList(),
                ipv6Addresses = emptyList(),
                subnet = "unknown",
                gateway = "unknown",
                ssid = "unknown",
                vpnActive = false,
                connectivityState = "DISCONNECTED"
            )
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            log("Capabilities changed: $networkCapabilities")
            updateNetworkInfo(network)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            log("Link properties changed: $linkProperties")
            updateNetworkInfo(network)
        }
    }

    private val _networkInfo = MutableLiveData<NetworkInfoData>()
    val networkInfo: LiveData<NetworkInfoData> = _networkInfo

    private val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.Job())

    data class NetworkInfoData(
        val isConnected: Boolean,
        val interfaceName: String,
        val ipv4Addresses: List<String>,
        val ipv6Addresses: List<String>,
        val subnet: String,
        val gateway: String,
        val ssid: String,
        val vpnActive: Boolean,
        val connectivityState: String
    )

    fun start() {
        scope.launch {
            registerCallback()
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                updateNetworkInfo(activeNetwork)
            }
        }
    }

    fun refresh() {
        scope.launch {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                updateNetworkInfo(activeNetwork)
            }
        }
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            log("Error unregistering network callback: $e")
        }
        scope.cancel()
    }

    private fun registerCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun updateNetworkInfo(network: Network) {
        scope.launch {
            val caps = connectivityManager.getNetworkCapabilities(network)
            val linkProps = connectivityManager.getLinkProperties(network)

            val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val vpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

            val interfaceName = linkProps?.interfaceName ?: "unknown"
            val ipv4Addresses = mutableListOf<String>()
            val ipv6Addresses = mutableListOf<String>()
            var subnet = "unknown"
            var gateway = "unknown"

            linkProps?.linkAddresses?.forEach { prefix ->
                val addr = prefix.address
                if (addr is Inet4Address) {
                    ipv4Addresses.add(addr.hostAddress)
                    subnet = "${addr.hostAddress}/${prefix.prefixLength}"
                } else if (addr is Inet6Address) {
                    ipv6Addresses.add(addr.hostAddress)
                }
            }

            linkProps?.routes?.forEach { route ->
                if (route.isDefaultRoute) {
                    gateway = route.gateway?.hostAddress ?: "unknown"
                }
            }

            val ssid = try {
                val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                val connInfo = wifiManager.connectionInfo
                connInfo?.ssid?.replace("\"", "") ?: "unknown"
            } catch (e: Exception) {
                "unknown (permission?)"
            }

            val connectivityState = when {
                !isConnected -> "DISCONNECTED"
                vpnActive -> "VPN_ACTIVE"
                else -> "CONNECTED"
            }

            _networkInfo.postValue(NetworkInfoData(
                isConnected = isConnected,
                interfaceName = interfaceName,
                ipv4Addresses = ipv4Addresses,
                ipv6Addresses = ipv6Addresses,
                subnet = subnet,
                gateway = gateway,
                ssid = ssid,
                vpnActive = vpnActive,
                connectivityState = connectivityState
            ))
        }
    }

    companion object {
        private const val TAG = "NetworkDiagnostics"
        fun log(msg: String) {
            Timber.tag(TAG).d(msg)
        }
    }
}