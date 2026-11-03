# Accepted LOAD12 branch capture wire

Frozen revision **1** for the test-only `P07AcceptedBranchCaptureDiagnostics`
helper and its consumer. This schema describes the source-bound LOAD12 branch
event only. It does not claim via avoidability, routing treatment, chronology
proof, route adoption, layout qualification, or playable100. Production code,
routing policy, request limits, and the existing fourteen D_SENSE records stay
unchanged.

## API

All types are package-private nested types of
`P07AcceptedBranchCaptureDiagnostics`. Production objects are accepted as
`Object` because `Search`, `BranchSearch`, `Publisher`, and their `Node` values
are private implementation types; implementations may only read them.

```java
static SeedSnapshot captureInitialSeeds(Object search, Object branch);

static final class SeedSnapshot {
    int nodeCount();
    boolean containsNodeIdentity(Object node);
    String toJson();
}

static final class Context {
    Context(Object routeSession, Object search, Object branch,
            Object publisher, SeedSnapshot seeds);
}

static PreCursor beginPre(Context context);
static PostCursor beginPost(Context context, PreResult preResult);

static final class PreCursor {
    boolean advance(int maximumWork);
    int lastWork();
    boolean isComplete();
    PreResult result();
}
static final class PostCursor {
    boolean advance(int maximumWork);
    int lastWork();
    boolean isComplete();
    PostResult result();
}

static final class PreResult {
    int pathNodeCount();
    int transitionCount();
    int prefixHoleCount();
    String netId();
    int branchOrdinal();
    String prefixHoleIdAt(int index);
    String prefixHoleFingerprintAt(int index);
    String pathTupleSha256();
    String toJson();
}
static final class PostResult {
    int postHoleCount();
    int createdHoleCount();
    int createdTraceCount();
    String postHoleIdAt(int index);
    String postHoleFingerprintAt(int index);
    String createdHoleIdAt(int index);
    String toJson();
}
```

The source driver calls `captureInitialSeeds` while the target branch is newly
created and still has its two source seed nodes in `BranchSearch.queue`. It
retains those exact Node identities. The driver then resumes normal
`advanceSlice(1)` calls. After FOUND transfers the same branch's actual end
Node to Publisher, it pauses at Publisher stage 1 with
`qualityIndex == reversed.size()`, constructs `Context` using the retained
BranchSearch reference and actual Publisher, and drains `PreCursor`. It then
resumes the same route session until that Publisher completes, before any next
branch is created, and drains `PostCursor` with the same Context and PreResult.

The Context validates live source references at both boundaries. At pre:
`routeSession.search == search`, `search.branch == null`,
`search.publisher == publisher`, the retained branch is the captured branch,
`publisher.end` is the found Node, its source net/start/root/ordinal match, and
Publisher is quiescent at stage 1 after quality measurement. At post:
the retained Search is still the same object, `search.branch == null`,
`search.publisher == null`, the retained Publisher reached stage 6, the
current net and branch ordinal still match, the source reached-pad list gained
the branch start exactly once, and no successor branch has started. Source
references and scalar state are checked before and after each cursor. The
driver separately verifies its macro owner, route budget, and retained Search.

`SeedSnapshot` records the actual queue nodes in heap-array order and retains
their identities. The first path Node must be one of those exact objects. No
path or seed Node is reconstructed from coordinates. The helper does not
write source fields, invoke graph queries, or change any source state.

## Bounds, accounting, and failure behavior

- `advance(maximumWork)` accepts only `1..32768`; one call performs at most the
  requested work. A complete pre+post capture allows at most 640 calls and
  `640 * 32768` aggregate work units.
- A path may contain at most 4096 Nodes. The grid may contain at most 250000
  cells; the combined two-face goal mask therefore has at most 500000 booleans.
- Hole and trace inventories are capped at 48 and 100000 records respectively.
  IDs and other source strings are capped at 4096 Java UTF-16 code units.
- Work is charged for every visited source row/value, Node/edge, goal cell,
  hole/trace comparison, lineage/transition match, path-run point, digest input
  byte, and UTF-16 code unit scanned or emitted while escaping JSON. A scan,
  comparison, hash, or payload may not run atomically past the current quantum.
  Each full payload String copy is charged by its exact UTF-16 length over
  bounded `advance` work before the JVM copy. The copy is one bounded atomic
  `StringBuilder.toString()` operation after that precharge. Seed capture is
  bounded to the initial two queue entries and reports its entry count in
  `SeedSnapshot`. A separate finalization unit is debited when assembling the
  immutable result, including when the last string-credit quantum ends exactly
  at its boundary.
- `work.activeMillis` includes cursor setup, all `advance` work, nested prefix
  inventory work, and a full payload-sized placeholder String materialization.
  The self-referential `activeMillis` value excludes replacing its fixed-width
  digits and the final immutable String copy that contains those digits. Both
  copies are charged by UTF-16 length; the driver's outer clock includes the
  digit patch, final copy, and enclosing constant-size result/adapter wrappers.
- The native Pre and Post `work` objects keep their four and five numeric
  counters, respectively, in 19-character JSON spans. The final values are
  right-aligned after the other span characters are replaced; this padding is
  part of `serializedUtf16Units`. The nested `prefixInventory` is embedded as
  raw JSON from the existing prefix helper. Its final `activeMillis` is the
  legacy compact decimal representation because that helper shrinks its
  19-character placeholder when applying the measured value. The native
  stream emits ordinary non-surrogate Unicode literally, uses JSON short
  escapes for quote, backslash and common controls, and escapes other controls,
  DEL, and each Java surrogate code unit as `\uXXXX`. Unit counts describe the
  exact final helper body, including those emitted escapes.
- Any missing boundary, changed source reference/value, incomplete cursor,
  malformed Node chain, non-unique/incorrect lineage, cap overrun, payload
  overrun, or failed identity/geometry comparison throws. No partial result is
  serializable and no failure can be represented as PASS. There is no path
  truncation or placeholder success.

`lastWork` is the work consumed by the most recent `advance` call; it is zero
before the first call, retains the final call's debit when that call completes
the cursor, and returns to zero on a later call to an already-complete cursor.
`isComplete` becomes true only after result assembly and materialization.
`result()` and `toJson()` throw before completion.

## Stable encodings

1. **Node path tuple hash.** `path.tupleSha256` is SHA-256 over UTF-8 bytes of
   the actual ordered Node tuples. Each tuple is
   `index,parentIndex,cell,x,y,layer,direction,transitions,cost,heuristic,serial,key`;
   decimal integers use base 10 with no leading zeroes, fields are comma
   separated, tuples are newline separated, and there is no trailing newline.
   The start Node has `parentIndex=-1`; each later Node's parent index must be
   the immediately preceding path index. Layer is the source ordinal `0` or
   `1`. The key is exactly
   `(((transitions*2+layer)*5+direction)*cells)+cell`.
2. **Goal mask.** `goal.faceBits` has exactly one ASCII character per grid cell
   in row-major cell order: `0` means neither face, `1` TOP only, `2` BOTTOM
   only, and `3` both. The character string is SHA-256 hashed as UTF-8 into
   `goal.faceBitsSha256`. `goal.cellCount` counts unique grid cells whose
   character is nonzero; `topCount` and `bottomCount` count cells with each
   corresponding face bit, and `bothCount` counts `3` characters. Thus
   `cellCount = topCount + bottomCount - bothCount`. `work.goalCells` is the
   number of mask cells scanned and equals `grid.cells`, including cells whose
   face character is `0`. The found endpoint must be a set goal bit on its
   actual layer.
3. **Trace fingerprint.** `traceFingerprint` is SHA-256 over UTF-8 bytes of
   `sourceIdField + netIdField + startPadIdField + endPadIdField + layer + LF +
   exposure + LF + width + LF + xCsv + LF + yCsv`, where each string field is
   `-1:` for null or `<Java String.length()>:<literal value>`, `xCsv` and
   `yCsv` are comma-separated base-10 integer arrays, and layer/exposure use
   enum names. The fingerprint includes every immutable PcbTraceGeometry
   field.
4. **Hole rows.** Prefix rows use `P07PrefixAllocationDiagnostics.Row.snapshot`
   and its existing `toJson()` unchanged. The strict pre cursor requires each
   creator branch ordinal to be less than the active branch ordinal. Post
   rows use the same `Row.snapshot` and `toJson()` to report actual current
   creators; creator ordinal 73 is permitted only in this post inventory.

## Closed JSON objects

All objects below are closed: no additional keys are permitted. Keys are
serialized in the order listed. Nullable values are JSON `null`; arrays are
never omitted. Enum values are exact Java enum names. The two helper bodies
are nested as JSON objects, not quoted JSON strings.

### `SeedSnapshot.toJson()`

```json
{
  "schema": "p07-accepted-branch-seeds-r1",
  "queueSize": 2,
  "faceMask": 3,
  "nodes": [
    {"heapIndex": 0, "cell": 0, "x": 0, "y": 0, "layer": 0,
     "direction": 4, "transitions": 0, "cost": 0, "heuristic": 0,
     "serial": 0, "key": 400},
    {"heapIndex": 1, "cell": 0, "x": 0, "y": 0, "layer": 1,
     "direction": 4, "transitions": 0, "cost": 0, "heuristic": 0,
     "serial": 1, "key": 900}
  ]
}
```

`nodes` contains exactly `queueSize` rows in source heap-array order. The sample
uses `cells=100` for key-shape illustration only; numeric values are not
fixture goldens. `faceMask` uses bit 0 for TOP and bit 1 for BOTTOM. All node
fields and heap positions come from the actual queue Nodes.

### `PreResult.toJson()`

```json
{
  "schema": "p07-accepted-branch-pre-r1",
  "source": {
    "pass": 1, "netId": "LOAD12", "netIndexNextFetch": 4,
    "netPosition": 3, "netOrder": ["NET_A", "NET_B", "NET_C", "LOAD12"],
    "branchOrdinal": 73, "policy": "FULLER_TWO_LAYER",
    "templateHoleCount": 0, "startPadAttachment": "PLATED_THROUGH_HOLE",
    "startCopperFaceMask": 3, "rootCopperFaceMask": 3,
    "startPadId": "", "rootPadId": "",
    "startX": 0, "startY": 0, "rootX": 0, "rootY": 0,
    "reachedPadIds": [], "reachedPthPadIds": [],
    "grid": {"minX": 0, "minY": 0, "width": 10, "height": 10,
      "cells": 100, "primaryLayer": "TOP", "maximumVias": 48,
      "maximumTransitions": 4}
  },
  "seeds": {"schema": "p07-accepted-branch-seeds-r1", "queueSize": 2,
    "faceMask": 3, "nodes": []},
  "prefixInventory": {"stability": {"prefixGeometrySha256": "",
    "liveBeforeVerified": true, "liveAfterVerified": true,
    "holeIdentitiesVerified": true, "lineageVerified": true},
    "work": {"advanceCalls": 0, "workSteps": 0, "rowsScanned": 0,
      "netComparisons": 0, "netTotalsWritten": 0, "activeMillis": 0},
    "totals": {"totalHoles": 0,
      "byKind": {"VIA": 0, "PLATED": 0, "NON_PLATED": 0}, "byNet": []},
    "rows": []},
  "prefixTraces": {"count": 0, "rows": []},
  "path": {
    "nodeCount": 0, "gridSteps": 0, "length": 0, "bends": 0,
    "transitionCount": 0, "directDistance": 0, "tupleSha256": "",
    "nodes": [], "planarRuns": []
  },
  "goal": {"cellCount": 0, "topCount": 0, "bottomCount": 0,
    "bothCount": 0, "faceBits": "", "faceBitsSha256": "",
    "foundGoal": true},
  "nativePublisher": {"stage": 1, "qualityIndex": 0, "nodeCount": 0,
    "length": 0, "bends": 0, "directDistance": 0,
    "endCell": 0, "endX": 0, "endY": 0, "endLayer": 0,
    "endTransitions": 0, "endTerminalPadId": null},
  "work": {"advanceCalls": 0, "workSteps": 0, "prefixWorkSteps": 0,
    "seedEntries": 2, "pathNodes": 0, "goalCells": 0,
    "traceRows": 0, "serializedUtf16Units": 0, "activeMillis": 0},
  "stability": {"liveBeforeVerified": true, "liveAfterVerified": true,
    "seedIdentityVerified": true, "pathChainVerified": true,
    "prefixLineageVerified": true, "goalMaskVerified": true,
    "traceIdentityVerified": true}
}
```

`prefixInventory` has exactly the unchanged body schema emitted by
`P07PrefixAllocationDiagnostics.Cursor.bodyJson()`: `stability`, `work`,
`totals`, and `rows`. `prefixTraces.rows` are sorted in actual layout order and
contain the trace row schema below. `path.nodes` are ordered from start seed to
found Node; each row has exactly the twelve fields from encoding 1
(`index,parentIndex,cell,x,y,layer,direction,transitions,cost,heuristic,serial,key`).
`path.planarRuns` contains the ordered compacted
same-layer geometry runs expected from the actual path, each with exactly
`{"layer":int,"firstNodeIndex":int,"lastNodeIndex":int,"xPoints":[int],"yPoints":[int]}`.
Each run has at least two points; collinear points in the same direction are
removed, while endpoints and face transitions are retained. `nativePublisher`
copies Publisher values at stage 1; `directDistance` is Manhattan distance
from the branch start pad to the found cell, not to the root pad. Independent
path `length`, `bends`, and `transitionCount` must agree with the native
Publisher metrics where applicable.

The `source` object has exactly the keys shown above. `netIndexNextFetch` and
`netPosition` are the actual Search index and `netIndexNextFetch-1`; `netOrder`
is the actual ordered Search net vector. `templateHoleCount` is the actual
empty Session template count. `policy` and `maximumTransitions` are read from
the actual source policy. `startPadAttachment` is the actual attachment enum
name. `startCopperFaceMask` and `rootCopperFaceMask` use TOP bit 0 and BOTTOM
bit 1 from `PcbCopperAccess` at those pads; they are independent of
`seeds.faceMask`.

### Trace row

Every trace row has exactly:

```json
{"index":0,"sourceId":"p07/LOAD12/73/0","netId":"LOAD12",
 "startPadId":"","endPadId":null,"layer":"TOP","exposure":"EXPOSED",
 "width":10,"xPoints":[0,0],"yPoints":[0,10],"traceFingerprint":""}
```

Sample values are shape only. X/Y arrays are the actual immutable source arrays.
Trace rows have the same closed schema in `prefixTraces.rows` and
`post.createdTraceRows`.

### `PostResult.toJson()`

```json
{
  "schema": "p07-accepted-branch-post-r1",
  "source": {"pass": 1, "netId": "LOAD12", "branchOrdinal": 73,
    "searchBranches": 73, "currentNet": "LOAD12", "searchBranchIsNull": true,
    "searchPublisherIsNull": true, "publisherStage": 6,
    "startPadId": "", "reachedStartCount": 1,
    "preHoleCount": 0, "preTraceCount": 0},
  "post": {"holeCount": 0, "createdHoleCount": 0, "createdTraceCount": 0,
    "holeRows": [], "createdHoleIds": [], "retainedHoleBindings": [],
    "createdTraceRows": [], "retainedTraceBindings": [],
    "transitionBindings": []},
  "work": {"advanceCalls": 0, "workSteps": 0, "aggregateWorkSteps": 0,
    "holeRows": 0, "traceRows": 0, "transitionMatches": 0,
    "serializedUtf16Units": 0, "activeMillis": 0},
  "stability": {"liveBeforeVerified": true, "liveAfterVerified": true,
    "searchIdentityVerified": true, "publisherCompleted": true,
    "reachedStartVerified": true, "holeIdentityVerified": true,
    "traceIdentityVerified": true, "transitionBindingsVerified": true,
    "traceRunsVerified": true},
  "claims": {"avoidability": "UNKNOWN", "chronologyProof": false,
    "routeAdoption": false, "layoutQualification": false, "playable100": 0}
}
```

`post.holeRows` are the complete actual post inventory, ordered by Java String
ID order and encoded with the existing prefix `Row.toJson()` fields. The
resumable post inventory cursor validates strict prior-branch ownership using
`branchOrdinal + 1` as a validation horizon, so the just-published current
branch is prior to that horizon. This horizon is not source metadata: event
fields and created-row ownership checks continue to use the actual captured
branch ordinal. A hole
row has exactly `index,id,netId,kind,x,y,drillRadius,landRadius,exposure,
fingerprint,creatorNetPosition,creatorBranchOrdinal,piece`. `createdHoleIds`
are the Java String-order IDs absent from the pre inventory.

`retainedHoleBindings` rows have exactly
`{"id":string,"preFingerprint":string,"postFingerprint":string,"preIndex":int,"postIndex":int,"sameObject":true}`.
`retainedTraceBindings` rows have exactly
`{"sourceId":string|null,"preFingerprint":string,"postFingerprint":string,"preIndex":int,"postIndex":int,"sameObject":true}`.
Indexes are reported for provenance only; matching uses IDs and retained
object identity, never old TreeMap slots.

`transitionBindings` has one row for each actual path face flip, in path order,
with exactly
`{"pathNodeIndex":int,"fromLayer":int,"toLayer":int,"cell":int,"x":int,"y":int,"netId":string,"viaId":string,"viaFingerprint":string,"creatorNetPosition":int,"creatorBranchOrdinal":int,"piece":int,"createdInBranch":boolean}`.
Each row resolves uniquely to an actual same-net VIA at the flip coordinate.
`createdInBranch` is true only if that VIA ID was absent before publication.
Created-hole count is deliberately independent of transition count. The real
P06 Publisher microfixture records a 2-transition/2-created-hole creator and a
0/0 retained-via consumer; the separate independent Python literal reader
fixture exercises 4 transition bindings against 3 created holes. Those
synthetic reader counts establish that the fields are not interchangeable;
they are not claims about the LOAD12 source run.

`source.searchBranches` remains 73 and `source.currentNet` remains LOAD12 at
the post boundary. `reachedStartCount` is exactly one. `post.createdTraceRows`
must match the ordered `path.planarRuns` by net, layer, and geometry, allowing
only same-direction collinear compaction performed by normal Publisher. No
future net or full-board completion oracle is applied to this incomplete
prefix.

## Diagnostic append

The adapter appends exactly one closed event after all capture, accounting,
original D_SENSE, NULL/PROBE parity, and cleanup checks pass. The adapter
validates every header value against the live macro context before emission.
The outer event has exactly these keys in this order, followed by the closed
nested PreResult and PostResult objects above:

```text
RB100_LOAD12_BRANCH_EVENT {"protocolVersion":1,"sourceRevision":18,"runId":"<actual run id>","operationId":"QUALITY_PROBE","scope":"ACTUAL_LOAD12_BRANCH_ENTRY_PATH_AND_PUBLICATION_ONLY","rootSeed":"9007199254740993","placementSeed":"-6587958367974952701","routingSeed":"8445301674744508279","routeRequestIndex":0,"selectedPlacementIndex":0,"selectedPlacementAttempt":4,"status":"PASS","claims":{"avoidability":"UNKNOWN","chronologyProof":false,"routeAdoption":false,"layoutQualification":false,"playable100":0},"pre":<PreResult JSON object>,"post":<PostResult JSON object>}
PASS: RB100 LOAD12 branch contracts {"protocolVersion":1,"sourceRevision":18,"status":"PASS","runId":"<actual run id>","operationId":"QUALITY_PROBE","events":1,"scope":"ACTUAL_LOAD12_BRANCH_ENTRY_PATH_AND_PUBLICATION_ONLY","rootSeed":"9007199254740993","placementSeed":"-6587958367974952701","routingSeed":"8445301674744508279","routeRequestIndex":0,"selectedPlacementIndex":0,"selectedPlacementAttempt":4,"pathNodes":0,"transitions":0,"createdHoles":0,"claims":{"avoidability":"UNKNOWN","chronologyProof":false,"routeAdoption":false,"layoutQualification":false,"playable100":0}}
```

The summary JSON keys are closed and shown in order. `pathNodes`, `transitions`,
and `createdHoles` are actual PreResult/PostResult counts, replacing the shape
zeros above. `runId` is copied from the actual macro run context. The approved
seed strings, request and placement indices, operation, scope, protocol, and
source revision must equal the frozen values above. The event and summary
claim objects carry the same limits.

No event or PASS line is emitted for incomplete capture, mismatch, timeout,
budget overrun, or failed cleanup. The revision/input-run-result wrapper stays
revision 18 / v3. The first fourteen D_SENSE records remain in their existing
order with their semantic oracle contracts intact; byte-for-byte output is not
promised because source revision, timings, and work accounting change.
