# Task report — current checkpoint

**Date:** 2026-11-13 · **Branch:** `master` · **Base:** `5f4abcb3` · **Final:** `72c4f00e` · **Task:** board realism
(compaction). The previous checkpoint (E07) is in
[archive/2026-11-12-e07-checkpoint.md](2026-11-12-e07-checkpoint.md).

## Delivered

- **RB30 compaction** (`5f4abcb3`, pushed). Gravity compaction in the packing probe plus an
  area-dominant selection rule. RB30 fill 11.6% → 16.0%; root 0 went from 230×112 mm to 147×107 mm.
- **RB56 placement 16.** Version 15 plus connected-pocket packing and gravity compaction. Fill
  17.8% → 19.4%; root 0 went from 159×109 mm to 151×105 mm. New private replay namespace
  `tsj-private-session/16`; placement 15 stays selectable and keeps its identity.
- **`PcbBoardDensityContractTest`** reports fill for every family and holds per-family floors.
- Evidence and findings: [task-evidence/board-realism](../task-evidence/board-realism/README.md).

## Not done, by decision

- **Bottom-side parts.** All gameplay packages are through-hole, so bottom placement gains little
  and is unrealistic. It needs SMD packages; the owner deferred it on 2026-11-13.
- **Small boards.** One-face compaction made boards larger or unroutable and was reverted.
  The owner chose on 2026-11-13 to keep small boards one-sided.

## Gates

| Gate | Result |
|---|---|
| `scripts/build.ps1` (JDK 8, GWT 2.7) | PASS, 113 s |
| `PcbBoardDensityContractTest` | PASS, 26 cases |
| RB56, identity and session suites (about 50) | PASS |
| Full native gate (no `-Suite`) | PASS: 215 Java suites plus independent oracles, 52 min. Two earlier runs stopped on child time budgets while a live RB56 board was simulating in the browser; rerun with the browser idle |
| Browser play (built-in browser, real clicks) | PASS: RB30 board and live 11.998 V reading; RB56 placement 16 board loads |

## Limits

- RB56 boards take about 35% longer to generate (the extra compaction route): the 20-board
  physical matrix went from 102 s to 138 s, inside its 600 s child budget.
- REL-B's 30-root RB56 MEDIUM qualification was not re-run for placement 16. Required before the
  beta ships.
- Run alone, `ProceduralFamilyContractTest` stops at RB56 generation with a `CompositeElm` NPE
  (single-suite harness gap). It passes in the full gate. `U04SessionContractTest` passed in this
  task's grouped focused run.

## Resources

- Preview server `tsj-preview` on port 8899, started by the app's preview tool.
- Scratch logs and board SVGs in OS temp only.
- Untouched, still awaiting the owner from the audit: 28 worktrees and 8 stashes.

## Next action

Board realism is complete in its agreed scope. Next: ROADMAP Next up item 2 (stabilize what is
playable), then REL-B close-or-park (including the RB56 placement-16 corpus re-run), then E08.
