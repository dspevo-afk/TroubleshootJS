package com.lushprojects.circuitjs1.client;

class GeneratedComponentConnectionBinding {
    private final String componentId;
    private final String padId;
    private final CircuitMeasurementEndpoint boardEndpoint;
    private final CircuitMeasurementEndpoint componentEndpoint;
    private final CircuitElm connectionElement;

    GeneratedComponentConnectionBinding(String componentId, String padId,
            CircuitMeasurementEndpoint boardEndpoint,
            CircuitMeasurementEndpoint componentEndpoint, CircuitElm connectionElement) {
        this.componentId = componentId;
        this.padId = padId;
        this.boardEndpoint = boardEndpoint;
        this.componentEndpoint = componentEndpoint;
        this.connectionElement = connectionElement;
    }

    String getComponentId() { return componentId; }
    String getPadId() { return padId; }
    CircuitMeasurementEndpoint getBoardEndpoint() { return boardEndpoint; }
    CircuitMeasurementEndpoint getComponentEndpoint() { return componentEndpoint; }
    CircuitElm getConnectionElement() { return connectionElement; }
}