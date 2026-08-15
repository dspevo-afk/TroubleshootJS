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

`BoardSimulationBindings` maps stable pad IDs to resolvable
`CircuitMeasurementEndpoint` instances. CircuitJS element/post references are
valid current schematic bindings, and resolve dynamically after reanalysis.
Current schematic probes continue to use `CircuitPostProbeTarget`; a PCB probe
target will use a board pad ID after the renderer provides marker geometry. Both
paths converge through `CircuitMeasurementAdapter`.

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
