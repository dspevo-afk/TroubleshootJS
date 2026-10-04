# R2 actual window: empty trace categories, app NOT_RUN

Exactly one normal-entrypoint diagnostic ran in the parent-granted240s window
09:15:43.2814931-09:19:43.2814931UTC on2026-10-04, against local HEAD
`40235228052f3079e9bed65fed68dd9900cd0223`. Candidate manifest SHA256
`74fb6460385c5e33fc9a7e241c21d62c990d1e442dc02c7ac15e1ac412d16cd3`.
No direct worker bypass, rerun, cold77, canary or other live test occurred.

[Actual result](actual-result.json): FAIL_CAPTURE_SETUP_APP_NOT_RUN. The early
[category receipt](renderer-category-preflight.json) retains the complete valid
list `[]`, count0/type list/0 UTF-8 bytes, SHA256
`4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945`.
All five required tokens are absent: devtools.timeline, v8, v8.execute, toplevel,
disabled-by-default-v8.gc. No grouping/alias/malformed-data ambiguity applies to
this empty reply. The correction did retain the evidence before rejection.
R1's unretained category reply remains UNKNOWN; this does not reconstruct it.

Edge154.0.4258.53/protocol1.3/revision5af6e80f702cffe10645c6d6433668f011b739be
returned Browser.getVersion, Target.getTargetInfo and Tracing.getCategories.
The page session targets the exact held cold-75 URL, target/frame
360D6F60515339F958BE7BCCDE6B3493, loader340054BBAD3ED1849E6E24E6F3AFC296,
Document2/HTML4, rendererPID20696 present in the owned Edge set. No wrong target
is observed. Category RPC took22.6893ms wall; it is not a renderer CPU sample.

Validation aborted the held main-frame compiled `.cache.js` before release,
Performance.enable or Tracing.start. No compiled app generation, terminal state,
report, metrics, trace or root75 timing exists. HTML/loader activity is not ruled
out. Secondary absent-report FileNotFoundError does not classify APP_FAIL. An
empty successful enumeration does not prove Tracing.start unsupported or actual
GC/task events absent; those calls and trace-content audit remain NOT_RUN.

Outer operation5.829s/exit1; inner3.348s/exit1; host case0.3153342s. Host cleanup
PASS1.248107s/server stopped/no owned survivors/errors. Outer/inner cleanup
verified0ms at recorded resolution, job0/readers complete. Finalization1.152344s
within20s. Independent [release](release.json) PASS at09:17:00.6354489UTC: all11
recorded worker/host/driver/Edge/preflight PIDs absent, port60046 no listener,
no process termination. Desktop release was reported before docs/commit work;
Core resumed Local Dots. Five unrelated idle Edge startup processes were preserved.
Release hostFinishedUtc05:15:48.059962-04:00 equals raw09:15:48.059962UTC.

Audit-only preflight PASS1.377s operation/1.3864142s wall, exit0/verified cleanup/
job0/readers complete. Both live before/after audits PASS1354 repository and1526
prepared-app inputs unchanged; all six candidate pins unchanged. The74 offline
checks are reused only for those unchanged sources, not runtime support or a new
diagnostic implementation. Original cold77 archive still matches SHA256
`0bf9b802e973c4b691b1e50a4025a338229f17ebc9880d58b588e7533b42a4a3`.

Thirteen sanitized receipts bind raw/copied SHA256; raw inputs, logs, consumed
grant/marker and prior r1/r2 evidence remain untouched in workspace/OS temp.
Compact [packet manifest](packet-manifest.json) binds this packet's files.
Offline receipt/hash/JSON/privacy audit PASS_ARTIFACT_ONLY. This is not the full
Q30 source archive gate. Limited independent review covered the retained r2
receipts, selected page/session and Chromium contract; no tests/probes/edits/live
actions by that reviewer. No new browser, CPU probe, fixture run, build or matrix
ran during this preservation. JDK8/GWT rebuild NOT APPLICABLE to docs/evidence.

[Next diagnostic proposal](next-diagnostic.md): one CPU/wall-only root75 case;
optional metrics unavailable must not abort a correctly attached app. Proposal
only, unimplemented/NOT_RUN; no new grant or auto-run. Do not reuse consumed grant.
Q30 disabled/NOT ACCEPTED; authoritative cold77 remains31 PASS/1 root75
TIMEOUT90.221s/45 NOT_RUN; fixed77/308/90s/640/5s unchanged. Root75 cause remains
UNKNOWN. Root75 correction, final regression/cold77/enabled build/menu/replay,
supported visible repair/retest, full archive and parent acceptance pending.
U06/U07/Q60 unstarted; no push/publish/email/evidence deletion.
