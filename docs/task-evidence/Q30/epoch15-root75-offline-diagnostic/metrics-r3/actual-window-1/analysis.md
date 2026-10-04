# Offline analysis of R3's missing report

The diagnostic adapter has a concrete frontend-node-ID defect. It can hide both
initial RUNNING and terminal SCREEN_DONE while the app executes normally. The
actual app publication/outcome and root75 generation slowdown remain UNKNOWN:
the run did not record frontend IDs or rejected events, and never queried the
report before its observer deadline. This is a harness diagnosis, not Q30 proof.

## Evidence and cause

The actual Edge154.0.4258.53 receipt records one held main-frame module, correct
root75 URL, stable frame/loader/backend document2/HTML4 through capture completion,
zero accepted state events, null cached state, and no page/HTTP/console/navigation/
observer/listener errors. Performance threadTicks produced 76.260622 s script,
77.369066 s main-thread time over a 149.910079 s metric interval. Those aggregate
counters cannot identify startup, coordinator phases, GC or preemption.

In the frozen R3 archive's `instrumented_host.py:47-53`, the proxy starts metrics,
then calls `read_document_scope()` and discards its returned document and HTML.
That owner method executes DOM.getDocument at `headful_attribute_host.py:436`.
Its semantic scope uses frame/loader/URL/backend identities (:421-448), so it can
still match while frontend node IDs have changed. Only `acquire_document()`
(:450-460) and `attach_html()` (:408-419) normally consume fresh frontend IDs.
The adapter's extra query bypasses those bindings. `attribute_modified()`
(:487-492) continues filtering events against the earlier `self.html_id`.

Chromium154 source calls DiscardFrontendBindings in getDocument (:730-749), clears
node maps without resetting the ID counter (:867-881), allocates increasing IDs
in Bind (:546-556), and emits attributeModified with the current binding
(:2658-2672). Thus the extra query changes frontend IDs even when backend identity
is stable. Applying that behavior to the selected Edge binary is an inference;
its exact frontend IDs were not captured. [Primary Chromium154 implementation](https://raw.githubusercontent.com/chromium/chromium/refs/tags/154.0.8037.93/third_party/blink/renderer/core/inspector/inspector_dom_agent.cc).

The host loop reads only cached `observer.state` (:779-792). A clean timeout does
not take its error-path state reread (:800-803). It attempts the report at :805-806
after the deadline; `read_attribute()` refuses the request at :736-741. The
captured DeadlineExpired confirms that refusal. The later FileNotFoundError is
for the absent saved report, not an app error and not evidence of an absent HTML
attribute. No terminal scope was verified; timeout/final counter scope did pass.

## Narrow offline reproduction

Two synthetic tests import the exact pinned R3 capture/adapter/owner. Modelled
DOM.getDocument refreshes frontend IDs 2/4 to12/14 while keeping backend2/4 and
frame/loader/URL stable. Current R3 drops injected RUNNING/SCREEN_DONE events and
retains INITIAL; a control binds the same validated response before the original
continue and accepts both events. Both paths continue once, abort zero times,
and clear route tasks. Assertions PASS2/2, zero failures/errors/skips; unittest
0.087 s, owned operation333 ms/exit0, cleanup PASS0 ms at recorded resolution,
job0/readers closed; child28952 absent at12:37:54.0122799 UTC. The check launches
one owned Python worker; it launches no browser/app/native fixture child.

This proves the code-level failure under the model, not that actual publication
occurred or the proposed fix passes Edge. The control omits navigation/version
negative cases; those remain required for any future correction. R3's earlier
28-method PASS used fixed frontend IDs (`offline_checks.py:130-133`), masking this
integration. The passing R4 live canary did not include the adapter's added query.

## Smallest proposed correction: NOT IMPLEMENTED / selected-host NOT RUN

Keep the setup semantic scope check, but capture the DOM version before the
asynchronous setup/query. Consume `document, html, scope` from that same response.
Reject closed/lost session, changed DOM version or navigation generation, missing
HTML, and frame/loader/URL/document-backend/HTML-backend mismatch. After those
guards, update `document_id`, `document_backend`, `document_scope` and call the
existing `attach_html(html)` before releasing the original route once. Do not
issue another DOM.getDocument merely to rebind: it resets frontend IDs again.
No deadline, polling fallback, producer change or diagnostic framework is needed
to correct this defect. Retain the original route-abort and cleanup ownership.

Add one focused changing-frontend-ID positive and the existing identity/DOM-epoch
negative guards in a new candidate; preserve this R3 candidate and consumed grant.
Selected-host verification needs a separately authorized parent window. No new
live run is authorized by this analysis.

## Producer and remaining uncertainty

Independent read-only producer review confirmed the prepared module selects
Q30NormalScreenHarness and GWT's host document matches the main-page HTML target.
The actual URL has lang=en and the expected mode/seed. `Q30NormalScreenHarness.java`
calls `super.loadSimulator()` at:31-32 before its settle timer; first RUNNING is
at:126 before coordinator.start at:128. Timer/begin exceptions publish through
failBeforeStart/finish (:429-459). State/report writes target documentElement
(:473-483). A synchronous boot stall before the first status is still possible,
but zero accepted events cannot establish it while this observer defect exists.
Prioritize the adapter correction over adding new producer instrumentation.

Observer review and producer review were read-only, with no tests/process/live
actions. Root reconciled their conclusions using the masked-event defect and
the bounded reproduction. Production/build inputs and all six R3 pins unchanged.
Q30 remains disabled/NOT ACCEPTED; original31 PASS/1 root75 TIMEOUT90.221 s/45
NOT_RUN remains authoritative. The150 s observer timeout substitutes for no
90,000 ms qualification result. Full regression/cold77, enabled build/menu/replay/
visible repair/retest, full source archive and parent acceptance remain pending.
