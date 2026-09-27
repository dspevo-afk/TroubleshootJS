# Timing and ownership audit

Base `745d537`. The timing rows are fresh compiled measurements; the code
findings below are read-only inspection, not loop-level timing claims.

| Required investigation | Evidence / finding |
|---|---|
| Routing candidate generation and retries | `SeededPcbLayoutGenerator` makes six placement candidates, ranks them, and routes at most three P05 candidates. A successful P05 path permits one P07 probe; otherwise at most three P07 candidates. P05/P07 preserve their five/three ordering bounds. Measured total routing is 12,778–20,875 ms; the proof alone exceeds 90 seconds. |
| Physical admission | Seed 13 PHYSICAL is 66 ms. Geometry, actual copper/solver correspondence and service checks remain required. |
| Diagnostic hypotheses/proof | Each cold row uses five disjoint owners, 185 live DC readings and 390 charged proof units. Healthy, input, repair-status and customer-retest settlement dominate. |
| Repeated CircuitJS setup/analyze/settle | The temporary kernel profile samples the existing transient path with unchanged 30-ms intervals and 5-us maximum steps. Analysis and matrix dimensions have separate counters. |
| Redundant reconstruction | Five REPLAY_INSTALL operations total 475 ms for seed 13. Hypotheses already copy the sealed physical layout into fresh electrical owners; sharing mutable solver state is not an optimization option. |
| Repeated immutable calculations | Placement candidates rebuild board-only `TopologyPlacementGraph` data; `getLinksFor` copies its vector. Per-candidate scoring constructs net geometry/MST data. A session-owned immutable graph or indexed read-only links could retain exact iteration order, but measured placement-specific compiled time is not available here. |
| Hot allocations/copies | P07 allocates goal/distance/queue arrays and a priority queue per pad branch, plus small neighbor arrays in BFS/A* loops. Solver batches rebuild iteration/scoped-element arrays every 128 accepted steps. These are candidates for measurement, not established dominant costs. |
| Indexed/incremental work | The routers already index copper occupancy and pad/escape reservations on an empty component face. Populated-face checks still scan pads/components. An exact per-search spatial index is possible. Goal masks and occupancy cannot be reused across branch/via publication without tracking the changed copper/hole set. |
| Apparently duplicate validation | P07 checks route geometry/rules/quality; `finishRoutedPlan` checks before and after compaction. These validate different states. Dropping them is unjustified. |
| Cold versus warm | Baseline cold median 120,720 ms; fresh-owner warm median 27,812 ms. Only private immutable proof values are reused. Warm proof time is zero; routing and physical/electrical reconstruction still execute. Private cache cleanup and unchanged ordinary cache are verified. |

Primary source locations: `SeededPcbLayoutGenerator.advanceMediumPlacement`,
`advanceMediumRouting`, `finishRoutedPlan`; `MediumBoardPhysicalPolicy`;
`PcbLayerRoutingPrototype.Session` and `Search`; `PcbNetRouter` occupancy
indexes; `PcbPlacementPlanner`; `TopologyPlacementGraph.getLinksFor`;
`CirSim.runCircuitOwned`; `CircuitSolverExecutor.advanceFor`;
`Rb30DiagnosticProvider.generateHypothesis`; `GenerationCoordinator.Services`.

Do not shorten candidate orderings, remove checks or extend deadlines based on
these source observations. Any exact-work optimization needs matched timings,
identity and rejection parity, and unchanged cancellation/cleanup boundaries.

## Unimplemented follow-up

The sampled profile identifies factorization as about 63% of measured kernel
time. Skipping finite zero row factors improves the uninstrumented three-seed
median by 12.0%, but does not close the budget gap. The user's stop rule applies.

A read-only solver follow-up proposes measuring adjacent identical nonlinear
matrix inputs before considering exact, operation-local LU reuse. Every trial
would still stamp models, solve its RHS, publish state and run all guards and
events. No repeat-frequency measurement or reuse implementation exists here.
It would need exact numeric comparison (including signed zeros), matrix
identity/size and restamp invalidation, no cross-operation lifetime, singular
and nonfinite parity, and independent cached/uncached oracle cases. Copy and
comparison cost could exceed any saving. This is an investigation candidate,
not an accepted optimization, estimated speedup or promise of 90-second fit.
