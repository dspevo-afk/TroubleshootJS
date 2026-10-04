# Root75 diagnostic owner reuse: R5 offline checkpoint

R4 actual capture failed before releasing the compiled app. The added setup guard
raised `diagnostic setup crossed held-script attachment`; the proxy aborted the
original route. Case operation 0.313188 s, no terminal state/events/app report,
no final CPU sample/delta. This is a harness failure, not an app nonpass or a
90 s root75 result. The compound guard recorded no operands: its exact rejected
predicate is UNKNOWN. [Preserved actual receipts](../metrics-r4/actual-window-1/README.md)
retain FAIL_CAPTURE, consumed grant and all cleanup evidence.

The proven owner explicitly distinguishes DOM acquisition/frontend epochs from
navigation and semantic document identity. R4 introduced an extra
`read_document_scope()`/`DOM.getDocument` that discards frontend bindings, plus
a version-equality fence. An exact-source synthetic counterexample reproduces
that guard error after a same-frame/loader/backend/navigation owner refresh.
It proves an artificial rejection exists; it does not identify the actual R4
operand. No producer, electrical, browser setting or qualification-budget cause
has been established.

Implemented in a separate six-source R5 candidate: reuse the exact pinned
observer's `document_updated -> acquire_document(version)` and the maintained
owned-job launcher. Remove setup DOM.getDocument, manual frontend rebind and
version equality. Retain one Page.getFrameTree read, which does not discard DOM
frontend bindings, to preserve independent frame/loader checks. Await the
owner's existing html_ready acquisition within its unchanged deadline, then
synchronously require live session, unchanged attachment/navigation, exact
frame/loader/URL/semantic scope, acquired frontend/backend IDs, no pending
acquisition and no owner error. Invoke original route continue immediately after
that proof. Rejections name each failed check and retain actual operands in the
diagnostic binding. Original abort/cleanup owners and optional metrics behavior
remain unchanged. No second observer, polling fallback, new launcher, app
instrumentation or dependency/version/budget change.

This is the smallest supported setup simplification: two candidate files changed
(adapter/fixtures); four files, including the proven observer, metrics collector,
normal entrypoint and verifier, are byte-identical to R4. The necessary nested
Windows job/process cleanup boundaries remain. Q30/public behavior/save/replay
formats and production sources are unchanged; JDK8/GWT rebuild NOT APPLICABLE.

Final 35 offline unittest methods with subcases PASS, zero
failures/errors/skips. Fixture+input audit 2471.7173 ms; input
audit 1101.2634 ms. Owned operation
2608 ms/exit 0, cleanup PASS 0 ms at recorded
resolution, job 0/readers closed. Child17736 and first-attempt child9388 absent
2026-10-04T13:44:21.1451386Z. All1,354 repository/1,526 prepared-app inputs and
six final pins unchanged. Exact nonpass report bytes/hash, missing-report truth,
old-ID rejection/current-ID acceptance, frontend refresh and pending/failed
acquisition are covered.31 original test bodies retained; one former
version-refresh rejection oracle explicitly corrected because acquisition epoch
is not semantic identity; all seven true identity fault cases retained, plus
pending/error negatives. Three new methods. The immutable R4 adapter is a
separately hash-bound regression oracle.

First35-method attempt FAIL with three synthetic-fixture assertion failures:
the actual-owner fake session was not linked to its observer, and the old-oracle
assertion ran before the fake owner acquisition finished. Only those fixture
link/wait issues were fixed; adapter unchanged. Original raw outputs, exact
pre-test source snapshot/manifest and failed receipts are included, not blessed.

Independent final static source/fixture review: PASS/no correctness blocker.
Reviewer did not execute checks/browser/imports/process queries. Nonblocking
scope limit: identity negative subcases prove abort/fatal/cleanup without
individual diagnostic-label assertions. Root reviewed integrated sources.

Duration correction is separate: use DateTimeOffset UTC arithmetic, verified by
five actual selected-PowerShell cases (Zulu, positive/negative offsets, midnight,
negative interval). Original raw release remains unchanged with its invalid
14,400.5279744 s field flagged; actual UTC stamps yield0.5279744 s. Actual original
launch script retained; corrected script was syntax-checked and NOT executed.
The timing helper/tests/receipt are included; no replacement actual evidence.

Manifest `40d92a573000021bb22190aeda4fe40455ed39ada1349d8d638e36e326bfb02b`. Review parent local HEAD
`8409d7248899b4bd16825696f21852fc63eec109`. Workspace folder
`root75-metrics-offline-r5`; default `run_one_diagnostic.py` remains audit-only.
Audit roles are the same as R4. To replay offline checks, retain the immutable
R4 sibling adapter at `../root75-metrics-offline-r4/instrumented_host.py` (oracle
bytes also included under `oracle/` in this archive), physical Python3.13.14,
the unchanged pointer/matched-helper/runner/original-host roles and frozen app.
Run `verify_offline_candidate.py` through the maintained30s owned offline helper.
Do not copy or reuse the consumed R4 grant. No new grant/window/auto-run exists.
No live retry is authorized until parent review; selected-host R5 is NOT_RUN.

No persistent task-owned resource, no push/publish/email. Seven original
untracked caches, sibling Desktop work and all prior evidence are preserved.
Q30 DISABLED/NOT_ACCEPTED; original31PASS/1root75TIMEOUT90.221s/45NOT_RUN remains
authoritative.77roots/308candidates/90,000ms/640shared/5,000msactive unchanged.
Root75 cause/correction, full regression/cold77, enabled build/menu/replay/visible
repair/retest, full source archive and parent acceptance remain pending.
U06/U07/Q60 unstarted. Next: parent reviews this concrete offline correction.
