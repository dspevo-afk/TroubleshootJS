# Sparse normal board exporter

This task-only Java entry point exports the two accepted first-New-board public
replays from the unchanged `e877c2cf6a790804dbb5a64693ab799ee9e7ea1d`
candidate. It accepts exactly four arguments, in order:

```text
--family RC_DELAY --seed -9187226374781659024
--family COMPOSED_CONTROLLED_INDICATOR --seed 7141843758046216173
```

It rejects other family/seed pairs, malformed or noncanonical signed-long text,
and any argument shape other than the two accepted forms. It builds the exact
`tsj-alpha/6/<profile>/<family>/<seed>` replay string and calls
`PlayerLaunchRequest.parse()`. That request is asserted to have
`candidateSearch=false`; the exporter resolves one `GenerationRequest` with a
fresh `PlanCache` and calls `Prepared.construct()` once. It never calls the
random request constructor or candidate selector. Both replays use their actual
catalog construction path; no layer count, route policy, or geometry is chosen
from the displayed difficulty label.

The simulation setup, physical admission, geometry checks and disposal follow
`PcbNormalPackingCensusContractTest`: a fresh `CirSim`, the existing grid values,
`owner.requireNormalPhysicalAdmission()`, `layout.validateGeometry(board)`, and
finally disconnecting external power and deleting only the constructed owner's
simulation elements. A nested `finally` restores the previous `CircuitElm.sim`
even if disposal throws.

Each successful invocation emits one `PCB_SPARSE_NORMAL_DIAGNOSTIC ` row with
schema `sparse-normal-board@1`, followed by the required PASS marker. The
existing `PcbFunctionalGroupingContractTest.reportWhitespace(board, layout,
publicSeed, 1)` also emits its own `PCB_WHITESPACE_CASE` observation row with the
sampler's largest protected empty-rectangle coordinates. Its last argument is
sampler version 1; the sampler uses a 10-unit grid. It labels free space as
`FREE_IS_NOT_PACKABLE_PROOF`.

The sparse row contains exact family/profile/seed/replay identity, current
physical admission, selected-layout SHA-256 before and after export, sorted
component/pad/net inventories and counts, full selected outline and tray,
ordered component package/variant/transform/pose/body/courtyard/access/selection
bounds, per-terminal logical endpoints and physical/probe coordinates, current
traces, holes and silkscreen bounds, placement constraints and barriers, the normal packing probe
canonical plus its separately exported metric-evidence state, and the current
`PcbPackingMetrics` values or explicit unavailable flag/reason. The row also
records the resolved request's actual execution policy. Its assertion count is
the exporter-owned checks; the existing helper contracts retain their own
internal check counters. The live graph SHA-256 fields come from
`PcbCanvasBoundaryContractTest.liveGraphSha256(board)`: they fingerprint the
authoritative logical board graph and do not certify runtime CircuitJS solver
state, electrical behavior, or fault answers.

The existing version-1 whitespace sample and independent outline-area, copper
Manhattan-length, and via counts are compared with `PcbPackingMetrics` when the
production metric is available. An unavailable production measurement remains
explicit in both the row and comparison status. The exporter also invokes the
existing normal admission, layout geometry, routing geometry, route quality,
trace clearance, access, graph-immutability, and selected-geometry-immutability
owners. Inventory counts are observed from the generated graph; the exporter
does not hardcode expected part or pad counts.

No native command, test, build, browser, or replay was run by this leaf. The root
owns adapter compilation, host canaries, exact seed executions, and final review.
