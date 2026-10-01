# Q30 production-path baseline readiness screen

2026-10-01. **BLOCKED: committed harness capability missing. Q30 remains
BLOCKED / NOT ACCEPTED and disabled for normal players.** No fresh cold
production-admission job ran. None of the 33/35/37-package variants has a
production pass/fail result from this task. This is the requested harness-gap
report, not a private measurement relabeled as a production screen.

## Frozen baseline and interpretation

Source: `41f02f1b9f6c32cb8cf4ffbf9e8bd2bb3ea2476b`, tree
`812f13f85c665dbda8932e007433b13617bc3031`, branch
`codex/q30-multirail-qualification`. A new OS-temp directory received a
`git archive --format=zip` export of that exact commit. All 8,556 exported
files matched the archive bytes. No dirty scale/desktop source, untracked
normal-acceptance verifier, historical profiler or routing-map overlay was
imported. [Baseline identities and relevant SHA256 inputs](baseline.json)
include the archive hash, generation/proof/policy owners and harness inputs.

The committed plan is **version 3**, with 33/35/37 physical-package recipes.
The unchanged private seed-7/33-package controls are 79.020, 76.978, 79.989
and 78.817 seconds. They are supporting timing evidence only. The preceding
plan-4/seed-10014/40-package support-cost campaign included unaccepted scale
work and is a different workload. These results are not combined into an
optimization trend. The routing-map candidate failed its declared material
improvement rule and remains unintegrated; neither campaign is extended.

The installed browser inspected here was Microsoft Edge **154.0.4258.37**.
No browser was launched, no GWT build was produced, and no compiled-browser
identity or runtime result is claimed. A future screen must record its actual
JDK8/GWT build and loaded permutation hashes, browser version, host conditions
and fresh profile identity. There is no new 75- or 80-second gate.

## Why the committed harness cannot run this screen

Source references below identify the frozen commit, not the unrelated dirty
worktree implementation.

| Boundary | Committed behavior | Missing screen capability |
|---|---|---|
| Request | `Q30CoordinatorQualificationVerifier.start` calls `GenerationRequest.forFamilyQualification`; difficulty is null, search is false and the private request carries the small-board policy token. | A non-private `PlayerLaunchRequest`/`GenerationRequest`, MEDIUM assessment and `NORMAL_MEDIUM_EXECUTION@2`. |
| Admission gate | `PlayerFamilyCatalog.currentRegistration` registers Q30 with `false`; coordinator `normalMaximumJobMillis` and `Services.publish` both check the global catalog. | A narrowly scoped test admission authority at both checks, leaving the ordinary catalog disabled. |
| Budget | The private verifier passes `300000` to the measurement-only overload; the unchanged 640-work and 5,000-ms active-operation guards are enforced. | Actual enforcement of the normal 90,000-ms cumulative policy/path with those same work/operation guards. Merely finishing below 90 seconds is insufficient. |
| Cache | The private lane uses/clears `diagnosticMeasurementProofs` and asserts ordinary-cache counters stayed unchanged. | A fresh ordinary D01 proof cache with reuse enabled and cold miss evidence; private-cache isolation must remain intact. |
| Scheduling | Debug makes `foregroundBudget` false; the verifier manually advances turns and disables normal watchdog/continuation scheduling. | The production asynchronous/foreground path and its existing cancellation/cleanup behavior. |
| Candidates | Private requests have one exact candidate. Normal search derives four ordinal candidates with one shared job budget. | All attempted ordinals, exact signed-long seeds, resolved workloads and rejection reasons under the normal search request. |
| Rejection evidence | `GenerationJob.Attempt` stores manifest, stage, outcome and work, but no rejection message; `handleRejection` clears the live failure before retry. | Capture each rejection cause before it is cleared, without changing retry order or charging a new budget. |

The existing private lane does execute meaningful physical and D01 proof:
it requires normal-medium physical admission, explicit completion, repair
reachability, customer retest evidence and exact predecessor cleanup. Those
capabilities should be reused. They do not fill the request/difficulty/cache/
scheduling gaps above. A copied `RegistrationBoundary` with Q30 enabled also
does not solve them: the coordinator still checks the global `CURRENT` catalog.
**No qualification admission override was installed or used in this task.**

## Smallest required harness work

Add a scoped normal-path test lane, separate from the existing private
cold/warm measurement lane. The normal request constructors and family-owned
`StagedFamilyCapability` already exist; no new Q30 construction path, solver,
request format or performance change is needed.

1. Keep the qualification bootstrap/authority issuer in an isolated test
   build, and bind its eligibility authority to one request/job and require its
   live scope at pre-start and pre-publication. It may exempt only catalog
   availability. Keep the ordinary catalog false and all policy, actual
   physical, difficulty, diagnostic and completion checks unchanged. Do not
   add Q30-specific branches to the generic coordinator or expose a shipping
   URL/flag that enables ordinary play.
2. Drive the existing asynchronous normal start with a non-private MEDIUM
   request, the ordinary proof cache and the frozen 90,000/640/5,000 limits.
   Use a test driver independent of the debug flag, or explicitly preserve
   production foreground-clock semantics in the qualification scope; calling
   asynchronous start with debug still enabled does not close this gap.
   Run each cold job in a fresh isolated browser profile; assert both caches
   initially empty, ordinary miss/no warm receipt, and no private-cache use.
3. Reuse the existing snapshot, owner-retirement and proof-value readers.
   Extend value telemetry to retain candidate rejection reasons and report
   difficulty, actual package inventory, physical/D01 results and cleanup on
   both success and failure. Preserve incomplete cleanup as failure.
4. Validate the seam with focused normal-disabled/unauthorized-entry,
   lost-scope/cancellation, stale-successor and cleanup canaries, plus strict
   report-reader negatives and the actual final-source JDK8/GWT build.
   Then run only the frozen small screen below, not the acceptance matrix.

This is more than changing a timeout constant. An unreviewed catalog toggle
or calling the private measurement overload with `90000` would conceal the
missing semantics. No such prototype is integrated here.

## Predeclared screen and per-case status

The actual Java probe resolved seeds 7, 13 and 64 before selection. Its
`plan.board().getComponentIds().size()` matched each declared package count.
No fallback seed search was needed. These are physical recipe inventories;
live routed admission remains unrun. [Raw plan/request output](plan-probe-results.txt)
and [machine-readable frozen screen](screen-plan.json) retain exact signed-long
seeds, canonical plans and all four prospective ordinal candidate manifests.

| Cold job order | Plan | Root seed | Physical recipe packages | Root topology | Production result |
|---|---:|---:|---:|---|---|
| 1 | 3 | `7` | 33 | separate/direct; A BJT, B BJT | NOT RUN — harness blocked |
| 2 | 3 | `64` | 35 | shared/hysteretic; A NMOS, B NMOS | NOT RUN — harness blocked |
| 3 | 3 | `13` | 37 | separate/direct; A NMOS, B BJT | NOT RUN — harness blocked |

The initial screen is **three fresh cold generation jobs**, below the six-job
ceiling. Each is a normal `PlayerLaunchRequest.random(..., "MEDIUM")`
search request with its unchanged maximum of four internally ordered
candidates sharing the same job budget. These retries are not separate cold
jobs or new budget grants. Record each candidate's changed workload separately;
a later candidate's pass does not turn an earlier rejected seed into a pass.
No failed root will be replaced, and no additional repetitions are predeclared.
No cold job has run, so there are zero actual candidate attempts.

## Timing and evidence contract for the pending screen

Every cold job must report source/build identity, canonical normal request,
plan and exact decimal signed-long seeds for every attempted candidate, actual
physical package count, complete cumulative job elapsed time, total work,
maximum active-operation time, attempt outcomes/reasons, requested/computed
difficulty and physical/diagnostic/repair/completion/cleanup outcomes.
Headroom is `90000 - cumulativeJobElapsedMs`; negative values remain failures.
Browser startup and host teardown are separate. Job abort/cleanup time must
be described according to the existing job clock, never subtracted to rescue
a failure. Routing and proof stage timers are nested within job time; inner
proof timers, if collected, are nested within proof time and are not added
again. No private timer replaces a missing normal-path timer.

The selected-case table above and native checks below come from the clean
export. Until the harness gap is repaired, elapsed/work/max-operation/routing/
proof/headroom fields are **NOT RUN**, not zero; difficulty, live physical
admission, diagnostic completion and browser cleanup are also **NOT RUN**.

## Validation, commands and preservation

- **PASS:** pure Java plan inventory/request probe from the clean export,
  including canonical signed-long candidate transport and 33/35/37 coverage.
- **PASS:** maintained focused native4, JDK `1.8.0_502`: plan 2,256 assertions,
  request 446, measurement-budget 25, normal-policy 325. The last suite includes
  disabled-family rejection before predecessor/cache mutation. Some native
  request/policy fixtures perform detached construction/routing; they are not
  production admission attempts or timing results. [Native output](native-focused.txt).
- **PASS:** source/evidence review after correcting the budget and foreground
  wording; root reviewed plan output, native results and the scoped diff.
  This is not a runtime admission review.
- **NOT RUN:** browser/GWT, fresh cold admission, full acceptance matrix,
  ordinary-cache lifecycle/cleanup, visible player qualification and expanded
  scale. No production source changed, so documentation closeout does not
  require a GWT rebuild. Future harness work does.

Reproduction: export the exact commit into a new OS-temp directory; provide
the two hashed GWT jars under `.tools/gwt-2.7.0` and JDK8. The maintained command
was `scripts/verify-current-contracts.ps1 -JavaHome <jdk8> -PythonExe <python>
-Suite @('Q30NormalExecutionPolicyContractTest','Q30GenerationRequestContractTest',
'Q30GenerationMeasurementBudgetContractTest','Q30PlanContractTest')`.
The [probe source](Rb30PlanAuditProbe.java) and [sanitized invocation script](run-native-and-probe.ps1)
are evidence helpers outside production source. Put the script in
`<task-root>/plan-audit/` and the probe in its
`src/com/lushprojects/circuitjs1/client/` subdirectory, with the clean export
in sibling `baseline/`. Replace `<jdk8>` and `<python>` before use. The probe uses the maintained
explicit source list and fail-closed Task35 native stub; it does not invoke
the solver or routing. Process results and original/sanitized hashes are in
[validation.json](validation.json).

The first probe compilation failed on implicit Java source discovery; its
[failure log](plan-probe-initial-compile-failure.txt) is retained. A subsequent
wrapper directory setup failed before Java launch; idempotent task-owned
directory creation fixed it. Final explicit-source compilation, probe and
native4 returned zero. Neither setup failure is a production seed rejection.
An initial preservation scan hit Windows long-path diagnostics; the successful
scan used per-command `git -c core.longpaths=true`, with no global setting change.

[Pre-existing input hashes](preserved-inputs.json) cover 172 Q30 and 96 desktop
changed/untracked files. They are rechecked unchanged at closeout. The 26
tracked Q30 scale edits and existing untracked source/evidence/caches remain
unstaged. No browser/server was launched. The maintained native runner reports
its own scratch cleanup; final export/probe cleanup is recorded separately in
[resource-cleanup.json](resource-cleanup.json). The maintained cleanup helper
first failed physical-path preflight on a long historical evidence path;
deletion had not begun. Native PowerShell extended-length literal paths,
with exact OS-temp containment and all ancestor/descendant reparse checks,
then removed the 12,641 task files in 1,758 ms. Both bounded Java children
were already terminated and their log roots absent. No task process or
resource remains. Cleanup time is not a generation-job measurement.

## Decision and scope limit

The next action is the scoped harness change and this bounded screen.
There is no measured failing production workload yet from which to select a
new optimization. Do not start another profiler or revive the map/support/LU
experiments on this evidence.

After a real failure, retain the exact workload/phase and choose one measured
causal opportunity, using an existing applicable profile where possible.
After all selected variants pass, return to the roadmap's **approximately
30-part heterogeneous board plus held-out 20–40-part corpus** and focused
scale validation. That text does not require every integer package count.
Keep existing scale changes separate; expanded variants are new workloads.
Full qualification waits for readiness. This small screen cannot prove a
population tail, arbitrary-seed guarantee or Q30 acceptance. U06, U07, Q60 and
later milestones remain unstarted. No push or email is authorized or performed.
