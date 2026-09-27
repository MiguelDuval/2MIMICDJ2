package com.miguenduval.mimicdj2.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.util.Log

class ConnectivityChangeReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ConnectivityReceiver"
        var onConnectivityChanged: (() -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            Log.d(TAG, "Connectivity changed: ${intent.action}")
            onConnectivityChanged?.invoke()
        }
    }
}