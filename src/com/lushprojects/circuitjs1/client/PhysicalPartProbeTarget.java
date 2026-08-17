package com.lushprojects.circuitjs1.client;

/** Generic probe target used by providers without a family-specific probe class. */
class PhysicalPartProbeTarget implements ProbeTarget {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final String partId;
    private final int terminal;
    private final PcbWorkbenchRenderer renderer;

    PhysicalPartProbeTarget(CirSim sim, GeneratedBoardInstance instance, String partId,
            int terminal, PcbWorkbenchRenderer renderer) {
        this.sim = sim;
        this.instance = instance;
        this.partId = partId;
        this.terminal = terminal;
        this.renderer = renderer;
    }

    public boolean isValid() {
        PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getPart(partId);
        return sim.getGeneratedBoardInstance() == instance && part != null && !part.isInstalled();
    }

    public boolean isSameTarget(ProbeTarget other) {
        if (!(other instanceof PhysicalPartProbeTarget))
            return false;
        PhysicalPartProbeTarget target = (PhysicalPartProbeTarget) other;
        return instance == target.instance && partId.equals(target.partId) &&
            terminal == target.terminal;
    }

    public Point getMarkerPoint() { return renderer.getLooseTerminalPoint(partId, terminal); }

    public CircuitMeasurementEndpoint getMeasurementEndpoint() {
        PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getPart(partId);
        if (part == null)
            throw new IllegalStateException("Unknown physical probe part: " + partId);
        return part.getTerminal(terminal).getEndpoint();
    }
}
