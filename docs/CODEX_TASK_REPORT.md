# Latest Codex Task Report

## Task
Focused correction: enforce component-specific, exact physical PCB markings in
commit `5fd7a52d777a23301cec76128615d711460dc556` before the first faulted
challenge.

## Summary
The PCB renderer now carries each stable resistor component ID into color-band
lookup for both installed/lifted and removed/tray drawings. It no longer reaches
back to `R1` from generic rendering code. Four-band encoding now rejects any
nameplate whose exact nominal resistance cannot be represented, rather than
silently truncating it. Nameplates also reject `NaN`, infinities, zero, and
negative numeric metadata.

## Architecture Decisions
- The marking path remains `PcbComponentPlacement` component ID ->
  `BoardPhysicalSpecifications` -> `ResistorNameplate` -> `ResistorColorCode`
  semantic `ResistorColorBand` tokens -> renderer CSS color.
- The renderer never uses live CircuitJS elements to determine a printed
  marking. Immutable nameplates remain authoritative for installed, lifted,
  removed, and restored component appearance.
- `ResistorColorCode` supports only finite, positive, integral, exactly
  representable `+/-5%` four-band values. It has no rounding policy and does
  not attempt five-band encoding.
- `BoardModificationRejectedException` remains the only mutation exception
  translated into the UI's power-off guidance. Structural errors continue to
  propagate rather than being misreported as ordinary user rejections.

## Files Changed
- `PcbWorkbenchRenderer.java`: passes logical component IDs through installed
  and tray band drawing.
- `ResistorColorCode.java`: verifies decoded four-band resistance exactly
  equals the immutable nominal value.
- `ResistorNameplate.java` and `PowerInputNameplate.java`: reject non-finite
  and non-positive numbers.
- `ResistanceMeasurementDeveloperVerifier.java`: covers component-keyed
  nameplates, exact mappings, unsupported values, invalid metadata, typed
  power rejection, all seeds, and existing electrical regressions.
- `PcbWorkbenchController.java`: normalized the nameplate block indentation.
- `docs/ARCHITECTURE.md` and this report.

## Deterministic Validation Data
| Seed | VIN | R1 Nameplate | Exact Four-Band Tokens |
| --- | --- | --- | --- |
| `0` | `+5V` | `330 Ohm +/-5%` | `ORANGE, ORANGE, BROWN, GOLD` |
| `2` | `+9V` | `680 Ohm +/-5%` | `BLUE, GRAY, BROWN, GOLD` |
| `3` | `+12V` | `1000 Ohm +/-5%` | `BROWN, BLACK, RED, GOLD` |

The URL-gated verifier creates each deterministic variant and verifies that its
nameplates match the initial CircuitJS source/resistor values. It additionally
checks separate `R1` and `R2` nameplates to prove logical component-keyed band
lookup, without adding a fake production component.

## Invalid-Input Tests
The verifier rejects four-band values `332 Ohm` and `101 Ohm` because each
would otherwise encode a nearby value. It also rejects resistor resistance and
tolerance values of `NaN`, positive infinity, negative infinity, zero, and
negative numbers, plus the corresponding invalid nominal input voltages.

## Build And Browser Validation
- JDK verification:
  `$java8Home = Join-Path $env:TEMP 'TroubleshootJS-build-probe\temurin8'; & "$java8Home\bin\java.exe" -version`
  reported `openjdk version "1.8.0_502"`.
- Development server command:
  `.\scripts\dev.ps1 -JavaHome $java8Home`
  started GWT DevMode at `http://127.0.0.1:8888` under that JDK 8 runtime.
- Browser verifier command URL:
  `http://127.0.0.1:8888/circuitjs.html?tsjFixture=led&seed=2&tsjVerifyResistance=true&reviewfix=component-specific-final`
  completed with no browser console or page errors.
- Normal browser checks confirmed selected R1 panel values for seeds `0`, `2`,
  and `3`. The URL-gated regression covers OHM, continuity, diode mode, power
  isolation, lift/remove/tray/restore markings and measurements, canonical
  export/order, and restored LED behavior.
- Complete production build command:
  `$java8Home = Join-Path $env:TEMP 'TroubleshootJS-build-probe\temurin8'; & .\scripts\build.ps1 -JavaHome $java8Home`
  compiled and linked all five GWT permutations successfully.

## Known Limitations
Only exact integral `+/-5%` four-band resistor nameplates are supported. The
initial board still has one physical resistor and a manually authored layout;
five-band codes, replacement labels, fault-specific markings, and other
component-package nameplates remain out of scope.

## Recommended Next Step
Begin the first validated faulted LED challenge while retaining immutable
nameplates as printed physical identity and CircuitJS as mutable electrical
truth.

## Intended Commit Message
`Enforce component-specific PCB markings`
