# Task 33 — Wrong Repair Semantics and Post-Repair Validation

## Status

Complete. Primary architect disposition: `FINAL PASS`. Commit message:
`Task 33: validate wrong resistor repairs`. No push was performed. Task 34 is
identified as the next eligible milestone and was not started.

## Scope and acceptance

Task 33 lets a player install a physically compatible but electrically wrong
resistor. The installed physical part remains legal, its catalog value becomes
the actual CircuitJS resistor value, and the challenge completes only when the
generic solver-backed functional contract reports restored behavior.

The closed proof uses the existing LED challenge at seed 3. The board begins
with the original customer complaint, `Indicator does not light.` The player
removes R1, installs a 2.2 kOhm catalog resistor, powers the board, observes a
working but out-of-contract indicator, then removes it and installs a 1 kOhm
resistor. The final state completes through the same generic challenge path.

## Architectural decisions

- Added `GeneratedRepairStatus` to the existing
  `GeneratedChallengeBehaviorContract` / `GeneratedRepairValidator` boundary.
  Validators classify live solved behavior as
  `STILL_FAULTED_OR_NONFUNCTIONAL`, `DEGRADED_BUT_OPERATING`, or
  `CORRECTLY_RESTORED`.
- `GeneratedChallengeController` latches completion only for
  `CORRECTLY_RESTORED`; no installed resistance, catalog ID, or hidden
  expected-part comparison decides completion.
- The existing physical mutation ownership remains in
  `ResistorSlotController`. Catalog specifications, acquired physical part
  instances, installed state, and CircuitJS `ResistorElm` backing remain
  distinct.
- Normal UI retains the original complaint and does not reveal “wrong
  resistor,” fault identity, or hidden original values. Status values are
  internal verifier state only.
- No resistor ratings, stress, thermal, delayed-failure, or secondary-damage
  systems were added; those remain Task 34 work.

## Deterministic wrong-repair evidence

Seed 3 selects a 12 V LED board with a generated 1 kOhm R1 requirement. The
developer verifier used the real slot controller and CircuitJS graph:

- Removed/open R1: status remained
  `STILL_FAULTED_OR_NONFUNCTIONAL`; completion remained false.
- Installed wrong catalog part: a distinct physical instance with a 2.2 kOhm
  immutable nameplate and actual `ResistorElm` resistance of 2200 Ohm. Solved
  LED and resistor current was nonzero and matched; the LED was illuminated,
  but current was above 1 mA and below the healthy 5-15 mA contract. Status was
  `DEGRADED_BUT_OPERATING`; completion remained false.
- Removed the wrong instance and installed a distinct 1 kOhm catalog instance.
  The solved current returned to the healthy range, status became
  `CORRECTLY_RESTORED`, and the generic challenge controller completed the
  challenge.
- The original faulted physical resistor remained loose and faulted throughout;
  inventory identity, slot occupancy, actual element binding, attachment
  reachability, and no-duplicate backing checks passed.

The normal-player route used genuine CDP mouse/keyboard input to perform the
same removal, catalog selection, installation, power, and subsequent repair
flow. In the wrong-powered screenshot, the 2.2 kOhm resistor is visibly
installed, the board is powered and operating, the original complaint remains,
and no completion or wrong-resistor diagnostic is shown.

## Files changed

Product and verifier changes:

- `src/com/lushprojects/circuitjs1/client/GeneratedRepairStatus.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedRepairValidator.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedChallengeBehaviorContract.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedChallengeBehaviorAdapter.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedChallengeController.java`
- `src/com/lushprojects/circuitjs1/client/LedIndicatorRepairValidator.java`
- `src/com/lushprojects/circuitjs1/client/ParallelDualIndicatorRepairValidator.java`
- `src/com/lushprojects/circuitjs1/client/DiodeProtectedIndicatorRepairValidator.java`
- `src/com/lushprojects/circuitjs1/client/ReplacementDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/CirSim.java`
- `scripts/verify-browser.ps1`

Documentation and visual evidence:

- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/task-evidence/task-33/initial-board.png`
- `docs/task-evidence/task-33/r1-selected.png`
- `docs/task-evidence/task-33/wrong-repair-powered.png`
- `docs/task-evidence/task-33/completed.png`
- `docs/CODEX_TASK_REPORT.md`

## Validation

The coder and independent reviewer completed the Task 33 closed validation
set. The primary architect inspected the final implementation, diff, and all
four PNGs; each screenshot is nonblank and shows the intended production
player state.

- JDK 8 / GWT production build: PASS; all five permutations compiled and
  linked.
- PowerShell parser validation for `scripts/verify-browser.ps1`: PASS.
- `git diff --check`: PASS before documentation completion.
- `-WrongRepair -PlayerSeed 3`: PASS; solver-backed 2.2 kOhm degraded state,
  incomplete challenge, and subsequent 1 kOhm restored completion.
- `-WrongRepairNormalPlayer -PlayerSeed 3` with the Task 33 evidence directory:
  PASS using genuine CDP mouse/keyboard input.
- LED baseline `-Seeds 0,2,3`: PASS for all 15 existing routes.
- Parallel, diode-open, diode-short, and LED-parts seed routes: PASS for
  seeds 0, 2, and 3.
- Existing generic LED, parallel, diode, and legacy LED normal-player routes:
  PASS in serialized runs.
- Physical replacement, generated-fault, scenario/complaint, power,
  measurement, identity, and attachment regression checks: PASS.
- Final completion checks after this report update: production build PASS and
  staged-diff whitespace validation (`git diff --cached --check`) PASS.

The Codex built-in browser initialization timed out during this task; the
repository's established headless CDP verifier was used successfully as the
accepted browser-validation fallback. Concurrent legacy CDP runs exposed a
test-harness/WebSocket race, classified as a non-blocking FOLLOW-UP; serialized
runs passed and no player-facing defect was demonstrated.

## Review and disposition

The coder reported `COMPLETE`, with no staging, commit, or push. The
independent read-only reviewer returned `PASS` and found no substantive
blockers. The reviewer confirmed solver-backed status classification, physical
identity and actual resistor backing, normal-player CDP interaction, privacy,
and nonblank screenshots.

Reviewer follow-up classification:

- `FOLLOW-UP`: concurrent legacy browser/CDP runs can race; serialized runs
  pass and the issue does not demonstrate incorrect player-facing behavior.
- No `BLOCKER` or `BACKLOG` findings remained.

Primary architect review: Round 1, `FINAL PASS`. The primary architect
independently reviewed the implementation and diff, verified the seed-3
electrical proof and screenshots, and confirmed that completion is gated by
the status contract rather than a hidden expected resistor value. Escalation
architect review was not required.

## Known limitations and handoff

The scenario catalog and complaint remain unchanged; incorrect repair does not
rewrite the service ticket. Task 34 component ratings, stress accumulation,
thermal behavior, delayed failures, and secondary damage remain future work.
The legacy concurrent CDP race is recorded as a follow-up and does not block
Task 33.

The next eligible roadmap milestone is Task 34 — Component Ratings and
Stress/Damage v1. It is identified only; implementation did not begin.
