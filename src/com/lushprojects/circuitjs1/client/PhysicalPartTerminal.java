package com.lushprojects.circuitjs1.client;

/** A physical terminal and the current simulation endpoint behind it. */
final class PhysicalPartTerminal {
    private final String id;
    private final String terminalName;
    private final CircuitMeasurementEndpoint endpoint;

    PhysicalPartTerminal(String physicalPartId, String terminalName,
            CircuitMeasurementEndpoint endpoint) {
        if (physicalPartId == null || physicalPartId.length() == 0 ||
                terminalName == null || terminalName.length() == 0 || endpoint == null)
            throw new IllegalArgumentException("Invalid physical part terminal");
        this.id = physicalPartId + "." + terminalName;
        this.terminalName = terminalName;
        this.endpoint = endpoint;
    }

    String getId() { return id; }
    String getTerminalName() { return terminalName; }
    CircuitMeasurementEndpoint getEndpoint() { return endpoint; }
}
