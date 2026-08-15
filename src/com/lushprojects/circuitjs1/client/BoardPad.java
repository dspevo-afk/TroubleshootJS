package com.lushprojects.circuitjs1.client;

class BoardPad {
    private final String id;
    private final String componentId;
    private final String terminalId;
    private final String netId;

    BoardPad(String id, String componentId, String terminalId, String netId) {
        this.id = id;
        this.componentId = componentId;
        this.terminalId = terminalId;
        this.netId = netId;
    }

    String getId() {
        return id;
    }

    String getComponentId() {
        return componentId;
    }

    String getTerminalId() {
        return terminalId;
    }

    String getNetId() {
        return netId;
    }
}
