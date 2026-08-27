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

Pre-status-document checks: before the later status-document updates, `git -c safe.directory=C:/Users/david/Desktop/TroubleshootJS status --short` showed only ` M AGENTS.md` and this report; `git diff --check` and report-format/word-count checks exited 0. Production Java/scripts, preserved reports, and `AGENTS.md` were unchanged at that checkpoint; `AGENTS.md` SHA-256 was `3A1C101F7C013FF3D0C46981CDBDD094E4CC38845C4B07C9674F5BD392362E69`. The later final candidate scope is `AGENTS.md` plus `docs/research/AUDIT_STATUS.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/CODEX_TASK_REPORT.md`, and this report.
