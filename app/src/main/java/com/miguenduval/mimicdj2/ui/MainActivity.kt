package com.miguenduval.mimicdj2.ui

import android.Manifest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.miguenduval.mimicdj2.network.NetworkDiagnostics
import com.miguenduval.mimicdj2.R
import com.miguenduval.mimicdj2.server.NetworkServerService
import com.miguenduval.mimicdj2.server.ServerDiagnostics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private companion object {
        const val MEDIA_PERMISSION_REQUEST = 4101
        const val LOCAL_NETWORK_PERMISSION_REQUEST = 4102
    }

    // UI Components
    private lateinit var tvServerStatus: TextView
    private lateinit var tvNetworkInfo: TextView
    private lateinit var tvServicesInfo: TextView
    private lateinit var tvDiscoveryInfo: TextView
    private lateinit var tvSessionInfo: TextView
    private lateinit var tvLibraryInfo: TextView
    private lateinit var tvFileServerInfo: TextView
    private lateinit var tvEventLog: TextView
    private lateinit var btnToggleServer: Button
    private lateinit var btnCopyDiagnostics: Button
    private lateinit var btnClearLogs: Button

    // Services
    private lateinit var networkDiagnostics: NetworkDiagnostics
    private var serverService: NetworkServerService? = null
    private var isBound = false
    private var periodicUiJob: Job? = null
    private var pendingServerStartAfterLocalNetworkPermission = false
    private var pendingServerStartAfterServiceBinding = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as NetworkServerService.LocalBinder
            serverService = binder.getService()
            isBound = true
            Timber.tag(TAG).d("Service connected")
            updateServerUI(binder.getService().getServerState())
            if (pendingServerStartAfterServiceBinding) {
                pendingServerStartAfterServiceBinding = false
                startServer()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serverService = null
            isBound = false
            Timber.tag(TAG).d("Service disconnected")
            updateServerUI(NetworkServerService.ServerState.STOPPED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        networkDiagnostics = NetworkDiagnostics(applicationContext)
        initViews()
        ensureMediaPermission()
        setupClickListeners()
        startNetworkMonitoring()
        bindToServerService()
        startPeriodicUIUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        periodicUiJob?.cancel()
        periodicUiJob = null
        stopNetworkMonitoring()
        unbindFromServerService()
    }

    private fun initViews() {
        tvServerStatus = findViewById(R.id.tvServerStatus)
        tvNetworkInfo = findViewById(R.id.tvNetworkInfo)
        tvServicesInfo = findViewById(R.id.tvServicesInfo)
        tvDiscoveryInfo = findViewById(R.id.tvDiscoveryInfo)
        tvSessionInfo = findViewById(R.id.tvSessionInfo)
        tvLibraryInfo = findViewById(R.id.tvLibraryInfo)
        tvFileServerInfo = findViewById(R.id.tvFileServerInfo)
        tvEventLog = findViewById(R.id.tvEventLog)
        btnToggleServer = findViewById(R.id.btnToggleServer)
        btnCopyDiagnostics = findViewById(R.id.btnCopyDiagnostics)
        btnClearLogs = findViewById(R.id.btnClearLogs)
    }

    private fun setupClickListeners() {
        btnToggleServer.setOnClickListener { toggleServer() }
        btnCopyDiagnostics.setOnClickListener { copyDiagnostics() }
        btnClearLogs.setOnClickListener { clearLogs() }
    }

    private fun ensureMediaPermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), MEDIA_PERMISSION_REQUEST)
        } else {
            ensureLocalNetworkPermission()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MEDIA_PERMISSION_REQUEST -> {
                val granted = grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
                Toast.makeText(
                    this,
                    if (granted) "Music library access granted" else "Music library access denied",
                    Toast.LENGTH_SHORT
                ).show()
                ensureLocalNetworkPermission()
            }
            LOCAL_NETWORK_PERMISSION_REQUEST -> {
                val granted = grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
                Toast.makeText(
                    this,
                    if (granted) "Local network access granted" else "Local network access denied",
                    Toast.LENGTH_SHORT
                ).show()
                if (granted && pendingServerStartAfterLocalNetworkPermission) {
                    pendingServerStartAfterLocalNetworkPermission = false
                    startServer()
                } else if (!granted) {
                    pendingServerStartAfterLocalNetworkPermission = false
                }
            }
        }
    }

    private fun ensureLocalNetworkPermission(): Boolean {
        // Android's Local Network Protection uses the Nearby Wi-Fi permission for
        // apps targeting API 33+ during the compatibility phase. This build
        // intentionally targets 32 to test the legacy socket policy, where LAN
        // access remains implicit and the permission must not block Start.
        if (android.os.Build.VERSION.SDK_INT < 33 ||
            applicationInfo.targetSdkVersion < 33
        ) {
            return true
        }
        val permission = Manifest.permission.NEARBY_WIFI_DEVICES
        val granted = checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        Timber.tag(TAG).d("Local network / Nearby devices permission granted=$granted")
        if (granted) {
            return true
        }
        requestPermissions(arrayOf(permission), LOCAL_NETWORK_PERMISSION_REQUEST)
        return false
    }

    private fun startNetworkMonitoring() {
        networkDiagnostics.start()
        networkDiagnostics.networkInfo.observe(this, Observer { info ->
            updateNetworkUI(info)
        })
    }

    private fun stopNetworkMonitoring() {
        networkDiagnostics.stop()
    }

    private fun bindToServerService() {
        val intent = Intent(this, NetworkServerService::class.java)
        if (!isBound) {
            val bound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Timber.tag(TAG).d("bindToServerService autoCreate=$bound")
        }
    }

    private fun unbindFromServerService() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        pendingServerStartAfterServiceBinding = false
    }

    private fun toggleServer() {
        when (serverService?.getServerState() ?: NetworkServerService.ServerState.STOPPED) {
            NetworkServerService.ServerState.RUNNING -> stopServer()
            NetworkServerService.ServerState.STOPPED -> startServer()
            NetworkServerService.ServerState.STARTING,
            NetworkServerService.ServerState.STOPPING -> {
                Timber.tag(TAG).d("Ignoring toggle during server transition")
            }
        }
    }

    private fun startServer() {
        if (!ensureLocalNetworkPermission()) {
            pendingServerStartAfterLocalNetworkPermission = true
            Snackbar.make(
                findViewById(android.R.id.content),
                "Allow Nearby devices / local network access. Server will start automatically.",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }
        pendingServerStartAfterLocalNetworkPermission = false

        val service = serverService
        if (service != null && isBound) {
            pendingServerStartAfterServiceBinding = false
            service.requestStart(50010)
            updateServerUI(NetworkServerService.ServerState.STARTING)
            return
        }

        pendingServerStartAfterServiceBinding = true
        bindToServerService()
        Snackbar.make(
            findViewById(android.R.id.content),
            "Starting local network server…",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun stopServer() {
        val intent = Intent(this, NetworkServerService::class.java).apply {
            action = NetworkServerService.ACTION_STOP_SERVER
        }
        serverService?.requestStop() ?: runCatching { startService(intent) }
        updateServerUI(NetworkServerService.ServerState.STOPPING)
    }

    private fun updateServerUI(state: NetworkServerService.ServerState) {
        runOnUiThread {
            when (state) {
                NetworkServerService.ServerState.RUNNING -> {
                    tvServerStatus.text = getString(R.string.server_status_on)
                    tvServerStatus.setTextColor(getColor(R.color.green_500))
                    btnToggleServer.text = getString(R.string.server_button_on)
                    btnToggleServer.isEnabled = true
                }
                NetworkServerService.ServerState.STARTING -> {
                    tvServerStatus.text = getString(R.string.server_status_starting)
                    tvServerStatus.setTextColor(getColor(R.color.amber_500))
                    btnToggleServer.text = getString(R.string.server_button_starting)
                    btnToggleServer.isEnabled = false
                }
                NetworkServerService.ServerState.STOPPING -> {
                    tvServerStatus.text = getString(R.string.server_status_stopping)
                    tvServerStatus.setTextColor(getColor(R.color.amber_500))
                    btnToggleServer.text = getString(R.string.server_button_stopping)
                    btnToggleServer.isEnabled = false
                }
                NetworkServerService.ServerState.STOPPED -> {
                    tvServerStatus.text = getString(R.string.server_status_off)
                    tvServerStatus.setTextColor(getColor(R.color.red_500))
                    btnToggleServer.text = getString(R.string.server_button_off)
                    btnToggleServer.isEnabled = true
                }
            }
        }
    }

    private fun updateNetworkUI(info: NetworkDiagnostics.NetworkInfoData) {
        runOnUiThread {
            val sb = StringBuilder()
            sb.append("Interface: ${info.interfaceName}\n")
            sb.append("IPv4: ${info.ipv4Addresses.joinToString(", ") { if (it.isEmpty()) "none" else it }}\n")
            sb.append("IPv6: ${info.ipv6Addresses.joinToString(", ") { if (it.isEmpty()) "none" else it }}\n")
            sb.append("Subnet: ${info.subnet}\n")
            sb.append("Gateway: ${info.gateway}\n")
            sb.append("SSID: ${info.ssid}\n")
            sb.append("VPN: ${if (info.vpnActive) "YES" else "NO"}\n")
            sb.append("State: ${info.connectivityState}")
            tvNetworkInfo.text = sb.toString()
        }
    }

    private fun refreshNetworkInfo() {
        networkDiagnostics.refresh()
    }

    private fun startPeriodicUIUpdate() {
        periodicUiJob = lifecycleScope.launch {
            while (isActive) {
                refreshNetworkInfo()
                updateServerUI(serverService?.getServerState() ?: NetworkServerService.ServerState.STOPPED)
                updateServicesUI()
                updateDiscoveryUI()
                updateSessionUI()
                updateLibraryUI()
                updateFileServerUI()
                updateEventLog()
                delay(1000L)
            }
        }
    }

    private fun updateServicesUI() {
        val service = serverService
        if (service == null) {
            tvServicesInfo.text = "Service: not bound"
            return
        }
        val port = service.getBoundPort()
        val iface = service.getBoundInterface() ?: "none"
        val httpPort = service.getHttpBoundPort()
        val httpError = service.getHttpBindError()
        tvServicesInfo.text =
            "gRPC: ${if (port > 0) "LISTENING" else "OFF"}
" +
            "Port: ${if (port > 0) port else "—"}
" +
            "Interface: $iface
" +
            "HTTP: ${if (httpPort > 0) "LISTENING" else "OFF"}
" +
            "HTTP port: ${if (httpPort > 0) httpPort else "—"}" +
            (if (!httpError.isNullOrBlank()) "
HTTP error: $httpError" else "")
    }

    private fun updateDiscoveryUI() {
        val service = serverService
        if (service == null) {
            tvDiscoveryInfo.text = "Server service not bound"
            return
        }
        val diag = service.getDiagnostics()
        tvDiscoveryInfo.text =
            "RX: ${diag.discoveryRxCount.get()} | TX: ${diag.discoveryTxCount.get()}
" +
            "Last RX: ${if (diag.lastDiscoveryRx > 0) formatTime(diag.lastDiscoveryRx) else "never"}
" +
            "Last TX: ${if (diag.lastDiscoveryTx > 0) formatTime(diag.lastDiscoveryTx) else "never"}
" +
            "Last client: ${diag.lastClientIp ?: "none"}"
    }

    private fun updateSessionUI() {
        val service = serverService
        if (service == null) {
            tvSessionInfo.text = "Server service not bound"
            return
        }
        val diag = service.getDiagnostics()
        tvSessionInfo.text = "Raw TCP accepts: ${diag.rawTcpAccepts.get()}
gRPC connections: ${diag.connectionsOpened.get()}
Trust Msgs: ${diag.trustMessages.get()}
RPC Calls: ${diag.rpcCount.get()}
Methods: ${diag.observedRpcMethods.joinToString(", ") { if (it.isEmpty()) "none" else it }}
Prime GO TCP/50010: ${diag.primeGoPort50010 ?: "not probed"}
Prime GO TCP/50020: ${diag.primeGoPort50020 ?: "not probed"}
Prime GO TCP/50021: ${diag.primeGoPort50021 ?: "not probed"}
Prime GO high ports: ${diag.primeGoHighPortScan ?: "not finished"}
Scan progress: ${diag.primeGoHighPortScanProgress ?: "not started"}
Last Client: ${diag.lastClientIp ?: "none"}
Last Contact: ${if (diag.lastClientContact > 0) formatTime(diag.lastClientContact) else "never"}"
    }

    private fun updateLibraryUI() {
        val service = serverService
        if (service == null) {
            tvLibraryInfo.text = "Server service not bound"
            return
        }
        val count = service.getIndexedTrackCount()
        tvLibraryInfo.text = "Indexed tracks: $count"
    }

    private fun updateFileServerUI() {
        val service = serverService
        if (service == null) {
            tvFileServerInfo.text = "Server service not bound"
            return
        }
        val diag = service.getDiagnostics()
        tvFileServerInfo.text =
            "Requests: ${diag.fileRequests.get()}
" +
            "Bytes: ${diag.bytesServed.get()}
" +
            "Ranges: ${diag.rangeRequests.get()}
" +
            "404: ${diag.errors404.get()} | 416: ${diag.errors416.get()} | 500: ${diag.errors500.get()}\n" +
            "Open Failures: ${diag.openFileFailures.get()}"
    }

    private fun updateEventLog() {
        val service = serverService
        if (service == null) {
            tvEventLog.text = "Server service not bound"
            return
        }
        val entries = service.getDiagnostics().getLogEntries().takeLast(12)
        tvEventLog.text = entries.joinToString("
") { entry ->
            val t = formatTime(entry.timestamp)
            "$t ${entry.level} ${entry.tag}: ${entry.message}"
        }
    }

    private fun formatTime(timestamp: Long): String {
        val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        return fmt.format(java.util.Date(timestamp))
    }

    private fun copyDiagnostics() {
        val report = serverService?.getDiagnostics()?.generateDiagnosticReport() ?: "Server not running"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("2MIMICDJ2 diagnostics", report))
        Toast.makeText(this, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun clearLogs() {
        serverService?.getDiagnostics()?.clearLogs()
    }
}
