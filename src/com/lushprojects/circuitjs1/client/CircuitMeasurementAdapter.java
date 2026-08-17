package com.lushprojects.circuitjs1.client;

class CircuitMeasurementAdapter implements CircuitMeasurementBoundary {
    private final CirSim sim;
    private final BoardPowerController boardPowerController;

    CircuitMeasurementAdapter(CirSim sim) {
        if (sim == null)
            throw new IllegalArgumentException("Missing CircuitJS simulation");
        this.sim = sim;
        boardPowerController = sim.getBoardPowerController();
    }

    public double measureDcVoltage(ProbeTarget redProbe, ProbeTarget blackProbe) {
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

    public boolean isActiveMeasurementAllowed(ProbeTarget redProbe, ProbeTarget blackProbe) {
        return boardPowerController.isElectricallyUnpowered() &&
            redProbe != null && blackProbe != null &&
            redProbe.isValid() && blackProbe.isValid();
    }

    public double measureResistance(ProbeTarget redProbe, ProbeTarget blackProbe) {
        if (!isActiveMeasurementAllowed(redProbe, blackProbe))
            return Double.NaN;
        ActiveMeasurementSession session = new ActiveMeasurementSession(redProbe, blackProbe);
        try {
            if (!session.hasValidTargets())
                return Double.NaN;
            if (!(session.getRedEndpoint() instanceof CircuitPostMeasurementEndpoint) ||
                    !(session.getBlackEndpoint() instanceof CircuitPostMeasurementEndpoint))
                return Double.NaN;
            return sim.measureResistance(
                (CircuitPostMeasurementEndpoint) session.getRedEndpoint(),
                (CircuitPostMeasurementEndpoint) session.getBlackEndpoint());
        } finally {
            session.close();
        }
    }

    public DiodeMeasurementResult measureDiode(ProbeTarget redProbe, ProbeTarget blackProbe) {
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
}
