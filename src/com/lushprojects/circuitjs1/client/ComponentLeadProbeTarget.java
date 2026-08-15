package com.lushprojects.circuitjs1.client;

class ComponentLeadProbeTarget implements ProbeTarget {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final String componentId;
    private final String padId;
    private final PcbWorkbenchRenderer renderer;
    private final String physicalPartId;
    private final CircuitMeasurementEndpoint endpoint;

    ComponentLeadProbeTarget(CirSim sim, GeneratedBoardInstance instance, String componentId,
            String padId, PcbWorkbenchRenderer renderer) {
        this(sim, instance, componentId, padId, renderer, getInstalledPartId(instance, componentId),
            instance.getConnectionBindings().get(componentId, padId).getComponentEndpoint());
    }

    ComponentLeadProbeTarget(CirSim sim, GeneratedBoardInstance instance, String componentId,
            String padId, PcbWorkbenchRenderer renderer, String physicalPartId,
            CircuitMeasurementEndpoint endpoint) {
        this.sim = sim;
        this.instance = instance;
        this.componentId = componentId;
        this.padId = padId;
        this.renderer = renderer;
        this.physicalPartId = physicalPartId;
        this.endpoint = endpoint;
    }

    public boolean isValid() {
        if (sim.getGeneratedBoardInstance() != instance)
            return false;
        try {
            return isSelectedPhysicalPartStillInstalled() &&
                sim.getBoardModificationController().getComponentState(componentId) !=
                    ComponentPhysicalState.INSTALLED &&
                renderer.getComponentLeadPoint(componentId, padId) != null &&
                endpoint != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isSameTarget(ProbeTarget other) {
        if (!(other instanceof ComponentLeadProbeTarget))
            return false;
        ComponentLeadProbeTarget target = (ComponentLeadProbeTarget) other;
        return instance == target.instance && componentId.equals(target.componentId) &&
            padId.equals(target.padId) && physicalPartId.equals(target.physicalPartId);
    }

    public Point getMarkerPoint() { return renderer.getComponentLeadPoint(componentId, padId); }
    public CircuitMeasurementEndpoint getMeasurementEndpoint() {
        return endpoint;
    }

    String getPhysicalPartIdForDeveloperVerification() { return physicalPartId; }
    String getComponentIdForDeveloperVerification() { return componentId; }
    String getPadIdForDeveloperVerification() { return padId; }

    private boolean isSelectedPhysicalPartStillInstalled() {
        if (!"R1".equals(componentId) || LedIndicatorFamilyState.require(instance).getR1Slot().isEmpty())
            return false;
        PhysicalResistorPart part = LedIndicatorFamilyState.require(instance).getR1Slot().getInstalledPart();
        return physicalPartId.equals(part.getId()) && part.getLocation() ==
            ResistorPartLocation.INSTALLED;
    }

    private static String getInstalledPartId(GeneratedBoardInstance instance, String componentId) {
        if (!"R1".equals(componentId) || LedIndicatorFamilyState.require(instance).getR1Slot().isEmpty())
            throw new IllegalArgumentException("Component lead is not installed: " + componentId);
        return LedIndicatorFamilyState.require(instance).getR1Slot().getInstalledPart().getId();
    }
}