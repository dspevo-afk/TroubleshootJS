package com.lushprojects.circuitjs1.client;

/**
 * A compact, deterministic one-sided layout for the first RC proof family.
 * It consumes only stable board pads/nets and typed package geometry; the
 * renderer still owns all installed/loose part body and probe geometry.
 */
final class RcDelayPcbLayoutFactory {
    private RcDelayPcbLayoutFactory() { }

    static PcbBoardLayout create(TroubleshootBoard board, long seed) {
        PcbBoardLayout layout = new PcbBoardLayout(1040, 520,
            new Rectangle(40, 30, 770, 430), new Rectangle(850, 125, 150, 255));
        addComponents(layout);
        addPads(layout);
        addTraces(layout);
        addLabels(layout);
        int variationMode = (int) ((seed % 4 + 4) % 4);
        layout.compactToContent(40 + variationMode * 10,
            30 + (variationMode % 2) * 10, 26);
        layout.positionPartsTrayDisjointFromBoard();
        layout.validateGeometry(board);
        return layout;
    }

    private static void addComponents(PcbBoardLayout layout) {
        layout.addComponent(component("J1", 80, 150, 70, 100, 90, 160, 50, 80));
        layout.addComponent(resistorComponent("R1", 250, 90, 180, 50, 280, 105, 120, 20));
        layout.addComponent(component("C1", 450, 70, 100, 100, 460, 80, 80, 80));
        layout.addComponent(component("J2", 690, 160, 70, 80, 700, 170, 50, 60));
        layout.addComponent(resistorComponent("R2", 540, 290, 180, 50, 570, 305, 120, 20));
        layout.addComponent(component("C2", 200, 300, 80, 70, 210, 310, 60, 40));
    }

    private static PcbComponentPlacement component(String id, int x, int y, int width,
            int height, int bodyX, int bodyY, int bodyWidth, int bodyHeight) {
        Rectangle body = new Rectangle(bodyX, bodyY, bodyWidth, bodyHeight);
        return new PcbComponentPlacement(id, x, y, width, height, body, body);
    }

    private static PcbComponentPlacement resistorComponent(String id, int x, int y, int width,
            int height, int bodyX, int bodyY, int bodyWidth, int bodyHeight) {
        Rectangle body = new Rectangle(bodyX, bodyY, bodyWidth, bodyHeight);
        return new PcbComponentPlacement(id, x, y, width, height, body,
            new Rectangle(x, y, width, height));
    }

    private static void addPads(PcbBoardLayout layout) {
        pad(layout, "J1.1", 80, 180, -1, 0); pad(layout, "J1.2", 80, 220, -1, 0);
        pad(layout, "J2.1", 690, 180, -1, 0); pad(layout, "J2.2", 690, 220, -1, 0);
        pad(layout, "R1.1", 240, 120, -1, 0); pad(layout, "R1.2", 440, 120, 1, 0);
        pad(layout, "R2.1", 530, 320, -1, 0); pad(layout, "R2.2", 730, 320, 1, 0);
        pad(layout, "C1.+", 450, 80, -1, 0); pad(layout, "C1.-", 550, 140, 1, 0);
        pad(layout, "C2.1", 190, 320, -1, 0); pad(layout, "C2.2", 190, 350, -1, 0);
    }

    private static void pad(PcbBoardLayout layout, String id, int x, int y, int dx, int dy) {
        layout.addPad(new PcbPadPlacement(id, x, y, dx, dy, 20));
    }

    private static void addTraces(PcbBoardLayout layout) {
        trace(layout, "VIN", "J1.1", "R1.1", 80,180, 60,180, 60,120, 240,120);
        trace(layout, "VIN", "J1.1", "C2.1", 80,180, 60,180, 60,140, 150,140,
            150,320, 190,320);

        trace(layout, "RC_OUT", "R1.2", "C1.+", 440,120, 450,120, 450,90, 440,90,
            440,80, 450,80);
        trace(layout, "RC_OUT", "R1.2", "J2.1", 440,120, 450,120, 450,180, 690,180);
        trace(layout, "RC_OUT", "R1.2", "R2.1", 440,120, 450,120, 450,180, 500,180,
            500,320, 530,320);

        trace(layout, "GND", "J1.2", "C1.-", 80,220, 60,220, 60,400, 780,400,
            780,260, 760,260, 760,150, 560,150, 560,140, 550,140);
        trace(layout, "GND", "J1.2", "J2.2", 80,220, 60,220, 60,420, 780,420,
            780,250, 660,250, 660,220, 690,220);
        trace(layout, "GND", "J1.2", "C2.2", 80,220, 60,220, 60,380, 150,380,
            150,350, 190,350);
        trace(layout, "GND", "J1.2", "R2.2", 80,220, 60,220, 60,440, 740,440,
            740,320, 730,320);
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
        label(layout, "component:J1", "J1", 100, 260, 18, 18, true, null);
        label(layout, "component:R1", "R1", 300, 70, 18, 18, true, null);
        label(layout, "component:C1", "C1", 470, 52, 18, 18, true, null);
        label(layout, "component:J2", "J2", 770, 130, 18, 18, true, null);
        label(layout, "component:R2", "R2", 590, 270, 18, 18, true, null);
        label(layout, "component:C2", "C2", 220, 380, 18, 18, true, null);
        label(layout, "net:J1.1", "+V", 160, 160, 24, 16, false, "J1.1");
        label(layout, "net:J1.2", "GND", 160, 200, 32, 16, false, "J1.2");
    }

    private static void label(PcbBoardLayout layout, String id, String text, int x, int y,
            int width, int height, boolean bold, String targetPad) {
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text,
            new Rectangle(x, y, width, height), 14, bold, targetPad));
    }
}
