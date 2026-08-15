# Latest Codex Task Report

## Task
Task #14: add the first validated faulted LED challenge.

## Summary
`?tsjChallenge=led&seed=<seed>` now stages a healthy generated LED board,
verifies it through CircuitJS, applies a deterministic internal open-R1 fault,
reanalyzes and validates the no-light symptom, then presents the ready service
ticket: `Indicator does not light.` The healthy fixture route remains separate.

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

## Validation
- The healthy family validator remains unchanged: powered LED current must be
  5-15 mA and match resistor current.
- Faulted validation requires powered, installed R1, a bound open fault switch,
  LED current below 1 uA, and a non-illuminated LED operational state.
- `tsjVerifyChallenge=true` runs after `READY` and checks VIN, faulted PCB and
  component-lead OL readings, lift/remove/tray/restore fault persistence, and
  developer clear/reapply recovery through CircuitJS.
- Browser checks of challenge seeds `0`, `2`, and `3` reached the ready ticket
  with Board Power ON and no page or console errors. The healthy fixture for
  seed `2` stayed ticket-free and error-free.
- JDK 8 production build compiled and linked all five GWT permutations:
  `$java8Home = Join-Path $env:TEMP 'TroubleshootJS-build-probe\temurin8'; & .\scripts\build.ps1 -JavaHome $java8Home`.

## Known Limitations
This is one fixed fault family: an internally open R1 on the manually authored
LED board. It intentionally does not add replacement parts, scoring,
procedural routing, or player-visible fault controls.

## Recommended Next Step
Add a repair primitive that mutates the electrical graph, then verify repaired
functional behavior without disclosing the original fault.

## Intended Commit Message
`Add validated open-resistor challenge`
