# Post-selection border reduction — accepted source

Base: `fc8751e07859febc1bedabddafe7a063582f89f9`. This packet records a
source-bound experiment separate from the rejected soft-isolation experiment
that also used an unpublished explicit15 number. Neither experiment was a
supported replay interpretation before promotion. No main merge or email.

The new policy contracts the **selected** routed board, after existing route
selection. It removes whole ten-unit steps of excess uniform border while
preserving the drawing-grid phase. The target is at least26 units and at least
the exact access-envelope protrusion plus10. It deliberately replaces the old
conservative accessMargin+30 floor at this final stage. Routing, component
arrangement, copper lengths, widths, vias, physical isolation, source streams
and search/work limits retain their existing contracts.

The trim first validates against the existing final outline as fallback, then
works on an isolated mutable copy. Expected placement or route-quality rejection
retains the original selected layout. Unexpected errors and cancellation
propagate. A successful copy refreshes the winner's actual area and route-quality
metadata without reranking candidates. Workbench canvas and parts-tray placement
remain separate from physical board extents.

## Native experiment

`proposal.json` predates implementation/runtime. All fourteen roots are informed
regression cases, not holdouts. Attempt3 passed ten suites in190.6914293 seconds,
exit0, with1480 frozen inputs audited unchanged and24 synthetic assessor guards.
The strict metric assessment passed: eight boards shrink1.45–3.18%, six retain
exact geometry; protected void does not grow and copper/vias stay exact in every
case. Selected attempts, ranks, outcomes and work receipts stay exact. All14
saved-version14 results match the independently pinned published baseline.
The paired and literal contracts executed313734 assertions. Cleanup is included
in phase time and was not measured separately. Recorded driver identities exited.

The actual proof is `focused-native.txt`, its numeric timing/receipt, frozen
inputs and audit, and `focused-assessment.json`. Native checks do not establish
solver behavior, a production build, visible player flows, all seeds,
manufacturing suitability or timing on a modest host.

## Preserved failures and oracle corrections

- The development compile failed because a new quality-score call omitted its
  required board argument. The corrected call is in both source and reproducer;
  sanitized diagnostics and raw/sanitized hash receipt remain.
- Attempt1 failed at a copy fixture that assumed negative x was invalid. The
  existing coordinate system permits it. The fixture now checks the documented
  invalid negative margin; production bytes did not change for this correction.
- Attempt2 failed at a raw junction-serial comparison. Existing conductor building
  sorts coordinate-string keys, so translation across decimal digit boundaries
  can renumber derived junction/edge serials and reverse edge endpoint order.
  The graph builder is unchanged. The corrected oracle establishes a complete
  semantic bijection of every translated junction/net/layer, edge/endpoints/
  width/provenance, stable land/terminal/pad-face mapping and probe surface.
  Fourteen independent positive/negative graph fixtures test renumbering,
  miswiring, exposure and bindings. Source component/pad/trace identities remain
  exact. The failed original fixture and logs remain under `attempt2/`.
- Repeated preparation had duplicated registry entries. Attempt2 stopped before
  duplicate execution. Preparation now reconstructs ten unique definitions from
  the verified original registry snapshot. No maintained execution bound changed.

Neither failed attempt qualifies the candidate. Their frozen inputs, terminal
failures, elapsed phase times, source snapshots and cleanup records are retained.
No metric tolerance, root, route budget or physical threshold was relaxed.

## Final integration and acceptance

Current/private15 maps to density14; named explicit saved14 maps to density13.
Public alpha5, session/model1 and dependency epoch16 remain unchanged. Current
identity fixtures retain literal old14 oracles and add15/future16 boundaries.
Final selected66 native PASS830.0759271s, exit0, including clean teardown;
1466 frozen source/gate inputs remain exact after native, build and player work.
The unchanged strict metric oracle passes, and `promotion-reproduction.json`
also compares every non-time14/15 export field exactly with the explicit trial.
Actual final-source JDK8/GWT five-permutation compile/link PASS93.6715477s.
Build-log path prefixes alone are sanitized with raw/sanitized hash receipts.

The visible production preview binds all five served identity fields to
`preview-expected.json`; post-player execution bytes match. Desktop1280 root4
admits55 packages in67818ms/601 work, max unit1716ms. Real voltage probes are
placed using the visible board-view controls; the output reading evolves with
the live solver. DC mode toggles off through the same mode button. Power-off
initially keeps component service disabled until real stored energy discharges.
The correct acquired catalog part is installed in REN through ordinary controls.
An initial retest while power was still OFF fails and is preserved in
`desktop-unpowered-retest-dom.txt`; power ON then gives ordinary customer PASS.
The actual downloaded2145-byte file carries private15. FileReader15 restores in
67361ms/601 work, with a fresh owner, prior owner retired, power OFF and one loose
part. A fresh powered customer retest passes. Close session gives all seven
cleanup flags true, total wall350666ms.

Narrow319 root4 cold admission takes73850ms/601 work, max unit1549ms. The actual
unchanged saved14 file (SHA453928609c7a09c533da3e7df64bd08190ac384b5982cdd712fde7e2a1bb2c71)
restores in73129ms/601 work; fresh/prior owner and OFF/one-part state checks pass.
Powered customer retest and all seven Close cleanup checks pass, wall211747ms.
Both cases retain90000ms/640/5000ms and360000ms hold limits. Browser warnings/
errors are empty. Five raw inspected captures show ticket privacy, meter/board,
customer PASS, restored15 and narrow restored14. `player-acceptance.json` checks
the recorded actual flows; it does not perform or simulate them.

The narrow capture shows crowded labels at a zoomed-out fit. This is an observed
open rendering limitation, not a passing legibility or general responsive-layout
claim. New code does not change renderer, toolbar or instrument/input owners.
No fresh chip-menu or canvas left/right-hit proof is claimed for this packet;
their unchanged handlers retain prior evidence, while native mappings and actual
visible terminal-selector probes are fresh here. Large internal pockets remain.

`source-review.json` records optional source review of the pre-promotion physical
implementation only. It does not certify subsequent replay/default changes.
Root reviewed the integrated promoted source and migrated fixtures separately.
`player-scope-preparation-failed.json` preserves a PowerShell array-precedence
failure before player input; grouped paths/count checks fix it, and its valid
three-owner scope is audited unchanged. No product source or limits changed.

The private recovery state and five verified original snapshots have been
removed after acceptance with nonrecursive containment/reparse/hash checks;
accepted edits remain. Preview23632 exited, both task tabs closed, viewport reset
and recorded native/build driver identities exited. Shared tools and HTTP.sys
PID4/port8899 remain untouched. Original five unrelated untracked entries remain.
No main merge/email, full native matrix, independent electrical oracles,
unseen/all-seed/manufacturing or modest-host proof. Next: bounded raw-plan pocket
decision measurement in one pre-compaction coordinate frame, before any new
placement heuristic. Publication is recorded by the commit containing this packet.

## Commands and input boundary

Final host invocations used the maintained driver below, serially, with the
configured JDK8 and Python executables supplied as external local paths. The
initial `final_inputs.py freeze` preceded execution; each later audit compared
against that immutable file. Do not replace its expected hashes on mismatch.

```powershell
& $taskPythonExe -X utf8 -B docs/task-evidence/PCB/post-selection-edge-trim-r1/final_inputs.py freeze
& docs/task-evidence/PCB/post-selection-edge-trim-r1/run_final.ps1 -JavaHome $taskJdk8 -PythonExe $taskPythonExe -Phase native
& $taskPythonExe -X utf8 -B docs/task-evidence/PCB/post-selection-edge-trim-r1/final_inputs.py audit post-native-input-audit.json
& $taskPythonExe -X utf8 -B docs/task-evidence/PCB/post-selection-edge-trim-r1/final_assess.py
& $taskPythonExe -X utf8 -B docs/task-evidence/PCB/post-selection-edge-trim-r1/compare_promotion.py
& docs/task-evidence/PCB/post-selection-edge-trim-r1/run_final.ps1 -JavaHome $taskJdk8 -PythonExe $taskPythonExe -Phase build
& $taskPythonExe -X utf8 -B docs/task-evidence/PCB/post-selection-edge-trim-r1/final_inputs.py audit post-build-input-audit.json
& scripts/start-preview.ps1 -Port 8907
# Actual visible browser interactions recorded above; no injected controllers.
& $taskPythonExe -X utf8 -B docs/task-evidence/PCB/post-selection-edge-trim-r1/final_inputs.py audit post-player-input-audit.json
& scripts/stop-preview.ps1 -StateFile .tools/preview/state.json
& $taskPythonExe -X utf8 -B docs/task-evidence/PCB/post-selection-edge-trim-r1/check_player.py
& $taskPythonExe -X utf8 -B docs/task-evidence/PCB/post-selection-edge-trim-r1/adopt_snapshots.py
```

The driver captures actual parent/child process birth identity before work and
checks the maintained native or build script's terminal success. Its commands
resolve the repository from the packet location. `unchanged-owner-audit.json`
enumerates exactly eleven changed prior inputs (six production, four migrated
tests, registry); all other1436 prior accepted inputs stay byte-exact.
