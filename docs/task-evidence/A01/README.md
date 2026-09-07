# A01 — reference manifests and reproducible measurement harness

Status: IMPLEMENTED — CORRECTIVE PASS ACCEPTED after fresh independent final review. Base: `3b4fd13db927a673a1d106bdb1ac7d19af9dea12`; implementation commit: `fdabfea1ab6ea0324ba39d76b6ad4173e9bea869`; branch `codex/task43p-final-recovery`.

## Lineage and corrective scope

The original A01 implementation was published in `4a9f4a2f479fc077ba1b6944ebb708cb8406fd12`; its documentation-only successor was
`3b4fd13db927a673a1d106bdb1ac7d19af9dea12`. Independent review then found
three qualification-integrity defects: collection exceptions could retain a
passing wrapper, declared timing limits were not independently enforced, and
URL-supplied source/build labels were not bound to the served artifact. This
bounded pass repairs those defects and adds focused regression coverage. The
CircuitJS solver, A01 fixture families, desktop exception, frozen budgets, and
all A02 scope remain unchanged.

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

## Corrective-pass evidence

The corrected source/build identity is recorded in
[`corrected-identity-summary.json`](corrected-identity-summary.json). It reports
five compiled OBF permutations and the execution provenance stamped into the
served page. The fresh pilot and holdout receipts are summarized in
[`corrected-pilot-summary.json`](corrected-pilot-summary.json) and
[`corrected-holdout-summary.json`](corrected-holdout-summary.json); each has 16
attempts and 32 accepted steps. The independent checker accepted the pair with
32 attempts and 64 accepted steps; see
[`corrected-check-summary.json`](corrected-check-summary.json).

The forced-failure route retained a diagnostic `FAIL:a01` report, restored the
owner, and closed its tab; the checker rejected it as a qualification corpus.
[`corrected-negative-summary.json`](corrected-negative-summary.json) records
that result. The debug-off route retained no report or terminal result while
still closing its tab; see [`corrected-debug-off-summary.json`](corrected-debug-off-summary.json).

The collector now preserves terminal, report, cleanup, and error fields while
forcing any wrapper exception to `INFRASTRUCTURE_FAILURE`; the independent
checker rejects wrapper/report contradictions, cleanup failures, recorded
errors, timing-budget violations, trace intervals outside attempts, and served
artifact mismatches. Focused cases 1–10 are covered by
`tests/contracts/a01_qualification_integrity.py` and
`tests/contracts/a01_collection_integrity.mjs`.

The Browser tabs were empty after collection. The supported preview stop wrapper
failed closed because the launch shell's parent had exited. The exact recorded
preview PID/start identity, command line, owned script and port were revalidated
before termination through the existing verifier isolation module; termination,
listener absence, endpoint unreachability, and state removal all passed. The
result is recorded in [`corrected-cleanup-summary.json`](corrected-cleanup-summary.json).

The original `*-summary.json` files and earlier raw receipts remain historical
diagnostic evidence and are not relabeled as proof of this corrected behavior.

The fresh independent final review returned PASS with no blockers; its scope and
one nonblocking single-corpus follow-up are recorded in
[`corrected-review-summary.json`](corrected-review-summary.json).

## Remaining limits

This pass does not add A02 predicates, bend counts, coordinate corrections,
large-board qualification, or modest-host portability evidence. The checker
continues to accept a single corpus when invoked with one receipt, and the
forced-canary attempt does not inject a cleanup-failure route; both are recorded
future hardening items outside this bounded correction.
