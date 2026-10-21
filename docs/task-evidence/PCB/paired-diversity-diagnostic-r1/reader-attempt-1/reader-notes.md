# Paired LED diversity diagnostic reader

read_diagnostic.py is a task-only evidence reader for the copied original-assertion LED matrix. It reports a reproduced diversity failure and bounded no-hook comparison. Its top-level result is DIAGNOSTIC_ONLY; it does not report production acceptance.

The reader accepts three positional paths:

    python read_diagnostic.py current/native.txt nohook/receipt.txt current/native.txt

The first and third paths may be the same. The current adapter writes its receipt only after a successful run, so the expected assertion-failing current phase has no receipt file. The reader extracts diagnostic rows from current/native.txt and requires that log to contain exactly the source assertion text for seed -611041393415742133. The no-hook receipt must contain its 20 rows, one PHYSICAL_MATRIX line and the task diagnostic PASS line. The --canaries option runs self-contained controls without native inputs; normal reading also includes those controls in its JSON output.

The reader independently computes the original signed-long root sequence and requires the pinned duplicate at root ordinal 18 with the earlier matching row at ordinal 13 / seed 4515843680402079038. It checks JSON field order and duplicate keys, canonical signed seeds, row order, physical and novelty SHA-256, prior-match bookkeeping, duplicate poses and outline dimensions, and normal-packing@3 receipt bounds, disposition and metric consistency. For no-hook it requires 20 accepted unique physical and novelty rows and preserves the original thresholds: placements/routes/copper at least 12, macros at least 6, sizes at least 2, and roots at most 48. It independently recomputes physical, placement, macro and outline-size counts from rows. Route and copper distinct counts remain sourced from the original matrix summary and retained Java assertions.

Cross-run placement, physical/novelty fingerprints, board outline and packing receipt values are reported by matching seed. They may vary because packing changes geometry. The current-run duplicate pair must still match in normalized placement, fingerprint, novelty identity and outline dimensions. Clock fields are reported but not compared. The reader does not re-derive electrical graph equivalence, authenticate receipts, or claim full-matrix completion, all-seed diversity, optimality, manufacturing quality, player behavior or production acceptance.

The reader pins these source hashes in its comparison output. The root's frozen input/source gates separately verify the bytes:

| Source | SHA-256 |
| --- | --- |
| tests/contracts/QuickPlayPhysicalMatrixContractTest.java | f54486fad58d30d003355932a67e01dfdeb814d401a7bcfdaa212f2e0d4ff395 |
| src/com/lushprojects/circuitjs1/client/PhysicalBoardFingerprint.java | e6b8a1bca49ff913e614171c65bc86df9f6a5a39ed28cb44744b8e88cf9daf06 |
| src/com/lushprojects/circuitjs1/client/PlayerSessionFingerprint.java | e1efcc45ae485b978f9ee771193eaae2622f4930d7a7461318de4c09c10673cf |
| src/com/lushprojects/circuitjs1/client/PcbPackingMetrics.java | 724e4d6822eaeb3178efdfbaa30340ff12f0bdb1821734666b1a49df47ca5a8e |
| src/com/lushprojects/circuitjs1/client/PcbPackingProbeReceipt.java | c7d2f17a3e4b2a892beae7856b5d00dce9af66f7d50d2c3f802ec74d8116cf68 |
| docs/task-evidence/PCB/paired-diversity-diagnostic-r1/export-source/SparseNormalBoardDiagnostic.java | fb0f1caea0d5d341e1fc5ea04ee3f0b82b8d7d4ac0664e5cca81722e51a9ef6a |
| src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java | 1144f0575cbbae802db890100a460fda9a8541c79459629c06769a4ee9d6fceb |
| src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java | df561459c15fcd85840672971b1244bc019e92f58cfef4f81dc663d14f99f1b1 |
| src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java | 9dcf4917692f32071574ef5adf8f75aa55ef0e6a9a3159d3011549be2b4fbda3 |
| src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java | 7f27c83d12f47579a7fea50b496c2f0320e833e412f0aebe82594a62c7184750 |
| tests/contracts/PcbConnectedPocketPackingContractTest.java | d4d788bb9d6cf351c8c28c02ac8f7b4aef8445b110380382532392886d4e0a51 |
| tests/contracts/PcbNormalPackingIdentityContractTest.java | a4c4806a856dd6ed9d5aee213c5c82d7914bc87d75fd90a1b45ee5ce29250 |

No Python reader or native test was executed while implementing this helper. The root owns canary execution, receipt comparison and final acceptance.
