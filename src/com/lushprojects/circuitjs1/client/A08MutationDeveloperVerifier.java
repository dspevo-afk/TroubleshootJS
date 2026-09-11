package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import com.google.gwt.core.client.Scheduler;

/** Actual provider mutations with independent before/after ownership observations. */
final class A08MutationDeveloperVerifier {
    private final CirSim sim;
    private int assertions, compensatedWrites, diodeScenarios, freshFailures;
    private long maxSettlementMillis, maxFailedMutationMillis;
    private final StringBuilder cases = new StringBuilder("[");
    private final StringBuilder failureRows = new StringBuilder("[");

    private A08MutationDeveloperVerifier(CirSim sim) { this.sim = sim; }

    static void start(final CirSim sim, final boolean forcedFailure) {
        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            public void execute() {
                try {
                    if (!sim.troubleshootDebug || !sim.troubleshootA08Verification ||
                            !sim.developerVerifierRunning)
                        throw new IllegalStateException("A08 requires its explicit developer route");
                    if (forcedFailure)
                        throw new IllegalStateException("a08-explicit-failure-canary");
                    sim.finishA08Verification(new A08MutationDeveloperVerifier(sim).run(), null);
                } catch (Throwable failure) { sim.finishA08Verification(null, failure); }
            }
        });
    }

    private String run() {
        long started = System.currentTimeMillis();
        String resistor = CompositionMutationGateDeveloperVerifier.verify(sim);
        require(resistor.contains("real-overload-damage-preserved=true") &&
                resistor.contains("original-fault-preserved=true") &&
                resistor.contains("failed-recovery-isolated=PASS"),
            "maintained resistor compensation and independent damage corpus");
        record("resistor-compensation-and-damage");
        diode(0, false);
        diode(3, false);
        diode(0, true);
        diodeFailureIsolation();
        staleSuccessor();
        staleGraphOnly();
        freshInstallation();
        require(!sim.solverExecutor.isUnavailable() && sim.isGeneratedRuntimeSettled() &&
                !sim.activeMeasurementOverlay, "all proof owners released");
        record("proof-owner-restored");
        return "{\"version\":\"TSJ-A08-MUTATION-1\",\"status\":\"PASS\",\"cleanup\":\"PASS\"," +
            "\"assertions\":" + assertions + ",\"diodeScenarios\":" + diodeScenarios +
            ",\"compensatedWrites\":" + compensatedWrites + ",\"freshFailures\":" + freshFailures +
            ",\"wallMs\":" + (System.currentTimeMillis() - started) +
            ",\"maxSettlementMs\":" + maxSettlementMillis + ",\"maxFailedMutationMs\":" + maxFailedMutationMillis +
            ",\"cases\":" + cases + "],\"failures\":" + failureRows + "]}";
    }

    private void diode(long seed, boolean shorted) {
        Task41SimulationSnapshot protectedOwner = Task41SimulationSnapshot.capture(sim);
        boolean oldShort = sim.troubleshootDiodeShort;
        final String scenario = (shorted ? "diode-short-" : "diode-open-") + seed;
        try {
            installDiode(seed, shorted);
            final GeneratedBoardInstance board = sim.getGeneratedBoardInstance();
            final ReplaceableDiodeBoardCapability capability = ReplaceableDiodeBoardCapability.require(board);
            final DiodeSlotController controller = sim.getDiodeSlotController();
            final BoardModificationController changes = sim.getBoardModificationController();
            final PhysicalDiodePart original = capability.getSlot().getInstalledPart();
            powerOff();
            final Observation entry = new Observation(sim);
            for (int occurrence = 1; occurrence <= 2; occurrence++)
                injected(scenario, "remove", PhysicalMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT,
                    occurrence, new Runnable() { public void run() { controller.removeInstalledPart(); }});
            injected(scenario, "remove", PhysicalMutationScope.FailureStage.AFTER_SLOT_CLEAR, 1,
                new Runnable() { public void run() { controller.removeInstalledPart(); }});
            injected(scenario, "remove", PhysicalMutationScope.FailureStage.AFTER_COMMIT, 1,
                new Runnable() { public void run() { controller.removeInstalledPart(); }});
            injected(scenario, "lift", PhysicalMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT, 1,
                new Runnable() { public void run() { changes.liftLead("D1", "D1.K"); }});
            require(changes.liftLead("D1", "D1.K"), "positive diode lift");
            settle();
            final ProbeTarget retained = new ComponentLeadProbeTarget(sim, board, "D1", "D1.K",
                sim.pcbWorkbenchController.getRenderer());
            require(retained.isValid(), "lifted diode probe initially valid");
            injected(scenario, "reconnect", PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT, 1,
                new Runnable() { public void run() { changes.reconnectLead("D1", "D1.K"); }});
            require(retained.isValid(), "compensated reconnect retains exact probe target");
            require(changes.reconnectLead("D1", "D1.K"), "positive diode reconnect");
            settle();
            require(changes.removeComponent("D1"), "positive diode graph disconnect");
            settle();
            for (int occurrence = 1; occurrence <= 2; occurrence++)
                injected(scenario, "restore", PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT,
                    occurrence, new Runnable() { public void run() { changes.restoreComponent("D1"); }});
            injected(scenario, "restore", PhysicalMutationScope.FailureStage.AFTER_GRAPH_RESTORE, 1,
                new Runnable() { public void run() { changes.restoreComponent("D1"); }});
            require(changes.restoreComponent("D1"), "positive diode graph restore");
            settle();
            require(controller.removeInstalledPart(), "remove diode original");
            settle();
            require(!retained.isValid(), "retained component probe cannot follow removed part");

            PhysicalMutationScope.FailureStage[] catalogStages = {
                PhysicalMutationScope.FailureStage.AFTER_INVENTORY_ACQUIRE,
                PhysicalMutationScope.FailureStage.AFTER_CANONICAL_REGISTER,
                PhysicalMutationScope.FailureStage.AFTER_GRAPH_APPEND,
                PhysicalMutationScope.FailureStage.AFTER_PRIMARY_BINDING,
                PhysicalMutationScope.FailureStage.AFTER_ENDPOINT_RETARGET,
                PhysicalMutationScope.FailureStage.AFTER_ATTACHMENT,
                PhysicalMutationScope.FailureStage.AFTER_SLOT_MOUNT,
                PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT,
                PhysicalMutationScope.FailureStage.AFTER_GRAPH_RESTORE,
                PhysicalMutationScope.FailureStage.AFTER_COMMIT
            };
            for (PhysicalMutationScope.FailureStage stage : catalogStages) {
                int occurrences = stage == PhysicalMutationScope.FailureStage.AFTER_ENDPOINT_RETARGET ||
                    stage == PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT ? 2 : 1;
                for (int occurrence = 1; occurrence <= occurrences; occurrence++)
                    injected(scenario, "catalog", stage, occurrence, new Runnable() {
                        public void run() { controller.installNewFromCatalog(DiodeReplacementCatalog.CORRECT); }
                    });
            }
            verifyForeignAndReentry(board, capability, controller, changes);
            if (seed == 0 && !shorted) verifyRestrictedWrites(board, capability, changes);
            require(controller.installNewFromCatalog(DiodeReplacementCatalog.REVERSED), "install wrong compatible diode");
            settle();
            PhysicalDiodePart reversed = capability.getSlot().getInstalledPart();
            require(reversed.isReversedInstallation() && !reversed.isFaulted(), "wrong orientation remains a healthy wrong repair");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            settle();
            double reversedCurrent = Math.abs(reversed.getElement().getCurrent());
            require(reversedCurrent < .000001 && !board.getOperationalStates().isIlluminated("LED1") &&
                    !sim.getGeneratedChallengeController().performCustomerRetest().isPassed(),
                "wrong repair fails actual solver/customer behavior");
            powerOff();
            require(controller.removeInstalledPart(), "remove reversed diode");
            settle();
            require(controller.installNewFromCatalog(DiodeReplacementCatalog.CORRECT), "install correct diode");
            settle();
            final PhysicalDiodePart healthy = capability.getSlot().getInstalledPart();
            require(!retained.isValid() && original.isFaulted() && board.getFaultBinding().isApplied(),
                "replacement does not retarget stale probes or heal original fault");
            require(controller.removeInstalledPart(), "isolate correct diode");
            settle();
            // Use distinct retained primary/terminal bindings, not no-op assignments.
            require(controller.install(reversed.getId()), "prepare distinct binding for loose-part failure probes");
            settle();
            require(controller.removeInstalledPart(), "leave distinct binding with an empty slot");
            settle();
            PhysicalMutationScope.FailureStage[] looseStages = {
                PhysicalMutationScope.FailureStage.AFTER_PRIMARY_BINDING,
                PhysicalMutationScope.FailureStage.AFTER_ENDPOINT_RETARGET,
                PhysicalMutationScope.FailureStage.AFTER_ATTACHMENT,
                PhysicalMutationScope.FailureStage.AFTER_SLOT_MOUNT,
                PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT,
                PhysicalMutationScope.FailureStage.AFTER_GRAPH_RESTORE,
                PhysicalMutationScope.FailureStage.AFTER_COMMIT
            };
            for (PhysicalMutationScope.FailureStage stage : looseStages) {
                int occurrences = stage == PhysicalMutationScope.FailureStage.AFTER_ENDPOINT_RETARGET ||
                    stage == PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT ? 2 : 1;
                for (int occurrence = 1; occurrence <= occurrences; occurrence++)
                    injected(scenario, "install", stage, occurrence, new Runnable() {
                        public void run() { controller.install(healthy.getId()); }
                    });
            }
            require(controller.install(healthy.getId()), "reinstall exact measured part");
            settle();
            sim.setBoardPowerState(BoardPowerState.POWERED);
            settle();
            double current = Math.abs(healthy.getElement().getCurrent());
            require(current >= .005 && current <= .015 && board.getOperationalStates().isIlluminated("LED1"),
                "correct diode restores solver-driven indicator current");
            // Successful retest intentionally closes this leaf challenge. Exercise
            // remaining legal mutations first, then perform that terminal action.
            require(board.getPhysicalBoardRuntime().getLastMutationReceipt().getOutcome() ==
                    PhysicalMutationReceipt.Outcome.COMMITTED, "successful operation has a committed receipt");
            require(original.isFaulted() && !original.isInstalled() && !reversed.isInstalled(),
                "all physical identities/faults survive successful repair");
            powerOff();
            require(!sim.getGeneratedChallengeController().performCustomerRetest().isPassed() &&
                    sim.getGeneratedChallengeController().getCustomerRetestResult() != null,
                "unpowered retest publishes a genuine failed customer observation");
            require(controller.removeInstalledPart(), "direct diode removal after a published retest");
            require(sim.getGeneratedChallengeController().getCustomerRetestResult() == null,
                "direct diode replacement path invalidates prior customer result");
            boolean pendingRejected = false;
            try { changes.liftLead("R1", "R1.1"); }
            catch (BoardModificationRejectedException expected) { pendingRejected = true; }
            require(pendingRejected, "pending replacement settlement blocks unrelated direct graph mutations");
            settle();
            require(controller.install(healthy.getId()), "restore healthy part after retest invalidation");
            settle();
            require(changes.liftLead("D1", "D1.K"), "post-success mutation");
            require(sim.getGeneratedChallengeController().getCustomerRetestResult() == null,
                "diode mutation invalidates previous retest");
            settle();
            require(changes.reconnectLead("D1", "D1.K"), "restore last diagnostic lead before final customer retest");
            settle();
            sim.setBoardPowerState(BoardPowerState.POWERED);
            settle();
            require(sim.getGeneratedChallengeController().performCustomerRetest().isPassed(),
                "correct diode passes actual terminal customer retest");
            invariant();
            require(entry.board == sim.getGeneratedBoardInstance(), "no mutation replaces current board owner");
            diodeScenarios++;
            record(scenario);
        } finally {
            PhysicalMutationScope.clearFailureHookForDeveloperVerification();
            PhysicalMutationScope.clearAbortFailureHookForDeveloperVerification();
            sim.troubleshootDiodeShort = oldShort;
            protectedOwner.restore(sim);
            protectedOwner.assertRestored(sim);
        }
    }

    private void verifyForeignAndReentry(final GeneratedBoardInstance board,
            final ReplaceableDiodeBoardCapability capability, final DiodeSlotController controller,
            final BoardModificationController changes) {
        final PhysicalMutationIntent prepared = PhysicalMutationIntent.prepare(board.getPhysicalBoardRuntime(),
            board, changes, capability.getSlot(), "install", capability.getInventory().get("D1_ORIGINAL"));
        require(controller.install("D1_ORIGINAL"), "install while an earlier empty-slot intent remains prepared");
        settle();
        Observation preparedBefore = new Observation(sim);
        boolean staleRejected = false;
        PhysicalMutationScope unexpected = null;
        try { unexpected = new PhysicalMutationScope(sim, board, changes, prepared); }
        catch (IllegalStateException expected) { staleRejected = true; }
        finally {
            if (unexpected != null) unexpected.abort(new IllegalStateException("unexpected stale intent admission"));
        }
        require(staleRejected && !board.getPhysicalBoardRuntime().isMutationInProgress(),
            "prepared intent must reject a changed physical slot before obtaining a permit");
        preparedBefore.assertSame(sim, "stale prepared intent");
        require(controller.removeInstalledPart(), "remove original after stale-intent proof");
        settle();
        GeneratedBoardInstance other = new DiodeProtectedIndicatorGenerator().generate(board.getSeed());
        PhysicalDiodePart foreign = ReplaceableDiodeBoardCapability.require(other).getSlot().getInstalledPart();
        ReplaceableDiodeBoardCapability.require(other).getSlot().getPhysicalSlot().remove();
        Observation before = new Observation(sim);
        WorkbenchOperation request = WorkbenchOperation.forPartAtSlot(WorkbenchOperation.INSTALL, foreign, "D1");
        require(!controller.isAvailable(request, null) && !controller.invoke(request, null),
            "same-ID foreign inventory object is rejected before writes");
        before.assertSame(sim, "foreign inventory identity");
        final boolean[] reached = {false};
        PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
            public void afterStage(PhysicalMutationScope.FailureStage stage) {
                if (stage != PhysicalMutationScope.FailureStage.AFTER_INVENTORY_ACQUIRE) return;
                reached[0] = true;
                boolean diodeRejected = false, otherRejected = false;
                try { controller.installNewFromCatalog(DiodeReplacementCatalog.CORRECT); }
                catch (BoardModificationRejectedException expected) { diodeRejected = true; }
                try { changes.liftLead("R1", "R1.1"); }
                catch (BoardModificationRejectedException expected) { otherRejected = true; }
                require(diodeRejected && otherRejected, "runtime scope blocks same/different provider reentry");
                throw new IllegalStateException("a08-reentry-probe");
            }
        });
        try {
            controller.installNewFromCatalog(DiodeReplacementCatalog.CORRECT);
            throw new AssertionError("Reentry failure hook was bypassed");
        } catch (IllegalStateException expected) {
            require("a08-reentry-probe".equals(expected.getMessage()) && expected.getSuppressed().length == 0,
                "reentry probe preserves original failure and restores all state");
        } finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
        require(reached[0], "reentry occurred during a real partial catalog acquisition");
        before.assertSame(sim, "reentrant operation");
        invariant();
    }

    private void verifyRestrictedWrites(final GeneratedBoardInstance board,
            final ReplaceableDiodeBoardCapability capability, BoardModificationController changes) {
        Observation before = new Observation(sim);
        GeneratedBoardInstance foreign = new DiodeProtectedIndicatorGenerator().generate(3);
        PhysicalDiodePart original = capability.getInventory().get("D1_ORIGINAL");
        boolean rejected = false;
        try {
            PhysicalMutationIntent.prepare(board.getPhysicalBoardRuntime(), board, changes,
                ReplaceableDiodeBoardCapability.require(foreign).getSlot(), "install", original);
        } catch (IllegalStateException expected) { rejected = true; }
        require(rejected, "foreign physical slot rejected before obtaining a transaction permit");
        PhysicalMutationIntent intent = PhysicalMutationIntent.prepare(board.getPhysicalBoardRuntime(),
            board, changes, capability.getSlot(), "install", original);
        PhysicalMutationScope scope = new PhysicalMutationScope(sim, board, changes, intent);
        IllegalStateException aborted = new IllegalStateException("a08-scope-guard-probe");
        try {
            CircuitElm foreignElement = foreign.getSimulationElements().get(0);
            rejected = false;
            try { scope.registerCanonicalElement(foreignElement); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected && !board.ownsRuntimeSimulationElement(foreignElement),
                "restricted scope rejects foreign canonical registration before writing");
            GeneratedComponentConnectionBinding foreignBinding = foreign.getConnectionBindings().get("D1", "D1.A");
            CircuitMeasurementEndpoint foreignEndpoint = foreignBinding.getComponentEndpoint();
            rejected = false;
            try { scope.retargetEndpoint(foreignBinding, original.getTerminalForBoardPad("D1.A")); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected && foreignBinding.getComponentEndpoint() == foreignEndpoint,
                "same-ID foreign endpoint binding cannot be changed through a current scope");
            rejected = false;
            try {
                scope.acquire(ReplaceableDiodeBoardCapability.require(foreign).getInventory(), "D1_CATALOG_PART",
                    new PhysicalPartIdentityFactory<PhysicalDiodePart>() {
                        public PhysicalDiodePart create(String id) { throw new AssertionError("Foreign inventory factory was invoked"); }
                    });
            } catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "foreign inventory rejected before executing its factory");
        } finally {
            scope.abort(aborted);
            require(aborted.getSuppressed().length == 0, "restricted-scope probe compensation completed");
        }
        before.assertSame(sim, "restricted scope no-write guards");
        invariant();
        final DiodeCatalogEntry entry = capability.getCatalog().get(DiodeReplacementCatalog.CORRECT);
        final DiodeElm element = DynamicDiodeBackingAllocator.create(board.getSimulationElements());
        PhysicalPartIdentityFactory<PhysicalDiodePart> factory = new PhysicalPartIdentityFactory<PhysicalDiodePart>() {
            public PhysicalDiodePart create(String id) {
                PhysicalDiodePart part = new PhysicalDiodePart(id, entry.getSpecification(), entry.getSpecification(),
                    entry.getPlayerVisibleNameplate().forPhysicalPartId(id), element, null, false,
                    DiodePartLocation.LOOSE, new PhysicalPartProvenance(PhysicalPartProvenance.CATALOG_ACQUIRED, id));
                capability.getSlot().getPhysicalSlot().bindGeometryForAcquisition(part);
                return part;
            }
        };
        scope = new PhysicalMutationScope(sim, board, changes, capability.getSlot(), "catalog");
        aborted = new IllegalStateException("a08-single-acquisition-probe");
        try {
            scope.acquire(capability.getInventory(), "D1_CATALOG_PART", factory);
            int count = board.getPhysicalBoardRuntime().getPhysicalParts().size();
            rejected = false;
            try { scope.acquire(capability.getInventory(), "D1_CATALOG_PART", factory); }
            catch (IllegalStateException expected) { rejected = true; }
            require(rejected && board.getPhysicalBoardRuntime().getPhysicalParts().size() == count,
                "second acquisition is rejected without overwriting the first compensation ledger");
        } finally {
            scope.abort(aborted);
            require(aborted.getSuppressed().length == 0, "single acquisition rollback restores exact inventory");
        }
        before.assertSame(sim, "bounded single acquisition");
        require(sim.getDiodeSlotController().install("D1_ORIGINAL"), "install original for independent terminal oracle");
        settle();
        Observation mounted = new Observation(sim);
        scope = new PhysicalMutationScope(sim, board, changes, capability.getSlot(), "lead");
        Throwable terminalFailure = null;
        try {
            scope.retargetEndpoint(board.getConnectionBindings().get("D1", "D1.A"),
                original.getTerminalForBoardPad("D1.K"));
            scope.commit();
        } catch (IllegalStateException expected) { terminalFailure = expected; }
        finally { scope.abort(terminalFailure == null ? new IllegalStateException("wrong terminal was accepted") : terminalFailure); }
        require(terminalFailure != null && terminalFailure.getSuppressed().length == 0,
            "commit rejects same-part wrong diode terminal rather than accepting any owned endpoint");
        mounted.assertSame(sim, "wrong terminal compensation");
        require(sim.getDiodeSlotController().removeInstalledPart(), "restore empty slot after terminal oracle");
        settle();
        invariant();
        record("restricted-scope-write-guards");
    }

    private void injected(String scenario, String operation,
            final PhysicalMutationScope.FailureStage stage, final int occurrence, Runnable action) {
        final Observation before = new Observation(sim);
        final int[] seen = {0};
        final boolean[] reached = {false};
        final String marker = "a08-injected-" + scenario + "-" + operation + "-" + stage + "-" + occurrence;
        PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
            public void afterStage(PhysicalMutationScope.FailureStage actual) {
                if (actual != stage || ++seen[0] != occurrence) return;
                reached[0] = true;
                Observation intermediate = new Observation(sim);
                require(!before.state.equals(intermediate.state) || !before.identities.equals(intermediate.identities) ||
                        !before.active.equals(intermediate.active), "injection follows a real write: " + marker);
                throw new IllegalStateException(marker);
            }
        });
        boolean rejected = false;
        long started = System.currentTimeMillis();
        try { action.run(); }
        catch (IllegalStateException failure) {
            require(marker.equals(failure.getMessage()), "original mutation error retained: " + marker + ": " + failure.getMessage());
            require(failure.getSuppressed().length == 0, "compensation failed: " + marker);
            rejected = true;
        } finally {
            maxFailedMutationMillis = Math.max(maxFailedMutationMillis, System.currentTimeMillis() - started);
            PhysicalMutationScope.clearFailureHookForDeveloperVerification();
        }
        require(reached[0] && rejected, "missing required write-stage failure: " + marker);
        before.assertSame(sim, marker);
        require(sim.isGeneratedRuntimeSettled() && !sim.activeMeasurementOverlay &&
                !before.board.getPhysicalBoardRuntime().isMutationInProgress(), "compensated mutation is actionable");
        PhysicalMutationReceipt receipt = before.board.getPhysicalBoardRuntime().getLastMutationReceipt();
        require(receipt != null && receipt.getOutcome() == PhysicalMutationReceipt.Outcome.COMPENSATED &&
                receipt.isValidationPassed() && receipt.isCompensationComplete(),
            "actual compensation receipt for " + marker);
        invariant();
        compensatedWrites++;
        if (failureRows.length() > 1) failureRows.append(',');
        failureRows.append("{\"scenario\":\"").append(scenario).append("\",\"operation\":\"")
            .append(operation).append("\",\"stage\":\"").append(stage).append("\",\"occurrence\":")
            .append(occurrence).append(",\"status\":\"COMPENSATED\"}");
    }

    private void diodeFailureIsolation() {
        Task41SimulationSnapshot protectedOwner = Task41SimulationSnapshot.capture(sim);
        try {
            installDiode(0, false);
            final GeneratedBoardInstance board = sim.getGeneratedBoardInstance();
            powerOff();
            PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
                public void afterStage(PhysicalMutationScope.FailureStage stage) {
                    if (stage == PhysicalMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT)
                        throw new IllegalStateException("a08-primary-failure");
                }
            });
            PhysicalMutationScope.setAbortFailureHookForDeveloperVerification(new PhysicalMutationScope.AbortFailureHook() {
                public void afterActiveGraphClear() { throw new IllegalStateException("a08-abort-failure"); }
            });
            boolean rejected = false, cleanup = false;
            try { sim.getDiodeSlotController().removeInstalledPart(); }
            catch (IllegalStateException failure) {
                rejected = "a08-primary-failure".equals(failure.getMessage());
                for (Throwable suppressed : failure.getSuppressed())
                    if ("a08-abort-failure".equals(suppressed.getMessage())) cleanup = true;
            } finally {
                PhysicalMutationScope.clearFailureHookForDeveloperVerification();
                PhysicalMutationScope.clearAbortFailureHookForDeveloperVerification();
            }
            require(rejected && cleanup && sim.failedGeneratedRuntimeOwner == board &&
                    !sim.isChallengeInteractionEnabled() && !sim.simIsRunning() &&
                    !sim.isGeneratedRuntimeSettled() && !board.getPhysicalBoardRuntime().isMutationInProgress(),
                "failed diode compensation isolates and releases exact runtime");
            require(board.getPhysicalBoardRuntime().getLastMutationReceipt().getOutcome() ==
                    PhysicalMutationReceipt.Outcome.ISOLATED, "failed cleanup is not a compensation PASS");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            require(sim.getBoardPowerController().isElectricallyUnpowered() && !sim.performCustomerRetest().isPassed(),
                "quarantined runtime cannot energize or claim repair");
            boolean blocked = false;
            try { sim.getDiodeSlotController().installNewFromCatalog(DiodeReplacementCatalog.CORRECT); }
            catch (BoardModificationRejectedException expected) { blocked = true; }
            require(blocked && board.getFaultBinding().isApplied(), "quarantine blocks subsequent mutation without healing fault");
            record("diode-failed-compensation-isolated");
        } finally {
            PhysicalMutationScope.clearFailureHookForDeveloperVerification();
            PhysicalMutationScope.clearAbortFailureHookForDeveloperVerification();
            protectedOwner.restore(sim); protectedOwner.assertRestored(sim);
        }
    }

    private void staleSuccessor() {
        Task41SimulationSnapshot protectedOwner = Task41SimulationSnapshot.capture(sim);
        try {
            installDiode(3, false);
            powerOff();
            final GeneratedBoardInstance retiring = sim.getGeneratedBoardInstance();
            final DiodeSlotController stale = sim.getDiodeSlotController();
            final GeneratedBoardInstance successor = new DiodeProtectedIndicatorGenerator().generate(0);
            final Vector<CircuitElm> successorGraph = successor.getSimulationElements();
            final Vector<CircuitElm> expectedGraph = new Vector<CircuitElm>(successorGraph);
            PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
                public void afterStage(PhysicalMutationScope.FailureStage stage) {
                    if (stage != PhysicalMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT) return;
                    // Deliberate foreign replacement, bypassing public admission, only in this proof.
                    sim.elmList = successorGraph; sim.generatedBoardInstance = successor; sim.t = 123;
                    throw new IllegalStateException("a08-stale-owner");
                }
            });
            boolean rejected = false;
            try { stale.removeInstalledPart(); }
            catch (IllegalStateException expected) { rejected = true; }
            finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
            require(rejected && sim.elmList == successorGraph && successorGraph.equals(expectedGraph) &&
                    sim.generatedBoardInstance == successor && sim.t == 123 && sim.failedGeneratedRuntimeOwner != successor,
                "obsolete compensation never restores into successor owner/graph/clock");
            require(!retiring.getPhysicalBoardRuntime().isMutationInProgress(), "stale owner releases its own lease");
            boolean blocked = false;
            try { stale.installNewFromCatalog(DiodeReplacementCatalog.CORRECT); }
            catch (BoardModificationRejectedException expected) { blocked = true; }
            require(blocked && successorGraph.equals(expectedGraph), "stale provider cannot mutate successor by matching component ID");
            record("stale-scope-successor-preserved");
        } finally {
            PhysicalMutationScope.clearFailureHookForDeveloperVerification();
            protectedOwner.restore(sim); protectedOwner.assertRestored(sim);
        }
    }

    private void staleGraphOnly() {
        Task41SimulationSnapshot protectedOwner = Task41SimulationSnapshot.capture(sim);
        try {
            installDiode(0, false);
            powerOff();
            final GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
            final Vector<CircuitElm> replacement = new DiodeProtectedIndicatorGenerator().generate(3).getSimulationElements();
            final Vector<CircuitElm> expectedGraph = new Vector<CircuitElm>(replacement);
            PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
                public void afterStage(PhysicalMutationScope.FailureStage stage) {
                    if (stage != PhysicalMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT) return;
                    // A graph pointer change is a new execution context even when board identity is unchanged.
                    sim.elmList = replacement;
                    throw new IllegalStateException("a08-same-owner-foreign-graph");
                }
            });
            boolean rejected = false;
            try { sim.getDiodeSlotController().removeInstalledPart(); }
            catch (IllegalStateException expected) { rejected = true; }
            finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
            require(rejected && sim.elmList == replacement && replacement.equals(expectedGraph),
                "same-owner stale compensation must not clear or repopulate a replacement graph");
            require(!owner.getPhysicalBoardRuntime().isMutationInProgress() &&
                    owner.getPhysicalBoardRuntime().getLastMutationReceipt().getOutcome() == PhysicalMutationReceipt.Outcome.ISOLATED,
                "graph replacement retires the old mutation with an isolated receipt");
            record("same-owner-graph-replacement-preserved");
        } finally {
            PhysicalMutationScope.clearFailureHookForDeveloperVerification();
            protectedOwner.restore(sim); protectedOwner.assertRestored(sim);
        }
    }

    private void freshInstallation() {
        Task41SimulationSnapshot protectedOwner = Task41SimulationSnapshot.capture(sim);
        try {
            installDiode(0, false);
            powerOff();
            require(sim.getDiodeSlotController().removeInstalledPart(), "mutate original before fresh rejection");
            settle();
            final GeneratedBoardInstance original = sim.getGeneratedBoardInstance();
            Observation before = new Observation(sim);
            boolean rejected = false;
            try { FreshGeneratedRuntimeInstallation.install(sim, original, false); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "mutated original cannot masquerade as fresh candidate");
            before.assertSame(sim, "original reused as fresh");
            GeneratedBoardInstance alias = new DiodeProtectedIndicatorGenerator().generate(3);
            alias.registerRuntimeSimulationElement(original.getSimulationElements().get(0));
            rejected = false;
            try { FreshGeneratedRuntimeInstallation.install(sim, alias, false); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "fresh candidate cannot alias an original solver element");
            before.assertSame(sim, "fresh candidate solver alias");
            record("fresh-owner-no-alias");
            assertions += A08FreshOwnerAliasVerifier.run(sim);
            record("fresh-mutable-holder-aliases");
            record("fresh-callback-owner-aliases");
            record("fresh-family-captured-owner-aliases");
            assertions += A08ScopedAdmissionVerifier.run(sim);
            record("scoped-admission-identity");
            for (FreshGeneratedRuntimeInstallation.Stage stage : FreshGeneratedRuntimeInstallation.Stage.values()) {
                GeneratedBoardInstance candidate = new DiodeProtectedIndicatorGenerator().generate(3);
                FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(stage);
                rejected = false;
                try { FreshGeneratedRuntimeInstallation.install(sim, candidate, true); }
                catch (IllegalStateException expected) {
                    rejected = expected.getMessage().equals("Injected fresh installation failure after " + stage);
                } finally { FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(null); }
                require(rejected, "fresh failure after " + stage);
                before.assertSame(sim, "fresh failure " + stage);
                require(sim.isGeneratedRuntimeSettled() && !FreshGeneratedRuntimeInstallation.isInProgress(sim),
                    "fresh failure restores actionable original owner");
                freshFailures++;
            }
            GeneratedBoardInstance candidate = new DiodeProtectedIndicatorGenerator().generate(3);
            FreshGeneratedRuntimeInstallation.install(sim, candidate, false);
            require(sim.getGeneratedBoardInstance() == candidate && sim.isGeneratedRuntimeSettled(),
                "disjoint fresh diode owner publishes successfully");
            FreshGeneratedRuntimeInstallation.requireDisjoint(original, candidate);
            record("fresh-install-failure-matrix");
        } finally {
            FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(null);
            protectedOwner.restore(sim); protectedOwner.assertRestored(sim);
        }
    }

    private void installDiode(long seed, boolean shorted) {
        sim.troubleshootDiodeShort = shorted;
        GeneratedBoardInstance board = shorted ?
            new DiodeProtectedIndicatorGenerator().generateForDeveloperVerification(seed) :
            new DiodeProtectedIndicatorGenerator().generate(seed);
        FreshGeneratedRuntimeInstallation.install(sim, board, false);
        sim.getGeneratedChallengeController().beginDeveloperVerificationScope();
        require(sim.pcbWorkbenchController != null, "actual diode workbench attached to proof owner");
    }

    private void powerOff() {
        settle();
        sim.setBoardPowerState(BoardPowerState.UNPOWERED); settle();
        require(sim.getBoardPowerController().isElectricallyUnpowered(), "actual external source isolation");
    }

    private void settle() {
        long started = System.currentTimeMillis();
        sim.setSimRunning(true);
        for (int attempt = 0; attempt < 20; attempt++) {
            sim.updateCircuit();
            if (sim.isGeneratedRuntimeSettled()) {
                maxSettlementMillis = Math.max(maxSettlementMillis, System.currentTimeMillis() - started);
                return;
            }
        }
        throw new IllegalStateException("A08 mutation did not settle through production CircuitJS");
    }

    private void invariant() {
        GeneratedRuntimeInvariant.verify(sim, sim.getGeneratedBoardInstance(),
            sim.getBoardModificationController(), sim.elmList);
    }

    private void record(String name) {
        if (cases.length() > 1) cases.append(',');
        cases.append("{\"case\":\"").append(name).append("\",\"status\":\"PASS\"}");
    }

    private void require(boolean value, String message) {
        assertions++;
        if (!value) throw new IllegalStateException("A08: " + message);
    }

    /** An oracle of real state, independent of the production compensation ledger. */
    private final class Observation {
        final GeneratedBoardInstance board;
        final GeneratedChallengeController challenge;
        final BoardModificationController modifications;
        final Vector<CircuitElm> graph, active, canonical;
        final Vector<Object> identities = new Vector<Object>();
        final String state;
        Observation(CirSim sim) {
            board = sim.getGeneratedBoardInstance(); challenge = sim.getGeneratedChallengeController();
            modifications = sim.getBoardModificationController(); graph = sim.elmList;
            active = new Vector<CircuitElm>(graph); canonical = board.getSimulationElements();
            PhysicalBoardRuntime runtime = board.getPhysicalBoardRuntime();
            StringBuilder values = new StringBuilder();
            values.append(sim.getBoardPowerController().getState()).append('|')
                .append(board.getFaultBinding().isApplied()).append('|').append(board.getFaultBinding().getFault().getId());
            for (CircuitElm element : active) values.append('|').append(canonical.indexOf(element));
            for (CircuitElm element : canonical) values.append('|').append(element.dump());
            for (GeneratedComponentConnectionBinding binding : board.getConnectionBindings().getAll()) {
                identities.add(binding); identities.add(binding.getBoardEndpoint()); identities.add(binding.getComponentEndpoint());
                values.append('|').append(binding.getPadId()).append(':')
                    .append(modifications.isLeadConnected(binding.getComponentId(), binding.getPadId()));
            }
            for (PhysicalPart<?> part : runtime.getPhysicalParts()) {
                identities.add(part); identities.add(part.getMountState()); identities.add(part.getBoardSlot());
                identities.add(part.getGeometryRealization());
                for (PhysicalPartTerminal terminal : part.getTerminals()) {
                    identities.add(terminal); identities.add(terminal.getEndpoint());
                }
                values.append('|').append(part.getId()).append(':').append(part.isInstalled())
                    .append(':').append(part.isFaulted()).append(':').append(runtime.getInventoryIdForPart(part.getId()));
            }
            for (String id : board.getBoard().getComponentIds()) {
                identities.add(runtime.getSlot(id)); identities.add(runtime.getInstalledPart(id));
                if (board.getComponentBindings().hasComponentBinding(id)) {
                    identities.addAll(board.getComponentBindings().getElements(id));
                    identities.addAll(board.getComponentBindings().getAuxiliaryElements(id));
                }
                values.append('|').append(id).append(':').append(runtime.getNextPartSerial(id + "_CATALOG_PART"));
            }
            state = values.toString();
        }
        void assertSame(CirSim sim, String label) {
            Observation after = new Observation(sim);
            require(board == after.board && challenge == after.challenge && modifications == after.modifications && graph == after.graph,
                "exact owner/graph references after " + label);
            require(active.equals(after.active) && canonical.equals(after.canonical) && identities.equals(after.identities),
                "exact owned part/endpoint/mount/geometry references after " + label);
            require(state.equals(after.state), "graph/binding/inventory/fault state after " + label);
        }
    }
}
