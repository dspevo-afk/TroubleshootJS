# Latest Codex Task Report

## Task
Task #11: Add simulated diode-test mode.

## Summary
Added `DIODE`, a real active CircuitJS diode tester. It drives the unpowered
circuit with a finite-compliance source, displays solved red-minus-black DUT
voltage, and identifies open/reverse/compliance conditions as `OL`.

## Architectural Decisions
- `ActiveMeasurementStimulus` and the shared CirSim transaction centralize
  install/solve/sample/remove/final-restore behavior for both OHM and DIODE.
- `DiodeTestStimulus` is distinct from resistance: it samples solved DUT
  voltage/current rather than deriving resistance or inspecting diode metadata.
- DIODE uses the existing active-measurement power gate and validates it again
  after transaction cleanup, discarding readings when queued power changes the
  final board state.
- Continuity audio preparation now occurs only for a changed CONT probe or its
  button. Empty canvas clicks leave active measurements and feedback unchanged.

## Files Changed
- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`
- `src/com/lushprojects/circuitjs1/client/ActiveMeasurementStimulus.java`
- `src/com/lushprojects/circuitjs1/client/CirSim.java`
- `src/com/lushprojects/circuitjs1/client/CircuitMeasurementAdapter.java`
- `src/com/lushprojects/circuitjs1/client/DiodeMeasurementResult.java`
- `src/com/lushprojects/circuitjs1/client/DiodeTestStimulus.java`
- `src/com/lushprojects/circuitjs1/client/InstrumentController.java`
- `src/com/lushprojects/circuitjs1/client/ResistanceMeasurementStimulus.java`
- `src/com/lushprojects/circuitjs1/client/ResistanceMeasurementDeveloperVerifier.java`

## Stimulus Constants
- Compliance source: `3 V`.
- Series resistance: `1 kOhm`.
- Maximum short current: approximately `3 mA`.
- Meaningful current: at least `10 uA`.
- OL/compliance threshold: `2.95 V`.
- Source orientation is midpoint-to-red, so red is electrically positive
  relative to black through the DUT.

## Validation
- Production GWT build passed all five permutations after implementation.
- Browser verifier at
  `?tsjFixture=led&seed=12345&tsjVerifyResistance=true&diode=final` passed and
  displayed the DIODE control.
- Forward LED (`LED1.A -> LED1.K`) solved to exactly observed
  `1.594696569036657 V` at `0.0014053034309633428 A`; it was finite and not OL.
- Reverse LED displayed `OL`; same-net `J1.1 -> R1.1` was approximately `0 V`,
  not OL. Forward/reverse order therefore produced distinct behavior.
- Powered and detached legacy graphs displayed `POWER OFF` without a diode
  overlay. Invalid probes cleared to `--- V`; reinstallation did not revive
  cleared probes.
- Retained diode probes refreshed exactly once after `needAnalyze()`; normal
  repaint cycles added no transaction. DIODE-to-CONT/OHM/DC and exit behavior
  passed, including immediate BEEP shutdown and restored pointer handling.
- A queued power-on during diode overlay removed the temporary elements, showed
  `POWER OFF`, restored final POWERED state with connected external controls,
  approximately `+9 V` VIN, and passed generated-board verification.
- OHM, CONT (including `49/50/51 Ohm`), DC `+9/0/+9 V`, export, undo/redo,
  unsaved state, board identities, collision-free geometry, solver cleanup,
  and queued-power resistance regressions all remained covered by the verifier.

## Audio Validation
The verifier confirms continuity feedback was requested and BEEP state changed
only when appropriate. It cannot prove speaker output. Browser automation does
not observe oscillator construction or actual audible sound; visible BEEP is
the deterministic fallback.

## Known Limitations
Diode classification is DC and intentionally conservative around compliance;
complex parallel networks may legitimately report their in-circuit result.
Browser audio remains best-effort and unrelated to DIODE mode.

## Recommended Next Step
Add component removal/lift primitives so users can isolate misleading parallel
paths before active measurements.

## Intended Commit Message
Add simulated diode test
