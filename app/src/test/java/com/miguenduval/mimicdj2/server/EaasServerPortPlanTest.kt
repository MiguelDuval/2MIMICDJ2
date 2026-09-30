package com.miguenduval.mimicdj2.server

import org.junit.Assert.assertEquals
import org.junit.Test

class EaasServerPortPlanTest {
    @Test
    fun bridge_mode_uses_only_the_fixed_gateway_target_port() {
        assertEquals(
            listOf(EaasServerPortPlan.BRIDGE_GRPC_PORT),
            EaasServerPortPlan.grpcCandidates(
                requestedPort = EaasEndpointConfig.STANDARD_GRPC_PORT,
                bridgeMode = true
            )
        )
    }

    @Test
    fun normal_mode_keeps_existing_high_port_fallbacks() {
        assertEquals(
            listOf(50010, 50100, 60000, 0),
            EaasServerPortPlan.grpcCandidates(
                requestedPort = 50010,
                bridgeMode = false
            )
        )
    }
}
