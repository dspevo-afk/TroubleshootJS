package com.lushprojects.circuitjs1.client;

/** Generic probe target used by providers without a family-specific probe class. */
class PhysicalPartProbeTarget implements ProbeTarget {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final String partId;
    private final int terminal;
    private final PcbWorkbenchRenderer renderer;
    private final Object looseProjectionToken;

    PhysicalPartProbeTarget(CirSim sim, GeneratedBoardInstance instance, String partId,
            int terminal, PcbWorkbenchRenderer renderer) {
        this.sim = sim;
        this.instance = instance;
        this.partId = partId;
        this.terminal = terminal;
        this.renderer = renderer;
        looseProjectionToken = renderer == null ? null : renderer.captureLooseProjectionToken();
    }

    public boolean isValid() {
        if (sim == null || instance == null || renderer == null || looseProjectionToken == null ||
                sim.getGeneratedBoardInstance() != instance ||
                !renderer.isLooseProjectionTokenCurrent(looseProjectionToken))
            return false;
        try {
            PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getPart(partId);
            return part != null && !part.isInstalled() && terminal >= 0 &&
                terminal < part.getTerminalCount() && part.getTerminal(terminal) != null &&
                renderer.isLoosePartVisibleOnCurrentPage(partId);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public boolean isSameTarget(ProbeTarget other) {
        if (!(other instanceof PhysicalPartProbeTarget))
            return false;
        PhysicalPartProbeTarget target = (PhysicalPartProbeTarget) other;
        return instance == target.instance && partId.equals(target.partId) &&
            terminal == target.terminal;
    }

    public Point getMarkerPoint() {
        if (!isValid())
            return null;
        Point point = renderer.getLooseTerminalPoint(partId, terminal);
        return isValid() ? point : null;
    }

    public CircuitMeasurementEndpoint getMeasurementEndpoint() {
        PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getPart(partId);
        if (part == null)
            throw new IllegalStateException("Unknown physical probe part: " + partId);
        return part.getTerminal(terminal).getEndpoint();
    }
}
