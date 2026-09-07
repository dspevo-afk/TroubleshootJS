# A01 — reference manifests and reproducible measurement harness

Status: IMPLEMENTED — ACCEPTED after fresh independent Luna delta review. Base: `55cac1d24528a26505f540ee52f85631e0add4d2`, branch `codex/task43p-final-recovery`.

## Frozen scope and ownership

The owner authorized A01 after the verified N00 push and authorized this desktop
as the reference host by explicit exception. It is an Alienware Aurora R15 AMD,
Ryzen 7 7700 (8 cores/16 threads), 34,081,759,232 bytes installed RAM, RTX 3070,
Windows 11 Home build 26200, Balanced power plan. It is not called a modest PC.

Three read-only investigations returned before implementation. Astra owns
integration, scripts, checks, evidence and publication. One Luna MAX worker owns
`A01MeasurementVerifier.java` and the bounded CirSim hooks; a second owns the
architecture-only reference inventory. One fresh read-only Luna MAX review of
the integrated candidate is required. No browser or build is owned by workers.

The explicit desktop exception is fully identified below and carries no
modest-machine portability claim; a modest-host run remains future evidence.
RB15/RB30/RB56/RB100 are architecture manifests, not implemented boards. Separate
20/40/60/100-resistor series networks exercise actual CircuitJS with independent
Ohm-law/KVL expectations; every resistor participates in the divider. They are
synthetic solver/structural pilots, not routed PCB or playable qualifications.
Current small generated boards provide real package/pad/net/trace baselines.

The developer route is opt-in with debug enabled. It records actual owner-side
analysis/stamping/solve/step work, restores the original graph/state after success
or forced failure, and publishes a versioned DOM JSON receipt. Timings never
select candidates. Unavailable stages/counters are explicit, never zero-as-pass.
The runner reads that receipt through the existing Browser API and the local
checker verifies frozen corpus completeness, electrical expectations, identity,
measurement traces and outcome/budget accounting.

## Closed acceptance matrix

| Gate | Production path / verifier / oracle | Required result |
| --- | --- | --- |
| Architecture inventory | Four JSON manifests; unique purposeful roles and exact roadmap allocations; independent count/invalid inventory checks | 15/30/56/100 real role slots, architecture-stage only |
| Small baselines | Existing generators/assembler, board/layout owners; explicit seeded repeated cases | Counts and identities repeat; unsupported metrics labelled |
| Scale pilot | Actual CircuitJS linear network, sizes 20/40/60/100, pilot seeds 0/1 then held-out 2/3; 32 accepted steps per corpus (64 combined) | Analytic current/node voltages, matrix dimensions, work counts and trace provenance |
| Repeat protocol | Cold means first fixture construction/analysis; warm means immediate repeated construction in the same loaded application (not cleared OS/network/JIT caches) | Same deterministic identity and counters; both attempt records retained |
| Fail closed and lifecycle | Forced failure after temporary graph installation; debug-off route suppression; report/oracle negative tests | FAIL never PASS; original graph and counters restored; no public report without debug |
| Budget process | Pilot first, then freeze numeric stage/memory/throughput/cancellation limits before held-out corpus | All outcomes retained; misses explicit; browser viewport/DPR, p50/p95/worst timing, memory availability and bounded cancellation recorded; no retrospective threshold tuning |
| Source/build | Final JDK8/GWT2.7 five-permutation OBF build; focused contract regressions | PASS on final source; no A02 changes |
| Review/publication | Fresh independent Luna MAX source/evidence review, targeted deltas as needed, final/staged diff checks, normal push and email | Exact candidate and resource outcomes recorded |

## Non-scope

No A02 predicates, bends or coordinate corrections; no new scalable physical
router, topology/provider engine, general solver replacement, scope/instrument,
player flow, or Q15/Q30/Q60/Q100 qualification. Browser benchmark evidence is
separate from visible normal-player acceptance. Future release budgets and modest
hardware portability remain qualified only to the actual declared evidence.

## Final candidate evidence

The final source candidate fingerprints and build identity are regenerated after
each source/build change by the A01 identity command; the checker requires both
receipt values to match that current identity before accepting a corpus.
The pinned JDK 8/GWT 2.7 OBF build linked all five permutations. The independent
checker accepted the four architecture manifests and both 16-attempt corpora;
the live negative routes retained `FAIL:a01` and suppressed the report with
debug disabled. Compact evidence is in `*-summary.json`; the full receipts are
retained in the task-owned OS-temp run referenced by the task report.

The first integrated Luna review identified four evidence/contract gaps. The
implementation repaired them, and the required fresh read-only Luna delta
review passed against this candidate. Two nonblocking follow-ups remain: the
checker accepts a single corpus when only one receipt is supplied, and the
forced-canary attempt record labels cleanup `PASS` without an injected
cleanup-failure canary. Current pilot/holdout evidence and the observed forced
failure cleanup pass; those hardening items are outside this milestone.
