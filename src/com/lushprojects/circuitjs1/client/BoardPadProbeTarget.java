package com.lushprojects.circuitjs1.client;

class BoardPadProbeTarget implements ProbeTarget {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final String padId;
    private final PcbWorkbenchRenderer renderer;

    BoardPadProbeTarget(CirSim sim, GeneratedBoardInstance instance, String padId,
            PcbWorkbenchRenderer renderer) {
        this.sim = sim;
        this.instance = instance;
        this.padId = padId;
        this.renderer = renderer;
    }

    public boolean isValid() {
        return sim.getGeneratedBoardInstance() == instance &&
            instance.getBoard().getPad(padId) != null && renderer.hasPad(padId) &&
            instance.getSimulationBindings().getEndpoint(padId) != null;
    }

    public boolean isSameTarget(ProbeTarget other) {
        if (!(other instanceof BoardPadProbeTarget))
            return false;
        BoardPadProbeTarget target = (BoardPadProbeTarget) other;
        return instance == target.instance && padId.equals(target.padId);
    }

    public Point getMarkerPoint() { return renderer.getPadPoint(padId); }
    public CircuitMeasurementEndpoint getMeasurementEndpoint() {
        return instance.getSimulationBindings().getEndpoint(padId);
    }

    String getPadId() { return padId; }
}