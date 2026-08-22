# TASK 43R-5A — REPAIR THE MISSING RC FIXED-LAYOUT ACCEPTANCE PROOF

Repository:
`dspevo-afk/TroubleshootJS`

Accepted pre-Task-43 baseline:
`c0eb342b29165b8218a4b97b16fb8554fee42aff`

Accepted 43R-7 production checkpoint:
`c306556d3d387e4ad7d20353a73a6b703e58c477`
`Reconstruct Task 43 NMOS fixed routing`

Previous R8 handoff-only commit:
`5f30b6c7ebfbed163009265fd0e97d99209a327b`

Working branch:
`codex/task43-recovery-integration`

## WHY THIS TASK EXISTS

43R-8 correctly stopped at its Phase-A barrier because 43R-5 claimed a complete finite RC fixed-layout proof that does not actually exist in the current repository.

Verified current state:

- `RcDelayPcbLayoutFactory` exposes only seeded creation and has no developer-only finite-matrix seam.
- There is no `RcFixedLayoutDeveloperVerifier`.
- `Task43DeveloperVerifier` invokes the NPN and NMOS fixed-layout matrices but not an RC fixed-layout matrix.
- The roadmap/docs nevertheless mark 43R-5 complete and describe a 3 × 3 resistor-variant proof with four origin classes.
- `PcbLayoutDeveloperVerifier` still contains stale RC deferred-failure signatures that can swallow exactly the sort of RC geometry regression 43R-5 was supposed to close.

Classification:
`IMPLEMENTATION_FAILURE` in the prior 43R-5 acceptance contract.

This task repairs that missing proof only.

43R-8 remains BLOCKED until 43R-5A passes and is independently reviewed.
Task 44 remains BLOCKED.

---

# STARTING-STATE CHECK

This prompt itself may be added in a newer handoff-only commit after `5f30b6c7...`.

Before changing anything:

1. Confirm branch is exactly `codex/task43-recovery-integration`.
2. Confirm `c306556d3d387e4ad7d20353a73a6b703e58c477` is an ancestor of HEAD.
3. Compare `c306556d3d387e4ad7d20353a73a6b703e58c477..HEAD`.
4. Newer changes are expected to be handoff-only inbox files, principally:
   - `codex_tasks/inbox/TASK_43R_8_FINAL_ACCEPTANCE.md`
   - `codex_tasks/inbox/TASK_43R_5A_RC_ACCEPTANCE_PROOF.md`
5. Confirm no production changes occurred after accepted 43R-7.
6. Confirm `master` and `origin/master` remain at `c0eb342b29165b8218a4b97b16fb8554fee42aff`.
7. Confirm working tree is clean.
8. Confirm no other write-capable Task 43 agent is active.

If any condition is false, STOP with a PROCESS BLOCKER.
Do not merge master.
Do not force-push.
Do not begin R8 implementation.
Do not begin Task 44.

---

# ORCHESTRATION RULES — HARD REQUIREMENTS

## EVERY AGENT MUST BE ACTUAL MAX REASONING

EVERY SINGLE SUBAGENT YOU SPAWN MUST USE `gpt-5.6-luna` AT **MAX REASONING**.

Not Extra High.
Not a worker merely named “MAX.”
Not another model with a misleading nickname.
Actual MAX reasoning.

Verify the reasoning setting before dispatch.

## ABSOLUTELY DO NOT SPAWN THE CODER UNTIL ALL INVESTIGATORS ARE FINISHED

This is mandatory.

Do NOT create the coder while investigators are still gathering information.
Do NOT start the coder with the original task and then drip-feed investigator findings later.
That workflow is ABSOLUTELY FUCKING UNACCEPTABLE.

Required order:

1. Spawn the read-only investigators.
2. Let every investigator finish completely.
3. Read every complete report.
4. Reconcile contradictions and omissions yourself.
5. Freeze one complete 43R-5A implementation design and exact allowed diff.
6. Review that design.
7. ONLY THEN may one writer be spawned.

If you accidentally spawn the writer early:
- cancel it;
- discard partially informed edits;
- record a process failure;
- finish Phase A correctly;
- then create a new writer.

## DO NOT PESTER THE SUBAGENTS

Do not repeatedly ask whether they are still working.
Do not poll timestamps, process lists, git status, partial diffs, or logs for reassurance.
Do not narrate useless status updates while the subagents work.
Do not burn credits babbling.

Unless concrete new evidence requires intervention, sit idle and let them finish.
Pure liveness checks should not happen more often than roughly every 30 minutes and normally should not happen at all if the runtime reports completion automatically.

The subagents are doing the detailed work. Treat them accordingly.
Your job is to delegate, reconcile, review, and enforce the contract.

## ONE-WRITER POLICY

Only one write-capable agent may exist at any time.
Investigators and final reviewers are read-only.
The primary architect must not quietly become a second coder.

---

# TASK SCOPE

## GOAL

Restore the missing 43R-5 acceptance machinery so the repository can directly prove the complete finite RC fixed-layout structural matrix.

The intended matrix is:

- R1 axial resistor variant: `SPAN_220`, `SPAN_240`, `SPAN_260`
- R2 axial resistor variant: `SPAN_220`, `SPAN_240`, `SPAN_260`
- origin/variation class: 0, 1, 2, 3

Total:
**3 × 3 × 4 = 36 explicit RC fixed-layout cases.**

This must be deterministic finite enumeration, not random-seed theater.

## CRITICAL RULE: THIS IS NOT AUTOMATICALLY A REROUTING TASK

The missing proof does NOT by itself mean the current RC copper is wrong.

Do not reroute RC simply because the verifier is missing.
Do not move components.
Do not tweak coordinates “just in case.”

First build the missing proof seam and verifier.

If the explicit 36-case matrix proves the current route valid, great: close the acceptance gap.

If any explicit case fails the real production geometry/connectivity rules:

**STOP.**

Classify it as a real 43R-5 fixed-route defect.
Do NOT repair the route inside this acceptance-proof task.
Do NOT begin coordinate roulette.
Return the exact failing variants, origin class, validator message, trace/net/pad evidence, and owning 43R-5 route defect.

No commit should claim 43R-5A success in that case.

---

# PHASE A — READ-ONLY DESIGN

NO WRITER MAY EXIST DURING PHASE A.

Spawn exactly TWO read-only Luna MAX investigators.

## Investigator A — RC Factory / Variant / Matrix Design

Inspect:

- `RcDelayPcbLayoutFactory.java`
- `NpnLowSideSwitchPcbLayoutFactory.java`
- `NpnFixedLayoutDeveloperVerifier.java`
- `NmosLowSideSwitchPcbLayoutFactory.java`
- `NmosFixedLayoutDeveloperVerifier.java`
- `PhysicalPackage.java`
- `PhysicalPackages.java`
- `PcbFootprint.java`
- `PcbBoardLayout.java`

Produce a concrete design for:

1. A developer-only RC creation seam that explicitly selects:
   - variation/origin mode 0..3;
   - R1 canonical axial variant;
   - R2 canonical axial variant.
2. One shared internal factory path so production seeded creation and developer finite creation do not duplicate RC route logic.
3. Exact 36-case enumeration.
4. Canonical package/variant identity assertions.
5. Exact route endpoint/membership assertions.
6. Complete physical-net connectivity assertions.
7. Legal pad escape assertions.
8. Courtyard, clearance, silkscreen, route-quality, containment, and deterministic identity checks.
9. Normalized rigid-translation equivalence across the four origin classes.
10. A production-seed parity check proving the developer seam exercises the same live implementation path rather than a parallel fake fixture.
11. Exact allowed files and no-route-change boundary.

The investigator must explicitly identify every current RC logical net and every required physical pad membership.

## Investigator B — Adversarial Acceptance / Deferral Audit

Inspect:

- current RC route code;
- `PcbLayoutDeveloperVerifier` RC deferred signatures;
- `Task43DeveloperVerifier` aggregation;
- 43R-5 architecture/roadmap/report claims;
- current generic physical-union validator;
- NPN/NMOS fixed-matrix patterns.

Determine:

1. Exactly what 43R-5 claimed versus what code currently proves.
2. Whether the new matrix can invoke the real production validators directly without relying on `PcbLayoutDeveloperVerifier` deferrals.
3. Which RC deferred signatures are now stale and must be removed after the matrix passes.
4. Whether any remaining RC waiver/bypass can swallow a completed-frontier regression.
5. Negative canaries that prove the RC matrix is not merely checking itself.
6. How to ensure every logical RC pad is represented and physically joined to the correct net.
7. Whether current route equations appear structurally capable of supporting all 36 explicit combinations, without modifying them.

Do not edit anything.

## PHASE-A BARRIER

After both investigators are dispatched:

DO NOTHING until both are finished.

Do not spawn a coder.
Do not start implementing yourself.
Do not pester them.

After both reports arrive:

1. Read both completely.
2. Reconcile them.
3. Freeze one exact implementation plan.
4. Freeze one exact allowed diff.
5. State the exact 36-case acceptance matrix.
6. State every required positive and negative proof.
7. State exactly which RC deferrals may be removed and why.
8. State explicitly that no route/component/package changes are planned.

If either investigator finds evidence that the current RC route is already known to fail one of the required finite cases, STOP before spawning the writer and report a 43R-5 route defect.

Only if Phase A concludes the task is truly missing-proof infrastructure may the writer exist.

---

# PHASE B — SINGLE WRITER IMPLEMENTATION CONTRACT

Spawn ONE Luna MAX writer only after the complete Phase-A plan is frozen.

The writer receives the entire reconciled plan in its initial prompt.
No investigator should still be running.

## REQUIRED IMPLEMENTATION

### 1. Add a developer-only RC finite-matrix factory seam

Add an explicit RC developer-verification entry point analogous in purpose to the accepted NPN/NMOS seams.

It must allow explicit selection of:

- variation mode 0..3;
- R1 canonical variant key;
- R2 canonical variant key.

The production `create(board, seed)` path and developer seam must share the same underlying RC layout construction and route code.

Do not duplicate the route into a second fake verifier-only board builder.

Production behavior must remain seeded and unchanged.

### 2. Add `RcFixedLayoutDeveloperVerifier`

Create a dedicated developer-only verifier that enumerates exactly:

- 3 R1 variants ×
- 3 R2 variants ×
- 4 origin classes =
- **36 cases**.

For every case, require the real layout to build successfully and call the real generic validators.

At minimum verify:

- exact case count 36;
- canonical R1/R2 geometry variant identity;
- canonical connector/capacitor package identity;
- stable component/pad IDs;
- exact trace count and expected endpoint/net membership;
- every logical pad is present physically;
- every logical pad is represented by required copper or approved package-internal connectivity;
- complete physical union of every multi-pad RC net;
- endpoint escape direction and declared escape length behavior;
- routing courtyard legality;
- unrelated-net clearance;
- no crossing;
- no zero-length, duplicate, repeated, retraced, or self-intersecting segments;
- silkscreen/body/pad/copper legality;
- trace-width board containment;
- existing bend/detour route-quality limits;
- deterministic duplicate geometry fingerprints;
- normalized rigid-translation equivalence across the four origin classes;
- production seeded creation shares the same underlying realization path.

The verifier must not merely duplicate the factory’s coordinate arithmetic and call equality a proof.
Coordinate witness checks are allowed as a frozen realization check, but they MUST be paired with independent production validators and logical/physical connectivity assertions.

### 3. Aggregate RC into Task 43

`Task43DeveloperVerifier.verify(...)` must explicitly invoke the RC finite matrix alongside NPN and NMOS.

Expected conceptual order:

- RC fixed matrix;
- NPN fixed matrix;
- NMOS fixed matrix;

Exact order may differ if there is a documented reason, but RC must be a hard Task 43 gate.

### 4. Remove stale RC deferrals after direct proof passes

Audit `PcbLayoutDeveloperVerifier.recordDeferredFixedLayoutFailure(...)`.

Once the explicit RC matrix passes, remove the stale RC deferred signatures that can swallow completed RC fixed-layout failures.

Do NOT remove unrelated NPN deferrals in this task unless the frozen Phase-A plan proves doing so is mechanically inseparable.
NPN deferral cleanup remains a 43R-8 concern.

After RC deferral removal, a future RC regression must fail the general layout verifier rather than print a deferred message and continue.

### 5. Documentation

Update:

- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/CODEX_TASK_REPORT.md`

Record 43R-5A as the corrective acceptance-proof closure.
Do not claim 43R-8 complete.
Do not claim Task 43 complete.
Do not begin Task 44.

---

# RC LOGICAL CONNECTIVITY THAT MUST BE PROVED

Do not trust this list blindly; verify it against the current live `TroubleshootBoard` fixture during Phase A and implementation.

The current RC factory visibly routes these memberships and the verifier must prove the live logical board agrees:

`VIN`
- `J1.1`
- `R1.1`
- `C2.1`

`RC_OUT`
- `R1.2`
- `C1.+`
- `J2.1`
- `R2.1`

`GND`
- `J1.2`
- `C1.-`
- `J2.2`
- `C2.2`
- `R2.2`

If the live logical board differs, use the live board as authority and report the discrepancy.
Do not silently rewrite the logical topology.

---

# ALLOWED FILES

Expected allowed files are limited to:

- `src/com/lushprojects/circuitjs1/client/RcDelayPcbLayoutFactory.java`
- new `src/com/lushprojects/circuitjs1/client/RcFixedLayoutDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/Task43DeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/PcbLayoutDeveloperVerifier.java` only for RC deferral removal
- narrowly required existing RC developer verifier aggregation/plumbing
- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/CODEX_TASK_REPORT.md`

Any additional production file requires explicit architect approval and a concrete dependency explanation before editing.

---

# FORBIDDEN SCOPE

Do NOT:

- change RC copper coordinates unless the matrix first proves a real route defect, in which case STOP rather than fixing it here;
- move RC components;
- change package geometry;
- change package variants;
- change the geometry-contract version;
- change generic board connectivity rules;
- change NPN routes;
- change NMOS routes;
- redesign rendering, hit testing, probing, loose-part pose, measurement, fault, stress, replacement, or electrical topology;
- add a production autorouter;
- remove NPN deferrals merely because you noticed them;
- implement the Task 43 negative shell canary here;
- begin 43R-8 acceptance work beyond what is necessary to hand control back cleanly;
- begin Task 44;
- modify `AGENTS.md`;
- merge master;
- force-push.

---

# REQUIRED NEGATIVE / ANTI-TAUTOLOGY CANARIES

Phase A may refine exact implementation, but the accepted proof must include direct evidence that the matrix would catch at least these classes of failure:

1. A missing RC logical pad/copper branch is rejected by the real physical-union validator.
2. A wrong-net trace endpoint is rejected.
3. An illegal endpoint escape or courtyard entry is rejected.
4. An unrelated-net clearance/crossing violation is rejected.
5. A noncanonical/undeclared resistor geometry variant is rejected by the developer seam or package boundary.
6. A deterministic duplicate mismatch is rejected.
7. General RC layout verification no longer swallows the old exact deferred RC failure signatures.

Do not mutate production code permanently to activate a canary.
Use established developer-negative-fixture patterns.

---

# REQUIRED VALIDATION

Run the strongest real repository-supported validation appropriate to this corrective slice.

At minimum:

1. JDK 8 production build/link with the established `scripts/build.ps1` path.
2. All five GWT production permutations.
3. Direct RC fixed matrix:
   expected evidence equivalent to
   `PASS:RC_FIXED_LAYOUT_MATRIX:cases=36/36;variantTuples=9;originClasses=4`
   Exact spelling may differ, but counts must be explicit.
4. General layout verifier with RC no longer deferred.
5. Task 43 aggregate proving RC + NPN + NMOS fixed matrices are all hard gates.
6. Existing RC electrical verifier.
7. Existing stored-energy/temporal RC verifier.
8. Relevant deterministic RC regeneration checks.
9. All new negative canaries.
10. `git diff --check` before staging.
11. `git diff --cached --check` after staging.
12. Final `git status` and staged-file audit.

If a supported normal-player RC browser smoke is cheap and reliable, run it, but this task does not replace the broad current-run Task 39/40/41 and shell acceptance required later by 43R-8.

If the environment blocks a required R5A validation lane, report `EXTERNAL_BLOCKER` and do not claim acceptance.

---

# MANDATORY STOP CONDITIONS

STOP immediately and do not commit 43R-5A as successful if:

- any of the 36 explicit RC cases fails real production geometry/connectivity validation;
- the current RC route must be changed;
- a component must move;
- package geometry must change;
- generic validation must be weakened to make RC pass;
- an RC failure is merely hidden behind another deferral;
- the developer seam needs a parallel fake route implementation;
- the finite verifier does not exercise the same live RC layout path as production;
- a negative canary unexpectedly passes;
- a required build/runtime path is externally blocked;
- another writer appears;
- the writer was spawned before all Phase-A investigators were finished.

Classify failures as:

`IMPLEMENTATION_FAILURE`
`REALIZATION_INFEASIBLE`
`ARCHITECTURAL_CONTRADICTION`
`EXTERNAL_BLOCKER`

For a route failure, return exact:
- R1 variant;
- R2 variant;
- origin class;
- failing net/trace/pad;
- validator message;
- whether failure occurs before or after compaction;
- whether seeded production can reach the failing realization.

Do not fix the route in this task.

---

# INDEPENDENT FINAL REVIEW

After implementation and validation pass, dispatch ONE fresh read-only Luna MAX reviewer.

Actual MAX reasoning.
No edits.

Reviewer must inspect:

1. branch and starting refs;
2. complete 43R-5A diff;
3. developer seam shares live RC production construction;
4. explicit 3 × 3 × 4 enumeration;
5. all 36 cases actually execute;
6. canonical variants are forced, not merely asserted by strings;
7. every live RC logical pad is physically represented;
8. physical net unions are validated by the real generic validator;
9. route/courtyard/clearance/quality checks are independent of coordinate witness assertions;
10. deterministic fingerprints and normalized origin equivalence;
11. stale RC deferrals are gone;
12. NPN/NMOS production code is untouched;
13. RC production route coordinates are unchanged;
14. docs truthfully describe 43R-5A as corrective closure, not final Task 43 acceptance.

Reviewer returns exactly:

`PASS`

or

`BLOCKERS`

If blockers exist, include file/method/evidence and make no edits.

---

# COMMIT / PUBLICATION POLICY

Commit only after:

- all 36 RC cases pass;
- RC negative canaries pass;
- build passes;
- general layout no longer defers completed RC failures;
- Task 43 aggregate includes RC matrix;
- RC electrical/stored-energy checks pass;
- independent Luna MAX reviewer returns `PASS`;
- staged diff contains only intended files;
- `git diff --cached --check` passes;
- `docs/CODEX_TASK_REPORT.md` is complete and truthful.

Suggested implementation commit message:

`Restore Task 43 RC fixed-layout proof`

Follow the repository/user publication protocol currently in force for this recovery branch.
Do not merge master.
Do not force-push.
Do not begin 43R-8 automatically.
Do not begin Task 44.

Return:

- final implementation commit SHA;
- exact 36-case matrix result;
- exact build result;
- exact general layout result;
- exact Task 43 aggregate result;
- exact RC electrical/stored-energy results;
- negative canary results;
- RC deferrals removed;
- independent reviewer disposition;
- confirmation that RC route/component/package production geometry was not changed;
- confirmation that 43R-8 remains the next blocked/unblocked milestone depending on this result.
