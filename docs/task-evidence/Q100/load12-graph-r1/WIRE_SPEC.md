# LOAD12 zero-new-via graph API and wire

Status: root-approved private API and closed wire. This packet uses diagnostic
source revision 20 and zero-via graph schema r5. It does not revise production layout26,
dependency28, VIA60, route limits, source ordering or electrical state.

## Operation selection and receipt shape

One operation selects exactly one `GraphTarget`: `D_SENSE` or `LOAD12`. It never
materializes both full graphs in one operation. The existing D_SENSE target
entry remains; the new LOAD12 target entry uses the same source driver and
completes the existing LOAD12 publication/post capture and D_SENSE rejection,
NULL/PROBE parity, accounting and cleanup proof.

Each entry emits the same 16 receipt records, in the current order: the common
scalar and path records, one zero-via graph event/summary pair, prefix allocation
event/summary, and LOAD12 branch event/summary. The graph pair has one explicit
target in both records. No extra marker is introduced. Every record in this
diagnostic revision uses `sourceRevision:20`; `protocolVersion` remains `1`.

The D_SENSE suite remains
`tests/contracts/Rb100QualityZeroViaGraphContractTest.java`; the LOAD12 suite is
the new root-owned `tests/contracts/Rb100Load12ZeroViaGraphContractTest.java`.
Each input spec names exactly one `target` and its matching `suite`, and its
source metadata/record shape must agree with that target. The v5 input, native
run and reader result schemas are `p07-quality-zero-via-input-v5`,
`q100-quality-zero-via-native-run-v5`, and
`q100-quality-zero-via-reader-result-v5`. The shared source revision is 20 and
the post-addition native input inventory is 1077 files (850 production Java,
221 contract Java, 6 static inputs). Readers reject a suite/target/revision or
record-shape mismatch, including old revision-19/v4 input. Other 28 Python
sources remain byte-frozen.

Graph targets and scopes are closed:

| target | scope | required input kind |
|---|---|---|
| `D_SENSE` | `ACTUAL_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY` | `D_SENSE_PUBLISHER_PATH` |
| `LOAD12` | `ACTUAL_LOAD12_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY` | `LOAD12_ACCEPTED_PRE_RESULT` |

The target and scope in the graph event and summary must match each other and
the operation's typed request. All existing negative claims remain false:
`feasibility` is `UNKNOWN`; `legalRouteProof`, `layoutQualification` and
`routeAdoption` are `false`.

## Native producer API

`P07ZeroViaGraphDiagnostics` exposes this source-only API:

```java
enum Target { D_SENSE, LOAD12 }

interface GraphInput {
    String sourceKind();                 // one of the two values in the table
    int seedLayerMask();                  // actual retained source seed mask
    int pathNodeCount();
    int pathCellAt(int index);
    int pathXAt(int index);
    int pathYAt(int index);
    int pathLayerAt(int index);           // TOP=0, BOTTOM=1
    int pathDirectionAt(int index);       // N=0,E=1,S=2,W=3, seed/flip=4
    int pathTransitionsAt(int index);
    int pathCostAt(int index);
    int pathHeuristicAt(int index);
    int pathSerialAt(int index);
    int pathKeyAt(int index);
    String pathTupleSha256();             // LOAD12 PreResult value; null for D_SENSE
    String goalFaceBitsSha256();          // LOAD12 PreResult value; null for D_SENSE
    int goalCellCount();                  // LOAD12 PreResult value; -1 for D_SENSE
    int expectedGoalFaceMaskAt(int cell); // LOAD12 nibble; -1 for D_SENSE
}

Context(Target target, PcbBoardLayout layout, PcbNetRouter.Router[] faces,
        String net, PcbPadPlacement start, PcbPadPlacement root,
        PcbPadPlacement[] reached, boolean[] actualGoalMask, int width,
        int height, int originX, int originY, int pitch, int initialLayerMask,
        GraphInput input);
static Cursor begin(Context context);
boolean Cursor.advance(int maximumWork);
int Cursor.lastWork();
boolean Cursor.isComplete();
Result Cursor.result();

int Result.bodyLength();                 // O(1), virtual JSON UTF-16 length
void Result.writeBody(java.io.PrintStream out); // exact PrintStream class only
char Result.maskCharAt(int stream, int cell); // stream 0=TOP,1=BOTTOM,2=GOAL
```

`LOAD12_ACCEPTED_PRE_RESULT` is implemented by the immutable
`P07AcceptedBranchCaptureDiagnostics.PreResult`, through read-only accessors for
the actual captured Node tuples and goal-face bits. It exposes no mutable arrays.
The D_SENSE entry adapts its already captured `QualityPathSnapshot` as the
path-only `GraphInput`. The LOAD12 immutable `PreResult` implements the same
interface directly, without alias accessors or duplicate arrays. In both cases
`Context.actualGoalMask` is the authoritative reference to the current live
BranchSearch `goal` array. The cursor reads both face bits for each cell while
charging work and encodes their nibble directly into the GOAL mask array. It
computes the observed goal-face hash and nonzero-cell count and, for LOAD12,
compares every encoded face nibble plus the final hash/count against the
immutable PreResult values. It rechecks the live source against the captured
GOAL mask after graph scanning. The source array, not an expected digest, is
the graph's goal input. Context stores the actual goal reference and GraphInput
reference by identity; it does not eagerly copy a 4096-Node path or the goal
mask.

`Context` requires `GraphInput.sourceKind()` to match the requested target, a
grid cell count equal to `width * height`, and an initial-layer mask of `1..3`.
It requires `GraphInput.seedLayerMask()` to equal that initial-layer mask; both
values come from the actual retained source seed queue/objects.
It also binds the exact current `actualGoalMask` array, whose length is
`2 * width * height`. It binds the actual layout, both actual face Routers, net,
start/root/reached PTH objects, grid and target. Construction performs only
constant-size scalar/reference checks: it does not traverse, clone, or hash the
layout, Router effects, path, goal array, or reached-pad list. It captures the two
Router references, checks their layout and TOP/BOTTOM slots, and rejects later
replacement of either caller-array slot. Both actual Routers must already be
prepared for the selected net and its authoritative trace width; unsupported
unprepared captures fail before graph work. The literal fixture overload may
prepare its bounded expected transition table during test setup; actual D_SENSE
and LOAD12 operations consume existing read-only inputs through the primary API.
The cursor
traverses every canonical layout-geometry UTF-8 byte used by
`PcbBoardLayout.geometryFingerprint()` before and after graph scanning, compares
the full-stream SHA-256 and exact byte length, and retains the source-container,
row-order and object-identity guards. It likewise traverses every UTF-16 unit of
the complete Router observation-effect stream on both sides, comparing its
UTF-8 SHA-256, exact UTF-16 length and source identities. These are full-stream
digest comparisons, not retained-string byte-for-byte comparisons. Context and
cursor construction do not materialize either canonical source stream. It
imports each path Node, validates each reached-PTH identity and duplicate check,
copies/checks each goal cell, then rechecks reached-PTH identities and list
contents after graph scanning. All associated hashing is incremental under
`advance` work debits. The aggregate work report includes those imports/checks.
The completed `Result` contains immutable scalar/String metadata, hashes,
counts and work values, a small JSON header/suffix, and three private ASCII
mask arrays of `grid.cells` characters each. The cursor encodes TOP, BOTTOM and
GOAL mask characters in place, transfers ownership of those arrays to
`Result`, then clears all live references to Context, source arrays,
Router/layout objects, source views, walkers and cursor-owned array references
before returning the completed result. Result retains no Context, Cursor,
Router, layout, source view or mutable array alias. No mask array is exposed.
`maskCharAt` reads one stored character; `bodyLength()` returns the exact
virtual JSON UTF-16 length without assembling the body. Before writing its
first character, `writeBody(PrintStream)` rejects null and any runtime class
other than exactly `java.io.PrintStream`; this prevents an overriding
PrintStream subclass from receiving the owned mask arrays. It then streams the
small header, fixed separators, the three mask arrays and suffix directly to
the supplied base PrintStream; it does not create a full-body String. It
neither closes nor takes ownership of the stream.

The bounded writer fixture wraps a `ByteArrayOutputStream` in an exact base
`PrintStream` and compares emitted bytes with the expected small fixture JSON
and `bodyLength()`. It checks repeated writes for identical body text, checks
the base PrintStream error flag after writing to a closed stream, and checks
that null and a PrintStream subclass are rejected before output. The completion
fixture checks cleared source/cursor references and unchanged results after
repeated completion. This establishes the selected Java writer/framing
behavior; it does not claim an OS pipe, arbitrary sink or transport-level
proof. The actual adapter streams the body to its selected base PrintStream
and performs the same exact-class/null preflight before writing the event
prefix, so rejection cannot leave a partial receipt prefix.
The adapter holds the LOAD12 live graph
context exclusively through capture, saves this immutable result, drops live
cursor/context/source-view references, and records that release before resuming
Publisher. No graph cursor or source view may inspect a successor branch.

The LOAD12 PreResult accessors needed by this interface are:

```java
int pathCellAt(int index);
int pathXAt(int index);
int pathYAt(int index);
int pathLayerAt(int index);
int pathDirectionAt(int index);
int pathTransitionsAt(int index);
int pathCostAt(int index);
int pathHeuristicAt(int index);
int pathSerialAt(int index);
int pathKeyAt(int index);
String sourceKind();
int seedLayerMask();
String goalFaceBitsSha256();
int goalCellCount();
int expectedGoalFaceMaskAt(int cell);
```

Existing `pathNodeCount()` and `pathTupleSha256()` match the interface;
`netId()`, `branchOrdinal()`, and `transitionCount()` retain LOAD12 source
identity. The `goalFaceBitsSha256()`, `goalCellCount()` and
`expectedGoalFaceMaskAt(cell)` accessors expose immutable PreResult values; the
per-cell accessor reads the existing face-bit string without returning or
copying an array. The graph cursor recomputes the 12-field
Node tuple SHA-256 and goal-face SHA-256 from captured source values while
charging every path field/tuple byte and goal cell; it requires equality with
those PreResult values. Tuple `index` is the ordered path index and
`parentIndex` is `-1` for the first Node, otherwise the preceding index; the
cursor validates this chain while importing. PreResult retains its captured
immutable face-bit string and adds no second path or boolean goal array.

## Graph bounds and edge meaning

The existing graph algorithm remains a capture of directed ordinary edges from
the actual top and bottom Router predicates. It adds no VIA edge, hypothetical
copper, physics or shortest-path result. For each cell and each face it tests
the four in-bounds directed neighbors with the unchanged Router predicate.
Mask bits are N=1, E=2, S=4, W=8. Boundary bits are absent. Goal face nibbles
are `0..3` with TOP=1 and BOTTOM=2.

`seedLayerMask` is derived from the actual retained seed Node layers and
identities, not from the FULLER policy constant; `initialLayerMask` must equal
it, and the first witness Node's face must be present in it. The first Node's
physical coordinate must equal the actual start pad coordinate exactly and map
to its source grid cell; off-grid coordinates are rejected, never snapped.

The source path may contain `2..4096` actual Nodes and at most four coincident
cell face flips. Every planar source step must be one pitch on one face and must
exist in that face's captured directed mask. Each face flip must preserve the
physical cell/coordinates, change exactly one face, and increment the source
transition count. A flip is checked as a source-path annotation; it is never a
graph edge or an ordinary Router query. `D_SENSE` retains its source-bound
zero-flip path. `LOAD12` may contain the actual four flips. `maximumTransitions`
remains four for the route policy.

For LOAD12, the accepted PreResult's path, goal mask, grid and target metadata
must still describe the actual captured `pass=1`, `net=LOAD12`, branch ordinal
73, net position 3 / next-fetch index 4 source at the Pre Publisher boundary.
These values bind the approved request and are checked against the common
LOAD12 branch record; they are not substituted for current-source reads or
used to predict the graph masks. The approved request remains root seed
`9007199254740993`, placement seed `-6587958367974952701`, routing seed
`8445301674744508279`, request/index `0`, placement attempt `4`.

The source facts in PLAN are identity references, not replacement goldens:
222 path Nodes, 217 planar steps, four coincident flips, 17 prefix VIAs, 88
prefix traces, grid `478 x 309` at origin `(30,30)` and pitch `10`, and goal
union count `447`. The tuple and goal-mask hashes are recomputed from the actual
source accessors and cross-checked with the common LOAD12 capture record.

## Graph event and summary

`RB100_ZERO_VIA_GRAPH_EVENT` retains its marker. Its exact top-level keys are:

```json
{
  "protocolVersion": 1,
  "sourceRevision": 20,
  "runId": "<actual UUID>",
  "operationId": "QUALITY_PROBE",
  "target": "LOAD12",
  "scope": "ACTUAL_LOAD12_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY",
  "binding": {
    "branchIdentityVerified": true,
    "sourcePublisherUnchanged": true,
    "sourceKind": "LOAD12_ACCEPTED_PRE_RESULT",
    "pass": 1,
    "branchOrdinal": 73,
    "netPosition": 3,
    "netIndexNextFetch": 4,
    "netId": "LOAD12",
    "startPadId": "JLOAD.1",
    "rootPadId": "CSTORE_E.1",
    "sourceReleasedBeforeSuccessorWork": true,
    "reachedPthPadIds": [],
    "foundTerminalPadId": "<actual nullable terminal>",
    "endCell": 0,
    "endLayer": 1,
    "goalMaskLength": 295404,
    "goalUnionCellCount": 447,
    "policy": "FULLER_TWO_LAYER",
    "primaryLayer": 1,
    "initialLayerMask": 3,
    "maximumVias": 48,
    "maximumTransitions": 4,
    "prefixHoleCount": 17,
    "pathNodeCount": 222,
    "pathTransitionCount": 4,
    "pathTupleSha256": "<actual recomputed source tuple SHA-256>",
    "goalFaceBitsSha256": "<actual recomputed goal face SHA-256>"
  },
  "diagnostic": {},
  "feasibility": "UNKNOWN",
  "legalRouteProof": false,
  "layoutQualification": false,
  "routeAdoption": false
}
```

Dynamic values in the event and diagnostic examples are shape only; the event
carries actual captured values. `goalMaskLength` is the two-face Boolean mask
length; `goalUnionCellCount` counts grid cells with either goal-face bit, while
`work.goalCells` counts mask cells incrementally read and `grid.cells` is the
full grid cell count. For `D_SENSE`, `target` is
`D_SENSE`, `scope` is `ACTUAL_FIXED_PREFIX_ZERO_NEW_VIA_GRAPH_ONLY`,
`sourceKind` is `D_SENSE_PUBLISHER_PATH`, and all binding values come from that
actual D_SENSE source. The graph event and summary each contain exactly one
target and one scope, never both target payloads.

The exact graph diagnostic keys are `schema,stability,grid,path,work,masks`:

```json
{
  "schema": "p07-zero-via-graph-r5",
  "stability": {
    "prefixGeometrySha256": "<actual SHA-256>",
    "liveBeforeVerified": true,
    "liveAfterVerified": true,
    "liveEffectsUnchanged": true,
    "goalVerified": true,
    "originalPathVerified": true
  },
  "grid": {
    "width": 478, "height": 309, "cells": 147702,
    "originX": 30, "originY": 30, "pitch": 10,
    "startCell": 0, "initialLayerMask": 3,
    "endCell": 0, "endLayer": 1, "goalUnionCellCount": 447
  },
  "path": {
    "nodeCount": 222, "planarEdgeCount": 217,
    "transitionCount": 4, "tupleSha256": "<actual SHA-256>"
  },
  "work": {
    "advanceCalls": 0, "workSteps": 0, "copyEntries": 0,
    "comparisonEntries": 0, "geometryBytes": 0, "effectUnits": 0,
    "ordinaryQueries": 0, "goalCells": 0, "reachedPadChecks": 0,
    "encodedCells": 0, "pathNodes": 0,
    "pathEdgesVerified": 0,
    "tupleBytes": 0, "serializedUtf16Units": 0, "activeMillis": 0
  },
  "masks": {
    "topStepsHex": "", "bottomStepsHex": "", "goalFacesHex": ""
  }
}
```

`grid.cells == width * height`, `goalCells == cells`, and
`encodedCells == 3 * cells`. Goal cell `i` is read from
`actualGoalMask[i]` (TOP) and `actualGoalMask[cells + i]` (BOTTOM), in row-major
order. `geometryBytes` counts canonical layout fingerprint UTF-8 bytes
traversed for the before/after snapshots; `effectUnits` counts UTF-16 units
traversed in the resumable before/after Router-effect digests.
`reachedPadChecks` counts bounded source-identity,
PTH-attachment and pairwise-duplicate checks on the reached-pad list. Each mask
is exactly one lowercase hexadecimal
digit per cell in row-major order. `path.planarEdgeCount + path.transitionCount
== path.nodeCount - 1`; the transition count is at most four. `tupleSha256`
is SHA-256 over UTF-8 bytes of the actual Node tuples, ordered by path index.
Each tuple is the 12 base-10 fields
`index,parentIndex,cell,x,y,layer,direction,transitions,cost,heuristic,serial,key`;
`index` is the row's zero-based index and `parentIndex` is `-1` at row zero or
the preceding index thereafter. Tuples are separated by one LF byte, with no
trailing LF. `goalFaceBitsSha256` is SHA-256 over one ASCII digit `0..3` per
row-major grid cell, with TOP=1 and BOTTOM=2 and no separators.

The reported `workSteps` is at least the sum of
`copyEntries + comparisonEntries + geometryBytes + effectUnits +
ordinaryQueries + goalCells + reachedPadChecks + pathNodes +
pathEdgesVerified + tupleBytes + serializedUtf16Units`; this lower bound
counts every router-copy/compare item, source snapshot byte, graph query, copied
goal cell, reached-pad check, imported path Node, path edge check and tuple byte,
plus one bounded JSON-frame/mask-encoding pass. The cursor allocates three
`char[grid.cells]` arrays for the ASCII TOP, BOTTOM and GOAL masks; it reads the
live Boolean goal source directly and does not allocate a second two-face goal
array. It encodes each mask cell in place, one character per debit. The result
takes ownership of those arrays. `encodedCells == 3 * cells` is an informational
subcount of `serializedUtf16Units`, not an additional pass. The goal-face nibble
emitted after the live goal scan is compared with the copied source and, for
LOAD12, the immutable PreResult nibble in that same charged encoding step.
Additional state transitions and bounded finalization also debit work.

`work.serializedUtf16Units` is the exact Java UTF-16 length of the virtual
diagnostic JSON body, not the enclosing graph event or one of the larger receipt
lines. It equals `Result.bodyLength()`, which is O(1). Every numeric work
counter occupies a fixed 19-character JSON span, padded with JSON whitespace
before its digits. The graph cursor precharges the three mask arrays and the
small metadata header/suffix across `advance` quanta, then encodes the masks in
place; it does not allocate an output body buffer, placeholder body or final
body String. `Result.writeBody(PrintStream)` streams the fixed header, JSON
separators, owned mask arrays and suffix to the base stream after exact-class
preflight. `activeMillis`
includes cursor setup, all graph work, incremental tuple hashing, fixed-prefix
preparation and mask encoding. It excludes only the fixed-width counter patch
and final small immutable header String copy; those bounded operations are
covered by the 10,000-unit metadata credit. The adapter streams fixed event
metadata around the existing immutable result body without creating a second
full-event String. The per-case timer ends before receipt logging; the native
host clock includes stdout transport. JSON keys remain in the order shown.

Canonical source text uses fragments over immutable String references. Decimal
formatting uses a reusable 20-cell scratch buffer per geometry/effect stream;
the buffer is prepaid once per stream. It produces one digit per work debit
using negative arithmetic (including both signed minima), then emits one
UTF-16 unit per debit. Component terminal getter work and package-internal
connection scans are prepaid before their bounded source operations. Each
12-field Node tuple is streamed from bounded fragments into the tuple digest;
numeric formatting and digest bytes are charged incrementally, with no whole
tuple String copy. The fixed JSON metadata header reserves 10,000 work units
before construction, final counter patching and its small immutable String
copy.

The prepared-net precondition makes `Router.prepareNet` return immediately, so
ordinary queries modify only the observation clone's counters/conflicts/work.
One verified fixed-graph copy per face therefore serves both as the query graph
and the before/after comparison snapshot. Both complete live-graph comparisons,
the independent live effect stream, and all layout/goal/path/identity checks
remain. This removes a redundant second clone and duplicate pre-query comparison;
it changes no Router predicate, graph mask, routing budget, or acceptance oracle.

`sourceReleasedBeforeSuccessorWork` is true only after the adapter has retained
the immutable graph result and dropped live graph context/cursor references; the
adapter enforces this release before Publisher or other successor work resumes.
The LOAD12 event and summary bind to the same accepted PreResult path tuple,
goal-face hash/count and prefix-hole count. The D_SENSE entry binds to its actual
path and live BranchSearch goal-mask source.

The source summary marker remains
`PASS: RB100 quality zero-via graph contracts`. Its exact keys are
`protocolVersion,sourceRevision,status,runId,operationId,events,target,scope,
ordinaryQueries,sourceReleasedBeforeSuccessorWork,feasibility,legalRouteProof,
layoutQualification,routeAdoption`.
It has `sourceRevision:20`, `status:"PASS"`, `events:1`, and repeats the exact
event target/scope/query count and false/unknown claims. No second graph summary
is allowed for the same operation.

## Reader-derived analysis (not native source claims)

The current reader checks each target's masks and path independently, then runs
both a forward queue BFS and a separate stdlib heap/Dijkstra implementation over
the same directed ordinary-edge masks. The algorithms use only the initially
seeded face(s), never add face-flip edges, and evaluate each set goal face using
its own positive Manhattan distance and the existing `pathLength <= 3 * direct`
eligibility test. One planar edge costs one grid pitch in both shortest-path
implementations and in the Manhattan distance. A zero-distance goal is not
length-eligible.

The derived `analysis` object is not part of the native graph event. It contains
`target`, `scope:"FIXED_PREFIX_ZERO_VIA_LENGTH_ONLY"`, `queueBfs`,
`heapDijkstra`, and `crossCheck:"IDENTICAL_PER_FACE_DISTANCES_AND_GOAL_COUNTS"`.
Each algorithm object has exactly `seedLayerMask,faces,lengthEligibleGoals,
minimumEligibleLength,geometricRelaxation`; each face has exactly
`layer,reachableGoals,shortestAnyGoal,distanceCapturedEnd,lengthEligibleGoals,
minimumEligibleLength`. Unreachable distances and empty minima are JSON null.
The reader compares the complete BFS and heap/Dijkstra summaries before it
emits the derived record. Literal tests cover directed edges, multiple goals,
disconnected goals and zero-distance exclusions. Even an eligible relaxed
witness is not a BranchSearch, bend/turn/finalizer, clearance, access, electrical
or full layout proof. No result establishes avoidance, route adoption or
playability; `playable100` remains zero.

## Work and lifecycle caps

`advance(maximumWork)` accepts `1..32768`; no call may debit more than requested.
The pre+post operation stays within 640 outer work units, `640 * 32768` total
work, 90 seconds active graph time, 90 seconds per actual case, 5 seconds per
outer work unit, and 120 seconds cleanup. The route request, branch, grid,
ordering, VIA and transition limits remain `1,000,000`, `100,000`, `250,000`,
3, 48 and 4. Large path setup, tuple hashing, goal copying, graph queries,
mask encoding and metadata framing are resumable/charged. Any cap or source
stability failure throws; no partial result is serializable. Root alone runs
actors and gates. No protected data or HARD corpus is read.
