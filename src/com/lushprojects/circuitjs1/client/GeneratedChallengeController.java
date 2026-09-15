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
    private boolean boundedTemporalPreparation;
    private GeneratedWork<GeneratedRepairStatus> preparationProfileWork;
    private int temporalPreparationUnits;
    private GeneratedWork<GeneratedCustomerRetestResult> activeRetestWork;
    private GeneratedWork<Void> activeRetestCompletion;

    void useBoundedTemporalPreparation() { boundedTemporalPreparation = true; }
    boolean hasTemporalPreparationWork() { return preparationProfileWork != null; }
    int getTemporalPreparationUnits() { return temporalPreparationUnits; }
    boolean requiresExplicitCompletion() { return finishJobRequired; }

    void cancelTemporalPreparation() {
        GeneratedWork<GeneratedRepairStatus> work = preparationProfileWork;
        if (work != null) work.cancel();
        preparationProfileWork = null;
    }

    void cancelOwnedWork() {
        Throwable failure = null;
        try { cancelTemporalPreparation(); } catch (Throwable problem) { failure = problem; }
        GeneratedWork<?> work = activeRetestWork;
        try { if (work != null) work.cancel(); activeRetestWork = null; }
        catch (Throwable problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
        if (activeRetestWork == null) operationInProgress = false;
        work = activeRetestCompletion;
        try { if (work != null) work.cancel(); activeRetestCompletion = null; }
        catch (Throwable problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure != null) throw new IllegalStateException("Generated work cleanup failed", failure);
    }

    private boolean advancePreparationProfile(GeneratedTemporalBehavior.Profile profile) {
        if (preparationProfileWork == null)
            preparationProfileWork = instance.getTemporalBehavior().beginProfile(sim, instance, profile);
        boolean more = preparationProfileWork.step();
        temporalPreparationUnits++;
        if (more) { sim.generatedBoardVerificationPending = true; return false; }
        preparationProfileWork.finish();
        preparationProfileWork = null;
        return true;
    }

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
            if (temporal) {
                if (boundedTemporalPreparation) {
                    if (!advancePreparationProfile(GeneratedTemporalBehavior.Profile.HEALTHY)) return;
                } else instance.getTemporalBehavior().prepareHealthyProfile(sim, instance);
            }
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
            if (instance.getTemporalBehavior() != null) {
                if (boundedTemporalPreparation) {
                    if (!advancePreparationProfile(GeneratedTemporalBehavior.Profile.FAULTED)) return;
                } else instance.getTemporalBehavior().prepareFaultedProfile(sim, instance);
            }
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
        // Admission already proved the generated fault's symptom. In READY,
        // supported player edits may create additional failures or change that
        // symptom. Structural/electrical ownership is verified by the runtime;
        // only the customer retest decides whether the current behavior works.
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
        return GeneratedWork.complete(beginCustomerRetest());
    }

    GeneratedWork<GeneratedCustomerRetestResult> beginCustomerRetest() {
        if (!isCurrentOwner() || state != GeneratedChallengeState.READY ||
                !sim.isGeneratedRuntimeSettled())
            return GeneratedWork.value(GeneratedCustomerRetestSupport.failure());
        final Object request = new Object();
        currentRetestRequest = request;
        customerRetestResult = null;
        operationInProgress = true;
        final GeneratedWork<GeneratedCustomerRetestResult> operation;
        try {
            GeneratedBoardOperation declared = instance.getOperationCatalog().find(
                GeneratedBoardOperationIds.CUSTOMER_RETEST);
            if (declared == null) throw new IllegalStateException("Missing customer retest operation");
            operation = declared.begin(sim, instance);
            if (operation == null) throw new IllegalStateException("Missing customer retest work");
        } catch (RuntimeException failure) {
            operationInProgress = false;
            throw failure;
        } catch (Error failure) {
            operationInProgress = false;
            throw failure;
        }
        activeRetestWork = new GeneratedWork<GeneratedCustomerRetestResult>() {
            private boolean complete, cancelled;
            private GeneratedCustomerRetestResult result;
            boolean step() {
                if (cancelled) throw new IllegalStateException("Customer retest was cancelled");
                if (complete) return false;
                if (request != currentRetestRequest || !isCurrentOwner() ||
                        state != GeneratedChallengeState.READY)
                    throw new IllegalStateException("Customer retest lost its request owner");
                if (operation.step()) return true;
                result = operation.finish();
                complete = true;
                operationInProgress = false;
                activeRetestWork = null;
                GeneratedWork<Void> completion = retestCompletion(request, result);
                activeRetestCompletion = completion;
                try {
                    if (retestCompletionDispatch == null) completion.run();
                    else retestCompletionDispatch.dispatch(completion);
                } catch (RuntimeException failure) {
                    try { completion.cancel(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                    throw failure;
                } catch (Error failure) {
                    try { completion.cancel(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                    throw failure;
                }
                return false;
            }
            GeneratedCustomerRetestResult finish() {
                if (!complete || cancelled) throw new IllegalStateException("Customer retest is incomplete");
                return result;
            }
            void cancel() {
                if (cancelled || complete) return;
                operation.cancel();
                cancelled = true;
                if (activeRetestWork == this) { activeRetestWork = null; operationInProgress = false; }
            }
            int getWorkUnits() { return operation.getWorkUnits(); }
        };
        return activeRetestWork;
    }

    private GeneratedWork<Void> retestCompletion(final Object request,
            final GeneratedCustomerRetestResult result) {
        return new GeneratedWork<Void>() {
            private boolean complete;
            private GeneratedWork<GeneratedRepairStatus> repair;
            boolean step() {
                if (complete) return false;
                if (request != currentRetestRequest || !isCurrentOwner() ||
                        state != GeneratedChallengeState.READY) { cancel(); return false; }
                boolean restored = false;
                if (!finishJobRequired && result != null && result.isPassed()) {
                    if (instance.getTemporalBehavior() != null) {
                        if (repair == null) repair = instance.getTemporalBehavior().beginProfile(
                            sim, instance, GeneratedTemporalBehavior.Profile.REPAIR);
                        if (repair.step()) return true;
                        restored = repair.finish() == GeneratedRepairStatus.CORRECTLY_RESTORED;
                        repair = null;
                    } else restored = getLiveRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED;
                }
                customerRetestResult = result;
                complete = true;
                if (activeRetestCompletion == this) activeRetestCompletion = null;
                if (restored) latchCompleted();
                else { sim.refreshBoardModificationControls(); sim.repaint(); }
                return false;
            }
            Void finish() {
                if (!complete) throw new IllegalStateException("Retest completion is incomplete");
                return null;
            }
            void cancel() {
                if (complete) return;
                GeneratedWork<GeneratedRepairStatus> pending = repair;
                if (pending != null) pending.cancel();
                repair = null;
                complete = true;
                if (activeRetestCompletion == this) activeRetestCompletion = null;
            }
            int getWorkUnits() {
                return !finishJobRequired && result != null && result.isPassed() &&
                    instance.getTemporalBehavior() != null ?
                    instance.getTemporalBehavior().getProfileWorkUnits() : 1;
            }
        };
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
