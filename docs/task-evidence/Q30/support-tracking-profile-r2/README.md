# Q30 support-tracking investigation — BLOCKED / NOT ACCEPTED

2026-09-28. Evidence checkpoint on `codex/q30-multirail-qualification`, prepared
from `6e531049084b5224eb8cb923f95e46c127169d57`. Accepted production optimization
source remains `ffe9132`; no new production implementation is accepted here.
The live-support LU prototype and this instrumentation ran only in task-owned
copies. Exact source overlays below identify measured artifacts, not integrated
production code. No push, notification, scale expansion or later milestone.

The requested ownership repair already exists in `745d537`. A fresh source audit
confirmed `StagedFamilyCapability` exposes opaque family-owned staged resolution
and construction, `Rb30PlayerFamilyCapability` owns Q30 plan/routing/construction,
and `GenerationRequest`/`GenerationCoordinator` have no Q30/RB30 branch. Layout
uses shared medium constraints, without the old Q30 layout special case. The
fresh native registration/request/policy checks below pass. No duplicate repair
or architecture change was needed; pre-existing architecture edits are preserved.

Normal-player Q30 remains disabled. `NORMAL_MEDIUM_EXECUTION@2` remains
90,000 ms cumulative / 640 shared work units / 5,000 ms active operation. The
300,000 ms private measurement job is isolated and cannot qualify normal play.

## Result and measured boundaries

Every cold row still exceeds 90 seconds. The two unchanged controls are
98.846/99.106 s; the final prototype with profiling off is 102.361/102.737 s.
These observations do not demonstrate a speed benefit. They also do not isolate
the uninstrumented LU prototype's cost from dormant instrumentation/code shape.
No optimization is accepted or rejected as a mathematical design from this test.

The final OFF/ON and ON/OFF comparisons show profile-on slowdowns of 10.924 and
12.105 s. They are materially larger than the 0.260 s bracket-control range and
0.376 s prototype-OFF range in this sequence. This is an observed association,
not a causal overhead estimate or confidence interval. The ON range is 1.557 s.
Source/build differences, host activity, OS caches and the intervening rebuild
prevent treating controls as a correction for the candidate timings.

Support activity is now counted in the actual Q30 proof, but runtime stamp cost
is **INCONCLUSIVE**. Its timer lies too close to clock resolution. Thus this
investigation does not establish a dominant *removable* cost and cannot justify
refining or integrating the LU prototype. Factorization is the largest sampled
kernel category, which is useful direction for a further experiment, not proof
of achievable savings. The normal corpus needs margin, not one favorable row.

## Candidate, sequence and clock protocol

This is the same private **seed 10014, plan-4, 40-package** fixture as the prior
live-support trial. It is not the earlier seed-7 106.350 s stage profile with
79.421 s solver stepping / 20.070 s routing. Do not substitute the timings or
sum categories across those fixtures. Each cold run executes five hypotheses,
490 total work units and 390 hypothesis work units; warm uses 101/1 units.

`plan.json` records the initial sequence. After the first ON report exposed
timer bias and a source review found two profiler failure-path cleanup issues,
`plan-r2.json` froze the final comparison: control01, OFF04, ON05, ON06, OFF07,
control08. The initial control's exact source/build is unchanged at control08;
the intervening builds and preliminary OFF02/ON03 are explicitly retained.
All four final candidate rows use the same 1,344 source inputs and 1,527 runtime
inputs. Controls use 1,340 source / 1,525 runtime inputs. Input snapshots and the
maintained host runner hash match before/after each operation.

Each row starts a fresh Edge headless persistent context and task-specific
profile, with no previous browser/proof cache. The warm run intentionally reuses
the proved value on a distinct owner within the same case. OS caches are not
reset. GWT/JVM compilation and native gates occur outside timed browser rows.
Only one expensive campaign runs at a time. CPU observations record WMI nominal
3801 MHz endpoints and surviving-process CPU deltas. They do not measure turbo,
temperature or throttling; ended processes are absent. Concurrent ChatGPT/Codex
activity exists and cannot be causally corrected from these snapshots.

Application `elapsedMs`/proof/routing fields remain the existing wall-clock
fields. The temporary collector uses only `performance.now()`, with balanced
scopes and no wall-clock fallback. Host case/outer time uses a monotonic clock;
cleanup is separately reported. Wall-minus-monotonic host differences ranged
from about -13.4 to +5.1 ms, not evidence of the multi-second clock jump needed
to explain earlier variation. Browser case timeout is 600 s; the outer Python
sequence still has no independent subprocess deadline. That remains a limit.

| Row, in execution order | Cold ms | Proof ms | Routing ms | Warm ms | Host case s | Cleanup s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 01 control | 98846 | 69328 | 23266 | 29013 | 128.594 | 0.828 |
| 02 prototype OFF, preliminary | 102544 | 73062 | 23045 | 29367 | 132.657 | 0.812 |
| 03 profile ON, superseded timer | 114421 | 84296 | 22897 | 31006 | 146.172 | 0.844 |
| 04 prototype OFF r2 | 102361 | 72742 | 23195 | 29396 | 132.500 | 1.125 |
| 05 profile ON r2 | 113285 | 83198 | 22983 | 30125 | 144.203 | 1.125 |
| 06 profile ON r2 | 114842 | 84414 | 23270 | 30282 | 145.875 | 1.297 |
| 07 prototype OFF r2 | 102737 | 73096 | 23113 | 29657 | 133.141 | 1.047 |
| 08 control | 99106 | 69393 | 23453 | 29312 | 129.172 | 1.281 |

`analysis.json` and `timings.csv` retain exact values. ON05−OFF04 is +10,924 ms
cold / +10,456 proof / -212 routing / +729 warm. ON06−OFF07 is +12,105 cold /
+11,318 proof / +157 routing / +625 warm. The roughly 23 s current routing cost
remains material and unchanged; no new routing experiment is run. The earlier
P07 trial remains inconclusive. Neither route nor solver observations establish
a safe removable reduction or a reliable path below 90 s.

## Solver observations and accounting

`profiles.csv` includes every observed category in ON03/05/06, cold and warm:
coverage, exact invocation/work count, timed sample count, measured cumulative
milliseconds, measured mean, measured maximum, and explicit expanded estimate.
For `untimed` rows, zero time means **not timed**, not free. Sample maxima are
maxima only among sampled calls. Absent categories are not invented zero-cost
work. Rejected/retried work has exact counts but no independent duration.

The collector times all calls to coarse owners, analysis, construction, replay,
settling, source transitions, observations, signature capture and snapshot
restore. Kernel timing samples the first and every 64th step attempt, including
all nonlinear trials within the selected step. Stamp timing separately samples
the first and every 4096th call; these deterministic samples are not statistically
independent. All counters run on the full workload;
double counters are guarded below 2^53 and are exact integers here. Category
timings are inclusive/nested and must **not** be summed into an additive budget.

| Cold category | Calls per run | ON05 seconds | ON06 seconds | Coverage |
| --- | ---: | ---: | ---: | --- |
| Solver owned loop | 5440 | 81.784 | 83.063 | all, inclusive |
| Analyze/setup | 501 | 1.611 | 1.614 | all |
| Matrix/RHS reset | 1350440 | 7.663 | 7.414 | expanded sampled estimate |
| Element doStep / nonlinear assembly | 1350440 | 15.927 | 16.201 | expanded sampled estimate |
| Factor | 742391 | 34.205 | 35.010 | expanded sampled, includes domains |
| Solve/back-substitution | 742391 | 8.095 | 8.389 | expanded sampled estimate |
| Accepted-step publication | 608049 | 2.259 | 2.304 | expanded sampled estimate |
| Domain preparation | 742391 | 2.100 | 2.049 | sampled, nested inside factor |
| Support comparisons | 742391 | 0.370 | 0.389 | sampled, nested inside domains |
| Support creation | 521 | 0.0275 | 0.0279 | all |
| Support invalidation | 521 | 0.0005 | 0.0007 | all |

Both final ON rows count 608,059 attempts, 608,049 accepted steps, 1,350,440
nonlinear trials, 742,391 factors/solves/domain preparations, and ten retries /
timestep rollbacks. Matrix/RHS restoration copies 9,661,990,182 cells; the element
loop performs 89,980,305 `doStep` invocations. There are 118,624,334 runtime
support stamps, 28,610 new coordinates, 125,985 union attempts, 85,417 merges,
3,595,570 original cells scanned and the same number of validation cells scanned.
Domain preparation visits 62,391,618 rows and columns, and 124,783,236 group slots.
Five replay/install owners and five snapshot restores remain in the proof.

The enclosing loops, repeated healthy/faulted/repair/retest settling, 185 DC
observations, control checks and source transitions remain visible in the full
CSV. For example, ON06 source signatures take 3.6 ms across 420 calls; replay/
install takes 513.3 ms across five calls; snapshot restore takes 80.8 ms across
five calls. These inclusive observations do not license removing required
electrical settling, proof, source-currentness checks or lifecycle work.

### Rejected fine-grained stamp attribution

ON03 timed every stamp within selected kernel steps. Its expanded `doStep`
estimate was 88.772 s, exceeding the fully timed 83.036 s solver loop. This is
retained as evidence of observer/sampling bias, not usable phase attribution.

R2 reduced stamp timing to 28,962 periodic samples out of 118,624,334 calls.
ON05 measured only 14.4 ms in those samples, versus 10.4 ms for the paired empty
timer scope; ON06 measured 15.4 versus 9.8 ms. The naive expansions 58.98/63.08 s
exceed the enclosing sampled `doStep` estimates 15.93/16.20 s. Both raw expansions
and calibration-subtracted expansions are **rejected as cost claims**. The raw
fields remain unchanged for audit. This does not prove that stamp tracking is
cheap, expensive or dominant. Lower-frequency support creation/invalidation
measurements do not resolve the repeated runtime stamp cost.

## Fresh gates and review scope

| Check | Result and scope |
| --- | --- |
| Exact reconstruction | PASS: all 1,340 current baseline inputs match prior control audit; six archived LU overlays reconstruct 1,343-input prototype; final profile adds one helper and changes 14 measured source files |
| Control JDK8/GWT build | PASS: actual `scripts/build.ps1`, five permutations 87.817 s, link 2.080 s |
| Final r2 JDK8/GWT build | PASS: five permutations 77.816 s, link 1.511 s |
| Native ownership/policy | PASS: registration 15, request 321, measurement budget 25, normal execution policy 299 assertions; host total 41.269 s including cleanup |
| Final r2 native support/factor | PASS: 1661/2148 assertions; host total 32.941 s including cleanup; separate native cleanup duration not captured |
| Final r2 compiled A07 / normal disabled | PASS: 1.312/0.500 s operations, cleanup recorded separately in host receipt |
| Maintained A07 reader | PASS plus three corruptions rejected on final r2 compiled output |
| Actual Q30 measurements | PASS all eight private cases, restored owners, input audits and host cleanups |
| Existing independent strict proof reader | PASS plus 43 corruptions rejected per row |
| Complete non-timing parity | PASS all eight full projections equal after only declared timing fields and the named profiler metadata are excluded |
| Profiler metadata reader | PASS ON03/05/06, cold/warm; ten corruptions rejected per report; same worker wrote profiler and reader, so this is not an independent oracle |
| Normal 90 s gate | BLOCKED: all measured cold rows exceed it; prior normal deadline failure retained |
| Full normal/scale matrix | NOT RUN while timing fails; no resumed scale expansion |
| Visible player-flow screenshots | NOT APPLICABLE: no player-flow source change |
| Injected profiler snapshot/parked-cleanup failure tests | NOT RUN; source-only review plus successful actual cleanup does not prove those exceptional paths |

Initial, superseded candidate gates are retained separately: GWT five
permutations 78.956 s / link 1.355 s; native five suites including A07 execution,
Q30 temporal work and measurement budget; compiled A07/normal disabled. They
are not claimed as final-r2 source gates. The fresh control compiled canaries
also pass. All host JSON reports retain operation and cleanup fields separately.

Optional independent read-only review audited the family boundary and private
profiler lifecycle. It found collector enablement surviving a parked startup
failure and snapshot exceptions bypassing cleanup. The temporary verifier now
clears profiling before either parked path and in a `finally`; errors join the
existing failure and common owner-cleanup path. The same reviewer accepted the
repair by source inspection. Another read-only reviewer checked final timing
interpretation, rejected stamp extrapolation and confirmed the blocked outcome.
Final read-only packet review found no substantive blocker and requested the
periodic-sampling wording clarification above. Root acceptance remains
evidence-only. No independent performance rerun occurred.

## Reproduction and retained failures

Use a hash-matching copy of the preserved plan-4 baseline, outside the repo in a
unique OS-temp directory. It includes unaccepted unstaged inputs and therefore
cannot be recreated by checking out `6e53104` alone. The previous packet's
`live-lu-support-r1/source/source-audit.json.gz` lists the baseline and prototype;
apply its six exact overlays, then this packet's 14 `source/overlays.json`
entries for final r2. `source/final-inputs.json.gz` binds every final source
input. The misleadingly named `pre-fix-source-audit` captures the intermediate
state after the lifecycle repair, before stamp sampling changed. ON03's runtime
manifest and the three changed-source backups bind its actual earlier inputs.
The sources are measurement provenance; do not overlay them into production.

The actual commands used the maintained owners, with paths sanitized here:

```powershell
# In each task fixture, one expensive job at a time; JDK 8u502-b07.
& .\scripts\build.ps1 -JavaHome $jdk8
& .\scripts\verify-current-contracts.ps1 -JavaHome $jdk8 -Suite StagedFamilyRegistrationContractTest,Q30GenerationRequestContractTest,Q30GenerationMeasurementBudgetContractTest,Q30NormalExecutionPolicyContractTest -ReceiptOutputPath $receipt
& .\scripts\verify-current-contracts.ps1 -JavaHome $jdk8 -Suite LuStructuralSupportContractTest,LuStructuralFactorizationContractTest -ReceiptOutputPath $receipt
# Python 3.12.14; task-local Playwright 1.57.0, Edge selected by maintained runner.
& $python -B tests/browser/compiled_attribute_acceptance.py $fixture $newTempOutput $spec
```

The saved `*-spec.json` and `runs/*/spec.json` contain exact queries. The Q30
route is `tsjVerifyQ30=true&tsjQ30Coordinator=true&tsjQ30Seed=10014`; ON adds
`tsjQ30TrackingProfile=true`. No injected application/controller calls substitute
for the actual coordinator. Archived procedure payloads use `<Q30_REPO>`,
`<TASK_TEMP>`, `<RECOVERY>`, `<DESKTOP_REPO>` and `<USERPROFILE>` path tokens;
substitute them before replay. `provenance.json` distinguishes exact payloads
from path-sanitized records. Packet-local Git attributes preserve evidence bytes
across Windows newline conversion. Raw application reports and source overlays are
exact gzip payloads. Browser caches, libraries, private paths and uncontrolled
console dumps are excluded. Runtime and source manifests retain content hashes.

Failures/limitations retained rather than converted to PASS:

- The user confirmed a concurrent cleanup task deleted the initial and older
  retained Q30 Temp fixtures. Initial control compilation was observed to finish
  (80.611 s / link 2.168 s), but its outputs and first canary receipt disappeared.
  That canary's exit zero is **UNVERIFIED**, not a passing retained gate. No repo
  source was lost. A hash-pinned reconstruction and one retry succeeded; the
  replacement fixtures survived all measurements. Older deleted scratch cannot
  be certified or recovered from this packet beyond previously archived bytes.
- The old Store Python launch failure is not declared repaired. Bundled Python
  plus task-local packages ran the selected host path and fresh canaries. No
  global interpreter, browser or runtime settings were changed.
- A reader was invoked before OFF04 produced its report and failed with missing
  file. The later completed row passed normally; this is an invocation failure.
- Initial packet audit detected the projection hash serializer used LF while
  the original Windows writer stored CRLF. The audit now reproduces that byte
  convention and still compares the complete parsed projections. No proof
  exclusion, source, raw report or electrical expectation changed.
- Per-stamp cost, precise profiler overhead, isolated tracker-versus-scan cost,
  full corpus timing margin and direct exceptional-lifecycle tests remain open.

`audit_packet.py` verifies the inventory, sanitized provenance, exact raw report
hashes, final/pre-fix source overlays, unchanged strict positive reader and all
complete non-timing projections. Run `python -B audit_packet.py`; `--seal` is
reserved for rebuilding the packet inventory after an intentional evidence edit.
`audit-result.json` is excluded from its own inventory to avoid self-reference.

## Next bounded action and resources

Leave Q30 blocked and normal-disabled. If this investigation resumes, compare
private full-LU/no-tracker, full-LU/tracker-only, restricted-LU/tracker and
profile-on modes using aggregate phases and counterbalanced repetitions. That
can isolate tracker overhead from scan savings without unreliable per-stamp
timer expansion. This next experiment is **NOT RUN**. No further optimization,
scale expansion, U06, U07 or Q60 has started.

`preservation.json` audits every pre-existing changed file: 96 on the unrelated
desktop checkout and 172 in the Q30 worktree, all byte-unchanged. The Q30
worktree retains its 26 tracked plan-4 edits and pre-existing untracked source,
evidence and caches. This checkpoint changes only the report and this packet.
Browser/server hosts finished and all owned cleanup receipts pass. The exact
task Temp root (6,624 files) and small recovery directory (129 files) were
removed after evidence verification, containment/reparse checks and confirmation
that no live process referenced them. Cleanup took 1.098/0.031 s and is recorded
separately in `resource-cleanup.json`; no other Temp or repository data was removed.
