# P07 - Required two-layer comparison and working bench

Base: `d0829afcac984c8d9186cd55a1118eec0ff7b319` (P06).
This is a developer-only investigation/prototype, not normal Quick Play admission.
Qualification receipts are in `acceptance.json`; commands are in `REPRODUCE.md`.

## What is implemented

Two-face routing reuses the current router's physical predicates, with explicit
plated vias, independent per-layer/domain validation and bounded search. The
production workbench renders the current face, flips explicitly and resolves
exposed trace/via probes to actual CircuitJS endpoints on the current physical
island. Top and bottom crossings remain isolated. No invisible layer or logical
net label is allowed to supply connectivity or a reading.

The electrical bench has independent 5 V / 100-ohm and 7 V / 1,000-ohm loops.
Actual solved currents are 0.05 A and 0.007 A. Two vias carry the underside route.
Real mouse clicks exercise via lands on both faces and the underside trace; the
same projected crossing measures 5 V on top and 7 V underneath. Flipping away
clears an inaccessible captured contact. All five plated terminal identities
survive both views. Test-point artwork is deliberately simple, not a retail PCB.

The search qualifies plated-through-hole packages only. A surface-mount package
is rejected explicitly, not granted an imaginary second land. Existing P01/P02
surface-pad geometry remains available; expanding this router's envelope needs
its own qualification. Mixed-layer/via layouts remain blocked from ordinary
`GeneratedBoardInstance` construction until P09.

## Matched policies and bounded cost

| Policy | Transitions per branch | Board via cap | Via penalty | Secondary step surcharge | Raised underpass |
| --- | ---: | ---: | ---: | ---: | --- |
| One layer | 0 | 0 | 0 | 0 | Disabled control |
| Sparse link | 0 | 0 | 0 | 0 | Declared P06 geometry only |
| Restricted two layer | 2 | 8 | 160 | 8 | Declared P06 geometry only |
| Fuller two layer | 4 | 48 | 60 | 2 | Declared P06 geometry only |

All share grid step 10, three deterministic ordering attempts, at most 250,000
cells, 100,000 expansions per branch and one million total. Existing length/bend
and clearance validation still apply. Failure/cancellation publishes no partial
copper or holes and leaves the input unchanged. No plane pours or extra layers.

The linked inventory obeys P06's sparse count/density caps. Its proposed
400-unit insertion penalty is not an automatic insertion algorithm: the link is
fixed before all four runs, so it is a constant within that matched quartet.
Sparse-link versus one-layer on the unlinked inventory is deliberately identical.

`comparison.json` contains 54 raw structural rows: RB30/RB56/RB100, seed 3,
primary top/bottom, original/fixed-link inventories, four policies, plus six
P05 reference runs. Every quartet verifies identical netlists, package sets,
positions and outlines before and after routing. The fixed-link variant replaces
one two-terminal package before placement; it is a separate structural inventory,
NOT proof that replacing an arbitrary real component with a link is electrically
safe. It is not pooled with the original inventory to claim an area saving.

## Results and adoption decision

Six of the 48 prototype requests route successfully; all six are RB30. The
separate P05 reference contributes one further success. None of the frozen RB56
or RB100 requests qualifies. These are bounded rejections, not hangs or silently
accepted partial boards. The raw outcome, runtime, expansions, via/link count,
length, bends and layer-route counts are retained for every row, including failures.

For the original RB30 inventory: top-primary fuller routing succeeds with 11
vias; bottom-primary restricted succeeds with 8; bottom-primary fuller with 10.
The P05 bottom-primary reference succeeds without vias. The top-primary restricted
request rejects. Thus neither a blanket second-layer recommendation nor a claim
that the via-heavy alternative is always better is supported by this corpus.
P05 is a separate reference algorithm, not an equal-cost A* ablation.

The small crossing routes with 1,040 units of copper/8,315 expansions on one layer,
versus 700 units/3,405 expansions/two vias restricted and 700/670/two vias fuller.
The fixed-link crossing needs 540 copper units/57 expansions/no vias when its
actual declared underpass is enabled. These illustrate tradeoffs, not universal
speedups. Same-inventory board area is identical across routing policies. No
packing or area reduction is demonstrated by adding a layer.

Keep this prototype available for P09's policy decision, but do not admit it to
normal generation yet. Restricted transitions have lower interaction cost but
reject a matched RB30 request that the fuller policy solves. More permissive vias
do not solve the tested 56/100 fixtures. P08 validation scalability is next;
placement, route quality, interaction cost and diagnostic proof remain relevant.

## Readability and probe/cut implications

For the original frozen outlines fitted ideally into a 1,000 x 700 board-only
rectangle (not the complete workbench), the smallest pad/via target estimates are:

| Fixture | Pad pixels | Via pixels | Zoom over fit for a 12-pixel via |
| --- | ---: | ---: | ---: |
| RB30 | 7.47 | 3.73 | 3.21x |
| RB56 | 4.63 | 2.31 | 5.19x |
| RB100 | 3.88 | 1.94 | 6.19x |

These are geometry estimates, not a human clarity study or acceptance of the
rejected large boards. The smaller electrical bench has actual mouse evidence.
Bottom-only paths require an explicit flip; top clicks cannot select that hidden
trace. Plated lands are accessible on either face, but the empty drill is not a
probe contact. More vias and face changes create genuine interaction cost.

A trace-cut snapshot can disconnect an island, and the real solver correspondence
audit must then reject an unchanged electrical model. This is the tested cut
implication, NOT a working player cut/repair operation. Live copper editing still
needs its own mutation, solver projection, instrument and diagnostic acceptance.

## Negative cases and recovery

Tests cover wrong-net vias, omitted plating/underside routes, forbidden-domain
copper on both faces, forbidden/overlapping pad drills, face confusion, floating
same-label islands, stale/cut targets, cancellation, exhausted budgets, unsupported
surface-only pads, ordinary-admission rejection and forced verifier cleanup.

The interrupted attempt's build-02 launcher failed and did not qualify a new
binary. Its old bench run timed out waiting for a cleared hidden-face reading.
Fresh build-03 qualified the existing projection-invalidation repair; the final
build also includes the surface-pad guard. The new unsupported-SMD regression was
observed failing before that guard and passing afterward. Historical failed runs
are retained and are not counted as final acceptance.

`native-results.json`, `browser-results.json`, `source-build-identity.json` and
`acceptance.json` distinguish the clean publishable source from the separately
preserved visual/FPS/packing/tray working changes. The 54-row matrix is part of the
maintained native runner. Only this new corpus suite receives a 600-second bound;
existing suites keep their original 60-second bounds. Compiled browser waits keep
the existing 600-second verifier ceiling. An actual GWT compile through
`scripts/build.ps1` and physical browser inputs are required; a static screenshot
or a successful launcher alone is not sufficient.

Exact owned browser processes/listeners are closed by the drivers. Unique raw
run directories retain logs, DOM, commands, source identities and failure evidence.
No blanket process killing, unrelated file cleanup or relaxed legacy CLI ownership
deadline is part of this work. P08, P09 and live copper repair remain unstarted.

Views: [top via](01-top-via.png), [underside trace](02-bottom-trace.png), [flip clears contact](03-flip-clears-contact.png), and [preserved normal player](04-normal-player-preserved.png). The first three use the developer bench; the last uses the separately preserved working UI.
