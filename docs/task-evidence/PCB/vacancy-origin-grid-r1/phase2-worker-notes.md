# Phase 2 vacancy-grid worker handoff

Status: source implementation complete in the isolated copy; routed/native experiment NOT RUN. Root owns integration, registration, and acceptance.

## Scope and behavior

- Added explicit RB56 placement interpretation 16, mapped to density15 while leaving `CURRENT_VERSION=15`, density14, legacy defaults, saved14/current15 replay identities, and the replay epoch unchanged.
- The planner derives `PcbVacancyTargets.originsOnGrid(rawVacancies, 10)` once only for density15 in the existing pin-local, non-anchor route. A nonempty aligned set supplies ring-zero vacancy positions; the raw vacancy vector still supplies preference and pocket scoring. No raster, ring, evaluation, score, candidate-order, or pose policy was added.
- Allowed density15 through the existing selected-board border trim body and the existing medium-canvas owner body. These are the only exact-density postselection gates found by the source-wide equality scan; their bodies are unchanged.
- Added a native16/density15-only observation adapter in the copied edge-trim contract. It validates pre-route plan, intent, and graph fingerprints, calls the shared physical observation body, and changes only the output prefix to `PCB_VACANCY_GRID_CASE`. The existing saved14/trial15 entry guard and assertions remain in place.
- Added a source/replay/raw-candidate target contract and routed common contract with six fixed two-root wrappers. Raw candidate comparison covers candidates 0, 2, 3, 4, and 5 for roots -1022, 42, and 4. Routed inputs use all 14 sealed current15 roots in seven partitions; roots are isolated with per-root failure reporting. Full signed-long seeds are transported as decimal strings / `Long.parseLong`.

## Static review and validation boundary

- Static source diff review confirmed the three production changes are limited to version16 support, the density15 ring-zero origin hook, and the two density15 selected-board gates. No exact14-only density gate was found outside the two updated `SeededPcbLayoutGenerator` checks.
- Compared all 14 embedded baseline rows against `accepted15-baselines.json`: 14 rows, 0 mismatches. The accepted file SHA-256 is `12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816`.
- Static source hygiene scan: 12 assigned Java files; 0 trailing-whitespace lines; 0 `->` tokens. The isolated copy has no `.git` directory.
- No compile, unit test, native test, physical route, or full 28-route run was performed, as required by the worker assignment. The target marker, routed markers, and physical PASS results therefore remain unverified pending root-owned gates.

Expected markers:

- Target: `PASS: PCB vacancy grid trial target contracts `; raw bindings: `PCB_VACANCY_GRID_RAW `.
- Routed input: `PCB_VACANCY_GRID_INPUT `; per-root physical observation: `PCB_VACANCY_GRID_CASE `; per-root failure: `PCB_VACANCY_GRID_FAILURE `.
- Routed group markers: `PASS: PCB vacancy grid trial contracts ` and `PASS: PCB vacancy grid trial Group2 ` through `PASS: PCB vacancy grid trial Group7 `.

## Assigned Java file SHA-256

```text
b039e273e416c3f76ed0bf3ef44d6376508b895e13a88dd4d2dc23f172704d0d  src/com/lushprojects/circuitjs1/client/Rb56Placement.java
36ef9c3991f79076136e838a4d132a45f161c6a2bf498292171abd954176cb0c  src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java
0dfd1890ecedf90350237152caee42cabfa7abae412386250fa74b9e09098cf6  src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java
380b6a81b0a91f7c0f87502b2032635de8c469e64c862d81b9a09683235577e8  tests/contracts/PcbPostSelectionEdgeTrimContractTest.java
92ae4fcb3662b30226d16dc6685973dc4592565ec9d311b908077c0c3dfb8909  tests/contracts/PcbVacancyGridTrialTargetContractTest.java
dc3005211c1dcd62b71c31c11266cca044aa36da8f26a721731063e0930039d0  tests/contracts/PcbVacancyGridTrialContractTest.java
363fd089007126ea94747ad2f68e2633c76406bf7adb161292b3419389acfd8e  tests/contracts/PcbVacancyGridTrialContractTestGroup2.java
83477a06d84036092031c126241172d24a1567d94897a82dc69dfcd275795a2c  tests/contracts/PcbVacancyGridTrialContractTestGroup3.java
01967ce14d13f4f3dd5d562f7fab2178944b3becf0250adaa0e1827569225255  tests/contracts/PcbVacancyGridTrialContractTestGroup4.java
597b803bbc9087ff35e54bfc4bda90d83d88ef4df547acd68d8f536fb50252e6  tests/contracts/PcbVacancyGridTrialContractTestGroup5.java
3739bddd6b04e11f21484b0c07d1b62a6625537548682d634b7042277ba01596  tests/contracts/PcbVacancyGridTrialContractTestGroup6.java
0752e445eddc9f0a493a4f702e4f702d919204863fe22e20a16b621a98283cb6  tests/contracts/PcbVacancyGridTrialContractTestGroup7.java
```

No files outside the worker assignment were edited. No files were staged, committed, pushed, or registered.
