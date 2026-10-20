# Source-only investigation

The Luna/MAX explorer reviewed the published395fe5d8 source and accepted
selected geometry. It owned no files and ran no tests/build/browser/native
commands. It did not capture separate per-file hashes; baseline-inputs.json
binds the unchanged previously published1482-file inventory, and the experiment
manifest separately pins the actual planner bytes consumed by the root.

PcbPlacementPlanner uses the small planner below20 parts; this board has15.
MEDIUM is a display/profile label, not this algorithm selector. Anchors are
placed on the declared edge with outward escape checks (planner119,204).

compactConnectedVacancies406-485 visits constraints.getParts in declared order
once. It skips all anchors and isolation bodies. For each eligible part it
tries up to five origins from PcbVacancyTargets: center/corners of one largest
coarse40-unit free pocket, with deterministic ties and a20000-cell ceiling.
Origins snap in10-unit offsets relative to the current pose. fits and complete
validate remain authoritative. A move must not increase union-envelope area
or wire-demand proxy, and at least one must decrease. All trials share the
60000 evaluation ceiling with the incoming plan's work; no reset is allowed.

The actual call chain is PcbPlacementCompactor.compact12-19, generator
beginPackingProbe244-260, advancePackingProbe323-350 and successful
routePlan/finishRoutedPlan1060-1143 including compactRouted. Those owners already
run; no missing invocation explains the observed green patch. The recorded
original proposal's equal area and extra190 copper explain the KEPT decision,
but source alone does not identify an exact retained-width cause.

A second sweep is the smallest suggested experiment: a later accepted move can
change the pocket available to an earlier part. It preserves the existing move
type, order, validators, exclusions and counter ceiling. It may find nothing
or improve only the placement proxy, and needs actual final routed metrics.
Group relocation needs a new proposal/validation seam. Alternate rotations need
package/escape compatibility proof absent from this translation-only path.
The root scoped only the second-sweep experiment; no production change follows
from this source review.
