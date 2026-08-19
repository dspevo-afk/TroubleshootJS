package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class PhysicalLedPart implements PhysicalPart<LedNameplate>, GeneratedFaultOwningPart {
    private final String id;
    private final LedNameplate specification;
    private final LedNameplate nameplate;
    private final PhysicalNameplate playerNameplate;
    private final LEDElm element;
    private final GeneratedFaultBinding faultBinding;
    private final CircuitMeasurementEndpoint anode;
    private final CircuitMeasurementEndpoint cathode;
    private final PhysicalPartTerminal[] terminals;
    private final CircuitPhysicalPartElectricalBacking backing;
    private final PhysicalPartMountState mountState = new PhysicalPartMountState();
    private final PhysicalPartProvenance provenance;
    private final Vector<PhysicalPartCapability> capabilities =
        new Vector<PhysicalPartCapability>();
    private final boolean reversedInstallation;

    PhysicalLedPart(String id, LedNameplate nameplate, LEDElm element,
            boolean reversedInstallation, LedPartLocation location) {
        this(id, nameplate, nameplate, element, reversedInstallation, location,
            defaultProvenance(id));
    }

    PhysicalLedPart(String id, LedNameplate specification, LedNameplate nameplate,
            LEDElm element, boolean reversedInstallation, LedPartLocation location,
            PhysicalPartProvenance provenance) {
        this(id, specification, nameplate,
            createPlayerNameplate(id, nameplate), element,
            reversedInstallation, location, provenance, null);
    }

    PhysicalLedPart(String id, LedNameplate specification, LedNameplate nameplate,
            LEDElm element, GeneratedFaultBinding faultBinding,
            boolean reversedInstallation, LedPartLocation location,
            PhysicalPartProvenance provenance) {
        this(id, specification, nameplate,
            createPlayerNameplate(id, nameplate), element, reversedInstallation,
            location, provenance, faultBinding);
    }

    PhysicalLedPart(String id, LedNameplate specification, LedNameplate nameplate,
            PhysicalNameplate playerNameplate, LEDElm element, boolean reversedInstallation,
            LedPartLocation location, PhysicalPartProvenance provenance) {
        this(id, specification, nameplate, playerNameplate, element, reversedInstallation,
            location, provenance, null);
    }

    PhysicalLedPart(String id, LedNameplate specification, LedNameplate nameplate,
            PhysicalNameplate playerNameplate, LEDElm element, boolean reversedInstallation,
            LedPartLocation location, PhysicalPartProvenance provenance,
            GeneratedFaultBinding faultBinding) {
        if (id == null || id.length() == 0 || specification == null || nameplate == null ||
                playerNameplate == null || element == null || location == null || provenance == null)
            throw new IllegalArgumentException("Invalid physical LED part");
        this.id = id;
        this.specification = specification;
        this.nameplate = nameplate;
        this.playerNameplate = playerNameplate.hasWorkbenchDetail() ? playerNameplate :
            createPlayerNameplate(id, nameplate);
        this.element = element;
        this.faultBinding = faultBinding;
        this.reversedInstallation = reversedInstallation;
        this.provenance = provenance;
        anode = faultBinding == null ? new CircuitPostMeasurementEndpoint(element, 0) :
            faultBinding.getPublicTerminal(element, 0);
        cathode = faultBinding == null ? new CircuitPostMeasurementEndpoint(element, 1) :
            faultBinding.getPublicTerminal(element, 1);
        terminals = new PhysicalPartTerminal[] {
            new PhysicalPartTerminal(id, "A", anode),
            new PhysicalPartTerminal(id, "K", cathode)
        };
        Vector<CircuitMeasurementEndpoint> endpoints = new Vector<CircuitMeasurementEndpoint>();
        endpoints.add(anode); endpoints.add(cathode);
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(element);
        if (faultBinding != null) elements.addAll(faultBinding.getPrivateSimulationElements());
        backing = new CircuitPhysicalPartElectricalBacking(endpoints, elements);
        capabilities.add(new LoosePartInspectableCapability());
    }

    public String getId() { return id; }
    public LedNameplate getSpecification() { return specification; }
    public PhysicalNameplate getPlayerVisibleNameplate() { return playerNameplate; }
    public PhysicalPartRenderMetadata getRenderMetadata() {
        return new PhysicalPartRenderMetadata(nameplate,
            PhysicalPartOrientation.polarized(reversedInstallation),
            PhysicalPartRenderProbeProviders.LED);
    }
    public PhysicalPartOrientation getOrientation() { return getRenderMetadata().getOrientation(); }
    public PhysicalPackage getPackage() { return PhysicalPackages.THROUGH_HOLE_LED; }
    public int getTerminalCount() { return 2; }
    public PhysicalPartTerminal getTerminal(int terminal) {
        if (terminal < 0 || terminal >= terminals.length)
            throw new IllegalArgumentException("Invalid LED part terminal: " + terminal);
        return terminals[terminal];
    }
    public Vector<PhysicalPartTerminal> getTerminals() {
        Vector<PhysicalPartTerminal> result = new Vector<PhysicalPartTerminal>();
        result.add(terminals[0]); result.add(terminals[1]); return result;
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

    LedNameplate getNameplate() { return nameplate; }
    LEDElm getElement() { return element; }
    GeneratedFaultBinding getFaultBinding() { return faultBinding; }
    boolean isReversedInstallation() { return reversedInstallation; }
    LedPartLocation getLocation() {
        return isInstalled() ? LedPartLocation.INSTALLED : LedPartLocation.LOOSE;
    }

    CircuitMeasurementEndpoint getTerminalEndpoint(int terminal) { return getTerminal(terminal).getEndpoint(); }

    CircuitMeasurementEndpoint getTerminalForBoardPad(String padId) {
        if (padId == null || (!padId.endsWith(".A") && !padId.endsWith(".K")))
            throw new IllegalArgumentException("Invalid LED board pad: " + padId);
        boolean anodePad = padId.endsWith(".A");
        return getTerminalEndpoint((anodePad ^ reversedInstallation) ? 0 : 1);
    }

    private static PhysicalPartProvenance defaultProvenance(String id) {
        return new PhysicalPartProvenance(id != null && id.endsWith("_ORIGINAL") ?
            PhysicalPartProvenance.GENERATED_ORIGINAL : PhysicalPartProvenance.CATALOG_ACQUIRED,
            id == null ? "UNKNOWN" : id);
    }

    private static PhysicalNameplate createPlayerNameplate(String id, LedNameplate nameplate) {
        String displayName = nameplate == null ? "" : nameplate.getDisplayName();
        return new PhysicalNameplate(id, displayName, "Part", displayName);
    }
}
