package com.lushprojects.circuitjs1.client;

/** Explicit drill declaration. An NPTH has no electrical layer endpoints. */
final class PcbBoardHole {
    enum Kind { VIA, PLATED, NON_PLATED }
    final String id, netId;
    final Kind kind;
    final int x, y, drillRadius, landRadius;
    final PcbCopperAccess.Exposure exposure;

    PcbBoardHole(String id, Kind kind, String net, int x, int y, int drillRadius,
            int landRadius, PcbCopperLayer first, PcbCopperLayer second,
            PcbCopperAccess.Exposure exposure) {
        this.id = PcbConductorGraph.requireId(id);
        if (kind == null || exposure == null || drillRadius <= 0 || landRadius <= 0)
            throw new IllegalArgumentException("Invalid physical hole");
        if (kind == Kind.NON_PLATED) {
            if (net != null || first != null || second != null || landRadius != drillRadius)
                throw new IllegalArgumentException("Non-plated hole cannot declare copper");
        } else {
            PcbConductorGraph.requireId(net);
            if (first == null || second == null || first == second || landRadius <= drillRadius)
                throw new IllegalArgumentException("Plating requires two valid layer lands");
        }
        PcbCoordinateSystem.requireBoardCoordinate(x);
        PcbCoordinateSystem.requireBoardCoordinate(y);
        this.kind = kind; this.netId = net; this.x = x; this.y = y;
        this.drillRadius = drillRadius; this.landRadius = landRadius; this.exposure = exposure;
        PcbCoordinateSystem.requireBoardRectangle(getBounds());
    }
    Rectangle getBounds() {
        return new Rectangle(PcbCoordinateSystem.checkedInt((long)x - landRadius),
            PcbCoordinateSystem.checkedInt((long)y - landRadius),
            PcbCoordinateSystem.checkedInt(2L * landRadius),
            PcbCoordinateSystem.checkedInt(2L * landRadius));
    }
    PcbBoardHole translatedBy(int dx, int dy) {
        boolean plated = kind != Kind.NON_PLATED;
        return new PcbBoardHole(id, kind, netId,
            PcbCoordinateSystem.checkedInt((long)x + dx),
            PcbCoordinateSystem.checkedInt((long)y + dy), drillRadius, landRadius,
            plated ? PcbCopperLayer.TOP : null, plated ? PcbCopperLayer.BOTTOM : null, exposure);
    }
    String fingerprint() {
        return PcbConductorGraph.field(id) + kind + PcbConductorGraph.field(netId) + x + "," + y +
            "," + drillRadius + "," + landRadius + "," + exposure;
    }
}
