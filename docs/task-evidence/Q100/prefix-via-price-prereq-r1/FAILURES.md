# Preserved Phase1 failures and limits

No failed or unrun gate is counted as PASS.

- BASE1: FAIL at COMPILE. A missing closing bracket in the new canary prevented
  Java parsing; no native canary ran. Compiler exit1/termination proven. Exact
  source profile, receipt and scratch remain in external QR.
- BASE2: FAIL at COMPILE. The reflection helper intField was missing; compiler
  reported all nine references. Compiler exit1/termination proven; no native
  canary ran. Its source profile, receipt and scratch remain in QR.
- Root text-patch helper1: FAIL before source mutation because its assumed
  newline style did not match the mixed template. Original source was archived;
  helper2 normalized only the new task-owned canary and applied the intended
  reflection-owner/timing corrections. This was no Java/native PASS.
- Optional real junction negative: NOT_RUN. Automatic approval review rejected
  the proposed owned OS-temp create/read/remove command with the stated reason
  `blocked by policy`. Execution did not start; a read-only check confirms no
  junction exists. It was optional to the frozen required Phase1 gates.

Source-only reviews R1/R2 found incomplete consumed-input binding, missing bridge
copy audits, producer capture-limit and compiler process/argv binding gaps, and
setup ownership gaps. Root corrected these before final acceptance. Actual
source/hash negatives, native output-limit negatives, source/bridge close audits,
compiler-binding CLI negatives and both final native modes pass. Source review
alone supplies no runtime proof. The unrun STUDY draft and original study host
are preserved externally and excluded from delivered tooling.

The process adapter still reads streams to completion. The declared8MiB bound
belongs to the pinned producer's shared stdout/stderr wrapper, installed before
fixture work; actual oversize-chunk children prove rejection before stdout
delegation. No separately capped OS pipe implementation is claimed.

These are native literal fixtures, not a qualification-controller or authored100
run. Existing controller90s/640groups/5s/32768-step settings are unchanged and
not requalified here. Entire canary work and final-output checks remain under5s,
with measured work0.6662132s BASE/0.365132s RAMP. Compilation, native termination
and conservative cleanup are separate. No exact post-report-close timestamp is
exported; successful child exit is also required.

No new GWT/Player/electrical/default/population qualification or routing adoption.
Prior failures, unrelated worktrees and uncertain E06 resources remain untouched.
Playable100=0/betaHELD; all protected-data restrictions remain.
