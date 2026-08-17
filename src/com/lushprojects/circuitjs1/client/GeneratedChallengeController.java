package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class GeneratedChallengeController {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final GeneratedChallengeDefinition definition;
    private final GeneratedFaultController faults;
    private final GeneratedChallengeLifecycleEvidence lifecycleEvidence =
        new GeneratedChallengeLifecycleEvidence();
    private boolean developerVerificationScope;
    private GeneratedChallengeState state = GeneratedChallengeState.PREPARING_HEALTHY;
    private GeneratedScenario<GeneratedObservedBehavior> scenario;

    GeneratedChallengeController(CirSim sim, GeneratedBoardInstance instance) {
        if (instance.getChallengeDefinition() == null)
            throw new IllegalArgumentException("Generated challenge requires a definition");
        this.sim = sim;
        this.instance = instance;
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
            lifecycleEvidence.healthyGraphAnalyzedAfterTimeAdvance = true;
            lifecycleEvidence.healthyFamilyValidated = true;
            state = GeneratedChallengeState.PREPARING_FAULTED;
            faults.apply();
            lifecycleEvidence.selectedFaultApplied = faults.isApplied();
            return;
        }
        if (state == GeneratedChallengeState.PREPARING_FAULTED) {
            lifecycleEvidence.faultedGraphAnalyzedAfterTimeAdvance = true;
            definition.getBehaviorContract().verifyFaulted(instance,
                sim.getBoardModificationController(), sim.getBoardPowerController().getState());
            lifecycleEvidence.selectedFaultValidated = true;
            scenario = definition.getScenarioCatalog().select(definition.getSelectionSeed(), instance,
                sim.getBoardModificationController(), sim.getBoardPowerController().getState());
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
    boolean isCompleted() { return state == GeneratedChallengeState.COMPLETED; }
    GeneratedChallengeState getState() { return state; }
    GeneratedFaultController getFaultController() { return faults; }
    GeneratedChallengeDefinition getDefinition() { return definition; }
    GeneratedChallengeLifecycleEvidence getLifecycleEvidence() { return lifecycleEvidence; }
    GeneratedScenario<GeneratedObservedBehavior> getScenario() { return scenario; }
    String getComplaintText() {
        if (scenario == null)
            return "Preparing challenge...";
        if (!isCompleted())
            return scenario.getComplaintText();
        return definition.getCompletionText();
    }

    void verifyReadyState() {
        if (!isReady() || developerVerificationScope)
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
            definition.getBehaviorContract().verifyFaulted(instance,
                sim.getBoardModificationController(),
                BoardPowerState.POWERED);
        if (definition.getBehaviorContract().getRepairStatus(instance,
            sim.getBoardModificationController(), sim.getBoardPowerController().getState(),
                sim.activeMeasurementOverlay) == GeneratedRepairStatus.CORRECTLY_RESTORED) {
            state = GeneratedChallengeState.COMPLETED;
            sim.refreshBoardModificationControls();
            sim.repaint();
        }
    }

    void beginDeveloperVerificationScope() { developerVerificationScope = true; }
    void endDeveloperVerificationScope() { developerVerificationScope = false; }
    boolean isDeveloperVerificationScopeActive() { return developerVerificationScope; }

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
