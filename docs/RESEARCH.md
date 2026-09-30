# 2MIMICDJ2 EAAS Research Log

## 2026-09-30 — dynamic-port failure analysis

### Direct hardware evidence

- The Redmi/Android server now starts successfully.
- EAAS discovery on UDP 11224 is bidirectional with Prime GO.
- Prime GO displays the discovered "Mimic DJ" resource.
- With the tested build, gRPC 50010 cannot be bound on the Android device: `EPERM`.
- gRPC 50100 can be bound successfully on the concrete LAN IPv4 address.
- The diagnostic report showed no completed Prime GO -> phone gRPC session and no RPC calls after selecting the resource.

### Packet-capture evidence

The supplied `denonlog.txt` contains a successful Engine/Prime GO remote-library exchange between 10.93.187.191 (Prime GO) and 10.93.187.49 (server PC):

1. TCP connection to server port 50020.
2. HTTP `GET /ping`.
3. HTTP 200 response.
4. TCP connection to server port 50010.
5. HTTP/2/gRPC handshake.
6. `/networktrust.v1.NetworkTrustService/CreateTrust`.
7. `/enginelibrary.v1.EngineLibraryService/EventStream`.
8. `/enginelibrary.v1.EngineLibraryService/GetLibrary`.
9. `/enginelibrary.v1.EngineLibraryService/GetTracks`.
10. Later, `GetTrack` followed by HTTP file transfer on port 50020.

The supplied `denonlog2.txt` also shows direct gRPC operation on TCP 50010, including `GetLibrary`, `GetTracks`, and `GetTrack`. It does not show a pre-gRPC HTTP ping in that capture, so the ping is not assumed to be universal across firmware/workflows.

### External implementation evidence

Open-source go-stagelinq documents the standard EAAS endpoints as gRPC 50010 and HTTP 50020.

Open-source StageLinq TypeScript explicitly parses the advertised gRPC URL port and derives the HTTP port as `grpcPort + 10`.

### Current hypothesis

The previous Android fallback was internally inconsistent:

- discovery advertised `grpc://<phone-ip>:50100`;
- gRPC listened on 50100;
- HTTP nevertheless attempted to listen on fixed 50020.

For a dynamically advertised gRPC endpoint, the paired HTTP endpoint is expected to be 50110. The server must have both endpoints ready before advertising the resource.

### Change implemented in this branch

- Derive HTTP port from the actual bound gRPC port (`grpcPort + 10`).
- Bind HTTP before starting EAAS discovery, so discovery is not advertised before both endpoints exist.
- Make bind diagnostics target the actual HTTP port.

### Remaining unknown

If Prime GO still does not open a TCP session to 50100/50110 after this change, the next decisive question is whether that Prime GO firmware actually honors a non-standard EAAS gRPC port from the discovery URL. A hardware packet capture of the click on "Mimic DJ" is then more useful than further blind port changes.

References:
- go-stagelinq: https://github.com/icedream/go-stagelinq
- StageLinq: https://github.com/chrisle/StageLinq
