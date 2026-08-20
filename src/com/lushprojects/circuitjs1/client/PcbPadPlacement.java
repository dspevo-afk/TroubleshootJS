package com.lushprojects.circuitjs1.client;

class PcbPadPlacement {
    // These defaults exist only for legacy package-less callers.  Authoritative
    // package footprints always supply their declared pad/probe rectangles.
    private static final int LEGACY_PAD_SIZE = 26;
    private static final int LEGACY_PROBE_SIZE = 46;
    private final String padId;
    private final int x;
    private final int y;
    private final int escapeDx;
    private final int escapeDy;
    private final int escapeLength;
    private final Rectangle padBounds;
    private final Rectangle probeBounds;

    PcbPadPlacement(String padId, int x, int y) {
        this(padId, x, y, 0, 0, 0, centered(x, y, LEGACY_PAD_SIZE),
            centered(x, y, LEGACY_PROBE_SIZE));
    }

    PcbPadPlacement(String padId, int x, int y, int escapeDx, int escapeDy,
            int escapeLength) {
        this(padId, x, y, escapeDx, escapeDy, escapeLength,
            centered(x, y, LEGACY_PAD_SIZE), centered(x, y, LEGACY_PROBE_SIZE));
    }

    PcbPadPlacement(String padId, int x, int y, int escapeDx, int escapeDy,
            int escapeLength, Rectangle padBounds, Rectangle probeBounds) {
        if (padId == null || padId.length() == 0 || padBounds == null ||
                probeBounds == null || padBounds.width <= 0 || padBounds.height <= 0 ||
                probeBounds.width <= 0 || probeBounds.height <= 0 ||
                !contains(padBounds, x, y) || !contains(probeBounds, padBounds))
            throw new IllegalArgumentException("Invalid PCB pad geometry: " + padId);
        if (escapeLength < 0 || Math.abs(escapeDx) + Math.abs(escapeDy) > 1 ||
                (escapeLength > 0 && escapeDx == 0 && escapeDy == 0))
            throw new IllegalArgumentException("Invalid PCB pad escape direction: " + padId);
        this.padId = padId;
        this.x = x;
        this.y = y;
        this.escapeDx = escapeDx;
        this.escapeDy = escapeDy;
        this.escapeLength = escapeLength;
        this.padBounds = new Rectangle(padBounds);
        this.probeBounds = new Rectangle(probeBounds);
    }

    String getPadId() { return padId; }
    int getX() { return x; }
    int getY() { return y; }
    int getEscapeDx() { return escapeDx; }
    int getEscapeDy() { return escapeDy; }
    int getEscapeLength() { return escapeLength; }
    Rectangle getPadBounds() { return new Rectangle(padBounds); }
    Rectangle getProbeBounds() { return new Rectangle(probeBounds); }

    String geometryFingerprint() {
        return padId + '@' + x + ',' + y + " escape=" + escapeDx + ',' + escapeDy + ',' +
            escapeLength + " pad=" + rectangleFingerprint(padBounds) + " probe=" +
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

    private static Rectangle centered(int x, int y, int size) {
        return new Rectangle(x - size / 2, y - size / 2, size, size);
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
