# Final validation commands

Run from this worktree with the existing qualified JDK8 and Python executables
supplied as `$jdk8` and `$pythonExe`; personal toolchain paths are not committed.

```powershell
./scripts/verify-current-contracts.ps1 -JavaHome $jdk8 -PythonExe $pythonExe -Suite @(
  'PcbCompactionContractTest', 'PcbFunctionalGroupingContractTest',
  'PcbGuardedRouteSelectionContractTest', 'PcbSavedSingleGroupGeometryContractTest',
  'PcbSavedGuardedGeometryContractTest', 'PcbSavedNarrowGeometryContractTest',
  'PcbSavedConnectedGeometryContractTest', 'PcbConnectedPackingHoldoutContractTest',
  'PcbSupportPairPlacementContractTest', 'PcbPinLocalPackingContractTest',
  'U06SessionSaveContractTest', 'RelBSessionIdentityContractTest',
  'Rb56DependencyCaptureContractTest', 'Rb56GeometryContractTest',
  'MediumBoardPhysicalPolicyContractTest'
) -ReceiptOutputPath docs/task-evidence/PCB/pin-local-packing-r1/native-receipt.txt
./scripts/build.ps1 -JavaHome $jdk8
./scripts/start-preview.ps1 -Port 8899
git diff --check
```

The native command compiled final production inputs once and ran the explicit15
suites serially. native-inputs.json freezes consumed sources/scripts/contracts;
final production preview uses the resulting actual GWT output. See README and
saved DOM/report artifacts for actual browser interactions and input audit.
