package com.miguenduval.mimicdj2.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.miguenduval.mimicdj2.R
import com.miguenduval.mimicdj2.server.MediaLibrary
import com.miguenduval.mimicdj2.server.NetworkServerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private companion object {
        const val MEDIA_PERMISSION_REQUEST = 4101
        const val LOCAL_NETWORK_PERMISSION_REQUEST = 4102
    }

    private lateinit var tvServerStatus: TextView
    private lateinit var tvServerMessage: TextView
    private lateinit var tvMediaFolder: TextView
    private lateinit var tvTrackCount: TextView
    private lateinit var btnToggleServer: Button
    private lateinit var btnSelectMediaFolder: Button
    private lateinit var btnUseAllDeviceMusic: Button

    private var serverService: NetworkServerService? = null
    private var isBound = false
    private var periodicUiJob: Job? = null
    private var pendingServerStartAfterLocalNetworkPermission = false
    private var pendingServerStartAfterServiceBinding = false
    private var pendingUseAllDeviceMusic = false
    private var pendingDefaultMusicPermission = false

    private val mediaFolderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val persisted = runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess

            if (persisted) {
                getSharedPreferences(MediaLibrary.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(MediaLibrary.KEY_SELECTED_TREE_URI, uri.toString())
                    .apply()
                refreshMediaLibrary()
                updateMediaSourceUI()
                Toast.makeText(this, R.string.folder_connected, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.folder_access_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as NetworkServerService.LocalBinder
            serverService = binder.getService()
            isBound = true
            refreshMediaLibrary()
            updateMediaSourceUI()
            updateServerUI(binder.getService().getServerState())

            if (pendingServerStartAfterServiceBinding) {
                pendingServerStartAfterServiceBinding = false
                requestServerStart()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serverService = null
            isBound = false
            updateServerUI(NetworkServerService.ServerState.STOPPED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        setupClickListeners()
        ensureDefaultMusicPermission()
        bindToServerService()
        startPeriodicUiUpdate()
    }

    override fun onDestroy() {
        periodicUiJob?.cancel()
        periodicUiJob = null
        unbindFromServerService()
        super.onDestroy()
    }

    private fun initViews() {
        tvServerStatus = findViewById(R.id.tvServerStatus)
        tvServerMessage = findViewById(R.id.tvServerMessage)
        tvMediaFolder = findViewById(R.id.tvMediaFolder)
        tvTrackCount = findViewById(R.id.tvTrackCount)
        btnToggleServer = findViewById(R.id.btnToggleServer)
        btnSelectMediaFolder = findViewById(R.id.btnSelectMediaFolder)
        btnUseAllDeviceMusic = findViewById(R.id.btnUseAllDeviceMusic)
        updateMediaSourceUI()
        updateTrackCount()
    }

    private fun setupClickListeners() {
        btnToggleServer.setOnClickListener { toggleServer() }
        btnSelectMediaFolder.setOnClickListener { mediaFolderPicker.launch(null) }
        btnUseAllDeviceMusic.setOnClickListener { useAllDeviceMusic() }
    }

    private fun bindToServerService() {
        if (isBound) return
        bindService(
            Intent(this, NetworkServerService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
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
            NetworkServerService.ServerState.STOPPING -> Unit
        }
    }

    private fun startServer() {
        if (!ensureLocalNetworkPermission()) {
            pendingServerStartAfterLocalNetworkPermission = true
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.local_network_permission_required,
                Snackbar.LENGTH_LONG
            ).show()
            return
        }
        pendingServerStartAfterLocalNetworkPermission = false

        if (serverService != null && isBound) {
            requestServerStart()
        } else {
            pendingServerStartAfterServiceBinding = true
            bindToServerService()
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.server_starting,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestServerStart() {
        serverService?.requestStart(50010)
        updateServerUI(NetworkServerService.ServerState.STARTING)
    }

    private fun stopServer() {
        val service = serverService
        if (service != null) {
            service.requestStop()
        } else {
            val intent = Intent(this, NetworkServerService::class.java).apply {
                action = NetworkServerService.ACTION_STOP_SERVER
            }
            runCatching { startService(intent) }
        }
        updateServerUI(NetworkServerService.ServerState.STOPPING)
    }

    private fun ensureLocalNetworkPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return true

        val permission = Manifest.permission.NEARBY_WIFI_DEVICES
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return true
        }

        requestPermissions(arrayOf(permission), LOCAL_NETWORK_PERMISSION_REQUEST)
        return false
    }

    private fun ensureDefaultMusicPermission() {
        val selectedFolder = getSharedPreferences(MediaLibrary.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(MediaLibrary.KEY_SELECTED_TREE_URI, null)
        if (!selectedFolder.isNullOrBlank()) return

        val permission = mediaReadPermission()
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) return

        pendingDefaultMusicPermission = true
        requestPermissions(arrayOf(permission), MEDIA_PERMISSION_REQUEST)
    }

    private fun mediaReadPermission(): String =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun onMediaPermissionResult(granted: Boolean) {
        val requestedAllMusic = pendingUseAllDeviceMusic
        val requestedDefaultSource = pendingDefaultMusicPermission
        pendingUseAllDeviceMusic = false
        pendingDefaultMusicPermission = false

        if (!granted) {
            tvTrackCount.text = getString(R.string.music_access_required)
            Toast.makeText(this, R.string.music_permission_denied, Toast.LENGTH_SHORT).show()
            return
        }

        if (requestedAllMusic) {
            switchToAllDeviceMusic()
        } else if (requestedDefaultSource) {
            refreshMediaLibrary()
        }
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with the app's target SDK.")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MEDIA_PERMISSION_REQUEST -> onMediaPermissionResult(
                grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
            )
            LOCAL_NETWORK_PERMISSION_REQUEST -> {
                val granted = grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted && pendingServerStartAfterLocalNetworkPermission) {
                    pendingServerStartAfterLocalNetworkPermission = false
                    startServer()
                } else if (!granted) {
                    pendingServerStartAfterLocalNetworkPermission = false
                }
            }
        }
    }

    private fun useAllDeviceMusic() {
        val permission = mediaReadPermission()
        if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingUseAllDeviceMusic = true
            requestPermissions(arrayOf(permission), MEDIA_PERMISSION_REQUEST)
            return
        }
        switchToAllDeviceMusic()
    }

    private fun switchToAllDeviceMusic() {
        val preferences = getSharedPreferences(MediaLibrary.PREFS_NAME, Context.MODE_PRIVATE)
        preferences.getString(MediaLibrary.KEY_SELECTED_TREE_URI, null)?.let { selected ->
            runCatching {
                contentResolver.releasePersistableUriPermission(
                    Uri.parse(selected),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        preferences.edit().remove(MediaLibrary.KEY_SELECTED_TREE_URI).apply()
        refreshMediaLibrary()
        updateMediaSourceUI()
        Toast.makeText(this, R.string.using_all_device_music, Toast.LENGTH_SHORT).show()
    }

    private fun refreshMediaLibrary() {
        runCatching { serverService?.refreshMediaLibrary() }
        updateTrackCount()
    }

    private fun updateMediaSourceUI() {
        val selected = getSharedPreferences(MediaLibrary.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(MediaLibrary.KEY_SELECTED_TREE_URI, null)

        if (selected.isNullOrBlank()) {
            tvMediaFolder.text = getString(R.string.source_all_device_music)
        } else {
            val uri = Uri.parse(selected)
            val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }
                .getOrDefault(uri.lastPathSegment.orEmpty())
            val folderName = documentId.substringAfter(':', documentId)
            tvMediaFolder.text = getString(R.string.source_selected_folder, folderName)
        }
    }

    private fun updateTrackCount() {
        val count = runCatching { serverService?.getIndexedTrackCount() ?: 0 }.getOrDefault(0)
        tvTrackCount.text = resources.getQuantityString(R.plurals.track_count, count, count)
    }

    private fun updateServerUI(state: NetworkServerService.ServerState) {
        runOnUiThread {
            when (state) {
                NetworkServerService.ServerState.RUNNING -> {
                    tvServerStatus.text = getString(R.string.server_status_on)
                    tvServerStatus.setTextColor(getColor(R.color.green_500))
                    btnToggleServer.text = getString(R.string.stop_server)
                    btnToggleServer.isEnabled = true
                    tvServerMessage.text = getString(R.string.server_running_message)
                }
                NetworkServerService.ServerState.STARTING -> {
                    tvServerStatus.text = getString(R.string.server_status_starting)
                    tvServerStatus.setTextColor(getColor(R.color.amber_500))
                    btnToggleServer.text = getString(R.string.server_starting)
                    btnToggleServer.isEnabled = false
                    tvServerMessage.text = getString(R.string.server_starting_message)
                }
                NetworkServerService.ServerState.STOPPING -> {
                    tvServerStatus.text = getString(R.string.server_status_stopping)
                    tvServerStatus.setTextColor(getColor(R.color.amber_500))
                    btnToggleServer.text = getString(R.string.server_stopping)
                    btnToggleServer.isEnabled = false
                    tvServerMessage.text = getString(R.string.server_stopping_message)
                }
                NetworkServerService.ServerState.STOPPED -> {
                    tvServerStatus.text = getString(R.string.server_status_off)
                    tvServerStatus.setTextColor(getColor(R.color.red_500))
                    btnToggleServer.text = getString(R.string.start_server)
                    btnToggleServer.isEnabled = true
                    val startupError = serverService?.getLastStartupError()
                    tvServerMessage.text = startupError?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.server_stopped_message)
                }
            }
        }
    }

    private fun startPeriodicUiUpdate() {
        periodicUiJob?.cancel()
        periodicUiJob = lifecycleScope.launch {
            while (isActive) {
                serverService?.let { service ->
                    updateServerUI(service.getServerState())
                    updateTrackCount()
                }
                delay(1000)
            }
        }
    }
}
