# Q30 source and process identity evidence

Captured 2026-10-02T12:22:52.584010+00:00. Candidate source identity `a43f8165921f08c309636d2bf1a971905fdbc9382885c0708788d79744e1fb51` at base `d37c3b3b8581abdeaa9b88b7bfb2349cff958d31`.

**This packet does not claim Q30 acceptance.** The bound source audit says the normal-player Q30 catalog is disabled.

## Evidence in scope

- Source audit: PASS for 1354 current files; all snapshot hashes were rechecked against the repository.
- Passive Edge/CDP transport: PASS for 21 scenarios in 59.105s; all four cleanup receipts pass.
- Compiled-host process ownership: PASS in 5.893s; both worker/Edge trees absent at final inventory.
- Normal-screen-host process ownership: PASS in 5.924s; both worker/Edge trees absent at final inventory.
- Seven parser negative fixtures per host are supplemental checks, not separate acceptance gates.
- The first WindowsApps-alias attempt and rejected wrapper prepare are retained as failures.

Exact drafthosts, the helper copies used by each canary, transport fixtures, JSON/text receipts, and empty worker stdout/stderr files are gzip-packed. Each entry records raw, portable payload, and compressed SHA-256 values. Paths are sanitized. Browser profiles and system-wide process lists are excluded.

The packet reports no full native, cold77, compiled31, menu/replay, archive, visible-input, or parent-acceptance result. These gates remain separate from this evidence package.
