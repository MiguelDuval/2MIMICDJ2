# 2MIMICDJ2 EAAS Research Log

## 2026-09-30 — dynamic-port failure analysis

The Android host can bind the high gRPC/HTTP pair but cannot bind Engine's standard TCP 50010/50020 ports; the kernel rejects those standard binds with EPERM. EAAS discovery on UDP 11224 works and the app advertises a service, but the direct 50100 test did not produce an external Prime GO gRPC session. This led to the LAN compatibility gateway design.

## 2026-11-06 — reverse-engineering review and implementation corrections

Reviewed DeathCamel58's reverse-engineering corpus through pinned source commit `0f7966516609e3de2b71e4aef21625bd8d94c57c`. Its detailed networking/gRPC results are chiefly from SC6000/JP13 Engine OS 5.0.4, not the Prime GO/JP11. On those SC6000 units TCP 50010 served EngineSync; the externally exposed EAAS Library endpoint remained unresolved. This does not contradict our Prime GO capture, where Prime GO connects to a PC Remote Library server on TCP 50010 for EngineLibrary RPC and TCP 50020 for file transfer.

Capture re-analysis:
- `denonlog.txt`: Prime GO opens TCP 50020, GET /ping -> 200; then TCP 50010 HTTP/2/gRPC, calls NetworkTrust/CreateTrust, EventStream/GetLibrary/GetTracks.
- `denonlog2.txt`: confirms EventStream/GetLibrary/GetTracks/GetTrack on TCP 50010 and GET /download on HTTP 50020 with a long audio transfer. It does not contain CreateTrust or /ping.
- The packet-list exports omit raw HTTP/2 DATA payload bytes; they cannot resolve CreateTrust response field numbering or GetTrack response field mapping.

Code corrections on `agent/clean-slate`:
- GetTracks, SearchTracks and EventStream are server-streaming RPCs; track results are sent in batches without truncation.
- Track URLs use the actual paired HTTP port in direct high-port mode and standard 50020 in bridge mode.
- EAAS token identity is persisted across Android Service restarts.
- Local gRPC self-tests are counted separately from external peer connections.
- A read-only GetLibraries probe fingerprints Prime GO TCP 50010 and transient high ports.
- CreateTrust diagnostics record only protobuf sizes and field/wire tags; no key or field values are logged.

The current compatibility gateway remains an implementation, not hardware proof. Final acceptance still requires a real Prime GO source browse, GetTrack, HTTP 50020 download, and playback through the gateway. The exact Prime GO Engine OS/build is still unknown and must not be inferred from SC6000 data.
