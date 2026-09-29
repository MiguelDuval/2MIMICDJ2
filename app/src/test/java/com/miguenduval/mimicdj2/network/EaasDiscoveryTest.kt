package com.miguenduval.mimicdj2.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EaasDiscoveryTest {
    @Test
    fun request_has_expected_wire_prefix() {
        val request = byteArrayOf(
            'E'.code.toByte(),
            'A'.code.toByte(),
            'A'.code.toByte(),
            'S'.code.toByte(),
            0x01,
            0x00
        )
        assertTrue(EaasDiscovery.isDiscoveryRequest(request))
    }

    @Test
    fun response_contains_expected_header_token_and_legacy_tail() {
        val token = ByteArray(16) { it.toByte() }
        val response = EaasDiscovery.buildResponse(
            token = token,
            hostname = "Mimic DJ",
            grpcHost = "192.168.1.20",
            grpcPort = 50010,
            softwareVersion = "1.0.0-debug"
        )

        assertArrayEquals(
            byteArrayOf(
                'E'.code.toByte(),
                'A'.code.toByte(),
                'A'.code.toByte(),
                'S'.code.toByte(),
                0x01,
                0x01
            ),
            response.copyOfRange(0, 6)
        )
        assertArrayEquals(token, response.copyOfRange(6, 22))

        var offset = 22
        val hostnameBytes = "Mimic DJ".toByteArray(Charsets.UTF_16BE)
        val hostnameLength = ByteBuffer.wrap(response, offset, 4)
            .order(ByteOrder.BIG_ENDIAN).int
        assertEquals(hostnameBytes.size, hostnameLength)
        offset += 4
        assertArrayEquals(hostnameBytes, response.copyOfRange(offset, offset + hostnameLength))
        offset += hostnameLength

        val urlBytes = "grpc://192.168.1.20:50010".toByteArray(Charsets.UTF_8)
        val urlLength = ByteBuffer.wrap(response, offset, 4)
            .order(ByteOrder.BIG_ENDIAN).int
        assertEquals(urlBytes.size, urlLength)
        offset += 4
        assertArrayEquals(urlBytes, response.copyOfRange(offset, offset + urlLength))
        offset += urlLength

        val versionBytes = "1.0.0-debug".toByteArray(Charsets.UTF_16BE)
        val versionLength = ByteBuffer.wrap(response, offset, 4)
            .order(ByteOrder.BIG_ENDIAN).int
        assertEquals(versionBytes.size, versionLength)
        offset += 4
        assertArrayEquals(versionBytes, response.copyOfRange(offset, offset + versionLength))
        offset += versionLength

        assertEquals(0x01, response[offset].toInt())
        offset += 1

        val extraBytes = "_".toByteArray(Charsets.UTF_16BE)
        val extraLength = ByteBuffer.wrap(response, offset, 4)
            .order(ByteOrder.BIG_ENDIAN).int
        assertEquals(extraBytes.size, extraLength)
        offset += 4
        assertArrayEquals(extraBytes, response.copyOfRange(offset, offset + extraLength))
        offset += extraLength

        assertEquals(response.size, offset)
    }
}
