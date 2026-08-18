package com.lushprojects.circuitjs1.client;

/** Provider-owned loose B/C/E probe target for a physical NPN part. */
final class PhysicalNpnPartProbeTarget extends PhysicalPartProbeTarget {
    PhysicalNpnPartProbeTarget(CirSim sim, GeneratedBoardInstance instance, String partId,
            int terminal, PcbWorkbenchRenderer renderer) {
        super(sim, instance, partId, terminal, renderer);
    }
}
