# TroubleshootJS Architecture

CircuitJS remains the electrical simulation engine. TroubleshootJS layers its
challenge, board, and instrument behavior around that engine rather than
replacing its electrical results.

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
removed and the original graph is synchronously reanalyzed and solved in
`finally` before the transaction completes. The temporary intermediate point is
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
same R1 slot. Board-side pad endpoints remain fixed and physically distinct.

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
slice. Changing a page clears an invisible part selection. A retained probe on
a hidden loose part remains electrically bound to its physical ID, but its
marker point becomes absent rather than moving to another equal-value part.

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
rectangle outside the board copper area. The browser verifier's geometry bridge
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
resistor-open or resistor-incorrect-value challenges; the diode family selects
seeded diode-open or diode-short challenges.

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
and all printed/nameplate state remain unchanged. Normal players see only the
ready service ticket text, `Indicator does not light.` Developer-only
`tsjVerifyChallenge=true` exercises the fault lifecycle, measurements,
physical removal/restoration, and clear/reapply path after `READY`.

All active meter modes use the same physical polarity convention: the red
probe is the electrically positive test terminal and the black probe is the
electrically negative terminal. OHM and CONT share a $1 V$ / $1 kOhm$
CircuitJS stimulus wired with the same source orientation as the diode test;
the resistance formula still uses current magnitude only when deriving a
magnitude. DIODE is a distinct $3 V$ finite-compliance meter function, so a
forward-biased diode may yield a high finite OHM/CONT resistance without
meeting the $50 Ohm$ continuity threshold.

Generated challenge orchestration is family-agnostic. Family generation owns a
small `GeneratedChallengeCatalog` of compatible immutable
`GeneratedChallengeDefinition` candidates. A definition contains stable
challenge and complaint IDs/text, family/topology compatibility, selection
seed, selected fault/binding, and its `GeneratedChallengeBehaviorContract`.
`GeneratedBoardInstance` owns that contract and the definition references the
same object identity. `GeneratedChallengeController` uses the shared contract
for faulted validation and functional completion, while
`GeneratedBoardVerifier` uses it for healthy validation. The generic
controller only records the solver-gated healthy/faulted stages, applies the
selected binding, invokes the contract, and transitions to READY. It validates
that private fault infrastructure is simulation-owned but not a logical
component, external-power element, or detachable connection.

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
instead of merely carrying fault metadata. `ResistorReplacementInventory` is deterministic per seed and retains
the original failed resistor alongside healthy replacement choices. Changing a
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
`LedIndicatorFamilyState` for the R1 and LED1 slots, their separate inventories
and non-depleting catalogs, and their physical-part serial allocation. Generic
runtime simulation-element ownership remains on the instance. Future component
families must add their own family state rather than component-specific instance
fields or getters.

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
owns the D1 slot, diode inventory, non-depleting catalog, and physical-part serial
allocator. None of those diode concepts are fields or getters on
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
