# Q30 intermediate solver optimization — accepted intermediate checkpoint

Base `745d537de5b18bd7ec2ae769fc00d3696e5af6bd`. This checkpoint evaluates only
the finite-zero-row-factor branch in `CirSim.lu_factor` and the
expanded independent oracle. Q30 itself remains BLOCKED and disabled for
normal players. Frozen budgets, all proof/settlement/service requirements and
the full future qualification corpus are unchanged.

## Predeclared fresh comparison

Eight isolated cold/warm private measurements, in this exact order:
**B13, A13, A7, B7, B64, A64, A13, B13**. A is the control without the new
zero-row-factor branch; B is the current optimization. Each case starts a
new maintained runner, browser process and empty browser profile. The cold
proof cache must start empty and miss; warm rows are retained for audit but
do not count as cold performance evidence. Seed 13 brackets the other pairs
in reversed order to expose run-order drift. Three unique seeds cover the
existing 37/33/35-package variants; this is an attribution experiment, not a
reduced acceptance corpus or a population tail estimate.

The isolated OS-temp control copies current tracked source/static web/build
scripts and verified compiler dependencies, excluding Git, caches, historical
evidence and prebuilt output. Only the reviewed runtime branch is removed;
the extended oracle is identical. The control must build through the maintained
JDK8/GWT script. The optimized compiled candidate is audited against its prior
final-source manifest. No repository production source is toggled for comparison.

Record exact request, canonical proof identity, all observation and repair/retest
evidence, units, cache counters and cleanup. Compare paired cold total and proof
time; also retain the host runner's monotonic complete cold-plus-warm duration
as an independent check. Existing application timing fields use its current
clock; the subsequent pipeline profile will use a monotonic browser clock.
Do not report a successful production deadline, p95 or acceptance from this
small private experiment.

Independent source review and the smallest sufficient fresh native regressions
must pass before this is accepted and committed as an intermediate optimization.
No push is authorized. The full final acceptance matrix remains NOT RUN.

## Result and attribution

**Intermediate optimization ACCEPTED; Q30 BLOCKED / NOT ACCEPTED.**
The fresh independent review found no correctness blocker. Four focused native
suites PASS in 48.747 seconds including maintained cleanup; A07 retains 93,541
assertions against the historical Crout implementation. The actual control
JDK8/GWT build passes all five permutations in 84.403 seconds. The optimized
build is reused only after auditing all 1,524 source/web inputs unchanged.
No production file changed during the experiment.

| Paired seed | Control cold ms | Optimized cold ms | Saved ms |
| --- | ---: | ---: | ---: |
| 13, optimized first | 129015 | 114309 | 14706 |
| 7, control first | 120113 | 106942 | 13171 |
| 64, optimized first | 114442 | 103287 | 11155 |
| 13, control first | 126578 | 114013 | 12565 |

All pairs improve. Median paired saving is 12,868 ms. Proof savings account for
10,300–13,960 ms of each improvement. Repeated seed-13 cold ranges are 2,437 ms
for control and 296 ms for optimized, below the smallest measured paired gain.
Fresh browser processes/profiles, zero initial private proof cache, cold misses
and counterbalanced order exclude warm proof reuse and expose order drift.
The source comparison finds exactly one runtime delta. This supports attribution
to the branch on this host; eight runs do not establish population variance or
a production tail. Host monotonic total durations improve in every pair too;
wall-minus-monotonic drift is below 0.003 ms. Application cold fields retain
their existing clock and are not represented as new monotonic stage measurements.

The strict reader passes every row and all 37 corruption canaries. Exact
request, every diagnostic proof field except elapsed time, electrical sample,
canonical partition, repair/retest result and shared work count agree within
each pair. Five hypotheses, 185 observations and 390 proof units remain.
Native/compiled cancellation, isolation and cleanup checks pass. IEEE signed-zero
bit preservation is not claimed; numeric factor/pivot/solution equality is.
No threshold, settlement, hypothesis, routing/admission or cleanup requirement
was changed. No mutable graph reuse or new proof cache was introduced.

All optimized cold totals remain above 90,000 ms. Only 33/35/37 packages are
implemented; 20–32, 34, 36 and 38–40 remain unsupported. Normal-player Q30
publication stays disabled. Full final matrices are NOT RUN. Next work is a
monotonic stage profile and measured optimization, then purposeful scale work.
No push or completion email is authorized for this checkpoint.

## Evidence and commands

- [Independent review and six-question audit](independent-review.md).
- [Paired timings and exact parity](comparison.json), [complete receipts](receipts.json.gz)
  and [strict reader outputs](strict-readers.txt).
- [Native result](native-result.json) and [receipt](native-receipt.txt).
- [Control build](control-build.json), [source comparison](source-comparison.json)
  and [isolated control preparation](control-preparation.json).
- `runner-*.json` records each maintained runner's input audit, owned process
  identity and successful server/browser cleanup; no owned survivor remains.
- `compare_cold.py` and `collect_ab.py` preserve the orchestration with repository
  and task-temp paths supplied as arguments, not personal paths.

Commands used: `scripts/build.ps1 -JavaHome <jdk8> -Target Compile -Style OBF`;
`scripts/verify-current-contracts.ps1 -JavaHome <jdk8> -Suite
A07ExecutionContractTest,Q30TemporalWorkContractTest,Q30GenerationMeasurementBudgetContractTest,Q30NormalExecutionPolicyContractTest`
(with an actual PowerShell array); the maintained
`tests/browser/compiled_attribute_acceptance.py <arm-root> <output> <spec>`;
and `normal-admission/check_coordinator.py <receipt> --seeds <seed>`, with
`--self-test` on the first row. Host elapsed time uses Stopwatch/time.monotonic.
The eight process wrappers have exited. Task-owned inactive scratch is retained
for continued profiling; earlier policy-blocked cleanup remains separately
documented in acceptance-continuation/resource-cleanup.json.
