# Post-Task-43 Roadmap Redesign

## Status

The documentation and planning work is complete in the working tree. No
implementation milestone began. Task 44, Gate A, Gate B, and Task 43P remain
unstarted; the exact next gate is Post-Task-43 Gate A.

## Baseline and branch

- Final accepted Task 43 SHA used: `8245c79990647f6c40f53bc1dd9330ec2ccd22b4`.
- Accepted implementation source branch: `codex/task43-recovery-integration`.
- Live remote confirmation: `origin/codex/task43-recovery-integration` points
  to the same SHA.
- Roadmap redesign branch: `codex/post43-roadmap-redesign`, based directly on
  that accepted SHA.
- Package geometry-contract version: 3.

## Files changed

- `docs/ROADMAP.md`
- `docs/CODEX_TASK_REPORT.md`
- `docs/task-evidence/task-43/recovery-history.md`

No production Java, scripts, verifiers, `AGENTS.md`, or branch-cleanup files
were changed.

## Roadmap changes

### Task 43 compression

The active Task 43 entry is now a concise accepted completion record covering
package-owned geometry, explicit variants, geometry versioning, package-backed
footprints, exact pad/probe geometry, compaction/containment, physical
same-net validation, installed/lifted/loose interaction, RC/NPN/NMOS fixed
layouts, integrated regression, forced-negative process integrity, and the
final accepted SHA. Detailed recovery chronology was moved to
`docs/task-evidence/task-43/recovery-history.md`.

### Post-Task-43 gates

- Gate A is the single immediate next gate for mainline consolidation and
  evidence preservation.
- Gate B follows Gate A and establishes isolated ports, profiles, temporary
  state, evidence paths, failure classes, CI checks, and mainline protection.
- Task 43P follows Gate B and reproduces or falsifies historical lifecycle and
  verification-integrity findings against the final SHA. It creates correction
  milestones only for proven open blockers.
- The Owner Review Gate follows Task 43P and requires explicit owner approval
  before Task 44. Task 44 must not begin automatically.
- The Composition Entry Gate is the hard runtime-integrity wall before Task 47.

### Tasks 44–48

Task 44 remains an immutable block descriptor/stable namespace contract and is
blocked by Task 43P plus explicit owner review. It does not implement CircuitJS
assembly, PCB generation, mutation, runtime composition, or same-owner
snapshotting. Task 45 remains typed electrical-domain/port preflight, with
CircuitJS as final electrical truth. Task 46 remains the versioned descriptor,
named-seed, and typed-constraint contract; it may define immutable replay
contracts when Task 43P allows, but it does not authorize Task 47. Task 47 is
blocked by Tasks 44–46 and the Composition Entry Gate and now carries explicit
owner, settlement, stale-callback, exception, proof-boundary, and no-duplicate-
architecture requirements. Task 48 remains the first small real composed proof
and now requires independent physical correspondence, visible player-control
proof, deterministic replay, isolation evidence, and monolithic leaf fixtures.

### Preserved sequence and conditional work

Tasks 49–65 remain substantially in their prior order; Task 52 remains after
the first composed proof. Tasks 54–56 and 56(A) remain evidence-gated. Task 66
now distinguishes hard dependencies from preferred sequence: Task 65 is not a
universal architectural prerequisite and becomes hard only if continuous LED
intensity is required by the relay challenge. Tasks 71–73 and later damage,
thermal, history, scoring, persistence, sharing, and multi-fault work remain in
their existing long-term positions.

Task 80 now admits HARD by an explicit advertised capability bundle rather than
requiring every Task 66–78 feature. The bundle must be defined at planning time
and include legitimate multi-block reasoning, plausible owners, domains,
purposeful support, isolation/repair reasoning, readable physical complexity,
solver-backed retest, deterministic replay, and a legal diagnostic plan. Task
80(A) validates only the advertised beta surface and does not automatically
require PSYCHOTIC, multiple faults, economy, mobile, every future block, or
every future instrument.

### Cross-cutting runtime composition rules

The roadmap now records future entry/acceptance requirements for visible-player
proof, independent physical correspondence, verifier independence, mutation
settlement/rollback, exception-safe measurement cleanup, stale async identity,
fresh-candidate versus same-owner proof boundaries, distinct reset semantics,
causal fault/damage separation, isolated browser automation, and CircuitJS
truth/stable identity.

## History preservation

The completed Task 1–43 ledger remains intact. The historical lifecycle and
verification-integrity audits are explicitly treated as older-baseline
findings/hypotheses. The recovery file preserves the 43R chronology, including
failed attempts, corrective returns, fixed-layout evidence, the preserved
43R-8B stash disposition, and final 43R-8 acceptance facts.

## Validation performed

- Verified the final integrated Task 43 report, commit, diff, and accepted
  review evidence before editing.
- Verified live remote presence with `git ls-remote`:
  `8245c79990647f6c40f53bc1dd9330ec2ccd22b4`.
- Verified the redesign branch starts at that exact SHA.
- Inspected all listed worktrees; each was clean before roadmap editing.
- Inspected the sole stash. Its production verifier files match accepted HEAD;
  its older script candidate is superseded by later accepted integration
  commits and remains preserved as historical evidence.
- Read `AGENTS.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, the prior task
  report, current research, and historical lifecycle/verification/recovery
  reports from their repository branches.
- Production Java/GWT build was **not run**. This is a documentation-only task,
  and the task instructions explicitly say not to spend time running a full
  production build when no production source changes occur.
- `git diff --check` passed; Git emitted only the expected LF-to-CRLF working-
  copy warnings.
- Markdown major-heading uniqueness passed; repeated generic `### Goal`,
  `### Requirements`, and completion headings are intentional task templates.
- Task-heading continuity passed for Tasks 28–89, including 34(A), 35(A), and
  80(A), with no renumbering.
- Local Markdown link/reference validation passed, including the new recovery
  history file.
- Stale-baseline searches passed: no Task 38/92df240 current-baseline claim,
  obsolete 43R-8/43R-8B next-state claim, unblocked Task 44 claim, universal
  Task 65→66 hard dependency, or all-features Task 80 claim remains.
- Status consistency passed: one active `[>]` gate, one Immediate Next
  Milestone heading, one Task 43 heading, and no duplicate major headings.
- A focused roadmap acceptance script passed for the final SHA, geometry
  version, Gate A/B/43P/Owner/Composition entries, Task 44/47/48 blockers,
  Task 66 preferred-sequence language, Task 80 capability bundle, Task 80(A)
  scope, recovery history, report content, and file scope.
- Intended-scope inspection passed: only the two existing roadmap/report files
  and the new Task 43 recovery-history file are changed.

## Known uncertainties and intentionally deferred work

- Gate A mainline consolidation has not begun; no branches or stashes were
  cleaned up by this task.
- Gate B verifier isolation and branch-protection configuration remain future
  work; owner-only GitHub settings may require an external action.
- Task 43P has not yet determined which historical findings remain open on the
  final SHA. No correction milestone was pre-invented.
- The current preserved stash is historical evidence, not a current accepted
  implementation baseline.
- No post-Task-43 production capability is claimed complete.

## Completion handoff

- Exact next gate: **Post-Task-43 Gate A — Mainline Consolidation and Evidence
  Preservation**.
- Task 44 is not next and remains unstarted.
- No implementation milestone began.
- The roadmap redesign commit SHA and verified push result are established after
  the final commit and publication; this report is intentionally written before
  that self-referential commit SHA exists.
- Post-push Gmail notification is attempted only after verified publication and
  only if the connected capability is available; its result must be reported
  truthfully.
