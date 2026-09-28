# Routing-bookkeeping trial — accepted intermediate

The isolated routing-bookkeeping candidate is accepted as an intermediate optimization trial. Root has integrated its three source/test paths into the current combined source; final integrated-source gates PASS. The predeclared sequence R7, F7, F13, R13, R64, F64, F7, R7 completed with all eight strict reports, input audits, cleanup records, and four paired parity receipts passing. R is the routing candidate; F is the finite-predicate control. See candidate-integration-receipt.json for the exact integration hashes and boundary.

| Pair (candidate/control) | Candidate cold / proof / routing (ms) | Control cold / proof / routing (ms) | Candidate savings cold / proof / routing (ms) |
|---|---:|---:|---:|
| R7 / F7 | 86,074 / 63,089 / 17,317 | 91,038 / 63,471 / 22,020 | 4,964 / 382 / 4,703 |
| R13 / F13 | 92,615 / 69,115 / 17,478 | 98,361 / 69,838 / 22,433 | 5,746 / 723 / 4,955 |
| R64 / F64 | 80,748 / 64,209 / 10,489 | 89,046 / 69,219 / 13,628 | 8,298 / 5,010 / 3,139 |
| R7 / F7 (reversed order) | 80,852 / 60,027 / 15,700 | 86,589 / 60,390 / 20,985 | 5,737 / 363 / 5,285 |

Median paired routing-time savings are 4,829 ms. Median cold-time savings are 5,741.5 ms, but cold-time differences include proof and run-to-run variation and should not be attributed wholly to this routing change. Seed 13 remains above the 90,000 ms cold target at 92,615 ms, so Q30 remains BLOCKED / NOT ACCEPTED.

The maintained focused native route/order/resumption/physical suites passed in 33.4420668 s. The maintained JDK 8/GWT five-permutation build passed in 90.6453101 s. Compiled A07 and its strict reader passed with three negative corruption canaries. These gates belong to the isolated routing trial. Final integrated-source gates also PASS, as detailed below.

The isolated comparison uses a57ffc30b04d306daf0355e44d218de4175bb9ea, which contains the earlier finite-predicate change. It excludes the separately trialed right-looking LU and plan4 changes. The canonical input manifests cover all eight runs and match every before/after inventory within each arm. Raw application reports are byte-exact inside gzip. Provenance and cleanup evidence are sanitized. source-audit.json records the baseline/archive comparison, exactly three modified candidate files, nine pinned jars, and the source patch hash. artifact-hash-manifest.json hashes every other file in this bundle.


## Final integrated-source gates

The combined current source passed the focused native routing suites in 35.391672 s and the maintained JDK 8/GWT five-permutation build in 82.0174975 s. The compiled browser canaries passed: A07 completed in 1.2854305 s and its strict reader passed three negative corruption canaries; the disabled normal-catalog case returned the exact expected BLOCKED:normal-catalog-disabled state in 0.4882848 s, with normal-catalog-enabled false, generation, snapshot capture, and live-owner mutation all false. The canary runner recorded zero errors, stable before/after inputs across 1,525 files, and cleanup PASS with the server stopped, no owned survivors, and no cleanup errors. The combined source includes the earlier finite-predicate, right-looking LU, plan4, and routing-bookkeeping changes; these integrated gates do not alter the isolated timing attribution.

Q30 remains BLOCKED / NOT ACCEPTED because the seed-13 routing-candidate cold run was 92,615 ms against the 90,000 ms target. Normal-player publication remains disabled. See final-integrated-source/ for sanitized receipts, case metadata, byte-exact compressed reports, and input/cleanup provenance.

The source delta replaces the generic priority queue with a typed Node heap and removes temporary neighbor arrays. The heap retains the exact cost/heuristic/key/serial comparator, and direct neighbor visits retain the original order. Every route expansion, physical check, observer/budget check and candidate ranking remains. The focused queue oracle compares full dequeue identity against the independent Java PriorityQueue during mixed operations, growth, tied priorities and draining; existing routing and resumption fixtures remain. No solver, electrical graph, proof cache, hypothesis or admission threshold is changed.

Application cold/proof/routing milliseconds come from the unchanged report wall clocks; host totals use Python monotonic timing, with wall-minus-monotonic drift recorded separately. These are fresh Edge processes and profiles, with cold preceding warm in each. Seed-7 repeat ranges are 4,449 ms for control and 5,222 ms for candidate. Routing gains are consistent across both execution orders despite that host variation; combined kernel/plan4 gains are not assumed additive. The integration receipt describes the source copy before final gates; comparison.json and the final integrated-source receipts govern this accepted intermediate decision.
