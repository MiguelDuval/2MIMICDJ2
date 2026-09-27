# Agent operating rules

## Repository
- Repository: `MiguelDuval/2MIMICDJ2`
- Active development branch: `agent/clean-slate`
- Never develop directly on `main`.
- Never force-push, reset, or rewrite `main`.
- Keep commits small and meaningful; push after each stable milestone so GitHub Actions produces a build/result.

## Product goal
Build and validate a real Android-side local-network server that can interoperate with the connected Denon DJ Prime GO / Engine OS hardware. The exact protocol and architecture are not predetermined.

## Evidence rule
Hardware behavior and packet captures from the target unit outrank assumptions, old code, forum claims, and inferred protocol diagrams. Maintain a research log that separates:
1. observed directly on the target hardware;
2. reproduced against another Engine OS device;
3. reproduced against a reference implementation;
4. documented by a source;
5. hypothesis.

## Legacy code
The older `MiguelDuval/MimicDJ` repository is historical context only. Do not copy its architecture or protocol assumptions without independently validating them.

## CI
Every non-main push is intended to trigger GitHub Actions automatically. Build artifacts should be downloadable from the workflow run.

## Quality
Do not add unrelated product features. Prioritize discoverability, trust/session establishment, library browse/search, track metadata, and reliable file loading/transfer. Add diagnostics before adding complexity.
