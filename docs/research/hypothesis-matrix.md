# Protocol Investigation Matrix

**Instructions**: Fill this matrix FROM EVIDENCE (packet captures, hardware observation, reproducible tests), not from memory or assumptions. Each cell should reference a ledger entry or capture file.

| Layer | Question | Evidence Status | Evidence Reference | Current Hypothesis | Confidence |
|-------|----------|-----------------|-------------------|-------------------|------------|
| **Link** | Ethernet/Wi-Fi only? | UNKNOWN — NEEDS EVIDENCE | | Wi-Fi (Prime GO has no Ethernet) | Low |
| **Discovery** | Broadcast/multicast/unicast? | UNKNOWN — NEEDS EVIDENCE | | UDP broadcast (per historical hints) | Low |
| **Discovery** | Source port? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Discovery** | Destination port? | UNKNOWN — NEEDS EVIDENCE | | 11224 (historical hint) | Low |
| **Discovery** | Interval/trigger? | UNKNOWN — NEEDS EVIDENCE | | On Source menu open, periodic | Low |
| **Discovery** | Payload format? | UNKNOWN — NEEDS EVIDENCE | | EAAS/StageLinq beacon | Low |
| **Discovery** | Response format? | UNKNOWN — NEEDS EVIDENCE | | EAAS service advertisement | Low |
| **Discovery** | Address advertisement? | UNKNOWN — NEEDS EVIDENCE | | Server IP in response | Low |
| **Discovery** | Service/port advertisement? | UNKNOWN — NEEDS EVIDENCE | | Library port, file port | Low |
| **Discovery** | UUID/token/device identifier? | UNKNOWN — NEEDS EVIDENCE | | Device UUID in beacon | Low |
| **Trust** | Trust handshake exists? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Trust** | Request/response sequence | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Trust** | Cryptography or simple approval? | UNKNOWN — NEEDS EVIDENCE | | Simple approval (historical) | Low |
| **Trust** | Interactive approval? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Transport** | TCP/UDP? | UNKNOWN — NEEDS EVIDENCE | | TCP for library/file, UDP for discovery | Low |
| **Transport** | Fixed or dynamic port? | UNKNOWN — NEEDS EVIDENCE | | Dynamic (advertised in discovery) | Low |
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
| **Failure** | Expected errors? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Network** | Interface selection? | UNKNOWN — NEEDS EVIDENCE | | Wi-Fi interface bound | Low |
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