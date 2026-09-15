# P05: bounded rerouting and congestion recovery

Status: PASS. Qualified on September 15, 2026 against base
`94d885ea37955c46980ae7d1e55a68b8dd3e17ea`, branch
`codex/task43p-final-recovery`. This commit contains P05 only, not the pending
visual/FPS/packing/tray/shop implementation. P06, P07 and the post-P09 arbitrary
candidate-seed Quick Play gate remain unstarted; current curated seeds remain.

## Ownership and deterministic limits

`PcbNetRouter` retains the existing single-layer A* search and physical rules.
Immutable net requests contain sorted stable pad IDs, typed power priority,
degree and span. The scheduler tries canonical, conflict-promoted, degree/span,
short-span and reverse-tie alternatives in a fixed order. Real occupancy conflict
counts select victims with stable net-ID ties; names such as GND confer no magic
priority. A failed net is tried before the selected earlier blockers.

Each fixed-placement routing request permits at most 5 orderings, 2 rip-up passes,
2 victims per pass, 6 rerouted-net attempts and 1,000,000 search expansions across
all alternatives. Net attempts are capped at `5*N+6`; grids at 700,000 cells;
each branch retains its additional `20*cells` guard. Limits are centralized in
`PcbRoutingWork.Limits` and tests may only tighten them. Existing 80-placement
and successful-candidate limits are unchanged. Original and optional compact
plans each get one bounded request. Elapsed time is evidence or cancellation,
never the successful-candidate tie breaker. No negotiated congestion was added.

Routes are built on private layout copies. Rip-up removes exact net ownership
and reconstructs occupancy/clearance from surviving copper, retaining shared
clearance halos. An exact occupancy audit runs before and after canonicalization.
Hard geometry, connectivity, clearance and quality checks precede the final
cancellation checkpoint and publication. Exhaustion/abort publishes no traces.

## Frozen comparisons and adverse results

The same 70 fixed-placement cases have identical component/pad/net/outline
identity hashes before and after. Pre-P05 accepts 14; P05 accepts 24. No previously
accepted case is lost and no common accepted route has a worse quality score.
The remaining 46 failures are retained, including harder held-out placements.
All successful cases run full physical validation and pristine correspondence.
See `corpus-comparison.json` for each outcome, work receipt and route metric.

| Fixed-placement metric | Pre-P05 | Integrated P05 |
|---|---:|---:|
| Accepted | 14/70 | 24/70 |
| Total expansions | 6,421,041 | 31,246,802 |
| Observed total route milliseconds | 2,191.21 | 6,847.12 |
| Observed p50 / p95 / worst milliseconds | 27.89 / 63.08 / 135.15 | 109.90 / 163.85 / 271.55 |

Recovery costs more search; the acceptance gain is not a universal speedup.
Clean-publication routing decisions/expansions match the integrated corpus.
The separate 13-seed whole-generator comparison improves from 11 successes and
two 80-placement exhaustions to 13 successes. Integrated observed total time is
37.35 to 19.96 seconds, with recorded expansions 90,319,040 to 100,004,633.
Those timings are single-host observations, not controlled cold/warm benchmarks.
The pre-P05 baseline includes preserved visual work; publication excludes it.

The 11-seed integrated area sum decreases from 21,680,230 to 20,746,824 (4.31%).
Seed 3 nevertheless grows from 1,705,620 to 1,894,050 (11.05%) because a different
earlier legal placement wins. No fixed placement is silently expanded. The
current compaction ablation passes all 11 seeds, but does not promise that every
new routing decision improves the previous generator's final board area.

## Recovery and cleanup witnesses

The real dual-indicator board at seed -17, placement 1, fails canonical routing
on BRANCH1_NODE. Promoting only that failed net succeeds with no rip-up and
35,080 expansions. Keeping the initial order and permitting local recovery also
succeeds: occupancy selects VIN and GND, with one rip-up pass, two victims,
three rerouted-net attempts and 71,986 expansions. Repeated geometry and work
receipts are identical; physical/electrical identities are unchanged.

A reduced one-layer grid with alternating opposite-boundary terminal pairs
cannot connect both nets disjointly. It terminates with NO_PATH, 5 orders,
2 rip-up passes and 23,390 expansions. A separate one-expansion cap stops at
exactly one expansion with SEARCH_LIMIT. Neither publishes partial copper.
Observer invariants, cancellation, stale requests and deadlines propagate their
original classifications rather than becoming normal routing rejection.

The 21,976-assertion P05 suite uses an independent trace raster/clearance oracle.
It checks shared-halo ownership, exact unrelated trace survival, repeated rip-up,
a genuinely changed replacement path and failed-search atomicity. Deliberately
injected ghost copper and stale clearance fail the publication audit. These are
negative detections, not cleanup that conceals a faulty occupancy state.

## Final gates

- PASS: nine clean-publication native suites and twelve integrated native suites.
  P03 has 864,267 assertions; P04 6,408; P02 3,209; Q15 6,863. Exact markers and
  recovery witnesses are in `native-results.json`. Separate integrated dense
  packing passes all 11 seeds within the unchanged 60-second per-suite limit.
- PASS: fresh maintained JDK8/GWT builds of both source variants, all five
  permutations each. Build identities are in `source-build-identity.json`.
- PASS: compiled full layout/geometry verifier on publication (23.71 seconds)
  and integrated source (23.90 seconds), including P05 recovery witnesses.
- PASS: compiled publication Q15, all 11 seeds, six admission stages, cancellation,
  wrong/correct repair, actual solved continuity and exact owner restoration.
  The 371.89-second total includes the full matrix, not one board load. Individual
  admission takes 18.32-36.10 seconds; worst synchronous work unit is 2.393 seconds.
- PASS: freshly compiled integrated normal-player RB15 seed 3, using menu controls,
  exact-seed entry, ticket acceptance, copper-face flip, middle-drag pan and wheel
  zoom. Preparation took 24.72 seconds. No application exceptions. Three inspected
  screenshots and `browser-results.json` record the actual flow, not injected
  controller actions. This smoke is not an additional manual repair matrix.

Fresh final publication emits the exact same five cache.js files as the just-run
layout/Q15 build. Source manifests and generated-byte hashes qualify reuse of
those compiled receipts; the integrated layout/player checks ran its own fresh
build. Full unrelated release matrices, new hardware/browser populations and
modest-machine performance certification are NOT RUN. Host: Ryzen 7 7700,
approximately 32 GB RAM, RTX 3070, Windows 11 build 26200, Edge 153.0.4234.32.

## Failed attempts and preserved work

The earlier compiled runner's 10-second DOM poll expired during a synchronous
layout matrix. The corrected runner retains the same 600-second overall cap.
Native fixture lookup initially failed because Java inherited Desktop Commander's
OS working directory; setting only PowerShell's location was insufficient.
Final runs set the actual process cwd. These failures are not counted as PASS.
Player harness selectors were corrected for nested labels, the ticket modal and
the Board view accessible name. Successful UI input was then repeated end to end.

The preserved, untracked DensePcbPackingContractTest initially exceeded 60 seconds:
its old outer retry loop nested P05's new scheduler. One test-only integration
hunk now invokes the current scheduler once, retaining every seed/assertion and
the timeout. The rerun passes in 38.45 seconds including compilation. The original
file and exact hunk are retained in task-owned Temp. The adjusted file remains
unstaged/untracked; `visual-harness-integration.patch` records only that necessary
integration delta, not the unrelated visual implementation.

All other unrelated visual/FPS/packing/tray/shop source and untracked evidence
are preserved. Only the P05 portions of the shared generation owner and contract
runner enter this commit, using the separately built clean-publication candidate.
Root integrated review covers request/state ownership, conflict ties, work caps,
rollback, publication, exceptions, physical checks and the mixed-worktree boundary.
No independent agent review was used. No production geometry/electrical rule was
relaxed. The inspected menu retains a pre-existing encoding blemish in its heading;
this task does not certify or modify the rest of the pending visual pass.

All task browser contexts, preview servers and exact recorded browser-process
identities terminated cleanly. Raw attempts/builds live under the task-owned
OS Temp directory `TroubleshootJS-P05-resume-20260915-dzyh5u7_`; baseline receipts
remain under `TroubleshootJS-P05-20260915-4jwc0pgd`. No broad cleanup was run.
Reproduction commands and the browser drivers are in `REPRODUCE.md` and the two
Python files here. Next unstarted milestone: P06. Broad Quick Play admission
remains deferred to its explicit post-P09 gate.
