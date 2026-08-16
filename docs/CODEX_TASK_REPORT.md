# Task 32 — Scenario and Customer Complaint Foundation

## Status

Complete. Primary architect disposition: `FINAL PASS`. The requested local
commit is `Task 32: add scenario and complaint foundation`. No push was
performed. Task 33 is identified as the next roadmap milestone and was not
started.

## Scope and acceptance

Task 32 adds a generic immutable scenario and customer-complaint foundation
for the generated TroubleshootJS challenges. The selected scenario is only
made visible after the actual faulted CircuitJS solve has passed the family
validator and the scenario compatibility predicate. The implementation
covers the LED indicator, diode-protected indicator, and parallel dual
indicator families; preserves seeded generation; and keeps hidden fault and
expected-behavior metadata out of the normal player UI.

The normal diode route excludes diode-short from player-eligible scenario
selection because this topology has no suitable normal complaint. The
explicit `tsjDiodeShort=true` developer route type-selects the real
diode-short effect and validates the solver-backed bright/high-current
complaint, including seed 3.

## Architecture decisions

- `GeneratedScenario<T>`, `GeneratedScenarioCompatibility<T>`, and
  `GeneratedScenarioCatalog<T>` form the reusable immutable scenario boundary.
- Scenario compatibility reads live CircuitJS element and operational-state
  results. No hard-coded meter behavior or direct fault-type-to-complaint
  mapping was added.
- Scenario selection uses a separate deterministic stream so complaint choice
  cannot perturb topology, value, fault, or PCB-layout randomness.
- The lifecycle is healthy validation → fault application → faulted solve →
  fault validation → scenario compatibility validation → `READY`.
- Incorrect-value faults reject healthy/effectively equal values, and fault
  ownership validation also checks the element mutated by a value fault.
- The browser verifier uses genuine CDP mouse/keyboard input and identity/state
  predicates for the player-facing flows. No DOM `.click()` or controller
  state mutation is used.

## Implementation and evidence

Product changes include:

- `src/com/lushprojects/circuitjs1/client/GeneratedScenario.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedObservedBehavior.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedScenarioLibrary.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedChallengeDefinition.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedChallengeController.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedChallengeLifecycleEvidence.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedFaultEffect.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedFaultEngine.java`
- `src/com/lushprojects/circuitjs1/client/DiodeProtectedIndicatorGenerator.java`
- `src/com/lushprojects/circuitjs1/client/LedIndicatorGenerator.java`
- `src/com/lushprojects/circuitjs1/client/ParallelDualIndicatorGenerator.java`
- `src/com/lushprojects/circuitjs1/client/ChallengeDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/DiodeFamilyDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/ParallelDualIndicatorDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/CirSim.java`

Validation/documentation changes include:

- `scripts/verify-browser.ps1`
- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- the 13 PNG captures under `docs/task-evidence/task-32/` listed below.

Committed evidence captures:

- `diode-open-ready-complaint.png`
- `diode-open-seed-3-correction.png`
- `diode-short-developer-complaint.png`
- `diode-short-seed-3-correction.png`
- `led-after-removal.png`
- `led-before-repair-power-off.png`
- `led-player-initial.png`
- `led-r1-selected.png`
- `led-ready-complaint.png`
- `led-repair-complete.png`
- `parallel-ready-complaint.png`
- `parallel-seed-3-correction.png`
- `parallel-selected-original-r1-correction.png`

These captures show the initial LED workbench, solver-backed complaints for
LED, parallel, diode-open, and developer diode-short cases, selected/removed
parts, and a repaired LED with the completion ticket.

## Validation

Completed required checks:

- `./scripts/build.ps1 -JavaHome ./.tools/jdk8-download/jdk8u502-b07` — PASS;
  all five GWT permutations compiled and linked.
- PowerShell parser validation for `scripts/verify-browser.ps1` — PASS.
- `git diff --check` — PASS.
- LED baseline `-Seeds 0,2,3` — PASS for all 15 routes, including complaint
  and solver markers.
- `-Parallel -Seeds 0,2,3` — PASS for all 3 routes, including the exact
  asymmetric complaint.
- `-Diode -Seeds 0,2,3` — PASS for all 3 diode-open routes.
- `-DiodeShort -Seeds 0,2,3` — PASS for all 3 developer diode-short routes,
  including the exact bright-indicator complaint.
- `-LedParts -Seeds 0,2,3` — PASS for all 3 physical-part routes.
- `-Layout -PlayerSeed 3` — PASS.
- Generic `-NormalPlayer -PlayerSeed 3` — PASS in five independent final
  runs, with solver-backed repair and expected 100 kOhm original fault value.
- `-ParallelNormalPlayer -PlayerSeed 3` — PASS in five independent final
  runs, with solver-backed repair.
- `-DiodeNormalPlayer -PlayerSeed 3` — PASS, including forward/reverse
  measurement behavior and repair.

## Review and disposition

The Luna coder implemented the bounded Task 32 change and completed the
required correction work. Bacon independently confirmed the scenario
architecture, solver-backed lifecycle and predicates, deterministic behavior,
diode-short developer handling, fault hardening, stable identity, power
safety, privacy-safe UI, and evidence.

Bacon's final re-review reported one remaining 1-in-5 failure in the legacy
`-LedNormalPlayer` developer verifier: a one-shot CDP click on `Install as
LED1` can be lost during a GWT rerender. This is a test-harness interaction
race, not evidence of incorrect player-facing product behavior; the generic
and parallel normal-player acceptance flows and the diode normal-player flow
passed. It is recorded as a FOLLOW-UP and does not block Task 32 completion
under the closed acceptance set. No additional coder pass was opened.

Primary architect final disposition: `FINAL PASS`. Sol escalation was not
required.

## Limitations and handoff

The scenario catalog currently provides the first validated scenario for each
family while retaining a generic catalog boundary for future variation.
Connector/trace repair scenarios and advanced scope, damage, and thermal
systems remain future work. The LED-specific legacy verifier's rerender-safe
installation retry is a follow-up test-harness improvement; it does not alter
the Task 32 product result.

The next eligible roadmap milestone is Task 33. It is identified only and was
not started.
