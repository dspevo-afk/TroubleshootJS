package com.lushprojects.circuitjs1.client;

class GeneratedChallengeController {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final GeneratedChallengeDefinition definition;
    private final GeneratedFaultController faults;
    private final GeneratedChallengeLifecycleEvidence lifecycleEvidence =
        new GeneratedChallengeLifecycleEvidence();
    private boolean developerVerificationScope;
    private GeneratedChallengeState state = GeneratedChallengeState.PREPARING_HEALTHY;

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
            definition.getFaultValidator().verify(instance,
                sim.getBoardModificationController(), sim.getBoardPowerController().getState());
            lifecycleEvidence.selectedFaultValidated = true;
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
    String getComplaintText() { return isCompleted() ?
        "Repair verified. Indicator operating normally." : definition.getComplaintText(); }

    void verifyReadyState() {
        if (!isReady() || developerVerificationScope)
            return;
        if (!faults.isApplied())
            throw new IllegalStateException("Selected challenge fault was cleared outside developer scope");
        if (instance.getFamilyState().isFaultedTargetInstalled(
                definition.getFault().getTargetComponentId()) &&
            sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
                sim.getBoardModificationController().isComponentInstalled(
                    definition.getFault().getTargetComponentId()))
            definition.getFaultValidator().verify(instance, sim.getBoardModificationController(),
                BoardPowerState.POWERED);
        if (definition.getRepairValidator().isFunctionallyRepaired(instance,
                sim.getBoardModificationController(), sim.getBoardPowerController().getState(),
                sim.activeMeasurementOverlay)) {
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
        if (instance.getBoard().getComponent(definition.getFault().getTargetComponentId()) == null)
            throw new IllegalArgumentException("Challenge target component is missing");
        if (definition.getFaultBinding() != instance.getFaultBinding() ||
                !instance.getSimulationElements().contains(
                    definition.getFaultBinding().getIsolationElement()))
            throw new IllegalArgumentException("Challenge fault binding is not owned by board");
        if (instance.getComponentBindings().isElementBoundToComponent(
                definition.getFault().getTargetComponentId(),
                definition.getFaultBinding().getIsolationElement()) ||
                instance.getExternalPowerBindings().isBackingElement(
                    definition.getFaultBinding().getIsolationElement()) ||
                instance.getConnectionBindings().isConnectionElement(
                    definition.getFaultBinding().getIsolationElement()))
            throw new IllegalArgumentException("Challenge isolation element is not private infrastructure");
    }
}
