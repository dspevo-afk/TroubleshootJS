# Compact private boards and full readable labels

The 40/44/60-part fixed boards use 14.58% / 16.51% / 11.59% less outline area,
retain every declared part/pad, and pass unchanged physical/copper rules. Actual
fit and wheel-zoom views retain full reference/title/terminal text and have zero
measured fully visible secondary-silk collisions in the checked views. Existing
private saves reconstruct their exact legacy geometry. This is finite private
qualification; public HARD/RB56 release and human findings remain held.

Candidate over `7d733a10bb203a1cda7b8f55409cb9b1b6e11cd2` on `codex/q60-faulted-profile-counts`,
GWT build `82ED33BBD8DE32C145D1C3B4A3CEB5F3`. [Sealed source, receipts, metrics, images and failures](evidence-r1.json).

## Cause and change

Legacy placement used an inventory-growing spare-area factor and macroregion
hints, while penalizing the entire rectangle between connected pads. The final
crop already followed routed content and therefore could not remove irregular
internal gaps. Route selection favored detour score and used outline area only
for ties. Static label bounds estimated widths in board units, while rendered
fonts had a pixel minimum; separate terminal legends were not collision obstacles.

Fresh private RB56 requests select immutable placement interpretation 2. Four
compact candidates start from protected package demand and score the less
obstructed Manhattan connection path plus occupied-area growth. Two existing
slots run established placement algorithms 0/1; the routing prefix reserves its
best-ranked fallback within the same three-attempt budget. P07 then uses the
existing monotone strip compactor with protected via lands and preserved via
identity/net/plating/dimensions. Every changed axis and final layout retain their
physical, connectivity, copper and route-quality checks. V2 selects the smallest
accepted outline, then uses route quality and existing deterministic tie-breaks.
It can select a worse detour score that still satisfies the existing quality bound.

Rendering measures actual Canvas ink and places complete reference/net/title/
provider text around the exact currently drawn body/lead geometry and pad bounds.
Fixed owner-local searches and gutter slots use leaders when needed. Font size
keeps its readable minimum. An impossible slot retains full text with a plain
zoom hint; offscreen owners keep natural clipping. The bounded cached annotation
layout changes no physical fingerprint, electrical graph or hit target. Title
priority and affine leader coordinates have focused contracts.

## Same fixed inventories, actual routed dimensions

All dimensions/gaps are drawing units. Courtyard occupancy is package courtyard
area divided by the final outline, not usable routing/access area or a manufacturing
rating. Part/pad censuses are independent literal expectations.

| Parts / pads | Before | After | Area reduction | Courtyard occupancy |
| --- | --- | --- | --- | --- |
| 40 / 96 | 4322 × 1475 | 3692 × 1475 | 14.58% | 11.76% → 13.77% |
| 44 / 104 | 4251 × 1904 | 4061 × 1664 | 16.51% | 9.83% → 11.81% |
| 60 / 143 | 6041 × 1869 | 5341 × 1869 | 11.59% | 9.40% → 10.63% |

Seeds are -1017 / -1004 / -1022. Median nearest protected-envelope gap remains
10 units in all three. P90/max changes are 30/140→30/60, 30/37→34/70, and
20/66→20/66 respectively. Some gaps increase; this is not uniform spacing or an
optimal-packing claim. Protected probe/service/escape and domain/copper space
remains. Minimum independently measured different-net same-layer trace centerline
distance is 20 units against the unchanged 15-unit rule in each final board.

Native184 checks all three legacy geometry SHA-256 values exactly, all three
new routed censuses, and one matched actual golden60 via compaction. The latter
requires an actual area contraction, unchanged input, every original hole's
identity/properties, and full routed/two-layer validation without another search.

## Final-source checks and real user flows

| Check | Result / scope |
| --- | --- |
| Native184 | PASS79.726s,11 focused suites: geometry585/labels134/U04 4010/U06 34942/private identity42/fingerprint119/backing204/dependencies1166/P07 11937/resumption2203/replay148. Full matrix NOT RUN. |
| Actual JDK8/GWT46 | PASS102.564s, all five permutations through `scripts/build.ps1`. |
| Headed195 | PASS150.110s host; actual three private cold proofs, fit views and real mousewheel zoom. Exact full reference census at every capture; actual before secondary text multiset74/100 retained for44/60, zero measured visible secondary-label collisions. The40 before multiset is unavailable; current references/ink and image are checked. |
| Headed198 | PASS149.606s host /146.735s flow. Actual model-1 Blob downloads and native FileReader restores switch private2→private1→private2. Saved source connections/current limits and exact semantic history survive; import grants no completion. Old accepted repaired file/history23 independently retests, resaves private1 with identical realization; fresh private2 resave retains its own realization/source/history. Actual public Shop/remove/install repair on compact2 independently passes retest and closes with complete owner/session/bridge/meter/coordinator cleanup. |

Private cold product limits remain90s/640 work/5s per unit and the ticket hold
remains360000ms. The file collector's predeclared420s host limit encloses one cold
launch, two bounded file reconstructions and guarded repair; it relaxes no product
deadline. The three observation-only views deliberately close with the exact
`FAIL:rb56:Ordinary customer retest did not pass before view cleanup` status and
complete cleanup. Their case PASS means observed geometry/input/retirement,
not a customer or human PASS. Source/served digests and owned browser/localhost/
native handles/job/drainers audits pass. No runtime/preview is left active.

Normal alpha5 replay, session1/model1 framing and public admission flags remain.
Private `/1/<epoch>/RB56_CONTROL/<seed>` saves select legacy placement1;
fresh `/2/<epoch>/rb56-placement-2/RB56_CONTROL/<seed>` selects placement2.
Version is bound in immutable request/cache/dependency, capture owner and restore
before generation. Successful restore commits the authoritative request version
only after publication/retirement; failed restore leaves the previous request.

## Actual inspected views

Historical before images were captured on published build E6CA9A4CB99EE6E4301B9F1C980D1830.
Their adapters later failed and are not whole browser PASS evidence. Native177
baseline geometry passed. Final images use build 82ED33BBD8DE32C145D1C3B4A3CEB5F3; no photo editing.

| Board | Before fit | After fit | Actual wheel zoom |
| --- | --- | --- | --- |
| 44 | [Before](before44-fit.png) | [After](after44-fit.png) | [Zoom](after44-wheel_zoom.png) |
| 60 | [Before](before60-fit.png) | [After](after60-fit.png) | [Zoom](after60-wheel_zoom.png) |

Root also inspected40's current raw fit capture and the file-flow
[compact source setting](fresh-compact2-source-state.png) and
[legacy repaired restoration](accepted-legacy1-repaired-restore.png).
The Canvas observer's general all-text rectangles can include body ink removed by
clipping; those broad pairs require image inspection and are not all declared
collisions. Required secondary text uses actual measured full ink at the observed
identity Canvas transform. Natural offscreen clipping at zoom is not label removal.

## Retained failures and next action

The packet retains all failed native176/178–183 and before-view187–194 receipts.
It records the compiler/route-limit/receipt-marker/fixture causes and the actual
before observations without relabeling a failure. Native182's partial geometry
result was audited once, then native184 freshly reran the final integrated source.
File196 failed a collector power-off assumption after successful legacy restore;
197 passed both restores/downloads/retest and then failed an unsupported tray Close
selector.198 uses the actual public popover toggle; production source was unchanged.

Next is [one qualitative human attempt](../../U05/q60-human-play-r1/README.md),
with prior guided same-electrical-candidate exposure disclosed. Human strategies/
outcomes and U05 falsifier/calibration proof are still absent. Public HARD, normal
RB56, population/diversity/retry/release remain held. Maximum endurance and weaker
hardware are not certified; the latter alone is owner-deferred. Q100 is outside
this slice. Original8 files, unknown Q30 91.616s failure, six missing inventories,
lateBLOCKED audit and C4130 hold are preserved. No unchanged Q30 corpus rerun,
email, Core/Android work, unrelated process action or protected-main merge.
