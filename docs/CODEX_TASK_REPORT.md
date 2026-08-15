# Task 25 — First Real Seeded Procedural PCB Generator

Status: complete

Approved baseline: `7eace5c0e76be92a82c89b4819bb531db7ca0463` (Task 24).

## Implementation

Task 25 adds `SeededPcbLayoutGenerator`, a small reusable physical-layout
stage shared by `LED_INDICATOR` and `DIODE_PROTECTED_INDICATOR`:

```text
logical circuit/netlist
    -> electrical validation
    -> seeded PCB layout generation
    -> renderer and interaction system
```

The generator consumes the existing logical board, stable component/pad/net
IDs, and the challenge seed. It does not create electrical connectivity,
faults, meter readings, simulation nodes, or repair decisions. The fixed
family-specific PCB layout classes were removed from the active path.

Supported procedural footprints are connector, axial resistor, axial diode,
and through-hole LED. Component-type rules provide recognizable bodies,
polarity cues, pad locations, keep-out regions, and accessible leads. The
connector remains near an edge, while board width, height, placement, and
routing vary by seed. The external parts tray remains separate from PCB
copper and is included as workbench geometry.

Placement uses deterministic seeded retries with spacing checks for component
bodies and pads. Routing uses a coarse, deterministic Manhattan grid with
obstacle-aware breadth-first search. It routes only the existing logical
net-to-pad relationships and records stable pad endpoints. Each candidate is
validated; failed placement/routing candidates are retried up to a bounded
limit, then generation fails clearly rather than drawing disconnected copper.

`PcbBoardLayout.validateGeometry` checks component and pad coverage, board
containment, legal spacing, trace endpoints and net identity, Manhattan path
geometry, keep-out avoidance, unrelated-trace crossing, finite coordinates,
and required-net representation. Same-seed geometry is compared through
stable fingerprints, while component and trace fingerprints make variation
independent of seed metadata.

## Determinism and variation

The dedicated developer verifier passed for both supported families:

- seeds 0, 2, and 3 repeat identically for the same logical board and seed;
- every seed pair is required to differ in at least two meaningful geometry
  properties among board outline, component placement, and routed paths;
- the installed layout is checked against deterministic regeneration;
- procedural geometry validation passed for all generated boards.

The browser verifier no longer uses fixed component canvas coordinates. With
the explicit `tsjVerifyGeometry=true` query flag, the renderer publishes a
read-only bridge containing current component, pad, and loose-part hit
locations. Verification still dispatches real CDP mouse input at those
locations; the bridge does not mutate state or expose electrical answers.

The stale `LedIndicatorFamilyState.require(...)` messages were also changed
to family-neutral wording.

## Validation results

- JDK 8 production build: PASS; all five GWT permutations compiled and
  linking succeeded.
- Existing LED/resistor browser verifier: PASS, 15/15 routes.
- Existing diode verifier: PASS, 3/3 routes.
- Existing LED physical verifier: PASS, 3/3 routes.
- Procedural layout verifier: PASS.
- Resistor normal-player flow on generated geometry: PASS.
- Diode normal-player flow on generated geometry: PASS.
- LED normal-player flow on generated geometry: PASS.
- No route timeouts, unhandled JavaScript errors, or failure-class console
  messages were reported by the final browser runs.

## Visual evidence

All screenshots were captured from the production preview after the final
JDK 8 build and pixel-inspected for a nonblank application, visible board,
recognizable footprints, connected-looking copper, usable labels, and no
obvious overlap:

- [led-seed-0.png](task-evidence/task-25/led-seed-0.png) — LED board seed 0.
- [led-seed-2.png](task-evidence/task-25/led-seed-2.png) — materially
  different board size, placement, and route shape for seed 2.
- [led-seed-3.png](task-evidence/task-25/led-seed-3.png) — LED board seed 3.
- [diode-seed-0.png](task-evidence/task-25/diode-seed-0.png) — diode board
  seed 0 with visible D1 polarity band.
- [diode-seed-3.png](task-evidence/task-25/diode-seed-3.png) — diode board
  seed 3 with a different physical arrangement and visible band.
- [component-selected.png](task-evidence/task-25/component-selected.png) —
  selected LED1 state with live component details and controls.

## Persistent preview

The detached production preview remains running after launcher exit:

- LED seed 0: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=0`
- LED seed 3: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=3`
- diode seed 3: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=diode&seed=3`

The seed-0 endpoint returned HTTP 200 during final preview verification, and
the captured seed-0 and seed-3 boards visibly differ.

## Known limitations and next task

Task 25 keeps all footprints horizontal; useful orientation randomization was
deferred to avoid a renderer rewrite. The router is intentionally modest and
one-sided, does not perform manufacturing DRC, and does not insert automatic
jumpers. The current generator targets the two simple existing families and
does not add a new electrical component or fault.

Recommended next task: add procedural placement/routing coverage for the next
existing functional family, then introduce explicit real jumper geometry only
where the one-sided router cannot complete a valid route.
