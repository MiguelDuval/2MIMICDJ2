package com.miguenduval.mimicdj2.server

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ServerSocketFactory

/**
 * Forces the gRPC server transport onto the concrete IPv4 LAN address.
 *
 * Some Android/vendor network stacks reject wildcard TCP listeners with EPERM.
 * Binding directly to the interface address avoids that policy while still
 * exposing the service to Engine OS on the same LAN.
 */
class GrpcServerSocketFactory(
    private val diagnostics: ServerDiagnostics,
    private val bindAddress: String = "0.0.0.0",
    private val bindIpv6Wildcard: Boolean = false,
    private val reuseAddress: Boolean = false,
    private val localAddresses: Set<String> = emptySet()
) : ServerSocketFactory() {
    override fun createServerSocket(): ServerSocket = LoggingServerSocket()

    override fun createServerSocket(port: Int): ServerSocket =
        LoggingServerSocket().also { socket ->
            socket.reuseAddress = reuseAddress
            socket.bind(InetSocketAddress(resolveBindAddress(), port))
        }

    override fun createServerSocket(port: Int, backlog: Int): ServerSocket =
        LoggingServerSocket().also { socket ->
            socket.reuseAddress = reuseAddress
            socket.bind(InetSocketAddress(resolveBindAddress(), port), backlog)
        }

    override fun createServerSocket(
        port: Int,
        backlog: Int,
        ifAddress: InetAddress
    ): ServerSocket =
        LoggingServerSocket().also { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(resolveBindAddress(), port), backlog)
        }

    private fun resolveBindAddress(): InetAddress =
        if (bindIpv6Wildcard) InetAddress.getByName("::") else InetAddress.getByName(bindAddress)

    private inner class LoggingServerSocket : ServerSocket() {
        override fun accept(): Socket {
            val socket = super.accept()
            val remote = socket.remoteSocketAddress
            val remoteIp =
                (remote as? InetSocketAddress)?.address?.hostAddress ?: remote?.toString()
            val local = NetworkAddress.isLocalPeerAddress(remoteIp, localAddresses)
            diagnostics.recordRawTcpAccept(remoteIp, local)
            diagnostics.info(
                "EAAS-TCP",
                "Raw TCP accept from " + (remoteIp ?: "unknown") +
                    ":" + ((remote as? InetSocketAddress)?.port ?: "?") +
                    if (local) " (local self-test)" else " (external peer)"
            )
            return socket
        }
    }
}
