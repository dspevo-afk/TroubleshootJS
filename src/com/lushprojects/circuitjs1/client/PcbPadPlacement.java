package com.lushprojects.circuitjs1.client;

class PcbPadPlacement {
    private final String padId;
    private final int x;
    private final int y;

    PcbPadPlacement(String padId, int x, int y) {
        this.padId = padId;
        this.x = x;
        this.y = y;
    }

    String getPadId() { return padId; }
    int getX() { return x; }
    int getY() { return y; }
}