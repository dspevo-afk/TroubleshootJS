package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.Vector;

/** External scratch-only baseline probes for A02 F5/F6. */
public final class A02BaselineProbe {
    private static final Rectangle OUTLINE = new Rectangle(70, 50, 720, 400);

    private static final class ProbeRandom extends Random {
        private static final long serialVersionUID = 1L;
        ProbeRandom() { super(0L); }
        @Override public long nextLong() { System.out.println("RANDOM nextLong"); return 0x123456789ABCDEFL; }
        @Override public int nextInt(int bound) { System.out.println("RANDOM nextInt bound=" + bound);
            if (bound == 31) return 15;
            if (bound == 25) return 0;
            return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        probeF5();
        probeF6();
        probeGeneratedLayouts();
    }

    private static void probeF5() {
        PcbBoardLayout layout = new PcbBoardLayout(400, 300,
            new Rectangle(20, 20, 360, 240), new Rectangle(0, 0, 1, 1));
        int[][][] cases = new int[][][] {
            { { 0, 0 }, { 10, 0 }, { 30, 0 } },
            { { 0, 0 }, { 0, 10 }, { 0, 30 } },
            { { 0, 0 }, { 10, 0 }, { 10, 10 } },
            { { 30, 0 }, { 20, 0 }, { 0, 0 } },
            { { 0, 0 }, { 10, 0 }, { 0, 0 } },
            { { 0, 0 }, { 0, 0 }, { 10, 0 } },
            { { Integer.MAX_VALUE - 20, 0 }, { Integer.MAX_VALUE - 10, 0 }, { Integer.MAX_VALUE - 1, 0 } }
        };
        System.out.println("F5_BASELINE");
        for (int i = 0; i < cases.length; i++) {
            int[] xs = new int[cases[i].length];
            int[] ys = new int[cases[i].length];
            for (int j = 0; j < cases[i].length; j++) {
                xs[j] = cases[i][j][0];
                ys[j] = cases[i][j][1];
            }
            PcbTraceGeometry trace = new PcbTraceGeometry("N" + i, xs, ys);
            int current = layout.getTraceBendCount(trace);
            int corrected = expectedDirectionNormalizedBends(xs, ys);
            System.out.println("case=" + i + ",points=" + points(xs, ys) +
                ",current=" + current + ",corrected=" + corrected);
        }
    }

    private static int expectedDirectionNormalizedBends(int[] xs, int[] ys) {
        int bends = 0;
        int previousDx = 0;
        int previousDy = 0;
        boolean havePrevious = false;
        for (int i = 1; i < xs.length; i++) {
            int dx = xs[i] - xs[i - 1];
            int dy = ys[i] - ys[i - 1];
            int directionX = dx == 0 ? 0 : (dx < 0 ? -1 : 1);
            int directionY = dy == 0 ? 0 : (dy < 0 ? -1 : 1);
            if (havePrevious && (directionX != previousDx || directionY != previousDy)) bends++;
            previousDx = directionX;
            previousDy = directionY;
            havePrevious = true;
        }
        return bends;
    }

    private static void probeF6() throws Exception {
        TroubleshootBoard board = TroubleshootBoardFixtures.createLedIndicatorBoard();
        TopologyPlacementGraph topology = new TopologyPlacementGraph(board);
        Vector<PcbFootprint> placed = new Vector<PcbFootprint>();
        PcbFootprint connector = PcbFootprint.fromPhysicalPackage(board.getComponent("J1"), 100, 150);
        placed.add(connector);
        SeededPcbLayoutGenerator generator = new SeededPcbLayoutGenerator();
        Method method = SeededPcbLayoutGenerator.class.getDeclaredMethod(
            "placeTopologyComponent", BoardComponent.class, TopologyPlacementGraph.class,
            Rectangle.class, Random.class, Vector.class, int.class);
        method.setAccessible(true);
        PcbFootprint candidate = (PcbFootprint) method.invoke(generator,
            board.getComponent("R1"), topology, OUTLINE, new ProbeRandom(), placed, 0);
        for (int variation = 0; variation < 4; variation++) {
            PcbFootprint v = (PcbFootprint) method.invoke(generator,
                board.getComponent("R1"), topology, OUTLINE, new ProbeRandom(), placed, variation);
            System.out.println("variation=" + variation + ";candidate=" + v.getPlacement().getX() + "," + v.getPlacement().getY());
        }
        PcbFootprint prototype = PcbFootprint.fromPhysicalPackage(board.getComponent("R1"), 0, 0,
            new Random(0x123456789ABCDEFL), OUTLINE);
        TopologyPlacementGraph.PadLink link = null;
        for (TopologyPlacementGraph.PadLink value : topology.getLinksFor("R1"))
            if ("J1".equals(value.getOtherComponentId())) { link = value; break; }
        PcbPadPlacement source = prototype.getPad(link.getPadId());
        PcbPadPlacement other = connector.getPad(link.getOtherPadId());
        int centerX = OUTLINE.x + OUTLINE.width / 2 - prototype.getPlacement().getWidth() / 2;
        int centerY = OUTLINE.y + OUTLINE.height / 2 - prototype.getPlacement().getHeight() / 2;
        double intendedX = other.getX() - source.getX();
        double intendedY = other.getY() - source.getY();
        double baselineX = centerX + intendedX;
        double baselineY = centerY + intendedY;
        System.out.println("F6_BASELINE");
        System.out.println("fixture=LED_INDICATOR;placed=J1@" + connector.getPlacement().getX() + "," + connector.getPlacement().getY() +
            ";link=" + link.getPadId() + "->" + link.getOtherPadId() + ";weight=" + link.getWeight());
        System.out.println("prototype=R1@0,0;width=" + prototype.getPlacement().getWidth() + ",height=" + prototype.getPlacement().getHeight() +
            ";sourcePad=" + source.getX() + "," + source.getY() + ";otherWorldPad=" + other.getX() + "," + other.getY());
        System.out.println("centerOrigin=" + centerX + "," + centerY + ";intendedRawTarget=" + intendedX + "," + intendedY +
            ";baselineRawTarget=" + baselineX + "," + baselineY);
        Method fitsMethod = SeededPcbLayoutGenerator.class.getDeclaredMethod("fits", PcbFootprint.class, Rectangle.class, Vector.class);
        fitsMethod.setAccessible(true);
        System.out.println("hypotheticalFits=" + fitsMethod.invoke(generator, prototype.translated(480, 370), OUTLINE, placed) + ";courtyard=" + prototype.translated(480, 370).getPlacement().getRoutingCourtyard());
        System.out.println("hypotheticalFitsMinus120=" + fitsMethod.invoke(generator, prototype.translated(360, 320), OUTLINE, placed) + ";courtyard=" + prototype.translated(360, 320).getPlacement().getRoutingCourtyard());
        System.out.println("returnedCandidateOrigin=" + candidate.getPlacement().getX() + "," + candidate.getPlacement().getY() +
            ";returnedCandidatePad=" + candidate.getPad(link.getPadId()).getX() + "," + candidate.getPad(link.getPadId()).getY());
        System.out.println("baselineRawAligned=" + align((int) Math.round(baselineX)) + "," + align((int) Math.round(baselineY)) +
            ";intendedRawAligned=" + align((int) Math.round(intendedX)) + "," + align((int) Math.round(intendedY)));
        System.out.println("returnedMinusBaselineAligned=" + (candidate.getPlacement().getX() - align((int) Math.round(baselineX))) + "," +
            (candidate.getPlacement().getY() - align((int) Math.round(baselineY))));
    }

    private static void probeGeneratedLayouts() {
        SeededPcbLayoutGenerator generator = new SeededPcbLayoutGenerator();
        System.out.println("GENERATED_BASELINE");
        for (long seed : new long[] { 0L, 1L, 2L, 3L, Long.MIN_VALUE, Long.MAX_VALUE }) {
            try {
                TroubleshootBoard board = TroubleshootBoardFixtures.createLedIndicatorBoard();
                PcbBoardLayout layout = generator.generate(board, seed);
                System.out.println("seed=" + seed + ",fingerprintHash=" + layout.geometryFingerprint().hashCode() +
                    ",components=" + layout.componentGeometryFingerprint().hashCode() +
                    ",traces=" + layout.traceGeometryFingerprint().hashCode() +
                    ",quality=" + layout.getRouteQualityScore(board) +
                    ",traceCount=" + layout.getTraces().size());
                for (PcbComponentPlacement placement : layout.getComponents())
                    System.out.println("  component=" + placement.getComponentId() + "@" + placement.getX() + "," + placement.getY());
            } catch (RuntimeException failure) {
                System.out.println("seed=" + seed + ",failure=" + failure.getClass().getName() + ":" + failure.getMessage());
            }
        }
    }

    private static int align(int value) { return (value / 10) * 10; }

    private static String points(int[] xs, int[] ys) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < xs.length; i++) {
            if (i != 0) value.append(';');
            value.append(xs[i]).append(',').append(ys[i]);
        }
        return value.toString();
    }
}
