# Successful route pool: bounded observation scope

Published22 `27521289793b21ddfe636d0f6975e962797ebab9` is rejected. Its seed5
candidate2 rank remains6, yet candidate2's P07 route changes from a branch-limit
rejection to success. This changes the third route opportunity from5 to3 and the
winner from5 to3. The existing cap stays3 per strategy,6 total. Seed2 retains
winner2 with smaller area and less copper. Neither observation identifies an
individual passive move or an unobserved alternate route's geometry.

The source owner is SeededPcbLayoutGenerator.advanceMediumRouting, the grouped
route order4,2,5, and guarded/narrow/support candidate overrides. After each P07
completion, mediumTwoLayerSuccesses is refreshed from actual route outcomes.
When the third slot sees an earlier success and untried narrow3, it selects3;
with no success it retains fallback5. The narrowSucceeded flag refers to the
state before that slot, so the support override does not replace newly tried3.
considerMediumLayout and mediumRouteCandidateBetter separately select among
successful finalized layouts. This is an opportunity change, not rank movement.

AttemptObserver reports pre-attempt checkpoints, not completed routed geometry.
The result exposes only the winner and aggregate outcomes. This COPY-only
measurement therefore adds a null-default callback at considerMediumLayout after
route finalization, physical/envelope checks, and existing quality/area computation.
It reports finalized candidates of both strategies. It adds no routes, finalizers,
policy weights, limits or candidate-selection decisions. The existing22 generator
must reconstruct byte-exact when observer additions are erased.

Only FINALIZED_ROUTE_CANDIDATE is measured. No raw-router result is claimed.
finishRoutedPlan already compacts copper, crops/repositions and validates before
selection. The later trimSelectedMediumBorder still applies: accepted15 uses
density14 and the exact archived22 overlay admits density15 as well. The initial
leaf audit read acceptedmain's density14-only branch and incorrectly said22 skips
trim; root corrected that statement against the actual22 source before coding.
Selected-board receipts therefore remain a distinct post-trim observation.

The finite informed scope is seed5 then2, each explicit15 then22. The unchanged
physical witness must reproduce all42 non-time selected fields of the published22
receipt for each of these four cases. Elapsed time remains separately bounded.
Every callback must match, in order, a SUCCESS in that case's routeOutcomes; no
missing, duplicate, failed, unknown or extra attempt can enter the pool. The
callback restores any previous observer in finally. Unexpected errors propagate.
Lifecycle and callback checks precede fresh paired native execution.

The metric report will name geometry units and standard trace length definitions.
Finalized-candidate area precedes the selected-only border trim and is not final
board area. Copper/vias and immutable fingerprints must be captured when called;
later selection cannot relabel a mutable reference as a prior snapshot. This is
diagnostic measurement, not acceptance of22 or a new policy/threshold exception.

Native/GWT/player and all new metric results are NOT RUN during preparation.
Main1475/default15/general16 remain exact. Root owns the accepted1467 COPY,
corpus/harness/readers/freeze/cleanup; one leaf owns only the generator observer,
its test/recorder and worker note. No main/email; original5 entries remain.
