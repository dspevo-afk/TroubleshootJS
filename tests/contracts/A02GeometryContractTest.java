package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Focused current geometry and placement contract. */
public final class A02GeometryContractTest {
    private static int assertions;

    private A02GeometryContractTest() { }

    public static void main(String[] args) {
        // Real generators use CircuitElm.drag's simulator-owned grid even in
        // this native geometry check; no solver observations are supplied here.
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~(sim.gridSize - 1);
        sim.gridRound = sim.gridSize / 2 - 1;
        CircuitElm.sim = sim;
        bendSemantics();
        routeQualityUsesCurrentBends();
        connectedPlacementTarget();
        invalidGeometryRemainsRejected();
        silkscreenUsesPhysicalMarking();
        generatedCurrentPaths();
        System.out.println("PASS: A02GeometryContractTest " + assertions
                + " assertions");
    }

    private static void bendSemantics() {
        PcbBoardLayout layout = new PcbBoardLayout(400, 300,
                new Rectangle(20, 20, 360, 240), new Rectangle(0, 0, 1, 1));
        requireBends(layout, new int[] { 0, 10, 20 },
                new int[] { 0, 0, 0 }, 0, "equal horizontal subdivision");
        requireBends(layout, new int[] { 0, 10, 35, 100 },
                new int[] { 0, 0, 0, 0 }, 0, "unequal horizontal subdivisions");
        requireBends(layout, new int[] { 0, 0, 0, 0 },
                new int[] { 0, 10, 35, 100 }, 0, "unequal vertical subdivisions");
        requireBends(layout, new int[] { 0, 10, 10 },
                new int[] { 0, 0, 10 }, 1, "one orthogonal turn");
        requireBends(layout, new int[] { 0, 10, 10, 25, 25 },
                new int[] { 0, 0, 15, 15, 40 }, 3, "several orthogonal turns");
        requireBends(layout, new int[] { 0, 10, 0 },
                new int[] { 0, 0, 0 }, 1, "horizontal reversal");
        requireBends(layout, new int[] { 0, 0, 0 },
                new int[] { 0, 10, 0 }, 1, "vertical reversal");
        requireBends(layout, new int[] { 0, 10, 10, 35 },
                new int[] { 0, 0, 0, 0 }, 0, "repeated point on straight path");
        requireBends(layout, new int[] { 0, 10, 10, 10, 25 },
                new int[] { 0, 0, 0, 20, 20 }, 2,
                "repeated points around a real turn");
        // P01 bounds physical coordinates; full integer extremes now reject at construction.
        int limit = PcbCoordinateSystem.MAX_ABS_BOARD_COORDINATE;
        requireBends(layout, new int[] { limit - 100, limit - 1, limit },
                new int[] { 0, 0, 0 }, 0, "positive boundary coordinates");
        requireBends(layout, new int[] { -limit, -limit + 1, -limit + 100 },
                new int[] { 0, 0, 0 }, 0, "negative boundary coordinates");
    }

    private static void routeQualityUsesCurrentBends() {
        TroubleshootBoard board = new TroubleshootBoard("A02_SCORE");
        PcbTraceGeometry trace = new PcbTraceGeometry("N",
                new int[] { 0, 10, 30 }, new int[] { 0, 0, 0 });
        PcbBoardLayout implicit = new PcbBoardLayout(400, 300,
                new Rectangle(20, 20, 360, 240), new Rectangle(0, 0, 1, 1));
        PcbBoardLayout explicit = new PcbBoardLayout(400, 300,
                new Rectangle(20, 20, 360, 240), new Rectangle(0, 0, 1, 1),
                SeededPcbLayoutGenerator.CURRENT_VERSION);
        implicit.addTrace(trace);
        explicit.addTrace(new PcbTraceGeometry("N",
                new int[] { 0, 10, 30 }, new int[] { 0, 0, 0 }));
        require(implicit.getLayoutAlgorithmVersion() == SeededPcbLayoutGenerator.CURRENT_VERSION,
                "four-argument layout selects current algorithm");
        require(explicit.getLayoutAlgorithmVersion() == SeededPcbLayoutGenerator.CURRENT_VERSION,
                "explicit layout accepts current algorithm");
        require(implicit.getRouteQualityScore(board) == explicit.getRouteQualityScore(board),
                "all route scoring uses current bend semantics");
        require(implicit.getTraceBendCount(implicit.getTraces().get(0)) == 0,
                "straight subdivisions have no bends");
    }

    private static void connectedPlacementTarget() {
        TroubleshootBoard board = TroubleshootBoardFixtures.createLedIndicatorBoard();
        TopologyPlacementGraph topology = new TopologyPlacementGraph(board);
        BoardComponent candidateComponent = board.getComponent("LED1");
        final PcbFootprint prototype = PcbFootprint.fromPhysicalPackage(candidateComponent, 0, 0);
        PcbFootprint connector = PcbFootprint.fromPhysicalPackage(
                board.getComponent("J1"), 100, 150);
        PcbFootprint resistor = PcbFootprint.fromPhysicalPackage(
                board.getComponent("R1"), 310, 205);

        Vector<TopologyPlacementGraph.PadLink> allLinks =
                topology.getLinksFor(candidateComponent.getId());
        TopologyPlacementGraph.PadLink connectorLink = linkTo(allLinks, "J1");
        TopologyPlacementGraph.PadLink resistorLink = linkTo(allLinks, "R1");
        final Vector<PcbFootprint> onePlaced = new Vector<PcbFootprint>();
        onePlaced.add(connector);
        Point one = PcbPlacementPlanner.weightedConnectedTarget(prototype,
                onePlaced, allLinks, 321, -77);
        PcbPadPlacement oneSource = prototype.getPad(connectorLink.getPadId());
        PcbPadPlacement oneWorld = connector.getPad(connectorLink.getOtherPadId());
        require(one.x == oneWorld.getX() - oneSource.getX() &&
                one.y == oneWorld.getY() - oneSource.getY(),
                "one connected neighbor solves world minus local pad");

        Vector<PcbFootprint> twoPlaced = new Vector<PcbFootprint>();
        twoPlaced.add(connector);
        twoPlaced.add(resistor);
        Point two = PcbPlacementPlanner.weightedConnectedTarget(prototype,
                twoPlaced, allLinks, 321, -77);
        PcbPadPlacement twoSourceA = prototype.getPad(connectorLink.getPadId());
        PcbPadPlacement twoWorldA = connector.getPad(connectorLink.getOtherPadId());
        PcbPadPlacement twoSourceB = prototype.getPad(resistorLink.getPadId());
        PcbPadPlacement twoWorldB = resistor.getPad(resistorLink.getOtherPadId());
        double totalWeight = connectorLink.getWeight() + resistorLink.getWeight();
        int expectedX = (int) Math.round(((twoWorldA.getX() - twoSourceA.getX()) *
                connectorLink.getWeight() + (twoWorldB.getX() - twoSourceB.getX()) *
                resistorLink.getWeight()) / totalWeight);
        int expectedY = (int) Math.round(((twoWorldA.getY() - twoSourceA.getY()) *
                connectorLink.getWeight() + (twoWorldB.getY() - twoSourceB.getY()) *
                resistorLink.getWeight()) / totalWeight);
        require(two.x == expectedX && two.y == expectedY &&
                connectorLink.getWeight() != resistorLink.getWeight(),
                "multiple neighbors use unequal topology weights");

        Vector<PcbFootprint> noPlaced = new Vector<PcbFootprint>();
        Point fallback = PcbPlacementPlanner.weightedConnectedTarget(prototype,
                noPlaced, allLinks, 321, -77);
        require(fallback.x == 321 && fallback.y == -77,
                "no placed connected neighbor uses fallback");

        expectRejected(new Runnable() {
            public void run() {
                Vector<TopologyPlacementGraph.PadLink> invalid =
                        new Vector<TopologyPlacementGraph.PadLink>();
                invalid.add(new TopologyPlacementGraph.PadLink("LED1", "LED1.A",
                        "J1", "J1.1", "VIN", -1.0));
                PcbPlacementPlanner.weightedConnectedTarget(prototype,
                        onePlaced, invalid, 0, 0);
            }
        }, "negative placement weight");
        expectRejected(new Runnable() {
            public void run() {
                Vector<TopologyPlacementGraph.PadLink> invalid =
                        new Vector<TopologyPlacementGraph.PadLink>();
                invalid.add(null);
                PcbPlacementPlanner.weightedConnectedTarget(prototype,
                        onePlaced, invalid, 0, 0);
            }
        }, "null placement link");
    }

    private static void generatedCurrentPaths() {
        String[] families = { QuickPlayFamilyRegistry.LED_INDICATOR,
            QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR,
            QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR };
        for (String family : families) {
            GeneratedBoardInstance first = QuickPlayFamilyRegistry.generate(family, 0L);
            GeneratedBoardInstance second = QuickPlayFamilyRegistry.generate(family, 0L);
            require(first.getPcbLayout().getLayoutAlgorithmVersion() ==
                    SeededPcbLayoutGenerator.CURRENT_VERSION,
                family + " uses current layout algorithm");
            require(first.getPcbLayout().geometryFingerprint().equals(
                    second.getPcbLayout().geometryFingerprint()),
                family + " current geometry remains deterministic");
            first.getPcbLayout().validateGeometry(first.getBoard());
        }
        for (String family : new String[] { QuickPlayFamilyRegistry.RC_DELAY,
                QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH,
                QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH }) {
            GeneratedBoardInstance instance = QuickPlayFamilyRegistry.generate(family, 0L);
            require(instance.getPcbLayout().getLayoutAlgorithmVersion() ==
                    SeededPcbLayoutGenerator.CURRENT_VERSION,
                family + " fixed layout uses current algorithm");
            instance.getPcbLayout().validateGeometry(instance.getBoard());
        }
    }

    private static void invalidGeometryRemainsRejected() {
        final TroubleshootBoard board = TroubleshootBoardFixtures.createLedIndicatorBoard();
        final PcbBoardLayout diagonal = new SeededPcbLayoutGenerator().generate(board, 0L);
        final PcbPadPlacement start = diagonal.getPad("J1.1");
        final PcbPadPlacement end = diagonal.getPad("R1.1");
        diagonal.addTrace(new PcbTraceGeometry("VIN", "J1.1", "R1.1",
                new int[] { start.getX(), end.getX() },
                new int[] { start.getY(), end.getY() }));
        expectIllegalState(new Runnable() {
            public void run() { diagonal.validateGeometry(board); }
        }, "diagonal route", "PCB trace is not Manhattan routed:");
    }

    private static void silkscreenUsesPhysicalMarking() {
        String semanticId = "block/source/device/resistor-with-long-semantic-identity";
        TroubleshootBoard board = new TroubleshootBoard("A02_DISPLAY_MARKING");
        board.addNet(new BoardNet("VIN"));
        board.addNet(new BoardNet("GND"));
        board.addComponent(new BoardComponent("J1", "CONNECTOR",
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, "J1"));
        board.addComponent(new BoardComponent(semanticId, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR, "R1"));
        board.addPad(new BoardPad("J1.1", "J1", "1", "VIN"));
        board.addPad(new BoardPad("J1.2", "J1", "2", "GND"));
        board.addPad(new BoardPad(semanticId + ".1", semanticId, "1", "VIN"));
        board.addPad(new BoardPad(semanticId + ".2", semanticId, "2", "GND"));
        board.addPowerInput(new ExternalBoardPowerInput("VIN_INPUT", "J1.1", "J1.2",
                "VIN", "GND"));
        board.validate();

        PcbBoardLayout layout = new SeededPcbLayoutGenerator().generate(board, 1L);
        PcbSilkscreenLabel label = layout.getSilkscreenLabel("component:" + semanticId);
        require(label != null && "R1".equals(label.getText()) &&
                !semanticId.equals(label.getText()),
                "silkscreen uses the explicit physical marking for a long semantic ID");
        layout.validateGeometry(board);
    }

    private static TopologyPlacementGraph.PadLink linkTo(
            Vector<TopologyPlacementGraph.PadLink> links, String componentId) {
        for (TopologyPlacementGraph.PadLink link : links)
            if (componentId.equals(link.getOtherComponentId())) return link;
        throw new AssertionError("missing topology link to " + componentId);
    }

    private static void requireBends(PcbBoardLayout layout, int[] x, int[] y,
            int expected, String label) {
        require(layout.getTraceBendCount(new PcbTraceGeometry("N", x, y)) == expected,
                label + " bend count");
    }

    private static void expectRejected(Runnable action, String label) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, label + " is rejected");
    }

    private static void expectIllegalState(Runnable action, String label, String expectedReason) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalStateException expected) {
            require(expected.getMessage().startsWith(expectedReason),
                label + " rejected at intended geometry boundary");
            rejected = true;
        }
        require(rejected, label + " is rejected");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
