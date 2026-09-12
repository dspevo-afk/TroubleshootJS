package com.lushprojects.circuitjs1.client;

/** Immutable routed polyline. Caller-owned coordinate arrays are never retained or exposed. */
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
        for (int index = 0; index < xPoints.length; index++) {
            PcbCoordinateSystem.requireBoardCoordinate(xPoints[index]);
            PcbCoordinateSystem.requireBoardCoordinate(yPoints[index]);
        }
        this.netId = netId;
        this.startPadId = startPadId;
        this.endPadId = endPadId;
        this.xPoints = copy(xPoints);
        this.yPoints = copy(yPoints);
    }

    String getNetId() { return netId; }
    String getStartPadId() { return startPadId; }
    String getEndPadId() { return endPadId; }
    int[] getXPoints() { return copy(xPoints); }
    int[] getYPoints() { return copy(yPoints); }

    private static int[] copy(int[] values) {
        int[] result = new int[values.length];
        System.arraycopy(values, 0, result, 0, values.length);
        return result;
    }
}
