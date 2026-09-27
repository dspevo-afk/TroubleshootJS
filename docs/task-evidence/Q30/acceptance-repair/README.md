# Q30 acceptance repair — BLOCKED / NOT ACCEPTED

Base: `97e072767a54292e964958185c17798e18b107ae` (`Qualify Q30 normal-medium
generation and repair`), parent `16a922b2b757dfcd1cbc3a0230ab2b97ac2fbb50`.
The prior completion claim is rejected. No U06, U07, Q60 or later work is
included. Historical evidence is preserved in its original directories.

## Acceptance boundary

Normal production retains **90,000 ms cumulative, 640 shared work units,
5,000 ms per active unit, at most four deterministic candidates**. A timeout
is terminal and cannot become a retryable rejection. The private diagnostic
measurement path may use 90–300 seconds with its separate proof cache and
explicit developer scope; it cannot authorize normal-player publication.

The repair moves staged planning, canonical plan identity, routing, admission,
construction and realization manifests behind a registered family capability.
Q30's implementation remains available for private qualification. Its normal
menu entry and publication are disabled while acceptance is unresolved.

`Rb30PlayerFamilyCapability` owns the Q30 plan, canonical request, routing
policy, accepted-route construction and realization manifest. The generic
request/session and coordinator contain no Q30/RB30 plan, generator or route
type. `StagedFamilyRegistrationContractTest` registers a second MEDIUM provider
in an isolated catalog, validates its normal execution contract and exercises
candidate ordinal 2, cache identity, yielding construction and manifest return.
Its LED owner is a dispatch sentinel, not medium physical-admission evidence.
The independent source review found no blockers in these boundaries or the
control-observation change; it did not run tests. Root also inspected the diff.
The final documentation/receipt review found no acceptance blocker; root
clarified its one historical branch-label observation. It ran no runtime tests.

## Performance investigation

The [retained GWT21 measurements](../normal-admission/performance-review.md)
were taken from the rejected candidate. Their private gate PASS is not a
production-budget PASS. Seeds 13, 7 and 64 took respectively
127,275 / 120,635 / 115,685 ms cold total, with routing
20,338 / 20,005 / 12,493 ms and diagnostic proof
99,335 / 93,312 / 95,410 ms. Every row exceeded 90 seconds.

The actual label aggregates put about 23–25 seconds in healthy-hypothesis
profiles, 16–18 seconds in the 20 input observations and settlement, 19–21
seconds in repair-status profiles, another 19–21 seconds in actual customer
retests, and 5–6 seconds in power-off/service readiness. The 185 real DC
readings consume about 1.0–1.2 seconds; WAIT labels consume 1–2 milliseconds.
Those timings retain all five disjoint hypotheses, 37 observations per
hypothesis and 390 explicit-completion units. The four-condition healthy,
repair-status and customer-retest profiles alone contain 60 real 30-ms
intervals and at least 360,000 accepted 5-us solver steps before retries.

The bounded optimization removes repeated string-map lookups from generic
source-control currentness checks. It relies on the private binding registry's
append-only construction contract and terminal aborted flag, while retaining
every captured binding revision and live control-state check. Focused negative
contracts cover additions, abort, missing bindings, raw control mutations and
change-then-restore. No hypothesis, measurement, settlement duration, solver
timestep, repair, retest or cleanup requirement is removed. Any measured
speed benefit must come from fresh compiled runs, not this source argument.

The final-source compiled [measurements](timing-summary.json) still fail the
90-second production target. No material speed benefit is established:

| Seed | Parts | Cold total ms | Routing ms | Proof ms | Cleanup ms |
|---|---:|---:|---:|---:|---:|
| 13 | 37 | 126,904 | 20,603 | 98,702 | 3 |
| 7 (held out) | 33 | 119,996 | 19,935 | 92,662 | 3 |
| 64 | 35 | 114,754 | 12,659 | 94,376 | 3 |

The exact archived requests identify seed13 as separate/direct A-NMOS/B-BJT,
seed7 as separate/direct BJT/BJT, and seed64 as shared/hysteretic NMOS/NMOS.

These are **private measurement-only** runs with a 300,000 ms allowance,
not accepted normal launches. Cleanup is excluded from each total. The cold
median is 119,996 ms and sample maximum is 126,904 ms; n=3 does not establish
a population p95 or SLO. Every cold proof alone exceeds 90 seconds. Warm
private totals are 28,017 / 26,957 / 20,055 ms and do not qualify cold normal
admission. Normal-player Q30 acceptance is BLOCKED and was not attempted by
enabling its catalog entry.

The compact [final work-label timings](work-label-timings.json) retain every
label, unit count and maximum unit time. Each seed completed all 390 units;
active label sums were 96,805 / 90,778 / 92,506 ms respectively. The cumulative
proof times also include work between those labels.

Routing already tries deterministically ranked placements and bounded route
policies with a typed priority queue. Native examples consume millions of
expansions; native timing is not browser timing. Dropping orderings/attempts or
reordering them would change route identity and rejection results. This audit
found no proved generic shortcut sufficient to close the measured gap; routing
semantics and frozen limits remain intact.

## Original scope audit

The [predeclared Q30 corpus](../normal-admission/README.md) has 24 representative
and 24 held-out rows: all eight driver/reference axes at 33, 35 and 37 packages,
plus three signed-long boundary rows. There are 42 accepted routes and nine
retained rejections; held-out routes are 19/24 accepted and five rejected.
This is meaningful topology diversity inside the 20–40 band. It supplies no
measurements for 20–32 or 38–40 parts and cannot establish arbitrary full-range
support. The original approximately-30-part plus held-out 20–40-part deliverable
is unchanged; this repair neither adds new board variants nor invents a new
acceptance definition. Broader size qualification remains unproved.

The 40 service and 40 finer-step rows use eight selected seeds and all five
faults; only one of those seeds is held out. The separate P09 final22 72-row
corpus is RB15 regression and must not count as Q30-scale proof.

## Fresh validation

Maintained commands (placeholders identify the local JDK8 and owned OS-temp
paths, not alternate implementations):

```text
pwsh -NoProfile -File scripts/build.ps1 -JavaHome <JDK8> -Target Compile -Style OBF
pwsh -NoProfile -File scripts/verify-current-contracts.ps1 -JavaHome <JDK8> -ReceiptOutputPath <task-temp>/native-receipt.txt
python -B tests/browser/compiled_attribute_acceptance.py <repo> <task-temp>/coordinator-final <task-temp>/coordinator-spec.json
python -B tests/browser/quickplay_procedural_acceptance.py <repo> <task-temp>/normal-player-final
python -B docs/task-evidence/Q30/normal-admission/check_corpus.py --log <task-temp>/native.log
```

The private runner receipt contains every case URL, seed, expected terminal
state and timeout from its specification. Visible interactions are recorded
in the two service receipts below; both used `scripts/start-preview.ps1
-Port 8901` and `scripts/stop-preview.ps1` for owned preview lifecycle.

- PASS: the 12-suite focused gate (72.142 s), including exact request/replay,
  physical admission, policy guards, small/composed generation and cleanup.
  Its initial registration test was subsequently strengthened; the final test
  is included in the full native gate below.
- COMPILE/LINK PASS, initial wrapper receipt INVALID: actual
  `scripts/build.ps1 -JavaHome <JDK8> -Target Compile -Style OBF` compiled
  all five permutations with JDK 8u502. The temporary wrapper incorrectly
  read `$LASTEXITCODE` after a normally returning PowerShell script; it
  recorded null and FAIL. That [original receipt](build-wrapper-original.json)
  is preserved. The direct child-process rerun passed with exit 0 in
  79.443 s on the final production source; see [receipt](build-final-result.json).
  All five permutations compiled (76.081 s) and linked (1.287 s).
- PASS: compiled private cold/warm runs for seeds 13, 7 and 64, with 30
  independently checked electrical pairs and 37 corruption canaries. The
  separate scope-loss negative restores its predecessor and passes 13
  corruption canaries. The ordinary cache remains unchanged; private cache
  clearing, input audits and owned-process cleanup pass. See the
  [compiled receipt](compiled-private-result.json), [strict reader](coordinator-reader.txt)
  and [scope-loss reader](scope-loss-reader.txt).
- PASS: [normal-player compiled gate](normal-player-result.json): 30 launches
  across ten enabled families, two exact replays, and a blocked Q30 replay
  with the predecessor still isolated and unchanged. Maximum observed launch
  time was 27.036 s. No private verifier metadata appeared; cleanup passed.
- PASS: [visible normal-player service](visible-normal-service.json), exact
  LED seed3 replay: physical red/black probes, diagnosis, isolation, removal,
  shop purchase, 1-kohm replacement, repower and customer retest. Replaying
  restored the original failure with empty tray and cleared meter.
- PASS (developer service only): [Q30 visible service](visible-service.json),
  seed13 / DRIVE_A_OPEN: failed retest, 4.85 V versus 0 V across RDA, lead lift,
  removal, correct 1-kohm replacement and successful customer retest. Two
  retest-click transport timeouts are retained; subsequent
  visible state confirmed the completed actions. This is not normal Q30
  admission and does not certify a CDP wrapper. No controller was injected.
  Five useful screenshots were inspected and retained. Both boards were
  powered off; the task tab and owned preview server were closed.
- FAIL, retained: the first unfiltered native attempt stopped at the old
  developer-verifier assertion that all eleven registered families were
  enabled. Its [failure and cleanup](native-attempt1-failure.txt) and
  [539.181 s result](native-attempt1-result.json) are preserved. The corrected
  oracle checks ten enabled / eleven registered families and retains Q30's
  construction coverage. The [focused rerun](catalog-canary-receipt.txt) passes.
  An earlier invocation passed a comma-delimited string instead of a PowerShell
  suite array and failed before tests; its argument error is also retained.
- PASS: final unfiltered native matrix, **80 Java suites** plus independent
  seed/value/role oracles and report protocol, exit 0 in 3,421.497 s including
  cleanup. All 40 service and 40 finer-step cases pass. The independent corpus
  reader validates all 51 rows (42 accepted routes, nine retained rejections)
  and 17 corruption canaries. These are physical/behavioral contracts, not
  acceptance of the production deadline. See [native result](native-result.json),
  [summary](native-summary.txt) and [corpus reader](native-corpus-reader-summary.json).

The [final input audit](input-audit.json) finds all 1,682 source, test, script
and compiled-web inputs unchanged across the final gates. The compressed
`input-manifest.json.gz` contains the exact relative paths and hashes;
`native-receipt.txt.gz` preserves the complete native receipt once. The audit
also verifies both maintained strict readers are unchanged from the base.

Historical PASS receipts are not reused for changed inputs. Complete private
receipts are retained once in compressed form (`coordinator-receipts.json.gz`
and `scope-loss-receipts.json.gz`); decompress to JSON and run the maintained
`../normal-admission/check_coordinator.py --seeds 13 7 64 --self-test` or its
`--seeds 13 --expect-scope-loss --self-test` mode respectively. Archive hashes
are in [receipt-archives.json](receipt-archives.json).

## Evidence retention

Runtime cleanup passed: native-owned scratch, compiled browser processes,
visible boards/tab and the owned preview server were retired. Removal of the
inactive top-level task scratch directory was separately **BLOCKED** by automatic
approval review ("blocked by policy", no further reason). It is preserved;
see [resource cleanup](resource-cleanup.json). No alternate deletion was attempted.

Keep compact final receipts, input/candidate hashes, measured phase summaries,
useful failures and 2–5 inspected screenshots. Intermediate logs, complete
temporary input inventories and browser profiles belong in owned OS-temp
scratch, with exact process/path checks before cleanup. Existing committed
evidence remains untouched; pruning it requires separate authorization.
