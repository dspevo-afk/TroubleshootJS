package com.lushprojects.circuitjs1.client;

class CircuitMeasurementAdapter {
    private final CirSim sim;
    private final BoardPowerController boardPowerController;

    CircuitMeasurementAdapter(CirSim sim) {
        this.sim = sim;
        boardPowerController = sim.getBoardPowerController();
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
        return Double.NaN;
    }

    boolean isActiveMeasurementAllowed(ProbeTarget redProbe, ProbeTarget blackProbe) {
    return boardPowerController.isElectricallyUnpowered() &&
            redProbe != null && blackProbe != null &&
            redProbe.isValid() && blackProbe.isValid();
    }

    double runActiveMeasurement(ProbeTarget redProbe, ProbeTarget blackProbe,
            ActiveMeasurementOperation operation) {
        if (!isActiveMeasurementAllowed(redProbe, blackProbe))
            return Double.NaN;

        ActiveMeasurementSession session = new ActiveMeasurementSession(redProbe, blackProbe);
        try {
            if (!session.hasValidTargets())
                return Double.NaN;
            return operation.measure(session);
        } finally {
            session.close();
        }
    }

    // Future test sources must be owned, reanalyzed, and removed by this adapter/session boundary.
}

interface ActiveMeasurementOperation {
    double measure(ActiveMeasurementSession session);
}