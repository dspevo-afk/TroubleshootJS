# R3 readiness probe: NO_GO; compiled CPU diagnosis

R2's 55/60-part development cases timed out under the unchanged admission caps.
This probe asked whether a shorter natural OFF wait could be justified. It could
not. The production three-second wait, all model guards and limits are unchanged.

The experimental native fixture covered actual selected faults and board-wide
readiness, but used fixed 50us steps rather than the production adaptive recipe.
First run: actual exit1/73.519s, four seed-1017 rows before a physical/runtime ID
namespace lookup error. The lookup was corrected. Second: exit1/74.752s; all five
seed-1017 rows reached readiness, then seed-1067 failed during powered HIGH with
the existing E02 output-backfeed guard (24.23847V, target5V, ceiling24V). No full
15-row result exists. Partial native data does not prove a production regression,
a shorter global wait or a passing admission. No bound/guard was weakened.

`probe-diff.json` preserves the failed experimental source; the three maintained
native fixture files were restored to the original passing R2 bytes. The
1,377-input [restoration audit](restoration-audit.json) passes. The strict readiness
reader's synthetic tests pass, but no actual dataset passes its admission contract.

With the unchanged R2 build5BC4454BB0B4620B6611849B4D993BC0 (1,182-input reuse
audit), a real browser CDP CPU profile captured a new private seed-1067 attempt.
The application again timed out (90,295ms), with cleanup complete. This attempt
has profiler overhead and is CPU diagnosis only, not a development-cohort result.
The raw capture remains task-private; its SHA and the matching GWT symbol-map SHA
are in [cpu-summary.json](cpu-summary.json). The 133.118s capture includes startup
and idle after completion. Idle accounts for40.750s. The bounded LU trailing
kernel accounts for23.119s self time; factorization including that kernel31.383s.
This points to solver matrix work, rather than proving the detached-drawing lead.

The initial reducer exited1 (0.372s): it incorrectly treated each delta as a
nonnegative sample weight. Actual CDP has five out-of-order deltas (-13,-18,-21,
-6,-10us), with all sample IDs present. The corrected reducer reconstructs paired
timestamps, stably sorts them, and assigns each sample through the next timestamp
(last through actual capture end). No sample is dropped or clamped. It records
the five deltas and unassigned initial48,878us; actual reduction exit0/0.773s.
This follows the timestamp-conversion/sort approach in the pinned
[DevTools source](https://chromium.googlesource.com/devtools/devtools-frontend/+/9a696c4e723caa3c7e1f78886da353f1f06a79b0/front_end/core/sdk/CPUProfileDataModel.ts).
Percentages refer to the entire sampled capture; aggregated inclusive functions
count a recursive function once per sample. No speedup/admission follows from it.

Profiler was stopped and disabled; temporary tab26 explicitly closed. Exact owned
preview PID22372/birth639283942785293652/port8914 was stopped with the maintained
script, actual exit0; state/listener absent. Retained driver45951 ended0.
[Cleanup receipt](profile-preview-cleanup.json). Five previously blocked profiles
and the policy-blocked earlier tab20 remain untouched.

Next bounded candidate: [R4 compiled LU access](../q60-calibration-r4/scope.md).
HARD/profile/catalog/public-save gates remain held. Owner play approval is already
recorded; no repeat human trial is required.
