# Latest Codex Task Report

## Task
Task #14 corrective pass: meter polarity, generic challenge lifecycle,
preparation gating, and verification evidence.

## Summary
OHM/CONT now use a CircuitJS-positive red probe, matching the existing diode
test orientation. Challenge selection is owned by a deterministic family
catalog/definition rather than the generic controller, and normal player input
is blocked until solver-gated validation reaches READY.

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
- `GeneratedChallengeDefinition` carries IDs, complaint text, selected binding,
  selection seed, and a `GeneratedFaultValidator` strategy. The generic
  controller contains no LED, R1, fault-type, or complaint-text knowledge.
- READY-state validation retains the selected fault and reconfirms the symptom
  after restored/repowered hardware; clear/reapply is developer-scoped.

## Validation
- The healthy family validator remains unchanged: powered LED current must be
  5-15 mA and match resistor current.
- Faulted validation requires powered, installed R1, a bound open fault switch,
  LED current below 1 uA, and a non-illuminated LED operational state.
- `tsjVerifyChallenge=true` runs after `READY` and checks VIN, faulted PCB and
  component-lead OL readings, lifecycle evidence, deterministic metadata,
  LED OHM/CONT/DIODE polarity, meter transaction restoration,
  lift/remove/tray/restore fault persistence, and developer clear/reapply.
- LED OHM and CONT are finite with red on anode/black on cathode and OL when
  reversed. CONT remains below no false continuity/BEEP; DIODE stays forward
  finite/reverse OL. The test voltage is 1 V for OHM/CONT and 3 V for DIODE.
- Deterministic metadata remains seed 0: 5 V/330 Ohm; seed 2: 9 V/680 Ohm;
  seed 3: 12 V/1000 Ohm. The selected IDs are `LED_INDICATOR_NO_LIGHT`,
  `INDICATOR_DOES_NOT_LIGHT`, and `LED_R1_OPEN` targeting `R1`.
- Browser checks of challenge seeds `0`, `2`, and `3` reached the ready ticket
  with Board Power ON and no page or console errors. The healthy fixture for
  seed `2` stayed ticket-free and error-free.
- JDK 8 production build compiled and linked all five GWT permutations:
  `$java8Home = Join-Path $env:TEMP 'TroubleshootJS-build-probe\temurin8'; & .\scripts\build.ps1 -JavaHome $java8Home`.
- Browser evidence: `docs/screenshots/task-14/preparing-disabled-controls.png`,
  `docs/screenshots/task-14/ready-faulted-challenge.png`, and
  `docs/screenshots/task-14/healthy-powered-led.png`. The paused
  `running=false` preparation route showed Board Power and all meter buttons
  disabled; READY re-enabled them with no browser errors.

## Known Limitations
This is one fixed fault family: an internally open R1 on the manually authored
LED board. It intentionally does not add replacement parts, scoring,
procedural routing, or player-visible fault controls.

## Recommended Next Step
Add a repair primitive that mutates the electrical graph, then verify repaired
functional behavior without disclosing the original fault.

## Intended Commit Message
`Correct challenge lifecycle and meter polarity`
