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
