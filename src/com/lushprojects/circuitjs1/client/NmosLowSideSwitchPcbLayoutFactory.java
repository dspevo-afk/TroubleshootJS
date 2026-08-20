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
        addTraces(layout, shift);
        addLabels(layout, specifications, shift);
        layout.positionPartsTrayDisjointFromBoard();
        layout.validateGeometry(board);
        return layout;
    }

    private static void addComponents(PcbBoardLayout layout, TroubleshootBoard board,
            long seed, int s) {
        addProviderFootprint(layout, board.getComponent("J1"), 80 + s, 80, seed);
        addProviderFootprint(layout, board.getComponent("J2"), 80 + s, 400, seed + 1);
        addProviderFootprint(layout, board.getComponent("RLOAD"), 350 + s, 200, seed + 2);
        addProviderFootprint(layout, board.getComponent("RPD"), 300 + s, 320, seed + 3);
        addProviderFootprint(layout, board.getComponent("LED1"), 500 + s, 70, seed + 4);
        addProviderFootprint(layout, board.getComponent("Q1"), 900 + s, 100, seed + 5);
    }

    private static void addProviderFootprint(PcbBoardLayout layout, BoardComponent component,
            int x, int y, long seed) {
        PcbFootprint footprint = StandardPcbFootprintProviders.createRegistry().create(
            component, x, y, new Random(seed), layout.getBoardOutline());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
    }

    private static void addTraces(PcbBoardLayout layout, int s) {
        PcbPadPlacement j11 = layout.getPad("J1.1");
        PcbPadPlacement j12 = layout.getPad("J1.2");
        PcbPadPlacement j21 = layout.getPad("J2.1");
        PcbPadPlacement j22 = layout.getPad("J2.2");
        PcbPadPlacement rload1 = layout.getPad("RLOAD.1");
        PcbPadPlacement rload2 = layout.getPad("RLOAD.2");
        PcbPadPlacement rpd1 = layout.getPad("RPD.1");
        PcbPadPlacement rpd2 = layout.getPad("RPD.2");
        PcbPadPlacement ledA = layout.getPad("LED1.A");
        PcbPadPlacement ledK = layout.getPad("LED1.K");
        PcbPadPlacement gate = layout.getPad("Q1.G");
        PcbPadPlacement drain = layout.getPad("Q1.D");
        PcbPadPlacement source = layout.getPad("Q1.S");

        int j1EscapeX = escapeX(j11);
        int j1ReturnEscapeX = escapeX(j12);
        int outerLeftX = layout.getBoardOutline().x + 20;
        int groundStartX = j1ReturnEscapeX;
        int groundRpdX = 590 + s;
        int groundBottomX = 240 + s;
        int groundRpdY = 420;
        int groundBottomY = 550;
        int rload2EscapeX = escapeX(rload2);
        int drainUpperX = 920 + s;

        trace(layout, "LOAD_SUPPLY", "J1.1", "RLOAD.1", j11.getX(),j11.getY(),
            j1EscapeX,j11.getY(), 360+s,j11.getY(), 360+s,rload1.getY(),
            rload1.getX(),rload1.getY());
        // CONTROL_INPUT is one physical board net.  J2.1 is its stable root;
        // the two branches visibly join RPD.1 and Q1.G without pseudo headers.
        trace(layout, "CONTROL_INPUT", "J2.1", "Q1.G", j21.getX(),j21.getY(),
            escapeX(j21),j21.getY(), 900+s,j21.getY(), 900+s,gate.getY(),
            gate.getX(),gate.getY());
        trace(layout, "LOAD_NODE", "RLOAD.2", "LED1.A", rload2.getX(),rload2.getY(),
            rload2EscapeX,rload2.getY(), rload2EscapeX,200, ledA.getX(),200,
            ledA.getX(),ledA.getY());
        trace(layout, "DRAIN", "LED1.K", "Q1.D", ledK.getX(),ledK.getY(),
            ledK.getX(),170, 700+s,170, 700+s,80, drainUpperX,80,
            drainUpperX,250, drain.getX(),250, drain.getX(),drain.getY());
        trace(layout, "GND", "J1.2", "J2.2", j12.getX(),j12.getY(),
            groundStartX,j12.getY(), groundStartX,250, outerLeftX,250,
            outerLeftX,groundBottomY, groundBottomX,groundBottomY,
            groundBottomX,j22.getY(), j22.getX(),j22.getY());
        trace(layout, "GND", "J1.2", "RPD.2", j12.getX(),j12.getY(),
            groundStartX,j12.getY(), groundStartX,250, outerLeftX,250,
            outerLeftX,groundRpdY, groundRpdX,groundRpdY, groundRpdX,rpd2.getY(),
            rpd2.getX(),rpd2.getY());
        trace(layout, "GND", "J1.2", "Q1.S", j12.getX(),j12.getY(),
            groundStartX,j12.getY(), groundStartX,250, outerLeftX,250,
            outerLeftX,groundBottomY, source.getX(),groundBottomY,
            source.getX(),222, source.getX(),source.getY());
    }

    private static int escapeX(PcbPadPlacement pad) {
        return pad.getX() + pad.getEscapeDx() * pad.getEscapeLength();
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
        label(layout, "component:J2", "J2", 100+s, 365, 18, 18, null);
        label(layout, "component:RLOAD", "RLOAD", 400+s, 175, 54, 18, null);
        label(layout, "component:RPD", "RPD", 350+s, 395, 26, 18, null);
        label(layout, "component:LED1", "LED1", 480+s, 170, 36, 18, null);
        label(layout, "component:Q1", "Q1", 1040+s, 230, 18, 18, null);
        label(layout, "net:J1.1", loadSupplyLabel, 230+s, 135, 42, 16, "J1.1");
        label(layout, "net:J1.2", "GND", 270+s, 225, 30, 16, "J1.2");
        label(layout, "net:J2.1", controlSupplyLabel, 220+s, 465, 42, 16, "J2.1");
        label(layout, "net:J2.2", "GND", 270+s, 525, 30, 16, "J2.2");
    }

    private static void label(PcbBoardLayout layout, String id, String text, int x, int y,
            int width, int height, String targetPad) {
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text,
            new Rectangle(x, y, width, height), 12, false, targetPad));
    }
}
