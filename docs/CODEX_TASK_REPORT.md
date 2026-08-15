# Task 24: Persistent preview and replaceable LED1

## Result

Task 24 is complete on baseline `5850b9a`. The existing LED-indicator family
retains its original R1-open challenge, while LED1 is now an independently
removable, measurable, replaceable physical part backed by a real CircuitJS
`LEDElm`. The Task 23 diode family and all earlier resistor workflows remain
green.

## Persistent production preview

The recurring blank preview was caused by using the blocking `preview.ps1`
server as a child of short-lived automation shells. When its launcher exited,
the child server was not guaranteed to survive, leaving an already-open browser
pointed at a dead localhost endpoint.

`start-preview.ps1` now starts that same dependency-free `HttpListener` server
in an independent hidden PowerShell process, records repository path, exact
preview script path, PID, process start ticks, and port under ignored
`.tools/preview`, and waits for both the host page and GWT bootstrap to return
HTTP 200. Existing healthy owned previews are idempotently reused; stale state
is removed, while ambiguous or unrelated listeners are never killed.
`stop-preview.ps1` revalidates the repository, script command line, PID, and
start time before stopping anything, then removes its state.

Cross-process proof used a short-lived launcher PID 27348, which exited. A
different PowerShell process then reached both
`circuitjs.html?tsjChallenge=led&seed=3` and
`circuitjs1/circuitjs1.nocache.js` with HTTP 200 from detached server PID 20308.
The final server remains live at:

`http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=3`

The production sidebar is now hosted in a normal scroll container. This fixes a
related visible integration defect where the fixed-height page and disabled body
scrolling made lower catalog/tray actions unreachable after the sidebar grew.

## LED physical and electrical model

`LedIndicatorFamilyState` owns both families of repair state: the existing R1
slot/inventory/catalog and the new LED1 slot/inventory/catalog/serial allocator.
No LED-specific state was added to family-agnostic `GeneratedBoardInstance`.

`PhysicalLedPart` owns a stable physical ID, immutable LED nameplate, unique
`LEDElm`, distinct anode/cathode endpoints, installed/loose location, and
installation orientation. The initial healthy `LED1_ORIGINAL` is installed and
the loose inventory is empty. Removal preserves that same identity. Repeated
catalog acquisitions allocate separate IDs, separate collision-free hidden
backing elements, and distinct endpoints. Catalog entries remain immutable,
nonphysical, and non-depleting.

The catalog deliberately exposes one honest generic-red LED specification plus
a reversed installation of that same specification; CircuitJS color alone is
not represented as a different electrical model. Correct installation maps the
backing anode/cathode to `LED1.A`/`LED1.K`; reversed installation swaps the real
terminal attachments. Removal and installation retarget the actual detachable
connection graph and operational LED binding. The existing generic lead actions
also support lifting/reconnecting LED1 anode or cathode while power is off.

Installed and tray LED rendering retain a recognizable through-hole body and
cathode/flat polarity cue. Loose LED probe targets resolve to the physical
part's endpoints, while an installed part is absent from loose tray targeting.

## Automated electrical evidence

The dedicated LED physical verifier passed seeds 0, 2, and 3. It proved initial
single-part occupancy, identity-preserving removal, canonical one-time backing
ownership, unique acquired IDs and `LEDElm` instances, geometrically distinct
terminals, unchanged catalog entries, installed-versus-loose target exclusion,
and coherent slot/inventory bindings. It also proved:

- installed and loose healthy LED diode tests are solver-derived;
- forward voltage is within the LED/compliance range and reverse is `OL`;
- repairing R1 while LED1 is missing does not complete the challenge;
- a physically reversed LED carries effectively zero branch current, stays
  dark, and does not complete repair;
- a correctly oriented replacement restores 5-15 mA solved branch current,
  illumination, and functional completion.

## Normal-player browser evidence

A fresh seed-3 production route used browser mouse input on the visible canvas
and controls plus browser keyboard input on visible catalog selectors. It did
not use verifier flags, synthetic DOM events, controller calls, or direct state
mutation. The observed LED sequence was:

- initial installed LED1, empty Parts Tray, and separate non-depleting LED
  Replacement Catalog;
- `LED1_ORIGINAL` removed while electrically unpowered;
- loose original forward diode reading: **1.595 V**;
- loose original reverse reading: **OL**;
- a healthy new LED alone did not bypass the original R1-open fault;
- a reversed installed LED remained dark and did not complete the repair after
  R1 was replaced;
- the measured correctly oriented replacement plus correct 1 kOhm R1 completed
  the challenge and illuminated LED1;
- after removal, `LED1_ORIGINAL` and `LED1_CATALOG_PART_0` remained separate
  physical identities.

The final UI harness also uses real wheel scrolling before off-screen sidebar
clicks and deterministic keyboard tab traversal to catalog selectors. This
validates the same interactions a player can now reach through the scrolling
sidebar.

## Regression and build results

- Explicit Temurin JDK 8u502 production build: PASS.
- GWT permutations 0 through 4: all compiled successfully.
- Production linking: PASS.
- Existing 15-route LED/resistor matrix for seeds 0, 2, and 3: **15/15 PASS**.
- Existing resistor normal-player flow: **1 kOhm** forward, **1 kOhm** reverse,
  faulted original **OL**.
- Existing diode verifier routes for seeds 0, 2, and 3: **3/3 PASS**.
- Existing diode normal-player flow: **496.051 mV** forward, reverse **OL**,
  faulted original **OL**; body/band/LED pixels remained visibly distinct.
- New LED physical verifier routes for seeds 0, 2, and 3: **3/3 PASS**.
- New LED normal-player flow: PASS with **1.595 V** forward and reverse **OL**.
- Fresh detached-preview normal-player load: PASS.
- No required route timed out in the final runs, and no page error or
  failure-class console message was captured.
- `git diff --check`: PASS.

## Committed visual evidence

All images are final-build production-browser captures at 1416x908 and were
pixel-inspected as nonblank, non-error application views:

- `docs/task-evidence/task-24/initial-board.png` — fresh LED challenge with
  installed LED1, empty physical tray, and separate catalogs.
- `docs/task-evidence/task-24/led-selected.png` — LED1 selection outline plus
  visible anode/cathode lift and Remove component controls.
- `docs/task-evidence/task-24/led-removed-parts-tray.png` — empty LED1 footprint
  and the original physical LED visible in both canvas and sidebar Parts Tray.
- `docs/task-evidence/task-24/repaired-board.png` — illuminated LED1, solver-
  backed repair ticket, and separate loose physical parts retained in the tray.
- `docs/task-evidence/task-24/persistent-preview-fresh-load.png` — fresh normal-
  player browser load served after the detached launcher's process exited.

## Files changed

- `AGENTS.md`
- `DEVELOPMENT.md`
- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`
- five PNG files under `docs/task-evidence/task-24/`
- `scripts/preview.ps1`
- `scripts/start-preview.ps1`
- `scripts/stop-preview.ps1`
- `scripts/verify-browser.ps1`
- `BoardPhysicalSpecifications.java`
- `CirSim.java`
- `ComponentLeadProbeTarget.java`
- `DynamicLedBackingAllocator.java`
- `GeneratedComponentOperationalStates.java`
- `LedCatalogEntry.java`
- `LedComponentSlot.java`
- `LedIndicatorFamilyState.java`
- `LedIndicatorGenerator.java`
- `LedIndicatorRepairValidator.java`
- `LedNameplate.java`
- `LedPartLocation.java`
- `LedPhysicalDeveloperVerifier.java`
- `LedReplacementCatalog.java`
- `LedReplacementInventory.java`
- `LedSlotController.java`
- `PcbWorkbenchController.java`
- `PcbWorkbenchRenderer.java`
- `PhysicalLedPart.java`
- `PhysicalLedPartProbeTarget.java`
- `ResistanceMeasurementDeveloperVerifier.java`

## Remaining limitations and recommendation

The LED family intentionally still injects only the original R1-open fault; no
LED-specific failure mode was added. The LED catalog uses one defensible
electrical LED model with orientation choices rather than pretending cosmetic
colors have distinct electrical behavior. The localhost preview serves the most
recent production output and therefore still requires an explicit build after
source changes.

The recommended next feature is a separately scoped LED-open or LED-short fault
strategy that reuses this physical-part architecture and adds its own generated
fault validation without changing the existing R1 challenge.
