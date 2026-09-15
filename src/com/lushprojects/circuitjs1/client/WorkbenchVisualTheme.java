package com.lushprojects.circuitjs1.client;

/** Shared paint palette and bounded material primitives; never owns geometry or electrical state. */
final class WorkbenchVisualTheme {
    static final String FONT = "'Segoe UI', sans-serif";
    static final String MAT = "#d9dfdb";
    static final String MAT_EDGE = "#c2cbc5";
    static final String BOARD = "#285d49";
    static final String BOARD_EDGE = "#163c30";
    static final String BOARD_BEVEL = "#61806b";
    // Covered trace appearance. Exposure/access remains owned by physical realization.
    static final String COPPER = "#689879";
    static final String COPPER_LIGHT = "#86b395";
    static final String SILK = "#f0efda";
    static final String SILK_SECONDARY = "#d0dfcf";
    static final String PLATING = "#bdab7d";
    static final String PLATING_LIGHT = "#e9d9a6";
    static final String DRILL = "#142721";
    static final String METAL = "#83938e";
    static final String METAL_LIGHT = "#dce3de";
    static final String BODY = "#252e30";
    static final String BODY_LIGHT = "#4d5959";
    static final String BODY_EDGE = "#162022";
    static final String RESISTOR = "#d8c8a1";
    static final String CERAMIC = "#c18b59";
    static final String CONNECTOR = "#527a6c";
    static final String CONNECTOR_LIGHT = "#80a18d";
    static final String TRAY = "#aeb7b5";
    static final String TRAY_INSET = "#c6ceca";
    static final String TEXT = "#31483f";
    static final String MUTED_TEXT = "#596e63";
    static final String SELECTION = "#eed9a2";
    static final String SHADOW = "rgba(9, 28, 22, 0.18)";

    private WorkbenchVisualTheme() { }

    /** Subtle, irregular maple fibres fixed in bench space as the camera moves. */
    static void wood(Graphics g, Rectangle area, PcbViewport.Transform camera) {
        g.setColor("#b99a74"); g.fillRect(0, 0, area.width, area.height);
        double scale = camera.scale, plank = 160;
        double left = -camera.x / scale, right = (area.width - camera.x) / scale;
        int first = (int)Math.floor(-camera.y / (plank * scale)) - 1;
        int last = (int)Math.ceil((area.height - camera.y) / (plank * scale));
        int fibres = Math.max(1, (int)Math.ceil(2.5 / (9 * scale)));
        g.setLineWidth(1);
        for (int board = first; board <= last; board++) {
            double top = board * plank, sy = camera.y + top * scale;
            double variation = Math.sin(board * 13.71);
            g.setColor(variation > 0 ? "rgba(255,230,183,.065)" : "rgba(86,54,30,.035)");
            g.fillRect(0, (int)sy, area.width, (int)Math.ceil(plank * scale));
            g.setColor("rgba(90,58,34,.16)"); g.drawLine(0, (int)sy, area.width, (int)sy);
            g.setColor("rgba(249,226,187,.16)"); g.drawLine(0, (int)sy + 1, area.width, (int)sy + 1);
            for (int fibre = 1; fibre < 18; fibre += fibres) {
                double row = top + fibre * 8.7 + Math.sin(fibre * 11.17 + board) * 1.4;
                g.setColor(fibre % 4 == 0 ? "rgba(250,226,183,.19)" : "rgba(91,56,29,.105)");
                g.context.beginPath();
                double step = 80;
                double start = Math.floor(left / step) * step;
                g.context.moveTo(camera.x + start * scale, camera.y + grainHeight(start, row, board) * scale);
                for (double wx = start; wx < right; wx += step) {
                    double end = wx + step;
                    g.context.bezierCurveTo(camera.x + (wx + step / 3) * scale,
                        camera.y + grainHeight(wx + step / 3, row, board) * scale,
                        camera.x + (wx + step * 2 / 3) * scale,
                        camera.y + grainHeight(wx + step * 2 / 3, row, board) * scale,
                        camera.x + end * scale, camera.y + grainHeight(end, row, board) * scale);
                }
                g.context.stroke();
            }
        }
    }

    private static double grainHeight(double x, double row, int board) {
        double center = board * 160 + 76;
        double knot = 420 + Math.sin(board * 7.31) * 260;
        double along = (x - knot) / 95, across = (row - center) / 34;
        double curl = (row < center ? -1 : 1) * 14 * Math.exp(-along * along - across * across);
        return row + curl + Math.sin(x * .005 + board * 3.1) * 2.3 +
            Math.sin(x * .018 + row * .08) * .6;
    }

    static void metalTray(Graphics g, Rectangle b, double scale) {
        int rim = Math.max(2, (int)Math.round(10 * scale));
        double radius = Math.max(2, 14 * scale);
        g.setColor("rgba(45, 31, 21, .22)");
        roundedPath(g, b.x + 3 * scale, b.y + 7 * scale, b.width, b.height, radius);
        g.context.fill();
        g.setColor(TRAY);
        roundedPath(g, b.x, b.y, b.width, b.height, radius);
        g.context.fill();
        g.setColor("#e3e7e2");
        g.setLineWidth(Math.max(1, 2 * scale));
        roundedPath(g, b.x + 1, b.y + 1, b.width - 2, b.height - 2, radius);
        g.context.stroke();
        g.setColor("#7f8c86");
        roundedPath(g, b.x + rim, b.y + rim, b.width - rim * 2, b.height - rim * 2, radius * .6);
        g.context.fill();
        g.setColor(TRAY_INSET);
        roundedPath(g, b.x + rim + 1, b.y + rim + Math.max(1, scale * 2),
            b.width - rim * 2 - 2, b.height - rim * 2 - Math.max(2, scale * 2), radius * .6);
        g.context.fill();
        g.setLineWidth(1);
    }

    static void roundedPath(Graphics g, double x, double y, double w, double h, double r) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        g.context.beginPath();
        g.context.moveTo(x + r, y);
        g.context.lineTo(x + w - r, y);
        g.context.quadraticCurveTo(x + w, y, x + w, y + r);
        g.context.lineTo(x + w, y + h - r);
        g.context.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
        g.context.lineTo(x + r, y + h);
        g.context.quadraticCurveTo(x, y + h, x, y + h - r);
        g.context.lineTo(x, y + r);
        g.context.quadraticCurveTo(x, y, x + r, y);
        g.context.closePath();
    }

    // Unlike legacy Graphics.fillOval, this honors both supplied dimensions.
    static void ellipsePath(Graphics g, double x, double y, double w, double h) {
        double rx = w / 2, ry = h / 2, cx = x + rx, cy = y + ry;
        double k = 0.5522847498;
        g.context.beginPath();
        g.context.moveTo(cx + rx, cy);
        g.context.bezierCurveTo(cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry);
        g.context.bezierCurveTo(cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy);
        g.context.bezierCurveTo(cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry);
        g.context.bezierCurveTo(cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy);
        g.context.closePath();
    }

    static void ellipse(Graphics g, double x, double y, double w, double h, String color) {
        if (w <= 0 || h <= 0) return;
        g.setColor(color);
        ellipsePath(g, x, y, w, h);
        g.context.fill();
    }

    static void body(Graphics g, Rectangle b, String color, String light, boolean capsule) {
        if (b.width <= 0 || b.height <= 0) return;
        double radius = capsule ? Math.min(b.width, b.height) * .38 : Math.min(3, b.height * .1);
        g.setColor(SHADOW);
        roundedPath(g, b.x + 1, b.y + 2, b.width, b.height, radius);
        g.context.fill();
        g.setColor(color);
        roundedPath(g, b.x, b.y, b.width, b.height, radius);
        g.context.fill();
        g.context.save();
        g.context.clip();
        g.setColor(light);
        g.fillRect(b.x + 1, b.y + 1, Math.max(0, b.width - 2), Math.max(1, b.height / 5));
        g.setColor(SHADOW);
        g.fillRect(b.x, b.y + b.height * 4 / 5, b.width, Math.max(1, b.height / 5));
        g.context.restore();
        g.setColor(BODY_EDGE);
        g.setLineWidth(1);
        roundedPath(g, b.x + .5, b.y + .5, Math.max(0, b.width - 1),
            Math.max(0, b.height - 1), radius);
        g.context.stroke();
    }

    static void lead(Graphics g, Point start, Point end, int width) {
        g.setColor(METAL);
        g.setLineWidth(width);
        g.drawLine(start.x, start.y, end.x, end.y);
        if (width > 1) {
            g.setColor(METAL_LIGHT);
            g.setLineWidth(Math.max(1, width / 3));
            g.drawLine(start.x, start.y, end.x, end.y);
        }
        g.setLineWidth(1);
    }

    static void platedLand(Graphics g, int x, int y, int radius) {
        ellipse(g, x - radius, y - radius, radius * 2, radius * 2, PLATING);
        if (radius > 2)
            ellipse(g, x - radius + 1, y - radius + 1, radius * 2 - 2,
                radius * 2 - 2, PLATING_LIGHT);
    }

    /** Stripe follows the physical terminal axis, including rotations and reversals. */
    static void polarityStripe(Graphics g, Rectangle b, Point first, Point marked,
            int thickness, String color) {
        g.setColor(color);
        boolean horizontal = Math.abs(marked.x - first.x) >= Math.abs(marked.y - first.y);
        int t = Math.max(1, Math.min(thickness, (horizontal ? b.width : b.height) / 4));
        if (horizontal)
            g.fillRect(marked.x < first.x ? b.x + 1 : b.x + b.width - t - 1,
                b.y + 1, t, Math.max(1, b.height - 2));
        else
            g.fillRect(b.x + 1, marked.y < first.y ? b.y + 1 : b.y + b.height - t - 1,
                Math.max(1, b.width - 2), t);
    }

    static void marking(Graphics g, String text, Rectangle b, int size, String color) {
        if (text == null || b.width < 12 || b.height < 9) return;
        Font previousFont = g.currentFont;
        int previousSize = g.currentFontSize;
        g.context.save();
        g.clipRect(b.x + 1, b.y + 1, Math.max(0, b.width - 2), Math.max(0, b.height - 2));
        g.setFont(new Font(FONT, Font.BOLD, Math.max(7, Math.min(size, b.height - 2))));
        double width = g.measureWidth(text);
        if (width > b.width - 4) {
            int fit = (int)Math.floor(g.currentFontSize * (b.width - 4) / width);
            // Small packages gain legible printed detail as the player zooms in.
            // Do not crop a value into a different apparent marking.
            if (fit < 7) {
                g.context.restore();
                g.currentFont = previousFont;
                g.currentFontSize = previousSize;
                return;
            }
            g.setFont(new Font(FONT, Font.BOLD, fit));
            width = g.measureWidth(text);
        }
        g.setColor(color);
        g.drawString(text, b.x + (int)Math.round((b.width - width) / 2),
            b.y + (b.height + g.currentFontSize) / 2 - 2);
        g.context.restore();
        g.currentFont = previousFont;
        g.currentFontSize = previousSize;
    }
}
