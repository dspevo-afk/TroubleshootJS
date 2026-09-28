# Q30 immutable routing geometry trial — REJECTED

Accepted production source remains `0b29680c7daa62123732b91e3c1157e8ba0d6abd`.
The trial passed focused correctness but did not establish a useful, repeatable
runtime benefit. Its two production/test changes were removed. Q30 remains
BLOCKED, normal publication disabled, and the final acceptance matrices NOT RUN.

The candidate replaced Search's retained component/pad object arrays with
Rectangle snapshots of their immutable courtyard/pad bounds. It avoided repeated
defensive rectangle copies in the measured `viaFits` predicate. Search owns the
snapshots; live holes, both face occupancies, rules, predicate order, queue order,
work limits, cancellation and full final geometry validation stayed unchanged.
No solver, settling, hypothesis, service, repair, retest, completion or proof-cache
code changed. The exact rejected patch and forward/reverse checks are retained.

Root reviewed the actual diff. An independent Luna/MAX read-only review inspected
the two changed files and placement/layout/router owners and found no correctness
blocker. This was a source review, not execution evidence.

| Gate | Result |
| --- | --- |
| Maintained native P07, routing resumption, private measurement budget and normal execution policy | PASS, 44.000 s including compile/tests and maintained cleanup; 1,665 P07 and 2,203 resumption assertions |
| Actual final-candidate `scripts/build.ps1`, JDK8, OBF, five GWT permutations | PASS, 89.852 s host elapsed; compiler 86.089 s, link 1.462 s |
| Eight fresh private cold comparisons | PASS for complete proof, request, physical-owner fields, work and cleanup; not normal deadline acceptance |
| Strict coordinator reader and corruption canaries | PASS for all eight reports; 37 negative canaries on the first |
| Input audits / resources | PASS, stable source/web digests within each arm; all owned browser/server cleanup PASS, no survivors |

The P07 oracle retains the historical getter-driven predicate and compares every
fixture grid coordinate, including accepted/rejected positions. Separate changes
to a live nearby hole and opposing-net occupancy still reject a via. Existing
route determinism, expansion counts, physical validation and cancellation tests
remain. Numerical behavior is independently checked by full compiled Q30 proof
parity, including exact proof context and evidence, excluding only elapsed time.
Each run retains five hypotheses, 185 observations and 390 proof work units.
Job work is 468/464/446 for seeds 7/13/64 in both arms.

The first six runs were predeclared R7/B7/B13/R13/R64/B64. Because the effect was
small and proof timing varied, B7/R7 was declared and then run as a reverse-order
repeat. Every row launched a fresh owned Edge profile and server. R is the
candidate; B is the frozen accepted source/build. Each private run also executes
its separately identified warm proof-cache check; warm timing is not cold evidence.

| Pair | B cold ms | R cold ms | Cold saved ms | B route ms | R route ms | Route saved ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 7, first | 99,193 | 98,042 | 1,151 | 23,615 | 22,780 | 835 |
| 13 | 100,221 | 95,775 | 4,446 | 22,672 | 21,188 | 1,484 |
| 64 | 85,079 | 84,456 | 623 | 13,145 | 13,040 | 105 |
| 7, reverse repeat | 89,060 | 89,252 | -192 | 21,096 | 20,983 | 113 |

Median paired cold savings are 887 ms, route savings 474 ms. The reverse repeat
does not improve total cold time. Unchanged seed-7 control varies by 10,133 ms;
candidate varies by 8,790 ms. Seed-13 proof alone differs by 3,074 ms, despite no
solver change. Thus the largest total savings cannot be attributed to cached
rectangles. No cause for the host/execution-state variation is established.
The small route-only difference in the repeat is insufficient to retain this
candidate as a useful optimization.

Host elapsed/cleanup use monotonic clocks; maximum wall-minus-monotonic drift is
0.0052 ms. Application cold/proof/route fields are the existing verifier clock
values, not relabeled high-resolution timings. Host operation and cleanup are
separate in the runner receipts; native aggregate timing includes its cleanup.
The preceding dedicated monotonic stage profile remains the stage-cost evidence.

Commands: maintained `verify-current-contracts.ps1` with the four named suites;
`scripts/build.ps1 -Target Compile -Style OBF` with verified JDK8; maintained
`tests/browser/compiled_attribute_acceptance.py` against exact private Q30 seed
queries; `normal-admission/check_coordinator.py` per receipt, first with
`--self-test`; `git apply --check` in both directions; final source restoration
against the accepted frozen reference. No deadline, threshold, corpus or oracle
was relaxed. The 90,000/640/5,000 production contract and private 300,000-ms ceiling
remain separate and unchanged.

`comparison.json`, compressed full receipts/input manifests, runner receipts,
strict-reader output, native/build logs and `disposition.json` retain the evidence.
Root WAR contains the rejected candidate and must be rebuilt before use. The
frozen accepted build remains available for the next diagnostic CPU profile.
