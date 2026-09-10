# A07 schematic-replacement lifecycle correction

Base: `5a60d66cd054aa9902f199d33c1bcf7a137dda25`
Branch: `codex/task43p-final-recovery`

## Scope and result

PASS: non-retaining `CirSim.readCircuit` retires the previous solver execution
context when it resets simulation time. Reusing the same `elmList` cannot
carry the old accepted-event clock or scheduled callbacks into the new circuit.
Actual reload/import, undo and redo entrypoints are covered. Ordinary reanalysis
and `RC_RETAIN` imports keep their legitimate current clock and scheduled work.
The backwards-time guard, private-owner guards and event ordering are unchanged.
A08 and broader solver or schematic-editor refactors are outside this correction.

## Closed validation set

| Gate | Production path / oracle | Result |
|---|---|---|
| Red compiled regression | New lifecycle verifier with unchanged base CirSim; same-list replacement after accepted steps | Expected `FAIL:a07:Invalid accepted-state event boundary` |
| Native contracts | Maintained `scripts/verify-current-contracts.ps1`, JDK 8u502, explicit CPython 3.12.8 | Exit 0: 17 Java suites; seed/value/role oracles; 271 report assertions |
| Final production build | Maintained `scripts/build.ps1`, JDK 8 / GWT 2.7, OBF | Exit 0: all five permutations |
| Final compiled routes | Fresh Chromium contexts, actual production preview, exact maintained A07 route definitions and strict readers | 9/9 expected outcomes |
| Lifecycle assertions | `A07SolverDeveloperVerifier` calls actual readCircuit, needAnalyze, doUndo and doRedo | Six required cases; A07 runtime total 71, previously 53 |
| Independent review | Fresh gpt-reserve MAX, read-only integrated diff and lifecycle callers | PASS; no blocker |
| Final source identity | Captured preview execution digests plus four code/reader/test hashes | Recorded in candidate-provenance.json |

The six required cases cover an empty old event clock, stale callback rejection,
reanalysis preservation, retaining-import preservation, undo and redo. First
post-replacement steps must be accepted before reaching the old clock; stale
actions must remain unfired even after the new clock passes their original due
time. Report-contract negatives reject each missing or failed lifecycle case.

The nine compiled routes are A07 positive / forced-negative / debug-off,
A06 positive / forced-negative / debug-off, A03, Task49 and stored energy.
Existing A07 shared pure assertions remain 38 and model assertions remain 59.
The deliberate A06 forced failure emits its exact failure marker and one
AssertionError. OBF names that error `L6`; the retained adjudication identifies
the source and compiled mapping. Other routes emitted no page errors.

## Evidence and limits

- `candidate-provenance.json`: exact tested source hashes, execution digests,
  commands and toolchain. Documentation-only closure does not alter those inputs.
- `red-result.json`, `build.log`, `native-summary.log`,
  `compiled-validation.json`, `a07-report.json`: readable outcomes.
- `native.log.gz`, `native-receipt.txt.gz`, `compiled-reports.json.gz`:
  sanitized raw native and compiled evidence.
- `review.md`: independent report. Its constrained PowerShell could not run the
  dot-sourced contract script; root's successful complete native run is separate.
- `cleanup.json`: exact task-owned preview/process and browser cleanup.

These are compiled behavioral checks through actual application methods, not
fresh manual or OS-input player interaction. No such manual run is claimed.
The previous A07 player evidence remains historical. This correction does not
certify the CLI wrapper's process-ownership preflight; equivalent behavior and
maintained reader checks are deliberately reported separately.

Only the four identified code/reader/test files change behavior. Architecture,
roadmap and task-report edits record the correction and reconcile the stale
roadmap summary. A08 remains unstarted and needs separate authorization.
