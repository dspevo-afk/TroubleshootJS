# LOAD12 graph validation evidence

**Checkpoint status: locally accepted for the finite diagnostic; publication pending.**
This page summarizes the final positive evidence and fresh negative controls.
All evidence aliases are task-owned: `N` is the current source worktree and
`QN` is the external QA bundle. No personal absolute paths or raw receipt bodies
are copied here. The detailed native receipts and reader outputs remain in QN.

## Candidate identity

| Item | Value |
|---|---|
| Source parent | `b033826e108773ef1c0fc723efd9efc120d539cd` |
| Private diagnostic revision | 20; graph schema r5; input/run/reader schemas v5 |
| Native input set | 1,077 files; 86,611,889 bytes |
| Native input pin SHA-256 | `408473f0d144250f0d901821486674ad8d01ad2af43dfd2633a228ec2f9e137c` |
| Production inputs | 850 Java; layout 26, dependency 28, VIA cost 60 unchanged |
| Approved identity | root `9007199254740993`; placement `-6587958367974952701`; routing `8445301674744508279`; request/index `0/0`; attempt `4`; pass `1` |
| Target-specific source | D_SENSE position 14 / next-fetch 15; LOAD12 position 3 / next-fetch 4, branch 73 |

The actual LOAD12 graph is captured before normal publication. Its prefix is
17 VIAs / 88 traces and its path is 222 Nodes / 217 planar edges / 4 source
transitions. Its grid is 478x309 (147,702 cells), goal union 447, and its tuple
digest is `1410a3abd90478627a638109cc31971802e53b3590015f83ebe4d8a57923a13d`.
These values are measured source observations, not synthetic edge-mask
goldens. D_SENSE is a separate target and uses its later 48-VIA prefix.

## Native gates

All rows below use the same final input pin SHA shown above. Both target runs
report `PASS_BEFORE_AND_AFTER` source auditing, exit code 0, termination proven,
and cleanup status PASS. The selected host was Windows PowerShell 5.1 with
JDK 8.0.502 and source/target 1.7 compilation.

### Focused contracts

`P07ShortcutQueryContractTest`, `P07ZeroViaGraphContractTest`,
`P07PrefixAllocationContractTest`, `P07AcceptedBranchCaptureContractTest`, and
`P07TwoLayerContractTest` passed in 39.0378681 seconds. Assertion counts, in
that suite order: 24, 57, 48, 3,736, and 13,112. The marker records all five
suites passing. This is the focused contract gate, not the full test matrix.

### Actual target runs and operation cleanup

| Target / QN receipt | Operation | Status | Operation time | Work units | Max unit | Cleanup |
|---|---|---:|---:|---:|---:|---|
| D_SENSE `query-d-r8` | `QUALITY_NULL` | PASS | 17,478 ms | 300/640 | 1,446 ms | PASS, 1 ms |
| D_SENSE `query-d-r8` | `QUALITY_PROBE` | PASS | 25,318 ms | 634/640 | 816 ms | PASS, 0 ms |
| LOAD12 `query-load-r2` | `QUALITY_NULL` | PASS | 17,995 ms | 300/640 | 1,361 ms | PASS, 1 ms |
| LOAD12 `query-load-r2` | `QUALITY_PROBE` | PASS | 25,678 ms | 628/640 | 853 ms | PASS, 0 ms |

The enclosing native runs were 78.2774239 seconds for D_SENSE and
79.0452749 seconds for LOAD12. Both proved child termination and cleanup.
The graph work counters are:

| Counter | D_SENSE | LOAD12 |
|---|---:|---:|
| `advanceCalls` | 300 | 294 |
| `workSteps` | 9,807,733 | 9,631,996 |
| `copyEntries` / `comparisonEntries` | 1,335,370 / 2,671,732 | 1,335,370 / 2,671,732 |
| `geometryBytes` / `effectUnits` | 539,214 / 1,844 | 523,282 / 1,358 |
| `ordinaryQueries` | 1,178,468 | 1,178,468 |
| `goalCells` / `reachedPadChecks` | 147,702 / 0 | 147,702 / 54 |
| `encodedCells` / `serializedUtf16Units` | 443,106 / 444,306 | 443,106 / 444,310 |
| `pathNodes` / `pathEdgesVerified` | 66 / 65 | 222 / 221 |
| `tupleBytes` / `activeMillis` | 3,248 / 2,095 | 11,774 / 2,211 |

The `QUALITY_NULL` and `QUALITY_PROBE` operations returned status PASS and
completed cleanup. In both runs the routing session ended `EXHAUSTED` after a
policy rejection; no layout was returned. The receipts therefore record
diagnostic completion and an exhausted routing session, not a successful route.

## Current reader results

The selected reader source hash was
`1933b4fec3f983c7eb846b02ad61339d6ffce961091cfa90c67d56b231f6caff` and was
stable during both runs. `reader-d-v5-r1` completed in 7.1535337 seconds and
`reader-load-v5-r1` in 6.2167453 seconds; both returned exit 0, with empty
stderr and bounded child-only cleanup. The reader outputs and native receipts
are tied by SHA-256 in each reader result.

The reader independently runs directed queue BFS and heap/Dijkstra per seeded
face; their distances and goal counts match exactly:

| Target / face | Reachable goals | Shortest goal length | Captured-end distance | Length-eligible goals |
|---|---:|---:|---:|---:|
| D_SENSE TOP | 1 | 650 | 650 | 0 |
| D_SENSE BOTTOM | 1 | 8,010 | none | 0 |
| LOAD12 TOP | 46 | 2,510 | none | 46 |
| LOAD12 BOTTOM | 415 | 3,250 | 4,070 | 415 |

The LOAD12 total is 461 relaxed goals, each passing its own positive-Manhattan
3:1 length test. The source path reports direct distance 2,150; that single
value is not used as a global gate for every goal. D_SENSE has no length-
eligible goal under the per-goal test; its source path reports direct distance
210. The graph uses the actual ordinary Router edge predicates. A length-eligible
relaxed goal still does not prove BranchSearch turn/bend state, copper
publication/finalization, final physical access, or electrical/layout behavior.

## Other completed checks

- The paired-target source audit `pair-v5-r1` passed in 1.1008342 seconds,
  checked source revision 20 / 1,077 inputs, and reported `sourceStable:true`.
- The Python protocol/math/source/path suite R5 reported 51 passing methods and
  one declared file-symlink privilege skip (52 cases total, 1.0733248 seconds).
- The independent actual graph oracle, source `b7475a16`, passed for D_SENSE
  in 0.8023798 seconds and LOAD12 in 0.7541897 seconds. Both processes exited 0
  with termination and source/input stability proven. Their controlled outputs
  are [D_SENSE](independent-d-actual.json) and [LOAD12](independent-load-actual.json).

The required paired hash-mismatch check and final combined source audit also
passed, as recorded below.

## Final controls and preserved failures

| Gate | State at this checkpoint | Notes |
|---|---|---|
| D_SENSE adversarial reader controls, v5 r6 | PASS | 65 negatives, 3 scoped N/A cases, junction cleanup, zero exclusions. |
| LOAD12 adversarial reader controls, v5 r6 | PASS | 68 negatives, junction cleanup, zero exclusions. |
| Standalone actual-target oracles | PASS | Final-source D R5 and LOAD R2. Earlier failures remain preserved. |
| Paired hash-mismatch control | PASS | Synthetic rebound LOAD reader exits 0 in 6.4677 s; paired D+LOAD audit exits 2 in 0.7195445 s with `PAIR_D_ALLOCATION_stability`. Termination and source stability proven. |
| Final combined input audit | PASS | r10, 57.0072596 s; all selected gates, 1,077 native inputs, 850 unchanged production inputs, 1,202 compiler inputs per variant and 28 frozen Python files bound to final source. |
| Scoped review and task cleanup | PASS | Root integrated diff review; independent source-only helper/reader/runner/audit reviews; selected actors terminated, junctions removed, no persistent task resource. |

The failed negative-reader attempts R1-R5 and prior native over-cap/compile/
fixture failures remain archived in QN. They receive no PASS credit. R4 reached
the new earlier D graph-binding guard for the unchanged current-index mutation;
R5 exposed the standalone LOAD12 format-only boundary for a well-formed later D
hash. R6 retains D content-mismatch coverage and uses malformed `x` characters
for the standalone LOAD12 format check. The paired negative rejects a
well-formed mismatching D hash. The standalone LOAD reader has no later D graph
authority; its exit 0 is expected in this synthetic check. All LOAD branch and
earlier graph bindings remain unchanged. A post-negative 1,077-input rehash
passes in 2.8208856 s. This synthetic control grants no native or full-matrix
qualification. No reader assertion is weakened.
Oracle failures preserve the incorrect shared-protocol assumption, raw
fingerprint-as-SHA assumption, board/grid unit mismatch, and planar-only
verification count. The final oracle independently checks the source contracts,
exact pitch steps and every planar or coincident-flip edge.

Combined-audit attempts R1-R9 failed incorrect audit assumptions about producer
metadata, receipt framing, helper ownership, historical H analysis shape,
negative-spec paths, the intentional bad-digest control, shared wrapper process
ownership or paired-artifact pins. Their source, launch and failure records stay
preserved. R10 passes without changing the already-qualified consumed inputs.
It compares the original H path, masks, allocation and all five analysis metrics
to both current algorithms. The exact `Require` implementation separately passes
one positive and five negative canaries in 0.2228138 s; that narrow result alone
does not certify the full audit. See [final-input-audit.json](final-input-audit.json).

## Commands and source reuse

The selected host was Windows PowerShell 5.1. The maintained native entry was
`scripts/verify-current-contracts.ps1 -Suite <suite>`, with the five focused
suites listed above and separate actual-target invocations of
`Rb100QualityZeroViaGraphContractTest` and `Rb100Load12ZeroViaGraphContractTest`.
The external wrappers pin JDK8, compile source/target7, capture the full output,
and retain separate operation, termination and cleanup records. Compile limit
is 60 s; focused suite limit is 60 s; actual-target suite limit is 480 s.

Selected Python commands used `read_quality_zero_via.py --input-spec <spec>
--input-spec-sha256 <digest>`; the wrapper requires the exact expected exit and
error code. `python-tests-r5` uses unittest discovery for
`test_read_quality_zero_via.py`. Fresh r6 negative-host runs select D_SENSE and
LOAD12 separately. Standalone actual oracles select those same targets; the
paired audit consumes both captures. The paired-prefix negative first runs the
standalone reader, then the paired audit and a complete source rehash.

The final audit selects focused-r13, query-d-r8, query-load-r2,
reader-{d|load}-v5-r1, oracle-d-v5-r5, oracle-load-v5-r2, pair-v5-r1,
paired-prefix-negative-v1, python-tests-r5 and reader-controls-v5-r6-{d_sense|load12}.
It hashes consumed sources/artifacts, audits exact native definitions and process
identities, and recenses all final input inventories before granting reuse.
Documentation is outside those consumed inventories; no unchanged expensive gate
was repeated after the passing final audit. External source hashes and controlled
results are recorded in [qualification.json](qualification.json).

## Limits and next milestone

The diagnostic keeps the 90-second case / 640-unit operation / 5-second unit /
120-second cleanup / 32,768 quantum caps, as well as request 1,000,000, branch
100,000, grid 250,000, ordering 3, VIA 48, and transition 4 limits. No
production Java changes or budget/oracle relaxation were made.

Full30 remains owner-excluded; HARD and PSYCHOTIC remain unavailable/unrun;
protected current data was not read and historical data boundaries remain
uncertified. No GWT/Player/electrical, normal-three-root, 2-by-31 or full30
renewal was performed.
Playable100 remains 0 and beta is held. Only this finite diagnostic is locally
accepted; publication and one completion email are pending.

The next unstarted milestone is a finite actual BranchSearch/finalizer replay
of any relaxed candidate before considering generic via-reduction adoption. It
does not include a full-board claim or beta release.
