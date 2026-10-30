# Q100-1 private electrical pilot

Status: **Q100-1 ACCEPTED AND PUBLISHED.** Commit
`9c74c46bdcf99dc62d3b7f7d35b71eb5ba9b3acf` was normally pushed to
`origin/codex/q100-constrained-pilot` and the exact remote SHA verified.
Completion Gmail `1a18d49856ea7daf` was sent once. The final compiled
matrix passes all62 electrical cases and aggregate cleanup98.455 s under120 s.
Final-source builds, native contracts, fresh Current/lifecycle checks, audited
Player artifact/privacy evidence, real Player repair and the finite normal
MEDIUM regression close the declared private pilot. Full playable Q100 remains
in progress. Earlier failures retain their original results.

Base: `a1352b5caeb78e069606f84a569e14670f28d807`.
Branch: `codex/q100-constrained-pilot`.
The [frozen plan and amendments](PLAN.md) retain the pre-measurement decisions and
earlier failed interpretations. The [current checkpoint](../../../CODEX_TASK_REPORT.md)
identifies active resources and the next action.

## Scope and interpretation

The authored RB100 graph has exactly 100 purposeful physical package declarations,
with actual CircuitJS backing and independently checked terminal/source/load
mapping. External loads, wires and converter internals do not add board packages.
It uses the exact signed-long seed `9007199254740993`, plan 1/model 4, with
`SHARED_HYSTERETIC_LINEAR` and `SEPARATE_DIRECT_SWITCHING` structural variants.

Model 4 supplies contact/DC loads from the external LOAD12 domain and keeps
converted RAIL12 for control power and relay coils. The source-limiting canary
uses an actual temporary 10-ohm load: LOAD12 reaches about 5.000065 V/0.500007 A
while the control rails remain healthy, then recovers near 12 V after release.
The imported RB56 plan and its 20 power declarations remain unchanged.

Reference protocol v3 selects the existing owned temporal API. Each unit has at
most 64 real accepted steps, fixed min/max/current dt and an exact accepted-step
count plus time-delta witness. It declares a 5000 ms operation deadline and the
existing 500 ms inter-Newton-trial deadline; the latter is not a terminal step
duration cap. The outer limits remain 5 s/unit, 90 s/case and 640 work/case.
Healthy pairs use 100/50 us. Every fault case uses 50 us, all six FAULT contexts,
same-graph fault clearing and all six REPAIRED contexts. Each context records
14 real solver values. The pair comparison covers nine rail/output values with
the original 0.15 V maximum difference.

## Current gates

| Gate | Result | Evidence and boundary |
| --- | --- | --- |
| Declaration/mapping and actual native healthy references | PASS | [Four cases](native-healthy-model4-reference-v3.json), 2787 declaration and 24294 electrical assertions; pair maxima 0.018191/0.010428 V |
| Complete native fault/repair reference | PASS | [62 cases](native-faults-model4-reference-v3.json), 254091 assertions; 24000 steps/380 batches/393 work per case; every cleanup PASS |
| Native reference/model source closure | PASS, scoped electrical reuse | [Final consumed-input audit v4](native-final-reuse-boundary-v4.json) and [eight-suite model audit](native-models-5-reuse-audit.json); does not reuse old host cleanup as current acceptance; full native matrix/oracles NOT RUN |
| Final-source JDK8/GWT Player5 and Developer15/16 | PASS | [Build identities, source pins and sizes](final-source-builds-v4.json); five permutations/full357-file inventory each; Developer16 exactly reproduces Developer15 |
| Dependency v20 cache/private-replay rejection and save/identity native contracts | PASS | [Five fresh suites](dependency-v20-native-guards.json), including prior-key cache miss/reuse rejection, actual private replay parser rejection and exact 30 identity-only manifests |
| Final compiled healthy, preflight, failure/cancel, input lock and debug-off privacy | PASS | [Lifecycle attempt5, all11 routes](compiled-lifecycle-strict-privacy-final.json), including wheel/change and two real debug-off DOM leak/rejection/removal canaries; operation97.703 s, cleanup53.421 s, helper151.429 s |
| Final Player artifact and debug-query privacy | PASS, scoped functional reuse | [Player5/gate7](compiled-player-final.json), ordinary ticket entry, full attribute collector, two real DOM leak/rejection/removal canaries; [unchanged Player input/function audit](player-final-reuse-boundary-v1.json) closes reuse after debug-off/host-only changes; its earlier cleanup is recorded evidence, not current-host acceptance |
| Strict reader/oracle synthetic contracts | PASS | [Fresh PS5 reader result v6](final-synthetic-readers-v6.json), including14 current/stale/malformed A04 fixtures and strict debug-off attribute negatives; real Developer debug-off DOM canaries PASS separately; in-memory fixtures are not runtime physics proof |
| Final visible normal Player repair | PASS, finite | [Player5 interaction record](visible-player-final.json), three inspected screenshots, actual built-in Browser input, verified tab/server closure; earlier Player4 cancellation was not refreshed on Player5 |
| Complete final compiled 2-by-31 fault reference | PASS | [Attempt3](compiled-faults-final.json), all62 cases; operation2334.747 s, cleanup98.455 s, natural close10.169 s, helper2433.474 s; independent reader and resource/process exit PASS; [case metrics](compiled-faults-final-metrics.json) |
| Focused real-host cleanup and safety boundaries | PASS, finite | [HTTP.sys/512 files](cleanup-canary-attempt2-pass.json): 41.084 s; [TCP/2048 files](cleanup-canary-attempt3-tcp-pass.json): 61.673 s, natural close 9.977 s under 15 s; real helper and junction negatives PASS |
| Final 62-artifact real-host cleanup workload | PASS after retained baseline failure | [Baseline144.404 s FAIL](cleanup-evidence-baseline-fail.json); [post-fix117.936 s PASS](cleanup-evidence-fixed-pass.json),254 checks,62 registered JSON files/6934 bytes plus2048 profile fixtures, real helper/profile/artifact reparse negatives and final owner absence; HTTP.sys only;2.064 s stress-canary headroom is no host-variance guarantee |
| Live preview listener identity | PASS | [63 checks](cleanup-live-listener-identity.json), actual TCP transport, three 219–231 ms identity proofs under 500 ms, negative host/identity/deadline cases and cleanup PASS |
| Final compiled Current corpus | PASS | [Fresh attempt8, all9 routes](compiled-current-refreshed-final.json), helper241.125 s, cleanup/process exit PASS on the corrected reader/module; final Developer artifacts unchanged |
| Final compiled A04 correspondence | PASS | [Attempt3, all3 routes](compiled-a04-final.json),3150 contract/839 runtime assertions, six generator6 cases, actual adjacent-node checks and forced failure/debug-off; cleanup PASS |
| Final native operational, inventory, terminal and geometry contracts | PASS | [Fresh eight-suite gate](a04-terminal-upstream-native.json): A04 actual617/pure680/physical5270, resistive241, controlled1177, A03 actual401, copper26, endpoints320; helper69.568 s/cleanup PASS; detached projections do not prove solver nodes |
| Three normal RB56 development-root regressions | PASS, finite | [Final finite-three record](normal-medium-final-finite3.json), exact seeds and source/artifact bindings; browser/wrapper/strict reader exits0; input audit/cleanup PASS; no30-root renewal or normal release claim |
| Fresh RB56 manifest identity regression | PASS, identity only | [Exact 30 rows](rb56-manifest-identity-regression.json), no circuit construction or new electrical population qualification |
| CI | NOT RUN | Self-hosted Windows runner registration remains a prerequisite |

Native fault execution took 224.382 s for the electrical phase; the complete
helper took 275.320 s including compilation/harness and cleanup. The worst case
was CIN33_SHUNT/switching at 9065 ms with a 519 ms maximum completed unit. This
is within temporal-v3 limits and exceeds the earlier synchronous batch deadline;
the operation choice is material. Operation, harness and cleanup costs are not
interchangeable. These are measurements on this host, not portability evidence.

The final compiled matrix uses24000 accepted steps/380 batches/393 work units
and twelve observations (six FAULT plus six REPAIRED) per case. Its worst case
is CIN33_SHUNT/switching:80.842 s operation,4.468 s maximum unit, within the
unchanged90 s/640work/5 s limits. The outer2334.747 s matrix operation includes
browser capture/harness work and is separate from98.455 s cleanup. The frozen
6000 s matrix and120 s aggregate cleanup limits remain unchanged.

The [final Player5 visible check](visible-player-final.json) observes an unrepaired
LED retest failure, actual power isolation, removal, Shop acquisition, tray
selection and installation, power restoration and a successful customer retest.
Three screenshots were inspected and the owned tab/server were closed. A delayed
launcher poll saw the already-stopped PID and emitted errors; its exit0 is not
ownership or acceptance proof. Independent live ownership capture and the
maintained stop proof are recorded separately. The narrow browser header clips
the first failure message in its screenshot; the observed accessibility text
records it. No injected controller calls count as player input.

The [earlier Player4/v20 check](visible-player-check-v20.json) also cancelled a normal
MEDIUM request during measurement proof and observed the repaired/lit predecessor
return. That result predates the final constructor/receipt corrections and is
not refreshed on Player5. Exact graph/owner restoration belongs to the separate
final private lifecycle canaries.

## Failures retained

Models 1/2 and early model 3 failed actual loop analysis or startup convergence.
The model-3 [full fault attempt](native-faults-attempt1-summary.json) was 50/62,
with 12 failures. Model-4 [earlier full attempt](native-faults-model4-before-flat-fix.json)
was 61/62. The [v2 50-us full attempt](native-faults-model4-reference-v2.json)
also failed 61/62, despite a passing selected diagnostic. None was relabeled.
Source pins and exact failed-source snapshots remain retained.

The [first compiled matrix](compiled-faults-attempt1-cleanup-budget-fail.json)
has62 electrical passes but138.930 s cleanup FAIL. [Attempt2](compiled-faults-attempt2-cleanup-fail.json)
also has62 electrical passes but124.861 s cleanup FAIL, despite resource release.
The actual62-artifact/2048-file canary then reproduced144.404 s cleanup FAIL.
Removing only the redundant pre-view census preserved both fresh manifest-action
checks and all profile/process/listener/job boundaries. The same host canary
passed117.936 s, followed by fresh lifecycle5, Current8 and complete matrix3.
The earlier lifecycle2 natural-close15.034 s failure and its retained resources
also remain failed evidence; no old aggregate was relabeled.

Current-corpus failures remain preserved: the [missing operational owner](compiled-current-attempt1-a03-fail.json),
[negative-fixture admission ordering](compiled-current-attempt2-task47-fail.json),
[unregistered resistance targets](compiled-current-attempt4-resistance-target-fail.json),
[obsolete copper branch-end assumption](compiled-current-attempt5-copper-endpoint-fail.json)
and [pre-service element-count assumption](compiled-current-attempt6-a04-count-fail.json).
The old connector literal was corrected only after an independent source-derived
ownership/coordinate analysis, with its [native failures retained](a03-connector-oracle-native-failures.json).
The exact final inventory is the finished construction receipt plus existing
component, connection, pad-anchor and docking owners; no count slack was added.
The [support-resistor terminal failure](compiled-a04-attempt1-support-terminal-fail.json)
and [generator-version reader timeout](compiled-a04-attempt2-reader-version-timeout.json)
remain failed attempts. The final A04 checks use exact physical secondary paths
and declared fault-switch adjacency, then separately check settled solver nodes.
The reader requires exactly generator6 and rejects stale/malformed versions.

The [first normal regression launch](normal-regression-attempt1-runtime-fail.json)
failed before cases because the selected Python3.12 lacked Playwright. Its
temporary server cleanup passed. The finite-three retry used the existing
Python3.13.14/Playwright1.57 runtime, identical frozen plan bytes and unchanged
budgets; no dependency was installed and the old result was not relabeled.

The adaptive-v0 study remains a [failed diagnostic](adaptive-study-failures.json)
and its source-copy adapter is retired. Selected diagnostic completion does not
emit a full-population qualification marker. Pending trace guards make that
distinction fail closed at the native driver entry. The [actual guard checks and
selected traced diagnostic](native-diagnostic-guard-canaries.json) passed their
limited contracts; the traced run remains unqualified for a full population.

The focused [cleanup canary attempt 1](cleanup-canary-attempt1-fail.json) failed
its extra custom CDP readiness check and then the unchanged natural-close deadline.
Its profile and CDP claim remain retained; fresh browser-root absence, typed
listener absence and profile quiescence do not authorize their release. The narrow
recovery contract rejects its attempted-close receipt, absent root and two-lease
preview-owned manifest. No manual release was attempted. Later host canaries use
the maintained factory's about:blank attachment and qualify their separate runs.

Player artifact [attempts 1/2](player-artifact-attempts-1-2.json) retain the PS5
receipt-count error and four surviving query-name markers. The complete failed
module was copied outside Git and verified against all 357 inventory hashes.
[Attempt 3](compiled-player-attempt3-timeout.json) passed the artifact scan but
timed out on a stale button-label readiness oracle; cleanup passed. Attempt 4
entered the current customer ticket through actual input and passed. Earlier
wrong-host browser/cleanup failure evidence remains preserved separately from
its verified manual cleanup and subsequent PS5 host-path smoke PASS.

## Limits and follow-ups

This pilot does not provide normal catalog/admission authority, 100-package PCB
routing/access, production D01 uniqueness, playable RB100, save/replay capacity,
endurance, human diagnosis/repair qualification, all-seed support, modest-host
operation or public beta. The 96-part save cap is unchanged. HARD/PSYCHOTIC remain
unavailable and no HARD gate was run. The [read-scope audit](protected-read-scope-audit.json)
retains an independent reviewer's broad evidence search and its limits: only
Q100/A01 excerpts were displayed; metadata enumeration found no known protected
HARD payload there. Protected fixtures were not intentionally opened/displayed,
but exhaustive filesystem-read exclusion is not certified. No result depends on
that search, and protected data was not opened to investigate the uncertainty.

E06 broad nominal fidelity remains unqualified. Static review also found its
pre-existing receipt/controller/predictor update precedes executor acceptance.
Stage those values and add real envelope/later-callback/event/retry/reset/private
restoration canaries before E06-backed physical/admission claims. The current
finite successful electrical sequences do not establish those failure boundaries.

Independent reader review identified unfamiliar-attribute privacy coverage and
diagnostic-trace qualification gaps. Pending patches were integrated after the
frozen matrix ended and exact source-base comparison; fresh synthetic checks pass.
Final real Player privacy positive/negative checks and lifecycle checks now pass.
Review additionally found that the Q100
debug-off reader did not reject unfamiliar root developer attributes. The
strict reader correction, independent synthetic fixtures and a fresh lifecycle
with the added real DOM canaries now pass. The manifest change
removes only a full census preceding an in-memory projection. Exact path guards,
fresh full censuses immediately before writing and committing, and all process,
profile, listener, lease and job proofs remain. The real62-artifact workload
reproduced the old cost failure and passes after that change; the subsequent
fresh complete electrical matrix also passes. Legacy general
`verify-browser -NormalPlayer` has obsolete catalog/tray/retest controls and was
NOT RUN; its result is not claimed. The current public Player flow was checked
directly. This packet preserves failures, exact tested candidates and limitations.
