package com.lushprojects.circuitjs1.client;

class ComponentLeadProbeTarget implements ProbeTarget {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final String componentId;
    private final String padId;
    private final PcbWorkbenchRenderer renderer;

    ComponentLeadProbeTarget(CirSim sim, GeneratedBoardInstance instance, String componentId,
            String padId, PcbWorkbenchRenderer renderer) {
        this.sim = sim;
        this.instance = instance;
        this.componentId = componentId;
        this.padId = padId;
        this.renderer = renderer;
    }

    public boolean isValid() {
        if (sim.getGeneratedBoardInstance() != instance)
            return false;
        try {
            return sim.getBoardModificationController().getComponentState(componentId) !=
                    ComponentPhysicalState.INSTALLED &&
                renderer.getComponentLeadPoint(componentId, padId) != null &&
                instance.getConnectionBindings().get(componentId, padId).getComponentEndpoint() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isSameTarget(ProbeTarget other) {
        if (!(other instanceof ComponentLeadProbeTarget))
            return false;
        ComponentLeadProbeTarget target = (ComponentLeadProbeTarget) other;
        return instance == target.instance && componentId.equals(target.componentId) &&
            padId.equals(target.padId);
    }

    public Point getMarkerPoint() { return renderer.getComponentLeadPoint(componentId, padId); }
    public CircuitMeasurementEndpoint getMeasurementEndpoint() {
        return instance.getConnectionBindings().get(componentId, padId).getComponentEndpoint();
    }
}