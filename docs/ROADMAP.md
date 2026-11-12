# TroubleshootJS Roadmap

**Updated:** 2026-11-12 · **Mainline:** `master` · **Catalog:** Edition 3.0 (73 milestones)

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

- **Playable today:** twelve board families. EASY has nine (indicator, protected
  indicator, dual indicator, timing, BJT driver, MOSFET driver, relay output,
  sensor control, 15-part procedural control). MEDIUM has three (two-channel
  controller, 30-part multi-rail control, 56-part isolated AC control).
- **Not available:** HARD and PSYCHOTIC difficulty, 100-part boards, player
  jumpers and trace cuts, logic/MCU content, circuit import.
- **Release state:** REL-A (limited alpha) accepted. REL-B (advanced beta) is
  qualified for MEDIUM on the 56-part board but the public beta is held.

## Milestone status

41 complete · 2 in progress · 30 unstarted.

| Lane | Complete | In progress | Unstarted |
|---|---|---|---|
| Baseline | T49, N00, R00 | | |
| Architecture (A) | A01–A11 | | |
| Physical board (P) | P01–P09 | | |
| Workbench and instruments (U) | U01–U07 | | U08–U12 |
| Electrical content (E) | E01–E06 | | E07–E14 |
| Diagnostics (D) | D01 | | |
| Board-scale qualification (Q) | Q15, Q30, Q60 | Q100 | |
| Releases | REL-A | REL-B | REL-1 |
| Microcontroller | | | MCU-1, MCU-2 |
| Circuit import | | | IMPORT-1 – IMPORT-5 |
| Advanced (X) | | | X01–X09 |

"Complete" means accepted within the scope written on the milestone card. Those
scopes are bounded: fixed seed corpora, the reference PC, and EASY/MEDIUM only.
None of them is an all-seeds or all-hosts guarantee.

### In progress

**REL-B · Advanced desktop beta.** Normal MEDIUM admission on the 56-part board
passed its 30-root qualification, including save/replay/restore and a physical
repair and retest path. Still open: the owner's decision to release the beta,
HARD calibration (deferred by the owner on 2026-10-24), and modest-host
performance evidence (never run).

**Q100 · 100-part procedural board.** Stalled on PCB routing.

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
- Decision needed before more Q100 work: build a large-board routing policy and
  envelope (bigger via and search budgets, placement that leaves routing
  channels), shrink the stretch target, or park Q100. That is an engineering
  and product choice, not something more diagnostics will settle.

## Next up

In preferred order. Each needs its own authorization.

1. **Stabilize what is playable.** Fix defects found by playing the current
   build before adding scope. Open items are listed in [TASK_REPORT.md](TASK_REPORT.md).
2. **Decide Q100.** Choose between a large-board routing policy and envelope, a
   reduced stretch target, or parking Q100.
3. **Close or park REL-B.** Either run the remaining release checks and ship the
   MEDIUM beta, or record it as parked.
4. **Content the alpha is missing.** E08 (jumpers, trace cuts, physical
   restoration) and E07 (timers and oscillators) add the most troubleshooting
   variety for existing board sizes. U09 (two-channel scope) follows E07.
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
