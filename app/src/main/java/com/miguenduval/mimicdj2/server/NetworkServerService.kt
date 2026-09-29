package com.miguenduval.mimicdj2.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.system.Os
import android.system.OsConstants
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
import io.grpc.InsecureServerCredentials
import io.grpc.okhttp.OkHttpServerBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
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
    private val executor = Executors.newCachedThreadPool()
    private var stageLinqHostService: StageLinqHostService? = null
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
        eaasToken = loadStableEaasToken()
        diagnostics.info(TAG, "Service created")
        diagnostics.info(TAG, "EAAS identity token is stable for this Android installation")
    }

    private fun loadStableEaasToken(): ByteArray {
        val prefs = getSharedPreferences("eaas_identity", Context.MODE_PRIVATE)
        val existing = prefs.getString("token", null)
        val restored = existing?.let { EaasDiscovery.tokenFromHex(it) }
        if (restored != null) return restored
        val created = EaasDiscovery.newToken()
        prefs.edit().putString("token", EaasDiscovery.tokenToHex(created)).apply()
        return created
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
        // Enter foreground immediately; do not wait for network sockets to bind.
        try {
            updateNotification("Starting EAAS server…")
        } catch (t: Throwable) {
            diagnostics.error(TAG, "Failed to enter foreground", t)
            synchronized(lifecycleLock) { serverState = ServerState.STOPPED }
            return
        }
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
        val lanIp = if (iface.isNullOrBlank()) {
            NetworkAddress.currentLanIpv4(applicationContext)
        } else iface

        if (lanIp.isNullOrBlank()) {
            throw IllegalStateException("No LAN IPv4 address available")
        }

        // Inbound server sockets must not depend on a process-wide
        // ConnectivityManager binding. The phone can expose the controller
        // LAN as a local/Wi-Fi network whose routing semantics differ from
        // the process default. The gRPC listener therefore uses an explicit
        // IPv4 wildcard ServerSocketFactory, while outbound probes select
        // the LAN network explicitly.
        diagnostics.info(TAG, "Android process network binding skipped for inbound server")

        // Keep gRPC on 50010 whenever possible. If Android forbids only HTTP
        // 50020, move only HTTP to 50110 so the advertised gRPC endpoint stays
        // stable and the StageLinQ path can be tested independently.
        val candidates = if (port == 50010) {
            listOf(50010 to 50020, 50010 to 50110)
        } else {
            listOf(port to (port + 10))
        }

        var lastFailure: Throwable? = null

        for ((grpcPort, httpPort) in candidates) {
            var candidateGrpc: Server? = null
            try {
                diagnostics.info(TAG, "Trying EAAS endpoint pair gRPC=$grpcPort HTTP=$httpPort")

                candidateGrpc = OkHttpServerBuilder
                    .forPort(
                        grpcPort,
                        InsecureServerCredentials.create()
                    )
                    .socketFactory(GrpcServerSocketFactory(diagnostics))
                    .executor(executor)
                    .addService(NetworkTrustGrpcService(diagnostics))
                    .addService(EngineLibraryGrpcService())
                    .addService(MimicEngineSyncService())
                    .intercept(RpcDiagnosticsInterceptor(diagnostics))
                    .addTransportFilter(GrpcTransportDiagnosticsFilter(diagnostics))
                    .build()
                    .start()

                val candidateHttpFd = bindHttpServer(httpPort)

                synchronized(lifecycleLock) {
                    if (serverState != ServerState.STARTING) {
                        try { candidateGrpc?.shutdownNow() } catch (_: Exception) {}
                        runCatching { Os.close(candidateHttpFd) }
                        diagnostics.warn(TAG, "Discarding late server start; state=$serverState")
                        return
                    }

                    grpcServer = candidateGrpc
                    httpServerFd = candidateHttpFd
                    boundPort = grpcPort
                    boundInterface = lanIp
                    httpBoundPort = httpPort
                    httpBindError = null
                    diagnostics.serverGrpcPort = grpcPort
                    diagnostics.httpServerPort = httpPort
                    diagnostics.httpServerBindError = null
                    serverState = ServerState.RUNNING
                }

                if (httpPort != 50020) {
                    diagnostics.warn(
                        TAG,
                        "EAAS legacy HTTP port 50020 unavailable; keeping gRPC=$grpcPort and using HTTP=$httpPort"
                    )
                }

                diagnostics.info(TAG, "EAAS gRPC server listening on $lanIp:$grpcPort")
                diagnostics.info(TAG, "EAAS HTTP native server listening on 0.0.0.0:$httpPort")
                scope.launch { probeLocalGrpcTcp(lanIp, grpcPort) }
                startHttpAcceptLoop(candidateHttpFd)
                startEaasDiscoveryListener()
                stageLinqHostService = StageLinqHostService(diagnostics, executor).also { host ->
                    runCatching { host.start() }.onFailure {
                        diagnostics.error(TAG, "StageLinQ host startup failed; EAAS remains active", it)
                    }
                }
                updateNotification("EAAS + StageLinQ on $lanIp:$grpcPort")
                return
            } catch (t: Throwable) {
                lastFailure = t
                diagnostics.error(
                    TAG,
                    "EAAS endpoint pair gRPC=$grpcPort HTTP=$httpPort failed",
                    t
                )
                runCatching { candidateGrpc?.shutdownNow() }

                if (httpPort == 50020) {
                    diagnoseTcpBindFailure()
                }
            }
        }

        throw lastFailure ?: IllegalStateException("No usable EAAS endpoint pair")
    }

    private fun bindHttpServer(httpPort: Int): FileDescriptor {
        val fd = Os.socket(
            OsConstants.AF_INET,
            OsConstants.SOCK_STREAM,
            OsConstants.IPPROTO_TCP
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Os.setsockoptInt(
                    fd,
                    OsConstants.SOL_SOCKET,
                    OsConstants.SO_REUSEADDR,
                    1
                )
            }

            // Do not bind this descriptor to a ConnectivityManager Network.
            // The old Mimic branch removed that step because it could break
            // ingress on local/hotspot interfaces.
            Os.bind(fd, InetAddress.getByName("0.0.0.0"), httpPort)
            Os.listen(fd, 64)
            return fd
        } catch (t: Throwable) {
            runCatching { Os.close(fd) }
            throw IllegalStateException(
                "Native HTTP $httpPort bind failed: " +
                    t.javaClass.simpleName + ": " + (t.message ?: "no message"),
                t
            )
        }
    }

    private fun startHttpAcceptLoop(fd: FileDescriptor) {
        scope.launch {
            while (httpServerFd === fd) {
                try {
                    val clientFd = Os.accept(fd, null)
                    executor.execute { handleHttpClient(clientFd) }
                } catch (t: Throwable) {
                    if (httpServerFd === fd) {
                        diagnostics.error(TAG, "HTTP native accept error", t)
                    }
                }
            }
        }
    }

    private fun diagnoseTcpBindFailure() {
        diagnostics.info(TAG, "Port 50020 diagnostic matrix:\n${PortDiagnostics.snapshot(NetworkAddress.currentLanIpv4(applicationContext))}")

        // A failed bind does not tell us whether the port is reserved-but-unused
        // or whether another system/vendor service already owns it. A connect
        // probe distinguishes those cases without requiring root.
        val host = NetworkAddress.currentLanIpv4(applicationContext)
        if (!host.isNullOrBlank()) {
            diagnostics.info(
                TAG,
                "TCP connect probes: 50020 LAN=${probeTcpEndpoint(host, 50020)} " +
                    "loopback=${probeTcpEndpoint("127.0.0.1", 50020)}; " +
                    "50021 LAN=${probeTcpEndpoint(host, 50021)}; " +
                    "50022 LAN=${probeTcpEndpoint(host, 50022)}"
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
        var input: FileInputStream? = null
        var output: FileOutputStream? = null
        try {
            val inputFd = Os.dup(fd)
            val outputFd = Os.dup(fd)
            input = FileInputStream(inputFd)
            output = FileOutputStream(outputFd)

            val request = readHttpRequest(input)
            if (request == null) return

            diagnostics.fileRequests.incrementAndGet()
            diagnostics.lastClientContact = System.currentTimeMillis()
            diagnostics.info(TAG, "HTTP ${request.method} ${request.path} from native client")

            val status = when {
                request.method == "GET" && request.path == "/ping" -> 200
                request.method == "HEAD" && request.path == "/ping" -> 200
                else -> 404
            }
            val body = if (status == 404) {
                "Not Found".toByteArray(Charsets.UTF_8)
            } else {
                ByteArray(0)
            }
            val reason = if (status == 200) "OK" else "Not Found"
            // HTTP requires literal CRLF delimiters. The previous diagnostic
            // listener emitted the characters "\\r\\n", making the response malformed.
            val headers = "HTTP/1.1 " + status + " " + reason + "\r\n" +
                    "Content-Length: " + body.size + "\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "Connection: close\r\n\r\n"
            output.write(headers.toByteArray(Charsets.US_ASCII))
            output.write(body)
            output.flush()
            diagnostics.bytesServed.addAndGet(body.size.toLong())
            if (status == 404) diagnostics.errors404.incrementAndGet()
        } catch (e: Throwable) {
            diagnostics.errors500.incrementAndGet()
            diagnostics.error(TAG, "Error handling native HTTP client", e)
        } finally {
            runCatching { output?.close() }
            runCatching { input?.close() }
            runCatching { Os.close(fd) }
        }
    }

    private data class HttpRequest(val method: String, val path: String)

    private fun readHttpRequest(input: java.io.InputStream): HttpRequest? {
        val bytes = java.io.ByteArrayOutputStream()
        val one = ByteArray(1)
        var state = 0
        while (bytes.size() < 16384) {
            val n = input.read(one)
            if (n < 0) break
            bytes.write(one[0].toInt())
            when (state) {
                0 -> if (bytes.toByteArray().takeLast(4).toByteArray().contentEquals(byteArrayOf(13, 10, 13, 10))) break
            }
        }
        if (bytes.size() == 0) return null
        val line = bytes.toString(Charsets.US_ASCII.name()).lineSequence().firstOrNull() ?: return null
        val parts = line.trim().split(" ")
        if (parts.size < 2) return null
        return HttpRequest(parts[0], parts[1])
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
                                if (diagnostics.externalRawTcpAccepts.get() > 0 ||
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
                            softwareVersion = "1.0.0"
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
        diagnostics.serverGrpcPort = 0
        httpBoundPort = 0
        httpBindError = null
        diagnostics.httpServerPort = 0
        diagnostics.httpServerBindError = null
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
        try { stageLinqHostService?.stop() } catch (e: Exception) { diagnostics.error(TAG, "Error stopping StageLinQ", e) }
        stageLinqHostService = null
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
        try { stageLinqHostService?.stop() } catch (_: Exception) {}
        stageLinqHostService = null
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

    /** Called by the bound Activity so Stop cannot depend on a second startService delivery. */
    fun requestStop() = stopServer()

    fun getDiagnostics(): ServerDiagnostics = diagnostics
    fun getServerState(): ServerState = serverState
    fun getBoundPort(): Int = boundPort
    fun getBoundInterface(): String? = boundInterface
    fun getHttpBoundPort(): Int = httpBoundPort
    fun getHttpBindError(): String? = httpBindError
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