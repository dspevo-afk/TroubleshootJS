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
execution. `BoardPowerController` currently records whether a board is powered
or unpowered without changing arbitrary CircuitJS voltage sources. Future
generated-board metadata will identify the external power connector, rails, and
power domains that the controller may operate.

Active measurements run through an `ActiveMeasurementSession`. The adapter
validates probe targets and board-power requirements, then closes the session
through `finally`. The current session deliberately has no graph-mutation API.
A future test source must be applied, analyzed, measured, removed, and restored
inside this adapter/session boundary without entering user history or permanent
board state.

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
simulation placement. `GeneratedBoardInstance` couples board metadata to the
CircuitJS elements it owns while keeping generation, simulation, and rendering
separate. External board-power metadata identifies the specific simulation
supply backing that power input. Generated pad bindings use CircuitElm/post
endpoints, never analyzed node IDs. Generated circuits are installed only
through the controlled `CirSim` installation boundary.
