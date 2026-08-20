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

The goal is to make `AGENTS.md` more internally consistent, safer and more efficient for future autonomous work, and explicit about model selection, investigation, delegation, correction routing, subagent patience, convergence, feasibility, and architecture preservation while retaining every valid existing project law.

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

## 2. Pre-implementation Luna MAX investigation and architect-synthesis protocol

For large, architecture-sensitive, cross-cutting, or unusually risky milestones, the primary architect should consider a short **read-only investigation phase before any production writer begins**.

The purpose is to discover hidden coupling, feasibility problems, regression risks, and architecture boundaries before a coder accumulates a large speculative diff.

This is not required for every tiny task. Do not create an investigation bureaucracy around trivial, already-understood changes.

### Investigator count

For a normal complex milestone, use **2 or 3 read-only Luna MAX investigators** when their perspectives are materially distinct.

Do not spawn a large crowd of redundant investigators merely because usage is available.

More than 3 requires a concrete justification from the task's independent risk dimensions. The architect must not create a committee whose reports cost more to reconcile than the uncertainty they remove.

Each investigator must be explicitly requested as **Luna MAX** under the model-selection policy. If the runtime silently downgrades the actual spawned configuration, record the mismatch rather than repeatedly respawning.

### Distinct investigator roles

Do not ask three agents the same vague question.

For a difficult milestone, useful investigation roles include:

#### Investigator A — Architecture and ownership

Inspect the relevant implementation and report:

- current authoritative owners of state/identity/behavior;
- relevant call paths and mutation boundaries;
- existing abstractions that must be reused;
- invariants that must remain true;
- likely integration points;
- architecture decisions that the implementation must not make accidentally;
- areas where a proposed change would create duplicate ownership or a parallel system.

#### Investigator B — Regression, coupling, and validation

Inspect:

- affected families/features;
- existing verifiers/tests/browser routes;
- hidden coupling and legacy fixtures;
- historical regressions relevant to the task;
- likely failure surfaces;
- the minimum closed validation set needed to prove the task without expanding into an unbounded matrix.

#### Investigator C — Adversarial feasibility / falsification

Actively try to prove that the initial implementation premise is wrong or incomplete.

Ask questions such as:

- Are any requested constraints mutually incompatible?
- Is a particular historical realization actually feasible under the new rules?
- Are we preserving an incidental implementation detail rather than a real contract?
- Is there an easier clean architecture that satisfies the same requirement?
- Could the proposed approach create an unobservable, unrepairable, nondeterministic, or physically impossible state?
- Is there a reachability, graph, routing, lifecycle, or ownership limitation that should be proven before implementation?

For geometry/routing work, explicitly consider whether the proposed fixed realization is routable before allowing prolonged coordinate experimentation.

For lifecycle/state work, explicitly consider whether the requested transitions can be atomic/coherent under current ownership.

For generation work, explicitly consider whether constraints can be satisfied without rejection explosions or hidden answer leakage.

At least one investigator on a genuinely architecture-sensitive task should have an explicit falsification/adversarial role rather than assuming the requested approach must succeed.

### Investigator restrictions

Investigators are read-only unless the task explicitly assigns an isolated diagnostic artifact that cannot affect production state.

Investigators must not:

- edit production source;
- independently implement their preferred design;
- create competing worktrees for the same implementation;
- silently change acceptance criteria;
- begin later roadmap work;
- vote on architecture as if majority opinion establishes correctness.

They return concise evidence and recommendations to the primary architect.

### Required-report barrier before decisions or implementation

When the architect delegates investigators, reviewers, or other subagents whose reports are designated as required for the current decision, those reports form a hard synchronization barrier.

The architect must wait until **all required active reports have arrived before making the architectural decision, finalizing synthesis, choosing an implementation strategy, classifying the next implementation chunk, or spawning a production coder**.

A partial set of reports is evidence, not permission to decide early. The architect must not conclude that it has "seen enough," pre-commit to a design, or start implementation while another required report is still outstanding.

If one required report arrives before the others, the architect may retain that completed report, but must continue silent, low-frequency completion monitoring until every remaining required report arrives unless:

- the completed report identifies a concrete blocker that requires immediate user action;
- the completed report proves the remaining delegated work is invalid or unsafe to continue; or
- the user explicitly changes the plan.

When all required reports have arrived, the architect must consider the full set together before making the decision. Investigator voting does not determine the answer; the architect still resolves conflicts from evidence.

A delegated investigation/review phase does not silently authorize the following implementation phase. The current task instructions or explicit user authorization still control what work may begin next.

### Architect synthesis is mandatory before implementation

Only after the required-report barrier is satisfied may the architect synthesize the investigation reports into **one authoritative implementation plan**.

The architect decides. Investigators advise.

The synthesis should identify:

- chosen architecture/integration boundary;
- rejected alternatives and why where material;
- real compatibility requirements;
- explicit non-goals;
- required invariants;
- feasibility findings or unresolved feasibility checks;
- ordered implementation chunks;
- dependencies between chunks;
- validation required after each meaningful boundary and at final completion.

If investigators materially disagree, the architect must resolve the disagreement from evidence or request one focused follow-up investigation. Do not hand contradictory plans to a coder and ask the coder to decide the architecture by accident.

If investigation discovers a genuine `REALIZATION_INFEASIBLE` or `ARCHITECTURAL_CONTRADICTION`, resolve that finding before spawning a production coder.

---

## 3. Ordered chunk decomposition and model routing

After investigation/synthesis on a complex task, decompose the accepted plan into the **smallest useful ordered chunks**, but do not confuse small size with low risk.

Every chunk must state:

- what it changes;
- what it must not change;
- its dependency on previous chunks;
- its authoritative contract/interface;
- how completion is verified;
- whether it is `SPARK_SAFE` or `LUNA_MAX_REQUIRED`.

### `SPARK_SAFE`

A chunk is Spark-safe only when all architecture and semantics are already decided and the remaining implementation is mechanical.

Examples:

- implement an already-designed immutable value object;
- migrate known call sites to a finalized API;
- add exact accessors/adapters dictated by an existing interface;
- add narrowly specified canaries/tests;
- mechanical provider/registry additions following a proven pattern;
- fix straightforward compiler errors after an accepted refactor;
- documentation/presentation-only changes with no simulation authority.

### `LUNA_MAX_REQUIRED`

A chunk requires Luna MAX whenever any meaningful reasoning remains about:

- architecture;
- electrical semantics;
- graph ownership;
- stable identity;
- lifecycle/state ordering;
- mutation atomicity/coherence;
- measurement behavior;
- physical geometry ownership;
- routing/connectivity semantics;
- procedural generation;
- fault/repair/retest semantics;
- deterministic replay/versioning;
- feasibility;
- ambiguous root cause;
- competing implementation approaches.

A chunk does not become Spark-safe merely because it is short.

### Sequential write execution

Write-capable chunks that affect the same milestone/worktree must run **sequentially** unless existing permanent rules prove they are genuinely disjoint.

Preferred execution:

```text
architect synthesis
-> chunk 1: Spark or Luna MAX according to risk
-> inspect/validate boundary as appropriate
-> chunk 2: Spark or Luna MAX according to risk
-> inspect/validate boundary as appropriate
-> ...
-> complete assembled candidate
```

The architect must explicitly transfer write ownership between coders.

Do not run multiple Spark coders against the same implementation surface simultaneously merely because the chunks are individually simple.

Do not let Spark infer architecture from a vague chunk description. If the architect cannot specify the chunk precisely enough for mechanical execution, classify it `LUNA_MAX_REQUIRED`.

---

## 4. GPT-5.3-Codex-Spark delegation policy

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

## 5. Fresh independent Luna MAX review after assembled implementation

For substantive implementation milestones, the final assembled candidate should receive an independent read-only review from a **fresh Luna MAX reviewer** that did not participate in the implementation or investigation synthesis whenever the runtime supports spawning such a reviewer.

Explicitly request Luna MAX. If the platform silently supplies a lower configuration, record the actual configuration rather than respawning indefinitely.

The reviewer receives:

- the original milestone requirements;
- architect acceptance criteria;
- permanent AGENTS.md invariants;
- relevant architecture/roadmap context;
- the final diff/candidate;
- validation evidence.

The reviewer should not be given the implementation agents' conclusions as assumptions it must preserve. It should independently try to falsify the result.

Review priorities include:

- architectural ownership violations;
- CircuitJS/electrical truth;
- stable identity;
- lifecycle/mutation coherence;
- temporary measurement cleanup;
- fault/repair/retest validity;
- deterministic generation/replay;
- physical/electrical correspondence;
- hidden answer/privacy leakage;
- fixture-specific hacks;
- missing validation;
- whether multiple individually-correct Spark chunks compose into an incorrect whole.

The reviewer is read-only and does not repair the candidate.

Reviewer findings return to the architect for classification and correction routing.

The architect still performs its own final review after reviewer acceptance/classification. Fresh Luna review does not replace architect responsibility.

---

## 6. Permanent correction-routing policy

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

## 7. Exclusive write ownership

Only one write-capable coder may own a given implementation surface at a time.

Before changing coders/models, the previous write-capable coder must have returned control. Ownership must transfer explicitly.

Never allow Luna MAX and Spark to edit the same worktree/implementation concurrently.
Never allow two Luna coders to edit the same implementation concurrently.

Parallel read-only investigation remains permitted when genuinely useful.

Model switching is sequential, not tag-team editing.

---

## 8. Subagent patience, silent completion monitoring, and anti-polling policy

Once implementation, review, correction, investigation, or escalation has been delegated and the assigned subagent is actively working, the architect should trust delegated ownership and allow the subagent to work.

The architect must not repeatedly inspect unrelated state merely to reassure itself that the subagent is still alive.

### Keep orchestration alive; check only completion status

Do **not** assume that a fully stopped or completely idle parent architect will automatically resume when a subagent finishes. In runtimes where automatic parent wake-up has not been explicitly demonstrated, ending the turn or stopping all architect activity can strand completed subagent reports until the user manually intervenes.

After delegating work whose result is required before the architect can make the next decision, the architect must keep the orchestration alive while doing the least possible work:

1. Use the longest practical sleep/wait interval supported by the runtime.
2. At the end of that interval, perform one lightweight check of delegated subagent completion state only.
3. If required reports remain outstanding, return immediately to waiting.
4. When all required reports are complete, collect and synthesize them together.

Pure completion checks should ordinarily occur **no more frequently than approximately once every 10 minutes**. Longer intervals are preferred for large investigations, implementations, builds, or reviews. This is a ceiling on unnecessary checking frequency, not a requirement to check exactly every 10 minutes.

While waiting, the architect must not:

- issue periodic user-facing status narration;
- say things such as "the worktree remains unchanged," "the agent is still working," "no new commits," "I continue to wait," or equivalent;
- inspect git status, diffs, file timestamps, logs, processes, partial source files, browser state, or generated artifacts merely to prove activity;
- request progress updates that are not needed for a decision;
- perform additional architecture analysis unrelated to a newly returned report;
- launch duplicate or speculative agents;
- start a coder before required investigator/reviewer reports are complete;
- make the pending architectural/implementation decision before all reports designated as required for that decision are complete;
- perform unrelated implementation while waiting;
- treat silence as permission to advance to the next phase.

The lightweight completion check itself should not be narrated to the user. Report only when:

- all required reports are complete and synthesis can begin;
- a subagent reports `BLOCKED`/`FAILURE`;
- a subagent requests clarification;
- a concrete destructive or out-of-scope risk appears;
- the user explicitly requests status; or
- another genuine decision-relevant synchronization event occurs.

Credit/compute conservation is part of this rule. The architect should spend the minimum cycles necessary to avoid becoming permanently asleep, not keep a running diary of elapsed time.

If **multiple required subagents** are outstanding, the completion of one does not authorize the architect to make the pending decision, finalize synthesis, or begin dependent implementation. Retain the completed result, then return to the silent completion-check loop until all required reports are available unless the new result establishes a concrete blocker or the user explicitly changes the plan.

If a runtime later provides a proven reliable automatic wake-up mechanism, event-driven waiting may replace periodic completion checks. Do not assume that capability merely because a subagent can continue running after the parent stops.

### Wasteful liveness polling

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

Do not terminate, replace, duplicate, or spawn another coder for the same implementation merely because the current coder has been quiet.

More frequent checking than the default cadence is permitted only for a concrete reason such as an explicit user status request, a known timeout/process failure, a subagent request/blocker, unexpected termination, suspected destructive/out-of-scope behavior, materially new user information, or an explicit synchronization point.

### Explicit authorization controls new work

The architect/coordinator is not authorized to invent work merely because it can see a plausible next step.

Do not start a new coder, add investigators, begin the next implementation chunk, continue into a later phase, edit production files directly, or expand the task unless that action is authorized by the current task instructions or by an explicit user instruction.

A request to investigate does not imply permission to implement.
A request to review does not imply permission to repair.
A completed subagent does not imply permission to launch the next phase.
Silence does not imply permission to continue.

When the currently authorized step is complete and no next action is authorized, report the result and stop. The architect's primary role is to delegate, synthesize, review, classify, and coordinate within the requested scope, not to keep itself busy.

Governing principle:

> Delegate, wait quietly, check only completion state at a conservative cadence, and inspect the complete required evidence set once it exists. Do not pay a senior architect to narrate waiting or stare at file modification timestamps.

---

## 9. Conservative parallel-subagent policy

Review the existing `Parallel Subagent Policy` and change any wording that implies the architect SHOULD parallelize merely because it might save elapsed time.

Permanent intent:

- Parallelism is optional, not a goal.
- Correctness, clear ownership, and integration simplicity outrank throughput.
- A small number of **read-only Luna MAX investigators** may work in parallel before implementation when their scopes are distinct and useful.
- Normal complex-task investigation should usually be capped at 2–3 investigators.
- Write-capable parallel implementation is exceptional and requires clearly disjoint ownership with no shared abstraction or unfinished dependency.
- Sequential write implementation is the safe default when uncertainty exists.
- Never create extra agents merely to maximize utilization.
- Exactly one roadmap milestone remains active regardless of subagent count.

Preserve the existing prohibition against concurrent writers touching the same implementation/shared abstraction.

---

## 10. Architecture over incidental compatibility

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

## 11. Intra-task convergence / anti-thrash protocol

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

## 12. Feasibility / impossibility / challenge-the-premise protocol

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

## 13. Correct stable-identity wording

Find the existing Development Rule approximately stating:

> Keep electrical node IDs stable and explicit.

Replace/clarify it so the permanent rule states:

- TroubleshootJS semantic identities such as board, component, pad, net, physical-part, block, and other durable IDs must be stable and explicit where required.
- CircuitJS analyzed/solver node numbers are transient implementation details.
- CircuitJS analyzed node numbers must never become durable TroubleshootJS identities, persistence keys, replay keys, physical identities, or cross-reanalysis references.
- Simulation bindings/adapters resolve current CircuitJS endpoints from durable semantic identity.

Preserve the implemented architecture in `docs/ARCHITECTURE.md`.

---

## 14. Remove or neutralize stale sequencing guidance

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

## 15. Correct generated-link / zero-ohm fallback guidance

Reconcile any broad `AGENTS.md` guidance allowing jumper/zero-ohm fallback whenever one-sided routing fails with the current roadmap's evidence-gated physical-scalability sequence.

Permanent rule:

- A generated factory link/zero-ohm overpass is a real physical/electrical feature, not an invisible routing escape.
- Do not introduce one merely because the current router fails.
- Generated link fallback may be implemented/consumed only when the applicable roadmap/evidence gate authorizes it.
- Until such support is admitted, an unroutable candidate is rejected or its permitted realization parameters are changed rather than patched with hidden/unauthorized connectivity.
- Player-created jumper wires are a separate repair/tool concept.

Do not implement generated-link functionality in this governance task.

---

## 16. Reconcile large seed-sweep guidance with closed validation

Clarify older guidance recommending hundreds/thousands of seeded generator tests.

Permanent intent:

- Normal bounded implementation tasks use deterministic representative seeds covering touched families/topologies plus known boundary/regression seeds appropriate to risk.
- Hundreds/thousands-seed sweeps belong in dedicated stress/corpus/scalability work, release/readiness gates, or tasks whose closed validation set explicitly requires them.
- Do not turn every ordinary milestone into an unbounded random stress campaign.
- Large sweeps supplement architectural reasoning and targeted deterministic checks; they do not replace them.

Do not weaken any currently required deterministic verifier corpus.

---

## 17. Tighten hard-coded electrical-result guidance

Remove ambiguity from early wording permitting hard-coded fake meter readings "unless absolutely necessary."

Permanent intent:

- Normal player-facing electrical truth must never be manufactured by hard-coded expected answers.
- Meter readings, voltages, currents, continuity, diode behavior, functional states, and fault consequences must originate from CircuitJS or a clearly defined solver-derived/physical-state contract.
- Test doubles, developer-only fixtures, presentation placeholders, and non-electrical UI mock data are allowed only when they cannot masquerade as normal gameplay electrical truth.
- If CircuitJS cannot support required electrical behavior yet, defer/reject that gameplay feature rather than silently faking the result.

Preserve legitimate developer/test infrastructure.

---

## 18. Keep governance changes isolated from implementation milestones

Clarify that unrelated `AGENTS.md` governance changes should not be injected into an active production implementation merely because a useful policy idea arose during that task.

Finish the bounded implementation first, then perform governance cleanup separately unless the active task is genuinely blocked by a contradictory permanent rule.

Materially new user information affecting the active task may still be relayed under the existing delegation/patience protocol.

---

## 19. Reviewer and escalation model policy

The reviewer remains independent and read-only.

For substantive architecture-sensitive final review, explicitly request a **fresh Luna MAX reviewer** where the runtime supports it.

If explicit model selection is available for an escalation architect, request Luna MAX or another configuration explicitly designated as at least as capable for architectural reasoning.

If the platform silently downgrades a requested reviewer/escalation configuration, record the actual configuration rather than entering an endless respawn loop.

Do not use Spark as the independent architectural reviewer, escalation architect, or final authority on architecture-sensitive findings.

---

## 20. Preserve the intended agent hierarchy

For simple tasks, do not manufacture unnecessary ceremony. A direct architect -> appropriate coder -> reviewer -> architect path remains valid.

For complex architecture-sensitive tasks, the preferred long-term workflow is:

```text
Primary architect
    -> 2-3 distinct read-only Luna MAX investigators when useful
         A: architecture/ownership
         B: regressions/coupling/validation
         C: adversarial feasibility/falsification
    -> silent low-frequency completion checks until ALL required reports are complete
    -> architect synthesis into one authoritative plan
    -> ordered implementation chunks
         Spark for `SPARK_SAFE` chunks
         Luna MAX for `LUNA_MAX_REQUIRED` chunks
         write ownership sequential
    -> complete assembled candidate
    -> fresh independent Luna MAX reviewer
    -> silent low-frequency completion checks until required review reports are complete
    -> primary architect diagnosis/classification
    -> if correction needed:
         Spark for exact MECHANICAL correction
         Luna MAX for REASONING_SENSITIVE correction
    -> independent review when required
    -> primary architect final review
    -> normal completion protocol
```

The primary architect owns task definition, architecture, acceptance criteria, investigation scopes, synthesis, model selection, delegation, blocker classification, correction routing, final review, escalation, and publication/completion.

Luna MAX investigators own independent read-only analysis.
Luna MAX coders own difficult/reasoning-sensitive implementation.
Spark owns only explicitly bounded mechanical implementation.
The fresh reviewer owns independent hostile inspection of the assembled result.

Do not blur these responsibilities merely to reduce elapsed time.

---

## 21. Avoid a giant AGENTS.md rewrite

This task is a targeted consistency pass, not a prose-modernization project.

Do not:

- rewrite the whole file;
- reorder every section without need;
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
- search for contradictions involving roadmap authority, stable identity, CircuitJS truth, multi-agent ownership, validation, model selection, investigation, feasibility, subagent patience, and task completion;
- confirm no production source changed;
- confirm the document does not accidentally authorize future-roadmap work;
- confirm substantive coder requests explicitly ask for Luna MAX;
- confirm complex-task investigators explicitly request Luna MAX;
- confirm Luna Extra High is not intentionally treated as equivalent to Luna MAX;
- confirm a platform-side silent downgrade is reported rather than triggering an endless respawn loop;
- confirm Spark cannot be selected merely for cost/speed;
- confirm Spark cannot make architecture/electrical/state/identity decisions;
- confirm implementation chunks are classified by reasoning risk rather than size;
- confirm write ownership transfers sequentially before changing coders/models;
- confirm read-only parallel investigation is allowed without authorizing parallel writes;
- confirm normal complex tasks are generally capped at 2–3 investigators rather than encouraging unlimited fan-out;
- confirm at least one investigator may be assigned adversarial feasibility/falsification on architecture-sensitive work;
- confirm **all reports designated as required for a decision must be complete before the architect makes that decision, finalizes synthesis, or launches dependent implementation**;
- confirm a partial report set cannot be treated as "enough" to decide early;
- confirm the architect must synthesize investigator findings into one plan before implementation;
- confirm contradictory investigator opinions are not handed unresolved to the coder;
- confirm a fresh independent Luna MAX reviewer is requested for substantive assembled implementations where supported;
- confirm the reviewer is read-only and does not replace architect final responsibility;
- confirm the architect is discouraged from routinely implementing delegated corrections itself;
- confirm the architect does not assume that stopping completely will automatically wake it when subagents finish;
- confirm the architect keeps orchestration alive using silent, lightweight completion-state checks;
- confirm pure completion checks occur no more frequently than approximately every 10 minutes absent a concrete reason, with longer intervals preferred for long tasks;
- confirm periodic narration such as "the worktree remains unchanged," "the agent is still working," or equivalent is explicitly prohibited;
- confirm waiting is intended to conserve compute/credits rather than spend architect cycles observing elapsed time;
- confirm repeated last-edit timestamp/git-status/diff/log/process liveness polling is explicitly prohibited;
- confirm completion checks inspect subagent status only rather than unrelated repository state;
- confirm if multiple required subagents are outstanding, one completion causes the architect to return to silent waiting rather than make the pending decision early;
- confirm long silence or unchanged files alone do not authorize killing/replacing an active subagent;
- confirm the architect cannot invent new work, agents, implementation phases, or production edits merely because it sees a plausible next step;
- confirm investigation does not imply implementation authorization, review does not imply repair authorization, and silence does not imply permission to continue;
- confirm the convergence rule does not weaken persistence requirements;
- confirm the feasibility rule explicitly allows `REALIZATION_INFEASIBLE` as a valid supported engineering conclusion;
- confirm infeasibility does not become permission to call an entire feature impossible without evidence;
- confirm routing/placement reachability is preferred over prolonged manual coordinate guessing when applicable;
- confirm the compatibility rule does not authorize breaking explicit version/replay/stable-identity contracts;
- confirm `docs/ROADMAP.md` remains the sole sequencing authority.

Explicitly inspect the final Multi-Agent Development Protocol and verify that a future architect reading only `AGENTS.md` would understand:

1. Main substantive implementation = Luna MAX unless a chunk is explicitly proven Spark-safe.
2. Large/risky tasks may use 2–3 distinct read-only Luna MAX investigators before implementation.
3. Investigators advise; the architect synthesizes and decides.
4. At least one investigator may be tasked specifically with falsifying feasibility/assumptions.
5. **Every report designated as required for the pending decision must arrive before that decision or dependent implementation is allowed.**
6. A partial set of required reports never authorizes an early architectural conclusion merely because the architect believes it already knows the answer.
7. The architect decomposes the accepted plan into ordered chunks with explicit contracts and validation.
8. Spark handles only fully specified mechanical chunks/corrections.
9. Architecture-sensitive chunks/corrections use Luna MAX.
10. Luna Extra High is not an intentional substitute for Luna MAX when MAX is required.
11. Platform-side spawn downgrade should be recorded, not fought with endless respawning.
12. Only one write-capable coder owns a given implementation surface at once.
13. Fresh independent Luna MAX review attacks the complete assembled candidate after substantive implementation.
14. Architect diagnoses and delegates corrections instead of routinely fixing production code itself.
15. The architect does not completely stop and assume a subagent completion will wake it automatically.
16. The architect keeps the orchestration alive through silent, low-frequency completion-state checks only.
17. With multiple required active reports, each partial completion is followed by quiet waiting until the complete required evidence set exists.
18. Repeatedly checking last-edit timestamps, git status, diffs, logs, or process activity merely to prove liveness is prohibited.
19. Periodic "still waiting" or "worktree unchanged" narration is prohibited.
20. Approximately 10 minutes is the default minimum interval between purely completion-oriented checks, with longer intervals appropriate for large tasks.
21. The architect performs only the work explicitly authorized by the current task/user and does not invent follow-on work merely to stay busy.
22. Repeated non-converging local edits require a re-baseline/root-cause diagnosis.
23. Agents are explicitly allowed to determine that a specific realization is infeasible under current constraints.
24. `REALIZATION_INFEASIBLE` should identify the smallest constraint that must change rather than triggering endless brute force.
25. A proven infeasible realization is a valid engineering result, not a failure to obey persistence rules.
26. Parallel read-only analysis is a way to reduce uncertainty, not an excuse to maximize agent count.

Update the appropriate task/report documentation only as required by the current governance/task-completion protocol.

Use the normal review/commit/push/notification process in force on the accepted post-Task-43 baseline.

Then STOP.

Do not begin Task 44.