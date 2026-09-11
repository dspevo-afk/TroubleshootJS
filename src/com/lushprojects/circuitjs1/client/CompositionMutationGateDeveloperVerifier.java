package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Independent before/after oracle for the bounded resistor transaction. */
final class CompositionMutationGateDeveloperVerifier {
    private static int assertions;
    private static int failures;
    private CompositionMutationGateDeveloperVerifier() { }

    static String verify(final CirSim sim) {
        require(sim.troubleshootDebug && sim.developerVerifierRunning, "developer entry");
        assertions = failures = 0;
        Task41SimulationSnapshot original = Task41SimulationSnapshot.capture(sim);
        try {
            FreshGeneratedRuntimeInstallation.install(sim,
                QuickPlayFamilyRegistry.generate(QuickPlayFamilyRegistry.LED_INDICATOR, 3), false);
            final GeneratedBoardInstance board = sim.getGeneratedBoardInstance();
            final GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            challenge.beginDeveloperVerificationScope();
            final ReplaceableResistorBoardCapability capability = ReplaceableResistorBoardCapability.find(board);
            final ResistorSlotController slots = capability.getController();
            final BoardModificationController modifications = sim.getBoardModificationController();
            final String component = capability.getSlot().getComponentId();
            final String pad = board.getBoard().getComponent(component).getPadIds().get(0);
            final PhysicalResistorPart faultedOriginal = capability.getSlot().getInstalledPart();
            power(sim, BoardPowerState.UNPOWERED);
            invariant(sim);
            injected(sim, capability, PhysicalMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT,
                new Runnable() { public void run() { modifications.liftLead(component, pad); }});
            require(modifications.liftLead(component, pad), "positive lift");
            settle(sim);
            invariant(sim);
            injected(sim, capability, PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT,
                new Runnable() { public void run() { modifications.reconnectLead(component, pad); }});
            require(modifications.reconnectLead(component, pad), "positive reconnect");
            settle(sim);
            injected(sim, capability, PhysicalMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT,
                new Runnable() { public void run() { modifications.removeComponent(component); }});
            require(modifications.removeComponent(component), "positive graph removal");
            settle(sim);
            injected(sim, capability, PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT,
                new Runnable() { public void run() { modifications.restoreComponent(component); }});
            require(modifications.restoreComponent(component), "positive graph restoration");
            settle(sim);
            injected(sim, capability, PhysicalMutationScope.FailureStage.AFTER_SLOT_CLEAR,
                new Runnable() { public void run() { slots.removeInstalledPart(); }});
            require(slots.removeInstalledPart(), "remove original");
            settle(sim);
            require(slots.installNewFromCatalog("R_CATALOG_220"), "install overload fixture");
            settle(sim);
            PhysicalResistorPart damaged = capability.getSlot().getInstalledPart();
            power(sim, BoardPowerState.POWERED);
            ResistorStressDamageSystem stress = capability.getStressDamageSystem();
            stress.refreshSolvedMeasurements();
            require(stress.getState(damaged.getId()).getStressRatio() > 1.5,
                "damage fixture requires real solved overload");
            stress.advanceServiceTimeForDeveloperVerification(4);
            settle(sim);
            require(damaged.getSecondaryOpenPath().isOpen() &&
                stress.getState(damaged.getId()).isFailed(), "real secondary failure");
            require(damaged.getFaultBinding() == null && faultedOriginal.getFaultBinding() ==
                board.getFaultBinding() && board.getFaultBinding().isApplied(), "distinct fault causality");
            power(sim, BoardPowerState.UNPOWERED);
            injected(sim, capability, PhysicalMutationScope.FailureStage.AFTER_SLOT_CLEAR,
                new Runnable() { public void run() { slots.removeInstalledPart(); }});
            require(slots.removeInstalledPart(), "remove damaged part");
            settle(sim);

            PhysicalMutationScope.FailureStage[] catalogStages = {
                PhysicalMutationScope.FailureStage.AFTER_INVENTORY_ACQUIRE,
                PhysicalMutationScope.FailureStage.AFTER_CANONICAL_REGISTER,
                PhysicalMutationScope.FailureStage.AFTER_STRESS_REGISTER,
                PhysicalMutationScope.FailureStage.AFTER_GRAPH_APPEND,
                PhysicalMutationScope.FailureStage.AFTER_PRIMARY_BINDING,
                PhysicalMutationScope.FailureStage.AFTER_AUXILIARY_BINDING,
                PhysicalMutationScope.FailureStage.AFTER_ENDPOINT_RETARGET,
                PhysicalMutationScope.FailureStage.AFTER_ATTACHMENT,
                PhysicalMutationScope.FailureStage.AFTER_SLOT_MOUNT,
                PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT,
                PhysicalMutationScope.FailureStage.AFTER_GRAPH_RESTORE,
                PhysicalMutationScope.FailureStage.AFTER_COMMIT
            };
            for (PhysicalMutationScope.FailureStage stage : catalogStages)
                injected(sim, capability, stage, new Runnable() {
                    public void run() { slots.installNewFromCatalog("R_CATALOG_470"); }
                });
            require(slots.install(damaged.getId()), "reinstall damaged physical identity");
            settle(sim);
            require(capability.getSlot().getInstalledPart() == damaged &&
                damaged.getSecondaryOpenPath().isOpen() && stress.getState(damaged.getId()).isFailed(),
                "reinstall preserves damage");
            require(slots.removeInstalledPart(), "remove damaged identity again");
            settle(sim);
            require(slots.install(faultedOriginal.getId()), "reinstall original");
            settle(sim);
            require(capability.getSlot().getInstalledPart() == faultedOriginal &&
                faultedOriginal.isFaulted() && damaged.isFaulted(), "original and secondary faults survive aborts");
            verifyInvariantNegatives(sim, capability);
            verifyFailedClosedAbort(sim, modifications, component, pad, board);
            return "TSJ-COMPOSITION-MUTATION-GATE-1\nassertions=" + assertions +
                "\npartial-failure-aborts=" + failures +
                "\nreal-overload-damage-preserved=true\noriginal-fault-preserved=true" +
                "\nreference-and-committed-state-negatives=PASS\nfailed-recovery-isolated=PASS";
        } finally {
            PhysicalMutationScope.clearFailureHookForDeveloperVerification();
            PhysicalMutationScope.clearAbortFailureHookForDeveloperVerification();
            original.restore(sim);
            original.assertRestored(sim);
        }
    }

    private static void injected(final CirSim sim, final ReplaceableResistorBoardCapability capability,
            final PhysicalMutationScope.FailureStage stage, Runnable operation) {
        final Observation before = new Observation(sim, capability);
        final boolean[] reached = { false };
        PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
            public void afterStage(PhysicalMutationScope.FailureStage actual) {
                if (actual != stage) return;
                reached[0] = true;
                require(!before.state.equals(new Observation(sim, capability).state),
                    "failure must follow a real partial write: " + stage);
                throw new IllegalStateException("gate-injected-" + stage);
            }
        });
        boolean rejected = false;
        try { operation.run(); }
        catch (IllegalStateException expected) {
            require(expected.getMessage().equals("gate-injected-" + stage),
                "original failure reporting: " + stage + ": " + expected.getMessage());
            require(expected.getSuppressed().length == 0, "abort cleanup failed: " + stage);
            rejected = true;
        } finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
        require(reached[0] && rejected, "required partial failure was not exercised: " + stage);
        before.assertSame(sim, capability, stage.toString());
        invariant(sim);
        failures++;
    }

    private static void verifyInvariantNegatives(final CirSim sim,
            final ReplaceableResistorBoardCapability capability) {
        final GeneratedBoardInstance board = sim.getGeneratedBoardInstance();
        final Vector<CircuitElm> saved = new Vector<CircuitElm>(sim.elmList);
        sim.elmList.add(saved.get(0));
        try { rejectsInvariant(sim, "duplicate graph owner"); }
        finally { sim.elmList.clear(); sim.elmList.addAll(saved); }
        CircuitElm foreign = new ResistorElm(0, 0);
        sim.elmList.add(foreign);
        try { rejectsInvariant(sim, "foreign active element"); }
        finally { sim.elmList.remove(foreign); }
        CircuitElm body = capability.getSlot().getInstalledPart().getElement();
        sim.elmList.remove(body);
        try { rejectsInvariant(sim, "dangling installed backing"); }
        finally { sim.elmList.clear(); sim.elmList.addAll(saved); }
        GeneratedComponentConnectionBinding lead = board.getConnectionBindings()
            .getForComponent(capability.getSlot().getComponentId()).get(0);
        CircuitMeasurementEndpoint endpoint = lead.getComponentEndpoint();
        lead.setComponentEndpoint(new CircuitPostMeasurementEndpoint(foreign, 0));
        try { rejectsInvariant(sim, "foreign component endpoint"); }
        finally { lead.setComponentEndpoint(endpoint); }
        CircuitElm wire = lead.getConnectionElement();
        int wireX = wire.x, wireY = wire.y, wireX2 = wire.x2, wireY2 = wire.y2;
        CircuitPostMeasurementEndpoint wrongTerminal = new CircuitPostMeasurementEndpoint(body, 1);
        Point wrongPoint = body.getPost(1);
        Point boardPoint = ((CircuitPostMeasurementEndpoint) lead.getBoardEndpoint())
            .getElement().getPost(((CircuitPostMeasurementEndpoint) lead.getBoardEndpoint()).getPostIndex());
        lead.setComponentEndpoint(wrongTerminal);
        wire.x = boardPoint.x; wire.y = boardPoint.y;
        wire.x2 = wrongPoint.x; wire.y2 = wrongPoint.y;
        wire.setPoints();
        try {
            // The wrong post remains owned and the conductor really reaches
            // it. Membership and geometry alone must not bless this mapping.
            board.getConnectionBindings().validateAgainst(board.getBoard(),
                board.getSimulationElements(), board.getComponentBindings(),
                board.getExternalPowerBindings(), board.getFaultBinding());
            require(true, "coherent wrong-terminal fixture passes legacy geometry");
            rejectsInvariantReason(sim, "owned but wrong resistor terminal",
                "Physical connection disagrees with installed terminal:");
        } finally {
            lead.setComponentEndpoint(endpoint);
            wire.x = wireX; wire.y = wireY; wire.x2 = wireX2; wire.y2 = wireY2;
            wire.setPoints();
        }
        GeneratedComponentBindings componentBindings = board.getComponentBindings();
        String componentId = capability.getSlot().getComponentId();
        Vector<CircuitElm> primary = componentBindings.getElements(componentId);
        Vector<CircuitElm> auxiliary = componentBindings.getAuxiliaryElements(componentId);
        Vector<CircuitElm> aliasAuxiliary = new Vector<CircuitElm>(auxiliary);
        aliasAuxiliary.add(lead.getConnectionElement());
        componentBindings.restoreForMutation(componentId, primary, aliasAuxiliary);
        try { rejectsInvariantReason(sim, "connection claimed as component backing",
            "Mutable generated element has duplicate ownership:"); }
        finally { componentBindings.restoreForMutation(componentId, primary, auxiliary); }
        CircuitElm powerBacking = board.getExternalPowerBindings().getBinding(
            board.getBoard().getPowerInputIds().get(0)).getBackingElements().get(0);
        aliasAuxiliary = new Vector<CircuitElm>(auxiliary);
        aliasAuxiliary.add(powerBacking);
        componentBindings.restoreForMutation(componentId, primary, aliasAuxiliary);
        try { rejectsInvariantReason(sim, "power claimed as component backing",
            "Mutable generated element has duplicate ownership:"); }
        finally { componentBindings.restoreForMutation(componentId, primary, auxiliary); }
        // This is canonical and active, and is an authentic physical part,
        // but belongs to a loose part instead of the installed slot owner.
        CircuitElm looseBacking = capability.getInventory().getLooseParts().get(0).getElement();
        aliasAuxiliary = new Vector<CircuitElm>(auxiliary);
        aliasAuxiliary.add(looseBacking);
        componentBindings.restoreForMutation(componentId, primary, aliasAuxiliary);
        try { rejectsInvariantReason(sim, "loose part claimed by installed component",
            "Component binding disagrees with installed part backing:"); }
        finally { componentBindings.restoreForMutation(componentId, primary, auxiliary); }
        PhysicalBoardSlot slot = capability.getSlot().getPhysicalSlot();
        PhysicalPart<?> part = slot.remove();
        try { rejectsInvariant(sim, "empty slot with connected graph"); }
        finally { slot.install(part); }
        sim.activeMeasurementOverlay = true;
        try { rejectsInvariant(sim, "unsafe overlay"); }
        finally { sim.activeMeasurementOverlay = false; }
        boolean rejected = false;
        try {
            GeneratedRuntimeInvariant.verify(sim, board, new BoardModificationController(sim, board),
                sim.elmList);
        } catch (IllegalStateException expected) { rejected = true; }
        require(rejected, "duplicate same-instance modification owner rejected");
        rejected = false;
        try { board.getPhysicalBoardRuntime().validateSupportedCompositionProviders(); }
        catch (IllegalStateException expected) { rejected = true; }
        require(rejected, "LED mutable provider must not enter resistor-only composition");
        invariant(sim);
    }

    private static void verifyFailedClosedAbort(final CirSim sim,
            BoardModificationController modifications, String component, String pad,
            GeneratedBoardInstance board) {
        final boolean[] cleared = { false };
        PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
            public void afterStage(PhysicalMutationScope.FailureStage stage) {
                if (stage == PhysicalMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT)
                    throw new IllegalStateException("primary-mutation-failure");
            }
        });
        PhysicalMutationScope.setAbortFailureHookForDeveloperVerification(new PhysicalMutationScope.AbortFailureHook() {
            public void afterActiveGraphClear() {
                cleared[0] = sim.elmList.isEmpty();
                throw new IllegalStateException("active-graph-reconstruction-failure");
            }
        });
        boolean rejected = false;
        try { modifications.liftLead(component, pad); }
        catch (IllegalStateException failure) {
            require("primary-mutation-failure".equals(failure.getMessage()), "primary abort error retained");
            boolean cleanupFound = false;
            for (Throwable cleanup : failure.getSuppressed())
                if ("active-graph-reconstruction-failure".equals(cleanup.getMessage())) cleanupFound = true;
            require(cleanupFound, "cleanup error retained separately");
            rejected = true;
        } finally {
            PhysicalMutationScope.clearFailureHookForDeveloperVerification();
            PhysicalMutationScope.clearAbortFailureHookForDeveloperVerification();
        }
        require(rejected && cleared[0] && sim.failedGeneratedRuntimeOwner == board &&
            !sim.simIsRunning() && !sim.isGeneratedRuntimeSettled() &&
            !sim.isChallengeInteractionEnabled() && !sim.instrumentController.isInteractionEnabledForDeveloperVerification() &&
            sim.getBoardPowerController().isElectricallyUnpowered(), "failed recovery must isolate and disable");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        require(sim.getBoardPowerController().isElectricallyUnpowered() &&
            sim.getGeneratedChallengeController().getCustomerRetestResult() == null &&
            !sim.performCustomerRetest().isPassed(), "failed recovery cannot energize or retest");
        boolean mutationRejected = false;
        try { modifications.restoreComponent(component); }
        catch (BoardModificationRejectedException expected) { mutationRejected = true; }
        require(mutationRejected && board.getFaultBinding().isApplied(), "failed recovery preserves fault and rejects mutation");
    }

    private static void rejectsInvariant(CirSim sim, String name) {
        boolean rejected = false;
        try { invariant(sim); } catch (IllegalStateException expected) { rejected = true; }
        require(rejected, "owner invariant did not reject " + name);
    }

    private static void rejectsInvariantReason(CirSim sim, String name, String reason) {
        boolean rejected = false;
        try { invariant(sim); }
        catch (IllegalStateException expected) {
            rejected = expected.getMessage() != null && expected.getMessage().startsWith(reason);
        }
        require(rejected, "owner invariant did not specifically reject " + name);
    }

    private static void invariant(CirSim sim) {
        GeneratedRuntimeInvariant.verify(sim, sim.getGeneratedBoardInstance(),
            sim.getBoardModificationController(), sim.elmList);
    }

    private static final class Observation {
        final GeneratedBoardInstance board;
        final GeneratedChallengeController challenge;
        final BoardModificationController modifications;
        final Vector<CircuitElm> active, canonical;
        final Vector<PhysicalPart> parts;
        final Vector<Object> identities = new Vector<Object>();
        final String state;
        Observation(CirSim sim, ReplaceableResistorBoardCapability capability) {
            board = sim.getGeneratedBoardInstance();
            challenge = sim.getGeneratedChallengeController();
            modifications = sim.getBoardModificationController();
            active = new Vector<CircuitElm>(sim.elmList);
            canonical = board.getSimulationElements();
            parts = board.getPhysicalBoardRuntime().getPhysicalParts();
            StringBuilder result = new StringBuilder();
            result.append(sim.getBoardPowerController().getState()).append('|')
                .append(board.getFaultBinding().isApplied()).append('|')
                .append(board.getFaultBinding().getFault().getId()).append('|');
            for (CircuitElm element : active) result.append(canonical.indexOf(element)).append(',');
            for (CircuitElm element : canonical) result.append('|').append(element.dump());
            for (GeneratedComponentConnectionBinding binding : board.getConnectionBindings().getAll()) {
                result.append('|').append(binding.getPadId()).append(':')
                    .append(modifications.isLeadConnected(binding.getComponentId(), binding.getPadId()));
                identities.add(binding); identities.add(binding.getBoardEndpoint());
                identities.add(binding.getComponentEndpoint());
            }
            for (PhysicalPart<?> part : parts) {
                result.append('|').append(part.getId()).append(':').append(part.isInstalled())
                    .append(':').append(part.isFaulted()).append(':')
                    .append(part.getBoardSlot() == null ? "loose" : part.getBoardSlot().getId());
                identities.add(part.getMountState()); identities.add(part.getBoardSlot());
                for (PhysicalPartTerminal terminal : part.getTerminals()) {
                    identities.add(terminal); identities.add(terminal.getEndpoint());
                }
            }
            for (String id : board.getBoard().getComponentIds()) {
                if (!board.getComponentBindings().hasComponentBinding(id)) continue;
                identities.addAll(board.getComponentBindings().getElements(id));
                identities.addAll(board.getComponentBindings().getAuxiliaryElements(id));
            }
            for (PhysicalResistorPart part : capability.getInventory().getAll()) {
                result.append('|').append(part.getId());
                if (!capability.getStressDamageSystem().ownsState(part)) {
                    result.append(":stress-unregistered");
                    continue;
                }
                ResistorStressState stress = capability.getStressDamageSystem().getState(part.getId());
                identities.add(stress);
                result.append(':').append(stress.getAccumulatedDamage()).append(':')
                    .append(stress.getServiceTime()).append(':').append(stress.getFailureServiceTime())
                    .append(':').append(stress.isFailed()).append(':')
                    .append(part.getSecondaryOpenPath().isOpen());
            }
            result.append("|next-catalog-serial=").append(board.getPhysicalBoardRuntime()
                .getNextPartSerial(capability.getSlot().getComponentId() + "_CATALOG_PART"));
            state = result.toString();
        }
        void assertSame(CirSim sim, ReplaceableResistorBoardCapability capability, String stage) {
            Observation after = new Observation(sim, capability);
            require(board == after.board && challenge == after.challenge && modifications == after.modifications,
                "owner references after " + stage);
            require(active.equals(after.active) && canonical.equals(after.canonical) &&
                parts.equals(after.parts) && identities.equals(after.identities), "owned references after " + stage);
            require(state.equals(after.state), "semantic/graph/inventory/damage state after " + stage);
            require(sim.isGeneratedRuntimeSettled() && !sim.activeMeasurementOverlay,
                "actionability after validated abort " + stage);
        }
    }

    private static void power(CirSim sim, BoardPowerState power) {
        sim.setBoardPowerState(power); settle(sim);
        require(sim.getBoardPowerController().getState() == power, "power transition");
    }
    private static void settle(CirSim sim) {
        sim.setSimRunning(true);
        for (int attempt = 0; attempt < 20; attempt++) {
            sim.updateCircuit();
            if (sim.isGeneratedRuntimeSettled()) return;
        }
        throw new IllegalStateException("Composition mutation gate did not settle");
    }
    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException("Composition mutation gate: " + message);
    }
}
