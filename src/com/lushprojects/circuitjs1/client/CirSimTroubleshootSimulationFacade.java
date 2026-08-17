package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** CircuitJS adapter; no analyzed node numbers become physical identity. */
final class CirSimTroubleshootSimulationFacade implements TroubleshootSimulationFacade {
    private final CirSim sim;

    CirSimTroubleshootSimulationFacade(CirSim sim) {
        if (sim == null) throw new IllegalArgumentException("Missing CircuitJS simulator");
        this.sim = sim;
    }

    public boolean ownsBacking(PhysicalPartElectricalBacking backing) {
        if (backing == null) return false;
        Vector<CircuitElm> elements = backing.getCircuitElements();
        if (elements == null || elements.isEmpty()) return false;
        for (CircuitElm element : elements)
            if (element == null || !sim.containsElement(element)) return false;
        return true;
    }

    public void validateBacking(PhysicalPart<?> part) {
        if (part == null || part.getElectricalBacking() == null ||
                !ownsBacking(part.getElectricalBacking()))
            throw new IllegalStateException("Physical part backing is not owned by CircuitJS: " +
                (part == null ? "null" : part.getId()));
        if (part.getElectricalBacking().getTerminalCount() != part.getTerminalCount())
            throw new IllegalStateException("Physical terminal/backing count mismatch: " + part.getId());
    }
}
