PASS : no blocker found.

Reviewed the diff over `5a60d66…` in:

- `CirSim.readCircuit` and lifecycle callers (`needAnalyze`, RC_RETAIN, undo/redo).
- `CircuitSolverExecutor`, `SolverEventQueue`, and private-owner guards.
- A07 lifecycle probes and all six recorded cases.
- A07 browser/report readers and A04 contract vectors.

The fix correctly retires the solver boundary only for non-retaining replacements after resetting `sim.t`; ordinary reanalysis and RC_RETAIN retain queue/clock state. New probes exercise reload, stale events, reanalysis, retain import, undo, and redo, and the readers require all six cases plus the updated assertion floor.

Validation performed: `git diff --check` passed. No browser or build run. The A04 contract script was not executable here because constrained-language PowerShell blocked its dot-sourcing; root validation remains authoritative. Pre-existing `tests/contracts/__pycache__/` was untouched.