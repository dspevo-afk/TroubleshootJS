package com.lushprojects.circuitjs1.client;

/** One composed board-to-canvas transform. Camera state never changes physical identity. */
final class PcbViewport {
    static final double MAX_SCALE = 8;
    private final Rectangle outline;
    private Rectangle workbenchBounds;
    private Rectangle area = new Rectangle(0, 0, 1, 1);
    private PcbBoardSide face = PcbBoardSide.TOP;
    private double scale = 1, x, y;
    private boolean initialized, inspecting;
    private double magnification = 3;
    private int cursorX, cursorY;
    static final class State {
        private final double scale, x, y, magnification;
        private final int cursorX, cursorY;
        private final PcbBoardSide face;
        private final boolean inspecting;
        State(PcbViewport v) {
            scale=v.scale; x=v.x; y=v.y; magnification=v.magnification;
            cursorX=v.cursorX; cursorY=v.cursorY; face=v.face; inspecting=v.inspecting;
        }
    }
    State capture() { return new State(this); }
    void restore(State s) {
        if (s == null) throw new IllegalArgumentException("Missing viewport state");
        scale=s.scale; x=s.x; y=s.y; magnification=s.magnification;
        cursorX=s.cursorX; cursorY=s.cursorY; face=s.face; inspecting=s.inspecting;
    }

    /** Immutable forward/inverse projection shared by rendering, markers and target bounds. */
    static final class Transform {
        final double scale, x, y, mirror;
        final boolean flipped;
        Transform(double scale, double x, double y, double mirror, boolean flipped) {
            this.scale = scale; this.x = x; this.y = y;
            this.mirror = mirror; this.flipped = flipped;
        }
        double screenX(double boardX) { return x + scale * (flipped ? mirror - boardX : boardX); }
        double screenY(double boardY) { return y + scale * boardY; }
        double boardX(double screenX) {
            double value = (screenX - x) / scale;
            return flipped ? mirror - value : value;
        }
        double boardY(double screenY) { return (screenY - y) / scale; }
        Point project(Point point) {
            return point == null ? null : new Point((int)Math.round(screenX(point.x)),
                (int)Math.round(screenY(point.y)));
        }
        Rectangle project(Rectangle rect) {
            double left = screenX(rect.x), right = screenX((double)rect.x + rect.width);
            int sx = (int)Math.round(Math.min(left, right));
            int sy = (int)Math.round(screenY(rect.y));
            // A positive physical feature still occupies a raster pixel when
            // zoomed out. Rendering and hit testing share this same envelope;
            // overlapping projected targets remain ambiguous at the resolver.
            return new Rectangle(sx, sy, Math.max(rect.width > 0 ? 1 : 0,
                (int)Math.round(Math.max(left, right)) - sx), Math.max(rect.height > 0 ? 1 : 0,
                (int)Math.round(screenY((double)rect.y + rect.height)) - sy));
        }
    }

    PcbViewport(Rectangle outline) {
        PcbCoordinateSystem.requireBoardRectangle(outline);
        if (outline.width <= 0 || outline.height <= 0) throw new IllegalArgumentException("Empty board");
        this.outline = new Rectangle(outline);
        workbenchBounds = new Rectangle(outline);
    }
    void setWorkbenchBounds(Rectangle bounds) {
        PcbCoordinateSystem.requireBoardRectangle(bounds);
        if (bounds.width <= 0 || bounds.height <= 0) throw new IllegalArgumentException("Empty workbench");
        workbenchBounds = new Rectangle(bounds);
    }
    Rectangle getArea() { return new Rectangle(area); }
    boolean contains(int sx, int sy) { return area.contains(sx, sy); }
    void resize(Rectangle value) {
        if (value == null || value.width < 1 || value.height < 1)
            throw new IllegalArgumentException("Empty board viewport");
        if (area.x == value.x && area.y == value.y && area.width == value.width &&
                area.height == value.height && initialized) return;
        dismiss();
        if (initialized) {
            x += value.x - area.x + (value.width - area.width) / 2.0;
            y += value.y - area.y + (value.height - area.height) / 2.0;
        }
        area = new Rectangle(value);
        if (!initialized) { initialized = true; fit(outline); }
        constrainPan();
    }
    PcbBoardSide getFace() { return face; }
    void setFace(PcbBoardSide value) {
        if (value == null) throw new IllegalArgumentException("Missing board face");
        dismiss(); face = value;
    }
    void fitBoard() { fit(outline); }
    void fit(Rectangle target) {
        PcbCoordinateSystem.requireBoardRectangle(target);
        if (target.width <= 0 || target.height <= 0) throw new IllegalArgumentException("Empty target");
        dismiss();
        scale = Math.min(MAX_SCALE, Math.min(Math.max(1, area.width - 40) / (double)target.width,
            Math.max(1, area.height - 40) / (double)target.height));
        double cx = target.x + target.width / 2.0;
        if (face == PcbBoardSide.BOTTOM) cx = mirror() - cx;
        x = area.x + area.width / 2.0 - scale * cx;
        y = area.y + area.height / 2.0 - scale * (target.y + target.height / 2.0);
    }
    void pan(double dx, double dy) {
        if (!finite(dx) || !finite(dy)) throw new IllegalArgumentException("Invalid camera motion");
        if (inspecting) return;
        x = Math.max(-1e9, Math.min(1e9, x + dx));
        y = Math.max(-1e9, Math.min(1e9, y + dy));
        constrainPan();
    }
    private void constrainPan() {
        // Limit the PCB itself, never the larger bench/tray envelope. At high
        // zoom half the maximum visible PCB area replaces an impossible demand
        // that half an oversized board fit on screen. Diagonal drags obey the
        // same area bound as horizontal and vertical drags.
        double width = outline.width * scale, height = outline.height * scale;
        double left = x + outline.x * scale, top = y + outline.y * scale;
        double fullLeft = clamp(left, Math.min(area.x, area.x + area.width - width),
            Math.max(area.x, area.x + area.width - width));
        double fullTop = clamp(top, Math.min(area.y, area.y + area.height - height),
            Math.max(area.y, area.y + area.height - height));
        double required = .5 * Math.min(width, area.width) * Math.min(height, area.height);
        if (visibleArea(left, top, width, height) >= required) return;
        double low = 0, high = 1;
        for (int i = 0; i < 40; i++) {
            double fraction = (low + high) / 2;
            if (visibleArea(fullLeft + (left - fullLeft) * fraction,
                    fullTop + (top - fullTop) * fraction, width, height) >= required)
                low = fraction;
            else high = fraction;
        }
        x = fullLeft + (left - fullLeft) * low - outline.x * scale;
        y = fullTop + (top - fullTop) * low - outline.y * scale;
    }
    private double visibleArea(double left, double top, double width, double height) {
        return Math.max(0, Math.min(left + width, area.x + area.width) - Math.max(left, area.x)) *
            Math.max(0, Math.min(top + height, area.y + area.height) - Math.max(top, area.y));
    }
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    void zoom(double factor, int sx, int sy) {
        if (!(factor > 0) || !finite(factor)) throw new IllegalArgumentException("Invalid zoom");
        if (!contains(sx, sy)) return;
        if (inspecting) {
            magnification = Math.max(1.5, Math.min(8, magnification * factor));
            moveCursor(sx, sy); return;
        }
        double minimum = Math.min(area.width / (double)outline.width,
            area.height / (double)outline.height) / 4;
        double next = Math.max(minimum, Math.min(MAX_SCALE, scale * factor));
        x = sx - (sx - x) * next / scale;
        y = sy - (sy - y) * next / scale;
        scale = next;
        constrainPan();
    }
    boolean inspect(int sx, int sy) {
        if (!contains(sx, sy)) return false;
        if (!inspecting) { inspecting = true; magnification = 3; }
        moveCursor(sx, sy); return true;
    }
    void moveCursor(int sx, int sy) { cursorX = sx; cursorY = sy; }
    void dismiss() { inspecting = false; }
    boolean isInspecting() { return inspecting; }
    double getMagnification() { return inspecting ? magnification : 1; }
    Transform permanent() { return new Transform(scale, x, y, mirror(), face == PcbBoardSide.BOTTOM); }
    Transform current() {
        double m = getMagnification();
        return new Transform(scale * m, cursorX + (x - cursorX) * m,
            cursorY + (y - cursorY) * m, mirror(), face == PcbBoardSide.BOTTOM);
    }
    private double mirror() { return (double)outline.x * 2 + outline.width; }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
