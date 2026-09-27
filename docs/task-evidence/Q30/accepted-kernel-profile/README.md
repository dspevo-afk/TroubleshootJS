# Accepted Q30 kernel profile and fresh control — Q30 BLOCKED

Baseline production is `0b29680c7daa62123732b91e3c1157e8ba0d6abd`.
All instrumentation is confined to the temporary fixture; its complete 16-file
patch has passing forward/reverse application checks. Actual JDK8/GWT5 build
PASS in 90.009 s. The compiled seed-7 run, strict reader and 37 corruption
canaries PASS, with exact request/physical/electrical proof context and work
against the accepted control. No normal admission is claimed.

Cold application time is **96,358.7 ms monotonic** (96,352 ms legacy), with
68,124 ms proof and 22,419 ms active routing. All 616,000 accepted solver steps,
1,345,510 trials, 729,510 factors/solves, five hypotheses, 185 observations,
390 proof units and 468 job units remain. All stage scopes and sampled timer
pairs balance. Each hypothesis still gets a fresh graph; routing failures and
candidate order remain. There is one exact generation candidate and no retry;
the retained P05/P07 search work is unchanged from the earlier stage profile.

## Exclusive stage partition

| Work | Milliseconds |
| --- | ---: |
| Plan resolution | 0.2 |
| Placement | 857.1 |
| Routing, including final route validators | 22,541.7 |
| Physical admission | 90.7 |
| Initial and five hypothesis constructions | 471.4 |
| CircuitJS analysis/setup | 1,445.3 |
| Solver stepping | 66,219.4 |
| Settlement orchestration | 1,480.7 |
| Hypothesis/proof orchestration | 774.8 |
| Proof cleanup | 93.6 |
| Other stage work | 72.4 |
| Unscoped/event-loop/clock-boundary residual | 2,311.4 |

The residual is not all idle time. Inclusive proof purposes overlap those
solver stages: healthy settling 16,339.6 ms; candidate settling 3,845.6;
input sweep 12,146.6; DC observations 1,156.0; power-off settling 3,734.5;
remove/replace/power-on 777.8; repair status 13,816.1; customer retest 13,449.0;
completion 35.3. `stage-partition.json` retains every purpose, including replay,
wait/power/settle and restoration. Verifier cleanup is separately 3.5 ms;
maintained browser/server cleanup is 1.295 s with no survivors.

## Remaining contributors

One in 64 accepted steps samples kernel work. Factorization is about 47% of
sampled kernel time, followed by voltage publication, substitution and wire
current updates. Only one in 1,024 factors enters a separately instrumented
duplicate; other calls use the exact accepted kernel and instance workspace.
Across 712 selected factors, sampled phase times are: Crout 21.6 ms, pivot
search 16.1, scale/append 14.5, full finite-input scan 7.0, reference pivot swap
5.4, workspace reset/cleanup 3.0. There are 201,134 balanced timer pairs.
These short timings include aggregation/JIT/quantization effects; they rank
work but must not be multiplied into claimed full-run savings. A zero-valued
32-pair clock calibration does not prove zero overhead.

P07 A* takes 17,543.9 ms; lower-bound BFS 1,106.8; goals 439.7. Sampling every
128 queue polls/eligible expansions gives queue pop 25.3 ms/12,969 calls,
neighbor predicates 45.9/44,795, path-via checks 17.2/9,757 and via geometry
50.9/7,205. These samples overlap A* and have differing conditional populations;
they are excluded from the exclusive partition and are not exact percentages.
They support examining repeated immutable-bound copies in via geometry, while
the largest remaining LU phases are independently reviewed. Live holes,
occupancy, every predicate and all solver settling remain mandatory.

## Variation and limits

The previous uninstrumented seed-7 control was 86,458 ms. Because this profile
was roughly ten seconds slower, a fresh uninstrumented control was required.
It also slowed: **97,498 ms cold**, 68,430 proof, 23,294 routing, 27,793 warm.
Its strict reader, exact proof/work, unchanged source/web inputs, private-cache
isolation and cleanup PASS. Browser/server cleanup is 0.901 s with no survivors.
Host monotonic totals are 130.775 s profiled and 128.689 s control, including
warm work and report capture; wall/monotonic drift is below 0.005 ms for both.
A read-only host load snapshot during the control showed no obvious aggregate
CPU saturation; it does not diagnose the cause of the variation.

The immediate control does not show a material aggregate profiling penalty,
but this is not an isolated estimate of instrumentation overhead. The roughly
11-second movement in unchanged source is retained, not discarded. Earlier
paired optimization savings remain evidence for that delta, while their small
deadline margins cannot establish production acceptance. No population p95 or
normal cold-corpus PASS is claimed. Full acceptance is NOT RUN while timing and
scale fail. Q30 stays disabled and BLOCKED, with unchanged 90,000/640/5,000
limits, private measurement isolation and exact replay. No push.
