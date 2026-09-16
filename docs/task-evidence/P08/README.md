# P08 — Scalable physical validation and projection caches

Scope: P08 only, on P07 base `31a124a135728f40c62873436fade6bca0382bd3`,
branch `codex/task43p-final-recovery`. Git and the delivery receipt identify the
publication commit; this packet does not embed a circular self-hash.

## Implementation

The selected interval broad phase keeps linear index storage and returns exact
closed-boundary/layer candidates in canonical source order. Existing finite-stroke
contact and centerline-clearance predicates remain authoritative. Segment bounds
and trace arrays are materialized once per validation rather than once per pair.

Immutable graph snapshots index surfaces and their canonical actual connected
pad. Every changed cut snapshot still rebuilds complete connectivity. Solver
correspondence uses an exact two-way physical-island/observed-root bijection;
live solver observations and readings are never cached.

The single-entry copper projection cache keys exact snapshot ownership, all camera
transform values, face and explicit policy version. Paint and picking share these
projections. Pose/package/layer/cut/policy changes cannot reuse a hash-equivalent
foreign owner. Part occlusion remains a live check. No previous validation PASS
is cached, and no local dirty-region approximation replaces global connectivity.

The all-pairs conductor implementation is test-only, independent of candidate
selection, and absent from the production GWT module. The independent analytical
clearance oracle and existing P02 lattice oracle remain separate correctness checks.
Two existing native oracle tests now select the exact router constructor signature
instead of relying on the unspecified order of reflected constructors.

## Scale evidence and interpretation

`scale.json` retains eight measured rows: 15/30/56/100 single-terminal components
and the frozen P03 mixed-package RB15/RB30/RB56/RB100 inventories. Mixed inventories
use deliberately roomy, explicitly routed bottom-face trees. Full current layout
validation passes, but these are physical-only measurement fixtures, not successful
production routing, compact boards, playable CircuitJS families or P09 admission.

Each row retains contact candidates, broad-phase box checks, clearance candidates,
index nodes and primitive payload, seven interleaved current/reference samples
summarized by their medians, actual JVM thread allocations and full validation time.
Index primitive payload is a lower bound, not retained Java heap. Thread allocations
are not peak memory. Wall timings are host-dependent native observations, not FPS.
A separate unchanged-view experiment compares 1,000 cache lookups with 1,000 rebuilds.

The mixed 100-component case contains 535 trace segments and 586 layer lands.
It executes 778 exact contact comparisons rather than 627,760; clearance candidates
fall from 139,615 different-net segment pairs to zero on this separated fixture.
The index still performs 14,160 box checks; candidate pruning is not free work.

The dense 128-segment shared-copper case executes all 8,128 pairs in both paths.
The index adds overhead there and on some tiny workloads. A first grid prototype
was rejected because measured allocation and small-board overhead were worse.
The delivered implementation is an interval index, not that discarded grid.

The P07 frozen large-board routing failures remain failures. P08 does not increase
the normal playable component envelope, adopt two-layer routing, introduce live
trace-cut repairs or claim a universal subquadratic worst case. P09 is separate.

## Final qualification

PASS: all 46 maintained Java suites plus independent seed/value/role, provider,
diagnostic and report/listener protocols. P08 passes 2,022 assertions. The isolated
source and the combination with pre-existing visual/tray edits both compile all
five GWT permutations; the combination also passes nine targeted native suites.

PASS: ten isolated compiled browser runs plus three preserved-worktree runs.
These include P07 positive/forced-negative/mouse copper checks, P06 positive/
forced-negative/link service and 0.05-ohm measurement, layout, all eleven Q15 seeds
(613 assertions plus 80 support assertions), Q15's explicit negative, and actual
normal-player seed preparation/face/pan/zoom/privacy flows. All final owned browser
processes and loopback listeners were released. Four screenshots were inspected.

| Mixed physical fixture | Reference median | Current median |
| --- | ---: | ---: |
| 15 components | 1.011 ms | 0.695 ms |
| 30 components | 1.903 ms | 1.542 ms |
| 56 components | 7.291 ms | 3.837 ms |
| 100 components | 16.038 ms | 6.193 ms |

For the 100-component row, actual thread allocation was 22,144,880 bytes reference
versus 9,076,984 bytes current (about 59% lower). The dense 128-segment case was
0.280 ms reference versus 0.454 ms current and allocated more in the current path.
These are observed medians/samples on a shared workstation, not universal bounds.

## Evidence and preservation

`acceptance.json` is the scope/qualification summary. `native-summary.txt` and
`native-integrated-summary.txt` are explicitly labeled excerpts, not full raw
logs. `scale.json` retains the measurements, including the unfavorable dense case.
`build-identity.json` and each `browser/` receipt bind current source/build identity
to actual loaded JavaScript and owned process/listener cleanup. `visual/` contains
only the four inspected images. `attempt-history.json` retains unqualified attempts
and their raw-log hashes; a later green run does not relabel those attempts.

The 97 pre-existing changed/untracked files remain separate from P08: 95 are
byte-identical, while the renderer and native runner include P08 via checked
three-way integration. Reversing only P08 restores their original user text.
Those unrelated visual/FPS/tray changes are not staged into this milestone.
Raw attempts and isolated source copies remain in task-owned OS temporary storage;
personal absolute paths, browser profiles and uncontrolled build output are not
part of this packet. See `REVIEW.md` and `REPRODUCE.md` for review scope and reruns.
