# Saved13 canvas-boundary regression

The maintained focused native suite passed in53.6755478s including terminal
cleanup, whose duration was not separately measured. Its1,600 assertions prove
the precise negative fixture: actual saved13 root4 throws
`PCB board outline leaves workbench canvas` at
`SeededPcbLayoutGenerator.finishRoutedPlan`, line851 before any production edit.
All1,442 consumed inputs remained byte-exact. This proves the existing failure,
not physical admission or a repaired board.

Before and after generation, the fixture checks the plan's full declared
component/type/package, terminal-to-net mapping, component/net pad memberships
and external power bindings. It compares a canonical live-board graph digest,
including routing roles, plus the complete plan digest. The plan digest includes
placement metadata; it is a per-version purity check, not a cross-version hash.
No geometry measurement values from the preceding four-root collection were
displayed or inspected.

Root source review and a separate read-only source investigation identify the
cause: fallback crop margins can enlarge an asymmetric validated outline when
represented by one symmetric margin. Placement validation does not check canvas
fit; moving an oversized crop to origin zero leaves it oversized. The final
geometry check throws after mutation, outside typed candidate rejection handling.
The pre-crop layout has already passed full geometry and routing validation.

Next trial is declared separately in `../canvas-guard-trial-r1/scope.json`:
retain that validated layout before mutation when the resolved crop cannot fit
the canvas. It remains an unimplemented proposal here. Existing physical limits,
candidate/work bounds and saved13 behavior stay. Native-only fixture/registration
does not change production or compiled web, so GWT/player gates were not rerun.
Root reviewed source and registry diff. Native and verified snapshot cleanup
passed; no preview/tab/viewport/download exists. No main/email.
