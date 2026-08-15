package com.lushprojects.circuitjs1.client;

class ActiveMeasurementSession {
    private final ProbeTarget redProbe;
    private final ProbeTarget blackProbe;
    private boolean closed;

    ActiveMeasurementSession(ProbeTarget redProbe, ProbeTarget blackProbe) {
        this.redProbe = redProbe;
        this.blackProbe = blackProbe;
    }

    boolean hasValidTargets() {
        return !closed && redProbe.isValid() && blackProbe.isValid();
    }

    CircuitMeasurementEndpoint getRedEndpoint() {
        return redProbe.getMeasurementEndpoint();
    }

    CircuitMeasurementEndpoint getBlackEndpoint() {
        return blackProbe.getMeasurementEndpoint();
    }

    void close() {
        closed = true;
    }
}
