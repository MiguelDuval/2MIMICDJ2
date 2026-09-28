package com.miguenduval.mimicdj2.server

import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One-shot diagnostic scan of the Prime GO's transient TCP service range.
 *
 * This is deliberately diagnostic-only. It does not select a protocol port
 * and it never changes the server's advertised endpoint.
 */
object PrimeGoPortScanner {
    private const val FIRST_PORT = 30000
    private const val LAST_PORT = 47000
    private const val CONNECT_TIMEOUT_MS = 120
    private const val WORKERS = 128
    private const val TIMEOUT_SECONDS = 45

    data class Result(
        val summary: String,
        val completed: Int,
        val total: Int
    )

    fun scan(host: String, onProgress: (completed: Int, total: Int) -> Unit): Result {
        val open = Collections.synchronizedList(mutableListOf<Int>())
        val completed = AtomicInteger(0)
        val total = LAST_PORT - FIRST_PORT + 1
        val executor = Executors.newFixedThreadPool(WORKERS)

        try {
            for (port in FIRST_PORT..LAST_PORT) {
                executor.execute {
                    if (probe(host, port)) {
                        open.add(port)
                    }
                    val done = completed.incrementAndGet()
                    if (done % 250 == 0 || done == total) {
                        onProgress(done, total)
                    }
                }
            }

            executor.shutdown()
            if (!executor.awaitTermination(TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)) {
                executor.shutdownNow()
                val ports = open.sorted()
                val summary = "TIMEOUT range=$FIRST_PORT-$LAST_PORT open=" +
                    ports.joinToString(",").ifEmpty { "none" }
                return Result(summary, completed.get(), total)
            }

            val ports = open.sorted()
            val summary = if (ports.isEmpty()) {
                "DONE range=$FIRST_PORT-$LAST_PORT open=none"
            } else {
                "DONE range=$FIRST_PORT-$LAST_PORT open=" + ports.joinToString(",")
            }
            return Result(summary, completed.get(), total)
        } catch (t: Throwable) {
            executor.shutdownNow()
            return Result(
                "FAILED: " + t.javaClass.simpleName + ": " + (t.message ?: "no message"),
                completed.get(),
                total
            )
        }
    }

    private fun probe(host: String, port: Int): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            }
            true
        } catch (_: Throwable) {
            false
        }
}
