package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class GeneratedBoardVerifier {
    private static final double NET_TOLERANCE = .001;

    static void verify(GeneratedBoardInstance instance, BoardPowerState powerState,
            BoardModificationController modifications, Vector<CircuitElm> activeElements,
            boolean verifyHealthyFamily) {
        TroubleshootBoard board = instance.getBoard();
        BoardSimulationBindings bindings = instance.getSimulationBindings();
        instance.getComponentBindings().validateElementsAreOwnedBy(instance.getSimulationElements());
        instance.getExternalPowerBindings().validateElementsAreOwnedBy(instance.getSimulationElements());
        if (modifications != null)
            modifications.verifyStructuralState();
        for (CircuitElm element : instance.getSimulationElements()) {
            if (!activeElements.contains(element) &&
                    !instance.getConnectionBindings().isConnectionElement(element))
                throw new IllegalStateException("Undeclared generated element missing from graph");
        }
        for (String padId : board.getPadIds()) {
            CircuitMeasurementEndpoint endpoint = bindings.getEndpoint(padId);
            if (endpoint == null)
                throw new IllegalStateException("Missing simulation binding for board pad: " + padId);
        verifyEndpointIsOwned(endpoint, activeElements, padId);
        }
        for (String netId : board.getNetIds())
            verifyNetVoltage(bindings.getEndpointsForNet(netId), netId);
        if (verifyHealthyFamily && instance.getFamilyValidator() != null &&
            (modifications == null || modifications.isFullyRestored()))
            instance.getFamilyValidator().verify(instance, powerState);
    }

    private static void verifyNetVoltage(Vector<CircuitMeasurementEndpoint> endpoints,
            String netId) {
        if (endpoints.isEmpty())
            throw new IllegalStateException("Missing endpoints for board net: " + netId);
        double referenceVoltage = getVoltage(endpoints.firstElement());
    if (!isFinite(referenceVoltage))
        throw new IllegalStateException("Non-finite voltage on board net: " + netId);
        for (CircuitMeasurementEndpoint endpoint : endpoints) {
        double voltage = getVoltage(endpoint);
        if (!isFinite(voltage))
        throw new IllegalStateException("Non-finite voltage on board net: " + netId);
        if (Math.abs(voltage - referenceVoltage) > NET_TOLERANCE)
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

    private static boolean isFinite(double value) {
	return !Double.isNaN(value) && !Double.isInfinite(value);
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
