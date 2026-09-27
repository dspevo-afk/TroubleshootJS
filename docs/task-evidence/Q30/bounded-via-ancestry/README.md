# Q30 bounded via ancestry — rejected timing experiment

Baseline: accepted solver commit `0b29680c7daa62123732b91e3c1157e8ba0d6abd`.
The stage profile measured 15.595 s in P07 A*; pathViaFits currently walks the
entire previous-node chain for every eligible via position, even with zero
prior vias. The candidate adds one immutable previous-via reference per node
and scans only prior transitions (at most four), retaining the same global
hole cap, strict 20-unit spacing and initial cancellation checkpoint. The
route publisher retains the full original chain. No cost/key/ordering/budget,
geometry predicate, diagnostic proof or admission changes are intended.

Focused gates: P07TwoLayerContractTest with an independent historical ancestry
oracle, PcbLayerRoutingResumptionContractTest, private measurement budget and
normal execution policy; actual final-source JDK8/GWT build. Then fresh private
cold comparison order R7, B7, B13, R13, R64, B64 against the frozen accepted
source/build. Stop after the first pair if route or complete cold time fails
to improve; retain the result and do not claim unrun rows. All exact request,
physical/electrical proof context, work and cleanup must match. Warm proof-cache
rows remain separate. No instrumentation is added to these timing runs.

One additional reference per search node is a memory cost. Observer callback
count changes: long ancestry scans previously checked every 256 nodes; bounded
scans retain the first check. Production observer checks deadlines/cancellation,
not callback ordinal; explicit cancellation and unchanged expansion/work counts
must pass. Candidate saving is unmeasured at this checkpoint.

Q30 remains BLOCKED. Normal publication is disabled. Frozen limits remain
90,000 ms cumulative, 640 shared units and 5,000 ms active operation. Full final
acceptance is NOT RUN while timing/scale fail. No push or later milestones.

## Result

Focused native gates PASS in 38.091 s: P07 independent ancestry and existing
route checks (80 assertions), resumption/cancellation (2,203), private budget
(25) and normal policy (325). Actual final-source GWT5 build PASS in 79.856 s.
Independent read-only review found no actionable correctness risk: actual
callers exclude a current via node, retain strict spacing and the global cap,
and keep the outer checkpoint every 128 expansions. The additional reference
does not retain new ancestors beyond the existing parent chain, though its
per-node storage cost can affect heap/GC. The reviewer ran no tests/builds.

| Seed 7 | Cold ms | Proof ms | Routing ms | Warm ms | Host seconds | Cleanup seconds |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Candidate | 86,816 | 61,586 | 19,963 | 25,285 | 117.113 | 1.136 |
| Fresh control | 86,458 | 61,064 | 20,167 | 25,138 | 115.060 | 1.127 |

The candidate saves only 204 ms of routing and is 358 ms slower overall. This
single pair does not establish a useful improvement beyond measurement noise.
Per the predeclared stop, B13/R13/R64/B64 are NOT RUN. The outer exec reports
exit 1 for the script's deliberate stop path (coded exit 2); neither is PASS.
Both individual browser operations, strict readers and 37 corruption canaries
PASS, with exact request, equal complete proof key sets/values except elapsed,
468 total/390 proof work, five hypotheses, 185 observations, cold private-cache
misses, unchanged normal-cache state and no cleanup survivors. Input manifests
are stable. Host wall/monotonic drift is below 0.005 ms.

The source and test changes were removed after archiving their exact patch.
Forward and reverse patch checks PASS; production now matches accepted 0b29680.
The root WAR still contains this rejected build and must be rebuilt before use.
Frozen columnref-reference source/web remains the accepted comparison fixture.
No performance or Q30 acceptance is claimed. A revised isolated profiler will
measure the accepted solver and routing predicates before another optimization.
