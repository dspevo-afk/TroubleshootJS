package com.lushprojects.circuitjs1.client;

class PhysicalResistorPartProbeTarget implements ProbeTarget {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final String partId;
    private final int terminal;
    private final PcbWorkbenchRenderer renderer;

    PhysicalResistorPartProbeTarget(CirSim sim, GeneratedBoardInstance instance, String partId,
            int terminal, PcbWorkbenchRenderer renderer) {
        this.sim = sim;
        this.instance = instance;
        this.partId = partId;
        this.terminal = terminal;
        this.renderer = renderer;
    }

    public boolean isValid() {
        if (sim.getGeneratedBoardInstance() != instance ||
                !(instance.getFamilyState() instanceof ReplaceableResistorFamilyState))
            return false;
        return ((ReplaceableResistorFamilyState) instance.getFamilyState()).getResistorInventory()
            .get(partId).getLocation() == ResistorPartLocation.LOOSE;
    }

    public boolean isSameTarget(ProbeTarget other) {
        if (!(other instanceof PhysicalResistorPartProbeTarget))
            return false;
        PhysicalResistorPartProbeTarget target = (PhysicalResistorPartProbeTarget) other;
        return instance == target.instance && partId.equals(target.partId) && terminal == target.terminal;
    }

    public Point getMarkerPoint() { return renderer.getLoosePartLeadPoint(partId, terminal); }
    public CircuitMeasurementEndpoint getMeasurementEndpoint() {
        PhysicalResistorPart part = ((ReplaceableResistorFamilyState) instance.getFamilyState())
            .getResistorInventory().get(partId);
        return part.getPublicTerminal(terminal);
    }
}
