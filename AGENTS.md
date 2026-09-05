# AGENTS.md — TroubleshootJS

Standing repository instructions | Astra Max / Luna flat orchestration
Policy revision: 2026-09-04

This is a complete root-file replacement, not an appendix to an older
agent hierarchy. It governs work in this repository without authorizing
unrequested product changes. Current task-specific acceptance and safety
contracts remain in force. Keep current status in the project roadmap/log,
not in this operating manual.

## Default operating model — Astra Max with Luna workers

```text
ASTRA — MAX reasoning — single root owner and hands-on orchestrator
    +-- LUNA — bounded investigation / implementation / test / review
    +-- LUNA — independent work only when it adds useful parallelism
    `-- ... no more than 10 concurrently open Luna subagent threads
```

This is the default workflow, not an opt-in bureaucracy mode. There is no
Superintendent, Foreman, mandatory three-investigator panel, or mandatory second
Inspector. Old role names in historical reports do not reactivate an old
hierarchy. A current explicit user instruction can change the workflow.

- Astra owns scope, architecture, assignments, integration, acceptance, and the
  final answer. It may inspect code, implement, debug, test, and repair directly.
  Delegation is not a prohibition on the root doing useful engineering.
- Use Luna for every spawned worker, including reviewers and specialists.
  Default to MAX reasoning and NORMAL speed. Do not select Ultra or Fast for
  workers unless the current user task explicitly changes that requirement.
- The limit is 10 simultaneously open child threads per root session, excluding
  Astra. Count working, waiting, and completed-but-not-closed workers until the
  runtime releases their slots. A lower runtime limit always wins. Ten is a
  ceiling, not a quota; small tasks may need no workers or only one reviewer.
- Workers are leaves: no child agents, nested managers, recursive delegation,
  or side-channel spawning. A worker needing help sends its question to Astra.
- Astra may parallelize independent work inside the authorized task. Do not
  distribute unresolved shared architecture across competing implementations.
  Do not spin up ten redundant investigations because ten slots exist.

### Runtime settings are real settings, not claims in a document

Start the root using Astra with MAX reasoning. Request Luna and its reasoning
explicitly through the available supported spawn/configuration interface rather
than letting workers accidentally inherit Astra. Use the runtime's real model
identifiers and schemas; do not invent parameter names or capability checks.

This file does not itself switch the running model, enable delegation, increase
thread capacity, or alter global configuration. Missing effective-model or
speed metadata is not a blocker by itself. State what was requested and what
was observable; never claim enforcement that the interface cannot verify.

Keep project worker defaults in `.codex/config.toml` and any custom agent
presets consistent with this operating model. Retire obsolete role instructions
when changing the workflow; do not leave a second command hierarchy in presets.

Where a speed control exists, select NORMAL/default non-Fast for every worker,
regardless of root speed. Where it does not, do not deliberately select Fast,
do not invent a setting, and disclose the limitation once. Known model,
reasoning, or speed mismatches must not be silently relabeled as compliant.
Do not repeatedly respawn workers to chase unavailable settings.

If delegation is unavailable, Astra may continue safe, authorized work directly
and report that limitation. Do not invent an independent review or declare a
required independent-review gate satisfied. Do not silently substitute another
worker model or edit the user's global agent configuration to bypass a limit.

## Authority, orientation, and scope

Follow platform/system/developer instructions and actual runtime precedence.
Within repository guidance, the current explicit user task takes precedence,
then applicable more-specific instruction files, then this root manual, then
standing project documentation. Read applicable `AGENTS.override.md`, nested
`AGENTS.md`, and relevant project guidance; never assume this file overrides a
higher-priority instruction. Flag an actual conflict rather than following two
incompatible hierarchies or silently rewriting an unrelated instruction file.

Before substantive edits:

1. Confirm the repository, working directory, branch, HEAD, tracked changes,
   and pre-existing untracked paths. Preserve unrelated work.
2. Read the project sources named below, relevant implementation, callers,
   tests, and recent history. Read affected sections, not every historical log.
3. Identify the authoritative state/ownership boundary and the root cause.
   Existing code is evidence of behavior; existing behavior is not automatically
   the intended contract, and a passing test does not override the user request.
4. Define the smallest coherent scope, acceptance criteria, explicit non-scope,
   and closed validation set. A short plan or existing task record is enough;
   do not create a paperwork framework for a small repair.

Use the actual local task branch, not an assumed default branch. Read current
roadmap status rather than freezing a phase number or old blocker into this
manual. If documentation disagrees with code, resolve the relevant discrepancy
using implementation, tests, history, and the requested contract. A renamed
navigation document is not automatically a product blocker; a missing required
acceptance contract may be.

Work only within the requested boundary. A request for an entire phase or
explicit sequence authorizes its eligible substeps without another permission
request after each small fix; respect dependencies and required gates. It does
not authorize the next unrequested phase, unrelated backlog, or a rewrite.

Prefer surgical root-cause fixes, existing adapters/services/registries, and
small testable changes. Preserve working behavior and compatibility. No
opportunistic dependency upgrades, framework migrations, formatting churn,
duplicate state owners, or broad god-class decomposition. Do not impose a
fake two-file limit when a correct repair genuinely requires more files.

## Delegation, ownership, and independent review

Give each worker one compact, self-contained assignment: objective, expected
behavior, relevant files/contracts, read/write permission, exclusive write
scope, dependencies, required checks, and requested evidence. Provide only
necessary context; use the smallest supported history fork. Reviewers receive
the original requirement and candidate, not a transcript telling them why the
author believes the patch is correct.

A small in-session ownership map is sufficient. Record worker ID, purpose,
owned files or read-only scope, state, and any owned processes/temp locations.
Do not build a permanent delegation database or transcript archive.

- One active writer per file, including Astra. Different files can still share
  an invariant; agree on interfaces and integration order before parallel edits.
- Shared models, schema, core state, build/configuration, generated outputs, and
  integration files need one explicit owner. Other workers propose changes to
  that owner instead of editing concurrently.
- Complete and reconcile prerequisite investigations before dependent coding.
  Independent work need not wait for an unrelated investigation to finish.
- Test workers and reviewers are read-only with respect to source, fixtures,
  goldens, and configuration unless separately reassigned to implementation.
  Their test runs may create only scoped, disposable execution artifacts.
- Workers do not stage, commit, merge, cherry-pick, push, or send external
  notifications. Astra owns authorized integration/publication. Never treat
  another session's simultaneous edit as something to overwrite or discard.
- If isolated worktrees are used, record each base and patch/commit identity.
  Astra reviews and integrates them explicitly; never assume separate worktrees
  share uncommitted edits.

For a nontrivial production change, or any persistence, money calculation,
identity, synchronization, security, or resource-lifecycle change, obtain one
fresh Luna read-only review of the integrated candidate. The reviewer must not
have authored the implementation or its test oracle. Review includes the real
diff, relevant call paths, acceptance checks, and focused independent validation
where feasible. A documentation-only or genuinely trivial change does not
require a ceremonial worker panel.

Astra inspects the integrated result and adjudicates findings using evidence.
An extra independent Luna specialist is optional for a concrete unresolved risk,
not a mandatory Inspector role. If Astra or a worker repairs a reviewed area,
rerun affected checks and obtain a targeted independent delta review. Do not
reuse a pre-repair PASS for changed behavior or restart an unrelated full audit.

## Waiting, updates, and resource use

When useful non-overlapping root work remains, Astra may do it. Otherwise use
the supported long blocking agent wait, not busy polling. Prefer the configured
wait; when an explicit duration is needed, use the longest supported practical
wait, up to 3,600,000 ms when supported. Do not invent an unsupported one-hour
argument. A platform-imposed shorter timeout simply means wait again silently.

No periodic "still working," "no updates," worker-list polling, status requests,
or minute-by-minute heartbeat narration. Send a brief initial plan, material
findings or decisions, genuine blockers, and the final result. An empty wake-up
is not progress. Do not interrupt or kill a worker because it is quiet or slow.
Agent-wait duration and subprocess/test timeouts are separate controls.

Reuse a worker only for a known immediate follow-up compatible with its role.
Once its result and resource ownership are reconciled, close it. Do not retain
idle workers just in case or create duplicates of active assignments.

Use one expensive full build/test matrix per worktree at a time. Give each
browser session, GUI, emulator, device, port, and mutable test resource one
owner. Reduce concurrency if memory, CPU, disk, shared tooling, or desktop
responsiveness suffers. Ten workers are not permission for ten simultaneous
Gradle builds, browser farms, or recursive repository copies.

## Validation, failure handling, and closure

Define required gates before implementation from the user task, current
roadmap, project policy below, and directly affected invariants. Run focused
checks during development, then the applicable complete gates against the
final integrated candidate. Reuse evidence only when the relevant candidate,
inputs, environment, and toolchain are unchanged, and identify that reuse.
A documentation-only correction does not require rebuilding unchanged code.

When changing an operating-system, subprocess, listener, or transport boundary,
run a small check through the actual selected implementation early, before the
large validation matrix. Pair it with focused negative canaries; a mocked
dependency does not establish that the real host path works. Preserve required
gate ordering, including independent review before live browser work when the
task requires it. This rule does not add runtime checks to documentation-only
work or substitute a smoke check for the final complete gates.

For each result record the command or interaction, exit/result, relevant
output/evidence, and candidate identity (SHA plus uncommitted diff when needed).
Use `PASS`, `FAIL`, `BLOCKED`, `NOT RUN`, or `NOT APPLICABLE` accurately.
Missing dependencies, no collected tests, disabled smoke paths, skipped required
cases, timeouts, unauthorized devices, and missing logs are not PASS.

Do not weaken assertions, suppress errors, bless goldens blindly, fake UI
interaction, bypass the actual execution path, or substitute a nearby easier
check for a required gate. An intentional contract/oracle change requires
explicit task scope and reviewed expectations. Separate implementation tests,
manual behavior evidence, and verifier/tooling certification.

Classify findings by both impact and cause:

- `BLOCKER`: breaks current acceptance, correctness, data/electrical integrity,
  an established invariant, a relevant regression, or a required gate.
- `FOLLOW-UP`: a real issue that does not invalidate the requested result.
- `BACKLOG`: an optional improvement outside scope.

Cause is `CODE`, `TEST/TOOL`, `ENVIRONMENT`, or `USER DECISION`. A broken required
harness can block qualification without proving the product broken. Optional
harness flakiness is not automatically a reason to reopen product code.
Record nonblocking findings; only genuine blockers reopen implementation.

After two identical environment/tool failures, stop rerunning the unchanged
full command. Preserve the signature, diagnose a specific cause, change or
verify a relevant condition, try a bounded equivalent method where permitted,
or report the remaining prerequisite. Continue while a safe evidence-based
repair path exists; neither elapsed time nor an arbitrary retry quota decides
correctness. Do not stack speculative patches or call repeated output progress.

Do not expand the blocking finish line indefinitely. New evidence of a real
correctness/integrity defect may add a necessary regression; aesthetic cleanup
or hypothetical perfection may not. Once acceptance, applicable gates, review,
and final reconciliation pass, complete the authorized handoff/publication and
stop at the requested boundary.

## Git, user data, processes, and scratch safety

Never discard unrelated edits, untracked files, private project data, or another
agent's work. No broad `git clean`, `git reset --hard`, destructive restore,
force-push, history rewriting, branch deletion, or mass filesystem cleanup
without explicit authorization for the exact operation and targets. Stage
explicit intended paths only, and inspect staged and unstaged changes separately.

Do not expose or commit credentials, tokens, private documents/inventory,
absolute personal paths, raw user payloads, or uncontrolled logs. Use sanitized
fixtures. Do not escalate privileges, change authentication, weaken sandboxing,
or modify global runtime settings just to turn a failure green.

Prefer bounded commands that exit naturally. Do not leave task-only shells,
servers, watchers, browsers, test runners, or worker threads alive. For a needed
persistent process, record ownership at launch using the available process
handle/job/group or PID plus creation time, executable, and task-specific
profile/port. Revalidate identity before terminating it; a process name or PID
alone is not sufficient. Respect stricter task-specific cleanup contracts.

Never kill all Edge, Chrome, Node, Python, Java, PowerShell, or IDE processes.
If ownership cannot be established, leave that resource alone, record it, and
use safe isolation where possible. An uncertain old process can block its
cleanup or a particular isolation gate; it does not automatically prohibit all
unrelated useful work. Do not claim a clean-state test when it was not proven.

Substantial scratch trees, temporary repository copies, and pytest `--basetemp`
must use unique, task-owned OS-temp subdirectories outside the repository.
Never pass the shared OS-temp root itself as a destructive test base. Verify
resolved containment and ownership before cleanup, including symlink/reparse
boundaries. Exclude `.git`, caches, nested scratch, build output, and private
data from temporary source copies unless a small specific fixture needs them.
Do not recursively copy a repository into itself or run per-file Git commands
over generated scratch. Unexpected tree/process growth is a reason to stop the
run and diagnose it, not launch more workers or hide it with `.gitignore`.

Normal build outputs may remain in their documented build directories; the
external-scratch rule is not permission to relocate or delete required build
assets. Clean only verified task-owned disposable resources. Before a pause or
context handoff, save one compact checkpoint with HEAD/candidate, diff scope,
pre-existing changes, worker/resource ownership, exact gate results, blockers,
and the next action. Revalidate that state when resuming.

## TroubleshootJS — project direction and sources

TroubleshootJS is a PCB troubleshooting simulator built around CircuitJS, not a
schematic-reading quiz or a general-purpose PCB CAD package. The player gets an
incomplete complaint, examines an unfamiliar board, measures, isolates faults,
repairs it, and verifies restored operation. Favor electrical reasoning over
memorized layouts or clicking the secretly marked bad part.

Read `docs/ROADMAP.md` for sequencing, `docs/ARCHITECTURE.md` for implemented
ownership, and `docs/CODEX_TASK_REPORT.md` for the current handoff and evidence.
Consult current build scripts, verifier contracts, tests, and any more-specific
instructions for the affected code. These documents, not a snapshot in this
manual, determine the current task number and implementation status.

Keep one compact current checkpoint at the top of `docs/CODEX_TASK_REPORT.md`:
active scope, branch/HEAD and candidate identity, gate results and their limits,
review status, owned resources, and the next safe action. Preserve historical
handoffs below it. When the candidate changes, distinguish earlier passing
evidence from checks still required for the new candidate.

Keep simulation adaptation, circuit generation/validation, fault injection,
PCB generation/routing/rendering, instruments, board mutation, damage, and
scenario/scoring responsibilities separated. Prefer existing extension seams;
isolate necessary upstream CircuitJS changes and preserve mergeability.

## TroubleshootJS — electrical and gameplay invariants

- **CircuitJS is electrical truth.** Player actions modify the active electrical
  graph; the solver produces the resulting behavior. Never make a failing
  circuit appear correct with hard-coded meter readings, scenario-specific UI
  patches, or an independent replacement physics engine. Any deliberate model
  approximation must be explicit, bounded, and consistent with the contract.
- **One authoritative graph and stable identity.** Keep generated logical
  circuits separate from visual geometry and from current user modifications.
  Preserve stable component, terminal, node/net, pad, trace, and probe identity
  through mutation, reset, and rerendering. Reuse the actual graph/mutation
  owner; do not infer connectivity from pixels, labels, or array positions.
- **Every conductive visual feature maps correctly.** Pads, leads, exposed
  copper, connectors, and test points must identify the right electrical target.
  Routing and placement must not silently short nets or disconnect a logical
  connection. Realistic routing links are preferable to fake connectivity.
- **Generate valid challenges.** Use constrained functional families and valid
  topology modules, not arbitrary random circuits. Validate healthy behavior,
  inject a compatible fault, verify a meaningful faulty symptom, then generate
  and map the PCB. Reject invalid/uninteresting generations instead of hiding
  them. Supporting healthy circuitry is not automatically faulty.
- **Reproducibility matters.** Preserve seeded topology, parameter, layout, and
  fault generation for a given version/settings contract. Tests select explicit
  representative families/topologies and known boundary/regression seeds rather
  than hoping a random draw reaches the changed path.
- **Mutations are electrical, not decorative.** Removal, lead lifting,
  replacement, jumpers, trace cuts/restoration, and secondary failures modify the
  live circuit. Preserve original-versus-current board state and documented
  undo/reset behavior. Wrong repairs have their electrical consequences; do not
  silently undo mistakes or protect a player with fictitious readings.
- **Measurement lifecycle is safe.** Respect powered versus unpowered modes,
  in-circuit parallel paths, isolated/removed components, and board-power
  isolation. Temporary test sources, temporary graph elements, listeners, and
  instrument state must be removed/restored on exit, error, cancellation,
  switching, mutation, and reset. Stale instrument work must not alter a new
  graph or reenergize a board after the user turns it off.
- **Preserve probe controls.** In a probe-based meter mode, left click places
  red and right click places black. Suppress normal context behavior only while
  needed, and selecting the active mode again exits and restores normal input.
  Do not change this interaction contract in an unrelated repair.
- **Damage and waveforms follow simulation.** Stress, heating, current limiting,
  secondary failures, and scope waveforms must have defensible electrical causes.
  Do not introduce arbitrary damage rolls, fake power limiting, or theatrical
  waveform animation as substitutes for implemented behavior.
- **Keep the answer private.** Normal-player complaints, UI, labels, overlays,
  and interaction must not reveal hidden faults, topology answers, netlists,
  debug flags, the neat schematic, or the intended repair path. Developer views
  are explicitly separate. Difficulty and scoring may not cheat the physics.
- **Verify function, not the answer key.** Completion requires restored customer
  behavior under relevant power/input conditions. Preserve valid alternative
  repairs instead of requiring a click on the originally faulted component.

These rules preserve existing behavior; descriptions of possible instruments,
board features, or damage systems are not permission to implement future
roadmap features during a focused task.

## TroubleshootJS — validation and browser evidence

Select direct electrical, identity, mutation, generation, lifecycle, and privacy
regressions for the changed boundary. Use representative explicit seeds and
known failures; broad historical matrices are justified by risk, not required
for every minor edit. Java/GWT production changes require a final JDK 8/GWT
production build from the final source candidate using the repository's actual
build command. Earlier diagnostic builds do not prove later edits.

Preflight only capabilities required by the selected gates: JDK/GWT, listeners,
local service reachability, browser access, and process-ownership inspection
when the verifier needs it. Do not require elevated WMI, a CDP handshake, or a
browser for an unrelated documentation/unit-test task. Never grant yourself
elevation or weaken a listener/ownership contract to get a verifier running.

For a visible player-flow change, exercise the actual production preview with
real visible input and inspect meaningful initial, changed, invalid/unrepaired,
and successfully repaired states as relevant. Prefer the built-in Browser.
An available user-authorized Computer Use route may interact with a task-owned
browser when needed; protect the user's desktop and record the route used.
Do not silently substitute manual evidence for a task that explicitly requires
a particular automated verifier, browser capability, or certification method.

DOM/console/CDP inspection can diagnose failures. Injected clicks, direct
controller calls, private state mutation, and mock screenshots do not prove
normal-player interaction. A CDP transport failure is not evidence that the
application is broken, and successful manual interaction is not evidence that
the CDP verifier itself passed. Record these as separate gates.

Use a dedicated profile/port where the test requires isolation. Do not close
all of the user's browsers to manufacture a clean state. Preserve exact
process/listener ownership, immediate identity revalidation, fail-closed trust,
negative/positive canaries, and documented exit-code semantics. In particular,
an unproven/blocked verifier result or exit code 2 is not PASS. A new method may
satisfy an equivalent behavior check only when its evidence meets that contract;
it cannot waive stricter task-specific cleanup or isolation requirements.

Capture a small curated set of real screenshots for material visible changes,
normally two to five. Inspect that they are nonblank and show the intended
normal-player states. Surface them to Astra/user as supported. Preserve final
curated evidence under `docs/task-evidence/task-XX/` using the actual task number
and descriptive filenames, and explain what each proves in the task report.
Do not commit screenshot spam, private data, or fabricated visual evidence.

## TroubleshootJS — documentation and publication

Preserve the existing project-specific completion workflow for implementation
tasks unless the current user task overrides it. It does not apply to a
read-only review, planning-only request, or an explicit no-commit/no-push task.
Astra alone owns the following publication sequence:

1. Complete the requested work, required validation, independent review, and
   final reconciliation. Do not publish an unresolved blocker as success.
2. Update `docs/ARCHITECTURE.md` when implemented architecture changed. For a
   completed milestone, update `docs/ROADMAP.md`, retain history, and identify
   but do not start the next unrequested milestone. Update
   `docs/CODEX_TASK_REPORT.md` with evidence, limitations, and curated screenshots.
3. Stage only intended changes; inspect the staged diff and whitespace. Commit
   with a concise descriptive message unless the task forbids it.
4. Verify branch, configured remote, upstream, and final SHA; make a normal push
   and verify the remote contains that accepted SHA. Do not force-push or guess
   another remote when the configured publication path fails.
5. Only after verified publication, attempt the established completion email
   through connected Gmail when available, unless the user disables it:

   ```text
   To: dspevock@stateofthearcelectric.com
   Subject: TroubleshootJS: <task/commit summary> pushed
   Body: Task; exact commit SHA/message; branch; change summary; validation;
         limitations/follow-ups; next unstarted milestone when applicable.
   ```

Do not create another mail-delivery mechanism. A failed push leaves a local
commit, not a successfully published task; report the SHA and stop at that
boundary. A successful push with unavailable/failed Gmail remains published,
but the notification must be reported as not sent. Do not send a success email
before the push or retry into duplicate notifications without checking results.

## Final handoff

Inspect the actual final diff, `git diff --check`, and `git status`. When staging
is authorized, also inspect `git diff --cached` and run
`git diff --cached --check`. Confirm there are no accidental data, scratch,
generated, or unrelated changes. Do not label a previously dirty tree clean.

Keep the final report short and evidence-based. Include:

```text
STATUS: COMPLETE | IMPLEMENTED — VALIDATION BLOCKED | BLOCKED | REVIEW ONLY
SCOPE: What the user requested and what was actually done.
CHANGES: Important behavior and files; no transcript dump.
VALIDATION: Exact checks/results and candidate; identify reused evidence.
REVIEW: Independent reviewer result, or NOT RUN with the reason.
LIMITATIONS: Failed/unavailable required gates, uncertainties, and impact.
FOLLOW-UPS: Genuine nonblocking findings, or NONE.
GIT: Branch, commit/push outcome when applicable, and remaining worktree changes.
RESOURCES: Unresolved owned processes/temp resources, or NONE.
NEXT: Only a necessary unblock action or the next unstarted roadmap item.
```

For review-only tasks, report findings rather than making unrequested edits.
For incomplete work, identify the exact missing proof and preserve the useful
changes. Never claim a test, review, commit, push, cleanup, or notification that
was not actually performed. Stop when the requested work is finished.
