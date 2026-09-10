# TroubleshootJS Architecture

CircuitJS remains the electrical simulation engine. TroubleshootJS layers its
challenge, board, and instrument behavior around that engine rather than
replacing its electrical results.

The current development contract is Edition 3.0 of [ROADMAP.md](ROADMAP.md).
Historical generators, serialized formats, ID spellings, stage counts and
golden reports are not compatibility obligations. Current solver truth,
physical correspondence, coherent ownership, deterministic inputs, truthful
instruments, valid repair behavior and answer privacy remain required. The
[current task report](CODEX_TASK_REPORT.md) records qualification and limitations;
older evidence packets describe their own historical candidates.

## A05 qualification and current limits

A05 current qualification is recorded in [A05 evidence](task-evidence/A05/README.md): two independently controlled NMOS/NPN channels, supply-present support, current generator 5, exact repeated-instance ownership and solver-backed diagnosis/repair/retest. The normal URL and visible launch control reach this implementation. The layout and four OPEN resistor fault owners remain bounded; no arbitrary composition, transistor mutation, persistence or Q15/Q60 qualification is implied.

All six selected current Gate A05 routes pass on the final production source; the maintained native suite has 15 Java suites plus independent seed/value/role oracles. Three manifest cases and current numeric choices agree exactly between JVM and GWT. The strict CLI launcher is not certified by the separate owned visible-browser evidence. Final closure/publication status is in the current report.

## Current design identity and reconstruction

`BlockNamespace.idFor()` is the semantic encoder for the resolved composition.
`BlockRealizationIdentity` records the stable instance key, role, provider and
schema variant. Resolved component, endpoint, pad, local-net, role and port IDs
carry the provider/variant envelope; component-terminal tuples use the same
owner. Collection positions, allocation order and random cursors are absent.
The pure port preflight uses explicitly provisional addresses before provider
selection. Those addresses are not persisted as board/report identities.

`DeviceBusBindings` owns declared device buses and their conductive anchors.
It validates the temporary electrical equivalence partition, then maps each
semantic local net to its explicit bus or its unjoined semantic identity.
External supply/return references permit real singleton buses. Conflicting bus
owners, missing merged-bus declarations and dangling aliases reject. The plan,
electrical spec and BoardNet construction consume this mapping. A lexical union
representative is never the board net ID. Physical conductors, acquired parts,
CircuitJS analyzed nodes and live endpoint ownership retain separate mappings.
Matching immutable IDs never authorizes a different construction attempt.

`RealizationManifest` schema 2 contains the current descriptor, block
realizations, algorithm pins, resolved choices, net/conductor bindings and
actual target identities. Records use deterministic framing and ordering;
seeds retain exact signed-long decimal transport and finite numeric choices
retain exact binary64 encoding. There are no live CircuitElm references,
mutable graphs, solver-node numbers or future saved-program/state envelopes.
The bounded manifest permits 2,048 resolved choices within the unchanged
262,144-character encoding limit; both construction and parsing reject excess.

`A03RealizationReplay` resolves only `bounded-assembler@5`, validates its exact
current recipe and physical choices before mutable assembly, and checks the
captured result again afterward. Retired or unknown schemas, algorithms,
packages or choices reject. The six current leaf families use
`LeafChallengeReplay` with `leaf@1`; no general leaf composition or saved-game
adapter is implemented. Package geometry remains independently at contract 3.

## Provider-owned electrical and physical construction

`ElectricalRealizationSpec` and `ElectricalUnitPackageMap` carry provider
declarations, resolved values, physical units, packages, terminals and explicit
joins. `ElectricalConstructionContext` is the single private allocation and
binding owner. Local providers allocate declared elements within their own
solver reservations. Canonically ordered declared owners receive distinct bounded
coordinate envelopes; allocation order never supplies semantic identity.
`BoundedElectricalDeviceConstructionAdapter` owns external
supplies, connectors, command control and exact cross-provider bridges.

`BoundedGeneratedBoardAssembler` resolves the plan, describes the board, iterates
local provider declarations, completes the device joins, builds the layout and
materializes one runtime. Its failure boundaries are mapping, electrical
construction, layout, registration and validation. It has no historical source-
half stage, fixed-versus-resolved load branch or provider-specific physical part
registration. The current resistive and controlled compositions remain bounded
device recipes. The controlled intent selects compatible NMOS/NPN role providers
for two stable channel instances and includes one supply-present contribution.

`PhysicalConstructionProvider` declares nets, package-backed parts, terminals,
nameplates, runtime backing and fault/repair policy. The generic materializer
consumes those declarations in deterministic order without naming the
resistive/controlled families or their source/driver/load nets. Supported
materialization categories remain fixed parts and mutable resistors.

The physical boundary validates each declared component, pad, net and terminal
against the electrical specification and package/unit map. Secondary paths,
attachments and fault backings must match the precise declared component and
terminal relationships; a same-kind element from elsewhere is insufficient.
Plan/spec/metadata and completed receipt/runtime ownership are checked before
physical allocation. Each context issues one canonical receipt; a copied
receipt cannot acquire that context's authority. Runtime provider metadata is
derived from the same validated provider ID, without a separate alias input.
Shared-package mapping has bounded data controls, not a
claim of arbitrary multi-unit runtime support.

Allocation records ownership before potentially failing initialization. A
completed scope must have bound all its units. Abort revokes its receipts and
clears the private registries without touching an unrelated active owner.
Persistent board pads remain on device copper during lead removal; connection
bindings retain their exact canonical endpoint objects. The resistive recipe
names incorrect resistance at R1 with a 100000-ohm effect directly; controlled
RG, RB and RLOAD faults name their real open-switch backing.

## Functional block descriptions and namespaces — Task 44

`FunctionalBlockDescriptor` is an immutable local contribution description,
separate from `TroubleshootBoard`, `GeneratedBoardInstance`, live CircuitJS
elements and physical runtime ownership. It declares a block type/schema version,
stable instance key, typed deterministic parameters (boolean, 32-bit integer,
finite double or text), components and terminals, endpoints, pads, local nets,
roles and ports. Each component terminal has one endpoint; a pad maps an endpoint
to a local net. Unpadded internal endpoints are allowed. Every reference resolves
within the descriptor; an endpoint cannot acquire competing pad/net mappings.

Roles declare required or optional local contributions. Required roles contain
at least one member; optional roles may be empty. A port names a role and a
PAD, NET or ENDPOINT attachment included in that role. These declarations do not
specify runtime connection counts or create wiring. All input collections,
including nested terminal/member lists, are copied into canonical immutable
collections. `BlockContractException` identifies malformed IDs, duplicates,
missing declarations, dangling references, contradictory roles and invalid
attachments with stable codes and field/entity IDs.

`BlockNamespace` owns immutable lookup and ID derivation for one device schema.
Resolved IDs use
`tsj-realization-v1/<device>@<version>/<instance>/<provider>@<version>/<variant>@<version>/<kind>/<local>`.
Provisional preflight addresses use the distinct `tsj-preflight-v1` prefix.
Local IDs match `[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}`; separators, whitespace and
Unicode reject. Terminal tuples are framed when dot-separated local names would
be ambiguous. Duplicate instances fail, and unchanged semantic tuples remain
stable under unrelated inventory reordering. No allocation counter or global
UUID supplies design identity.

Blocks own these local descriptions and ports. Device intent owns proposed
interblock wiring; the assembler owns runtime allocation and net merging. Semantic IDs are distinct from solver nodes, acquired physical-part
identity and owner authorization. `FunctionalBlockExamples` contains only small
NMOS driver and resistor/LED load descriptor fixtures. They create no playable
block or board. Task 45 adds separate electrical metadata at the immutable port
ID/attachment seam used by current bounded composition.

`scripts/verify-current-contracts.ps1` compiles current client sources once and
exercises 15 Java contract suites plus independent seed/value/role equations and
the browser report protocol. Its native tests do not establish solved behavior;
the five-permutation JDK8/GWT build and `verify-a03-browser.ps1 -Gate Current`
remain separate production gates. Explicit generic packages serve developer
fixtures; package-less constructors, implicit geometry projection and duplicate
loose-geometry and board-probe aliases are retired. Local package keep-outs,
their placed projections and physical lead probes retain distinct APIs.

## Electrical port metadata and preflight — Task 45

Task 45 is accepted: focused tests, the final production build, all 55 selected
legacy routes and independent review pass, with evidence reuse and limits in
the [Task 45 evidence](task-evidence/task-45/README.md).

`ElectricalPortContract` separates role, block-relative direction, source/sink
behavior, drive/loading, voltage evidence, reference/isolation, digital levels,
merge policy and required/provided accessibility. Immutable scalar and range
values distinguish known, unknown and not applicable; nominal voltage does not
imply tolerance, capacity, loading or switching thresholds. Constructor checks
reject nonfinite/inverted values, negative current bounds, contradictory level
declarations and missing references. `ElectricalContractException` reports a
stable code and field/entity ID.

`ElectricalBlockContract` binds one electrical declaration to each exact Task 44
port and validates its local reference net. References are scoped by block
instance and local net; matching `GND` labels across blocks do not establish a
common reference. Isolation IDs are explicit device-domain declarations. A
regulator, divider, level shifter, relay or isolation barrier can declare its
input/output port pair with separate voltage evidence. Isolated sides retain
distinct references and isolation IDs. The contract performs no conversion or
internal wiring and makes no claim about an adapter's simulated implementation.

Device intent owns immutable `ElectricalConnection` proposals.
`PortCompatibilityPreflight.check` implements `TSJ-PORT-PREFLIGHT-1` over these
descriptions. Per-call semantic equivalence combines overlapping proposals and
local conductive aliases so that group conflicts and aggregate current demand
cannot hide in separate pair checks. Only explicitly permitted return wiring
with adequate isolation evidence establishes a common reference. Different
isolation domains cannot merge; incomplete reference evidence stays unproven.

Supported source/load groups require one voltage driver, full source-range
containment in each receiver's limits, and capacity covering the sum of declared
bounded loading. Digital/control receivers additionally require adequate LOW
and HIGH guarantees, thresholds and matching active levels. Equal nominal
voltages do not permit multiple driven outputs. Logic-to-analog transfer remains
unsupported even when numerical ranges fit. Open-drain low-side sinking
does not provide a positive supply; pull-up/transfer analysis remains unsupported
in v1. Declared access requirements are checked without claiming later physical
reachability. Wiring an adapter's input and output together is rejected.

Results are `COMPATIBLE`, `INCOMPATIBLE`, `INSUFFICIENT_INFORMATION` or `MALFORMED`,
with stable, canonical connection/port/field diagnostics. Compatibility certifies
only this declared policy, not solver operation, electrical safety or challenge
validity. No BoardNet, CircuitJS candidate, allocator or runtime owner is created
or changed by checking a proposal.

`LegacyInputPortMetadata` reads the existing external-input, pad and optional
nameplate objects into an immutable external-input contribution view. It copies
the actual IDs and nominal values; missing specifications remain unknown.
`LowSideSwitchInputMetadata` is the leaf-owned provider for the existing NPN/NMOS
load/control input mappings. It verifies the separate J1/J2 boundary and assigns
roles without a family switch or numeric value table in the generic checker.
These adapters are callable metadata utilities, not new production admission
gates; existing generators, seeds, electrical graphs and player behavior retain
their current paths. Current bounded composition consumes separate typed providers.

## Challenge description, named streams and leaf replay — Task 46

`ChallengeDescriptor` is an immutable request, independent of a generated board,
solver state or random cursor. Schema 2 binds a signed 64-bit root seed,
generator ID/version, device-intent ID/version, difficulty-profile ID/version,
the existing `PcbGeometryContractVersion`, and complete `GenerationConstraints`.
Its bounded ASCII canonical representation uses sorted fields, versioned IDs
and exact decimal seed strings; parsing accepts field permutations but rejects
duplicates, missing/unknown fields, invalid ranges and noncanonical numbers.
All effective overrides must be represented, even when replay cannot enforce
them. Unsupported schema/constraint encodings reject; referenced versions are
retained as descriptions and resolved explicitly before runtime construction.

`GenerationConstraints` version 1 separates requested count ranges,
parallel/temporal requirements and allowed instruments from Task 41 measured
evidence and admission. Counts cover blocks, components, domains, plausible
physical owners, diagnostic depth, input/power transitions, isolation actions,
temporal samples and purposeful auxiliaries. Instrument IDs use the existing
DC voltage, resistance, continuity and diode vocabulary. Unspecified, exact
zero and an explicitly empty allowed set are distinct. Counts use nonnegative
signed-int encoding bounds, not calibrated difficulty thresholds. Contradiction
checks reject impossible temporal/depth combinations. No difficulty presets,
search, scoring or admission policy is implemented by this value.

`NamedRandomStreams` derivation version 1 uses FNV-1a-64 over length-framed ASCII
fields, followed by fresh local SplitMix64 cursors. Arithmetic is modulo 2^64
with unsigned right shifts on both Java and compiled GWT. Each tuple includes
root seed, versioned intent, device/block scope, stable block key, explicit
concern token/revision and semantic key. `block` and `values` require block
scope; topology, support, fault, scenario, placement, routing and presentation
support either scope. Reopening restarts a stream. Seed inspection never draws.
Selection sorts copied unique candidate IDs before bounded rejection sampling;
empty/duplicate sets reject. A changed eligible population may change selection.

| Version/input owner | Intended effect |
| --- | --- |
| Descriptor schema | Encoding/interpretation only; absent from named seed tuples |
| Generator ID/version | Resolves the replay algorithm; absent from named tuples |
| Device-intent ID/version | Resolves intent meaning and affects its named seeds |
| Named derivation version | Changes the tuple/hash contract for all named seeds |
| Concern revision and semantic key | Affect only that exact scoped tuple |
| Block key | Affects that block scope; optional inventory is never hashed globally |
| Constraints/profile/geometry | Bound replay inputs; do not reseed unrelated named concerns |
| Task 44 namespace and runtime owner | Separate identities; no runtime objects enter the descriptor |

`LeafChallengeReplay` accepts only `leaf@1`, current family intent version 1,
`quick-play@1`, geometry 3 and unspecified constraints. It reaches each current
family directly with the exact seed. All layouts report the current corrected
algorithm epoch 4; package geometry remains 3. The leaf families keep their
existing useful random decision schemes and fault/scenario selection. Named
streams support current composed generation and concern-isolation tests; they
do not pretend to replace leaf randomness. Every reconstruction creates a fresh
owner. Specified unsupported constraints and retired/unknown artifacts reject
before generation rather than selecting a fallback implementation.

The explicit `tsjDebug=true&tsjVerifyTask46=true` route executes the same pure
contract corpus used by JDK8, publishes canonical string outputs and compares
direct generation with parsed-descriptor replay. This debug verifier keeps
a workbench in debug mode, permitting the initial challenge's existing normal
diagnostic admission. Subsequent paired installs use the existing developer
installation scope, real healthy/faulted solver validation and fresh controllers;
they do not claim independent normal admission. Cleanup constructs another
fresh challenge instead of extending same-owner restoration. The developer
diagnostic API exposes deterministic rejection codes and fields. DOM attributes
publish descriptor, reserved seeds, snapshot and parity output only behind both
flags. Normal player pages receive none of these attributes. The
[Task 46 evidence](task-evidence/task-46/README.md) defines
the algorithm, fixed oracle corpus, replay coverage and qualification limits.

## Composition entry runtime boundary

The accepted gate supplies `CirSim.isGeneratedRuntimeSettled()` as the
shared actionability check. It combines analysis, verification, physical
mutation, active measurement, queued power, observational work and failed
recovery state. Workbench, instrument, challenge, power and mutation entries
check their current owner and this state. READY alone is insufficient.
Completed challenges retain their existing semantic operations. Pending work
suspends instrument controls without silently discarding the selected mode or
probes; normal exit still drains the accepted measurement cleanup path.

One-shot repaint requests retain both their originating board reference and
the actual scheduled command reference. Superseded commands discard without
updating a new owner or clearing its newer request. Retained workbench handlers
check the workbench and challenge references. Customer retest publication checks
the originating challenge and request identity, including after reset. The
periodic simulator timer remains simulator-owned.

`ResistorMutationScope` covers one replaceable resistor's lead, removal,
reinstallation and catalog operations. It prepares the touched graph,
connection flags, slot attachment, binding and append-only registration state,
validates committed ownership, and compensates those writes on failure.
Original physical fault and secondary damage objects are retained. A failed
compensator retains the primary and suppressed cleanup errors, isolates power,
stops the runtime and disables actions. This is a bounded resistor transaction;
other mutable part providers are rejected by the composition provider boundary.
Typed inventory views share the single `PhysicalBoardRuntime` registry.

`FreshGeneratedRuntimeInstallation` requires a distinct candidate with disjoint
mutable owners. It detaches the original workbench, supplies private graph and
UI containers to the existing installation path, runs the actual solver and
challenge validation, then exposes the settled candidate. On failure Task 41
restores the untouched original owner. Task 41 remains Option A and does not
restore arbitrary mutations to that original owner's physical objects. Nested
fresh installation and current-instance installation reject.
The composition-facing `installComposition` entry rejects unsupported mutable
providers before invoking that publication path. The generic `install` entry
remains available for existing leaf proof fixtures; composed callers use the
restricted entry.

`GeneratedRuntimeInvariant` checks actual board, graph, power, component,
connection, physical slot/part, inventory, provider, stress and selected-fault
references. The settled simulator form additionally checks current controllers
and rejects pending overlays. The structural form is used while the bounded
mutation or fresh installation owns its guard. Immutable package definitions
and established board-side endpoint aliases retain their existing authority.
Component, power and detachable-element owning claims share one identity check.
Installed primary/auxiliary bindings must belong to the exact physical part,
and resistor pad terminal names must match that installed part's exact endpoint.

The double-gated developer route `tsjDebug=true&tsjVerifyCompositionGate=true`
executes the compiled lifecycle, partial-mutation and attachment failure corpus.
`tsjCompositionGateControls=true`, also requiring debug, exposes a hold of real
verification for visible pending-control checks. Neither enables Task 47 or
adds a player menu entry. Qualification status and exact evidence remain in the
[gate packet](task-evidence/task-47/gate/README.md).

## Bounded assembly and executable contributions — Task 47

`BoundedAssemblyRequest` binds `bounded-assembler@4`, the `resistive-coupling@1`
intent, `developer-canary@1`, geometry 3 and an exact signed 64-bit seed to
explicit block declarations and device wiring. `BoundedAssemblyPlan` resolves
the typed resistor-source/load providers and requires Task 45 COMPATIBLE before
mutable allocation. It rejects altered electrical facts, unknown versions,
changed fixed wiring and unsupported specified constraints. Supported count
ranges must include two blocks, three components (including J1) and one explicitly
joined reference domain. Historical generator revisions reject before allocation.

`ComposedBlockContribution` carries immutable local resistor recipes, endpoint/
pad/net declarations, healthy predicates, observed-current semantics, a local
high-resistance fault and replacement/input/retest requirements. Providers own
no generated instance, PCB, solver element, inventory or fault controller.
VALUES/revision-1 streams use the independent source/load keys and `resistance`;
the device FAULT/revision-1 stream uses `selected-fault`. Exact stable decision
keys map to qualified owners. No qualified ID is sanitized into a local key.

The plan retains semantic local nets and explicit connection provenance. Only
the declared OUT-to-IN and RETURN-to-RETURN connections join the two blocks.
Equal local SUPPLY labels do not join nets. Device buses name the separate
source-supply, loaded-output and return networks independently of union order.
The local fault recipe explicitly declares kind, physical target and effect;
no OPEN wrapper is reinterpreted by version.

`BoundedGeneratedBoardAssembler` constructs one private board, CircuitJS graph,
binding set, physical runtime and generated instance. Per-slot resistor inventory
views use that runtime's single part registry. Its immutable target receipt maps
block owners to actual slots, original parts, provider capabilities, fault and
repair targets; endpoint records retain the chosen actual CircuitJS posts.
Device-owned J1 and a controlled 5 V external input complete the envelope.
The existing package/layout infrastructure supplies its three-component PCB.
No complete leaf generator or hidden child board is used.

`GeneratedFaultLocus` validates current qualified component identities at its
owner boundary. Simple leaf component IDs retain their current grammar.
Qualified terminal/path IDs, wrong namespace kinds and private identity tokens
remain rejected.

`ComposedResistiveDeviceBehavior` executes the local contribution rules and checks
the actual coupled divider. Existing fault effects, physical resistor replacement,
measurement adapters, lifecycle and customer retest remain authoritative. Device
services select the complaint and check restored voltage/current function,
including supported alternative resistor values. Private failures report their
construction stage and cleanup outcome; publication uses the accepted disjoint
`FreshGeneratedRuntimeInstallation.installComposition` boundary.

The explicit developer-fixture diagnostic contract validates identity and owner
metrics while carrying no Task 41 plan. The generated-instance overload accepts
it only with developer-only mode; normal diagnostic admission rejects it. The
normal contract remains distinct. This is a fixture envelope, not a new
admitted player family.

`tsjDebug=true&tsjVerifyTask47=true` runs the compiled assembler's finite proof and
restores the original owner. It publishes a bounded report and terminates errors
with an explicit FAIL marker. A separate literal physical manifest joins raw
board/copper, rendered terminals and actual solver endpoints through the existing
correspondence verifier, including its wrong-mapping negatives. The route adds no
player menu or composed challenge admission. Current qualification and limits are
recorded in the [Task 47 packet](task-evidence/task-47/README.md); Task 48 is separate.

## Repeated controlled-indicator composition - A05

`BoundedAssemblyRequest.forControlledIndicator` uses current
`bounded-assembler@5`, the `controlled-indicator@1` intent and geometry 3.
`LowSideRoleFamily` admits compatible implementations, sorts registration IDs,
and selects independently for `channel-a-driver` and `channel-b-driver` through
instance-scoped TOPOLOGY streams. The bounded device has five contributions:
two drivers, two resolved resistor/LED loads and a supply-present indicator.
Device adapters provide J1 board supply and J2/J3 independent control inputs.
They share one board, one physical inventory and one active CircuitJS graph.

The role supports a 4.75-5.25 V supply, active-high control of at least 4.75 V,
LOW at most 0.1 V, a 2 mA control capacity and a 20 mA sinking capacity for a
5-16 mA indicator load. These declarations bound admission of ideal voltage
sources; they do not implement dynamic current limiting. High-side operation
and insufficient base-drive proposals reject before construction.

The NMOS provider uses an actual `NMosfetElm`, 1 kohm RG, 100 kohm gate pulldown,
threshold 1.5 V and beta 10. The NPN provider uses an actual `NTransistorElm`,
2.7 kohm RB, 100 kohm base pulldown and beta 100. NPN drive admission accounts
for worst-case base current and pulldown current. Provider observations check
actual gate versus base behavior, ON current, OFF leakage and the NPN voltage
and current envelope. NMOS G/D/S maps to CircuitJS posts 0/2/1; NPN B/C/E maps
to 0/1/2 through explicit package-terminal declarations.

`ElectricalConstructionProvider.declare` owns the local immutable element,
helper, model parameter, package, terminal and board-endpoint description.
The matching provider constructs from that declaration. Explicit electrical,
physical and observation registrations are the bounded extension points.
`ElectricalRealizationSpec` validates known primitive post maps, model/value
requirements and complete package/pad coverage. The device adapter iterates
declared inter-instance joins and external inputs. The generic materializer
checks exact kind and provenance; an unknown kind cannot become a switch.
Removable resistor pads bind to persistent local copper separately from their
detachable attachment wires. Declarations reject a contact backed by its own
attachment or removable component before allocation; runtime validation also
checks the actual separate endpoints and continued backing ownership.
`A03RealizationReplay` captures provider declarations, numeric/model choices,
units, backings, bridge endpoints and actual physical geometry without separate
NMOS/NPN capture methods. Generator 4 artifacts reject before installation.

Each load resolves one immutable catalog recipe from its own VALUES stream.
`ControlledIndicatorValueSynthesis` consumes the actual request's supply,
control, load and sinking relationship. The catalog permits 270 ohms/0.25 W or
330 ohms/0.22 W at 5% tolerance in the current axial package. Worst-case current
must remain within 5-16 mA, sink capacity has 1.25x headroom, and a 2x power
guard assigns the full maximum supply voltage to the minimum resistance.
The LED model envelope is 1.6-2.0 V. Electrical allocation, physical nameplates,
reconstruction and behavior consume the same resolved values. Catalog
replacement remains available for wrong and electrically valid alternatives.

The support provider contains a fixed 1 kohm resistor and a real LED between
board supply and return. Its powered expectation is 2-4 mA and its unpowered
expectation is at most 1 microamp. It has no selected fault or repair target.
Healthy verification and customer retest include support current and LED
current during all-off, all-on and each independently commanded channel state.
A disconnected support branch fails the device contract.

Named FAULT selection occurs after the healthy composition. The four admitted
owners are each channel's RG or RB and each load's RLOAD, all with a real OPEN
fault backing. Each has its own mutable resistor capability and isolation pair.
RPD, Q1, LEDs, support and connectors remain fixed parts. Provider observations
and `GeneratedDiagnosticExecutionProvider` let the maintained Task41 proof
execute every admitted candidate, ordinary measurements, physical replacement
and retest without adding variant dispatch to admission. The aggregate retest
checks customer behavior and installed part ratings rather than the original
fault answer. Other channels and support must continue operating.

The ordinary `tsjChallenge=controlled-indicator&seed=<canonical-long>` URL and
**Open controlled indicator** button use `installNormalComposition`. Admission
settles a fresh disjoint candidate and completes the real diagnostic contract
before publishing the owner to the workbench. Failure preserves the prior
owner. Shared player controls expose HIGH/LOW for both channels, measurements,
resistor service and customer retest. Power isolation disconnects all three
external inputs; existing stale-work, measurement, reset and replacement
ownership guards remain in force. Labels contain physical markings, while
provider/fault identities remain developer information.

The package-backed layout supports this bounded two-channel population through
the existing board, copper, renderer and probe pipeline, using the current
default axial `SPAN_220` geometry in a repeated placement pattern. It is not a general
router, large-board, multi-layer or arbitrary multi-unit implementation.
Current native, compiled, player and independent-review evidence is recorded
in the [A05 packet](task-evidence/A05/README.md). The retained Task48/49 class
names and historical packets do not define a second supported generator.

## Reference manifests and measurement harness — A01

`A01MeasurementVerifier` is an opt-in developer route owned by `CirSim`. It
installs a bounded 20/40/60/100-resistor ladder into the live CircuitJS graph,
records the existing analysis/stamp/factorization/solve/iteration counters,
checks the solved current and node voltages against an independent Ohm-law/KVL
oracle, and restores the prior simulation owner in every exit path. The route
does not participate in normal challenge admission or player UI, and its
synthetic ladder explicitly reports PCB/routing/diagnostic/playable stages as
unsupported.

`tests/benchmarks/a01-reference-boards.json` contains the architecture-only
RB15/RB30/RB56/RB100 functional inventories. `scripts/collect-a01.mjs` reads the
versioned developer DOM receipt through the existing Browser API and records
viewport/DPR, timing summaries, memory availability and cleanup state. Preview
responses stamp the source, script, served-web and execution-tree digests into
the loaded document; `A01MeasurementVerifier` publishes those values in its
report, and the collector retains them as executed-artifact evidence. A wrapper
exception is retained as nonpassing infrastructure evidence with its terminal,
report, cleanup and error fields intact.
`scripts/a01.py` independently validates manifest allocations, electrical
vectors, cold/warm identity and work counters, frozen budgets, finite timing
totals and traces, receipt identity against both the current source/build and
the served artifact, and explicit negative outcomes. The compact
[A01 evidence packet](task-evidence/A01/README.md) records the accepted pilot,
holdout, forced-failure and debug-off boundaries; it does not qualify routed
PCB or playable large-board content.

## Probe and measurement ownership

`ProbeTarget` describes where a user placed a probe: validity, semantic target
identity, marker position, and resolution to a `CircuitMeasurementEndpoint`.
It does not perform a measurement. `CircuitPostProbeTarget` identifies a
physical target by its owning simulation, live element, and post index rather
than wrapper allocation identity. This lets hit-testing return a new target
wrapper without invalidating an unchanged probe, and leaves PCB pad targets
free to define their own identity later. `CircuitMeasurementEndpoint`
represents a resolvable electrical endpoint, while `CircuitMeasurementAdapter`
owns measurement behavior and the translation to CircuitJS.

Board electrical power is represented independently from CircuitJS simulation
execution. Generated-board power is enforced through explicit external-power
controls, not by changing arbitrary CircuitJS voltage sources. Power OFF means
electrically isolating the external supply connection; it is not equivalent to
setting a voltage source to zero. `BoardPowerController` only reports a board
as electrically unpowered when an attached generated-board control actually
enforces the open state. External isolation infrastructure belongs to the
simulation backing, not to the logical PCB component model. Future active meter
modes require this electrically enforced UNPOWERED state.

Active measurements run through an `ActiveMeasurementSession`. The adapter
validates probe targets and board-power requirements, then closes the session
through `finally`. Resistance measurement uses a temporary $1 V$ DC source in
series with a $1 kOhm$ internal resistor. CircuitJS solves the resulting
current, and the meter derives $|V/I| - 1 kOhm$ from that solve. The temporary
elements are not board metadata, export content, or undo/redo history; they are
removed and the original graph is synchronously reanalyzed and solved before
the transaction completes. Removal, reconstruction, queued power, and final
time synchronization have separate exception handling. The original failure
propagates with later cleanup failures retained as suppressed exceptions.
Queued power is applied only after the temporary elements are absent from the
element list, voltage-source index, and node links, with a valid restored
solver. If restoration fails, the real external inputs are disconnected,
solving stops, the latest power request remains pending, and the visible power
control requires a board reload. The temporary intermediate point is
allocated deterministically from occupied CircuitJS posts, so it cannot connect
to either probe or an existing circuit endpoint. A temporary $1 TOhm$ resistor
connects the black probe node to a separate remote ground point, giving the
solver a numerical reference without directly grounding either DUT terminal.
The reference is removed with the other temporary elements. Developer
verification records its solver nodes and current: it requires the remote ground
node to differ from the black-probe node, limits reference current to $0.1\%$ of
a measurable test current, and limits open-circuit reference leakage to $1 nA$.

Resistance results are demand-driven rather than calculated from a draw pass.
They are invalidated whenever CircuitJS requests reanalysis, when either probe
changes, when OHM mode is entered, or when board power changes. This makes board
replacement and future topology mutations such as removals, replacements,
lifted leads, jumpers, and trace changes invalidate the cached result through
the shared reanalysis path. After normal graph analysis completes,
`InstrumentController` consumes one pending resistance refresh only when OHM
mode still has two valid probes. Internal measurement cleanup does not enqueue
another refresh, preventing an analyze/measure loop. Invalid probes are cleared
as part of invalidation and show the mode-specific OHM placeholder. A power
request during an active measurement is queued until the temporary overlay is
removed and the normal solver state is restored.

DC voltage uses a temporary passive `$10 MOhm$` `ResistorElm` between the red
and black probe endpoints. `InstrumentController` requests one DC refresh for a
probe, topology, part-location, or power change; `CirSim` installs the resistor,
solves the actual circuit, samples `$V_{red} - V_{black}$`, and synchronously
restores the canonical graph. This lets a floating lifted lead acquire its real
meter-loaded voltage without deriving anything from board metadata or expected
topology. The input resistor is never board metadata, export content, or undo
history. Active overlays suppress their own simulation-step callbacks, so a DC
refresh cannot recursively reinsert a meter during its transaction. The pending
refresh remains owned by `InstrumentController.updateReading()` until that method
starts the actual measurement transaction; a post-analysis callback requests the
update without consuming it first. Retained probes therefore get exactly one
fresh loaded solve after a topology or power analysis, while ordinary repaints do
not create recurring DC transactions.

Component-side detachable bindings continue to resolve to the physical part
currently installed in a slot. `ResistorSlotController` retargets those
measurement endpoints together with the attachment wires during installation.
A `ComponentLeadProbeTarget`, however, captures the installed physical-part ID
and component-side endpoint at hit-test time. It remains valid only while that
same part is installed and the lead remains physically exposed, and target
identity includes the physical-part ID. Removing or replacing the part therefore
clears the old probe instead of silently migrating it to another resistor in the
same R1 slot. The component-side endpoint supplied by the current connection
binding is authoritative; target validation checks that endpoint's membership in
the installed physical part rather than inferring polarity from board-pad or
terminal-name equality. Board-side pad endpoints remain fixed and physically
distinct, including for reversed polarized LED and diode installations.

Continuity is a policy over the same simulated resistance transaction, not a
separate connectivity shortcut or stimulus. CONT uses the temporary $1 V$ /
$1 kOhm$ CircuitJS resistance measurement, shares its power-off gate and final
state validation, and reports continuity only when the finite solved resistance
is at most $50 Ohm$. A `ContinuityFeedback` abstraction owns browser-audio
resources; the controller owns only the requested continuity state and visible
`BEEP` indicator. Browser audio is prepared only by meter/probe user gestures,
is idempotent, and may fail silently under autoplay restrictions. The visible
indicator remains authoritative. Topology, probe, mode, board, and power
invalidation clear continuity feedback immediately; normal post-analysis
refreshes recompute it once without draw-time stimulation.

Active stimuli share a narrow `ActiveMeasurementStimulus` transaction in
`CirSim`. The transaction owns temporary-element installation, overlay state,
analysis/solve, result sampling, `finally` removal, normal graph restoration,
queued power application, generated-board verification resumption, and solver
cleanliness checks. This keeps resistance and diode electrical behavior
separate while preserving one cleanup and final-state path.

## Unlimited Resistor Catalog

`ResistorReplacementCatalog` contains immutable, non-physical E12 resistor
specifications. Its 73 ordered `+/-5%` entries span $10 Ohm$ through $10 MOhm$;
catalog entries own no CircuitJS element, physical ID, location, or probe
target. Catalog selection alone cannot mutate the electrical graph or appear in
the physical tray.

`PhysicalResistorPart` remains the electrical and measurement identity. Only
the faulted `R1_ORIGINAL` exists at generated-board initialization. A successful
`installNewFromCatalog` allocates a monotonically numbered physical ID, distinct
`ResistorElm`, independent terminals, and a new nameplate. The fresh element is
allocated from unoccupied simulation post coordinates, registered once in the
generated instance's canonical ownership vector, then inserted once into the
active graph before the R1 attachment bindings are retargeted. Rejected catalog
installations happen before allocation and therefore leave serials, inventory,
the graph, and slot state unchanged.

The Replacement Catalog UI creates new physical parts while the Parts Tray UI
only presents loose parts that have been removed from R1. Installed parts are
not tray targets. The tray pages loose parts three at a time; drawing,
hit-testing, selection, and loose-lead geometry share the same current-page
slice. Changing a page clears an invisible part selection. After 43R-4C, a
loose probe target captures the renderer's projection epoch: an off-page target
is invalidated and cleared from the instrument, while its stable physical and
electrical endpoint identity remains unchanged. Returning to the page requires
a fresh target object and never revives the old target.

R1 removal remains an electrical mutation transaction: both detachable
connection bindings are removed while board power is electrically isolated,
then the installed part is marked loose and the slot clears. A fresh-browser
native event trace confirmed one `Remove component` click targets only its
handler, removes both R1 bindings, and creates one loose original part; the
earlier lead-lift observation came from a stale post-initialization browser
interaction context rather than a partial removal transaction.

Diode test is an independent active measurement rather than a continuity or
resistance policy. Its temporary CircuitJS overlay is a $3 V$ finite-compliance
source and $1 kOhm$ series resistance, limiting a short to approximately
$3 mA$. The source is oriented so the red probe is electrically positive
relative to the black probe. After CircuitJS solves the unpowered graph, the
meter samples $V_{red} - V_{black}$ and source current. It displays a finite
voltage only for finite results with at least $10 uA$ current and a diode
voltage below the $2.95 V$ compliance threshold; otherwise it displays `OL`.
Direct shorts remain valid approximately-$0 V$ results. DIODE shares the same
power gate, invalidation, post-analysis one-shot refresh, and queued-power
cleanup as other active modes, but never prepares or enables continuity audio.

`TroubleshootBoard` owns stable board identity. `BoardComponent`, `BoardPad`,
and `BoardNet` use durable string IDs; a `BoardNet` is the TroubleshootJS
electrical identity, not a CircuitJS analyzed node number. `BoardPad` connects
physical component/pad identity to a board net. Generated-board metadata
declares its external power inputs explicitly.

`GeneratedBoardInstance` also owns immutable physical/nameplate specifications
for the generated board. A `ResistorNameplate` records a component's nominal
resistance and tolerance, and a `PowerInputNameplate` records a nominal input
voltage. Generators derive these values from the same selection used to build
the initial CircuitJS elements. Renderers and contextual panels read only this
metadata for printed labels, values, and resistor color bands; they never
inspect a live CircuitJS element to infer a marking. The nameplate is therefore
stable when a user lifts/removes a component or a future fault changes effective
simulation behavior, while CircuitJS remains the source of truth for actual
electrical behavior and measurements.

Player-facing identification deliberately does not expose all nameplate
metadata. Original resistor panels show the physical type, installed/removed
state, and `Markings: Color bands` rather than nominal resistance; removed
original parts retain an identity and the same rendered bands without a
numeric label. The replacement catalog and a known installed catalog part may
show their catalog value because that is an intentional selection, while
developer verifiers may inspect exact original metadata. Ordinary contextual
text, attributes, and generated complaint descriptions follow the same privacy
boundary.

Resistor-band rendering follows the stable component ID from each
`PcbComponentPlacement` (including a tray component) to its
`ResistorNameplate`, then through `ResistorColorCode` semantic tokens to local
renderer CSS colors. Four-band encoding supports only finite, positive,
integral, exactly representable `+/-5%` values; it rejects values that would
otherwise be rounded or truncated to a different printed resistance.

PCB geometry is a separate rendering layer. `PcbBoardLayout` contains the board
outline, component placements, pad placements, traces, and parts-tray geometry,
and references only stable `BoardComponent`, `BoardPad`, and `BoardNet` IDs.
It does not contain CircuitJS elements or analyzed node numbers. The
`SeededPcbLayoutGenerator` consumes that logical board after electrical
validation and produces a simple one-sided layout for the LED indicator,
diode-protected indicator, and dual-parallel-indicator families. It does not
choose components, nets, faults, meter readings, or repair outcomes.

The generator uses a deterministic seed stream. Each attempt starts in a
bounded virtual working area, builds a `TopologyPlacementGraph` from stable
`BoardComponent`, `BoardPad`, and `BoardNet` relationships, and places connected
pad targets before routing. Two-pad nets receive a stronger attraction than
shared rails; connector links remain useful anchors but do not overwhelm
component-to-component functional links. A bounded set of grid candidates is
scored for topology distance, component spacing, seeded variation, and fit.
Stable IDs such as `R1.1`, `LED1.K`, and `D1.A` are copied into the resulting
placements regardless of their coordinates. The connector remains an inward-
escaping board-edge anchor; component candidates are accepted only when their
practical routing courtyards do not overlap. Orientation is deliberately
deferred, so this first procedural layer keeps the existing recognizable
horizontal component presentation.

`PcbComponentPlacement` now keeps both a body keep-out and a larger routing
courtyard. The courtyard covers the mounted body, lead span, pad neighborhood,
and readability margin. The A* router and `PcbBoardLayout` validator block all
copper from that courtyard except the exact endpoint pad's explicit escape
corridor. This is intentionally stricter than universal through-hole PCB
manufacturing practice: TroubleshootJS keeps unrelated copper visible and
probeable so the player can understand the topology instead of losing a trace
under a component. Same-net copper may still merge, but a same-net branch that
does not terminate on a component cannot use that component's courtyard as a
shortcut.

Task 26 hardens this stage with a deterministic coarse-grid A* Manhattan
router. It connects only the already-defined pad relationships, applies a
small bend penalty, blocks component body keep-outs, and records explicit
start/end pad IDs on every trace. Pads carry narrow escape corridors: the
router and `PcbBoardLayout.validateGeometry` use the same corridor semantics,
so copper may leave an exact pad through its legal lead direction while the
component body remains forbidden. The LED pads escape downward from the body;
axial pads escape horizontally, and connector pads escape inward from the
edge.

Copper uses the shared `PcbTraceRules` contract: rendered traces are 9 pixels
wide, unrelated nets require 6 pixels of visible soldermask, and their
centerlines therefore must be at least 15 pixels apart. The router inflates
existing copper occupancy by one coarse grid cell in every direction, blocking
different-net adjacent or diagonal cells during pathfinding. Same-net copper
may intentionally re-use its own clearance cells, and its A* step cost is
lowered when it joins an existing same-net trunk. Validation independently
measures every unrelated horizontal, vertical, corner, and perpendicular
segment pair against the 15-pixel minimum, so the rule is not inferred from
the drawing alone.

Each candidate is validated for component/pad coverage, stable net and
endpoint identity, board bounds, body keep-outs, routing courtyards, legal
escape edges, body/pad overlap, Manhattan segments, route detour/bend limits,
silkscreen collisions, unrelated crossings, and minimum copper clearance.
After routing and silkscreen placement, `PcbBoardLayout.compactToContent` finds
the bounding rectangle of courtyards, pads, copper, and labels, translates the
geometry consistently, and derives the final outline with a reusable 26-pixel
edge margin. The parts tray is excluded from this calculation. Candidate
quality includes routed length, bends, detour, connected-pad distance,
component spacing, board area, unused area, courtyard utilization, silkscreen
fit, and same-net reuse. `getCompactnessMetric()` is an explicit procedural
quality signal; the verifier rejects obviously sparse boards without pretending
to be an IPC design-rule check.

Placement and routing are retried with a bounded deterministic attempt derived
from the same seed. Seeds choose among good topology-aware alternatives rather
than deciding whether a layout is valid. A failed candidate is never replaced
with disconnected decorative copper.
`PcbLayoutDeveloperVerifier`
proves repeated seeds 0, 2, and 3 have identical fingerprints and that each
pair has at least two meaningful differences across outline, component
placement, and routed copper. It also directly regresses the seed-3 LED
cathode endpoint case that previously entered the LED body. It additionally
checks courtyard coverage for resistor and diode footprints, compactness and
edge margins, the parallel multi-pad nets, and the absence of copper
intersections with courtyards except legal endpoint escapes.

Silkscreen reference and connector-net labels are generated as collision-aware
layout objects rather than fixed renderer pixels. `J1.1` and `J1.2` retain
stable pad identity while rendering a positive/negative connector cue and
seed-dependent voltage text. The parts tray remains a separate workbench
rectangle outside the board outline and all board components, pads, traces, and
silkscreen. `PcbBoardLayout.validateGeometry` rejects board/tray intersection,
and `positionPartsTrayDisjointFromBoard` provides the shared placement invariant
used by fixed and procedural layouts. The browser verifier's geometry bridge
is read-only and only enabled by an explicit developer query; normal input is
still dispatched through real CDP mouse events. Its CDP receive window is
bounded independently from the route timeout so a slow diagnostic response
cannot masquerade as a routing algorithm.

`BoardSimulationBindings` maps stable pad IDs to resolvable
`CircuitMeasurementEndpoint` instances. CircuitJS element/post references are
valid current schematic bindings, and resolve dynamically after reanalysis.
Current schematic probes continue to use `CircuitPostProbeTarget`.
`BoardPadProbeTarget` identifies the active generated-board instance and stable
pad ID, resolves electrically through `BoardSimulationBindings`, and asks the
current PCB renderer for marker geometry. `ComponentLeadProbeTarget` also keeps
the generated-board, component, and pad IDs, but captures the exposed physical
part and its declared component-side endpoint. It does not follow later slot
retargeting. Neither target persists analyzed node numbers. Both PCB and
schematic hit testers feed the same generic probe-selection path in
`InstrumentController` and converge through `CircuitMeasurementAdapter`.

`GeneratedComponentConnectionBindings` adds the mutable physical-workbench
boundary without changing a `BoardPad` or `BoardNet` identity. Each detachable
lead records a persistent board-side endpoint, a distinct component-side
endpoint, and one owned CircuitJS connection element. The board side remains
probeable when the lead is lifted or the component is removed; the component
side remains available for out-of-circuit measurement. Bindings reject shared
connection elements and endpoints not owned by the generated graph.

`BoardModificationController` is the sole graph-mutation owner. It can lift or
reconnect one declared lead, remove every declared lead for a component, or
restore them. It mutates only the declared connection elements, is idempotent,
requires the exact installed generated board with no active meter overlay, and
requires actual electrical power isolation. A mutation requests normal CircuitJS
analysis, so active instruments invalidate and refresh through their existing
path. Generated verification always checks graph/connection structural state;
it runs family healthy-behavior checks only once every detachable lead is
restored.

Expected user-safety rejections use `BoardModificationRejectedException`.
`PcbWorkbenchController` turns only that typed rejection into the inline
power-off guidance; structural and other unexpected mutation errors are not
misrepresented as user-safety failures.

Component physical state is derived explicitly from all declared lead
connections: `INSTALLED`, `LEAD_LIFTED`, or `REMOVED`. Reconnection inserts a
detachable element according to the canonical generated-element vector rather
than appending it, while structural verification counts occurrences and checks
relative order. Connection validation requires a detachable element to bridge
distinct persistent-board and component-owned endpoints, rejects removable
board-side endpoints, shared pads/elements, and external-power infrastructure.

`PcbWorkbenchController` owns view-specific hit testing, component selection,
and a generic contextual action panel. Actions are derived from declared
connection bindings and are disabled while board power is on. Normal rejection
uses inline feedback and never mutates the graph. `PcbWorkbenchRenderer`
renders logical state without modifying copper geometry: pads and traces remain
fixed, a lifted lead gains a visible air gap and component-side target, and a
removed component moves to the parts tray with probeable leads. CircuitJS keeps
running and solving behind this view. Generated boards use the PCB workbench by
default; `?tsjDebug=true` and all legacy circuits retain the schematic renderer
and upstream controls.

Generators are seeded so a seed reproduces a board's topology, values, and
simulation placement. `GeneratedBoardInstance` is family-agnostic: it couples
board metadata, owned CircuitJS elements, stable family/topology IDs, generic
component bindings, external-power bindings, and its
`GeneratedChallengeBehaviorContract` without exposing a topology-specific API.
The contract represents the healthy, faulted, and functionally repaired phases;
`GeneratedChallengeBehaviorAdapter` delegates those phases to the existing
family-specific `GeneratedBoardValidator`, `GeneratedFaultValidator`, and
`GeneratedRepairValidator` implementations. Component bindings map logical
`BoardComponent` IDs to one or more live `CircuitElm` references. External
power bindings map logical `ExternalBoardPowerInput` IDs to one or more backing
elements, leaving room for a future source-isolation control without assuming a
`VoltageElm`. Generated pad bindings use CircuitElm/post endpoints, never
analyzed node IDs. Family-specific electrical expectations therefore remain in
their validators while the generic board verifier consumes the shared contract.

Generated circuits are installed only through the controlled `CirSim`
installation boundary. Generated-board verification is requested after
installation or reanalysis and runs once only after CircuitJS has analyzed the
circuit and simulation time has advanced; paused simulation leaves the request
pending rather than treating unsolved current values as a failure.

URL-gated meter lifecycle verification also waits for a generated challenge to
reach `READY`. `?tsjVerifyMeter=true` exercises the normal PCB hit-testing and
left/right probe path. On challenge routes, `?tsjVerifyResistance=true`
delegates to the same physical-board lifecycle checks. Resistance verification
has explicit not-started, running, passed, and failed terminal states, so a
deterministic pass or failure is not retried by later generated-board
verification cycles. The lifecycle verifier covers both lifted-lead directions,
component-lead versus board-pad isolation, retained DC readings and power
transitions, one-shot refresh counts, repaint stability, physical-part target
invalidation, and final solver restoration.

The first faulted challenge is selected with `?tsjChallenge=led&seed=<seed>`;
the corresponding `?tsjFixture=led&seed=<seed>` route remains a healthy
fixture. `GeneratedChallengeController` advances only through solver-gated
verification: `PREPARING_HEALTHY` validates the closed, powered generated
board; it then applies its selected fault, requests another CircuitJS analysis
and time advance, validates the resulting symptom in `PREPARING_FAULTED`, and
only then enters `READY`. This is not a UI timer or an assumed settle delay.

`GeneratedFault` is immutable challenge metadata (stable ID, type, target
component, circuit family, selection seed, and—when applicable—healthy and
effective values). `GeneratedFaultEngine` builds explicit compatible candidate
bindings instead of leaving fault setup inside a family-specific validator. Its
CircuitJS-backed effects model a series open path, an incorrect resistor value,
or a parallel diode short; connector/open-path effects are represented and
filtered out when the current family has no compatible repair primitive. Each
generator clears every candidate, selects deterministically from compatible
candidates using the challenge seed, retains every candidate's private
simulation element in the canonical generated-element ownership, and applies
only the selected effect. LED and parallel families currently select seeded
resistor-open or resistor-incorrect-value challenges. Normal diode challenges
deterministically reject the diode-short candidate and select diode-open;
`generateForDeveloperVerification` plus `tsjDiodeShort=true` intentionally
retains the solver-backed short route for developer verification.

`GeneratedFaultBinding` owns the private CircuitJS implementation, while
`GeneratedFaultController` is the only code permitted to apply or
developer-clear it. Private switches are not `BoardComponent`s, PCB pads,
placements, nameplates, or external-power controls. A declared binding may be
the component-side endpoint of the target's detachable lead, preserving two
valid outer terminals for in- and out-of-circuit measurement without giving
internal fault infrastructure a physical board identity. Incorrect-value
effects mutate the real `ResistorElm` value and restore the healthy value when
cleared; short/open effects alter the real solved graph.

Faulted verification keeps generic ownership, topology, pad, net, and
physical-modification checks active, but does not apply a healthy-current
family assertion while the intentional fault is active. Family fault
validators now verify the selected meaningful symptom for open, incorrect,
and short effects, while repair completion remains the shared solver-backed
functional predicate. Fault application never changes printed resistor
markings, physical part identity, stable IDs, external power bindings,
undo/redo state, or board geometry.

`GeneratedComponentOperationalStates` is a small runtime adapter from stable
component ID to solved LED current. The PCB renderer uses it only to add a
visible illumination halo to the existing LED body; the red plastic package
and all printed/nameplate state remain unchanged.

Task 32 adds the immutable generic `GeneratedScenario<T>` representation and
`GeneratedScenarioCatalog<T>`. A scenario owns stable scenario and complaint
IDs, the player-facing complaint text, an internal `GeneratedObservedBehavior`
semantic, and a compatibility predicate. The predicate reads the live solved
CircuitJS elements; it does not map a `GeneratedFaultType` to a string and does
not create a second simulator. Family generators contribute scenario catalogs
through `GeneratedScenarioLibrary`, while `GeneratedChallengeDefinition` owns
the catalog rather than loose complaint strings. `GeneratedChallengeController`
selects a compatible scenario only after healthy validation, fault injection,
and solver-backed faulted validation. A failed compatibility selection leaves
the controller out of `READY`, so the normal service ticket cannot race ahead
of electrical truth. The parallel scenario describes unequal branch behavior;
diode-open describes a dark indicator; the developer-only diode-short scenario
describes the solved higher-current/bright behavior and is not part of normal
player generation. Scenario selection uses an independent stable seed stream
and therefore cannot perturb topology, values, or PCB layout.

Normal players see only the selected validated service-ticket complaint. Fault
identity, target IDs, values, observed semantics, and expected measurements
remain outside the player UI. Developer-only `tsjVerifyChallenge=true`,
`tsjVerifyParallel=true`, and `tsjVerifyDiode=true` verify the lifecycle and
scenario/solver agreement; `tsjDiodeShort=true` enables the explicit diode
short route.

All active meter modes use the same physical polarity convention: the red
probe is the electrically positive test terminal and the black probe is the
electrically negative terminal. OHM and CONT share a $1 V$ / $1 kOhm$
CircuitJS stimulus wired with the same source orientation as the diode test;
the resistance formula still uses current magnitude only when deriving a
magnitude. DIODE is a distinct $3 V$ finite-compliance meter function, so a
forward-biased diode may yield a high finite OHM/CONT resistance without
meeting the $50 Ohm$ continuity threshold.

Generated challenge orchestration is family-agnostic. Family generation owns a
small scenario catalog and an immutable `GeneratedChallengeDefinition`.
A definition contains stable challenge identity, family/topology compatibility,
selection seed, selected fault/binding, the scenario catalog, and its
`GeneratedChallengeBehaviorContract`; the selected scenario is runtime state on
the challenge controller until solver-gated faulted validation completes.
`GeneratedBoardInstance` owns that contract and the definition references the
same object identity. `GeneratedChallengeController` uses the shared contract
for faulted validation and functional completion, while
`GeneratedBoardVerifier` uses it for healthy validation. The generic
controller records the solver-gated healthy/faulted stages, applies the
selected binding, invokes the contract, selects the compatible scenario, and
only then transitions to READY. It validates that private fault infrastructure
is simulation-owned but not a logical component, external-power element, or
detachable connection. Value-mutating effects also expose their real
`ResistorElm` target so ownership validation rejects a fault that claims one
component while mutating another even when its private-element list is empty.

During either preparation stage, `CirSim` disables board power, the meter,
PCB probe placement, component selection, and component actions at their
actual event/mutation boundaries. READY restores normal interaction; healthy
fixtures and legacy circuits remain ungated. After READY, generic board checks
continue and the selected fault must remain applied. With powered, restored
target hardware the selected strategy rechecks the symptom; unpowered or
intentionally isolated hardware is allowed while fault binding integrity still
holds. Developer-only clear/reapply uses an explicit scoped exception.

Replaceable hardware distinguishes the stable logical board slot from the
physical part. `ReplaceableComponentSlot` owns the fixed R1 designator,
intended immutable board specification, pads/nets, and current occupancy;
`PhysicalResistorPart` owns a stable part-instance ID, immutable nameplate,
complete CircuitJS backing, two public terminals, optional internal fault binding,
and loose/installed location. A fault switch is inside the failed original's
public two-terminal path, so the removed physical part measures electrically open
instead of merely carrying fault metadata. `PhysicalPartInventory<PhysicalResistorPart>`
is a typed view over the runtime-owned inventory and retains the original
failed resistor alongside healthy replacement choices. Changing a
part never mutates a nameplate or closes the original fault switch.

`ResistorSlotController` is the only owner of R1 install/remove graph changes.
It requires genuine board power isolation, no meter overlay, and a ready
challenge. Each part keeps its fixed, unique hidden solver coordinates while the
two detachable R1 attachment wires are retargeted to the installed part's public
terminals. Removing the wires isolates the complete backing without removing it
from the active solver, so loose parts are individually probeable and cannot be
reconnected by a later install. The PCB layout, pads, traces, and R1 designator stay
stable while the renderer displays the installed physical part's bands.

Family-specific `GeneratedRepairValidator` implementations, reached through
the shared `GeneratedChallengeBehaviorContract`, decide functional completion
from solved behavior. The LED validator requires healthy installed
physical R1 and LED1 parts, powered board, no active meter overlay, 5-15 mA LED
current, matching resistor current, and illuminated operational state. A missing
or reversed LED therefore cannot complete an otherwise repaired R1 challenge.
Incorrect but electrically valid replacements simply remain READY. `COMPLETED` is latched
after a solver-backed successful repair; later board changes still affect the
electrical simulation honestly but do not retract the first verified repair.

The package-visible PCB tray-slice accessor is verifier-only integration access.
It exposes the same paginated loose-part slice used by rendering and hit testing,
so developer verification can compare physical marker geometry and page state
without embedding testing behavior in normal drawing.

`GeneratedBoardInstance` is family-agnostic. Mutable repair and catalog state is
held by its optional `GeneratedBoardFamilyState`; the LED family provides
`LedIndicatorFamilyState` for the R1 and LED1 slots, typed views over the shared
runtime inventories, non-depleting catalogs, and family-specific electrical
allocation. Generic runtime simulation-element ownership remains on the
instance. Future component families must add their own family state rather than
component-specific instance fields or getters.

`PhysicalLedPart` is intentionally distinct from `PhysicalDiodePart`. It owns a
stable acquired identity, immutable LED nameplate, one actual CircuitJS
`LEDElm`, distinct anode/cathode measurement endpoints, installed/loose state,
and installation orientation. LED catalog rows are specifications only and do
not own solver elements or become probe targets. Acquisition allocates a new
physical ID and collision-free hidden backing element without depleting the
catalog. `LedSlotController` retargets the existing LED1 attachment wires and
component/operational bindings to the installed part. Correct orientation maps
anode to `LED1.A` and cathode to `LED1.K`; reversed installation swaps the real
backing terminals. Forward diode readings, reverse blocking, illumination, and
repair completion remain consequences of the `LEDElm` and CircuitJS solver, not
renderer or meter overrides. Installed and loose LED drawings retain cathode
polarity cues, while loose parts use the existing paginated tray architecture.

The second generated family is `DIODE_PROTECTED_INDICATOR`, with the series
topology VIN -> D1 -> R1 -> LED1 -> GND. `DiodeProtectedIndicatorFamilyState`
owns the D1 slot, its typed view over the runtime inventory, non-depleting
catalog, and physical-part serial allocator. None of those diode concepts are
fields or getters on
`GeneratedBoardInstance`. The only family-state behavior used by generic
challenge orchestration is a target-agnostic predicate that reports whether the
faulted target remains installed.

`DiodeNameplate` is immutable catalog/physical specification metadata for the
generic silicon model. `PhysicalDiodePart` separately owns acquired identity,
its `DiodeElm`, public anode/cathode endpoints, location, installation orientation,
and optional internal fault binding. Repeated catalog acquisition allocates new
hidden backing coordinates and physical IDs; catalog rows never become probe
targets or deplete. The correct and reversed installation choices use the same
electrical diode specification, with orientation changing which public terminal
the fixed `D1.A` and `D1.K` board pads reach.

The original D1 carries a private series isolation switch inside its public
two-terminal path. Applying `D1 OPEN` therefore stops solved branch current and
keeps the LED dark both installed and removed, while diode mode measures the
physical original as OL in either direction. A healthy `DiodeElm` replacement
uses CircuitJS's built-in default silicon model and nonlinear solver. Its forward
drop, reverse blocking, branch current, LED operation, and challenge completion
all come from the solved graph; neither the workbench renderer nor the instrument
controller contains a D1 reading override.

The PCB renders D1 as a separate axial through-hole black body with metal leads,
a contrasting cathode band, a D1 reference, and a K marking aligned to the actual
installed polarity. Removed diodes retain probeable anode/cathode identity in the
parts tray. Diode leads use the same detachable-connection boundary as resistor
leads, so either D1 lead can be lifted, measured in isolation, and reconnected
without changing stable pad or net identity.

## Task 27 parallel dual-indicator family

`PARALLEL_DUAL_INDICATOR` is the first player-facing family with two real
parallel CircuitJS branches:

```text
VIN -> R1 -> LED1 -> GND
VIN -> R2 -> LED2 -> GND
```

Its stable logical board has four nets (`VIN`, `BRANCH1_NODE`,
`BRANCH2_NODE`, `GND`) and five components (`J1`, `R1`, `LED1`, `R2`,
`LED2`). `VIN` intentionally has pads `J1.1`, `R1.1`, and `R2.1`; `GND`
intentionally has `J1.2`, `LED1.K`, and `LED2.K`. Those stable pad/net IDs
remain the PCB identity. The verifier may inspect transient CircuitJS node
numbers after analysis to prove the three-pad networks are joined, but those
numbers are never stored as board identity.

Seeds 0, 2, and 3 select the validated 5 V / 330 Ohm + 680 Ohm, 9 V / 680
Ohm + 1.5 kOhm, and 12 V / 1 kOhm + 2.2 kOhm configurations. The generated
CircuitJS source, resistors, and LEDs supply all solved currents, voltages,
illumination, and meter readings. The initial `PARALLEL_R1_OPEN` fault is a
real series isolation switch in branch 1, so LED1 goes dark while the ideal
source leaves branch 2 operating.

`ParallelDualIndicatorGeneratedBoardValidator` checks healthy branch currents,
branch current equality through each resistor/LED pair, branch voltage sums,
shared VIN/GND voltage, and KCL using the CircuitJS source current normalized
to the source-delivery direction:

```text
I_source_delivery = I_R1 + I_R2
```

The dedicated parallel verifier records branch-2 current before opening R1 and
requires it to remain within solver-safe tolerance afterward. It also drives
the existing DC voltmeter across VIN, R2, and LED2. The separate
`ParallelResistanceMeasurementFixture` uses two real `ResistorElm` instances
in a 1 kOhm || 10 kOhm network, verifies approximately 909.09 Ohm in both
probe orientations, then isolates each path and verifies the remaining 1 kOhm
or 10 kOhm result through the active meter stimulus.

Generic resistor repair now depends on the small
`ReplaceableResistorFamilyState` contract rather than `LedIndicatorFamilyState`
or hard-coded R1 pad strings. Both LED and parallel families provide the slot,
inventory, catalog, and physical-part ID allocator. The slot supplies the
component ID and pad terminal order, so R1 removal/replacement retargets the
actual declared bindings. The parallel workbench exposes only the R1 resistor
workflow; R2 and both LEDs remain fixed but their PCB pads and probes remain
available.

The existing one-sided router now accepts the parallel family's two three-pad
nets. It emits root-to-each-pad same-net copper, prefers reusing a compact
same-net trunk, continues to enforce unrelated-net clearance and routing
courtyards, and validates every endpoint. The broad Task 26 three-consecutive-
failure abort was removed; attempts remain bounded and retain the last failure
for diagnostics. Task 28 replaces the oversized simple placement with generic
topology-aware grouping and derives the final outline from occupied PCB content.

## Task 33 wrong-repair semantics and post-repair validation

Post-repair behavior is represented by the generic `GeneratedRepairStatus`
contract boundary. Family repair validators classify a solved board as
`STILL_FAULTED_OR_NONFUNCTIONAL`, `DEGRADED_BUT_OPERATING`, or
`CORRECTLY_RESTORED` using live CircuitJS currents, component relationships,
and operational state. The status is developer/verifier-facing state and is
not rendered in the normal player UI. The existing boolean
`isFunctionallyRepaired` contract remains available and is true only for
`CORRECTLY_RESTORED`.

`GeneratedChallengeController` completes a challenge only after the behavior
contract reports `CORRECTLY_RESTORED`; it does not compare an installed
catalog value, part ID, or nameplate with a hidden expected resistor. This
preserves Task 30's alternate-repair principle while allowing the solver to
reject a physically installed resistor whose electrical result is outside the
family's healthy contract. Missing/open or dark behavior remains
`STILL_FAULTED_OR_NONFUNCTIONAL`, while nonzero operating behavior outside the
healthy current range is `DEGRADED_BUT_OPERATING`.

The deterministic LED seed-3 proof uses a 12 V board with a generated 1 kOhm
R1 requirement. Removing the original faulted part and installing the
physically compatible 2.2 kOhm catalog part creates a distinct physical
resistor instance whose actual `ResistorElm` is 2200 Ohm. CircuitJS then
solves nonzero LED/resistor current above the illumination threshold but below
the 5-15 mA healthy contract, so the board operates in a degraded state and
the ticket remains incomplete. Removing it and installing a distinct 1 kOhm
instance restores the healthy solved current and completes the same generic
contract. The original fault binding remains on the loose original part.

`ReplacementDeveloperVerifier.verifyWrongRepair` covers the electrical,
physical-identity, inventory, attachment, and still-faulted assertions. The
`-WrongRepairNormalPlayer` browser route covers the genuine CDP mouse/keyboard
flow and captures the wrong-installed powered state and the completed state.
These developer-only checks do not add diagnosis metadata or wrong-part
messages to normal UI.

Task 34 adds the first component-specific stress boundary without generalizing
ratings to other part families. `ResistorNameplate` carries an immutable hidden
rated wattage, and `ResistorCatalogEntry` carries the catalog specification
used when a distinct physical acquisition is created. `PhysicalResistorPart`
retains that rating, its stable identity, and a private
`ResistorSecondaryOpenPath`; the latter is a real CircuitJS `SwitchElm` in the
part's public second-terminal path and is intentionally independent of
`GeneratedFaultBinding`. `ResistorSlotController` continues to own physical
R1 replacement graph changes, while `ResistorStressDamageSystem` alone owns
resistor stress state and secondary-open transitions.

The stress system samples the live solved `ResistorElm.getPower()` value and
uses the ratio of actual power to the immutable rating for deterministic
service-time accumulation. A powered-off board, loose part, active temporary
meter stimulus, or DC meter loading cannot advance persistent damage. At the
failure threshold the owned path opens, causing CircuitJS current and board
behavior to change naturally; the same failed part remains open after removal
and reinstallation. Explicit board reset clears secondary stress state and
closes only the secondary path, preserving the generated fault. Developer
verification is available only through the stress route and reports electrical
proof; the normal player UI never displays rating, stress, damage, thermal, or
failure diagnostics.

Task 34's player-facing manual gate was completed through Computer Use on the
active Windows desktop in Edge. The primary architect used normal visible
mouse/keyboard interaction on seed 3 to select and remove original R1, acquire
and install the 220 Ohm replacement, power the board, observe initial LED
operation with the original complaint and no stress diagnostic, advance only
the permitted developer service-time seam, and observe the same installed part
after secondary open with the LED dark and no diagnostic UI. The primary
Computer Use screenshots are `computer-use-severe-overload-powered.png` and
`computer-use-secondary-failure.png` under `docs/task-evidence/task-34/`.

Headless/CDP routes remain supporting evidence only. Their distinct supporting
screenshots are `initial-board.png`, `severe-overload-powered.png`,
`secondary-failure.png`, and `correct-restored.png`. Failed-part identity,
graph ownership, and solver measurements are supported by the developer/CDP
verifier rather than treated as substitutes for the direct player-facing gate.

---

## Task 34(A) — Core extensibility boundaries

Task 34(A) hardens the seams needed to add future physical part families and
instrument modes without making the common workbench a catalog of component
types.

PhysicalBoardRuntime is the owner of runtime identity and association. It
registers PhysicalBoardSlot instances, physical part instances, inventory
ownership, runtime capabilities, and mutation providers. A slot owns its
board package, pad IDs, terminal IDs, net IDs, and installed-part association.
PhysicalPart exposes reusable identity, specification, package, terminal,
electrical-backing, mount-state, provenance, failure-state, render metadata,
and capability contracts. PhysicalPartIdentityFactory allocates IDs through
the runtime-owned inventory namespace; family generators retain only the
electrical topology and family-specific validation they actually own.

WorkbenchCapabilityRegistry and WorkbenchCapabilityStrategy provide the
operation seam. PhysicalSlotMutationProvider implementations own remove, lift,
reconnect, restore, replacement, catalog, and loose-part behavior for their
declared slot/capability. PcbWorkbenchController discovers those providers
through the runtime and renders generic controls; it does not dispatch on
resistor, diode, LED, or reference-designator names.

PhysicalPartRenderRegistry composes PhysicalPartRenderProvider and
PhysicalPartRenderProbeProvider implementations. Typed PhysicalPackage and
PhysicalPartRenderMetadata carry package geometry, terminal geometry, visual
markings, polarity/orientation, hit regions, selection bounds, pads, and
probe targets. PcbWorkbenchRenderer performs common traversal, transforms,
selection, and probe orchestration. Providers draw both fixed and replaceable
parts, including loose/tray instances; the renderer does not decide
electrical behavior.

InstrumentModeProvider and InstrumentModeStrategy are the production mode
contract. InstrumentModeRegistry composes the built-in providers through a
bootstrap seam and accepts later production registrations with duplicate,
developer-only, and invalid-provider rejection. InstrumentController owns
common pointer/probe/UI lifecycle only. A registered visible provider can
create a button after controller construction, receive activation and probe
events, update its display, and clean up when switched or exited. DC voltage,
resistance, continuity, and diode test continue to use the CircuitMeasurement
boundary and CircuitJS-backed measurements.

The architecture verifier includes positive and negative canaries for
3–6-terminal parts, package connectivity, provider-owned rendering and probe
resolution, capability discovery, and post-construction visible instrument
registration. Normal UI continues to omit original numeric resistor values,
ratings, stress/damage state, injected fault identity, and other developer
diagnostics.

The final architect-owned visible gate used the built application at
tsjChallenge=led&seed=3 and tsjChallenge=diode&seed=3. Direct visible clicks
selected LED1, D1, and R1; power-off controls exposed lift/reconnect/remove;
lift/reconnect restored physical identity and powered operation; current
browser diagnostics contained no error or warning entries. Headless
verifiers and the visible gate are recorded in the Task 34(A) completion
report.

---

## Task 35 — Generalized physical specifications and catalogs

Task 35 completes the reusable physical-part/specification boundary needed by
the next component family without moving family electrical behavior into the
generic runtime.

`PhysicalSpecification` is immutable technical catalog data. It exposes a
stable specification ID and an extensible `Vector<PhysicalRating>` seam;
resistors currently contribute `PowerRating`, while diode, LED, and basic
specifications contribute no ratings. This keeps the generic runtime unaware
of future rating kinds such as voltage or capacitance voltage. The
`PhysicalPartCatalog<E>` contract is typed on reusable `PhysicalCatalogEntry`
rows, and `AbstractPhysicalCatalogEntry` owns common immutable entry identity,
specification, orientation metadata, and player-visible metadata. A future
family can add its own specification fields and electrical factory without
copying inventory or identity code.

`PhysicalNameplate` remains the player-visible privacy boundary. A catalog row
may contain hidden model/value/rating data in its `PhysicalSpecification`, but
the workbench renders only the physical instance's nameplate. Generated fault
ownership, stress/damage state, private original values, and hidden ratings are
not inferred or displayed from the generalized specification contract.

Production catalog acquisition preserves this boundary: the resistor, diode,
and LED slot controllers retain the exact immutable specification object from
the selected `PhysicalCatalogEntry`, while `PhysicalNameplate.forPhysicalPartId`
copies only the entry's permitted visible fields onto the newly allocated
physical identity. Slot/component IDs therefore remain separate from catalog
specification IDs, repeated acquisitions share immutable specification identity
but receive distinct physical IDs, and removal/reinstallation does not rebuild
either identity. Generated original parts continue to use their own privacy-safe
nameplates, so hidden original values, ratings, fault state, and stress state
remain unavailable to normal-player UI.

`PhysicalPartInventory<P>` is the only runtime-backed identity/inventory
mechanism. Resistor, diode, and LED generators now use typed views over that
inventory; their old family inventory wrappers were removed. Acquisition still
allocates a new stable physical ID, while removing and reinstalling the same
part preserves its identity and replacing it acquires a new one. Slot
controllers and capability providers remain family-owned where they create
CircuitJS elements, retarget electrical bindings, interpret polarity, or
validate family behavior.

`PhysicalPartOrientation` carries `NON_POLARIZED`, `NORMAL`, and `REVERSED`
metadata. Generic part and render contracts carry the orientation, while
diode/LED providers interpret reversal for electrical terminals and visual
polarity. Terminal names and pad IDs remain stable independently of render
orientation. Resistor power stress and secondary-open behavior remain owned by
the resistor implementation and are unchanged.

Package identity is declared by stable package ID. `PhysicalPackage`
canonicalizes endpoint order and sorts its normalized internal-connection set;
slot compatibility, PCB footprint lookup, and physical render lookup all use
the same package definition equivalence rule. A same-ID conflicting definition
is rejected deterministically. The developer-only Task 35 canary proves
reordered equivalent definitions through slot installation, footprint lookup,
installed rendering, loose rendering, removal, and reinstall identity, and
proves conflict rejection without adding a player-visible component family.

The selected catalog entry now controls workbench availability and the install
button label, so future entries with different compatibility do not inherit
the first entry's state. The Task 35 canary also proves a non-capacitor
three-terminal future part's specification/nameplate separation, stable part
identity, runtime inventory lifecycle, terminal/pad identity, CircuitJS
backing, capability discovery, footprint provider, and render provider.
No capacitor implementation or Task 36 behavior belongs to this boundary.

## Task 35(A) Quick Play session and completion boundary

Quick Play is an additive player route selected by `tsjQuickPlay=true`. The
small `QuickPlaySession` seam owns one `QuickPlaySelection`, and
`QuickPlaySelector` obtains exactly a family choice and a fresh generator seed
from its selection source. `QuickPlayFamilyRegistry` is the normal-player
eligibility boundary: the legacy families retain their validated seed envelope
`{0, 2, 3}`, while `NPN_LOW_SIDE_SWITCH` has its own validated envelope
`{0, 1, 2, 3}`. The selector delegates the selected seed to the selected
family's ordinary generator path; it does not randomize topology, values,
faults, layout, or measurements itself. Normal selection therefore never
forces a developer-only fault or verifier route.

The session and its generated board are page-owned state. A full reload of the
Quick Play URL constructs a new selector/session, board, physical runtime,
CircuitJS graph, probes, inventory, and challenge controller, so the next
selection does not reuse prior physical modifications, damage, or completion
state. The selected family and seed are available only through a developer
verification attribute/getter; normal UI shows the real PCB, service complaint,
and workbench without fault, answer, specification, rating, stress, or damage
metadata.

The Quick Play canary exercises the same ordinary selector/generator boundary
with exact and arbitrary injected selection values. It proves that NPN seeds
0, 1, 2, and 3 naturally reach C-E open, C-E short, base-resistor open, and
load-path open respectively, while the legacy families remain on `{0, 2, 3}`
and the diode developer-only short remains excluded. The NPN canary also reads
the generated physical load-input nameplates (`9 V`, `12 V`, `5 V`, `9 V`),
so the seed envelope covers all three nominal load supplies without teaching
Quick Play about fault effects or bypassing the family generator.

Route precedence remains explicit: `tsjFixture` and `tsjChallenge` routes are
resolved before Quick Play, and their existing family/seed behavior is
unchanged. Quick Play therefore cannot replace an explicit challenge, fixture,
or developer verification route. The focused developer verifier injects its
selection source so selector coverage is deterministic rather than
probabilistic.

`PcbWorkbenchController` adds `Finish Job` only when the active installed
challenge came from Quick Play. The button is disabled while the generic
challenge controller is preparing. Its action calls
`GeneratedChallengeController.finishJob()`, which checks the existing generic
`GeneratedRepairStatus` contract against the live solver-backed board. A failed
or degraded check leaves the same board and reports only
`Functional check failed. Continue troubleshooting.`. A correctly restored
board crosses the existing generic completion boundary and then reloads the
Quick Play URL for a clean next session. Stock CircuitJS, explicit generated
routes, and arbitrary developer routes do not receive this control.

## Task 36 — capacitor foundation, RC delay, and stored-energy readiness

Capacitors now have an immutable typed `CapacitorSpecification`, separate
player-facing `CapacitorNameplate`, reusable `VoltageRating`, catalog entry,
and distinct runtime `PhysicalCapacitorPart` identity. A catalog selection
retains the exact immutable specification while each acquisition receives a
new physical part identity. The original C1 remains the sole fault-owning
physical part, so removing and reinstalling it restores its generated fault;
a catalog replacement never inherits that fault. Neither a catalog ID nor a
specification ID is used to decide repair success.

The physical package/provider boundary now includes radial electrolytic and
ceramic through-hole capacitors. The electrolytic package owns `+`/`-`
terminals, polarity stripe/plus rendering, and permitted value/rating marking;
the ceramic provider owns its compact `104` marking. Footprint, installed and
loose-part render geometry, and probe targeting remain package/render-provider
owned rather than component-family switches in the generic workbench or PCB
renderer. Reversed electrolytic installation is rejected by terminal/package
compatibility; this task does not model reverse-install damage.

`RC_DELAY` / `RC_CHARGE_DELAY` is the first deterministic temporal family. It
uses ordinary CircuitJS `CapacitorElm` elements: `VIN -> R1 -> RC_OUT`/J2,
with C1 and R2 each from `RC_OUT` to `GND`, and the healthy ceramic C2 directly
from `VIN` to `GND`. Stable pads and nets include `VIN`, `RC_OUT`, and `GND`.
Its current documented seed envelope is: seed 0 = 5 V / R1 12 kOhm / R2 10
kOhm / C1 positive-lead open; seed 2 = 9 V / R1 15 kOhm / R2 10 kOhm / C1
short; seed 3 = 12 V / R1 15 kOhm / R2 10 kOhm / C1 positive-lead open. C1
is 33 uF / 16 V, C2 is 100 nF / 50 V. The actual R2/C1 power-off discharge
constant is `.330 s`; effective healthy charge constants are about `.180 s`
for seed 0 and `.198 s` for seeds 2/3. The profile performs a genuine
external isolation for `1.000 s` and uses `.100 s` and `.800 s` power-on
checkpoints. R1 limits the short-fault source current.

`GeneratedTemporalBehavior` is an optional family-owned contract. The generic
challenge lifecycle invokes it without knowing an RC component or fault ID.
The RC implementation performs a genuine external power-off isolation and
resistive discharge through the real graph, advances bounded CircuitJS solver
time, retains a pre-fault healthy sample, then samples the faulted output and
classifies healthy delayed rise, open-too-fast, or short-stuck-low from those
live solver samples. Generic `Finish Job`
therefore accepts only a functionally valid timing repair, not a matching
part/catalog identity; instantaneous families retain their existing behavior.
`GeneratedLiveTemporalSimulation` is a second optional generic seam: a family
may request a small bounded solver-time increment during an ordinary live UI
update. `CirSim` performs only that real solver advance and never receives an
RC ID, a waveform, or a display formula. RC requests `.005 s` per live update
so the visible C1/R2 charge and discharge complete at a usable cadence while
remaining ordinary `CapacitorElm` physics. Once a challenge is completed, the
generic controller no longer replays its temporal profile on later frames or
on a repeated direct Finish Job request: only the exact `READY` state may run
the functional repair profile. `COMPLETED` remains semantic-operation-ready
but is a terminal, mutation-free state: board power, instruments, PCB
selection, and physical topology changes are disabled, so manual physical
actions cannot silently alter the finished job or replay a temporal profile.
The player's actual live capacitor state is still retained.

`BoardPowerState.UNPOWERED` remains external source isolation. Optional
`ActiveMeasurementReadinessCapability` adds a separate, generic stored-energy
policy with one documented residual-voltage threshold of `.25 V`. The RC
policy reports `POWER OFF`, `SETTLING`, or `DISCHARGE` before it returns ready and
maps every exposed board pad back to the installed storage part's physical board
nets; loose physical parts remain relevant only when their own terminals are
selected. Both the measurement adapter and CirSim's direct resistance/diode
transactions enforce it, preventing temporary overlays until the real circuit
has discharged. DC voltage receives a provider-gated live reading only for
this temporal board; ordinary boards preserve the legacy one-shot path.
An active OHM/continuity/diode transaction is itself a real source and can
recharge C1; after cleanup the same generic policy correctly requires another
natural R2 discharge before the next active transaction.

Quick Play now has six eligible normal-player families, including `RC_DELAY`,
`NPN_LOW_SIDE_SWITCH`, and `NMOS_LOW_SIDE_SWITCH`. Its generic completion path
uses the temporal repair contract when present, while explicit
fixture/challenge precedence and normal-player fault/privacy boundaries remain
unchanged.

## Task 37 — NPN low-side switch family and tray correction

The NPN family keeps CircuitJS as the electrical source of truth. Its bounded
topology has independent `LOAD_VIN_INPUT` and `CONTROL_VIN_INPUT` sources,
`RLOAD -> LED1 -> Q1.C` on the load path, `RB -> Q1.B` with `RPD` to ground,
and `Q1.E` on the ground net. Stable logical nets are `LOAD_SUPPLY`,
`CONTROL_INPUT`, `BASE`, `LOAD_NODE`, `COLLECTOR`, and `GND`; stable Q1 pads
are `Q1.B`, `Q1.C`, and `Q1.E`. `NTransistorElm`/`TransistorElm` post order
is explicitly preserved as base/post 0, collector/post 1, emitter/post 2,
and the family validators measure live transistor, LED, resistor, and node
values for healthy, faulted, and repaired behavior.

The physical foundation adds an immutable `NpnSpecification`, a TO-92 package
with ordered B/C/E terminals, `PhysicalNpnPart`, and a replaceable Q1 slot.
`StandardPcbFootprintProviders` owns the registered TO-92 footprint and
`StandardPhysicalPartRenderProviders` plus
`PhysicalPartRenderProbeProviders` own installed/loose body and probe geometry.
Catalog acquisition allocates a separate live `NTransistorElm` backing and
physical identity. The original Q1 retains its private generated fault when
removed and reinstalled; a catalog replacement is a distinct fault-free part.
`NpnSlotController` retargets the existing detachable graph connections so
loose measurement and replacement remain solver-backed.

The family fault boundary includes collector/emitter open, collector/emitter
short, base-resistor open, and load-path open effects. Fault infrastructure is
owned by the original physical part and is not copied into replacements.
`NpnLowSideSwitchFaultValidator` and
`NpnLowSideSwitchRepairValidator` consume solver state and the generic
challenge lifecycle; repair success requires the live circuit to switch both
on and off and does not recognize a component ID, catalog ID, or hidden fault
flag. `NPN_LOW_SIDE_SWITCH` is registered in Quick Play without exposing
fault, answer, or physical metadata in the normal player UI.

The two connector positive pads have independent authoritative
`PowerInputNameplate` records. The NPN layout receives those records from the
generated physical board specification, and the renderer resolves a targeted
positive pad to that pad's own display label (`+5V`, `+9V`, or `+12V`); return
pads resolve to `GND`. This prevents a multi-input board from collapsing every
positive connector label to an aggregate `VIN` label while preserving the
generic single-input fallback for older families.

The NPN ground copper is routed as a shared trunk from J1.2 to J2.2, RPD.2,
and Q1.E. The route validator now rejects duplicate points, repeated or
overlapping segments, non-adjacent self-intersections, and backtracking while
allowing adjacent continuous/orthogonal segments and intentional same-net
topology. The NPN generator consequently has no old long ground detour or
ambiguous crossing in its stable route IDs.

Instantaneous NPN repair and compatibility checks are observational: they may
temporarily command the real solver-backed board to test a state, but capture
the prior commanded state and restore it in `finally`. The RC temporal family
remains intentionally stateful because its validation advances real solver
time and models stored energy. A developer-only
`data-tsj-npn-electrical-report` exposes the live full-precision healthy
envelope only when the NPN verifier flag is active; normal player pages never
receive the attribute or its hidden terms.

`NpnLowSideSwitchRepairValidator.getRepairStatus()` captures the command state
before its precondition gates, performs the genuine healthy ON/OFF CircuitJS
profile, and restores the command through `NpnLowSideSwitchFamilyState` on
every exit path, including early nonfunctional returns. The developer proof
also snapshots the live control voltage, load/base/collector currents, and
collector voltage around faulted/wrong and correctly repaired status queries
for both C-E-open commanded-ON and C-E-short commanded-OFF cases. The
snapshot is intentionally solver-backed; it is not a second bookkeeping model.

The instantaneous NPN fault, scenario, and repair validators share a narrow
`CirSim` observational-validation depth. While that depth is nonzero,
`needAnalyze()` still analyzes and solves the real switched graph but does not
clear the active instrument through an intermediate topology refresh. Each
validator owns a nested `try/finally` restoration, so candidate evaluation and
automatic ready-state checks cannot leak a temporary command into the next
candidate or into the player view. `GeneratedScenarioCatalog` orders compatible
scenarios by stable scenario ID, and the selected scenario then applies its
explicit family presentation boundary: NPN not-switching is presented
commanded ON/high, while NPN stuck-active is presented commanded OFF/low.
Temporary active-measurement cleanup removes its overlay and solver elements
without manufacturing a new generated-board verification request when no real
board mutation is pending; legitimate board, power, and probe changes retain
their normal refresh/verification behavior.

The parts-tray correction is generic rather than family-specific. Fixed RC,
seeded LED/diode/parallel, and NPN layouts all call the same tray placement and
geometry validation seam. The compact-board calculation excludes tray chrome,
then the shared invariant moves the tray to a valid canvas side and rejects
any overlap with the board outline. `PcbLayoutDeveloperVerifier` additionally
regenerates the Q1 footprint through the registered TO-92 provider and checks
placement, body, courtyard, pad coordinates, escape vectors/lengths, and B/C/E
ordering. This preserves provider ownership while keeping the tray visible,
selectable, paginated, and probeable.

Focused Task 37 correction validation covers the JDK 8/GWT production build,
the four-seed natural NPN envelope, the 16-case forced seed/fault matrix for
seeds 0, 1, 2, and 3, and four ordinary Quick Play NPN routes,
provider/renderer and architecture boundaries, layout determinism/tray
separation, Quick Play, RC/stored-energy, and LED/diode/parallel regressions.
Visible in-app Browser evidence is stored under
`docs/task-evidence/task-37-correction/`. The renderer still shows the compact
physical `NPN` body and B/C/E markings; improving that silkscreen fidelity is
future work, not an electrical-truth blocker for this milestone.

The final correction's focused verifier regenerates ordinary Quick Play NPN
seeds 0, 1, 2, and 3, compares each raw J1.1/J2.1 `PcbSilkscreenLabel` and
rendered targeted label to the generated physical nameplates, and preserves
the family-specific normal-player envelopes: legacy families use 0, 2, and 3;
NPN uses 0, 1, 2, and 3. The seed-1 ordinary route presents the solver-backed
stuck-active complaint, restores Q1 through the real workbench path, and
finishes through the generic completion boundary without exposing developer
metadata.

## Task 38 — NMOS low-side switch family

The NMOS family keeps CircuitJS as the electrical source of truth. Its bounded
topology uses independent load and control supplies: `LOAD_SUPPLY -> RLOAD ->
LED1 -> Q1.D`, `Q1.S -> GND`, and external control infrastructure -> `J2.1` ->
the real board gate net -> `Q1.G`, with `RPD` from that same gate net to ground.
The command switch is outside the physical-board boundary, so `J2.1` is the
board-side commanded voltage. Stable logical nets are `LOAD_SUPPLY`,
`CONTROL_INPUT`, `LOAD_NODE`, `DRAIN`, and `GND`; `J2.1`, `RPD.1`, and `Q1.G`
share `CONTROL_INPUT`, and stable physical pads remain `Q1.G`, `Q1.D`, and
`Q1.S`. No TP1/TP2 pseudo-headers are generated.

At the CircuitJS binding boundary, `NMosfetElm`/`MosfetElm` post order is
permanently documented as post 0 = gate, post 1 = source, and post 2 = drain.
The generated model remains a three-post NMOS with its default body diode
enabled and no exposed body terminal. Physical package order is deliberately
G/D/S and is translated explicitly to solver posts G/S/D. The solver's legacy
MOSFET channel coefficient is configured through the typed NMOS specification;
it is not treated as BJT beta or as a base-current model. Healthy validation
measures live VGS, VDS, load/LED current, control/load supply voltage, and
`getCurrentIntoNode(0)` gate current, which remains effectively zero.

The fault boundary contains Q1-owned D-S open, D-S short, and gate-path-open
effects. D-S short uses a 0.1-ohm solver shunt plus a series private board-path
switch. That switch remains in the original part's electrical backing when the
part is loose or reinstalled, and is opened when a distinct catalog backing is
installed; replacements do not inherit the original `GeneratedFaultBinding`.
Fault and scenario compatibility checks command the real control switch only
inside an observational boundary and restore the prior command in `finally`.
Generic repair status requires healthy live ON and OFF behavior and does not
recognize a component ID, catalog ID, or hidden fault flag.

The physical layer adds typed NMOS specification, TO-92-style G/D/S package,
installed/loose renderer and probe target, private original-fault identity,
replaceable Q1 slot, and a catalog that allocates a distinct live
`NMosfetElm`. The one-sided PCB layout uses the registered provider for Q1
footprint geometry, pads, keepout, and routing parity; visible
`CONTROL_INPUT` copper branches from J2.1 to RPD.1 and Q1.G on the compact
one-sided board. `NMOS_LOW_SIDE_SWITCH`
is appended to Quick Play at index 5 with the validated normal-player seed
envelope `{0, 1, 2}` mapping naturally to D-S open, D-S short, and gate open.
Complaints remain symptom-only: controlled load does not turn on, or remains on
when control is low.

Task 38 validation includes the dedicated NMOS developer verifier, deterministic
fault/seed canary, provider/layout parity, NPN/Quick Play regression, JDK 8/GWT
OBF build, and visible in-app Browser evidence under
`docs/task-evidence/task-38/`. The standalone Edge PowerShell harness remains
unavailable in this environment because its WMI/CIM process query returns
`Access denied`; this is recorded as a harness limitation rather than a
product pass. The permanent control-boundary canary proves ON/OFF voltage
agreement at J2.1/RPD.1/Q1.G, board-power isolation, absence of TP1/TP2, and
J2-rooted visible copper. The corrected layout also avoids unrelated trace
crossings for seeds 0 through 3 while preserving provider parity, clearance,
keepouts, deterministic routing, and parts-tray separation. Fresh visible
Browser evidence covers the symptom-only complaint, 5 V gate/source, the
D-S-short low-control case with both J2.1 and Q1.G at 0 V while the load stays
active, power-off Q1 removal, catalog replacement, and repair verification.

## Task 39 — semantic player operations and customer retest

Generated boards expose one family-neutral `GeneratedBoardOperationCatalog`
through `GeneratedBoardInstance`. Operation IDs are stable semantic constants
(`CONTROL_INPUT_HIGH`, `CONTROL_INPUT_LOW`, and `CUSTOMER_RETEST`); the ID
validation rejects solver-node, pad, coordinate, collection-index, and UUID
identity. Family state owns the catalog and its
`GeneratedCustomerRetestProfile`, so the profile describes the required power
or input transition, observable output, timing/repetition, and unaffected
functions without exposing fault metadata or private values.

The NPN and NMOS HIGH/LOW operations dispatch the existing external
`SwitchElm` command switch and settle CircuitJS. Their customer profiles invoke
those same semantic operations and validate J2.1/gate and load response from
the solved circuit, restoring the prior command, board power, and physical
state in nested `finally` cleanup. Scenario compatibility, fault validation,
repair validation, and developer checks use the same boundary; there is no
validator-only control hook. LED, diode-protected, and parallel-indicator
families use the shared observation profile. RC owns a temporal profile that
delegates to `RcDelayTemporalBehavior` for a real board-power cycle and natural
stored-energy discharge rather than duplicating RC physics.

`GeneratedChallengeController` keeps live repair status, customer-retest
result, Finish Job, and latched `COMPLETED` state separate. A normal player
sees family-safe operation/retest controls in the service ticket. Retest is
required before completion. After completion, NPN/NMOS semantic operation
controls remain electrically live, while board power, instruments, PCB
selection, and physical mutation controls are disabled and completion is not
silently rechecked. Board power still uses `BoardPowerController` and remains
independent from CircuitJS RUN/STOP.

## Task 40 — physical fault locus and serviceability admission

Task 40 adds a hidden physical-ownership boundary between a solver effect and
the workbench actions that can legally expose, isolate, repair, and retest it.
`GeneratedFaultLocus` classifies the owner as component-internal,
terminal/lead attachment, connector contact, or trace segment. Locus identity
is semantic and stable (`componentId`, terminal ID, or path ID); it never uses
a private solver switch, CircuitJS coordinate, post/index, collection index,
or generated UUID as a physical owner. `GeneratedFaultServiceability` carries
the legal observation, isolation, repair, and Task 39 customer-retest IDs.

`GeneratedFaultServiceabilityAdmission` is the candidate boundary. Admission
requires compatibility, a stable locus, non-empty service actions, the known
observation/workbench action whitelist, and `CUSTOMER_RETEST`. Runtime
validation then checks the installed original physical owner, terminal and
connection bindings, replacement provider, probe exposure, operation catalog,
and family controller providers. `Task40DeveloperVerifier` executes the
resulting remove/lift/reconnect/catalog-install operations through the real
`PcbWorkbenchController`, powers the board, and invokes the existing customer
retest; it does not accept metadata-only repair claims. A bogus repair ID is
rejected at candidate admission and cannot inflate owner metrics.

`GeneratedBoardInstance` retains the complete admitted candidate vector so
physical-owner metrics count every candidate owner rather than only the
selected binding. The selected owner remains separately available for runtime
integrity checks. Current examples include RC `C1` positive-lead attachment,
NMOS `Q1` public `G`/`D`/`S` terminals, NPN `Q1`/`RB` ownership, and component
internal ownership for the supported simple parts. Connector and trace owners
remain incompatible in normal admission; NPN `LOAD_PATH_OPEN` is retained
only as a forced developer fixture until a real path owner and repair primitive
exist. Normal UI surfaces none of this hidden fault metadata.

The current normal family corpus is 14 hypotheses: LED 3 (including the later
LED-owned open hypothesis), diode 1, parallel 2, RC 2, NPN 3, and NMOS 3.
Normal `DIODE_SHORT` is developer-only and NPN `LOAD_PATH_OPEN` was removed
from normal admission under the option-B resolution. Task 40 is therefore a
serviceability/admission boundary only; it does not add trace, connector,
jumper, cut, or generic repair gameplay.

## Task 41 — diagnostic solvability verifier and complexity evidence

Task 41 adds the family-agnostic `GeneratedDiagnosticSolvabilityContract`,
`GeneratedDiagnosticPlan`, route catalog, solver sample, evidence, and live
admission boundary. A normal generated challenge is not READY until the
rendered board has passed the same bounded diagnostic proof used by the
developer verifier. The proof operates on one unchanged topology/layout at a
time, enumerates the owner's admitted hypotheses, and uses real `PcbWorkbenchController`
probe targets, `InstrumentController` modes, `BoardPowerController` state,
player input operations, temporal waits, isolation, repair, and customer
retest. Voltage, resistance, continuity, and diode observations are produced
by CircuitJS; evidence records actual values and tolerances rather than labels
or scripted readings. Plan capability metadata is kept separate from the
execution trace: a declared isolation or meter capability is not reported as
executed unless the verifier actually performs it.

The current normal family corpus contains 14 hypotheses across LED 3,
diode 1, parallel 2, RC 2, NPN 3, and NMOS 3. Candidate groups are compared
pairwise using solver-derived signatures. Distinct candidates must have a
separating plan; candidates with the same observable/repair behavior must
share an explicit equivalent-repair class. `DIODE_SHORT` and NPN
`LOAD_PATH_OPEN` are excluded from normal player admission. The latter is
retained as a developer-only same-layout comparison against NPN C-E open so
the exclusion is evidence-backed rather than silently ignored.

Developer verification is isolated from the player workbench. Proof boards use
detached real renderers, and `CirSim` tracks attached workbench ownership so a
candidate cannot append duplicate player panels. `Task41SimulationSnapshot`
restores the exact generated owner and CircuitJS graph object references and
contents without re-running analysis. It also restores board-power bindings,
instrument and continuity-feedback state, physical modifications, runtime and
render counters (`myframes`, `mytime`, `myruntime`, `mydrawtime`, and CircuitElm
render globals), and the active UI/runtime flags. Normal rollback is
transactional with best-effort exception recovery; eight injected restore
stages are exercised by the developer verifier. Task 41 evidence is published
only on an explicit developer verification route and never in normal-player UI.

Task 43P-C2 checks the entire currently captured synchronous inventory,
including the resistance test current, diagnostic values, solver flags,
input/render bookkeeping, and measurement/verification state. It uses
null-safe string/rectangle value comparisons and the existing paired-NaN
double semantics. UI refresh and run/stop callbacks can alter geometry,
repaint/analysis flags, and instrument state; those captured values are
restored last before the immediate assertion. Scope-array identity is checked;
scope internals, nested physical/challenge/renderer state, callback cancellation,
and timer identity are not captured. This remains the detached-original-owner
proof model, not a complete same-owner transaction.

## A02 admission identity and seeded geometry contracts

`GeneratedFaultCandidate.isAdmitted()` owns normal eligibility: compatibility
and an admissible, non-null serviceability contract. The null-safe
`GeneratedFaultServiceabilityAdmission` population adapter is used by selection,
counting, owner counting, diagnostic enumeration and evidence validation. Empty
live populations fail. Forced developer selection is separate and cannot fill
normal proof coverage.

`GeneratedFault.getHypothesisKey()` length-frames family, fault type, physical
target, stable fault ID and canonical IEEE-754 healthy/effective value bits.
Seed, collection order,
runtime identity and geometry do not enter the key. Every duplicate admitted
key rejects deterministically, including identical records. The diagnostic
contract retains sorted exact keys independently of unique physical-owner
count. Task41 regenerates and checks the exact hypothesis and compares proved
keys to the contract, so two supported faults on one owner remain two hypotheses.

`PcbBoardLayout.getTraceBendCount()` compares direction signs using widened
coordinate differences. Segment length does not matter, immediate reversals
count, and repeated points are ignored without resetting the last direction.
Geometry validation rejects diagonals and degenerate segments. Current quality
admission, ranking and snapshot metrics use direction bends; the historical
raw-displacement branch is removed.

Prototype footprint origins are (0,0) and their pads are local. Placed origins
and pads use board coordinates. Algorithm 4's production connected-placement
helper solves `round(sum(w * (neighborWorldPad - candidateLocalPad)) / sum(w))`
for the new component origin. Only an empty usable-neighbor set uses the board
center. Widened differences, finite sums and checked integer range prevent
silent target overflow. Existing jitter draws, grid search, offsets, bounds,
routing and whole-board `compactToContent` translation follow this raw target.
Raw translation covariance is separate from final compaction normalization.

## Task 40/41 contract-hardening correction

The bounded correction after Task 41 keeps the accepted solver-backed proof and
hardens the serviceability contract before the next milestone. In
`GeneratedFaultServiceability` and `GeneratedDiagnosticPlan`, fault-clearing
repair actions are distinct from workflow/manipulation actions. For the current
catalog, `CATALOG_INSTALL` is the fault-clearing replacement primitive;
`RECONNECT_LEAD` is only a physical workflow/restoration operation. Reconnecting
a lifted C1 positive lead or Q1 gate lead, and reinstalling the original
`GeneratedFaultOwningPart`, therefore remain explicitly non-restoring paths.
The Task 40 verifier proves those negative cases through the live workbench and
then proves that a different correct catalog part reaches
`CORRECTLY_RESTORED` and passes `CUSTOMER_RETEST`.

`GeneratedActionVocabulary` is shared by the Task 40 and Task 41 executable
admission boundaries. Known/reserved IDs such as `RESTORE` and
connector-specific observation are not currently executable and cannot make a
normal candidate or plan admissible. Connector and trace repair gameplay is
unchanged and remains deferred.

`GeneratedDiagnosticExecutionTrace` records the meter modes, input/power
transitions, isolation operations, temporal waits, repair sequence, and
customer retest actually exercised by the proof. Evidence exposes those as
`executed*` fields alongside unambiguous `declared*` plan fields. The measured
depth is the sum of the distinct frozen-trace entries for executed meter modes,
input/power transitions, isolation actions, and temporal waits; repair and
retest are reachability evidence and are intentionally excluded. It is not
copied from the catalog's hand-authored plan depth, and the trace checks that
the published value still equals that formula. In particular, the RC plan may
declare remove/lift isolation capability while its current signature execution
performs no isolation, which is reported as zero executed isolation actions
rather than fabricated evidence.

Candidate equivalence now requires both solver-observation equivalence within
the defined sample tolerances and equivalent legal physical repair semantics
(owner, locus/path, terminal, and repair/workflow action sets). The negative
fixture rejects observationally identical candidates with different physical
owners instead of assigning them an `EQUIVALENT_REPAIR` class. The live
pre-READY gate, solver truth, unchanged topology/layout, detached snapshot /
restore, and player evidence privacy remain intact across all 13 normal routes.
Task 42 remains the next eligible roadmap milestone and was not started by this
correction.

## Task 42 — existing-family diagnostic diversity proof

Task 42 adds a second physical owner to the existing LED indicator family
without changing the PCB renderer or substituting UI readings for CircuitJS.
`GeneratedFaultType.LED_OPEN` uses a private `SwitchElm` in series with the
original `LEDElm`. `LedOpenFaultEffect` maps the board-facing LED1 anode to
the switch input and the board-facing cathode to the LED output. The original
`PhysicalLedPart` retains that binding and the private switch in its electrical
backing when it is removed or reinstalled; a catalog-acquired LED has a new
`LEDElm` and no generated-fault binding. The normal repair boundary is
therefore `REMOVE` → `CATALOG_INSTALL` → `CUSTOMER_RETEST`, with no hidden
reconnect or restore shortcut.

The LED fault is admitted through the same serviceability and diagnostic-plan
contracts as the existing R1 faults. Task 40 exercises wrong-owner R1 repair,
original-owner reinstall, and distinct correct LED replacement. Task 41 uses
the existing LED public pads plus in-circuit resistance to separate LED_OPEN,
R1 open, and R1 incorrect value from the solver, then performs real repair and
retest. The normal route count is derived from the admitted candidate corpus,
not a fixed expected-count assertion.

`GeneratedDiagnosticOwnerDiversity` is a derived contract metric. It is
computed from the distinct stable physical owner IDs of admitted candidates
and is revalidated with the solvability contract. The current normal corpus is
14 routes and has this derived shape:

| Family | Admitted routes | Physical owners | Classification |
| --- | ---: | --- | --- |
| LED | 3 | LED1, R1 | MULTI_OWNER_DIAGNOSTIC |
| Diode | 1 | D1 | GUIDED_EASY_SINGLE_OWNER |
| Parallel | 2 | R1 | GUIDED_EASY_SINGLE_OWNER |
| RC | 2 | C1 | GUIDED_EASY_SINGLE_OWNER |
| NPN | 3 | Q1, RB | MULTI_OWNER_DIAGNOSTIC |
| NMOS | 3 | Q1 | GUIDED_EASY_SINGLE_OWNER |

The LED Quick Play seed envelope is family-specific `{0, 2, 3, 4}` so the
new LED-owned route is reachable while the other legacy family envelopes and
their established seed mappings remain unchanged. Classification is not a
player-facing difficulty selector and no future topology/composition system
is introduced.

## Task 43R-1 — physical package contract and geometry identity

Task 43R-1 originally froze package-local physical geometry as an immutable
contract (contract version 2). Recovery correction 43R-2C revised the live
production geometry contract to version 3 for the escape correction described
below; the original version-2 history remains recorded in the task handoff.
`PhysicalPackageGeometry` owns body, connected and lifted
lead poses, pad geometry, body keep-out, routing courtyard, selection, drag,
and interaction surfaces. Board-pad probe surfaces and component-lead probe
surfaces are separate declarations; the package validator rejects overlap
within the full cross-terminal surface matrix, including peer board pads and
component-lead probes. Connected lead endpoints are terminal pad centers.
Lifted leads have a detached free endpoint outside the board-pad probe surface,
while their body attachment remains the connected body point. A placed geometry
object projects these immutable values by checked translation rather than
mutating package state.

`PhysicalPackage` owns stable package identity, ordered terminal IDs, internal
connectivity, the geometry-contract version, and a finite canonical catalog of
named variants. Axial resistor variants are exactly `SPAN_220`, `SPAN_240`,
and `SPAN_260`; axial diode variants are `SPAN_230` and `SPAN_250`; the
two-terminal connector declares base and `MIRROR_X` realizations. Each
placement retains its canonical package object, variant key, transform key,
and geometry version, and exposes an immutable
`PhysicalGeometryRealization` carrier. `PcbComponentPlacement.geometryFingerprint()`
includes those values and the declared loose/default variant, so translation
and layout identity cannot silently discard the selected physical realization.
`PhysicalPackage` owns the package definition/catalog/default;
`PcbComponentPlacement` owns the selected board realization.

The package declares the loose/default projection explicitly. The final
`PcbBoardLayout` binds each generated package-backed `PhysicalBoardSlot` to its
selected carrier, and the slot retains that carrier when its part is removed.
Each `PhysicalPart` binds its carrier once; lift, reconnect, remove, and
reinstall cannot change that realization. Replacement parts are new identities
and receive the slot carrier when installed. An unassigned loose projection
uses the package's declared default rather than inferring geometry from a
package ID. Current board components and component placements require explicit
canonical packages and package-backed geometry. Obsolete rejecting component-
placement overloads and implicit board-component package selection are removed.
Any separate developer-only pad fixtures still undergo the layout geometry gate.

The focused Task 43 verifier enumerates the package catalog and checks
determinism, terminal order, translation, canonical-object identity,
undeclared/foreign geometry rejection, malformed escape and detached lifted
endpoint/bounds/probe canaries, geometry-version identity, connector
orientation, generated family/topology agreement, and stable generated board
component/pad/net/semantic IDs plus runtime slot/part/terminal/endpoint/carrier
identity. A synthetic SPAN_260 axial-resistor lifecycle proof covers binding,
remove/reinstall, and rejection of a SPAN_220 rebind without adding electrical
nodes to a live board. No CircuitJS electrical topology changed; no measurement
endpoint identity changed; no generated fault semantics changed; no
stress/damage semantics changed; no existing physical repair operation
semantics were redesigned; physical slot installation now additionally
binds/enforces the approved immutable geometry realization.

The milestone boundary is intentionally staged. Task 43R-2 is strictly board
geometry consumers: layout realization, compaction, containment, and physical
net-connectivity validation. Task 43R-3 consumes installed geometry for board
rendering, selection, and board-pad/component-side probe interaction. Task
43R-4 covers loose-part rigid pose, loose rendering/hit testing/probing, and
the physical-part realization consumer lifecycle. These renderer, tray,
routing-consumer, and physical-part consumer slices remain deferred; 43R-1
freezes the contract without claiming that any of them is implemented.

## Task 43R-2 — board geometry consumers, compaction, and physical connectivity

Task 43R-2 closes the board/layout consumers of the frozen package geometry
contract. `PcbBoardLayout` now treats pad centers and trace-stroke segment
nodes as one physical graph. Trace-to-pad, trace-to-trace, and pad-to-pad
contacts are unioned only when they share a logical net; cross-net physical
contacts are rejected, and every logical multi-pad net must have one connected
physical component. Package-declared internal connectivity is applied
transitively, so a multi-terminal package can connect separate board pads
without fabricating trace copper. Orphan pads, disconnected islands, unknown
trace pads, endpoint-only shortcuts, and unrelated trace crossings are
rejected by the developer canaries.

Containment and occupied-bounds validation use the complete installed geometry:
body, body keep-out, routing courtyard, selection/drag envelope, pad/probe
surfaces, and connected or lifted component leads. Trace containment and
surface clearances use the full `TRACE_WIDTH` stroke. A trace may leave its
own endpoint pad through the declared endpoint escape, but it may not use a
pad center or zero-length segment as a connectivity shortcut or trespass
through another component's routing courtyard/silkscreen. The seeded router's
collision envelope mirrors this contract.

Compaction translates placements through checked rigid translation and keeps
the exact immutable `PhysicalGeometryRealization` object identity. Pads,
traces, labels, and bounds translate with checked arithmetic; the selected
package, variant, transform, geometry version, and realization fingerprint
therefore remain stable after compaction. Production validation also requires
the canonical package object rather than merely an equivalent package value.

`PcbR2DeveloperVerifier` provides positive canaries for two-, three-, branch-,
perpendicular-, transitive-package-, compaction-, and generated LED/diode/
parallel-family layouts, plus negative canaries for the rejected physical
contacts and surfaces above. `PcbLayoutDeveloperVerifier` keeps the remaining
NPN authored-route signatures as deferred failures while the completed RC and
NMOS fixed-layout proofs are hard gates. No fixed route coordinates, renderer,
tray, electrical graph, fault, stress/damage, probe, or Task 44 work is
included in 43R-2.

## Task 43R-2C — production escape geometry recovery correction

Recovery correction 43R-2C revises the physical geometry contract from the
historical R-2 version 2 to current contract version 3 before RC fixed-route
reconstruction. The correction changes only the declared full-width escape
lengths for four production geometry families: TO-92 NPN collector/emitter
and TO-92 NMOS drain/source increase from 32 to 36, while radial ceramic
capacitor terminals 1/2 and `THROUGH_HOLE_OUTPUT_HEADER_2` terminals 1/2
increase from 30 to 35. Package IDs, terminal order, canonical variant keys
and transforms, package flags, internal connectivity, and every other geometry
surface remain unchanged. `RcDelayGenerator` continues to bind J2 to
`THROUGH_HOLE_OUTPUT_HEADER_2` with its fixed `DEFAULT`/`IDENTITY` geometry;
it is not replaced by the input connector package.

The version bump is part of package/realization identity and therefore flows
through placement and footprint fingerprints, package-backed validation, and
physical lifecycle/rebinding checks. `PcbProductionEscapeDeveloperVerifier`
is invoked once by `PcbR2DeveloperVerifier`; it enumerates the live registered
package catalog while excluding developer-generic packages, requires the
current matrix of 13 canonical variants and 28 nonzero terminal escapes, and
validates package-backed long escape fixtures through the real
`PcbBoardLayout.validateGeometry` oracle. A version-3 negative canary shortens
exactly one corrected escape by one and requires the existing routing-courtyard
failure. No router, fixed route, component movement, validator, electrical,
measurement, fault, rendering, lifecycle, or Task 44 code is changed.

## Task 43R-3 — installed rendering, selection, and board/component probing

Task 43R-3 consumes the immutable package placement contract for the installed
board view. `PcbWorkbenchRenderer` obtains installed body, lead, selection,
pad, and probe geometry from the registered `PhysicalPartRenderer`; it does
not synthesize a second component shape for drawing, hit testing, or marker
placement. `PhysicalPartRenderContext` exposes the exact placed package
surfaces, and `PhysicalPartRenderTerminal` keeps board-pad and component-side
lead surfaces distinct.

For a connected lead, the board pad is the physical probe target and the
component-side lead is not exposed. Lifting a lead preserves the board-pad
target and exposes the detached component-side surface as a
`ComponentLeadProbeTarget`. Both surfaces use the provider-declared bounds and
points, so selection, hit testing, probe creation, and measurement markers
agree with the rendered geometry. Loose/tray projections remain outside this
slice and are deferred to 43R-4.

Installed probe targets capture the renderer's observed physical projection
epoch, mounted slot/part identity, physical package identity, stable terminal
binding, and CircuitJS measurement endpoint. Removal, replacement, lead-state
change, or slot association change invalidates stale targets. Renderer
selection is also cleared when the selected component is no longer the exact
part mounted in its slot. Same-part reinstall preserves the stable physical
part, terminal, and endpoint identities but produces a fresh target for the
new projection epoch; a replacement part has a distinct physical identity.
The binding endpoint remains authoritative across orientation-aware replacement
retargeting, so an installed reversed LED or diode can still be isolated and
measured through its component-side targets without a name-based endpoint
re-resolution.

`PhysicalPartRenderGeometry` returns defensive copies of mutable rectangles,
and the focused Task 43 verifier covers provider dispatch, connected/lifted
surface semantics, terminal identity, selection/hit/probe agreement, removal,
reinstall, replacement invalidation, and board/runtime identity preservation.
No CircuitJS electrical topology, measurement primitive, routing algorithm,
or controller mutation semantics changed in this slice.

## Task 43R-4 — loose rendering, probing, and physical-part lifecycle

Task 43R-4 closes the tray-side consumers of the immutable package realization
contract. `LoosePartPose` is the one package-private immutable presentation
carrier for a loose part. When a physical part has a bound
`PhysicalGeometryRealization`, the pose uses that exact realization and its
`PhysicalPackageGeometry`; an unbound replacement uses the explicit
package-owned default loose geometry. The tray renderer no longer invents
synthetic terminal positions or reads a second package shape.

The pose records source realization/geometry, physical orientation, polarity
mirror state, the required quarter-turn metadata, one positive uniform scale no
greater than one, a translation, and the fixed tray cell. Its transform order
is package-local point/rectangle, optional polarity mirror, optional clockwise
quarter-turn for connector/developer-generic vertical packages, one uniform
scale, then one translation into the tray cell. The scale fits the existing
three-row tray without changing tray dimensions or package-local identity.

`PhysicalPartRenderContext` projects every loose feature through that pose:
body, selection and drag envelopes, pads, probe surfaces, component-lead
probe bounds, connected lead body/end/bounds, terminal markers, and hit
geometry. `PhysicalPartRenderGeometry` is the provider-owned source shared by
loose drawing, selection, hit testing, marker placement, and probe creation.
It validates that visible bodies, leads, pads, probe surfaces, and markers are
contained by the declared interaction envelope. Loose lead stroke width is
scaled with the same pose and is clamped to its transformed lead bounds.

All registered physical providers participate in the same contract: axial
resistor and diode, through-hole LED, TO-92 NPN/NMOS, radial electrolytic and
ceramic capacitors, connectors, and the multi-terminal developer canaries.
Pagination clears a selection that is no longer visible and keeps hidden probe
targets invalidated and cleared after 43R-4C; it does not migrate identity to
another tray part. Removal, replacement, and reinstall preserve the R3 slot/part/
terminal/endpoint lifecycle rules: a bound carrier is retained for the same
physical part, an unbound replacement falls back to the package default, an
installed replacement binds its carrier, and a removed replacement uses that
carrier while retaining its distinct physical identity.

The R4 verifier exercises the package/provider matrix and rejects independent
terminal warps, non-uniform body warps, visible features outside hit geometry,
giant empty-tray hit regions, markers outside declared surfaces, and
body/terminal transform mismatches with explicit failure reasons. No routing,
board placement, CircuitJS electrical topology, measurement primitive, fault,
stress/damage, or replacement-catalog behavior changed.

## Task 43R-5 — RC fixed-layout reconstruction

Recovery slice 43R-5 replaces the stale authored RC copper route after the
version-3 package escape correction. `RcDelayPcbLayoutFactory` keeps the live
RC logical topology and every fixed component anchor unchanged: J1 at
`(50,150)`, R1 at `(200,90)`, C1 at `(700,70)`, J2 at `(900,160)`, R2 at
`(500,290)`, and C2 at `(200,300)`. J2 remains the typed
`THROUGH_HOLE_OUTPUT_HEADER_2` package with `DEFAULT`/`IDENTITY` geometry.
The route is derived from the generated pad centers and declared v3 escapes;
it does not move components or duplicate package geometry.

The global route witness contains two VIN branches from J1.1, three RC_OUT
branches from R1.2, and four GND branches from J1.2. VIN uses the R1.1 lane
at y=190/120 and the C2.1 lane at y=190/275. RC_OUT leaves the R1.2
escape, rises to the y=47 upper lane, and branches to C1.+ and J2.1; its
R2.1 branch drops through the span-specific x coordinate at y=290. GND uses
the lower y=400/y=365 trunk, then rises through the C1.- and J2.2 v3 upward
escapes at y=62; the C2.2 branch uses y=290 and the R2.2 branch uses the
lower return lane. The three R1/R2 axial spans (220, 240, 260) use the
approved finite branch forms, including the direct x=480 transition for
the 260/220 case, without duplicate or zero-length segments.

The route factory still runs the generic `PcbBoardLayout` oracle after
compaction. Compaction applies one checked rigid translation to all geometry
and uses the four existing seed-origin classes, so component/package
identity and route fingerprints remain deterministic while the board stays
content-fitted. The corrected C2 and J2 escape lengths are consumed from the
package pads rather than reintroduced as route constants.

The closed R5 witness covers all nine structural span combinations and all
four compaction-origin classes, with complete VIN/RC_OUT/GND physical
connectivity, full-width courtyard containment, unrelated-net clearance,
silkscreen/body/pad separation, route-quality limits, and deterministic
fingerprints. The in-app fixed-layout verifier reports `PASS:layout`. The
RC electrical/stored-energy browser route remains limited by the accepted
43R-4C renderer debt (`Renderer omitted disconnected component-side lead:
C1.+`); Task 40/41 corpus routes likewise stop at their deferred NPN fixed
layout. These are validation boundaries, not R5 route changes. No generic
validator, renderer, electrical model, fault model, NPN, NMOS, or Task 44
code changed.

## Task 43R-6 — NPN fixed-layout reconstruction

Recovery slice 43R-6 replaces the stale authored NPN copper route after the
version-3 package escape correction. `NpnLowSideSwitchPcbLayoutFactory`
preserves the live NPN topology, component anchors, package providers, and
CircuitJS node identities. The route is reconstructed globally from the
current pad centers and declared package escapes; no generic validator,
renderer, placement, geometry contract, or electrical behavior changed.

The fixed board has J1 at `(170,150)`, J2 at `(170,570)`, RLOAD at
`(330,100)`, RB at `(530,530)`, RPD at `(280,380)`, LED1 at `(620,140)`,
and Q1 at `(970,190)` before compaction. Each resistor selects one of the
three canonical axial spans `SPAN_220`, `SPAN_240`, or `SPAN_260`, so the
explicit structural matrix is 3 × 3 × 3 = 27 resistor tuples. The nine
authored traces are the two supply/control paths, load path, collector path,
two Q1-base branches, and the four-way J1.2 ground tree:

- `LOAD_SUPPLY`: J1.1 to RLOAD.1;
- `CONTROL`: J2.1 to RB.1;
- `LOAD_NODE`: RLOAD.2 to LED1.A;
- `COLLECTOR`: LED1.K to Q1.C;
- `BASE_RPD`: Q1.B to RPD.1;
- `BASE_RB`: Q1.B to RB.2;
- `GND_J1_J2`: J1.2 to J2.2;
- `GND_J1_RPD`: J1.2 to RPD.2;
- `GND_J1_Q1`: J1.2 to Q1.E.

The base routes use the upper y=36 lane and separate y=330/y=470 branches.
The ground tree uses the translated x=60/y=430 trunk, a y=440 RPD detour,
and the emitter branch's x=214 separation from the RPD branch. The paths
have no duplicate, zero-length, self-intersecting, or courtyard-crossing
segments; the closed witness records minimum distinct-net centerline
separation of 16 units. Compaction remains a checked rigid translation and
preserves package/variant identity, route membership, and deterministic
fingerprints across its four origin classes.

`NpnFixedLayoutDeveloperVerifier` executes all 27 canonical resistor tuples
across all four compaction-origin classes, for 108/108 cases. It uses the
live generator fixture, canonical package objects, exact nine-trace endpoint
and membership assertions, the real `PcbBoardLayout.validateGeometry`
oracle, deterministic duplicate fingerprints, and normalized translation
equivalence. The NPN ground-tree verifier now derives its checks from the
placed, compacted J1.2/J2.2 pads rather than stale absolute coordinates.
At the end of 43R-6, the build and developer outputs were
`PASS:NPN_FIXED_LAYOUT_MATRIX`, `PASS:npn`, and `PASS:task43`; the NMOS
fixed-layout frontier was then deferred to 43R-7.

## Task 43R-7 — NMOS fixed-layout reconstruction

Recovery slice 43R-7 replaces the abandoned NMOS authored copper route after
the version-3 package escape correction. `NmosLowSideSwitchPcbLayoutFactory`
preserves the live NMOS topology, fixed component anchors, canonical package
objects, stable board/component/pad/net identities, and CircuitJS endpoint
bindings. The route is derived from named pad centers and package-declared
escapes; no generic geometry, validator, electrical, measurement, fault,
stress, replacement, RC, NPN, or Task 44 implementation changed.

The approved un-compacted anchors are J1 `(80+s,80)`, J2 `(80+s,400)`, RLOAD
`(350+s,200)`, RPD `(300+s,320)`, LED1 `(500+s,70)`, and Q1 `(900+s,100)` on
the existing 1300x680 canvas, with `s=10*m` for four origin classes. RLOAD
and RPD independently select `SPAN_220`, `SPAN_240`, or `SPAN_260`. The eight
traces are inserted in this order: LOAD_SUPPLY, LOAD_NODE, DRAIN,
CONTROL_INPUT to RPD.1, CONTROL_INPUT to Q1.G, and the three J1.2-rooted GND
branches to J2.2, RPD.2, and Q1.S. The DRAIN upper lane is dynamically 20 units
right of the LOAD_NODE resistor escape lane. Q1 has no internal terminal
connectivity, so all three control/load-return unions are physical copper.

After labels and copper are added, the factory applies the established rigid
compaction contract `compactToContent(40+s, 30+(m%2)*10, 26)`, positions the
parts tray disjointly, and runs `validateGeometry`. The developer-only fixed
factory overload and `NmosFixedLayoutDeveloperVerifier` cover all nine
resistor tuples across all four origin classes, for exactly 36 cases. Each case
checks the coordinate witness, endpoint order, logical membership, real
physical-union oracle, package/escape parity, route quality, clearance,
deterministic duplicate fingerprints, and normalized origin-class geometry.

The normal in-app developer results are `PASS:NMOS_FIXED_LAYOUT_MATRIX`,
`PASS:layout`, `PASS:task43`, and `PASS:nmos` for the existing nine NMOS
electrical/control/mutation checks. 43R-8 is now the next eligible milestone;
it was not started here.

## Task 43R-5A — RC fixed-layout acceptance-proof closure

43R-5A is a corrective acceptance-proof slice, not a route reconstruction.
`RcDelayPcbLayoutFactory.create(TroubleshootBoard,long)` and the new
developer-only `createForDeveloperVerification` seam share one live private
`createLayout` path. Production retains the real seed and null resistor keys;
the finite developer path selects `SPAN_220`, `SPAN_240`, or `SPAN_260` for R1
and R2 while passing the variation mode as its seed for the unchanged
non-resistor provider choices. All six existing RC anchors, board dimensions,
trace coordinates, labels, compaction, parts-tray placement, and the final
`validateGeometry` call are unchanged. Explicit resistor realization uses only
the canonical `AXIAL_RESISTOR` package geometry variants.

`RcFixedLayoutDeveloperVerifier` is a hard Task 43 gate over exactly
`3 x 3 x 4 = 36` live cases. It validates the live RC board before the matrix,
the canonical package/variant/transform and physical surface identities, the
ordered nine-trace VIN/RC_OUT/GND witness, exact logical memberships, physical
union through the generic layout oracle, escape metadata, route quality,
deterministic full fingerprints, normalized origin-class geometry, seam
negative canaries, and production parity for seeds 0–3. Its successful result
is `PASS:RC_FIXED_LAYOUT_MATRIX:cases=36/36;variantTuples=9;originClasses=4`.
The two RC-specific deferred failures were removed from
`PcbLayoutDeveloperVerifier`; the existing NPN deferrals remain unchanged.

This correction does not claim Task 43 or 43R-8 complete. 43R-8 remains the
next unstarted acceptance/regression/cleanup milestone. The existing RC
electrical/stored-energy browser route may still report the accepted renderer
boundary for the disconnected C1 positive component-side lead; that is outside
this fixed-layout acceptance proof.

## Historical pre-acceptance record — Task 43R-4D — detached installed lead renderer/probe corrective closure

43R-4D classifies the remaining `Renderer omitted disconnected component-side
lead: C1.+` failure as an `IMPLEMENTATION_FAILURE` in the generic developer
render verifier's state dispatch. It is not a renderer geometry, package,
target-construction, endpoint, RC route, or electrical defect. A lifted lead
disconnects its CircuitJS connection while `PhysicalCapacitorPart` remains in
its `PhysicalBoardSlot`; `part.isInstalled()` and
`PhysicalPartRenderContext.isInstalledPartMounted()` therefore remain true.
Physical removal is a later slot operation: the installed part becomes null
and uninstalled, while the original part is projected through the loose/tray
provider. `ComponentPhysicalState.REMOVED` by itself is graph state and is not
physical mount authority.

The generic installed projection contract is now verified with the following
state distinction:

| State | Physical authority | Installed interaction |
| --- | --- | --- |
| `CONNECTED` | slot owns the installed part and the part is mounted | connected lead pose; `BoardPadProbeTarget`; no component-side target |
| `LEAD_LIFTED` | the same slot/part identity remains mounted while one or more electrical leads are disconnected | package-declared lifted pose; board pad remains probeable; detached lead exposes a distinct `ComponentLeadProbeTarget` and physical terminal endpoint |
| `REMOVED` | slot is empty and the part is uninstalled | installed component-side point/target/hit is absent; board pads remain; loose provider owns a fresh tray target |
| `REINSTALLED` | the same part/carrier/terminal/endpoint may be mounted again | stale installed targets remain invalid; a fresh projection target is required; fully connected reinstall exposes board-side targets only |

The candidate `PhysicalPartRenderDeveloperVerifier` derives mounted state from runtime
installed-part identity, slot identity, `part.isInstalled()`, and
`part.getBoardSlot()` independently of electrical connection state. Its
removed-state branch explicitly rejects an installed component-side point or
hit and then checks loose ownership. Its lifecycle canary prefers a
deterministically selected installed two-terminal resistor/non-capacitor (the
generated LED family's real `R1` path when available), with `C1` as the RC
fallback. The same helper exercises both terminal positions, including the
explicit RC `C1.+` regression while C1 remains mounted and lifted.

The detached loose-projection fixture assigns distinct electrical backing-wire
coordinates to each concurrently inserted canary part. Those coordinates do
not affect tray geometry or terminal identity; they prevent unrelated loose
parts from creating accidental shared nodes and parallel wire loops during
the real measurement and solver-restoration checks.

The canary preserves positive connected/lifted/reconnected/graph-only-removed
behavior and checks board-pad precedence, provider point/bounds and marker
agreement, non-overlapping board/component probe surfaces, target-class and
endpoint distinction, physical-part identity, stale-target invalidation,
physical removal and loose transfer, same-part reinstall freshness, wrong
physical identity, wrong component/pad binding, and replacement-endpoint
rejection. The replacement negative uses the existing generic catalog mutation
provider: the old lifted target must retain its original physical endpoint and
be invalid after a new physical part is installed, while the replacement gets
a new physical-part/terminal target. `BoardPadProbeTarget` continues to
represent the stable board endpoint; `ComponentLeadProbeTarget` represents the
mounted physical terminal endpoint. No production
renderer/context/provider/geometry/target class changed, and no synthetic
geometry or endpoint was introduced.

The verifier now also runs exactly two installed-path adversarial negatives from
a detached one-component fixture. `verifyInstalledProbeOverlapNegative` wraps
the standard installed provider with a mutable terminal copy whose same-
terminal board-pad and detached component probe surfaces completely collapse,
then invokes the real installed geometry and `PcbWorkbenchRenderer` hit path
over the collapsed surface. Every hit must remain a valid `BoardPadProbeTarget`
for the declared `boardPadId`; no valid component-side target may be exposed.
`verifyInstalledDetachedMarkerNegative` obtains the real installed projection
and exercises `PhysicalPartRenderTerminal` with a mutable copy whose detached
marker is outside its declared component-lead probe, requiring the exact
`Physical render component probe omits its center` rejection. The fixture swaps
only its detached instance/modification view for the duration and restores the
live simulation state; canonical package geometry and the live registry/tray,
selection, and power state are untouched.

This is an unaccepted Task 43 recovery correction candidate only. The source
build/link and static renderer-boundary check pass, but the mandatory
compiled-preview runtime routes could not run because the Edge harness was
blocked by WMI access denial and the in-app preview remained a blank GWT shell.
The initial independent final review identified the two installed-path negative
fixtures; this correction adds them, but no runtime acceptance is claimed and
no commit or push was authorized.
RC routing, electrical topology,
measurement/fault/stress/replacement behavior, NPN/NMOS routes, and Task 44
remain untouched. The production compile/link and static renderer-boundary
check pass in the current candidate; the compiled-preview route result could
not be claimed in this environment because the Edge harness's WMI process
inspection is denied and the in-app local preview remained at a blank GWT
bootstrap shell.

### Historical pre-final-review 43R-4D acceptance-evidence update — 2026-08-22

The preceding paragraph records the earlier blocked harness attempt. A clean
compiled `war/` was subsequently served at `127.0.0.1:3000` and exercised by
the supported in-app Browser. The final source/build and renderer-boundary
checks passed; the Task 43 route returned `PASS:task43`, the layout route
returned `PASS:layout`, RC returned `PASS:rc` for seeds 0/2/3, stored-energy
returned `PASS:stored-energy` for seeds 0/2/3, and the combined RC/stored-energy
route returned `PASS:rc`. No Browser error logs were observed. Visible
normal-player interaction also observed power toggle, R1 selection, lead-lift,
and reconnect state transitions.

The replacement-endpoint verifier negative is isolated to a disposable
generated board so catalog-acquired replacement parts cannot alter the live
board identity or inventory. 43R-4D remains pending the fresh independent
final Luna MAX review; no acceptance or commit is claimed by this update.

### 43R-4D final acceptance — 2026-08-22

The fresh independent read-only Luna MAX review returned `PASS`, so the
43R-4D installed renderer/probe closure is accepted. The final JDK 8/GWT
build, renderer boundary, Task 43 installed positive/negative verifier,
layout, RC seeds 0/2/3, stored-energy seeds 0/2/3, and combined RC/stored
runtime lanes passed through the clean supported in-app Browser route. Visible
normal-player power, selection, lead-lift, and reconnect transitions also
passed with no Browser error logs. 43R-8 is next eligible and remains
unstarted, and Task 44 remains blocked and unstarted.

## Historical Task 43R-3 corrective return — lifted component endpoint identity

The 43R-3 corrective return closes a verifier boundary defect exposed before
43R-8B. A mounted physical part owns stable `PhysicalPartTerminal` and
physical-part identity across lead lift, reconnect, graph-only removal,
physical removal, and same-part reinstall. The installed component-side
measurement target remains authoritative to the current
`GeneratedComponentConnectionBinding`, whose endpoint may be retargeted for
replacement and orientation-aware LED/diode installation.

The generated R1.1 path can hold two independently allocated
`CircuitPostMeasurementEndpoint` wrappers for the same CircuitJS element/post:
one from the physical part and one from the binding. Those wrappers are
electrically equivalent but are not Java-reference identical. The verifier now
uses its existing semantic post comparison only at that cross-owner boundary.
It still requires exact target-to-binding identity, physical part/terminal and
carrier identity, slot/mount state, lifecycle invalidation, board-pad
identity/separation, and replacement non-migration. No production renderer,
probe-target, binding, physical-terminal, measurement, fault, or loose-part
lifecycle behavior changed.

The corrective source/build, renderer-boundary, complete Task 43,
Task 39/40/41 regression routes, and independent read-only Luna MAX review
passed. The 43R-8B candidate remains preserved separately and is not part of
this correction. At that historical checkpoint, 43R-8B and Task 44 remained
unstarted; the later 43R-8B closure is documented below.

## Task 43R-8B — acceptance-infrastructure closure

43R-8B closes two acceptance-infrastructure gaps without changing the
electrical, lifecycle, or player-facing architecture. The general layout
verifier no longer swallows the two superseded NPN fixed-layout failures as
`DEFERRED`; current NPN geometry is a hard failure, while the independent
`NpnFixedLayoutDeveloperVerifier` remains the authoritative finite 108-case
proof.

The browser verifier includes a developer-only forced-negative Task 43 route
used to prove process integrity. After the real Task 43 aggregate runs, the
route deliberately publishes the normal
`FAIL:task43-forced-negative-canary` DOM state. The shell accepts only the
anchored, deliberate CDP diagnostic associated with that exact state, reports
`EXPECTED FAILURE`, and exits nonzero. Unexpected console or application
failures remain rejected, infrastructure failures remain distinct, and the
forced query is not a normal-player path or persistent board state. Ordinary
Task 43 remains a `PASS` route with exit `0`.

The acceptance-infrastructure build, renderer boundary, layout, NPN matrix,
Task 39/40/41, ordinary Task 43, forced-negative, post-negative, and
independent Luna MAX review gates passed. This section does not mark final
integrated 43R-8 acceptance complete; that remains the next eligible recovery
slice, and Task 44 remains blocked and unstarted.

## Task 43R-8 — final integrated acceptance

Final integrated 43R-8 closes the remaining Task 43 recovery orchestration
without changing the production electrical, PCB, package, or player-facing
architecture. The committed Task 41 correction remains the narrow production
contract repair: the developer-only diode-short generator path propagates
`includeDeveloperShort` into the generated scenario and candidate accounting.
Normal diode generation remains unchanged, and Task 41 continues to derive its
counts from admitted candidates rather than from a verifier-side exception.

The uncommitted acceptance candidate is verifier-only. Its integrated child
wrapper preserves the existing quoted nested PowerShell command and captures
the child success flag and numeric `$LASTEXITCODE` immediately after the child
command. A successful child, including natural script fall-through, maps to
exit `0`; an unsuccessful child with an explicit numeric exit preserves `1` or
`2`; an unsuccessful child with no numeric status maps to infrastructure exit
`2`. Expected-exit matching and positive-route `FAIL` rejection remain strict.
This keeps the forced-negative application canary distinct from missing-browser
infrastructure and prevents a successful child from being misclassified.

The final acceptance matrix passed the compiled local preview across the
geometry, layout, Task 39/40/41, RC/stored-energy, NPN/NMOS, LED/diode/parallel,
legacy, Quick Play, and normal-player routes. It included the forced-negative
expected exit `1`, missing BrowserPath expected exit `2`, and post-process
natural-success expected exit `0` canaries. Direct Task 41 and diode-short
regressions passed for the requested seeds. No Task 44 implementation was
started; Task 44 remains blocked and unstarted.

## Post-Task-43 Gate B — verifier isolation and mainline protection

The accepted Gate B implementation is the verifier-isolation and mainline-CI
baseline on top of the canonical post-Task-43 baseline
`9dc06141190da3a44ebe12015a4f5656f0f40ef5`. It is a
validation/infrastructure boundary only: CircuitJS remains electrical truth,
generated topology and PCB behavior remain unchanged, and no second solver or
synthetic meter/browser result was introduced. The implementation passed the
independent Reviewer, Foreman, and Sol Inspector gates and was published with
the completed Gate B task. At that historical publication checkpoint, Task 43P
was next eligible; current status is recorded in the section below.

### Per-run ownership model

`scripts/VerifierIsolation.psm1` is the single ownership implementation used by
all `scripts/verify-browser.ps1` route families, including the specialized
normal-player routes and `-Task43Integrated` child routes. Each invocation
creates a context with:

- an opaque GUID `runId` and a repository identity derived from the absolute
  script/repository root;
- a unique `%TEMP%\TroubleshootJS\verify\<repository>\<runId>` run root and
  manifest;
- a unique evidence directory, including a `run-<runId>` child when an
  explicit `-EvidenceDirectory` is supplied;
- collision-safe loopback port leases, with a global per-port named mutex and a
  run-scoped claim record held through bind and exact cleanup; ordinary ports
  `8888`, `8898`, `8899`, and `9876` are excluded from verifier allocation and
  are never treated as ownership evidence or allocator fallbacks;
- run-owned preview state/logs, process start identity, script/port identity,
  and the `/__tsj/verify-identity` handshake; or an explicitly supplied
  caller-owned preview that must prove the repository root, preview script,
  web root, protocol, and loopback port;
- a unique CDP port lease, profile below the run root, route/run markers,
  bounded target attachment including a route-deadline-bound WebSocket
  handshake, and recorded PID/start identity; and
- manifest records for ownership, PID/start identity, port, profile, target,
  evidence, leases, logs, and cleanup results.

Cleanup stops or deletes only resources whose run identity, path, process start
identity, command/script identity, and port/profile ownership remain provable.
It never broad-kills Edge, adopts a foreign preview, removes another run's
profile/evidence, or falls back to a shared fixed port/profile when allocation
or ownership proof fails. Unsafe ownership, target/port/profile mismatch,
timeout, tool, and cleanup errors are retained in the run manifest/evidence
and classified as verifier infrastructure failure.

Port allocation is an atomic retained claim, not an ephemeral-port probe. The
allocator first holds the candidate loopback bind while it acquires a global
per-port named mutex and creates an exact run-scoped claim record containing the
worktree, run, and claiming verifier PID/start identity. The mutex remains held
through the external preview/Edge bind and is released only by the same owner
after exact cleanup. A successful preview identity or CDP target bind changes
the manifest claim state to `bound`, records the exact listener PID/start
identity, and rejects a foreign listener. A competing process in another
worktree cannot acquire the requested port while that claim is held; an
abandoned or mismatched claim is never adopted. Claim-file and manifest
failures roll back the exact file, map entry, mutex ownership, and handle before
returning infrastructure failure; partial claim data is not adopted.

The Gate B competing-worktree canary launches a real Windows PowerShell child
with `System.Diagnostics.Process` stream capture. It waits, refreshes, proves
`HasExited`, and reads `ExitCode` using the Windows PowerShell 5.1-compatible
path; an unavailable exit code is infrastructure failure rather than a
contract pass. Expected infrastructure exit `2` with an observed `0` or `1`
is also infrastructure failure. The child reports a structured cleanup record
covering its manifest, lease ledger, profiles, claim paths, and exact-port
absence. Captured child output remains in the canary evidence until all owned
cleanup succeeds.

All verifier-started processes use the shared
`ConvertTo-VerifierWindowsArgument`/`Start-VerifierProcess` path (or the
equivalent redirected `ProcessStartInfo` path using the same argument
builder). This is the Windows PowerShell 5.1 command-line boundary for browser,
preview, and child-script/profile paths. The deterministic canary performs an
actual launch from temporary worktree and profile paths containing spaces and
proves termination before deleting that exact temporary root.

The shared command-line parser keeps exact Windows token boundaries for all
ownership markers. After a token is matched, path-valued identity values such
as `--user-data-dir` and `-File` are compared as absolute Windows paths with
separator, `.`/`..`, trailing-separator, and case differences normalized.
Opaque repository identity values remain ordinary exact values. The same
canonical path key is used for repository identity hashing, worktree/script/
profile/claim/run comparisons, preview state validation, stale-claim recovery,
and stop-preview checks. The final profile-quiescence scan uses that key, so a
late differently named helper using an equivalent foreign profile path blocks
deletion and lease release.

Listener inspection returns an explicit success/known/has-listeners result.
Nonzero, empty, malformed, or error-text netstat output, incomplete listener
records, and unknown listener PID/start identity are infrastructure failures;
they are never interpreted as proof that a port is free. A claim is released
only after exact loopback-listener absence is positively known. Browser profile
cleanup scopes the Win32 process snapshot to relevant browser executable
candidates and exact readable run/profile/remote-port markers. Irrelevant
PID-zero, null-command, or unrelated transient records do not invalidate the
run; every relevant candidate must still provide PID, parent PID, readable
command line, and current start identity. Missing, stale, inaccessible, or
changed relevant data retains the profile and claim, including when the
recorded browser PID is zero or gone. A PPID relationship alone never authorizes
termination: `Complete-VerifierBrowserSession` first establishes the canonical
session/run root and one immutable owner-root record; the browser root must
match the resolved `BrowserPath` by exact
current executable name and nonblank canonical `ExecutablePath`; an inferred
descendant must also expose a nonblank current `ExecutablePath` that canonically
matches an explicitly supported browser descendant executable identity, the verified current
PID/start/parent ancestry, and any
root-owned markers that it actually carries. Real Chromium/Edge renderer,
utility, GPU, and helper descendants are not required to repeat every root-only
verifier switch; markerless helpers are admissible only through that complete
ancestry plus executable proof. An unsupported executable, reparented/PID-reused
node, unknown identity, or conflicting marker is left running and the run is
retained as infrastructure failure. Once an owned root is being traversed, the
candidate graph is built from the complete process snapshot, so differently
named helpers cannot be filtered out before their exact identity is checked.
Immediately before each termination, the verifier re-queries the exact current
`Win32_Process` record and compares PID, parent PID, command line,
run/profile/remote-port markers, executable identity, and current start identity
with the discovery record. It stops only the revalidated process object; a PID
disappearance, replacement, access failure, or mismatch retains the run and
returns infrastructure failure.

The descendant policy also recognizes Edge's `identity_helper.exe` only at the
exact versioned sibling path derived from the configured `msedge.exe`. Both
files must have matching four-part versions, expected original filenames,
valid Microsoft signatures, and the same signer. Physical paths must have no
reparse ancestors. This companion must explicitly carry this run's profile,
the utility process type, the Windows app-ID service subtype, and the Windows
package-identity sandbox type. It still requires the complete current ancestry
and process identity proofs above. The browser root and listener identity
checks continue to require the configured browser executable itself.

The browser cleanup drain keeps the verified root alive while it repeatedly
captures a complete current process snapshot, expands exact root ancestry,
and stops verified descendants deepest-first. It requires a bounded fixed
point of empty graphs before stopping the root, then rechecks the complete
post-stop graph and exact children of every known parent. A late,
markerless, reparented, inaccessible, or otherwise unknown descendant blocks
release and retains the profile, claim, manifest, evidence, and run root; the
profile-bearing quiescence scan is never the sole cleanup proof. The
WMI-backed late-markerless canary creates a same-executable helper only after
the initial empty graph capture and asserts either fixed-point cleanup or a
retained typed infrastructure result. The root-alive markerless-helper and
root-gone retention canaries remain separate proofs.

Root disappearance is fail-closed. A two-view absence of the recorded browser
root is not a termination or descendant-cleanup proof: if the root is gone
before its complete descendant graph has been revalidated, the verifier skips
profile/claim/root deletion, marks the lease blocked, retains the manifest,
evidence, profile, and claim, and returns typed infrastructure exit `2`. The
only release path is a separately proven exact recovery after all owned
processes and listeners are absent; the verifier never broadens cleanup to
profile guesses or reused PIDs. The WMI-backed root-gone canary launches a
root plus a differently named markerless helper, signals the root to exit,
asserts that the helper and resources remain retained through the failed
cleanup/drain, and then recovers the fixture only after the helper's natural
exit. The normal root-alive canary separately preserves markerless
same-executable helper cleanup.

`Resolve-VerifierBrowserPath` is the one browser resolver used by the verifier
and the opt-in supplemental live ownership canary. It checks an explicit configured path, PATH,
`ProgramW6432`, `ProgramFiles`, `ProgramFiles (x86)`, and the supported local
Edge installation location. Missing browser or inaccessible process inspection
is infrastructure exit `2`, never a skipped or fabricated ownership pass. The
deterministic default driver proves markerless same-executable ancestry,
requires a nonblank descendant `ExecutablePath`, and rejects the missing-path,
different-name, reparented, and PID-replacement records. The separate opt-in
supplemental real-Edge canary launches the resolved Edge executable and proves
helper, profile, claim, listener, evidence, and run-root cleanup when WMI is
available. It returns typed infrastructure `2` without printing PASS when Edge
or WMI is unavailable. This canary supplements and does not replace required
visible Browser validation.

Every run-owned browser root and its paired CDP ledger entry carries a mandatory
nonblank, canonical resolved `BrowserPath` executable identity. The resolver is
threaded into the initial lease/manifest write and parent/child ledgers; a
missing, stale, or mismatched value fails infrastructure exit `2` before root
ownership, adoption, or termination can be attempted. The integrated ledger
reader independently requires the same identity on both sides of each browser
lease/profile relation. Caller-owned previews remain a separate verified,
non-owned class and do not acquire this browser-root cleanup obligation.

Run-owned previews receive both the run ID and a unique nonce. The identity
endpoint must echo both values, the recorded PID/start identity, and the port;
the verifier also checks the recorded command line for the exact script, run,
nonce, and port before it will stop the process. The process handle/PID is
recorded immediately after launch, before fallible start-identity capture. If
identity or delayed-bind proof is uncertain, cleanup refuses lease release and
retains the process record, claim, manifest, and evidence unless exact process
termination/absence and listener absence are proven. An explicitly supplied
caller-owned preview must be identity-verified but has no run ownership or
cleanup fields and is never adopted or killed. Its ledger and manifest proof
fields are checked as exact JSON Booleans: `identityVerified` and `callerOwned`
must be `true`, while the non-owned process/termination/listener proof fields
must carry their explicit not-applicable values; strings such as `"false"` or
`"true"`, numbers, and other truthy values are infrastructure failures. CDP transport/protocol errors,
deadline expiration, target/attach failures, and verifier harness failures use
the infrastructure classification; only a verified application `FAIL` or the
deliberate Task 43 negative marker uses application exit `1`.
The WebSocket handshake itself is cancellation-bound to the route deadline and
its socket is aborted/disposed on timeout or protocol failure. Explicit verifier
result data carrying `VerifierExitCode=2` is also infrastructure even if a
producer omitted the typed failure-kind marker; exit severity remains monotonic.

Context, evidence, lease, and initial-manifest setup is also inside the
infrastructure classification path. When a run root exists but setup cannot
finish, a minimal `setup-failure.json` is retained there when possible, and
cleanup refuses to touch paths whose ownership cannot be proven. Browser
profile cleanup always scans all process command lines for the exact profile,
including when the recorded browser PID is zero or stale; inspection failure
or a referencing process retains the profile/claim and returns infrastructure
failure. A final lease-ledger walk retries every still-owned lease after
partial startup/cleanup.

`Complete-VerifierRun` reports unsuccessful cleanup results instead of
swallowing them and drains every still-owned lease record. Port release first
persists a durable `releasing`/`os-released` state, then releases and disposes
the named mutex, and persists a `complete` + `delete-pending` tombstone before
removing the exact claim file. Only after exact deletion is proven does it
persist terminal `complete` + `released` state. After `os-released` or
`complete` is durable, recovery validates and removes only the exact old claim;
it does not require the port to be currently absent, because a newer legitimate
run may already have rebound it. Recovery with a missing claim plus the durable
marker is likewise idempotent and never deletes a newer claim.
injected release, disposal, post-delete interruption, final-manifest, or
claim-delete failures retain the tombstone for retry/diagnosis and cannot
report complete. Gate B canaries
retain their run roots, manifests, logs, and evidence whenever a cleanup
exception, uncertain ownership, live listener, or unreleased claim remains.
Listener startup/stop paths use exact PID/start and post-stop listener proof;
an uncertain child/process cleanup prevents recursive deletion. Recursive
canary-root deletion occurs only after the exact claim/profile/listener and
lease-ledger proofs succeed; the deterministic cleanup-retention canary
exercises a live-listener failure followed by a safe retry. Integrated child
routes receive a parent-visible ledger containing the child run root, manifest,
claim/profile ledger, and cleanup state. A timeout proves only the exact child
PID termination; if the child finally cannot be proven, its owned resources and
ledger remain retained rather than being broad-killed or recursively deleted.
For every normally terminated child, including a child whose expected result is
infrastructure exit `2`, the parent requires ledger state `completed` with
complete cleanup and released leases/profiles. The ledger reader requires
canonical run-root/manifest/evidence paths under the parent namespace, matching
run/worktree/repository identities in the child manifest, positive listener
absence for every released claim, no claim files, absent profiles, and complete
run-owned-server termination proof. It rejects malformed, foreign-path,
stale-claim, cleanup-failed, and completed-zero-resource ledgers unless the
zero-resource case has the real canonical manifest/evidence proof. Only the
explicit parent-timeout path may accept `started`/`resource-started` state, and
that path validates retained canonical manifest/evidence/claims/profiles before
returning infrastructure failure; it can never report success from an
incomplete ledger.
Completion flags are not accepted as proof: for every completed lease/server/
profile the reader independently re-queries the recorded PID/start/parent/
command identity where present, the exact loopback listener records, and the
relevant run/profile process graph. A missing, replaced, inaccessible, or
inconsistent query is infrastructure failure; a newer listener is treated as
port reuse only after the old identity is positively distinguished and is never
adopted or deleted. Ledger leases are limited to `preview`/`cdp`, use the exact
global mutex derived from a port in the `1..65535` range and the canonical
`<port>-<leaseId>.lease` claim path, and every active/nonterminal lease and
retained claim must carry a positive owner PID/start identity. Duplicate live
ports or derived mutexes are rejected even when IDs and claim paths differ;
same-port reuse is accepted only after a durable terminal release and no live
claim. Every browser profile is paired bijectively with a `cdp` lease (never a
`preview` lease) with the exact canonical profile, port, and run identity.
Run-owned server records must use `scripts\\preview.ps1`, the exact
`http://127.0.0.1:<port>` root, and direct `runRoot\\server` stdout/stderr logs.
An explicitly supplied caller-owned server is accepted only as a verified
non-owned record with exact repository root, `war` web root, identity protocol,
loopback root, and no process/lease/cleanup claim; it is never adopted or
killed. The deterministic ledger canary creates a real released lease, rejects
mutated cleanup flags, and exercises retained server records plus duplicate
live-port, invalid-port, owner-identity, wrong-profile-kind, caller-owned,
strict-Boolean ledger/manifest, custom-mutex, wrong-script,
non-loopback/path/query, wrong-port, and foreign-log variants.
Verifier and Gate B child/subprocess paths use bounded asynchronous
output/termination runners. The listener fallback routes `netstat.exe` through
a redirected, exact-PID runner; `build.ps1` uses its equivalent bounded
`ProcessStartInfo` wrapper for JDK/GWT while retaining stdout/stderr streaming
and logs. Successful exit/timeout canary assertions remove only their exact
temporary process namespaces; unexpected failures retain those logs/evidence.
A workflow-level timeout remains only a backstop.

The bounded subprocess runner captures positive creation-time ticks from its
retained `System.Diagnostics.Process` launch handle before starting output
readers. This launch-only boundary permits a naturally exited short-lived
child whose retained handle still provides its exact creation time. It never
adopts a process through a PID lookup. The shared ownership identity helper
continues to require a live process; numeric exit, complete output, exact
termination, and owned log cleanup still govern bounded-runner success.

Listener authorization and port-bind ownership proofs use one 500 ms monotonic
`Stopwatch.ElapsedTicks` budget per call, including the initial query or schema
inspection and every semantic authorization and current-process refresh.
Dependencies cannot return a positive proof after that deadline. The strict
port-bind query and bounded authorization refresh opt into the existing
validated netstat route through `-PreferNetstat`; ordinary listener callers
retain `Get-NetTCPConnection` as their primary provider. Both paths retain
exact current PID/start identity, listener schemas, and separate HTTP.sys
semantic authorization. Empty, malformed, failed, or otherwise unknown netstat
results remain infrastructure failures.

When every listener belongs to the exact browser root PID/start, a strictly
formed browser owner can omit the broad process snapshot during port binding
and downstream live listener authorization. Both boundaries use the same
eligibility predicate; each independently performs the existing PID-scoped
identity proof, which checks current process/start identity, executable, parent
PID/start, and run/profile/worktree/port markers. No positive live proof is
cached or transferred between calls. Descendant and mixed listener sets retain
their complete ancestry proof. This changes neither cleanup graph requirements
nor the total proof deadline.

Fixed-point browser cleanup pins a descendant's native process handle only
after complete live ownership proof. A module-private registry binds the
original drain scope, descendant record, Process object, native handle, and
immutable identity tuple. Copies or substituted objects cannot authorize
cleanup. If that exact handle later reports natural exit, its retained creation
time must still match and two independent current PID/WMI observations must
prove absence within one whole-call 500 ms monotonic budget. A live child still
requires the ordinary fresh ownership and exact-stop checks; an initially
unproved or missing child remains infrastructure failure. The scope releases
its retained handles on success and failure. Complete graph, root, listener,
profile, and claim cleanup requirements remain in force. This implementation's
current review and host-qualification status is recorded in the task report.

Repeated descendant discovery can encounter a child that was fully attested
in an earlier observation but has since exited. In that case the private
`Get-VerifierPreviouslyAttestedBrowserDescendantExit` helper reconciles the
candidate's exact parent/name/executable/command and any supplied start
identities against all bindings for that PID in the same live drain scope.
It returns only an original registered record after the existing retained-handle
and two-current-absence proof succeeds. Its whole-call 500 ms budget includes
the binding lookup and reconciliation. An initially missing or already-exited
child without that original capability still fails, as do altered capabilities,
identity changes, unavailable observations and late proof. The current parent
ancestry proof remains mandatory before admitting each candidate. The shared
live-process guard distinguishes an observed exit using its typed missing-PID
error; only that exact descendant's disappearance can consult a prior binding.
The Task 45 report records qualification of this bounded cleanup repair.

At already-selected missing-child failures, diagnostics include captured drain
and prior-attestation counts, sanitized Chromium process type/subtype, and
function names at the complete-snapshot boundary. These diagnostics add no
process-provider query or ownership authority and preserve the typed failure,
its PID and existing deadline/exit semantics.
The shared empty-command-line guard likewise reports validated child/parent PIDs,
active-scope and prior-binding counts, and up to twelve function names. Diagnostic
errors restore the original message before its unconditional infrastructure
failure. Counts describe captured state; they cannot establish ownership or exit.

For an attached browser, cleanup can request Edge's normal shutdown through a
new private browser-level CDP socket. The `/json/version` endpoint must resolve
to the exact leased loopback address/port and browser target. The owner record
must match the durable session. Current listener/root proofs and repeated full
descendant observations establish a stable set of retained identities before
`Browser.close`; any identity change fails closed. The wire reply must exactly
acknowledge that command. Each retained root/child handle then proves natural
exit, followed by the existing complete residual, profile, listener, and claim
checks. One 15-second monotonic budget includes preparation, transport, proof,
and resource disposal. Command attempts are recorded against immutable process
PID/start before sending; changing copied session labels cannot bypass them.
An unproven attempt blocks retry or force-stop fallback in that verifier process.
Unattached startup cleanup retains the descendant-first exact-stop path.
Unavailable preparation, malformed replies, late/unknown children, or failed
disposal preserve failure evidence and cannot qualify cleanup.

The 500 ms total proof budget is the current Task 43P trust-recovery acceptance
requirement. It bounds the complete observation/authorization attempt while
exact identity checks address PID reuse and changing ownership. It is not a
claim that Windows providers finish within that time on every host. Qualify the
actual query and ownership path on the selected host; a late or unknown proof
returns infrastructure exit `2`. Document measured provider limitations and
optimize the implementation within this budget. Changing the budget or its
coverage requires an explicit reviewed contract change, not a local timeout
increase to pass a gate.

The normal single-run developer workflow remains compatible: omit `-BaseUrl`
for an isolated run-owned preview, or pass the exact Preview root URL printed
by a running preview for an explicitly verified caller-owned server. The
Preview page URL is not a verifier BaseUrl; it is only the page-opening URL.
The legacy singleton
preview scripts remain caller-owned developer conveniences on their documented
ordinary ports; those ports are neither verifier allocator defaults nor proof
of ownership. The Gate B verifier does not adopt or stop caller-owned state.

### Exit and integrated-child contract

The verifier preserves the existing contract:

| Result | Process exit |
| --- | ---: |
| Verified application failure, including a genuine forced-negative Task 43 canary | `1` |
| Verified positive route | `0` |
| Browser/tool/port/profile/target/timeout/ownership/cleanup/toolchain infrastructure failure | `2` |

`-Task43Integrated` preserves expected child `0`, forced-negative child `1`,
and missing-browser/tool child `2`. A positive child that prints a `FAIL`
marker while exiting `0` is rejected as application failure. A child with an
unknown or unavailable failure status is infrastructure failure. The parent
does not convert a real application failure into a pass.

### CI and visible Browser boundary

`.github/workflows/windows-gate-b.yml` is the Windows mainline lane. It
explicitly selects Temurin JDK 8, verifies both `java` and `javac` report
version 8, runs the pinned GWT 2.7.0 compile path, asserts the generated
bootstrap/permutation artifacts, runs the renderer/provider boundary check,
and runs the parser, GWT XML, isolation, and 0/1/2 contract checks in
`scripts/verify-gate-b.ps1`. The exact job check name is
`Gate B Windows JDK8 deterministic verification`.

The default `verify-gate-b.ps1` invocation used by this job is deterministic
and nonvisual: it includes the deterministic isolation/ownership and contract
checks but intentionally does not invoke the live-Edge ownership canary. The
live-Edge proof is available only through the explicit
`-GateBRealEdgeOwnershipProbe` opt-in as a separate supplemental WMI/Edge
lane; its infrastructure exit `2` remains truthful and is not required to
make this deterministic check green. Neither lane replaces visible Browser
validation.

The workflow intentionally does not claim browser-bound visual verification
as headless or nonvisual CI. Required normal-player validation remains real,
visible Browser interaction through the supported in-app Browser boundary;
the CDP routes are supporting developer/regression evidence only. On a host
that denies Windows process command-line inspection, the profile ownership
canary reports an infrastructure limitation rather than fabricating a pass.
The Gate B driver maps required JDK selection, process/listener inspection,
setup, timeout, and harness failures to exit `2`; `-SkipJdkCheck` skips only the
JDK probe and does not waive the remaining infrastructure gates. Gate B is
accepted and published. Owner/admin configuration of `master` protection
remains an explicit external action and is not claimed here.

### Mainline protection — OWNER ACTION REQUIRED

The repository owner/admin must configure and then verify protection for
`master`: disallow force-push and branch deletion, require pull requests as
appropriate, and require the exact check
`Gate B Windows JDK8 deterministic verification` before merge. After the
workflow has produced a check, the owner must confirm that this exact job is
listed as required on a test pull request and record any GitHub-renamed check
context if the UI presents one. The Foreman observed that the current live
protection read was unavailable (`403 Resource not accessible by integration`);
preserved historical Gate A evidence records a separate earlier `401
Unauthorized` protection-read observation. These are time-separated evidence
observations, not a fabricated setting or an inference that protection exists.
The live workflows read was `404/not present`, master had no tracked workflow
at baseline, and recorded Actions runs were zero. No remote protection setting
was fabricated or mutated by Gate B.

## Current Task 43P evidence status

Task43P verification separates the historical baseline from the invocation's
candidate. `Task43PCandidateIdentity.ps1` freezes actual Git HEAD and validates
an explicitly supplied `-ExpectedCandidateSha`; an empty, malformed or mismatched
expectation fails closed. Source experiments pass the same SHA to the browser
child, and Gate B passes its frozen SHA to the source checks. Detached and
shallow CI checkouts use their actual checked-out HEAD. Historical `baselineSha`
is metadata; it makes no ancestry claim for a checkout without that history.

The wrapper records `candidateSha` separately and retains source/verifier hashes,
file counts, Git status and exact selected execution-tree fingerprints. Browser
repository capture checks the frozen invocation state across routes and after
cleanup. Disposable source/build roots remain separate from the wrapper's Git
checkout. The legacy forced-proof field `diagnosticBaselineHead` records the
candidate HEAD; its consumer compares it to the owning invocation, not a moving
literal. The [candidate handoff](task-evidence/task-43p/candidate-identity/README.md)
records focused checks, dependency reuse and the clean-HEAD receipt boundary.

C1 independently handles measurement removal, solver reconstruction, queued
power and synchronization; it retains primary/suppressed failures and visibly
isolates/stops an unproven graph. C2 asserts the supported Task41 snapshot
inventory, including `lastResistanceTestCurrent` with NaN-aware comparison,
and restores UI-sensitive captured values after refresh/restart.

The dedicated A–I route returns legitimate `0` with no open observations. Four
real measurement cases, seven cleanup canaries, non-default snapshot round trips,
the exact field sentinel and compiled omitted-assignment proof qualify. The
snapshot remains a fresh-candidate/detached-original proof; deep same-owner
transactions, generic epochs, timer identity and deep renderer/scope internals
are not covered.

Source experiments preflight all eleven anchors before compiling a selected
mutation. Gate B includes those eleven mutations and four restoration/rejection
canaries. Task43P's final page diagnostic runs before evidence capture; phase,
operation, timing and cleanup metadata diagnose failures without changing
ownership, the 500 ms proof budget, route deadlines or exit semantics.
The positive preflight BOM fixture verifies the actual `EF BB BF` bytes and
exact restoration. Its final test-only correction has fresh preflight/Gate B
proof; unchanged browser and compiled-source proof retains its original digests
under a separate dependency audit.

The [current reconciliation](research/TASK43P_RUNTIME_RECONCILIATION_2026-09-05.md)
and [acceptance index](task-evidence/task-43p/c1c2/acceptance-index.json) record the
build, Gate B, forced-first, A–I, eighteen physical fixtures, eleven compiled
falsifiers and visible LED3 proof, including reviewed dependency reuse. Failed
invocations retain their original status and separate cleanup dispositions.
Final packet review passed for the authorized publication and Owner Review handoff.
The owner subsequently approved `a5e8efa` and the bounded Tasks 44–45 sequence
on 2026-09-05. That approval does not certify runtime composition.

## Earlier Task 43P evidence status — before final runtime qualification

The current recovery candidate adds an opt-in `-Task43P -Task43PRuntime`
route with a fixed LED seed-3 entry board. Developer-only collectors exercise
existing mutation/meter/stress/stored-energy paths, one-shot exceptions inside
the real temporary-measurement implementation, paused customer completion,
fresh-session/reset ordering, and an actual scheduled repaint across an owner
switch. They restore the detached original owner and copy observations before
restoration. They do not implement production rollback or a new epoch owner.
The wrapper validates a separate run/route-bound `TSJ-TASK43P-RUNTIME-1`
packet. A complete observed application blocker returns `1`; missing or
contradictory evidence returns `2`, and any unproven cleanup overrides the
application result to `2`. This new route is under build/review qualification;
the historical partial route and evidence below do not establish its runtime
acceptance.

The evidence-only reconciliation pass is recorded in
[`docs/research/POST_TASK_43_INTEGRITY_RECONCILIATION.md`](research/POST_TASK_43_INTEGRITY_RECONCILIATION.md).
No production behavior changed in that pass. The supported Task 41 proof
boundary is Option A: evaluate fresh candidate boards with the original
workbench detached and restore the original owner. Option B, same-owner
nested transactional mutation, is unsupported.

Lifecycle settlement/epoch behavior, the independent physical
raw-copper/rendered-pad/solver triad, multi-owner rollback, active-measurement
cleanup, public-action reachability versus direct dispatch, and a complete
owner/state digest remain follow-ups. The evidence packet is not an acceptance
or unlock: missing runtime A-I lanes and the independent physical triad block
Owner Review; Task 43P acceptance is pending, and Task 44 remains blocked and
unstarted.

### Task 43P developer-only candidate route

The 2026-08-29 uncommitted repair candidate starts from published baseline
`20f83535163070a0688fcc0958715e6bc827d445` and retains a narrow query-gated route,
`tsjVerifyTask43P`, at the existing generated-board verification settlement
seam. `Task43PDeveloperVerifier` reads current lifecycle/owner state and
publishes bounded structured evidence; it does not become a runtime owner and
does not add a request, board, or session epoch. Its verifier-design state
fingerprint is evidence of current state, not a repository claim, rollback, or
transaction guarantee.

`Task43PPhysicalTruthDeveloperVerifier` is intentionally authored outside the
production board-binding and renderer metadata. It gathers independent raw
logical-board, raw `PcbTraceGeometry`, package, renderer, and live CircuitJS
post observations into source snapshots. One canonical validator performs
set-based completeness first, checks every trace endpoint, checks exact
package terminals/catalog pairs (including the mirrored connector realization),
and derives solver net membership from the independent manifest/raw board
rather than the binding-table net resolver. Positive observations and all
negative fixtures, including omitted-terminal, use that same validator.
The wrapper captures the DOM evidence attribute into a run-owned manifest and
owns the actual baseline/HEAD, dirty state, source SHA-256, file count, and
evidence path; Java/GWT repository-provenance fields are rejected.

Task 43P can explicitly select `-Task43PStartupSettleMilliseconds` (default
zero, maximum 45000) to let a fresh Edge profile mature before application
navigation. The wrapper uses a bounded CDP Promise, requires the complete
owned `about:blank` document, preserves the existing route deadline, and
records the selected duration in its evidence. This timing condition does
not relax current process identity, listener, or exact cleanup requirements.

`scripts/verify-task43p-source-experiments.ps1` mutates only isolated
disposable copies for renderer-only `J1.1 +20px`, raw-copper endpoint-gap
through the `GeneratedBoardInstance`/`PcbTraceGeometry` layout data, and
solver-binding/post redirection through `BoardSimulationBindings`. It records
exact byte hashes and restoration plus repository-state identity, compiles all
five GWT permutations, and attempts the compiled extraction route. The latest
three targeted runs each compiled with exit `0`, attempted runtime extraction,
and returned exit `2` because this host cannot construct
`System.Net.HttpListener`; the disposable preview was stopped and no
repository mutation remained. Browser-blocked experiments remain
`UNPROVEN`; disposable outcomes are not visible-browser acceptance evidence.

After the final wrapper hardening and validation, its repository-state audit
still showed the published baseline HEAD, source/verifier digest
`77d722d6ae2787ba7ed80bd3f28c9728f7bb0f2ec741ee18d4579c94fb01f3bf`, and
file count `855`; no committed-tree mutation was introduced.

The route emits explicit `PARTIAL`/`UNPROVEN` A-I statuses and returns exit 2
until the missing runtime sequences are independently proven. The current
Coder run could not reach the page because WMI denied exact browser ownership
and cleanup inspection; no runtime evidence is claimed. This developer route
is supplemental regression evidence, not visible built-in `@Browser` proof.

### Task 43P delta remediation — disposable ownership and fail-closed schema

**2026-09-05 source-negative protocol:** The current nine-case source harness
selects one compiled disposable family/seed for each mutation. The preview's
HTTP identity must equal the exact PID/start tuple from its launch handle,
and the current process/script/port is revalidated before the browser child.
Cleanup retains that original tuple and separately proves listener absence.
The wrapper's source-negative route accepts only the case's exact DOM marker
and validated Java diagnostic bound to nonce/run/route/request/execution and
experiment identity. It writes separate durable proof after final verifier
cleanup. The harness additionally requires matching original/mutated hashes,
compiled provenance, actual child exit `1`, exact source restoration, unchanged
repository state, and successful cleanup/evidence persistence. Any uncertainty
or late error remains `2`; the sequence stops and retains failed evidence.
These source outcomes do not replace normal A-I or visible Browser acceptance.
An optional exact `-Task43PFamily` selector limits focused normal runs; the
default six-family corpus remains unchanged. NMOS `RPD.2` is independently
expected to bind to `GroundElm` post 0; this corrects a manifest regression
without altering the generator or electrical graph.

The source-experiment harness now starts and identifies each disposable
preview from the same isolated compiled tree whose source mutation produced
the `war` output. It passes the disposable repository, web, and script roots
explicitly to the repository wrapper; the wrapper uses those roots only for
the execution surface and continues to own actual checkout provenance.
Renderer, raw-copper, and solver producer mutations remain separate,
byte-restored experiments.

The physical validator compares raw logical net-ID sets before terminal
iteration, including empty extra nets. Its solver phase re-fetches each live
simulation endpoint and requires exact element/post identity with the retained
detachable binding endpoint or fixed-generated physical-part terminal endpoint;
detachable-connection geometry is required only for detachable pads. The
negative sorts source observations and prefers a same-node/different-element
collision before using independent class/post/node incompatibility. Java
Task 43P evidence is accepted only through a complete exact nested schema,
so unknown repository-authority aliases fail before persistence. The current
host still prevents all three runtime experiments at the unsupported
`System.Net.HttpListener` preview boundary, before disposable identity
completion. Their records prove the runtime call was paired with the mutated
source bytes, but their compile/runtime-attempt and restoration results remain
`UNPROVEN`/exit `2`, not acceptance evidence. The latest combined record is
`task43p-source-experiments-1ee260b7a3c94b609830e475f3dc4491.json`.

## Task 43P final remediation corpus and NPN audit — 2026-08-30

The NPN physical manifest was re-audited against the current generator and
records all 15 terminals: `J1.1` is `LOAD_SUPPLY / WireElm / 0` and `LED1.K`
is `COLLECTOR / WireElm / 0`, with the other 13 NPN terminals unchanged and
reconciled to board, package, and solver bindings. Across the six families,
the independent manifest contains 64 terminals in total (`6 + 8 + 12 + 15 +
13 + 10`). No generator, CircuitJS, gameplay, or repair behavior was changed.

The disposable source harness now covers nine separately recorded producer
paths: renderer pad offset, renderer lead offset, raw-copper endpoint gap,
raw logical-net mismatch, fixed and detachable solver identity redirects,
package/mirror transform mismatch, internally self-consistent logical-pad
remapping, and omitted manifest terminal. Each case compiles all five OBF
permutations and attempts the matching disposable-root runtime route while
the mutated source is present. The combined record is
`task43p-source-experiments-027ac10835a24e31a91625b9bbcfee85.json`.

All nine compile results are exit `0`; all nine runtime results are exit
`2`/`UNPROVEN` before preview identity because this host cannot construct
`System.Net.HttpListener`. Each case records exact source-byte restoration,
unchanged wrapper-owned HEAD/status/source digest/file count, and stopped
preview proof with zero cleanup errors. The wrapper's repository-state scan
and route-failure boundary now convert repository/evidence file, process,
transport, timeout, and ownership uncertainty to typed infrastructure exit
`2`; strict nested Java evidence schema validation rejects unknown or aliased
repository-authority properties before persistence. No runtime mutation
rejection or visible `@Browser` acceptance is claimed.

## Task 43P fixed-part solver endpoint boundary — 2026-08-30

The solver proof now resolves a retained producer endpoint for every manifest
pad. A detachable pad uses
`GeneratedComponentConnectionBinding.getBoardEndpoint()` from the exact
component/pad binding and retains the existing detachable connection-element
ownership/geometry check. A pad without a detachable binding uses the installed
`FIXED_GENERATED` physical part and its `PhysicalPartTerminal` endpoint (the
foundation factory captures this endpoint for fixed foundation parts); it
requires fixed-generated provenance, the expected mounted component slot, and
exact manifest/package/terminal identity. A mutable replacement cannot satisfy
this no-binding path.

Each live `BoardSimulationBindings.getEndpoint(padId)` result is compared with
that retained endpoint by exact `CircuitElm` identity and post index. The
independent manifest/raw-board class, post, net, node, ownership, and finite
reading checks remain in force, as does distinct-net node separation. Fixed
pads do not fabricate detachable geometry. The disposable solver mutation
preserves the first J1.1 read used by fixed-part construction and redirects a
later live binding read, so its expected endpoint remains the retained
producer object rather than an alias of the mutation.

The refreshed source record is
`<OS-temp>/TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-a1e8de2ba420464086e03d52a6b70147.json`.
Its renderer, raw-copper, and solver mutations each compiled with exit `0`,
attempted the matching disposable-root runtime route, and returned exit `2` /
`UNPROVEN` at the unsupported `System.Net.HttpListener` preview boundary.
Exact source restoration and unchanged repository state were proven; no
runtime mutation rejection or visible `@Browser` acceptance is claimed.

## Task 43P retained board-endpoint oracle correction — 2026-08-30

`GeneratedBoardInstance` now captures a separate immutable
`GeneratedBoardEndpointOracle` directly from the authoritative board binding
map at the generated-board composition boundary. The capture occurs before
composition validation marks the instance ready for the developer-only live
lookup experiment seam. It is not a Git, source, or repository claim and is
not used by normal gameplay or CircuitJS behavior. Every Task 43P solver pad
uses this retained board endpoint as the expected identity. Detachable pads
must also match the exact `GeneratedComponentConnectionBinding` board endpoint
and its connection-element geometry. Non-detachable pads use the board oracle;
their installed `FIXED_GENERATED` part is checked for mounted component,
package, and terminal identity, with an endpoint cross-check only for
`FixedPhysicalPart` foundation parts. Internal fixed NPN/NMOS LED and resistor
terminals therefore remain component-side checks rather than board-endpoint
substitutes.

The source corpus's detachable solver case targets actual RC `C1.+`, not fixed
RC `R1.1`; fixed J1.1 remains a separate identity mutation. The live
`BoardSimulationBindings` redirect is enabled only after the retained oracle
has been captured and composition validation has completed, so a reachable
runtime would exercise the canonical verifier against a genuinely independent
expected endpoint. The latest nine-case record is
`<OS-temp>/TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-a702ea7b3abe44c4853d146daccb360e.json`:
all nine OBF compiles exit `0`, all nine runtime attempts exit `2`/UNPROVEN at
the unsupported `System.Net.HttpListener` preview boundary, and every case
records exact restoration, unchanged HEAD/status/source digest/file count,
stopped preview cleanup, and zero cleanup errors. No runtime or visible
`@Browser` acceptance is claimed.
