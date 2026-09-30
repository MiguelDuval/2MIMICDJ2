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
}
