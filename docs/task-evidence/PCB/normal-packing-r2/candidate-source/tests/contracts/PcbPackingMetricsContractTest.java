package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/** Literal and independent-oracle contracts for drawing-space packing metrics. */
public final class PcbPackingMetricsContractTest {
    private static int assertions;
    private static int fixtures;
    private static final Vector<String> fixtureNames = new Vector<String>();

    private PcbPackingMetricsContractTest() { }

    public static void main(String[] args) {
        histogramLiteralCases();
        literalRealLedGeometry();
        nonAlphabeticTerminalOrder();
        actualCopperViaAndSilkscreen();
        declaredIsolationSampling();
        strictMetricComparator();
        unsupportedGridAndInvalidGeometry();
        cancellationAndScopeCleanup();
        System.out.println("PASS: PCB packing metrics contracts assertions=" + assertions +
            " fixtures=" + fixtures);
    }

    private static void histogramLiteralCases() {
        check(PcbPackingMetrics.largestEmptyRectangleCells(
                new boolean[] { false, false, false, false, false, false }, 3, 2) == 6,
            "all-free literal histogram has six cells");
        check(PcbPackingMetrics.largestEmptyRectangleCells(
                new boolean[] { true, true, true, true }, 2, 2) == 0,
            "all-blocked literal histogram has no empty rectangle");
        check(PcbPackingMetrics.largestEmptyRectangleCells(new boolean[] {
                false, false, false,
                false, true, true,
                false, true, true
            }, 3, 3) == 3,
            "literal L-shaped free cells have a three-cell maximum rectangle");
        expectIllegalArgument(new Runnable() {
            public void run() {
                PcbPackingMetrics.largestEmptyRectangleCells(new boolean[3], 2, 2);
            }
        }, "histogram rejects a cell-count mismatch");
        expectIllegalArgument(new Runnable() {
            public void run() {
                PcbPackingMetrics.largestEmptyRectangleCells(null, 1, 1);
            }
        }, "histogram rejects a missing occupancy array");
        namedFixture("histogramLiteralCases");
    }

    private static void literalRealLedGeometry() {
        Fixture fixture = singleLedFixture(300, 300);
        String before = fixture.layout.geometryFingerprint();
        PcbPackingMetrics metrics = PcbPackingMetrics.measure(fixture.board, fixture.layout);
        Rectangle envelope = PcbPlacementPlanner.envelope(new PcbFootprint(
            fixture.layout.getComponent("LED"), fixture.layout.getPads()), 20);
        check(envelope.x == 81 && envelope.y == 64 && envelope.width == 123 &&
                envelope.height == 171,
            "declared LED selection, courtyard, escapes and access give literal envelope");
        int oracle = PcbFunctionalGroupingContractTest.reportWhitespace(
            fixture.board, fixture.layout, 123L, 1);
        check(metrics.area == 90000L && metrics.copper == 0L && metrics.vias == 0,
            "real declared LED fixture has literal 300x300 area and no copper or vias");
        check(metrics.protectedVoid == 24000,
            "columns8..21 and rows6..24 occupied leave an eight-by-thirty free strip");
        check(metrics.protectedVoid == oracle,
            "production sampler matches the unchanged independent LED sampler");
        check(before.equals(fixture.layout.geometryFingerprint()),
            "metric sampling leaves real package and layout geometry unchanged");
        namedFixture("literalRealLedGeometry");
    }

    private static void nonAlphabeticTerminalOrder() {
        Fixture nmos = to92Fixture(true), npn = to92Fixture(false);
        Vector<String> order = nmos.board.getComponent("Q1").getPadIds();
        check(order.size() == 3 && "Q1.G".equals(order.get(0)) &&
                "Q1.D".equals(order.get(1)) && "Q1.S".equals(order.get(2)),
            "real NMOS declared terminal order is G,D,S and must not be sorted");
        String before = nmos.layout.geometryFingerprint();
        PcbPackingMetrics measured = PcbPackingMetrics.measure(nmos.board, nmos.layout);
        PcbPackingMetrics renamed = PcbPackingMetrics.measure(npn.board, npn.layout);
        check(measured.area == 90000L && measured.copper == 0 && measured.vias == 0,
            "literal single TO92 fixture has 300x300 area and no copper or vias");
        check(measured.protectedVoid == renamed.protectedVoid,
            "identical declared TO92 geometry has equal occupancy under GDS/BCE terminal renaming");
        check(before.equals(nmos.layout.geometryFingerprint()) &&
                order.equals(nmos.board.getComponent("Q1").getPadIds()),
            "metric reconstruction preserves final geometry and authoritative terminal identities");
        namedFixture("nonAlphabeticTerminalOrder");
    }

    private static Fixture to92Fixture(boolean nmos) {
        TroubleshootBoard board = new TroubleshootBoard("packing-metric-to92");
        board.addComponent(new BoardComponent("Q1", nmos ? "NMOS" : "NPN",
            nmos ? PhysicalPackages.TO92_NMOS : PhysicalPackages.TO92_NPN));
        String[] terminals = nmos ? new String[] {"G", "D", "S"} :
            new String[] {"B", "C", "E"};
        for (int i = 0; i < terminals.length; i++) {
            String net = "NET_" + i;
            board.addNet(new BoardNet(net));
            board.addPad(new BoardPad("Q1." + terminals[i], "Q1", terminals[i], net));
        }
        board.setPlacementConstraints(constraints(board,
            new Vector<PcbPlacementConstraints.Barrier>(), PcbTraceWidthProfile.ROLE_WIDTHS));
        PcbBoardLayout layout = layout(300, 300);
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(board.getComponent("Q1"), 100, 100);
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
        return new Fixture(board, layout);
    }

    private static void actualCopperViaAndSilkscreen() {
        Fixture fixture = singleLedFixture(300, 300);
        fixture.layout.addTrace(new PcbTraceGeometry("route/top", "LED_A", null, null,
            PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
            new int[] { 10, 80 }, new int[] { 260, 260 }, 13));
        fixture.layout.addTrace(new PcbTraceGeometry("route/bottom", "LED_K", null, null,
            PcbCopperLayer.BOTTOM, PcbCopperAccess.Exposure.EXPOSED,
            new int[] { 220, 280 }, new int[] { 260, 260 }, 9));
        fixture.layout.addHole(new PcbBoardHole("via/one", PcbBoardHole.Kind.VIA,
            "LED_A", 250, 150, 4, 12, PcbCopperLayer.TOP, PcbCopperLayer.BOTTOM,
            PcbCopperAccess.Exposure.EXPOSED));
        fixture.layout.addHole(new PcbBoardHole("mount/one", PcbBoardHole.Kind.NON_PLATED,
            null, 40, 150, 5, 5, null, null, PcbCopperAccess.Exposure.EXPOSED));
        fixture.layout.addSilkscreenLabel(new PcbSilkscreenLabel("label/one", "LED",
            new Rectangle(20, 20, 40, 10), 10, false, null));

        PcbPackingMetrics metrics = PcbPackingMetrics.measure(fixture.board, fixture.layout);
        int oracle = PcbFunctionalGroupingContractTest.reportWhitespace(
            fixture.board, fixture.layout, 124L, 1);
        check(metrics.area == 90000L && metrics.copper == 130L && metrics.vias == 1,
            "actual top/bottom trace geometry contributes 70+60 copper units and one via");
        check(metrics.protectedVoid == oracle,
            "full-width two-face strokes, both hole kinds, and label match independent sampling");
        namedFixture("actualCopperViaAndSilkscreen");
    }

    private static void declaredIsolationSampling() {
        Fixture isolated = dualLedFixture(true);
        Fixture open = dualLedFixture(false);
        PcbPackingMetrics isolatedMetrics = PcbPackingMetrics.measure(
            isolated.board, isolated.layout);
        PcbPackingMetrics openMetrics = PcbPackingMetrics.measure(open.board, open.layout);
        int isolatedOracle = PcbFunctionalGroupingContractTest.reportWhitespace(
            isolated.board, isolated.layout, 125L, 1);
        int openOracle = PcbFunctionalGroupingContractTest.reportWhitespace(
            open.board, open.layout, 126L, 1);
        check(isolatedMetrics.area == 120000L && isolatedMetrics.copper == 0L &&
                isolatedMetrics.vias == 0,
            "two real package domains keep literal base metrics");
        check(isolatedMetrics.protectedVoid == isolatedOracle &&
                openMetrics.protectedVoid == openOracle,
            "declared isolation and no-barrier layouts match the independent sampler");
        Rectangle first = isolated.board.getPlacementConstraints().domainCourtyards(
            isolated.layout.getComponent("LEFT_LED")).get("left-domain");
        Rectangle second = isolated.board.getPlacementConstraints().domainCourtyards(
            isolated.layout.getComponent("RIGHT_LED")).get("right-domain");
        check(first.x == 56 && first.width == 78 && second.x == 496 && second.width == 78,
            "literal domain courtyards put the isolation midpoint at315");
        check(isolatedMetrics.protectedVoid == 20000 && openMetrics.protectedVoid == 60000,
            "barrier columns27..36 split the thirty-by-twenty free strip into ten-by-twenty strips");
        namedFixture("declaredIsolationSampling");
    }

    private static void strictMetricComparator() {
        PcbPackingMetrics baseline = new PcbPackingMetrics(1000L, 200L, 3, 500);
        check(PcbPackingMetrics.strictlyImproves(baseline,
                new PcbPackingMetrics(900L, 200L, 3, 500)),
            "strict area reduction admits equal copper, via, and protected-void metrics");
        check(!PcbPackingMetrics.strictlyImproves(baseline,
                new PcbPackingMetrics(1000L, 200L, 3, 500)),
            "area tie is not a strict improvement");
        check(!PcbPackingMetrics.strictlyImproves(baseline,
                new PcbPackingMetrics(900L, 201L, 3, 500)),
            "higher copper rejects an area reduction");
        check(!PcbPackingMetrics.strictlyImproves(baseline,
                new PcbPackingMetrics(900L, 200L, 4, 500)),
            "higher via count rejects an area reduction");
        check(!PcbPackingMetrics.strictlyImproves(baseline,
                new PcbPackingMetrics(900L, 200L, 3, 501)),
            "larger protected void rejects an area reduction");
        expectIllegalArgument(new Runnable() {
            public void run() { new PcbPackingMetrics(-1L, 0L, 0, 0); }
        }, "negative area is invalid");
        expectIllegalArgument(new Runnable() {
            public void run() { new PcbPackingMetrics(0L, 0L, 0, 0); }
        }, "zero area is invalid");
        expectIllegalArgument(new Runnable() {
            public void run() { new PcbPackingMetrics(1L, -1L, 0, 0); }
        }, "negative copper is invalid");
        expectIllegalArgument(new Runnable() {
            public void run() { new PcbPackingMetrics(1L, 0L, -1, 0); }
        }, "negative via count is invalid");
        expectIllegalArgument(new Runnable() {
            public void run() { new PcbPackingMetrics(1L, 0L, 0, -1); }
        }, "negative protected void is invalid");
        namedFixture("strictMetricComparator");
    }

    private static void unsupportedGridAndInvalidGeometry() {
        final Fixture narrow = singleLedFixture(5, 300);
        expectUnavailable(new Runnable() {
            public void run() { PcbPackingMetrics.measure(narrow.board, narrow.layout); }
        }, "a positive outline smaller than one sample cell is unavailable");
        final Fixture tooLarge = singleLedFixture(5010, 5000);
        expectUnavailable(new Runnable() {
            public void run() { PcbPackingMetrics.measure(tooLarge.board, tooLarge.layout); }
        }, "more than 250000 sample cells are unavailable");

        final Fixture valid = singleLedFixture(300, 300);
        final PcbBoardLayout missingPad = new PcbBoardLayout(500, 500,
            new Rectangle(0, 0, 300, 300), new Rectangle(320, 320, 100, 100));
        missingPad.addComponent(valid.layout.getComponent("LED"));
        boolean invalidPropagated = false;
        try {
            PcbPackingMetrics.measure(valid.board, missingPad);
        } catch (PcbPackingMetrics.Unavailable wrongClassification) {
            throw new AssertionError("invalid geometry became an unavailable-grid result",
                wrongClassification);
        } catch (IllegalStateException expected) {
            invalidPropagated = expected.getMessage().indexOf("missing pad") >= 0;
        }
        check(invalidPropagated,
            "unknown incomplete layout geometry propagates instead of becoming unavailable");
        expectIllegalArgument(new Runnable() {
            public void run() { PcbPackingMetrics.measure(null, valid.layout); }
        }, "missing measurement input is invalid rather than unavailable");
        namedFixture("unsupportedGridAndInvalidGeometry");
    }

    private static void cancellationAndScopeCleanup() {
        final PcbBoardLayout layout = new PcbBoardLayout(3300, 300,
            new Rectangle(0, 0, 3000, 10), new Rectangle(3100, 50, 100, 100));
        layout.addSilkscreenLabel(new PcbSilkscreenLabel("full-row", "x",
            new Rectangle(0, 0, 3000, 10), 10, false, null));
        final TroubleshootBoard board = new TroubleshootBoard("packing-metric-cancel");
        final CancellingServices services = new CancellingServices(4);
        final GenerationJob job = new GenerationJob(services, 100000L, 8, 5000L);
        services.owner = job;
        GenerationWorkScope.enter(job);
        boolean propagated = false;
        try {
            try {
                PcbPackingMetrics.measure(board, layout);
            } catch (GenerationJob.Cancelled expected) {
                propagated = expected.getOutcome() == GenerationJob.Outcome.CANCELLED;
            }
        } finally {
            GenerationWorkScope.exit(job);
        }
        check(propagated && services.currentChecks == 4 &&
                job.getOutcome() == GenerationJob.Outcome.CANCELLED,
            "mark-chunk cancellation propagates as the generation owner's typed cancellation");
        boolean scopeCleared = false;
        try {
            GenerationWorkScope.check();
            scopeCleared = true;
        } catch (GenerationJob.Cancelled leakedScope) {
            // A leaked cancelled owner must make the fresh checkpoint fail.
        }
        check(scopeCleared, "metric cancellation leaves no generation work owner installed");
        namedFixture("cancellationAndScopeCleanup");
    }

    private static Fixture singleLedFixture(int outlineWidth, int outlineHeight) {
        TroubleshootBoard board = new TroubleshootBoard("packing-metric-led");
        addLed(board, "LED", "LED_A", "LED_K");
        board.setPlacementConstraints(constraints(board,
            new Vector<PcbPlacementConstraints.Barrier>(), PcbTraceWidthProfile.ROLE_WIDTHS));
        PcbBoardLayout layout = layout(outlineWidth, outlineHeight);
        addPlacedLed(board, layout, "LED", 100, 100);
        return new Fixture(board, layout);
    }

    private static Fixture dualLedFixture(boolean isolationBarrier) {
        TroubleshootBoard board = new TroubleshootBoard(
            isolationBarrier ? "packing-metric-isolated" : "packing-metric-open");
        addLed(board, "LEFT_LED", "LEFT_A", "LEFT_K");
        addLed(board, "RIGHT_LED", "RIGHT_A", "RIGHT_K");
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        parts.add(new PcbPlacementConstraints.Part("LEFT_LED", "all", "All",
            "left-domain", PcbPlacementConstraints.Anchor.NONE, 20));
        parts.add(new PcbPlacementConstraints.Part("RIGHT_LED", "all", "All",
            "right-domain", PcbPlacementConstraints.Anchor.NONE, 20));
        Vector<PcbPlacementConstraints.Barrier> barriers =
            new Vector<PcbPlacementConstraints.Barrier>();
        if (isolationBarrier)
            barriers.add(new PcbPlacementConstraints.Barrier("left-domain", "right-domain", 80));
        board.setPlacementConstraints(constraints(parts, barriers,
            PcbTraceWidthProfile.ROLE_WIDTHS));
        PcbBoardLayout layout = layout(600, 200);
        addPlacedLed(board, layout, "LEFT_LED", 50, 50);
        addPlacedLed(board, layout, "RIGHT_LED", 490, 50);
        return new Fixture(board, layout);
    }

    private static void addLed(TroubleshootBoard board, String id, String anodeNet,
            String cathodeNet) {
        board.addNet(new BoardNet(anodeNet, BoardNet.RoutingRole.HIGH_CURRENT));
        board.addNet(new BoardNet(cathodeNet, BoardNet.RoutingRole.SIGNAL));
        board.addComponent(new BoardComponent(id, "LED", PhysicalPackages.THROUGH_HOLE_LED));
        board.addPad(new BoardPad(id + ".A", id, "A", anodeNet));
        board.addPad(new BoardPad(id + ".K", id, "K", cathodeNet));
    }

    private static PcbPlacementConstraints constraints(TroubleshootBoard board,
            Vector<PcbPlacementConstraints.Barrier> barriers,
            PcbTraceWidthProfile profile) {
        Vector<String> ids = board.getComponentIds();
        Collections.sort(ids);
        Vector<PcbPlacementConstraints.Part> parts =
            new Vector<PcbPlacementConstraints.Part>();
        for (String id : ids)
            parts.add(new PcbPlacementConstraints.Part(id, "all", "All", "board",
                PcbPlacementConstraints.Anchor.NONE, 20));
        return constraints(parts, barriers, profile);
    }

    private static PcbPlacementConstraints constraints(
            Vector<PcbPlacementConstraints.Part> parts,
            Vector<PcbPlacementConstraints.Barrier> barriers,
            PcbTraceWidthProfile profile) {
        return new PcbPlacementConstraints(parts, barriers, PcbCopperLayer.TOP,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, false, profile, 1);
    }

    private static PcbBoardLayout layout(int outlineWidth, int outlineHeight) {
        int canvasWidth = outlineWidth + 200;
        int canvasHeight = outlineHeight + 200;
        return new PcbBoardLayout(canvasWidth, canvasHeight,
            new Rectangle(0, 0, outlineWidth, outlineHeight),
            new Rectangle(outlineWidth + 20, outlineHeight + 20, 100, 100));
    }

    private static void addPlacedLed(TroubleshootBoard board, PcbBoardLayout layout,
            String componentId, int x, int y) {
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(
            board.getComponent(componentId), x, y);
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
    }

    private static int oracle(Fixture fixture, long seed) {
        return PcbFunctionalGroupingContractTest.reportWhitespace(
            fixture.board, fixture.layout, seed, 1);
    }

    private static void expectUnavailable(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (PcbPackingMetrics.Unavailable expected) {
            assertions++;
        }
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static void namedFixture(String name) {
        if (name == null || name.length() == 0 || fixtureNames.contains(name))
            throw new AssertionError("Named unique fixture group required: " + name);
        fixtureNames.add(name);
        fixtures++;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        Fixture(TroubleshootBoard board, PcbBoardLayout layout) {
            this.board = board;
            this.layout = layout;
        }
    }

    private static final class CancellingServices implements GenerationJob.Services {
        final int cancelAtCheck;
        int currentChecks;
        GenerationJob owner;
        CancellingServices(int cancelAtCheck) { this.cancelAtCheck = cancelAtCheck; }
        public int candidateCount() { return 1; }
        public String manifest(int candidate) { return "packing-metric-cancel"; }
        public void beginCandidate(int index) { }
        public String resolve() { return "resolved"; }
        public String healthy() { return "healthy"; }
        public String physical() { return "physical"; }
        public boolean proveNext() { return false; }
        public String symptom() { return "symptom"; }
        public String dependencies() { return "dependencies"; }
        public void publish(GenerationReceipt receipt) { }
        public void abort() { }
        public boolean isCurrent() {
            currentChecks++;
            if (currentChecks == cancelAtCheck) owner.cancel();
            return true;
        }
        public long nowMillis() { return 0L; }
    }
}
