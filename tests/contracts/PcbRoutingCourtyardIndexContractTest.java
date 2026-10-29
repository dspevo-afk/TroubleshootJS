package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Vector;

/** Independent broad-phase coverage and full-scan decision parity for courtyard routing. */
public final class PcbRoutingCourtyardIndexContractTest {
    private static final int GRID = 10;
    private static final int[][] DIRECTIONS = {{0, -1, 0}, {1, 0, 1}, {0, 1, 2}, {-1, 0, 3}};
    private static int assertions;

    private PcbRoutingCourtyardIndexContractTest() { }

    public static void main(String[] args) throws Exception {
        for (PcbTraceWidthProfile profile : new PcbTraceWidthProfile[] {
                PcbTraceWidthProfile.UNIFORM, PcbTraceWidthProfile.ROLE_WIDTHS }) {
            for (PcbCopperLayer layer : new PcbCopperLayer[] {
                    PcbCopperLayer.TOP, PcbCopperLayer.BOTTOM }) {
                Fixture fixture = new Fixture(profile);
                PcbNetRouter.Router indexed = new PcbNetRouter.Router(fixture.layout,
                    fixture.board, fixture.outline, 0, P06FactoryLinkFixtures.OBSERVER, layer);
                verifyCandidateCoverage(indexed, profile, layer);
                verifyOverflowFallback(fixture, layer);
                PcbNetRouter.Router fullScan = new PcbNetRouter.Router(fixture.layout,
                    fixture.board, fixture.outline, 0, P06FactoryLinkFixtures.OBSERVER, layer);
                forceFullComponentScan(fullScan);
                verifyQueryParity(indexed, fullScan, fixture, profile, layer);
            }
        }
        verifyPadLookupContracts();
        System.out.println("PASS: PCB routing courtyard index contracts assertions=" + assertions);
    }

    /** Out-of-int broadphase projections conservatively keep every same-face component. */
    private static void verifyOverflowFallback(Fixture fixture, PcbCopperLayer layer)
            throws Exception {
        PcbComponentPlacement[] components;
        int faceCount = 0;
        for (int origin : new int[] {Integer.MAX_VALUE - 20, Integer.MIN_VALUE}) {
            PcbNetRouter.Router router = new PcbNetRouter.Router(fixture.layout, fixture.board,
                new Rectangle(origin, 0, 20, 20), 0, P06FactoryLinkFixtures.OBSERVER, layer);
            components = (PcbComponentPlacement[])field(router, "components");
            int[][] byColumn = (int[][])field(router, "componentCandidatesByColumn");
            faceCount = 0;
            for (PcbComponentPlacement component : components)
                if (component.getMountingSide() == layer.getFace()) faceCount++;
            for (int[] candidates : byColumn) {
                check(candidates.length == faceCount,
                    "overflowing x projection falls back to the complete same-face component set");
                int previous = -1;
                for (int candidate : candidates) {
                    check(candidate > previous && components[candidate].getMountingSide() == layer.getFace(),
                        "overflow fallback preserves face and original order");
                    previous = candidate;
                }
            }
        }
    }

    /** Exhaustively compare each column index with actual point and finite-stroke geometry. */
    private static void verifyCandidateCoverage(PcbNetRouter.Router router,
            PcbTraceWidthProfile profile, PcbCopperLayer layer) throws Exception {
        PcbComponentPlacement[] components = (PcbComponentPlacement[])field(router, "components");
        Rectangle[] courtyards = (Rectangle[])field(router, "courtyards");
        int[][] byColumn = (int[][])field(router, "componentCandidatesByColumn");
        int minX = ((Integer)field(router, "minX")).intValue();
        int minY = ((Integer)field(router, "minY")).intValue();
        int gridWidth = ((Integer)field(router, "gridWidth")).intValue();
        int gridHeight = ((Integer)field(router, "gridHeight")).intValue();
        int[] widths = profile == PcbTraceWidthProfile.ROLE_WIDTHS ?
            new int[] {9, 11, 13} : new int[] {9};
        int faceComponents = 0;
        for (PcbComponentPlacement component : components)
            if (component.getMountingSide() == layer.getFace()) faceComponents++;
        boolean narrowed = false;

        check(minX < 0 && minY < 0 && byColumn.length == gridWidth,
            "oracle spans negative physical coordinates and every routing column");
        for (int column = 0; column < gridWidth; column++) {
            int[] candidates = byColumn[column];
            int previous = -1;
            for (int candidate : candidates) {
                check(candidate > previous && candidate < components.length,
                    "column candidates retain unique original component order");
                check(components[candidate].getMountingSide() == layer.getFace(),
                    "column index contains only components mounted on the routed face");
                previous = candidate;
            }
            if (candidates.length < faceComponents) narrowed = true;

            long centerX = (long)minX + (long)column * GRID;
            for (int index = 0; index < components.length; index++) {
                if (components[index].getMountingSide() != layer.getFace()) continue;
                Rectangle courtyard = courtyards[index];
                boolean required = false;
                for (int row = 0; row < gridHeight && !required; row++) {
                    int centerY = minY + row * GRID;
                    if (containsInclusive(courtyard, (int)centerX, centerY)) {
                        required = true;
                        break;
                    }
                    for (int[] direction : directions()) {
                        int nextColumn = column + direction[0];
                        int nextRow = row + direction[1];
                        if (nextColumn < 0 || nextColumn >= gridWidth ||
                                nextRow < 0 || nextRow >= gridHeight) continue;
                        int nextX = minX + nextColumn * GRID;
                        int nextY = minY + nextRow * GRID;
                        for (int width : widths) {
                            Rectangle actualStroke = PcbConductorBuilder.stroke((int)centerX,
                                centerY, nextX, nextY, width);
                            if (courtyard.intersects(actualStroke)) {
                                required = true;
                                break;
                            }
                        }
                        if (required) break;
                    }
                }
                if (required)
                    check(contains(candidates, index), "actual point/stroke hit is indexed at column=" +
                        column + " component=" + components[index].getComponentId());
            }
        }
        check(narrowed, "component columns prune at least one full component scan");
        int[] first = byColumn[0], last = byColumn[gridWidth - 1];
        int leftEdge = componentIndex(components, "EDGE_LEFT");
        int rightEdge = componentIndex(components, "EDGE_RIGHT");
        if (layer == PcbCopperLayer.TOP) {
            check(contains(first, leftEdge) && !contains(last, rightEdge),
                "top-face left boundary is retained and opposite-face right edge is excluded");
        } else {
            check(contains(last, rightEdge) && !contains(first, leftEdge),
                "bottom-face right boundary is retained and opposite-face left edge is excluded");
        }
    }

    /** Exercise exact Router queries against a reflection-forced original full scan. */
    private static void verifyQueryParity(PcbNetRouter.Router indexed,
            PcbNetRouter.Router fullScan, Fixture fixture, PcbTraceWidthProfile profile,
            PcbCopperLayer layer) throws Exception {
        boolean top = layer == PcbCopperLayer.TOP;
        checkEquivalent(indexed, fullScan, "HIGH", fixture.highStart, fixture.highEnd,
            -500, -400, -490, -400, Boolean.TRUE, "start escape");
        requireWidth(indexed, profile, 13, "HIGH_CURRENT width");
        checkEquivalent(indexed, fullScan, "HIGH", fixture.highStart, fixture.highEnd,
            -210, -400, -200, -400, Boolean.TRUE, "end escape");
        // At y=-130 the raised courtyards end at -155 and -235; only the start
        // endpoint courtyard [-510,-470]x[-140,-120] is touched, along its +x escape.
        checkEquivalent(indexed, fullScan, "SUPPLY", fixture.supplyStart, fixture.supplyEnd,
            -500, -130, -490, -130, Boolean.TRUE, "role-width switch to supply");
        requireWidth(indexed, profile, 11, "SUPPLY width");
        checkEquivalent(indexed, fullScan, "SIGNAL", fixture.signalStart, fixture.signalEnd,
            -500, -300, -500, -290, Boolean.TRUE, "vertical start escape and role-width switch");
        requireWidth(indexed, profile, 9, "SIGNAL width");
        checkEquivalent(indexed, fullScan, "HIGH", fixture.highStart, fixture.highEnd,
            -500, -400, -490, -400, Boolean.TRUE, "role-width switch back to HIGH_CURRENT");
        requireWidth(indexed, profile, 13, "HIGH_CURRENT width after switching back");
        checkEquivalent(indexed, fullScan, "SIGNAL", fixture.signalStart, fixture.signalEnd,
            -200, -310, -200, -300, Boolean.TRUE, "vertical end escape");

        int obstacleX = top ? -390 : -380;
        int obstacleY = top ? -390 : -290;
        // These points lie inside [-420,-380]x[-404,-384] on top and
        // [-394,-354]x[-304,-284] on bottom; all four strokes intersect.
        for (int[] direction : directions()) {
            checkEquivalent(indexed, fullScan, "HIGH", fixture.highStart, fixture.highEnd,
                obstacleX, obstacleY, obstacleX + direction[0] * GRID,
                obstacleY + direction[1] * GRID, Boolean.FALSE,
                (top ? "top" : "bottom") + " courtyard adjacent direction " + direction[2]);
        }
        // Opposite-face courtyards do not participate in the exact query.
        checkEquivalent(indexed, fullScan, "HIGH", fixture.highStart, fixture.highEnd,
            top ? -380 : -390, top ? -290 : -390,
            top ? -370 : -380, top ? -290 : -390, Boolean.TRUE,
            "opposite mounting face is ignored");

        int underpassY = top ? -180 : -260;
        checkEquivalent(indexed, fullScan, "HIGH", fixture.highStart, fixture.highEnd,
            -400, underpassY, -390, underpassY, Boolean.TRUE, "raised underpass permitted");
        indexed.factoryUnderpasses = false;
        fullScan.factoryUnderpasses = false;
        checkEquivalent(indexed, fullScan, "HIGH", fixture.highStart, fixture.highEnd,
            -400, underpassY, -390, underpassY, Boolean.FALSE, "raised underpass disabled");
        indexed.factoryUnderpasses = true;
        fullScan.factoryUnderpasses = true;

        // This long stroke crosses only the top courtyard, outside the start-column envelope.
        checkEquivalent(indexed, fullScan, "HIGH", fixture.highStart, fixture.highEnd,
            -460, -390, -370, -390, top ? Boolean.FALSE : Boolean.TRUE,
            "nonadjacent traversal uses full scan");
        checkEquivalent(indexed, fullScan, "HIGH", fixture.highStart, fixture.highEnd,
            -430, -390, -430, -390, Boolean.TRUE, "zero-length traversal uses full scan");
    }

    /** Preserve pad collision decisions while checking coordinates before endpoint IDs. */
    private static void verifyPadLookupContracts() throws Exception {
        PadLookupFixture fixture = new PadLookupFixture();
        PcbNetRouter.Router router = new PcbNetRouter.Router(fixture.layout, fixture.board,
            fixture.outline, 0, P06FactoryLinkFixtures.OBSERVER, PcbCopperLayer.TOP);
        PcbPadPlacement[] pads = (PcbPadPlacement[])field(router, "pads");

        checkPadLookup(router, pads, fixture.startEndpoint.getX(), fixture.startEndpoint.getY(),
            fixture.startEndpoint.getPadId(), null, true,
            "distinct other-net pad at the endpoint coordinate still blocks");
        checkPadLookup(router, pads, fixture.loneEndpoint.getX(), fixture.loneEndpoint.getY(),
            new String(fixture.loneEndpoint.getPadId()), null, false,
            "start endpoint ID uses value equality");
        checkPadLookup(router, pads, fixture.loneEndpoint.getX(), fixture.loneEndpoint.getY(),
            null, new String(fixture.loneEndpoint.getPadId()), false,
            "end endpoint ID uses value equality");
        checkPadLookup(router, pads, fixture.startEndpoint.getX(), fixture.startEndpoint.getY(),
            null, null, true, "null endpoint IDs retain coincident-pad rejection");
        checkPadLookup(router, pads, fixture.loneEndpoint.getX(), fixture.loneEndpoint.getY(),
            null, null, true, "null endpoint IDs do not exempt a pad");
        checkPadLookup(router, pads, -300, -350, null, null, false,
            "missing negative coordinate has no pad");
        checkPadLookup(router, pads, -499, -399, null, null, false,
            "negative off-grid coordinate has no pad");
        checkPadLookup(router, pads, 17, 23, null, null, false,
            "positive off-grid coordinate has no pad");
    }

    private static void checkPadLookup(PcbNetRouter.Router router, PcbPadPlacement[] pads,
            int x, int y, String startPadId, String endPadId, boolean expected, String label)
            throws Exception {
        boolean actual = invokePadLookup(router, x, y, startPadId, endPadId);
        boolean reference = originalPadLookup(pads, x, y, startPadId, endPadId);
        check(actual == reference, label + " matches independent original full scan");
        check(actual == expected, label + " retains fixed expected result");
    }

    private static boolean invokePadLookup(PcbNetRouter.Router router, int x, int y,
            String startPadId, String endPadId) throws Exception {
        Method method = PcbNetRouter.Router.class.getDeclaredMethod("isPadAtOtherNet",
            Integer.TYPE, Integer.TYPE, String.class, String.class);
        method.setAccessible(true);
        return ((Boolean)method.invoke(router, Integer.valueOf(x), Integer.valueOf(y),
            startPadId, endPadId)).booleanValue();
    }

    /** Literal copy of the prior full-scan decision, independent of the optimized query. */
    private static boolean originalPadLookup(PcbPadPlacement[] pads, int x, int y,
            String startPadId, String endPadId) {
        for (PcbPadPlacement pad : pads) {
            if (pad.getPadId().equals(startPadId) || pad.getPadId().equals(endPadId))
                continue;
            if (pad.getX() == x && pad.getY() == y)
                return true;
        }
        return false;
    }

    private static void checkEquivalent(PcbNetRouter.Router indexed,
            PcbNetRouter.Router fullScan, String net, PcbPadPlacement start,
            PcbPadPlacement end, int x1, int y1, int x2, int y2, Boolean expected,
            String label) {
        boolean actual = indexed.permitsLayerStep(x1, y1, x2, y2, net, start, end);
        boolean reference = fullScan.permitsLayerStep(x1, y1, x2, y2, net, start, end);
        check(actual == reference, label + " matches full component scan");
        if (expected != null) check(actual == expected.booleanValue(), label + " retains exact fixed result");
    }

    private static void requireWidth(PcbNetRouter.Router router, PcbTraceWidthProfile profile,
            int roleWidth, String label) throws Exception {
        int expected = profile == PcbTraceWidthProfile.ROLE_WIDTHS ? roleWidth : 9;
        check(((Integer)field(router, "activeTraceWidth")).intValue() == expected,
            label + " is prepared before candidate query");
    }

    private static void forceFullComponentScan(PcbNetRouter.Router router) throws Exception {
        int[] all = (int[])field(router, "allComponentIndexes");
        int[][] byColumn = (int[][])field(router, "componentCandidatesByColumn");
        for (int column = 0; column < byColumn.length; column++) byColumn[column] = all;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int componentIndex(PcbComponentPlacement[] components, String id) {
        for (int index = 0; index < components.length; index++)
            if (id.equals(components[index].getComponentId())) return index;
        throw new AssertionError("Missing courtyard fixture component " + id);
    }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) if (candidate == value) return true;
        return false;
    }

    private static boolean containsInclusive(Rectangle rectangle, int x, int y) {
        return x >= rectangle.x && y >= rectangle.y &&
            (long)x <= (long)rectangle.x + rectangle.width &&
            (long)y <= (long)rectangle.y + rectangle.height;
    }

    private static int[][] directions() {
        return DIRECTIONS;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        final TroubleshootBoard board = new TroubleshootBoard("ROUTING_COURTYARD_INDEX");
        final Rectangle outline = new Rectangle(-600, -500, 500, 400);
        final PcbBoardLayout layout = new PcbBoardLayout(800, 600, outline,
            new Rectangle(100, 100, 200, 200));
        final PcbPadPlacement highStart, highEnd, signalStart, signalEnd, supplyStart, supplyEnd;

        Fixture(PcbTraceWidthProfile profile) {
            board.addNet(new BoardNet("HIGH", BoardNet.RoutingRole.HIGH_CURRENT));
            board.addNet(new BoardNet("SIGNAL", BoardNet.RoutingRole.SIGNAL));
            board.addNet(new BoardNet("SUPPLY", BoardNet.RoutingRole.SUPPLY));
            highStart = endpoint("HIGH_START", "HIGH", -500, -400, 1, 0, 30);
            highEnd = endpoint("HIGH_END", "HIGH", -200, -400, -1, 0, 30);
            signalStart = endpoint("SIGNAL_START", "SIGNAL", -500, -300, 0, 1, 30);
            signalEnd = endpoint("SIGNAL_END", "SIGNAL", -200, -300, 0, -1, 30);
            supplyStart = endpoint("SUPPLY_START", "SUPPLY", -500, -130, 1, 0, 30);
            supplyEnd = endpoint("SUPPLY_END", "SUPPLY", -200, -130, -1, 0, 30);
            Vector<PcbPlacementConstraints.Part> demands = new Vector<PcbPlacementConstraints.Part>();
            for (String id : board.getComponentIds())
                demands.add(new PcbPlacementConstraints.Part(id, "fixture", "Routing fixture",
                    "board", PcbPlacementConstraints.Anchor.NONE, 16));
            board.setPlacementConstraints(new PcbPlacementConstraints(demands,
                new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID, 1, 1, false, profile));

            addLayoutComponent("OBSTACLE_TOP", P06FactoryLinkFixtures.TEST_POINT,
                new PcbPackagePose(-420, -404, PcbRotation.DEG_0, PcbBoardSide.TOP));
            addLayoutComponent("OBSTACLE_BOTTOM", P06FactoryLinkFixtures.TEST_POINT,
                new PcbPackagePose(-394, -304, PcbRotation.DEG_0, PcbBoardSide.BOTTOM));
            addLayoutComponent("EDGE_LEFT", P06FactoryLinkFixtures.TEST_POINT,
                new PcbPackagePose(-610, -494, PcbRotation.DEG_0, PcbBoardSide.TOP));
            addLayoutComponent("EDGE_RIGHT", P06FactoryLinkFixtures.TEST_POINT,
                new PcbPackagePose(-130, -430, PcbRotation.DEG_0, PcbBoardSide.BOTTOM));
            addLayoutComponent("EDGE_TOP", P06FactoryLinkFixtures.TEST_POINT,
                new PcbPackagePose(-300, -494, PcbRotation.DEG_0, PcbBoardSide.TOP));
            addLayoutComponent("EDGE_BOTTOM", P06FactoryLinkFixtures.TEST_POINT,
                new PcbPackagePose(-300, -126, PcbRotation.DEG_0, PcbBoardSide.TOP));
            addLayoutComponent("RAISED_TOP", PhysicalPackages.RAISED_FACTORY_LINK,
                new PcbPackagePose(-500, -220, PcbRotation.DEG_0, PcbBoardSide.TOP));
            addLayoutComponent("RAISED_BOTTOM", PhysicalPackages.RAISED_FACTORY_LINK,
                new PcbPackagePose(-500, -300, PcbRotation.DEG_0, PcbBoardSide.BOTTOM));
        }

        private PcbPadPlacement endpoint(String id, String net, int x, int y,
                int escapeX, int escapeY, int escapeLength) {
            PhysicalPackage physical = P06FactoryLinkFixtures.TEST_POINT;
            BoardComponent component = new BoardComponent(id, physical.getId(), physical, id);
            board.addComponent(component);
            board.addPad(new BoardPad(id + ".1", id, "1", net));
            layout.addComponent(PcbComponentPlacement.fromPhysicalGeometry(id,
                new PcbPackagePose(x - 10, y - 10, PcbRotation.DEG_0, PcbBoardSide.TOP),
                physical, physical.getGeometry()));
            PcbPadPlacement pad = new PcbPadPlacement(id + ".1", x, y, escapeX, escapeY,
                escapeLength, new Rectangle(x - 5, y - 5, 10, 10),
                new Rectangle(x - 8, y - 8, 16, 16), PcbTerminalAttachment.PLATED_THROUGH_HOLE,
                PcbBoardSide.TOP);
            layout.addPad(pad);
            return pad;
        }

        private void addLayoutComponent(String id, PhysicalPackage physical, PcbPackagePose pose) {
            layout.addComponent(PcbComponentPlacement.fromPhysicalGeometry(id, pose, physical,
                physical.getGeometry()));
        }
    }

    private static final class PadLookupFixture {
        final TroubleshootBoard board = new TroubleshootBoard("ROUTING_PAD_LOOKUP");
        final Rectangle outline = new Rectangle(-600, -500, 500, 400);
        final PcbBoardLayout layout = new PcbBoardLayout(800, 600, outline,
            new Rectangle(100, 100, 200, 200));
        final PcbPadPlacement startEndpoint;
        final PcbPadPlacement loneEndpoint;

        PadLookupFixture() {
            board.addNet(new BoardNet("START_NET", BoardNet.RoutingRole.SIGNAL));
            board.addNet(new BoardNet("OTHER_NET", BoardNet.RoutingRole.SIGNAL));
            board.addNet(new BoardNet("LONE_NET", BoardNet.RoutingRole.SIGNAL));
            addEndpointComponent("START", "START.P1", "START_NET", -500, -400);
            addEndpointComponent("COLOCATED_OTHER", "OTHER.P1", "OTHER_NET", -500, -400);
            addEndpointComponent("LONE", "LONE.P1", "LONE_NET", -200, -400);

            Vector<PcbPlacementConstraints.Part> demands = new Vector<PcbPlacementConstraints.Part>();
            for (String id : board.getComponentIds())
                demands.add(new PcbPlacementConstraints.Part(id, "fixture", "Pad lookup fixture",
                    "board", PcbPlacementConstraints.Anchor.NONE, 16));
            board.setPlacementConstraints(new PcbPlacementConstraints(demands,
                new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID, 1, 1, false,
                PcbTraceWidthProfile.UNIFORM));
            startEndpoint = layout.getPad("START.P1");
            loneEndpoint = layout.getPad("LONE.P1");
        }

        private void addEndpointComponent(String componentId, String padId, String netId,
                int x, int y) {
            PhysicalPackage physical = P06FactoryLinkFixtures.TEST_POINT;
            BoardComponent component = new BoardComponent(componentId, physical.getId(),
                physical, componentId);
            board.addComponent(component);
            board.addPad(new BoardPad(padId, componentId, "1", netId));
            layout.addComponent(PcbComponentPlacement.fromPhysicalGeometry(componentId,
                new PcbPackagePose(x - 10, y - 10, PcbRotation.DEG_0, PcbBoardSide.TOP),
                physical, physical.getGeometry()));
            layout.addPad(new PcbPadPlacement(padId, x, y, 1, 0, 30,
                new Rectangle(x - 5, y - 5, 10, 10), new Rectangle(x - 8, y - 8, 16, 16),
                PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP));
        }
    }
}
