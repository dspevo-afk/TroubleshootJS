# Minimum-area floor: source boundary

Base: `748e9c083f2bd4a4ace6289529fba94dc1aa6060`. This is an offline
measurement of retained accepted15 candidate demands, with no altered planner.
The exact owner hashes and baseline references are in `protocol.json`.

The read-only ownership audit found `MEDIUM_MIN_AREA=5000000` in
`PcbPlacementPlanner.planMedium`. Density14 candidates0..3 use the compact
formula; fallback candidates4/5 use the floored roomy formula. Candidate4
overrides the spacing factor to2.0. Candidate5 uses3.0 up to40 parts and adds
`min(6.0,(parts-40)*.10)` above40. The allowance is500000. Large inventories
then cap the target at `MAX_AREA-8000000`; every target is capped again at
`MAX_AREA-1000000`, with `MAX_AREA=64000000`.

The floor is a target-sizing heuristic, rather than a separate minimum-area
admission rule. It is coupled to the small-inventory outline ceiling through
`min(MAX_AREA,targetArea+2500000)`. Any future changed-floor experiment must
declare whether that ceiling is intentionally changed. This census changes
neither value and chooses no replacement floor.

Placement still owns insets, actual access envelopes, overlap and barrier
corridors. Access planning owns connected terminal escape channels. Board-layout
and two-layer validation own containment, pad and probe bounds, copper
clearance, connectivity and isolation. Routing and placement work caps have
separate owners. These checks remain unchanged. The separate single-face
`SupportedEnvelope.MIN_EDGE` is not a normal-medium minimum-area rule.

The read-only provenance audit confirms that the planner stores its pre-floor
`demandArea` on `Plan`, compaction preserves it, `PlacementScore` reads it, and
the generator records each successful attempt under its candidate ID. The owners
are `PcbPlacementPlanner` (demand accumulation and final plan),
`PcbPlacementCompactor` (plan reconstruction), `MediumBoardPhysicalPolicy`
(`PlacementScore` and score creation), and `SeededPcbLayoutGenerator`
(candidate collection). Their hashes are pinned in the protocol.

Root also verified the baseline's declared original native log digest and the
exact equality of all14 version15 `PCB_EDGE_TRIM_CASE` JSON rows with the retained
baseline. The reader repeats that check; this reuses existing native observations
only after auditing their consumed inputs. It does not turn the offline census
into fresh native proof.

Rejected attempts have no score. Their demand cannot be inferred from another
candidate; a missing or rejected candidate makes this fixed census fail closed.
It classifies only strict floor increases versus equality/no-op on28 successful
fallback candidates from the informed14-root corpus. It does not establish a
different outline, reranking, route, selected layout, player result or all-seed
behavior under a lowered floor. Candidates0..3 can still be affected indirectly
by changed fallback ranking in a future planner interpretation.

No source change, JVM, GWT build, browser, preview, persistent process or source
copy is needed for this measurement. Python arithmetic is checked against an
independent rational ceiling; disagreement is a failed interpretation.
