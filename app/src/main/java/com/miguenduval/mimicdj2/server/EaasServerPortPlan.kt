package com.miguenduval.mimicdj2.server

/**
 * Chooses the Android-side gRPC listener ports.
 *
 * In compatibility-bridge mode the external gateway is fixed to forward
 * Engine's 50010/50020 pair to the phone's 50100/50110 pair, so the phone
 * must not silently move to another high port.
 */
object EaasServerPortPlan {
    const val BRIDGE_GRPC_PORT = 50100
    const val BRIDGE_HTTP_PORT = 50110

    fun grpcCandidates(requestedPort: Int, bridgeMode: Boolean): List<Int> {
        if (bridgeMode) {
            return listOf(BRIDGE_GRPC_PORT)
        }

        return buildList {
            add(requestedPort)
            if (requestedPort != BRIDGE_GRPC_PORT) add(BRIDGE_GRPC_PORT)
            if (requestedPort != 60000) add(60000)
            add(0)
        }
    }
}
