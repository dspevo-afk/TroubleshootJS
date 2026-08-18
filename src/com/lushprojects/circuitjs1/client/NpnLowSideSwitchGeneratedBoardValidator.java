package com.lushprojects.circuitjs1.client;

/** Structural and healthy-state proof for the real NPN low-side topology. */
final class NpnLowSideSwitchGeneratedBoardValidator implements GeneratedBoardValidator {
    private static final double MIN_LOAD_CURRENT = .008;

    public void verify(GeneratedBoardInstance instance, BoardPowerState powerState) {
        require(instance, "RLOAD", ResistorElm.class);
        require(instance, "RB", ResistorElm.class);
        require(instance, "RPD", ResistorElm.class);
        require(instance, "LED1", LEDElm.class);
        require(instance, "Q1", NTransistorElm.class);
        requirePadNet(instance, "J1.1", "LOAD_SUPPLY");
        requirePadNet(instance, "J2.1", "CONTROL_INPUT");
        requirePadNet(instance, "RB.2", "BASE");
        requirePadNet(instance, "Q1.C", "COLLECTOR");
        requirePadNet(instance, "Q1.E", "GND");
        if (powerState == BoardPowerState.UNPOWERED) {
            if (Math.abs(loadCurrent(instance)) > .000001 ||
                    Math.abs(baseCurrent(instance)) > .000001)
                throw new IllegalStateException("Unpowered NPN board has current");
            return;
        }
        if (!isHealthyOn(instance))
            throw new IllegalStateException("Healthy NPN board does not solve to load-on behavior");
    }

    static boolean isHealthyOn(GeneratedBoardInstance instance) {
        double load = loadCurrent(instance);
        double base = baseCurrent(instance);
        double collector = collectorCurrent(instance);
        double resistor = Math.abs(resistor(instance, "RLOAD").getCurrent());
        double led = Math.abs(led(instance).getCurrent());
        double supply = voltage(instance, "J1.1") - voltage(instance, "J1.2");
        double control = voltage(instance, "J2.1") - voltage(instance, "J2.2");
        return load >= MIN_LOAD_CURRENT && base > .00002 && collector >= .005 &&
            led >= .005 && Math.abs(load - resistor) < .0005 &&
            Math.abs(load - led) < .0005 && Math.abs(load - collector) < .002 &&
            supply > 4 && control > 3;
    }

    static boolean isHealthyOff(GeneratedBoardInstance instance) {
        return Math.abs(loadCurrent(instance)) < .000001 &&
            Math.abs(baseCurrent(instance)) < .000001;
    }

    static double loadCurrent(GeneratedBoardInstance instance) {
        return Math.abs(resistor(instance, "RLOAD").getCurrent());
    }

    static double baseCurrent(GeneratedBoardInstance instance) {
        return Math.abs(transistor(instance).ib);
    }

    static double collectorCurrent(GeneratedBoardInstance instance) {
        return Math.abs(transistor(instance).ic);
    }

    static double collectorVoltage(GeneratedBoardInstance instance) {
        return voltage(instance, "Q1.C") - voltage(instance, "Q1.E");
    }

    static double baseVoltage(GeneratedBoardInstance instance) {
        return voltage(instance, "Q1.B") - voltage(instance, "Q1.E");
    }

    static double voltage(GeneratedBoardInstance instance, String padId) {
        CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings().getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Missing NPN voltage endpoint: " + padId);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return post.getElement().getPostVoltage(post.getPostIndex());
    }

    private static ResistorElm resistor(GeneratedBoardInstance instance, String id) {
        return (ResistorElm) instance.getComponentBindings().getSingleElement(id);
    }

    private static LEDElm led(GeneratedBoardInstance instance) {
        return (LEDElm) instance.getComponentBindings().getSingleElement("LED1");
    }

    private static NTransistorElm transistor(GeneratedBoardInstance instance) {
        return (NTransistorElm) instance.getComponentBindings().getSingleElement("Q1");
    }

    private static void require(GeneratedBoardInstance instance, String componentId,
            Class<?> expected) {
        CircuitElm element = instance.getComponentBindings().getSingleElement(componentId);
        if (element.getClass() != expected)
            throw new IllegalStateException("NPN component is not " + expected.getName() + ": " +
                componentId);
    }

    private void requirePadNet(GeneratedBoardInstance instance, String padId, String netId) {
        BoardPad pad = instance.getBoard().getPad(padId);
        if (pad == null || !netId.equals(pad.getNetId()))
            throw new IllegalStateException("NPN topology mismatch at " + padId);
    }
}
