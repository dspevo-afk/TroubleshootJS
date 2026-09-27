# Q30 acceptance continuation — BLOCKED / NOT ACCEPTED

Base: `745d537de5b18bd7ec2ae769fc00d3696e5af6bd`, branch
`codex/q30-multirail-qualification`. Normal Q30 remains disabled. The frozen
production bounds are 90,000 ms cumulative, 640 shared work units and 5,000 ms
per active operation. U06, U07, Q60 and later milestones are out of scope.

## Predeclared investigation

The initial matched timing sample is exact seeds **13, 7, 64**, in that order,
each cold followed by a fresh-owner warm request. These are the accepted repair's
37/33/35-package cases; seed 7 is from its held-out corpus. The maintained
`tests/browser/compiled_attribute_acceptance.py` runner reads the compiled
private coordinator report. Its 300-second private measurement allowance is
used only to locate work and retain complete timings; no row is production
acceptance. Failed/rejected rows remain separate. With n=3, report the median
and sample maximum, not an estimated population p95.

Before this baseline, every one of the 1,682 files in the accepted repair's
compressed input manifest matched its archived hash, including the compiled
web output. The baseline therefore executes that exact built candidate.
The runner independently audits its consumed inputs before and after the run.

The investigation separates matrix copying, model stepping, factorization,
solving, wire-current work and ownership checks before selecting an optimization.
Required proof, solver settings, physical admission, deterministic ordering,
service, difficulty, identity, cancellation and cleanup remain fixed.

Separately audit purposeful small/middle/large circuit recipes. The current
33/35/37 variants do not demonstrate the requested 20–40 range. Structural
coverage and normal production timing must both pass before Q30 can be enabled.

The bounded optimization below improves the measured cold path but fails the
frozen production limit. Broad scale remains unimplemented. The user's stop
rule applies; no Q30 completion or normal publication is claimed. No commit
or push has been made by this continuation.

## Fresh baseline

The three cases completed on the [recorded qualification host](qualification-host.json).
The private measurement runner and cleanup PASS; production budget comparison
FAIL. Cold median is 120,720 ms and maximum 127,588 ms. Diagnostic proof alone
has median 94,747 ms and maximum 98,912 ms. Warm median is 27,812 ms; warm
proof reuse does not qualify the cold requirement. The source/web input audit
passes with no changed consumed files, and no owned process survives cleanup.

The unchanged independent reader passes all three rows, 30 electrical pairs
and 37 receipt-corruption canaries. See [timings](baseline/timings.json),
[runner and cleanup](baseline/runner-result.json),
[strict reader](baseline/strict-reader.txt), and the complete compressed
[receipts](baseline/receipts.json.gz).

Seed 13 spends 24,654 ms on healthy-hypothesis profiles, 17,682 ms on input
observations, 20,373 ms on repair-status profiles and 20,394 ms on actual
customer retests. All 185 DC observations cost 1,222 ms, hypothesis replay
installation 475 ms and the PHYSICAL stage 66 ms. Its total is 127,588 ms:
20,875 ms recorded routing and 98,912 ms proof. This rules out dropping,
caching or weakening physical admission or reconstruction as a useful way
to recover the roughly 38-second deficit. Required solver work is the main
investigation target. All five hypotheses and all 390 proof units remain.

## Sampled kernel profile

The [temporary instrumentation](kernel-instrumentation.patch.gz) was built with
the maintained JDK8/GWT command; the corrected [build passed](profile-build.json).
One initial GWT compile failure in its timer fallback is retained separately.
The actual private seed-13 cold/warm run and cleanup passed. The patch was then
removed from production source before optimization and uninstrumented timing.

The [raw kernel profile](kernel-profile.json) records 99 temporal calls,
606,019 accepted steps, 1,347,344 nonlinear trials, 741,325 factorizations and
741,325 solves in the cold run. Full matrices span 82–92 rows; reduced matrices
70–81, with 267–269 solver elements. Ninety-nine analyses cost 0.3184 seconds;
4,750 batch preparations cost 0.1057 seconds.

Sampling one of every 64 accepted steps covered 9,470 steps and 11,560
factorizations. Factorization consumed 0.9864 of 1.5683 total sampled seconds
(about 63%). Other sampled seconds: matrix copy 0.0923, model work 0.0889,
solve 0.1229, solved-state publication/finite checks 0.1522, wire/callback work
0.1112 and measured guards 0.0144. These are raw sample sums, not estimated
whole-request component totals. Browser timer resolution and sampling overhead
limit precision; the instrumented request duration is not production evidence.

This measurement selects a narrow zero-product elimination in the existing
Crout factorization for testing. It adds no cache or graph reuse and retains
all nonzero subtraction order, pivots, finite-input validation, factorization,
solving, trial checks, events, observations, intervals and proof units.
Independent historical Crout oracles are expanded with connected sparse
systems through the measured 81-row reduced size.

## Uninstrumented optimization result

The production change only skips a finite zero row factor in the existing
Crout column update. There is no new cache, graph lifetime, recipe, random
draw, routing order, proof omission or deadline. The final uninstrumented
JDK8/GWT [build passes](sparse-factor-build.json), exit 0, 81.399 seconds,
all five permutations. The native [A07 execution gate](native-factor.txt)
passes 93,541 assertions, including the independent historical factor,
pivot and solution oracle, finite-input rejection and execution boundaries.

| Seed | Packages | Baseline cold ms | Optimized cold ms | Routing ms | Proof ms | Shared units |
|---|---:|---:|---:|---:|---:|---:|
| 13 | 37 | 127,588 | 113,630 | 20,572 | 86,107 | 464 |
| 7 (held out) | 33 | 120,720 | 106,251 | 20,018 | 79,834 | 468 |
| 64 | 35 | 115,450 | 103,046 | 12,635 | 83,305 | 446 |

Cold median improves from 120,720 to 106,251 ms (12.0%); observed maximum
improves from 127,588 to 113,630 ms. Every request still exceeds 90,000 ms,
by 13,046–23,630 ms. All five hypotheses and 390 proof units execute in every
cold case. The private runner, input audit and cleanup PASS; the production
budget comparison FAILS. Normal production Q30 acceptance is NOT RUN because
Q30 remains disabled; these 300-second private reports cannot substitute for
it. There are no rejected cases in this selected timing sample. Stage times
and active-operation maxima are retained in [timings](sparse-factor/timings.json).
Warm median is 26,215 ms and supplies no cold-admission proof.

The unchanged [strict reader](sparse-factor/strict-reader.txt) passes all
three rows, 30 electrical pairs and 37 corruption canaries. Complete
[receipts](sparse-factor/receipts.json.gz) retain proof/cache/cleanup details;
the [runner receipt](sparse-factor/runner-result.json) records fresh process
ownership, zero source/web input changes and no surviving owned processes.
[Baseline parity](sparse-factor/baseline-parity.json) compares the exact
request, canonical context, program, partition, every proof sample and all
repair/retest evidence: all match for all three cold rows. The JSON lists the
compared fields explicitly; duration and transient owner identifiers are not
part of the parity claim.

Independent numerical review found no correctness blocker for finite inputs.
It identified the IEEE signed-zero limit: skipping a zero subtraction can
change the sign bit of an exactly zero factor or solution. Numeric values,
pivot choices and the measured replay/proof values agree; this is not a
bit-for-bit signed-zero preservation claim. The review was read-only and did
not run tests. Root inspected the integrated change separately.

## Scale and acceptance boundary

The [scale audit](scale-audit.md) accounts for every existing package and
examines purposeful smaller/larger recipes. Actual implemented sizes remain
33/35/37. Counts **20–32, 34, 36 and 38–40** are unsupported by the current
implementation. This is not a new requirement to support every integer;
it describes the exact current gap and why three midrange sizes cannot prove
the requested broad 20–40 coverage. No new topology has been qualified.

The [pipeline audit](pipeline-audit.md) covers all requested investigation
areas and separates measured costs from source-level opportunities. Both
frozen-budget performance and broad scale remain BLOCKED. U06, U07, Q60 and
later milestones are unstarted. Regression and visible-flow results below
do not remove these two blockers.

## Regression and visible verification

- PASS: [17 focused native suites](native-focused-result.json), exit 0 in
  73.306 seconds including maintained cleanup; [full focused output](native-focused.txt).
  This covers staged-family registration, control observations, Q30 plan,
  temporal cancellation/power/successor boundaries, service preparation,
  exact generation requests, private measurement isolation, both frozen
  production policies, catalog identity, regulator/relay/sensor behavior,
  diagnostic proof, generation dependencies and medium admission. The
  separate A07 oracle gate above supplies solver-specific coverage.
- PASS: compiled [normal-player regression](normal-player.json): all ten
  enabled families, three launches each, two exact replays, power isolation,
  privacy and blocked Q30 replay without starting generation. Owned browser
  and server cleanup passed. No family was enabled or disabled by this change.
- PASS with recorded transport limit: [visible input flow](visible-service.json)
  through the actual compiled preview and built-in Browser. The normal menu
  offers nine EASY families and one MEDIUM family; Q30 is absent. Normal LED
  replay seed 3 reaches its ticket and fails unrepaired customer retest.
  Private Q30 seed 13 / DRIVE_A_OPEN also fails unrepaired retest; red RDA.1
  versus black J1.2 reads 4.85 V and red RDA.2 reads 0 V. Power-off, lead lift,
  reconnect, removal and a new 1000-ohm resistor lead to a passing customer
  retest. Both boards are powered off before leaving; the task tab is closed
  and the owned preview is stopped with port 8901 released.
- Four screenshots were inspected: [normal menu](01-normal-medium-menu.png),
  [normal unrepaired board](02-normal-unrepaired.png),
  [37-package Q30 overview](03-q30-unrepaired.png), and
  [repaired Q30 retest](04-q30-repaired.png). Q30 overview labels are crowded;
  visible terminal selectors were usable. This is no new full-board readability
  or full-range qualification. Small/large full-range screenshots are NOT RUN
  because those recipes do not exist.
- One repaired retest click returned a CDP transport timeout after delivery;
  subsequent visible text and screenshot confirmed completion. This does not
  certify transport reliability. An initial URL included the automated service
  verifier; that page was powered off and replaced before manual evidence.
- NOT RUN: unfiltered native matrix, expanded structural/held-out corpus,
  full service/finer-step matrix and normal-production Q30 acceptance. Prior
  full-suite receipts are historical, not reused as fresh solver-change proof.
  Focused passes cannot convert the failed budget or missing scale into PASS.

Maintained command forms (local paths intentionally use placeholders):

```text
pwsh -NoProfile -File scripts/build.ps1 -JavaHome <JDK8> -Target Compile -Style OBF
pwsh -NoProfile -File scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -Suite A07ExecutionContractTest -ReceiptOutputPath <task-temp>/native-factor-receipt.txt
# The 17 exact additional suite names are in native-focused-result.json.
python -B tests/browser/compiled_attribute_acceptance.py <repo> <task-temp>/sparse-factor <task-temp>/sparse-factor-spec.json
python -B docs/task-evidence/Q30/normal-admission/check_coordinator.py <task-temp>/sparse-factor/reader-wrapper.json --seeds 13 7 64 --self-test
python -B tests/browser/quickplay_procedural_acceptance.py <repo> <task-temp>/normal-player
pwsh -NoProfile -File scripts/start-preview.ps1 -QuickPlay -Port 8901
pwsh -NoProfile -File scripts/stop-preview.ps1
```

Each private runner receipt contains the exact case URLs, expected terminal
state, timeout and input digests. Compressed manifests beside those receipts
record every consumed source/web file. The temporary profiling patch and its
failed first build are retained; no profiling hook remains in production.
Architecture ownership is unchanged, so no architecture document change is
needed. Source/evidence are reviewable but uncommitted; no publication or
completion email is attempted.

The closing [JDK8/GWT rebuild](final-build.json) also PASSes all five
permutations, exit 0 in 79.594 seconds. The [final input audit](final-input-audit.json)
checks all 1,524 consumed source/web files against the optimized run's manifest:
zero changed bytes, including the regenerated JavaScript. Thus the timing and
browser results apply to the final compiled candidate without repeating an
unchanged full test command. The restored private verifier has the exact HEAD
Git blob; its remaining CRLF normalization was confirmed content-identical and
its index stat refreshed with no staged change. A preliminary attempt to
reconstruct original raw line endings failed its hash assertion before writing;
no source was changed by that attempt.

Runtime resources are closed. Administrative removal of the task-owned OS-temp
scratch and initial failed-build scratch was rejected by automatic approval
review as "blocked by policy" without a more specific reason. No alternate
deletion route was attempted. The inactive directories remain; exact leaves,
prechecks and separate runtime PASS results are in [resource cleanup](resource-cleanup.json).
Root's final tracked diff and cached diff checks PASS; the index is empty.
Pre-existing bytecode and the unrelated Desktop checkout remain untouched.
The two raw patch artifacts are stored as gzip data with verified byte-exact
roundtrips and [uncompressed hashes](patch-archives.json); this preserves their
original diff context whitespace. Independent evidence review confirmed the
reported numbers and scope limits. Its visible-preview cleanup traceability
note is resolved by the separate resource receipt above; the reviewer ran no
tests or builds.
