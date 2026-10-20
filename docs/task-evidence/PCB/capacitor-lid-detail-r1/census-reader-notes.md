# Capacitor lid detail paired census reader

`compare_census.py` is the strict same-epoch reader for the frozen accepted
packing3 receipt and the forthcoming capacitor-lid candidate receipt. It takes
`--baseline`, `--current`, and `--output` paths. The root owns execution and
canary evidence; this worker did not run the reader, a test, a build, or a native
command.

The pinned baseline is
`docs/task-evidence/PCB/packing-via-cap-r1/final/census-receipt.txt`, SHA-256
`766fe39ad1e23ae18e08b7de1244a325826a5e42a9c5a5cba5cbe87b0d582510`. Its five
maintained observation prefixes each have exactly 44 rows: normal packing,
whitespace, board silk, reference metadata, and lead/silk. The reader requires
all 220 paired rows and checks the family/root identity and canonical order for
each stream; the whitespace rows are paired by emitted order, signed root, and
part count. Every packing row must be accepted/finalized, preserve the live
graph hash, and declare layout algorithm version 19 in both frames. The raw
receipt, including suite summaries and row framing, must match exactly after
masking only the validated clock tokens. Duplicate JSON keys, unknown PCB row
types, duplicate/missing/extra rows, schema changes, reordered fields, and any
other raw-text change fail closed.

All nonclock nested data and complete raw observations remain exact. In
particular, this retains graph and geometry hashes, exposed component and
terminal identities, text/style/target metadata, all counts, routing work and
recovery strings, admissions/status, and all four production metrics: outline
area, Manhattan copper, vias, and protected void. The whitespace sampler's
largest-rectangle area must also match the production protected-void value.
There are no geometry, graph, epoch, routing-work, or metric deltas allowed.

## Clock scope

Source review of the maintained emitter identifies exactly three top-level
clock fields in each accepted packing row:

| Field | Maintained owner | Validation before masking |
| --- | --- | --- |
| `elapsedMillis` | `PcbNormalPackingCensusContractTest.acceptCase`; elapsed `System.nanoTime()` around one case | Exactly one raw token and one JSON key; nonnegative integer |
| `maxAdvanceMillis` | `PcbNormalPackingCensusContractTest.runCase`; maximum `System.nanoTime()` duration of a normal-medium advance | Exactly one raw token and one JSON key; `null` or nonnegative integer |
| `generationRoutingMillis` | `PcbBoardLayout.getGenerationRoutingMillis`, accumulated by `SeededPcbLayoutGenerator` around route work using `System.currentTimeMillis()` | Exactly one raw token and one JSON key; nonnegative integer |

The reader rejects duplicate JSON keys recursively, requires each listed value
exactly once at the packing-row top level, then checks that each raw token is
framed once before substituting a clock marker. It reports every changed clock
with its family, exact signed root, field, baseline value, and current value.
Clock availability must remain identical: an unmeasured `null` advance is not
interchangeable with a measured zero. The positive clock control therefore uses
an actually measured case for each field, including a normal-medium advance.
The `routingRecovery` canonical string and every other field remain unmasked.
No clock fields are permitted or masked in the four companion observation
streams or suite summaries.

## Version and source binding

The receipt directly exposes layout epoch 19 on every packed case. Its schema
does not expose a separate per-case `placementPackingVersion` field. Packing3
identity is therefore bound through the exact accepted baseline receipt SHA and
the current source's pinned `CONNECTED_POCKET_PACKING_VERSION = 3` and
`normal-packing@3` identities. The reader checks both frames are epoch 19 and
checks those current source identities before comparison. This makes the
same-epoch paint-only control explicit without pretending the receipt carries a
packing-version field it does not have.

The script pins these source inputs; any byte change fails before comparison:

| Input | SHA-256 |
| --- | --- |
| `tests/contracts/PcbNormalPackingCensusContractTest.java` | `d27dc575ff767da34a3d0eab70755777e65602f72162e634daa23757cf4691e8` |
| `tests/contracts/PcbFunctionalGroupingContractTest.java` | `c9396d0e93b9d042098f9e61efa0fa450e085fff96ae700b9690ebb64091a8d3` |
| `tests/contracts/PcbCanvasBoundaryContractTest.java` | `4aaa2d718ca2845eb0e38a0e6d9bcde5ed37144ea4f3199d795c99242e819aa6` |
| `src/com/lushprojects/circuitjs1/client/PcbPackingMetrics.java` | `724e4d6822eaeb3178efdfbaa30340ff12f0bdb1821734666b1a49df47ca5a8e` |
| `src/com/lushprojects/circuitjs1/client/PcbBoardLayout.java` | `5a3ea41332c85ff1ea263fd3952963fda1c845e9d9359460abcc271fdcd4d180` |
| `src/com/lushprojects/circuitjs1/client/PcbRoutingWork.java` | `20409e00ad33953c57bb043103e676866f2f5248ad2ed1ab93f6882ca9450031` |
| `src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java` | `6f3af5412696b6dbf3e14593edf195151825483a9a034b7dadbb9043b854980f` |
| `src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java` | `e1ef75f6b00098d8d5a9a9965622a81463426eb470a8b50f6c0c8f9ae16ff97d` |
| `src/com/lushprojects/circuitjs1/client/PcbPackingProbeReceipt.java` | `c7d2f17a3e4b2a892beae7856b5d00dce9af66f7d50d2c3f802ec74d8116cf68` |

## Built-in canaries

Canaries start from the actual pinned accepted baseline, used as both same-19
frames. A positive control changes only three actually measured clock values
and must pass while reporting each difference. Five isolated negatives must fail
with their named field/reason: changed outline area, changed geometry SHA-256,
changed nonclock `generationRoutingExpansions`, and a current-frame layout
epoch changed to 18, and unmeasured advance clock changed from null to zero.
The reader checks the rejection text so an unrelated
precondition cannot make a canary appear to pass.

The comparison JSON exposes top-level `status`, `cases`, `observationPairs`,
`clockDifferences`, and `canaries` for the root qualifier. It also records the
baseline/current hashes, per-case exact metrics, and each of the 220
clock-masked raw-row hashes. The result is finite to this accepted 44-case
corpus; it does not claim all-seed behavior, a packing optimum, manufacturing
qualification, or player-flow evidence.

R2 corrects a63-character literal metrics-source pin from the already frozen
published inventory. The first actual control failed before comparison/native
execution; its original helper seal/source and private raw output remain.
Every pinned source now also requires lowercase64-character SHA framing.
