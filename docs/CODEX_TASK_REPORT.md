# Task 34(A) — Core Extensibility Hardening and Validation Policy

## Status

Complete. Primary architect disposition: FINAL PASS. All executable subagent
stage reviews passed, the architect-owned visible Browser gate passed, and no
push was performed. Task 35 remains the next eligible roadmap milestone and
was not started.

Final commit message: Task 34(A): harden extensibility boundaries

## Summary

Task 34(A) hardens the physical-part, workbench, PCB-rendering, and instrument
extension boundaries while preserving CircuitJS as the electrical source of
truth. The work is staged as:

- Stage A: runtime-owned PhysicalPart, PhysicalBoardSlot, package, terminal,
  pad/net identity, capability, rating, provenance, failure, and simulation
  backing contracts.
- Stage B: capability-owned workbench operations and generic discovery for
  remove, lift, reconnect, restore, replacement, catalog, and loose
  inspection.
- Stage C: provider-owned installed/loose PCB rendering, terminal geometry,
  hit/selection bounds, polarity/orientation, pads, and probe targets.
- Stage D: genuinely pluggable InstrumentMode providers with provider-owned
  measurement, lifecycle, power policy, polarity, cleanup, and dynamic
  production UI registration.

The implementation preserves family-specific electrical topology and
validation while removing component-type/reference-ID dispatch from common
workbench and renderer orchestration. A future-shaped instrument provider is
registered through the production registry/controller path and is exercised by
the architecture canary.

## Review and recovery protocol

- Sol Max diagnostic pass produced the staged recovery order A through E and
  identified the reusable PhysicalPart, slot, capability, render-provider,
  and InstrumentMode seams.
- Stage A had three bounded review failures; the user-authorized Sol Max
  corrections repaired family-owned state, runtime identity/capability
  ownership, and generated-fault ownership for secondary failures.
- The main architect then completed the independent visible LED/diode Stage A
  gate before Stage B began.
- Stage B, Stage C, and Stage D each received fresh independent reviewer
  disposition. Stage B PASS, Stage C PASS, and Stage D PASS.
- The only Stage C finding classified as out of stage was provider-owned
  rendering before Stage C implementation; it was not used to weaken the
  Stage A gate.
- The Stage D reviewer initially found a real production-discovery blocker.
  A bounded correction added post-construction visible-provider registration
  and a production-visible canary; the fresh review then returned PASS.

## Architect-owned visible Browser gate

After all executable subagent reviews, the primary architect used the visible
in-app Browser on fresh seed-3 routes:

- LED route: the board rendered normally with R1 color bands, LED1 body and
  polarity, copper, pads, J1, and labels in the expected locations. CUA
  selection of LED1 exposed its type, part, lead identities, and generic
  component controls. CUA selection of R1 exposed markings and R1.1/R1.2
  terminal identities. With power off, Lift lead 1, Reconnect lead 1, and
  Remove component were reachable; the lift/reconnect cycle restored R1
  identity and the board powered on again.
- Diode route: the board rendered R1, D1, LED1, copper, pads, J1, and labels.
  D1 was visibly recognizable with its cathode stripe on the expected side.
  CUA selection exposed D1.A and D1.K; R1 selection exposed its markings and
  terminal identities. With power off, Lift lead A, Reconnect lead A, and
  Remove component were reachable; the lift/reconnect cycle restored D1
  identity and powered operation.
- No player-visible private original resistance, rating, stress/damage,
  injected-fault, or fault-infrastructure information appeared. Current
  browser diagnostics contained only CircuitJS convergence and expected
  unconnected-node log entries from lift actions; there were no error or
  warning entries attributable to the candidate.

The visible gate screenshots were surfaced directly in the architect session.
The written gate record is
docs/task-evidence/task-34a/visible-browser-gate.md. Supporting production
evidence remains under docs/task-evidence/task-34 and
docs/task-evidence/task-34-review2.

## Closed validation set

- Visible in-app Browser LED and diode seed-3 gate: PASS.
- Architecture and renderer provider-boundary verifiers: PASS.
- Procedural layout verifier: PASS.
- Seeded LED core matrix, seeds 0/2/3: 15/15 PASS.
- Diode, diode-short, parallel, and LED-parts matrices, seeds 0/2/3:
  PASS.
- Wrong-repair and stress electrical verifiers: PASS.
- LED, diode, parallel, wrong-repair, and stress normal-player routes:
  PASS. Stress normal-player confirmed natural secondary-open behavior,
  no diagnostic UI, and no console/page exceptions.
- PowerShell parser checks for verify-browser.ps1 and
  verify-renderer-boundary.ps1: PASS.
- git diff --check: PASS; Git emitted only expected line-ending conversion
  warnings for the existing Windows worktree.
- The final architect build rerun was environment-limited: this host exposes
  OpenJDK 21 while GWT 2.7.0 requires JDK 8. The delegated implementation
  and review rounds recorded JDK 8/GWT production-build PASS; no source
  change was made to bypass the project’s JDK 8 guard.

## Scope boundaries and handoff

No AGENTS.md policy change, normal-player harness rewrite, Task 35 work,
commit push, or unrelated cleanup was performed. The known distinction
between visible architect validation and subagent headless validation remains
the project rule. The next eligible milestone is Task 35 — Generalized
Physical Part Specifications; it is identified only and was not started.

---

# Task 34 — Component Ratings and Stress/Damage v1

## Status

Complete. Primary architect disposition: `FINAL PASS`. Task 35 is the next
eligible roadmap milestone and was not started. No push was performed.

Final commit message: `Task 34: add resistor stress damage`

## Summary

Task 34 adds a narrow resistor-only stress/damage vertical slice. Physical
resistors carry immutable hidden rated wattage, while
`ResistorStressDamageSystem` reads live solved CircuitJS resistor power and
advances deterministic service-time damage. Severe overload produces a real
secondary resistor-open state on the same physical part. Correctly rated and
mildly overloaded replacements survive the bounded validation window.

The original generated customer fault remains owned by its original physical
resistor. Secondary damage is independent state on the replacement and changes
the CircuitJS graph rather than selecting a special complaint or completion
answer. Task 35's generalized physical-part specification architecture and all
other component damage classes remain out of scope.

## Architectural decisions

- `ResistorNameplate` stores immutable hidden rated wattage. Catalog entries
  preserve that specification, and each acquisition creates a distinct
  `PhysicalResistorPart` with its own immutable rating and CircuitJS backing.
- `ResistorStressDamageSystem` owns current physical part, rated watts, live
  solved watts, stress ratio, accumulated damage, service time, failure time,
  secondary failure state, and reset behavior.
- Actual stress is `abs(ResistorElm.getPower()) / ratedWattage`; catalog
  resistance is not used as a substitute for solved electrical stress.
- `ResistorSecondaryOpenPath` owns a real CircuitJS `SwitchElm` in the
  physical resistor's public second-terminal path. Opening it collapses branch
  current naturally and leaves the same physical ID installed or loose.
- The secondary path is separate from `GeneratedFaultBinding`; the original
  loose `R1_ORIGINAL` retains its original generated fault while a replacement
  may independently fail.
- Service time is derived from CircuitJS simulation time during normal
  operation. A developer-only service-time seam advances a known interval
  without sleeping. Power-off, loose parts, and active temporary meter
  overlays do not accumulate normal service damage.
- Reset clears secondary damage and closes the secondary path while preserving
  original-fault ownership. Auxiliary component bindings retarget whenever a
  different physical resistor is installed, including catalog-to-original and
  failed-part reinstall paths.
- Normal player text and accessibility content expose resistance/state and the
  existing complaint only; wattage, stress, accumulated damage, and failure
  diagnostics remain developer-only.

## Concrete deterministic electrical proof

Fixture: LED challenge, seed `3`, 12 V supply, original generated R1 fault
`LED_R1_INCORRECT_VALUE/RESISTOR_INCORRECT_VALUE`, original physical part
`R1_ORIGINAL`.

| Case | Physical part / resistance | Rated W | Solved W before failure | Stress ratio | Service / failure time | Result |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| A correct | `R1_CATALOG_PART_2` / 1000 Ohm | 0.25 | 0.10431712989518951 | 0.41726851958075806 | 10 / — | Damage 0; same ID/backing; LED operating and repair contract completed |
| B severe | `R1_CATALOG_PART_0` / 220 Ohm | 0.25 | 0.4606930416523886 | 1.8427721666095545 | 3.5 / 2.815850719609588 | Damage 1.2429636186414272; same ID opened; post-failure current `2.007372046364253e-24 A`; LED dark/nonfunctional |
| C mild | `R1_CATALOG_PART_1` / 330 Ohm | 0.22 | 0.3095359534060624 | 1.4069816063911926 | 10 / — | Damage 0.8281701397037783; survived; current remained nonzero |

Case B initially had nonzero current and an operating LED before the threshold.
After failure, CircuitJS reported approximately zero current and the existing
solver-backed functional status observed nonfunctional behavior. Removing and
reinstalling the failed part preserved its ID and open path. The original
`R1_ORIGINAL` remained loose and fault-owned throughout.

Case D advanced an already stressed but not-yet-failed severe part for 5
seconds with board power off; damage and service time did not increase.
Powering on resumed accumulation from the existing state. Case E exercised
the active DC-voltage measurement transaction; its temporary stimulus left
persistent damage and service time unchanged. The stress subsystem also gates
all active measurement overlays, including resistance, continuity, and diode
stimuli. Case F reset the challenge deterministically: damage returned to 0,
the secondary path closed, ratings reproduced, and original-fault ownership
remained intact.

## Browser evidence

The primary player-facing manual gate was completed through Computer Use on the
active Windows desktop in Edge. Genuine visible mouse/keyboard actions selected
original R1, powered off the board, removed it, selected/acquired and installed
the 220 Ohm catalog replacement, powered on, and showed the initially operating
LED with the original complaint and no wattage/stress/damage diagnostic. Only
the permitted developer service-time seam was then used; the same installed
part was visibly observed after secondary open with the LED dark and no
diagnostic UI.

Direct Computer Use screenshots, captured from the visible production window,
are:

- `docs/task-evidence/task-34/computer-use-severe-overload-powered.png`
- `docs/task-evidence/task-34/computer-use-secondary-failure.png`

The following are supporting headless Edge/CDP production screenshots only:

- `docs/task-evidence/task-34/initial-board.png`
- `docs/task-evidence/task-34/severe-overload-powered.png`
- `docs/task-evidence/task-34/secondary-failure.png`
- `docs/task-evidence/task-34/correct-restored.png`

CDP/verifier routes support deterministic solver, identity, graph, and
regression checks; they are not substitutes for the direct Computer Use
player-facing gate.

## Files changed

Product/runtime and verifier:

- `src/com/lushprojects/circuitjs1/client/CirSim.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedComponentBindings.java`
- `src/com/lushprojects/circuitjs1/client/LedIndicatorGenerator.java`
- `src/com/lushprojects/circuitjs1/client/ParallelDualIndicatorGenerator.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalResistorPart.java`
- `src/com/lushprojects/circuitjs1/client/ReplacementDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/ResistorCatalogEntry.java`
- `src/com/lushprojects/circuitjs1/client/ResistorNameplate.java`
- `src/com/lushprojects/circuitjs1/client/ResistorReplacementCatalog.java`
- `src/com/lushprojects/circuitjs1/client/ResistorSlotController.java`
- `src/com/lushprojects/circuitjs1/client/ResistorSecondaryOpenPath.java`
- `src/com/lushprojects/circuitjs1/client/ResistorStressDamageDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/ResistorStressDamageSystem.java`
- `scripts/verify-browser.ps1`

Documentation and evidence:

- `AGENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/CODEX_TASK_REPORT.md`
- the six PNGs under `docs/task-evidence/task-34/` listed above

## Closed validation set

- JDK 8 / GWT production build: PASS; `scripts/build.ps1` compiled and linked
  all five permutations.
- PowerShell parser validation for `scripts/verify-browser.ps1`: PASS.
- `-StressDamage`: PASS with Cases A–F and the electrical values above.
- `-StressDamageNormalPlayer`: PASS as supporting CDP proof; direct manual
  validation is recorded separately above.
- LED seed matrix `-Seeds 0,2,3`: PASS for all 15 existing routes.
- Existing `-WrongRepair` and `-WrongRepairNormalPlayer`: PASS.
- Existing resistance, continuity, diode, and DC-voltage measurement checks:
  PASS through the resistance/meter/diode developer routes and regression
  matrix.
- Generated-fault, challenge/scenario, repair-status, and completion checks:
  PASS.
- Parallel and diode family regressions, including diode-short and LED-parts:
  PASS for seeds 0, 2, and 3.
- Existing normal-player LED, parallel, diode, and generic repair flows:
  PASS for player seed 3.
- Procedural layout, stable identity, attachment, no-duplicate-backing, and
  auxiliary-binding checks: PASS.
- `git diff --check`: PASS.
- Final staged `git diff --cached --check`: PASS after staging the intended
  Task 34 files.

The known concurrent legacy-CDP WebSocket race was reproduced only as a
test-harness issue; serialized runs passed and no player-facing defect was
demonstrated. It is a `FOLLOW-UP`, not a Task 34 product blocker.

## Protocol results and handoff

- Coder result: Franklin `COMPLETE`; implementation and both bounded
  correction passes reported complete, with no source changes in the final
  documentation/evidence correction.
- Reviewer result: Einstein final independent review `PASS`.
- Reviewer findings and classifications:
  - Initial `BLOCKER`: missing required Computer Use rule and accepted visible
    evidence; resolved by exact `AGENTS.md` rule and direct evidence files.
  - Initial `BLOCKER`: stale auxiliary binding after existing-part install;
    resolved by retargeting the binding and adding catalog/original/reinstall
    verifier assertions.
  - Initial `FOLLOW-UP`: stale roadmap pointer; resolved in final roadmap docs.
  - Correction review `BLOCKER`: first wording/evidence correction was not
    exact/preserved; resolved in the final bounded correction.
  - Correction review `FOLLOW-UP`: same roadmap pointer drift; resolved.
  - Final review: `PASS`; no unresolved `BLOCKER`, `FOLLOW-UP`, or `BACKLOG`
    finding remains for this milestone.
- Primary architect review rounds: one; final result `FINAL PASS`.
- Escalation-architect review: not required.
- Known limitations: the accelerated damage model is intentionally an
  approximate deterministic service model, not thermal/materials physics; it
  covers only resistor power stress and open failure. Customer-return history,
  scoring, economy, cascading damage, and generalized component ratings remain
  future work.
- Next roadmap milestone: Task 35 — Generalized Physical Part
  Specifications; identified only and not started.
- Commit message: `Task 34: add resistor stress damage`.
