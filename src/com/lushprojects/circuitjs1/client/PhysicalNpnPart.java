package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Physical NPN identity with explicit B/C/E order and private fault ownership. */
final class PhysicalNpnPart implements PhysicalPart<NpnSpecification>,
        GeneratedFaultOwningPart {
    private final String id;
    private final NpnSpecification specification;
    private final PhysicalNameplate playerNameplate;
    private final NTransistorElm element;
    private final GeneratedFaultBinding faultBinding;
    private final PhysicalPartTerminal[] terminals;
    private final CircuitPhysicalPartElectricalBacking backing;
    private final PhysicalPartMountState mountState = new PhysicalPartMountState();
    private final PhysicalPartProvenance provenance;
    private final Vector<PhysicalPartCapability> capabilities =
        new Vector<PhysicalPartCapability>();

    PhysicalNpnPart(String id, NpnSpecification specification,
            PhysicalNameplate playerNameplate, NTransistorElm element,
            GeneratedFaultBinding faultBinding, NpnPartLocation location,
            PhysicalPartProvenance provenance) {
        if (id == null || id.length() == 0 || specification == null ||
                playerNameplate == null || element == null || location == null ||
                provenance == null)
            throw new IllegalArgumentException("Invalid physical NPN part");
        if (!id.equals(playerNameplate.getId()))
            throw new IllegalArgumentException("NPN nameplate must identify its physical part");
        this.id = id;
        this.specification = specification;
        this.playerNameplate = playerNameplate;
        this.element = element;
        this.faultBinding = faultBinding;
        this.provenance = provenance;
        String[] terminalIds = { "B", "C", "E" };
        terminals = new PhysicalPartTerminal[3];
        Vector<CircuitMeasurementEndpoint> endpoints = new Vector<CircuitMeasurementEndpoint>();
        for (int index = 0; index < terminalIds.length; index++) {
            CircuitMeasurementEndpoint endpoint = faultBinding == null ?
                new CircuitPostMeasurementEndpoint(element, index) :
                faultBinding.getPublicTerminal(element, index);
            terminals[index] = new PhysicalPartTerminal(id, terminalIds[index], endpoint);
            endpoints.add(endpoint);
        }
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(element);
        if (faultBinding != null)
            elements.addAll(faultBinding.getPrivateSimulationElements());
        backing = new CircuitPhysicalPartElectricalBacking(endpoints, elements);
        capabilities.add(new LoosePartInspectableCapability());
    }

    public String getId() { return id; }
    public NpnSpecification getSpecification() { return specification; }
    public PhysicalNameplate getPlayerVisibleNameplate() { return playerNameplate; }
    public PhysicalPartRenderMetadata getRenderMetadata() {
        return new PhysicalPartRenderMetadata(specification,
            PhysicalPartOrientation.NON_POLARIZED, PhysicalPartRenderProbeProviders.NPN);
    }
    public PhysicalPartOrientation getOrientation() { return PhysicalPartOrientation.NON_POLARIZED; }
    public PhysicalPackage getPackage() { return specification.getPhysicalPackage(); }
    public int getTerminalCount() { return terminals.length; }
    public PhysicalPartTerminal getTerminal(int terminal) {
        if (terminal < 0 || terminal >= terminals.length)
            throw new IllegalArgumentException("Invalid NPN terminal: " + terminal);
        return terminals[terminal];
    }
    public Vector<PhysicalPartTerminal> getTerminals() {
        Vector<PhysicalPartTerminal> result = new Vector<PhysicalPartTerminal>();
        for (PhysicalPartTerminal terminal : terminals) result.add(terminal);
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

    NTransistorElm getElement() { return element; }
    GeneratedFaultBinding getFaultBinding() { return faultBinding; }
    CircuitMeasurementEndpoint getPublicTerminal(int terminal) { return getTerminal(terminal).getEndpoint(); }
    CircuitMeasurementEndpoint getTerminalForBoardPad(String padId) {
        if (padId == null)
            throw new IllegalArgumentException("Missing NPN board pad");
        for (PhysicalPartTerminal terminal : terminals)
            if (padId.endsWith("." + terminal.getTerminalName()))
                return terminal.getEndpoint();
        throw new IllegalArgumentException("Unknown NPN board pad: " + padId);
    }
}
