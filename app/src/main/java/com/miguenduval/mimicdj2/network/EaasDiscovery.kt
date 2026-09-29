package com.miguenduval.mimicdj2.network

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/**
 * Minimal EAAS discovery codec used by Engine OS Remote Library.
 *
 * Prime GO hardware testing shows that the legacy response shape is required
 * for the device to keep the discovered source visible in the Source menu:
 * response: "EAAS" + 0x01 0x01 + token(16) +
 *            UTF-16BE hostname + UTF-8 grpc URL + UTF-16BE version +
 *            0x01 + UTF-16BE extra.
 *
 * The current chrisle/StageLinq implementation omits the tail, but that shape
 * caused Prime GO to stop displaying Mimic DJ, so the Prime GO compatibility
 * path intentionally keeps the legacy tail.
 */
object EaasDiscovery {
    const val PORT = 11224

    private val MAGIC = byteArrayOf(
        'E'.code.toByte(),
        'A'.code.toByte(),
        'A'.code.toByte(),
        'S'.code.toByte()
    )

    private val REQUEST = MAGIC + byteArrayOf(0x01, 0x00)
    private val RESPONSE_PREFIX = MAGIC + byteArrayOf(0x01, 0x01)

    private val secureRandom = SecureRandom()

    fun isDiscoveryRequest(data: ByteArray, length: Int = data.size): Boolean {
        if (length < REQUEST.size) return false
        for (i in REQUEST.indices) {
            if (data[i] != REQUEST[i]) return false
        }
        return true
    }

    fun newToken(): ByteArray = ByteArray(16).also(secureRandom::nextBytes)

    fun tokenToHex(token: ByteArray): String =
        token.joinToString(separator = "") { "%02x".format(it) }

    fun tokenFromHex(value: String): ByteArray? = runCatching {
        if (value.length != 32 || value.any { it !in "0123456789abcdefABCDEF" }) return null
        ByteArray(16) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }.getOrNull()

    fun buildResponse(
        token: ByteArray,
        hostname: String,
        grpcHost: String,
        grpcPort: Int,
        softwareVersion: String,
        extra: String = "_"
    ): ByteArray {
        require(token.size == 16) { "EAAS token must contain exactly 16 bytes" }

        val output = ByteArrayOutputStream(112)
        output.write(RESPONSE_PREFIX)
        output.write(token)
        writeNetworkString(output, hostname, Charsets.UTF_16BE)
        writeNetworkString(output, "grpc://$grpcHost:$grpcPort", Charsets.UTF_8)
        writeNetworkString(output, softwareVersion, Charsets.UTF_16BE)
        output.write(0x01)
        writeNetworkString(output, extra, Charsets.UTF_16BE)
        return output.toByteArray()
    }

    private fun writeNetworkString(
        output: ByteArrayOutputStream,
        value: String,
        charset: java.nio.charset.Charset
    ) {
        val encoded = value.toByteArray(charset)
        val length = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(encoded.size)
            .array()
        output.write(length)
        output.write(encoded)
    }
}
