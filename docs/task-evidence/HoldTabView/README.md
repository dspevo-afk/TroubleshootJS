# Hold Tab board view evidence

Candidate: base HEAD `8ff4dc4aac2cba20362be54a47fb59f0a7808408`,
branch `codex/task43p-final-recovery`, 2026-09-20. Browser evidence used the
actual production preview at `http://127.0.0.1:8899/circuitjs.html` after a
fresh JDK8/GWT build. The existing preview process was already running and
was left in place. The normal Quick Play board was a fresh control board with
an unrecorded random seed; the exact developer input audit used LED seed 3.

| Check | Result | Evidence |
|---|---|---|
| `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF` | PASS | JDK `1.8.0_502`; five GWT permutations compiled and linked; exit 0. |
| `scripts/verify-current-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Suite U01ViewportContractTest` | PASS | 1,259 assertions; exit 0; task scratch cleaned. |
| Browser CUA click on board canvas, then Tab press in exact LED seed-3 developer route with `tsjVerifyU01=true` | PASS | [Trusted input audit](tab-input-audit.json): keydown `flipped=true`, keyup `flipped=false`; canvas retained focus. |
| Normal Quick Play: accept ticket, focus board, press Tab, inspect View panel | PASS | View panel reported `Top side / top copper` after release. |
| Normal Quick Play: focused Fit bench button, press Tab | PASS | Focus advanced to Zoom +; board remained on top. |
| Normal Quick Play: use View button to inspect face rendering | PASS | [Front](front.png), [bottom reference](back-reference.png), [returned front](returned-front.png); all three screenshots inspected. |

The Browser CUA keypress completes keydown and keyup in one call, so the
bottom screenshot is a reference captured with the persistent View button,
not a screenshot captured mid-hold. The input audit directly observes the
temporary face state from trusted native keyboard events. Full regression
matrix, actual long key hold and hold-across-window-blur were NOT RUN.

The change uses only view state and input/lifecycle handling. No generated
board, circuit, fault, measurement or repair contract changed. The build
included pre-existing uncommitted visual, tray and temporal-solver edits in
the shared worktree; these were left untouched and excluded from this task's
commit. The task did not start or stop the pre-existing preview process.
