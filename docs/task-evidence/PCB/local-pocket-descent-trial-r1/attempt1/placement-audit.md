# Local pocket descent source scope

Accepted source at91ffe9a3 remains the same1475-input production boundary as the
last renderer acceptance. Rejected16–20 sources are excluded from this COPY.

`PcbPlacementPlanner.planMedium` greedily places anchors and then each remaining
footprint. Once chosen, an earlier footprint is not revisited. Candidate2 uses
the existing compact demand sizing and algorithm0, with group scoring, but is
ungrouped and not the tight candidate path. There is no current post-plan local
relaxation pass. The existing full `validate` just before constructing `Plan`
is the new pass's entry prerequisite.

The existing owners provide sufficient bounded primitives: `PcbFootprint.translated`
moves a whole physical package and its pads/probes/escapes; `vacancyDomainBox`
clips advisory search against established domain separation; `PcbVacancyTargets`
returns at most5 center/corner origins for the largest coarse40-unit-grid pocket,
with its existing20000-cell ceiling. Exact `fits` and full `validate`, including
`PcbAccessPlanner`, remain required. A free coarse rectangle alone is not proof
that a package or its future copper can fit.

The trial visits only the four existing passive kinds in canonical component-ID
order, with current pose/variant and a fixed original outline. A single pass
keeps the current position on equal cost. Existing linked-pad/corridor/content/
semantic-group terms retain their weights; macro hints are omitted for this
local pass. The local score is a heuristic and can disagree with the whole-board
MST/corridor/connector placement ranking or actual routing. The full fixed corpus
therefore retains its four independent strict metrics and admission checks.

Every attempted advisory origin uses the same plan evaluation counter; reaching
60000 ends the optional pass with its last legal incumbent. No separate budget,
additional placement candidate or router request is introduced. Source demand,
initial sizing, seed/random concerns and other candidate algorithms stay unchanged.
Representative other-candidate raw-plan comparisons are a unit prerequisite.

The current routing owner offers whole-board routing plus bounded internal
rip-up recovery. It has no public partial-reroute transaction for moved placement.
This experiment therefore stays before the existing routing request, under the
six-plan/three-route policy and its current expansion/order/time limits.

A separate read-only audit confirmed selected-layout rebasing and re-finalization
could support a future winner-only compactor without persistent duplicate state.
The split-guard20 trial produced exact selected geometry in all14 roots, however;
repeating that same rule after selection has no measured benefit here. This
local placement experiment addresses a different owner and makes no success claim.

The leaf source audit completed at91ffe9a3 without edits/runtime or inspecting
trial outputs. Root reviewed the relevant owner seams. `source-audit.json` binds
the consumed accepted files; the full accepted1475 seal is checked by preparation
and every frozen-input audit. Native/GWT/player proof is pending.
