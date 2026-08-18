package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Stable physical capacitor identity with family-owned CircuitJS backing and fault ownership. */
final class PhysicalCapacitorPart implements PhysicalPart<CapacitorSpecification>,
        GeneratedFaultOwningPart {
    private final String id;
    private final CapacitorSpecification specification;
    private final PhysicalNameplate playerNameplate;
    private final CapacitorElm element;
    private final GeneratedFaultBinding faultBinding;
    private final PhysicalPartTerminal[] terminals;
    private final CircuitPhysicalPartElectricalBacking backing;
    private final PhysicalPartMountState mountState = new PhysicalPartMountState();
    private final PhysicalPartProvenance provenance;
    private final Vector<PhysicalPartCapability> capabilities =
        new Vector<PhysicalPartCapability>();

    PhysicalCapacitorPart(String id, CapacitorSpecification specification,
            PhysicalNameplate playerNameplate, CapacitorElm element,
            GeneratedFaultBinding faultBinding, CapacitorPartLocation location,
            PhysicalPartProvenance provenance) {
        if (id == null || id.length() == 0 || specification == null ||
                playerNameplate == null || element == null || location == null ||
                provenance == null)
            throw new IllegalArgumentException("Invalid physical capacitor part");
        if (!id.equals(playerNameplate.getId()))
            throw new IllegalArgumentException("Capacitor nameplate must identify its physical part");
        this.id = id;
        this.specification = specification;
        this.playerNameplate = playerNameplate;
        this.element = element;
        this.faultBinding = faultBinding;
        this.provenance = provenance;
        CircuitMeasurementEndpoint first = faultBinding == null ?
            new CircuitPostMeasurementEndpoint(element, 0) :
            faultBinding.getPublicTerminal(element, 0);
        CircuitMeasurementEndpoint second = faultBinding == null ?
            new CircuitPostMeasurementEndpoint(element, 1) :
            faultBinding.getPublicTerminal(element, 1);
        Vector<String> terminalIds = specification.getPhysicalPackage().getTerminalIds();
        if (terminalIds.size() != 2)
            throw new IllegalArgumentException("Capacitor package must have two terminals");
        terminals = new PhysicalPartTerminal[] {
            new PhysicalPartTerminal(id, terminalIds.get(0), first),
            new PhysicalPartTerminal(id, terminalIds.get(1), second)
        };
        Vector<CircuitMeasurementEndpoint> endpoints = new Vector<CircuitMeasurementEndpoint>();
        endpoints.add(first);
        endpoints.add(second);
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(element);
        if (faultBinding != null)
            elements.addAll(faultBinding.getPrivateSimulationElements());
        backing = new CircuitPhysicalPartElectricalBacking(endpoints, elements);
        capabilities.add(new LoosePartInspectableCapability());
        capabilities.add(new RatedPartCapability(specification.getVoltageRating()));
    }

    public String getId() { return id; }
    public CapacitorSpecification getSpecification() { return specification; }
    public PhysicalNameplate getPlayerVisibleNameplate() { return playerNameplate; }
    public PhysicalPartRenderMetadata getRenderMetadata() {
        return new PhysicalPartRenderMetadata(specification,
            specification.isPolarized() ? PhysicalPartOrientation.NORMAL :
                PhysicalPartOrientation.NON_POLARIZED,
            PhysicalPartRenderProbeProviders.CAPACITOR);
    }
    public PhysicalPartOrientation getOrientation() { return getRenderMetadata().getOrientation(); }
    public PhysicalPackage getPackage() { return specification.getPhysicalPackage(); }
    public int getTerminalCount() { return terminals.length; }
    public PhysicalPartTerminal getTerminal(int terminal) {
        if (terminal < 0 || terminal >= terminals.length)
            throw new IllegalArgumentException("Invalid capacitor terminal: " + terminal);
        return terminals[terminal];
    }
    public Vector<PhysicalPartTerminal> getTerminals() {
        Vector<PhysicalPartTerminal> result = new Vector<PhysicalPartTerminal>();
        result.add(terminals[0]);
        result.add(terminals[1]);
        return result;
    }
    public PhysicalPartElectricalBacking getElectricalBacking() { return backing; }
    public PhysicalPartMountState getMountState() { return mountState; }
    public PhysicalBoardSlot getBoardSlot() { return mountState.getSlot(); }
    public PhysicalPartProvenance getProvenance() { return provenance; }
    public PhysicalFailureState getFailureState() {
        return faultBinding != null && faultBinding.isApplied() ?
            new PhysicalFailureState(PhysicalFailureState.GENERATED_FAULT, true) :
            new PhysicalFailureState(PhysicalFailureState.HEALTHY, false);
    }
    public Vector<PhysicalPartCapability> getCapabilities() {
        return new Vector<PhysicalPartCapability>(capabilities);
    }
    public Vector<PhysicalPartCapability> getIntrinsicCapabilities() { return getCapabilities(); }
    public boolean isInstalled() { return mountState.isInstalled(); }
    public boolean isOriginal() { return provenance.isOriginal(); }
    public boolean isFaulted() { return getFailureState().isFailed(); }
    public boolean ownsGeneratedFault(GeneratedFaultBinding binding) {
        return faultBinding != null && faultBinding == binding;
    }

    CapacitorNameplate getNameplate() { return specification.getNameplate(); }
    CapacitorElm getElement() { return element; }
    GeneratedFaultBinding getFaultBinding() { return faultBinding; }
    /**
     * Stored charge is player-relevant only when both physical terminals still
     * reach the underlying capacitor.  An internally open original part may
     * retain charge on its isolated element, but that charge is not exposed
     * at the loose or installed board terminals.
     */
    boolean hasAccessibleStoredEnergyTerminals() {
        return terminalConnectsToBacking(0) && terminalConnectsToBacking(1);
    }
    CircuitMeasurementEndpoint getPublicTerminal(int terminal) { return getTerminal(terminal).getEndpoint(); }
    CircuitMeasurementEndpoint getTerminalForBoardPad(String padId) {
        if (padId == null)
            throw new IllegalArgumentException("Missing capacitor board pad");
        for (PhysicalPartTerminal terminal : terminals)
            if (padId.endsWith("." + terminal.getTerminalName()))
                return terminal.getEndpoint();
        throw new IllegalArgumentException("Unknown capacitor board pad: " + padId);
    }

    private boolean terminalConnectsToBacking(int terminal) {
        CircuitMeasurementEndpoint endpoint = getPublicTerminal(terminal);
        return endpoint instanceof CircuitPostMeasurementEndpoint &&
            ((CircuitPostMeasurementEndpoint) endpoint).getElement() == element &&
            ((CircuitPostMeasurementEndpoint) endpoint).getPostIndex() == terminal;
    }
}
