package com.lushprojects.circuitjs1.client;

/** Provider-owned loose capacitor probe target. */
final class PhysicalCapacitorPartProbeTarget extends PhysicalPartProbeTarget {
    PhysicalCapacitorPartProbeTarget(CirSim sim, GeneratedBoardInstance instance, String partId,
            int terminal, PcbWorkbenchRenderer renderer) {
        super(sim, instance, partId, terminal, renderer);
    }
}
