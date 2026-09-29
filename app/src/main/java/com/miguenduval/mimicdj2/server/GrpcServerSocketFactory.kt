package com.miguenduval.mimicdj2.server

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ServerSocketFactory

/**
 * Forces the gRPC server transport onto an IPv4 wildcard listener.
 *
 * This keeps inbound TCP acceptance independent from ConnectivityManager's
 * process-wide network binding. The advertised LAN IPv4 remains the address
 * sent in the EAAS discovery response.
 */
class GrpcServerSocketFactory(
    private val diagnostics: ServerDiagnostics,
    private val bindAddress: String
) : ServerSocketFactory() {
    override fun createServerSocket(): ServerSocket = LoggingServerSocket()

    override fun createServerSocket(port: Int): ServerSocket =
        LoggingServerSocket().also { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(bindAddress, port))
        }

    override fun createServerSocket(port: Int, backlog: Int): ServerSocket =
        LoggingServerSocket().also { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(bindAddress, port), backlog)
        }

    override fun createServerSocket(
        port: Int,
        backlog: Int,
        ifAddress: InetAddress
    ): ServerSocket =
        LoggingServerSocket().also { socket ->
            socket.reuseAddress = true
            // Deliberately ignore ifAddress: every IPv4 LAN interface is a
            // valid ingress path for the local Engine Remote Library server.
            socket.bind(InetSocketAddress("0.0.0.0", port), backlog)
        }

    private inner class LoggingServerSocket : ServerSocket() {
        override fun accept(): Socket {
            val socket = super.accept()
            val remote = socket.remoteSocketAddress
            val remoteIp = (remote as? InetSocketAddress)?.address?.hostAddress
            val localAddress = remoteIp?.let { isLocalAddress(it) } == true
            diagnostics.rawTcpAccepts.incrementAndGet()
            if (!localAddress) {
                diagnostics.externalRawTcpAccepts.incrementAndGet()
            }
            diagnostics.lastClientContact = System.currentTimeMillis()
            diagnostics.lastClientIp = remoteIp ?: remote?.toString()
            val scope = if (localAddress) "local/self-test" else "EXTERNAL/remote"
            diagnostics.info(
                "EAAS-TCP",
                "Raw TCP accept ($scope) from " +
                    (diagnostics.lastClientIp ?: "unknown") + ":" +
                    ((remote as? InetSocketAddress)?.port ?: "?")
            )
            return socket
        }
    }

    private fun isLocalAddress(address: String): Boolean =
        runCatching {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.any { it.hostAddress == address }
                ?: false
        }.getOrDefault(false)
}
