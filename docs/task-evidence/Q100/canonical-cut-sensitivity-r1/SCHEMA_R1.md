# P7 exact schema and pure-analysis API R1

Frozen after PROTOCOL_R1 and before code or execution. Final input paths/hashes are
bound only after source completion, before selected canaries/cases. No result has
been produced. All JSON is strict UTF-8/ASCII without BOM, duplicates or nonfinite
values; booleans never satisfy integer fields. Schema keys below are exact.

## Input spec

schema=tsj-canonical-cut-sensitivity-input-v1. Keys:
schema,parent,repositoryRoot,sourceManifest,sourceManifestSha256,parentReader,
parentReaderSha256,parentPins,parentPinsSha256,parentRun,parentQualification,
parentQualificationSha256,transport,transportSha256,blockers,blockersSha256,
stages,stagesSha256,cutInput,cutInputSha256,plan,planSha256,protocol,protocolSha256,
schemaDocument,schemaDocumentSha256,analysisModule,analysisModuleSha256,worker,
workerSha256,tests,testsSha256,graphOracle,graphOracleSha256,viaOracle,
viaOracleSha256,captureReader,captureReaderSha256,selectedPython,
selectedPythonSha256,selectedPowerShell,selectedPowerShellSha256,files,limits.

parent is23f94f8f4ea8025fe24475fabcb194840e472fd8. All path fields are absolute
canonical stable regular files except repositoryRoot/parentRun (directories).
files is the exact nonduplicate path/bytes/sha256 pin census for consumed inputs;
sourceManifest records the unchanged1084-row D/C source inventory and exact roles.
Use explicit current and parent source roots, not a forged parent source spec.
Pin the P6 input/source inventory and all its consumed dependencies transitively.
The parent qualification/pins/reader hashes are those in PROTOCOL_R1. Transport,
blockers and stages are genuine final P6 observe-r6 artifacts; cut input is the
accepted P5 cut. Plan is published P6 NEXT_SENSITIVITY_PLAN.md. Paths and raw
process data stay private; published results contain only relative references/
hashes and sanitized identity fields.

limits equals workNanos90000000000, groupNanos5000000000, groups640,
batchEntries4096, cloneEntries1048576, observationUnits32768,
artifactBytes8388608, resultBytes65536, lineBytes65536, readChunkBytes32768,
specBytes4194304, parentMs180000, workerMs180000, cleanupMs120000,
outerMs1800000. Existing native constraints remain unchanged and unrun.

## Pure analysis module

No filesystem/process/import-time work and no PCB physics. It accepts the pinned
codec/oracle module objects from the root adapter after source binding. Public API:

    class SensitivityError(ValueError): code: str
    class WorkBudget:
        # timed complete units; methods/record below
        def __init__(self): ...
        def call(self, phase: str, action, entries: int=0): ...
        def finish(self) -> dict: ...
    def analyze_graph_cases(base, openings, owner_order, codec, via_oracle,
                            budget: WorkBudget) -> list[dict]: ...

openings is an ordered tuple of immutable seven-field tuples:
(ordinal,layer,fromState,toState,bit,ownerNetId,selectedForOpening).
selectedForOpening is true for the eight validated foreign-clearance cut rows;
the fourteen geometric rows are present with false. owner_order is the two
validated owner strings, in B_SENSE/B_REF case order for the real adapter.
Pure literal tests may use other nonempty owner strings. Reject unknown owners
on selected rows, duplicate ordinal or edge identity, invalid types, face/state/
bit/adjacency/bounds mismatch, enabled original cut bits and empty owner sets.
False selected rows may carry null owner and must never be opened.

Use base directly for CONTROL (no pointless full copy). Other cases build one
steps list in batches<=4096, OR only selected bits, validate a full payload using
the unchanged codec, prove every field/bit delta, analyze and release. Keep base
steps/vias/goals/starts immutable and verify base digest/state after all cases.
Each logical clone includes at most1M entries. The opaque codec/oracle calls
remain whole measured groups; no fake group subdivision. Shared immutable
unchanged tuples may be retained; no lazy substitute type is passed to the oracle.

Each case result has exact keys:
case,owners,openedOrdinals,openedEdges,counterfactual,actualCaptureAttested,
canonicalGraphSha256,changedBitCount,preserved,bfs,minimumVias,agreement,work.
case names/order are those in PROTOCOL_R1 (tiny tests use the same role names).
owners and openedOrdinals are sorted arrays in their declared case/cut order.
openedEdges rows: ordinal,layer,fromState,toState,bit,ownerNetId. counterfactual
is false for CONTROL and true otherwise; actualCaptureAttested=false for all
analysis result rows (parent proof separately validates the original capture).
preserved has dimensions,protocol,model,caseId,primary,starts,goals,vias,base
booleans, all true. changedBitCount is exactly0/4/4/8 in real cases.

bfs={reachable,reachedStates,edgesExamined,goalHits,reachedMaskSha256,fullCensus};
fullCensus=true. Exhaust directed planar closure; hashes cover all state bytes.
minimumVias is the unchanged oracle's complete result. agreement is true iff
bfs.reachable equals(minimumViaCount==0); fail otherwise. work contains
buildEntries,buildUnits,bfsPops,bfsUnits,codecCalls,oracleCalls,elapsedNanos.
Report actual oracle counters as emitted; never label offline work as P07 work.

WorkBudget.call times the entire action, charges one actual group and its declared
entry count, and rejects entries>4096 for copy/BFS phases. Opaque codec/oracle/
decode/hash/serialization phases use entries0 and are still timed complete groups.
finish checks total wall work<=90s, groups<=640 and maximum group<=5s. The record
keys are groups,elapsedNanos,maximumGroupNanos,copyEntries,copyUnits,bfsPops,
bfsUnits,opaqueCalls,totalObservationUnits,actualNewP07Steps,actualNewP07Advances,
actualNewP07Expansions. The last three are0; totalObservationUnits is actual
group count under32768, not invented owner steps. Error paths retain elapsed/
phase data in the outer worker failure receipt; incomplete work is never accepted.

## Final result

schema/status/mode/specSha256/scope are fixed as in PROTOCOL_R1. ANALYZE status is
PASS_CONDITIONAL_CUT_SENSITIVITY_ONLY; CANARY is PASS_LITERAL_HOST_CANARY_ONLY.
Top keys are schema,status,mode,specSha256,scope,parent,source,graph,cut,cases,
work,acceptance,limitations. CANARY has parent/graph/cut null, cases[], and an
explicit work.canaryAssertions plus casesRun=0; it does not load the actual graph.

ANALYZE parent={qualificationSha256,inputPinsSha256,readerSha256,runId,
receiptSha256,status}. source={analysisModuleSha256,workerSha256,testsSha256,
graphOracleSha256,viaOracleSha256,captureReaderSha256,stableBefore,stableAfter}.
graph={rawSha256,canonicalSha256,prefixGeometrySha256,width,height,cells,states,
starts,goalCount,initialFacesMask}. cut={queryCount,openableCount,geometricCount,
ownerCounts,legalMoveRejected,traverseRejected,ordinalsSha256}. Actual constants
are22/8/14, owner counts4/4,9/5 stages,478x309/147702cells/295404states/mask3.
work={analysis,prerequisiteWallMs,workerElapsedNanos,sourceVerificationNanos,
serializationNanos,casesRun}; analysis is WorkBudget.finish output.

acceptance={route:false,layout:false,electrical:false,q100:false,
productionFeasibility:"UNKNOWN",newGeneration:false,newP07Search:false}.
limitations is the exact ordered string array:
CONDITIONAL_MASK_OPENINGS_ONLY,NOT_WHOLE_NET_REMOVAL,
NOT_UNIQUE_TRACE_OWNERSHIP,NOT_PHYSICAL_ROUTE,NO_PRODUCTION_ADOPTION,
NO_NEW_ELECTRICAL_OR_POPULATION_QUALIFICATION.

Worker stdout index: CANONICAL_CUT_SENSITIVITY_COMPLETE plus one JSON object+LF,
schema=tsj-canonical-cut-sensitivity-worker-v1, with keys schema,status,mode,
specSha256,result,aggregateBytes,elapsedNanos. result={path,bytes,sha256} uses only
sensitivity-result.json. Include its LF in sizes/hashes; no files beyond the exact
host/worker source/process/result receipt set. CREATE_NEW/EOF/no-reparse checks
are mandatory. Final host status remains HOST_COMPLETE_READER_PENDING until an
independent selected consumer checks the whole four-case result and preserved
input/source/process/budget/cleanup boundaries. No success solely from an index.
