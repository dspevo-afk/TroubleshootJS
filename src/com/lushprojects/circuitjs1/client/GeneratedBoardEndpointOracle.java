package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/**
 * Immutable developer-verifier view of the board endpoints captured at the
 * generated-board composition boundary.  This is deliberately separate from
 * the live BoardSimulationBindings lookup map: verifier experiments may
 * redirect later lookups, but they cannot rewrite this retained producer
 * output.  It carries no repository or source-control provenance.
 */
final class GeneratedBoardEndpointOracle {
    private final HashMap<String, CircuitMeasurementEndpoint> endpoints;

    private GeneratedBoardEndpointOracle(
            HashMap<String, CircuitMeasurementEndpoint> capturedEndpoints) {
        endpoints = new HashMap<String, CircuitMeasurementEndpoint>(capturedEndpoints);
    }

    static GeneratedBoardEndpointOracle fromGeneratedBoardBindings(
            HashMap<String, CircuitMeasurementEndpoint> capturedEndpoints) {
        if (capturedEndpoints == null)
            throw new IllegalArgumentException("Missing generated board endpoint capture");
        return new GeneratedBoardEndpointOracle(capturedEndpoints);
    }

    CircuitMeasurementEndpoint getEndpoint(String padId) {
        return endpoints.get(padId);
    }

    Vector<String> getPadIds() {
        Vector<String> result = new Vector<String>();
        for (String padId : endpoints.keySet())
            result.add(padId);
        return result;
    }
}
