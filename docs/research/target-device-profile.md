# Target Device Profile

**Instructions**: Populate from direct observation of the user's physical Prime GO. Do not assume values from documentation or other devices.

## Hardware
| Field | Value | Source | Verified |
|-------|-------|--------|----------|
| Exact Model | Prime GO / Prime GO+ | User report needed | ❌ |
| Hardware Revision | | | ❌ |
| Serial Number (partial) | | | ❌ |

## Firmware / Software
| Field | Value | Source | Verified |
|-------|-------|--------|----------|
| Engine OS Version | | Prime GO Settings -> About | ❌ |
| Firmware Build Version | | Prime GO Settings -> About | ❌ |
| Engine Desktop Version (if paired) | | | ❌ |
| Last Update Date | | | ❌ |

## Network Configuration
| Field | Value | Source | Verified |
|-------|-------|--------|----------|
| Connection Type | Wi-Fi / Ethernet (USB-C adapter?) | Observation | ❌ |
| Wi-Fi Band | 2.4 GHz / 5 GHz | Router/AP info | ❌ |
| SSID | | | ❌ |
| Security | WPA2/WPA3/Enterprise/Open | | ❌ |
| AP Isolation / Guest Network | Yes/No | Router config | ❌ |
| Mesh/Extender Present | Yes/No | Topology | ❌ |
| Prime GO IP Address | | Prime GO Settings -> Network | ❌ |
| Prime GO MAC Address | | Prime GO Settings -> Network | ❌ |
| Subnet | | | ❌ |
| Gateway | | | ❌ |
| DNS | | | ❌ |

## Phone (Server) Configuration
| Field | Value | Source | Verified |
|-------|-------|--------|----------|
| Phone Model | | | ❌ |
| Android Version | | Settings -> About | ❌ |
| Target SDK (app) | | Build config | ❌ |
| Wi-Fi Interface | | `ip addr` / `NetworkCallback` | ❌ |
| Phone IPv4 (Wi-Fi) | | | ❌ |
| Phone IPv6 (Wi-Fi) | | | ❌ |
| VPN Active | Yes/No | | ❌ |
| Hotspot Mode | Yes/No | | ❌ |

## Topology
```
[Prime GO] ---- Wi-Fi ---- [Router/AP] ---- Wi-Fi ---- [Android Phone]
                                    |
                              [Internet? VPN?]
```

## Capabilities to Verify
- [ ] Prime GO discovers network sources via "Source" menu
- [ ] Prime GO shows "Engine Remote Library" or similar source type
- [ ] Prime GO initiates trust/pairing flow
- [ ] Prime GO browses library hierarchy
- [ ] Prime GO loads and plays track from network source
- [ ] Prime GO supports MP3, WAV, FLAC, M4A/AAC
- [ ] Prime GO requests artwork
- [ ] Prime GO uses range requests for seeking

## Notes
- Record any deviation from SC6000/Prime 4+ behavior
- Note error messages displayed on Prime GO screen
- Capture timestamps for correlation with packet captures

## Protocol Evidence (from research)

**Confirmed from SC6000/Engine OS 5.0.4 reverse engineering (deathcamel58):**

- Discovery: UDP 11224, "EAAS\x01\x00" request, structured response with token + grpc:// URL
- Transport: gRPC over cleartext HTTP/2 (no TLS)
- Port 50010 = enginesync.v1 ONLY (enginelibrary.v1, networktrust.v1 NOT on 50010)
- Trust: NetworkTrustService.CreateTrust with Ed25519 PK + device_name, interactive approve/deny
- Library: enginelibrary.v1.EngineLibraryService with GetLibraries, GetLibrary, GetTracks, GetTrack, SearchTracks
- File Transfer: HTTP on port 50020, GET /download/{path}
- Required gRPC metadata: `uuid: <host-uuid>` + host must be in discovered-host table

**Prime GO Specific (UNVERIFIED - needs hardware testing):**

- Same discovery protocol?
- Same gRPC port for library service?
- Same trust flow?
- Same file transfer protocol?
- Engine OS version compatibility?
## Current physical-test snapshot — 2026-09-28

The following values are directly observed from the user's latest 2MIMICDJ2 diagnostic report (Evidence class A):

- Target hardware: Denon DJ Prime GO.
- Prime GO LAN IPv4: 10.122.26.191.
- Android server LAN IPv4: 10.122.26.146.
- Android Wi-Fi interface reported by the server: wlan1.
- EAAS discovery peer/source port observed: UDP 44829 on Prime GO.
- EAAS discovery destination: UDP 11224 on the Android server.
- Discovery exchange: 7 requests / 7 replies during the observed test window.
- Advertised gRPC endpoint: grpc://10.122.26.146:50010.
- Inbound gRPC transport/RPC observed by the app: none.
- Android HTTP 50020 bind: EPERM. 50021 and 50022 also EPERM; 50019, 50100 and 60000 bind successfully.
- Prime GO firmware/Engine OS exact version: NOT YET RECORDED.
- Android version / device model: NOT READ FROM THIS DIAGNOSTIC REPORT.

This entry deliberately does not infer firmware-specific protocol behavior from SC6000 or older Engine DJ references.


## Cross-device/version caution — 2026-11-06

DeathCamel58's Engine Networking / Engine gRPC research is principally from SC6000 hardware running Engine OS 5.0.4. On those units TCP/50010 served EngineSync only; the separate EAAS gRPC server's external port was not resolved. The same research did not observe TCP/50020 active.

Do not substitute those SC6000 findings for Prime GO facts. The direct Prime GO capture dated 2026-09-29 shows EngineLibrary RPC on TCP/50010 and HTTP audio download on TCP/50020. This target-specific capture remains authoritative for this project.

The Prime GO's exact Engine OS/firmware version is still missing from the profile and must be recorded before making any version-specific protocol change.


## 2026-09-30 physical test snapshot — Android host + Prime GO

Directly observed in the latest supplied 2MIMICDJ2 diagnostics (Evidence A):
- Prime GO LAN IPv4: `10.93.187.191`.
- Android phone LAN IPv4: `10.93.187.7`, Wi-Fi interface `wlan1`.
- Prime GO sends EAAS discovery from UDP source port `38954` to destination UDP `11224`.
- App replies 6/6 and advertised `grpc://10.93.187.7:50100`.
- Android refused bind to TCP 50010/50011 with EPERM; 50100 and 50110 were permitted.
- No external gRPC transport/RPC reached the phone in that test.
- The old `Raw TCP accepts: 1` was the phone's own self-test. The old `Prime GO TCP/50010: CONNECTED` label described an outbound probe from the phone to the Prime GO's TCP/50010, not an inbound Prime GO-to-phone connection.
- Exact Prime GO Engine OS/firmware build and router/AP isolation settings remain unknown.

These IP addresses are a test snapshot and may change on a later Wi-Fi session.
