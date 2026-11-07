# P9 selected-host contract R1

Status: FROZEN before producer work; no implementation or runtime PASS.
Every schema below is closed: listed keys only, no optional legacy variants or
implicit unchecked references.

## Entry and owners

Selected PS5.1 runs `run-provenance.ps1` with mandatory named arguments
RepositoryRoot, InputPinsPath, InputPinsSha256, OutputRoot, JavaHome, PythonExe,
Mode. Mode is CANARY or SUITE. `run-controls.ps1` takes those same arguments and
owns five early host negatives, the actual positive host call, independent reader,
five reader CLI negatives and the SUITE semantic controls. All child processes use
the unchanged pinned `Invoke-VerifierBoundedProcess`; Python always uses -I -B.
No old executing host is dot-sourced. Host and controller self-authenticate before
mutation; every returned process receipt is preserved before its validation.

CANARY process role order is exactly:
preflight, compiler, preNative, p6Canary, p8Canary, p9Canary, badMode, badUuid,
badInputSha, nonemptyData, nonemptyP9, postSource.
SUITE inserts nullMode, observeMode, tapThrow, callbackThrow, reasonThrow immediately
before postSource. JSON object role order is checked through the retained parsed
member sequence; repeated role names are invalid. Actual operation order is
NULL, OBSERVE, TAP_THROW, CALLBACK_THROW, REASON_THROW. CANARY has no operations.
The separate `modeOrder` native-invocation array is exactly
[P6_CANARY,P8_CANARY,P9_CANARY,P9_BAD_MODE,P9_BAD_UUID,P9_BAD_INPUT_SHA,
P9_NONEMPTY_DATA,P9_NONEMPTY_P9] in CANARY. SUITE appends exactly
[NULL,OBSERVE,TAP_THROW,CALLBACK_THROW,REASON_THROW]. It never contains process
role keys or source-audit/compiler roles. Host-result, index and footer bind this
same array. Process role keys remain the separate ordered contract above.

Audit before compile, before native, and after native. One exact 1085-source JDK8
compile, with inherited exclusions, bridge copies, GWT jars, empty sourcepath and
source/target7. Source hashes and inherited ownership are the source contract.
No compilation wildcard, implicit source discovery or mutable old manifest.

## Genuine native calls

P9 normal wrapper takes seven positional arguments:
MODE RUN_UUID P9_INPUT_SHA P8_WIRE_SHA P9_INVENTORY_SHA EMPTY_P8_DATA_DIR EMPTY_P9_DIR.
NULL delegates P8 NULL; OBSERVE and TAP_THROW delegate genuine P8 OBSERVE. P9
wrapperMode and actual p8Mode are always distinct named fields, even if equal.
The normal wrapper preserves the original P8 sole stdout marker and seven-file
subtree. A P8 marker alone cannot accept P9: its sibling index remains provisional
until all P9 final guards, actual native exit0 and independent consumption.

CALLBACK_THROW invokes genuine original CanonicalPrefixStageStudy with five
application arguments CALLBACK_THROW,runId,P9_INPUT_SHA,P8_WIRE_SHA,dataDir.
REASON_THROW invokes genuine CanonicalGeometricPrefixStudy with six application
arguments REASON_THROW,runId,P9_INPUT_SHA,P8_WIRE_SHA,P9_INVENTORY_SHA,dataDir.
Neither registers a P9 tap or creates a P9 output directory. Their target capture
is absent, because the original deliberate throw occurs at parent0. Require actual
exit1 and every existing exception/partial-prefix/source/effect/cleanup predicate.

The normal P9 wrapper/preflight and canary marker/schema/API, exact intentional
tap containment and native index are defined by NATIVE_WORK_R1. It must never catch
an unexpected budget, source, allocation, protocol, I/O or cleanup failure as an
expected sentinel. No process exit or cleanup fact is inferred from marker text.

## Exact retained success tree

All paths here are relative POSIX paths under one fresh external OS-temp output.
Every leaf is CREATE_NEW. Its parent chain is canonical and has no reparse point.

Common 11 leaves:
host-launch-intent.json, scratch-intent.json, compiler-launch-intent.json,
compiler-sources.txt, compiler-process.json, compiler.stdout, compiler.stderr,
bridge-copies.json, host-result.json, suite-index.json, suite-footer.json.

For each stem source-preflight, source-prenative, source-post retain exactly
STEM-process.json, STEM.stdout, STEM.stderr (9 leaves).
For each stem p6-canary,p8-canary,p9-canary,p9-bad-mode,p9-bad-uuid,
p9-bad-input-sha,p9-nonempty-data,p9-nonempty-p9 retain STEM-launch-intent.json,
STEM-process.json, STEM.stdout, STEM.stderr (32 leaves).
The two authored control leaves are
controls/P9_NONEMPTY_DATA/data/sentinel.txt and
controls/P9_NONEMPTY_P9/p9/sentinel.txt, each with bytes
`P9_PREEXISTING_DATA_DO_NOT_CHANGE\n` (ASCII, one LF).
Common total is 54 leaves; CANARY success has exactly54.

SUITE adds runs/MODE/launch-intent.json, runs/MODE/process.json,
runs/MODE.stdout, runs/MODE.stderr for all five wrapper modes (20 leaves).
Under runs/NULL/data, runs/OBSERVE/data, runs/TAP_THROW/data retain exactly
op-1.jsonl, fixed-prefix.jsonl, prefix-blockers.json, prefix-stages.json,
canonical-stage-index.json, prefix-geometric-reasons.jsonl,
canonical-geometric-prefix-index.json (21 leaves).
Under runs/CALLBACK_THROW/data retain op-1.jsonl only.
Under runs/REASON_THROW/data retain op-1.jsonl, prefix-blockers.json,
prefix-stages.json, prefix-geometric-reasons.jsonl (five throw leaves total).
Under runs/NULL/p9, runs/OBSERVE/p9, runs/TAP_THROW/p9 retain exactly
reservation-operands.jsonl and reservation-index.json (six leaves).
SUITE success has exactly106 leaves. The count never substitutes for exact names.

Allowed directories are the union of all listed leaf ancestors plus these exact
empty control directories: controls/P9_BAD_MODE/data, controls/P9_BAD_MODE/p9,
controls/P9_BAD_UUID/data, controls/P9_BAD_UUID/p9,
controls/P9_BAD_INPUT_SHA/data, controls/P9_BAD_INPUT_SHA/p9,
controls/P9_NONEMPTY_DATA/p9, controls/P9_NONEMPTY_P9/data.
Their corresponding controls/ parents are admitted
only as ancestors. No P9 directory exists for original throw modes. Empty normal
mode data/P9 directories become populated; no other empty directory is admitted.
No extras, aliases, missing paths, streams or trailing JSONL records are allowed.

## Scalar and stream conventions

JSON duplicate keys, BOM, nonfinite numbers, bool-as-integer and unknown fields
are rejected. Digests are lowercase64hex. UUIDs are canonical lowercase version4
strings. Paths are canonical absolute local leaves in private receipts and safe
relative names in artifact references. Integers are exact; all measured milliseconds
are finite nonnegative JSON numbers, never numeric strings or booleans. Positive
elapsed times are strictly greater than0. Native/file protocols use compact ASCII
JSON and one LF. PowerShell marker CRLF is explicitly allowed; native markers keep
their original framing. Returned Stdout/Stderr are decoded strings from the actual
module. Retained .stdout/.stderr are their exact UTF8 encodings, not a claim about
original undecoded pipe bytes. Structured line/index bound is65536 bytes; compiler
source list and inventory each1048576. Every complete JSON file has one final LF.

Raw process receipt keys are exactly FilePath,Arguments,ProcessId,ProcessStartTicks,
ExitCode,Stdout,Stderr,StdoutLog,StderrLog,ProcessRoot,TerminationProven, with types
and actual API meaning unchanged from the pinned VerifierIsolation module.
No Data field. Arguments is the exact string array. TerminationProven must be true;
the actual owned root/log paths must be absent after the call. If invocation throws
without returning, preserve the attempted call, exception and Exception.Data,
process=null/terminationProven=false. Such a record cannot accept a role.

Parent receipt keys exactly schema,elapsedMs,process. Schema is
tsj-q100-reservation-provenance-parent-v1; elapsedMs is the full bounded positive
host call through actual return, <=1800000. It includes the final host tail.
Process descriptors in host.processes have receipt,stdout,stderr,wallMs only;
paths bind the exact stems above and wallMs is actual complete bounded-call wall.

Every launch intent has schema,role,owner,executable,executableSha256,arguments,
limitMs,inputSha256,sourceSha256,outputRoot. schema is
tsj-q100-reservation-provenance-launch-v1; owner has pid,startTicks,executable,
executableSha256. Host launch additionally has mode,script,scriptSha256,
outputCreationTicks,sourceParent,p8PrerequisiteSha256. Child launch intents are
written before starting the child. Actual process receipts, rather than intents,
certify image/PID/start/argv/exit. The host records outputCreationTicks immediately
after creation; the reader checks actual Win32 FILETIME against those .NET ticks.
Scratch intent has schema,status,path,owner,suiteId; schema
tsj-q100-reservation-provenance-scratch-v1 and status=INTENT_NOT_CREATION_PROOF.
It is written before creation and makes no claim about nonexistent creation ticks.
After creation, actual ticks are recorded in host.cleanup.scratchCreationTicks;
the pinned host revalidates that exact identity before removal. Compiler intent
additionally has sourceCount,sourceListBytes,
sourceListSha256. Bridge-copy receipt retains the exact inherited three names,
source/destination lengths and hashes, and verified before/after flags.

## Host result and noncircular byte closure

host-result exact keys: schema,status,mode,suiteId,inputSha256,sourceSha256,
sourceParent,p8PrerequisiteSha256,wireSha256,modeOrder,host,processes,sourceChecks,
compiler,bridgeCopies,canaries,operations,timing,cleanup,limits,outputClosure,
layoutQualification. schema=tsj-q100-reservation-provenance-host-v1. Status is
HOST_CANARY_COMPLETE_READER_PENDING or HOST_COMPLETE_READER_PENDING by mode.
modeOrder is the exact native-invocation array above. layoutQualification is always false.
host has pid,startTicks,executable,executableSha256,script,scriptSha256 only.
sourceChecks has preflight,preNative,postSource with the exact auditor summaries
from SOURCE_INPUTS_R1, bound to the actual respective process and pins/inventory.
compiler has sourceCount,sourceList,sourceListBytes,sourceListSha256; sourceCount1085.
bridgeCopies has receipt,count,verifiedAfterCompile,verifiedBeforeNative,
verifiedAfterNative; count3 and flags true. The referenced receipt retains the
existing genuine bridge-copy row schema from the pinned P8 host, not invented
new bridge identities. canaries has p6,p8,p9,preflight; the first two retain the
original exact validated P6/P8 payloads, p9 and preflight are NATIVE_WORK_R1.

Each operation has wrapperMode,p8Mode,runId,disposition,processKey,p8DataDirectory,
p9Directory,workNanos,groups,observationUnits,p9Index. workNanos is the validated
P8 marker value (null only for original CALLBACK_THROW); groups is actual raw
receipt GROUP count before the existing3 wrapper groups. observationUnits is the
P8 combined total for the three normal wrappers and REASON_THROW. The original
direct P6 CALLBACK_THROW has no P8 index: bind observationUnits to its genuine
P6 exception marker stages.totals.totalObservationUnits, validated by the unchanged
P6 exception kernel. Its priorObservationUnits is0; its actual total need not be0.
The qualified P8 host/reader implement this distinction; older HOST_R1 prose
stating a zero total is superseded by that actual source and qualified evidence.
P9 additional accounting is separately bound through p9Index.
disposition is COMPLETE_NULL_CONTROL_ONLY, COMPLETE_RESERVATION_OBSERVATION_ONLY,
EXPECTED_CONTAINED_TAP_THROW_ONLY, EXPECTED_CALLBACK_THROW_ONLY or
EXPECTED_REASON_THROW_ONLY by wrapperMode. p9Directory/p9Index are explicit null
only for the original two throws. All other refs have path,bytes,sha256 exactly.

suite-index has schema,status,mode,suiteId,inputSha256,sourceSha256,wireSha256,
modeOrder,artifactCount,aggregateBytes,artifacts,directories,layoutQualification.
schema=tsj-q100-reservation-provenance-suite-index-v1; status=HOST_INDEX_COMPLETE.
artifacts is the exact case-sensitive Ordinal sorted success leaves excluding
suite-index.json,suite-footer.json,host-result.json. Rows have
path,role,operation,bytes,sha256. role is HOST_INTENT,PROCESS_RECEIPT,STDOUT,STDERR,
COMPILER_INPUT,BRIDGE_COPIES,CONTROL_PAYLOAD,NATIVE_PAYLOAD or NATIVE_INDEX by the
explicit names above. operation is the wrapper mode for every runs/MODE/... and
runs/MODE.stdout/.stderr path; every other file is HOST. Each physical leaf is
counted once, including native-created files not written through host helpers.
directories is the exact sorted allowlist. artifactCount and aggregateBytes are
the exact row count and byte sum.

suite-footer has schema,status,mode,suiteId,inputSha256,sourceSha256,wireSha256,
modeOrder,suiteIndex,artifactCount,aggregateBytes,directoryCount,layoutQualification.
schema=tsj-q100-reservation-provenance-suite-footer-v1; status=HOST_OUTPUT_CLOSED;
suiteIndex is a path/bytes/sha256 ref. The other totals bind the index rows.
outputClosure has files,directories,aggregateBytes,hostResultExcluded; true for
hostResultExcluded. Its rows have name,bytes,sha256 and cover every success leaf
except host-result.json, including index/footer. After writing host-result, rehash
every closure row and independently add host-result bytes for final partitions.
No index or result hashes itself. The actual parent marker binds all three files.

Host stdout is exactly P9_RESERVATION_HOST_COMPLETE plus one space and compact
JSON plus LF or CRLF; stderr empty. Payload keys schema,status,mode,suiteId,
inputSha256,sourceSha256,wireSha256,hostResult,suiteIndex,suiteFooter,
layoutQualification. schema=tsj-q100-reservation-provenance-host-complete-v1.
Status and identities equal host-result; three refs bind actual returned bytes.
Any final failed guard or nonzero exit invalidates an earlier marker.

## Time, quota and cleanup

The native final work guard uses the complete P9 monotonic elapsed minus only
the genuine P6 index entry cleanup.elapsedNanos, independently authenticated by
the existing inner reader. It therefore covers the P8 marker tail and every new
final write without crediting Arm cleanup. Its reported numeric cutoff remains
provisional until the final post-I/O guard and actual successful process exit.

timing has nativePhaseStartOffsetMs,cleanupStartOffsetMs,nativePhaseElapsedMs,
childWallSumMs,workCreditsMs,nativePhaseChargeMs,nativePhaseOverheadMs,
hostElapsedBeforeFinalWriteMs. native phase includes all canary/preflight/mode
child calls and their between-call overhead. Sum complete child walls once.
Only NULL and OBSERVE can receive work credit from the native contract;
TAP_THROW and both original throws receive zero. No Arm/outside cleanup or
P8 tail/final-index/post-cutoff time is credited. Require credits<=child walls,
nativePhaseElapsed=cleanupStart-nativeStart,
nativePhaseOverhead=nativePhaseElapsed-childWallSum>=0,
nativePhaseCharge=nativePhaseElapsed-workCredits>=0. Final cleanup upper bound is
nativePhaseCharge+(actual parent elapsed-cleanupStartOffsetMs)<=120000.
Host self-report cannot substitute for actual parent wall or process completion.

cleanup has scratchPath,scratchCreationTicks,scratchRemoved,boundMs,formula;
boundMs120000 and formula exactly
nativePhaseChargeMs+(parentFullHostElapsedMs-cleanupStartOffsetMs).
Scratch removal uses exact identity/no-reparse/containment validation, one shell,
and only owned resources; uncertain unrelated resources remain untouched.
Source and output closure are checked after native and again by independent reader;
all actual child roots/logs and the owned compiler scratch must be absent.

limits has compileMs60000,auditMs60000,nativeMs480000,readerMs180000,
outerMs1800000,cleanupMs120000,workNanos90000000000,groups640,
groupNanos5000000000,observationUnits32768,cloneEntries1048576,chunkEntries4096,
expansions1000000,holes48,flips4,operationBytes8388608,hostEvidenceBytes8388608,
suiteBytes41943040,lineBytes65536,compilerListBytes1048576,inventoryBytes1048576.
Every operation partition includes its launch/process/stream/P8/P9 artifacts.
All other bytes, including canaries/control evidence/index/footer/host-result,
form the host partition. Each operation<=8MiB, host<=8MiB, physical sum<=40MiB.
Five operation maxima plus host do not grant48MiB. The native inclusive8MiB
quota separately includes its P8/P9 payloads and actual emitted streams.

Early rejection frame is exactly one stderr line
P9_RESERVATION_HOST_REJECT plus space and JSON with keys code,phase,outputCreated,
scratchCreated,compilerInvoked,nativeInvoked,cleanupError. Preserve actual first
failure. Early controls require all four flags false and cleanupError null.
Later failure evidence is retained under a separate unique owned failure root;
full returned receipts/decoded streams are retained before a bounded summary.
An unreturned receipt or cleanup failure is FAIL, never a synthesized PASS.

Unexpected failure retention ports the existing P8 retention rules with only P9
namespaces changed; it does not reuse or fabricate an old P8 success envelope.
Register every actual returned object in memory before validation. Preserve the
original first error even if cleanup or evidence writing also fails. Allocate
one fresh external OS-temp root tsj-p9-reservation-failure-r1-<32lowerhex>, disjoint
from success output, all input roots and compiler scratch. Verify canonical
containment, no reparse point, actual owner PID/start/image, and creation identity.
This retained failure root is evidence, not scratch to delete automatically.

owner.json keys: schema,hostPid,hostStartTicks,hostExecutable,hostScriptSha256,
inputSha256,bundleRoot,creationTicks; schema
tsj-q100-reservation-failed-process-owner-v1. Ticks are actual positive decimal
strings; PID a positive integer. Every returned call in registration order has
calls/0000/receipt.json, calls/0000/stdout.utf8, calls/0000/stderr.utf8 etc. The raw
11-field object and UTF8 of its returned decoded strings are unchanged. A failed
invocation with no returned receipt retains its attempted call/exception separately
and is never represented by an invented object in this returned-call sequence.

manifest.json keys: schema,status,complete,owner,captures,totalBytes,error; schema
tsj-q100-reservation-failed-process-bundle-v1. Status is
PRESERVED_FAILED_PROCESS_EVIDENCE_ONLY or FAILED_TO_PRESERVE_ALL_PROCESS_EVIDENCE.
owner is an exact path/bytes/sha256 ref or null after a failed write. captures is
ordered rows ordinal,phase,receipt,stdout,stderr,status,error; ordinal is actual
registration index, phase the actual call phase, refs relative contained
path/bytes/sha256 objects or null if not saved. Status is SAVED or WRITE_FAILED.
complete=true only if every registered object, stream and owner ref was flushed
and hash-checked. totalBytes is payload+owner bytes excluding manifest. Error is
null or the actual failure description, never a silently dropped write failure.

Preserve the inherited failure-only128MiB/file and256MiB/bundle caps. These do not
raise any accepted8MiB/40MiB quota; escaping large returned receipts is retention
of rejected evidence only. Oversized/unwritable records produce complete=false
with actual size/I/O error, never truncation advertised as complete. Structured
owner/manifest/host-failure summaries stay<=65536 bytes. If success output already
exists, host-failure.json has schema,status,phase,code,cleanupError,failureEvidence;
schema=tsj-q100-reservation-host-failure-v1, status=FAIL. failureEvidence has exactly
root,manifestPath,manifestSha256,complete,writeError, with actual refs or explicit
nulls when preservation failed. No large raw object enters this summary. Before-
output failures retain the bundle externally without creating the success root.
On preservation/summary-write failure emit a bounded additional stderr diagnostic
P9_RESERVATION_FAILURE_EVIDENCE with the actual error and available refs after the
original rejection frame. The five expected early negatives still require exactly
their single rejection frame, unchanged targets and no compiler/native invocation.
Unknown termination, failed cleanup or incomplete retention remains FAIL.

## Scope

One exact authored BASE slot only. No new seed, query/order/policy/epoch/budget,
predicate replay, edge opening, constraint relaxation, broader prefix or adoption.
Full30 owner-excluded; HARD/PSYCHOTIC unavailable/unrun, protected bodies unread
here, historical unread UNCERTIFIED. Playable100=0, betaHELD. No production,
GWT, Player, electrical or population qualification is renewed by this diagnostic.
