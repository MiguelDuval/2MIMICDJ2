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
| **Transport** | TCP/UDP? | OBSERVED — UDP discovery + inbound TCP/50010 and TCP/50020 | denonlog2.txt, 2026-09-29 direct Wireshark capture | Prime GO Remote Library uses TCP after EAAS discovery | High |
| **Transport** | Fixed or dynamic port? | OBSERVED — 50010 and 50020 used in direct Prime GO session | denonlog2.txt, 2026-09-29 | 50010 = gRPC/HTTP2 library session; 50020 = HTTP file transfer | High |
| **Transport** | HTTP/1.1 / HTTP/2 / custom framing? | OBSERVED — HTTP/2 on 50010; HTTP/1.1 on 50020 | denonlog2.txt, 2026-09-29 | Split EAAS transport: gRPC library + HTTP file stream | High |
| **RPC** | Service name? | OBSERVED | denonlog2.txt, 2026-09-29 | enginelibrary.v1.EngineLibraryService | High |
| **RPC** | Method names? | OBSERVED | denonlog2.txt, 2026-09-29 | EventStream, GetLibrary, GetTracks, GetTrack observed | High |
| **RPC** | Request/response encoding? | OBSERVED — gRPC/PB over cleartext HTTP/2 | denonlog2.txt, 2026-09-29 | Protobuf / gRPC | High |
| **RPC** | Streaming vs unary? | UNKNOWN — NEEDS EVIDENCE | | Unary for metadata, streaming for browse? | Low |
| **Library** | Root library request? | OBSERVED — GetLibrary then GetTracks | denonlog2.txt, 2026-09-29 | Implement GetLibraries/GetLibrary/GetTracks for MVP | High |
| **Library** | Hierarchy? | UNKNOWN — NEEDS EVIDENCE | | Playlists -> tracks | Low |
| **Library** | Pagination? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Library** | Search? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Library** | Filters? | UNKNOWN — NEEDS EVIDENCE | | Unknown | Low |
| **Tracks** | Metadata fields consumed? | PARTIAL — GetTrack is followed by file download; exact decoded protobuf fields not present in packet-list export | denonlog2.txt, 2026-09-29 | Return stable id + title/artist/album/duration and HTTP blob URL | Medium |
| **Tracks** | Artwork? | UNKNOWN — NEEDS EVIDENCE | | Possibly required | Low |
| **Tracks** | Performance data? | UNKNOWN — NEEDS EVIDENCE | | Beatgrid, cues? | Low |
| **Track Retrieval** | Returned location/URL/blob? | OBSERVED indirectly — HTTP request path is /download/<URL-encoded file key>; GetTrack occurs immediately before it | denonlog2.txt, 2026-09-29 | GetTrackResponse.blob.url should resolve to TCP/50020 download endpoint | High |
| **File Transfer** | Protocol? | OBSERVED — HTTP/1.1 200 OK followed by ~41.6 MB response stream | denonlog2.txt, 2026-09-29 | Native HTTP/1.1 server on 50020 | High |
| **File Transfer** | Range requests? | UNKNOWN — not exercised in this capture | denonlog2.txt, 2026-09-29 | Implement single-byte-range support defensively | Low |
| **File Transfer** | HEAD support? | UNKNOWN — NEEDS EVIDENCE | | Likely | Low |
| **File Transfer** | Content-Length? | NOT SHOWN in packet-list export, but fixed-length binary transfer is strongly indicated | denonlog2.txt, 2026-09-29 | Send explicit Content-Length for reliable streaming | Medium |
| **File Transfer** | Content-Type? | NOT SHOWN in packet-list export | denonlog2.txt, 2026-09-29 | Use MediaStore MIME type | Medium |
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