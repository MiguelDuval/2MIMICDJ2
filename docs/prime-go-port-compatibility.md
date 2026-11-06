# Prime GO / Android port compatibility research

Last reviewed: 2026-11-06

## Direct Android hardware evidence

The 2026-09-30 diagnostic report from the target phone and Prime GO shows:
- EAAS discovery on UDP 11224 works in both directions.
- The app advertised `grpc://10.93.187.7:50100` in that test.
- Android rejected TCP bind 50010/50011 with EPERM across Java/native and wildcard/LAN/loopback paths.
- TCP 50100 and 50110 were available.
- No external Prime GO gRPC transport or RPC reached the phone in that test.

Two old diagnostic labels were misleading:
- `Prime GO TCP/50010: CONNECTED` was a phone-initiated outbound connection to the Prime GO's own TCP/50010.
- `Raw TCP accepts: 1` was the phone's local self-test connection.
Current counters split local self-tests from external peer connections.

The 2026-09-30 test did not configure the compatibility gateway: the EAAS response advertised the phone's 50100 directly. It therefore does not validate the gateway.

## What the target Prime GO captures show

Both packet-list captures are direct Prime GO Remote Library sessions against a Windows Engine Desktop host at `10.93.187.49`.

### denonlog.txt
1. Prime GO opens TCP 50020.
2. Sends `GET /ping`; receives HTTP 200.
3. Opens TCP 50010 and negotiates cleartext HTTP/2.
4. Calls `NetworkTrustService/CreateTrust`.
5. Calls `EngineLibraryService/EventStream`, `GetLibrary` and `GetTracks`.

### denonlog2.txt
1. EAAS discovery on UDP 11224.
2. Prime GO connects to TCP 50010 and uses cleartext HTTP/2/gRPC.
3. Calls `EventStream`, `GetLibrary`, and `GetTracks` repeatedly.
4. Calls `GetTrack`.
5. Opens a new TCP connection to port 50020 and sends an HTTP/1.1 `GET /download/<URL-encoded-key>`.
6. Receives HTTP 200 and a sustained audio response stream exceeding 41 MB.

The second capture has no `CreateTrust` or `/ping` call in its packet list. These calls are observed behaviors, not mandatory steps in every browse session. Packet-list exports omit raw HTTP/2 DATA payload bytes and cannot resolve protobuf field-number disagreements.

## Cross-device scope from DeathCamel58

DeathCamel's detailed network/gRPC findings primarily describe SC6000/JP13 Engine OS 5.0.4. On those units TCP 50010 served EngineSync only; EngineLibrary/NetworkTrust were not implemented on that particular listener. The external EAAS Library endpoint was not identified. The Prime GO captures show Prime GO as a client of a PC Remote Library server on 50010/50020. This describes a different device/server role and is not a contradiction.

The same corpus documents a separate EAAS HTTP server with `/ping` and `/download` routes, short-lived high TCP ports on SC6000, and a license callback on TCP 41401. The license callback is not the audio download server. See `docs/research/deathcamel58-reverse-engineering-findings-2026-11-06.md`.

## Android bind conclusion

The target phone's kernel rejects app-level bind to standard TCP 50010 and 50020 with EPERM. Changing Java/native socket APIs, address families, wildcard vs concrete LAN address, or gRPC library does not bypass a kernel-level bind policy.

The app uses high local ports (normally gRPC 50100 and HTTP 50110). This is diagnostic mode unless a LAN bridge maps standard external endpoints.

## Compatibility gateway architecture

The implemented bridge maps:
- LAN PC/gateway TCP 50010 -> phone TCP 50100 (gRPC/HTTP2).
- LAN PC/gateway TCP 50020 -> phone TCP 50110 (HTTP file transfer).

In bridge mode, discovery advertises `grpc://GATEWAY_IP:50010`, while GetTrack returns a file URL using gateway TCP 50020. The app pins the local ports in bridge mode so it cannot silently fall back to a mismatched phone port.

Windows setup helper: `tools/configure_eaas_bridge.cmd PHONE_IP` (run from an elevated Command Prompt). Python alternative: `tools/eaas_bridge.py --target-host PHONE_IP`.

**Hardware status:** the bridge has not yet been validated by a real Prime GO session. It is an implemented workaround, not a claimed resolution.

## Next real-device test

1. Put Prime GO, Windows PC and Android phone on the same non-isolated LAN.
2. Start the phone server with the PC's LAN IPv4 in the app's Engine compatibility bridge field.
3. Configure PC forwarding to the phone's current LAN IP using the Windows helper or Python bridge.
4. Verify discovery advertises PC:50010, then select the source.
5. Confirm external TCP/gRPC counters, required Trust/Library RPCs, GetTrack, external GET /download on TCP50020, returned bytes, and actual playback.

Do not interpret phone local self-test counters as Prime GO traffic. Exact Prime GO Engine OS/build is still required before applying version-specific SC6000 findings.
