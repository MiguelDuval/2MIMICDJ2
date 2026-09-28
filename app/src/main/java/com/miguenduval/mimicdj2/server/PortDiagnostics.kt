package com.miguenduval.mimicdj2.server

import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Read-only diagnostics for port binding and local kernel listener state.
 *
 * The production server does not depend on these probes. They answer whether
 * TCP/50020 is blocked globally, blocked only on wildcard bind, already owned,
 * or affected by socket-option behaviour.
 */
object PortDiagnostics {
    private const val TARGET_PORT = 50020
    private const val CACHE_MS = 5_000L

    @Volatile private var cachedAtMs = 0L
    @Volatile private var cachedText: String? = null

    @Synchronized
    fun snapshot(lanIpv4: String? = null): String {
        val now = System.currentTimeMillis()
        cachedText?.takeIf { now - cachedAtMs < CACHE_MS }?.let { return it }

        val text = buildString {
            append("Java 50019 wildcard: ").append(probeJava("0.0.0.0", 50019)).append('\n')
            append("Java 50020 wildcard: ").append(probeJava("0.0.0.0", TARGET_PORT)).append('\n')
            append("Java 50021 wildcard: ").append(probeJava("0.0.0.0", 50021)).append('\n')
            append("Java 50022 wildcard: ").append(probeJava("0.0.0.0", 50022)).append('\n')
            append("Java 50100 wildcard: ").append(probeJava("0.0.0.0", 50100)).append('\n')
            append("Java 60000 wildcard: ").append(probeJava("0.0.0.0", 60000)).append('\n')

            append("Native 50020 wildcard: ").append(probeNative("0.0.0.0", TARGET_PORT, false)).append('\n')
            append("Native 50020 wildcard reuseport: ").append(probeNative("0.0.0.0", TARGET_PORT, true)).append('\n')
            append("Native 50021 wildcard: ").append(probeNative("0.0.0.0", 50021, false)).append('\n')

            if (!lanIpv4.isNullOrBlank()) {
                append("Java 50020 LAN ").append(lanIpv4).append(": ")
                    .append(probeJava(lanIpv4, TARGET_PORT)).append('\n')
                append("Native 50020 LAN ").append(lanIpv4).append(": ")
                    .append(probeNative(lanIpv4, TARGET_PORT, false)).append('\n')
            } else {
                append("Java 50020 LAN: no LAN IPv4 supplied\n")
                append("Native 50020 LAN: no LAN IPv4 supplied\n")
            }

            append("Java 50020 loopback: ").append(probeJava("127.0.0.1", TARGET_PORT)).append('\n')
            append("Native 50020 loopback: ").append(probeNative("127.0.0.1", TARGET_PORT, false)).append('\n')
            append("Native 50020 IPv6 wildcard: ").append(probeNativeIpv6(TARGET_PORT)).append('\n')

            append("Java ephemeral wildcard: ").append(probeJava("0.0.0.0", 0)).append('\n')
            append("tcp/50020 proc listeners: ").append(findProcListeners("/proc/net/tcp", TARGET_PORT)).append('\n')
            append("tcp6/50020 proc listeners: ").append(findProcListeners("/proc/net/tcp6", TARGET_PORT)).append('\n')

            append("ip_local_port_range: ")
                .append(readSysctl("/proc/sys/net/ipv4/ip_local_port_range")).append('\n')
            append("ip_local_reserved_ports: ")
                .append(readSysctl("/proc/sys/net/ipv4/ip_local_reserved_ports")).append('\n')
            append("ip_local_unbindable_ports: ")
                .append(readSysctl("/proc/sys/net/ipv4/ip_local_unbindable_ports"))
        }

        cachedText = text
        cachedAtMs = now
        return text
    }

    private fun probeJava(host: String, port: Int): String = try {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(host, port))
        }
        "ALLOWED"
    } catch (t: Throwable) {
        t.javaClass.simpleName + ": " + (t.message ?: "no message")
    }

    private fun probeNative(host: String, port: Int, reusePort: Boolean): String {
        val fd = try {
            Os.socket(OsConstants.AF_INET, OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP)
        } catch (t: Throwable) {
            return "socket " + t.javaClass.simpleName + ": " + (t.message ?: "no message")
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, OsConstants.SO_REUSEADDR, 1)
            }
            if (reusePort) {
                val reusePortConstant = runCatching {
                    OsConstants::class.java.getField("SO_REUSEPORT").getInt(null)
                }.getOrNull() ?: return "SO_REUSEPORT_UNAVAILABLE"
                Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, reusePortConstant, 1)
            }
            Os.bind(fd, InetAddress.getByName(host), port)
            Os.listen(fd, 4)
            "ALLOWED"
        } catch (t: Throwable) {
            t.javaClass.simpleName + ": " + (t.message ?: "no message")
        } finally {
            runCatching { Os.close(fd) }
        }
    }

    private fun probeNativeIpv6(port: Int): String {
        val fd = try {
            Os.socket(OsConstants.AF_INET6, OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP)
        } catch (t: Throwable) {
            return "socket " + t.javaClass.simpleName + ": " + (t.message ?: "no message")
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, OsConstants.SO_REUSEADDR, 1)
            }
            Os.bind(fd, InetAddress.getByName("::"), port)
            Os.listen(fd, 4)
            "ALLOWED"
        } catch (t: Throwable) {
            t.javaClass.simpleName + ": " + (t.message ?: "no message")
        } finally {
            runCatching { Os.close(fd) }
        }
    }

    private fun findProcListeners(path: String, port: Int): String =
        runCatching {
            val hexPort = "%04X".format(port)
            val matches = File(path).readLines()
                .drop(1)
                .filter { line ->
                    val fields = line.trim().split(Regex("\\s+"))
                    fields.size >= 4 &&
                        fields[1].substringAfter(':').equals(hexPort, ignoreCase = true) &&
                        fields[3] == "0A"
                }
                .map { it.trim() }

            matches.joinToString(" || ").ifEmpty { "none" }
        }.getOrElse { "<unavailable>" }

    private fun readSysctl(path: String): String =
        runCatching { File(path).readText().trim().ifEmpty { "<empty>" } }
            .getOrElse { "<unavailable: " + it.javaClass.simpleName + ">" }
}
