package com.lushprojects.circuitjs1.client;

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

    public void prepareHealthyProfile(CirSim sim, GeneratedBoardInstance instance) {
        samplePowerCycle(sim);
        healthyResidualVoltage = residualVoltage;
        healthyEarlyVoltage = earlyVoltage;
        healthyLateVoltage = lateVoltage;
        if (!isHealthyDelay())
            throw new IllegalStateException("Healthy RC graph did not produce a visible delay");
        healthyReferenceCaptured = true;
        observedBehavior = GeneratedObservedBehavior.RC_DELAY_HEALTHY_DELAY;
    }

    public void prepareFaultedProfile(CirSim sim, GeneratedBoardInstance instance) {
        samplePowerCycle(sim);
        observedBehavior = classifyAgainstHealthyProfile();
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
                !modifications.isFullyRestored())
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        samplePowerCycle(sim);
        observedBehavior = classifyAgainstHealthyProfile();
        return observedBehavior == GeneratedObservedBehavior.RC_DELAY_HEALTHY_DELAY ?
            GeneratedRepairStatus.CORRECTLY_RESTORED :
            GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
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
        samplePowerCycle(sim);
        observedBehavior = classifyAgainstHealthyProfile();
    }

    boolean passedCustomerRetest() {
        return observedBehavior == GeneratedObservedBehavior.RC_DELAY_HEALTHY_DELAY;
    }

    private void samplePowerCycle(CirSim sim) {
        sim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.UNPOWERED);
        advanceSolverTime(sim, NATURAL_DISCHARGE_SECONDS);
        residualVoltage = Math.abs(voltage());
        sim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.POWERED);
        advanceSolverTime(sim, EARLY_SAMPLE_SECONDS);
        earlyVoltage = voltage();
        advanceSolverTime(sim, LATE_SAMPLE_SECONDS);
        lateVoltage = voltage();
    }

    /**
     * The healthy profile defines the electrical reference. Subsequent fault
     * and repair decisions compare real solver samples to that reference,
     * which permits a real R1/R2 divider instead of pretending RC_OUT equals
     * VIN.
     */
    private GeneratedObservedBehavior classifyAgainstHealthyProfile() {
        if (!finite(residualVoltage) || !finite(earlyVoltage) || !finite(lateVoltage) ||
                !finite(healthyEarlyVoltage) || !finite(healthyLateVoltage))
            throw new IllegalStateException("RC temporal profile produced a non-finite sample");
        double healthyRise = healthyLateVoltage - healthyEarlyVoltage;
        if (healthyRise <= nominalSupply * CLASSIFICATION_RISE_MINIMUM_FRACTION)
            throw new IllegalStateException("Healthy RC reference has no measurable rise");
        if (lateVoltage < healthyLateVoltage * CLASSIFICATION_LATE_MAXIMUM_FRACTION)
            return GeneratedObservedBehavior.RC_DELAY_STUCK_LOW;
        if (earlyVoltage > healthyEarlyVoltage + healthyRise *
                CLASSIFICATION_EARLY_DIFFERENCE_FRACTION)
            return GeneratedObservedBehavior.RC_DELAY_TOO_FAST;
        if (Math.abs(earlyVoltage - healthyEarlyVoltage) <= healthyRise *
                CLASSIFICATION_HEALTHY_EARLY_DIFFERENCE_FRACTION &&
                Math.abs(lateVoltage - healthyLateVoltage) <= healthyRise *
                CLASSIFICATION_HEALTHY_LATE_DIFFERENCE_FRACTION)
            return GeneratedObservedBehavior.RC_DELAY_HEALTHY_DELAY;
        return GeneratedObservedBehavior.RC_DELAY_STUCK_LOW;
    }

    private boolean isHealthyDelay() {
        return finite(residualVoltage) && finite(earlyVoltage) && finite(lateVoltage) &&
            residualVoltage < ActiveMeasurementReadiness.RESIDUAL_VOLTAGE_THRESHOLD_VOLTS &&
            lateVoltage > nominalSupply * HEALTHY_LATE_MINIMUM_FRACTION &&
            earlyVoltage < lateVoltage * HEALTHY_EARLY_MAXIMUM_FRACTION &&
            lateVoltage - earlyVoltage > nominalSupply * HEALTHY_RISE_MINIMUM_FRACTION;
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
