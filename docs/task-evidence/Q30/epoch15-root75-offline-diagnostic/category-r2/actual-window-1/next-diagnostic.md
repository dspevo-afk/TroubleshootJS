# Smallest next root75 CPU/wall diagnostic

Proposal only; no replacement candidate has been implemented or launched. Keep
the r2 failure immutable. Prepare one separate candidate by changing the existing
capture/adapter/result handling and focused checks, then freeze new source pins.
Reuse the current exact page/DOM attachment owner, headful host, normal launcher,
bounded process jobs, unchanged compiled app, root75 request and report reader.
No new native sampler, tracing system, browser session or machine setting is needed.

The actual reply was a complete empty category list from the correct page target.
In public Chromium154, [TracingHandler::GetCategories](https://raw.githubusercontent.com/chromium/chromium/refs/tags/154.0.8037.93/content/browser/devtools/protocol/tracing_handler.cc)
delegates to the singleton controller, without a page-target filter. That
[controller](https://raw.githubusercontent.com/chromium/chromium/refs/tags/154.0.8037.93/content/browser/tracing/tracing_controller_impl.cc)
queries central tracing state; [Perfetto extraction](https://raw.githubusercontent.com/chromium/chromium/refs/tags/154.0.8037.93/services/tracing/public/cpp/perfetto/perfetto_session.cc)
can return empty on service-query/parse failure or absent category descriptors.
Early producer readiness is possible, not proven. Inference: switching from page
to browser CDP session has weak support as the next fix. Exact Edge revision
source was unavailable; these Chromium sources do not establish Edge's cause.

Omit all Tracing/category/stream commands for this case. Through the existing page
session, attempt `Performance.enable({timeDomain:'threadTicks'})` and getMetrics
immediately before releasing the held compiled script. Save raw baseline metrics
and host monotonic before/after command brackets. Recheck the held Document/HTML,
URL/frame/loader/generation scope, then release the original script exactly once.
An optional metric failure records CPU_UNAVAILABLE with exact phase/error and
null values; it must not by itself take the adapter's old setup-abort path. Expiration
of only the optional5s metrics slot also yields CPU_UNAVAILABLE: attempt bounded
disable/cleanup, recheck exact route/session ownership, and release the app if safe.
Expiration of the parent/job/route-action deadline, or identity, navigation or
unsafe transport failure, remains fatal. Do not confuse the metrics slot with
those enclosing deadlines. No preflight-only browser attempt or automatic retry.

Let the unchanged cold-75 app reach its ordinary terminal report or existing
observer timeout. At that boundary, getMetrics once more through the same session
and recheck target/document identity. Record Timestamp/ThreadTime/TaskDuration
baseline, final and finite non-regressing deltas only when both samples and scope
are valid. Retain normal app stage/work/terminal report independently through the
unchanged reader; compare matching work, including any timeout-truncated prefix.
Do not inject timing labels, evaluate controller calls, change production code,
save/replay formats or use measurementOnly. Keep the150s observer separate from
app90,000ms/640 shared work/5,000ms active-operation limits.

Use the existing5s bounded setup/final-capture slots and cleanup reserve; attempt
Performance.disable whenever enabling was attempted, including setup failure,
app nonpass, error/cancellation or timeout. Keep listener/route/host/server/process
cleanup and exact PID/port release checks. Required cleanup failures stay FAIL.
Optional missing/malformed/regressing/late samples are unavailable, never zero.
The result must expose separate app outcome, CPU measurement capability and
cleanup status. A valid app report survives optional CPU failure; it still cannot
qualify Q30. Every result retains qualification:false/diagnosticOnly:true.

The [Performance protocol](https://raw.githubusercontent.com/ChromeDevTools/devtools-protocol/master/pdl/domains/Performance.pdl)
defines threadTicks as thread running time. Public Chromium154's
[Performance agent](https://raw.githubusercontent.com/chromium/chromium/refs/tags/154.0.8037.93/third_party/blink/renderer/core/inspector/inspector_performance_agent.cc)
reports Timestamp from monotonic time and ThreadTime from the current thread's
ticks; TaskDuration uses the chosen domain. The installed Edge path is untested.
A valid whole-run ThreadTime delta measures aggregate renderer-main-thread running
time, including GC/DOM/host activity. It is not solver-only CPU, all-process CPU,
per-stage CPU or independently measured GC. Wall minus thread time includes waiting,
idle/scheduling/other effects and does not prove preemption. Setup/terminal command
overhead is bracketed, not subtracted speculatively. No per-proof/slice CPU labels
exist in the frozen normal artifact. A high running-time fraction supports compute
cost as a direction; a large wall gap motivates a later targeted investigation.
Neither result establishes a cause or changes the90s acceptance test.

Before requesting one fresh parent240s quiet window, offline focused fixtures
must cover optional enable/baseline/final failures while app release/report still
occur, identity failures still abort, metric validation/null handling, no Tracing
calls, cleanup/cancellation and independent app/CPU classifications. Audit all
consumed source/app inputs and freeze new pins; do not repeat the unchanged r4
canary. During the window, run exactly one supported normal-entrypoint cold-75
case, preserve all results and release/report the desktop before housekeeping.
If Performance is unavailable, retain the actual app wall/stage result and exact
CPU capability gap; do not schedule another setup-only run automatically.

No window is currently granted. Core's Local Dots recovery has priority. Parent
coordination is required before any new live run; this proposal grants none.
Q30 stays disabled/NOT ACCEPTED with all fixed acceptance budgets/corpus/gates.
