# State Lifecycle Integrity Audit

## 1. Executive Summary

[Experiment] The accepted baseline remains operational for the bounded workflows that currently have developer and browser verifiers. The pinned baseline builds successfully with the repository's JDK 8 workflow. Task 39, Task 40, Task 41, Quick Play, explicit RC and stored-energy paths, meter paths, normal-player repair paths, wrong-repair behavior, stress damage, architecture seams, and the natural NPN/NMOS seeds all passed the available checks. The stress route also demonstrated a real secondary failure caused by excessive simulated power and then verified that a fresh replacement did not inherit that damage.

[Observed] The current implementation is not a complete state-lifecycle transaction system. It is coherent for the ordinary, serialized paths that the current verifiers exercise, but it does not guarantee coherence for every legal sequence involving paused simulation, rapid actions, pending solver analysis, exceptions during physical mutation, active measurement cleanup, or same-owner snapshot/restore. The most important reason is that several independently mutable owners are coordinated by a single simulator-level pending-verification flag and by synchronous UI sequencing rather than by an explicit lifecycle epoch or transaction boundary.

[Inference] The answer to the audit question—whether ugly but legal sequences preserve physical identity, fault ownership, graph state, and player-visible state coherently—is **not yet for all sequences**. The normal bounded answer is yes; the composition answer is no without additional lifecycle contracts. No P0 failure was found. Two conditional P1 risks should be treated as entry criteria for stateful composition work:

- A board can remain externally `READY` while graph analysis or generated verification is pending. A paused or rapid retest can therefore observe solver-backed fields before the mutation has settled, especially for simple families whose retest reads current electrical values directly.
- Task 41's snapshot restores simulator collections and references, but not every mutable owner of physical identity, challenge state, family/temporal state, damage, private fault-effect state, instrument strategy state, or renderer state. Its current proof passes because it isolates candidate boards and restores a detached original owner; that is a successful isolation strategy, not a general transaction guarantee.

[Recommendation] Keep the existing Task 42 dependency and do not newly block Task 43 solely on this audit: Task 43 is primarily a physical package/interaction-envelope milestone and does not itself need to add new electrical state. Before Tasks 44–48 compose more mutable systems, require a bounded lifecycle correction or an explicit composition contract covering settlement, owner identity, rollback/snapshot completeness, and exception cleanup. Add the canaries in Section 15 before accepting those composition milestones.

## 2. Baseline and Method

[Observed] The audit was performed in a dedicated worktree and branch:

- Worktree: `C:\Users\david\Desktop\state-lifecycle-integrity-audit`
- Branch: `codex/state-lifecycle-integrity-audit`
- Accepted baseline at audit start: `2ccc3b6fb3c6197b5dcadc12cdd63f6ab0ef6ca6`
- Baseline source: the then-current `origin/master`
- Prompt source: `origin/codex/prompt-dropbox`, commit `236c7e6`, file `docs/prompts/STATE_LIFECYCLE_INTEGRITY_AUDIT.txt`

[Observed] Remote refs were fetched with pruning before the dedicated worktree was created. During the audit, the separate Task 42 session advanced `origin/master` to `c0eb342b29165b8218a4b97b16fb8554fee42aff`. That commit is intentionally excluded from this audit. The audit branch remains pinned to the accepted starting snapshot and is one commit behind the current remote `master`; the main worktree and all other worktrees were left untouched.

[Observed] The required architectural documents were read before analysis: `AGENTS.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, and `docs/CODEX_TASK_REPORT.md`. The authoritative audit prompt was read in full before beginning the audit.

[Observed] The audit used source tracing across the simulator, generated-board, challenge, physical-runtime, mutation, fault, stress, instrument, probe, and snapshot layers. Relevant implementation areas include:

- `src/com/lushprojects/circuitjs1/client/CirSim.java`
- `src/com/lushprojects/circuitjs1/client/GeneratedChallengeController.java`
- `src/com/lushprojects/circuitjs1/client/BoardModificationController.java`
- `src/com/lushprojects/circuitjs1/client/PhysicalBoardRuntime.java`
- `src/com/lushprojects/circuitjs1/client/Task41SimulationSnapshot.java`
- generated fault, physical-part, slot-controller, instrument, probe, and temporal-behavior classes under `src/com/lushprojects/circuitjs1/client/`

[Experiment] The following validation was run from the pinned baseline:

- JDK 8 build using `scripts/build.ps1`: GWT compile and link succeeded for all five permutations.
- Task 41 developer verifier: passed diagnostic solvability.
- Task 39: NPN, NMOS, RC, and normal-player retest paths passed.
- Task 40: physical-locus/serviceability admission passed.
- Quick Play: selector/session, RC finish, explicit precedence, fresh-reload privacy, and NPN/NMOS seed paths passed after the verifier's temporary CDP receive timeout was increased.
- Explicit RC, stored-energy, meter, parallel, normal-player LED, wrong-repair, stress-damage, architecture, RC normal-player, natural NPN, and natural NMOS paths passed.

[Observed] The first Quick Play run timed out at the verifier's 30-second CDP receive boundary while the synchronous RC proof was still running. The same route completed when only that temporary harness receive timeout was increased to 120 seconds. This was a harness timeout, not a product failure. The verifier script and tracked screenshot evidence were restored exactly after the run.

[Observed] No production source, test source, roadmap, architecture document, or task report was changed. The final audit branch is intended to contain only this report.

[Inference] The browser results are automated developer-verifier evidence, including scripted normal-player routes through the rendered application. They should not be read as evidence from a manually operated built-in `@Browser` session. That distinction does not affect the source-level lifecycle findings, but it limits the claim about visual/player observation in this audit.

## 3. Runtime State Ownership Map

[Observed] The runtime has a healthy separation of many responsibilities, but the separation is not matched by a single owner-level transaction protocol. The important state owners and their boundaries are:

| State domain | Primary owner | Important mutable state | Current synchronization boundary | Audit result |
|---|---|---|---|---|
| Electrical graph and solver | `CirSim` plus CircuitJS element objects | `elmList`, `conductors`, analysis flag, simulation time, element internals, solver-derived values | `needAnalyze()`, CircuitJS analysis, running timer | [Observed] Electrical behavior remains solver-backed in tested paths. |
| Generated board identity | `GeneratedBoardInstance` and `PhysicalBoardRuntime` | board instance, ordered simulation vector, slots, parts, inventory, IDs, capabilities, providers | board installation creates a new instance; runtime owns identity | [Observed] Identity is explicit and stable within an instance. |
| Physical connectivity | `BoardModificationController` | per-binding `connected` map, inserted/removed connection elements | each mutation changes graph and calls `finishMutation()` | [Observed] Structural state is checked, but mutation is not transactional. |
| Physical mounting | `PhysicalBoardSlot`, `PhysicalPartMountState` | installed part pointer, part-to-slot pointer, loose/installed projection | remove/install operations | [Observed] Normal remove/reinstall identity works; rollback is absent. |
| Part catalog/inventory | `PhysicalPartInventory` and runtime | all registered parts, installed versus loose membership, serial counters | acquire/register/install | [Observed] Replacement identity is preserved; snapshot does not restore it. |
| Generated original fault | `GeneratedFaultController`, `GeneratedFaultEffect`, generated bindings | fault application, private switch/shunt/value state, board-path enable state | challenge preparation and verification | [Observed] Fault infrastructure is retained privately and remains solver-backed. |
| Secondary damage | stress system, for example `ResistorStressDamageSystem` | accumulated damage, failure state, secondary-open path | solver observation while powered and installed | [Experiment] Deterministic damage and replacement isolation passed. |
| Board power | `BoardPowerController` and generated power bindings | requested power, actual external switches, pending overlay power | power transitions and temporary-measurement cleanup | [Observed] Power is separate from run/pause, but pending power is a single coalesced value. |
| Instrument mode | `InstrumentController` and concrete mode strategies | active mode, probes, refresh flags, temporary measurement overlay | mode entry/exit, active measurement, power changes | [Observed] Probe identity is strong; private strategy state is incompletely snapshotted. |
| Probe targets | probe-target objects and `InstrumentController` | board instance, physical part ID, endpoint/pad, element post | target validity checks on read/mutation | [Experiment] Normal remove/replacement/probe routes passed. |
| Workbench presentation | `PcbWorkbenchController` and renderer | selected component/part, tray page, feedback, enabled controls | board install and UI refresh | [Observed] New-board isolation is effective; same-owner snapshot coverage is incomplete. |
| Challenge lifecycle | `GeneratedChallengeController` | preparation state, scenario, cached retest, developer scope, lifecycle evidence | generated verification and explicit actions | [Observed] State transitions are serialized, but simulator interaction gating does not include every pending state. |
| Family/temporal behavior | family state and temporal behavior objects | commanded input, RC timing/residual/profile data, retest side effects | challenge operation and customer retest | [Observed] RC repair status is stateful and can perform a power cycle. |
| Session boundary | `QuickPlaySession` plus page reload/new board install | selected family, seed, board/runtime, probes and report state | fresh session/reload | [Experiment] Fresh reload and selector isolation passed. |
| Task 41 proof state | `Task41SimulationSnapshot` and static proof guards | graph/list references, selected elements, instrument developer state, workbench owner | capture/detach/restore/assert | [Observed] Graph/reference restoration is broad; owner-state restoration is not complete. |

[Inference] The dominant coordination gap is not that one subsystem is missing. It is that a physical mutation can change the graph, physical owner, bindings, inventory, fault path, stress registration, probes, challenge retest state, and UI eligibility in one logical action, while the simulator exposes only a boolean `generatedVerificationPending` and an `analyzeFlag` as the cross-system settlement signal.

## 4. Cross-System Invariants

[Observed] The following invariants hold in the tested bounded routes:

- A solver-backed measurement is used for voltage, resistance, diode, and active measurement paths; the UI does not substitute a hard-coded reading for the normal measurement result.
- A physical part has a stable identity within a generated board instance. Removing a part makes it loose; replacing it creates or installs a different physical identity rather than silently reusing the old one.
- A board-pad probe is distinct from a component-lead or loose-part probe. Replacement invalidates the old installed-part target while the board pad can remain a valid board target.
- The original generated fault is represented separately from a replacement's physical identity. A replacement does not inherit the original component's private fault backing in the tested routes.
- Stress damage is keyed by physical-part identity. A damaged original remains damaged through removal/reinstallation, while a fresh replacement starts with its own stress state.
- Physical mutation is rejected while the board is electrically powered or while an active measurement overlay is running.
- The generated verifier checks active generated elements, detachable connection elements, board pads, and structural connection counts after settled graph changes.
- A fresh Quick Play session creates a fresh board/runtime and does not carry the previous family, seed, probes, report, or physical part objects into the new session.

[Observed] The following cross-system invariants are weaker than the architecture requires:

- `READY` means the challenge controller is ready, but it does not mean that no graph analysis or generated verification is pending. `CirSim.isChallengeInteractionEnabled()` does not include `generatedVerificationPending`, `analyzeFlag`, or a solver-settlement token.
- A physical mutation updates the connection graph and often multiple physical owners before the asynchronous verification pass runs. There is no general commit/rollback object spanning those owners.
- `PhysicalBoardRuntime.validate()` checks important slot, package, mounted-part, and inventory relationships, but it is not a complete per-mutation assertion of every binding endpoint, connection-map entry, fault-path owner, stress registration, and graph occurrence.
- Task 41's snapshot restores list and graph references and a boolean fully-restored indication, but it does not restore all mutable physical, challenge, temporal, family, damage, fault-effect, strategy, and renderer state.
- Simple customer retest validators can read live element values without explicitly requiring that pending analysis and generated verification have settled. NPN/NMOS/RC operations perform more explicit simulation work, but that does not make the global interaction gate transactional.
- A temporary active measurement has a cleanup sequence, but cleanup is not enclosed by an outer `try/finally` that guarantees overlay, temporary stimulus, pending power, and UI state restoration if a solver or cleanup step throws.

[Recommendation] Treat “ready for player action” as a settled lifecycle state, not simply as `GeneratedChallengeController.state == READY`. The settlement contract should include graph analysis, pending verification, overlay closure, and an owner/epoch identity check.

## 5. Transition Inventory

[Observed] The following inventory describes the current transition behavior and its boundary conditions.

| Transition | Preconditions | State changes | Analysis/verification behavior | Rollback and risk |
|---|---|---|---|---|
| Board installation | Existing board may be active | Old elements/workbench/instrument targets are cleared; new runtime, challenge, controllers, and UI are installed | Requests analysis and generated verification | [Observed] Fresh-instance isolation is strong. No epoch protects late callbacks from an older board. |
| Power on/off | No active overlay for ordinary player action | External power bindings switch; runtime observes power; retest invalidates | Requests verification; stored-energy capability waits for a solver sample after power transition | [Observed] Power is solver-backed. During overlay, one pending requested value is retained. |
| Run/pause | Simulator control | Timer and solver stepping stop/start | Analysis can complete while paused; pending generated verification waits for running time to advance | [Inference] Paused `READY` plus pending verification creates a stale-action window. |
| Meter mode entry/exit | Instrument enabled | Mode and probe ownership change; exiting can trigger active-measurement cleanup | Refreshes topology and readings | [Observed] Normal meter routes pass. Private strategy refresh flags are not fully snapshot-restored. |
| Probe placement/read | Valid physical or CircuitJS target | Instrument target changes or measurement session runs | Uses solver/active measurement where applicable | [Experiment] Target invalidation and normal measurements pass. |
| Lift/reconnect lead | Electrically unpowered, no overlay, mutation allowed | Connection element removed/inserted; binding map changes | `needAnalyze`, invalidates retest, requests verification | [Observed] Structural operation is coherent when it completes. No multi-owner rollback on exception. |
| Remove component | Electrically unpowered, no overlay | Component is removed, slot is cleared, part becomes loose; installed bindings are changed | Same mutation finish path | [Experiment] Normal remove/loose-measure routes pass. |
| Reinstall existing component | Electrically unpowered, compatible part | Component/auxiliary paths and bindings are retargeted; slot remounts part | Same mutation finish path | [Observed] Identity and original-fault isolation work in ordinary routes. |
| Install replacement | Electrically unpowered, compatible catalog item | New physical part is acquired/registered, dynamic elements and stress state are added, bindings retargeted, slot mounted | Same mutation finish path | [Observed] No rollback if acquisition, registration, graph insertion, retargeting, or mount fails partway. |
| Stress damage | Powered and installed part under solver observation | Damage accumulates; a secondary failure path can open | Runtime observation integrates damage; later verification sees the changed behavior | [Experiment] Stress damage persistence and fresh-replacement isolation pass. |
| Stored-energy discharge | Board power transition and solver sample | Capacitor voltage and readiness state evolve | Readiness rejects or waits until the required discharge/sample condition | [Experiment] Stored-energy route passes. Composition with mutation and pause is not fully canaried. |
| Customer retest | `READY` and family operation available | Family state/temporal state and power may change; cached result is written | Family-specific operation may analyze/step; simple validators can inspect current values directly | [Observed] RC retest is stateful and may cycle power; pending analysis is not a universal precondition. |
| Finish Job | `READY` and cached retest passed | Live repair status is checked; challenge latches `COMPLETED` | Family status can be re-evaluated; RC status can perform another temporal sample | [Observed] Completion is behavior-based. Repeated finish after completion is protected, but first finish can have stateful side effects. |
| Completion | Challenge retest and repair satisfy family contract | Physical mutation is disabled; semantic controls remain available for completed state | No ordinary mutation verification is expected | [Experiment] Normal completion and wrong-repair rejection pass. Residual-state compositions need a dedicated canary. |
| Fresh session | Page reload or new selector session | New board/runtime/challenge/workbench/instrument state | New board verification starts independently | [Experiment] Quick Play privacy and identity boundary pass. |
| Task 41 proof/restore | Active generated board, no overlay | Owners are detached/swapped; graph/list/controller references are restored | Candidate verification occurs under proof scope | [Observed] The proof is safe through fresh candidate isolation, not a universal transaction. |
| Exception during mutation or measurement cleanup | Any operation can throw outside the narrow UI rejection type | Some owners may already have changed | Verification may be requested or skipped depending on failure location | [Inference] There is no complete compensating transaction; this is an untested but legal engineering failure path. |

## 6. Adversarial Sequence Results

[Observed] The requested A–J sequences were evaluated against source behavior and the available verifiers. “Pass” below means the bounded route completed coherently; “conditional” means the ordinary path passes but the source contains an uncovered legal edge.

| Sequence | Result | Evidence and assessment |
|---|---|---|
| A. Original-fault owner round-trip | Conditional pass | [Experiment] Normal remove, loose measurement, replacement, and terminal repair paths passed. [Inference] The complete original-fault lift/measure/reconnect/reinstall/power-on round trip is not exercised as one same-owner adversarial canary. The retained private fault backing is designed to make loose measurement real, and fault ownership is preserved in ordinary reinstall paths. |
| B. Replacement A/B identity swap | Conditional pass | [Experiment] Replacement and wrong-repair routes pass, and probe targets reject stale physical identity. [Observed] Catalog installation acquires a new physical ID and retargets bindings. [Inference] An explicit rapid A→B→A swap while probes and verification are pending lacks a dedicated proof; no silent rebind was found in the normal path. |
| C. Secondary damage remove/measure/reinstall/replace/reinstall | Pass for resistor path | [Experiment] The stress normal-player route produced a deterministic secondary open after excessive power, preserved damage through remove/reinstall, and left a fresh replacement healthy until separately stressed. The developer stress verifier also passes reset and original-fault separation checks. [Observed] Damage state is keyed by physical part ID. |
| D. Active meter/power race | Conditional pass | [Experiment] Meter and powered/unpowered routes pass, including explicit measurement sequencing. [Observed] Active measurement queues at most one pending board-power value and suppresses ordinary interaction during the overlay. [Inference] An injected exception or rapid power/mode action during cleanup is not covered by a complete outer cleanup transaction. |
| E. Stored energy/mutation | Pass for explicit route; composition gap remains | [Experiment] The stored-energy route passes, including the required solver-backed discharge/readiness behavior. [Observed] The readiness capability tracks solver samples after power transitions and derives residual voltage from live capacitors. [Inference] Mutation-while-discharge-pending combined with pause or rapid retest needs a dedicated canary because topology mutation and stored-energy readiness are coordinated through different owners. |
| F. Probes through mutation | Pass for tested identity paths | [Experiment] Normal-player and meter routes pass component removal, loose measurement, replacement, and terminal verification. [Observed] Lead targets include board instance, physical part ID, endpoint, and installation validity; board-pad targets intentionally have different stability. |
| G. Completion with residual state | Conditional pass | [Experiment] Wrong repair is rejected and correct repair completes; repeated Finish Job after completion is guarded. [Observed] Finish Job recomputes live repair status, and RC status is stateful. [Inference] Completion after a residual pending verification, stale simple-family value, or active strategy flag is not directly canaried. |
| H. Fresh-session contamination | Pass | [Experiment] Quick Play selector/session and fresh-reload normal-player privacy checks pass. New board, seed, family, physical parts, probes, report state, and failure state do not leak across the tested session boundary. |
| I. Paused/resume | Conditional pass | [Observed] `resetAction` and simulator pause/resume are supported; pending generated verification is not completed merely by analysis while paused. [Inference] A mutation followed by a paused retest can leave the challenge state externally ready while solver-backed values remain pre-settlement. Existing verifiers serialize and wait rather than stress this edge. |
| J. Rapid/repeated action | Conditional pass | [Experiment] Repeated ordinary Finish Job after completion is safe, and normal scripted flows pass. [Observed] Physical UI dispatch rechecks provider availability but does not universally reject a `READY` action while verification is pending. [Inference] Rapid lift/remove/replace/retest sequences can enqueue multiple pending verification requests without a transaction token or rollback boundary. |

[Recommendation] Convert the conditional rows into short deterministic canaries rather than relying on long end-to-end scripts. The highest-value sequence is: power off, mutate, pause before settlement, attempt retest, resume, verify, then repeat with a wrong replacement and a valid replacement.

## 7. Graph / Private Infrastructure Ownership

[Observed] The generated board intentionally keeps private infrastructure in the CircuitJS graph. This is necessary for realistic fault behavior and out-of-circuit measurement; it is not automatically a leak.

| Infrastructure | Owner and lifetime | Loose/replacement behavior | Reset behavior | Assessment |
|---|---|---|---|---|
| External power and ground switches | Board instance plus `BoardPowerController` | Remain board-owned; physical components do not own them | Board install recreates them; simulation reset resets element dynamics | [Observed] Correct private ownership. |
| Generated fault switches/shunts/value elements | `GeneratedFaultEffect` and generated bindings | Original fault backing remains connected to the original physical identity; board path can be disabled for replacement | Fault lifecycle is challenge-controlled, not physical-slot-controlled | [Observed] This preserves original-fault semantics. |
| Component connection elements | `BoardModificationController` | One canonical connection element per detachable binding, inserted or removed | New board install clears old graph; simulation reset does not restore removed connections | [Observed] Structural invariant is checked on completion, but mutation is not atomic. |
| Secondary failure path | Part-specific stress system and physical part | Follows the physical part; remains meaningful while loose where the design requires it | Stress reset clears accumulated damage and secondary path | [Experiment] Correctly isolated in stress route. |
| Dynamic replacement elements | Slot controller plus physical runtime | Registered for the life of the board instance; loose replacement can remain measurable through its own private path | New board install discards old instance graph | [Observed] Normal registration avoids duplicates; partial failure can leave an acquired/registered object without a complete mount. |
| Active-measurement stimulus | `ActiveMeasurementSession` and temporary CircuitJS elements | Exists only during overlay; pending power is applied after cleanup | No independent persistent owner | [Inference] Cleanup failure can leave temporary graph or overlay state if an exception occurs in the cleanup sequence. |

[Observed] `GeneratedBoardVerifier` checks that generated elements are active or represented by valid detachable connections, that board pads are owned, and that structural connection counts are coherent. It does not provide a general owner-transaction rollback. `PhysicalBoardRuntime.registerRuntimeSimulationElement()` appends runtime-created elements to the canonical generated vector for the board instance; ordinary replacement routes do not duplicate elements because registration rejects duplicate canonical entries and reinstall does not re-add the same object.

[Inference] The main private-infrastructure landmine is not ordinary retention. It is a future or exceptional path that changes a fault-path switch, binding endpoint, runtime registration, or physical slot before the corresponding graph and challenge state has committed. The code has no single “all owners committed” marker that would make such a split state unrepresentable.

## 8. Generated Fault vs Secondary Damage

[Observed] Generated fault and secondary damage are separate internal mechanisms. The physical-part failure projection is less expressive than the internal state.

| Physical situation | Original fault binding | Secondary damage state | Solver effect | Public projection |
|---|---|---|---|---|
| Healthy installed original | Applied only if challenge fault is active | Healthy | Healthy generated topology | Healthy/not faulted |
| Original installed with generated fault | Applied | Healthy | Intended injected fault | Generated fault |
| Original removed or loose | Retained in private original backing | Existing damage, if any, retained | Loose/out-of-circuit behavior remains physically meaningful | Original fault remains distinguishable internally |
| Correct replacement installed | Original fault backing retained but board path is not attached to replacement | New physical part has fresh stress state | Correct replacement behavior | Replacement healthy |
| Wrong-value replacement | Original fault remains on original owner | New replacement stress state | Degraded but real electrical behavior | Repair status fails |
| Original physically overstressed | Original fault may or may not also be applied | Secondary-open/failure path can activate | Changed solver graph and current | `isFaulted` true |
| Damaged original reinstalled | Original fault owner unchanged | Damage follows original physical ID | Fault plus secondary effect can coexist | Projection can be lossy |
| Fresh replacement after damaged original | Original fault owner unchanged | Fresh damage state | Replacement is not damaged by inheritance | Healthy unless separately stressed |

[Experiment] The stress verifier and stress normal-player route establish the intended causal chain: excessive simulated power produces damage; damage becomes a secondary open; removal/reinstallation preserves that state; replacement does not inherit it. No arbitrary random failure was observed in that path.

[Observed] `PhysicalResistorPart.getFailureState()` prioritizes the secondary-open result over the original generated-fault result and exposes a compact failure kind. The internal fault and damage fields remain separate, but a caller using only `isFaulted` or a single failure kind cannot distinguish “original injected fault,” “secondary damage,” and “both.”

[Inference] This lossy projection is acceptable for the current bounded UI but is a composition risk. Diagnostic, scoring, snapshot, and repair logic should not use the compact projection as the sole owner of causality once multiple failure mechanisms can coexist.

[Recommendation] Preserve separate causal fields in lifecycle evidence: original-fault owner/effect, secondary-damage owner/state, current solver failure effect, and repair status. Keep the public failure summary as a derived view rather than a restorable source of truth.

## 9. Measurement / Power / Stored-Energy Interactions

[Observed] Board electrical power, simulator run/pause, and active-measurement overlay are three different controls:

- Board power switches the generated external power bindings and is the precondition for voltage and functional behavior.
- Run/pause controls solver time advancement. A paused simulation can still analyze a changed graph, but it does not advance generated verification that requires positive simulation time.
- Active measurement temporarily modifies the graph and may queue a requested board-power state until the overlay is removed.

[Experiment] Resistance, diode, DC voltage, active meter, parallel, RC, and stored-energy routes all use the solver-backed measurement path and pass their intended checks. The stored-energy verifier observes the expected waiting/discharge/readiness behavior rather than treating a capacitor as instantly safe merely because power was switched off.

[Observed] `StoredEnergyMeasurementReadinessCapability` records that a solver sample is required after relevant power transitions and derives residual energy from live capacitor voltage. It does not own the full physical mutation lifecycle. Topology changes request instrument refresh and generated verification through other paths; there is no common settle token shared by stored-energy readiness, board mutation, and challenge retest.

[Observed] `CirSim.runTemporaryActiveMeasurement()` has the right high-level ordering in the successful case: enter overlay, install temporary stimulus, optionally queue power, analyze/step/read, remove stimulus, analyze/step, apply pending power after leaving overlay, and run verification. The method's cleanup is not an outer exception-safe transaction around every step. An exception during stimulus removal, post-removal analysis, pending-power application, or verification can leave a partial combination of `activeMeasurementOverlay`, temporary graph elements, pending power, and UI eligibility.

[Observed] RC customer retest is intentionally stateful. `RcDelayTemporalBehavior.getRepairStatus()` samples a power cycle and advances the temporal profile. The family retest restores prior board power in a `finally` block, but the challenge controller can call repair status again while deciding whether to latch completion, and Finish Job calls live repair status again. This is valid for the current bounded route but means “status” is not a pure observation.

[Inference] Repeated RC retest/status calls can consume or alter temporal state, capacitor charge, and power-transition readiness in a way that is not obvious from the word “verify.” The current Quick Play proof verifies the final result and protects a second Finish call, but it is not a proof that the first Finish call is side-effect-free or idempotent with respect to temporal state.

[Recommendation] Define the ordering contract explicitly: power transition, solver sample, stored-energy readiness, topology mutation, retest, and completion should each expose a settled state or epoch. Make status queries pure where possible; where a power-cycle sample is required, make the side effect explicit and execute it once per named operation.

## 10. Probe and Physical Identity Integrity

[Observed] Probe target identity is one of the stronger parts of the current implementation.

| Target | Identity carried | Invalidated by | Intended stability |
|---|---|---|---|
| Component lead | board instance, component ID, pad/endpoint, physical part ID, installation state | replacement, removal, incompatible retargeting, missing renderer lead | Follows the installed physical part, not merely the slot |
| Loose physical-part terminal | board instance, physical part ID, terminal | reinstall, board replacement, missing part | Follows the loose physical part only |
| Board pad | board instance, pad ID, current board endpoint | new board instance, pad removal/retargeting | Can remain valid across component removal/replacement |
| Circuit post | Circuit element and post | element deletion | Follows the actual CircuitJS element |

[Experiment] Meter and normal-player routes passed the important identity transitions: component removal invalidated installed lead access, loose measurement used the physical part, replacement did not silently reuse the old physical identity, and the final terminal check remained coherent.

[Observed] `ComponentLeadProbeTarget.isValid()` checks the generated board instance, physical part identity, installation state, endpoint, and renderer lead point. `BoardPadProbeTarget` intentionally uses board-pad identity instead of component identity. This is the correct distinction for a workbench simulator.

[Observed] Renderer/workbench selection and tray state are separate mutable state. New board installation detaches the old workbench and creates a fresh controller, which explains why the current Task 41 proof and Quick Play session routes pass. Those presentation fields are not all captured as independent fields by Task 41's snapshot.

[Inference] A future same-owner proof or composition path could retain a visually selected component, loose-part tray item, or stale instrument strategy flag after the electrical/physical owner has been restored. No current bounded route demonstrated silent probe rebinding; the risk is snapshot/composition scope rather than the ordinary target-validity logic.

## 11. Async / Reanalysis / Verification Ordering

[Observed] The current settlement path is approximately:

```text
player mutation
    -> graph/physical owners change
    -> needAnalyze() and generatedVerificationPending = true
    -> simulator may still expose challenge state READY
    -> analysis runs
    -> while running, solver time advances
    -> generated verification runs after a later positive-time sample
    -> challenge transitions or remains READY
```

[Observed] `requestGeneratedBoardVerification()` stores one boolean pending flag, marks verification unanalyzed, records the current simulation time, and requests analysis. A later request overwrites the same pending state. `runGeneratedBoardVerificationIfReady()` clears the pending flag before invoking the challenge controller's post-verification transition. There is no generation/epoch token tying the callback to the board and mutation that requested it.

[Observed] `CirSim.isChallengeInteractionEnabled()` is based primarily on challenge physical-mutation permission and overlay state. It does not require `generatedVerificationPending == false`, `analyzeFlag == false`, or a positive-time solver settlement. The physical providers correctly reject powered and overlay mutations, but the broader action envelope can remain enabled during the short post-mutation window.

[Inference] A legal sequence can therefore be:

1. Make a power-off physical mutation.
2. Leave the simulator paused or immediately issue another action.
3. Call a simple-family retest whose validator reads current CircuitJS values.
4. Observe or cache a result before the changed graph has completed its analysis/verification path.

The exact failure depends on which element fields were already recomputed, but the ordering contract is not strong enough to make the sequence impossible or self-identifying.

[Observed] The normal verifiers avoid this window by serializing actions, advancing the simulation, waiting for verification, and rechecking the visible result. That is good test discipline, but it does not establish that the product state machine rejects or safely handles rapid/paused input.

[Inference] There is also a callback identity risk at board replacement boundaries. Old board callbacks are not visibly tagged with a board-generation epoch. In ordinary installation the old lists and controller are cleared synchronously and fresh candidates are used, so no stale callback was observed. An epoch check would make this invariant explicit.

[Recommendation] Add a lifecycle token to board installation and every mutation/verification request. A callback should settle only if its token, board instance, graph owner, and challenge owner still match. Gate physical and semantic actions while settlement is pending, or make each action join/await the same transaction rather than reading current fields directly.

## 12. Task 41 Snapshot / Restore Assessment

[Observed] Task 41's snapshot is broad at the CircuitJS graph/reference level. It captures simulator list/array references and contents, graph identity, simulation time, selected elements, board/challenge/modification/workbench/family/temporal/fault references, power state, instrument developer state, UI state, and overlay/verification flags. Restore stages controller disposal, owner assignment, workbench attachment, power/instrument restoration, graph restoration, UI refresh, and restart, then asserts key references and list contents.

[Observed] The snapshot does not capture or restore all mutable state inside the referenced owners. The material omissions are:

| Omitted or compressed owner state | Why it matters |
|---|---|
| `PhysicalBoardRuntime` slot mounts, part-to-slot pointers, inventory membership, serial counters, registered capabilities/providers, and runtime element registration | A restored graph/list can refer to a physical owner map that no longer agrees with the graph. |
| Per-binding connection map beyond the `modificationsFullyRestored` boolean | A boolean can say “fully restored” while an individual connection/binding map or endpoint is stale. |
| `GeneratedChallengeController` lifecycle state, scenario, cached retest result, developer scope, and lifecycle evidence | The restored simulator owner can be attached to a challenge controller whose behavioral state has advanced or been invalidated. |
| Family state such as commanded input and temporal state such as RC residual/profile/early/late values | A graph restore does not undo semantic commands, timing samples, or profile consumption. |
| Stress/damage maps and component-internal mutable values | Solver element references and list contents do not restore damage accumulation, capacitor voltage, or private element fields. |
| Private generated-fault-effect booleans and values, including N-MOS board-path enable state | Fault ownership can disagree with a restored public challenge/fault binding. |
| Concrete instrument strategy private refresh flags | `DeveloperState.ModeState` is restored, but the strategy's private `refreshPending` field is not necessarily restored to the same value. |
| Renderer selection, tray page, and other inner workbench presentation state | A workbench can be electrically restored while retaining a presentation selection for a different physical object. |
| Asynchronous callbacks and callback identity | A pending callback can act after owner restoration unless it is independently guarded by an epoch. |

[Experiment] Task 41's developer verifier passes because it detaches the original workbench, evaluates fresh candidate boards, and restores the original owner after candidate work. The candidate boards are fresh runtimes, so the candidate mutation does not have to be undone inside the original runtime maps. Injected restore stages also demonstrate that graph/controller cleanup paths are exercised.

[Inference] Task 41 proves a valuable **fresh-candidate isolation strategy**. It does not prove that `Task41SimulationSnapshot` is a complete general-purpose snapshot for an in-place proof, exception rollback, nested composition, or same-owner mutation. Using the snapshot as if it were a complete transaction boundary would be unsafe.

[Observed] The current `assertRestored()` checks graph/list identity and content, controller ownership, power, selected elements, overlay, simulation time/UI/static guard values, and the compressed fully-restored modification flag. It cannot detect the omitted owner-state divergence listed above.

[Recommendation] Choose and document one of two safe contracts before composition:

- Make proofs strictly fresh-candidate and detached-owner based, with explicit prohibitions on same-owner mutation; or
- Extend snapshot/restore into a true owner transaction that captures physical runtime, challenge/family/temporal, fault, damage, instrument strategy, renderer, and callback epoch state, with post-restore owner-level validation.

The second option is more flexible; the first is narrower and may be a reasonable interim boundary if enforced.

## 13. Fresh-Session / Reset Integrity

[Experiment] Quick Play fresh-session checks passed. Selector/session checks showed a new family/seed/board/runtime; fresh-reload normal-player checks showed no stale part, probe, report, family, seed, or failure state and left Finish visible at the new challenge boundary.

[Observed] `installGeneratedBoard()` clears old CircuitJS elements, lists, scopes, adjustables, history, time, instrument targets, selections, and workbench ownership before installing a new generated instance. Runtime identity is per generated instance, so the normal new-board path has a clean physical inventory and slot map.

[Observed] `resetAction()` is a narrower simulation reset, not a session reset. It resets CircuitJS element dynamics and scopes, resets the physical stress runtime, requests new verification, and resets simulation time. It does not reset board power, physical slot/mount/inventory state, generated fault ownership, challenge lifecycle, probes, instrument mode, or the user's physical modifications.

[Experiment] The stress verifier deliberately passes this reset semantic: reset clears secondary damage while preserving the original generated fault and the relevant original binding. That is internally consistent with the current design, but it is not equivalent to “start a fresh challenge.”

[Inference] The reset boundary is safe when callers know whether they want a simulation reset or a session reset. It is a future contamination risk if a new proof, challenge restart, or UI affordance assumes `resetAction()` restores all physical and challenge state. The distinction should remain explicit in APIs and tests.

[Recommendation] Keep page/session replacement as the clean-owner boundary. If an in-process “restart challenge” is added, give it a separate operation that discards the complete generated board/challenge/runtime/instrument/workbench owner rather than reusing `resetAction()` as an implied session reset.

## 14. State Integrity Risk Register

| ID | Severity | Risk | Trigger | Current evidence | Impact | Recommended disposition |
|---|---|---|---|---|---|---|
| R1 | P1 conditional | `READY` remains actionable while graph analysis/verification is pending | Mutate while power-off, pause immediately, retest or repeat an action before solver settlement | [Observed] Single pending flag and READY-based gating. [Inference] Simple retest can read stale solver fields. Existing serialized verifiers pass. | Stale retest/cache, incorrect completion decision, confusing player-visible state | Add settlement gate/epoch and a paused/rapid retest canary. Treat as entry criterion for composition. |
| R2 | P1 conditional | Task 41 snapshot is not a complete owner transaction | Same-owner mutation, nested proof, exception rollback, or composition of physical/family/damage/instrument state | [Observed] Omitted mutable owners. [Experiment] Fresh-candidate Task 41 proof passes. | Graph, physical identity, challenge, damage, or UI state can diverge after restore | Enforce fresh-candidate-only proof or extend owner snapshot/restore and validation before Tasks 44–48. |
| R3 | P2 | Physical replacement/mutation has no multi-owner rollback | Exception after inventory acquisition, dynamic element registration, fault-path retargeting, or slot mount | [Observed] Slot controllers and mutation controller perform sequential writes with no compensating transaction | Part/inventory/graph/binding/fault state split | Add prepare/commit/abort or validate-and-rebuild boundary; inject failure at each write point. |
| R4 | P2 | Active-measurement cleanup is not exception-safe as a whole | Solver/analysis/stimulus removal/pending-power application throws during overlay cleanup | [Observed] Successful ordering exists; no outer guaranteed cleanup transaction | Stuck overlay, temporary graph, queued power, or disabled interaction | Wrap all cleanup in an outer `finally`, restore from a session token, and add a forced-failure canary. |
| R5 | P2 | RC status/retest is stateful and re-entrant | Retest followed by Finish, repeated status, or completion with stored-energy/timing state | [Observed] `getRepairStatus()` samples power and advances temporal behavior; current routes pass | Profile/charge/power state consumed more than once; completion depends on call count | Make status pure/cacheable or define one explicit side-effecting sample per operation. |
| R6 | P2 | Failure projection collapses original fault and secondary damage | Original fault and stress failure coexist | [Observed] Internal mechanisms are separate; `getFailureState()` prioritizes one compact kind | Diagnostic/scoring/snapshot logic loses causality | Store and report separate derived causes; do not restore only the compact projection. |
| R7 | P2 | Simulation reset is partial and easy to mistake for session reset | Reset after physical modifications, damage, active probes, or challenge progression | [Observed] Reset intentionally clears dynamics/damage but retains physical/challenge owners | New workflows can inherit physical or lifecycle state unexpectedly | Keep explicit reset/session APIs and add a reset-boundary canary. |
| R8 | P2 | Runtime validation is not a complete mutation invariant | Binding endpoint/connection-map/fault/stress registration changes without a full validation | [Observed] Runtime validation covers major slot/package/inventory relations; generated verifier covers graph structure | Divergent physical and graph ownership after exceptional or future paths | Run owner-level validation after every commit and before verification/completion. |
| R9 | P3 | Renderer and concrete instrument strategy state is not fully snapshotted | Same-owner restore after selection/mode/refresh changes | [Observed] New-owner isolation passes; private refresh and renderer fields are outside the complete snapshot | Stale visual/probe affordances or skipped refresh | Add explicit snapshot fields or prohibit same-owner proof. |
| R10 | P3 | Pending requests are coalesced and lack request identity | Rapid power/mutation/board changes | [Observed] One pending power value and one generated-verification flag are retained | Earlier intent can be overwritten; late work can settle the wrong owner | Add request/board epoch and make coalescing explicit; add rapid-action canary. |

[Inference] R1 and R2 are the only risks that currently justify a composition-level stop. R3–R5 are material correctness risks but can be bounded with a narrow transaction/settlement design. R6–R10 should be addressed before the affected owners are exposed to nested proofs or broader scoring/repair composition.

## 15. Permanent Sequence Canary Recommendations

[Recommendation] Add a small deterministic sequence suite. These are acceptance canaries, not a request to implement them as part of this read-only audit.

1. **Paused mutation settlement.** Start a known-good board, power off, perform a valid removal or replacement, pause before verification, attempt simple-family retest and Finish, resume, then repeat. Assert that no pre-settlement result can be cached or complete the challenge.
2. **Original-fault round trip.** Power off; lift a faulted component lead; measure loose/out-of-circuit behavior; reconnect; remove; reinstall the original physical part; power on; assert original fault ownership and expected customer symptom remain attached to the original physical ID.
3. **Replacement identity swap.** Install replacement A, place lead and loose probes, remove A, install B, reinstall A where supported, and assert every old target invalidates or follows its exact physical part rather than the slot.
4. **Damage persistence/isolation.** Create solver-backed over-stress, observe secondary failure, remove/measure/reinstall the damaged part, install a fresh replacement, and assert damage follows only the damaged physical ID.
5. **Stored-energy composition.** Power-cycle a capacitor-bearing board, stop at each readiness phase, perform a legal mutation, change instrument mode, pause/resume, and assert that measurements cannot bypass discharge/sample readiness.
6. **Active-measurement failure cleanup.** Force a controlled failure at each temporary-stimulus cleanup boundary and assert overlay exit, temporary-element removal, pending-power resolution, instrument restoration, and challenge gating.
7. **Task 41 owner restore.** Capture a snapshot with an active probe, selected physical part, concrete instrument mode, physical mutation state, family command, temporal state, fault state, and stress state; mutate all of them; restore; assert owner-level identity and behavior, not just list/reference equality.
8. **Fresh versus simulation reset.** Run the same board through physical mutation, damage, reset, and new-session paths; assert which state is intentionally retained by `resetAction()` and which state must disappear after a fresh session.

[Recommendation] Keep each canary seed fixed and report the lifecycle epoch, board instance ID, physical part IDs, pending flags, power state, challenge state, and final solver/retest evidence. This will make a future failure distinguish stale ordering from an electrical-model defect.

## 16. Roadmap Impact

[Observed] The roadmap at the accepted baseline marks Tasks 39–41 complete, Task 42 active in a separate session, Task 43 dependent on the current sequence, and Task 44 dependent on Task 41 and Task 43. This audit did not edit the roadmap or interfere with Task 42.

[Recommendation] The roadmap impact is:

- **Task 42:** Leave unchanged. It is an active separate task and was excluded from the pinned audit baseline after the worktree was created.
- **Task 43:** Do not newly block on this audit. Its stated scope is physical package geometry, routing, drawing, and probing coherence, not a new electrical/state-composition owner. Preserve the existing Task 42 dependency. Require the identity/probe canaries before accepting package changes that expand physical mutation surfaces.
- **Task 44:** Condition entry on an explicit lifecycle transaction/snapshot contract. The current Task 41 proof is sufficient only if Task 44 preserves fresh-candidate isolation; it is insufficient if Task 44 introduces same-owner or nested composition.
- **Tasks 45–48:** Treat R1–R5 and the relevant permanent canaries as prerequisites or explicit acceptance criteria before adding more interacting subsystems, advanced instruments, secondary-failure paths, or broad scoring/repair state. Those milestones multiply the number of mutable owners and therefore multiply the cost of restoring a partially committed state.

[Inference] The smallest roadmap correction is not to reorder the existing feature milestones silently. It is to insert or define a bounded lifecycle-hardening gate before composition: settle/epoch semantics, owner validation, exception-safe mutation/measurement cleanup, and a documented Task 41 proof boundary. This audit reports that need; it does not implement it.

## 17. Final Verdict

[Experiment] The pinned baseline is healthy for the current bounded proof envelope. It builds, the existing developer verifiers pass, ordinary family routes pass, physical identity and probe routes pass, fresh-session isolation passes, solver-backed measurements pass, and secondary damage behaves causally in the tested stress route.

[Observed] The implementation already has several important integrity properties: stable physical IDs, explicit board-instance identity, solver-backed measurements, retained private fault infrastructure, physical-part keyed damage, structural graph verification, and fresh-owner isolation for Quick Play and Task 41 candidate evaluation.

[Inference] It does **not** yet guarantee perfect coherence for all ugly legal sequences. The principal landmines are:

- a ready-looking challenge during pending analysis/verification;
- paused or rapid retest ordering;
- nontransactional multi-owner mutation and replacement;
- exception-sensitive active-measurement cleanup;
- stateful RC status calls;
- incomplete same-owner snapshot/restore coverage; and
- partial simulation reset semantics that must not be confused with a fresh session.

[Recommendation] Verdict: **conditionally acceptable for the existing bounded workflow; not ready to serve as a general lifecycle-composition foundation without a targeted hardening gate.** No P0 was found. Keep Task 43 in its existing roadmap order, but make the lifecycle canaries and the Task 41 proof-boundary decision explicit before Tasks 44–48 add more interacting mutable state.
