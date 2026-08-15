package com.lushprojects.circuitjs1.client;

class PhysicalLedPartProbeTarget implements ProbeTarget {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final String partId;
    private final int terminal;
    private final PcbWorkbenchRenderer renderer;

    PhysicalLedPartProbeTarget(CirSim sim, GeneratedBoardInstance instance, String partId,
            int terminal, PcbWorkbenchRenderer renderer) {
        this.sim = sim;
        this.instance = instance;
        this.partId = partId;
        this.terminal = terminal;
        this.renderer = renderer;
    }

    public boolean isValid() {
        return sim.getGeneratedBoardInstance() == instance &&
            LedIndicatorFamilyState.require(instance).getLedInventory().get(partId).getLocation() ==
                LedPartLocation.LOOSE;
    }

    public boolean isSameTarget(ProbeTarget other) {
        if (!(other instanceof PhysicalLedPartProbeTarget)) return false;
        PhysicalLedPartProbeTarget target = (PhysicalLedPartProbeTarget) other;
        return instance == target.instance && partId.equals(target.partId) && terminal == target.terminal;
    }

    public Point getMarkerPoint() { return renderer.getLooseLedLeadPoint(partId, terminal); }
    public CircuitMeasurementEndpoint getMeasurementEndpoint() {
        return LedIndicatorFamilyState.require(instance).getLedInventory().get(partId).getTerminal(terminal);
    }
}
