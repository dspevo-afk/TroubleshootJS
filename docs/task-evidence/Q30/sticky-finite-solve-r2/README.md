# Sticky finite solve r2 — intermediate timing evidence

This packet records a predeclared private comparison of the `lu_solve` change. All eight timing rows and four pair comparisons passed strict parity, cleanup, and input-audit checks. The final source-gate receipt shows all steps exit 0; the separate root-runtime equality receipt passed, comparing all 392 generated WAR files and all 1,525 consumed inputs byte-for-byte.

This is **not normal-player acceptance**. Candidate seed 10014 measured 98,306 ms cold, above the unchanged 90,000 ms limit. Q30 remains blocked from acceptance and enablement; the required full matrix was not run.

## Measured comparison

Cold-time savings (control minus candidate) across the four predeclared pairs were 2,022, 266, 1,441, and 1,459 ms (median 1,450 ms). Proof-time savings were 1,852, 275, 1,394, and 1,349 ms (median 1,371.5 ms). Repeated seed-7 run-to-run ranges were 263 ms for control and 826 ms for candidate cold time, and 175/678 ms respectively for proof time. This focused sample does not establish a population speedup.

The exact plan and execution order are in `receipts/sticky-finite-r2-measurement-plan.json` and `receipts/sticky-finite-r2-sequence-result.json`. The actual sampled solve seed was 10014; an earlier draft note incorrectly called it seed 7. The 90-second miss is retained as a limiting result.

## Source and validation

Each timing arm reconstructs from the committed `docs/task-evidence/Q30/owned-zero-row-scan` baseline plus its exact compressed overlays in `sources/`. The full source audit records 1,339 inputs per arm. `LuFactorizationChecks.java` is identical across arms; only `CirSim.java` differs. The staged index `CirSim.java` and `LuFactorizationChecks.java`, index source audit, integration receipt, and final-gate receipts are included. Original and packet hashes are in the inventory.

The final-gates receipt status is `FINAL_SOURCE_GATES_PASS_PENDING_ROOT_INPUT_EQUALITY`; its companion root-runtime equality receipt is `PASS`. Together they establish passing final-source native, JDK8/GWT, compiled A07/disabled-normal canaries and root GWT build, plus byte equality for the measured inputs and generated WAR. This does not satisfy normal acceptance requirements.

All eight timing rows include host manifests, process and cleanup receipts, errors, strict-reader outputs, and losslessly gzipped raw reports. Four additional lossless reports cover the control/candidate compiled A07 and disabled-normal canaries; one report covers the index A07 canary. Thus the packet contains 13 raw browser reports and six compressed exact source files. Redacted metadata/logs include original/source hashes, transformed hashes, and redaction counts. No raw report bytes were modified.

`tool-references.json` names resolvable committed paths and hashes for the maintained browser runner and strict readers. The executed sequence, pair validator, preparers, gate runners, and root-runtime audit code are also included in `tools/` or `receipts/` as inspection copies; the inventory records their source hashes and any path redactions. Do not run redacted copies as commands. No full WARs, JARs, caches, or uncontrolled logs are included.

`packet-inventory.json` covers this README and all packet files except itself and `packet-inventory.sha256`. Verify the inventory with the adjacent checksum before use.
