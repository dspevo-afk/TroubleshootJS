# Routed compactor cut diagnosis — complete

All 14 current15 boards retain every one of the 42 sealed non-time fields,
including geometry, source graph, selected route and work receipts. The observer
changed no layout policy. The final JDK8 unit passed 91 assertions across three
actual compactor fixtures in 45.4756772 seconds; the seven corpus suites passed
in 127.8662223 seconds. The strict receipt/event reader also passed.

| Observed axis outcome | Count |
| --- | ---: |
| No removable strip | 52 |
| Cuts accepted | 10 |
| Placement rejection | 0 |
| Route-quality rejection | 0 |

The 62 axis passes produced 139 events, including 15 cuts totaling 5,400 units
across route alternatives. Those are measurements of existing behavior, not new
board shrinkage. Whole-axis rollback is possible by source control flow and is
exercised by the unit fixtures, but it was **not observed in this corpus**. The
data does not support changing cut acceptance to reclaim these boards' space.

The actual fixtures prove straight copper shortens from 700 to 140 units with
source/net/pad/layer/exposure/width identity intact; an 80-to-60 barrier reduction
rejects the x axis; and a newly excessive detour rejects x while an independent
y cut succeeds. A default-null replay retains exact geometry and work fields.
All three validate their real input before calling the existing evaluator.

The COPY began from accepted1467 source, excluding rejected pose trials. Three
overrides and eight test additions produce COPY1475; its file set differs from
the separately audited main1475 set. Removing only observer/wrapper/registration
adapters reconstructs all three original owners byte for byte. Final source and
24 gate owners were frozen before the successful native phases. The offline
reader passed 49 synthetic cases; the actual selected PowerShell suite transport
passed 12 canaries. [Integrated review](root-review.json),
[strict assessment and cause census](assessment.json), [predeclared scope](proposal.json).

Two failures remain reproducible. Attempt1 failed before fixture execution when
PowerShell unwrapped a one-row suite manifest into two single-character names
(1.1759876 seconds, metadata exit2); 40 exact artifacts and 21 gate owners remain
under `attempt1/`. A shared resolver now preserves the outer array. Attempt2
compiled but failed fixture construction because its package omitted the used
TOP/DEG180 pose (44.9865788 seconds, JVM exit1, metadata exit2); 43 artifacts and
24 gates remain under `attempt2/`. The fixture now uses the existing explicit
pose-declaration constructor. Its geometry and expectations did not change.
Static review had also corrected zero-escape fixture geometry before runtime.
No failed run or earlier PASS was reused as final native proof.

All eight recorded native driver births exited without termination. The COPY
was restored to exact1467 and safely retired; private local state is gone.
Main1475, original unrelated entries and shared tools remain unchanged. Phase
times include maintained terminal cleanup; its duration was not measured alone.
Separate birth-verification times remain in the process-cleanup receipts.

No production interpretation changed. Fresh GWT, player flow, solver/repair,
full matrix, all-seed and manufacturing proof are **NOT RUN**. Publication is on
the task branch only; no main merge or email. Next audit the trace-vertex guard's
ownership and conservatism before choosing another bounded compaction trial.
