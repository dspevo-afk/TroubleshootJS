# Reduced nonlinear CircuitJS matrix ordering

Accepted for the exact finite source and cases recorded here. Publication is recorded in the task report.

CircuitJS now deterministically reorders eligible reduced nonlinear matrices during analysis, before saving the original matrix and right side. Greedy minimum degree uses the undirected union of the static off-diagonal pattern. Rows, columns and right side move together, and the inverse updates the existing RowInfo maps. Physical nodes, terminals, element models, stamps, LU, convergence, finite checks and ownership remain with CircuitJS.

The production path is bounded to matrices 16..128 with finite coefficients/right side at most 1e100 and valid maps. Unsupported inputs retain the existing path. There is no cache or seed/fault-specific policy. Dependency interpretation v27 makes the changed floating-point order explicit; circuit dump/model input epoch v7 remains. Current replay/save/proof consumers reject stale v26 identities before live intake.

Native and compiled developer checks cover literal ordering/ties, inverse maps, signed zero, atomic invalid input, independent Crout/known-solution/residual expectations, actual forward-diode/AC-RC KCL/KVL, accepted-step events, node identities, reset/reanalysis and private parent restoration. The existing26 A07 cases and TSJ-A07-SOLVER-2 report stay.

Final source gate facts already established:

| Gate | Result and scope |
| --- | --- |
| Focused native10 | PASS, includes A07/LU/epoch cache+save intake and unchanged routing contracts |
| Native authored100 full fault/repair | PASS62/62,254091 reported assertions,131.634s under1200s |
| Compiled authored100 full fault/repair | PASS62/62,1175.905s operation under6000s;112.913s cleanup under120s;11.444s browser under15s |
| Compiled E06State | PASS10/10; both CIN33_SHUNT50us variants finish24000steps/1.2s/work393 under original limits |
| Final compiled A07 | PASS9/9 routes;68.813s cleanup,8.836s browser |
| Current | PASS9/9; cleanup.success=true,8.317s browser; aggregate cleanup elapsed not exported |
| JDK8/GWT Player and Developer | PASS actual final-source scripts/build.ps1 builds; Developer rebuild reproduced identical qualified input/output digests |
| Player privacy | PASS1 route, artifact checks and2 leakage-sentinel negatives,54.019s cleanup/10.960s browser |
| Normal MEDIUM three development roots | PASS3/3: native request/candidate manifests, full source/compiled-script binding, independent preparation and original payload postprocessing |
| Manual visible input flow | NOT APPLICABLE: no UI or physical interaction code changed; no human repair claim |
| CI | NOT RUN: approved self-hosted runner registration remains prerequisite |

Compiler identity: Temurin8u502/GWT2.7 OBF,1202 input files and357 output files per variant. Developer input3415963aac69c3e5cc21292ec8f3c239671f54e253ec4d9a4c534f49b35bc06e/outputdb929514ea26d2e50a8d9530d757abd7a683a05ef642b1a15c6dc142738e62e8. Player input67c4fbb91a4df6494252d726cb9361e26ccdbcef59c8c559e45ae860810560bb/output36920c116085091bffd5cb0488c8681d21471e3040f5f9e43aa4823db1c4960f.

CIN33 uses protocolq100-electrical-reference-v3/model4/exact string seed9007199254740993, unchanged64-step batches,90s case,640 work,5s temporal and500ms accepted-attempt limits. Shared fault/repair wall19.480s/max batch unit393ms; separate27.154s/max unit1288ms. A batch unit is not the accepted-attempt guard. No broad performance guarantee is inferred.

Scope limits: this renews only the recorded finite authored electrical and three-root regression envelope recorded below. It does not renew the original30-root R12 qualification, establish all-seed support, certify broad E06 nominal fidelity, provide human repair/long-session/modest-host/FPS coverage, or qualify playable100 layouts. Playable100 accepted layouts remain zero; physical routing/access, procedural capacity and later Q100 work remain. Public beta is held; HARD/PSYCHOTIC remain unavailable/unrun and the historical unread boundary stays uncertified.

Earlier failed treatments, compile/fixture/metadata/route failures and their exact sources/receipts remain preserved in task-owned evidence. No failed run is reclassified. The final normal3 gate uses only three approved development roots and the original independent payload checker; the fail-closed Task35 javac adapter and temporary host-capture tools make no electrical or release claim on their own.

The controlled [qualification summary](qualification.json) records exact sources and raw artifact hashes. [Finite3 postprocess output](finite3-postprocess.json) is copied byte-for-byte. Its maxUnitMillis summary field is not used as a measured peak; the original producer budgets and unchanged independent predicates are the qualification boundary.

Temporary producer/capture adapters and their source snapshots remain in the task-owned evidence archive. The Task35 javac adapter excludes exactly the unrelated GWT-only developer verifier and throws if invoked; original production source remains in the complete source manifest and both GWT builds. All physical/mathematical payload checks remain byte-identical to the independent reader. Metadata readers bind actual script URLs and terminal backend identity to full before/after/live file manifests; no invented hash field is added to browser receipts.

## Maintained commands and recorded scope

Commands ran in the isolated candidate worktree using the selected Windows
PowerShell5.1 binary and explicit Temurin8u502. Every output location was fresh.

```powershell
scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -Suite @('A07ExecutionContractTest','Q30OwnedLuZeroRowContractTest','Q30LuPivotCollectionContractTest','Rb100ElectricalPilotContractTest','A10DependencyContractTest','Rb56ModelIdentityContractTest','RelBSessionIdentityContractTest','D01DiagnosticContractTest','P07RelaxedDistanceBoundContractTest','P07OrderingCandidatesContractTest')
scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -Suite @('Rb100ElectricalPilotContractTest') -Rb100FaultMatrix
scripts/build.ps1 -JavaHome <JDK8> -Target Compile -Style OBF -Variant Developer
scripts/verify-a03-browser.ps1 -Gate E06State -TimeoutSeconds 90 -OutputPath <fresh-output>
scripts/verify-a03-browser.ps1 -Gate Q100Faults -TimeoutSeconds 90 -OutputPath <fresh-output>
scripts/verify-a03-browser.ps1 -Gate A07 -TimeoutSeconds 90 -OutputPath <fresh-output>
scripts/verify-a03-browser.ps1 -Gate Current -TimeoutSeconds 90 -OutputPath <fresh-output>
scripts/build.ps1 -JavaHome <JDK8> -Target Compile -Style OBF -Variant Player
scripts/verify-a03-browser.ps1 -Gate Q100Player -TimeoutSeconds 90 -OutputPath <fresh-output>
scripts/verify-e06-accepted-evidence.ps1
```

The source-bound three-root gate used the maintained compiled attribute runner
with the exact approved development roots and existing LED/3 predecessor. The
native producer emitted four production candidate hashes per root. Temporary
capture/metadata tools were checked against actual process receipts and the
maintained runner's emitted schema; their captured files were not edited.
Full native source, compiler/JDK/jar identity, both variant inputs, all357 output
files, exact attached script URL/document backend identity, and all current
source/web manifest rows were checked before the original payload checker ran.
Release/admission remain false in that gate. Detailed local commands, owned
process launches/returns, immutable raw artifacts and tool source snapshots stay
in the task-owned evidence archive referenced by the working checkpoint.