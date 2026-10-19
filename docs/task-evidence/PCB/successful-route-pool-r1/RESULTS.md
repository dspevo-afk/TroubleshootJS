# Successful routed pool: measured alternatives retain the copper tradeoff

The observation passes; private22 remains rejected. Seed5 has no measured trial
alternative that preserves the old fallback's copper length. Both new successful
routes use more copper, and the existing selector already chooses the smaller,
lower-copper one. Changing the winner among this pool cannot remove that measured
regression. No new policy or acceptance exception is introduced.

The following values all describe **FINALIZED_ROUTE_CANDIDATE**: after route-local
compaction/content crop/tray/envelope checks and existing score computation,
before candidate selection and selected-only edge trim. Geometry uses stored
units; quality is the existing geometric score multiplied by1000 and rounded.

| Seed | Version/attempt | Candidate area | Summed copper | Vias | Quality×1000 |
| --- | --- | ---: | ---: | ---: | ---: |
| 5 | 15/5, old winner | 9445744 | 38610 | 48 | 356092136 |
| 5 | 22/2 | 8091624 | 49140 | 48 | 336104680 |
| 5 | 22/3, new winner | 6107220 | 43060 | 48 | 286395681 |
| 2 | 15/2, old winner | 9313352 | 51860 | 48 | 368394917 |
| 2 | 22/2, new winner | 9126701 | 48720 | 48 | 361643817 |

All five are actual P07_FULLER_TWO_LAYER successes already produced under the
unchanged three-route-per-strategy cap; no P05 success appears. Seed5's trial2
uses10530 more copper than old5; trial3 uses4450 more. Trial3 uses6080 less copper
than trial2, has a smaller candidate area and lower existing quality score. The
source comparator prioritizes area for these compact interpretations; all four
selected cases have a unique minimum candidate area in their successful strategy.
These facts describe this informed pool, not unseen layouts or move-level cause.

[Same-phase analysis](pool-analysis.json) binds the actual terminal log and reader
assessment. Seed2's preselection candidate area falls186651 and copper3140.
Its final post-trim board area instead falls317871, because border removal also
changes:135260 old versus266480 trial. Seed5 old5 removes273000 after selection;
new3 removes0. Candidate and final-board areas/quality therefore are not silently
interchanged. Copper counts every Manhattan trace segment, overlaps included;
via counts include only VIA holes. Unselected protected-empty area was not measured,
so this is not a new four-metric fitness evaluation.

Every pool entry matches the next SUCCESS in its bound selected routeOutcomes,
with canonical seed/version/strategy/attempt/order, placement score, unchanged
source-graph digest and immutable geometry/component/trace snapshots. The winning
entry's copper/vias match the final selected receipt. All four selected15/22 rows
reproduce all42 non-time fields from the published22 packet, and their whitespace
rows remain exact. Callback metrics cannot influence route selection. The
[reader assessment](assessment.json) explicitly reports no policy/fitness change.

Fresh final-source gates:

- **PASS** native unit1,48.2908905s/exit0:15 assertions,4 named plumbing fixtures
  for null no-op, callback arguments, unexpected-error propagation and nested
  observer restoration. This fixture scope does not claim routed physics.
- **PASS** native routes2,77.6597873s/exit0: informed seed5 then2, explicit15 then22,
  four selected physical admissions and five real callback snapshots. No additional
  candidate, route, cap, random draw, score weight or changed source geometry rule.
- **PASS** fresh root55 synthetic parser cases equal the released receipt:
  four positive/51 rejected; parser payloads are not route measurements. Actual
  selected PowerShell1/2-manifest transport12 cases pass, including single-row
  nested-array preservation and10 negative boundaries.
- **PASS** root source/fixture/reader review and integrated post-runtime review:
  COPY1470/8overrides/3adds/11deltas/26gates; main1475 exact. Two marked observer
  blocks erase to exact22 generator bytes; six other22 overrides and the existing
  physical witness stay exact. Three registrations erase to the accepted verifier.
  Prior22 input pins remain exact. Root reviewed the integrated diff; no separate
  independent source review is claimed for this measurement.
- **PASS** four recorded driver births exited without termination; maintained
  native receipts separately own child cleanup. COPY1470→1467 restored exact,
  verified contained/non-reparse retirement removes private state. Original5
  unrelated entries/shared resources preserved; no active owned native/browser/
  preview/profile. Phase elapsed includes maintained cleanup, not separately
  measured; driver birth checks have distinct elapsed receipts.

All native phases passed on their first final frozen run. Before freeze, root
aligned its erasure checker with the worker's actual marker names after an initial
static mismatch, replaced a vacuous null-fixture check with no-inspection proof,
and caught/fixed equal-but-foreign graph and aggregate-census gaps in the reader.
Fresh negative/positive canaries cover those changes. Reader receipt shape was
integrated before freeze. No post-run source/oracle repair or reused native PASS.
Initial source-audit trim confusion was corrected against actual archived22:
both density14 and15 retain selected-only edge trim. Raw-source and sanitized-log
hashes are preserved; known-prefix privacy replacements change no result/timing.

The first publication staged-whitespace check **FAIL** flagged a frozen reader
note's extra blank EOF and a unified-diff blank-context line's required space
prefix. All frozen source/gate bytes are preserved. Packet attributes narrowly
exclude only reader-notes.md's blank-at-eof and source-review.diff's blank-at-eol
rules, with other whitespace rules active. This changes publication formatting
checks, not native acceptance or source; unchanged runtime is not rerun for it.

The observer's sourceGraphBefore snapshot is at the first successful callback,
sourceGraphAfter after the helper returns. Both match the final receipt's source
graph. The unchanged physical helper separately owns whole-run graph fidelity.
Snapshots hold scalar/string/bounds values, not deferred mutable layouts. The
owner restores the previous observer in finally and unexpected errors propagate.
Only serial-JVM plumbing is qualified; concurrency/cancellation responsiveness
was not measured. Rounded quality does not resolve sub-milli ties; no such tie
comparison is claimed. The native runs, not the synthetic cases, prove actual
callback delivery at the selected generator implementation.

**NOT RUN:** GWT production build, visible player input,22 owner save/load, solver/
repair, full matrix, independent electrical oracles, new/unseen roots, all-seed,
manufacturing or general/modest-host performance qualification. Production source,
default15/general16 and architecture/ownership stay unchanged.

Reproduction uses the saved preparation/bootstrap/overlay pins and a new owned
copy. Review source and fresh in-memory `check_assessment.run_canaries()`, write
root acknowledgement, then `inputs.py freeze`. `run_native.ps1 -Phase unit`,
driver cleanup, `sanitize.py unit <JDK8>` and `assess.py unit` precede analogous
routes commands. `inputs.py audit`, `analyze_pool.py`, `retire_copy.py` and
`review_packet.py` close the evidence. Existing runtime and assessment output
paths refuse overwrite; do not rerun the retired lifecycle in place.

Next inspect the geometry owner for bounded local compaction after a route has
already succeeded, with incumbent retention and no added route requests. A
read-only leaf audit is active; feasibility, identity/trace boundaries and work
limits must be established before any implementation. No next policy is chosen.
