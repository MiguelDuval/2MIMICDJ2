package com.miguenduval.mimicdj2.server

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Diagnostics learned from the original Mimic DJ port-50020 investigation.
 * Compare the managed Java socket path with the native Android/Linux path
 * and inspect kernel port policy without changing production behaviour.
 */
object PortDiagnostics {
    private const val TARGET_PORT = 50020
    private const val CACHE_MS = 5_000L

    @Volatile private var cachedAtMs = 0L
    @Volatile private var cachedText: String? = null

    @Synchronized
    fun snapshot(): String {
        val now = System.currentTimeMillis()
        cachedText?.takeIf { now - cachedAtMs < CACHE_MS }?.let { return it }

        val text = buildString {
            append("Java 50019: ").append(probeJava(50019)).append('\n')
            append("Java 50020: ").append(probeJava(50020)).append('\n')
            append("Java 50021: ").append(probeJava(50021)).append('\n')
            append("Java 50022: ").append(probeJava(50022)).append('\n')
            append("Java 50100: ").append(probeJava(50100)).append('\n')
            append("Java 60000: ").append(probeJava(60000)).append('\n')
            append("Native 50020: ").append(probeNative(TARGET_PORT)).append('\n')
            append("Native 50021: ").append(probeNative(50021)).append('\n')
            append("Java ephemeral: ").append(probeJava(0)).append('\n')
            append("ip_local_port_range: ").append(readSysctl("/proc/sys/net/ipv4/ip_local_port_range")).append('\n')
            append("ip_local_reserved_ports: ").append(readSysctl("/proc/sys/net/ipv4/ip_local_reserved_ports")).append('\n')
            append("ip_local_unbindable_ports: ").append(readSysctl("/proc/sys/net/ipv4/ip_local_unbindable_ports"))
        }

        cachedText = text
        cachedAtMs = now
        return text
    }

    private fun probeJava(port: Int): String = try {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("0.0.0.0", port))
        }
        "ALLOWED"
    } catch (t: Throwable) {
        t.javaClass.simpleName + ": " + (t.message ?: "no message")
    }

    private fun probeNative(port: Int): String {
        val fd = try {
            Os.socket(OsConstants.AF_INET, OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP)
        } catch (t: Throwable) {
            return "socket " + t.javaClass.simpleName + ": " + (t.message ?: "no message")
        }
        return try {
            Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, OsConstants.SO_REUSEADDR, 1)
            Os.bind(fd, java.net.InetAddress.getByName("0.0.0.0"), port)
            Os.listen(fd, 4)
            "ALLOWED"
        } catch (t: Throwable) {
            t.javaClass.simpleName + ": " + (t.message ?: "no message")
        } finally {
            runCatching { Os.close(fd) }
        }
    }

    private fun readSysctl(path: String): String =
        runCatching { File(path).readText().trim().ifEmpty { "<empty>" } }
            .getOrElse { "<unavailable: " + it.javaClass.simpleName + ">" }
}