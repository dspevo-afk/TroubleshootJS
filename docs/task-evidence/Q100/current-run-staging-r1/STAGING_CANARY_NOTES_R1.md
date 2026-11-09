# Current RUN input and cohort staging canary (R1)

## Scope

`CurrentRunStagingCanary` is a Java 7 source-only qualification canary. It invokes
the actual `CurrentV2InputSetup.read` with a local `Factory` adapter backed by a
fresh `CohortTerminalTelemetryInputV2` static factory call for each case. Accepted
cases then call
`CurrentAssemblyMetadata.cohortBeforeBody(SetupMeter, SetupResult, List<String>)`.
The canary uses the real `CohortTerminalTelemetryStudy.Parent` and `SetupMeter`.
It does not call original capture, arm execution, a graph, a session, or routing.

The fixture is synthetic and is not owner evidence. The frozen input-context SHA
is `b18d9b6169cfe1ed846402a6a7784fb49f008624a00bc8aa535fa73e444e984d`.
Captured and requested IDs are independently bound to the literal vectors in
`FIXTURE_REFERENCE_R1.json`, including `D_SENSE` in the captured sequence and at
requested position 12. The input snapshot is taken in the factory callback after
the case's pre-read synthetic mutation. Setup and staging are checked against
that snapshot. The three captured-vector mutations use a separate `ArrayList`
copied after setup; no admitted setup input is changed after `read`.

## Invocation and output

Invoke with exactly one argument, `SUITE`. It runs all nine cases sequentially
in one JVM and constructs a fresh actual parent/meter for each. Any other
argument count or value emits a `FAIL` JSON record and exits 1. Any unexpected
throwable, missing expected rejection, altered input, wrong payload, counter
mismatch, or timing/bound failure makes the suite fail and exit 1.

The output schema identifier is `TSJ_CURRENT_RUN_STAGING_CANARY_R1`; the fixed
top-level and per-case keys are defined in `STAGING_CANARY_SCHEMA_R1.json`.
`payload` is the actual returned projection `Map` for `positive` and `null` for
all rejected cases. The serializer enforces ASCII, a 1 MiB output limit, and
maximum nesting depth 6.

Per-case field meanings:

- `units`, `copiedEntries`, `copyChunks`, group fields, and elapsed fields are
  read from the real meter. `operationNanos` times the specific setup, staging,
  guard, or group-guard operation; `totalElapsedNanos` starts with meter creation.
- `setupDelta*` and `stageDelta*` are measured before/after native operations.
  Failed native false-flag setup reports its actual partial setup debit, bounded
  by the success setup census; no exact false-case total is assumed.
- `sourceReads` counts the four actual `Factory` callbacks. `sourceReadObserved`
  is null except in the group-guard case, where it is the synthetic `Mapspy`
  `get` count. `sourceReadCensus` is the source-reviewed expected `data.get
  ("prior")` count: one for staging attempts that pass the initial meter guard,
  zero for native setup rejection, null guards, and the exhausted-meter guard.
  The positive typed `LinkedHashMap` read is not claimed as instrumented.
- `finishCalls` counts only the canary's explicit close of the successful
  staging group. It excludes `CurrentV2InputSetup.read`'s setup close and the
  640 direct `finishGroup` calls used to prepare the group-guard meter. Failed
  stage meters are left open and never finished by the caller. `discarded` is
  false only for a successful positive case after its two groups close; it is
  true for every rejection and failed case because those per-case meters are
  not reused.
- `inputUnchanged` compares the complete input snapshot against its post-mutation,
  pre-read baseline. `outputDetached` is true only after recursive identity checks
  confirm that the successful payload retains none of the input map/list
  containers.

## Cases and expected phases

| Case | Expected phase/reason | Expected result |
| --- | --- | --- |
| `positive` | `complete` / `OK` | Setup `10007/478/3`, stage `264/125/64`; after one caller finish, combined `10271/603/67`, 2 groups. Exact payload Map and detached output. |
| `wrong_capture_order` | `stage` / `EXPECTED_REJECTION` | Swap indices 0 and 1 in the independent captured copy; `IOException:COHORT_REQUESTED_VECTOR`, debit `259/123/63`. |
| `duplicate_capture` | `stage` / `EXPECTED_REJECTION` | Set captured index 20 to index 19; `IOException:COHORT_VECTOR_DUPLICATE`, debit `141/84/24`. |
| `missing_capture` | `stage` / `EXPECTED_REJECTION` | Set captured index 43 to `SYNTH_MISSING`; `IOException:COHORT_NOT_IN_CAPTURE`, debit `258/123/63`. |
| `requested_mismatch` | `stage` / `EXPECTED_REJECTION` | Factory callback swaps 0/1 in both prior and capture requested lists before native read; typed unique input is accepted, then staging rejects with `IOException:COHORT_REQUESTED_VECTOR`, debit `259/123/63`. |
| `prior_false` | `native_setup` / `EXPECTED_REJECTION` | Factory callback sets the prior RUN `orderChanged` false; native setup rejects with `IOException:V2_SETUP_POLICY`; staging is not called. |
| `capture_false` | `native_setup` / `EXPECTED_REJECTION` | Factory callback sets the capture RUN `orderChanged` false; native setup rejects with `IOException:V2_SETUP_POLICY`; staging is not called. |
| `null_arguments` | `argument_guards` / `EXPECTED_REJECTION` | Null meter, setup, and captured-ID guards each return `IOException:METADATA_ARGUMENTS`; three rejections, zero staging debit. |
| `group_guard_before_read` | `group_guard` / `EXPECTED_REJECTION` | Real meter accumulates 640 individually finished one-unit groups. A synthetic `SetupResult` uses a counting map; staging rejects with `IOException:native wrapper exceeded 640 measured groups` before `get`, observed count 0, zero staging debit. |

All expected failures preserve their meter state without an explicit caller
finish. `prior_false` and `capture_false` use the current native policy check and
are not inferred from the old false-accepting profile.

## Validation status and limits

Source-only review was performed against `CONTRACT_R1.md`,
`FIXTURE_REFERENCE_R1.json`, the allowed current setup/cohort source, the edited
scratch `CurrentV2InputSetup.java` and `CurrentAssemblyMetadata.java`, and the
admitted result-canary JSON formatter. This worker did not compile or execute the
canary, run the native suite, or claim host/owner evidence. Root integration and
the assigned gates remain necessary.
