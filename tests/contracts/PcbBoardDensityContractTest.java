package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Board realism: generated boards are space conscious. Courtyard fill is the
 * summed routing-courtyard area of every placed part (both board sides) over
 * the final board outline area. Each family must stay above its fill floor.
 */
public final class PcbBoardDensityContractTest {
    static final long[] ROOTS = { 0L, 17L };
    private static int assertions;

    private PcbBoardDensityContractTest() { }

    public static void main(String[] args) {
        CirSim prior = CircuitElm.sim;
        CirSim sim = new CirSim();
        sim.elmList = new Vector<CircuitElm>();
        sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7;
        CircuitElm.sim = sim;
        int cases = 0;
        try {
            for (String family : PlayerFamilyCatalog.families()) {
                DifficultyProfile profile = PlayerFamilyCatalog.candidateProfile(family);
                double minimum = 1, sum = 0;
                int accepted = 0;
                for (long root : ROOTS) {
                    double fill = measure(family, profile, root);
                    cases++;
                    if (Double.isNaN(fill)) continue;
                    accepted++; sum += fill; minimum = Math.min(minimum, fill);
                }
                require(accepted > 0, "density census has an accepted board: " + family);
                System.out.println("DENSITY_FAMILY " + family + " accepted=" + accepted +
                    " meanFill=" + percent(sum / accepted) + " minFill=" + percent(minimum));
                require(minimum >= floor(family), family + " courtyard fill " + percent(minimum) +
                    " is below its floor " + percent(floor(family)));
            }
        } finally {
            CircuitElm.sim = prior;
        }
        System.out.println("PASS: PCB board density contracts cases=" + cases + " assertions=" + assertions);
    }

    /**
     * Fill floors, one point under the measured minimum when compaction landed.
     * RB30 uses gravity compaction; it measured 11.6% before and 15.6% after.
     */
    static double floor(String family) {
        if ("RB30_CONTROL".equals(family)) return 0.145;
        if ("PARALLEL_DUAL_INDICATOR".equals(family)) return 0.09;
        if ("RB15_CONTROL".equals(family)) return 0.105;
        if ("COMPOSED_CONTROLLED_INDICATOR".equals(family)) return 0.115;
        return 0.125;
    }

    private static double measure(String family, DifficultyProfile profile, long root) {
        GeneratedBoardInstance owner = null;
        try {
            GenerationRequest candidate = PlayerLaunchRequest.random(family, Long.toString(root),
                profile.name()).generation().candidate(0);
            GenerationRequest.Prepared prepared = candidate.resolve(new GenerationRequest.PlanCache());
            if (candidate.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM ||
                    candidate.getExecutionPolicy() == GenerationExecutionPolicy.RB56) {
                GenerationRequest.ConstructionSession session = prepared.beginConstruction();
                int steps = 0;
                while (!session.advance())
                    if (++steps > GenerationCoordinator.MAX_JOB_STEPS)
                        throw new AssertionError("density construction exceeded the shared step bound");
                owner = session.result().instance;
            } else {
                owner = prepared.construct().instance;
            }
            PcbBoardLayout layout = owner.getPcbLayout();
            Rectangle outline = layout.getBoardOutline();
            long courtyard = 0; int bottom = 0;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (PcbComponentPlacement part : layout.getComponents()) {
                Rectangle c = part.getRoutingCourtyard();
                courtyard += (long) c.width * c.height;
                minX = Math.min(minX, c.x); minY = Math.min(minY, c.y);
                maxX = Math.max(maxX, c.x + c.width); maxY = Math.max(maxY, c.y + c.height);
                if (part.getMountingSide() == PcbBoardSide.BOTTOM) bottom++;
            }
            double fill = courtyard / (double) ((long) outline.width * outline.height);
            System.out.println("DENSITY_ROW " + family + " root=" + root + " parts=" +
                layout.getComponents().size() + " bottom=" + bottom + " outline=" +
                outline.width + "x" + outline.height + " mm=" + mm(outline.width) + "x" +
                mm(outline.height) + " fill=" + percent(fill) + " partsBox=" + (maxX - minX) + "x" +
                (maxY - minY) + " margins=L" + (minX - outline.x) + ",T" + (minY - outline.y) + ",R" +
                (outline.x + outline.width - maxX) + ",B" + (outline.y + outline.height - maxY) + probe(layout.getPackingProbeReceipt()));
            String svgDir = System.getenv("DENSITY_SVG_DIR");
            if (svgDir != null) writeSvg(new java.io.File(svgDir, family + "-" + root + ".svg"), layout);
            return fill;
        } catch (PcbRoutingRejectedException rejected) {
            System.out.println("DENSITY_ROW " + family + " root=" + root + " REJECTED " + rejected.getMessage());
            return Double.NaN;
        } finally {
            if (owner != null) {
                owner.getExternalPowerBindings().setConnected(false);
                for (CircuitElm element : owner.getSimulationElements()) element.delete();
            }
        }
    }

    /** Developer picture of one board: courtyards, bodies, pads, top/bottom copper. */
    private static void writeSvg(java.io.File file, PcbBoardLayout layout) {
        Rectangle o = layout.getBoardOutline();
        StringBuilder s = new StringBuilder();
        s.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='").append(o.x).append(' ')
            .append(o.y).append(' ').append(o.width).append(' ').append(o.height)
            .append("' width='").append(o.width / 2).append("'>");
        s.append(rect(o, "#0b3d2e", "none"));
        for (PcbComponentPlacement p : layout.getComponents()) {
            s.append(rect(p.getRoutingCourtyard(), "none", "#ffd54f"));
            s.append(rect(p.getBodyBounds(), "#455a64", "none"));
        }
        for (PcbTraceGeometry t : layout.getTraces()) {
            int[] x = t.getXPoints(), y = t.getYPoints();
            s.append("<polyline fill='none' stroke-linecap='round' stroke-width='").append(t.getWidth())
                .append("' stroke='").append(t.getLayer() == PcbCopperLayer.TOP ? "#e57373" : "#64b5f6")
                .append("' stroke-opacity='0.8' points='");
            for (int i = 0; i < x.length; i++) s.append(x[i]).append(',').append(y[i]).append(' ');
            s.append("'/>");
        }
        for (PcbPadPlacement pad : layout.getPads()) s.append(rect(pad.getPadBounds(), "#cfa850", "none"));
        s.append("</svg>");
        try {
            java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), "UTF-8");
            w.write(s.toString()); w.close();
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    private static String rect(Rectangle r, String fill, String stroke) {
        return "<rect x='" + r.x + "' y='" + r.y + "' width='" + r.width + "' height='" + r.height +
            "' fill='" + fill + "' stroke='" + stroke + "' stroke-width='3'/>";
    }

    private static String probe(PcbPackingProbeReceipt r) {
        if (r == null) return " probe=NONE";
        String out = " probe=" + r.disposition + " attempts=" + r.attempts;
        PcbPackingProbeReceipt.MetricEvidence m = r.metricEvidence;
        if (m == null) return out;
        out += " " + m.state;
        if (m.baseline != null) out += " base[a=" + m.baseline.area + " cu=" + m.baseline.copper +
            " v=" + m.baseline.vias + " pv=" + m.baseline.protectedVoid + "]";
        if (m.candidate != null) out += " cand[a=" + m.candidate.area + " cu=" + m.candidate.copper +
            " v=" + m.candidate.vias + " pv=" + m.candidate.protectedVoid + "]";
        if (m.unavailableReason != null) out += " why=" + m.unavailableReason;
        return out;
    }

    /** Board drawing units are 2.5 mil (40 per 0.1 inch pin pitch). */
    private static String mm(int units) { return String.format("%.0f", units * 0.0635); }
    private static String percent(double value) { return String.format("%.1f%%", value * 100); }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
