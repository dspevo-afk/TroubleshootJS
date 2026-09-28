# Current LU sampler root review

Purpose: measure exact adjacent matrix equality without numerical reuse. The
candidate is based on local HEAD 86aa58bc plus the unaccepted plan-4 working source
and accepted routing. No sampler source is integrated into the root worktree.

The first sampler draft incorrectly compared widely spaced captures and used a
wall clock; both were corrected before gates. A separate coordinator-advance
window excludes successor and ordinary UI solver work. Current sampling starts
at every 1,021st scoped nonlinear factor call and compares the immediately next
call, with generation/revision/analysis/dimension guards and abort on exit.
It invokes the existing factor routine exactly once and never reuses factors,
matrices, graph state or proof. Query-off allocation/clock/counter work is absent.
The remaining null checks and code generation can still affect both timing arms.

Native r1 failed on the old temporal fixture's five-unit expectation for a current
one-channel seed. R2 failed on its final retest advance expectation. Fixed seeds
13 and 7 independently assert one/two-channel topology, literal input schedules,
unit counts, 30-ms sample size and lifecycle behavior. A one-channel retest ends
at its prior HIGH input, so the final restoration cursor has no additional
advance; both LOW/HIGH observations still settle. R3 native5 passed. The first
GWT build rejected System.nanoTime in GWT 2.7's System emulation. The corrected
browser still uses only performance.now; native sampler timing is explicitly
unavailable rather than reported as a wall or monotonic measurement. R4 native6
and GWT5-r2 passed. All failures and their source snapshots remain retained.

The logical matrix census performs only one real step per seed, not settling,
proof or admission. Root review corrected its row/column identity conflation:
simplifyMatrix maps equations and unknowns independently, so the probe uses a
bipartite support graph. Its separate LU copy never mutates the live matrix.
Per-seed cleanup disconnects sources, deletes only owned elements, verifies
detachment and restores prior static simulator/localization state. Native PASS
does not make its logical matrix dimensions representative of installed proofs.

Compiled query-off A07, exact maintained report reader plus three corruptions,
disabled-normal canary, host/input audit and owned cleanup passed. The two fresh
seed-7 runs use the same instrumented build and separate browser processes.
Profile cold/proof/route = 83.571/63.418/14.565 s; off = 82.683/62.407/14.774 s.
Host monotonic totals are 107.0518755 and 106.1069080 s, with wall-minus-monotonic
drift about 4 microseconds each. The legacy application phase times remain wall
times. Strict pair validation has its own receipt; this note does not replace it.

Cold sampling sees 745,131 factors, 729 completed adjacent comparisons and only
14 exact matches; warm sees 43,112 factors and 42 comparisons with no match.
Zero aborted pairs/clock errors were reported. The 21.6-ms cold and 1.0-ms warm
brackets cover sampled copy/compare only, not per-call checks or compilation.
One pair cannot separate the +0.888-s cold difference from host variation.
The observed 1.92% exact-match rate does not justify a full-matrix factor cache.

No production deadline, settling interval, hypothesis, physical or electrical
admission, service/retest semantics, cache isolation or cleanup requirement was
changed. Q30 remains BLOCKED and normal publication disabled. No final matrix,
push or completion email was performed.
