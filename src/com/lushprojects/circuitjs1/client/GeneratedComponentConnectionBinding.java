package com.lushprojects.circuitjs1.client;

class GeneratedComponentConnectionBinding {
    private final String componentId;
    private final String padId;
    private final CircuitMeasurementEndpoint boardEndpoint;
    /** Original generated endpoint retained while the slot is temporarily empty. */
    private final CircuitMeasurementEndpoint canonicalComponentEndpoint;
    private CircuitMeasurementEndpoint componentEndpoint;
    private final CircuitElm connectionElement;

    GeneratedComponentConnectionBinding(String componentId, String padId,
            CircuitMeasurementEndpoint boardEndpoint,
            CircuitMeasurementEndpoint componentEndpoint, CircuitElm connectionElement) {
        this.componentId = componentId;
        this.padId = padId;
        this.boardEndpoint = boardEndpoint;
        this.canonicalComponentEndpoint = componentEndpoint;
        this.componentEndpoint = componentEndpoint;
        this.connectionElement = connectionElement;
    }

    String getComponentId() { return componentId; }
    String getPadId() { return padId; }
    CircuitMeasurementEndpoint getBoardEndpoint() { return boardEndpoint; }
    CircuitMeasurementEndpoint getComponentEndpoint() { return componentEndpoint; }
    void setComponentEndpoint(CircuitMeasurementEndpoint endpoint) {
	if (endpoint == null)
	    throw new IllegalArgumentException("Missing component endpoint");
	componentEndpoint = endpoint;
    }
    void restoreCanonicalComponentEndpoint() {
        componentEndpoint = canonicalComponentEndpoint;
    }
    CircuitElm getConnectionElement() { return connectionElement; }
}
