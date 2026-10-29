# Runtime correction and unchanged expectations

Before any replacement-cohort execution, the runtime correction removes
repeated defensive copies of the completed placement topology and
recomputation of each immutable footprint's fixed placement envelope. The
graph owns immutable pad links, finishes construction before placement starts,
and has no post-construction writer. Freeze its per-component lists once and
return read-only views to the existing read-only consumers. Each footprint owns
one defensive envelope snapshot; the planner still applies its access and label
margins. Envelope calculation, pad order and rectangle union semantics stay exact.

The envelope cache reduced the failing seed's cold generation from 81.615 s to
66.785 s. The broader valid restore then exposed a separate 5 s work-unit
failure: PUBLISH reached 5.029 s at 73.197 s overall, before the 90 s deadline.
Retain that failure. Reconstruction now uses separate bounded units for isolation,
initial restart, final restart, permanent damage, each final source and final
validation. A history unit performs at most four consecutive actions, retaining
every action's owner checks, replay, solver settlement and checkpoints in the
original order. Its exact declaration is `ceil(history / 4) + sources + 5`,
followed by one atomic publication unit. Reserve all of that work before proof
within the existing 640-work cap. A checkpoint within the batch still enforces
the same cumulative job and current-unit deadlines. No private reconstruction
step publishes a board.

The initial one-action-per-unit cursor passed the exact player wire and both
private/public representative fixtures, but the broader same-seed U06 fixture
rejected at preflight. The diagnostic proof reserves its worst-case work even
when first-ready preparation later uses fewer units. Four-action history units
bound each turn while avoiding that unnecessary reservation inflation. The
failed attempt remains retained; no proof work or history action is omitted.

The exact job/request/owner/controller/graph/installation/save and pristine proof
authorize one private restore permit. Full current dependency and semantic state,
live retained temporal references, fixed solver configuration and transient
continuity are checked across every actual browser yield. Only owned operations
may advance the permitted state. The pristine proof context stays separate.
The controller must retain the exact pristine receipt held by that permit;
starting and completing another admission cannot replace its proof authority.
Final validation and single-use publication remain mandatory; cancellation
revokes work and uses the existing exact predecessor-restoration owner.

Preserve link instances, canonical iteration order, weights, placement search,
five-placement target, routing, epochs, physical identities, CircuitJS analysis,
timesteps, startup recipe, five-hypothesis proof, observations and all limits.
No solver-storage change is adopted: the isolated typed-storage prototype was
slower, and a warmed packed-array prototype showed no meaningful gain. Both are
retained outside Git as rejected experiments.

The pre-change current-source normal-packing census passed all nine groups,
48 cases, exit 0 / 78.325 s, with cleanup PASS. Retain its raw receipt and compare
every family/root geometry hash, acceptance outcome and packing receipt after
the change. Extend the existing geometry contract with independent ordered-link
expectations, read-only boundaries, literal envelopes and all declared package
poses. Rebuild actual JDK8/GWT targets, re-run
the original fourteen cases as regression and the frozen eight-root replacement
cohort as blind evidence. Compare original-case physical fingerprints exactly.
The required real Player fresh-page restore and retest remain acceptance gates.

The current Player gate uses exactly three new unseeded New Board attempts in
order, with no retry or substitution. Preserve the original three attempts and
their failure evidence separately. Require distinct public realizations and an
exact public replay of the third new board. Before replacing the retained board,
repeat the public replay/file rejection cases through real controls. On a fresh
page load the original repaired 2,289-byte file, verify its mixed source settings,
cross-provider stock and retained original, reconnect SENSOR_A and require a new
customer retest. Only then replay that previously diagnosed board to its original
unrepaired state and exercise real isolated removal, compatible cross-provider
stock acquisition and installation, with unrepaired FAIL and repaired PASS
retests. This is ordinary Player regression input; it does not
claim a new human diagnosis or successful human play. Finish this gate before
opening the replacement blind cohort.

These allocation and restore-scheduling corrections preserve electrical and
layout interpretation. Any observed geometry or proof difference is a failure to
investigate, not a new golden to accept silently.
