# TroubleshootJS Roadmap

_Last updated: 2026-08-15_

## Purpose

This document defines the **ordered development direction** for TroubleshootJS.

It is intentionally separate from:

- `AGENTS.md`, which defines permanent project mission, architecture rules, and development constraints.
- `docs/ARCHITECTURE.md`, which documents how implemented systems currently work.
- `docs/CODEX_TASK_REPORT.md`, which records the most recently completed implementation task.

This roadmap answers a different question:

> **What should we build next, and what must exist before later systems are attempted?**

The roadmap is a planning document, not permission for an autonomous agent to continue indefinitely.

**One milestone is one bounded task. After a milestone is completed and committed, STOP. Never automatically begin the next milestone.**

---

# Product North Star

TroubleshootJS should become a free, open-source electronics troubleshooting simulator that feels less like solving a neat schematic and more like receiving an unfamiliar physical PCB on a workbench.

The core player loop is:

1. Receive an incomplete customer complaint.
2. Inspect an unfamiliar PCB.
3. Infer its functional sections.
4. Choose useful measurements.
5. Probe the physical board.
6. Isolate components or circuit sections when necessary.
7. Diagnose the actual electrical fault.
8. Make a repair.
9. Power the board and verify the customer's complaint is resolved.
10. Live with the consequences of a bad repair rather than being protected from it.

Difficulty should come from **electrical reasoning, topology, incomplete information, realistic in-circuit measurements, and believable physical layout**, not hidden simulator tricks.

CircuitJS remains the electrical source of truth.

---

# Roadmap Rules

## Status

- `[x]` Complete and validated.
- `[>]` Next planned milestone.
- `[ ]` Planned.
- `[~]` Exploratory / timing intentionally flexible.
- `[!]` Blocked by an earlier dependency.

## Milestone selection

The project architect should:

1. Select only the first eligible incomplete milestone in the ordered near-term queue unless the user explicitly reprioritizes.
2. Confirm that its dependencies are satisfied.
3. Define bounded acceptance criteria for that milestone.
4. Implement and review only that milestone.
5. Commit only after final validation.
6. Stop after the commit.

Later roadmap phases describe direction. They are **not** authority to skip prerequisites or start several systems at once.

## Definition of done

A milestone is not complete merely because the UI appears to work.

Where applicable, completion requires:

- the electrical result comes from CircuitJS rather than a hard-coded answer;
- normal-player browser interaction has been exercised;
- stable board/component/pad/net identities survive required mutations;
- temporary meter infrastructure leaves no graph, export, undo, or state contamination;
- generated boards remain deterministic for a seed;
- existing verifier routes continue to pass;
- the JDK 8 / GWT production build passes;
- architectural documentation is updated when behavior or boundaries change;
- `docs/CODEX_TASK_REPORT.md` is updated;
- the intended changes are committed once;
- the agent stops rather than beginning the next milestone.

---

# Current Baseline

TroubleshootJS has already moved well beyond a stock CircuitJS fork.

## Development foundation

- [x] Reproducible JDK 8 / GWT build and development scripts.
- [x] Root `AGENTS.md` project constitution and task-completion protocol.
- [x] Rolling `docs/CODEX_TASK_REPORT.md`.
- [x] Developer verification routes and production-browser validation.
- [x] Seeded/reproducible generated-board behavior.

## Stable logical board model

- [x] Stable `TroubleshootBoard`, component, pad, and net identities.
- [x] Separation between stable TroubleshootJS identities and transient CircuitJS solver node numbers.
- [x] Family-agnostic generated-board instances and simulation bindings.
- [x] External board-power metadata and real electrical isolation controls.

## Instruments

- [x] DC voltage mode using real solver-backed electrical behavior.
- [x] Resistance mode using temporary CircuitJS meter stimulus.
- [x] Continuity as policy over the resistance primitive.
- [x] Diode test using its own finite-compliance CircuitJS stimulus.
- [x] Active-measurement cleanup and power-safety transaction boundaries.
- [x] Probe identity that survives appropriate reanalysis while invalidating correctly when a physical part disappears.

## Physical workbench

- [x] PCB-primary generated-board view.
- [x] Probeable PCB pads and component leads.
- [x] Lead lifting and reconnection.
- [x] Component removal.
- [x] Loose physical parts in a parts tray.
- [x] Out-of-circuit measurement support.
- [x] Replacement resistor catalog.
- [x] Distinct physical identity for every replacement part.
- [x] Correct mutation of the active CircuitJS graph rather than fake repair state.
- [x] Resistor color-band rendering from immutable physical/nameplate metadata.

## PCB generation

- [x] Seeded one-sided PCB geometry.
- [x] Recognizable through-hole footprints for the implemented families.
- [x] Deterministic placement variation.
- [x] Manhattan/A* routing.
- [x] Keep-out handling.
- [x] Pad escape corridors.
- [x] Copper-clearance validation.
- [x] Multi-pad same-net routing.
- [x] Silkscreen/reference labeling.
- [x] Independent geometry validation.

## Circuit families

- [x] Simple LED indicator family.
- [x] Diode-protected indicator behavior.
- [x] Replaceable-resistor repair workflow.
- [x] First genuine parallel family with two independent resistor/LED branches.
- [x] KCL and branch-specific fault validation through the solver.
- [x] Real parallel resistance measurement behavior.

The current baseline is **Task 29: Player-Facing Component Identification Fidelity**.

---

# Ordered Near-Term Queue

These milestones are deliberately ordered. This phase should turn the existing technical foundation into a convincing small troubleshooting game before the project explodes into dozens of circuit families.

---

## Task 28 — Compact Topology-Aware PCB Placement

**Status:** `[x] Complete`

### Goal

Replace the intentionally oversized/simple generated layouts with more believable compact layouts that understand the topology they are placing.

### Why now

Task 27 proved that the router can handle genuine multi-branch nets, but the current layout remains intentionally roomy. Better placement should be solved before adding much larger circuit families, otherwise later boards will magnify layout weaknesses.

### Requirements

- Keep placement deterministic from the challenge seed.
- Preserve stable component/pad/net identities.
- Prefer sensible physical grouping without making topology visually obvious.
- Reduce unnecessary board area and trace length.
- Support the existing LED, diode-protected, and parallel families.
- Preserve router keep-outs, copper clearance, legal escape corridors, and validation.
- Avoid designing a general-purpose PCB placer.
- Keep one-sided through-hole readability.

### Completion gate

Existing electrical, workbench, measurement, repair, and layout verifier routes remain green across the required seed matrix.

### Completed implementation

Task 28 added stable-ID topology-aware placement, routing courtyards with legal
pad escapes, compact outline derivation from occupied geometry, compactness
validation, and same-net trunk preference for the LED, diode, and parallel
families. The JDK 8 build, electrical verifiers, normal-player flows, layout
verifier, and production-browser evidence passed.

---

## Task 29 — Player-Facing Component Identification Fidelity

**Status:** `[x] Complete`

### Goal

Make component identification behave like a real troubleshooting board rather than quietly handing the player the answer.

### Requirements

- Original installed resistors should normally expose **physical color bands**, not a convenient numeric resistance label.
- The actual original resistor value must not be revealed in ordinary player-facing contextual UI.
- Replacement catalog parts may expose their catalog value because the player is intentionally selecting a known replacement.
- Removed original parts retain their color-band/nameplate identity.
- Debug/developer mode may expose exact generated values.
- Other component markings should eventually follow the same principle: show what would physically be printed on the part, not hidden simulator metadata.
- Ensure accessibility does not accidentally leak the hidden value through tooltips, labels, ARIA text, or hidden player-visible fields.

### Completion gate

A player can determine an original resistor value by reading its bands or electrically measuring it, but cannot obtain that answer from ordinary UI metadata.

### Completed implementation

Task 29 hides original resistor values from ordinary panels, loose-part labels,
selected-part labels, accessibility-visible attributes, and generated family
descriptions while preserving physical color bands, stable original identity,
CircuitJS-backed measurement, and catalog/replacement values. The LED,
diode-protected, and parallel normal-player flows plus the seeded developer
routes and production-browser evidence passed.

---

## Task 30 — Generic Functional Challenge Completion Contract

**Status:** `[>] NEXT`

### Goal

Move from family-specific “the repair looks right” behavior toward a reusable definition of a successfully repaired board.

### Requirements

- Separate **diagnosis**, **physical repair**, and **functional verification**.
- A challenge is complete because required electrical behavior has been restored, not because the player replaced the originally faulted object.
- Permit alternate electrically valid repairs where the scenario allows them.
- Do not reveal the hidden original fault during normal play.
- Define reusable healthy/faulted/repaired behavior contracts for generated families.
- Preserve family-specific validators behind generic interfaces.
- Do not yet build a scoring system.

### Completion gate

At least the existing simple and parallel families use a common completion/verification path without hard-coding “R1 was replaced correctly” as the universal success condition.

---

## Task 31 — Fault Engine v1

**Status:** `[ ]`

### Goal

Make faults explicit generated data rather than family-specific setup accidents.

### Initial fault types

Start narrowly with faults the current component set can represent defensibly:

- resistor open;
- resistor incorrect value;
- diode open;
- diode short;
- connector/open-path fault where the existing topology supports it.

### Requirements

Generation pipeline becomes:

healthy board
→ validate healthy behavior
→ choose compatible fault
→ inject fault into electrical graph/state
→ simulate faulty behavior
→ validate meaningful symptom
→ accept or reject generation

- Fault selection must be compatible with the selected topology.
- Original healthy specification remains known internally.
- Effective electrical failure state must be represented in CircuitJS.
- Faults must be seeded/reproducible.
- Fault metadata is hidden from normal player UI.
- Reject boring or electrically meaningless fault generations.

### Completion gate

The same functional family can produce multiple reproducible faulted challenges without special-case UI answers.

---

## Task 32 — Scenario and Customer Complaint Foundation

**Status:** `[ ]`

### Goal

Give the player a reason to troubleshoot rather than merely presenting “find the bad part.”

### Requirements

- Challenge scenario owns a vague customer-visible complaint.
- Complaints describe symptoms, not diagnoses.
- Complaint selection must match the actually simulated faulty behavior.
- Examples:
  - “Power light comes on, but the second indicator never lights.”
  - “Unit is completely dead.”
  - “It worked for a while after the last repair, then failed again.”
- Keep internal expected behavior separate from displayed complaint.
- Support seeded/reproducible scenario text selection where appropriate.
- Avoid an LLM dependency for basic challenge generation.

### Completion gate

Normal-player challenges begin with a believable symptom statement that is consistent with what the solver actually produces.

---

## Task 33 — Wrong Repair Semantics and Post-Repair Validation

**Status:** `[ ]`

### Goal

Allow the player to make a plausible but electrically wrong repair and let the circuit tell the story.

### Requirements

- Installing the wrong resistor value must remain legal when physically compatible.
- Wrong repairs change actual simulated behavior.
- A challenge does not complete merely because a component occupies the correct slot.
- Post-repair functional verification detects:
  - still-faulted behavior;
  - degraded but operating behavior;
  - correct restored behavior.
- Avoid popups that say “wrong resistor.”
- The player should discover the bad repair from measurements or functional behavior.
- Replacement choice must preserve the distinction between catalog specification and physical installed instance.

### Completion gate

At least one challenge can be “repaired” with an incorrect resistor that powers up but fails the functional completion criteria for a genuine electrical reason.

---

## Task 34 — Component Ratings and Stress/Damage v1

**Status:** `[ ]`

### Goal

Introduce consequences for repairs that initially work but electrically overstress a component.

### First target

Start with **resistor power stress** because the model is understandable, measurable, and directly relevant to existing replacement gameplay.

### Requirements

- Give applicable physical parts hidden ratings, beginning with resistor wattage.
- Derive actual stress from CircuitJS voltage/current/power.
- Stress accumulation should use simulation time or an accelerated service-time abstraction rather than forcing the player to literally wait several minutes.
- Mild overrating may survive.
- Severe overrating should fail faster.
- Failure timing must be bounded and testable.
- Failure state should be physically plausible, initially resistor open or changed resistance where justified.
- Do not announce damage with a magic popup.
- Secondary failure becomes part of current board state.
- Reset/retry behavior must be deterministic enough for testing.

### Delayed-return scenario

The architecture should permit a future scenario such as:

1. Player installs an undersized or badly chosen replacement.
2. Board appears to work during immediate verification.
3. The simulated device experiences an accelerated service interval.
4. The overstressed component fails.
5. The “customer” returns with the board and a new complaint.
6. The player must diagnose the consequence of the previous repair.

Do **not** require real-time multi-minute waiting.

### Completion gate

An electrically overstressed resistor can genuinely fail because of solver-derived stress, while a correctly rated replacement survives the same validation window.

---

## Task 35 — Generalized Physical Part Specifications

**Status:** `[ ]`

### Goal

Prepare the workbench for capacitors, diodes, transistors, relays, and later SMD parts without turning every component family into a resistor-specific fork.

### Requirements

Define reusable physical/specification boundaries for:

- catalog specification;
- immutable nameplate/marking data;
- physical part instance identity;
- ratings;
- orientation/polarity where applicable;
- loose/installed state;
- simulation backing;
- player-visible markings.

Preserve specialized electrical behavior where specialization is appropriate.

### Completion gate

Adding the next major component type does not require copying the resistor inventory/identity architecture wholesale.

---

# Phase 2 — Broaden the Circuit Vocabulary

The project should next add circuit families that teach distinct troubleshooting concepts. Add them one at a time, validating each before composing them into larger boards.

---

## Task 36 — Capacitor Foundation and RC Family

**Status:** `[ ]`

### Goals

Introduce capacitors as real stored-energy/reactive components and create the first family where time behavior matters.

### Initial capabilities

- through-hole ceramic and electrolytic representation;
- polarity metadata for electrolytics;
- capacitor open fault;
- capacitor short fault;
- optionally excessive leakage once the model is defensible;
- simple RC charge/discharge or filtering family;
- player-visible physical markings that do not reveal hidden failure state.

### Notes

A capacitance meter is not required merely to introduce capacitors. Voltage, resistance behavior, timing, and isolation can already support useful troubleshooting.

---

## Task 37 — NPN Low-Side Switch Family

**Status:** `[ ]`

### Goals

Introduce:

- base current;
- base resistor;
- pull-down behavior;
- collector/emitter reasoning;
- control signal versus load supply;
- transistor open/short or failed-base-path faults.

### Completion gate

The family should support multiple meaningful probe points and at least several fault locations that produce distinguishable symptoms.

---

## Task 38 — NMOS Low-Side Switch Family

**Status:** `[ ]`

### Goals

Introduce:

- gate-source voltage;
- high-impedance control;
- gate pull-down;
- drain/source reasoning;
- MOSFET D-S short/open behavior.

Avoid making it merely an NPN circuit with a different picture.

---

## Task 39 — Relay Driver Family

**Status:** `[ ]`

### Goals

Introduce:

- control side versus switched load side;
- relay coil;
- flyback diode;
- contact state;
- coil-open fault;
- contact fault;
- driver fault;
- realistic distinction between control voltage and load voltage.

---

## Task 40 — Regulator / Multi-Rail Power Family

**Status:** `[ ]`

### Goals

Introduce:

- input rail;
- regulated output rail;
- enable where appropriate;
- multiple voltage domains;
- failed regulator;
- shorted load;
- missing rail;
- power-sequencing groundwork.

This is the point where multi-input and multi-rail power safety rules become much more important.

---

## Task 41 — Comparator / Sensor Input Family

**Status:** `[ ]`

### Goals

Introduce analog decision thresholds and an input that represents a real-world sensor condition.

Support faults such as:

- open sensor path;
- incorrect divider value;
- stuck comparator output;
- missing reference;
- pull-up/pull-down failure.

---

## Task 42 — Timer / Oscillator Family

**Status:** `[ ]`

### Goals

Create the first family where static DC measurements alone may not be enough.

This becomes a natural prerequisite for the oscilloscope phase.

---

# Phase 3 — Physical Repair Toolbox

After the board contains more varied circuits, expand what the player can physically do to it.

---

## Task 43 — Jumper Wires

**Status:** `[ ]`

### Goals

- Select two electrically accessible points.
- Add a real conductive element to the active CircuitJS graph.
- Render the jumper physically.
- Allow correct repair, diagnostic bypass, and catastrophic mistakes.
- Do not protect the player from shorting something.

---

## Task 44 — Trace Cutting

**Status:** `[ ]`

### Goals

- Select eligible copper.
- Electrically open the correct graph connection.
- Preserve board/net semantic identity while tracking physical mutation.
- Allow isolation for diagnosis as well as accidental damage.

---

## Task 45 — Trace Repair

**Status:** `[ ]`

### Goals

- Repair an intentionally cut/broken trace.
- Permit jumper-based and direct-trace repair where appropriate.
- Completion depends on restored function, not a specific repair gesture.

---

## Task 46 — Fuse and Protection Components

**Status:** `[ ]`

### Goals

Introduce:

- fuses;
- reverse-polarity protection;
- transient/protection elements where useful;
- failure from overcurrent;
- the possibility of a player dangerously bypassing protection.

---

# Phase 4 — Bench Instruments and Dynamic Troubleshooting

---

## Task 47 — Oscilloscope Foundation

**Status:** `[ ]`

### Initial scope

- one channel first;
- probe mapped to PCB-accessible points;
- time/div and volts/div;
- waveform rendering from CircuitJS simulation;
- triggering sufficient for stable repetitive signals;
- no fake pre-scripted waveforms.

Later extend to:

- multiple channels;
- differential reasoning;
- PWM;
- clock/data signals;
- intermittent captures.

---

## Task 48 — Frequency Measurement

**Status:** `[ ]`

Reuse waveform/signal infrastructure where practical.

---

## Task 49 — Capacitance Measurement

**Status:** `[ ]`

Only add after a defensible active measurement method can be represented through the simulation rather than reading a capacitor's configured value directly.

---

## Task 50 — Bench Power Supply and Current Limit

**Status:** `[ ]`

### Goals

- adjustable supply voltage where the scenario permits it;
- current readout;
- current limit;
- actual electrical consequences of current limiting;
- interaction with the damage system.

A sensible current limit should be able to save a board. A reckless setting should be able to let damage continue.

---

# Phase 5 — Intermittent and Secondary Failure

---

## Task 51 — Intermittent Fault Engine

**Status:** `[ ]`

### Fault behavior

Support time/state-dependent failures such as:

- intermittent open;
- temperature/stress-sensitive connection;
- periodic dropout;
- startup-only failure;
- vibration-like randomized contact within a reproducible seed.

Intermittency must mutate real electrical state at bounded events. Do not simply flash a fake bad reading.

---

## Task 52 — Expanded Stress and Damage Models

**Status:** `[ ]`

Expand beyond resistor power to selected defensible limits:

- diode current/reverse voltage;
- electrolytic reverse voltage;
- fuse I²t-like approximation;
- transistor/MOSFET current and power;
- relay coil stress;
- IC supply abuse where simplified models justify it.

Use approximate engineering models. Do not attempt semiconductor-device physics for its own sake.

---

## Task 53 — Thermal Approximation

**Status:** `[ ]`

### Goals

- component thermal state derived from electrical dissipation;
- cooling over simulated time;
- thermal contribution to failure timing;
- optional player-facing heat clues.

Future UI may include a simplified thermal camera, but the thermal state must first exist electrically/physically.

---

## Task 54 — Customer Return / Repair History

**Status:** `[ ]`

### Goal

Allow a challenge to retain consequences across service visits.

Example:

- first repair appears successful;
- bad component selection causes later stress failure;
- simulated time advances;
- customer returns;
- second complaint reflects the new actual failure;
- the board retains the player's prior repair history.

This feature should sit on top of the stress and scenario systems rather than containing hard-coded “come back later” scripts.

---

# Phase 6 — Multi-Subsystem Procedural Boards

This is where TroubleshootJS begins to resemble genuinely unfamiliar equipment rather than one educational circuit on a PCB.

---

## Task 55 — Auxiliary Healthy Circuits / Red Herrings

**Status:** `[ ]`

Add optional healthy circuitry such as:

- power indicator;
- secondary LED output;
- buzzer;
- protection network;
- sensor connector;
- unused header;
- filtering;
- decoupling;
- secondary rail.

These circuits are not automatically faults.

They exist to force the player to identify which subsystem matters.

---

## Task 56 — Functional Module Composition

**Status:** `[ ]`

### Goal

Compose validated circuit-family modules into one larger logical board while preserving clear internal electrical contracts.

Example:

power input
→ protection
→ regulator
→ control/sensor stage
→ transistor/relay driver
→ load/output

Avoid unrestricted random netlist generation.

---

## Task 57 — Multi-Subsystem Fault Selection

**Status:** `[ ]`

Select a fault from a compatible subsystem while keeping unrelated subsystems healthy.

Complaint generation should point only to customer-visible symptoms, not the failed section.

---

## Task 58 — Multiple Simultaneous Faults

**Status:** `[~]`

Only after single-fault generation is mature.

Default challenge generation should remain mostly single-fault, with a small configurable probability of multiple compatible faults.

Multi-fault challenges must be validated to ensure they remain diagnosable rather than becoming arbitrary chaos.

---

## Task 59 — Larger Board Placement and Routing

**Status:** `[ ]`

Extend placement/routing only as demanded by composed functional modules.

Possible additions:

- board regions;
- connector-edge constraints;
- denser through-hole layout;
- realistic jumper/zero-ohm links when one-sided routing cannot complete;
- later SMD footprints.

Do not turn this into KiCad.

---

# Phase 7 — Challenge Progression and Game Layer

---

## Task 60 — Difficulty Model

**Status:** `[ ]`

Difficulty should increase through:

- number of components;
- number of functional subsystems;
- topology ambiguity;
- parallel measurement paths;
- multiple rails;
- incomplete complaint information;
- intermittent behavior;
- need for oscilloscope measurements;
- secondary consequences.

Do not increase difficulty by hiding normal physical information or lying to the player.

---

## Task 61 — Challenge Generator

**Status:** `[ ]`

Combine:

- circuit family/modules;
- valid topology;
- parameter values;
- PCB seed;
- compatible fault;
- complaint;
- optional auxiliary circuits;
- difficulty settings;
- validation results.

The entire challenge should be reproducible from versioned generation settings plus seed.

---

## Task 62 — Scoring and Troubleshooting History

**Status:** `[ ]`

Potential factors:

- measurements taken;
- unnecessary removals;
- unnecessary replacements;
- wrong repairs;
- secondary damage caused;
- time;
- successful functional verification;
- diagnostic efficiency.

Scoring must never change the underlying electrical truth.

---

## Task 63 — Guided Beginner Mode

**Status:** `[~]`

Add teaching support without contaminating normal troubleshooting behavior.

Possible features:

- optional hints;
- explanation after completion;
- show healthy schematic only after the challenge;
- measurement reasoning review;
- fault postmortem.

Normal mode should remain PCB-first.

---

# Phase 8 — Mobile, Accessibility, and Productization

Mobile support is valuable, but it should not distract from proving the core troubleshooting loop first.

---

## Task 64 — Touch Input Layer

**Status:** `[ ]`

Desktop behavior remains:

- left click = red probe;
- right click = black probe.

Touch behavior should provide an explicit, reliable equivalent, likely:

- tap = place selected probe;
- visible red/black probe selector;
- drag = pan;
- pinch = zoom;
- long press = contextual component actions where appropriate.

Do not rely on browser-emulated right click as the primary phone UX.

---

## Task 65 — Responsive PCB Workbench UI

**Status:** `[ ]`

- phone-sized meter controls;
- larger touch hit targets;
- collapsible contextual panels;
- safe viewport/gesture handling;
- landscape and portrait sanity;
- no hidden electrical functionality on mobile.

---

## Task 66 — Accessibility Pass

**Status:** `[ ]`

Address:

- keyboard navigation where practical;
- non-color-only meter state;
- readable component labels;
- screen-reader semantics that do not leak hidden fault/value information;
- zoom and contrast;
- optional alternatives for audio continuity feedback.

---

## Task 67 — Save / Resume Challenge State

**Status:** `[ ]`

Persist:

- challenge seed/version;
- current board mutations;
- installed physical parts;
- loose inventory;
- damage state;
- measurements/history as appropriate.

Do not serialize transient CircuitJS solver node IDs as durable identity.

---

## Task 68 — Shareable Challenges

**Status:** `[ ]`

A shared challenge should reproduce the intended generated board and fault from a compact versioned challenge descriptor/seed.

---

## Task 69 — Public Deployment and Project Identity

**Status:** `[ ]`

Before broad release:

- replace inherited CircuitJS-facing README with TroubleshootJS documentation;
- document project goals and controls;
- clearly preserve upstream attribution and GPL requirements;
- provide hosted web build;
- document browser support;
- provide contribution guidance appropriate to the new architecture.

---

# Phase 9 — Advanced / Long-Term Circuit Families

These are intentionally later. They should be built only after the core generated challenge pipeline is mature.

- [~] PNP / PMOS high-side switching.
- [~] Op-amp amplifier circuits.
- [~] More comparator/reference circuits.
- [~] Buck converter.
- [~] More realistic AC/power-supply rectifier/filter circuits.
- [~] Motor/fan drivers.
- [~] Logic gates and state machines.
- [~] Enable / PGOOD dependency chains.
- [~] Memory/data interface abstractions.
- [~] Simplified GPU-memory-style troubleshooting challenge using logically correct but intentionally slowed timing.
- [~] SMD-dominant boards.
- [~] Logic analyzer.
- [~] Multi-channel oscilloscope.
- [~] Virtual thermal camera.

These are not near-term commitments.

---

# Explicit Non-Goals

Unless the roadmap is deliberately changed, TroubleshootJS should **not** spend development effort on:

- manufacturing-ready Gerber generation;
- professional DRC;
- arbitrary multilayer PCB autorouting;
- electromagnetic field simulation;
- high-speed signal-integrity simulation;
- replacing CircuitJS with a custom electrical solver;
- unrestricted random circuit synthesis;
- protecting the player from every electrically bad decision;
- fake meter answers that bypass the simulation;
- perfect semiconductor thermal physics;
- turning the product into a general schematic/PCB CAD package.

---

# Architectural Dependency Map

```text
STABLE BOARD MODEL
        |
        +----> PROBE / MEASUREMENT SYSTEM
        |          |
        |          +----> DC / OHM / CONT / DIODE
        |          |
        |          +----> FUTURE SCOPE / FREQUENCY / CAPACITANCE
        |
        +----> PHYSICAL WORKBENCH
        |          |
        |          +----> LIFT / REMOVE / REPLACE
        |          |
        |          +----> JUMPER / CUT / REPAIR
        |
        +----> GENERATED CIRCUIT FAMILIES
        |          |
        |          +----> HEALTHY VALIDATION
        |          |
        |          +----> FAULT ENGINE
        |                     |
        |                     +----> COMPLAINT / SCENARIO
        |                     |
        |                     +----> FUNCTIONAL COMPLETION
        |
        +----> PCB GENERATOR / ROUTER
        |          |
        |          +----> COMPACT PLACEMENT
        |          |
        |          +----> LARGER MULTI-SUBSYSTEM BOARDS
        |
        +----> PHYSICAL PART RATINGS
                   |
                   +----> STRESS / DAMAGE
                               |
                               +----> THERMAL
                               |
                               +----> CUSTOMER RETURN / REPAIR HISTORY
```

The important sequencing rule is:

> **Do not scale complexity until the smaller version is electrically honest.**

---

# Near-Term Product Target

Before aggressively expanding into advanced circuit families, TroubleshootJS should be able to deliver this complete experience:

1. Player receives a vague complaint.
2. Player sees only the believable PCB workbench.
3. Original resistor values are not handed to them numerically.
4. Player uses DC/ohm/continuity/diode measurements.
5. In-circuit measurements can legitimately mislead because of parallel paths.
6. Player can lift/remove a component and test it out of circuit.
7. Player chooses a replacement from a catalog.
8. A wrong replacement is allowed.
9. CircuitJS determines whether the repair actually works.
10. Functional verification decides whether the job is complete.
11. An electrically overstressed replacement may fail later.
12. The simulator can preserve that consequence for a follow-up service event.

If we can make **that small loop genuinely convincing**, adding transistors, relays, regulators, oscillators, and larger boards becomes expansion rather than another foundational rewrite.

---

# Roadmap Maintenance

Update this file when:

- a milestone is completed;
- a dependency changes;
- a newly discovered architectural problem changes sequencing;
- the user intentionally reprioritizes;
- a planned feature is split into smaller milestones;
- a feature is dropped.

When updating:

1. Preserve completed history.
2. Mark the completed milestone `[x]`.
3. Mark exactly one immediate next milestone `[>]` when practical.
4. Do not silently reorder major product priorities during an implementation task.
5. Record major sequencing changes in `docs/CODEX_TASK_REPORT.md`.
6. Keep permanent architecture rules in `AGENTS.md`, not here.
7. Keep implementation details in `docs/ARCHITECTURE.md`, not here.

---

# Immediate Next Milestone

**Task 30 — Generic Functional Challenge Completion Contract**

Task 29 passed final review and is committed in this run. Task 30 is identified
as the next eligible milestone but is not started in this task/run.
