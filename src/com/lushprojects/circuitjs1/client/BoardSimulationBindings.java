package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class BoardSimulationBindings {
    private final TroubleshootBoard board;
    private final HashMap<String, CircuitMeasurementEndpoint> padEndpoints =
        new HashMap<String, CircuitMeasurementEndpoint>();

    BoardSimulationBindings(TroubleshootBoard board) {
        this.board = board;
    }

    void bindPad(String padId, CircuitMeasurementEndpoint endpoint) {
        if (board.getPad(padId) == null)
            throw new IllegalArgumentException("Unknown board pad: " + padId);
        if (endpoint == null)
            throw new IllegalArgumentException("Missing measurement endpoint for pad: " + padId);
        if (padEndpoints.containsKey(padId))
            throw new IllegalArgumentException("Duplicate simulation binding for pad: " + padId);
        padEndpoints.put(padId, endpoint);
    }

    CircuitMeasurementEndpoint getEndpoint(String padId) {
        return padEndpoints.get(padId);
    }

    Vector<CircuitMeasurementEndpoint> getEndpointsForNet(String netId) {
        BoardNet net = board.getNet(netId);
        if (net == null)
            throw new IllegalArgumentException("Unknown board net: " + netId);
        Vector<CircuitMeasurementEndpoint> endpoints = new Vector<CircuitMeasurementEndpoint>();
        for (String padId : net.getPadIds()) {
            CircuitMeasurementEndpoint endpoint = padEndpoints.get(padId);
            if (endpoint != null)
                endpoints.add(endpoint);
        }
        return endpoints;
    }
}
