# Root body constructor native driver: source-only handoff

Status: SOURCE_AUTHORED_NOT_EXECUTED. This note releases only driver Source.
No import, syntax compilation, test, generation, javac, Java, DATA, journal, old
classes, actual producer, OriginalArm, Session or routing execution occurred in
this leaf assignment. No repository files, existing immutable inputs, runtime
artifacts or other workers' files were edited. No process was launched beyond
bounded source-inspection/source-authoring shell commands.

Admitted parents: source/build_body_ownership_r1.py SHA256
93c2679dd637f091c72994b6bd1def6711b9bab9f32f49c84ceee9541d31a92a and
source/run_body_ownership_r1.py SHA256
964868d26b2a7ad2dc230ab6c3d5b4e35c2ca1ad12a9ff2f54f2e8768d6eb32d.
Read initial contract CONSTRUCTOR_CONTRACT_R1.md SHA256
02804d983545b7c2853bde1f61b26c262f9234d59d4ad31c6a214d657a22018b.
Root preserved that raw contract and clarified the shorthand meter type to
CohortTerminalTelemetryStudy.SetupMeter; final contract SHA256
b544bb8e9e41277b1593307a751231f562f8ac0669dc72822af7688eeb128789.
The root constructor contract prices success as 14 units/13 entries/1 chunk.
The independent literal/header checks and construction assertions belong to the
frozen canary; these drivers authenticate its exact Source and execution.

## Immutable inputs and source freeze

Both drivers require SOURCE_INPUT_R1.json SHA256
b425e75b68349611b7471c98f9562b47adc4b05209faeaec570d17dccb4fef7c,
scope CURRENT_THIRTEEN_KEY_ROOT_BODY_SOURCE_AUDIT, nine exact ordered rows;
SOURCE_INPUT_R2.json SHA256
a03674e7d7f9d9db2491b9ac7bca5899da7eb937f1cade24d6a600472152bfe2,
scope ROOT_CONSTRUCTOR_NATIVE_AND_ORACLE_SOURCE, seven exact ordered rows.
Both have base 1b22c5edb55b0009ad03037370a5fef72a87112b,
status PINNED_SOURCE_ONLY_NO_DATA and genuineEnvelope UNSPENT_GATED.
Each manifest and every admitted member is reauthenticated before/after operations.

Root must author FROZEN_SOURCE_R1.json before build. It is an object with
status ROOT_ACCEPTED_FINAL_SOURCE_FOR_SYNTHETIC_GATES and files, an array of
exactly nine unique {"role":string,"sha256":lowercase64hex} rows. Exact roles:

- source/CURRENT_STUDY.java (9ac45b37...)
- source/SHARED_OWNER.java (99993fc4...)
- source/NATIVE_PRIVATE_PCB.java (7bf4ab57...)
- source/CURRENT_RECEIPT_BINDING.java (1df7e060...)
- source/CurrentBodyOwnership.java (8a7a5b0d...)
- source/CurrentV2CodecOracle.java (644cf35b...)
- emitted/CurrentV2CanonicalCodec.java (Root selects exact fresh emitted hash)
- RootBodyConstructorCanary.java (Root selects exact accepted canary hash)
- build_root_body_r1.py (Root selects exact released driver hash)

The first six are hard-pinned in both drivers. The baseline codec 76fb7bbc...
remains pinned immutable R1 Source but is never compiled. Root owns emitter CLI
positive/negative gates and acceptance of the emitted codec before native build.
The runtime driver and CASE_MANIFEST belong to runtime control, not this freeze.

## Exact fresh compilation closure

861 explicit sources = 848 F production Java files, excluding
PcbLayerRoutingPrototype.java and PhysicalSpecificationDeveloperVerifier.java;
three staged exact owners (FailureCohortRoutingStudy.java from SHARED_OWNER,
PcbLayerRoutingPrototype.java from NATIVE_PRIVATE_PCB,
CohortTerminalTelemetryStudy.java from CURRENT_STUDY);
the same five pinned F authored-flat-work public denyfixture sources;
ReceiptContentBinding.java staged exactly from CURRENT_RECEIPT_BINDING;
source/CurrentBodyOwnership.java; emitted/CurrentV2CanonicalCodec.java;
RootBodyConstructorCanary.java; source/CurrentV2CodecOracle.java.
Basenames and full paths must be unique. No inferred source discovery is allowed
for missing dependencies: sourcepath is a fresh empty directory.

The build creates native-build-r1, classes and staging exclusively; it never
reuses or overwrites an attempt. The actual javac argv uses JDK8 -source 7
-target 7 -encoding UTF-8, only fresh classes and the two pinned GWT 2.7.0 jars
on classpath, the empty sourcepath and a complete explicit argument file.
Build input records source, dependency, six-tool and driver hashes, source list,
actual-argv location and caps. Compile process phase is
native-root-body-fresh-jdk8-compile-r1, limit 60000 ms.
Successful status is PASS_FRESH_NATIVE_ROOT_BODY_COMPILE_ONLY and scope
FRESH_NATIVE_ROOT_BODY_SYNTHETIC_ONLY. Native runtime remains NOT_RUN.

Every produced class is bounded, inventoried, SHA256 authenticated and major51.
Required owners include current meter/setup, codec, receipt, guard, constructor
canary and independent oracle. Reject producer Study/Mode/ArmRun, OriginalArm,
CurrentV2InputSetup and all its nested classes (including Factory/State),
CurrentAssemblyMetadata and CohortTerminalTelemetryInputV2.
The pinned public input denyfixture is intentionally allowed. The detached
ArmResult carrier must be exactly 811 bytes, major51, SHA256
7d9cbb2c16132df342829c8174eb8565e41d33efb45ae120e0f011f4f1fc6ca0;
its presence does not license instantiation or genuine capture.

## Runtime control and case manifest schemas

RUNTIME_CONTROL_R1.json must have exactly:
status ROOT_ACCEPTED_RUNTIME_CONTROL_ONLY;
originalSourceFreezeSha256 equal to the exact frozen manifest digest;
unchangedCompiledSources true; files containing exactly two unique
{"role":string,"sha256":lowercase64hex} rows for run_root_body_r1.py and
CASE_MANIFEST_R1.json. Root creates/accepts it after final Source release.

CASE_MANIFEST_R1.json has exactly status ROOT_ACCEPTED_NATIVE_CASE_MANIFEST
and cases, ordered [smoke,suite]. Each case row has role; class
com.lushprojects.circuitjs1.client.RootBodyConstructorCanary; args ["SMOKE"] or
["SUITE"]; status PASS; caseShape records; names equal to the exact ordered
lists below. Optional expectedScalars is a string-keyed scalar object; omit it
for this protocol. No fixture directory or extra Java argument is accepted.

SMOKE names:
root_smoke_actual_meter

SUITE names, in order:
root_order_headers_null_slots
root_fresh_borrowed_inputs
root_trap_children_unobserved
root_null_meter_guard
root_copy_allowance_12_reject
root_copy_admission_units_13_before_allocation_fee
root_closed_meter_normal_start
root_unit_group_roll
root_group_640_reject
root_expired_operation_reject
root_expired_group_reject
root_staged_bad_headers_deferred
root_staged_null_children_deferred
root_staged_incomplete_slots_reject
root_completed_synthetic_codec_oracle

Canary stdout is exactly one compact ASCII JSON line, LF or CRLF terminated:
{mode:"SMOKE"|"SUITE",status:"PASS",names:[...],cases:[...]}.
Each ordered record has exactly these scalar fields:
name,status,units,copies,chunks,groups,observedItems,operationNanos,cleanupNanos,
cleanupDisposition,error,cleanupError.
status must be PASS, error and cleanupError empty, all seven counter/timing
fields nonnegative integers. Counter deltas are measured after fixture baseline
and before cleanup. Operation and cleanup durations remain separate.
Every observedItems must be zero. groups must be one only for
root_copy_admission_units_13_before_allocation_fee and root_unit_group_roll,
and zero otherwise. Smoke units/copies/chunks must be exactly 14/13/1.

Exact successful cleanupDisposition:
NONE for root_null_meter_guard;
ALREADY_CLOSED for root_copy_allowance_12_reject,
root_copy_admission_units_13_before_allocation_fee and root_group_640_reject;
EXPECTED_EXPIRED_METER_DISCARDED for root_expired_operation_reject and
root_expired_group_reject; CLOSED for every other case.
A cleanup failure never creates a success gate.

Root invokes selected pinned Python isolated, bytecode disabled, assertions on:
python -I -B build_root_body_r1.py
python -I -B run_root_body_r1.py --case smoke
python -I -B run_root_body_r1.py --case suite

Each JVM runs -ea -Xmx512m with only fresh classes and pinned jars, 60000 ms.
Smoke process phase is native-root-body-smoke-r1; suite is
native-root-body-suite-r1. Separate native-run-smoke-r1 and native-run-suite-r1
directories contain input.json and, only after all checks succeed, gate.json.
Before starting suite, the runner authenticates the actual smoke request,
process/cleanup proof, exact stdout/stderr, accepted summary and all source,
build, class and control digests. It repeats that audit after suite.
All source/tool/jar/class pins are rechecked around every JVM and failure path.
Compile stdout/stderr, actual request argv and process receipts are audited.

## Preserved limits and remaining proof

The same six selected tools, root helper 224cf971... and nested helper 4f414bc2...,
JDK8/GWT jars, environment injection rejection, stable reads, safe paths,
class count10000/bytes268435456, Source cap4MiB, report cap1MiB, exact process
wrapper and isolated cleanup evidence remain. Global logical caps remain
90s/640groups/5s/32768units/copy1MiB/chunk4096/depth64/visits1m.
No cap, golden, expected error or oracle was weakened. Runtime/host qualification,
metadata/R7/full-result composition, actual producer, electrical/population,
GWT/player flows and playable Q100 are not granted by synthetic gates.
actualPriorDataReads/oldDataClassReads/protectedHeldoutReads/nativeProducerCalls=0,
normalHostAcceptance NONE, genuineEnvelope UNSPENT_GATED.
Full30 excluded; HARD/PSYCHOTIC unavailable/unrun; old normal20 historical,
no reread; protected unread UNCERTIFIED; playable100=0; beta HELD.

Source review used exact admitted-file hashes, bounded source reads, textual
adaptation and a full source diff against the two admitted driver parents.
NOT RUN: importing or parsing Python with an interpreter, syntax compilation,
test execution, emitter, javac, native smoke/suite. Root must review final actual
inventory and freeze exact final hashes before any of those operations. The
released files must be preserved as raw bytes before a later correction.
