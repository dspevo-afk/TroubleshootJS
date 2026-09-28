# Owned zero-row scan — intermediate evidence

**Accepted intermediate optimization; Q30 BLOCKED / NOT ACCEPTED.** The experiment receipt is `FOCUSED_EXPERIMENT_PASS_NOT_ACCEPTANCE`. Normal-player publication remains disabled. The fixed 90,000 ms, 640 work-unit, and 5,000 ms per-operation contracts were not changed.

The candidate short-circuits each private owned nonlinear LU zero-row scan at its first nonzero while still checking every row. General finite validation, factor/solve arithmetic and ordering, proof work, and work accounting remain. The timed R2 source pair and final staged-index source are separately identified in `source/lineage.json`; inherited source reconstruction uses the adjacent committed [validated-factor-input baseline](../validated-factor-input/README.md).

The [preceding sparse measurement](../current-preflight/README.md) found that a first-hit scan would visit only 24.247% of the sampled cells. This change removes redundant reads once a row is already known to contain a nonzero. It does not infer a row from its diagonal or skip a zero row. Both signed zeros still compare equal to zero; subnormal nonzero values still count. The private input contract already establishes finite restored/stamped values; the general LU API retains its full validation. The shared numerical kernel, pivot ordering, solve, and workspace `finally` cleanup are unchanged.

Independent [source review](provenance/independent-review.md) found no blocker. New reflection fixtures invoke the actual private helper against the unchanged independent factor/solve oracle, including off-diagonal and last-column nonzeros, zero rows in several positions, pivot ties, subnormals, overflow and reuse after failure. Neither generation/replay nor candidate ordering is edited. Complete cold/warm reports match outside explicitly enumerated timing fields on all four pairs, including electrical observations, full physical mapping, all five hypotheses, service/repair/retest proofs, work and cache state. Settling and acceptance thresholds are unchanged; the private measurement does not supply proof to normal generation.

## Predeclared timing rows

| Run | Arm | Seed | Cold ms | Proof ms | Route ms | Work units | Hypothesis work units |
|---|---|---:|---:|---:|---:|---:|---:|
| owned-zero-row-01-control-7 | control | 7 | 80663 | 60711 | 14565 | 462 | 390 |
| owned-zero-row-02-candidate-7 | candidate | 7 | 78806 | 58754 | 14827 | 462 | 390 |
| owned-zero-row-03-candidate-10387 | candidate | 10387 | 23984 | 16274 | 5782 | 276 | 230 |
| owned-zero-row-04-control-10387 | control | 10387 | 24724 | 16940 | 5800 | 276 | 230 |
| owned-zero-row-05-control-10014 | control | 10014 | 103739 | 74740 | 22507 | 490 | 390 |
| owned-zero-row-06-candidate-10014 | candidate | 10014 | 99800 | 70808 | 22637 | 490 | 390 |
| owned-zero-row-07-candidate-7 | candidate | 7 | 78115 | 58201 | 14623 | 462 | 390 |
| owned-zero-row-08-control-7 | control | 7 | 81290 | 61346 | 14530 | 462 | 390 |

| Pair | Seed | Control−candidate cold ms | Proof ms | Route ms |
|---|---:|---:|---:|---:|
| owned-zero-row-pair-1 | 7 | 1857 | 1957 | -262 |
| owned-zero-row-pair-2 | 10387 | 740 | 666 | 18 |
| owned-zero-row-pair-3 | 10014 | 3939 | 3932 | -130 |
| owned-zero-row-pair-4 | 7 | 3175 | 3145 | -93 |

Median paired savings: cold **2516.0 ms**, proof **2551.0 ms**, routing **-111.5 ms**. Seed-7 cold ranges: control 627 ms, candidate 691 ms; proof ranges: control 635 ms, candidate 553 ms. These four pairs are a focused trial, not a population claim.

Every row starts a fresh browser process and context with an empty cold private cache; the order is counterbalanced and predeclared. Both repeated seed-7 gains exceed their observed within-arm spreads, and the 40-part gain closely follows proof time while route timing changes little. This supports attribution to the scan change; the single 20-part difference is not enough for a population claim. Application elapsed fields are legacy wall-clock fields. Host durations use `time.monotonic`, with recorded wall/monotonic drift; no monotonic application-time claim is made. The 40-part candidate remains **99,800 ms**, above the production limit. Full normal and scale acceptance are NOT RUN.

Warm phases are separate private proof-cache hits (one hypothesis work unit); they are not numerical factor reuse evidence. No signed-zero bit-identity claim is made.

R1's failed native result is preserved. R2 focused gates and final exact-index native/GWT/A07 gates passed; the root build/runtime equality receipt reports 1,525 consumed inputs and 392 WAR files equal to the measured candidate. This evidence does not cover the full normal acceptance or scale corpus. Raw application reports are preserved byte-for-byte in gzip; metadata paths are scrubbed.

R1 completed 164 assertions but failed the maintained driver's required `PASS:` marker, exit 2 in 33.827 s. R2 fixes only that output prefix. Working-source native5 passes in 33.677 s and exact-index native5 in 36.888 s (A07: 271,503 assertions; new private helper: 164). Both trial GWT5 builds pass (78.265/78.598 s), as do compiled A07/disabled-normal checks and exact readers with negative cases. The exact source index excludes unaccepted plan-4 and normal-verifier edits; GWT5 and compiled A07 plus three corruptions pass, with 0.881 s owned cleanup. Root GWT5 passes in 76.798 s and its complete WAR/consumed-input equality permits reuse of the candidate canaries. All eight timing runs have empty host-error arrays, unchanged consumed inputs and verified cleanup with no survivors. Gate elapsed time includes the maintained native/build command; browser/server cleanup is separately recorded in each run.

Only the row scan, one native test and its driver registration are committed as production/test changes. The measured working source deliberately includes preserved unaccepted scale work; its 1,339-input reconstruction and the separate 1,338-input committed-source gate are recorded independently. Metadata/procedure copies may redact personal paths and normalize archival whitespace; `provenance/archive-transforms.json` retains their original and stored hashes. Raw source overlays and raw application reports are byte-exact.

`inventory.json` lists every stored file except itself and `inventory.sha256`; gzip entries include decompressed byte counts and hashes. The detached inventory checksum checks the inventory bytes; it is not keyed authentication.

Large JSON metadata is stored losslessly as gzip to keep the review compact; `provenance/large-metadata-compression.json` maps original receipt filenames to stored names.
