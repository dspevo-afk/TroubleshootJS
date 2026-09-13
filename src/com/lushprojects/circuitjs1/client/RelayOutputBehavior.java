package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.Vector;

/** Electrical observations and input recipe; RelayElm owns the actual RL/contact dynamics. */
final class RelayOutputBehavior implements GeneratedBoardFamilyState,
        GeneratedChallengeBehaviorContract, GeneratedTemporalBehavior, GeneratedLiveTemporalSimulation {
    static final double SAMPLE_SECONDS = .025;
    static final double DISCHARGED_AMPS = 1e-6;
    private final SwitchElm command;
    private final CircuitPostMeasurementEndpoint output, reference;
    private final GeneratedBoardOperationCatalog operations = new GeneratedBoardOperationCatalog();
    private final GeneratedCustomerRetestProfile retest;
    private GeneratedObservedBehavior observed;

    RelayOutputBehavior(SwitchElm command, CircuitPostMeasurementEndpoint output,
            CircuitPostMeasurementEndpoint reference) {
        this.command = command; this.output = output; this.reference = reference;
        operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH,
            "Set control HIGH", new GeneratedBoardOperation.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance owner) {
                    requireOwnedBy(owner); setCommand(sim, true); return null;
                }
            }));
        operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CONTROL_INPUT_LOW,
            "Set control LOW", new GeneratedBoardOperation.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance owner) {
                    requireOwnedBy(owner); setCommand(sim, false); return null;
                }
            }));
        retest = new GeneratedCustomerRetestProfile("RELAY_OUTPUT_RETEST",
            "Check that the external load turns on with control HIGH and off with control LOW.",
            "Connect the 5 V control supplies and the isolated 12 V load supply.",
            "Control HIGH, then LOW; restore the previous command.",
            "Measure the load across J4 terminals 1 and 2.",
            "Allow 25 ms of solver time after each command.",
            "Low-voltage DC only: 180 Ohm external load, no contact arcing or bounce model.",
            new GeneratedCustomerRetestProfile.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance owner) {
                    if (!GeneratedCustomerRetestSupport.isReadyForPoweredObservation(sim, owner))
                        return GeneratedCustomerRetestSupport.failure();
                    boolean prior = isCommandedOn();
                    try {
                        setCommand(sim, true); boolean on = healthyOn();
                        setCommand(sim, false); boolean off = healthyOff();
                        return on && off ? GeneratedCustomerRetestSupport.success() :
                            GeneratedCustomerRetestSupport.failure();
                    } finally {
                        // Synchronous input recipe never connects a source or restores a foreign owner.
                        if (sim.getGeneratedBoardInstance() == owner) setCommand(sim, prior);
                    }
                }
            });
        operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CUSTOMER_RETEST,
            "Retest Customer", new GeneratedBoardOperation.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim, GeneratedBoardInstance owner) {
                    return retest.execute(sim, owner);
                }
            }));
    }

    public void requireOwnedBy(GeneratedBoardInstance owner) {
        if (owner == null || !owner.getSimulationElements().contains(command) ||
                !owner.getSimulationElements().contains(output.getElement()) ||
                !owner.getSimulationElements().contains(reference.getElement()) ||
                !same(owner.getSimulationBindings().getEndpoint("J4.1"), output) ||
                !same(owner.getSimulationBindings().getEndpoint("J4.2"), reference))
            throw new IllegalArgumentException("Foreign relay behavior endpoints");
    }
    private boolean same(CircuitMeasurementEndpoint actual, CircuitPostMeasurementEndpoint expected) {
        if (!(actual instanceof CircuitPostMeasurementEndpoint)) return false;
        CircuitPostMeasurementEndpoint ep = (CircuitPostMeasurementEndpoint)actual;
        return ep.getElement() == expected.getElement() && ep.getPostIndex() == expected.getPostIndex();
    }
    public GeneratedTemporalDependency getDependency(GeneratedBoardInstance owner) {
        requireOwnedBy(owner);
        TreeMap<String,String> parameters = new TreeMap<String,String>();
        parameters.put("sample-seconds", Double.toString(SAMPLE_SECONDS));
        parameters.put("live-seconds", "0.005");
        parameters.put("output-on-min-volts", "10.8");
        parameters.put("output-on-max-volts", "12.6");
        parameters.put("output-off-max-volts", "0.05");
        parameters.put("discharged-amps", Double.toString(DISCHARGED_AMPS));
        parameters.put("recipe", "HIGH-sample-LOW-sample-HIGH");
        return new GeneratedTemporalDependency("RELAY_OUTPUT_TEMPORAL", 1,
            GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1, "J4.1", "J4.2", parameters);
    }
    public boolean isFaultedTargetInstalled(GeneratedBoardInstance owner, String id) {
        return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(owner, id);
    }
    public GeneratedBoardOperationCatalog getOperationCatalog() { return operations; }
    public GeneratedCustomerRetestProfile getCustomerRetestProfile() { return retest; }
    public double getLiveSolverAdvanceSeconds() { return .005; }
    boolean isCommandedOn() { return command.position == 0; }
    private void setCommand(CirSim sim, boolean on) {
        if (isCommandedOn() != on) command.toggle();
        sim.advanceGeneratedTemporalProfile(SAMPLE_SECONDS);
    }
    double outputVoltage() {
        return output.getElement().getPostVoltage(output.getPostIndex()) -
            reference.getElement().getPostVoltage(reference.getPostIndex());
    }
    private boolean healthyOn() { double v = outputVoltage(); return v > 10.8 && v < 12.6; }
    private boolean healthyOff() { return Math.abs(outputVoltage()) < .05; }
    public void prepareHealthyProfile(CirSim sim, GeneratedBoardInstance owner) {
        requireOwnedBy(owner);
        setCommand(sim, true); boolean on = healthyOn();
        setCommand(sim, false); boolean off = healthyOff();
        setCommand(sim, true);
        if (!on || !off || !healthyOn()) throw new IllegalStateException("Healthy relay failed HIGH/LOW retest");
    }
    public void prepareFaultedProfile(CirSim sim, GeneratedBoardInstance owner) {
        requireOwnedBy(owner); setCommand(sim, true);
        observed = healthyOn() ? null : GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING;
    }
    public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState power) {
        requireOwnedBy(owner);
        if (power != BoardPowerState.POWERED || !healthyOn())
            throw new IllegalStateException("Healthy relay output is absent");
    }
    public void verifyFaulted(GeneratedBoardInstance owner, BoardModificationController modifications,
            BoardPowerState power) {
        if (power != BoardPowerState.POWERED || observed != GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING)
            throw new IllegalStateException("Relay fault has no meaningful symptom");
    }
    public void verifyFaultedProfile(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, BoardPowerState power) { verifyFaulted(owner, modifications, power); }
    public GeneratedObservedBehavior getObservedBehavior() { return observed; }
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner,
            BoardModificationController modifications, BoardPowerState power, boolean overlay) {
        return !overlay && power == BoardPowerState.POWERED && modifications.isFullyRestored() &&
            (isCommandedOn() ? healthyOn() : healthyOff()) ? GeneratedRepairStatus.CORRECTLY_RESTORED :
                GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
    }
    public GeneratedRepairStatus getRepairStatus(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, BoardPowerState power, boolean overlay) {
        return getRepairStatus(owner, modifications, power, overlay);
    }
    public boolean isFunctionallyRepaired(GeneratedBoardInstance owner,
            BoardModificationController modifications, BoardPowerState power, boolean overlay) {
        return getRepairStatus(owner, modifications, power, overlay) == GeneratedRepairStatus.CORRECTLY_RESTORED;
    }
    static boolean isDischarged(GeneratedBoardInstance owner) {
        for (CircuitElm e : owner.getSimulationElements()) if (e instanceof ServiceRelayElm) {
            double amps = ((ServiceRelayElm)e).coilCurrent;
            if (!PowerDomainContract.finite(amps) || Math.abs(amps) >= DISCHARGED_AMPS) return false;
        }
        return true;
    }
    GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios() {
        Vector<GeneratedScenario<GeneratedObservedBehavior>> scenarios = new Vector<GeneratedScenario<GeneratedObservedBehavior>>();
        scenarios.add(new GeneratedScenario<GeneratedObservedBehavior>("RELAY_OUTPUT_ABSENT", "LOAD_WONT_START",
            "The external load will not turn on when the control input is HIGH. Restore switching on HIGH and off on LOW.",
            GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING,
            new GeneratedScenarioCompatibility<GeneratedObservedBehavior>() {
                public boolean matches(GeneratedBoardInstance owner, BoardModificationController mods,
                        BoardPowerState power, GeneratedObservedBehavior behavior) {
                    return power == BoardPowerState.POWERED && observed == behavior;
                }
            }));
        return new GeneratedScenarioCatalog<GeneratedObservedBehavior>(scenarios, this);
    }
}
