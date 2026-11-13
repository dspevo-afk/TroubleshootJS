# Task report — current checkpoint

**Date:** 2026-11-13 · **Branch:** `master` · **Base:** `72c4f00e` · **Task:** stabilize what is
playable, first pass (ROADMAP Next up item 1). The previous checkpoint (board realism) is in
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

## Gates

| Gate | Result |
|---|---|
| `scripts/build.ps1` (JDK 8, GWT 2.7) | PASS |
| 23 focused suites touching the changed classes | PASS (one rerun after a 60 s child budget tripped while a board simulated in the browser) |
| Full native gate (no `-Suite`), browser idle | PASS: 215 Java suites plus independent oracles, 52 min |
| `tests/contracts/u04_ui_contract.mjs` | NOT RUN: jsdom is not installed here; only its expected hint strings changed |
| Browser play (built-in browser, real clicks) | PASS: relay board toggle, meter off, remove/buy/caption/click-install; sensor board toggle |

## Open items for this milestone

- Tray part numbers skip because they count installed parts.
- Single-sided boards show no copper in the default top view (bottom view exists).
- Empty footprints are not click targets.
- Only the relay and sensor boards were played in this pass; other families are next.

## Limits

- REL-B's 30-root RB56 MEDIUM qualification is still not re-run for placement 16.
- Run alone, `ProceduralFamilyContractTest` stops at RB56 generation with a `CompositeElm` NPE
  (single-suite harness gap). It passes in the full gate.

## Resources

- Preview server `tsj-preview` on port 8899, started by the app's preview tool.
- Scratch logs in OS temp only.
- Untouched, still awaiting the owner from the audit: 28 worktrees and 8 stashes.

## Next action

Continue the playability pass on the other families (555 timer, RB30, RB56, two-channel
controller), then REL-B close-or-park, then E08.
