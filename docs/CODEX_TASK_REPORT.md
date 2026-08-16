# Task 27 — First Genuine Parallel Circuit, KCL, and Parallel Measurement Validation

Status: complete

Approved baseline: `c4180400855906f418bf2a8138e643f73cd5a526` (Task 26).

## Result

Added the first player-facing `PARALLEL_DUAL_INDICATOR` family with topology
`DUAL_PARALLEL_BRANCHES`. It is a real CircuitJS network, not a parallel-reading
facade:

```text
VIN -> R1 -> LED1 -> GND
VIN -> R2 -> LED2 -> GND
```

The stable board model contains `J1`, `R1`, `LED1`, `R2`, and `LED2`, with four
nets. `VIN` has `J1.1`, `R1.1`, and `R2.1`; `GND` has `J1.2`, `LED1.K`, and
`LED2.K`. Seeds 0, 2, and 3 select 5 V / 330 Ohm + 680 Ohm, 9 V / 680 Ohm +
1.5 kOhm, and 12 V / 1 kOhm + 2.2 kOhm configurations.

## Electrical validation

`ParallelDualIndicatorDeveloperVerifier` validates, for seeds 0, 2, and 3:

- both real LEDs illuminate in the healthy circuit;
- both branches carry positive solved current and branch 1 carries more;
- KCL uses the normalized CircuitJS source current:
  `I_source_delivery = I_R1 + I_R2`;
- both branch voltage sums equal the solver-derived supply voltage;
- transient solver-node identity joins the required three-pad VIN and GND nets;
- `R1 OPEN` collapses only branch 1 while branch 2 current remains unchanged;
- solver-backed DC readings cover VIN, R2, and LED2;
- repair with the correct replacement restores both branches and KCL.

`ParallelResistanceMeasurementFixture` uses real `ResistorElm` elements in a
1 kOhm || 10 kOhm network. Existing active OHM measurement reports
approximately 909.09 Ohm in both orientations, then approximately 1 kOhm and
10 kOhm when each alternate branch is isolated. No meter result is calculated
from a displayed answer.

## Repair architecture

Added the reusable `ReplaceableResistorFamilyState` contract. Both the existing
LED family and the new parallel family provide the resistor slot, inventory,
catalog, and physical-part ID allocator. `ResistorSlotController` now uses the
slot's component ID and declared terminal order instead of assuming
`LedIndicatorFamilyState`, `R1`, `R1.1`, or `R1.2`.

The parallel workbench shows the resistor catalog, supports normal-player R1
removal/replacement, retains the faulted original as a distinct loose physical
part without exposing its numeric value, and does not expose replacement
workflows for fixed R2/LED1/LED2. Their PCB pads remain probeable.

## PCB and routing

The seeded one-sided PCB generator now handles the parallel family's multi-pad
VIN/GND nets with root-to-each-pad same-net copper. Layout validation checks all
endpoints, same-net merging, component keep-outs, unrelated-net clearance,
escape corridors, route quality, determinism, and cross-seed variation.

The unsound Task 26 early abort after three broad keep-out/clearance failures
was removed. Generation remains bounded by `MAX_ATTEMPTS` and preserves the
last failure diagnostic. Compact topology-aware placement remains deferred to
Task 28.

## Validation results

- JDK 8 production build: PASS; all five GWT permutations compiled and linked.
- Existing LED/resistor verifier: PASS, 15/15 routes.
- Existing resistor normal-player flow: PASS.
- Existing diode verifier: PASS, 3/3 routes.
- Existing diode normal-player flow: PASS.
- Existing LED physical verifier: PASS, 3/3 routes.
- Existing LED normal-player flow: PASS.
- Procedural layout verifier: PASS.
- Parallel electrical verifier: PASS, seeds 0/2/3.
- Parallel normal-player flow: PASS, solver-backed DC measurement and repair.
- No final browser route reported a JavaScript exception, failure-class console
  message, or timeout.

Measured wall-clock runtimes from the final production preview:

- `.\scripts\verify-browser.ps1 -Layout`: 24.62 seconds.
- `.\scripts\verify-browser.ps1 -Parallel`: 38.83 seconds for 3 routes.
- Full existing 15-route matrix: 27.07 seconds.

## Visual evidence

Final production-browser screenshots are under
[`docs/task-evidence/task-27/`](task-evidence/task-27/). Pixel inspection found
all six images nonblank and showing the intended application view:

- [`parallel-seed-0.png`](task-evidence/task-27/parallel-seed-0.png) — seeded
  5 V layout with both branches and verified repair state.
- [`parallel-seed-2.png`](task-evidence/task-27/parallel-seed-2.png) — seeded
  9 V layout with materially different placement/routing.
- [`parallel-seed-3.png`](task-evidence/task-27/parallel-seed-3.png) — 12 V
  initial faulted board; LED1 is dark and LED2 operates.
- [`parallel-faulted.png`](task-evidence/task-27/parallel-faulted.png) —
  powered faulted state with the same branch-specific symptom.
- [`parallel-measurement.png`](task-evidence/task-27/parallel-measurement.png)
  — live DC V mode reading 12 V across J1.
- [`parallel-repaired.png`](task-evidence/task-27/parallel-repaired.png) —
  R1 installed, both indicators illuminated, and the ticket reports verified
  repair.

## Persistent preview and limitations

The detached production preview remains running:

- parallel seed 0: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=parallel&seed=0`
- parallel seed 2: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=parallel&seed=2`
- parallel seed 3: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=parallel&seed=3`

This task validates one constrained dual-branch family, not arbitrary parallel
PCB synthesis. The board is intentionally oversized, footprints remain in the
existing simple one-sided style, R2 and both LEDs are fixed, and compact
topology-aware placement is deferred to Task 28. No capacitors, transistor
circuits, thermal damage, trace cutting, jumpers, or UI redesign were added.
