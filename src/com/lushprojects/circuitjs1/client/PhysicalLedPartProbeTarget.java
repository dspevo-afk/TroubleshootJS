package com.lushprojects.circuitjs1.client;

class PhysicalLedPartProbeTarget extends PhysicalPartProbeTarget {
    PhysicalLedPartProbeTarget(CirSim sim, GeneratedBoardInstance instance, String partId,
            int terminal, PcbWorkbenchRenderer renderer) {
        super(sim, instance, partId, terminal, renderer);
    }
}
