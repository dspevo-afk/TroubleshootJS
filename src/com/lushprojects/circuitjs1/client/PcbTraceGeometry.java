package com.lushprojects.circuitjs1.client;

class PcbTraceGeometry {
    private final String netId;
    private final String startPadId;
    private final String endPadId;
    private final int[] xPoints;
    private final int[] yPoints;

    PcbTraceGeometry(String netId, int[] xPoints, int[] yPoints) {
        this(netId, null, null, xPoints, yPoints);
    }

    PcbTraceGeometry(String netId, String startPadId, String endPadId,
            int[] xPoints, int[] yPoints) {
        if (xPoints == null || yPoints == null || xPoints.length < 2 ||
                xPoints.length != yPoints.length)
            throw new IllegalArgumentException("Invalid PCB trace geometry: " + netId);
        this.netId = netId;
        this.startPadId = startPadId;
        this.endPadId = endPadId;
        this.xPoints = xPoints;
        this.yPoints = yPoints;
    }

    String getNetId() { return netId; }
    String getStartPadId() { return startPadId; }
    String getEndPadId() { return endPadId; }
    int[] getXPoints() { return xPoints; }
    int[] getYPoints() { return yPoints; }
}
