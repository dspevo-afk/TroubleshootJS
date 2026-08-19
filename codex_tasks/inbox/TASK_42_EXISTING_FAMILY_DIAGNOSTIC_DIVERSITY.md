# TASK 42 — EXISTING-FAMILY DIAGNOSTIC DIVERSITY PROOF

Repository:
`dspevo-afk/TroubleshootJS`

Expected starting implementation baseline:
`7abba4ae5816bb1631b2d3d212fb4ed963823ba5`
`Harden Task 40/41 diagnostic serviceability contract`

NOTE: This task prompt itself may have been added in a newer repository-only handoff commit. If master is newer than the implementation baseline above, inspect the newer commit(s) and confirm they contain only task-handoff/documentation changes before proceeding.

Before changing anything:
- Confirm the working tree is clean.
- Read `AGENTS.md`.
- Read `docs/ARCHITECTURE.md`.
- Read `docs/ROADMAP.md`, especially Tasks 40, 41, 42, and 43.
- Read `docs/CODEX_TASK_REPORT.md`.
- Inspect the current LED indicator generator/fault engine, generated diagnostic plan/serviceability catalogs, replacement/workbench machinery, `Task40DeveloperVerifier`, and `Task41DeveloperVerifier`.

## GOAL

Prove that the Task 40/41 diagnostic-solvability architecture can support genuine diagnostic diversity inside an EXISTING generated family.

Do this by extending the existing LED indicator family with a second physically distinct normal fault owner:

`LED1 internal open-circuit fault`

The existing R1-owned failures must remain.

After this task, a normal LED challenge must be capable of failing because either:
- R1 is the physical fault owner, or
- LED1 is the physical fault owner.

The player must therefore diagnose which physical component actually owns the failure rather than being able to infer "replace R1" merely from the family structure.

This is a proof task, not a broad feature-expansion task.

## CORE ARCHITECTURAL RULES

Preserve all existing project invariants:

1. CircuitJS remains the electrical source of truth.
2. Do not infer or fake diagnostic outcomes from fault metadata.
3. Do not use transient CircuitJS analyzed node numbers as durable identity.
4. Physical component/pad/net IDs remain stable.
5. Generated faults must be electrically enforced in the live CircuitJS graph.
6. Workbench actions must be real player-capable operations.
7. A fault is cured only through an admitted fault-clearing repair action.
8. Reconnect/restore/workflow actions must not masquerade as repair.
9. Diagnostic evidence must come from executable solver-backed observations and the real execution trace.
10. Candidate equivalence must remain repair-aware.
11. No hidden serviceability affordances may be added solely to make a challenge solvable.
12. Do not special-case Task 42 inside generic meter code, solver code, or completion logic.

## REQUIRED IMPLEMENTATION

### 1. ADD LED1 AS A REAL NORMAL FAULT OWNER

Add an LED internal-open fault to the existing LED indicator family.

Use an appropriate explicit `GeneratedFaultType` such as `LED_OPEN` if one does not already exist.

The fault must:
- belong physically to LED1,
- represent an internal open circuit of the LED package,
- be electrically enforced by real CircuitJS simulation infrastructure,
- remain associated with the original physical LED while it is installed, removed, or otherwise manipulated according to the existing physical-part/fault infrastructure rules,
- disappear only when the bad original is replaced by a different correct catalog LED,
- preserve all stable logical board identities,
- not require a new topology family.

Do NOT implement this as:
- validator-only metadata,
- a hard-coded displayed symptom,
- a fake current/voltage override,
- a diagnostic-plan special case,
- or a hidden switch that is not owned and managed through the established generated-fault infrastructure.

Inspect the existing diode/capacitor/transistor fault-effect patterns and use the cleanest established architecture.

### 2. REUSE THE EXISTING REPLACEMENT/WORKBENCH ARCHITECTURE

Do not invent a parallel LED replacement system if the project already has a generic or LED-capable replacement/catalog boundary.

A bad LED1 must be repairable through the normal player workbench path.

For `LED_OPEN`:

Physical fault owner:
`LED1`

Fault locus:
`component-internal`, unless the existing architecture has a more accurate established locus representation for an internally open two-terminal device.

Required isolation capability:
`REMOVE_COMPONENT`

Required fault-clearing repair:
`CATALOG_INSTALL`

Workflow actions:
Only those genuinely required by the existing workbench path. Do not add `RECONNECT`/`RESTORE` merely to satisfy a contract.

Required retest:
`CUSTOMER_RETEST`

The replacement must be a different physical part object from the original bad LED.

Reinstalling the original bad LED must NOT cure the fault.

### 3. PRESERVE THE EXISTING R1 FAULTS

Do not delete or weaken the current normal R1-owned LED-family failures.

The selected family should now have at least two distinct admitted physical fault-owner classes:
- R1
- LED1

Existing fault behavior and previous regression fixtures must continue to work.

### 4. MAKE THE TWO OWNERS GENUINELY DIAGNOSABLE

The Task 41 evidence machinery must be able to produce a legal solver-backed diagnostic path that separates the LED1-open candidate from the R1-owned candidates.

Use only measurements/actions already available to a normal player.

Prefer the smallest realistic observation set.

For example, depending on actual solver behavior, useful observations may include:
- powered DC voltage at existing public pads/nets,
- unpowered resistance/continuity/diode behavior at authentic physical terminals,
- legitimate isolation of a component if required.

DO NOT assume these observations distinguish the faults.

Run the real CircuitJS solver and prove it.

A likely useful physical distinction is:
- With R1 open, the downstream LED node may be pulled toward the LED/GND side.
- With LED1 internally open, the downstream node may rise toward VIN through healthy R1 because branch current is essentially zero.

That is only a hypothesis.

CircuitJS must decide the actual values. Do not encode that expected result into production logic.

If the current legal diagnostic plan produces indistinguishable signatures, improve the legal observation plan or isolation sequence.

Do NOT whitelist the pair as distinct merely because their owner IDs differ.

Task 41 must continue to require both:
- solver/evidence separation where required, and
- repair-semantic separation.

### 5. PROVE WRONG-OWNER REPAIRS FAIL

Add regression coverage proving that physical repair ownership matters.

At minimum:

A. `LED_OPEN` challenge:
- replacing/removing/reinstalling R1 must not cure the LED-owned fault,
- reinstalling the original bad LED1 must not cure it,
- installing a different correct LED replacement must cure it,
- normal customer retest must pass only after the correct repair.

B. At least one R1-owned LED-family fault:
- replacing LED1 must not cure the R1-owned generated fault,
- the correct R1 repair must still cure it.

Use the normal workbench/player operations wherever practical.

Do not mutate fault state directly inside the verifier just to create the expected result.

### 6. EXTEND TASK 40 SERVICEABILITY COVERAGE

`Task40DeveloperVerifier` must enumerate and validate the newly admitted normal `LED_OPEN` route.

It must prove:
- the challenge installs normally,
- the fault owner is LED1,
- runtime serviceability admission succeeds,
- the relevant physical terminals/bindings exist,
- the required replacement provider/catalog exists,
- wrong-owner actions do not falsely restore the board,
- original-part reinstall does not falsely restore the board,
- correct different-part catalog replacement restores it,
- `CUSTOMER_RETEST` passes afterward.

Update the normal-route count naturally based on the resulting corpus.

Do not hard-code an expected count merely because this prompt anticipates one additional route.

Document the final exact route count in `CODEX_TASK_REPORT.md`.

### 7. EXTEND TASK 41 DIAGNOSTIC-SOLVABILITY COVERAGE

`Task41DeveloperVerifier` must include the new `LED_OPEN` candidate in the normal corpus and exercise it through the normal diagnostic evidence path.

For every normal candidate, including `LED_OPEN`:
- plan admission must succeed,
- actual execution trace must be collected,
- solver-backed samples must be recorded,
- measured diagnostic depth must come from executed evidence,
- repair semantics must be recorded,
- real repair must be performed,
- `CUSTOMER_RETEST` must pass.

Pairwise candidate analysis must demonstrate that the LED1-owned candidate and the R1-owned candidates are not collapsed into an invalid equivalent-repair class.

Do not weaken:
- `REPAIR_EQUIVALENCE_REJECTED` behavior,
- reserved `RESTORE` rejection,
- connector-only observation rejection,
- trace consistency checks,
- declared-vs-executed separation,
- or existing negative fixtures.

If `LED_OPEN` exposes a legitimate bug in the generic Task 41 machinery, fix the generic machinery rather than special-casing `LED_OPEN`.

### 8. ADD A MINIMAL DIAGNOSTIC-OWNER-DIVERSITY CLASSIFICATION

Task 42 also needs to establish which existing families are genuinely multi-owner diagnostic challenges and which are still effectively guided/easy because all normal faults resolve to one physical owner.

Implement the smallest clean internal representation necessary.

A reasonable representation would be an enum similar to:

`GUIDED_EASY_SINGLE_OWNER`
`MULTI_OWNER_DIAGNOSTIC`

Names may differ if another name better matches existing architecture.

IMPORTANT:
This is NOT the future player difficulty-selection system.

Do not add difficulty UI.

The classification must be DERIVED from the admitted/serviceable normal candidate corpus and distinct physical repair owners.

Do not maintain a hand-written table that simply declares:
`LED = hard`, etc.

At minimum:
- the enhanced LED family must classify as multi-owner diagnostic,
- existing normal families with only one admitted physical fault owner must classify as guided/easy/single-owner,
- existing leaf/single-owner families remain valid regression fixtures and are NOT required to gain new faults in this task.

If a family already has multiple genuinely serviceable physical owners, classify it from the actual admitted corpus accordingly.

Do not confuse:
- number of fault types,
- number of diagnostic plans,
- number of seeds,
- or number of replacement candidates

with number of distinct physical repair owners.

### 9. PREVENT STRUCTURAL ANSWER LEAKAGE

The selected LED family should no longer reveal every normal repair merely through one obvious replaceable slot.

Do not hide component names, remove authentic measurements, fabricate ambiguous behavior, or disable legitimate workbench actions to manufacture difficulty.

Difficulty must come from real alternative physical fault ownership and real electrical observations.

### 10. KEEP NORMAL UI PHYSICAL AND HONEST

No Task-42-only diagnostic buttons.
No hidden test points.
No magic "identify bad component" affordance.
No metadata shown to the player.
No serviceability-only pad that would not physically exist.
No automatic isolation that the player could not perform.

Any normal-player measurement used by the verifier must correspond to an authentic physical probe surface and an existing instrument capability.

### 11. DO NOT START TASK 43

Task 42 should prove diversity inside one existing family.

Do NOT:
- create a new topology family,
- add challenge composition,
- build the difficulty selector,
- generalize every family,
- add broad new catalogs,
- implement scoring,
- redesign the PCB,
- or begin arbitrary-board diagnostic synthesis.

Keep this task narrow enough that its evidence remains auditable.

## VERIFIER / ACCEPTANCE REQUIREMENTS

The task is complete only if all of the following are demonstrated:

A. LED1 can own a real solver-enforced internal-open fault.

B. The LED family has at least two distinct admitted physical repair owners:
- R1
- LED1

C. The LED1-open fault has an executable normal-player serviceability path.

D. The LED1-open fault has an executable solver-backed diagnostic evidence path.

E. R1-owned and LED1-owned candidates are not incorrectly merged into the same equivalent-repair class.

F. Wrong-owner replacement does not cure the board.

G. Reinstalling the original bad LED does not cure the board.

H. A different correct LED catalog replacement does cure `LED_OPEN`.

I. `CUSTOMER_RETEST` passes after the correct repair.

J. Existing R1 repairs still behave correctly.

K. Diagnostic owner-diversity classification is derived from the admitted candidate corpus.

L. The enhanced LED family is classified as genuinely multi-owner diagnostic.

M. Existing single-owner families remain valid and are classified as guided/easy/single-owner rather than being falsely presented as higher-diagnostic-diversity families.

N. Existing Task 40 and Task 41 negative fixtures still pass.

O. Existing Task 39 family verifiers still pass.

P. Legacy meter, replacement, fault, physical-board, challenge, and generated-board verifiers still pass.

Q. Stable board component/pad/net identities survive the new fault path.

R. No temporary or private fault infrastructure leaks into logical PCB identity or normal player-visible component inventory.

S. No new topology family has been added.

## VALIDATION

Run the strongest validation available in the repository/environment, including:

1. JDK 8 GWT production compile/link using the established build process.

2. `Task40DeveloperVerifier` in the real application/browser runtime.

3. `Task41DeveloperVerifier` in the real application/browser runtime.

4. Existing Task 39 NPN/NMOS/RC verifier coverage.

5. Existing relevant generated-board, challenge, fault, replacement, physical-board, meter, stress/damage, and workbench verifiers.

6. Explicit Task 42 visible/runtime proof covering:
- R1-owned LED-family failure,
- LED1-owned open failure,
- wrong-owner repair rejection,
- original bad LED reinstall rejection,
- correct LED replacement success,
- customer retest success,
- pairwise Task 41 separation,
- derived owner-diversity classification.

7. Inspect browser/runtime output for uncaught exceptions.

8. Run `git diff --check` before staging and `git diff --cached --check` after staging.

For browser testing, use the built-in `@Browser` workflow by default and keep the shared browser view visible/watchable. Do not use `@Computer` unless explicitly authorized or there is no reasonable browser alternative.

If an environment limitation prevents one validation path:
- do not invent a PASS,
- use another real available validation path if possible,
- document exactly what was and was not run.

## DOCUMENTATION

Update:
- `docs/ARCHITECTURE.md` with the owner-diversity concept and why it is derived rather than hand-labeled.
- `docs/ROADMAP.md` to mark Task 42 complete only if all acceptance criteria are satisfied.
- `docs/CODEX_TASK_REPORT.md` with the full result.

`CODEX_TASK_REPORT.md` must include:
- final PASS/FAIL,
- exact commit task,
- files changed,
- new fault type and physical owner,
- exact final normal-route count,
- distinct owner count for the enhanced LED family,
- owner-diversity classification results for all existing normal families,
- Task 40 results,
- Task 41 results,
- solver sample/evidence summary,
- negative wrong-owner repair results,
- original-part reinstall result,
- correct LED replacement/customer-retest result,
- production build result,
- regression verifier results,
- any environment limitations,
- known limitations/concerns,
- recommended next task.

## TASK COMPLETION PROTOCOL

Follow `AGENTS.md`.

If validation succeeds:
1. Inspect `git diff` and `git status`.
2. Overwrite `docs/CODEX_TASK_REPORT.md` with this task's report.
3. Stage only intended files.
4. Run `git diff --cached --check`.
5. Review the complete staged diff.
6. Commit with a concise descriptive message, for example:
   `Add LED diagnostic fault diversity`
7. Follow the repository's current `AGENTS.md` publication/notification protocol exactly.
8. STOP after Task 42. Do not begin Task 43.

If validation fails:
- do not falsely mark Task 42 complete,
- do not begin Task 43,
- clearly document the blocker and failing evidence,
- follow the repository's failure/reporting protocol in `AGENTS.md`.

Do not begin Task 43.