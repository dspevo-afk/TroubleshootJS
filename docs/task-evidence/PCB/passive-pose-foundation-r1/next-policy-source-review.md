# Next policy boundary (source review only)

Optional leaf source inspection, 2026-10-19, base9f29107. No runtime or edits by
that reviewer. This is planning evidence, not acceptance of enabled rotations.

RB56 placement15 maps to density14; the general layout engine is separately16.
A future RB56 interpretation16 cannot simply become density16: the constraints
allowlist currently stops14, selected edge trimming checks14, and canvas fitting
checks13/14. A separate pose-policy field carried by constraints would keep the
packing-density interpretation intact, but its canonical/cache owner and replay
version must bind that field. Reusing a package permission as an unversioned
placement switch would leave old saved interpretations exposed to new behavior.

`PcbPlacementPlanner.plan` creates provider footprints before deriving `Item`
envelopes, demand/courtyard areas and maximum widths. `mediumPlace` then enumerates
translations. Choosing a fixed, keyed pose before those envelopes exist is the
smallest no-extra-draw policy. It cannot claim to select an optimal orientation.
Searching rotations is feasible within the current60,000 evaluation ceiling only
if every pose-origin pair is charged; it must replace origin samples, not run
four copies of the current rings. Equal dimensions do not imply equal routing:
180-degree turns change terminal positions, escape directions and scores.

There remain six placement candidates and at most three placements retained for
the existing routing policies. No extra candidates/routes/evaluation cap are
justified by this inspection. A pose fixed from a separate keyed concern must
leave the provider's geometry-variant random stream unchanged. Candidate-specific
changes need raw bindings of all untouched candidates, not just final source IDs.

`Rb56Plan.canonical` and the RB56 request/replay namespace already carry placement
interpretation. Existing saved14/current15 fixtures must remain pinned while new
15/16 fixtures establish the new boundary. A03 bounded physical choices record
the separate general layout version and local geometry choices; they do not
store actual package pose. Changing that path would require its own explicit
layout/model/pose interpretation rather than conflating its version with RB56.

`SupportedEnvelope.current` separately requires TOP/DEG_0 and single-domain,
single-face routing. RB56's isolated private qualification path has other admission
owners; proving a passive factory/stock slot does not widen either generation
admission contract. Any enabled normal path needs explicit corresponding evidence.

A subsequent bounded admission inspection finds no additional passive TOP0 guard
in the RB56 P07 path beyond the current package capability. `PcbTwoLayerRules`
keeps exact UAC TOP0/UFB TOP180 requirements and the RB56 package allowlist. Thus
widening the global catalog alone would expose turned passives to the existing
RB56 admission interpretation. The future policy must explicitly keep legacy
pose-policy0 at TOP0 and admit the declared passive turns under its new epoch.
The separate RB56 admission contract currently owns `RB56_BOARD_NORMAL@1` and its
`rb56-physical-contract` canonical field. A new RB56-specific admission identity
with an explicit bounded pose policy is the proposed owner; generic
`MEDIUM_BOARD_NORMAL@1` and the ordinary single-face envelope remain separate.
This is a recommendation to implement and verify, not an accepted @2 artifact.

The actual owner gate also needs proof: route turned passives, issue admission,
construct and attach the real RB56 owner, then verify normal-owner/canonical
identity and replay behavior. Layout success alone cannot certify this boundary.
Keep the existing negative UFB pose fixture. The source review ran no tests.

Diode and electrolytic stripes follow transformed terminal points. Their initial
text offsets remain screen/board-axis based and require rendered verification.
Resistor bands choose horizontal/vertical axes from body bounds, but their color
order currently increases along that screen axis rather than following terminal
direction;180/270 need special review. Rendered fixtures must include all used
poses, actual diode/cap polarity and band direction, followed by real removal,
replacement, save/restore and customer retest. Native pad projection alone cannot
certify those flows.
