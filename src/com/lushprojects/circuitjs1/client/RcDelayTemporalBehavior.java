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

    private final CircuitPostMeasurementEndpoint output;
    private final CircuitPostMeasurementEndpoint ground;
    private final double nominalSupply;
    private GeneratedObservedBehavior observedBehavior;
    private double healthyResidualVoltage;
    private double healthyEarlyVoltage;
    private double healthyLateVoltage;
    private double residualVoltage;
    private double earlyVoltage;
    private double lateVoltage;

    RcDelayTemporalBehavior(CircuitPostMeasurementEndpoint output,
            CircuitPostMeasurementEndpoint ground, double nominalSupply) {
        if (output == null || ground == null || nominalSupply <= 0 ||
                Double.isNaN(nominalSupply) || Double.isInfinite(nominalSupply))
            throw new IllegalArgumentException("Invalid RC temporal behavior");
        this.output = output;
        this.ground = ground;
        this.nominalSupply = nominalSupply;
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

    void advanceForDeveloperVerification(CirSim sim, double seconds) {
        advanceSolverTime(sim, seconds);
    }

    void advanceNaturalDischargeForDeveloperVerification(CirSim sim) {
        advanceSolverTime(sim, NATURAL_DISCHARGE_SECONDS);
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
        if (healthyRise <= nominalSupply * .15)
            throw new IllegalStateException("Healthy RC reference has no measurable rise");
        if (lateVoltage < healthyLateVoltage * .85)
            return GeneratedObservedBehavior.RC_DELAY_STUCK_LOW;
        if (earlyVoltage > healthyEarlyVoltage + healthyRise * .45)
            return GeneratedObservedBehavior.RC_DELAY_TOO_FAST;
        if (Math.abs(earlyVoltage - healthyEarlyVoltage) <= healthyRise * .30 &&
                Math.abs(lateVoltage - healthyLateVoltage) <= healthyRise * .15)
            return GeneratedObservedBehavior.RC_DELAY_HEALTHY_DELAY;
        return GeneratedObservedBehavior.RC_DELAY_STUCK_LOW;
    }

    private boolean isHealthyDelay() {
        return finite(residualVoltage) && finite(earlyVoltage) && finite(lateVoltage) &&
            residualVoltage < ActiveMeasurementReadiness.RESIDUAL_VOLTAGE_THRESHOLD_VOLTS &&
            lateVoltage > nominalSupply * .25 && earlyVoltage < lateVoltage * .65 &&
            lateVoltage - earlyVoltage > nominalSupply * .20;
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
