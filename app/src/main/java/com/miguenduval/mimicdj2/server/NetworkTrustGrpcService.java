package com.miguenduval.mimicdj2.server;

import com.miguenduval.mimicdj2.eaas.networktrust.CreateTrustRequest;
import com.miguenduval.mimicdj2.eaas.networktrust.CreateTrustResponse;
import com.miguenduval.mimicdj2.eaas.networktrust.NetworkTrustServiceGrpc;
import io.grpc.stub.StreamObserver;

public final class NetworkTrustGrpcService extends NetworkTrustServiceGrpc.NetworkTrustServiceImplBase {
    public static final String INTERNAL_SELF_TEST_DEVICE_NAME = "2MIMICDJ2_INTERNAL_SELF_TEST";

    private final ServerDiagnostics diagnostics;

    public NetworkTrustGrpcService(ServerDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    @Override
    public void createTrust(
            CreateTrustRequest request,
            StreamObserver<CreateTrustResponse> responseObserver) {
        String deviceName = request.hasDeviceName() ? request.getDeviceName() : "";
        boolean localSelfTest = INTERNAL_SELF_TEST_DEVICE_NAME.equals(deviceName);
        diagnostics.recordTrustMessage(localSelfTest);

        String pk = request.hasEd25519Pk() ? request.getEd25519Pk() : "";
        String wireguardPort = request.hasWireguardPort()
                ? String.valueOf(request.getWireguardPort())
                : "";

        diagnostics.info(
                "NetworkTrustGrpcService",
                "CreateTrust from device=" + deviceName
                        + " ed25519_pk=" + (pk.isEmpty() ? "none" : "present")
                        + " wireguard_port=" + (wireguardPort.isEmpty() ? "none" : wireguardPort)
                        + (localSelfTest ? " (local self-test)" : " (external peer)"));

        CreateTrustResponse response = CreateTrustResponse.newBuilder()
                .setGranted(
                        com.miguenduval.mimicdj2.eaas.networktrust.CreateTrustGranted
                                .newBuilder()
                                .build())
                .build();

        byte[] requestBytes = request.toByteArray();
        byte[] responseBytes = response.toByteArray();
        diagnostics.info(
                "NetworkTrustGrpcService",
                "CreateTrust protobuf shape: requestBytes=" + requestBytes.length
                        + " requestTags=" + ProtobufWireTagSummary.summarize(requestBytes)
                        + " responseBytes=" + responseBytes.length
                        + " responseTags=" + ProtobufWireTagSummary.summarize(responseBytes));

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
