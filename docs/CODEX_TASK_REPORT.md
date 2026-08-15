# Latest Codex Task Report

## Task
Corrective follow-up to Task #13: make generated PCB markings electrically
honest before introducing the first faulted challenge.

## Summary
The LED indicator PCB no longer hardcodes a `+9V` marking or a single resistor
color pattern. Each generated board now carries immutable physical/nameplate
metadata, built from the same value selection as its initial CircuitJS source
and resistor. PCB labels, installed/lifted/tray resistor bands, and the
component panel read that metadata instead of live solver elements.

## Architecture Decisions
- `BoardPhysicalSpecifications` belongs to `GeneratedBoardInstance` and holds
typed `ResistorNameplate` and `PowerInputNameplate` records keyed by stable
logical IDs.
- The generator selects the supply/resistor pair once, then uses those values
for both nameplate metadata and the initial CircuitJS construction.
- `ResistorColorCode` returns semantic `ResistorColorBand` tokens. The PCB
renderer owns the local token-to-CSS mapping, so display colors are not part
of the logical physical specification.
- Nameplates are immutable physical markings, deliberately distinct from
mutable CircuitJS behavior and future injected-fault state. A lifted or
removed part retains its original marking and value in the tray.
- `BoardModificationRejectedException` is the only exception translated into
the user-facing power-off message. Unexpected structural/mutation failures
remain visible rather than being mislabeled as safety rejections.

## Files Changed
- Added immutable nameplate and color-code model classes under
`src/com/lushprojects/circuitjs1/client/`.
- Updated `LedIndicatorGenerator`, `GeneratedBoardInstance`, PCB renderer,
controller, modification controller, and URL-gated developer verifier.
- Updated `docs/ARCHITECTURE.md`, this report, and screenshots in
`docs/screenshots/task13/`.

## Deterministic Validation Data
| Seed | Input Marking | R1 Nameplate | Four-Band Code |
| --- | --- | --- | --- |
| `0` | `+5V` | `330 Ohm +/-5%` | orange, orange, brown, gold |
| `2` | `+9V` | `680 Ohm +/-5%` | blue, gray, brown, gold |
| `3` | `+12V` | `1000 Ohm +/-5%` | brown, black, red, gold |

The URL-gated verifier creates all three fixtures directly and confirms their
immutable values equal the initial CircuitJS resistor/source values and their
semantic color-band tokens. It retains the existing full seed-2 electrical
regression: measurement, power isolation, lead lift, removal, tray probing,
restoration, canonical export/order, and healthy LED-current validation.

## Build And Browser Validation
- Recovered JDK 8 home:
`C:\Users\david\AppData\Local\Temp\TroubleshootJS-build-probe\temurin8`.
- Explicit verification:
`& "$jdk8Home\bin\java.exe" -version` reported
`openjdk version "1.8.0_502"`.
- Exact production build command:
`$java8Home = Join-Path $env:TEMP 'TroubleshootJS-build-probe\temurin8'; & .\scripts\build.ps1 -JavaHome $java8Home`
- Result: all five GWT permutations compiled and linked successfully.
- Existing local server at `http://127.0.0.1:8888` served the current build;
a second JDK-8 `dev.ps1 -JavaHome $jdk8Home` launch correctly deferred because
the port was already occupied.
- URL-gated verifier:
`http://127.0.0.1:8888/circuitjs.html?tsjFixture=led&seed=2&tsjVerifyResistance=true&reviewfix=honest`
completed with no browser console or page errors.
- Normal PCB views for seeds `0`, `2`, and `3` visibly showed their matching
`+5V`, `+9V`, and `+12V` labels and correct installed color bands. Selecting
R1 showed the corresponding value/tolerance in the component panel.
- Browser interaction on seed `2` powered the board off, lifted a lead,
removed R1, confirmed its tray bands and exposed endpoint cues, then restored
it. The panel retained `680 Ohm +/-5%` in every state; browser console/page
errors remained empty.

## Visual Captures
- `markings-seed-0-installed.png`, `markings-seed-2-installed.png`, and
`markings-seed-3-installed.png`: normal installed boards for every value
variant.
- `markings-seed-0-panel.png`, `markings-seed-2-panel.png`, and
`markings-seed-3-panel.png`: selected R1 panel values for every variant.
- `markings-seed-2-lifted.png`, `markings-seed-2-removed-tray.png`, and
`markings-seed-2-restored.png`: the physical state transition with the
marking invariant.

## Known Limitations
The initial metadata model covers resistor and input nameplates only. It does
not yet model replacement part markings, fault-specific markings, capacitor
values, or arbitrary package labeling. The PCB layout remains manually authored
for the LED indicator family.

## Recommended Next Step
Introduce the first validated faulted LED challenge using this immutable
nameplate boundary, then add replacement-component metadata without allowing
the PCB renderer to infer markings from mutable solver elements.
