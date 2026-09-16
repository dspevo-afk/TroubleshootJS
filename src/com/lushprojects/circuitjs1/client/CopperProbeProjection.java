package com.lushprojects.circuitjs1.client;

/** Physical copper appearance only; a renderer never supplies an electrical result. */
interface CopperProbeProjection {
    boolean canProbeCopper(String surfaceId);
    Point getCopperPoint(String surfaceId);
    boolean isBoardPointVisible(Point point);
}
