# Failure-cohort native study wire R1

Status: frozen native-only diagnostic contract. This is a normative delta over
`failed-first-ordering-r1/source/WIRE_R1.md`, SHA-256
`aaf0852450e069c442497734ff2233b8ee0229d99760cf58eca21baa8d6d78e7`. That
immutable wire is incorporated in full except where this delta explicitly
replaces a name, field, transition, or rule below. All unchanged source, layout,
finalizer, serialization, meter, copy, cleanup, and bound requirements still
apply. This file itself is the authenticated wire input and does not contain
its own SHA; the launcher supplies the SHA-256 of the exact UTF-8, no-BOM, LF
bytes.

## Names and invocation

- Java entry point: `com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy`.
- Canary: `--canary` (one argument).
- Authored run: `FAILURE_COHORT UUID FINAL_RUN_MANIFEST_SHA256 WIRE_CONTRACT_SHA256 EMPTY_ABSOLUTE_DATA_DIR` (five arguments).
- UUID, SHA, and real-empty-directory validation are inherited unchanged from the predecessor wire. Expected preflight rejection writes exactly `FAILURE_COHORT_INPUT_REJECTED:<CODE>\n` to stderr and exits 1, without creating files. Codes remain `ARGS`, `UUID`, `MANIFEST_SHA`, `WIRE_SHA`, `DATA_PATH`, and `DATA_NONEMPTY`.
- The result file remains `op-1.jsonl` plus `result.json`; operation 1 is freshly emitted by the unchanged genuine `ActualGeneratorBranchOrderStudy.ArmRun`, byte-preserved, and independently checked. Result and marker byte, elapsed, cleanup, line, and file limits are unchanged.
- Result schema is `tsj-failure-cohort-routing-v1`; mode is `OBSERVED_FAILURE_COHORT`. Marker schema is `tsj-failure-cohort-marker-v1`, status `COMPLETED_DIAGNOSTIC`. Canary schema is `tsj-failure-cohort-canary-v1`.

The authored operation-1 identity stays bound to root seed `9007199254740993`,
placement seed `-6587958367974952701`, routing seed `8445301674744508279`,
request 0, placement attempt 4, pass 2, receipt ordinal 307, lineage ordinal
77, prefix SHA-256
`6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb`, and
actual prior promotion `D_SENSE`. The captured failure `A_COIL_LOW` and prior
promotion are assertions against the genuine receipt/live owner. These values
are not ordering branches, sort keys, or special cases; all requested ordering
is derived from validated live vectors and observed feedback.

## Result root and trial disposition

The result object has exactly these top-level keys:

```json
{
  "schema":"tsj-failure-cohort-routing-v1",
  "mode":"OBSERVED_FAILURE_COHORT",
  "runId":"lowercase UUID",
  "finalRunManifestSha256":"lowercase SHA-256",
  "wireContractSha256":"lowercase SHA-256",
  "operation1":{},
  "reference":{},
  "source":{},
  "capturedOrder":{},
  "control":{},
  "trial":null,
  "trialDisposition":{},
  "work":{}
}
```

`operation1`, `reference`, `source`, `capturedOrder`, all existing nested
physical/layout and finalizer fields, and stdout marker fields preserve their
predecessor meanings and exact schemas. `operation1` keeps the original seeds
as decimal strings. The operation-1 capture and P07 source ancestry are
unchanged and genuine; no predecessor routing row is reused as a new run.

`control` is always one fresh actual Session. `trial` is either one fresh
actual Session or null. `trialDisposition` has exactly these keys and types:

```json
{
  "status":"RUN|SKIPPED",
  "reason":"TRIAL_EXECUTED|CONTROL_FOUND|CONTROL_NON_NO_PATH_TERMINAL|NO_ORDER_CHANGE",
  "capturedFailedNetId":"A",
  "controlFailedNetId":null,
  "cohortNetIds":null,
  "requestedNetIds":null,
  "orderChanged":false,
  "feedbackValidated":true
}
```

`feedbackValidated=true` means every feedback item required by the disposition
was validated; `CONTROL_FOUND` requires no failure feedback. Invalid feedback
is rejected before any trial Session is constructed and has no completed DTO.

For `RUN`, `trial` is non-null, reason is `TRIAL_EXECUTED`,
`controlFailedNetId` is the verified B, `cohortNetIds` is exactly `[A,B]`,
`requestedNetIds` is the full expected trial order, and `orderChanged=true`.
For `SKIPPED`, `trial` is null and `orderChanged=false`:

- `CONTROL_FOUND`: the actual control outcome is `FOUND`, it has no terminal
  failure feedback, and the remaining cohort/vector fields are null.
- `CONTROL_NON_NO_PATH_TERMINAL`: the owner completed with a cap or quality
  rejection rather than a genuine `*:NO_PATH` net failure. Any terminal proof
  that exists is validated and retained in `control`; `controlFailedNetId` is
  its `failedNetId` (or null if no proof exists); cohort/vector fields are null.
- `NO_ORDER_CHANGE`: valid distinct A/B feedback produced a cohort order equal
  to the control's requested order. `cohortNetIds=[A,B]` and
  `requestedNetIds` records that vector; no duplicate Session is created.

No other completed disposition is allowed. A `NO_PATH` control without valid
terminal feedback, or feedback that is absent, malformed, unknown, repeated,
not bound to the vector/cursor, or absent from actual traversal is a hard
owner-contract rejection before constructing the trial Session. It emits no
successful result and no success marker. It is never serialized as a skipped
trial. FOUND/non-NO_PATH/no-change are the only successful skip reasons.

## Captured order and requested vectors

`capturedOrder` retains the predecessor's exact keys
`pass,failedNetId,priorPromotedNetId,netIds,liveSearchIndex,owner` and meanings.
Its `failedNetId` A, prior promotion, complete unique `netIds`, live search
index, policy, widths, and all routing settings come from the selected genuine
operation-1 terminal callback while the owner is live. It retains the original
required `FULLER_TWO_LAYER`, role-width, complete-net-vector, and settings
checks.

Each run's `ordering` replaces the predecessor six-key object with exactly:
`capturedNetIds,computedNetIds,requestedCohortNetIds,requestedNetIds,
traversedNetIds,orderMatchesCapture,searchCount`. The control prefix is `[A]`;
the trial prefix is `[A,B]` in first-observed order. `computedNetIds` is the
native pass-2 Search vector before intervention. It must equal the captured
complete vector. `requestedNetIds` is the vector installed before any net
starts. `orderMatchesCapture` means `computedNetIds == capturedNetIds`, even
when a requested order is applied. `searchCount` remains exactly 1.

Build each requested full vector with one stable partition: emit the requested
cohort IDs in their declared order, then emit every other source ID in its
original relative order. Validate non-null, known, unique cohort IDs and exact
unique complete source/board membership before mutation. Control and trial use
the same explicit requested-order operation path; no run-kind-specific mutation
or repeated move-to-front calls. Verify the native computed vector, pristine
pass-2 state (`pass=2`, `passes=1`, `expanded=0`, `netIndex=0`, null
`currentNet`, no traces/holes), owner settings, and complete membership before
replacing the live Search vector. The serialized `initial` object still reports
`passes=0` before Search construction; at the actual pre-net mutation boundary
Search has been constructed and the owner has `passes=1`. Verify the requested
vector after the write.
The single A-front control always runs, including when A was already first.
The trial is planned only after validated distinct B feedback. Compare its
requested vector with the control requested vector; if identical, record
`NO_ORDER_CHANGE` and do not execute a duplicate run.

## Actual terminal failure evidence

Every run object has exactly
`kind,initial,ordering,outcome,terminalFailure,finalizers,layout,work`.
The run kinds are `OBSERVED_FAILURE_CONTROL` and
`OBSERVED_FAILURE_COHORT_TRIAL`. Existing `initial`, `outcome`, `finalizers`,
`layout`, and all physical operand row schemas remain as in the predecessor.
`layout` is null unless the native owner returned `FOUND`; every original
applicable finalizer is required for `FOUND`. Rejected arms keep finalizers
`NOT_RUN` unless the predecessor explicitly requires a native finalizer to be
reported `PASS` before a route-quality rejection.

`terminalFailure` is null unless one actual `advanceSlice(1)` call clears the
active pass-2 Search by the genuine exhausted transition to pass 3. When
present, it has exactly:

```json
{
  "failedNetId":"actual retired Search.currentNet",
  "searchIndex":1,
  "searchPass":2,
  "passBefore":2,
  "passAfter":3,
  "advanceCallOrdinal":1,
  "ownerSearchCleared":true,
  "searchReleased":true,
  "ownerPromotedNetId":"actual Session.previousFailedNet",
  "ownerReason":"exact actual Session.reason"
}
```

Before each `advanceSlice(1)`, take transient before/after `session.search`
identity snapshots and owner pass values for that call only. Count a null-to-
non-null Search transition; reject an unexpected replacement when both
snapshots are non-null. Do not keep a `lastSearchIdentity` or any Search alias
between calls. If that same call clears the Session search and changes pass 2
to 3, read `currentNet`, `netIndex`, and pass from the retained retired Search
object, and read `previousFailedNet` and `reason` from the actual Session before
dropping the retained reference. Do not infer the failure from a
pre-call `currentNet`, traversal tail, exception text alone, or a post-completion
owner read. The owner may advance/select during the call; the retired Search's
post-call fields are authoritative. Require retired search pass 2,
`searchIndex` equal to its actual `netIndex` cursor (1-based), valid index,
`requestedNetIds[searchIndex-1] == failedNetId`, owner `previousFailedNet ==
failedNetId` under the required role-width policy, exact owner reason, and the
failed ID present exactly once in actual `traversedNetIds`. For a trial-eligible
control, `ownerReason` and `outcome.reason` must be the same actual net
`*:NO_PATH` reason and `outcome.disposition` must be `NO_PATH`. The producer
also verifies the trial cohort B is known and distinct from captured A before
trial construction.

`ownerSearchCleared` records the observed null Session search after that exact
call. `searchReleased` records that the transient retired-Search reference and
every other adapter alias were nulled in cleanup after the immutable scalar
evidence was detached. Release references on normal, rejected, exceptional,
and cancellation paths; do not retain a Search between calls or arms. A
non-NO_PATH terminal may have valid `terminalFailure` evidence but can never
authorize the trial. A `FOUND` control must have null `terminalFailure`.

## Work and original limits

Each run's `work` adds `failureObservationUnits` and `terminalEvidenceUnits`
to the predecessor run-work keys `advanceCalls,ownerUnits,wrapperUnits,
copyEntries,copyChunks,groups,maxGroupNanos,maximumUnitsPerGroup`.
`failureObservationUnits` is exactly one bounded transition-observation dispatch
per `advanceSlice(1)` call, so it equals `advanceCalls`. This is charged in
`wrapperUnits` in addition to the predecessor's existing fixed one-wrapper-unit
poll/dispatch charge per call; it does not claim one unit per primitive field
read. The dispatcher holds only transient before/after Search references for
that one call and counts null-to-non-null creation; it does not retain a Search
between calls.

`terminalEvidenceUnits` is independently exact. It is 0 when `terminalFailure`
is null. Otherwise let `N` be `ordering.traversedNetIds.length`; the value is
`11 + N`, plus 1 when this is a control whose outcome disposition is `NO_PATH`
and trial eligibility is checked. The fixed 11 comprises five actual terminal
field reads (`retiredSearch.pass`, `.currentNet`, `.netIndex`,
`Session.previousFailedNet`, `Session.reason`), five checks (pass identity,
search-index bounds, requested-vector cursor binding, owner-promotion equality,
and owner-reason/outcome-reason equality), and one release operation for the
borrowed retired-Search reference. The `N` units are the actual per-entry
comparisons needed to verify that the failed ID occurs exactly once in
`traversedNetIds`. The optional one is the actual control `*:NO_PATH` eligibility
check. The `passes` transition and null Session-search observation are covered
by that call's `failureObservationUnits`; do not count them again here. Charge
only these actual reads/checks and do not add speculative fields or work.

For every attempted `advanceSlice(1)`, increment `advanceCalls` and charge the
old fixed poll unit plus its one `failureObservationUnit` before entering the
owner call. On either normal return or exception, read `Session.lastWork()`
exactly once: if positive, charge that value once to `ownerUnits`; if zero,
charge exactly one wrapper fallback unit. Do not increment the attempt after
return and do not add fallback work when owner work is positive. On exception,
complete this accounting and release transient Search references before
rethrowing the same original throwable. Accounting or cleanup failures must
remain visible as secondary failures; no success result or marker is allowed.

Copied terminal strings/evidence and every copied/requested vector entry are
charged to the existing copy ledger before copy. Require
`wrapperUnits >= advanceCalls + failureObservationUnits + terminalEvidenceUnits`;
all other adapter work is charged by its existing wrapper-meter categories.
The aggregate `copiedEntries`, `copyChunks`, metadata tree walk, and per-group
units include every new read, comparison, copy, and serialized field.

Top-level `work` retains every predecessor key and formula, replacing
`failFirst` with `trial` and adding `controlFailureObservationUnits`,
`trialFailureObservationUnits`, `controlTerminalEvidenceUnits`, and
`trialTerminalEvidenceUnits` as cross-checks equal to their run values. These
are subsets of `control.wrapperUnits` and `trial.wrapperUnits`, not extra terms
in the additive unit formula. For a null trial, trial-run owner/wrapper/evidence
units and trial Session copy/group counts are zero. `trialDisposition` can still
contain validated control feedback and the candidate cohort/vector for
`NO_ORDER_CHANGE`. Cleanup keys use `trialReferencesDropped`; its value is true
only when no trial existed or the trial's complete reference-drop proof passed.
Cleanup still includes the genuine original ArmRun and every created fresh owner.

All unchanged predecessor limits remain hard: 90 seconds total, 640 groups,
5 seconds and 32768 units per complete group, 120 seconds cleanup, 1048576
copied entries, 4096-entry copy chunks, original expansion maximum, 48-hole
maximum, four routing transitions, 65536-byte operation line, 8 MiB receipt,
1 MiB result line, and 8 MiB result file. Preserve the original 1M expansion
bound and other P07 budgets; each fresh arm starts at expanded 0 under the
captured maximum. The only authored arms are one A-front control and at most
one `[A,B]` trial. There is no retry, third arm, fallback, budget carry, hidden
order, or additional ordering algorithm. The predecessor additive work,
receipt/source proof, group census, cleanup timing, cutoff/export accounting,
complete layout operands, and no-post-cutoff rules apply unchanged.

## Canary delta

`--canary` emits exactly one JSON object with exactly these root keys:
`schema,status,openPositive,blockedNoFallback,activeExceptionCleanup,
stableTransform`. `schema=tsj-failure-cohort-canary-v1` and `status=PASS`.
The canary uses only the literal native fixtures and synthetic IDs listed here;
it is structural evidence, not authored-board evidence.

- `openPositive` has exactly `control,trialDisposition`. `control` uses the
  full run schema above, has `kind=OBSERVED_FAILURE_CONTROL`, requested cohort
  `["NET_A"]`, `outcome.disposition=FOUND`, `terminalFailure=null`,
  `layout!=null`, all applicable finalizers `PASS`, and two actual traversed
  target nets. `trialDisposition` has the same exact eight keys/types as the
  result schema; values are `status=SKIPPED`, `reason=CONTROL_FOUND`,
  `capturedFailedNetId=NET_A`, null `controlFailedNetId`, null `cohortNetIds`,
  null `requestedNetIds`, `orderChanged=false`, `feedbackValidated=true`; true means every feedback item required by that disposition was validated, and `CONTROL_FOUND` requires no failure feedback.
- `blockedNoFallback` has exactly `control,repeatedFeedback,noFallback`.
  `control` uses the full run schema, has one Search, one terminal proof bound
  to `NET_A` and cursor 1, `outcome.disposition=NO_PATH`,
  `outcome.reason=NET_A:NO_PATH`, terminal pass 3/passes 1, null layout,
  all finalizers `NOT_RUN`, and no pass-3 Search. `repeatedFeedback` has exactly
  `capturedFailedNetId,controlFailedNetId,status,reason,trialSessionCreations`;
  values are `NET_A`,`NET_A`,`REJECTED_BEFORE_TRIAL`,
  `REPEATED_FAILURE_ID`, and 0. `noFallback=true`. This is a caught expected
  pretrial rejection inside the canary; it does not produce an authored result.
- `stableTransform` has exactly
  `source,cohort,requested,noChangeSource,noChangeCohort,noChangeRequested,
  unrelatedOrderPreserved,noChangeSkipped,duplicateCohortRejected,
  missingCohortRejected,unknownSourceRejected,missingFeedbackRejected,
  unknownFeedbackRejected,repeatedFeedbackRejected,
  rejectedFeedbackTrialSessions,noChangeTrialSessions`.
  `source=["NET_A","NET_B","NET_C","NET_D"]`,
  `cohort=["NET_C","NET_A"]`,
  `requested=["NET_C","NET_A","NET_B","NET_D"]`;
  `noChangeSource` and `noChangeCohort` are both
  `["NET_C","NET_A","NET_B","NET_D"]` and
  `["NET_C","NET_A"]`, respectively, and `noChangeRequested` equals
  `noChangeSource`. The eight boolean results are all true, and both trial
  session counts are 0. The booleans cover stable relative order, identical
  requested-order skip, duplicate/missing cohort rejection, unknown source,
  and missing/unknown/repeated feedback rejection before trial creation.
- `activeExceptionCleanup` keeps every predecessor field and adds
  `adapterSearchAliasesReleased` plus exception-attempt accounting. Its exact
  keys are `exceptionClass,message,sameThrowable,callbackCalls,expandedAtThrow,
  searchPresent,branchPresent,ownerDiagnosticFailureSame,sessionReleased,
  searchReleased,candidateReleased,layoutReleased,boardReleased,
  adapterSearchAliasesReleased,referencesReleased,advanceCalls,
  failureObservationUnits,terminalEvidenceUnits,ownerUnits,wrapperUnits,
  attemptWorkAccounted,cleanupNanos`. These work counters describe only the
  one failing call; preceding setup/routing calls remain accounted normally.
  `advanceCalls=1`,
  `failureObservationUnits=1`, `terminalEvidenceUnits=0`, and
  `attemptWorkAccounted=true`. `ownerUnits` is the exact positive
  `Session.lastWork()` value or zero; `wrapperUnits` includes the old poll and
  the new observation unit, plus one fallback unit iff `ownerUnits=0`, and
  cleanup/diagnostic work. Require `wrapperUnits >= 2 + (ownerUnits==0 ? 1 : 0)`.
  The required exception class/message remain
  `java.lang.IllegalStateException` and
  `FAILED_FIRST_ACTIVE_BRANCH_CANARY`; `sameThrowable=true`, `callbackCalls=1`,
  `expandedAtThrow>=1`, `searchPresent=true`, `branchPresent=true`,
  `ownerDiagnosticFailureSame=true`, and all old and new release flags are
  true. `referencesReleased` is true only if every listed owner and adapter
  release succeeded; `cleanupNanos` is a positive integer.
## Deliberately unchanged evidence and limits

The predecessor's genuine operation-1 P07 receipt, callback/source ancestry,
physical output schema, owner finalizers, selected-host policy, and required
independent-reader checks are unchanged. In particular the reader must derive
the captured A-front control vector and `[A,B]` trial vector independently,
then bind any terminal proof's requested-vector cursor, actual owner reason,
role-width promotion field, and actual traversal. Preserve all predecessor
reader cases and add malformed, absent, duplicate, unknown, repeated, and
non-traversed terminal feedback rejection; control FOUND, non-NO_PATH, and
no-change skips; and borrowed-reference cleanup. No result can turn a genuine
`NO_PATH` into proof that all physical paths are impossible.

Production850/P9 frozen19, old helpers/data/classes/tool paths, and predecessor
sources remain immutable. HARD and PSYCHOTIC remain unavailable/unrun; full30
is excluded; playable100 remains 0 and beta HELD. CircuitJS remains electrical
truth. This is diagnostic evidence only and does not adopt a production routing
policy.
