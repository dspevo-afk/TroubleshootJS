# Q30 LU repetition profile candidate

This fixture is a scratch-only, query-gated measurement candidate. Its purpose is to establish whether consecutive LU inputs repeat during a normal Q30 cold/warm coordinator qualification before considering any factorization reuse. It must not skip factorization, alter LU arithmetic, alter proof/cache behavior, change solver budgets, or omit solver convergence/finite checks, settling, or diagnostic work.

## Predeclared sample protocol

- Opt-in only: `tsjQ30LuRepetitionProfile=true`; the ordinary path omits every added report field and performs no matrix snapshot, comparison, allocation, or profile bookkeeping.
- The coordinator starts one independent profile for each candidate run (`cold`, then `warm`) immediately before starting that run's generation job, and closes it at the end of that exact job before owner cleanup. Terminal error, cancellation, startup failure, and successor-scope paths must close and clear any active profile.
- In `CirSim`, count every qualifying nonlinear LU factor call. Deterministically select a pair start every 1,021 calls within the same operation generation and analysis epoch. Snapshot the complete pre-factor finite matrix and its dimension on the selected start call; compare every matrix entry against the immediately following factor call in that same scope. No hashes stand in for full entry comparison. Cancel an open pair on epoch/generation/owner change, missing next call, failure, or profile end.
- Report total calls and analyzed sample positions, completed/matched/different/canceled pairs, dimensions and phase. Compare exact numeric values and IEEE signed-zero identity separately so `+0.0` versus `-0.0` is visible. The result is deterministic sampled evidence, not a population estimate.
- Reuse only a bounded matrix snapshot buffer owned by `CirSim`; no per-call allocation. All scratch contents are cleared/rebuilt at profile/epoch boundaries. This candidate contains no factor cache and does not reuse numerical results.

## Projection for the existing strict plan-4 reader

For profile-enabled native evidence only, create a metadata-only projection that removes exactly `cold.luRepetitionProfile` and `warm.luRepetitionProfile` from each report row before passing the remaining report to the existing strict plan-4 reader. Preserve every other field and the raw report hash; bind the projection to that raw hash and document the removed field paths. Do not change or relax the strict reader, expectations, or proof evidence. Query-off evidence is passed unmodified.

## Snapshot provenance and scope limitation

The source snapshot began from current working root `<REPO>` at HEAD `d825d0f6df667f470d5a8bcad74f1d607b206cfc`, not a clean-HEAD checkout. Before coordinator edits, all 1,519 files under `src` and `war` (excluding `WEB-INF/deploy`, build/target/out) matched the working root byte-for-byte after copying the one untracked but consumed `Q30NormalAcceptanceVerifier.java`. This count includes 19 generated files under `war/circuitjs1`; they are intentionally preserved and this old copy must not be used as a build source. Root will create a fresh build fixture after both instrumentation files freeze. The acceptance hook and verifier are preserved unchanged in this snapshot. Baseline coordinator SHA-256: `43f5aa1cfa85401aa048b2394261e266bab2697bef4f66fbf01cabb0fc86040c`. Copied untracked verifier SHA-256: `ea9b22dff24be42c296c78bff18b4eeac9533bbbeccf89667c1ff3e209dad5c9`.
## Coordinator integration

`Q30CoordinatorQualificationVerifier` recognizes only the browser query `tsjQ30LuRepetitionProfile=true`. It begins a phase profile (`cold` or `warm`) immediately before `startForDiagnosticCacheVerification`, ends it at completion of that exact job before owner cleanup, and also closes it on terminal startup/error/cancel/successor/cleanup-pending paths. The returned object is attached only to `cold.luRepetitionProfile` or `warm.luRepetitionProfile`. With the query absent, no profile API is called and neither row gets an additional property; the strict proof fields and report path remain unchanged.

## Audit artifacts

`src-source-audit.json` records the current-root source parity audit: all 1,131 unassigned files under `src` matched byte-for-byte; the two owned/assigned instrumentation files are recorded separately. The initial source/war parity check covered 1,519 files and confirmed the copied untracked acceptance verifier and acceptance hook, but it also included generated WAR output. Do not use this old copy for builds. Root will make the clean fixture from current production inputs after both assigned files freeze.

For profile-enabled evidence, preserve the original raw report bytes and SHA-256. Produce a separate projection by deleting only the existing `cold.luRepetitionProfile` and `warm.luRepetitionProfile` properties when present; do not alter any other value or sort/normalize the raw file. Bind the projection to the original report with a sidecar carrying that raw SHA-256 and the exact removed JSON paths. Feed only the projection to the existing strict reader and retain the original report and sidecar together. Query-off evidence is passed byte-for-byte without projection.
