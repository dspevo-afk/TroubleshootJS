# TroubleshootJS Architecture

CircuitJS remains the electrical simulation engine. TroubleshootJS layers its
challenge, board, and instrument behavior around that engine rather than
replacing its electrical results.

`ProbeTarget` describes where a user placed a probe: validity, marker position,
and resolution to a `CircuitMeasurementEndpoint`. It does not perform a
measurement. `CircuitMeasurementEndpoint` represents a resolvable electrical
endpoint, while `CircuitMeasurementAdapter` owns measurement behavior and the
translation to CircuitJS.

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
