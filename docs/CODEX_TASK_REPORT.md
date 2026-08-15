# Latest Codex Task Report

## Task
Focused corrective follow-up to Task #11: semantic probe identity, diode
reverse verification, and generic active-measurement cleanup validation.

## Summary
Fixed active-instrument pointer handling so repeated clicks on the same
physical CircuitJS post do not remeasure or prepare continuity audio. Cleanup
verification now validates the most recently executed generic stimulus rather
than retaining a resistance-only state reference.

## Architectural Decisions
- `ProbeTarget.isSameTarget()` is an extensible semantic equality contract.
  Circuit post targets compare simulation, element, and post index.
- The controller only invalidates/remeasures after a semantic probe change.
  Empty or same-target canvas hits retain the active result; CONT prepares
  audio only for a genuinely changed probe or the CONT button gesture.
- `lastActiveMeasurementStimulus` and
  `isActiveMeasurementSolverRestoredForDeveloperVerification()` cover either
  resistance or diode overlays and check all temporary elements against the
  element list, voltage source table, node links, and final solver matrix.
- Reverse diode verification now requires an OL readout, published NaN voltage,
  and finite reverse current below the `10 uA` current threshold; it no longer
  compares amperes to a voltage constant.

## Files Changed
- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`
- `src/com/lushprojects/circuitjs1/client/CirSim.java`
- `src/com/lushprojects/circuitjs1/client/CircuitPostProbeTarget.java`
- `src/com/lushprojects/circuitjs1/client/ProbeTarget.java`
- `src/com/lushprojects/circuitjs1/client/ContinuityFeedback.java`
- `src/com/lushprojects/circuitjs1/client/BrowserContinuityFeedback.java`
- `src/com/lushprojects/circuitjs1/client/InstrumentController.java`
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
- Production build command:
  `$java8Home = Join-Path $env:TEMP 'TroubleshootJS-build-probe\temurin8'; & .\scripts\build.ps1 -JavaHome $java8Home`
  completed all five GWT permutations and linked successfully.
- Browser verifier:
  `http://127.0.0.1:8888/circuitjs.html?tsjFixture=led&seed=12345&tsjVerifyResistance=true&reviewfix=3`
  completed with `Resistance verification passed` and final `680 Ohm`.
- Persistent canvas hit-tests confirm same CONT red and black physical-post
  clicks added zero resistance transactions and zero `prepare()` calls.
  A different CONT post added exactly one resistance transaction and one
  prepare call. Empty canvas added neither.
- Same DIODE red and black physical-post clicks added zero diode transactions
  and zero continuity prepares. A different DIODE post added exactly one diode
  transaction.
- Forward LED still solved to `1.594696569036657 V` at
  `0.0014053034309633428 A`; reverse was OL with NaN published voltage and a
  finite current below `10 uA`; same-node remained approximately `0 V`.
- Powered/legacy blocking, invalid probes, topology one-shot refresh, mode
  switching, queued power, generic solver cleanup, export/history/identity,
  OHM, CONT `49/50/51 Ohm`, and DC `+9/0/+9 V` regressions passed.
- `git diff --check` and `git diff --cached --check` passed.

## Audio Validation
The new prepare counter proves whether a gesture requests browser-audio
initialization. Same/empty/DIODE canvas clicks leave it unchanged; a changed
CONT probe increments it once. Actual audible output remains unobservable to
browser automation, and visible BEEP remains the deterministic fallback.

## Known Limitations
Diode classification is DC and intentionally conservative around compliance;
complex parallel networks may legitimately report their in-circuit result.
Browser audio remains best-effort and unrelated to DIODE mode.

## Recommended Next Step
Add component removal/lift primitives so users can isolate misleading parallel
paths before active measurements.

## Intended Commit Message
Fix semantic probe measurement refresh
