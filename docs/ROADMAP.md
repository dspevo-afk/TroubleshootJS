# TroubleshootJS: Current Roadmap

**Updated:** 2026-10-28 · **Roadmap edition:** 3.0 (complete catalog retained in the linked archive)

## Current product checkpoint

R12 RB56 MEDIUM qualification Phase 1 is accepted for its recorded candidate. The owner chose MEDIUM qualification while
keeping HARD unavailable and stopped before completing the repair; no human repair outcome is
inferred and no repeat play is requested.

R12 Phase 2 remains unstarted. Normal RB56 catalog enablement, normal population, and public
advanced saves remain held. Q100 is unstarted. The current audit-remediation task is a separate
engineering and verification track; it does not advance these product milestones.
Its model/replay changes do not refresh the historical human qualification evidence.

See the [R12 qualification scope](task-evidence/REL-B/rb56-medium-qualification-r1/scope.md), [Phase 1 evidence and limits](task-evidence/REL-B/rb56-medium-qualification-r1/README.md), and the [current task checkpoint](CODEX_TASK_REPORT.md).

## Current direction

CircuitJS remains the electrical source of truth. Generation, simulation adaptation, physical
mapping, instruments, mutation, damage, and scoring retain separate owners. Preserve current
seeded determinism, exact signed-long seed transport, candidate ordering, documented resource
limits, and the separation between normal-player and developer surfaces.

The [implemented ownership map](ARCHITECTURE.md) describes current code responsibilities. The archived Edition 3.0 roadmap
retains the full product direction, architecture decisions, reference-board taxonomy, dependency
rules, risk register, and acceptance definition.

## Execution order and future milestones

The [complete milestone catalog and execution order](archive/2026-10-27-roadmap.md#7-milestone-catalog-and-execution-order) remains accessible, including all future planned milestones and their dependency
cards. The archive is the full roadmap snapshot, not a cancellation of work that remains
planned. The [product direction](archive/2026-10-27-roadmap.md#1-product-direction-and-current-only-policy), [reference-board taxonomy](archive/2026-10-27-roadmap.md#4-functional-taxonomy-and-reference-boards), and [definition of done](archive/2026-10-27-roadmap.md#9-risk-register-non-goals-and-completion) retain their original section anchors.

The current, separate audit-remediation findings and evidence are tracked in [docs/audit-remediation.md](audit-remediation.md). Final
audit gates pass, including the full native matrix, final-source GWT/browser checks,
deterministic Gate B and live preview boundaries. The audit does not change R12
Phase 2 or start Q100.

## Archive note

The complete previous ROADMAP.md contents are preserved in [archive/2026-10-27-roadmap.md](archive/2026-10-27-roadmap.md). Its relative links were
adjusted for the archive location; internal milestone anchors are retained. No evidence files
were removed or untracked.
