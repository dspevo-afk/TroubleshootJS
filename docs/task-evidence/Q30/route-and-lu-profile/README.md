# Q30 accepted-kernel and routing profile — focused measurement PASS

This isolated fixture extends the accepted-source stage profile. It changes no
production source or requirement. Accepted source is 7a30a3f; current local
evidence checkpoint is 18f57af. Rejected index/list experiments are separate.

The full instrumentation patch applies to the accepted source and reverses from
the frozen fixture; both checks PASS. Only temporary profiling sources differ.
The maintained JDK8/GWT five-permutation build PASSes in 81.334 s. The fresh
private compiled seed-7 run PASSes with exact request, full proof/context
(including physical geometry), work counts, balanced scopes, stable source/web
inputs and cleanup. The strict coordinator reader and 37 corruption canaries
PASS. Host monotonic elapsed is 138.394 s, including the separate warm row and
runner overhead; cleanup is 1.218 s with no owned survivors.

The LU selector samples actual active factor calls 1024, 2048 and so on. Other
calls dispatch to the unchanged accepted three-argument kernel. Only sampled
calls enter a duplicate with phase timers: finite-input/zero-row scan, Crout
updates, pivot search, numeric row swap and scaling. All timers use monotonic
`performance.now()`; phase starts/ends are counted, and early returns/failures
close their scopes. A separate 32-pair calibration estimates clock cost only,
excluding dispatch/aggregation and code-shape overhead. Quantized zero clock
cost does not establish zero overhead. The existing kernel sample remains.

Routing timers split session/candidate setup, rule/router construction, net and
branch preparation, goal fields, lower-bound BFS, A* work, publisher work,
geometry/rule/quality validation and accepted-route finalization. Predicate
samples occur only every 4096 branch-local expansions. No inner edge, path-node,
pad, part or hole counters remain. Branch/expansion counts use existing counters;
complete goal/BFS scan counts derive from grid dimensions and completed branch
construction. Sparse predicate samples overlap the coarse A* scope and must not
be added to the exclusive stage partition.

## Cold result and next target

Monotonic cold time is **106,730.6 ms**; the legacy application clock reports
106,724 ms. Proof takes 80,263 ms and routing 20,042 ms. This is close to both
the earlier 106,350.4-ms profile and fresh uninstrumented 107,655-ms control.
No material aggregate slowdown is observed in these samples; this is not an
isolated estimate of profiler overhead. The host wall/monotonic drift is under
0.005 ms. The unchanged 616,000 accepted solver steps, 1,345,510 trials,
729,510 factorizations/solves, five hypotheses, 185 observations, 390 proof
units and 468 total job units remain recorded.

| Sampled LU phase | Sampled milliseconds | Share of sampled phase sum |
| --- | ---: | ---: |
| Crout updates | 36.6 | 38.7% |
| Numeric row copying during pivots | 19.1 | 20.2% |
| Scaling | 15.9 | 16.8% |
| Pivot search | 13.9 | 14.7% |
| Finite-input and zero-row scan | 9.1 | 9.6% |

These 712 selected factorizations contain 50,524 columns and 48,138 row swaps.
They execute the instrumented duplicate. Their 199,710 phase pairs/399,420
clock reads balance exactly; calibration is separate. **Do not extrapolate
sampled milliseconds directly to total runtime.** Short phase intervals suffer
clock quantization, timer/aggregation cost and different JIT code shape. The
zero calibration result is below timer resolution, not proof of zero cost.
These shares support targeting Crout traversal, pivot row copying, then scaling;
they are not precise production cost percentages.

| P07 route operation | Inclusive milliseconds |
| --- | ---: |
| A* expansion work | 15,594.8 |
| Lower-bound BFS preparation | 1,001.9 |
| Goal-field construction | 395.4 |
| Accepted-route finalization | 96.6 |
| Final geometry/rules/quality checks | 27.0 |
| Publisher work | 11.2 |
| Session/copy/rules/router construction, excluding nested double counting | 6.1 |

P07 records 7 ordering searches, 270 branches and 1,448,229 expansions. With
the three retained one-face failures, total routing remains 4,448,229
expansions as in the accepted stage profile. No candidates or orderings were
dropped. Goal/BFS totals are derived counts for this complete run. Sparse
neighbor/via samples total only 2.1/1.4 ms over 1,224/272 calls; they are too
small and branch-biased for reliable production percentages. A* is the routing
target; rebuilding indexes and final physical validation are not significant
contributors. See raw stage values for all inclusive/exclusive scopes and warm
repetition. Warm routing is 19,964 ms and its proof is a private immutable-cache
hit; it is excluded from cold performance claims.

The next solver experiment keeps column coefficient hoisting and stores
references to nonzero lower rows, with row-reference pivots and unconditional
scratch-reference cleanup. That is a new, unaccepted candidate. The audited
possibility of omitting duplicate finite-input checks is **not implemented**:
raw package-visible matrix/snapshot writes are not structurally constrained,
and the scan is not the largest measured contributor.

No deadline, work cap, candidate ordering, search budget, solver settling,
hypothesis, service/repair/retest/completion requirement or admission predicate
is altered. The private measurement allowance and cache remain isolated.
Q30 stays BLOCKED and normal publication disabled; full final matrices and
later milestones are NOT RUN. No push.
