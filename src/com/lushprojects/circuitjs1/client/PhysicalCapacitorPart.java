package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Stable physical capacitor identity with family-owned CircuitJS backing and fault ownership. */
final class PhysicalCapacitorPart implements PhysicalPart<CapacitorSpecification>,
        GeneratedFaultOwningPart {
    private final String id;
    private final CapacitorSpecification specification;
    private final PhysicalNameplate playerNameplate;
    private final CapacitorElm element;
    private final ResistorElm esrElement;
    private final GeneratedFaultBinding faultBinding;
    private final PhysicalPartTerminal[] terminals;
    private final CircuitPhysicalPartElectricalBacking backing;
    private final PhysicalPartMountState mountState = new PhysicalPartMountState();
    private final PhysicalPartGeometryRealization geometryRealization =
        new PhysicalPartGeometryRealization();
    private final PhysicalPartProvenance provenance;
    private final Vector<PhysicalPartCapability> capabilities =
        new Vector<PhysicalPartCapability>();

    PhysicalCapacitorPart(String id, CapacitorSpecification specification,
            PhysicalNameplate playerNameplate, CapacitorElm element,
            GeneratedFaultBinding faultBinding, CapacitorPartLocation location,
            PhysicalPartProvenance provenance) {
        this(id, specification, playerNameplate, element, null, faultBinding,
            location, provenance);
    }

    /** The ESR is an actual private series element owned by this one package. */
    PhysicalCapacitorPart(String id, CapacitorSpecification specification,
            PhysicalNameplate playerNameplate, CapacitorElm element, ResistorElm esrElement,
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
        validateEsrBacking(specification, element, esrElement, faultBinding);
        this.element = element;
        this.esrElement = esrElement;
        this.faultBinding = faultBinding;
        this.provenance = provenance;
        CircuitMeasurementEndpoint first = esrElement != null ?
            new CircuitPostMeasurementEndpoint(esrElement, 0) : faultBinding == null ?
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
        if (esrElement != null) elements.add(esrElement);
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
    ResistorElm getEsrElement() { return esrElement; }
    GeneratedFaultBinding getFaultBinding() { return faultBinding; }
    /**
     * Stored charge is player-relevant only when both physical terminals still
     * reach the underlying capacitor.  An internally open original part may
     * retain charge on its isolated element, but that charge is not exposed
     * at the loose or installed board terminals.
     */
    boolean hasAccessibleStoredEnergyTerminals() {
        if (esrElement == null)
            return terminalConnectsToBacking(0) && terminalConnectsToBacking(1);
        if (!isEndpoint(getPublicTerminal(0), esrElement, 0) ||
                !terminalConnectsToBacking(1) || !seriesNodesMatch(element, esrElement))
            throw new IllegalStateException("Capacitor ESR terminal path changed");
        return true;
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
        return isEndpoint(getPublicTerminal(terminal), element, terminal);
    }

    private static boolean isEndpoint(CircuitMeasurementEndpoint endpoint,
            CircuitElm expected, int post) {
        return endpoint instanceof CircuitPostMeasurementEndpoint &&
            ((CircuitPostMeasurementEndpoint) endpoint).getElement() == expected &&
            ((CircuitPostMeasurementEndpoint) endpoint).getPostIndex() == post;
    }

    private static void validateEsrBacking(CapacitorSpecification specification,
            CapacitorElm capacitor, ResistorElm esr, GeneratedFaultBinding fault) {
        if (specification.hasExplicitModelRecipe() &&
                (fault != null || capacitor.getClass() != CapacitorElm.class ||
                 capacitor.capacitance != specification.getCapacitanceFarads() ||
                 (capacitor.flags & CapacitorElm.FLAG_BACK_EULER) != specification.getIntegrationFlags() ||
                 capacitor.initialVoltage != specification.getInitialVoltage()))
            throw new IllegalArgumentException("Capacitor backing differs from its explicit recipe");
        if (esr == null) {
            if (specification.getEsrOhms() > 0)
                throw new IllegalArgumentException("Capacitor recipe requires its owned ESR");
            return;
        }
        // Existing generated lead-open/short paths retain their historical owner.
        // Combining those paths with ESR requires its own explicit qualification.
        if (!specification.hasExplicitModelRecipe() || specification.getEsrOhms() <= 0 ||
                fault != null || capacitor.getClass() != CapacitorElm.class ||
                esr.getClass() != ResistorElm.class ||
                esr.resistance != specification.getEsrOhms() ||
                capacitor.capacitance != specification.getCapacitanceFarads() ||
                (capacitor.flags & CapacitorElm.FLAG_BACK_EULER) != specification.getIntegrationFlags() ||
                capacitor.initialVoltage != specification.getInitialVoltage() ||
                !seriesNodesMatch(capacitor, esr))
            throw new IllegalArgumentException("Invalid owned capacitor ESR backing");
    }

    private static boolean seriesNodesMatch(CapacitorElm capacitor, ResistorElm esr) {
        Point internal = capacitor.getPost(0), negative = capacitor.getPost(1);
        Point positive = esr.getPost(0), esrInternal = esr.getPost(1);
        return internal != null && negative != null && positive != null && esrInternal != null &&
            internal.x == esrInternal.x && internal.y == esrInternal.y &&
            !(positive.x == internal.x && positive.y == internal.y) &&
            !(negative.x == internal.x && negative.y == internal.y) &&
            !(positive.x == negative.x && positive.y == negative.y);
    }
}
