# Latest Codex Task Report

## Task
Task #8: Make generated-board POWERED and UNPOWERED state electrically real.

## Summary
Added SwitchElm-backed external positive-supply isolation to the generated LED board. Board power now changes the CircuitJS graph through a generic external-power control, reanalyzes the circuit, and verifies the new electrical state without rebuilding the board.

## Architectural Decisions
- `ExternalPowerControl` separates BoardPowerController from the current SwitchElm implementation.
- `ExternalPowerSimulationBinding` can own an optional control and multiple backing elements. The LED binding owns its DC source and external isolation switch.
- The isolation switch is external simulation infrastructure, not a logical PCB component. `J1.1` binds to the board side of the switch.
- BoardPowerController treats UNPOWERED as electrically safe only with an attached, controllable generated-board binding whose controls are open. Legacy circuits remain unsafe for active measurements.
- Generated verification rejects non-finite voltages and passes BoardPowerState to the family validator. LED current is healthy only when powered and approximately zero when isolated.

## Files Changed
- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`
- `src/com/lushprojects/circuitjs1/client/BoardPowerController.java`
- `src/com/lushprojects/circuitjs1/client/CircuitMeasurementAdapter.java`
- `src/com/lushprojects/circuitjs1/client/CirSim.java`
- `src/com/lushprojects/circuitjs1/client/ExternalPowerControl.java`
- `src/com/lushprojects/circuitjs1/client/ExternalPowerSimulationBinding.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedBoardValidator.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedBoardVerifier.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedExternalPowerBindings.java`
- `src/com/lushprojects/circuitjs1/client/LedIndicatorGeneratedBoardValidator.java`
- `src/com/lushprojects/circuitjs1/client/LedIndicatorGenerator.java`
- `src/com/lushprojects/circuitjs1/client/SwitchExternalPowerControl.java`
- `src/com/lushprojects/circuitjs1/client/TroubleshootBoard.java`

## Validation
- Production GWT build: passed all five permutations before and after implementation.
- Generated verification: pad ownership, net consistency, finite-voltage checks, and powered/unpowered LED-family behavior passed after each state transition.
- Browser: initial ON, OFF, repower, five alternating toggles, paused OFF/resume, malformed seed, normal LRC startup, and Edit menu regression completed with no page or console errors.
- DC meter with one persistent board-side VIN probe: `+9 V` powered, `0 V` unpowered, and `+9 V` after repowering.

## Test Data
- Seed `12345`: `9 V`, `680 ohm`.
- Powered: VIN-to-GND `+9 V`; isolation switch closed; switch current `10.603 mA`.
- Unpowered: VIN-to-GND `0 V`; isolation switch open; branch current `0 A`; LED and resistor validators required approximately zero current.
- Repowered: VIN-to-GND returned to `+9 V`; healthy current range validation passed.
- Repeated toggle: five alternating states completed as OFF, ON, OFF, ON, OFF with stable bindings and no errors.
- Paused toggle: OFF was requested while `running=false`, produced no stale-value failure while paused, and completed verification after simulation resumed.
- Active-measurement gating: generated POWERED is blocked, generated electrically UNPOWERED is allowed, and legacy circuits are blocked because the adapter now requires `isElectricallyUnpowered()`.
- Malformed `seed=banana`: safely fell back to seed `1` (`5 V`, `330 ohm`) with no errors.

## Known Limitations / Concerns
Power control currently switches all declared generated-board inputs together. Independent power-domain controls and bench supply UI are intentionally deferred.

## Recommended Next Step
Implement active resistance/continuity measurement using the electrically enforced UNPOWERED gate and the existing ActiveMeasurementSession boundary.

## Commit
Add real generated board power control
