package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import java.nio.charset.Charset;
import java.security.MessageDigest;

/** Focused pure contracts for the A02 PCB bend and placement corrections. */
public final class A02GeometryContractTest {
    private static int assertions;

    private A02GeometryContractTest() { }

    public static void main(String[] args) {
        bendSemantics();
        routeQualityVersionBoundary();
        connectedPlacementTarget();
        invalidGeometryRemainsRejected();
        legacyProductionCompatibility();
        privatePlacementPath();
        generatedVersionPaths();
        System.out.println("PASS: A02GeometryContractTest " + assertions
                + " assertions");
    }

    private static void bendSemantics() {
        PcbBoardLayout layout = new PcbBoardLayout(400, 300,
                new Rectangle(20, 20, 360, 240), new Rectangle(0, 0, 1, 1),
                SeededPcbLayoutGenerator.CURRENT_VERSION);
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
        requireBends(layout, new int[] { 25, 25, 10, 10, 0 },
                new int[] { 40, 15, 15, 0, 0 }, 3, "reversed path");
        requireBends(layout, new int[] { 0, 10, 0 },
                new int[] { 0, 0, 0 }, 1, "horizontal reversal");
        requireBends(layout, new int[] { 0, 0, 0 },
                new int[] { 0, 10, 0 }, 1, "vertical reversal");
        requireBends(layout, new int[] { 0, 10, 10, 35 },
                new int[] { 0, 0, 0, 0 }, 0, "repeated point on straight path");
        requireBends(layout, new int[] { 0, 10, 10, 10, 25 },
                new int[] { 0, 0, 0, 20, 20 }, 2,
                "repeated points around a real turn");
        requireBends(layout, new int[] { Integer.MAX_VALUE - 100,
                Integer.MAX_VALUE - 1, Integer.MAX_VALUE },
                new int[] { 0, 0, 0 }, 0, "positive boundary coordinates");
        requireBends(layout, new int[] { Integer.MIN_VALUE,
                Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 100 },
                new int[] { 0, 0, 0 }, 0, "negative boundary coordinates");
        requireBends(layout, new int[] { Integer.MAX_VALUE - 100,
                Integer.MAX_VALUE - 10, Integer.MAX_VALUE - 100 },
                new int[] { 0, 0, 0 }, 1, "boundary horizontal reversal");
    }

    private static void routeQualityVersionBoundary() {
        TroubleshootBoard board = new TroubleshootBoard("A02_SCORE");
        PcbTraceGeometry trace = new PcbTraceGeometry("N",
                new int[] { 0, 10, 30 }, new int[] { 0, 0, 0 });
        PcbBoardLayout legacy = new PcbBoardLayout(400, 300,
                new Rectangle(20, 20, 360, 240), new Rectangle(0, 0, 1, 1));
        PcbBoardLayout current = new PcbBoardLayout(400, 300,
                new Rectangle(20, 20, 360, 240), new Rectangle(0, 0, 1, 1),
                SeededPcbLayoutGenerator.CURRENT_VERSION);
        legacy.addTrace(trace);
        current.addTrace(new PcbTraceGeometry("N",
                new int[] { 0, 10, 30 }, new int[] { 0, 0, 0 }));
        require(legacy.getLayoutAlgorithmVersion() == SeededPcbLayoutGenerator.LEGACY_VERSION,
                "four-argument layout preserves legacy score version");
        require(current.getLayoutAlgorithmVersion() == SeededPcbLayoutGenerator.CURRENT_VERSION,
                "explicit layout selects corrected score version");
        double legacyScore = legacy.getRouteQualityScore(board);
        double currentScore = current.getRouteQualityScore(board);
        require(Math.abs((legacyScore - currentScore) - 35.0) < 0.000001,
                "corrected straight-subdivision bend changes route score by one bend weight");
        require(current.getTraceBendCount(current.getTraces().get(0)) == 0,
                "current score path uses direction-normalized bend semantics");
        System.out.println("A02_BEND_SCORE straight-subdivision legacy=" + legacyScore +
                ";corrected=" + currentScore + ";delta=" + (currentScore - legacyScore));
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
        Point one = SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
                onePlaced, allLinks, 321, -77);
        PcbPadPlacement oneSource = prototype.getPad(connectorLink.getPadId());
        PcbPadPlacement oneWorld = connector.getPad(connectorLink.getOtherPadId());
        require(one.x == oneWorld.getX() - oneSource.getX() &&
                one.y == oneWorld.getY() - oneSource.getY(),
                "one connected neighbor solves raw origin from world minus local pad");

        Vector<PcbFootprint> twoPlaced = new Vector<PcbFootprint>();
        twoPlaced.add(connector);
        twoPlaced.add(resistor);
        Point two = SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
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
        Point fallback = SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
                noPlaced, allLinks, 321, -77);
        require(fallback.x == 321 && fallback.y == -77,
                "no placed connected neighbor uses the board-center fallback");
        Vector<TopologyPlacementGraph.PadLink> zeroOnly =
                new Vector<TopologyPlacementGraph.PadLink>();
        zeroOnly.add(new TopologyPlacementGraph.PadLink("LED1", "LED1.A",
                "missing", "missing", "N", 0.0));
        Point zeroFallback = SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
                noPlaced, zeroOnly, -19, 23);
        require(zeroFallback.x == -19 && zeroFallback.y == 23,
                "zero-weight links do not count as connected neighbors");

        verifyTranslationCovariance(prototype, allLinks, connector, 37, -29);
        verifyTranslationCovariance(prototype, allLinks, connector, -31, 43);
        verifyBoundaryTranslation(board, topology, 1);
        verifyBoundaryTranslation(board, topology, -1);

        expectRejected(new Runnable() {
            public void run() {
                Vector<TopologyPlacementGraph.PadLink> invalid =
                        new Vector<TopologyPlacementGraph.PadLink>();
                invalid.add(new TopologyPlacementGraph.PadLink("LED1", "LED1.A",
                        "J1", "J1.1", "VIN", -1.0));
                SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
                        onePlaced, invalid, 0, 0);
            }
        }, "negative placement weight");
        expectRejected(new Runnable() {
            public void run() {
                Vector<TopologyPlacementGraph.PadLink> invalid =
                        new Vector<TopologyPlacementGraph.PadLink>();
                invalid.add(new TopologyPlacementGraph.PadLink("LED1", "LED1.A",
                        "J1", "J1.1", "VIN", Double.NaN));
                SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
                        onePlaced, invalid, 0, 0);
            }
        }, "NaN placement weight");
        expectRejected(new Runnable() {
            public void run() {
                Vector<TopologyPlacementGraph.PadLink> invalid =
                        new Vector<TopologyPlacementGraph.PadLink>();
                invalid.add(new TopologyPlacementGraph.PadLink("LED1", "LED1.A",
                        "J1", "J1.1", "VIN", Double.POSITIVE_INFINITY));
                SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
                        onePlaced, invalid, 0, 0);
            }
        }, "infinite placement weight");
        expectRejected(new Runnable() {
            public void run() {
                Vector<TopologyPlacementGraph.PadLink> invalid =
                        new Vector<TopologyPlacementGraph.PadLink>();
                invalid.add(null);
                SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
                        onePlaced, invalid, 0, 0);
            }
        }, "null placement link");
        expectRejected(new Runnable() {
            public void run() {
                Vector<TopologyPlacementGraph.PadLink> huge =
                        new Vector<TopologyPlacementGraph.PadLink>();
                huge.add(new TopologyPlacementGraph.PadLink("LED1", "LED1.A",
                        "J1", "J1.1", "VIN", Double.MAX_VALUE));
                SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
                        onePlaced, huge, 0, 0);
            }
        }, "placement arithmetic overflow");
    }

    private static void verifyTranslationCovariance(PcbFootprint prototype,
            Vector<TopologyPlacementGraph.PadLink> links, PcbFootprint source,
            int dx, int dy) {
        Vector<PcbFootprint> beforePlaced = new Vector<PcbFootprint>();
        beforePlaced.add(source);
        Vector<PcbFootprint> afterPlaced = new Vector<PcbFootprint>();
        afterPlaced.add(source.translated(source.getPlacement().getX() + dx,
                source.getPlacement().getY() + dy));
        Point before = SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
                beforePlaced, links, 321, -77);
        Point after = SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
                afterPlaced, links, 321, -77);
        require(after.x - before.x == dx && after.y - before.y == dy,
                "translated connected world moves raw target exactly once");
    }

    private static void verifyBoundaryTranslation(TroubleshootBoard board,
            TopologyPlacementGraph topology, int direction) {
        PcbFootprint prototype = PcbFootprint.fromPhysicalPackage(
                board.getComponent("R1"), 0, 0);
        Vector<TopologyPlacementGraph.PadLink> links = topology.getLinksFor("R1");
        TopologyPlacementGraph.PadLink connectorLink = linkTo(links, "J1");
        int edge = direction > 0 ? Integer.MAX_VALUE - 1000 : Integer.MIN_VALUE + 1000;
        PcbFootprint connector = PcbFootprint.fromPhysicalPackage(
                board.getComponent("J1"), edge, 100);
        Vector<PcbFootprint> beforePlaced = new Vector<PcbFootprint>();
        beforePlaced.add(connector);
        int delta = direction > 0 ? -123 : 123;
        PcbFootprint moved = connector.translated(edge + delta, 100 + delta);
        Vector<PcbFootprint> afterPlaced = new Vector<PcbFootprint>();
        afterPlaced.add(moved);
        Point before = SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
                beforePlaced, links, 0, 0);
        Point after = SeededPcbLayoutGenerator.weightedConnectedTarget(prototype,
                afterPlaced, links, 0, 0);
        require(after.x - before.x == delta && after.y - before.y == delta &&
                ((direction > 0 && before.x > 0) ||
                (direction < 0 && before.x < 0)) && connectorLink.getWeight() > 0,
                "near-boundary connected translation remains exact and in range");
    }

    private static void generatedVersionPaths() {
        TroubleshootBoard board = TroubleshootBoardFixtures.createLedIndicatorBoard();
        SeededPcbLayoutGenerator legacyGenerator = new SeededPcbLayoutGenerator(
                SeededPcbLayoutGenerator.LEGACY_VERSION);
        SeededPcbLayoutGenerator currentGenerator = new SeededPcbLayoutGenerator(
                SeededPcbLayoutGenerator.CURRENT_VERSION);
        PcbBoardLayout legacy = legacyGenerator.generate(board, 0L);
        PcbBoardLayout current = currentGenerator.generate(board, 0L);
        require(legacy.getLayoutAlgorithmVersion() == SeededPcbLayoutGenerator.LEGACY_VERSION &&
                current.getLayoutAlgorithmVersion() == SeededPcbLayoutGenerator.CURRENT_VERSION,
                "production generators stamp their selected layout versions");
        require(!legacy.geometryFingerprint().equals(current.geometryFingerprint()),
                "corrected connected placement is reached by seeded production generation");
        require(current.getTraces().size() > 0 && current.getComponents().size() > 0,
                "current production generation returns routed physical geometry");
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

        final PcbBoardLayout duplicate = new SeededPcbLayoutGenerator().generate(board, 0L);
        final PcbPadPlacement duplicateStart = duplicate.getPad("J1.1");
        final PcbPadPlacement duplicateEnd = duplicate.getPad("R1.1");
        duplicate.addTrace(new PcbTraceGeometry("VIN", "J1.1", "R1.1",
                new int[] { duplicateStart.getX(), duplicateStart.getX(), duplicateEnd.getX() },
                new int[] { duplicateStart.getY(), duplicateStart.getY(), duplicateEnd.getY() }));
        expectIllegalState(new Runnable() {
            public void run() { duplicate.validateGeometry(board); }
        }, "degenerate duplicate-point route", "PCB trace segment has zero length:");
    }

    private static void legacyProductionCompatibility() {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~(sim.gridSize - 1);
        sim.gridRound = sim.gridSize / 2 - 1;
        CircuitElm.sim = sim;
        verifyLegacyRoute("LED", new LedIndicatorGenerator(
                SeededPcbLayoutGenerator.LEGACY_VERSION), 0L,
                4062.212935131351, "c20e36edeba41c7811701a0487f23459dea8eb021237cab82dfdb640df73d4e6");
        verifyLegacyRoute("LED", new LedIndicatorGenerator(
                SeededPcbLayoutGenerator.LEGACY_VERSION), 2L,
                4070.3997483181643, "e3433fcb63516dad9f0548430168c193392f6f47d70a6e703f149e88cd16aac6");
        verifyLegacyRoute("DIODE", new DiodeProtectedIndicatorGenerator(
                SeededPcbLayoutGenerator.LEGACY_VERSION), 0L,
                5049.174070430738, "94720e880f39fb716fd923d9f657979c8b22ec3820a9a5091e02281b18bac9b7");
        verifyLegacyRoute("PARALLEL", new ParallelDualIndicatorGenerator(
                SeededPcbLayoutGenerator.LEGACY_VERSION), 3L,
                6831.519925716576, "80dab4a641cca6f8fa659d102c7b2909ced1bb752d4d48bb149b47ed36c98e0c");
    }

    private static void verifyLegacyRoute(String family, Object generator, long seed,
            double score, String geometryHash) {
        try {
            GeneratedBoardInstance instance = (GeneratedBoardInstance) generator.getClass()
                    .getDeclaredMethod("generate", long.class).invoke(generator, Long.valueOf(seed));
            PcbBoardLayout layout = instance.getPcbLayout();
            require(layout.getLayoutAlgorithmVersion() == SeededPcbLayoutGenerator.LEGACY_VERSION &&
                    layout.getRouteQualityScore(instance.getBoard()) == score &&
                    geometryHash.equals(sha256(layout.geometryFingerprint())),
                    family + " legacy fingerprint and score retained");
            System.out.println("A02_LEGACY family=" + family + ";seed=" + seed +
                    ";score=" + score + ";geometrySha256=" + geometryHash);
            compareBendRanking(instance.getBoard(), seed, geometryHash, family);
        } catch (Exception failure) {
            throw new AssertionError("legacy production route failed: " + family + ": " + failure);
        }
    }

    /** Rescore the exact first five legacy viable candidates, keeping geometry fixed. */
    private static void compareBendRanking(TroubleshootBoard board, long seed,
            String expectedLegacyWinner, String family) throws Exception {
        java.lang.reflect.Method random = SeededPcbLayoutGenerator.class.getDeclaredMethod(
                "attemptRandom", long.class, int.class);
        java.lang.reflect.Method generate = SeededPcbLayoutGenerator.class.getDeclaredMethod(
                "generateAttempt", TroubleshootBoard.class, java.util.Random.class, int.class);
        random.setAccessible(true);
        generate.setAccessible(true);
        SeededPcbLayoutGenerator legacyGenerator = new SeededPcbLayoutGenerator(3);
        double oldBest = Double.POSITIVE_INFINITY, newBest = Double.POSITIVE_INFINITY;
        String oldWinner = null, newWinner = null;
        int viable = 0, changedScores = 0;
        for (int attempt = 0; attempt < 80 && viable < 5; attempt++) {
            PcbBoardLayout candidate;
            try {
                candidate = (PcbBoardLayout) generate.invoke(legacyGenerator, board,
                        random.invoke(null, seed, attempt), (int) ((seed % 4 + 4) % 4));
            } catch (java.lang.reflect.InvocationTargetException rejected) {
                if (!(rejected.getCause() instanceof RuntimeException)) throw rejected;
                continue;
            }
            PcbBoardLayout rescored = new PcbBoardLayout(candidate.getWidth(), candidate.getHeight(),
                    candidate.getBoardOutline(), candidate.getPartsTray(), 4);
            for (PcbPadPlacement pad : candidate.getPads()) rescored.addPad(pad);
            for (PcbComponentPlacement part : candidate.getComponents()) rescored.addComponent(part);
            for (PcbTraceGeometry trace : candidate.getTraces()) rescored.addTrace(trace);
            for (PcbSilkscreenLabel label : candidate.getSilkscreenLabels())
                rescored.addSilkscreenLabel(label);
            rescored.validateGeometry(board);
            require(rescored.geometryFingerprint().equals(candidate.geometryFingerprint()),
                    "bend rescoring leaves geometry unchanged");
            double oldScore = candidate.getRouteQualityScore(board);
            double newScore = rescored.getRouteQualityScore(board);
            String fingerprint = sha256(candidate.geometryFingerprint());
            if (oldScore < oldBest) { oldBest = oldScore; oldWinner = fingerprint; }
            if (newScore < newBest) { newBest = newScore; newWinner = fingerprint; }
            if (oldScore != newScore) changedScores++;
            viable++;
        }
        require(viable > 0 && expectedLegacyWinner.equals(oldWinner),
                "audited candidate set reproduces the actual legacy winner");
        System.out.println("A02_BEND_RANKING family=" + family + ";seed=" + seed +
                ";legacyViableCandidates=" + viable + ";changedScores=" + changedScores +
                ";legacyBest=" + oldBest + ";rescoredBest=" + newBest +
                ";winnerChanged=" + !oldWinner.equals(newWinner) + ";rescoredWinner=" + newWinner);
    }

    private static void privatePlacementPath() {
        try {
            final TroubleshootBoard board = TroubleshootBoardFixtures.createLedIndicatorBoard();
            final TopologyPlacementGraph topology = new TopologyPlacementGraph(board);
            final Vector<PcbFootprint> placed = new Vector<PcbFootprint>();
            placed.add(PcbFootprint.fromPhysicalPackage(board.getComponent("J1"), 100, 150));
            java.lang.reflect.Method method = SeededPcbLayoutGenerator.class.getDeclaredMethod(
                    "placeTopologyComponent", BoardComponent.class, TopologyPlacementGraph.class,
                    Rectangle.class, java.util.Random.class, Vector.class, int.class);
            method.setAccessible(true);
            PcbFootprint legacy = (PcbFootprint) method.invoke(new SeededPcbLayoutGenerator(
                    SeededPcbLayoutGenerator.LEGACY_VERSION), board.getComponent("R1"), topology,
                    new Rectangle(70, 50, 720, 400), new PlacementProbeRandom(), placed, 0);
            PcbFootprint current = (PcbFootprint) method.invoke(new SeededPcbLayoutGenerator(
                    SeededPcbLayoutGenerator.CURRENT_VERSION), board.getComponent("R1"), topology,
                    new Rectangle(70, 50, 720, 400), new PlacementProbeRandom(), placed, 0);
            require(legacy.getPlacement().getX() == 360 && legacy.getPlacement().getY() == 320 &&
                    current.getPlacement().getX() == 240 && current.getPlacement().getY() == 160,
                    "private production placement consumes legacy and corrected targets");
            System.out.println("A02_PLACEMENT legacy=360,320;current=240,160;raw=160,160");
        } catch (Exception failure) {
            throw new AssertionError("private placement production path failed: " + failure);
        }
    }

    private static final class PlacementProbeRandom extends java.util.Random {
        private static final long serialVersionUID = 1L;
        PlacementProbeRandom() { super(0L); }
        @Override public long nextLong() { return 0x123456789ABCDEFL; }
        @Override public int nextInt(int bound) {
            if (bound == 31) return 15;
            if (bound == 25) return 0;
            return 0;
        }
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
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, label + " is rejected");
    }

    private static void expectIllegalState(Runnable action, String label, String expectedReason) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalStateException expected) {
            require(expected.getMessage().startsWith(expectedReason),
                    label + " rejected at the intended geometry boundary: " + expected.getMessage());
            rejected = true;
        }
        require(rejected, label + " is rejected");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(Charset.forName("UTF-8")));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format("%02x", b & 0xff));
            return result.toString();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
