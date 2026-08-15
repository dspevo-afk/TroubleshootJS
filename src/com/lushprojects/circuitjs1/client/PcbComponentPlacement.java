package com.lushprojects.circuitjs1.client;

class PcbComponentPlacement {
    private final String componentId;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Rectangle keepOut;

    PcbComponentPlacement(String componentId, int x, int y, int width, int height) {
        this(componentId, x, y, width, height, new Rectangle(x, y, width, height));
    }

    PcbComponentPlacement(String componentId, int x, int y, int width, int height,
            Rectangle keepOut) {
        if (keepOut == null || keepOut.width <= 0 || keepOut.height <= 0)
            throw new IllegalArgumentException("Invalid PCB component keep-out: " + componentId);
        this.componentId = componentId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.keepOut = new Rectangle(keepOut);
    }

    String getComponentId() { return componentId; }
    int getX() { return x; }
    int getY() { return y; }
    int getWidth() { return width; }
    int getHeight() { return height; }
    Rectangle getBounds() { return new Rectangle(x, y, width, height); }
    Rectangle getKeepOut() { return new Rectangle(keepOut); }
}
