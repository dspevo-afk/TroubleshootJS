package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class GeneratedBoardVerifier {
    private static final double NET_TOLERANCE = .001;

    static void verify(GeneratedBoardInstance instance) {
        TroubleshootBoard board = instance.getBoard();
        BoardSimulationBindings bindings = instance.getSimulationBindings();
        instance.getComponentBindings().validateElementsAreOwnedBy(instance.getSimulationElements());
        instance.getExternalPowerBindings().validateElementsAreOwnedBy(instance.getSimulationElements());
        for (String padId : board.getPadIds()) {
            CircuitMeasurementEndpoint endpoint = bindings.getEndpoint(padId);
            if (endpoint == null)
                throw new IllegalStateException("Missing simulation binding for board pad: " + padId);
	    verifyEndpointIsOwned(endpoint, instance.getSimulationElements(), padId);
        }
        for (String netId : board.getNetIds())
            verifyNetVoltage(bindings.getEndpointsForNet(netId), netId);
        if (instance.getFamilyValidator() != null)
            instance.getFamilyValidator().verify(instance);
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

    private static void verifyEndpointIsOwned(CircuitMeasurementEndpoint endpoint,
            Vector<CircuitElm> simulationElements, String padId) {
    if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
        throw new IllegalStateException("Unsupported generated measurement endpoint: " + padId);
    CircuitPostMeasurementEndpoint circuitPost =
        (CircuitPostMeasurementEndpoint) endpoint;
    CircuitElm element = circuitPost.getElement();
    if (!simulationElements.contains(element))
        throw new IllegalStateException("Pad binding is not owned by generated board: " + padId);
    if (circuitPost.getPostIndex() < 0 || circuitPost.getPostIndex() >= element.getPostCount())
        throw new IllegalStateException("Pad binding has an invalid post index: " + padId);
    }
}
