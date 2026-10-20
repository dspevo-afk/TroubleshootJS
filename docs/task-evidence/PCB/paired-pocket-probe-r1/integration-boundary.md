# Source-only production integration boundary

Reviewed at c29b81bbe87aa192761b35a6ee3c9ebdb037ba43. This private small-board
experiment does not qualify a production integration. The source review made
no edits and ran no tests or native/browser work.

`PcbPlacementPlanner.planMedium` calls the ordinary compactor before incumbent
selection. A production paired pass must instead be a separate method called
from `SeededPcbLayoutGenerator.beginPackingProbe`, after the existing compact
call and before same-placement/headroom checks. Medium reaches that hook after
`finishMediumSuccess`. Preserve its initial strip/single-part behavior.

A production move-set change needs a distinct packing interpretation, such as
4, with normal declarations, accepted-version checks and dependency identity
updated together. Current production remains layout19/packing3. Keep the same
headroom, shared evaluation counter, one optional route, route policies and
strict four metrics. No compatibility commitment or promotion follows from this
private test. Relevant production contracts include connected-pocket packing,
normal packing identity, medium normal admission and packing metrics, followed
by the required final-source native corpus, actual JDK8/GWT build and player proof.

Consumed owners are pinned in baseline-inputs.json: planner373f6f4d, compactor
e8be50e4, generator6f3af541, constraints e1ef75f6, procedural layout da9a5512,
dependency context beed78fa, admission f1051e4a and metrics724e4d68. Full hashes
are in that baseline; this is a bounded read-only finding, not runtime evidence.
