# Task report — current checkpoint

**Date:** 2026-11-12 · **Branch:** `master` · **Task:** project audit, gate repair and repository cleanup
(new project lead, after about three weeks of unsupervised Codex work). Earlier checkpoints are in
[archive/](archive/).

## What was wrong

1. **The full native gate had been red since 2026-10-31.** Commit `34ff05d0` ("Correct shared PCB
   placement and layered routing") deliberately moved generation to dependency epoch v26. That
   changed the routed geometry of every saved RB56 placement version. Codex re-pinned one golden
   test (`PcbCompactnessContractTest`) and then ran only focused gates, so it never saw the other
   twelve stale suites:
   - saved role-width v12 representatives and holdouts
   - saved v3, v4, v5, v6, v7, v8 and v9 geometry
   - pocket packing representatives and holdouts
   - packing-probe receipt (the Statistics canonical gained two fields)
2. **`PcbRoleWidthSearchDiagnosticTest` crashed** with a `ClassCastException`. It read private
   router fields by reflection, and a later router refactor turned one of those fields into a
   different type. It was a measurement-only diagnostic and checked nothing about the product.
3. **Scope drift on Q100.** Roughly 270 commits from 2026-11-02 to 2026-11-12 went into routing
   diagnostics and then into verification tooling for those diagnostics. No 100-part board ever
   routed. See [ROADMAP.md](ROADMAP.md).
4. **Repository bloat and sprawl:**
   - about 18,500 tracked raw evidence files (JSON dumps, logs, source copies, hash manifests)
   - a 1.5 MB task report and a 180 KB roadmap narrative
   - 54 leftover worktrees and 8 stashes
   - the Desktop checkout was 499 commits behind on a stale branch
   - GitHub `master` was 286 commits behind; the work existed only on
     `origin/codex/q100-physical-feasibility`

## What was done

- **Gate repair.** A scratch harness measured current geometry for every affected seed and
  version. All 72 boards route and pass geometry, routing, clearance, two-layer and admission
  validation. Every area is equal to or smaller than its earlier accepted value. Exact geometry
  literals were re-pinned to the current output. The earlier accepted area and void arrays stay in
  place as regression bounds. Unreachable version-10/11 branches in `measure()` were removed.
  The packing-probe expected canonical string gained `routeEligiblePlacementAttempts=[]` and
  `routeEligibilitySkips=[]`.
- **Retired** `PcbRoleWidthSearchDiagnosticTest` from `tests/contracts` and the gate registry.
- **Cleanup.**
  - Removed tracked raw evidence. Milestone README files and screenshots remain.
  - Moved the old roadmap, task report and two checkpoints to `docs/archive/`.
  - Removed the Codex inbox task files.
  - Wrote a short [ROADMAP.md](ROADMAP.md).
  - Added scope-discipline rules to `AGENTS.md`.
  - Ignored `.worktrees/`, `.tmp/` and Python caches.
  - Everything removed is still reachable at tag `pre-cleanup-2026-11-12`.
- **Worktrees.** Removed 26 merged worktrees that had no uncommitted changes, plus their merged
  local branches.
- **Player check.** Played a real EASY board in the built-in browser preview: complaint,
  measurement, removing R1, buying and installing a 680 Ω part, and a passing customer retest.
  The four Quick Play candidate seeds all replay. One earlier "Board preparation failed" message
  came from the browser pane being hidden, which pauses the foreground generation clock. Six
  launches after that succeeded.

## Gates

| Check | Result |
|---|---|
| GWT production build, `scripts/build.ps1`, JDK 8 | PASS at `201b993f`. No production source changed afterwards. |
| Native contracts, `scripts/verify-current-contracts.ps1`, all suites | PASS: 213 Java suites, exit 0, 2,921 s, after the repair. Before it, 13 suites failed. |
| Browser player flow (EASY repair and retest) | PASS by manual visible input. Screenshots were not kept. |

## Open items, not done here

- **28 worktrees still hold uncommitted edits.** They are mostly Q100 diagnostic scratch under
  `%TEMP%`, `C:\tmp` and `~\.codex\worktrees`, and every branch is already merged. The **8 stashes**
  are from 2026-08-22 to 2026-11-01. Deleting either needs the owner's go-ahead.
- **Four unmerged local branches:** `archive/desktop-wip-20260923` (the stale Desktop work-in-progress
  that was archived), `codex/state-lifecycle-integrity-audit`, `codex/task43-recovery-assessment`
  and `codex/verification-integrity-audit`. They are left untouched.
- **The Codex desktop app is still running** (PID 16044, started 2026-11-11). Stop it before
  starting new work so it cannot resume old tasks.
- **Canvas bug.** When the canvas is zero-size (for example a hidden pane), `drawImage` calls throw
  uncaught errors (`CirSim.java` near lines 1977 and 2157, `PcbWorkbenchController.java:370`).
  This is a small production guard plus a build.
- **Old evidence.** `docs/task-evidence/` still holds about 145 MB of historical README files and
  screenshots, more than the new per-milestone budget allows. It was left as is.
- **Saved sessions.** A saved session that recorded an older placement version now regenerates
  the corrected v26 geometry rather than its original layout. That follows from the epoch change
  in `34ff05d0`, not from this task.

## Next action

The owner decides on Q100 (large-board routing policy, a smaller target, or parking it) and on
REL-B. See [ROADMAP.md](ROADMAP.md#next-up).
