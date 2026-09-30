package com.miguenduval.mimicdj2.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupPublicationGateTest {
    @Test
    fun does_not_publish_server_until_both_tcp_endpoints_are_ready() {
        var published = false

        val committed = StartupPublicationGate.commit(
            grpcPort = 50100,
            httpPort = 0
        ) {
            published = true
        }

        assertFalse(committed)
        assertFalse(published)
    }

    @Test
    fun publishes_server_after_both_tcp_endpoints_are_ready() {
        var published = false

        val committed = StartupPublicationGate.commit(
            grpcPort = 50100,
            httpPort = 50110
        ) {
            published = true
        }

        assertTrue(committed)
        assertTrue(published)
    }
}
