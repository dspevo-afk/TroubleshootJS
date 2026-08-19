# TroubleshootJS Roadmap

_Last updated: 2026-08-18_

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
- [x] RC delay family with real stored energy and temporal validation.
- [x] NPN and NMOS low-side switch families with real CircuitJS devices,
  independent control/load inputs, physical replacement, and corrected PCB
  connectivity.

## Product shell

- [x] Quick Play session and solver-backed Finish Job boundary.
- [x] Integrated workbench navigation shell with a real TOOLS/workbench view
  and safe modal focus/keyboard isolation.
- [x] SHOP is explicitly a local mock with no board connection; RESOURCES and
  SETTINGS are visible placeholders. They are not yet gameplay systems.

The current accepted implementation baseline is **Task 38: NMOS Low-Side
Switch Family**, including the physical gate-topology correction and the
subsequent integration of the four architecture audits and workbench shell.
The local master and origin/master baseline used for this redesign was
92df240.

## Completed milestone ledger

This ledger preserves the completed Task 1–38 history. Tasks 28–38 retain
their detailed completion records below; the earlier rows summarize the
accepted history recorded by repository commits and rolling task reports.

| Task | Accepted completed result |
| --- | --- |
| 1 | Reproducible JDK 8/GWT build and development workflow. |
| 2 | Improved red/black probe controls and instrument-mode pointer behavior. |
| 3 | Measurement endpoint and CircuitJS adapter boundary. |
| 4 | Active-measurement session and initial board-power safety boundary. |
| 5 | Stable board, component, pad, net, binding, and external-input model. |
| 6 | First seeded generated LED-indicator family and logical/simulation binding. |
| 7 | Family-agnostic generated-board ownership and solver-gated verification. |
| 8 | Real external board-power isolation distinct from simulation RUN/STOP. |
| 9 | Solver-backed resistance measurement and hardened transaction lifecycle. |
| 10 | Continuity policy over the simulated resistance primitive. |
| 11 | Finite-compliance diode test and semantic probe/cleanup correction. |
| 12 | Reversible lead lift, reconnect, remove, and restore graph mutations. |
| 13 | First interactive PCB-primary workbench and physical parts tray. |
| 14 | First solver-validated open-resistor challenge and gated lifecycle. |
| 15 | Electrically real resistor replacement, physical isolation, and functional repair. |
| 16 | Solver-backed 10 MOhm DC voltmeter loading and lifted-lead behavior. |
| 17 | Retained-probe refresh correctness and stable physical-part probe identity. |
| 18 | Unlimited resistor catalog plus normal-player replacement validation. |
| 19 | Family-agnostic generated-board replacement state boundary. |
| 20 | Visible development-preview repair. |
| 21 | Post-refactor validation and preview hardening. |
| 22 | Self-contained deterministic browser verification. |
| 23 | Replaceable silicon-diode challenge family. |
| 24 | Replaceable LED identity and persistent preview behavior. |
| 25 | First seeded procedural one-sided PCB layout generator. |
| 26 | Procedural PCB routing, clearance, escape, and visual-believability hardening. |
| 27 | First genuine parallel circuit with KCL and in-circuit parallel measurement. |
| 28 | Compact topology-aware placement, derived board sizing, and routing courtyards. |
| 29 | Player-facing component-identification fidelity and value privacy. |
| 30 | Generic functional challenge-completion contract. |
| 31 | Explicit seeded fault engine and compatible real graph effects. |
| 32 | Solver-compatible scenario and customer-complaint foundation. |
| 33 | Wrong-repair semantics and post-repair solver validation. |
| 34 | Resistor ratings and solver-derived stress/damage v1. |
| 34(A) | Runtime physical-part, workbench, renderer, and instrument extensibility hardening. |
| 35 | Generalized physical specifications, catalogs, packages, and inventory identity. |
| 35(A) | Quick Play and normal-player Finish Job loop. |
| 36 | Capacitor foundation, stored-energy safety, and RC temporal family. |
| 37 | NPN low-side switch family and corrected control/state/layout behavior. |
| 38 | NMOS low-side switch family and corrected physical control/gate topology. |

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

**Status:** `[x] Complete`

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

### Completed implementation

Task 30 introduced one generic healthy/faulted/repaired behavior contract and
adapter boundary shared by generated board instances and challenge
definitions. The challenge controller now uses the contract for lifecycle
verification and solver-backed functional completion, while the existing LED,
diode, and parallel family validators remain behind that boundary. Original
part identity is no longer a universal completion requirement; electrically
valid alternate repairs are accepted where the family predicate permits them,
and nonfunctional or still-faulted repairs remain incomplete.

---

## Task 31 — Fault Engine v1

**Status:** `[x] Complete`

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

### Completed implementation

Task 31 introduced the seeded `GeneratedFaultEngine` candidate/effect boundary.
LED and parallel indicator families now generate resistor-open and
resistor-incorrect-value challenges, while the diode-protected family models
both diode-open and diode-short effects. Fault effects mutate CircuitJS-backed
switches or component values, retain healthy/effective metadata, and expose
private simulation ownership without leaking fault identity to normal player
UI. Family validators reject meaningless symptoms before a challenge reaches
READY, and the shared functional repair contract remains the completion gate.
Task 32 applies the normal-player scenario eligibility filter to diode-short
while retaining the developer route.
Connector/open-path candidates are represented but marked incompatible until a
compatible connector/trace repair primitive exists.

---

## Task 32 — Scenario and Customer Complaint Foundation

**Status:** `[x] Complete`

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

### Completed implementation

Task 32 introduced immutable generic `GeneratedScenario<T>` and
`GeneratedScenarioCatalog<T>` values. Scenarios own stable scenario/complaint
identity, player complaint text, internal observed-behavior semantics, and a
CircuitJS-backed compatibility predicate. The challenge lifecycle now reaches
`READY` only after healthy validation, fault injection, solver-backed faulted
validation, and compatible scenario selection in that order.

LED and diode-open challenges use a truthful dark-indicator complaint. The
parallel family uses a truthful asymmetric-indicator complaint based on the
solved branch currents/illumination. Normal diode generation deterministically
rejects diode-short as scenario-ineligible; the explicit developer
`tsjDiodeShort=true` route exercises the real short fault with a solver-verified
higher-current/bright scenario. Complaint selection is isolated from topology,
value, fault, and layout randomness, and normal UI keeps internal fault and
expected-behavior metadata hidden.

The requested incorrect-value equality and value-mutation ownership checks are
enforced and covered by the developer verifier. Browser routes cover scenario
and solver agreement for LED, parallel, diode-open, and developer diode-short
seeds, along with normal-player repair completion.

---

## Task 33 — Wrong Repair Semantics and Post-Repair Validation

**Status:** `[x] Complete`

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

### Completed implementation

Task 33 adds a generic solver-backed repair status boundary with explicit
still-faulted/nonfunctional, degraded-but-operating, and correctly-restored
results. Challenge completion now requires the correctly-restored result from
the existing functional behavior contract; it does not compare a replacement
catalog value or physical part ID with hidden expected metadata. The LED seed-3
normal-player and developer routes prove that a legal 2.2 kOhm replacement
produces nonzero illuminated but out-of-contract behavior, while a subsequent
1 kOhm replacement restores the solved functional contract. Physical part,
inventory, attachment, original-fault, complaint, and privacy invariants remain
intact. At the Task 33 completion point, Task 34 stress/damage behavior had
not started.

---

## Task 34 — Component Ratings and Stress/Damage v1

**Status:** `[x] Complete`

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

### Completed implementation

Task 34 adds an immutable hidden rated wattage to each physical resistor
nameplate. Original and catalog-acquired resistors retain that rating for the
life of the physical part, while each catalog acquisition receives its own
stable identity. `ResistorStressDamageSystem` is a narrow family-state service
that samples live solved `ResistorElm` power, integrates deterministic
service-time damage only for the installed powered part, pauses while powered
off or under temporary measurement stimulus, and opens the part through its
owned `ResistorSecondaryOpenPath` when severe damage reaches the threshold.
The secondary path is separate from `GeneratedFaultBinding`, so generated
fault ownership and loose original parts remain unchanged; removal and
reinstallation of a failed physical part preserve its open state.

The seed-3 developer proof covers a 220 Ohm severe replacement, a 330 Ohm
modest-overload replacement, and a 1 kOhm correctly rated replacement. It
reports live resistance, solved watts, rating, stress ratio, damage, service
time, failure time, physical IDs, original-fault ownership, meter/power pause
behavior, reset behavior, and post-failure current/illumination. The primary
player-facing manual gate was completed through Computer Use on the active
Windows Edge desktop with normal mouse/keyboard input: seed 3 original R1 was
selected, powered off, removed, replaced with the acquired 220 Ohm part,
powered on, observed operating without a wattage/stress/damage diagnostic,
advanced only through the permitted developer service-time seam, and observed
again after secondary open with the LED dark and no diagnostic UI. The primary
screenshots are `computer-use-severe-overload-powered.png` and
`computer-use-secondary-failure.png` under `docs/task-evidence/task-34/`.

Headless/CDP routes and their distinct screenshots—`initial-board.png`,
`severe-overload-powered.png`, `secondary-failure.png`, and
`correct-restored.png`—are supporting evidence for solver, identity, graph,
and deterministic checks only. Normal UI contains no wattage, stress, or
damage diagnostics.

---

## Task 34(A) — Core Extensibility Hardening and Validation Policy

**Status:** [x] Complete

### Goal

Harden the reusable physical-part, workbench, renderer, and instrument
extension boundaries without replacing CircuitJS as the electrical source of
truth or beginning Task 35.

### Completed implementation

Task 34(A) now has a runtime-owned physical foundation. PhysicalBoardRuntime
owns stable physical-part identities, board slots, package/terminal/pad/net
associations, inventory ownership, capability registration, and slot mutation
providers. PhysicalPart, PhysicalSpecification, PhysicalPackage, and the
capability contracts provide reusable metadata and lifecycle seams while
family-specific electrical behavior remains specialized where appropriate.

Workbench operations are capability-owned and discovered through the generic
workbench registry. The common controller no longer owns resistor, diode, LED,
or reference-designator mutation branches. Provider-owned PCB rendering now
covers installed and loose parts, package-specific geometry, terminal and pad
identity, selection/hit regions, polarity, and probe targets. The common
renderer is orchestration only.

Instrument modes are provider-driven through InstrumentModeProvider,
InstrumentModeStrategy, and InstrumentModeRegistry. Provider implementations
own mode metadata, probe semantics, measurement/stimulus lifecycle, power
policy, cleanup, and state. Production registration refreshes visible controls
after controller construction, and the architecture canary exercises a
newly-registered visible provider through the production UI path.

### Completion and dependency result

The main architect completed the required visible LED and diode seed-3 gate
after all executable subagent review passes. The boards rendered with stable
physical identities, correct resistor markings, diode cathode orientation,
LED polarity, selectable parts, reachable lift/reconnect controls, and no
player-visible original values, stress, fault, or private infrastructure.
Task 35 — Generalized Physical Part Specifications remains the next eligible
milestone and was identified only; it was not started.

---

## Task 35 — Generalized Physical Part Specifications

**Status:** `[x] Complete`

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

### Completed implementation

Task 35 generalized the immutable `PhysicalSpecification` and
`PhysicalPartCatalog` boundary with typed catalog entries, separate
player-visible `PhysicalNameplate` metadata, extensible ratings, and generic
orientation metadata. Resistor, diode, and LED catalogs now use the common
contract while retaining their family-specific specifications and CircuitJS
electrical behavior.

Production acquisition now retains the exact selected catalog specification
object on each acquired physical part. Visible catalog metadata is materialized
onto the physical identity through the common nameplate boundary; slot IDs such
as R1, D1, and LED1 are never used as replacement specification IDs. Repeated
acquisition creates distinct runtime-owned physical instances sharing the same
immutable catalog specification, and removal/reinstallation preserves both.

`PhysicalPartInventory<P>` remains the runtime-owned identity and inventory
mechanism; the redundant resistor, diode, and LED inventory wrappers were
removed. Stable part identity, acquisition, lookup, loose/installed
transitions, simulation backing, and family mutation controllers remain
separate concerns.

Package identity is stable by declared package ID with canonicalized,
order-independent internal connectivity. Slot compatibility, footprint lookup,
and physical render lookup now share the same definition rule and reject
conflicting same-ID definitions deterministically. The developer-only
future-shaped three-terminal canary proves catalog/specification privacy,
inventory lifecycle, terminal/pad identity, CircuitJS backing, capability
discovery, footprint lookup, and installed/loose rendering without adding a
capacitor or another player-visible component family.

The selected catalog entry now determines install availability and labeling.
Existing resistor stress/damage, generated-fault ownership, LED/diode polarity,
and solver-backed player behavior remain specialized and unchanged.

### Completion result

Task 35 and its bounded post-commit catalog-ownership correction passed the
closed validation set, independent coder/reviewer gates, the primary architect
final review, and the visible production-browser smoke gate. Task 36 is the
next eligible milestone and is identified only; it was not started.

---

## Task 35(A) — Quick Play / Playable Challenge Loop

**Status:** `[x]` Complete — primary architect `FINAL PASS`

### Goal

Turn the existing generated-board foundation into a direct normal-player loop
without adding a new circuit family or beginning Task 36.

### Implementation candidate

The root `Start TroubleshootJS.cmd` now resolves the repository from its own
location, reuses or starts the detached production preview, opens the default
browser into `tsjQuickPlay=true`, keeps the server alive, and reports useful
startup/build errors. Preview startup builds only when the compiled bootstrap
is missing and only when a safely resolved JDK 8 is available; it never installs
a JDK and preserves explicit `-Challenge`/`-Seed` routes.

Quick Play uses a small selector/registry/session seam. It selects only the
normal-player LED, diode-protected-indicator, and parallel dual-indicator
families, passes a fresh selected seed into the existing deterministic family
generator, and never selects the developer-only diode-short route. The
selection source is injectable for deterministic verification, while family,
seed, fault, answer, ratings, stress, and specification details remain out of
normal-player UI.

Quick Play exposes a `Finish Job` control only for its own generated challenge.
Preparation disables the control. Unrepaired or degraded boards stay on the
same PCB with neutral feedback; correctly restored boards cross the existing
solver-backed generic repair-status/completion boundary and reload into a
fresh Quick Play session. Explicit challenge, fixture, and verifier route
precedence remains unchanged.

This task also adopts the permanent risk-based targeted validation policy in
`AGENTS.md` and adds focused selector, Finish Job, launcher, privacy, and
fresh-session checks. The existing explicit LED, diode, and parallel routes
remain representative adjacent smoke regressions; the historical full matrix
is not part of the default closed set.

Task 35(A) passed its bounded targeted validation, independent reviewer gate,
and visible production-browser gate. Task 36 — Capacitor Foundation and RC
Family is the next eligible milestone; it is identified only and was not
started.

---

# Phase 2 — Broaden the Circuit Vocabulary

The project should next add circuit families that teach distinct troubleshooting concepts. Add them one at a time, validating each before composing them into larger boards.

---

## Task 36 — Capacitor Foundation and RC Family

**Status:** `[x]` Complete — primary architect `FINAL PASS`; Task 37 was the
next eligible milestone at Task 36 acceptance and is completed below

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

### Completion result

Task 36 adds typed physical capacitor specification/nameplate/rating/catalog
identity, provider-owned radial electrolytic and ceramic packages, the
solver-backed `RC_DELAY` family, generic optional temporal functional testing,
and generic stored-energy measurement readiness. Its deterministic documented
fixture topology is `VIN -> R1 -> RC_OUT`, C1 and R2 from `RC_OUT` to `GND`,
and C2 from `VIN` to `GND`; its seed envelope is seed 0: 5 V / R1 12 kOhm /
R2 10 kOhm / C1 positive-lead open; seed 2: 9 V / R1 15 kOhm / R2 10 kOhm /
C1 short; seed 3: 12 V / R1 15 kOhm / R2 10 kOhm / C1 positive-lead open.
C1 is 33 uF / 16 V and C2 is 100 nF / 50 V. Its real R2/C1 discharge constant
is `.330 s`; its generic live solver cadence makes the physical residual and
subsequent rise observable in the normal player flow without an artificial
discharge graph or a UI waveform. Focused automated RC, stored-energy,
renderer-boundary, Quick Play, and adjacent family checks pass;
the visible in-app browser acceptance gate is recorded separately in the Task
36 report, including its specific native-select Browser-control limitation.
At Task 36 acceptance, Task 37 was the next eligible milestone; its completed
result is recorded below.

---

## Task 37 — NPN Low-Side Switch Family

**Status:** `[x]` Complete — primary architect `FINAL PASS` after the
silkscreen/state-preservation and validation-purity/meter-stability corrections;
Task 38 is next eligible and has not been started

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

### Completion result

Task 37 adds the solver-backed `NPN_LOW_SIDE_SWITCH` family with independent
load/control supplies, stable `LOAD_SUPPLY`, `CONTROL_INPUT`, `BASE`,
`LOAD_NODE`, `COLLECTOR`, and `GND` nets, and explicit CircuitJS
`NTransistorElm` base/collector/emitter mapping. It supports seeded healthy
ON/OFF behavior and four distinct compatible faults: Q1 C-E open, Q1 C-E
short, base-resistor open, and load-path open. Fault effects remain private to
the original physical part, while remove/reinstall preserves the original
fault and catalog replacement creates a distinct solver-backed NPN.

The physical layer adds ordered TO-92 B/C/E package/specification/nameplate
identity, provider-owned footprint/render/probe geometry, loose-part
inspection, a replaceable Q1 slot, and generic solver-backed repair status.
Quick Play now includes the family while preserving explicit-route precedence
and normal-player privacy. The shared `PcbBoardLayout` tray invariant rejects
board/tray overlap and all existing fixed/procedural layouts were corrected;
the NPN verifier proves exact parity with the registered TO-92 footprint.

Task 37 passed the bounded coder/reviewer protocol, final JDK 8/GWT build,
post-review corrections, and the 16/16 forced NPN seed/fault verification
matrix for seeds 0, 1, 2, and 3. The correction also proves four ordinary
Quick Play NPN routes, including the non-forced seed-1 C-E-short route through
repair and Finish Job; it preserves the legacy families' `{0, 2, 3}` envelope,
keeps the NPN envelope at `{0, 1, 2, 3}`, and resolves
each connector's authoritative positive nameplate and `GND` return label,
removes the old ground detour, rejects route overlap/self-intersection,
restores prior commanded state after instantaneous checks, and keeps original
fault ownership separate from catalog replacements. Architecture/renderer/
layout checks, Quick Play, RC/stored-energy, and LED/diode/parallel
regressions remain passing. Visible in-app Browser evidence is recorded under
`docs/task-evidence/task-37-correction/`.

The final bounded corrections strengthen the acceptance proof without changing
the NPN family architecture: generated seed 0/1/2/3 raw and rendered
silkscreen text is compared with its physical power-input nameplates,
wrong/correct CE-open and CE-short status checks compare live CircuitJS state
before and after restoration, and scenario/fault/repair validation uses a
solver-backed observational boundary with deliberate NPN presentation state.
The DC meter verifier covers stable NPN control/collector readings and the
cross-family LED/RC paths without placeholder flicker. Fresh independent review
passed; Task 38 remains unstarted.

---

## Task 38 — NMOS Low-Side Switch Family

**Status:** `[x]` Complete — correction passed independent review and primary
validation. At Task 38 acceptance the old provisional roadmap named Relay
Driver as Task 39; the audit-driven future-roadmap reset below supersedes that
unstarted ordering.

### Goals

Introduce:

- gate-source voltage;
- high-impedance control;
- gate pull-down;
- drain/source reasoning;
- MOSFET D-S short/open behavior.

Avoid making it merely an NPN circuit with a different picture.

### Completion result

Task 38 adds the solver-backed `NMOS_LOW_SIDE_SWITCH` family using the real
`NMosfetElm` model, independent load/control supplies, stable
`LOAD_SUPPLY`/`CONTROL_INPUT`/`LOAD_NODE`/`DRAIN`/`GND` nets, and explicit
CircuitJS post mapping G=0, S=1, D=2 with the body diode
preserved. Healthy validation proves live VGS, VDS, load current, and
effectively zero gate current in both commanded states.

The control command switch is external infrastructure. The board-facing path
is `external control infrastructure -> J2.1 -> CONTROL_INPUT -> Q1.G/RPD.1`;
J2.1 is the commanded voltage after the switch. The board has no separate
`GATE_DRIVE` or `GATE` identity and no TP1/TP2 pseudo-headers. Board power off
opens both supply isolation switches while the real 100 kOhm RPD holds the
board control node low.

The family supports Q1-owned D-S open, D-S short, and gate-path-open faults.
The original physical part retains its private solver fault when loose or
reinstalled; a catalog replacement allocates a distinct fault-free NMOS. The
typed G/D/S physical package, installed/loose renderer and probe target,
replaceable Q1 slot, provider-owned one-sided PCB footprint, symptom-only
scenarios, generic live repair status, privacy checks, and stable layout parity
are implemented.

Quick Play appends NMOS at index 5 with normal-player seeds `{0, 1, 2}` and a
natural-fault canary for all three admitted faults. The permanent family canary
also checks ON/OFF voltage agreement at J2.1/RPD.1/Q1.G, compact visible
CONTROL_INPUT copper, and absence of obsolete pseudo-headers. The standalone
Edge harness remains blocked by WMI/CIM `Access denied` in this environment;
the required visible in-app Browser validation passed the corrected boards,
developer canary, layout verifier, NPN regression, and Quick Play seeds 0, 1,
and 2. Fresh screenshots cover the corrected board, 5 V/0 V gate and control
measurements, power-off removal, catalog replacement, and repair verification.

The earlier `FINAL PASS` recorded after commit `ee310d4` was overturned by
post-commit visible review: the player-visible PCB control/gate connectivity
did not match the solver graph. The correction moves command infrastructure
outside the board, collapses the physical path to `CONTROL_INPUT`, removes
TP1/TP2, fixes the original-fault switch attachment boundary, and reroutes the
gate branch around the drain trace. Primary architect final result: `FINAL
PASS`. No post-Task-38 production milestone has started.

---

# Audit-Driven Future Roadmap Reset

The future roadmap below was redesigned on 2026-08-18 from the accepted Task
38 implementation, the permanent project laws, the current architecture and
handoff report, all four completed architecture audits, recent repository
history, and the integrated workbench shell.

Completed Task 1–38 identities and history remain unchanged. The former
provisional Task 39–69 sequence had not started, so those future numbers are
reassigned below. The migration table near the end records where every former
future item moved, split, combined, or became conditional. Historical reports
that called Relay Driver “Task 39” describe the old provisional queue; they do
not override this reset.

Exactly one milestone is immediately eligible: Task 39. Later milestones are
not authority to continue automatically.

## Governing architecture gates

| Gate | Required evidence | What it prevents |
| --- | --- | --- |
| G1 — Player-operable diagnosis | Legal inputs, observations, isolation actions, repair, and customer retest exist for every admitted current route. | Validators silently exercising states the player cannot reproduce. |
| G2 — Diagnostic admission | Plausible physical fault owners are solver-separable through a safe legal plan and have a reachable repair/retest path. | Electrically valid but unfair challenges. |
| G3 — Physical envelope truth | Pad, body, lead, keep-out, courtyard, selection, and probe envelopes agree for every registered package. | Dense placement routing through rendered bodies or misleading hit areas. |
| G4 — Composition identity | Stable block-local/global identity, typed ports/domains, one runtime envelope, and versioned named randomness are proven. | Another monolithic generator layer or transient solver-node identity. |
| G5 — Two-block proof | One small composed challenge passes healthy, faulted, diagnostic, repair, retest, privacy, replay, and PCB checks. | Premature generic frameworks without a playable proof. |
| G6 — Composed acceptance | Intent values, purposeful support, staged rejection, composed faults, and complaint projection are solver-backed. | Decorative red herrings and unbounded candidate rejection. |
| G7 — Physical scalability | Envelope canaries, measured layout telemetry, region placement, and any evidence-required routing recovery pass a fixed corpus. | Claiming medium-board support by merely enlarging the canvas. |
| G8 — Difficulty release | Requested constraints and computed diagnostic/layout evidence agree for a versioned corpus. | Cosmetic labels or unfair difficulty. |
| G9 — Alpha readiness | Menu-to-results flow, replay, honest supported profiles, Resources, Settings, and no fake critical shell surface pass. | Shipping a developer fixture collection as a product loop. |
| G10 — High-energy/dynamic admission | Appropriate player controls, instruments, source limits, stress models, and repair actions exist. | Unobservable waveforms, uncontrolled shorts, or arbitrary secondary damage. |

## Permanent generation and product rules

- CircuitJS remains the electrical source of truth.
- GeneratedBoardInstance remains the one family-agnostic runtime result. A
  functional block is not a nested board instance.
- Stable component, pad, net, terminal, block, fault-locus, and physical-part
  identities never depend on CircuitJS node numbers, coordinates, insertion
  indexes, collection order, or random UUIDs.
- Semantic domains reject nonsensical composition before a solve; they never
  replace solver validation.
- Physical copper must match electrical connectivity. Placement and routing
  never depend on which component is faulted.
- Every admitted fault has a real solver effect, a coherent physical locus,
  legal diagnostic evidence, a reachable repair, and a player-operable retest.
- Menus, tabs, difficulty profiles, scoring, and assistance orchestrate the
  simulation; they do not own electrical truth.
- Difficulty may increase legitimate reasoning complexity but may not hide
  ordinary physical information, remove required controls, demand unavailable
  instruments, shrink targets into ambiguity, or create nonsense values.
- A generated candidate is rejected rather than patched with fake behavior.
- Every conditional routing, economy, multi-fault, or layer milestone requires
  the evidence gate stated below. “It would be useful eventually” is not enough.

---

# Phase 3 — Diagnostic Fairness and Existing-Family Closure

## Task 39 — Player-Operable Functional Inputs and Customer Retest Contract

**Status:** [>] Immediate next milestone

**Purpose:** Close the known gap between solver-capable validators and the
operations a normal player can actually perform.

**Dependencies:** Completed Tasks 1–38.

**Bounded goals:**

- Add one family-agnostic stable-ID capability for player-operable functional
  inputs and customer operations.
- Use it to let a normal player command both NPN and NMOS control inputs HIGH
  and LOW through the real external command infrastructure.
- Make the same public operation boundary available to deterministic validators;
  validators may automate it but may not be its only caller.
- Define a family-owned customer retest profile containing required input and
  power transitions, observable output, timing/repetition, and unaffected
  functions.
- Give RC an explicit player-visible power-cycle/output retest at its current
  difficulty.
- Define the distinction between live repair status, Finish Job, latched job
  completion, and later post-completion board behavior.

**Explicit non-goals:** No relay, diagnostic search engine, scope, difficulty
UI, independent bench supplies, persistent customer return, or new family.

**Acceptance criteria:**

- Visible normal-player interaction can reproduce and retest required NPN and
  NMOS ON/OFF behavior; CircuitJS produces the resulting J2.1 and load state.
- No UI path mutates BoardNet metadata or fabricates a pad voltage.
- Automated retest uses the same operation contract and restores prior state in
  finally-style cleanup.
- Every current family has a documented customer operation/retest profile.
- Board power remains distinct from simulation RUN/STOP and stored-energy
  safety remains enforced.

**Architectural invariants:** External stimulus stays outside the PCB when that
is the physical truth; stable IDs and solver ownership remain unchanged.

**Addresses:** Troubleshooting-solvability audit P0 input/retest finding and
product-session-flow requirement.

**Unlocks:** Task 40 only.

---

## Task 40 — Physical Fault Locus and Serviceability Admission

**Status:** [!] Blocked by Task 39

**Purpose:** Make the physical thing the player diagnoses agree with the solver
effect and the available repair.

**Dependencies:** Task 39 and the existing fault/physical-part runtime.

**Bounded goals:**

- Add a stable fault-locus classification for component-internal failure,
  terminal/lead attachment, connector contact, and trace/copper segment.
- Associate every currently admitted fault candidate with supported
  observation, isolation, replacement/repair, and retest operations.
- Resolve NPN LOAD_PATH_OPEN as an honest RLOAD internal lead/attachment failure
  with corroborating loose-part behavior, or remove it from normal admission
  until a distinct trace/path owner and repair primitive exist.
- Reconfirm that NMOS gate/D-S effects remain owned by the original Q1 and are
  observable at public terminals.
- Keep connector and trace faults incompatible until their real physical owners
  and repair actions exist.

**Explicit non-goals:** No new trace fault, connector fault, jumper, trace cut,
or generic repair implementation.

**Acceptance criteria:** All 14 currently admitted normal routes across LED,
diode, parallel, RC, NPN, and NMOS have a coherent chain:

    solver effect
      → stable physical locus
      → exposed diagnostic/isolation action
      → reachable repair
      → player-operable retest

Private solver switches never become fake PCB components, and physical-owner
counts rather than enum counts are available for later difficulty evidence.

**Architectural invariants:** Physical and electrical identities remain
separate but connected; hidden fault metadata remains private.

**Addresses:** Troubleshooting audit fault-ownership finding and Task 38’s
corrected control-boundary lesson.

**Unlocks:** Task 41 only.

---

## Task 41 — Diagnostic Solvability Verifier v1 and Complexity Evidence

**Status:** [!] Blocked by Tasks 39–40

**Purpose:** Add the missing family-agnostic proof that a challenge can actually
be diagnosed, repaired, and retested by a player.

**Dependencies:** Player operations from Task 39 and fault-locus/serviceability
metadata from Task 40.

**Bounded goals:**

- Enumerate plausible compatible fault candidates for one unchanged
  topology/layout at a time.
- Enumerate actual rendered probe endpoints, meter/power safety rules,
  player-controlled inputs, lift/remove/isolation actions, repairs, and retest.
- Sample solver-derived signatures with tolerances for a bounded library of
  physically reasonable action plans.
- Prove at least one safe legal sequence separates each admitted candidate from
  plausible alternatives, or place truly equivalent candidates in an explicit
  equivalent-repair class.
- Prove repair reachability and the customer-visible retest.
- Emit deterministic rejection reasons and complexity evidence.

**Explicit non-goals:** No exhaustive circuit theorem prover, arbitrary action
search, new instrument, new family, or player-facing answer/hint.

**Acceptance criteria:**

- All 14 current normal routes pass or are removed from admission.
- NPN C-E open versus load-path open, NMOS D-S open versus gate-path open, and
  RC open versus short receive explicit separating-plan evidence.
- Hidden command state, developer-only hooks, Finish Job pass/fail, destructive
  damage, and shotgun replacement do not count as diagnostic observations.
- Evidence includes plausible-owner count, minimum/worst plan depth, required
  input/power transitions, isolation actions, meter modes, temporal samples,
  rails/domains, parallel-path ambiguity, and unaffected-function retest.
- Results are deterministic for the route/version/seed.

**Architectural invariants:** Healthy/faulted validation remains separate from
diagnostic solvability; every measurement remains CircuitJS-backed.

**Addresses:** Both the troubleshooting and procedural-generation audits’
largest fairness gap.

**Unlocks:** Task 42 and the later difficulty-evidence contract.

---

## Task 42 — Existing-Family Diagnostic Diversity Proof

**Status:** [!] Blocked by Task 41

**Purpose:** Use the new verifier to improve one real challenge before
generalizing architecture further.

**Dependencies:** Task 41.

**Bounded goals:**

- Add one additional credible, physically distinct, solver-backed normal fault
  owner to one simple existing family, preferably LED or diode.
- Supply its isolation, repair, complaint compatibility, and retest path.
- Classify any remaining single-owner families as guided/Easy exercises rather
  than pretending that they are unfamiliar-board higher-difficulty content.
- Preserve current leaf families as regression fixtures.

**Explicit non-goals:** No new topology family, broad catalog expansion,
composition, difficulty selector, or hidden serviceability affordances.

**Acceptance criteria:** The selected family no longer reveals every normal
fault through one obvious repair slot, all candidates pass Task 41 evidence,
and normal UI still exposes authentic physical capabilities instead of hiding
them.

**Architectural invariants:** Diversity comes from real alternative physical
owners, not from concealing real information.

**Addresses:** Structural answer leakage and the request to keep delivering
playable value during architectural hardening.

**Unlocks:** Task 43 only.

---

## Task 43 — Physical Package and Interaction Envelope Contract

**Status:** [!] Blocked by Task 42

**Purpose:** Reconcile the physical geometry that routing, drawing, selection,
and probing currently describe independently.

**Dependencies:** Existing package/footprint/render provider registries; the
chosen roadmap order also requires Tasks 39–42 complete.

**Bounded goals:**

- Let each registered package declare pad geometry, visible body/lead envelope,
  body keep-out, routing courtyard, selection/hit envelope, and probe terminals
  in one coordinate system while keeping those concepts distinct.
- Add canaries for every current package, especially electrolytic, ceramic,
  TO-92 NPN, and TO-92 NMOS.
- Include declared installed geometry in compaction and layout containment.
- Preserve provider ownership; the router must not call renderer drawing code.

**Explicit non-goals:** No relay package, cosmetic redesign, new routing
algorithm, electrical change, SMD, rotation system, or multilayer model.

**Acceptance criteria:** Every current package passes envelope containment,
body/copper/silkscreen separation, selection/probe agreement, terminal identity,
and board-outline canaries across representative seeds. Existing electrical
behavior and stable layout identity remain unchanged unless a documented
geometry-version bump is necessary.

**Architectural invariants:** Draw-only effects do not imply probeability;
footprint geometry does not decide electrical behavior.

**Addresses:** PCB scalability and component-visual-realism audits’ shared
architecture prerequisite.

**Unlocks:** Task 44 and safe later package expansion.

---

# Phase 4 — Composition Kernel and Small Real Proof

## Task 44 — Functional Block Descriptor and Stable Namespace Contract

**Status:** [!] Blocked by Tasks 41 and 43

**Purpose:** Define the smallest reusable generation unit above the existing
leaf-family runtime.

**Dependencies:** Stable board/runtime identities and diagnostic-operation
contracts.

**Bounded goals:**

- Define block type/schema version, stable instance key, deterministic
  parameters, local component/pad/net/endpoint IDs, required/optional roles,
  and declared ports.
- Define global ID derivation from device schema, stable block instance key,
  entity kind, and local ID.
- Keep optional-block insertion from renumbering existing entities.
- Define ownership: blocks own local contributions and port declarations;
  device intent owns wiring; the assembler alone owns global allocation and
  net merging.

**Explicit non-goals:** No CircuitJS composition, PCB, general random netlist,
new family, or rewrite of current generators.

**Acceptance criteria:** Two in-memory block descriptors validate local identity,
stable namespace behavior, role declarations, and insertion stability. No ID
depends on insertion index, coordinate, solver node, collection order, or
random UUID.

**Architectural invariants:** A block is not a GeneratedBoardInstance and
physical part identity remains distinct from logical component identity.

**Addresses:** Procedural-generation audit functional-block and stable-identity
findings.

**Unlocks:** Task 45 only.

---

## Task 45 — Typed Electrical Domains and Port Compatibility Preflight

**Status:** [!] Blocked by Task 44

**Purpose:** Prevent semantically nonsensical block connections before building
an expensive solver candidate.

**Dependencies:** Task 44 port contract.

**Bounded goals:**

- Represent source/rail, return/ground, control, analog, digital, passive,
  load-interface, and isolation relationships in a semantic model separate from
  BoardNet.
- Carry direction, source/sink/passive behavior, nominal/allowed range,
  reference, drive/loading mode, active level/threshold, mergeability,
  isolation, and required physical accessibility where relevant.
- Express regulators, dividers, level shifters, relays, and isolation barriers
  as explicit adapters/blocks rather than magic net merges.
- Adapt existing external-input and current leaf metadata without changing
  leaf behavior.

**Explicit non-goals:** No second electrical solver, mains certification model,
arbitrary symbolic analysis, or new multi-rail family.

**Acceptance criteria:** Valid and invalid port connections classify
deterministically before CircuitJS graph construction; representative voltage,
direction, reference, and isolation mismatches are rejected; current leaves
still generate and solve unchanged.

**Architectural invariants:** Semantic compatibility is a preflight only;
CircuitJS remains the final electrical truth.

**Addresses:** Procedural-generation and troubleshooting audits’ multi-domain
composition risk.

**Unlocks:** Task 46 only.

---

## Task 46 — Versioned Challenge Descriptor, Named Sub-Seeds, and Constraint Input

**Status:** [!] Blocked by Tasks 41, 44, and 45

**Purpose:** Make challenge identity replayable and prevent one subsystem’s
evolution from scrambling every unrelated result.

**Dependencies:** Stable block keys, diagnostic evidence dimensions, and typed
ports/domains.

**Bounded goals:**

- Define a versioned challenge descriptor containing root seed, generator/schema
  version, device-intent ID, and difficulty-profile ID/version.
- Derive platform-stable named streams for topology, each stable block instance,
  values, support selection, fault, scenario, placement, routing, and
  presentation.
- Canonically sort stable IDs before indexed selection.
- Add a typed GenerationConstraints input shape for later difficulty profiles;
  do not enable the four player presets yet.
- Preserve or explicitly version the current leaf replay behavior.

**Explicit non-goals:** No menu, share link, four difficulty labels, composed
board, or silent promise that future algorithm changes preserve old geometry.

**Acceptance criteria:** Exact descriptor replay reproduces the accepted
challenge; optional-block insertion and one named-stream change do not perturb
unrelated block values/fault/scenario streams; debug mode can report root seed,
versions, named seeds, and rejection reason without exposing them in ordinary
play.

**Architectural invariants:** No ordinary object hash, map iteration order, or
live Random consumption defines durable identity.

**Addresses:** Both generation and PCB audits’ deterministic-partitioning
finding and the product shell’s seed-replay prerequisite.

**Unlocks:** Task 47 only.

---

## Task 47 — Bounded Assembler and Composed Contribution Contracts

**Status:** [!] Blocked by Tasks 44–46

**Purpose:** Assemble reusable blocks into the existing runtime without
creating a parallel architecture.

**Dependencies:** Descriptor, domain, identity, seed, input/retest, and
solvability contracts.

**Bounded goals:**

- Assemble namespaced block contributions into one TroubleshootBoard, one
  CircuitJS graph, one PhysicalBoardRuntime, and one GeneratedBoardInstance.
- Reuse existing simulation bindings, fault engine, mutation controller,
  physical registries, challenge lifecycle, and measurement adapters.
- Compose local healthy assertions with one device-level functional behavior.
- Compose block-owned fault descriptors, repair capabilities, input/retest
  requirements, and observed-behavior semantics.
- Keep final complaint and full PCB ownership at the device/challenge services,
  not inside a block.

**Explicit non-goals:** No player-facing composed challenge, general device
generator, auxiliary system, or broad leaf-family rewrite.

**Acceptance criteria:** A bounded two-block canary produces one structurally
valid global logical/solver/runtime envelope with stable local-to-global maps,
and every owned solver element, slot, binding, fault effect, and physical part
has exactly one authoritative owner.

**Architectural invariants:** No nested GeneratedBoardInstance, second fault
engine, second inventory, or renderer-owned connectivity.

**Addresses:** Procedural-generation audit’s missing assembler boundary.

**Unlocks:** Task 48 only.

---

## Task 48 — First Two-Block Composed Controlled-Indicator Challenge

**Status:** [!] Blocked by Tasks 41 and 43–47

**Purpose:** Prove the new contracts immediately with a small real playable
challenge.

**Dependencies:** All composition-kernel gates and existing NMOS/LED physics.

**Bounded goals:**

- Compose an NMOS low-side-driver block and an LED-load block, plus explicit
  external control/power adapters.
- Provide a player-operated ON/OFF control and visible customer retest.
- Admit at least one physical fault owner from each block, such as a gate-path
  failure and a load/resistor-path failure.
- Select one solver-backed fault, project a symptom-only complaint, provide a
  legal measurement plan that separates the candidates, allow a current repair,
  and verify end-to-end function.
- Produce a PCB from the assembled board. A bounded small-board realization is
  allowed, but no block may own the complete layout and this task makes no
  medium-board scalability claim.
- Preserve the monolithic NMOS family unchanged as a regression/parity fixture.

**Explicit non-goals:** No relay, regulator, auxiliary red herring, generic
medium-board router, multiple faults, or rewrite of all existing families.

**Acceptance criteria:** Deterministic replay reaches one global board and
runtime; healthy/faulted/diagnostic/repair/retest/privacy/layout checks pass;
normal-player Browser validation proves the complete control-to-repair loop;
the implementation does not copy a complete family generator under a new name.

**Architectural invariants:** CircuitJS owns all voltages/currents and stable
identity survives composition and repair.

**Addresses:** The audits’ requested contract → implementation → real challenge
proof pattern.

**Unlocks:** Task 49 only and establishes G5.

---

## Task 49 — Intent-Driven Value Synthesis v1

**Status:** [!] Blocked by Task 48

**Purpose:** Stop family/block growth from duplicating hard-coded value tables.

**Dependencies:** Typed interfaces and one proven composed block path.

**Bounded goals:** For one proven block, derive a finite set of standard-series
values and package/rating choices from target function, interface range,
tolerance, and power/voltage/current margin; then let the seed select among
already valid candidates.

**Explicit non-goals:** No symbolic circuit-design engine, unrestricted part
search, or conversion of every leaf family.

**Acceptance criteria:** Values are deterministic, standard/catalog-backed,
within rating margins, rejected when intent constraints fail, and accepted
only when CircuitJS healthy behavior meets the declared target.

**Architectural invariants:** Design math proposes bounded candidates;
CircuitJS verifies actual behavior.

**Addresses:** Procedural-generation audit value-synthesis finding.

**Unlocks:** Task 50 only.

---

## Task 50 — Purposeful Auxiliary and Healthy-Support Block Proof

**Status:** [!] Blocked by Tasks 48–49

**Purpose:** Add diagnostic realism without decorative junk.

**Dependencies:** Composition, typed domains, intent values, and solvability
verification.

**Bounded goals:** Add one role-bearing healthy support block, such as a power
indicator, decoupling/protection path, or secondary output, with explicit
domain, loading, access, physical, and scenario relevance contracts.

**Explicit non-goals:** No random orphan components, broad red-herring library,
new primary device family, or decorative-only board clutter.

**Acceptance criteria:** The support block changes the real graph/layout, has a
technician-inferable purpose, remains healthy under every admitted selected
fault, creates no unresolved equally plausible complaint explanation, and is
included in unaffected-function retest.

**Architectural invariants:** “Red herring” describes scenario relevance of a
real healthy function; it is not a component type.

**Addresses:** Troubleshooting and procedural audits’ auxiliary-circuit rules.

**Unlocks:** Task 51 only.

---

## Task 51 — Composed Acceptance and Rejection Pipeline v1

**Status:** [!] Blocked by Tasks 48–50

**Purpose:** Generalize the first proof into a bounded, observable generation
pipeline before adding more device blocks.

**Dependencies:** Composed proof, intent values, support block, fault-locus,
diagnostic, and replay contracts.

**Bounded goals:**

- Stage cheap descriptor/domain/value checks before CircuitJS and physical work.
- Validate healthy states, bounded block-owned fault candidates, meaningful
  symptoms, legal diagnostic signatures, repairs, retest, scenario projection,
  package assignment, and final physical validity.
- Reject no-op, globally masking, unobservable, unrepairable, unintentionally
  indistinguishable, or unroutable candidates.
- Project complaints from solver-validated device outputs/state transitions,
  never from fault type.
- Emit machine-readable deterministic rejection reasons and bounded retry
  budgets.

**Explicit non-goals:** No unrestricted challenge generator, multiple faults,
large-board support, or optimization based on mutable solver-state reuse.

**Acceptance criteria:** A fixed corpus of leaf and composed candidates takes a
deterministic path through the stages; failures identify the responsible stage;
accepted candidates retain one runtime architecture and pass end-to-end
solvability.

**Architectural invariants:** Physical failure rejects a realization and never
rewrites electrical semantics.

**Addresses:** Procedural audit’s staged pipeline and composed fault/scenario/
repair contract.

**Unlocks:** Task 52 only and establishes G6.

---

# Phase 5 — Measured Physical Scalability

## Task 52 — Deterministic Layout Stress Corpus and Structured Telemetry

**Status:** [!] Blocked by Tasks 43, 46, 48, and 51

**Purpose:** Measure what actually fails before choosing routing complexity or
claiming a component-count limit.

**Dependencies:** Envelope contract, named layout streams, block metadata, and
a first composed fixture.

**Bounded goals:**

- Build a versioned deterministic corpus spanning chains, parallel branches,
  high-fanout rails, adverse connector order, multiple connectors, current
  leaves, and reduced composed boards.
- Record rejection stage/reason, attempts, placement fallback, route/net
  expansion, length, bends, detour, near-clearance occupancy, density,
  renderer scale, minimum probe separation, validation cost, and fingerprint.
- Report wall-clock timing separately from deterministic identity.

**Explicit non-goals:** No placement/routing behavior change, invented maximum
component count, normal-player telemetry, or performance rewrite.

**Acceptance criteria:** Fixed seed/version produces identical structural
reports/fingerprints; failures identify placement, net routing, clearance,
labels, bounds, or quality; the report defines measured supported corpus bands
and the evidence for Tasks 53–56.

**Architectural invariants:** Telemetry does not affect accepted electrical or
layout semantics.

**Addresses:** PCB audit’s required stress/telemetry gate.

**Unlocks:** Task 53 only.

---

## Task 53 — Subsystem and Region-Aware Placement

**Status:** [!] Blocked by Task 52 and block/domain metadata from Tasks 44–45

**Purpose:** Place composed boards by functional regions and real access needs,
not only raw adjacency.

**Dependencies:** Measured baseline, envelope truth, block IDs/roles, typed
domains, and connector interfaces.

**Bounded goals:** Add bounded placement problems with subsystem roles,
connector-edge anchors, source/sink relationships, high-degree-net corridors,
provider-declared orientation candidates, density budgets, and probe/readability
limits. Placement remains fault-independent.

**Explicit non-goals:** No route-layer model, universal CAD placer, arbitrary
rotation, fault-shaped layout, or automatic answer-highlighting regions.

**Acceptance criteria:** Seeded multi-connector/two-region fixtures preserve
stable IDs and provider envelopes, meet edge/readability/probe constraints, and
improve measured route rejection or congestion against Task 52 without hidden
connectivity.

**Architectural invariants:** Regions aid physical realism but do not reveal the
selected fault or determine electrical behavior.

**Addresses:** PCB and procedural audits’ subsystem-placement prerequisite.

**Unlocks:** Re-run the Task 52 corpus, then apply the conditional sequence
below.

---

## Task 54 — High-Degree Net Tree and Visible-Trunk Routing

**Status:** [~] Conditional after Task 53

**Purpose:** Replace root-star fanout when measured high-degree-net behavior is
the remaining blocker.

**Dependencies:** Task 53 and the versioned Task 52/53 corpus evidence.

**Entry condition:** Task 52/53 evidence shows root-star length, fanout
congestion, or rail-corridor failure outside the supported composed target.

**Bounded goals:** Add deterministic visible tree/trunk candidates and semantic
priority for declared rails/high-fanout nets. Do not infer role from names such
as GND alone.

**Explicit non-goals:** No plane, hidden copper, via, layer, or universal
Steiner-tree optimizer.

**Acceptance criteria:** All endpoints remain visibly/electrically connected;
target fixtures improve trunk reuse/fanout/congestion metrics while clearance,
courtyard, determinism, and route-quality checks remain green.

**Architectural invariants:** Visible copper remains the physical truth.

**Addresses:** PCB audit high-degree routing recommendation.

**Unlocks:** Re-run the corpus; Task 55 only if its entry condition is met,
otherwise Task 57.

---

## Task 55 — Bounded Deterministic Rip-Up and Reroute

**Status:** [~] Conditional after Task 53 or 54

**Purpose:** Recover from measured route-order failures without unbounded
regeneration.

**Dependencies:** Task 53 and, when its entry condition was met, the recorded
Task 54 outcome.

**Entry condition:** A supported fixture still fails primarily because an
earlier legal route blocks a later route.

**Bounded goals:** Add deterministic alternate net order/path candidates and a
strict small backtracking budget with structured exhaustion.

**Explicit non-goals:** No clearance relaxation, arbitrary local component
movement, unbounded search, or silent disconnection.

**Acceptance criteria:** A recorded order-sensitive fixture succeeds through
the same bounded alternatives for the same seed; unrelated crossings remain
illegal; exhaustion rejects cleanly.

**Architectural invariants:** Routing recovery never changes the logical graph,
fault, or solver behavior.

**Addresses:** PCB audit bounded rerouting recommendation.

**Unlocks:** Re-run the corpus; Task 56 only if its entry condition is met,
otherwise Task 57.

---

## Task 56 — Visible Generated Link Fallback

**Status:** [~] Conditional after Tasks 53–55 evidence

**Purpose:** Provide a believable one-sided factory-routing escape hatch before
adding layers.

**Dependencies:** Task 53 plus the recorded outcomes of every evidence-required
Task 54–55 routing step.

**Entry condition:** Supported composed topologies still fail genuine crossing
constraints after applicable placement/tree/reroute work, and a small capped
link budget resolves them.

**Bounded goals:** Add a real visible factory link/zero-ohm component with stable
component/pad/package identity, solver backing, selection/probes, fingerprints,
and a strong quality penalty/cap.

**Explicit non-goals:** This is not the player-created jumper of Task 71, an
invisible renderer bridge, unlimited links, or a second copper layer.

**Acceptance criteria:** The link carries only its declared connectivity and is
visible/probeable; if insertion changes logical net segmentation or the solver
graph, the candidate returns through healthy/fault/solvability validation;
excess link demand rejects.

**Architectural invariants:** Physical overpass semantics may not be erased by
renaming two disconnected segments as one magical net.

**Addresses:** PCB audit’s evidence-gated link recommendation.

**Unlocks:** Task 57. If a supported corpus still fails solely from unavoidable
crossings after the capped link policy, insert a bounded Task 56(A) two-sided
through-hole prototype before Task 57. That prototype must include front/back
copper, explicit vias, layer-aware clearance, visible board-side interaction,
and probe semantics together. Do not create invisible backside connectivity or
more than two layers.

---

## Task 57 — Supported Composed-Board Physical Scalability Gate

**Status:** [!] Blocked by Task 53 and all evidence-required Tasks 54–56/56(A)

**Purpose:** Convert physical work into a defensible supported envelope.

**Dependencies:** Re-run of the versioned stress corpus after the applicable
conditional tasks.

**Bounded goals:** Record the supported small/composed board bands, failure
policy, route/link/layer policy, readable scale, minimum probe separation, and
quality bounds consumed by generation and difficulty.

**Explicit non-goals:** No promise of universal medium/large boards, SMD,
planes, arbitrary layers, or manufacturing DRC.

**Acceptance criteria:** Every admitted target corpus board is deterministic,
fully connected, envelope-valid, legible, and unambiguously probeable; every
rejected board has a structured reason. A task is skipped when evidence does
not justify it, and that skip is recorded rather than implemented “just in
case.”

**Architectural invariants:** Component count alone is not the support limit.

**Addresses:** PCB audit medium-board gate and difficulty-readability rules.

**Unlocks:** Task 58 only and establishes G7.

---

# Phase 6 — Difficulty, Product Flow, and Desktop Alpha

## Task 58 — Difficulty Profile and Computed Assessment Foundation

**Status:** [!] Blocked by Tasks 41, 46, 51, and 57

**Purpose:** Make difficulty a typed generation constraint plus verified result,
not a label applied after generation.

**Dependencies:** Diagnostic evidence, versioned request, composed rejection,
and physical metrics.

**Bounded goals:**

- Define DifficultyProfileId values EASY, MEDIUM, HARD, and PSYCHOTIC.
- Define versioned DifficultyProfile constraints, assistance policy, allowed
  instruments/features, and physical limits.
- Define computed ChallengeComplexityMetrics/DifficultyAssessment from solver,
  diagnostic-plan, and layout evidence.
- Reject a candidate when requested constraints and computed evidence disagree.
- Keep supported/unsupported profile availability explicit.

**Explicit non-goals:** No menu, public profile enablement, four separate
generators, scoring, or arbitrary thresholds without corpus evidence.

**Acceptance criteria:** A deterministic corpus can be constrained and assessed
by block/component/domain count, plausible physical owners, diagnostic depth,
parallel paths, isolation/input transitions, temporal/instrument needs,
purposeful auxiliaries, complaint specificity, wrong-repair consequences, and
layout complexity.

**Architectural invariants:** Every profile passes the same solvability and
physical-readability floor.

**Addresses:** All audits and the requested four-mode architecture.

**Unlocks:** Task 59 only.

---

## Task 59 — EASY and MEDIUM Profile Calibration and Admission

**Status:** [!] Blocked by Task 58

**Purpose:** Enable only the profiles the current proved systems can honestly
support.

**Dependencies:** Difficulty foundation and versioned leaf/composed corpus.

**Bounded goals:**

- Calibrate EASY around guided/leaf/static routes with a direct legal plan and
  optional teaching help.
- Calibrate MEDIUM around multiple plausible owners, parallel-path or
  isolation/input reasoning, and bounded two-block/domain complexity.
- Persist requested profile/version with challenge identity.
- Keep HARD and PSYCHOTIC unavailable rather than showing nonfunctional labels.

**Explicit non-goals:** No Hard/Psychotic approximation, hidden physical
information, randomized inconvenience, or mode-specific physics.

**Acceptance criteria:** Requested profile constrains generation, accepted
evidence fits its range, misclassified candidates reject, and every selectable
route remains solver-backed and diagnostically admitted.

**Architectural invariants:** Assistance may vary; necessary controls,
measurements, physical markings, and accessibility do not.

**Addresses:** Product-shell difficulty requirement and alpha prerequisites.

**Unlocks:** Tasks 60–62.

---

## Task 60 — Resources v1

**Status:** [!] Blocked by Tasks 41 and 59

**Purpose:** Turn the prominent Resources placeholder into legitimate
technician help before alpha.

**Dependencies:** Stable diagnostic concepts and authentic physical metadata.

**Bounded goals:** Provide generic meter safety/use, resistor color-code,
polarity/package identification, isolation guidance, and public catalog/
datasheet-style references. EASY may link optional contextual teaching help.

**Explicit non-goals:** No generated fault answer, private original value,
expected hidden measurement, dynamic “next correct probe,” or schematic reveal
during normal play.

**Acceptance criteria:** Content is useful across challenges and difficulties,
uses only public/nameplate/reference information, remains available without
altering the board, and passes privacy/accessibility review.

**Architectural invariants:** Resources educate; they never inspect hidden
challenge truth.

**Addresses:** Workbench shell and Guided Beginner requirements.

**Unlocks:** Task 61.

---

## Task 61 — Settings v1 and Workbench-Shell Truthfulness

**Status:** [!] Blocked by Task 60

**Purpose:** Make Settings real and ensure no prominent shell surface pretends
to be connected to gameplay when it is not.

**Dependencies:** Stable workbench shell and difficulty separation.

**Bounded goals:**

- Add presentation/accessibility preferences such as audio, contrast/color,
  text/UI scale, reduced motion, and safe interaction preferences.
- Keep difficulty, solver parameters, board power semantics, and challenge
  generation outside Settings.
- Keep TOOLS as the real instrument/workbench surface, not a second instrument
  implementation.
- Remove or hide the current mock Shop/cart from normal alpha navigation, or
  replace it with an explicitly read-only preview that cannot be mistaken for
  board inventory. Task 70 defines the real Shop.
- Preserve modal focus, keyboard isolation, and normal workbench state.

**Explicit non-goals:** No gameplay cheats, economy, simulation-timestep
setting, catalog acquisition, or electrical mutation from JavaScript shell
state.

**Acceptance criteria:** Preferences persist safely, do not change simulation
truth, and no visible alpha tab claims purchasing/inventory behavior it does
not have.

**Architectural invariants:** DOM/local shell state is a view preference, not
authoritative session, inventory, profile, or challenge state.

**Addresses:** Product-shell Settings, Tools, Shop-honesty, and basic
accessibility requirements.

**Unlocks:** Task 62.

---

## Task 62 — Main Menu, Seed Replay, and Explicit Session/Results Flow

**Status:** [!] Blocked by Tasks 46, 48, 59–61

**Purpose:** Replace direct random-workbench launch as the mature product flow.

**Dependencies:** Versioned descriptor, admitted profiles, composed proof,
Resources, Settings, and player retest.

**Bounded goals:**

- Add a small product/session coordinator and a proper main menu.
- Support Start Repair/Quick Play, supported difficulty, versioned seed/replay
  entry, Resources, Settings, and developer/debug separation.
- Route a typed ChallengeLaunchRequest into existing generation services.
- Add explicit ticket/workbench, player retest, Finish Job, result, next/replay,
  and return-to-menu states.
- Retain direct explicit routes for regression/developer use.

**Explicit non-goals:** No giant front-end framework, family-specific menu
branches, electrical rules in navigation, Shop economy, save/resume, or
unsupported difficulty labels.

**Acceptance criteria:** The visible flow is:

    launch
      → main menu
      → supported profile or replay descriptor
      → generation/admission
      → service ticket and workbench
      → repair and player-operated retest
      → Finish Job
      → results
      → next, replay, or menu

The menu never calls a family generator directly or decides repair success.
Replay reproduces the versioned challenge or gives a clear version error.

**Architectural invariants:** Results record solver-backed outcome; they do not
become electrical truth.

**Addresses:** Main-menu, seed-replay, and product/session-flow requirements.

**Unlocks:** Task 63 only.

---

## Task 63 — First Desktop Alpha Readiness Gate

**Status:** [!] Blocked by Tasks 39–62, excluding skipped evidence-conditional
routing tasks

**Purpose:** Decide whether the first public/playable desktop alpha is honest
and supportable.

**Dependencies:** Gates G1–G8 and the complete menu/session flow.

**Bounded goals:** Run and record the desktop-alpha admission checklist against
the supported route/profile corpus and product shell; either declare the exact
supported alpha surface or reject readiness with structured blockers.

**Acceptance criteria:**

- Every selectable route is solver-backed, diagnostically admitted, physically
  owned, repairable, and visibly retestable.
- Every required functional input is player-operable.
- One versioned replay descriptor/root seed is visible and reproducible.
- EASY and MEDIUM are real supported profiles; unsupported profiles are absent.
- Menu → challenge → result → menu works and handles generation rejection.
- Normal play leaks no fault/answer/private value.
- Resources and basic Settings are functional.
- Shop is either honestly hidden/read-only or already authoritative; no mock
  cart is presented as gameplay.
- Package envelopes, readable rendering, probe access, deterministic generation,
  and rejection telemetry pass the target corpus.
- Desktop keyboard/focus/accessibility basics pass.

**Explicit non-goals:** Alpha does not require Shop/economy, scoring, save/
resume, mobile, multilayer, scope unless an exposed profile needs it, all four
difficulty levels, multiple faults, or photorealistic art.

**Architectural invariants:** Alpha cannot waive CircuitJS truth, fairness,
identity, safety, or privacy.

**Addresses:** Requested alpha decision gate.

**Unlocks:** Task 64 and later post-alpha expansion. Stop and report the gate
result; do not automatically begin Task 64.

---

# Phase 7 — Bounded Visual and Presentation Improvements

## Task 64 — Static Axial and PCB Surface Realism v1

**Status:** [!] Blocked by Tasks 43 and 63

**Purpose:** Improve physical readability after the architecture and first
alpha gate, without displacing fairness work.

**Dependencies:** Envelope contract and accepted composed-board rendering.

**Bounded goals:** Add shared installed/tray axial resistor/diode primitives,
accurate band spacing and cathode stripe, bounded two-pass leads, plus restrained
pad annulus/drill, copper edge/center, board-edge, and silkscreen treatment.

**Explicit non-goals:** No pad/trace centerline, hit radius, footprint, terminal,
fault, layout fingerprint, radial/TO-92 size, gradient framework, or electrical
change.

**Acceptance criteria:** Markings/polarity remain accurate at low zoom,
installed/loose probe and selection behavior is unchanged, geometry fingerprints
stay stable, and target-board repaint/readability remains bounded.

**Architectural invariants:** Cosmetic depth never obscures copper, pads,
polarity, or probe affordance.

**Addresses:** Component-visual-realism audit Tier 1 and PCB surface request.

**Unlocks:** Task 65.

---

## Task 65 — Continuous LED Visual State and Dynamic Physical Lens

**Status:** [!] Blocked by Tasks 39, 43, and 64

**Purpose:** Make visible LED brightness follow the solver continuously.

**Dependencies:** Player-operable output changes and envelope-safe rendering.

**Bounded goals:**

- Extract one normalized intensity accessor from the existing LEDElm
  current/max-brightness/logarithmic curve.
- Pass intensity through GeneratedComponentOperationalStates and physical
  render context while preserving the boolean functional predicate.
- Render the installed physical lens from solver intensity and LedNameplate RGB
  with off, dim, nominal, and saturated states and restrained color-matched
  bloom.
- Keep loose parts at zero intensity.

**Explicit non-goals:** No duplicated renderer brightness equation, absolute
current, renderer time advancement, fake interpolation, or damage visuals.

**Acceptance criteria:** Formula edge cases, signed current, saturation,
replacement rebinding, reversed/faulted/off states, and nameplate color pass;
the intensity seam is independently tested before visual consumption; repair/
complaint thresholds remain unchanged.

**Architectural invariants:** Solver owns intensity; physical nameplate owns
lens color; renderer owns presentation only.

**Addresses:** Visual audit’s continuous/dynamic LED recommendation.

**Unlocks:** Task 66.

---

# Phase 8 — Reusable Block, Repair, and Instrument Expansion

## Task 66 — Relay Driver Reusable Block and Composed Load Proof

**Status:** [!] Blocked by Tasks 43, 45, 49, 51, 57, and 65

**Purpose:** Transform the former Relay Driver family into the first post-proof
reusable electromechanical block.

**Dependencies:** Composition, domains, values/ratings, diagnostic admission,
physical envelope, supported layout, and the completed Task 64–65 post-alpha
visual sequence.

**Bounded goals:** Model coil, contacts, driver, flyback protection, control and
switched-load domains, physical relay package, player input/retest, and
coherent coil/contact/driver fault loci in one composed controlled-load
challenge.

**Explicit non-goals:** No standalone monolithic family clone, mains claim,
contact arcing physics, broad relay library, or uncontrolled short fault.

**Acceptance criteria:** Coil and contact sides remain electrically/physically
distinct, flyback has a real role, admitted faults are solver-separable and
repairable, and the composed load passes player-operated energized/de-energized
retest.

**Architectural invariants:** Relay contacts/driver are explicit blocks and do
not magic-merge domains.

**Addresses:** Former Task 39, transformed.

**Unlocks:** Task 67.

---

## Task 67 — Independent Rails, External Loads, and Source-Bound Semantics

**Status:** [!] Blocked by Tasks 45, 51, and 66

**Purpose:** Replace one global power toggle as the only operating model before
multi-rail challenges.

**Dependencies:** Typed domains and player-operation contract.

**Bounded goals:** Add independent but scenario-controlled external rails/load
connections, explicit source bounds/nominal state, safe isolation, and retest
semantics without making Settings own them.

**Explicit non-goals:** No adjustable bench supply UI, current limiting, rail
short admission, or arbitrary source topology.

**Acceptance criteria:** A composed device can independently control and report
required rails/loads; active measurement safety and queued power cleanup remain
correct; scenarios specify which sources are available to the player.

**Architectural invariants:** UNPOWERED remains genuine source isolation and is
not “voltage set to zero.”

**Addresses:** Troubleshooting audit global-power limitation.

**Unlocks:** Task 68.

---

## Task 68 — Regulator and Multi-Rail Reusable Block

**Status:** [!] Blocked by Tasks 49, 51, 57, and 67

**Purpose:** Transform the former regulator family into a typed rail-producing
block.

**Dependencies:** Independent source/load semantics, domains, intent values,
ratings, and composed diagnostics.

**Bounded goals:** Add input/output/return and optional enable interfaces,
regulated output behavior, physical identity, value/rating checks, player
enable/load retest, and bounded open/missing-output/enable-path faults.

**Explicit non-goals:** No normal rail-short or shorted-load fault until Task
75 current limiting/source consequences exist; no switching-regulator physics
or bespoke monolithic family.

**Acceptance criteria:** Valid/invalid domain connections preflight correctly,
live regulation/enable behavior comes from CircuitJS, and each admitted fault
has a fair multi-rail diagnostic and repair path.

**Architectural invariants:** Regulator semantic metadata does not manufacture
the output rail.

**Addresses:** Former Task 40, transformed.

**Unlocks:** Task 69.

---

## Task 69 — Player-Stimulated Sensor and Comparator Blocks

**Status:** [!] Blocked by Tasks 39, 45, 51, 57, and 68

**Purpose:** Transform the former comparator/sensor family into reusable analog
input and decision blocks.

**Dependencies:** Player-operation contract, typed analog/reference domains,
composition, and multi-rail support.

**Bounded goals:** Add a player-adjustable sensor condition, reference/divider,
comparator output/pull behavior, threshold retest, and bounded open-sensor,
divider/reference, pull, and output-path faults.

**Explicit non-goals:** No hidden sensor-state validator as the only stimulus,
op-amp library, noisy analog simulation campaign, or fake threshold result.

**Acceptance criteria:** A normal player can sweep the real input through the
threshold, observe and measure both sides, distinguish admitted faults, repair,
and repeat the customer operation.

**Architectural invariants:** CircuitJS decides the analog transition.

**Addresses:** Former Task 41, transformed.

**Unlocks:** Task 70 and broader block-library work.

---

## Task 70 — Authoritative Shop and Replacement Source v1

**Status:** [!] Blocked by Tasks 61, 66, 68, and 69

**Purpose:** Make SHOP a real gameplay surface only after the component catalog
is broad enough to justify it.

**Dependencies:** Shared physical catalogs, runtime inventory/capabilities, and
several reusable component blocks.

**Bounded goals:** Browse authoritative catalog/nameplate projections, filter
compatible replacements, and acquire a selected free/training part through
existing workbench capabilities and PhysicalBoardRuntime.

**Explicit non-goals:** No hard-coded JavaScript product list, fake cart,
economy, price balancing, persistent progression, or direct CircuitJS mutation
from the Shop.

**Acceptance criteria:** Catalog data agrees with actual replacement
specifications; acquisition creates a distinct runtime physical identity;
installation remains a separate real workbench action; wrong compatible parts
remain possible; hidden fault/private original values never leak.

**Architectural invariants:** Shop is a source/orchestrator, not inventory or
electrical truth.

**Addresses:** Workbench Shop requirement.

**Unlocks:** Task 71. Economy remains a separate late decision gate after
persistent progression.

---

## Task 71 — Player Jumper Wires

**Status:** [!] Blocked by Tasks 40, 41, 51, and 57

**Purpose:** Add the first general alternate-repair/bypass primitive.

**Dependencies:** Stable accessible endpoints, fault-locus/serviceability,
solvability, and physical-layout identity.

**Bounded goals:** Let the player connect two accessible points with a visible,
stable, solver-backed jumper through the board-mutation owner; allow correct
repair, diagnosis, and harmful shorts.

**Explicit non-goals:** No generated factory link reuse without distinct
ownership, no automatic safety protection, and no hidden net merge.

**Acceptance criteria:** Jumper identity/render/probes/history and undo/reset
are coherent; CircuitJS determines every consequence; solvability can declare
a jumper-required or jumper-optional repair plan.

**Architectural invariants:** Wrong player actions remain possible.

**Addresses:** Former Task 43 and repair-toolbox timing.

**Unlocks:** Task 72.

---

## Task 72 — Stable Copper-Segment Identity and Trace Cutting

**Status:** [!] Blocked by Tasks 43, 57, and 71

**Purpose:** Make a physical copper cut target an electrically meaningful,
durable object.

**Dependencies:** Physical connectivity truth and mutation lifecycle.

**Bounded goals:** Define stable eligible copper-segment identity, visible cut
state, correct graph opening, board/net semantic continuity, and diagnostic/
accidental cut history.

**Explicit non-goals:** No arbitrary pixel cut, automatic repair, planes,
hidden layers, or trace-fault admission without repair reachability.

**Acceptance criteria:** Cutting selected copper opens exactly its declared
electrical connection, never another same-net branch; rendered state, probes,
reset, export/history policy, and solvability evidence agree.

**Architectural invariants:** Renderer geometry alone never decides what graph
edge is cut.

**Addresses:** Former Task 44.

**Unlocks:** Task 73.

---

## Task 73 — Trace Repair

**Status:** [!] Blocked by Tasks 71–72

**Purpose:** Complete the copper-fault serviceability loop.

**Dependencies:** Stable cut identity and jumper primitive.

**Bounded goals:** Restore an eligible cut directly or with a jumper, preserve
repair history, and admit trace/connector-open challenges only when their
physical owner and repair/retest paths are proven.

**Explicit non-goals:** No automatic “correct repair” gesture, arbitrary PCB
editing, or protection from bad bypasses.

**Acceptance criteria:** Functional completion accepts any electrically valid
permitted repair; wrong repairs have real consequences; repaired copper and
solver graph agree.

**Architectural invariants:** Completion remains functional, not hidden-fault
identity matching.

**Addresses:** Former Task 45 and deferred connector/trace fault admission.

**Unlocks:** Task 74.

---

## Task 74 — Fuse and Protection Support Blocks

**Status:** [!] Blocked by Tasks 49–51 and 73

**Purpose:** Add purposeful protection components through the reusable support
architecture.

**Dependencies:** Intent/rating synthesis, composed acceptance, repair actions,
and physical fault locus.

**Bounded goals:** Add bounded fuses, reverse-polarity protection, and one
defensible flyback/transient protection path with real roles and failure
states.

**Explicit non-goals:** No high-current abuse scenario until Task 75, broad
power-electronics library, or arbitrary RNG failure.

**Acceptance criteria:** Protection behavior and failure come from CircuitJS
plus defensible rating/stress rules; bypass remains possible and visible;
support does not create unresolved complaint ambiguity.

**Architectural invariants:** Safety metadata never replaces electrical effect.

**Addresses:** Former Task 46.

**Unlocks:** Task 75.

---

## Task 75 — Bench Power Supply and Current Limit

**Status:** [!] Blocked by Tasks 67, 74, and existing stress/damage v1

**Purpose:** Make high-current troubleshooting and prevention of secondary
damage electrically meaningful.

**Dependencies:** Independent source semantics, protection blocks, ratings, and
stress observations.

**Bounded goals:** Add permitted adjustable voltage, current readout/limit, and
real source behavior whose limiting changes the solved circuit and damage
exposure.

**Explicit non-goals:** No idealized fake current display, universal laboratory
supply for every scenario, or unrestricted dangerous mains model.

**Acceptance criteria:** A sensible current limit can prevent a proven overload;
a high limit allows the solver-derived consequence; active measurements,
source isolation, and reset remain correct.

**Architectural invariants:** Current limit belongs to source behavior, not a
post-solve clamp in UI.

**Addresses:** Former Task 50 and high-current admission gate.

**Unlocks:** Task 76 and allows bounded rail-short/protection/damage candidates.

---

## Task 76 — Oscilloscope Foundation

**Status:** [!] Blocked by Tasks 39, 51, 57, and 75 where source behavior matters

**Purpose:** Add the first waveform instrument before normal oscillator or
waveform-dependent challenges.

**Dependencies:** Stable probe endpoints, player input/retest, composed
temporal behavior, and readable PCB access.

**Bounded goals:** One channel, volts/div, time/div, stable repetitive trigger,
PCB-accessible probe, and waveform rendering from CircuitJS simulation.

**Explicit non-goals:** No pre-scripted waveform, multi-channel logic analyzer,
advanced decoding, or oscillator challenge before instrument acceptance.

**Acceptance criteria:** A real known temporal fixture produces the expected
CircuitJS waveform; probe/power/mutation lifecycle and cleanup are correct;
normal-player operation is visible and accessible.

**Architectural invariants:** Scope is an instrument consumer of solver time,
not a second signal generator.

**Addresses:** Former Task 47.

**Unlocks:** Task 77.

---

## Task 77 — Triggered Timer Block Proof

**Status:** [!] Blocked by Tasks 39, 45, 51, and 76

**Purpose:** Split the former Timer/Oscillator task and prove a player-triggered
temporal block first.

**Dependencies:** Player trigger, typed signal/timing contracts, composed
acceptance, and scope/DC temporal observation.

**Bounded goals:** Add one reusable timer/trigger block with player-operated
trigger, solver-backed timing, bounded faults, and retest using the appropriate
instrument.

**Explicit non-goals:** No free-running oscillator library, hidden trigger,
microcontroller/state-machine framework, or signal not observable by the
player.

**Acceptance criteria:** Timing faults are distinguishable and repairable with
available controls/instruments, and complaint timing is derived from the solved
behavior.

**Architectural invariants:** Temporal validation never substitutes a scripted
UI animation.

**Addresses:** First half of former Task 42.

**Unlocks:** Task 78.

---

## Task 78 — Frequency Measurement and Oscillator Block Proof

**Status:** [!] Blocked by Tasks 76–77

**Purpose:** Add free-running frequency reasoning only after waveform access
exists.

**Dependencies:** Scope, typed temporal ports, and timer proof.

**Bounded goals:** Add a frequency measurement path and one reusable oscillator
block with real CircuitJS waveform, bounded compatible faults, diagnostic
signature, repair, and customer retest.

**Explicit non-goals:** No arbitrary digital protocol, logic analyzer, or fake
frequency read from configured component values.

**Acceptance criteria:** Frequency and waveform agree with the solver across
healthy/faulted/repaired states; the normal player can observe and retest the
required signal.

**Architectural invariants:** Instrument results are measured, not metadata.

**Addresses:** Former Tasks 42 and 48.

**Unlocks:** Task 79.

---

## Task 79 — Capacitance Measurement Decision and Proof

**Status:** [~] Conditional after Task 78

**Purpose:** Add capacitance mode only if accepted challenges need it and a
defensible active method exists.

**Dependencies:** Task 78 and an admitted challenge/profile whose diagnostic
evidence establishes the need.

**Entry condition:** A supported profile/family has a solvability requirement
that cannot be met reasonably with voltage/time/isolation, and a CircuitJS
stimulus method is designed.

**Bounded goals:** Implement one finite active measurement transaction with
stored-energy readiness, tolerance, cleanup, and in/out-of-circuit behavior.

**Explicit non-goals:** No direct read of CapacitorElm configured value or
mandatory mode without a challenge need.

**Acceptance criteria:** The method derives the result from the solve, respects
power/discharge safety, cleans every temporary element, and improves a proven
diagnostic plan.

**Architectural invariants:** Active meter stimulus remains temporary.

**Addresses:** Former Task 49.

**Unlocks:** Task 80 whether implemented or explicitly deferred by evidence.

---

## Task 80 — HARD Profile Calibration and Admission

**Status:** [!] Blocked by Tasks 58, 66–78 and the Task 79 decision

**Purpose:** Enable Hard only after multi-block, multi-rail, repair, and dynamic
capabilities provide legitimate difficulty knobs.

**Dependencies:** Updated diagnostic/layout corpus and every feature requested
by the proposed Hard profile.

**Bounded goals:** Calibrate Hard around composed subsystems, multiple rails,
purposeful auxiliaries, controlled inputs, required isolation/repair, and
bounded temporal/instrument demands.

**Explicit non-goals:** No Psychotic label, unavailable instrument, unreadable
routing, forced secondary damage, or multi-fault requirement.

**Acceptance criteria:** Requested Hard constraints and computed assessment
agree across the versioned corpus; every Hard route remains fair, repairable,
and player-retestable.

**Architectural invariants:** Hard means deeper honest reasoning, not less
physical truth.

**Addresses:** Third requested difficulty mode.

**Unlocks:** Task 80(A) only.

---

## Task 80(A) — First Desktop Beta Readiness Gate

**Status:** [!] Blocked by Task 80 and an accepted Task 63 alpha gate

**Purpose:** Decide whether the supported desktop product has progressed from
an honest alpha into a stable, externally testable beta surface.

**Dependencies:** Accepted Task 63 alpha evidence, admitted EASY/MEDIUM/HARD
profiles, and completed or explicitly skipped evidence-conditional decisions
through Task 80.

**Bounded goals:** Run and record one beta admission matrix across the
advertised profiles, reusable blocks, repair actions, instruments, product
shell, rejection paths, and replay/version contracts; either declare the exact
supported beta surface or reject readiness with structured blockers.

**Explicit non-goals:** Beta does not require PSYCHOTIC, intermittent faults,
expanded damage/thermal/history, scoring, save/share, economy, mobile,
multilayer, appliance-scale devices, or a public hosted release.

**Acceptance criteria:**

- EASY, MEDIUM, and HARD each pass their versioned diagnostic, physical,
  replay, repair, and player-retest corpora at documented support bounds.
- Every advertised block, repair action, instrument, Shop/catalog operation,
  Resource, and Setting uses its authoritative contract and fails safely.
- Menu-to-results flow, seed/replay, generation rejection, privacy, keyboard/
  focus basics, and normal-player browser workflows pass a beta checklist.
- Supported desktop/browser scope, known limitations, generator/layout
  versions, failure telemetry, and bug-report identity are documented.

**Architectural invariants:** A release label cannot waive CircuitJS truth,
diagnostic fairness, stable identity, deterministic replay, physical
readability, or honest shell ownership.

**Addresses:** The missing alpha-to-beta product-stage boundary and prevents
advanced difficulty/persistence work from silently defining release readiness.

**Unlocks:** Phase 9. Stop and report the gate result; do not automatically
begin Task 81.

---

# Phase 9 — Post-Beta Intermittency, Consequences, and Progression

## Task 81 — Intermittent Fault Engine

**Status:** [!] Blocked by Tasks 41, 51, 76, and 80(A)

**Purpose:** Add reproducible time/state-dependent failure only when it can be
observed and retested.

**Dependencies:** Diagnostic admission, composed fault contract, temporal
instrumentation, Hard profile, and the accepted desktop-beta gate.

**Bounded goals:** Support a small set of real graph-state events such as
intermittent open, startup dropout, or bounded reproducible contact behavior.

**Explicit non-goals:** No fake flashing reading, unseeded randomness, broad
temperature model, or automatic Psychotic classification.

**Acceptance criteria:** Events are deterministic from named streams, mutate
real electrical state, have a legal capture/retest plan, and cleanly separate
event schedule from player-facing complaint.

**Architectural invariants:** Cause and timing are reproducible and solver-backed.

**Addresses:** Former Task 51.

**Unlocks:** Task 82.

---

## Task 82 — Expanded Stress and Secondary Damage Models

**Status:** [!] Blocked by Tasks 74–75 and 81

**Purpose:** Extend consequences beyond resistor power using defensible stress.

**Dependencies:** Ratings, current-limited source, protection, and event/time
contracts.

**Bounded goals:** Add selected diode/electrolytic/transistor/MOSFET/relay/fuse
limits and plausible failure transitions driven by solved stress and duration.

**Explicit non-goals:** No arbitrary RNG damage, perfect semiconductor/thermal
physics, or hidden scripted cascades.

**Acceptance criteria:** Safe operation survives, excess stress accumulates
deterministically, failure changes the real graph/element behavior, and the
physical owner/history is preserved.

**Architectural invariants:** Randomness may choose among plausible outcomes,
never the cause of failure.

**Addresses:** Former Task 52.

**Unlocks:** Task 83.

---

## Task 83 — Thermal Approximation

**Status:** [!] Blocked by Task 82

**Purpose:** Add bounded heating/cooling only where it improves diagnosis or
failure timing.

**Dependencies:** Solved dissipation and expanded damage state.

**Bounded goals:** Approximate component thermal state, cooling, and its
contribution to failure timing; expose only justified optional clues.

**Explicit non-goals:** No field solver, photoreal thermal camera, or arbitrary
hot-component hint.

**Acceptance criteria:** Temperature follows solved power and time, cooling is
bounded/testable, and any player-visible clue is accessibility-safe and does
not reveal hidden fault identity.

**Architectural invariants:** Thermal state is derived, not cosmetic RNG.

**Addresses:** Former Task 53.

**Unlocks:** Task 84.

---

## Task 84 — Persistent Customer Return and Service History

**Status:** [!] Blocked by Tasks 39, 81–83

**Purpose:** Add multi-visit consequences on top of the immediate retest
contract established in Task 39.

**Dependencies:** Job-closure semantics, intermittent/time behavior, damage,
and thermal state.

**Bounded goals:** Preserve prior repair/part/damage history across a simulated
service interval and generate a new solver-compatible complaint for the actual
new failure.

**Explicit non-goals:** No hard-coded “come back later” script, economy, or
assumption that every completed job fails again.

**Acceptance criteria:** A defensible wrong repair can pass an immediate test,
fail later from modeled stress, return with preserved board history, and be
diagnosed/repaired through the normal pipeline.

**Architectural invariants:** Immediate functional completion and historical
job record remain distinct.

**Addresses:** Split later half of former Task 54.

**Unlocks:** Task 85.

---

## Task 85 — PSYCHOTIC Profile Calibration and Admission

**Status:** [!] Blocked by Tasks 58, 80(A), and 81–84

**Purpose:** Enable the fourth mode as the maximum legitimate complexity the
implemented systems can prove.

**Dependencies:** Accepted Task 80(A) beta evidence plus updated solvability,
temporal, damage, service-history, layout, and instrument corpora through Task
84. Scoring, persistence, and sharing are not difficulty prerequisites.

**Bounded goals:** Calibrate multi-stage diagnostic depth, composed domains,
parallel ambiguity, isolation/repair actions, temporal/intermittent evidence,
stress consequences, purposeful auxiliaries, and maximum readable routing.

**Explicit non-goals:** No simulator cheating, unavailable controls/instruments,
unreadable copper, arbitrary values, mandatory multiple faults, or withheld
real-world physical information.

**Acceptance criteria:** Every admitted Psychotic route has a bounded solver-
verified legal plan and repair/retest path, requested/computed profile agreement,
and readable/probeable layout. Psychotic may remain single-fault.

**Architectural invariants:** Maximum difficulty never lowers the universal
fairness floor.

**Addresses:** Fourth requested difficulty mode.

**Unlocks:** Task 86 decision gate.

---

## Task 86 — Multiple Simultaneous Fault Decision and Bounded Proof

**Status:** [~] Conditional after Task 85

**Purpose:** Decide with evidence whether multiple generated faults add useful
reasoning rather than chaos.

**Dependencies:** Task 85 plus the current diagnostic, physical-owner, repair,
and retest contracts.

**Entry condition:** Single-fault Psychotic content is mature; the diagnostic
model can prove a multi-fault plan; fault effects do not mask repair or create
untracked secondary state.

**Bounded goals:** If justified, add one explicitly authored compatible two-
fault proof with deterministic selection, distinguishability, staged repair,
and retest.

**Explicit non-goals:** No default random multi-fault probability, arbitrary
fault count, or claim that Psychotic requires multiple faults.

**Acceptance criteria:** The proof is diagnostically fair and measurably
different from a single-fault challenge; otherwise reject the feature and keep
single-fault generation as the normal rule.

**Architectural invariants:** Every fault retains its own physical owner and
solver effect.

**Addresses:** Former Task 58.

**Unlocks:** Task 87 whether implemented or explicitly rejected by evidence.

---

## Task 87 — Scoring and Troubleshooting History

**Status:** [!] Blocked by Tasks 41, 62, 84, and the Task 86 decision

**Purpose:** Reward efficient reasoning without changing electrical behavior.

**Dependencies:** Diagnostic-action evidence, explicit sessions/results,
service history, and a recorded multiple-fault start/skip decision.

**Bounded goals:** Record measurements, unnecessary removals/replacements,
wrong repairs, caused damage, time, verification, and diagnostic efficiency;
derive transparent scoring from that history.

**Explicit non-goals:** No blocked legal actions, physics changes, hidden
penalty that alters the board, or global economy.

**Acceptance criteria:** Identical electrical play produces stable history/
score; scoring can be disabled without changing challenge state; results
explain factors without revealing unreached hidden answers.

**Architectural invariants:** Scoring observes; it never simulates.

**Addresses:** Former Task 62.

**Unlocks:** Task 88.

---

## Task 88 — Save and Resume Mutable Challenge State

**Status:** [!] Blocked by Tasks 46, 62, 82, 84, and 87

**Purpose:** Persist a real workbench session without serializing transient
solver identity.

**Dependencies:** Versioned challenge descriptor, session state, board
mutations, inventory, damage, and history.

**Bounded goals:** Persist challenge/version/seed/profile, current physical
parts/inventory, lifted/removed/replaced state, jumpers/cuts/repairs, damage,
power/input state, and permitted history; rebuild CircuitJS through authoritative
adapters.

**Explicit non-goals:** No serialized solver node numbers/matrix, cloud account,
or economy.

**Acceptance criteria:** Round-trip resume reproduces stable physical/electrical
state and deterministic behavior; incompatible versions fail safely.

**Architectural invariants:** Durable identity is semantic and physical, never
analyzed node identity.

**Addresses:** Former Task 67.

**Unlocks:** Task 89.

---

## Task 89 — Shareable Versioned Challenges

**Status:** [!] Blocked by Tasks 46, 62, and 88

**Purpose:** Extend local replay into a compact supported sharing contract.

**Dependencies:** Versioned descriptor, menu replay, and persistence/version
policy.

**Bounded goals:** Encode a compact challenge descriptor for pristine replay and
a separate explicit saved-state artifact when mutable progress is shared.

**Explicit non-goals:** No hidden fault answer in visible share text, server
account, or promise that every old generator version is supported forever.

**Acceptance criteria:** A clean environment reproduces the intended challenge
from the descriptor; version mismatch is explicit; normal share UI exposes seed/
version/profile but not answer metadata.

**Architectural invariants:** Sharing reuses the one generation/persistence
contract.

**Addresses:** Former Task 68.

**Unlocks:** Post-beta productization backlog.

---

# Release-Stage Strategy

Release labels are evidence gates over supported behavior, not substitutes for
milestone completion.

| Stage | Roadmap decision | Required supported surface |
| --- | --- | --- |
| Developer build | Current and ongoing milestone state; not a release gate. | Deterministic developer routes may expose debug data, but normal-player claims remain limited to accepted features. |
| Desktop alpha | Task 63. | Solver-backed/retestable routes, EASY/MEDIUM, replay identity, honest menu/results flow, Resources/Settings, truthful Shop treatment, and the G1–G8 corpus. |
| Desktop beta | Task 80(A). | Accepted alpha plus admitted HARD, the bounded block/repair/instrument surface through Task 80, authoritative advertised shell behavior, documented support bounds, and beta reliability/browser evidence. |
| Mature / 1.0 | Intentionally not numbered until beta telemetry and post-beta product decisions exist. | Before scheduling, define the exact advertised four-profile surface (including Task 85 PSYCHOTIC), release/accessibility and responsive scope, persistence/share policy, public packaging/attribution, support bounds, and honest Shop/economy decision. Scoring, multi-fault, economy, cloud accounts, or mobile are required only if advertised. |

Tasks 81–89 are post-beta capability/progression work; their position does not
retroactively make them beta prerequisites. After beta evidence exists, a
future roadmap update may create one bounded mature-release gate, but may not
claim 1.0 merely because the numbered queue is exhausted.

---

# Phase 10 — Post-Beta Productization and Conditional Backlog

These items remain deliberately later. Before starting one, create a bounded
numbered milestone only when its listed prerequisites and product need are
satisfied.

| Capability | Decision and prerequisites |
| --- | --- |
| Shop economy/purchasing | Deferred. Task 70 provides a real catalog/source without money. Economy requires Task 87 scoring/progression, Task 88 persistent inventory, a documented gameplay reason, and a separate economy decision gate. Do not add prices merely because the tab says SHOP. |
| Touch and responsive workbench | Retain after the desktop loop and persistence are stable. Implement explicit red/black touch selection, pan/zoom, safe gestures, larger targets, and responsive panels together; never hide electrical functionality on mobile. |
| Full accessibility pass | Basic settings/accessibility land before alpha in Task 61. A later full pass covers keyboard board interaction where practical, screen-reader privacy, non-color-only dynamic state, zoom/contrast, and continuity alternatives across desktop/mobile. |
| Public deployment and project identity | Retain after the chosen release gate: TroubleshootJS README/controls, upstream attribution/GPL, hosted build, browser support, contribution guidance, and reproducible release verification. |
| PNP/PMOS high-side variants | Defer until a composed device genuinely requires high-side control. Implement as reusable driver variants with explicit terminal/post/domain mapping, not renderer aliases or another family clone. |
| Power-supply/rectifier blocks | Defer until typed high-energy/isolation domains, protection, bench source limits, ratings, and suitable instruments exist. Keep abstractions bounded and solver-backed. |
| Motor/fan/pump device | Use as a composed-device proof from power, control, driver, protection, and load blocks after Tasks 66–75. Do not create a monolithic motor family. |
| Logic/state-machine blocks | Defer until typed digital timing, player-operable inputs, scope/frequency support, and stateful diagnostic contracts exist. |
| Appliance-scale devices | Defer until a multi-block fan/pump proof passes composition, solvability, physical scalability, and product-profile gates. |
| Two-sided routing/vias | Only the Task 56(A) evidence gate may bring this forward. More than two layers, arbitrary multilayer autorouting, planes, or hidden copper remain out of scope. |
| SMD-dominant boards and richer outlines | Defer until through-hole composed-board telemetry proves a product need and probe/readability/interaction contracts are ready. |
| Logic analyzer, multi-channel scope, thermal camera | Add only when an admitted challenge requires them and their observations remain solver/derived-state backed. |
| Advanced visual package polish | Radial-can, ceramic-disc, TO-92, mounting, solder, and damage visuals follow envelope truth and measured readability. They never outrank fairness/composition prerequisites. |

---

# Difficulty Strategy

The shared architecture is:

    GenerationRequest
      (root seed, generator version, device intent, DifficultyProfile ID/version)
        → DifficultyProfile
        → typed GenerationConstraints
        → generation and solver/physical admission
        → ChallengeComplexityMetrics
        → DifficultyAssessment
        → accept or reject

No difficulty has its own generator.

| Profile | First eligible milestone | Legitimate initial meaning |
| --- | --- | --- |
| EASY | Task 59 | Guided/leaf/static content, few plausible owners, direct legal plan, optional help. |
| MEDIUM | Task 59 | Multiple plausible owners, parallel path or isolation/input reasoning, bounded two-block/domain complexity. |
| HARD | Task 80 | Composed subsystems, multiple rails, purposeful support, required isolation/repair, controlled temporal/instrument work. |
| PSYCHOTIC | Task 85 | Maximum currently implemented and corpus-proven legitimate complexity, including multi-stage temporal/stress consequences when supported. |

Permanent constraints at every level:

- Required controls, instruments, physical markings, probe targets, and
  accessibility remain available.
- Complaint ambiguity may increase only while solver-separable.
- Physical complexity is measured by regions/connectors, fanout, route
  length/bends/detour, near-clearance occupancy, visible links/layers, rendered
  scale, and probe separation—not raw component count alone.
- Continuous LED brightness or color cannot be the sole diagnostic discriminator;
  an accessible electrical observation and accessibility-safe cue must exist.
- Requested profile constrains generation; computed evidence must confirm it.
  A mismatch is rejected, never relabeled after the fact.

---

# Product-Shell Decisions

- **TOOLS:** Already represents the real workbench/instrument surface. Keep it
  as view orchestration; do not implement a second instrument system in the
  shell.
- **RESOURCES:** Becomes real in Task 60, before alpha, using generic technician
  references without hidden challenge data.
- **SETTINGS:** Becomes real in Task 61, before alpha, for presentation and
  accessibility only.
- **SHOP:** The current mock cart is not authoritative. Task 61 hides or makes
  it unmistakably read-only for alpha. Task 70 later connects the surface to
  actual catalogs and runtime acquisition without economy. Money/progression
  remains separately gated.
- **MAIN MENU:** Task 62, after versioned replay, diagnostic admission, two
  honest difficulty profiles, and one composed proof. It precedes alpha and
  owns navigation only.
- **RESULTS:** A session state after Finish Job, not another permanent tab.
- **Future tabs:** None are justified now.

---

# Former Future-Task Migration Map

All former entries below were unstarted.

| Former task | Revised disposition |
| --- | --- |
| 39 Relay Driver | Transformed/moved to Task 66 reusable relay block and composed proof. |
| 40 Regulator / Multi-Rail | Split across Tasks 67–68; high-current faults wait for Task 75. |
| 41 Comparator / Sensor | Transformed/moved to Task 69 with player-adjustable stimulus. |
| 42 Timer / Oscillator | Split into Tasks 77–78 after scope; no hidden-only temporal challenge. |
| 43 Jumper Wires | Retained as Task 71; distinct from generated factory links. |
| 44 Trace Cutting | Retained as Task 72 after stable copper identity. |
| 45 Trace Repair | Retained as Task 73. |
| 46 Fuse / Protection | Transformed into reusable support blocks at Task 74. |
| 47 Oscilloscope | Retained/moved to Task 76 before oscillator admission. |
| 48 Frequency Measurement | Retained/combined with oscillator proof at Task 78. |
| 49 Capacitance Measurement | Retained as evidence-conditional Task 79. |
| 50 Bench Power / Current Limit | Retained/moved to Task 75 before normal high-current faults/damage. |
| 51 Intermittent Fault Engine | Retained as Task 81 after temporal observation and Hard. |
| 52 Expanded Damage | Retained as Task 82 after current limiting/protection. |
| 53 Thermal | Retained as Task 83 after damage. |
| 54 Customer Return | Split: immediate retest/job semantics in Task 39; persistent return history in Task 84. |
| 55 Auxiliary Circuits | Transformed/moved to Task 50 purpose-driven support blocks. |
| 56 Functional Composition | Superseded/split across Tasks 44–48. |
| 57 Multi-Subsystem Fault Selection | Transformed into Task 51 composed acceptance. |
| 58 Multiple Faults | Deferred to conditional Task 86; not required for Psychotic. |
| 59 Larger Placement / Routing | Superseded/split across Tasks 43 and 52–57, with conditional links/layers. |
| 60 Difficulty Model | Split across Tasks 58–59, 80, and 85. |
| 61 Challenge Generator | Absorbed into versioned request, assembler, and staged acceptance at Tasks 46–51; no new monolith. |
| 62 Scoring / History | Retained as Task 87. |
| 63 Guided Beginner | Absorbed into EASY assistance and Resources at Tasks 59–60. |
| 64 Touch Input | Retained in the post-beta touch/responsive backlog. |
| 65 Responsive UI | Combined with the post-beta touch/responsive work. |
| 66 Accessibility | Split: basic Settings/accessibility in Task 61; full pass post-beta. |
| 67 Save / Resume | Retained as Task 88. |
| 68 Shareable Challenges | Split: descriptor/replay at Tasks 46/62; sharing at Task 89. |
| 69 Public Deployment | Retained in post-beta productization after release readiness. |

---

# Revised Dependency Map

    COMPLETED TASKS 1–38
            |
            v
    PLAYER INPUT + CUSTOMER RETEST (39)
            |
            v
    PHYSICAL FAULT LOCUS (40)
            |
            v
    DIAGNOSTIC SOLVABILITY + EVIDENCE (41)
            |
            +----> EXISTING-FAMILY DIVERSITY (42)
            |           |
            |           v
            |      PHYSICAL ENVELOPES (43)
            |
            +----> BLOCK IDENTITY / PORTS (44)
                        |
                        v
                 TYPED DOMAINS (45)
                        |
                        v
                 VERSIONED SEEDS / CONSTRAINTS (46)
                        |
                        v
                 ASSEMBLER CONTRACTS (47)
                        |
                        v
                 TWO-BLOCK REAL PROOF (48)
                        |
                        v
                 INTENT VALUES + SUPPORT + ACCEPTANCE (49–51)
                        |
             +----------+-----------+
             |                      |
             v                      v
      LAYOUT CORPUS (52)     DIAGNOSTIC/LAYOUT METRICS
             |                      |
             v                      |
      REGION / CONDITIONAL ROUTING (53–56)
             |                      |
             v                      |
      PHYSICAL SCALABILITY GATE (57)
             +----------+-----------+
                        |
                        v
              DIFFICULTY CONTRACT (58)
                        |
                        v
                 EASY + MEDIUM (59)
                        |
                        v
            RESOURCES / SETTINGS / MENU (60–62)
                        |
                        v
                 DESKTOP ALPHA GATE (63)
                        |
                        v
             STATIC VISUAL / PCB POLISH (64)
                        |
                        v
            CONTINUOUS LED VISUAL STATE (65)
                        |
                        v
        BLOCK / REPAIR / INSTRUMENT EXPANSION (66–79)
                        |
                        v
             HARD + DESKTOP BETA GATE (80 / 80A)
                        |
                        v
       INTERMITTENCY / DAMAGE / HISTORY (81–84)
                        |
                        v
                  PSYCHOTIC (85)
                        |
                        v
             OPTIONAL MULTI-FAULT GATE (86)
                        |
                        v
              SCORE / SAVE / SHARE (87–89)

The sequencing rule remains:

> Do not scale complexity until the smaller version is electrically honest,
> diagnostically fair, physically truthful, and player-retestable.

---

# Roadmap Maintenance Rules

Update this roadmap when a milestone completes or a decision gate resolves.

1. Preserve the completed Task 1–38 ledger and detailed completed history.
2. Mark exactly one immediate next milestone [>] when practical.
3. For Tasks 54–56, 79, and 86, record the evidence-based start/skip decision.
4. Record generator/profile/layout version consequences when deterministic
   behavior changes.
5. Do not claim planned block/domain/difficulty/menu architecture is already
   implemented; current implementation truth remains in docs/ARCHITECTURE.md.
6. Record task evidence, limitations, review, commit, and publication in
   docs/CODEX_TASK_REPORT.md.
7. Stop after the accepted milestone. Never begin the newly unlocked task
   automatically.

---

# Immediate Next Milestone

**Task 39 — Player-Operable Functional Inputs and Customer Retest Contract**

Its dependencies are satisfied by completed Tasks 1–38. Relay Driver is
deferred and transformed into Task 66. Task 39 has been selected only; no
production implementation has started.
