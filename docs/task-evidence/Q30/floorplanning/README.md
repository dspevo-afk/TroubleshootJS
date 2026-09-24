# Q30-P1 floorplan diagnosis evidence

This directory contains reproducible developer-only placement measurements for Q30-P1. The machine-readable comparison measures the actual Q30 exact-root placements against the successful structured 33-part two-layer fixture. It reports placement geometry and routing proxies; it does not claim Q30 admission, solver behavior, player inspectability, or diagnostic qualification.

## Final matched comparison

The stable comparison is [before-after-comparison.json](before-after-comparison.json). It compares:

- BEFORE: [before-head-current/floorplan-comparison.json](before-head-current/floorplan-comparison.json), using the exact `git show HEAD:src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java` snapshot.
- AFTER: [after-stable/floorplan-comparison.json](after-stable/floorplan-comparison.json), using the explicit planner snapshot after anchor-region coverage was fixed.
- Structured control: the seven validated successful `count=33`, `FULLER_TWO_LAYER`, `validMixedLayer=true` rows selected from the shared synthetic raw receipt.

Both Q30 runs use the same current Q30 pilot source, actual `Rb30Plan`, exporter patch, 12 signed seeds, and six candidates per seed. Each has 72 requested attempts, 72 accepted placements, and zero rejected attempts. The structured control has seven successful seeds: `0, 1, 11, 23, 37, 59, 83`.

### Source and input hashes

| Input | SHA-256 or Git identity |
|---|---|
| Q30PhysicalPilot.java | `31c47dd9f5bd08ee946ebf2b541e3f3ab3709d1a9277d7d1d335618e647ed5fc` |
| Rb30Plan.java | `538bdd61849b8ef837710d089513bfa67e985d2d119464fe50e4c9de55547488` |
| BEFORE planner snapshot | `4d0a6349fb9562b13a4306f55a86d74536ae808a35554b7a9b50021ba003281c`; HEAD blob `c217b98c6cf080602cb4a0b86362dbd37527ea06` |
| AFTER planner snapshot | `bcc264566b6267c77722d1d96dc60bffd0f26dc339ed88ab29b0ac4dc86bcb60`; fixed planner blob `ab64675cb2b51f8d7714cc1e7de9fd0d7a748414` |
| Q30 exporter patch | `e0032548a266bd7e29090762c4b19fc61468c552319f6c5a8bfa44f9899643ec` |
| Q30 BEFORE inspect stream | `bb9c69ec541457d6d6ed98ffad7de632fa0da7c84b72c66b9d776c37f8993159` |
| Q30 AFTER inspect stream | `4bc1934077023422741afa493f8ededae584baf612e4dc35a81eea116b85f00f` |
| `Q30TwoLayerScaling.java` | `4a312fa13bb9448cadc751a11b3f41bb0d051e74b7553cad67689216aa16c575` |
| Shared synthetic raw receipt | `df0a9c56cc6a73689934903dd3d40502ffc3a85c0646bdf29257b619dcd707fc` |

The final BEFORE receipt is [before-head-current-receipt.txt](before-head-current-receipt.txt) and the final AFTER receipt is [after-stable-receipt.txt](after-stable-receipt.txt). The receipts record the JDK8 compile, full seed/candidate invocation, acceptance counts, and verified task-owned snapshot cleanup.

### Key median comparison

All values are pilot coordinate units except fractions and aspects. `afterMinusBefore` is AFTER minus BEFORE. The `structuredMedian` column is the median across all seven validated structured rows. The complete 34-row table, counts, hashes, methods, and limits are in the JSON comparison.

| Metric | BEFORE | AFTER | Structured | AFTER / BEFORE |
|---|---:|---:|---:|---:|
| Board area | 5,139,400 | 7,839,600 | 3,542,000 | 1.525 |
| Board aspect | 1.0380 | 2.1372 | 1.4935 | 2.059 |
| Courtyard area | 589,652 | 589,652 | 693,000 | 1.000 |
| Courtyard fraction | 0.114716 | 0.075088 | 0.195652 | 0.655 |
| Net HPL total | 27,895 | 23,240 | 12,360 | 0.833 |
| Net Manhattan MST total | 34,100 | 28,025 | 18,210 | 0.822 |
| Component pair distance median | 1,100.0 | 868.125 | 700.0 | 0.789 |
| Electrically adjacent pad pair count | 219 | 219 | 465 | 1.000 |
| Electrically adjacent pad distance mean | 1,169.635 | 952.968 | 791.828 | 0.815 |
| Electrically adjacent pad distance p95 | 2,308.250 | 1,933.750 | 1,570.000 | 0.838 |
| Maximum net fanout | 16 | 16 | 24 | 1.000 |
| Maximum-fanout net HPL mean | 3,330 | 2,895 | 2,150 | 0.869 |
| SUPPLY HPL total | 9,325 | 8,445 | 4,000 | 0.906 |
| SUPPLY HPL p95 | 3,011.250 | 2,987.500 | 2,055.000 | 0.992 |
| RETURN HPL total | 5,860 | 5,485 | 2,150 | 0.936 |
| RETURN HPL p95 | 3,300.750 | 2,922.000 | 2,150.000 | 0.885 |
| Estimated straight-line ratsnest crossings | 74.5 | 41.0 | 35 | 0.550 |
| Net bounding-box overlap pairs | 97 | 63 | 56 | 0.649 |
| Connector nearest same-net distance median | 845 | 235 | 245 | 0.278 |
| Connector nearest same-net distance p95 | 1,907.500 | 2,452.000 | 451.500 | 1.285 |
| Connector cross-region incidence fraction | 0.583333 | 0.583333 | 0.500000 | 1.000 |
| Minimum courtyard edge clearance | 44 | 65 | 70 | 1.477 |

The repaired AFTER placement lowers aggregate HPL, MST, component locality median, complete electrical pad-pair mean/p95, maximum-fanout HPL, SUPPLY/RETURN HPL totals, crossing proxy, and net-box overlap. Connector median locality improves substantially, while its p95 increases; this exposes a long tail that the median alone would hide. The board grows and becomes wider, reducing courtyard fraction and increasing minimum courtyard edge clearance. Structure counts, maximum fanout, region incidence, and connector cross-region incidence remain unchanged.

## Part A diagnostics in each run

The Q30 JSONL rows and run summaries contain the full per-region, channel, connector, and role diagnostics. They are emitted by [measure_floorplans.py](measure_floorplans.py) and are retained for every accepted Q30 row, so they can be regrouped without rerunning Java.

- `locality.electricallyAdjacentPadDistanceMean` and `...P95` use every unordered same-net pad pair whose pads belong to different packages. The distance is Manhattan distance between pad centers. A high-fanout net contributes all cross-package pairs; pairs within one package are excluded because they do not represent an inter-package route adjacency.
- `locality.maxNetFanout` is the maximum pad count of any exported net. `locality.maxFanoutNetHplMean/P95` summarize HPL for all tied maximum-fanout nets. `locality.netRoleMetrics.SUPPLY` and `.RETURN` report role-specific net counts, fanout, HPL total/mean/median/p95/max, and maximum-fanout net IDs. Q30 roles come from `Q30_ROOT_NET`; synthetic roles are authored by the fixture reconstruction.
- `locality.regionMetrics.<name>` uses the exact Q30 root region expression. Each region reports component/pad count, courtyard area, courtyard envelope width/height/area/aspect, courtyard fraction of that envelope, edge clearance, component-pair locality, electrical pad adjacency, and incident-net HPL. A cross-region pair is counted for each endpoint region; an incident net HPL is counted once per incident region.
- `locality.channelMetrics.<name>` is deliberately mechanical. Q30 `channel-a` and `channel-b` group exact region labels ending in `-a` or `-b`; synthetic `CH0` through `CH3` remain the fixture's authored region names. Unsuffixed Q30 regions are left unassigned rather than given inferred circuit semantics.
- `connector.byId.<id>` reports source-backed connector identity, region, package pad count, courtyard area/aspect, nearest same-net distance count/mean/median/p95, board-edge margin statistics, and cross-region incidence. Q30 connectors are `{J1, JSA, JSB, JLOAD, JOA, JOB}`; synthetic connectors are `JIN`, each `CH<n>_JS`, and each `CH<n>_JOUT`.
- p95 uses sorted values and linear interpolation at index `(n - 1) * 0.95`. Aspect is width divided by height. All distances use Manhattan units; areas use squared pilot coordinate units.

The structured control has a different electrical graph, package inventory, connector count, and direct authored coordinate arrangement. Its values are included as a reproducible scale/control distribution, not as a matched Q30 target.

## Exact fixture reconstruction and aggregation

`measure_floorplans.py` parses each Q30 developer inspect stream, including part bounds, courtyards, pads, net roles, net boxes, seed, and candidate. It reconstructs the 33-part synthetic fixture from the authored `Q30TwoLayerScaling.java` component list, package dimensions, Java `Random` jitter, electrical nets, exact POWER/CH<n> region names, connector identity, and board outline. Only rows with policy `FULLER_TWO_LAYER`, outcome `SUCCESS`, and `validMixedLayer=true` enter the structured placement corpus. Route fields remain fixture provenance and are not used to infer geometric placement metrics.

Each Q30 summary value is the median across all accepted placements in the 72-row corpus. No best seed, candidate, or row is selected. Rejected attempts remain in corpus counts and rejection reasons. The comparison script validates equal current Q30 source, `Rb30Plan`, exporter patch, synthetic source/raw, signed seed set, candidate population, and structured-success seed set before writing the table.

## Reusable harness

The PowerShell harness accepts an explicit planner snapshot through `-PlannerSource`. It copies the complete source tree into a unique OS-temp scratch directory, replaces only scratch `src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java`, applies the existing exporter and inspect-argument patches in scratch, compiles source/target 7 with JDK8, runs the requested signed seed/candidate pairs, and parses the unfiltered stream. Production files are not patched.

Run a labeled measurement from the Q30 worktree:

```powershell
$worktree = (Get-Location).Path
$jdk8 = '<JDK8_HOME>'
$gwt = Join-Path $worktree '.tools\gwt-2.7.0'
$snapshot = '<PLANNER_SNAPSHOT.java>'
$output = '<OUTPUT_DIR>'

.\docs\task-evidence\Q30\floorplanning\run_floorplan_metrics.ps1 `
  -SourceRoot $worktree -JavaHome $jdk8 -GwtHome $gwt `
  -PlannerSource $snapshot -PlannerLabel '<LABEL>' `
  -MeasurementLabel '<LABEL>' -MeasurementStatus '<STATUS>' `
  -Seed all -CandidateCount 6 -OutputDir $output
```

Create and verify the BEFORE snapshot from HEAD using binary Git blob bytes:

```powershell
$beforeSnapshot = Join-Path $env:TEMP ('tsj-q30-planner-before-' + [Guid]::NewGuid().ToString('N') + '.java')
python -c "import subprocess,sys; open(sys.argv[1],'wb').write(subprocess.check_output(['git','cat-file','blob','HEAD:src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java']))" $beforeSnapshot
$headBlob = (& git -C $worktree rev-parse HEAD:src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java).Trim()
$snapshotBlob = (& git -C $worktree hash-object --no-filters $beforeSnapshot).Trim()
if ($headBlob -ne $snapshotBlob) { throw 'Planner snapshot differs from HEAD blob' }
```

Compare matched labeled runs:

```powershell
python .\docs\task-evidence\Q30\floorplanning\compare_floorplans.py `
  --before .\docs\task-evidence\Q30\floorplanning\before-head-current\floorplan-comparison.json `
  --after .\docs\task-evidence\Q30\floorplanning\after-stable\floorplan-comparison.json `
  --output .\docs\task-evidence\Q30\floorplanning\before-after-comparison.json
```

The harness removes scratch only after successful completion and verifies the resolved path is under the OS temp root with the task-owned `tsj-q30-floorplan-` prefix. Explicit planner snapshots are separately verified under the OS temp root and removed after each run.

## Preserved intermediate and historical corpora

The pre-anchor-fix AFTER corpus is retained under [after-intermediate-anchor-omission](after-intermediate-anchor-omission/) and is marked `measurement.status=intermediate` in its JSON and receipt. Its comparison is [before-after-comparison-intermediate-anchor-omission.json](before-after-comparison-intermediate-anchor-omission.json), paired with the historical pre-Q30-pilot-update BEFORE under `before-head` so its shared hashes remain truthful. It is not used by the final comparison.

The earlier BEFORE corpus under `before-head` is retained as pre-Q30-pilot-update provenance in its receipt and JSON. The root-level `floorplan-comparison.json`/raw/JSONL files are preserved for audit and are labeled `intermediate-concurrent-planner-edit`. Neither historical corpus is mixed into the final table.

## Limits

Q30 rows are placement geometry only. They do not prove routed copper, electrical clearance, solver behavior, player inspectability, normal admission, or diagnostic solvability. Straight-line ratsnest crossings and net-box overlaps are geometric proxies. HPL is a pad bounding-box proxy, not routed copper length. Electrical pad adjacency is a complete pre-route cross-package net-pair metric and does not predict the router's selected topology.

Q30 role and region labels are source-backed exporter/manifest labels. Channel groups use only the mechanical suffix rules described above; unassigned regions are intentionally omitted from channel groups. Region envelopes are axis-aligned bounds of exported courtyards and can exaggerate empty space. Connector p95 includes all connector pads with a same-net pad on another package and can be dominated by a single long connection.

The synthetic fixture differs in graph, package inventory, pad count, connector count, region arrangement, and authored coordinates. Its route successes are retained as provenance; placement metrics do not claim the synthetic route algorithm proves Q30 routeability.

The harness compiles the full source tree in scratch. The comparison hashes the Q30 pilot, `Rb30Plan`, planner snapshot, exporter patch, and fixture inputs; it does not independently hash every unrelated Java dependency. Devbench source edits overlapped this measurement window, although both JDK8 compiles completed successfully. Treat any dependency outside the recorded inputs as a source-tree provenance limit.

`SHA256SUMS.txt` records hashes for the delivered files. Each run directory's `javac.txt` records the JDK8 compiler diagnostics; the observed bootstrap-classpath and unchecked-operation warnings did not prevent compilation.
