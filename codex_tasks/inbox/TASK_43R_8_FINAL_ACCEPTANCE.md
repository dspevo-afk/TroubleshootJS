# TASK 43R-8 — FINAL TASK 43 ACCEPTANCE, REGRESSION CLOSURE, AND CLEANUP

Repository:
`dspevo-afk/TroubleshootJS`

Accepted pre-Task-43 baseline:
`c0eb342b29165b8218a4b97b16fb8554fee42aff`

Frozen recovery checkpoint:
`239b52f0c1fd36eac5ccb65ad7dbe559474c1800`

Accepted 43R-7 implementation baseline:
`c306556d3d387e4ad7d20353a73a6b703e58c477`
`Reconstruct Task 43 NMOS fixed routing`

Working branch:
`codex/task43-recovery-integration`

## IMPORTANT: THE INBOX HANDOFF COMMIT IS NOT IMPLEMENTATION WORK

This prompt itself is being added to the working branch after the accepted 43R-7 implementation commit.
Therefore HEAD may be newer than `c306556d3d387e4ad7d20353a73a6b703e58c477` when you start.

Before doing anything:

1. Verify the branch is exactly `codex/task43-recovery-integration`.
2. Verify `c306556d3d387e4ad7d20353a73a6b703e58c477` is an ancestor of HEAD.
3. Compare `c306556d3d387e4ad7d20353a73a6b703e58c477..HEAD`.
4. The only expected newer change is this inbox handoff file:
   `codex_tasks/inbox/TASK_43R_8_FINAL_ACCEPTANCE.md`
5. Verify `master` and `origin/master` remain at the accepted baseline unless an independently documented repository-only handoff commit exists.
6. Verify the working tree is clean.
7. Verify there is no other write-capable Task 43 agent active.

If any condition is false, STOP and report a PROCESS BLOCKER.
Do not repair branch history casually.
Do not merge master.
Do not begin Task 44.

---

# PROCESS RULES — THESE ARE HARD REQUIREMENTS

## EVERY AGENT MUST BE MAX REASONING

EVERY SINGLE SUBAGENT YOU SPAWN MUST USE `gpt-5.6-luna` AT **MAX REASONING**.

Not Extra High.
Not a worker merely named “MAX.”
Not a different model with a cute nickname.
Actual MAX reasoning.

If the runtime exposes a different exact model identifier for Luna MAX, verify the actual reasoning setting is MAX before dispatch.

## INVESTIGATORS MUST FINISH BEFORE THE CODER EXISTS

This is the most important orchestration rule in this task.

**DO NOT SPAWN THE CODER WHILE ANY PHASE-A INVESTIGATOR IS STILL GATHERING INFORMATION.**

Spawning a coder from the initial prompt and then drip-feeding reviewer findings into that coder later is ABSOLUTELY FUCKING UNACCEPTABLE.

The required sequence is:

1. Spawn the read-only investigators.
2. Let every investigator finish completely.
3. Receive every full report.
4. Reconcile contradictions and omissions yourself.
5. Freeze one complete acceptance design and one exact allowed diff.
6. Review that frozen design.
7. ONLY THEN may one write-capable coder be spawned.

If you accidentally spawn the writer before all investigator reports are complete:
- stop/cancel that writer;
- mark it as a process failure;
- do not salvage partially informed edits;
- finish Phase A properly before creating a new writer.

Do not send a coder incremental “new information just came in” messages. That is precisely the broken workflow this recovery plan is designed to prevent.

## DO NOT PESTER THE SUBAGENTS

The subagents are doing the detailed investigative and coding work. Do not be a dick to them.

While they are working:
- do not interrupt them for status;
- do not repeatedly ask whether they are still working;
- do not poll process lists, timestamps, git status, partial diffs, or logs just to reassure yourself that something is happening;
- do not narrate the same status to the user over and over;
- do not burn credits babbling while agents are already doing the work.

Unless concrete new evidence requires intervention, sit idle and let them finish.
Pure liveness checks should not occur more often than approximately every 30 minutes, and normally should not occur at all if the agent runtime will return completion automatically.

Only provide an update when there is actual new information, a real blocker, a completed phase, or the user directly asks.

Your job here is to:
- delegate;
- reconcile investigator reports;
- freeze the design;
- review the writer’s diff;
- run/interpret final acceptance;
- dispatch the independent final reviewer.

That is it unless this prompt explicitly assigns something else.

## ONE-WRITER POLICY

Only one write-capable agent may exist at any time.
Read-only investigators and reviewers must make no repository edits.
The primary architect should not quietly become a second coder.

---

# 43R-7 REVIEW HANDOFF

43R-7 implementation baseline `c306556d3d387e4ad7d20353a73a6b703e58c477` is accepted as the NMOS fixed-layout code checkpoint.

The pushed commit is exactly one implementation commit on top of accepted 43R-6 and changes only:

- `src/com/lushprojects/circuitjs1/client/NmosLowSideSwitchPcbLayoutFactory.java`
- `src/com/lushprojects/circuitjs1/client/NmosFixedLayoutDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/PcbLayoutDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/Task43DeveloperVerifier.java`
- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/CODEX_TASK_REPORT.md`

The NMOS fixed-layout verifier enumerates the complete current structural product:
- 3 RLOAD axial variants;
- 3 RPD axial variants;
- 4 origin classes;
- 36 cases total.

It checks the eight-trace coordinate/endpoint witness, canonical package/variant identity, package escapes, all logical pad membership, physical CONTROL_INPUT union including `RPD.1`, real `PcbBoardLayout.validateGeometry`, route quality, clearance, deterministic duplicate fingerprints, and normalized translation equivalence.

The production route derives its key lane coordinates from actual named pads and package-declared escape data and restores the complete `CONTROL_INPUT` copper union:
- `J2.1` → `RPD.1`;
- `J2.1` → `Q1.G`.

Do not redesign the NMOS route in 43R-8 unless final acceptance proves a real blocker. If that happens, STOP and return the defect to 43R-7 ownership rather than patching it here.

## 43R-7 VALIDATION DEBT THAT MUST BE CLOSED NOW

The 43R-7 report explicitly records environment limitations:

- the architect’s built-in visible Browser attempt was blocked before page load with `ERR_BLOCKED_BY_CLIENT` on loopback;
- the separate Edge harness hit a GPU-process crash and WMI cleanup `Access denied`;
- the separate Task 39, Task 40, and Task 41 browser routes were therefore not reached in that session;
- no independent normal-player NMOS browser screenshot/smoke was claimed.

The coder-reported compiled-preview outputs were:
- `PASS:NMOS_FIXED_LAYOUT_MATRIX:cases=36/36;variantTuples=9;originClasses=4`;
- `PASS:layout`;
- `PASS:task43`;
- `PASS:nmos` for the nine existing NMOS electrical/control/mutation cases.

Those are strong evidence for 43R-7, but **they are not a substitute for the missing final regression lanes**.

43R-8 MUST close this evidence gap.

You may NOT say:
- “Task 39/40/41 files were unchanged, therefore PASS”;
- “the previous milestone passed them, therefore PASS”;
- “the browser failed for environmental reasons, therefore we will assume PASS.”

If no real supported path can execute a required final acceptance lane, classify it `EXTERNAL_BLOCKER` and DO NOT mark Task 43 complete.

## STALE DEFERRED-FAILURE PATHS TO AUDIT

`PcbLayoutDeveloperVerifier.recordDeferredFixedLayoutFailure(...)` still contains exact deferred signatures for completed RC and NPN fixed-layout frontiers.

43R-5 and 43R-6 now have dedicated fixed-layout evidence. Therefore 43R-8 must explicitly audit every remaining `DEFERRED R-...` or equivalent waiver path in the repository.

A completed family must not be able to regress back into an old known-bad geometry state and have the general layout verifier silently swallow the failure.

Remove superseded deferrals only after equivalent direct accepted coverage exists.
Do not remove a waiver blindly if another unfinished frontier still legitimately depends on it.
At final Task 43 acceptance, no completed Task 43 frontier may remain hidden behind a stale deferral.

---

# TASK PURPOSE

Close Task 43 as a whole.

This milestone is for:
- acceptance orchestration;
- regression closure;
- negative canaries;
- shell/process integrity;
- removal of superseded Task 43 diagnostic/deferral scaffolding;
- final architecture/roadmap/report documentation.

This milestone is NOT for inventing new production architecture.
The verifier confirms already-approved architecture. It does not become the place where architecture is designed.

If final validation exposes a production architecture or route defect, STOP and return it to the milestone that owns it.
Do not patch around it in 43R-8.

---

# ALLOWED SYSTEMS

You may modify only what is necessary for final acceptance orchestration and cleanup, principally:

- `Task43DeveloperVerifier.java`
- `PhysicalPartRenderDeveloperVerifier.java`
- `PcbLayoutDeveloperVerifier.java`
- narrowly required Task 39/40/41 verifier aggregation or invocation plumbing
- Task 43 developer plumbing in `CirSim.java`
- `scripts/verify-browser.ps1`
- Task 43 evidence files
- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/CODEX_TASK_REPORT.md`
- superseded Task 43 diagnostic/deferred-failure scaffolding, only after replacement coverage is proven

Any additional production file requires an explicit dependency explanation and primary-architect approval before the writer touches it.

---

# FORBIDDEN SCOPE

Do NOT:

- redesign `PhysicalPackage` or `PhysicalPackageGeometry`;
- redesign package variants or geometry version ownership;
- change footprint architecture;
- change generic board connectivity semantics;
- redesign installed interaction;
- redesign loose-part pose/lifecycle;
- reroute RC;
- reroute NPN;
- reroute NMOS;
- move components;
- change electrical generator topology;
- change CircuitJS solver behavior;
- change measurement semantics;
- change fault/stress/replacement semantics;
- add a production autorouter;
- begin Task 44;
- modify `AGENTS.md`;
- merge to master;
- force-push.

---

# PHASE A — READ-ONLY FINAL ACCEPTANCE DESIGN

NO WRITER MAY EXIST DURING PHASE A.

Spawn exactly three distinct read-only Luna MAX investigators.
All must be `gpt-5.6-luna` with MAX reasoning.

## Investigator A — Acceptance Matrix Auditor

Map every Task 43 roadmap/recovery criterion to:

1. authoritative owner;
2. exact positive proof;
3. exact negative canary;
4. exact command or verifier entry point;
5. exact finite variant corpus;
6. expected evidence string/result;
7. failure classification;
8. whether current code already supplies the proof or R8 needs orchestration/cleanup.

It must cover package contract, board contract, installed interaction, loose interaction, fixed layouts, deterministic identity, and final documentation.

## Investigator B — Runtime / Regression Auditor

Inventory the actual repository-supported commands and browser paths for:

- JDK 8 production build;
- all five GWT permutations;
- Task 43;
- general layout;
- Task 39;
- Task 40;
- Task 41;
- RC electrical verification;
- stored-energy verification;
- NPN verification;
- NMOS verification;
- relevant generated-board/challenge/replacement/mutation normal-player regressions.

Investigate the prior loopback/Edge failures and identify every real supported alternative already available in the environment/repository.

Do not invent fake DOM-only substitutes.
Do not call environment failure a product pass.

## Investigator C — Adversarial / Negative-Canary / Waiver Auditor

Audit:

- every Task 43 negative canary;
- shell exit behavior;
- every `DEFERRED`, waiver, bypass, catch-and-continue, expected-failure, or similar path related to Task 43;
- especially stale RC/NPN fixed-layout deferrals in `PcbLayoutDeveloperVerifier`;
- whether a deliberately broken Task 43 condition can still produce a success process exit;
- whether any final aggregate merely prints PASS while swallowing a child failure.

The investigator must identify which deferrals are still legitimate and which are now dangerous stale holes.

## ARCHITECT BARRIER

After dispatching the three investigators:

**DO NOTHING UNTIL ALL THREE ARE FINISHED.**

Do not spawn the coder.
Do not send “just getting started” notes.
Do not poll them.
Do not ask whether they are alive.
Do not start writing your own implementation in parallel.

When all three complete:

1. read every full report;
2. reconcile discrepancies;
3. produce one closed acceptance matrix;
4. produce one exact allowed diff;
5. identify all required current-run commands;
6. identify all negative canaries;
7. identify all superseded deferrals to remove;
8. explicitly state whether a production defect has been found.

If a production defect exists, STOP and assign it to the owning previous milestone. Do not create the R8 writer.

Only if Phase A proves this is acceptance/cleanup work may the writer be spawned.

---

# REQUIRED CLOSED ACCEPTANCE MATRIX

The architect’s reconciled Phase-A matrix must explicitly cover all of the following.

## A. PACKAGE CONTRACT

Positive proof:
- every production package has authoritative geometry;
- stable terminal IDs and terminal order;
- body/keep-out/courtyard;
- selection/drag envelopes;
- board-pad interaction surface;
- lifted-component interaction surface;
- all resistor variants `SPAN_220`, `SPAN_240`, `SPAN_260`;
- all diode variants `SPAN_230`, `SPAN_250`;
- connector normal and mirrored realizations;
- selected-geometry lifecycle;
- explicit PCB geometry-contract version;
- deterministic package realization.

Negative proof must include malformed/foreign/undeclared geometry and ambiguous interaction canaries already established by the earlier milestones.

## B. BOARD CONTRACT

Positive proof:
- package-backed footprints;
- exact pad and probe bounds;
- compaction includes every declared physical/interaction envelope;
- trace-width board containment;
- complete same-net physical connectivity;
- same-net tees/intersections/overlaps where legitimate;
- package-internal connectivity where declared;
- deterministic geometry fingerprint/version identity.

Negative proof:
- orphan logical pad;
- two disconnected copper islands on one logical net;
- wrong-net endpoint;
- illegal courtyard entry;
- wrong escape direction;
- unrelated-net crossing;
- insufficient clearance;
- duplicate/zero-length/repeated/self-intersecting route segment;
- silkscreen/body/pad/copper overlap;
- centerline inside but trace width outside board.

## C. INSTALLED INTERACTION

Prove:
- installed body/leads use package geometry;
- component selection agrees with visible geometry;
- connected terminal resolves only to board-pad target;
- lifted board-side pad remains reachable;
- lifted component-side lead remains separately reachable;
- the two targets have distinct electrical endpoints;
- marker points agree with visible surfaces;
- reconnect/removal invalidate stale component-side targets;
- reinstallation preserves required identity.

## D. LOOSE INTERACTION

Prove:
- one rigid loose pose;
- no per-terminal warping;
- drawing/hit/probe/selection/drag/marker agreement;
- all supported removable package terminals are probeable;
- polarized reversal preserves stable terminal identity;
- pagination invalidates off-page targets safely;
- remove/reinstall preserves physical identity and selected-geometry lifecycle according to the frozen contract.

## E. FIXED-LAYOUT FINITE MATRIX

Do not spray random seeds and call that coverage.
Run the actual finite structural products.

### RC
- all 9 resistor-span geometry combinations;
- all 4 compaction-origin classes;
- direct RC fixed-layout matrix evidence;
- complete physical net connectivity;
- route quality/clearance/courtyard/silkscreen checks;
- deterministic geometry.

### NPN
- all 27 resistor-span geometry combinations;
- all 4 compaction-origin classes;
- 108 cases total;
- complete BASE/COLLECTOR/GND copper;
- legal B/C/E escapes;
- route quality/clearance/courtyard/silkscreen checks;
- deterministic geometry.

### NMOS
- all 9 RLOAD/RPD span combinations;
- all 4 compaction-origin classes;
- 36 cases total;
- explicit physical union of `J2.1`, `RPD.1`, and `Q1.G` on `CONTROL_INPUT`;
- legal G/D/S escapes;
- route quality/clearance/courtyard/silkscreen checks;
- deterministic geometry.

The general layout verifier must no longer silently defer a completed RC/NPN/NMOS fixed-layout failure.

## F. PRIOR-TASK / ELECTRICAL REGRESSION

Run current evidence for:

- Task 39;
- Task 40;
- Task 41;
- RC electrical behavior;
- stored-energy behavior;
- NPN electrical/control/mutation behavior;
- NMOS electrical/control/mutation behavior;
- generated LED/diode/parallel families;
- replacement/workbench behavior;
- mutation/removal/lead-lift behavior;
- relevant normal-player browser flows.

The missing 43R-7 Task 39/40/41 lanes MUST be executed here.

“Unchanged since prior pass” is context, not acceptance evidence.

## G. SHELL / PROCESS INTEGRITY

Prove both:

1. valid Task 43 implementation returns success;
2. a deliberately activated Task 43 negative canary returns nonzero.

A command that prints `FAIL:` and exits success is unacceptable.
A child verifier failure swallowed by an aggregate is unacceptable.
A browser harness that fails only during cleanup must be analyzed carefully so test-result success is not confused with harness-cleanup failure.

---

# PHASE B — SINGLE WRITER, ACCEPTANCE ORCHESTRATION ONLY

Only after the architect freezes Phase A may ONE Luna MAX writer be spawned.

The writer receives the complete reconciled design in its initial prompt.
Do not drip-feed later investigator findings because there must be no unfinished investigators at this point.

The writer may implement only:

- verifier aggregation/orchestration;
- current-run evidence plumbing;
- negative canaries;
- shell failure propagation;
- removal of superseded deferral/diagnostic paths;
- final Task 43 documentation/report cleanup.

The writer may NOT alter production geometry, routes, electrical behavior, or interaction semantics.

If the writer discovers it needs a production fix:
STOP THE WRITER.
Classify the defect and return it to the owning milestone.
Do not turn R8 into a secret R5/R6/R7 patch session.

---

# REQUIRED VALIDATION

Use the exact commands supported by the repository. Inspect the scripts instead of inventing flags.

At minimum, execute the strongest real current-run equivalent of:

1. JDK 8 production build/link using `scripts/build.ps1`.
2. All five production GWT permutations.
3. `scripts/verify-browser.ps1 -Task43` or the repository’s exact Task 43 flag.
4. `scripts/verify-browser.ps1 -Layout`.
5. `scripts/verify-browser.ps1 -Task39`.
6. `scripts/verify-browser.ps1 -Task40`.
7. `scripts/verify-browser.ps1 -Task41`.
8. RC verifier.
9. stored-energy verifier.
10. NPN verifier.
11. NMOS verifier.
12. existing relevant normal-player browser regression suite.
13. forced Task 43 negative-shell canary.
14. `git diff --check`.
15. `git status`.
16. `git diff --cached --check` before commit.

## BROWSER / ENVIRONMENT RULE

Use the supported built-in Browser workflow by default.
Keep the browser visible/watchable where supported.

If loopback is blocked in one browser surface:
- use another real supported repository/runtime path if available;
- distinguish page-load failure from application failure;
- distinguish test-result failure from browser-process cleanup failure;
- do not substitute static source inspection for a required runtime test;
- do not use a fake DOM shortcut simply to manufacture PASS.

If every real supported runtime path is unavailable for a mandatory final acceptance lane:
classify `EXTERNAL_BLOCKER`.
Do not mark Task 43 complete.
Do not edit the roadmap to PASS.
Do not commit a “final acceptance” commit.

---

# FAILURE CLASSIFICATION

Every failure must be classified as exactly one of:

`IMPLEMENTATION_FAILURE`
- approved existing architecture is correct, but current code/verifier orchestration is wrong.

`REALIZATION_INFEASIBLE`
- a required physical realization cannot satisfy the frozen contract.

`ARCHITECTURAL_CONTRADICTION`
- two frozen Task 43 contracts cannot simultaneously hold.

`EXTERNAL_BLOCKER`
- build/browser/environment prevents required acceptance evidence.

Do not blur these categories.
Do not call a product failure environmental because it is inconvenient.
Do not call an environment failure product PASS.

---

# MANDATORY STOP CONDITIONS

STOP immediately if:

- final validation requires changing package/geometry architecture;
- a fixed RC/NPN/NMOS route needs editing;
- a board connectivity rule needs redesign;
- installed or loose interaction needs redesign;
- an electrical endpoint or topology must change;
- a negative canary unexpectedly passes;
- a child verifier can fail while the aggregate succeeds;
- the shell can print FAIL while returning success;
- Task 39/40/41 cannot be executed by any real supported path;
- the acceptance matrix exposes a missing prior-milestone contract;
- another write-capable agent appears;
- the coder was spawned before all investigators were complete.

Do not patch around these failures in R8.
Return them to the owning milestone.

---

# FINAL INDEPENDENT REVIEW

After all required validation passes, dispatch one NEW read-only Luna MAX senior reviewer.

Again: actual MAX reasoning.
No edits.
No sharing a context contaminated by implementation assumptions if a fresh agent can be used.

The reviewer must inspect:

1. the complete `c0eb342b29165b8218a4b97b16fb8554fee42aff..HEAD` Task 43 production diff;
2. the `c306556d3d387e4ad7d20353a73a6b703e58c477..HEAD` R8 diff separately;
3. Task 43 roadmap requirements;
4. package/variant/version ownership;
5. generic physical net connectivity;
6. installed interaction;
7. loose-part interaction;
8. RC finite-route evidence;
9. NPN finite-route evidence;
10. NMOS finite-route evidence;
11. Task 39/40/41 current-run regressions;
12. electrical regressions;
13. stale deferral removal;
14. negative canaries;
15. process exit behavior;
16. final git status and staged diff.

The reviewer must return exactly one top-level disposition:

`PASS`

or

`BLOCKERS`

If `BLOCKERS`, each blocker must include:
- file;
- method/system;
- concrete evidence;
- owning milestone to which it must return.

The reviewer makes no edits.

---

# FINAL DOCUMENTATION

Only after every gate passes:

Update `docs/ARCHITECTURE.md` with the final Task 43 ownership boundaries.

Update `docs/ROADMAP.md` to mark Task 43 complete only if the entire current-run acceptance matrix passes.

Update `docs/CODEX_TASK_REPORT.md` with:

- final disposition;
- exact branch and starting refs;
- exact files changed in R8;
- package matrix result;
- board/connectivity result;
- installed interaction result;
- loose interaction result;
- RC finite matrix result;
- NPN finite matrix result;
- NMOS finite matrix result;
- Task 39 result;
- Task 40 result;
- Task 41 result;
- RC/stored-energy/NPN/NMOS electrical results;
- normal-player/browser evidence;
- negative-canary results;
- shell nonzero-on-failure proof;
- stale deferrals removed or explicitly justified;
- environment limitations, if any;
- independent reviewer disposition;
- known FOLLOWUP items that are not Task 43 blockers.

Task 44 remains locked.

---

# BLOCKER VS FOLLOWUP

Task 43 BLOCKERS include:

- production package without authoritative geometry;
- ambiguous geometry owner;
- missing geometry version;
- orphan logical pad;
- disconnected same-net copper;
- invalid fixed route;
- package courtyard violation;
- copper crossing/clearance failure;
- inaccessible lifted component lead;
- draw/hit/probe disagreement;
- loose-part geometry warp;
- physical identity drift;
- deterministic identity drift for identical version/input;
- electrical regression;
- Task 39/40/41 regression or inability to execute mandatory current-run evidence;
- stale deferral capable of swallowing a completed frontier regression;
- negative canary unexpectedly passing;
- verifier FAIL with success process exit;
- unreviewed production workaround.

FOLLOWUP items after all contracts pass may include:

- cosmetic shading;
- richer silkscreen aesthetics;
- future rotation;
- SMD;
- relay package;
- multilayer routing;
- production autorouter improvements;
- route beauty beyond the existing accepted quality limits.

Do not block Task 43 on cosmetic future work.
Do not demote a real contract failure to FOLLOWUP.

---

# COMMIT / COMPLETION POLICY

Commit only if:

- every required current-run acceptance gate passes;
- all required negative canaries behave correctly;
- superseded deferrals are closed;
- independent Luna MAX reviewer returns `PASS`;
- the staged diff contains only intended R8 files;
- `git diff --cached --check` passes;
- `docs/CODEX_TASK_REPORT.md` is complete and truthful.

Suggested final implementation commit message:

`Complete Task 43 physical package contract recovery`

Do not merge to master.
Do not force-push.
Do not begin Task 44.
Follow the repository/user publication protocol in effect for this recovery branch; do not invent authorization for destructive history changes.

Return:

- final implementation commit SHA;
- exact validation commands/results;
- exact finite matrix counts;
- exact Task 39/40/41 results;
- negative-shell-canary result;
- independent reviewer disposition;
- any remaining non-blocking FOLLOWUP items.
