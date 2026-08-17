package com.lushprojects.circuitjs1.client;

/** Provider-owned selectable/hit-test region in screen coordinates. */
final class PhysicalPartRenderHitRegion {
    private final Rectangle bounds;

    PhysicalPartRenderHitRegion(Rectangle bounds) {
        if (bounds == null || bounds.width < 1 || bounds.height < 1)
            throw new IllegalArgumentException("Invalid physical render hit region");
        this.bounds = new Rectangle(bounds);
    }

    Rectangle getBounds() { return new Rectangle(bounds); }
    boolean contains(int x, int y) { return bounds.contains(x, y); }
}
