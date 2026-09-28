# Q30 solver-publication profile r1

This is a source-only private probe draft. It is based on the
`pivot-lower-fusion-r2-preparation/candidate` tree supplied with the task
(complete1340inputmap, base SHA `0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197`).
The draft has no production acceptance claim: no build, native gate, browser
run, staging, commit, or push was performed here.

## Inclusion

The overlay contains four source files:

* `CirSim.java` adds one nullable, simulator-owned pointer to a
  `Q30SolverPublicationProfile`, plus coarse call-boundary probes around
  solved-right-side publication, convergence rollback, accepted-step
  bookkeeping, and the three wire-refresh contexts.
* `Q30SolverPublicationProfile.java` owns only bounded counters and timing
  samples. It retains no simulator, graph, element, node, generation owner, or
  mutable external object.
* `Q30CoordinatorQualificationVerifier.java` creates one profile for each
  private cold or warm coordinator run when the explicit
  `tsjQ30PublicationProfile=true` query is present. The query is read only by
  the already private Q30 coordinator verifier after its debug-scope checks.
* `Q30SolverPublicationProfileContractTest.java` is a JVM-native contract with
  an injected fake clock. Its unique stdout marker is
  `PASS: Q30 solver publication profile contracts assertions=<n>`.

The root task must register the native contract in its existing focused driver;
this draft intentionally does not edit that driver.

## Exclusion and nesting

The probe does not change solver arithmetic, matrix/model callbacks, callback
order, timestep settling, graph construction, current refresh behavior,
work limits, budgets, normal-player fields, normal query parsing, or the
ordinary verifier routes. Existing calls remain nested as follows:

* `applySolvedRightSide` contains its full finite scan, current assignment,
  and `setNodeVoltages` callback publication in one whole-operation span.
* `rollbackVoltagePublication` surrounds only the existing convergence
  rollback `setNodeVoltages(lastNodeVoltages)` call.
* `acceptedStepBookkeeping` surrounds `stepFinished`, scope updates,
  embedded scope callbacks, the last-node-voltage snapshot, and
  `solverExecutor.accepted`. An immediate wire refresh pauses and resumes
  this span so its duration is excluded. A delayed owned temporal refresh is
  outside the accepted span, after `solverExecutor.accepted`.
* `wireRefreshImmediateAcceptedStep`,
  `wireRefreshDelayedOwnedAcceptedStep`, and
  `wireRefreshDeferredUiBatch` each surround only their existing
  `calcWireCurrents` call. They never share an accepted-step timer.

The verifier owns a profile through the complete coordinator cold or warm run,
including all candidate hypotheses and retries. Completion, startup or
advance failure, cancellation/scope loss, successor loss, and final cleanup
all use the same freeze/capture/conditional-clear path. Clearing is identity
checked, so an old runner cannot clear a successor's profile pointer.

## Sampling and report shape

Each phase has an independent monotonic eligible-call counter. The 512th and
1024th eligible calls are selected (`eligible % 512 == 0`), so two selected
operations are expected after 1,024 calls in one phase. Only selected
operations call the browser `performance.now` clock. Clock readings are
required to be finite and nondecreasing across all selected readings in the
profile, including readings in separate selected operations. The accepted span
uses selected-only pause/resume clock points around an immediate wire refresh;
the paused interval is excluded, and no node or element clock is used.

Every phase reports `eligible`, `selected`, `completed`, `failed`,
`elapsedMs`, `zeroDurationCount`, `timerErrors`, `rawDurationCount`, and
`rawDurationsDropped`. Raw selected durations are capped at 64 values per
phase. Profile-level `sampledElapsedMs` is the sum of observed selected
durations only; the producer never extrapolates an exact whole-operation cost.
Clock errors, nonfinite values, backwards values, open/finalized spans,
unknown phases, and late operations set `valid=false`. Finishing while paused
and finishing the same sample twice are explicit lifecycle errors; lifecycle
and timer error counters saturate instead of wrapping. Physical solver
exceptions still propagate through the original operation path; a profiler
clock failure is recorded as invalid timing rather than becoming a solver
result.

The JSON shape is value-only and nested as:

```text
run
  publicationProfile
    sampleInterval, valid, frozen, clock, lifecycle counters
    eligible, selected, completed, failed, sampledElapsedMs
    phases[]
      name, counts, elapsedMs, zeroDurationCount, timerErrors
      rawDurationCount, rawDurationsDropped, rawDurationsMs[], counterOverflow
```

Cold and warm `RunRecord` objects each retain their own frozen profile report;
no profile object or raw array is reused between runs. A later reader must
reject any report whose `valid` flag is false or whose lifecycle counters show
late, unknown, timer, or open-sample errors.

## Native contract and limits

The contract covers independent 512/1024 selection for all six phases, finite
and zero-duration clocks, backwards clocks within and between selected spans,
nonfinite/throwing clocks, exception `finally` completion, accepted-span
pause/resume non-overlap including multiple pauses, illegal paused/duplicate
finalization, freeze and raw-array alias isolation, cold/warm isolation, and
unknown-path invalidation. It does not invoke GWT JSON or browser JSNI.

This draft is a measurement seam, not a timing oracle. `performance.now`
quantization may produce zero-duration selected samples, and a finite but
small sample set cannot establish sub-quantum work shares. Repeated cold and
warm runs are required before any follow-up optimization decision. Native
timing is unavailable unless the private profile is exercised in the compiled
browser path.
