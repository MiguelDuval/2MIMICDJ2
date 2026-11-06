package com.miguenduval.mimicdj2.server

import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.EngineLibraryServiceGrpc
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetLibrariesRequest
import io.grpc.StatusRuntimeException
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit

/**
 * Read-only fingerprint of EngineLibrary at a remote Engine endpoint.
 *
 * Unlike CreateTrust, GetLibraries cannot trigger an interactive pairing prompt.
 */
internal object PrimeGoGrpcCompatibilityProbe {
    fun getLibraries(host: String, port: Int, timeoutMillis: Long = 1_200L): String {
        var channel: io.grpc.ManagedChannel? = null
        return try {
            channel = OkHttpChannelBuilder.forAddress(host, port).usePlaintext().build()
            val response = EngineLibraryServiceGrpc
                .newBlockingStub(channel)
                .withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS)
                .getLibraries(GetLibrariesRequest.getDefaultInstance())
            "GRPC_OK libraries=" + response.librariesCount
        } catch (error: StatusRuntimeException) {
            val description = error.status.description?.replace('\n', ' ')?.take(120).orEmpty()
            "GRPC_" + error.status.code.name +
                if (description.isNotBlank()) ": " + description else ""
        } catch (error: Throwable) {
            "NO_GRPC " + error.javaClass.simpleName + ": " + (error.message ?: "no message")
        } finally {
            runCatching { channel?.shutdownNow() }
        }
    }
}
