# Q30-P1 independent-review repair

This repairs the blocking finding against commit
`98c3a75bb10529f527aebb103345c3092b8de0cc` on the isolated
`codex/q30-multirail-qualification` branch. Q30-P1 is a developer-only
physical sub-gate and is **independently PASS / ACCEPTED** at
`fac582c1150273c01d09bc704dd796c1bf49a135`. Full Q30 remains **BLOCKED**, unregistered and absent
from normal-player admission. P09 and the qualified small-board envelope are
unchanged.

## Defect and repair

`PcbPlacementPlanner.mediumRegionHints()` inserted a semantic region into
both the left and right source sets when ordinary edge connectors landed on
opposite sides. Each distance to that same region was zero. The previous
`left/(left+right)` produced NaN; clamp comparisons did not catch it and
candidate scoring never selected a best placement, eventually reporting
`MEDIUM_CONNECTOR_PACKING` or `MEDIUM_COMPONENT_PACKING` even for valid
placements.

The generic planner now chooses x fraction 0.5 for that zero/zero case. It
also rejects any non-finite completed x or y hint with
`MEDIUM_REGION_HINT_NONFINITE` before placement/scoring. There is no Q30
family, component, region or topology special case.

`MediumBoardFloorplanningContractTest` now builds a connected heterogeneous
20-package board: two ordinary two-pad connectors, eight resistor/capacitor
pairs and two diodes on ten nets. Its standard placement constraints put
both connectors in the same `circuit` region with `Anchor.EDGE`. Seed 0,
candidate 0 **actually places JIN right and JOUT left**. All six medium
candidates receive 20 finite, in-outline placements and scoring work; each
replays identical geometry. The fixture uses the production planner and
footprint registry, with no test-only RegionHint access API.

Before the source fix, the new regression exited 1 with
`MEDIUM_CONNECTOR_PACKING` at `PcbPlacementPlanner.planMedium`. After the
fix it passed 3,774 assertions with cleanup. The red outcome was observed in
the task command output; no separate raw red log was saved and no failing
result was relabeled PASS.

## Reproduction and results

Use a clean checkout of this repair commit, Temurin JDK 8u502 (set
`$jdk8Home` to its directory), and the repository's maintained scripts.
Commands were run from the isolated worktree root:

```powershell
.\scripts\verify-current-contracts.ps1 -JavaHome $jdk8Home -Suite @('MediumBoardFloorplanningContractTest')
.\scripts\verify-current-contracts.ps1 -JavaHome $jdk8Home -Suite @('MediumBoardFloorplanningContractTest','MediumBoardPhysicalPolicyContractTest','Q30PlanContractTest','Rb30PhysicalMetadataContractTest','Q30RelayServiceContractTest','ProceduralFamilyContractTest')
.\scripts\verify-current-contracts.ps1 -JavaHome $jdk8Home
.\scripts\build.ps1 -JavaHome $jdk8Home -Style OBF -Target Compile
```

| Gate | Repair-source result |
| --- | --- |
| Focused generic regression | PASS, 3,774 assertions; cleanup PASS. |
| Six focused contracts | PASS: medium floorplanning 3,774, medium policy 30, Q30 plan 1,397, RB30 metadata 1,713, relay service 110; procedural family partition 10 families/20 cohorts/160 rows. Cleanup PASS. |
| Complete maintained native runner | PASS, exit 0: 66 Java suites, independent seed/value/role oracles, report protocol and scratch cleanup. [Raw log](native-full.log). |
| JDK 8/GWT production compile | PASS, exit 0: five permutations, compile 79.713 s, link 2.603 s. [Sanitized log](gwt-build.log). |

The full native run includes the maintained P09/Quick Play and current
EASY/MEDIUM regression gates. No gate, tolerance, router budget or normal
admission rule was relaxed.

## Fresh compiled workbench check

After the final-source GWT build, `scripts/start-preview.ps1 -Port 8902`
served the compiled app. The in-app Browser loaded developer-only
`circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&tsjQ30Bench=true&tsjQ30Seed=<seed>`
for seed 0 and held-out seed 37. Both returned `PASS`, 1,497 runtime
assertions, `MEDIUM_BOARD@1`, `P07_FULLER_TWO_LAYER`, 33 packages, 82 pads,
82/82 targets inspectable on both faces and `normalAdmission=false`.
Seed 37 visibly switched to bottom copper and back using the production
controls. The [fresh machine-readable receipts](compiled-workbench-results.json)
match the previous seed-0/37 board, layout and copper identities exactly.
The previous [visible probing and screenshots](../inspectability/README.md)
remain valid because this repair changed only generic placement arithmetic,
and these Q30 seeds exercise unchanged finite-region cases.

The browser tab was closed. `scripts/stop-preview.ps1` identity-checked
task-owned PID 15060 and positively released port 8902. This is workbench
inspection, not a compiled normal-player diagnosis/repair/retest proof.

## Evidence hygiene and remaining limits

Six clearly superseded, unfrozen intermediate captures in
`../floorplanning/` were identified for removal:
`q30-current-exact-root.jsonl`, `q30-current-inspect.raw.txt`,
`synthetic-33-success.jsonl`, `floorplan-comparison.json`,
`run-receipt.txt` and `javac.txt`. After path and ownership checks, the
attempted exact-file `Remove-Item -LiteralPath` was rejected by automatic
command policy with the stated reason `blocked by policy`. No alternate
deletion was attempted. Those historical files and all unrelated work remain
untouched; stable before/after and unique failure receipts are preserved.

This repair qualifies only the generic P1 floorplanner defect. It does not
prove Q30 D01 hypotheses, physical mutation and service, normal-player
diagnosis/repair/retest, exact compiled application replay across a qualified
20–40-part corpus, or generation performance percentiles. Q30 stays BLOCKED.
