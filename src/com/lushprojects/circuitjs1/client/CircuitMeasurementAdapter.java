package com.lushprojects.circuitjs1.client;

class CircuitMeasurementAdapter {
    private final CirSim sim;

    CircuitMeasurementAdapter(CirSim sim) {
        this.sim = sim;
    }

    double getDcVoltageDifference(ProbeTarget redProbe, ProbeTarget blackProbe) {
        return getDcPotential(redProbe) - getDcPotential(blackProbe);
    }

    private double getDcPotential(ProbeTarget probe) {
        CircuitMeasurementEndpoint endpoint = probe.getMeasurementEndpoint();
        if (endpoint instanceof CircuitPostMeasurementEndpoint) {
            CircuitPostMeasurementEndpoint circuitPost =
                (CircuitPostMeasurementEndpoint) endpoint;
            return circuitPost.getElement().getPostVoltage(circuitPost.getPostIndex());
        }
        return 0;
    }

    // Future active measurements will temporarily modify and restore the simulation graph here.
}