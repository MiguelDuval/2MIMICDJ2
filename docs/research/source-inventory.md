# Source Inventory

## Official / Product Behavior Sources

| Source | URL | Scope/Device/Version | Access Date | Notes |
|--------|-----|---------------------|-------------|-------|
| Engine DJ Support | https://support.enginedj.com/ | General Engine OS | 2026-09-27 | Official support portal |
| Engine DJ Downloads/Release Notes | https://enginedj.com/downloads | Firmware/software releases | 2026-09-27 | Check for Prime GO firmware |
| Denon DJ Prime GO Product Page | https://www.denondj.com/prime-go/ | Prime GO hardware specs | 2026-09-27 | Hardware specifications |

## Reverse Engineering Sources

| Source | URL | Scope/Device/Version | Access Date | Evidence Class | Notes |
|--------|-----|---------------------|-------------|----------------|-------|
| DeathCamel58 reverse-engineering corpus | https://deathcamel58.github.io/denon-reverse-engineering/starter-topic.html | Pinned source commit 0f7966516609e3de2b71e4aef21625bd8d94c57c; networking/gRPC chiefly SC6000/JP13 Engine OS 5.0.4; separate Prime GO/JP11 OS/UART pages | 2026-11-06 | D | Device/version scope is mandatory. See docs/research/deathcamel58-reverse-engineering-findings-2026-11-06.md. |
| Engine Networking | https://deathcamel58.github.io/denon-reverse-engineering/engine-networking.html | SC6000/JP13 Engine OS 5.0.4 live sockets + binary analysis | 2026-11-06 | D | EngineSync observed on SC6000 TCP50010; EAAS service port unresolved; transient high ports; separate license callback 41401. |
| Engine gRPC Reference | https://deathcamel58.github.io/denon-reverse-engineering/engine-grpc.html | SC6000/JP13 Engine OS 5.0.4 reconstructed schema | 2026-11-06 | D | GetTracks/SearchTracks/EventStream are server-streaming in the reconstruction; some field semantics remain uncertain. |
| Engine Library / OS / UART / Updating | https://deathcamel58.github.io/denon-reverse-engineering/engine-library.html ; https://deathcamel58.github.io/denon-reverse-engineering/engine-os.html ; https://deathcamel58.github.io/denon-reverse-engineering/uart.html ; https://deathcamel58.github.io/denon-reverse-engineering/updating.html | Engine Library schema reconstruction plus Prime GO JP11 and SC6000 JP13 platform reference | 2026-11-06 | D | Use the OS page to distinguish JP11 vs JP13; UART may identify the exact Prime GO build. No firmware flashing/unlocking is part of this project. |

## Open-Source Protocol Implementations

| Source | URL | Language | Scope/Device/Version | Access Date | Evidence Class | Notes |
|--------|-----|----------|---------------------|-------------|----------------|-------|
| StageLinq (chrisle) | https://github.com/chrisle/StageLinq | C#/.NET | EAAS/StageLinq protocol, multiple devices | 2026-09-27 | D | Protocol docs, EAAS code, tests, Wireshark dissectors |
| go-stagelinq (icedream) | https://github.com/icedream/go-stagelinq | Go | EAAS/storage implementation, tests | 2026-09-27 | D | Go implementation |
| DenonDJ-EAAS-Server (andyscuff) | https://github.com/andyscuff/DenonDJ-Eaas-Server | Python | EAAS server implementation | 2026-09-27 | D | Issues, commits, protocol assumptions |

## Android Platform References

| Source | URL | Topic | Access Date |
|--------|-----|-------|-------------|
| ConnectivityManager | https://developer.android.com/reference/android/net/ConnectivityManager | Network management | 2026-09-27 |
| ConnectivityManager.NetworkCallback | https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback | Network callbacks | 2026-09-27 |
| Network | https://developer.android.com/reference/android/net/Network | Network binding | 2026-09-27 |
| Android 14 FG Service Requirements | https://developer.android.com/about/versions/14/changes/fgs-types-required | Foreground service types | 2026-09-27 |
| Android 15 Behavior Changes | https://developer.android.com/about/versions/15/behavior-changes-15 | Android 15 changes | 2026-09-27 |
| Android Storage | https://developer.android.com/training/data-storage | Scoped storage, MediaStore | 2026-09-27 |

## gRPC References

| Source | URL | Topic | Access Date |
|--------|-----|-------|-------------|
| gRPC Java | https://github.com/grpc/grpc-java | Java/Kotlin gRPC implementation | 2026-09-27 |

## Historical Context (Treat as Hypotheses Only)

| Source | URL | Scope | Notes |
|--------|-----|-------|-------|
| MiguelDuval/MimicDJ (older) | https://github.com/MiguelDuval/MimicDJ | Previous implementation attempt | Historical hypotheses: UDP 11224, gRPC 50010, HTTP 50020, EAAS response structure, track URL format, automatic trust, MediaStore-only, exact protobuf, folder playlist hierarchy. Do not copy without validation. |

## Research Rule
Every source above is evidence with a particular scope. The agent must record the device/version to which each statement applies and must not silently generalize from one Engine OS device or firmware version to another.