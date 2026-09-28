package com.miguenduval.mimicdj2.ui

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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as NetworkServerService.LocalBinder
            serverService = binder.getService()
            isBound = true
            Timber.tag(TAG).d("Service connected")
            updateServerUI(binder.getService().getServerState())
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
            bindService(intent, serviceConnection, 0)
        }
    }

    private fun unbindFromServerService() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
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
        val intent = Intent(this, NetworkServerService::class.java).apply {
            action = NetworkServerService.ACTION_START_SERVER
            putExtra(NetworkServerService.EXTRA_PORT, 50010)
        }
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to start foreground server service")
            Snackbar.make(findViewById(android.R.id.content), "Server start failed: " + e.message, Snackbar.LENGTH_LONG).show()
            updateServerUI(NetworkServerService.ServerState.STOPPED)
            return
        }
        updateServerUI(NetworkServerService.ServerState.STARTING)
        if (!isBound) bindToServerService()
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
        periodicUiJob?.cancel()
        periodicUiJob = lifecycleScope.launch {
            while (isActive) {
                val service = serverService
                if (service != null) {
                    updateDiagnosticsUI()
                    updateServerUI(service.getServerState())
                }
                delay(500)
            }
        }
    }

    private fun updateDiagnosticsUI() {
        val diag = serverService?.getDiagnostics()
        if (diag == null) return

        runOnUiThread {
            val port = serverService?.getBoundPort() ?: 0
            val iface = serverService?.getBoundInterface() ?: "none"
            val httpPort = serverService?.getHttpBoundPort() ?: 0
            val httpError = serverService?.getHttpBindError()
            tvServicesInfo.text = "TCP gRPC: ${if (port > 0) "$iface:$port" else "not bound"}\n" +
                    "EAAS UDP discovery: ${if (port > 0) "0.0.0.0:11224" else "not bound"}\n" +
                    "HTTP 50020: ${if (httpPort == 50020) "ON" else "OFF"}" +
                    (if (httpError != null) "\nHTTP error: $httpError" else "")

            tvDiscoveryInfo.text = "Discovery: ${if (port > 0) "ON" else "OFF"}\nRX: ${diag.discoveryRxCount.get()} | TX: ${diag.discoveryTxCount.get()}\nLast RX: ${if (diag.lastDiscoveryRx > 0) formatTime(diag.lastDiscoveryRx) else "never"}\nLast TX: ${if (diag.lastDiscoveryTx > 0) formatTime(diag.lastDiscoveryTx) else "never"}"

            tvSessionInfo.text = "Raw TCP accepts: ${diag.rawTcpAccepts.get()}\ngRPC connections: ${diag.connectionsOpened.get()}\nTrust Msgs: ${diag.trustMessages.get()}\nRPC Calls: ${diag.rpcCount.get()}\nMethods: ${diag.observedRpcMethods.joinToString(", ") { if (it.isEmpty()) "none" else it }}\nPrime GO TCP/50010: ${diag.primeGoPort50010 ?: "not probed"}\nPrime GO TCP/50020: ${diag.primeGoPort50020 ?: "not probed"}\nPrime GO TCP/50021: ${diag.primeGoPort50021 ?: "not probed"}\nPrime GO high ports: ${diag.primeGoHighPortScan ?: "not finished"}\nScan progress: ${diag.primeGoHighPortScanProgress ?: "not started"}\nLast Client: ${diag.lastClientIp ?: "none"}\nLast Contact: ${if (diag.lastClientContact > 0) formatTime(diag.lastClientContact) else "never"}"

            tvLibraryInfo.text = "Indexed: 0 | Supported: 0\nUnreadable: 0 | Duplicates: 0"

            tvFileServerInfo.text = "Requests: ${diag.fileRequests.get()}\nBytes: ${diag.bytesServed.get()}\nRanges: ${diag.rangeRequests.get()}\n404: ${diag.errors404.get()} | 416: ${diag.errors416.get()} | 500: ${diag.errors500.get()}\nOpen Failures: ${diag.openFileFailures.get()}"

            val logs = diag.getLogEntries().takeLast(20).joinToString("\n") { "[${formatTime(it.timestamp)}] ${it.level} ${it.tag}: ${it.message}" }
            tvEventLog.text = if (logs.isEmpty()) "[No log entries]" else logs
        }
    }

    private fun copyDiagnostics() {
        val diag = serverService?.getDiagnostics()
        val report = diag?.generateDiagnosticReport() ?: "Server not running"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Diagnostic Report", report)
        clipboard.setPrimaryClip(clip)
        Snackbar.make(findViewById(android.R.id.content), "Diagnostic report copied to clipboard", Snackbar.LENGTH_LONG).show()
    }

    private fun clearLogs() {
        serverService?.getDiagnostics()?.clearLogs()
        tvEventLog.text = "[Logs cleared]"
    }

    private fun formatTime(timestamp: Long): String {
        return android.text.format.DateFormat.format("HH:mm:ss", timestamp).toString()
    }
}