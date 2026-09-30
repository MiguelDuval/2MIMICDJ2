# Prime GO / Android port compatibility research

## Status: 2026-09-30

### Direct hardware observation (Android phone + Prime GO)

The latest 2MIMICDJ2 test establishes all of the following:

- EAAS discovery on UDP 11224 is working.
- Prime GO sends repeated `EAAS 01 00` requests and receives valid `EAAS 01 01` responses.
- The response advertises `grpc://10.93.187.7:50100`.
- The phone successfully listens on gRPC 50100 and passes its local TCP self-test.
- Prime GO is visible as the peer at 10.93.187.191.
- No gRPC session reaches the phone after selecting the Mimic DJ source.
- Therefore source discovery and source-list presentation are not the remaining blocker.

### Captured Engine Remote Library behavior

The repository capture `denonlog2.txt` provides the target-device behavior.

Observed sequence on a known-good Engine Remote Library server:

1. Prime GO performs EAAS discovery on UDP 11224.
2. Prime GO opens TCP to the server on port 50010.
3. HTTP/2 / gRPC negotiation follows.
4. Prime GO calls `NetworkTrustService/CreateTrust`.
5. The server returns a successful trust response.
6. Prime GO then calls `EngineLibraryService/GetLibrary`.
7. Prime GO calls `GetTracks` and later `GetTrack`.
8. The selected track is subsequently fetched over HTTP port 50020.

The known-good capture uses server endpoints 50010/tcp (gRPC) and 50020/tcp (HTTP).

### Android kernel evidence from the test device

The current diagnostic matrix on the phone reports:

- Java 50020 wildcard: EPERM
- Java 50021 wildcard: EPERM
- Java 50022 wildcard: EPERM
- Java 50100 wildcard: ALLOWED
- Java ephemeral wildcard: ALLOWED
- Java 50020 on the concrete LAN address: EPERM
- Java 50020 on loopback: EPERM
- Native 50020 wildcard: EPERM
- Native 50020 on the concrete LAN address: EPERM
- Native 50020 on loopback: EPERM

The gRPC listener shows the same pattern:

- 50010 bind: EPERM
- 50100 bind: succeeds

This is a port-level bind restriction, not a wildcard-vs-concrete-address problem.

### Interpretation

Linux/Android kernels contain an `ip_local_unbindable_ports` mechanism. The kernel returns `-EPERM` when an application explicitly binds one of those ports. This can affect both Java and native sockets because both eventually invoke the kernel bind operation.

The current evidence is therefore consistent with the phone firmware marking the Engine-standard 50010/50020 ports as unbindable.

### Compatibility consequence

An arbitrary fallback such as 50100 is useful as a diagnostic listener but is **not demonstrated to be compatible with the Prime GO**. The target hardware capture demonstrates the standard 50010/50020 endpoint pair. The current 50100 build proving discovery while producing zero gRPC calls is strong evidence that discovery does not by itself make the non-standard gRPC port usable on this target.

### What this means for implementation

Do not spend another iteration changing only:

- gRPC library version;
- ServerSocket wildcard vs LAN address;
- fixed high-port fallback;
- HTTP implementation;
- Foreground Service lifecycle.

Those changes cannot make a kernel-rejected 50010/50020 bind succeed.

A true standalone-phone solution now requires one of:

1. a firmware/runtime change that makes 50010/50020 bindable;
2. a privileged/system-level port forwarder/NAT path;
3. a different Engine protocol path that the actual Prime GO demonstrably follows.

Until one of those is demonstrated, 50100/50110-style fallback must be treated as diagnostic/research mode, not as a proven Engine-compatible transport.
