# Frozen canonical prefix blocker protocol R1

FROZEN before producer implementation/native execution. Supplements PLAN_R1.
UTF8 LF, no CR/BOM/duplicates/nonfinite/boolean-as-integer values. Bounded
CREATE_NEW files and exact lengths/SHA256/EOF. Attribution JSON<=65536 bytes;
op1+graph transport+index+attribution together<=8388608 bytes. No raw process
paths or mutable Java owner objects belong in emitted data.

## Native modes and envelope

CLI remains MODE UUID INPUT_SHA WIRE_SHA EMPTY_ABSOLUTE_DATA_DIR, with MODE
CONTROL, CAPTURE or INSPECTOR_THROW. The last mode deliberately throws
IllegalStateException("CANONICAL_PREFIX_INSPECTOR_THROW") after its first actual
pristine copy is completed/checked, before any predicate query. Sole args
--canary and --capture-overflow remain; overflow cap is shared stdout/stderr8MiB.
Actual owner is operation1 AUTHORED BASE in every real case.

Success stdout is exactly CANONICAL_PREFIX_BLOCKER_COMPLETE plus one JSON line:
schema=tsj-canonical-prefix-blocker-native-v1,mode,runId,sourceManifestSha256,
wireSha256,scope=CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY,layoutQualification=false,
index={path:canonical-blocker-index.json,bytes,sha256},
wrapper={preparationNanos,graphExportNanos,postNanos,elapsedNanos,workNanos,groups:3},
aggregateBytes. Actual P4 ArmResult cleanup is the only normal subtraction.
GraphExport includes all attribution verification/serialization/hash/IO; native
probe work is already included in the measured ADVANCE group. Check post-output
limits and child exit, not just the trailer's earlier timing sample.

Index keys: schema=tsj-canonical-prefix-blocker-index-v1,mode,profile=BASE,
kind=AUTHORED,runId,sourceManifestSha256,wireSha256,
scope=CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY,layoutQualification=false,
target={receiptBranchOrdinal:307,lineageBranchOrdinal:77},
operation=(unchanged P4 actual operation1 entry),
transport=(null CONTROL, unchanged P4 graph/facts transport descriptor CAPTURE),
attribution=(null CONTROL, {path:prefix-blockers.json,bytes,sha256} CAPTURE),
selection=(unchanged P4 graph observer selection/dropped fields).
Graph transport schema tsj-canonical-prefix-transport-v1 and its FACTS schema,
caseId and canonical graph encoding remain unchanged. Legacy op1 GRAPH records
remain absent. Capture graph SHA must equal the accepted P4 graph.

Canary final stdout marker CANONICAL_PREFIX_BLOCKER_CANARY_COMPLETE has JSON:
schema=tsj-canonical-prefix-blocker-canary-v1,status=PASS,assertions positiveint,
inspectorAssertions positiveint,elapsedNanos<=90e9,groups<=640,
maximumGroupNanos<=5e9,layoutQualification=false. Maintained fixture/P4/helper
PASS lines may precede it; all actual work and output remain charged.

## Inspector helper API and payload

Task-owned CanonicalPrefixBlockerInspector implements the native owner's
TerminalPrefixInspector. Constructor(String runId,String inputSha,String wireSha,
boolean throwAfterPristineCopy). Expose package-private int runCanaries(),
String completedJson(String actualGraphSha), String expectedExceptionJson(),
boolean expectedExceptionObserved(), and boolean referencesDropped(). Root
adapter handles file/aggregate/process envelope; helper owns observations only.
Query fixture is CanonicalPrefixCutQueries, generated from frozen cut-input-r1.
It exposes COUNT,WIDTH,HEIGHT,CELLS,PITCH,ORIGIN_X,ORIGIN_Y,PRIMARY,
CUT_INPUT_SHA,GRAPH_SHA,PREFIX_SHA and fromState(i),toState(i),bit(i).

Both helper JSON methods produce exactly these keys:
schema=tsj-canonical-prefix-blocker-observations-v1,
status=(COMPLETE_OBSERVATIONS_ONLY or EXPECTED_INSPECTOR_THROW_ONLY),
runId,sourceManifestSha256,wireSha256,scope=CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY,
layoutQualification=false,cutInputSha256,graphSha256,prefixGeometrySha256,
receiptBranchOrdinal=307,lineageBranchOrdinal=77,netId=A_COIL_LOW,
startPadId=QA.C,rootPadId=DA.A,
lifecycle={offers,selected,completed,ownersReleased,contextRestored,sourceRefsDropped},
sourceProof={geometryBeforeSha256,geometryAfterSha256,faces:[FACE...]},
observations:[QUERY...],prefixTraceNetCounts:[{netId,traceCount}...],
totals={queryCount,foreignCongestionQueries,unattributedQueries,copyEntries,
comparisonEntries,copyUnits,comparisonUnits,queryUnits,elapsedNanos,
maximumUnitNanos},exceptionStage=(null normal or AFTER_PRISTINE_COPY).
graphSha256 remains the frozen input graph hash for exception; no actual graph
capture is claimed there. completedJson requires actualGraphSha equal that hash.
Lifecycle offers308/selected1/completed1 normal, selected1/completed0 exception;
owners/context/ref release must be true. Do not retain source objects in fields.

FACE keys: layer,sourceEffectsBeforeSha256,sourceEffectsAfterSha256,
liveToPristineBeforeEqual,queryToPristineBeforeEqual,liveToPristineAfterEqual,
copyEntries,comparisonEntries,copyUnits,comparisonUnits,queryCount.
Normal faces0,1 each use separate pristine/query copies. Warm query preparation
may differ from pristine; compare live to untouched pristine after probes.
The exception's first face may have queryToPristineBeforeEqual=false and no
query copy; its completed pristine copy and live stability must still be proved.
Copy/comparison chunks<=4096, each clone<=1048576 entries; all observation
units<=32768 and measured full ADVANCE group<=5s. Drop every copy/source reference.

QUERY keys: ordinal,layer,fromState,toState,bit,fromX,fromY,toX,toY,
permitsStep=false,routerCongestionBefore,routerCongestionAfter,
routerCongestionDelta,workCongestionBefore,workCongestionAfter,
workCongestionDelta,conflictsBefore,conflictsAfter,conflictsDelta,
targetOccupiedNet,targetClearanceNet,neighborOccupiedNets,classification.
Conflict maps are sorted [{netId,count}...] arrays with unique known board net IDs.
Neighbor rows are sorted [{gridX,gridY,netId}...] occupied cells in the actual
ROUTING_GRID_CLEARANCE_CELLS radius; own-net rows are retained, null cells omitted.
Counters/maps are monotonic per clone. Independent delta arithmetic must match;
foreign conflict delta equals foreign neighbor histogram times congestion delta.
Router/work congestion deltas agree. Positive delta requires a foreign target
occupied/clearance owner. Zero delta attributes no foreign-occupancy rejection,
even if a later occupancy predicate could have rejected the step.
classification is FOREIGN_OCCUPANCY_REJECTION_RECORDED for delta>0, otherwise
BLOCKED_WITHOUT_FOREIGN_OCCUPANCY_REJECTION. Prefix trace counts are NET-level
associations, never unique trace-owner or necessary-reroute claims.

## Expected exception and host/reader boundary

INSPECTOR_THROW exits nonzero, stdout empty, no index/graph/attribution sidecars.
Its bounded stderr starts CANONICAL_PREFIX_BLOCKER_EXPECTED_EXCEPTION plus JSON:
schema=tsj-canonical-prefix-blocker-exception-v1,mode=INSPECTOR_THROW,runId,
sourceManifestSha256,wireSha256,scope,layoutQualification=false,
exceptionType=java.lang.IllegalStateException,
exceptionMessage=CANONICAL_PREFIX_INSPECTOR_THROW,
partialReceipt={path:op-1.jsonl,bytes,sha256},inspector=(helper exception payload),
inclusiveWorkNanos<=90e9,cleanupAccounting=FULL_NATIVE_WALL_PLUS_POST_SOURCE.
Bounded original stack trace may follow. Partial op1 has no complete-operation
acceptance; compare its ordered routing prefix through target BRANCH to CONTROL.
Cleanup callback true and all temporary refs dropped are mandatory. Cleanup
upper uses entire native wall plus post-source/scratch cleanup conservatively.

Selected PS5/JDK8/Python/module hashes stay P4-qualified. Compiler initially1079:
P4's explicit1077 with current writer/adapter plus inspector and cut-query fixture.
No production overlay/new physical predicate. Host/input/process field bindings
retain P4 shapes under tsj-canonical-prefix-blocker-* schema names, with the
additional two pinned source roles and explicit exception accounting. Every
consumed path is pinned before/after; modeFiles keys are router,support,writer,
adapter,inspector,queries. Source baseline is P4's accepted1084 current rows at
parent83027040, with only the planned native writer delta allowed. Reader may
reuse unchanged P4 operation/transport/oracle functions; it must validate the new
envelope/source/exception/attribution fields itself, not claim old full validation.
