# A04 candidate reconciliation

Scope is bounded electrical/physical construction for the existing resistive canary and controlled-indicator generator versions 1/2/3. A05 and later milestones remain unstarted. This record describes completed validation; independent final source review is PASS, with publication performed by the root after final Git reconciliation.

## Final execution evidence

`runtime-a04-final.json` is PASS with three routes and cleanup=true. The positive route executes 204 context-contract assertions and 238 runtime assertions: real same-context coordinate separation and explicit joins; exact terminal/polarity/value correspondence; completion and revocation negatives; injected construction failures; three migrated generator versions; original-owner restoration. The other routes prove expected explicit failure and an absent developer report with debug disabled.

`runtime-regressions-final.json` is PASS with all nine required routes and cleanup=true: A03 positive/forced/debug-off, Task41, Task46, Task47, Task48, Task49, and A02. Task47/48/49 retain 1,540 / 755 / 1,053 assertions, the original physical-truth checks, and their actual repair/retest cases. Task41 retains its full 14-route / 128-solver-sample corpus. These developer checks are not represented as new ordinary-player visible-input evidence; A04 introduces no visible workflow.

Both receipts match the recomputed current `execution-provenance.json`: source, scripts, production web output, full execution digest/count, unchanged preview script, and unchanged verifier-isolation module. `candidate-inputs.json` independently records all 987 source/script/test file hashes at the final native/build freeze; the inputs remained exact through both final browser gates.

## Replay: exact identity versus numerical observation

The final native and compiled A03 vectors and all three canonical manifests byte-match the accepted A03 artifacts. Task41, Task46, and A02 complete report payloads also match exactly. No accepted fixture or golden was rewritten.

Task47/48/49 full JSON text is deliberately NOT described as byte-identical. Its complete field-by-field comparison finds only transient analyzed solver-node renumbering and small live numerical changes from the new construction/allocation order. Every old-to-new terminal-node mapping is bijective within each of the eight cases, preserving the full observed terminal partition. All identities, recipes, packages, geometry, repair/fault metadata, assertion results, and other report fields are exact.

Task47 differs only in 48 incidental node numbers. Task48 has 120 node differences and 256 finite numerical differences, maximum 9.172564929826876e-10. Task49 has 120 node differences and 246 numerical differences, maximum 1.216083234112375e-09. Every diagnostic sample remains within its original recorded tolerance; the largest delta is 1.4653920183955624e-08 of that tolerance. A supplemental 1e-8 absolute comparison bound was also enforced for these observation-only fields. It does not replace or relax the existing production assertions. Details and input receipt hashes are in `compatibility-reconciliation.json`.

The construction context preserves the exact authoritative persistent pad endpoint object in detachable connection bindings. This is distinct from transient solver node numbering: the unchanged Task47 reference-identity assertion and the new A04 reference canary both pass.

## Environment, failed attempts, and retained limits

Earlier 90-second Task41 application runs timed out with cleanup=true. A scratch-only debugger diagnostic mapped the active stack to the existing diode/PCB placement path, not a new provider ownership wait. The final run explicitly selected the existing configurable 300-second application-route budget and completed the unchanged corpus. A04's three-route final run used 90 seconds. No 500 ms ownership, listener, revalidation, cleanup, or exit-code limit was changed. The diagnostic was recorded as an intentional non-acceptance failure, never as PASS.

The initial A04 transport failures in `compiled-browser.json` and `compiled-browser-tcp.json` are retained historical failures. The resumed transport and final runs use fresh owned contexts, profiles, and leases. Disposable listener-query initialization warms code only; it never reuses target ownership observations. All final candidate browser/preview resources report successful cleanup. Historical A03 orphan/profile evidence remains untouched.

The shared verifier-isolation module remains byte-identical to accepted A03 SHA256 `202867ead5c38202538ae0e22991ecd6bc5fb0d12f8c9e66860b23ef31425676`. The core preview and Gate B verifier were not modified. Their accepted lifecycle qualification is reused only for those unchanged dependencies; no fresh complete Gate B run is claimed here. The added A04 report reader is covered by 103 strict parser assertions, positive/negative compiled routes, and final actual owned-resource cleanup.

Physical materialization remains bounded to existing fixed parts and mutable resistors. The multi-unit/shared-package fixture proves a data/conformance shape, not a new IC implementation. General leaf conversion, alternative family selection, multi-provider mutation transactions, MCU/import support, and arbitrary deep rollback remain outside A04. Unsupported interpretation-bearing saved-action data remains safely rejected as documented in A03.

The central assembler was reduced from 1,617 to 780 lines while migrating both existing paths. Exact changed central/provider files and retained integration boundaries are documented in the architecture and final diff; no arbitrary two-file extension promise is made.
