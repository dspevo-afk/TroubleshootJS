package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Device-owned behavior for the bounded two-block resistive canary.
 *
 * <p>The block contributions supply local predicates.  This class supplies
 * the one device-level assertion: a five volt source must drive the two
 * actual resistor bodies as a series divider.  All readings are taken from
 * the CircuitJS elements retained by the generated board instance.</p>
 */
final class ComposedResistiveDeviceBehavior implements GeneratedChallengeBehaviorContract {
    static final String FAMILY_ID = "RESISTIVE_COUPLING";
    static final String TOPOLOGY_VARIANT_ID = "SERIES_SOURCE_LOAD";
    static final String POWER_INPUT_ID = "VIN_INPUT";
    static final double SUPPLY_VOLTAGE = 5.0;
    static final double MIN_CURRENT_AMPS = ComposedBlockContribution.MIN_HEALTHY_CURRENT_AMPS;
    static final double MAX_CURRENT_AMPS = ComposedBlockContribution.MAX_OPERATING_CURRENT_AMPS;

    private static final double UNPOWERED_CURRENT_TOLERANCE = 1.0e-6;
    private static final double CURRENT_TOLERANCE = 1.0e-6;
    private static final double OUTPUT_TOLERANCE = 0.002;
    /** Declared device output range from the source/load port contracts. */
    private static final double MIN_OUTPUT_VOLTS = 3.9;
    private static final double MAX_OUTPUT_VOLTS = 5.0;

    private final BoundedAssemblyPlan plan;

    ComposedResistiveDeviceBehavior(BoundedAssemblyPlan plan) {
        if (plan == null)
            throw new IllegalArgumentException("Missing composed resistive assembly plan");
        this.plan = plan;
    }

    BoundedAssemblyPlan getPlan() { return plan; }

    @Override
    public void verifyHealthy(GeneratedBoardInstance instance, BoardPowerState powerState) {
        if (instance == null || powerState == null)
            throw new IllegalArgumentException("Missing composed healthy verification context");
        if (powerState == BoardPowerState.UNPOWERED) {
            requireNearZero(current(instance, "source"),
                "Unpowered source resistor current is not zero");
            requireNearZero(current(instance, "load"),
                "Unpowered load resistor current is not zero");
            return;
        }

        for (String block : new String[] { "source", "load" }) {
            ComposedBlockContribution contribution = plan.getBlocks().get(block);
            if (contribution == null)
                throw new IllegalStateException("Composed plan has no " + block + " contribution");
            contribution.verifyHealthy(new LocalObservation(instance, block));
        }
        verifyHealthyDivider(instance);
    }

    @Override
    public void verifyFaulted(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        if (instance == null || powerState == null)
            throw new IllegalArgumentException("Missing composed fault verification context");
        if (powerState == BoardPowerState.UNPOWERED) {
            requireNearZero(current(instance, "source"),
                "Unpowered source resistor current is not zero during fault verification");
            requireNearZero(current(instance, "load"),
                "Unpowered load resistor current is not zero during fault verification");
            return;
        }

        for (String block : new String[] { "source", "load" }) {
            ComposedBlockContribution contribution = plan.getBlocks().get(block);
            if (contribution.observe(new LocalObservation(instance, block)) !=
                    ComposedBlockContribution.ObservationResult.LOW_CURRENT)
                throw new IllegalStateException("Faulted " + block +
                    " contribution did not exhibit low measured current");
        }

        String faultBlock = plan.getFaultBlockKey();
        double output = outputVoltage(instance);
        double loadCurrent = Math.abs(current(instance, "load"));
        requireFinite(output, "Faulted output voltage");
        requireFinite(loadCurrent, "Faulted load current");
        if (loadCurrent >= 0.0001)
            throw new IllegalStateException("Faulted load current is not insufficient: " +
                loadCurrent);
        if ("source".equals(faultBlock)) {
            if (output >= 0.12)
                throw new IllegalStateException("Source fault did not lower device output: " +
                    output);
        } else if ("load".equals(faultBlock)) {
            if (output <= 4.98)
                throw new IllegalStateException("Load fault did not leave source output high: " +
                    output);
        } else {
            throw new IllegalStateException("Unknown composed fault block: " + faultBlock);
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

        double sourceResistance = resistance(instance, "source");
        double loadResistance = resistance(instance, "load");
        double sourceCurrent = Math.abs(current(instance, "source"));
        double loadCurrent = Math.abs(current(instance, "load"));
        double output = outputVoltage(instance);
        if (!finitePositive(sourceResistance) || !finitePositive(loadResistance) ||
                !finite(sourceCurrent) || !finite(loadCurrent) || !finite(output))
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        if (sourceCurrent < MIN_CURRENT_AMPS || loadCurrent < MIN_CURRENT_AMPS)
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;

        double expectedCurrent = SUPPLY_VOLTAGE / (sourceResistance + loadResistance);
        double expectedOutput = expectedCurrent * loadResistance;
        if (close(sourceCurrent, expectedCurrent, CURRENT_TOLERANCE) &&
                close(loadCurrent, expectedCurrent, CURRENT_TOLERANCE) &&
                close(output, expectedOutput, OUTPUT_TOLERANCE) &&
                withinFunctionalOutputRange(output) &&
                withinFunctionalOutputRange(expectedOutput) &&
                expectedCurrent <= MAX_CURRENT_AMPS + CURRENT_TOLERANCE)
            return GeneratedRepairStatus.CORRECTLY_RESTORED;
        return GeneratedRepairStatus.DEGRADED_BUT_OPERATING;
    }

    @Override
    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        return getRepairStatus(instance, modifications, powerState, activeMeasurementOverlay) ==
            GeneratedRepairStatus.CORRECTLY_RESTORED;
    }

    /** Device-owned family state used by the single generated challenge. */
    GeneratedBoardFamilyState createFamilyState() {
        return new FamilyState(this);
    }

    /** Device-owned complaint scenarios; compatibility is measured, not fault-ID based. */
    GeneratedScenarioCatalog<GeneratedObservedBehavior> createScenarioCatalog() {
        Vector<GeneratedScenario<GeneratedObservedBehavior>> scenarios =
            new Vector<GeneratedScenario<GeneratedObservedBehavior>>();
        scenarios.add(new GeneratedScenario<GeneratedObservedBehavior>(
            "RESISTIVE_TRANSFER_LOW_OUTPUT", "RESISTIVE_TRANSFER_COMPLAINT",
            "The downstream output is too weak under the customer load.",
            GeneratedObservedBehavior.RESISTIVE_TRANSFER_LOW_OUTPUT,
            new GeneratedScenarioCompatibility<GeneratedObservedBehavior>() {
                public boolean matches(GeneratedBoardInstance instance,
                        BoardModificationController modifications,
                        BoardPowerState powerState, GeneratedObservedBehavior behavior) {
                    return powerState == BoardPowerState.POWERED &&
                        outputIsLow(instance);
                }
            }));
        scenarios.add(new GeneratedScenario<GeneratedObservedBehavior>(
            "RESISTIVE_TRANSFER_LOW_CURRENT", "RESISTIVE_TRANSFER_COMPLAINT",
            "The transfer path carries too little current for the customer load.",
            GeneratedObservedBehavior.RESISTIVE_TRANSFER_LOW_CURRENT,
            new GeneratedScenarioCompatibility<GeneratedObservedBehavior>() {
                public boolean matches(GeneratedBoardInstance instance,
                        BoardModificationController modifications,
                        BoardPowerState powerState, GeneratedObservedBehavior behavior) {
                    return powerState == BoardPowerState.POWERED &&
                        loadCurrentIsLow(instance);
                }
            }));
        return new GeneratedScenarioCatalog<GeneratedObservedBehavior>(scenarios, this);
    }

    private void verifyHealthyDivider(GeneratedBoardInstance instance) {
        double sourceResistance = resistance(instance, "source");
        double loadResistance = resistance(instance, "load");
        double expectedCurrent = SUPPLY_VOLTAGE / (sourceResistance + loadResistance);
        double expectedOutput = expectedCurrent * loadResistance;
        double sourceCurrent = Math.abs(current(instance, "source"));
        double loadCurrent = Math.abs(current(instance, "load"));
        double output = outputVoltage(instance);
        require(finitePositive(sourceResistance) && finitePositive(loadResistance),
            "Healthy resistor values are invalid");
        require(expectedCurrent >= MIN_CURRENT_AMPS &&
            expectedCurrent <= MAX_CURRENT_AMPS + CURRENT_TOLERANCE,
            "Healthy divider current is outside the bounded operating range: " +
            expectedCurrent);
        require(close(sourceCurrent, expectedCurrent, CURRENT_TOLERANCE) &&
            close(loadCurrent, expectedCurrent, CURRENT_TOLERANCE),
            "Healthy divider currents do not agree with the actual resistor values");
        require(close(output, expectedOutput, OUTPUT_TOLERANCE),
            "Healthy divider output does not agree with the actual resistor values");
        require(withinFunctionalOutputRange(output) &&
            withinFunctionalOutputRange(expectedOutput),
            "Healthy divider output is outside the declared device range: " + output);
    }

    private ResistorElm resistor(GeneratedBoardInstance instance, String block) {
        String componentId = plan.idFor(block, FunctionalBlockDescriptor.EntityKind.COMPONENT, "R1");
        CircuitElm element = instance.getComponentBindings().getSingleElement(componentId);
        if (!(element instanceof ResistorElm))
            throw new IllegalStateException("Composed component is not a resistor: " + componentId);
        return (ResistorElm) element;
    }

    private double current(GeneratedBoardInstance instance, String block) {
        return resistor(instance, block).getCurrent();
    }

    private double resistance(GeneratedBoardInstance instance, String block) {
        return resistor(instance, block).getResistance();
    }

    private double outputVoltage(GeneratedBoardInstance instance) {
        return voltage(instance, plan.idFor("load", FunctionalBlockDescriptor.EntityKind.PAD,
            "R1.1")) - voltage(instance, plan.idFor("load",
            FunctionalBlockDescriptor.EntityKind.PAD, "R1.2"));
    }

    private double voltage(GeneratedBoardInstance instance, String padId) {
        CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings().getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Composed board pad has no CircuitJS post: " + padId);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return post.getElement().getPostVoltage(post.getPostIndex());
    }

    private boolean outputIsLow(GeneratedBoardInstance instance) {
        try {
            CircuitMeasurementEndpoint output = instance.getSimulationBindings().getEndpoint(
                plan.idFor("load", FunctionalBlockDescriptor.EntityKind.PAD, "R1.1"));
            CircuitMeasurementEndpoint returned = instance.getSimulationBindings().getEndpoint(
                plan.idFor("load", FunctionalBlockDescriptor.EntityKind.PAD, "R1.2"));
            if (!(output instanceof CircuitPostMeasurementEndpoint) ||
                    !(returned instanceof CircuitPostMeasurementEndpoint)) return false;
            CircuitPostMeasurementEndpoint a = (CircuitPostMeasurementEndpoint) output;
            CircuitPostMeasurementEndpoint b = (CircuitPostMeasurementEndpoint) returned;
            return finite(a.getElement().getPostVoltage(a.getPostIndex())) &&
                finite(b.getElement().getPostVoltage(b.getPostIndex())) &&
                Math.abs(a.getElement().getPostVoltage(a.getPostIndex()) -
                    b.getElement().getPostVoltage(b.getPostIndex())) < 0.12;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private boolean loadCurrentIsLow(GeneratedBoardInstance instance) {
        try {
            String id = plan.idFor("load", FunctionalBlockDescriptor.EntityKind.COMPONENT, "R1");
            CircuitElm element = instance.getComponentBindings().getSingleElement(id);
            return element instanceof ResistorElm && finite(element.getCurrent()) &&
                Math.abs(element.getCurrent()) < 0.0001;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static void requireNearZero(double value, String message) {
        require(finite(value) && Math.abs(value) <= UNPOWERED_CURRENT_TOLERANCE, message +
            ": " + value);
    }

    private static void requireFinite(double value, String message) {
        require(finite(value), message + ": " + value);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }

    private static boolean close(double actual, double expected, double tolerance) {
        return finite(actual) && finite(expected) && Math.abs(actual - expected) <= tolerance;
    }

    private static boolean finitePositive(double value) { return finite(value) && value > 0.0; }
    private static boolean withinFunctionalOutputRange(double value) {
        return finite(value) && value >= MIN_OUTPUT_VOLTS - OUTPUT_TOLERANCE &&
            value <= MAX_OUTPUT_VOLTS + OUTPUT_TOLERANCE;
    }
    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private final class LocalObservation implements ComposedBlockContribution.Observation {
        private final GeneratedBoardInstance instance;
        private final String block;

        LocalObservation(GeneratedBoardInstance instance, String block) {
            this.instance = instance;
            this.block = block;
        }

        public double voltage(String endpointId) {
            if (!"R1_1".equals(endpointId) && !"R1_2".equals(endpointId))
                throw new IllegalArgumentException("Unknown composed local endpoint: " + endpointId);
            int terminal = "R1_1".equals(endpointId) ? 1 : 2;
            String pad = plan.idFor(block, FunctionalBlockDescriptor.EntityKind.PAD, "R1." + terminal);
            CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings().getEndpoint(pad);
            if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                throw new IllegalStateException("Composed local endpoint has no CircuitJS post: " + pad);
            CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
            return post.getElement().getPostVoltage(post.getPostIndex());
        }

        public double current(String componentId) {
            if (!"R1".equals(componentId))
                throw new IllegalArgumentException("Unknown composed local component: " + componentId);
            String qualified = plan.idFor(block, FunctionalBlockDescriptor.EntityKind.COMPONENT, "R1");
            CircuitElm element = instance.getComponentBindings().getSingleElement(qualified);
            if (!(element instanceof ResistorElm))
                throw new IllegalStateException("Composed local component is not a resistor");
            return element.getCurrent();
        }

        public double resistance(String componentId) {
            if (!"R1".equals(componentId))
                throw new IllegalArgumentException("Unknown composed local component: " + componentId);
            String qualified = plan.idFor(block, FunctionalBlockDescriptor.EntityKind.COMPONENT, "R1");
            CircuitElm element = instance.getComponentBindings().getSingleElement(qualified);
            if (!(element instanceof ResistorElm))
                throw new IllegalStateException("Composed local component is not a resistor");
            return ((ResistorElm) element).getResistance();
        }
    }

    private static final class FamilyState implements GeneratedBoardFamilyState {
        private final ComposedResistiveDeviceBehavior behavior;
        private final GeneratedBoardOperationCatalog operations =
            new GeneratedBoardOperationCatalog();
        private final GeneratedCustomerRetestProfile retestProfile;

        FamilyState(final ComposedResistiveDeviceBehavior behavior) {
            this.behavior = behavior;
            retestProfile = GeneratedCustomerRetestProfiles.observation(
                "RESISTIVE_COUPLING_CUSTOMER_RETEST",
                "Observe the downstream output after repair.",
                "Power the board for a steady-state observation.",
                "No external input transition.",
                "The downstream resistive load receives the expected transfer.",
                "Steady state; one observation.",
                "Both source and load blocks remain part of the transfer path.");
            operations.add(new GeneratedBoardOperation(GeneratedBoardOperationIds.CUSTOMER_RETEST,
                "Retest Customer", new GeneratedBoardOperation.Executor() {
                public GeneratedCustomerRetestResult execute(CirSim sim,
                        GeneratedBoardInstance instance) {
                    return retestProfile.execute(sim, instance);
                }
            }));
        }

        public void requireOwnedBy(GeneratedBoardInstance instance) {
            if (behavior != instance.getBehaviorContract())
                throw new IllegalArgumentException("Fresh family state captures a foreign behavior owner");
        }

        public boolean isFaultedTargetInstalled(GeneratedBoardInstance instance,
                String componentId) {
            return GeneratedBoardFamilyPolicy.isFaultedTargetInstalled(instance, componentId);
        }

        public GeneratedBoardOperationCatalog getOperationCatalog() { return operations; }
        public GeneratedCustomerRetestProfile getCustomerRetestProfile() { return retestProfile; }
    }
}
