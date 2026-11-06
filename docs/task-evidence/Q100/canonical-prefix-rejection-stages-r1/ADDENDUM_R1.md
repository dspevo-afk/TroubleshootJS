# Frozen canary and source-binding envelope addendum R1

Frozen before P6 adapter/host/reader implementation or any native execution;
PLAN31547c75 and PROTOCOL4b795d77 remain unchanged. This fills the host/canary
envelope details without changing predicates, scope, caps or accepted inputs.

Canary stdout retains maintained PASS lines followed by exactly one final marker
CANONICAL_PREFIX_STAGE_CANARY_COMPLETE with JSON keys schema,status,assertions,
blockerAssertions,stageAssertions,elapsedNanos,groups,maximumGroupNanos,
layoutQualification=false. Schema tsj-canonical-prefix-stage-canary-v1,
status=PASS; all three assertion counts positive integers, elapsed<=90e9,
groups<=640 and maximumGroupNanos<=5e9. P07 maintained849 remains an actual
preceding PASS line; blockerAssertions is the unchanged P5 helper's canary count.
Stage helper constructor permits NULL(false,false), OBSERVE(true,false) and
CALLBACK_THROW(true,true); false,true must reject before mutation.

Input manifest schema tsj-canonical-prefix-stage-inputs-v1. Required keys:
schema,repositoryRoot,historicalRoot,parent,baselineManifestPath,
baselineManifestSha256,sourceRows,changedRows,modeFiles,bridgeRoot,bridgeManifest,
preflight,nullRunRoot,p5Root,p5InputPinsPath,p5InputPinsSha256,p5QualificationPath,
p5QualificationSha256,p5ReaderPath,p5CaptureRunRoot,p5ControlReceipt,
planPath,planSha256,protocolPath,protocolSha256,addendumPath,addendumSha256,
zeroDeltaInputPath,zeroDeltaInputSha256,fullCutInputPath,fullCutInputSha256,files.
sourceRows1084,changedRows empty, parent831d69a41d9504bfb845caef6942e9ef58c9d6d2;
eight modeFiles roles exactly as protocol; all consumed paths in files, with
byte/SHA pins and no duplicates. Extra-file census comes from the actual final
manifest; no implicit directory enumeration or whole old-scratch search.
Accepted P5 inputs/qualification are exact1e570d55/301fca39; P5 source modules
and P4 inherited graph/operation oracles are rebound through their actual refs.

Host launch schema tsj-canonical-prefix-stage-host-v1 and result schema
tsj-canonical-prefix-stage-host-result-v1 retain the exact P5 launch/result keys.
Status LAUNCHED_NOT_ACCEPTED then HOST_COMPLETE_READER_PENDING. ModeCANARY,
NULL,OBSERVE,CALLBACK_THROW. Script identity/hashes, canonical absolute paths,
scratch identity, exact JDK8/Python/PS5/module/bridge/source-list/process bindings,
before/pre-native/post source closures and owned cleanup stay P5 rules.
Compiler source count1080 and isolation budgets unchanged. Failure schema
tsj-canonical-prefix-stage-host-failure-v1, statusFAIL with actual phase/error.
Source-audit stdout exact keys status,sourceRows,changedRows,extraFiles,pinsSha256;
status PASS_CANONICAL_PREFIX_STAGE_INPUT_CLOSURE_ONLY,1084,0,actual count,actualSHA.

Native normal stage helper completion requires actual graph SHA. P5 normal
blocker payload is checked with unchanged inner attribution semantics; new native
adapter provides its own outer source closure. NULL uses both inspectors and a
graph capture, disabling only the stage callback. CALLBACK_THROW omits the P5
blocker inspector and proves only the actual stage callback's partial/cleanup
boundary. It must never imply a completed P5 inspection, operation or graph export.

Independent reader API validate_run(run_root,pins_path,pins_sha,mode), CLI four
arguments, schema tsj-canonical-prefix-stage-reader-result-v1. Exit0 only on
PASS_CANARY_ONLY, PASS_NULL_CONTROL_ONLY, PASS_STAGE_OBSERVATIONS_ONLY or
PASS_EXPECTED_CALLBACK_THROW_CLEANUP_ONLY. Rejections exit2 with typed REJECTED
status/error and false route/layout/electrical/q100 acceptance. Results must
include scope/layoutQualification=false, actual mode/runId/input SHA and cleanup
upper; normal comparisons preserve ordered routing/graph/inner attribution while
separately validating actual scheduling/observation units. Full source/pin closure
is rechecked after successful validation. Do not relax P5 predicate/geometry/counter
oracles or claim historical whole-run acceptance for changed outer sources.
