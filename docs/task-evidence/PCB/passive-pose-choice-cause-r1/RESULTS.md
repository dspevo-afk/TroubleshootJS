# Passive choice diagnosis result

**Measurement fidelity PASS; local pose attribution INCONCLUSIVE.** No placement
policy or production/default behavior changed. This measures the rejected policy,
whose strict fitness remains FAIL.

All four routed rows, roots42/3 at interpretations15/16, reproduce every42
non-timing field from the published policy result. The reader validates complete
candidate1 captures, source inventories, order, prefixes, bounds and summaries.
Independent event replay covers only the first selected-footprint difference's
complete126-event arrays per version. Other summaries are checked for consistency.

| Observation | Root42 | Root3 |
| --- | --- | --- |
| First recorded difference | sizing | sizing |
| Candidate demand | 3445134 → 3448134 | 3206748 → 3209748 |
| Raw outline | 3330×1760 → 3330×1770 | 3240×1700 unchanged |
| Domain boundary X | 850 → 860 | 900 unchanged; operands differ |
| First changed selected item | UAC, ordinal4 | RENAC, ordinal6 |
| Selected pose/origin | TOP0 (640,900) → TOP0 (650,900) | TOP0 (360,620) → TOP90 (560,540) |
| Whole candidate actual visits | 6800 each | 6170 each |
| Placement order | equal | equal |

Root42 changes a non-passive placement before any changed passive selection. Its
outline, boundary and guidance already differ at UAC. Root3 retains the same raw
outline, aligned boundary X, prefix and guidance at RENAC, but the boundary demand
operands differ. The predeclared comparison requires equal boundary inputs as
well as equal outline/prefix/guidance, so both local comparisons remain
INCONCLUSIVE. The trace cannot apportion the final routed regressions among
sizing, orientation, downstream placement, compaction and routing.

For RENAC, the old selected TOP0 geometry/origin is absent from the trial's actual
visits; that origin occurs once with TOP90. Its old selected visit is ring7/slot1.
The trial reports63 TOP0 and63 TOP90 visits, compared with126 TOP0 visits before.
These are observed schedule differences, not an evaluation of an unvisited pose
or proof that restoring that opportunity improves a routed PCB.

## Execution and failure preservation

- Actual JDK8 unit3: **PASS**,44.3133237s/exit0. Observer23, old-source114 and
  trial-source128 assertions; actual synthetic serializer sample and14+14 source
  bindings verified. The sample is explicitly synthetic, not board evidence.
- Actual JDK8 routes2: **PASS**,73.2877039s/exit0, four boards. Both phase times
  include compilation/JVM work and maintained cleanup; cleanup is not timed
  separately. Four recorded driver births verified exited; none terminated.
- Initial reader22 synthetic cases/30 assertions passed, but the actual capture
  reader **FAILed**: it applied five anchor slots to every item and rejected a
  valid remaining-item index5. Full source/gates/outputs remain byte-exact in
  [attempt1](attempt1/archive-sha256.json). Its result remains FAIL.
- Source-derived correction preserves five anchor slots and uses six remaining
  slots, regardless of passive eligibility. Corrected canaries **PASS**25 cases/
  33 assertions, including ineligible TOP180 provider positives at the last
  valid index of ring1 and invalid anchor-index/remaining-stride negatives.
- Successor seal: exact1508 native inputs, six diagnostic source deltas and29
  gates. [Reuse audit](native-evidence-reuse.json) proves every native input and
  all12 phase artifact bytes unchanged from attempt1. No Java execution was
  repeated for this reader-only fix. Protocol/source checks were rerun through
  the corrected reader before the full [assessment](assessment.json).
- [Root packet review](root-review.json), frozen gate/source/peer-policy audits,
  accepted main1475 audit, policy1504→accepted1467 restoration and contained
  non-reparse copy/private-state retirement: **PASS**. No task process remains.

Commands: `prepare.py`, `prepare_routes.py`, `prepare_phase_gates.py`, `register.py`,
root reviewed21 hook regions with `review_source.py`; `check_assessment.py`;
`inputs.py freeze/audit`; `run_native.ps1 -Phase unit/routes` using the private
Python/JDK8 runtime pointer; `check_process_cleanup.ps1`; `sanitize.py`;
`validate_unit.py`; failed `run_assessment.py`; `preserve_attempt.py attempt1`;
corrected canaries/freeze/audit; `reuse_phase_outputs.py`; fresh `validate_unit.py`
and `run_assessment.py`; `retire_copy.py`; `review_packet.py`.

## Next boundary

Separate conservative sizing from the alternating pose schedule with an explicit,
identified native control that retains TOP0 selection. Preserve source/graph/seed,
all search/routing/physical/fitness limits and the rejected policy evidence.
No next control is implemented in this packet. Owner/replay/repair, GWT/player,
full matrix, all-seed, manufacturing and packing-improvement proof are NOT RUN or
NOT APPLICABLE for this read-only native measurement.
