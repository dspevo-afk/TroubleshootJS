# Body ownership native driver source release R1

Scope: source-only adaptation of admitted `source/build_qualified_metadata_r1.py`
SHA256 `098b8cd45b435beb27c96ac7533ae131f18f40a8a9abf64c5c8d999d786e4f00`
and `source/run_qualified_metadata_r1.py`
SHA256 `902af48f6b1f15f453209c0b57f6a9a92178370455ebad564cd274c31b019bf0`.
These source files were read and adapted, never executed/imported/compiled.
The root owns acceptance, freezes and all later execution.

## Source inputs and freeze

Both drivers require exact immutable source manifests, duplicate-free JSON and
canonical non-reparse regular file paths with stable handle/path snapshots:

- SOURCE_INPUT_R1.json SHA256
  `42ac5fd96c609dbc3caeb65856243519f2dd35ed065fef85f8911b2c62db5997`,
  nine exact source names, audit base `acb4dd5d7f309c7bc4f461c124974c0b254b5d90`,
  scope CURRENT_RESULT_BODY_OWNERSHIP_SOURCE_AUDIT.
- SOURCE_INPUT_R2.json SHA256
  `b943fb60a54dc651f75b6d80e15bb0aee5e3e93e3912f863f30d4444deb5e94b`,
  four exact source names, native base `40520885d2e93e078e77958b5695a9f05aead620`,
  scope CURRENT_BODY_OWNERSHIP_NATIVE_SOURCE.

Both require status PINNED_SOURCE_ONLY_NO_DATA and envelope UNSPENT_GATED.
Manifest rows are size/hash authenticated and remain source inputs, not permission
to execute any Python parent or compile obsolete component sources.

FROZEN_SOURCE_R1.json uses the admitted `status`/`files` schema, root status
ROOT_ACCEPTED_FINAL_SOURCE_FOR_SYNTHETIC_GATES and exactly these eight unique roles:

1. emitted/CohortTerminalTelemetryStudy.java
2. source/SHARED_OWNER.java
3. source/NATIVE_PRIVATE_PCB.java
4. source/CURRENT_RECEIPT_BINDING.java
5. source/CurrentV2CanonicalCodec.java
6. CurrentBodyOwnership.java
7. BodyOwnershipCanary.java
8. build_body_ownership_r1.py

Shared/PCB/receipt/codec retain their admitted fixed digests. Root supplies and
accepts emitted Study/guard/canary/build digests in that freeze. The original Study
is authenticated by R1 but never compiled; the emitted Study is staged byte-exact.

## Fresh build contract

Explicit closure: 848 F production Java sources excluding PCB and developer
verifier + three staged Shared/PCB/emitted Study owners + five unchanged F public
deny/flat-work fixtures + codec/staged receipt/guard/canary = 860 unique names and
paths. No V2InputSetup, AssemblyMetadata, InputV2 Factory is compiled. The generic
public legacy input remains only its pinned deny fixture.

The admitted selected JDK8, six tools, root helper 224cf971 and nested helper
4f414bc2, two exact GWT jars and environment denials remain. Fresh exclusive
native-build-r1 directories, empty sourcepath, explicit compiler argfile and only
fresh classes plus the two GWT jars remain mandatory. Source cap 4 MiB, class count
cap 10000, aggregate class cap 256 MiB, target class major 51 and compile operation
limit 60000 ms are retained. Count/bytes are observed, never forecast.

All source/manifest/freeze/tool/jar pins reauthenticate before and after compile;
the complete fresh class inventory is authenticated and traversed again. Guard,
canary, Study and actual Meter classes are required. Actual producer Study/Mode/
ArmRun and OriginalArm classes are absent, as are obsolete component classes and
nested classes. The uninstantiated ArmResult carrier is permitted only at 811
bytes, major51, SHA256
`7d9cbb2c16132df342829c8174eb8565e41d33efb45ae120e0f011f4f1fc6ca0`.

Outputs retain the admitted NATIVE_BUILD_INPUT_R1, NATIVE_CLASS_INVENTORY_R1,
BUILD_MANIFEST_R1 and NATIVE_COMPILE_GATE_R1 filenames. Build status is exactly
PASS_FRESH_NATIVE_BODY_OWNERSHIP_COMPILE_ONLY; scope is
FRESH_NATIVE_BODY_OWNERSHIP_SYNTHETIC_ONLY. Runtime is NOT_RUN. Both input manifest
hashes are recorded (`sourceInputSha256`, `nativeSourceInputSha256`). No old DATA
classes, prior DATA, protected heldout or producer invocation is admitted.

## Runtime control and protocol

RUNTIME_CONTROL_R1.json retains the exact admitted schema:
`status`, `originalSourceFreezeSha256`, `unchangedCompiledSources`, `files`.
Status ROOT_ACCEPTED_RUNTIME_CONTROL_ONLY, unchangedCompiledSources true; exactly
two role/hash rows: run_body_ownership_r1.py and CASE_MANIFEST_R1.json.

CASE_MANIFEST_R1.json retains status ROOT_ACCEPTED_NATIVE_CASE_MANIFEST and two
ordered case rows smoke/suite. Each row has role/class/args/status/names/caseShape,
optionally expectedScalars. The class is
com.lushprojects.circuitjs1.client.BodyOwnershipCanary, args exactly [SMOKE] or
[SUITE], status PASS, caseShape records. Root freezes exact ordered names; no
assertion total is guessed. The current source protocol has nine smoke names and
21 suite names, independently accepted by root before running.

Runner CLI is --case smoke or --case suite, under isolated Python with bytecode
writing and optimization disabled. Java argv is -ea -Xmx512m -cp FRESH_CLASSES;
GWT_USER;GWT_DEV BodyOwnershipCanary SMOKE|SUITE, with exactly one application mode
argument. There is no fixture-directory option or native OS-file fixture.
Each runtime operation limit is 60000 ms. Suite requires the authenticated smoke
success gate, exact input hashes and JVM argv, audited process and exact stdout
before its own JVM starts, and reauthenticates that prerequisite afterwards.

Stdout must be exactly one canonical compact ASCII JSON line, optionally CRLF.
Top-level exact fields: mode/status/names/cases plus root expectedScalars, if any.
Each case has exactly name/status/units/copies/chunks/groups/observedItems/
operationNanos/cleanupNanos/cleanupDisposition/error/cleanupError. All records must
match frozen ordered names and PASS; numeric fields must be nonnegative integers,
errors empty strings. cleanupDisposition is CLOSED except group-expiry, which
must be DISCARDED_EXPECTED_REJECTION. Operation and cleanup times stay distinct.
Unexpected records/keys/errors, stderr, process/request drift, timeout, nonzero
exit or cleanup failure do not receive a success gate. Compile/JVM actual argv,
process ownership/audit and complete class/source/tool/jar reauthentication retain
the admitted wrapper mechanisms. Runtime artifacts stay in native-run-smoke-r1
and native-run-suite-r1 plus their separate owned process receipt directories.

## Authoring evidence and limits

Manual source/diff inspection and SHA256 identity checks only. No build, test,
import, AST/bytecode compilation, emitter generation, journal, producer, routing,
Original/Session, real envelope or DATA access was performed by this worker.
Only build_body_ownership_r1.py, run_body_ownership_r1.py and this note are owned.
No repository files, staging, commits, pushes, mail or persistent processes.
Root must authenticate/freeze final emitted Study, guard, canary and driver sources
and accept CASE_MANIFEST/RUNTIME_CONTROL before actual native gates.

This source release supplies no runtime PASS or whole-result/host/playable claim.
Genuine envelope remains UNSPENT_GATED; normalHostAcceptance NONE. All contract
limits and excluded/protected populations in OWNERSHIP_CONTRACT_R1.md remain.

## Released source identities and supersession

Final source-only build release: 23396 bytes, SHA256
`93c2679dd637f091c72994b6bd1def6711b9bab9f32f49c84ceee9541d31a92a`.
Final source-only runner release: 37979 bytes, SHA256
`964868d26b2a7ad2dc230ab6c3d5b4e35c2ca1ad12a9ff2f54f2e8768d6eb32d`.
Protocol reconciled with independently released BodyOwnershipCanary.java,
25891 bytes, SHA256
`b0a42dee7d1281aad913e35b806b98414064898332479237e203f288c4705b34`.

Earlier runner release SHA256
`cdbdbd53130582c13bac5e37a0db67b653f7e90e3b7625d07dd5b7d64ddf324b`
was explicitly withdrawn before root execution/freeze. Its raw bytes were not
retained before the fix; this is a metadata-only supersession record, not a
reconstructed source artifact. Its byte length was not independently recorded.
The fix removes the empty smoke stderr file from a generic nonempty source-pin
list; explicit authenticated empty stderr checks remain in the smoke prerequisite.
No old release was executed by this worker. No syntax parser/build/test ran.
The no-index whitespace comparison printed no whitespace diagnostics; its exit1
indicates different source files and is not represented as a runtime/test PASS.
