# Minimal visible Q30 acceptance review - NOT RUN

This checklist is for the reviewer. No screenshots or visible interactions have
been produced in this session; graphical/node_repl tools are unavailable. Current
shipping Q30 stays disabled. Cold/native/compiled/menu/archive readiness gates
are still pending, so this is not a request to perform a review on an unready build.

## Preview prerequisite

After private readiness gates pass, prepare an isolated copy of the exact final
source, apply only its reviewed catalog boolean for the acceptance preview and
build all five actual production permutations with scripts/build.ps1 -JavaHome
<verified-JDK8> -Target Compile -Style OBF. Keep the shipping worktree disabled
and preserve its dedicated disabled-family negative fixture. Explicitly build;
BuildIfMissing can serve stale output. Use maintained start-preview.ps1 with a
unique port, record source/build identity plus PID/creation/executable/port, then
stop-preview.ps1 for that exact owned preview when finished. The challenge switch
does not accept arbitrary Q30 routes; use the ordinary menu below. Parent must
provide the exact ready preview URL and candidate identity with this checklist.

## Three ordinary-player flows

Run these exact seeds in order:10387 (20 parts/one channel),10226 (30 parts/one
channel),10014 (40 parts/two channels). Confirm the actual package/seed identity.

1. Main menu: MEDIUM -> Board family "Multi-rail control board" -> "Enter an
   exact seed" -> decimal seed -> "Prepare exact seed". Capture the Customer
   ticket, then "Accept ticket and start". The ticket must not disclose a hidden
   fault, debug flags, answer key, netlist or neat schematic.
2. Connect the named sources in "Supplies". Use visible sensor LOW/HIGH controls;
   for the two-channel board inspect both LOW, A-only HIGH, B-only HIGH and both
   HIGH. Run "Run customer retest" before repair and retain the actual failure.
3. Diagnose through the live PCB and meter controls. Left click places red,
   right click black; selecting the active mode again exits it. Record at least
   one meaningful powered DC observation and an appropriate isolated check.
   Use visible input; no injected controller calls or developer stepping.
4. Switch every supply off and wait for discharge before modifying the circuit.
   Use the physical component context actions "Lift lead ..." or "Remove
   component" as needed; Shop -> compatible spec -> "Add to Parts Tray" ->
   select stock -> "Install as ...". Record the component/stock/repair actually used.
5. Restore the required supplies and sensor conditions, then "Run customer
   retest". Require "FUNCTION VERIFIED" and the actual customer-pass message.
   Verify all two or four input combinations, not only the repair click.
6. Record exact replay identity, actual observations and any failure. A failed,
   timed-out or incomplete flow stays FAIL/NOT RUN. Stop and report a new blocker.

Reviewer reference only (keep these answers out of player UI):

| Seed | Frozen fault / compatible repair | Functional expectation |
| --- | --- | --- |
|10387|Open KA coil; compatible 5 V relay replacement|Load A follows LOW/HIGH after repair|
|10226|Open DREV; correctly specified protection diode replacement|12V feed/load response restored|
|10014|Open 10 kohm REN; compatible resistor replacement|A-only,B-only,combined load response restored|

These expected causal symptoms derive from the frozen plan and provider behavior;
retain what the actual UI/solver shows. The visible complaint is "One or both
loads fail to follow their sensor inputs. Check each channel separately and together."
Unrepaired retest should fail; valid repair should resolve the customer behavior.

## Evidence to return

Keep a timestamped interaction ledger for all three complete flows: seed/candidate,
complaint, supplies/inputs, observations, pre-repair retest, actual repair, post-repair
retest and limitations. Save and inspect 2–5 screenshots under a fresh
docs/task-evidence/Q30/visible-player-flow packet. Suggested five views:20-part
unrepaired ticket/failure;20-part repaired pass;30-part live diagnosis/unrepaired
failure;40-part dual-channel initial failure;40-part repaired pass. Screenshots
support the interaction ledger; they do not certify unrecorded player inputs.
