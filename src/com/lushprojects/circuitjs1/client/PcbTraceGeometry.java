package com.lushprojects.circuitjs1.client;

class PcbTraceGeometry {
    private final String netId;
    private final int[] xPoints;
    private final int[] yPoints;

    PcbTraceGeometry(String netId, int[] xPoints, int[] yPoints) {
        if (xPoints == null || yPoints == null || xPoints.length < 2 ||
                xPoints.length != yPoints.length)
            throw new IllegalArgumentException("Invalid PCB trace geometry: " + netId);
        this.netId = netId;
        this.xPoints = xPoints;
        this.yPoints = yPoints;
    }

    String getNetId() { return netId; }
    int[] getXPoints() { return xPoints; }
    int[] getYPoints() { return yPoints; }
}