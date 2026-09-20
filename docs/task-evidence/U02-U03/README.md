# U02–U03 browser evidence

Built production preview on the pre-existing local port 8899, after a fresh
JDK8/GWT five-permutation build. The generated LED board was accepted through
the normal ticket, then J1 `+5 V` received a visible left-click red probe and
J1 `GND` a visible right-click black probe.

- [ac-rms-dc-supply.png](ac-rms-dc-supply.png) shows the real AC mode result:
  `0 V RMS` across that DC supply, with both physical probe markers visible.
- [scope-no-signal-controls.png](scope-no-signal-controls.png) shows the same
  real probes in the one-channel scope. The finite accepted sample window
  correctly reports `NO SIGNAL` for DC and exposes live `T 5 ms/div`,
  `V 5 V/div`, and falling-trigger controls after player clicks.

Focused final-source contracts passed: `U02MeasurementContractTest` (28),
`U03ObservationContractTest` (12), `A07ExecutionContractTest` (24,868), and
`VisualWorkbenchContractTest` (150). The JS bench-meter contract passed 380
checks and the U04 UI adapter contract passed 113 checks. The browser evidence
does not claim a periodic player waveform that this LED fixture does not have;
timestamped known-waveform, gap, bandwidth, alias, reference, stale-operation,
and cleanup cases are exercised by the focused contracts.

Validation used `scripts/verify-current-contracts.ps1` with the repository JDK8
and the four named suites, `node --check` plus the two JS contract files, and
`scripts/build.ps1`. An isolated detached candidate based on
`8b371d98b923449fd143486a159e4bc519c84ce7` received the exact staged U02/U03
binary diff and independently passed those JS contracts and a five-permutation
GWT build (all five cache outputs dated 2026-09-20 02:43:21). Its task-owned
temporary worktree was used only to prove the staged change does not depend on
the preserved P09 edits in the integrated worktree, then was removed after
verification. The pre-existing port-8899 preview remains unowned.
