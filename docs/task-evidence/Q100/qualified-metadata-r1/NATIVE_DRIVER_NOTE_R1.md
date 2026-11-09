# Qualified metadata native drivers: Source handoff

These drivers are Source authoring only. No candidate import, syntax execution,
generation, compilation, native call, DATA/journal/class read or test has been
performed by their author. Only `build_qualified_metadata_r1.py`,
`run_qualified_metadata_r1.py` and this note belong to this assignment. Existing
parent Sources, other workers' edits and the modified repository checkpoint were
preserved. No process, persistent link, staging, commit, push or mail was created.

The parents are the unchanged original component build driver SHA256
`810f3195118566ea746f90db055ae83046f98986ef507ef3b7cee2ed1bbbe514` and
runtime driver SHA256
`6b0c00389afb31a8579e470a0c8473f86a4834dc038e63385a602baa4730f9de`.
The baseline observed before editing was
`3f8c6a64a7c29d0343613e34baf4e70eedd8b4c9`. The current authority is
`COMPONENT_CONTRACT_R1.md`; this component supplies no normal assembly or genuine
producer authority.

The immutable fifteen-row `SOURCE_INPUT_R1.json` pin supplied by Root is
`1ca539a66abc6227cef6320e4bc0ae13acfc56834bdb01bfcf0e2bf0e31970d3`.
Both drivers require exactly `status`, `base`, `scope`, `rows`, `genuineEnvelope`,
the declared baseline and `QUALIFIED_PRIOR_SCHEMA_AND_METADATA_STAGING_COMPONENT`
scope, `PINNED_SOURCE_ONLY_NO_DATA` status, `UNSPENT_GATED`, and the exact canonical
fifteen names/row schemas. Every Source body is rehashed. Admitted Python Sources
are read as bytes, never imported by these drivers.

Root must create `FROZEN_SOURCE_R1.json` with status
`ROOT_ACCEPTED_FINAL_SOURCE_FOR_SYNTHETIC_GATES` and exactly ten unique `files`
rows, each containing only `role` and lowercase 64-character `sha256`:

- `source/CURRENT_STUDY.java`, SHA256
  `524318f4023fa3b884494657a24dffaac063352cc6e21b4c71bd84e41c0e69e4`.
- `source/SHARED_OWNER.java`, SHA256
  `99993fc4f2c32e38fc85d4cea79b7662841a744f35816b95b0b2e8579483915b`.
- `source/NATIVE_PRIVATE_PCB.java`, SHA256
  `7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c`.
- `source/CURRENT_RECEIPT_BINDING.java`, SHA256
  `1df7e06085165d025ff79bdd0cdaa7f603c110cc26b6cc6f280d73f52e8d2e41`.
- `source/CurrentV2CanonicalCodec.java`, SHA256
  `76fb7bbc8af73e41fd8d79a26823a0687f61e2a812e4b9de251bc953c04c7522`.
- `source/CurrentV2InputSetup.java`, SHA256
  `7baab4caf45741f00d311e3b3854c794204356bfa03c0f3271399d44087df6a4`.
- `CurrentAssemblyMetadata.java`, Root-supplied SHA256
  `e2b6b05bc95b9295d15aba5305a188c1eb98cb39db4b689747e2f126a6835198`.
- `emitted/CohortTerminalTelemetryInputV2.java`, Root-supplied synthetic factory
  SHA256 `c8a25f88ccf523e030e7e1c14073255b78b9358687ac65088339a237bea80089`.
  Generation remains a later Root operation; these drivers do not generate it.
- `QualifiedMetadataCanary.java`, final Root-accepted canary Source hash.
- `build_qualified_metadata_r1.py`, final Root-accepted build driver Source hash.

Unknown freeze roles fail. The runtime driver is separately frozen, allowing the
original compiled-source freeze to remain unchanged.

The fresh source count is 862: production848 excluding the production PCB and
developer verifier; Shared/Study/PCB replacements3; exact public authored-flat
canary/deny-fixture Sources5; codec/receipt2; input-helper/metadata/factory/canary4.
Four correctly named stages include the current receipt. The combined
`componentSourceCount` is6; sourceInputCount is15. Source names and paths must be
unique. No old classes enter classpath. The intentional synthetic V2 class and
current helper/metadata/canary classes must be present; actual producer
Study/Mode/ArmRun and Study.OriginalArm classes and nested descendants must remain
absent. The unchanged ArmResult carrier is allowed only at811 bytes, major51 and
SHA256 `7d9cbb2c16132df342829c8174eb8565e41d33efb45ae120e0f011f4f1fc6ca0`.
It is never instantiated by either driver.

The trusted Root helper and historical process-helper Source remain pinned at
`224cf971a7142be7ea3679555f051ec65c4b2a2e6afab00c329dd9eb0d7372e3` and
`4f414bc2a8823e539c8f6ea7aaa0a2ff09d5bd04c092e617f14ff7bb7de78a8b`.
`root.H` and `g.R` point at this task directory. Only the six minimal
`root.tools_pinned()` pins are used, never historical `g.tools_pinned()`.
Stable reads reject symlinks/reparse ancestors, compare file identity/length
before/open/after and rehash Sources, tools, JARs and the fresh class closure.
The four inherited Java-option injection variables are rejected.

Build output is exclusively created under `native-build-r1`; existing output is
never overwritten, reused or removed. Classes/sourcepath begin empty. JDK8 uses
explicit Sources, `-source 7 -target 7 -encoding UTF-8`, only the fresh classes
plus two pinned GWT JARs, and unchanged60000ms compile limit. Source4MiB,
class10000/268435456B and major51 constraints remain. Sources/driver/argfile,
source/freeze manifests, production name census, tools and JARs are rechecked
around the compile, and class closure is walked twice. The runtime additionally
authenticates exact actual compiler args and accepts only the known exact
four-line unchecked-note stderr or two-line warning-only stderr, with no stdout.

The build writes `NATIVE_BUILD_INPUT_R1.json`, `NATIVE_CLASS_INVENTORY_R1.json`,
`BUILD_MANIFEST_R1.json` and `NATIVE_COMPILE_GATE_R1.json` with the established
schemas. Source roles begin `A/` or `F/`; actual paths/argv remain in task-private
argfile/wrapper receipts. Build status is
`PASS_FRESH_NATIVE_QUALIFIED_METADATA_COMPILE_ONLY`, scope
`FRESH_NATIVE_QUALIFIED_METADATA_SYNTHETIC_ONLY`, sourceCount862,
`syntheticV2InputClassPresent:true`, `qualifiedMetadataCanaryClassPresent:true`.
Runtime remains `NOT_RUN`, normal-host acceptance `NONE`, genuine envelope
`UNSPENT_GATED`. Compile/process/wrapper elapsed times remain separate; separate
cleanup duration is null unless independently measured elsewhere.

Root supplies runtime controls before any native execution:

```json
{
  "status": "ROOT_ACCEPTED_RUNTIME_CONTROL_ONLY",
  "originalSourceFreezeSha256": "<exact original freeze hash>",
  "unchangedCompiledSources": true,
  "files": [
    {"role": "run_qualified_metadata_r1.py", "sha256": "<final runner hash>"},
    {"role": "CASE_MANIFEST_R1.json", "sha256": "<frozen case manifest hash>"}
  ]
}
```

`CASE_MANIFEST_R1.json` has exact keys `status`, `cases`, status
`ROOT_ACCEPTED_NATIVE_CASE_MANIFEST`, and exactly two ordered rows, smoke then
suite. Each row has `role`, `class`, `args`, `status`, `names`, `caseShape`, plus
optional `expectedScalars`. Class is
`com.lushprojects.circuitjs1.client.QualifiedMetadataCanary`; args are
`["SMOKE","__FIXTURE_DIR__"]` or `["SUITE","__FIXTURE_DIR__"]`; status is`PASS`,
caseShape is`records`, and names are Source-frozen, nonempty and unique.
`expectedScalars` applies exact typed top-level scalar equality. Assertion counts
are not guessed; optional observed assertions must be positive integers.

The later CLI is `--case smoke|suite --fixture-dir <absolute canonical empty
direct-child-of-task-directory>`. Root creates a unique empty owned directory
for each mode. Native args are selected Java `-ea -Xmx512m`, the fresh classpath,
the fixed canary class and frozen mode/resolved directory args. Every native
`root.call` has60000ms limit. Smoke is phase `native-qualified-metadata-smoke-r1`,
suite `native-qualified-metadata-suite-r1`; records are separate
`native-run-smoke-r1` and `native-run-suite-r1` input/gate JSON files. Existing
phase/record/failure evidence prevents reuse or overwrite. Suite requires the
previous authenticated smoke gate, matching all Source/build/control/case pins,
actual process/argv and strict summary, before its own JVM can start.

Each native response must be exactly one compact JSON line with empty stderr,
Root-frozen status/names/order/count, and PASS case records containing scalars
only. Mode is exact. Optional suppressedFailures must be integer0. Sources,
controls, tools, JARs, class closure, actual compile/native args and receipts are
rechecked around each call, including a finally path on native/semantic failure.
Failure gets no success gate and retains its existing evidence. Full two-mode
acceptance remains Root-owned; a single mode never claims whole-component proof.

The canary author reports that successful smoke retains exactly
`qualified-prior.json` and successful suite leaves an empty fixture directory.
The runtime enforces this shape with regular bounded ASCII/JSON file checks,
hashes the smoke fixture and tags it `SYNTHETIC_QUALIFIED_PRIOR_FIXTURE_ONLY`.
Its gate says `SYNTHETIC_QUALIFIED_PRIOR_FIXTURE_NOT_WHOLE_RESULT`; it is not
presented as actual current DATA, a whole result or a host artifact. The file
is reauthenticated as the smoke prerequisite for suite. Both owned directories
are retained. The runner deletes no fixture or process resource.

Validation here is manual Source inspection only; final Source gates, fixture
generation, compile, actual-Meter Windows smoke, suite and independent result
audit are NOT RUN and belong to Root. No electrical, population, GWT/Player,
normal assembly/host or genuine qualification is inferred.
