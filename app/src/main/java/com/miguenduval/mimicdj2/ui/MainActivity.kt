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
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import com.miguenduval.mimicdj2.network.NetworkDiagnostics
import com.miguenduval.mimicdj2.R
import com.miguenduval.mimicdj2.server.NetworkServerService
import com.miguenduval.mimicdj2.server.ServerDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val networkDiagnostics = NetworkDiagnostics(this)
    private var serverService: NetworkServerService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as NetworkServerService.LocalBinder
            serverService = binder.getService()
            isBound = true
            Timber.tag(TAG).d("Service connected")
            updateServerUI(true)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serverService = null
            isBound = false
            Timber.tag(TAG).d("Service disconnected")
            updateServerUI(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        setupClickListeners()
        startNetworkMonitoring()
        bindToServerService()
        startPeriodicUIUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
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
        NetworkDiagnostics.onConnectivityChanged = { runOnUiThread { refreshNetworkInfo() } }
    }

    private fun stopNetworkMonitoring() {
        networkDiagnostics.stop()
    }

    private fun bindToServerService() {
        val intent = Intent(this, NetworkServerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindFromServerService() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun toggleServer() {
        if (serverService?.getBoundPort() != 0) {
            stopServer()
        } else {
            startServer()
        }
    }

    private fun startServer() {
        val intent = Intent(this, NetworkServerService::class.java).apply {
            action = NetworkServerService.ACTION_START_SERVER
            putExtra(NetworkServerService.EXTRA_PORT, 50010)
        }
        startForegroundService(intent)
        updateServerUI(true)
    }

    private fun stopServer() {
        val intent = Intent(this, NetworkServerService::class.java).apply {
            action = NetworkServerService.ACTION_STOP_SERVER
        }
        startService(intent)
        updateServerUI(false)
    }

    private fun updateServerUI(running: Boolean) {
        runOnUiThread {
            if (running) {
                tvServerStatus.text = getString(R.string.server_status_on)
                tvServerStatus.setTextColor(getColor(R.color.green_500))
                btnToggleServer.text = getString(R.string.stop_server)
            } else {
                tvServerStatus.text = getString(R.string.server_status_off)
                tvServerStatus.setTextColor(getColor(R.color.red_500))
                btnToggleServer.text = getString(R.string.start_server)
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
        val activeNetwork = (getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork
        if (activeNetwork != null) {
            networkDiagnostics.updateNetworkInfo(activeNetwork)
        }
    }

    private fun startPeriodicUIUpdate() {
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                if (serverService != null) {
                    updateDiagnosticsUI()
                }
                delay(2000)
            }
        }
    }

    private fun updateDiagnosticsUI() {
        val diag = serverService?.getDiagnostics()
        if (diag == null) return

        runOnUiThread {
            val port = serverService?.getBoundPort() ?: 0
            val iface = serverService?.getBoundInterface() ?: "none"
            tvServicesInfo.text = "TCP: ${if (port > 0) "$iface:$port" else "not bound"}\nUDP: ${if (port > 0) "$iface:${port-1}" else "not bound"}"

            tvDiscoveryInfo.text = "Discovery: ${if (port > 0) "ON" else "OFF"}\nRX: ${diag.discoveryRxCount.get()} | TX: ${diag.discoveryTxCount.get()}\nLast RX: ${if (diag.lastDiscoveryRx > 0) formatTime(diag.lastDiscoveryRx) else "never"}\nLast TX: ${if (diag.lastDiscoveryTx > 0) formatTime(diag.lastDiscoveryTx) else "never"}"

            tvSessionInfo.text = "Connections: ${diag.connectionsOpened.get()}\nTrust Msgs: ${diag.trustMessages.get()}\nRPC Calls: ${diag.rpcCount.get()}\nMethods: ${diag.observedRpcMethods.joinToString(", ") { if (it.isEmpty()) "none" else it }}\nLast Client: ${diag.lastClientIp ?: "none"}\nLast Contact: ${if (diag.lastClientContact > 0) formatTime(diag.lastClientContact) else "never"}"

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
        clipboard.primaryClip = clip
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