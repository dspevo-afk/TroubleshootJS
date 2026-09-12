# A10 staged generation qualification

Status: ACCEPTED; required qualification complete. Publication is recorded in the
current project checkpoint and Git history.
Base: `66a62b7f49d53464b8ada4ef02abf598373aba82` on
`codex/task43p-final-recovery`. P02 is accepted and published at this base.

## Scope and acceptance

Implement staged generation over the existing CircuitJS and physical owners:
cheap immutable input checks, healthy solve, physical realization/access,
complete diagnostic hypothesis proof, symptom projection and atomic publication.
Candidate order and work ceilings are deterministic. Cancellation or failure
preserves the original owner. Receipts name every consumed dependency and never
reuse a mutable owner. Expected candidate rejection is distinct from unexpected
programming or infrastructure failure. A11 remains the next authorized task.

No new physics, larger-board router, imported design support, save UI, alternate
solver, parallel live contexts or player-facing diagnostic answer is in scope.

## Current final-candidate results

- Native: PASS, actual JDK8 runner exited 0; 23 suites, 190 generation, 26 routing
  and 12 dependency assertions; independent seed/value/role/diagnostic oracles,
  466 report assertions and scoped cleanup.
- Build: PASS, actual final JDK8/GWT OBF build, five permutations. All source,
  test and runner inputs remain identical to the frozen candidate; see
  [provenance](candidate-provenance.json).
- A10 compiled: PASS, 1,273 assertions, all six cancellation stages, 18 individual
  hypothesis cancellation boundaries, 12 preparation/cleanup/scope failure
  canaries, five dependency/publication rejections and production-session overlap
  checks. Fixed/adaptive serial-versus-batched CircuitJS state, events, counters,
  independent RC response and stopped-step canaries pass.
- Performance: all 24 frozen attempts and the maintained strict reader PASS.
  First-attempt p50/p95/worst: 817/3,094/3,094 ms; immediate-repeat:
  808/2,939/2,939 ms. Plan caching never replaces fresh electrical/diagnostic proof.
  [Full report](generation-report.json), [strict summary](performance-summary.json).
- RC: full seed-0 proof/publication 55,883 ms; repeated complete proof followed by
  the changed-reference rejection 48,243 ms. Maximum active units were 4,371 and
  3,726 ms. Worst cancellation-handler cleanup was 5 ms. RC admission remains
  slow on this desktop; these figures do not establish modest-hardware or larger
  board performance, or a bound on queued input delay.
- Final A07, A08, Task41 and Task49 compiled reports passed their maintained
  strict readers. A09 passed 97 assertions/11 negatives and its independent
  reader passed 144 assertions. P02 projection passed on NPN, RC and composed
  boards: 3,209 pure assertions each, 220/210/256 runtime assertions, all 16 SMD
  poses and real metadata-only-cut falsifiers. A10 forced failure and debug-off
  privacy passed. No production source, test, script or compiled artifact changed
  during these checks (1,055 input hashes and five compiled permutations).
- Ordinary visible input passed cancellation and LED diagnosis/repair/retest.
  The unrepaired customer retest failed, unpowered R1 measured OL, the installed
  replacement measured 330 Ohm, and the powered customer retest passed. The final
  player page had no error logs. [Interaction receipt](player-flow.json).
- Optional read-only review found no blocking source defect and confirmed the
  scenario-dependency repair. Its one concrete repaint-retention follow-up is
  recorded in [review evidence](independent-review.json).

## Player evidence and limits

The five inspected screenshots show [cancelled preparation](01-cancelled.png),
[unrepaired retest](02-unrepaired.png), [open resistance](03-open-resistance.png),
[measured replacement](04-replacement-measured.png) and
[successful customer retest](05-repaired.png). Cancellation from the initial RC
route restores its empty original simulator; no candidate board is published.

The first long-lived-tab Task41/A09 run crashed before yielding a report. One
fresh isolated retry passed both strict readers; the original failure remains
recorded in [the crash receipt](a09-tab-crash.json). The crash tab is retained
because Browser URL policy rejected its data: page inspection and closure.
Some attempted RC input commands exceeded Browser's three-second CDP command
deadline; the successful final button click is distinct evidence, without a
claim that every input was handled within 500 ms.

Preview port 64817 and active Browser tab 4 remain root-owned for the authorized
A11 continuation; exact process identity is in external task scratch. Routing
test scratch is retained because automatic approval review rejected guarded
deletion as blocked by policy. The historical isolated Windows browser wrapper
remains unqualified. These checks do not establish larger-board or modest-host
performance. Earlier superseded positive reports remain external historical
evidence and do not qualify this candidate.

## Closed validation set

| Gate | Production boundary, verifier and oracle | Fixtures / toolchain |
|---|---|---|
| Current native contracts | Actual maintained `scripts/verify-current-contracts.ps1`; existing seed/value/role/diagnostic oracles and report contracts, plus focused A10 tests | JDK 8u502, pinned GWT jars, Python 3.13; scoped OS-temp scratch |
| Stages and receipt negatives | Integrated generation coordinator; independent expected stage order, rejection classification, dependency changes, cancellation and stale publication checks | Same manifests and signed-long boundary seeds; unexpected exception, changed source/load/access, missing receipt and superseded job |
| Actual production build | `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Style OBF` | Final Java source; all five GWT permutations |
| Compiled integration | A10 positive/forced-negative/debug-off checks; affected A09, A08, P02 and current Task49 strict report checks | Current leaf and controlled composition; real CircuitJS and immutable copper projection |
| Temporal executor regression | Actual A07 solver verifier and strict reader; A10 fixed/adaptive RC comparison of one-step reference versus bounded batches | Exact final state, accepted event traces and wire currents; independent exponential response; existing deadline/owner/event negatives |
| Player workflow | Built-in Browser ordinary visible input; initial board, unrepaired retest, measurement, replacement and restored retest | LED seed 0; curated screenshots and input receipt; no injected controller calls |
| Cost and cancellation | Stage counters and cold/immediate-warm attempts; same loaded-app protocol as A01; deterministic work separate from elapsed time | Predeclared pilot and held-out manifests; current desktop host, measured browser viewport/DPR; no modest-hardware or Q-scale claim |
| Optional independent review | Root has chosen to continue the already dispatched Luna review because it covers concrete ownership risks; the owner's 2026-09-12 instruction makes separate agents optional | Real diff, call paths, acceptance/evidence and focused independent validation |
| Final reconciliation | Intended diff, whitespace, source/evidence fingerprints, owned-resource cleanup, normal commit/push and remote SHA verification | Preserve pre-existing Python cache; Gmail notification after verified publication |

## Preflight results

- P02 closeout audit: PASS, no drift in the reviewed candidate and consumed inputs.
- Actual selected JDK 8 runtime: PASS.
- Actual Python launch through `Invoke-VerifierBoundedProcess`: PASS, exit 0,
  exact output and termination proven. This is a new observation on this host;
  it does not rewrite historical wrapper failures.
- Complete maintained native baseline: PASS, exit 0, 20 Java suites, independent
  seed/value/role/diagnostic oracles and report protocol. Cleanup completed.
- Production preview: PASS, HTTP 200 and exact task-owned server identity.
- Built-in Browser: PASS, production LED board rendered; ordinary customer-retest
  click produced the expected unrepaired failure. This is preflight, not final
  A10 player qualification.

## Declared performance corpus and limits

Before compiled execution, the bounded current corpus is frozen as LED indicator,
NMOS low-side switch and controlled two-channel composition, seeds 0/1 for pilot
then 2/3 for holdout, each followed immediately by a fresh repeat (24 attempts).
The frozen 24-attempt benchmark acceptance remains **5,000 ms per complete
attempt**. The first implementation also applied that number as a universal
whole-job runtime cutoff, with an entire hypothesis counted as one unit. The
retained [RC failure](rc-budget-failure.json) exposed that incorrect unit: an RC
hypothesis includes several complete real temporal profiles and observations.
It is a failure of that candidate, not passing evidence for a larger threshold.

The current runtime uses a 90,000 ms cumulative **generation job** ceiling and
a 5,000 ms active operation ceiling. A01's 30,000 ms aggregate corpus ceiling is
a distinct measurement; the 24-attempt A10 report does not claim that aggregate
limit. CircuitJS's existing 500 ms accepted-
step and 5,000 ms temporal-operation limits remain unchanged. Proof execution
yields between individual observations and repair operations; 640 work units
bound the current four-hypothesis/two-channel program, including all 132 samples
per hypothesis. The router retains its independent 80-attempt ceiling. The
coordinator batches at most 16 cheap units or 8 ms before yielding; a synchronous
operation can extend the turn to its bounded completion. Timing changes only
scheduling, never candidate or hypothesis order. These revised runtime boundaries
received fresh native/compiled validation and optional focused review.

Cancellation handler-to-owner-restoration must remain within 500 ms, including
cancellation between private hypothesis operations. This is separate from queued
input delay during a synchronous operation. The report records maximum active
unit duration; no universal 500 ms input-response claim is made.

Report per-stage work/timing, first/repeat p50/p95/worst, cache provenance and all
failures. Plans are the only cached artifact; no electrical or diagnostic PASS is
reused. The first LED seed-0 plan may already be warm from lifecycle canaries;
its actual cache-hit flag is retained. Browser/application/OS caches are not flushed.
This host measures current bounded content, not a modest-hardware or Q-scale claim.

Diagnostic evidence: the earlier 24-case compiled run passed, and the dispatch
repair passed actual A09 (97 assertions/11 negatives plus strict reader 144) and
A08. Those reports precede finer operation slicing and cannot qualify the final
candidate. The final checks and focused review are recorded above.

The subsequent [RC active-unit failure](rc-work-unit-failure.json) measured 5,909 ms
for a unit containing both healthy and faulted profiles. The correction gives each
profile its own unit and separately dispatches the real retest completion callback.
No temporal samples, completion checks or existing solver ceilings are removed.
Cleanup canaries cover failed private disposal and failed outer staged cleanup,
retained ownership, rejection of a successor job and exact guarded retry.
The [cumulative RC failure](rc-cumulative-failure.json) then reached 31,108 ms at
hypothesis work unit 26. The executor correction groups up to 128 accepted steps
without changing the first accepted target time, model, timestep, source checks,
trial/accepted-step deadlines, event delivery or wire-current refresh order. The
one-step executor remains the independent comparison path. The actual A07 gate
and fixed/adaptive RC equivalence checks are required for this affected boundary.

The [batched](rc-batch-cumulative-failure.json) and
[executor](rc-executor-cumulative-failure.json) pilots still exceeded the 30-second
job ceiling: 30,735 ms at work 40 and 30,629 ms at work 30. The workload audit found
52 canonical RC units including 14 real temporal sequences: two initial profiles,
then healthy/faulted profiles, observations, repair status, customer retest and its
completion status for each of two hypotheses. These sequences remain required.
The root and a read-only budget reviewer explicitly rerated the whole-job guard
to **90,000 ms before fresh qualification**. A10 specifies no universal 30-second
job limit; A01's 30-second aggregate and this frozen corpus's five-second complete
attempt limit are unchanged. The old failures stay failures. The higher guard is
not a latency target or proof of fast RC admission; final measured RC first/repeat
costs, maximum active unit, cancellation and any misses must be reported.
