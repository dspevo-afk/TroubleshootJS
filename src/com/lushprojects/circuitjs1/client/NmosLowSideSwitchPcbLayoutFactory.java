package com.lushprojects.circuitjs1.client;

import java.util.Random;

/** Believable deterministic one-sided NMOS board; Q1 geometry comes from its provider. */
final class NmosLowSideSwitchPcbLayoutFactory {
    private NmosLowSideSwitchPcbLayoutFactory() { }

    static PcbBoardLayout create(TroubleshootBoard board,
            BoardPhysicalSpecifications specifications, long seed) {
        if (specifications == null)
            throw new IllegalArgumentException("Missing NMOS physical specifications");
        int variationMode = variationMode(seed);
        return createLayout(board, specifications, variationMode, seed, null, null);
    }

    /** Developer-only finite matrix path; it never searches for a route. */
    static PcbBoardLayout createForDeveloperVerification(TroubleshootBoard board,
            BoardPhysicalSpecifications specifications, int variationMode,
            String rloadVariantKey, String rpdVariantKey) {
        if (specifications == null)
            throw new IllegalArgumentException("Missing NMOS physical specifications");
        requireVariationMode(variationMode);
        requireResistorVariantKey(rloadVariantKey);
        requireResistorVariantKey(rpdVariantKey);
        return createLayout(board, specifications, variationMode, variationMode,
            rloadVariantKey, rpdVariantKey);
    }

    private static PcbBoardLayout createLayout(TroubleshootBoard board,
            BoardPhysicalSpecifications specifications, int variationMode, long seed,
            String rloadVariantKey, String rpdVariantKey) {
        requireVariationMode(variationMode);
        int s = variationMode * 10;
        PcbBoardLayout layout = new PcbBoardLayout(1300, 680,
            new Rectangle(40 + s, 30, 1050, 570), new Rectangle(1150, 100, 130, 240));
        addComponents(layout, board, seed, s, rloadVariantKey, rpdVariantKey);
        addTraces(layout);
        addLabels(layout, specifications, s);
        layout.compactToContent(40 + s, 30 + (variationMode % 2) * 10, 26);
        layout.positionPartsTrayDisjointFromBoard();
        layout.validateGeometry(board);
        return layout;
    }

    private static int variationMode(long seed) {
        return (int) (((seed % 4) + 4) % 4);
    }

    private static void requireVariationMode(int variationMode) {
        if (variationMode < 0 || variationMode > 3)
            throw new IllegalArgumentException("NMOS variation mode must be 0..3: " +
                variationMode);
    }

    private static void requireResistorVariantKey(String key) {
        if (!"SPAN_220".equals(key) && !"SPAN_240".equals(key) &&
                !"SPAN_260".equals(key))
            throw new IllegalArgumentException("Unknown canonical NMOS resistor variant: " +
                key);
    }

    private static void addComponents(PcbBoardLayout layout, TroubleshootBoard board,
            long seed, int s, String rloadVariantKey, String rpdVariantKey) {
        addProviderFootprint(layout, board.getComponent("J1"), 80 + s, 80, seed);
        addProviderFootprint(layout, board.getComponent("J2"), 80 + s, 400, seed + 1);
        addResistorFootprint(layout, board.getComponent("RLOAD"), 350 + s, 200, seed + 2,
            rloadVariantKey);
        addResistorFootprint(layout, board.getComponent("RPD"), 300 + s, 320, seed + 3,
            rpdVariantKey);
        addProviderFootprint(layout, board.getComponent("LED1"), 500 + s, 70, seed + 4);
        addProviderFootprint(layout, board.getComponent("Q1"), 900 + s, 100, seed + 5);
    }

    private static void addProviderFootprint(PcbBoardLayout layout, BoardComponent component,
            int x, int y, long seed) {
        PcbFootprint footprint = StandardPcbFootprintProviders.createRegistry().create(
            component, x, y, new Random(seed), layout.getBoardOutline());
        addFootprint(layout, footprint);
    }

    private static void addFootprint(PcbBoardLayout layout, PcbFootprint footprint) {
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
    }

    private static void addResistorFootprint(PcbBoardLayout layout, BoardComponent component,
            int x, int y, long seed, String explicitVariantKey) {
        if (explicitVariantKey == null) {
            addProviderFootprint(layout, component, x, y, seed);
            return;
        }
        if (component.getPhysicalPackage() != PhysicalPackages.AXIAL_RESISTOR)
            throw new IllegalStateException("NMOS resistor does not use the axial package: " +
                component.getId());
        PhysicalPackage.GeometryVariant variant = component.getPhysicalPackage()
            .getGeometryVariant(explicitVariantKey);
        if (variant == null)
            throw new IllegalArgumentException("NMOS resistor catalog lacks variant: " +
                explicitVariantKey);
        addFootprint(layout, PcbFootprint.fromPhysicalPackage(component, x, y,
            variant.getGeometry()));
    }

    private static void addTraces(PcbBoardLayout layout) {
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

        int j1SupplyEscapeX = escapeX(j11);
        int j1GroundEscapeX = escapeX(j12);
        int j2ControlEscapeX = escapeX(j21);
        int outerLeftX = layout.getBoardOutline().x + 20;
        int rload1EscapeX = escapeX(rload1);
        int rload2EscapeX = escapeX(rload2);
        int rpd1EscapeX = escapeX(rpd1);
        int rpd2EscapeX = escapeX(rpd2);
        int gateEscapeX = escapeX(gate);
        int ledAnodeEscapeY = escapeY(ledA);
        int ledCathodeEscapeY = escapeY(ledK);
        int drainEscapeY = escapeY(drain);
        int sourceEscapeY = escapeY(source);

        trace(layout, "LOAD_SUPPLY", "J1.1", "RLOAD.1", j11.getX(), j11.getY(),
            j1SupplyEscapeX, j11.getY(), rload1EscapeX, j11.getY(), rload1EscapeX,
            rload1.getY(), rload1.getX(), rload1.getY());

        int loadNodeLaneY = ledAnodeEscapeY + 20;
        trace(layout, "LOAD_NODE", "RLOAD.2", "LED1.A", rload2.getX(), rload2.getY(),
            rload2EscapeX, rload2.getY(), rload2EscapeX, loadNodeLaneY, ledA.getX(),
            loadNodeLaneY, ledA.getX(), ledA.getY());

        int drainLaneX = rload2EscapeX + 20;
        int drainApproachY = drainEscapeY + 44;
        trace(layout, "DRAIN", "LED1.K", "Q1.D", ledK.getX(), ledK.getY(), ledK.getX(),
            ledCathodeEscapeY, drainLaneX, ledCathodeEscapeY, drainLaneX, drainApproachY,
            drain.getX(), drainApproachY, drain.getX(), drainEscapeY, drain.getX(),
            drain.getY());

        int controlRpdLaneY = rpd1.getY() + 90;
        trace(layout, "CONTROL_INPUT", "J2.1", "RPD.1", j21.getX(), j21.getY(),
            j2ControlEscapeX, controlRpdLaneY, rpd1EscapeX, controlRpdLaneY, rpd1EscapeX,
            rpd1.getY(), rpd1.getX(), rpd1.getY());

        int controlOuterX = source.getX() + 32;
        int controlUpperY = gate.getY() - 92;
        trace(layout, "CONTROL_INPUT", "J2.1", "Q1.G", j21.getX(), j21.getY(),
            j2ControlEscapeX, j21.getY(), controlOuterX, j21.getY(), controlOuterX,
            controlUpperY, gateEscapeX, controlUpperY, gateEscapeX, gate.getY(),
            gate.getX(), gate.getY());

        int groundSharedY = j12.getY() + 70;
        int groundRpdLaneY = rpd2.getY() - 50;
        int groundBottomY = j22.getY() + 50;
        int groundBranchX = j1GroundEscapeX;
        trace(layout, "GND", "J1.2", "J2.2", j12.getX(), j12.getY(), j1GroundEscapeX,
            j12.getY(), j1GroundEscapeX, groundSharedY, outerLeftX, groundSharedY,
            outerLeftX, groundBottomY, groundBranchX, groundBottomY, groundBranchX,
            j22.getY(), j22.getX(), j22.getY());

        trace(layout, "GND", "J1.2", "RPD.2", j12.getX(), j12.getY(), j1GroundEscapeX,
            j12.getY(), j1GroundEscapeX, groundSharedY, outerLeftX, groundSharedY,
            outerLeftX, groundRpdLaneY, rpd2EscapeX, groundRpdLaneY, rpd2EscapeX,
            rpd2.getY(), rpd2.getX(), rpd2.getY());

        trace(layout, "GND", "J1.2", "Q1.S", j12.getX(), j12.getY(), j1GroundEscapeX,
            j12.getY(), j1GroundEscapeX, groundSharedY, outerLeftX, groundSharedY,
            outerLeftX, groundRpdLaneY, source.getX(), groundRpdLaneY, source.getX(),
            sourceEscapeY, source.getX(), source.getY());
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
