# Supplied-plan route comparison: complete

The two maintained native cases PASS: original527 and translated526 assertions,
exit0, 50.4114941s for compilation, both separate JVMs and terminal cleanup.
Cleanup duration was not measured separately. The frozen1497 input audit and
independent result reader PASS. Synthetic reader checks PASS2positive/88negative;
these are reader-boundary checks, not native route evidence.

This is one informed root42/current15/density14, requested placement attempt1
(internal algorithm0), with its exact accepted layout/routing seeds. The original
raw/returned/selected plan is
`d88e50e98d6c268d3bb3486f6c7c4f1377a4399d9ecf84dc9cfbe26b3a94b73b`.
The translated case moves only the complete QA footprint and pads from
(1690,1580) to (1390,1550), retaining TOP/DEG_0, all other53 placements, graph,
source plan, endpoint identities and metadata. Placement validation precedes
routing. The seed-copy correction is recorded before instrumentation/runtime;
the exact accepted routing seed is4493419427463069983.

Each case supplies one plan to a fresh generator-owned P07 session, then follows
ordinary routing, compaction, physical validation, finalization and selected-board
border trim. The original matches the accepted final geometry fingerprint and
all retained physical scalar expectations exactly. This does not replay the
historical six-plan ranking or earlier P05/P07 attempts. Ranking remains empty
for this supplied-plan diagnostic; it is not represented as a generator replay.

| Actual result | Original | Translated | Change |
| --- | ---: | ---: | ---: |
| Physical status | SUCCESS | SUCCESS | Both validated |
| Parts / pads | 54 /131 | 54 /131 | Exact counts |
| Board area | 5,519,502 | 5,347,620 | -3.1141% |
| Width / height | 3321 /1662 | 3301 /1620 | -20 /-42 |
| Largest protected empty rectangle | 178,200 | 156,600 | -12.1212% |
| Copper Manhattan length | 42,230 | 43,450 | +2.8889% |
| Vias | 45 | 47 | +2 |
| Selected quality, milli | 270,256,005 | 267,247,148 | -1.1133% |
| P07 orderings | 3 | 3 | Unchanged cap |
| P07 expansions | 846,167 | 975,936 | +129,769 |
| P07 begin/slices time, ms | 2191 | 2417 | +226 |

Lower selected quality is better under the existing production score. Its
improvement is a measured tradeoff, not an exception to the earlier strict
area/void/copper/via non-regression oracle. No production heuristic is accepted.
The translated route uses97.5936% of the unchanged one-million expansion cap;
the comparison does not demonstrate general runtime headroom. Route timing
excludes finalization; the whole native-phase time above includes it.

The unchanged P07 limits remain three orderings, one-million total expansions,
100,000 branch expansions, 250,000 grid cells, 32,768 expansion quantum and48
vias. Each wrapper retains the maintained60,000ms JVM limit. The existing
protected-void guard for root42 remains active; there is no cap, clearance,
assertion or result-status relaxation. Existing raw-pocket causal results remain
INCONCLUSIVE under their own sampling caps.

Exact final geometry hashes:

- Original: `df556b8d89f1f91e15912f99af9238cd99368ce19e1929e1707b09c97758598a`.
- Translated: `bf8115f42645f8ba6b7b3cade02f17a0572fdc50abb0e173d569187dbd9d5bd1`.

The root reviewed the adapter, fixture, two wrappers, registration and reader.
The leaf implemented the copy-only source without running it. Native log SHA256
is `200d6163ac1ce46247cd08c477bafbb969377a1f2ace7feadf4b58d05b3dfc05`;
receipt SHA256 is
`5ce356d4bb0f2f0d610a37d9c953e31d1a9546e5e54cc21675dd38c5a5b38578`.
The successful logs required no sanitization. Recorded driver birth identities
are verified exited; maintained terminal cleanup PASS is distinct from that
driver-exit check.

Attempt1 remains FAIL35.285166s: the maintained explicit compiler list omitted
the shared fixture, producing four missing-symbol errors before any routes ran.
Its completed timing, logs,1494 seal/audit and cleanup are retained in attempt1/.
The only repair adds that same helper to the compile-only list; the runtime
selection remains exactly two wrappers. Fixture/generator bytes and all budgets
are unchanged. The successful repaired phase has its own1497 seal and audit.

Commands use the archived run_focused.ps1 driver, maintained
scripts/verify-current-contracts.ps1 selected suites PcbQaRouteOriginalContractTest
and PcbQaRouteTranslatedContractTest, phase_inputs.py audit, check_routes.py,
check_reader.py and
check_process_cleanup.ps1. The owned source copy is restored and retired through
retire_copy.py; copy-restoration.json/copy-cleanup.json record the outcome.

Main production/gate files were not changed by this diagnostic. Main renderer1475
remains separately frozen and unpublished pending actual visible player proof.
Fresh GWT/player/electrical/full-matrix/general-population/manufacturing/modest-host
qualification is NOT RUN here. The evidence supports a finite route tradeoff and
a next investigation of corridor estimation, not a new packing policy or optimum.
