package com.lushprojects.circuitjs1.client;

/** Developer-only installed target used to verify provider probe resolution. */
final class PhysicalPartRenderCanaryProbeTarget implements ProbeTarget {
    private final CirSim sim;
    private final PhysicalPartRenderContext context;
    private final int terminal;

    PhysicalPartRenderCanaryProbeTarget(CirSim sim, PhysicalPartRenderContext context,
            int terminal) {
        this.sim = sim;
        this.context = context;
        this.terminal = terminal;
    }

    public boolean isValid() {
        return sim != null && context.isDeveloperCanary() && context.getPart() != null &&
            context.getPart().isInstalled() && terminal >= 0 &&
            terminal < context.getPart().getTerminalCount();
    }

    public boolean isSameTarget(ProbeTarget other) {
        if (!(other instanceof PhysicalPartRenderCanaryProbeTarget))
            return false;
        PhysicalPartRenderCanaryProbeTarget target =
            (PhysicalPartRenderCanaryProbeTarget) other;
        return context == target.context && terminal == target.terminal;
    }

    public Point getMarkerPoint() { return context.getProviderTerminalPoint(terminal); }
    public CircuitMeasurementEndpoint getMeasurementEndpoint() {
        return context.getPart().getTerminal(terminal).getEndpoint();
    }

    String getPartIdForDeveloperVerification() { return context.getPart().getId(); }
    int getTerminalIndexForDeveloperVerification() { return terminal; }
    String getTerminalIdForDeveloperVerification() {
        return context.getPart().getTerminal(terminal).getTerminalName();
    }
    String getBoardPadIdForDeveloperVerification() { return context.getBoardPadId(terminal); }
}
