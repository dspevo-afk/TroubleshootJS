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
        if (instance.getFamilyState() instanceof ReplaceableResistorFamilyState) {
            ReplaceableResistorFamilyState state =
                (ReplaceableResistorFamilyState) instance.getFamilyState();
            ReplaceableComponentSlot slot = state.getReplaceableResistorSlot();
            if (slot.getComponentId().equals(componentId) && !slot.isEmpty()) {
                PhysicalResistorPart part = slot.getInstalledPart();
                return physicalPartId.equals(part.getId()) && part.getLocation() ==
                    ResistorPartLocation.INSTALLED;
            }
        }
        if (instance.getFamilyState() instanceof LedIndicatorFamilyState) {
            LedIndicatorFamilyState state = LedIndicatorFamilyState.require(instance);
            if ("R1".equals(componentId) && !state.getR1Slot().isEmpty()) {
                PhysicalResistorPart part = state.getR1Slot().getInstalledPart();
                return physicalPartId.equals(part.getId()) && part.getLocation() ==
                    ResistorPartLocation.INSTALLED;
            }
            if ("LED1".equals(componentId) && !state.getLed1Slot().isEmpty()) {
                PhysicalLedPart part = state.getLed1Slot().getInstalledPart();
                return physicalPartId.equals(part.getId()) && part.getLocation() ==
                    LedPartLocation.INSTALLED;
            }
            return false;
        }
        if (instance.getFamilyState() instanceof DiodeProtectedIndicatorFamilyState) {
            DiodeComponentSlot slot = DiodeProtectedIndicatorFamilyState.require(instance).getD1Slot();
            return "D1".equals(componentId) && !slot.isEmpty() &&
                physicalPartId.equals(slot.getInstalledPart().getId()) &&
                slot.getInstalledPart().getLocation() == DiodePartLocation.INSTALLED;
        }
        return false;
    }

    private static String getInstalledPartId(GeneratedBoardInstance instance, String componentId) {
        if (instance.getFamilyState() instanceof ReplaceableResistorFamilyState) {
            ReplaceableResistorFamilyState state =
                (ReplaceableResistorFamilyState) instance.getFamilyState();
            ReplaceableComponentSlot slot = state.getReplaceableResistorSlot();
            if (slot.getComponentId().equals(componentId) && !slot.isEmpty())
                return slot.getInstalledPart().getId();
        }
        if (instance.getFamilyState() instanceof LedIndicatorFamilyState && "R1".equals(componentId) &&
                !LedIndicatorFamilyState.require(instance).getR1Slot().isEmpty())
            return LedIndicatorFamilyState.require(instance).getR1Slot().getInstalledPart().getId();
        if (instance.getFamilyState() instanceof LedIndicatorFamilyState && "LED1".equals(componentId) &&
                !LedIndicatorFamilyState.require(instance).getLed1Slot().isEmpty())
            return LedIndicatorFamilyState.require(instance).getLed1Slot().getInstalledPart().getId();
        if (instance.getFamilyState() instanceof DiodeProtectedIndicatorFamilyState && "D1".equals(componentId) &&
                !DiodeProtectedIndicatorFamilyState.require(instance).getD1Slot().isEmpty())
            return DiodeProtectedIndicatorFamilyState.require(instance).getD1Slot().getInstalledPart().getId();
        throw new IllegalArgumentException("Component lead is not installed: " + componentId);
    }
}
