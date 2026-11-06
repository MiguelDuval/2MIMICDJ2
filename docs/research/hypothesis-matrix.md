# Protocol Investigation Matrix

**Rule:** distinguish direct Prime GO evidence (A) from published reverse-engineering/reference evidence (D). A method name in a packet list does not reveal its protobuf payload fields.

| Layer | Question | Evidence status | Evidence reference | Current conclusion |
|---|---|---|---|---|
| Discovery | Is EAAS discovery used? | A — observed | 2026-09-30 diagnostics; both captures | Prime GO sends `EAAS 01 00` to UDP 11224; app replies `EAAS 01 01`. |
| Discovery | Does a reply prove a usable session? | A — negative evidence | 2026-09-30 diagnostic report | No. Discovery succeeded while external gRPC/RPC remained zero. |
| Discovery | Is UDP 51337 the required replacement? | Not established | DeathCamel Engine Networking + JP13 UART socket snapshot | Some JP13 captures show UDP 51337 listeners, but it is not the Prime GO Remote Library discovery path seen in our capture. Keep EAAS/11224. |
| Identity | Is the EAAS token stable? | D / implementation | icedream/go-stagelinq beacon; current app code | It identifies a returning library. App now persists it; Prime GO restart behavior still needs testing. |
| Transport | Prime GO to PC library gRPC port? | A — confirmed | denonlog.txt and denonlog2.txt | TCP 50010, cleartext HTTP/2/gRPC. |
| Transport | Prime GO to PC audio HTTP port? | A — confirmed | denonlog.txt and denonlog2.txt | TCP 50020, HTTP/1.1. |
| Transport | Does Android permit standard server binds? | A — denied | 2026-09-30 diagnostics | Bind 50010/50020 returns EPERM; 50100/50110 are permitted. |
| Transport | Does Prime GO accept a non-standard advertised port? | Unknown / not demonstrated | 2026-09-30 log advertised 50100, no external RPC | Use the gateway to expose 50010/50020. Bridge hardware test remains outstanding. |
| gRPC | Does Prime GO use EngineLibraryService? | A — confirmed | both captures | EventStream, GetLibrary, GetTracks and GetTrack paths observed on PC TCP 50010. |
| gRPC | Which methods are streaming? | D — reference | DeathCamel Engine gRPC reconstruction, SC6000 5.0.4 | GetTracks, SearchTracks and EventStream are server-streaming in the reconstruction; app descriptors now match. |
| Trust | Does CreateTrust exist? | A — observed in one capture | denonlog.txt | Prime GO called NetworkTrustService/CreateTrust before EventStream/GetLibrary/GetTracks in this session. |
| Trust | Is CreateTrust required every time? | A — not universal in shown captures | denonlog.txt vs denonlog2.txt | Second capture has no CreateTrust; likely conditional, previously trusted, or session state. |
| Trust | What is field 2 in CreateTrustRequest? | D — partly known | DeathCamel NetworkTrust proto vs Go reference | Wire type is varint; Go reference calls it wireguard_port, but semantic meaning is not independently confirmed. |
| Trust | What are CreateTrustResponse outcome tags? | Conflicting references / unknown for Prime GO | DeathCamel SC6000 proto vs icedream Go proto | Go uses oneof fields 1/2/3; DeathCamel SC6000 5.0.4 reports one message field 3 with nested mapping uncertain. Need raw target DATA payload. |
| Library | Does Prime GO browse a library? | A — confirmed | both captures | GetLibrary followed by GetTracks. |
| Library | Is pagination used? | Partly known / inferred | Go proto + DeathCamel reconstructed proto | Go calls a field page_size, but DeathCamel marks some GetTracksRequest field semantics tentative. App batches by request page_size without dropping results. |
| Tracks | Does Prime GO request GetTrack before downloading? | A — confirmed | denonlog2.txt | GetTrack is followed by a new TCP 50020 connection. |
| Tracks | Are GetTrackResponse fields fully decoded? | Not resolved | DeathCamel reconstructed proto + Go reference | Packet list shows a 1,075-byte GetTrack response but omits DATA payload bytes. App retains Go mapping hypothesis. |
| File transfer | What is the request? | A — confirmed | denonlog2.txt | HTTP GET /download/<URL-encoded key>; HTTP 200 and a sustained >41 MB audio response. |
| File transfer | Is /ping mandatory? | A — not universal in shown captures | denonlog.txt vs denonlog2.txt | Present in first capture, absent in second. |
| File transfer | Are Range/seek requests required? | Unknown | current captures | Not proven; test after initial playback works. |
| Metadata | Are artwork/performance fields required? | Partial | DeathCamel proto + Go reference | Broad TrackMetadata and TrackPerformanceData structures known; exact Prime GO requirements remain. |
| Firmware | Exact Prime GO Engine OS/build? | Unknown | not recorded in project | Do not generalize SC6000 version-specific findings to Prime GO. |

## Remaining blockers (in order)

1. Validate the LAN gateway on the actual Prime GO: discovery advertises PC:50010; phone listens on 50100/50110.
2. Confirm external TCP/gRPC traffic, and required Trust/Library calls, reach the phone through forwarding.
3. Confirm GetLibrary/GetTracks browse the phone's media inventory.
4. Confirm GetTrack produces a usable URL and external GET /download on TCP 50020 streams the selected file.
5. Confirm the track loads and plays; only then test ranges, seeking, artwork and performance metadata.
6. Record the exact Prime GO Engine OS/build. For schema mismatches, capture original pcapng with raw HTTP/2 DATA bytes rather than only packet-list exports.
