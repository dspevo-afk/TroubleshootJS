package com.lushprojects.circuitjs1.client;

class PcbPadPlacement {
    private final String padId;
    private final int x;
    private final int y;
    private final int escapeDx;
    private final int escapeDy;
    private final int escapeLength;

    PcbPadPlacement(String padId, int x, int y) {
        this(padId, x, y, 0, 0, 0);
    }

    PcbPadPlacement(String padId, int x, int y, int escapeDx, int escapeDy,
            int escapeLength) {
        if (escapeLength < 0 || Math.abs(escapeDx) + Math.abs(escapeDy) > 1 ||
                (escapeLength > 0 && escapeDx == 0 && escapeDy == 0))
            throw new IllegalArgumentException("Invalid PCB pad escape direction: " + padId);
        this.padId = padId;
        this.x = x;
        this.y = y;
        this.escapeDx = escapeDx;
        this.escapeDy = escapeDy;
        this.escapeLength = escapeLength;
    }

    String getPadId() { return padId; }
    int getX() { return x; }
    int getY() { return y; }
    int getEscapeDx() { return escapeDx; }
    int getEscapeDy() { return escapeDy; }
    int getEscapeLength() { return escapeLength; }

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
}
