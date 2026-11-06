package com.miguenduval.mimicdj2.server

import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.EngineLibraryServiceGrpc
import io.grpc.MethodDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineLibraryGrpcContractTest {
    @Test
    fun engine_streaming_methods_have_server_streaming_descriptors() {
        assertEquals(
            MethodDescriptor.MethodType.SERVER_STREAMING,
            EngineLibraryServiceGrpc.getEventStreamMethod().type
        )
        assertEquals(
            MethodDescriptor.MethodType.SERVER_STREAMING,
            EngineLibraryServiceGrpc.getGetTracksMethod().type
        )
        assertEquals(
            MethodDescriptor.MethodType.SERVER_STREAMING,
            EngineLibraryServiceGrpc.getSearchTracksMethod().type
        )
    }

    @Test
    fun library_lookup_methods_remain_unary() {
        assertEquals(
            MethodDescriptor.MethodType.UNARY,
            EngineLibraryServiceGrpc.getGetLibrariesMethod().type
        )
        assertEquals(
            MethodDescriptor.MethodType.UNARY,
            EngineLibraryServiceGrpc.getGetLibraryMethod().type
        )
        assertEquals(
            MethodDescriptor.MethodType.UNARY,
            EngineLibraryServiceGrpc.getGetTrackMethod().type
        )
    }
}
