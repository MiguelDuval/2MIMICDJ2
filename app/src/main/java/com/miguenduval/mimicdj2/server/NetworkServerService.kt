package com.miguenduval.mimicdj2.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import com.miguenduval.mimicdj2.BuildConfig
import com.miguenduval.mimicdj2.network.EaasDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import io.grpc.Attributes
import io.grpc.Grpc
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerTransportFilter
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class NetworkServerService : Service() {
    private val diagnostics = ServerDiagnostics()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var grpcServer: Server? = null
    private var udpListener: java.net.DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private lateinit var eaasToken: ByteArray
    private val executor = Executors.newCachedThreadPool()
    private var boundPort = 0
    private var boundInterface: String? = null

    // Binder for service binding
    inner class LocalBinder : Binder() {
        fun getService(): NetworkServerService = this@NetworkServerService
    }

    private val binder = LocalBinder()

    companion object {
        private const val TAG = "NetworkServerService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "network_server_channel"
        const val ACTION_START_SERVER = "com.miguenduval.mimicdj2.START_SERVER"
        const val ACTION_STOP_SERVER = "com.miguenduval.mimicdj2.STOP_SERVER"
        const val EXTRA_PORT = "port"
        const val EXTRA_INTERFACE = "interface"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        eaasToken = com.miguenduval.mimicdj2.network.EaasDiscovery.newToken()
        diagnostics.info(TAG, "Service created")
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_SERVER -> {
                val port = intent.getIntExtra(EXTRA_PORT, 50010)
                val iface = intent.getStringExtra(EXTRA_INTERFACE)
                startServer(port, iface)
            }
            ACTION_STOP_SERVER -> stopServer()
            null -> diagnostics.warn(TAG, "Received null intent")
        }
        return START_STICKY
    }

    private fun startServer(port: Int, iface: String?) {
        if (grpcServer != null) {
            diagnostics.warn(TAG, "Server already running on port $boundPort")
            return
        }
        scope.launch { doStartServer(port, iface) }
    }

    private fun doStartServer(port: Int, iface: String?) {
        try {
            val bindHost = if (iface.isNullOrBlank()) "0.0.0.0" else iface

            grpcServer = NettyServerBuilder
                .forPort(port)
                .executor(executor)
                .addService(NetworkTrustGrpcService(diagnostics))
                .addInterceptor(RpcDiagnosticsInterceptor(diagnostics))
                .addTransportFilter(GrpcTransportDiagnosticsFilter(diagnostics))
                .build()
                .start()

            boundPort = port
            boundInterface = bindHost
            diagnostics.info(TAG, "EAAS gRPC server listening on $bindHost:$port")
            startEaasDiscoveryListener()
            updateNotification("EAAS gRPC + discovery running on $bindHost:$port")
        } catch (e: Exception) {
            diagnostics.error(TAG, "Failed to start EAAS gRPC server", e)
            try { grpcServer?.shutdownNow() } catch (_: Exception) {}
            grpcServer = null
            boundPort = 0
            boundInterface = null
            stopSelf()
        }
    }
    private fun startEaasDiscoveryListener() {
        scope.launch {
            try {
                val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifi?.createMulticastLock("2MIMICDJ2-EAAS")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }

                udpListener = java.net.DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(EaasDiscovery.PORT))
                    soTimeout = 1000
                }

                diagnostics.info(TAG, "EAAS discovery listener on UDP ${EaasDiscovery.PORT}")
                val buffer = ByteArray(2048)

                while (udpListener?.isClosed == false) {
                    try {
                        val packet = java.net.DatagramPacket(buffer, buffer.size)
                        udpListener?.receive(packet)

                        val length = packet.length
                        val data = buffer.copyOf(length)
                        val sender = packet.socketAddress as? InetSocketAddress
                        val senderIp = sender?.address?.hostAddress

                        diagnostics.discoveryRxCount.incrementAndGet()
                        diagnostics.lastDiscoveryRx = System.currentTimeMillis()
                        diagnostics.lastDiscoveryRxPayload = data
                        diagnostics.lastClientIp = senderIp
                        diagnostics.info(TAG, "EAAS discovery RX from $senderIp:${sender?.port}: ${bytesToHex(data)}")

                        if (!EaasDiscovery.isDiscoveryRequest(data)) {
                            diagnostics.debug(TAG, "Ignoring non-EAAS discovery packet")
                            continue
                        }

                        val responseHost = resolveLocalIPv4For(sender?.address)
                        val response = EaasDiscovery.buildResponse(
                            token = eaasToken,
                            hostname = "Mimic DJ",
                            grpcHost = responseHost,
                            grpcPort = 50010,
                            softwareVersion = BuildConfig.VERSION_NAME,
                            extra = "_"
                        )

                        sender?.let {
                            val reply = java.net.DatagramPacket(
                                response,
                                response.size,
                                it.address,
                                it.port
                            )
                            udpListener?.send(reply)
                            diagnostics.discoveryTxCount.incrementAndGet()
                            diagnostics.lastDiscoveryTx = System.currentTimeMillis()
                            diagnostics.lastDiscoveryTxPayload = response
                            diagnostics.info(TAG, "EAAS discovery TX to ${it.address.hostAddress}:${it.port}: ${bytesToHex(response)}")
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Periodic wake-up lets coroutine observe service shutdown.
                    } catch (e: Exception) {
                        if (udpListener?.isClosed == false) {
                            diagnostics.error(TAG, "EAAS discovery receive error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                diagnostics.error(TAG, "Failed to start EAAS discovery listener on UDP ${EaasDiscovery.PORT}", e)
            }
        }
    }

    private fun resolveLocalIPv4For(peer: InetAddress?): String {
        if (peer != null) {
            runCatching {
                java.net.DatagramSocket().use { probe ->
                    probe.connect(peer, 9)
                    val local = probe.localAddress
                    if (local is java.net.Inet4Address && !local.isLoopbackAddress && !local.isLinkLocalAddress) {
                        return local.hostAddress
                    }
                }
            }
        }

        return java.net.NetworkInterface.getNetworkInterfaces()?.toList()
            ?.asSequence()
            ?.filter { runCatching { it.isUp }.getOrDefault(false) }
            ?.filterNot { it.isLoopback || it.isVirtual }
            ?.flatMap { it.inetAddresses.toList().asSequence() }
            ?.filterIsInstance<java.net.Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
            ?: "0.0.0.0"
    }

    private fun stopServer() {
        diagnostics.info(TAG, "Stopping server")
        try { grpcServer?.shutdownNow() } catch (e: Exception) { diagnostics.error(TAG, "Error shutting down gRPC", e) }
        grpcServer = null
        try { udpListener?.close(); udpListener = null } catch (e: Exception) { diagnostics.error(TAG, "Error closing UDP", e) }
        try {
            multicastLock?.let { if (it.isHeld) it.release() }
            multicastLock = null
        } catch (e: Exception) { diagnostics.warn(TAG, "Error releasing Wi-Fi multicast lock: $e") }
        boundPort = 0
        boundInterface = null
        updateNotification("Server stopped")
        stopForeground(true)
        stopSelf()
    }
    private fun updateNotification(content: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("2MIMICDJ2 Network Server")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Network Server", NotificationManager.IMPORTANCE_LOW).apply { description = "2MIMICDJ2 network server status" }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString(" ") { String.format("%02X", it) }

    override fun onDestroy() {
        diagnostics.info(TAG, "Service destroyed")
        try { grpcServer?.shutdownNow() } catch (_: Exception) {}
        try { udpListener?.close() } catch (_: Exception) {}
        try { multicastLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        grpcServer = null
        udpListener = null
        multicastLock = null
        boundPort = 0
        boundInterface = null
        diagnostics.shutdown()
        executor.shutdown()
        scope.cancel()
        super.onDestroy()
    }
    fun getDiagnostics(): ServerDiagnostics = diagnostics
    fun getBoundPort(): Int = boundPort
    fun getBoundInterface(): String? = boundInterface
}
private class RpcDiagnosticsInterceptor(
    private val diagnostics: ServerDiagnostics
) : ServerInterceptor {
    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val method = call.methodDescriptor.fullMethodName
        diagnostics.rpcCount.incrementAndGet()
        diagnostics.observedRpcMethods.add(method)
        diagnostics.lastClientContact = System.currentTimeMillis()
        val remote = call.attributes.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR)
        diagnostics.lastClientIp = (remote as? InetSocketAddress)?.address?.hostAddress ?: remote?.toString()
        diagnostics.info("EAAS-RPC", "RPC $method from ${diagnostics.lastClientIp ?: "unknown"}")
        return next.startCall(call, headers)
    }
}

private class GrpcTransportDiagnosticsFilter(
    private val diagnostics: ServerDiagnostics
) : ServerTransportFilter() {
    override fun transportReady(transportAttrs: Attributes): Attributes {
        diagnostics.connectionsOpened.incrementAndGet()
        diagnostics.lastClientContact = System.currentTimeMillis()
        val remote = transportAttrs.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR)
        diagnostics.lastClientIp = (remote as? InetSocketAddress)?.address?.hostAddress ?: remote?.toString()
        diagnostics.info("EAAS-Transport", "gRPC transport ready from ${diagnostics.lastClientIp ?: remote ?: "unknown"}")
        return transportAttrs
    }

    override fun transportTerminated(transportAttrs: Attributes) {
        val remote = transportAttrs.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR)
        diagnostics.info("EAAS-Transport", "gRPC transport terminated from ${remote ?: "unknown"}")
    }
}