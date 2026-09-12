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
    private Object currentRetestRequest = new Object();
    private boolean operationInProgress;
    private Object diagnosticAdmissionAttempt;
    private GeneratedDiagnosticProofReceipt diagnosticProof;
    private boolean stagedGenerationPresentation;
    interface RetestCompletionDispatch { void dispatch(Runnable completion); }
    private RetestCompletionDispatch retestCompletionDispatch;

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

    Object beginDiagnosticAdmission() {
        if (!isCurrentOwner() || instance.isDeveloperOnlyFaultRoute() ||
                diagnosticAdmissionAttempt != null || operationInProgress)
            throw new IllegalStateException("Diagnostic admission requires an idle current normal owner");
        diagnosticProof = null;
        diagnosticAdmissionAttempt = new Object();
        return diagnosticAdmissionAttempt;
    }

    void completeDiagnosticAdmission(GeneratedDiagnosticProofReceipt receipt, Object attempt) {
        if (receipt == null || attempt == null || diagnosticAdmissionAttempt != attempt)
            throw new IllegalStateException("Stale, empty or already consumed diagnostic admission attempt");
        receipt.validateForAdmission(sim, instance, this, attempt);
        diagnosticProof = receipt;
        diagnosticAdmissionAttempt = null;
    }

    void abortDiagnosticAdmission(Object attempt) {
        if (attempt != null && diagnosticAdmissionAttempt == attempt) {
            diagnosticAdmissionAttempt = null;
            diagnosticProof = null;
        }
    }

    /** Historical evidence for THIS controller, not authority for a later admission. */
    GeneratedDiagnosticProofReceipt getDiagnosticProofReceipt() { return diagnosticProof; }

    Vector<GeneratedDiagnosticSolvabilityEvidence> getDiagnosticProofEvidence() {
        if (diagnosticProof == null)
            throw new IllegalStateException("This owner has no completed production diagnostic proof");
        return diagnosticProof.getEvidence();
    }

    void begin() {
        lifecycleEvidence.healthyGenerationInstalled = true;
        sim.requestGeneratedBoardVerification();
    }

    /** A10 qualifies the detached candidate before presenting its complaint. */
    void deferGenerationPresentation() {
        if (state != GeneratedChallengeState.PREPARING_HEALTHY)
            throw new IllegalStateException("Generation presentation must be deferred before solving");
        stagedGenerationPresentation = true;
    }

    void completeGenerationPresentation() {
        if (!stagedGenerationPresentation || !isCurrentOwner() || !isReady() ||
                diagnosticProof == null || scenario == null || !lifecycleEvidence.selectedFaultValidated)
            throw new IllegalStateException("Generation presentation requires complete current proof");
        markScenarioPresented();
        stagedGenerationPresentation = false;
    }

    /** Configure the scenario's real input before capturing proof dependencies. */
    void prepareGenerationInputState() {
        if (!stagedGenerationPresentation || !isCurrentOwner() || !isReady() || scenario != null)
            throw new IllegalStateException("Generation input preparation requires its private faulted owner");
        chooseScenarioAndInputs();
    }

    private void presentScenario() {
        chooseScenarioAndInputs();
        markScenarioPresented();
    }

    private void chooseScenarioAndInputs() {
        scenario = definition.getScenarioCatalog().select(definition.getSelectionSeed(), instance,
            sim.getBoardModificationController(), sim.getBoardPowerController().getState());
        scenario.present(sim, instance);
    }

    private void markScenarioPresented() {
        lifecycleEvidence.scenarioCompatibilityValidated = true;
        state = GeneratedChallengeState.READY;
        lifecycleEvidence.readyAfterValidation = true;
        sim.refreshBoardModificationControls();
        sim.repaint();
    }

    void afterGeneratedVerification() {
        if (state == GeneratedChallengeState.PREPARING_HEALTHY) {
            boolean temporal = instance.getTemporalBehavior() != null;
            if (temporal)
                instance.getTemporalBehavior().prepareHealthyProfile(sim, instance);
            lifecycleEvidence.healthyGraphAnalyzedAfterTimeAdvance = true;
            lifecycleEvidence.healthyFamilyValidated = true;
            state = GeneratedChallengeState.PREPARING_FAULTED;
            faults.apply();
            // The fault controller has queued the next ordinary verification
            // callback.  A temporal healthy settlement must return here so the
            // next bounded operation owns fault-profile preparation.
            if (!temporal && sim.isQuickPlayMode())
                sim.updateCircuit();
            lifecycleEvidence.selectedFaultApplied = faults.isApplied();
            return;
        }
        if (state == GeneratedChallengeState.PREPARING_FAULTED) {
            if (instance.getTemporalBehavior() != null)
                instance.getTemporalBehavior().prepareFaultedProfile(sim, instance);
            lifecycleEvidence.faultedGraphAnalyzedAfterTimeAdvance = true;
            if (instance.getTemporalBehavior() != null)
                instance.getTemporalBehavior().verifyFaultedProfile(sim, instance,
                    sim.getBoardModificationController(), sim.getBoardPowerController().getState());
            else
                definition.getBehaviorContract().verifyFaulted(instance,
                    sim.getBoardModificationController(), sim.getBoardPowerController().getState());
            lifecycleEvidence.selectedFaultValidated = true;
            if (!instance.isDeveloperOnlyFaultRoute() &&
                    !sim.developerVerifierRunning &&
                    !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning())
                GeneratedDiagnosticSolvabilityAdmission.validateLive(sim, instance, this);
            if (stagedGenerationPresentation) {
                // Internal READY permits guarded proof operations. The A10 owner lock
                // and detached workbench prevent player access until publication.
                state = GeneratedChallengeState.READY;
            } else {
                presentScenario();
            }
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

    void perturbScenarioForDeveloperVerification() {
        if (scenario == null) throw new IllegalStateException("No selected scenario");
        scenario = scenario.withComplaintText(scenario.getComplaintText() + " changed");
    }
    GeneratedCustomerRetestProfile getCustomerRetestProfile() {
        return instance.getCustomerRetestProfile();
    }
    GeneratedCustomerRetestResult getCustomerRetestResult() { return customerRetestResult; }
    GeneratedRepairStatus getLiveRepairStatus() { return getRepairStatus(); }

    GeneratedBoardInstance getInstanceForRuntimeValidation() { return instance; }
    boolean isOperationInProgress() { return operationInProgress; }

    void setRetestCompletionDispatchForDeveloperVerification(RetestCompletionDispatch dispatch) {
        retestCompletionDispatch = dispatch;
    }

    RetestCompletionDispatch getRetestCompletionDispatchForDeveloperVerification() {
        return retestCompletionDispatch;
    }

    private boolean isCurrentOwner() {
        return sim.getGeneratedBoardInstance() == instance &&
            sim.getGeneratedChallengeController() == this;
    }

    void invalidateCustomerRetest() {
        currentRetestRequest = new Object();
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
        if (!isCurrentOwner() || state != GeneratedChallengeState.READY ||
                !sim.isGeneratedRuntimeSettled())
            return false;
        if (getLiveRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED ||
                customerRetestResult == null || !customerRetestResult.isPassed())
            return false;
        latchCompleted();
        return true;
    }

    GeneratedCustomerRetestResult performCustomerRetest() {
        if (!isCurrentOwner() || state != GeneratedChallengeState.READY ||
                !sim.isGeneratedRuntimeSettled())
            return GeneratedCustomerRetestSupport.failure();
        final Object request = new Object();
        currentRetestRequest = request;
        customerRetestResult = null;
        final GeneratedCustomerRetestResult result;
        operationInProgress = true;
        try {
            result = instance.invokeOperation(GeneratedBoardOperationIds.CUSTOMER_RETEST, sim);
        } finally {
            operationInProgress = false;
        }
        // This is the actual result publication callback, also captured by
        // the bounded developer succession proof. Normal dispatch is immediate.
        Runnable completion = new Runnable() {
            public void run() {
                if (request != currentRetestRequest || !isCurrentOwner() ||
                        state != GeneratedChallengeState.READY)
                    return;
                customerRetestResult = result;
                if (canLatchCompletionAfterCustomerRetest())
                    latchCompleted();
                else {
                    sim.refreshBoardModificationControls();
                    sim.repaint();
                }
            }
        };
        if (retestCompletionDispatch == null)
            completion.run();
        else
            retestCompletionDispatch.dispatch(completion);
        return result;
    }

    boolean invokePlayerOperation(String stableId) {
        if (!isCurrentOwner() || !isReady() || !sim.isGeneratedRuntimeSettled() ||
                GeneratedBoardOperationIds.CUSTOMER_RETEST.equals(stableId))
            return false;
        invalidateCustomerRetest();
        operationInProgress = true;
        try {
            instance.invokeOperation(stableId, sim);
        } finally {
            operationInProgress = false;
        }
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
        return isCurrentOwner() && !finishJobRequired && state == GeneratedChallengeState.READY &&
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
        if (!instance.isDeveloperOnlyFaultRoute()) {
            GeneratedFaultServiceabilityAdmission.validate(instance, definition.getFaultBinding());
        }
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
