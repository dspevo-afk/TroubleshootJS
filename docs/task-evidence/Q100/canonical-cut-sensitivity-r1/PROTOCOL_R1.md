# Cut-edge sensitivity protocol R1

Frozen before P7 implementation or case execution. Implements the question in
published P6 NEXT_SENSITIVITY_PLAN.md at receipt23f94f8f. This is offline,
counterfactual graph analysis; CircuitJS and every physical/electrical oracle stay
unchanged. No generator, P07 search, routing treatment, new seed or epoch change.

## Source and parent authority

The private input spec is canonical UTF-8 JSON plus LF, at most4MiB. The caller
provides its SHA-256 separately. Reject duplicates, nonfinite numbers, booleans in
integer fields, unknown keys, relative/noncanonical/reparse paths or changed files.
Bind P6 qualification d7f39f1a77e302e9e883b4aedbdeb9c416a93cf4cfa85029710b04da5b25d692,
P6 input b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771,
P6 reader1d58c06ecb3581ad7d8c9eef1dfcd653d8ba1e5b797b2dc7cda76b2ed621a96e,
published implementationf4df753d/receipt23f94f8f and the exact final observe-r6 run.
Pin all consumed code, schemas, existing graph oracles, P6 source inventory,
transport/index/operation/attribution/stage files, selected tools and spec sources.
The detailed spec enumerates exact field names and file sizes/hashes before use.

The host first runs the unchanged selected P6 OBSERVE reader against its genuine
original run/pins/context. Require PASS_STAGE_OBSERVATIONS_ONLY, original SHA,
actual selected Python/argv/process identity, exit0 and proven termination.
Never construct a forged parent context. Preserve the prerequisite process receipt;
pass its exact path/hash to the analysis worker. Recheck all consumed source/input
bytes after analysis so prior proof cannot silently become stale.

Keep raw native graph SHA ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a
separate from the graph codec's canonical semantic digest. Prefix remains
6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb.
Exact source identity: BASE/SEPARATE root9007199254740993,
placement-6587958367974952701/routing8445301674744508279,
request0/attempt4/pass2 A_COIL_LOW QA.C->DA.A, receipt307/lineage77/H48.

## Exact variants and algorithms

Cases are ordered CONTROL, B_SENSE_CUT_ONLY, B_REF_CUT_ONLY, BOTH_OBSERVED_CUTS.
Re-derive the complete22-query outgoing directed planar cut from the base graph's
full zero-VIA reachable closure. Join by exact ordinal/layer/fromState/toState/bit
and coordinates to accepted P5 attribution and P6 events, not a convenient fixture.
Require eight positive foreign-clearance observations, four for each named net,
and14 other rows with9 actual legal-move and5 traversal terminal events.
The edge list is sorted in the original cut order; duplicates or missing rows fail.

Open respectively0,4,4,8 currently-disabled directed bits. A descriptor binds
query ordinal, layer, fromState, toState, bit and actual targetClearanceNet. A bit
must be one of1/2/4/8, in bounds, on the same face and absent in the original mask.
Do not add reverse edges. Preserve every unselected step bit, vias, goals, starts,
primary face, dimensions, case ID, protocol/model and original graph object.
The14 geometric cut bits remain closed. Prove exact changed-bit sets independently.

Use the existing exact FixedPrefixGraph type, immutable tuples and pinned codec
validation. A lazy override class would change the oracle and is forbidden. Build,
validate, analyze and release one variant at a time; keep the base immutable.
Each logical graph clone has at most1,048,576 entries. Do not emit full hypothetical
graphs or paths. Reuse the existing minimum-VIA oracle unmodified; minimum0 agrees
with an independently implemented directed planar BFS. Positive VIA counts or
None both exclude zero-VIA connectivity; report their actual values distinctly.

Independent BFS uses both actual starts (initialFacesMask3), outgoing N1/E2/S4/W8
edges and only same-face movement. It exhausts the complete planar closure even
after a goal is seen. Report reachability, reached-state count, enabled directed
edges examined, goal-hit count and SHA of the zero/one reached-state bytearray.
Preserve the captured per-face goals. CONTROL must reproduce12 states/23 edges.
Compare its minimum-VIA result to the recorded accepted parent, not a new golden.

## Work and process accounting

Retain90s analysis work,640 measured analysis groups,5s per complete group,
4096-entry copy/BFS batches, clone<=1M entries, total observation units<=32768,
8MiB aggregate input/result/output,64KiB result/index/line,32KiB source chunks,
120s conservative cleanup and1800s outer limits. Original native32768 P07 slice,
1M expansions,48 holes/four flips remain unchanged and unrun here.

Count offline work separately from actual new P07 steps/advances/expansions, all0.
A BFS batch expands at most4096 states, each with at most four planar neighbors;
report actual pops and edges separately. Build batches copy at most4096 step
entries. Existing codec validation and minimum-VIA calls are explicitly opaque
groups: time the entire unmodified call, charge it once, report its actual counters
and fail if it exceeds5s. Do not manufacture internal owner advances or divide an
over-budget opaque call into fictitious successful groups. Count all copy, graph
validation, hashing, BFS/oracle and result serialization work in the analysis clock.
Transport/schema/input validation inside the worker is timed work too.

The existing P6 whole-parent qualification is a separate measured prerequisite,
not a P7 analysis group. Report its wall time and process result separately.
The selected host bounds each Python child at180s (no increase of existing reader
limits). Conservative cleanup charges full prerequisite+worker wall plus post-source
verification and owned-output cleanup; must stay below120s. Source proof and all
post-read hashes remain mandatory. Any timeout, incomplete case, failed cleanup,
missing proof or nonzero unexpected exit is FAIL/BLOCKED, never PASS.

## Worker and host transport

Selected PS5/Python hashes remain those in P6. No policy/global runtime override.
The analysis CLI is MODE SPEC_ABSOLUTE SPEC_SHA OUTPUT_ABSOLUTE
PARENT_RECEIPT_ABSOLUTE PARENT_RECEIPT_SHA. MODE is CANARY or ANALYZE. CANARY uses
literal graphs and no real case; parent arguments are explicit '-' sentinels and
its result says parent/cases NOT_RUN. ANALYZE requires the genuine successful
parent receipt above. Sources/specs/inputs are stable-read and pinned before use.

Output directory is new, empty, task-owned OS-temp outside all source roots.
CREATE_NEW result file sensitivity-result.json, one JSON object+LF <=65536B.
Stdout is one marker plus bounded JSON index+LF; stderr empty on success.
On error use sanitized static codes, preserve the failure and partial output,
release source/graph references, and exit nonzero. The host records actual launch/
source/selected-tool/process/argv/cleanup receipts. No file/path/process claim is
made from an INTENT record alone. Validate the real selected host early with a
literal positive and source/spec/output-boundary negatives before actual cases.

Final result schema: tsj-canonical-cut-sensitivity-result-v1. Required top-level
keys: schema,status,mode,specSha256,scope,parent,source,graph,cut,cases,work,acceptance,
limitations. Exact nested fields are frozen in the schema addendum before coding.
scope=CANONICAL_BASE_DIRECTED_CUT_SENSITIVITY_ONLY. Every result has route/layout/
electrical/q100 acceptance false and productionFeasibility UNKNOWN. Hypotheses
explicitly have counterfactual=true and actualCaptureAttested=false. Parent proof
may attest the unchanged input capture; it never attests a hypothetical variant.

## Validation and publication

Freeze the exact source/input/output schema addendum before producer code. Literal
and independent tiny-graph tests cover direction, face/start/goal semantics, exact
owner subsets, duplicates, unselected geometric bits, zero/no-path cases, type/
source/hash/EOF/work and cleanup failures. Use an independent tiny closure oracle;
do not mirror the BFS implementation or bless actual output as expected data.
Run actual selected CLI malformed source/candidate/edge-set/EOF/hash/budget controls.
All four cases must finish and both algorithms agree within original bounds.
Audit that all reused production/P6 inputs remain unchanged. No Java/GWT/Player or
electrical/population renewal follows from this Python-only diagnostic.

Root reviews integrated/cached/source/privacy evidence, updates architecture,
roadmap/report, then normal commit/push/exact SHA and one completion Gmail only
after full finite acceptance. Preserve earlier failures and other worktrees.
Playable100=0/betaHELD; full30 excluded. HARD/PSYCHOTIC stay unavailable and unread/
unrun here; historical protected unread boundary UNCERTIFIED. No browser/junction
or real net removal/reroute is part of this scope.
