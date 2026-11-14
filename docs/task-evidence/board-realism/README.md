# Board realism · compaction

Owner request, 2026-11-12: make generated boards compact, and put parts on the
bottom of boards with more than 30 parts. On 2026-11-13 the owner chose to
compact first and leave bottom-side parts for later. Ownership is described in
[ARCHITECTURE.md](../../ARCHITECTURE.md) under "Gravity compaction".

## What changed for a player

| Family | Before | After |
|---|---|---|
| RB30 root 0 (35 parts) | 230×112 mm, 11.6% fill (family mean) | 147×107 mm, 16.0% fill (family mean) |
| RB30 root 17 (26 parts) | 226×91 mm | 171×60 mm |
| RB56 | placement 15, 17.8% fill (family mean) | unchanged: placement 16 (19.4% fill) was removed the same day, see below |
| Ten EASY families, RB15, two-channel controller | unchanged | unchanged |

Fill is the summed routing-courtyard area of every part over the board outline
area, measured by `PcbBoardDensityContractTest` on roots 0 and 17.

## Why it was loose

- The packing probe already tried one compacted layout, but kept it only if it
  was smaller **and** had no more copper, vias or empty-pocket area. On RB30 the
  compacted layout halved the board and was thrown away for about 15% more copper.
- The compaction itself only removed empty strips. Parts never slid together.

## Findings that changed the plan

- **Bottom-side parts.** Every gameplay package is through-hole (the only SMD
  packages are developer canaries). A through-hole part on the bottom still puts
  pads through both copper layers, and its body would block the bottom copper the
  router uses most. Real assembly almost never does it. Bottom-side parts need SMD
  packages, which is new gameplay; the owner deferred it.
- **Small boards** route on one copper face under the qualified `THT_SINGLE_FACE`
  envelope (no vias). Gravity compaction with a 40-unit gap made their compacted
  plans route larger or not at all, so they were left alone. On 2026-11-13 the
  owner chose to keep small boards one-sided; simple through-hole products are
  commonly single-sided.

## Commands run

- `verify-current-contracts.ps1 -Suite PcbBoardDensityContractTest`: PASS, 26 cases
- RB30 change (`5f4abcb3`): 28 related suites PASS, then the full native gate PASS
  (215 Java suites plus independent oracles, 52 min), `build.ps1` PASS
- RB56 placement 16: about 50 RB56/identity/session suites PASS after re-pinning
  tests that hard-coded "current = 15"; `build.ps1` PASS (113 s); full native gate
  PASS (215 Java suites plus independent oracles, 52 min). Two earlier gate runs
  stopped on child time budgets while a live RB56 board was simulating in the
  browser; the rerun with the browser idle passed

## Browser (built-in browser, real clicks, production build)

| Screenshot | State |
|---|---|
| `01-rb30-compacted-board.jpg` | RB30 MEDIUM replay seed 0 on the compacted layout |
| `02-rb30-live-reading.jpg` | Meter DC V, red on J1 +12 V, black on R10: 11.998 V |
| `03-rb56-placement16-board.jpg` | RB56 MEDIUM replay seed 0 on placement 16 (since removed; historical) |

## RB56 placement 16 removed (2026-11-13)

The REL-B release check ran the 30 qualified RB56 roots in the compiled Developer
build under the unchanged 90 s / 640-unit / 5 s job limits. On placement 16, 22/30
passed: seven roots spent 266–308 units constructing the board, leaving too little
budget for the 485–520-unit diagnostic proof, and one hit the 5 s unit limit. A
player gets a failed preparation on those seeds. On placement 15, 28/30 passed.
Placement 16 was removed, so RB56 keeps its earlier layout. Details:
[REL-B release decision](../REL-B/release-decision-2026-11-13/README.md).

## Limits

- RB30 compaction is checked by the native physical matrix and a 10-root compiled
  coordinator run (see the REL-B release decision); RB30 has no 30-root corpus.
- Fill is a courtyard measure, not a manufacturing density claim.
