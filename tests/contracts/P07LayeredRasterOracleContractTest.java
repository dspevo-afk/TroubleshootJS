package com.lushprojects.circuitjs1.client;

/** Focused physical fixtures for the independent face-qualified centerline oracle. */
public final class P07LayeredRasterOracleContractTest {
    private static int checks;

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void reject(String messagePart, Runnable action, String reason) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalStateException expected) {
            rejected = true;
            require(expected.getMessage() != null && expected.getMessage().contains(messagePart),
                reason + ": wrong rejection: " + expected.getMessage());
        }
        require(rejected, reason + ": invalid copper was accepted");
    }

    public static void main(String[] args) {
        twoFaceCrossingStaysSeparate();
        pthPadBarrelConnectsActualFaces();
        viaConnectsTwoFaceRouteAndWrongFaceCrossingStaysSeparate();
        missingViaDoesNotConnectFaces();
        wrongNetViaIsRejected();
        viaMustTouchTraceOnBothFaces();
        unintendedSameNetCrossFaceJoinIsRejected();
        declaredPackageInternalLinkConnectsTerminals();
        System.out.println("PASS: P07 layered raster oracle contracts assertions=" + checks);
    }

    private static void twoFaceCrossingStaysSeparate() {
        final Fixture fixture = new Fixture("CROSSING", "TOP_NET", "BOTTOM_NET");
        fixture.addTwoPadPart("TOP", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "TOP_NET", "T1", 10, 20, "T2", 30, 20,
            PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP);
        fixture.addTwoPadPart("BOTTOM", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "BOTTOM_NET", "B1", 20, 10, "B2", 20, 30,
            PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP);
        fixture.addTrace("cross-top", "TOP_NET", "T1", "T2", PcbCopperLayer.TOP,
            new int[] { 10, 30 }, new int[] { 20, 20 });
        fixture.addTrace("cross-bottom", "BOTTOM_NET", "B1", "B2", PcbCopperLayer.BOTTOM,
            new int[] { 20, 20 }, new int[] { 10, 30 });

        P07LayeredRasterOracle.Metrics metrics = P07LayeredRasterOracle.verify(
            fixture.layout, fixture.board);
        require(metrics.getOrderedPadPairs() == 16,
            "every ordered pad pair is checked for the two-face crossing");
        require(metrics.getPlatedPadBarrels() == 4 && metrics.getViaBarrels() == 0,
            "crossing fixture has only its four declared PTH pad barrels");
    }

    private static void pthPadBarrelConnectsActualFaces() {
        Fixture fixture = new Fixture("PTH_BARREL", "N");
        fixture.addTwoPadPart("J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "N", "J1.1", 10, 10, "J1.2", 30, 30,
            PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP);
        fixture.addTrace("top-approach", "N", "J1.1", null, PcbCopperLayer.TOP,
            new int[] { 10, 20 }, new int[] { 10, 10 });
        fixture.addTrace("bottom-departure", "N", null, "J1.2", PcbCopperLayer.BOTTOM,
            new int[] { 10, 10, 10, 30 }, new int[] { 10, 30, 30, 30 });

        P07LayeredRasterOracle.Metrics metrics = P07LayeredRasterOracle.verify(
            fixture.layout, fixture.board);
        require(metrics.getOrderedPadPairs() == 4 && metrics.getPlatedPadBarrels() == 2,
            "the two-terminal board net connects through a declared plated pad barrel");
        require(metrics.getViaBarrels() == 0,
            "this face transition is supplied by the PTH pad, not a via");
    }

    private static void viaConnectsTwoFaceRouteAndWrongFaceCrossingStaysSeparate() {
        Fixture fixture = viaFixture("N", true, true);
        P07LayeredRasterOracle.Metrics metrics = P07LayeredRasterOracle.verify(
            fixture.layout, fixture.board);
        require(metrics.getOrderedPadPairs() == 16 && metrics.getViaBarrels() == 1,
            "an actual two-face via completes the route and all ordered pairs are checked");
        require(metrics.getTraceUnitSteps() >= 60,
            "trace centerlines were expanded into their actual unit steps");
    }

    private static void missingViaDoesNotConnectFaces() {
        final Fixture fixture = viaFixture(null, false, true);
        reject("Raster connectivity disagrees with board net partition", new Runnable() {
            public void run() { P07LayeredRasterOracle.verify(fixture.layout, fixture.board); }
        }, "opposite-face centerline crossing without its declared via");
    }

    private static void wrongNetViaIsRejected() {
        final Fixture fixture = viaFixture("M", true, true);
        reject("Plated hole net disagrees", new Runnable() {
            public void run() { P07LayeredRasterOracle.verify(fixture.layout, fixture.board); }
        }, "via net must match copper at both contacted centers");
    }

    private static void viaMustTouchTraceOnBothFaces() {
        final Fixture fixture = viaFixture("N", true, false);
        reject("does not contact trace copper on both faces", new Runnable() {
            public void run() { P07LayeredRasterOracle.verify(fixture.layout, fixture.board); }
        }, "a via with a missing bottom-face trace contact");
    }

    private static void unintendedSameNetCrossFaceJoinIsRejected() {
        final Fixture fixture = new Fixture("UNINTENDED_JOIN", "N");
        fixture.addTwoPadPart("J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "N", "J1.1", 10, 20, "J1.2", 20, 10,
            PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP);
        fixture.addTrace("top", "N", "J1.1", null, PcbCopperLayer.TOP,
            new int[] { 10, 20 }, new int[] { 20, 20 });
        fixture.addTrace("bottom", "N", "J1.2", null, PcbCopperLayer.BOTTOM,
            new int[] { 20, 20 }, new int[] { 10, 20 });
        reject("Raster connectivity disagrees with board net partition", new Runnable() {
            public void run() { P07LayeredRasterOracle.verify(fixture.layout, fixture.board); }
        }, "same-net traces that only cross on opposite faces must remain disconnected");
    }

    private static void declaredPackageInternalLinkConnectsTerminals() {
        Fixture fixture = new Fixture("PACKAGE_LINK", "N");
        fixture.addTwoPadPart("U1", PhysicalPackages.DEV_CANARY_3, "N",
            "U1.1", 10, 10, "U1.2", 30, 10,
            PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP);
        P07LayeredRasterOracle.Metrics metrics = P07LayeredRasterOracle.verify(
            fixture.layout, fixture.board);
        require(metrics.getOrderedPadPairs() == 4 && metrics.getPackageInternalLinks() == 1,
            "only the package's declared terminal link connects the otherwise isolated pads");
    }

    private static Fixture viaFixture(String viaNet, boolean includeVia,
            boolean includeBottomTrace) {
        Fixture fixture = new Fixture("VIA_FIXTURE", "N", "M");
        fixture.addTwoPadPart("JN", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "N", "N1", 10, 10, "N2", 30, 30,
            PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP);
        fixture.addTwoPadPart("JM", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "M", "M1", 10, 20, "M2", 30, 20,
            PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP);
        fixture.addTrace("n-top", "N", "N1", null, PcbCopperLayer.TOP,
            new int[] { 10, 20 }, new int[] { 10, 10 });
        if (includeBottomTrace)
            fixture.addTrace("n-bottom", "N", null, "N2", PcbCopperLayer.BOTTOM,
                new int[] { 20, 20, 20, 30 }, new int[] { 10, 30, 30, 30 });
        fixture.addTrace("m-top", "M", "M1", "M2", PcbCopperLayer.TOP,
            new int[] { 10, 30 }, new int[] { 20, 20 });
        if (includeVia) {
            fixture.layout.addHole(new PcbBoardHole("V1", PcbBoardHole.Kind.VIA,
                viaNet, 20, 10, 1, 2, PcbCopperLayer.TOP, PcbCopperLayer.BOTTOM,
                PcbCopperAccess.Exposure.EXPOSED));
        }
        return fixture;
    }

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;

        Fixture(String id, String... netIds) {
            board = new TroubleshootBoard(id);
            for (String netId : netIds) board.addNet(new BoardNet(netId));
            layout = new PcbBoardLayout(100, 100, new Rectangle(1, 1, 80, 80),
                new Rectangle(85, 5, 10, 10));
        }

        void addTwoPadPart(String componentId, PhysicalPackage physicalPackage,
                String netId, String firstPadId, int firstX, int firstY,
                String secondPadId, int secondX, int secondY,
                PcbTerminalAttachment attachment, PcbBoardSide mountingSide) {
            board.addComponent(new BoardComponent(componentId, "RASTER_ORACLE_FIXTURE",
                physicalPackage));
            addPad(componentId, "1", netId, firstPadId, firstX, firstY, attachment,
                mountingSide);
            addPad(componentId, "2", netId, secondPadId, secondX, secondY, attachment,
                mountingSide);
        }

        private void addPad(String componentId, String terminalId, String netId,
                String padId, int x, int y, PcbTerminalAttachment attachment,
                PcbBoardSide mountingSide) {
            board.addPad(new BoardPad(padId, componentId, terminalId, netId));
            Rectangle padBounds = new Rectangle(x - 1, y - 1, 2, 2);
            Rectangle probeBounds = new Rectangle(x - 2, y - 2, 4, 4);
            layout.addPad(new PcbPadPlacement(padId, x, y, 0, 0, 0, padBounds,
                probeBounds, attachment, mountingSide));
        }

        void addTrace(String id, String netId, String startPadId, String endPadId,
                PcbCopperLayer layer, int[] xs, int[] ys) {
            layout.addTrace(new PcbTraceGeometry(id, netId, startPadId, endPadId,
                layer, PcbCopperAccess.Exposure.EXPOSED, xs, ys, 9));
        }
    }
}
