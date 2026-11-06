# Research Status

## Overview
This directory contains the evidence-led research for the 2MIMICDJ2 project. All protocol implementation decisions must be traceable to entries in `ledger.md`.

## Structure
- `ledger.md` — Evidence ledger with Date, Source, Scope, Observation, Evidence Class, Confidence, Reproduction Method, Consequence, Open Questions
- `source-inventory.md` — Catalog of all sources consulted with scope/version annotations
- `hypothesis-matrix.md` — Protocol investigation matrix (discovery, transport, RPC, library, track retrieval, etc.)
- `target-device-profile.md` — Actual Prime GO hardware/firmware/network profile
- `capture-plan.md` — Packet capture strategy and tools

## Evidence Classes
- **A** — Directly captured/observed on the target Prime GO
- **B** — Reproduced against another Engine OS device
- **C** — Reproduced against a known compatible reference implementation
- **D** — Published reverse engineering / source code / documentation
- **E** — Hypothesis or inference

**Rule**: Never silently upgrade E to A.

## Current Phase
**Phase A — Research** (in progress)

## Next Steps
1. Complete target device profile (hardware model, Engine OS version, firmware, network topology)
2. Populate source inventory with all consulted references
3. Build hypothesis matrix from evidence (not memory)
4. Design capture plan
5. Create protocol probe tools