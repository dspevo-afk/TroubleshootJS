# Task 40 — Physical Fault Locus and Serviceability Admission

Date: 2026-08-19

Status: `FINAL PASS` for implementation, validation, and independent review;
ready for the primary-architect commit/push gate.

## Scope and behavior

Task 40 adds family-agnostic hidden metadata connecting each normally admitted
solver fault candidate to a stable physical locus, legal observation and
isolation actions, a supported repair path, and the Task 39 customer retest.
Loci are semantic component-internal, terminal/lead attachment, connector
contact, or trace-segment identities; private CircuitJS switches never become
physical owners. Admission now rejects unknown observation, isolation, and
repair IDs before candidate selection or physical-owner metrics. The verifier
also proves that a bogus `BOGUS_REPAIR` candidate is rejected.

The generated board retains the full admitted candidate set for owner metrics
and keeps selected-binding integrity separate. Runtime admission checks the
installed original physical part, stable terminal/connection bindings, probe
exposure, replacement provider, operation catalog, and family controller
providers. `Task40DeveloperVerifier` then executes real remove/lift/reconnect
and catalog-install operations through the existing workbench controller,
repowers the board, and invokes customer retest. Connector and trace candidates
remain incompatible for normal play. NPN `LOAD_PATH_OPEN` is a forced
developer-only fixture; NMOS public Q1 terminals and RC C1 positive-lead
attachment remain owned by the original physical part.

The source-of-truth normal corpus is 13 routes: LED 2, diode 1, parallel 2,
RC 2, NPN 3, and NMOS 3. The roadmap’s previous estimate of 14 was stale:
normal `DIODE_SHORT` is developer-only and NPN `LOAD_PATH_OPEN` was removed
from normal admission under the option-B resolution.

## Validation evidence

- JDK8 OBF compile/link passed with the bundled JDK 8u502 after the final
  action-whitelist correction.
- PowerShell parser check returned `PS_PARSE_OK`; `git diff --check` passed.
- The focused Task 40 route in the visible in-app Browser completed the real
  generated-candidate verifier and reported `PASS:task40`.
- Independent review reran/confirmed Task 40, Task 39, NPN, and NMOS browser
  verifiers and found no blocker.
- The standalone Edge/CDP helper remains environment-limited on this host:
  its cleanup/process snapshot can fail before app execution with Edge/WMI
  access denied or a WebSocket cancellation. This does not invalidate the
  visible in-app Browser result.

## Review and limitations

The first independent review found metadata-only Task 40 verification and
selected-owner-only metrics; both were corrected. A fresh independent review
then found the stale 14-route documentation and the missing admission-time
action whitelist; the roadmap/report/architecture notes and source verifier
were corrected. The fresh review returned `FINAL PASS`. Task 41 is identified
as the next milestone but has not been implemented.

---

# Task 39 — Player-Operable Functional Inputs and Customer Retest Contract

Date: 2026-08-19

Status: `FINAL PASS` for implementation, validation, and independent review;
ready for the primary-architect commit/push gate.

## Scope and behavior

Task 39 adds a family-neutral `GeneratedBoardOperationCatalog` with stable
semantic IDs (`CONTROL_INPUT_HIGH`, `CONTROL_INPUT_LOW`, and
`CUSTOMER_RETEST`). NPN and NMOS HIGH/LOW operations dispatch their existing
external CircuitJS command switches; customer profiles validate the resulting
J2.1/gate and load behavior from the solved circuit and restore command, power,
and physical state with nested `finally` cleanup. All six current families own
profiles, and RC uses a real board-power cycle plus natural stored-energy
discharge.

The workbench exposes the operation/retest contract to a normal player. Live
repair status, retest result, Finish Job, and latched `COMPLETED` are distinct.
After completion, board power, instruments, PCB selection, and physical
mutation are disabled; NPN/NMOS semantic operation controls remain live and
solver-backed. Legacy replacement and stress verifiers now finish all physical
checks before the public retest.

## Validation evidence

- JDK8 GWT OBF compile/link passed with the bundled JDK 8u502.
- PowerShell parser check: `PS_PARSE_OK`; `git diff --check` passed.
- `-Task39 -TimeoutSeconds 120`: six routes passed (NPN, NMOS, RC boundary
  plus three visible normal-player routes).
- Seeded matrices passed: NPN 16/16, NMOS 12/12, RC 4/4; natural NPN 4/4,
  natural NMOS 4/4, stored-energy 3/3.
- Affected legacy normal-player flows passed: resistor, diode, parallel, LED,
  and RC terminal-state checks. Replacement seed 3, stress/damage, LED parts,
  diode, parallel, and wrong-repair routes also passed; the two routes that
  initially collided under concurrent Edge processes passed when rerun serially.
- Visible built-in Browser interaction on the rebuilt preview exercised NPN
  `Set control HIGH`, `Set control LOW`, and `Retest Customer`, producing the
  expected solver-backed unrepaired retest message. A visible RC
  `Power-cycle and Retest Customer` action likewise produced the expected
  unrepaired message. Screenshots were captured during both flows.
- Visible Quick Play NPN seed 3 reached the completed report
  `unrepaired-finish-blocked;correct-finish-passed;fresh-session-isolated`;
  physical controls were disabled while HIGH/LOW semantic controls remained
  enabled.

## Review and limitations

The first independent review found post-completion physical checks in the
replacement and stress verifiers; those checks were moved before completion,
and both routes were rerun successfully. The second independent review
returned `FINAL PASS`. The standalone full `-QuickPlay` harness still
reports an Edge/CDP WebSocket cancellation when successful Finish Job reloads
the document; the direct visible Quick Play route verifies the product state
before that expected target replacement. This is recorded as harness evidence,
not a product failure.

---

# Roadmap Completion-Workflow Consistency Correction Report

Date: 2026-08-18

Task type: bounded documentation/process correction only.

Baseline: clean local `master` synchronized with `origin/master` at
`a1e1957bd574c731b9dedcd33e0e53fcee1f89f4`
(`Fix roadmap and publishing workflow`).

Scope: `docs/ROADMAP.md` and this rolling report only. Task 39 and every
implementation milestone remain untouched.

## Contradiction corrected

`AGENTS.md` already defined the normal successful primary-architect workflow as:

    validation
      → final diff/status inspection
      → report and documentation
      → intended-only staging and cached checks
      → one final commit
      → push to the configured upstream
      → verify the remote contains the exact final SHA
      → attempt the post-push Gmail completion notification when available
      → STOP without beginning another milestone

The roadmap still said both “After a milestone is completed and committed,
STOP” and “Stop after the commit.” Those statements could end a task before
publication, remote verification, and notification.

The roadmap now:

- preserves one milestone as one bounded task;
- requires the full permanent `AGENTS.md` Task Completion Protocol before
  stopping;
- states that the configured upstream must contain the exact final SHA;
- includes the post-push Gmail notification attempt and truthful result
  reporting in its Definition of Done;
- updates the roadmap-maintenance stop rule to the same boundary; and
- continues to prohibit automatically beginning the next milestone.

No detailed process law was duplicated beyond the short sequence needed to
remove ambiguity; `AGENTS.md` remains authoritative.

## Files changed

- `docs/ROADMAP.md`
  - corrects the opening milestone boundary;
  - replaces milestone-selection step 6's stop-after-commit instruction;
  - aligns the Definition of Done with verified publication and notification;
  - aligns the roadmap-maintenance stop rule.
- `docs/CODEX_TASK_REPORT.md`
  - overwrites the previous correction handoff with this task report.

`AGENTS.md` was reviewed and intentionally unchanged. Its normal primary
workflow is already consistent, and its delegated coder/reviewer/escalation
prohibitions against committing, pushing, publishing, or notifying remain
intentional.

## Validation

- Initial `git status`: clean on `master` with local HEAD and `origin/master`
  both at `a1e1957bd574c731b9dedcd33e0e53fcee1f89f4`.
- Allowed-path inspection: only `docs/ROADMAP.md` and this report are intended
  to differ.
- Re-read the roadmap Purpose/task-boundary statement, Roadmap Rules →
  Milestone selection, Definition of Done, Roadmap Maintenance Rules, and
  Immediate Next Milestone after editing.
- Searched `AGENTS.md` and `docs/ROADMAP.md` for normal primary-workflow
  stop-after-commit, do-not-push, and commit-as-final-boundary language.
- No stale normal-workflow stop-after-commit instruction remains in the
  roadmap.
- The primary sequence is unambiguous: commit → push → verify → notification
  attempt → stop.
- Delegated subagents remain prohibited from publishing; those rules were not
  treated as contradictions.
- Exactly one immediate `[>]` milestone remains Task 39.
- Task 39's milestone text, scope, dependencies, and status were not modified,
  and no Task 39 implementation was started.
- No roadmap ordering, Task 64/65/66 dependency, difficulty migration, or
  production/architecture content changed.
- Working-tree `git diff --check`: passed.
- Final intended-path, diff, status, and staged-diff checks: passed; exactly the
  two authorized files are staged and `git diff --cached --check` is clean.
- Production JDK/GWT and browser validation were intentionally not run because
  this correction is documentation/process only.

## Review and acceptance

Coder/reviewer subagents: not used; this was a narrow primary-architect wording
correction.

Primary architect result: `FINAL PASS` subject to the required final staged
check, verified push, and notification sequence.

Escalation architect: not required.

## Known limitations

- The report is written before its own commit, push, and email action exist, so
  it cannot contain its authoritative final commit SHA, push result, or
  notification result. Those exact results are available from repository
  history and the final Codex task response.
- This task provides no production-runtime validation evidence and starts no
  implementation milestone.

## Completion intent

Intended commit message: `Align roadmap completion workflow`

Configured publication target: `master -> origin/master`

Notification recipient: `dspevock@stateofthearcelectric.com`

Intended notification subject:
`TroubleshootJS: Roadmap completion workflow correction pushed`

Next roadmap milestone: Task 39 — Player-Operable Functional Inputs and
Customer Retest Contract. It remains the sole immediate milestone and was not
started.
