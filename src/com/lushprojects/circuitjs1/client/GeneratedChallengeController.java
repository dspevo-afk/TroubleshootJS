package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class GeneratedChallengeController {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final GeneratedChallengeDefinition definition;
    private final GeneratedFaultController faults;
    private final GeneratedChallengeLifecycleEvidence lifecycleEvidence =
        new GeneratedChallengeLifecycleEvidence();
    private final boolean finishJobRequired;
    private boolean developerVerificationScope;
    private GeneratedChallengeState state = GeneratedChallengeState.PREPARING_HEALTHY;
    private GeneratedScenario<GeneratedObservedBehavior> scenario;
    private GeneratedCustomerRetestResult customerRetestResult;

    GeneratedChallengeController(CirSim sim, GeneratedBoardInstance instance) {
        this(sim, instance, false);
    }

    GeneratedChallengeController(CirSim sim, GeneratedBoardInstance instance,
            boolean finishJobRequired) {
        if (instance.getChallengeDefinition() == null)
            throw new IllegalArgumentException("Generated challenge requires a definition");
        this.sim = sim;
        this.instance = instance;
        this.finishJobRequired = finishJobRequired;
        definition = instance.getChallengeDefinition();
        validateDefinition();
        faults = new GeneratedFaultController(sim, instance, definition.getFaultBinding());
    }

    void begin() {
        lifecycleEvidence.healthyGenerationInstalled = true;
        sim.requestGeneratedBoardVerification();
    }

    void afterGeneratedVerification() {
        if (state == GeneratedChallengeState.PREPARING_HEALTHY) {
            if (instance.getTemporalBehavior() != null)
                instance.getTemporalBehavior().prepareHealthyProfile(sim, instance);
            lifecycleEvidence.healthyGraphAnalyzedAfterTimeAdvance = true;
            lifecycleEvidence.healthyFamilyValidated = true;
            state = GeneratedChallengeState.PREPARING_FAULTED;
            faults.apply();
            if (instance.getTemporalBehavior() != null)
                instance.getTemporalBehavior().prepareFaultedProfile(sim, instance);
            // A temporal family has already advanced its real solver profile.
            // Complete the second (faulted) validation pass immediately so its
            // deterministic profile does not depend on a later paint tick.
            if (instance.getTemporalBehavior() != null || sim.isQuickPlayMode())
                sim.updateCircuit();
            lifecycleEvidence.selectedFaultApplied = faults.isApplied();
            return;
        }
        if (state == GeneratedChallengeState.PREPARING_FAULTED) {
            lifecycleEvidence.faultedGraphAnalyzedAfterTimeAdvance = true;
            if (instance.getTemporalBehavior() != null)
                instance.getTemporalBehavior().verifyFaultedProfile(sim, instance,
                    sim.getBoardModificationController(), sim.getBoardPowerController().getState());
            else
                definition.getBehaviorContract().verifyFaulted(instance,
                    sim.getBoardModificationController(), sim.getBoardPowerController().getState());
            lifecycleEvidence.selectedFaultValidated = true;
            scenario = definition.getScenarioCatalog().select(definition.getSelectionSeed(), instance,
                sim.getBoardModificationController(), sim.getBoardPowerController().getState());
            scenario.present(sim, instance);
            lifecycleEvidence.scenarioCompatibilityValidated = true;
            state = GeneratedChallengeState.READY;
            lifecycleEvidence.readyAfterValidation = true;
            sim.refreshBoardModificationControls();
            sim.repaint();
        }
    }

    boolean isHealthyValidationExpected() {
        return state == GeneratedChallengeState.PREPARING_HEALTHY;
    }

    boolean isReady() { return state == GeneratedChallengeState.READY ||
        state == GeneratedChallengeState.COMPLETED; }
    /**
     * READY remains the only state in which player board interaction may
     * change power, instruments, selection, or physical topology.  isReady()
     * deliberately also includes COMPLETED for latched semantic operations.
     */
    boolean isPhysicalMutationAllowed() { return state == GeneratedChallengeState.READY; }
    boolean isCompleted() { return state == GeneratedChallengeState.COMPLETED; }
    GeneratedChallengeState getState() { return state; }
    GeneratedFaultController getFaultController() { return faults; }
    GeneratedChallengeDefinition getDefinition() { return definition; }
    GeneratedChallengeLifecycleEvidence getLifecycleEvidence() { return lifecycleEvidence; }
    GeneratedScenario<GeneratedObservedBehavior> getScenario() { return scenario; }
    GeneratedCustomerRetestProfile getCustomerRetestProfile() {
        return instance.getCustomerRetestProfile();
    }
    GeneratedCustomerRetestResult getCustomerRetestResult() { return customerRetestResult; }
    GeneratedRepairStatus getLiveRepairStatus() { return getRepairStatus(); }

    void invalidateCustomerRetest() {
        if (state == GeneratedChallengeState.READY)
            customerRetestResult = null;
    }
    String getComplaintText() {
        if (scenario == null)
            return "Preparing challenge...";
        if (!isCompleted())
            return scenario.getComplaintText();
        return definition.getCompletionText();
    }

    void verifyReadyState() {
        // Completion is terminal.  Re-running a temporal functional profile
        // after a player has already repaired the board would silently reset
        // its live capacitor state on every ordinary solver frame.
        if (state != GeneratedChallengeState.READY || developerVerificationScope)
            return;
        if (!faults.isApplied())
            throw new IllegalStateException("Selected challenge fault was cleared outside developer scope");
        String targetComponentId = definition.getFault().getTargetComponentId();
        Vector<GeneratedComponentConnectionBinding> targetConnections =
            instance.getConnectionBindings().getForComponentOrEmpty(targetComponentId);
        boolean targetInstalled = targetConnections.isEmpty() ||
            sim.getBoardModificationController().isComponentInstalled(targetComponentId);
        if (instance.getFamilyState().isFaultedTargetInstalled(instance, targetComponentId) &&
            targetInstalled &&
            sim.getBoardModificationController().isFullyRestored() &&
            sim.getBoardPowerController().getState() == BoardPowerState.POWERED)
            verifyFaultedBehavior(BoardPowerState.POWERED);
        if (canLatchCompletionAfterCustomerRetest()) {
            latchCompleted();
        }
    }

    boolean finishJob() {
        // READY includes the only state allowed to run a functional profile.
        // COMPLETED remains interaction-ready, but it is terminal: temporal
        // profiles must never replay merely because Finish Job is invoked again.
        if (state != GeneratedChallengeState.READY)
            return false;
        if (getLiveRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED ||
                customerRetestResult == null || !customerRetestResult.isPassed())
            return false;
        latchCompleted();
        return true;
    }

    GeneratedCustomerRetestResult performCustomerRetest() {
        if (state != GeneratedChallengeState.READY)
            return GeneratedCustomerRetestSupport.failure();
        GeneratedCustomerRetestResult result = instance.invokeOperation(
            GeneratedBoardOperationIds.CUSTOMER_RETEST, sim);
        customerRetestResult = result;
        if (canLatchCompletionAfterCustomerRetest())
            latchCompleted();
        else {
            sim.refreshBoardModificationControls();
            sim.repaint();
        }
        return result;
    }

    boolean invokePlayerOperation(String stableId) {
        if (!isReady() || GeneratedBoardOperationIds.CUSTOMER_RETEST.equals(stableId))
            return false;
        instance.invokeOperation(stableId, sim);
        if (state == GeneratedChallengeState.READY)
            customerRetestResult = null;
        sim.refreshBoardModificationControls();
        sim.repaint();
        return true;
    }

    void beginDeveloperVerificationScope() { developerVerificationScope = true; }
    void endDeveloperVerificationScope() { developerVerificationScope = false; }
    boolean isDeveloperVerificationScopeActive() { return developerVerificationScope; }

    GeneratedRepairStatus getRepairStatus() {
        if (instance.getTemporalBehavior() != null)
            return instance.getTemporalBehavior().getRepairStatus(sim, instance,
                sim.getBoardModificationController(), sim.getBoardPowerController().getState(),
                sim.activeMeasurementOverlay);
        return definition.getBehaviorContract().getRepairStatus(instance,
            sim.getBoardModificationController(), sim.getBoardPowerController().getState(),
                sim.activeMeasurementOverlay);
    }

    private boolean canLatchCompletionAfterCustomerRetest() {
        return !finishJobRequired && state == GeneratedChallengeState.READY &&
            customerRetestResult != null && customerRetestResult.isPassed() &&
            getLiveRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED;
    }

    private void latchCompleted() {
        state = GeneratedChallengeState.COMPLETED;
        sim.refreshChallengeInteractionState();
        sim.repaint();
    }

    private void verifyFaultedBehavior(BoardPowerState powerState) {
        if (instance.getTemporalBehavior() != null)
            instance.getTemporalBehavior().verifyFaultedProfile(sim, instance,
                sim.getBoardModificationController(), powerState);
        else
            definition.getBehaviorContract().verifyFaulted(instance,
                sim.getBoardModificationController(), powerState);
    }

    private void validateDefinition() {
        if (!definition.getCircuitFamilyId().equals(instance.getCircuitFamilyId()) ||
                !definition.getTopologyVariantId().equals(instance.getTopologyVariantId()))
            throw new IllegalArgumentException("Challenge definition is incompatible with board");
        if (definition.getBehaviorContract() != instance.getBehaviorContract())
            throw new IllegalArgumentException("Challenge behavior contract is not owned by board");
        if (instance.getBoard().getComponent(definition.getFault().getTargetComponentId()) == null)
            throw new IllegalArgumentException("Challenge target component is missing");
        if (definition.getFaultBinding() != instance.getFaultBinding())
            throw new IllegalArgumentException("Challenge fault binding is not owned by board");
        validateFaultEffectOwnership(instance, definition.getFault(), definition.getFaultBinding());
    }

    static void validateFaultEffectOwnership(GeneratedBoardInstance instance,
            GeneratedFault fault, GeneratedFaultBinding binding) {
        Vector<CircuitElm> privateElements = binding.getPrivateSimulationElements();
        Vector<CircuitElm> ownedElements = instance.getSimulationElements();
        for (CircuitElm element : privateElements) {
            if (!ownedElements.contains(element))
                throw new IllegalArgumentException("Challenge fault effect is not owned by board");
            if (instance.getComponentBindings().isElementBoundToComponent(
                    fault.getTargetComponentId(), element) ||
                    instance.getExternalPowerBindings().isBackingElement(element) ||
                    instance.getConnectionBindings().isConnectionElement(element))
                throw new IllegalArgumentException("Challenge fault effect is not private infrastructure");
        }
        CircuitElm valueMutationTarget = binding.getEffect().getValueMutationTarget();
        if (valueMutationTarget != null && !instance.getComponentBindings()
                .isElementBoundToComponent(fault.getTargetComponentId(), valueMutationTarget))
            throw new IllegalArgumentException("Challenge value fault mutates an unrelated component");
    }
}
