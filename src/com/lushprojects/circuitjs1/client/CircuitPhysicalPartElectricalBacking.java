package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Package-private adapter for CircuitJS elements/endpoints owned by one physical part. */
final class CircuitPhysicalPartElectricalBacking implements PhysicalPartElectricalBacking {
    private final Vector<CircuitMeasurementEndpoint> endpoints;
    private final Vector<CircuitElm> elements;

    CircuitPhysicalPartElectricalBacking(Vector<CircuitMeasurementEndpoint> endpoints,
            Vector<CircuitElm> elements) {
        if (endpoints == null || endpoints.size() < 1 || elements == null || elements.size() < 1)
            throw new IllegalArgumentException("Invalid physical electrical backing");
        this.endpoints = new Vector<CircuitMeasurementEndpoint>(endpoints);
        this.elements = new Vector<CircuitElm>(elements);
        for (CircuitMeasurementEndpoint endpoint : this.endpoints)
            if (endpoint == null)
                throw new IllegalArgumentException("Missing physical terminal endpoint");
        for (CircuitElm element : this.elements)
            if (element == null)
                throw new IllegalArgumentException("Missing physical backing element");
    }

    public int getTerminalCount() { return endpoints.size(); }
    public CircuitMeasurementEndpoint getTerminalEndpoint(int terminal) {
        if (terminal < 0 || terminal >= endpoints.size())
            throw new IllegalArgumentException("Invalid physical terminal: " + terminal);
        return endpoints.get(terminal);
    }
    public Vector<CircuitElm> getCircuitElements() { return new Vector<CircuitElm>(elements); }
}
