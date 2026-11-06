# Canonical prefix transport protocol R1

FROZEN before adapter implementation/runtime. UTF8 JSON with exact fields;
duplicate/type/bounds/complete-EOF validation. Original op-1.jsonl is unchanged.

Native CLI: MODE UUID INPUT_SHA WIRE_SHA EMPTY_ABSOLUTE_DATA_DIR.
MODE=CONTROL|CAPTURE|MISSING_TARGET. AUTHORED and immutable BASE only.
Other sole args: --canary, --capture-overflow. Producer shared stdout/stderr cap
8MiB installed before fixture work; overflow attempts one8MiB+1 write, stdout
empty, stderr CANONICAL_PREFIX_CAPTURE_LIMIT, nonzero exit.
Canary runs actual P07FixedPrefixObservationContractTest plus selector/transport/
constructor/helper guard controls under bounded5s groups/90s total; no graph
fixture/link creation beyond its maintained literal native contracts.

Native uses actual Mode.AUTHORED/Study/ArmRun(study,1[,observer]).execute.
Return no fake ArmResult facts. Validate actual candidateHeader root/placement/
routing/census/variant against plan. CAPTURE observer selects exact lineage77
inpass2; receipt ordinal307 is separate. MISSING_TARGET deliberately requests
lineage307 and must exit nonzero NO_TARGET_CAPTURE after actual cleanup, with no
sidecar/index. No other seed/strategy. CONTROL has no observer.

CAPTURE emits fixed-prefix.jsonl with records:
HEADER: record,schema='tsj-canonical-prefix-transport-v1',runId,
sourceManifestSha256,wireSha256,scope='CANONICAL_BASE_REQUEST0_ONLY',
payloads=[{kind:'GRAPH',bytes,sha256,chunks},{kind:'FACTS',bytes,sha256,chunks}],
aggregateCapBytes=8388608,chunkBytes=32768.
CHUNK: record='CHUNK',kind,index,offset,bytes,data (strict standard Base64).
GRAPH chunks precede FACTS; index0+,offset exact rawbyte sum,rawchunk<=32768.
FOOTER: record='FOOTER',complete=true,graphChunks,factChunks,payloadBytes.
Every line<=65536B, UTF8 LF, no CR. Exactly one header/footer and no trailers.
GRAPH is exact canonical payload from the existing serializer through its new
native-only bridge, caseId='CANONICAL_SEPARATE_REQ0_PASS2_A_COIL_LOW'.

FACTS JSON keys: schema='tsj-canonical-prefix-facts-v1',runId,
sourceManifestSha256,wireSha256,caseId,rootSeed,placementSeed,routingSeed
(seeds strings),variant='SEPARATE',layoutVersion,dependencyEpoch,
componentCount,padCount,netCount,routeRequestIndex,selectedPlacementIndex,
selectedPlacementAttempt,eligiblePlacementAttempts,receiptBranchOrdinal=307,
lineageBranchOrdinal,pass,netId,startPadId,rootPadId,startX,startY,rootX,rootY,
branchExpanded,totalRouteExpansions,terminalReason,prefixHoleCount,prefixViaCount,
prefixHoleCountsByKind,reachedPadIds,reachedPthPadIds,
rawPlacementFingerprint,rawPlacementFingerprintUtf8Bytes,rawPlacementSha256,
prefixGeometryFingerprint,prefixGeometryFingerprintUtf8Bytes,prefixGeometrySha256,
gridWidth,gridHeight,gridPitch,originX,originY,primaryLayer,startCell,
initialFacesMask,cellCount,captureWorkUnits,copyEntries,goalProbes,
ordinaryEdgeProbes,viaProbes,comparisonEntries,captureActiveElapsedMillis,
viaMaskSemantics='RELAXED_GEOMETRY_OMITS_REMAINING_HOLE_CAP',
maximumHoles=48,remainingHoles=0,maximumBranchTransitions=4,
remainingBranchTransitionsFromPrefix=0.
Values from actual snapshot/context/lineage/header except explicit plan constants.
Do not invent a frontier field absent from lineage; outer reader joins receipt
snapshot307 and requires frontier0. Epoch values use actual production constants.

Index canonical-prefix-index.json<=65536B CREATE_NEW:
schema='tsj-canonical-prefix-index-v1',mode,profile='BASE',kind='AUTHORED',runId,
sourceManifestSha256,wireSha256,scope='CANONICAL_BASE_REQUEST0_ONLY',
layoutQualification=false,target={receiptBranchOrdinal:307,lineageBranchOrdinal:77},
operation={operation:1,armId:'OBSERVED_DEFAULT_NEAREST',receipt:{path:'op-1.jsonl',
bytes,sha256},elapsedToCloseNanos,maxFullGroupNanos,
cleanup:{status:'PASS',elapsedNanos,ownersReleased:true,contextRestored:true}},
transport=null for CONTROL, otherwise {path:'fixed-prefix.jsonl',bytes,sha256,
graph:{bytes,sha256},facts:{bytes,sha256}},
selection={calls,selected,callbacks,snapshotDropped:true}.
CONTROL selection counts0; CAPTURE selected/callbacks1. Drop snapshot/lineage/
encoded buffers on success/error; no snapshot dispose API exists.

Success stdout one line CANONICAL_PREFIX_CAPTURE_COMPLETE followed by JSON:
schema='tsj-canonical-prefix-native-v1',mode,runId,sourceManifestSha256,wireSha256,
scope='CANONICAL_BASE_REQUEST0_ONLY',layoutQualification=false,
index={path:'canonical-prefix-index.json',bytes,sha256},
wrapper={preparationNanos,graphExportNanos,postNanos,elapsedNanos,workNanos,groups:3},
aggregateBytes. elapsed/work measured after indexwrite/hash before trailer;
work=elapsed-actualArmResultcleanup. CONTROL graphExportNanos=0.
Check all group<=5s/work<=90s after output too, no success on nonzero child exit.
Aggregate actual op1+transport+index<=8388608. Reserve index<=65536 before
sidecar write, verify finalactual aggregate. Whole owner groupcount+3<=640.

Wrapper graph export includes serialization, metadata/hash/chunk/IO work and is
charged as an explicit group; if it cannot fit5s, split bounded legitimate units
under the same640/90s caps before accepting. No free serialization or cleanup
subtraction except actual ownercleanup. All post-output/error cleanup proofs remain.
