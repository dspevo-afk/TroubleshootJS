# Latest Codex Task Report

## Task
Task #17: fix lifted-lead meter refresh and physical probe identity.

## Summary
Retained DC probes now consume each requested refresh only when the loaded
CircuitJS measurement actually starts. Component-lead probes capture one
physical resistor and cannot silently migrate to a later part installed in the
same logical R1 slot.

## Implementation
- `InstrumentController.updateReading()` is the sole consumer of a pending DC
  refresh. Post-analysis handling no longer clears the flag before entering the
  measurement path, so retained probes update once after power or topology
  changes and canvas repaints do not create recurring transactions.
- Developer DC probe setup now uses the normal left/right pointer path. This
  verifies the same target comparison, invalidation, and refresh behavior used
  by a player.
- `ComponentLeadProbeTarget` captures the installed physical-part ID and
  component-side endpoint. It becomes invalid when that part is removed or no
  longer has an exposed lead; equality includes the physical-part ID.
- `PcbWorkbenchRenderer` supplies that captured identity during physical-lead
  hit-testing. PCB pad targets remain separate, stable board-side targets.
- `MeterLifecycleDeveloperVerifier` covers symmetric lifted-lead resistance,
  lifted lead versus PCB pad isolation, retained loaded DC voltage, exact
  refresh counts through power transitions, repaint stability, physical-part
  invalidation after replacement, and canonical solver restoration.
- `?tsjVerifyMeter=true` runs only after a generated challenge reaches `READY`.
  Challenge resistance verification delegates to the same lifecycle suite, and
  terminal verifier state prevents deterministic retry spam after pass/failure.

## Validation
- JDK 8 production build compiled and linked all five GWT permutations:
  `$java8Home = Join-Path $env:TEMP 'TroubleshootJS-build-probe\temurin8'; & .\scripts\build.ps1 -JavaHome $java8Home`.
- Browser verification on `http://localhost:8926` completed for seeds `0`, `2`,
  and `3` with zero page errors and zero failure-class console messages across
  all 15 routes:
  - `?tsjChallenge=led&seed=<seed>&tsjVerifyResistance=true`
  - `?tsjChallenge=led&seed=<seed>&tsjVerifyMeter=true`
  - `?tsjChallenge=led&seed=<seed>&tsjVerifyChallenge=true`
  - `?tsjChallenge=led&seed=<seed>&tsjVerifyReplacement=true`
  - `?tsjChallenge=led&seed=<seed>&tsjVerifyChallenge=true&tsjVerifyReplacement=true`
- A real player-input flow replaced R1, lifted lead 2, and used the rendered PCB
  targets. It measured `1 kOhm` across the physical resistor while unpowered,
  `11.999 V` from the powered lifted lead to GND, and `0 V` from the separate
  isolated R1.2 PCB pad to GND.
- Pixel inspection passed for
  [lifted-resistor-ohm.png](screenshots/task-17/lifted-resistor-ohm.png),
  [lifted-lead-dc.png](screenshots/task-17/lifted-lead-dc.png), and
  [isolated-board-pad-dc.png](screenshots/task-17/isolated-board-pad-dc.png).
  Each image is a final-build player canvas with visible probes and no clipping,
  blank rendering, or incoherent overlap.

## Known Limitations
Physical component-lead identity currently applies to the replaceable R1 path in
the first LED challenge. General replacement families, an unlimited parts
catalog, and persistent component damage remain out of scope.

## Intended Commit Message
`Fix lifted-lead meter refresh`
