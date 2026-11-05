# Frozen OUT_F ordinary-edge graph wire R1

FROZEN2026-11-05 before graph implementation or new fixture/parser/native runtime.
Root reviewed the source-only proposal6e9cf218 and adopts the closed contract
below, including the final root clarifications. PLAN R2 is
76770083958cdbb4432b3ca7eeb5f3274b7f1789c66e9de9e0520f8a9fbb75ad;
its initial freeze and the original proposal remain in external QG. No runtime,
fit, routing outcome, qualification or publication is claimed. Source parent
e271e7afc5e961bfe90de866a661b732da2587c4.

Read/frozen inputs:
- PLAN e775c110a5a33ed7e39824544dbbf57c606f8bb46942e13c5449179fa53cbc2d.
- v5 WIRE 60f34fb75a5ea9c7eb8fee085fc625a251d1312ad5574fd3168de95f0df9dc93.
- Graph owner ce7817c8ee428d868c0d40a9a63ac560dc8272d8bbab0cd4988d5018792a2661.
- ActualGeneratorBranchOrderStudy.java and its strict reader at that parent;
  PcbLayerRoutingPrototype Search/BranchSearch/Publisher and Router predicates.
- Bounded source-only git show of read_load12_adrive_graph.py, lines1..300, for
  historical envelope/oracle separation only. No historical graph/data payload,
  HARD/PSYCHOTIC, holdout or full30 body was read. The sparse older v4 WIRE was
  absent; current v5 reader/source supplied inherited implemented field contracts.

## 1. Version, enablement, unchanged bounds

HEADER.schema = p07-actual-branch-order-study-v6. Reject v1..v5, including otherwise
well-formed reports. Every unchanged v5 key/value/guard stays in force. Index,
source manifest, spec and transport envelope schemas remain their existing v1.
The PUB target remains p07-publisher-target-v1 with unchanged fields/semantics.
There are still exactly five operations and complete request-zero receipts.

HEADER.arm adds graphCapture, exact boolean. Operations0..3 require false.
For native kinds, operation4 requires true iff HEADER.mode=AUTHORED; TINY_CANARY
requires false while preserving publisherCapture=true and actual publication.
READER_FIXTURE may explicitly choose true/false for operation4 to exercise either
wire lane; that choice never grants native/source binding acceptance. A fixture
true still obeys every enabled-graph wire check. No native disable switch.

Use existing publisherPayloadBytes=2097152 and publisherChunkBytes=4096 limits for
EACH target payload, graph and Publisher alike; do not add or enlarge a limit.
Existing owner body bound1000000 ASCII bytes remains independently enforced.
All operation/file/line/group/preimage/native/outer/cleanup limits remain exactly
unchanged. In particular:90s/operation,640groups,5s/full-group,32768diagnostic units,
8MiB receipt,65536line,1MiB prefix,250000cells,48holes,4transitions,3orders,
1Mrequest/100Kbranch expansions,480s native,1800s outer,120s cleanup.
284 prior authored groups imply at most356 nominal additional slots, NOT fit.

## 2. New outer records and ordering

GRAPH_CHUNK exact keys, same canonical base64 mechanism as PUB_CHUNK:
record,operation,group,targetId,index,offset,bytes,data.
record=GRAPH_CHUNK, operation=4, targetId=op4/pass1/target0. index starts0 and is
contiguous; offset starts0 and is contiguous decoded-byte offset; bytes1..4096.
data is a primitive string whose strict base64 decoding is exactly bytes and
whose canonical re-encoding equals data. Reject duplicates/gaps/overlaps,
reordering, empty chunks, surplus data and invalid padding/noncanonical spelling.

GRAPH_RESULT exact keys: record,operation,group,targetId,payloadBytes,
payloadSha256,chunkCount. record=GRAPH_RESULT; operation/target as above;
payloadBytes1..2097152, exact lowercase64hex SHA256 of concatenated decoded
chunks; chunkCount positive and equal to actual contiguous chunks. One result.

All graph records belong to CAPTURE groups, operation4, graphCapture=true.
Complete the graph cursor and diagnostic-reference release BEFORE emitting ANY
PUB_CHUNK or GRAPH_CHUNK. Emit the complete existing PUB stream and PUB_RESULT
first; only then emit GRAPH_CHUNK*, GRAPH_RESULT, before ordinary operation RESULT.
No interleaving or second target; no graph records in a disabled lane. All source
checkpoints precede graph cursor groups, which precede output groups. A graph
result without a complete sealed PUB result rejects. Normal final closure and
no-data-after-closure rules remain. A failure/partial raw report cannot pass.

GROUP adds exact keys graphChunkRows,graphResultRows,graphCursorSteps.
All are exact nonnegative integers; graphCursorSteps0..32768. Counts equal emitted
graph records in that exact group. Any positive graph field requires enabled
operation4 CAPTURE. Disabled/non-CAPTURE groups require all three zero.
CAPTURE retains generatorAdvances=0,p07Slices=0,p07Steps=null,
diagnosticCloneSteps=0,accounting=NOT_APPLICABLE. Do not fabricate p07Steps=0.

## 3. Graph target document and joins

Concatenated graph bytes are strict UTF8 JSON, no BOM and no trailing bytes.
Top-level exact keys:
schema,targetId,publisherPayloadSha256,context,objects,branch,source,
goalFacesSha256,phaseWork,body,integrity,limitations.

schema=p07-outf-rejected-publisher-graph-v1; targetId=op4/pass1/target0;
publisherPayloadSha256 equals the prior PUB_RESULT.payloadSha256, whose payload
has independently passed its existing strict validation. No producer equality
flag substitutes for that seal/data validation.

context is an exact copy of PUB.context with identical exact key set:
sourceManifestSha256,operation,rootSeed,placementSeed,routingSeed,attempt,request,
pass,placementSha256. Join HEADER/context/NET_ORDER/source manifest as v5 already
does. Native AUTHORED exact roots remain string9007199254740993,
string-6587958367974952701,string8445301674744508279; attempt4/request0/pass1.
No numeric seed coercion or alternate-root substitution.

objects has precisely the PUB.objects keys/values: generator,route,search,branch,
layout,publisher,found,terminal. All are positive per-operation collision-safe
identity-registry tokens, terminal nullable under the general PUB grammar.
Native OUT_F requires a nonnull terminal for actual CSTORE_F.2. Require exact
joins, not identityHashCode similarity. Tokens are int1..2147483647 and never
rebind. Existing PUB distinct-object/node-token checks remain.

branch has precisely the PUB.branch keys/values: ordinal,netVectorPosition,netId,
startPadId,rootPadId,reachedPadIds,foundTerminalPadId,branchRecordGroup.
Native exact selection:248/28/OUT_F/RSH_F.1/CSTORE_F.2/CSTORE_F.2/group57;
source BRANCH terminalReason=FOUND. One existing source BRANCH and NET_ORDER
entry must join. Preserve the full reachedPadIds set from PUB; it is NOT the
PTH-only array passed to Router predicates.

source exact keys:
target,sourceKind,policy,initialLayerMask,activeTraceWidth,facesToken,
routerTokens,goalArrayToken,reachedTerminalsArrayToken,startPadToken,
rootPadToken,reachedPthPadIds.
- target=OUT_F; sourceKind=OUT_F_REJECTED_PUBLISHER_PATH;
  policy=FULLER_TWO_LAYER. Add that explicit enum/source-kind case to test owner;
  preserve old D_SENSE_PUBLISHER_PATH/LOAD12_ACCEPTED_PRE_RESULT contracts.
- initialLayerMask=3 from actual FULLER BranchSearch seeding (both TOP/BOTTOM),
  including when prefix48holes leaves no remaining new-via capacity. It is not
  inferred from the found path's first face. body.grid.initialLayerMask equals it.
- activeTraceWidth is the actual target net role width, positive Java int and
  equal to v5 independently derived OUT_F role width from the PAD census. Both
  actual Search.faces must already have that active net/width. No prepareNet to
  make an unprepared/stale source fit.
- facesToken,goalArrayToken,reachedTerminalsArrayToken identify the exact retained
  Search.faces, BranchSearch.goal and BranchSearch.reachedTerminals containers.
  routerTokens is exactly [TOP-token,BOTTOM-token], each positive Java int.
  All five container/router tokens are pairwise distinct and distinct from all
  PUB objects/node tokens. startPadToken/rootPadToken identify actual placed
  endpoints. They may alias PUB.terminal only when native reference equality
  establishes that same pad; actual rootPadToken=objects.terminal here. No token
  collision/alias can be inferred from matching pad text alone.
- reachedPthPadIds is sorted unique actual BranchSearch.reachedTerminals IDs,
  excluding root and start, bounded4096. Reader checks it is a sorted subset of
  PUB.branch.reachedPadIds minus root/start, with matching PAD net/layout IDs.
  Existing PAD rows have NO attachment/copper fields: reader cannot independently
  derive PTH filtering from those rows. Native assertions and the retained source
  contract verify exact array population/order, placed object identity and
  attachment=PLATED_THROUGH_HOLE. Do not claim an absent wire oracle.

body is the existing immutable Result body, raw JSON object, schema below.
goalFacesSha256 is the owner's actual goalFaceBitsSha256, and independently
recomputed from body's masks by the exact formula in section5. It is necessary:
the unchanged r5 body does not itself expose that hash.
phaseWork is exactly32 nonnegative signed-long-range integers copied from the
completed owner's phaseWork; sum=body.work.workSteps. Export through a safe test
owner accessor if needed; never privately construct Result or expose its arrays.

integrity exact keys: nativeSourceBound,parentPaused,retainedSourceUnchanged,
cursorSourcesReleased,producerSourcesReleased. Each exact boolean true for a
qualifying graph. Native in-process checks justify these facts; reader data joins
are additional evidence. Fixture validation certifies grammar/oracles only.

limitations exact keys: model,lengthEligibilityOnly,legalSearchHistory,
publicationQualified,layoutQualification,routeAdoption,playable100,
fullPhysicalProof. Values DIRECTED_PER_FACE_ORDINARY_EDGES,true,NOT_RUN,false,
false,false,0,NOT_RUN. No via edges, invented copper or found-path optimality.

The native source must bind actual Publisher stage1 REJECTED with qualityIndex=
reversedSize171 and actual OUT_F:BRANCH_QUALITY_LIMIT, no source handoff/start
reached, zero emitted copper, router rejected/finalizerNOT_REACHED0->0. Before
and after PUB geometry must independently rebuild to identical180traces/48holes,
271606 UTF8 bytes and SHA
cbc98315295693be7cddc31d0327f6f89e5819fedb2b4ad1d65a25e7decc2739.
body.stability.prefixGeometrySha256 equals BOTH rebuilt PUB geometry hashes and
the matching BRANCH pre-prefix hash. A TINY published post-state cannot satisfy
this graph source lane and is deliberately disabled.

## 4. Unchanged r5 Result body: closed schema and checks

body exact keys in owner output order: schema,stability,grid,path,work,masks.
schema=p07-zero-via-graph-r5. This nested r5 body is accepted only inside outer v6;
it does not allow accepting old branch study reports.

stability exact keys: prefixGeometrySha256,liveBeforeVerified,liveAfterVerified,
liveEffectsUnchanged,goalVerified,originalPathVerified. Hash as above; all five
flags primitive true. The owner checks full native geometry and Router graphs
before/after, effect identity/digests, live goal array and source path rechecks.
These flags alone cannot attest electrical/physical route validity.

grid exact keys: width,height,cells,originX,originY,pitch,startCell,
initialLayerMask,endCell,endLayer,goalUnionCellCount. Dimensions/origin/pitch join
PUB.grid. Native exact478x309=147702/origin30,30/pitch10. startCell is PUB first
path node.cell (and actual RSH_F.1); endCell/endLayer are PUB final path node cell/
layer (actual CSTORE_F.2/BOTTOM=1). PUB actual first layer is primaryLayer=1,
but eligible seed faces remain mask3. GoalUnionCellCount is number of cells whose
goal nibble is nonzero, not number of goal-face states. It is in1..cells.

path exact keys: nodeCount,planarEdgeCount,transitionCount,tupleSha256.
Join PUB.path nodes and independently recompute hash/counts. Native171nodes,
170planar edges,0transitions,1700length,4bends,320direct. First parent=-1,
subsequent parent=i-1, all native Node.previous identities and terminal/goal joins
retain v5 verification. Every same-face path step must appear in the matching
directed step mask at its SOURCE cell. Do not substitute reverse-edge membership.
No transitions are graph edges; actual OUT_F path is a zero-transition witness.

work exact keys (owner output order): advanceCalls,workSteps,copyEntries,
comparisonEntries,geometryBytes,effectUnits,ordinaryQueries,goalCells,
reachedPadChecks,encodedCells,pathNodes,pathEdgesVerified,tupleBytes,
serializedUtf16Units,activeMillis. Primitive nonnegative exact integers <=Long.MAX;
advanceCalls/workSteps positive. Specific conservation:
- advanceCalls = number of groups with graphCursorSteps>0.
- workSteps = sum(all GROUP.graphCursorSteps) = sum(phaseWork).
- ordinaryQueries = 2*(2*height*(width-1)+2*width*(height-1)); native1178468.
- goalCells=cells; encodedCells=3*cells; pathNodes=nodeCount;
  pathEdgesVerified=nodeCount-1; geometryBytes=2*PUB.before.geometryBytes;
  tupleBytes equals independent tuple preimage ASCII length.
- serializedUtf16Units equals exact Result body ASCII byte length, <=1000000.
- workSteps >= copyEntries+comparisonEntries+geometryBytes+effectUnits+
  ordinaryQueries+goalCells+reachedPadChecks+encodedCells+pathNodes+
  pathEdgesVerified+tupleBytes+serializedUtf16Units is NOT required: encodedCells
  and serialization overlap. Preserve the EXISTING literal owner's valid lower
  bound, which omits encodedCells: sum of every listed item here except
  advanceCalls,workSteps,encodedCells,activeMillis. Preserve its literal oracle.
- copyEntries/comparisonEntries positive; no invented exact values. phaseWork
  indices come from existing owner32 stages; phases3/16 include effect hashing,
 18/19/20 body preparation/encoding accounting,22 finalization,24 initialization,
 25/26 geometry allocation,28 query guard,29 fixed-prefix preparation. Unused
  indices remain0 for this owner revision. Never reorder phases to fit a ledger.
- activeMillis is the existing elapsed wall time since Cursor construction,
  including inter-call/group intervals; it is not exclusive CPU time, no sum of
  group times or unexported cleanup/serialization split can be inferred from it.

masks exact keys: topStepsHex,bottomStepsHex,goalFacesHex. Each primitive ASCII
string length=cells. Step chars lower0..f; goal chars0..3. Cell order row-major
c=y*width+x; layer TOP=0,BOTTOM=1. Step bits N=1,E=2,S=4,W=8, directed from c to
neighbor. Reject a set boundary-facing bit. Never force reciprocity. Goal bits
TOP=1,BOTTOM=2. Final found state must have its actual goal bit; per-face goal
counts equal BRANCH.snapshot.goalsTop/goalsBottom and union count above.

Exact byte preservation: owner writes compact ASCII keys/structure/masks, with
15 work values formatted in exactly19 columns of leading SPACE padding followed
by ordinary unsigned decimal digits. Those spaces are JSON whitespace, not
leading zero digits. Retain that spelling and all field order/bytes. An incremental
bodyLength/bodyCharAt or bounded stream may view Result; each consumed character
is charged by outer serialization. It must byte-match existing writeBody output
for literal fixtures. No whole-body String/ByteArrayOutputStream/PrintStream
materialization or private mask escape in the production recorder lane. The
existing exact-PrintStream API/negative subclass oracle remains useful.

The graph wrapper can stream body as a raw nested object without reparsing or
reserializing it. Other wrapper members use the existing bounded JSON encoder;
key sets are closed, while wrapper whitespace/key order need not be new semantic
constraints. Reader reconstructs the canonical r5 body independently from the
parsed object and its fixed work widths and requires the exact body value bytes
to equal that spelling (bounded structural extraction of the nested body value).
Its exact raw body length then validates serializedUtf16Units. No additional
body hash is necessary: GRAPH_RESULT seals every wrapper/body byte and the
semantic geometry/path/goal hashes bind to PUB independently.

## 5. Exact preimages and actual source goals

Path hash is NOT the JSON nodes spelling. For each PUB.path.nodes[i] whose tuple
is [cell,layer,x,y,direction,transitions,cost,heuristic,serial,key,parentRow], form
ASCII decimal fields, commas only:
i,parentRow,cell,x,y,layer,direction,transitions,cost,heuristic,serial,key
with parentRow=-1 for i0 and i-1 thereafter. Join rows using ONE LF byte between
rows, no header, CR, trailing LF or spaces. tupleSha256=lower SHA256(those ASCII
bytes); tupleBytes=their exact byte count. This matches importPathOneUnit.

For each row-major c, let m=(actualGoal[c]?1:0)|
(actualGoal[cells+c]?2:0). Hash the one ASCII byte '0'+m for every c, no separator
or trailing newline. goalFacesSha256=lower SHA256(goalFacesHex ASCII bytes),
which equals the owner's goalFaceBitsSha256. No packed binary, two-plane boolean
text or JSON string quotes in this preimage. Prefix geometry preimage remains
the v5 independent reconstruction of PcbBoardLayout.geometryFingerprint UTF8.

Actual seed/goal authority is the retained BranchSearch, not reconstructed
hypothetical copper or terminal-only masks. Native Context binds exact layout,
Search.faces slots, start/root placements, BranchSearch.reachedTerminals and
BranchSearch.goal references. Its GraphInput views the recorder's already-copied
immutable path tuples; OUT_F supplies pathTupleSha256=null,
goalFaceBitsSha256=null,goalCellCount=-1,expectedGoalFaceMaskAt=-1, as no external
PreResult exists. Require these metadata sentinels for OUT_F as for D_SENSE;
LOAD12 still requires its existing immutable external PreResult metadata.

Search goals for FULLER are, independently per face: root cell, every cell whose
actual face Router.ownsLayerGridCell(c%width,c/width,net) is true, and each actual
reached PTH terminal cell on a face with copper. Both seed faces are offered
because policy FULLER, regardless of primary. Root is excluded from the reached
PTH array but included separately. Copper-interior goals and multi-face goals
must survive; do not replace the mask with only root/found-terminal cells or
borrow LOAD12/D_SENSE masks. Router ownsLayerGridCell includes occupiedNet and
canJoinTree; generic trace rasterization alone is not an independent proof of
all actual goal bits. Native literal independent predicate fixtures establish
that boundary; reader checks all exported joins/counts/hashes/witness bits.

## 6. Native lifecycle and budget accounting

Bind while retained source references still exist. Publisher rejection may have
retired the original Search from route.search; retained original Search is
acceptable iff all recorded native references/arrays/active net/width/full
graphs/geometry/effects remain unchanged. Require the same generator Session,
route, Publisher/Search/BranchSearch/found/layout and actual pad/router identities
from FOUND through stage1 rejection and graph completion. A successor Search or
reprepared face is unsupported, never an alternate source. Parent generation
must remain paused throughout immutable path copy, cursor capture and output.

Run cursor only after immutable path copy/audit; before producer live-reference
release and before any output payload. NativeSource retains actual faces/goals/
reached containers through cursor completion, with collision-safe token registry
checks in-process. At most ONE positive Cursor.advance call per CAPTURE group;
argument<=32768 and <=remaining group diagnostic units. Debit exactly lastWork,
positive and <=argument; do not call advance on completed cursor and count it as
work. Completion requires isComplete and actual result(). No fabricated Result.
Constructor/context/begin and any adapter metadata copies are inside a timed
CAPTURE group and debit real outer copy work unless already charged by the
owner's existing initialization work. No hidden allocation/copy/scan between groups.

V6 explicitly partitions work, as confirmed by root during design:
  diagnosticUnits = graphCursorSteps + diagnosticCopiedEntries
                  + diagnosticSerializedBytes + diagnosticHashBytes.
Owner-internal copies, comparisons, hashes, mask encoding and body preparation
are ALL included in graphCursorSteps via unchanged actual workSteps/phaseWork.
Owner subcounters remain exported and validated but are not also added to the
three outer diagnostic counters. Those counters count actual recorder/transport
work OUTSIDE Cursor.advance. No owner work is subtracted from graphCursorSteps;
this disjoint boundary prevents both missing work and accidental double debit.
PLAN R2 froze this explicit preimplementation clarification; it changes no cap
and does not discount internal work or an observed failure.

Outer serialization/hash/copy accounting:
- diagnosticSerializedBytes counts actual PUB + GRAPH payload UTF8 emission,
  plus complete corresponding CHUNK/RESULT JSONL line bytes INCLUDING newline.
  Reading the immutable Result body for payload emission counts here too; do not
  claim zero because cursor already prepared the body's characters.
- diagnosticHashBytes counts actual outer hash-input bytes: PUB before/after
  fingerprint hashing; PUB and GRAPH payload seals; every PUB/GRAPH CHUNK/RESULT
  line entering the real receipt digest. Each repeated traversal/hash is counted.
  The inner owner's independent geometry/effect/goal/tuple hashing is already in
  graphCursorSteps; do not add its nested byte counters a second time.
- diagnosticCopiedEntries counts actual outer scalar/array copies, explicit
  graph metadata/phaseWork copies, base64 input traversal and temporary copies
  not already charged as serialization. If body read and serialization are one
  traversal, charge serialization once; a separate copied buffer is extra copy.
- hashInputBytes includes every outer diagnosticHashBytes input as in v5. Inner
  owner hashing is represented by graphCursorSteps and body subcounters, not
  silently injected into the receipt hash-byte ledger. Retain all preexisting
  non-diagnostic hashInputBytes contributions unchanged.
- serializedBeforeOwnLineBytes includes every actual record line before GROUP.
  GROUP's own line remains outside diagnosticSerializedBytes, exactly as v5;
  its write/flush time and receipt/file budget are included in full group close.

Per-group serialization equality joins decoded PUB and GRAPH bytes emitted in
that group plus full PUB/GRAPH CHUNK/RESULT line byte lengths. Cursor-only groups
have zero outer serialization. For qualification, total outer hash bytes equal
PUB.before.geometryBytes+PUB.after.geometryBytes+PUB.payloadBytes+
GRAPH.payloadBytes+all PUB/GRAPH CHUNK/RESULT line bytes, unless actual additional
outer hashes are deliberately introduced and closed before implementation; this
proposal introduces none. No extra goal hash: use actual Result hash and reader
recomputation. All metadata copy work remains real ledger entries, not a one-unit
placeholder. The existing path/snapshot minimum copy checks remain additive.

After cursor completion it clears Context/pristine/query/live rows as existing
buildResult does; adapter and producer then clear every diagnostic graph-bearing
reference, path reference list, identity registry reference key, retained source
handle and temporary Context, on success AND error/cancel/cap-limit paths. Keep only immutable
Result/scalars/snapshots needed for output. Verify native no-live-reference
assertions BEFORE first payload emission. This applies to diagnostic references;
the paused live generator remains its own real graph owner and resumes only
after diagnostic output closes. Never clear the generator's legitimate graph to
manufacture the proof. Release Result masks/stream state when output finishes.
Actual temporary clone effects cannot alter source effects. Failed cleanup is FAIL.
An incomplete cursor must have an explicit test-owner cancel/close release path
if dropping the producer variable cannot assert its Context/Router/walker fields
are cleared. Native fixtures must inspect incomplete-cursor cleanup after error,
cancellation and a cap-limit abort, and prove no stale work mutates a successor.
Do not turn cleanup of an incomplete capture into a successful Result.

## 7. Independent per-face analysis and result boundary

After full wire/source/transport validation, run two independent algorithms:
queue BFS and separate heap Dijkstra, per seed face in ascending0,1, with NO
cross-face transitions. Outgoing edges come only from that face's source-cell
bits; each edge physical length=pitch. Dijkstra compares physical lengths to
BFS edgeDistance*pitch for EVERY reachable actual goal state on that face;
unreachable goal states must agree too. Independent neighbor/distance logic and
literal directed/boundary tests prevent using one algorithm as its own oracle.
Do not use the found path as expected shortest answer. For actual end face the
existing171node witness proves a directed walk of170edges, hence shortest
length<=1700, but its exact shortest distance is measured, never hardcoded.

For each eligible face report goalFaceCount,reachableGoalCount,
lengthEligibleGoalCount and shortest reachable/shortest length-eligible witness.
For each goal c compute its OWN direct Manhattan distance from the actual start
coordinates, not the root/found endpoint's320. Eligibility is direct>0 AND
shortestLength<=3*direct. A reachable direct0 goal is excluded. Integer distances,
counts and explicit unreachable nulls only; no Infinity/NaN/-1 coercions.

Bounded reader summary can add graphDiagnostics, an exact five-entry list with
null in disabled operations. Enabled entry exact keys: targetId,
publisherPayloadSha256,graphPayloadSha256,prefixGeometrySha256,pathTupleSha256,
goalFacesSha256,faces,scope. scope=ORDINARY_EDGE_LENGTH_ONLY. faces contains the
eligible faces in ascending order, each exact keys: layer,goalFaceCount,
reachableGoalCount,lengthEligibleGoalCount,shortestReachable,
shortestLengthEligible,foundEndpointLength. The last is a nonnegative physical
length or null when the found state is not reachable on that face; it is null
for a face different from the captured found layer, since no transition modeled.
Each witness is null or exact keys cell,x,y,direct,edges,length,pathNodeCount,
pathSha256. Select minimum(length,cell) among the respective goal set; BFS N,E,S,W
parent order determines one stable witness. Verify that witness against captured
directed edges before hashing. Witness hash preimage ASCII lines
index,layer,cell, joined LF with no trailing LF, lower SHA256. pathNodeCount=
edges+1; length=edges*pitch. Full path need not be duplicated into bounded report;
both algorithms independently check the distance, and reader verifies the BFS
witness internally. No witness/goal count is an actual legal routing outcome.

A positive length-eligible result is a history-relaxed candidate only: no claim
about bend<=16, secondary cost, search history, via opportunities, legal Publisher
progress, finalizer, clearance/access/functional behavior or global routing
impossibility/avoidable earlier copper. Negative per-face results do not prove
all-path impossibility. TINY graph output is null, while its PUB success remains
mandatory. All op3/op4 routing choices, vectors, receipts, owner work parity and
uncaptured/observed controls retain v5 acceptance.

## 8. Fresh source closure, required controls, questions

Native manifest retains exactly1080 BASE_SOURCE rows plus WRITER,HOST,READER,WIRE
once each=1084. Materialize a fresh canonical1080 inputs.json and pin its actual
bytes/hash, replacing ONLY tests/contracts/P07ZeroViaGraphDiagnostics.java's row
with final bytes/hash; the other1079 rows/order/paths/hashes equal the accepted
base manifest exactly, including all850 production inputs. Reference old base
SHA92e4a3ba69de6a8666f9789e7facafb5d2a6d8461f42c0f2ccca8e95f7141e7b only
as audit/oracle reference. Do not assert old exact1080-manifest equality for a
modified owner. Keep schema keys unchanged and update strict reader's fresh base
pins/totals from final bytes before runtime. WIRE role path changes to this new
milestone's frozen WIRE.md. No extra TEST row in native1084; fixture manifests
retain existing explicitly fixture-only TEST allowance. Preserve old independent
literal graph and historical graph-reader/oracle source expectations unchanged.

Before native acceptance require strict positives/negatives for old schema,
primitive types, unknown/duplicate keys, base64/order/seals, PUB joins/native
tokens, wrong prefix/face/seed/goal direction/boundary/path hashes, source mutation/
active-net/role-width drift, work/phase/ledger conservation, source manifest
changed-row census, stream byte parity and error/cancel/reference release.
Real selected host/CLI positive/negative controls precede expensive native runs.
The actual JDK8 source7/target7 compile and new actual graph-owner literal fixtures
are mandatory through maintained scripts/verify-current-contracts.ps1 with exactly
-Suite P07ZeroViaGraphContractTest, once on final sources and separately from the
five-arm host. No new host mode/embedded fixture process. Literals do not prove
generator binding. Then fresh tiny five-arm
and exact authored five-arm graph canary, both independent algorithms, source/
process/resource review under original caps. No unchanged full command after two
identical environmental failures; missing authority or over-cap capture is FAIL/
BLOCKED as appropriate, never PASS. No GWT/Player proof is claimed by this design.
All new object key sets are exact; reject unknown/duplicate fields, floats/NaN,
boolean-as-integer coercion, noncanonical integer/seed forms, malformed UTF8 or
unpaired surrogates. Arrays/strings/nulls must have their declared primitive types.
Inherited v5 ID/plus/UTF16/coordinate and Java signed-int/long guards remain.

Closed questions: none for proposed field sets/hash formulas/accounting. Root froze PLAN R2 and this final WIRE before code/runtime. Implementation feasibility, source retention after route
retirement, runtime budget/640group fit, actual shortest distances/goal counts
and qualification all remain UNMEASURED, not design assumptions. Current source
needs explicit OUT_F enum/sentinel validation and incremental Result body/phase
access; it has neither today. It already releases cursor sources on successful
completion; cancellation/error release needs explicit adapter/owner handling and
negative proof. These are required test-owner changes, not production seams.
## Root closure before implementation

The existing PAD.role is the net routing role, so role width is independently
derivable. PAD has no attachment/copper fields: do not invent a reader oracle
for generic PTH filtering. Native exact placement/array/attachment assertions
remain required. In this exact AUTHORED source, PUB.branch.reachedPadIds is
exactly ["CSTORE_F.2"], so source.reachedPthPadIds is exactly empty after root
exclusion. Fixtures may exercise a nonempty ID/net-consistent subset, with no
native acceptance granted by fixture data.

All new source tokens are primitive int1..2147483647, never bool. The five
container/Router tokens are pairwise distinct and distinct from all PUB/node/
start-pad/root-pad tokens. Start/root pad tokens are distinct from each other
and from PUB/node tokens, except the terminal alias established by the same
placed pad identity. Reader requires a terminal/root-pad token alias iff the
joined foundTerminalPadId equals rootPadId, and analogous start identity if
representable under the inherited PUB grammar; no numeric-only alias grants
native authority. Actual AUTHORED rootPadToken=PUB.objects.terminal and start
is distinct. Native asserts the actual references, never merely textual IDs.

body.work.activeMillis is bounded by floor(the same operation's actual
elapsedToCloseNanos/1000000), hence by90000; this is only a whole-operation
upper bound, not a fabricated exact cursor/cleanup phase split. All owner work
counters and32phase entries remain nonnegative exact signed-long values and
must reconcile as specified above. No new SOURCE or graph hashing traversal
is introduced outside the declared cursor/transport domains.

The maintained P07ZeroViaGraphContractTest focus supplies native new-target,
stream-parity and incomplete-cursor release controls. It is separate from the
unchanged five-arm host interface. TINY native has no graph; enabled fixture
grammar grants only fixture validation. Only the exact fresh AUTHORED retained
source can supply the real generator/graph join and budget-fit proof.
