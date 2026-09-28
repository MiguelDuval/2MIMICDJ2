# Protocol Investigation Matrix

**Instructions**: Fill this matrix FROM EVIDENCE (packet captures, hardware observation, reproducible tests), not from memory or assumptions. Each cell should reference a ledger entry or capture file.

| Layer | Question | Evidence Status | Evidence Reference | Current Hypothesis | Confidence |
|-------|----------|-----------------|-------------------|-------------------|------------|
| **Link** | Ethernet/Wi-Fi only? | UNKNOWN — NEEDS EVIDENCE | | Wi-Fi (Prime GO has no Ethernet) | Low |
| **Discovery** | Broadcast/multicast/unicast? | OBSERVED — Prime GO sends EAAS discovery to server | 2026-09-28 physical test | UDP/EAAS request-response | High |
| **Discovery** | Source port? | OBSERVED | 2026-09-28 physical test: Prime GO UDP source port 44829 | Ephemeral source port | High |
| **Discovery** | Destination port? | OBSERVED | 2026-09-28 physical test: UDP 11224 | 11224 | High |
| **Discovery** | Interval/trigger? | OBSERVED | 2026-09-28 physical test: requests about every 2 s while source UI active | Periodic retry during source discovery | High |
| **Discovery** | Payload format? | OBSERVED | 2026-09-28 physical test: `45 41 41 53 01 00` | EAAS v1 request | High |
| **Discovery** | Response format? | OBSERVED | 2026-09-28 physical test: `EAAS 01 01` + 16-byte token + hostname + grpc URL + version + extra | EAAS service advertisement | High |
| **Discovery** | Address advertisement? | OBSERVED | 2026-09-28 physical test: `grpc://10.122.26.146:50010` | Server IP in response | High |
| **Discovery** | Service/port advertisement? | PARTIAL — gRPC port observed, HTTP port not advertised in the captured response | 2026-09-28 physical test | gRPC endpoint 50010 advertised; 50020 remains a separate hypothesis | Medium |
| **Discovery** | UUID/token/device identifier? | PARTIAL — 16-byte token observed in server response; no UUID in request | 2026-09-28 physical test | Token present in response, UUID mechanism still unknown | Medium |
| **Trust** | Trust handshake exists? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Trust** | Request/response sequence | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Trust** | Cryptography or simple approval? | UNKNOWN — NEEDS EVIDENCE | | Simple approval (historical) | Low |
| **Trust** | Interactive approval? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Transport** | TCP/UDP? | PARTIAL — UDP discovery confirmed; no inbound TCP session observed after discovery | 2026-09-28 physical test | TCP likely next stage, but target-specific evidence is still missing | Medium |
| **Transport** | Fixed or dynamic port? | UNKNOWN — target did not open a TCP session in this test | 2026-09-28 physical test | Do not treat 50010/50020 as proven architecture yet | Low |
| **Transport** | HTTP/1.1 / HTTP/2 / custom framing? | UNKNOWN — NEEDS EVIDENCE | | HTTP/2 + gRPC (SC6000) or custom | Low |
| **RPC** | Service name? | UNKNOWN — NEEDS EVIDENCE | | engine.LibraryService (SC6000) | Low |
| **RPC** | Method names? | UNKNOWN — NEEDS EVIDENCE | | GetLibrary, GetTracks, GetTrack | Low |
| **RPC** | Request/response encoding? | UNKNOWN — NEEDS EVIDENCE | | Protobuf (if gRPC) | Low |
| **RPC** | Streaming vs unary? | UNKNOWN — NEEDS EVIDENCE | | Unary for metadata, streaming for browse? | Low |
| **Library** | Root library request? | UNKNOWN — NEEDS EVIDENCE | | GetLibrary with root ID | Low |
| **Library** | Hierarchy? | UNKNOWN — NEEDS EVIDENCE | | Playlists -> tracks | Low |
| **Library** | Pagination? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Library** | Search? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Library** | Filters? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Tracks** | Metadata fields consumed? | UNKNOWN — NEEDS EVIDENCE | | Title, artist, album, BPM, duration, artwork | Low |
| **Tracks** | Artwork? | UNKNOWN — NEEDS EVIDENCE | | Possibly required | Low |
| **Tracks** | Performance data? | UNKNOWN — NEEDS EVIDENCE | | Beatgrid, cues? | Low |
| **Track Retrieval** | Returned location/URL/blob? | UNKNOWN — NEEDS EVIDENCE | | HTTP URL with IP/port | Low |
| **File Transfer** | Protocol? | UNKNOWN — NEEDS EVIDENCE | | HTTP/1.1 (historical) | Low |
| **File Transfer** | Range requests? | UNKNOWN — NEEDS EVIDENCE | | Likely (seeking) | Low |
| **File Transfer** | HEAD support? | UNKNOWN — NEEDS EVIDENCE | | Likely | Low |
| **File Transfer** | Content-Length? | UNKNOWN — NEEDS EVIDENCE | | Required | Low |
| **File Transfer** | Content-Type? | UNKNOWN — NEEDS EVIDENCE | | audio/mpeg, audio/flac, etc. | Low |
| **File Transfer** | Redirects? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **File Transfer** | Retries? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **File Transfer** | Connection reuse? | UNKNOWN — NEEDS EVIDENCE | | Keep-alive likely | Low |
| **Failure** | Expected errors? | OBSERVED | 2026-09-28 physical test: Prime GO UI reports `No connection was detected`; server saw discovery only | Failure occurs after discovery and before observed RPC/file activity | High |
| **Network** | Interface selection? | OBSERVED | 2026-09-28 physical test: Android interface `wlan1`, server IP `10.122.26.146`, Prime GO `10.122.26.191` | Wi-Fi LAN path | High |
| **Lifecycle** | Behavior after Wi-Fi changes? | UNKNOWN — NEEDS EVIDENCE | | Re-discovery? | Low |

## Evidence Gap Summary

**Critical Unknowns (Blocking MVP)**:
1. Discovery mechanism and packet format
2. Transport protocol (gRPC/HTTP/2 vs custom)
3. Service ports (fixed vs dynamic)
4. Trust/pairing sequence
5. Library RPC service definition
6. Track location/URL format
7. File transfer protocol details

**Next Actions**:
1. Set up packet capture environment
2. Observe Prime GO behavior when entering Source menu
3. Capture discovery traffic
4. Capture library browse traffic
5. Capture track load traffic