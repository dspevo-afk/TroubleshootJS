package com.lushprojects.circuitjs1.client;

/** Immutable routed polyline. Caller-owned coordinate arrays are never retained or exposed. */
class PcbTraceGeometry {
    private final String netId;
    private final String sourceId;
    private final PcbCopperLayer layer;
    private final PcbCopperAccess.Exposure exposure;
    private final String startPadId;
    private final String endPadId;
    private final int[] xPoints;
    private final int[] yPoints;

    PcbTraceGeometry(String netId, int[] xPoints, int[] yPoints) {
        this(netId, null, null, xPoints, yPoints);
    }

    PcbTraceGeometry(String netId, String startPadId, String endPadId,
            int[] xPoints, int[] yPoints) {
        this(defaultSourceId(startPadId, endPadId, PcbCopperLayer.TOP), netId,
            startPadId, endPadId, PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
            xPoints, yPoints);
    }

    PcbTraceGeometry(String sourceId, String netId, String startPadId, String endPadId,
            PcbCopperLayer layer, PcbCopperAccess.Exposure exposure,
            int[] xPoints, int[] yPoints) {
        if (layer == null || exposure == null)
            throw new IllegalArgumentException("Missing PCB copper layer/access");
        if (sourceId != null) PcbConductorGraph.requireId(sourceId);
        if (xPoints == null || yPoints == null || xPoints.length < 2 ||
                xPoints.length != yPoints.length)
            throw new IllegalArgumentException("Invalid PCB trace geometry: " + netId);
        for (int index = 0; index < xPoints.length; index++) {
            PcbCoordinateSystem.requireBoardCoordinate(xPoints[index]);
            PcbCoordinateSystem.requireBoardCoordinate(yPoints[index]);
        }
        this.netId = netId;
        this.sourceId = sourceId;
        this.layer = layer;
        this.exposure = exposure;
        this.startPadId = startPadId;
        this.endPadId = endPadId;
        this.xPoints = copy(xPoints);
        this.yPoints = copy(yPoints);
    }

    String getNetId() { return netId; }
    String getSourceId() { return sourceId; }
    PcbCopperLayer getLayer() { return layer; }
    PcbCopperAccess.Exposure getExposure() { return exposure; }

    PcbTraceGeometry withPath(int[] x, int[] y) {
        return new PcbTraceGeometry(sourceId, netId, startPadId, endPadId, layer, exposure, x, y);
    }

    static String defaultSourceId(String start, String end, PcbCopperLayer layer) {
        if (start == null || end == null) return null; // Geometry-only test/statistic, not routed copper.
        String first = start.compareTo(end) <= 0 ? start : end;
        String second = start.compareTo(end) <= 0 ? end : start;
        return "route/" + layer + "/" + first.length() + ":" + first + second.length() + ":" + second;
    }
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
