package com.lushprojects.circuitjs1.client;

class PcbPadPlacement {
    private final String padId;
    private final int x;
    private final int y;
    private final int escapeDx;
    private final int escapeDy;
    private final int escapeLength;
    private final PcbTerminalAttachment attachment;
    private final PcbBoardSide mountingSide;
    private final Rectangle padBounds;
    private final Rectangle probeBounds;

    PcbPadPlacement(String padId, int x, int y, int escapeDx, int escapeDy,
            int escapeLength, Rectangle padBounds, Rectangle probeBounds) {
        this(padId, x, y, escapeDx, escapeDy, escapeLength, padBounds, probeBounds,
            PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP);
    }

    PcbPadPlacement(String padId, int x, int y, int escapeDx, int escapeDy,
            int escapeLength, Rectangle padBounds, Rectangle probeBounds,
            PcbTerminalAttachment attachment, PcbBoardSide mountingSide) {
        if (padId == null || padId.length() == 0 || padBounds == null ||
                attachment == null || mountingSide == null ||
                probeBounds == null || padBounds.width <= 0 || padBounds.height <= 0 ||
                probeBounds.width <= 0 || probeBounds.height <= 0 ||
                !contains(padBounds, x, y) || !contains(probeBounds, padBounds))
            throw new IllegalArgumentException("Invalid PCB pad geometry: " + padId);
        PcbCoordinateSystem.requireBoardCoordinate(x);
        PcbCoordinateSystem.requireBoardCoordinate(y);
        PcbCoordinateSystem.requireBoardRectangle(padBounds);
        PcbCoordinateSystem.requireBoardRectangle(probeBounds);
        if (escapeLength < 0 || Math.abs((long)escapeDx) + Math.abs((long)escapeDy) > 1 ||
                (escapeLength > 0 && escapeDx == 0 && escapeDy == 0))
            throw new IllegalArgumentException("Invalid PCB pad escape direction: " + padId);
        PcbCoordinateSystem.requireBoardCoordinate((long)x + (long)escapeDx * escapeLength);
        PcbCoordinateSystem.requireBoardCoordinate((long)y + (long)escapeDy * escapeLength);
        this.padId = padId;
        this.x = x;
        this.y = y;
        this.escapeDx = escapeDx;
        this.escapeDy = escapeDy;
        this.escapeLength = escapeLength;
        this.attachment = attachment;
        this.mountingSide = mountingSide;
        this.padBounds = new Rectangle(padBounds);
        this.probeBounds = new Rectangle(probeBounds);
    }

    String getPadId() { return padId; }
    int getX() { return x; }
    int getY() { return y; }
    int getEscapeDx() { return escapeDx; }
    int getEscapeDy() { return escapeDy; }
    int getEscapeLength() { return escapeLength; }
    PcbTerminalAttachment getAttachment() { return attachment; }
    PcbBoardSide getMountingSide() { return mountingSide; }
    Rectangle getPadBounds() { return new Rectangle(padBounds); }
    Rectangle getProbeBounds() { return new Rectangle(probeBounds); }

    String geometryFingerprint() {
        return padId + '@' + x + ',' + y + " escape=" + escapeDx + ',' + escapeDy + ',' +
            escapeLength + " attachment=" + attachment + " mount=" + mountingSide +
            " pad=" + rectangleFingerprint(padBounds) + " probe=" +
            rectangleFingerprint(probeBounds);
    }

    boolean isInEscapeCorridor(int pointX, int pointY) {
        int dx = pointX - x;
        int dy = pointY - y;
        if (escapeLength == 0)
            return dx == 0 && dy == 0;
        if (dx * escapeDx + dy * escapeDy < 0 ||
                dx * escapeDx + dy * escapeDy > escapeLength)
            return false;
        return dx * escapeDy - dy * escapeDx == 0;
    }

    private static boolean contains(Rectangle outer, int x, int y) {
        return x >= outer.x && y >= outer.y &&
            (long) x <= (long) outer.x + outer.width &&
            (long) y <= (long) outer.y + outer.height;
    }

    private static boolean contains(Rectangle outer, Rectangle inner) {
        return inner.x >= outer.x && inner.y >= outer.y &&
            (long) inner.x + inner.width <= (long) outer.x + outer.width &&
            (long) inner.y + inner.height <= (long) outer.y + outer.height;
    }

    private static String rectangleFingerprint(Rectangle rectangle) {
        return rectangle.x + "," + rectangle.y + "," + rectangle.width + "," + rectangle.height;
    }
}
