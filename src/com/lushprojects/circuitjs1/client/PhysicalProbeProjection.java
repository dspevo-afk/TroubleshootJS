package com.lushprojects.circuitjs1.client;

/** Presentation coordinates/access only. No canvas, engine, solver or mutation capability. */
interface PhysicalProbeProjection {
    boolean hasPad(String padId);
    boolean canProbePad(String padId);
    Point getPadPoint(String padId);
    Point getComponentLeadPoint(String componentId, String padId);
    Point getLooseTerminalPoint(String partId, int terminal);
    boolean isBoardPointVisible(Point point);
    Object captureInstalledTargetIdentity(String componentId, String padId);
    boolean isInstalledTargetIdentityCurrent(String componentId, String padId, Object token);
    Object captureLooseProjectionToken();
    boolean isLooseProjectionTokenCurrent(Object token);
    boolean isLoosePartVisibleOnCurrentPage(String partId);
}
