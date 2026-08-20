# Post-Task-43 AGENTS.md Governance Cleanup

Perform one bounded governance/documentation task on the **accepted post-Task-43 baseline**.

This task is ONLY to review and clean up permanent project-agent governance in `AGENTS.md`.

Do not modify production code.
Do not begin Task 44 or any later roadmap milestone.
Do not change electrical behavior, PCB behavior, tests, verifiers, generated layouts, or application behavior.
Do not broadly rewrite `AGENTS.md` merely for style.

Before editing, read the current post-Task-43 versions of:

- `AGENTS.md`
- `docs/ROADMAP.md`
- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`

Also inspect the accepted Task 43 diff/history where useful.

The goal is to make `AGENTS.md` more internally consistent, safer and more efficient for future autonomous work, and explicit about model selection, delegation, correction routing, subagent patience, convergence, feasibility, and architecture preservation while retaining every valid existing project law.

---

## 1. Permanent substantive-coder model policy

Whenever the primary architect delegates substantive implementation work to a coder agent, it must explicitly request the highest-capability Luna configuration currently named:

**Luna MAX**

Do not intentionally substitute Luna Extra High, High, Medium, Low, or another cheaper/faster configuration for architecture-sensitive work merely to save credits or elapsed time.

The architect must explicitly request Luna MAX rather than assuming a generic Luna request resolves to MAX.

If the runtime exposes the actual spawned configuration, inspect it once when useful. If the platform silently supplies a lower configuration despite an explicit Luna MAX request, record that mismatch truthfully. Do **not** enter a respawn loop, repeatedly kill/recreate agents, or burn credits trying to force MAX unless the platform provides a documented supported mechanism for doing so.

If Luna MAX is unavailable for work that requires it, do not silently treat Luna Extra High as equivalent. Report the model availability limitation or use another model only when this policy explicitly permits it for that class of work.

Line count is not a model-selection criterion. A five-line lifecycle or identity fix may be reasoning-sensitive; a fifty-line mechanical migration may be low risk.

Luna MAX is required by policy for substantive work involving, or plausibly affecting:

- architecture or abstraction boundaries;
- CircuitJS electrical behavior or solver integration;
- board-power semantics;
- active measurement transactions;
- physical/electrical correspondence;
- stable semantic or physical identity;
- package/footprint/probe/selection geometry contracts;
- lifecycle epochs, readiness, asynchronous state, or mutation ordering;
- atomic/coherent graph mutation;
- fault-engine semantics;
- repair/completion/customer-retest contracts;
- diagnostic solvability or admission;
- procedural-generation architecture;
- deterministic replay/versioning;
- functional block composition;
- domains, ports, namespace, or ownership contracts;
- routing algorithms or physical-connectivity semantics;
- damage/stress/thermal behavior;
- player-answer/privacy boundaries;
- ambiguous bugs whose root cause has not yet been established;
- broad or cross-cutting refactors;
- competing implementation strategies;
- corrections whose exact desired implementation cannot be stated from an already-settled contract.

---

## 2. GPT-5.3-Codex-Spark delegation policy

GPT-5.3-Codex-Spark may be used only for low-risk, tightly bounded work whose correct implementation is already substantially determined by existing architecture, explicit instructions, established interfaces/types, or focused tests.

Spark is a fast implementation assistant for mechanical work. It is **not** an architectural decision-maker.

The primary architect remains responsible for understanding surrounding architecture, defining the implementation boundary, deciding contracts/semantics, determining whether Spark is appropriate, reviewing Spark output completely, integrating accepted output, and requiring normal validation.

Appropriate Spark work may include:

- implementing a small already-designed immutable value object;
- straightforward accessors/adapters to an established interface;
- mechanical call-site migration after the new API is defined;
- narrowly specified tests or verifier canaries;
- simple compiler fixes caused by an already-designed refactor;
- tracing references/usages of a known symbol;
- repetitive registry/provider additions following a proven pattern;
- localized presentation-only CSS/UI work with no simulation authority;
- documentation changes;
- behavior-preserving cleanup;
- removing obsolete calls after the replacement path is already established;
- exact reviewer-requested corrections whose implementation is unambiguous and architecture-free.

Spark must not independently:

- invent a new architectural pattern;
- choose between competing architectural designs;
- diagnose an unresolved architecture-sensitive root cause;
- weaken a verifier or acceptance criterion;
- alter electrical or physical semantics;
- alter lifecycle/state ordering;
- alter stable identity semantics;
- create one-off compatibility hacks because the proper boundary is unclear;
- expand task scope;
- decide whether a permanent invariant should change;
- declare an architectural conflict resolved without escalation.

Before delegating implementation to Spark, the primary architect must be able to answer YES to all of these:

1. Is the desired behavior already clearly and completely specified?
2. Is the relevant architecture already established?
3. Is there no meaningful architectural decision left for the coder?
4. Is the affected surface small and easy to review completely?
5. Would a mistake probably fail obviously through compile errors, focused tests, or a small diff?
6. Can the primary architect quickly inspect the entire result?
7. Is there minimal realistic chance of a subtle defect affecting electrical truth, stable identity, lifecycle state, physical geometry, generation determinism, or player-facing correctness?

If any answer is no, uncertain, context-dependent, "probably," or "it depends," use Luna MAX.

When in doubt, Luna MAX wins.

Delegating work never delegates responsibility. Spark-produced changes receive the same review and validation required if Luna MAX had written them.

Cost or speed must never override correctness risk.

Governing principle:

> Spark is appropriate for mechanical work after the hard decisions have already been made. If Spark would need to determine what the architecture should be, understand an ambiguous root cause, or choose between competing solutions, use Luna MAX.

---

## 3. Permanent correction-routing policy

After a primary coder returns its candidate and a reviewer or architect identifies a legitimate `BLOCKER`, the architect should diagnose enough of the root cause to classify the correction as exactly one of:

- `MECHANICAL`
- `REASONING_SENSITIVE`

### MECHANICAL

Mechanical means the architecture is already settled, the exact desired behavior follows directly from an established contract, there is no meaningful design choice, the affected surface is easy to review, and no interpretation of electrical/physical/lifecycle/identity/generation semantics is required.

A `MECHANICAL` correction may be delegated to Spark.

Examples include a forgotten interface method, missed call-site migration, focused verifier assertion, trivial compile repair after an accepted refactor, or cleanup of a deprecated API after its replacement is already established.

### REASONING_SENSITIVE

Reasoning-sensitive includes ambiguous root cause; deciding which subsystem owns a defect; choosing among architecture alternatives; electrical behavior; solver integration; stable identity; graph ownership; lifecycle/state ordering; measurement transactions; mutation atomicity; package/geometry ownership; routing/connectivity semantics; procedural generation; challenge admission/solvability; fault/repair/retest semantics; version/replay behavior; privacy/answer leakage; broad regression risk; or a correction where previous fixes merely moved the failure elsewhere.

A `REASONING_SENSITIVE` correction must be delegated to Luna MAX.

Do not intentionally use Luna Extra High as a cheaper/faster substitute.

The architect should ordinarily remain the architect rather than becoming a second general-purpose coder.

Preferred flow:

```text
architect diagnoses
-> architect classifies correction
-> Spark handles exact MECHANICAL correction OR Luna MAX handles REASONING_SENSITIVE correction
-> reviewer independently checks corrected candidate when required
-> architect performs final review
```

---

## 4. Exclusive write ownership

Only one write-capable coder may own a given implementation surface at a time.

Before changing coders/models, the previous write-capable coder must have returned control. Ownership must transfer explicitly.

Never allow Luna MAX and Spark to edit the same worktree/implementation concurrently.
Never allow two Luna coders to edit the same implementation concurrently.

Parallel read-only investigation remains permitted when genuinely useful.

Model switching is sequential, not tag-team editing.

---

## 5. Subagent patience and anti-polling policy

Once implementation, review, correction, or escalation has been delegated and the assigned subagent is actively working, the architect should trust delegated ownership and allow the subagent to work.

The architect must not repeatedly poll activity merely to reassure itself that the subagent is still alive.

Wasteful liveness polling includes repeatedly:

- checking the timestamp of the last edited file;
- checking when the subagent last wrote to the worktree;
- running `git status` only to see whether another file changed;
- running `git diff` repeatedly merely to prove activity;
- checking process state without evidence of a problem;
- tailing logs merely to confirm new lines are appearing;
- asking the subagent "are you still working?";
- requesting progress updates that do not change any decision;
- repeatedly inspecting the same partial implementation while the coder still owns it;
- repeatedly narrating "the coder is still working" or equivalent;
- consuming reasoning/model cycles solely to observe elapsed time.

Long elapsed time, silence, a long build, a long verifier run, or an unchanged file timestamp are not themselves evidence of failure.

Prefer event-driven completion: delegate, let the phase run, react when the subagent returns, reports `BLOCKED`/`FAILURE`, requests clarification, or reaches a real synchronization point.

If the environment has no event-driven completion mechanism, an occasional lightweight status check is permitted. Do not poll an apparently active subagent more frequently than approximately once every 30 minutes solely for liveness. Longer intervals are preferred for large architecture tasks, and this is a ceiling on unnecessary polling frequency, not a requirement to poll every 30 minutes.

More frequent checking is permitted only for a concrete reason such as an explicit user status request, a known timeout/process failure, a subagent request/blocker, unexpected termination, suspected destructive/out-of-scope behavior, materially new user information, or an explicit synchronization point.

Do not terminate, replace, duplicate, or spawn another coder for the same implementation merely because the current coder has been quiet.

Governing principle:

> Delegate, trust the delegated phase, and inspect the result when it returns. Do not pay a senior architect to stare at file modification timestamps.

---

## 6. Conservative parallel-subagent policy

Review the existing `Parallel Subagent Policy` and change any wording that implies the architect SHOULD parallelize merely because it might save elapsed time.

Permanent intent:

- Parallelism is optional, not a goal.
- Correctness, clear ownership, and integration simplicity outrank throughput.
- Read-only investigation/review may be parallelized when genuinely useful.
- Write-capable parallel implementation is exceptional and requires clearly disjoint ownership with no shared abstraction or unfinished dependency.
- Sequential implementation is the safe default when uncertainty exists.
- Never create extra agents merely to maximize utilization.
- Exactly one roadmap milestone remains active regardless of subagent count.

Preserve the existing prohibition against concurrent writers touching the same implementation/shared abstraction.

---

## 7. Architecture over incidental compatibility

Task 43 exposed a permanent engineering lesson.

When introducing or consolidating an authoritative contract/source of truth, preserve real compatibility requirements, not accidental implementation details.

Real compatibility requirements include electrical behavior, stable TroubleshootJS board/component/pad/net/physical-part identity, terminal identity, explicitly promised replay/version behavior, required player-visible behavior, and established architectural contracts.

Historical implementation details are not automatically compatibility requirements. Examples include old hand-authored pixel coordinates, route control points, fixture-specific offsets, duplicated geometry, collection order, or implementation accidents not promised by a version/replay contract.

Do not pollute a new authoritative abstraction with component-ID-specific, seed-specific, test-fixture-specific, or route-specific exceptions merely to preserve incidental historical behavior.

A package/provider/layout/etc. variant must represent a genuine reusable semantic or physical variation, not "the old fixture only works if this one component lies about its geometry."

When a clean authoritative contract conflicts with incidental legacy assumptions:

1. Confirm what behavior is actually required.
2. Preserve the real invariant.
3. Adapt the consumer/fixture to the authoritative contract.
4. Use an explicit documented version bump when compatibility is genuinely versioned and a bump is permitted.
5. Never weaken validation merely to preserve an old implementation accident.

Do not treat historical pixels as sacred unless they are explicitly part of a compatibility/version contract.

---

## 8. Intra-task convergence / anti-thrash protocol

Persistence is required, but persistence must not become endless local patching.

If work begins oscillating between alternatives, or several successive narrow edits in the same failing area merely move the failure elsewhere, the coder must temporarily stop making local tweaks and re-baseline.

Strong signals include:

- repeatedly adding/removing/re-adding the same workaround;
- repeatedly moving coordinates or route points one failure at a time;
- creating multiple one-off variants for individual fixtures;
- each local fix causing a nearby form of the same failure;
- repeatedly changing the apparent owner of the problem;
- uncertainty whether the defect belongs to an authoritative contract, consumer/fixture, placement/routing, verifier/test, or environment/tooling.

At that point:

1. Stop editing temporarily.
2. Inspect the complete current diff.
3. Remove abandoned or superseded experiments when safe.
4. Inventory newly introduced variants/exceptions/workarounds and state why each legitimately exists.
5. Identify the remaining failing checks.
6. Classify each failure by ownership.
7. Form a root-cause hypothesis before making another production change.
8. Prefer one coherent repair over accumulating local exceptions.
9. Use focused instrumentation/diagnostics rather than blind coordinate/value nudging.
10. Resume implementation only after the boundary is understood well enough to justify the next change.

This does not permit abandoning a legitimate `BLOCKER` merely because debugging is difficult.

Governing principle:

> Retry aggressively, but change strategy when evidence shows the current strategy is not converging.

---

## 9. Feasibility / impossibility / challenge-the-premise protocol

Add a permanent rule stating that agents must **not assume every requested combination of constraints is satisfiable**.

When repeated evidence suggests the current constraint set may be impossible, stop brute-forcing and test feasibility directly where practical.

It is acceptable, and expected, to report that a specific realization cannot be achieved under the current constraints when that conclusion is supported by evidence.

Use explicit classifications where useful:

### `IMPLEMENTATION_FAILURE`

A solution probably exists, but the current implementation has not found or correctly implemented it.

### `REALIZATION_INFEASIBLE`

The specific realization/placement/layout/configuration cannot satisfy the required constraints, even though the broader feature/circuit may remain valid under another realization.

### `ARCHITECTURAL_CONTRADICTION`

The task's required constraints or permanent architecture rules conflict in a way that cannot be satisfied simultaneously without an explicit decision/change.

### `EXTERNAL_BLOCKER`

A required external capability, permission, environment, credential, or unavailable dependency prevents completion.

A demonstrated infeasible realization is a valid engineering result. It is not permission to claim the entire feature is impossible.

When reporting `REALIZATION_INFEASIBLE`, identify the smallest constraint or degree of freedom that must change where possible, such as:

- component placement;
- board size;
- package orientation;
- routing layer count;
- generated-link allowance;
- source/load configuration;
- another explicitly permitted realization parameter.

Never fabricate a workaround, weaken validation, or continue indefinite brute force merely because the task was expected to succeed.

For routing/placement problems, if reachability can be tested algorithmically, use that before prolonged manual coordinate experimentation. Treat package courtyards, unrelated copper plus required clearance, legal escape corridors, and board bounds as the actual obstacle set. Determine whether a legal path exists before repeatedly hand-nudging route points.

If no path exists under the current fixed placement but a small deterministic placement change produces a legal path while preserving real electrical/stable-identity requirements, report the original realization as infeasible and use the permitted minimum physical-layout change rather than inventing fake package geometry.

The coder is explicitly allowed to challenge an implementation premise when evidence supports doing so.

The architect is explicitly allowed to accept a well-supported "this exact realization cannot be done under these constraints" finding and choose the smallest permitted constraint change rather than demanding endless retries.

This rule complements, and does not weaken, the persistence protocol. Persistence means keep solving the engineering problem. It does not mean assume every initial realization must have a solution.

---

## 10. Correct stable-identity wording

Find the existing Development Rule approximately stating:

> Keep electrical node IDs stable and explicit.

Replace/clarify it so the permanent rule states:

- TroubleshootJS semantic identities such as board, component, pad, net, physical-part, block, and other durable IDs must be stable and explicit where required.
- CircuitJS analyzed/solver node numbers are transient implementation details.
- CircuitJS analyzed node numbers must never become durable TroubleshootJS identities, persistence keys, replay keys, physical identities, or cross-reanalysis references.
- Simulation bindings/adapters resolve current CircuitJS endpoints from durable semantic identity.

Preserve the implemented architecture in `docs/ARCHITECTURE.md`.

---

## 11. Remove or neutralize stale sequencing guidance

`AGENTS.md` is permanent governance. `docs/ROADMAP.md` owns current development sequencing.

Review older sections including:

- `Difficulty Scaling`
- `Implementation Strategy`
- `First-Proof Challenge`

Do not invent a new roadmap in `AGENTS.md`.

Keep timeless difficulty principles, but remove or clearly mark obsolete Beginner/Intermediate/Advanced/Expert labels as historical/non-authoritative if the current roadmap/profile contracts supersede them.

Remove or clearly mark the old preferred early milestone order and first-proof challenge as historical/completed context so no agent can interpret them as current sequencing authority.

Actual player-facing profile IDs, constraints, availability, and calibration belong to current roadmap/profile contracts.

---

## 12. Correct generated-link / zero-ohm fallback guidance

Reconcile any broad `AGENTS.md` guidance allowing jumper/zero-ohm fallback whenever one-sided routing fails with the current roadmap's evidence-gated physical-scalability sequence.

Permanent rule:

- A generated factory link/zero-ohm overpass is a real physical/electrical feature, not an invisible routing escape.
- Do not introduce one merely because the current router fails.
- Generated link fallback may be implemented/consumed only when the applicable roadmap/evidence gate authorizes it.
- Until such support is admitted, an unroutable candidate is rejected or its permitted realization parameters are changed rather than patched with hidden/unauthorized connectivity.
- Player-created jumper wires are a separate repair/tool concept.

Do not implement generated-link functionality in this governance task.

---

## 13. Reconcile large seed-sweep guidance with closed validation

Clarify older guidance recommending hundreds/thousands of seeded generator tests.

Permanent intent:

- Normal bounded implementation tasks use deterministic representative seeds covering touched families/topologies plus known boundary/regression seeds appropriate to risk.
- Hundreds/thousands-seed sweeps belong in dedicated stress/corpus/scalability work, release/readiness gates, or tasks whose closed validation set explicitly requires them.
- Do not turn every ordinary milestone into an unbounded random stress campaign.
- Large sweeps supplement architectural reasoning and targeted deterministic checks; they do not replace them.

Do not weaken any currently required deterministic verifier corpus.

---

## 14. Tighten hard-coded electrical-result guidance

Remove ambiguity from early wording permitting hard-coded fake meter readings "unless absolutely necessary."

Permanent intent:

- Normal player-facing electrical truth must never be manufactured by hard-coded expected answers.
- Meter readings, voltages, currents, continuity, diode behavior, functional states, and fault consequences must originate from CircuitJS or a clearly defined solver-derived/physical-state contract.
- Test doubles, developer-only fixtures, presentation placeholders, and non-electrical UI mock data are allowed only when they cannot masquerade as normal gameplay electrical truth.
- If CircuitJS cannot support required electrical behavior yet, defer/reject that gameplay feature rather than silently faking the result.

Preserve legitimate developer/test infrastructure.

---

## 15. Keep governance changes isolated from implementation milestones

Clarify that unrelated `AGENTS.md` governance changes should not be injected into an active production implementation merely because a useful policy idea arose during that task.

Finish the bounded implementation first, then perform governance cleanup separately unless the active task is genuinely blocked by a contradictory permanent rule.

Materially new user information affecting the active task may still be relayed under the existing delegation/patience protocol.

---

## 16. Reviewer and escalation model policy

The reviewer remains independent and read-only.

For architecture-sensitive review, prefer the highest-capability reasoning configuration available.

If explicit model selection is available for an escalation architect, request Luna MAX or another configuration explicitly designated as at least as capable for architectural reasoning.

Do not use Spark as the independent architectural reviewer, escalation architect, or final authority on architecture-sensitive findings.

---

## 17. Preserve the intended agent hierarchy

The preferred long-term workflow is:

```text
Primary architect
    -> Luna MAX primary coder
    -> independent reviewer
    -> primary architect diagnosis/classification
    -> if correction needed:
         Spark for exact MECHANICAL correction
         Luna MAX for REASONING_SENSITIVE correction
    -> independent reviewer when required
    -> primary architect final review
    -> normal completion protocol
```

The primary architect owns task definition, architecture, acceptance criteria, model selection, delegation, blocker classification, correction routing, final review, escalation, and publication/completion.

Luna MAX owns difficult implementation.
Spark owns only explicitly bounded mechanical implementation.
The reviewer owns independent inspection.

Do not blur these responsibilities merely to reduce elapsed time.

---

## 18. Avoid a giant AGENTS.md rewrite

This task is a targeted consistency pass, not a prose-modernization project.

Do not:

- rewrite the whole file;
- reorder every section;
- remove permanent project laws;
- alter accepted product architecture;
- change Task Completion Protocol publication/email behavior unless a real contradiction is found;
- modify roadmap sequencing;
- modify production source;
- begin the next milestone.

Where exact duplicate governance text exists, modest deduplication is allowed only when semantic behavior is unquestionably preserved.

If a proposed cleanup requires guessing whether an old rule remains product-authoritative, preserve it and report the ambiguity instead of guessing.

---

# Validation / completion

Because this is governance/documentation only:

- inspect the full final `AGENTS.md` diff;
- search for contradictions involving roadmap authority, stable identity, CircuitJS truth, multi-agent ownership, validation, model selection, feasibility, subagent patience, and task completion;
- confirm no production source changed;
- confirm the document does not accidentally authorize future-roadmap work;
- confirm substantive coder requests explicitly ask for Luna MAX;
- confirm Luna Extra High is not intentionally treated as equivalent to Luna MAX;
- confirm a platform-side silent downgrade is reported rather than triggering an endless respawn loop;
- confirm Spark cannot be selected merely for cost/speed;
- confirm Spark cannot make architecture/electrical/state/identity decisions;
- confirm correction routing is based on reasoning risk rather than diff size;
- confirm write ownership transfers sequentially before changing coders/models;
- confirm the architect is discouraged from routinely implementing delegated corrections itself;
- confirm repeated last-edit timestamp/git-status/diff/log/process liveness polling is explicitly prohibited;
- confirm occasional lightweight status checks remain permitted when genuinely useful;
- confirm long silence or unchanged files alone do not authorize killing/replacing an active subagent;
- confirm the convergence rule does not weaken persistence requirements;
- confirm the feasibility rule explicitly allows `REALIZATION_INFEASIBLE` as a valid supported engineering conclusion;
- confirm infeasibility does not become permission to call an entire feature impossible without evidence;
- confirm routing/placement reachability is preferred over prolonged manual coordinate guessing when applicable;
- confirm the compatibility rule does not authorize breaking explicit version/replay/stable-identity contracts;
- confirm `docs/ROADMAP.md` remains the sole sequencing authority.

Explicitly inspect the final Multi-Agent Development Protocol and verify that a future architect reading only `AGENTS.md` would understand:

1. Main substantive coder request = Luna MAX.
2. Architecture-sensitive correction = Luna MAX.
3. Mechanical, fully specified correction = Spark permitted.
4. Luna Extra High is not an intentional substitute for Luna MAX when MAX is required.
5. A platform-side spawn downgrade should be recorded, not fought with endless respawning.
6. Architect diagnoses and delegates corrections instead of routinely fixing production code itself.
7. Only one write-capable coder owns a given implementation surface at once.
8. An active coder should normally be left alone to work.
9. Repeatedly checking last-edit timestamps, git status, diffs, logs, or process activity merely to prove liveness is prohibited.
10. Approximately 30 minutes is the default minimum interval between purely liveness-oriented checks when no event-driven completion mechanism exists, with longer intervals appropriate for large tasks.
11. Repeated non-converging local edits require a re-baseline/root-cause diagnosis.
12. Agents are explicitly allowed to determine that a specific realization is infeasible under current constraints.
13. `REALIZATION_INFEASIBLE` should identify the smallest constraint that must change rather than triggering endless brute force.
14. A proven infeasible realization is a valid engineering result, not a failure to obey persistence rules.

Update the appropriate task/report documentation only as required by the current governance/task-completion protocol.

Use the normal review/commit/push/notification process in force on the accepted post-Task-43 baseline.

Then STOP.

Do not begin Task 44.
