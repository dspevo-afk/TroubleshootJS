package com.lushprojects.circuitjs1.client;

class PhysicalDiodePartProbeTarget implements ProbeTarget {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final String partId;
    private final int terminal;
    private final PcbWorkbenchRenderer renderer;

    PhysicalDiodePartProbeTarget(CirSim sim, GeneratedBoardInstance instance, String partId,
            int terminal, PcbWorkbenchRenderer renderer) {
        this.sim = sim;
        this.instance = instance;
        this.partId = partId;
        this.terminal = terminal;
        this.renderer = renderer;
    }

    public boolean isValid() {
        return sim.getGeneratedBoardInstance() == instance &&
            DiodeProtectedIndicatorFamilyState.require(instance).getInventory().get(partId)
                .getLocation() == DiodePartLocation.LOOSE;
    }

    public boolean isSameTarget(ProbeTarget other) {
        if (!(other instanceof PhysicalDiodePartProbeTarget))
            return false;
        PhysicalDiodePartProbeTarget target = (PhysicalDiodePartProbeTarget) other;
        return instance == target.instance && partId.equals(target.partId) && terminal == target.terminal;
    }

    public Point getMarkerPoint() { return renderer.getLooseDiodeLeadPoint(partId, terminal); }
    public CircuitMeasurementEndpoint getMeasurementEndpoint() {
        return DiodeProtectedIndicatorFamilyState.require(instance).getInventory().get(partId)
            .getTerminal(terminal);
    }
}
