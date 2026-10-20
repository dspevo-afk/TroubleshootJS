# Existing owners and bounded rejection classification

Read-only API audits identify PcbPlacementPlanner.validate as the placement
owner. Its public-to-package Rejected reason field distinguishes four physical
reasons from budget and malformed-construction reasons. PcbAccessPlanner adds
DISCONNECTED_ESCAPE_CHANNEL and ACCESS_GRID_BUDGET; only the former is a
physical census outcome. Root checked their actual selected source bytes.

PcbBoardLayout.validateGeometry performs routing geometry, silkscreen and route
quality in that order. Routing geometry validates package correspondence,
courtyards, fixed envelopes, conditional domain/two-layer rules, physical net
connectivity and copper clearance. Therefore GEOMETRY can legitimately report
a nested two-layer rejection. The later explicit PcbTwoLayerRules.validate call
is a separately reported confirmation. Unknown failures always abort.

RouteQualityRejectedException has typed BENDS/DETOUR kinds; arbitrary
IllegalStateException cannot be treated as an admission rejection. The protocol
lists exact known physical message prefixes from seven hash-pinned owners.
It deliberately is not an all-future-error catalog. No catch may surround
candidate construction or independent proofs. Immutable trace/hole identity
and known original source must be checked before the mixed via/domain message
can be interpreted as a physical barrier rejection.

PcbConductorGraph.pristine caches the connected-component snapshot. Individual
pad connectivity lookups are constant-time after that snapshot. Capture source
once and candidate once per admitted row; do not skip full validator calls or
independent9045-pair oracle. At most31 candidates means up to280395 candidate
pair comparisons, not a measured timing guarantee. Source oracle is separately
checked once. Stage elapsed and terminal cleanup limits stay honest.

The successful-route callback occurs in considerMediumLayout before winner
selection and border trim. This census measures alternatives at that phase;
it cannot establish returned-board area/void reduction. A later matched
selected-layout compaction probe would need the selected plan handed off from
finishMediumSuccess/mediumPlacements and translatedPlanFootprints, then
compactRouted control/alternative, silkscreen restoration and complete physical,
via, copper, area and protected-empty-rectangle gates. That probe remains
unstarted until a physically feasible slide is actually demonstrated.

No production geometry owner, policy, graph, main/default version or solver
behavior changes. All reports are private native-only diagnostic artifacts.

Catalog review resolution: board constraints are fixed, but PcbTwoLayerRules
reconstructs domain bounds from candidate placements, then calculates corridor
midpoints. Therefore fixed holes do not imply fixed drill/domain permission.
The DRILL_LAND_CLEARANCE hole-pair check alone is invariant and excluded. Known
plated-hole net prechecks are required before classifying VIA_DOMAIN_OR_NET.
Outer-call stage semantics preserve whether validateGeometry actually completed;
classifying its nested error as a later explicit TWO_LAYER call would misstate
that completion. Corrected optional catalog audit reports no remaining blocker.
