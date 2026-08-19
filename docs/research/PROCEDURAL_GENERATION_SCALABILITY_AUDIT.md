# TroubleshootJS Procedural Generation Scalability Audit

This is a read-only architecture investigation. It does not implement a new
family, change the roadmap, or modify production behavior.

Evidence labels used throughout:

- [Implemented] describes behavior directly supported by the inspected
  checkout.
- [Inference] describes a conclusion drawn from that behavior.
- [Recommendation] describes a proposed future direction, not current
  capability.

The committed audit checkout contains five normal-player generated families:
LED indicator, diode-protected indicator, parallel dual indicator, RC delay,
and NPN low-side switch. The shared main worktree also contained uncommitted
NMOS work. That work was inspected only as in-progress context and is not
counted as a committed or accepted family in this report.

## 1. Executive Summary

### Direct answer

**Yes, with substantial intermediate architecture.**

TroubleshootJS has the right foundation for believable complex boards. CircuitJS
is still the electrical source of truth; stable board, pad, component, and net
identifiers are separated from transient solver nodes; physical parts and
rendering are behind provider boundaries; generated challenges have a generic
lifecycle; and the current families already prove real solver-backed fault,
repair, and temporal behavior.

The current generator is not yet a scalable procedural device generator,
however. It is a collection of bounded, hand-authored family generators. Each
generator currently owns most of the following at once:

- logical board topology and stable IDs;
- CircuitJS elements and bindings;
- values and operating assumptions;
- compatible fault candidates;
- family-specific healthy and faulted validators;
- scenario/complaint candidates;
- physical specifications and replacement catalogs;
- and either a general small-board layout request or a hand-authored PCB
  layout.

That architecture is sound for proving one complete end-to-end family. It will
become expensive and error-prone when the project reaches multi-rail,
multi-subsystem devices. Adding more complete family classes can reach a
coffee-maker-like board only through a large amount of duplicated code and
manual integration, not through reusable composition.

### Most important finding

[Inference] The project has a reusable *runtime and mutation architecture*, but
not yet a reusable *generation intent and composition architecture*. The next
scaling boundary is therefore not a more general random circuit algorithm. It
is a typed functional-block and interface layer that can assemble constrained
subsystems, derive values from intent, validate solvability, and then hand the
result to the existing CircuitJS, fault, physical, and PCB layers.

### What can be preserved

[Implemented] The following are strong foundations and should remain central:

- CircuitJS-backed electrical behavior and measurement;
- stable logical IDs distinct from solver node IDs;
- GeneratedBoardInstance as a family-agnostic board/runtime envelope;
- explicit board modification ownership through BoardModificationController;
- explicit fault bindings and real graph mutations;
- generic challenge lifecycle and behavior-contract adapters;
- physical slot/part identity separate from logical component identity;
- package, footprint, definition, and render provider registries;
- deterministic family generation and deterministic small-board layout;
- a temporal behavior capability rather than hard-coded UI timing;
- one-sided, educational PCB scope.

[Recommendation] Scale these seams by adding intent and composition above them,
rather than replacing them with a general-purpose arbitrary circuit synthesizer.

## 2. Current Generation Pipeline

[Implemented] There are two related entry paths.

In normal Quick Play, QuickPlaySelector chooses a family and a seed.
QuickPlayFamilyRegistry maps the selected family ID to one of the family
generator classes. The selector itself chooses from a small registry; it does
not understand electrical domains, subsystems, component values, or
diagnostic quality.

In fixture and challenge routes, CirSim selects a named family and invokes the
corresponding generator directly. The family generator returns a complete
GeneratedBoardInstance.

The effective current pipeline is:

1. Select a family and seed.
2. In one family-specific generator, build the logical board and stable
   components, pads, and nets.
3. Build the real CircuitJS elements, wires, sources, switches, and ground
   elements needed by the family.
4. Build board-to-solver measurement and detachable-connection bindings.
5. Create physical specifications, runtime slots, parts, inventories, and
   replacement capabilities.
6. Create family-specific behavior validators, fault candidates, and scenario
   candidates.
7. Select or install the family layout. LED, diode, and parallel families use
   SeededPcbLayoutGenerator; RC and NPN use family-specific manual layout
   factories.
8. Construct and structurally validate GeneratedBoardInstance.
9. CirSim installs the instance's CircuitJS elements and board modification
   controller, connects power controls, and requests generated verification.
10. After simulation analysis, GeneratedBoardVerifier and the family behavior
    contract validate the healthy state.
11. GeneratedChallengeController applies the selected fault effect and requests
    another solve.
12. The family fault validator validates the faulted behavior.
13. GeneratedScenarioLibrary selects a compatible complaint scenario.
14. The challenge becomes ready for normal-player interaction.

This differs from the ideal generation order in the project guidance in one
important respect: the PCB layout is currently made inside the family
generator, before the first runtime healthy validation. There is no
top-level semantic generation loop that rejects a failed candidate and tries
another topology, value set, fault, or physical layout. The layout generator
has its own placement/routing attempts, but that is a geometry search, not a
whole-challenge acceptance loop.

[Implemented] The healthy and faulted phases are solver-backed. They are not
fake meter readings. Board modifications also go through real CircuitJS graph
changes and then trigger reanalysis.

[Inference] The current architecture is therefore a complete *execution
pipeline* but only a partial *procedural generation pipeline*. It can execute a
generated family honestly; it cannot yet search a large design space while
guaranteeing useful, solvable challenges.

## 3. What Is Actually Procedural Today?

The word procedural currently covers several different levels of variation.
They should not be conflated.

| Area | Current behavior | Assessment |
| --- | --- | --- |
| Family selection | QuickPlay selects from an explicit registry of five committed families. | Procedural selection over a small curated set. |
| Topology | Each family has one hand-authored topology in its generator. | Not procedural within a family. |
| Component count | Fixed by family. | Not procedurally scalable. |
| Logical IDs and nets | Hand-authored per family. | Deterministic, but not composed. |
| Values | Small seeded tables or seed cases; most component values are constants. | Parameter variation, not design synthesis. |
| Fault choice | Family-specific compatible candidates are selected by seed. | Curated fault variation. |
| Healthy validation | Real CircuitJS and family-specific assertions. | Strong execution validation. |
| Fault validation | Real CircuitJS and family-specific type checks. | Strong for the selected family envelope. |
| Scenarios | Small hard-coded catalogs selected after fault validation. | Curated complaint variation. |
| PCB geometry | General seeded placement/routing for the smaller indicator families; manual factories for RC and NPN. | Procedural geometry is currently narrow. |
| Physical identity | Provider-driven package/spec/part/runtime construction. | Reusable physical infrastructure. |
| Composition | No call-site uses GeneratedChallengeCatalog to assemble complete candidates, and no functional-block assembler is present. | Not implemented. |

The LED, diode, and parallel generators use seeded supply and resistor
configurations. Their topologies and component identities remain fixed. The RC
generator uses a small seed-dependent value/fault envelope and a temporal
behavior object. The NPN generator changes a small supply envelope and selects
among a fixed list of four fault types while retaining a fixed switch topology.

The existing GeneratedChallengeCatalog is a useful-looking selection helper,
but the inspected baseline has no production call site that populates or
selects from it. It does not currently provide a device-level composition
mechanism.

[Inference] Today the meaningful procedural search space is approximately:

- choose a leaf family;
- choose a small parameter case;
- choose one curated fault;
- choose a scenario compatible with the resulting behavior;
- and, for only some families, search a small physical layout space.

That is a good proof envelope. It is not yet a generator that can produce many
structurally different boards from a device intent.

## 4. Current Family Architecture

The committed family set is intentionally small and demonstrates increasing
behavioral variety, but the implementation boundary is still one complete
generator per family.

| Family | Logical topology | Seed variation | Fault envelope | PCB strategy | Behavior boundary |
| --- | --- | --- | --- | --- | --- |
| LED indicator | VIN to resistor to LED to ground | Three supply/resistor cases | Resistor open, incorrect resistor, and connector-related candidates subject to compatibility | Seeded general placement/routing | LED-specific healthy/fault/repair validation |
| Diode-protected indicator | VIN to diode to resistor to LED to ground | Three supply/resistor cases | Diode open, development-only diode short, and connector-related candidates subject to compatibility | Seeded general placement/routing | Diode-family validation plus indicator behavior |
| Parallel dual indicator | Two real resistor/LED branches sharing VIN and ground | Paired resistor cases from small tables | Branch resistor open/incorrect and connector-related candidates | Seeded general placement/routing | KCL and branch-current validation |
| RC delay | R1/C1 transient node with R2, C2 support, source control, and real stored energy | Four normalized seed cases | Capacitor open/short patterns | Fixed RcDelayPcbLayoutFactory | Temporal profile and RC-specific repair behavior |
| NPN low-side switch | Separate load/control supplies, load resistor/LED, base resistor/pulldown, and NPN low-side switch | Small supply envelope | Collector-emitter open/short, base resistor open, load path open | Fixed NpnLowSideSwitchPcbLayoutFactory | NPN-specific on/off and repair validation |

[Implemented] These families use common wrappers such as
GeneratedBoardInstance, GeneratedChallengeController, generated bindings,
physical runtime, and challenge behavior interfaces. They do not use a common
functional-block builder.

[Implemented] Each family still supplies its own generator, family state,
fault compatibility logic, fault validator, repair validator or temporal
profile, scenario mapping, value assumptions, and physical setup.

[Inference] The duplication is manageable for the current five-family proof.
At roughly ten families, it will be a maintenance concern; at a few dozen
families or multi-block device variants, it becomes a generation correctness
risk because the same design decisions will be reimplemented in many classes.

The in-progress NMOS work in the shared main worktree follows the same general
pattern as NPN: a dedicated generator, fixed low-side topology, a small value
envelope, dedicated physical support, and family-specific verification. It is
useful evidence about the next leaf-family milestone, but it does not yet
solve composition and is intentionally excluded from the committed family
count above.

## 5. Scalability Strengths

### Electrical truth and stable identity

[Implemented] CircuitJS remains the simulation backbone. Board measurements
resolve through real CircuitJS endpoints, and power-off instrument behavior is
implemented through source isolation rather than a UI-only reading table.
Stable board/pad/net IDs are kept separate from transient solver node IDs.
This is exactly the separation needed when a procedural generator later
rebuilds or composes a circuit.

[Implemented] BoardModificationController owns physical graph mutations such as
component removal, lead disconnection, replacement, jumpers, and trace
changes. That gives future block composition one mutation boundary instead of
requiring every UI path to understand every family.

### Runtime and challenge seams

[Implemented] GeneratedBoardInstance already packages the logical board,
simulation elements, bindings, behavior contract, PCB layout, physical
specifications/runtime, selected fault binding, challenge definition, family
state, and optional temporal behavior. It is a good runtime envelope for a
composed result.

[Implemented] GeneratedChallengeController has a generic healthy-to-faulted-to-
ready lifecycle. GeneratedChallengeBehaviorAdapter delegates generic lifecycle
events to family-specific validators. This lets a future composed device
provide a composed behavior contract without changing the player-facing
challenge state machine.

[Implemented] Fault effects are explicit and can mutate real CircuitJS
elements. A selected fault is not merely a label; it has a binding and applied
state.

### Physical and rendering reuse

[Implemented] PhysicalBoardRuntime separates stable board slots from installed
physical part identities and replacement inventories. Physical specifications,
packages, footprint providers, definition providers, and render providers are
registered separately from electrical behavior.

[Inference] This is a stronger reuse boundary than the current generator
classes. A block system can reuse these registries if it emits stable slot
descriptors and does not make rendering responsible for connectivity.

### Seeded geometry and temporal seams

[Implemented] SeededPcbLayoutGenerator provides deterministic topology-aware
placement and Manhattan/A* routing for the smaller families, with geometry
validation and multiple placement attempts. RC has a real temporal behavior
seam, and the runtime supports bounded solver advancement for temporal
profiles.

[Inference] The project has already avoided two common traps: treating a PCB
image as the electrical model, and treating temporal behavior as a hard-coded
UI animation. Those decisions make later complexity possible.

## 6. Scalability Limits

### Limits that are acceptable now

[Implemented] The following are deliberate and appropriate for the present
proof stage:

- one curated topology per leaf family;
- small deterministic value envelopes;
- one-sided PCB layouts;
- family-specific validators;
- one selected compatible fault;
- no professional DRC, multilayer routing, Gerber output, or unrestricted
  circuit synthesis;
- a small Quick Play registry.

These limits keep the current families understandable and testable.

### Limits that appear before medium-complexity boards

[Inference] The following need architectural work before reliably composing
roughly 10–30 board components across multiple functions:

- no functional-block contract or assembler;
- no typed power/signal domain metadata on ports or nets;
- no design-intent model from which values can be derived;
- no common component-role metadata for optional support parts;
- no general generated physical-specification registration API;
- no generation-versioned root seed and named sub-seed policy;
- no common semantic preflight before CircuitJS construction;
- no generic diagnostic signature or distinguishability check.

### Limits that appear before large or multi-rail boards

[Inference] The following become material around 25–60 components or multiple
interacting subsystems:

- family validators cannot express subsystem interaction without more bespoke
  code;
- the current fault selector does not compare candidates for solvability or
  diagnostic quality;
- scenarios are selected from hard-coded family behavior rather than a
  device-level symptom model;
- ExternalBoardPowerInput and BoardPowerController do not describe independent
  rails, isolation, source limits, or interface compatibility;
- the general PCB placer has no subsystem regions, port channels, or density
  strategy, while the RC and NPN layouts are manually authored;
- one-sided routing and repeated rejection will become increasingly sensitive
  to trace density and board dimensions;
- there is no bounded top-level rejection pipeline with reason telemetry.

### Long-term architectural blocker

[Inference] The largest risk is allowing each new device to become another
monolithic generator. That would preserve current correctness locally while
making global correctness, reuse, and challenge solvability increasingly
unreviewable.

## 7. Functional Subsystem Composition

[Implemented] A GeneratedBoardInstance represents a complete generated board,
not a reusable subsystem. BoardComponent and BoardNet represent board-local
entities. GeneratedComponentBindings, GeneratedExternalPowerBindings, and
GeneratedComponentConnectionBindings bind that completed board to CircuitJS
and detachable leads.

[Implemented] GeneratedBoardFamilyState and
GeneratedChallengeBehaviorAdapter are extension points for family behavior
after a board has been built. They do not describe how two independent
functional modules expose ports, merge nets, share rails, or contribute
faults.

[Implemented] GeneratedChallengeDefinition describes a complete challenge:
family ID, topology ID, selected fault, behavior contract, and scenario
catalog. It is not a functional block descriptor.

[Inference] A composed generator would currently have to duplicate or
manually coordinate:

- local-to-global component and net ID allocation;
- CircuitJS element ownership and connection binding ownership;
- external power input merging;
- shared ground and rail policy;
- value dependencies between modules;
- physical slot and package registration;
- fault target ownership;
- behavior aggregation;
- scenario projection;
- and layout anchors/port locations.

That is the boundary where a family-specific class will grow nonlinearly.

[Recommendation] Treat a subsystem as a constrained module with explicit
interfaces, not as an arbitrary subgraph and not as a miniature complete
GeneratedBoardInstance. A composed board should have one global board/runtime
envelope, while each block contributes namespaced logical entities, port
contracts, solver elements, physical slot descriptors, health assertions, and
fault candidates through a controlled assembly API.

## 8. Proposed Functional Block Contract

[Recommendation] A future functional block contract should be split into
logical, behavioral, fault, and physical contributions. The following is a
bounded conceptual contract, not an implementation request in this audit.

### Block identity and parameters

- stable block ID, family/type ID, and block schema version;
- deterministic parameter record;
- declared required and optional support roles;
- local ID namespace for components, pads, nets, solver elements, and
  measurement endpoints;
- declared dependencies on other blocks or adapters.

### Ports and interfaces

Each port should declare more than a string net name:

- port ID and direction;
- electrical domain or domain capability;
- nominal and allowed voltage/current range;
- source, sink, high-impedance, bidirectional, or passive behavior;
- reference/return requirement;
- signal kind and semantics;
- whether the port must be externally accessible as a pad/test point;
- whether the port may be merged with another port;
- required adapter types when direct connection is not valid.

### Logical and solver contribution

The block should be able to emit:

- board components, pads, and local nets;
- CircuitJS elements and stable endpoint bindings;
- detachable component connections where applicable;
- external interfaces and controlled power inputs;
- healthy-state assertions or profiles;
- an owned list of role-bearing components.

The assembly layer, not the block, should allocate global IDs and decide
whether two ports are actually connected.

### Behavior and repair contribution

A block should declare:

- healthy operating states and inputs;
- observable outputs and expected ranges;
- behavior transitions or temporal profiles where needed;
- repair completion conditions for block-owned behavior;
- a way to contribute assertions to a composed behavior contract.

The block should not decide the final customer complaint. That belongs to a
device-level scenario projection over the composed behavior.

### Fault contribution

A block should emit candidate fault descriptors with:

- target role and stable local component identity;
- compatibility preconditions;
- effect factory;
- expected state/output impact;
- allowable repair actions;
- stress or secondary-failure implications;
- accessible diagnostic endpoints;
- and a difficulty/ambiguity classification.

The global fault engine should own selection, application, validation, and
interaction between block candidates.

### Physical contribution

Physical contribution should declare package/spec/slot requirements and
preferred placement roles, but should not draw traces or determine electrical
behavior. Existing PhysicalBoardRuntime and provider registries are suitable
consumers of this information.

[Recommendation] Keep this contract constrained. It should support known
functional families and adapters, not expose an unrestricted “generate any
netlist” escape hatch.

## 9. Power / Signal Domain Composition

[Implemented] ExternalBoardPowerInput currently identifies positive and return
pads/nets. BoardNet contains IDs and pad membership, and board power controls
can connect or disconnect the generated inputs. These classes do not currently
carry a full domain contract.

[Implemented] The current power controller does not model independent rail
voltage ranges, source impedance/current limits, galvanic isolation, startup
ordering, or current-limited behavior. BoardSimulationBindings maps stable pads
to CircuitJS endpoints but does not identify whether an endpoint is a power
rail, analog signal, digital input, open-drain output, or passive node.

[Inference] Directly connecting module ports by matching net names will become
unsafe as soon as the generator includes regulators, sensors, logic thresholds,
relay coils, isolated sections, or multiple external supplies.

[Recommendation] Add a separate typed electrical-domain model rather than
overloading BoardNet with every design rule. A domain/port contract should
cover:

- nominal voltage and valid range;
- polarity and reference node;
- source/sink/current capacity;
- source impedance or drive strength when relevant;
- AC/DC category and frequency envelope when relevant;
- galvanic-isolation boundary;
- startup and enable requirements;
- absolute maximum limits for connected parts;
- whether the domain is externally powered, internally generated, or passive.

Signal contracts should additionally cover:

- analog, digital, PWM, clock, pulse, sensor, control, or power classification;
- input/output/bidirectional direction;
- logic thresholds or analog range;
- pull-up/pull-down/open-drain behavior;
- expected loading and drive mode;
- polarity and active level;
- timing/frequency requirements when behavior depends on them.

Adapters should be explicit functional blocks. Examples include a regulator,
level shifter, resistor divider, isolation barrier, optocoupler, relay
contact, current-sense element, or sensor model. A domain compatibility
preflight should reject an invalid connection before constructing a difficult
CircuitJS candidate.

CircuitJS should still determine the actual result after this preflight. The
domain model is a design and validation contract, not a replacement solver.

For mains-like appliance exercises, the first abstraction can model an
isolated or non-isolated source and safety boundary without attempting
complete power-electronics physics. The boundary must be explicit so the
generator does not silently treat a mains rail as another 5 V net.

## 10. Component Value Generation

[Implemented] Current generators use small tables or seed cases. LED, diode,
and parallel families select from a few supply/resistor configurations. RC
uses a four-case envelope. NPN uses mostly fixed component values and a small
supply envelope. Replacement catalogs provide E12-like or family-specific
choices, but they are repair inventories rather than a general design solver.

[Implemented] Some validators calculate expected current or apply thresholds.
Those calculations verify a chosen design; they do not express a reusable
design intent from which values are synthesized.

[Inference] Scaling values by choosing a random resistor, capacitor, or
threshold independently will quickly generate technically legal but
uninteresting or nonfunctional boards. Values must be derived from the role
and interface constraints of a block.

[Recommendation] Use bounded design equations followed by standard-series
candidate selection:

1. State a design intent and constraints.
2. Calculate a continuous target range.
3. Choose from an allowed value series and available package/rating set.
4. Check operating margin, power, voltage, timing, and tolerance assumptions.
5. Build the real CircuitJS design.
6. Validate healthy states and reject candidates that do not meet the intent.

Examples include:

- LED current and resistor power from supply range, LED drop, and target
  current;
- base/gate drive and pull-down values from the load and control interface;
- RC timing from a target delay and acceptable threshold window;
- regulator feedback values from rail target and input range;
- decoupling from block current/transient role and voltage rating;
- flyback or snubber components from an inductive load contract;
- heater/fan/motor path ratings from current and switching limits.

Value metadata should include nominal value, tolerance or valid range,
voltage/current/power rating, package, availability class, and role. The
generator should use a finite candidate set per intent, so rejection remains
bounded and reproducible.

The current family formulas and replacement catalogs are useful leaf-level
building blocks. They should be extracted behind a design-intent API rather
than discarded.

## 11. Optional Support Components and Red Herrings

[Implemented] Current families contain support elements, but they are mostly
fixed by the family topology. Examples include source switches, ground
elements, NPN base pulldown, RC support capacitor, and branch components. The
generator does not yet choose from a reusable catalog of purpose-driven
support blocks.

[Recommendation] Optional parts should be role-bearing instances with
constraints. Useful roles include:

- input reverse-polarity protection;
- rail decoupling;
- status or power indicator;
- pull-up/pull-down;
- current limiting or sensing;
- flyback diode;
- snubber;
- connector/test point;
- filter;
- enable/PGOOD indicator;
- secondary but healthy output.

Each optional role should declare where it can connect, what domain it
requires, what it may load, what physical access it needs, and whether it is
relevant to the complaint.

Red herrings should be healthy and electrically plausible. They should have a
declared purpose and be observably unrelated or only indirectly related to the
reported symptom. A status LED across a healthy rail, a protected unused
connector, or a secondary sensor filter is a better red herring than an
orphan resistor with no role.

[Inference] The distinction matters for troubleshooting realism. A random
orphan part increases visual noise but teaches no judgment. A healthy support
network that creates a real parallel measurement path can make isolation and
interpretation meaningful without falsifying the circuit.

## 12. Fault Generation at Scale

[Implemented] GeneratedFaultEngine currently exposes explicit family-oriented
fault factories such as resistor open/wrong value, diode open/short, capacitor
open/short, connector open, transistor open/short, base resistor open, load
path open, and related variants. A family adds compatible candidates and the
selected candidate is chosen from that curated set using the seed.

[Implemented] Fault compatibility is primarily a family/type predicate. Fault
effects may add or remove real private CircuitJS paths or mutate a value.
GeneratedFaultBinding tracks the selected fault and application state.
GeneratedFaultValidator is family-specific and checks the selected fault's
expected effect. The current challenge lifecycle selects one fault.

[Inference] This is enough for a controlled leaf family, but not enough to
select faults safely from a composed board. A compatible target can still be a
no-op, collapse every output, be impossible to distinguish from another fault,
or make repair impossible under the available board actions.

[Recommendation] A scalable fault descriptor should include:

- target block, subsystem, role, and stable component ID;
- electrical preconditions;
- operating states in which the fault is observable;
- effect factory and fault state;
- expected rail/output/state impact;
- severity and whether total shutdown is intended;
- allowed isolation, replacement, jumper, or trace-repair actions;
- diagnostic endpoints and modes expected to be informative;
- interaction/exclusion rules with other candidates;
- stress and secondary-failure implications;
- a semantic fault ID independent of presentation text.

Fault selection should happen after a healthy candidate exists and should
evaluate the faulted candidate, not merely index into a list. Candidate
selection can remain seed-driven after compatibility and solvability filters
have run.

At larger complexity levels, reject a fault if it:

- produces no meaningful change;
- destroys all useful access without an intended advanced challenge;
- is not observable through permitted probes or behavior;
- has no valid repair path;
- is indistinguishable from every other selected candidate beyond the intended
  difficulty;
- or creates an untracked secondary failure.

Multiple simultaneous faults should remain a later feature. The single-fault
contract should first become compositional and diagnosable.

## 13. Solvability and Diagnostic Distinguishability

[Implemented] Current validation is real but local. GeneratedBoardVerifier
checks structural ownership and healthy net behavior, then a family validator
checks the selected fault. NPN, RC, and indicator validators exercise the
states relevant to their family, including real temporal advancement for RC.

[Implemented] The current validators do not search alternative fault
hypotheses, compare measurement signatures across candidates, or prove that
the permitted player actions contain a useful diagnostic path.

[Recommendation] A scalable acceptance pipeline should be staged:

1. Structural graph and component/package validation.
2. Domain and interface compatibility validation.
3. Healthy operating-state validation through CircuitJS.
4. Selected-fault application and meaningful symptom validation.
5. Accessible diagnostic signature generation over legal probe points,
   instrument modes, power states, and relevant user inputs.
6. Bounded isolation/repair existence validation.
7. Ambiguity and difficulty classification.
8. Only then physical layout and player presentation.

A diagnostic signature need not make the fault immediately unique. Beginner
challenges may leave two suspects until a component is isolated; advanced
challenges may require a sequence of measurements. The acceptance criterion
should be that a bounded, physically legal troubleshooting plan can separate
the intended fault from plausible alternatives and eventually restore the
functional contract.

The signature can be a normalized observation vector containing selected
voltages, currents, continuity/ohm/diode readings, state transitions, and
output behavior. Each value should be compared with measurement tolerances and
power/input state. The generator should not expose the vector to the player;
it is a validation artifact.

Exhaustively searching every probe sequence will not scale. A practical
approach is:

- use block metadata to identify candidate informative endpoints;
- evaluate a bounded set of representative measurement plans;
- allow isolation actions only where the physical model makes them legal;
- classify equivalent candidates into difficulty groups;
- run real CircuitJS solves for the final accepted plans.

[Inference] This makes “solvable” a testable property without claiming that
every troubleshooting path must be unique.

## 14. Scenario / Complaint Scaling

[Implemented] GeneratedScenario and GeneratedScenarioCatalog represent complaint
text, observed behavior, compatibility, and optional presentation. Scenario
selection occurs after faulted validation, and the selection seed is separated
from topology/fault selection by a deterministic hashing step. This is a good
privacy boundary: the normal complaint does not need to reveal the fault.

[Implemented] GeneratedScenarioLibrary is currently a small hard-coded
family-oriented catalog. It contains complaint patterns for indicators, the RC
delay behavior, and the NPN switch behavior. It does not derive a complaint
from a composed device graph or a general output contract.

[Recommendation] Generate scenarios from device-level behavior projections:

- customer-visible output or subsystem;
- reported state versus expected state;
- whether the symptom is always present, absent, stuck, intermittent, delayed,
  or load-dependent;
- preserved healthy observations such as a power indicator;
- available user actions and operating conditions.

Templates should be authored by semantic observed-behavior classes, not by
fault type. The projection should select only text whose predicates are true
in the solver-backed faulty state. The hidden challenge definition can retain
the affected subsystem and fault metadata while the complaint exposes only the
customer-observable subset.

[Inference] A device-level symptom model is the bridge between complex
subsystem composition and a believable incomplete complaint. It also lets
multiple faults produce the same symptom while preserving diagnostic
distinguishability through measurements.

## 15. PCB Generation Dependency

[Implemented] SeededPcbLayoutGenerator consumes a completed logical board and
physical package providers. It places components with a topology-aware
placement graph, routes Manhattan traces with A* and keepouts, validates
clearance/courtyards/silkscreen/trace geometry, and makes deterministic
attempts. It is not the electrical source of truth.

[Implemented] The current general layout uses a fixed-sized one-sided board,
one connector anchor, a finite placement attempt budget, grid-based routing,
and compacting/label placement. RC and NPN currently use manual layout
factories instead of the general generator. This is strong evidence that the
current router is a small-board proof, not yet a composition-ready physical
planner.

[Implemented] PcbBoardLayout validates geometry and stable net IDs. Physical
traces do not themselves determine CircuitJS connectivity. That separation
must be preserved.

[Inference] Logical generation must eventually precede physical generation:

healthy semantic graph → CircuitJS healthy acceptance → fault/diagnostic
acceptance → physical package assignment → subsystem-aware placement →
routing → physical validation.

Routing failure should reject a physical realization or choose a bounded
alternate realization. It should not silently change electrical semantics or
drive fault selection.

[Recommendation] Subsystem-aware layout should add:

- preferred regions and relative ordering;
- connector/board-edge constraints;
- rail and return routing channels;
- block port escape locations;
- keepout and isolation regions;
- high-current or high-voltage spacing;
- test-point/accessibility requirements;
- explicit jumper/zero-ohm policy for one-sided failures;
- layout complexity metrics that can feed difficulty, not electrical behavior.

The general router can remain a later-stage physical service. It should not be
forced to infer functional groupings from raw net connectivity alone.

## 16. Performance and Rejection Pipeline

[Implemented] The current small-board layout search uses a finite number of
placement attempts and invokes grid/A* routing plus geometry validation. The
family generators also construct and validate real CircuitJS simulations.
Temporal validation adds bounded solver advancement.

[Inference] For larger candidates, the expensive operations will be:

- CircuitJS construction, analysis, and repeated solves;
- multiple operating-state and temporal validations;
- fault candidate application and reanalysis;
- diagnostic signature/plan evaluation;
- placement attempts;
- per-net routing and clearance/keepout checks.

The physical checks also grow with the number of components, pads, traces, and
keepouts. The current single-board canvas and one-sided constraints will make
route rejection increasingly common as density rises.

[Recommendation] Use a staged, bounded pipeline:

1. Cheap seed/configuration and parameter checks.
2. Cheap block interface/domain compatibility.
3. Cheap value/rating/design-margin checks.
4. Build the healthy CircuitJS graph.
5. Run the minimum healthy state/profile set.
6. Generate and test a bounded fault candidate set.
7. Test symptom, diagnostic, and repair acceptance.
8. Assign physical packages and run placement/routing.
9. Run final physical and stable-ID validation.

Each stage should have a finite retry budget and a machine-readable rejection
reason such as invalid-domain-connection, no-healthy-state, no-observable-
symptom, no-repair-plan, ambiguous-diagnostic, or no-route.

Cache immutable block descriptors and value candidate calculations where
possible, but do not cache mutable CircuitJS state across candidates unless
the lifecycle can prove isolation. Stable ordering and deterministic
parallelism are more valuable than speculative optimization at first.

[Inference] The key performance rule is to spend geometry and temporal-solver
work only on candidates that have already passed cheap semantic and electrical
checks.

## 17. Deterministic Seed Strategy

[Implemented] Current generators use a mix of seeded Random instances,
normalized seed modulo cases, deterministic layout attempt derivation, and
separate scenario selection hashing. QuickPlay's family/selection source is
not itself a fully persisted root-seed protocol, although the selected family
and family seed make the generated family deterministic after selection.

[Implemented] The general layout generator derives different placement
attempts from the seed, and scenario selection avoids consuming the same
selection stream as topology/fault choices. These are good local practices.

[Inference] A composed generator needs a stronger global contract. One shared
random stream would make adding an optional support block change unrelated
values, fault choices, and geometry, making bug reports and challenge sharing
fragile.

[Recommendation] Define a versioned challenge seed descriptor:

- root challenge seed;
- generator/schema version;
- difficulty/settings ID;
- family/device intent ID;
- named sub-seeds derived from root and stable labels for topology, each block,
  values, support roles, fault candidates, layout, scenario, and presentation;
- stable ordering by explicit IDs before any indexed selection.

The derivation function should be explicit and stable across platforms. It
should not depend on object iteration order or incidental class hash codes.
Adding a block should consume only that block's named stream where practical.
The challenge descriptor and debug mode should expose root seed, generator
version, selected family/intent, and rejection reasons.

QuickPlay should eventually persist or display the root selection seed when
reproducibility is required. Random family selection can remain a product
choice, but the selected result must be replayable.

## 18. Coffee-Maker Complexity Exercise

### What the current architecture can do

[Implemented] It can represent many individual electrical pieces in CircuitJS
and can package a completed board with real components, physical parts,
bindings, faults, and behavior validation. A bespoke coffee-maker generator
could therefore be written.

### What a bespoke implementation would require

[Inference] A single current-style generator would have to hand-author most of:

- power-entry/protection and an abstract mains or high-voltage boundary;
- rectifier/filter or a pre-modeled DC input;
- low-voltage regulator and decoupling;
- button/input network;
- timer/controller behavior;
- thermal sensor and thermostat/safety path;
- relay, triac, or MOSFET driver;
- heater/load connector and current path;
- status indicators and perhaps a pump/motor path;
- multiple operating states and timing;
- physical packages, slot inventory, and test access;
- fault candidates for power, control, driver, load, sensor, and protection;
- solver-backed healthy/faulted/repair validators;
- complaint scenarios;
- and a manually arranged or specially tuned PCB.

The existing power model does not describe mains safety, isolation, current
capacity, or independent rails. The existing family validators do not provide
a reusable way to assert stateful multi-subsystem behavior. The current
one-family generator boundary would cause the class to own all of those
decisions.

### What a composed implementation should look like

[Recommendation] A coffee-maker intent should select constrained blocks such
as:

1. power-entry/protection;
2. isolated or abstract low-voltage supply;
3. user input;
4. timer/controller;
5. thermal sensor/control;
6. relay/triac/MOSFET driver;
7. heater/load interface;
8. status indicator;
9. optional pump or auxiliary output.

The assembler should connect typed ports, derive values and ratings, build one
CircuitJS graph, validate healthy states such as idle/heating/ready, select a
fault with an observable and repairable signature, then produce the complaint.
The PCB service should place power, control, and load regions with explicit
access and spacing constraints.

[Inference] The exercise demonstrates that the current foundation is viable
but the current family-generator boundary is not. The missing layer is
composition and semantic validation, not a replacement for CircuitJS.

## 19. Secondary Device Complexity Exercise

A useful intermediate example is a fan controller rather than another
indicator variant.

### Fan-controller blocks

- power entry and protection;
- regulated control rail;
- button, thermostat, or sensor input;
- timer/control block;
- MOSFET or relay driver;
- flyback/protection support;
- fan connector/load;
- status LED and test points.

### Current-style implementation

[Inference] A current-style generator could build this device by copying the
NPN pattern, adding another family state object, creating all CircuitJS
elements directly, manually selecting values and faults, writing a dedicated
validator, adding a scenario catalog entry, registering every physical part,
and likely introducing another manual PCB layout. The result could be valid,
but its reusable content would be trapped in one class.

### Composed implementation

[Recommendation] A composed fan controller should reuse a power block, a
control-input block, a switch-driver block, and a load-interface block. The
driver's output port should require a load domain and optionally a flyback
support role. The control block should expose a typed enable signal. The
device behavior contract should combine “fan starts/stops under control” with
the regulator and status indicator assertions.

Candidate faults could then be drawn from block-owned descriptors:

- input protection open;
- regulator output absent;
- control input path open;
- gate/base drive open;
- switch path open;
- fan connector open;
- flyback protection short or open where electrically meaningful.

The diagnostic validator can check whether rail, gate/base, and load-side
measurements separate those hypotheses. This is a bounded intermediate target
that tests composition, domains, faults, and layout without immediately
requiring a full appliance controller.

## 20. Recommended Long-Term Generation Architecture

[Recommendation] The long-term architecture should be a hybrid of constrained
functional blocks and shared generation services:

Device intent
→ subsystem selection and parameters
→ functional block instances
→ typed interface/domain wiring
→ logical board and CircuitJS graph
→ design/value/rating validation
→ healthy solver validation
→ compatible fault generation and validation
→ diagnostic/repair acceptance
→ scenario projection
→ physical package/spec assignment
→ subsystem-aware placement and routing
→ final physical validation
→ GeneratedBoardInstance and challenge runtime

The layers should have distinct ownership:

- Device intent chooses what kind of device is being generated.
- Block instances describe reusable functional behavior and interfaces.
- An assembler allocates stable global IDs and merges ports/nets.
- A design service derives values and checks ratings.
- A CircuitJS adapter builds the electrical graph and remains the source of
  truth.
- A fault service owns candidate filtering, application, and validation.
- A diagnostic service checks accessible distinguishability and repair paths.
- A scenario service projects hidden behavior into an incomplete complaint.
- Physical services assign packages, slots, footprints, and rendered geometry.
- The challenge runtime owns mutable board state and player interaction.

Existing family generators should remain leaf implementations during the
transition. The first goal is not to rewrite all five families. It is to add
one small composition path and prove that it can use the existing board,
binding, fault, physical, and validation seams.

[Recommendation] Keep unrestricted random graph generation out of scope.
Generalize the assembly and validation machinery, not the set of allowed
electrical ideas. A library of well-defined blocks can still produce many
boards through topology choices, parameters, support roles, layout seeds, and
fault/scenario combinations.

## 21. General Generator vs Functional Block Composition

### General unrestricted generator

[Inference] An unrestricted graph generator would have to decide whether a
random graph is:

- physically plausible;
- electrically stable;
- functionally meaningful;
- within component ratings;
- observable through the UI;
- diagnosable with legal measurements;
- repairable with available parts/actions;
- and routable on an educational PCB.

Those constraints are coupled. Randomly satisfying one does not make the
others likely. Rejection rates and debugging cost would become dominant, and
the resulting boards would often be visually complex but instructionally
empty.

### Functional-block composition

[Inference] Constrained blocks encode intent, interface semantics, expected
states, component roles, and likely diagnostic access. Composition can vary
which blocks are present, how valid interfaces are wired, which support roles
are added, which values are chosen within constraints, and where the resulting
board is placed. This produces meaningful variability with a bounded search
space.

### Recommendation

Use functional-block composition as the primary generation model and retain
general utilities for:

- ID allocation;
- port/net aliasing;
- domain compatibility;
- value candidate enumeration;
- fault filtering;
- diagnostic measurement planning;
- physical placement/routing;
- deterministic seeding;
- and validation/rejection telemetry.

Do not make every functional block a complete challenge family. Do not make
the assembler responsible for semiconductor physics. Do not make the PCB
renderer infer circuit intent.

## 22. Procedural Complexity Levels

The following levels describe realistic boundaries for the current project.
They are recommendations for planning, not claims that all levels are
implemented.

| Level | Approximate board/device envelope | Variation | Required architecture | Current status |
| --- | --- | --- | --- | --- |
| Level 0: leaf proof | 3–7 board components, one functional path, one fault | Seeded values, faults, and small layout variation | Current family generators, solver validators, physical runtime | Implemented |
| Level 1: supported leaf | 6–15 components, one main function plus purposeful support | Optional role-bearing protection, decoupling, indicator, or connector | Role metadata, design-intent values, generic support registration | Partially available as hand-authored family logic |
| Level 2: two-block device | 12–30 components, two interacting blocks, one shared rail or control interface | Topology/block choices, support roles, bounded faults | Block contracts, port/domain compatibility, assembly, composed behavior | Not implemented |
| Level 3: multi-rail controller | 25–60 components, power, control, driver, output, and sensor paths | Independent rails, isolation/adapter choices, temporal/state behavior | Domain model, value/rating synthesis, multi-subsystem fault and diagnostic validation, regional layout | Not implemented |
| Level 4: appliance controller | 60+ components or multiple high-energy/load boundaries | Multiple operating modes, safety paths, intermittent/temporal behavior | External-interface/safety model, scalable physical planner, bounded diagnostic search, stateful behavior abstractions | Long-term |

[Recommendation] Complete and stabilize the current leaf-family milestones
before using Level 2 as a new architecture proof. The next architecture
investment should be measured by whether one composed Level 2 challenge can
be built without copying a complete family generator.

## 23. Prioritized Recommendations

### Tier 1

Preserve the architecture that already protects correctness:

- CircuitJS as electrical truth;
- stable board/pad/net IDs;
- BoardModificationController as the graph mutation owner;
- GeneratedBoardInstance and generic challenge lifecycle;
- explicit fault bindings;
- physical slot/part identity and provider registries;
- family-specific leaf validators behind generic behavior contracts;
- deterministic seeded generation;
- and intentionally limited one-sided PCB scope.

Finish the currently sequenced leaf-family proof work before broadening the
generation surface. Do not silently reorder the roadmap as part of this
audit.

### Tier 2

Build the minimum composition foundation before the project adds many more
families:

1. Functional block descriptors with namespaced local entities and typed
   ports.
2. A bounded assembler that merges block contributions into one
   GeneratedBoardInstance.
3. Separate power/signal domain contracts and interface preflight.
4. Intent-driven value/rating selection using finite standard-series
   candidates.
5. Role-based optional support and red-herring blocks.
6. Versioned root seeds and named sub-seeds.
7. Generic registration adapters for physical specifications, bindings, and
   block-owned fault candidates.

### Tier 3

Add the capabilities required for credible multi-subsystem devices:

- composed healthy/faulted/repair behavior contracts;
- semantic fault candidate filtering;
- solver-backed diagnostic signature and bounded-plan validation;
- device-level symptom projection and complaint templates;
- subsystem-aware placement/routing regions and test access;
- independent rails, current limits, isolation, and external interface
  contracts;
- stateful and temporal behavior composition.

This tier is the threshold for a coffee-maker-like or fan-controller-like
challenge that is more than a bespoke demo.

### Tier 4

Defer these until the composition and solvability layers work:

- multilayer/SMD physical scaling;
- advanced routing optimization and caching;
- multiple simultaneous faults;
- detailed thermal and bench-power damage modeling;
- broad automated diagnostic-plan optimization;
- full difficulty/scoring calibration across large devices.

These are valuable, but they should consume a stable composed model rather than
become another source of generator coupling.

## 24. Recommended Bounded Future Tasks

The following are proposed bounded tasks. They do not authorize changing the
ordered roadmap in this audit. The current roadmap's next leaf milestones
remain the sequencing authority; these tasks should be scheduled only after
the architect confirms dependencies.

### Task A: Define the functional-block descriptor and port contract

Goal: represent a reusable block's identity, local entities, ports, roles,
healthy assertions, and contribution boundaries without changing current
families.

Likely scope: new logical descriptor/port classes, stable ID namespace helper,
and contract tests. Keep current generators as adapters or untouched leaves.

Depends on: current stable board/net/binding model.

Acceptance: two in-memory block descriptors can validate local IDs and
interface metadata without constructing a PCB or changing CircuitJS behavior.

Risk: medium; over-generalizing this contract would make every future block
pay for fields it does not need.

### Task B: Add typed electrical-domain metadata and compatibility preflight

Goal: distinguish external inputs, rails, returns, analog signals, digital
controls, and load interfaces before graph construction.

Likely scope: separate domain/port model, adapters for existing
ExternalBoardPowerInput and BoardNet, and compatibility tests.

Depends on: Task A.

Acceptance: valid and invalid connections are classified deterministically;
existing leaf families continue to generate unchanged.

Risk: medium; choose an extensible boundary for AC/isolation without coupling
the first version to a full power-electronics solver.

### Task C: Assemble one two-block challenge from existing leaf behavior

Goal: prove composition with one small device, such as a control block plus a
load/indicator block, while retaining real CircuitJS behavior.

Likely scope: assembler, global ID allocation, local-to-global bindings, one
composed behavior adapter, and one scenario.

Depends on: Tasks A and B.

Acceptance: a deterministic composed board has one merged logical graph,
healthy and faulted solver validation, at least one legal repair, and no
copied complete-family generator.

Risk: high; this is the first integration proof and may reveal that current
family generators need narrow extraction adapters.

### Task D: Introduce intent-driven value synthesis for one block

Goal: derive a bounded set of E-series/rated values from a block's target
behavior and interface ranges.

Likely scope: value candidate model, design equations, margin checks, and
tests for LED/RC or driver values.

Depends on: Tasks A and B.

Acceptance: values are reproducible, within ratings, and rejected when the
healthy CircuitJS behavior misses the declared intent.

Risk: medium; avoid turning this into a generic symbolic circuit solver.

### Task E: Add role-based support and red-herring blocks

Goal: add optional healthy decoupling, protection, indicator, connector, or
filter roles with explicit electrical and physical constraints.

Likely scope: block role descriptors, support selection, and one composed
scenario.

Depends on: Tasks A–C.

Acceptance: optional support changes the real graph and layout, has a declared
purpose, and cannot become an unconnected decorative part.

Risk: medium; support roles can accidentally dominate fault selection or
create unintended parallel paths if contracts are too weak.

### Task F: Add composed fault, symptom, and diagnostic acceptance

Goal: select faults from multiple blocks and prove that the resulting
challenge is observable, diagnosable within a bounded plan, and repairable.

Likely scope: fault descriptors, composed behavior/diagnostic validator,
measurement signature normalizer, and rejection reasons.

Depends on: Tasks A–E and the existing fault/measurement runtime.

Acceptance: at least two plausible faults have distinguishable legal
measurement plans, and an accepted fault has a real repair path.

Risk: high; this is the core anti-brute-force/scalability requirement.

### Task G: Add subsystem-aware physical placement and routing

Goal: place a composed board by regions, ports, access points, and domain
constraints rather than only raw net topology.

Likely scope: placement regions/anchors, router constraints, physical
validation metrics, and a bounded one-sided fallback policy.

Depends on: Task C and stable physical package/provider boundaries.

Acceptance: the same composed electrical board can produce multiple valid
deterministic layouts without changing its logical graph or fault.

Risk: high; route failure must remain a physical rejection and not feed back
into electrical semantics.

### Task H: Project device behavior into complaint scenarios

Goal: produce incomplete customer complaints from composed observed behavior.

Likely scope: behavior projection, template catalog, hidden/visible metadata
boundary, and compatibility tests.

Depends on: Task F.

Acceptance: complaint text describes only solver-validated customer-visible
symptoms and does not reveal subsystem/fault/repair answer fields.

Risk: medium; templates need enough variation without inventing facts.

### Task I: Build one bounded appliance-style prototype

Goal: exercise the architecture at Level 3 or early Level 4 using a fan,
pump, or coffee-maker subset.

Likely scope: a deliberately limited set of blocks and operating states, not
a production appliance simulator.

Depends on Tasks A–H.

Acceptance: the prototype demonstrates composition, multi-domain interfaces,
one or more purposeful red herrings, a solver-backed fault, a diagnostic path,
repair, complaint projection, and a valid physical layout.

Risk: high; this should be a validation of the architecture, not a reason to
expand scope into all appliance electronics.

## 25. File/Class Watchlist

The following files/classes are the most important boundaries to monitor when
future generation work begins.

### Runtime and orchestration

- CirSim
- GeneratedBoardInstance
- GeneratedChallengeController
- GeneratedBoardVerifier
- BoardModificationController
- GeneratedBoardFamilyPolicy
- GeneratedBoardFamilyState
- GeneratedChallengeBehaviorContract
- GeneratedChallengeBehaviorAdapter
- GeneratedTemporalBehavior

### Logical board and electrical bindings

- TroubleshootBoard
- BoardComponent
- BoardPad
- BoardNet
- ExternalBoardPowerInput
- BoardSimulationBindings
- GeneratedComponentBindings
- GeneratedExternalPowerBindings
- GeneratedComponentConnectionBindings

### Current family leaves

- LedIndicatorGenerator
- DiodeProtectedIndicatorGenerator
- ParallelDualIndicatorGenerator
- RcDelayGenerator
- NpnLowSideSwitchGenerator
- RcDelayPcbLayoutFactory
- NpnLowSideSwitchPcbLayoutFactory
- the in-progress NMOS generator and related support, when that work is
  intentionally reviewed as a separate milestone

### Fault and scenario services

- GeneratedFaultEngine
- GeneratedFault
- GeneratedFaultBinding
- GeneratedFaultEffect
- GeneratedFaultValidator
- GeneratedRepairValidator
- GeneratedScenario
- GeneratedScenarioCatalog
- GeneratedScenarioLibrary
- GeneratedChallengeDefinition
- GeneratedChallengeCatalog

### Quick Play and deterministic selection

- QuickPlayFamilyRegistry
- QuickPlaySelector
- QuickPlaySession
- QuickPlayDeveloperVerifier

### Physical and PCB services

- PhysicalBoardRuntime
- PhysicalBoardSlot
- PhysicalPart
- PhysicalPartInventory
- PhysicalSpecification
- BoardPhysicalSpecifications
- PhysicalPackages
- StandardPcbFootprintProviders
- StandardPhysicalDefinitionProviders
- StandardPhysicalPartRenderProviders
- SeededPcbLayoutGenerator
- TopologyPlacementGraph
- PcbBoardLayout

[Recommendation] Any block/assembler work should integrate with these
boundaries rather than create a second board model, second fault engine,
second physical inventory, or second route representation.

## 26. Risks and Open Questions

### Electrical domains and safety

- How should AC, mains-like input, isolation, protective earth, and
  high-energy load boundaries be represented without pretending to be a
  safety-certified power-electronics simulator?
- Which domain rules belong in a semantic preflight and which must be left to
  CircuitJS?
- How should current limiting and source impedance affect both behavior and
  secondary damage?

### Controller and behavioral abstraction

- How should a timer, comparator, microcontroller, or state machine be
  represented while preserving the CircuitJS source-of-truth rule?
- When is an abstract behavioral element acceptable, and what solver-backed
  evidence must it expose?
- How should intermittent, timed, and input-dependent faults share a contract
  with static faults?

### Composition ownership

- Which layer allocates global IDs and owns cross-block nets?
- How are local block component IDs kept stable when optional blocks are
  present or absent?
- How are shared rails, grounds, and isolation boundaries merged explicitly?
- How does a block contribute a physical slot without taking ownership of the
  whole board layout?

### Solvability and difficulty

- What is the minimum accepted diagnostic plan for each difficulty level?
- How much ambiguity is educational before it becomes guesswork?
- How should equivalent faults be grouped when they have the same observable
  signature but different repairs?
- How should diagnostic validation account for realistic in-circuit parallel
  measurements and required isolation actions?

### Fault interaction

- How are stress-induced secondary failures represented and attributed?
- How does the generator avoid selecting a fault whose effect masks every
  other useful measurement?
- When should multiple faults be considered a later challenge mode rather than
  an accidental consequence of a bad repair?

### Physical scaling

- At what component/trace density should one-sided routing require a jumper,
  zero-ohm link, or alternate board outline?
- How should layout preserve the visual difficulty of tracing without making
  probes inaccessible?
- When should subsystem regions become hard constraints rather than soft
  placement preferences?
- How should package/render providers expose access, orientation, and thermal
  constraints without learning circuit semantics?

### Determinism and evolution

- What generator version must be persisted so an old challenge can be replayed
  after a block schema changes?
- Which changes are allowed to preserve seed replay, and which require a new
  version?
- How will rejection reasons and intermediate seeds be captured in debug mode
  without leaking hidden answers to normal players?

### Maintainability

- How much common behavior belongs in block contracts before the contract
  becomes a second god object?
- Which current family validators can be decomposed into reusable assertions,
  and which are truly family-specific?
- How can the project retain the educational clarity of small leaf families
  while adding composed devices?

### Recommended decision gate

[Recommendation] Do not begin an unrestricted “random complex board”
initiative until a two-block composed challenge has passed all of the
following: typed interface validation, value/rating validation, real healthy
and faulted CircuitJS validation, a bounded legal diagnostic plan, real repair,
complaint privacy, deterministic replay, and multiple valid physical layouts.
That gate would demonstrate that the architecture scales in the direction the
project actually needs.
