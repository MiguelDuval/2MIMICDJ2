# DeathCamel58 reverse-engineering findings

Reviewed: 2026-11-06  
Primary index: https://deathcamel58.github.io/denon-reverse-engineering/starter-topic.html  
Source repository: https://github.com/DeathCamel58/denon-reverse-engineering  
Reviewed source commit: `0f7966516609e3de2b71e4aef21625bd8d94c57c`  
Relevant pages: Engine Networking, Engine gRPC, Engine Library, Engine OS, UART, Updating.

## Executive conclusion

The source is a substantial reverse-engineering corpus, not one Prime GO protocol specification. Its detailed networking and gRPC observations are principally from two SC6000 / JP13 units running Engine OS 5.0.4. The repo also contains a separate Prime GO / JP11 firmware and UART profile.

Our own known-good Prime GO packet-list captures are more authoritative for this project's Remote Library endpoint: they show the Prime GO connecting to a Windows Engine Desktop server on TCP 50010 for Engine Library gRPC and TCP 50020 for HTTP audio downloads. The SC6000 finding that its own TCP 50010 serves EngineSync is not contradictory: it describes a different device's server-side listeners, while our capture describes Prime GO acting as a client to a PC Remote Library server.

Do not copy SC6000 5.0.4 port assignments onto Prime GO without a matching target capture.

## Findings that fill previous gaps

### 1. EngineSync, EngineLibrary, and NetworkTrust are distinct services

DeathCamel identifies `enginesync.v1.EngineSyncService`, `enginelibrary.v1.EngineLibraryService`, and `networktrust.v1.NetworkTrustService` as separate gRPC services in Engine. On the tested SC6000 5.0.4 devices, TCP 50010 exposed EngineSync; EngineLibrary and NetworkTrust returned `UNIMPLEMENTED` on that particular listener. The externally exposed port for the separate EAAS gRPC server was not resolved.

For the captured Prime GO Remote Library client session, the PC server's TCP 50010 definitely carried EngineLibrary methods. These are different sides of the connection and different device/server configurations. The app's service registration on one listener remains based on the Prime GO capture plus the Go reference, not a universal Engine OS claim.

EngineSync's `ExchangeTimestamps` and `UpdateSyncLeadAudioInfo` paths use a `uuid` gRPC metadata value and a host discovered into its own host table. That is an EngineSync-specific rule. It is not evidence that every EngineLibrary request must carry the same metadata. Do not add a global UUID gate to the Remote Library service.

### 2. EAAS HTTP and the license callback are different servers

The SC6000 binary contains an EAAS HTTP server based on cpp-httplib, registering `/ping` and `/download/(<)(.*)(>)` for file transfer. The separate hand-written license activation callback listens on TCP 41401 and serves `/authorise?code=`; 41401 is not the audio download port.

DeathCamel did not observe an active TCP 50020 EAAS listener on the tested SC6000 5.0.4 units. Our Prime GO packet capture directly shows a PC Remote Library host serving TCP 50020: the Prime GO requests `GET /download/<URL-encoded-key>`, receives HTTP 200, and then reads a large binary audio stream. Keep the target-specific 50020 behavior.

### 3. Discovery, trust, and session establishment are separate

The EAAS request is six bytes: ASCII `EAAS` followed by `01 00`. The reference beacon replies with a 16-byte token, length-prefixed UTF-16BE hostname, UTF-8 `grpc://host:port` URL, UTF-16BE software version, a one-byte marker and UTF-16BE extra string. The Go reference selects a reply address based on the receiving interface/subnet.

The two Prime GO captures show that the observed steps vary:
- `denonlog.txt`: HTTP `GET /ping` -> HTTP 200, then gRPC `CreateTrust`, then EventStream/GetLibrary/GetTracks.
- `denonlog2.txt`: EAAS discovery followed by EventStream/GetLibrary/GetTracks/GetTrack and a separate HTTP 50020 download; this capture contains no `CreateTrust` call and no `/ping`.

Therefore neither `/ping` nor `CreateTrust` should be assumed to repeat in every browse session. Discovery proves only UDP advertisement, not a fully established library session.

The EAAS token is an identity marker: the Go reference preserves it across a server lifetime so Engine can identify a returning library. This app now persists its 16-byte token in app-private preferences and restores it when the Service is recreated.

### 4. CreateTrust schema is still not resolved for Prime GO

The recovered request shows:
- field 1: `ed25519_pk`, string;
- field 3: `device_name`, string;
- field 2: a varint. The Go reference names it `wireguard_port`, but DeathCamel's SC6000 binary analysis does not independently confirm that semantic name.

There is a response discrepancy:
- icedream/go-stagelinq models a oneof: granted / denied / busy at fields 1 / 2 / 3;
- DeathCamel's SC6000 5.0.4 reconstruction reports one message-valued field at number 3, but explicitly marks its nested outcome mapping uncertain.

These claims must not be silently merged. The available Prime GO packet-list exports show the method name and frame sizes but not the raw protobuf DATA bytes. Consequently they cannot resolve the response tags. The current app retains the Go reference encoding and now logs only serialized sizes plus field-number/wire-type summaries for each request and response; it never logs the public key or field values. The next raw payload capture can compare the two encodings without exposing secrets.

### 5. Engine Library RPC method kinds and the recovered schema

DeathCamel's reconstructed SC6000 5.0.4 service declares:
- Unary: GetCredentials, GetLibraries, GetLibrary, GetTrack, GetSearchFilters, GetHistorySessions, GetHistoryPlayedTracks, PutEvents.
- Server streaming: GetTracks, SearchTracks, EventStream.

The methods and RPC kinds are useful reference facts, but some request fields and response-message semantics remain marked UNKNOWN / UNCONFIRMED. In particular, the reconstructed GetTracksRequest confirms message/varint/string wire types but marks some fields' meanings tentative. The Go schema labels those fields as page_size, sort, list_type and filters. This app follows the Go reference's names while treating it as a compatible schema hypothesis, not an official vendor contract.

Similarly, DeathCamel's reconstructed GetTrackResponse shows three message-valued fields but does not settle their mapping. The Go reference maps these as blob, performance_data and metadata. The packet-list capture reports a 1,075-byte GetTrack response DATA frame but does not contain the decoded payload. Do not change that field mapping without a raw protobuf payload.

The app now has server-streaming descriptors for GetTracks/SearchTracks/EventStream and emits all track results in page-sized batches instead of dropping everything after the first page. Tests check the generated method descriptors and batch behavior.

### 6. High TCP ports may be short-lived service-directory sockets

On SC6000 5.0.4, roughly a dozen high TCP ports appeared while network library sharing was active and disappeared within seconds. They did not respond to an HTTP/2 preface and were suspected to belong to `airNetworkExchange` directory/service sockets. That is a hypothesis about the SC6000 observations, not proof that these are Prime GO EAAS endpoints.

The app's scan is diagnostic-only: it probes ports 30000–47000 and fingerprints any open TCP port with a read-only `EngineLibrary.GetLibraries` request. It does not automatically choose or advertise a scanned port.

### 7. UDP 51337 is not a universal replacement for EAAS discovery

DeathCamel's Engine Networking captures did not identify useful `airD` discovery traffic on UDP 51337, while EAAS UDP 11224 was active. However, its separate JP13 live socket snapshot lists three UDP 51337 listeners. These observations are not enough to claim 51337 is universally absent or universally the discovery protocol. The Prime GO packet capture directly confirms EAAS UDP 11224; it remains the project's discovery path.

Ableton Link multicast at `224.76.78.75:20808` carries tempo/phase synchronization and is unrelated to Remote Library browsing or audio file delivery.

### 8. Engine OS version and hardware-family differences matter

The Engine OS page distinguishes the Prime GO / JP11 platform from the SC6000 / JP13 platform. The Prime GO table lists the Buildroot-era releases through 4.3.4 and the Yocto/OpenEmbedded Scarthgap transition beginning at 5.0.0; 5.0.x belongs to a different software/kernel family from the SC6000 observations. The exact version/build running on the user's Prime GO is still not recorded in this project.

The UART page documents a Prime GO serial header at 115200 baud, which can be used to read boot/runtime information if a suitable adapter is available. This project does not need firmware flashing, bootloader unlocking, or destructive update commands to complete the Remote Library implementation.

## Corrected project assumptions

| Old simplification | Corrected understanding |
|---|---|
| TCP 50010 universally hosts Engine Library and Trust | Observed for the PC Remote Library server in the Prime GO capture; SC6000 5.0.4's own 50010 listener was EngineSync only. |
| Every session starts with CreateTrust | Present in `denonlog.txt`, absent in `denonlog2.txt`; likely conditional or previously trusted. |
| Every session starts with /ping | Present in `denonlog.txt`, absent in `denonlog2.txt`; not universal. |
| Discovery proves a usable library session | It proves UDP request/reply and endpoint advertisement only. |
| All Engine Library fields are fully recovered | Method kinds and major shapes are known; some field semantics/mappings remain unconfirmed. |
| The CreateTrust response schema is settled | Go and SC6000 reconstructions disagree; Prime GO payload bytes are needed. |
| An open high port is necessarily an EAAS listener | SC6000 research suggests some high ports may be transient non-gRPC directory sockets. |

## Engineering actions completed in this branch

1. Kept the Android port workaround and gateway design, without claiming the bridge has been hardware-validated.
2. Corrected GetTracks, SearchTracks and EventStream to server-streaming RPCs.
3. Added page-sized response batching without dropping tracks after the first page.
4. Corrected GetTrack URL generation to use the actual paired HTTP listener in direct mode and TCP 50020 in bridge mode.
5. Added a stable persisted EAAS identity token.
6. Split local self-tests from external peer counters so local traffic can no longer masquerade as Prime GO.
7. Added a read-only EngineLibrary.GetLibraries probe for Prime GO TCP 50010 and transient high-port fingerprints.
8. Added privacy-safe CreateTrust protobuf wire-shape diagnostics.
9. Documented the remaining target-specific unknowns and the required end-to-end hardware acceptance test.

## Remaining acceptance criteria

A real Prime GO test with the compatibility gateway must confirm:
1. Prime GO source discovery reaches the gateway's advertised endpoint;
2. external TCP 50010 accepts HTTP/2 and the real device invokes NetworkTrust/EngineLibrary as required;
3. GetLibrary/GetTracks show the phone's media inventory;
4. GetTrack returns a usable track URL;
5. Prime GO makes HTTP 50020 GET /download and receives the complete file;
6. the track loads and plays; seeking/range behavior is separately tested.

## Sources and references

- Starter index: https://deathcamel58.github.io/denon-reverse-engineering/starter-topic.html
- Engine Networking: https://deathcamel58.github.io/denon-reverse-engineering/engine-networking.html
- Engine gRPC: https://deathcamel58.github.io/denon-reverse-engineering/engine-grpc.html
- Engine Library: https://deathcamel58.github.io/denon-reverse-engineering/engine-library.html
- Engine OS: https://deathcamel58.github.io/denon-reverse-engineering/engine-os.html
- UART: https://deathcamel58.github.io/denon-reverse-engineering/uart.html
- Updating: https://deathcamel58.github.io/denon-reverse-engineering/updating.html
- Pinned source: https://github.com/DeathCamel58/denon-reverse-engineering/tree/0f7966516609e3de2b71e4aef21625bd8d94c57c
- Go EAAS reference: https://github.com/icedream/go-stagelinq/tree/main/eaas
- Prime GO captures: `denonlog.txt`, `denonlog2.txt`, summarized in `docs/research/denon-engine-remote-library-capture-2026-09-29.md`
