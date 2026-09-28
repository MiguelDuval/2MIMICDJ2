package com.miguenduval.mimicdj2.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrpcServerSocketFactoryTest {
    @Test
    fun creates_an_ipv4_wildcard_listener() {
        val diagnostics = ServerDiagnostics()
        try {
            GrpcServerSocketFactory(diagnostics).createServerSocket(0).use { socket ->
                assertTrue(socket.isBound)
                assertTrue(socket.localPort > 0)
                assertEquals("0.0.0.0", socket.inetAddress.hostAddress)
            }
        } finally {
            diagnostics.shutdown()
        }
    }
}
