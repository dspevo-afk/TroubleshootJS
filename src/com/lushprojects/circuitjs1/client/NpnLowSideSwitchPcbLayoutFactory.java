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
        addTraces(layout, shift);
        addLabels(layout, specifications, shift);
        layout.positionPartsTrayDisjointFromBoard();
        layout.validateGeometry(board);
        return layout;
    }

    private static void addComponents(PcbBoardLayout layout, TroubleshootBoard board,
            long seed, int s) {
        addProviderFootprint(layout, board.getComponent("J1"), 80 + s, 80, seed);
        addProviderFootprint(layout, board.getComponent("J2"), 80 + s, 500, seed + 1);
        addProviderFootprint(layout, board.getComponent("RLOAD"), 300 + s, 70, seed + 2);
        addProviderFootprint(layout, board.getComponent("RB"), 500 + s, 500, seed + 3);
        addProviderFootprint(layout, board.getComponent("RPD"), 250 + s, 350, seed + 4);
        addProviderFootprint(layout, board.getComponent("LED1"), 600 + s, 70, seed + 5);
        addProviderFootprint(layout, board.getComponent("Q1"), 950 + s, 100, seed + 6);
    }

    private static void addProviderFootprint(PcbBoardLayout layout, BoardComponent component,
            int x, int y, long seed) {
        PcbFootprint footprint = StandardPcbFootprintProviders.createRegistry().create(
            component, x, y, new Random(seed), layout.getBoardOutline());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads())
            layout.addPad(pad);
    }

    private static void addTraces(PcbBoardLayout layout, int s) {
        PcbPadPlacement j11 = layout.getPad("J1.1");
        PcbPadPlacement j12 = layout.getPad("J1.2");
        PcbPadPlacement j21 = layout.getPad("J2.1");
        PcbPadPlacement j22 = layout.getPad("J2.2");
        PcbPadPlacement rload1 = layout.getPad("RLOAD.1");
        PcbPadPlacement rload2 = layout.getPad("RLOAD.2");
        PcbPadPlacement rb1 = layout.getPad("RB.1");
        PcbPadPlacement rb2 = layout.getPad("RB.2");
        PcbPadPlacement rpd1 = layout.getPad("RPD.1");
        PcbPadPlacement rpd2 = layout.getPad("RPD.2");
        PcbPadPlacement ledA = layout.getPad("LED1.A");
        PcbPadPlacement ledK = layout.getPad("LED1.K");
        PcbPadPlacement base = layout.getPad("Q1.B");
        PcbPadPlacement collector = layout.getPad("Q1.C");
        PcbPadPlacement emitter = layout.getPad("Q1.E");
        int j1EscapeX = escapeX(j11);
        int rload2EscapeX = escapeX(rload2);
        int baseEscapeX = escapeX(base);
        int groundJ1RouteX = 200 + s;
        int groundRpdRouteX = 500 + s;
        int groundJ2LeftX = 50 + s;
        int groundJ2RightX = 230 + s;
        int groundJ2LaneY = 455;
        int groundJ2BottomY = 660;
        int groundEmitterX = emitter.getX();
        int groundEmitterOuterX = 1185 + s;
        int groundEmitterRouteX = 1050 + s;
        int baseRpdRouteX = 240 + s;
        int baseRpdOuterX = 1170 + s;
        int baseRbOuterX = 1170 + s;
        int baseReturnX = 800 + s;
        int baseRpdBottomY = 430;
        int loadSupplyTurnX = 220 + s;
        int loadSupplyLaneY = 60;
        int loadRouteX = 310 + s;
        trace(layout, "LOAD_SUPPLY", "J1.1", "RLOAD.1", j11.getX(),j11.getY(),
            j1EscapeX,j11.getY(), loadSupplyTurnX,j11.getY(), loadSupplyTurnX,loadSupplyLaneY,
            loadRouteX,loadSupplyLaneY, loadRouteX,rload1.getY(),
            rload1.getX(),rload1.getY());
        trace(layout, "CONTROL_INPUT", "J2.1", "RB.1", j21.getX(),j21.getY(),
            escapeX(j21),j21.getY(), 220+s,j21.getY(), 220+s,470,
            450+s,470, 450+s,rb1.getY(), rb1.getX(),rb1.getY());
        trace(layout, "LOAD_NODE", "RLOAD.2", "LED1.A", rload2.getX(),rload2.getY(),
            rload2EscapeX,rload2.getY(), rload2EscapeX,180, ledA.getX(),180,
            ledA.getX(),ledA.getY());
        trace(layout, "COLLECTOR", "LED1.K", "Q1.C", ledK.getX(),ledK.getY(),
            ledK.getX(),170, 710+s,170, 710+s,400, collector.getX(),400,
            collector.getX(),collector.getY());
        trace(layout, "BASE", "Q1.B", "RB.2", base.getX(),base.getY(), baseEscapeX,base.getY(),
            baseEscapeX,80, baseRbOuterX,80, baseRbOuterX,480,
            baseReturnX,480,
            baseReturnX,rb2.getY(), rb2.getX(),rb2.getY());
        trace(layout, "BASE", "Q1.B", "RPD.1", base.getX(),base.getY(), baseEscapeX,base.getY(),
            baseEscapeX,80, baseRpdOuterX,80, baseRpdOuterX,baseRpdBottomY,
            baseRpdRouteX,baseRpdBottomY, baseRpdRouteX,rpd1.getY(),
            rpd1.getX(),rpd1.getY());
        trace(layout, "GND", "J1.2", "J2.2", j12.getX(),j12.getY(),
            groundJ1RouteX,j12.getY(), groundJ1RouteX,groundJ2LaneY,
            groundJ2LeftX,groundJ2LaneY, groundJ2LeftX,groundJ2BottomY,
            groundJ2RightX,groundJ2BottomY, groundJ2RightX,j22.getY(),
            j22.getX(),j22.getY());
        trace(layout, "GND", "J1.2", "RPD.2", j12.getX(),j12.getY(),
            groundJ1RouteX,j12.getY(), groundRpdRouteX,j12.getY(),
            groundRpdRouteX,350, groundRpdRouteX,rpd2.getY(),
            rpd2.getX(),rpd2.getY());
        trace(layout, "GND", "J1.2", "Q1.E", j12.getX(),j12.getY(),
            groundJ1RouteX,j12.getY(), groundJ1RouteX,groundJ2LaneY,
            groundEmitterOuterX,groundJ2LaneY, groundEmitterOuterX,70,
            groundEmitterRouteX,70, groundEmitterRouteX,230, groundEmitterX,230,
            emitter.getX(),emitter.getY());
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
        label(layout, "board-title", "TSJ NPN LOW-SIDE", 500+s, 720, 150, 18, null);
        label(layout, "component:J1", "J1", 100+s, 50, 18, 18, null);
        label(layout, "component:J2", "J2", 230+s, 665, 18, 18, null);
        label(layout, "component:RLOAD", "RLOAD", 350+s, 45, 54, 18, null);
        label(layout, "component:RB", "RB", 580+s, 645, 18, 18, null);
        label(layout, "component:RPD", "RPD", 300+s, 300, 26, 18, null);
        label(layout, "component:LED1", "LED1", 620+s, 45, 36, 18, null);
        label(layout, "component:Q1", "Q1", 960+s, 240, 18, 18, null);
        label(layout, "net:J1.1", loadSupplyLabel, 230+s, 135, 42, 16, "J1.1");
        label(layout, "net:J1.2", "GND", 270+s, 225, 30, 16, "J1.2");
        label(layout, "net:J2.1", controlSupplyLabel, 220+s, 565, 42, 16, "J2.1");
        label(layout, "net:J2.2", "GND", 230+s, 645, 30, 16, "J2.2");
    }

    private static void label(PcbBoardLayout layout, String id, String text, int x, int y,
            int width, int height, String targetPad) {
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text,
            new Rectangle(x, y, width, height), 12, false, targetPad));
    }
}
