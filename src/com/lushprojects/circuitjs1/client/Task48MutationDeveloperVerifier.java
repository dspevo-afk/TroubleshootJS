package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Focused physical mutation proof used by the maintained compiled gate.
 * Serviceable owners are discovered from the resolved provider plan, so the
 * proof covers both repeated channels and either low-side role.
 */
final class Task48MutationDeveloperVerifier {
    private static final double[] ALTERNATIVE_FROM_OHMS = {
        270.0, 330.0, 1000.0, 2700.0
    };
    private static final double[] ALTERNATIVE_TO_OHMS = {
        330.0, 270.0, 1500.0, 3300.0
    };
    private static int assertions;

    static final class Receipt {
        final String faultRepairsJson;
        final String json;

        Receipt(String faultRepairsJson, String json) {
            this.faultRepairsJson = faultRepairsJson;
            this.json = json;
        }
    }

    private static final class OwnerProof {
        final String json;
        final int liftChecks;
        final int reconnectChecks;
        final int removeChecks;
        final int restoreChecks;

        OwnerProof(String json, int liftChecks, int reconnectChecks,
                int removeChecks, int restoreChecks) {
            this.json = json;
            this.liftChecks = liftChecks;
            this.reconnectChecks = reconnectChecks;
            this.removeChecks = removeChecks;
            this.restoreChecks = restoreChecks;
        }
    }

    private Task48MutationDeveloperVerifier() { }

    static String verify(CirSim sim) {
        return verifyDetailed(sim).json;
    }

    static Receipt verifyDetailed(CirSim sim) {
        assertions = 0;
        require(sim != null && sim.getGeneratedBoardInstance() != null,
            "generated board is required");
        final GeneratedBoardInstance originalBoard = sim.getGeneratedBoardInstance();
        require(originalBoard.getBehaviorContract() instanceof ControlledIndicatorDeviceBehavior,
            "controlled provider is required");
        final ControlledIndicatorDeviceBehavior originalBehavior =
            (ControlledIndicatorDeviceBehavior) originalBoard.getBehaviorContract();
        final BoundedAssemblyPlan originalPlan = originalBehavior.getAssemblyPlan();
        require(sim.getBoardModificationController() != null &&
                sim.getBoardModificationController().isFullyRestored(),
            "mutation proof requires a fully restored board");

        // Capture a settled owner, including completed generated verification.
        settle(sim, originalBoard, "original owner before mutation proof");
        final BoardPowerState priorPower = sim.getBoardPowerController().getState();
        final Task41SimulationSnapshot originalSnapshot =
            Task41SimulationSnapshot.capture(sim);
        Throwable primary = null;
        boolean internalProof = false;
        try {
            originalSnapshot.beginProof(sim);
            /* The explicit owner route is the same guarded diagnostic route
             * used by Task 41.  It permits a normal candidate with a selected
             * physical owner to be installed for synchronous proof work while
             * preventing live admission from recursively re-entering here. */
            GeneratedDiagnosticSolvabilityAdmission.beginInternalProof();
            internalProof = true;
            StringBuilder repairs = new StringBuilder();
            int liftChecks = 0;
            int reconnectChecks = 0;
            int removeChecks = 0;
            int restoreChecks = 0;
            for (String componentId : originalPlan.getDecisionOwners().values()) {
                OwnerProof owner = verifyFreshFaultOwner(sim, originalBoard,
                    originalPlan, originalSnapshot, componentId);
                if (repairs.length() != 0) repairs.append(',');
                repairs.append(owner.json);
                liftChecks += owner.liftChecks;
                reconnectChecks += owner.reconnectChecks;
                removeChecks += owner.removeChecks;
                restoreChecks += owner.restoreChecks;
            }
            originalSnapshot.restore(sim);
            originalSnapshot.assertRestored(sim);
            require(sim.getGeneratedBoardInstance() == originalBoard &&
                sim.getBoardPowerController().getState() == priorPower,
                "fresh mutation proof did not restore the original owner");
            BoardModificationController originalModifications =
                sim.getBoardModificationController();
            require(originalModifications != null && originalModifications.isFullyRestored(),
                "restored mutation proof left the original board modified");
            if (priorPower == BoardPowerState.POWERED)
                require(originalBehavior.isFunctionallyRepaired(originalBoard,
                    originalModifications, priorPower, sim.activeMeasurementOverlay),
                    "restored mutation proof did not retain healthy solver behavior");
            else
                originalBehavior.verifyHealthy(originalBoard, priorPower);
            // Function observation can queue generated verification after solving.
            settle(sim, originalBoard, "original owner after behavior observation");
            GeneratedRuntimeInvariant.verify(sim, originalBoard, originalModifications,
                sim.elmList);
            String faultRepairs = "[" + repairs + "]";
            String json = "{\"status\":\"PASS\",\"owners\":" +
                originalPlan.getDecisionOwners().size() + ",\"freshFaultBoards\":true" +
                ",\"alternativeMap\":\"270->330,330->270,1000->1500,2700->3300\"" +
                ",\"faultRepairs\":" + faultRepairs +
                ",\"liftChecks\":" + liftChecks +
                ",\"reconnectChecks\":" + reconnectChecks +
                ",\"removeChecks\":" + removeChecks +
                ",\"restoreChecks\":" + restoreChecks +
                ",\"assertions\":" + assertions + "}";
            return new Receipt(faultRepairs, json);
        } catch (Throwable failure) {
            primary = failure;
            rethrow(failure);
            return null;
        } finally {
            Throwable cleanup = null;
            try {
                if (internalProof) {
                    GeneratedDiagnosticSolvabilityAdmission.endInternalProof();
                    internalProof = false;
                }
                originalSnapshot.restore(sim);
                originalSnapshot.assertRestored(sim);
                require(sim.getGeneratedBoardInstance() == originalBoard &&
                    sim.getBoardPowerController().getState() == priorPower,
                    "mutation cleanup lost the original owner");
                BoardModificationController restoredModifications =
                    sim.getBoardModificationController();
                require(restoredModifications != null && restoredModifications.isFullyRestored(),
                    "mutation cleanup left a disconnected lead");
                GeneratedRuntimeInvariant.verify(sim, originalBoard, restoredModifications,
                    sim.elmList);
            } catch (Throwable failure) {
                cleanup = failure;
            }
            if (internalProof) {
                try {
                    GeneratedDiagnosticSolvabilityAdmission.endInternalProof();
                    internalProof = false;
                } catch (Throwable failure) {
                    if (cleanup == null) cleanup = failure;
                    else cleanup.addSuppressed(failure);
                }
            }
            if (cleanup != null) {
                if (primary != null) primary.addSuppressed(cleanup);
                else rethrow(cleanup);
            }
        }
    }

    private static OwnerProof verifyFreshFaultOwner(CirSim sim,
            GeneratedBoardInstance originalBoard, BoundedAssemblyPlan originalPlan,
            Task41SimulationSnapshot originalSnapshot, String componentId) {
        GeneratedBoardInstance faultBoard = null;
        Throwable primary = null;
        try {
            require(GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning(),
                "fresh owner proof escaped the internal diagnostic guard");
            BoundedGeneratedBoardAssembler.Result assembled =
                BoundedGeneratedBoardAssembler.assembleForDiagnosticProof(
                    originalPlan.getRequest(), componentId);
            faultBoard = assembled.getInstance();
            require(faultBoard != null && faultBoard != originalBoard,
                "owner proof did not allocate a fresh board: " + componentId);
            FreshGeneratedRuntimeInstallation.requireDisjoint(originalBoard, faultBoard);
            require(faultBoard.getBehaviorContract() instanceof ControlledIndicatorDeviceBehavior,
                "fresh owner has no controlled behavior: " + componentId);
            ControlledIndicatorDeviceBehavior behavior =
                (ControlledIndicatorDeviceBehavior) faultBoard.getBehaviorContract();
            BoundedAssemblyPlan plan = behavior.getAssemblyPlan();
            require(plan.getRequest() == originalPlan.getRequest() &&
                    plan.getDecisionOwners().equals(originalPlan.getDecisionOwners()),
                "fresh owner changed the resolved assembly plan: " + componentId);
            require(plan.getDecisionOwners().containsValue(componentId),
                "fresh owner is not a declared decision owner: " + componentId);

            sim.installGeneratedChallengeForDeveloperVerification(faultBoard);
            require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
                "fresh owner proof attached a player workbench: " + componentId);
            settle(sim, faultBoard, "fresh fault install " + componentId);
            GeneratedDiagnosticSolvabilityAdmission.validate(sim, faultBoard);
            require(sim.getGeneratedBoardInstance() == faultBoard &&
                    sim.getGeneratedChallengeController() != null &&
                    sim.getGeneratedChallengeController().isReady(),
                "fresh owner proof did not publish a ready challenge: " + componentId);
            final Vector<Runnable> retestCompletions = new Vector<Runnable>();
            sim.getGeneratedChallengeController().setRetestCompletionDispatchForDeveloperVerification(
                new GeneratedChallengeController.RetestCompletionDispatch() {
                    public void dispatch(Runnable completion) {
                        retestCompletions.add(completion);
                    }
                });

            GeneratedFaultBinding faultBinding = faultBoard.getFaultBinding();
            GeneratedFaultLocus faultLocus = faultBoard.getFaultLocus();
            require(faultBinding != null && faultBinding.isApplied() &&
                    faultBinding.getFault().getType() == GeneratedFaultType.RESISTOR_OPEN &&
                    faultLocus != null && componentId.equals(faultLocus.getComponentId()),
                "fresh owner did not start with its applied OPEN fault: " + componentId);
            BoardModificationController modifications = sim.getBoardModificationController();
            require(modifications != null && modifications.isFullyRestored(),
                "fresh owner starts with a physical mutation: " + componentId);
            behavior.verifyFaulted(faultBoard, modifications, BoardPowerState.POWERED);
            GeneratedCustomerRetestResult unrepaired = customerRetest(sim, faultBoard);
            require(unrepaired != null && !unrepaired.isPassed(),
                "fresh OPEN fault passed customer retest: " + componentId);
            settle(sim, faultBoard, "fresh fault retest " + componentId);

            HashMap<String, PhysicalPart<?>> baselines = captureAllOwners(
                faultBoard, plan);
            PhysicalPart<?> baselinePart = baselines.get(componentId);
            require(baselinePart instanceof PhysicalResistorPart &&
                    baselinePart.isInstalled() && baselinePart.isFaulted() &&
                    ((PhysicalResistorPart) baselinePart).ownsGeneratedFault(faultBinding),
                "fresh OPEN fault is not owned by the installed physical part: " + componentId);
            ReplaceableResistorBoardCapability capability =
                ReplaceableResistorBoardCapability.find(
                    faultBoard.getPhysicalBoardRuntime(), componentId);
            require(capability != null && capability.getController() != null &&
                    !capability.getSlot().isEmpty() &&
                    capability.getSlot().getInstalledPart() == baselinePart,
                "fresh fault owner has no replaceable resistor capability: " + componentId);
            PhysicalResistorPart baseline = (PhysicalResistorPart) baselinePart;
            Map<String, PhysicalPart<?>> peers = captureOtherOwners(faultBoard, plan, componentId);
            String padId = capability.getSlot().getPhysicalSlot().getPadIds().firstElement();

            power(sim, BoardPowerState.UNPOWERED, faultBoard,
                "fault owner mutation entry " + componentId);
            require(modifications.liftLead(componentId, padId),
                "fault owner lift was rejected: " + componentId);
            settle(sim, faultBoard, "fault owner lift " + componentId);
            require(!modifications.isLeadConnected(componentId, padId) &&
                    modifications.getComponentState(componentId) ==
                        ComponentPhysicalState.LEAD_LIFTED,
                "fault owner lift state was not observed: " + componentId);
            int liftChecks = 1;
            require(otherOwnersUnchanged(faultBoard, plan, componentId, peers),
                "lifting one fault owner changed a peer owner: " + componentId);
            require(modifications.reconnectLead(componentId, padId),
                "fault owner reconnect was rejected: " + componentId);
            settle(sim, faultBoard, "fault owner reconnect " + componentId);
            require(modifications.isLeadConnected(componentId, padId) &&
                    modifications.getComponentState(componentId) ==
                        ComponentPhysicalState.INSTALLED,
                "fault owner reconnect state was not observed: " + componentId);
            int reconnectChecks = 1;
            require(otherOwnersUnchanged(faultBoard, plan, componentId, peers),
                "reconnecting one fault owner changed a peer owner: " + componentId);

            ResistorCatalogEntry wrong = chooseWrongEntry(capability, baseline);
            replaceWithCatalog(sim, faultBoard, capability, wrong.getId(),
                "wrong highest-catalog " + componentId);
            require(capability.getSlot().getInstalledPart() != baseline &&
                    !capability.getSlot().getInstalledPart().isFaulted() &&
                    wrong.getNameplate().getNominalResistanceOhms() !=
                        baseline.getSpecification().getNominalResistanceOhms(),
                "wrong replacement did not create a distinct healthy catalog part: " + componentId);
            power(sim, BoardPowerState.POWERED, faultBoard,
                "wrong replacement powered " + componentId);
            boolean wrongHealthy = behavior.isFunctionallyRepaired(faultBoard,
                modifications, BoardPowerState.POWERED, sim.activeMeasurementOverlay);
            GeneratedCustomerRetestResult wrongRetest = customerRetest(sim, faultBoard);
            require(!wrongHealthy && wrongRetest != null && !wrongRetest.isPassed(),
                "highest-catalog wrong replacement was accepted: " + componentId);
            settle(sim, faultBoard, "wrong replacement retest " + componentId);
            power(sim, BoardPowerState.UNPOWERED, faultBoard,
                "wrong replacement cleanup " + componentId);
            require(capability.getController().removeInstalledPart(),
                "wrong replacement removal failed: " + componentId);
            settle(sim, faultBoard, "wrong replacement removed " + componentId);
            int removeChecks = 1;
            require(otherOwnersUnchanged(faultBoard, plan, componentId, peers),
                "wrong replacement changed a peer owner: " + componentId);

            ResistorCatalogEntry correct = catalogEntryForValue(capability,
                baseline.getSpecification().getNominalResistanceOhms());
            installCatalog(sim, faultBoard, capability, correct.getId(),
                "correct replacement " + componentId);
            power(sim, BoardPowerState.POWERED, faultBoard,
                "correct replacement powered " + componentId);
            boolean correctHealthy = behavior.isFunctionallyRepaired(faultBoard,
                modifications, BoardPowerState.POWERED, sim.activeMeasurementOverlay);
            require(correctHealthy,
                "correct catalog replacement did not clear the actual OPEN fault: " + componentId);
            GeneratedCustomerRetestResult correctRetest = customerRetest(sim, faultBoard);
            require(correctRetest != null && correctRetest.isPassed(),
                "correct catalog replacement failed customer retest: " + componentId);
            settle(sim, faultBoard, "correct replacement retest " + componentId);
            power(sim, BoardPowerState.UNPOWERED, faultBoard,
                "correct replacement cleanup " + componentId);
            require(capability.getController().removeInstalledPart(),
                "correct replacement removal failed: " + componentId);
            settle(sim, faultBoard, "correct replacement removed " + componentId);
            require(otherOwnersUnchanged(faultBoard, plan, componentId, peers),
                "correct replacement changed a peer owner: " + componentId);

            restoreBaseline(sim, faultBoard, capability, baseline,
                "fault baseline before alternative " + componentId);
            require(capability.getSlot().getInstalledPart() == baseline &&
                    baseline.isInstalled() && baseline.isFaulted() &&
                    baseline.ownsGeneratedFault(faultBinding) && faultBinding.isApplied(),
                "fault baseline was not reinstalled with its generated fault: " + componentId);
            power(sim, BoardPowerState.POWERED, faultBoard,
                "fault baseline powered before alternative " + componentId);
            behavior.verifyFaulted(faultBoard, modifications, BoardPowerState.POWERED);
            require(!behavior.isFunctionallyRepaired(faultBoard, modifications,
                    BoardPowerState.POWERED, sim.activeMeasurementOverlay),
                "reinstalled faulty baseline was treated as repaired: " + componentId);
            GeneratedCustomerRetestResult baselineRetest = customerRetest(sim, faultBoard);
            require(baselineRetest != null && !baselineRetest.isPassed(),
                "reinstalled faulty baseline passed customer retest: " + componentId);
            settle(sim, faultBoard, "fault baseline retest before alternative " + componentId);
            power(sim, BoardPowerState.UNPOWERED, faultBoard,
                "fault baseline cleanup before alternative " + componentId);
            require(capability.getController().removeInstalledPart(),
                "fault baseline removal before alternative failed: " + componentId);
            settle(sim, faultBoard, "fault baseline removed before alternative " + componentId);
            removeChecks++;
            require(otherOwnersUnchanged(faultBoard, plan, componentId, peers),
                "fault baseline replacement changed a peer owner: " + componentId);

            ResistorCatalogEntry alternative = alternativeEntry(capability, baseline);
            installCatalog(sim, faultBoard, capability, alternative.getId(),
                "frozen alternative " + componentId);
            power(sim, BoardPowerState.POWERED, faultBoard,
                "frozen alternative powered " + componentId);
            boolean alternativeHealthy = behavior.isFunctionallyRepaired(faultBoard,
                modifications, BoardPowerState.POWERED, sim.activeMeasurementOverlay);
            require(alternativeHealthy,
                "frozen alternative did not clear the actual OPEN fault: " + componentId);
            GeneratedCustomerRetestResult alternativeRetest = customerRetest(sim, faultBoard);
            require(alternativeRetest != null && alternativeRetest.isPassed(),
                "frozen alternative failed customer retest: " + componentId);
            settle(sim, faultBoard, "frozen alternative retest " + componentId);
            power(sim, BoardPowerState.UNPOWERED, faultBoard,
                "frozen alternative cleanup " + componentId);
            require(capability.getController().removeInstalledPart(),
                "frozen alternative removal failed: " + componentId);
            settle(sim, faultBoard, "frozen alternative removed " + componentId);
            removeChecks++;
            require(otherOwnersUnchanged(faultBoard, plan, componentId, peers),
                "frozen alternative changed a peer owner: " + componentId);

            restoreAllBaselines(sim, faultBoard, plan, modifications, baselines);
            require(capability.getSlot().getInstalledPart() == baseline &&
                    baseline.isInstalled() && baseline.isFaulted() &&
                    faultBinding.isApplied() && modifications.isFullyRestored() &&
                    otherOwnersUnchanged(faultBoard, plan, componentId, peers),
                "baseline cleanup did not restore the actual fault owner: " + componentId);
            int restoreChecks = 1;
            require(retestCompletions.size() == 5,
                "mutation proof did not execute all five customer retests: " + componentId);
            String json = "{\"owner\":" + q(componentId) +
                ",\"wrongRejected\":true,\"correctPassed\":true" +
                ",\"alternativePassed\":true,\"otherOwnersUnchanged\":true" +
                ",\"componentId\":" + q(componentId) +
                ",\"faultId\":" + q(faultBinding.getFault().getId()) +
                ",\"faultType\":\"RESISTOR_OPEN\",\"faultApplied\":true" +
                ",\"baselineCatalogId\":" + q(correct.getId()) +
                ",\"baselineOhms\":" + correct.getNameplate().getNominalResistanceOhms() +
                ",\"wrongCatalogId\":" + q(wrong.getId()) +
                ",\"wrongOhms\":" + wrong.getNameplate().getNominalResistanceOhms() +
                ",\"wrongFunctionallyRepaired\":false,\"wrongRetestPassed\":false" +
                ",\"correctCatalogId\":" + q(correct.getId()) +
                ",\"correctOhms\":" + correct.getNameplate().getNominalResistanceOhms() +
                ",\"correctFunctionallyRepaired\":true,\"correctRetestPassed\":true" +
                ",\"alternativeCatalogId\":" + q(alternative.getId()) +
                ",\"alternativeOhms\":" + alternative.getNameplate().getNominalResistanceOhms() +
                ",\"alternativeFunctionallyRepaired\":true,\"alternativeRetestPassed\":true" +
                ",\"customerRetestsExecuted\":" + retestCompletions.size() +
                ",\"liftChecks\":" + liftChecks + ",\"reconnectChecks\":" + reconnectChecks +
                ",\"removeChecks\":" + removeChecks + ",\"restoreChecks\":" + restoreChecks +
                ",\"peerOwnersUnchanged\":true,\"baselineRestored\":true}";
            return new OwnerProof(json, liftChecks, reconnectChecks, removeChecks,
                restoreChecks);
        } catch (Throwable failure) {
            primary = failure;
            rethrow(failure);
            return null;
        } finally {
            Throwable cleanup = null;
            try {
                GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
                if (challenge != null)
                    challenge.setRetestCompletionDispatchForDeveloperVerification(null);
                originalSnapshot.restore(sim);
                originalSnapshot.assertRestored(sim);
                disposeDetachedBoard(sim, faultBoard, originalBoard);
            } catch (Throwable failure) {
                cleanup = failure;
            }
            if (cleanup != null) {
                if (primary != null) primary.addSuppressed(cleanup);
                else rethrow(cleanup);
            }
        }
    }

    private static void disposeDetachedBoard(CirSim sim, GeneratedBoardInstance candidate,
            GeneratedBoardInstance protectedOwner) {
        if (candidate == null) return;
        require(candidate != protectedOwner && candidate != sim.getGeneratedBoardInstance(),
            "cannot dispose active or protected fresh owner");
        candidate.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : candidate.getSimulationElements()) element.delete();
    }

    private static HashMap<String, PhysicalPart<?>> captureAllOwners(
            GeneratedBoardInstance board, BoundedAssemblyPlan plan) {
        HashMap<String, PhysicalPart<?>> result = new HashMap<String, PhysicalPart<?>>();
        for (String componentId : plan.getDecisionOwners().values()) {
            ReplaceableResistorBoardCapability capability =
                ReplaceableResistorBoardCapability.find(
                    board.getPhysicalBoardRuntime(), componentId);
            require(capability != null && !capability.getSlot().isEmpty() &&
                    capability.getSlot().getInstalledPart() != null,
                "missing installed owner " + componentId);
            result.put(componentId, capability.getSlot().getInstalledPart());
        }
        return result;
    }

    private static Map<String, PhysicalPart<?>> captureOtherOwners(
            GeneratedBoardInstance board, BoundedAssemblyPlan plan, String current) {
        HashMap<String, PhysicalPart<?>> result = new HashMap<String, PhysicalPart<?>>();
        for (String componentId : plan.getDecisionOwners().values()) {
            if (current.equals(componentId)) continue;
            ReplaceableResistorBoardCapability capability =
                ReplaceableResistorBoardCapability.find(
                    board.getPhysicalBoardRuntime(), componentId);
            require(capability != null && !capability.getSlot().isEmpty(),
                "missing peer owner " + componentId);
            result.put(componentId, capability.getSlot().getInstalledPart());
        }
        return result;
    }

    private static boolean otherOwnersUnchanged(GeneratedBoardInstance board,
            BoundedAssemblyPlan plan, String current,
            Map<String, PhysicalPart<?>> before) {
        for (String componentId : plan.getDecisionOwners().values()) {
            if (current.equals(componentId)) continue;
            ReplaceableResistorBoardCapability capability =
                ReplaceableResistorBoardCapability.find(
                    board.getPhysicalBoardRuntime(), componentId);
            if (capability == null || capability.getSlot().isEmpty() ||
                    capability.getSlot().getInstalledPart() != before.get(componentId))
                return false;
        }
        return true;
    }

    private static ResistorCatalogEntry chooseWrongEntry(
            ReplaceableResistorBoardCapability capability, PhysicalPart<?> baseline) {
        Vector<ResistorCatalogEntry> entries = capability.getCatalog().getEntries();
        double nominal = ((PhysicalResistorPart) baseline).getSpecification()
            .getNominalResistanceOhms();
        ResistorCatalogEntry result = null;
        double highest = Double.NEGATIVE_INFINITY;
        for (ResistorCatalogEntry entry : entries) {
            double value = entry.getNameplate().getNominalResistanceOhms();
            if (value != nominal && value > highest) {
                highest = value;
                result = entry;
            }
        }
        if (result == null)
            throw new IllegalStateException("Resistor catalog has no wrong replacement");
        return result;
    }

    private static ResistorCatalogEntry catalogEntryForValue(
            ReplaceableResistorBoardCapability capability, double nominal) {
        Vector<ResistorCatalogEntry> entries = capability.getCatalog().getEntries();
        for (int index = entries.size() - 1; index >= 0; index--) {
            ResistorCatalogEntry entry = entries.get(index);
            if (entry.getNameplate().getNominalResistanceOhms() == nominal)
                return entry;
        }
        throw new IllegalStateException("Resistor catalog has no nominal replacement: " + nominal);
    }

    private static ResistorCatalogEntry alternativeEntry(
            ReplaceableResistorBoardCapability capability, PhysicalResistorPart baseline) {
        double nominal = ((PhysicalResistorPart) baseline).getSpecification()
            .getNominalResistanceOhms();
        double target = Double.NaN;
        for (int index = 0; index < ALTERNATIVE_FROM_OHMS.length; index++)
            if (ALTERNATIVE_FROM_OHMS[index] == nominal) {
                target = ALTERNATIVE_TO_OHMS[index];
                break;
            }
        require(!Double.isNaN(target),
            "no frozen alternative mapping for nominal resistance " + nominal);
        ResistorCatalogEntry result = catalogEntryForValue(capability, target);
        require(result.getNameplate().getNominalResistanceOhms() == target &&
                result.getNameplate().getNominalResistanceOhms() != nominal,
            "frozen alternative catalog value is not the requested value: " + target);
        return result;
    }

    private static void replaceWithCatalog(CirSim sim, GeneratedBoardInstance board,
            ReplaceableResistorBoardCapability capability, String catalogId, String label) {
        removeInstalled(sim, board, capability, label + " remove");
        installCatalog(sim, board, capability, catalogId, label + " install");
    }

    private static void removeInstalled(CirSim sim, GeneratedBoardInstance board,
            ReplaceableResistorBoardCapability capability, String label) {
        require(capability.getController() != null &&
            capability.getController().removeInstalledPart(),
            "could not remove resistor for " + label);
        settle(sim, board, label + " remove settle");
    }

    private static void installCatalog(CirSim sim, GeneratedBoardInstance board,
            ReplaceableResistorBoardCapability capability, String catalogId, String label) {
        require(capability.getController() != null &&
            capability.getController().installNewFromCatalog(catalogId),
            "could not install resistor " + catalogId + " for " + label);
        settle(sim, board, label + " install settle");
    }

    private static void restoreBaseline(CirSim sim, GeneratedBoardInstance board,
            ReplaceableResistorBoardCapability capability, PhysicalPart<?> baseline,
            String label) {
        if (capability.getSlot().isEmpty()) {
            require(capability.getController().install(baseline.getId()),
                "could not reinstall baseline resistor for " + label);
            settle(sim, board, label + " install settle");
        } else if (capability.getSlot().getInstalledPart() != baseline) {
            removeInstalled(sim, board, capability, label + " replace");
            require(capability.getController().install(baseline.getId()),
                "could not restore baseline resistor for " + label);
            settle(sim, board, label + " baseline settle");
        }
    }

    private static void restoreAllBaselines(CirSim sim, GeneratedBoardInstance board,
            BoundedAssemblyPlan plan, BoardModificationController modifications,
            Map<String, PhysicalPart<?>> baselines) {
        if (sim.getBoardPowerController().getState() != BoardPowerState.UNPOWERED)
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        settle(sim, board, "cleanup unpowered");
        for (String componentId : plan.getDecisionOwners().values()) {
            PhysicalPart<?> baseline = baselines.get(componentId);
            if (baseline == null) continue;
            ReplaceableResistorBoardCapability capability =
                ReplaceableResistorBoardCapability.find(
                    board.getPhysicalBoardRuntime(), componentId);
            if (capability == null) throw new IllegalStateException(
                "cleanup lost resistor capability " + componentId);
            restoreBaseline(sim, board, capability, baseline, "cleanup " + componentId);
            if (modifications.getComponentState(componentId) !=
                    ComponentPhysicalState.INSTALLED) {
                require(modifications.restoreComponent(componentId),
                    "cleanup could not reconnect " + componentId);
                settle(sim, board, "cleanup reconnect " + componentId);
            }
        }
    }

    private static GeneratedCustomerRetestResult customerRetest(CirSim sim, GeneratedBoardInstance board) {
        settle(sim, board, "before customer retest");
        GeneratedCustomerRetestResult result = sim.performCustomerRetest();
        require(result != null, "customer retest operation was not executed");
        settle(sim, board, "customer retest");
        return result;
    }

    private static void power(CirSim sim, BoardPowerState state,
            GeneratedBoardInstance board, String label) {
        settle(sim, board, "before power-" + state + " " + label);
        sim.setBoardPowerState(state);
        settle(sim, board, "power-" + state + " " + label);
        require(sim.getBoardPowerController().getState() == state &&
            (state == BoardPowerState.UNPOWERED ?
                sim.getBoardPowerController().isElectricallyUnpowered() :
                board.getExternalPowerBindings().areAllConnected()),
            "power transition did not settle: " + state);
    }

    private static void settle(CirSim sim, GeneratedBoardInstance board,
            String label) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, board,
            "Task48 mutation " + label);
        require(sim.getGeneratedBoardInstance() == board &&
            sim.isGeneratedRuntimeSettled() && !sim.activeMeasurementOverlay &&
            !board.getPhysicalBoardRuntime().isMutationInProgress(),
            "runtime did not settle: " + label);
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException("Task48 mutation: " + message);
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        throw new IllegalStateException("Task48 mutation failure", failure);
    }

    private static String q(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
