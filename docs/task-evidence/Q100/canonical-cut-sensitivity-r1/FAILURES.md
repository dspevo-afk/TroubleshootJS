# P7 failure record

This sanitized record keeps the unsuccessful attempts visible beside the accepted
candidate. Raw receipts and private logs remain in the task-owned evidence store;
they are not copied into this draft.

## Resolved before final acceptance

| Attempt | Observed failure | Resolution and disposition |
|---|---|---|
| P7 input freeze R1 | `sys.executable` reported a Windows application alias/reparse path and failed the existing executable-path guard. | Used the genuine P6 preflight receipt's selected `FilePath` for the approved interpreter. The guard was not weakened; the earlier failure and partial manifest remain preserved. |
| CANARY consumer R1 | The consumer expected original process logs to remain at paths inside the run root. The wrapper had removed its original scratch logs and the host saved separate stream copies. | Validated the original scratch-path identities and absence, then compared copied bytes with the receipt strings. The corrected CANARY R3 and strict consumer passed. |
| ANALYZE R1 | The genuine P6 reader passed, then host receipt framing rejected CRLF before the worker started. All four cases and post-source verification were `NOT RUN`; aggregate cleanup failed. | Corrected only the local parse path while preserving original receipt bytes and kept the worker's strict LF requirement. The failed attempt was not counted as a case result. |
| Host negatives R1–R2 | Negative controls exposed double trimming/CRLF handling and a missing expected-failure argument. | Corrected the negative harness. Seven selected-host negative cases passed in R4. |
| Reader negatives R1–R2 | The maintained guard was called before module loading, a self-pin role was wrong, one bit-count negative expected a more specific reason than the derived-set guard promises, and PowerShell `GenericList @()` conversion prevented result writing. | Added a local bootstrap reparse check before authenticated import, corrected the pin role, asserted the source-based result mismatch and used `ToArray()`. The harness's initial 40-minute outer value was corrected to the original 30-minute cap before the complete passing runs. |

## Test and accounting corrections

- An early literal BFS case omitted start initialization. The fixture/core path was
  corrected before the final selected suite.
- The suite initially referenced `P7_CASES` while the declared constant was
  `P07_CASES`. The name was corrected; no expectation was changed.
- An initial test guessed an exact 35-group total. R3 showed 38 legitimate timed
  groups, including three variant-selection groups. The brittle equality was
  replaced with the contract-based lower bound that groups cover copy, BFS, and
  opaque work, while preserving total-observation and hard-cap checks.
- Before the actual case, the core reused unchanged `vias`, `goals`, and `starts`
  payload lists after each codec result was checked for exact type, length,
  element type, and value equality with the immutable base. Each variant still
  receives a fresh `steps` list and the pinned codec still creates a new graph.
  This source-lawful reuse required the tiny fixture's expected copy work to be
  recalculated from its four array lengths; semantics and independent graph
  expectations were unchanged.

## Final evidence state

The final selected suite passed 21 tests. The actual CANARY, strict consumer,
seven host negatives, four-case analyzer, source/process checks, and cleanup
checks passed. The earlier failures remain failures in their original receipts;
they are not relabeled as passes. No production source, solver behavior, or
electrical qualification was changed to resolve them.

Reader-controls R3 passed all 13 cases but also emitted the returned JSON byte
array to the console. Silencing that unassigned return left the retained result
unchanged in meaning; fresh R4 passed the same control and 12 exact rejections
with a single completion line. Failed R2 result aggregation produced no
`results.json`; its available child process and stream receipts remain retained.
