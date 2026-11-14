# REL-B release decision · 2026-11-13

**Decision (morning): REL-B parked. Update (evening): the regression is fixed, 30/30 again; REL-B is unparked pending a release check.** The owner delegated the
release call on 2026-11-13. The release card requires a finite normal population
qualification of the advanced family on the current build. The current build fails
that check, so there is nothing honest to ship yet.

## What was run

The 30 roots of the October qualification (profile 3, development 3, holdout 8,
replacement-blind 8, replacement-blind-2 8), unchanged, in order, no retries or
substitutions. Maintained runner `tests/browser/compiled_attribute_acceptance.py`
(headless Edge, its own server and profile) against the compiled Developer build.
Route per root:

`circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyRb56=true&tsjRb56Seed=<root>&tsjRb56Difficulty=true&tsjRb56Medium=true&running=true&tsjRb56Normal=true`

This is the normal MEDIUM player request through the production coordinator, under
the unchanged 90 s / 640-unit / 5 s-per-unit job limits.

## Results

| Build | Pass | Failures |
|---|---|---|
| HEAD `f9ea50f3`, RB56 placement 16 | 22/30 | 7 × work budget (construction 266–308 units, proof needs 485–520 of 640); 1 × one solver unit 5.06 s |
| Same build with RB56 placement 15 | 28/30 | root `-3798151323707447735`: work budget (construction 299 units); root `5954648469308924264`: one solver unit 5.06 s (93,921 trial steps for 1,498 accepted) |
| October qualification (`bd846017`, placement 15) | 30/30 | none; heaviest root used 502 units, slowest unit 1.84 s |

A budget failure is terminal for the job: the player sees a failed preparation for
that seed. It does not fall back to another candidate.

## What changed in the code

- **RB56 placement 16 removed.** It came from the board-realism task the same day and
  caused six of the eight failures. RB56 is back on placement 15
  (`Rb56Placement.CURRENT_VERSION = 15`); RB30 keeps its compaction. Public
  `tsj-alpha/7` RB56 replays now rebuild the placement-15 board. Private session
  links in the `tsj-private-session/16` namespace (which existed only on
  2026-11-13) are rejected as unknown.
- Checks for the revert: Player build PASS; 9 focused suites PASS; full native gate
  PASS (215 suites, 49 min, RB56 physical matrix 93 s against 133 s on placement 16).
- Verified in the Player build with real input: root `3437424778755667517`, which
  failed on placement 16, prepares and opens its ticket and board
  (`01-rb56-root-3437-placement15.jpg`).

## RB30 spot check

RB30 has the same compaction, so 10 roots (0–7, 17, 42) ran through the maintained
Q30 coordinator route. All 10 cold generations PASS, using 247–458 of 640 work
units in about 25–65 s. Every case then stopped at the route's warm phase with
"The warm generation did not issue a cache-reuse receipt", an expectation inside
that measurement-only developer route, not the player path. It is recorded here and
was not investigated.

## Regression found and fixed (same day)

Bisected on three of the 30 roots (one work-budget failure, one solver stall, one
that passed) with the same runner and route. Two separate causes:

| Cause | Commit | Effect | Fix |
|---|---|---|---|
| Guided P07 ordering stream compares all three route orderings | `34ff05d0` (shared placement and layered routing) | RB56 board construction work about doubles (root `-3798…` 120 → 288 units), proof no longer fits in 640 | The Medium generator calls `stopAtFirstSuccess()`: a guided stream ends at its first accepted route (`FIRST_SUCCESS_GUIDED`). Direct callers keep the comparison. |
| Reduced nonlinear matrix ordering on every 16–128 node matrix | `cad0799a` (from the Q100 pilot) | One fault proof on root `5954…` stalls a solver unit at 5.06 s | Ordering is opt-in (`CirSim.reducedOrderingEnabled`, default off). Only the parked Q100 pilot needed it; its runtime checks now enable it on their own fixtures. |

Dependency interpretation moves from v28 to v29 because both change layouts or
solve order. RB56 stays on placement 15.

### Re-run on the final source

Same 30 roots, same order, no retries or substitutions, Developer build of the
committed source:

| Build | Pass | Heaviest root | Slowest unit |
|---|---|---|---|
| Both fixes, final source | **30/30** | 601 of 640 units (root `1325853684799777255`) | 1.82 s |
| Both fixes, before test/epoch edits | 30/30 | 601 units | 1.84 s |
| October qualification (`bd846017`) | 30/30 | 502 units | 1.84 s |

Work per root is identical between the two fixed runs. Headroom on the heaviest
root is 39 units (6 %), tighter than October's.

Checks on the final source:

- Player and Developer builds PASS.
- Full native gate PASS: 215 Java suites plus independent oracles, 46 min, browser idle.
- New contract: the first-success stop yields the saved7 fixture's independently
  recorded order-0 route and does no successor work. The stop rule cannot be set
  after a stream has started searching or on a non-stream request.
- Golden layouts re-pinned where the generator now keeps a different ordering.
  Changed: RB56 legacy seed -1022; saved3–9 and saved12 representatives -1022, 17 and 42;
  saved12 holdouts 5 and 7; pocket holdouts 7 and 14. Each changed case routed
  with `FIRST_SUCCESS_GUIDED`, and each re-run was deterministic. Areas are equal or
  up to about 2.4 % larger. The saved7 root-42 board now equals the order-0 area
  the P07 test records independently (6,524,231).
  `PcbFunctionalGroupingContractTest` and `PcbPocketPackingContractTest` note this
  beside the literals.
- Real browser, Player build, real clicks and typing: MEDIUM, Isolated AC control
  board, exact seed `-3798151323707447735` (the placement-15 work-budget failure)
  prepares in about 60 s, the ticket opens with replay code
  `tsj-alpha/7/MEDIUM/RB56_CONTROL/-3798151323707447735`, and the accepted board is
  shown powered (`02-rb56-root-3798-fixed.jpg`).

## Status after the fix

The population check that parked REL-B passes again. REL-B is **unparked but not
accepted**: in October the beta stayed held after its 30/30 as well. Accepting it
still needs a fresh release check on this build: the Player save, replay and
restore gate and a finite repair and retest path on RB56 MEDIUM.

## Remaining ways forward

1. Run the release check above on this build and accept REL-B if it passes.
2. Let a random (not replayed) launch move to the next candidate seed when a job
   runs out of budget, so players do not see a failed preparation.
3. Any change to the 90 s / 640 / 5 s limits needs an explicit owner decision.

Unchanged limits: HARD and PSYCHOTIC stay unavailable; modest-host performance was
never measured; no CI.
