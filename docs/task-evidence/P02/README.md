# P02 layer-aware copper and conductive surfaces

Status: ACCEPTED after the owner supplied the fresh independent delta-review PASS.
F1 is resolved; no blocking findings remain. [Review summary](independent-review.json)
and [acceptance.json](acceptance.json) record the exact candidate and evidence.
This pre-publication packet does not itself claim a successful push or email.
Base: `79be5ac5687aa3ad8552dc063a0434838ff1bf1d` on
`codex/task43p-final-recovery`. That accepted P01 commit remains published.

## Production contract

`PcbConductorBuilder` replaces the old one-layer, trace-index disjoint-set
validator. It normalizes current Manhattan geometry into physical junctions and
edges before assigning realization-local IDs. Collinear subdivision, insertion
order and view changes do not rename copper. Shared physical trunks retain all
source-route provenance without becoming duplicate independently cuttable wires.
Logical net labels validate physical connections; they never create them.

Top and bottom copper are distinct. Same-layer finite-width contacts connect;
projected crossings on different layers do not. Plated pads have two lands and a
barrel. Explicit vias/plated holes require valid contacted lands on both layers.
Non-plated holes have no net, layer endpoints, or conductive edge. The current
bounded model rejects an NPTH intersecting copper rather than pretending to clip
Gerber geometry around it. A hole cannot inherit a neighboring pad's probe halo.

`PcbConductorGraph` freezes geometry, provenance, pad-face correspondence and
surface exposure. Surface-to-edge geometry and terminal identity are checked.
Published layouts are sealed; translated construction copies preserve layers,
exposure and holes. Renderer copper comes from the immutable current snapshot.

## Electrical ownership and limits

`PcbConductorState` owns current immutable connectivity. A change must use its
prepare/apply/verify/rollback projection contract before publication. Stale or
foreign snapshots are rejected; failed compensation quarantines the owner.
Cut/restore encoding includes the exact pristine realization and durable edge IDs.
A genuine reroute rejects prior artifacts; no historical migration is provided.

The existing game does NOT install a live copper-cut adapter. Its owner refuses
metadata-only live edits. Tests demonstrate physical edge splits/restoration and
transaction semantics with an explicitly labeled projection double. Actual
CircuitJS tests separately reject a cut snapshot against unchanged real solver
connectivity. This is a foundation for E08, not player trace-cutting gameplay.

`PcbConductorProjection` audits actual CircuitJS post/wire connectivity at settled
runtime boundaries. Components, their private auxiliaries, detachable leads and
external power infrastructure do not become permanent board copper. Pad mapping
must exactly preserve physical islands. Shared posts are permitted only when that
physical correspondence agrees; current backing ownership checks remain active.
Solver node numbers are temporary observations and never persisted identities.

`PcbCopperAccess` and the renderer share face/exposure policy. Covered copper may
conduct but is not probeable. A surface pad exists only on its mounting layer.
Actual 0805/SOT-23 provider canaries prove that the opposite viewing face cannot
render/select the mounted body, resolve its probe, or retain a usable endpoint.
Board-view reflection does not mirror the parts tray or rename a terminal.
No player-facing flip/loupe control, SMD catalog, or two-layer router is enabled.

## Final candidate checks

| Check | Result |
|---|---|
| Actual JDK 8 production/native compilation and maintained suites | PASS, 20 suites |
| P02 contracts | PASS, 3,209 assertions; 40 lattice cases; 48 F1 permutations; 16 SMD poses |
| P01 pure pose regression | PASS, 8,357 assertions and 43 declared poses |
| Real production GWT OBF build | PASS, all five permutations, exit 0 |
| Compiled P02 across seven current board families | PASS, real projection and SMD face-access checks |
| P02 forced-negative and debug-off routes | PASS, exact controlled failure / no success report |
| Compiled P01, A08 and Task49 | PASS, zero ordinary page errors |
| Maintained A08 and Task49 strict report readers | PASS, actual final reports |
| Maintained Python seed, diagnostic, value/role oracles | PASS, direct execution against final Java receipts |
| Actual report protocol | PASS, 466 assertions |
| Original independent full review | CHANGES REQUIRED, one F1 shared-copper blocker |
| F1 new maintained regression on original builder | EXPECTED FAIL; 19 other native suites PASS |
| Unchanged independent review probes on repaired builder | PASS, minimal reproduction and 500 off-grid fixtures / 198,987 assertions |
| Fresh independent repair-delta review | PASS, separate owner-supplied review; no blocking findings |
| Independent additional interval/provenance oracle | PASS, 96 fixtures, four short-rejection cases, 405,796 assertions |
| Closeout dependency audit | PASS, 46 candidate files, 642 source/test inputs, eight readers/runners and five permutations unchanged |

[Native results](native-results.json), [P02 marker](p02-native.log),
[build log](production-build.log), [compiled receipts](browser-results.json),
[oracle/reader results](oracle-results.json), and [provenance](candidate-provenance.json)
identify the final source candidate. Curated text logs redact local paths and
normalize line endings/trailing whitespace; raw receipts remain in the external handoff.
The complete raw browser reports and runner
sources remain in the external task-owned review handoff directory. Their hashes
are retained here instead of duplicating another large Task49 report in the repo.

[Normal-player screenshot](normal-player.png) is an inspected, nonblank final-build
LED board with P02 flags ignored without debug. It proves rendering and privacy
smoke only, not mouse/keyboard diagnosis, component repair, or customer retest.

## F1 shared-copper repair

The independent full-candidate review found that a late finite-width contact could
split one partially overlapping source route without splitting the other. Cutting
one representation left a duplicate TRACE edge conducting through the same metal.

The builder now collects all breakpoints by physical layer and supporting line
before distributing them inside every participating segment. Edge emission then
merges identical physical intervals and their source provenance. This is a
bounded two-pass normalization, not repeated pairwise propagation or net-wide cutting.

The maintained regression fails on the old builder and passes on the repair.
It covers all 48 combinations of both layers, both orientations, all six source
orders, and original/reversed-subdivided paths. The exact shared interval has both
provenances; one cut disconnects it; immutable pristine state, save and restoration
remain intact. Fixture checks reject positive-length duplicate TRACE intervals.
The original reviewer-authored minimal and 500-case off-grid probes were rerun
unchanged and pass. These are independent test oracles, not a fresh human/agent review.
See [repair results](repair-results.json), [baseline rejection](repair-baseline.log),
and [independent probe receipts](repair-independent-probes.log).

## Corrected failures and remaining limits

The first compiled matrix passed nine routes but missed P02 dispatch on NPN,
NMOS and the composed family after delayed admission. Moving only the P02 hook
from a consumed verification callback to the regular update cycle fixed that
verifier bug. All readiness/settlement/non-reentry guards remain. The fresh final
matrix executed and passed all twelve planned routes, including those three.
An intermediate report change failed GWT compilation because JSONString's module
was not inherited. Native JSON quoting replaced that unnecessary dependency;
the later actual five-permutation build passed. Neither failed run is called PASS.

The old Windows native-wrapper Python-launch exit 2 remains unqualified. Direct
Java/Python/report checks do not certify its process-launch/isolation boundary.
No native mouse/keyboard interaction gate was performed in this P02 retry.
After the original full review and F1 repair, both allowed worker delta-review
routes returned usage-limit failures before substantive review. Those attempts
remain failures. The owner subsequently supplied a completed independent review
from a separate session: PASS for the exact candidate, F1 resolved and no blockers.
That review freshly ran the native and compiled checks and its additional oracle;
the production build was hash-revalidated, not rebuilt. Closeout corrected its
sole documentation follow-up and confirmed no drift before changing only records.
No fresh Luna result is inferred from the earlier quota-blocked attempts.

Current checks cover bounded present layouts, not future large-board routing
scalability. The existing P01 review follow-up about extreme shared-route-score
integer accumulation is unchanged and nonblocking for this candidate. P02 does
not silently implement A10, U01, E08, or a historical copper-migration service.
