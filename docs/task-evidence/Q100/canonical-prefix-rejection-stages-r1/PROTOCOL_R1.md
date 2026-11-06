# Frozen predicate-stage transport/source protocol R1

FROZEN before P6 overlay/helper implementation or execution. Supplements
PLAN_R1 SHA31547c7531d5c950d305f2010bf3cabfbe56fd709498953298d8d31e8bcab68f.
UTF8 LF, no CR/BOM/duplicates/nonfinite/boolean-as-integer values; CREATE_NEW,
exact sizes/hashes/EOF. Every payload/index/line<=65536 bytes; all generated
artifacts together<=8388608 bytes. Original chunk/output/process/work caps remain.

## Native overlay API

The only routing source override is exact accepted PcbNetRouter.java SHA
759831c91345aedab9e78d8c83785019f7e59287a18f4f99f7af14876776c474.
Do not edit repository production sources. A surgical builder asserts original
fragments and emits the full native-only source plus before/after/diff hashes.
It preserves the permitsLayerStep bounds/endpoint loop and all other source bytes.
At permitsLayerStepToEndpoint only, expand the original three-operand && into
one ordered if-return chain with each original predicate expression used once.
Each failure/final success returns through a passive reporting helper. Do not
add an alternate replay path or evaluate a predicate twice.

Inside Router, package-private native overlay API:

    interface FixedGraphStageObserver {
        void onEndpoint(String netId,String startPadId,String endpointPadId,
            int fromGridX,int fromGridY,int toGridX,int toGridY,int direction,int stage);
    }
    static final int STAGE_LEGAL_MOVE_REJECTED=0;
    static final int STAGE_TRAVERSE_REJECTED=1;
    static final int STAGE_OCCUPANCY_REJECTED=2;
    static final int STAGE_ALLOWED=3;
    void attachFixedGraphStageObserverForStudy(FixedGraphStageObserver observer,
        Router expectedQuery,Router live,Router pristine,boolean comparisonPassed);
    void detachFixedGraphStageObserverForStudy();
    boolean hasFixedGraphStageObserverForStudy();

The private callback field defaults null, is never copied, and stores no physical
state. Attach requires nonnull observer/query/live/pristine, all three roles
distinct, this==expectedQuery, this!=live/pristine, comparisonPassed true and no
existing observer. Invalid calls throw IllegalArgumentException/IllegalStateException
before mutation. Helper-owned identity arrays and actual completed comparison
are role authority; source equality alone is not. Detach clears the field; getter
allows explicit null verification on live/pristine/query owners. The callback
receives only immutable strings/primitives, never mutable owner objects.

Reporting checks the callback field before pad-ID extraction and calls it once
with the actual endpoint's terminal stage. Return original false for rejected
stages or true for ALLOWED. Null callback allocates no observation rows. Preserve
original exceptions; callback throws propagate. No observer on live/pristine faces.

## Sources and compilation

Keep all1084 baseline source rows byte-identical, changedRows empty,850 production
sources unchanged. Eight native mode roles, in compiler order:
router (qualified BASE PcbLayerRoutingPrototype), support (BASE price support),
writer (unchanged native ActualGeneratorBranchOrderStudy), adapter (new
CanonicalPrefixStageStudy.java), blocker (immutable P5
CanonicalPrefixBlockerInspector.java), queries (immutable P5
CanonicalPrefixCutQueries.java), netRouterOverlay (new source override),
stageInspector (new CanonicalPrefixStageInspector.java).
Exclude the original repository PcbNetRouter.java from compilation and substitute
exactly its overlay. Other P5 exclusions/three pinned bridge copies remain.
Final compiler census1080: P5's1079, minus original Router, plus overlay/helper.
No new library, JVM/OS setting, production owner hook or budget change.

## Stage helper and composition

CanonicalPrefixStageInspector implements TerminalPrefixInspector. Constructor:
(String runId,String inputSha,String wireSha,boolean observe,boolean callbackThrow).
Package-private API: setPriorObservationUnits(long), int runCanaries(),
String completedJson(String actualGraphSha), String expectedExceptionJson(),
boolean expectedExceptionObserved(), boolean referencesDropped(). Prior-unit
offset may be set once before selection, in0..32768; it includes all P5 units.

Normal NULL/OBSERVE use a composition inspector: unchanged P5 blocker first,
then stage inspector at the actual owner callback. Before selected stage inspection,
sum P5 copyUnits+comparisonUnits+queryUnits+geometryRowUnits through bounded native
metadata reads and set the offset. After-owner callback reaches both inspectors
even if one fails; preserve/propagate all failures. CALLBACK_THROW uses the stage
inspector alone, prior units0. It throws exactly
IllegalStateException("CANONICAL_PREFIX_STAGE_CALLBACK_THROW") from its first
actual endpoint callback after recording that event. No sentinel substitutes for
an original predicate failure or masks cleanup errors.

Stage helper borrows actual Search/Branch/layout/endpoints only during selection;
global307/local77 and full frozen identity/prefix binding remain. Own the live
reference wrapper, never clear Search.faces. Make two pristine/two query copies
in4096-entry units and compare before use. On all exits compare live/pristine,
verify effect/geometry hashes and observer nulls, detach query observers in finally,
then drop every source/copy reference. Keep query cache warming separate.

Normal helper queries the same22 in full-cut order, attaching only for the frozen
14 selected ordinals in OBSERVE. NULL never attaches; all its event arrays are
empty. All22 results/counter/map deltas must equal the same-run P5 observations.
Selected events bind actual end then eligible reached-PTH endpoint order, with
early success. CALLBACK_THROW attempts the first query0 only, records one event,
reports no returned predicate result, detaches, proves live stability and propagates.

## Stage payload

prefix-stages.json is a single JSON object plus LF, schema
tsj-canonical-prefix-stage-observations-v1, with exactly these keys:
schema,status,mode,runId,sourceManifestSha256,wireSha256,scope,
layoutQualification=false,zeroDeltaInputSha256,fullCutInputSha256,graphSha256,
prefixGeometrySha256,receiptBranchOrdinal=307,lineageBranchOrdinal=77,
netId=A_COIL_LOW,startPadId=QA.C,rootPadId=DA.A,reachedPthPadIds,
lifecycle,sourceProof,queries,totals,exceptionStage.
scope=CANONICAL_BASE_REQ0_ZERO_DELTA_STAGES_ONLY.
status=COMPLETE_NULL_CONTROL_ONLY, COMPLETE_STAGE_OBSERVATIONS_ONLY or
EXPECTED_CALLBACK_THROW_ONLY, consistent with mode NULL/OBSERVE/CALLBACK_THROW.
Graph SHA is actual normal capture; exception binds the frozen prior graph only.

lifecycle={offers,selected,completed,ownersReleased,contextRestored,
sourceRefsDropped,observersDetached}. Normal308/1/1, exception308/1/0;
all four final booleans true. Proof is not a full heap-leak claim.
sourceProof={geometryBeforeSha256,geometryAfterSha256,faces:[FACE0,FACE1]}.
Each FACE has layer,sourceEffectsBeforeSha256,sourceEffectsAfterSha256,
liveToPristineBeforeEqual,queryToPristineBeforeEqual,liveToPristineAfterEqual,
liveObserverNullBefore,pristineObserverNullBefore,liveObserverNullAfter,
pristineObserverNullAfter,queryObserverNullAfter,copyEntries,comparisonEntries,
copyUnits,comparisonUnits,queryCount. All booleans true; query warming is allowed.
Always complete source proof on both faces, including deliberate callback throw.

Each QUERY has ordinal,layer,fromState,toState,bit,selected,fromGridX,fromGridY,
toGridX,toGridY,outcome,permitsStep,routerCongestionBefore,routerCongestionAfter,
routerCongestionDelta,workCongestionBefore,workCongestionAfter,workCongestionDelta,
conflictsBefore,conflictsAfter,conflictsDelta,events.
Normal22 queries outcome=RETURNED,permitsStep=false; exception one query0,
outcome=EXPECTED_CALLBACK_THROW,permitsStep=null. Selected means membership in
the frozen14, independent of NULL's disabled callback. Map arrays are sorted
known board net IDs/counts under the P5 arithmetic contract.

Each EVENT has ordinal (per-query zero-based),netId,startPadId,endpointPadId,
fromGridX,fromGridY,toGridX,toGridY,direction,stage. Endpoint ID may be null only
where the actual endpoint is null; this case's root is DA.A. Direction is actual
Router0=N/1=E/2=S/3=W. Stage is one of the four full uppercase names above,
without the STAGE_ prefix. Observe actual calls; never manufacture skipped or
unreached endpoint rows. Normal real queries all fail and therefore have no ALLOWED
terminal event; literal positives separately prove success/early-exit behavior.

totals={queryCount,selectedQueryCount,callbackQueryCount,eventCount,attachments,
detachments,copyEntries,comparisonEntries,copyUnits,comparisonUnits,queryUnits,
priorObservationUnits,totalObservationUnits,elapsedNanos,maximumUnitNanos}.
Normal queryCount22/selected14/queryUnits22. NULL callbacks/events/attach/detach0;
OBSERVE attach/detach14, callbackQueryCount14, eventCount from actual endpoints.
Exception queryCount/selected/callbackQueryCount/queryUnits/attachments/detachments1,
eventCount1, prior0. totalObservationUnits=prior+copy+comparison+query units<=32768;
each clone<=1048576 entries, elapsed and maximum unit charged inside ADVANCE.
exceptionStage=null normal, FIRST_ENDPOINT_CALLBACK exception.

## Native envelope and selected host

CLI MODE UUID INPUT_SHA WIRE_SHA EMPTY_ABSOLUTE_DATA_DIR, MODE NULL/OBSERVE/
CALLBACK_THROW; --canary and --capture-overflow retain actual bounded producer
controls. Main class com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy.
Success marker CANONICAL_PREFIX_STAGE_COMPLETE plus one JSON line, schema
tsj-canonical-prefix-stage-native-v1: mode,runId,sourceManifestSha256,wireSha256,
scope,layoutQualification=false,index,wrapper,aggregateBytes. wrapper retains
P5 preparationNanos,graphExportNanos,postNanos,elapsedNanos,workNanos,groups:3.
All serialization/verification/hash/IO charged, normal subtraction only actual
ArmResult cleanup. Index path canonical-stage-index.json, schema
tsj-canonical-prefix-stage-index-v1, exact P5 keys plus stages: mode,profile=BASE,
kind=AUTHORED,runId,sourceManifestSha256,wireSha256,scope,layoutQualification=false,
target={receiptBranchOrdinal:307,lineageBranchOrdinal:77},operation,transport,
attribution,stages,selection. Both normal modes have the same five artifacts:
op-1.jsonl,fixed-prefix.jsonl,prefix-blockers.json,prefix-stages.json,index.
Operation/transport/selection use unchanged P5 shapes; graph/facts transport stays
the P4 canonical protocol/scope. P5 blocker payload remains unchanged inner schema.
Both normal modes capture the graph and P5 payload; NULL refers to stage callback.

Expected nonzero child has empty stdout and stderr marker
CANONICAL_PREFIX_STAGE_EXPECTED_CALLBACK_THROW plus one JSON line (bounded original
stack trace may follow), schema tsj-canonical-prefix-stage-exception-v1:
mode=CALLBACK_THROW,runId,sourceManifestSha256,wireSha256,scope,
layoutQualification=false,exceptionType=java.lang.IllegalStateException,
exceptionMessage=CANONICAL_PREFIX_STAGE_CALLBACK_THROW,partialReceipt,
stages=(embedded expected helper payload),inclusiveWorkNanos<=90e9,
cleanupAccounting=FULL_NATIVE_WALL_PLUS_POST_SOURCE. No index/sidecars, only actual
partial op1 through BRANCH307, no complete-operation acceptance.

Host/source manifest/process envelopes retain P5 shapes with stage schema names,
new modes/source roles/census and unchanged tool hashes. Preserve canonical Windows
parameter spelling and within-API stat/handle metadata comparisons. Source baseline
is P6 baseline above, zero changedRows. Bind immutable P5 inputs/qualification/
reader/helpers/query fixture, canonical control/capture artifacts and P4 oracles.
Do not call old whole-run validation with a forged parent context. Reuse unchanged
inner operation/graph/attribution/partial validators with explicit new outer closure.
Normal cleanup accounting NATIVE_RESIDUAL_PLUS_POST_SOURCE; exception full native
wall plus post-source/scratch, with overflow negative added in final upper report.
Final source manifests and actual selected canaries precede case execution.
