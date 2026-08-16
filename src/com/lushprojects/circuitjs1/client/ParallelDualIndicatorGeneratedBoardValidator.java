package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class ParallelDualIndicatorGeneratedBoardValidator implements GeneratedBoardValidator {
    private static final double MIN_BRANCH_CURRENT = .002;
    private static final double MAX_BRANCH_CURRENT = .020;

    public void verify(GeneratedBoardInstance instance, BoardPowerState powerState) {
        ResistorElm r1 = resistor(instance, "R1");
        ResistorElm r2 = resistor(instance, "R2");
        LEDElm led1 = led(instance, "LED1");
        LEDElm led2 = led(instance, "LED2");
        DCVoltageElm source = source(instance);
        if (powerState == BoardPowerState.UNPOWERED) {
            require(Math.abs(r1.getCurrent()) < 0.000001 && Math.abs(r2.getCurrent()) < 0.000001 &&
                Math.abs(led1.getCurrent()) < 0.000001 && Math.abs(led2.getCurrent()) < 0.000001,
                "Unpowered parallel indicator current is not zero");
            return;
        }
        verifyHealthyCurrents(r1, r2, led1, led2, source);
        verifyBranchVoltageSums(instance, r1, r2, led1, led2);
    }

    static void verifyHealthyCurrents(ResistorElm r1, ResistorElm r2, LEDElm led1,
            LEDElm led2, DCVoltageElm source) {
        double branch1 = r1.getCurrent();
        double branch2 = r2.getCurrent();
        require(branch1 >= MIN_BRANCH_CURRENT && branch1 <= MAX_BRANCH_CURRENT,
            "Parallel branch 1 current is outside the healthy range: " + branch1);
        require(branch2 >= MIN_BRANCH_CURRENT && branch2 <= MAX_BRANCH_CURRENT,
            "Parallel branch 2 current is outside the healthy range: " + branch2);
        require(led1.getCurrent() >= MIN_BRANCH_CURRENT &&
            led1.getCurrent() <= MAX_BRANCH_CURRENT &&
            led2.getCurrent() >= MIN_BRANCH_CURRENT && led2.getCurrent() <= MAX_BRANCH_CURRENT,
            "Parallel LED current is outside the healthy range");
        require(branch1 > branch2, "Lower-value parallel branch did not carry more current: branch1=" +
            branch1 + " branch2=" + branch2);
        require(Math.abs(branch1 - led1.getCurrent()) <= .0001 &&
            Math.abs(branch2 - led2.getCurrent()) <= .0001,
            "Parallel branch resistor and LED currents do not match");

        // CircuitJS reports a voltage-source current into its positive terminal.
        // The source delivers the intentional opposite direction into the shared VIN node.
        double deliveredSourceCurrent = source.getCurrent();
        require(deliveredSourceCurrent > 0,
            "Voltage-source current direction was not normalized as source delivery: " +
            source.getCurrent());
        require(Math.abs(deliveredSourceCurrent - (branch1 + branch2)) <= .0001,
            "Parallel KCL failed at VIN: source=" + deliveredSourceCurrent + " branch1=" +
            branch1 + " branch2=" + branch2);
    }

    static void verifyBranchVoltageSums(GeneratedBoardInstance instance, ResistorElm r1,
            ResistorElm r2, LEDElm led1, LEDElm led2) {
        double supply = voltage(instance, "J1.1") - voltage(instance, "J1.2");
        require(Math.abs((r1.getPostVoltage(0) - r1.getPostVoltage(1)) +
                (led1.getPostVoltage(0) - led1.getPostVoltage(1)) - supply) <= .01,
            "Parallel branch 1 voltage sum does not equal supply");
        require(Math.abs((r2.getPostVoltage(0) - r2.getPostVoltage(1)) +
                (led2.getPostVoltage(0) - led2.getPostVoltage(1)) - supply) <= .01,
            "Parallel branch 2 voltage sum does not equal supply");
        require(Math.abs(voltage(instance, "J1.1") - voltage(instance, "R1.1")) <= .001 &&
            Math.abs(voltage(instance, "J1.1") - voltage(instance, "R2.1")) <= .001,
            "Parallel VIN pads do not resolve to one electrical node");
        require(Math.abs(voltage(instance, "J1.2") - voltage(instance, "LED1.K")) <= .001 &&
            Math.abs(voltage(instance, "J1.2") - voltage(instance, "LED2.K")) <= .001,
            "Parallel GND pads do not resolve to one electrical node");
    }

    static double voltage(GeneratedBoardInstance instance, String padId) {
        CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)
            instance.getSimulationBindings().getEndpoint(padId);
        return endpoint.getElement().getPostVoltage(endpoint.getPostIndex());
    }

    static ResistorElm resistor(GeneratedBoardInstance instance, String id) {
        CircuitElm element = instance.getComponentBindings().getSingleElement(id);
        require(element instanceof ResistorElm, id + " is not a ResistorElm");
        return (ResistorElm) element;
    }

    static LEDElm led(GeneratedBoardInstance instance, String id) {
        CircuitElm element = instance.getComponentBindings().getSingleElement(id);
        require(element instanceof LEDElm, id + " is not a LEDElm");
        return (LEDElm) element;
    }

    static DCVoltageElm source(GeneratedBoardInstance instance) {
        Vector<CircuitElm> elements = instance.getExternalPowerBindings().getBinding("VIN_INPUT")
            .getBackingElements();
        for (CircuitElm element : elements)
            if (element instanceof DCVoltageElm)
                return (DCVoltageElm) element;
        throw new IllegalStateException("Parallel family has no DC source");
    }

    static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
