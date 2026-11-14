# Task report — playability checkpoint (archived 2026-11-13)

**Date:** 2026-11-13 · **Branch:** `master` · **Base:** `72c4f00e` · **Task:** stabilize what is
playable, two rounds (ROADMAP Next up item 1). Round 1 was committed as `64eeedff`. The previous checkpoint (board realism) is in
[archive/2026-11-13-board-realism-checkpoint.md](archive/2026-11-13-board-realism-checkpoint.md).

## Delivered

Found by playing a fresh relay output board from ticket to repair. Details:
[task-evidence/playability-pass](task-evidence/playability-pass/README.md).

- **Applied input is visible.** HIGH/LOW and sensor LOW/MID/HIGH buttons show which setting is
  applied, through the optional `GeneratedActiveInputOperations` (relay, NPN, NMOS, sensor,
  controlled indicator, RB30/RB56). Momentary START is unchanged.
- **Probe markers hide when the meter is off.** Probe positions are kept for the next mode.
- **Click-to-install from the tray.** A plain click on a loose tray part opens its Install menu;
  shop and help text updated.
- **Tray captions** "Removed part" / "New part".
- **"Install as K1"** instead of "Install as Circuit / K1" on single-region boards.

Round 2, from playing the 555 timer, two-channel controller, RB30 and RB56 boards:

- **Tray numbering** runs 1..n among loose parts instead of counting installed parts.
- **Toolbar fit:** long board input labels wrap to two lines, so RB30/RB56 keep "Parts tray"
  on screen at 1280 px.
- **Meter OFF key** is lit only while the meter is off.
- **Preparation text** no longer says only timing boards are slow.

## Gates

| Gate | Result |
|---|---|
| `scripts/build.ps1` (JDK 8, GWT 2.7) | PASS |
| 23 focused suites touching the changed classes | PASS (one rerun after a 60 s child budget tripped while a board simulated in the browser) |
| Full native gate (no `-Suite`), browser idle | PASS: 215 Java suites plus independent oracles, 52 min |
| `tests/contracts/u04_ui_contract.mjs` | NOT RUN: jsdom is not installed here; only its expected hint strings changed |
| Browser play (built-in browser, real clicks) | PASS: relay board toggle, meter off, remove/buy/caption/click-install; sensor board toggle |
| Round 2: `scripts/build.ps1` | PASS |
| Round 2: 12 focused suites (A03, architecture footprint, physical serviceability, Q30 catalog/service, RB56 fault/physical owner, sensor control, U04/U06 session, E07) | PASS, 5 min |
| Round 2: full native gate, browser idle | PASS: 215 Java suites plus independent oracles, 51 min; RB56 matrix 133 s |
| Round 2: `bench_meter_contract.mjs`, `u04_ui_contract.mjs` | NOT RUN: jsdom missing |
| Round 2: browser play | PASS: 555 flasher, two-channel, RB30 (remove two, renumber, click-install), RB56 toolbar and inputs, meter OFF key |

## Open items for this milestone

- Single-sided boards show no copper in the default top view (bottom view exists).
- Empty footprints are not click targets.
- The scope's 8192-sample window (about 0.8 s on the 555 board) cannot show a 0.7–2 Hz
  flasher; documented E07 limit, needs decimated capture (U09).
- RB30/RB56 preparation takes 60–80 s in the browser.
- Families not played in either round: indicator, protected indicator, dual indicator, RC
  timing, BJT/MOSFET driver, RB15 (all share the same workbench code paths).

## Limits

- REL-B's 30-root RB56 MEDIUM qualification is still not re-run for placement 16.
- Run alone, `ProceduralFamilyContractTest` stops at RB56 generation with a `CompositeElm` NPE
  (single-suite harness gap). It passes in the full gate.

## Resources

- Preview server `tsj-preview` on port 8899, started by the app's preview tool.
- Scratch logs in OS temp only.
- Untouched, still awaiting the owner from the audit: 28 worktrees and 8 stashes.

## Next action

Owner decision on REL-B (close or park), then E08 (jumpers, trace cuts).
