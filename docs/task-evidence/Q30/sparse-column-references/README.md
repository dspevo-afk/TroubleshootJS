# Q30 sparse column row-reference candidate — unaccepted

Accepted production baseline is 7a30a3f. Packed-column masks and per-row index
lists passed correctness checks but regressed cold timing and were removed.
The accepted-source profile measured LU at 61.5% of sampled kernel time and
82.55% zero row-factor probes. This candidate targets that measured work while
preserving the accepted column/term loop order and coefficient hoisting.

For each finalized lower column, store references to the distinct numeric rows
with nonzero post-scaling entries. Future pivot swaps exchange outer row
references, so membership follows the numeric row without index repair. Each
cell still receives subtractions in ascending lower-column order. Rows within
one term are independent under the audited unique-row ownership contract.
Full finite-input scanning, zero-row detection, arithmetic guards, pivot ties,
epsilon handling, solves and all solver stepping remain mandatory.

The refined profile also identifies scaling as the third-largest sampled phase.
After the multiplier is proven finite, an existing zero lower entry can skip
multiplication, its redundant finite-result check and the write. Nonzero inputs
still compute and validate the scaled result before testing for underflowed zero.
This shares the accepted numeric-equivalence scope; signed-zero bits are not
promised. The independent historical oracle must still match numerically.

Scratch allocation is per CirSim; standalone calls use their own scratch.
Every populated row reference and count must be cleared in `finally`, including
success, singular return, nonfinite input and arithmetic overflow. No mutable
matrix references survive a completed call and no graph/proof cache is added.
Failure-side partial matrices need not have the same independent row visitation
order; numerical failure and restoration/cleanup behavior must remain intact.

Predeclared focused gates: independent historical factor/pivot/solve oracle,
forced later pivot, dense/sparse and dimension-boundary fixtures, workspace
cleanup and reuse after every exit, inversion, four native suites, actual
final-source JDK8/GWT build, compiled A07 with strict reader/negative canaries.

Fresh private cold comparison order: C7, B7, B13, C13, C64, B64. C is the new
candidate, B the frozen accepted source/build. Stop the batch after C7 unless
it improves the recent accepted 106,447-ms seed-7 control. Any rejected pilot
gets a separate fresh control and retained failure evidence. Compare exact
requests, full electrical/physical proof context, hypotheses, all observations,
service/retest/completion outcomes, work units, cold-cache isolation and cleanup.
Warm rows are recorded separately and excluded from cold claims.

No final acceptance matrix while hard gates fail. The normal 90,000-ms
cumulative / 640-unit / 5,000-ms operation limits and private measurement
isolation are unchanged. Q30 is BLOCKED, publication disabled, scale 33/35/37.
No push; U06/U07/Q60 and later milestones remain unstarted.
