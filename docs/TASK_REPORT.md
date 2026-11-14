# Task report — current checkpoint

**Date:** 2026-11-14 · **Branch:** `master` · **Base:** `a61dfe7f` · **Task:** find and fix the
post-October RB56 generation regression that parked REL-B (owner delegated the lead). The
previous checkpoint (REL-B parked) is in
[archive/2026-11-13-relb-decision-checkpoint.md](archive/2026-11-13-relb-decision-checkpoint.md).

## Outcome

**Regression fixed; the 30 October release roots pass 30/30 again.** A three-root bisect found
two causes. Both are fixed in their owners:

- `34ff05d0`: the guided P07 ordering stream compared all three route orderings, roughly
  doubling RB56 board construction work. The Medium generator now calls
  `PcbLayerRoutingPrototype.Session.stopAtFirstSuccess()` and keeps the first accepted route
  (`FIRST_SUCCESS_GUIDED`). Direct callers keep the comparison.
- `cad0799a`: reduced nonlinear matrix ordering let one RB56 fault proof stall a solver unit
  past 5 s. It is opt-in again (`CirSim.reducedOrderingEnabled`, default off); only the parked
  Q100 pilot used it.

Dependency interpretation moves from v28 to v29. REL-B is unparked but not accepted. Details:
[release decision](task-evidence/REL-B/release-decision-2026-11-13/README.md).

## Gates

| Gate | Result |
|---|---|
| 30-root RB56 normal MEDIUM, Developer build of the final source | PASS 30/30; heaviest 601/640 units, slowest unit 1.82 s |
| `scripts/build.ps1` Player and Developer | PASS |
| Focused suites (A07, D01, P07 ordering/relaxed/fixed-prefix, RB56 model identity, REL-B identity, pocket packing/holdout) | PASS |
| Full native gate, browser idle | PASS: 215 Java suites plus independent oracles, 46 min |
| Browser, Player build, real input | PASS: RB56 MEDIUM exact seed `-3798151323707447735` prepares (~60 s), ticket accepted, board shown |

## Limits

- Heaviest root has 39 units (6 %) of headroom, less than in October (138).
- Golden layouts for guided seeds were re-pinned (listed in the decision README). Boards are
  the same size or up to about 2.4 % larger, because the generator no longer picks the most
  compact of three orderings.
- REL-B acceptance still needs a release check on this build: the Player save/replay/restore gate
  and a repair/retest path.
- `tests/qualification/rb100-layout` readers still pin dependency v28 and the old -1022 hash.
  They read saved Q100 evidence, are not in the gate and were left unchanged.
- Nothing enables reduced matrix ordering now; reopening the parked Q100 pilot must opt in.
- RB30 checked through the full gate only; no RB30 population run this task.
- Carried over: scope window too short for 555 flasher frequency; empty footprints are not
  click targets; RB30/RB56 preparation takes about a minute; jsdom-based UI tests not run.

## Follow-up: RB56 playtest friction

A Haiku subagent playtest of exact seed `-3798151323707447735` did not reach a diagnosis.
The Board view panel now stays open (manual popover) so both probes can be placed without
reopening it. Details and checks:
[playability-pass README](task-evidence/playability-pass/README.md).

## Resources

- Preview server `tsj-preview` on port 8899 (app preview tool), serving the Player build.
- Runner outputs in OS temp (`relb-exp2-*`, `relb-final-*`, `tsj-*.log`), not in Git.
- The bisect worktree (OS temp, detached at `8a2c1244`) was verified clean and removed.
- Untouched, still awaiting the owner: 28 worktrees and 8 stashes from the audit.

## Next action

Run the REL-B release check on this build (save/replay/restore and repair/retest on RB56
MEDIUM) and accept REL-B if it passes; otherwise start E08 (jumpers and trace cuts).
