package com.lushprojects.circuitjs1.client;

class PcbComponentPlacement {
    private final String componentId;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    PcbComponentPlacement(String componentId, int x, int y, int width, int height) {
        this.componentId = componentId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    String getComponentId() { return componentId; }
    int getX() { return x; }
    int getY() { return y; }
    int getWidth() { return width; }
    int getHeight() { return height; }
}