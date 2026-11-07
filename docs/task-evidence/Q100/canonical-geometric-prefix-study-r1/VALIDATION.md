# Validation and acceptance

Candidate source parent: `0891474cadc4b9e00d7911e81c124721a11efb87`.
Runtime pins: `b3bca1391fcb8e229e6f34dddddfc52ae77447a02a064d9e7eb8a30e7bc8ba7a`.
Baseline: 1,084 rows / 850 production files / 88,031,179 bytes, SHA-256
`dba31be32c8d0d838fa8a1859c2a37a62a619b517515714ef519e59ce4a02de8`.
Seventeen direct BASE files and 25 total direct input rows close unchanged.

| Gate | Result | Actual scope / time |
|---|---|---|
| SOURCE33 | PASS | Final input stability; literal, path/type, mapping, partition/counter and parent controls |
| Historical source functions | PASS | Four modes/parity/two throws/12 cases on sealed S5 data; original-auditor-result fixture explicitly scoped; not a current full host |
| CANARY17 | PASS | Real selected host, 5 host + positive + 5 reader rows; 214.412 s controller |
| SUITE8 | PASS | Fresh native, independent whole consumer and all controls; 396.053 s controller |
| Final JDK8 | PASS | Actual 1,083-source compile; maintained P6 and 31-case P8 native canaries; four native preflight controls |
| Independent consumer | PASS | P4/P5/P6/P8 and six parity flags; 84.984 s call / 86.095 s full closure, below 180 s |
| Semantic CLI | PASS | Fresh genuine bundle plus all 12 exact synthetic rejections; 85.762 s below 180 s |
| Cleanup | PASS | 28 actual receipt owners/process roots absent; owned scratch absent; 79.696 s conservative bound below 120 s |
| Final source/output | PASS | 25 direct inputs and all 80 output files / 12,396,181 bytes rehashed unchanged |
| Production GWT / Player / electrical / population | NOT APPLICABLE | Production inputs unchanged; no renewal claimed |
| Full30 / protected HARD / PSYCHOTIC | NOT RUN | Owner exclusions; historical unread remains UNCERTIFIED |

## Actual native modes

| Mode | Native wall ms | Queries | Fine / coarse | Combined units |
|---|---:|---:|---|---:|
| NULL | 13467.9667 | 14 | 0 / 14 | 6081 |
| OBSERVE | 13061.0147 | 14 | 14 / 14 | 6081 |
| CALLBACK_THROW | 12361.6156 | 1 | No P8 fine/coarse count claim | 1646 |
| REASON_THROW | 12745.4448 | 1 | 1 / 0 | 5965 |

NULL/OBSERVE native work is 13.338 s / 12.912 s; REASON_THROW is 12.546 s.
CALLBACK_THROW receives no P8 work credit. Normal operations use 257 actual plus
wrapper groups; throws use 101. Every applicable 90 s / 640-group / 5 s /
32,768-unit / clone / chunk check passed. Native phase 55,526.1794 ms; cleanup
charge 29,276.8500 ms plus post-cleanup-start host tail 50,419.0109 ms yields
79,695.8609 ms. Operation, cleanup and whole-controller time are separate figures.

## Commands and process boundary

The selected pinned Windows PowerShell 5.1 ran `run-study-outer-r1.ps1`, which
calls `run-study-controls.ps1` through unchanged `scripts/VerifierIsolation.psm1`
SHA `fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c`.
The exact preserved launcher is included. The actual normalized invocations were:

```powershell
& $PS51 -NoLogo -NoProfile -NonInteractive -File $Outer `
  -RepositoryRoot $ExactOriginalRepo -PythonExe $PinnedPython -JavaHome $PinnedJdk8 `
  -InputPinsPath $Source33Pins -InputPinsSha256 $Source33Sha `
  -OutputRoot $FreshExternalCanary -Mode CANARY -EvidenceStem gate-runner-canary-r17
& $PS51 -NoLogo -NoProfile -NonInteractive -File $Outer `
  -RepositoryRoot $ExactOriginalRepo -PythonExe $PinnedPython -JavaHome $PinnedJdk8 `
  -InputPinsPath $Source33Pins -InputPinsSha256 $Source33Sha `
  -OutputRoot $FreshExternalSuite -Mode SUITE -EvidenceStem gate-runner-suite-r8
```

Both fresh whole gates use the actual auditor and source chain, with no historical
audit fixture. Python invocations use the pinned Python 3.13 image with `-I -B`;
native compilation/execution uses pinned JDK8. Exact executable hashes and limits
are in the frozen source contracts and qualification. Raw returned receipts were
retained privately; UTF-8 of returned strings is the transport authority, not a
claim about original OS pipe bytes. Launch intent/provisional host files are not
completion evidence. Actual selected-process exits, final guards, source/output
closure and post-exit observations establish completion.

The qualification records exact raw receipt hashes, including outer
`77b92fdcbcd739522cdd67446373a0de88c8592122187fb4c335b38c5933cc17`,
consumer `db47e7726a7f97b413cafbaa18c6cd33d692503d4256a794ab7406ecbe1a8240`,
and semantic `80db67b96d2c51be43e47a1c2149c08525df9a3b69d8099f83e3dca8109ae388`.
The closeout file is explicitly tool-completion/post-exit observations, not a
fabricated bounded-process receipt. Root reviewed actual receipts and integrated
source changes. Independent source reviews were scoped CLEAR for the loader,
identity, accounting, process census and callback-kernel boundaries; they are not
runtime certificates. All failed prior runs remain failed.
