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