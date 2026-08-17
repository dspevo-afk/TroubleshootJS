package com.lushprojects.circuitjs1.client;

/** Typed loose-probe behavior contributed by a physical part's render metadata. */
interface PhysicalPartRenderProbeProvider {
    ProbeTarget createLooseProbeTarget(CirSim sim, GeneratedBoardInstance instance,
            PhysicalPart<?> part, int terminal, PcbWorkbenchRenderer renderer);
}
