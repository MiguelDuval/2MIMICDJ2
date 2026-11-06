# Research Status

## Overview

This directory contains the evidence-led research for 2MIMICDJ2. Protocol implementation decisions must remain traceable to `ledger.md`.

## Structure

- `ledger.md` — evidence ledger with device/version scope and confidence
- `source-inventory.md` — consulted sources with pinned versions and scope
- `hypothesis-matrix.md` — questions, findings, and remaining gaps
- `target-device-profile.md` — actual Prime GO hardware/firmware/network profile
- `capture-plan.md` — packet capture strategy
- `deathcamel58-reverse-engineering-findings-2026-11-06.md` — source findings and implementation consequences
- `denon-engine-remote-library-capture-2026-09-29.md` — direct target Prime GO Remote Library capture sequence

## Evidence classes

- **A** — directly captured/observed on the target Prime GO
- **B** — reproduced against another Engine OS device
- **C** — reproduced against a known compatible reference implementation
- **D** — published reverse engineering, source code or documentation
- **E** — hypothesis or inference

Never silently upgrade E to A. Keep device and firmware scope beside every D/B finding.

## Current phase

**Implementation / hardware validation.** Discovery and the Prime GO Remote Library transport sequence are captured. Android's standard-port bind restriction is measured. The app implements the Engine Library service and a LAN compatibility gateway, but the gateway still requires a real Prime GO end-to-end test.

## Remaining evidence gaps

1. Record the Prime GO's exact Engine OS/build.
2. Capture raw HTTP/2 DATA payload bytes (pcapng) if Trust/GetTrack protobuf field mappings must be settled.
3. Validate browse -> GetTrack -> HTTP download -> playback through the gateway.
4. Test seeking/range requests after basic playback works.
