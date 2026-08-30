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
