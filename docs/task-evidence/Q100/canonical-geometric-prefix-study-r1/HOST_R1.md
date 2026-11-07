# P8 BASE host contract R1

FROZEN before host implementation. INPUTS_R1, SOURCE_INVENTORY_R1,
NATIVE_TRANSPORT_R1, READER_R1 and CONTROL_CASES_R1 are the accepted contracts.
PLAN_R1 controls scope. This freeze is not a runtime PASS; all new host and
BASE gates remain NOT RUN until completed source is actually qualified.

## Inputs and launch

Use the input object from INPUTS_R1.md: schema
`tsj-q100-geometric-prefix-study-inputs-v1`, with exact top-level keys
`schema,repositoryRoot,parent,canaryPrerequisite,p6Context,sourceInventory,
roles,files,toolPaths`. `repositoryRoot` is the unchanged E source tree at parent
`0891474cadc4b9e00d7911e81c124721a11efb87`. Keep original P6 root and parent
inside `p6Context`; authenticate its R6 pins SHA
`b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771`, original
parent `831d69a41d9504bfb845caef6942e9ef58c9d6d2`, and equality with frozen
CANARY R9 without rewriting that context. Authenticate CANARY R9 pins
`11b3fa459e9bf6950c7ee3d3e804a3b37638bb23cc6c322aaa8ef94549462a5a` and
qualification `755f921b068f325c9e81315b88f38c4bc8b7196dd61d65f8c8ecf61e26e43276`.
Reuse the CANARY host behavior only after authenticating all consumed bytes;
the pinned `run-canary.ps1` SHA-256 is
`0ceb429f4120173843f2c1bd73d4ed2659b225a0a7d1515def65dbc594f179e0`.
Host receipt/process semantics are reused from `CANARY_HOST_R1.md` SHA-256
`4fce0587587b8bf4c1e18064171bba2215ef68a194c8c0b77ce4c85088c3843d` and that
pinned host source. The BASE input contract is `INPUTS_R1.md` SHA-256
`9c2002d2d95366615ca6830ee421333d9bccc2d425dfc9156c998ca80b49875e`;
the source-inventory contract is `SOURCE_INVENTORY_R1.md` SHA-256
`836b0d57086ceea09dc6db2579a970567cdc16c35b678594df88772c9c00ae21`.

The external `native-source-inventory.jsonl` is authenticated by
`sourceInventory.path/sha256` and one `files` row. Validate its exact
SOURCE_INVENTORY_R1 framing and rows against CANARY R9 and current E: 1084
source rows, 850 production, no differences. `inputSha256` is the BASE pins
digest; `sourceSha256` is the inventory digest; `wireSha256` is the qualified
P6 wire digest. Preserve these as separate values in every receipt and native
invocation. No absolute personal paths enter public results.

The only host entry is selected Windows PowerShell 5.1, `run-study.ps1`:

```text
powershell.exe -NoLogo -NoProfile -NonInteractive -File run-study.ps1 -RepositoryRoot <E> -InputPinsPath <pins> -InputPinsSha256 <lowerhex64> -OutputRoot <fresh-external-temp> -JavaHome <pinned-JDK8> -PythonExe <pinned-Python> -Mode SUITE
```

`Mode` is exactly `SUITE` or `CANARY`. `CANARY` is the focused early transport
gate. `SUITE` runs one compile and the same qualified canaries and malformed
argument controls, then the fixed ordered BASE modes
`NULL,OBSERVE,CALLBACK_THROW,REASON_THROW`. `modeOrder` is exactly
`[P6_CANARY,P8_LITERAL_CANARY,P8_BAD_MODE,P8_BAD_UUID,P8_BAD_INPUT_SHA,
P8_NONEMPTY_DATA]` in CANARY, with those four BASE modes appended in SUITE. No
arbitrary subsets or caller-supplied mode arrays are accepted. The parent
invokes the independent reader after the host returns. Host status is
`HOST_CANARY_COMPLETE_READER_PENDING` for CANARY and
`HOST_COMPLETE_READER_PENDING` for SUITE until that separate read succeeds.

## Preflight, source and compiler

Before creating output or scratch, authenticate the invoking PowerShell image,
host script, pins digest/schema/types/unique keys, all `files` rows and role
mapping, source inventory, selected Python/JDK/module paths and hashes, genuine
P6 context, and canonical paths with no reparse ancestors. Reject existing,
source-overlapping, or input-overlapping output roots. Run the selected isolated
Python auditor (`-I -B`) before any mutation. Preserve the five selected-host
rejection controls from the frozen CANARY contract; they remain separate
parent-controlled executions and must reject before compile/native work.

After preflight succeeds, create only the fresh task-owned output and scratch.
Audit all 1084 source files before compile, before native execution, and after
native work. Keep the accepted P6 overlays passive and byte-checked. Compile
once from the exact ordered 1081-file CANARY Java list plus exactly
`CanonicalGeometricPrefixStudy.java` and
`CanonicalGeometricReasonInspector.java` (1083 Java inputs total).
Use the unchanged exclusions from that pinned list: production `CirSim.java`,
`ChipElm.java`, `PhysicalSpecificationDeveloperVerifier.java`,
`PcbLayerRoutingPrototype.java`, `PcbNetRouter.java`, and
`PcbTwoLayerRules.java`. There are no other exclusions or implicit source
lookups. Preserve the three genuine bridges by copying to dedicated scratch
and verifying each name, length, and digest. Use the pinned JDK 8 compiler,
source/target 7, UTF-8, empty sourcepath, and the exact two pinned GWT jars.
Do not alter the E checkout. Save the exact compiler argument and source list,
raw compiler process receipt and streams, bridge-copy receipt, and all three
source-audit receipts.

## Exact native sequence

Run the original P6 canary as
`-ea -cp <qualified-classpath> com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy --canary`;
run the frozen P8 literal canary as
`-ea -cp <qualified-classpath> com.lushprojects.circuitjs1.client.CanonicalGeometricReasonCanary`
with zero application arguments. These prove the pinned sources and literal
fixtures only; neither is the BASE result. The new
`CanonicalGeometricPrefixStudy` has no `--canary` entry point.

Before BASE observations, run four actual selected-Java preflight controls
against the new study exactly as defined in `CONTROL_CASES_R1.md` SHA-256
`515270728ae7498496b35674d0112e58734cce54a22e90cb849176a6aaaeace83`:
unsupported mode, malformed UUID, invalid input-SHA syntax, and nonempty output
directory. Each child has six argv positions with that one field changed and
all other values valid. Require its expected native error code/argIndex,
nonzero exit, proven termination, empty stdout, and exactly one stderr frame
`P8_GEOMETRIC_PREFIX_PREFLIGHT_REJECT <compact JSON>`. The nonempty directory
contains the exact frozen sentinel; verify its bytes/hash before and after.
Preserve each actual bounded process receipt and exact streams. A successful
negative control is not a BASE run.

For `NULL`, `OBSERVE`, and `REASON_THROW`, call the new main with exactly six
arguments:

```text
MODE RUN_UUID BASE_INPUT_PINS_SHA256 QUALIFIED_WIRE_SHA256 SOURCE_INVENTORY_SHA256 EMPTY_ABSOLUTE_DATA_DIR
```

For `CALLBACK_THROW`, invoke the unchanged original P6
`CanonicalPrefixStageStudy` five-argument operation:

```text
CALLBACK_THROW RUN_UUID BASE_INPUT_PINS_SHA256 QUALIFIED_WIRE_SHA256 EMPTY_ABSOLUTE_DATA_DIR
```

Use a fresh unique run ID and fresh empty data directory for every operation.
Keep P6's original five-argument protocol and old throwable contract; its
`sourceManifestSHA` slot is the BASE input-pins digest. Do not put the BASE
operation under the historical P6 root, call its full old-host reader, or
fabricate a P6 reason sidecar. The P6 callback throw has no P8 sidecar. P8
reason sidecars are only for NULL, OBSERVE, and REASON_THROW, with header
`inputSha256` bound to BASE pins and `sourceSha256` bound to the inventory;
their exact rows/footer/marker and REASON_THROW evidence are the
hash-pinned NATIVE_TRANSPORT_R1.md contract. The P8 success marker for NULL and
OBSERVE is `CANONICAL_GEOMETRIC_PREFIX_COMPLETE`; REASON_THROW has only the
expected stderr frame `CANONICAL_GEOMETRIC_PREFIX_EXPECTED_REASON_THROW` and
its exact partial four-file bundle. REASON_THROW is never a complete
observation. Old P6 CALLBACK_THROW preserves its exact
`CANONICAL_PREFIX_STAGE_EXPECTED_CALLBACK_THROW` marker and partial
`op-1.jsonl` contract; the independent reader validates it with the original
P6 exception validator against actual BASE run/pins and genuine P5/P4 context.

## Receipts, output inventory and limits

Call the pinned `scripts/VerifierIsolation.psm1` bounded process API for every
auditor, compiler, canary, negative-control, and native child. Keep each
returned receipt byte/field-identical to the module result, including actual
image, PID and creation ticks, arguments, exit, termination proof,
`ProcessRoot`, and log paths. Copy stdout/stderr as exact bytes. No process
root/log directory may remain after the bounded call. If the API throws before
returning a receipt, record the attempted executable/arguments/limit/wall and
original exception with process/termination unavailable; never synthesize a
receipt. If a returned receipt fails validation, retain that raw receipt and
wall. Preserve the first failure and emit the frozen one-line reject frame;
never print a success marker on a failure path.

Use this fixed host-evidence inventory (all paths relative to `OutputRoot`;
each name below includes its literal extension):

The `runs/` rows below are SUITE-only. CANARY mode has no `runs` directory.

```text
host-launch-intent.json
scratch-intent.json
source-preflight-process.json
source-preflight.stdout
source-preflight.stderr
compiler-launch-intent.json
compiler-process.json
compiler.stdout
compiler.stderr
compiler-sources.txt
bridge-copies.json
source-prenative-process.json
source-prenative.stdout
source-prenative.stderr
p6-canary-launch-intent.json
p6-canary-process.json
p6-canary.stdout
p6-canary.stderr
p8-canary-launch-intent.json
p8-canary-process.json
p8-canary.stdout
p8-canary.stderr
p8-bad-mode-launch-intent.json
p8-bad-mode-process.json
p8-bad-mode.stdout
p8-bad-mode.stderr
p8-bad-uuid-launch-intent.json
p8-bad-uuid-process.json
p8-bad-uuid.stdout
p8-bad-uuid.stderr
p8-bad-input-sha-launch-intent.json
p8-bad-input-sha-process.json
p8-bad-input-sha.stdout
p8-bad-input-sha.stderr
p8-nonempty-data-launch-intent.json
p8-nonempty-data-process.json
p8-nonempty-data.stdout
p8-nonempty-data.stderr
controls/P8_BAD_MODE/data/
controls/P8_BAD_UUID/data/
controls/P8_BAD_INPUT_SHA/data/
controls/P8_NONEMPTY_DATA/data/
controls/P8_NONEMPTY_DATA/data/sentinel.txt
source-post-process.json
source-post.stdout
source-post.stderr
runs/NULL/launch-intent.json
runs/NULL/process.json
runs/NULL.stdout
runs/NULL.stderr
runs/NULL/data/op-1.jsonl
runs/NULL/data/fixed-prefix.jsonl
runs/NULL/data/prefix-blockers.json
runs/NULL/data/prefix-stages.json
runs/NULL/data/canonical-stage-index.json
runs/NULL/data/prefix-geometric-reasons.jsonl
runs/NULL/data/canonical-geometric-prefix-index.json
runs/OBSERVE/launch-intent.json
runs/OBSERVE/process.json
runs/OBSERVE.stdout
runs/OBSERVE.stderr
runs/OBSERVE/data/op-1.jsonl
runs/OBSERVE/data/fixed-prefix.jsonl
runs/OBSERVE/data/prefix-blockers.json
runs/OBSERVE/data/prefix-stages.json
runs/OBSERVE/data/canonical-stage-index.json
runs/OBSERVE/data/prefix-geometric-reasons.jsonl
runs/OBSERVE/data/canonical-geometric-prefix-index.json
runs/CALLBACK_THROW/launch-intent.json
runs/CALLBACK_THROW/process.json
runs/CALLBACK_THROW.stdout
runs/CALLBACK_THROW.stderr
runs/CALLBACK_THROW/data/op-1.jsonl
runs/REASON_THROW/launch-intent.json
runs/REASON_THROW/process.json
runs/REASON_THROW.stdout
runs/REASON_THROW.stderr
runs/REASON_THROW/data/op-1.jsonl
runs/REASON_THROW/data/prefix-blockers.json
runs/REASON_THROW/data/prefix-stages.json
runs/REASON_THROW/data/prefix-geometric-reasons.jsonl
suite-index.json
suite-footer.json
host-result.json
```

The only allowed directories in CANARY are `controls`, `controls/P8_BAD_MODE`,
`controls/P8_BAD_MODE/data`, `controls/P8_BAD_UUID`,
`controls/P8_BAD_UUID/data`, `controls/P8_BAD_INPUT_SHA`,
`controls/P8_BAD_INPUT_SHA/data`, `controls/P8_NONEMPTY_DATA`, and
`controls/P8_NONEMPTY_DATA/data`. In SUITE add exactly `runs`, `runs/NULL`,
`runs/NULL/data`, `runs/OBSERVE`, `runs/OBSERVE/data`,
`runs/CALLBACK_THROW`, `runs/CALLBACK_THROW/data`, `runs/REASON_THROW`, and
`runs/REASON_THROW/data`. The
nonempty-control sentinel is exactly
`controls/P8_NONEMPTY_DATA/data/sentinel.txt` and has the frozen bytes from
CONTROL_CASES_R1.md. All three other control data directories stay empty. The
host result and output closure list the exact allowed directory paths; no
implicit parent or empty directory is tolerated.

The native payload names and byte rules are pinned to `NATIVE_TRANSPORT_R1.md`
SHA-256 `62b55f64dabb23a6e46a40809254a55b2725afefa8c8460643cd778bcd6c6718`.
NULL/OBSERVE each contain exactly these seven native files:
`op-1.jsonl,fixed-prefix.jsonl,prefix-blockers.json,prefix-stages.json,
canonical-stage-index.json,prefix-geometric-reasons.jsonl,
canonical-geometric-prefix-index.json`. CALLBACK_THROW contains only
`op-1.jsonl`; REASON_THROW contains exactly `op-1.jsonl,prefix-blockers.json,
prefix-stages.json,prefix-geometric-reasons.jsonl`, no fixed-prefix transport
or normal index. There are no globs or recursive discovery. Reject missing, duplicate, extra,
unreferenced, or changing files. All relative paths use `/`, are unique and
strictly ordinal-sorted in the index. The suite index has exact keys
`schema,status,suiteId,mode,inputSha256,sourceSha256,wireSha256,modeOrder,
artifactCount,aggregateBytes,artifacts,directories,layoutQualification`, with values
`schema="tsj-q100-geometric-prefix-suite-index-v1"`,
`status="HOST_INDEX_COMPLETE"`, `mode=CANARY|SUITE`, canonical UUID `suiteId`,
lowercase SHA-256 digests, the exact mode order above, and
`layoutQualification=false`. Every artifact row has exact keys
`path,bytes,sha256,role,operation`: path/role are strings, bytes is a
nonnegative integer (not bool; zero for empty streams), sha256 is lowercase
hex64, and operation is `HOST` or one
exact token from `modeOrder`. `role` is exactly one of `HOST_INTENT`,
`SOURCE_AUDIT`, `PROCESS_RECEIPT`, `STDOUT`, `STDERR`, `COMPILER_INPUT`,
`BRIDGE_RECEIPT`, `CANARY_REPORT`, `CONTROL_REPORT`, `NATIVE_DATA`, or
`MODE_INDEX`. `artifactCount` counts only artifact rows and `aggregateBytes`
is only their byte sum; both explicitly exclude `suite-index.json`,
`suite-footer.json`, and `host-result.json`. The `directories` array contains
every allowed relative directory in strict ordinal order. No artifact row may
name those three closure files.

The footer has exact keys
`schema,status,suiteId,inputSha256,sourceSha256,wireSha256,modeOrder,
artifactCount,indexBytes,indexSha256,aggregateBytes,directoryCount,layoutQualification`,
with `schema="tsj-q100-geometric-prefix-suite-footer-v1"`,
`status="HOST_OUTPUT_CLOSED"`, values matching the index, and
`layoutQualification=false`. Its `artifactCount`/`aggregateBytes` equal the
index values; `indexBytes`/`indexSha256` bind the canonical index. It excludes
its own bytes and the host-result bytes. `directoryCount` equals the index
directory-array length. Native JSONL remains exact UTF-8
without BOM and LF; do not normalize raw receipts, stdout, or stderr.

`host-result.json` has the exact top keys
`schema,status,mode,suiteId,inputSha256,sourceSha256,wireSha256,modeOrder,
host,processes,sourceChecks,compiler,bridgeCopies,nativeCanaries,operations,
timing,cleanup,outputClosure,limits,layoutQualification`. Its schema is
`tsj-q100-geometric-prefix-host-v1`; statuses are
`HOST_CANARY_COMPLETE_READER_PENDING` and
`HOST_COMPLETE_READER_PENDING`. `layoutQualification` is false. `host` reuses
the exact CANARY host object shape from `run-canary.ps1` SHA
`0ceb429f4120173843f2c1bd73d4ed2659b225a0a7d1515def65dbc594f179e0`:
`pid:int,startTicks:string,executable:string,executableSha256:lowerhex64,
script:string,scriptSha256:lowerhex64`.

`processes` is an object. CANARY has exactly the ordered keys
`preflight,compiler,preNative,p6Canary,p8Canary,badMode,badUuid,badInputSha,
nonemptyData,postSource`. SUITE has those keys plus `nullMode,observeMode,
callbackThrow,reasonThrow` immediately before `postSource` (14 keys total).
Every value reuses the exact CANARY `processRecords` shape: `receipt,stdout,
stderr,wallMs`; the first three are relative artifact paths, and `wallMs` is
a finite nonnegative number. Successful output requires a returned raw
receipt for every listed process. A thrown bounded API call cannot produce a
success host result. `sourceChecks` is an object with exactly `preflight,
preNative,postSource`; each entry has exact keys
`status,sourceRows,productionFiles,extraFiles,pinsSha256,sourceInventorySha256`
with exact `status="PASS_Q100_GEOMETRIC_PREFIX_INPUTS_ONLY"`,
`sourceRows=1084`, `productionFiles=850`, `extraFiles` a nonnegative integer,
`pinsSha256=inputSha256`, and `sourceInventorySha256=sourceSha256`; hashes are
lowercase hex64, counters are exact integers rather than bools.
`compiler` reuses the CANARY exact shape
`sourceCount,sourceList,sourceListBytes,sourceListSha256` (1083, the relative
`compiler-sources.txt` path, exact byte count and lowercase hash).
`bridgeCopies` reuses the CANARY exact shape
`receipt,count,verifiedAfterCompile,verifiedBeforeNative,verifiedAfterNative`
with three verified bridges.

`nativeCanaries` has exactly `p6,p8,inputControls`. `p6` reuses the CANARY
shape `schema,status,assertions,blockerAssertions,stageAssertions,elapsedNanos,
groups,maximumGroupNanos,layoutQualification`; `p8` reuses the CANARY shape
`schema,status,assertions,caseCount,workNanos,groups,maximumGroupNanos,
totalObservationUnits,layoutQualification`. Both have status `PASS` and
`layoutQualification=false`. `inputControls` is an array of exactly four rows
in mode order; each row has exact keys
`name,expectedFailureCode,observedFailureCode,exitCode,terminationProven,
dataDirectoryUnchanged`. The name/code pairs are exactly
`P8_BAD_MODE/INVALID_MODE`, `P8_BAD_UUID/INVALID_RUN_UUID`,
`P8_BAD_INPUT_SHA/INVALID_INPUT_SHA256`,
`P8_NONEMPTY_DATA/OUTPUT_DIRECTORY_NOT_EMPTY`. `exitCode` is a nonzero exact
integer, `terminationProven` and `dataDirectoryUnchanged` are true.

`operations` is an array of exactly four rows in SUITE and empty in CANARY.
Each row has exact keys
`mode,runId,disposition,processKey,dataDirectory,workNanos,groups,
observationUnits`; modes are the fixed four BASE modes, runId is a canonical
lowercase UUID, and `processKey` maps to the matching SUITE process entry.
`dataDirectory` is `runs/<MODE>/data`. Disposition is `COMPLETE` for
NULL/OBSERVE and `EXPECTED_THROW` for CALLBACK_THROW/REASON_THROW. `workNanos`
is null only for old P6 CALLBACK_THROW; otherwise it is an exact nonnegative
integer. `groups` and `observationUnits` are always exact nonnegative integers.
NULL/OBSERVE `workNanos`
is the whole-wrapper work from the success completion marker; for REASON_THROW
it is the whole-wrapper work from its expected-exception marker; CALLBACK_THROW
uses null because its pinned P6 marker schema remains unchanged. Group/unit
values are exact nonnegative integers: `groups` is actual op-1 GROUP rows plus
the three native wrapper groups; `observationUnits` comes from the P8 footer's
total for NULL/OBSERVE/REASON_THROW and is exactly zero for old P6
CALLBACK_THROW. The footer's nested `groupCount` is separately retained in
native data and is not added to the 640 scheduler-group count. Only
NULL/OBSERVE completion-marker work may become cleanup credit. `timing` has
exact fields
`nativePhaseStartOffsetMs,cleanupStartOffsetMs,nativePhaseElapsedMs,
`childWallSumMs,workCreditsMs,nativePhaseChargeMs,nativePhaseOverheadMs,
hostElapsedBeforeFinalWriteMs`. All are finite nonnegative numbers. The
continuous interval is `cleanupStartOffsetMs-nativePhaseStartOffsetMs` and
`nativePhaseElapsedMs` equals that interval; require elapsed >= full child-wall
sum. `nativePhaseOverheadMs` equals elapsed minus the full child-wall sum.
`workCreditsMs` is only the sum of actual whole-wrapper work from NULL and
OBSERVE completion markers, each <= its same-mode child wall.
`nativePhaseChargeMs=nativePhaseElapsedMs-workCreditsMs` and must be >= the
sum of child-wall residuals after those two credits. No parent elapsed or
cleanup upper bound is host-authored; the parent derives them from its actual
full wall and the host offsets.

`cleanup` reuses the CANARY exact shape
`scratchPath,scratchCreationTicks,scratchRemoved,boundMs,formula`; `boundMs`
is 120000 and `formula` is
`nativePhaseChargeMs+(parentFullHostElapsedMs-cleanupStartOffsetMs)`. The
parent reader evaluates the formula, including final host-result I/O. The
`outputClosure` has exact keys
`files,directories,aggregateBytes,hostResultExcluded`; each file row is exact
`name,bytes,sha256`, with unique sorted relative paths, and `directories` is
the exact sorted directory list defined above. It covers all output files
except `host-result.json` and includes suite-index/footer bytes. `aggregateBytes`
is that exact file-byte sum and `hostResultExcluded=true`; the reader measures
and validates host-result bytes and the total suite cap.
`limits` has exact integer keys
`compileMs,nativeMs,auditMs,readerMs,cleanupMs,outerMs,workNanos,groups,
groupNanos,observationUnits,operationBytes,hostEvidenceBytes,suiteBytes,
lineBytes,compilerListBytes,inventoryBytes` with values respectively
60000,480000,60000,180000,120000,1800000,90000000000,640,5000000000,32768,
8388608,8388608,41943040,65536,1048576,1048576. All integer fields are exact
integers, not booleans. This contract reuses host/process/source/compiler/
bridge/canary/cleanup field shapes from the hash-pinned CANARY host wherever
unchanged; only BASE-specific operation, index and directory fields are new.

On success, host stdout is exactly one CRLF-terminated line
`P8_PREFIX_HOST_COMPLETE <compact JSON>` with no other stdout bytes. The JSON
has exactly keys
`schema,status,mode,suiteId,inputSha256,sourceSha256,wireSha256,hostResult,
suiteIndex,suiteFooter,layoutQualification`. `schema` is
`tsj-q100-geometric-prefix-host-complete-v1`; `status,mode,suiteId` and the
three digests equal `host-result.json`; `hostResult`, `suiteIndex`, and
`suiteFooter` are exact `{path,bytes,sha256}` references to the three named
files; each reference has a relative path string, positive exact byte count and
lowercase SHA-256. `layoutQualification=false`. Write the marker only after closing,
reopening, and hashing host-result/index/footer and verifying the allowlist,
directory closure, scratch removal, and source post-audit. Then exit zero with
proven termination. No success marker survives a failure or nonzero exit.

Host rejection uses `CONTROL_CASES_R1.md`'s exact one-line frame
`P8_PREFIX_HOST_REJECT <JSON>` (a single PS5 CRLF is allowed), empty stdout,
nonzero exit, and proven termination. JSON has exactly
`code,phase,outputCreated,scratchCreated,compilerInvoked,nativeInvoked,
cleanupError`; code/phase are strings, the four flags are actual booleans, and
cleanupError is null or a string. Expected negative controls require their
specific code and all four flags false with null cleanupError. Preserve actual
bounded child receipts and the original first failure; never map a startup,
import, or traceback failure to an expected code.

The root controller records the selected host child in an external parent
receipt with exact schema `tsj-q100-geometric-prefix-parent-v1` and keys
`schema,elapsedMs,process`; `process` is the unchanged bounded-module receipt.
The host neither writes nor transforms that parent receipt. `elapsedMs` is the
actual full host-process wall used by the reader's cleanup bound and outer
limit.

Each native operation keeps its own <=8 MiB cap for data, index/parent
artifacts, and captured streams; do not raise it to fit the suite. The four
operation caps are independent because BASE contains four operations. Keep
host metadata/source evidence and CANARY/preflight control streams separately
<=8 MiB. Inventory all four operations and all control streams without
deduplication; the derived complete-suite ceiling is 40 MiB (4 x 8 MiB plus
8 MiB host evidence).
For the operation cap, sum all indexed files whose `operation` equals that
native mode, including its native data, mode index where present, launch
intent, raw process receipt and copied stdout/stderr. The host-evidence cap
sums `operation=HOST`, both canaries, all four malformed controls, plus the
suite index/footer/host-result bytes; include the nonempty sentinel once.
The reader computes these partitions from exact indexed paths/roles and counts
every physical file once, without subtracting duplicate stdout/stderr strings inside
process receipts. The total cap includes the host-result bytes too.
Each structured line/index/report is <=64 KiB; the exact compiler source list
and inventory retain their separate 1 MiB limits. No unbounded logs or
recursive output discovery.

## Timing, cleanup and acceptance boundary

Keep the existing caps: compiler 60 s, each bounded child 480 s, reader 180 s,
selected parent 1800 s, cleanup 120 s. Preserve native work limits of 90 s,
640 groups, 5 s per complete group, and 32768 combined observation units; keep
the 32768 P07 slice, 1M expansion/clone limits, 48 holes/four flips and 4096
chunk limit. Apply the frozen per-operation native limits, count actual BASE
work, and add P6's hierarchical total once to P8 own units. Nested callback
time is diagnostic overlap, not a second group. CANARY uses its frozen literal
limits. No budget is increased by this suite.

Use a monotonic host clock. Start `nativePhaseStartOffsetMs` immediately before
the first Java child and `cleanupStartOffsetMs` immediately before the
post-native source/scratch closure. The continuous native phase includes all
canary and malformed-control calls, all four BASE child calls, child setup
between calls, raw receipt/stream copies, writes, and parsing. Validate
`0 <= nativePhaseStartOffsetMs <= cleanupStartOffsetMs <= hostElapsedBeforeFinalWriteMs`.
`nativePhaseElapsedMs = cleanupStartOffsetMs-nativePhaseStartOffsetMs` and must
be at least the sum of full bounded child walls. `nativePhaseOverheadMs` is
exactly phase elapsed minus that full-wall sum. Credits are exactly the sum
of whole-wrapper work from only the NULL and OBSERVE success markers; each
credit must be <= its corresponding same-mode child wall.
`nativePhaseChargeMs = nativePhaseElapsedMs-workCreditsMs` and must be at
least the sum of every child-wall residual after only those credits. Charge
full walls for both throw operations, both positive canaries, and malformed
controls. Do not clamp residuals, adjust offsets, or credit either throw.

The parent derives the conservative cleanup bound as
`nativePhaseChargeMs+(parentFullHostElapsedMs-cleanupStartOffsetMs)` and
requires it <=120000 ms. This charges all post-native audit/bridge/source
checks, verified scratch removal, suite index/footer/host-result writes,
stream draining, and host return. The host does not write a parent elapsed
value or cleanup upper bound it cannot observe. REASON_THROW uses
`FULL_NATIVE_WALL_PLUS_POST_SOURCE`, preserves the four-file partial evidence
and completed P5/P6 proofs from NATIVE_TRANSPORT_R1, and earns no work credit.
The reader and parent closure are separate and remain within the 180 s reader
and 1800 s outer limits. Failed cleanup,
missing termination/source proof, late write, cap breach or extra output means
failure, not PASS.

The selected independent reader consumes only the returned host bundle and
actual BASE modes. It validates P4/P5/P6 route and result parity on genuine BASE
context, rederives the cut from graph/facts, joins exactly the original 14
selected query ordinals, validates the exact P8 reason records, and checks the
old P6 callback exception using its original validator with actual BASE run
and pins while retaining the genuine P6/P5/P4 capture context. REASON_THROW
requires exact first-fine-before-coarse callback and cleanup proof. NULL and
OBSERVE must preserve route/counters/effects and all 14 identities. A passing
result establishes only the scoped geometric reason observation. It does not
qualify route benefit, physical feasibility, electrical/player behavior,
population, GWT, full30, HARD or PSYCHOTIC data.

## Parent and contract-set binding

The actual selected-host parent receipt has exact keys schema,elapsedMs,process
and schema tsj-q100-geometric-prefix-parent-v1. elapsedMs is its full actual
bounded call through returned process/stream cleanup. process is the unchanged
VerifierIsolation result, never a fabricated host-owned summary. The independent
reader uses that actual parent to compute the final tail/cleanup upper bound.

CONTRACT_SET_R1.json has exactly schema,status,sourceParent,contracts. schema is
tsj-q100-geometric-prefix-contract-set-v1; status is
FROZEN_FOR_IMPLEMENTATION_ONLY; sourceParent is
0891474cadc4b9e00d7911e81c124721a11efb87. contracts is exactly eight rows with
exact path,bytes,sha256 fields, sorted by unique relative path, naming
CONTROL_CASES_R1.md,HOST_R1.md,INPUTS_R1.md,NATIVE_TRANSPORT_R1.md,PLAN_R1.md,
PREREQUISITE_REUSE_REVIEW_R1.md,READER_R1.md,SOURCE_INVENTORY_R1.md. Each row
binds current actual bytes and lowercase SHA-256 and agrees with its files row
in BASE pins. No self-hash, absolute path, code hash or runtime success flag.
Canonical compact ASCII-escaped sorted-key JSON plus one LF, <=65536 bytes.

The new auditor validates this exact set before host compile/native work.
Original CANARY/P5/P6 code and data remain unchanged. New raw source/process
records are private runtime evidence; public packet material must be sanitized.

## R2 evidence authority and failure retention amendment

Explicit scope correction before the first new-host run or BASE pins. The
immutable selected VerifierIsolation.psm1 fb07ad54 returns decoded Stdout and
Stderr strings through ReadToEndAsync and removes its temporary log files
before returning. Its contract does not return original OS pipe bytes. The
earlier phrase "exact stream bytes" here means exactly UTF8(original returned
receipt strings), without newline or text normalization. The complete returned
process object is preserved unchanged. This is the original qualified CANARY
capture authority; it is not raw OS byte preservation or strict source-pipe
BOM/invalid-UTF8 rejection. No module or source baseline is changed.

Real selected-implementation probes establish the limit: ASCII41420a survives;
emitted efbbbf41420a becomes41420a; emitted ff41420a becomes efbfbd41420a in
UTF8(receipt.Stdout). Each selected child exits0 with proven termination and
absent process roots. The probe is OBSERVED_DECODED_TEXT_AUTHORITY_ONLY, never
PASS_RAW_BYTE_CAPTURE. The qualification must disclose this finite authority.
Original native artifact FILE bytes retain every strict LF/BOM/UTF8/hash/EOF
assertion. Normal text-to-receipt equality, source/argv/PID/exit/cleanup proofs
and all existing success caps remain unchanged. No failed assertion is removed
to claim raw capture. General raw-byte/other-encoding support is excluded.

Every actual returned process is registered in memory immediately, before
receipt/exit/size validation. Where success OutputRoot already exists, preserve
its normal receipt/streams promptly. Preflight may retain the original object
in memory until output creation; this does not bypass preflight's prohibition
on success-root/compiler-scratch mutation. Any later validation failure must
flush all registered returned records; no64KiB summary contains a large raw
receipt and no failed summary write is silently swallowed.

On unexpected failure, allocate a fresh task-owned OS-temp directory named
tsj-p8-prefix-failure-r1-<32lowerhex>, outside success output and input roots.
Preserve this evidence directory after failure; it is not compiler scratch.
Verify canonical path, containment, no reparse ancestors, creation identity and
actual owner PID/start ticks. There is no new child process for this bundle.
Never add source-revisions/private failures to the17-source/25-file input set.

owner.json has exact schema,hostPid,hostStartTicks,hostExecutable,
hostScriptSha256,inputSha256,bundleRoot,creationTicks. schema is
tsj-q100-prefix-failed-process-owner-v1; ticks are actual positive decimal
strings and PID an actual positive int. Raw calls in registration order go to
calls/0000/receipt.json, calls/0000/stdout.utf8, calls/0000/stderr.utf8, etc.
receipt.json serializes the unchanged returned module object; stream files
are exactly UTF8 of its original strings. Do not invent missing receipts.

manifest.json has exact schema,status,complete,owner,captures,totalBytes,error.
schema is tsj-q100-prefix-failed-process-bundle-v1. status is
PRESERVED_FAILED_PROCESS_EVIDENCE_ONLY or FAILED_TO_PRESERVE_ALL_PROCESS_EVIDENCE.
owner is a file ref or null if writing failed. captures is an ordered list of
objects with exact ordinal,phase,receipt,stdout,stderr,status,error. ordinal is
the actual registration index; phase the actual call phase. Each file ref has
exact path,bytes,sha256, with a relative contained path. Capture status is SAVED
or WRITE_FAILED; missing refs are null, never fictional. complete is true only
after all registered records and owner refs are flushed/hash-checked. totalBytes
counts all preserved payload/owner bytes, excluding manifest to avoid self-size
cycles. error is null or the actual preservation failure description.

The failed raw bundle has a separate128MiB per-file/256MiB aggregate storage
bound. This only preserves otherwise rejected evidence: two allowed8MiB text
streams can expand up to6x in escaped JSON, still below128MiB plus small receipt
metadata. It raises no accepted operation/source/output bound and never makes
an oversized capture pass. If a larger/unwritable record cannot be fully saved,
record complete=false with the actual size/I/O error and fail acceptance; do
not truncate data and claim complete preservation. Structured owner/manifest/
host-failure summaries stay<=64KiB. A failure bundle is not a success artifact.

If success output exists, host-failure.json references the bundle using root,
manifestPath,manifestSha256,complete,writeError; it contains no large raw object.
Before-output failures retain the bundle externally without creating output.
An unexpected preservation/summary-write failure must produce a bounded
P8_PREFIX_FAILURE_EVIDENCE diagnostic with its actual error and available refs
after the normal first-failure frame; expected five early negatives still
require exactly their one frame and unchanged targets. Any retention failure,
unknown termination, nonzero unexpected exit or failed cleanup remains FAIL.
No publication until the real selected-host/reader/failure controls qualify.

## R3 correction: exact selected process receipt shape

Frozen before the corresponding reader correction and before any BASE run.
The R2 prose incorrectly named a `Data` field. The selected unchanged module
`VerifierIsolation.psm1` SHA-256
`fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c`
returns exactly these eleven fields from `Invoke-VerifierBoundedProcess`:
`FilePath,Arguments,ProcessId,ProcessStartTicks,ExitCode,Stdout,Stderr,StdoutLog,StderrLog,ProcessRoot,TerminationProven`.
Its source return object and genuine selected-host, compiler, source-auditor,
native-canary and malformed-control receipts independently confirm that shape.

Both the outer parent process and every child process must have exactly this
field set. Reject a missing or extra field, including an invented `Data` field.
Retain every existing field type, value, image/hash/argv, PID/start, exit,
termination, log-root and stream check. Do not add a synthetic field, accept an
optional legacy shape, change the process module, or rewrite old receipts.
Actual .NET exception `Exception.Data` dictionaries on failed calls remain a
separate failure-metadata channel and are unchanged. Output byte accounting
still counts each complete receipt and its separate stream files once each.
No source, execution, output, timing or cleanup bound changes in this correction.
