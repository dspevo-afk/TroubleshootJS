# TroubleshootJS - Current Task Report

## Current checkpoint - Task 48 complete and accepted - 2026-09-06

- **Scope/status:** COMPLETE — Task48 and its closed validation set are accepted.
  One real composed NMOS driver / LED-load challenge has a normal sidebar entry,
  actual LOW/HIGH control, symptom-only complaint, physical diagnosis and repair,
  and solver-backed OFF/ON customer retest. RG and RLOAD are the two admitted
  physical fault owners. Fixed support parts and the exact typed low-side,
  replay, mutation and fresh-owner boundaries are documented in the
  [Task48 packet](task-evidence/task-48/README.md). Task49 is unstarted.
- **Git/candidate:** Clean entry on `codex/task43p-final-recovery` at reviewed
  Task47 `3928f4b8cdf633b89aeb6b78512b5cced833a774`; accepted gate
  `623f3a74b37eec771576fe11a4d13c03a09335e7` is an ancestor. No pre-existing
  changes were present. The accepted implementation is the commit containing
  this checkpoint; exact commit, remote verification and notification outcomes
  belong to the final handoff. Final build16 fingerprint (972 inputs):
  `cdf2bbee7afdc1af2bccfa38bbda09f3ed65a450156a9dd7793d2dec3234ec09`.
- **Validation:** Final JDK8/GWT OBF build16 PASS, all five permutations, exit0;
  before/after inputs and loaded Browser permutation verified. Pure suites PASS:
  Task44 247, Task45 202, Task46 410 / 36 negatives, Task47 269, Task48 224,
  plus 12 direct low-side cases and independent Python 22 + 8 + 8 seed oracles.
  Compiled Task48 PASS: 755 assertions, eight exact-string seeds, both repaired
  owners, six measurement cases, five construction failures, five prior-owner
  and five detached-initial installation failures, actual stale work rejection
  and original-owner restoration. Mutation PASS: 235 assertions, 30 partial
  aborts, four lifecycle and three damage checks. Independent correspondence
  covers 15 terminals / nine traces / nine wrong mappings for each seed.
- **Regressions:** Task47 1540 assertions / eight seeds, composition entry 113
  lifecycle / 180 mutation assertions, Task40 corpus, Task41 14 routes / 128
  solver samples, strict Task43P runtime oracle exit0 / zero blockers, six-family
  layout and challenge/replacement PASS. LED physical build15 PASS; final16
  NMOS DS_OPEN, DS_SHORT and GATE_OPEN PASS. All four legacy descriptors/replays/
  parity strings exactly match accepted Task47. Forced Task48 AssertionError
  reaches the required terminal FAIL. No failed or missing result is a PASS.
- **Visible input/privacy:** Real visible Browser, debug off, full seed0 RLOAD
  and seed1 RG sequences PASS: complaint, faulty LOW/HIGH, ordinary left-red /
  right-black probes, distinguishing voltages/resistances, all-source isolation,
  physical catalog repair and visible OFF/ON customer pass. Unrepaired retests
  fail; mode reselection exits and ordinary body input works. Text, attributes,
  tooltips and reports retain privacy. Four inspected screenshots are linked in
  the packet. The source-disabled actual entry fails the same public helper;
  source bytes were restored. Final16 normal initial entry and NPN/NMOS controls,
  physical 0 V isolated / 5 V restored control probes and unrepaired retest PASS.
- **Evidence reuse/limits:** The exact dependency audit reuses full visible12,
  compiled14 and pure05/14 results only for unchanged consumers. Final14-to16
  changes affect three legacy developer verifiers; their dependent actions now
  settle through the existing boundary without weakening guards or assertions.
  Normal-player implementation is byte-identical to visible12. All affected
  selected routes received fresh proof. A worker's unrequested PRETTY build is
  excluded and superseded by root OBF16. Fixed values/small layout only; fixed
  NMOS/LED/support parts have no mutation workflow. Task41 Option A/metrics and
  historical CDP/physical aggregate limitations remain; Task48's independent
  physical proof is separately qualified. Nonblocking follow-ups are in the
  packet; no Task49/value-synthesis work is included.
- **Review/resources:** Fresh independent Luna MAX integrated review and all
  source deltas PASS before their live Browser runs; final evidence review PASS
  with no blocker. Astra owns all reconciled files and publication; no worker owns live
  resources, and no agent-close or speed selector is exposed. The task Browser
  tab is closed. Preview run `d316bf21ba5d4abfb7682929ae320684` exact cleanup
  Success=true / no errors / wrapper exit0; process, listener and lease released.
  The [cleanup receipt](task-evidence/task-48/cleanup.json) records scratch
  disposition: automatic approval review rejected deletion before execution
  ("blocked by policy"). The isolated 61-file / 4,792,679-byte diagnostic directory
  is retained as a nonblocking TEST/TOOL limitation, with scratch status BLOCKED.
  Existing user resources and the completed verifier audit journal are preserved.
- **Publication/next:** Commit the accepted Task48 files after staged inspection,
  normally push the configured upstream, verify its exact SHA and attempt the
  authorized Gmail notification; the final handoff records those outcomes. G5 is
  satisfied. Then STOP; Task49 is unstarted and requires separate authorization.

## Current checkpoint - Task 47 complete and accepted - 2026-09-06

- **Scope/status:** The bounded assembler and contribution contracts are
  implemented and the closed validation set passes. The real developer-only
  canary couples a 5 V resistive-source block to a resistive load through
  explicit signal and return joins. Task48 and player-facing composition remain
  unstarted. Independent code, delta and final evidence review PASS.
- **Git/candidate:** Branch `codex/task43p-final-recovery`; the gate/base commit
  `623f3a74b37eec771576fe11a4d13c03a09335e7` is the separately reviewed, pushed
  Composition Entry Gate; its completion email was sent. Phase B began clean.
  The accepted Task47 implementation is the commit containing this checkpoint;
  the exact SHA and remote/notification outcome are recorded in the final handoff.
  Final build08 source fingerprint is
  `57c91b7e47598259a50504ab2d862225fdf661a75f812ca36d6e24bedf3abf7f`
  (960 inputs). All current source/test/docs/evidence changes belong to Task47.
- **Ownership/replay:** One device-owned board, CircuitJS graph, physical runtime,
  global inventory authority, generated instance and existing fault/lifecycle
  services. Immutable block contributions own local recipes and executable
  requirements; exact namespaced mappings preserve aliases and explicit net
  provenance. `bounded-assembler@1` / `resistive-coupling@1` /
  `developer-canary@1`, geometry 3; isolated named streams and exact signed-long
  seed strings. Unsupported constraints and open-drain transfer reject.
- **Validation:** Final JDK8/GWT OBF build08 PASS, all five permutations, exit 0.
  Fresh phase pure suites PASS: 247 Task44, 202 Task45, 410 Task46 assertions /
  36 negatives, 269 Task47 assertions; independent Python 22 + 8 vectors.
  Build02 actual compiled Task47 PASS: 1,540 assertions / eight seeds, both
  repair owners, four measurement cases, five private and five install failures,
  stale-work rejection, 72 physical negatives and exact JVM/Python/GWT replay.
  Selected composition gate PASS 113 lifecycle / 180 mutation assertions;
  explicit Task47 AssertionError produces FAIL. Final build08 Task43P runtime
  A-I/C1 strict oracle PASS, exit 0 / zero blockers; Task40 corpus and Task41
  14 routes / 128 solver samples PASS; challenge/replacement and six-family
  layout at 0/2/3 PASS. All four final legacy replays PASS with descriptors,
  summaries and 22-vector parity byte-identical to the accepted gate.
- **Reuse/limits:** The exact build02-to-build08 audit changes only three
  developer-verifier files; all 957 other inputs are identical and the reused
  assembly/gate/error routes do not call the changed helper or its consumers.
  Affected consumers receive fresh final regressions. All 36 explicit pure gate
  inputs are unchanged from their fresh phase runs. No old failed proof is
  reused. Task41 Option A and declared-versus-executed complexity limitations
  remain; no deep same-owner/nested transaction or full historical CDP/source-
  falsifier certification is claimed. The legacy physical aggregate remains
  UNPROVEN while its separately bounded triad passes. Canonical seed transport
  is exact; the redundant legacy physical-report numeric seed can round in a
  JavaScript Number consumer and is explicitly excluded from replay transport.
  Task46's pre-existing AssertionError-to-RUNNING follow-up is unchanged.
- **Diagnostics:** Reviewed test-seam guard, Task40 dependent-action settlement
  and null-safe diagnostic repairs retain assertions. The tight developer helper
  now requests an actual guarded CircuitJS step when wall-clock throttling
  leaves analyzed verification pending; ordinary settlement remains required.
  Earlier browser focus timeouts retain separate failed receipts and were closed
  by delayed collection or a fresh owned tab. Exact evidence and failure
  dispositions are in the [Task47 packet](task-evidence/task-47/README.md).
- **Visible input/privacy:** Ordinary LED3 page with Task47 flags but no debug
  exposes no assembly marker/report/details. Actual left red/right black probes
  read 10.653 V and the unrepaired customer retest fails. Active-mode reselection
  exits to --- V and normal R1 selection works. Two inspected screenshots are
  linked in the packet; they do not claim a playable composed challenge.
- **Review/resources/next:** Fresh read-only Luna code review and all targeted
  deltas PASS; final evidence reconciliation and wording delta PASS. All owned Browser tabs
  closed. Preview run `dcb1d77150c54478aee380848dc418c9` cleanup Success=true,
  no errors, wrapper exit 0; no owned live process remains. Scoped diagnostic
  receipts remain outside the repository and existing user resources were
  preserved. Publish the accepted Task47 candidate as a separate reviewed commit,
  push the configured upstream, verify its exact SHA and send the authorized
  completion email. Stop before Task48.

## Accepted checkpoint — Composition Entry Gate — 2026-09-06

- **Scope/status:** COMPLETE, bounded gate validation and independent review
  accepted; publication is the next action. The owner authorizes this gate and
  Task 47 as separate reviewed commits. Task 47 coding and Task 48 are unstarted.
- **Candidate:** Branch `codex/task43p-final-recovery`, reviewed base
  `7249709e5061af6b14d4aa968fc3282a1355dac1`, no pre-existing worktree changes.
  Final 950-input source fingerprint:
  `a2f24ce5049aa72aa7a2594ccfa0910b61ef163d5f8200c4b5fe447748d390f9`.
  Exact inputs, five final compiled hashes and command receipts are in the
  [gate evidence](task-evidence/task-47/gate/README.md).
- **Changes:** Settled/current-owner action guards; real repaint/workbench/retest
  succession checks; fresh-owner Option A publication/restoration; explicit
  resistor-only composition installation; bounded resistor prepare/commit/abort;
  reference, inventory, cross-role and exact-terminal invariant checks. Failed
  recovery isolates power and disables actions. No broad transaction framework.
- **Validation:** Final JDK8/GWT OBF build, all five permutations PASS, exit 0.
  Actual compiled gate PASS: 113 lifecycle / 180 mutation assertions, five
  installation failure stages and 18 partial-write aborts. Full Task43P A–I/C1
  strict oracle PASS (exit 0, zero open blockers); Task41 14 routes / 128 solver
  samples PASS; selected challenge/replacement/meter/stress/wrong-repair,
  all-family layout and LED3/RC2/NPN0/NMOS0 legacy replay PASS. Actual visible
  probes, mode exit, power/mutation, wrong/correct retest and held/re-enabled
  controls PASS; five inspected screenshots and ordinary-page privacy recorded.
- **Reuse/limits:** Both actual pure suites PASS (247 + 202 block assertions;
  410 challenge assertions / 36 negatives, independent seed oracle). Their 27
  explicit inputs are unchanged. Runtime/visible evidence at fingerprint
  `469e8ce0aacae88ee319b97de62ebcf8c80e2ede3ea47989676216bb83ac0827` remains
  applicable after exact dependency audit: the only final production addition
  is the restricted composition entry; all prior installation bytes are
  unchanged. That entry and its negative execute in the final build/gate.
  The physical packet's independent triad passes six terminals/six solver
  checks and nine negatives; its historical aggregate remains UNPROVEN. No
  expansion of the separate historical CDP/isolation/source-falsifier campaign.
- **Review:** Fresh read-only Luna MAX `gate_review` PASS across A–H and targeted
  corrections. No concrete blocker or new nonblocking product finding remains.
  Task46's recorded AssertionError-to-RUNNING test/tool follow-up is unchanged.
  All worker files are reconciled; Astra owns publication. The interface has
  no worker-speed control; no global settings changed.
- **Resources:** All task Browser tabs closed. Preview run
  `0adabdc4450d434f9bf7c4a10ec85cd7` cleanup Success=true, no errors, wrapper
  exit 0. Baseline run cleanup also passed. No owned live processes remain.
  Raw diagnostic receipts remain in the uniquely owned external scratch;
  existing user resources and historical quarantines were preserved.
- **Next:** Stage the intended gate files, inspect/commit, push to the configured
  upstream and verify the exact remote SHA; attempt the authorized completion
  email. Then freeze Task 47's contracts and complete only Task 47.
## Accepted checkpoint — Task 46 versioned replay — 2026-09-06

- **Scope/status: COMPLETE — ACCEPTED.** Task 46's immutable request, named
  streams, constraints, real legacy replay and closed validation set are accepted.
  Task 47 remains unstarted; the Composition Entry Gate is unclaimed.
- **Branch/candidate:** Clean entry at reviewed HEAD
  `134a0791b052d9a1928565e7b2318ac7284a357c`, branch
  `codex/task43p-final-recovery`. Task 44 `10dcafa` and approved Task 43P `a5e8efa`
  are ancestors. No intervening or pre-existing worktree changes.
  Current source/test/harness fingerprint:
  `1b21500d2c07f5b2f9c082f83203a4be96e4948c6bf396b4fb5c9d1ba494cc23`;
  the evidence packet records all 12 input hashes and the exact replay reuse.
- **Design/acceptance:** Prerequisite investigations are complete and reconciled.
  The [Task 46 packet](task-evidence/task-46/README.md) freezes the descriptor,
  version resolution, lossless seed encoding, named-stream tuple/arithmetic,
  constraints, real legacy adapter and closed validation set before coding.
- **Gates/review:** Final pure JDK8 Task46 PASS (410 assertions, 36 negative
  cases, 22 independently calculated seed/selection vectors); Task44/45 PASS
  (247 + 202 assertions), exact scratch cleanup. Five-permutation production
  build passes. Exact final JVM/compiled-browser parity PASS (4,748 bytes).
  All 19 real replay cases PASS, with 13 unchanged proofs reused under an exact
  dependency audit. Final challenge, all-family layout and Task 41 (14 cases,
  128 solver samples) PASS; normal LED/RC privacy and readiness PASS with two
  inspected screenshots. Independent Luna source, targeted test delta and final
  evidence/cleanup reviews PASS. The retained corpus-AssertionError diagnostic
  limitation is a nonblocking TEST/TOOL follow-up.
- **Ownership/resources:** Astra owns integration, runtime/browser and docs;
  Luna MAX leaf implementation/oracle/review assignments are reconciled. The
  task-created Browser tab is closed. Exact preview process/listener/lease
  cleanup PASS, wrapper exit 0; no task-owned live process or tab remains.
  Raw qualification/failure receipts are retained outside the repository.
  Pre-existing preview state at port 8897 and historical quarantined resources
  remain outside Task 46 ownership.
- **Publication:** The enclosing commit identifies the accepted source, docs
  and curated evidence. Exact remote SHA and the post-push Gmail outcome are
  recorded in the final task handoff.
- **Next unstarted boundary:** The Composition Entry Gate requires its separate
  runtime-integrity qualification and owner authorization before Task 47.

## Accepted checkpoint — Task 45 electrical port preflight — 2026-09-06

- **Scope/status: COMPLETE — ACCEPTED.** The owner-approved Task 43P baseline is
  `a5e8efa775b52914a87269103a316c10669a5305`. Task 44 was separately accepted,
  pushed and notified at `10dcafa36fa9c40d97ee6460aa41df5836e8ea9b` before Task 45
  began. This completes the authorized Tasks 44–45 sequence. Task 46 and runtime
  composition remain unstarted.
- **Candidate/ownership:** Branch `codex/task43p-final-recovery`, Task 44 base
  HEAD `10dcafa36fa9c40d97ee6460aa41df5836e8ea9b`. The accepted 13-file
  implementation/test/runner fingerprint is
  `301058d9eb0a111bf6343aee881665da28dbce0a4c0d29fdb6f0ae75c0f1ab13`.
  The containing Git commit also records four documentation paths, for 17
  intended paths total. Astra integrated all work; Luna assignments are complete.
  No pre-existing work or global Windows/runtime settings were changed.
- **Changes:** Immutable typed ports and block/adapter contracts, deterministic
  pure compatibility preflight and nominal-only legacy input adaptation.
  Owner-authorized tooling fixes retain previously attested natural child exits,
  add failure-only identity diagnostics, separate coincident loose-part fixture
  wires and give Task39 screenshots distinct existing-session names. Electrical
  runtime, generators, seeds, stable IDs and player controls retain their owners.
- **Validation: PASS.** Actual-source JDK8 suites: 247 + 202 assertions. Final
  JDK8/GWT build: all five OBF permutations, 40.082 s compile / 1.102 s link.
  Nine affected process-boundary probes freshly pass on module `a5420969...`
  and oracle `b3d1e1a...`. All 55 selected legacy routes qualify in 20 complete
  exit-0 invocations, including independent NPN/NMOS control/load inputs and
  all six Task39 routes on browser verifier `23e2b1d7...`. Every accepted run
  has complete browser/preview/profile/lease cleanup. Nine Task39 screenshots
  were independently decoded and all were visually inspected by Astra.
- **Review/reuse: PASS.** Fresh read-only Luna reviews cover the pure contracts,
  integrated process repair/oracle, fixture and each subsequent delta. Final
  independent evidence reconciliation verifies 20/55, exact receipts and all
  13 implementation hashes, with no findings. Nine pure inputs and six compiled
  outputs are unchanged; exact reconstruction proves later failure diagnostics
  and Task39-only naming do not affect reused successful gates. Failed exit-2
  invocations remain failed and contribute no acceptance observations.
- **Limits/follow-up:** This is semantic metadata and declared-policy checking,
  not runtime composition certification. Existing Task39 CDP captures do not
  claim manual visible-input evidence. Intermittent strict process-identity
  failures remain a tooling reliability follow-up; no acceptance rule was relaxed.
  Full historical Gate B/43P matrices and CI dispatch were not rerun or claimed.
- **Resources/publication:** No owned browser, preview, build or test process
  remains. Remaining Chrome profiles and the temporary test-browser package and
  archive were removed after exact ownership/process/listener checks; original
  failure receipts remain unchanged. Three policy-rejected Edge profiles and
  private evidence receipts remain. The containing acceptance commit targets
  normal `origin/codex/task43p-final-recovery` publication, followed by the
  authorized Gmail notification; exact remote SHA and send outcome belong to
  the final publication handoff. Next roadmap item: **Task 46, UNSTARTED**.

Details, exact commands, hashes, historical failures and evidence limits:
[Task 45 evidence packet](task-evidence/task-45/README.md).

### Authorized cleanup repair — acceptance set

The diagnostic diode seed `0` reproduced exit `2` at the initial child-handle
lookup with **two prior attestations for the missing PID in the same drain**.
The repeated graph scan discarded that already-qualified natural-exit route.
The repair consults the original module-private record/handle binding only at
that missing-child boundary, validates the candidate and all retained identities,
and runs the existing retained-handle/two-current-absence proof. One outer 500 ms
budget includes lookup and reconciliation; existing proof deadlines remain.
Unknown children still fail; current parent, complete graph, late-child,
root/listener, profile and lease checks retain their existing paths.
The first repaired smoke exposed a later exit in fresh child validation. The
shared live-process guard now distinguishes a positively observed exit (the
existing typed missing-process error) from a changed start identity. Only the
exact child's typed disappearance may consult its prior attestation; identity
mismatch, inaccessible data and expired proof budgets remain fatal.
An initially returned `Process` whose exit is already observable follows the
same prior-attestation proof before any live start-time read. An unreadable or
mismatched process object remains fatal; no shared start-time helper is relaxed.

| Gate / production path | Verifier and independent oracle | Environment / candidate limits |
| --- | --- | --- |
| Repeated descendant discovery | Actual `Get-VerifierDescendantProcessRecords` exercised by new focused Gate B canary: previously attested exit succeeds; first-ever missing child, changed identities, present/reused PID, invalid capabilities, unavailable/late proof fail | Windows PowerShell 5.1, real retained Process handle, lower process-provider seams; root owns module, Luna owns oracle |
| Existing identity and exit boundaries | `-GateBBrowserDescendantIdentityRetryProbe`, `-GateBDescendantSnapshotRefreshProbe`, `-GateBBrowserDrainNaturalExitProbe`, `-GateBBrowserNaturalShutdownProbe`, `-GateBProcessStartIdentityProbe`, `-GateBBrowserRootListenerFastPathProbe`, `-GateBProcessOwnershipProbe` | Existing `scripts/verify-gate-b.ps1`; focused ownership probe also runs the existing descendant-cleanup canary; preserve malformed/foreign identity and 500 ms assertions |
| Complete ancestry and retained resources | `-GateBLateMarkerlessProbe`, `-GateBRootGoneProbe` | Existing real Windows process fixtures; exact cleanup or typed infrastructure result, never a fabricated pass |
| Early actual implementation smoke | Existing browser verifier `-Diode -Seeds @(0) -TimeoutSeconds 90` with the pinned explicit Chrome-for-Testing path | Unique task-owned profile/port/preview and exact cleanup; diagnostic before final acceptance |
| Independent delta review | Fresh read-only Luna review of integrated module/canary diff, actual call paths and evidence reuse | Reviewer authors neither module nor test oracle; repair findings require affected checks/delta review |
| Final Task 45 validation/publication | Both pure contract suites; seven legacy groups below, partitioned into 20 sequential invocations / the same 55 routes; final diff/index checks, normal push/SHA proof and notification | Reuse unchanged Java/GWT and completed successful routes only after the documented exact source/dependency audit and independent review; failed or unrun invocations do not qualify |

Root owns one browser/preview run at a time. New disposable resources use a
separate unique OS-temp cleanup-repair directory. Original failed manifests and
the three policy-rejected Edge profile targets remain untouched.

### Preserved checkpoint before the authorized verifier repair

- **Entry:** Task 44 is accepted and published at
  `10dcafa36fa9c40d97ee6460aa41df5836e8ea9b` (`Define immutable functional blocks
  and stable namespaces`) on `codex/task43p-final-recovery`. Normal `origin` push
  and exact remote SHA equality passed; the worktree was clean. Authorized Gmail
  notification was sent after publication. Its [acceptance packet](task-evidence/task-44/README.md)
  and checkpoint below preserve the namespace/immutability evidence.
- **Scope/state: IMPLEMENTED — VALIDATION BLOCKED.** Task 45's seven pure
  contract/checker/adapter types are implemented on the accepted Task 44 HEAD
  above, with nine tested source/test/runner hashes in the
  [candidate manifest](task-evidence/task-45/README.md#tested-candidate-file-manifest).
  Both focused suites, final production build and fresh independent review pass.
  Required legacy parity remains incomplete after repeated browser-descendant
  cleanup failures. Task 45 is uncommitted and unpublished; Task 46 and runtime
  composition remain unstarted. No pre-existing work was overwritten.
- **Ownership/resources:** Astra owns Task 45 types, pure checker, adapters,
  integration, scripts and docs. Luna `task45_contract_tests` completed and handed
  back its independent oracle. Fresh read-only Luna `task45_review` passed the
  integrated candidate without findings. All Luna assignments are complete.
  Build, test and browser commands have ended. Existing complete ownership and
  listener helpers found no known/run-marked processes and no listeners on the
  eight ports from the failed Edge/Chrome runs. The two failed Chrome profiles
  and task-only downloaded browser package were removed through the existing
  owned-tree helper after fresh absence/ownership checks. Three Edge profiles
  remain quarantined: the user authorized deletion, but automatic approval review
  rejected the validated native commands after the existing helper failed.
  Failed manifests, retained lease journals and private execution receipts remain;
  later resource reconciliation does not upgrade their original cleanup results.
- **Gates:** Both actual-source JDK8 suites PASS, exit `0`: Task 44 nine groups /
  247 assertions and Task 45 202 assertions, exact scratch cleanup (root and oracle
  author's separate runs). Final JDK8/GWT production build PASS, exit `0`, all five
  permutations (compile 36.468 s / link 2.163 s). Fresh independent Luna review
  PASS, no findings; independent focused run exit `0` (247 + 202 assertions),
  exact cleanup, whitespace and all nine candidate hashes matched. The final
  LED set passed all 15 routes, exit `0`, exact cleanup. Diode seeds `0,2,3`
  returned exit `2`: seed 2 passed, seeds 0/3 had unproven descendant cleanup.
  Parallel, RC, NPN, NMOS and Task39 commands were NOT RUN after the repeated
  failure. Full six-family and independent control/load parity is BLOCKED.
- **Blocker/next safe action:** `BLOCKER — TEST/TOOL`: the existing
  verifier cannot retain inspectable descendant identity through cleanup on
  these failed cases; the exact host/provider cause is unresolved.
  Diagnose/correct that protected verifier/host boundary
  under appropriate scope, preserving the 500 ms ownership proofs, isolation,
  cleanup and exit semantics; then complete the closed legacy set. Task 45 does
  not authorize browser/process-isolation redesign. No further identical run,
  Task 45 staging/commit/push or success email was attempted. Task 44's exact
  origin SHA was rechecked; final Task 45 whitespace/status checks pass with
  only the 13 intended changed/new files and an empty index.

The root selected the existing explicit `-BrowserPath` seam with standalone
Chrome for Testing `152.0.7977.82` as a bounded equivalent after the installed
Edge failed. Read-only Luna review confirmed the unchanged verifier's supported
configured-executable path and the real exit-0 probe's ownership/cleanup receipt.
The full LED run qualified this selected path; the later diode exit `2` still
blocks complete parity. No verifier, deadline, ownership, assertion or global
setting changed. Exact commands, timing, candidate identity and resource limits
are in the [Task 45 evidence packet](task-evidence/task-45/README.md).

### Task 45 frozen v1 semantics and acceptance matrix

`ElectricalPortContract` contains separate functional role (rail, return,
control, analog, digital, passive, load), block-relative direction, electrical
behavior, drive mode, voltage declarations, scoped reference/isolation domain,
loading/current declarations, digital active level/guarantees/thresholds,
merge policy and declared accessibility. Final specialized scalar/range values
distinguish KNOWN, UNKNOWN and NOT_APPLICABLE. Numeric constructors reject NaN,
infinity and inverted ranges. Port validation rejects contradictory declared
thresholds/ranges and absent required local references. Nominal values are
optional evidence and never manufacture guaranteed ranges or current ratings.

`ElectricalBlockContract` binds metadata to Task 44's exact local ports and
references. It also declares explicit adapter interfaces (regulator, divider,
level shifter, relay or isolation barrier). An adapter names its input/output
ports and whether their references remain isolated. Both sides retain their
own declared ranges and references; the checker neither computes a conversion
nor connects them internally. Nonisolated v1 adapters use their declared common
reference; isolated adapters require distinct declared isolation domains.

`ElectricalConnection` is device-owned proposed wiring, with a stable connection
ID and qualified block/port references. `PortCompatibilityPreflight` evaluates
these immutable inputs without a live graph. It combines overlapping proposals
and declared local conductive aliases before checking the whole group, including
otherwise unlisted port declarations on the same local attachment. Thus split
two-port proposals cannot hide a shared conflicting driver. V1 regards each
declared driven port as a separate driver; aliasing shared-driver capacity needs
an explicit future policy and is not silently inferred.

References are block-instance/local-net tuples, not labels. A group of RETURN
ports explicitly wires common references only when its ports permit merging
and declared isolation domains agree. Unknown merge/isolation evidence remains
unproven. Distinct isolation domains cannot merge, including transitively.
Signal groups require identical references or a qualified proposed return
connection; merely naming two nets GND never joins them. A return/signal mixed
group is rejected. Adapter declarations never authorize a magic direct merge.

Supported signal/rail groups have one declared voltage driver and compatible
receivers/passive loads. The entire guaranteed source range must be inside each
receiver's allowed range, and declared capacity must cover the sum of bounded
loading. Digital/control groups additionally require adequate low/high guarantees,
receiver thresholds and matching active levels. Analog/passive declarations do
not invent digital guarantees. Logic sources feeding an ANALOG receiver remain
unsupported (`INSUFFICIENT_INFORMATION` / `UNSUPPORTED_ROLE_PAIR`); numerical
range containment alone does not establish that interface's transfer behavior.
Stiff/push-pull driver conflicts reject even at
equal nominal voltages. Open-drain low-side sinks cannot masquerade as positive
rail outputs; cases requiring pull-up/transfer analysis remain unsupported v1
information rather than passing. Accessibility checks compare declared required
access with declared provision; real layout reachability remains a later proof.

Malformed data has a typed validation exception (stable code and field/entity
identity); malformed proposals produce structured MALFORMED results. Other
results are COMPATIBLE, INCOMPATIBLE or INSUFFICIENT_INFORMATION with canonically
ordered connection/port/field-specific diagnostics. A positive result means
only that the proposal passes this declared v1 policy. It is not solver-validated
operation, electrical safety certification or challenge validity.

Oracle clarification during focused validation: a return-only join with unknown
isolation reports `UNKNOWN_ISOLATION`; a dependent signal additionally reports
`REFERENCE_UNPROVEN`. Unsupported logic-to-analog is insufficient information,
not proven incompatibility. Unknown-port diagnostics use the full v1 namespace
address even though the referenced entity is undeclared. The integrated checker
and final test oracle were covered by the fresh independent review above.

`LegacyInputPortMetadata` reads copies of existing `ExternalBoardPowerInput`,
`BoardPad` and optional `PowerInputNameplate` values. Its descriptor is only the
leaf's external-input contribution view. Actual pad/component/terminal/net IDs
come from these objects, never string splitting. A small leaf-owned
`LowSideSwitchInputMetadata` provider validates the authoritative NPN/NMOS
`LOAD_VIN_INPUT` and `CONTROL_VIN_INPUT` mappings and supplies their roles; the
generic checker has no family-ID dispatch. Missing ranges, thresholds, loading,
merge/isolation or access evidence stay UNKNOWN. No generator, family selection,
runtime gate, solver element, stable legacy ID, seed or package is changed.

| Explicit case | Required v1 result / independent oracle |
| --- | --- |
| Declared 4.75–5.25 V source, receiver 4.5–5.5 V, adequate bounded current | COMPATIBLE after qualified common-reference wiring |
| Permitted common-reference return group; known passive load; analog range | COMPATIBLE within declared scope |
| Digital 0.4 V maximum LOW / 4.4 V minimum HIGH to 1.5 V LOW / 3.5 V HIGH thresholds, matching active level | COMPATIBLE with adequate voltage/current/reference declarations |
| Explicit 12 V-to-5 V regulator; isolated level shifter/barrier with separate return groups | Side connections COMPATIBLE; domains remain distinct; direct bypass rejected |
| Nominal-only current leaf inputs, including load/control supplies with different values and identical GND labels in different instances | Nominals and exact mappings preserved; required missing evidence stays INSUFFICIENT_INFORMATION |
| Voltage mismatch or overlap without containment | INCOMPATIBLE with source/receiver range reason |
| Invalid direction/drive; low-side sink presented as rail; two equal-voltage stiff sources | INCOMPATIBLE with direction/drive/driver reason |
| Three-port driver conflict; split overlapping proposals; hidden local aliases; aggregate loading beyond capacity | INCOMPATIBLE at whole-group boundary |
| Distinct scoped references without permitted wiring; forbidden merge across isolation | INCOMPATIBLE; unproved permitted-reference evidence remains INSUFFICIENT_INFORMATION |
| Insufficient digital guarantees or opposite active levels | INCOMPATIBLE; UNKNOWN guarantees/thresholds do not pass |
| Absent reference, duplicate/missing port metadata, malformed/nonfinite/inverted numeric data, contradictory thresholds | MALFORMED typed failure with relevant field/entity identity |
| Required access absent versus unknown | INCOMPATIBLE versus INSUFFICIENT_INFORMATION, respectively |
| Known, unknown and not-applicable data; unknown/unsupported drive/loading cases | Distinct outcomes; required NOT_APPLICABLE declaration is malformed |
| Reordered declarations/proposals, repeated calls, constructor/output mutation attempts, new in-memory example | Identical sorted decisions/reasons; immutable inputs; no family dispatch edit |
| Task 44 regression | All literal namespace/identity/immutability checks still pass |

The focused runner compiles every new pure production type and both suites
with the pinned JDK8 and no runtime class/source path. The final GWT command is
unchanged. Applicable existing browser routes are the default LED verifier and
`-Diode`, `-Parallel`, `-Rc`, `-Npn`, `-Nmos`, plus the existing `-Task39` independent
control proof, with explicit representative seeds. Non-43P routes reject the
Task43P-only startup-settle flag; use their actual supported route options and
the existing owned browser/preview implementation, preserving exit/cleanup proof.
Historical exit-2 aggregates are not reused as fresh parity results.

Closed final legacy command set, each through `scripts/verify-browser.ps1` with
the existing owned browser/preview path and `-TimeoutSeconds 90`: default LED
`-Seeds 0,2,3`; `-Diode -Seeds 0,2,3`; `-Parallel -Seeds 0,2,3`;
`-Rc -Seeds 0,2,3`; `-Npn -Seeds 0,1,2,3`; `-Nmos -Seeds 0,1,2`; and `-Task39`.
The default LED path covers five lifecycle routes per seed, NPN covers all four
forced faults per seed, NMOS covers all three forced faults per seed, and Task39
covers generated NPN/NMOS/RC input boundaries plus the existing independent
control/load proof. Execute sequentially after candidate review/build. Preserve
each exit and cleanup result; diagnose repeated infrastructure failures before
continuing the unchanged set. No Task43P source falsifier or historical aggregate
is added to this contract-only scope.

## Accepted checkpoint — Task 44 contract — 2026-09-05

- **Entry/authorization:** The owner explicitly authorized the attached Tasks
  44–45 prompt after the final Task 43P handoff. Approved baseline and clean
  starting HEAD: `a5e8efa775b52914a87269103a316c10669a5305`, branch
  `codex/task43p-final-recovery`, tracking the matching branch on `origin`
  (`dspevo-afk/TroubleshootJS`). Remote HEAD equality was checked. No pre-existing
  tracked or untracked changes. Accepted C1/C2 and candidate-identity corrections
  are retained; historical reports below describe their original approval state.
- **Scope/state:** Task 44 accepted: immutable block descriptions, validated
  local relationships, restricted/versioned stable namespace and two in-memory
  examples. Focused tests, final production build and independent review pass.
  Task 45 implementation awaits only this checkpoint's verified publication.
  Task 46 and runtime
  composition remain unstarted. This batch does not certify runtime composition.
- **Ownership:** Astra owns design, integration, tests, documentation and
  publication. Luna `task44_descriptors` completed the four new descriptor/
  namespace/example/validation Java files and reconciled its scratch. Luna
  `contract_test_infrastructure` completed both read-only investigations. Fresh
  read-only Luna `task44_review` passed the integrated candidate with no findings.
  All worker ownership is reconciled. Requested worker settings are
  `gpt-5.6-luna` / `max`; the supported spawn interface exposes no speed control.
  No task-owned browser or preview has been launched; the completed bounded
  build/test children exited and cleaned their execution scratch. Root retains
  a unique OS-temp evidence directory for this batch's build/publication receipts.
- **CI preparation:** The existing workflow is absent from `origin`'s default
  `master` branch (contents API returned 404). Its current branch copy is present.
  Default-branch integration is separate from these contract tasks; local
  acceptance must use the actual supported repository commands.
- **Gates:** JDK8 `1.8.0_502` direct source-7/target-7 harness PASS, exit `0`,
  nine groups / 247 assertions and exact cleanup. Final JDK8/pinned GWT2.7 OBF
  production build PASS, exit `0`, five permutations (compile 37.114 s; link
  1.487 s). Fresh independent Luna review PASS; independent focused rerun and
  whitespace/scope checks pass. The worker's preliminary JDK21 compile
  was diagnostic only; root supplied the actual required JDK8 proof. No prior
  runtime result is relabeled a new descriptor/port-contract result.
- **Evidence/publication:** The [Task 44 acceptance packet](task-evidence/task-44/README.md)
  records exact commands, tested source hashes and limits. This enclosing commit
  identifies the accepted checkpoint. Astra performs final staged inspection,
  normal upstream push, remote-SHA equality and the authorized post-push Gmail
  attempt; their exact receipts follow publication without changing the SHA
  being qualified. After verified publication continue to Task 45 automatically.

### Frozen bounded design and acceptance set

Task 44 introduces final, package-local immutable descriptions, independent of
`TroubleshootBoard`, `GeneratedBoardInstance`, CircuitJS and physical runtime
ownership. `FunctionalBlockDescriptor` owns typed parameters, components with
declared terminal IDs, endpoint-to-component/terminal relationships, pads mapping
endpoints to local nets, role declarations and ports. Parameters support only
boolean, signed 32-bit integer, finite double and immutable text values. Inputs
are copied; nested values are final and returned collections are immutable and
canonically ordered. Duplicate declarations, unresolved references, duplicate
component-terminal endpoints, contradictory role declarations and invalid port
attachments fail explicitly with a stable validation code and field identity.

Roles declare REQUIRED or OPTIONAL contributions: required roles have at least
one local member; optional roles may be empty. Every member resolves locally.
A port names a declared role and a PAD, NET or ENDPOINT attachment that is a
member of that role. Required here describes the local role contribution; it
does not silently introduce a runtime wiring/connection-count policy.

`BlockNamespace` validates unique block instance keys within one device schema
and derives IDs without allocating or merging live nets. Encoding version 1 is
`tsj-block-v1/<device-schema>@<positive-schema-version>/<instance>/<kind>/<local>`.
Each input identifier uses the validated ASCII grammar
`[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}`; no slash, at-sign, percent escape, whitespace
or Unicode normalization is accepted. Kind tokens are fixed lowercase words.
Thus segments cannot overlap. Schema version and encoding version are explicit;
block type/version and parameter values remain descriptor metadata rather than
construction-order identity. The API resolves declared IDs only. Optional block
insertion/removal/reordering cannot change any unchanged tuple. Logical IDs are
neither acquired physical-part IDs nor runtime-owner authorization tokens.

Two small descriptor fixtures describe an NMOS low-side driver and a resistor/
LED indicator load using explicit local terminals/nets/roles. They are examples
only. Device intent will own proposed interblock wiring, and a later assembler
alone will own global allocation and net merging. Task 44's immutable port ID,
role and local attachment are the seam for Task 45's separate typed electrical
metadata; Task 45 does not mutate the Task 44 descriptor or repurpose BoardNet.

Task 45 v1 will separate functional role, block-relative direction, electrical
source/sink/passive behavior, reference/domain, drive/loading, isolation/merge
policy and declared accessibility. Known, unknown and not-applicable evidence
are distinct. Its pure group preflight will require full source-range containment
in receiver limits, adequate digital high/low guarantees and loading/capacity
where applicable, compatible direction/drive, scoped references, and explicit
reference-join permission. Multiple stiff drivers fail even at equal nominal
voltages. Low-side sinks are not positive supplies. Isolated domains stay
separate across explicit adapter input/output contracts; a direct merge cannot
stand in for a regulator, divider, level shifter, relay or isolation barrier.
Results distinguish malformed input, incompatibility, insufficient/unsupported
evidence and a supported semantic-policy pass using stable reason and field IDs.
No nominal value supplies an invented range, tolerance, threshold or capacity.
Read-only external-input adaptation will consume current metadata and preserve
the independent NPN/NMOS control/load boundary and nominal-only nameplates.

| Gate / production path | Oracle and explicit fixtures | Verifier / environment |
| --- | --- | --- |
| Task 44 namespace and descriptor validation | Literal expected IDs; reversed declarations; repeated local IDs across instances/kinds; optional block insert/remove/reorder; invalid delimiter/Unicode/empty IDs; duplicate instances, locals and roles; dangling references and invalid port attachments | Focused JDK8 Java harness against actual production classes, no solver/browser |
| Task 44 immutability/purity | Mutate constructor collections and nested members; attempt output mutation; repeat validation/derivation; both in-memory examples | Same harness, isolated OS-temp classes; explicit source compilation |
| Task 44 final Java candidate | All five OBF permutations plus direct new-code execution | Repository `scripts/build.ps1`, selected JDK8/pinned GWT; focused harness |
| Task 45 supported positives | Fully declared rail/load; permitted common reference; passive/load; digital/control levels; explicit adapter and isolated domains; nominal-only legacy adaptation | Focused JDK8 contract harness; frozen policy and literal expected reasons |
| Task 45 required negatives | Mismatch and overlapping-not-contained voltage; direction/drive; equal-voltage conflicting drivers; three-port conflict; unrelated GND references; forbidden isolated merge; insufficient digital levels; missing/invalid declarations; NaN/infinite/inverted ranges; magic adapter bypass; unmet declared accessibility | Same harness, connection/port/field-specific expected outcomes |
| Task 45 determinism/purity | Reordered declarations/groups; repeated calls; immutable input/output snapshots; new generic in-memory example; Task 44 suite rerun | Same harness, no CircuitJS candidate for classification |
| Task 45 legacy parity | Explicit six-family cases, distinct input values and NPN/NMOS independent control/load states; stable IDs/mappings, determinism and electrical behavior | Applicable existing leaf verifiers with accepted browser route/settings; exact command set follows infrastructure inspection |
| Task 45 final Java candidate | Fresh all-five-permutation build and both contract suites | Repository JDK8/GWT build and direct harness |
| Each task's review/publication | Integrated diff, acceptance evidence and scope/dependency audit | Fresh independent read-only Luna MAX review; root staged inspection, commit, normal upstream push, SHA equality and authorized Gmail attempt |

The infrastructure investigation confirmed there is no existing Java unit-test
runner. The small same-package `tests/contracts/FunctionalBlockContractTest.java`
main and `scripts/verify-block-contracts.ps1` compile the actual four production
files explicitly with JDK8 `-source 7 -target 7 -encoding UTF-8` and empty
source/class paths. This both exercises code beyond GWT's transitive reachability
and rejects accidental runtime dependencies. The runner reuses existing bounded
process and exact owned-temp cleanup helpers without changing them. Final build
command per task: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File
scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile
-Style OBF`. Focused command: `powershell.exe -NoProfile -ExecutionPolicy Bypass
-File scripts/verify-block-contracts.ps1 -JavaHome
.tools/jdk8-download/jdk8u502-b07`.

The closed set excludes historical 43R/43P full recovery matrices, source
falsifiers, Gate B process recertification and fabricated screenshots for these
nonvisual contracts. Unchanged runtime evidence retains its original limits;
any actual protected-boundary change requires its affected checks. No new family,
live composition, solver, PCB/layout, inventory, fault engine, player control,
same-owner lifecycle framework, random stream or challenge descriptor is in scope.

## Prior checkpoint — Task 43P committed-candidate verification — 2026-09-05

- **Status/scope:** REVIEWED CANDIDATE — bounded candidate-identity correction in the
  source-experiment, browser and Gate B scripts, their shared identity helper,
  focused regressions and current handoff documentation. No Java, electrical,
  geometry, C1/C2, snapshot or Task 44 changes. Owner approval remains ungranted.
- **Candidate:** `codex/task43p-final-recovery`, starting clean HEAD
  `164704ccbbcbea14532b21a1d8c8bc6b4f380252`. The enclosing commit identifies the
  correction; source/verifier digest
  `1a9c9109e38aadfa7593684eeb36d26b94f148a1926afd594487b57ffa6c6753` and execution
  digest `cd3cac3cbf71de52da0fa1551c81a3d012ed71d0f0f2dcfb6d3a09d1e23645cb` identify
  the reviewed bytes. Historical evidence keeps its original HEAD/diff and digests.
- **Root cause:** Runtime admission and forced-proof checks equated the actual
  checkout with the historical `8bf4424` baseline. The real current-HEAD
  `-MutationPreflight` reproduced exit `2` before any anchors. The correction
  freezes actual HEAD, validates explicit expectations and forwards that identity
  to child verification. Historical baseline metadata is independent of candidate
  admission; ancestry requires a separate audit and is not inferred in shallow CI.
- **Current evidence:** Corrected real preflight passes all eleven anchors and
  four restoration/rejection canaries. Committed, subsequent-commit and detached
  shallow-checkout fixture preflights pass. Wrong expected identity, execution
  provenance and actual source/status/HEAD mutation readers reject. Source-proof
  and forced-proof focused contracts pass. Complete Gate B returned `0` and the
  fresh independent Luna review passed without blockers. A test fixture's initial in-memory
  reader extraction lost `PSScriptRoot`; file-backed extraction corrected that
  harness failure. It was not a product pass or a browser result.
- **Evidence/reuse:** The [candidate handoff](task-evidence/task-43p/candidate-identity/README.md)
  and its precommit validation/dependency audit retain exact commands, outcomes
  and fingerprints. Production Java/web, all eleven mutation definitions,
  detectors, build/preview/isolation and pinned toolchain are unchanged. The
  original five-permutation build, eighteen physical triads and eleven compiled
  catches are reused with original identities; legacy aggregate exits remain `2`.
- **Postcommit receipt:** Clean committed-HEAD preflight and complete Gate B,
  forced-negative first and dedicated A–I with exact cleanup, normal push/remote
  SHA equality, CI outcome and notification are recorded outside the checkout and
  linked in the final handoff. They are deliberately separate from the dirty-parent
  checks above; recording their exact commit must not create a SHA-update loop.
- **Ownership/resources:** Astra owns all edits and test processes. The Luna CI
  investigation and independent review are complete and read-only. At this
  precommit checkpoint no browser or preview is active; task-owned logs and a
  failed regression fixture remain in OS temp. The postcommit receipt records
  subsequent runtime resource cleanup.
- **Next:** Consult the postcommit receipt for the published candidate's outcome;
  the handoff stops at Owner Review. Task 44 remains unstarted.

## Prior checkpoint — Task 43P C1/C2 and efficiency work — 2026-09-05

- **Status/scope:** COMPLETE — ready for Owner Review. C1/C2 and the authorized
  efficiency work passed acceptance and independent review, including the final
  BOM fixture and evidence delta. Owner approval has not occurred; Task 44 is
  unstarted.
- **Candidate:** `codex/task43p-final-recovery`, tested starting HEAD
  `8bf442416a2fa2c0c9d654d1efa14e754c2b7ee7` plus the intended diff. Source/verifier
  digest `9e4d9457411db8c24fbb74b3d6655d38a14b3a9a2c0ff930068b7033ef79b663`.
  The enclosing commit identifies the final source, documentation and evidence.
- **Changes:** C1 independently handles temporary-element removal, solver
  reconstruction, queued power and synchronization while retaining primary and
  suppressed failures. Unproven restoration visibly isolates/stops the board.
  C2 asserts the supported snapshot inventory and restores UI-sensitive values
  last. Seven cleanup canaries and exact non-default/sentinel checks cover both.
- **Efficiency:** Dependency-based reuse is explicit in `AGENTS.md`; all eleven
  source anchors and four preflight canaries run before selected compilation.
  Task43P's page diagnostic runs before evidence capture. Phase, operation,
  timing and cleanup diagnostics retain existing failure, deadline and ownership
  semantics, including the 500 ms proof budget.
- **Validation:** Final five-permutation JDK8/GWT build and full current Gate B
  returned `0`. Forced-first qualified expected `1`; dedicated A–I returned
  legitimate `0`, no open observations, all four real D cases closed, seven
  canaries and exact H checks. Eighteen physical triads and nine negatives each
  qualify across six families; their six legacy aggregates remain `2`.
  Eleven compiled falsifiers qualify: two anchor repair cases, eight earlier
  physical cases and one current-Java C2 case. All browser/compiled proof is
  reused after the final test-only BOM delta under original digests; all eleven
  mutation definitions and production/web bytes are unchanged. LED3 real input,
  five inspected screenshots and bounded privacy checks
  cover the player flow. Every accepted live run has qualified cleanup/audit.
- **Review:** Independent Luna MAX integrated/delta, dependency-reuse, source,
  runtime, physical and final packet reviews passed. The documentation wording
  correction also passed targeted delta review. The final BOM source/reuse
  delta and its updated evidence packet review passed.
- **Limits/resources:** Failed invocations retain their actual exits and
  separate manual/natural-exit dispositions. No active owned browser, preview,
  listener or tab remains after final cleanup. Private raw diagnostics and
  retained failed profiles/claims remain. Snapshots prove the supported
  fresh-candidate/detached-owner boundary; generic epochs and deep same-owner
  rollback remain follow-ups. Privacy covers the exercised LED3 states.
- **Git/next:** Astra owns the authorized commit, normal push, remote-SHA check
  and post-push Gmail attempt. Actual publication/notification outcomes are
  reported in the final handoff. Stop at Owner Review; do not start Task 44.

The [current reconciliation](research/TASK43P_RUNTIME_RECONCILIATION_2026-09-05.md)
and [acceptance index](task-evidence/task-43p/c1c2/acceptance-index.json) contain
the criterion mapping, exact commands, original identities/exits, source/build
hashes, review provenance and cleanup. [Curated evidence and screenshots](task-evidence/task-43p/c1c2/README.md)
are separate from the historical blocked checkpoint below.

## Historical published checkpoint — Task 43P — 2026-09-05

- **Status/scope:** IMPLEMENTED — VALIDATION BLOCKED. Verifier trust and
  forensic evidence are implemented; runtime acceptance is not granted.
  No production cleanup repair, Owner Review closure, or Task 44 work occurred.
- **Candidate:** `codex/task43p-final-recovery`, tested base
  `a5b253873c2b25a54d7c393b118e3f2e1831a4d8` plus the intended integrated
  diff. The [evidence index](task-evidence/task-43p/runtime-evidence-index.json)
  records final and reused source digests, exact commands, hashes, and run IDs.
  The enclosing commit identifies the documentation/evidence publication.
- **Findings:** A-I returned proven application `1`, with three open
  observations representing two causes: post-removal measurement cleanup
  leaves solver/overlay/queued-power residue (43P-C1), and Task41's restoration
  assertion accepts a changed resistance-current sentinel (43P-C2). Both have
  bounded correction milestones, owners, reproductions, and acceptance checks
  in the [current reconciliation](research/TASK43P_RUNTIME_RECONCILIATION_2026-09-05.md).
- **Validation:** Final JDK8/GWT production build and complete Gate B returned
  `0`; unchanged Gate B seams reuse their recorded evidence. Forced-first
  reached its exact expected DOM/Java proof with `1` and exact cleanup. All
  18 physical triads passed; that route's aggregate remains `2` because its
  separate legacy A-I fields are unproven. All eleven compiled source
  falsifiers are individually qualified; their original invocation exits and
  explicit unchanged-evidence reuse are recorded in the evidence index.
  No aggregate `2` or generic shell `1` is relabeled PASS.
- **Visible evidence:** Real built-in Browser LED3 input proved original and
  wrong-2200-ohm retest rejection, red-left/black-right meter controls and mode
  exit, removal, correct-1000-ohm completion, and disabled terminal controls.
  [Five real screenshots](task-evidence/task-43p/README.md) and bounded ordinary
  DOM/accessibility inspection are retained. The supplementary legacy
  NormalPlayer CLI returned `2`; its manual cleanup is separate and never
  counts as verifier-owned acceptance cleanup.
- **Review:** Fresh independent Luna integrated/delta reviews cover the code
  and runtime observations. Final documentation/evidence reconciliation passed;
  review provenance is recorded in the index. C1/C2 remain blockers; broad settlement/epoch,
  transaction, seed, geometry, and privacy claims remain bounded follow-ups.
- **Resources:** No known task-owned live browser, preview, listener, or tab
  remains after the final absence audits. Private OS-temp failure evidence,
  retained failed source copies/profiles, and audit artifacts remain preserved.
  Workers own no mutable resources. No broad process kill or evidence deletion
  was used. Successful route cleanup and exact manual cleanup of failed runs
  are explicitly distinguished.
- **Next:** 43P-C1, then 43P-C2 and affected acceptance checks. These are
  unstarted correction tasks; Owner Review and Task 44 remain blocked. Root
  owns the ordinary commit/push and post-push Gmail notification; their actual
  outcome is supplied in the final handoff, not inferred from this document.


## Before final source cases — Task 43P — 2026-09-05

- **Status/scope:** IN PROGRESS — RUNTIME ACCEPTANCE BLOCKED. Verifier trust
  and evidence reconciliation only; no production cleanup repair, Owner Review
  closure, or Task 44 implementation.
- **Candidate:** `codex/task43p-final-recovery`, HEAD
  `a5b253873c2b25a54d7c393b118e3f2e1831a4d8`, 24 intended worktree entries.
  Current source/verifier digest is
  `6b4e1541894eff75c6ff8a4b6c222e2fee5d9ff1ccf046f0299c8b2dd55f85fb`.
  The only code change after the A-I/triad runs selects a genuinely different
  solver endpoint in source falsifier 6; no Java, wrapper, isolation, or Gate B
  code changed. Root owns integration and publication.
- **Qualified runtime:** Forced `2efbdc81cf7c4981a4ed35a685ac1c16` returned
  exact expected `1`; A-I `1e71c0ae2b4a40d0a2672775d3a83e57` returned proven
  application `1` with a complete validated packet. Its three open observations
  represent two causes: post-removal measurement cleanup leaves solver/overlay/
  queued-power residue, and Task41's restoration assertion accepts a changed
  resistance-current sentinel. All 18 physical triads passed in
  `813c693b2cda48a9b980a59c147ff84b`; the separate legacy route's aggregate
  remains `2` because its A-I fields remain unproven. All three runs completed
  exact verifier cleanup and fresh current absence audits without manual cleanup.
- **Source falsification:** Five cases qualified before the sixth selected an
  alias of the original C1.+ endpoint, reached the generic forced marker, and
  remained `2` with final child cleanup unproven. Source restoration and a
  separate current absence audit passed; failure evidence is retained. The
  test-only target correction (`F175C33E...`) passed Windows PowerShell 5.1
  contract checks and fresh independent delta review. Current forced-first
  `8e481ed75b29428098bb5ab760e1dd96` then returned exact `1` plus complete
  cleanup/current absence; corrected source case 6
  `302a40709aba43a69322370046f1bffe` returned qualified `CAUGHT`/`1`, exact
  restoration, unchanged repository, and complete cleanup/current absence.
  The remaining five cases are pending; the first five retain their original
  unaffected source/provenance evidence.
- **Visible evidence:** Real built-in Browser LED3 input proved unrepaired and
  wrong-2200-ohm retest rejection, meter red-left/black-right controls and mode
  exit, removal, correct-1000-ohm completion, and disabled terminal controls.
  Five real screenshots and bounded ordinary DOM/accessibility inspection are
  retained for curation. This is separate from the supplementary legacy
  NormalPlayer CLI run `207aabfafa5b4d7599c9097ff3dbf9de`, which timed out and
  failed cleanup with actual `2`. Exact root-only manual cleanup and fresh
  process/listener absence were proven for that failure; profile/claim/evidence
  remain retained. It is not counted as a CLI pass.
- **Gates/review:** Final unchanged Java build passed all five JDK8/GWT OBF
  permutations with actual `0`. Complete Gate B passed with actual `0` before
  the source-case-only correction; unchanged seams reuse that evidence and
  current source contract checks cover the correction. Fresh integrated and
  targeted independent Luna reviews found no additional current blocker.
- **Resources/next:** No live browser/preview remains after current audits.
  Root owns retained private evidence; workers own no mutable resources.
  Complete remaining source cases, finalize historical dispositions and bounded
  correction milestones, curate evidence, and obtain final evidence review.
  No commit, push, or email has occurred.

## Before final runtime runs — 2026-09-05

- **Status/scope:** IN PROGRESS — NOT ACCEPTED. Complete verifier trust,
  physical falsification, and A-I evidence reconciliation only. Owner Review
  and Task 44 remain blocked; no production lifecycle repair is in scope.
- **Candidate:** `codex/task43p-final-recovery`, HEAD
  `a5b253873c2b25a54d7c393b118e3f2e1831a4d8`, 24 intended worktree entries.
  Root owns integration. Final Java build `gwt-source-latch-h-target-a041fd81470c4f23b3c8dc5d6afc64e4.log`
  passed all five JDK 8/GWT permutations with actual `0`; Java has not changed.
- **Qualified evidence:** On the preceding script candidate, forced run
  `ea5706234f5a4da98c6e95ed09dc94b9` reached its exact DOM/anchored Java failure,
  actual expected `1`, and complete verifier cleanup. The renderer-only
  `J1.1 +20px` compiled source falsifier then returned validated `CAUGHT`/actual
  `1` (`6d12268d2b6b4a2b9ad6bc005558068c`): exact physical rejection, bound child
  proof, cleanup, restored source bytes, and unchanged repository. No generic
  shell failure was accepted. These are earlier-script evidence, not proof of
  the current changed wrapper.
- **Latest incomplete A-I run:** `f95064683d814fe99d302a108ed16bf2` reached the
  aggregate observation but returned `2` because the wrapper incorrectly
  classified the deferred resistance-reading refresh as temporary measurement
  residue. No qualified aggregate packet was saved. Exact verifier cleanup
  and a separate fresh process/listener/profile/lease absence audit passed;
  no manual cleanup was used.
- **Current correction/gates:** The validator now matches Java's actual graph,
  solver, overlay, power, and identity cleanup predicate while retaining the
  raw refresh flag. Rejected runtime packets are retained as unvalidated
  diagnostics without assigning an outcome. Windows PowerShell 5.1 focused
  schema/capture/persistence-failure canaries passed with actual `0`. Review
  additionally tightened D's positive baseline and exact power-state enums.
  Fresh independent delta review passed on helper `1268EA3F...`; final complete
  Gate B passed with actual `0` and unchanged before/after script hashes in
  `gate-b-runtime-schema-final-c99f4eef3b8b43aabd3deb4633180cac.log`.
  Wrapper/Gate B hashes begin `789956A8...` / `28E01914...`.
- **Resources/next:** No live browser is owned; failed evidence remains
  retained. Freeze the reviewed candidate and repeat forced-negative first, then A-I, final
  physical corpus, remaining compiled falsifiers, and real built-in Browser
  evidence. Reconcile historical findings, obtain final review, and publish
  only after the requested gates are complete. No commit/push/email occurred.

## Before runtime predicate correction — 2026-09-05

- **Status/scope:** IN PROGRESS — NOT ACCEPTED. Verifier trust and A-I runtime
  reconciliation; no Owner Review closure or Task 44 work.
- **Candidate:** `codex/task43p-final-recovery`, HEAD
  `a5b253873c2b25a54d7c393b118e3f2e1831a4d8`, moving worktree candidate.
  Four new Java runtime collectors cover existing A/B/C/E/F assertions,
  active-meter exception cases, paused completion/reset/session/snapshot cases,
  and observation of a real queued repaint across a board switch. Their
  integration and strict evidence schema now have a current JDK 8/GWT
  production build (all five permutations, actual exit `0`) and fresh
  independent scoped review. Two subsequent test-path corrections below are
  the moving candidate; their current production build is running.
- **Latest qualified runtime:** Forced run
  `1db920291ba74b82a482f7b4ae8557d1` returned actual expected `1`, exact DOM and
  anchored Java diagnostic, and verifier-owned complete cleanup. Separate
  current residue audit found no remaining processes/listeners/profile/lease;
  no manual cleanup was used. NMOS seed 3 and LED seed 0 then recorded triad
  PASS and complete cleanup; each overall invocation remained actual `2`
  because its A-I evidence was still explicitly unproven. These results use
  the earlier `5F38FAD5...` wrapper/source-protocol candidate and the explicit
  30000 ms startup condition, not the current moving runtime candidate.
- **First compiled source case:** Renderer-only `J1.1 +20px` compiled all five
  permutations with actual `0`, then returned actual `2`. The real application
  rejected it at diagnostic admission before the physical verifier ran. The
  harness also exposed an incorrectly named exception type while reporting
  the unproven result. Preview process/listener absence, exact byte restoration,
  and unchanged repository state were proven; the failed disposable copy and
  evidence remain retained (`task43p-source-experiments-c29da7ed342d4c68981d92cf8f5041cc.json`).
- **Corrections/review:** The source rejection now uses the exact
  `System.IO.InvalidDataException` contract; focused Windows PowerShell 5.1
  contract/identity probes passed with actual `0`. A validated source-only
  request runs the unchanged physical checker after real analysis/time advance
  and before other admission checks; no admission check is skipped. That seam
  passed independent source review. New lifecycle collectors also passed a
  scoped static review after correcting observation reads across restoration.
  No current runtime defect is claimed proven from those new source checks.
- **Latest integration:** The runtime wrapper distinguishes validated observed
  blockers (`1`) from unproved evidence/cleanup (`2`). H now tests direct
  session succession and a dirty reset; terminal Finish/Retest must reject
  repeats. New compiled falsifiers cover the omitted snapshot restore field
  and disabled public Remove. The latter separately proves direct workbench
  dispatch and real mouse input on a fresh player document. The exact
  navigation token is retained and validated; geometry is a coordinate aid.
- **Current runtime attempts:** Forced run `e1efba6d49d0413a97f7c60c5d6af21f`
  on the reviewed navigation candidate returned exact expected `1` with
  anchored Java/DOM proof and complete verifier cleanup. A separate current
  residue audit passed. Source attempt `f43ddb30d6654c7599ac37ce139e22c2`
  compiled successfully but remained `2` after its first physical failure could
  be replaced by a later diagnostic-admission result. Source bytes restored
  exactly; preview cleanup and unchanged repository were proven. The A-I
  attempt `e6d593d401b84c809af233eea6471e18` returned `2`, without an aggregate
  packet, at an invalid collector lookup of NPN `R1`; verifier cleanup and the
  separate residue audit passed. H now selects real NPN `RLOAD` posts (LED/RC
  retain `R1`), with independent delta review PASS. The source-only catch now
  pauses after a validated real failure so later timer updates cannot replace
  it. That delta is under review. No product lifecycle blocker is inferred from
  these incomplete runs; no production repair or admission bypass was added.
- **Gates/resources:** Build log `gwt-runtime-public-3b73608a24f44b57be3cb7c83cd6b17d.log`
  records current Java exit `0`. Windows PowerShell 5.1 runtime schema,
  source proof/public input schema, and forced-proof probes returned `0`.
  Fresh Luna runtime and public/snapshot source reviews passed after repairs.
  The first complete Gate B returned actual `2` because Windows PowerShell 5.1
  rejected an omitted optional reference parameter in navigation. The corrected
  actual helper passes omitted/reference/invalid-output canaries and independent
  delta review. Complete Gate B then passed with actual `0`, unchanged before/
  after hashes for all 13 integration files, in
  `gate-b-navigation-final-60eae52af6774838946ce02d582898e7.log`.
  Current wrapper/Gate B hashes begin `45F17BB2...` / `D1B87E3D...`.
  The unchanged Java candidate reuses the build above. A prior root-only
  preflight used an unexported assertion and is not a clean-state proof;
  fresh native process and verifier-owned listener checks precede live runs.
  No live browser is owned. Root owns all
  integration; workers returned file ownership and own no processes. Failed
  evidence is retained. No publication occurred.
- **Next:** Finish the current JDK 8/GWT build and source-latch delta review;
  repeat forced-negative first on that frozen Java candidate, then source smoke
  and A-I. Complete Gate B evidence above applies to its unchanged script,
  schema, XML, and toolchain inputs; the Java deltas require fresh live proof.
  Final physical corpus, required compiled falsifiers, visible built-in Browser
  evidence, finding reconciliation, and final review remain pending.

## Source-negative protocol and first focused positives — 2026-09-05

- **Status/scope:** IN PROGRESS — NOT ACCEPTED. Verifier-trust recovery and
  runtime reconciliation; Owner Review and Task 44 remain blocked/unstarted.
- **Candidate:** `codex/task43p-final-recovery`, HEAD
  `a5b253873c2b25a54d7c393b118e3f2e1831a4d8`, 18 worktree status entries.
  Module `8BEF40EAD838B7AB450A0BF60FDA6F6E0F7540B4580AF06CE03416BC737B5701`;
  wrapper `5F38FAD5F56B2AEF44268B72A949C1D944EC3738C38BE5015C63FBACD3BAD420`;
  source harness `58BA97710CBAA8BD9847783945EB5A755123C82CC0408A17661AF38C8EE09BD5`;
  Gate B `6C67E6CB682FCAD6FAF7E3298BE106AD2772B9BF56951842440093A0454F500B`;
  physical verifier `08412EFB947AC4DD65099A667BAA402241FB38C4A6B36F0A23A2BEBE6AA21353`.
- **Delta:** NMOS `RPD.2` was a verifier-manifest regression: the unchanged
  generator has always bound this GND pad to `GroundElm` post 0. The manifest
  now matches that independently checked endpoint contract. No electrical
  implementation changed. Set-mismatch diagnostics use sorted IDs without
  changing set equality. A normal Task 43P invocation can select one exact
  family for focused checks; the default six-family corpus is unchanged.
- **Source-negative proof:** Nine fixed source cases select the corresponding
  compiled disposable route. A validated Java request binds the real specific
  failure to nonce/run/route/request/execution/experiment identity; the DOM
  retains the original exact failure. The wrapper writes its separate proof
  only after final verifier cleanup. The harness requires actual child `1`,
  exact proof/hashes/provenance, preview process and listener absence, exact
  byte restoration, repository equality, and final evidence persistence.
  Late errors remain `2`; the sequence stops at the first unproven case and
  retains its disposable copy. No live source case has passed yet.
- **Checks:** Current Java JDK `1.8.0_502` production build
  `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile
  -Style OBF` passed with captured exit `0`, all five permutations and link
  (`gwt-source-proof-7eaab24879ad40d8a13517d5d3d170b1.log`). The wrapper's
  Windows PowerShell 5.1 `-GateBForcedNegativeProofProbe` passed with actual
  `0`; source `-ContractProbe` and `-IdentityCanary` passed with actual `0`.
  Invalid family/source CLI selections returned actual `2` before context
  creation. These synthetic checks do not prove live Java/UI cleanup.
  Full Gate B passed wrapper `5F38FAD5...`, Gate B `6C67E6CB...`, and source
  harness `0A6D42F5...` with actual `0`
  (`gate-b-source-proof-551214c9f0944abfbe3ae929baf011f3.log`). Review then
  found an unpinned preview response PID/start. The current harness binds that
  response to the exact launched handle tuple, revalidates the current owner
  before the browser child, and retains the original tuple for cleanup.
  Added launch-tuple negative checks pass with actual `0`. Final complete
  Windows PowerShell 5.1 Gate B passed the current `58BA9771...` harness with
  captured exit `0` (`gate-b-source-launch-final-df661c431f0e48ae8d8ca13ce0fe0ff4.log`).
  Current live forced-negative remains pending.
- **Review:** The source-request Java/wrapper delta passed independent Luna
  review. A fresh Luna integration review found the preview launch-binding
  blocker above; the correction passed targeted independent delta review,
  including separate focused checks with exit `0`. Root also ran the current
  probes in Windows PowerShell 5.1 with actual `0`. The source harness author
  is separate from this reviewer.
- **Prior runtime limits:** Forced run `a452a5450ff344fb95f02bc1cad2a65d`
  passed expected exit `1`, exact Java/DOM proof, and verifier cleanup using
  explicit 30000 ms startup settling. Normal run
  `ef122e530c0c45af91a4cf125e2ab346` recorded triad PASS for LED/diode/RC/NPN
  seed 3, then failed the now-corrected NMOS manifest expectation; parallel
  was not reached. All five browser sessions and preview were verifier-cleaned,
  with a separate current residue audit of zero. These remain earlier-candidate
  observations, not proof for the new candidate or complete A-I acceptance.
- **Resources/publication:** No owned live browser/preview remains. Root owns
  integration; workers have completed source edits and the reviewer is read-only.
  Failed evidence and previously retained temporary resources remain preserved.
  No staging, commit, push, notification, or broad cleanup occurred.
- **Next:** Final integration review and full Gate B; forced-negative first on
  this candidate; focused positive LED/NMOS checks and one real source-negative
  smoke case. Complete A-I runtime falsification, the final seed corpus, all
  required source cases, and visible built-in Browser evidence remain pending.

## Startup qualification and first normal corpus — 2026-09-05

- **Status/scope:** IN PROGRESS — NOT ACCEPTED. Verifier-trust recovery and
  runtime acceptance; Owner Review and Task 44 remain blocked. Inherited dirty
  work and the owner's project agent-configuration changes are preserved.
- **Candidate:** `codex/task43p-final-recovery`, HEAD
  `a5b253873c2b25a54d7c393b118e3f2e1831a4d8`, with 18 worktree status entries.
  Module SHA-256 `8BEF40EAD838B7AB450A0BF60FDA6F6E0F7540B4580AF06CE03416BC737B5701`;
  wrapper `69A685B301F4658317DBC4926F388B18AE9492CB7D7B49881D67422805573461`;
  Gate B `D43D9EA2E9D5E6C13951F93BC58F78751DC40C6AA12D5DDC741415EA5F5ABF1F`;
  physical evidence producer `F90BD2EF7008C348244A8C2C7231512DB6DD15BDE55B7AB3A897DFD4F2AEBBA1`.
- **Latest boundary:** Optional `-Task43PStartupSettleMilliseconds` defaults to
  zero and accepts only bounded canonical input (0–45000 ms). A selected Task
  43P route waits through an actual CDP Promise before navigation, retains the
  existing route deadline, verifies minimum elapsed time and the complete
  owned blank document, and records the setting in wrapper evidence. This
  does not change the module's 500 ms proofs or 15 s cleanup budget.
- **Validation:** Focused Windows PowerShell 5.1
  `scripts/verify-gate-b.ps1 -SkipJdkCheck -GateBCdpReferenceProbe`: captured
  exit `0`, including actual helper references/JSON, injected startup timing,
  document/protocol/deadline negatives, and actual pre-context CLI rejection.
  Those injected cases are not live Edge proof. Parser and diff checks pass.
  Final full Windows PowerShell 5.1 Gate B passed with captured exit `0`
  against the current `8BEF40EA...` / `69A685B3...` / `D43D9EA2...` hashes.
  JDK `1.8.0_502` `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07
  -Target Compile -Style OBF`: captured exit `0`, all five GWT permutations
  and link passed for the current `F90BD2EF...` Java candidate; no Java changed
  afterward, so this build is reused.
- **Review:** Independent Luna reviews passed the shutdown capability/attempt
  guard, CDP reference/PowerShell 5.1 repair, and triad `faultType` emission.
  The startup-wait delta also passed fresh read-only Luna review, including a
  separate focused probe run with exit `0`; the reviewer launched no browser.
- **Runtime evidence:** The 30-second settled prerequisite passed with exact
  verifier cleanup. Forced run `a452a5450ff344fb95f02bc1cad2a65d` passed the
  required expected-negative gate with actual exit `1`, exact DOM marker,
  anchored Java diagnostic, nonce/route/request/execution proof, and complete
  verifier cleanup. Final manifest/packet audit confirmed both released leases,
  browser/server absence, profile/claim removal, unchanged source digest
  `983a6cc7abf8249618305d3558d9c68c357f0aa57275582cf67453963ca839e2`
  (856 files), unchanged status, and zero current process/listener residue.
  This result uses the explicit 30000 ms startup condition.
  Earlier forced run `31672c206ddd4472804ca80e85811ef0` rejected the
  missing triad field with exit `2`, while verifier cleanup fully passed.
  After the field repair, run `757519e50da4420fbdce5ea35cb33f29` captured the
  complete Java/DOM packet at browser age 8.2 s, then returned `2` on unproved
  transient descendants. Its root was already absent at the separate audit;
  two current views, a full process scan, both ports, and unchanged manifest
  were proved. No manual stop occurred. Failure evidence remains retained.
- **Normal seed-3 runtime:** Run `ef122e530c0c45af91a4cf125e2ab346` recorded
  physical-triad PASS for LED (6 terminals), diode (8), RC (12), and NPN (15),
  each with all nine canonical negative fixtures. Their A-I status remains
  partial/unproven. NMOS stopped at the actual DOM failure
  `FAIL:task43p-solver-post-oracle-mismatch:RPD.2`; the run returned exit `1`
  and parallel was not reached. This is an unresolved correspondence finding,
  not a normal-corpus pass. All five browser sessions and the preview completed
  verifier-owned cleanup; fresh process/listener, profile, and claim audits
  confirmed no remaining resource. Source digest remained unchanged.
- **Resources/publication:** Gate B and its fixtures are finished; no live
  browser/preview remains from the failed route. Root is the sole writer;
  workers are read-only. No staging, commit, push, or notification occurred.
- **Next:** Independently determine whether the NMOS RPD.2 mismatch is in the
  independent oracle or the live binding, then repair only the authorized
  verifier boundary or document a reproduced product blocker. Complete runtime
  falsification, final seed corpus, source experiments, and visible Browser
  evidence remain required before acceptance.

## Recovery evidence before opt-in startup qualification — 2026-09-05

- **Status/scope:** IN PROGRESS — NOT ACCEPTED. Verifier-trust recovery and
  runtime acceptance; Owner Review and Task 44 remain blocked. The owner's
  project agent-configuration/guidance changes are included.
- **Candidate:** Branch `codex/task43p-final-recovery`, HEAD
  `a5b253873c2b25a54d7c393b118e3f2e1831a4d8`, inherited dirty work preserved.
  Frozen module SHA-256
  `8BEF40EAD838B7AB450A0BF60FDA6F6E0F7540B4580AF06CE03416BC737B5701`;
  Browser wrapper SHA-256
  `4DFFD686B8A8B846EAD57B6708E0ED540FF8219D3BF310563B090515B2A09894`;
  Gate B test SHA-256
  `EC784AAE19381B645B55A7522F0200E633DC0F8CAB070693AD16E3330B2CC101`;
  physical evidence producer SHA-256
  `F90BD2EF7008C348244A8C2C7231512DB6DD15BDE55B7AB3A897DFD4F2AEBBA1`.
- **Latest change:** The next forced-negative run reached the decoded Java
  payload and exposed a missing required `triad.faultType`. The physical
  evidence producer now emits the selected fault object's type alongside its
  existing fault ID. The exact consumer schema remains unchanged. Fresh
  independent producer/consumer review passed with no findings: the selected
  fault/binding identity and complete nested allowlists agree. The new Windows PowerShell
  JDK `1.8.0_502` build (`scripts/build.ps1 -JavaHome
  .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF`) passed with
  captured exit `0`; all five GWT permutations compiled and linked from the
  unchanged before/after `F90BD2EF...` producer candidate.
- **Wrapper repair:** The first forced-negative run exposed nested PowerShell
  reference forwarding in evidence capture and an unsupported PowerShell 5.1
  JSON option. Astra corrected those sites and the same reference defect in
  three input helpers. The focused actual-helper canary passed with captured
  exit `0`, including caller counter/diagnostic mutation, nested JSON parsing,
  and malformed/empty rejection. Its injected transport and decode observer
  do not establish Java proof or visible interaction. Fresh independent delta
  review passed, including its own focused PowerShell 5.1 run with exit `0`.
  Final Windows PowerShell 5.1 `scripts/verify-gate-b.ps1 -SkipJdkCheck`
  passed with captured exit `0` against the `8BEF40EA...` / `4DFFD686...` /
  `EC784AAE...` source candidate. Its sidecar records unchanged before/after
  hashes; the existing JDK 8/GWT build is reused for unchanged Java source.
- **Shutdown change:** Attached browsers use a private browser-level CDP close
  request after current session/root/listener ownership and repeated complete
  child-graph proofs. Exact acknowledgement, retained root/child exit, final
  residual scan, and all profile/listener/claim gates are required. Unproven
  shutdown is exit `2`; an attempted command cannot fall back to force-stop.
- **Validation:** Windows PowerShell 5.1
  `scripts/verify-gate-b.ps1 -SkipJdkCheck -GateBBrowserNaturalShutdownProbe`
  passed with captured exit `0`. Real loopback ClientWebSocket cases cover
  successful acknowledgement and malformed/duplicate/error/drop/timeout
  rejection. Separate orchestration injections cover changed ownership/graph,
  missing proof, late descendants, disposal, attempt reuse, and whole-call
  deadline failures. These mocked orchestration cases do not prove live Edge
  cleanup. Parser and whitespace checks passed. Earlier real retained-handle
  tests/review remain evidence only for unchanged functions; the earlier
  complete Gate B is not the final current gate. The prior JDK 8/GWT build is
  reusable because Java source remains unchanged in this recovery session.
- **Review:** Independent Luna review rejected the preceding shutdown candidate
  because changing a copied session's route label bypassed the attempt key.
  Astra bound the key to immutable process PID/start, added route/run relabel
  negatives, and reran the focused PowerShell 5.1 suite with actual exit `0`.
  Fresh targeted delta review passed, including an independently reproduced
  real durable-context copy rejection and a focused suite rerun with exit `0`.
  The first live Edge prerequisite for this candidate returned `2` after
  CDP/readiness passed: descendant PID `27544` exited before its current start
  identity was established. No cleanup acceptance was claimed. A separately
  revalidated exact manual stop of root PID `32212` proved current process and
  port `52068` absence; its manifest remained unchanged.
  A subsequent diagnostic completed all verifier-owned cleanup with 30 child
  checks, zero exact-stop calls, and profile/claim removal; its fixed diagnostic
  exit remains `2`. The untraced 10-second startup-settled prerequisite returned
  `2` on an empty descendant command line in the broad snapshot. Separate exact
  manual cleanup of root PID `100976` proved two current absence observations,
  complete process and port `52786` absence, and unchanged manifest evidence.
  The current unknown observation remains a failure; no discovery rule changed.
  The reviewed untraced 30-second startup-settled prerequisite then passed with
  actual exit `0`: CDP readiness, `Complete-VerifierRun`, profile removal, and
  claim removal all passed for run `20e8e3aa33fc4ac093e989e850097fd4`.
  A separate post-run current process/listener audit also passed. This
  qualifies the settled startup condition only; the 3- and 10-second failures
  remain failures. The final complete PowerShell 5.1
  `scripts/verify-gate-b.ps1 -SkipJdkCheck` then passed with captured exit `0`
  against the preceding `8BEF40EA...` / `E18545F8...` candidate, including parser,
  source/workflow, listener/schema, real process, root-gone/late-helper,
  natural-shutdown, 0/1/2 exit, resource isolation, and renderer checks.
- **Forced-negative first:** Run `2985286a1f2647afbc25181a7f1f5bc9` returned
  actual exit `2`. Capture attempted to increment a nested `PSReference`;
  cleanup also failed on unproved disappearing descendants. No anchored Java
  evidence packet or acceptance was recorded. Separate immediate exact
  PID/start/parent/executable/full-command proof authorized manual stop of root
  `53172`. Two current root-absence observations, the complete process scan,
  and CDP `61180`/preview `61173` absence passed; the manifest was unchanged.
  This manual reconciliation does not count as verifier-owned cleanup.
  The repaired wrapper's run `31672c206ddd4472804ca80e85811ef0` also returned
  `2`, this time rejecting the missing required `root.triad.faultType`.
  Verifier-owned cleanup completed with no errors: browser `80752`, CDP
  `57381`, preview `59495`, profile, and claim were released. A separate fresh
  process/listener audit confirmed absence; no manual cleanup was performed.
  The application gate remains failed until its complete packet is accepted.
- **Host evidence:** The previous reviewed candidate reached CDP/readiness but
  failed the 15-second force-stop drain. A diagnostic recorded repeated Edge
  child recreation after 30 exact stops. Both roots had already exited at
  separate fresh audits; listener absence was proved, no manual stop occurred,
  and all failed profiles/claims/manifests remain retained.
- **Resources/publication:** The prerequisite owns no remaining browser or
  listener. Full Gate B and its bounded fixtures have completed.
  Fresh post-prerequisite audits found zero Edge/helper/WScript/verifier-marked processes; failed
  profiles, claims, manifests, and logs remain preserved. The focused wire
  fixtures proved disposal and listener absence. Root is the sole writer;
  shutdown and wrapper delta reviews are complete. No staging,
  commit, push, or email.
- **Next:** Rerun forced-negative first with the worktree frozen after a fresh
  process/listener audit. Normal A-I/physical-triad, source
  experiments, and visible Browser evidence remain unrun in this recovery.

## Recovery evidence before cooperative shutdown qualification — 2026-09-05

- **Status:** IN PROGRESS — NOT ACCEPTED. Owner Review is blocked; Task 44 is
  blocked and unstarted.
- **Scope:** Verifier-trust recovery and runtime acceptance, plus the owner's
  in-session request to align project agent configuration and improve recovery
  and validation guidance.
- **Candidate:** Branch `codex/task43p-final-recovery`, HEAD
  `a5b253873c2b25a54d7c393b118e3f2e1831a4d8`, with inherited uncommitted work
  preserved and additional verifier/configuration/documentation changes. The
  companion and downstream listener implementation has module SHA-256
  `65807ABCDB49CB0045E3A00AB9F1DFC3243BFCFB22E409CE8F4095A9CC19B1AD`.
  Its integrated test candidate is
  `A4600A7731768ACE55F6D1BF648D498EF6B3CB5020F302B39E0168DC0A868760`.
  These hashes identify the last reviewed downstream authorization correction.
  Independent review rejected the subsequent natural-exit candidate
  `ED6AB0E1...` for copied-scope minting, substituted retained Process objects,
  and an incomplete absence-proof deadline. Astra corrected those boundaries.
  The new frozen module SHA-256 is
  `7659B40ED63DC6664219B075AD8304A3E9FF75228E5815A9FF359AD6A9415754` and
  test SHA-256 is
  `71B239B6545B168F07779DA0E8AD88630C04CD19ED58FF37D692D9CD406ED813`.
  That candidate passed fresh targeted independent review and real-process
  negative checks. Its live prerequisite still failed the 15-second drain
  deadline. A new cooperative shutdown correction is being implemented by
  Astra; its current module hash is
  `137FEBAED37BD2529A422AA3F90D71B566D2011849B953427FF58377B2D686F6`.
  This moving candidate has only parser validation; focused canaries and fresh
  independent review are still required before live qualification.
- **Validation:** Complete Windows PowerShell 5.1 Gate B passed with actual exit
  `0` against the earlier `34A3EE21...` / `CB88B8C1...` candidate, including
  renderer validation. The
  JDK 8/GWT production build passed all five permutations; Java source has not
  changed during this recovery session. The companion matcher and focused
  ownership checks passed. The final explicit compatibility probe passed with
  actual exit `0`, including copied-file policy acceptance before signature
  mutation and rejection afterward. The downstream authorization correction
  passed the focused root-listener probe with actual exit `0`, including the
  real setter, identity negatives, full-snapshot fallbacks, and delayed proof.
  Real Edge cleanup and final complete Gate B still require qualification
  against the corrected candidate. Astra's final focused PowerShell 5.1
  `-SkipJdkCheck -GateBBrowserDrainNaturalExitProbe` passed with captured exit
  `0` against `7659B40E...` / `71B239B6...`. It exercised a real retained
  handle's natural exit, copied-scope minting, substituted handles for the same
  PID/start before and after exit, immutable-identity replacement, disposed
  handles/scopes, wrong retained creation time, delayed attestation/handle/
  first and final current views, live pre-stop parent mismatch, and ordinary
  exact termination. Parser and whitespace checks passed. The worker's
  earlier full deterministic run preceded the required real Edge prerequisite
  and is not counted as the ordered final gate.
- **Review:** The initial deadline/restoration repair passed independent review.
  The launch-handle implementation and final timeout/fixture-cleanup corrections
  have fresh independent review PASS. The reviewer ran the final focused
  process-start identity probe once with exit `0`, including a silent timeout
  child, exact retained process identity, canonical diagnostic, current child
  absence, and listener cleanup. A fresh independent Luna review of the
  same-root candidate passed after the host crash. Fresh independent review
  also passed the companion production policy, including actual signed-file
  acceptance and rejection cases. The test dependency and signature-oracle
  findings were corrected; fresh targeted independent Luna delta review passed
  the integrated `DDD668CB...` / `6E3CF349...` candidate.
  The subsequent `65807ABC...` / `A4600A77...` correction also passed fresh delta
  review; unchanged structural schema evidence is reused alongside inspection
  of the real production schema call paths.
  The natural-exit implementation subsequently failed independent review;
  the corrected `7659B40E...` / `71B239B6...` candidate passed a fresh targeted
  Luna review, including independent real-process checks and the focused
  canary with captured exit `0`. The new cooperative-shutdown delta is not
  covered by that PASS.
- **Runtime:** The first two fresh Edge/CDP prerequisites returned infrastructure
  exit `2` at the strict ownership deadline with unproven descendant cleanup.
  Separate exact manual cleanup proved each run's Edge processes/listener
  absent and retained evidence. The third prerequisite passed the strict
  listener proof and opened CDP, then returned `2` because a cleanup-snapshot
  process disappeared before inspection. Its separately revalidated manual
  cleanup also proved no live Edge/listener residue; all failed evidence is
  retained. A separate settled-startup run passed a real CDP document-readiness
  request but failed descendant executable proof. A reviewed diagnostic-only
  observer then captured the rejected child as Microsoft's signed, matching-
  version `identity_helper.exe` under this run's exact Edge profile. The
  failing verifier only recognized `msedge.exe` descendants. All three
  subsequent roots were manually revalidated/stopped and their listeners
  proved absent; no verifier cleanup prerequisite has yet passed. Forced
  negative, normal A-I/physical-triad runtime, and visible Browser evidence have
  not started in this recovery session.
- **Latest prerequisite:** The downstream correction passed actual startup
  listener authorization and CDP document readiness on a fresh run. Cleanup
  returned `2` because descendant PID `16456` disappeared during identity
  inspection. The independently reviewed diagnostic observer then reproduced
  the failure after full child ownership validation: renderer PID `64768`
  passed discovery proof, eleven other children were exactly stopped, and the
  same renderer was missing at its own immediate pre-stop check. This locates
  the failure after discovery, without proving its natural exit. The worker's
  retained-handle proof for that transition is under review. Diagnostic
  runs always return `2` and cannot qualify acceptance.
  The subsequent `7659B40E...` real prerequisite reached CDP/readiness but
  returned `2` at the 15-second descendant-drain limit. A diagnostic run then
  recorded 30 exact child stops: after the initial 15 children, Edge repeatedly
  created five new GPU/network/storage groups under the same root PID/start.
  Increasing query speed alone does not remove that restart behavior. Both
  runs' processes had already exited at the fresh post-failure audit; no manual
  stop was performed, their listeners were absent, and profiles/claims/manifests
  remain retained. Neither failed run qualifies acceptance.
- **Resources:** Preflight found an abandoned late-markerless Gate B fixture
  process from an earlier probe. Root revalidated its exact manifest/current
  PID, start time, parent PID, executable, full command, and task markers before
  stopping that one process; current process/listener absence was proved and
  retained evidence was unchanged. This manual fixture cleanup is not verifier
  acceptance. A subsequent fresh inventory found zero Edge/helper and zero
  verifier-marked processes. Prerequisite run
  `cb6458bfd8cd4192804a9a804f9ff3b3` and subsequent corrected run
  `5abb1a03082946c3af2b5a0bf9f5fa82` failed; separate exact manual root cleanup
  proved all Edge/helper processes and each listener absent. Diagnostic run
  `2b076bb659c54a9e9a04ea973331fda3` also has separate exact manual cleanup
  proving all Edge/helper processes and port `62517` absent, with its manifest
  unchanged. Root owns no live browser. Retained failure profiles, claims,
  manifests, and logs remain preserved. A fresh post-worker audit found zero
  Edge, identity-helper, WScript, or verifier-marked processes.
- **Publication:** No staging, commit, push, or completion email in this session.
- **Next:** Complete the bounded cooperative browser-shutdown proof and its
  canaries, independently review the final delta, qualify the real Edge/CDP/cleanup path,
  then final
  Gate B and forced-negative/subsequent runtime gates.

Detailed current-session evidence is recorded in
[the post-crash recovery entry](#task-43p-post-crash-verifier-recovery--2026-09-04).
Earlier records below describe their own candidates and do not supersede this
checkpoint.

## Historical Gate B — Verification Isolation and Mainline Protection Baseline

## Historical Task 43P handoff — post-inspection (pre-Coder candidate)

**Status:** Evidence reconciliation has been performed and recorded for
current evidence; Task 43P acceptance remains incomplete and pending. Missing
runtime A-I lanes and the independent physical triad block Owner Review. Task
44 remains blocked and unstarted.

**Evidence packet:**
[`docs/research/POST_TASK_43_INTEGRITY_RECONCILIATION.md`](research/POST_TASK_43_INTEGRITY_RECONCILIATION.md)
at tested baseline `9853f1e5fd311830d14d671d0ba380c51018a658`.

**Coder evidence:** JDK8/GWT build, renderer boundary, and minimal current
Gate B relevance checks returned exit 0; visible in-app Browser observations
covered `PASS:task39`, `PASS:task43`, `PASS:meter`, and `PASS:stress`.
External CDP lanes returned infrastructure uncertainty, and all temporary
source mutations were restored. No production Java/script behavior changed.

**Review handoff:** The first independent Reviewer returned a BLOCKER for
non-auditable provenance, fault/secondary-damage classification, incomplete
A-I status, and missing visible-artifact qualification. The Coder repaired the
reconciliation report with exact baselines, IDs, manifests, lane limits, and
preservation proof. The delta Reviewer returned PASS, and the bounded Foreman
review returned PASS for the evidence packet. Terra Inspector then returned a
BLOCKER for stale status/publication wording and the D5 exit summary. The
documentation-only repairs corrected those contradictions; fresh delta Reviewer
checks passed, the bounded Foreman review passed, and Terra targeted reinspection
returned PASS. That Inspector PASS is limited to the integrity and provenance of
this evidence packet; it does not accept or unlock Task 43P.

**Current decision:** The evidence packet is not acceptance or an unlock.
Owner Review remains blocked, Task 43P is not accepted, and Task 44 has not
started.

### Task Completion Protocol metadata

- **Roadmap milestone/task:** Task 43P — Post-Task-43 Cross-Boundary Integrity Reconciliation.
- **Summary:** Forensic-only reconciliation of preserved lifecycle, verification, and recovery evidence against current evidence; no production behavior changed. Dispositions remain `FOLLOW-UP`, `STALE OR SUPERSEDED`, or `CLOSED`; no proven current `OPEN BLOCKER` was found.
- **Architectural decisions:** CircuitJS remains electrical truth; the supported Task 41 boundary is fresh-candidate/detached-owner-only Option A; same-owner transactional Option B is unsupported. The independent physical triad, runtime A-I lanes, and Owner Review remain unresolved prerequisites.
- **Historical six-file candidate scope:** `AGENTS.md`; `docs/research/AUDIT_STATUS.md`; `docs/ROADMAP.md`; `docs/ARCHITECTURE.md`; `docs/CODEX_TASK_REPORT.md`; `docs/research/POST_TASK_43_INTEGRITY_RECONCILIATION.md`.
- **Validation and limitations:** JDK8/GWT build, renderer boundary, and minimal Gate B relevance checks exited 0. Primary D5 preview stop exited 2 because orphan-parent identity was unproven; separate exact command-line/PID-verified fallback and post-stop port check exited 0 with no owned residual. Visible `@Browser` routes recorded `PASS:task39`, `PASS:task43`, `PASS:meter`, and `PASS:stress`; external CDP and missing A-I lanes remain infrastructure/evidence limits.
- **Coder result:** PASS — current evidence packet and status documentation prepared without production edits.
- **Reviewer result:** PASS for the repaired evidence packet.
- **Primary architect/Foreman review:** 1 bounded round; FINAL PASS for the evidence packet. Task 43P acceptance and unlock remain pending.
- **Escalation-architect review:** Not required; no escalation-architect invocation. The Terra Inspector was the required independent final gate for this candidate, was not the escalation architect, and its targeted reinspection returned `PASS`.
- **Next roadmap milestone:** Task 44 — blocked and unstarted pending Task 43P acceptance and Owner Review.
- **Intended commit message:** `Reconcile post-Task-43 integrity evidence`.
- **Remote/upstream:** configured remote `origin`; branch/upstream `codex/post43-mainline-consolidation` tracking `origin/codex/post43-mainline-consolidation`.
- **Notification:** destination `dspevock@stateofthearcelectric.com`; intended subject `TroubleshootJS: Task 43P integrity reconciliation pushed`.
- **Publication boundary:** This report is written before publication. The authoritative final commit SHA, push result, and notification result are established after the report is written and are available from repository history and the final Codex task response. No final SHA, push result, or notification result is claimed here.

## Historical Gate B closure status

Gate B is COMPLETE and published on `codex/post43-mainline-consolidation`.
The publication commit containing this report was independently verified after
the Reviewer, Foreman, and Sol Inspector gates passed. At that publication
checkpoint, Task 43P was next eligible; its subsequent evidence reconciliation
is recorded above. Task 44 remains blocked and unstarted.

## Baseline

- Branch: `codex/post43-mainline-consolidation`
- Baseline `HEAD`, local `master`, and local `origin/master`:
  `9dc06141190da3a44ebe12015a4f5656f0f40ef5`
- Gate A evidence-preservation commit and archive tags remain untouched.
- Changes remain within the bounded Gate B verifier, CI, and documentation
  scope; CircuitJS/electrical truth, generated topology, start-block
  composition, gameplay, and visible Browser validation were not changed.

## Remediation implementation

- Port claims now use a global per-port named mutex independent of worktree,
  run-scoped claim files containing worktree/run/PID/start metadata, and a
  retained claim through bind. Exact listener PID/start validation rejects a
  foreign listener; release proves the leased port is no longer listening.
- Release is now durable and retryable: `releasing`/`os-released` state is
  persisted before mutex release/disposal, then a `complete` +
  `delete-pending` tombstone is persisted before exact claim deletion, followed
  by terminal `complete` + `released` state. Missing-claim recovery with the
  durable pre-delete marker is idempotent. Once OS release is durable, recovery
  validates/deletes only the exact old claim and does not require current port
  absence, so a newer legitimate run may reuse that port safely. Injected release, mutex-disposal,
  post-delete interruption, final-manifest, partial-claim, and manifest
  failures retain evidence and cannot report complete cleanup.
- Claim-file and manifest acquisition is transactional. Partial claim writes,
  injected manifest failures, mutex handles, map entries, and unregistered
  LeaseRecords roll back only the exact current attempt. The final run cleanup
  walks every still-owned lease after partial startup/cleanup failures.
- Browser/CDP deadlines, transport/protocol errors, target/attach failures,
  profile/process ownership failures, and harness failures remain typed
  infrastructure results. Application FAIL/marker results remain exit `1`,
  and infrastructure `2` remains monotonic over later application failures.
  CDP WebSocket handshake cancellation is bounded by the route deadline and
  aborts/disposes the socket on timeout. Explicit `VerifierExitCode=2` data is
  infrastructure even without a typed failure-kind marker.
- Run-owned preview identity is bound to the recorded nonce, run, process
  start identity, command/script, worktree, and port. The injected identity-
  capture canary retains the live handle/PID and claim until exact termination
  and listener absence are proven. Caller-owned previews are verified only and
  are never adopted or killed. Their integrated ledger and manifest copies now
  require strict JSON Boolean proof values for `identityVerified`,
  `callerOwned`, process identity/termination, ownership uncertainty,
  `processAbsent`, and listener inspection/absence; string `"false"`/`"true"`,
  numeric, and other wrong types are rejected as infrastructure exit `2` in
  both copies. Non-applicable lease proof fields must remain absent or null.
- Browser cleanup scopes WMI/CIM inspection to relevant configured-browser
  candidates plus exact readable run/profile/remote-port markers. Irrelevant
  PID-zero/null-command records do not invalidate a run; an inaccessible
  relevant candidate, stale/unknown start identity, or exact profile reference
  retains the profile/claim and returns infrastructure failure. PPID alone is
  never enough to terminate an inferred descendant; cleanup expands the
  ownership graph from the complete process snapshot so differently named
  helpers are checked rather than filtered out. Markerless real Edge helpers
  are accepted only when their nonblank current `ExecutablePath` exactly matches
  the configured executable identity and their full
  verified ancestry are present; conflicting markers, different executables,
  reparenting, PID replacement, or unknown identity retain evidence. Immediately
  before each stop, the exact current Win32_Process record is re-queried and
  compared for PID, parent PID, command line, markers, executable identity,
  and current start identity; only the revalidated process object may be
  terminated, closing the PID-reuse window. The verified root remains alive
  while complete snapshots are repeated until a bounded fixed point of empty
  descendant graphs; descendants are stopped deepest-first, and the complete
  post-root graph plus exact children of every known parent are rechecked.
  A WMI-backed late-markerless canary creates a same-executable helper only
  after the initial empty graph capture and proves exact cleanup or retained
  typed infrastructure state.
- Root-gone cleanup is fail-closed: two-view absence of the recorded browser
  root never authorizes descendant/profile/claim/lease/run-root deletion. The
  cleanup path blocks the lease, retains profile/claim/manifest/evidence, and
  returns typed infrastructure exit `2` until an exact recovery proof is
  available. The WMI-backed root-gone canary starts a root and a differently
  named markerless helper, signals only the exact root to exit, proves the
  helper/resources remain retained through browser cleanup and the final drain,
  and performs test-fixture recovery only after the helper naturally exits.
  The ordinary root-alive canary continues to prove markerless
  same-executable helper cleanup.
- The browser root is stricter than a markerless helper: its current WMI
  `Name` and nonblank `ExecutablePath` must match the resolved `BrowserPath`
  exactly (canonical Windows path, case-insensitive). Missing or mismatched
  root executable identity is infrastructure failure; it is never adopted by
  matching run/profile markers.
- Every run-owned browser root and paired CDP ledger entry now carries that
  nonblank canonical resolved `BrowserPath` from the first lease/manifest write
  through parent/child ledgers. The integrated reader rejects a missing or
  mismatched value before ownership or cleanup, and the contract probe mutates a
  temporary completed cdp ledger/manifest fixture to prove the
  missing-configured-BrowserPath case is infrastructure failure. Caller-owned
  previews remain explicitly non-owned and are not subject to browser-root
  termination.
- `Resolve-VerifierBrowserPath` is shared by `verify-browser.ps1` and the
  opt-in supplemental live-Edge ownership canary. It checks explicit paths,
  PATH, `ProgramW6432`, both `ProgramFiles` trees (including the host's x86
  Edge installation), and the supported local Edge location. The deterministic
  default driver covers root-only marker handling, same-executable markerless
  ancestry, mandatory and missing descendant `ExecutablePath`, different-name,
  and reparent/PID-replacement rejection. The separate supplemental real-Edge
  canary
  launches the resolved executable and proves helper/profile/claim/listener/
  evidence/run-root cleanup when WMI is available; Edge/WMI unavailability is
  typed infrastructure exit `2`, never a skipped or fabricated PASS, and this
  canary does not replace visible Browser validation.
- The protected/default Gate B driver is source-checked to exclude the live-Edge
  call from its deterministic path; `-GateBRealEdgeOwnershipProbe` is the only
  live-Edge invocation and remains a separate truthful infrastructure lane.
- The cross-worktree contention child uses a Windows PowerShell 5.1-compatible
  `System.Diagnostics.Process` wait/refresh/stream/`ExitCode` path. An exit code
  that cannot be proven is infrastructure failure; the canary retains the
  child stdout/stderr, asserts the real competing child exit `2`, and records a
  structured child cleanup ledger for the manifest, claims, profiles, and
  leased-port inspection. Expected infrastructure child mismatches (`2` versus
  `0` or `1`) remain infrastructure failures.
- Integrated Task 43 child orchestration uses the same bounded process
  termination and numeric-exit proof. Null, unparseable, or otherwise
  unproven child status is infrastructure exit `2`; the deterministic contract
  probe exercises those cases while preserving expected child `0`/`1`/`2`,
  forced-negative marker, and positive false-pass behavior.
- Integrated children receive a parent-visible ledger containing their run root,
  manifest, lease/profile ledger, and cleanup state. A timeout proves only the
  exact parent PID termination; if child-finally cleanup cannot be proven, the
  child claim/evidence/root is retained. Gate B child and renderer subprocesses
  use one bounded asynchronous output/termination runner, with a workflow
  timeout backstop.
- A normally terminated integrated child must publish ledger state `completed`
  with complete cleanup even when its expected result is infrastructure exit
  `2`. The reader now requires canonical child run-root/manifest/evidence
  paths, matching manifest run/worktree identity, released claims with
  positively proven listener absence, no unrecorded claim files, absent
  profiles, and complete run-owned-server termination proof. It rejects
  malformed, foreign-path, stale-claim, cleanup-failed, and pathless ledgers.
  Only the explicit parent-timeout path may accept an incomplete ledger, after
  validating retained canonical manifest/evidence/claim/profile proof, and it
  always returns infrastructure failure. The contract probe covers completed
  zero-resource, expected-2 incomplete, malformed, foreign-path, stale-claim,
  cleanup-failed, and retained-resource cases. Completion flags are not
  trusted: completed lease/server/profile records are independently checked
  against current PID/start/parent/command identity, exact listener state, and
  relevant run/profile process references. Lease kinds/mutexes/claim filenames,
  canonical `scripts\\preview.ps1`, exact loopback-root BaseUrl, and direct
  server log paths are validated; the canary includes a real released lease,
  false-cleanup-flag rejection, a retained two-lease server fixture, and
  custom-mutex/wrong-script/non-loopback/wrong-port/foreign-log negatives.
- The shared Windows command-line parser accepts the exact outer-quoted
  switch/value form emitted by the PS5.1 argument builder. A helper-generated
  preview and browser command-line round trip verifies `-Port`, run/nonce,
  profile, worktree, and remote-debugging identity forms, including spaces;
  exact-token prefix negatives reject `--evil-*` markers and cannot authorize
  foreign/stale cleanup. Path-valued switches are compared after absolute
  Windows normalization (case, separators, `.`/`..`, and trailing separators),
  while opaque repository identity values remain exact tokens. One canonical
  Windows path routine is also used for repository identity hashing, claim and
  run/worktree/profile/script comparisons, preview state ownership, recovery,
  and stop-preview checks. The late-helper quiescence canary proves an
  equivalent foreign profile path blocks cleanup, and the PID-replacement
  canary rejects changed start, parent, and command identities.
- Verifier module import is guarded before run-context construction. Missing or
  malformed `VerifierIsolation.psm1` returns infrastructure exit `2` and
  retains an early `setup-failure.json` record where possible. The Gate B
  driver uses a typed expected-infrastructure resolver for actual `0`, `1`,
  `2`, null, and unparseable child statuses; ordinary application assertions
  remain non-infrastructure failures.
- All verifier-started browser, preview, and canary child paths use the shared
  Windows-safe argument builder. A real temporary script/profile launch whose
  worktree and profile paths contain spaces is checked with wait/refresh/
  `HasExited`/`ExitCode` proof before its exact root is removed.
- `start-preview.ps1` uses the shared bounded ProcessStartInfo runner for both
  `java -version` selection and its optional JDK8/GWT build invocation. The
  existing 15-minute workflow timeout remains a backstop, not the subprocess
  ownership proof.
- `start-preview.ps1` prints separate `Preview root URL` and `Preview page URL`
  lines. Caller-owned verifier guidance passes only the exact root URL; the
  page URL containing `/circuitjs.html?...` is explicitly not a verifier
  `BaseUrl`.
- Cleanup canaries treat every cleanup exception or unsuccessful result as
  infrastructure failure. Recursive root deletion is attempted only after
  exact claims, profiles, listeners, and lease-ledger state are proven gone;
  the cleanup-retention canary proves a live-listener failure retains the run
  root, manifest, and claim before a safe retry.
- A separate hanging-child canary starts an owned claim, times out through the
  bounded runner, proves exact parent termination, retains the child ledger and
  claim, and verifies an independent survivor run is not corrupted. A separate
  release-failure probe covers release, disposal, final-manifest, and
  post-delete/pre-final-state interruption failures. Listener `netstat.exe` and
  build Java/GWT calls use bounded redirected/streamed process wrappers with
  exact termination and numeric-exit proof; the build process canary exercises
  both exit capture and timeout classification.
- The deterministic matrix covers injected rollback, immediate same-port
  reacquisition, distinct temporary worktree roots, a separate competing
  process with expected infrastructure exit `2`, a separate foreign listener,
  malformed/error listener output, route/CDP deadline and protocol failures,
  monotonic severity, explicit exit-2 top-level results with and without the
  typed marker, bounded hanging CDP handshakes, durable tombstone recovery while
  a newer run reuses the port, the driver infrastructure-exit probe, and
  stale/foreign/helper profile-process protection when Windows process
  command-line inspection is available.

## Files changed

- `.github/workflows/windows-gate-b.yml`
- `scripts/VerifierIsolation.psm1`
- `scripts/build.ps1`
- `scripts/verify-browser.ps1`
- `scripts/verify-gate-b.ps1`
- `scripts/preview.ps1`
- `scripts/start-preview.ps1`
- `scripts/stop-preview.ps1`
- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/CODEX_TASK_REPORT.md`
- `DEVELOPMENT.md`
- `README.md`

## Validation

- Windows PowerShell 5.1 parser: PASS for all 9 scripts/modules.
- The elevated default `scripts/verify-gate-b.ps1 -SkipJdkCheck` run returned
  exit `0` and passed parser, source/static, workflow structure, GWT XML,
  module-import/setup, the
  WMI-backed root-gone retention/recovery canary, the late-markerless
  fixed-point canary, the normal root-alive markerless-helper descendant/
  reparse canary, and the remaining deterministic contracts. The default
  driver is deterministic/nonvisual and intentionally does not invoke live
  Edge. A separate elevated `-GateBRealEdgeOwnershipProbe` run found the
  installed x86 Edge, but Edge exited before its current identity proof; that
  opt-in supplemental lane therefore returned typed infrastructure exit `2`
  and retained its exact evidence namespace. It did not claim an Edge cleanup
  PASS or visible Browser validation.
- Standalone `-GateBRootGoneProbe`: PASS (exit `0`) under elevated WMI. The
  same root-gone fail-closed proof completed with exact test-fixture recovery;
  no root/profile/claim from the successful canary was deleted before its
  ownership and process-absence checks completed.
- Standalone `-GateBLateMarkerlessProbe`: PASS (exit `0`) under elevated WMI.
  The canary captured an empty graph, caused a same-executable markerless
  helper to appear afterward, and proved fixed-point cleanup released exact
  resources; its retained-resource infrastructure path is also fail-closed.
- Standalone `-GateBProcessOwnershipProbe`: PASS with WMI access for the
  relevant-record filter, root-only/markerless same-executable helper,
  mandatory/missing-descendant executable identity, different-name helper
  ancestry, stale/reparent/PID-replacement, prefix-marker, canonical-path, and
  late-profile cases. This is supplemental
  ownership evidence, not visible Browser validation.
- Standalone `-GateBHangingChildProbe`: PASS, with a bounded parent timeout,
  exact current-process stop, `WaitForExit`/`Refresh`/`HasExited` and numeric
  status proof, retained child ledger/claim evidence, and an unaffected
  independent survivor run.
- Standalone `-GateBLeaseReleaseProbe`, `-GateBPreviewIdentityFailureProbe`,
  `-GateBStopPreviewProbe`, `-GateBCdpHandshakeProbe`, and
  `-GateBArgumentPathProbe`: PASS (exit `0`). These cover transactional and
  durable lease rollback/port reuse, preview identity retention, exact
  stop-preview ownership, hanging CDP handshake classification, and a real
  quoted-spaces launch/identity round trip.
- Elevated `verify-browser.ps1 -GateBContractProbe`: PASS (exit `0`). It
  exercised route-deadline, CDP protocol, monotonic/explicit exit-2,
  null/unparseable child status, false-pass, completed/incomplete ledger,
  caller-owned preview, strict caller Boolean proof in both ledger/manifest
  copies, bijective resource, terminal tombstone, invalid-port, owner-identity,
  and canonical negative cases. The explicit probe-failure mode remains typed
  infrastructure exit `2`.
- `-GateBDriverInfrastructureProbe`: exact child exit `2`, as required;
  malformed/error/empty/nonzero listener-output negatives remain rejected.
  `scripts/build.ps1 -BuildProcessCanary`: PASS. Direct pinned GWT
  2.7.0/JDK8 selection remains infrastructure-blocked by host OpenJDK
  `21.0.8`; no Java was substituted. The successful build canary removed its
  exact exit/timeout process evidence roots; failures retain their evidence.
  `verify-renderer-boundary.ps1`: PASS.
- Successful canaries remove only exact namespaces after their ownership and
  quiescence proofs succeed. The separate opt-in real-Edge run intentionally
  retained the exact evidence namespace
  `<OS-temp>/TroubleshootJS\gate-b-real-edge-22245742a5dc401a8f53599e245cea53`
  because Edge exited before root identity/cleanup proof; its claim/profile
  were not guessed at or deleted. Earlier retained canary resources were
  recovered only through recorded ownership proofs; no wildcard or PID-only
  termination was used. No current canary resource was claimed as a visible
  Browser result.
- `README.md`, `DEVELOPMENT.md`, `docs/ARCHITECTURE.md`, and the Gate B source
  contract agree that the printed Preview root URL is the only caller-owned
  `-BaseUrl`; the page URL is opening guidance, not a verifier root.
- The Roadmap status legend explicitly defines `[~]` for exploratory,
  conditional, or pending final acceptance/publication milestones; Gate B is
  now recorded as `[x]` complete and no documentation minor is left
  unexplained.
- Run-owned visible preview/browser validation: not claimed. The WMI-backed
  process canaries are supplemental ownership evidence only; normal-player
  validation remains the separate required visible Browser lane.
- `git diff --check`: PASS. Final status contains only the bounded Gate B
  implementation/documentation/workflow paths listed above; no commit or push
  was performed.

## OWNER ACTION REQUIRED — mainline protection

Repository owner/admin must protect `master`, disallow force-push and branch
deletion, require the intended pull-request policy, require the exact workflow
check `Gate B Windows JDK8 deterministic verification`, and verify that check
on a test pull request after the workflow runs. Existing Foreman evidence says
the live protection read was unavailable with `403 Resource not accessible by
integration`; preserved historical Gate A evidence records a separate earlier
`401 Unauthorized` protection-read observation. These are both evidence of
unavailable reads at different checkpoints, not inferred settings. Workflows
were `404/not present`, and recorded Actions runs were `0`; no remote setting
was fabricated or mutated.

## Final review and publication

- Reviewer: PASS, no blockers or deferred items.
- Foreman: PASS after complete-diff review and a fresh elevated default-suite
  run returning exit `0`.
- Inspector: PASS, no blockers or deferred items.
- Publication: the final Gate B commit was pushed without force or history
  rewrite; the exact SHA and remote-ref verification are reported in the
  completion packet.
- Owner action remains required to protect `master`, disallow force-push and
  branch deletion, require the exact deterministic check, and verify it on a
  test pull request. This report does not claim that owner-only settings were
  configured.

## Task 43P Coder handoff — 2026-08-28

**Task:** Post-Task-43 Cross-Boundary Integrity Reconciliation

**Status:** IMPLEMENTED, ACCEPTANCE PENDING

**Baseline/current HEAD:** `3bfaab093f85247fc20aec068824c83dc3d214c8`; working
tree intentionally uncommitted.

**Implementation:** Added the query-gated developer-only
`Task43PDeveloperVerifier` and independent physical-triad
`Task43PPhysicalTruthDeveloperVerifier`. Added structured DOM evidence and
direct source/verifier digest capture to `scripts/verify-browser.ps1`, with
`-Task43P` and `-Task43PForcedNegative` routes. The verifier records an
explicit A-I matrix, current lifecycle/owner/part/terminal/solver evidence,
read-only digest checks, and in-memory negative canary results. No gameplay,
electrical model, CircuitJS physics, transaction, or epoch behavior changed.

**Files changed:**

- `src/com/lushprojects/circuitjs1/client/CirSim.java`
- `src/com/lushprojects/circuitjs1/client/Task43PDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/Task43PPhysicalTruthDeveloperVerifier.java`
- `scripts/verify-browser.ps1`
- relevant Task 43P status/report/evidence documentation, including
  `docs/task-evidence/task-43p/README.md`

**Validation:**

- `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF` — exit `0`, JDK `1.8.0_502`, GWT link succeeded.
- PowerShell parser — exit `0`.
- `git diff --check` — exit `0`.
- Task43P positive (`-Seeds 0`, port `8768`) and forced-negative (port `8769`)
  routes, plus direct Task43 (port `8770`) and Task43Integrated (port `8771`),
  returned exit `2`; typed infrastructure/ownership failure blocked page
  startup. No runtime JSON artifact is claimed.
- After the Task43P attempts, direct `-Task39`, `-Task40`, `-Task41`,
  `-QuickPlay`, `-Rc -Seeds 3`, `-StoredEnergy`, `-StressDamage`, and
  `-StressDamageNormalPlayer` attempts were made against caller-owned port
  `8773`/`8774`. Each stopped before page execution at the same WMI
  `Get-CimInstance Win32_Process` `Access denied` ownership/cleanup boundary;
  none is counted as a pass. The multi-seed stored-energy and RC attempt also
  retained typed cleanup uncertainty. The Foreman still owns the complete
  closed regression set.

**A-I matrix:** A, B, C, D, F, and G emit bounded `PARTIAL` checks; E emits
`PARTIAL` for temporal candidates and `UNPROVEN` otherwise; H and I emit
`UNPROVEN`. The current one-flag pending-verification and absent request/
board/session epoch are recorded, not strengthened.

**Physical triad/negative canaries:** independently authored manifest and
six in-memory canaries are implemented. Runtime triad execution and expected
forced-negative exit `1` were not proven because browser startup/cleanup
ownership returned WMI `Access denied`; no canary PASS is claimed.

**Visible browser:** not performed by the Coder; the Foreman must perform
visible built-in `@Browser` validation. **OPEN BLOCKER:** none reproduced as a
current product defect. The host ownership failure is infrastructure exit `2`.

**Next gate:** Foreman review and visible `@Browser` execution. Do not mark
Owner Review complete or start Task 44 from this candidate.

## Task 43P verifier false-pass repair — Coder handoff — 2026-08-29

**Task:** Focused Task 43P verifier false-pass repair.

**Status:** IMPLEMENTED, RUNTIME ACCEPTANCE UNPROVEN, RETURNED TO FOREMAN.

**Baseline/current HEAD:** published repair baseline
`20f83535163070a0688fcc0958715e6bc827d445`; no commit, amend, reset,
push, or Task 44 work was performed. The working tree is intentionally
dirty with only the bounded candidate paths.

**Implementation:** Replaced the post-extraction baseline-copy physical
canaries with independent raw logical-board, raw layout/copper, renderer,
package, and solver observation snapshots feeding one canonical,
set-completeness-first validator. Positive validation and all eight negative
fixtures use that same path: renderer-only `J1.1 +20px`, renderer lead-only
offset, raw-copper endpoint gap, raw-net mismatch, solver endpoint/node
mismatch, mirrored-package variant/transform mismatch, internally
self-consistent wrong mapping, and omitted terminal. The validator checks
duplicate/invalid/missing/extra pad and terminal IDs, exact
board/layout/manifest/package/renderer/solver sets, all pad coverage, every
trace endpoint, package catalog membership, expected component/package
ownership, actual `CircuitPostMeasurementEndpoint` element identity/class/post
and `CircuitElm.nodes[post]` net identity, and finite live readings. The NPN
`J1.1` manifest entry now matches the generator's `WireElm` post `0`; the
independent package catalog accepts the current mirrored connector
realization `DEFAULT_MIRRORED_X`/`MIRROR_X`.

Java/GWT emits no repository HEAD, baseline SHA, dirty/clean, source digest,
file-count, or evidence-path claim. `scripts/verify-browser.ps1` owns those
claims, rejects forbidden Java provenance fields, compares before/after HEAD,
status, source/verifier SHA-256, and file count, and binds forced-negative
diagnostics to the run, route, and published repair baseline. A final wrapper
guard preserves infrastructure exit `2` even when PowerShell's host reports
the child process as generic nonzero.

**Files changed:**

- `src/com/lushprojects/circuitjs1/client/Task43PDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/Task43PPhysicalTruthDeveloperVerifier.java`
- `scripts/verify-browser.ps1`
- `scripts/verify-task43p-source-experiments.ps1`
- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`
- `docs/research/POST_TASK_43_INTEGRITY_RECONCILIATION.md`
- `docs/task-evidence/task-43p/README.md`
- `docs/task-evidence/task-43p/candidate-manifest.json`

**Validation and exact results:**

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF` — exit `0`; JDK `1.8.0_502`; all five GWT permutations compiled and linked.
- PowerShell parser for `scripts/verify-browser.ps1` and `scripts/verify-task43p-source-experiments.ps1` — exit `0` (`PASS:verify-browser-parser`; source harness parser PASS).
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-renderer-boundary.ps1` — exit `0`.
- `git -c safe.directory=C:/Users/david/Desktop/TroubleshootJS diff --check` — exit `0`.
- Final wrapper-owned repository-state audit — HEAD remained the published
  repair baseline; source/verifier SHA-256 is
  `2b3b5419ad75f6ecad8aabcd4ec0647a80dfb40b74339302aead380343a21296`, file
  count `855`, and the pre-existing candidate worktree remained dirty with
  the same nine expected paths (eight tracked, one untracked harness).
- Six-family static manifest audit and Java forbidden-provenance audit — PASS; no `BoardSimulationBindings.getNetIdForEndpoint()` use remains in the Task 43P physical verifier.
- Three targeted `scripts/verify-task43p-source-experiments.ps1 -ExperimentId ... -JavaHome .tools/jdk8-download/jdk8u502-b07 -ProcessTimeoutSeconds 900` runs — each actual script exit `2`; final evidence paths are `<OS-temp>/TroubleshootJS\verify\task43p-source-experiments\task43p-source-experiments-cd6119174f7f48819783b14c40f60526.json` (renderer), `...\task43p-source-experiments-9e4e4edfd0974ac89890c10558f8f81a.json` (raw copper), and `...\task43p-source-experiments-3d39d727ba5f4af2adf737066289e2ce.json` (solver). Each compiled all five GWT permutations with exit `0`, attempted the compiled extraction route, returned runtime exit `2` because this host cannot construct `System.Net.HttpListener`, stopped its disposable preview, restored exact source bytes, and proved unchanged HEAD/status/source digest/file count (`2b3b5419ad75f6ecad8aabcd4ec0647a80dfb40b74339302aead380343a21296`, `855`).
- `powershell -NoProfile -Command '& { & powershell -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\verify-browser.ps1 -Task43P -Seeds 0 -TimeoutSeconds 30; $code=$LASTEXITCODE; Write-Output ("SCRIPT_EXIT=" + $code); exit 0 }'` — actual verifier exit `2`; final retained manifest `<OS-temp>/TroubleshootJS\verify\20cd7fcd353094d844fb\05d57eaac00c47989a59dbdefb2dbfe6\manifest.json`.
- The equivalent `-Task43PForcedNegative -Seeds 0`, `-Task43 -Seeds 0`, and `-Task43Integrated -Seeds 0` commands — actual exits `2`; final forced-negative infrastructure evidence is retained at `<OS-temp>/TroubleshootJS\verify\20cd7fcd353094d844fb\2a0c90d122194f4db714730bcb572f92\manifest.json`; the current regression loop also retained `8f3aed61108a49dfb33ff7a566f56ee6` for Task43 and `9cc9cddf54f44b64a2a0610dfdafe097` for the integrated parent. No DOM/runtime Task43P JSON was reached.
- The same nested-capture route commands for `-Task39`, `-Task40`, `-Task41`, `-QuickPlay`, `-Rc -Seeds 0`, `-StoredEnergy -Seeds 0`, `-StressDamage -Seeds 0`, and `-StressDamageNormalPlayer -Seeds 0` — each actual exit `2` before page execution at the WMI process-identity boundary. These are attempted regression routes, not passes.

**Real mutation status:** The source harness ran each mutation in an isolated
disposable copy and restored exact bytes. Renderer-only `J1.1 +20px` changed
SHA-256 `5d51d4d5f2a7b10cac6028f25eacd5d79a0d0fbfc0670af1dc6e3c4630dc492f`
to `65ebf96a3cd5215f4d593c7f4ef4b52309adb75342739b413e4b029fbb682ee8`
and restored it. The raw-copper producer mutation in
`GeneratedBoardInstance.java` changed
`a820d66978e3b0638022c016ae0cb09781574cfe831216707e16fb0ea85e923c` to
`fd6073a6c4e7993960fc1966886f4eaf693d9817265bf31d2e0dbceead092008` and
restored it. The solver producer mutation in
`BoardSimulationBindings.java` changed
`505e68d2e61cf36a86b05fb868eaf2a475e2bf20e243fc2834343da877015d2b` to
`3e0f94176612e9e2881cf017f133cc9d9f1fdb9398c93a515bea9e1e3ff2b57b` and
restored it. All three disposable five-permutation compiles exited `0` and
all three compiled extraction routes were attempted; the host preview
constructor failure made each experiment `UNPROVEN`/exit `2`, not mutation
acceptance. Raw-net, mirrored package, internally self-consistent wrong
mapping, omitted-terminal, and renderer lead-only cases remain
same-canonical-path source-snapshot fixtures and are not durable runtime
source-mutation acceptance. No committed-tree mutation remains.

**Visible browser / uncertainty:** Built-in visible `@Browser` validation was
not performed. Every live route stopped at process identity/cleanup because
`Get-CimInstance Win32_Process` returned `Access denied`; exact run-owned
claims/manifests were retained rather than guessed clean. Therefore Task43P
remains `UNPROVEN`, the expected forced-negative application exit `1` was not
reached, and Owner Review/Task43P acceptance remain incomplete. No product
defect, browser pass, or publication SHA is claimed.

**Next gate:** Foreman review, required visible built-in `@Browser` evidence,
and any permitted final publication actions. Task 44 was not started.

## Task 43P delta remediation loop — Coder handoff — 2026-08-30

**Status:** IMPLEMENTED, RUNTIME ACCEPTANCE UNPROVEN, RETURNED TO FOREMAN.

This same candidate repaired the remaining delta-review blockers without
agents, commits, resets, or Task 44 work. The disposable source harness now
passes an explicit disposable repository/script/web root triplet to the
repository wrapper and requires a matching preview identity before extraction
can run. The wrapper uses those roots only as the compiled execution surface;
actual checkout HEAD, status, source SHA-256/file count, and run-owned
evidence remain wrapper-owned.

The physical verifier adds manifest/raw logical net-ID set equality, including
empty unmanifested nets, and its solver negative selects a guaranteed
class/post/node-incompatible observation without vector-order assumptions.
If no such observed endpoint exists, it mutates the captured source
observation's node to a deterministic invalid value, which the same canonical
validator rejects. Java Task 43P evidence now passes a complete exact nested
schema allowlist; null/array/malformed payloads and unknown/case/format alias
fields fail before persistence. Java state fingerprints are named
`verifierDesignStateBefore/After`, not repository digest fields. Post-module
wrapper evidence directory creation and text/byte writes use typed
infrastructure helpers, so persistence failures remain exit `2`.

**Files changed in this loop:**

- `src/com/lushprojects/circuitjs1/client/Task43PDeveloperVerifier.java`
- `src/com/lushprojects/circuitjs1/client/Task43PPhysicalTruthDeveloperVerifier.java`
- `scripts/verify-browser.ps1`
- `scripts/verify-task43p-source-experiments.ps1`
- `docs/ARCHITECTURE.md`
- `docs/CODEX_TASK_REPORT.md`
- `docs/research/POST_TASK_43_INTEGRITY_RECONCILIATION.md`
- `docs/task-evidence/task-43p/README.md`
- `docs/task-evidence/task-43p/candidate-manifest.json`

**Targeted results:**

- JDK8/GWT OBF compile: exit `0`; all five permutations linked.
- PowerShell 5.1 and bundled `pwsh` parser checks: exit `0`.
- Wrapper explicit-root contract against an unreachable loopback URL: captured
  child exit `2`, typed infrastructure failure, cleanup complete.
- Combined renderer/raw-copper/solver producer record
  `<OS-temp>/TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-be4175476c5f465cbbad9747853a7daa.json`:
  each mutation compiled with exit `0`, attempted runtime extraction, returned
  runtime exit `2`/UNPROVEN, restored exact target bytes, stopped its preview,
  and proved repository unchanged.
- Repository-aware `git diff --check`: exit `0` (normal line-ending warnings
  only).
- Final wrapper-owned candidate state: HEAD remained
  `20f83535163070a0688fcc0958715e6bc827d445`; source/verifier digest
  `77d722d6ae2787ba7ed80bd3f28c9728f7bb0f2ec741ee18d4579c94fb01f3bf`, file
  count `855`, and dirty state `true`.

The source experiments reached the preview process but this host failed at
`System.Net.HttpListener` before disposable identity completion. The evidence
records `runtimeAgainstMutatedSource=true` for all three attempts, while
`runtimeExecutionRootsValidated=false` because the preview could not publish
its identity. Therefore the new root handoff has not been credited with
runtime mutation rejection; compile/runtime-attempt results remain unproven.
Built-in visible `@Browser` evidence
and WMI process-ownership proof remain unavailable, so Task 43P stays
UNPROVEN and Owner Review is incomplete. No commit SHA is supplied; the
Foreman owns review and publication.

## Task 43P final solver identity delta — Coder handoff — 2026-08-30

**Status:** IMPLEMENTED, RUNTIME ACCEPTANCE UNPROVEN, RETURNED TO FOREMAN.

The solver phase now re-fetches
instance.getSimulationBindings().getEndpoint(padId) for every manifest pad
and requires exact CircuitPostMeasurementEndpoint element identity and post
equality with the retained detachable binding endpoint or the matching
fixed-generated physical-part terminal endpoint. Missing or malformed
detachable/fixed ownership, invalid endpoint types/posts, unowned elements,
and non-touching detachable connection geometry are verifier failures. The
actual CircuitJS node at the live post is still compared to the source
observation and grouped against the independent raw logical-board net oracle;
distinct manifest nets must not share a node and all readings remain finite.
No getNetIdForEndpoint shortcut or copied endpoint/post tautology was
introduced.

The canonical negative first anchors its target to the retained producer
endpoint, then sorts source observations and prefers a different element with
the same node/class/post, directly covering the RC J1.1/R1.1 collision when
present. It falls back to independent
class/post/node incompatibility or a deterministic invalid node only when no
observed incompatible endpoint exists. The disposable solver producer
mutation now similarly prefers R1.1, then RLOAD.1, then J1.2, in the actual
BoardSimulationBindings lookup path.

**Targeted validation:**

- JDK8/GWT OBF compile after the identity delta: exit 0; all five
  permutations linked.
- PowerShell parsers for the wrapper and source harness: exit 0.
- scripts/verify-renderer-boundary.ps1: exit 0.
- solver identity static audit and git diff --check: exit 0 (line-ending
  warnings only).
- Refreshed source record
  <OS-temp>/TroubleshootJS\verify\task43p-source-experiments\task43p-source-experiments-1ee260b7a3c94b609830e475f3dc4491.json:
  renderer, raw-copper, and solver producer mutations each compiled with exit
  0, attempted the matching disposable-root runtime route, and returned
  runtime exit 2/UNPROVEN before identity completion because
  System.Net.HttpListener is unsupported. Each mutation was byte-restored
  exactly; repository HEAD/status/source digest/file count were unchanged.

The source-experiment run captured wrapper-owned HEAD
20f83535163070a0688fcc0958715e6bc827d445, source/verifier digest
62f9923882167c36115c0e3d6255a9727cc87b2bcd0d90d4229b2fac5cc56919, file
count 855, and dirty state true. The forced-negative and visible @Browser
application routes remain unproven/exit 2 on this host; no runtime mutation
rejection, acceptance, Owner Review, or commit SHA is claimed. The strict
missing-binding behavior may surface existing non-detachable connector pads
as a verifier failure when the browser route is eventually reachable; this
was not softened or hidden.

The final direct wrapper command was
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\\verify-browser.ps1 -Task43P -Seeds 0 -TimeoutSeconds 30`.
It failed closed as infrastructure exit `2` while proving preview process
identity/cleanup (`Process PID 13600 disappeared before current-identity proof`;
WMI `Access denied` while proving absence). Its retained run manifest is
`<OS-temp>/TroubleshootJS\\verify\\20cd7fcd353094d844fb\\44068b4a89be4e2f91c13efeee006e98\\manifest.json`;
cleanup was deliberately not represented as complete.

## Task 43P fixed connector endpoint boundary — Coder handoff — 2026-08-30

The solver validator now resolves one retained producer endpoint for every
manifest pad. A detachable pad uses
`instance.getConnectionBindings().get(componentId, padId).getBoardEndpoint()`
and retains the detachable connection-element geometry check. A pad without a
detachable binding uses the installed part from
`getPhysicalBoardRuntime().getInstalledPart(componentId)`, but only when it is
the mounted `FIXED_GENERATED` physical part with matching provenance source,
component slot, package, and terminal. Its `PhysicalPartTerminal` endpoint is
retained at fixed-part construction (the foundation factory supplies this
boundary for fixed foundation parts). Missing or mutable fixed parts,
malformed terminals/packages, and unreadable bindings fail closed. The live
`BoardSimulationBindings` endpoint is compared to that retained endpoint by
exact CircuitElm identity and post index before independent class/post/net,
node, ownership, and finite-voltage checks.

The disposable solver mutation remains on the real
`BoardSimulationBindings.getEndpoint` producer path. Its first J1.1 read is
left untouched for fixed-part construction; later reads redirect to a
different observed endpoint, preserving the fixed terminal's retained source
while mutating the live binding path. It is therefore not a copied solver DTO
canary. The mutation is isolated, byte/SHA restored, and paired to the
compiled disposable root; no runtime rejection is claimed because this host
still fails at the `System.Net.HttpListener` preview boundary (exit `2`).

The refreshed source record is
`<OS-temp>/TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-a1e8de2ba420464086e03d52a6b70147.json`.
All three isolated mutations compiled with exit `0`, attempted runtime
extraction, returned exit `2`/UNPROVEN at the unsupported preview boundary,
restored exact bytes, stopped their disposable previews, and proved unchanged
HEAD/status/source digest/file count. The wrapper-owned source/verifier digest
for this checkpoint is
`fd2ea0629d799601fca2fcde611765267845b4907f18133061416b57e22d5af3` with file
count `855`.

Targeted JDK8/GWT, parser, static, and diff checks remain required after this
delta. Browser/WMI infrastructure and visible `@Browser` evidence remain
unproven; no acceptance or publication SHA is claimed.

## Task 43P final remediation corpus and NPN audit — Coder handoff — 2026-08-30

**Status:** IMPLEMENTED, RUNTIME ACCEPTANCE UNPROVEN, RETURNED TO FOREMAN.

The NPN manifest was re-audited against `NpnLowSideSwitchGenerator` in full.
It now records `J1.1` as `LOAD_SUPPLY / WireElm / 0` and `LED1.K` as
`COLLECTOR / WireElm / 0`; all other NPN terminals remain reconciled to the
current board, package, and solver bindings. No generator, CircuitJS,
gameplay, repair, or Task 39/40/41 source was changed.

The disposable source harness now contains nine individually named,
producer-path experiments: renderer pad `J1.1 +20px`, renderer lead `J1.1
+20px`, raw-copper endpoint gap, raw logical-net mismatch, fixed solver
identity redirect, detachable solver identity redirect (with RC's same-node
different-element candidate), package/mirror transform mismatch, internally
self-consistent R1 pad-net remapping, and omitted manifest terminal. Each
mutation is made in an isolated disposable source tree and passed through the
same compiled route setup; no DTO-only fixture is described as runtime
acceptance proof.

**Final source-experiment evidence:**

`<OS-temp>/TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-027ac10835a24e31a91625b9bbcfee85.json`

- All nine disposable OBF compile exits: `0`.
- All nine runtime results: exit `2` / `UNPROVEN`; the host cannot construct
  `System.Net.HttpListener` before preview identity and wrapper extraction.
- All nine records: `runtimeAgainstMutatedSource=true`, exact source-byte
  restoration, unchanged wrapper-owned HEAD/status/source digest/file count,
  stopped preview proof, and zero cleanup errors.
- No runtime mutation rejection, visible `@Browser`, or Task 43P acceptance
  is claimed.

`Get-Task43PRepositoryState` now wraps Git state, source enumeration, hashing,
and count reads in a typed infrastructure boundary. Standard repository/
evidence I/O, process, transport, ownership, and timeout failures are also
classified fail-closed when a route catch reports them. The strict nested Java
schema remains the gate against unknown or aliased repository-authority
properties before persistence.

The candidate remains uncommitted at published baseline HEAD
`20f83535163070a0688fcc0958715e6bc827d445`; no commit SHA is supplied.

## Task 43P verifier process-identity repair — Coder handoff — 2026-08-30

**Status:** IMPLEMENTED, RUNTIME ACCEPTANCE UNPROVEN, RETURNED TO FOREMAN.
Task 43P remains NOT ACCEPTED, Owner Review remains NOT READY, and Task 44
remains BLOCKED/UNSTARTED.

`Get-VerifierProcessStartTicks` now accepts only a retained real
`System.Diagnostics.Process`, refreshes and proves that same object is alive,
reads its non-null `StartTime`, converts that value to positive UTC ticks, and
uses a monotonic five-attempt/250 ms bound for transient accessor failures.
It never looks up a replacement process by PID and has no CIM/WMI timestamp
fallback. Null, malformed, disposed, non-positive, natural-exit, and
permanently unavailable identities remain typed verifier infrastructure
failures. All verifier-side direct `StartTime` readers are routed through the
shared contract; the sole raw property boundary is the helper itself.

The Gate B driver now includes a real retained-process canary for positive
current/child identities, transient null/unavailable accessor recovery,
permanent-unavailability timeout, malformed/null/non-process/disposed input,
and natural-exit failure. Existing ownership/PID-reuse and retained-claim
canaries remain in place.

**Repair files changed:**
`scripts/VerifierIsolation.psm1`, `scripts/verify-browser.ps1`,
`scripts/start-preview.ps1`, `scripts/preview.ps1`, `scripts/build.ps1`, and
`scripts/verify-gate-b.ps1`. The preview reader was also corrected to call the
exported bounded helper; its child now reaches the host listener boundary.

**Validation:** pinned OpenJDK 8u502/GWT compile and link passed (exit `0`;
five OBF permutations). PowerShell parser/source checks passed (10 files and
the Gate B isolation/source contract, exit `0`). The targeted retained
real-process identity canary passed (exit `0`). The complete deterministic
Gate B driver reached its source/static and module checks, then failed closed
with verifier infrastructure exit `2` at the root-gone WMI/CIM canary because
this host returned `Access denied` (the shell wrapper reports nonzero exit
`1`). `git diff --check` passed.

The required exact commands were run after the repair:

- `.\scripts\verify-browser.ps1 -Task43PForcedNegative` did not reach the
  anchored Java forced-negative canary. It failed closed at preview startup
  after the child reached `System.Net.HttpListener`, whose constructor is
  unsupported on this host; cleanup separately retained the claim because
  WMI absence proof returned `Access denied`.
- `.\scripts\verify-browser.ps1 -Task43P -Seeds 0,2,3` had the same
  infrastructure result before application execution or durable Task43P
  route evidence. No runtime mutation rejection, visible `@Browser` proof,
  Task43P acceptance, commit, or push is claimed.

The dirty candidate remains at published baseline HEAD
`20f83535163070a0688fcc0958715e6bc827d445`; all pre-existing candidate
changes and retained evidence were preserved.

## Task 43P retained board-endpoint oracle correction — Coder handoff — 2026-08-30

The solver identity boundary now retains an immutable
`GeneratedBoardEndpointOracle` in `GeneratedBoardInstance`, captured directly
from the authoritative board binding map before composition marks the instance
ready for developer verification. The live
`BoardSimulationBindings.getEndpoint(padId)` result is compared to that oracle
for every manifest pad. Detachable pads additionally require exact agreement
with `GeneratedComponentConnectionBinding.getBoardEndpoint()` and retain the
existing detachable connection-element point/ownership check. Non-detachable
pads use the board oracle rather than an internal component terminal; installed
`FIXED_GENERATED` parts are checked for mounted component/package/terminal
consistency, and only `FixedPhysicalPart` foundation terminals are endpoint
cross-checked. This preserves NPN/NMOS intermediate WireElm/GroundElm board
endpoints while rejecting exact-element/post redirects, including RC's
same-node/different-element collision.

The real detachable solver experiment is now named
`solver-detachable-c1-plus-identity-mismatch`; it targets RC `C1.+`, which has
the detachable binding. The fixed J1.1 identity case remains separate. Both
redirect only live binding lookups after the retained oracle and composition
validation are complete.

The full nine-case source corpus record is
`<OS-temp>/TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-a702ea7b3abe44c4853d146daccb360e.json`.
All nine compiled all five OBF permutations with exit `0` and attempted the
matching disposable-root route against the mutated source. All nine returned
exit `2`/`UNPROVEN` because this host cannot construct
`System.Net.HttpListener` before preview identity; no runtime rejection or
visible `@Browser` proof is claimed. Each restored exact target bytes and
proved unchanged baseline HEAD/status/source digest/file count, with zero
cleanup errors. The wrapper-owned current source/verifier digest is
`e1a30e2b16648e5c56caaf6318a96bf0a65e19fc1158aff365c948f336498baa`, file
count `856`, with 12 status lines and dirty state `true`; the candidate
remains uncommitted at the published baseline.

## Task 43P verifier identity retry classification repair — Reviewer delta — 2026-08-30

**Status:** IMPLEMENTED, RUNTIME ACCEPTANCE UNPROVEN, RETURNED TO FOREMAN.
Task 43P remains NOT ACCEPTED, Owner Review remains NOT READY, and Task 44
remains BLOCKED/UNSTARTED.

The shared identity helper now separates raw `StartTime` acquisition from
validation. Only no-output or explicit single-value `$null` results retry on
the same live retained `System.Diagnostics.Process`; accessor exceptions are
typed infrastructure failures immediately. Multiple outputs, malformed or
non-positive values, invalid UTC conversion, wrong/disposed inputs, and other
validation failures do not enter the retry loop. No PID lookup, PID reuse,
CIM timestamp, or wall-clock identity fallback was added.

The focused Gate B canary now proves: current and newly spawned real-process
positive identities; no-output and explicit-null transient recovery in 3
attempts; permanent null timeout in 5 attempts/153.5 ms; and first-attempt rejection for
malformed, non-positive, multiple-output, and arbitrary-exception accessors
(1 attempt each). Natural-exit, ownership/PID-reuse, retained-claim, and
preview identity-retention canaries remain fail-closed.

Focused canary result: exit `0`. Full Gate B parser/source/static checks
passed, then the deterministic driver failed closed at the known WMI/CIM
root-gone canary with infrastructure exit `2` because this host returned
`Access denied`; no host exit `2` is treated as a pass. This delta changed no
Java files; the prior pinned JDK8/GWT compile remains the applicable compile
evidence. No Task43P runtime acceptance or visible `@Browser` proof is
claimed, and the candidate remains uncommitted at the published baseline.

## Task 43P PID 4 HTTP.sys listener-path repair — Coder handoff — 2026-08-30

**Status:** IMPLEMENTED, RUNTIME ACCEPTANCE UNPROVEN, RETURNED TO FOREMAN.
Task 43P remains NOT ACCEPTED, Owner Review remains NOT READY, and Task 44
remains BLOCKED/UNSTARTED.

The listener ownership path now classifies PID 4/System HTTP.sys as an
explicit `kernel-transport` owner only when the validated listener query
record is PID 4 and the OS System process evidence is present. The record
retains `ProcessStartTicks = null`; it never calls the bounded process-start
identity helper, reads `StartTime`, invents a timestamp, or uses PID 4 as a
process identity. User-mode listener owners retain positive Diagnostics
start identity and the existing exact ownership checks.

Kernel transport authorization is restricted to the exact retained,
run-owned preview after its identity handshake: positive preview and parent
identities, command/script/run/nonce/worktree evidence, lease binding, and
the preview identity protocol are all required. Browser, caller-owned,
unverified, missing-proof, malformed, or generic listener paths remain
rejected. Durable lease/manifest records preserve the owner kind, proof, and
evidence. Release/revalidation is absence-only for the kernel transport;
PID 4 is never terminated, and a remaining or ambiguous listener retains
the claim and fails closed.

**Repair files changed:** `scripts/VerifierIsolation.psm1`,
`scripts/verify-browser.ps1`, `scripts/verify-gate-b.ps1`, and this report.
No Java or unrelated pre-existing Task43P candidate changes were altered by
this delta.

**Validation:**

- Focused retained-process identity canary: exit `0`; transient null retry
  succeeded in 3 attempts, permanent unavailable identity stopped at 5
  attempts/142.3 ms, and malformed/non-positive/multiple/arbitrary accessors
  each failed on attempt 1.
- Focused PID 4 ownership canary: exit `0`; exact run-owned preview proof was
  accepted, caller/browser/unverified/missing-proof and malformed records
  were rejected, and the PID 4 termination guard rejected the attempted
  termination without killing it.
- Full Gate B parser/source/static path: parser, source, workflow, GWT module,
  import, and PID 4 checks passed; the driver then failed closed at the
  existing root-gone WMI/CIM check with verifier infrastructure exit `2`
  (`Access denied`; wrapper process exit `1`). This is not a pass.
- `git diff --check`: exit `0` (only existing LF/CRLF conversion warnings).

The exact `powershell.exe -NoLogo -NoProfile -NonInteractive
-ExecutionPolicy Bypass -File .\\scripts\\verify-browser.ps1
-Task43PForcedNegative` command returned shell exit `1`, but did not reach the
anchored Java forced-negative canary. On this host the retained preview child
exited before its start identity was established; cleanup also failed closed
because Win32_Process absence and release ownership proof returned `Access
denied`. No exit `1` or `2` is treated as acceptance, and no visible
`@Browser` evidence or Task43P runtime acceptance is claimed. The candidate
remains dirty and uncommitted at baseline HEAD
`20f83535163070a0688fcc0958715e6bc827d445`.

## Task 43P live listener-consumer schema repair — Coder handoff — 2026-08-30

**Status:** IMPLEMENTED, RUNTIME ACCEPTANCE UNPROVEN, RETURNED TO FOREMAN.
Task 43P remains NOT ACCEPTED, Owner Review remains NOT READY, and Task 44
remains BLOCKED/UNSTARTED.

The runtime module now has one exact `Test-VerifierListenerRecordSchema`
boundary. It requires explicit loopback/port/PID/source and owner
kind/proof/evidence fields. User-process records require the exact Diagnostics
start-proof tuple and a positive start identity. Kernel HTTP.sys records
require the exact PID-4 tuple plus the exact run-owned preview identity
handshake context. Missing, legacy, arbitrary, mixed, malformed, and
unauthorized records fail closed; no consumer defaults a missing kind to
user-process. Inspection creation, lease inspection storage, bound-port
validation, listener ownership, and release validation all use the shared
schema.

**Delta files changed:** `scripts/VerifierIsolation.psm1`,
`scripts/verify-browser.ps1`, `scripts/verify-gate-b.ps1`, and this report.
No Java files or unrelated pre-existing Task 43P candidate changes were
modified.

**Validation:**

- `verify-gate-b.ps1 -SkipJdkCheck -GateBListenerRecordConsumerProbe`:
  exit `0`. Direct ownership, inspection creation, lease-setting, bind, and
  release consumers rejected missing kind/proof/evidence, legacy/arbitrary
  tuples, malformed ports, and non-positive starts; exact user records passed.
- `verify-browser.ps1 -GateBListenerProofProbe`: exit `0`. User and kernel
  proof preservation across absence, terminal validation, remaining-listener
  rejection, and per-copy omission rejection remain green.
- `verify-gate-b.ps1 -SkipJdkCheck -GateBProcessStartIdentityProbe`: exit `0`;
  transient attempts `3`, unavailable attempts `5` over `164.8 ms`, and all
  malformed/non-positive/multiple/arbitrary cases rejected on attempt `1`.
- `verify-gate-b.ps1 -SkipJdkCheck -GateBKernelTransportProbe`: exit `0`;
  exact preview-only PID-4 authorization and no-kill rules remain green.
- PowerShell parser: exit `0`; full Gate B parser/source/static/workflow/GWT
  XML/import/PID-4/live-consumer stages passed, then the default deterministic
  run failed closed with verifier infrastructure exit `2` and shell exit `1`
  because the host returned `Access denied` for complete `Win32_Process`
  inspection.
- The exact `verify-browser.ps1 -Task43PForcedNegative` command returned shell
  exit `1` without anchored Java forced-negative evidence. It failed before
  Java because retained preview PID `22152` exited before identity
  establishment; cleanup retained port `60671` after PID/absence proof also
  returned `Access denied`. This is not a pass.
- `git diff --check`: exit `0`; only existing LF/CRLF normalization warnings
  were emitted.

No visible `@Browser` or Task43P acceptance is claimed. The dirty candidate
remains uncommitted at baseline HEAD
`20f83535163070a0688fcc0958715e6bc827d445`.

## Task 43P listener proof preservation and strict ledger schema repair — Coder handoff — 2026-08-30

**Status:** IMPLEMENTED, RUNTIME ACCEPTANCE UNPROVEN, RETURNED TO FOREMAN.
Task 43P remains NOT ACCEPTED, Owner Review remains NOT READY, and Task 44
remains BLOCKED/UNSTARTED.

`Set-VerifierLeaseListenerInspection` now changes the durable listener
owner tuple only for a positive listener observation. A later positively
proven absence updates inspection/absence flags while preserving the last
user-process or kernel-transport kind/proof/evidence needed to interpret the
retained listener PID/start fields. New/unbound leases remain explicit
`none`/empty/empty records.

The integrated ledger reader now requires explicit listener PID, start,
owner kind, owner proof, and owner evidence fields in both ledger copies.
Positive user-process listeners require the exact Diagnostics start tuple and
positive start identity; PID 4 requires the exact preview-only HTTP.sys tuple
and JSON null start. Missing fields, empty positive proof, mismatches, PID 4
fallbacks, and malformed schemas fail closed. Normalization preserves
missing values as invalid rather than manufacturing a user-process default.

**Delta files changed:** `scripts/VerifierIsolation.psm1`,
`scripts/verify-browser.ps1`, `scripts/verify-gate-b.ps1`, and this report.
No Java files or unrelated pre-existing Task43P candidate changes were
modified.

**Validation:**

- `verify-browser.ps1 -GateBListenerProofProbe`: exit `0`. User-process and
  kernel proof was stored on positive bind, preserved after positive absence,
  both terminal records validated, remaining listeners were rejected, and
  removing each of kind/proof/evidence from each ledger-copy role was
  rejected.
- Retained process identity canary: exit `0`; transient attempts `3`,
  unavailable attempts `5` over `143.0 ms`, malformed/non-positive/multiple/
  arbitrary accessors each rejected on attempt `1`.
- PID 4 ownership canary: exit `0`; exact preview proof accepted,
  caller/browser/unverified/missing-proof/malformed cases rejected, and PID
  4 termination rejected.
- Full Gate B with `-SkipJdkCheck`: parser/source/workflow/GWT-module/import/
  PID-4 checks passed; it failed closed at the existing root-gone
  Win32_Process/WMI check with verifier infrastructure exit `2` and shell
  exit `1` because the host returned `Access denied`.
- Browser contract probe reached the new listener-proof PASS line, then
  failed closed at the existing relevant-browser ownership query with
  `Access denied` (shell exit `1`).
- `git diff --check`: exit `0`.

The exact `powershell.exe -NoLogo -NoProfile -NonInteractive
-ExecutionPolicy Bypass -File .\\scripts\\verify-browser.ps1
-Task43PForcedNegative` command returned shell exit `1` without anchored Java
forced-negative evidence. The final run failed before Java because retained
preview child PID `44096` exited before identity establishment; cleanup also
retained the port `50065` claim after Win32_Process absence/release proof
returned `Access denied`.
No exit `1` or `2` is a pass, and no visible `@Browser` or Task43P acceptance
is claimed. The dirty candidate remains uncommitted at baseline HEAD
`20f83535163070a0688fcc0958715e6bc827d445`.

## Task 43P forced-negative proof gate repair loop — Coder handoff — 2026-08-30

**Status:** IMPLEMENTED, RUNTIME ACCEPTANCE UNPROVEN, RETURNED TO FOREMAN.
Task 43P remains NOT ACCEPTED, Owner Review remains NOT READY, and Task 44
remains BLOCKED/UNSTARTED.

`verify-browser.ps1` now carries an explicit per-run/per-route forced-negative
proof record. It separately records the exact expected marker, marker
observation, anchored Java diagnostic, run/route identities, route success
after browser cleanup, final verifier cleanup, and invalidation. The only
proof reset is at forced-route entry; later route, identity, evidence, child,
or cleanup uncertainty invalidates the proof monotonically. Both direct forced
routes and the final top-level exit gate require the complete proof before
returning application exit `1`. Ordinary non-forced application
classification is unchanged.

Integrated expected-exit-1 children now emit the proof record in the parent
ledger and the reader/output contract requires the exact marker, anchored
diagnostic, matching nonempty child run/route identities, strict Boolean
marker/route/cleanup fields, and non-invalidated state. The legacy
`-Task43ForcedNegative` route remains the integrated expected-1 route. The
child `$?`/`$LASTEXITCODE` boundary is fail-closed for stale, missing, or
nonzero status. Gate B includes bounded deterministic cases for exact proof,
preview/identity/WMI uncertainty, missing or wrong evidence, later cleanup
failure, ordinary legacy proof, and integrated expected-1 without proof.

**Delta files changed:** `scripts/verify-browser.ps1`,
`scripts/verify-gate-b.ps1`, and this report. No Java, electrical/PCB, module,
PID4/HTTP.sys implementation, or unrelated pre-existing dirty change was
modified by this loop.

**Validation:**

- PowerShell parser for both changed verifier scripts: exit `0`.
- `verify-browser.ps1 -GateBContractProbe`: all new forced-negative canary
  lines passed, including exact proof -> `1`, every uncertainty/missing/wrong/
  later-cleanup case -> `2`, ordinary legacy proof -> `1`, and integrated
  expected-1 without proof -> infrastructure `2`. The overall probe exited
  with actual script exit `2` at the existing relevant-browser ownership
  query because the host returned `Access denied` for `Win32_Process`.
- `verify-gate-b.ps1 -SkipJdkCheck`: parser/source/workflow/GWT XML/module
  import/PID4/listener stages passed; actual script exit `2` at the existing
  root-gone Win32_Process/WMI `Access denied` check.
- `verify-gate-b.ps1 -SkipJdkCheck -GateBProcessStartIdentityProbe`:
  exit `0`; bounded retained-process identity cases passed.
- `verify-gate-b.ps1 -SkipJdkCheck -GateBKernelTransportProbe`: exit `0`;
  exact PID4/HTTP.sys proof and no-kill semantics passed.
- `verify-gate-b.ps1 -SkipJdkCheck -GateBListenerRecordConsumerProbe`:
  exit `0`; live listener consumers rejected missing/arbitrary/malformed
  proof and accepted only the exact user-process tuple.
- Required `verify-browser.ps1 -Task43PForcedNegative` and
  `verify-browser.ps1 -Task43P -Seeds 0,2,3`: both actual script exits `2`.
  Neither output contained the expected `FAIL:task43p-forced-negative-canary`
  DOM result or an anchored Java diagnostic; both failed before Java at
  retained preview/process identity and cleanup WMI/CIM uncertainty. The
  unwrapped shell surface reported `1` in one invocation, but explicit
  `$LASTEXITCODE` capture proved the script exit was `2`; no shell `1` is
  treated as proof.
- `git diff --check`: exit `0`; only existing LF/CRLF normalization warnings.

The deterministic canary’s synthetic exact diagnostic input was
`Console failure: exception in runCircuit java.lang.IllegalStateException:
Generated board verification failed for led/controlled-indicator, seed 3:
task43p-forced-negative-canary`; it is contract coverage, not product/browser
evidence. No visible `@Browser` evidence or Task43P acceptance is claimed.
The candidate remains dirty and uncommitted at baseline HEAD
`20f83535163070a0688fcc0958715e6bc827d445`.

## Task 43P forced-negative integrated exit-0 false-pass remediation — Coder handoff — 2026-08-30

**Status:** IMPLEMENTED, RUNTIME ACCEPTANCE UNPROVEN, RETURNED TO FOREMAN.

`Assert-IntegratedChildOutputContract` now recognizes an integrated
forced-negative expected-exit-1 child before calling the generic
`Test-VerifierChildContract`. It requires actual child exit exactly `1`, then
validates the durable forced-negative proof. Actual `0`, `2`, any other exit,
missing/unparseable proof, or any proof mismatch is typed infrastructure exit
`2`; the generic non-forced child contract remains unchanged. Gate B now
explicitly covers both actual-exit-0-without-proof and actual-exit-1-without-
proof, while the exact-proof actual-1 path remains green.

**Delta files changed:** `scripts/verify-browser.ps1`,
`scripts/verify-gate-b.ps1`, and this report. No Java, electrical/PCB,
PID4/HTTP.sys implementation, or unrelated dirty change was modified.

**Validation:**

- PowerShell parser for both verifier scripts: exit `0`.
- `verify-browser.ps1 -GateBContractProbe`: exact proof, uncertainty,
  legacy-proof, and cleanup canaries passed; specifically actual-exit-0 and
  actual-exit-1 without proof both reported infrastructure exit `2`. Overall
  actual script exit was `2` at the existing `Win32_Process` ownership query
  (`Access denied`).
- PID4/HTTP.sys kernel transport probe: exit `0`.
- Live listener-consumer probe: exit `0`.
- Required `-Task43PForcedNegative` and `-Task43P -Seeds 0,2,3`: actual
  captured script exit `2`. No expected DOM marker or anchored Java diagnostic
  was emitted; preview PID identity failed before Java and cleanup retained
  uncertainty (`Access denied`).
- `git diff --check`: exit `0`; only existing LF/CRLF warnings.

No visible `@Browser` evidence or Task43P acceptance is claimed. The candidate
remains dirty and uncommitted at baseline HEAD
`20f83535163070a0688fcc0958715e6bc827d445`.

## Task 43P post-crash verifier recovery — 2026-09-04

**Status:** IN PROGRESS — NOT ACCEPTED. Owner Review remains blocked; Task 44
remains blocked and unstarted. Current branch is `codex/task43p-final-recovery`
at HEAD `a5b253873c2b25a54d7c393b118e3f2e1831a4d8`, with the inherited
uncommitted implementation preserved. The owner subsequently authorized the
project agent-configuration and workflow-guidance changes recorded below.

Fresh independent Luna review found that two 500 ms listener-proof stopwatches
were constructed without starting. The repair starts one monotonic budget at
entry and checks it before and after every relevant dependency, including the
initial listener query, schema validation, and direct HTTP.sys authorization.
Delayed valid observations cannot become a positive proof after the deadline.

The complete Gate B run then exposed a test-isolation defect: the kernel
release canary retained mutable `FunctionInfo` objects while replacing their
definitions. It restored a mock into later tests. The canary now snapshots
immutable command type and script-block values, asserts exact restoration, and
creates and completes a real context afterward in the same PowerShell process.

Validation against this intermediate reviewed candidate:

- `scripts/verify-gate-b.ps1 -SkipJdkCheck`: PASS, actual exit `0`, including
  focused kernel/listener tests and the complete contract, ownership, child,
  cleanup, and integration canaries. Log is retained in the task-owned temp
  directory `deadline-gate-final4-59ed0baaea104e16810bb5f9d7f77d0c`.
- Fresh independent Luna deadline/restoration review: PASS. Module SHA-256
  `11F1C0935A1A583F7A2A1949BC786A2279A4E99A6726C978EA3505BA5E52B256`;
  Gate B SHA-256
  `524C4109C33711A6E3110AF62B30A9F280860EAE5524902650416BAF402062D3`.
- Windows PowerShell 5.1 execution of `scripts/build.ps1 -JavaHome
  .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF`: PASS, exit `0`,
  JDK `1.8.0_502`, all five GWT permutations compiled and linked.
- `git diff --check`: PASS, exit `0`.

The first fresh Edge/CDP prerequisite used a new profile and dedicated port
after a current process/listener inventory. Run
`5a128ada6ef8490eafe64c0cf6008cc8` returned infrastructure exit `2` before CDP
attachment: the listener proof exceeded its strict 500 ms budget, and browser
descendant cleanup could not prove complete current identities. A separate
read-only listener query took 641 ms using `Get-NetTCPConnection`.

The retained run manifest, profile, claim, and diagnostic records were
preserved. Separate manual cleanup revalidated the exact root PID/start,
parent, executable, command line, run marker, profile, and port immediately
before stopping it. A subsequent inventory found zero Edge processes and no
listener on that run's port. This is manual resource cleanup, **not** a passed
verifier cleanup gate. The original prerequisite remains exit `2`.

Independent read-only diagnosis measured the provider no-match query at
429.433 ms and `netstat -ano -p tcp` at 83.737 ms (exit `0`). The proposed
repair selects the existing validated netstat path only inside the strict
ownership deadline. A separate cleanup-path review found no proven defect:
one descendant vanished between the complete snapshot and its direct query,
and the other lacked recorded executable proof. Neither condition authorizes
discarding an incomplete cleanup graph or relaxing identity checks.

The scoped `-PreferNetstat` implementation now passes its focused provider and
identity/schema canary and the complete Windows PowerShell 5.1 Gate B run
(`-SkipJdkCheck`, actual exit `0`). Default provider behavior, malformed/empty/
error-output rejection, strict deadlines, and all cleanup contracts are
preserved. This candidate's module SHA-256 is
`97032BAC7B68113A1EC627AC863F9C93F8F1B5DB74329C967C5F6E276909F603` and Gate B
SHA-256 is `41657E966070C38A28D9A22A91390B400F38AB3D9FBC751C4225DE25120C4DE0`.
The final log is retained under
`deadline-gate-netstat-final2-5974e197518e43f4ab43887ff94203c8`.
Independent delta review returned BLOCKED after a real preferred-route query
exposed a short-lived subprocess race that the mocked provider canary did not
exercise. `Invoke-VerifierBoundedProcess` starts asynchronous output readers
before capturing start identity; `netstat.exe` can exit before the existing
live-process identity reader accepts it. The failure is typed infrastructure,
and its logs remain in
`verify-process-7117c6989bf440b4af5b632f368c06b5`. The reviewer proved its
temporary listener and subprocesses absent afterward. Focused consumer/kernel
tests and static call-path review passed, but that candidate's real preferred
query was unproven.

The launch repair captures exact positive creation time from the retained
`System.Diagnostics.Process` launch handle before starting asynchronous output
readers. This launch-only accessor also permits a naturally exited child with
that retained handle; the shared live-process identity helper still rejects
already-exited processes. Numeric exit, complete output, exact termination,
and owned log cleanup remain required. The real netstat listener route and
quick-child checks now pass in Windows PowerShell 5.1, and independent review
found no blocker in this implementation delta. Module SHA-256 is
`34A3EE21711968F6D94AB4EB06B4CAAAB10D6D0B31FBC4884DCA7B1433019E26`.

Root integration review and independent adjudication identified a TEST/TOOL
blocker in the new canary: its timeout branch accepts any infrastructure
error, and listener teardown suppresses exceptions. Its reported timeout-stop
and fixture-cleanup PASS is therefore invalid. The real listener and quick
child evidence remains valid; a focused oracle correction and independent
delta review must precede the final complete Gate B and live browser retry.

Those oracle defects are now corrected. The canary requires the complete
canonical timeout diagnostic, records the retained `Process` PID/start tuple
passed to the real stop helper, proves current child absence, and emits PASS
only after listener cleanup. An appended cleanup-uncertainty diagnostic is
rejected. A subsequent intermittent null-expression failure led to a bounded
real-path reproduction: a deliberately timed-out child may produce zero bytes,
and `Get-Content -Raw` followed by `.Trim()` dereferenced null. The fixture now
uses a silent child, reads both retained streams with `File.ReadAllText`, and
gets identity from the retained process tuple. The quick natural-exit child
separately requires exact complete marker output. The original failed child's
identity was not captured, so its exact attribution remains unproven; the
reachable null-dereference defect was reproduced and corrected.

Fresh independent final delta review: PASS. Windows PowerShell 5.1
`scripts/verify-gate-b.ps1 -SkipJdkCheck -GateBProcessStartIdentityProbe` ran
once with actual exit `0`. Gate B SHA-256 is
`CB88B8C1B25924CEBC5ED4AABDD19151FF2D8905EC3E48833E2FFB21D9618A6B`;
the module remains at the SHA above. The final complete Windows PowerShell 5.1
`scripts/verify-gate-b.ps1 -SkipJdkCheck` returned actual exit `0`, including
the hanging-child, A/B isolation, and renderer checks. Root evidence log:
`gate-b-final-1061c05bb43c4c92b32b2608880f02ba.log` in the task-owned recovery
temp directory. Source hashes were reconfirmed after the run.

The next current process inventory found zero Edge processes and zero verifier
run-marked processes. A new Edge profile/run
`ab72e5c649a64be188b87b6965dd3e6f` then returned prerequisite exit `2`, again
before CDP attachment. Its complete port-bind proof exceeded 500 ms, and
descendant PID `180428` lacked the required executable identity during cleanup.
That descendant was already absent when inspected afterward; the retained
records do not establish whether its earlier executable was missing or wrong.

Read-only Windows PowerShell 5.1 measurements on the owned live browser found
warm preferred listener queries at 71-89 ms, current root records at 32-40 ms,
and a warm complete browser ownership snapshot at 210 ms. Cold component calls
were substantially slower. These are component timings, not a successful
integrated ownership proof. Static inspection shows that the port-bind path
takes a complete graph snapshot even when the listener is the exact root
PID/start and the existing identity helper supports a fresh PID-scoped query.
A bounded optimization of that case is implemented. Descendant ownership,
preview/HTTP.sys authorization, cleanup graph traversal, and the 500 ms proof
contract remain required.

After diagnosis, exact manual cleanup revalidated the second run's root
PID `45404`, start identity, parent, executable, complete command line, and
run/route/worktree/profile/port switches immediately before stopping it.
Subsequent current queries proved zero Edge processes and no listener on port
`50037`. All retained profiles, claims, manifests, and logs were preserved.
This manual action does not convert the prerequisite or verifier cleanup to
PASS.

The same-root candidate recorded in the current checkpoint passed a fresh
independent Luna review after another host crash. Windows PowerShell 5.1
focused probes passed for the new browser-root path, descendant snapshot
refresh, listener authorization retry, browser root and descendant identity
retry, kernel transport, and listener-record consumers. Parser checks and
`git diff --check` passed. The new canary is currently an explicit focused
probe; adding it to the default full driver is a nonblocking coverage follow-up.

On this candidate, fresh run `04e83ae623a74c91bd4a22eaa39a26c6` passed the
strict listener ownership proof and opened its CDP WebSocket. Immediate
cleanup failed because relevant snapshot PID `7164` disappeared before its
direct inspection; the complete prerequisite remained exit `2`. A later
independent current query proved that PID absent, and a later complete snapshot
succeeded. These observations do not retroactively prove the failed cleanup.
Separate manual cleanup immediately revalidated root PID `10580`, creation
time, parent, executable, full command line, and all ownership switches before
stopping it; subsequent queries proved zero Edge processes and no listener on
port `64049`. Retained profile, claim, manifest, and logs remain untouched.
The next small prerequisite adds a declared startup-settling interval and an
actual CDP document-readiness response before the unchanged cleanup gate. It
uses a fresh run and does not retry or relabel the failed immediate-close run.

The separate settled-startup run `0faf32eeaefd4ad9a2b98f1b3dcab935` also
passed CDP and returned the expected ready `about:blank` document, but failed
cleanup at a descendant executable mismatch (exit `2`). Waiting alone did not
resolve cleanup. A subsequent diagnostic-only run
`9b7ddf9cfe9d41799b7b251d624b78f7` used a reviewed observer that delegated the
real executable check unchanged and recorded only rejected candidate data.
It captured `identity_helper.exe` from Edge's matching version directory,
with the exact run profile and Windows package-identity utility arguments.
The installed browser and helper both have version `152.0.4191.62` and valid
Microsoft signatures with the same signer. The actual rejection occurred
before the helper's start time was captured, so this trace identifies the
compatibility cause and is not a process-ownership proof or acceptance run.

The bounded repair recognizes only this exact versioned, signed Edge companion
in the descendant policy, with required profile and utility identity. Root
executable, PID/start, current parent/start, complete graph, pre-stop identity,
missing-process, and deadline contracts remain required. The two new failed
roots were manually cleaned only after full current target revalidation;
subsequent listener/process queries proved no live residue. All run evidence
remains retained. No additional live attempt or full matrix is appropriate
until the repair passes focused validation and fresh independent review.

The companion implementation (`DDD668CB...`) and corrected tests (`6E3CF349...`)
passed fresh independent integrated Luna review. Its final explicit Windows
PowerShell 5.1 `-GateBEdgeDescendantCompatibilityProbe` returned actual exit `0`.
The test now proves the untouched copied pair is admitted by the actual policy
before changing one helper byte, preserving version/original-filename metadata,
observing `HashMismatch`, and requiring policy rejection. This host-dependent
file capability probe remains explicit-only; the default Gate B suite has a
source guard against accidentally requiring installed Edge.

Fresh preflight found one retained late-markerless test root from a worker's
earlier parallel dispatch. That dispatch did not capture an actual exit code;
the retained manifest records cleanup failure, and a separate serial PASS log
does not prove this run. Root exactly revalidated and stopped PID `17708`,
proved process and port `51488` absence, and preserved all retained evidence.
The manual cleanup artifact is `manual-retained-late-markerless-cleanup.json`
in the task-owned recovery directory; it is not verifier acceptance.

With a fresh zero-Edge/helper/verifier-marker inventory, the reviewed candidate
ran prerequisite `cb6458bfd8cd4192804a9a804f9ff3b3`. It returned actual exit `2`
before CDP readiness: `Positive listener inspection lacked canonical live owner
authorization.` Cleanup then failed on disappearing descendant identities.
Result artifact: `edge-cdp-prerequisite-8ac7ff719eff46faaa38450f8f7ea066.json`.
Root's subsequent exact current target/manifest comparison and immediate stop
revalidation proved root PID `187236`, all Edge/helper processes, and port
`57745` absent. The retained manifest hash and artifacts were unchanged;
`manual-reviewed-companion-prerequisite-cleanup.json` records that manual action.
Code inspection found the downstream live listener-authorization setter still
collects a complete browser census even when its entire listener set is the
exact browser root. The same strict scoped eligibility is being applied there,
with fresh independent identity proof and unchanged whole-call deadline.
The frozen implementation now shares one narrow eligibility helper between
both authorization boundaries. Windows PowerShell 5.1
`scripts/verify-gate-b.ps1 -SkipJdkCheck -GateBBrowserRootListenerFastPathProbe`
returned actual exit `0`; the worker retained
`tsj-gateb-fast-22f5b6fbde084be795f7e44bf2167daf.log` in OS temp. The extended
canary invokes the downstream setter and checks successful exact identity,
identity rejection, retained full-snapshot fallbacks, and late-proof rejection.
Parser/source checks and whitespace checks passed. Fresh independent Luna delta
review passed this integrated candidate, with unchanged schema evidence reused.

Run `5abb1a03082946c3af2b5a0bf9f5fa82` then passed the actual startup listener
authorization, CDP attachment, and settled document-readiness response. It still
returned actual exit `2`: descendant PID `16456` disappeared during cleanup
identity inspection. Result: `edge-cdp-prerequisite-d9b51b23e85b437193ff5a9242e85419.json`.
Separate exact manual cleanup of root PID `216560` proved all Edge/helper
processes and port `52579` absent while preserving the retained manifest and
all failure artifacts. `manual-canonical-prerequisite-cleanup.json` records
that manual action; it does not qualify verifier-owned cleanup. The scratch-only
observer for descendant proof and exact stop calls passed independent review
at SHA-256 `C2E875F645C98DC9F38C7845771CE256A6866C1FD2AE48F99D5DB20284B3910C`,
including isolated delegation/restoration canaries. It delegates original
functions and always returns diagnostic exit `2`.

Diagnostic run `2b076bb659c54a9e9a04ea973331fda3` opened CDP and passed actual
document readiness, then returned actual exit `2` on cleanup. Its
`edge-cdp-cleanup-diagnostic-e21b2680057941b1802f68592a1e2c31.json` records
successful full discovery proof for renderer PID `64768` with start identity
`639241825758562248` (trace `22`). After eleven other exact child terminations,
the same recorded child disappeared at the immediate pre-stop identity query
(trace `74`, fixed-point drain call path). This proves where the failure occurs;
it does not itself prove the child's natural exit. A bounded retained-handle
proof is being designed for fully verified children only. Unknown disappearance,
PID reuse, incomplete identity, and inconsistent inspection remain infrastructure
failures. Separate current exact manual cleanup proved root PID `78576`, all
Edge/helper processes, and port `62517` absent, with the retained manifest
unchanged. `manual-cleanup-lifecycle-diagnostic.json` records this action;
no files were deleted and it is not verifier-owned acceptance cleanup.

The owner also authorized removing the obsolete coder/reviewer presets and
aligning the project configuration during this session. The old architect
preset is replaced by a bounded read-only `specialist` preset, and project
defaults plus that preset request `gpt-5.6-luna` with `max` reasoning. Existing
concurrency settings remain unchanged. Both TOML files parse and match these
settings; this does not claim that an already-running session hot-reloaded
them. Repository guidance now requires consistent presets, an authoritative
checkpoint at the top of this report, and a small real OS-path check early in
validation when relevant. Architecture guidance records the strict proof
budget's purpose and host-qualification limits; the 500 ms contract is unchanged.

Forced-negative, normal A-I/physical-triad runtime, and visible built-in Browser
evidence have not started in this recovery session. The next step is the
cleanup lifecycle diagnosis and any bounded reviewed correction, then the real
prerequisite and final Gate B if it passes. Deadline, ownership, and
exact-cleanup contracts remain unchanged.
No commit, push, or completion email has occurred.
