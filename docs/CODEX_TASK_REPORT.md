# TroubleshootJS — Current Task Report

## Current checkpoint — Task 43P committed-candidate verification — 2026-09-05

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
