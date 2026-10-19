# Whole-plan pocket refinement: source audit and frozen scope

At published basea3a0b851, accepted production/default15/general16 are unchanged.
The prior private21 finite trial is rejected:13 selected geometries stay exact,
seed2 switches from successful candidate2 to a larger candidate5 after modified2
rejects CTRL_RETURN branch quality. Its local score differs from the authoritative
whole-plan score, which increases in11 of14 candidate2 plans. This motivates a
new isolated scoring experiment; it does not prove which individual move caused
the route rejection or that any next rule will shrink a PCB.

`MediumBoardPhysicalPolicy` already owns the complete placement score and ranking.
Its immutable PlacementScore uses8 times role-weighted per-net Manhattan pad MST,
4 times region-center bounding spans,6 times connector-to-nonconnector same-net
nearest-pad distance,12 times coarse MST corridor overlap, plus outline/courtyard/
demand areas divided by1000. Role weights are SUPPLY/RETURN6,HIGH_CURRENT4,
CONTROL2,SIGNAL1. The MST uses deterministic Prim over canonical pads; corridor
rectangles expand each MST edge bbox20 and count foreign component courtyards by
max(1,overlapArea/100). This is a ranking proxy, not electrical physics or a routed
clearance proof. The local planner instead uses incident pairwise links, partial
peers, Manhattan-L obstruction and content/group/hint terms.

Private22/density15 adds only a nonnull complete-plan scorePlan adapter delegating
to that same owner. Removing the adapter must reconstruct the accepted policy
byte-exact. The existing scorer, weights, ranking, initial greedy placement and
all other candidate algorithms stay unchanged. No duplicate formula/state enters
production. Independent unit arithmetic may repeat literal expected fixture
values to test the existing owner; those expectations are not runtime scoring.

The new candidate2 pass keeps21's bounded advisory origins and rigid translations,
but evaluates complete proposed plans with scorePlan.total. Only a strict decrease
and unchanged full placement validation permit a move. Equal/worse costs retain
the incumbent. Full-source board/terminal/pad/net identities, source poses/variants,
outline, demand receipts, canonical proposal order and random concerns remain.
Each distinct actual proposal consumes the same60000 per-plan counter; a bound
stop retains the valid incumbent. Six outer candidates and each strategy's route
caps remain. Scoring a complete plan costs roughly sum(p_net^2)+C*sum(p_net) plus
connector scans/sorting per proposal; the existing128-part bound, five target
origins and host/work/time limits remain. Native and eventual browser timings
must qualify that additional work; no general performance claim is made.

The paired14-root corpus must reproduce all42 saved15 non-time fields. All22
boards must admit physically and preserve source identities; their candidate2
whole-plan total may not increase and the otherfive score records must stay exact.
Area, protected void, copper and vias may not increase, and some area/void must
improve. Independent literal global-cost fixtures, actual linked/unlinked moves,
identity/eligibility/access-envelope/tie/budget/raw-parity checks, strict synthetic
readers and actual4/7 PowerShell transport precede fresh native runs.

Twelve accepted source-owner hashes and the1475-input seal are audited. The
read-only source audit identified the scorer adapter seam without reading trial
outputs. Bootstrap adapts only the proven21 corpus/host scaffold for22, fourth
unit and1476-copy restoration; accepted1467 starts without rejected16–21 policies.
Native/GWT/player/production promotion remain NOT RUN at this preparation stage.
