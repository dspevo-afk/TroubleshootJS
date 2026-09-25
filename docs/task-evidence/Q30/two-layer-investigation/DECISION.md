# Q30 routing investigation decision

This is the **pre-P1 developer-only baseline**, retained as an architectural
finding. P1 later improved the same fuller route comparison from 6/72 to 54/72
with generic medium floorplanning; its [review repair](../p1-review-repair/README.md)
is awaiting independent acceptance. [The matched Q30 run](README.md)
holds each 33-package placement fixed across four policies. [The scaling run](scaling/README.md)
uses a different, regular connected control-board graph. Neither changes P09 or
admits Q30 to normal play.

1. **Is single-face routing the main cause of Q30's 0/12?** It is a real cause
   for some placements: the accepted P05 single-face router completes 0/72
   matched placements, while P07 fuller two-layer completes 6/72 across five
   seeds. The shared-hysteretic incidence graph also has a one-face
   nonplanarity witness. Two-layer copper leaves 66/72 placements unrouted,
   so single-face policy is not the whole cause. Placement, net fanout and
   bounded search remain material.
2. **Restricted two-layer on actual Q30:** 0/72. Its eight-via board cap and
   higher transition penalty are unchanged. The observed failures do not
   prove that no alternate restricted route exists.
3. **Fuller two-layer on actual Q30:** 6/72 placements, representing five of
   12 seeds and both sensor/reference arrangements. Every success has real
   copper on both faces, 36–46 plated vias and complete physical validation.
   The six route scores are 165,082–184,760, areas 4,658,000–5,610,000 and
   unique copper lengths 31,850–39,950. These exceed P09 v1's score 70,000,
   area 2,250,000 and copper 16,000 bounds in addition to its zero-via rule.
4. **Largest single genuine two-face success measured:** 180 packages in the
   synthetic structural family (2/3 seeds), at the experiment's upper bound.
   The validated layout has 537 pads, 129-degree return, 15,134,000 area and
   uses the full 48-via cap. This is a measured lower bound on what the P07
   router can sometimes route, not a global maximum or a playable board.
5. **Repeatable measured size:** the synthetic family passed 10/10 seeds at
   80 packages, with 48 vias on every success; 90 passed 9/10. The full
   20–80 initial sweep passed 3/3 at each other sampled size. This establishes
   repeatability only for that graph and regular floorplan. The normal-player
   practical ceiling above P09's accepted 16-package envelope is unmeasured.
6. **Where do failures change type?** There is no monotone component-count
   transition. On exact Q30, P05 reports 70 search limits and two no-path
   results; restricted reports 44 no-path, 18 branch-search, eight
   branch-quality and two total-search limits; fuller reports 50 branch-search,
   ten total-search, six no-path and six successes. In the synthetic family,
   fuller passes 9/10 at 90, 0/7 at 95 and 5/10 at 100 as channel/auxiliary
   structure and outline change. These are bounded algorithm outcomes, not
   mathematical no-route proofs.
7. **Does Q30 need more than a layer switch?** Yes. Fuller routing rescues
   only five seeds and consumes 36–46 vias, so the current placement search
   and physical plan need improvement and readability proof. A new electrical
   topology is not proved necessary for those five seeds; the shared variant
   does require two faces or a topology change under the one-face premise.
8. **Is a medium two-layer envelope justified now?** An explicit versioned
   medium-board *investigation* is justified. Normal adoption is not: no
   routed held-out Q30 population, inspection/probe/service proof, acceptable
   via/copper cost or player-scale demonstration exists. Do not turn
   `FULLER_TWO_LAYER` into a global default or raise P09 caps without those
   proofs. A future policy should prefer the current one-face route for small
   boards and select penalized two-face routing only when its measured benefit
   exceeds transition and interaction cost.
9. **Factory links:** no factory links were inserted in either experiment.
   Their value after two-layer routing is unmeasured. Retain P09's zero-link
   policy. A later separate experiment may test 0, at most one, two and four
   real finite P06 links after a better copper floorplan, with honest package
   accounting and service/probe ownership.
10. **Normal playable two-layer work required:** version a provider-selected
    physical envelope and route policy without Q30 branches in generic
    routing; bind selection into request/replay dependency identity; qualify
    through-hole package/pose and via/segment/copper/work budgets; retain
    `PcbBoardLayout`/`PcbConductorGraph` per-face connectivity, clearance and
    full correspondence checks. P07 already demonstrates face-specific
    rendering, flip and real copper/via probe endpoints in a developer bench.
    Normal Q30 still needs U02 measurement/reference negatives on both faces,
    physical service and mutation after flips, D01 proof of each hypothesis,
    compiled normal-player diagnosis/repair/retest, replay identity, and
    representative inspectability/performance evidence.

**Recommendation at that checkpoint:** keep Q30 unregistered and P09 unchanged.
Improve Q30's floorplan and routing cost/yield, then qualify a versioned medium
two-layer policy against a held-out procedural corpus and normal-player UI.
The electrical brownout repair is independent and documented in
[`brownout-investigation`](../brownout-investigation/README.md).
