# TroubleshootJS Roadmap

**Updated:** 2026-11-13 · **Mainline:** `master` · **Catalog:** Edition 3.0 (73 milestones)

This page is the status summary. It stays short. The full milestone cards,
product direction, reference-board taxonomy and definition of done live in the
[Edition 3.0 catalog](archive/2026-10-27-roadmap.md#7-milestone-catalog-and-execution-order).
Implemented ownership is described in [ARCHITECTURE.md](ARCHITECTURE.md). The
current working checkpoint is in [TASK_REPORT.md](TASK_REPORT.md).

## Where we are

TroubleshootJS is a playable desktop alpha. A player picks a difficulty and board
family, receives a customer complaint, probes a procedurally placed and routed PCB
with a solver-backed multimeter or scope, replaces parts from the shop, and runs a
customer retest. Sessions can be saved and restored.

- **Playable today:** thirteen board families. EASY has ten (indicator, protected
  indicator, dual indicator, RC timing, 555 timer, BJT driver, MOSFET driver,
  relay output, sensor control, 15-part procedural control). MEDIUM has three (two-channel
  controller, 30-part multi-rail control, 56-part isolated AC control).
- **Not available:** HARD and PSYCHOTIC difficulty, 100-part boards, player
  jumpers and trace cuts, logic/MCU content, circuit import.
- **Release state:** REL-A (limited alpha) accepted. REL-B (advanced beta) is
  unparked but not accepted: its 30 release boards pass again (2026-11-13); a
  release check on the current build is still needed.

## Milestone status

42 complete · 1 in progress · 1 parked · 29 unstarted.

| Lane | Complete | In progress / parked | Unstarted |
|---|---|---|---|
| Baseline | T49, N00, R00 | | |
| Architecture (A) | A01–A11 | | |
| Physical board (P) | P01–P09 | | |
| Workbench and instruments (U) | U01–U07 | | U08–U12 |
| Electrical content (E) | E01–E07 | | E08–E14 |
| Diagnostics (D) | D01 | | |
| Board-scale qualification (Q) | Q15, Q30, Q60 | Q100 (parked) | |
| Releases | REL-A | REL-B (in progress) | REL-1 |
| Microcontroller | | | MCU-1, MCU-2 |
| Circuit import | | | IMPORT-1 – IMPORT-5 |
| Advanced (X) | | | X01–X09 |

"Complete" means accepted within the scope written on the milestone card. Those
scopes are bounded: fixed seed corpora, the reference PC, and EASY/MEDIUM only.
None of them is an all-seeds or all-hosts guarantee.

### In progress and parked

**REL-B · Advanced desktop beta.** **In progress (unparked 2026-11-13).** Parked in
the morning when the 30-root RB56 MEDIUM qualification from October passed only
28/30 (22/30 with the same-day placement 16, which was removed). The same day the
regression was traced to two commits and fixed: the Medium generator stops a
guided route-ordering stream at its first accepted route, and reduced matrix
ordering is opt-in again. The 30 roots now pass 30/30 on the committed source.
Still needed to accept: a release check on this build (Player save, replay and
restore; a repair and retest path). HARD stays unavailable and modest-host
performance was never measured. Details:
[release decision](task-evidence/REL-B/release-decision-2026-11-13/README.md).

**Q100 · 100-part procedural board.** **Parked on 2026-11-12 by the project lead.** Reopen it only
as a scoped task to build a large-board routing policy and envelope, not as more diagnostics.

- Done: a private electrical pilot. Two authored 100-package circuits solve in
  CircuitJS and pass a 2 × 31 fault and repair reference matrix. A reduced-matrix
  ordering fix made the solver fast enough for them.
- Blocked: no 100-part layout has ever routed. The authored placement fails with
  `NO_PATH` or the branch search limit in the existing two-layer router
  (`FULLER_TWO_LAYER`, the policy medium boards use) under its current via,
  hole and search budgets. There is also no supported physical envelope for a
  board this size; the experiment ran on a private un-enveloped path.
- What happened next: the experiment's own plan froze the router, placement and
  budgets, so nothing was allowed to be fixed. From 2026-11-02 to 2026-11-12
  roughly 270 commits went into routing diagnostics and then into verification
  tooling for those diagnostics. None of it produced a routed board or changed
  what a player sees. That work is preserved in the
  [Q100 narrative](archive/2026-11-12-roadmap-q100-narrative.md) and is not
  being continued in that form.
- Decision: parked. Fifty-six parts is already a full MEDIUM board, and players get more
  from new troubleshooting content than from bigger boards. Restarting Q100 means
  building a large-board routing policy and envelope: bigger via and search
  budgets, placement that leaves routing channels, and parts on both board
  sides. Bottom-side parts need surface-mount packages, because through-hole
  pads block both copper layers; the owner deferred that on 2026-11-13.

## Next up

In preferred order. Each needs its own authorization.

1. **Stabilize what is playable (in progress, 2026-11-13).** Fix defects found
   by playing the current build before adding scope. Two rounds done across all
   board sizes: input state, probe markers, tray install, labels and numbering,
   toolbar fit, meter OFF key; see
   [playability pass](task-evidence/playability-pass/README.md). Open items are
   listed in [TASK_REPORT.md](TASK_REPORT.md).
2. **Board realism** is done in its agreed scope: RB30 compaction; RB56 compaction
   was removed because it broke generation budgets; small boards stay one-sided
   (owner, 2026-11-13); bottom-side parts wait for SMD packages; see
   [board-realism](task-evidence/board-realism/README.md).
3. **REL-B release check.** The generation regression is fixed and the 30
   release boards pass. Accepting the beta needs a fresh save/replay/restore and
   repair/retest check on this build; see the
   [release decision](task-evidence/REL-B/release-decision-2026-11-13/README.md).
4. **Content the alpha is missing.** E07 (555 timer board) is done. E08
   (jumpers, trace cuts, physical restoration) is next; it changes the mutation
   and save formats. U09 (two-channel scope) can now build on the E07 timer
   boards.
5. **Later lanes** enter through content that consumes them: E09–E14, U10–U12,
   MCU-1/2, IMPORT-1 – IMPORT-5, the X series, then REL-1.

## History

- [Edition 3.0 full roadmap and milestone cards](archive/2026-10-27-roadmap.md)
- [Q100 investigation narrative, 2026-10-27 to 2026-11-12](archive/2026-11-12-roadmap-q100-narrative.md)
- [Task reports through 2026-11-12](archive/2026-11-12-task-report.md) and
  [through 2026-10-27](archive/2026-10-27-task-report.md)
- Per-milestone evidence summaries and screenshots: [task-evidence/](task-evidence/).
  Raw logs, JSON dumps and source snapshots were removed from the tree on
  2026-11-12 and remain available at git tag `pre-cleanup-2026-11-12`.
