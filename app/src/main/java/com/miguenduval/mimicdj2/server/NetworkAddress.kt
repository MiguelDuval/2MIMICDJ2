package com.miguenduval.mimicdj2.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Selects the Android Network that owns the LAN IPv4 address advertised to
 * Engine OS and binds the process to that Network before opening listeners.
 */
object NetworkAddress {

    fun currentLanIpv4(context: Context): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
        val candidates = interfaces
            .filter { runCatching { it.isUp }.getOrDefault(false) }
            .filterNot { it.isLoopback || it.isVirtual }
            .flatMap { network ->
                network.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .map { network.name.lowercase() to it.hostAddress }
            }

        return candidates.firstOrNull { (name, _) ->
            name.contains("wlan") || name.contains("wifi")
        }?.second ?: candidates.firstOrNull()?.second
    }

    fun networkForIpv4(context: Context, ipv4: String): Network? {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return null

        val exact = connectivity.allNetworks.firstOrNull { network ->
            connectivity.getLinkProperties(network)
                ?.linkAddresses
                ?.any { address ->
                    address.address is Inet4Address &&
                        address.address.hostAddress == ipv4
                } == true
        }
        return exact ?: connectivity.activeNetwork
    }

    fun bindProcessToLanIpv4Network(context: Context, ipv4: String): String {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return "NO_CONNECTIVITY_MANAGER"

        val network = networkForIpv4(context, ipv4) ?: return "NO_NETWORK"

        val bound = runCatching {
            connectivity.bindProcessToNetwork(network)
        }.getOrDefault(false)

        val interfaceName = connectivity.getLinkProperties(network)?.interfaceName ?: "unknown"
        return if (bound) {
            "OK interface=$interfaceName"
        } else {
            "BIND_FAILED interface=$interfaceName"
        }
    }

    fun clearProcessBinding(context: Context) {
        runCatching {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
            connectivity.bindProcessToNetwork(null)
        }
    }
}
