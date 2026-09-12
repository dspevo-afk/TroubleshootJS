# AGENTS.md — TroubleshootJS

Repository guidance · updated 2026-09-12. Current user instructions take precedence
within repository guidance. Keep task status in the roadmap/report, not this file.

## Workflow and scope

- Start with `docs/ROADMAP.md` (sequence and acceptance), `docs/ARCHITECTURE.md`
  (implemented ownership), and the current checkpoint at the top of
  `docs/CODEX_TASK_REPORT.md`. Read relevant code, callers, tests, recent history,
  and applicable nested instructions; do not load the entire historical log.
- Confirm working directory, branch, HEAD, tracked changes and pre-existing
  untracked files before editing. Preserve unrelated work.
- Work directly by default. **Subagents are optional for every activity,
  including implementation, testing and independent review.** Use them only when
  useful; missing delegation or a separate reviewer is not a completion blocker.
  Historical instructions and task templates do not restore mandatory delegation.
- If delegating, prefer Luna with MAX reasoning and normal/default speed. Give a
  compact assignment, explicit file ownership and required evidence. Workers are
  leaves, must preserve others' edits, and do not stage, commit, push or notify.
  Keep one writer per file; the root integrates and owns acceptance/publication.
  Respect the runtime thread limit; never change global settings to bypass it.
- Define a small, coherent scope and its validation before editing. An authorized
  milestone sequence includes its necessary fixes and eligible substeps. Respect
  dependencies, finish that sequence, and stop before unrequested milestones.
- Fix the cause through existing owners/adapters. Avoid unrelated upgrades,
  migrations, formatting churn, duplicate state or cosmetic decomposition.
- Historical development versions are not a compatibility promise. When a task
  owns their callers, retire obsolete APIs/formats/resolvers and migrate current
  consumers. Preserve useful independent test oracles. Reject incompatible
  artifacts before live mutation; cross-version support needs explicit scope.

## Product invariants

TroubleshootJS is a PCB troubleshooting simulator: complaint, examination,
measurement, isolation, repair and functional retest. Preserve electrical reasoning
and the unfamiliar-board experience.

- **CircuitJS is electrical truth.** Use the active solver graph for behavior and
  readings. No fake measurements, scenario-specific physics or replacement solver.
  Any model approximation must be explicit, bounded and supported by its contract.
- Keep generation, simulation adaptation, faults, PCB geometry/routing/rendering,
  instruments, mutations, damage and scoring responsibilities separate. Maintain
  one authoritative graph with stable component, terminal, net, pad, copper and
  probe identities through edits, reset and rerendering.
- Every conductive visual target must map to its correct electrical endpoint.
  Geometry may not silently short nets, disconnect logical connections or invent
  connectivity. Preserve original versus current physical/electrical state.
- Generate constrained functional circuits: prove healthy behavior, inject a
  compatible fault, prove a meaningful symptom, then validate physical mapping and
  diagnostic solvability. Supporting circuitry is not automatically faulty.
- Preserve current seeded determinism, exact signed-long seed transport, canonical
  candidate order and independent random concerns. Identify interpretation with
  the current schema/model epoch; use explicit representative and regression seeds.
- Removal, lifting, replacement, jumpers, cuts and secondary failures must change
  the live electrical graph. Preserve documented undo/reset and the consequences
  of wrong repairs. Damage, limiting and waveforms need electrical causes.
- Instruments must respect power state, parallel paths, isolation and residual
  energy. Restore temporary sources, graph elements, listeners and instrument
  state on exit, error, cancellation, switching, mutation and reset. Stale work
  must not alter a successor graph or repower a board the player turned off.
- In probe modes, left click places red and right click places black. Selecting
  the active mode again exits it. Suppress normal context behavior only as needed.
- Normal-player UI must not reveal hidden faults, answer keys, debug flags,
  netlists or the neat schematic. Developer views are separate. Completion checks
  restored customer behavior under relevant inputs, including valid alternative
  repairs, rather than the originally intended repair click.

## Validation and evidence

- Run focused checks during development and the applicable final gates. Java/GWT
  production changes require the actual final-source JDK8/GWT build through
  `scripts/build.ps1`; use the maintained native/verifier commands for the affected
  contracts. One expensive full build/test matrix per worktree at a time.
- Validate changed electrical, identity, generation, mutation, lifecycle and
  privacy boundaries with explicit fixtures and independent expectations. Do not
  weaken assertions, bless goldens blindly, omit required cases or suppress errors.
- For changed OS/process/listener/transport boundaries, test the real selected
  implementation early with focused positive and negative canaries. Mocks do not
  prove the host path. Do not rerun an unchanged full command after two identical
  environment failures; diagnose the cause or record the missing prerequisite.
- For visible player-flow changes, use the actual production preview and real
  visible input, preferably built-in Browser. Check meaningful initial, changed,
  unrepaired and repaired states. Inspect two to five useful screenshots and keep
  them in `docs/task-evidence/<milestone>/`. Injected controller calls are not
  player-input evidence; manual interaction does not certify a CDP wrapper.
- Record commands/interactions, candidate identity, results and limitations.
  Distinguish PASS, FAIL, BLOCKED, NOT RUN and NOT APPLICABLE. Timeouts, missing
  proof, failed cleanup and exit code 2 are not PASS. Report phase/operation and
  elapsed time separately from cleanup. Preserve failures and existing limits;
  any intentional budget/oracle change needs explicit scope and justified evidence.
- Reuse passing evidence only after auditing that all consumed inputs are
  unchanged; document that boundary. Task-required fresh gates take precedence.
  Documentation-only changes do not require rebuilding unchanged production code.
- Root reviews the integrated diff and resolves blockers. Independent agent review
  is optional. If used, report its actual scope/result and do not reuse a pre-fix
  PASS for changed behavior. Record nonblocking follow-ups without expanding the
  finish line into unrelated cleanup or hypothetical perfection.

## Repository, data and resource safety

- Never discard unrelated edits or untracked data. No broad clean/reset, force
  push, history rewriting, branch deletion or mass cleanup without exact user
  authorization. Stage intended paths only; inspect staged and unstaged changes.
- Do not commit credentials, private data, personal absolute paths or uncontrolled
  logs. Use sanitized fixtures. Do not weaken authentication, sandboxing or global
  runtime settings to turn a failed check green.
- Use unique task-owned OS-temp directories outside the repo for substantial
  scratch/copies. Exclude `.git`, caches, nested scratch, build output and private
  data. Before recursive cleanup verify exact ownership, resolved containment and
  symlink/reparse boundaries. Normal documented build outputs may stay in place.
- Record persistent process ownership at launch: handle or PID plus creation
  time, executable and task-specific profile/port. Revalidate before termination.
  Never kill all browsers/runtimes; leave uncertain resources alone and report them.
- Clean up verified task-only resources. Keep one compact current checkpoint in
  `docs/CODEX_TASK_REPORT.md`: scope, HEAD/candidate, gates and limits, resources,
  preserved changes and next action. Preserve historical handoffs below it.

## Completion and publication

For implementation tasks, unless the user specifies review-only, planning-only,
no-commit or no-push:

1. Complete the authorized work and required checks; do not publish an unresolved
   blocker as success. Update architecture when ownership changed, the roadmap
   when a milestone completes, and the task report with evidence/limitations.
2. Inspect the actual diff and `git diff --check`. Stage explicit intended paths,
   inspect `git diff --cached` and `git diff --cached --check`, then commit with a
   concise descriptive message. Preserve unrelated worktree changes.
3. Verify branch, configured remote/upstream and SHA. Make a normal push and
   verify the remote contains the accepted SHA. A failed push is a local commit;
   do not force-push or guess another remote.
4. After verified publication, send the established completion email through
   connected Gmail, unless disabled by the user. No alternate delivery mechanism.
   To: `dspevock@stateofthearcelectric.com`
   Subject: `TroubleshootJS: <task/commit summary> pushed`
   Body: task, exact SHA/message, branch, changes, validation, limitations/follow-ups
   and next unstarted milestone. Avoid duplicates. If Gmail is unavailable or
   fails, report the published work and notification failure separately.

Finish with a short, factual handoff: completion status, delivered scope, important
changes, checks/review and their limits, commit/push outcome, remaining worktree
changes/resources and the next unstarted milestone. Never claim actions or proof
that did not happen.
