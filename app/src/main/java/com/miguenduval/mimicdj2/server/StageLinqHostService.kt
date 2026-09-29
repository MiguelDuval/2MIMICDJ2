package com.miguenduval.mimicdj2.server

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Minimal host-side StageLinQ discovery + main directory.
 *
 * Prime GO has been publicly observed announcing DISCOVERER_HOWDY_ on UDP
 * 51337, then connecting to the TCP port announced in that packet. This
 * implementation is diagnostic-first and deliberately independent from EAAS.
 */
class StageLinqHostService(
    private val diagnostics: ServerDiagnostics,
    private val executor: Executor
) {
    companion object {
        const val DISCOVERY_PORT = 51337
        private const val ANNOUNCE_INTERVAL_MS = 500L
        private const val SOURCE = "Mimic DJ"
        private const val ACTION_HOWDY = "DISCOVERER_HOWDY_"
        private const val SOFTWARE_NAME = "Mimic DJ"
        private const val SOFTWARE_VERSION = "1.0.0"
    }

    private val running = AtomicBoolean(false)
    private var scope: CoroutineScope? = null
    private var discoveryListener: DatagramSocket? = null
    private var directoryServer: ServerSocket? = null
    private val serviceServers = mutableListOf<Pair<String, ServerSocket>>()
    @Volatile private var directoryPort = 0
    private val token = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }

    fun start() {
        if (!running.compareAndSet(false, true)) return

        try {
            startDiscoveryListener()
            directoryServer = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("0.0.0.0", 0), 32)
            }
            directoryPort = directoryServer!!.localPort
            diagnostics.stageLinqDirectoryPort = directoryPort
            diagnostics.info("StageLinQ", "StageLinQ directory listening on 0.0.0.0:" + directoryPort)

            for (serviceName in listOf("StateMap", "BeatInfo", "FileTransfer")) {
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", 0), 16)
                }
                serviceServers += serviceName to server
                diagnostics.info(
                    "StageLinQ",
                    "StageLinQ service " + serviceName + " listening on 0.0.0.0:" + server.localPort
                )
                executor.execute { serviceAcceptLoop(serviceName, server) }
            }

            executor.execute { directoryAcceptLoop() }

            val localScope = CoroutineScope(Dispatchers.IO + Job())
            scope = localScope
            localScope.launch {
                while (isActive && running.get()) {
                    broadcast()
                    delay(ANNOUNCE_INTERVAL_MS)
                }
            }

            diagnostics.info("StageLinQ", "StageLinQ host discovery started on UDP " + DISCOVERY_PORT)
        } catch (t: Throwable) {
            stop()
            throw t
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { scope?.cancel() }
        runCatching { discoveryListener?.close() }
        runCatching { directoryServer?.close() }
        serviceServers.forEach { runCatching { it.second.close() } }
        serviceServers.clear()
        directoryPort = 0
        diagnostics.stageLinqDirectoryPort = 0
        diagnostics.info("StageLinQ", "StageLinQ host stopped")
    }

    private fun startDiscoveryListener() {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(DISCOVERY_PORT))
            soTimeout = 1000
        }
        discoveryListener = socket

        executor.execute {
            val buffer = ByteArray(4096)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val payload = buffer.copyOf(packet.length)
                    val remote = packet.socketAddress as? InetSocketAddress
                    if (payload.size >= 4 &&
                        payload[0] == 'a'.code.toByte() &&
                        payload[1] == 'i'.code.toByte() &&
                        payload[2] == 'r'.code.toByte() &&
                        payload[3] == 'D'.code.toByte()
                    ) {
                        diagnostics.stageLinqDiscoveryRxCount.incrementAndGet()
                        diagnostics.lastStageLinqDiscoveryRx = System.currentTimeMillis()
                        diagnostics.lastStageLinqDiscoveryPayload = payload
                        diagnostics.lastStageLinqClientIp = remote?.address?.hostAddress
                        diagnostics.info(
                            "StageLinQ",
                            "StageLinQ discovery RX from " + remote + ": " + bytesToHex(payload)
                        )
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Periodic wake-up for shutdown.
                } catch (t: Throwable) {
                    if (running.get()) {
                        diagnostics.error("StageLinQ", "StageLinQ discovery RX error", t)
                    }
                }
            }
        }
    }

    private fun directoryAcceptLoop() {
        while (running.get()) {
            try {
                val socket = directoryServer!!.accept()
                diagnostics.stageLinqDirectoryAccepts.incrementAndGet()
                val remote = socket.remoteSocketAddress as? InetSocketAddress
                diagnostics.lastStageLinqClientIp = remote?.address?.hostAddress
                diagnostics.info("StageLinQ", "StageLinQ directory accept from " + remote)
                handleDirectory(socket)
            } catch (t: Throwable) {
                if (running.get()) diagnostics.error("StageLinQ", "StageLinQ directory accept error", t)
            }
        }
    }

    private fun serviceAcceptLoop(serviceName: String, server: ServerSocket) {
        while (running.get()) {
            try {
                val socket = server.accept()
                diagnostics.stageLinqServiceAccepts.incrementAndGet()
                val remote = socket.remoteSocketAddress as? InetSocketAddress
                diagnostics.lastStageLinqClientIp = remote?.address?.hostAddress
                diagnostics.info("StageLinQ", "StageLinQ " + serviceName + " accept from " + remote)
                socket.soTimeout = 2500
                socket.use {
                    val input = BufferedInputStream(it.getInputStream())
                    val sample = ByteArray(256)
                    val n = runCatching { input.read(sample) }.getOrDefault(-1)
                    if (n > 0) {
                        val payload = sample.copyOf(n)
                        diagnostics.lastStageLinqServicePayload = payload
                        diagnostics.info(
                            "StageLinQ",
                            "StageLinQ " + serviceName + " RX " + bytesToHex(payload)
                        )
                    }
                }
            } catch (t: Throwable) {
                if (running.get()) diagnostics.error("StageLinQ", "StageLinQ " + serviceName + " accept error", t)
            }
        }
    }

    private fun handleDirectory(socket: Socket) {
        socket.use {
            it.soTimeout = 2500
            val input = BufferedInputStream(it.getInputStream())
            val first = ByteArray(256)
            val n = runCatching { input.read(first) }.getOrDefault(-1)
            if (n <= 0) return

            val payload = first.copyOf(n)
            diagnostics.stageLinqDirectoryRxCount.incrementAndGet()
            diagnostics.lastStageLinqDirectoryPayload = payload
            diagnostics.info("StageLinQ", "StageLinQ directory RX " + bytesToHex(payload))

            if (n >= 20 &&
                payload[0] == 0.toByte() &&
                payload[1] == 0.toByte() &&
                payload[2] == 0.toByte() &&
                payload[3] == 2.toByte()
            ) {
                val response = ByteArrayOutputStream()
                for ((serviceName, server) in serviceServers) {
                    response.write(buildServiceAnnouncement(token, serviceName, server.localPort))
                }
                val bytes = response.toByteArray()
                it.getOutputStream().apply {
                    write(bytes)
                    flush()
                }
                diagnostics.stageLinqDirectoryTxCount.incrementAndGet()
                diagnostics.lastStageLinqDirectoryTxPayload = bytes
                diagnostics.info("StageLinQ", "StageLinQ directory TX " + bytesToHex(bytes))
            }
        }
    }

    private fun broadcast() {
        val payload = buildDiscovery(token, SOURCE, ACTION_HOWDY, SOFTWARE_NAME, SOFTWARE_VERSION, directoryPort)
        val targets = broadcastTargets()
        if (targets.isEmpty()) return

        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (target in targets) {
                    socket.send(
                        DatagramPacket(
                            payload,
                            payload.size,
                            java.net.InetAddress.getByName(target),
                            DISCOVERY_PORT
                        )
                    )
                    diagnostics.stageLinqDiscoveryTxCount.incrementAndGet()
                    diagnostics.lastStageLinqDiscoveryTx = System.currentTimeMillis()
                }
            }
        }.onFailure {
            diagnostics.error("StageLinQ", "StageLinQ discovery broadcast failed", it)
        }
    }

    private fun buildDiscovery(
        token: ByteArray,
        source: String,
        action: String,
        softwareName: String,
        softwareVersion: String,
        port: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('a'.code.toByte(), 'i'.code.toByte(), 'r'.code.toByte(), 'D'.code.toByte()))
        out.write(token)
        writeUtf16(out, source)
        writeUtf16(out, action)
        writeUtf16(out, softwareName)
        writeUtf16(out, softwareVersion)
        out.write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(port.toShort()).array())
        return out.toByteArray()
    }

    private fun buildServiceAnnouncement(token: ByteArray, serviceName: String, servicePort: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0, 0))
        out.write(token)
        writeUtf16(out, serviceName)
        out.write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(servicePort.toShort()).array())
        return out.toByteArray()
    }

    private fun writeUtf16(out: ByteArrayOutputStream, value: String) {
        val data = value.toByteArray(Charsets.UTF_16BE)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array())
        out.write(data)
    }

    private fun broadcastTargets(): List<String> {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        val targets = linkedSetOf<String>()
        for (network in interfaces) {
            if (!runCatching { network.isUp }.getOrDefault(false) || network.isLoopback || network.isVirtual) continue
            for (address in network.interfaceAddresses) {
                val inet = address.address as? Inet4Address ?: continue
                if (inet.isLoopbackAddress || inet.isLinkLocalAddress) continue
                val broadcast = address.broadcast as? Inet4Address
                if (broadcast != null) targets += broadcast.hostAddress
            }
        }
        if (targets.isEmpty()) targets += "255.255.255.255"
        return targets.toList()
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { String.format("%02X", it) }
}
