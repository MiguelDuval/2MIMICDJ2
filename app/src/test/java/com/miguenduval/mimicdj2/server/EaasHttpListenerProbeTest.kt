package com.miguenduval.mimicdj2.server

import java.net.InetAddress
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.concurrent.thread

class EaasHttpListenerProbeTest {
    @Test
    fun accepts_a_real_http_200_ping_response() {
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { listener ->
            val serverThread = thread(isDaemon = true) {
                listener.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader(Charsets.US_ASCII)
                    while (reader.readLine()?.isNotEmpty() == true) {
                        // Consume request headers.
                    }
                    client.getOutputStream().apply {
                        write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n\r\n"
                            ).toByteArray(Charsets.US_ASCII)
                        )
                        flush()
                    }
                }
            }

            val status = EaasHttpListenerProbe.verify(
                host = "127.0.0.1",
                port = listener.localPort,
                maxAttempts = 1,
                retryDelayMillis = 0
            )

            serverThread.join(1_000)
            assertEquals("HTTP/1.1 200 OK", status)
        }
    }
}
