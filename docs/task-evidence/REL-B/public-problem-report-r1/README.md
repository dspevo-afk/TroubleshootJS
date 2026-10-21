# Public problem reports: bounded software acceptance

Candidate over `fc598e7f8ead61acd06c6d5707a712ea6df8f102`, branch
`codex/q60-faulted-profile-counts`. The owner closed PCB-size experiments.
This REL-B step adds a local report composer under **Share pristine challenge →
Report a problem**. It includes the public replay, interpretation, build and screen,
plus three bounded player-written fields. **Select report** selects the preview;
copying and sending remain explicit player actions. The replay reconstructs the
pristine board, so the form explains that repairs and tray contents need written steps.
Private qualification sessions expose neither public share nor this composer.

The existing JavaScript adapter owns the form and its listener lifecycle. It does
not serialize snapshots, history, URLs, circuits, fault metadata or private proofs.
Drafts survive count-only updates of the same view and reset with the session token.
The added nested disclosure exposed a real focus defect: checking only the closest
closed `details` admitted a hidden inner summary. The filter now checks every
enclosing disclosure, retaining the original collapsed-share test.

| Check | Actual result and scope |
| --- | --- |
| Node syntax / focused DOM contract | PASS, final UI contract **154 assertions / exit 0**. Public allowlist, exact signed-long minimum replay, literal markup, field bounds, selection, refresh, detached controls, Results/error/private state and nested focus. jsdom is a DOM fixture, not a browser or electrical oracle. |
| Production initial view | PASS through real visible keyboard/input. Closed Share reverse-wrap and nested Report focus work. Entered text and selected full read-only report; [initial report](menu-report.jpg). |
| Ordinary random ticket / copying | PASS, first unseeded EASY indicator accepted `7134229391935085532`; no seed substitution/reroll. The menu draft reset. Exact alpha/6 replay, build and TICKET remain; full selection 285 characters. Actual Ctrl+C/session clipboard equals the selected preview; [ticket report](ticket-report.jpg). |
| Reproduction | PASS, copied `tsj-alpha/6/EASY/LED_INDICATOR/7134229391935085532` entered through the ordinary replay form. Same visible complaint, “Indicator does not light.” Original customer retest failed on both launches. |
| Repaired Results | PASS, known regression replay `5577758334702827054`. Original retest failed; power OFF before removing R1, catalog 330 Ω stock installed through the actual tray, power ON and customer retest passed. The report retains pristine replay and only entered repair steps; [Results image](repaired-report.jpg) and [read-only DOM observation](repaired-report.json), selected 0..544. This guided software repair is not human diagnosis or HARD calibration. |
| Served input / reuse | PASS, final served JavaScript SHA-256 `27118a181d1657e2f9440e55930c7154d34e29394d766816004a8e116b3dd10e` equals disk bytes. No Java/scripts changes; three relevant Java inputs and all five compiled modules match the baseline. Existing GWT strongname `13808B6577D799A49A34CCFD1D18738D` was observed. Fresh GWT/electrical/population matrix **NOT RUN**, unchanged inputs reused within this JS-only boundary. |
| Cleanup | PASS for this task: board OFF observed, task IAB tab closed, maintained exact-identity preview stop exit 0 / 2.498 s; recorded worker and launcher exited, port8911 absent, task state files removed. Five earlier blocked profiles remain untouched; prior cleanup limitation is not cleared. |

All four images were inspected. Browser actions used the actual production preview
and visible input through the built-in Browser. Read-only DOM checks are observations,
not injected controller actions. The report never submitted data externally.

Failures are retained in [validation](validation.json): the optional leaf initially
reached zero assertions because jsdom was absent. A task-local registry install
of jsdom26.1.0 resolved that prerequisite. The first actual run failed the retained
collapsed-share assertion; the nested-focus candidate passed152, then the final screen-guard candidate passed154. No pre-fix PASS reused.
[Resource evidence](resources.json) also records the incorrect guessed PowerShell
path that prevented the first stop process from launching; its wrapper0 was not
counted as success. The initial launcher invocation stayed pending while the worker
held its pipe and returned actual0 after the verified stop. Readiness was separately
observed before all UI work. Exact raw logs and dependency lock remain task-private;
hashes are in validation. [Baseline](baseline.json), [served bytes](served-input.json).

Optional independent source review found a narrow lifecycle gap: hidden content
remains connected after leaving its screen. The final guard additionally requires
the captured screen to match. Two new same-token screen-transition assertions prove
obsolete input/selection cannot update the preview or steal focus. The independent
targeted post-fix review passed, source-only; it did not run the tests/browser.
Root ran final154 and checked the final Menu edit/selection in production, with
[fourth capture](final-menu-report.jpg), [served final source](final-served-input.json)
and [separate final cleanup](final-resources.json). The earlier three images and full
ticket/copy/replay/repair flow used source `33a23203...`, differing only by this added
screen equality; those flows were not rerun or relabeled as final-source evidence.

Root reviewed the integrated source/tests. No solver, generation, density, profile, catalog or save
format changes. REL-B remains in progress: real human observations, calibrated HARD
admission and a predeclared normal advanced population campaign remain pending.
No Q100, main merge or email. Publication uses the existing configured upstream.
