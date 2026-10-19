# Rejected local passive pocket pass

Private21 is rejected: all28 boards admit physically, but seed2 increases board
area and protected empty space. All14 explicit15 rows reproduce42 pinned non-time
fields; all14 explicit21 boards preserve graph, intent, parts, pads and seeds.
The remaining13 selected physical geometry hashes and four metrics are exact15.

| Seed2 metric | Accepted15 | Trial21 | Delta |
| --- | ---: | ---: | ---: |
| area | 9,178,092 | 10,244,301 | +1,066,209 |
| largestProtectedEmptyRectangleArea | 1,807,000 | 1,909,600 | +102,600 |
| copperManhattanLength | 51,860 | 50,330 | -1,530 |
| vias | 48 | 48 | +0 |

Area grows11.6169%; largest protected empty rectangle grows102600 drawing-square
units. Copper length falls1530 and vias stay fixed. No case improves area or
protected void. Native PASS is distinct from strict fitness FAIL/assessor exit2.

The [selected comparison](selected-comparison.json) shows candidate2 was replaced
by candidate5 on seed2. Existing route receipts give the direct selection cause:
old `P07_FULLER_TWO_LAYER@2=SUCCESS`; trial candidate2 rejects on
`CTRL_RETURN:BRANCH_QUALITY_LIMIT`, and candidate5 succeeds. This establishes a
changed route outcome, without identifying an individual move as its cause.

The [global-score comparison](placement-score-comparison.json) parses all six
existing placement-score records in canonical rank order. The other five
candidate records remain exact for all14 seeds. Candidate2's global cost worsens
in11, improves in1(-1004), and stays equal in2(-1022/7). On seed2 its cost grows
136648, including15900 in the existing electrical term, while rank changes3→4.
The source pass only promises a lower local score; it does not promise this
global ranking cost or an actual route improvement. Total accepted placement
counts increase135–195; route-attempt counts stay unchanged. These are offline
receipt observations, not per-move attribution or a new qualification run.

Fresh final phases: unit3 PASS49.3722267s/exit0 and routes7 PASS200.9823333s/exit0.
Unit records961 local assertions/10 named fixtures,74 existing vacancy assertions
and191 replay-identity assertions. Source/geometry/budget/eligibility/tie and raw
other-candidate parity fixtures pass within their declared scope. The native
phase maintains actual JDK8 and existing work/time limits.

The first unit phase FAIL48.4835479s/metadata2/JVM1 is retained50 artifacts/26 gates.
It compiled then failed at the tie fixture's missing topology-required connector;
no suite PASS is claimed. Final fixture-only correction keeps every production,
corpus and physical-oracle byte exact, adds a neutral connector and preserves the
same tie expectation. Final28-gate freeze includes archive preservation. Both
final phases are fresh. Synthetic reader45 and real PowerShell transport12 pass.

Root integrated review recomputes assessments, all four metric deltas, selected/
global-score comparisons,28 gate identities,15 source deltas,50 failed artifacts,
all1475 accepted inputs and exact COPY restoration/cleanup. A separate read-only
source review finds no blocking defect but explicitly limits positive package/
linked-score, shared-channel rejection, cancellation-latency and tie-trial coverage.

All6 recorded driver births exited, no termination attempted. COPY1475 restored
exact1467 and safely retired; no private state/native/browser/preview/profile
remains. Phase timings include maintained cleanup, not separately measured;
driver-birth verification timings are separate. Original5 unrelated entries remain.

No production promotion, GWT/player, actual21 session owner round trip, solver/
repair, full matrix, unseen/all-seed, manufacturing or general performance claim.
No acceptance limit, seed, routing budget or oracle changed. Next inspect the
existing global electrical/corridor scoring owner and its mismatch with the local
rule; no next policy is selected by this packet.
