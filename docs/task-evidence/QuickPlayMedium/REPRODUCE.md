# Reproduce procedural Medium follow-up

From repository root with JDK8 at .tools\jdk8-download\jdk8u502-b07:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-contracts.ps1 -JavaHome .tools\jdk8-download\jdk8u502-b07 -Suite ControlledIndicatorAssemblyContractTest
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-contracts.ps1 -JavaHome .tools\jdk8-download\jdk8u502-b07 -Suite QuickPlayGateContractTest
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-contracts.ps1 -JavaHome .tools\jdk8-download\jdk8u502-b07 -Suite P03PlacementContractTest
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-contracts.ps1 -JavaHome .tools\jdk8-download\jdk8u502-b07 -Suite Q15ControlBoardContractTest
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-contracts.ps1 -JavaHome .tools\jdk8-download\jdk8u502-b07 -Suite U04SessionContractTest
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-contracts.ps1 -JavaHome .tools\jdk8-download\jdk8u502-b07 -Suite A10GenerationContractTest
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\build.ps1 -JavaHome .tools\jdk8-download\jdk8u502-b07 -Style OBF -ProcessTimeoutSeconds 120
```

The final build observed five successful GWT permutations and successful linking.
