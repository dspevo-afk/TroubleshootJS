package com.lushprojects.circuitjs1.client;

/** Instrument acquisition uses solver time, never a UI paint slice's wall clock. */
final class TemporaryMeasurementSettlement {
    // Stateful and unreviewed graphs keep this finite window; it is not a
    // claim that every capacitor reaches DC.
    static final double ACTIVE_SECONDS = .01;
    private static final int MEMORYLESS_DC_ACQUISITION_STEPS = 8;

    private TemporaryMeasurementSettlement() { }

    static void acquire(CirSim sim, boolean active) {
        if (active && hasOnlyMemorylessDcElements(sim))
            sim.solverExecutor.advanceSteps(MEMORYLESS_DC_ACQUISITION_STEPS);
        else if (active) sim.solverExecutor.advanceFor(ACTIVE_SECONDS);
        else sim.solverExecutor.advanceSteps(2);
    }

    /**
     * A step-bounded acquisition is valid only for this closed set of exact
     * CircuitJS classes. Exact classes matter: an unreviewed subclass may add
     * storage, time dependence, or other solver state. DC sources must also be
     * constant and zero-bias. This scans the live graph after the instrument
     * stimulus is installed, so its temporary elements receive the same check.
     *
     * LimitedDcSupplyElm's lastDrop field is Newton continuation state for its
     * static current-limit I/V curve. Its doStep law has no simulation-time or
     * integrated state; retaining lastDrop only changes the tangent used to
     * reach the same algebraic fixed point.
     */
    private static boolean hasOnlyMemorylessDcElements(CirSim sim) {
        if (sim == null || sim.elmList == null || sim.elmList.isEmpty()) return false;
        for (CircuitElm element : sim.elmList) {
            if (element == null) return false;
            Class<?> type = element.getClass();
            if (type == ResistorElm.class || type == WireElm.class ||
                    type == SwitchElm.class || type == GroundElm.class ||
                    type == NTransistorElm.class || type == NMosfetElm.class ||
                    type == LEDElm.class) {
                continue;
            }
            if (type == LimitedDcSupplyElm.class) {
                LimitedDcSupplyElm source = (LimitedDcSupplyElm)element;
                if (source.waveform != VoltageElm.WF_DC || source.bias != 0) return false;
                continue;
            }
            if (type == DCVoltageElm.class) {
                DCVoltageElm source = (DCVoltageElm)element;
                if (source.waveform != VoltageElm.WF_DC || source.bias != 0) return false;
                continue;
            }
            return false;
        }
        return true;
    }

    static void restore(CirSim sim) {
        sim.solverExecutor.advanceSteps(1);
    }
}
