package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Developer-only evidence returned by the real renderer canary path. */
final class PhysicalPartRenderCanaryResult {
    private final PhysicalPartRenderGeometry geometry;
    private final String hitComponentId;
    private final Vector<ProbeTarget> probeTargets;
    private final boolean bodyDrawn;

    PhysicalPartRenderCanaryResult(PhysicalPartRenderGeometry geometry,
            String hitComponentId, Vector<ProbeTarget> probeTargets, boolean bodyDrawn) {
        this.geometry = geometry;
        this.hitComponentId = hitComponentId;
        this.probeTargets = new Vector<ProbeTarget>(probeTargets);
        this.bodyDrawn = bodyDrawn;
    }

    PhysicalPartRenderGeometry getGeometry() { return geometry; }
    String getHitComponentId() { return hitComponentId; }
    Vector<ProbeTarget> getProbeTargets() { return new Vector<ProbeTarget>(probeTargets); }
    boolean wasBodyDrawn() { return bodyDrawn; }
}
