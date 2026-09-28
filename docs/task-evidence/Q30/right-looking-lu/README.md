# Right-looking LU trial

This is an intermediate, measurement-only comparison for the isolated right-looking LU implementation. The predeclared sequence was R7, B7, B13, R13, R64, B64, B7, R7: four adjacent candidate/control pairs, with the final pair reversing the seed-7 order. The frozen column-reference baseline is 0b29680c7daa62123732b91e3c1157e8ba0d6abd. The candidate changes only CirSim.java and LuFactorizationChecks.java; the normalized source patch is recorded in source.patch.gz (exact decompressed SHA-256 6fff31eb7e7b840199b25af3cc98818398005b5c34fd378207f8447537437867).

All eight run reports passed the maintained strict report reader, and the four pairs retained exact request, work, proof-result, owner, and physical-result parity. The candidate reduced cold elapsed time in each pair:

| Pair | Cold-time reduction | Proof-time reduction | Routing-time reduction |
|---|---:|---:|---:|
| R7 â†’ B7 | 2,558 ms | 2,255 ms | 187 ms |
| B13 â†’ R13 | 1,587 ms | 1,256 ms | 177 ms |
| R64 â†’ B64 | 935 ms | 1,060 ms | -126 ms |
| B7 â†’ R7 | 2,122 ms | 1,929 ms | 22 ms |

Median paired cold-time reduction was 1,854.5 ms. The seed-7 control pair differed by 283 ms while the candidate pair differed by 719 ms, so the paired improvements are useful evidence but do not remove run-to-run variation. Most of the reduction appears in proof time; routing did not improve consistently. The source change replaces row-reference-capacity growth with pivot-local scratch while preserving the per-entry arithmetic order. This is a plausible mechanism, not proof that every workload benefits.

The native right-looking gate, actual GWT build, and maintained A07 report gate passed for the isolated trial source. The A07 strict reader also passed its three negative corruption canaries. comparison.json, the compressed application reports, gate logs/receipts, inputs, cleanup records, and strict-reader outputs preserve the detailed run evidence. source-hashes-and-audit.json records baseline/candidate source and jar equivalence checks. integration-receipt.json records that the two-file candidate was integrated with line endings preserved and the normal verifier hook retained separately.

This does not qualify Q30 or establish the 90-second acceptance target. In this isolated right-looking sequence, seed 13 took 92,306 ms, above the target; Q30 remains BLOCKED / NOT ACCEPTED and normal publication remains disabled. The final integrated-source GWT and canary gates have since passed before local commit; their exact receipts and source/input/cleanup audit are appended below. Those gates do not replace the still-required normal-player full-corpus acceptance. This trial makes no claim about broader seed coverage or timing stability beyond the eight predeclared runs.


## Final integrated-source gates (pre-commit)

The final integrated source passed the maintained JDK 8/GWT build of five permutations: **PASS**, exit code 0, **88.9878218 s**. The preserved build log reports all five permutations compiled and linked successfully. The browser canary then passed both expected checks: compiled A07 returned **PASS** in **1.2653 s** (271,502 pure and 73 runtime assertions), and the normal Q30 verifier returned the expected **BLOCKED:normal-catalog-disabled** in **0.4811 s**. The A07 strict reader passed three negative corruption canaries. The disabled-reader receipt confirms generation, snapshot capture, and live-owner mutation were all false; it is a fail-closed canary, not normal-player acceptance.

The canary's before/after input audit retained identical aggregate, source, web, and runner hashes across 1,525 files. Current `CirSim.java` and `LuFactorizationChecks.java` byte hashes match their entries in both input manifests. Cleanup passed in **0.9423 s** with the server stopped, no owned survivors, and no cleanup errors. See [`final-integrated-source/`](final-integrated-source/) for the sanitized gate receipts, exact application reports in deterministic gzip, input inventories in deterministic gzip, source/input/cleanup audit, and per-artifact SHA-256 manifest.

The original pre-optimization Crout factorization and solve oracle remains unchanged and independent. Factor entries, pivots, and solution values are compared numerically; signed-zero bit identity is not asserted (`+0.0 == -0.0`). Coverage includes late cross-column pivots, the later-row pivot-tie rule, and workspace clearing after success and failure paths. The compiled A07 verifier calls these checks, and the final A07 report passed. Cleanup assertions for the LU workspace are separate from the browser/server cleanup receipt.

These are final integrated-source build, A07, and disabled-catalog boundary gates before local commit. **No push was authorized or performed. Q30 remains BLOCKED / NOT ACCEPTED; normal publication is disabled, and the normal-player acceptance corpus has not passed.**
