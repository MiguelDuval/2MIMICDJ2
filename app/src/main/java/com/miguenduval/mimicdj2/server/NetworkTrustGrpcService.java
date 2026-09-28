package com.miguenduval.mimicdj2.server;

import com.miguenduval.mimicdj2.eaas.networktrust.CreateTrustRequest;
import com.miguenduval.mimicdj2.eaas.networktrust.CreateTrustResponse;
import com.miguenduval.mimicdj2.eaas.networktrust.NetworkTrustServiceGrpc;
import io.grpc.stub.StreamObserver;

public final class NetworkTrustGrpcService extends NetworkTrustServiceGrpc.NetworkTrustServiceImplBase {
    private final ServerDiagnostics diagnostics;

    public NetworkTrustGrpcService(ServerDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    @Override
    public void createTrust(
            CreateTrustRequest request,
            StreamObserver<CreateTrustResponse> responseObserver) {
        diagnostics.trustMessages.incrementAndGet();
        diagnostics.lastTrustRequest = System.currentTimeMillis();

        String deviceName = request.hasDeviceName() ? request.getDeviceName() : "";
        String pk = request.hasEd25519Pk() ? request.getEd25519Pk() : "";
        String wireguardPort = request.hasWireguardPort()
                ? String.valueOf(request.getWireguardPort())
                : "";

        diagnostics.info(
                "NetworkTrustGrpcService",
                "CreateTrust from device=" + deviceName
                        + " ed25519_pk=" + (pk.isEmpty() ? "none" : "present")
                        + " wireguard_port=" + (wireguardPort.isEmpty() ? "none" : wireguardPort));

        responseObserver.onNext(
                CreateTrustResponse.newBuilder()
                        .setGranted(
                                com.miguenduval.mimicdj2.eaas.networktrust.CreateTrustGranted
                                        .newBuilder()
                                        .build())
                        .build());
        responseObserver.onCompleted();
    }
}
