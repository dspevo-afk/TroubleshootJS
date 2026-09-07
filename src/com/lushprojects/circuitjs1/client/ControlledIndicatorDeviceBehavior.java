package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * Device-owned behavior for the bounded NMOS/LED composition.
 *
 * <p>The contribution objects describe local parts and ports.  This class
 * owns the one end-to-end assertion made by the device: a real high control
 * must sink the LED load and a real low control must release it.  Every value
 * used by the checks is read from the CircuitJS elements retained by the
 * assembled board.</p>
 */
final class ControlledIndicatorDeviceBehavior
        implements GeneratedChallengeBehaviorContract {
    static final String FAMILY_ID = ControlledIndicatorBlockContributions.FAMILY_ID;
    static final String TOPOLOGY_VARIANT_ID = "NMOS_LED_LOW_SIDE";
    static final String LOAD_POWER_INPUT_ID = "LOAD_VIN_INPUT";
    static final String CONTROL_POWER_INPUT_ID = "CONTROL_VIN_INPUT";
    static final double SUPPLY_VOLTAGE = 5.0;

    private static final double MIN_LOAD_CURRENT = 0.008;
    private static final double MIN_LED_CURRENT = 0.005;
    private static final double MAX_LOAD_CURRENT = 0.020;
    private static final double OFF_CURRENT = 0.000001;
    private static final double ON_VGS = 3.0;
    private static final double OFF_VGS = 0.1;
    private static final double ON_VDS = 0.8;
    private static final double OFF_VDS_MARGIN = 1.0;
    private static final double UNPOWERED_CURRENT = 0.000001;

    private final BoundedAssemblyPlan plan;
    private final SwitchElm controlCommand;

    ControlledIndicatorDeviceBehavior(BoundedAssemblyPlan plan,
            SwitchElm controlCommand) {
        if (plan == null || !plan.isControlledIndicator())
            throw new IllegalArgumentException("Controlled indicator plan is required");
        if (controlCommand == null)
            throw new IllegalArgumentException("Controlled indicator command is required");
        this.plan = plan;
        this.controlCommand = controlCommand;
    }

    BoundedAssemblyPlan getPlan() { return plan; }

    @Override
    public void verifyHealthy(GeneratedBoardInstance instance,
            BoardPowerState powerState) {
        if (instance == null || powerState == null)
            throw new IllegalArgumentException("Missing controlled healthy context");
        if (powerState == BoardPowerState.UNPOWERED) {
            requireNearZero(loadCurrent(instance), "Unpowered load current is not zero");
            requireNearZero(ledCurrent(instance), "Unpowered LED current is not zero");
            return;
        }
        // This lifecycle check runs before the selected fault is applied.
        // Prove the complete device requirement, including release of the
        // low-side output, on the actual healthy graph.
        CirSim sim = CircuitElm.sim;
        if (sim == null)
            throw new IllegalStateException("Healthy controlled proof has no active simulator");
        boolean wasOn = isCommandedOn();
        sim.beginObservationalValidation();
        try {
            setCommand(sim, false);
            requireHealthyOff(instance);
            setCommand(sim, true);
            requireOn(instance);
        } finally {
            try { setCommand(sim, wasOn); }
            finally { sim.endObservationalValidation(); }
        }
    }

    /** Explicit healthy OFF proof used by assembly and diagnostic admission. */
    void verifyHealthyOff(GeneratedBoardInstance instance) {
        require(instance != null, "Missing controlled healthy OFF instance");
        requireHealthyOff(instance);
    }

    /** Explicit healthy ON proof used by assembly and diagnostic admission. */
    void verifyHealthyOn(GeneratedBoardInstance instance) {
        require(instance != null, "Missing controlled healthy ON instance");
        requireOn(instance);
    }

    @Override
    public void verifyFaulted(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        if (instance == null || powerState == null || modifications == null)
            throw new IllegalArgumentException("Missing controlled fault context");
        if (powerState == BoardPowerState.UNPOWERED) {
            requireNearZero(loadCurrent(instance),
                    "Unpowered controlled load current is not zero during fault verification");
            return;
        }
        if (!modifications.isFullyRestored())
            throw new IllegalStateException("Fault proof requires untouched physical owners");
        boolean wasOn = isCommandedOn();
        CirSim sim = CircuitElm.sim;
        if (sim != null) sim.beginObservationalValidation();
        try {
            setCommand(sim, true);
            requireFaultedOnSymptom(instance);
        } finally {
            try {
                setCommand(sim, wasOn);
            } finally {
                if (sim != null) sim.endObservationalValidation();
            }
        }
    }

    @Override
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        if (instance == null || powerState != BoardPowerState.POWERED ||
                activeMeasurementOverlay || modifications == null ||
                !modifications.isFullyRestored())
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        CirSim sim = CircuitElm.sim;
        boolean wasOn = isCommandedOn();
        if (sim != null) sim.beginObservationalValidation();
        try {
            setCommand(sim, true);
            if (!isHealthyOn(instance))
                return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            setCommand(sim, false);
            return isHealthyOff(instance) ?
                GeneratedRepairStatus.CORRECTLY_RESTORED :
                GeneratedRepairStatus.DEGRADED_BUT_OPERATING;
        } finally {
            try {
                setCommand(sim, wasOn);
            } finally {
                if (sim != null) sim.endObservationalValidation();
            }
        }
    }

    @Override
    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        return getRepairStatus(instance, modifications, powerState,
                activeMeasurementOverlay) == GeneratedRepairStatus.CORRECTLY_RESTORED;
    }

    /** Device-level command and customer-retest operations. */
    GeneratedBoardFamilyState createFamilyState() {
        return new FamilyState(this, controlCommand);
    }

    /** Complaint candidates are selected from observed solver behavior only. */
    GeneratedScenarioCatalog<GeneratedObservedBehavior> createScenarioCatalog() {
        Vector<GeneratedScenario<GeneratedObservedBehavior>> scenarios =
                new Vector<GeneratedScenario<GeneratedObservedBehavior>>();
        scenarios.add(new GeneratedScenario<GeneratedObservedBehavior>(
                "CONTROLLED_INDICATOR_DARK",
                "CONTROLLED_LOAD_DOES_NOT_SWITCH_ON",
                "The controlled indicator does not turn on.",
                GeneratedObservedBehavior.NMOS_LOAD_NOT_SWITCHING,
                new GeneratedScenarioCompatibility<GeneratedObservedBehavior>() {
                    public boolean matches(GeneratedBoardInstance instance,
                            BoardModificationController modifications,
                            BoardPowerState powerState,
                            GeneratedObservedBehavior behavior) {
                        if (powerState != BoardPowerState.POWERED || instance == null)
                            return false;
                        return isCommandedOn() && loadCurrent(instance) < OFF_CURRENT &&
                                ledCurrent(instance) < OFF_CURRENT;
                    }
                }, new GeneratedScenarioPresentation<GeneratedObservedBehavior>() {
                    public void present(CirSim sim, GeneratedBoardInstance instance,
                            GeneratedObservedBehavior behavior) {
                        if (behavior != GeneratedObservedBehavior.NMOS_LOAD_NOT_SWITCHING)
                            throw new IllegalStateException("Unsupported controlled complaint");
                        instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH, sim);
                    }
                }));
        return new GeneratedScenarioCatalog<GeneratedObservedBehavior>(scenarios);
    }

    boolean isCommandedOn() {
        return controlCommand.position == 0;
    }

    /** Exposed to the assembler for solver-backed private admission checks. */
    boolean isHealthyOn(GeneratedBoardInstance instance) {
        return healthyOn(instance);
    }

    /** Exposed to the assembler for solver-backed private admission checks. */
    boolean isHealthyOff(GeneratedBoardInstance instance) {
        return healthyOff(instance);
    }

    private void requireOn(GeneratedBoardInstance instance) {
        if (!healthyOn(instance))
            throw new IllegalStateException("Controlled indicator does not solve to ON behavior");
    }

    private void requireHealthyOff(GeneratedBoardInstance instance) {
        if (!healthyOff(instance))
            throw new IllegalStateException("Controlled indicator does not solve to OFF behavior");
    }

    private void requireFaultedOnSymptom(GeneratedBoardInstance instance) {
        double load = loadCurrent(instance);
        double led = ledCurrent(instance);
        require(finite(load) && finite(led) && load < OFF_CURRENT && led < OFF_CURRENT,
                "Faulted controlled indicator still conducts on command");
        if (ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY.equals(
                plan.getFaultBlockKey())) {
            require(gateSourceVoltage(instance) < OFF_VGS,
                    "Open gate resistor did not leave the NMOS gate pulled low");
            require(Math.abs(rgCurrent(instance)) < OFF_CURRENT,
                    "Open gate resistor still conducts");
        } else if (ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY.equals(
                plan.getFaultBlockKey())) {
            require(gateSourceVoltage(instance) > ON_VGS,
                    "Open load resistor lost the commanded gate drive");
            require(Math.abs(rloadCurrent(instance)) < OFF_CURRENT,
                    "Open load resistor still conducts");
        } else {
            throw new IllegalStateException("Unknown controlled fault block " +
                    plan.getFaultBlockKey());
        }
    }

    private boolean healthyOn(GeneratedBoardInstance instance) {
        double load = loadCurrent(instance);
        double led = ledCurrent(instance);
        double mosfet = mosfetCurrent(instance);
        double supply = supplyVoltage(instance);
        boolean task49 = plan.isControlledIndicatorValues();
        ResistorElm loadResistor = resistor(instance, "load", "RLOAD");
        double loadResistance = loadResistor.getResistance();
        double loadRatedWatts = loadRatedWatts(instance);
        double minimumLoad = task49 ? plan.getResolvedLoadRecipe().getIntent()
                .getTargetMinimumCurrentAmps() : MIN_LOAD_CURRENT;
        double maximumLoad = task49 ? plan.getResolvedLoadRecipe().getIntent()
                .getTypedDemandAmps() : MAX_LOAD_CURRENT;
        double minimumSupply = task49 ? plan.getResolvedLoadRecipe().getIntent()
                .getSourceMinimumVolts() : 4.0;
        boolean electricalEnvelope = !task49 ||
                (supply >= plan.getResolvedLoadRecipe().getIntent().getSourceMinimumVolts() &&
                 supply <= plan.getResolvedLoadRecipe().getIntent().getSourceMaximumVolts() &&
                 drainSourceVoltage(instance) >= plan.getResolvedLoadRecipe().getIntent()
                    .getSinkMinimumVolts() - 1.0e-9 &&
                 drainSourceVoltage(instance) <= plan.getResolvedLoadRecipe().getIntent()
                    .getSinkMaximumVolts() + 1.0e-9 &&
                 ledForwardVoltage(instance) >= plan.getResolvedLoadRecipe().getIntent()
                    .getLedMinimumForwardVolts() - 1.0e-9 &&
                 ledForwardVoltage(instance) <= plan.getResolvedLoadRecipe().getIntent()
                    .getLedMaximumForwardVolts() + 1.0e-9 &&
                 finite(loadResistance) && loadResistance > 0.0 &&
                 finite(loadRatedWatts) && loadRatedWatts > 0.0 &&
                 load * load * loadResistance <= loadRatedWatts + 1.0e-9);
        return finite(load) && finite(led) && finite(mosfet) && finite(supply) &&
                electricalEnvelope &&
                load >= minimumLoad && load <= maximumLoad &&
                led >= MIN_LED_CURRENT && Math.abs(load - led) < 0.0005 &&
                Math.abs(load - mosfet) < 0.002 && gateSourceVoltage(instance) > ON_VGS &&
                drainSourceVoltage(instance) <= ON_VDS && (!task49 ||
                    drainSourceVoltage(instance) >= 0.0) &&
                 (task49 ? supply >= minimumSupply : supply > minimumSupply) &&
                controlVoltage(instance) > 3.0 && gateCurrent(instance) < 1.0e-9;
    }

    private boolean healthyOff(GeneratedBoardInstance instance) {
        double supply = supplyVoltage(instance);
        return finite(supply) && finite(loadCurrent(instance)) &&
                Math.abs(loadCurrent(instance)) < OFF_CURRENT &&
                gateSourceVoltage(instance) < OFF_VGS &&
                drainSourceVoltage(instance) > supply - OFF_VDS_MARGIN &&
                gateCurrent(instance) < 1.0e-9;
    }

    private double loadCurrent(GeneratedBoardInstance instance) {
        return Math.abs(resistor(instance, "load", "RLOAD").getCurrent());
    }

    private double rloadCurrent(GeneratedBoardInstance instance) {
        return loadCurrent(instance);
    }

    private double rgCurrent(GeneratedBoardInstance instance) {
        return resistor(instance, "driver", "RG").getCurrent();
    }

    private double ledCurrent(GeneratedBoardInstance instance) {
        return Math.abs(led(instance).getCurrent());
    }

    private double mosfetCurrent(GeneratedBoardInstance instance) {
        return Math.abs(mosfet(instance).getCurrent());
    }

    private double gateCurrent(GeneratedBoardInstance instance) {
        return Math.abs(mosfet(instance).getCurrentIntoNode(0));
    }

    private double gateSourceVoltage(GeneratedBoardInstance instance) {
        NMosfetElm q = mosfet(instance);
        return q.getPostVoltage(0) - q.getPostVoltage(1);
    }

    private double drainSourceVoltage(GeneratedBoardInstance instance) {
        NMosfetElm q = mosfet(instance);
        return q.getPostVoltage(2) - q.getPostVoltage(1);
    }

    private double ledForwardVoltage(GeneratedBoardInstance instance) {
        LEDElm led = led(instance);
        return led.getPostVoltage(0) - led.getPostVoltage(1);
    }

    private double supplyVoltage(GeneratedBoardInstance instance) {
        return voltage(instance, "power-adapter", "J1.1") -
                voltage(instance, "power-adapter", "J1.2");
    }

    private double controlVoltage(GeneratedBoardInstance instance) {
        return voltage(instance, "control-adapter", "J2.1") -
                voltage(instance, "control-adapter", "J2.2");
    }

    private double voltage(GeneratedBoardInstance instance, String block,
            String localPad) {
        String padId;
        if ("power-adapter".equals(block))
            padId = plan.getNamespace().idFor("power-adapter", EntityKind.PAD, localPad);
        else if ("control-adapter".equals(block))
            padId = plan.getNamespace().idFor("control-adapter", EntityKind.PAD, localPad);
        else
            padId = plan.idFor(block, EntityKind.PAD, localPad);
        CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings().getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Controlled endpoint has no CircuitJS post: " + padId);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return post.getElement().getPostVoltage(post.getPostIndex());
    }

    private ResistorElm resistor(GeneratedBoardInstance instance, String block,
            String localComponent) {
        CircuitElm element = instance.getComponentBindings().getSingleElement(
                plan.idFor(block, EntityKind.COMPONENT, localComponent));
        if (!(element instanceof ResistorElm))
            throw new IllegalStateException("Controlled component is not a resistor: " +
                    block + "/" + localComponent);
        return (ResistorElm) element;
    }

    private double loadRatedWatts(GeneratedBoardInstance instance) {
        String componentId = plan.idFor("load", EntityKind.COMPONENT, "RLOAD");
        PhysicalPart<?> installed = instance.getPhysicalBoardRuntime()
                .getInstalledPart(componentId);
        if (installed instanceof PhysicalResistorPart)
            return ((PhysicalResistorPart) installed).getNameplate().getRatedWattage();
        return plan.getResolvedLoadRecipe() == null ?
            ComposedBlockContribution.RATED_WATTS :
            plan.getResolvedLoadRecipe().getRatedWatts();
    }

    private NMosfetElm mosfet(GeneratedBoardInstance instance) {
        CircuitElm element = instance.getComponentBindings().getSingleElement(
                plan.idFor("driver", EntityKind.COMPONENT, "Q1"));
        if (!(element instanceof NMosfetElm))
            throw new IllegalStateException("Controlled driver is not an NMOS");
        return (NMosfetElm) element;
    }

    private LEDElm led(GeneratedBoardInstance instance) {
        CircuitElm element = instance.getComponentBindings().getSingleElement(
                plan.idFor("load", EntityKind.COMPONENT, "LED1"));
        if (!(element instanceof LEDElm))
            throw new IllegalStateException("Controlled load is not an LED");
        return (LEDElm) element;
    }

    private void setCommand(CirSim sim, boolean on) {
        boolean closed = controlCommand.position == 0;
        if (closed != on)
            controlCommand.toggle();
        if (sim != null) {
            sim.needAnalyze();
            sim.analyzeCircuit();
            sim.runCircuit(true);
            sim.runCircuit(true);
        }
    }

    private static void requireNearZero(double value, String message) {
        require(finite(value) && Math.abs(value) <= UNPOWERED_CURRENT, message + ": " + value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class FamilyState implements GeneratedBoardFamilyState {
        private final ControlledIndicatorDeviceBehavior behavior;
        private final SwitchElm controlCommand;
        private final GeneratedBoardOperationCatalog operations =
                new GeneratedBoardOperationCatalog();
        private final GeneratedCustomerRetestProfile retestProfile;
        private boolean commandedOn;

        FamilyState(final ControlledIndicatorDeviceBehavior behavior,
                SwitchElm controlCommand) {
            this.behavior = behavior;
            this.controlCommand = controlCommand;
            this.commandedOn = controlCommand.position == 0;
            operations.add(new GeneratedBoardOperation(
                    GeneratedBoardOperationIds.CONTROL_INPUT_HIGH,
                    "Set control HIGH", new GeneratedBoardOperation.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim,
                        GeneratedBoardInstance instance) {
                    applyCommandState(sim, true);
                    return null;
                }
            }));
            operations.add(new GeneratedBoardOperation(
                    GeneratedBoardOperationIds.CONTROL_INPUT_LOW,
                    "Set control LOW", new GeneratedBoardOperation.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim,
                        GeneratedBoardInstance instance) {
                    applyCommandState(sim, false);
                    return null;
                }
            }));
            retestProfile = new GeneratedCustomerRetestProfile(
                    "CONTROLLED_INDICATOR_CUSTOMER_RETEST",
                    "Set control HIGH and LOW and verify the indicator responds.",
                    "Both external board supplies powered during the functional check.",
                    "External control HIGH, then LOW.",
                    "Gate voltage, drain voltage, resistor current, and LED current.",
                    "One HIGH/LOW repetition after CircuitJS settles each command.",
                    "Both power adapters remain connected; physical parts are unchanged.",
                    new GeneratedCustomerRetestProfile.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim,
                        GeneratedBoardInstance instance) {
                    return retest(sim, instance);
                }
            });
            operations.add(new GeneratedBoardOperation(
                    GeneratedBoardOperationIds.CUSTOMER_RETEST,
                    "Retest Customer", new GeneratedBoardOperation.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim,
                        GeneratedBoardInstance instance) {
                    return retestProfile.execute(sim, instance);
                }
            }));
        }

        boolean isCommandedOn() { return commandedOn; }

        private void applyCommandState(CirSim sim, boolean on) {
            boolean closed = controlCommand.position == 0;
            if (closed != on) controlCommand.toggle();
            commandedOn = on;
            if (sim != null) {
                sim.needAnalyze();
                sim.analyzeCircuit();
                sim.runCircuit(true);
                sim.runCircuit(true);
            }
        }

        private GeneratedCustomerRetestResult retest(CirSim sim,
                GeneratedBoardInstance instance) {
            if (!GeneratedCustomerRetestSupport.isReadyForPoweredObservation(sim, instance))
                return GeneratedCustomerRetestSupport.failure();
            boolean priorCommand = commandedOn;
            BoardPowerState priorPower = sim.getBoardPowerController().getState();
            boolean priorPhysical = sim.getBoardModificationController().isFullyRestored();
            try {
                applyCommandState(sim, true);
                if (!behavior.isHealthyOn(instance))
                    return GeneratedCustomerRetestSupport.failure();
                applyCommandState(sim, false);
                if (!behavior.isHealthyOff(instance))
                    return GeneratedCustomerRetestSupport.failure();
                return GeneratedCustomerRetestSupport.success();
            } finally {
                try {
                    applyCommandState(sim, priorCommand);
                } finally {
                    GeneratedCustomerRetestSupport.restorePower(sim, priorPower);
                    if (priorPhysical != sim.getBoardModificationController().isFullyRestored())
                        throw new IllegalStateException("Customer retest changed physical board state");
                }
            }
        }

        public boolean isFaultedTargetInstalled(GeneratedBoardInstance instance,
                String componentId) {
            return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(instance, componentId);
        }

        public GeneratedBoardOperationCatalog getOperationCatalog() { return operations; }
        public GeneratedCustomerRetestProfile getCustomerRetestProfile() { return retestProfile; }
    }
}
