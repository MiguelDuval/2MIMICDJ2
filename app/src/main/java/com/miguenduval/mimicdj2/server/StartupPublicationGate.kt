package com.miguenduval.mimicdj2.server

/**
 * Keeps server publication atomic: the service is not exposed as RUNNING until
 * both the gRPC and paired HTTP endpoints have valid listening ports.
 */
object StartupPublicationGate {
    fun commit(
        grpcPort: Int,
        httpPort: Int,
        publish: () -> Unit
    ): Boolean {
        if (grpcPort <= 0 || httpPort <= 0) return false
        publish()
        return true
    }
}
