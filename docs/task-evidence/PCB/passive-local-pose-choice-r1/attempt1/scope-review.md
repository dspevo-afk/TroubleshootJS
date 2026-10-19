# Local pose-choice source scope

A read-only leaf checked archived18 planner and helper against their frozen
hashes. No native outputs, tests or builds were used. Planner SHA-256:
`aae7d7926e019c9eed385725ba8092aaf0f99700488f6e7122ae9ec7cb647fa5`;
helper `a5ab2f77e6929986d7a9310adb01913b4d1896ef8c7c050609df1f6ff5993926`.

One counter is reset per medium candidate and shared across anchors/nonanchors.
The ring loop increments then checks the cap; legacy fallback stops at the cap
and first legal escape. New policy4 must reserve each pose before fit, finish
both poses at the first legal fallback origin before stopping, and reject budget
exhaustion before an uncounted pose. Policies0..3 keep their existing behavior.
Advisory pockets replace existing ring-zero duplicate slots; two poses must not
consume or reorder those raw origin slots. Apply the existing actual-footprint
clamp separately to each pose. Strict score comparison keeps first encountered
ties, with TOP0 first at each origin.

Required actual-loop fixtures: advisory-only TOP90 fit, fallback-only TOP90 fit,
source win and score tie, and59999 budget boundary proving the TOP0 check uses
the last slot and TOP90 is rejected before fit/score. A narrow read-only test
probe is permitted if needed to observe the actual evaluator; no replacement
chooser or fake routing/physics. Numeric work/search/route limits and strict14
acceptance stay unchanged. These are code-scope conclusions, not runtime proof.

## Independent fallback fixture arithmetic

Root derived this before native execution. Use canonical R/SPAN_220 and sixteen
canonical electrolytic peers, all with the legal access margin16. Outline is
(0,0,220,6000); peer poses are (30,1800+200*i), TOP0, for i=0..15. There are no
topology links, hints, groups or barriers affecting this unit placement call.
This directly tests the selected mediumPlace implementation, not full generator
eligibility or electrical behavior.

The source resistor envelope is x[-46,266], y[-31,81]; its width312 cannot fit
the 200-unit interior. The TOP90 envelope is x[-11,81], y[-66,266]. Every legal
turned x origin30..120 overlaps peers spanning x19..161. Peer envelopes have
height184 and16-unit gaps, from y1746 through4930; all126 existing ring origins
remain blocked for both poses. Source width also prevents advisory targets.

Source-derived fallback bounds yield one column and147 rows (x60, y50..5900,
step40). Java string hash for R is82. With seed0/algorithmCandidate0, the existing
hash starts at row121. Turned checks at y4890,4930,4970 overlap the final peer;
y5010 is the first legal position. Expected selected TOP90 pose is (60,5010),
with252 ring checks plus8 fallback checks =260 evaluations. The implementation
worker must verify these assumptions against actual constructors before freeze;
fresh native assertions remain required. An earlier margin0 arithmetic sketch
was discarded before native use because Part requires margin>=16.

## Independent advisory fixture arithmetic

A read-only leaf derived this before native execution: target R/SPAN_220 TOP0
at (0,0), an already-placed R/SPAN_220 TOP90 at (355,500), both margin20, no
barriers, outline (20,20,500,780). Local target envelopes are TOP0
(-50,-35,320,120) and TOP90 (-15,-70,100,340). Blocker envelope
(340,430,100,340) remains inside the outline's ten-unit interior.

Actual vacancy domain (30,30,480,760) has the largest clear pocket
(30,30,480,400). Its bottom-right target, vacancies[4]/ring-zero slot5, is raw
(240,345). The existing ten-unit clamp makes both actual origins (240,350).
TOP0 envelope (190,315,320,120) overlaps the blocker by100x5, while TOP90 envelope
(225,280,100,340) clears it horizontally by15. This predicts a real advisory
evaluation with TOP0 rejected and TOP90 fitting; it does not predict the final
score winner. A narrow read-only observer may verify those actual-loop checks
without changing the chooser, and must be restored after each fixture. Fresh
native proof is still required; these are code/arithmetic expectations only.
