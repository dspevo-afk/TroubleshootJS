package com.lushprojects.circuitjs1.client;

/** Built-in loose-probe providers for the current physical package semantics. */
final class PhysicalPartRenderProbeProviders {
    static final PhysicalPartRenderProbeProvider RESISTOR =
        new PhysicalPartRenderProbeProvider() {
            public ProbeTarget createLooseProbeTarget(CirSim sim,
                    GeneratedBoardInstance instance, PhysicalPart<?> part, int terminal,
                    PcbWorkbenchRenderer renderer) {
                return new PhysicalResistorPartProbeTarget(sim, instance, part.getId(), terminal,
                    renderer);
            }
        };

    static final PhysicalPartRenderProbeProvider DIODE =
        new PhysicalPartRenderProbeProvider() {
            public ProbeTarget createLooseProbeTarget(CirSim sim,
                    GeneratedBoardInstance instance, PhysicalPart<?> part, int terminal,
                    PcbWorkbenchRenderer renderer) {
                return new PhysicalDiodePartProbeTarget(sim, instance, part.getId(), terminal,
                    renderer);
            }
        };

    static final PhysicalPartRenderProbeProvider LED =
        new PhysicalPartRenderProbeProvider() {
            public ProbeTarget createLooseProbeTarget(CirSim sim,
                    GeneratedBoardInstance instance, PhysicalPart<?> part, int terminal,
                    PcbWorkbenchRenderer renderer) {
                return new PhysicalLedPartProbeTarget(sim, instance, part.getId(), terminal,
                    renderer);
            }
        };

    static final PhysicalPartRenderProbeProvider CAPACITOR =
        new PhysicalPartRenderProbeProvider() {
            public ProbeTarget createLooseProbeTarget(CirSim sim,
                    GeneratedBoardInstance instance, PhysicalPart<?> part, int terminal,
                    PcbWorkbenchRenderer renderer) {
                return new PhysicalCapacitorPartProbeTarget(sim, instance, part.getId(),
                    terminal, renderer);
            }
        };

    static final PhysicalPartRenderProbeProvider NPN =
        new PhysicalPartRenderProbeProvider() {
            public ProbeTarget createLooseProbeTarget(CirSim sim,
                    GeneratedBoardInstance instance, PhysicalPart<?> part, int terminal,
                    PcbWorkbenchRenderer renderer) {
                return new PhysicalNpnPartProbeTarget(sim, instance, part.getId(), terminal,
                    renderer);
            }
        };

    private PhysicalPartRenderProbeProviders() { }
}
