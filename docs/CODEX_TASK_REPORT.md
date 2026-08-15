# Latest Codex Task Report

## Task
Task #15 corrective pass: electrically honest resistor replacement isolation.

## Summary
Fixed ghost replacement paths and made every physical resistor part a complete,
individually measurable CircuitJS backing with explicit R1 slot attachments.

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
- Each `PhysicalResistorPart` has immutable identity/nameplate, complete backing,
  public terminals, and location. The original's open switch stays in its public
  electrical path.
- R1 attachment wires, not part coordinates, are retargeted during swaps. All
  loose backings remain active exactly once at unique grid-aligned solver points.
- The resistance overlay temporarily grounds exactly its black-probe node. Its
  midpoint is grid-snapped, preventing the source and meter resistor from landing
  on adjacent but electrically distinct coordinates.
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
- `tsjVerifyReplacement=true` passed with no page/console errors:
  `http://localhost:8901/circuitjs.html?tsjChallenge=led&seed=0&tsjVerifyReplacement=true&run=final`,
  `http://localhost:8901/circuitjs.html?tsjChallenge=led&seed=2&tsjVerifyReplacement=true&run=final`,
  and `http://localhost:8901/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyReplacement=true&run=final`.
- Seed 2 numeric sequence: 220 Ohm reads 220 Ohm both orientations and drives
  31 mA uncompleted; 10000 Ohm reads 10000 Ohm both orientations and drives
  approximately 0.7 mA without LED illumination; 680 Ohm reads 680 Ohm installed,
  drives approximately 10 mA matching LED current, illuminates, and completes.
- Original loose R1 reads OL in both orientations before and after reinstall;
  reinstallation remains below 1 uA and does not complete.
- JDK 8 production build compiled and linked all five GWT permutations:
  `$java8Home = Join-Path $env:TEMP 'TroubleshootJS-build-probe\temurin8'; & .\scripts\build.ps1 -JavaHome $java8Home`.
- Browser evidence was exercised on the three URL-gated production pages above.
  The existing `docs/screenshots/task-15` images predate this corrective pass and
  are not asserted as evidence for the corrected electrical behavior.

## Known Limitations
Only resistor replacement for the LED challenge is implemented. There are no
replacement LEDs, polarized parts, jumpers, damage, scoring, or repair hints.

## Recommended Next Step
Replace predefined replacement choices with an effectively unlimited on-demand
resistor catalog; removed parts alone remain in the tray. Preserve solved
functional acceptance, stable realized tolerance values, and later introduce
persistent damage only from modeled electrical/thermal stress.

## Intended Commit Message
`Fix replacement part electrical isolation`
