# Audit-Driven Roadmap Redesign Report

Date: 2026-08-18

Task type: architecture planning and roadmap synthesis only.

Baseline: clean local master synchronized with origin/master at
92df2401d865f81e9c68fb1e1b503a6b92fa8051 after a fresh origin fetch.

Scope: documentation only. Task 39 implementation, Relay Driver work, and all
production electrical, PCB, UI, validator, generator, and CircuitJS behavior
remain untouched.

## Summary and current decision

The old provisional future queue added Relay, Regulator, Comparator, and Timer
as additional monolithic family generators before addressing the architecture
risks identified by the four completed audits. The redesigned roadmap preserves
completed Tasks 1–38, retires and remaps only the unstarted former Task 39–69
sequence, and makes the immediate next milestone:

**Task 39 — Player-Operable Functional Inputs and Customer Retest Contract**

Relay Driver is deferred and transformed into a reusable relay block/composed
load proof at revised Task 66.

The new critical path is:

    player-operable input and retest
      → physical fault-locus/serviceability admission
      → diagnostic-solvability verification
      → one existing-family diversity proof
      → package/interaction envelope truth
      → functional blocks, typed domains, versioned named seeds
      → bounded assembler and one real two-block challenge
      → intent values, purposeful support, composed rejection
      → measured physical-layout scalability
      → typed difficulty evidence
      → EASY/MEDIUM
      → Resources, Settings, main menu, results
      → desktop alpha gate
      → bounded block/repair/instrument expansion and HARD
      → desktop beta gate

No newly selected implementation milestone was started.

## Sources reviewed

The primary architect read and reconciled:

- AGENTS.md;
- docs/ROADMAP.md;
- docs/ARCHITECTURE.md;
- docs/CODEX_TASK_REPORT.md;
- docs/research/TROUBLESHOOTING_REALISM_SOLVABILITY_AUDIT.md;
- docs/research/PROCEDURAL_GENERATION_SCALABILITY_AUDIT.md;
- docs/research/PCB_ROUTING_SCALABILITY_AUDIT.md;
- docs/research/COMPONENT_VISUAL_REALISM_AUDIT.md;
- recent repository history through the Task 38 correction and audit/UI
  integration;
- the current generated-family, Quick Play, player-control, measurement,
  package/PCB, and workbench-shell paths;
- war/tsj-workbench-ui.js and its current TOOLS, mock SHOP, RESOURCES, and
  SETTINGS behavior.

The audits were written against slightly different pre-integration baselines.
Current source/history was used to reconcile those differences. Task 38 is
complete, NMOS is now the sixth Quick Play family, and the current normal fault
surface is 14 routes rather than the earlier five-family/11-route audit count.

## Required specialist subagents

Five bounded read-only specialists ran in parallel and modified no files:

1. Diagnostic solvability/gameplay realism: player operations, retest,
   physical fault ownership, ambiguity, and difficulty fairness.
2. Procedural generation/composition: functional blocks, stable namespaces,
   typed ports/domains, named randomness, values, support blocks, and the first
   two-block proof.
3. PCB placement/routing scalability: envelope, stress corpus, region
   placement, net trees, rerouting, link/layer gates, and physical difficulty.
4. Physical/visual realism: cosmetic versus architecture prerequisites,
   continuous LED state, package envelopes, and safe visual sequencing.
5. Current roadmap/product shell: old-task disposition, main menu, difficulty,
   Shop/Resources/Settings, session flow, and alpha readiness.

Their reports were decision-oriented and independently agreed that old Relay-
first sequencing should not remain immediate.

One independent synthesis reviewer is required after the complete roadmap draft.
Its final disposition is recorded in the review section after that review.

## Major audit conflicts reconciled

### Solvability versus composition

Diagnostic fairness comes first. The first composition contracts depend on a
player-operable input/retest boundary and a distinct diagnostic-solvability
admission gate. Composition cannot treat hidden validator command state as a
legal player observation.

### Architecture contracts versus playable progress

The roadmap uses bounded contracts followed quickly by a real proof:

    descriptor and ports
      → typed domains
      → versioned request/seeds
      → bounded assembler
      → composed controlled-indicator challenge

The existing leaf families remain regressions/adapters. They are not rewritten
wholesale and no unrestricted generator is introduced.

### Physical envelope versus cosmetic polish

Package/body/lead/selection/probe reconciliation is an early architecture gate
before denser placement and new packages. Static axial/PCB polish and dynamic
LED presentation are post-alpha bounded milestones; they do not displace
fairness/composition work.

### Composition versus routing scale

The first small two-block proof may use the currently bounded small-board
physical path and makes no medium-board claim. Before broader composed boards,
the roadmap requires envelope truth, deterministic stress telemetry, region
placement, and evidence-driven routing decisions. Net trees, rerouting,
factory links, and two-sided routing are conditional on measured failure modes.

### Difficulty versus product UI

Difficulty evidence/types arrive only after diagnostic and layout metrics
exist. EASY/MEDIUM become selectable after calibration. HARD and PSYCHOTIC are
separate later admission milestones. The main menu is scheduled only after it
can control a versioned replayable challenge request and two real profiles.

### Product shell versus simulation ownership

TOOLS remains the real workbench surface. Resources and Settings become real
before alpha. The current mock Shop is hidden or unmistakably read-only before
alpha, then later becomes an authoritative catalog/acquisition surface backed
by existing physical catalogs/runtime capabilities. Shell JavaScript does not
own session, inventory, difficulty, or electrical state.

## Old roadmap assumptions changed

The redesign rejects these former assumptions:

- another complete family generator should be the immediate next task;
- multi-rail semantics can wait until after a Regulator family exists;
- composition can remain a single late Task 56;
- purposeful auxiliary circuitry should precede composition;
- larger-board routing can remain one late broad Task 59;
- difficulty is a late game-layer label;
- a new monolithic Challenge Generator is needed after the existing pipeline;
- Shop’s current hard-coded JavaScript catalog/cart is a path toward gameplay;
- opening directly into random Quick Play is the mature launch flow;
- oscillator/timer content may be added before player stimulus and suitable
  temporal instruments;
- multiple faults or tangled traces are a legitimate shortcut to maximum
  difficulty.

The revised roadmap contains a complete former Task 39–69 migration table.

## New architecture and decision gates

The roadmap adds explicit gates for:

- player-operable diagnostic and customer-retest actions;
- physical fault-locus and serviceability admission;
- family-agnostic diagnostic distinguishability and repair reachability;
- package/footprint/body/selection/probe envelope truth;
- stable block-local/global identity;
- typed electrical domains and compatibility preflight;
- versioned root descriptors and named deterministic sub-seeds;
- one-runtime bounded composition;
- a real two-block controlled-indicator proof;
- intent-derived values and purposeful support;
- staged composed acceptance/rejection;
- measured layout telemetry and supported physical corpus;
- region placement plus conditional net-tree/reroute/link/two-sided work;
- requested-versus-computed difficulty agreement;
- desktop alpha readiness;
- high-energy/dynamic feature admission.

Conditional milestones must record an evidence-based start or skip decision.
Two-sided routing is not scheduled automatically. If one-sided placement/tree/
reroute plus a capped visible-link policy satisfies the target corpus, layers
remain deferred.

## Main-menu timing decision

A proper main menu is a desktop-alpha prerequisite, but not the immediate next
task. It becomes eligible at revised Task 62 after:

- versioned challenge/replay identity;
- diagnostic admission;
- the first composed challenge;
- EASY and MEDIUM as real generation profiles;
- Resources and Settings v1.

The menu owns navigation and a typed ChallengeLaunchRequest only. It supports
Start Repair, supported difficulty, seed/replay entry, Resources, Settings,
and developer/debug separation. It does not own family selection rules,
electrical generation, fault choice, or repair completion.

The explicit mature session loop becomes:

    launch
      → menu
      → supported difficulty or replay
      → generation/admission
      → service ticket/workbench
      → troubleshooting and repair
      → player-operated customer retest
      → Finish Job
      → results
      → next/replay/menu

## Difficulty-system decision

The shared architecture is:

    GenerationRequest
      → versioned DifficultyProfile / GenerationConstraints
      → generation and solver/physical admission
      → ChallengeComplexityMetrics / DifficultyAssessment
      → accept or reject

There are no four separate generators.

- EASY and MEDIUM are calibrated and admitted at revised Task 59.
- HARD is admitted at Task 80 after multi-block, multi-rail, repair, and
  appropriate temporal/instrument capabilities.
- PSYCHOTIC is admitted at Task 85 after intermittent, stress/damage, service
  history, and updated corpus evidence.
- Unsupported profiles are absent, not cosmetic disabled labels.
- PSYCHOTIC means the maximum legitimate currently supported complexity; it
  does not require multiple simultaneous faults.

Difficulty can vary functional blocks, plausible physical owners, domains/
rails, parallel paths, isolation actions, input transitions, temporal work,
instruments, purposeful auxiliaries, complaint specificity, wrong-repair
consequences, and measured layout complexity. It cannot hide ordinary physical
information, remove necessary controls, require unavailable observations, use
nonsense values, or make targets/copper unreadable.

## SHOP, RESOURCES, SETTINGS, and TOOLS decisions

### TOOLS

Already the real workbench/instrument surface. It remains view orchestration and
does not become a parallel instrument implementation.

### RESOURCES

Becomes functional before alpha at Task 60 with generic meter safety/use,
color-code and polarity/package identification, isolation guidance, and public
catalog/datasheet-style references. It may provide optional Easy teaching help
but never reads hidden fault/private original data.

### SETTINGS

Becomes functional before alpha at Task 61 for presentation/accessibility
preferences such as audio, contrast/color, text/UI scale, reduced motion, and
safe interaction preferences. Difficulty and simulation semantics remain in
typed challenge/instrument contracts.

### SHOP

The current hard-coded mock cart is not gameplay. Task 61 hides it or makes it
unmistakably read-only before alpha. Task 70 later makes it an authoritative
catalog/replacement source backed by shared physical catalogs,
PhysicalBoardRuntime, and workbench capabilities. Shop never mutates CircuitJS
directly. Economy/pricing is a separately deferred decision requiring persistent
inventory/progression and a real gameplay reason.

No additional permanent tabs are warranted now. Results are a session state.

## Alpha-readiness decision

Revised Task 63 is the first desktop-alpha gate. It requires:

- every selectable route solver-backed, diagnostically admitted, physically
  owned, repairable, and visibly retestable;
- player controls for every required functional state;
- visible versioned seed/replay;
- real EASY and MEDIUM profiles;
- menu-to-results-to-menu flow and graceful generation rejection;
- no normal debug/fault/private-value leakage;
- Resources and Settings v1;
- no prominent fake Shop/cart;
- envelope/readability/probe-access checks and deterministic telemetry;
- basic desktop keyboard/focus/accessibility sanity.

Alpha does not require full Shop/economy, scoring, save/resume, mobile,
multilayer, scope unless an exposed profile needs it, all four difficulty
levels, multiple faults, or photorealistic art.

## Release-stage decision

Developer builds remain milestone/test surfaces rather than a release claim.
Task 63 is the bounded desktop-alpha gate. Task 80(A) is the separate desktop-
beta gate after an accepted alpha, admitted HARD, the bounded block/repair/
instrument expansion, and documented beta reliability evidence. Tasks 81–89
are post-beta work and do not retroactively become beta prerequisites.

A mature/1.0 gate is deliberately not numbered before beta telemetry exists.
Before one is scheduled, the roadmap requires an explicit advertised four-mode
surface decision, release/accessibility/responsive scope, persistence/share
policy, packaging/attribution, support bounds, and honest Shop/economy decision.
Optional scoring, multi-fault, economy, accounts, or mobile are requirements
only if that product surface advertises them.

## Deliberately deferred capabilities

Deferred until explicit prerequisites/evidence exist:

- Relay, Regulator, Comparator/Sensor, and Timer/Oscillator as reusable blocks
  rather than more monolithic families;
- player jumper, trace cut, and trace repair until stable physical ownership;
- rail-short/high-current faults until source/current-limit consequences;
- scope/frequency before waveform-dependent normal challenges;
- capacitance measurement unless a proven solvability need and CircuitJS
  stimulus method exist;
- intermittent faults, expanded damage, thermal, persistent customer returns,
  scoring, save/share, and advanced profiles;
- multiple faults until single-fault Psychotic content is mature (conditional
  Task 86);
- full economy until persistence/progression gives purchasing a purpose;
- two-sided routing, vias, SMD, planes, and richer layers until layout corpus
  evidence demands them;
- PNP/PMOS, power supply, motor/fan, logic, state-machine, and appliance-scale
  devices until composition/domain/solvability/physical gates pass;
- advanced visual package detail, thermal camera, logic analyzer, and
  multi-channel scope until a real challenge requires them.

## Files changed

- docs/ROADMAP.md — completed-history ledger, audit-driven future-roadmap reset,
  numbered milestones, dependency/decision gates, difficulty/product-shell
  strategy, and old-future-task migration map.
- docs/CODEX_TASK_REPORT.md — this planning handoff.

docs/ARCHITECTURE.md was intentionally not changed. No implemented architecture
or production behavior changed; planned architecture is described only as
future roadmap direction.

## Validation plan and results

Required documentation-only checks:

- fresh fetch and local master/origin/master equality: passed at task start;
- no production-code changes: passed; only docs/ROADMAP.md and this report
  differ;
- completed Tasks 1–38 preserved and Task 38 complete: roadmap ledger plus
  detailed Task 28–38 history; structurally rechecked;
- no planned capability described as implemented: passed primary/reviewer
  inspection;
- every numbered milestone has purpose, dependencies, bounded goals, explicit
  non-goals, acceptance, invariants, source requirement, and unlock/eligibility:
  passed structural check, including Task 80(A);
- no circular dependency: passed; all explicit task/status dependencies point
  backward in the revised order;
- one unambiguous immediate next milestone: Task 39;
- explicit main-menu, four-mode difficulty, Shop/Resources/Settings, alpha,
  beta, and mature-stage decision paths: present;
- every audit/product requirement scheduled, combined, deferred with reason, or
  rejected with reason: present in milestones, product decisions, migration
  map, and deferred matrix;
- working-tree git diff, git status, and git diff --check: passed; final cached
  diff check remains for staging;
- production build/browser suites: intentionally not run because this task
  changes documentation only.

## Review protocol

Specialist phase: five required read-only reports complete.

Synthesis reviewer: one independent read-only review completed against all four
audits, the current architecture, the integrated shell, and the task brief.

Reviewer result: `FAIL` with two substantive findings: the draft lacked an
explicit beta/mature release-stage boundary, and PSYCHOTIC was unnecessarily
blocked by scoring/save/share tasks.

Bounded correction pass: complete. Added Task 80(A) and a release-stage matrix;
moved PSYCHOTIC to Task 85 after the actual diagnostic/dynamic prerequisites,
made multiple-fault work conditional Task 86, and moved scoring/save/share to
Tasks 87–89. No second review loop was run, as required.

Primary architect review rounds: one draft review plus one bounded substantive
correction pass complete.

Primary architect final result: `FINAL PASS`. The reviewer’s two findings are
resolved, the dependency sequence is acyclic and bounded, Task 39 is the sole
immediate milestone, and no implementation work was started.

Escalation architect: not indicated; use only if the single synthesis review
finds an unresolved substantive architectural contradiction.

## Known limitations and decision notes

- The original audits predate parts of Task 38/integration; the redesign uses
  current source/history where counts or state differed.
- A normal-player diagnostic verifier should use bounded action templates, not
  claim exhaustive theorem proving.
- The physical support envelope must be established by telemetry rather than a
  universal component count.
- Conditional routing tasks may be skipped by evidence.
- Existing leaf families remain valid regression/guided content even when they
  are not yet rich enough for higher difficulty.
- Final commit SHA and push result are necessarily established after this file
  is committed. The authoritative exact values will be reported in the final
  user response and repository history while preserving the one-final-commit
  rule.

## Completion record

Planned commit message: Redesign roadmap around audit findings

Final commit SHA: PENDING FINAL COMMIT

Push result: PENDING FINAL COMMIT

Remote: origin

Branch/upstream: master -> origin/master

Next roadmap milestone: Task 39 — Player-Operable Functional Inputs and
Customer Retest Contract. It is identified only and was not started.
