# Dependency capture timing assessment; cache deferred

Base a4e41a90de483349d7fd61b37f50070aaec5b84a, accepted private11. This is a
measurement clarification, not a cache or production-performance improvement.
The proposed instance-local immutable placement fingerprint cache was not added.

Maintained native capture fixtures now report isolated first context capture and
repeated key capture separately from whole-case elapsed time, plus their actual
placement interpretation1. All existing assertions, solver-zero checks, identities
and limits remain. These legacy-placement normal physical fixtures do not prove
current11 browser performance or normal session loading.

| Parts | First capture ms | Repeat key capture ms | Whole case ms |
| --- | ---: | ---: | ---: |
|40|369.6073|8.5927|5846.7808|
|55|12.3684|15.4880|4986.3642|
|60|7.9089|8.9580|5882.0040|

The40 first-call outlier remains visible. No warm-up exclusion, fixed timing
threshold or speedup claim is introduced. The old multi-second field includes
construction/routing/checks/cleanup; it was not a measurement of context capture.
All three complete canonical lengths and independent SHA256 digests match the
published11 baseline exactly. Actual maintained native gate PASS1166, exec0,
56.179s including proven scratch cleanup. Full matrix NOT RUN. Production source
is unchanged, so GWT and visible product-flow gates are NOT APPLICABLE here.

The read-only immutability audit found no cache invalidation blocker, but no
fingerprint-specific profile demonstrates dominant cost. Cache/fixture worker
kept both proposed files untouched, ran no gates and recommended deferral. Root
ran the measurement and reviewed the small timing-only test diff. Shared tools,
original five untracked entries and Desktop edits are preserved; no resources
remain. Next: role-specific physical trace widths under existing routing, copper
and replay contracts. No main merge/email; realism goal remains active until9am.

Actual command: `scripts/verify-current-contracts.ps1 -JavaHome $jdk8 -PythonExe
$pythonExe -Suite Rb56DependencyCaptureContractTest -ReceiptOutputPath
docs/task-evidence/PCB/placement-fingerprint-cache-r1/baseline-receipt.txt`.
