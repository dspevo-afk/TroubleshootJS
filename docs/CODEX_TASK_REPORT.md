# Latest Codex Task Report

## Task Identification

Task #17: fix lifted-lead meter refresh and physical probe identity.

Implementation commit SHA: `d4eaf3d2185f979ffbe5293f79f25dd71d66a9be`

Implementation commit message: `Fix lifted-lead meter refresh`

This documentation-only follow-up is committed as `Complete Task 17 report`.

## Original Root Causes

- The pending DC refresh flag was consumed in the post-analysis callback before
  `InstrumentController.updateReading()` could start the measurement.
- `ComponentLeadProbeTarget` dynamically followed mutable R1 slot bindings,
  allowing a retained probe to migrate to a newly installed physical resistor.
- The legacy resistance verifier ran before the generated challenge reached its
  `READY` gate.
- A resistance-verifier exception could be retried on later simulation cycles
  instead of becoming a terminal reported failure.

## Implementation Commit Files

Commit `d4eaf3d2185f979ffbe5293f79f25dd71d66a9be` changed these eleven files:

- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`
- `docs/screenshots/task-17/isolated-board-pad-dc.png`
- `docs/screenshots/task-17/lifted-lead-dc.png`
- `docs/screenshots/task-17/lifted-resistor-ohm.png`
- `src/com/lushprojects/circuitjs1/client/CirSim.java`
- `src/com/lushprojects/circuitjs1/client/ComponentLeadProbeTarget.java`
- `src/com/lushprojects/circuitjs1/client/InstrumentController.java`
- `src/com/lushprojects/circuitjs1/client/MeterLifecycleDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java`
- `src/com/lushprojects/circuitjs1/client/ResistanceMeasurementDeveloperVerifier.java`

## Architectural Decisions

- DC voltage is measured through a temporary passive `10 MOhm`
  `ResistorElm` in CircuitJS between the red and black probe endpoints. The
  temporary overlay is removed after the loaded solve and is not board metadata,
  export content, or undo history.
- `InstrumentController.updateReading()` owns consumption of the DC refresh
  request. The post-analysis callback leaves the pending request available for
  that method instead of clearing it prematurely.
- Power and topology analysis request one retained-probe refresh. A retained
  probe therefore gets a fresh loaded solve after the relevant state change.
- Repaint and repeated update cycles do not repeatedly measure when no new
  refresh is pending.
- Active measurement overlays suppress recursive measurement callbacks while a
  temporary stimulus is installed.
- `ComponentLeadProbeTarget` captures the physical-part ID and component-side
  endpoint at hit-test time.
- A retained component-lead target cannot migrate to a different part installed
  in R1. Its physical-part identity is part of target equality.
- Invalid physical targets clear after reconnection, removal, or replacement.
- Physical component leads and PCB pads are separate targets. A lifted
  component-side lead remains distinct from its fixed board-side PCB pad.
- Resistance verification has explicit `NOT_STARTED`, `RUNNING`, `PASSED`, and
  `FAILED` states. The field applies to the resistance verifier specifically.
  Challenge, replacement, and meter lifecycle verification retain their own
  generated-board readiness and completion guards; they do not all use the
  four-state resistance field.
- Renderer-level verifier interaction exercises the same logical target and
  endpoint behavior, but it does not replace real DOM-to-canvas Chromium
  interaction. The final browser evidence used actual page mouse input and the
  live canvas geometry.

## Build Validation

The explicit JDK 8 production build compiled and linked all five GWT
permutations:

```powershell
$java8Home = Join-Path $env:TEMP 'TroubleshootJS-build-probe\temurin8'
& .\scripts\build.ps1 -JavaHome $java8Home
```

The build completed successfully with all five permutations compiled and the
`war/circuitjs1` output linked.

## Complete Browser Matrix

The final browser matrix ran on `http://localhost:8926` for seeds `0`, `2`, and
`3`, covering these five verifier routes per seed and therefore 15 routes total:

- `?tsjChallenge=led&seed=<seed>&tsjVerifyResistance=true`
- `?tsjChallenge=led&seed=<seed>&tsjVerifyMeter=true`
- `?tsjChallenge=led&seed=<seed>&tsjVerifyChallenge=true`
- `?tsjChallenge=led&seed=<seed>&tsjVerifyReplacement=true`
- `?tsjChallenge=led&seed=<seed>&tsjVerifyChallenge=true&tsjVerifyReplacement=true`

Observed result: zero page errors and zero failure-class console messages on
all 15 routes.

## Representative Results

The following are calculated expected loaded lifted-lead voltages, not literal
formatted browser display strings:

- Seed 0: `330 Ohm` replacement; expected loaded lifted-lead voltage
  approximately `4.999835 V`.
- Seed 2: `680 Ohm` replacement; expected loaded lifted-lead voltage
  approximately `8.999388 V`.
- Seed 3: `1 kOhm` replacement; expected loaded lifted-lead voltage
  approximately `11.99880012 V`.

The final real-player seed-3 canvas flow produced these visibly observed and
formatted meter readings:

- Physical resistor leads: `1 kOhm` with power OFF.
- Physical lifted R1.2 lead to GND: `11.999 V` with power ON.
- Separate downstream R1.2 PCB pad to GND: `0 V` with power ON.

## Lifecycle and Restoration Evidence

The meter lifecycle verifier asserted the following behavior:

- Exactly one DC transaction per changed probe. The verifier asserts that the
  measurement count advances by two after placing the two changed probes.
- Exactly one retained refresh after power-on.
- Exactly one retained refresh after power-off.
- No additional measurements during repeated repaint/update cycles.
- Symmetric lifted-lead resistance behavior for lifting R1.1 and for lifting
  R1.2, including both probe polarities.
- A lifted component lead versus its PCB pad measures `OL` across the isolated
  gap.
- The still-attached component lead measures approximately zero resistance to
  its attached PCB pad.
- A target captured while a lead is lifted becomes invalid after reconnection.
- The same captured target becomes invalid after removing its physical part.
- Installing another physical resistor does not transfer the previous target's
  identity to that new part.
- Placing invalid targets clears them before a new measurement; the developer
  readout returns the mode placeholder rather than measuring through the stale
  target.
- Temporary meter overlays are removed after measurement.
- The canonical CircuitJS graph and solver state are restored after temporary
  overlay transactions and topology changes.

The verifier also restores the correct replacement, reconnects the lifted lead,
and confirms the LED returns to its healthy operating state.

## Real Browser Coordinate Diagnosis

The physical-lead diagnosis established the canvas offset explicitly:

`canvasY = clientY - 30`

The renderer-local lead points and Chromium observations were:

```text
R1.1 renderer lead: Point(505,408)
R1.2 renderer lead: Point(679,397)

Chromium left click:
client=(504,437), local=(504,407)
ComponentLeadProbeTarget
valid=true
marker=Point(505,408)

Chromium right click:
client=(678,426), local=(678,396)
ComponentLeadProbeTarget
valid=true
marker=Point(679,397)
```

Both targets captured `R1_REPLACEMENT_1`, both remained valid, and both hit
distances were `2`.

Renderer-local coordinates had incorrectly been supplied directly to Chromium's
page-coordinate mouse API, missing the hit zones by the canvas's 30 px top
offset. This was an automation defect, not a production hit-testing defect.

## Browser-Layout Diagnosis

Resizing an already initialized GWT page produced stale and unstable automation
layout. A fresh `1620 x 1000` viewport set before navigation produced a healthy
layout, and `elementFromPoint()` identified the real DC V button.

The final meter-state verification was performed one visible action at a time:
power was turned OFF and asserted, R1 was selected, removed, replaced, and
reselected, lead 2 was lifted and asserted, OHM was selected exactly once, and
the two physical leads produced `1 kOhm`. DC V was then selected exactly once
with a captured native `mousedown`/`mouseup`/`click` sequence targeting the
button; the mode class changed to `chsel` while OHM cleared. Power was turned ON
and asserted separately, then the lifted lead and GND were probed for `11.999 V`.
Finally, only the red probe was moved to the isolated R1.2 PCB pad and the
display changed to `0 V`. The final screenshots were captured from the stable
native browser viewport after this controlled sequence.

## Screenshots

- `docs/screenshots/task-17/lifted-resistor-ohm.png`: OHM mode, power OFF,
  red and black probes on the two physical resistor leads, visible reading
  `1 kOhm`.
- `docs/screenshots/task-17/lifted-lead-dc.png`: DC V mode, power ON, red
  probe on the physical lifted R1.2 lead and black probe on GND, visible reading
  `11.999 V`.
- `docs/screenshots/task-17/isolated-board-pad-dc.png`: DC V mode, power ON,
  red probe on the separate downstream R1.2 PCB pad and black probe on GND,
  visible reading `0 V`.

Pixel inspection confirmed that all three final-build images had a nonblank,
correctly framed board, visible probe markers and readings, and no clipping or
incoherent overlap.

## Known Limitations and Next Task

Known limitations:

- Physical component-lead identity is currently specialized for replaceable R1
  in the LED challenge.
- General replacement families and persistent electrical stress/component damage
  remain out of scope.

Recommended next task: implement an unlimited user-selectable catalog of
same-type replacement parts while showing only physically removed board
components in the parts tray. Persistent electrical stress and hidden component
damage remain a later dedicated task.

## Repository Completion

For this documentation-only follow-up:

- `git diff` and `git status --short` were inspected.
- Only `docs/CODEX_TASK_REPORT.md` changed.
- Only that file was staged.
- The required cached checks were run:

```powershell
git diff --cached --name-status
git diff --cached --stat
git diff --cached --check
```

- No build or browser rerun was required because this follow-up changes
  documentation only.

## Documentation Follow-up Commit

`Complete Task 17 report`
