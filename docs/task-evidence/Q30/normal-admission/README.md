# Current status: Q30 BLOCKED / NOT ACCEPTED

Independent review rejected the completion claim in `97e0727`. The
300-second normal-player budget was not authorized; the production limit
remains 90 seconds. The working implementation and historical receipts below
are preserved. Their PASS labels certify only their recorded scopes and
inputs, not current Q30 acceptance. U06, U07, Q60 and later work remain
unstarted. See the [acceptance repair](../acceptance-repair/README.md).

# Historical Q30 normal-medium qualification — COMPLETE for the qualified family scope

Base/HEAD `16a922b2b757dfcd1cbc3a0230ab2b97ac2fbb50`; branch
`codex/q30-multirail-qualification`. All Q30 qualification gates PASS for the
33/35/37-package family across all eight declared topology axes. This is a
bounded qualification sample, not a guarantee for arbitrary 20–40-part
circuits. This checkpoint records prepublication acceptance; root owns
publication. Q60 is unstarted; the next preferred milestones are U06 then U07. Full attempt history,
prior failures and nonblocking limits remain in [attempts.md](attempts.md).

The normal registration implementation catalogs 11 entries. Private schema-2
coordinator receipts remain measurement-only; their `normalAdmission=false`
and `playerPublished=false` flags do not replace or contradict the separate
normal-player browser and manual evidence below.

Current evidence:

- Final GWT21 build and reused compiled gates PASS on unchanged inputs. Alpha21
  full41/strict reader, host21, regressions21, D01-21 and coordinator21 pass for
  their recorded scopes. The [final source-input audit](final-source-input-audit-22.json)
  confirms unchanged 1,133 build, 392 web and 1,248 native inputs; performance
  statistics and sample limits are in [performance review](performance-review.md).
- Unfiltered native05 PASSed 78 Java suites and independent oracles. The run
  started `2026-09-27T02:18:14.6666262Z` and finished
  `2026-09-27T03:15:26.4869934Z`. Its 51-row corpus has 42 accepted routes,
  nine retained rejections and zero failures; 17 corruption canaries pass.
  The service and finer-step matrices each pass 40/40. The root audit reports
  12/12 checks true and unchanged 1,248 native inputs plus three reader inputs.
- The combined [P09 final22 result](p09-final-22/p09-final-22-result.json) and
  [reader result](p09-final-22/reader-result.json) PASS with actual exit0. They
  combine fresh native05 with compiled22 and controlled22: all 24 development
  and 48 holdout rows pass with geometry matches; six controlled outcomes and
  46 malformed controls pass. The 394-input compiled audits and cleanup pass.
- Modern22 passes 33/33 normal launches and 3/3 replays with zero errors.
  Manual22 passes typed QA/QB/RDA/U2B service and the correct seed13 repair;
  the intentionally wrong 12 V relay fails, and replay of the original fault
  fails with an empty tray and cleared probes. Five screenshots, the final
  1,133/392 input audit and manual cleanup pass.
- No Q30 qualification gates remain. Native05 evidence sanitization and exact
  task-temp child/parent cleanup are complete ([copy audit](native-final-05-copy-audit.json));
  no build or browser is active. This checkpoint records prepublication
  acceptance; root owns publication. The canonical corpus table and historical
  failures are preserved.
## Admission boundary

A separate value-only physical eligibility record binds an accepted
`MEDIUM_BOARD@1` route, exact placement/routing seeds, board declarations,
geometry, probes and bounded two-layer work. It does not constitute a D01
receipt or final player publication. Q30 catalog registration and staged normal
generation are separate implementation boundaries. P09 and the existing
routing/work budgets stay unchanged. The ordinary Q30 developer route remains
available.

The private normal candidate must then pass the unchanged production D01
service: five disjoint normal hypothesis owners, real CircuitJS observations,
physical replacement, actual customer retest and independently verified cleanup.
The normal observation program retains 37 measurements and declares power-off
and settlement. Provider v3 declares five bounded service-readiness units; each
queries the exact installed part's REMOVE availability and advances CircuitJS
by at most 50 ms only when unavailable. The physical mutation owner still
checks real target-pin voltage and coil current. Timing alone cannot authorize
service. Earlier build05 seeds13/7 pass all seven actual compiled readiness
canaries and full D01 proof; the independent readers also pass. The selected
adaptive solver configuration belongs to the healthy device recipe and is
restored by existing owner snapshots.

## Current source state and gate status

`PlayerFamilyCatalog` now exposes the 11-entry catalog while
`QuickPlayFamilyRegistry` retains ownership of the nine synchronous leaf
generators. Its `PLAYER_FAMILY_EXECUTION@1` declaration binds each catalog entry
to its profile, execution policy and physical-admission identity. The composed
and Q30 entries use staged `GenerationRequest` values.
Q30 normal requests carry immutable `NORMAL_MEDIUM_EXECUTION@1` with the
reviewed 300,000 ms cumulative allowance, pre-cancel request validation and
publication-time validation of the actual `MEDIUM_BOARD_NORMAL@1` token. Small
and composed requests retain their 90,000 ms allowance; all requests retain the
shared 640-unit and 5,000 ms per-unit limits. `DifficultyAssessment` v2 derives
the requested profile from admitted diagnostic evidence.

Rows explicitly marked historical, and the detailed handoffs below, record status
at their original checkpoint. The final PASS rows below supersede their old
pending/NOT RUN claims; retained failures remain part of the history.

| Boundary | Status | Evidence and limit |
| --- | --- | --- |
| Q30 catalog registration | IMPLEMENTED | Eleven source catalog entries; Q30 is present as the staged multi-rail family. |
| Normal-medium execution policy | IMPLEMENTED | `NORMAL_MEDIUM_EXECUTION@1`, 300,000 ms, bound to the actual `MEDIUM_BOARD_NORMAL@1` owner token. |
| Prerequisite corpus/service/performance gates | PASS | Native 51-row corpus, selected-census service/sensitivity, and coordinator21 measurement evidence described below and in [performance review](performance-review.md). |
| Previous maintained native run | FAIL | [native-final-01.log](native-final-01.log) stops at the RB30 cohort-1 procedural family gate; exact scratch cleanup passes and later suites are NOT RUN. |
| Previous native02 run | FAIL | [native-final-02.log](native-final-02.log) passed all 22 procedural cohorts, 220 accepted boards, 220 exact replays and compaction696, then failed unchanged `PhysicalServiceabilityContractTest` because Q30 QA/QB lacked real service owners. No final receipt was emitted; this failure remains historical. |
| Historical maintained native03 run | FAIL; later tail partial | [native-final-03.log](native-final-03.log) has no Suite filter but fails at `MediumBoardFloorplanningContractTest` on connector locality after the prior generation/physical/compaction/serviceability passes. The separate [native-tail-01.log](native-tail-01.log) passes 46 later Java suites with cleanup, but the full matrix and independent oracles were NOT RUN at that checkpoint. |
| Focused QA/QB service and removal | PASS (focused) | [native-driver-service-03-receipt.txt](native-driver-service-03-receipt.txt) passes all 37 Q30 positions (1,427 assertions), catalog transfer (219), identity (13), physical metadata (1,989) and relay (161), with cleanup and solver/model reset. The separate normal-player Modern22/manual22 QA/QB repair evidence PASSes below. |
| Focused floorplan and P09 qualification slice | PASS (focused) | [native-floorplan-p09-03-receipt.txt](native-floorplan-p09-03-receipt.txt) passes three suites and 76,556 floorplan assertions, preserving historical MST 27,630 and 72 P09 physical rows; the corrected compiled population is 72/72 with exact 72-row geometry parity. The two focused locality failures remain preserved. |
| Bounded P09 fixture discovery | PASS; no selected fixture | [p09-fixture-census-01-summary.json](p09-fixture-census-01-summary.json) retains all 128 disjoint roots: 128/128 physical passes, zero typed rejects and zero unexpected failures, `selectedFixture=null`, so no natural rejection/fixture coverage was obtained. The separate [host positive](p09-fixture-host-positive-01-summary.json) passes three exact signed-long rows and cleanup; its full matrix is NOT RUN. |
| Historical GWT15 focused Alpha and QuickPlay checks | PASS focused; Full Alpha15 FAIL | [compiled-focused-15.log](compiled-focused-15.log) ends `PASS cases 11`: Alpha LED seed 0, corrected Alpha case 13 (`alpha-npn0-shop`) and all nine QuickPlay cases pass. The completed [Alpha15 aggregate](compiled-alpha-15-result.json) fails at case 39 U04 cross-target catalog mutations after 580.656 seconds, 39 cases, 12,734 assertions and 415 acquisitions; it is not a timeout, and owner restore/cleanup/input audit pass. GWT16 then fails composed39/40 on the same U04 fixture; Full Alpha16 and the then-current reader remained pending at that checkpoint. |
| Historical GWT12 D01 seed 13 | BLOCKED_BROWSER_TRANSPORT | [Interruption receipt](compiled-d01-12-seed13-interruption.json) records the last readable `RUNNING:coldProof` state at 4/5 hypotheses and 242,559 ms; `Page.getFrameTree` timed out twice and `Page.captureScreenshot` timed out. No terminal report or owner-restoration proof was captured, so no application PASS/FAIL is inferred. Owned IAB-tab close returned an empty list. GWT15 host canaries did not supply a D01 receipt; its bounded rerun was pending then. The final D01-21 PASS is recorded below. |
| GWT13/GWT14 production build | GWT13 FAIL; GWT14 PASS | [build-final-13.log](build-final-13.log) retains the GWT compile failure at `QuickPlayGateDeveloperVerifier` line 85 (`search` is not final); its exact cleanup passes. [build-final-14.log](build-final-14.log) then compiles five permutations in 77.108 seconds and links in 1.289 seconds. This is build evidence, not a runtime or serviceability PASS. |
| Retained GWT15 production build | PASS | [build-final-15.log](build-final-15.log) compiles five permutations in 82.611 seconds and links in 1.361 seconds; its 1,133 build inputs and 392 web files are unchanged. This is build evidence, not a full runtime or Q30 acceptance result. |
| Retained GWT15 host validation | PASS for six declared canaries | [compiled-host-canaries-15.json](compiled-host-canaries-15.json) records smoke exit 0; reject, 1-second timeout and sub-millisecond timeout expected exit 1; and external-URL/null-schema negatives expected exit 2 before launch. Cleanup and input audits pass for every launched case. [compiled-host-alpha-negative-15.json](compiled-host-alpha-negative-15.json) is an independent actual Alpha-negative reader PASS. The outer Alpha capture increase from 600 to 900 seconds is documented in [compiled-alpha-host-budget-16.json](compiled-alpha-host-budget-16.json); application budgets remain unchanged. |
| Historical GWT16 production build | PASS | [build-final-16.log](build-final-16.log) compiles five permutations in 79.122 seconds and links in 1.355 seconds; its 1,133 build inputs and 392 web files are frozen. This is build evidence, not full Alpha or Q30 acceptance. |
| Historical GWT16 host and focused checks | Host PASS; focused FAIL | [compiled-host-canaries-16.json](compiled-host-canaries-16.json) matches all eight declared outcomes: smoke 0; reject/timeout/tiny-timeout expected 1; URL/null-schema/invalid-901s/sole-null-schema expected 2. [compiled-focused-16.log](compiled-focused-16.log) finishes 13 cases: 11 PASS and composed39/40 FAIL at 10.755/10.375 seconds on the same U04 no-cross-target fixture. Full Alpha16 was NOT RUN at that checkpoint. |
| Historical GWT17 production build | PASS | [build-final-17.log](build-final-17.log) compiles five permutations in 78.718 seconds and links in 1.341 seconds; its 1,133 inputs and 392 web files are unchanged from GWT16, with only the U04 verifier consumed as a source difference. |
| Historical GWT17 focused composed checks | PASS focused | Focused17 passes composed39/40 in 11.550/11.069 seconds with input audit and host cleanup PASS. The [focused17 specification](compiled-focused-17-spec.json) retains the two composed cases; Full Alpha16 was NOT RUN at that checkpoint. |
| Historical native04 maintained run | FAIL exit 2; prerequisite slices PASS | [native-final-04-result.json](native-final-04-result.json) records session70557, 1,248 unchanged input hashes and scratch cleanup PASS. Raw51 finished 42 accepted/nine routing rejects; service and sensitivity are 40/40 each. `Q30NormalExecutionPolicyContractTest.preMutationGuards` failed on the stale six-argument `GenerationCoordinator.start` reflection after the P09 hook signature change. The strict corpus reader correctly FAILed on the failed child; no corpus-reader PASS is claimed for native04. The tests-only seventh-null-argument reflection fix is delivered; production/GWT17 are unchanged. Native05 had not run at that checkpoint; its separate PASS is recorded below. |
| Retained Alpha17 aggregate | FAIL; explicit negative PASS | [compiled-alpha-17-result.json](compiled-alpha-17-result.json) stops at active case40 after 40 completed cases, 13,367 assertions, 434 acquisitions and 397 mutations at successful catalog-formation state equality. Cleanup and input audit PASS; the raw difference is unknown and an NPN transient is only a hypothesis, so this remains diagnostic evidence. Sanitized artifacts, [copy audit](compiled-alpha-17-copy-audit.json) and [cleanup receipt](compiled-alpha-17-cleanup.json) are retained; duplicate spec removal is policy-blocked and not retried. |
| Retained native-normal-policy06 and GWT19 | PASS focused/build | [native-normal-policy-06-result.json](native-normal-policy-06-result.json) passes 323 assertions with 1,248-input audit and scratch cleanup PASS; the full native matrix is NOT RUN. [GWT19](build-final-19.log) passes five permutations in 81.335/1.332 seconds with 1,133 build inputs and 392 web files unchanged. |
| Retained GWT20 build | FAIL retained; correction reviewed | GWT20 retained the unsupported `java.util.StringTokenizer` failure. The correction is package-local and does not bypass equal-NPN checks; build20/final gates are NOT RUN. |
| Current GWT21 build | PASS | [build-final-21.log](build-final-21.log) passes five permutations in 72.202/1.093 seconds with 1,133 build inputs and 392 web files audited. |
| Current Alpha21 full41 and strict reader | PASS actual | Alpha21 full41 passes in 573.614 seconds and its explicit negative passes in 0.759 seconds. [compiled-alpha-21-reader.log](compiled-alpha-21-reader.log) passes 49 malformed, two cross-version and two unknown cases. This scoped compiled gate is retained separately from final Q30 acceptance below. |
| Unfiltered native05 maintained matrix | PASS, actual exit0 | Full 78 Java suites and independent oracles pass. The 51-row corpus has 42 accepted routes, nine retained route rejections and zero failures; all 17 corruption canaries reject. Service and finer-step comparisons pass 40/40 each. The root audit reports 12/12 checks true and unchanged 1,248 native inputs plus three reader inputs. [P09 final22 root result](p09-final-22/p09-final-22-result.json) confirms the native runner and both native readers exit0. |
| Combined P09 final22 population and strict reader | PASS, actual exit0 | [Root result](p09-final-22/p09-final-22-result.json) and [reader result](p09-final-22/reader-result.json) combine fresh native05, compiled22 and controlled22. All 24 development and 48 holdout rows pass with exact geometry match; all six controlled cases and 46 malformed controls pass. The 394-input compiled audits and cleanup pass. |
| Current host/regression continuation | PASS | U04 is resolved under [source-review-21.md](source-review-21.md), whose read-only review PASS and limits remain binding. Host21 matches all eight expected outcomes; regressions21 passes all twelve, including the A10 positive/negative and all nine QuickPlay cases. |
| GWT14 host smoke and negative canaries | PASS after correction; initial smoke retained | [compiled-host-smoke-14-result.json](compiled-host-smoke-14-result.json) retains the known favicon 404 and stale negative expected-string failure. [compiled-host-smoke-14b-result.json](compiled-host-smoke-14b-result.json) passes the two corrected cases and cleanup; [compiled-host-negative-canaries-14.json](compiled-host-negative-canaries-14.json) passes reject, timeout and URL negatives as expected failures. The Alpha explicit negative is PASS in the corrected smoke receipt. |
| P09 controlled invalid-copy/retry seam | PASS; strict reader limited | [compiled-p09-controlled-15.log](compiled-p09-controlled-15.log) passes all six declared cases: typed physical rejection 1.557 s, search retry 14.108 s, replays 12.276/12.292 s and cancellations 0.759/3.070 s, with cleanup and inputs audited. The combined [reader preflight](p09-reader-preflight-15/reader-result.json) passes 46 malformed controls plus historical GWT12 native/compiled72 and current controlled6, but its [scope](p09-reader-preflight-15/scope.json) is mixed and not a final same-candidate gate. |
| Compiled A10 regression | PASS | [compiled-a10-12.json](compiled-a10-12.json), [strict reader](compiled-a10-12-reader.log) and [summary](compiled-a10-12-summary.json) report 3,128 assertions, 24 normal rows, eight D01 rows, 4 ms maximum cancellation and cleanup PASS. All 1,133 build inputs are unchanged. The [negative capture](compiled-a10-negative-12.json) verifies `FAIL:a10-explicit-failure-canary` and absence of a success report. |
| Current normal-player launch, replay, visible repair, privacy and service evidence | PASS | [Modern22](browser-modern-22-root-result.json) passes 33/33 launches and 3/3 replays with zero errors. Manual22 passes typed QA/QB/RDA/U2B service and the correct seed13 repair; the wrong 12 V relay and original-fault replay fail as expected, with an empty tray and cleared probes. Five screenshots, final 1,133/392 input audit and manual cleanup pass. |
| Final regression and integrated Q30 gates | PASS | GWT21, Alpha21, host21, regressions21, D01-21, coordinator21, unfiltered native05, P09 final22, Modern22 and Manual22 pass their declared gates. Private coordinator receipts remain `normalAdmission=false`/`playerPublished=false`; normal-player acceptance is supported by separate Modern22/manual22 evidence. |
| Full Q30 | COMPLETE for qualified scope | All gates pass for the declared 33/35/37-package family across eight topology axes. This is not a universal 20–40-part guarantee. Q60 remains unstarted; U06 then U07 are next. |

Schema-2 private verifier output is source-implemented with
`registered=true`/`normalCatalogRegistered=true`, `normalAdmission=false` and
`playerPublished=false`. The fresh schema-2 coordinator and scope-loss receipts
below are measurement-only: they do not authorize normal admission or player
publication. Historical schema-1 receipts and strict readers retain
`registered=false` and `playerPublished=false`. Those historical results remain
valid for their schema and are not rewritten to stand in for the schema-2 normal
admission gate.

## Historical production-build and normal-player evidence

The preceding-source receipt in [build-final-11.log](build-final-11.log) compiles
five GWT permutations in 75.752 seconds and links in 1.299 seconds.
[build-final-11-audit.json](build-final-11-audit.json) is PASS after auditing
1,133 unchanged input hashes. This remains a valid **pre-fix PASS** for that
input set, not a final-source result for the later QA/QB metadata correction.
The correction uses each existing relay-driver provider specification with the
actual component ID and the shared service-construction owner; the existing
CircuitJS NPN/NMOS element, model parameters and terminal posts are unchanged.
Provider call sites are migrated, and native actual-replacement coverage is
being extended. [build-final-12-inputs.json](build-final-12-inputs.json) records
the new build inputs. GWT12 has now completed successfully.

[build-final-12.log](build-final-12.log) records five compiled permutations in
81.933 seconds and linking in 1.357 seconds. The final build session exited 0.
The build manifest records 1,133 inputs, and
[web-final-12-inputs.json](web-final-12-inputs.json) records 392 web inputs.
The log reconstructs the exact compiler result lines from unified session 39975
because the initial `2>&1` capture omitted `Write-Host`; that capture limitation
is explicit in the log. This GWT12 PASS establishes compilation, not native QA/QB
serviceability or full Q30 acceptance.

The retained [GWT13 build](build-final-13.log) is **FAIL**: GWT compilation
stopped at `QuickPlayGateDeveloperVerifier` line 85 because the `search` local
was not final in the captured verifier path. Its exact build-process cleanup
passes. The subsequent [GWT14 build](build-final-14.log) is **PASS** for five
permutations in 77.108 seconds with linking in 1.289 seconds. GWT14 build
success does not establish the focused verifier, D01, host or full Q30 gates.

The current [GWT15 build](build-final-15.log) is **PASS** for five permutations
in 82.611 seconds with linking in 1.361 seconds. Its 1,133 build inputs and
392 web files are unchanged. The [six-case host receipt](compiled-host-canaries-15.json)
is **PASS** for all declared expected codes: smoke exit 0, reject/1-second
timeout/sub-millisecond timeout expected exit 1, and external-URL/null-schema
negatives expected exit 2 before launch. Cleanup and input audits pass for every
launched case. The [independent Alpha-negative reader](compiled-host-alpha-negative-15.json)
also passes. Focused15 then passes all 11 cases; the completed Alpha15 aggregate
is recorded below as a runtime FAIL, so these host and build receipts do not
establish full Alpha or Q30 acceptance.

An independent source-only provider review passes its bounded scope: metadata,
model/post mapping and Q1 migration. It makes no runtime claim. The earlier
[driver02 run](native-driver-service-02.log) remains a preserved FAIL because
its new test oracle incorrectly expected the loose-part backing to be deleted.
The existing model intentionally keeps that backing active, isolated with all
three leads disconnected for probing. The focused
[driver03 receipt](native-driver-service-03-receipt.txt) now passes the five
service/identity/metadata suites; no production change was made for the driver02
oracle correction. Cleanup and native solver/model reset pass.

Compiled [A10-12](compiled-a10-12.json) and its [strict reader](compiled-a10-12-reader.log)
PASS with 3,128 assertions, 24 normal rows, eight D01 rows, 4 ms maximum
cancellation and cleanup. First-run p50/p95 is 481/4,404 ms; warm p50/p95 is
77/1,511 ms. All 1,133 build inputs are unchanged. The
[negative capture](compiled-a10-negative-12.json) records and audits
`FAIL:a10-explicit-failure-canary` with no success report, as expected. Earlier
DOM reads timed out during synchronous execution; the final compiled A10 receipt
passes. Alpha and other final gates remain pending.

[compiled-coordinator-11-seed13.json](compiled-coordinator-11-seed13.json)
and its [independent reader](compiled-coordinator-11-reader.log) PASS as
schema-2 measurement-only output: the catalog is registered, with five
hypotheses, 10 fault pairs and 37 corruption canaries; cold elapsed time is
166,105 ms with 25,083 ms routing and 130,035 ms proof, and warm elapsed time
is 35,544 ms. The receipt records `normalAdmission=false` and
`playerPublished=false`; cleanup and predecessor restoration pass. The
[scope-loss receipt](compiled-scope-loss-11.json) is the expected negative:
injected scope loss cancels the job, restores the predecessor and completes
cleanup; its reader passes the 13 corruption canaries. Its receipt status is
the injected negative outcome; the reader records the expected cancellation and
restoration as PASS, not as a successful admission result. These private
verifiers remain measurement-only and do not authorize normal admission.

Current [compiled-coordinator-12-seed13.json](compiled-coordinator-12-seed13.json)
and its [reader](compiled-coordinator-12-reader.log) PASS as measurement-only
schema-2 evidence: cold 165,306 ms, warm 36,074 ms, five hypotheses, 185 live
readings, 10 fault pairs and 37 corruption canaries. The receipt retains
`normalAdmission=false` and `playerPublished=false`, with cleanup and owner
restoration PASS. [compiled-scope-loss-12.json](compiled-scope-loss-12.json)
is the expected negative: cancellation and predecessor restoration pass with
13 corruption canaries. These receipts do not qualify normal publication.

The corrected [Alpha reader manifest](alpha-reader-current-12-input.json)
declares 41 current protocol-2 cases, exact SensorControl 0/1/2 service
positions 9/10/9 and 43 current canaries. The historical protocol-1 reader
retains its 38-case evidence. The first current GWT12 Alpha LED seed-0
`catalog-acquire/R1` attempt is a preserved **FAIL** in
[compiled-alpha-12-failure-01.json](compiled-alpha-12-failure-01.json): its
`LoosePart` geometry assertion assumes the package must be inspectable. Audit
found valid unformed stock already present since source `0018f3d`, with
canonical `LoosePartPose` geometry; the verifier correction is running. The
41-case Alpha runtime has no PASS until that correction is rerun. The
manifest's parser evidence remains synthetic and does not replace the runtime
gate.

The earlier Alpha2 reader remains a legacy-epoch census of nine mutation
providers and 310 rows plus its shop-geometry census. Current Alpha first39
runtime evidence includes the E04/regulator 397-row matrix, NPN0 SPAN220/260
coverage and three composed0 variants. The `q30_alpha_reader_census` correction
is rebuilding the current-epoch reader from independent contracts and fixtures;
the legacy epoch remains preserved. Alpha15's runtime FAIL is therefore not a
reader PASS.

The current GWT12 QuickPlay LED family-0 seed-3 check is also a preserved
**FAIL** in [compiled-quickplay-12-failure-01.json](compiled-quickplay-12-failure-01.json):
completed removal unexpectedly succeeds. The verifier predates `8b371d9`, whose
intentional `COMPLETED` interactive policy permits that result; its correction
is running. No QuickPlay PASS is claimed until the corrected verifier reruns.

The retained [GWT14 focused verifier](compiled-focused-14.log) is **FAIL**
overall across 11 cases. Alpha LED seed 0 passes, Alpha case 13
(`alpha-npn0-shop`) fails on a stale span fixture, and all nine QuickPlay cases
pass. This historical partial receipt is retained; GWT15 is the current focused
run and final Q30 acceptance remains pending.

The current [GWT15 focused run](compiled-focused-15.log) is **PASS** for all 11
cases: Alpha LED seed 0, corrected Alpha case 13 (`alpha-npn0-shop`) and all
nine QuickPlay cases. The completed [Alpha15 aggregate](compiled-alpha-15-result.json)
is **FAIL**, not a timeout: it reached case 39 after 580.656 seconds and failed
U04 cross-target catalog mutations after 39 cases, 12,734 assertions and 415
acquisitions. Owner restore (3 ms), cleanup and input audit pass. Root fixed the
Alpha-negative path so eligibility and provider installation both execute; the
U04 composed-fixture correction remains in progress. GWT16 focused execution
below passes the 11 Alpha/QuickPlay cases and fails composed39/40 on that same
fixture; Full Alpha16 is not run. Independent source review found wrong-fit and
wrong-type cases plus an assertion short-circuit before the actual
`target.install` call. Semantic unequal canonical geometry is intentional, and
the literal-RB260/RLOAD220 requirement was rejected as out of scope. No
production behavior change or full Alpha acceptance is claimed.

The current protocol-2 reader unit evidence covers 49 malformed, two
cross-version and two unknown cases. The historical actual protocol-1 reader
retains 38+2+2. Focused16's independent geometry helper passes NPN0 SPAN220/260
and composed0/3 SPAN220/240/260; this does not establish a full actual
protocol-2 reader PASS. The current-epoch reader and U04 fixture remain pending.

[normal-player-10.json](normal-player-10.json) and its five screenshots record
PASS for the normal menu and exact seed-13 replay, complaint privacy, live
4.85 V and 0 V readings, RDA isolation/lift/reconnect/remove, the original
reinstall failure, and the replacement 1-kohm RDA pass. Exact replay restores
the fault and an empty tray. The observed shots include 58.093 mV to 42.6 pV
and 462.049 mV to 158.738 nV. The plain normal URL has no private attributes.
Browser retest click acknowledgements timed out after delivery, so the actual
DOM outcomes were read after each click. The completed-job retest was disabled
for the initial trial and verified in the fresh replay final combined trial.
The Q30 silkscreen and About copy are corrected. The five screenshots are
[ticket](normal-player-10-01-ticket.png),
[unrepaired measurement](normal-player-10-02-unrepaired-measurement.png),
[isolated board](normal-player-10-03-isolated-bottom.png),
[new stock](normal-player-10-04-new-stock.png), and
[repaired result](normal-player-10-05-repaired.png).

[normal-transfer-11.json](normal-transfer-11.json) and its three screenshots
also PASS: stock U2A part 39 transfers to U2B, the new 1-kohm RDA is installed,
the wrong 12 V relay part 40 at KB fails, and the correct 5 V relay part 41
passes all customer inputs. The transfer trial used a fresh replay before the
final combined completion. Its cleanup records power OFF, Main menu and the
owned tab closed. The only nonblocking UI availability refresh was waiting for
real relay discharge and reselecting the component before Remove became
available; no guard was weakened and no solver failure occurred. Its
[controller](normal-transfer-11-01-controller.png),
[wrong relay](normal-transfer-11-02-wrong-relay.png), and
[passed result](normal-transfer-11-03-passed.png) screenshots are retained.
Retest acknowledgements again timed out after delivery; subsequent DOM state
established each actual result.

## Historical final-gate runs and retained failures (current status is above)

The fresh maintained native run in [native-final-01.log](native-final-01.log)
is **FAIL** at `ProceduralFamilyContractTest` for `RB30_CONTROL` cohort 1.
It accepted 5/8 raw exact-seed cases against the required minimum of 6, with
the recorded macro count at 5. Cohort 0 accepted 6/8 and passed its gate. The
older ten families pass in both cohorts. Every raw RB30 rejection and its
route or placement message remains in the log; exact task scratch cleanup
passes, and the later native suites are **NOT RUN**. This failed receipt stays
preserved; the later final-source audit and independent review resolved the
legacy fingerprint issue described below without changing this result.

The maintained modern browser run in [browser-modern-01-result.json](browser-modern-01-result.json)
is **PASS** for 33 real launches across 11 families and three exact replays,
with privacy checks, no JavaScript errors, a 0.406-second cleanup, the owned
server stopped and no owned process survivors. The Q30 launches are seed
`9082502577921131578` (172.0396 s, observed candidate labels 0/1/2, maximum
548 units), `7817683248775161701` (138.7494 s, label 1, maximum 459 units),
and `-6007545791683042230` (161.9749 s, label 1, maximum 499 units). The Q30
exact replay took 47.3952 s; the relay and composed replays took 7.2740 s and
4.5040 s. The three inspected Q30 screenshots are [first top](browser-modern-01-rb30_control-0-top.png),
[first bottom](browser-modern-01-rb30_control-0-bottom.png), and
[third top](browser-modern-01-rb30_control-2-top.png).

All 393 current inputs were verified: 392 pre-run web hashes plus the browser
script hash captured during the run. The script mtime was 18:34, before the
19:29 launch; [browser-modern-01-inputs.json](browser-modern-01-inputs.json)
records the limitation that this script hash was captured during the run and
was not retroactively claimed as a launch-time hash. The identity and owned
process receipts are [browser-modern-01-identity.json](browser-modern-01-identity.json)
and [browser-modern-01-owned-processes.json](browser-modern-01-owned-processes.json).

The replacement maintained native run [native-final-02.log](native-final-02.log)
completed from [native-final-02-inputs.json](native-final-02-inputs.json), which
records 1,246 input hashes and no Suite filter. It is **FAIL**, with no final
receipt, at `PhysicalServiceabilityContractTest`. All 22 procedural cohorts
passed; Q30 passed both 8/8 cohorts with eight geometry classes and eight macro
cases per cohort, and all 16 exact candidate replays passed. The full physical
matrix passed 220 accepted boards plus 220 exact replays. Q30 had 20 accepted
boards and six retained route rejections among 26 roots; 175,160 ms is the
accepted generation/replay aggregate and excludes rejected-root timings. PCB
compaction passed 696 assertions. The serviceability failure is limited to Q30
QA/QB, which lacked real catalog/service owners in that run. The focused
[service-coverage run](native-service-coverage-01.log) confirms 35/37 owners on
seed 13, with QA and QB missing; it retains the unchanged assertion failure and
passes exact scratch cleanup.

The earlier source audit found that a legacy fingerprint omitted pads and vias.
Q30 replay now uses `PhysicalBoardFingerprint` plus `EXACT` layout geometry, and
independent review confirmed that correction before native-final-02. That run's
procedural and physical results above remain valid, while its later serviceability
failure remains a failure. GWT12 and focused driver03 pass the QA/QB metadata
correction; the native03 repair census and fresh normal/manual replacement
validation remain pending.

Earlier final build, normal-transfer, coordinator and scope-loss evidence
remains valid and preserved.

The current [native-final-03 run](native-final-03.log) is **FAIL** with no Suite
filter and 1,248 recorded input hashes. Generation passed 22 cohorts and 176
rows; physical coverage passed 220 accepted boards and 220 exact replays, with
compaction and serviceability passing before `MediumBoardFloorplanningContractTest`
failed on connector locality. The separate [native-tail-01 run](native-tail-01.log)
then passed 46 later Java suites with exact cleanup, but the full matrix and
independent oracles are **NOT RUN**. The fixed 51-row raw corpus and all prior
native failures remain unchanged.

The literal historical P1 graph used by the focused floorplan work is frozen
from source commit `fac582c1150273c01d09bc704dd796c1bf49a135` and plan blob
`37191a3d291b6eb1fc438187a3dec27759a411e1`; it has no `RB30_Plan` dependency.
The 300/28,000 oracles, geometry/access/replay/JLOAD checks and generic-20
coverage remain unchanged. Current 33/35/37 recipes retain all 18 candidates.
The focused [locality-01 failure](native-floorplan-locality-01.log) and
[locality-02 failure](native-floorplan-locality-02.log) remain preserved.

The focused [native-floorplan-p09-03 receipt](native-floorplan-p09-03-receipt.txt)
is **PASS** for three suites, 76,556 floorplan assertions, historical MST
27,630 and 72 P09 physical rows, with exit 0, exact cleanup and 1,248 unchanged
input hashes. The native corpus fix applies the existing P09 envelope during
bounded candidate selection as production does; the raw `P09EnvelopeCorpus`
remains untouched. The compiled [P09 population](compiled-p09-12-population-result.json)
is **PASS** for 72/72 rows (24 development and 48 holdout), and
[compiled-p09-12-parity.json](compiled-p09-12-parity.json) records exact 72-row
geometry parity with native cleanup PASS. The old v2/layout-12 canary remains
the unchanged actual FAIL (exit 1); its first case now passes under current
v3/layout-13, while the other five cases are **NOT RUN**. The controlled
[old-canary artifacts](compiled-p09-12-oldcanary-result.json) remain available;
the full P09 canary reader is still pending.

The bounded [P09 fixture census](p09-fixture-census-01-summary.json) is **PASS**
with all 128 predeclared roots retained, 128/128 physical passes, zero typed
physical rejections and zero unexpected failures, but `selectedFixture=null`.
It is disjoint from the fixed 72-row qualification population and therefore
obtained no natural rejection or fixture coverage. The separate [three-seed
host positive](p09-fixture-host-positive-01-summary.json) is **PASS** with exact
signed-long extremes, preserved indices and cleanup; its full matrix is **NOT
RUN**. These host/fixture results do not claim a controlled invalid copy,
electrical admission or relaxed production budget.

The P09 controlled invalid-copy and coordinator-retry seam now has an actual
[GWT15 receipt](compiled-p09-controlled-15-result.json) **PASS** for six
declared cases: typed PHYSICAL rejection in 1.557 s, search in 14.108 s, exact
replays in 12.276/12.292 s, and cancellations in 0.759/3.070 s. Cleanup and
input audits pass; the 394 GWT15 web/driver/spec inputs are unchanged and the
source edits were not consumed. One unattributed console 404 is retained, with
no JS or HTTP response errors. The strict-reader preflight initially failed
because it required injected `search` control and then included `search` in the
uninjected exclusion loop. The corrected tuple exclusion is narrow: the actual
controlled six plus historical GWT12 native/compiled72 pass the 46-malformed
reader set. Its [scope](p09-reader-preflight-15/scope.json) is mixed and is not
a final same-candidate gate; fresh P09 population/native evidence remains
pending. Production budgets are unchanged.

The normal trial ended with tabs closed, board power OFF and Main menu visible.
Preview09 was stopped with its identity verified and port 8904 released. The
previous root-owned Preview10 process (PID 38552, start ticks
`639260496502974912`, parent PID 36504, start ticks `639260496453915199`) was
stopped and port 8904 released. Preview11 was then stopped. Preview12 was
stopped with PID 37364 and port 8904 released; its sanitized [provenance](preview-12-provenance.json)
and `.tools/preview/state.json` retain the run identity and final state.
The
focused native08 receipt is **PASS**
with exact cleanup; its earlier fixture-only failures in 05 (GWT widget), 06
(assumed U2A source) and 07 (UI reset NPE) remain preserved as failure history.
The maintained native history has three preserved failures: native-final-01 at
the Q30 exact-cohort gate, native-final-02 at physical serviceability, and
native-final-03 at floorplan locality. Its separate native-tail-01 slice passes
46 later suites with cleanup, but is not a full-suite result. The focused P09
native and compiled population slices pass as described above. The
modern browser run is **PASS** for 33 launches across 11 families plus three
exact replays on its tested build inputs. GWT15, focused driver03, compiled A10,
coordinator12/scope-loss12 measurement receipts and focused P09 are also
**PASS** for their stated contracts. GWT15 focused execution is **PASS** for
all 11 cases. Full Alpha15 is **FAIL**, not a timeout: it stopped at case 39
after 580.656 seconds on U04 cross-target catalog mutations, after 12,734
assertions and 415 acquisitions; owner restore, cleanup and input audit pass.
The U04 composed-fixture and current-epoch reader corrections remain pending.
A bounded GWT13/GWT14 D01 rerun after the GWT12
browser-transport block, fresh normal browser/manual QA/QB repair, the final
unfiltered native04 run, final review and
Q30 acceptance remain **PENDING**. Full Q30 remains **BLOCKED**; Q60 is unstarted
and no commit or push has occurred.

The browser run's owned server/processes and listener cleanup **PASS**, but its exact
temporary output/profile directory was not removed. The cleanup receipt records
`BLOCKED_DIRECTORY_REMOVAL`: automatic approval review rejected both verified
PowerShell deletion attempts; the tool returned only `blocked by policy`, so no
alternate deletion was tried. This resource limit is separate from the browser runner's
reported process and server shutdown. The retained directory path pointer is
`.tools/q30-modern-01-path.txt`. See
[browser-modern-01-cleanup.json](browser-modern-01-cleanup.json).

The task-created Alpha bytecode file also remains because exact removal was
blocked by automatic approval review: [bytecode-cleanup-12.json](bytecode-cleanup-12.json)
records `BLOCKED_FILE_REMOVAL` for
`tests/contracts/__pycache__/alpha_evidence_contract.cpython-313.pyc`. It is
excluded from staging. The tool returned only `blocked by policy`; no bypass or
alternate deletion mechanism was used.

The two recent P09 profiles have separate cleanup receipts: both are **PASS**
with the directory absent, zero process references and zero listeners in
[p09-cleanup-12.json](p09-cleanup-12.json). The older modern01 directory and
Alpha bytecode remain policy-blocked as documented above; the user’s requested
remove-or-retain choice for those two resources has not been answered.

These fresh receipts are additive. All frozen corpus rows, historical
schema-1 evidence, GWT08 cleanup failure and excluded timings, the earlier
90-second timeout, rejected numerical candidates and failed reader attempts
remain preserved below and in this directory. All normal and final gates remain
**PENDING** for final regression, normal publication and milestone acceptance;
the ordinary normal-player evidence above is PASS. Full Q30 remains
**BLOCKED** until the outstanding final gates complete. The final policy is
unchanged.

## Corpus fixed before routing

Plan epoch3 adds a real 1-kohm pull-down at each raw sensor input. The source
model cannot sink feedback current; these parts make LOW behavior independent
of source reverse leakage while preserving the series sensor fault path.
The support concern now selects a functional 33-package board without its
optional power indicator, the 35-package reference arrangement, or a 37-package
arrangement with two real 100 nF sensor-input filters. Existing driver/reference,
fault, placement and routing streams remain independent. The current plan epoch
records the support choice; the eight existing driver/reference axes are retained.
These sizes alone do not establish arbitrary 20–40-part circuit support.

Each cell below was selected only by immutable support/topology derivation,
before any routing outcomes. D/H means separate direct/shared hysteretic
reference; B/N means BJT/NMOS for channels A and B.

| Support | Population | D-BB | D-NB | D-BN | D-NN | H-BB | H-NB | H-BN | H-NN |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Compact 33 | Representative | 0 | 1 | 15 | 14 | 44 | 8 | 10 | 12 |
| Standard 35 | Representative | 48 | 35 | 6 | 18 | 43 | 93 | 20 | 64 |
| Filtered 37 | Representative | 56 | 13 | 4 | 11 | 2 | 3 | 19 | 9 |
| Compact 33 | Held-out | 7 | 42 | 24 | 21 | 75 | 22 | 105 | 27 |
| Standard 35 | Held-out | 53 | 50 | 16 | 25 | 60 | 100 | 59 | 84 |
| Filtered 37 | Held-out | 70 | 41 | 45 | 23 | 5 | 38 | 40 | 26 |

Additional exact signed-long replay seeds are `-9223372036854775808`,
`9223372036854775807`, and `9007199254740993`. Every attempted row, including
rejection, timeout and cleanup failure, must remain in the census.

The initial full service matrix proposed seeds `0,35,4,14,43,3,10,64`: one of each
driver/reference axis and all three support variants, each with all five actual
faults. This is 40 cases, with the maintained 60-second per-child limit. No
successful replacement or original-part reinstall can alter the accepted
board/pad/copper identity.

The historical epoch2 structural run rejected seed 35. Its five service cases were NOT RUN
because it has no accepted layout; the rejection remains in the corpus. The
service matrix uses already-declared, accepted seed 13 for that topology axis:
`0,13,4,14,43,3,10,64`. This selection does not remove seed 35 or retry its routing.
The added physical pull-downs require a fresh epoch3 census over these same51
seeds. No epoch2 route or service outcome is assumed for the changed circuit.
Historical epoch2 rows, summary and failed experiments remain preserved.

The full epoch3 census has42 accepted routes and nine retained rejections:
`0,48,2,9,24,22,50,16,59`. The current service/numerical selection is
`7,13,4,14,43,3,10,64`, each with all five faults. Already-declared held-out
seed7 replaces rejected seed0 for the same compact direct BJT/BJT axis. Seed0
has five service cases NOT RUN because routing rejected; no retry or route-cap
change is used. The selected eight rows cover every driver/reference axis and
all three functional support sizes. All40 current service cases and40 finer-step
comparisons pass; the selected-census reader passes with full matrix explicitly
NOT RUN. The independent reader validates cleanup and rejects corruption, scope
and incomplete-census canaries. Numerical reference is2.5us versus production5us;
67,285 assertions pass. The worst deviation is6.175mV versus15.972mV tolerance
(seed14/DREV_OPEN/faulted LOW U1 output),38.66% of the bound. No tolerance or
child budget changed. See native-current-census-01-summary.json.

## Current epoch3 structural results

`native-corpus-02.log` passes51 row contracts:42 accepted, nine routing rejections,
zero failed rows. The independent reader validates the complete census, current
plan epoch, exact replay, allfive structural hypotheses and37 observations per
accepted board; all17 corruption canaries reject. Task-owned scratch cleanup passes.
Accepted native phase timing, nearest-rank p50 / p95 (42 successful samples):

| Phase | Cold | Warm |
| --- | --- | --- |
| Electrical construction | 50.9 / 53.6 ms | 2.8 / 3.2 ms |
| Placement | 224.6 / 259.0 ms | 112.1 / 121.8 ms |
| Routing | 4.79 / 6.02 s | 4.17 / 5.61 s |
| Normal assembly | 44.1 / 50.1 ms | 23.2 / 30.9 ms |

Five-hypothesis structural replay takes231.5 /273.8 ms. Rejected rows and their
latencies are separate in `native-corpus-02-summary.json`; no rejected latency is
included above. This structural run does not execute CircuitJS diagnostic proof
or certify the normal browser deadline.

## Historical intermediate results

The second compiled normal D01 canary passes for seed 0, with five hypotheses,
185 live measurements, actual physical repair/customer retest and restoration.
Its fresh-owner warm value-cache receipt and all five negative cases pass. The
independent reader checks all ten distinct fault pairs and rejects 17 corrupted
receipts. Cold proof took 165.95 seconds (162.97 seconds active work); this does
not qualify the normal generation deadline. Both compiled canaries use the
intermediate 5-us solver build. The actual coordinator canary exceeded its
unchanged 5-second work-unit limit during construction, before diagnostic proof;
cleanup and predecessor restoration passed. Generic routing resumption and
bounded profile work are under validation. The 25-us numerical candidate failed
the regulator input envelope on seed 4 and was rejected. Production remains at
5 us; 15/25-us experiments were rejected. The complete 10-us comparison also
rejects that candidate: 22 paired passes, seven production-reference failures,
and eleven experimental-candidate failures across all 40 requested cases.
Those historical reference failures exposed two causes: negative unpowered regulator
trials after REN replacement on seeds13/4, and feedback holding seed3 above its
LOW input. The current bounded Newton change and physical pull-downs pass the
focused seed3/13 REN checks and the full selected40-case service/40-case finer-step
census. Owner and maintained scratch cleanup pass.
`native-step-matrix-01-summary.json` retains the complete failed census.
The historical plan-epoch2 native structural census passed 51 row contracts: 49 accepted layouts,
two routing rejections (seeds 1 and 35), no row-contract failures. All eight
topology axes, three support arrangements and signed-long extremes are covered.
Each accepted row checks five disjoint normal structural replays. The independent
reader rejects all 16 corruption canaries, and maintained scratch cleanup passes.
These results do not include live diagnostic/service proof for all 49 layouts.

Accepted native structural phase timing, nearest-rank p50 / p95:

| Phase | Cold | Warm |
| --- | --- | --- |
| Electrical construction | 50.2 / 57.5 ms | 2.7 / 3.2 ms |
| Placement | 219.9 / 253.0 ms | 107.5 / 126.2 ms |
| Routing | 3.80 / 5.47 s | 3.20 / 4.74 s |
| Normal assembly | 41.8 / 57.5 ms | 23.2 / 26.9 ms |

Five-hypothesis structural replay takes 207.6 / 240.6 ms. Rejection timings and
full route failures are separate in `native-corpus-01-summary.json`. These native
measurements do not certify browser execution or normal generation budgets.

## Final native, compiled-population, normal-player and performance evidence

Unfiltered native05 passes all 78 Java suites and independent oracles. Its
51-row epoch3 corpus has 42 accepted routes, nine retained routing rejections
and zero failures; the strict corpus reader and 17 corruption canaries pass.
The selected service matrix passes 40/40 cases, and the 2.5-us finer-step
comparison passes 40/40 against the production 5-us step across the eight
topology axes, three supports and five faults. The largest deviation is
6.175 mV against a 15.972 mV tolerance (38.66%); no tolerance, child budget or
timestep changed. The run started `2026-09-27T02:18:14.6666262Z` and finished
`2026-09-27T03:15:26.4869934Z` (57m11.820s wall interval). Native root checks
pass 12/12; 1,248 native inputs and three reader inputs are unchanged. The
service and corpus readers report exit0 in the [P09 final22 root result](p09-final-22/p09-final-22-result.json).
Structural measurements remain separate from compiled solver proof and do not
constitute a browser timing guarantee.

The [coordinator21 receipt](compiled-coordinator-21.json) records the actual
cohort `13,7,64`: every seed has a complete cold run and a fresh-owner warm run
under the isolated measurement allowance. Its [strict reader](compiled-coordinator-21-reader.log)
passes 30 electrical pairs and 37 corruption canaries; all three cold and warm
outcomes are PASS with no failures in this sample. The cold path executes all
five hypotheses, 185 live readings, physical repair and customer retest through
390 explicit-completion proof units. Warm runs reuse only the immutable proof
value on a fresh electrical owner; their proof phase is 0 ms, while
construction and routing remain measured. Earlier failures remain retained
outside these successful-cohort statistics.

| Phase | p50 | p95 |
| --- | --- | --- |
| Cold total | 120.635 s | 127.275 s |
| Cold routing | 20.005 s | 20.338 s |
| Cold proof | 95.410 s | 99.335 s |
| Warm total | 26.756 s | 27.715 s |
| Warm routing | 19.549 s | 20.109 s |
| Warm proof (cache reuse) | 0 s | 0 s |

These three-row p95 values are sample maxima, not population-tail estimates,
universal performance statistics or a service-level objective. The independent
budget review **APPROVED** the `NORMAL_MEDIUM_EXECUTION@1` 300,000 ms allowance
from this `n=3` cohort and the same-build receipts. The receipts remain private
measurement-only (`normalAdmission=false`, `playerPublished=false`) and do not
authorize normal admission or publication. The allowance is cumulative across
candidates and foreground time, with terminal timeout and exact cleanup; it
does not imply an arbitrary-seed or slower-hardware guarantee. Small and
composed requests retain 90,000 ms, 640 shared units and 5,000 ms per unit,
with existing candidate, router, via and D01 limits unchanged. See the complete
phase/work-label tables in [performance review](performance-review.md).

The [combined P09 final22 result](p09-final-22/p09-final-22-result.json) and
[strict reader result](p09-final-22/reader-result.json) PASS with actual exit0.
The combined helper binds fresh native05 to compiled22 and controlled22, verifies
all 24 development and 48 holdout rows accepted with geometry match, all six
controlled outcomes and 46 malformed controls, and confirms the input and
cleanup checks. The compiled population manifest has 394 unchanged inputs;
server thread stopped and no owned survivors. P09-21's host-restart interruption,
38 saved PASS rows and shutdown UNVERIFIED state remain preserved in
[attempts.md](attempts.md); they are not credited to P09 final22. The compiled-
P09-22 run log separately retains one unattributed initial console404.

Modern22 passes 33/33 normal launches and 3/3 replays with zero errors on
unchanged GWT21 build/web inputs. Its three Q30 cold runs measured
121.014/152.252/106.760 s and exact RB30 replay measured 20.242 s. Manual22
behavior, final 1,133/392 input audit and cleanup PASS on seed13: typed
QA/QB/RDA/U2B service and the correct 5 V relay replacement pass; the wrong
12 V relay and replay of the original fault fail as expected, with empty tray
and cleared probes. Five screenshots were root-viewed. These are separate
normal-player proof from the private coordinator timings above.

No Q30 qualification gates remain. Native05 evidence sanitization and exact
task-temp child/parent cleanup are complete ([copy audit](native-final-05-copy-audit.json));
no build or browser is active. The final source-input audit is
[PASS](final-source-input-audit-22.json). This checkpoint records prepublication
acceptance; root owns publication. Q60 remains unstarted; U06 then U07 are the
next preferred milestones.

## Historical remaining-gates handoff (superseded by final Q30 PASS — 2026-09-27)

The following checkpoint text preserves the failures and pending work that were
true at the time; it is not the current Q30 status summarized above.

Ordinary normal-player launch/replay, privacy, visible input and
service/replacement/customer-retest evidence is **PASS on its tested build**.
GWT15 production compilation is **PASS** after the retained GWT13/GWT14 build
history; GWT16 production compilation is also **PASS** in 79.122 seconds with
1.355-second linking and frozen 1,133/392 build/web inputs. Focused driver03,
compiled A10, coordinator12 and scope-loss12 are **PASS** for their stated
contracts. The retained GWT14 verifier failed on
Alpha case 13's stale span fixture, although Alpha case 0 and all nine
QuickPlay cases passed. GWT15 focused execution is **PASS** for all 11 cases.
Full Alpha15 is **FAIL**, not a timeout: it stopped at case 39 after 580.656
seconds on U04 cross-target catalog mutations, after 12,734 assertions and 415
acquisitions; owner restore, cleanup and input audit pass. The U04 composed-fixture
and current-epoch reader corrections remain in progress. GWT16 host validation
is **PASS** for all eight expected codes/outcomes; its focused run is **FAIL**
after 13 cases, with Alpha0/13 and all nine QuickPlay cases passing and
composed39/40 failing in 10.755/10.375 seconds on the same U04 fixture. The
actual diagnostic loose stock is source-formed. GWT17 then compiles five permutations in 78.718 seconds with
1.341-second linking and passes focused composed39/40 in 11.550/11.069 seconds;
its input audit and host cleanup pass, and only the U04 verifier differs from
GWT16. The U04 fix accounts for old 10-ohm stock already formed by earlier
service, searches all candidates and real three-slot triples, uses a fresh
catalog source-owned portable-stock fallback, performs real target formation and
restores power. Full Alpha16 is **NOT RUN**. The GWT15 corrected host smoke and
expected-negative canaries **PASS** for their declared outcomes. The focused P09 native and compiled 72-row
population/parity slices are **PASS**; the actual controlled15 six-case seam
also **PASS**es with the strict reader's mixed-scope limitation described below.
The browser receipts predate the QA/QB metadata correction, so fresh normal
browser/manual QA/QB repair remains pending.
Native-final-03's floorplan failure, native-tail-01's partial 46-suite PASS,
native-final-02's serviceability failure and the driver02 oracle failure remain
preserved. Native04 session70557 is **FAIL** exit 2 and its tests-only fix is
delivered. GWT20 retains the unsupported-`StringTokenizer` FAIL with the
package-local correction and no equal-NPN bypass. GWT21 and Alpha21 full41/
strict-reader evidence PASS for their scopes; U04 is resolved under
[source-review-21.md](source-review-21.md), whose read-only review PASS and
limits remain binding. Native-normal-policy06 is PASS for 323 assertions with
1,248-input audit and cleanup PASS; full native05 is NOT RUN. Host21’s eight
canaries are next, followed by regressions21 session61405. Full Q30 remains
BLOCKED and no publication is claimed.
The corrected Alpha
reader manifest is ready, but its earlier GWT12 LED seed-0 acquisition and
QuickPlay LED seed-3 checks remain preserved stale-verifier failures. The retained
GWT14 focused run passed Alpha case 0 and all nine QuickPlay cases but failed
Alpha case 13 on a stale span fixture. The current GWT15 focused run passes all
11 cases; Alpha15 then completed **FAIL** at case 39 after 580.656 seconds on
U04 cross-target catalog mutations. GWT16 focused execution now passes the same
11 Alpha/QuickPlay cases and fails composed39/40 on that fixture; Full Alpha16
and the current-epoch reader remain pending.
Current GWT12 D01 seed 13 is
**BLOCKED_BROWSER_TRANSPORT** in [its interruption receipt](compiled-d01-12-seed13-interruption.json):
the last readable state was `RUNNING:coldProof` at 4/5 hypotheses and
242,559 ms; two `Page.getFrameTree` timeouts and a screenshot timeout ended the
transport. No terminal report or owner-restoration proof was captured, and no
application PASS/FAIL is inferred. Owned IAB-tab close returned an empty list.
A bounded host-DOM-attribute driver rerun remains pending after the GWT12
transport block; the GWT15 build and host canaries do not supply a D01 receipt.
The schema-2 private verifier source state records catalog registration
separately from normal publication. Measurement-only schema-2 coordinator and
scope-loss receipts exist, but schema-2 normal-admission runtime and publication
remain **NOT RUN**. Historical GWT08 cleanup failure, earlier 90-second
coordinator timeout, rejected numerical candidates, failed reader attempts and
other unsuccessful rows remain preserved and excluded from successful timing
tables. The P09 controlled-invalid-copy and actual-coordinator retry seam now
has six actual GWT15 cases: typed rejection, search retry, two exact replays and
two cancellations, all with cleanup and input audit PASS. The combined strict
reader passes its 46 malformed controls plus historical GWT12 native/compiled72
and controlled6, but its mixed scope is not a final same-candidate gate. No
natural rejection claim or production budget relaxation follows from the
128-root census; fresh P09 population/native evidence remains pending. Full Q30
remains **BLOCKED** pending Full Alpha16/current-reader completion, corrected D01, native05 after the native04 FAIL, fresh
browser/manual repair, final same-candidate P09 evidence and final review.

Historical service-slice evidence, failed attempts and the earlier crashed-tab
cleanup limitation remain in the adjacent `service-flow` directory.

## Historical final-gate continuation (superseded by final Q30 PASS — 2026-09-27)

The continuation below preserves the earlier pending-gate handoff as it stood
at that checkpoint; final outcomes are recorded above.

Typed QA/QB metadata now passes focused `native-driver-service-03`: five suites
cover all 37 Q30 serviceability positions (1,427 assertions), catalog transfer
(219), identity (13), physical metadata (1,989) and relay behavior (161).
Owner, graph, power, capability and slot cleanup pass, as does native
solver/model reset. Loose transistor backing remains active for probing; all
three leads disconnect and reconnect to the selected part. This corrects the
driver02 test oracle without another production change.

GWT12 coordinator seed 13 cold/warm 165,306/36,074 ms passes strict 10-fault-pair
and 37-corruption evidence. Scope-loss12 verifies expected cancellation and
predecessor restoration with 13 corruption canaries. These private measurement
receipts do not claim normal-player publication. Native-final-03 is **FAIL** at
floorplan locality; native-tail-01 passes 46 later suites with cleanup, but the
full matrix and independent oracles are **NOT RUN**. The focused P09 native
floorplan and compiled 72-row parity evidence is **PASS** with exact cleanup.
The bounded fixture-census tooling is explicitly selected-suite-only: all five
negative host preflights pass with expected exit 2 before Java or scratch, the
three-seed positive exact-long-host canary is **PASS** with cleanup, and the
128-root census [summary](p09-fixture-census-01-summary.json) is **PASS** with
128/128 physical passes, zero rejections, zero unexpected failures and
`selectedFixture=null`. The census is disjoint from the 72-row population and
obtained no natural fixture coverage; the
[predeclared specification](p09-fixture-census-01-spec.json) remains the source
of its bounded 1..128 selection. The
[negative preflights](p09-fixture-host-negative-01.json) and
[positive canary](p09-fixture-host-positive-01-receipt.txt) are retained as
separate host-tooling evidence.
The P09 controlled invalid-copy/retry seam now has six actual GWT15 cases with
expected rejection, retry, replay and cancellation outcomes, cleanup and input
audit PASS. Its strict-reader preflight passes the 46-malformed set plus
historical GWT12 native/compiled72 and controlled6; the mixed scope is not a
final same-candidate gate. The corrected current Alpha reader is not consumed
by the native runner. GWT20 remains a retained unsupported-`StringTokenizer`
FAIL with the package-local correction and no equal-NPN bypass. GWT21 and
Alpha21 full41/strict-reader evidence PASS for their scopes; U04 is resolved
under [source-review-21.md](source-review-21.md), whose read-only review PASS
and limits are retained.
Native-normal-policy06 remains PASS for 323 assertions with 1,248-input audit
and cleanup PASS; full native05 is NOT RUN. Host21’s eight canaries are next,
followed by regressions21 session61405. Native04 session70557 remains retained
as FAIL exit 2 with its strict corpus reader FAIL; the full Q30 matrix and
remaining final gates are still pending. Fresh normal-browser and visible QA/QB repair checks
remain pending. GWT17 source/build evidence is current; prior GWT16/GWT15
evidence is retained. Preview11 and Preview12
are stopped and port 8904 is released. Preview12 state is retained in
`.tools/preview/state.json`. The frozen
corpus manifest remains EXACT and is parsed by [check_corpus.py](check_corpus.py).
Prior build11 normal evidence remains retained with its source boundary. Final
documentation, push and notification remain pending.
