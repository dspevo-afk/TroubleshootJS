# One authorized root75 diagnostic: setup FAIL, desktop released

Parent granted one240s quiet desktop window after preflight. Reviewed local HEAD
`4dc70a19c6f4ee1f120f0f947e92188ab9e18c00`; candidate manifest
`14879cb0249319e6cd70c85bf61a9d382649398fd792fad21c86b99edd3dda96`.
Window08:34:59.9590768-08:38:59.9590768UTC on2026-10-04. Exactly one normal
entrypoint call; no direct worker bypass, r4 rerun or other live test.

[Actual result](actual-result.json): FAIL_CAPTURE_SETUP_APP_NOT_RUN. Edge
154.0.4258.53 returned Browser.getVersion, exact page Target.getTargetInfo and
Tracing.getCategories. Required GC/task category validation failed, so the held
compiled script was aborted before release, Performance.enable or Tracing.start.
One diagnostic case was attempted; application generation/reader audit NOT_RUN.
No state/report, metrics, trace, GC spans or root75 timing were produced. Missing
app-report FileNotFoundError is a secondary diagnostic finalization failure.
The returned category list was not persisted; exact missing member/type UNKNOWN.
No unsupported-category fix or additional live probe is part of this receipt.

Outer operation6.977s/exit1, inner4.516s/exit1, host case0.352419s.
Host cleanup PASS1.1699825s; outer/inner cleanup verified0ms at recorded resolution,
job0/readers complete. Finalization1.1268531s within20s. Independent exact PID/port
check PASS_RELEASED at08:36:34.969051UTC: worker1976, host5792, driver18828, seven
Edge PIDs and two preflight PIDs absent; task port49934 has no listener. No process
was terminated; five unrelated idle Edge startup processes were preserved.

Audit-only preflight PASS1.296s operation/1.3054622s wall, exit0/job0/readers complete.
No competing renderer/test owner was present; a short read-only process snapshot
showed zero CPU increment for unrelated Edge startup services and old server.js.
Both live input audits PASS/unchanged1354 repository and1526 prepared-app files;
six source hashes unchanged. All raw receipts remain in task-owned OS temp and
workspace; these JSON copies sanitize personal paths and bind raw/copied hashes.

Original cold77 archive SHA256
`0bf9b802e973c4b691b1e50a4025a338229f17ebc9880d58b588e7533b42a4a3`
still matches its declared manifest. Cold77 remains31 PASS/1 root75 TIMEOUT90.221s/
45 NOT_RUN. Q30 remains disabled/NOT ACCEPTED;77/308/90s/640/5s unchanged.
U06/U07/Q60 unstarted; no build/push/publish/email/deletion.

Next scope requires parent coordination: resolve/retain actual category support
evidence offline where possible, then a separately authorized live window. The
consumed grant and launch marker must never be reused. Root75 cause and all
remaining full regression/cold77/enabled build/menu/replay/visible repair/retest/
source-archive/parent acceptance gates remain pending.
