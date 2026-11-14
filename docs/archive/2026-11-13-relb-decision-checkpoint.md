# Task report — current checkpoint

**Date:** 2026-11-13 · **Branch:** `master` · **Base:** `f9ea50f3` · **Task:** REL-B release
decision, delegated by the owner. The previous checkpoint (playability rounds 1–2) is in
[archive/2026-11-13-playability-checkpoint.md](archive/2026-11-13-playability-checkpoint.md).

## Outcome

**REL-B parked.** The 30 October release roots, re-run unchanged on the current build, pass
22/30 on RB56 placement 16 and 28/30 on placement 15. Placement 16 (same-day board-realism
compaction) is removed; RB56 is back on placement 15. Details:
[release decision](task-evidence/REL-B/release-decision-2026-11-13/README.md).

## Gates

| Gate | Result |
|---|---|
| 30-root RB56 normal MEDIUM, Developer build, placement 16 | FAIL 22/30 (7 work budget, 1 unit over 5 s) |
| Same 30 roots, placement 15 | FAIL 28/30 (1 work budget, 1 unit over 5 s) |
| RB30 Q30 coordinator route, 10 roots | Cold generation PASS 10/10; warm-cache receipt step FAIL 10/10 (developer route expectation, not the player path) |
| `scripts/build.ps1` Player, placement 15 | PASS |
| 9 focused suites (density, packing identity, role width, trace width, REL-B identity, U04/U06 session, RB56 fault population and physical owner) | PASS |
| Full native gate, browser idle | PASS: 215 Java suites plus independent oracles, 49 min; RB56 matrix 93 s (133 s on placement 16) |
| Browser, Player build, real input | PASS: RB56 root `3437424778755667517` (failed on placement 16) prepares, ticket accepted, board shown |

## Limits

- Two of the 30 roots still fail on placement 15 although they passed in October; cause not
  identified (candidates listed in the decision README). Not investigated further here.
- RB30 is checked on 10 roots only; it has no 30-root corpus.
- Carried over: scope window too short for 555 flasher frequency; empty footprints are not
  click targets; RB30/RB56 preparation takes about a minute; jsdom-based UI tests not run.

## Resources

- Preview server `tsj-preview` on port 8899 (app preview tool). Served build is Player.
- Runner outputs in OS temp (`relb-p16-run-*`, `relb-p15-*`, `rb30-coord-*`), not in Git.
- Untouched, still awaiting the owner: 28 worktrees and 8 stashes from the audit.

## Next action

Owner choice: investigate the post-October RB56 generation regression (reopens REL-B), or
start E08 (jumpers and trace cuts).
