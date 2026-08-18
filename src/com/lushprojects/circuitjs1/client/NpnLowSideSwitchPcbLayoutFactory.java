package com.lushprojects.circuitjs1.client;

import java.util.Random;

/** Deliberately simple seeded physical layout for the bounded NPN proof board. */
final class NpnLowSideSwitchPcbLayoutFactory {
    private NpnLowSideSwitchPcbLayoutFactory() { }

    static PcbBoardLayout create(TroubleshootBoard board,
            BoardPhysicalSpecifications specifications, long seed) {
        if (specifications == null)
            throw new IllegalArgumentException("Missing NPN physical specifications");
        int shift = (int) (((seed % 4) + 4) % 4) * 10;
        PcbBoardLayout layout = new PcbBoardLayout(1400, 800,
            new Rectangle(40 + shift, 30, 1150, 720), new Rectangle(1200, 125, 150, 255));
        addComponents(layout, board, seed, shift);
        addPads(layout, shift);
        addTraces(layout, shift);
        addLabels(layout, specifications, shift);
        layout.positionPartsTrayDisjointFromBoard();
        layout.validateGeometry(board);
        return layout;
    }

    private static void addComponents(PcbBoardLayout layout, TroubleshootBoard board,
            long seed, int s) {
        layout.addComponent(component("J1", 80 + s, 80, 100, 130,
            74 + s, 74, 112, 142));
        layout.addComponent(component("J2", 80 + s, 500, 100, 130,
            74 + s, 494, 112, 142));
        layout.addComponent(component("RLOAD", 300 + s, 70, 220, 70,
            312 + s, 75, 196, 60));
        layout.addComponent(component("RB", 500 + s, 500, 220, 70,
            512 + s, 505, 196, 60));
        layout.addComponent(component("RPD", 250 + s, 350, 220, 70,
            258 + s, 355, 204, 60));
        layout.addComponent(component("LED1", 600 + s, 70, 90, 100,
            610 + s, 80, 70, 80));
        addProviderFootprint(layout, board.getComponent("Q1"), 950 + s, 100, seed);
    }

    private static void addProviderFootprint(PcbBoardLayout layout, BoardComponent component,
            int x, int y, long seed) {
        PcbFootprint footprint = StandardPcbFootprintProviders.createRegistry().create(
            component, x, y, new Random(seed), layout.getBoardOutline());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads())
            layout.addPad(pad);
    }

    private static PcbComponentPlacement component(String id, int x, int y, int width,
            int height, int courtyardX, int courtyardY, int courtyardWidth,
            int courtyardHeight) {
        Rectangle body = new Rectangle(x + 10, y + 10, width - 20, height - 20);
        return new PcbComponentPlacement(id, x, y, width, height, body,
            new Rectangle(courtyardX, courtyardY, courtyardWidth, courtyardHeight));
    }

    private static void addPads(PcbBoardLayout layout, int s) {
        pad(layout, "J1.1", 100 + s, 120, -1, 0, 30);
        pad(layout, "J1.2", 100 + s, 180, -1, 0, 30);
        pad(layout, "J2.1", 100 + s, 540, 1, 0, 100);
        pad(layout, "J2.2", 100 + s, 600, -1, 0, 30);
        pad(layout, "RLOAD.1", 300 + s, 105, -1, 0, 30);
        pad(layout, "RLOAD.2", 560 + s, 105, 1, 0, 30);
        pad(layout, "RB.1", 500 + s, 535, -1, 0, 30);
        pad(layout, "RB.2", 720 + s, 535, 1, 0, 30);
        pad(layout, "RPD.1", 250 + s, 385, -1, 0, 30);
        pad(layout, "RPD.2", 470 + s, 385, 1, 0, 30);
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
        trace(layout, "CONTROL_INPUT", "J2.1", "RB.1", 100+s,540, 220+s,540,
            220+s,535, 500+s,535);
        trace(layout, "LOAD_NODE", "RLOAD.2", "LED1.A", 560+s,105, 580+s,105,
            580+s,170, 620+s,170, 620+s,140);
        PcbPadPlacement base = layout.getPad("Q1.B");
        PcbPadPlacement collector = layout.getPad("Q1.C");
        PcbPadPlacement emitter = layout.getPad("Q1.E");
        int baseEscapeX = base.getX() + base.getEscapeDx() * base.getEscapeLength();
        int baseEscapeY = base.getY() + base.getEscapeDy() * base.getEscapeLength();
        int collectorEscapeX = collector.getX() + collector.getEscapeDx() *
            collector.getEscapeLength();
        int collectorEscapeY = collector.getY() + collector.getEscapeDy() *
            collector.getEscapeLength();
        int emitterEscapeX = emitter.getX() + emitter.getEscapeDx() * emitter.getEscapeLength();
        int emitterEscapeY = emitter.getY() + emitter.getEscapeDy() * emitter.getEscapeLength();
        trace(layout, "COLLECTOR", "LED1.K", "Q1.C", 660+s,140, 690+s,140,
            690+s,260, 900+s,260, collector.getX(),260,
            collectorEscapeX,collectorEscapeY, collector.getX(),collector.getY());
        trace(layout, "BASE", "Q1.B", "RB.2", base.getX(),base.getY(), baseEscapeX,baseEscapeY,
            940+s,40, 1170+s,40, 1170+s,535,
            720+s,535);
        trace(layout, "BASE", "Q1.B", "RPD.1", base.getX(),base.getY(), baseEscapeX,baseEscapeY,
            940+s,40, 520+s,40, 520+s,330,
            240+s,330, 240+s,385, 250+s,385);
        trace(layout, "GND", "J1.2", "J2.2", 100+s,180, 60+s,180,
            60+s,600, 100+s,600);
        trace(layout, "GND", "J1.2", "RPD.2", 100+s,180, 60+s,180,
            60+s,430, 520+s,430, 520+s,385, 470+s,385);
        trace(layout, "GND", "J1.2", "Q1.E", 100+s,180, 60+s,180,
            60+s,430, emitter.getX(),430,
            emitter.getX(),260,
            emitterEscapeX,emitterEscapeY, emitter.getX(),emitter.getY());
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
        label(layout, "board-title", "TSJ NPN LOW-SIDE", 500+s, 720, 150, 18, null);
        label(layout, "component:J1", "J1", 100+s, 225, 18, 18, null);
        label(layout, "component:J2", "J2", 100+s, 645, 18, 18, null);
        label(layout, "component:RLOAD", "RLOAD", 350+s, 45, 54, 18, null);
        label(layout, "component:RB", "RB", 580+s, 645, 18, 18, null);
        label(layout, "component:RPD", "RPD", 300+s, 300, 26, 18, null);
        label(layout, "component:LED1", "LED1", 620+s, 45, 36, 18, null);
        label(layout, "component:Q1", "Q1", 960+s, 240, 18, 18, null);
        label(layout, "net:J1.1", loadSupplyLabel, 230+s, 135, 42, 16, "J1.1");
        label(layout, "net:J1.2", "GND", 200+s, 225, 30, 16, "J1.2");
        label(layout, "net:J2.1", controlSupplyLabel, 220+s, 565, 42, 16, "J2.1");
        label(layout, "net:J2.2", "GND", 200+s, 645, 30, 16, "J2.2");
    }

    private static void label(PcbBoardLayout layout, String id, String text, int x, int y,
            int width, int height, String targetPad) {
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text,
            new Rectangle(x, y, width, height), 12, false, targetPad));
    }
}
