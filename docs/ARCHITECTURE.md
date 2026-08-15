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
to either probe or an existing circuit endpoint.

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

Passive DC voltage is separate from active measurement. After each normal
CircuitJS simulation step, `InstrumentController` refreshes a valid retained
DC probe pair from the current solved post voltages. This callback never
installs test elements or consumes an OHM refresh. The OHM transaction
revalidates its probes and electrical-power permission after the adapter
returns; a queued power change discards the earlier unpowered resistance result,
then restores and solves the final powered or unpowered graph before the
transaction reports solver restoration or generated verification resumes.

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

Resistor-band rendering follows the stable component ID from each
`PcbComponentPlacement` (including a tray component) to its
`ResistorNameplate`, then through `ResistorColorCode` semantic tokens to local
renderer CSS colors. Four-band encoding supports only finite, positive,
integral, exactly representable `+/-5%` values; it rejects values that would
otherwise be rounded or truncated to a different printed resistance.

PCB geometry is a separate rendering layer. `PcbBoardLayout` contains the board
outline, component placements, pad placements, traces, and parts-tray geometry,
and references only stable `BoardComponent`, `BoardPad`, and `BoardNet` IDs.
It does not contain CircuitJS elements or analyzed node numbers. The initial
`LedIndicatorPcbLayout` is manually authored; it is intentionally not an
autorouter, manufacturing model, or source of electrical behavior.

`BoardSimulationBindings` maps stable pad IDs to resolvable
`CircuitMeasurementEndpoint` instances. CircuitJS element/post references are
valid current schematic bindings, and resolve dynamically after reanalysis.
Current schematic probes continue to use `CircuitPostProbeTarget`.
`BoardPadProbeTarget` identifies the active generated-board instance and stable
pad ID, resolves electrically through `BoardSimulationBindings`, and asks the
current PCB renderer for marker geometry. `ComponentLeadProbeTarget` similarly
uses generated-board, component, and pad IDs but resolves through the declared
component-side endpoint. Neither target persists analyzed node numbers. Both
PCB and schematic hit testers feed the same generic probe-selection path in
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
component bindings, external-power bindings, and an optional family validator
without exposing a topology-specific API. Component bindings map logical
`BoardComponent` IDs to one or more live `CircuitElm` references. External
power bindings map logical `ExternalBoardPowerInput` IDs to one or more backing
elements, leaving room for a future source-isolation control without assuming a
`VoltageElm`. Generated pad bindings use CircuitElm/post endpoints, never
analyzed node IDs. Family-specific electrical expectations stay with the
generator family rather than the generic verifier.

Generated circuits are installed only through the controlled `CirSim`
installation boundary. Generated-board verification is requested after
installation or reanalysis and runs once only after CircuitJS has analyzed the
circuit and simulation time has advanced; paused simulation leaves the request
pending rather than treating unsolved current values as a failure.

The first faulted challenge is selected with `?tsjChallenge=led&seed=<seed>`;
the corresponding `?tsjFixture=led&seed=<seed>` route remains a healthy
fixture. `GeneratedChallengeController` advances only through solver-gated
verification: `PREPARING_HEALTHY` validates the closed, powered generated
board; it then applies its selected fault, requests another CircuitJS analysis
and time advance, validates the resulting symptom in `PREPARING_FAULTED`, and
only then enters `READY`. This is not a UI timer or an assumed settle delay.

`GeneratedFault` is immutable challenge metadata (stable ID, type, target
component, circuit family, and selection seed). `GeneratedFaultBinding` owns
the private CircuitJS implementation, while `GeneratedFaultController` is the
only code permitted to apply or developer-clear it. The first LED challenge
uses a private series `SwitchElm` to model an internally open R1. That switch
is not a `BoardComponent`, PCB pad, placement, nameplate, or external-power
control. A declared binding may be the component-side endpoint of the target's
detachable lead, preserving two valid outer terminals for in- and
out-of-circuit measurement without giving the internal fault infrastructure a
physical board identity.

Faulted verification keeps generic ownership, topology, pad, net, and
physical-modification checks active, but does not apply a healthy-current
family assertion while the intentional fault is active. The fault-specific
validator requires powered, installed R1, a bound open isolation switch, LED
current below $1 uA$, and a non-illuminated LED state. Fault application never
changes printed resistor markings, component physical state, stable IDs,
external power bindings, undo/redo state, or board geometry.

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
seed, selected fault/binding, and a `GeneratedFaultValidator` strategy. The
generic controller only records the solver-gated healthy/faulted stages,
applies the selected binding, invokes the strategy, and transitions to READY.
It validates that private fault infrastructure is simulation-owned but not a
logical component, external-power element, or detachable connection.

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

Family-specific `GeneratedRepairValidator` implementations decide functional
completion from solved behavior. The LED validator requires a healthy installed
physical resistor, powered board, no active meter overlay, 5-15 mA LED current,
matching resistor current, and illuminated operational state. Incorrect but
electrically valid replacements simply remain READY. `COMPLETED` is latched
after a solver-backed successful repair; later board changes still affect the
electrical simulation honestly but do not retract the first verified repair.
