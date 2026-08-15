# Task #18: Replacement Validation Completion

## Restored automated coverage

- Restored `verifyUnpoweredDcVoltageCases(...)`. It runs with electrical board power off, `R1_ORIGINAL` loose and faulted, and a dynamically acquired healthy 1 kOhm part loose. It asserts 0 V for VIN and across both loose parts.
- The existing twelve 1 kOhm acquisitions now prove unique backing post coordinates, no overlap with the canonical generated graph, distinct physical identity/backing/endpoints, unchanged catalog entries, exact tray page count, at most three parts per page, correct visible probe targets, no hidden marker, selection clearing on page change, and restored marker identity after returning to a page.

## Normal-player interaction results

Removed `R1_ORIGINAL` measures `OL` in both orientations. A removed healthy 1 kOhm catalog part measures within its 1 kOhm +/-5% nameplate in both orientations. With one lead lifted, its body remains within that nameplate range in both orientations, the lifted-lead-to-pad gap reads `OL`, and the attached lead-to-pad path reads approximately 0 Ohm. Reconnecting and powering restores the LED's solved 5–15 mA operation.

Same-value 1 kOhm parts retain distinct physical identities across pages. A retained loose target has no marker on another page, returns to the same part after paging back, and is cleared when installed.

## Screenshots

- `docs/screenshots/task-18/initial-empty-tray-catalog.png`: installed R1, empty canvas/sidebar Parts Tray, and separate Replacement Catalog.
- `docs/screenshots/task-18/removed-parts-only.png`: only loose physical parts, page 1 of 2, no installed part in the tray, and separate non-depleting catalog.

Pixel inspection passed for both images.

## Validation

- Explicit JDK 8 production build passed: all five GWT permutations compiled and linked.
- `git diff --check` passed.

## Files changed

- `src/com/lushprojects/circuitjs1/client/ReplacementDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/PcbWorkbenchRenderer.java`
- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`

## Remaining limitation

Replacement remains deliberately scoped to the LED-board R1 slot; no additional component family was introduced.
