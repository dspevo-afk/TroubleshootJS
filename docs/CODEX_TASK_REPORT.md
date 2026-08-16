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
