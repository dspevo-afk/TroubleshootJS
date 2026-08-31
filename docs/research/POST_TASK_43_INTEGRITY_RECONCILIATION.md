# Post-Task 43 Integrity Reconciliation

Date: 2026-08-26. Tested baseline `C` is full `9853f1e5fd311830d14d671d0ba380c51018a658` on `codex/post43-mainline-consolidation`. Only the intentional `AGENTS.md` governance diff pre-existed. Historical reports remain untouched.

## Provenance and evidence boundary

Report keys and original baselines:

| Key | Preserved report path | Original baseline |
|---|---|---|
| S | `docs/research/STATE_LIFECYCLE_INTEGRITY_AUDIT.md` | `2ccc3b6fb3c6197b5dcadc12cdd63f6ab0ef6ca6` |
| V | `docs/research/VERIFICATION_INTEGRITY_FALSE_PASS_AUDIT.md` | `2ccc3b6fb3c6197b5dcadc12cdd63f6ab0ef6ca6` |
| A | `docs/task-evidence/task-43/recovery-assessment.md` | `c0eb342b29165b8218a4b97b16fb8554fee42aff` |
| VR | `docs/research/COMPONENT_VISUAL_REALISM_AUDIT.md` | `ef778563ea48f3a5b2db3bf6eca1ff69cafa4c31` |
| RR | `docs/research/PCB_ROUTING_SCALABILITY_AUDIT.md` | `ef778563ea48f3a5b2db3bf6eca1ff69cafa4c31` |
| PG | `docs/research/PROCEDURAL_GENERATION_SCALABILITY_AUDIT.md` | `ef778563ea48f3a5b2db3bf6eca1ff69cafa4c31` |
| TR | `docs/research/TROUBLESHOOTING_REALISM_SOLVABILITY_AUDIT.md` | `ef778563ea48f3a5b2db3bf6eca1ff69cafa4c31` |

Every row uses `C`; allowed dispositions are `CLOSED`, `OPEN BLOCKER`, `FOLLOW-UP`, or `STALE OR SUPERSEDED`.

Deterministic ledger: `D1` = `powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF`, exit 0; JDK8 `1.8.0_502`. `D2` = `scripts/verify-renderer-boundary.ps1`, exit 0 (`PASS:renderer-provider-boundary`). `D3` = `scripts/verify-gate-b.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07`, exit 0; minimal relevance check. `D4` = disposable `src`/`scripts` byte comparison and cleanup, exit 0. `D5` = `scripts/stop-preview.ps1 -Port 8897`, exit 2 (orphan parent); identity-checked PID 3224 fallback/port check, exit 0; 8899/PID 14912 preserved.

External CDP: `scripts/verify-browser.ps1 -BaseUrl http://127.0.0.1:8897 -BrowserPath "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" -Task39 -TimeoutSeconds 90`, exit 2: attach deadline expired before WebSocket handshake. Retained manifest: `C:\Users\david\AppData\Local\Temp\TroubleshootJS\verify\20cd7fcd353094d844fb\9073437bbfe345368a19cd170d9c7710\manifest.json`. Task40 retry with `-TimeoutSeconds 20` stalled with manifest `C:\Users\david\AppData\Local\Temp\TroubleshootJS\verify\20cd7fcd353094d844fb\e7454b9e9f2b4b2593917e4e61b4455a\manifest.json` showing started/cleanup pending; wrapper was interrupted (exit 1), classified exit 2 infrastructure uncertainty.

Visible in-app `@Browser` tabs at `B=http://127.0.0.1:8897/` observed: `B/circuitjs.html?tsjFixture=npn&seed=0&tsjVerifyTask39=true&running=true` → `PASS:task39`; `B/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyTask43=true&running=true` → `PASS:task43`; `B/circuitjs.html?tsjChallenge=led&seed=0&tsjVerifyMeter=true` → `PASS:meter`; `B/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyStress=true` → `PASS:stress`; Quick Play `B/circuitjs.html?tsjQuickPlay=true` loaded, HIGH/LOW were exercised, and Finish was disabled before repair. No persistent screenshot/tab/run artifact was retained. Visible, not CDP/CLI. Fresh Task40/41/QuickPlay-query/RC/stored-energy attempts timed out at `Page.getFrameTree` (each exit 2; not a pass).

## Auditable finding matrix

| Historical finding / experiment | Reproduction on `C` | Evidence | Disposition |
|---|---|---|---|
| S:R1 READY may precede analysis settlement | `CirSim.java:4480-4527`; interaction gate at `4832-4834` omits pending flag; I exit 2 | Source only; no runtime artifact | FOLLOW-UP |
| S:R2 snapshot is not a complete owner transaction | `Task41SimulationSnapshot.java:325,644` includes resistance current; fresh-candidate proof only; B/F not runtime-complete | M5 omission stale; no owner digest artifact | FOLLOW-UP |
| S:R3 replacement/mutation lacks multi-owner rollback | Sequential writes remain; no forced exception transaction | No artifact; source-only | FOLLOW-UP |
| S:R4 active-measurement cleanup may fail on exception | Meter/stress positives do not inject cleanup failure | No retained artifact; D source-only for failure lane | FOLLOW-UP |
| S:R5 RC status/retest is re-entrant/stateful | RC query ended in `Page.getFrameTree` timeout, exit 2 | No runtime artifact | FOLLOW-UP |
| S:R6 original fault versus secondary damage | Visible seed-3 stress passed one resistor-owner case; `getFailureState()`/`PhysicalFailureState` compress causal kinds; no independent public/snapshot/diagnostic/scoring causality proof | Temporary visible tab; no retained artifact | FOLLOW-UP |
| S:R7 reset is not a fresh session | Source distinguishes reset/install owners; H exit 2 | No route-order artifact | FOLLOW-UP |
| S:R8 runtime validation is not a complete mutation invariant | `D2` only covers renderer boundary; no full owner mutation proof | `D2`, no independent runtime artifact | FOLLOW-UP |
| S:R9 renderer/instrument state is not fully snapshotted | Option-A fresh-owner evidence does not prove same-owner restoration | Source/history only | FOLLOW-UP |
| S:R10 pending requests lack request/board/session identity | One pending verification flag remains; I exit 2 | Source at `CirSim.java:4480-4527`; no callback artifact | FOLLOW-UP |
| V:R-01/M1 renderer-pad/copper/solver triad false pass | Renderer-only J1.1 `+20px` disposable mutation; `D2` exit 0; live mutation not run | No retained visible mutation artifact | FOLLOW-UP |
| V:R-02/M4 public Remove versus direct dispatch | Remove-disabled source mutation canary exit 0; normal-player mutation run unavailable | No retained artifact; direct dispatch remains | FOLLOW-UP |
| V:R-03/M5 mutable snapshot omission | Restore-to-`NaN` disposable canary exit 0; current field is captured/restored | Source and canary; exact omission superseded | STALE OR SUPERSEDED |
| V:M2 retained meter stimulus | Historical meter mutation caught; current forced mutation not rerun | No current mutation artifact | FOLLOW-UP |
| V:M3 omitted component replacement binding | Historical Task40 caught it; current mutation not rerun | Task40 manifest is started/cleanup-pending only | FOLLOW-UP |
| V:R-04 shared metadata/oracle independence | No independently authored terminal oracle was added | No artifact; prohibited production design change | FOLLOW-UP |
| V:R-05 diode-short route admission | No current `-DiodeShort -Seeds @(0)` closure run | Explicitly unexecuted; no artifact | FOLLOW-UP |
| V:R-06 QuickPlay RC/fixed-CDP repeatability | RC and verifier queries hit exit-2 CDP timeouts; Task40 cleanup pending | Two exact manifests above | FOLLOW-UP |
| V:R-07 finite seed/fault corpus | Only visible seeds 0/3 plus deterministic checks; no broad sampling | Temporary tabs only | FOLLOW-UP |
| V:R-08 visible coverage below helper coverage | Four visible PASS routes, but no retained screenshot/tab/run artifact or full-family coverage | Exact URLs above | FOLLOW-UP |
| V:R-09 privacy/accessibility channels | No answer token observed in visible LED ticket; accessibility/privacy inspection unexecuted | No artifact | FOLLOW-UP |
| A:recovery-assessment §Architecture audit findings #1 compaction | Source finding remains; no complete envelope canary | No artifact; source-only | FOLLOW-UP |
| A:#2 board containment | No body/lead/probe/selection/drag outline matrix run | Unexecuted; no artifact | FOLLOW-UP |
| A:#3 hard-coded pad consumers | Fixed overlap/silkscreen boxes remain a source concern | Source-only | FOLLOW-UP |
| A:#4 package-local nominal-dimension rule | No package-envelope matrix run | Unexecuted; no artifact | FOLLOW-UP |
| A:#5 lifted-lead transition | No connected/disconnected probe transition run | Unexecuted; no artifact | FOLLOW-UP |
| A:#6 geometry identity omissions | No digest/fingerprint mutation proof | Source-only | FOLLOW-UP |
| A:#7 global package-ID variant ownership | No custom-package variant run | Unexecuted; no artifact | FOLLOW-UP |
| A:#8 loose probe radius parallel rule | No loose-tray geometry canary | Unexecuted; no artifact | FOLLOW-UP |
| A:#9 verifier structural gaps | `D2`/D3 do not cover compaction, renderer agreement, hits, leads, variants, or routes | `D2`, D3; insufficient for closure | FOLLOW-UP |
| A:#10 installed package render coverage | Four visible routes do not cover the declared package matrix | No retained artifact | FOLLOW-UP |
| A:#11 layout identity/version | No version/replay decision was tested | Unexecuted; no artifact | FOLLOW-UP |
| A:#12 verifier shell exit trust | Task43 evidence does not repair historical status-propagation concern | No new shell-failure artifact | FOLLOW-UP |
| VR:visual envelope/pixel-fidelity independence | Boundary canary passed; no independent pixel/visual-envelope oracle | `D2`; no screenshot artifact retained | FOLLOW-UP |
| RR:one-sided sequential routing and density limit | No dense multi-board stress benchmark | Unexecuted; no artifact | FOLLOW-UP |
| PG:composed-module generation contract | No composed multi-subsystem generation run | Unexecuted; no artifact | FOLLOW-UP |
| TR:diagnostic distinguishability/hidden input/target leakage | Solver-backed routes passed narrowly; no separating-sequence proof | Visible tabs only; no artifact | FOLLOW-UP |
| Current Gate B process/profile isolation | `D3` exit 0; D5 primary `scripts/stop-preview.ps1 -Port 8897` exit 2 because orphan parent identity was unproven; separate exact command-line/PID-verified fallback and post-stop port check exit 0; no owned residual remained | CLOSED only for isolation/cleanup after verified fallback; unrelated listener preserved | CLOSED |

## Required falsification cases A–I

| Case | Lane actually executed | Result and limit |
|---|---|---|
| A original-fault round trip | Source/deterministic-only | No same-owner runtime sequence; not a PASS. |
| B replacement A/B identity swap | Attempted, Browser/CDP exit 2 | No pending-probe or wrong-owner runtime evidence. |
| C secondary-damage persistence/isolation | Visible `@Browser` stress route, narrow PASS | One physical-owner scenario; causal projection remains FOLLOW-UP. |
| D active meter/power race and cleanup | Visible meter PASS plus source checks; race attempt exit 2 | No injected exception or cleanup proof. |
| E stored-energy/mutation | Attempted Browser query, exit 2 | `Page.getFrameTree` timeout; not a PASS. |
| F probes through mutation | Source/deterministic-only plus narrow visible routes | No complete mutation/probe sequence. |
| G residual/pending completion | Quick Play manual pre-repair observation | Finish was disabled; no residual/pending race. |
| H fresh-session/route-order contamination | Attempted, exit 2 | No completed order matrix. |
| I paused/resume, rapid action, wrong-board callback | Attempted, exit 2 | No runtime epoch canary; source shows the gap. |

Reconciliation is complete for current evidence; missing runtime lanes/physical triad block Owner Review. No proven current `OPEN BLOCKER` or correction milestone is declared. Task 43P is not claimed accepted/unlocked, and Task 44 was not started.

## Temporary mutation and preservation proof

`git worktree add --detach .worktrees/task43p-integrity-falsification HEAD` failed exit 1 because `.git/worktrees` was not writable. A disposable copy was used and removed. Four source-only mutations were applied with `apply_patch`, canary-checked, and restored: `PcbWorkbenchRenderer.getPadPoint` J1.1 `+20px` (`D2` exit 0); disabled public Remove while direct dispatch remained (canary exit 0); omitted `lastResistanceTestCurrent` restore (canary exit 0); and omitted `NpnSlotController.replaceSingleElement` (canary exit 0). Final all-`src`/`scripts` byte identity was `D4` exit 0; no mutation remains.

Pre-status-document checks: before the later status-document updates, `git -c safe.directory=C:/Users/david/Desktop/TroubleshootJS status --short` showed only ` M AGENTS.md` and this report; `git diff --check` and report-format/word-count checks exited 0. Production Java/scripts, preserved reports, and `AGENTS.md` were unchanged at that historical checkpoint; `AGENTS.md` SHA-256 was `3A1C101F7C013FF3D0C46981CDBDD094E4CC38845C4B07C9674F5BD392362E69`. The status-document candidate at that checkpoint was `AGENTS.md` plus `docs/research/AUDIT_STATUS.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/CODEX_TASK_REPORT.md`, and this report; the current Task 43P Coder candidate is documented separately below.

## Task 43P Coder candidate — developer-only cross-boundary route

Date: 2026-08-28. This uncommitted candidate starts at and still has HEAD
`3bfaab093f85247fc20aec068824c83dc3d214c8` on
`codex/post43-mainline-consolidation`. No commit, push, branch/worktree
mutation, Task 44 work, normal gameplay behavior, or CircuitJS physics change
was made.

### Implementation boundary

- `Task43PDeveloperVerifier` runs from the existing generated-board
  verification settlement hook only when `tsjVerifyTask43P=true`. It records
  the existing challenge/lifecycle state, a bounded owner/part/lead/binding
  digest, early `Finish Job` rejection, cleanup fields, and explicit A-I
  statuses. It does not add a transaction owner, request epoch, board epoch,
  session epoch, alternate solver, or fake reading.
- `Task43PPhysicalTruthDeveloperVerifier` has a separately authored manifest
  for LED, diode, RC, NPN, NMOS, and parallel boards. It joins board pad/net
  identity, raw trace endpoints, installed physical part/slot identity,
  rendered terminal/hit surface, and live CircuitJS element/post readings.
  Renderer-only offset, raw endpoint displacement/gap, wrong net, solver post
  swap, mirror/transform mismatch, and internally self-consistent wrong
  mapping are disposable in-memory negative fixtures. Production mutation is
  reported as `none` and fixture residue as `in_memory_only`.
- `scripts/verify-browser.ps1` adds `-Task43P` and
  `-Task43PForcedNegative`. Reached positive routes capture
  `data-tsj-task43p-evidence` as run-owned JSON and compare a direct
  `src`/`scripts` worktree digest before/after capture. The forced-negative
  route preserves the anchored application/canary exit-1 contract; browser,
  CDP, timeout, ownership, cleanup, and unproven conditions remain exit 2.

### A-I falsification matrix in the candidate route

The route emits every lane explicitly. `PARTIAL` means a real bounded check
ran but the complete sequence is not claimed; `UNPROVEN` means the current
owner/epoch boundary or required runtime sequence cannot be proven by this
route. These are not positive acceptance results.

| Lane | Candidate check | Honest status |
| --- | --- | --- |
| A | Early Finish is rejected before repair; same-owner isolation/repair and wrong-repair round trip are not invoked by the read-only route. | `PARTIAL` |
| B | Current stable slot/part/terminal identities participate in the digest and triad; replacement A/B invalidation and stale-probe checks are not invoked. | `PARTIAL` |
| C | Fault locus and private physical runtime ownership are recorded; solver-derived secondary-damage causality is not inferred. | `PARTIAL` |
| D | Entry digest and active-overlay/pending-power fields are checked; rapid meter/power/mode actions and exception cleanup are not synthesized. | `PARTIAL` |
| E | Temporal owner presence is recorded for RC-capable candidates; charge/off/residual/decay/readiness mutation is not claimed by a non-temporal route. | `PARTIAL` or `UNPROVEN` |
| F | Installed board/component identity, render target, and live solver/net join are checked; lift/reconnect/remove/reinstall/loose transitions are not invoked. | `PARTIAL` |
| G | Early Finish cannot latch; paused/pending settlement followed by correct repair, retest, and terminal Finish is not claimed. | `PARTIAL` |
| H | No fresh-session or route-order permutation is performed; Task 41 remains its narrower fresh-candidate/detached-owner proof. | `UNPROVEN` |
| I | The route records that request/board/session epochs are absent and does not manufacture a stale callback result. | `UNPROVEN` |

This matrix deliberately does not promote the existing single pending flag or
Task 41 snapshot into a stronger same-owner transaction claim. A complete
runtime A-I closure therefore remains pending.

### Validation and runtime boundary

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
  -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF`:
  exit `0`; JDK `1.8.0_502`, five GWT permutations, link succeeded.
- PowerShell parser for `scripts/verify-browser.ps1`: exit `0`.
- `git diff --check`: exit `0` (Git only reported the repository's normal
  LF/CRLF warning for touched files).
- The attempted `-Task43P -Seeds 0` (caller-owned port `8768`),
  `-Task43PForcedNegative` (port `8769`), `-Task43` (port `8770`), and
  `-Task43Integrated` (port `8771`) routes each returned exit `2`. Edge/browser
  startup and exact cleanup ownership failed with WMI `Access denied`; the
  caller-owned preview handshake itself was not enough to prove the browser
  process claim. These are exit-2 infrastructure outcomes, not application
  passes or current product defects. No runtime Task43P JSON artifact is
  claimed because the DOM evidence attribute was never reached.
- The retained infrastructure manifests for those attempts are
  `C:\Users\david\AppData\Local\Temp\TroubleshootJS\verify\20cd7fcd353094d844fb\f32917bf63874747af44672a344c84e5\manifest.json`
  (Task43P),
  `...\53bc9b915a2e49248fa5d608fd92e999\manifest.json` (forced negative),
  `...\1ccf41fb5fc0407e875e2f23c9513577\manifest.json` (Task43), and
  `...\90351553cb5741c3aa3702fad6bb38bd\manifest.json` (integrated). They
  record `artifacts: []` and the exact cleanup/ownership uncertainty; they
  are supplemental infrastructure evidence, not player-facing proof.
- After the Task43P attempts, direct `-Task39`, `-Task40`, `-Task41`,
  `-QuickPlay`, `-Rc -Seeds 3`, `-StoredEnergy`, `-StressDamage`, and
  `-StressDamageNormalPlayer` attempts were made against caller-owned port
  `8773`/`8774`. Every route stopped before page execution at the same WMI
  `Get-CimInstance Win32_Process` `Access denied` ownership/cleanup boundary;
  no route is counted as a pass. The multi-seed stored-energy and RC attempt
  also retained typed cleanup uncertainty. These are regression attempts, not
  replacement evidence for the Foreman's complete closed set.
- Built-in visible `@Browser` validation was not performed by the Coder; it is
  reserved for the Foreman as required by the project boundary.

Current Task 43P disposition: implementation and evidence plumbing are
present, but acceptance remains pending. No final-SHA product `OPEN BLOCKER`
was reproduced; the missing runtime evidence is an infrastructure limitation
and the known absent epoch contract remains an explicit unproven result, not a
repair performed inside 43P. Owner Review remains incomplete and Task 44 was
not started.

## Task 43P verifier false-pass repair — Coder handoff 2026-08-29

The focused repair starts from published baseline
`20f83535163070a0688fcc0958715e6bc827d445`. The prior in-memory DTO-copy
canaries are retired as runtime proof. `Task43PPhysicalTruthDeveloperVerifier`
now gathers independent raw logical-board, raw PCB-layout/copper, renderer,
physical-package, and solver-post observations into disposable source
snapshots. Positive validation and every negative fixture call one
set-based-completeness-first canonical validator. The negative set includes
renderer-only `J1.1 +20px`, raw-copper endpoint gap, raw-net mismatch,
solver element/post mismatch, mirrored-package transform mismatch, an
internally self-consistent wrong mapping, and omitted-terminal.

The NPN manifest's known `J1.1` exception is corrected to the generator's
`WireElm` post `0`. The solver oracle no longer calls
`BoardSimulationBindings.getNetIdForEndpoint()`; expected net membership comes
from the independent manifest and raw logical-board snapshot, while actual
`CircuitPostMeasurementEndpoint` element identity/class/post and finite live
readings remain checked. The independent package catalog accepts the current
connector realization pair `DEFAULT_MIRRORED_X`/`MIRROR_X` exactly.

Java/GWT evidence emits no repository baseline, HEAD, dirty-state, source
digest, file-count, or evidence-path claims. `scripts/verify-browser.ps1`
owns those claims, rejects forbidden Java fields, compares HEAD/status/source
SHA-256/file count before and after capture, and binds forced-negative
diagnostics to the run-owned browser route and repair baseline. The route
continues to report `UNPROVEN:task43p` and exit `2` until A-I and visible
built-in `@Browser` evidence are complete.

The new
`scripts/verify-task43p-source-experiments.ps1` harness applies the three
source/extraction-path mutations only in disposable copies: renderer
projection, raw `PcbTraceGeometry` endpoint data at the
`GeneratedBoardInstance` producer seam, and solver endpoint lookup at the
`BoardSimulationBindings` producer seam. It records exact before/after/restore
SHA-256 values, attempts the five-permutation compile, runs a bounded compiled
extraction attempt, and verifies the committed-tree HEAD/status/source
digest/count are unchanged. The latest separate renderer, raw-copper, and
solver records each returned exit `2`/`UNPROVEN`: compile exit `0`, runtime
attempted, preview stopped, exact source restoration proven, and repository
state unchanged. Runtime mutation acceptance remains unproven because this
host cannot construct `System.Net.HttpListener`; none of these source
experiments is visible-browser acceptance proof.

The final wrapper-owned repository audit after the repair checks retained the
published baseline HEAD, source/verifier digest
`77d722d6ae2787ba7ed80bd3f28c9728f7bb0f2ec741ee18d4579c94fb01f3bf`, and file
count `855`; the candidate's pre-existing dirty status and expected paths were
unchanged.

## Task 43P delta remediation loop — 2026-08-30

The same candidate received one focused repair loop for the delta-review
blockers. `scripts/verify-task43p-source-experiments.ps1` now requires each
disposable preview's repository/script/web identity to match and passes that
execution-root triplet to `scripts/verify-browser.ps1`; the repository-root
wrapper remains the owner of actual checkout HEAD/status/source digest and
file-count claims. The three producer mutations remain isolated and are
restored by exact bytes and SHA-256 before disposable cleanup.

`Task43PPhysicalTruthDeveloperVerifier` now rejects any observed raw logical
net-ID set that differs from the independent manifest net set, including an
empty extra net. Its solver negative selects an incompatible endpoint by
class/post/node rather than vector position, with a deterministic invalid
source-node fallback. The wrapper's Java payload boundary is an exact nested
schema allowlist that rejects null/array/malformed payloads and unknown or
aliased repository-authority properties before persistence. Java state
fingerprints use verifier-design names rather than repository digest names.

The latest combined renderer, raw-copper, and solver source-experiment record
is `task43p-source-experiments-be4175476c5f465cbbad9747853a7daa.json`. Each
entry compiled all five OBF permutations with exit `0`, attempted runtime
extraction against the still-mutated disposable source (`runtimeAgainstMutatedSource=true`),
and returned exit `2`/`UNPROVEN` because this host cannot construct
`System.Net.HttpListener` before preview identity completion. Each proves
exact source restoration, preview stop, and unchanged repository state; none
proves runtime mutation rejection or visible `@Browser` acceptance. No
gameplay, electrical, generator, or `CirSim.java` source change was made in
this loop.

## Final solver identity delta — 2026-08-30

The Task 43P solver validator now joins each live
BoardSimulationBindings.getEndpoint(padId) result to the immutable board
endpoint retained by the exact GeneratedComponentConnectionBinding for
detachable pads or by the matching fixed-generated physical-part terminal for
fixed pads. It requires exact element identity and post equality, direct
CircuitJS node and finite-reading checks, and the actual post point touching
the owned detachable connection element when a detachable binding exists. A
missing/malformed binding or fixed part is a verifier failure. The negative
source snapshot deterministically prefers the same-node/different-element
collision before using other independent class/post/node mismatches.

The refreshed combined source record is
task43p-source-experiments-1ee260b7a3c94b609830e475f3dc4491.json. Its three
producer-path mutations compiled with exit 0, attempted runtime extraction
against their matching disposable roots, and returned exit 2/UNPROVEN because
this host cannot construct System.Net.HttpListener. Exact source bytes, HEAD,
status, source/verifier digest, and file count were restored and matched. No
runtime rejection or visible @Browser proof is claimed.

## Task 43P final remediation corpus and NPN audit — 2026-08-30

The NPN manifest was re-audited against the generator and now records all
fifteen terminals without the prior contradiction: `J1.1` is
`LOAD_SUPPLY / WireElm / 0`, `LED1.K` is `COLLECTOR / WireElm / 0`, and the
remaining NPN entries match the current board, package, and solver bindings.
Across the six families, the independent manifest contains 64 terminals in
total (`6 + 8 + 12 + 15 + 13 + 10`). No generator or CircuitJS source was
changed.

The disposable source harness now records nine distinct real producer-path
experiments: renderer pad offset, renderer lead offset, raw-copper endpoint
gap, raw logical-net mismatch, fixed solver identity redirect, detachable
solver identity redirect, package/mirror transform mismatch, internally
self-consistent logical-pad remapping, and omitted manifest terminal. Each
case uses its own disposable root, compiles the mutated source through all
five OBF permutations, and records the exact mutation bytes/SHA-256 before,
after, and after restoration.

The combined record is
`C:\\Users\\david\\AppData\\Local\\Temp\\TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-027ac10835a24e31a91625b9bbcfee85.json`.
All nine compile exits are `0`; all nine runtime exits are `2`/`UNPROVEN`
because the host cannot construct `System.Net.HttpListener` before preview
identity and wrapper extraction. All nine records show
`runtimeAgainstMutatedSource=true`, exact source restoration, unchanged
repository HEAD/status/source digest/file count, stopped preview proof, and
zero cleanup errors. No compile-only case is acceptance evidence.

The repository wrapper's HEAD/status/file enumeration, hashing, and count
computation are now enclosed in a typed infrastructure boundary, with
standard file/process/transport/timeout errors also fail-closed when surfaced
through route catches. Strict nested Java schema validation rejects unknown
or aliased repository-authority fields before evidence persistence. Visible
`@Browser`, WMI ownership, and runtime mutation rejection remain unproven and
do not authorize acceptance or publication.

## Task 43P fixed-part solver endpoint boundary — 2026-08-30

The solver boundary now has a valid retained endpoint for both ownership
models. Detachable pads use the board endpoint from the exact
`GeneratedComponentConnectionBinding`; fixed pads use the endpoint retained by
the matching installed `FIXED_GENERATED` physical-part terminal (foundation
parts are constructed through `PhysicalFoundationPartFactory`, while the
other fixed-generated part classes retain the same terminal endpoint boundary).
The fixed path requires `FIXED_GENERATED` provenance, mounted slot/component
identity, and exact manifest/package/terminal identity, and rejects mutable
replacements or missing parts. Live
simulation endpoints are compared with the retained endpoint by exact
CircuitElm identity and post index. Independent manifest/raw-board class,
post, net, node, ownership, finite-reading, and distinct-net checks remain
active; detachable geometry is checked only where a detachable binding exists.

The solver source experiment preserves the first J1.1 `BoardSimulationBindings`
read used to construct the fixed terminal and redirects a later live read to a
different endpoint. Thus its retained expected endpoint cannot be copied from
the mutated live lookup. The experiment remains exit `2`/UNPROVEN on this host
because the disposable preview cannot construct `System.Net.HttpListener`; it
compiled, restored exact bytes, and proved repository state unchanged, but did
not claim runtime rejection or visible `@Browser` acceptance.

The refreshed record is
`C:\\Users\\david\\AppData\\Local\\Temp\\TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-a1e8de2ba420464086e03d52a6b70147.json`.
The solver mutation preserves the first J1.1 read used by fixed-part
construction before redirecting a later live binding read, so the retained
endpoint remains independent of the mutation. The runtime result is still
exit `2`/UNPROVEN; no browser/solver mutation rejection is claimed.

## Latest Task 43P endpoint-oracle correction — 2026-08-30

The prior universal fixed-terminal expectation was removed. At generated-board
composition, `GeneratedBoardInstance` now retains an immutable
`GeneratedBoardEndpointOracle` copied directly from the authoritative board
binding map, before the developer-verification-ready live lookup seam. The
solver validator compares every live pad endpoint to that oracle by exact
`CircuitElm` object and post. Detachable pads additionally cross-check the
exact `GeneratedComponentConnectionBinding` board endpoint and its detachable
connection geometry. Non-detachable pads use the oracle and only use installed
`FIXED_GENERATED` parts for package/terminal/fixed-boundary checks; foundation
`FixedPhysicalPart` terminals are cross-checked where they semantically are the
board endpoint. This keeps internal NPN/NMOS LED/resistor component terminals
from being mistaken for intermediate board WireElm/GroundElm endpoints.

The real detachable solver case is `C1.+` in RC, which has the actual
detachable binding; fixed J1.1 remains separate. The nine-case source record is
`C:\\Users\\david\\AppData\\Local\\Temp\\TroubleshootJS\\verify\\task43p-source-experiments\\task43p-source-experiments-a702ea7b3abe44c4853d146daccb360e.json`.
All nine OBF compiles exit `0`; all nine runtime attempts exit `2`/UNPROVEN
because the host cannot construct `System.Net.HttpListener` before preview
identity. Exact source restoration, unchanged baseline HEAD/status/source
digest/file count, stopped preview, and zero cleanup errors were recorded. No
runtime mutation rejection or visible `@Browser` proof is claimed.
