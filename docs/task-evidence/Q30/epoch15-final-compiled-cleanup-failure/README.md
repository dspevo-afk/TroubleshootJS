# Epoch 15 compiled31 cleanup failure evidence

This packet records a failed qualification run. It is not acceptance, does not authorize enabling Q30, and does not authorize publication.

## Result

- Overall host run: FAIL_CLEANUP; aggregate stage: FAIL (exit 1).
- Host case rows: 31/31 PASS; terminal state/report checks passed.
- Operation elapsed: 2534.582 s; cleanup elapsed: 5.845 s.
- Cleanup: FAIL; serverStopped=true.
- Cleanup errors: one process-snapshot plus twenty survivor-query failures, each with the blank-detail message process identity query failed: . The ownedSurvivors entry is a query-error record, not a verified count of twenty live processes.
- Strict audit: FAIL with 6 aggregate/source-binding/cleanup assertions. Case outcomes and the seed-35 declared rejection oracle remain separately recorded.
- Fresh disabled shipping GWT build receipt: PASS and bound to this source identity. It does not turn the compiled host cleanup failure into acceptance.

## Source and frozen contract

- Base HEAD: d37c3b3b8581abdeaa9b88b7bfb2349cff958d31
- Source identity: 25f0c65ae6c29a70fd7a9cca7e622f3e7872fa05b1e7b45ba16538bce19fd62a
- Layout epoch: 15; normal-player Q30 flag: false.
- Frozen plan: epoch 4, 77 cold roots, 21 service/sensitivity roots; 90,000 ms job limit, 640 shared-work cap, 5,000 ms active-operation cap.
- Compiled31 spec SHA-256: 898145a040b8920350461961dbad85b94f2d7ef286e95a0ebd347781d291bc97
- Declared physical-rejection oracle SHA-256: 76c4adb18948b1b7770a4228948a4b1f94c627d77a9cc352dd124e1e7bf09838
- Host runner SHA-256: ab84751f0ba0ad7c46cc53e34dc2f69a397bd1186f55d266fe4c44e22e6e6456
- Raw source inputs verified: 1354; unchanged during host run: true.

## Packet and privacy

- 113 text/JSON evidence files: all 31 case/report/state receipts, passive-CDP observation/read/error records, runner/process receipts, input manifests, source snapshots, pointers, build/source bindings, strict audit, frozen spec/oracle, and host log.
- The manifest records original raw byte lengths and SHA-256. Packaged copies redact absolute user, workspace, and temp paths; unredacted raw bytes are not embedded.
- Browser profile contents, screenshots, cookies, history, storage, and private browsing data are not collected. Only the redacted profile path in the runner identity receipt is retained.
- Acceptance remains FAIL / pending. Q30 remains disabled for normal play. No publish or enable action is represented here.
