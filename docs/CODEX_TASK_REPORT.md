# Task 26 — Harden Procedural PCB Routing and Visual Believability

Status: complete

Approved baseline: `44805d074c5a7ffcaf679081fd3ab1eb3e437c09` (Task 25).

## Result

Task 26 keeps the logical board/netlist as the electrical source of truth and
hardened the physical stage shared by `LED_INDICATOR` and
`DIODE_PROTECTED_INDICATOR`:

```text
logical circuit/netlist
    -> electrical validation
    -> seeded PCB layout generation
    -> renderer and interaction system
```

The PCB generator does not create connectivity, faults, meter readings,
simulation nodes, or repair decisions. Stable identities including `R1.1`,
`LED1.A`, `LED1.K`, `D1.A`, and `D1.K` remain unchanged when placement changes.

## Procedural layout architecture

- `SeededPcbLayoutGenerator` uses reusable footprint rules for the connector,
  axial resistor, axial ordinary diode, and through-hole LED.
- Seeded attempts vary board outline, component coordinates, pad locations, and
  routed paths while retaining an accessible connector near an edge.
- Components and pads are placed with body/pad spacing checks and remain inside
  the board margin. Orientation remains horizontal for this first hardened
  version; rotation was deferred to avoid a renderer rewrite.
- The external parts tray remains separate workbench geometry, never copper.

## Placement, pad access, and routing

Each pad carries an explicit escape direction and bounded corridor. Horizontal
axial footprints escape left/right, connector terminals escape inward, and the
LED anode/cathode pads escape vertically downward from the rendered LED body.
The router and `PcbBoardLayout.validateGeometry` use the same corridor rule:
the exact connected pad may occupy its lead corridor, but unrelated copper may
not use it and the corridor must terminate outside the component keep-out.

Routing uses deterministic coarse-grid A* with Manhattan movement and a bend
penalty. It routes existing `BoardNet` pad relationships only. Component body
keep-outs, other pads, routed copper, and inflated unrelated-net clearance are
obstacles. Route points are retained at the routing-grid resolution so a later
polyline simplification cannot cut across an obstacle. A bounded retry loop
derives every candidate from `(seed, attempt)` and returns the best validated
candidate by route-quality score; it never fabricates a disconnected trace.

The exact seed-3 regression is covered directly: the GND trace from `J1.2` to
`LED1.K` must approach the cathode through the downward escape corridor, while
the corridor tip must be outside the LED keep-out. This prevents the original
`370,200 -> 370,190` body-entry failure.

## Minimum copper clearance

`PcbTraceRules` defines the reusable rendered trace contract:

- trace width: `9` pixels;
- minimum visible unrelated-net soldermask: `6` pixels;
- minimum unrelated centerline distance: `15` pixels;
- coarse routing occupancy inflation: one grid cell in every direction.

The router marks centerline cells and their clearance neighborhood. Different
nets cannot occupy, touch, overlap, share, or enter adjacent/diagonal inflated
cells; same-net copper may intentionally join. `PcbBoardLayout.validateGeometry`
also computes exact axis-aligned segment distances for every unrelated-net
pair, covering parallel segments, bends, corners, and perpendicular near
misses. Thus the rule accounts for actual rendered width and preserves a
visible green soldermask gap rather than making copper artificially thin.

## Geometry validation and determinism

Validation checks component and pad coverage, board bounds, stable endpoint/net
identity, Manhattan coordinates, legal endpoint escapes, component keep-outs,
body/pad overlap, required-net representation, trace crossings, route detour
and bend limits, silkscreen collisions, finite geometry, and the minimum
unrelated-net clearance. Repeated deterministic keep-out/clearance
contradictions fail early with a useful diagnostic instead of consuming all
80 candidate attempts.

`PcbLayoutDeveloperVerifier` generated both families for seeds 0, 2, and 3.
It proved:

- seed 0 repeated twice: identical geometry;
- seed 2 repeated twice: identical geometry;
- seed 3 repeated twice: identical geometry;
- each seed pair differed in at least two meaningful properties among outline,
  component geometry, and routed paths;
- all layouts passed route-quality, escape-corridor, label, and copper-clearance
  validation;
- the installed layout matched deterministic regeneration.

The browser geometry bridge remains read-only and developer-query-gated. Normal
player verification obtains current generated hit locations and still performs
real `Input.dispatchMouseEvent` input; no normal flow relies on stale board
coordinates or controller shortcuts.

## Verification results

Final production verification after the JDK 8 build:

- JDK 8 GWT production build, PRETTY: all five permutations compiled and
  linked;
- JDK 8 GWT production build, OBF: all five permutations compiled and linked;
- JDK 8 GWT production build, DETAILED: all five permutations compiled and
  linked;
- existing LED/resistor matrix: 15/15 PASS;
- diode electrical verifier: 3/3 PASS;
- LED physical verifier: 3/3 PASS;
- resistor normal-player flow on generated seed 3 geometry: PASS;
- diode normal-player flow on generated seed 3 geometry: PASS;
- LED normal-player flow on generated seed 3 geometry: PASS;
- procedural layout verifier, including copper-clearance regression: PASS;
- no route timeout, unhandled JavaScript error, or failure-class console
  message in the clean final runs.

The focused timing investigation showed the former long layout run was not an
A* performance problem: route attempts were generally below 90 ms. It exposed
and fixed an exact-distance validator error for vertical segments, after which
the complete clean `-Layout` verification finished in 7.2 seconds wall-clock.

## Visual evidence

All six final screenshots were captured from the final detached production
preview and pixel-inspected. Every image is a nonblank application view with a
fully visible board, recognizable parts, readable labels, no visible body
overlap, no obvious disconnected copper, and visible green soldermask between
unrelated parallel/nearby copper:

- [led-seed-0.png](task-evidence/task-26/led-seed-0.png) — LED family seed 0.
- [led-seed-2.png](task-evidence/task-26/led-seed-2.png) — materially different
  LED outline, placement, and routing for seed 2.
- [led-seed-3.png](task-evidence/task-26/led-seed-3.png) — LED family seed 3
  with a different board arrangement and +12V/GND connector cues.
- [diode-seed-0.png](task-evidence/task-26/diode-seed-0.png) — diode family
  seed 0 with D1 body and cathode band visible.
- [diode-seed-3.png](task-evidence/task-26/diode-seed-3.png) — diode family
  seed 3 with different placement/routing and readable D1 polarity marking.
- [component-selected.png](task-evidence/task-26/component-selected.png) —
  live LED1 selection state on generated geometry with contextual details.

## Persistent preview

The detached production preview remains running after launcher exit:

- LED seed 0: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=0`
- LED seed 2: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=2`
- LED seed 3: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=3`
- diode seed 0: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=diode&seed=0`
- diode seed 3: `http://127.0.0.1:8899/circuitjs.html?tsjChallenge=diode&seed=3`

The final seed-0 and seed-3 LED boards were loaded through the persistent
preview workflow and visibly differ in board dimensions, component placement,
and trace routing.

## Known limitations and recommended next task

Footprints remain horizontal; useful orientation randomization is deferred.
The router is intentionally modest, one-sided, coarse-grid, and not a
manufacturing DRC. It does not insert automatic jumpers, although the
architecture now has explicit pad corridors and clearance occupancy that can
support real jumper geometry later. The generator currently targets the two
simple existing families and adds no electrical component or fault.

Recommended next task: add procedural coverage for the next existing family or
introduce explicit real one-sided jumper representation only where a validated
route cannot be completed without it.
