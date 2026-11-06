package com.miguenduval.mimicdj2.server

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Verifies the exact HTTP endpoint that Engine will use before discovery is
 * published. A successful bind alone is not enough: the listener must accept
 * a TCP connection and answer the same /ping request shape used by EAAS.
 */
internal object EaasHttpListenerProbe {
    fun verify(
        host: String,
        port: Int,
        maxAttempts: Int = 6,
        retryDelayMillis: Long = 100L
    ): String {
        require(host.isNotBlank()) { "HTTP listener host is blank" }
        require(port in 1..65535) { "HTTP listener port is invalid: $port" }
        require(maxAttempts > 0) { "maxAttempts must be positive" }

        var lastFailure: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 500)
                    socket.soTimeout = 750
                    val request = (
                        "GET /ping HTTP/1.1\r\n" +
                            "Host: $host:$port\r\n" +
                            "Connection: close\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII)
                    socket.getOutputStream().apply {
                        write(request)
                        flush()
                    }

                    val statusLine = socket.getInputStream()
                        .bufferedReader(Charsets.US_ASCII)
                        .readLine()
                        ?: throw IOException("HTTP /ping returned no status line")

                    if (statusLine != "HTTP/1.1 200 OK") {
                        throw IOException("HTTP /ping returned '$statusLine'")
                    }
                    return statusLine
                }
            } catch (failure: Exception) {
                lastFailure = failure
                if (attempt < maxAttempts && retryDelayMillis > 0L) {
                    Thread.sleep(retryDelayMillis)
                }
            }
        }

        throw IOException(
            "HTTP listener did not answer GET /ping at $host:$port " +
                "after $maxAttempts attempts: " +
                (lastFailure?.javaClass?.simpleName ?: "unknown") + ": " +
                (lastFailure?.message ?: "no detail"),
            lastFailure
        )
    }
}
