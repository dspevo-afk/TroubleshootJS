package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Family-owned CircuitJS transient sequence for the RC-delay board. Values
 * are sampled from the solved graph after genuine external isolation; C1
 * then discharges only through the rendered R2 path. No behavior timer,
 * formula waveform, or fault-type decision is involved.
 */
final class RcDelayTemporalBehavior implements GeneratedTemporalBehavior,
        GeneratedLiveTemporalSimulation, PhysicalBoardRuntimeCapability {
    static final String CAPABILITY_ID = "RC_DELAY_TEMPORAL";
    /** Long enough to model a normal player's next click after power-off. */
    static final double PLAYER_RESELECT_SECONDS = .120;
    /** Natural R2/C1 discharge reaches the shared .25 V safety threshold. */
    static final double NATURAL_DISCHARGE_SECONDS = 1.000;
    private static final double EARLY_SAMPLE_SECONDS = .100;
    private static final double LATE_SAMPLE_SECONDS = .700;
    // CirSim's ordinary 5 us max step permits this bounded segment without
    // exceeding the generic 200,000-iteration temporal-solver safety guard.
    private static final double MAX_SOLVER_ADVANCE_SECONDS = .750;
    /**
     * The PCB view normally advances CircuitJS in tiny UI-speed batches.  This
     * bounded solver-time budget makes a real C1/R2 transient observable
     * through ordinary player frames without inventing a wall-clock waveform.
     */
    private static final double LIVE_SOLVER_ADVANCE_SECONDS = .005;
    private static final double HEALTHY_RISE_MINIMUM_FRACTION = .20;
    private static final double HEALTHY_LATE_MINIMUM_FRACTION = .25;
    private static final double HEALTHY_EARLY_MAXIMUM_FRACTION = .65;
    private static final double CLASSIFICATION_RISE_MINIMUM_FRACTION = .15;
    private static final double CLASSIFICATION_LATE_MAXIMUM_FRACTION = .85;
    private static final double CLASSIFICATION_EARLY_DIFFERENCE_FRACTION = .45;
    private static final double CLASSIFICATION_HEALTHY_EARLY_DIFFERENCE_FRACTION = .30;
    private static final double CLASSIFICATION_HEALTHY_LATE_DIFFERENCE_FRACTION = .15;

    private final CircuitPostMeasurementEndpoint output;
    private final CircuitPostMeasurementEndpoint ground;
    private final String outputEndpointId;
    private final String groundEndpointId;
    private final double nominalSupply;
    private GeneratedObservedBehavior observedBehavior;
    private boolean healthyReferenceCaptured;
    private double healthyResidualVoltage;
    private double healthyEarlyVoltage;
    private double healthyLateVoltage;
    private double residualVoltage;
    private double earlyVoltage;
    private double lateVoltage;

    RcDelayTemporalBehavior(CircuitPostMeasurementEndpoint output,
            CircuitPostMeasurementEndpoint ground, String outputEndpointId,
            String groundEndpointId, double nominalSupply) {
        if (output == null || ground == null || outputEndpointId == null ||
                outputEndpointId.length() == 0 || groundEndpointId == null ||
                groundEndpointId.length() == 0 || nominalSupply <= 0 ||
                Double.isNaN(nominalSupply) || Double.isInfinite(nominalSupply))
            throw new IllegalArgumentException("Invalid RC temporal behavior");
        this.output = output;
        this.ground = ground;
        this.outputEndpointId = outputEndpointId;
        this.groundEndpointId = groundEndpointId;
        this.nominalSupply = nominalSupply;
    }

    public void requireOwnedBy(GeneratedBoardInstance instance) {
        requireEndpointOwner(instance, output);
        requireEndpointOwner(instance, ground);
    }

    private static void requireEndpointOwner(GeneratedBoardInstance instance,
            CircuitPostMeasurementEndpoint endpoint) {
        if (instance == null || !instance.getSimulationElements().contains(endpoint.getElement()) ||
                endpoint.getPostIndex() < 0 || endpoint.getPostIndex() >= endpoint.getElement().getPostCount())
            throw new IllegalArgumentException("Fresh temporal state captures a foreign endpoint");
    }

    public GeneratedTemporalDependency getDependency(GeneratedBoardInstance instance) {
        requireOwnedBy(instance);
        requireExactEndpointBinding(instance, outputEndpointId, output);
        requireExactEndpointBinding(instance, groundEndpointId, ground);
        if (!healthyReferenceCaptured)
            throw new IllegalStateException("RC temporal healthy reference is unavailable");
        return new GeneratedTemporalDependency(CAPABILITY_ID,
            GeneratedTemporalDependency.CURRENT_VERSION,
            GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1,
            outputEndpointId, groundEndpointId, nominalSupply,
            PLAYER_RESELECT_SECONDS, NATURAL_DISCHARGE_SECONDS,
            EARLY_SAMPLE_SECONDS, LATE_SAMPLE_SECONDS,
            MAX_SOLVER_ADVANCE_SECONDS, LIVE_SOLVER_ADVANCE_SECONDS,
            ActiveMeasurementReadiness.RESIDUAL_VOLTAGE_THRESHOLD_VOLTS,
            HEALTHY_RISE_MINIMUM_FRACTION, HEALTHY_LATE_MINIMUM_FRACTION,
            HEALTHY_EARLY_MAXIMUM_FRACTION,
            CLASSIFICATION_RISE_MINIMUM_FRACTION,
            CLASSIFICATION_LATE_MAXIMUM_FRACTION,
            CLASSIFICATION_EARLY_DIFFERENCE_FRACTION,
            CLASSIFICATION_HEALTHY_EARLY_DIFFERENCE_FRACTION,
            CLASSIFICATION_HEALTHY_LATE_DIFFERENCE_FRACTION,
            healthyResidualVoltage, healthyEarlyVoltage, healthyLateVoltage);
    }

    /**
     * Stable endpoint names are only useful when they resolve to the exact
     * post consumed by this behavior.  Ownership of an element vector alone
     * would allow a wrong post or a stale semantic label to pass capture.
     */
    private static void requireExactEndpointBinding(GeneratedBoardInstance instance,
            String endpointId, CircuitPostMeasurementEndpoint expected) {
        BoardSimulationBindings bindings = instance.getSimulationBindings();
        CircuitMeasurementEndpoint actual = bindings == null ? null :
            bindings.getEndpoint(endpointId);
        if (!(actual instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Missing temporal endpoint binding: " + endpointId);
        CircuitPostMeasurementEndpoint bound = (CircuitPostMeasurementEndpoint) actual;
        if (bound.getElement() != expected.getElement() ||
                bound.getPostIndex() != expected.getPostIndex())
            throw new IllegalStateException("Temporal endpoint binding changed: " + endpointId);
    }

    public String getCapabilityId() { return CAPABILITY_ID; }

    public double getLiveSolverAdvanceSeconds() {
        return LIVE_SOLVER_ADVANCE_SECONDS;
    }

    /**
     * The temporal recipe has exactly the four solver calls already used by
     * the synchronous sample.  Keep that count aligned with the resumable
     * cursor so generation budgets describe actual solver work.
     */
    public int getProfileWorkUnits() {
        return 4;
    }

    public GeneratedWork<GeneratedRepairStatus> beginProfile(final CirSim sim,
            final GeneratedBoardInstance instance, final Profile profile) {
        if (sim == null || instance == null || profile == null)
            throw new IllegalArgumentException("Missing RC temporal profile context");
        if (sim.getGeneratedBoardInstance() != instance || instance.getTemporalBehavior() != this)
            throw new IllegalStateException("RC temporal profile has no current owner");
        if (profile == Profile.REPAIR && (sim.activeMeasurementOverlay ||
                sim.getBoardPowerController().getState() != BoardPowerState.POWERED ||
                sim.getBoardModificationController() == null ||
                !sim.getBoardModificationController().isFullyRestored()))
            return GeneratedWork.value(GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL);
        return new RcProfileWork(sim, instance, profile);
    }

    public void prepareHealthyProfile(CirSim sim, GeneratedBoardInstance instance) {
        GeneratedWork.complete(beginProfile(sim, instance, Profile.HEALTHY));
    }

    public void prepareFaultedProfile(CirSim sim, GeneratedBoardInstance instance) {
        GeneratedWork.complete(beginProfile(sim, instance, Profile.FAULTED));
    }

    public void verifyFaultedProfile(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        if (observedBehavior != GeneratedObservedBehavior.RC_DELAY_TOO_FAST &&
                observedBehavior != GeneratedObservedBehavior.RC_DELAY_STUCK_LOW)
            throw new IllegalStateException("RC fault did not produce a meaningful transient symptom");
    }

    public GeneratedRepairStatus getRepairStatus(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        if (activeMeasurementOverlay || powerState != BoardPowerState.POWERED ||
                modifications == null || modifications != sim.getBoardModificationController() ||
                !modifications.isFullyRestored())
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        return GeneratedWork.complete(beginProfile(sim, instance, Profile.REPAIR));
    }

    public GeneratedObservedBehavior getObservedBehavior() { return observedBehavior; }
    double getHealthyResidualVoltageForDeveloperVerification() { return healthyResidualVoltage; }
    double getHealthyEarlyVoltageForDeveloperVerification() { return healthyEarlyVoltage; }
    double getHealthyLateVoltageForDeveloperVerification() { return healthyLateVoltage; }
    double getResidualVoltageForDeveloperVerification() { return residualVoltage; }
    double getEarlyVoltageForDeveloperVerification() { return earlyVoltage; }
    double getLateVoltageForDeveloperVerification() { return lateVoltage; }
    double getNominalSupplyForDeveloperVerification() { return nominalSupply; }

    /** Developer-only canary seam; normal gameplay never changes this state. */
    void perturbHealthyReferenceForDeveloperVerification() {
        if (!healthyReferenceCaptured)
            throw new IllegalStateException("RC healthy reference is unavailable");
        double delta = Math.max(1e-6, Math.abs(healthyLateVoltage) * 1e-6);
        double perturbed = healthyLateVoltage + delta;
        if (!finite(perturbed))
            throw new IllegalStateException("RC healthy reference canary overflowed");
        healthyLateVoltage = perturbed;
    }

    void advanceForDeveloperVerification(CirSim sim, double seconds) {
        advanceSolverTime(sim, seconds);
    }

    void advanceNaturalDischargeForDeveloperVerification(CirSim sim) {
        advanceSolverTime(sim, NATURAL_DISCHARGE_SECONDS);
    }

    /** Customer-facing retest: the board is really isolated, discharged, and repowered. */
    void performCustomerRetest(CirSim sim) {
        GeneratedWork.complete(beginCustomerRetest(sim,
            sim == null ? null : sim.getGeneratedBoardInstance()));
    }

    /**
     * Customer retest uses the same four-phase cursor as repair validation.
     * The wrapper deliberately defers the profile finish until its own finish
     * so neither samples nor the player result become visible at the final
     * step boundary.
     */
    GeneratedWork<GeneratedCustomerRetestResult> beginCustomerRetest(
            final CirSim sim, final GeneratedBoardInstance instance) {
        if (sim == null || instance == null || sim.activeMeasurementOverlay ||
                sim.getBoardModificationController() == null ||
                !sim.getBoardModificationController().isFullyRestored())
            return GeneratedWork.value(GeneratedCustomerRetestSupport.failure());
        if (sim.getBoardPowerController().getState() != BoardPowerState.POWERED)
            return GeneratedWork.value(GeneratedCustomerRetestSupport.powerRequiredFailure());

        final BoardModificationController modifications =
            sim.getBoardModificationController();
        final boolean physicalState = modifications.isFullyRestored();
        final GeneratedWork<GeneratedRepairStatus> profile =
            beginProfile(sim, instance, Profile.REPAIR);
        return new GeneratedWork<GeneratedCustomerRetestResult>() {
            private boolean complete;
            private boolean cancelled;
            private boolean readyToFinish;
            private GeneratedCustomerRetestResult result;

            boolean step() {
                if (cancelled)
                    throw new IllegalStateException("RC customer retest was cancelled");
                if (complete || readyToFinish)
                    return false;
                boolean more = profile.step();
                if (more)
                    return true;
                readyToFinish = true;
                return false;
            }

            GeneratedCustomerRetestResult finish() {
                if (complete)
                    return result;
                if (cancelled || !readyToFinish)
                    throw new IllegalStateException("RC customer retest is incomplete");
                GeneratedRepairStatus status = profile.finish();
                requireRetestPhysicalState(sim, instance, modifications, physicalState);
                result = status == GeneratedRepairStatus.CORRECTLY_RESTORED ?
                    GeneratedCustomerRetestSupport.success() :
                    GeneratedCustomerRetestSupport.failure();
                complete = true;
                return result;
            }

            void cancel() {
                if (cancelled || complete)
                    return;
                profile.cancel();
                cancelled = true;
            }

            int getWorkUnits() { return getProfileWorkUnits(); }
        };
    }

    boolean passedCustomerRetest() {
        return observedBehavior == GeneratedObservedBehavior.RC_DELAY_HEALTHY_DELAY;
    }

    /**
     * The healthy profile defines the electrical reference. Subsequent fault
     * and repair decisions compare real solver samples to that reference,
     * which permits a real R1/R2 divider instead of pretending RC_OUT equals
     * VIN.
     */
    private GeneratedObservedBehavior classifyAgainstHealthyProfile(double residual,
            double early, double late) {
        if (!finite(residual) || !finite(early) || !finite(late) ||
                !finite(healthyEarlyVoltage) || !finite(healthyLateVoltage))
            throw new IllegalStateException("RC temporal profile produced a non-finite sample");
        double healthyRise = healthyLateVoltage - healthyEarlyVoltage;
        if (healthyRise <= nominalSupply * CLASSIFICATION_RISE_MINIMUM_FRACTION)
            throw new IllegalStateException("Healthy RC reference has no measurable rise");
        if (late < healthyLateVoltage * CLASSIFICATION_LATE_MAXIMUM_FRACTION)
            return GeneratedObservedBehavior.RC_DELAY_STUCK_LOW;
        if (early > healthyEarlyVoltage + healthyRise *
                CLASSIFICATION_EARLY_DIFFERENCE_FRACTION)
            return GeneratedObservedBehavior.RC_DELAY_TOO_FAST;
        if (Math.abs(early - healthyEarlyVoltage) <= healthyRise *
                CLASSIFICATION_HEALTHY_EARLY_DIFFERENCE_FRACTION &&
                Math.abs(late - healthyLateVoltage) <= healthyRise *
                CLASSIFICATION_HEALTHY_LATE_DIFFERENCE_FRACTION)
            return GeneratedObservedBehavior.RC_DELAY_HEALTHY_DELAY;
        return GeneratedObservedBehavior.RC_DELAY_STUCK_LOW;
    }

    private boolean isHealthyDelay(double residual, double early, double late) {
        return finite(residual) && finite(early) && finite(late) &&
            residual < ActiveMeasurementReadiness.RESIDUAL_VOLTAGE_THRESHOLD_VOLTS &&
            late > nominalSupply * HEALTHY_LATE_MINIMUM_FRACTION &&
            early < late * HEALTHY_EARLY_MAXIMUM_FRACTION &&
            late - early > nominalSupply * HEALTHY_RISE_MINIMUM_FRACTION;
    }

    /**
     * Four-phase owner cursor for one RC profile.  Each step contains one of
     * the existing solver calls; the cursor retains no solver permit between
     * steps and publishes its samples only from finish().
     */
    private final class RcProfileWork extends GeneratedWork<GeneratedRepairStatus> {
        private static final int PHASE_COUNT = 4;

        private final CirSim ownerSim;
        private final GeneratedBoardInstance ownerInstance;
        private final Object ownerGraph;
        private final Vector<CircuitElm> ownerGraphElements;
        private final PhysicalMutationReceipt ownerMutationReceipt;
        private final GeneratedChallengeController ownerChallenge;
        private final GeneratedBoardFamilyState ownerFamilyState;
        private final GeneratedDiagnosticProvider ownerProvider;
        private final GeneratedTemporalBehavior ownerTemporalBehavior;
        private final GeneratedFaultBinding ownerFaultBinding;
        private final GeneratedChallengeDefinition ownerDefinition;
        private final TroubleshootBoard ownerBoard;
        private final BoardSimulationBindings ownerSimulationBindings;
        private final PhysicalBoardRuntime ownerRuntime;
        private final BoardPowerController ownerPowerController;
        private final GeneratedExternalPowerBindings ownerPowerBindings;
        private final BoardModificationController ownerModifications;
        private final BoardPowerState priorPowerState;
        private final GeneratedExternalPowerBindings.SavedControls priorControls;
        private final boolean priorPhysicalState;

        private GeneratedExternalPowerBindings.ControlObservation expectedControls;
        private GeneratedExternalPowerBindings.SavedControls expectedSavedControls;
        private BoardPowerState expectedPowerState;
        private int phase;
        private boolean complete;
        private boolean cancelled;
        private double localResidualVoltage;
        private double localEarlyVoltage;
        private double localLateVoltage;
        private GeneratedObservedBehavior localObservedBehavior;
        private GeneratedRepairStatus result;

        RcProfileWork(CirSim sim, GeneratedBoardInstance instance, Profile profile) {
            if (sim == null || instance == null || profile == null)
                throw new IllegalArgumentException("Missing RC temporal profile context");
            if (CircuitElm.sim != sim || sim.getGeneratedBoardInstance() != instance ||
                    sim.elmList == null || instance.getTemporalBehavior() !=
                        RcDelayTemporalBehavior.this)
                throw new IllegalStateException("RC temporal profile has no current owner");
            requireOwnedBy(instance);

            ownerSim = sim;
            ownerInstance = instance;
            ownerGraph = sim.elmList;
            ownerGraphElements = new Vector<CircuitElm>(sim.elmList);
            ownerChallenge = sim.getGeneratedChallengeController();
            ownerFamilyState = instance.getFamilyState();
            ownerProvider = instance.getDiagnosticProvider();
            ownerTemporalBehavior = instance.getTemporalBehavior();
            ownerFaultBinding = instance.getFaultBinding();
            ownerDefinition = instance.getChallengeDefinition();
            ownerBoard = instance.getBoard();
            ownerSimulationBindings = instance.getSimulationBindings();
            ownerRuntime = instance.getPhysicalBoardRuntime();
            ownerMutationReceipt = ownerRuntime.getLastMutationReceipt();
            ownerPowerController = sim.getBoardPowerController();
            ownerPowerBindings = instance.getExternalPowerBindings();
            ownerModifications = sim.getBoardModificationController();
            if (ownerPowerBindings == null || ownerPowerController == null ||
                    ownerPowerController.getBindingsForDeveloperVerification() !=
                        ownerPowerBindings || ownerModifications == null || ownerRuntime == null)
                throw new IllegalStateException("RC temporal profile has incomplete owner state");
            priorPowerState = ownerPowerController.getState();
            if (priorPowerState == null)
                throw new IllegalStateException("RC temporal profile has no power state");
            priorControls = ownerPowerBindings.saveControls();
            expectedControls = ownerPowerBindings.observeControls();
            expectedSavedControls = ownerPowerBindings.saveControls();
            expectedPowerState = priorPowerState;
            priorPhysicalState = ownerModifications.isFullyRestored();
            requireCurrent("begin");
            this.profile = profile;
        }

        private final Profile profile;

        boolean step() {
            if (cancelled)
                throw new IllegalStateException("RC temporal profile was cancelled");
            if (complete || phase >= PHASE_COUNT)
                return false;
            requireCurrent("phase " + phase + " before");
            if (phase == 0) {
                ownerSim.setBoardPowerStateForGeneratedTemporalProfile(
                    BoardPowerState.UNPOWERED);
                expectedPowerState = BoardPowerState.UNPOWERED;
                rememberCurrentControls();
                ownerSim.advanceGeneratedTemporalProfile(.750);
            } else if (phase == 1) {
                ownerSim.advanceGeneratedTemporalProfile(.250);
                localResidualVoltage = Math.abs(voltage());
            } else if (phase == 2) {
                ownerSim.setBoardPowerStateForGeneratedTemporalProfile(
                    BoardPowerState.POWERED);
                expectedPowerState = BoardPowerState.POWERED;
                rememberCurrentControls();
                ownerSim.advanceGeneratedTemporalProfile(.100);
                localEarlyVoltage = voltage();
            } else {
                ownerSim.advanceGeneratedTemporalProfile(.700);
                localLateVoltage = voltage();
                classifyLocalProfile();
            }
            phase++;
            requireCurrent("phase " + (phase - 1) + " after");
            return phase < PHASE_COUNT;
        }

        GeneratedRepairStatus finish() {
            if (complete)
                return result;
            if (cancelled || phase < PHASE_COUNT || localObservedBehavior == null)
                throw new IllegalStateException("RC temporal profile is incomplete");
            requireCurrent("finish");
            residualVoltage = localResidualVoltage;
            earlyVoltage = localEarlyVoltage;
            lateVoltage = localLateVoltage;
            observedBehavior = localObservedBehavior;
            if (profile == Profile.HEALTHY) {
                healthyResidualVoltage = localResidualVoltage;
                healthyEarlyVoltage = localEarlyVoltage;
                healthyLateVoltage = localLateVoltage;
                healthyReferenceCaptured = true;
                result = GeneratedRepairStatus.CORRECTLY_RESTORED;
            } else if (profile == Profile.FAULTED) {
                result = GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            } else {
                result = localObservedBehavior ==
                    GeneratedObservedBehavior.RC_DELAY_HEALTHY_DELAY ?
                    GeneratedRepairStatus.CORRECTLY_RESTORED :
                    GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            }
            complete = true;
            return result;
        }

        void cancel() {
            if (cancelled || complete)
                return;
            Throwable cleanupFailure = null;
            if (canRestorePriorControls()) {
                try {
                    ownerPowerController.restoreForDeveloperVerification(
                        ownerPowerBindings, priorPowerState, priorControls);
                    if (isCurrentOwnerIdentity())
                        ownerRuntime.onBoardPowerStateChanged(priorPowerState);
                } catch (Throwable failure) {
                    cleanupFailure = failure;
                    // Retain the effects of this synchronous restoration attempt
                    // so an exact retry can finish after a partial cleanup failure.
                    if (isCurrentOwnerIdentity()) {
                        expectedPowerState = ownerPowerController.getState();
                        rememberCurrentControls();
                    }
                }
            }
            if (cleanupFailure instanceof Error)
                throw (Error) cleanupFailure;
            if (cleanupFailure instanceof RuntimeException)
                throw (RuntimeException) cleanupFailure;
            if (cleanupFailure != null)
                throw new IllegalStateException("RC temporal profile cleanup failed",
                    cleanupFailure);
            cancelled = true;
        }

        int getWorkUnits() { return PHASE_COUNT; }

        private void classifyLocalProfile() {
            if (profile == Profile.HEALTHY) {
                if (!isHealthyDelay(localResidualVoltage, localEarlyVoltage,
                        localLateVoltage))
                    throw new IllegalStateException(
                        "Healthy RC graph did not produce a visible delay");
                localObservedBehavior = GeneratedObservedBehavior.RC_DELAY_HEALTHY_DELAY;
                return;
            }
            localObservedBehavior = classifyAgainstHealthyProfile(localResidualVoltage,
                localEarlyVoltage, localLateVoltage);
        }

        private void rememberCurrentControls() {
            expectedControls = ownerPowerBindings.observeControls();
            expectedSavedControls = ownerPowerBindings.saveControls();
        }

        private void requireCurrent(String stage) {
            if (!isCurrentOwnerIdentity() || ownerPowerController.getState() !=
                    expectedPowerState || ownerModifications.isFullyRestored() !=
                        priorPhysicalState || !controlsAreCurrent())
                throw new IllegalStateException("RC temporal profile lost its owner at " + stage);
            requireOwnedBy(ownerInstance);
        }

        private boolean controlsAreCurrent() {
            try {
                return expectedControls != null && expectedControls.isCurrent() &&
                    expectedSavedControls != null && expectedSavedControls.matches();
            } catch (Throwable ignored) {
                return false;
            }
        }

        private boolean canRestorePriorControls() {
            try {
                return isCurrentOwnerIdentity() &&
                    ownerPowerController.getState() == expectedPowerState &&
                    ownerModifications.isFullyRestored() == priorPhysicalState &&
                    controlsAreCurrent();
            } catch (Throwable ignored) {
                return false;
            }
        }

        private boolean isCurrentOwnerIdentity() {
            return ownerSim.getGeneratedBoardInstance() == ownerInstance &&
                ownerSim.elmList == ownerGraph && graphElementsUnchanged() && CircuitElm.sim == ownerSim &&
                ownerSim.getGeneratedChallengeController() == ownerChallenge &&
                ownerSim.getBoardModificationController() == ownerModifications &&
                ownerSim.getBoardPowerController() == ownerPowerController &&
                ownerPowerController.getBindingsForDeveloperVerification() ==
                    ownerPowerBindings && ownerInstance.getBoard() == ownerBoard &&
                ownerInstance.getSimulationBindings() == ownerSimulationBindings &&
                ownerInstance.getExternalPowerBindings() == ownerPowerBindings &&
                ownerInstance.getPhysicalBoardRuntime() == ownerRuntime &&
                ownerRuntime.getLastMutationReceipt() == ownerMutationReceipt &&
                ownerInstance.getFamilyState() == ownerFamilyState &&
                ownerInstance.getDiagnosticProvider() == ownerProvider &&
                ownerInstance.getTemporalBehavior() == ownerTemporalBehavior &&
                ownerInstance.getFaultBinding() == ownerFaultBinding &&
                ownerInstance.getChallengeDefinition() == ownerDefinition;
        }

        private boolean graphElementsUnchanged() {
            if (ownerSim.elmList.size() != ownerGraphElements.size()) return false;
            for (int i = 0; i < ownerGraphElements.size(); i++)
                if (ownerSim.elmList.get(i) != ownerGraphElements.get(i)) return false;
            return true;
        }
    }

    private static void requireRetestPhysicalState(CirSim sim,
            GeneratedBoardInstance instance, BoardModificationController modifications,
            boolean expectedState) {
        if (sim == null || instance == null || modifications == null ||
                sim.getGeneratedBoardInstance() != instance ||
                sim.getBoardModificationController() != modifications ||
                modifications.isFullyRestored() != expectedState)
            throw new IllegalStateException("Customer retest changed physical board state");
    }

    private static void advanceSolverTime(CirSim sim, double seconds) {
        if (sim == null || seconds <= 0 || Double.isNaN(seconds) || Double.isInfinite(seconds))
            throw new IllegalArgumentException("Invalid RC solver duration");
        double remaining = seconds;
        while (remaining > 1e-12) {
            double segment = Math.min(remaining, MAX_SOLVER_ADVANCE_SECONDS);
            sim.advanceGeneratedTemporalProfile(segment);
            remaining -= segment;
        }
    }

    private double voltage() {
        return output.getElement().getPostVoltage(output.getPostIndex()) -
            ground.getElement().getPostVoltage(ground.getPostIndex());
    }

    private boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
