package com.lushprojects.circuitjs1.client;

class ComponentLeadProbeTarget implements ProbeTarget {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final String componentId;
    private final String padId;
    private final PcbWorkbenchRenderer renderer;
    private final String physicalPartId;
    private final CircuitMeasurementEndpoint endpoint;
    private final Object lifecycleIdentity;

    ComponentLeadProbeTarget(CirSim sim, GeneratedBoardInstance instance, String componentId,
            String padId, PcbWorkbenchRenderer renderer) {
        this(sim, instance, componentId, padId, renderer, getInstalledPartId(instance, componentId),
            instance.getConnectionBindings().get(componentId, padId).getComponentEndpoint(),
            renderer == null ? null : renderer.captureInstalledTargetIdentity(componentId, padId));
    }

    ComponentLeadProbeTarget(CirSim sim, GeneratedBoardInstance instance, String componentId,
            String padId, PcbWorkbenchRenderer renderer, String physicalPartId,
            CircuitMeasurementEndpoint endpoint) {
        this(sim, instance, componentId, padId, renderer, physicalPartId, endpoint,
            renderer == null ? null : renderer.captureInstalledTargetIdentity(componentId, padId));
    }

    ComponentLeadProbeTarget(CirSim sim, GeneratedBoardInstance instance, String componentId,
            String padId, PcbWorkbenchRenderer renderer, String physicalPartId,
            CircuitMeasurementEndpoint endpoint, Object lifecycleIdentity) {
        this.sim = sim;
        this.instance = instance;
        this.componentId = componentId;
        this.padId = padId;
        this.renderer = renderer;
        this.physicalPartId = physicalPartId;
        this.endpoint = resolvePhysicalEndpoint(instance, componentId, padId, endpoint);
        this.lifecycleIdentity = lifecycleIdentity;
    }

    public boolean isValid() {
        if (sim == null || sim.getGeneratedBoardInstance() != instance || renderer == null ||
                lifecycleIdentity == null || endpoint == null || physicalPartId == null ||
                !renderer.isInstalledTargetIdentityCurrent(componentId, padId,
                    lifecycleIdentity))
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
                    !part.isInstalled() || slot.getInstalledPart() != part ||
                    part.getBoardSlot() != slot ||
                    !physicalPartId.equals(part.getId()) ||
                    !component.getPhysicalPackage().isEquivalentTo(part.getPackage()) ||
                    !sameEndpoint(binding.getComponentEndpoint(), endpoint) ||
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
            padId.equals(target.padId) && physicalPartId.equals(target.physicalPartId) &&
            (lifecycleIdentity == null || target.lifecycleIdentity == null ||
                lifecycleIdentity == target.lifecycleIdentity);
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
                    sameEndpoint(terminal.getEndpoint(), endpoint))
                return true;
        return false;
    }

    private static CircuitMeasurementEndpoint resolvePhysicalEndpoint(
            GeneratedBoardInstance instance, String componentId, String padId,
            CircuitMeasurementEndpoint fallback) {
        if (instance == null || componentId == null || padId == null)
            return fallback;
        BoardPad boardPad = instance.getBoard().getPad(padId);
        PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
        if (boardPad == null || part == null)
            return fallback;
        for (PhysicalPartTerminal terminal : part.getTerminals())
            if (boardPad.getTerminalId() != null &&
                    boardPad.getTerminalId().equals(terminal.getTerminalName()))
                return terminal.getEndpoint();
        return fallback;
    }

    private static boolean sameEndpoint(CircuitMeasurementEndpoint first,
            CircuitMeasurementEndpoint second) {
        if (first == second)
            return true;
        if (!(first instanceof CircuitPostMeasurementEndpoint) ||
                !(second instanceof CircuitPostMeasurementEndpoint))
            return false;
        CircuitPostMeasurementEndpoint a = (CircuitPostMeasurementEndpoint) first;
        CircuitPostMeasurementEndpoint b = (CircuitPostMeasurementEndpoint) second;
        return a.getElement() == b.getElement() && a.getPostIndex() == b.getPostIndex();
    }

    private static String getInstalledPartId(GeneratedBoardInstance instance, String componentId) {
        PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
        if (part != null)
            return part.getId();
        throw new IllegalArgumentException("Component lead is not installed: " + componentId);
    }
}
