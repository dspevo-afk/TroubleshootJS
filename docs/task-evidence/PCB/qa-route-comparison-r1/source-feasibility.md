# Source feasibility review

Optional read-only leaf review completed against accepted source owners; no edits,
native route, build or browser action was performed by that reviewer. Root then
read the relevant session/stage/finalization definitions before implementation.

`PcbLayerRoutingPrototype.begin/route` exposes real FULLER_TWO_LAYER routing with
three orderings, one million total expansions,100000 branch expansions,250000
grid cells and32768 work quantum. The supplied placement is its geometric input;
P07 takes neither a route-attempt ordinal nor routing-seed argument. The generator
session retains source seeds and candidate identity for result/finalization.

The complete generator finalization is private. A package-private temporary
adapter inside `SeededPcbLayoutGenerator` can initialize one existing
`MediumBoardPhysicalPolicy.PlacementCandidate` in a fresh medium Session at its
ordinary P07 stage. It must retain `advanceMediumRouting`, incremental P07,
`finishMediumTwoLayerRoute`, `finishRoutedPlan`, `finishMediumSuccess` and selected
border trim. It must not duplicate a router, finalizer or admission oracle.
Only the isolated accepted-source copy receives this adapter; main source stays
unchanged. Its direct method rejects a plan/attempt mismatch before session init.

Historical root42 routed P05 candidates4,2,5 and P07 candidates4,3,1. A one-plan
diagnostic can reproduce the selected P07@1 route and final geometry; it cannot
reproduce the full historical rank/order/selection without the other five plans.
The source scope explicitly excludes that claim and excludes P05 execution.
Supplied candidate count1/rank description is diagnostic metadata; the session's
unrun historical ranking stays empty. Aggregate historical19 orderings/5301842
expansions are not per-route expectations.

The original supplied plan must match the prior selected plan and exact final
metrics. Failure blocks the translated case. Translated input gets fresh placement
validation first; invalid input is fixture FAIL. Normal typed routing or post-route
physical rejection stays REJECTED with its actual reason. Unexpected errors and
source/identity drift propagate as failures. Existing source/graph and complete
physical validation remain authoritative. This review itself provides no runtime
or general qualification proof.
