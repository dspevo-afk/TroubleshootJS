package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class PhysicalResistorPart implements PhysicalPart<ResistorNameplate>, GeneratedFaultOwningPart {
    private final String id;
    private final ResistorNameplate specification;
    private final ResistorNameplate nameplate;
    private final PhysicalNameplate playerNameplate;
    private final ResistorElm element;
    private final GeneratedFaultBinding faultBinding;
    private final ResistorSecondaryOpenPath secondaryOpenPath;
    private final CircuitMeasurementEndpoint firstTerminal;
    private final CircuitMeasurementEndpoint secondTerminal;
    private final PhysicalPartTerminal[] terminals;
    private final CircuitPhysicalPartElectricalBacking backing;
    private final PhysicalPartMountState mountState = new PhysicalPartMountState();
    private final PhysicalPartProvenance provenance;
    private final Vector<PhysicalPartCapability> capabilities =
        new Vector<PhysicalPartCapability>();

    PhysicalResistorPart(String id, ResistorNameplate nameplate, ResistorElm element,
            GeneratedFaultBinding faultBinding, ResistorSecondaryOpenPath secondaryOpenPath,
            ResistorPartLocation location) {
        this(id, nameplate, nameplate, element, faultBinding, secondaryOpenPath, location,
            defaultProvenance(id));
    }

    PhysicalResistorPart(String id, ResistorNameplate specification, ResistorNameplate nameplate,
            ResistorElm element, GeneratedFaultBinding faultBinding,
            ResistorSecondaryOpenPath secondaryOpenPath, ResistorPartLocation location,
            PhysicalPartProvenance provenance) {
        this(id, specification, nameplate,
            createPlayerNameplate(id, nameplate, provenance), element, faultBinding,
            secondaryOpenPath, location, provenance);
    }

    PhysicalResistorPart(String id, ResistorNameplate specification, ResistorNameplate nameplate,
            PhysicalNameplate playerNameplate, ResistorElm element,
            GeneratedFaultBinding faultBinding, ResistorSecondaryOpenPath secondaryOpenPath,
            ResistorPartLocation location, PhysicalPartProvenance provenance) {
        if (id == null || id.length() == 0 || specification == null || nameplate == null ||
                playerNameplate == null || element == null || location == null || provenance == null)
            throw new IllegalArgumentException("Invalid physical resistor part");
        this.id = id;
        this.specification = specification;
        this.nameplate = nameplate;
        this.playerNameplate = playerNameplate;
        this.element = element;
        this.faultBinding = faultBinding;
        this.secondaryOpenPath = secondaryOpenPath;
        this.firstTerminal = new CircuitPostMeasurementEndpoint(element, 0);
        this.secondTerminal = secondaryOpenPath == null ?
            new CircuitPostMeasurementEndpoint(element, 1) : secondaryOpenPath.getPublicTerminal();
        this.terminals = new PhysicalPartTerminal[] {
            new PhysicalPartTerminal(id, "1", firstTerminal),
            new PhysicalPartTerminal(id, "2", secondTerminal)
        };
        Vector<CircuitMeasurementEndpoint> endpoints = new Vector<CircuitMeasurementEndpoint>();
        endpoints.add(firstTerminal);
        endpoints.add(secondTerminal);
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(element);
        if (faultBinding != null) elements.addAll(faultBinding.getPrivateSimulationElements());
        if (secondaryOpenPath != null) elements.add(secondaryOpenPath.getSimulationElement());
        backing = new CircuitPhysicalPartElectricalBacking(endpoints, elements);
        this.provenance = provenance;
        capabilities.add(new LoosePartInspectableCapability());
        capabilities.add(new RatedPartCapability(new PowerRating(getRatedWattage())));
        capabilities.add(new StressAwarePartCapability());
    }

    public String getId() { return id; }
    public ResistorNameplate getSpecification() { return specification; }
    public PhysicalNameplate getPlayerVisibleNameplate() { return playerNameplate; }
    public PhysicalPartRenderMetadata getRenderMetadata() {
        return new PhysicalPartRenderMetadata(nameplate, false,
            PhysicalPartRenderProbeProviders.RESISTOR);
    }
    public PhysicalPackage getPackage() { return PhysicalPackages.AXIAL_RESISTOR; }
    public int getTerminalCount() { return 2; }
    public PhysicalPartTerminal getTerminal(int terminal) {
        if (terminal < 0 || terminal >= terminals.length)
            throw new IllegalArgumentException("Invalid resistor part terminal: " + terminal);
        return terminals[terminal];
    }
    public Vector<PhysicalPartTerminal> getTerminals() {
        Vector<PhysicalPartTerminal> result = new Vector<PhysicalPartTerminal>();
        result.add(terminals[0]); result.add(terminals[1]);
        return result;
    }
    public PhysicalPartElectricalBacking getElectricalBacking() { return backing; }
    public PhysicalPartMountState getMountState() { return mountState; }
    public PhysicalBoardSlot getBoardSlot() { return mountState.getSlot(); }
    public PhysicalPartProvenance getProvenance() { return provenance; }
    public PhysicalFailureState getFailureState() {
        if (secondaryOpenPath != null && secondaryOpenPath.isOpen())
            return new PhysicalFailureState(PhysicalFailureState.SECONDARY_FAILURE, true);
        if (faultBinding != null && faultBinding.isApplied())
            return new PhysicalFailureState(PhysicalFailureState.GENERATED_FAULT, true);
        return new PhysicalFailureState(PhysicalFailureState.HEALTHY, false);
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

    ResistorNameplate getNameplate() { return nameplate; }
    ResistorElm getElement() { return element; }
    GeneratedFaultBinding getFaultBinding() { return faultBinding; }
    ResistorSecondaryOpenPath getSecondaryOpenPath() { return secondaryOpenPath; }
    double getRatedWattage() { return nameplate.getRatedWattage(); }
    ResistorPartLocation getLocation() {
        return isInstalled() ? ResistorPartLocation.INSTALLED : ResistorPartLocation.LOOSE;
    }

    CircuitMeasurementEndpoint getPublicTerminal(int terminal) {
        if (terminal == 0) return firstTerminal;
        if (terminal == 1) return secondTerminal;
        throw new IllegalArgumentException("Invalid resistor part terminal: " + terminal);
    }

    private static PhysicalPartProvenance defaultProvenance(String id) {
        return new PhysicalPartProvenance(id != null && id.endsWith("_ORIGINAL") ?
            PhysicalPartProvenance.GENERATED_ORIGINAL : PhysicalPartProvenance.CATALOG_ACQUIRED,
            id == null ? "UNKNOWN" : id);
    }

    private static PhysicalNameplate createPlayerNameplate(String id, ResistorNameplate nameplate,
            PhysicalPartProvenance provenance) {
        boolean original = provenance != null && provenance.isOriginal();
        return new PhysicalNameplate(id, "Physical resistor markings",
            original ? "Markings" : "Value",
            original ? "Color bands" : nameplate.getDisplayValue());
    }
}
