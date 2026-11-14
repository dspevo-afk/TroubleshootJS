# REL-B release decision · 2026-11-13

**Decision: REL-B is parked. The MEDIUM beta does not ship.** The owner delegated the
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

## Still open after the revert

Two of 30 roots fail on placement 15 although they passed in October. Board
construction for the heavier RB56 variant now costs roughly twice the work it did
then (192–299 units against about 95–125), and one fault proof hits a solver step
stall. The cause is not identified. Commits since the October qualification that
touch this path include `cad0799a` (reduced nonlinear matrix ordering, from the
Q100 pilot), `8a2c1244` (E06 state committed only after solver acceptance) and
shared routing changes from the Q100 period.

## Ways to reopen REL-B

1. Find and fix the post-October regression, then re-run these 30 roots. Accept only
   on 30/30.
2. Separately, let a random (not replayed) launch move to the next candidate seed
   when a job runs out of budget, so players do not see a failed preparation. This
   helps players but does not qualify REL-B.
3. Any change to the 90 s / 640 / 5 s limits needs an explicit owner decision; none
   was made here.

Unchanged limits: HARD and PSYCHOTIC stay unavailable; modest-host performance was
never measured; no CI.
