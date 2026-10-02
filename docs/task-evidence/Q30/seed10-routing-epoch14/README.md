# Q30 seed-10 routing repair - layout epoch 14

2026-10-02. Local continuation from `bf5063f4f3eadc989eadd7d1bf7191c2c8fb8a06`. **Q30 NOT ACCEPTED; normal catalog disabled.**
U06/U07/Q60 remain unstarted. No merge, push, publication, email or evidence deletion.

## Cause and narrow change

The frozen original seed 10 is 36 packages, two channels, separate/direct references,
BJT channel A and NMOS channel B, layout seed `-5437028946546985473`, routing seed
`6161596101315970262`. The fixture constructs that plan directly; no admission retry
or 24-package substitution can satisfy it.

P07 routed the remaining endpoints in lexical ID order. That scheduled long,
congested branches before nearby endpoints built a usable trunk. The unchanged
100,000-expansion guard rejected the pre-fix candidate: for example, A_REF
`RREF_LA.1` at `(1030,1940)` toward lexical root `RREF_HA.2` at `(330,790)`.
Diagnostic-only rejection traces and the minimal pre-fix regression retain that
failure. Their logs are evidence of a rejected baseline, not successful gates.

P07 now shares P05's existing deterministic nearest-to-reached-pad selector. The
lexical root and sorted-ID tie order stay fixed; a start pad enters the reached
set only after its complete path publisher finishes. Electrical membership is
unchanged. All six placements, three-per-router candidate breadth, net-order
passes, branch/search/via caps and quality selection remain unchanged. Layout
interpretation advances from 13 to 14 through request/cache/manifest identities.
Public save/replay text remains `tsj-alpha/4`; no historical cross-build geometry
compatibility is newly promised.

With only branch selection changed, P07 placements 0 and 2 succeed; placement 3
still rejects with CTRL_RETURN NO_PATH. The original placement ranking remains
`[0,2,3,5,4,1]`, with 27,192 placement evaluations. The selected placement is 0.
Total policy routing work rises from 4,702,997 to 5,242,412 because successful
routes finish their other nets. This is a routing correction; **new normal-job
90-second timing has not been measured**.

## Focused evidence

| Gate | Result | Total elapsed |
| --- | --- | --- |
| Pre-fix exact seed-10 physical regression | Expected FAIL, maintained exit 2 | 22.764 s |
| Initial branch-order counterfactual | Intermediate PASS, before final epoch/test additions | 38.607 s |
| Final physical/routing/resumption, five maintained suites | PASS | 41.215 s |
| Original seed-10 service, all five frozen faults | 5 attempted / 5 PASS / 0 FAIL | 100.280 s |
| Original seed-10 solver-step sensitivity, all five faults | 5 attempted / 5 PASS / 0 FAIL | 131.342 s |
| Actual final-source disabled JDK8/GWT build, five permutations | PASS | 83.320 s |

The five physical suites retain existing assertions and add independent spatial
ordering/tie and exact-plan witnesses: Medium policy 38, P05 21,976, P06 1,705,
P07 11,686, layer resumption 2,203 assertions. Service uses actual CircuitJS,
physical repair and customer retest. Sensitivity compares the production 5 us
step with the independent 2.5 us reference. Each census child keeps its 60,000 ms
budget. Census totals above include compilation/all children/cleanup and are
**not** evidence that a normal launch meets 90 seconds. Native cleanup PASS;
separate cleanup elapsed time was not recorded.

Maintained commands (one at a time, same retained JDK8):

```powershell
scripts/verify-current-contracts.ps1 -JavaHome $jdk -Suite MediumBoardPhysicalPolicyContractTest,P05RoutingContractTest,P06FactoryLinkContractTest,P07TwoLayerContractTest,PcbLayerRoutingResumptionContractTest -ReceiptOutputPath $physicalReceipt
scripts/verify-current-contracts.ps1 -JavaHome $jdk -Suite Q30ServiceFlowContractTest -Q30ServiceSeeds 10 -ReceiptOutputPath $serviceReceipt
scripts/verify-current-contracts.ps1 -JavaHome $jdk -Suite Q30SolverStepSensitivityContractTest -Q30SensitivitySeeds 10 -ReceiptOutputPath $sensitivityReceipt
# In the exact tracked-source OS-temp shipping export, with no overlay:
scripts/build.ps1 -JavaHome $jdk
```

The diagnostic probe uses exact base production blobs plus one rejection-only
printf. Its native run reproduces failure and cleanup; its outer wrapper also
failed on an invalid exit command after native completion. That host wrapper is
not reported PASS. Raw output is preserved without rerunning unchanged failure.
The early physical wrapper omitted PcbNetRouter from its four-file hash receipt;
the final 1,354-input snapshot and later five-file service/sensitivity receipts
bind that identical source as a supplemental boundary, without rewriting raw
receipts. The shared selector body is unchanged.

The shipping export has no qualification overlay or enable patch and exactly
matches all 1,354 tracked src/war/scripts/tests inputs before and after the build.
Compared with the prior 1,341-input r9 snapshot, six paths differ: this patch's
three production/two test files and the prior checkpoint's physical-census test.
The other 1,335 prior inputs match. The source identity is
`bc08deef78a040a27e6fd7803d401e59fe4b3ed971217c5507bb637129b7c2c4`. The expanded snapshot and reviewed results are
stored here; raw logs/export/helpers remain under the task-owned OS-temp label
`q30-seed10-routing-9e2440a2cafc413d8fe274e278845ae2`. Portable gzip logs normalize
line endings and redact user paths; results.json binds both raw and portable hashes.

Root reviewed the actual integrated patch and evidence. A leaf performed a
read-only identity/lifecycle audit with no blocker and no test execution.
**Parent's independent acceptance review remains pending.**

## Restart boundary

Do not reuse old layout-13 cold77 as qualification of this changed source.
Resume with the parent review, then fresh isolated compiled/cold qualification
of the original unchanged cohort and 90-second budget; preserve D01 rejection
oracles (including seed 35) and investigate any changed outcome. Remaining gates
are full native82, service105/sensitivity105, structural51+26, enabled production
build, compiled31/D01-21, ordinary menu33/replay3, visible 20/30/40 diagnosis,
repair/retest, new candidate source archive verification and final acceptance.
Full matrix was not started in this focused patch turn.

Supported visible input/screenshots remain BLOCKED / NOT RUN: Browser and
Computer Use skills require unavailable node_repl JS/graphical-control tools.
Maintained headless compiled hosts do not certify visible player input. Q30
catalog enable stays unapplied until all acceptance gates pass. Preserve the
four existing untracked Python cache directories, all prior evidence/temp
exports and sibling worktrees. Resource absence is recorded in the task report.
