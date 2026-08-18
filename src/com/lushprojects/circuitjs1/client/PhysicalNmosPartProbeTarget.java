package com.lushprojects.circuitjs1.client;

/** Provider-owned loose G/D/S probe target for a physical NMOS part. */
final class PhysicalNmosPartProbeTarget extends PhysicalPartProbeTarget {
    PhysicalNmosPartProbeTarget(CirSim sim, GeneratedBoardInstance instance, String partId,
            int terminal, PcbWorkbenchRenderer renderer) {
        super(sim, instance, partId, terminal, renderer);
    }
}
