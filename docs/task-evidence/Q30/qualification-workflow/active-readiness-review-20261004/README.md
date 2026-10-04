# Q30 active-readiness defect and local acceptance review

2026-10-04; parent HEAD `88e9ff27411a470a6dde2120732c7e3af609257e`.
Recommendation: **NOT READY for normal enablement**. Public registration remains
false; Q30 remains BLOCKED / NOT ACCEPTED. No push/publish/email, evidence deletion,
budget change or U06/U07/Q60 work. This is a verified local fix checkpoint.

The three earlier headed flows correctly blocked OHM/DIODE with disconnected
supplies but displayed UNKNOWN. An observation-only actual CircuitJS diagnostic
on the exact preceding 1,354-input candidate found finite, owned, mutually
consistent voltage samples and current receipts. Real residual voltage on RAW12,
EN5, LED12_FEED, reference and coil paths was classified as unexplained because
the Q30 contract omitted those storage exposures. This is a contract defect;
the real electrical blocking itself is required.

`Rb30PowerDomains` now declares the existing control paths exposed to CIN/C5,
optional C12 and coil energy. Filtered/hysteretic RAW/SENSE declarations follow
the actual plan. OBSERVATION_REQUIRED means observe voltage and enforce discharge;
it does not add capacitance. Load-domain nets and unrelated sensor paths remain
NONE. The generic owner, solver, models, thresholds, active-source transaction,
generation plan, routes, fault/service owners, epoch and save/replay format are
unchanged. No readiness bypass or fake decay/reading was added.

| Check | Actual result | Operation time / limit |
| --- | --- | --- |
| Pre-fix diagnostic, three serial actual CircuitJS graphs | Completed, exit0; UNKNOWN cause identified, not acceptance | 72.050 s; each compiler/JVM bounded 60 s |
| First five-suite attempt | FAIL; fixture lacked UI speed widget; child exit1 / maintained command exit2 / outer tool exit1 preserved | 39.606 s |
| Corrected five affected native suites | PASS exit0 | 62.971 s |
| Four affected cache/diagnostic/preparation/temporal suites | PASS exit0 | 48.750 s |
| Actual final-source `scripts/build.ps1` JDK8/GWT OBF | PASS exit0, five permutations/link | 99.875 s; process bound 900 s |
| Exact current 1,355-file source archive byte round trip | PASS, no extraction | See archive audit |
| Nine recorded exact process instances after exit | PASS absent, no termination attempted | See release audit |

Maintained native commands and exact arguments are retained in `helpers/` and
raw receipts are exported with path tokens. Five suites: Q30 power readiness
120 assertions, Q30 plan 4,381, A06 power 152, U02 measurement 585, generated
power controls 16. Four more: Q30 temporal 161, service preparation 18, D01 168,
A10 15. Neither invocation runs the full matrix or independent oracle scripts.
The new suite is registered in the maintained matrix (now 83 suites).

The new fixture checks 20/30/40 packages at roots 10387/10226/10014. An actually
uncharged and isolated graph gives READY, real 180-ohm OHM readings, and real
3-V/1-kohm DIODE divider/current readings. Production temporary sources/readers
and restoration execute; only native scheduling uses eight guarded CircuitJS
steps because there is no GWT speed widget. Charged-then-isolated graphs report
DISCHARGE at 40 us and after 1 ms, 100 ms and 899 ms further simulation, and both
active calls remain blocked without graph/time/power change. Independent canaries
keep unexplained load energy and NaN/unknown-source observations UNKNOWN, stale
observations WAITING. No compiled UI measurement PASS follows from this fixture.

**Remaining acceptance work:** power.contract.canonical enters
GenerationDependencyContext and exact D01 cache identity. Every Q30 context
changes, despite unchanged solver graph and work recipe. The old cold77 result
is valid historical timing/electrical evidence for its preceding source/context;
it cannot qualify this new candidate. Regenerate the maintained pin and run the
full frozen 77-case cold cohort in a newly coordinated resource window, without
reusing the consumed grant or changing 90,000 ms / 640 shared / 5,000 ms limits.
Provide the original requested cold/warm p50/p95/failure distribution with clear
population boundaries; 77 normal warm cases are not present in the prior packet.
Historical nearest-rank cold p50/p95 are 60.844/78.689 s, min/max
20.788/85.562 s, maximum 506 work units. The source-bound derivation is in
`historical-cold-distribution.json`; those values are not current-candidate proof.

Fresh compiled production instrument status/retry evidence on this new source
is NOT RUN. The previous private enabled build/menu/replay and headed repair
results remain historical; the private app still contains the preceding source.
A new private enabled build must contain only the reviewed one-line catalog
toggle and bind its menu/replay/status evidence to the current candidate before
public enablement. Repeating all three complete guided repair flows is not
automatically needed: no service/mutation/customer recipe changed. The existing
105 native service/sensitivity rows and visible guided repair/retest evidence
retain their stated boundaries, rather than becoming fresh-source passes.

Longer same-graph charged-to-READY recovery is NOT RUN. The pre-fix native graph
still retained real input charge after five simulated seconds (30-part entry
charge about 12 V; 40-part RAW12 about 9.20 V). This fix truthfully reports
DISCHARGE and preserves waiting; it does not promise quick/numeric measurement
on every charged board. A bounded compiled recovery canary should resolve this
usability limit; a product discharge-path change would need separate evidence
and would affect the electrical/performance qualification more broadly.

The prior headed host exit1 remains a harness FAIL: its final body check saw its
own conhost before controller exit. Independent post-exit audit showed all 16
recorded instances absent and port closed. It is not a new product defect and
must not be relabeled a successful host command.

`current-source-inventory.json` audits all preceding inputs: 1,352 unchanged,
two changed, plus the new test = 1,355. The new exact archive is retained in
task temp with its hash in `current-source-archive-audit.json`; no compiled/cache
inputs or private paths are archived. This byte audit does not rerun the prior
baseline/three-overlay reconstruction or establish original acceptance.
Post-run raw inventory is not an immutable pre-run launch pin. Root was sole
writer during the checks/build and reviewed the consumed source/diff boundary.

Native scratch cleanup markers passed; bounded diagnostic children prove exit;
controller identities were audited after exit. Cleanup elapsed time is NOT
RECORDED separately from the operation timings. Diagnostic classes/raw failures,
new source archive and restart helpers remain in task-owned temp; no new browser,
preview listener, server or long-lived process was launched in this followup.
All prior evidence, eight existing untracked paths and Desktop sibling survive.

Root reviewed the integrated diff, source/receipt/archive bindings and original
roadmap gates. Read-only Luna MAX review scopes/results are recorded separately.
Parent owns final acceptance. The minimum eventual public enablement diff is
`PlayerFamilyCatalog`: `registerStagedFamily(new Rb30PlayerFamilyCapability(),
false)` to `true`, after remaining gates and parent review. It is not applied.
