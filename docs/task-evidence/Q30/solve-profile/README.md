# Solve-profile measurement — intermediate evidence

**No optimization or acceptance result. Q30 remains BLOCKED.** This is one private, seed-10014 off/profile diagnostic pair; the sampler was not integrated into production. It does not establish a speedup, zero overhead, or normal acceptance.

## Timings

| Run | Mode | Cold ms | Proof ms | Routing ms | Host seconds | Cleanup seconds |
|---|---|---:|---:|---:|---:|---:|
| solve-profile-01-off-10014 | off | 99730 | 71015 | 22466 | 131.656 | 1.024 |
| solve-profile-02-profile-10014 | profile | 99729 | 70837 | 22604 | 131.594 | 0.888 |

The off/profile cold totals were 99,730/99,729 ms; proof was 71,015/70,837 ms and routing 22,466/22,604 ms. This near-identical pair is not proof of zero instrumentation overhead.

The cold profile sampled 727 starts, completed 726 adjacent-pair comparisons (30 equal and 696 different), and read 1453 input matrices across sizes 78–90. It measured 1453 selected `lu_solve` calls totaling 22.300 ms among 742,391 factor calls. A rough per-sample-mean projection is 11.394 s across all factor calls; this is extrapolation only, not measured elapsed time or expected savings. The combined strict lower/upper triangles were 89.14% exact zeros by structural census, not observed skipped arithmetic. `performance.now` granularity and sparse selection limit interpretation. The 38.4 ms overhead measures sample capture/compare inside brackets; it excludes per-factor window/counter/modulo, compilation, initial RHS, and strict-triangle census/traversal.

Each cold report recorded 390 hypothesis work units across five hypotheses. The later warm report is a separate proof-cache hit with one hypothesis work unit; this does not imply numerical factor reuse. The pair preserved request and report fields outside declared timing/profile paths. Both strict plan-4 readers passed 43 negative checks each; the root validator passed all 79 metadata-negative cases. Root focused native/GWT/compiled-canary gates also passed. The source audit remains historically `SOURCE_ONLY_FIXTURE_PREPARED_NOT_VALIDATED`; the gate receipt binds its exact hash.

The frozen measurement plan did not contain an explicit package-count assertion; the executed seed-10014 reports identify 40 packages and passed the strict readers. Treat that as fixture evidence, not broader cohort coverage.

Raw application reports are gzip-compressed byte-for-byte. Metadata/log path redactions, when present, are enumerated with original/stored hashes in `provenance/archive-transforms.json`. The five exact source overlays and complete 1,341-input hash inventory reconstruct on top of [owned-zero-row-scan](../owned-zero-row-scan/README.md). No profile cache, compiled WAR, or browser profile is included.

`inventory.json` covers every stored file except itself and `inventory.sha256`; gzip entries include decompressed hashes. The detached SHA-256 checks the inventory bytes; it is not keyed authentication.
