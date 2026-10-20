# Exact normal NPN sparse-board exporter

This task-only Java entry point exports the one accepted public replay
`tsj-alpha/6/EASY/NPN_LOW_SIDE_SWITCH/380469407799690561` from base
`ce6f3fef9ac86b3157f0ecac08ab53350022134d`. It accepts exactly four arguments,
in order:

```text
--family NPN_LOW_SIDE_SWITCH --seed 380469407799690561
```

It rejects any other family or seed, malformed or noncanonical signed-long
text, and any argument shape other than the pinned form. It builds the exact
replay string and calls `PlayerLaunchRequest.parse()`. That request is asserted
to have `candidateSearch=false`; the exporter resolves one `GenerationRequest`
with a fresh `PlanCache` and calls `Prepared.construct()` once. It does not
reroll, select a candidate, or change the requested geometry.

The scope identifies this replay as ordinary procedural bottom-face THT using
`SMALL_BOARD_EXECUTION@1`, layout 19 and packing 3, with standard 20-unit access
margins and two edge-anchored connectors. The exporter reports the actual
request execution policy, physical admission, placement constraints, and
packing-probe policy/disposition from the constructed instance.

The exporter preserves the accepted sparse diagnostic's normal admission,
geometry, routing, access, inventory, metric, selected-geometry and logical
board-graph checks. It uses the same `CirSim` setup and owned-instance cleanup
as `PcbNormalPackingCensusContractTest`; nested `finally` restores the previous
`CircuitElm.sim` even if disposal throws.

Each successful invocation emits one `PCB_SPARSE_NORMAL_DIAGNOSTIC ` row with
schema `sparse-normal-board@1`, followed by the required PASS marker. The
existing `PcbFunctionalGroupingContractTest.reportWhitespace(board, layout,
publicSeed, 1)` also emits its `PCB_WHITESPACE_CASE` row with largest protected
empty-rectangle coordinates. Its final argument is sampler version 1; the
sampler uses a 10-unit grid and labels free space
`FREE_IS_NOT_PACKABLE_PROOF`.

The sparse row carries the exact replay identity; selected geometry SHA-256 and
logical board-graph SHA-256 before and after export; sorted component, pad and
net inventories and counts; outline and tray; ordered package, variant,
transform, pose, body, courtyard, access and selection bounds; terminal and
probe endpoints; current traces, holes and silkscreen; physical constraints;
packing-probe canonical plus separate metric-evidence state; and current
`PcbPackingMetrics` or an explicit unavailable flag and reason. The independent
sampler's protected-void value and separately computed outline area, copper
Manhattan length, and via count are compared with production metrics when those
metrics are available.

The graph SHA fields come from
`PcbCanvasBoundaryContractTest.liveGraphSha256(board)`. They fingerprint the
authoritative logical board graph; they do not certify runtime CircuitJS solver
state, electrical behavior, or fault answers. Inventory counts are observed
from the constructed board rather than hardcoded.

No native command, compilation, test, build, browser, or replay was run by this
leaf. The root owns source freezing, adapter review, host canaries, compilation,
runtime execution, and final review.
