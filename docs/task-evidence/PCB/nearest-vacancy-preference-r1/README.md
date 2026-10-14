# Nearest connected vacancy preference — private13

Status: accepted for branch publication. The scope was frozen before implementation in
`scope.json`; accepted baseline is private12 commit
`5d4de10b56c10fbf1012a2ac39324382074fc6a1`.

The existing repeated-control placement candidate uses the existing pocket origin
nearest its weighted connected target, instead of always preferring the center.
Only unpaired components receive this preference. The five origins, canonical
order, scoring weights, overlap reward, evaluation count, search bounds, routing
allocation and physical admission rules remain unchanged. Candidates 0 and 2–5
retain their raw placement geometry. No seed-specific placement is introduced.
Private13 selects density12 and ROLE_TRACE@1; saved12 retains density11 with role
widths, while saved1–11 retain their previous interpretation. Public alpha5,
session model1 and dependency epoch16 remain unchanged.

The focused four-suite run passed in 82.0827904 seconds including cleanup, with
all 1,440 frozen consumed inputs unchanged. The independent metric assessment
passed: root42 board area falls from 6,036,952 to 5,519,502 drawing units squared
(8.57%), its largest protected empty rectangle from 225,500 to 178,200 (20.98%),
copper length from 43,440 to 42,230 (2.79%), and vias from 48 to 45. The other nine
representative and untuned cases retain exact baseline geometry and metrics.
The free-space raster does not prove that every empty region is packable.

`source-review.json` records a separate scoped source review with no actionable
finding. The reviewer did not run runtime checks. `final-gate-plan.json` selects
49 affected native suites, all PASS in 553.3999899 seconds including cleanup.
The final-source JDK8/GWT five-permutation build passed in 91.2671063 seconds;
post-native and post-build frozen-input audits passed. The complete registry, all-seed and modest-host
performance qualification are outside this evidence.

Earlier rejected alternatives are retained in the neighboring candidate-zero,
isolation-aware, finite-copper and vacancy-alternative packets. Their passing
unit checks are not packing acceptance. Their changed production paths were
removed before this candidate's final input freeze.

The actual 1280×720 production preview passed cold54 preparation in75.420s/622
work units (max unit1.480s). Actual chip right-click → View schematic, both copper
faces and left-red/right-black regulator pad measurement4.996V were checked.
The unrepaired board failed customer retest. With power OFF and the normal
removal guard, Shop Part55 medium1k replaced original Part30/RDA; customer retest
passed. Actual Save13/download/FileReader13 restored in77.213s/622 with a fresh
owner and the prior owner retired. OFF and one loose part survived, and retest /
Close passed in393.461s overall with all seven cleanup flags.

At319×670, fresh13 cold54 passed in79.552s/622 (max unit1.486s). The unrepaired
board failed retest. The committed repaired12 fixture loaded through the actual
chooser/FileReader in81.673s/608 with fresh ownership and prior retirement. OFF
and the loose part survived; retest / Close passed in222.161s with all seven
cleanup flags. Both browser warning/error logs are empty. Five unedited captures
were inspected; the preview's five identity fields match the final build.

These are finite reference-host runs, not a controlled speed comparison or a
modest-host release. The current54 preparation uses622 work units; accepted12
used608. The unchanged90s/640-work/5s-unit bounds still apply. A fresh60 cold run
is recorded separately under `large60-r1` and does not stand in for a repaired
60-component player-flow proof. Final owned-resource cleanup passed.
The additional fresh60 repair run passed: cold72.826s/586, max unit1.464s; actual
guarded Shop61 medium10k replacement for original28/RSA, customerPASS and
ClosePASS200.351s/all seven cleanup flags. The preceding cold-only run retained a
final FAIL because it was ended unrepaired; see both large60 subpackets. No failed
flow is presented as PASS. All four owned tabs closed, viewport reset, preview
PID3176/port8899 positively stopped, exact owned download removed after byte-copy
verification, and oracle scratch removed after ownership/reparse checks. Shared
tools and the five original untracked entries remain. Post-player frozen1440
input audit passed. `final-gates.json` records the acceptance boundary.