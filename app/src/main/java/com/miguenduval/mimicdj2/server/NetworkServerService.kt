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
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.Executors

class NetworkServerService : Service() {
    private val diagnostics = ServerDiagnostics()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var tcpListener: ServerSocketChannel? = null
    private var udpListener: java.net.DatagramSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var boundPort = 0
    private var boundInterface: String? = null

    // Binder for service binding
    inner class LocalBinder : Binder() {
        fun getService(): NetworkServerService = this@NetworkServerService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? = binder

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
        diagnostics.info(TAG, "Service created")
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
        if (tcpListener != null) { diagnostics.warn(TAG, "Server already running on port $boundPort"); return }
        scope.launch { doStartServer(port, iface) }
    }

    private fun doStartServer(port: Int, iface: String?) {
        try {
            tcpListener = ServerSocketChannel.open()
            tcpListener?.configureBlocking(false)
            val socket = tcpListener?.socket()
            if (iface != null && !iface.isEmpty()) {
                try {
                    val address = InetAddress.getByName(iface)
                    socket?.bind(InetSocketAddress(address, port))
                    boundInterface = iface
                    diagnostics.info(TAG, "Bound to interface $iface:$port")
                } catch (e: Exception) {
                    diagnostics.warn(TAG, "Failed to bind to $iface, fallback 0.0.0.0: $e")
                    socket?.bind(InetSocketAddress(port))
                    boundInterface = "0.0.0.0"
                }
            } else {
                socket?.bind(InetSocketAddress(port))
                boundInterface = "0.0.0.0"
            }
            boundPort = socket?.localPort ?: port
            diagnostics.info(TAG, "TCP server listening on ${boundInterface}:$boundPort")
            acceptConnections()
            startUdpListener(port - 1)
            updateNotification("Server running on ${boundInterface}:$boundPort")
        } catch (e: Exception) {
            diagnostics.error(TAG, "Failed to start server", e)
            stopSelf()
        }
    }

    private fun acceptConnections() {
        scope.launch {
            while (tcpListener?.isOpen == true) {
                try {
                    val clientChannel = tcpListener?.accept()
                    if (clientChannel != null) {
                        diagnostics.connectionsOpened.incrementAndGet()
                        val clientAddr = (clientChannel.socket().remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress ?: "unknown"
                        diagnostics.lastClientIp = clientAddr
                        diagnostics.lastClientContact = System.currentTimeMillis()
                        diagnostics.info(TAG, "Accepted connection from $clientAddr")
                        executor.execute { handleClient(clientChannel, clientAddr) }
                    }
                } catch (e: Exception) {
                    if (tcpListener?.isOpen == true) diagnostics.error(TAG, "Error accepting", e)
                }
            }
        }
    }

    private fun handleClient(channel: SocketChannel, clientAddr: String) {
        val socket = channel.socket()
        val input = socket.getInputStream()
        val buffer = ByteArray(8192)
        try {
            val bytesRead = input.read(buffer)
            if (bytesRead > 0) {
                val data = buffer.copyOf(bytesRead)
                diagnostics.debug(TAG, "Received $bytesRead bytes from $clientAddr: ${bytesToHex(data)}")
                val preview = data.take(100)
                diagnostics.info(TAG, "First bytes from $clientAddr: ${bytesToHex(preview)}")
                identifyProtocol(data, clientAddr)
                val response = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".toByteArray()
                socket.getOutputStream().write(response)
            }
        } catch (e: Exception) {
            diagnostics.error(TAG, "Error handling client $clientAddr", e)
        } finally {
            try { channel.close() } catch (e: Exception) { diagnostics.debug(TAG, "Error closing", e) }
        }
    }

    private fun identifyProtocol(data: ByteArray, clientAddr: String) {
        val str = String(data.take(minOf(data.size, 200)), java.nio.charset.StandardCharsets.UTF_8)
        if (str.startsWith("GET ") || str.startsWith("POST ") || str.startsWith("HEAD ")) {
            diagnostics.info(TAG, "HTTP request from $clientAddr")
        } else if (data.size >= 4 && data[0] == 0x16 && data[1] == 0x03) {
            diagnostics.info(TAG, "TLS handshake from $clientAddr")
        } else if (str.contains("PRI * HTTP/2.0")) {
            diagnostics.info(TAG, "HTTP/2 preface from $clientAddr")
        } else if (data.size >= 5 && data[0] == 0x00 && data[1] == 0x00 && data[2] == 0x00) {
            diagnostics.info(TAG, "Possible gRPC/Protobuf from $clientAddr")
        } else {
            diagnostics.info(TAG, "Unknown protocol from $clientAddr: ${bytesToHex(data.take(20))}")
        }
    }

    private fun startUdpListener(port: Int) {
        scope.launch {
            try {
                udpListener = java.net.DatagramSocket(port)
                udpListener?.soTimeout = 1000
                diagnostics.info(TAG, "UDP listener on port $port")
                val buffer = ByteArray(2048)
                val packet = java.net.DatagramPacket(buffer, buffer.size)
                while (udpListener?.isClosed == false) {
                    try {
                        udpListener?.receive(packet)
                        val length = packet.length
                        val data = buffer.copyOf(length)
                        val sender = packet.socketAddress
                        diagnostics.discoveryRxCount.incrementAndGet()
                        diagnostics.lastDiscoveryRx = System.currentTimeMillis()
                        diagnostics.lastDiscoveryRxPayload = data
                        diagnostics.lastClientIp = (sender as? InetSocketAddress)?.address?.hostAddress
                        diagnostics.info(TAG, "UDP discovery from $sender: ${bytesToHex(data)}")
                    } catch (e: java.net.SocketTimeoutException) { }
                    catch (e: Exception) {
                        if (udpListener?.isClosed == false) diagnostics.error(TAG, "UDP receive error", e)
                    }
                }
            } catch (e: Exception) {
                diagnostics.warn(TAG, "Failed to start UDP listener on $port: $e")
            }
        }
    }

    private fun stopServer() {
        diagnostics.info(TAG, "Stopping server")
        try { tcpListener?.close(); tcpListener = null } catch (e: Exception) { diagnostics.error(TAG, "Error closing TCP", e) }
        try { udpListener?.close(); udpListener = null } catch (e: Exception) { diagnostics.error(TAG, "Error closing UDP", e) }
        boundPort = 0; boundInterface = null
        updateNotification("Server stopped")
        stopForeground(true); stopSelf()
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        diagnostics.info(TAG, "Service destroyed")
        stopServer()
        diagnostics.shutdown()
        executor.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    fun getDiagnostics(): ServerDiagnostics = diagnostics
    fun getBoundPort(): Int = boundPort
    fun getBoundInterface(): String? = boundInterface
}