# Latest Codex Task Report

## Task
Task #13: build the first interactive PCB workbench for the generated LED
indicator fixture.

## Summary
The generated LED board now opens as a one-sided interactive PCB while
CircuitJS continues to solve the hidden electrical graph. The workbench has
stable PCB pad probes, contextual component selection/actions, visible lifted
and removed resistor states, a parts tray, power-safe controls, and a compact
multimeter. `?tsjDebug=true` retains the backing schematic; arbitrary legacy
CircuitJS circuits retain their original schematic experience.

## Architecture Decisions
- `PcbBoardLayout`, component/pad placements, and trace geometry reference only
  stable board IDs and remain separate from CircuitJS elements and node numbers.
- `BoardPadProbeTarget` resolves through `BoardSimulationBindings`;
  `ComponentLeadProbeTarget` resolves through the declared component-side
  endpoint. Both use current renderer geometry and semantic generated-instance
  identity.
- `InstrumentController` accepts a generic hit-tested `ProbeTarget`; schematic
  and PCB pointer paths share selection, suppression, measurement, and cleanup.
- Physical component state is `INSTALLED`, `LEAD_LIFTED`, or `REMOVED` based on
  all declared connections. Canonical reconnect order and occurrence-count
  checks restore exact generated structure/export without disturbing power
  infrastructure or meter overlays.
- Contextual actions derive from generic connection bindings. Powered actions
  are disabled with inline guidance; normal interaction uses no alert.
- The renderer keeps pads and copper persistent while moving only component
  bodies/leads according to logical modification state.

## Files Changed
- Board modification/binding model and verifier classes under
  `src/com/lushprojects/circuitjs1/client/`.
- New PCB layout, placement, trace, renderer, controller, and probe-target
  classes under `src/com/lushprojects/circuitjs1/client/`.
- `CirSim.java`, `InstrumentController.java`, `LedIndicatorGenerator.java`, and
  `war/circuitjs.html` for workbench lifecycle, input, controls, and styling.
- `docs/ARCHITECTURE.md`, this report, and visual captures in
  `docs/screenshots/task13/`.

## Build And Browser Verification
- Exact build command:
  `$java8Home = Join-Path $env:TEMP 'TroubleshootJS-build-probe\temurin8'; & .\scripts\build.ps1 -JavaHome $java8Home`
- Result: all five GWT permutations compiled and linked successfully with JDK 8.
- Exact complete verifier URL:
  `http://127.0.0.1:8888/circuitjs.html?tsjFixture=led&seed=12345&tsjVerifyResistance=true&reviewfix=13final`
- Debug verifier URL:
  `http://127.0.0.1:8888/circuitjs.html?tsjFixture=led&seed=12345&tsjDebug=true&tsjVerifyResistance=true&reviewfix=13report`
- Normal PCB and debug schematic verifier runs completed with no page or console
  errors. A no-fixture URL retained the legacy LRC schematic and controls.

## Numerical Results
- Installed/restored R1 pad-to-pad resistance: `680 Ohm` within `2 Ohm`.
- Lifted and removed board path: `OL`; continuity remained inactive.
- Lifted and tray resistor lead-to-lead resistance: `680 Ohm` within `2 Ohm`.
- Powered removed-board VIN: approximately `9 V`; LED current below `1 uA`.
- Restored powered LED current: `0.010607265446555162 A` (`10.607 mA`).
- Forward LED diode mode: `1.594696569036657 V` at
  `0.0014053034309633428 A` (`1.405 mA`).
- Same PCB pad clicks did not start another measurement transaction.

## State And Interaction Results
- `INSTALLED -> LEAD_LIFTED -> INSTALLED` restored exact element order/export.
- `LEAD_LIFTED -> REMOVED -> INSTALLED` disconnected/restored every lead.
- Repeated lift, reconnect, remove, and restore requests were idempotent.
- Unknown component/pad IDs and components without connections were rejected
  without graph mutation; duplicate detachable occurrences were detected.
- Stable board-pad targets survived lift, removal, and reanalysis. Detached lead
  targets remained available in lifted/tray states and expired after restore.
- Powered modification buttons were disabled with inline safety text and no
  alert; powering an already modified board remained valid.
- DC V, OHM, CONT, DIODE, queued-power, semantic-target, cleanup, identity,
  undo/redo, export, and component-isolation regressions passed.

## Visual Verification
- `1600 x 900`: installed and lifted states fit without clipping; labels, pads,
  traces, highlight, air gap, probes, meter, and sidebar remained legible.
- `1366 x 768`: removed board and parts tray fit without clipping; both board
  pads and tray resistor remained visually clear.
- Normal mode showed no fixed R1 controls, schematic, scopes, simulation-speed,
  current-speed, or upstream editing controls.
- Captures: `installed-1600x900.png`, `lifted-1600x900.png`, and
  `removed-1366x768.png` in `docs/screenshots/task13/`.

## Known Limitations
The layout is manually authored for the LED indicator family. It is not a PCB
router or manufacturing model. Only declared lift/remove/restore operations are
available; replacement parts, faults, jumpers, trace cuts, damage, and
procedural PCB placement/routing remain out of scope.

## Recommended Next Step
Use this workbench boundary for the first validated faulted LED challenge, then
add replacement-component interaction without coupling PCB geometry to solver
state.

## Intended Commit Message
Build interactive PCB workbench