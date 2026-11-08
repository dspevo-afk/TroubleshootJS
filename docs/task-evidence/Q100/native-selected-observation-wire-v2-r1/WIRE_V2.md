# Generic selected-observation wire V2

FROZEN R1. This is a generic-only observation/materialization/stdout contract,
not a full authored D result, R7 comparison or playable routing qualification.
Native main accepts exactly four arguments: SELECTED_OBSERVATION_CANARIES_V2,
canonical lowercase UUID, source-manifest SHA256, this wire SHA256. All former
authored/canary modes reject before any input, descriptor or Session access.
The authored assemble entry and former whole-canary assembler deny explicitly.

## Envelope

One compact ASCII JSON object plus one LF, at most1048576bytes. Ordered keys:
schema,status,runId,sourceManifestSha256,wireContractSha256,complete,blocked,
nonBranchQuality,genericCallbackThrow,directOwnerAbortReuse,sourceReview.

schema=tsj-selected-observation-wire-canary-v2;
status=GENERIC_OBSERVATIONS_RECORDED_ONLY. IDs/digests bind exact expected CLI
context. No floats, nonfinite values, duplicate/extra/missing keys, second line,
noncanonical escapes/numbers, or control/non-ASCII text. Strings<=256characters;
depth<=64, visits<=1048576. Existing canonical writer and R9 count/admit/write
publication owner remain authoritative; no new formatter/transport implementation.

complete has exactly NULL and FOUND cases. blocked has exactly
BRANCH_SEARCH_LIMIT,NO_PATH,TOTAL_SEARCH_LIMIT. nonBranchQuality has exactly
QUALITY_LIMIT. Each case key equals its caseId. These are callback-context
fixtures, never whole fresh-run/electrical outcomes. genericCallbackThrow and
directOwnerAbortReuse retain the existing real generic helper maps and checks.

sourceReview exact keys: scope,physicalOwnerSha256,studyParentSha256,
headSemantics,injectedSessionBegins,otherSessionCounts.
scope=GENERIC_OBSERVATION_V2_ONLY;
physicalOwnerSha256=7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c;
studyParentSha256=d6485b0b0f9c518431d440fcb57e477143d50cae78302c29598d12aaa4601dbd;
headSemantics=SELECTED_AND_POPPED_BEFORE_REFRESH_NOT_PATH_WITNESS;
injectedSessionBegins=3; otherSessionCounts=SEPARATE_EXISTING_GENERIC_HELPERS.

## Case and context

Case ordered keys: caseId,provenance,armRole,observation,context,meterDelta,metadata.
provenance is INJECTED_ACTUAL_P06_SESSION for the three blocked cases and
LITERAL_CALLBACK_FIXTURE for NULL,FOUND,QUALITY_LIMIT. armRole=NULL only for NULL,
otherwise OBSERVE. The injected Session pass is2 before first advance; explicit
cap/layer/minimum/queue frames preserve the Q fixture contract. Count actual
driver begins; do not infer a lifetime count from separate generic helper calls.

context ordered keys: requestedNetIds,traversedNetIds,padsById,gridWidth,
gridHeight,outcome,terminalFailure,callbackOpportunities.
Exactly one net and two distinct pad identities form each bounded context.
padsById maps each pad ID to {netId,x,y}. Net/pad IDs are nonempty printable ASCII
<=256; coordinates are strict ints matching selected snapshot endpoints.
grid dimensions are strict positive ints with product<=250000. Lists preserve
the declared net ordering; this context is not a full routed-board traversal.

outcome exact keys: disposition,reason,expanded. For NULL: NOT_RUN,
OBSERVER_DISABLED,0 and no terminal. FOUND: FOUND,FOUND,positive total and no
terminal. Blocked: NO_PATH or CAP_REJECTED as appropriate, net:terminalReason,
actual snapshot total. QUALITY_LIMIT: QUALITY_REJECTED,QUALITY_LIMIT,0 and no
terminal. terminalFailure is null except blocked cases, where its exact keys are
failedNetId,ownerReason,searchPass, binding the snapshot/net/reason/pass2.
callbackOpportunities is1 for each one-callback fixture,0 for NULL/QUALITY_LIMIT;
it denotes fixture callback opportunities, not a claimed full owner advance run.

## Observation V2

Exactly18 ordered keys: schema,mode,state,callbackCount,foundCallbackCount,
nonFoundCallbackCount,firstNetOrdinal,lastNetOrdinal,lastCallbackTotalExpanded,
selectedCallbackOrdinal,transientTupleCopies,transientFieldCopies,validationCount,
observerUnits,copiedEntries,copyChunks,selectedSnapshot,selectedHead.
schema=tsj-cohort-terminal-observation-v2. Former V1/17-key observations reject.

NULL: NOT_ENABLED, all counters zero, ordinals/totals/selections null. OBSERVE:
NO_FAILURE for FOUND; FIRST_NON_FOUND for blocked; NON_BRANCH_REJECTION for
QUALITY_LIMIT. Found/nonfound sum to callbacks; at most one nonfound, final if
present. The selected snapshot is present iff FIRST_NON_FOUND and has the
unchanged33-field exact domain, identity/grid/flags/counter consistency from D.
No-failure/quality have neither selection. Other nonfound reasons have snapshot
only. selectedHead is present iff the selected reason is BRANCH_SEARCH_LIMIT.

selectedHead has exactly ten ordered strict int fields: branchOrdinal,cell,layer,
direction,transitions,cost,storedHeuristic,serial,key,storedPriority. Require
branchOrdinal>=1;0<=cell<cells;layer0..1;direction0..4;0<=transitions<=48-prefixVias;
cost,h,serial>=0; key=(((transitions*2+layer)*5+direction)*cells)+cell;
storedPriority is signed Java-int cost+h, including wrap. Local cap100000 and
total<1000000; bestStateCount>=1. This proves scalar coherence, not legal routing,
policy-specific via capacity, a frontier minimum or a path witness.

Let D=callbackCount,H=1 iff selectedHead exists else0,V=validationCount.
transientTupleCopies now explicitly counts BOTH terminal and head DTOs:
transientTupleCopies=copyChunks=D+H;
transientFieldCopies=copiedEntries=33D+10H;
observerUnits=69D+V+21H. No duplicate mutable H counter: use existing headCopies.
Sealed successful maps also require headReleases=H and all Q atomic invariants.
V range remains derived from the old92D bound plus2D+12H: D<=V<=94D+12H.
Thus observerUnits<=163D+33H, below unchanged callback ceiling512 per callback.
These new-version semantic bounds reflect the qualified fixed extra work; no
producer group/time/copy budget increases. Exact fixture successful values:
FOUND D1,V91,U160; branch D1,H1,V102,U192; NO_PATH/TOTAL D1,V90,U159;
NULL/QUALITY D0,V0,U0. Copies/chunks follow the equations above.

meterDelta exact keys units,copiedEntries,copyChunks, equal the actual observer
callback deltas (NULL/QUALITY zero). They exclude setup and materialization.
metadata exact keys copiedEntries,copyChunks, for the common materializer only:
NULL18/1; OBSERVE36+33S+10H entries and2+S+H chunks, S=snapshot present.
Branch=79entries/4chunks; ordinary failure=69/3; FOUND/QUALITY=36/2.
The head's ten callback entries and ten map entries are different actual copies.

## Meter/materializer/publication

One existing producer-start shared Meter throughout. Common materializeObservation
precharges out18, selected snapshot33 when present, head map10 when present,
then outer mutable map18; normal executeArm and generic driver use that same
owner. Precharge four fixed metadata inspections/operations (selected ref, head
ref, result-map operation, outer-map construction); these are outside observer
units. NULL uses existing nullObservation with18 slots. Callers seal before map
access; stale cleanup remains through the separate-meter selected4arg API.

Use the existing R9CanaryPublication/header/text/accountResultTree/count/stdout
owners with only the new schema/status literals. Fee remains3*ceil(bytes/4096),
one export group; exact actual byte admission/copy/write and checkpoint/body/
Meter binding remain. Do not emit an uncharged new metrics sidechannel. Global
limits are enforced by the unchanged native owner; the independent host binds
final source/classes/tools/process receipts before accepting captured bytes.
Reader CLI is read-only and outputs a small acceptance summary only after pure
semantics pass. Root materializes captured bytes only after source/process and
semantic acceptance, using existing checked exclusive I/O. No authored data,
compiled-input tree, D descriptor or old D reader CLI is loaded by the new mode.

R7 aggregation's observer-vs-metadata split remains unchanged and follows these
new counters, but no full original/two-arm R7 integration is qualified here.
No fabricated original capture is used to manufacture that proof. A later scope
must migrate/qualify the full result/marker/reader/source-bound host together.

All budgets remain90s/640groups/5s/32768units/copy1048576/chunk4096;
compile/suite60s, nativewrapper480s/reader180s/cleanup120s, local100000/global1m.
Full30 EXCLUDED; HARD/PSYCHOTIC unavailable/unrun; protected bodies unread here,
historical unread UNCERTIFIED. Playable100zero/betaheld. No production/GWT/Player,
electrical/population or physical/access/runtime renewal.
