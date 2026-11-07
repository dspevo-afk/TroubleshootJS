# Validation record

**Accepted and published source prerequisite.** All seven selected
final-source gates pass. Root completed prerequisite review and publication source/scope/diff/hash checks.
The exact implementation commit is remotely verified; one completion Gmail was sent.
[PUBLICATION.json](PUBLICATION.json) records its identity.

Source parent d97472291e8a2a12e57e79109b189821a181eb27; branch
codex/q100-terminal-only-20261107. Final owner SHA-256:
c382f3bc08f8ad65b3501dad57c927dd5c1f9f1c04914ac79edb772f4b4f4e3e.
Focused TerminalOnly test SHA-256:
2c20e0bbc892e1ff8eb4945805b86c28c354425209449e949610e787ad7ce04e.
Frozen plan SHA-256:
a06ae522a3c00461e17c1911c6a3086226d7a46d9c7937d79bcd1a559f995f59.

| Selected final-source gate | Result | Whole maintained command, ms | Wrapper total, ms |
| --- | --- | ---: | ---: |
| Build canary | PASS |2521.8477|2990.5964|
| Player JDK8/GWT build | PASS |98851.3053|99106.9006|
| Developer JDK8/GWT build | PASS |133797.7558|134045.3482|
| Focused actual-owner contract command | PASS |29596.164|29844.6556|
| Retained native regressions A | PASS |28996.6437|29243.5328|
| Retained native regressions B | PASS |54739.9988|55039.311|
| Retained native regressions C | PASS |29971.9984|30232.6741|

Every actual command exited0, with proven termination, absent owned process/log
roots and empty outer stderr. Native cleanup markers are true. Command durations
include maintained internal cleanup; wrapper totals also include root-wrapper
cleanup. Isolated domain/root cleanup durations are NOT AVAILABLE. These are not
per-JVM timings; the only separately recorded case duration is DetachedBranchReplay
830ms. Existing Java bootstrap/unchecked warnings appear in stdout; no warning-free
claim is made.

The ten explicitly selected native suites retain their existing expectations:

| Suite | Recorded assertions |
| --- | ---: |
| TerminalOnly |217421|
| BranchDiagnostics |30|
| LayerResumption |1185|
| RelaxedDistance |2201|
| OrderingCandidates |486652|
| RoleWidthTurnBound |169632|
| FixedPrefixObservation |849|
| AcceptedBranchCapture |3736|
| DetachedBranchReplay |92602|
| NetOrder |2372|

The maintained runner's own pass detection reports16 on each native command.
These selected suites do not imply a full matrix, Current-suite or three-normal-
root renewal. Full30 is excluded.

Player uses selected JDK8, Player OBF and five GWT permutations. Its input
inventory is1202 files, SHA-256
dc1d610e405ec203b2342eed4f3f3946362832e6050704d766844d407c7c863d;
output inventory is357 files, SHA-256
28fe93c15e573b1eac344e0d08c3a4b4932a8e7aa0f1417d91980e19b495c9d7.
Developer input is1202 files, SHA-256
377aa4e9cc53d8da6c1d83e3f9101e17008c1ef18c533f5aa49582c8cb430a80;
output is357 files, SHA-256
67ac65d06a2643c8b62664f0239b0f90c08cad9054edc12ebdd982918d62955a.

The final native available-source inventory records1075 files. It is NOT the
actual compiler argument list or a class census: the maintained runner removes
its classes and source list. No retained compiler/class census or new diagnostic
wire/artifact-provenance qualification is claimed. Existing native JSNI stub
logger, E06 intern and full-grid bridges remain; the separate actual GWT builds
provide the production-source build checks above.

Bounded source review clears only behavior and optional observer copies within
existing operation-unit accounting. Old full Diagnostics, NULL behavior and
mandatory zero-capacity checks remain unchanged. The reviewer withdrew a
primitive-per-cell accounting concern after checking the retained Search.advanceOne
unit. Original time caps remain hard; neither primitive-instruction limits nor
the later callback/snapshot copy ledger are certified by this review.

The only recorded workflow failure was unsupported git sparse-checkout add --no-cone; it made
no mutation and was not a native/product failure. The command was corrected to
the existing-mode add --stdin path. One root .gitattributes rule preserves
the focused test's gated bytes like existing owner/runner text rules; it changes
no runtime source or validation assertion.

No fresh authored cohort Session ran. The original immutable-source facade stays
BLOCKED/unaccepted; separately bound scalar-telemetry inputs/wire/ledger are next
and NOT STARTED. No authored routing, layout, electrical, population or adoption
proof is renewed. Manual Player-flow evidence is NOT APPLICABLE to this private
observation seam. Accepted E/N evidence remains immutable. HARD/PSYCHOTIC are
unavailable/unrun, protected bodies unread here, historical unread UNCERTIFIED.
Playable100=0; beta HELD. Raw process metadata, paths and logs remain private.

Commands used the actual maintained scripts, with the selected JDK8 donor and
unique task-owned receipt paths. Private requests retain the exact absolute paths.

```powershell
scripts/build.ps1 -JavaHome <JDK8> -BuildProcessCanary
scripts/build.ps1 -JavaHome <JDK8> -Variant Player
scripts/build.ps1 -JavaHome <JDK8> -Variant Developer
scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -Suite @('P07TerminalOnlyDiagnosticsContractTest') -ReceiptOutputPath <owned-receipt>
scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -Suite @('P07BranchDiagnosticsContractTest','PcbLayerRoutingResumptionContractTest','P07RelaxedDistanceBoundContractTest') -ReceiptOutputPath <owned-receipt>
scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -Suite @('P07OrderingCandidatesContractTest','PcbRoleWidthTurnBoundContractTest','P07FixedPrefixObservationContractTest') -ReceiptOutputPath <owned-receipt>
scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -Suite @('P07AcceptedBranchCaptureContractTest','P07DetachedBranchReplayContractTest','P07NetOrderContractTest') -ReceiptOutputPath <owned-receipt>
```

Each native command retains the existing 60-second child limits and uses a
480-second outer call. GWT retains its maintained 900-second compiler limit;
the wrapper also allows the existing cleanup envelope. No generation-operation,
group, copied-entry, expansion, via or transition limit is changed. Final source
inventory and real returned process/log-absence checks pass. Source review is
read-only; the root ran all gates. [SOURCE_PROOF.json](SOURCE_PROOF.json) binds
the actual measurements and private receipt hashes without publishing raw logs.
