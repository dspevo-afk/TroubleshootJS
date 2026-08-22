package com.lushprojects.circuitjs1.client;

import java.util.Random;

/** Deterministic fixed physical layout for the bounded NPN proof board. */
final class NpnLowSideSwitchPcbLayoutFactory {
    private NpnLowSideSwitchPcbLayoutFactory() { }

    static PcbBoardLayout create(TroubleshootBoard board,
            BoardPhysicalSpecifications specifications, long seed) {
        if (specifications == null)
            throw new IllegalArgumentException("Missing NPN physical specifications");
        int variationMode = variationMode(seed);
        return createLayout(board, specifications, variationMode, seed, null, null, null);
    }

    /** Developer-only finite matrix path; it never searches for a route. */
    static PcbBoardLayout createForDeveloperVerification(TroubleshootBoard board,
            BoardPhysicalSpecifications specifications, int variationMode,
            String rloadVariantKey, String rbVariantKey, String rpdVariantKey) {
        if (specifications == null)
            throw new IllegalArgumentException("Missing NPN physical specifications");
        requireVariationMode(variationMode);
        requireResistorVariantKey(rloadVariantKey);
        requireResistorVariantKey(rbVariantKey);
        requireResistorVariantKey(rpdVariantKey);
        return createLayout(board, specifications, variationMode, variationMode,
            rloadVariantKey, rbVariantKey, rpdVariantKey);
    }

    private static PcbBoardLayout createLayout(TroubleshootBoard board,
            BoardPhysicalSpecifications specifications, int variationMode, long seed,
            String rloadVariantKey, String rbVariantKey, String rpdVariantKey) {
        requireVariationMode(variationMode);
        int s = variationMode * 10;
        PcbBoardLayout layout = new PcbBoardLayout(1400, 800,
            new Rectangle(40 + s, 30, 1150, 720), new Rectangle(1200, 125, 150, 255));
        addComponents(layout, board, seed, s, rloadVariantKey, rbVariantKey, rpdVariantKey);
        addTraces(layout, s);
        addLabels(layout, specifications, s);
        layout.compactToContent(40 + variationMode * 10,
            30 + (variationMode % 2) * 10, 26);
        layout.positionPartsTrayDisjointFromBoard();
        layout.validateGeometry(board);
        return layout;
    }

    private static int variationMode(long seed) {
        return (int) (((seed % 4) + 4) % 4);
    }

    private static void requireVariationMode(int variationMode) {
        if (variationMode < 0 || variationMode > 3)
            throw new IllegalArgumentException("NPN variation mode must be 0..3: " +
                variationMode);
    }

    private static void requireResistorVariantKey(String key) {
        if (!"SPAN_220".equals(key) && !"SPAN_240".equals(key) &&
                !"SPAN_260".equals(key))
            throw new IllegalArgumentException("Unknown canonical NPN resistor variant: " + key);
    }

    private static void addComponents(PcbBoardLayout layout, TroubleshootBoard board,
            long seed, int s, String rloadVariantKey, String rbVariantKey,
            String rpdVariantKey) {
        addProviderFootprint(layout, board.getComponent("J1"), 80 + s, 80, seed);
        addProviderFootprint(layout, board.getComponent("J2"), 80 + s, 500, seed + 1);
        addResistorFootprint(layout, board.getComponent("RLOAD"), 300 + s, 70, seed + 2,
            rloadVariantKey);
        addResistorFootprint(layout, board.getComponent("RB"), 500 + s, 500, seed + 3,
            rbVariantKey);
        addResistorFootprint(layout, board.getComponent("RPD"), 250 + s, 350, seed + 4,
            rpdVariantKey);
        addProviderFootprint(layout, board.getComponent("LED1"), 600 + s, 70, seed + 5);
        addProviderFootprint(layout, board.getComponent("Q1"), 950 + s, 100, seed + 6);
    }

    private static void addProviderFootprint(PcbBoardLayout layout, BoardComponent component,
            int x, int y, long seed) {
        PcbFootprint footprint = StandardPcbFootprintProviders.createRegistry().create(
            component, x, y, new Random(seed), layout.getBoardOutline());
        addFootprint(layout, footprint);
    }

    private static void addResistorFootprint(PcbBoardLayout layout, BoardComponent component,
            int x, int y, long seed, String explicitVariantKey) {
        if (explicitVariantKey == null) {
            addProviderFootprint(layout, component, x, y, seed);
            return;
        }
        if (component.getPhysicalPackage() != PhysicalPackages.AXIAL_RESISTOR)
            throw new IllegalStateException("NPN resistor does not use the axial package: " +
                component.getId());
        PhysicalPackage.GeometryVariant variant = component.getPhysicalPackage()
            .getGeometryVariant(explicitVariantKey);
        if (variant == null)
            throw new IllegalArgumentException("NPN resistor catalog lacks variant: " +
                explicitVariantKey);
        addFootprint(layout, PcbFootprint.fromPhysicalPackage(component, x, y,
            variant.getGeometry()));
    }

    private static void addFootprint(PcbBoardLayout layout, PcbFootprint footprint) {
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

        int loadSupplyLaneY = j11.getY();
        int loadSupplyRloadEscapeX = escapeX(rload1);
        int loadSupplyJ1EscapeX = escapeX(j11);
        trace(layout, "LOAD_SUPPLY", "J1.1", "RLOAD.1", j11.getX(), j11.getY(),
            loadSupplyJ1EscapeX, loadSupplyLaneY, loadSupplyRloadEscapeX, loadSupplyLaneY,
            loadSupplyRloadEscapeX, rload1.getY(), rload1.getX(), rload1.getY());

        int controlLaneX = escapeX(j21);
        int controlLaneY = rb1.getY() - 50;
        int controlRbEscapeX = escapeX(rb1);
        trace(layout, "CONTROL_INPUT", "J2.1", "RB.1", j21.getX(), j21.getY(),
            controlLaneX, j21.getY(), controlLaneX, controlLaneY, controlRbEscapeX,
            controlLaneY, controlRbEscapeX, rb1.getY(), rb1.getX(), rb1.getY());

        int loadNodeLaneY = 180;
        int loadNodeRloadEscapeX = escapeX(rload2);
        int loadNodeLedEscapeX = escapeX(ledA);
        int loadNodeLedEscapeY = escapeY(ledA);
        trace(layout, "LOAD_NODE", "RLOAD.2", "LED1.A", rload2.getX(), rload2.getY(),
            loadNodeRloadEscapeX, rload2.getY(), loadNodeRloadEscapeX, loadNodeLaneY,
            loadNodeLedEscapeX, loadNodeLaneY, loadNodeLedEscapeX, loadNodeLedEscapeY,
            ledA.getX(), ledA.getY());

        int collectorLaneX = ledK.getX() + 40;
        int collectorEscapeY = escapeY(collector);
        trace(layout, "COLLECTOR", "LED1.K", "Q1.C", ledK.getX(), ledK.getY(),
            ledK.getX(), escapeY(ledK), collectorLaneX, escapeY(ledK), collectorLaneX,
            collectorEscapeY, escapeX(collector), collectorEscapeY, collector.getX(),
            collector.getY());

        int baseEscapeX = escapeX(base);
        int baseUpperLaneY = 36;
        int baseOuterRouteX = 1100 + s;
        int baseRpdLaneY = 330;
        int baseRpdRouteX = escapeX(rpd1);
        trace(layout, "BASE", "Q1.B", "RPD.1", base.getX(), base.getY(), baseEscapeX,
            base.getY(), baseEscapeX, baseUpperLaneY, baseOuterRouteX, baseUpperLaneY,
            baseOuterRouteX, baseRpdLaneY, baseRpdRouteX, baseRpdLaneY, baseRpdRouteX,
            rpd1.getY(), rpd1.getX(), rpd1.getY());

        int baseRbLaneY = 470;
        int baseBranchX = 780 + s;
        trace(layout, "BASE", "Q1.B", "RB.2", base.getX(), base.getY(), baseEscapeX,
            base.getY(), baseEscapeX, baseUpperLaneY, baseOuterRouteX, baseUpperLaneY,
            baseOuterRouteX, baseRbLaneY, baseBranchX, baseRbLaneY, baseBranchX, rb2.getY(),
            rb2.getX(), rb2.getY());

        int groundRootEscapeX = escapeX(j12);
        int groundSharedLaneY = 220;
        int groundTreeX = 60 + s;
        int groundTreeY = 430;
        int groundJ2BottomY = 650;
        trace(layout, "GND", "J1.2", "J2.2", j12.getX(), j12.getY(), groundRootEscapeX,
            j12.getY(), groundRootEscapeX, groundSharedLaneY, groundTreeX,
            groundSharedLaneY, groundTreeX, j22.getY(), groundTreeX, groundJ2BottomY,
            groundRootEscapeX, groundJ2BottomY, groundRootEscapeX, j22.getY(), j22.getX(),
            j22.getY());

        int groundRpdDetourY = 440;
        int groundRpdTreeX = 520 + s;
        int groundRpdBranchX = 530 + s;
        trace(layout, "GND", "J1.2", "RPD.2", j12.getX(), j12.getY(), groundRootEscapeX,
            j12.getY(), groundRootEscapeX, groundSharedLaneY, groundTreeX,
            groundSharedLaneY, groundTreeX, groundTreeY, groundTreeX, groundRpdDetourY,
            groundRpdTreeX, groundRpdDetourY, groundRpdTreeX, groundTreeY,
            groundRpdBranchX, groundTreeY, groundRpdBranchX, rpd2.getY(), rpd2.getX(),
            rpd2.getY());

        int emitterDetourX = baseRpdRouteX - (PcbTraceRules.MIN_CENTERLINE_CLEARANCE + 1);
        int emitterLaneY = 285;
        int emitterEscapeX = escapeX(emitter);
        int emitterEscapeY = escapeY(emitter);
        trace(layout, "GND", "J1.2", "Q1.E", j12.getX(), j12.getY(), groundRootEscapeX,
            j12.getY(), groundRootEscapeX, groundSharedLaneY, groundTreeX,
            groundSharedLaneY, groundTreeX, groundTreeY, groundTreeX, 450,
            emitterDetourX, 450, emitterDetourX, emitterLaneY, emitterEscapeX,
            emitterLaneY, emitterEscapeX, emitterEscapeY, emitter.getX(), emitter.getY());
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
        label(layout, "board-title", "TSJ NPN LOW-SIDE", 500 + s, 720, 150, 18, null);
        label(layout, "component:J1", "J1", 100 + s, 50, 18, 18, null);
        label(layout, "component:J2", "J2", 230 + s, 665, 18, 18, null);
        label(layout, "component:RLOAD", "RLOAD", 350 + s, 45, 54, 18, null);
        label(layout, "component:RB", "RB", 580 + s, 645, 18, 18, null);
        label(layout, "component:RPD", "RPD", 300 + s, 300, 26, 18, null);
        label(layout, "component:LED1", "LED1", 620 + s, 45, 36, 18, null);
        label(layout, "component:Q1", "Q1", 960 + s, 240, 18, 18, null);
        label(layout, "net:J1.1", loadSupplyLabel, 230 + s, 135, 42, 16, "J1.1");
        label(layout, "net:J1.2", "GND", 270 + s, 225, 30, 16, "J1.2");
        label(layout, "net:J2.1", controlSupplyLabel, 220 + s, 565, 42, 16, "J2.1");
        label(layout, "net:J2.2", "GND", 230 + s, 645, 30, 16, "J2.2");
    }

    private static void label(PcbBoardLayout layout, String id, String text, int x, int y,
            int width, int height, String targetPad) {
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text,
            new Rectangle(x, y, width, height), 12, false, targetPad));
    }
}
