package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * Device-owned bounded PCB realization for the composed controlled indicator.
 *
 * <p>The assembly plan owns the logical-to-global identity map.  This factory
 * only realizes that map as one small, fixed physical board; it does not
 * construct child boards or infer connectivity from component order.</p>
 */
final class ControlledIndicatorPcbLayoutFactory {
    private static final String DRIVER = "driver";
    private static final String LOAD = "load";
    private static final String POWER_ADAPTER = "power-adapter";
    private static final String CONTROL_ADAPTER = "control-adapter";

    private static final String RESISTOR_VARIANT = "SPAN_220";

    private ControlledIndicatorPcbLayoutFactory() { }

    /**
     * Create and validate the complete bounded device board.
     *
     * <p>All identities and net IDs are resolved through the supplied plan.
     * The coordinates are deliberately fixed and compacted only as a whole;
     * there is no route search or coordinate-by-coordinate repair.</p>
     */
    static PcbBoardLayout create(TroubleshootBoard board,
            BoardPhysicalSpecifications specifications, BoundedAssemblyPlan plan) {
        if (board == null)
            throw new IllegalArgumentException("Missing controlled-indicator board");
        if (specifications == null)
            throw new IllegalArgumentException(
                "Missing controlled-indicator physical specifications");
        if (plan == null)
            throw new IllegalArgumentException("Missing controlled-indicator assembly plan");

        String loadSupply = plan.netFor(LOAD, "SUPPLY");
        String loadNode = plan.netFor(LOAD, "LED_NODE");
        String switchedSink = plan.netFor(DRIVER, "SWITCHED_SINK");
        String control = plan.netFor(DRIVER, "CONTROL");
        String gate = plan.netFor(DRIVER, "GATE");
        String returned = plan.netFor(DRIVER, "RETURN");

        // Keep the board identity map explicit at this boundary.  In
        // particular, adapter outputs are represented by the canonical block
        // nets above; equal display labels never merge board nets.
        String rg = component(plan, DRIVER, "RG");
        String rpd = component(plan, DRIVER, "RPD");
        String q1 = component(plan, DRIVER, "Q1");
        String rload = component(plan, LOAD, "RLOAD");
        String led1 = component(plan, LOAD, "LED1");
        String j1 = component(plan, POWER_ADAPTER, "J1");
        String j2 = component(plan, CONTROL_ADAPTER, "J2");

        String rg1 = pad(plan, DRIVER, "RG.1");
        String rg2 = pad(plan, DRIVER, "RG.2");
        String rpd1 = pad(plan, DRIVER, "RPD.1");
        String rpd2 = pad(plan, DRIVER, "RPD.2");
        String q1g = pad(plan, DRIVER, "Q1.G");
        String q1d = pad(plan, DRIVER, "Q1.D");
        String q1s = pad(plan, DRIVER, "Q1.S");
        String rload1 = pad(plan, LOAD, "RLOAD.1");
        String rload2 = pad(plan, LOAD, "RLOAD.2");
        String ledA = pad(plan, LOAD, "LED1.A");
        String ledK = pad(plan, LOAD, "LED1.K");
        String j11 = pad(plan, POWER_ADAPTER, "J1.1");
        String j12 = pad(plan, POWER_ADAPTER, "J1.2");
        String j21 = pad(plan, CONTROL_ADAPTER, "J2.1");
        String j22 = pad(plan, CONTROL_ADAPTER, "J2.2");

        // The initial canvas is intentionally larger than the final board so
        // every canonical package surface and copper route is available while
        // the complete layout is assembled.  compactToContent performs one
        // whole-layout translation after all routes and labels exist.
        PcbBoardLayout layout = new PcbBoardLayout(1600, 1000,
            new Rectangle(20, 20, 1200, 680), new Rectangle(1350, 100, 180, 300));

        addResistor(layout, board.getComponent(rg), 600, 500);
        addResistor(layout, board.getComponent(rpd), 300, 320);
        addFixed(layout, board.getComponent(q1), 900, 100);
        addResistor(layout, board.getComponent(rload), 350, 200);
        addFixed(layout, board.getComponent(led1), 500, 70);
        addFixed(layout, board.getComponent(j1), 80, 80);
        addFixed(layout, board.getComponent(j2), 80, 400);

        addTraces(layout, loadSupply, loadNode, switchedSink, control, gate, returned,
            j11, j12, j21, j22, rg1, rg2, rpd1, rpd2, q1g, q1d, q1s,
            rload1, rload2, ledA, ledK);
        addLabels(layout, specifications, j11, j12, j1, j2, rg, rpd, q1, rload, led1);

        layout.compactToContent(40, 30, 26);
        layout.positionPartsTrayDisjointFromBoard();
        layout.validateGeometry(board);
        return layout;
    }

    private static String component(BoundedAssemblyPlan plan, String block, String local) {
        return plan.idFor(block, EntityKind.COMPONENT, local);
    }

    private static String pad(BoundedAssemblyPlan plan, String block, String local) {
        return plan.idFor(block, EntityKind.PAD, local);
    }

    private static void addFixed(PcbBoardLayout layout, BoardComponent component,
            int x, int y) {
        if (component == null)
            throw new IllegalArgumentException("Controlled-indicator board is missing a component");
        addFootprint(layout, PcbFootprint.fromPhysicalPackage(component, x, y));
    }

    private static void addResistor(PcbBoardLayout layout, BoardComponent component,
            int x, int y) {
        if (component == null)
            throw new IllegalArgumentException("Controlled-indicator board is missing a resistor");
        if (component.getPhysicalPackage() != PhysicalPackages.AXIAL_RESISTOR)
            throw new IllegalStateException("Controlled-indicator resistor does not use the axial package: "
                + component.getId());
        PhysicalPackage.GeometryVariant variant = component.getPhysicalPackage()
            .getGeometryVariant(RESISTOR_VARIANT);
        if (variant == null)
            throw new IllegalStateException("Controlled-indicator resistor catalog lacks "
                + RESISTOR_VARIANT + ": " + component.getId());
        addFootprint(layout, PcbFootprint.fromPhysicalPackage(component, x, y,
            variant.getGeometry()));
    }

    private static void addFootprint(PcbBoardLayout layout, PcbFootprint footprint) {
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads())
            layout.addPad(pad);
    }

    private static void addTraces(PcbBoardLayout layout, String loadSupply, String loadNode,
            String switchedSink, String control, String gate, String returned,
            String j11, String j12, String j21, String j22, String rg1, String rg2,
            String rpd1, String rpd2, String q1g, String q1d, String q1s,
            String rload1, String rload2, String ledA, String ledK) {
        // The fixed lanes were audited against every package courtyard and
        // the single-layer copper-clearance rules.  Gate branches share the
        // RG.2 trunk; return branches share the outer reference tree.
        trace(layout, loadSupply, j11, rload1,
            170, 120, 220, 120, 330, 120, 330, 230, 380, 230);
        trace(layout, loadNode, rload2, ledA,
            540, 230, 590, 230, 590, 195, 520, 195, 520, 140);
        trace(layout, switchedSink, ledK, q1d,
            560, 140, 560, 175, 610, 175, 610, 270, 960, 270,
            960, 226, 960, 190);
        trace(layout, control, j21, rg1,
            170, 440, 200, 440, 200, 470, 580, 470, 580, 530, 630, 530);
        trace(layout, gate, rg2, rpd1,
            790, 530, 840, 530, 1100, 530, 1100, 410,
            280, 410, 280, 350, 330, 350);
        trace(layout, gate, rg2, q1g,
            790, 530, 840, 530, 1100, 530, 1100, 50,
            890, 50, 890, 190, 920, 190);
        trace(layout, returned, j12, j22,
            170, 180, 220, 180, 220, 250, 60, 250,
            60, 550, 220, 550, 220, 500, 170, 500);
        trace(layout, returned, rpd2, j22,
            490, 350, 540, 350, 540, 300, 60, 300,
            60, 550, 220, 550, 220, 500, 170, 500);
        trace(layout, returned, q1s, j22,
            1000, 190, 1000, 226, 1000, 300, 60, 300,
            60, 550, 220, 550, 220, 500, 170, 500);
    }

    private static void trace(PcbBoardLayout layout, String net, String start, String end,
            int... points) {
        if (points == null || points.length < 4 || (points.length & 1) != 0)
            throw new IllegalArgumentException("Invalid controlled-indicator trace points");
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
            BoardPhysicalSpecifications specifications, String j11, String j12,
            String j1, String j2, String rg, String rpd, String q1, String rload,
            String led1) {
        PowerInputNameplate supply = specifications.getPowerInputNameplate("LOAD_VIN_INPUT");
        if (supply == null)
            throw new IllegalArgumentException("Controlled-indicator load supply nameplate is required");
        String supplyLabel = supply.getDisplayLabel();

        // These are the complete player-visible markings for this bounded
        // device.  No internal net, fault, topology, or proof identifier is
        // placed on the board.
        label(layout, "board-title", "CONTROLLED INDICATOR", 650, 600, 160, 18,
            true, null);
        label(layout, "component:" + j1, "J1", 100, 50, 24, 18, true, null);
        label(layout, "component:" + j2, "J2", 100, 365, 24, 18, true, null);
        label(layout, "component:" + rg, "RG", 670, 470, 28, 18, true, null);
        label(layout, "component:" + rpd, "RPD", 350, 265, 34, 18, true, null);
        label(layout, "component:" + q1, "Q1", 1040, 230, 28, 18, true, null);
        label(layout, "component:" + rload, "RLOAD", 400, 175, 52, 18, true, null);
        label(layout, "component:" + led1, "LED1", 480, 40, 42, 18, true, null);
        label(layout, "supply", supplyLabel, 230, 85, 46, 16, false, j11);
        label(layout, "return", "GND", 230, 220, 36, 16, false, j12);
    }

    private static void label(PcbBoardLayout layout, String id, String text,
            int x, int y, int width, int height, boolean bold, String targetPad) {
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text,
            new Rectangle(x, y, width, height), 12, bold, targetPad));
    }
}
