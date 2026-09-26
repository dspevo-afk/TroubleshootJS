package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.Vector;

/** Q30 input recipe and functional observations on the current CircuitJS graph. */
final class Rb30Behavior implements GeneratedBoardFamilyState,
        GeneratedChallengeBehaviorContract, GeneratedTemporalBehavior,
        GeneratedLiveTemporalSimulation {
    static final String SENSORS_LOW = "SENSORS_LOW";
    static final String SENSORS_A_ONLY = "SENSORS_A_ONLY";
    static final String SENSORS_B_ONLY = "SENSORS_B_ONLY";
    static final String SENSORS_HIGH = "SENSORS_HIGH";
    static final double SAMPLE_SECONDS = .030;
    private final TroubleshootBoard board;
    private final GeneratedExternalPowerBindings power;
    private final GeneratedBoardOperationCatalog operations = new GeneratedBoardOperationCatalog();
    private final GeneratedCustomerRetestProfile retest;
    private int input = 3;
    private GeneratedObservedBehavior observed;

    Rb30Behavior(Rb30Generator.Candidate candidate) {
        board = candidate.board();
        power = candidate.assembly.power;
        addInput(SENSORS_LOW, "Set both sensors LOW", 0);
        addInput(SENSORS_A_ONLY, "Set sensor A HIGH, B LOW", 1);
        addInput(SENSORS_B_ONLY, "Set sensor A LOW, B HIGH", 2);
        addInput(SENSORS_HIGH, "Set both sensors HIGH", 3);
        retest = new GeneratedCustomerRetestProfile("RB30_CUSTOMER_RETEST",
            "Check that each external load follows its own sensor, including both loads together.",
            "Connect the 12 V main and isolated load supplies and both sensor inputs.",
            "Both LOW, A only, B only, both HIGH; restore the previous inputs.",
            "Observe each output across its own two-terminal load connector.",
            "Allow 30 ms of CircuitJS time after each input change.",
            "Low-voltage DC, two 180 ohm external loads; all serviced leads reconnected.",
            new GeneratedCustomerRetestProfile.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance owner) {
                    if (!GeneratedCustomerRetestSupport.isReadyForPoweredObservation(sim, owner))
                        return GeneratedCustomerRetestSupport.failure();
                    return exercise(sim, owner) ? GeneratedCustomerRetestSupport.success() :
                        GeneratedCustomerRetestSupport.failure();
                }
            });
        operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CUSTOMER_RETEST,
            "Retest Customer", new GeneratedBoardOperation.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance owner) {
                    requireCurrent(sim, owner);
                    return retest.execute(sim, owner);
                }
            }));
    }

    private void addInput(String id, String label, final int value) {
        operations.add(new GeneratedBoardOperation(id, label, new GeneratedBoardOperation.Executor() {
            public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance owner) {
                setInputs(sim, owner, value);
                return null;
            }
        }));
    }

    public void requireOwnedBy(GeneratedBoardInstance owner) {
        if (owner == null || owner.getBoard() != board || owner.getExternalPowerBindings() != power ||
                owner.getFamilyState() != this || owner.getTemporalBehavior() != this)
            throw new IllegalArgumentException("Foreign Q30 behavior owner");
        for (String id : new String[] { "SENSOR_A", "SENSOR_B" })
            for (CircuitElm element : power.getBinding(id).getBackingElements())
                if (!owner.getSimulationElements().contains(element))
                    throw new IllegalArgumentException("Foreign Q30 sensor source");
    }

    private void requireCurrent(CirSim sim, GeneratedBoardInstance owner) {
        requireOwnedBy(owner);
        if (sim == null || sim.getGeneratedBoardInstance() != owner || sim.activeMeasurementOverlay)
            throw new IllegalStateException("Q30 operation lost its live owner");
    }

    void setInputs(CirSim sim, GeneratedBoardInstance owner, int value) {
        requireCurrent(sim, owner);
        if (value < 0 || value > 3) throw new IllegalArgumentException("Unsupported Q30 inputs");
        LimitedDcSupplyElm a = power.getBinding("SENSOR_A").getLimitedSupply();
        LimitedDcSupplyElm b = power.getBinding("SENSOR_B").getLimitedSupply();
        a.configure((value & 1) != 0 ? 5 : 0, a.getLimitAmps());
        b.configure((value & 2) != 0 ? 5 : 0, b.getLimitAmps());
        input = value;
        // Restamp the real finite sources; this never connects a source or repowers the board.
        sim.advanceGeneratedTemporalProfile(SAMPLE_SECONDS);
        requireCurrent(sim, owner);
    }

    int getInputs() { return input; }

    static double voltage(GeneratedBoardInstance owner, String positive, String negative) {
        return postVoltage(owner, positive) - postVoltage(owner, negative);
    }

    private static double postVoltage(GeneratedBoardInstance owner, String pad) {
        CircuitMeasurementEndpoint endpoint = owner.getSimulationBindings().getEndpoint(pad);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Missing Q30 observation endpoint " + pad);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        if (!owner.getSimulationElements().contains(post.getElement()))
            throw new IllegalStateException("Stale Q30 observation endpoint " + pad);
        double value = post.getElement().getPostVoltage(post.getPostIndex());
        if (!PowerDomainContract.finite(value))
            throw new IllegalStateException("Non-finite Q30 observation " + pad);
        return value;
    }

    boolean healthy(GeneratedBoardInstance owner, int condition) {
        requireOwnedBy(owner);
        double rail = voltage(owner, "U1.OUTPUT", "U1.RETURN");
        double a = voltage(owner, "JOA.1", "JOA.2");
        double b = voltage(owner, "JOB.1", "JOB.2");
        return rail >= 4.75 && rail <= 5.25 && outputMatches(a, (condition & 1) != 0) &&
            outputMatches(b, (condition & 2) != 0);
    }

    private static boolean outputMatches(double volts, boolean on) {
        return on ? volts >= 10.8 && volts <= 12.6 : Math.abs(volts) <= .05;
    }

    private boolean exercise(CirSim sim, GeneratedBoardInstance owner) {
        return exercise(sim, owner, false);
    }

    private boolean exercise(CirSim sim, GeneratedBoardInstance owner, boolean requireHealthy) {
        requireCurrent(sim, owner);
        int prior = input;
        Object graph = sim.elmList;
        boolean passed = true;
        StringBuilder failures = new StringBuilder();
        try {
            // Always exercise every condition, including after an earlier functional failure.
            for (int condition = 0; condition < 4; condition++) {
                setInputs(sim, owner, condition);
                boolean matched = healthy(owner, condition);
                passed &= matched;
                if (requireHealthy && !matched)
                    failures.append(" input=").append(condition).append(" rail=")
                        .append(voltage(owner, "U1.OUTPUT", "U1.RETURN"))
                        .append(" outputA=").append(voltage(owner, "JOA.1", "JOA.2"))
                        .append(" outputB=").append(voltage(owner, "JOB.1", "JOB.2"));
            }
            if (requireHealthy && !passed)
                throw new IllegalStateException("Healthy Q30 failed four input conditions:" + failures);
            return passed;
        } finally {
            if (sim.getGeneratedBoardInstance() == owner && sim.elmList == graph)
                setInputs(sim, owner, prior);
        }
    }

    public boolean isFaultedTargetInstalled(GeneratedBoardInstance owner, String id) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(owner, id);
    }
    public GeneratedBoardOperationCatalog getOperationCatalog() { return operations; }
    public GeneratedCustomerRetestProfile getCustomerRetestProfile() { return retest; }
    // The ordinary CircuitJS run already advances time. Keep its additional
    // UI-frame increment small on this larger graph; explicit profiles retain
    // their full 30 ms settling interval and unchanged solver budgets.
    public double getLiveSolverAdvanceSeconds() { return .0001; }
    public int getProfileWorkUnits() { return 1; }

    public GeneratedTemporalDependency getDependency(GeneratedBoardInstance owner) {
        requireOwnedBy(owner);
        TreeMap<String, String> values = new TreeMap<String, String>();
        values.put("recipe", "LOW-A_ONLY-B_ONLY-HIGH-restore-inputs");
        values.put("fault-preparation", "healthy-four-conditions-then-LOW-apply-fault-then-HIGH");
        values.put("sample-seconds", Double.toString(SAMPLE_SECONDS));
        values.put("qualification-solver", "CircuitJS-adaptive");
        values.put("qualification-maximum-step-seconds", "0.000005");
        values.put("qualification-minimum-step-seconds", "0.00000000005");
        values.put("rail-range-volts", "4.75..5.25");
        values.put("on-range-volts", "10.8..12.6");
        values.put("off-maximum-volts", "0.05");
        values.put("outputs", "JOA.1-JOA.2;JOB.1-JOB.2");
        values.put("topology", owner.getTopologyVariantId());
        values.put("physical-policy", MediumBoardPhysicalPolicy.identity());
        values.put("seed", Long.toString(owner.getSeed()));
        return new GeneratedTemporalDependency("RB30_TWO_CHANNEL_FUNCTION", 1,
            GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1,
            "JOA.1", "JOA.2", values);
    }

    public GeneratedWork<GeneratedRepairStatus> beginProfile(final CirSim sim,
            final GeneratedBoardInstance owner, final Profile profile) {
        requireCurrent(sim, owner);
        final Object graph = sim.elmList;
        return new GeneratedWork<GeneratedRepairStatus>() {
            private boolean done, cancelled;
            private GeneratedRepairStatus result;
            boolean step() {
                if (cancelled) throw new IllegalStateException("Q30 profile cancelled");
                if (done) return false;
                requireCurrent(sim, owner);
                if (sim.elmList != graph) throw new IllegalStateException("Q30 profile lost its graph");
                if (profile == Profile.HEALTHY) {
                    prepareHealthyProfile(sim, owner);
                    result = GeneratedRepairStatus.CORRECTLY_RESTORED;
                } else if (profile == Profile.FAULTED) {
                    prepareFaultedProfile(sim, owner);
                    result = GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
                } else result = GeneratedCustomerRetestSupport.isReadyForPoweredObservation(sim, owner) &&
                    exercise(sim, owner) ? GeneratedRepairStatus.CORRECTLY_RESTORED :
                    GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
                done = true;
                return false;
            }
            GeneratedRepairStatus finish() {
                if (!done || cancelled) throw new IllegalStateException("Q30 profile incomplete");
                return result;
            }
            void cancel() { if (!done) cancelled = true; }
            int getWorkUnits() { return 1; }
        };
    }

    public void prepareHealthyProfile(CirSim sim, GeneratedBoardInstance owner) {
        exercise(sim, owner, true);
        // Establish the same real pre-fault electrical state on both variants.
        // Opening a sensor resistor while HIGH can leave the regenerative
        // feedback path latched HIGH, hiding the symptom at the HIGH test point.
        // Drive LOW before the controller applies the fault; never reset the
        // decision element's internal state or alter its feedback model.
        setInputs(sim, owner, 0);
    }
    public void prepareFaultedProfile(CirSim sim, GeneratedBoardInstance owner) {
        setInputs(sim, owner, 3);
        observed = healthy(owner, 3) ? null : GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING;
    }
    public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState state) {
        if (state != BoardPowerState.POWERED || !healthy(owner, input))
            throw new IllegalStateException("Q30 healthy function absent");
    }
    public void verifyFaulted(GeneratedBoardInstance owner, BoardModificationController modifications,
            BoardPowerState state) {
        requireOwnedBy(owner);
        if (state != BoardPowerState.POWERED || observed == null || healthy(owner, 3))
            throw new IllegalStateException("Q30 fault has no measured symptom");
    }
    public void verifyFaultedProfile(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, BoardPowerState state) {
        verifyFaulted(owner, modifications, state);
    }
    public GeneratedObservedBehavior getObservedBehavior() { return observed; }
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner,
            BoardModificationController modifications, BoardPowerState state, boolean overlay) {
        return !overlay && state == BoardPowerState.POWERED && modifications.isFullyRestored() &&
            healthy(owner, input) ? GeneratedRepairStatus.CORRECTLY_RESTORED :
            GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
    }
    public GeneratedRepairStatus getRepairStatus(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, BoardPowerState state, boolean overlay) {
        // Live status is observational, as in E03. Only an explicit customer
        // retest or temporal profile may drive the four-condition input recipe.
        return getRepairStatus(owner, modifications, state, overlay);
    }
    public boolean isFunctionallyRepaired(GeneratedBoardInstance owner,
            BoardModificationController modifications, BoardPowerState state, boolean overlay) {
        return getRepairStatus(owner, modifications, state, overlay) == GeneratedRepairStatus.CORRECTLY_RESTORED;
    }
    GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios() {
        Vector<GeneratedScenario<GeneratedObservedBehavior>> result =
            new Vector<GeneratedScenario<GeneratedObservedBehavior>>();
        result.add(new GeneratedScenario<GeneratedObservedBehavior>("RB30_OUTPUT_NOT_TRACKING",
            "OUTPUT_NOT_TRACKING", "One or both loads fail to follow their sensor inputs. Check each channel separately and together.",
            GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING,
            new GeneratedScenarioCompatibility<GeneratedObservedBehavior>() {
                public boolean matches(GeneratedBoardInstance owner, BoardModificationController modifications,
                        BoardPowerState state, GeneratedObservedBehavior expected) {
                    return state == BoardPowerState.POWERED && observed == expected;
                }
            }));
        return new GeneratedScenarioCatalog<GeneratedObservedBehavior>(result, this);
    }
}
