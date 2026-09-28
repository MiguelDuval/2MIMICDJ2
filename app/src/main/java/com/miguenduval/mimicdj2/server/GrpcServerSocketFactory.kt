package com.miguenduval.mimicdj2.server

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ServerSocketFactory

/**
 * Forces the gRPC server transport onto an IPv4 wildcard listener.
 *
 * This keeps the inbound listener independent from ConnectivityManager's
 * process-wide network binding. The advertised LAN IPv4 remains the address
 * sent in the EAAS discovery response.
 */
class GrpcServerSocketFactory : ServerSocketFactory() {
    override fun createServerSocket(): ServerSocket = LoggingServerSocket()

    override fun createServerSocket(port: Int): ServerSocket =
        LoggingServerSocket().also { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("0.0.0.0", port))
        }

    override fun createServerSocket(port: Int, backlog: Int): ServerSocket =
        LoggingServerSocket().also { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("0.0.0.0", port), backlog)
        }

    override fun createServerSocket(
        port: Int,
        backlog: Int,
        ifAddress: InetAddress
    ): ServerSocket =
        LoggingServerSocket().also { socket ->
            socket.reuseAddress = true
            // Deliberately ignore ifAddress: all IPv4 LAN interfaces are valid
            // ingress paths for the local Engine Remote Library server.
            socket.bind(InetSocketAddress("0.0.0.0", port), backlog)
        }

    private class LoggingServerSocket : ServerSocket() {
        override fun accept(): Socket = super.accept()
    }
}
