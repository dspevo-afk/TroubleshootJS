# P09 - Production physical envelope and layer-strategy qualification

Status: COMPLETE - QUALIFIED. Publication identity is recorded by Git and the final delivery receipt.
Base: `5a9e544c41a343f3943211cf83fba85b05cfcd29`.

## Decision

Normal generation uses `SupportedEnvelope` `THT_SINGLE_FACE@1`. Exactly one
**declared** copper face is admitted: the small legacy families use top copper,
while RB15 uses bottom copper. All packages remain top-mounted through-hole
catalog realizations. This is not permission to mix the two faces on one board.
Production vias, extra drilled holes, raised factory links and isolated placement
domains remain unqualified and are explicitly rejected. P06/P07 remain usable
only through their existing developer-only prototype/proof routes.

The preferred limited-two-layer production policy is **rejected for this epoch**,
not declared physically impossible. P07's frozen comparison and actual two-layer
CircuitJS/interaction proofs establish a working prototype, but not a convincing
medium/large-board production envelope or a generally better area/work tradeoff.
A prototype existing in the tree is not evidence that normal players should get it.

P09 does not implement the separate post-P09 arbitrary-candidate-seed Quick Play
gate, expand the normal seed population, qualify Q30/Q60/Q100, or claim that an
arbitrary circuit with sixteen components will work. The registry has no seed or
family whitelist: its bounds are necessary conditions, followed by the existing
independent physical, live electrical and diagnostic admission checks.

## Frozen limits and enforced boundary

All geometry is in drawing units, not millimetres or electrical safety ratings.
Outline edges are 128..2048, area at most 2,250,000, and aspect at most 3:1.
The limits are 16 packages, 40 terminals, 16 nets, terminal degree 10, one placement
domain, no barriers, 160 route segments, 16,000 unique copper length and 70,000
current route-quality score. The exact catalog package multiplicity caps are in
`SupportedEnvelope.canonical()` and the native policy receipt. Equal package ID
text cannot admit a foreign package object. Catalog geometry variants are retained;
new rotations, mounting-side mixes or package families need fresh qualification.

Pads remain at least 26 units and probe bounds at least 30; trace width remains
at least 9 and visible copper clearance at least 6. The existing independent
geometry validator still checks actual footprints, route clearance, labels,
connectivity, terminal correspondence and declared physical access. Inspection
zoom is required where necessary; fitting a whole board is not a promise that
all its features remain comfortably targetable at every zoom level.

The existing generation ceilings remain 80 placement attempts, up to 800 route
order attempts across placements, and 160,000,000 aggregate search expansions.
Per-request routing keeps the original million-expansion, five-ordering, two-rip
pass, two-victim, six-rerouted-net and 700,000-grid-cell bounds. Normal generation
retains 90 seconds per job, five seconds per work unit and 640 units. None of these
existing budgets was increased. Geometry/work caps bound the workload; sampled
JVM heap figures are not a browser-memory guarantee or per-board allocation.

Normal staged installation checks the envelope before detaching the old graph,
then rechecks it in physical admission and at publication. Legacy normal live
admission and normal composition installation also retain the gate. Developer
verification mode is not a normal-board exemption; only the explicit prototype
owner route stays outside the production envelope. Unknown envelope IDs/versions
and unsupported physical inputs reject with a typed reason. Difficulty profiles
cannot supply relaxed geometry rules. Requests carry the envelope identity;
proof dependency epoch v9 captures its complete policy and resource fingerprint.

## Corpus discipline and interpretation

`PLAN.md` declares calibration/regression and held-out inputs. `frozen-limits.json`
records the pre-held-out policy hash. The first calibration gate exposed an
incorrect single-bottom assumption before any held-out execution; the corrected
freeze permits the existing **single declared** face. All numerical limits were
then retained. `pre-heldout-policy.java.txt` and `policy-hardening.diff` disclose
the later explicit covered-trace rejection, not a relaxation to rescue a seed.

Twenty calibration/regression rows passed. The twelve new held-out seeds yielded
ten physical passes and two explicit rejections. All ten passing held-out layouts
were regenerated and compared exactly. Bounds plus full geometry/connectivity
and exposed pad access were checked; these rows are not fresh CircuitJS diagnostic
proofs for twelve new playable seeds. Held-out elapsed times include the exact
second generation on passing rows, unlike calibration rows. Validation nanoseconds
are separately reported, and heap samples include the whole native test process.

The existing 60-second Quick Play native limit timed out on the initial complete
matrix; an isolated rerun passed without changing the limit. The receipt is retained.
Final verification receipts distinguish that failure from subsequent clean runs.

## Final source qualification and causal repairs

The clean final candidate passes all 48 native suites and independent oracles;
P09 has 58 native assertions. The actual preserved checkout separately passes nine
focused native suites. Both variants pass the real JDK8/GWT five-permutation build,
compiled envelope checks (49 checks plus pre-install owner-preserving rejection),
full RC regression, all eleven Q15 boards, forced rejection/restoration and actual
normal-player menu/seed/ticket/face/pan/zoom/privacy checks. All nine current normal
families pass at seed 0. Actual Q15 reports pass the maintained strict reader and
all 22 malformed-proof controls in both variants. Clean P06/P07 regressions and
actual trace/via/link mouse-input checks remain qualified on identical inputs.

Qualification exposed a cold temporal-health check before its complete startup
profile, and a mutable scheduler snapshot that advanced the saved event queue
while only simulator time was later restored. The fix restores pending events,
FIFO order, original cancellation handles and accepted time without permitting
ordinary backwards dispatch. Five maintained P09 regressions and a supplementary
19-check independent queue driver cover the snapshot behavior. RC public actions
now use existing bounded settlement, and the render verifier proves real empty
slots retain solder pads but no phantom installed body or leads. Earlier failures
remain in `failed-attempts.json`, `scheduler-red.txt` and the temporal regression.

`acceptance.json`, `qualification-phases.json`, both native summaries, both browser
result collections, Q15 reader receipts and `build-identity.json` record exact
scope. `clean-input-hashes.json` and `integrated-input-hashes.json` freeze tested
inputs; only documentation/evidence changed afterward. Native logs are sanitized
and compressed. Clean and integrated variants are not interchangeable: the latter
retains unrelated visual/FPS/packing/tray changes. All 97 original files survive:
94 byte-identical and three shared files with exact inverse-merge proof.

Four curated images distinguish clean front view, integrated front/copper views,
and actual prototype probing. Player inputs here prove normal entry/navigation;
compiled RC/Q15 provide the wrong/correct repair proof. No new human repair study,
FPS benchmark, modest-host certification, universal browser support or complete
unrelated release matrix is claimed. Root reviewed the integrated source and final
boundaries; no new independent model/human review is claimed. Owned test browsers
and listeners closed, while recovery scratch and uncertain older resources remain.

**Next unstarted work:** the separate post-P09 arbitrary-candidate-seed Quick Play
admission gate. The present normal-player seed population remains curated.

Published text-log copies are path-sanitized and trailing-whitespace normalized.
Raw receipt hashes remain recorded; originals remain in task-owned recovery scratch.
