# Task 30 — Generic Functional Challenge Completion Contract

Status: complete

## Milestone and acceptance

Implemented the first eligible roadmap milestone, Task 30, for the existing
`LED_INDICATOR`, `DIODE_PROTECTED_INDICATOR`, and
`PARALLEL_DUAL_INDICATOR` families.

The bounded acceptance criteria were:

- provide one reusable healthy/faulted/repaired behavior-contract boundary for
  generated boards and challenge definitions;
- keep the existing family-specific CircuitJS validators behind that boundary;
- make common challenge completion depend on a solver-backed functional
  predicate, not on a replacement gesture or an original-part ID;
- remove family repair checks whose only purpose was requiring the original
  faulted object to be replaced, while retaining real electrical and physical
  validity checks;
- preserve hidden fault metadata, stable board/component/pad/net identity,
  graph ownership, power and measurement safety, and the existing lifecycle;
- prove healthy, faulted, nonfunctional/reversed-repair, and repaired paths
  through the common contract for the existing simple, diode, and parallel
  families; and
- do not add scoring, new fault families, new circuit families, or adjacent
  roadmap work.

## Result

`GeneratedChallengeBehaviorContract` is now the generic boundary with
`verifyHealthy`, `verifyFaulted`, and `isFunctionallyRepaired` operations.
`GeneratedChallengeBehaviorAdapter` preserves the current family validators as
the implementation behind that boundary. `GeneratedBoardInstance` owns the
contract, `GeneratedChallengeDefinition` references the same contract, and
`GeneratedChallengeController` rejects mismatched ownership before using it
for lifecycle verification and completion.

Healthy verification now goes through the contract, faulted verification stays
solver-backed, and repaired completion is accepted only when the family’s
functional electrical predicate is true under the existing power and
measurement safety rules. The common controller no longer checks for
`R1`, `R1_ORIGINAL`, or a catalog-part identity. LED, diode, and parallel repair
validators no longer reject a repair solely because the original part is not
the installed object; an original faulted part still fails naturally through
its electrical state, while an alternate valid repair can complete where its
predicate permits it.

The parallel developer verifier now proves both the solver-backed functional
predicate and the common controller’s `COMPLETED` lifecycle state, closing the
generic completion-path gap found during review.

The browser-evidence paragraphs previously requested by the user remain
restored in `AGENTS.md` and `.codex/agents/coder.toml`; those files were not
changed in this milestone.

## Architecture decisions

- The behavior contract is a small lifecycle boundary, not a second source of
  electrical truth.
- Family validators continue to read CircuitJS-backed board state and remain
  responsible for family-specific electrical behavior.
- Board and definition contract identity is checked explicitly so a challenge
  cannot silently verify against a different family implementation.
- Functional completion remains separate from diagnosis and physical mutation;
  a board can be diagnosed or physically modified without being complete until
  restored behavior is verified.
- No scoring, new fault engine, new topology, or new player hint was added.

## Delegation and review

- Coder subagent `Halley` (`gpt-5.6-luna`, XHIGH, workspace-write) implemented
  the bounded candidate, ran the JDK 8 build and targeted routes, and reported
  the changed files and validation evidence.
- Reviewer subagent `Mendel` (`gpt-5.6-luna`, XHIGH, read-only) initially
  returned `FAIL` with two valid findings: the parallel developer verifier did
  not assert common-controller completion, and `docs/ARCHITECTURE.md` still
  described the removed separate-validator wiring.
- The coder made one narrow correction pass. The reviewer independently
  reran the review and returned exactly `PASS`.
- Luna MAX performed one independent final-review round of the actual diff and
  implementation and returned `FINAL PASS`.
- Sol escalation was not required.

## Files changed

- `src/com/lushprojects/circuitjs1/client/GeneratedChallengeBehaviorContract.java`
  — generic healthy/faulted/repaired behavior boundary.
- `src/com/lushprojects/circuitjs1/client/GeneratedChallengeBehaviorAdapter.java`
  — adapter preserving existing family validators.
- `src/com/lushprojects/circuitjs1/client/GeneratedBoardInstance.java` —
  board-owned behavior contract.
- `src/com/lushprojects/circuitjs1/client/GeneratedChallengeDefinition.java` —
  one contract instead of separate family validator fields.
- `src/com/lushprojects/circuitjs1/client/GeneratedChallengeController.java` —
  contract identity, lifecycle, and functional completion path.
- `src/com/lushprojects/circuitjs1/client/GeneratedBoardVerifier.java` —
  healthy verification through the contract.
- `src/com/lushprojects/circuitjs1/client/LedIndicatorGenerator.java` —
  shared LED behavior adapter wiring.
- `src/com/lushprojects/circuitjs1/client/DiodeProtectedIndicatorGenerator.java`
  — shared diode behavior adapter wiring.
- `src/com/lushprojects/circuitjs1/client/ParallelDualIndicatorGenerator.java`
  — shared parallel behavior adapter wiring.
- `src/com/lushprojects/circuitjs1/client/LedIndicatorRepairValidator.java` —
  removed original-only completion rejection.
- `src/com/lushprojects/circuitjs1/client/DiodeProtectedIndicatorRepairValidator.java`
  — removed original-only completion rejection.
- `src/com/lushprojects/circuitjs1/client/ParallelDualIndicatorRepairValidator.java`
  — removed original-only completion rejection.
- `src/com/lushprojects/circuitjs1/client/ChallengeDeveloperVerifier.java` —
  common contract route assertions.
- `src/com/lushprojects/circuitjs1/client/DiodeFamilyDeveloperVerifier.java`
  — diode contract route assertions.
- `src/com/lushprojects/circuitjs1/client/ParallelDualIndicatorDeveloperVerifier.java`
  — common completion assertion for the parallel family.
- `src/com/lushprojects/circuitjs1/client/ReplacementDeveloperVerifier.java`
  — replacement lifecycle assertions through the generic path.
- `docs/ARCHITECTURE.md` — current contract and adapter boundary.
- `docs/ROADMAP.md` — Task 30 marked complete and Task 31 identified as next.
- `docs/task-evidence/task-30/` — four curated production-browser screenshots.
- `docs/CODEX_TASK_REPORT.md` — this handoff evidence.

## Validation evidence

Build and static checks:

- `./scripts/build.ps1 -JavaHome ./.tools/jdk8-download/jdk8u502-b07` — PASS;
  all five GWT permutations compiled and linked.
- PowerShell parser check for `scripts/verify-browser.ps1` — PASS.
- `git diff --check` — PASS; only existing LF/CRLF conversion warnings were
  reported.

Production-browser player flows against the final build:

- `./scripts/verify-browser.ps1 -NormalPlayer -PlayerSeed 3` — PASS;
  original removal, replacement, and solver-backed repair completion.
- `./scripts/verify-browser.ps1 -DiodeNormalPlayer -PlayerSeed 3` — PASS;
  diode functional repair completion.
- `./scripts/verify-browser.ps1 -ParallelNormalPlayer` — PASS on the final
  run; supply measurement, power safety, replacement, and common completion.
- `./scripts/verify-browser.ps1 -LedNormalPlayer -PlayerSeed 3
  -EvidenceDirectory docs/task-evidence/task-30` — PASS; reversed physical
  installation remained nonfunctional before the valid repair, then the
  repaired board completed functionally.

Seeded developer/browser matrices:

- `./scripts/verify-browser.ps1 -Seeds 0,2,3` — PASS, all 15 LED routes.
- `./scripts/verify-browser.ps1 -Diode -Seeds 0,2,3` — PASS, all 3 diode
  routes.
- `./scripts/verify-browser.ps1 -Parallel -Seeds 0,2,3` — PASS, all 3
  parallel routes.
- `./scripts/verify-browser.ps1 -LedParts -Seeds 0,2,3` — PASS, all 3
  physical-part routes.

The Codex built-in browser was used against the final production preview to
inspect the rendered parallel and LED boards and the player-facing repair
states. The four evidence images were pixel-inspected and are nonblank,
legible, and representative of the completion contract:

- [`initial-board.png`](task-evidence/task-30/initial-board.png) — initial
  LED challenge with the complaint and unmodified board.
- [`led-selected.png`](task-evidence/task-30/led-selected.png) — selected
  physical LED with removable-component controls visible.
- [`led-removed-parts-tray.png`](task-evidence/task-30/led-removed-parts-tray.png)
  — powered-off board after removal with the loose original LED in the tray.
- [`repaired-board.png`](task-evidence/task-30/repaired-board.png) — powered
  repaired board with the solver-backed completion message and loose-part
  identities preserved.

## Remaining bounded limitations

The adapter is intentionally package-private and currently wraps the three
existing family validator interfaces; it is not a new generic circuit
simulation engine. Alternate repairs are accepted only when the family’s real
electrical predicate permits them. Fault selection remains family-specific and
is intentionally deferred to Task 31. This milestone adds no scoring, stress,
new fault types, new families, jumpers, or trace cutting.

## Roadmap handoff

Task 30 is complete, validated, reviewed, and committed. The next eligible
milestone is Task 31 — Fault Engine v1. It is identified only and was not
started in this task/run.

Commit message: `Task 30: unify functional challenge completion contract`
