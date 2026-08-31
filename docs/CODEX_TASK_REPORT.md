# POST-TASK-43 GATE B — Verification Isolation and Mainline Protection Baseline

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
  `C:\Users\david\AppData\Local\Temp\TroubleshootJS\gate-b-real-edge-22245742a5dc401a8f53599e245cea53`
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
- Three targeted `scripts/verify-task43p-source-experiments.ps1 -ExperimentId ... -JavaHome .tools/jdk8-download/jdk8u502-b07 -ProcessTimeoutSeconds 900` runs — each actual script exit `2`; final evidence paths are `C:\Users\david\AppData\Local\Temp\TroubleshootJS\verify\task43p-source-experiments\task43p-source-experiments-cd6119174f7f48819783b14c40f60526.json` (renderer), `...\task43p-source-experiments-9e4e4edfd0974ac89890c10558f8f81a.json` (raw copper), and `...\task43p-source-experiments-3d39d727ba5f4af2adf737066289e2ce.json` (solver). Each compiled all five GWT permutations with exit `0`, attempted the compiled extraction route, returned runtime exit `2` because this host cannot construct `System.Net.HttpListener`, stopped its disposable preview, restored exact source bytes, and proved unchanged HEAD/status/source digest/file count (`2b3b5419ad75f6ecad8aabcd4ec0647a80dfb40b74339302aead380343a21296`, `855`).
- `powershell -NoProfile -Command '& { & powershell -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\verify-browser.ps1 -Task43P -Seeds 0 -TimeoutSeconds 30; $code=$LASTEXITCODE; Write-Output ("SCRIPT_EXIT=" + $code); exit 0 }'` — actual verifier exit `2`; final retained manifest `C:\Users\david\AppData\Local\Temp\TroubleshootJS\verify\20cd7fcd353094d844fb\05d57eaac00c47989a59dbdefb2dbfe6\manifest.json`.
- The equivalent `-Task43PForcedNegative -Seeds 0`, `-Task43 -Seeds 0`, and `-Task43Integrated -Seeds 0` commands — actual exits `2`; final forced-negative infrastructure evidence is retained at `C:\Users\david\AppData\Local\Temp\TroubleshootJS\verify\20cd7fcd353094d844fb\2a0c90d122194f4db714730bcb572f92\manifest.json`; the current regression loop also retained `8f3aed61108a49dfb33ff7a566f56ee6` for Task43 and `9cc9cddf54f44b64a2a0610dfdafe097` for the integrated parent. No DOM/runtime Task43P JSON was reached.
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
  `C:\\Users\\david\\AppData\\Local\\Temp\\TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-be4175476c5f465cbbad9747853a7daa.json`:
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
  C:\Users\david\AppData\Local\Temp\TroubleshootJS\verify\task43p-source-experiments\task43p-source-experiments-1ee260b7a3c94b609830e475f3dc4491.json:
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
`C:\\Users\\david\\AppData\\Local\\Temp\\TroubleshootJS\\verify\\20cd7fcd353094d844fb\\44068b4a89be4e2f91c13efeee006e98\\manifest.json`;
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
`C:\\Users\\david\\AppData\\Local\\Temp\\TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-a1e8de2ba420464086e03d52a6b70147.json`.
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

`C:\\Users\\david\\AppData\\Local\\Temp\\TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-027ac10835a24e31a91625b9bbcfee85.json`

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
`C:\\Users\\david\\AppData\\Local\\Temp\\TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-a702ea7b3abe44c4853d146daccb360e.json`.
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
