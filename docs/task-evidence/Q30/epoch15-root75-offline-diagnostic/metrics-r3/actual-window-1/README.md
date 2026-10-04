# R3 actual window 1: observer timeout, app report unavailable

One parent-authorized normal-entrypoint root75 diagnostic ran after a fresh
read-only input/preflight audit. It returned
`DIAGNOSTIC_OBSERVER_TIMEOUT_REPORT_UNAVAILABLE` / exit1, with aggregate CPU
counters measured. App outcome/publication and phase wall rows are UNKNOWN.
Q30 is disabled/NOT ACCEPTED; this150 s observer run does not replace the fixed
90,000 ms app qualification. No extra live run during preservation or analysis.

The [offline analysis](analysis.md) identifies a concrete stale frontend-node-ID
filter in the diagnostic adapter and proposes the smallest correction. Two
synthetic tests against the pinned R3 sources PASS; the correction is NOT
IMPLEMENTED and selected-host proof is NOT_RUN. Original fixed-ID fixture PASS
did not cover this integration. A synchronous pre-status startup stall remains
unresolved, but zero accepted events cannot establish it.

## Actual clocks and cleanup

Window12:12:08.8752128-12:16:08.8752128 UTC,2026-10-04. Main normal entrypoint
finished12:14:57.7725208 UTC/168.8965585 s, exit1, watchdog not used.
Outer operation167,686 ms; inner154,226 ms; host case150.0502934 s.
Host cleanup PASS1.1371614 s/server stopped/no survivors. Outer/inner job cleanup
PASS0 ms at recorded resolution/job0/readers complete. Capture cleanup PASS;
Performance.disable succeeded. CPU setup7.8256 ms/finish16.8449 ms. Metric interval
149.910079 s, ThreadTime77.369066 s, ScriptDuration76.260622 s; no per-stage CPU,
GC/preemption or instrumentation-overhead conclusion.

Independent release check was late, at12:18:33.1488595 UTC, after the grant ended.
It found all12 recorded PIDs absent and zero listeners on exact port58193; no
termination was performed. The host/job receipts show earlier cleanup. Five
baseline Edge processes were untouched. Parent received the actual result/release
handoff before this offline housekeeping. Do not treat the late independent
check as immediate proof within the granted window.

## Bindings and retained data

Live candidate parent HEADa6770f35baced892ba949a6c67a20a20c453ff65.
Manifest6a76b910b89a523e578bb0a3856763378c57d3cf4c61f1c5794c92eefea48d9f;
all six source pins unchanged. The [prior source archive](../root75-metrics-r3.tar.gz)
contains the exact candidate. Before/after1,354 repo and1,526 prepared-app inputs
match. Original cold77 archive hash unchanged;31 PASS/1 root75 TIMEOUT90.221 s/45
NOT_RUN. Fixed77 roots/308 candidates/90,000 ms/640 shared units/5,000 ms active
operation unchanged. No Java/GWT/build/production source edit; rebuild NOT
APPLICABLE to this docs/evidence-only checkpoint.

[Actual result](actual-result.json) binds fourteen sanitized receipts and retained
raw log/manifests. Personal paths are replaced with roles. Full raw logs,
command/process baseline and input maps remain in their original owned locations;
no evidence/profile deletion. The consumed grant is retained and cannot be reused.
Controller raw:`<OS_TEMP>/q30-root75-diagnostic-controller-23bz3fkg`;
diagnostic raw:`<OS_TEMP>/q30-root75-renderer-diagnostic-hfyrcxwd`.

Offline repro raw:`<OS_TEMP>/q30-root75-r3-frontend-repro-nkk62p41`;
fixtures:`<OS_TEMP>/q30-r3-frontend-id-repro-1zjs45q5`. Verbatim repro source is
preserved here for review; run it only from
`<TASK_WORKSPACE>/epoch15-final/r3_missing_report_analysis/frontend_id_repro.py`,
with its sibling pinned `root75-metrics-offline-r3` folder. The actual bounded
command used `launch_owned_review_check.py --timeout-ms30000 --label
root75-r3-frontend-repro -- <physical-python> -B <repro>` and the maintained pinned
serial_runner.py. This is synthetic proof only, with no browser/app result.

Next: parent considers the narrow adapter fix and tests in a new candidate;
no current grant, rerun or acceptance permission. Production/full Q30 gates and
U06/U07/Q60 remain unstarted or pending as recorded in the roadmap.
