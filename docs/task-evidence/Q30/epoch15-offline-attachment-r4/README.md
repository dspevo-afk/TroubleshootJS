# Q30: revised attachment candidate, offline only

2026-10-04. Q30 remains disabled and NOT ACCEPTED. This packet preserves the
actual failed five-fixture Edge run and freezes a revised workspace candidate.
Production host, app, build and all 1,354 consumed inputs remain unchanged.
No Edge, solver, timing, build or broad regression command ran for this revision.

The draft separates DOM acquisition invalidation from main-frame navigation.
Attachment binds exact URL, frame, loader, Document backend and HTML backend.
Frame-tree reads bracket each DOM query in the same CDP session; stale acquisition
cannot adopt IDs or signal ready. Terminal verification permits refreshed frontend
IDs and a DOM refresh when semantic scope stays equal, while rejecting changed
frame/loader/backend identity. Main-frame navigation cancels and reacquires;
child-frame navigation does not invalidate the main scope. A held request also
retains its handler-entry navigation epoch and complete known semantic tuple,
and cannot rebind to a successor or same-loader replacement. With initially
unknown scope, an intervening DOM invalidation aborts ambiguous attribution;
initial acquisition without invalidation may proceed. A known unchanged-tuple
frontend refresh remains valid. This conservative unknown-entry behavior needs
the actual positive canary; no live route/event ordering proof is claimed.
Each route attempts one terminal action. Close allows that action to finish,
then cancels survivors under one shared existing ten-second deadline for unroute,
routes, acquisition tasks and detach. Interrupted actions explicitly fail.
Closed-route aborts consume the same remaining budget; late owned callbacks are
included in the drain. Actual transport/cancellation behavior remains a live gate.

The detached fixture installs a one-shot load listener and a subsequent timer,
checks parsing completed, then opens/writes/closes the replacement document.
Only the replacement HTML advertises PASS:detached. The live canary oracle still
requires a Document **or** HTML backend change; an event-counter change alone
cannot satisfy replacement proof. The detached oracle also requires unchanged
frame and loader plus exact URL/main-frame provenance, so navigation alone cannot
serve as replacement proof. Actual replacement under Edge is NOT_RUN.

69 focused synthetic checks PASS, no failures/errors/skips, operation0.159417s.
They cover stable frontend/DOM refreshes, same-URL loader and successor-frame
changes with reused backend IDs, either backend changing, acquisition races,
main/child navigation, missing loader, close, route provenance, exact negative
receipt errors and deferred fixture structure. Added cases check held-across-
navigation, close during a held or continuing action, cancellation before/during
abort/continue and a hanging-abort negative that must fail within its synthetic
time scale without retry or survivors. The module timeout is restored after that
synthetic check; production/app budgets are unchanged. This is synthetic CDP/receipt
coverage, not proof of the selected Edge protocol path or visible player input.
Outer job PASS0.366s; cleanup0ms at recorded resolution, job0/readers complete.
Read-only preparation PASS0.314381s, outer0.382s with the same cleanup proof:
AST/runtime imports,1354 frozen inputs,118 historical compiled31 packet members,
prior FAIL_CLEANUP and actual failed Edge raw/outer/source hashes preserved.
Selected runtime: CPython3.13.14, Playwright1.63.0, greenlet3.5.6.

Independent review: PASS on final r4 source, no remaining source blocker in this scope. No reviewer ran Edge or timing.
Root reviewed the docs/evidence diff. The draft remains unintegrated.

Earlier offline revisions are retained:54 PASS before review;60 cases with one
double-abort FAIL;61 PASS after close correction;65 cases with one missing abort-
cancellation diagnostic FAIL; final65 PASS after the cause was fixed, then69 PASS after the same-loader entry
fence. R4's first launch was NOT_RUN because a mistyped runner SHA failed before
any child launch; corrected invocation passed. That preflight receipt is retained. No pre-fix
PASS is reused for changed final behavior. Original sources/receipts remain in
workspace and relevant copies/hash bindings are in the packet.

The previous actual Edge suite remains **FAIL**: positive preserved Document/HTML
backends3/5 but the former generation1->2 guard rejected it; the detached fixture
kept9/11 and did not prove replacement. The packet retains exact failure semantics
and raw SHA-256 bindings. Portable copies replace personal paths; original raw
files remain outside the repo and are unchanged. Nothing is deleted or relabelled.

[Candidate summary](candidate-summary.json), [manifest](manifest.json) and
[offline packet](attachment-r4-offline-evidence.tar.gz) include tested sources,
diffs, synthetic results/log, preparation/owned job receipts, failed Edge receipts,
old selected sources and the next command plan. Hashes bind each original raw
input and portable member. Draft sources in the packet are evidence, not the
repository's active host. The exact executable plan remains in workspace
`epoch15-final/attachment-offline-resume-r4/quiet-window-plan.json`.

Next requested scope: a **new** parent-coordinated240s window, one five-fixture
Edge job, operation120s/cleanup15s/reserve105s. Cases: attached positive, missing,
same-URL detached, iframe-only and duplicate negatives. Require intended reasons,
replacement backend proof, exact URL/state, source pins and host/outer cleanup.
Plan `windowGranted:false`, `autoRun:false`; the expired first launch wrapper
must not be reused. A canary PASS only permits consideration of draft integration;
it does not accept Q30 or authorize broader timing work.

Authoritative cold77 remains FAIL:31 PASS/1 TIMEOUT90.221s/45 NOT_RUN.
77roots/308candidates/90s/640units/5s unchanged. Remaining: root75 diagnosis,
final full cold77, enabled build/menu/replay after prerequisites, supported visible
repair/retest, source archive verification and parent acceptance review.
U06/U07/Q60 unstarted. No task process/server/browser remains; raw evidence kept.
Original four cache directories/seven untracked files and sibling Desktop work
are preserved. Local checkpoint only; no push/publish/email.

Protocol field semantics are grounded in the official
[CDP Page schema](https://chromedevtools.github.io/devtools-protocol/tot/Page/)
and [DOM schema](https://chromedevtools.github.io/devtools-protocol/tot/DOM/).
Combining frame/loader/backend identity is the draft's reviewed inference; actual
compatibility with the selected Edge release remains an explicit next gate.

All nine exact offline child PIDs were checked absent at00:51:11UTC; each owned
job also retained job0/readers-complete cleanup. No resources were terminated.

[Final artifact audit](artifact-audit.json) PASS:62 member hashes, safe paths,
privacy, ASTs, exact draft pins, retained failure semantics and1354 unchanged
inputs. Audit operation0.199816s; outer0.258s/exit0, cleanup0ms at recorded
resolution/job0/readers complete. Its exact child28564 was checked absent.
