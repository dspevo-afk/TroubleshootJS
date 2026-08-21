package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Permanent developer proof for every registered production pad escape. */
final class PcbProductionEscapeDeveloperVerifier {
    private static final int EXPECTED_VARIANT_COUNT = 13;
    private static final int EXPECTED_NONZERO_ESCAPE_COUNT = 28;
    private static final int TARGET_X = 2000;
    private static final int TARGET_Y = 2000;
    private static final int ROUTE_EXTENSION = 240;
    private static final int SINK_ESCAPE_LENGTH = 40;
    private static final String COURTYARD_FAILURE =
        "PCB trace passes through component routing courtyard";

    private PcbProductionEscapeDeveloperVerifier() { }

    static void verify() {
        PcbFootprintRegistry registry = StandardPcbFootprintProviders.createRegistry();
        Vector<EscapeCase> cases = new Vector<EscapeCase>();
        int variantCount = 0;
        int escapeCount = 0;
        for (PhysicalPackage physicalPackage : registry.getRegisteredPackages()) {
            if (physicalPackage.isDeveloperGeneric())
                continue;
            Vector<PhysicalPackage.GeometryVariant> variants =
                physicalPackage.getGeometryVariants();
            for (PhysicalPackage.GeometryVariant variant : variants) {
                variantCount++;
                PhysicalPackageGeometry geometry = variant.getGeometry();
                require(physicalPackage.acceptsGeometry(geometry) &&
                        geometry.getGeometryContractVersionValue() ==
                            PcbGeometryContractVersion.CURRENT,
                    "R-2C production variant is not current/canonical: " +
                        physicalPackage.getId() + "/" + variant.getKey());
                Vector<PhysicalPackageGeometry.Terminal> terminals = geometry.getTerminals();
                for (int terminalIndex = 0; terminalIndex < terminals.size(); terminalIndex++) {
                    PhysicalPackageGeometry.Terminal terminal = terminals.get(terminalIndex);
                    if (terminal.getEscapeLength() <= 0)
                        continue;
                    escapeCount++;
                    EscapeCase escapeCase = new EscapeCase(physicalPackage, variant,
                        geometry, terminalIndex);
                    escapeCase.plan = buildRoutePlan(escapeCase);
                    verifyPositive(escapeCase);
                    cases.add(escapeCase);
                }
            }
        }
        require(variantCount == EXPECTED_VARIANT_COUNT,
            "R-2C production escape verifier saw " + variantCount +
                " canonical variants; expected " + EXPECTED_VARIANT_COUNT);
        require(escapeCount == EXPECTED_NONZERO_ESCAPE_COUNT,
            "R-2C production escape verifier saw " + escapeCount +
                " nonzero terminal escapes; expected " + EXPECTED_NONZERO_ESCAPE_COUNT);

        for (EscapeCase escapeCase : cases)
            if (isNegativeCanaryCase(escapeCase))
                verifyNegativeCanary(escapeCase);
    }

    private static void verifyPositive(EscapeCase escapeCase) {
        EscapeFixture fixture = buildFixture(escapeCase.physicalPackage,
            escapeCase.geometry, escapeCase.plan);
        PcbComponentPlacement target = fixture.layout.getComponent("TARGET");
        require(target != null && target.getPhysicalPackage() == escapeCase.physicalPackage &&
                target.getPhysicalGeometry() == escapeCase.geometry &&
                escapeCase.physicalPackage.acceptsGeometry(target.getPhysicalGeometry()) &&
                escapeCase.variant.getKey().equals(target.getGeometryVariantKey()) &&
                escapeCase.variant.getTransformKey().equals(target.getGeometryTransformKey()),
            "R-2C positive escape fixture lost canonical target realization: " +
                escapeCase.description());
        verifyRoutes(escapeCase, fixture);
        fixture.layout.validateGeometry(fixture.board);
    }

    private static void verifyRoutes(EscapeCase escapeCase, EscapeFixture fixture) {
        Vector<PcbTraceGeometry> traces = fixture.layout.getTraces();
        require(traces.size() == escapeCase.plan.routes.size(),
            "R-2C escape fixture has the wrong route count: " + escapeCase.description());
        for (RouteSpec route : escapeCase.plan.routes) {
            PcbPadPlacement targetPad = fixture.layout.getPad(route.targetPadId);
            PcbPadPlacement sinkPad = fixture.layout.getPad(route.sinkPadId);
            require(targetPad != null && sinkPad != null,
                "R-2C escape fixture is missing a route pad: " + escapeCase.description());
            PcbTraceGeometry trace = traces.get(route.terminalIndex);
            int[] x = trace.getXPoints();
            int[] y = trace.getYPoints();
            PhysicalPackageGeometry.Terminal terminal = escapeCase.geometry.getTerminal(
                route.terminalIndex);
            require(trace.getNetId().equals(route.netId) &&
                    route.targetPadId.equals(trace.getStartPadId()) &&
                    route.sinkPadId.equals(trace.getEndPadId()) && x.length == 2 &&
                    x[0] == targetPad.getX() && y[0] == targetPad.getY() &&
                    x[1] == sinkPad.getX() && y[1] == sinkPad.getY(),
                "R-2C escape route does not retain exact pad endpoints: " +
                    escapeCase.description());
            int deltaX = x[1] - x[0];
            int deltaY = y[1] - y[0];
            require((deltaX == 0) != (deltaY == 0) &&
                    sign(deltaX) == terminal.getEscapeDx() &&
                    sign(deltaY) == terminal.getEscapeDy() &&
                    abs(deltaX) + abs(deltaY) > terminal.getEscapeLength(),
                "R-2C escape route does not pass beyond its declared escape: " +
                    escapeCase.description() + "/" + terminal.getTerminalId());
        }
    }

    private static void verifyNegativeCanary(EscapeCase escapeCase) {
        PhysicalPackageGeometry shortenedGeometry = shortenOneEscape(escapeCase.geometry,
            escapeCase.terminalIndex);
        PhysicalPackage shortenedPackage = new PhysicalPackage(
            "TASK43_ESCAPE_NEGATIVE_" + escapeCase.physicalPackage.getId() + "_" +
                escapeCase.variant.getKey(), shortenedGeometry.getTerminalIds(),
            new Vector<String>(), escapeCase.physicalPackage.isConnector(), shortenedGeometry);
        EscapeFixture fixture = buildFixture(shortenedPackage, shortenedGeometry,
            escapeCase.plan);
        boolean rejected = false;
        try {
            fixture.layout.validateGeometry(fixture.board);
        } catch (IllegalStateException failure) {
            if (!messageContains(failure, COURTYARD_FAILURE))
                throw new IllegalStateException(
                    "R-2C negative escape canary failed for the wrong reason: " +
                        escapeCase.description() + ": " + failure.getMessage());
            rejected = true;
        }
        require(rejected,
            "R-2C shortened escape was accepted: " + escapeCase.description());

        PhysicalPackageGeometry.Terminal original = escapeCase.geometry.getTerminal(
            escapeCase.terminalIndex);
        PhysicalPackageGeometry.Terminal shortened = shortenedGeometry.getTerminal(
            escapeCase.terminalIndex);
        require(shortened.getEscapeLength() == original.getEscapeLength() - 1,
            "R-2C negative canary did not shorten exactly one target escape: " +
                escapeCase.description());
        Vector<PhysicalPackageGeometry.Terminal> originalTerminals =
            escapeCase.geometry.getTerminals();
        Vector<PhysicalPackageGeometry.Terminal> shortenedTerminals =
            shortenedGeometry.getTerminals();
        for (int index = 0; index < originalTerminals.size(); index++) {
            if (index == escapeCase.terminalIndex)
                continue;
            require(sameEscape(originalTerminals.get(index), shortenedTerminals.get(index)),
                "R-2C negative canary changed a non-target escape: " +
                    escapeCase.description());
        }
    }

    private static EscapeRoutePlan buildRoutePlan(EscapeCase escapeCase) {
        EscapeRoutePlan plan = new EscapeRoutePlan(TARGET_X, TARGET_Y);
        PhysicalPackageGeometry.Placement placed = escapeCase.geometry.placedAt(TARGET_X,
            TARGET_Y);
        Vector<PhysicalPackageGeometry.Terminal> terminals = escapeCase.geometry.getTerminals();
        for (int terminalIndex = 0; terminalIndex < terminals.size(); terminalIndex++) {
            PhysicalPackageGeometry.Terminal terminal = terminals.get(terminalIndex);
            require(terminal.getEscapeLength() > 0 &&
                    abs(terminal.getEscapeDx()) + abs(terminal.getEscapeDy()) == 1,
                "R-2C production escape is not a positive cardinal vector: " +
                    escapeCase.description() + "/" + terminal.getTerminalId());
            Point targetPad = placed.getPadPoint(terminalIndex);
            int distance = terminal.getEscapeLength() + ROUTE_EXTENSION;
            Point sinkPad = new Point(targetPad.x + terminal.getEscapeDx() * distance,
                targetPad.y + terminal.getEscapeDy() * distance);
            PhysicalPackage sinkPackage = sinkPackageFor(-terminal.getEscapeDx(),
                -terminal.getEscapeDy(), "TASK43_ESCAPE_SINK_" + escapeCase.packageId() +
                    "_" + terminalIndex);
            Point sinkLocalPad = sinkPackage.getGeometry().getTerminal(0).getPadCenter();
            int sinkX = sinkPad.x - sinkLocalPad.x;
            int sinkY = sinkPad.y - sinkLocalPad.y;
            plan.routes.add(new RouteSpec(terminalIndex, "ESCAPE_NET_" + terminalIndex,
                "TARGET." + terminal.getTerminalId(), "SINK" + terminalIndex,
                "SINK" + terminalIndex + ".1", sinkPackage, sinkX, sinkY));
        }
        return plan;
    }

    private static EscapeFixture buildFixture(PhysicalPackage targetPackage,
            PhysicalPackageGeometry targetGeometry, EscapeRoutePlan plan) {
        TroubleshootBoard board = new TroubleshootBoard("TASK43_R2C_ESCAPE_BOARD");
        for (RouteSpec route : plan.routes)
            board.addNet(new BoardNet(route.netId));
        BoardComponent target = new BoardComponent("TARGET", "ESCAPE_TARGET", targetPackage);
        board.addComponent(target);
        Vector<PhysicalPackageGeometry.Terminal> terminals = targetGeometry.getTerminals();
        for (int index = 0; index < terminals.size(); index++) {
            PhysicalPackageGeometry.Terminal terminal = terminals.get(index);
            RouteSpec route = plan.routes.get(index);
            board.addPad(new BoardPad(route.targetPadId, "TARGET", terminal.getTerminalId(),
                route.netId));
        }
        for (RouteSpec route : plan.routes) {
            BoardComponent sink = new BoardComponent(route.sinkId, "ESCAPE_SINK",
                route.sinkPackage);
            board.addComponent(sink);
            board.addPad(new BoardPad(route.sinkPadId, route.sinkId, "1", route.netId));
        }
        board.validate();

        PcbBoardLayout layout = new PcbBoardLayout(5000, 5000,
            new Rectangle(0, 0, 4500, 4500), new Rectangle(4600, 0, 300, 500));
        PcbFootprint targetFootprint = PcbFootprint.fromPhysicalPackage(target,
            plan.targetX, plan.targetY, targetGeometry);
        layout.addComponent(targetFootprint.getPlacement());
        for (PcbPadPlacement pad : targetFootprint.getPads())
            layout.addPad(pad);
        for (RouteSpec route : plan.routes) {
            PcbFootprint sinkFootprint = PcbFootprint.fromPhysicalPackage(
                board.getComponent(route.sinkId), route.sinkX, route.sinkY,
                route.sinkPackage.getGeometry());
            layout.addComponent(sinkFootprint.getPlacement());
            for (PcbPadPlacement pad : sinkFootprint.getPads())
                layout.addPad(pad);
        }
        addLabel(layout, "board-title", "R2C ESCAPES", 20, 20, 100, 14);
        addLabel(layout, "component:TARGET", "TARGET", 20, 50, 100, 14);
        for (int index = 0; index < plan.routes.size(); index++)
            addLabel(layout, "component:SINK" + index, "SINK" + index, 20,
                80 + index * 20, 80, 14);
        for (RouteSpec route : plan.routes) {
            PcbPadPlacement targetPad = layout.getPad(route.targetPadId);
            PcbPadPlacement sinkPad = layout.getPad(route.sinkPadId);
            layout.addTrace(new PcbTraceGeometry(route.netId, route.targetPadId,
                route.sinkPadId, new int[] { targetPad.getX(), sinkPad.getX() },
                new int[] { targetPad.getY(), sinkPad.getY() }));
        }
        return new EscapeFixture(board, layout);
    }

    private static PhysicalPackageGeometry shortenOneEscape(PhysicalPackageGeometry source,
            int targetTerminalIndex) {
        Vector<PhysicalPackageGeometry.Terminal> terminals = source.getTerminals();
        Vector<PhysicalPackageGeometry.Terminal> shortened =
            new Vector<PhysicalPackageGeometry.Terminal>();
        for (int index = 0; index < terminals.size(); index++) {
            PhysicalPackageGeometry.Terminal terminal = terminals.get(index);
            int escapeLength = terminal.getEscapeLength() -
                (index == targetTerminalIndex ? 1 : 0);
            shortened.add(new PhysicalPackageGeometry.Terminal(terminal.getTerminalId(),
                terminal.getPadCenter(), terminal.getPadBounds(),
                terminal.getBoardPadProbeCenter(), terminal.getBoardPadProbeBounds(),
                terminal.getConnectedLead(), terminal.getLiftedLead(), terminal.getEscapeDx(),
                terminal.getEscapeDy(), escapeLength));
        }
        return new PhysicalPackageGeometry(source.getWidth(), source.getHeight(), shortened,
            source.getBodyBounds(), source.getBodyKeepOut(), source.getRoutingCourtyard(),
            source.getSelectionEnvelope(), source.getDragEnvelope(),
            new PcbGeometryContractVersion(PcbGeometryContractVersion.CURRENT));
    }

    private static PhysicalPackage sinkPackageFor(int escapeDx, int escapeDy, String id) {
        require(abs(escapeDx) + abs(escapeDy) == 1,
            "R-2C sink escape is not cardinal: " + id);
        int width = 70;
        int height = 70;
        Point pad;
        Point body;
        Point lifted;
        Rectangle bodyBounds;
        Rectangle keepOut;
        Rectangle courtyard;
        if (escapeDx != 0) {
            pad = new Point(escapeDx < 0 ? 20 : 50, 35);
            body = new Point(escapeDx < 0 ? 50 : 20, 35);
            lifted = new Point(escapeDx < 0 ? 42 : 28, 35);
            bodyBounds = new Rectangle(escapeDx < 0 ? 40 : 10, 27, 20, 16);
            keepOut = new Rectangle(escapeDx < 0 ? 35 : 5, 22, 30, 26);
            courtyard = new Rectangle(2, 18, 66, 34);
        } else {
            pad = new Point(35, escapeDy < 0 ? 20 : 50);
            body = new Point(35, escapeDy < 0 ? 50 : 20);
            lifted = new Point(35, escapeDy < 0 ? 42 : 28);
            bodyBounds = new Rectangle(27, escapeDy < 0 ? 40 : 10, 16, 20);
            keepOut = new Rectangle(22, escapeDy < 0 ? 35 : 5, 26, 30);
            courtyard = new Rectangle(18, 2, 34, 66);
        }
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(new PhysicalPackageGeometry.Terminal("1", pad, centered(pad, 12), pad,
            centered(pad, 18), lead(pad, body, body), lead(lifted, body, lifted), escapeDx,
            escapeDy, SINK_ESCAPE_LENGTH));
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(width, height, terminals,
            bodyBounds, keepOut, courtyard, new Rectangle(1, 1, 68, 68),
            new Rectangle(0, 0, width, height),
            new PcbGeometryContractVersion(PcbGeometryContractVersion.CURRENT));
        return new PhysicalPackage(id, vector("1"), new Vector<String>(), false, geometry);
    }

    private static boolean isNegativeCanaryCase(EscapeCase escapeCase) {
        String packageId = escapeCase.physicalPackage.getId();
        String variantKey = escapeCase.variant.getKey();
        String terminalId = escapeCase.geometry.getTerminal(escapeCase.terminalIndex)
            .getTerminalId();
        return "DEFAULT".equals(variantKey) &&
            (("TO92_NPN".equals(packageId) && "C".equals(terminalId)) ||
             ("TO92_NMOS".equals(packageId) && "D".equals(terminalId)) ||
             ("RADIAL_CERAMIC_CAPACITOR".equals(packageId) && "1".equals(terminalId)) ||
             ("THROUGH_HOLE_OUTPUT_HEADER_2".equals(packageId) &&
                 "1".equals(terminalId)));
    }

    private static boolean sameEscape(PhysicalPackageGeometry.Terminal first,
            PhysicalPackageGeometry.Terminal second) {
        return first.getEscapeDx() == second.getEscapeDx() &&
            first.getEscapeDy() == second.getEscapeDy() &&
            first.getEscapeLength() == second.getEscapeLength();
    }

    private static PhysicalPackageGeometry.Lead lead(Point endPoint, Point bodyPoint,
            Point probeCenter) {
        int left = Math.min(endPoint.x, bodyPoint.x) - 3;
        int top = Math.min(endPoint.y, bodyPoint.y) - 3;
        int right = Math.max(endPoint.x, bodyPoint.x) + 3;
        int bottom = Math.max(endPoint.y, bodyPoint.y) + 3;
        return new PhysicalPackageGeometry.Lead(endPoint, bodyPoint,
            new Rectangle(left, top, right - left, bottom - top), probeCenter,
            centered(probeCenter, 8));
    }

    private static Rectangle centered(Point point, int size) {
        return new Rectangle(point.x - size / 2, point.y - size / 2, size, size);
    }

    private static void addLabel(PcbBoardLayout layout, String id, String text, int x, int y,
            int width, int height) {
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text,
            new Rectangle(x, y, width, height), 12, false, null));
    }

    private static int sign(int value) {
        return value < 0 ? -1 : value > 0 ? 1 : 0;
    }

    private static int abs(int value) {
        return value < 0 ? -value : value;
    }

    private static boolean messageContains(Throwable failure, String expected) {
        return failure != null && failure.getMessage() != null &&
            failure.getMessage().indexOf(expected) >= 0;
    }

    private static Vector<String> vector(String... values) {
        Vector<String> result = new Vector<String>();
        for (String value : values)
            result.add(value);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }

    private static final class EscapeCase {
        final PhysicalPackage physicalPackage;
        final PhysicalPackage.GeometryVariant variant;
        final PhysicalPackageGeometry geometry;
        final int terminalIndex;
        EscapeRoutePlan plan;

        EscapeCase(PhysicalPackage physicalPackage, PhysicalPackage.GeometryVariant variant,
                PhysicalPackageGeometry geometry, int terminalIndex) {
            this.physicalPackage = physicalPackage;
            this.variant = variant;
            this.geometry = geometry;
            this.terminalIndex = terminalIndex;
        }

        String packageId() {
            return physicalPackage.getId() + "_" + variant.getKey();
        }

        String description() {
            return packageId() + "/" + geometry.getTerminal(terminalIndex).getTerminalId();
        }
    }

    private static final class EscapeRoutePlan {
        final int targetX;
        final int targetY;
        final Vector<RouteSpec> routes = new Vector<RouteSpec>();

        EscapeRoutePlan(int targetX, int targetY) {
            this.targetX = targetX;
            this.targetY = targetY;
        }
    }

    private static final class RouteSpec {
        final int terminalIndex;
        final String netId;
        final String targetPadId;
        final String sinkId;
        final String sinkPadId;
        final PhysicalPackage sinkPackage;
        final int sinkX;
        final int sinkY;

        RouteSpec(int terminalIndex, String netId, String targetPadId, String sinkId,
                String sinkPadId, PhysicalPackage sinkPackage, int sinkX, int sinkY) {
            this.terminalIndex = terminalIndex;
            this.netId = netId;
            this.targetPadId = targetPadId;
            this.sinkId = sinkId;
            this.sinkPadId = sinkPadId;
            this.sinkPackage = sinkPackage;
            this.sinkX = sinkX;
            this.sinkY = sinkY;
        }
    }

    private static final class EscapeFixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;

        EscapeFixture(TroubleshootBoard board, PcbBoardLayout layout) {
            this.board = board;
            this.layout = layout;
        }
    }
}
