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

    /**
     * Resolves an exposed CircuitJS post back to its logical board net.  A
     * physical board can expose one net from several backing elements, so
     * policy code must not assume a chosen connector post is the only public
     * representation of that net.
     */
    String getNetIdForEndpoint(CircuitPostMeasurementEndpoint endpoint) {
        if (endpoint == null)
            return null;
        String netId = null;
        for (String padId : board.getPadIds()) {
            CircuitMeasurementEndpoint candidate = padEndpoints.get(padId);
            if (!(candidate instanceof CircuitPostMeasurementEndpoint) ||
                    !sameEndpoint(endpoint, (CircuitPostMeasurementEndpoint) candidate))
                continue;
            String candidateNetId = board.getPad(padId).getNetId();
            if (netId != null && !netId.equals(candidateNetId))
                throw new IllegalStateException("Board endpoint is bound to multiple nets");
            netId = candidateNetId;
        }
        return netId;
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

    private boolean sameEndpoint(CircuitPostMeasurementEndpoint first,
            CircuitPostMeasurementEndpoint second) {
        return first.getElement() == second.getElement() &&
            first.getPostIndex() == second.getPostIndex();
    }
}
