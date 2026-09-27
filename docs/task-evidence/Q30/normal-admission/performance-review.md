# Q30 measured execution envelope review

## Final qualified-family gate status

The unfiltered [native05 root result](native-final-05-root-result.json) is PASS:
78 Java suites and independent oracles, 51 structural rows (42 accepted, nine
retained route rejections, zero failures), 17 corruption canaries, and passing
service and finer-step matrices at 40/40 each. The run interval was
`2026-09-27T02:18:14.6666262Z`–`2026-09-27T03:15:26.4869934Z` (57m11.820s).
The [strict corpus reader](native-final-05-corpus-reader.log) reports accepted
route-advance p50/p95 of 4.591/6.168 s cold and 4.033/5.427 s warm across 42
accepted rows; the nine rejected routes remain separate. The service reader
reports 40/40 PASS with p50/p95 16.752/20.974 s, and the finer-step
comparisons also pass 40/40. Root input checks pass, with 1,248 native inputs
and three reader inputs unchanged; the separate [final source audit](final-source-input-audit-22.json)
confirms unchanged 1,133 build, 392 web and 1,248 native inputs.

The combined [P09 final22 root result](p09-final-22/p09-final-22-result.json)
and [strict reader](p09-final-22/reader-result.json) pass actual exit0, joining
native05 with compiled22 and controlled22: all 24 development and 48 holdout
rows pass exact geometry match, six controlled cases pass, and 46 malformed
controls reject. Modern22 and manual22 separately pass their declared
normal-player launch/replay and visible repair flows. Q30 is COMPLETE for the
qualified 33/35/37-package family across eight topology axes, not a universal
20–40-part guarantee. Native evidence copying and exact child/parent temporary
cleanup are complete; root owns the final privacy audit and publication.

## Current final GWT21 coordinator performance measurements

Status: **PASS for the private coordinator timing gate only.** The [compiled coordinator receipt](compiled-coordinator-21.json) rows for seeds 13, 7 and 64 each report `PASS`, `restored=true`, `cleanupComplete=true`, and passing cold and fresh-owner warm outcomes. Its [strict reader](compiled-coordinator-21-reader.log) passes 30 electrical pairs and 37 corruption canaries on the same build. The [root result](compiled-coordinator-21-result.json) confirms cleanup PASS, a stopped server and no owned survivors. The n=3 sample has no failed rows; prior failures remain in the historical record below and are excluded from these successful-row statistics.

The [before](compiled-coordinator-21-input-manifest-before.json) and [after](compiled-coordinator-21-input-manifest-after.json) audit covers 1,522 inputs and is unchanged: actual input SHA-256 `205b125fc92302f85f7102f74977e770c36557cc9b20840f85d109e674a5014a`, source SHA-256 `ad46583b8580ba1cf46b63e83530e26a61fa49fb7e936cb94f281ae154f522e2`, and web SHA-256 `9a4575953e339e00b636b827969181c9876216caf287a6ed80a066f68ecc4ecd`. The private coordinator uses an isolated 300,000 ms measurement allowance. Schema `defaultMaxJobMillis=90,000 ms` remains the default for small and composed requests; normal Q30 production runs use immutable `NORMAL_MEDIUM_EXECUTION@1` with a separate 300,000 ms deadline. These reports are `measurementOnly=true`, `normalAdmission=false`, and `playerPublished=false`; they do not certify normal admission or publication.

| Seed | Cold total / routing / proof / max unit / cleanup (ms) | Warm total / routing / proof / max unit / cleanup (ms) | HEALTHY residual cold / warm (ms) |
| --- | --- | --- | --- |
| 13 | 127275 / 20338 / 99335 / 2077 / 3 | 27715 / 20109 / 0 / 1137 / 2 | 7088 / 7101 |
| 7 | 120635 / 20005 / 93312 / 1508 / 3 | 26756 / 19549 / 0 / 1146 / 2 | 6795 / 6709 |
| 64 | 115685 / 12493 / 95410 / 1890 / 3 | 20716 / 12864 / 0 / 1375 / 3 | 7361 / 7443 |

Nearest-rank p50/p95 across the three successful rows:

| Metric | p50 | p95 |
| --- | ---: | ---: |
| Cold elapsed | 120635 ms | 127275 ms |
| Cold active routing | 20005 ms | 20338 ms |
| Cold diagnostic proof | 95410 ms | 99335 ms |
| Cold max unit | 1890 ms | 2077 ms |
| Cold cleanup | 3 ms | 3 ms |
| Warm elapsed | 26756 ms | 27715 ms |
| Warm active routing | 19549 ms | 20109 ms |
| Warm diagnostic proof | 0 ms | 0 ms |
| Warm max unit | 1146 ms | 1375 ms |
| Warm cleanup | 2 ms | 3 ms |
| Cold HEALTHY residual | 7088 ms | 7361 ms |
| Warm HEALTHY residual | 7101 ms | 7443 ms |

The three-row p95 is the sample maximum, not a population-tail estimate. Warm proof is 0 ms because each row reuses the immutable proof value on a newly constructed owner; the fresh-owner receipt passes and construction plus active routing remain measured. The HEALTHY stage includes routing. Subtracting active routing leaves a combined placement, assembly and healthy-settle residual, not a standalone browser-placement measurement. This coordinator receipt supplies no separate browser-placement timing. The separate Modern22 and manual22 production normal-player proof does not enter these measurements.

Current stage distributions (work units and actual elapsed time, nearest-rank p50/p95):

| Stage | Cold work | Cold elapsed | Warm work | Warm elapsed |
| --- | --- | --- | --- | --- |
| RESOLVE | 1 / 1 | 0 / 0 ms | 1 / 1 | 0 / 1 ms |
| HEALTHY | 70 / 74 | 26800 / 27426 ms | 70 / 74 | 26258 / 27210 ms |
| PHYSICAL | 1 / 1 | 59 / 66 ms | 1 / 1 | 49 / 53 ms |
| HYPOTHESES | 390 / 390 | 93628 / 97470 ms | 1 / 1 | 21 / 24 ms |
| SYMPTOM | 1 / 1 | 6 / 7 ms | 1 / 1 | 7 / 7 ms |
| PUBLISH | 1 / 1 | 57 / 62 ms | 1 / 1 | 56 / 60 ms |

Cold `diagnosticWorkTimings` actualElapsedMs summaries include the phase labels and the reported proof-work unit counts; each successful row’s raw per-seed timing entries are retained in [compiled-coordinator-21-summary.json](compiled-coordinator-21-summary.json):

| Cold proof-work label | Units p50 / p95 | Actual elapsed ms p50 / p95 | Max unit ms p50 / p95 |
| --- | ---: | ---: | ---: |
| REPLAY_INSTALL | 5 / 5 | 428 / 525 | 87 / 125 |
| CANDIDATE_HEALTHY_SETTLE | 25 / 25 | 23517 / 24609 | 1193 / 1219 |
| CANDIDATE_SETTLE | 25 / 25 | 5610 / 5732 | 1346 / 1406 |
| OBSERVATION_STEP:INPUT | 20 / 20 | 17393 / 17798 | 1530 / 1575 |
| OBSERVATION_STEP:WAIT | 20 / 20 | 1 / 2 | 1 / 1 |
| OBSERVATION_STEP:DC_VOLTAGE | 185 / 185 | 1147 / 1193 | 10 / 10 |
| OBSERVATION_STEP:POWER | 5 / 5 | 138 / 141 | 28 / 30 |
| OBSERVATION_STEP:SETTLE | 5 / 5 | 178 / 183 | 37 / 38 |
| POWER_OFF_SETTLE | 25 / 25 | 5057 / 6144 | 1890 / 2077 |
| REMOVE_SETTLE | 5 / 5 | 257 / 263 | 53 / 54 |
| REPLACE_SETTLE | 5 / 5 | 278 / 284 | 63 / 75 |
| POWER_ON_SETTLE | 5 / 5 | 268 / 387 | 70 / 120 |
| REPAIR_STATUS_SETTLE | 25 / 25 | 19509 / 20597 | 1240 / 1298 |
| CUSTOMER_RETEST | 25 / 25 | 19397 / 20670 | 1239 / 1308 |
| CUSTOMER_RETEST_COMPLETION | 5 / 5 | 33 / 34 | 7 / 7 |
| EVIDENCE_RESTORE_PRIMARY | 5 / 5 | 91 / 94 | 20 / 21 |

The coordinator’s cold proof evaluates all five hypotheses and 185 live observations with 390 explicit-completion proof units. Modern22 and manual22 are separate normal-player evidence and do not enter coordinator timing statistics. Native05 and the combined P09 final22 reader also pass the declared qualification gates; the scoped completion and retained limits are recorded above.

## Historical GWT09 data and budget-review rationale (not current GWT21 timings)

Historical GWT09 budget-review status: prerequisite measurements PASS. Independent review APPROVED the scoped 300,000 ms normal-medium hard limit; implementation and normal-player acceptance remained separate gates.

The fixed GWT09 cohort is 13,7,64. All three complete cold and fresh-owner warm runs pass the strict reader, with 37 corruption canaries per individual receipt and 37 over the combined census. Every cold run executes all five normal hypotheses, 185 CircuitJS observations, physical repair and customer retest through 390 charged proof units. The measurement-only 300,000 ms cap does not itself grant normal-player admission.

| Seed | Cold total | Cold route | Cold proof | Warm total | Warm route | Max unit (cold/warm) | Cleanup cold/warm |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 13 | 160335 ms | 21344 ms | 129423 ms | 35407 ms | 25087 ms | 2735/1377 ms | 5/5 ms |
| 7 | 150440 ms | 20407 ms | 121009 ms | 33829 ms | 24197 ms | 1964/1409 ms | 5/3 ms |
| 64 | 147316 ms | 13034 ms | 124529 ms | 25847 ms | 15256 ms | 2449/1723 ms | 4/4 ms |

Nearest-rank p50/p95, three successful complete rows per phase:

| Phase/metric | p50 | p95 |
| --- | --- | --- |
| cold elapsedMs | 150440 ms | 160335 ms |
| cold routingElapsedMs | 20407 ms | 21344 ms |
| cold proofElapsedMs | 124529 ms | 129423 ms |
| warm elapsedMs | 33829 ms | 35407 ms |
| warm routingElapsedMs | 24197 ms | 25087 ms |
| warm proofElapsedMs | 0 ms | 0 ms |

The sample p95 is the maximum of three observations, not a population-tail estimate. Warm proof time is zero because an immutable proof value is reused on a newly constructed electrical owner; warm total generation remains 25.8–35.4 seconds. All three predecessors restore, private caches clear, and ordinary caches remain unchanged.

Cold proof operations, each aggregated across all five hypotheses in a row:

| Operation | p50 | p95 |
| --- | --- | --- |
| CANDIDATE_HEALTHY_SETTLE | 30428 ms | 32048 ms |
| CANDIDATE_SETTLE | 7382 ms | 7469 ms |
| CUSTOMER_RETEST | 25401 ms | 26743 ms |
| CUSTOMER_RETEST_COMPLETION | 50 ms | 51 ms |
| EVIDENCE_RESTORE_PRIMARY | 127 ms | 132 ms |
| OBSERVATION_STEP:DC_VOLTAGE | 1331 ms | 1576 ms |
| OBSERVATION_STEP:INPUT | 23038 ms | 23655 ms |
| OBSERVATION_STEP:POWER | 174 ms | 182 ms |
| OBSERVATION_STEP:SETTLE | 229 ms | 241 ms |
| OBSERVATION_STEP:WAIT | 1 ms | 1 ms |
| POWER_OFF_SETTLE | 6724 ms | 7996 ms |
| POWER_ON_SETTLE | 397 ms | 483 ms |
| REMOVE_SETTLE | 317 ms | 337 ms |
| REPAIR_STATUS_SETTLE | 25145 ms | 26975 ms |
| REPLACE_SETTLE | 334 ms | 390 ms |
| REPLAY_INSTALL | 554 ms | 615 ms |

Actual compiled routing is measured separately. HEALTHY is the combined setup stage; subtracting routing yields a combined placement, assembly and healthy-settle residual, not a standalone placement measurement. The 42 accepted native structural rows separately measure placement p50/p95 224.6/259.0 ms cold and 112.1/121.8 ms warm. Native measurements must not be presented as browser placement timings. The full stage and work-label distributions are in compiled-coordinator-09-summary.json.

Failures remain separate: the earlier 90-second coordinator timeout stopped at 140/395 proof units; the GWT08 seed13 cold job completed but verifier retirement failed, so its entire row is excluded from successful timings. Nine current structural route rejections remain in the 51-seed census. No failed, rejected or incomplete row contributes to the tables.

Proposed bounded normal-medium allowance: 300,000 ms, selected by an immutable versioned request execution contract and checked against the accepted physical token before publication. Small and composed requests retain 90,000 ms. Keep 640 shared units, 5,000 ms per unit, four canonical candidates at most, exact replay one candidate, 48 vias, existing routing searches and all D01 predicates unchanged. The total allowance is cumulative across retries and foreground time; timeout remains terminal, with exact cleanup required. No arbitrary-seed or slower-hardware completion guarantee is implied.

The longest successful cold run leaves 139,665 ms under the proposed limit. This allows bounded rejected-candidate work and runtime variation without widening the per-unit, work-count or electrical envelope. Normal-player launch, replay, privacy, service, final regression and final-source build gates remain mandatory before full Q30 acceptance.

Independent budget review APPROVED the proposal after all three same-build receipts and corruption checks passed. Review examined GenerationCoordinator, GenerationJob, GenerationRequest and ForegroundGenerationClock: the cap must be validated before cancellation, bound to request/receipt identity, checked against actual physical admission before publication, cumulative across candidates, and unavailable to unsupported/Q60 contracts. It preserves terminal timeout/cleanup-failure behavior and private measurement cache isolation. This was a source/evidence review, not a final normal-player runtime pass. Root is implementing the contract with focused pre-mutation, physical-mismatch, exact-seed, shared-retry and foreground-deadline falsifiers.

## Separate native root-cohort test budget

The first full native run retains a real failure: the new Q30 raw exact-seed
cohort accepted five of eight cases against the legacy six-case threshold.
Independent source review identified that this newly added family needs a
separate public-root test: the ordinary request permits four canonical candidates,
whereas the prior ten-family oracle deliberately constructs exact seeds once.
Those ten families, their thresholds and their 60-second cohort caps are unchanged.
The Q30 check uses the same 16 values as roots, records every attempted candidate
and rejection, and checks exact single-candidate replay of each accepted result.
Its six-of-eight acceptance, six full geometries and four macro layouts remain
required. The independent 51-row raw exact-seed corpus remains unchanged; fallback
acceptance never rewrites an ordinal-zero route rejection.

Independent review APPROVED a separate hard 300-second cap for each eight-root
Q30 structural cohort. Each root performs at most four candidate constructions
and one accepted replay, at most 40 constructions per cohort. Frozen accepted
route/placement p95 values are 6.021/.259 seconds; rejected values are
6.504/.249 seconds. A largest successful shape of 24 rejected candidates,
eight accepted cold candidates and eight warm exact replays gives approximately
268 seconds using the separately observed phase p95 values and assembly costs,
leaving about 32 seconds for checks and process overhead. This arithmetic is an
estimate, not a cohort percentile or guaranteed upper runtime.

The cap is fail-closed: timeout is FAIL and does not authorize a larger allowance.
It is a native structural test bound, not a production job budget or evidence of
D01 or shared-work completion. The existing physical matrix retains its separate
reviewed 600-second Q30 child cap; all production routing, via, candidate and work
limits remain unchanged. This budget review ran no runtime tests.
