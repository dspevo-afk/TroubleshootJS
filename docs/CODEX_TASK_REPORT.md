# Latest Codex Task Report

## Task
Task #15: electrically real resistor replacement and repair completion.

## Summary
The open-R1 LED challenge now supports removal of the failed original resistor,
measurement in the tray, deterministic healthy replacement choices, real
CircuitJS slot installation, wrong repair attempts, and solver-backed repair
completion.

## Architecture Decisions
- `GeneratedFault` is immutable typed metadata; its private `SwitchElm` is
  bound through `GeneratedFaultBinding` and controlled only by
  `GeneratedFaultController`.
- The series switch is fault infrastructure, not a logical PCB component,
  placement, pad, net, external-power control, or printed nameplate. The
  declared R1 component-side terminal may resolve through it for realistic OL
  measurements without exposing the switch to the player.
- The challenge lifecycle is solver-gated: healthy validation, fault apply,
  reanalysis/time advance, faulted validation, then `READY`. No arbitrary UI
  delay declares the challenge valid.
- `GeneratedComponentOperationalStates` maps the stable LED ID to solved LED
  current. The renderer uses it only for illumination; printed identity remains
  immutable.
- `ReplaceableComponentSlot` is the stable R1 board location; each
  `PhysicalResistorPart` has its own immutable ID, nameplate, CircuitJS resistor
  backing, and loose/installed state. The failed original remains a distinct
  faulted part and never becomes a replacement.
- `ResistorSlotController` solely owns power-off removal/install swaps through
  the existing detachable R1 electrical boundary. PCB pads, nets, traces,
  designator, and layout are not rebuilt.
- `GeneratedRepairValidator` evaluates solved electrical function, not selected
  part identity. Completion is latched after 5-15 mA LED current, matching R1
  current, and illuminated solved operational state.

## Validation
- The healthy family validator remains unchanged: powered LED current must be
  5-15 mA and match resistor current.
- Faulted validation requires powered, installed R1, a bound open fault switch,
  LED current below 1 uA, and a non-illuminated LED operational state.
- Deterministic inventory: seed 0 `100/330/4700 Ohm`; seed 2
  `220/680/10000 Ohm`; seed 3 `330/1000/15000 Ohm`, all `+/-5%` exact
  four-band nameplates. Inventory order and IDs are stable.
- Manual browser validation on seed 2 removed the original, showed it loose,
  installed low and high wrong values without completion, then installed the
  680 Ohm replacement. CircuitJS measured 9 V at R1 and the ticket became
  `Repair verified. Indicator operating normally.`
- For the 9 V seed, the 220 Ohm low choice drives approximately 31 mA and the
  10000 Ohm high choice approximately 0.7 mA, both outside the 5-15 mA
  functional acceptance range; the 680 Ohm replacement returns the existing
  healthy approximately 10 mA operating point.
- Browser checks of challenge seeds `0`, `2`, and `3` reached the ready ticket
  with Board Power ON and no page or console errors. The healthy fixture for
  seed `2` stayed ticket-free and error-free.
- JDK 8 production build compiled and linked all five GWT permutations:
  `$java8Home = Join-Path $env:TEMP 'TroubleshootJS-build-probe\temurin8'; & .\scripts\build.ps1 -JavaHome $java8Home`.
- Browser evidence: `docs/screenshots/task-15/initial-faulted-challenge.png`,
  `empty-slot-original-tray.png`, `low-wrong-installed.png`,
  `high-wrong-installed.png`, and `completed-correct-replacement.png`.

## Known Limitations
Only resistor replacement for the LED challenge is implemented. There are no
replacement LEDs, polarized parts, jumpers, damage, scoring, or repair hints.

## Recommended Next Step
Add replacement support for another component family while preserving the same
slot/part and solver-backed functional-validation boundaries.

## Intended Commit Message
`Add electrically real resistor replacement`
