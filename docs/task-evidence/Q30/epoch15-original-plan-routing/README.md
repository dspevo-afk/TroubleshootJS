# Q30 original-plan routing correction: focused checkpoint

2026-10-02T04:23:21.069770+00:00. **Q30 BLOCKED / NOT ACCEPTED; disabled.** Local patch checkpoint from
`d9ec018bb725a29b47c68e72a3e1a5632a5c5a88` for independent parent review before a full matrix.
No merge, push, email, evidence deletion or later milestone.

The minimal reproduction routes the exact original plans in order 8, 4, 10000,
10, rather than their successful cold retry candidates. Only P07 branch ordering
differs between the three isolated, instrumented exports:

| Strategy | 8 / 32 parts | 4 / 34 parts | 10000 / 36 parts | 10 / 36 parts |
| --- | --- | --- | --- | --- |
| Current: nearest on every pass | REJECTED | REJECTED | REJECTED | ACCEPTED |
| Lexical on every pass | ACCEPTED | ACCEPTED | ACCEPTED | REJECTED |
| Mixed existing reverse-priority pass | ACCEPTED | ACCEPTED | ACCEPTED | ACCEPTED |

This branch-only comparison identifies loss of a distinct branch tree as the
common regression. Nearest runs include control-net NO_PATH/BRANCH_QUALITY_LIMIT
and completed routes subsequently rejected by DISCONNECTED_ESCAPE_CHANNEL.
Lexical root 10 exhausts its return branch's unchanged search cap. The correction
keeps nearest on passes 0/1; pass 2 uses lexical for non-return nets and nearest
for typed RETURN nets. It contains no root or Q30 topology condition. All three
net-order passes, placement ranking/candidate order, lexical roots and tie order,
one-million total/100,000 branch expansion limits, via cap and validators remain.
An escape-channel rejection still rejects its candidate. Layout epoch 15 identifies
the geometry change; public save/replay schemas and normal-player staging stay.

| Final-source focused gate | Result | Phase seconds |
| --- | --- | ---: |
| physical | PASS | 47.594 |
| corpus | PASS | 50.947 |
| service | PASS | 269.203 |
| sensitivity | PASS | 419.893 |
| build | PASS | 84.992 |

Physical covers medium original-plan fixtures plus P05/P06/P07/resumption.
Corpus covers exact originals, P07 admission, geometry/work replay and cleanup:
4 accepted, 0 rejected. Service and production solver-step sensitivity each cover
the metadata-declared five faults at all four roots: 20 attempted, 20 passed.
The actual maintained JDK8/GWT build compiled the disabled shipping export with
no overlay. These phase times include native compile/host work; they are **not**
proof of the 90-second cold app budget. Cleanup PASS is logged and independently
checked for recorded process/scratch ownership. Separate cleanup elapsed is
NOT RECORDED, apart from existing per-case receipts.

The final 1,354 raw inputs were rehashed after all gates; only the generic router,
layout epoch declaration and medium test differ from the prior layout-14 export.
Source identity: `fa2c58b0aaa742f01d8bb77dd1d215927103fe5f861c24284a951f96101c59af`. All raw pre-fix failures and
counterfactuals remain in OS temp label `q30-original-route-9e830ef8cad64a8681a600e116414df6`. Gzip artifacts redact user
paths; results.json records raw, portable-payload and gzip SHA-256 separately.
The failed current/lexical diagnostics did not emit standalone .txt receipts;
their logs, result files and exact source/fixture bindings are retained. An initial
packet assembly stopped on that missing file; its partial artifact was verified
and preserved on resume. No test was rerun or evidence deleted for this capture fix.
The instrumented router/fixture exports support replaying the minimal reproduction
from the recorded base with the copied maintained verifier and pinned JDK8.

Commands (run serially, retaining unique output tags):

```powershell
scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -Suite MediumBoardPhysicalPolicyContractTest,P05RoutingContractTest,P06FactoryLinkContractTest,P07TwoLayerContractTest,PcbLayerRoutingResumptionContractTest
scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -Suite Q30NormalCorpusContractTest -Q30CorpusSeeds 8,4,10000,10
scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -Suite Q30ServiceFlowContractTest -Q30ServiceSeeds 8,4,10000,10
scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -Suite Q30SolverStepSensitivityContractTest -Q30SensitivitySeeds 8,4,10000,10
scripts/build.ps1 -JavaHome <JDK8>
```

NOT RUN for changed source: full native 82 / frozen service105 / sensitivity105 /
structural51+26 / independent oracles, cold77, compiled31/D01, disabled controls,
formal source archive verification, private enabled build/menu33/replay3 and final
acceptance. Layout-14 cold77 PASS cannot qualify layout15. Visible ordinary repair/
retest remains BLOCKED / NOT RUN because supported graphical/node_repl input tools
are unavailable in this runtime. No enabled build or normal Q30 launch was attempted.
U06/U07/Q60 remain unstarted. Four pre-existing cache directories and sibling
worktree changes are preserved. The next action is independent parent review of
this exact patch checkpoint, then fresh required qualification on accepted source.
