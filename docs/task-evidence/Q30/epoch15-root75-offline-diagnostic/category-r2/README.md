# Root75 category preflight correction: offline r2

Reviewed against local HEAD `379bee18a3a15503315a8edb5f64cb1e12b86acd`.
This separate candidate changes only `renderer_capture.py` and `offline_checks.py`.
The other four source files, r1 sources/consumed grant/actual FAIL receipts and
compiled app remain unchanged. No live Edge, app case or benchmark ran. Core
resumes Local Dots recovery; no new Q30 desktop window is granted.

The r1 attempt failed category validation before releasing its compiled script;
it retained no category list. The exact missing member/type and actual Edge
grouping remain UNKNOWN. It yielded no root75 timing/CPU/GC evidence and is not
reclassified as PASS by this correction.

Chromium154.0.8037.93 [controller](https://raw.githubusercontent.com/chromium/chromium/refs/tags/154.0.8037.93/content/browser/tracing/tracing_controller_impl.cc)
collects reported category names, and its [registry](https://raw.githubusercontent.com/chromium/chromium/refs/tags/154.0.8037.93/base/trace_event/builtin_categories.h)
contains comma groups. The [category filter](https://raw.githubusercontent.com/chromium/chromium/refs/tags/154.0.8037.93/base/trace_event/trace_config_category_filter.cc)
matches group atoms separately. The [public controller contract](https://chromium.googlesource.com/chromium/src/+/HEAD/content/public/browser/tracing_controller.h)
describes groups/dynamic availability. Thus whole-string-only membership can
reject a valid grouped token. This is a general contract correction, not proof
of why installed Edge154.0.4258.53 failed. The public Chromium tag matches the
reported Chromium version, but exact Edge revision5af6e80f... was not retrievable.
Dynamic registration or genuinely missing categories remain possible.

R2 saves `renderer-category-preflight.json` exclusively immediately after the
category reply, before rejection, Performance.enable, Tracing.start or app script
release. It retains the full bounded valid list with original order/duplicates/
spelling, parsed-list SHA, required categories, matching entry indices/kinds and
ordered missing requirements. The final capture retains the same audit.
It accepts exact literal comma-group atoms; it does not trim, case-fold, use
prefix/substring/wildcard aliases or assume absent tokens are supported.
Malformed/over-bound/invalid UTF-8/empty-group responses refuse membership with
unknown requirements. Bounds:4096 entries,1024 UTF-8 bytes per entry,256KiB total.
Over-bound/malformed data is explicitly not retained as a complete valid list.

All five required categories stay unchanged: devtools.timeline, v8, v8.execute,
toplevel, disabled-by-default-v8.gc. Trace filters, buffer/file/capture/cleanup
limits and app90,000ms/640/5,000ms remain unchanged. Enumeration never certifies
trace events: traceContentAudit remains NOT_RUN pending actual content review.
Early receipt write failure also stops before starting instruments/script release.

Final74 fixtures PASS,0 failures/errors/skips;13 new checks cover literal/grouped
entries, each missing token, aliases/lookalikes, whitespace, malformed/bounded
data, early missing receipt, unchanged filters and exclusive file preservation.
Fixture+input audit1.8909131s; outer2.026s/exit0, cleanup verified0ms at recorded
resolution/job0/readers complete. Input audit1.1187784s verifies all1354 repository
and1526 prepared-app inputs and six final pins unchanged. Child24344 confirmed
absent08:56:10.3260459UTC. No task-owned persistent resource or termination.

Independent source review limited PASS/no blocker; no live actions. Optional
command-error/timeout and exact-boundary fixtures were suggested, not executed;
static code paths fail closed/accept at bounds respectively. No broader test
framework or production changes are introduced. Prior61 PASS is not used as proof
of the changed code; final74 covers this candidate.

Runtime gaps: actual returned Edge categories and missing tokens; whether grouped
entries or dynamic registration caused r1; r2 receipt/route ordering and overhead;
Performance/Tracing start/markers/stream support; trace content/GC/thread identity;
root75 CPU/wall attribution. No selected Edge success is claimed.

Restart from `epoch15-final/root75-category-offline-r2/run_one_diagnostic.py` with
the same repo/pointer/matched-helper/runner/r4-original/physical-Python/deps roles
as the r1 README. Audit-only default; only a new parent-granted240s window may add
`--launch --grant-json`. Use the documented normal entrypoint; direct internal
worker flag bypass remains unsupported. This new manifest requires a new grant;
the supplied grant example is false/expired. Do not reuse r1's consumed grant.

Q30 remains disabled/NOT ACCEPTED; cold77 remains31 PASS/1 root75 TIMEOUT90.221s/
45 NOT_RUN and fixed77/308/90s/640/5s unchanged. Remaining root75/full regression/
cold77/enabled build/menu/replay/visible repair/retest/source-archive/parent
acceptance gates remain. U06/U07/Q60 unstarted; no push/publish/email/deletion.
