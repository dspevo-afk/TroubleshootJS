# Latest Codex Task Report

## Task
Task #9 corrective follow-up: Harden the resistance-measurement lifecycle.

## Summary
Hardened the active OHM transaction so retained valid probes refresh exactly
once after normal CircuitJS reanalysis, while `InstrumentController.draw()`
only renders cached state. Each temporary-stimulus transaction now restores and
solves the normal graph synchronously before it returns.

## Architectural Decisions
- `needAnalyze()` invalidates OHM state through a single controller lifecycle
	method. `updateCircuit()` consumes a pending refresh after normal graph
	analysis, not during drawing.
- Internal temporary-overlay cleanup performs direct normal-graph analysis and
	solve without requeuing the meter, preventing recursive analysis/measurement
	cycles.
- A pending board-power request remains deferred until the overlay is removed
	and normal solver structures are restored.
- Temporary-source geometry is allocated from live CircuitJS post occupancy at
	a deterministic unused off-board coordinate. It cannot coincide with either
	probe or an existing element post.
- The URL-gated verifier checks production controller/adapter/session behavior
	through stable board pad bindings. It adds no normal-user UI.

## Files Changed
- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`
- `src/com/lushprojects/circuitjs1/client/CirSim.java`
- `src/com/lushprojects/circuitjs1/client/InstrumentController.java`
- `src/com/lushprojects/circuitjs1/client/ResistanceMeasurementDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/ResistanceMeasurementStimulus.java`

## Validation
- Focused and production GWT builds passed all five permutations after the
	lifecycle changes.
- Browser developer verifier:
	`?tsjFixture=led&seed=12345&tsjVerifyResistance=true&lifecycle=5` completed
	with `Resistance verification passed`, `Board Power: OFF`, and visible
	`680 Ohm` without page errors.
- Repeated `updateCircuit()` draw/repaint cycles did not add a transaction.
- A retained valid probe pair invalidated by `needAnalyze()` displayed
	`--- Ohm`, then refreshed exactly once to `680 Ohm` after analysis.
- Removing a probed element immediately cleared the cached OHM readout; after
	restoring the element, the cleared probe was not silently restored.
- An occupied former midpoint coordinate did not change the reading or create
	a temporary solver connection.
- Each transaction asserted that temporary source/resistor references were
	absent from `elmList`, voltage-source ownership, and circuit-node links after
	synchronous restoration. Export, history, unsaved state, BoardPad/BoardNet
	identities, valid retained probes, and board power remained intact.

## Exact Observed Test Data
- Seed `12345`: `9 V`, `680 Ohm` R1.
- `R1.1 -> R1.2`: `680 Ohm`.
- `R1.2 -> R1.1`: approximately `680 Ohm`.
- `J1.1 -> R1.1`: `0 Ohm`.
- Reverse/open LED path: `OL`.
- Powered generated board and detached legacy-power binding: `POWER OFF` with
	no active measurement transaction.
- A power-on request made during the overlay applied only after cleanup, then
	the verifier restored the board to electrically OFF and passed generated
	verification.

## Known Limitations / Concerns
Resistance mode is DC-only. It reports `OL` above `10 MOhm` or for non-finite
source current. Nonlinear, capacitive, inductive, and transient networks use
the current CircuitJS solve state; continuity and diode modes are intentionally
out of scope.

## Recommended Next Step
Add continuity mode by reusing the hardened active-measurement transaction,
with its own user-visible threshold and no changes to resistance semantics.

## Intended Commit Message
Harden resistance measurement lifecycle
