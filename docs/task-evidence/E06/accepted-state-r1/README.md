# E06 accepted-state boundary evidence

Status: **ACCEPTED for the declared E06 boundary and finite regressions.** Base
`9c74c46bdcf99dc62d3b7f7d35b71eb5ba9b3acf`, branch `codex/e06-accepted-state`.
The immutable [plan](PLAN.md) predates measurement. This packet does not grant
playable RB100 admission or broad converter fidelity.

The E06 element stages its validated receipt, next controller state and numerical
predictor, then commits through the existing executor acceptance callback after
events and boundary checks. Rejection preserves committed memory. Physical current
accessors expose the validated provisional vector for CircuitJS wire/scope sampling;
semantic receipt getters retain accepted values. Pending values retire on retry or
reset. Simulation time, scopes and earlier callbacks are not rolled back.
Stored predictor memory grants no current executor observation or reuse authority.
Dependency interpretation advances to v21; model dump v7 and public save schema
remain unchanged, with fresh regeneration/proof on public restore.

## Recorded failures and corrections

- [Native1](native-attempt1-compile-fail.json): compile FAIL, new snapshot field typo.
- [Native2](native-attempt2-envelope-fail.json): FAIL, reverse-input fixture used an
  absolute voltage despite a floating IN- terminal. The corrected injection is
  differential; the actual electrical envelope assertion remains.
- [Native3](native-attempt3-overflow-precondition-fail.json): FAIL, invalid overflow
  fixture setup. It now settles actual shutdown and injects finite extreme output
  that overflows derived power; no private controller state or new ceiling.
- [Native4](native-attempt4-queue-reset-fail.json): FAIL, recovery attempted to reuse
  an intentionally failed event queue. The fixture now proves explicit reset is
  required, resets its own queue at advanced simulation time, then retries.
- [Focused native5](native-focused-pass.json): PASS463 assertions/five actual solver
  cases, including signed IN+ wire current, six post currents/KCL, scope sampling,
  rejection/recovery/reset and stored memory versus executor authority.
- [Combined native6](final-native-pass.json): PASS eight focused suites; full native
  matrix/oracles NOT RUN. Native7 also passed but its last cleanup interval overlapped
  the next build launch. [Isolated native8](final-native8-pass.json) supersedes it,
  with the later [consumed-input audit](native8-final-reuse-boundary.json) below.
- [Compiled attempt1](compiled-attempt1-startup-timeout.json): INFRASTRUCTURE_FAILURE,
  exit2, no required first-canary evidence by90.150s; helper156.009s, resource/aggregate
  cleanup PASS47.793s. No route was accepted. The old reader did not retain the last
  DOM report on timeout, so the exact cause remains unproven. Source review
  found this new debug route used synchronous RB56 bootstrap, a candidate cause that can block deferred
  work. Only the selected E06 route now schedules the existing coordinator
  asynchronously. That source finding does not retrospectively establish the timeout cause. Failure
  reports now retain bounded route reports/events for diagnosis.
- [Compiled attempt2](compiled-attempt2-typed-flags-fail.json):
  INFRASTRUCTURE_FAILURE/exit2 despite a retained `PASS:e06-acceptance-canary`
  terminal and accepted-memory/restoration witnesses. Two accumulated GWT boolean
  flags serialized as numeric `1`; the unchanged strict reader rejected their
  types. Helper150.754s, deadline90.050s, aggregate cleanup PASS43.540s. The raw
  [canary payload](compiled-attempt2-canary-raw.json) remains a failed qualification
  input. Explicit JSON boolean serialization corrects the producer. The complete
  raw5487-event list stays in OS temp; controlled evidence retains the final20
  plus the count and raw report hash. No reader assertion or budget was relaxed.

[Earlier builds](pre-async-source-builds.json) and their six inventories are retained
under Player2/Developer1 names; they do not qualify the later bootstrap fix.
Player3/Developer2 are retained in [pre-serialization builds](pre-typed-json-source-builds.json).
Player1 also predates a stricter Developer cleanup guard. The stricter guard only
restores static model defaults while the exact predecessor and closed authority
remain owned; stale cleanup must not alter a successor.

## Final gates and limits

[Final JDK8/GWT builds](final-source-builds.json) PASS Player4/Developer3, five
permutations and357 full output files each. Isolated native8 PASS55.850s/cleanup;
its numerical model, solver, fixtures, eight contract suites and native runner
inputs remain unchanged after the Developer-only JSON and failure-diagnostic
changes. That scoped audit does not qualify the changed compiled reporter.

[Actual Player privacy2](compiled-player-privacy-final.json) PASS75.741s,
cleanup49.001s. [Final Player reuse](player-privacy-final-reuse.json) audits357/357
byte-identical Player4 output files and all58 unchanged reader functions. Its
original cleanup is not renewed by the audit. No visible Player flow/layout
change is claimed; previous repair screenshots retain their original candidate.

Strict WinPS5 [reader3](strict-readers-3.json) PASS120 positive/negative E06
assertions plus existing Q100/A04 fixtures; synthetic data is not runtime proof.
[The controlled evidence reread](controlled-evidence-reader.json) also accepts all
ten actual reports and rejects the retained actual numeric-boolean failure.
[Compiled E06State3](compiled-e06-state-final.json) PASS all10 routes, operation346.765s,
aggregate cleanup57.482s under120s, helper404.548s/exit0. Both accepted-state canaries
and all eight authored references passed the unchanged strict readers. Full report
payloads are retained; event lists are bounded to their final20 with original counts
and raw hashes. [Current regression](compiled-current-final.json) PASS all9 routes,
helper242.113s and cleanup PASS; that gate supplies no aggregate cleanup duration.
[Fresh v21 normal MEDIUM regression](normal-medium-final-finite3.json) PASS only the
three predeclared development roots, helper167.088s, exact source/input audit and
resource cleanup PASS. The [frozen plan](normal-frozen-plan.json) binds both final
build inventories; its historical baseCommit records the R12 plan lineage, while
its sourceBinding identifies this actual candidate. Exact actual captures0/1/2
remain in this packet. No blind partition was released.

All new gate-owned processes and listeners closed. Task-owned raw/frozen evidence
is retained in OS temp. The two older inactive failed profiles/claims remain
unrecovered under their original safety contract; no broad cleanup occurred.
The independent source and reader reviews found no material blockers within their
recorded scope; compiled execution subsequently caught the GWT serialization
defect above. The root reviewed the integrated diff and actual gate evidence.
CI NOT RUN; self-hosted Windows runner registration remains a recorded prerequisite.

The native E06 mechanics matrix retains its nominal-fidelity NOT QUALIFIED result.
No full2-by-31 or30-root renewal, physical100, save-capacity increase, long-session,
modest-host, public beta or human-repair claim. The96-part save cap remains. The
published Q100-1 v20 acceptance stays attached to its recorded commit. HARD and
PSYCHOTIC remain unavailable; protected HARD data is outside this task.

After E06 acceptance, the next proposed eligible substep is an authored RB100
physical feasibility pilot under the current single-face physical policy, with
independent copper/net/access checks and budgets frozen before measurement.
