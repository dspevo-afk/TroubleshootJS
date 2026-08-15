# Task 23: Replaceable silicon-diode challenge family

## Delivered family

Task 23 adds the `DIODE_PROTECTED_INDICATOR` family with the generated series
topology VIN -> D1 -> R1 -> LED1 -> GND. Seeds 0, 2, and 3 deterministically use
5 V/330 Ohm, 9 V/680 Ohm, and 12 V/1 kOhm operating points. The service ticket
states only `Indicator does not light.`

D1 is a real CircuitJS `DiodeElm` using the built-in `default` silicon model and
normal nonlinear solving. The implementation deliberately calls the catalog
part a generic silicon diode rather than claiming exact manufacturer behavior.
The original physical D1 owns a private series isolation switch; the selected
`D1 OPEN` fault therefore remains electrically open after the part is removed.

## State and physical identity

`DiodeProtectedIndicatorFamilyState` owns the D1 slot, physical inventory,
non-depleting catalog, and serial allocator. `GeneratedBoardInstance` remains
unchanged and family-agnostic, containing only its `GeneratedBoardFamilyState`
reference.

Each `PhysicalDiodePart` owns a stable physical ID, immutable nameplate, unique
CircuitJS backing diode, public anode/cathode endpoints, installed/loose state,
installation orientation, and optional internal fault binding. Catalog rows are
specifications only. Repeated correct/reversed acquisition produced distinct
`D1_CATALOG_PART_0` and `D1_CATALOG_PART_1` identities while retaining
`D1_ORIGINAL` separately.

The catalog offers a normal generic-silicon installation and a reversed
orientation of the same defensible model. The reversed part changed the actual
attachment polarity, carried effectively zero branch current, left LED1 dark,
and did not complete the challenge.

## PCB and interaction

The one-sided PCB includes stable `D1.A` and `D1.K` pads. D1 is rendered as a
separate axial black diode with two leads, a contrasting cathode band, `D1`
reference, and a `K` marking aligned with electrical polarity. Production-canvas
pixel inspection returned these representative RGB values:

- diode body: `40,44,49`
- cathode band: `216,221,224`
- LED1 body: `181,35,45`

The distinct colors and separated geometry confirm D1, its band, and LED1 are
visibly distinguishable. Removed parts render in the existing paginated Parts
Tray and remain electrically probeable. D1 supports anode/cathode lead lifting,
isolated diode testing, and reconnection through the shared detachable-lead
architecture.

## Automated diode evidence

The dedicated verifier ran on seeds 0, 2, and 3 and proved:

- healthy generated supply, forward orientation, 5-15 mA matched branch current,
  plausible silicon forward drop, and illuminated LED operation;
- powered `D1 OPEN` behavior with effectively zero D1/LED current and LED off;
- installed and loose faulted-original diode mode reads OL in both orientations;
- a healthy loose diode gives a solver-derived forward voltage and reverse OL;
- a lifted healthy D1 retains forward/reverse diode behavior;
- removal/reinstallation preserves backing ownership and separate identities;
- a reversed replacement does not repair the board;
- a correctly oriented healthy replacement restores matched solved current,
  LED operation, and functional challenge completion.

All three routes passed:

| Seed | Diode family verifier |
|---:|---|
| 0 | PASS |
| 2 | PASS |
| 3 | PASS |

No route timed out, published a verifier failure, or produced a captured page or
failure-class console error.

## Normal-player diode regression

The fresh seed-3 production route used browser mouse input against visible
buttons and canvas geometry; it did not call controllers or dispatch DOM events.
The flow observed the vague complaint, powered off, diode-tested installed D1 in
both orientations, removed it, and diode-tested the loose original in both
orientations. All four faulted readings were `OL`.

It then installed the default healthy catalog diode and powered on. CircuitJS
restored LED operation and the UI reported `Repair verified. Indicator operating
normally.` After powering off and removing the replacement, the tray retained
both `D1_ORIGINAL` and `D1_CATALOG_PART_0` as separate physical identities. The
healthy loose diode measured exactly `496.051 mV` forward and `OL` reverse; the
original still measured `OL`. The normal-player diode route passed with no page
or relevant console errors.

## Task 22 harness cleanup

The resistor normal-player reversal now waits for an observable intermediate
measurement and then a completed reverse measurement, preventing acceptance of
the stale forward display. Browser cleanup sends a DevTools `Browser.close`,
waits for Edge, retries exact-profile deletion, and only warns on cleanup failure
without masking the verifier result. A before/after profile-count audit around
the complete matrix remained unchanged, proving that the run created no
persistent profile.

## Production build and regressions

The explicit Temurin JDK 8u502 production build compiled all five GWT
permutations and linked successfully into `war/circuitjs1`.

The complete pre-existing Task 22 matrix remained green:

| Seed | Resistance | Meter | Challenge | Replacement | Challenge + replacement |
|---:|---|---|---|---|---|
| 0 | PASS | PASS | PASS | PASS | PASS |
| 2 | PASS | PASS | PASS | PASS | PASS |
| 3 | PASS | PASS | PASS | PASS | PASS |

The existing resistor normal-player flow also passed with `1 kOhm` forward,
`1 kOhm` reverse, and the faulted original at `OL`. Across the old 15-route
matrix, three new diode routes, and both normal-player flows there were no route
timeouts, unhandled JavaScript errors, or failure-class console messages.

## Files changed

- `DEVELOPMENT.md`
- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`
- `scripts/verify-browser.ps1`
- `BoardPhysicalSpecifications.java`
- `CirSim.java`
- `ComponentLeadProbeTarget.java`
- `GeneratedBoardFamilyState.java`
- `GeneratedBoardVerifier.java`
- `GeneratedChallengeController.java`
- `LedIndicatorFamilyState.java`
- `PcbWorkbenchController.java`
- `PcbWorkbenchRenderer.java`
- `DiodeCatalogEntry.java`
- `DiodeComponentSlot.java`
- `DiodeFamilyDeveloperVerifier.java`
- `DiodeNameplate.java`
- `DiodePartLocation.java`
- `DiodeProtectedIndicatorFamilyState.java`
- `DiodeProtectedIndicatorFaultValidator.java`
- `DiodeProtectedIndicatorGeneratedBoardValidator.java`
- `DiodeProtectedIndicatorGenerator.java`
- `DiodeProtectedIndicatorPcbLayout.java`
- `DiodeProtectedIndicatorRepairValidator.java`
- `DiodeReplacementCatalog.java`
- `DiodeReplacementInventory.java`
- `DiodeSlotController.java`
- `DynamicDiodeBackingAllocator.java`
- `PhysicalDiodePart.java`
- `PhysicalDiodePartProbeTarget.java`

## Preview, limitations, and next feature

Run `./scripts/preview.ps1` and open:

`http://127.0.0.1:8899/circuitjs.html?tsjChallenge=diode&seed=3`

The initial catalog intentionally models one generic silicon diode with two
installation orientations; it does not model manufacturer-specific switching,
recovery, power, or thermal limits. Players select the reversed orientation as a
catalog installation choice rather than freely rotating any loose diode during
installation. These are genuine scope limitations, not meter or solver
shortcuts.

A suitable next feature is a second diode-compatible fault or topology (for
example reverse-polarity protection behavior under a controlled reversed input),
while retaining the same physical-part and family-state boundaries.
