package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** RB56-only supported bench injection and real loose-module reference boundaries. */
final class Rb56InstrumentAdmissionCapability implements ActiveInstrumentAdmissionCapability,
        MeasurementReferenceCapability, PhysicalBoardInstallationProvider {
    static final String ID = "RB56_INSTRUMENT_ADMISSION@1";
    private final PhysicalBoardRuntime runtime;
    private final TroubleshootBoard board;
    private CirSim sim;
    private GeneratedBoardInstance owner;
    private BoardModificationController modifications;

    Rb56InstrumentAdmissionCapability(PhysicalBoardRuntime runtime) {
        if (runtime == null || !"RB56_CONTROL_BOARD".equals(runtime.getBoard().getId()))
            throw new IllegalArgumentException("RB56 instrument policy requires its actual physical board");
        this.runtime = runtime; board = runtime.getBoard();
    }
    public String getCapabilityId() { return ID; }
    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, double time) {
        if (sim == null || owner == null || modifications == null ||
                owner.getPhysicalBoardRuntime() != runtime || owner.getBoard() != board ||
                !Rb56Plan.FAMILY_ID.equals(owner.getCircuitFamilyId()) || runtime.getCapability(ID) != this)
            throw new IllegalArgumentException("Foreign RB56 instrument installation");
        this.sim = sim; this.owner = owner; this.modifications = modifications; return null;
    }
    private boolean currentOwner() {
        return sim != null && owner != null && CircuitElm.sim == sim &&
            sim.getGeneratedBoardInstance() == owner && owner.getPhysicalBoardRuntime() == runtime &&
            owner.getBoard() == board && runtime.getCapability(ID) == this &&
            sim.getBoardModificationController() == modifications &&
            sim.getBoardPowerController().getBindingsForDeveloperVerification() == owner.getExternalPowerBindings() &&
            sim.isGeneratedRuntimeSettled() && !sim.activeMeasurementOverlay &&
            !runtime.isMutationInProgress() && !runtime.isMutationQuarantined();
    }
    public ActiveMeasurementReadiness getActiveInstrumentAdmission(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black) {
        if (!currentOwner()) return ActiveMeasurementReadiness.UNKNOWN;
        try {
            Terminal r = terminal(red), b = terminal(black);
            if (touchesConverter(red) || touchesConverter(black) ||
                    r != null && !supported(r.part) || b != null && !supported(b.part))
                return ActiveMeasurementReadiness.UNSUPPORTED;
            if (r == null || b == null || r.part != b.part || r.index == b.index ||
                    r.part.isInstalled() || r.part.getBoardSlot() != null)
                return ActiveMeasurementReadiness.ISOLATE_COMPONENT;
            if (!validPart(r.part)) return ActiveMeasurementReadiness.UNKNOWN;
            return isolated(r.part) ? ActiveMeasurementReadiness.READY : ActiveMeasurementReadiness.ISOLATE_COMPONENT;
        } catch (RuntimeException invalidOwner) { return ActiveMeasurementReadiness.UNKNOWN; }
    }

    /** Existing simple loose-part families only; neither generic parts nor opaque controllers are blessed. */
    private static boolean supported(PhysicalPart<?> part) {
        Class<?> type = part.getClass();
        return type == PhysicalResistorPart.class || type == PhysicalDiodePart.class ||
            type == PhysicalLedPart.class || type == PhysicalCapacitorPart.class ||
            type == PhysicalNpnPart.class || type == PhysicalNmosPart.class || type == PhysicalRelayPart.class;
    }

    public MeasurementReferencePolicy.Result assessReference(MeasurementReferencePolicy.Mode mode,
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) {
        if (!currentOwner()) return reference(MeasurementReferencePolicy.Decision.UNPROVEN, "STALE_OWNER");
        try {
            Terminal r = terminal(red), b = terminal(black);
            boolean converter = touchesConverter(red) || touchesConverter(black);
            if (!converter) return MeasurementReferencePolicy.notApplicable();
            if (!validEndpoint(red) || !validEndpoint(black) ||
                    r != null && !validPart(r.part) || b != null && !validPart(b.part))
                return reference(MeasurementReferencePolicy.Decision.UNPROVEN, "UNRESOLVED_CONVERTER_OWNER");
            String rd = domain(r, red), bd = domain(b, black);
            if (rd == null || bd == null)
                return reference(MeasurementReferencePolicy.Decision.UNPROVEN, "UNDECLARED_CONVERTER_REFERENCE");
            if (!rd.equals(bd)) return reference(MeasurementReferencePolicy.Decision.REJECTED, "ISOLATION_BOUNDARY");
            boolean loose = r != null && r.part instanceof PhysicalConverterPart && !r.part.isInstalled() ||
                b != null && b.part instanceof PhysicalConverterPart && !b.part.isInstalled();
            if (!loose) return MeasurementReferencePolicy.notApplicable();
            if (mode != MeasurementReferencePolicy.Mode.DIFFERENTIAL || r == null || b == null ||
                    r.part != b.part || r.part.getBoardSlot() != null || !isolated(r.part))
                return reference(MeasurementReferencePolicy.Decision.UNPROVEN, "LOOSE_REFERENCES_NOT_JOINED");
            return reference(MeasurementReferencePolicy.Decision.ADMITTED, "SAME_ISOLATED_MODULE_SIDE");
        } catch (RuntimeException invalidOwner) {
            return reference(MeasurementReferencePolicy.Decision.UNPROVEN, "INVALID_CONVERTER_OWNER");
        }
    }
    private static MeasurementReferencePolicy.Result reference(MeasurementReferencePolicy.Decision decision, String reason) {
        return new MeasurementReferencePolicy.Result(decision, reason);
    }
    private String domain(Terminal terminal, CircuitPostMeasurementEndpoint endpoint) {
        if (terminal != null) {
            PhysicalBoardSlot slot = terminal.part.getBoardSlot();
            if (slot == null) {
                WorkbenchPartsProvider source = runtime.getWorkbenchPartsProviderForPart(terminal.part.getId());
                if (source == null) return null;
                slot = runtime.getSlot(source.getComponentId());
            }
            if (slot == null || slot.getRuntime() != runtime) return null;
            PcbPlacementConstraints.Part demand = board.getPlacementConstraints().get(slot.getComponentId());
            String name = terminal.part.getTerminal(terminal.index).getTerminalName();
            if (demand == null || !slot.getTerminalIds().contains(name)) return null;
            if (terminal.part instanceof PhysicalConverterPart) {
                PhysicalPackageGeometry.IsolationBody body = terminal.part.getPackage().getGeometry().getIsolationBody();
                if (body == null || !"PRIMARY".equals(body.domain(demand, true)) ||
                        !"SECONDARY".equals(body.domain(demand, false))) return null;
            }
            return declaredDomain(demand.terminalDomain(name));
        }
        String result = null;
        for (String pad : board.getPadIds()) if (same(board.getSimulationBindings().getEndpoint(pad), endpoint)) {
            String domain = declaredDomain(board.getPlacementConstraints().domainForPad(board, pad));
            if (domain == null || result != null && !result.equals(domain)) return null;
            result = domain;
        }
        return result;
    }
    private static String declaredDomain(String domain) {
        return "PRIMARY".equals(domain) || "SECONDARY".equals(domain) ? domain : null;
    }
    private static final class Terminal {
        final PhysicalPart<?> part; final int index;
        Terminal(PhysicalPart<?> part, int index) { this.part = part; this.index = index; }
    }
    private boolean touchesConverter(CircuitPostMeasurementEndpoint endpoint) {
        if (endpoint == null || endpoint.getElement() == null) return false;
        for (String id : runtime.getPartIds()) {
            PhysicalPart<?> part = runtime.getPart(id);
            if (part instanceof PhysicalConverterPart &&
                    part.getElectricalBacking().getCircuitElements().contains(endpoint.getElement())) return true;
        }
        return false;
    }
    private Terminal terminal(CircuitPostMeasurementEndpoint endpoint) {
        if (endpoint == null || endpoint.getElement() == null || endpoint.getPostIndex() < 0 ||
                endpoint.getPostIndex() >= endpoint.getElement().getPostCount()) return null;
        Terminal result = null;
        for (String id : runtime.getPartIds()) {
            PhysicalPart<?> part = runtime.getPart(id);
            for (int i = 0; i < part.getTerminalCount(); i++) if (same(part.getTerminal(i).getEndpoint(), endpoint)) {
                if (result != null) throw new IllegalStateException("Ambiguous physical measurement terminal");
                result = new Terminal(part, i);
            }
        }
        return result;
    }
    private boolean validEndpoint(CircuitPostMeasurementEndpoint endpoint) {
        return endpoint != null && endpoint.getElement() != null && endpoint.getPostIndex() >= 0 &&
            endpoint.getPostIndex() < endpoint.getElement().getPostCount() &&
            owner.ownsRuntimeSimulationElement(endpoint.getElement()) && sim.containsElement(endpoint.getElement());
    }
    private boolean validPart(PhysicalPart<?> part) {
        if (runtime.getPart(part.getId()) != part || !runtime.isPartOwnedByRegisteredProvider(part)) return false;
        if (part.isInstalled()) {
            PhysicalBoardSlot slot = part.getBoardSlot();
            if (slot == null || slot.getRuntime() != runtime || runtime.getSlot(slot.getComponentId()) != slot ||
                    slot.getInstalledPart() != part) return false;
        } else if (part.getBoardSlot() != null) return false;
        if (part instanceof PhysicalConverterPart) {
            PhysicalConverterPart converter = (PhysicalConverterPart)part;
            converter.getSpecification().requireModel(converter.getModule());
        }
        PhysicalPartElectricalBacking backing = part.getElectricalBacking();
        if (backing == null || backing.getTerminalCount() != part.getTerminalCount()) return false;
        Vector<CircuitElm> elements = backing.getCircuitElements();
        if (elements.isEmpty()) return false;
        Vector<CircuitElm> canonical = owner.getSimulationElements();
        for (CircuitElm element : elements) {
            if (count(elements, element) != 1 || count(canonical, element) != 1 || count(sim.elmList, element) != 1) return false;
            for (String id : runtime.getPartIds()) {
                PhysicalPart<?> other = runtime.getPart(id);
                if (other != part && other.getElectricalBacking().getCircuitElements().contains(element)) return false;
            }
        }
        for (int i = 0; i < part.getTerminalCount(); i++) {
            CircuitMeasurementEndpoint terminal = part.getTerminal(i).getEndpoint();
            if (!(terminal instanceof CircuitPostMeasurementEndpoint) ||
                    !same(terminal, backing.getTerminalEndpoint(i)) ||
                    !validEndpoint((CircuitPostMeasurementEndpoint)terminal) ||
                    !elements.contains(((CircuitPostMeasurementEndpoint)terminal).getElement())) return false;
        }
        return true;
    }
    /** CircuitJS attaches ordinary islands through coincident actual posts, including wires and instruments. */
    private boolean isolated(PhysicalPart<?> part) {
        if (part.isInstalled() || part.getBoardSlot() != null) return false;
        Vector<CircuitElm> elements = part.getElectricalBacking().getCircuitElements();
        for (CircuitElm element : elements) for (int post = 0; post < element.getPostCount(); post++) {
            if (element.hasGroundConnection(post)) return false;
            Point point = element.getPost(post);
            for (CircuitElm outside : sim.elmList) if (!elements.contains(outside))
                for (int other = 0; other < outside.getPostCount(); other++)
                    if (point.equals(outside.getPost(other))) return false;
        }
        return true;
    }
    private static int count(Vector<CircuitElm> elements, CircuitElm expected) {
        int result = 0; for (CircuitElm element : elements) if (element == expected) result++; return result;
    }
    private static boolean same(CircuitMeasurementEndpoint first, CircuitMeasurementEndpoint second) {
        if (!(first instanceof CircuitPostMeasurementEndpoint) || !(second instanceof CircuitPostMeasurementEndpoint)) return false;
        CircuitPostMeasurementEndpoint a = (CircuitPostMeasurementEndpoint)first, b = (CircuitPostMeasurementEndpoint)second;
        return a.getElement() == b.getElement() && a.getPostIndex() == b.getPostIndex();
    }
}
