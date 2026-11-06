package com.miguenduval.mimicdj2.server

import org.junit.Assert.assertEquals
import org.junit.Test

class EaasServerPortPlanTest {
    @Test
    fun normal_mode_keeps_fallbacks_for_android_vendor_port_restrictions() {
        assertEquals(
            listOf(50010, 50100, 60000, 0),
            EaasServerPortPlan.grpcCandidates(requestedPort = 50010)
        )
    }

    @Test
    fun avoids_duplicate_requested_ports() {
        assertEquals(
            listOf(50100, 60000, 0),
            EaasServerPortPlan.grpcCandidates(requestedPort = 50100)
        )
    }
}
