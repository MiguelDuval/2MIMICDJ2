package com.miguenduval.mimicdj2.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.system.Os
import android.system.OsConstants
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
import io.grpc.InsecureServerCredentials
import io.grpc.okhttp.OkHttpServerBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class NetworkServerService : Service() {
    private val diagnostics = ServerDiagnostics()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var grpcServer: Server? = null
    private var httpServerFd: FileDescriptor? = null
    @Volatile private var httpBoundPort = 0
    @Volatile private var httpBindError: String? = null
    private var udpListener: java.net.DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private lateinit var eaasToken: ByteArray
    private lateinit var mediaLibrary: MediaLibrary
    private lateinit var httpFileServer: EaasHttpFileServer
    private val executor = Executors.newCachedThreadPool()
    private var boundPort = 0
    private var boundInterface: String? = null
    private val lifecycleLock = Any()
    @Volatile private var serverState = ServerState.STOPPED
    @Volatile private var primeGoPortsProbed = false
    @Volatile private var primeGoHighPortScanStarted = false

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
        mediaLibrary = MediaLibrary(this)
        httpFileServer = EaasHttpFileServer(mediaLibrary, diagnostics)
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
        synchronized(lifecycleLock) {
            when (serverState) {
                ServerState.RUNNING, ServerState.STARTING -> {
                    diagnostics.warn(TAG, "Ignoring duplicate start; state=$serverState")
                    return
                }
                ServerState.STOPPING -> {
                    diagnostics.warn(TAG, "Ignoring start while server is stopping")
                    return
                }
                ServerState.STOPPED -> {
                    serverState = ServerState.STARTING
                }
            }
        }
        // Foreground promotion is useful for persistence/visibility, but it must
        // never prevent the LAN server itself from binding while the Activity is open.
        runCatching { updateNotification("Starting EAAS server…") }
            .onFailure { diagnostics.error(TAG, "Foreground promotion failed; continuing with LAN server startup", it) }
        scope.launch { doStartServer(port, iface) }
    }

    private fun probeLocalGrpcTcp(host: String, port: Int) {
        runCatching {
            java.net.Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1200)
            }
            diagnostics.info(TAG, "Phone self-test TCP/$port: OK")
        }.onFailure { e ->
            diagnostics.error(TAG, "Phone self-test TCP/$port failed", e)
        }
    }

    private fun doStartServer(port: Int, iface: String?) {
        StartupFailureGuard.run(
            onFailure = { throwable -> handleStartupFailure(throwable) }
        ) {
            doStartServerUnsafe(port, iface)
        }
    }

    private fun doStartServerUnsafe(port: Int, iface: String?) {
        // Resolve the concrete LAN address. Android/vendor builds may reject
        // wildcard listener binds with EPERM even when the interface address
        // itself is allowed.
        val lanIp = if (iface.isNullOrBlank()) {
            NetworkAddress.currentLanIpv4(applicationContext)
        } else iface
        val advertisedHost = lanIp?.takeUnless { it.isBlank() }

        if (advertisedHost.isNullOrBlank()) {
            throw IllegalStateException("No concrete LAN IPv4 address available for gRPC bind")
        }

        // Prime GO/Engine OS normally expects EAAS gRPC on 50010. Some Android
        // vendor kernels can deny that fixed listener even for an unprivileged
        // app, so keep the standard port as the first choice and automatically
        // fall back to an ephemeral high port when 50010 is rejected.
        val grpcCandidates = buildList {
            add(port)
            if (port != 50100) add(50100)
            if (port != 60000) add(60000)
            add(0) // OS-assigned high port: avoids fixed-port reservations.
        }

        var lastFailure: Throwable? = null

        for (candidatePort in grpcCandidates) {
            var localServer: Server? = null
            try {
                val candidateLabel = if (candidatePort == 0) "ephemeral" else candidatePort.toString()
                diagnostics.info(
                    TAG,
                    "Trying gRPC bind $advertisedHost:$candidateLabel"
                )

                localServer = OkHttpServerBuilder
                    .forPort(
                        candidatePort,
                        InsecureServerCredentials.create()
                    )
                    .socketFactory(GrpcServerSocketFactory(diagnostics, advertisedHost))
                    .executor(executor)
                    .addService(NetworkTrustGrpcService(diagnostics))
                    .addService(
                        EngineLibraryGrpcService(mediaLibrary) {
                            NetworkAddress.currentLanIpv4(applicationContext) ?: advertisedHost
                        }
                    )
                    .addService(MimicEngineSyncService())
                    .intercept(RpcDiagnosticsInterceptor(diagnostics))
                    .addTransportFilter(GrpcTransportDiagnosticsFilter(diagnostics))
                    .build()
                    .start()

                val actualPort = localServer.port
                if (actualPort <= 0) {
                    throw IllegalStateException("gRPC server returned invalid bound port $actualPort")
                }

                synchronized(lifecycleLock) {
                    if (serverState != ServerState.STARTING) {
                        try { localServer.shutdownNow() } catch (_: Exception) {}
                        diagnostics.warn(TAG, "Discarding late server start; state=$serverState")
                        return
                    }
                    grpcServer = localServer
                    boundPort = actualPort
                    boundInterface = advertisedHost
                    serverState = ServerState.RUNNING
                }

                diagnostics.info(
                    TAG,
                    "EAAS gRPC server listening on $advertisedHost:$actualPort" +
                        if (candidatePort == 0) " (ephemeral fallback)" else ""
                )
                if (candidatePort != port) {
                    diagnostics.warn(
                        TAG,
                        "Standard gRPC port $port unavailable; using fallback $actualPort"
                    )
                }

                val httpPort = pairedHttpPort(actualPort)
                startHttpServer(httpPort)

                // Do not announce the EAAS device until both paired endpoints are
                // actually listening. Engine derives HTTP as grpcPort + 10.
                scope.launch { probeLocalGrpcTcp(advertisedHost, actualPort) }
                startEaasDiscoveryListener()
                updateNotification(
                    "EAAS gRPC + HTTP + discovery on " +
                        "$advertisedHost:$actualPort / HTTP:$httpPort"
                )
                return
            } catch (t: Throwable) {
                lastFailure = t
                diagnostics.error(
                    TAG,
                    "gRPC bind $advertisedHost:$candidatePort failed",
                    t
                )
                runCatching { localServer?.shutdownNow() }
            }
        }

        throw lastFailure ?: IllegalStateException("No usable gRPC listener port")
    }

    private fun pairedHttpPort(grpcPort: Int): Int {
        require(grpcPort in 1..65525) {
            "gRPC port $grpcPort cannot derive EAAS HTTP port (grpcPort + 10)"
        }
        return grpcPort + 10
    }

    private fun startHttpServer(port: Int) {
        val fd = try {
            // Use the native Android socket API directly. This avoids the Java
            // ServerSocket path and lets the service bind the exact EAAS pair.
            Os.socket(
                OsConstants.AF_INET,
                OsConstants.SOCK_STREAM,
                OsConstants.IPPROTO_TCP
            )
        } catch (t: Throwable) {
            httpBindError = t.javaClass.simpleName + ": " + (t.message ?: "no message")
            diagnostics.error(TAG, "Failed to create native EAAS HTTP socket on $port", t)
            diagnoseTcpBindFailure(port)
            throw IllegalStateException(
                "Native HTTP socket creation failed on $port: " +
                    t.javaClass.simpleName + ": " + (t.message ?: "no message"),
                t
            )
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Os.setsockoptInt(
                    fd,
                    OsConstants.SOL_SOCKET,
                    OsConstants.SO_REUSEADDR,
                    1
                )
            }

            // EAAS HTTP is paired with gRPC at grpcPort + 10. With the
            // Android gRPC fallback 50100, the correct HTTP port is 50110.
            val lanHost = NetworkAddress.currentLanIpv4(applicationContext)
            val bindHost = lanHost ?: "0.0.0.0"
            try {
                Os.bind(fd, InetAddress.getByName(bindHost), port)
            } catch (first: Throwable) {
                if (bindHost != "0.0.0.0") {
                    diagnostics.warn(
                        TAG,
                        "HTTP $port bind on $bindHost failed; retrying wildcard: " +
                            first.javaClass.simpleName + ": " + (first.message ?: "no message")
                    )
                    Os.bind(fd, InetAddress.getByName("0.0.0.0"), port)
                } else {
                    throw first
                }
            }
            Os.listen(fd, 64)
        } catch (t: Throwable) {
            runCatching { Os.close(fd) }
            httpBindError = t.javaClass.simpleName + ": " + (t.message ?: "no message")
            diagnostics.error(TAG, "Failed to start native EAAS HTTP server on $port", t)
            diagnoseTcpBindFailure(port)
            throw IllegalStateException(
                "Native HTTP $port bind failed: " +
                    t.javaClass.simpleName + ": " + (t.message ?: "no message"),
                t
            )
        }

        httpServerFd = fd
        httpBoundPort = port
        httpBindError = null
        diagnostics.info(TAG, "EAAS HTTP native server listening on $port (gRPC=$boundPort)")

        scope.launch {
            while (httpServerFd != null) {
                try {
                    val clientFd = Os.accept(fd, null)
                    executor.execute { handleHttpClient(clientFd) }
                } catch (t: Throwable) {
                    if (httpServerFd != null) {
                        diagnostics.error(TAG, "HTTP native accept error", t)
                    }
                }
            }
        }
    }

    private fun diagnoseTcpBindFailure(targetPort: Int = 50020) {
        diagnostics.info(TAG, "Port $targetPort diagnostic matrix:\n${PortDiagnostics.snapshot(NetworkAddress.currentLanIpv4(applicationContext), targetPort)}")

        // A failed bind does not tell us whether the port is reserved-but-unused
        // or whether another system/vendor service already owns it. A connect
        // probe distinguishes those cases without requiring root.
        val host = NetworkAddress.currentLanIpv4(applicationContext)
        if (!host.isNullOrBlank()) {
            diagnostics.info(
                TAG,
                "TCP connect probes: $targetPort LAN=${probeTcpEndpoint(host, targetPort)} " +
                    "loopback=${probeTcpEndpoint("127.0.0.1", targetPort)}; " +
                    "${targetPort + 1} LAN=${probeTcpEndpoint(host, targetPort + 1)}; " +
                    "${targetPort + 2} LAN=${probeTcpEndpoint(host, targetPort + 2)}"
            )
        } else {
            diagnostics.warn(TAG, "TCP connect probes skipped: no LAN IPv4")
        }
    }

    private fun probeTcpEndpoint(host: String, port: Int): String =
        runCatching {
            java.net.Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 700)
            }
            "CONNECTED"
        }.fold(
            onSuccess = { it },
            onFailure = { error ->
                when (error) {
                    is java.net.ConnectException -> "REFUSED"
                    is java.net.SocketTimeoutException -> "TIMEOUT"
                    else -> error.javaClass.simpleName
                }
            }
        )
    private fun handleHttpClient(fd: FileDescriptor) {
        httpFileServer.handle(fd)
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

                        if (!primeGoPortsProbed) {
                            primeGoPortsProbed = true
                            scope.launch {
                                val peer = sender?.address?.hostAddress
                                if (!peer.isNullOrBlank()) {
                                    diagnostics.primeGoPort50010 = probeTcpEndpoint(peer, 50010)
                                    diagnostics.primeGoPort50020 = probeTcpEndpoint(peer, 50020)
                                    diagnostics.primeGoPort50021 = probeTcpEndpoint(peer, 50021)
                                    diagnostics.info(
                                        TAG,
                                        "Prime GO reverse TCP probes: 50010=${diagnostics.primeGoPort50010}, " +
                                            "50020=${diagnostics.primeGoPort50020}, 50021=${diagnostics.primeGoPort50021}"
                                    )
                                }
                            }
                        }

                        if (!primeGoHighPortScanStarted) {
                            primeGoHighPortScanStarted = true
                            scope.launch {
                                kotlinx.coroutines.delay(5000L)
                                if (diagnostics.rawTcpAccepts.get() > 0 ||
                                    diagnostics.connectionsOpened.get() > 0 ||
                                    diagnostics.rpcCount.get() > 0
                                ) {
                                    diagnostics.primeGoHighPortScan = "SKIPPED: server contact already observed"
                                    diagnostics.primeGoHighPortScanProgress = "not needed"
                                    return@launch
                                }

                                val peer = sender?.address?.hostAddress
                                if (!peer.isNullOrBlank()) {
                                    diagnostics.primeGoHighPortScanProgress = "STARTING"
                                    val result = PrimeGoPortScanner.scan(peer) { completed, total ->
                                        diagnostics.primeGoHighPortScanProgress = "$completed/$total"
                                    }
                                    diagnostics.primeGoHighPortScan = result.summary
                                    diagnostics.primeGoHighPortScanProgress =
                                        "${result.completed}/${result.total}"
                                    diagnostics.info(TAG, "Prime GO high-port scan: ${result.summary}")
                                }
                            }
                        }

                        val responseHost = resolveLocalIPv4For(sender?.address)
                        val response = EaasDiscovery.buildResponse(
                            token = eaasToken,
                            hostname = "Mimic DJ",
                            grpcHost = responseHost,
                            grpcPort = boundPort,
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
                        local.hostAddress?.let { return it }
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

    private fun handleStartupFailure(throwable: Throwable) {
        diagnostics.error(
            TAG,
            "EAAS startup failure (" + throwable.javaClass.name + "): " + (throwable.message ?: "no message"),
            throwable
        )

        try { grpcServer?.shutdownNow() } catch (t: Throwable) {
            diagnostics.error(TAG, "Error shutting down gRPC after startup failure", t)
        }
        grpcServer = null

        try {
            httpServerFd?.let { Os.close(it) }
        } catch (t: Throwable) {
            diagnostics.error(TAG, "Error closing HTTP after startup failure", t)
        }
        httpServerFd = null

        try { udpListener?.close() } catch (t: Throwable) {
            diagnostics.error(TAG, "Error closing discovery UDP after startup failure", t)
        }
        udpListener = null

        try {
            multicastLock?.let { if (it.isHeld) it.release() }
        } catch (t: Throwable) {
            diagnostics.error(TAG, "Error releasing multicast lock after startup failure", t)
        }
        multicastLock = null

        boundPort = 0
        boundInterface = null
        httpBoundPort = 0
        httpBindError = null
        primeGoPortsProbed = false
        primeGoHighPortScanStarted = false
        NetworkAddress.clearProcessBinding(applicationContext)

        synchronized(lifecycleLock) {
            serverState = ServerState.STOPPED
        }

        // Keep the service alive so the Activity can still retrieve the
        // original startup failure from diagnostics.
        try {
            updateNotification("Server start failed — copy diagnostics")
        } catch (t: Throwable) {
            diagnostics.error(TAG, "Failed to update failure notification", t)
        }
    }

    private fun stopServer() {
        synchronized(lifecycleLock) {
            if (serverState == ServerState.STOPPED || serverState == ServerState.STOPPING) {
                diagnostics.warn(TAG, "Ignoring duplicate stop; state=$serverState")
                return
            }
            serverState = ServerState.STOPPING
        }
        diagnostics.info(TAG, "Stopping server")
        try { grpcServer?.shutdownNow() } catch (e: Exception) { diagnostics.error(TAG, "Error shutting down gRPC", e) }
        grpcServer = null
        try {
            httpServerFd?.let { Os.close(it) }
            httpServerFd = null
        } catch (e: Exception) {
            diagnostics.error(TAG, "Error closing native HTTP", e)
        }
        try { udpListener?.close(); udpListener = null } catch (e: Exception) { diagnostics.error(TAG, "Error closing UDP", e) }
        try {
            multicastLock?.let { if (it.isHeld) it.release() }
            multicastLock = null
        } catch (e: Exception) { diagnostics.warn(TAG, "Error releasing Wi-Fi multicast lock: $e") }
        boundPort = 0
        boundInterface = null
        httpBoundPort = 0
        httpBindError = null
        primeGoPortsProbed = false
        synchronized(lifecycleLock) { serverState = ServerState.STOPPED }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
        try { httpServerFd?.let { Os.close(it) } } catch (_: Exception) {}
        try { udpListener?.close() } catch (_: Exception) {}
        try { multicastLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        grpcServer = null
        udpListener = null
        multicastLock = null
        boundPort = 0
        boundInterface = null
        httpBoundPort = 0
        httpBindError = null
        NetworkAddress.clearProcessBinding(applicationContext)
        diagnostics.shutdown()
        executor.shutdown()
        scope.cancel()
        super.onDestroy()
    }
    enum class ServerState { STOPPED, STARTING, RUNNING, STOPPING }

    /** Called by the bound Activity so Start/Stop do not depend on service-intent delivery timing. */
    fun requestStart(port: Int = 50010, iface: String? = null) = startServer(port, iface)

    /** Called by the bound Activity so Stop does not depend on a second startService delivery. */
    fun requestStop() = stopServer()

    fun getDiagnostics(): ServerDiagnostics = diagnostics
    fun getServerState(): ServerState = serverState
    fun getBoundPort(): Int = boundPort
    fun getBoundInterface(): String? = boundInterface
    fun getHttpBoundPort(): Int = httpBoundPort
    fun getHttpBindError(): String? = httpBindError
    fun getIndexedTrackCount(forceRefresh: Boolean = false): Int =
        if (::mediaLibrary.isInitialized) mediaLibrary.snapshot(forceRefresh).size else 0
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

    override fun transportTerminated(transportAttrs: Attributes?) {
        val remote = transportAttrs?.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR)
        diagnostics.info("EAAS-Transport", "gRPC transport terminated from ${remote ?: "unknown"}")
    }
}