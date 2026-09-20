# Parts tray drag and completed-board return

Candidate: Quick Play LED indicator, exact seed `3`, on branch
`codex/task43p-final-recovery` from `21ae5db58a6d33f59ac1c3b302b60e37495b2aab`.
The preview was already running on port 8899 and was left running.
The staged source tree `3d75dbf11c1852e53896f6b4160099ea3ec8b42d`
was also validated in a clean temporary checkout at candidate
`dc0132305a4b0f108b3eed496ec4b5866a18a05c`.
Only this evidence text and the current task report changed after that
checkout; all compiled and tested inputs stayed identical.

## Cause and correction

- The bottom drawer handle and scrollbar took mouse events while a loose part
  crossed them. Canvas `mouseout` cancelled the controller's part drag. The
  drawer now projects drag state at mouse press/release and makes its chrome
  pointer transparent for the duration of a drag. The native canvas retains
  the drag and receives the final drop.
- `COMPLETED` also disabled the shared workbench interaction guard, so
  returning from the passed result left power, instruments, selection and
  physical actions locked. The passed customer result remains latched while
  the retained board and isolated Shop stay usable. Retest stays terminal.

## Visible production preview

With the previously compiled preview, I accepted the LED seed-3 ticket,
repaired the board with normal clicks and drags, passed the customer retest,
then clicked **Return to board**. Shop, meter modes and Board Power were all
disabled: [before-return-locked.png](before-return-locked.png). The tray source
and handle geometry are shown in [before-tray-open.png](before-tray-open.png).
The first baseline drag happened to complete; the intercept/cancellation
was established from the actual `mouseout` and drawer pointer handlers.

After the JDK8/GWT rebuild, I reloaded the same URL and accepted the ticket.
With Board Power OFF, I removed R1, acquired a `1000 Ohm +/-5%` resistor,
opened the drawer, and used real Browser mouse input to drag from `(279,646)`
through the handle region `(290,578)` and `(306,556)` to R1 `(592,344)`.
The game reported `Part installed`, then the R1 footprint was populated and
the drawer count fell from two parts to one:
[after-tray-drag.png](after-tray-drag.png). I turned power on, ran customer
retest, and reached [after-retest-passed.png](after-retest-passed.png).

I clicked **Return to board**. Board Power and DC V both worked. I turned off
the meter, right-clicked R1, and removed it while the retest button remained
disabled. The now empty footprint and two-part tray are in
[after-return-mutated.png](after-return-mutated.png). Shop reopened and
acquiring another resistor raised the tray count to three. These actions
confirm the returned board is interactive and the prior retest stays recorded.

I repeated the same seed-3 repair and tray-crossing drag against a separate
production preview built solely from the staged tree on port 8901. R1 installed,
customer retest passed, and after Return to board, power, DC V, R1 removal and
Shop all worked. That task-owned preview was stopped with positive port release.

## Gates

| Gate | Result |
|---|---|
| `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF` | PASS, five GWT permutations, linked production output; shared worktree source included preserved unrelated edits. |
| The same JDK8/GWT build in the clean staged-tree checkout | PASS, five permutations and linked production output. |
| `verify-current-contracts.ps1 -Suite VisualWorkbenchContractTest,U04SessionContractTest` | PASS, 150 and 2,683 assertions; cleanup PASS. |
| The same focused Java suites in the clean staged-tree checkout | PASS, 150 and 2,683 assertions; cleanup PASS. |
| `node tests/contracts/tray_drawer_contract.mjs <jsdom>` | PASS, 22 assertions including pointer pass-through and restored handle. |
| `node tests/contracts/u04_ui_contract.mjs <jsdom>` | PASS, 112 assertions including completed-board Shop affordance. |
| Actual preview mouse/keyboard flow above | PASS for this LED seed-3 repair and returned board. |
| Clean staged-tree preview mouse flow on port 8901 | PASS for the same repair and returned-board actions; preview cleanup PASS. |

The full family matrix, hover timing under touch input, and every possible
post-completion mutation were NOT RUN. The preview listener was not task-owned
and remains running. The clean candidate checkout and its port were cleaned
up. Recursive removal of the task-owned temporary jsdom package directory
was rejected by command policy, so that OS-temp directory remains.
