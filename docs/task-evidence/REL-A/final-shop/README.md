# Final Shop, launcher and continuity candidate

Base HEAD `ec10aa8b849149c33ae00153873c94c6aa6c9ca6`, branch
`codex/task43p-final-recovery`. [Current preview](candidate.json) execution
`6c330f5040aa15d42d40da1a0695a1177e5c0652a34a832860e7d0eb901e39ae`.
The [1,155 source/test/script inputs](final-inputs-continuity.json) were
[rehashed without drift](final-input-audit-continuity.json). The final
[1,437-file execution/preview audit](final-execution-audit-continuity.json)
proves compiled output and exact process ownership.

Status: ACCEPTED. All required final technical and normal-player gates pass.
Commit/push/notification are recorded after their actual execution.

- Actual JDK8/GWT build: [PASS](gwt-continuity-1.txt), five permutations,
  69.974s compile/1.407s link, exit0.
- Maintained native33 plus independent seed/value/role/report oracles:
  [PASS](native-continuity.txt), exit0 and exact scratch cleanup PASS.
- Q15 eleven seeds/four designs/three faults:
  [PASS](q15/summary.json), 613 assertions plus80 support, 66 directed copper
  readings, 623124ms operation/2ms cleanup, owner restored. Forced failure and
  22 malformed reader canaries and maintained combined companion readers pass.
- Exact off-grid meter endpoints:
  [PASS20](measurement-endpoints.json), actual wire/resistor/open/diode and
  loaded-DC fixtures in both directions; finally cleanup and owner restoration.
  Existing compiled resistance lifecycle also passes.
- Visible [CONT/OHM in both directions](continuity-player-input.json) and
  [complete RB15 diagnosis/repair/retest](rb15-repair-continuity.json): PASS.
  [Before OL](continuity-before-ol.png),
  [before reversed](continuity-before-reversed-fit.png),
  [fixed forward](continuity-fixed-forward.png) and
  [fixed reversed](continuity-fixed-reversed.png) screenshots were inspected.
  Different viewport sizes are recorded; no same-viewport visual comparison.
- Current [Settings/Resources/menu/focus/privacy](ui-focus-continuity.json):
  PASS using actual visible input. The unchanged UI adapter's
  [88 assertions](ui-contracts-refined.txt) and syntax pass are reusable under
  the explicit input audit.
- Windows [launcher](launcher-recovery.json): actual root CMD exit0 in11756ms;
  child exit37 propagates. Real stale/reuse/foreign-state canaries pass with
  verified cleanup. Startup/preview/shared-isolation inputs are unchanged.
- Final [Alpha38](alpha-continuity-summary.json): PASS1245 assertions,230 mutation
  checks,115 acquisitions;915535ms operation/2ms cleanup, owner restored.
  Forced failure and35 malformed reader canaries pass.
- Fresh [compiled regressions](regressions-continuity.json),
  [Quick Play](quick-play-continuity.json), [A10](a10-readers.json),
  [Task43P identified runtime reader](task43p-runtime-reader-continuity.json)
  and [combined readers](combined-readers-continuity.json): PASS.
- Normal replay/error/privacy entry checks are recorded in
  [player-entry-continuity.json](player-entry-continuity.json); publication
  and current acceptance status are in the [ledger](../gates.json).
- [Read-only meter review](continuity-review.json) and bounded
  [session/catalog review](session-review-continuity.json): no blocking findings.
  This review did not run the root's browser/build/native checks.

Earlier `alpha-report.json`, `alpha-summary.json` and their negative/readers
belong to execution33426a5f before the continuity fix: PASS38/1245 assertions,
230 mutation checks,115 acquisitions,958301ms operation/2ms cleanup. They
establish the Shop/transfer delta but are not relabeled as final meter proof.
Final reports use the `alpha-continuity-` prefix. The earlier visible
[MEDIUM repeated transfer](repeated-transfer-player-input.json) moved the
same acquired part A-to-B-to-A, restored the other original, and passed retest.

Preserved failures include incorrect overload assumptions, duplicate empty-slot
ownership, the missing A08 method build error, and an unavailable Q15 tab before
report capture. Fixes and new positive evidence do not erase those records.
A browser-observation timeout is not a corpus timing or cleanup result.

Normal RB15 selection remains limited to
`0,1,2,3,17,42,101,-1,9007199254740993,-9223372036854775808,9223372036854775807`.
Exact arbitrary replay can reject honestly. No budget or tolerance was raised.
No generated broken traces, E08 copper repair, unsupported profiles or
arbitrary-seed population qualification is claimed. The user trials and their
uncertainty remain [qualitative evidence](../../U05/human-trials.json).

Task preview64209 and preserved user previews64207/64206/root launcher8899
remain recorded in the current task checkpoint. Completed visible boards are
powered/locked. The browser tab with the old compiled code is not silently
reloaded. Pre-existing Python cache files remain excluded from publication.
