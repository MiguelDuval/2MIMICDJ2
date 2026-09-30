package com.miguenduval.mimicdj2.server

import android.content.Context

data class EaasAdvertisedEndpoint(
    val host: String,
    val grpcPort: Int
)

object EaasEndpointConfig {
    private const val PREFS_NAME = "eaas_endpoint"
    private const val KEY_BRIDGE_HOST = "bridge_host"

    const val STANDARD_GRPC_PORT = 50010
    const val STANDARD_HTTP_PORT = 50020

    fun advertisedEndpoint(
        localHost: String,
        localGrpcPort: Int,
        bridgeHost: String
    ): EaasAdvertisedEndpoint {
        val normalizedBridgeHost = bridgeHost.trim()
        return if (normalizedBridgeHost.isNotEmpty()) {
            EaasAdvertisedEndpoint(
                host = normalizedBridgeHost,
                grpcPort = STANDARD_GRPC_PORT
            )
        } else {
            EaasAdvertisedEndpoint(
                host = localHost,
                grpcPort = localGrpcPort
            )
        }
    }

    fun loadBridgeHost(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BRIDGE_HOST, "")
            .orEmpty()

    fun saveBridgeHost(context: Context, host: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BRIDGE_HOST, host.trim())
            .apply()
    }
}
