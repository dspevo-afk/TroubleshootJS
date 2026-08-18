package com.lushprojects.circuitjs1.client;

/** Structural and live-solver proof for the real NMOS low-side topology. */
final class NmosLowSideSwitchGeneratedBoardValidator implements GeneratedBoardValidator {
    private static final double MIN_LOAD_CURRENT = .008;

    public void verify(GeneratedBoardInstance instance, BoardPowerState powerState) {
        require(instance, "RLOAD", ResistorElm.class);
        require(instance, "RPD", ResistorElm.class);
        require(instance, "LED1", LEDElm.class);
        require(instance, "Q1", NMosfetElm.class);
        if (instance.getComponentBindings().getSingleElement("Q1").getPostCount() != 3)
            throw new IllegalStateException("NMOS binding exposed a body terminal");
        requirePadNet(instance, "J1.1", "LOAD_SUPPLY");
        requirePadNet(instance, "J2.1", "CONTROL_INPUT");
        requirePadNet(instance, "TP1.1", "GATE_DRIVE");
        requirePadNet(instance, "RPD.1", "GATE");
        requirePadNet(instance, "Q1.G", "GATE");
        requirePadNet(instance, "Q1.D", "DRAIN");
        requirePadNet(instance, "Q1.S", "GND");
        if (powerState == BoardPowerState.UNPOWERED) {
            if (Math.abs(loadCurrent(instance)) > .000001 ||
                    Math.abs(gatePullDown(instance).getCurrent()) > .000001)
                throw new IllegalStateException("Unpowered NMOS board has current");
            return;
        }
        if (!isHealthyOn(instance))
            throw new IllegalStateException("Healthy NMOS board does not solve to load-on behavior");
    }

    static boolean isHealthyOn(GeneratedBoardInstance instance) {
        double load = loadCurrent(instance);
        double mosfet = mosfetCurrent(instance);
        double led = Math.abs(led(instance).getCurrent());
        double vgs = gateSourceVoltage(instance);
        double vds = drainSourceVoltage(instance);
        double supply = voltage(instance, "J1.1") - voltage(instance, "J1.2");
        double control = voltage(instance, "J2.1") - voltage(instance, "J2.2");
        return load >= MIN_LOAD_CURRENT && mosfet >= MIN_LOAD_CURRENT && led >= .005 &&
            Math.abs(load - led) < .0005 && Math.abs(load - mosfet) < .002 &&
            vgs > 3 && vds < 1.0 && supply > 4 && control > 3 && gateCurrent(instance) < 1e-9;
    }

    static boolean isHealthyOff(GeneratedBoardInstance instance) {
        double supply = voltage(instance, "J1.1") - voltage(instance, "J1.2");
        return Math.abs(loadCurrent(instance)) < .000001 && gateSourceVoltage(instance) < .1 &&
            drainSourceVoltage(instance) > supply - 1.0 && gateCurrent(instance) < 1e-9;
    }

    static double loadCurrent(GeneratedBoardInstance instance) {
        return Math.abs(loadResistor(instance).getCurrent());
    }
    static double mosfetCurrent(GeneratedBoardInstance instance) {
        return Math.abs(mosfet(instance).getCurrent());
    }
    static double gateCurrent(GeneratedBoardInstance instance) {
        // NMosfetElm reports its actual solver terminal current; CircuitJS's
        // MOSFET gate is an ideal high-impedance terminal, not a fake reading.
        return Math.abs(mosfet(instance).getCurrentIntoNode(0));
    }
    static double gatePullDownCurrent(GeneratedBoardInstance instance) {
        return Math.abs(gatePullDown(instance).getCurrent());
    }
    static double gateSourceVoltage(GeneratedBoardInstance instance) {
        NMosfetElm q = mosfet(instance);
        return q.getPostVoltage(0) - q.getPostVoltage(1);
    }
    static double internalGateVoltage(GeneratedBoardInstance instance) {
        return mosfet(instance).getPostVoltage(0) - mosfet(instance).getPostVoltage(1);
    }
    static double boardGateVoltage(GeneratedBoardInstance instance) {
        return voltage(instance, "Q1.G") - voltage(instance, "Q1.S");
    }
    static double drainSourceVoltage(GeneratedBoardInstance instance) {
        NMosfetElm q = mosfet(instance);
        return q.getPostVoltage(2) - q.getPostVoltage(1);
    }
    static double drainVoltage(GeneratedBoardInstance instance) {
        return voltage(instance, "Q1.D") - voltage(instance, "Q1.S");
    }
    static double voltage(GeneratedBoardInstance instance, String padId) {
        CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings().getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Missing NMOS voltage endpoint: " + padId);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return post.getElement().getPostVoltage(post.getPostIndex());
    }
    private static ResistorElm loadResistor(GeneratedBoardInstance instance) {
        return (ResistorElm) instance.getComponentBindings().getSingleElement("RLOAD");
    }
    private static ResistorElm gatePullDown(GeneratedBoardInstance instance) {
        return (ResistorElm) instance.getComponentBindings().getSingleElement("RPD");
    }
    private static LEDElm led(GeneratedBoardInstance instance) {
        return (LEDElm) instance.getComponentBindings().getSingleElement("LED1");
    }
    private static NMosfetElm mosfet(GeneratedBoardInstance instance) {
        return (NMosfetElm) instance.getComponentBindings().getSingleElement("Q1");
    }
    private static void require(GeneratedBoardInstance instance, String componentId,
            Class<?> expected) {
        CircuitElm element = instance.getComponentBindings().getSingleElement(componentId);
        if (element.getClass() != expected)
            throw new IllegalStateException("NMOS component is not " + expected.getName() + ": " +
                componentId);
    }
    private void requirePadNet(GeneratedBoardInstance instance, String padId, String netId) {
        BoardPad pad = instance.getBoard().getPad(padId);
        if (pad == null || !netId.equals(pad.getNetId()))
            throw new IllegalStateException("NMOS topology mismatch at " + padId);
    }
}
