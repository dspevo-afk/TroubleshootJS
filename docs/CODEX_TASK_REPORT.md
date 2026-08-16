# Task 29 — Player-Facing Component Identification Fidelity

Status: complete

## Milestone and acceptance

Implemented the first eligible roadmap milestone, Task 29, for the existing
`LED_INDICATOR`, `DIODE_PROTECTED_INDICATOR`, and
`PARALLEL_DUAL_INDICATOR` families.

The bounded acceptance criteria were:

- original installed resistors show physical color bands and no ordinary
  player-facing numeric value;
- removed originals retain their physical identity, bands, and measurement
  behavior without exposing the nominal value;
- catalog selections and known replacement parts may show their catalog value;
- generated family descriptions, ordinary DOM text, attributes, and
  accessibility-visible UI do not leak the original value;
- fixed original resistors without detachable bindings still show an accurate
  installed state;
- stable board/component/pad/net identity, CircuitJS graph ownership,
  simulation-backed measurements, fault behavior, repair behavior, and layout
  behavior remain intact.

Task 30 was not started.

## Result

`PcbWorkbenchController` now distinguishes original physical resistor
identity from catalog/replacement identity. Original panels show type,
installed state, and `Markings: Color bands`; loose original parts show a
removed-resistor identity without their nominal value. Catalog options and
installed catalog parts retain their known values. Original resistor values
were removed from the current family descriptions.

`PhysicalResistorPart` retains the stable original-part contract used by the
current generated families. The developer resistance verifier now asserts the
privacy-safe original panel while continuing to prove a real solver-backed
resistance measurement.

The browser verifier now checks color-band pixels, player-visible value leaks,
original/removed identity, the fixed parallel resistor state, replacement
catalog visibility, and the complete repair path. A post-removal animation-frame
wait was added after review found a real DOM/canvas synchronization race.

The browser-validation paragraphs requested by the user were restored before
completion in `AGENTS.md` and `.codex/agents/coder.toml`.

## Delegation and review

- Coder subagent `Goodall` implemented the bounded candidate and ran the
  initial build and player-flow checks.
- Reviewer subagent `Hume` found two valid issues during the normal review
  loop: missing `State: Installed` for fixed original resistors and a
  post-removal browser-render race. The coder made the two narrow corrections;
  the reviewer independently returned `PASS`.
- Luna MAX performed one independent final-review round and returned
  `FINAL PASS`.
- Sol escalation was not required.

## Files changed

- `AGENTS.md` — restored the requested browser-evidence process paragraphs.
- `.codex/agents/coder.toml` — restored the requested coder browser-evidence
  process paragraphs.
- `src/com/lushprojects/circuitjs1/client/PcbWorkbenchController.java` —
  privacy-safe original/replacement contextual labels and fixed-resistor state.
- `src/com/lushprojects/circuitjs1/client/PhysicalResistorPart.java` —
  original physical-part identity helper.
- `src/com/lushprojects/circuitjs1/client/LedIndicatorGenerator.java` —
  removed the original resistor value from the generated description.
- `src/com/lushprojects/circuitjs1/client/DiodeProtectedIndicatorGenerator.java`
  — removed the original resistor value from the generated description.
- `src/com/lushprojects/circuitjs1/client/ResistanceMeasurementDeveloperVerifier.java`
  — updated privacy and solver-measurement assertions.
- `scripts/verify-browser.ps1` — added player privacy, color-band, identity,
  state, catalog, and render-synchronization checks.
- `docs/ARCHITECTURE.md` — documented the player-facing identification boundary.
- `docs/ROADMAP.md` — marked Task 29 complete and Task 30 as next.
- `docs/task-evidence/task-29/` — five curated production-browser screenshots.

## Validation evidence

Build and static checks:

- `.\scripts\build.ps1 -JavaHome .\.tools\jdk8-download\jdk8u502-b07` — PASS;
  all five GWT permutations compiled and linked.
- PowerShell verifier parsing — PASS.
- `git diff --check` — PASS; only the repository's existing CRLF conversion
  warnings were reported.

Production-browser validation against the final build:

- `.\scripts\verify-browser.ps1 -NormalPlayer -PlayerSeed 3` — PASS;
  original removed as `OL`, replacement measured as `1 kOhm`, and repair
  verified.
- `.\scripts\verify-browser.ps1 -DiodeNormalPlayer -PlayerSeed 3
  -EvidenceDirectory docs/task-evidence/task-29` — PASS; forward diode
  measurement, reverse `OL`, privacy-safe resistor UI, and repair verified.
- `.\scripts\verify-browser.ps1 -ParallelNormalPlayer
  -EvidenceDirectory docs/task-evidence/task-29` — PASS; fixed R2 state and
  bands, parallel measurement, original identity, and repair verified.
- `.\scripts\verify-browser.ps1 -Seeds 0,2,3` — PASS, all 15 existing
  LED verifier routes.
- `.\scripts\verify-browser.ps1 -Route resistance -Seeds 0,2,3` — PASS,
  all 3 resistance routes.
- `.\scripts\verify-browser.ps1 -Diode -Seeds 0,2,3` — PASS, all 3
  diode routes.
- `.\scripts\verify-browser.ps1 -Parallel -Seeds 0,2,3` — PASS, all 3
  parallel routes.

The Codex built-in browser was also used against the final production preview.
It visibly confirmed the parallel board's original resistor bands, the
catalog-only numeric values, and the fixed resistor panel state:
`Markings: Color bands` and `State: Installed`.

## Visual evidence

All five images were captured from the final production preview, pixel-inspected,
and found nonblank with the intended application state and useful viewport:

- [`initial-board.png`](task-evidence/task-29/initial-board.png) — diode-family
  original R1 selected; type, bands, and installed state are visible without a
  numeric value.
- [`parallel-seed-3.png`](task-evidence/task-29/parallel-seed-3.png) — compact
  parallel board with original R2 selected; bands and installed state remain
  visible while the replacement catalog is separate.
- [`parallel-faulted.png`](task-evidence/task-29/parallel-faulted.png) —
  faulted board state with physical resistor markings and no original numeric
  metadata in the panel.
- [`parallel-measurement.png`](task-evidence/task-29/parallel-measurement.png)
  — active DC measurement state on the generated board.
- [`parallel-repaired.png`](task-evidence/task-29/parallel-repaired.png) —
  repaired functional state with the original resistor retained as a loose
  physical part labeled without its value.

## Remaining bounded limitations

The original/catalog distinction currently relies on the generated
`*_ORIGINAL` physical-ID convention used by the implemented resistor families.
Other component types still need their own physical-marking policies, as
planned by later roadmap work. The simulator remains a one-sided through-hole
workbench; this milestone does not add new circuit families, fault types,
scoring, or functional-completion architecture.

## Roadmap handoff

Task 29 is complete. The next eligible milestone is Task 30 — Generic
Functional Challenge Completion Contract. It is identified only and was not
started in this task.

Commit message: `Task 29: hide original resistor values from player UI`
