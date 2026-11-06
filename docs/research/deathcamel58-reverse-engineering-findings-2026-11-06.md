# DeathCamel58 reverse-engineering findings

Reviewed: 2026-11-06
Primary index: https://deathcamel58.github.io/denon-reverse-engineering/starter-topic.html
Source repository: https://github.com/DeathCamel58/denon-reverse-engineering
Relevant pages: Engine Networking, Engine gRPC, Engine Library, Engine OS, UART, Updating.

## Executive conclusion

The source is valuable, but its scope matters: the detailed networking and gRPC findings are principally from two SC6000 units running Engine OS 5.0.4. Our known-good packet capture is from the target Prime GO and directly shows its Remote Library session using TCP 50010 for Engine Library gRPC and TCP 50020 for the audio download. The Prime GO capture remains authoritative for this project. Do not copy SC6000 5.0.4 port assignments onto the Prime GO without a matching target capture.

## Findings that change our understanding

### 1. Engine has distinct gRPC subsystems

DeathCamel58 identifies Engine's device-to-device services and a separate EAAS gRPC server inside Libraries/private/Planck/Eaas/src/grpcServer. On SC6000 5.0.4, TCP 50010 was observed serving enginesync.v1.EngineSyncService only. enginelibrary.v1 and networktrust.v1 returned UNIMPLEMENTED on that listener. The author leaves the externally exposed port of the separate EAAS gRPC server unresolved.

This differs from our Prime GO capture, where EngineLibrary methods were observed on TCP 50010. This is a device/firmware-scope difference, not a reason to discard the Prime GO capture. The app currently registers EngineSync, NetworkTrust and EngineLibrary services on one listener; that may match the captured Prime GO generation, but it is not a universal Engine OS topology.

### 2. Two unrelated HTTP servers exist

The EAAS HTTP server is cpp-httplib and registers /ping and /download/(<)(.*)(>) for application/octet-stream transfers. The license activation callback is a separate hand-written HTTP server on TCP 41401, serving /authorise?code=. Port 41401 is not the music download server.

DeathCamel58 did not observe EAAS HTTP listening on 50020 on the tested SC6000 5.0.4 units. Our Prime GO capture, however, directly shows TCP 50020, HTTP/1.1 GET /download/<URL-encoded-key>, HTTP 200 and a long binary stream. Keep the target-specific implementation on 50020.

### 3. Discovery is not trust or session establishment

The six-byte EAAS request is ASCII EAAS followed by 01 00. The Go reference implementation replies with a 16-byte token, UTF-16BE hostname, UTF-8 grpc://host:port URL, UTF-16BE software version, a one-byte marker and UTF-16BE extra string. It chooses a reply address based on the receiving interface/subnet.

DeathCamel58 notes that the EAAS beacon alone does not populate EngineSync's discovered-host table. EngineSync methods also require uuid metadata and a previously discovered host. Our Prime GO diagnostics prove discovery request/reply works; they do not prove that trust or library RPC has succeeded.

### 4. Trust schema discrepancy must be resolved from the target capture

CreateTrustRequest contains ed25519_pk (UTF-8 string), wireguard_port (uint32) and device_name (string). The reference describes interactive grant/deny/busy outcomes.

Important discrepancy: icedream/go-stagelinq models CreateTrustResponse as a oneof with granted/denied/busy at fields 1/2/3. DeathCamel58's static analysis of the SC6000 5.0.4 binary reports a single message-valued response field at field 3 and explicitly marks the nested mapping uncertain. These are not interchangeable claims.

Do not change the Prime GO response field numbers based solely on SC6000 static analysis. Preserve raw CreateTrust request and response DATA payloads from the known-good Engine Desktop capture and compare the protobuf tags with our generated response. Current auto-grant behavior remains a prototype shortcut, not a complete trust implementation.

### 5. Engine Library schema is much better documented

Methods and RPC kinds from the source:
- Unary: GetCredentials, GetLibraries, GetLibrary, GetTrack, GetSearchFilters, GetHistorySessions, GetHistoryPlayedTracks, PutEvents.
- Server streaming: GetTracks, SearchTracks, EventStream.

The source schema confirms the main request/response shapes: GetTracksRequest has library_id, playlist_id, page_size, sort, list_type and filters; GetTrackResponse has blob, performance_data and metadata; TrackBlobUrl has URL and file_size. TrackMetadata includes title/artist/album/key/BPM/rating/year/genre/comment/label/length/composer/remixer/date-added. TrackPerformanceData includes beat grid, quick cues, loops, main cue, overview waveform and BPM.

The author distinguishes exact method names/kinds, field numbers and wire types from inferred names/types for some non-string fields. Preserve UNKNOWN/UNCONFIRMED labels rather than treating inferred semantics as certain.

### 6. High ports may be ephemeral service-directory sockets

On SC6000 5.0.4, roughly a dozen high ports were observed; extra ports appeared only while network library sharing was active and disappeared within seconds. They were silent to an HTTP/2 preface and were suspected to belong to airNetworkExchange directory/service sockets. If a future Prime GO capture reveals such a port, probe it immediately in the same capture pass. A delayed scan can miss it.

### 7. Classic StageLinq discovery and Ableton Link are not our current blocker

The SC6000 5.0.4 tests did not observe useful UDP 51337 airD discovery; EAAS UDP 11224 was active. Our Prime GO capture independently confirms UDP 11224. Ableton Link multicast at 224.76.78.75:20808 carries tempo/phase sync and is unrelated to library browse, trust or file transfer.

## Corrections to project assumptions

| Simplification | Corrected understanding |
|---|---|
| TCP 50010 always hosts EAAS Library and Trust | True for the captured Prime GO session; not true for the tested SC6000 5.0.4 listener, which hosted EngineSync only. |
| Discovery proves a usable session | It proves UDP request/reply and endpoint advertisement only. |
| TCP 50020 is universally active | Confirmed on the target Prime GO capture; not observed active on SC6000 5.0.4. |
| Local gRPC self-test proves hardware compatibility | It proves only that our own local client/server pair works. |
| CreateTrust response schema is settled | Available reconstructions disagree on response field numbering; target payload must decide. |

## Engineering actions

1. Keep the Android port restriction and LAN gateway work as a transport workaround, but test the full forwarded path from the Prime GO.
2. Make diagnostics distinguish UDP discovery, gateway TCP accept, HTTP/2 preface/SETTINGS, RPC method path, CreateTrust request/response tags, GetLibrary/GetTracks/GetTrack, and HTTP 50020 request/streamed bytes.
3. Record the exact Prime GO Engine OS and firmware version. The SC6000 5.0.4 research must not be silently treated as the target's firmware.
4. Resolve the CreateTrust field-number discrepancy from raw target capture bytes before changing the schema.
5. Acceptance remains a real Prime GO browse -> GetTrack -> GET /download/<encoded-key> -> sustained transfer -> playback test.

## Source limitations and references

The starter page is an index to a large reverse-engineering corpus. The key networking/gRPC findings are principally SC6000 5.0.4 static analysis plus live tests on two SC6000 units. They are strong technical references, not a replacement for the target Prime GO capture.

- https://deathcamel58.github.io/denon-reverse-engineering/starter-topic.html
- https://deathcamel58.github.io/denon-reverse-engineering/engine-networking.html
- https://deathcamel58.github.io/denon-reverse-engineering/engine-grpc.html
- https://deathcamel58.github.io/denon-reverse-engineering/engine-os.html
- https://github.com/DeathCamel58/denon-reverse-engineering
- https://github.com/icedream/go-stagelinq/tree/main/eaas
- Project capture: docs/research/denon-engine-remote-library-capture-2026-09-29.md