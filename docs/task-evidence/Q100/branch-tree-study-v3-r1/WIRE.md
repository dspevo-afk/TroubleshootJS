# Actual branch-order report v3

Status: FROZEN report protocol v3 before implementation and fresh runtime.
The v2 actual tiny execution completed all four arms but failed raw-work control
parity: diagnostics intentionally perform extra pristine router clones. V3 adds
explicit source-observed clone events,retains every raw-work cap,and requires
independent event/terminal reconciliation before normalized routing-work parity.
No routing/default adoption or native acceptance is granted. V1/V2 remain
preserved and incompatible data headers are rejected.

## Encoding and ownership

Each arm has its own UTF-8 JSONL data file, `op-0.jsonl` through `op-3.jsonl`.
No BOM,CR,blank lines,duplicate keys,unpaired surrogates,float/exponent/nonfinite
numbers or negative zero. Final LF required. Integers exclude booleans. Limit
8MiB per data file,32MiB total,64KiB per line,1024pad rows,1MiB per geometry
fingerprint preimage. Transport caps are admission limits,not changed physics.
All source/data/spec files are regular,stable,content-pinned and reparse-free;
duplicate case-insensitive source paths are rejected. Output uses exclusive creation.

The pre-run source manifest contains only source identity. It never hashes itself
or a receipt. The input spec later pins that manifest,StudyIndex and,for native
evidence,the separate post-exit transport receipt. Source manifest roles are
BASE_SOURCE,WRITER,HOST,READER,WIRE,TEST. Exactly one READER and WIRE are required;
READER is the actual running file. Native kinds additionally require all unchanged
1079base rows, exact raw manifest68b1489b.../228662bytes/87478294summed bytes and
at least one WRITER/HOST. Base sources are checked against their actual bytes.
Every consumed source/data file and the input spec is reread after comparison.

Evidence kinds: READER_FIXTURE,TINY_NATIVE,AUTHORED. Fixture kind has no base/native
transport,returns only PASS_READER_FIXTURE_ONLY,and cannot qualify a native run.
Native reports return VALIDATED_NATIVE_REPORT_ONLY_ROOT_ACCEPTANCE_REQUIRED;
mechanical report validation does not replace root's real host/context review.

## Four arms and data records

Arm IDs/APIs/strategies/observed flags are,exactly:

| op | id | api | strategy | observed |
|---|---|---|---|---|
|0|PLAIN_ENTRY_RECEIPT_CONTROL|PLAIN_BEGIN|CANONICAL_NEAREST|false|
|1|OBSERVED_DEFAULT_NEAREST|BOUNDARY_CONVENIENCE|CANONICAL_NEAREST|true|
|2|OBSERVED_EXPLICIT_NEAREST|BOUNDARY_EXPLICIT|CANONICAL_NEAREST|true|
|3|SECOND_ORDER_FARTHEST|BOUNDARY_EXPLICIT|SECOND_ORDER_FARTHEST|true|

Data starts HEADER,ends FOOTER. Between them are PAD,DIAGNOSTIC_CLONE,BRANCH,GROUP and one RESULT.
PAD rows precede all BRANCH rows and RESULT; no BRANCH follows RESULT. Each GROUP
closes the preceding records with that ordinal; ordinals are contiguous from0.
RESULT is followed by the final REPORT GROUP then FOOTER. Header belongs togroup0;
footer to the final group. PAD IDs are strictly sorted,unique,and census complete.

- HEADER fields: record,group,schema,runId,mode,operation,arm,sourceManifestSha256,
  wireSha256,candidate,limits. schema=p07-actual-branch-order-study-v3; runId is
  lowercase UUID text; mode=AUTHORED or TINY_CANARY. candidate fields are
  rootSeed,placementSeed,routingSeed,variant,layoutVersion,dependencyEpoch,
  componentCount,padCount,netCount,traceWidthProfile. Seeds are exact canonical
  signed-long strings. AUTHORED uses separate9007199254740993/
  -6587958367974952701/8445301674744508279,100components/246pads. All kinds retain
  layout26,dependency-v28,ROLE_WIDTHS. limits exactly matches reader.LIMITS:
  90e9operation nanos,640groups,5e9group nanos,32768steps; transport caps above;
  cost60,holes48,transitions4,orders3,request1M,branch100K,cells250K.
- PAD fields: record,group,padId,netId,componentId,x,y,role. IDs/role use ASCII
  `[-+A-Za-z0-9_.:]`,1..128chars; coordinates are integers within +/-1M. One role
  per net. Census hash preimage is sorted
  `padId\tnetId\tcomponentId\tx\ty\trole\n` in UTF-8,with final LF.
- BRANCH fields: record,operation,ordinal,group,prefixSha256,prefixBytes,snapshot.
  Only observed arms; contiguous callback ordinal from0. Snapshot has every field
  in reader.SNAP_INTS/SNAP_BOOLS/SNAP_STRINGS,matching pinned BranchSnapshot.
  Prefix is the actual live Search.layout geometry at terminal callback before
  Publisher. ROOT/source native code must prove that access; no later substitute.
  Terminal reasons:FOUND,NO_PATH,TOTAL_SEARCH_LIMIT,BRANCH_SEARCH_LIMIT. Face
  expansion sum equals branchExpanded; totalExpanded equals cumulative branch
  expansions. Maximum callbacks per arm=3*(padCount-netCount).
  TOTAL_SEARCH_LIMIT is the final callback and requires the matching incomplete
  receipt; later branches/passes or a complete receipt are contradictory.
- GROUP fields are reader.GROUP_FIELDS: record,operation,ordinal,phase,
  elapsedBeforeOwnLineNanos,generatorAdvances,p07Slices,p07Steps,diagnosticCloneSteps,accounting,
  observerChecks,callbacks,censusRows,hashInputBytes,serializedBeforeOwnLineBytes.
  Phase=SETUP,ADVANCE,PHYSICAL,REPORT. ADVANCE has exactly one generator advance;
  other phases have none. A measured P07 slice has p07Slices=1,observerChecks>=1,
  accounting=MEASURED,p07Steps0..32768. Otherwise p07Slices=0,
  accounting=NOT_APPLICABLE,p07Steps=null. Lost/partial accounting fails.
  Callback/PAD counts and pre-own-line bytes exactly match raw group records.
  Any PAD/BRANCH observation belongs to a real ADVANCE group with measured P07
  slice. Exactly one final REPORT GROUP closes RESULT; no further body record.
  Hash input bytes cover all observed prefix preimages,with other actual hash
  work counted separately by producer. Their sum also covers pristine/census and
  winner preimages. No invented opaque traversal counts.
- RESULT fields: record,operation,group,proof,route,context,receipt,outcome,winner,
  physical,logicalWork,boundary. proof=COMPLETE. Context fields:requestIndex,
  selectedAttempt,eligibleAttempts,pristineSha256,pristineBytes,padCensusSha256,
  boardSha256. Request0,unique eligible attempts0..5,selected first;AUTHORED attempt4.
  Receipt has all ten OrderingSearchReceipt fields; validation preserves its
  constructor predicates and source caps. Outcome is bounded nonempty text.
  Boundary fields:requestCount=1,outcomeCount=1,routeRetired=true,
  candidateRetired=true,generatorComplete=false,eventObserved=(op!=0),
  successorAdvanceCalls=0. Plain does not claim event or seal.
  Winner is null for TYPED_REQUEST_REJECTION with zero finalized/-1selected;
  otherwise REQUEST_FINALIZED with geometrySha256,geometryBytes,attempt,ordering,
  quality. It is the actual surviving request winner,not generator completion.
  Physical fields:connectivity,clearanceKeepout,layeredRaster,access,functional.
  First three are PASS only for a real request winner after independent checks;
  all unreached work is NOT_RUN; access/functional remain NOT_RUN in this study.
  logicalWork fields:generatorAdvances,p07Slices,p07OwnerSteps,diagnosticCloneSteps,
  routingOwnerSteps,expansions. Sums
  match ledger and typed receipt; observed branch expansions sum to receipt.
- FOOTER fields:record,group,operation,complete=true,padCount,branchCount,groupCount.
  Counts match complete raw data. Every started ordering needs terminal-branch
  evidence in v3; grid-only no-callback passes fail missing proof rather than
  being inferred. GROUP count includes setup/census/physical/report work in640.

## Index, timing and transport

StudyIndex fields:schema,runId,kind,sourceManifestSha256,wireSha256,operations.
schema=tsj-branch-order-index-v1. Four ordered entries contain operation,armId,
receipt(path,bytes,sha256),elapsedToCloseNanos,maxFullGroupNanos,cleanup.
Receipt paths are exactly op-N.jsonl beside the index. Cleanup fields are status,
elapsedNanos,ownersReleased,contextRestored. All PASS/true; cumulative cleanup<=120e9.
Each operation end-to-data-close<=90e9 and covers all pre-own-line group durations.
Full group maximum<=5e9 and covers all pre-own-line samples. Data including its
streamed hash is written/flushed/closed before operation clock stops. GROUP's own
time/bytes and footer tail are measured in the index,without self-reference.

Native post-exit transport schema=tsj-branch-order-native-transport-v1. Fields:
kind,sourceManifestSha256,indexSha256,compileExitCode=0,exitCode=0,
terminationProven=true,sourcesStable=true,processId,processStartTicks,
executableSha256,compilerExecutableSha256,powershellExecutableSha256,
isolationModuleSha256,compilerTerminationProven=true,nativeElapsedNanos,
cleanupElapsedNanos,cleanupStatus=PASS. Fixed hashes bind the accepted JDK8 java/
javac,PS5.1 and VerifierIsolation implementations. Native elapsed<=480e9 covers
sum of arm times and the full transport cleanup field; cleanup<=120e9 covers per-arm cleanup plus bounded
terminal-index work. Outer1800s remains a host requirement for root to verify.
No report field proves PID/ownership authority by itself; root audits real launches.

Input spec schema=tsj-branch-order-reader-input-v1 has kind,sourceManifest,
studyIndex,transport. File refs have path,bytes,sha256. Fixture transport is null.
Source manifest schema=tsj-branch-order-sources-v1 has kind,parentHead,
repositoryRoot,baseManifest,files. Files have role plus the same path/size/hash;
source paths are safe relative paths,not aliases. Fixture baseManifest is null.
Native baseManifest pins the exact accepted1079-row file; BASE_SOURCE rows equal it.
The CLI pins the input spec by --spec-sha256 and writes --output exclusively.
The WIRE role names docs/task-evidence/Q100/branch-tree-study-v3-r1/WIRE.md.
Generic source/index/spec/transport envelopes remain v1; source/wire pins and
the required v3 data-header schema reject incompatible v1 observations.

## Diagnostic work classification

Each extra clone is an actual existing P07 owner operation. When diagnostics are
enabled and immutable branch prefixViaCount<48,the relaxed-bound build makes two
additional pristine face copies. At zero remaining via capacity those copies
are common routing work and must not be classified as diagnostic-only. Existing
query-copy/bound/post-verification work remains routing work in every arm.

DIAGNOSTIC_CLONE is a closed record with fields:record,operation,ordinal,group,
pass,netId,startPadId,rootPadId,faceIndex,prefixViaCount,ownerSteps.
record=DIAGNOSTIC_CLONE; operation1..3; contiguous event ordinal from0 per arm;
group is the real current ADVANCE group; pass0..2; IDs use the exact bounded token
domain and reference census pads on the declared net; faceIndex0..1;
prefixViaCount0..47; ownerSteps=1 (strict integer). Producer emits once per actual
null-to-nonnull pristine-face slot transition at the existing post-assignment
AttemptObserver check,using actual branch object identity internally. It reads
the live route/search/branch,does not allocate a substitute router or change it.
Successful owner return charges the event in the existing raw lastWork count.
No event is allowed in plain or a non-ADVANCE/unmeasured group.

Event key(pass,netId,startPadId,rootPadId,faceIndex) is unique. Face0 precedes face1.
All events must precede and join one later terminal BRANCH by pass/net/start/root
and the identical prefixViaCount. An observed branch with relaxedBoundStarted
and prefixViaCount<48 requires complete bound plus all four observation/layout/
router-state/router-effect proof booleans and exactly faces{0,1}. A started but
incomplete bound is unsupported/missing proof,never inferred. Other branches
require zero diagnostic-only events. Extra,missing,duplicate,late,unmatched or
zero-capacity misclassified events fail. Bound events per arm<=2*3*(pads-nets).

GROUP.diagnosticCloneSteps equals that group's event count and is<=raw p07Steps;
it is0 when no measured P07 slice exists. Existing callback/census/serialized-byte
counters retain their meanings; serialized bytes include event rows. RESULT's
diagnosticCloneSteps equals all event/group counts; routingOwnerSteps equals raw
p07OwnerSteps minus verified diagnosticCloneSteps. All are exact nonnegative
integers. Raw owner steps remain the sum of actual lastWork and every raw32768/
640group/5s/90s bound stays unchanged. Diagnostic work is neither free nor hidden.

Plain versus observed canonical requires identical context,census,receipt,outcome,
winner,physical results,generatorAdvances,p07Slices,expansions and completed-request
routingOwnerSteps. Their raw owner-step delta must equal verified clone events.
Observed default versus explicit canonical additionally matches full raw logical
work,event stream (excluding op/group transport metadata) and branch/prefix stream.
Treatment pass0 events match control pass0 events as well as existing branches.
Treatment work is reported independently. Fixed slice quantums displace routing
work,so cross-arm per-group raw deltas are not a parity criterion; per-group self
event/raw reconciliation and completed-request normalized parity are required.
This explicit source-backed classification replaces the incorrect raw-owner-step
equality assumption; it does not change budgets or permit unexplained differences.

## Independent comparisons and claim limits

Plain/observed-default contexts,census,receipts,outcomes,winners,physical results
and the normalized routing-work comparison above match. Observed-default/explicit-nearest full branch snapshots
and prefixes also match. Treatment context and complete pass0 match. Reconstruct
lexical root,remaining/reached pads per pass/net from the actual census. Infer
prior FOUND publication only from a later selection; reset at each fresh pass.
Recompute nearest/farthest min-Manhattan with canonical ties. First pass1 start
divergence needs equal net/root/reached/remaining and prefix hash/bytes,distinct
independent nearest/farthest winners,and actual starts matching those winners.
An earlier nonselection difference invalidates attribution. Missing pass1 or
unequal trace coverage yields NOT_OBSERVED; no start difference yields
NO_DIVERGENCE. A tail FOUND's publication remains UNKNOWN without source proof.

The reader validates bounded observations and comparisons only. It grants no
all-path/board impossibility,model/electrical qualification,default adoption,
population expansion,playable100 or beta release. Independent reading is a later
timed phase. Missing termination/cleanup,timeout,exit2,truncation or lost proof
cannot pass. Full30 remains excluded; HARD/PSYCHOTIC remain unavailable/unrun.
