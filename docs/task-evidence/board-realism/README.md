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
| RB56 root 0 (42 parts) | 159×109 mm, 17.8% fill (family mean) | 151×105 mm, 19.4% fill (family mean) |
| RB56 root 17 (53 parts) | 204×113 mm | 207×102 mm |
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
  plans route larger or not at all, so they were left alone. Two-layer small boards
  would need a new envelope; not started.

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
| `03-rb56-placement16-board.jpg` | RB56 MEDIUM replay seed 0 on placement 16 |

## Limits

- RB56 generation is about 35% slower: the 20-board physical matrix took 138 s
  (was 102 s), inside its 600 s budget. Players wait longer for an RB56 board.
- REL-B's 30-root RB56 MEDIUM qualification was **not re-run** for placement 16.
  The full gate's RB56 suites pass, but the release corpus must be re-run before
  the beta ships.
- Fill is a courtyard measure, not a manufacturing density claim.
