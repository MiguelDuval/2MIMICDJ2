package com.miguenduval.mimicdj2.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Centralized diagnostics and logging for the network server.
 * Provides ring buffer for log entries and counters for metrics.
 */
class ServerDiagnostics {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val logChannel = Channel<LogEntry>(capacity = 1000)
    private val logBuffer = mutableListOf<LogEntry>()
    private val maxLogEntries = 500

    // Counters
    val discoveryRxCount = AtomicLong(0)
    val discoveryTxCount = AtomicLong(0)
    val connectionsOpened = AtomicLong(0)
    val externalConnectionsOpened = AtomicLong(0)
    val localSelfTestConnections = AtomicLong(0)
    val rawTcpAccepts = AtomicLong(0)
    val externalRawTcpAccepts = AtomicLong(0)
    val localSelfTestRawTcpAccepts = AtomicLong(0)
    val trustMessages = AtomicLong(0)
    val externalTrustMessages = AtomicLong(0)
    val localSelfTestTrustMessages = AtomicLong(0)
    val rpcCount = AtomicLong(0)
    val externalRpcCount = AtomicLong(0)
    val localSelfTestRpcCount = AtomicLong(0)
    val httpAcceptedConnections = AtomicLong(0)
    val externalHttpAcceptedConnections = AtomicLong(0)
    val localSelfTestHttpAcceptedConnections = AtomicLong(0)
    val httpRequests = AtomicLong(0)
    val externalHttpRequests = AtomicLong(0)
    val localSelfTestHttpRequests = AtomicLong(0)
    val httpRequestParseFailures = AtomicLong(0)
    @Volatile var lastHttpClientIp: String? = null
    @Volatile var lastHttpRequest: String? = null
    @Volatile var lastHttpStatus: String? = null
    @Volatile var httpListenerAddress: String? = null
    @Volatile var httpListenerPort: Int = 0
    @Volatile var httpListenerReadiness: String? = null
    val fileRequests = AtomicLong(0)
    val bytesServed = AtomicLong(0)
    val rangeRequests = AtomicLong(0)
    val errors404 = AtomicLong(0)
    val errors416 = AtomicLong(0)
    val errors500 = AtomicLong(0)
    val openFileFailures = AtomicLong(0)

    // Last timestamps
    var lastDiscoveryRx: Long = 0
    var lastDiscoveryTx: Long = 0
    var lastTrustRequest: Long = 0
    var lastLibraryRequest: Long = 0
    var lastTrackRequest: Long = 0
    var lastFileTransfer: Long = 0
    var lastClientContact: Long = 0
    // Reverse-connectivity probes: the phone checks whether the Prime GO
    // itself exposes a TCP service on candidate ports. This is read-only
    // diagnostics and does not change the server protocol.
    @Volatile var primeGoPort50010: String? = null
    @Volatile var primeGoPort50020: String? = null
    @Volatile var primeGoPort50021: String? = null
    @Volatile var primeGoGrpcLibraryProbe: String? = null
    @Volatile var primeGoHighPortScan: String? = null
    @Volatile var primeGoHighPortScanProgress: String? = null
    @Volatile var primeGoHighPortGrpcProbes: String? = null

    // Last payloads for inspection
    var lastDiscoveryRxPayload: ByteArray? = null
    var lastDiscoveryTxPayload: ByteArray? = null
    var lastClientIp: String? = null

    // RPC method names are split so internal self-tests never masquerade as hardware traffic.
    val observedRpcMethods = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    val observedExternalRpcMethods = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    val observedLocalSelfTestRpcMethods = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun recordRawTcpAccept(remoteIp: String?, local: Boolean) {
        rawTcpAccepts.incrementAndGet()
        if (local) {
            localSelfTestRawTcpAccepts.incrementAndGet()
        } else {
            externalRawTcpAccepts.incrementAndGet()
            lastClientContact = System.currentTimeMillis()
            if (!remoteIp.isNullOrBlank()) lastClientIp = remoteIp
        }
    }

    fun recordHttpAccept(remoteIp: String?, local: Boolean) {
        httpAcceptedConnections.incrementAndGet()
        if (local) {
            localSelfTestHttpAcceptedConnections.incrementAndGet()
        } else {
            externalHttpAcceptedConnections.incrementAndGet()
        }
        if (!remoteIp.isNullOrBlank()) lastHttpClientIp = remoteIp
    }

    fun recordHttpRequest(method: String, target: String, remoteIp: String?, local: Boolean) {
        httpRequests.incrementAndGet()
        lastHttpRequest = "$method $target"
        if (local) {
            localSelfTestHttpRequests.incrementAndGet()
        } else {
            externalHttpRequests.incrementAndGet()
            lastClientContact = System.currentTimeMillis()
            if (!remoteIp.isNullOrBlank()) lastHttpClientIp = remoteIp
        }
    }

    fun recordHttpRequestParseFailure(remoteIp: String?, local: Boolean) {
        httpRequestParseFailures.incrementAndGet()
        if (!local && !remoteIp.isNullOrBlank()) lastHttpClientIp = remoteIp
    }

    fun recordHttpStatus(status: Int) {
        lastHttpStatus = status.toString()
    }

    fun recordGrpcTransport(remoteIp: String?, local: Boolean) {
        connectionsOpened.incrementAndGet()
        if (local) {
            localSelfTestConnections.incrementAndGet()
        } else {
            externalConnectionsOpened.incrementAndGet()
            lastClientContact = System.currentTimeMillis()
            if (!remoteIp.isNullOrBlank()) lastClientIp = remoteIp
        }
    }

    fun recordGrpcRpc(method: String, remoteIp: String?, local: Boolean) {
        rpcCount.incrementAndGet()
        observedRpcMethods.add(method)
        if (local) {
            localSelfTestRpcCount.incrementAndGet()
            observedLocalSelfTestRpcMethods.add(method)
        } else {
            externalRpcCount.incrementAndGet()
            observedExternalRpcMethods.add(method)
            lastClientContact = System.currentTimeMillis()
            if (!remoteIp.isNullOrBlank()) lastClientIp = remoteIp
        }
    }

    fun recordTrustMessage(localSelfTest: Boolean) {
        trustMessages.incrementAndGet()
        if (localSelfTest) {
            localSelfTestTrustMessages.incrementAndGet()
        } else {
            externalTrustMessages.incrementAndGet()
            lastTrustRequest = System.currentTimeMillis()
        }
    }

    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    )

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message, throwable)
        scope.launch { logChannel.send(entry) }
        when (level) {
            LogLevel.DEBUG -> Timber.tag(tag).d(throwable, message)
            LogLevel.INFO -> Timber.tag(tag).i(throwable, message)
            LogLevel.WARN -> Timber.tag(tag).w(throwable, message)
            LogLevel.ERROR -> Timber.tag(tag).e(throwable, message)
        }
    }

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun error(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)

    fun getLogEntries(): List<LogEntry> = logBuffer.toList()

    fun clearLogs() { logBuffer.clear() }

    fun generateDiagnosticReport(): String {
        val sb = StringBuilder()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        sb.append("=== 2MIMICDJ2 Diagnostic Report ===\n")
        sb.append("Generated: ${fmt.format(Date())}\n\n")

        sb.append("--- Discovery ---\n")
        sb.append("RX: ${discoveryRxCount.get()} | TX: ${discoveryTxCount.get()}\n")
        sb.append("Last RX: ${if (lastDiscoveryRx > 0) fmt.format(Date(lastDiscoveryRx)) else "never"}\n")
        sb.append("Last TX: ${if (lastDiscoveryTx > 0) fmt.format(Date(lastDiscoveryTx)) else "never"}\n")
        sb.append("Last RX Payload: ${lastDiscoveryRxPayload?.let { bytesToHex(it) } ?: "none"}\n")
        sb.append("Last TX Payload: ${lastDiscoveryTxPayload?.let { bytesToHex(it) } ?: "none"}\n\n")

        sb.append("--- Sessions ---\n")
        sb.append("gRPC connections total: " + connectionsOpened.get() + " | external: " + externalConnectionsOpened.get() + " | local self-test: " + localSelfTestConnections.get() + "\n")
        sb.append("Raw TCP accepts total: " + rawTcpAccepts.get() + " | external: " + externalRawTcpAccepts.get() + " | local self-test: " + localSelfTestRawTcpAccepts.get() + "\n")
        sb.append("Trust Msgs total: " + trustMessages.get() + " | external: " + externalTrustMessages.get() + " | local self-test: " + localSelfTestTrustMessages.get() + "\n")
        sb.append("RPC Calls total: " + rpcCount.get() + " | external: " + externalRpcCount.get() + " | local self-test: " + localSelfTestRpcCount.get() + "\n")
        sb.append("External RPC methods: " + observedExternalRpcMethods.joinToString(", ") { if (it.isEmpty()) "none" else it } + "\n")
        sb.append("Local self-test RPC methods: " + observedLocalSelfTestRpcMethods.joinToString(", ") { if (it.isEmpty()) "none" else it } + "\n")
        sb.append("Last Trust: ${if (lastTrustRequest > 0) fmt.format(Date(lastTrustRequest)) else "never"}\n")
        sb.append("Last Library: ${if (lastLibraryRequest > 0) fmt.format(Date(lastLibraryRequest)) else "never"}\n")
        sb.append("Last Track: ${if (lastTrackRequest > 0) fmt.format(Date(lastTrackRequest)) else "never"}\n")
        sb.append("Last Client IP: ${lastClientIp ?: "none"}\n")
        sb.append("Last Contact: ${if (lastClientContact > 0) fmt.format(Date(lastClientContact)) else "never"}\n\n")
        sb.append("Phone -> Prime GO TCP/50010: ${primeGoPort50010 ?: "not probed"}\n")
        sb.append("Phone -> Prime GO TCP/50020: ${primeGoPort50020 ?: "not probed"}\n")
        sb.append("Phone -> Prime GO TCP/50021: ${primeGoPort50021 ?: "not probed"}\n")
        sb.append("Prime GO gRPC/50010 GetLibraries: ${primeGoGrpcLibraryProbe ?: "not probed"}\n")
        sb.append("Prime GO high-port scan: ${primeGoHighPortScan ?: "not started"}\n")
        sb.append("Prime GO high-port scan progress: ${primeGoHighPortScanProgress ?: "not started"}\n")
        sb.append("Prime GO high-port gRPC probes: ${primeGoHighPortGrpcProbes ?: "none"}\n\n")

        sb.append("--- HTTP Listener ---\n")
        sb.append("Bind target: ${httpListenerAddress ?: "unknown"}:${httpListenerPort}\n")
        sb.append("Readiness: ${httpListenerReadiness ?: "not tested"}\n")
        sb.append(
            "Accepted TCP connections total: ${httpAcceptedConnections.get()} | " +
                "external: ${externalHttpAcceptedConnections.get()} | " +
                "local self-test: ${localSelfTestHttpAcceptedConnections.get()}\n"
        )
        sb.append(
            "Parsed HTTP requests total: ${httpRequests.get()} | " +
                "external: ${externalHttpRequests.get()} | " +
                "local self-test: ${localSelfTestHttpRequests.get()}\n"
        )
        sb.append("Incomplete/empty HTTP requests: ${httpRequestParseFailures.get()}\n")
        sb.append("Last HTTP client IP: ${lastHttpClientIp ?: "none"}\n")
        sb.append("Last HTTP request: ${lastHttpRequest ?: "none"}\n")
        sb.append("Last HTTP response status: ${lastHttpStatus ?: "none"}\n\n")

        sb.append("--- File Server ---\n")
        sb.append("Requests: ${fileRequests.get()}\n")
        sb.append("Bytes: ${bytesServed.get()}\n")
        sb.append("Ranges: ${rangeRequests.get()}\n")
        sb.append("404: ${errors404.get()} | 416: ${errors416.get()} | 500: ${errors500.get()}\n")
        sb.append("Open Failures: ${openFileFailures.get()}\n")
        sb.append("Last Transfer: ${if (lastFileTransfer > 0) fmt.format(Date(lastFileTransfer)) else "never"}\n\n")

        sb.append("--- Recent Logs (last 50) ---\n")
        logBuffer.takeLast(50).forEach { entry ->
            sb.append("[${fmt.format(Date(entry.timestamp))}] ${entry.level} ${entry.tag}: ${entry.message}\n")
            entry.throwable?.let { t ->
                val writer = StringWriter()
                t.printStackTrace(java.io.PrintWriter(writer))
                sb.append(writer.toString())
            }
        }

        return sb.toString()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }

    init {
        scope.launch {
            for (entry in logChannel) {
                logBuffer.add(entry)
                if (logBuffer.size > maxLogEntries) logBuffer.removeAt(0)
            }
        }
    }

    fun shutdown() {
        logChannel.close()
        scope.cancel()
    }

    companion object {
        private const val TAG = "ServerDiagnostics"
    }
}