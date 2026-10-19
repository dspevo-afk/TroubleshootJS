package com.lushprojects.circuitjs1.client;

/** Focused lifecycle contract for the COPY-only successful-route observer. */
public final class PcbSuccessfulRoutePoolObserverContractTest {
    private static int assertions;
    private static int fixtures;

    private PcbSuccessfulRoutePoolObserverContractTest() { }

    public static void main(String[] args) {
        nullObserverIsNoOp();
        observerReceivesTheCandidateSnapshotInputs();
        observerFailurePropagatesUnchanged();
        nestedObserversRestoreTheirPreviousOwner();
        System.out.println("PASS: PCB successful route pool observer contracts assertions=" +
            assertions + " fixtures=" + fixtures + " evaluator=finalizedRoutePool");
    }

    /** Emits buffered route snapshots after the existing helper's whitespace and before its receipt. */
    static void observe(long seed, int version, String caseLabel) {
        if (caseLabel == null || caseLabel.length() == 0)
            throw new IllegalArgumentException("Missing successful-route case label");
        RouteRecorder recorder = new RouteRecorder(caseLabel, seed, version);
        SeededPcbLayoutGenerator.SuccessfulRouteObserver previous =
            SeededPcbLayoutGenerator.setSuccessfulRouteObserver(recorder);
        String selected;
        try {
            selected = PcbPostSelectionEdgeTrimContractTest
                .observeGlobalPocketDescent(seed, version);
        } finally {
            SeededPcbLayoutGenerator.setSuccessfulRouteObserver(previous);
        }
        String prefix = "PCB_EDGE_TRIM_CASE ";
        if (selected == null || !selected.startsWith(prefix))
            throw new IllegalStateException("Unexpected selected route receipt prefix");
        recorder.finishAndPrint();
        System.out.println("PCB_GLOBAL_POCKET_CASE " + selected.substring(prefix.length()));
    }

    private static void nullObserverIsNoOp() {
        SeededPcbLayoutGenerator.SuccessfulRouteObserver previous =
            SeededPcbLayoutGenerator.setSuccessfulRouteObserver(null);
        try {
            check(previous == null, "standalone fixture begins without an observer");
            SeededPcbLayoutGenerator.dispatchSuccessfulRouteObserver(null, -1,
                Long.MIN_VALUE, null, false, Double.NaN, -1L, -1, false, false);
            check(SeededPcbLayoutGenerator.setSuccessfulRouteObserver(null) == null,
                "null observer dispatch does not inspect payload or install state");
        } finally {
            SeededPcbLayoutGenerator.setSuccessfulRouteObserver(previous);
        }
        fixtures++;
    }

    private static void observerReceivesTheCandidateSnapshotInputs() {
        final Fixture fixture = new Fixture();
        final CaptureObserver capture = new CaptureObserver();
        SeededPcbLayoutGenerator.SuccessfulRouteObserver previous =
            SeededPcbLayoutGenerator.setSuccessfulRouteObserver(capture);
        try {
            check(previous == null, "delivery fixture owns the empty observer slot");
            dispatch(fixture);
        } finally {
            SeededPcbLayoutGenerator.setSuccessfulRouteObserver(previous);
        }
        check(capture.calls == 1, "one dispatch produces one observer callback");
        check(capture.board == fixture.board && capture.layout == fixture.layout,
            "callback receives the original read-only candidate references");
        check(capture.attempt == 4 && capture.cheapScore == 123456L && capture.twoLayer,
            "callback receives route identity and strategy");
        check(capture.quality == 0.625 && capture.area == 9600L &&
                capture.majorSide == 120 && capture.preferCompact &&
                !capture.preferDensePacking,
            "callback receives the already-computed quality and comparator inputs");
        fixtures++;
    }

    private static void observerFailurePropagatesUnchanged() {
        final Fixture fixture = new Fixture();
        final IllegalStateException marker = new IllegalStateException("observer-marker");
        SeededPcbLayoutGenerator.SuccessfulRouteObserver throwing =
            new SeededPcbLayoutGenerator.SuccessfulRouteObserver() {
                public void onSuccessfulRoute(TroubleshootBoard board, int attempt,
                        long cheapScore, PcbBoardLayout layout, boolean twoLayer,
                        double quality, long area, int majorSide, boolean preferCompact,
                        boolean preferDensePacking) {
                    throw marker;
                }
            };
        SeededPcbLayoutGenerator.SuccessfulRouteObserver previous =
            SeededPcbLayoutGenerator.setSuccessfulRouteObserver(throwing);
        boolean propagated = false;
        try {
            dispatch(fixture);
        } catch (IllegalStateException expected) {
            propagated = expected == marker;
        } finally {
            SeededPcbLayoutGenerator.setSuccessfulRouteObserver(previous);
        }
        check(propagated, "observer exception propagates without conversion");
        check(SeededPcbLayoutGenerator.setSuccessfulRouteObserver(null) == null,
            "throwing observer is removed by owner cleanup");
        fixtures++;
    }

    private static void nestedObserversRestoreTheirPreviousOwner() {
        final Fixture fixture = new Fixture();
        final CaptureObserver outer = new CaptureObserver();
        final CaptureObserver inner = new CaptureObserver();
        SeededPcbLayoutGenerator.SuccessfulRouteObserver previousOuter =
            SeededPcbLayoutGenerator.setSuccessfulRouteObserver(outer);
        try {
            check(previousOuter == null, "outer observer starts from the empty slot");
            SeededPcbLayoutGenerator.SuccessfulRouteObserver previousInner =
                SeededPcbLayoutGenerator.setSuccessfulRouteObserver(inner);
            try {
                check(previousInner == outer, "inner installation returns its outer owner");
                dispatch(fixture);
                check(inner.calls == 1 && outer.calls == 0,
                    "inner observer exclusively receives nested dispatch");
            } finally {
                check(SeededPcbLayoutGenerator.setSuccessfulRouteObserver(previousInner) == inner,
                    "inner cleanup restores and returns the inner observer");
            }
            dispatch(fixture);
            check(outer.calls == 1 && inner.calls == 1,
                "dispatch after nested cleanup reaches the restored outer observer");
        } finally {
            check(SeededPcbLayoutGenerator.setSuccessfulRouteObserver(previousOuter) == outer,
                "outer cleanup restores the original observer");
        }
        fixtures++;
    }

    private static void dispatch(Fixture fixture) {
        SeededPcbLayoutGenerator.dispatchSuccessfulRouteObserver(fixture.board, 4,
            123456L, fixture.layout, true, 0.625, 9600L, 120, true, false);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;

        Fixture() {
            board = Rb56Plan.resolve(2L, 22).board();
            layout = new PcbBoardLayout(500, 500,
                new Rectangle(40, 40, 120, 80), new Rectangle(300, 40, 80, 80));
        }
    }

    private static final class CaptureObserver implements
            SeededPcbLayoutGenerator.SuccessfulRouteObserver {
        int calls;
        TroubleshootBoard board;
        PcbBoardLayout layout;
        int attempt;
        long cheapScore;
        boolean twoLayer;
        double quality;
        long area;
        int majorSide;
        boolean preferCompact;
        boolean preferDensePacking;

        public void onSuccessfulRoute(TroubleshootBoard board, int attempt,
                long cheapScore, PcbBoardLayout layout, boolean twoLayer, double quality,
                long area, int majorSide, boolean preferCompact, boolean preferDensePacking) {
            calls++;
            this.board = board;
            this.attempt = attempt;
            this.cheapScore = cheapScore;
            this.layout = layout;
            this.twoLayer = twoLayer;
            this.quality = quality;
            this.area = area;
            this.majorSide = majorSide;
            this.preferCompact = preferCompact;
            this.preferDensePacking = preferDensePacking;
        }
    }

    private static final class RouteRecorder implements
            SeededPcbLayoutGenerator.SuccessfulRouteObserver {
        private final String caseLabel;
        private final long seed;
        private final int version;
        private final java.util.Vector<RouteSnapshot> snapshots =
            new java.util.Vector<RouteSnapshot>();
        private TroubleshootBoard sourceBoard;
        private String sourceGraphBeforeSha256;

        RouteRecorder(String caseLabel, long seed, int version) {
            this.caseLabel = caseLabel;
            this.seed = seed;
            this.version = version;
        }

        public void onSuccessfulRoute(TroubleshootBoard board, int attempt,
                long cheapScore, PcbBoardLayout layout, boolean twoLayer, double quality,
                long area, int majorSide, boolean preferCompact, boolean preferDensePacking) {
            if (board == null || layout == null)
                throw new IllegalStateException("Successful route callback omitted its owner");
            String graph = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
            if (sourceBoard == null) {
                sourceBoard = board;
                sourceGraphBeforeSha256 = graph;
            } else if (sourceBoard != board || !sourceGraphBeforeSha256.equals(graph)) {
                throw new IllegalStateException("Board graph changed during route-pool observation");
            }
            Rectangle outline = layout.getBoardOutline();
            Rectangle occupied = layout.getOccupiedContentBounds();
            if (occupied == null)
                throw new IllegalStateException("Successful route omitted occupied bounds");
            if (area != (long) outline.width * outline.height ||
                    majorSide != Math.max(outline.width, outline.height))
                throw new IllegalStateException("Comparator geometry disagrees with route outline");
            PcbRouteMetrics metrics = PcbRouteMetrics.measure(layout.getTraces());
            snapshots.add(new RouteSnapshot(caseLabel, snapshots.size(), seed, version,
                twoLayer ? MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER :
                    MediumBoardPhysicalPolicy.P05_ONE_FACE,
                attempt, cheapScore, routeQualityMilli(quality), area, majorSide,
                preferCompact, preferDensePacking, outline, occupied, metrics.length,
                viaCount(layout), layout.getTraces().size(),
                PlayerSessionFingerprint.of(layout.geometryFingerprint()),
                layout.componentGeometryFingerprint(), layout.traceGeometryFingerprint()));
        }

        void finishAndPrint() {
            if (snapshots.isEmpty() || sourceBoard == null || sourceGraphBeforeSha256 == null)
                throw new IllegalStateException("Accepted case produced no successful route callbacks");
            String sourceGraphAfterSha256 =
                PcbCanvasBoundaryContractTest.liveGraphSha256(sourceBoard);
            if (!sourceGraphBeforeSha256.equals(sourceGraphAfterSha256))
                throw new IllegalStateException("Board source graph changed during route-pool observation");
            for (RouteSnapshot snapshot : snapshots)
                System.out.println("PCB_SUCCESSFUL_ROUTE_POOL " +
                    snapshot.toJson(sourceGraphBeforeSha256, sourceGraphAfterSha256));
        }
    }

    private static final class RouteSnapshot {
        final String caseLabel;
        final int routeIndex;
        final long seed;
        final int version;
        final String strategy;
        final int attempt;
        final long placementCheapScore;
        final long routeQualityMilli;
        final long area;
        final int majorSide;
        final boolean preferCompact;
        final boolean preferDensePacking;
        final Rectangle rawOutline;
        final Rectangle occupiedBounds;
        final long copperManhattanLength;
        final int vias;
        final int traceCount;
        final String geometrySha256;
        final String componentGeometryFingerprint;
        final String traceGeometryFingerprint;

        RouteSnapshot(String caseLabel, int routeIndex, long seed, int version,
                String strategy, int attempt, long placementCheapScore,
                long routeQualityMilli, long area, int majorSide, boolean preferCompact,
                boolean preferDensePacking, Rectangle rawOutline, Rectangle occupiedBounds,
                long copperManhattanLength, int vias, int traceCount, String geometrySha256,
                String componentGeometryFingerprint, String traceGeometryFingerprint) {
            this.caseLabel = caseLabel;
            this.routeIndex = routeIndex;
            this.seed = seed;
            this.version = version;
            this.strategy = strategy;
            this.attempt = attempt;
            this.placementCheapScore = placementCheapScore;
            this.routeQualityMilli = routeQualityMilli;
            this.area = area;
            this.majorSide = majorSide;
            this.preferCompact = preferCompact;
            this.preferDensePacking = preferDensePacking;
            this.rawOutline = new Rectangle(rawOutline);
            this.occupiedBounds = new Rectangle(occupiedBounds);
            this.copperManhattanLength = copperManhattanLength;
            this.vias = vias;
            this.traceCount = traceCount;
            this.geometrySha256 = geometrySha256;
            this.componentGeometryFingerprint = componentGeometryFingerprint;
            this.traceGeometryFingerprint = traceGeometryFingerprint;
        }

        String toJson(String sourceGraphBeforeSha256, String sourceGraphAfterSha256) {
            StringBuilder json = new StringBuilder(2048 + componentGeometryFingerprint.length() +
                traceGeometryFingerprint.length());
            json.append("{\"caseLabel\":"); appendQuoted(json, caseLabel);
            json.append(",\"routeIndex\":").append(routeIndex);
            json.append(",\"seed\":"); appendQuoted(json, Long.toString(seed));
            json.append(",\"version\":").append(version);
            json.append(",\"strategy\":"); appendQuoted(json, strategy);
            json.append(",\"attempt\":").append(attempt);
            json.append(",\"phase\":\"FINALIZED_ROUTE_CANDIDATE\"");
            json.append(",\"placementCheapScore\":").append(placementCheapScore);
            json.append(",\"routeQualityMilli\":").append(routeQualityMilli);
            json.append(",\"area\":").append(area);
            json.append(",\"majorSide\":").append(majorSide);
            json.append(",\"preferCompact\":").append(preferCompact);
            json.append(",\"preferDensePacking\":").append(preferDensePacking);
            json.append(",\"rawOutline\":"); appendRectangle(json, rawOutline);
            json.append(",\"occupiedBounds\":"); appendRectangle(json, occupiedBounds);
            json.append(",\"copperManhattanLength\":").append(copperManhattanLength);
            json.append(",\"vias\":").append(vias);
            json.append(",\"traceCount\":").append(traceCount);
            json.append(",\"geometrySha256\":"); appendQuoted(json, geometrySha256);
            json.append(",\"componentGeometryFingerprint\":");
            appendQuoted(json, componentGeometryFingerprint);
            json.append(",\"traceGeometryFingerprint\":");
            appendQuoted(json, traceGeometryFingerprint);
            json.append(",\"sourceGraphBeforeSha256\":");
            appendQuoted(json, sourceGraphBeforeSha256);
            json.append(",\"sourceGraphAfterSha256\":");
            appendQuoted(json, sourceGraphAfterSha256);
            json.append('}');
            return json.toString();
        }
    }

    private static void appendRectangle(StringBuilder json, Rectangle value) {
        json.append("{\"x\":").append(value.x);
        json.append(",\"y\":").append(value.y);
        json.append(",\"width\":").append(value.width);
        json.append(",\"height\":").append(value.height).append('}');
    }

    private static void appendQuoted(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') json.append('\\').append(character);
            else if (character == '\b') json.append("\\b");
            else if (character == '\f') json.append("\\f");
            else if (character == '\n') json.append("\\n");
            else if (character == '\r') json.append("\\r");
            else if (character == '\t') json.append("\\t");
            else if (character < 0x20) {
                String hex = Integer.toHexString(character);
                json.append("\\u");
                for (int padding = hex.length(); padding < 4; padding++) json.append('0');
                json.append(hex);
            } else json.append(character);
        }
        json.append('"');
    }

    private static int viaCount(PcbBoardLayout layout) {
        int vias = 0;
        for (PcbBoardHole hole : layout.getHoles())
            if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
        return vias;
    }

    private static long routeQualityMilli(double quality) {
        if (Double.isNaN(quality) || Double.isInfinite(quality) || quality < 0) return -1;
        double scaled = quality * 1000.0;
        if (scaled >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.round(scaled);
    }
}
