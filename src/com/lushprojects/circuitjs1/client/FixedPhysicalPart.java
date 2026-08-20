package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Fixed, non-replaceable part used for production components outside a mutation workflow. */
final class FixedPhysicalPart<S extends PhysicalSpecification> implements PhysicalPart<S> {
    private final String id;
    private final S specification;
    private final PhysicalNameplate nameplate;
    private final PhysicalPackage physicalPackage;
    private final PhysicalPartTerminal[] terminals;
    private final CircuitPhysicalPartElectricalBacking backing;
    private final PhysicalPartMountState mountState = new PhysicalPartMountState();
    private final PhysicalPartGeometryRealization geometryRealization =
        new PhysicalPartGeometryRealization();
    private final PhysicalPartProvenance provenance;
    private final PhysicalPartRenderProbeProvider looseProbeProvider;
    private final Vector<PhysicalPartCapability> capabilities =
        new Vector<PhysicalPartCapability>();

    FixedPhysicalPart(String id, S specification, PhysicalNameplate nameplate,
            PhysicalPackage physicalPackage, Vector<PhysicalPartTerminal> terminals,
            Vector<CircuitElm> backingElements, PhysicalPartProvenance provenance) {
        this(id, specification, nameplate, physicalPackage, terminals, backingElements,
            provenance, null);
    }

    FixedPhysicalPart(String id, S specification, PhysicalNameplate nameplate,
            PhysicalPackage physicalPackage, Vector<PhysicalPartTerminal> terminals,
            Vector<CircuitElm> backingElements, PhysicalPartProvenance provenance,
            PhysicalPartRenderProbeProvider looseProbeProvider) {
        this(id, specification, nameplate, physicalPackage, terminals, backingElements,
            provenance, looseProbeProvider, null);
    }

    FixedPhysicalPart(String id, S specification, PhysicalNameplate nameplate,
            PhysicalPackage physicalPackage, Vector<PhysicalPartTerminal> terminals,
            Vector<CircuitElm> backingElements, PhysicalPartProvenance provenance,
            PhysicalPartRenderProbeProvider looseProbeProvider,
            Vector<PhysicalPartCapability> additionalCapabilities) {
        if (id == null || id.length() == 0 || specification == null || nameplate == null ||
                physicalPackage == null || terminals == null || backingElements == null ||
                provenance == null || terminals.size() != physicalPackage.getTerminalCount())
            throw new IllegalArgumentException("Invalid fixed physical part");
        this.id = id;
        this.specification = specification;
        this.nameplate = nameplate;
        this.physicalPackage = physicalPackage;
        this.provenance = provenance;
        this.looseProbeProvider = looseProbeProvider;
        if (additionalCapabilities != null)
            for (PhysicalPartCapability capability : additionalCapabilities) {
                if (capability == null)
                    throw new IllegalArgumentException("Missing fixed part capability");
                capabilities.add(capability);
            }
        Vector<CircuitMeasurementEndpoint> endpoints = new Vector<CircuitMeasurementEndpoint>();
        this.terminals = new PhysicalPartTerminal[terminals.size()];
        for (int index = 0; index < terminals.size(); index++) {
            PhysicalPartTerminal terminal = terminals.get(index);
            if (terminal == null) throw new IllegalArgumentException("Missing fixed terminal");
            this.terminals[index] = terminal;
            endpoints.add(terminal.getEndpoint());
        }
        backing = new CircuitPhysicalPartElectricalBacking(endpoints, backingElements);
    }

    public String getId() { return id; }
    public S getSpecification() { return specification; }
    public PhysicalNameplate getPlayerVisibleNameplate() { return nameplate; }
    public PhysicalPartRenderMetadata getRenderMetadata() {
        return new PhysicalPartRenderMetadata(specification, PhysicalPartOrientation.NON_POLARIZED,
            looseProbeProvider);
    }
    public PhysicalPartOrientation getOrientation() { return getRenderMetadata().getOrientation(); }
    public PhysicalPackage getPackage() { return physicalPackage; }
    public int getTerminalCount() { return terminals.length; }
    public PhysicalPartTerminal getTerminal(int terminal) {
        if (terminal < 0 || terminal >= terminals.length)
            throw new IllegalArgumentException("Invalid fixed part terminal: " + terminal);
        return terminals[terminal];
    }
    public Vector<PhysicalPartTerminal> getTerminals() {
        Vector<PhysicalPartTerminal> result = new Vector<PhysicalPartTerminal>();
        for (PhysicalPartTerminal terminal : terminals) result.add(terminal);
        return result;
    }
    public PhysicalPartElectricalBacking getElectricalBacking() { return backing; }
    public PhysicalGeometryRealization getGeometryRealization() {
        return geometryRealization.getGeometryRealization();
    }
    public void bindGeometryRealization(PhysicalGeometryRealization realization) {
        geometryRealization.bind(getPackage(), realization);
    }
    public PhysicalPartMountState getMountState() { return mountState; }
    public PhysicalBoardSlot getBoardSlot() { return mountState.getSlot(); }
    public PhysicalPartProvenance getProvenance() { return provenance; }
    public PhysicalFailureState getFailureState() {
        return new PhysicalFailureState(PhysicalFailureState.HEALTHY, false);
    }
    public Vector<PhysicalPartCapability> getCapabilities() {
        return new Vector<PhysicalPartCapability>(capabilities);
    }
    public Vector<PhysicalPartCapability> getIntrinsicCapabilities() {
        return getCapabilities();
    }
    public boolean isInstalled() { return mountState.isInstalled(); }
    public boolean isOriginal() { return provenance.isOriginal(); }
    public boolean isFaulted() { return false; }
}
