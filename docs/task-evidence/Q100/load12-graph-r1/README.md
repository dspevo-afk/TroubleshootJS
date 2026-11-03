# LOAD12 fixed-prefix zero-new-via graph

**Status: accepted and published for this finite diagnostic.**
Focused native contracts, same-source D_SENSE and LOAD12 captures, current
readers, 133 fresh adversarial controls, independent actual-graph oracles,
paired consistency and semantic mismatch checks, and the final combined input
audit pass. Earlier failures stay recorded and receive no PASS credit.

This packet records a diagnostic observation of the actual fixed routing
prefix. It does not change production routing or prove a legal route. Both
target operations use root seed `9007199254740993`, placement seed
`-6587958367974952701`, routing seed `8445301674744508279`, request/index
`0/0`, placement attempt `4`, and pass `1`. Each operation selects one target.
D_SENSE is branch 117 at net position 14 / next-fetch 15. LOAD12 is branch 73
at position 3 / next-fetch 4. The graph uses the actual two Router predicates
and directed planar edges; it introduces no via edges or hypothetical copper.

The [frozen scope](PLAN.md) and [wire/API](WIRE_SPEC.md) define the private
sourceRevision 20, graph r5, and input/run/reader schemas v5. The candidate
native input set has 1,077 files, 86,611,889 bytes, and pin SHA-256
`408473f0d144250f0d901821486674ad8d01ad2af43dfd2633a228ec2f9e137c`.
Native production input remains 850 Java files; layout 26, dependency 28 and
VIA cost 60 are unchanged.

## Current evidence

| Gate | Result | Evidence summary |
|---|---|---|
| Focused native contracts | PASS | Five suites, 39.0378681 s; assertions 24 / 57 / 48 / 3,736 / 13,112. |
| Actual D_SENSE native query | PASS | 78.2774239 s; 634/640 operation units; cleanup, termination, and before/after input audit passed. |
| Actual LOAD12 native query | PASS | 79.0452749 s; 628/640 operation units; cleanup, termination, and before/after input audit passed. |
| Current D_SENSE reader | PASS | v5 result, selected reader source stable. |
| Current LOAD12 reader | PASS | v5 result, selected reader source stable. |
| Paired-target source audit | PASS | Same source pins and revision 20; paired result reports source stable. |
| Python protocol/math/source/path suite | PASS with one declared skip | 51 passed; one file-symlink privilege check skipped out of 52. |
| D_SENSE adversarial reader controls | PASS | Fresh r6: 65 negatives, 3 scoped N/A cases, junction cleanup, zero exclusions. |
| LOAD12 adversarial reader controls | PASS | Fresh r6: 68 negatives, junction cleanup, zero exclusions. |
| Standalone actual-target oracles | PASS | Source `b7475a16`; D_SENSE 0.8023798 s and LOAD12 0.7541897 s, exit 0 with source/input stability and termination. |
| Paired hash-mismatch control | PASS | Synthetic standalone LOAD exit 0; paired audit rejects the false later D hash with exit 2 and exact `PAIR_D_ALLOCATION_stability`. No native qualification credit. |
| Final combined input audit | PASS | r10, 57.0072596 s; binds all selected gates and recenses 1,077 native, 850 production, 1,202 compiler inputs per variant and 28 unchanged Python files. |
| Scoped source review | PASS | Root integration review; separate source-only helper, reader, runner and aggregate-audit checks. Runtime gates belong to root. |

Native `PASS` here means the bounded diagnostics and their source/cleanup
contracts completed. Both captured routing sessions ended `EXHAUSTED` after a
routing rejection, emitted no routed layout, and reported
`layoutQualification:false`. The captures do not represent a successful board
route.

Standalone LOAD12 validates the format of the later D_SENSE prefix hash. Its
content is checked against the separate D_SENSE capture by the paired audit;
the earlier LOAD12 graph supplies no later D geometry authority. The malformed
LOAD12 hash control and paired content-mismatch control exercise those separate
contracts.

## Observed graph result

The LOAD12 pre-publication prefix contained 17 VIAs and 88 traces. Its actual
path had 222 Nodes, 217 planar steps, four coincident face transitions, and
tuple SHA-256
`1410a3abd90478627a638109cc31971802e53b3590015f83ebe4d8a57923a13d`.
The grid was 478 by 309 cells, with 447 goal-union cells. The D_SENSE graph
capture uses its distinct later prefix and 66-Node zero-transition path; its
graph result is not the LOAD12 prefix.

The current reader's queue BFS and separate heap/Dijkstra agree per face. For
LOAD12, 46 TOP goals and 415 BOTTOM goals were reachable, and each passes its
own positive-Manhattan 3:1 length test. The shortest relaxed lengths were 2,510
TOP and 3,250 BOTTOM. This is only a fixed-prefix geometric relaxation. For
D_SENSE, the source path reports direct distance 210; the shortest TOP and
BOTTOM goal lengths are 650 and 8,010, and no goal passes its own length test.

Neither graph proves BranchSearch, finalizer, bend, clearance, pad access,
electrical connectivity, full-layout feasibility, avoidability, or route
adoption. `feasibility` remains `UNKNOWN`; legal-route, layout-qualification,
and route-adoption flags remain false. `playable100` remains zero and beta is
held.

## Evidence and boundaries

The [validation record](validation.md), [qualification summary](qualification.json),
[combined input audit](final-input-audit.json), and [selected controls](validation-controls.json)
record the exact source and finite acceptance. Raw sources, launches, failed
attempts and process receipts remain in the preserved external QA bundle.

No full-30 owner-excluded suite, HARD/PSYCHOTIC data, GWT/player/electrical
renewal, or normal three-by-two-by-31 matrix was run for this diagnostic. HARD
and PSYCHOTIC remain unavailable/unrun; protected current data was not read;
historical data boundaries remain uncertified. See [publication receipt](publication.json).
Implementation `1ac4d845ce15739afcc0011d237de77fc6064162` is verified on
origin/codex/q100-physical-feasibility; completion Gmail `1a1a2bd2ae69511a`
was sent once. Acceptance covers the diagnostic contracts only.

The next unstarted milestone is a finite actual BranchSearch/finalizer replay
of any relaxed candidate before considering generic via-reduction adoption.
That work must not promote this prefix relaxation into a full-board or beta
claim.
