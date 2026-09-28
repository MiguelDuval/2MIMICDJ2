package com.miguenduval.mimicdj2.network

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/**
 * Minimal EAAS discovery codec used by Engine OS Remote Library.
 *
 * Wire format used by current StageLinq/EAAS implementations:
 * request:  "EAAS" + 0x01 0x00
 * response: "EAAS" + 0x01 0x01 + token(16) +
 *           UTF-16BE hostname + UTF-8 grpc URL + UTF-16BE version
 *
 * The older go-stagelinq implementation documented an additional 0x01 +
 * UTF-16BE "extra" field after the version. The current chrisle/StageLinq
 * implementation does not emit that tail. Prime GO hardware has so far
 * accepted our discovery request but never followed our legacy-tailed
 * response with a TCP/50010 connection, so v6 deliberately uses the
 * modern response shape for this hardware test.
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

    fun buildResponse(
        token: ByteArray,
        hostname: String,
        grpcHost: String,
        grpcPort: Int,
        softwareVersion: String
    ): ByteArray {
        require(token.size == 16) { "EAAS token must contain exactly 16 bytes" }

        val output = ByteArrayOutputStream(96)
        output.write(RESPONSE_PREFIX)
        output.write(token)
        writeNetworkString(output, hostname, Charsets.UTF_16BE)
        writeNetworkString(output, "grpc://$grpcHost:$grpcPort", Charsets.UTF_8)
        writeNetworkString(output, softwareVersion, Charsets.UTF_16BE)
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
