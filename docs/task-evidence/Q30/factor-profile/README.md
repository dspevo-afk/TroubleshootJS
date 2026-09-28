# Whole-factor profile — intermediate diagnostic

**Q30 remains BLOCKED. This packet is not acceptance evidence or a measured optimization.** It records one private seed-10014 off/profile pair on the frozen sticky-finite source base. The profile sampler was not integrated into production.

| Run | Cold (ms) | Proof (ms) | Routing (ms) | Host (s) | Cleanup (s) |
|---|---:|---:|---:|---:|---:|
| factor-profile-01-off-10014 | 98362 | 69299 | 22867 | 130.497 | 0.896 |
| factor-profile-02-profile-10014 | 98339 | 69627 | 22482 | 130.056 | 0.973 |

The profile selected 1,453 of 742,391 complete factor calls. Across 1,453 sampled matrices, `performance.now` recorded 65.8 ms inclusive factor time, 17.9 ms for the separate sampled solve scope, and 2.6 ms of nested preflight time. Preflight overlaps factor time and must not be added. Sampling copy/compare cost was 32.8 ms inside the profile brackets, outside factor timing; it excludes per-call cadence bookkeeping and compilation. Sparse selection and clock granularity limit interpretation.

A rough mean-cost projection is about 33.620 s across all factor calls and 9.146 s for solve calls. These are extrapolations for ranking only, not measured elapsed time or savings. The near-identical off/profile cold totals do not establish zero overhead. The cold run reports 390 hypothesis work units across five hypotheses. The warm result is a separate private proof-cache hit with one hypothesis work unit; it is not evidence of numerical factor reuse.

The R1 native gate failed with exit 2 because the native JVM test attempted a GWT `JSONObject` construction; this failure is preserved. R2 focused native, maintained JDK8/GWT, compiled A07/disabled-normal canaries and the exact A07 reader with three corruptions passed. The timing-sequence receipt is preserved with its original pending-validation status. The subsequent root pair validator passed exact request and non-profile report parity, plus the frozen 40-package request and audited plan/topology assertions for both runs. Its `observedPackageCounts` field ([40, 40]) echoes the request checked against those assertions; it is not an independent enumeration from the application reports. It also passed 109 expected metadata-negative cases, and both strict current-plan-4 readers passed 43 corruption cases each. Input identity, host error arrays and owned cleanup were checked. This remains one-seed diagnostic evidence, not cohort or electrical acceptance.

The 1,342-input source inventory reconstructs from the committed [solve-profile source packet](../solve-profile/README.md) plus four byte-exact overlays. Source audit status remains historically `SOURCE_ONLY_R2_PREPARED_NOT_VALIDATED`; later gate receipts bind that audit. Its overlay summary still lists the pre-R2 test hash (3bf74b…); the complete source input inventory and frozen R2 fixture both identify the repaired test as b82c79… (12,731 bytes), which is the exact overlay archived here. Raw application reports are gzip-compressed byte-for-byte. Sanitized metadata retains original and stored hashes/redaction counts in `provenance/archive-transforms.json`. Browser profiles, generated WARs, JARs and caches are excluded.

The independent validator review is archived in `reviews/`. `provenance/dependency-map.json` pins the executed helper chain, browser runner, and compiled-canary spec. `run_timing.py` is included as an incidental procedure artifact; the pair runner does not invoke it. The R1 source audit and exact pre-repair R1 test are retained under `source/r1/`.

`inventory.json` covers every stored file except itself and `inventory.sha256`; gzip members include decompressed hashes. The inventory SHA-256 checks its bytes; it is not keyed authentication.
