# Latest Codex Task Report

## Task
Task #9 second corrective follow-up: Preserve live DC voltage and finalize
active-measurement state correctly.

## Summary
Restored live passive DC meter updates after every normal CircuitJS solve while
keeping OHM strictly transactional and demand-driven. A resistance result is
now revalidated after its transaction returns, so a queued power-on discards an
earlier unpowered reading and publishes `POWER OFF` only after the final
powered graph has been solved.

## Architectural Decisions
- `InstrumentController.draw()` remains visual-only. A post-simulation-step
  callback refreshes passive DC V from solved post voltages.
- The post-step callback consumes at most one pending OHM refresh only after a
  normal analysis. Passive DC refreshes never install temporary elements.
- After a queued power request, the resistance transaction removes its overlay,
  applies the requested control state, synchronously analyzes and solves the
  final graph, and only then marks solver restoration complete.
- The controller revalidates active-measurement permission after adapter return
  before publishing resistance. A newly powered board displays `POWER OFF`.
- URL-gated verification remains outside normal UI and drives production
  controller/adapter/session paths through stable generated board pads.

## Files Changed
- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`
- `src/com/lushprojects/circuitjs1/client/CirSim.java`
- `src/com/lushprojects/circuitjs1/client/InstrumentController.java`
- `src/com/lushprojects/circuitjs1/client/ResistanceMeasurementDeveloperVerifier.java`

## Validation
- Focused and production GWT builds passed all five permutations.
- Browser developer verifier:
  `?tsjFixture=led&seed=12345&tsjVerifyResistance=true&stateFix=2` completed
  with `Resistance verification passed`, `Board Power: OFF`, and `680 Ohm`.
- Persistent board-side VIN/GND DC probes displayed `9 V` powered, `0 V` after
  power-off, and `9 V` after repower without moving probes. Repeated normal
  update cycles did not increment the resistance transaction count.
- Forced power-on during the overlay left the final board POWERED, all external
  isolation controls connected, VIN at approximately `+9 V`, solver restored
  without temporary elements, generated-board verification passing, and OHM
  visibly `POWER OFF` rather than a stale resistance.
- Restoring power OFF returned the board to electrically unpowered state; a
  subsequent ordinary measurement returned `680 Ohm`.
- Retained checks passed: forward/reverse `680 Ohm`, same-net `0 Ohm`, open
  LED path `OL`, powered and legacy blocking, one topology refresh, no
  draw/repaint refresh, invalid-probe clearing, collision-free geometry,
  synchronous cleanup, stable board identities, and unchanged export/history/
  unsaved state.

## Exact Observed Test Data
- Seed `12345`: `9 V`, `680 Ohm` R1.
- DC VIN-to-GND: `+9 V` powered, `0 V` unpowered, `+9 V` repowered.
- `R1.1 -> R1.2`: `680 Ohm`; reverse: approximately `680 Ohm`.
- `J1.1 -> R1.1`: `0 Ohm`; reverse/open LED path: `OL`.
- Queued final power-on: `Board Power: ON`, VIN approximately `+9 V`, OHM
  `POWER OFF`.

## Known Limitations
Resistance mode remains DC-only and reports `OL` above `10 MOhm` or for
non-finite source current. Nonlinear, capacitive, inductive, and transient
networks use CircuitJS's current solve state. Continuity and diode modes remain
out of scope.

## Recommended Next Step
Add continuity mode as a separate user-facing policy over the hardened active
measurement transaction, without changing DC or resistance lifecycle behavior.

## Intended Commit Message
Fix meter state transitions
