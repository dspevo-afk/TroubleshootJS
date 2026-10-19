package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

/** Read-only, authoritative geometry export for the private13 occupancy review. */
public final class PcbOccupancyDiagnosticContractTest {
    private static int assertions;

    public static void main(String[] args) {
        verifyLiteralBarrierStrips();
        exportRoot(42L);
        exportRoot(-1022L);
        System.out.println("PASS: PCB occupancy diagnostic contracts roots=2 assertions=" +
            assertions);
    }

    private static void verifyLiteralBarrierStrips() {
        Rectangle outline = new Rectangle(0, 0, 400, 300);

        // Both axes have room here; PcbTwoLayerRules chooses the x-separated
        // corridor first, so the strip is vertical despite the y separation.
        TreeMap<String,Rectangle> vertical = domains(
            "A", new Rectangle(30, 30, 80, 80),
            "B", new Rectangle(230, 220, 90, 60));
        BarrierStrip verticalStrip = deriveBarrierStrip(vertical,
            new PcbPlacementConstraints.Barrier("A", "B", 80), outline);
        expectStrip(verticalStrip, "VERTICAL", "A", "B", 130, 210,
            new Rectangle(130, 0, 80, 300), "vertical precedence fixture");

        TreeMap<String,Rectangle> horizontal = domains(
            "A", new Rectangle(40, 40, 150, 60),
            "B", new Rectangle(100, 200, 120, 60));
        BarrierStrip horizontalStrip = deriveBarrierStrip(horizontal,
            new PcbPlacementConstraints.Barrier("A", "B", 80), outline);
        expectStrip(horizontalStrip, "HORIZONTAL", "A", "B", 110, 190,
            new Rectangle(0, 110, 400, 80), "horizontal fixture");

        // The exact-gap edge case is independently fixed at x=120..200.
        TreeMap<String,Rectangle> exactGap = domains(
            "A", new Rectangle(40, 40, 80, 200),
            "B", new Rectangle(200, 50, 100, 180));
        BarrierStrip exactStrip = deriveBarrierStrip(exactGap,
            new PcbPlacementConstraints.Barrier("A", "B", 80), outline);
        expectStrip(exactStrip, "VERTICAL", "A", "B", 120, 200,
            new Rectangle(120, 0, 80, 300), "exact-gap fixture");
        check(exactGap.get("A").equals(new Rectangle(40, 40, 80, 200)) &&
                exactGap.get("B").equals(new Rectangle(200, 50, 100, 180)),
            "strip derivation leaves domain rectangles unchanged");

        TreeMap<String,Rectangle> overlap = domains(
            "A", new Rectangle(10, 10, 100, 100),
            "B", new Rectangle(50, 50, 100, 100));
        expectRejected(overlap, new PcbPlacementConstraints.Barrier("A", "B", 80),
            outline, "overlapping domains have no corridor");
        expectRejected(domains("A", new Rectangle(10, 10, 100, 100)),
            new PcbPlacementConstraints.Barrier("A", "B", 80), outline,
            "missing domain is rejected");
        expectRejected(null, new PcbPlacementConstraints.Barrier("A", "B", 80),
            outline, "missing domain map is rejected");
        expectRejected(exactGap, new PcbPlacementConstraints.Barrier("A", "B", 80),
            null, "missing outline is rejected");
        expectRejected(exactGap, null, outline, "missing barrier is rejected");
        expectRejected(domains("A", new Rectangle(10, 10, 0, 100),
                "B", new Rectangle(200, 10, 100, 100)),
            new PcbPlacementConstraints.Barrier("A", "B", 80), outline,
            "zero-area domain is rejected");
        boolean badGapRejected = false;
        try {
            new PcbPlacementConstraints.Barrier("A", "B", 19);
        } catch (IllegalArgumentException expected) {
            badGapRejected = true;
        }
        check(badGapRejected, "invalid barrier clearance is rejected");
    }

    private static void exportRoot(long seed) {
        Rb56Plan plan = Rb56Plan.resolve(seed, 13);
        TroubleshootBoard board = plan.board();
        MediumBoardPhysicalPolicy.Result route = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(board, plan.layoutSeed, plan.routingSeed);
        check(route != null && route.accepted(), "private13 route accepted for seed " + seed);
        PcbBoardLayout layout = route.getLayout();
        String before = layout.geometryFingerprint();

        layout.validateGeometry(board);
        layout.validateRoutingGeometry(board);
        layout.validateRouteQuality();
        new PcbTwoLayerRules(board, layout).validate(layout);
        PcbTraceClearance.validate(layout.getTraces());

        boolean top = false, bottom = false;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            top |= trace.getLayer() == PcbCopperLayer.TOP;
            bottom |= trace.getLayer() == PcbCopperLayer.BOTTOM;
        }
        check(top && bottom, "diagnostic geometry includes both copper faces for " + seed);
        boolean allHolesAreVias = true;
        for (PcbBoardHole hole : layout.getHoles())
            allHolesAreVias &= hole.kind == PcbBoardHole.Kind.VIA;
        check(allHolesAreVias, "all routed holes are exported as vias for " + seed);

        // Keep the established raster/void metric exactly on its existing path.
        PcbFunctionalGroupingContractTest.reportWhitespace(board, layout, seed, 13);

        TreeMap<String,Rectangle> unions = collectDomainUnions(board, layout);
        String packages = packageJson(board, layout);
        String domainUnions = domainUnionJson(unions);
        String barrierStrips = barrierStripJson(board, unions, layout.getBoardOutline());
        String traces = traceJson(board, layout);
        String vias = viaJson(layout, board.getPlacementConstraints().getTraceWidthProfile());
        String silk = silkJson(layout);

        String after = layout.geometryFingerprint();
        check(before.equals(after), "diagnostic export preserves geometry fingerprint for " +
            seed);
        MediumBoardPhysicalPolicy.Statistics stats = route.getStatistics();
        check(stats.selectedPlacementAttempt >= 0,
            "accepted route has a selected placement attempt for " + seed);

        Rectangle outline = layout.getBoardOutline();
        StringBuilder json = new StringBuilder(16000);
        json.append("PCB_OCCUPANCY_DIAGNOSTIC {");
        field(json, "seed", Long.toString(seed));
        json.append(",\"placementVersion\":").append(plan.placementInterpretationVersion);
        json.append(",\"placementDensityVersion\":")
            .append(board.getPlacementConstraints().placementDensityVersion);
        field(json, "geometryFingerprintBefore", PlayerSessionFingerprint.of(before));
        field(json, "geometryFingerprintAfter", PlayerSessionFingerprint.of(after));
        json.append(",\"selectedPlacementAttempt\":")
            .append(stats.selectedPlacementAttempt);
        field(json, "selectedRoutePolicy", stats.selectedRoutePolicy);
        json.append(",\"outline\":"); appendRect(json, outline);
        json.append(",\"packages\":").append(packages);
        json.append(",\"domainUnions\":").append(domainUnions);
        json.append(",\"barrierReservedStrips\":").append(barrierStrips);
        json.append(",\"traces\":").append(traces);
        json.append(",\"vias\":").append(vias);
        json.append(",\"silk\":").append(silk).append('}');
        System.out.println(json.toString());
    }

    private static TreeMap<String,Rectangle> collectDomainUnions(
            TroubleshootBoard board, PcbBoardLayout layout) {
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        TreeMap<String,Rectangle> unions = new TreeMap<String,Rectangle>();
        for (String componentId : componentIds) {
            PcbComponentPlacement placement = layout.getComponent(componentId);
            if (placement == null) throw new IllegalArgumentException(
                "Missing package while exporting domains: " + componentId);
            TreeMap<String,Rectangle> courtyards = constraints.domainCourtyards(placement);
            for (Map.Entry<String,Rectangle> entry : courtyards.entrySet()) {
                String domain = entry.getKey();
                Rectangle rect = requireRect(entry.getValue(), "domain courtyard " + domain);
                Rectangle prior = unions.get(domain);
                if (prior == null) unions.put(domain, new Rectangle(rect));
                else unions.put(domain, union(prior, rect));
            }
        }
        if (unions.isEmpty()) throw new IllegalArgumentException("No placement domain courtyards");
        return unions;
    }

    private static Rectangle union(Rectangle a, Rectangle b) {
        requireRect(a, "prior domain union"); requireRect(b, "domain courtyard");
        long left = Math.min((long)a.x, (long)b.x);
        long top = Math.min((long)a.y, (long)b.y);
        long right = Math.max((long)a.x + a.width, (long)b.x + b.width);
        long bottom = Math.max((long)a.y + a.height, (long)b.y + b.height);
        Rectangle result = new Rectangle(PcbCoordinateSystem.requireBoardCoordinate(left),
            PcbCoordinateSystem.requireBoardCoordinate(top),
            PcbCoordinateSystem.checkedInt(right - left),
            PcbCoordinateSystem.checkedInt(bottom - top));
        return requireRect(result, "merged domain union");
    }

    private static String packageJson(TroubleshootBoard board, PcbBoardLayout layout) {
        StringBuilder out = new StringBuilder("[");
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        boolean first = true;
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        for (String componentId : componentIds) {
            PcbComponentPlacement placement = layout.getComponent(componentId);
            BoardComponent component = board.getComponent(componentId);
            PcbPlacementConstraints.Part part = constraints.get(componentId);
            if (placement == null || component == null || part == null)
                throw new IllegalArgumentException("Incomplete package diagnostic: " + componentId);
            Vector<String> padIds = component.getPadIds();
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            for (String padId : padIds) {
                PcbPadPlacement pad = layout.getPad(padId);
                if (pad == null || board.getPad(padId) == null)
                    throw new IllegalArgumentException("Missing actual pad diagnostic: " + padId);
                pads.add(pad);
            }
            PcbFootprint footprint = new PcbFootprint(placement, pads);
            Rectangle access = requireRect(PcbPlacementPlanner.envelope(footprint,
                part.accessMargin), "package-access envelope " + componentId);
            Collections.sort(pads, new Comparator<PcbPadPlacement>() {
                public int compare(PcbPadPlacement a, PcbPadPlacement b) {
                    return a.getPadId().compareTo(b.getPadId());
                }
            });
            Rectangle routing = requireRect(placement.getRoutingCourtyard(),
                "routing courtyard " + componentId);
            TreeMap<String,Rectangle> domains = constraints.domainCourtyards(placement);
            if (!first) out.append(','); first = false;
            out.append('{'); field(out, "componentId", componentId);
            field(out, "packageId", placement.getPhysicalPackage().getId());
            out.append(",\"accessMargin\":").append(part.accessMargin);
            out.append(",\"packageAccess\":"); appendRect(out, access);
            out.append(",\"routingCourtyard\":"); appendRect(out, routing);
            out.append(",\"domainCourtyards\":[");
            boolean firstDomain = true;
            for (Map.Entry<String,Rectangle> entry : domains.entrySet()) {
                if (!firstDomain) out.append(','); firstDomain = false;
                out.append('{'); field(out, "domain", entry.getKey());
                out.append(",\"rect\":"); appendRect(out,
                    requireRect(entry.getValue(), "individual domain courtyard"));
                out.append('}');
            }
            out.append("],\"pads\":[");
            for (int index = 0; index < pads.size(); index++) {
                if (index > 0) out.append(',');
                PcbPadPlacement pad = pads.get(index);
                BoardPad logical = board.getPad(pad.getPadId());
                out.append('{'); field(out, "padId", pad.getPadId());
                field(out, "net", logical.getNetId());
                out.append(",\"bounds\":"); appendRect(out,
                    requireRect(pad.getPadBounds(), "pad bounds " + pad.getPadId()));
                out.append(",\"probeBounds\":"); appendRect(out,
                    requireRect(pad.getProbeBounds(), "probe bounds " + pad.getPadId()));
                out.append('}');
            }
            out.append("]}");
        }
        return out.append(']').toString();
    }

    private static String domainUnionJson(TreeMap<String,Rectangle> domains) {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String,Rectangle> entry : domains.entrySet()) {
            if (!first) out.append(','); first = false;
            out.append('{'); field(out, "domain", entry.getKey());
            out.append(",\"rect\":"); appendRect(out,
                requireRect(entry.getValue(), "domain union"));
            out.append('}');
        }
        return out.append(']').toString();
    }

    private static String barrierStripJson(TroubleshootBoard board,
            TreeMap<String,Rectangle> domains, Rectangle outline) {
        Vector<PcbPlacementConstraints.Barrier> barriers =
            board.getPlacementConstraints().getBarriers();
        Collections.sort(barriers, new Comparator<PcbPlacementConstraints.Barrier>() {
            public int compare(PcbPlacementConstraints.Barrier a,
                    PcbPlacementConstraints.Barrier b) {
                int first = a.firstDomain.compareTo(b.firstDomain);
                return first != 0 ? first : a.secondDomain.compareTo(b.secondDomain);
            }
        });
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < barriers.size(); index++) {
            if (index > 0) out.append(',');
            PcbPlacementConstraints.Barrier barrier = barriers.get(index);
            BarrierStrip strip = deriveBarrierStrip(domains, barrier, outline);
            out.append('{'); field(out, "firstDomain", barrier.firstDomain);
            field(out, "secondDomain", barrier.secondDomain);
            field(out, "lowDomain", strip.lowDomain);
            field(out, "highDomain", strip.highDomain);
            field(out, "axis", strip.axis);
            out.append(",\"clearance\":").append(barrier.clearance);
            out.append(",\"lowLimit\":").append(strip.lowLimit);
            out.append(",\"highLimit\":").append(strip.highLimit);
            out.append(",\"rect\":"); appendRect(out, strip.rect);
            out.append('}');
        }
        return out.append(']').toString();
    }

    /** Mirrors the authoritative PcbTwoLayerRules axis priority and reportWhitespace strip. */
    static BarrierStrip deriveBarrierStrip(Map<String,Rectangle> domains,
            PcbPlacementConstraints.Barrier barrier, Rectangle outline) {
        if (domains == null || barrier == null)
            throw new IllegalArgumentException("Missing barrier strip inputs");
        requireRect(outline, "barrier strip outline");
        if (barrier.clearance < 20 || barrier.clearance > 1000)
            throw new IllegalArgumentException("Invalid barrier clearance");
        Rectangle a = requireRect(domains.get(barrier.firstDomain), "first barrier domain");
        Rectangle b = requireRect(domains.get(barrier.secondDomain), "second barrier domain");
        long aRight = (long)a.x + a.width, bRight = (long)b.x + b.width;
        long aBottom = (long)a.y + a.height, bBottom = (long)b.y + b.height;
        long gap = barrier.clearance, low, high;
        String lowDomain, highDomain, axis;
        if (aRight + gap <= b.x) {
            low = aRight; high = b.x; lowDomain = barrier.firstDomain;
            highDomain = barrier.secondDomain; axis = "VERTICAL";
        } else if (bRight + gap <= a.x) {
            low = bRight; high = a.x; lowDomain = barrier.secondDomain;
            highDomain = barrier.firstDomain; axis = "VERTICAL";
        } else if (aBottom + gap <= b.y) {
            low = aBottom; high = b.y; lowDomain = barrier.firstDomain;
            highDomain = barrier.secondDomain; axis = "HORIZONTAL";
        } else if (bBottom + gap <= a.y) {
            low = bBottom; high = a.y; lowDomain = barrier.secondDomain;
            highDomain = barrier.firstDomain; axis = "HORIZONTAL";
        } else {
            throw new IllegalArgumentException("P07 domain barrier has no separated corridor");
        }
        long middle = low + (high - low) / 2;
        long lowLimit = middle - (gap + 1L) / 2L;
        long highLimit = middle + gap / 2L;
        int lowInt = PcbCoordinateSystem.requireBoardCoordinate(lowLimit);
        int highInt = PcbCoordinateSystem.requireBoardCoordinate(highLimit);
        Rectangle reserved = axis.equals("VERTICAL") ?
            new Rectangle(lowInt, outline.y, barrier.clearance, outline.height) :
            new Rectangle(outline.x, lowInt, outline.width, barrier.clearance);
        requireRect(reserved, "reserved barrier strip");
        if ((long)reserved.x < outline.x || (long)reserved.y < outline.y ||
                (long)reserved.x + reserved.width > (long)outline.x + outline.width ||
                (long)reserved.y + reserved.height > (long)outline.y + outline.height ||
                (long)highInt - lowInt != gap)
            throw new IllegalArgumentException("Reserved strip leaves its outline or gap");
        return new BarrierStrip(lowDomain, highDomain, axis, lowInt, highInt, reserved);
    }

    private static String traceJson(TroubleshootBoard board, PcbBoardLayout layout) {
        Vector<PcbTraceGeometry> traces = layout.getTraces();
        Collections.sort(traces, new Comparator<PcbTraceGeometry>() {
            public int compare(PcbTraceGeometry a, PcbTraceGeometry b) {
                String left = String.valueOf(a.getSourceId()) + "/" + a.getNetId() + "/" + a.getLayer();
                String right = String.valueOf(b.getSourceId()) + "/" + b.getNetId() + "/" + b.getLayer();
                return left.compareTo(right);
            }
        });
        int maximumWidth = board.getPlacementConstraints().getTraceWidthProfile().maximumWidth();
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < traces.size(); index++) {
            if (index > 0) out.append(',');
            PcbTraceGeometry trace = traces.get(index);
            BoardNet net = board.getNet(trace.getNetId());
            if (net == null || trace.getWidth() <= 0)
                throw new IllegalArgumentException("Invalid routed trace diagnostic");
            out.append('{'); field(out, "sourceId", trace.getSourceId());
            field(out, "net", trace.getNetId()); field(out, "layer", trace.getLayer().name());
            out.append(",\"width\":").append(trace.getWidth());
            out.append(",\"whitespaceGuard\":")
                .append(PcbTraceRules.clearance(trace.getWidth(), maximumWidth));
            out.append(",\"points\":[");
            int[] x = trace.getXPoints(), y = trace.getYPoints();
            if (x.length != y.length || x.length < 2)
                throw new IllegalArgumentException("Malformed routed trace points");
            for (int point = 0; point < x.length; point++) {
                if (point > 0) out.append(',');
                PcbCoordinateSystem.requireBoardCoordinate(x[point]);
                PcbCoordinateSystem.requireBoardCoordinate(y[point]);
                out.append('[').append(x[point]).append(',').append(y[point]).append(']');
            }
            out.append("]}");
        }
        return out.append(']').toString();
    }

    private static String viaJson(PcbBoardLayout layout, PcbTraceWidthProfile profile) {
        Vector<PcbBoardHole> vias = new Vector<PcbBoardHole>();
        for (PcbBoardHole hole : layout.getHoles())
            if (hole.kind == PcbBoardHole.Kind.VIA) vias.add(hole);
        Collections.sort(vias, new Comparator<PcbBoardHole>() {
            public int compare(PcbBoardHole a, PcbBoardHole b) {
                return a.id.compareTo(b.id);
            }
        });
        int guard = PcbTraceRules.clearance(profile.maximumWidth(), profile.maximumWidth());
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < vias.size(); index++) {
            if (index > 0) out.append(',');
            PcbBoardHole via = vias.get(index);
            if (via.netId == null) throw new IllegalArgumentException("Via lacks net identity");
            out.append('{'); field(out, "id", via.id); field(out, "net", via.netId);
            out.append(",\"bounds\":"); appendRect(out,
                requireRect(via.getBounds(), "via bounds " + via.id));
            out.append(",\"whitespaceGuard\":").append(guard).append('}');
        }
        return out.append(']').toString();
    }

    private static String silkJson(PcbBoardLayout layout) {
        Vector<PcbSilkscreenLabel> labels = layout.getSilkscreenLabels();
        Collections.sort(labels, new Comparator<PcbSilkscreenLabel>() {
            public int compare(PcbSilkscreenLabel a, PcbSilkscreenLabel b) {
                return a.getId().compareTo(b.getId());
            }
        });
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < labels.size(); index++) {
            if (index > 0) out.append(',');
            PcbSilkscreenLabel label = labels.get(index);
            out.append('{'); field(out, "id", label.getId());
            out.append(",\"bounds\":"); appendRect(out,
                requireRect(label.getBounds(), "silkscreen bounds " + label.getId()));
            out.append('}');
        }
        return out.append(']').toString();
    }

    private static TreeMap<String,Rectangle> domains(String first, Rectangle a,
            String second, Rectangle b) {
        TreeMap<String,Rectangle> result = new TreeMap<String,Rectangle>();
        result.put(first, new Rectangle(a)); result.put(second, new Rectangle(b));
        return result;
    }

    private static TreeMap<String,Rectangle> domains(String domain, Rectangle rect) {
        TreeMap<String,Rectangle> result = new TreeMap<String,Rectangle>();
        result.put(domain, rect == null ? null : new Rectangle(rect));
        return result;
    }

    private static void expectStrip(BarrierStrip actual, String axis, String lowDomain,
            String highDomain, int lowLimit, int highLimit, Rectangle rect, String label) {
        check(axis.equals(actual.axis), label + " axis");
        check(lowDomain.equals(actual.lowDomain) && highDomain.equals(actual.highDomain),
            label + " ordered domains");
        check(lowLimit == actual.lowLimit && highLimit == actual.highLimit,
            label + " literal limits");
        check(rect.equals(actual.rect), label + " literal rectangle");
    }

    private static void expectRejected(Map<String,Rectangle> domains,
            PcbPlacementConstraints.Barrier barrier, Rectangle outline, String label) {
        boolean rejected = false;
        try {
            deriveBarrierStrip(domains, barrier, outline);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, label);
    }

    private static Rectangle requireRect(Rectangle rectangle, String label) {
        if (rectangle == null) throw new IllegalArgumentException("Missing " + label);
        PcbCoordinateSystem.requireBoardRectangle(rectangle);
        return rectangle;
    }

    private static void appendRect(StringBuilder out, Rectangle rect) {
        requireRect(rect, "JSON rectangle");
        out.append('[').append(rect.x).append(',').append(rect.y).append(',')
            .append(rect.width).append(',').append(rect.height).append(']');
    }

    private static void field(StringBuilder out, String key, String value) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '{') out.append(',');
        appendString(out, key); out.append(':'); appendString(out, value);
    }

    private static void appendString(StringBuilder out, String value) {
        if (value == null) throw new IllegalArgumentException("Missing JSON string");
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c == '\b') out.append("\\b");
            else if (c == '\f') out.append("\\f");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else if (c < 0x20) {
                String hex = Integer.toHexString(c);
                out.append("\\u");
                for (int pad = hex.length(); pad < 4; pad++) out.append('0');
                out.append(hex);
            } else out.append(c);
        }
        out.append('"');
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }

    static final class BarrierStrip {
        final String lowDomain, highDomain, axis;
        final int lowLimit, highLimit;
        final Rectangle rect;
        BarrierStrip(String lowDomain, String highDomain, String axis,
                int lowLimit, int highLimit, Rectangle rect) {
            this.lowDomain = lowDomain; this.highDomain = highDomain; this.axis = axis;
            this.lowLimit = lowLimit; this.highLimit = highLimit; this.rect = new Rectangle(rect);
        }
    }
}
