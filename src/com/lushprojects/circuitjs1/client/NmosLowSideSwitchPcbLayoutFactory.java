package com.lushprojects.circuitjs1.client;

import java.util.Random;

/** Believable deterministic one-sided NMOS board; Q1 geometry comes from its provider. */
final class NmosLowSideSwitchPcbLayoutFactory {
    private NmosLowSideSwitchPcbLayoutFactory() { }

    static PcbBoardLayout create(TroubleshootBoard board,
            BoardPhysicalSpecifications specifications, long seed) {
        if (specifications == null)
            throw new IllegalArgumentException("Missing NMOS physical specifications");
        int shift = (int) (((seed % 4) + 4) % 4) * 10;
        PcbBoardLayout layout = new PcbBoardLayout(1300, 680,
            new Rectangle(40 + shift, 30, 1050, 570), new Rectangle(1150, 100, 130, 240));
        addComponents(layout, board, seed, shift);
        addPads(layout, board, shift);
        addTraces(layout, shift);
        addLabels(layout, specifications, shift);
        layout.positionPartsTrayDisjointFromBoard();
        layout.validateGeometry(board);
        return layout;
    }

    private static void addComponents(PcbBoardLayout layout, TroubleshootBoard board,
            long seed, int s) {
        layout.addComponent(component("J1", 80 + s, 80, 100, 130, 74 + s, 74, 112, 142));
        layout.addComponent(component("J2", 80 + s, 400, 100, 130, 74 + s, 394, 112, 142));
        layout.addComponent(component("RLOAD", 300 + s, 70, 220, 70, 312 + s, 75, 196, 60));
        layout.addComponent(component("RPD", 300 + s, 320, 220, 70, 298 + s, 315, 224, 80));
        layout.addComponent(component("LED1", 600 + s, 70, 90, 100, 610 + s, 80, 70, 80));
        addProviderFootprint(layout, board.getComponent("Q1"), 900 + s, 100, seed);
    }

    private static void addProviderFootprint(PcbBoardLayout layout, BoardComponent component,
            int x, int y, long seed) {
        PcbFootprint footprint = StandardPcbFootprintProviders.createRegistry().create(
            component, x, y, new Random(seed), layout.getBoardOutline());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
    }

    private static PcbComponentPlacement component(String id, int x, int y, int width,
            int height, int courtyardX, int courtyardY, int courtyardWidth,
            int courtyardHeight) {
        Rectangle body = new Rectangle(x + 10, y + 10, width - 20, height - 20);
        return new PcbComponentPlacement(id, x, y, width, height, body,
            new Rectangle(courtyardX, courtyardY, courtyardWidth, courtyardHeight));
    }

    private static void addPads(PcbBoardLayout layout, TroubleshootBoard board, int s) {
        pad(layout, "J1.1", 100 + s, 120, -1, 0, 30);
        pad(layout, "J1.2", 100 + s, 180, -1, 0, 30);
        pad(layout, "J2.1", 100 + s, 440, 1, 0, 100);
        pad(layout, "J2.2", 100 + s, 500, -1, 0, 30);
        pad(layout, "RLOAD.1", 300 + s, 105, -1, 0, 30);
        pad(layout, "RLOAD.2", 560 + s, 105, 1, 0, 30);
        pad(layout, "RPD.1", 300 + s, 355, -1, 0, 30);
        pad(layout, "RPD.2", 520 + s, 355, 1, 0, 30);
        pad(layout, "LED1.A", 620 + s, 140, 0, 1, 30);
        pad(layout, "LED1.K", 660 + s, 140, 1, 0, 30);
    }

    private static void pad(PcbBoardLayout layout, String id, int x, int y, int dx, int dy,
            int length) {
        layout.addPad(new PcbPadPlacement(id, x, y, dx, dy, length));
    }

    private static void addTraces(PcbBoardLayout layout, int s) {
        trace(layout, "LOAD_SUPPLY", "J1.1", "RLOAD.1", 100+s,120, 60+s,120,
            60+s,60, 260+s,60, 260+s,105, 300+s,105);
        // CONTROL_INPUT is one physical board net.  J2.1 is its stable root;
        // the two branches visibly join RPD.1 and Q1.G without pseudo headers.
        trace(layout, "CONTROL_INPUT", "J2.1", "RPD.1", 100+s,440, 220+s,440,
            220+s,300, 270+s,300, 270+s,355, 300+s,355);
        // Rise above the LED/load corridor before crossing the DRAIN L-route;
        // the upper jog keeps the single-sided gate trace physically separate.
        trace(layout, "CONTROL_INPUT", "J2.1", "Q1.G", 100+s,440, 220+s,440,
            220+s,250, 530+s,250, 530+s,60, 720+s,60, 720+s,190, 920+s,190);
        trace(layout, "LOAD_NODE", "RLOAD.2", "LED1.A", 560+s,105, 580+s,105,
            580+s,170, 620+s,170, 620+s,140);
        trace(layout, "DRAIN", "LED1.K", "Q1.D", 660+s,140, 700+s,140,
            700+s,220, 840+s,220, 840+s,300, 960+s,300, 960+s,190);
        trace(layout, "GND", "J1.2", "J2.2", 100+s,180, 60+s,180, 60+s,500, 100+s,500);
        trace(layout, "GND", "J1.2", "RPD.2", 100+s,180, 60+s,180,
            60+s,550, 600+s,550, 600+s,355, 520+s,355);
        trace(layout, "GND", "J1.2", "Q1.S", 100+s,180, 60+s,180,
            60+s,550, 1080+s,550, 1080+s,260, 1000+s,260, 1000+s,190);
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

    private static void addLabels(PcbBoardLayout layout,
            BoardPhysicalSpecifications specifications, int s) {
        String loadSupplyLabel = specifications.getPowerInputNameplate("LOAD_VIN_INPUT")
            .getDisplayLabel();
        String controlSupplyLabel = specifications.getPowerInputNameplate("CONTROL_VIN_INPUT")
            .getDisplayLabel();
        label(layout, "board-title", "TSJ NMOS LOW-SIDE", 500+s, 575, 170, 18, null);
        label(layout, "component:J1", "J1", 100+s, 225, 18, 18, null);
        label(layout, "component:J2", "J2", 100+s, 555, 18, 18, null);
        label(layout, "component:RLOAD", "RLOAD", 350+s, 45, 54, 18, null);
        label(layout, "component:RPD", "RPD", 350+s, 285, 26, 18, null);
        label(layout, "component:LED1", "LED1", 580+s, 210, 36, 18, null);
        label(layout, "component:Q1", "Q1", 1040+s, 230, 18, 18, null);
        label(layout, "net:J1.1", loadSupplyLabel, 230+s, 135, 42, 16, "J1.1");
        label(layout, "net:J1.2", "GND", 200+s, 225, 30, 16, "J1.2");
        label(layout, "net:J2.1", controlSupplyLabel, 220+s, 465, 42, 16, "J2.1");
        label(layout, "net:J2.2", "GND", 220+s, 525, 30, 16, "J2.2");
    }

    private static void label(PcbBoardLayout layout, String id, String text, int x, int y,
            int width, int height, String targetPad) {
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text,
            new Rectangle(x, y, width, height), 12, false, targetPad));
    }
}
