# Documentation and Publishing Workflow Correction Report

Date: 2026-08-18

Task type: bounded documentation/process correction only.

Baseline: clean local `master` synchronized with `origin/master` at accepted
planning commit `8ef9d78f59c07c395b17fd7c8bd1bdd340d6f2d2`
(`Redesign roadmap around audit findings`).

Scope: `AGENTS.md`, `docs/ROADMAP.md`, and this rolling report only. No Task 39
implementation, Relay work, visual implementation, production code, electrical
behavior, PCB behavior, UI behavior, generator, validator, build, test, or
CircuitJS integration was changed.

## Summary

This correction preserves the accepted audit-driven roadmap design while
repairing two internal roadmap references and making the project owner's
push-and-notify completion workflow permanent.

- The former Task 60 Difficulty Model migration now points to actual difficulty
  milestones Tasks 58–59, 80, and 85 rather than Save/Resume Task 88.
- The intended post-alpha sequence is now explicit and consistent:

      Task 63 alpha gate
        → Task 64 static visual/PCB polish
        → Task 65 continuous LED visual state
        → Task 66 reusable Relay block expansion

- Task 66 now names Task 65 in both its blocking declaration and dependency
  description.
- The Revised Dependency Map now includes Tasks 64–65 between the alpha gate
  and Tasks 66–79.
- The permanent completion protocol now requires a verified push followed by a
  Gmail completion-notification attempt when that capability is available.
- The rolling report no longer uses self-referential pending SHA/push fields.

Task 39 remains the sole immediate next implementation milestone and was not
started.

## Permanent workflow change

For normal successful TroubleshootJS tasks, the primary architect now:

1. performs the requested bounded work;
2. runs the required build/test/browser validation;
3. inspects the final diff and status;
4. updates architecture/roadmap documents when applicable and overwrites this
   rolling report;
5. stages only intended changes;
6. runs `git diff --cached --check`;
7. creates one concise final commit;
8. pushes the current branch to its configured upstream and verifies the remote
   contains the exact final SHA;
9. only after that verified push, sends the project owner a completion email
   through the connected Gmail capability when available; and
10. stops without beginning another roadmap milestone.

Failure behavior is explicit:

- Validation failure prevents commit, push, and success email; intended changes
  remain available for correction and the failure is reported.
- Commit success followed by push failure preserves the local commit, reports
  its exact SHA, sends no success email, and stops.
- Push success followed by Gmail unavailability/failure remains a successfully
  published Git task, but the notification failure is reported without
  fabricating delivery.
- An explicit instruction for an individual task may override automatic commit,
  push, or notification behavior.

Delegated subagents remain prohibited from publishing; only the primary
architect performs the accepted final push and notification workflow.

## Files changed

- `AGENTS.md`
  - updates permanent completion, failure, report, and stop conditions for the
    verified push and post-push Gmail workflow;
  - preserves validation-before-publication and primary-only push authority.
- `docs/ROADMAP.md`
  - corrects former Task 60's migration target from Task 88 to Task 85;
  - makes Task 65 an explicit Task 66 prerequisite;
  - inserts Tasks 64–65 into the dependency map.
- `docs/CODEX_TASK_REPORT.md`
  - replaces the prior planning report with this correction-task handoff;
  - removes permanently stale final-commit/push placeholders.

`docs/ARCHITECTURE.md` was intentionally unchanged because no implemented
architecture or behavior changed.

## Validation

- Initial `git status`: clean on `master` with local HEAD and `origin/master`
  both at `8ef9d78f59c07c395b17fd7c8bd1bdd340d6f2d2`.
- Allowed-path check: only `AGENTS.md`, `docs/ROADMAP.md`, and this report are
  intended to differ.
- Re-read Tasks 58–66, the Former Future-Task Migration Map, the Revised
  Dependency Map, and the Immediate Next Milestone after editing.
- Re-read the permanent Task Completion Protocol and multi-agent completion
  section after editing.
- Confirmed automatic verified push is the normal default.
- Confirmed validation failure prevents commit, push, and success email.
- Confirmed push failure preserves/reports the local commit and prevents a
  success email.
- Confirmed Gmail is attempted only after remote verification and its failure
  cannot masquerade as success.
- Confirmed an explicit future task instruction may override commit, push, or
  notification behavior.
- Stale-reference scan found no difficulty milestone pointing to Save/Resume.
- Task 66 status/dependencies and the dependency map all agree on Task 65.
- Forward-dependency scan found no future/self dependency in future milestone
  status/dependency declarations.
- Exactly one immediate `[>]` status remains: Task 39.
- Relay remains revised Task 66.
- The diff does not touch completed Task 1–38 roadmap history.
- No roadmap or report claims Task 39 implementation started.
- No obsolete normal-task do-not-push default remains; subagent publication
  prohibitions remain intentionally in force.
- Working-tree `git diff --check`: passed.
- Final intended-path, diff, status, and staged-diff checks: passed; exactly the
  three authorized files are staged and `git diff --cached --check` is clean.
- Production build and browser validation were intentionally not run because
  this task changes documentation/process only.

## Review and acceptance

Coder subagent: not used; this was a bounded primary-architect documentation
correction.

Reviewer subagent: not required by this correction brief.

Primary architect review rounds: one bounded edit and consistency pass.

Primary architect result: `FINAL PASS` subject to the required final staging,
push verification, and notification sequence.

Escalation architect: not required.

## Known limitations

- The final commit SHA, push result, and notification result cannot exist until
  after this report is written and committed. Their authoritative values are
  available from repository history and the final Codex task response.
- This documentation-only correction provides no production-runtime validation
  evidence and intentionally starts no roadmap implementation.

## Completion intent

Intended commit message: `Fix roadmap and publishing workflow`

Configured publication target: `master -> origin/master`

Notification recipient: `dspevock@stateofthearcelectric.com`

Intended notification subject: `TroubleshootJS: Roadmap correction pushed`

Next roadmap milestone: Task 39 — Player-Operable Functional Inputs and
Customer Retest Contract. It remains selected only and was not started.
