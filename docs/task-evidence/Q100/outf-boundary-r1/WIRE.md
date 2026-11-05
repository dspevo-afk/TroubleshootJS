# Frozen Publisher diagnostic report v5

FROZEN before producer/reader implementation or native execution. The
[plan](PLAN.md) owns exact scope, source baseline, accounting and limits. The
accepted [v4 wire](../net-priority-r1/WIRE.md), SHA
7aa6316d6254021b494d2a46100a5c747d0e4de7dd0bd34141aaf5f40d7f683d,
defines every unchanged field set/encoding/guard. Explicit changes below only.
HEADER.schema=p07-actual-branch-order-study-v5; v1..v4 reject. Generic index/source/
spec envelopes retain v1 but bind the new wire/tools. Base1080/850+224+6 and raw
source SHA92e4a3ba69de6a8666f9789e7facafb5d2a6d8461f42c0f2ccca8e95f7141e7b
remain; writer/host/reader/wire extend to1084, javac1075 after exact same bridges.

## Arms and outer records

HEADER.arm retains id,api,strategy,netOrderStrategy,observed and adds exact boolean
publisherCapture. Operations0..3 retain their v4 meaning and false. Operation4 is
MINIMUM_PAIR_PUBLISHER_CAPTURE/BOUNDARY_EXPLICIT/CANONICAL_NEAREST/
SECOND_ORDER_MINIMUM_PAIR/observed=true/publisherCapture=true. There are exactly
five complete per-operation receipts; every per-arm/aggregate cap stays unchanged.
NET_ORDER's observed operation domain extends1..4. Operations3/4 must have identical
complete vectors/context/promotion and passive source routing behavior. Existing
canonical controls0/1/2 remain. Only op4 may emit the following new records.

PUB_CHUNK exact keys: record,operation,group,targetId,index,offset,bytes,data.
record=PUB_CHUNK; targetId=op4/pass1/target0; index starts0/contiguous; offset is
contiguous decoded-byte offset; bytes=1..4096; data is canonical base64 decoding
to exactly bytes. No duplicate, gap, overlap, reordering or surplus data. It is
emitted in CAPTURE groups after its source observation, before PUB_RESULT/RESULT.

PUB_RESULT exact keys: record,operation,group,targetId,payloadBytes,payloadSha256,
chunkCount. record=PUB_RESULT; one result after all chunks; bytes1..2MiB, lower
SHA256 and exact count. It seals exactly one strict UTF8 JSON target document
below. Unknown/duplicate fields, JSON floats/NaN/coerced booleans and invalid
canonical integer/seed/ID forms reject. Existing v4 identifier/plus/UTF16 rules
remain. No data may follow the normal final report closure.

GROUP retains v4 fields and adds publisherChunkRows,publisherResultRows,
diagnosticCopiedEntries,diagnosticSerializedBytes,diagnosticHashBytes and
diagnosticUnits (nonnegative exact integers). Counts and emitted bytes must join
the actual records and native ledger. CAPTURE has p07Slices0/ownerSteps0; its
diagnosticUnits<=32768. Record real copy/UTF8/hash work, with no routing discount;
all groups/time count against original caps. Necessary immutable snapshots in an
ADVANCE observer remain timed in that whole group with their explicit copy count.
Group and arm closures conserve all new rows/bytes/units. Any unexported timing
split stays NOT_EXPORTED, never inferred. Full-group-close timing includes output.

## Target document (closed key sets)

Top-level exact keys: schema,context,objects,branch,grid,checkpoints,path,publisher,
ordering,before,after,integrity,limitations. schema=p07-publisher-target-v1.

context: sourceManifestSha256,operation,rootSeed,placementSeed,routingSeed,attempt,
request,pass,placementSha256. It joins HEADER/source/context exactly; operation4,
request0/pass1; authored attempt4 and exact signed seeds from PLAN. Tiny uses the
existing source-defined saved42/v12 context, not invented numeric substitutes.

objects: generator,route,search,branch,layout,publisher,found,terminal. Tokens are
positive per-operation integers or terminal=null. Native reference == and a
collision-safe identity registry establish them; identityHashCode is not authority.
No token may rebound to a successor object. Release graph-bearing references once
snapshots are complete and on every failure/retirement/cancellation path.

branch: ordinal,netVectorPosition,netId,startPadId,rootPadId,reachedPadIds,
foundTerminalPadId,branchRecordGroup. Exactly one source BRANCH joins pass/ordinal/
IDs/group; preserve sorted unique reached IDs and explicit nullable terminal.
Authored selection is OUT_F/RSH_F.1/CSTORE_F.2/FOUND, not a presumed terminal pad.

grid: width,height,cells,originX,originY,pitch,primaryLayer,maximumVias. Recompute
cells; pitch10, layers0/1; inherit current v4 raster/domain bounds. These values
come from the same actual Search; do not borrow the D_SENSE graph or a clone.

checkpoints: found,publisherSeen,publisherTerminal,orderingReady,orderingResult.
Each is null or an exact object group,sequence. Sequences identify actual probe
polls, are positive, non-decreasing and source-group bound. Facts established by
one poll share its sequence (not invented earlier observations); distinct polls
advance strictly. A first observation of an already-rejected Publisher may thus
co-observe publisherSeen/Terminal/orderingResult. found/publisherSeen/publisherTerminal/
orderingResult are required for a qualified capture. orderingReady is required
iff routerStatus=READY and is before generator finalization. Missing/ambiguous/
unsupported events are explicit failures, never filled from a later reason.

path: encoding,nodes,nodeTokens,foundGoal. encoding is the retained tuple spelling
cell,layer,x,y,direction,transitions,cost,heuristic,serial,key,parentRow. Nodes are
start-to-found,1..4096; each exact11 integers, first parent=-1, subsequent parent
i-1 and actual Node.previous equality. nodeTokens has the same length, unique
identity tokens; its last equals objects.found. foundGoal is the actual true goal
bit from that Branch at found cell/layer. Recompute grid coordinates, adjacency,
direction, layer/via changes, transition counts and supported endpoint/terminal
mapping. Preserve raw cost/heuristic/key; no shortest-path/optimality claim.
Fields/links used by deferred copying must be proven immutable; otherwise fail.

publisher: outcome,stage,qualityIndex,reversedSize,storedLength,storedBends,
storedLastDirection,segments,reason,sourceHandoff,startReachedAfter. outcome is
PUBLISHED or REJECTED for qualification; NOT_OBSERVED/AMBIGUOUS/UNSUPPORTED/
CANCELLED/ERROR can preserve a failed report but cannot pass this lane. PUBLISHED
needs stage6, actual same-Publisher/Search handoff, actual reached start and
path-derived delta. REJECTED needs actual stage1 completed quality scan and actual
source BRANCH_QUALITY_LIMIT reason before successor overwrite, with unchanged
geometry. Reversed size equals raw node count. Independently compute length,
bends, last direction, transitions, direct and all OR predicates; direct is
derived from coordinates, not an invented stored Publisher field. Never choose
a unique subcause if multiple predicates are true.

ordering: routerStatus,routerReason,readyLayoutToken,readyPass,finalizerStatus,
finalizedBefore,finalizedAfter,lastFailureDetail. READY binds the actual ready
layout/pass and its checkpoint; ROUTER_REJECTED binds the actual source boundary
and has no ready layout. finalizerStatus=NOT_REACHED/FINALIZED/REJECTED/
NOT_OBSERVED. NOT_REACHED follows an actual pre-ready router rejection; FINALIZED
or REJECTED requires the parent advance associated with that ready object and
actual counter/detail evidence. Equal repeated failure text is not fresh-event
authority. Close with the original whole-request receipt; it cannot contradict
per-ordering counts or falsely accept an unfinalized request.

before and after each have exact keys staticPrefix,traces,holes,geometrySha256,
geometryBytes. staticPrefix is the complete geometryFingerprint byte/string
prefix before its trace/hole suffix, including all fixed placement/label/region
metadata; it must remain identical. Trace rows are exact arrays:
[sourceId,netId,startPadId,endPadId,layer,exposure,width,xPoints,yPoints]. Nullable
fields retain null; layer0/1, exposureEXPOSED/COVERED, equal coordinate arrays,
valid integer widths. Hole rows are exact arrays:
[id,kind,netId,x,y,drillRadius,landRadius,exposure], with native VIA/PLATED/NON_PLATED
semantics and null net only where valid. Inherit native coordinate/ID bounds.

Independently rebuild PcbBoardLayout.geometryFingerprint's trace/hole suffix:
Java UTF16 field lengths/null=-1:, Java lexical/null-string ordering, layer names
TOP/BOTTOM, trace width marker only when width!=9, original point separators and
hole fingerprint. Concatenate staticPrefix; verify exact UTF8 length/SHA, including
the source BRANCH pre-prefix hash. No producer-only equality flag is sufficient.
Every old trace/hole row is conserved. Quality rejection makes before=after.
Publication adds only independently reconstructed compacted same-layer path
pieces, source-consistent terminal endpoints/IDs, EXPOSED role widths13/11/9 and
new compatible vias (drill2/land6/both layers). Singleton pieces add no trace.
Handle existing compatible holes and repeated transition coordinates without
inventing a new via or allowing foreign-net/NPTH electrical connectivity. The
whole-layout48-hole bound remains. No full blocked-mask/clearance/access proof
is implied by this local Publisher reconstruction.

integrity exact keys: ownerBindingVerified,immutablePathVerified,
staticGeometryEqual,prefixRowsConserved,sourceReferencesReleased. True values
require pinned native in-process assertions plus independent data checks where
derivable; flags do not replace them. limitations exact keys: routeAdoption,
layoutQualification,playable100,fullPhysicalProof. Values false,false,0,NOT_RUN.
Publisher path geometry may be added privately; it is not whole-board admission.

## Qualification boundary

The fixture-only lane remains explicitly fixture-only. Native report acceptance
requires fresh actual five-arm tiny/authored receipts, process/source/context/
cleanup proof and root review. The tiny canary requires an actual successful
Publisher/handoff. The authored outcome is measured, not predetermined. Treat
missing target/boundary or unsupported capture as incomplete diagnostic proof.
No fake positive, source-budget discount, new seed search, HARD/PSYCHOTIC/full30,
epoch/default change or playable100 admission is authorized by this wire.

## Accounting clarification R2

Frozen before any v5 parser, test or native execution. Producer source review
identified an ambiguity between payload serialization and outer JSONL accounting.
The initial wire is retained externally as WIRE_FREEZE_R1.md, SHA
37e89146a21a9211e70201a3e614e36f3823433c4572d9e6865fdccbcf1332b0.
This clarification adds conservation detail without increasing any cap.

diagnosticSerializedBytes counts target-payload UTF8 serialization bytes plus
emitted PUB_CHUNK/PUB_RESULT UTF8 JSONL line bytes including newlines. Existing
serializedBeforeOwnLineBytes still counts every emitted line; neither counter
subtracts from the other. diagnosticHashBytes counts actual new capture hash
input bytes, including geometry preimages and payload; hashInputBytes still
includes those bytes too. diagnosticCopiedEntries counts explicit scalar/array
copies and base64 input traversal unless already charged as serialization.
diagnosticUnits equals diagnosticCopiedEntries + diagnosticSerializedBytes +
diagnosticHashBytes exactly. CAPTURE's sum is at most32768 per group. Deferred
serialization/hashing/copying must be incremental when it exceeds that bound;
declaring one giant operation as a small unit is invalid. Necessary synchronous
snapshots retain their32768-entry cap and whole-ADVANCE timing. Timing includes
every actual operation and output, regardless of these separate diagnostic
counters. No original P07 owner-step or diagnostic-clone unit is discounted.

For qualified captures, publisher.segments is an exact integer0..4095 copied
from the actual Publisher, and sourceHandoff/startReachedAfter are booleans.
Stage, qualityIndex, reversedSize, storedLength and storedBends are exact
nonnegative integers bounded by the4096-node path and source algorithm;
storedLastDirection is0..4. PUBLISHED has reason=null; REJECTED has the actual
netId+":BRANCH_QUALITY_LIMIT" reason. ordering.routerReason is the actual
Session.reason string; it need not be SUCCESS for READY because that source
field can retain its earlier value. finalizedBefore/After are exact integers
0..3 at the causal ordering boundary and its parent advance. FINALIZED increments
by1; finalizer REJECTED and NOT_REACHED keep the count unchanged. lastFailureDetail
is nullable actual text; a rejected ready candidate requires nonempty detail and
the actual ready-parent-advance join, even if the text repeats a prior failure.
READY has a positive readyLayoutToken and readyPass equal to the captured pass;
ROUTER_REJECTED has both null. Missing, ambiguous or failed captures do not qualify
and may leave a rejected/partial raw report. Never fill absent authority with
invented default counters, tokens or nulls to obtain reader acceptance.

## Header clarification R3

Frozen before the first v5 reader, Java or host runtime. Earlier PS5 AST parsing
was source-only and supplied no report or native acceptance. HEADER.limits adds
exact keys pathNodes=4096, snapshotEntries=32768,
publisherPayloadBytes=2097152 and publisherChunkBytes=4096. These are the four
capture-only caps already frozen by PLAN; every v4 limit key/value stays intact.
No cap increases. The initial R2 wire is retained externally as WIRE_FREEZE_R2.md,
SHA0a83f1e376615b1eec071adefdfba65569dee15ae7a56b7790a5ad627983295d.

## Snapshot amendment R4

The [plan's measured R4 amendment](PLAN.md#snapshot-amendment-r4) supersedes only
earlier snapshotEntries=32768 and synchronous32768-entry snapshot statements:
HEADER.limits.snapshotEntries=1048576. Count raw copied scalar/array entries and
the actual after native geometryFingerprint's UTF16 code units inside the whole
ADVANCE group. Its actual preimage must be sampled from that layout, followed by
bounded CAPTURE suffix/static-prefix validation and UTF8 hashing. Reusing the
before static prefix as an asserted actual after observation is invalid.

All original P07 owner steps, clocks, groups, cleanup and file/preimage bounds
remain unchanged. CAPTURE diagnosticUnits remains at most32768 per group.
ADVANCE remains copy-only for new diagnostic serialization/hash counters. No
production hook, default change or acceptance expansion. This amendment was
frozen before the first v5 reader/Java/host runtime; the prior7764df1d wire and
initial03a0c635 plan are preserved externally. No native failure is reclassified.
