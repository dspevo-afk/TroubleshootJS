package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Small composition-root helper for fixed generated parts. */
final class PhysicalFoundationPartFactory {
    private PhysicalFoundationPartFactory() { }

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
