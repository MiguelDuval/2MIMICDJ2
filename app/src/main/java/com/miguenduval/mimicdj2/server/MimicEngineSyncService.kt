package com.miguenduval.mimicdj2.server

import com.google.protobuf.Empty
import com.miguenduval.mimicdj2.eaas.enginesync.EngineSyncServiceGrpc
import com.miguenduval.mimicdj2.eaas.enginesync.SyncState
import io.grpc.stub.StreamObserver

class MimicEngineSyncService : EngineSyncServiceGrpc.EngineSyncServiceImplBase() {
    override fun setSyncState(request: SyncState, responseObserver: StreamObserver<Empty>) {
        responseObserver.onNext(Empty.getDefaultInstance())
        responseObserver.onCompleted()
    }
}
