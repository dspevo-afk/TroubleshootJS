# Source audit before trial

At accepted `ca050e0445168249481b68dbb357f9e7b5553f9f`, the routed compactor
buffers every trace vertex by `clearance(w,w) = w+6` for valid odd widths.
Between two disjoint intervals it retains CHANNEL20, rounded down to GRID10
removal. Thus a cut leaves at least `g(w1)+g(w2)+20` between vertex coordinates,
possibly another0..9 units. It does not filter intervals by net or copper face.

The hard pair rule is `clearance(w1,w2) = (w1+w2+1)/2+6`, with integer division.
For odd widths9/11/13, splitting each self-clearance with an upward half gives
guards8/9/10. Pairwise cut spacing remains the physical pair minimum plus21,
before the0..9 grid remainder. This arithmetic describes only the vertex
interval heuristic. It is not proof that an arbitrary transformed segment,
package escape, isolation corridor or via remains legal.

Physical authority remains `PcbBoardLayout.validateRoutingGeometry`,
`PcbTraceClearance`, `PcbTwoLayerRules`, planner access and route-quality checks.
Full package access envelopes and independent hole/via guards remain occupied.
Only typed placement and route-quality rejections roll back an axis; unexpected
physical `IllegalStateException` still propagates. No catch is widened.

`SeededPcbLayoutGenerator.finishRoutedPlan` compacts every routed candidate
before silkscreen, content crop and medium route selection. Consequently a
smaller interval can affect route comparisons even without rerouting. The
experiment therefore checks complete selected boards, not only a direct helper.
Winner-only pre-silkscreen compaction has no existing retained raw-route seam;
introducing one would be a separate scope and is not part of this trial.

All density consumers were audited. Extending density14 behavior to15 requires
the constraints allowlist and two explicit generator conditions: selected-board
border trim and canvas containment. Other planner/routing decisions use the same
threshold branches at14/15. Dependency identity records density. Private20 also
requires explicit RB56 support/mapping and a distinct private replay token;
CURRENT15 and general generator16 remain unchanged. No new pose policy is added.

Independent leaf source audit was read-only, began at8b883119, finished atca050e04,
and confirmed reviewed production blobs unchanged across that documentation-only
publication. No native, build or browser was run by that audit.

Accepted source SHA-256 bindings:

| Owner | SHA-256 |
| --- | --- |
| PcbPlacementCompactor.java | a7f7dfca5c85c17380be868eda6175dee551172d7695bf6a2cf18012e844f732 |
| PcbTraceRules.java | ffd7848e0016bc726d9098d583ba958c49395a72d95648250a2f6209e9f65149 |
| PcbTraceClearance.java | 19984434852043ce9ef961ae4483cae36028e6deb444ae79fb1488b1c61ad5d4 |
| PcbBoardLayout.java | 68ef5eb7c2ed16f40d64fdf112ee4220498a16b2998d0613c3a2480f6218da6b |
| PcbTwoLayerRules.java | b04f9906d726117412d62b6022ea5b8e2f26bf111063292b0d7560f51b0fb8cf |
| Rb56Placement.java | 101ce980d753db359a9ed26fb66fed626d9f3945dcc4edc6350d8b8b8853cdfc |
| PcbPlacementConstraints.java | f67641475c2034ba8681df9c10c66a55fea4415db01d5d850cdf28d65d47d58a |
| SeededPcbLayoutGenerator.java | 3f84567092370ca1e5b07a8dd84db30b8956af54c0238941c6dcecfd6b1922ab |
| PlayerSessionReplay.java | b0cecbaa71ede48acb490704d125985391c908a19570a0254934c937b65e81cf |

Current trace-width fixtures independently cover equal/mixed-width physical
boundaries, pad/lead escapes and hole clearances. Existing compactor fixtures
cover real routes and immutability, but do not pin the proposed guard. The new
literal actual-compactor fixture is required before corpus execution.

Runtime improvement, all-seed safety, manufacturing suitability, actual20
Save/Load, solver/repair behavior and visible player flow are NOT PROVED here.
