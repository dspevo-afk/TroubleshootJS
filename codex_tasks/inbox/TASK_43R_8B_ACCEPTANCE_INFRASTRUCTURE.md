# TASK 43R-8B — TASK 43 ACCEPTANCE-INFRASTRUCTURE CLOSURE

Repository: `dspevo-afk/TroubleshootJS`

Working branch: `codex/task43-recovery-integration`

Accepted 43R-8A checkpoint:
`c952d8ea470972a4ce8b857387313645145608e7`
`Complete Task 43R-8A recovery`

Accepted pre-Task-43 baseline:
`c0eb342b29165b8218a4b97b16fb8554fee42aff`

Frozen recovery checkpoint:
`239b52f0c1fd36eac5ccb65ad7dbe559474c1800`

## IMPORTANT: THIS INBOX COMMIT IS HANDOFF-ONLY

This prompt is being committed to the recovery branch after accepted 43R-8A.
Therefore HEAD will be newer than `c952d8ea470972a4ce8b857387313645145608e7` when you start.

Before doing anything:

1. Verify the branch is exactly `codex/task43-recovery-integration`.
2. Verify `c952d8ea470972a4ce8b857387313645145608e7` is an ancestor of HEAD.
3. Compare `c952d8ea470972a4ce8b857387313645145608e7..HEAD`.
4. The only expected newer repository change is this handoff file:
   `codex_tasks/inbox/TASK_43R_8B_ACCEPTANCE_INFRASTRUCTURE.md`.
5. Verify the working tree is clean.
6. Verify Task 43 is still `RECOVERY IN PROGRESS`.
7. Verify 43R-8A is accepted/complete.
8. Verify Task 44 remains blocked/unstarted.
9. Verify no other write-capable Task 43 agent is active.

If any genuine unexpected repository mutation exists, STOP and report a PROCESS BLOCKER.
Do not repair branch history casually.
Do not merge master.
Do not force-push.
Do not begin Task 44.

---

# PURPOSE OF 43R-8B

43R-8B is a narrow acceptance-infrastructure cleanup milestone.

It exists to close exactly two known holes exposed by the first 43R-8 investigation:

1. `PcbLayoutDeveloperVerifier` still contains two stale NPN deferred-failure paths from already-completed recovery frontiers. Those paths can convert real NPN geometry failures into a false `PASS:layout`.

2. Task 43 still lacks an integrated forced-negative shell canary proving that a deliberate Task 43 verifier failure propagates all the way out to a NONZERO process exit.

43R-8B is NOT the final integrated Task 43 acceptance run.

After 43R-8B is independently accepted, STOP. A fresh final 43R-8 acceptance pass will then rerun the complete Task 43 matrix against the repaired acceptance infrastructure.

Do not mark Task 43 complete in this milestone.
Do not unlock or begin Task 44 in this milestone.

---

# HARD AGENT CONFIGURATION REQUIREMENT

EVERY SINGLE SUBAGENT you spawn for this task must use:

- model: `gpt-5.6-luna`
- reasoning: actual `MAX`

Not Extra High.
Not High.
Not a worker merely named “MAX.”
Actual MAX reasoning.

Verify the real reasoning setting when dispatching each agent.

---

# HARD ORCHESTRATION STATE MACHINE

## STATE A — READ-ONLY INVESTIGATION

Allowed:
- spawn the required read-only investigators;
- receive completed reports.

Forbidden:
- spawn a writer;
- edit the repository;
- begin implementation yourself;
- drip-feed partial investigator findings to a waiting coder.

Transition to STATE B only when ALL investigators are complete.

## STATE B — ARCHITECT RECONCILIATION

Allowed:
- read every investigator report in full;
- reproduce the relevant behavior;
- reconcile contradictions;
- freeze one exact implementation design;
- freeze one exact allowed-file list;
- freeze the validation commands.

Forbidden:
- spawn a writer before the design is frozen and reviewed.

Transition to STATE C only after the frozen design is approved by the primary architect.

## STATE C — SINGLE WRITER

Only then may ONE write-capable worker be spawned.

If a writer is spawned while ANY investigator is still working:

- cancel the writer;
- classify it as a process failure;
- do not salvage partially informed edits;
- finish investigation and reconciliation first;
- create a fresh writer afterward.

Do not send a coder incremental “new findings just came in” messages.

---

# IDLE POLICY

After delegating a subagent task, let the agent finish.

Do not repeatedly:
- ask for status;
- poll processes;
- inspect partial diffs merely to watch activity;
- inspect timestamps as liveness checks;
- interrupt workers without new evidence;
- narrate repetitive status updates.

Completion-driven orchestration is preferred.

Treat subagents professionally. The primary architect owns orchestration mistakes.

---

# ONE-WRITER POLICY

Only one write-capable agent may exist at any time.
Read-only investigators/reviewers make no repository edits.
The primary architect must not quietly become a second coder.

---

# PHASE A — EXACTLY TWO READ-ONLY INVESTIGATORS

NO WRITER MAY EXIST DURING PHASE A.

Spawn exactly TWO distinct read-only `gpt-5.6-luna` MAX investigators.

## Investigator A — stale NPN deferral auditor

Audit `PcbLayoutDeveloperVerifier` and the accepted NPN fixed-layout proof.

The current stale code is known to include exactly these two NPN deferrals in `recordDeferredFixedLayoutFailure(...)`:

1. `PCB trace passes through component routing courtyard: LOAD_SUPPLY / RLOAD`
   recorded as `DEFERRED R-6 fixed NPN layout`.

2. `PCB trace segment has zero length: 1050,230`
   recorded as `DEFERRED R-2 fixed NPN layout`.

The accepted direct NPN proof already exists in `NpnFixedLayoutDeveloperVerifier` and enumerates the exact finite current structural product:

- RLOAD variants: `SPAN_220`, `SPAN_240`, `SPAN_260`;
- RB variants: `SPAN_220`, `SPAN_240`, `SPAN_260`;
- RPD variants: `SPAN_220`, `SPAN_240`, `SPAN_260`;
- four origin/variation classes;
- exactly `3 × 3 × 3 × 4 = 108` cases;
- expected evidence: `PASS:NPN_FIXED_LAYOUT_MATRIX:cases=108/108;variantTuples=27;originClasses=4`.

The investigator must determine:

1. Whether both old deferrals are fully superseded by accepted 43R-6 coverage.
2. Whether removing them will cause the general layout verifier to exercise current NPN production geometry as a hard failure instead of silently swallowing it.
3. Whether any OTHER Task-43-related `DEFERRED`, waiver, expected-failure, catch-and-continue, or bypass remains in the general layout path.
4. Whether any remaining waiver is legitimate and, if so, exactly what unfinished frontier owns it.
5. The smallest safe cleanup diff.

The investigator must also propose an adversarial proof that a deliberately reintroduced equivalent NPN geometry failure would now be hard-red rather than deferred.

Do not edit.

## Investigator B — Task 43 shell/process-integrity auditor

Audit the full Task 43 verification chain from the browser/application verifier to `scripts/verify-browser.ps1` process exit.

Current relevant behavior includes:

- `verifyRoute(...)` detects application `FAIL:` state and returns `$false` after printing `FAIL ...`.
- the current `-Task43` branch invokes the Task 43 route and does `exit 1` when `verifyRoute` is false, `exit 0` on success.
- a prior generic experiment showed that merely printing `FAIL` can still exit 0, which is why an integrated Task 43 forced-negative proof is required.

Design the smallest DEV-ONLY forced-negative seam that proves all of the following:

1. The real Task 43 application route is reached.
2. The real Task 43 aggregate/verifier path deliberately fails for a known injected developer-only reason.
3. The page publishes the normal Task 43 failure state rather than a fake shell-only string.
4. `verifyRoute(...)` recognizes the application failure.
5. `scripts/verify-browser.ps1` propagates that failure to a nonzero process exit.
6. The ordinary valid `-Task43` path still exits 0.
7. The forced-negative mechanism is impossible to trigger during normal player operation unless the explicit developer-only query/switch is supplied.
8. No production electrical, geometry, repair, measurement, or gameplay semantics are changed.

Prefer reusing existing developer-verifier/query plumbing rather than inventing a second test framework.

A robust shape may be an explicit developer-only Task 43 failure injection plus a script switch that invokes it, but the investigator must choose the narrowest implementation after reading the current code.

The final proof must include a real child-process exit-code observation. It is not sufficient to assert that an exception “should” cause nonzero exit.

Do not edit.

---

# ARCHITECT BARRIER

After dispatching both investigators:

WAIT FOR BOTH COMPLETE REPORTS.

Do not spawn the writer.
Do not start implementing.
Do not poll them for reassurance.

When both reports are complete:

1. Read them in full.
2. Reconcile any disagreement.
3. Confirm no production architecture defect has been found.
4. Freeze one exact allowed diff.
5. Freeze exact positive commands.
6. Freeze exact forced-negative commands.
7. Freeze the expected exit codes.
8. Freeze the exact stale deferrals to remove.

If a production NPN route defect appears after removing the stale deferrals:

STOP.

Classify it as a regression owned by the earlier NPN recovery frontier (43R-6) rather than patching routing in 43R-8B.

If Task 43 failure cannot be propagated through the current supported shell/browser path without redesigning unrelated production systems:

STOP and report the blocker.

Only continue if Phase A proves this is bounded acceptance-infrastructure work.

---

# ALLOWED IMPLEMENTATION SCOPE

The frozen Phase-A diff should be as small as possible.

Likely eligible files, only when directly justified:

- `src/com/lushprojects/circuitjs1/client/PcbLayoutDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/Task43DeveloperVerifier.java`
- narrowly required Task 43 developer-only plumbing in `CirSim.java`
- `scripts/verify-browser.ps1`
- narrowly required developer-only Task 43 verification helper if a clean existing seam does not exist
- `docs/ARCHITECTURE.md` if the acceptance plumbing boundary needs documentation
- `docs/ROADMAP.md`
- `docs/CODEX_TASK_REPORT.md`

This list is not permission to touch every file.
Phase A must produce the narrower actual allowed-file list.

---

# FORBIDDEN SCOPE

Do NOT in 43R-8B:

- reroute NPN;
- reroute RC;
- reroute NMOS;
- modify component placements;
- redesign `PhysicalPackage`;
- redesign `PhysicalPackageGeometry`;
- change geometry version ownership;
- change generic board net-connectivity semantics;
- change installed interaction semantics;
- change loose-part lifecycle/pose;
- change CircuitJS solver behavior;
- change measurement semantics;
- change replacement/fault/stress semantics;
- reopen accepted 43R-8A endpoint behavior unless Phase A proves a direct regression;
- modify `AGENTS.md`;
- begin final integrated 43R-8 acceptance;
- mark Task 43 complete;
- unlock or begin Task 44;
- merge master;
- force-push.

If removing a stale NPN deferral exposes a real NPN production failure, STOP and return it to 43R-6 ownership.
Do not “fix” the route inside 43R-8B.

---

# REQUIRED NPN DEFERRAL CLOSURE

At minimum, the final candidate must establish:

1. The two stale NPN signatures listed above are no longer swallowed by `PcbLayoutDeveloperVerifier`.
2. The general layout verifier treats those failures as ordinary hard failures.
3. The accepted direct NPN fixed-layout matrix still passes all 108 cases.
4. The normal general layout route passes with no `DEFERRED R-2`, `DEFERRED R-6`, or equivalent completed-frontier waiver in its output.
5. Current NPN production seeds used by the general verifier remain deterministic and valid.
6. No RC/NMOS/LED/diode/parallel waiver behavior is accidentally changed.

If `recordDeferredFixedLayoutFailure(...)` becomes dead after the legitimate deferrals are removed, delete the dead helper/catch structure rather than preserving an empty swallowing seam.

Do not retain stale comments claiming R-2/R-6 work remains when those recovery frontiers are already accepted.

---

# REQUIRED TASK 43 SHELL CANARY

Implement a DEV-ONLY forced-negative lane that exercises the real Task 43 verification chain.

The exact naming is up to the frozen design, but the resulting behavior must be unmistakable.

Positive lane:

- run the ordinary Task 43 browser verifier;
- application result = `PASS:task43`;
- process exit code = 0.

Forced-negative lane:

- explicitly request the developer-only Task 43 failure injection;
- the application must reach Task 43 verification and publish a genuine `FAIL:` result for the deliberate canary;
- `verifyRoute(...)` must observe the failure;
- the Task 43 script path must terminate nonzero;
- capture the child process exit code and prove it is nonzero.

A shell-only command that merely prints `FAIL` is NOT sufficient.
A fake test that bypasses the Task 43 application route is NOT sufficient.
A source-level assertion that the shell “would exit 1” is NOT sufficient.

The forced failure must be deterministic, explicit, developer-only, and harmless after the process exits.

Do not leave temporary repository mutations behind to create the failure.

---

# MINIMUM VALIDATION MATRIX

After implementation, run at least:

1. `git diff --check`.
2. Fresh JDK 8 / GWT production build, all five permutations.
3. Renderer boundary check if any Task 43 developer plumbing touched renderer-adjacent code.
4. Direct NPN fixed-layout 108-case proof through the Task 43 aggregate or its authoritative entry point.
5. General `Layout` verifier with current supported seed(s), confirming no stale NPN deferral output.
6. Ordinary Task 43 route, confirming `PASS:task43` and process exit 0.
7. Forced-negative Task 43 route, confirming the real application reports the intended deliberate failure and the child process exits nonzero.
8. Re-run ordinary Task 43 immediately after the forced-negative proof to establish there is no persistent contamination.

If the supported automated Edge harness is blocked by the known host WMI/GPU issue, use the supported visible Edge/in-app route already used successfully for 43R-8A to obtain application evidence, BUT shell exit-code proof still requires an actual process-level execution path. Do not convert an environment failure into a pass.

---

# NEGATIVE / ANTI-FALSE-PASS REQUIREMENTS

The final accepted state must make these impossible:

- a completed NPN fixed-layout regression matching an old signature being printed as `DEFERRED` and accepted;
- Task 43 application failure being printed while the top-level verification process exits 0;
- a forced-negative developer seam leaking into ordinary player execution;
- the forced-negative lane mutating persistent repository or game state;
- a child verifier failing while the aggregate still publishes `PASS:task43`.

If any of those remain possible, 43R-8B fails.

---

# PRIMARY ARCHITECT POST-WRITER REVIEW

After the single writer finishes:

1. Inspect the complete diff.
2. Confirm the diff matches the frozen allowed-file list.
3. Confirm no route redesign or gameplay change slipped in.
4. Confirm both stale NPN deferrals are actually gone or otherwise made impossible to swallow.
5. Confirm direct NPN 108-case coverage still exists and passes.
6. Inspect the forced-negative implementation and prove it cannot affect normal player runs.
7. Run the positive and forced-negative shell commands yourself.
8. Record exact process exit codes.
9. Re-run ordinary Task 43 after the negative canary.
10. Confirm `git diff --check`.
11. Confirm working-tree cleanliness and no temporary browser/profile/process contamination attributable to the test design.

Do not accept the writer’s own PASS statement as sufficient.

---

# FRESH FINAL REVIEW

After the primary architect review passes, spawn ONE fresh read-only:

- `gpt-5.6-luna`
- MAX reasoning

The reviewer must make NO edits.

It must inspect:

- accepted 43R-8A remains intact;
- complete 43R-8B diff;
- removal of stale NPN deferrals;
- NPN 108-case evidence;
- general layout hard-failure behavior;
- Task 43 positive shell result;
- forced-negative Task 43 application result;
- forced-negative child process exit code;
- post-negative clean positive rerun;
- dev-only isolation of the failure injection;
- documentation state;
- scope and git status.

Reviewer returns exactly:

`PASS`

or:

`BLOCKERS`
- exact owner
- file/method
- evidence
- required correction

If BLOCKERS, do not commit the implementation completion state.

---

# DOCUMENTATION STATE

After and ONLY AFTER every 43R-8B gate passes:

`docs/ROADMAP.md` must say:

- Task 43 = `RECOVERY IN PROGRESS`;
- 43R-8A = accepted/complete;
- 43R-8B = accepted/complete;
- final integrated 43R-8 acceptance = NEXT ELIGIBLE / UNSTARTED;
- Task 44 = BLOCKED BY TASK 43 / UNSTARTED.

Do NOT mark Task 43 complete.
Do NOT mark Task 44 next eligible yet.

The repository currently describes 43R-8B as “final Task 43 acceptance, regression, and cleanup.” Correct that wording during 43R-8B handoff documentation so that 43R-8B is clearly the acceptance-infrastructure closure and a fresh final integrated 43R-8 pass remains after it.

`docs/CODEX_TASK_REPORT.md` must record:

- exact stale NPN deferrals removed;
- authoritative NPN 108-case evidence;
- ordinary Task 43 command/result/exit code;
- forced-negative Task 43 command/application failure/result/exit code;
- post-negative ordinary Task 43 rerun;
- build result;
- independent review disposition;
- exact changed files.

---

# COMMIT / PUBLICATION POLICY

Only if all 43R-8B gates pass:

1. Update final handoff documentation.
2. Inspect `git status`.
3. Stage only intended 43R-8B files.
4. Run `git diff --cached --check`.
5. Commit once with a concise message such as:
   `Close Task 43 acceptance infrastructure`
6. Follow the repository’s normal publication/completion protocol.
7. Verify the configured remote contains the exact final SHA.
8. Perform the required completion-notification attempt if available.
9. STOP.

Do NOT begin final 43R-8 acceptance in the same session.
Do NOT begin Task 44.

---

# FINAL RESPONSE REQUIREMENTS

Report:

- final commit SHA;
- exact changed files;
- stale NPN deferrals removed;
- whether any Task-43-related waiver remains and why;
- NPN 108-case result;
- general Layout result;
- ordinary Task 43 result;
- ordinary Task 43 process exit code;
- forced-negative Task 43 application result;
- forced-negative Task 43 child-process exit code;
- clean ordinary Task 43 rerun result after the negative canary;
- JDK 8/GWT result;
- independent Luna MAX reviewer disposition;
- publication result;
- confirmation Task 43 remains RECOVERY IN PROGRESS;
- confirmation final integrated 43R-8 remains NEXT ELIGIBLE / UNSTARTED;
- confirmation Task 44 remains BLOCKED / UNSTARTED.

Then STOP.