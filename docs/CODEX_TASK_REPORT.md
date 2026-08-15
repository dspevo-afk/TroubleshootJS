# Task #18: Unlimited Resistor Replacement Catalog

## Summary

The prior Task 17 model pre-created three loose `PhysicalResistorPart` spare
resistors and their CircuitJS backing elements for every LED board. That made a
finite inventory look like a catalog and conflated an orderable specification
with a physical, probeable part.

Task 18 introduces a 73-entry immutable E12 `+/-5%` replacement catalog from
`10 Ohm` through `10 MOhm`. The generated LED board starts with only the
faulted, installed `R1_ORIGINAL`; the physical tray starts empty. A catalog
selection creates a new physical part only when it is successfully installed.

## Architecture

- `ResistorReplacementCatalog` and `ResistorCatalogEntry` are specification
  only: no element, location, physical ID, or probe target.
- `ResistorSlotController.installNewFromCatalog` creates one new resistor
  element, nameplate, and monotonically assigned physical ID after all
  rejection checks pass.
- `DynamicResistorBackingAllocator` scans occupied simulation post coordinates
  and deterministically allocates a free pair for each acquired part.
- Runtime backing elements are registered once in canonical generated-board
  ownership and once in the active CircuitJS graph before attachment.
- The physical Parts Tray includes only removed loose parts. Installed parts are
  not tray entries or tray probe targets.
- Tray pagination shows three loose parts per page and shares its page slice
  across drawing, hit-testing, selection, and lead geometry. Hidden retained
  targets keep their electrical identity but have no visible marker point.
- `AGENTS.md` now includes the permanent Persistence and Retry Protocol and its
  clarification that retry counts do not excuse leaving a diagnosable product
  defect unresolved.

## Removal Investigation

A normal-page test initially observed an apparent `Lift lead 2` result after a
Remove click. URL-gated diagnostics captured a fresh browser event sequence:
`pointerdown`, `mousedown`, `pointerup`, `mouseup`, and `click` all targeted
the `Remove component` button. Its sole GWT handler invoked
`ResistorSlotController.removeInstalledPart`; `BoardModificationController`
removed `R1.1` and `R1.2`; the slot cleared and `R1_ORIGINAL` became loose.
The earlier result was stale interaction state after post-initialization
viewport/page reuse, not a partial electrical mutation or duplicate handler.
Temporary diagnostics were removed before the final build.

## Verification

- Explicit JDK 8 production build completed all five GWT permutations and
  linked successfully.
- Complete production verifier matrix passed for seeds 0, 2, and 3: resistance,
  meter, challenge, replacement, and challenge-plus-replacement for each seed
  (15 routes). Recorded page-error count: zero. Recorded failure-class console
  message count: zero.
- Replacement verification checks the 73-entry catalog, initial physical-only
  inventory, faulty original resistance, wrong and correct functional behavior,
  canonical graph topology, and 12 repeated 1 kOhm acquisitions. The loop
  asserts distinct IDs, elements, endpoints, canonical ownership, retained
  loose parts, and non-depleting catalog size.
- Fresh normal-player validation removed the original, installed and removed a
  wrong 10 Ohm catalog part, and repeatedly acquired/removed 1 kOhm parts to
  produce a second tray page. Canvas and sidebar both showed page 1 of 2.
- Final screenshot pixel inspection completed for both paths below.

## Screenshots

- `docs/screenshots/task-18/initial-empty-tray-catalog.png`
- `docs/screenshots/task-18/removed-parts-only.png`

## Files Changed

- `AGENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`
- `docs/screenshots/task-18/initial-empty-tray-catalog.png`
- `docs/screenshots/task-18/removed-parts-only.png`
- Catalog, allocation, generated-board ownership, controller, renderer, UI,
  meter-marker safety, and verifier sources under `src/com/lushprojects/circuitjs1/client`.

## Known Limitations And Next Task

Catalog replacement currently covers only the replaceable LED-board R1 slot;
capacitors, diodes, and other component families remain out of scope. The
recommended next task is to generalize the same-type catalog/slot architecture
to another component family before introducing persistent stress and hidden
damage.
