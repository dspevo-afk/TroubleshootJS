package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Small composition-root helper for fixed generated parts. */
final class PhysicalFoundationPartFactory {
    private PhysicalFoundationPartFactory() { }

    /** Resolve fixed terminals from the slot's declared pad map, including namespaced pads. */
    static <S extends PhysicalSpecification> FixedPhysicalPart<S> fromSlotBindings(
            PhysicalBoardSlot slot, S specification, PhysicalNameplate nameplate,
            BoardSimulationBindings bindings, CircuitElm backingElement,
            PhysicalPartProvenance provenance) {
        if (slot == null || bindings == null || backingElement == null)
            throw new IllegalArgumentException("Missing fixed slot backing");
        String id = slot.getComponentId();
        PhysicalPackage physicalPackage = slot.getPhysicalPackage();
        Vector<String> padIds = slot.getPadIds();
        Vector<String> slotTerminals = slot.getTerminalIds();
        Vector<PhysicalPartTerminal> terminals = new Vector<PhysicalPartTerminal>();
        for (String terminalId : physicalPackage.getTerminalIds()) {
            String padId = null;
            for (int index = 0; index < slotTerminals.size(); index++)
                if (terminalId.equals(slotTerminals.get(index))) {
                    if (padId != null)
                        throw new IllegalArgumentException("Ambiguous fixed slot terminal: " + terminalId);
                    padId = padIds.get(index);
                }
            CircuitMeasurementEndpoint endpoint = padId == null ? null : bindings.getEndpoint(padId);
            if (endpoint == null)
                throw new IllegalArgumentException("Missing declared fixed slot endpoint: " + terminalId);
            terminals.add(new PhysicalPartTerminal(id, terminalId, endpoint));
        }
        Vector<CircuitElm> backingElements = new Vector<CircuitElm>();
        backingElements.add(backingElement);
        return new FixedPhysicalPart<S>(id, specification, nameplate, physicalPackage,
            terminals, backingElements, provenance);
    }

    static <S extends PhysicalSpecification> FixedPhysicalPart<S> fromBoardBindings(
            String id, S specification, PhysicalNameplate nameplate, PhysicalPackage physicalPackage,
            BoardSimulationBindings bindings, CircuitElm backingElement,
            PhysicalPartProvenance provenance) {
        if (bindings == null || backingElement == null)
            throw new IllegalArgumentException("Missing fixed part backing");
        Vector<PhysicalPartTerminal> terminals = new Vector<PhysicalPartTerminal>();
        Vector<String> terminalIds = physicalPackage.getTerminalIds();
        for (String terminalId : terminalIds) {
            CircuitMeasurementEndpoint endpoint = bindings.getEndpoint(id + "." + terminalId);
            if (endpoint == null)
                throw new IllegalArgumentException("Missing board endpoint for fixed part: " +
                    id + "." + terminalId);
            terminals.add(new PhysicalPartTerminal(id, terminalId, endpoint));
        }
        Vector<CircuitElm> backingElements = new Vector<CircuitElm>();
        backingElements.add(backingElement);
        return new FixedPhysicalPart<S>(id, specification, nameplate, physicalPackage,
            terminals, backingElements, provenance);
    }
}
