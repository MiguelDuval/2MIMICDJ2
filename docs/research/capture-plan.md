# Packet Capture Plan

## Objective
Capture complete Prime GO network traffic for: discovery, source selection, trust/pairing, library browse, track selection, track transfer.

## Capture Methods (Priority Order)

### 1. Router/AP Span Port / Mirror Port (Best)
- **Pros**: Captures all traffic non-intrusively, both directions, no device modification
- **Cons**: Requires managed switch or router with port mirroring
- **Tools**: Wireshark on capture machine connected to mirror port

### 2. Wireless Capture (Monitor Mode)
- **Pros**: Captures all Wi-Fi traffic on channel
- **Cons**: Requires compatible Wi-Fi adapter + monitor mode, encryption keys for decryption
- **Tools**: Wireshark + AirPcap / Linux laptop with monitor mode NIC
- **Decryption**: Need PMK (WPA2) or SAE (WPA3) — capture 4-way handshake

### 3. Android Phone Capture (Host Capture)
- **Pros**: Directly on server device, no extra hardware
- **Cons**: Only captures traffic to/from phone, misses Prime GO -> router broadcast
- **Tools**: 
  - `tcpdump` via `adb shell` (requires root)
  - Android `Pcapture` / `NetworkCapture` APIs (no root, limited)
  - Custom VPN-based capture (e.g., PCAPdroid)
- **PCAPdroid**: https://github.com/emanueleg/PCAPdroid — no root, creates VPN interface, captures all app traffic

### 4. Intermediate Proxy / MITM
- **Pros**: Full visibility, can modify traffic for testing
- **Cons**: May break certificate pinning, Prime GO may not trust proxy CA
- **Tools**: mitmproxy, custom TCP proxy

### 5. Custom Diagnostic App (Our Milestone 1)
- **Pros**: Tailored logging, survives screen lock, integrates with our diagnostics
- **Cons**: Only sees traffic reaching our sockets
- **Implementation**: Raw socket listeners + `NetworkCallback` + packet logging

## Capture Scenarios (In Order)

| Scenario | Trigger | Expected Packets | Duration |
|----------|---------|------------------|----------|
| **S1: Idle/Background** | Prime GO on, Source menu closed | Periodic beacons? | 60s |
| **S2: Open Source Menu** | User opens Source on Prime GO | Discovery query/response | 10s |
| **S3: Select Engine Remote Library** | User selects our source | Trust handshake? | 10s |
| **S4: Browse Library** | User navigates playlists/tracks | Library RPC calls | 30s |
| **S5: Select Track** | User loads track to deck | GetTrack + File Transfer | 30s |
| **S6: Play/Seek** | User plays, seeks | Range requests? | 30s |
| **S7: Network Disruption** | Toggle Wi-Fi / Airplane mode | Reconnection behavior | 30s |

## Capture Filters (Wireshark/tcpdump)

```bash
# All traffic to/from Prime GO IP
host <PRIME_GO_IP>

# Discovery ports (historical hints)
udp port 11224 or udp port 51337

# Library/File ports (historical hints)
tcp port 50010 or tcp port 50020

# mDNS/DNS-SD
udp port 5353

# SSDP
udp port 1900

# HTTP/HTTPS
tcp port 80 or tcp port 443 or tcp port 8080 or tcp port 8443
```

## Sanitization Rules (Before Committing)
- Remove/replace: MAC addresses, serial numbers, real IPs (use 192.168.x.x), Wi-Fi passwords
- Keep: Protocol structure, packet timing, payload formats, port numbers, sequence numbers
- Anonymize: User metadata in track names (replace with "Track 1", "Artist 1")

## Capture Storage
- Location: `docs/protocol/fixtures/captures/`
- Naming: `capture-<scenario>-<date>-<sequence>.pcapng`
- Companion: `capture-<scenario>-<date>-<sequence>.notes.md`

## Analysis Tools
- Wireshark (primary)
- `tshark` for automation
- Custom Python/Go parsers for protocol-specific decoding
- Our diagnostic app's built-in hex dumps

## Reproduction Method
Each capture must be accompanied by:
1. Scenario description
2. Prime GO firmware version
3. Phone/app version
4. Network topology diagram
5. Step-by-step reproduction steps
6. Expected vs observed behavior