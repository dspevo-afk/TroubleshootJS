# Verification Integrity / False-Pass Audit

Audit date: 2026-08-19
Repository: `TroubleshootJS`
Audit branch: `codex/verification-integrity-audit`
Audit worktree: `C:\Users\david\Desktop\verification-integrity-audit`
Authoritative prompt ref: `origin/codex/prompt-dropbox` at `236c7e6f37a9af9c5011ed73b1fcf5fcbb730f4e`
Accepted audit baseline: `origin/master` at `2ccc3b6fb3c6197b5dcadc12cdd63f6ab0ef6ca6` (`Add Task 42 Codex inbox prompt`)

This is a read-only integrity audit. Temporary source mutations were used only as negative experiments, restored immediately, and are not part of this report change.

## 1. Executive Summary

[Observed] The baseline has substantial real electrical verification. Family validators read live CircuitJS values; the challenge controller validates faulted and repaired behavior; the instrument system performs solver-backed measurements; Task 41 proves 13 declared normal routes, including power transitions, isolation, temporal waits, repair, and customer retest. The final clean-baseline checks passed Task 41, a challenge route, and the architecture seams.

[Observed] Existing verification is not yet an independent integrity boundary across the physical PCB, renderer, solver, normal-player UI, and session lifecycle. Several checks validate declarations and shared bindings rather than independently deriving the relationship they claim to protect.

[Experiment] A renderer-only `J1.1` pad translation of `+20` pixels made the visible pad no longer meet the raw copper trace. The resistance route, challenge route, and layout verifier still passed. A visible browser screenshot showed the defect. This is a direct false pass in the physical-board truth chain.

[Experiment] Disabling the visible `Remove component` action left Task 41 passing because Task 41 dispatches the operation directly through the workbench controller. The actual LED normal-player route failed when it attempted the disabled button. This is a direct false pass in normal-player authenticity.

[Experiment] Omitting restoration of the stored resistance-test current from `Task41SimulationSnapshot` left Task 41 passing. The snapshot canary did not compare that field, and the final proof did not expose the leak. This demonstrates that the current isolation proof can miss mutable diagnostic state.

[Experiment] Two mutations were caught: retaining a temporary resistance stimulus caused the meter lifecycle verifier to fail, and omitting the component-binding replacement caused Task 40 live repair validation to fail. The stack therefore has useful local canaries, but their coverage does not compose into a complete independent proof.

[Inference] Final verdict: **not ready to claim false-pass resistance across the full TroubleshootJS trust boundary**. The most important blocker is the absence of an independently checked physical copper/pad/solver correspondence. Normal-player and state-isolation proofs are also incomplete. This does not mean the current solver-backed family behavior is fake; it means a wrong renderer, wrong UI path, or leaked mutable state can remain hidden from one or more accepted verifiers.

## 2. Baseline and Method

[Observed] Latest remote refs were fetched before the audit. The prompt branch was read in full from the remote ref named by the user. The audit was performed in a separate worktree created from the accepted baseline, not from `codex/prompt-dropbox` and not from the later `origin/master` tip.

[Observed] While the worktree was being created, `origin/master` advanced to `c0eb342b29165b8218a4b97b16fb8554fee42aff` (`Add LED diagnostic fault diversity proof`). That newer tip was intentionally not incorporated. The audit branch remained at `2ccc3b6`. The main worktree and the other active audit worktrees were not switched, reset, cleaned, stashed, or edited.

[Observed] The build was run with the repository’s JDK 8 toolchain:

```text
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -JavaHome 'C:\Users\david\Desktop\TroubleshootJS\.tools\jdk8-download\jdk8u502-b07' -Target Compile -Style OBF
```

It passed after every temporary mutation was restored. The audit preview used port `8898` and was stopped after the final checks; no other preview or concurrent worktree was stopped.

[Observed] The following baseline verification results were recorded:

| Area | Result |
|---|---|
| LED default browser matrix, seeds 0/2/3 | 15 routes passed |
| Diode normal, seeds 0/2/3 | 3 routes passed |
| Parallel, seeds 0/2/3 | 3 routes passed |
| RC stored-energy, seeds 0/2/3 | 3 routes passed |
| Layout, player seed 3 | Passed |
| Architecture seams | Passed |
| NPN forced, seeds 0/1/2/3 | 16 routes passed |
| NPN natural, seeds 0/1/2 | Passed |
| NMOS forced, seeds 0/1/2/3 | 12 routes passed |
| NMOS natural, seeds 0/1/2 | Passed |
| Task 39 | Passed |
| Task 40 | Passed |
| Task 41, final clean run | Passed |
| Diode developer short route | Failed admission candidate-count assertion |
| QuickPlay selector/session | Passed on clean single-seed retry |
| QuickPlay RC finish | Repeated operation-canceled failure |

[Observed] The normal LED route was also exercised through the visible in-app Browser. Real visible clicks activated DC voltage mode, placed the probes, selected a component, powered the board down, removed and replaced the resistor through the rendered controls, powered up, and pressed `Retest Customer`. The rendered result was `Repair verified. Indicator operating normally.` followed by `Customer retest passed. The reported behavior is resolved.` This is strong evidence for that route only; it is not evidence that all verifier routes use the same interaction path.

The labels in this report mean:

- `[Observed]` — directly seen in source, command output, or visible browser behavior.
- `[Experiment]` — result of a bounded temporary mutation or controlled negative test.
- `[Inference]` — conclusion drawn from the observed architecture or experiment.
- `[Recommendation]` — proposed permanent canary or process change; not implemented by this audit.

## 3. Current Verification Architecture

[Observed] The effective chain is:

```text
generated family/netlist
        ↓
GeneratedBoardVerifier + family validator
        ↓
fault binding/effect + serviceability admission
        ↓
diagnostic solvability admission
        ↓
challenge lifecycle / workbench operations
        ↓
CircuitJS instruments and live behavior
        ↓
repair status + customer retest
```

[Observed] `GeneratedChallengeController` validates healthy behavior, applies the fault, validates the faulty behavior, and admits the scenario. At READY it requires the fault to remain applied and the board to be restored to the expected powered/restored state. `finishJob` requires live repair status `CORRECTLY_RESTORED` and a passed customer retest (`src/com/lushprojects/circuitjs1/client/GeneratedChallengeController.java:34-79,117-197`).

[Observed] `GeneratedBoardVerifier` checks ownership, active generated elements, board-pad endpoint presence, and voltage consistency for declared board nets. `BoardSimulationBindings` supplies the endpoint lookup. `GeneratedComponentConnectionBindings` checks that declared component endpoints are connected to CircuitJS connection elements (`GeneratedBoardVerifier.java:8-77`, `BoardSimulationBindings.java:15-69`, `GeneratedComponentConnectionBindings.java:15-25,80-161`).

[Observed] These checks are valuable solver and metadata invariants, but the binding graph is declared by the same generated instance that supplies the board and solver objects. The verifier does not independently derive a solver endpoint from raw PCB copper geometry or independently establish that the rendered pad terminal is the same physical terminal represented by the solver post.

[Observed] `PcbLayoutDeveloperVerifier` validates raw trace endpoints, nets, Manhattan geometry, clearance, courtyards, labels, and deterministic fingerprints. `PcbWorkbenchRenderer` separately draws traces from `PcbTraceGeometry`, computes pad points, and performs hit testing (`PcbBoardLayout.java:46-171`, `PcbWorkbenchRenderer.java:56-77,164-204,237-255`). This separation is architecturally appropriate, but there is no independent end-to-end assertion joining all three representations.

[Observed] `GeneratedDiagnosticSolvabilityAdmission` confirms that declared diagnostic target IDs have rendered pads and that renderer hit testing returns the declared pad (`GeneratedDiagnosticSolvabilityAdmission.java:24-80`). That proves declared-target/rendered-hit identity through the renderer’s own coordinate path. It does not prove rendered copper-to-pad contact or copper-to-solver continuity.

## 4. Oracle Independence Matrix

| Claim under test | Current oracle | Independence assessment | Catches | Cannot catch |
|---|---|---|---|---|
| Generated board is structurally coherent | `GeneratedBoardVerifier` and binding metadata | Low to medium; it consumes the generated board’s own pads, nets, and endpoint bindings | Missing active elements, duplicate/absent bindings, declared-net voltage disagreement | A wrong renderer coordinate, wrong physical trace endpoint, or a binding map that is consistently wrong |
| Family works when healthy/faulted/repaired | Family validators reading live CircuitJS currents, voltages, drops, timing, and illumination | Medium for electrical behavior; low for independent topology identity because validators use the generated instance and its bindings | Real solver behavior outside expected ranges, bad repair behavior, KCL violations where explicitly checked | A shared bad topology/binding plus a shared expected contract; physical-board misrendering |
| Fault has a legal serviceable locus | Fault binding, physical runtime, serviceability admission, and replacement catalog | Medium; it exercises live replacement and solver behavior but trusts the generated fault locus and catalog metadata | Broken replacement binding, wrong physical owner, unavailable repair action | A fault declared on the wrong real physical terminal if metadata and runtime agree |
| Diagnostic plan is physically probeable | Admission checks `renderer.hasPad` and renderer hit testing | Low for physical truth; target IDs, pad coordinates, and hit testing share production paths | Missing rendered pad, target collision, inaccessible declared pad | Copper/pad gap, trace-to-solver mismatch, raster error, or a user-facing click path that is disabled |
| Task 41 solver signature | Direct developer instrument calls, live CircuitJS samples, direct board probe targets | Medium for solver values; low for player authenticity and independent endpoint identity | Non-finite samples, missing transitions, failed isolation, failed retest, candidate count changes | A disabled UI, wrong user click path, shared endpoint mapping, or a physical copper defect not used by the direct target |
| Candidate separation/equivalence | Solver samples and repair semantics from the production candidate routes | Medium-low; the candidate class is generated from the same family, plan, and binding abstractions | Candidates with indistinguishable signatures, semantic negative fixture failures | A family-wide shared oracle error or a route that is not accessible through normal UI |
| PCB layout geometry | Raw layout verifier | Medium for raw geometry; low for rendered physical truth | Invalid raw route geometry, overlap, clearance, net route omissions | Renderer-only drift, canvas/raster defect, pad/copper visual mismatch, solver mapping |
| Physical package/render canaries | Provider geometry, renderer geometry, hit boxes, and provider terminals | Low to medium; multiple checks share the same provider geometry | Missing provider, basic envelope and hit-box regressions | A shared geometry error, raw copper mismatch, pixel-level defect, or independent terminal identity error |
| Instrument lifecycle | Meter lifecycle verifier and overlay cleanup assertions | Medium-high for the exercised cleanup path | Leaked temporary resistor/overlay state; the mutation below was caught | Unexercised fields, deep physical runtime state, or normal-player interaction authenticity |
| Normal-player repair | Selected visible route and DOM/canvas controls | High only where a real visible route is actually used; lower for headless helper routes | Disabled or broken visible controls in the exercised route | Unexercised family UI paths, cross-session leakage, and physical truth not rendered by the route |

[Inference] The architecture has several live electrical oracles, but it does not yet have an independent oracle at the physical-to-solver boundary. The strongest current tests are therefore local proofs, not a proof that all representations describe the same board.

## 5. Normal-Player Path Authenticity

[Observed] The visible Browser LED repair described in Section 2 used real rendered interaction and produced a successful retest. The visible meter interaction also showed a real polarity-sensitive reading (`-12 V`) after placing the probes, and clicking the active meter mode exited instrument mode. The renderer showed the selected component and the modification controls.

[Observed] Much of `scripts/verify-browser.ps1` is a CDP helper that starts hidden headless Edge processes on fixed debugging ports and dispatches browser/CDP mouse and DOM actions (`scripts/verify-browser.ps1:201-214` and the route-specific launch blocks). Those tests are valid automation checks, but they are not equivalent to a visible in-app Browser session. The report distinguishes the visible LED evidence from the helper’s headless evidence.

[Observed] Task 41 is explicitly a developer proof. It detaches the workbench before evaluating candidates, enumerates `normalRoutes()`, invokes `GeneratedBoardOperationIds.CONTROL_INPUT_HIGH/LOW`, creates `BoardPadProbeTarget` objects directly, calls developer instrument seams, obtains the hidden fault owner, chooses the correct catalog ID in code, and dispatches workbench operations directly (`Task41DeveloperVerifier.java:18-140,175-278,442-675,985-1018`). These are useful internal capability checks, but they do not reproduce a normal player’s sequence of canvas selection, visible button activation, catalog choice, meter-mode selection, and customer retest.

[Experiment] A temporary mutation changed `PcbWorkbenchController.addRemoveAction` so the visible Remove action was always disabled. `-Task41` still passed. The actual LED normal-player verifier failed when it attempted the rendered `Remove component` button and reported that the button remained disabled. All source was restored and rebuilt.

[Inference] Task 41 should not be used alone as evidence that the normal-player diagnostic workflow is operable. The accepted internal route can prove that a legal controller operation exists while missing a disabled or unreachable public control.

[Observed] Public diagnostic-plan validation does reject reserved developer/private/solver/fault/answer/hint/candidate-style identifiers (`GeneratedDiagnosticSolvabilityAdmission.java:145-153`), and visible challenge text remained a vague complaint in the exercised route. No answer leakage was observed in the visible LED session. This is a source-token/privacy check, not a complete rendered DOM/accessibility or cross-route secrecy proof.

## 6. PCB / Physical / Solver Truth Chain

[Observed] The raw layout model records pad IDs, net IDs, trace endpoints, and geometry. The simulation binding model maps declared pad IDs to solver measurement endpoints. The renderer draws raw traces but calculates pad positions and probe hit targets through its own renderer path. Physical-package providers supply component-lead geometry and are also used by renderer hit testing and provider canaries.

[Observed] The historical Task 38 record documents an earlier false pass: a visible review found that the PCB control/gate connectivity did not match the solver graph even though the automated proof passed. The correction rerouted the external control path and removed the stale gate/test-point route (`docs/ROADMAP.md:871-909`). This is direct project history showing that a declared route can pass without proving the physical truth chain.

[Experiment] The following temporary renderer mutation was applied only in the audit worktree:

```text
PcbWorkbenchRenderer.getPadPoint("J1.1") returned the normal pad point with x + 20 pixels.
```

The raw copper trace remained at its original endpoint. After rebuilding:

```text
-Route resistance -Seeds @(0)       PASS
-Route challenge  -Seeds @(0)       PASS
-Layout -PlayerSeed 3               PASS
```

The visible Browser screenshot showed the `J1.1` pad translated away from the end of its copper trace. Because the renderer’s drawing, hit testing, and diagnostic admission used the shifted renderer point while the layout verifier used raw geometry, no existing route failed.

[Inference] This is a P0 false-pass risk. A physical board can be visibly wrong while the raw-layout verifier, solver signature, and target admission all remain green. The current stack has no independent check that a rendered pad center, raw trace endpoint, and solver-bound terminal form one connected physical/electrical fact.

[Observed] The NMOS layout and solver mapping currently agree on the accepted G/S/D post order and the external control path in the tested routes (`NmosLowSideSwitchGenerator.java:63-80,153-176,295-325`; `NmosLowSideSwitchPcbLayoutFactory.java:69-100`). That is a positive route-specific result, not a substitute for an independent all-family correspondence oracle.

## 7. Fault and Repair Verification

[Observed] LED, diode, parallel, NPN, and NMOS validators use live CircuitJS currents/voltages and, where relevant, real control operations. NPN and NMOS validators drive high/low controls and restore the prior state. Repair validators require live healthy behavior, installed replacement state, and non-faulted slot state. The RC family uses a temporal behavior contract and solver-backed temporal observations.

[Observed] The challenge lifecycle keeps the original fault applied until the repair operation, requires a restored board state before completion, and calls a live customer retest. The repair path is therefore materially stronger than a click-only “original component selected” check.

[Experiment] Removing the component-binding replacement call from `ResistorSlotController.installNewFromCatalog` caused:

```text
-Task40
FAIL task40 physical locus/serviceability admission - FAIL:Task 40 powered solver did not validate repair: RESISTOR_OPEN
```

The mutation was restored. This confirms that the Task 40 repair proof can catch a real disconnect between the physical replacement and the solver’s active binding.

[Observed] The diode developer-short route failed before the final clean checks because the developer route’s generator path constructed the route with `developerOnlyFaultRoute=false`; Task 41 then observed a changed live-admission candidate count. The normal diode route passed. This is a verifier/orchestration defect in a developer-only route, not evidence that the normal player route is unsound, but it means the complete route matrix is not green.

[Inference] Fault and repair behavior is solver-backed but not fully independent. The fault locus, fault effect, physical owner, replacement catalog, and validators are all connected through the generated instance. A consistent metadata error can evade an oracle that is built from the same metadata.

[Observed] Coverage is a fixed corpus: Task 41 declares 13 normal routes across LED, diode, parallel, RC, NPN, and NMOS. Developer-only routes include diode short and NPN load-path open. The tested seed ranges are useful but finite and do not establish arbitrary-seed or arbitrary-compatible-fault coverage.

## 8. Measurement Verification

[Observed] Meter modes are backed by the CircuitJS measurement adapter. Resistance/continuity routes power the board down, diode routes use simulated diode measurements, voltage routes use powered behavior, and RC routes wait through the existing temporal profile before taking stored-energy measurements. The visible Browser also demonstrated a polarity-sensitive DC reading and mode exit.

[Experiment] A temporary mutation omitted removal of the internal reference resistor from `ResistanceMeasurementStimulus.remove`. The meter route failed with:

```text
-Route meter -Seeds @(0)
FAIL seed=0 meter - FAIL:Resistance overlay did not restore after lifting R1.2
```

The source was restored. This is a good lifecycle canary: the measured overlay and temporary electrical graph were not allowed to survive.

[Observed] Task 41’s measurements are solver-derived, including finite encoding of CircuitJS open-circuit resistance and real power/temporal transitions. However, `boardProbe` constructs a `BoardPadProbeTarget` from a declared pad ID and renderer, rather than proving that a player’s actual click on visible copper would select the same terminal (`Task41DeveloperVerifier.java:588-609` in the audited baseline).

[Inference] Instrument cleanup has meaningful coverage, but measurement truth still inherits the shared pad/binding path. A meter can be numerically correct for the endpoint it was handed while the visible board exposes a different or disconnected physical point.

## 9. State Isolation / Order Dependence

[Observed] `Task41SimulationSnapshot` captures and restores CircuitJS lists, graph/runtime references, power and instrument state, selected state, workbench attachment, and several owner references. It runs eight injected restore-failure stages and asserts owner, graph, list, workbench, power, overlay, live simulation, instrument, and selection conditions (`Task41SimulationSnapshot.java:344-465,525-744`). This is a substantial isolation mechanism.

[Observed] The snapshot primarily stores references to mutable `GeneratedBoardFamilyState`, `GeneratedTemporalBehavior`, `GeneratedFaultBinding`, `PhysicalBoardRuntime`, and `PhysicalPart` objects. `assertRestored` checks the boolean `modificationsFullyRestored` but does not deep-compare every physical mount, damage, replacement, temporal, scenario, retest, catalog, or evidence field. It also cannot prove browser-global state or DOM state outside the captured simulator object graph.

[Experiment] A temporary mutation omitted restoration of `sim.lastResistanceTestCurrent`. Task 41 still passed. The omitted field was not compared by the snapshot assertion and was not exposed by the final route’s acceptance checks. The mutation was restored and the clean Task 41 run passed.

[Inference] State isolation is strong for the fields it explicitly captures, but the experiment demonstrates an actual gap in the claimed restore boundary. A leaked measurement or physical-runtime field could affect a later route without failing the current snapshot canary. No exhaustive route permutation or fresh-session digest was found that would prove order independence across all mutable state.

[Observed] A first Task 41 run and the first QuickPlay run also encountered WebSocket-closed behavior; clean retries separated the stable product results from harness timing. The helper uses fixed debugging ports for route families. This makes concurrent or stale-browser state a verifier reliability risk even where it is not a product-state leak.

## 10. Mutation-Test Results

All mutations below were applied temporarily in the dedicated audit worktree, tested, restored from the accepted baseline, and followed by a successful compile. No mutation remains in the branch.

| ID | Temporary mutation | Test and result | Caught? | Integrity conclusion |
|---|---|---|---|---|
| M1 | Translate renderer point for `J1.1` by `+20` px; leave raw copper unchanged | `-Route resistance -Seeds @(0)`: pass; `-Route challenge -Seeds @(0)`: pass; `-Layout -PlayerSeed 3`: pass; visible screenshot showed pad/copper separation | No | Direct physical false pass; no independent renderer/copper/solver oracle |
| M2 | Keep the internal resistance reference element in the meter graph during removal | `-Route meter -Seeds @(0)`: failed with `Resistance overlay did not restore after lifting R1.2` | Yes | Meter lifecycle cleanup canary is effective for this path |
| M3 | Omit `replaceSingleElement` during catalog installation | `-Task40`: failed with `powered solver did not validate repair: RESISTOR_OPEN` | Yes | Live repair binding is exercised and meaningful |
| M4 | Force visible `Remove component` action disabled | `-Task41`: passed; `-LedNormalPlayer`: failed on disabled Remove button | Task 41: no; normal-player route: yes | Internal proof is not normal-player proof |
| M5 | Omit restoration of `lastResistanceTestCurrent` | `-Task41`: passed | No | Snapshot assertion does not cover all mutable diagnostic state |

[Inference] The mutation set is intentionally small and targeted at the audit questions. Its distribution matters: local cleanup and repair mutations fail loudly, while physical rendering, public UI reachability, and one state field can pass. That pattern is consistent with a stack of strong local contracts but incomplete cross-boundary independence.

## 11. Browser / Visual Acceptance Assessment

[Observed] Visible in-app Browser evidence exists for the LED challenge’s meter, selection, removal, replacement, power, and customer-retest workflow. The final visible state showed the repaired resistor and illuminated indicator. This confirms that one end-to-end player path is genuinely interactive.

[Observed] The visible Browser screenshot of M1 also provided the clearest evidence of the current physical false pass: the pad was visibly shifted from the raw copper endpoint even though the CLI verifier routes passed.

[Observed] The normal helper matrix is primarily hidden headless CDP automation. It is appropriate for repeatable browser diagnostics, but it cannot by itself satisfy the project’s normal-player acceptance requirement that visible clicks and rendered state changes be observed. The report therefore does not treat headless Task 41 dispatches as equivalent to visible player validation.

[Observed] Attempting to inspect the QuickPlay route through the visible Browser did not stabilize within the available browser-tool timeout; the CLI QuickPlay selector passed on a clean retry but the RC finish route repeatedly canceled. This is recorded as a harness/route limitation rather than silently classified as a product failure.

[Inference] Visual acceptance currently proves selected workflows, not the physical truth of every rendered route. There is no pixel- or geometry-independent canary that compares visible copper/pad placement with the solver mapping, and there is no visible-player matrix covering the full 13-route Task 41 corpus.

## 12. False-Pass Risk Register

| ID | Severity | Risk | Evidence | Current disposition |
|---|---|---|---|---|
| R-01 | P0 | Renderer pad/trace geometry can disagree with raw PCB and solver endpoint while verifiers pass | M1; separate renderer and raw-layout paths; Task 38 historical false pass | Blocks physical truth acceptance and Task 43’s G3 gate |
| R-02 | P1 | Developer Task 41 can pass while public player controls are unavailable | M4; direct dispatch and hidden catalog/fault-owner use in Task 41 | Require visible normal-player canaries before treating diagnostic workflow as proven |
| R-03 | P1 | Snapshot restoration can miss mutable diagnostic, temporal, physical, or lifecycle state | M5; shallow/reference-based snapshot boundary | Require a complete state digest and order/fresh-session checks |
| R-04 | P1 | Validators and expected signatures share production bindings and contracts | Oracle matrix; `BoardSimulationBindings`, family validators, Task 41 plans | Add an independently authored netlist/terminal oracle |
| R-05 | P2 | Developer diode-short route is not admitted consistently | Baseline `-DiodeShort -Seeds @(0)` candidate-count failure | Fix verifier-route orchestration before claiming the developer matrix is green |
| R-06 | P2 | QuickPlay RC finish and fixed CDP ports create repeatability/concurrency risk | Repeated RC cancellation; initial WebSocket closures | Stabilize harness/session lifecycle and isolate port allocation |
| R-07 | P3 | Finite seed/fault corpus can miss generator-specific false passes | 13 fixed normal routes and bounded seed sets | Add deterministic multi-seed/fault sampling with independent canaries |
| R-08 | P3 | Visible Browser evidence covers fewer workflows than headless helper evidence | Visible LED proof; headless Task 41 matrix | Expand visible-player acceptance for representative family workflows |
| R-09 | P3 | Reserved-token privacy checks do not prove all rendered/accessibility channels are free of answers | Admission token filter; no visible leak observed in LED route | Add rendered DOM/accessibility/privacy inspection to the acceptance suite |

## 13. Permanent Canary Recommendations

[Recommendation] Add an independently authored physical-to-solver triad canary for every generated board: `(raw trace endpoint, rendered pad/lead point, solver terminal)`. The expected terminal identity must be authored outside `BoardSimulationBindings` and provider geometry. The canary should fail on M1, a raw trace-endpoint translation, a pad-net swap, and a component-terminal permutation.

[Recommendation] Add a visible normal-player canary for each functional family. It should use only visible Browser clicks, rendered selection, visible meter controls, visible catalog selection, power controls, and customer retest. It should reject direct controller dispatch, hidden fault-locus lookup, developer instrument methods, and hard-coded catalog IDs as evidence.

[Recommendation] Make a second oracle derive terminal/net identity from the logical netlist and physical layout, then compare that result with the runtime binding. Do not have the second oracle call the production binding map or reuse the same provider terminal table.

[Recommendation] Replace the snapshot’s boolean restoration assertion with a deterministic digest over every mutable board/session owner: component mount/removal/lift/replacement state, damage/stress, temporal state, fault state, catalog inventory, modification history, challenge status, retest result, meter overlays/readings, and selected/probe state. Assert equality before and after every candidate and route permutation.

[Recommendation] Add fresh-browser and route-order matrix tests. Run each route in a fresh session, then run all route permutations in one session, using allocated debug ports rather than fixed ports. A route must fail if a prior route’s measurement, physical, fault, or DOM state changes its result.

[Recommendation] Add a negative canary that disables or removes each public action while leaving the internal controller operation available. The accepted normal-player verifier must fail, proving that it observes the user-facing path rather than only the internal capability.

[Recommendation] Add independent fault-locus tests that compare the selected physical part, solver effect terminals, raw pad IDs, and visible repair catalog without obtaining any of them from the same fault binding object.

[Recommendation] Extend privacy canaries to rendered DOM text, accessibility names, developer/debug controls, URL/query state, and browser-visible metadata. Keep the existing reserved-token check as a useful early guard, not the complete privacy proof.

[Recommendation] Run deterministic multi-seed generation and compatible-fault sampling after the independent canaries exist. Diversity should increase coverage of the same integrity invariants, not replace them.

## 14. Roadmap Impact

[Observed] The roadmap defines G1 as player operability, G2 as diagnostic admission, G3 as physical envelope truth, and G4 as composition identity (`docs/ROADMAP.md:927-966`). It also requires CircuitJS to remain the electrical source of truth and every admitted fault to have a real solver effect, physical locus, legal diagnostics, repair, and retest.

[Inference] The M1 result means G3 is not currently demonstrated as an independent gate. The earlier Task 38 correction shows the project has already encountered this class of error; the current renderer drift experiment shows the broader gap remains possible.

[Inference] Task 42 is the immediate roadmap milestone at the accepted baseline, but diversity proof alone cannot close R-01 through R-04. Task 43 is explicitly blocked by Task 42 in the roadmap and is additionally blocked by this audit’s physical truth and normal-player integrity findings. Tasks 44-48 remain transitively blocked until their prerequisites and these cross-boundary canaries are satisfied.

[Recommendation] Do not edit the roadmap to conceal this result. Preserve the completed Task 39-41 history, add the integrity findings to the next acceptance criteria, and require the independent physical, visible-player, and state-digest canaries before declaring the blocked physical-package milestone ready.

[Observed] This audit did not inspect or incorporate the later Task 42 implementation tip after the accepted baseline was captured, and it did not modify the separate active Task 42 session.

## 15. Final Verdict

The audit question is whether the stack can fail loudly when each of the following is wrong:

| Wrong condition | Verdict | Reason |
|---|---|---|
| Physical board / rendered copper and pad geometry | **No, not reliably** | M1 produced a visible pad/copper disconnect while layout, challenge, and resistance routes passed |
| Live solver behavior | **Mostly, but not independently** | Family validators and repair tests use real CircuitJS values; shared bindings and contracts can make a consistently wrong mapping appear coherent |
| Diagnostic workflow | **Partially** | Task 41 catches many internal plan and repair failures, but M4 shows it can pass with a disabled public control |
| Repaired state and retest | **Partially** | Live repair binding and customer retest are meaningful; M3 was caught, but M5 shows the state boundary is incomplete |
| Exact failure location | **Not yet reliably** | Local failures are usually reported at a validator or admission seam, while physical/rendered and shallow-state false passes can survive |

[Inference] **Final verdict: FAIL for full verification-integrity acceptance; CONDITIONAL PASS for the narrower claim that many current family routes exercise real solver-backed behavior.** The stack is not ready to claim that a green verification run proves the physical board, live solver mapping, normal-player workflow, repaired state, and session isolation are all correct simultaneously.

[Recommendation] Treat R-01 as P0 and R-02/R-03/R-04 as P1 acceptance blockers. Keep the successful local canaries, but add independent cross-boundary canaries before Task 43 and the transitively dependent milestones are considered unblocked.
