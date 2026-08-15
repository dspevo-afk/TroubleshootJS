package com.lushprojects.circuitjs1.client;

class CircuitMeasurementAdapter {
    private final CirSim sim;
    private final BoardPowerController boardPowerController;

    CircuitMeasurementAdapter(CirSim sim) {
        this.sim = sim;
        boardPowerController = sim.getBoardPowerController();
    }

    double measureDcVoltage(ProbeTarget redProbe, ProbeTarget blackProbe) {
        if (redProbe == null || blackProbe == null || !redProbe.isValid() || !blackProbe.isValid())
            return Double.NaN;
        CircuitMeasurementEndpoint red = redProbe.getMeasurementEndpoint();
        CircuitMeasurementEndpoint black = blackProbe.getMeasurementEndpoint();
        if (!(red instanceof CircuitPostMeasurementEndpoint) ||
                !(black instanceof CircuitPostMeasurementEndpoint))
            return Double.NaN;
        return sim.measureDcVoltage((CircuitPostMeasurementEndpoint) red,
            (CircuitPostMeasurementEndpoint) black);
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

    double measureResistance(ProbeTarget redProbe, ProbeTarget blackProbe) {
        if (!isActiveMeasurementAllowed(redProbe, blackProbe))
            return Double.NaN;
        ActiveMeasurementSession session = new ActiveMeasurementSession(redProbe, blackProbe);
        try {
            if (!session.hasValidTargets())
                return Double.NaN;
            if (!(session.getRedEndpoint() instanceof CircuitPostMeasurementEndpoint) ||
                    !(session.getBlackEndpoint() instanceof CircuitPostMeasurementEndpoint))
                return Double.NaN;
            return sim.measureResistance((CircuitPostMeasurementEndpoint) session.getRedEndpoint(),
                (CircuitPostMeasurementEndpoint) session.getBlackEndpoint());
        } finally {
            session.close();
        }
    }

    DiodeMeasurementResult measureDiode(ProbeTarget redProbe, ProbeTarget blackProbe) {
        if (!isActiveMeasurementAllowed(redProbe, blackProbe))
            return null;
        ActiveMeasurementSession session = new ActiveMeasurementSession(redProbe, blackProbe);
        try {
            if (!session.hasValidTargets() ||
                    !(session.getRedEndpoint() instanceof CircuitPostMeasurementEndpoint) ||
                    !(session.getBlackEndpoint() instanceof CircuitPostMeasurementEndpoint))
                return null;
            return sim.measureDiode((CircuitPostMeasurementEndpoint) session.getRedEndpoint(),
                (CircuitPostMeasurementEndpoint) session.getBlackEndpoint());
        } finally {
            session.close();
        }
    }

    // Future test sources must be owned, reanalyzed, and removed by this adapter/session boundary.
}

interface ActiveMeasurementOperation {
    double measure(ActiveMeasurementSession session);
}