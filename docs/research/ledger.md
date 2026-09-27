# Evidence Ledger

**Format**: Date | Source | Scope/Device/Version | Observation | Evidence Class | Confidence | Reproduction Method | Consequence for Implementation | Open Question

---

## 2026-09-27 | C-LINE-MASTER-PROMPT | Project charter | Mission: Build Android app as local-network server for Denon DJ Prime GO (Engine OS). Phone stores music -> app exposes over LAN -> Prime GO discovers/connects/trusts/browses -> selects track -> obtains audio bytes -> plays locally. | D | High | N/A (charter) | Architecture must be determined from hardware evidence, not inherited from prior projects. | None (charter)

## 2026-09-27 | C-LINE-MASTER-PROMPT | Project charter | Repository: MiguelDuval/2MIMICDJ2, branch: agent/clean-slate. Never develop on main. Clean-sheet approach. | D | High | N/A | All implementation from scratch. | None

## 2026-09-27 | C-LINE-MASTER-PROMPT | Engineering principles | Hardware is final authority. When disagreement between any source and actual Prime GO behavior, Prime GO wins. Record disagreements. | D | High | N/A | All protocol decisions must be validated against actual hardware. | How to capture Prime GO traffic?

## 2026-09-27 | C-LINE-MASTER-PROMPT | Engineering principles | Do not inherit architecture by accident. Stack choice must consider: what hardware speaks, Android reliability, protocol difficulty, testability, binary size, failure behavior, instrumentability, Wi-Fi/screen-lock survival. | D | High | N/A | Technology stack TBD after protocol investigation. | What does Prime GO actually speak?

## 2026-09-27 | C-LINE-MASTER-PROMPT | Engineering principles | Do not treat old port numbers as facts (UDP 11224, TCP 50010, TCP 50020, UDP 51337 are hints only). | D | High | N/A | No hardcoded ports until verified. | Which ports does Prime GO actually use?

## 2026-09-27 | C-LINE-MASTER-PROMPT | Engineering principles | Separate evidence from hypotheses. Maintain ledger with evidence classes A-E. Never silently upgrade E to A. | D | High | N/A | Ledger discipline required. | None

## 2026-09-27 | C-LINE-MASTER-PROMPT | Target reconnaissance | Must determine before protocol implementation: exact hardware model, product variant (Prime GO vs Prime GO+), Engine OS version, firmware/build version, network connection type, phone Android version, phone Wi-Fi interface/IP, router/AP topology, VPN/hotspot/guest Wi-Fi/mesh status. | D | High | N/A | Need physical device access to populate. | What is the user's actual Prime GO configuration?

## 2026-09-27 | C-LINE-MASTER-PROMPT | Official docs | Search current Denon DJ / Engine documentation for: Prime GO support, firmware/software relationships, Engine Remote Library behavior, network requirements, device discovery, trust/pairing, supported media formats, network requirements. | D | Medium | Web search | Official docs may not expose protocol but define product behavior. | Are official docs current for target firmware?

## 2026-09-27 | deathcamel58.github.io | Reverse engineering | Engine networking: https://deathcamel58.github.io/denon-reverse-engineering/engine-networking.html | D | Medium | Web review | Reference for SC6000 Engine OS 5.0.4 — warns that public assumptions about ports/services can diverge from actual device behavior. | Does Prime GO behave like SC6000?

## 2026-09-27 | deathcamel58.github.io | Reverse engineering | Engine gRPC: https://deathcamel58.github.io/denon-reverse-engineering/engine-grpc.html | D | Medium | Web review | gRPC service definitions for Engine OS — but scope/version must be verified. | Which gRPC services (if any) does Prime GO use?

## 2026-09-27 | github.com/chrisle/StageLinq | Reference implementation | StageLinq protocol documentation, EAAS code, tests, Wireshark material, supported-device logic. | D | Medium | Source review | Prior art for EAAS/StageLinq. Authorized to disagree after evidence. | How much applies to Prime GO?

## 2026-09-27 | github.com/icedream/go-stagelinq | Reference implementation | EAAS/storage implementation and tests. | D | Medium | Source review | Go implementation of StageLinq/EAAS. | Protocol compatibility with Prime GO?

## 2026-09-27 | github.com/andyscuff/DenonDJ-Eaas-Server | Reference implementation | Source code, issues, commits, protocol assumptions. | D | Medium | Source review | Another EAAS server implementation. | Assumptions validated on Prime GO?

## 2026-09-27 | C-LINE-MASTER-PROMPT | Protocol investigation matrix | Must create matrix covering: Link, Discovery, Trust, Transport, RPC, Library, Tracks, Track Retrieval, File Transfer, Failure, Network, Lifecycle layers with specific questions. | D | High | N/A | Matrix must be filled from evidence, not memory. | Need to populate from captures/research.## 2026-09-27 | C-LINE-MASTER-PROMPT | Capture strategy | Preferred: phone + Prime GO on same LAN, start Engine Remote Library workflow, capture discovery, source selection, trust, library browse, track selection, track transfer. Save sanitized captures. | D | High | N/A | Need capture capability. | What capture tools are available to user?
