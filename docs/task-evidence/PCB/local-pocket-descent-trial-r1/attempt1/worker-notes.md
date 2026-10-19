# Local pocket descent worker handoff

Status: COPY implementation complete; no native test, runtime, or build was run by this worker. Root owns integration review and execution.

Source identity: the task-provided COPY is source-only accepted1467 with no `.git`, excluding rejected versions16–20; intended base candidate `91ffe9a3c0e444c28b1ad0ae51005071e7fe0c8a`, branch `codex/q60-faulted-profile-counts`. The COPY itself has no Git metadata, so these hashes identify final worker-owned bytes rather than commits.

Owned changes are limited to five existing production files and one new contract unit in the COPY. `Rb56Placement` recognizes private21 and maps it to density15 with `ROLE_TRACE@1`; version15 remains density14 and version20 remains unsupported. Replay parsing/canonicalization uses the private21 identity and preserves signed-long seeds. Constraints admit density15. The actual medium candidate2 seam calls `relaxMediumPockets` after the existing plan/compactor validation. The helper is gated by selected medium policy, density15, candidate2, and the 60000 counter; visits only the four named passive package IDs by component ID; retains footprint-vector order and geometry; charges each distinct snapped non-incumbent origin tried; uses existing vacancy/domain/grid/medium-score/full-placement-validation machinery; and accepts only a strict finite score decrease. The generator change adds density15 beside the existing density14 selected-medium border trim and density13/14 canvas-containment branches.

The native unit declares 10 named fixtures in its marker accounting: raw-root fixtures for -1017, 42, and 4; evaluation-budget fixtures at 59999 and 60000; isolation-package exclusion; selected-policy guard; four-part positive move/eligibility fixture; lower-score access-envelope rejection boundary; and equal-score incumbent retention. Its literal marker, if a later native run succeeds, is `PASS: PCB local pocket descent contracts assertions=N fixtures=10 evaluator=localPocketDescent`. The unit prints only that marker; the `rg PCB_` static scan found no extra receipt output.

The negative boundary uses a valid, `constraints.validate`-checked four-part incumbent. A translated R1 proposal at Q1's origin has lower independently recomputed existing no-link cost and overlapping access-envelope rectangles; the unchanged placement validator is expected to reject it with exact reason `ENVELOPE_ACCESS_OR_DOMAIN`. The test then calls the actual helper and validates its returned plan. This is deliberately limited: the hypothetical origin is not claimed to be a generated vacancy target or attempted by the helper, and the test does not claim the `PcbAccessPlanner` disconnected-channel check or local typed-rejection catch was reached. A valid constraints fixture cannot anchor a passive: `PcbPlacementConstraints.validate` rejects non-connector anchors, so the positive fixture instead covers an unanchored connector, an anchored connector, and an unanchored nonpassive remaining fixed while R1 moves.

Literal expectations encoded but NOT RUN by this worker:

- Current private15/density14 and candidate IDs0,1,3,4,5 remain exact in the three raw-root comparisons; private21 has the same ordered source intent/live graph, density15 and candidate2 helper/seam determinism.
- Private20 replay rejects; private21 replay round-trips `Long.MIN_VALUE` exactly.
- One available snapped trial at counter59999 returns at60000; counter60000 returns the same input plan before optional setup.
- The positive fixture moves only R1, preserves package/pose/pads/probes/escape identity and source graph, strictly lowers the independently calculated existing score, and remains within five trials.
- The lower-score overlapping alternative rejects as `ENVELOPE_ACCESS_OR_DOMAIN`; the actual helper result remains fully placement-validated.
- Equal-score placement retains the incumbent.

Static source review performed: inspected the proposal and owned production/helper/test sections; confirmed the selected-policy/density15/candidate2 guards, early 60000 return, strict finite score condition, full `validate` call and typed `Rejected` catch; confirmed density15 appears only as the requested selected-medium trim/canvas parity; counted 10 `fixtures++` contributions (three per raw root plus seven distinct helper/plan-loop scenarios); confirmed one stdout marker and no raw `PCB_` receipt prints. No Java compilation, native unit command, simulation/runtime, GWT/JDK build, or corpus execution was performed. No Git stage/commit/push or cleanup was performed.

Final SHA-256 for worker-owned COPY files:

| File | SHA-256 |
|---|---|
| `src/com/lushprojects/circuitjs1/client/Rb56Placement.java` | `FE1771DC3D24B9914F0EAFC920ECED21C6782E007CDBBD11729BDCD4024A7E92` |
| `src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java` | `5534541B3D218BDE55A76CC092D478850A6A8C01DB07C9BB5645F993D77FCC62` |
| `src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java` | `EA7BF03FD2AB9EE6F4C1A679507FFE74ACF5E0719DB500F02AB3B07436EF7A6E` |
| `src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java` | `F0F89A654AEDBE145C563F61FCBEA7436A461448129F2D04CC1A73E4AA28DD5F` |
| `src/com/lushprojects/circuitjs1/client/PlayerSessionReplay.java` | `AD838031BFB46D8819B302F5A627237A4038BBDEA4970B4C12CDF635F149FB2A` |
| `tests/contracts/PcbLocalPocketDescentContractTest.java` | `A416E7F62216D045A512BC8D7AAE77C490B6D945AD3A917B593B22B1AF3D7AF2` |

Next action: root review/freeze and actual native execution; this worker reports no test PASS.
Root pre-freeze audit caught a stale unit hash after the final assertion wording correction. The table above now matches the actual final bytes; no native phase or input freeze had occurred.
