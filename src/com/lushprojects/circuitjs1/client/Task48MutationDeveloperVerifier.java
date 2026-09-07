package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Developer-only physical mutation proof for the composed controlled
 * indicator.  This verifier runs against the already installed normal
 * challenge owner.  It deliberately uses the public board mutation providers
 * and legal power transitions; it never replaces the live owner or restores
 * one by copying a simulator snapshot.
 */
final class Task48MutationDeveloperVerifier {
    private static final String DRIVER_COMPONENT =
        "tsj-block-v1/controlled-indicator@1/driver/component/RG";
    private static final String LOAD_COMPONENT =
        "tsj-block-v1/controlled-indicator@1/load/component/RLOAD";
    private static final String CATALOG_PROBE = "R_CATALOG_470";
    private static final String CATALOG_DAMAGE = "R_CATALOG_10";

    private static int assertions;
    private static int partialAborts;
    private static int lifecycleChecks;
    private static int damageChecks;

    private Task48MutationDeveloperVerifier() { }

    /**
     * Verify both resistor providers on the current healthy, fault-cleared
     * composed challenge.  The caller owns the surrounding fault scope and
     * reapplies the selected challenge fault after this method returns.
     */
    static String verify(final CirSim sim) {
        require(sim != null && sim.troubleshootDebug && sim.developerVerifierRunning,
            "explicit developer verification is required");
        final GeneratedBoardInstance board = sim.getGeneratedBoardInstance();
        final GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(board != null && ControlledIndicatorBlockContributions.FAMILY_ID.equals(
                board.getCircuitFamilyId()) && !board.isDeveloperOnlyFaultRoute(),
            "normal controlled-indicator owner is required");
        require(challenge != null && challenge.isReady() &&
                challenge.isDeveloperVerificationScopeActive() &&
                board.getFaultBinding() != null && !board.getFaultBinding().isApplied(),
            "healthy fault-cleared developer scope is required");
        require(sim.getGeneratedBoardInstance() == board && sim.isGeneratedRuntimeSettled() &&
                sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
                sim.getBoardPowerController().getBindingsForDeveloperVerification() ==
                    board.getExternalPowerBindings(),
            "powered settled owner is required");

        final ReplaceableResistorBoardCapability driver =
            ReplaceableResistorBoardCapability.find(board.getPhysicalBoardRuntime(),
                DRIVER_COMPONENT);
        final ReplaceableResistorBoardCapability load =
            ReplaceableResistorBoardCapability.find(board.getPhysicalBoardRuntime(),
                LOAD_COMPONENT);
        require(driver != null && load != null && driver != load &&
                driver.getController() != null && load.getController() != null,
            "both composed resistor providers are required");
        final PhysicalResistorPart driverOriginal = requireOriginal(driver);
        final PhysicalResistorPart loadOriginal = requireOriginal(load);
        final GeneratedFaultBinding retainedFault = board.getFaultBinding();

        assertions = 0;
        partialAborts = 0;
        lifecycleChecks = 0;
        damageChecks = 0;
        Throwable primary = null;
        try {
            power(sim, BoardPowerState.UNPOWERED, board, "mutation-entry-power-off");
            String driverEvidence = verifyProvider(sim, board, driver, driverOriginal,
                "RG", false);
            String loadEvidence = verifyProvider(sim, board, load, loadOriginal,
                "RLOAD", true);
            String damageEvidence = verifySecondaryDamage(sim, board, load, loadOriginal,
                retainedFault);

            restoreOriginal(sim, board, driver, driverOriginal);
            restoreOriginal(sim, board, load, loadOriginal);
            sim.getGeneratedBoardInstance().invokeOperation(
                GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
            settle(sim, board, "mutation-final-control-low");
            power(sim, BoardPowerState.POWERED, board, "mutation-final-power-on");
            GeneratedRuntimeInvariant.verify(sim, board,
                sim.getBoardModificationController(), sim.elmList);
            require(!retainedFault.isApplied() && driverOriginal.isInstalled() &&
                    loadOriginal.isInstalled(), "final healthy physical state");
            return "{\"protocol\":\"TSJ-TASK48-MUTATION-1\",\"status\":\"PASS\",\"family\":\"COMPOSED_CONTROLLED_INDICATOR\",\"providers\":{" +
                "\"RG\":" + driverEvidence + ",\"RLOAD\":" + loadEvidence +
                "},\"secondaryDamage\":" + damageEvidence +
                ",\"partialAbortStages\":" + partialAborts +
                ",\"lifecycleChecks\":" + lifecycleChecks +
                ",\"damageChecks\":" + damageChecks +
                ",\"assertions\":" + assertions +
                ",\"healthySettled\":true}";
        } catch (Throwable failure) {
            primary = failure;
            rethrow(failure);
            return null;
        } finally {
            ResistorMutationScope.clearFailureHookForDeveloperVerification();
            ResistorMutationScope.clearAbortFailureHookForDeveloperVerification();
            try {
                // Cleanup is itself a sequence of legal public actions.  It
                // is intentionally not a snapshot restore or direct registry
                // mutation.  Preserve the first failure if cleanup also fails.
                power(sim, BoardPowerState.UNPOWERED, board, "mutation-cleanup-power-off");
                restoreOriginal(sim, board, driver, driverOriginal);
                restoreOriginal(sim, board, load, loadOriginal);
                sim.getGeneratedBoardInstance().invokeOperation(
                    GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
                settle(sim, board, "mutation-cleanup-control-low");
                power(sim, BoardPowerState.POWERED, board, "mutation-cleanup-power-on");
                GeneratedRuntimeInvariant.verify(sim, board,
                    sim.getBoardModificationController(), sim.elmList);
            } catch (Throwable cleanup) {
                if (primary != null)
                    primary.addSuppressed(cleanup);
                else
                    rethrow(cleanup);
            }
        }
    }

    private static String verifyProvider(final CirSim sim, final GeneratedBoardInstance board,
            final ReplaceableResistorBoardCapability capability,
            final PhysicalResistorPart original, String shortName, boolean exerciseDamage) {
        final BoardModificationController modifications = sim.getBoardModificationController();
        final ResistorSlotController slots = capability.getController();
        final String componentId = capability.getSlot().getComponentId();
        final String padId = capability.getSlot().getPhysicalSlot().getPadIds().get(0);
        require(capability.getSlot().getInstalledPart() == original &&
                modifications.getComponentState(componentId) == ComponentPhysicalState.INSTALLED,
            shortName + " starts installed");

        // Positive physical actions establish that both providers accept the
        // same player operations before the abort matrix begins.
        require(modifications.liftLead(componentId, padId), shortName + " lift accepted");
        settle(sim, board, shortName + " lift");
        require(!modifications.isLeadConnected(componentId, padId) &&
                modifications.getComponentState(componentId) == ComponentPhysicalState.LEAD_LIFTED,
            shortName + " lift state");
        require(modifications.reconnectLead(componentId, padId), shortName + " reconnect accepted");
        settle(sim, board, shortName + " reconnect");
        require(modifications.isLeadConnected(componentId, padId) &&
                modifications.getComponentState(componentId) == ComponentPhysicalState.INSTALLED,
            shortName + " reconnect state");
        require(modifications.removeComponent(componentId), shortName + " graph removal accepted");
        settle(sim, board, shortName + " graph removal");
        require(modifications.getComponentState(componentId) == ComponentPhysicalState.REMOVED,
            shortName + " graph removal state");
        require(modifications.restoreComponent(componentId), shortName + " graph restore accepted");
        settle(sim, board, shortName + " graph restore");
        require(modifications.getComponentState(componentId) == ComponentPhysicalState.INSTALLED,
            shortName + " graph restore state");
        GeneratedRuntimeInvariant.verify(sim, board, modifications, sim.elmList);

        // Graph-only failure stages use the actual BoardModificationController.
        inject(sim, board, capability, ResistorMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT,
            new Runnable() { public void run() {
                modifications.liftLead(componentId, padId);
            }});
        require(modifications.isLeadConnected(componentId, padId), shortName + " aborted lift restored");
        require(modifications.liftLead(componentId, padId), shortName + " second lift accepted");
        settle(sim, board, shortName + " second lift");
        inject(sim, board, capability, ResistorMutationScope.FailureStage.AFTER_GRAPH_CONNECT,
            new Runnable() { public void run() {
                modifications.reconnectLead(componentId, padId);
            }});
        require(!modifications.isLeadConnected(componentId, padId), shortName + " aborted reconnect preserved lift");
        require(modifications.reconnectLead(componentId, padId), shortName + " second reconnect accepted");
        settle(sim, board, shortName + " second reconnect");

        // The slot clear stage is reached by the accepted remove operation.
        inject(sim, board, capability, ResistorMutationScope.FailureStage.AFTER_SLOT_CLEAR,
            new Runnable() { public void run() { slots.removeInstalledPart(); }});
        require(capability.getSlot().getInstalledPart() == original,
            shortName + " aborted slot clear restored original");
        require(slots.removeInstalledPart(), shortName + " original removal accepted");
        settle(sim, board, shortName + " original removal");
        require(capability.getSlot().isEmpty() &&
                modifications.getComponentState(componentId) == ComponentPhysicalState.REMOVED,
            shortName + " empty retained slot state");

        ResistorMutationScope.FailureStage[] stages = catalogStages();
        for (final ResistorMutationScope.FailureStage stage : stages) {
            inject(sim, board, capability, stage, new Runnable() {
                public void run() { slots.installNewFromCatalog(CATALOG_PROBE); }
            });
            require(capability.getSlot().isEmpty(), shortName + " catalog abort left empty slot");
        }

        // Exercise a successful catalog replacement and then reinstall the
        // retained original through the public catalog/slot lifecycle.
        require(slots.installNewFromCatalog(CATALOG_PROBE), shortName + " catalog install accepted");
        settle(sim, board, shortName + " catalog install");
        PhysicalResistorPart replacement = capability.getSlot().getInstalledPart();
        require(replacement != null && replacement != original && !replacement.isFaulted() &&
                original.getLocation() == ResistorPartLocation.LOOSE,
            shortName + " catalog identity and provenance");
        lifecycleChecks++;
        require(slots.removeInstalledPart(), shortName + " catalog removal accepted");
        settle(sim, board, shortName + " catalog removal");
        require(slots.install(original.getId()), shortName + " retained original restore accepted");
        settle(sim, board, shortName + " retained original restore");
        require(capability.getSlot().getInstalledPart() == original && original.isInstalled() &&
                !replacement.isInstalled() && !replacement.isFaulted(),
            shortName + " retained original lifecycle");
        lifecycleChecks++;
        GeneratedRuntimeInvariant.verify(sim, board, modifications, sim.elmList);
        return "{\"abortStages\":" + stages.length + ",\"liftReconnect\":true,\"removeRestore\":true,\"catalogLifecycle\":true" +
            (exerciseDamage ? ",\"damageOwner\":true" : ",\"damageOwner\":false") + "}";
    }

    private static String verifySecondaryDamage(final CirSim sim, final GeneratedBoardInstance board,
            final ReplaceableResistorBoardCapability capability, final PhysicalResistorPart original,
            final GeneratedFaultBinding retainedFault) {
        final ResistorSlotController slots = capability.getController();
        final ResistorStressDamageSystem stress = capability.getStressDamageSystem();
        final String componentId = capability.getSlot().getComponentId();
        require(LOAD_COMPONENT.equals(componentId), "secondary damage uses RLOAD provider");
        final boolean loadOwnsSelectedFault = retainedFault.getFault().getTargetComponentId()
            .equals(componentId);
        require((loadOwnsSelectedFault && original.getFaultBinding() == retainedFault) ||
                (!loadOwnsSelectedFault && original.getFaultBinding() == null),
            "selected fault ownership is explicit before secondary damage");
        require(sim.getBoardPowerController().isElectricallyUnpowered(),
            "secondary damage starts unpowered");
        require(slots.removeInstalledPart(), "secondary damage original removal accepted");
        settle(sim, board, "secondary original removal");
        require(slots.installNewFromCatalog(CATALOG_DAMAGE),
            "secondary damage catalog install accepted");
        settle(sim, board, "secondary damage catalog install");
        final PhysicalResistorPart damaged = capability.getSlot().getInstalledPart();
        require(damaged != null && damaged != original && damaged.getFaultBinding() == null,
            "secondary replacement is distinct and has no generated fault");
        board.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH, sim);
        settle(sim, board, "secondary control-high");
        power(sim, BoardPowerState.POWERED, board, "secondary power-on");
        stress.refreshSolvedMeasurements();
        ResistorStressState state = stress.getState(damaged.getId());
        require(state.getStressRatio() > 1.5,
            "RLOAD damage fixture lacks solver-derived overload: " + state.getStressRatio());
        damageChecks++;
        stress.advanceServiceTimeForDeveloperVerification(4);
        settle(sim, board, "secondary service-time");
        require(damaged.getSecondaryOpenPath().isOpen() && state.isFailed() &&
                damaged.getFaultBinding() == null && board.getFaultBinding() == retainedFault &&
                ((loadOwnsSelectedFault && original.getFaultBinding() == retainedFault) ||
                    (!loadOwnsSelectedFault && original.getFaultBinding() == null)) &&
                !original.isFaulted(),
            "secondary failure is separate from retained original fault");
        damageChecks++;

        power(sim, BoardPowerState.UNPOWERED, board, "secondary power-off");
        require(slots.removeInstalledPart(), "secondary damaged removal accepted");
        settle(sim, board, "secondary damaged removal");
        require(slots.install(original.getId()), "secondary original reinstall accepted");
        settle(sim, board, "secondary original reinstall");
        require(capability.getSlot().getInstalledPart() == original &&
                ((loadOwnsSelectedFault && original.getFaultBinding() == retainedFault) ||
                    (!loadOwnsSelectedFault && original.getFaultBinding() == null)) &&
                damaged.getSecondaryOpenPath().isOpen() &&
                state.isFailed(), "secondary removal did not heal or steal identity");
        damageChecks++;
        return "{\"provider\":\"RLOAD\",\"catalog\":\"R_CATALOG_10\",\"stressRatio\":" +
            state.getStressRatio() + ",\"secondaryOpen\":true,\"originalFaultRetained\":true,\"status\":\"PASS\"}";
    }

    private static void restoreOriginal(CirSim sim, GeneratedBoardInstance board,
            ReplaceableResistorBoardCapability capability, PhysicalResistorPart original) {
        ResistorSlotController slots = capability.getController();
        BoardModificationController modifications = sim.getBoardModificationController();
        String componentId = capability.getSlot().getComponentId();
        if (!capability.getSlot().isEmpty() && capability.getSlot().getInstalledPart() != original) {
            require(slots.removeInstalledPart(), "legal cleanup removed replacement " + componentId);
            settle(sim, board, "legal cleanup remove " + componentId);
        }
        if (capability.getSlot().isEmpty()) {
            require(slots.install(original.getId()), "legal cleanup restored original " + componentId);
            settle(sim, board, "legal cleanup install " + componentId);
        }
        if (modifications.getComponentState(componentId) != ComponentPhysicalState.INSTALLED) {
            require(modifications.restoreComponent(componentId), "legal cleanup restored graph " + componentId);
            settle(sim, board, "legal cleanup graph " + componentId);
        }
        for (GeneratedComponentConnectionBinding binding : board.getConnectionBindings()
                .getForComponent(componentId)) {
            if (!modifications.isLeadConnected(componentId, binding.getPadId())) {
                require(modifications.reconnectLead(componentId, binding.getPadId()),
                    "legal cleanup reconnected " + binding.getPadId());
                settle(sim, board, "legal cleanup lead " + binding.getPadId());
            }
        }
        require(capability.getSlot().getInstalledPart() == original &&
                modifications.getComponentState(componentId) == ComponentPhysicalState.INSTALLED,
            "legal cleanup did not restore original installed state " + componentId);
    }

    private static void inject(final CirSim sim, final GeneratedBoardInstance board,
            final ReplaceableResistorBoardCapability capability,
            final ResistorMutationScope.FailureStage target, Runnable operation) {
        final Observation before = new Observation(sim, board);
        final boolean[] reached = { false };
        final boolean[] partial = { false };
        final IllegalStateException injected =
            new IllegalStateException("task48-mutation-injected-" + target);
        ResistorMutationScope.setFailureHookForDeveloperVerification(
            new ResistorMutationScope.FailureHook() {
                public void afterStage(ResistorMutationScope.FailureStage actual) {
                    if (actual != target) return;
                    reached[0] = true;
                    partial[0] = !before.sameState(new Observation(sim, board));
                    throw injected;
                }
            });
        Throwable caught = null;
        try { operation.run(); }
        catch (Throwable failure) { caught = failure; }
        finally { ResistorMutationScope.clearFailureHookForDeveloperVerification(); }
        require(caught == injected && caught.getSuppressed().length == 0,
            "exact injected abort for " + target);
        require(reached[0] && partial[0], "real partial write reached " + target);
        before.assertSame(sim, board, target.toString());
        partialAborts++;
        settle(sim, board, "abort " + target);
        GeneratedRuntimeInvariant.verify(sim, board,
            sim.getBoardModificationController(), sim.elmList);
    }

    private static ResistorMutationScope.FailureStage[] catalogStages() {
        return new ResistorMutationScope.FailureStage[] {
            ResistorMutationScope.FailureStage.AFTER_INVENTORY_ACQUIRE,
            ResistorMutationScope.FailureStage.AFTER_CANONICAL_REGISTER,
            ResistorMutationScope.FailureStage.AFTER_STRESS_REGISTER,
            ResistorMutationScope.FailureStage.AFTER_GRAPH_APPEND,
            ResistorMutationScope.FailureStage.AFTER_PRIMARY_BINDING,
            ResistorMutationScope.FailureStage.AFTER_AUXILIARY_BINDING,
            ResistorMutationScope.FailureStage.AFTER_ENDPOINT_RETARGET,
            ResistorMutationScope.FailureStage.AFTER_ATTACHMENT,
            ResistorMutationScope.FailureStage.AFTER_SLOT_MOUNT,
            ResistorMutationScope.FailureStage.AFTER_GRAPH_CONNECT,
            ResistorMutationScope.FailureStage.AFTER_GRAPH_RESTORE,
            ResistorMutationScope.FailureStage.AFTER_COMMIT
        };
    }

    private static PhysicalResistorPart requireOriginal(
            ReplaceableResistorBoardCapability capability) {
        PhysicalResistorPart original = capability.getSlot().getInstalledPart();
        require(original != null && original.isOriginal() && original.isInstalled(),
            "original resistor owner required: " +
            capability.getSlot().getComponentId());
        return original;
    }

    private static void power(CirSim sim, BoardPowerState state,
            GeneratedBoardInstance board, String label) {
        sim.setBoardPowerState(state);
        settle(sim, board, label);
        require(sim.getBoardPowerController().getState() == state &&
                (state == BoardPowerState.UNPOWERED ?
                    sim.getBoardPowerController().isElectricallyUnpowered() :
                    board.getExternalPowerBindings().areAllConnected()),
            "power transition did not settle: " + state);
    }

    private static void settle(CirSim sim, GeneratedBoardInstance board, String label) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, board, "Task48 mutation " + label);
        require(sim.getGeneratedBoardInstance() == board && sim.isGeneratedRuntimeSettled() &&
                !sim.activeMeasurementOverlay && !board.getPhysicalBoardRuntime().isMutationInProgress(),
            "runtime did not settle: " + label);
    }

    /** Read-only whole-owner observation used for exact transaction abort checks. */
    private static final class Observation {
        final GeneratedBoardInstance board;
        final GeneratedChallengeController challenge;
        final BoardModificationController modifications;
        final Vector<CircuitElm> active;
        final Vector<CircuitElm> canonical;
        final Vector<Object> identities = new Vector<Object>();
        final String state;

        Observation(CirSim sim, GeneratedBoardInstance board) {
            this.board = board;
            this.challenge = sim.getGeneratedChallengeController();
            this.modifications = sim.getBoardModificationController();
            this.active = new Vector<CircuitElm>(sim.elmList);
            this.canonical = new Vector<CircuitElm>(board.getSimulationElements());
            StringBuilder result = new StringBuilder();
            result.append(sim.getBoardPowerController().getState()).append('|')
                .append(sim.getBoardPowerController().isElectricallyUnpowered()).append('|')
                .append(board.getFaultBinding() == null ? "null" :
                    board.getFaultBinding().isApplied()).append('|');
            for (CircuitElm element : active)
                result.append("a").append(canonical.indexOf(element)).append(':')
                    .append(element.dump()).append(';');
            result.append("/canonical/");
            for (CircuitElm element : canonical)
                result.append(element.dump()).append(';');
            for (GeneratedComponentConnectionBinding binding : board.getConnectionBindings().getAll()) {
                result.append("/connection/").append(binding.getPadId()).append(':')
                    .append(modifications.isLeadConnected(binding.getComponentId(), binding.getPadId()));
                identities.add(binding);
                identities.add(binding.getBoardEndpoint());
                identities.add(binding.getComponentEndpoint());
                identities.add(binding.getConnectionElement());
            }
            for (String id : board.getBoard().getComponentIds()) {
                if (!board.getComponentBindings().hasComponentBinding(id)) continue;
                Vector<CircuitElm> primary = board.getComponentBindings().getElements(id);
                Vector<CircuitElm> auxiliary = board.getComponentBindings().getAuxiliaryElements(id);
                result.append("/component/").append(id).append(':').append(primary.size()).append(':')
                    .append(auxiliary.size());
                identities.addAll(primary);
                identities.addAll(auxiliary);
            }
            for (String id : board.getBoard().getPowerInputIds()) {
                ExternalPowerSimulationBinding binding = board.getExternalPowerBindings().getBinding(id);
                result.append("/power/").append(id).append(':').append(binding.isConnected());
                identities.add(binding);
                identities.addAll(binding.getBackingElements());
            }
            PhysicalBoardRuntime runtime = board.getPhysicalBoardRuntime();
            for (PhysicalBoardSlot slot : runtime.getSlots()) {
                PhysicalResistorPart part = slot.getInstalledPart() instanceof PhysicalResistorPart ?
                    (PhysicalResistorPart) slot.getInstalledPart() : null;
                result.append("/slot/").append(slot.getComponentId()).append(':')
                    .append(part == null ? "empty" : part.getId());
                identities.add(slot);
                identities.add(slot.getMountState());
                if (part != null) identities.add(part);
            }
            for (PhysicalPart<?> value : runtime.getPhysicalParts()) {
                result.append("/part/").append(value.getId()).append(':')
                    .append(value.isInstalled()).append(':').append(value.isFaulted());
                identities.add(value);
                identities.add(value.getMountState());
                identities.add(value.getBoardSlot());
                if (value instanceof PhysicalResistorPart) {
                    PhysicalResistorPart resistor = (PhysicalResistorPart) value;
                    result.append(':').append(resistor.getSecondaryOpenPath().isOpen());
                    identities.add(resistor.getElement());
                    identities.add(resistor.getSecondaryOpenPath());
                    identities.add(resistor.getSecondaryOpenPath().getSimulationElement());
                    for (PhysicalPartTerminal terminal : resistor.getTerminals()) {
                        identities.add(terminal);
                        identities.add(terminal.getEndpoint());
                    }
                }
            }
            appendResistorState(result, identities, board,
                ReplaceableResistorBoardCapability.find(runtime, DRIVER_COMPONENT));
            appendResistorState(result, identities, board,
                ReplaceableResistorBoardCapability.find(runtime, LOAD_COMPONENT));
            state = result.toString();
        }

        private static void appendResistorState(StringBuilder result, Vector<Object> identities,
                GeneratedBoardInstance board, ReplaceableResistorBoardCapability capability) {
            if (capability == null) return;
            result.append("/resistor/").append(capability.getSlot().getComponentId()).append(':')
                .append(board.getPhysicalBoardRuntime().getNextPartSerial(
                    capability.getSlot().getComponentId() + "_CATALOG_PART"));
            ResistorStressDamageSystem stress = capability.getStressDamageSystem();
            identities.add(stress);
            for (PhysicalResistorPart part : capability.getInventory().getAll()) {
                if (!stress.ownsState(part)) {
                    result.append("/unregistered/").append(part.getId());
                    continue;
                }
                ResistorStressState value = stress.getState(part.getId());
                result.append('/').append(part.getId()).append(':')
                    .append(value.getActualPower()).append(':').append(value.getStressRatio())
                    .append(':').append(value.getAccumulatedDamage()).append(':')
                    .append(value.getServiceTime()).append(':').append(value.isFailed());
                identities.add(value);
            }
        }

        boolean sameState(Observation other) {
            return other != null && state.equals(other.state) && sameReferences(active, other.active) &&
                sameReferences(canonical, other.canonical) && sameReferences(identities, other.identities);
        }

        void assertSame(CirSim sim, GeneratedBoardInstance expected, String label) {
            Observation after = new Observation(sim, expected);
            require(board == after.board && challenge == after.challenge &&
                    modifications == after.modifications && sameState(after),
                "abort changed owner state: " + label);
        }
    }

    private static boolean sameReferences(Vector<?> first, Vector<?> second) {
        if (first == null || second == null || first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++)
            if (first.get(index) != second.get(index)) return false;
        return true;
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException("Task48 mutation: " + message);
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        throw new IllegalStateException("Task48 mutation verification failed", failure);
    }
}
