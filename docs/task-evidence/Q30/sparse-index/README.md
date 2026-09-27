# Q30 sparse-index LU candidate — pilot PERFORMANCE FAIL

The first compiled seed-7 pilot takes **116,310 ms** cold, with 89,419 ms proof
and 19,917 ms routing. The accepted reference previously took 106,942 ms cold,
with 80,262 ms proof and 20,262 ms routing. The predeclared pilot stop condition
therefore fired; the remaining five comparison runs are NOT RUN. A separate
fresh accepted-build control confirms **106,447 ms** cold, 80,029 ms proof and
20,071 ms routing, with exact request/proof parity and successful cleanup.
The indexed pilot is 9,863 ms slower than this fresh control. The isolated
index-overhead profile is complete, with the limits recorded below.
This candidate is **NOT ACCEPTED** and has been removed from production source.
Its exact patch and isolated source/build fixture are retained for the cost
profile. The root compiled WAR remains from this experiment until the next
production build; it is not used as accepted evidence. Q30 remains BLOCKED.

Correctness checks pass: four native suites (49.581 s including cleanup),
actual five-permutation JDK8/GWT build (80.327 s), and compiled A07 with the
maintained strict reader plus three corruption canaries. Native A07 reports
283,589 assertions; browser A07 reports 283,588 pure and 73 runtime assertions.
Independent read-only review found no correctness blocker. The retained dense
historical oracle covers exact numeric factors, pivot choices and solutions;
signed-zero bit identity is not claimed. The private pilot passes the strict
Q30 reader and all 37 corruption canaries; exact request, all electrical/proof
fields except elapsed time, and all 468 job / 390 proof units match accepted B7.
Browser/server cleanup passes with no owned survivors. Passing correctness
does not make the failed performance experiment an accepted optimization.

See [pilot analysis](pilot-analysis.json), [complete receipt](pilot-receipt.json.gz),
[strict reader](pilot-strict-reader.txt), [runner/cleanup](pilot-runner.json), and
[rejected pilot source patch](rejected-pilot.patch.gz). Native, build, compiled
A07 and source/web input receipts are retained alongside these files.

Base: `7a30a3fdcfa1dc9092d546edcb01bfa9ed435a36`. The stage profile identifies
factorization as the largest sampled kernel cost and 82.55% zero row probes.
The bounded candidate indexes nonzero lower-column rows while preserving
Crout subtraction order, pivot ties, finite checks and all solver/proof work.
Only integer scratch is reused; masks are reset/rebuilt for every factor call.
Q30 remains disabled and BLOCKED, with frozen 90,000/640/5,000 limits.

Before timing: independent historical factor/pivot/solve oracle expanded for
32-bit boundaries, pivot swaps, workspace reuse, underflow, invalid input and
failure recovery; four focused native suites; actual final-source JDK8/GWT
five-permutation build; compiled A07 ownership/physics/oracle checks.

Predeclared small cold comparison: C7, B7, B13, C13, C64, B64. C is the new
candidate, B the accepted zero-factor optimization. Each is a fresh maintained
runner/browser/profile with empty private proof cache. The first C7 pilot must
improve the accepted 106,942-ms result before the comparison continues; otherwise
retain the failure and investigate. B uses an isolated reference whose 1,524
source/web files exactly match the accepted build manifest. No source toggles.
Compare complete requests, all five diagnostic/service/retest hypotheses,
electrical samples, canonical proofs, units and cleanup. Warm rows are retained
but excluded from cold claims. No final matrix while any hard gate fails.

This is an optimization experiment, not normal production admission or a
population-tail estimate. Scale remains 33/35/37 until purposeful family work
is implemented and qualified. No push or later milestone is authorized.

## Profile of the rejected index

The isolated five-permutation build passes in 82.610 s. Fresh seed 7 takes
118,129.6 ms monotonic (legacy field 118,123 ms), with 90,941 ms proof and
20,109 ms routing. The uninstrumented pilot was 116,310 ms: an apparent 1.56%
increase, including run noise and profiler effects. Use this profile for coarse
cost localization, not an unbiased latency estimate. Exact request, proof
values, 468 job units and 390 proof units match; the strict reader and 37
corruption canaries pass. All stage scopes balance. Process/server cleanup
passes in 0.873 s with no owned survivors.

The 1-in-1,024 factor sample includes 712 factorizations. The following are
sampled milliseconds, not extrapolated whole-job seconds:

| LU phase | Sampled ms | Share of listed phases |
| --- | ---: | ---: |
| Mask reset | 0.1 | 0.1% |
| Finite input scan | 7.3 | 7.8% |
| Crout update traversal | 30.6 | 32.8% |
| Pivot search, row swap and prior-mask repair | 37.4 | 40.0% |
| Zero-pivot handling, scaling and index construction | 18.0 | 19.3% |

These compound phases do not isolate mask repair from ordinary pivot work.
They justify testing per-row lower-column lists: keep ascending arithmetic
order, append finalized nonzero factors, and move each scratch list with its
pivoted numerical row. That removes prior-column mask repair and bit decoding.
It is a new experiment, not a promised speedup.

The same cadence captures an input and compares every entry of the immediately
next factor input, including signed-zero signs. Only **18 of 712 pairs (2.53%)**
match exactly. Capturing/comparing samples costs 14.9 ms. This does not justify
a last-matrix factor cache as the next optimization. No cache was implemented.
Pending captures are invalidated on analysis/new graphs and temporal-scope exit.

Known instrumentation limit: `luPhaseClockReads` overcounts by one per phase
end because both the end wrapper and elapsed helper increment it. The raw field
is retained and is **not used as an exact read count**. Actual performance.now
subtraction, phase durations, input comparisons, solver work and proof values
are unaffected. Small phase values are limited by browser timer resolution.

See [profile analysis](index-profile-analysis.json), [raw timing values](index-profile-values.json),
[full report](index-profile-receipt.json.gz), [strict reader](index-profile-strict-reader.txt),
[build](index-profile-build.json), [runner/cleanup](index-profile-runner.json),
[source provenance](index-profile-preparation.json) and [isolated patch](index-profile.patch.gz).
