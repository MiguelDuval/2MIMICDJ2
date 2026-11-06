package com.miguenduval.mimicdj2.server

import org.junit.Assert.assertEquals
import org.junit.Test

class EaasEndpointConfigTest {
    @Test
    fun bridge_mode_advertises_standard_engine_endpoint() {
        val endpoint = EaasEndpointConfig.advertisedEndpoint(
            localHost = "10.0.0.20",
            localGrpcPort = 50100,
            bridgeHost = "10.0.0.10"
        )

        assertEquals("10.0.0.10", endpoint.host)
        assertEquals(50010, endpoint.grpcPort)
    }

    @Test
    fun blank_bridge_host_keeps_actual_local_endpoint() {
        val endpoint = EaasEndpointConfig.advertisedEndpoint(
            localHost = "10.0.0.20",
            localGrpcPort = 50100,
            bridgeHost = "   "
        )

        assertEquals("10.0.0.20", endpoint.host)
        assertEquals(50100, endpoint.grpcPort)
    }

    @Test
    fun direct_mode_advertises_the_actual_paired_http_port() {
        assertEquals(50110, EaasEndpointConfig.advertisedHttpPort(50110, ""))
    }

    @Test
    fun bridge_mode_advertises_engine_standard_http_port() {
        assertEquals(50020, EaasEndpointConfig.advertisedHttpPort(50110, "10.0.0.10"))
    }
}
