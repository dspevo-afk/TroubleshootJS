# Task 28 — Topology-Aware Compact PCB Placement, Board Sizing, and Routing Courtyards

Status: complete

Approved baseline: `5de5eb874babdb16548e961b1e7f0ddfaf0ccf91`.

## Result

Replaced the family-sized/scattered PCB placement pass with a generic,
topology-aware generator for `LED_INDICATOR`,
`DIODE_PROTECTED_INDICATOR`, and `PARALLEL_DUAL_INDICATOR`.

The new placement stage builds `TopologyPlacementGraph` data from stable
`BoardComponent`, `BoardPad`, and `BoardNet` identities. Direct two-pad links
receive a strong attraction; shared VIN/GND nets encourage compact clusters.
Bounded candidates are scored using topology distance, routed length, bends,
component spacing, board area, unused area, silkscreen fit, and same-net reuse.
Seeded alternatives remain deterministic and valid.

`PcbComponentPlacement` now distinguishes the visible body keep-out from a
larger routing courtyard. Copper may enter a courtyard only for the exact
component endpoint pad and only through that pad's legal escape corridor. This
stricter rule is intentional simulator-layout/readability behavior: copper must
remain visible and probeable rather than disappearing beneath a through-hole
component.

The final board outline is derived after routing and silkscreen placement from
courtyards, pads, copper, and labels, then translated with a 26-pixel edge
margin. The external parts tray is excluded. `PcbBoardLayout` exposes an
explicit compactness/utilization metric and edge-margin measurement.

Same-net A* routing now prefers joining existing same-net copper trunks without
merging the physical pad targets.

## Validation results

- JDK 8 production build: PASS; all five GWT permutations compiled and linked.
- LED/resistor verifier: PASS, 15/15 routes.
- Resistor normal-player flow: PASS.
- Diode verifier: PASS, 3/3 routes.
- Diode normal-player flow: PASS.
- LED physical verifier: PASS, 3/3 routes.
- LED normal-player flow: PASS.
- Procedural layout verifier: PASS for all three families and seeds 0/2/3.
- Parallel electrical verifier: PASS for seeds 0/2/3.
- Parallel active-resistance fixture: PASS.
- Parallel normal-player flow: PASS; solver-backed voltage and repair.
- Courtyard regression: PASS for resistor, diode, LED, and connector traces.
- No required browser route reported a JavaScript exception, failure-class
  console message, or timeout.

Measured final production-preview wall-clock timings:

- `-Layout` all-family verifier: 13.41 s.
- LED single-seed generation/route verification proxy: 1.23 s.
- Diode single-seed generation/route verification proxy: 2.76 s.
- Parallel single-seed generation/route verification proxy: 1.89 s.

The family timings include browser startup/navigation and the existing verifier
route; they are bounded production timings rather than an isolated JVM profile.

## Visual evidence

Final production-browser screenshots are under
[`docs/task-evidence/task-28/`](task-evidence/task-28/):

- [`led-seed-0.png`](task-evidence/task-28/led-seed-0.png) — compact LED board
  with short series path and clear J1 labels.
- [`led-seed-3.png`](task-evidence/task-28/led-seed-3.png) — seeded alternate
  LED arrangement and board outline.
- [`diode-seed-0.png`](task-evidence/task-28/diode-seed-0.png) — compact diode,
  resistor, LED chain with exposed copper around courtyards.
- [`diode-seed-3.png`](task-evidence/task-28/diode-seed-3.png) — alternate
  diode-family layout with clear polarity marking.
- [`parallel-seed-0.png`](task-evidence/task-28/parallel-seed-0.png) — two
  readable branches with shared VIN/GND trunks.
- [`parallel-seed-2.png`](task-evidence/task-28/parallel-seed-2.png) — seeded
  parallel variation with compact common rails.
- [`parallel-seed-3.png`](task-evidence/task-28/parallel-seed-3.png) — second
  branch variation; no under-component copper illusion.

Pixel inspection found all seven images nonblank and showing the intended
production application. The boards are content-bounded, the parallel board is
larger because it contains two branches, directly connected pads are near each
other, J1 labels are readable, and no copper appears to pass beneath a
resistor, diode, LED, or connector courtyard.

## Preview URLs

The detached production preview remains running at port 8899:

- LED seed 0: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=0`
- LED seed 3: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=3`
- diode seed 0: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=diode&seed=0`
- diode seed 3: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=diode&seed=3`
- parallel seed 0: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=parallel&seed=0`
- parallel seed 2: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=parallel&seed=2`
- parallel seed 3: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=parallel&seed=3`

## Remaining limitations

This remains a bounded one-sided through-hole layout generator. Footprint
rotation, SMD packages, multilayer routing, vias, arbitrary netlists, and
professional manufacturing DRC are intentionally out of scope. Candidate
quality is heuristic and currently uses a fixed working grid; larger future
families will need additional placement regions and routing capacity. Task 29
is the next planned milestone: Player-Facing Component Identification Fidelity.
