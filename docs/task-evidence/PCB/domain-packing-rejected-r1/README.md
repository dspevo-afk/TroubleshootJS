# Rejected bounded PCB domain-packing trials

Accepted private version 6 remains the implementation. Two focused initial-placement
designs were rejected; no private version 7 production code or save interpretation ships.
Both trials retained six placements, three routes per policy, 60000 evaluations
per placement, physical/electrical guards and the original time/work limits.

| Trial | Native gate | 60-part outline | 53-part outcome | Decision |
| --- | --- | --- | --- | --- |
| Domain capacity hints on moderate candidate 4 | 215 PASS, 40.495s | 8443994 to 9982329 (+18.22%) | Exact version 6 area 7708262 | Reject density regression |
| Mixed boundary first on compact candidate 3 | 217 FAIL, 36.587s | Exact version 6 area 8443994 | No accepted route | Reject lost baseline route |

The first trial split full protected demand by actual conductive courtyard area,
remapped all domain-group X hints into advisory bands, and targeted mixed
UAC/UFB gap centres. Modified candidate 4 hit A_COIL_LOW limits on both seeds. The 60-part board fell back
to roomy candidate 5; the 53-part board retained candidate 2. Increased protected-free cells did not mean density gain.
Pure secondary target movement is a plausible sensitivity, not proven causality:
the logs do not include per-component coordinates. Structural PASS was not
density acceptance. Backing 205 and focused geometry 575 assertions passed.

The second trial preserved all ordinary graph X/Y hints and targeted only actual
mixed-courtyard packages immediately after fixed anchors. Compact candidate 2/moderate candidate 4/
roomy candidate 5, physical checks and route-selection code stayed exact. The 60-part board kept accepted candidate 4;
new candidate 3 failed B_COIL_LOW. On the 53-part board, new candidate 3's preliminary score 1770364 beat retained candidate 2's
1871029 and evicted candidate 2 from the compact category's routed slot. Candidates 4/3/5 all rejected
(LOAD_RETURN total-search, A_COIL_LOW branch-search, CTRL_RETURN branch-quality).
The assertion preserved the lost seed 17 route as FAIL; the backing suite was
NOT RUN after that failure. A retained candidate is not a retained routed
fallback when category scoring can exclude it. No retry or higher cap was used.

The common 80-unit PRIMARY/SECONDARY conductive-courtyard corridor and full
package/escape/service envelopes are necessary checks. The 53-part board's 2030x900 lower-left
patch lies on the PRIMARY side; the corridor does not itself reserve the entire
patch. PRIMARY entry circuitry can target it, subject to actual placement/access/
routing proof. Filling a patch alone does not establish removable outline area.
The actual baseline images and geometry remain in
[the preceding region assessment](../remaining-regions-r1/README.md).
No trial render is claimed; both designs were rejected before GWT/browser gates.

All twenty accepted version 6 source/test/owner hashes were restored byte-exact after
each trial. Both eleven-file code/diff archives and raw receipts remain in task
storage. Original eight untracked files stay byte-exact and unstaged. Previous
native 212/GWT 59/actual browser 224/225 evidence retains its original finite scope.
Native 216/218, GWT 61/62 and new browser/save/side campaigns were NOT RUN.
Static independent reviews found the guards intact; they were not runtime proof.

Next design issue is route-aware integration under the same three-route cap:
preliminary placement quality alone can displace a known successful route.
Resolve that coverage issue before another narrower-primary/boundary geometry
trial. Two-dimensional packing is not exhausted or proven optimal. Backside
remains deferred pending scoped permissions and opposite-face PTH-tail/body/
probe/service guards plus actual flip, repair and save proof. Additional 30%
remains open; human U05/normal population NOT RUN, HARD/normal RB56 HELD and
modest-host proof owner-deferred. No general roadmap advancement is claimed.

Gates ran serially after the ServerCenter 04:00 critical window, with read-only
host samples showing 15%/7% CPU and no relevant competing builds. Both owned jobs,
handles, drainers and scratch retired. No unrelated process/focus/ADB/tablet,
email, main merge, force push, deletion, Core/Android or unchanged Q30 corpus work.
All historical failures and Q30 risks remain preserved.
