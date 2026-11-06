# Phase2 one-arm descriptor/trailer protocol R1

FROZEN with PLAN_R1. UTF8 JSON; strict duplicate rejection; exact fields below.

Native adapter CLI: KIND RUN_UUID INPUT_SHA WIRE_SHA EMPTY_ABSOLUTE_DATA_DIR.
KIND is TINY_CANARY or AUTHORED. Profile is immutable PrefixViaRampSupport.mode().
No support.startObservation call. Fixed8MiB combined stdout/stderr producer cap
installed before fixture work. Special sole arg --capture-overflow attempts one
8MiB+1 chunk, fails before stdout, nonzero exit with PREFIX_VIA_CAPTURE_LIMIT.

Data directory receives original op-1.jsonl and one-arm-index.json (CREATE_NEW).
Index<=65536B, fields exactly:
schema='tsj-via-price-first-request-index-v1';
profile='BASE'|'RAMP'; kind='TINY_NATIVE'|'AUTHORED'; runId;
sourceManifestSha256=INPUT_SHA; wireSha256=WIRE_SHA;
scope='ONE_CANONICAL_REQUEST_ONLY'; layoutQualification=false;
operations=[entry] (exactly one).
Entry fields exactly operation=1, armId='OBSERVED_DEFAULT_NEAREST',
receipt={path:'op-1.jsonl',bytes,sha256},
elapsedToCloseNanos, maxFullGroupNanos,
cleanup={status:'PASS',elapsedNanos,ownersReleased:true,contextRestored:true}.
All these facts come from returned actual ArmResult; cleanup failures throw.

stdout on success exactly one line:
PREFIX_VIA_FIRST_REQUEST_COMPLETE <JSON> newline.
JSON fields exactly schema='tsj-via-price-first-request-native-v1', profile, kind,
runId, sourceManifestSha256, wireSha256,
descriptor={path:'one-arm-index.json',bytes,sha256},
wrapper={preparationNanos,postNanos,elapsedNanos,workNanos,groups:2},
scope='ONE_CANONICAL_REQUEST_ONLY',layoutQualification:false.
workNanos=elapsedNanos-entry.cleanup.elapsedNanos; elapsed/post measured after
index bytes written and hashed (in-memory bytes), before trailer output.
Final post-output deadline checks also required; nonzero exit defeats any marker.
Preparation ends immediately before ArmRun construction; post begins after execute.
No hidden extra schema fields or fabricated source/cleanup facts.

Host emits schema='tsj-via-price-first-request-host-v1' launch, result schema
'tsj-via-price-first-request-host-result-v1', same existing actual process records,
compiler/native intent/source-list/source/bridge audits and lifecycle evidence.
Host final status='HOST_COMPLETE_READER_PENDING'; sourceClosure='PASS';
profile, kind, runId, inputPinsSha256, compilerSources=1077,
compilerProcessId,compilerProcessStartTicks,compilerExitCode,compilerTerminationProven,
nativeProcessId,nativeProcessStartTicks,nativeExitCode,nativeTerminationProven,
nativeWallMs,wrapperWorkNanos,postSourceAndScratchCleanupMs,
cleanupConservativeUpperMs,cleanupBoundMs=120000,scratchRemoved,
nativeCaptureLimitNegative='PASS',nativeOutputBytes,outerElapsedMs.
Keep raw process ticks as exact JSON integers; outer binding strings may be exact
decimal strings, as Phase1. Transport wall/resource cleanup are not operation CPU.

Outer reader CLI RUN_ROOT INPUT_PINS INPUT_SHA PROFILE KIND.
It validates all process/input/descriptor/receipt bounds, imports pinned oldreader,
calls validate_operation with arm1/kind, enforces cleanup/entry hash itself and
combined group budget. It returns 'PASS_ONE_REQUEST_DIAGNOSTIC_ONLY', never
five-arm parity or whole-generator acceptance. No fake entries for absent arms.
