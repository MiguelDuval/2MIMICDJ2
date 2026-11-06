package com.miguenduval.mimicdj2.server

/**
 * Chooses the Android-side gRPC listener ports.
 *
 * In compatibility-bridge mode the external gateway is fixed to forward
 * Engine's 50010/50020 pair to the phone's 50100/50110 pair, so the phone
 * must not silently move to another high port.
 */
object EaasServerPortPlan {
    fun grpcCandidates(requestedPort: Int): List<Int> {
        return buildList {
            add(requestedPort)
            if (requestedPort != BRIDGE_GRPC_PORT) add(BRIDGE_GRPC_PORT)
            if (requestedPort != 60000) add(60000)
            add(0)
        }
    }
}
