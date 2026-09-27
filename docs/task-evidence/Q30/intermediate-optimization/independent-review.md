# Independent review before further implementation

Fresh read-only reviewer; base `745d537`, current uncommitted source diff.
The reviewer read the algorithm, independent oracle, callers and continuation
evidence. It made no edits and ran no tests/builds. Root owns fresh execution
and acceptance below; this review alone is not a runtime PASS.

1. **Change:** in the existing Crout factorization, skip a row update when its
   already-finite lower-column factor compares equal to zero. Expand the
   independent historical oracle with connected band, arrowhead and bridged
   sparse systems through 81 rows. No other production behavior changes.
2. **Mechanism:** sparse matrices otherwise perform a multiply, subtraction,
   finite-result check and array write whose numeric result is unchanged.
   The branch removes that work inside repeated factorization. The earlier
   sampled profile identified this kernel as the largest contributor.
3. **Electrical correctness:** all matrix input cells are checked for finiteness;
   computed updates, pivot inverses and multipliers retain their finite checks.
   A finite zero product cannot overflow or alter a nonzero entry. All nonzero
   arithmetic order, pivot comparisons/ties and solve calls remain. Numeric
   factors/pivots/solutions are checked against a separate historical algorithm.
   IEEE signed-zero bit preservation is not claimed; equality tests intentionally
   compare numeric values. No correctness blocker was found within that contract.
4. **Determinism:** the change introduces no random draw, candidate ordering,
   seed conversion, identity or cache operation. Prior three-seed request,
   partition, sample and repair/retest evidence matches exactly. Fresh paired
   comparisons must confirm this again before intermediate acceptance.
5. **Requirements:** no proof, hypothesis, settling interval, trial, solve,
   routing attempt, physical admission, service, repair, retest, ownership,
   cancellation, cleanup or acceptance step was removed. Normal 90,000/640/5,000
   limits and disabled Q30 publication remain; private measurement cannot
   authorize normal admission. The production diff is confined to the kernel.
6. **Attribution:** prior before/after evidence is promising but insufficient
   to exclude order/state noise by itself. The fresh predeclared interleaved
   control/optimized experiment uses independent browser processes/profiles
   and empty cold proof caches, with reversed seed-13 bracketing pairs.
   Warm rows are explicitly excluded from the cold claim. Neither experiment
   establishes normal deadline acceptance or a population p95.

The review recommends intermediate acceptance only after fresh focused
regressions, same-input electrical/proof comparison and complete cleanup.
Root selected A07ExecutionContractTest, Q30TemporalWorkContractTest,
Q30GenerationMeasurementBudgetContractTest and Q30NormalExecutionPolicyContractTest.
These cover the changed math plus actual transient work, power/successor
cancellation and frozen private/normal execution boundaries. Every timed
coordinator run additionally executes all five diagnostic and repair hypotheses.

Fresh execution completed: all four selected native suites and eight compiled
runs PASS. Every paired request/proof/electrical/service value agrees; cleanup
has no survivors. Root reviewed the integrated source/evidence diff and accepts
the bounded intermediate optimization. The runtime attribution and limitations
are quantified in comparison.json and README.md. Q30 itself remains BLOCKED.
