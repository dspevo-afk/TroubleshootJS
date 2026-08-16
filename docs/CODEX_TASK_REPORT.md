# Task 31 — Fault Engine v1

## Status

Complete. The implementation, documentation, browser evidence, independent
review, and final validation are complete. The requested commit is:

Prevent premature subagent takeover

No push was performed.

## Scope and acceptance

Task 31 makes generated faults explicit data and CircuitJS-backed effects
rather than family-specific setup accidents. The bounded implementation
covers:

- resistor open;
- resistor incorrect value;
- diode open;
- diode short; and
- connector/open-path candidates represented in the engine and rejected as
  incompatible until the current workbench has a compatible connector/trace
  repair primitive.

The generated families remain seeded and reproducible. Fault metadata and
effective values stay internal; normal player UI exposes only the board,
measurements, complaint, and repair workflow.

## Implementation

GeneratedFaultEngine now creates explicit GeneratedFaultCandidate values and
CircuitJS-backed GeneratedFaultEffect implementations. Series switch effects
represent open paths, resistor effects change the real ResistorElm resistance,
and diode-short effects close a real parallel SwitchElm. Every candidate's
private simulation elements are retained in the canonical generated-element
ownership, inactive candidates are cleared, and the selected effect is chosen
deterministically from compatible candidates.

The seeded production mappings are:

- LED and parallel families: seeds 0 and 2 select resistor-open; seed 3
  selects resistor-incorrect-value.
- Diode-protected family: seeds 0 and 2 select diode-short; seed 3 selects
  diode-open.

Family fault validators now verify the actual solved symptom for each selected
fault. The existing generic challenge controller still gates READY and
functional completion on CircuitJS-backed validation; no fake meter readings
or UI-only completion answers were added.

The player verifier now checks the seed-3 failed original resistor for the
expected 100 kOhm effective value, not merely any numeric reading.

## Files and evidence

Product implementation changed the generated-fault boundary, the three
functional-family generators/validators, physical resistor/diode terminal
mapping, challenge ownership/lifecycle validation, and developer verifiers.
New product files are:

- src/com/lushprojects/circuitjs1/client/GeneratedFaultCandidate.java
- src/com/lushprojects/circuitjs1/client/GeneratedFaultEffect.java
- src/com/lushprojects/circuitjs1/client/GeneratedFaultEngine.java

Documentation and validation changes are:

- AGENTS.md — added the permanent Delegation Ownership and Patience
  Protocol. This policy-only correction did not modify product source.
- docs/ARCHITECTURE.md — documented the fault candidate/effect boundary.
- docs/ROADMAP.md — marked Task 31 complete and Task 32 as next.
- scripts/verify-browser.ps1 — strengthened the seed-3 effective-value
  assertion and corrected the resistance-flow expectation.

Production browser evidence was captured and pixel-inspected:

- docs/task-evidence/task-31/initial-board.png
- docs/task-evidence/task-31/led-selected.png
- docs/task-evidence/task-31/led-removed-parts-tray.png
- docs/task-evidence/task-31/repaired-board.png

## Validation

All final checks below passed:

- ./scripts/build.ps1 -JavaHome ./.tools/jdk8-download/jdk8u502-b07
  — all five GWT permutations compiled and linked.
- ./scripts/verify-browser.ps1 -TimeoutSeconds 120 -Seeds 0,2,3
  — all LED routes passed on final rerun; the first run had one transient
  seed-2 resistance exception, and the isolated seed-2 rerun passed all five
  routes.
- ./scripts/verify-browser.ps1 -TimeoutSeconds 180 -Diode -Seeds 0,2,3
  — all diode routes passed.
- ./scripts/verify-browser.ps1 -TimeoutSeconds 180 -Parallel -Seeds 0,2,3
  — all parallel routes passed.
- ./scripts/verify-browser.ps1 -TimeoutSeconds 180 -NormalPlayer -PlayerSeed 3
  — solver-backed repair passed; original measured 100 kOhm.
- ./scripts/verify-browser.ps1 -TimeoutSeconds 180 -LedNormalPlayer -PlayerSeed 3
  — reversed replacement remained nonfunctional and correct repair passed.
- ./scripts/verify-browser.ps1 -TimeoutSeconds 180 -DiodeNormalPlayer -PlayerSeed 3
  — forward/reverse diode behavior and repair passed.
- ./scripts/verify-browser.ps1 -TimeoutSeconds 180 -ParallelNormalPlayer -PlayerSeed 3
  — solver-backed supply measurement and repair passed.
- ./scripts/verify-browser.ps1 -TimeoutSeconds 180 -LedParts -Seeds 0,2,3
  — all physical LED-part routes passed.
- ./scripts/verify-browser.ps1 -TimeoutSeconds 180 -Layout -PlayerSeed 3
  — procedural layout verification passed.
- git diff --check — passed; Git emitted only expected LF/CRLF conversion
  warnings.

## Multi-agent review

The initial coder produced the bounded Task 31 candidate. After the coder
became unusable, its valid work was preserved; the coder was later resumed for
the verifier-only correction and returned PASS without changing files.

The independent reviewer first returned FAIL for the weak browser assertion
and identified two medium hardening concerns. The assertion was tightened
without touching product source. On re-review, the reviewer confirmed the
assertion and returned exactly PASS; the two source concerns were judged
non-blocking for the current package-private, generated-only v1 scope because
all production call sites pass the target R1 element, all production
incorrect-value effects use nominal ×100, and fault validation blocks READY.

Primary architect final disposition: FINAL PASS.

The new AGENTS.md protocol preserves the existing two coder/reviewer correction
passes, three Luna final-review rounds, and Sol escalation rules. It clarifies
that active coders and reviewers own their phases until disposition and must be
allowed to complete naturally.

## Limitations and handoff

Connector/open-path effects are modeled as explicit incompatible candidates
until a connector, jumper, or trace-repair primitive can provide a valid
player repair path. Complaint text remains the existing generic indicator
symptom; Task 32 owns complaint selection that varies with the actual fault.
An unrelated diode seed-1 layout-generation failure remains outside this
milestone and was not changed.

The next eligible roadmap milestone is Task 32 — Scenario and Customer
Complaint Foundation. It is identified only; it was not started.
