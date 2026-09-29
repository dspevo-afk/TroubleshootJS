# Q30 support-cost isolation — BLOCKED / NOT ACCEPTED

2026-09-28. Evidence-only continuation from
`838886360b63df5640841448c8f24caa61b37b60` on
`codex/q30-multirail-qualification`. No production optimization is integrated.
The family-owned generation capability and generic layout repair remain accepted;
they were not reopened. No scale expansion, push, email, U06, U07 or Q60.

Normal Q30 remains disabled. The production contract remains 90,000 ms cumulative,
640 shared work units and 5,000 ms per active operation. This single private
300,000 ms measurement fixture cannot qualify the normal-player corpus.

## Result

All 14 private runs preserve the complete non-timing proof and cleanup; 14/14 cold rows exceed 90 s. Fresh unchanged controls are 104.176/106.454 s, and diagnostic tracker-off controls span 104.878–110.717 s. The prior tight baseline does not characterize this session. **Support-maintenance time remains INCONCLUSIVE; Q30 remains BLOCKED.**

| Comparison (candidate minus reference) | First pair ms | Reverse pair ms | OFF range ms | Decision threshold ms | Both material |
| --- | ---: | ---: | ---: | ---: | --- |
| Baseline profiler ON−OFF | +12840 | +1183 | 3593 | 10779 | NO |
| Tracker maintenance TRACK−OFF | -5129 | +2962 | 5839 | 17517 | NO |
| Tracker profiler ON−OFF | +22524 | +10379 | 9111 | 27333 | NO |
| Restricted prototype−OFF | +4681 | +763 | 5839 | 17517 | NO |
| Diagnostic OFF−unchanged control | +702 | +4263 | 2278 | 6834 | NO |

The structural-maintenance pairs do not establish a stable signed effect. Profile-on rows were slower in both pairs of each profiling comparison, but host spread precludes a precise causal overhead estimate or correction. The experiment does not prove tracking is either a minor or dominant cost. All pairwise proof/routing/warm differences are retained in `analysis.json`; no row was discarded.


There is no newly accepted optimization. Structural tracking is already absent
from accepted production code; its incremental cost remains distinct from the
accepted numeric LU scratch used in every arm. Neither high no-op counts nor a
single fast row justify adopting or polishing the existing structural prototype.
No safe removable share of factorization time is proved by these measurements.

## Frozen experiment and actual rows

`plan.json` was frozen before the diagnostic sequence. The control is the exact
prior 1,340-input source fixture: private seed **10014, plan 4, 40 packages**.
It uses the existing isolated scale fixture; no board-size expansion occurred.
It is not the older seed-7 stage profile. The same diagnostic binary has three
modes: `off` skips structural support; `track` maintains support and prepares
domains, then clears domains before the full LU scan; `restricted` retains the
prototype's domain-restricted scan. Numeric nonzero-row scratch stays enabled.
Profiling is an independent opt-in. No arithmetic, pivot/update order, proof,
convergence policy, random ordering, routing, cache or deadline was changed.

All diagnostic rows use final r3 source and one compiled build. The profile-off
comparisons isolate the mode choice without differing GWT binaries, but still
include host/JIT/cache effects and dormant diagnostic branches. Separate unchanged
control rows bracket this code-shape boundary. Baseline profiling is paired
OFF02/ON03 and ON04/OFF05; tracking cost OFF05/TRACK06 and TRACK09/OFF10; tracker
profiling TRACK06/ON07 and ON08/TRACK09; the restricted prototype OFF10/R11 and
R12/OFF13. Track-versus-restricted observations are not adjacent paired controls
and must not be promoted to a clean isolated scan-saving estimate.

| Row | Mode / profile | Cold ms | Proof ms | Routing ms | Warm ms | Case s | Cleanup s |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 01-control | control / off | 104176 | 73415 | 24089 | 30892 | 135.844 | 1.015 |
| 02-off | off / off | 104878 | 75233 | 23390 | 30073 | 135.750 | 1.047 |
| 03-off-profile | off / on | 117718 | 84692 | 24683 | 33422 | 151.891 | 0.828 |
| 04-off-profile | off / on | 109654 | 77053 | 24758 | 31256 | 141.672 | 0.907 |
| 05-off | off / off | 108471 | 78698 | 23423 | 30304 | 139.547 | 1.171 |
| 06-track | track / off | 103342 | 73385 | 23484 | 32491 | 136.609 | 1.171 |
| 07-track-profile | track / on | 125866 | 90765 | 25281 | 34240 | 160.891 | 1.110 |
| 08-track-profile | track / on | 122832 | 89874 | 24617 | 33943 | 157.563 | 0.890 |
| 09-track | track / off | 112453 | 80196 | 24920 | 31419 | 144.719 | 1.188 |
| 10-off | off / off | 109491 | 77056 | 25691 | 31876 | 142.125 | 1.172 |
| 11-restricted | restricted / off | 114172 | 79389 | 27620 | 31879 | 146.813 | 1.047 |
| 12-restricted | restricted / off | 111480 | 79718 | 24686 | 33882 | 146.140 | 1.219 |
| 13-off | off / off | 110717 | 78624 | 25116 | 31214 | 142.687 | 1.172 |
| 14-control | control / off | 106454 | 75693 | 24122 | 30816 | 138.031 | 0.907 |

Host wall-minus-monotonic outer differences span -14.843 to 11.182 ms. These endpoint checks do not certify browser wall-clock stability at every point.


Application elapsed/proof/routing fields retain their existing wall-clock basis.
The new collector uses only `performance.now()`; host case/outer time is monotonic.
Cleanup is separate. Each row has a fresh owned Edge headless profile; the warm
phase intentionally reuses a proved value on another owner. OS caches were not
reset. No build/native campaign ran during the sequence. Artifact readers and
normal Codex activity did run. WMI gives nominal endpoint clocks, not thermal,
turbo or throttling telemetry. Process CPU deltas cover surviving processes only.
The host's 600 s case timeout and lack of an independent outer subprocess deadline
are unchanged limitations. No machine-wide settings or unrelated processes changed.

The predeclared material-effect rule requires the same direction in both pairs
and each magnitude greater than `max(1000 ms, 3 × comparable OFF range)`.
`analysis.json` exposes the comparator rows and arithmetic. This is a conservative
decision rule for this sequence, not a confidence interval or a precise causal
decomposition. Prior 98.846–99.106 s controls are historical, not this session's
noise estimate. No routing subtraction is used to manufacture a solver speedup.

## What the counters establish

All four profiles agree on 608,049 accepted steps, ten timestep retries,
1,350,440 nonlinear trial iterations, 742,391 factors and solves, 501 analyses,
521 circuit stamps and 5,440 solver-loop calls in the cold phase. `trialAttempts`
counts nonlinear iterations, not distinct timesteps. The remaining iterations
are not all rejected steps. Warm counts are retained separately in raw reports.
Full nonstructural operation counts, including LU scans/copies and numeric scratch,
match between off and track profiles. This fixture shows no additional numerical
analyze/factor/solve/retry work caused by structural tracking.

Both tracker profiles count 118,624,334 support-stamp invocations: 118,595,724
existing coordinates and 28,610 new ones. Of the new coordinates, 6,478 merge
partitions and 22,132 stay within an existing partition. Initial construction adds
97,375 coordinates and 78,939 unions; total initial plus dynamic unions are 85,417.
There are 114,240 relabeled vertices. Thus coordinate no-ops, new coordinates and
partition changes have separate meanings; none means the numerical matrix is
unchanged. The structural tracker records mapped coordinates even for zero-valued
stamps; a recorded coordinate is not a claim of a currently nonzero matrix value.

Domain preparation succeeds 742,391 times, with 741,870 unchanged partition epochs
and 521 changed epochs. `domainCopiedSlots=124,783,236` counts row/column member
visits (several array assignments per visit), not bytes or all memory writes.
Domain capacity allocation is 39,174 element slots. Support construction makes
two 3,595,570-slot scans and allocates 4,287,954 boolean/int element slots. These
are source counters, not allocation bytes, GC time or heap-profiler evidence.
Creation also checks row aliasing in a nested pointer-comparison loop; this scan
has no separate operation counter. Object headers and profiling arrays are not
included in these capacity-slot counts.
The tracker uses indexed arrays/union operations, not a general hash-set rebuild.

The accepted numeric LU path still inserts and clears 345,366,723 lower-row
references. It allocates 345 lower-reference capacity slots and 345 upper-column
capacity slots in the cold profile. Full LU records 1,272,116,933 zero-row search
visits, 2,654,613,198 pivot-scan visits and 1,981,844,174 update slots (scaling and
trailing updates combined). Matrix reset copies 9,548,471,508 value slots plus
113,518,674 RHS slots. These numeric costs were counted but were not independently
ablated from factorization; structural-tracker-off does not make them free.

Observed cold instrumented scope totals (milliseconds; nested and clock-limited):

| Row | Solver loop | Analyze | Factor incl. domains | Solve | Create | Domains | Max create | Max domain | Sample total µs | Empty timer µs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 03-off-profile | 83644.200 | 1883.600 | 38840.400 | 8877.600 | 0.000 | 0.000 | 0.000 | 0.000 | 600.000 | 600.000 |
| 04-off-profile | 76396.900 | 1591.700 | 35826.000 | 7951.500 | 0.000 | 0.000 | 0.000 | 0.000 | 300.000 | 300.000 |
| 07-track-profile | 91215.500 | 1937.000 | 42570.600 | 9038.500 | 60.700 | 1506.100 | 0.900 | 0.300 | 400.000 | 500.000 |
| 08-track-profile | 88937.800 | 1893.200 | 40733.800 | 8708.700 | 59.400 | 1539.300 | 1.000 | 0.400 | 400.000 | 600.000 |

Each cold profile samples 1,812 mapped stamps out of 118,769,298; warm samples 109 of 7,160,494. Raw histograms, sample maxima, all coarse maxima and every warm counter remain in the reports. Zero create/domain time in OFF means those structural operations were absent; unmeasured numeric-maintenance cost is not zero.


The stamp sampler covers every 65,536th mapped matrix stamp, including diagnostic
classification overhead. Samples cluster at zero or approximately 100 microseconds;
the empty-timer samples are of comparable magnitude. The distributions and maxima
are quantized observations of sampled instrumented calls, not the distribution or
maximum of every intrinsic tracking operation. Neither raw nor calibration-subtracted
sample totals are extrapolated. `measurementValid` only certifies clock/counter
guards, not adequate timing resolution. Fine domain/factor/solve scope timings also
approach the clock floor. Timing categories are inclusive/nested, not additive.
Consequently an exact total support-maintenance time, intrinsic single-call maximum,
and majority of support time by element cannot be reported responsibly.

For call-count attribution, the seven dump types 100/162/450–454 account for
101,236,380 of 118,769,298 mapped matrix stamps (85.24%). They are diode, LED,
limited supply, protection fuse, service relay, bounded load and linear regulator.
The relay is largest at 22,461,888 calls (18.91%); this is not a time percentage.
Typed rows total 117,552,446, with another 1,216,852 outside the type table.
Cold/warm per-type counts and new-coordinate counts are retained in each raw report.

## Invalidation and the optimization decision

The 521 observed invalidation calls all change valid support to invalid:
496 analyze, 20 stamp and five other. Analyze discards graph/matrix mapping state;
the other bucket is snapshot graph restoration. Support captures exact matrix,
original-matrix, size and mapping identities, so those are ownership boundaries.
The stamp reason combines `stampCircuit` rebuild with a dynamic union that leaves
one component; this instrumentation cannot split the 20 events between them.
Ordinary coordinate additions update support incrementally. Unchanged partition
does not justify suppressing numerical factorization or graph invalidation.

Prepared domains are factor-local: they bind current matrix rows, and LU pivots
swap those row references together with group/index mappings. Cleanup clears the
prepared workspace. Therefore 741,870 unchanged epochs are not 741,870 safe cache
hits. An immutable partition cache would still need current row rebinding and
pivot synchronization, and these results do not justify implementing it.

Factorization remains the largest measured kernel category, with matrix assembly
and reset also material in the prior same-seed breakdown. The earlier profiled
reset estimate was 7.414–7.663 s, sampled and instrumented, not a guaranteed saving.
A narrow *unrun* hypothesis is to compare the accepted inner matrix value-copy
loop with per-row `System.arraycopy`, keeping current destination row objects,
numeric contents, pivot behavior and independent bitwise/proof oracles. LU mutates
values and swaps row references; aliasing `origMatrix` or replacing row identities
would be incorrect. A GWT copy change may be slower. This identifies a bounded
future measurement, not an optimization, largest removable share or route to 90 s.

## Fresh gates and retained failures

| Gate | Result and limit |
| --- | --- |
| Unchanged control JDK8/GWT | PASS, five permutations 87.085 s / link 2.118 s; host 91.657 s |
| Final private r3 JDK8/GWT | PASS, five permutations 87.980 s / link 1.401 s; host 91.465 s |
| r2 native prefix suites | PASS individually: temporal 161, measurement budget 25, A07 execution 271699, structural support 1661, factorization 2148 assertions |
| r2 native campaign | FAIL / exit 2 at new diagnostic test: reflection oracle called with missing size argument; host 47.195 s including successful cleanup |
| r3 focused diagnostic test | PASS 73 assertions; host 34.286 s including successful cleanup; only test source changed, so unchanged prefix-suite inputs retain their evidence |
| Compiled control A07 / normal-disabled | PASS, 1.281 / 0.485 s operations; host cleanup PASS |
| Compiled diagnostic A07 / normal-disabled | PASS, 1.313 / 0.484 s operations; host cleanup PASS |
| Maintained A07 reader | PASS plus three corrupted reports rejected for each build |
| Actual Q30 private rows | PASS 14/14, input/runtime audits, restored owners and separately timed host cleanup |
| Independent strict proof reader | PASS and 43 corruptions rejected per row; all 14 complete non-timing projections identical |
| Independent profile reader | PASS all 12 diagnostic reports, OFF metadata absence and four ON cold/warm profiles; 13 synthetic corruptions rejected per invocation |
| Frozen normal timing margin | BLOCKED; private diagnostic success does not qualify normal play |


The independent metadata reader initially expected internal `supportPartitionEpoch`,
which is not serialized. The reader was corrected to the emitted schema and still
checks union conservation and factor/domain categories; reports were not edited.
Its synthetic negative case now corrupts visible coordinate accounting. An early
parity receipt omitted the names of excluded OFF metadata; that receipt bookkeeping
was corrected and rechecked. The projection itself and exclusion boundary did not
change. Direct injected profiler failure/successor/cancellation paths were not run;
source lifecycle review plus successful cleanup does not certify every exception.
No zero-pivot fallback count or upper-column insertion count was added.

Full normal/scale acceptance is **NOT RUN** while timing remains blocked. Visible
player-flow testing is **NOT APPLICABLE**: no player-flow implementation changed.
Source-only independent review covered mode ownership, lifecycle, counter semantics,
callsite attribution and invalidation/cache hazards. The metadata reader has a
different author from the profiler. Artifact validation is not a new browser gate.

## Reproduction, integrity and resources

`source/baseline-inputs.json.gz` binds all 1,340 unchanged control inputs. Reconstruct
the prior `live-lu-support-r1` six-overlay prototype (1,343 inputs), then apply the
six exact `source/diagnostic-r3/*.gz` files for 1,345 inputs. Its `*-inputs.json.gz`
records every final hash. R1 is an ungated draft; r2 has the failed test invocation;
r3 changes only that test file. Browser source/web manifests bind the measured
builds before and after every case. The existing older packet retains baseline
witnesses and prototype overlays; no experimental Java file is installed in the
production source tree by this evidence commit.

Use JDK 8u502-b07 with `scripts/build.ps1 -JavaHome <JDK8>` in each private fixture.
Focused native commands use `scripts/verify-current-contracts.ps1`, with suite
names and outcomes in `gates.json`. Browser runs use the unchanged maintained
`compiled_attribute_acceptance.py`; exact per-row specs, procedure sources, pinned
source/runtime manifests and raw reports are archived. Python 3.12.14 used a task-
local Playwright 1.57.0 installation. No global installation changed.

Raw reports, proof projections and source gzip payloads are exact. Other records
replace personal paths with tokens; `provenance.json` records original/payload
hashes and that boundary. It separately records removal of trailing empty lines
from the archived metadata reader, with exact original newline bytes recoverable
and reader logic unchanged. Authored packet
documents and the packet auditor are covered by the inventory. The full unrelated
process lists, browser profiles,
libraries and compiled outputs are not committed. `audit_packet.py` verifies
inventory, source overlays, runtime bindings, raw proof hashes, named metadata,
independent readers and complete proof parity. Run `python -B audit_packet.py`;
`--seal` refreshes the inventory only after a deliberate evidence edit.

`preservation.json` confirms all 96 desktop and 172 Q30 pre-existing changed
files remain byte-identical, with status unchanged except this packet and the
task report. The 26 tracked Q30 scale edits and pre-existing untracked work are
preserved. Only the report and this evidence packet belong to this continuation.

All browser/server cleanup receipts pass. After archival and a passing packet
audit, exact ownership, containment, reparse and live-process checks passed for
the two task directories. The OS-Temp fixture (7,868 files) and small recovery
directory (224 files) were removed in 1.409/0.056 s, separately from the measured
cases. `resource-cleanup.json` retains exact durations. No task resource remains;
no other directory or process was cleaned up.
