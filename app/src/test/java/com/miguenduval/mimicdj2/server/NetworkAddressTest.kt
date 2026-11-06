package com.miguenduval.mimicdj2.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAddressTest {
    @Test
    fun recognizes_phone_lan_address_as_local() {
        assertTrue(NetworkAddress.isLocalPeerAddress("10.93.187.7", setOf("10.93.187.7")))
    }

    @Test
    fun recognizes_loopback_as_local() {
        assertTrue(NetworkAddress.isLocalPeerAddress("127.0.0.1", emptySet()))
        assertTrue(NetworkAddress.isLocalPeerAddress("::1", emptySet()))
    }

    @Test
    fun does_not_classify_prime_go_as_a_local_self_test() {
        assertFalse(NetworkAddress.isLocalPeerAddress("10.93.187.191", setOf("10.93.187.7")))
    }
}
