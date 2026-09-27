# Q30 sparse row-list candidate — REJECTED

Accepted production source: `7a30a3f`; evidence-only checkpoint: `5418b98`.
The rejected packed index spent 40.0% of sampled LU phase time in pivot search,
row swaps and mask repair, plus 19.3% in scaling/index construction. The next
candidate uses ascending finalized lower-column lists attached to each row;
scratch-list ownership follows pivots. It must preserve the historical Crout
arithmetic sequence, finite guards, pivot ties, epsilon handling and every solver
step. No numerical cache, mutable graph reuse or proof reuse is introduced.

Predeclared focused gates: historical independent factor/pivot/solve oracle,
four native suites, actual final-source JDK8/GWT five-permutation build,
compiled A07 physics/ownership/cleanup/oracle checks and strict reader.

Fresh cold order: R7, B7, B13, R13, R64, B64. R is the row-list candidate and
B the frozen accepted reference with audited source/web inputs. Each run starts
a new browser process/profile and empty private proof cache. Stop after the
first pilot if it does not improve the fresh accepted seed-7 result of
106,447 ms; retain any failure. Compare exact requests, complete electrical
proof values, all five hypotheses, service/retest outcomes, work units and
cleanup. Retain warm rows but exclude them from cold claims. Record host
monotonic duration and wall-versus-monotonic drift separately from cleanup.

## Result

Correctness gates PASS: four native suites in 50.961 s, 281,121 A07 assertions;
actual final-source JDK8/GWT build with five permutations in 82.492 s;
compiled A07 in 5.165 s host monotonic time and the exact maintained reader
with three negative corruption canaries. The independent historical factor,
pivot and solve oracle remains unchanged. Fixtures add workspace reuse across
sizes/failures, underflow, pivot ties, overflow recovery and pivoting inversion.
The current caller ownership audit supports swapping inner row references;
arbitrary callers retaining inner row aliases are outside that claim.

Performance FAIL: first R7 pilot is 112,399 ms cold, 85,645 ms proof and
20,012 ms routing. A separate fresh B7 control is 107,655 / 80,702 / 20,338 ms.
The candidate is **4,744 ms slower**, with 4,943 ms more proof time. The batch
stopped at the predeclared first-pilot condition; B7 was then measured separately
to confirm attribution. B13/R13/R64/B64 are NOT RUN. The outer batch process
returned exit 1 despite the inner script's intended stop code 2; neither is PASS.
The two maintained measurement runners returned exit 0 and their strict readers
PASS, including 37 negative corruption canaries. These are correctness and
measurement passes, not a performance pass.

Exact requests, every diagnostic proof field except elapsed time, five
hypotheses, 185 observations, 390 proof units and 468 total job units match.
Each cold cache started empty and missed; private cache cleanup and normal-cache
isolation pass. Warm rows are retained and excluded from timing claims.
Host monotonic elapsed times are 142.519 and 137.852 s; host wall/monotonic
drift is below 0.005 ms. Owned browser/server cleanup passes in 0.988 and
1.017 s with no survivors. One pair is not a distribution estimate.

The exact rejected delta is retained in `rejected-pilot.patch.gz`, with source
hashes and reverse-apply validation. After preservation, root removed only its
two experimental source changes and verified that the patch applies to the
accepted source. Production source is restored to 7a30a3f. The current root WAR
still contains the rejected build and must be rebuilt before use. No changes
to settling, proof scope, routing, physical admission, seed interpretation,
candidate order, generation owners or deadlines were made by this experiment.

Reproduction uses the maintained `scripts/verify-current-contracts.ps1` with
`A07ExecutionContractTest,Q30TemporalWorkContractTest,`
`Q30GenerationMeasurementBudgetContractTest,Q30NormalExecutionPolicyContractTest`,
then `scripts/build.ps1 -JavaHome <JDK8> -Target Compile -Style OBF`.
Compiled runs use `tests/browser/compiled_attribute_acceptance.py` and
`docs/task-evidence/Q30/normal-admission/check_coordinator.py --seeds 7 --self-test`.
The request is the private Q30 coordinator seed 7; source/web digests, complete
receipts, process ownership and gate results are retained beside this file.

This bounded experiment is not normal Q30 acceptance. Frozen limits remain
90,000 ms cumulative, 640 shared units and 5,000 ms per active operation.
The private measurement allowance and cache stay isolated. Q30 remains disabled
and BLOCKED; scale remains 33/35/37. Full final matrices and later milestones
are NOT RUN. No push.
