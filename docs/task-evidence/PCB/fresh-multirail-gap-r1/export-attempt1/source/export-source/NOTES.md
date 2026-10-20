# Fresh RB30 gap exporter

Task-owned source: `FreshRb30GapDiagnostic.java`.

Worker handoff SHA-256: `EB4F3014D946B21A70B349F57A639476B562F6868EFA1889FF232B88A189A577`.
Root integration adds explicit power-input metadata so the logical snapshot can
be reconstructed independently, and repeats identity checks after all
serialization and metric reads. The final source hash is frozen in the export
attempt manifest; the handoff hash describes the unexecuted worker version.

The exporter accepts only `--seed <canonical signed-long>` and has no default
seed. It resolves `Rb30Plan`, constructs the real board, and calls
`SeededPcbLayoutGenerator.generateWithPolicyResult` through the current medium
policy. It serializes the selected `PcbBoardLayout` and `MediumBoardPhysicalPolicy.Result`
with one `PCB_FRESH_RB30_GAP` JSON row and emits the exact marker prefix
`PASS: fresh RB30 gap diagnostic `.

The read-only checks use the existing `PcbBoardLayout` geometry/routing/route
quality validators, `PcbTraceClearance`, `PcbTwoLayerRules` normal-medium
admission, `PcbAccessPlanner`, `PcbPackingProbeReceipt`, and
`PcbPackingMetrics` when available. The row includes the exact plan and full
result route canonical strings, selected policy/attempt, packing receipt, before/after
geometry and logical-board fingerprints, component/package/anchor/body/access/
drag/courtyard data, declaration-ordered terminal/pad/probe geometry, traces
on both faces, holes/vias, silkscreen, placement domains/barriers, outline,
nets, and available packing metrics. No connectivity is inferred by the
exporter; it only emits the board's declared identities and routed geometry.

Limits: this source does not create CircuitJS solver state, apply or diagnose a
fault, run the RB56 occupancy helper, alter seeds/policy/budgets/geometry, or
invoke an alternate solver or live measurement. Packing metrics are represented
as unavailable only when the existing metrics owner reports its documented
`Unavailable` condition. Trace/hole/silkscreen arrays use stable output
ordering; component terminal and board pad declarations retain source order.

Status: NOT RUN by this worker. No compile, build, test, native runner,
stage, commit, push, or email was performed.
