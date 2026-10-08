# Q60 implementation checkpoint

This local checkpoint is **incomplete and unaccepted**. The accepted published
base is E06 `7aa0e1932f3c853e7265bf186e98989ada641a51`. Normal RB56 admission,
HARD and normal RB56 save/load remain held. No Q60 push is claimed.

The implementation supplies a shared 40-60-part isolated converter/control board,
typed storage and service ownership, all five fault hypotheses, complete-context
hashing and actual CircuitJS behavior. Electrical laws, fault population, seeds
and 90 s / 640 work-unit / 5 s generation limits remain fixed.

| Gate | Result and scope |
| --- | --- |
| Native R66 | PASS: floorplanning 90,483; A02 geometry 32 |
| Native R67 | PASS: all frozen 40/55/60 contexts 1,166; geometry 1,601; medium physical 59; normal admission 9; P07 11,776 |
| Native R68 | PASS: typed restart backing 202; RB56 temporal/service 295; Q30 temporal 161; D01 168 |
| Final build-r8 | PASS: five JDK8/GWT permutations, 94.606 s, unchanged phase inputs and cleanup |
| Visible-r3 | FAIL retained: stale Remove control after power-off |
| Final visible-r4 | PASS: seed -1017/40, diagnosis, discharge, ordinary removal/replacement, retained original, repaired customer retest and cleanup |
| Complete compiled cohort | FAIL: two of six pass; four HYPOTHESES deadlines |
| Ordinary Q30 seed10014 | FAIL: current 90 s deadline; historical accepted 75.535 s/440 exact work |
| Ordinary Q30 seed10387 | PASS control; does not clear seed10014 |
| E06 compiled mechanics | PASS narrowly scoped positive/forced-cleanup checks |

The final UI correction refreshes existing Remove/Lift controls only when actual
detachment readiness changes. Visible-r4 used the identical frozen seed, expected
40 parts, assertions and budgets as r3. Remove became available after 4.829 s
without reselection. Public measurements identified RENAC; the 10 kOhm catalog
replacement passed customer retest and the failed original remained in the tray.
Operation 76.045 s, cleanup 1.245 s, outer host phase 77.646 s. No injected game
controller calls or hidden fault reads supplied the player diagnosis.

| Frozen seed | Census | Compiled result | Application time / work |
| --- | ---: | --- | --- |
| -1017 | 40 actual | PASS | 54.789 s / 532 |
| -1067 | 55 planned | deadline FAIL | 90.679 s / 374 |
| -1022 | 60 planned | deadline FAIL | 91.108 s / 321 |
| 9007199254741579 | 50 planned | deadline FAIL | 90.469 s / 438 |
| -1004 | 44 actual | PASS | 65.392 s / 537 |
| -1028 | 56 planned | deadline FAIL | 90.090 s / 429 |

Failed reports omit actual census; planned counts come from the frozen workload.
All six terminal reports and cleanup checks are retained; each failed pair and
the aggregate remain FAIL. Current public Q30 seed10014 exposes a sampled
maximum of 435 work units, not an exact terminal count. Its historical exact
440-unit/75.535-second pass belongs to a different source and cannot clear the
current failure. No cause for that performance regression is yet established.

Evidence is indexed in these sanitized packets:

- [Final build, visible flow and reuse boundary](build8-visual4-final-supplement-r1.json).
- [Prior build/native/browser cohort and Q30 baseline](build-native-browser-current-evidence-r1.json).
- [R66/R67 routing correction](routing-evidence-r66-r67-supplement-r1.json).
- [R65 failure](routing-escape-evidence-r65-supplement-r1.json), [escape diagnosis](routing-escape-evidence-r1.json), [context capacity](context-capacity-evidence-r1.json).
- [Earlier electrical/native results and failures](native-evidence-packet-r1.json), [earlier browser results](browser-evidence-r1.json).

The prior build/native/browser packet's word "current" refers to its recorded
pre-UI candidate. Only CirSim and PcbWorkbenchController changed afterward.
Native evidence is reused solely for unchanged electrical/geometry/service
behavior; the final UI has its own fresh build and visible proof. The six-case
cohort and ordinary Q30 regression were not rerun or relabeled after the UI fix.

Root inspected these real production-preview screenshots. Normal labels and
controls are visible; the developer qualification route holds the generated
owner for the bounded flow. This is not an ordinary RB56 menu/save/load pass.

- [Expected unrepaired failure](visible-r3-unrepaired.png).
- [Replacement and retained original](visible-r4-replacement.png).
- [Powered repaired customer retest](visible-r4-repaired.png).

Resume with a measured investigation of HYPOTHESES cost and the separate Q30
seed10014 regression. Keep every hypothesis, fixed seed and limit. Repeat costly
qualification only after a relevant change; do not rerun unchanged Q30 cold/warm
corpora. Complete the affected frozen cases, 60-part visible flow, documented
modest-host and long-session/inventory evidence before Q60 acceptance. HARD
calibration and advanced advertised session/release proof remain U05/REL-B work.

All completed phases have source/cleanup receipts. R41's failed capped-output run
retains its 2,811-file / 13,394,855-byte scratch inventory. Historical Q30 unknown
91.616-second failure, six missing scratch inventories and late BLOCKED release
audit remain preserved. Original eight untracked files remain unchanged. No gate
is active, no email was sent and no evidence or unrelated work was deleted.
