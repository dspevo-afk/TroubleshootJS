package com.lushprojects.circuitjs1.client;

/** Screen-space drawer projection. Source package geometry and part identities never change. */
final class PartsTrayViewport {
    static final int HEIGHT = 172, CELL_PITCH = 184, MARGIN = 14;
    private int width = 1, height = 1, count, scroll;
    private boolean open;
    boolean isOpen() { return open; }
    int scroll() { return scroll; }
    int contentWidth() { return Math.max(width, MARGIN * 2 + count * CELL_PITCH); }
    int height() { return Math.min(HEIGHT, Math.max(60, height / 2)); }
    Rectangle bounds() { return new Rectangle(0, height - height(), width, height()); }
    void resize(int width, int height, int count) {
        if (width < 1 || height < 1 || count < 0 || count > 128)
            throw new IllegalArgumentException("Invalid tray viewport");
        this.width = width; this.height = height; this.count = count;
        set(open, scroll);
    }
    boolean set(boolean open, int offset) {
        int next = Math.max(0, Math.min(offset, Math.max(0, contentWidth() - width)));
        boolean changed = this.open != open || scroll != next;
        this.open = open; scroll = next; return changed;
    }
    Rectangle cell(int index) {
        if (index < 0 || index >= count) throw new IllegalArgumentException("Unknown tray cell");
        Rectangle tray = bounds();
        return new Rectangle(MARGIN + index * CELL_PITCH - scroll, tray.y + 48,
            CELL_PITCH - 24, Math.max(12, tray.height - 76));
    }
    boolean visible(int index) {
        if (!open || index < 0 || index >= count) return false;
        Rectangle c = cell(index);
        return c.x + c.width > 0 && c.x < width;
    }
    boolean contains(int x, int y) { return open && bounds().contains(x, y); }
    boolean contentContains(int x, int y) {
        Rectangle b = bounds();
        return contains(x, y) && y >= b.y + 36 && y < b.y + b.height - 20;
    }
}
