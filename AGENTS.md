# AGENTS.md — TroubleshootJS

## Project Mission

TroubleshootJS is an interactive electronics troubleshooting simulator built around CircuitJS.

The goal is NOT merely to teach schematic reading.

The goal is to recreate the reasoning process of troubleshooting a real PCB on a workbench:

1. Receive an incomplete customer complaint.
2. Inspect an unfamiliar PCB.
3. Determine what the board and its subsystems do.
4. Decide what measurements are useful.
5. Probe the actual board rather than a neat schematic.
6. Isolate the failed subsystem/component.
7. Remove or isolate components when appropriate.
8. Replace components or repair traces.
9. Power the board back up and verify the repair.

The simulator should reward electrical reasoning and discourage brute-force probing.

---

# Core Design Principle

CircuitJS is the electrical simulation backbone.

TroubleshootJS adds additional layers around CircuitJS:

- procedural circuit generation
- procedural PCB rendering
- PCB-to-simulation node mapping
- customer complaints
- hidden faults
- multimeter/scope interaction
- component removal/replacement
- jumper wires and trace cutting
- component stress and secondary failures
- scoring and troubleshooting workflow

Do NOT replace CircuitJS electrical behavior with hard-coded fake meter readings unless absolutely necessary.

Whenever practical:

USER ACTION
    ↓
MODIFY ELECTRICAL GRAPH
    ↓
CIRCUITJS SIMULATES RESULT
    ↓
MEASUREMENT/BEHAVIOR CHANGES NATURALLY

The simulation state should be the source of electrical truth.

---

# Important Non-Goal

Do NOT turn TroubleshootJS into KiCad, Altium, or a general-purpose PCB design package.

Generated PCBs only need to be:

- electrically representative
- visually believable
- easy enough for the software to generate
- difficult enough for a human to visually trace
- interactive

They do NOT initially need to:

- satisfy manufacturing design rules
- support arbitrary layer stacks
- model electromagnetic fields
- produce Gerber files
- perform professional autorouting
- reproduce GHz signal integrity

Prefer a useful training simulator over technically perfect PCB CAD.

---

# Architecture

Keep major systems separated.

Prefer modules/services roughly corresponding to:

1. Simulation Adapter
2. Circuit Generator
3. Circuit Validator
4. Fault Engine
5. PCB Generator
6. PCB Router
7. PCB Renderer
8. Probe/Instrument System
9. Board Modification System
10. Component Stress/Damage System
11. Challenge/Scenario Generator
12. Scoring/History System

Do not create unnecessary coupling between these systems.

The PCB renderer must not itself determine electrical behavior.

The generator must produce a logical circuit/netlist first.

The PCB is generated FROM the circuit.

---

# Procedural Circuit Generator

Do NOT attempt unrestricted random electronic circuit generation.

Use constrained functional families and reusable topology modules.

Examples of functional families:

- LED indicator/control
- transistor switch
- MOSFET switch
- relay driver
- regulator
- sensor input
- comparator
- amplifier
- oscillator
- timer
- motor/fan driver
- logic circuit
- power supply
- buck converter
- memory/data interface

A functional family describes WHAT the circuit accomplishes.

Each family may contain multiple valid implementations.

Example: "controlled LED"

Possible topologies include:

- direct switch
- NPN low-side driver
- PNP high-side driver
- NMOS low-side driver
- PMOS high-side driver
- relay-controlled LED
- logic/comparator-controlled LED

The player should not know which topology was selected.

---

# Circuit Randomization

Randomization should occur at multiple levels.

## Functional topology

Different valid implementations of the same customer-visible function.

## Parameters

Examples:

- supply voltage
- resistor values
- capacitor values
- transistor types
- MOSFET types
- pull-up/pull-down values
- component packages
- timing constants

Values must remain electrically sensible unless an intentionally incorrect value is the injected fault.

## Auxiliary circuitry

Generated boards should frequently include healthy circuitry unrelated or only indirectly related to the reported problem.

Examples:

- status LEDs
- buzzers
- reverse-polarity protection
- decoupling
- unused headers
- secondary outputs
- sensor inputs
- power indicators
- additional regulated rails
- filtering
- protection networks

These circuits are NOT automatically faults.

Their purpose is to make boards realistic and force users to determine which subsystem matters.

## Layout

Randomize:

- component placement
- component rotation
- reference designators where practical
- trace routing
- board dimensions/shape within reasonable limits
- connector placement
- test point placement

A user should not be able to memorize a board layout and immediately know where to probe.

---

# Generation Pipeline

Prefer this pipeline:

1. Select functional family.
2. Select valid topology.
3. Generate required components.
4. Calculate valid component values.
5. Add optional supporting/auxiliary circuits.
6. Build electrical netlist.
7. Validate healthy circuit through simulation.
8. Select a compatible fault.
9. Inject fault.
10. Simulate faulty circuit.
11. Verify that the fault produces a meaningful symptom.
12. Reject invalid/uninteresting generations.
13. Generate PCB footprints.
14. Place PCB components.
15. Route PCB traces.
16. Map every pad/trace/test point to simulation nodes.
17. Generate customer complaint.
18. Present challenge.

Do not render a board until the underlying electrical circuit has been validated.

---

# Roadmap Authority

The ordered development roadmap is maintained in:

docs/ROADMAP.md

Before defining or beginning any implementation milestone, the primary architect must read:

- AGENTS.md
- docs/ROADMAP.md
- docs/ARCHITECTURE.md
- docs/CODEX_TASK_REPORT.md

Use these documents as follows:

- AGENTS.md defines permanent project laws, architectural invariants, development protocol, and safety rules.
- docs/ROADMAP.md defines ordered development direction, milestone dependencies, and intended sequencing.
- docs/ARCHITECTURE.md documents the current implemented architecture and important technical boundaries.
- docs/CODEX_TASK_REPORT.md records the latest completed task, validation evidence, limitations, and current handoff state.

The roadmap defines development sequencing and dependencies, but it does not authorize autonomous continuation across milestones.

Rules:

1. Work on exactly ONE roadmap milestone at a time.
2. Unless the user explicitly reprioritizes work, select the first eligible incomplete milestone identified as the immediate next milestone in docs/ROADMAP.md.
3. Before delegating implementation, confirm that the milestone's dependencies are satisfied.
4. Convert the roadmap milestone into bounded implementation scope and explicit acceptance criteria.
5. The existence of later roadmap entries is NOT permission to implement them.
6. Never automatically begin the next roadmap milestone after finishing the current one.
7. After successful validation and commit of the current milestone, STOP.
8. The user may explicitly override roadmap order at any time.
9. Discovering an attractive adjacent feature is not permission to implement it.
10. Do not perform future roadmap work opportunistically during another task.
11. If implementation reveals that roadmap sequencing should materially change, report the proposed change rather than silently reordering the roadmap.
12. When a milestone is successfully completed:
    - mark the completed milestone complete;
    - identify the next eligible milestone;
    - preserve completed roadmap history;
    - do NOT begin the newly identified milestone.
13. If AGENTS.md, ROADMAP.md, ARCHITECTURE.md, CODEX_TASK_REPORT.md, and the actual repository appear inconsistent, inspect the real code/history and resolve the discrepancy before delegating implementation.
14. Permanent architectural rules belong in AGENTS.md. Development sequencing belongs in docs/ROADMAP.md. Current implementation explanations belong in docs/ARCHITECTURE.md. Per-task handoff evidence belongs in docs/CODEX_TASK_REPORT.md.

# Validation

Generated circuits must be tested automatically.

A healthy generated circuit should satisfy its intended behavior.

A faulty generated circuit should differ meaningfully from healthy behavior.

If validation fails:

DO NOT patch the displayed result with fake behavior.

Reject the generation and try another valid generation.

Where feasible, use CircuitJS itself as the validation engine.

## Player-Facing Validation

When a task requires normal-player validation of player-facing TroubleshootJS behavior, the built-in `@Browser` is the default and required interaction method. Verify that the development server is running (starting it when necessary), open the actual local application in `@Browser`, and keep the built-in browser visible during validation.

For normal-player validation:

- Interact with the running local application through real visible browser input: visible clicks, left/right clicks where the UI requires them, typing, rendered navigation, and observation of rendered state changes.
- Exercise the workflow as a normal player would, including final inspection of the rendered state after the important interactions. Capture screenshots when useful, including important UI states and interaction results, and preserve them as evidence where appropriate.
- Do not use DOM manipulation, JavaScript-triggered clicks, synthetic events, direct method calls, internal state mutation, or other shortcuts as substitutes for required player interaction. These techniques are allowed only for explicitly requested developer-level checks, not as evidence of normal-player behavior.
- Browser/CDP/DOM/console/network inspection may be used as supplemental diagnostics, but it must not replace the required visible `@Browser` interaction.
- Do not hide or replace the visible built-in browser with Windows desktop automation. Do not use `@Computer`/Windows desktop Computer Use by default; keeping the desktop usable is the default.
- If a required test cannot be performed through `@Browser` and would require `@Computer`, do not switch silently. Explain and record the specific limitation, continue all feasible `@Browser` validation, and use `@Computer` only when explicitly authorized or when no reasonable browser alternative exists.
- Clearly distinguish behavior directly observed through visible `@Browser` interaction from supplemental browser diagnostics, automated-test results, verifier results, or source-level reasoning.

The validation hierarchy is: `@Browser` real visible interaction -> browser/CDP diagnostics -> CLI/verifier tests -> `@Computer` only for genuinely non-browser GUI requirements.

Automated tests, verifiers, CDP, DOM inspection, and source-level diagnostics remain valid for non-player-facing checks. Do not require visible browser interaction for builds, unit tests, deterministic verifier routes, simulation invariants, or other purely automated checks unless the task explicitly requires player-facing validation.

---

# PCB Generation

Initial PCB generation should target simple believable ONE-SIDED boards.

Start with through-hole components where useful because they are educational and visually recognizable.

Later SMD footprints may be added.

PCB generation should include:

- board outline
- component footprints
- component bodies
- pads
- visible copper traces
- silkscreen/reference labels
- connectors
- optional test pads

Possible component representations include:

- axial resistors
- axial diodes
- radial ceramic capacitors
- radial electrolytics
- LEDs
- TO-92
- TO-220
- DIP ICs
- relays
- terminal blocks
- simple SMD packages later

Each conductive feature must know which electrical net/node it represents.

Do not use a flat decorative PCB image as the source of connectivity.

The board geometry must be interactive.

---

# PCB Routing

The router does not need industrial-quality PCB routing.

For initial implementations use simple deterministic/heuristic routing such as:

- grid routing
- Manhattan routing
- A* pathfinding
- obstacle avoidance

If a one-sided route cannot reasonably be completed, allow realistic jumper wires / zero-ohm links.

Routing must preserve correct electrical connectivity.

Visual complexity is desirable, but electrical correctness has priority.

---

# Customer Complaint System

The user should normally receive an incomplete real-world-style complaint rather than the answer.

Examples:

"The pump won't start. The power light still comes on."

"This controller turns on but the fan never runs."

"The machine shuts down after a few seconds."

"The indicator stays on all the time."

Do NOT reveal:

- failed subsystem
- failed component
- fault type
- intended troubleshooting path

Internally the scenario may know these facts.

The player must infer them.

---

# Fault Engine

Faults must be electrically meaningful.

Initial fault types may include:

- resistor open
- resistor incorrect value
- capacitor short
- capacitor open
- excessive capacitor leakage
- diode open
- diode short
- transistor open
- transistor short
- MOSFET D-S short
- failed gate/base path
- relay coil open
- relay contacts failed
- connector open
- trace open
- rail short
- missing ground
- failed regulator
- stuck logic state

Only choose faults compatible with the generated topology.

Never silently create physically nonsensical faults merely for difficulty.

---

# Measurement / Multimeter UI

Implement meter modes as selectable buttons.

Initial modes:

- DC voltage
- AC voltage when supported
- resistance
- continuity
- diode test

Future modes:

- capacitance
- frequency

While a meter mode is active:

LEFT CLICK = place/move red probe
RIGHT CLICK = place/move black probe

The normal right-click/context behavior should be suppressed only while an instrument mode requiring probes is active.

Clicking the currently selected meter mode again should exit that mode and restore normal mouse behavior.

Probe locations should be allowed on electrically exposed:

- component leads
- pads
- test points
- connectors
- exposed copper where appropriate

Measurements must come from the simulation whenever possible.

---

# Powered vs Unpowered Measurements

Respect realistic troubleshooting behavior.

Examples:

- resistance/continuity measurements should normally be performed with power off
- diode mode should interact with the simulated component/network appropriately
- voltage requires the relevant circuit to be powered
- parallel circuit paths may affect in-circuit resistance/diode measurements

Do not automatically reveal that a misleading in-circuit measurement is misleading.

The player may need to isolate the component.

---

# Component Manipulation

The PCB is an interactive electrical workbench.

Users should eventually be able to:

## Remove Component

Disconnect all component terminals from the board.

The removed component should remain available for out-of-circuit measurement when practical.

## Lift Lead

For components where appropriate, allow one terminal to be disconnected while the remainder stays installed.

Examples:

- resistor
- capacitor
- diode

This is important for isolating parallel measurement paths.

## Replace Component

Allow installation of a replacement component/value.

The replacement must actually modify the simulated circuit.

Incorrect replacements must produce their real electrical consequences.

Do not automatically prevent bad choices.

## Jumper Wire

Allow the user to select two accessible electrical points and connect them.

The jumper becomes part of the electrical graph.

The user may:

- repair an open trace
- bypass a connector
- temporarily force a signal
- intentionally bypass a component
- accidentally create a short

Do not protect the user from electrically bad jumper choices.

## Cut Trace

Allow selected copper paths to be electrically opened.

This can be used to isolate sections of a circuit.

Later allow trace repair/restoration.

---

# Board State Must Be Mutable

All physical troubleshooting actions must modify the active board state.

Maintain a clear distinction between:

ORIGINAL GENERATED BOARD

and

CURRENT USER-MODIFIED BOARD

Possible modifications include:

- removed components
- lifted leads
- replacements
- jumpers
- cut traces
- repaired traces
- secondary component damage

The simulator should support undo/reset where practical, but should not silently undo user mistakes.

---

# Component Damage / Secondary Failure System

TroubleshootJS should eventually allow the user to damage previously healthy components.

Example:

The user installs a jumper that shorts a rail.

CircuitJS calculates excessive current/power.

The damage system observes that stress.

A component may then fail.

Do NOT immediately tell the player that they damaged it.

The changed circuit behavior should be their indication.

Secondary failures must result from defensible electrical stress, never arbitrary RNG.

Useful hidden component limits include:

- maximum voltage
- maximum reverse voltage
- maximum current
- maximum power
- approximate thermal limit
- overload duration

Model accumulated damage approximately rather than attempting perfect semiconductor physics.

Example conceptual model:

stressRatio = actualStress / ratedStress

if stressRatio > safeThreshold:
    damage += f(stressRatio, elapsedTime)

if damage >= failureThreshold:
    transition component to a plausible failed state

Possible failure states:

- open
- short
- leakage
- changed resistance
- degraded behavior

Failure mode may contain limited randomness when multiple outcomes are physically plausible.

The CAUSE of failure must not be random.

---

# Power Supply / Current Limiting

Future bench-power functionality should support:

- adjustable supply voltage
- current limit
- voltage readout
- current readout

Current limiting should matter electrically.

A sensible current limit may prevent component destruction.

An excessively high current limit may permit cascading failures.

---

# Thermal Behavior

Do not build full thermal simulation initially.

Approximate heating from electrical power/stress.

Possible future UI:

- component temperature indication
- virtual thermal camera
- hot-component detection during short hunting

Thermal behavior should remain derived from simulated electrical conditions.

---

# Oscilloscope

Plan architecture so scope support is possible without redesign.

Eventually support:

- one or more channels
- probes mapped to PCB nodes
- voltage/time scale
- triggering
- digital signals
- PWM
- switching waveforms
- clocks
- intermittent events

CircuitJS should remain responsible for waveform behavior whenever feasible.

---

# Scoring / Anti-Brute-Force Design

Do not make random probing impossible.

Make thoughtful troubleshooting more rewarding.

Possible scoring factors:

- number of measurements
- unnecessary component removals
- unnecessary replacements
- incorrect repairs
- caused secondary damage
- time
- successful verification
- diagnostic efficiency

Do NOT make scoring rules interfere with electrical realism.

A player should be free to probe every component if they want; it should simply be inefficient.

---

# Repair Completion

Finding the bad part is not enough.

Prefer challenges where the player must:

1. diagnose the fault
2. repair/replace/isolate it
3. power the board
4. operate relevant inputs
5. verify that the customer's reported symptom is resolved

A repaired board is considered complete based on FUNCTIONAL BEHAVIOR, not merely whether the player clicked the originally faulted component.

This allows alternate valid repairs such as a properly installed jumper around a broken trace.

---

# Difficulty Scaling

Difficulty should increase primarily through circuit complexity and information ambiguity, not cheating.

Beginner:

- few components
- simple rails
- one obvious functional path
- one fault

Intermediate:

- topology variation
- auxiliary circuits
- more components
- misleading parallel meter paths
- several possible suspects

Advanced:

- multiple rails
- dependent subsystems
- enable/PGOOD chains
- analog/digital interaction
- scope requirements
- intermittent behavior
- faults that cause secondary symptoms

Expert:

- large boards
- multiple interacting subsystems
- subtle timing/signal faults
- user-caused secondary failures
- minimal hints

---

# UI Philosophy

The PCB should be the primary troubleshooting view.

Do NOT reveal the neat CircuitJS schematic by default during a normal challenge.

The schematic may exist internally and may later be available in special teaching/debug modes.

The normal player experience should require understanding the circuit from:

- PCB traces
- component identification
- meter readings
- scope readings
- board behavior

This is deliberate.

---

# Development Rules

Before implementing a feature:

1. Inspect the existing architecture.
2. Identify the smallest clean integration point.
3. Avoid rewriting working CircuitJS code unnecessarily.
4. Prefer adapters/wrappers around upstream CircuitJS functionality.
5. Preserve the ability to update/merge upstream CircuitJS when practical.
6. Keep generated-circuit data separate from rendered-board geometry.
7. Keep electrical node IDs stable and explicit.
8. Add tests for any generation or mutation logic.
9. Do not hide failures with hard-coded UI behavior.
10. Prefer deterministic seeded randomness during development/testing.

---

# Seeded Randomness

Procedural generation should support a reproducible seed.

Given the same:

- generator version
- challenge settings
- random seed

the same challenge should be reproducible whenever practical.

This is required for:

- debugging
- automated tests
- sharing challenges
- bug reports

Expose the seed in developer/debug mode.

---

# Testing Requirements

Every meaningful generator feature should have automated validation.

Test categories should eventually include:

- valid netlist generation
- healthy circuit functional validation
- fault compatibility
- faulty symptom validation
- no impossible component overlaps
- PCB route connectivity
- pad-to-node mapping
- probe measurement correctness
- component removal behavior
- lifted-lead behavior
- jumper behavior
- trace-cut behavior
- replacement behavior
- secondary damage behavior
- deterministic generation from seeds

For generator tests, prefer running hundreds/thousands of seeded generations where practical to identify edge cases.

---

# Debug Mode

Maintain a developer/debug mode capable of exposing information hidden from normal users.

Useful debug information:

- random seed
- selected functional family
- selected topology
- generated netlist
- node names
- PCB net highlights
- injected original fault
- expected symptom
- component stress
- secondary failures
- CircuitJS values
- generator rejection reason

Never expose these automatically in normal challenge mode.

---

# Implementation Strategy

Build incrementally.

DO NOT attempt every planned feature at once.

Preferred early milestone order:

1. Preserve/fork working CircuitJS.
2. Implement improved probe controls.
3. Define stable board/net/component data model.
4. Render one manually defined interactive PCB.
5. Map PCB pads/traces to CircuitJS nodes.
6. Implement removal/lift/replace/jumper primitives.
7. Implement one simple procedural functional family.
8. Add PCB placement/routing for that family.
9. Add fault injection and validation.
10. Add auxiliary/red-herring circuits.
11. Expand functional-family library.
12. Add damage/thermal/bench-power systems.
13. Add advanced scope/digital systems.

Do not build an enormous general generator before one complete end-to-end challenge works.

---

# First-Proof Challenge

A good first end-to-end procedural family is an LED/control board.

It should eventually be capable of generating multiple healthy implementations such as:

- direct switch
- NPN driver
- NMOS driver

Then:

- randomize values within valid ranges
- optionally add one harmless auxiliary circuit
- inject one compatible fault
- validate the symptom
- generate a simple one-sided PCB
- allow probing
- allow repair
- verify restored LED operation

This is the minimum proof that the architecture works.

---

# Code Quality

Prefer:

- clear names
- small focused modules
- explicit types/interfaces where supported
- comments explaining WHY rather than narrating obvious code
- testable pure logic for generation algorithms
- separation between model/state/rendering
- minimal hidden global state

Avoid:

- giant god classes
- duplicated electrical state
- hard-coded scenario-specific hacks
- UI code deciding circuit physics
- undocumented magic numbers
- unnecessary framework churn
- premature optimization

When encountering existing project conventions, follow them unless there is a strong technical reason not to.

---

# Working With Existing Code

Before editing unfamiliar code:

- search the repository
- understand relevant call paths
- inspect related tests
- determine whether functionality already exists
- reuse existing abstractions where appropriate

Do not create parallel systems when the repository already contains an adequate one.

When a requested change requires significant architecture work, explain the intended architecture before performing a large rewrite.

---

# Preserve Upstream CircuitJS Behavior

CircuitJS is a mature simulation engine.

Avoid modifying simulation internals unless required.

Prefer:

TroubleshootJS feature
    ↓
adapter/interface
    ↓
CircuitJS

rather than scattering TroubleshootJS-specific behavior throughout CircuitJS core classes.

Any unavoidable upstream modifications should be:

- small
- documented
- isolated
- easy to identify during future upstream merges

---

# Definition of Success

TroubleshootJS succeeds when a player can be handed an unfamiliar generated PCB and a vague complaint and must reason through:

"What does this section do?"

"Where should voltage be present?"

"Why is this node wrong?"

"Is this component actually bad, or is another path affecting my reading?"

"Should I isolate it?"

"What happens if I jumper this?"

"Did my repair actually fix the customer's problem?"

The simulator should teach troubleshooting judgment, not memorization.

---

# Multi-Agent Development Protocol

This protocol augments all existing project architecture, development,
validation, and completion requirements. It does not remove, weaken, or
replace them.

## Parallel Subagent Policy

The primary architect should use parallel subagents when doing so can safely
reduce task completion time or improve independent analysis.

Parallel work is encouraged for independent activities such as:

- repository/code-path investigation
- architecture analysis
- searching for related implementations
- test and verifier analysis
- documentation inspection
- independent bug investigation
- independent review of distinct concerns
- browser/test evidence analysis

Multiple write-capable coder subagents MAY work in parallel only when their
ownership boundaries are explicitly separated and their edits cannot reasonably
conflict.

Examples of safe parallel implementation:

- one coder modifies production code while another adds isolated tests
- coders modify separate modules with clearly defined interfaces
- one coder implements a new isolated class while another updates an
  unrelated verifier or fixture
- independent fixes in files/subsystems with no overlapping ownership

Do NOT run multiple write-capable agents in parallel when:

- they may edit the same files
- one agent's implementation depends on another agent's unfinished design
- they are changing the same subsystem or shared abstraction
- integration order is unclear
- concurrent edits could produce conflicting architectural decisions

The primary architect must assign explicit ownership before spawning parallel
write-capable agents and remains responsible for integrating and reviewing all
results.

The normal workflow uses a primary architect and bounded coder/reviewer
implementation and review roles. A separate escalation architect is available
when the normal bounded process cannot safely resolve a substantive issue.

## Primary Architect

The primary architect is the main task thread and is the task owner,
delegation controller, reviewer-finding evaluator, final code reviewer,
acceptance authority, completion owner, and escalation coordinator.

The primary architect must:

- read AGENTS.md, docs/ROADMAP.md, docs/ARCHITECTURE.md, and
  docs/CODEX_TASK_REPORT.md before defining or beginning a milestone;
- inspect the current repository state and relevant execution paths;
- select exactly one eligible milestone;
- confirm milestone dependencies;
- define bounded scope and explicit acceptance criteria;
- identify architectural invariants that must remain true;
- determine required build, test, and browser validation;
- delegate the bounded implementation to the coder;
- evaluate every reviewer finding rather than blindly forwarding it;
- perform an independent final review of the actual implementation and diff;
- decide `FINAL PASS` or `FINAL FAIL`;
- run the completion protocol after success; and
- stop after the task.

The primary architect must not implement delegated work while the coder owns
the implementation phase. Outside that phase, a narrow diagnostic or unblock
change is permitted only through the defined correction or escalation workflow.

## Delegation Ownership and Patience Protocol

Once implementation is delegated, the coder owns that phase until
it returns a result, explicitly reports `BLOCKED` or `FAILURE`, or the user
explicitly cancels the delegation.

While the coder is active, the primary architect must not tell it to hurry,
repeatedly request progress, send unnecessary follow-ups, interrupt or
terminate it merely because work is taking longer than expected, implement the
delegated task, edit its delegated product source files, or spawn a replacement
coder for the same work.

The architect should wait for natural completion and use passive wait/status
mechanisms when available. Silence, elapsed time, and long-running builds,
browser checks, dependency setup, or debugging are not evidence of failure.
The architect may contact an active coder only for clarification, materially
new user information, a confirmed blocking condition, an explicit `BLOCKED` or
`FAILURE` report, or intervention required to prevent confirmed destructive or
out-of-scope work.

If the coder genuinely fails or becomes unusable, preserve valid work, inspect
and diagnose the state, then resume or redelegate through the defined correction
workflow or report the blocker. Do not silently become the implementation
agent. Architect ownership remains task definition, acceptance criteria,
clarification, evidence and reviewer evaluation, failed-round diagnosis,
final review, and escalation; implementation remains the coder's
responsibility.

The same ownership and patience rules apply during correction rounds and
escalation-architect work: after a repair is delegated, let the coder complete
it before reviewer or architect action begins. Let reviewer work complete
naturally as well; do not pressure, interrupt, or replace a reviewer merely
because independent review takes time. No phase advances until its assigned
role has returned the required disposition or report. Existing bounded retry
limits and escalation rules remain unchanged.

## Coder Subagent

The coder has workspace-write access.

The coder must:

- read and obey AGENTS.md and relevant project documentation;
- implement only the bounded task supplied by the primary architect;
- preserve CircuitJS as the electrical simulation source of truth;
- preserve all established stable identity and graph-ownership rules;
- prefer small incremental changes over broad refactors;
- avoid unrelated cleanup and opportunistic future-roadmap work;
- run required builds and tests;
- inspect its own diff;
- report files changed, behavior implemented, validation performed, failures,
  uncertainty, and architectural concerns;
- never push automatically;
- never begin another milestone;
- never weaken tests or acceptance criteria to make a task pass; and
- stop and escalate architectural uncertainty instead of inventing a new
  architecture.

The coder must not spawn additional write-capable agents for routine work.

## Reviewer Subagent

The reviewer has read-only access.

The reviewer must independently inspect:

- the actual changed implementation;
- the actual diff;
- the architect's acceptance criteria;
- relevant architecture and permanent invariants;
- real execution paths where necessary; and
- test and browser evidence.

The reviewer must prioritize:

- functional correctness;
- architectural violations;
- CircuitJS simulation correctness;
- graph ownership;
- stable board, component, pad, and net identity;
- state and lifecycle bugs;
- temporary measurement cleanup;
- board-power safety;
- procedural-generation validity;
- deterministic behavior;
- browser/player-visible regressions;
- missing validation; and
- unsafe assumptions or scope creep.

The reviewer must not:

- edit source code;
- weaken requirements;
- approve merely because tests pass; or
- focus on cosmetic/style issues unless they expose substantive risk.

Every reviewer result must end in exactly one of:

`PASS`

or

`FAIL`

Every `FAIL` finding should identify the exact issue, affected file or symbol
where possible, why it matters, expected behavior, actual behavior or risk,
evidence/reproduction path where applicable, and required behavior for
resolution.

Reviewer `PASS` means only that the candidate is ready for the primary
architect's independent final review.

## Normal Review Loop

The normal hierarchy is:

```text
Primary architect
    -> coder
    -> reviewer
    -> primary architect final review
```

The primary architect must independently review the actual implementation
after reviewer `PASS` and must not blindly trust either the coder or reviewer.

If the reviewer returns `FAIL`:

1. The primary architect evaluates every finding.
2. The primary architect classifies every finding as `BLOCKER`, `FOLLOW-UP`,
   or `BACKLOG`.
3. Invalid findings are discarded with a brief reason; non-blocking findings
   are recorded without reopening implementation.
4. Only `BLOCKER` findings become precise corrective requirements and go back
   to the coder.
5. The coder makes the narrowest defensible fixes and reruns relevant
   validation.
6. The reviewer independently reviews the corrected candidate again when a
   `BLOCKER` correction requires independent review.

Coder and reviewer work remains sequential. Allow at most TWO coder/reviewer
correction passes within one primary-architect review round. Do not allow an
unlimited nested loop. If the candidate still cannot reach reviewer `PASS`
because of a `BLOCKER`, the unresolved issue returns to the primary architect
for diagnosis and a decision about the next bounded review round or
escalation-architect review. If only `FOLLOW-UP` or `BACKLOG` findings remain,
the architect records them and continues toward completion.

## Maximum Three Architect Final-Review Rounds

A task may receive at most THREE primary architect final-review rounds.

### Round 1

The coder implements, the reviewer independently reviews, and the primary
architect performs the first final review after the review phase.

If the architect returns `FINAL PASS`, proceed to final validation and
completion.

If the architect returns `FINAL FAIL`, the architect must identify every
substantive blocker, explain why it violates the requirements or architecture,
provide precise corrective requirements, send them to the coder, and begin
Round 2.

### Round 2

The coder implements the architect's corrections, the reviewer independently
reviews the corrected candidate, and the primary architect performs the second
final review.

If the architect returns `FINAL PASS`, proceed to final validation and
completion.

If the architect returns `FINAL FAIL`, the architect must recognize that only
one autonomous correction round remains and must perform deeper diagnosis
before beginning Round 3.

### Round 3 — Final Architect Diagnostic Attempt

Before returning the task to the coder, the primary architect must actively diagnose the
failure by inspecting enough of the real implementation and surrounding
architecture to produce a useful repair strategy. Where applicable, identify:

- the probable root cause;
- the affected execution path;
- affected files, classes, methods, and symbols;
- the incorrect state transition;
- the incorrect architectural assumption;
- relevant graph or simulation behavior;
- why previous corrections failed;
- required invariants and what must remain unchanged;
- the exact desired behavior;
- a proposed repair strategy; and
- tests or instrumentation needed to verify the diagnosis.

The primary architect must give the coder a detailed diagnostic repair brief resembling
senior-engineer implementation guidance rather than a generic review comment.
The coder must verify the diagnosis against the actual code, report
contradictory evidence instead of forcing the proposed fix, implement the
narrowest defensible repair, run especially thorough relevant validation, and
return the candidate. The reviewer then independently reviews it, and the
primary architect performs the THIRD AND FINAL independent final review.

If the primary architect returns `FINAL PASS`, proceed to completion. If the
primary architect returns `FINAL FAIL`, do not begin a fourth architect review
round; follow the escalation-architect protocol below.

## Escalation Architect Protocol

The escalation architect provides senior architectural escalation only.

An authorized escalation uses the configured escalation-architect role. The
primary architect remains the owner of orchestration.

The escalation architect is used only when the normal workflow cannot safely
resolve the task or when an architectural escalation condition is met. It is
NOT required for routine successful milestones.

Escalate to the escalation architect when any of these conditions occurs:

1. The third primary architect final-review round fails.
2. The coder and reviewer materially disagree about an architectural issue
   that the primary architect cannot confidently resolve.
3. Fixing the issue appears to require changing a permanent architectural
   invariant.
4. The project documents and actual implementation reveal a fundamental
   architectural contradiction.
5. The task exposes a potentially dangerous issue involving core boundaries
   such as CircuitJS graph ownership, active measurement stimulus cleanup,
   board-power isolation, stable board identity, solver-backed measurement
   correctness, generated-board ownership, procedural-generation validity,
   physical-part identity, or mutation lifecycle integrity, and the primary
   architect cannot confidently resolve it.
6. The primary architect determines that continuing without stronger
   architectural review risks hidden technical debt or corruption of an
   established invariant.

When escalated, the escalation architect must inspect the actual
implementation and relevant architecture and identify the root cause,
architectural conflict, execution path, affected symbols, why prior attempts
failed, what must remain unchanged, the narrowest defensible repair strategy,
and the validation required. The escalation architect provides a detailed
repair brief to the coder. The coder makes the repair, the reviewer
independently reviews it, and the escalation architect performs the final
escalation review.

The escalation architect's final result must be exactly one of:

`FINAL PASS`

or

`FINAL FAIL`

If the escalation architect returns `FINAL PASS`, proceed to normal task
completion. If it returns `FINAL FAIL`, STOP. Do not start another repair
cycle, weaken requirements, redesign the subsystem autonomously, begin another
roadmap milestone, or commit failed work. Report the blocker, attempted work,
validation state, and recommended human or architect decision to the user. The
escalation architect is the final safety valve, not an unlimited fourth
development loop.

## Hard Autonomy Limits

At all times:

- Exactly one milestone may be worked on per autonomous run.
- Never automatically start the next milestone.
- Never push automatically.
- Never weaken tests or acceptance criteria to achieve `PASS`.
- Never change product requirements merely to make implementation easier.
- Never allow multiple write-capable agents to edit the same implementation
  concurrently.
- Keep coder/reviewer work sequential unless a clearly safe read-only parallel
  investigation is explicitly useful.
- Keep the reviewer read-only.
- Do not perform unrelated refactors.
- Do not implement future-roadmap features opportunistically.
- Do not silently change established architecture.
- A failed task is a valid stopping condition.
- If the repository enters an uncertain state, stop rather than stacking more
  changes on top.
- Preserve unrelated user changes.
- User instructions override roadmap ordering.
- The primary architect may stop earlier than the maximum retry count when
  additional autonomous attempts would likely make the code worse.

## Successful Multi-Agent Acceptance and Completion

When the multi-agent workflow is used, successful acceptance requires:

- the coder considers the implementation complete;
- the reviewer returns `PASS`, or the primary architect independently
  classifies every remaining reviewer finding as `FOLLOW-UP` or `BACKLOG`
  under the severity protocol below; and
- the primary architect returns `FINAL PASS`, or the escalation architect
  returns `FINAL PASS` after an authorized escalation.

Only after those gates are satisfied may the normal Task Completion Protocol
proceed. It must then:

1. Run the required final JDK 8 / GWT build and applicable automated/browser
   validation.
2. Inspect `git diff` and `git status`.
3. Verify that only intended changes remain.
4. Update docs/ARCHITECTURE.md if architectural behavior changed.
5. Update docs/ROADMAP.md by marking the completed milestone complete,
   identifying the next eligible milestone, preserving completed history, and
   not beginning the next milestone.
6. Overwrite docs/CODEX_TASK_REPORT.md with the final task report.
7. Stage only intended changes.
8. Run `git diff --cached --check`.
9. Commit exactly once with a concise descriptive message when the task
   authorizes a commit.
10. Do not push.
11. STOP without beginning another milestone.

The final docs/CODEX_TASK_REPORT.md must include:

- roadmap milestone/task;
- summary;
- architectural decisions;
- files changed;
- validation performed;
- important test data and results;
- coder result;
- reviewer result;
- number of primary architect review rounds;
- primary architect final result;
- whether escalation-architect review was required;
- escalation-architect diagnosis and result if used;
- known limitations or concerns;
- next roadmap milestone; and
- commit message.

---

# Review Severity, Closed Validation Set, and Task Stop Conditions

This protocol supplements the existing architecture, safety, testing,
review, persistence, and Task Completion Protocol requirements. It defines
when a finding may reopen implementation and when a task must stop.

## Finding Severity

Every issue discovered during coder review, reviewer review, architect review,
or final validation must be classified as exactly one of the following:

### BLOCKER

A `BLOCKER` materially prevents the current task from being considered
correct. Examples include:

- product behavior is functionally incorrect;
- an explicit acceptance criterion for the current task fails;
- a core architectural invariant is violated;
- simulation or electrical truth is violated;
- state can be corrupted or persisted incorrectly;
- determinism required by the task is broken;
- safety, power, or measurement invariants are broken;
- an existing required user-facing behavior has regressed;
- the implementation could expose false or invalid player-facing information;
  or
- the task cannot perform its intended gameplay or functionality reliably.

Only `BLOCKER` findings may return the task to the coder.

### FOLLOW-UP

A `FOLLOW-UP` is a real issue that should be fixed but does not invalidate the
current milestone. Examples include:

- test or browser automation flakiness that does not demonstrate incorrect
  product behavior;
- intermittent CDP or UI interaction timing failures in verifier
  infrastructure;
- additional defensive validation;
- non-critical UI races;
- minor developer-tooling problems;
- documentation cleanup that does not materially misrepresent current
  behavior; or
- non-critical edge cases and robustness improvements outside the explicit
  acceptance criteria.

`FOLLOW-UP` findings must be documented for a later task and must not prevent
completion of the current task.

### BACKLOG

`BACKLOG` is a worthwhile improvement that is not currently required.
Examples include naming cleanup, refactoring, maintainability improvements,
better diagnostics, additional optional tests, architecture polish, and
developer-experience improvements. `BACKLOG` findings must not prevent task
completion.

## Only BLOCKER Findings Reopen Implementation

The architect must independently evaluate reviewer findings instead of
blindly treating every reviewer `FAIL` as a reason to return work to the
coder. If a reviewer returns `FAIL` but the underlying finding qualifies only
as `FOLLOW-UP` or `BACKLOG`, the architect must record the finding, explain
the classification, and continue toward completion without opening another
coder correction pass for that finding. A reviewer disposition is evidence
for the architect, not an automatic command to reopen implementation.

## Closed Validation Set

Every task has a closed validation set consisting of:

- the acceptance criteria explicitly defined when the task began;
- required repository-wide build and test checks established by AGENTS.md;
- regression checks directly necessary to prove that functionality touched by
  the task was not broken; and
- specific checks added to reproduce and verify a previously discovered
  `BLOCKER`.

Once the closed validation set passes and all substantive `BLOCKER` findings
are resolved, the task is eligible for completion. During final review, the
architect must not continually expand the blocking validation set by
inventing additional spot checks, extra legacy routes, supplemental stress
tests, optional browser matrices, “just to be safe” checks, unrelated edge
cases, or new acceptance requirements.

The architect may perform exploratory checks when useful. A failure found
exclusively by an exploratory check is `FOLLOW-UP` by default unless it
provides concrete evidence of a genuine `BLOCKER` in the product or an
original acceptance criterion. The finish line must not move indefinitely.

## Permanent Risk-Based Targeted Validation Policy

For every bounded task, the primary architect must define the closed
validation set before implementation and review begin. The set must name the
task acceptance checks, the required build or test checks, the direct
regressions for the changed execution path, and the adjacent regressions that
protect established boundaries. A later exploratory check may inform a
follow-up, but it must not silently expand the blocking set after the defined
set has passed.

For Java or GWT production-source changes, run one final JDK 8/GWT production
build after the final source candidate is ready. Intermediate builds are
diagnostic and do not replace that final build. The completion check also
requires inspection of the final `git status` and `git diff`,
`git diff --check`, and, when staging is authorized, `git diff --cached --check`
after only the intended files are staged.

Validation should cover the direct changed path and the smallest adjacent
regressions that could be affected by it. Broad historical matrices are
required only when the change surface or risk genuinely justifies them; they
are not the default substitute for reasoning about coverage. Seeded procedural
work should use deterministic representative seeds: at least one seed for
each touched family or topology, plus boundary or previously-regressed seeds
when relevant. Do not make a validation pass depend on probabilistic family or
seed selection.

Visible player behavior requires materially distinct built-in `@Browser`
workflows appropriate to the risk, such as initial state, the new interaction,
an invalid or unrepaired state, and a correctly completed state. Repeating the
same route or relying only on DOM/CDP diagnostics is not materially distinct
player validation. Screenshots should cover the important visible states and
must be surfaced and preserved when the task evidence rules apply. The
reviewer independently runs targeted checks for the changed boundary and any
direct regression; reviewer validation may be narrower than the full closed
set only when it still directly proves the review concerns.

Correction rounds rerun the narrowest relevant failed checks, the direct
regressions, and any required final build or browser evidence; they do not
restart an unrelated full matrix. Documentation-only or evidence-only
corrections do not require a product rebuild unless the correction changes
production source or invalidates the evidence, but they do require a diff and
consistency inspection. Testing effort scales with regression risk and change
surface. This policy never weakens electrical truth, CircuitJS ownership,
determinism, stable identity, power and measurement safety, player privacy, or
the established architecture.

## Test-Harness Failures Versus Product Failures

A test or verifier failure does not automatically mean that the product is
incorrect. The architect and reviewer must distinguish evidence that actual
product behavior is broken from evidence that automation failed to interact
with or observe otherwise-correct product behavior.

Lost synthetic CDP clicks, transient focus loss, GWT re-render timing, delayed
panel appearance, and automation-selector races are normally `FOLLOW-UP`
unless investigation demonstrates a corresponding player-facing defect. Do
not repeatedly reopen a completed feature solely to make optional automation
theoretically perfect. Required automated checks must still be reliable
enough to establish their acceptance criteria; this rule is not permission to
ignore a verifier that cannot prove required behavior.

## Bounded Correction Loops

Correction loops resolve substantive `BLOCKER`s, not unlimited polish. When a
correction is requested, give the coder the exact finding, identify expected
versus actual behavior, constrain the correction to the affected scope, and
rerun only the necessary affected validation plus required regression checks.
Send the corrected candidate back to the reviewer when independent review is
required. Do not restart a broad review cycle for every minor issue.

If repeated failures within the same subsystem indicate that the test
infrastructure itself is flaky, classify that infrastructure problem
separately rather than repeatedly treating each manifestation as a new
product `BLOCKER`.

## Architect Stop Condition

Once all of the following are true, the architect must stop searching for
additional reasons to delay completion:

- the coder has completed the requested implementation;
- the original acceptance criteria pass;
- required repository build and test checks pass;
- substantive `BLOCKER` findings are resolved;
- the reviewer has passed the implementation, or all remaining reviewer
  findings have been independently classified as `FOLLOW-UP` or `BACKLOG`;
  and
- the architect has completed the predefined final validation.

At that point, document `FOLLOW-UP` and `BACKLOG` findings, update the task
handoff/report, inspect git diff and status, stage only intended files, run the
repository's required staged-diff checks, commit according to the existing
Task Completion Protocol when the task authorizes a commit, and stop. Do not
begin the next task unless instructed by the established workflow. “Could be
improved” is not equivalent to “the current task is incorrect.”

## Incremental Development Principle

TroubleshootJS is developed incrementally. Non-game-breaking defects and
infrastructure improvements may be carried into the next task or backlog
rather than forcing the current milestone into an unlimited correction loop.
Quality remains important, but the objective is high confidence in the defined
milestone, not theoretical perfection across every adjacent subsystem. Core
correctness, simulation truth, architectural invariants, player-facing
validity, state integrity, and regressions remain blocking.

## Preservation of Existing Project Rules

This protocol does not remove or relax the Task Completion Protocol,
persistence and retry rules, build requirements, reviewer independence,
architectural invariants, CircuitJS-as-source-of-truth requirement,
normal-player privacy requirements, deterministic-generation requirements, or
power and measurement safety requirements. Where a generic retry instruction
overlaps with this section, classify the issue first: the retry loop applies
to a suspected `BLOCKER` or failed check in the closed validation set; it does
not turn a `FOLLOW-UP` or `BACKLOG` into a blocker.

---

# Task Completion Protocol

## Persistence and Retry Protocol

A task is not incomplete merely because work remains after one implementation or
validation pass. Continue through implementation, validation, screenshots,
documentation, staging, and commit in the same task whenever safely possible.
Do not return an unfinished task after a first failed command, browser
interaction, screenshot attempt, verifier failure, timeout, stale layout, or
automation mistake while a suspected `BLOCKER` or closed-validation failure
remains unresolved.

For a recoverable failure that may be a `BLOCKER` or affects the closed
validation set, diagnose it and make at least three materially distinct
attempts before declaring it externally blocking. A materially distinct
attempt changes a relevant execution method, browser context, viewport
initialization, event coordinates, route isolation, diagnostic instrumentation,
source fix, server process, or validation strategy; repeating the same command
does not count. Preserve the working tree between attempts and continue the
remaining checklist after a fix. This retry requirement does not apply to an
issue already classified as `FOLLOW-UP` or `BACKLOG` unless new evidence shows
that it is a `BLOCKER`.

Do not weaken assertions, remove validation, fabricate screenshots, bypass
normal UI interaction, or directly mutate verifier/controller state merely to
obtain a pass. An unfinished return is permitted only for unavailable required
permissions or credentials, a required user decision that materially changes
the implementation, an environment that repeatedly cannot perform the work,
behavior that contradicts the electrical model or repository architecture, or
a reproducible build/browser/source failure remaining after three diagnostic or
fix attempts. Such a return must list the exact blocking requirement, every
materially distinct attempt and diagnostic, why further autonomous work is
unsafe or nonproductive, and the current worktree state. Time, task length, or
an incomplete checklist is not itself a blocker.

Three alternate ways of invoking the same failing interaction do not
automatically count as three materially distinct attempts to resolve the
underlying defect. Once a reproducible product or integration defect is
identified, trace, fix, and validate it. A minimum retry count does not permit
stopping while useful diagnostic and repair paths remain. Materially distinct
attempts test different root-cause hypotheses or apply different repairs. A
defect in code currently being implemented is not a hard blocker merely
because it prevents later validation; a hard blocker requires an external
constraint or technically demonstrated contradiction that cannot be safely
corrected within task scope.

For normal TroubleshootJS implementation tasks, unless the task explicitly says
otherwise:

When the multi-agent workflow is used, do not proceed to staging or commit
until the successful multi-agent acceptance gates above have been satisfied.

1. Perform the requested work.
2. Run the required JDK 8 / GWT build and applicable automated, test, and
   browser validation.
3. Inspect `git diff` and `git status`.
4. Update `docs/ARCHITECTURE.md` if architectural behavior changed.
5. Update `docs/ROADMAP.md` when a roadmap milestone was completed: mark it
   complete, identify the next eligible milestone, preserve completed history,
   and do not begin the next milestone.
6. Update `docs/CODEX_TASK_REPORT.md` with the latest completed task report.
7. Stage only intended source and documentation changes.
8. Run `git diff --cached --check`.
9. Commit with a concise descriptive message when the task authorizes a
   commit.
10. Do not push.
11. STOP without beginning another milestone.

If a required check in the closed validation set fails because of an
unresolved `BLOCKER`, do not commit. Leave the changes in the working tree and
clearly report the failure. Do not auto-commit when explicitly told not to.
`docs/CODEX_TASK_REPORT.md` is intentionally overwritten after each successful
task; Git history preserves prior reports.

## Visual Evidence Protocol

For every successful task that changes or exercises visible player behavior:

For all browser-based player-flow validation:

- Use the Codex built-in `@Browser` for all browser-based normal-player interaction, as required by the Player-Facing Validation protocol; browser/CDP diagnostics are supplemental only.
- Capture and surface screenshots at every important UI state, including after
  reaching newly implemented UI and before and after important interactions.
- Do not merely report that a browser test passed; make the visual evidence
  user-visible to the primary architect during the task.
- Preserve the screenshots as task evidence where appropriate.
- If a coder subagent's browser session cannot be displayed directly in the
  visible Codex browser pane, return screenshots to the primary architect
  immediately during the task so the architect can inspect what the coder is
  seeing.

1. Capture a small curated set of final production-browser screenshots after
   the final production build, using the actual production preview rather than
   mockups.
2. Use normal-player mode unless a developer view is specifically relevant.
3. Prefer two to five screenshots that meaningfully show the initial state, new
   feature or UI, important interaction state, repaired or final state, and any
   visually important regression evidence.
4. Do not commit large numbers of debugging or intermediate screenshots.
5. Store screenshots under `docs/task-evidence/task-XX/`, where `XX` is the task
   number, and use descriptive filenames such as `initial-board.png`,
   `led-selected.png`, `led-removed-parts-tray.png`, and `repaired-board.png`.
6. Pixel-inspect or otherwise verify every screenshot is nonblank, is not an
   error page, shows the intended application state, and uses a useful viewport
   size.
7. List every committed screenshot in `docs/CODEX_TASK_REPORT.md` and briefly
   state what it proves.
8. Stage the curated screenshots with the intended source and documentation
   changes.

Screenshots supplement and never weaken or replace existing electrical,
automated, or browser validation. Screenshots are optional when a task has no
visible player or UI effect.

Before defining any milestone, the primary architect must read:

- AGENTS.md
- docs/ROADMAP.md
- docs/ARCHITECTURE.md
- docs/CODEX_TASK_REPORT.md

Use:
- AGENTS.md for permanent architectural and development rules.
- docs/ROADMAP.md for ordered development direction and dependencies.
- docs/ARCHITECTURE.md for current implemented architecture and technical
  boundaries.
- docs/CODEX_TASK_REPORT.md for the actual latest completed state.

If these disagree about current project state, inspect the repository and resolve the discrepancy before delegating implementation.
