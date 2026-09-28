package com.miguenduval.mimicdj2.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import timber.log.Timber

class ConnectivityChangeReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ConnectivityReceiver"
        var onConnectivityChanged: (() -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            Timber.tag(TAG).d("Connectivity changed: ${intent.action}")
            onConnectivityChanged?.invoke()
        }
    }
}