# Q30 sparse column row-reference optimization — accepted intermediate

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

## Results and acceptance boundary

The predeclared comparison completed without source/build changes. Four native
suites PASS in 47.584 s, including 271,331 independent A07 assertions and the
temporal, private-budget and normal-policy contracts. Actual final-source JDK8
`scripts/build.ps1` GWT five-permutation build PASS in 80.496 s. Compiled A07
PASS in 5.202 s host time, including strict reader and three corruption canaries.
Independent read-only source review found no actionable correctness blocker;
root reviewed the integrated diff and receipts. Review did not run tests.

| Seed | Control cold s | Candidate cold s | Saved s | Proof saved s |
| --- | ---: | ---: | ---: | ---: |
| 7 | 106.647 | 86.809 | 19.838 | 18.715 |
| 13 | 114.733 | 93.243 | 21.490 | 20.341 |
| 64 | 103.935 | 82.580 | 21.355 | 20.082 |

All six maintained runs, exact strict readers and 37 reader corruption canaries
PASS. Each paired exact request and every diagnostic-proof field except elapsed
time match, including physical/electrical context, canonical partition, five
hypotheses, 185 observations, all service/retest/completion results and 390 proof
units. Shared job units remain 468/464/446 by seed. No physical, settling,
hypothesis, routing, proof or cleanup requirement was removed. Each cold run
starts with an empty private cache and one proof miss; normal-cache state is
isolated. Warm rows are retained separately and excluded from cold timing claims.
Every browser/server cleanup PASSes with no owned survivors; cleanup takes
0.825–1.232 s separately from operation time.

Counterbalanced paired gains occur in both ordering directions. Routing differs
by only 10–115 ms, while proof gains are 18.715–20.341 s. Host monotonic duration
corroborates the gain; wall-minus-monotonic drift is below 0.005 ms. This supports
attribution to the frozen solver delta rather than private-cache warmth or the
previously observed small control variation. There is one pair per seed here,
so this does not estimate a population percentile or prove zero measurement noise.
The three bundled kernel changes are accepted together; their individual gains
were not separately measured.

Candidate source digest `7f07fee8219ff26686b531f33e57b156a04340268aad939081184b862decae30`,
web digest `6c902db8657d282930b52c4ee8c1ee7df4ba099286f95379486ba8da93a13fd5`.
Both match every candidate run and the current consumed inputs. A frozen copy
is retained outside the repository for the next paired routing experiment.
Control input manifests match the accepted 7a30a3f source/build. Complete
sanitized raw receipts, manifests, commands, per-stage timing and reader output
are stored beside this file. Personal paths are removed, including compressed
JSON contents; no live profile or private browser data is committed.

**Q30 remains BLOCKED / NOT ACCEPTED.** Seed 13 still exceeds 90,000 ms;
seed 7 has only 3.191 s margin. These are private 300-second measurement runs,
not normal production admission. Full final matrices are NOT RUN, required
20–40 scale remains unimplemented beyond 33/35/37, and publication remains
disabled. Limits, candidate ordering, replay schema and generic owners are
unchanged. No push or completion email.
