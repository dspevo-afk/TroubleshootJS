package com.lushprojects.circuitjs1.client;

/**
 * A compact, deterministic one-sided layout for the first RC proof family.
 * It consumes only stable board pads/nets and typed package geometry; the
 * renderer still owns all installed/loose part body and probe geometry.
 */
final class RcDelayPcbLayoutFactory {
    private RcDelayPcbLayoutFactory() { }

    static PcbBoardLayout create(TroubleshootBoard board, long seed) {
        PcbBoardLayout layout = new PcbBoardLayout(1400, 800,
            new Rectangle(40, 30, 770, 430), new Rectangle(850, 125, 150, 255));
        addComponents(layout, board, seed);
        addTraces(layout);
        addLabels(layout);
        int variationMode = (int) ((seed % 4 + 4) % 4);
        layout.compactToContent(40 + variationMode * 10,
            30 + (variationMode % 2) * 10, 26);
        layout.positionPartsTrayDisjointFromBoard();
        layout.validateGeometry(board);
        return layout;
    }

    private static void addComponents(PcbBoardLayout layout, TroubleshootBoard board, long seed) {
        addProviderFootprint(layout, board.getComponent("J1"), 50, 150, seed);
        addProviderFootprint(layout, board.getComponent("R1"), 200, 90, seed + 1);
        addProviderFootprint(layout, board.getComponent("C1"), 700, 70, seed + 2);
        addProviderFootprint(layout, board.getComponent("J2"), 900, 160, seed + 3);
        addProviderFootprint(layout, board.getComponent("R2"), 500, 290, seed + 4);
        addProviderFootprint(layout, board.getComponent("C2"), 200, 300, seed + 5);
    }

    private static void addProviderFootprint(PcbBoardLayout layout, BoardComponent component,
            int x, int y, long seed) {
        PcbFootprint footprint = StandardPcbFootprintProviders.createRegistry().create(
            component, x, y, new java.util.Random(seed), layout.getBoardOutline());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads())
            layout.addPad(pad);
    }

    private static void addTraces(PcbBoardLayout layout) {
        PcbPadPlacement j11 = layout.getPad("J1.1");
        PcbPadPlacement j12 = layout.getPad("J1.2");
        PcbPadPlacement r11 = layout.getPad("R1.1");
        PcbPadPlacement r12 = layout.getPad("R1.2");
        PcbPadPlacement r21 = layout.getPad("R2.1");
        PcbPadPlacement r22 = layout.getPad("R2.2");
        PcbPadPlacement c1p = layout.getPad("C1.+");
        PcbPadPlacement c1m = layout.getPad("C1.-");
        PcbPadPlacement c21 = layout.getPad("C2.1");
        PcbPadPlacement c22 = layout.getPad("C2.2");
        PcbPadPlacement j21 = layout.getPad("J2.1");
        PcbPadPlacement j22 = layout.getPad("J2.2");
        int j1EscapeX = escapeX(j11);
        int j1EscapeY = escapeY(j11);
        int j1ReturnEscapeX = escapeX(j12);
        int j1ReturnEscapeY = escapeY(j12);
        int r1OutputEscapeX = escapeX(r12);
        int r1Span = r12.getX() - 170;
        int r2InputEscapeX = escapeX(r21);

        trace(layout, "VIN", "J1.1", "R1.1", j11.getX(),j11.getY(),
            j1EscapeX + 10,j1EscapeY, j1EscapeX + 10,r11.getY(), r11.getX(),r11.getY());
        trace(layout, "VIN", "J1.1", "C2.1", j11.getX(),j11.getY(), 300,j11.getY(),
            300,275, c21.getX(),275, c21.getX(),c21.getY());

        trace(layout, "RC_OUT", "R1.2", "C1.+", r12.getX(),r12.getY(),
            r1OutputEscapeX,r12.getY(), r1OutputEscapeX,47, c1p.getX(),47,
            c1p.getX(),c1p.getY());
        trace(layout, "RC_OUT", "R1.2", "J2.1", r12.getX(),r12.getY(),
            r1OutputEscapeX,r12.getY(), r1OutputEscapeX,47, j21.getX(),47,
            j21.getX(),j21.getY());
        if (r1Span == 260) {
            trace(layout, "RC_OUT", "R1.2", "R2.1", r12.getX(),r12.getY(),
                r1OutputEscapeX,r12.getY(), r1OutputEscapeX,290,
                r2InputEscapeX,r21.getY(), r21.getX(),r21.getY());
        } else {
            trace(layout, "RC_OUT", "R1.2", "R2.1", r12.getX(),r12.getY(),
                r1OutputEscapeX,r12.getY(), r1OutputEscapeX,290,
                r2InputEscapeX,290, r2InputEscapeX,r21.getY(),
                r21.getX(),r21.getY());
        }

        trace(layout, "GND", "J1.2", "C1.-", j12.getX(),j12.getY(),
            j1ReturnEscapeX,j1ReturnEscapeY, j1ReturnEscapeX,400, 300,400, 300,365,
            850,365, 850,62, c1m.getX(),62, c1m.getX(),c1m.getY());
        trace(layout, "GND", "J1.2", "J2.2", j12.getX(),j12.getY(),
            j1ReturnEscapeX,j1ReturnEscapeY, j1ReturnEscapeX,400, 300,400, 300,365,
            1016,365, 1016,62, j22.getX(),62, j22.getX(),j22.getY());
        trace(layout, "GND", "J1.2", "C2.2", j12.getX(),j12.getY(),
            j1ReturnEscapeX,j1ReturnEscapeY, j1ReturnEscapeX,400, 300,400, 300,290,
            c22.getX(),290, c22.getX(),c22.getY());
        trace(layout, "GND", "J1.2", "R2.2", j12.getX(),j12.getY(),
            j1ReturnEscapeX,j1ReturnEscapeY, j1ReturnEscapeX,400, 300,400, 300,365,
            850,365, 850,320, r22.getX(),r22.getY());
    }

    private static int escapeX(PcbPadPlacement pad) {
        return pad.getX() + pad.getEscapeDx() * pad.getEscapeLength();
    }

    private static int escapeY(PcbPadPlacement pad) {
        return pad.getY() + pad.getEscapeDy() * pad.getEscapeLength();
    }

    private static void trace(PcbBoardLayout layout, String net, String start, String end,
            int... points) {
        int count = points.length / 2;
        int[] x = new int[count];
        int[] y = new int[count];
        for (int index = 0; index < count; index++) {
            x[index] = points[index * 2];
            y[index] = points[index * 2 + 1];
        }
        layout.addTrace(new PcbTraceGeometry(net, start, end, x, y));
    }

    private static void addLabels(PcbBoardLayout layout) {
        label(layout, "board-title", "TSJ RC DELAY", 300, 40, 108, 18, true, null);
        label(layout, "component:J1", "J1", 100, 285, 18, 18, true, null);
        label(layout, "component:R1", "R1", 300, 70, 18, 18, true, null);
        label(layout, "component:C1", "C1", 740, 52, 18, 18, true, null);
        label(layout, "component:J2", "J2", 980, 110, 18, 18, true, null);
        label(layout, "component:R2", "R2", 630, 270, 18, 18, true, null);
        label(layout, "component:C2", "C2", 310, 380, 18, 18, true, null);
        label(layout, "net:J1.1", "+V", 45, 100, 24, 16, false, "J1.1");
        label(layout, "net:J1.2", "GND", -10, 120, 32, 16, false, "J1.2");
    }

    private static void label(PcbBoardLayout layout, String id, String text, int x, int y,
            int width, int height, boolean bold, String targetPad) {
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text,
            new Rectangle(x, y, width, height), 14, bold, targetPad));
    }
}
