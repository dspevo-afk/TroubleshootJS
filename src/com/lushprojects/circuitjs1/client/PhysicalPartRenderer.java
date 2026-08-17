package com.lushprojects.circuitjs1.client;

/** Provider-owned rendering, geometry, and probe-target boundary. */
interface PhysicalPartRenderer {
    PhysicalPartRenderGeometry getInstalledGeometry(PhysicalPartRenderContext context);
    PhysicalPartRenderGeometry getLooseGeometry(PhysicalPartRenderContext context);
    void drawInstalled(Graphics graphics, PhysicalPartRenderContext context,
            PhysicalPartRenderGeometry geometry, boolean selected);
    void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
            PhysicalPartRenderGeometry geometry, boolean selected);
    ProbeTarget createInstalledProbeTarget(CirSim sim, PhysicalPartRenderContext context,
            int terminal);
    ProbeTarget createLooseProbeTarget(CirSim sim, PhysicalPartRenderContext context,
            int terminal);
}
