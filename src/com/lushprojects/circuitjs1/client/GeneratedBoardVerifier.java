package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class GeneratedBoardVerifier {
    private static final double NET_TOLERANCE = .001;
    private static final double MIN_LED_CURRENT = .005;
    private static final double MAX_LED_CURRENT = .015;

    static void verify(GeneratedBoardInstance instance) {
        TroubleshootBoard board = instance.getBoard();
        BoardSimulationBindings bindings = instance.getSimulationBindings();
        for (String padId : board.getPadIds()) {
            if (bindings.getEndpoint(padId) == null)
                throw new IllegalStateException("Missing simulation binding for board pad: " + padId);
        }
        for (String netId : board.getNetIds())
            verifyNetVoltage(bindings.getEndpointsForNet(netId), netId);

        double ledCurrent = instance.getLed().getCurrent();
        double resistorCurrent = instance.getResistor().getCurrent();
        if (ledCurrent < MIN_LED_CURRENT || ledCurrent > MAX_LED_CURRENT)
            throw new IllegalStateException("LED current outside generated range: " + ledCurrent);
        if (Math.abs(ledCurrent - resistorCurrent) > .0001)
            throw new IllegalStateException("Resistor and LED currents do not match");
    }

    private static void verifyNetVoltage(Vector<CircuitMeasurementEndpoint> endpoints,
            String netId) {
        if (endpoints.isEmpty())
            throw new IllegalStateException("Missing endpoints for board net: " + netId);
        double referenceVoltage = getVoltage(endpoints.firstElement());
        for (CircuitMeasurementEndpoint endpoint : endpoints) {
            if (Math.abs(getVoltage(endpoint) - referenceVoltage) > NET_TOLERANCE)
                throw new IllegalStateException("Inconsistent voltage on board net: " + netId);
        }
    }

    private static double getVoltage(CircuitMeasurementEndpoint endpoint) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported generated measurement endpoint");
        CircuitPostMeasurementEndpoint circuitPost =
            (CircuitPostMeasurementEndpoint) endpoint;
        return circuitPost.getElement().getPostVoltage(circuitPost.getPostIndex());
    }
}
