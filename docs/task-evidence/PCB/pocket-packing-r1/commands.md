# Final commands and actual UI inputs

Run from this worktree using the existing qualified JDK8 and Python executables
as `$jdk8` and `$pythonExe`; personal toolchain paths are omitted.

```powershell
./scripts/verify-current-contracts.ps1 -JavaHome $jdk8 -PythonExe $pythonExe -Suite @(
 'PcbCompactionContractTest', 'PcbFunctionalGroupingContractTest',
 'PcbGuardedRouteSelectionContractTest', 'PcbSavedSingleGroupGeometryContractTest',
 'PcbSavedGuardedGeometryContractTest', 'PcbSavedNarrowGeometryContractTest',
 'PcbSavedConnectedGeometryContractTest', 'PcbConnectedPackingHoldoutContractTest',
 'PcbSupportPairPlacementContractTest', 'PcbPinLocalPackingContractTest',
 'PcbPocketPackingContractTest', 'PcbPocketHoldoutContractTest',
 'PcbVacancyTargetsContractTest', 'PcbVacancyDomainContractTest',
 'U06SessionSaveContractTest', 'RelBSessionIdentityContractTest',
 'Rb56DependencyCaptureContractTest', 'Rb56GeometryContractTest',
 'MediumBoardPhysicalPolicyContractTest'
) -ReceiptOutputPath docs/task-evidence/PCB/pocket-packing-r1/native-receipt.txt
./scripts/build.ps1 -JavaHome $jdk8
./scripts/start-preview.ps1 -Port 8899
git diff --check
```

Actual visible IAB URL:
`http://127.0.0.1:8899/circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyRb56=true&tsjRb56Seed=42&tsjRb56Bench=true&tsjRb56PlayerSession=true&tsjRb56Session=true&tsjRb56Persistence=true`

Temporary desktop1280x720 working viewport; no controller injection. Accept ticket,
Run customer retest (expected unrepaired FAIL), Board view/Fit, close popup, actual
top capture. Right mouse pressed/released at observed chip body(533,472) opens
UAC Component actions; actual View schematic button opens public pinout. Capture/
Close schematic. Board view/View bottom copper/close popup; actual underside.
Return top/DC V; actual left-red mouse pair at(794,355), right-black pair(806,355)
on observed U1 pads measures4.996V and does not open the context menu. Public
Probe terminal selects U2A OUTPUT thenQA G, real Place red probe:4.846V vs479.747uV.
Turn multimeter off, powerOFF, select RDA/Component and inspect nominal bands.
Shop medium1kΩ Part55; after authoritative guard enables removal, remove Part30,
real tray select55/Install as Channel A/RDA. PowerON/customer retest PASS, capture.
Return/powerOFF/Main menu/Save session actual download; byte-exact evidence copy.
Actual Load session/file chooser/FileReader on repaired11-54.tsjsave. Restoration,
subsequent retest/Close and explicit saved10 checks are recorded in final receipts.

Four actual unedited captures, corresponding DOM snapshots, launched five-field
source/scripts/web identity, native inputs and source review hashes are retained.
Canvas mouse events are ordinary UI input via the supported IAB CDP capability;
they do not invoke app controllers or alter state outside the observed controls.

After the narrow-view admission correction, focused native runs selected
`FaultObservationInspectionContractTest,U01ViewportContractTest`; final native21
selected the original19 above plus those same two suites. Actual final source
freeze is admission-fix-r1/final-inputs.json. Final21 PASS371.157s including scratch
cleanup; final JDK8/GWT five PASS100.654s including cleanup. Build launch overlapped
completed suite wrapper cleanup by2.6s; terminal exit0/cleanup and the scheduling
error are retained in admission-fix-r1/sequencing-error.json. Preview started only
after final build terminal exit0. Five actual served provenance fields matched.

Final build82ED61DD8F7C1E462AA512643875A718: create blank IAB tab, reset temporary
viewport, confirm319x670 before navigating to the same URL. Cold proof PASS86.952s/
627; Accept ticket/Run customer retest gives expected unrepaired FAIL status.
Main menu/Load session with real file chooser reads repaired11-54.tsjsave;
reconstruction PASS84.918s/627 preserves OFF/one loose part. Actual powerON and
Run customer retest open Customer retest passed; Close session yields overall
PASS268.537s and all seven cleanup flags. Warning/error logs empty.

Create a separate blank tab, set/confirm1280x720 before navigation; close the
completed narrow tab. Cold proof PASS84.574s/627. Accept ticket/Main menu/Load
session reads the committed previous pin-local-packing-r1/repaired10-54.tsjsave
through the real file chooser/FileReader. Final restoration/retest/Close results
and exact owned cleanup are recorded in admission-fix-r1 final receipts.
