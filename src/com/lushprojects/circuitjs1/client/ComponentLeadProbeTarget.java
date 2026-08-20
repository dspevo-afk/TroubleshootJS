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
        if (sim == null || sim.getGeneratedBoardInstance() != instance || endpoint == null ||
                physicalPartId == null)
            return false;
        try {
            BoardComponent component = instance.getBoard().getComponent(componentId);
            BoardPad boardPad = instance.getBoard().getPad(padId);
            GeneratedComponentConnectionBinding binding = instance.getConnectionBindings()
                .get(componentId, padId);
            PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
            PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime().getSlot(componentId);
            if (component == null || boardPad == null || binding == null || part == null ||
                    slot == null || boardPad.getComponentId() == null ||
                    !componentId.equals(component.getId()) ||
                    !componentId.equals(boardPad.getComponentId()) ||
                    !componentId.equals(binding.getComponentId()) ||
                    !padId.equals(binding.getPadId()) ||
                    !part.isInstalled() || part.getBoardSlot() != slot ||
                    !physicalPartId.equals(part.getId()) ||
                    !component.getPhysicalPackage().isEquivalentTo(part.getPackage()) ||
                    binding.getComponentEndpoint() != endpoint ||
                    !padId.equals(boardPad.getId()) ||
                    !hasStablePartTerminal(part, boardPad.getTerminalId(), endpoint) ||
                    sim.getBoardModificationController().isLeadConnected(componentId, padId) ||
                    sim.getBoardModificationController().getComponentState(componentId) ==
                        ComponentPhysicalState.INSTALLED ||
                    renderer.getComponentLeadPoint(componentId, padId) == null)
                return false;
            return true;
        } catch (RuntimeException e) {
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

    private static boolean hasStablePartTerminal(PhysicalPart<?> part, String terminalId,
            CircuitMeasurementEndpoint endpoint) {
        for (PhysicalPartTerminal terminal : part.getTerminals())
            if (terminalId != null && terminalId.equals(terminal.getTerminalName()) &&
                    terminal.getEndpoint() == endpoint)
                return true;
        return false;
    }

    private static String getInstalledPartId(GeneratedBoardInstance instance, String componentId) {
        PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
        if (part != null)
            return part.getId();
        throw new IllegalArgumentException("Component lead is not installed: " + componentId);
    }
}
