package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Vector;

/** Literal actual-compactor contracts for per-axis cut observations. */
public final class PcbCompactorCutObserverContractTest {
    private static int assertions;

    public static void main(String[] args) {
        check(PcbPlacementCompactor.diagnosticObserver == null,
            "diagnostic observer starts clear");
        straightCrossingAndNoObserverEquivalence();
        barrierRejectsWholeXAxis();
        detourRejectsWholeXAxis();
        check(PcbPlacementCompactor.diagnosticObserver == null,
            "diagnostic observer is clear after all fixtures");
        check(assertions <= 160, "assertion count remains bounded");
        System.out.println("PASS: PCB compactor cut observer contracts assertions=" +
            assertions + " fixtures=3 evaluator=compactRouted");
    }

    private static void straightCrossingAndNoObserverEquivalence() {
        Fixture fixture = fixture("STRAIGHT", 41, 1000, 700, 100, 800,
            "BOARD", "BOARD", false);
        addTrace(fixture, false);
        validateInput(fixture);
        String inputBefore = inputFingerprint(fixture);
        Capture observed = new Capture();
        PcbPlacementCompactor.Routed compacted = compactObserved(fixture, observed);
        check(inputBefore.equals(inputFingerprint(fixture)),
            "straight cut leaves the input plan and routed layout immutable");
        check(compacted.plan.outline.width == 440 && compacted.plan.outline.height == 700,
            "literal straight-cut outline shrinks by560 on x only");
        check(compacted.layout.getBoardOutline().width == 440,
            "materialized straight result uses the shortened outline");
        PcbTraceGeometry inputTrace = fixture.routed.getTraces().firstElement();
        PcbTraceGeometry outputTrace = compacted.layout.getTraces().firstElement();
        check("STRAIGHT_SOURCE".equals(outputTrace.getSourceId()) &&
            "N".equals(outputTrace.getNetId()) &&
            "A.1".equals(outputTrace.getStartPadId()) &&
            "B.1".equals(outputTrace.getEndPadId()),
            "straight copper retains source, net and declared pad identities");
        check(outputTrace.getLayer() == inputTrace.getLayer() &&
            outputTrace.getExposure() == inputTrace.getExposure() &&
            outputTrace.getWidth() == inputTrace.getWidth() && outputTrace.getWidth() == 9,
            "straight copper retains its layer, exposure and literal width9");
        check(compacted.layout.getPad("A.1").getX() == 100 &&
            compacted.layout.getPad("B.1").getX() == 240 &&
            outputTrace.getXPoints()[0] == 100 && outputTrace.getXPoints()[1] == 240,
            "the actual connected crossing shortens from700 to140 at the moved endpoint");
        check(traceLength(inputTrace) == 700 && traceLength(outputTrace) == 140,
            "literal input and shortened copper lengths are independently exact");
        check(compacted.layout.getPad("A.1") != null && compacted.layout.getPad("B.1") != null,
            "both declared endpoint pads survive the cut");
        PcbPlacementPlanner.validate(fixture.board, fixture.constraints,
            compacted.plan.outline, compacted.plan.footprints);
        compacted.layout.validateRoutingGeometry(fixture.board);
        compacted.layout.validateRouteQuality();
        check(true, "shortened actual copper passes physical and route-quality validators");

        axis(observed, 0, 41, 1000, 700, 1, 0, 1, 560, "ACCEPTED", "", 440, 700);
        cut(observed, 0, 0, 740, 560);
        axis(observed, 1, 41, 440, 700, 1, 0, 0, 0, "NOCUT", "", 440, 700);

        check(PcbPlacementCompactor.diagnosticObserver == null,
            "observer cleared before no-callback equivalence run");
        PcbPlacementCompactor.Routed noObserver = PcbPlacementCompactor.compactRouted(
            fixture.board, fixture.constraints, fixture.plan, fixture.routed);
        check(PcbPlacementCompactor.diagnosticObserver == null,
            "no-callback run keeps observer clear");
        check(resultFingerprint(compacted).equals(resultFingerprint(noObserver)),
            "default-null observation preserves exact straight geometry and plan work fields");
        check(inputBefore.equals(inputFingerprint(fixture)),
            "no-callback equivalence run also leaves source input immutable");
    }

    private static void barrierRejectsWholeXAxis() {
        Fixture fixture = fixture("BARRIER", 42, 400, 500, 100, 200,
            "LEFT", "RIGHT", true);
        validateInput(fixture);
        RectanglePair gap = barrierCourtyards(fixture);
        check(gap.right.x - (gap.left.x + gap.left.width) == 80,
            "literal before-state physical barrier gap is80");
        String inputBefore = inputFingerprint(fixture);
        String xBefore = xProjection(fixture.plan, fixture.routed);
        Capture observed = new Capture();
        PcbPlacementCompactor.Routed compacted = compactObserved(fixture, observed);
        check(inputBefore.equals(inputFingerprint(fixture)),
            "rejected placement axis leaves source plan and routed layout immutable");
        axis(observed, 0, 42, 400, 500, 0, 0, 1, 20,
            "PLACEMENT_REJECTED", "ENVELOPE_ACCESS_OR_DOMAIN", 400, 500);
        cut(observed, 0, 0, 170, 20);
        axis(observed, 1, 42, 400, 500, 0, 0, 0, 0, "NOCUT", "", 400, 500);
        check(xBefore.equals(xProjection(compacted.plan, compacted.layout)),
            "barrier rejection retains every x coordinate and the original x outline");
        check(compacted.plan.outline.width == 400,
            "barrier-rejected x axis retains its pre-axis outline width");
        compacted.layout.validateRoutingGeometry(fixture.board);
        compacted.layout.validateRouteQuality();
        check(true, "retained barrier layout passes physical and route-quality validators");
    }

    private static void detourRejectsWholeXAxis() {
        Fixture fixture = fixture("DETOUR", 43, 1500, 1100, 100, 1100,
            "BOARD", "BOARD", false);
        addTrace(fixture, true);
        validateInput(fixture);
        check(1100 - 100 == 1000 && 950 - 200 == 750 &&
            traceLength(fixture.routed.getTraces().firstElement()) == 2500 &&
            2500 <= 3 * 1000,
            "literal U-route before-state has D1000 H750 L2500 within the three-times bound");
        check(1650 > 3 * 150,
            "literal x-compressed U-route length1650 exceeds direct-distance bound450");
        String inputBefore = inputFingerprint(fixture);
        String xBefore = xProjection(fixture.plan, fixture.routed);
        Capture observed = new Capture();
        PcbPlacementCompactor.Routed compacted = compactObserved(fixture, observed);
        check(inputBefore.equals(inputFingerprint(fixture)),
            "detour rejection and later y compaction leave routed input immutable");
        axis(observed, 0, 43, 1500, 1100, 1, 0, 1, 850,
            "QUALITY_REJECTED", "DETOUR", 1500, 1100);
        cut(observed, 0, 0, 1035, 850);
        axis(observed, 1, 43, 1500, 1100, 1, 0, 1, 680,
            "ACCEPTED", "", 1500, 420);
        cut(observed, 1, 0, 935, 680);
        check(xBefore.equals(xProjection(compacted.plan, compacted.layout)),
            "detour-rejected x axis retains x geometry while later y compaction may succeed");
        check(compacted.plan.outline.width == 1500 && compacted.plan.outline.height == 420,
            "after x rejection the independent y cut may shorten the outline by680");
        PcbPlacementPlanner.validate(fixture.board, fixture.constraints,
            compacted.plan.outline, compacted.plan.footprints);
        compacted.layout.validateRoutingGeometry(fixture.board);
        compacted.layout.validateRouteQuality();
        check(true, "actual detour-retained route passes physical and route-quality validators");
    }

    private static Fixture fixture(String name, int candidate, int outlineWidth,
            int outlineHeight, int leftPadX, int rightPadX, String leftDomain,
            String rightDomain, boolean barrier) {
        TroubleshootBoard board = new TroubleshootBoard("COMPACTOR_" + name);
        String leftNet = barrier ? "LEFT_NET" : "N";
        String rightNet = barrier ? "RIGHT_NET" : leftNet;
        board.addNet(new BoardNet(leftNet));
        if (barrier) board.addNet(new BoardNet(rightNet));
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        PhysicalPackage physical = escapePointPackage("COMPACTOR_" + name + "_POINT",
            barrier ? 0 : -1, barrier ? 1 : 0, 30);
        footprints.add(addPoint(board, "A", leftNet, leftPadX, 200,
            PcbRotation.DEG_180, physical));
        footprints.add(addPoint(board, "B", rightNet, rightPadX, 200,
            PcbRotation.DEG_0, physical));
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        parts.add(new PcbPlacementConstraints.Part("A", leftDomain, leftDomain, leftDomain,
            PcbPlacementConstraints.Anchor.NONE, 20));
        parts.add(new PcbPlacementConstraints.Part("B", rightDomain, rightDomain, rightDomain,
            PcbPlacementConstraints.Anchor.NONE, 20));
        Vector<PcbPlacementConstraints.Barrier> barriers = new Vector<PcbPlacementConstraints.Barrier>();
        if (barrier) barriers.add(new PcbPlacementConstraints.Barrier("LEFT", "RIGHT", 80));
        PcbPlacementConstraints constraints = new PcbPlacementConstraints(parts, barriers,
            PcbCopperLayer.TOP);
        board.setPlacementConstraints(constraints);
        board.validate();
        PcbPlacementPlanner.Plan plan = new PcbPlacementPlanner.Plan(
            new Rectangle(20, 20, outlineWidth, outlineHeight), footprints,
            new Vector<PcbLayoutRegion>(), 0, 0, candidate, 0);
        return new Fixture(board, constraints, plan, plan.materialize());
    }

    private static PcbFootprint addPoint(TroubleshootBoard board, String id, String net,
            int x, int y, PcbRotation rotation, PhysicalPackage physical) {
        BoardComponent component = new BoardComponent(id, physical.getId(), physical, id);
        board.addComponent(component);
        board.addPad(new BoardPad(id + ".1", id, "1", net));
        PcbPackagePose reference = new PcbPackagePose(100, 100, rotation, PcbBoardSide.TOP);
        Point local = physical.getGeometry().placedAt(reference).getPadPoint(0);
        PcbPackagePose pose = new PcbPackagePose(100 + x - local.x, 100 + y - local.y,
            rotation, PcbBoardSide.TOP);
        return PcbFootprint.fromPhysicalPackage(component, pose, physical.getGeometry());
    }

    private static void addTrace(Fixture fixture, boolean uShape) {
        int[] xs = uShape ? new int[] {100, 150, 150, 1050, 1050, 1100} :
            new int[] {100, 800};
        int[] ys = uShape ? new int[] {200, 200, 950, 950, 200, 200} :
            new int[] {200, 200};
        fixture.routed.addTrace(new PcbTraceGeometry(
            uShape ? "U_ROUTE_SOURCE" : "STRAIGHT_SOURCE", "N", "A.1", "B.1",
            PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED, xs, ys, 9));
    }

    private static PhysicalPackage escapePointPackage(String id, int escapeDx,
            int escapeDy, int escapeLength) {
        PhysicalPackage template = P06FactoryLinkFixtures.TEST_POINT;
        PhysicalPackageGeometry source = template.getGeometry();
        PhysicalPackageGeometry.Terminal original = source.getTerminal(0);
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(new PhysicalPackageGeometry.Terminal(original.getTerminalId(),
            original.getPadCenter(), original.getPadBounds(), original.getBoardPadProbeCenter(),
            original.getBoardPadProbeBounds(), original.getConnectedLead(),
            original.getLiftedLead(), escapeDx, escapeDy, escapeLength,
            original.getAttachment()));
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(source.getWidth(),
            source.getHeight(), terminals, source.getBodyBounds(), source.getBodyKeepOut(),
            source.getRoutingCourtyard(), source.getSelectionEnvelope(),
            source.getDragEnvelope(), source.getGeometryContractVersion());
        Vector<PhysicalPackage.GeometryVariant> variants =
            new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT", "IDENTITY", geometry));
        Vector<PcbRotation> rotations = new Vector<PcbRotation>();
        rotations.add(PcbRotation.DEG_0);
        rotations.add(PcbRotation.DEG_180);
        Vector<PcbBoardSide> sides = new Vector<PcbBoardSide>();
        sides.add(PcbBoardSide.TOP);
        return new PhysicalPackage(id, template.getTerminalIds(), new Vector<String>(), false,
            geometry, variants, "DEFAULT", PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT,
            rotations, sides);
    }

    private static int traceLength(PcbTraceGeometry trace) {
        int[] xs = trace.getXPoints(), ys = trace.getYPoints();
        int length = 0;
        for (int index = 1; index < xs.length; index++)
            length += Math.abs(xs[index] - xs[index - 1]) +
                Math.abs(ys[index] - ys[index - 1]);
        return length;
    }

    private static void validateInput(Fixture fixture) {
        PcbPlacementPlanner.validate(fixture.board, fixture.constraints,
            fixture.plan.outline, fixture.plan.footprints);
        fixture.routed.validateRoutingGeometry(fixture.board);
        fixture.routed.validateRouteQuality();
    }

    private static PcbPlacementCompactor.Routed compactObserved(Fixture fixture,
            Capture observer) {
        check(PcbPlacementCompactor.diagnosticObserver == null,
            "observer is null before each observed fixture");
        PcbPlacementCompactor.diagnosticObserver = observer;
        try {
            return PcbPlacementCompactor.compactRouted(fixture.board, fixture.constraints,
                fixture.plan, fixture.routed);
        } finally {
            PcbPlacementCompactor.diagnosticObserver = null;
        }
    }

    private static RectanglePair barrierCourtyards(Fixture fixture) {
        Rectangle left = fixture.constraints.domainCourtyards(
            fixture.plan.footprints.get(0).getPlacement()).get("LEFT");
        Rectangle right = fixture.constraints.domainCourtyards(
            fixture.plan.footprints.get(1).getPlacement()).get("RIGHT");
        check(left != null && right != null && left.x == 70 && left.width == 40 &&
            right.x == 190 && right.width == 40,
            "literal test-point courtyard coordinates are70..110 and190..230");
        return new RectanglePair(left, right);
    }

    private static void axis(Capture capture, int number, int candidate, int beforeWidth,
            int beforeHeight, int traces, int holes, int cutCount, int removed,
            String outcome, String reason, int afterWidth, int afterHeight) {
        check(capture.axes[number] != null, "observer began axis" + number);
        Axis axis = capture.axes[number];
        check(axis.candidate == candidate && axis.axis == number &&
            axis.beforeWidth == beforeWidth && axis.beforeHeight == beforeHeight,
            "axis" + number + " begin records candidate and literal before dimensions");
        check(axis.traces == traces && axis.holes == holes,
            "axis" + number + " begin records trace and hole counts");
        check(axis.ended && axis.cutCount == cutCount && axis.cuts.size() == cutCount &&
            axis.removed == removed,
            "axis" + number + " has one end with exact cut count and removal");
        check(outcome.equals(axis.outcome) && reason.equals(axis.reason) &&
            axis.afterWidth == afterWidth && axis.afterHeight == afterHeight,
            "axis" + number + " outcome, reason and retained/result dimensions are exact");
    }

    private static void cut(Capture capture, int axis, int index, int coordinate, int amount) {
        Cut cut = capture.axes[axis].cuts.get(index);
        check(cut.index == index && cut.coordinate == coordinate && cut.amount == amount,
            "axis" + axis + " cut" + index + " records literal coordinate and amount");
    }

    private static String inputFingerprint(Fixture fixture) {
        return planFingerprint(fixture.plan) + fixture.routed.geometryFingerprint();
    }

    private static String resultFingerprint(PcbPlacementCompactor.Routed routed) {
        return planFingerprint(routed.plan) + routed.layout.geometryFingerprint() + ":" +
            routed.layout.getRoutingExpansions() + ":" + routed.layout.getRawRoutingSegments() +
            ":" + routed.layout.getRoutingCongestionRejections();
    }

    private static String planFingerprint(PcbPlacementPlanner.Plan plan) {
        StringBuilder value = new StringBuilder();
        value.append(plan.outline.x).append(',').append(plan.outline.y).append(',')
            .append(plan.outline.width).append(',').append(plan.outline.height).append('|')
            .append(plan.candidate).append('|').append(plan.evaluations).append('|')
            .append(plan.courtyardArea).append('|').append(plan.demandArea).append('|')
            .append(plan.footprints.size()).append('|').append(plan.regions.size()).append(';');
        for (PcbFootprint footprint : plan.footprints)
            value.append(footprint.geometryFingerprint()).append(';');
        return value.toString();
    }

    private static String xProjection(PcbPlacementPlanner.Plan plan, PcbBoardLayout layout) {
        StringBuilder value = new StringBuilder();
        value.append(plan.outline.x).append(',').append(plan.outline.width).append('|');
        for (PcbFootprint footprint : plan.footprints) {
            PcbComponentPlacement placement = footprint.getPlacement();
            value.append(placement.getComponentId()).append('@').append(placement.getX())
                .append(',').append(placement.getWidth()).append(';');
            for (PcbPadPlacement pad : footprint.getPads())
                value.append(pad.getPadId()).append('@').append(pad.getX()).append(',')
                    .append(pad.getPadBounds().x).append(',').append(pad.getProbeBounds().x)
                    .append(';');
        }
        value.append('|').append(layout.getBoardOutline().x).append(',')
            .append(layout.getBoardOutline().width).append('|');
        for (PcbTraceGeometry trace : layout.getTraces()) {
            value.append(trace.getSourceId()).append(':');
            int[] xs = trace.getXPoints();
            for (int x : xs) value.append(x).append(',');
            value.append(';');
        }
        for (PcbBoardHole hole : layout.getHoles()) value.append(hole.id).append('@')
            .append(hole.x).append(';');
        return value.toString();
    }

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbPlacementConstraints constraints;
        final PcbPlacementPlanner.Plan plan;
        final PcbBoardLayout routed;
        Fixture(TroubleshootBoard board, PcbPlacementConstraints constraints,
                PcbPlacementPlanner.Plan plan, PcbBoardLayout routed) {
            this.board = board; this.constraints = constraints; this.plan = plan;
            this.routed = routed;
        }
    }

    private static final class RectanglePair {
        final Rectangle left, right;
        RectanglePair(Rectangle left, Rectangle right) { this.left = left; this.right = right; }
    }

    private static final class Cut {
        final int index, coordinate, amount;
        Cut(int index, int coordinate, int amount) {
            this.index = index; this.coordinate = coordinate; this.amount = amount;
        }
    }

    private static final class Axis {
        final int candidate, axis, beforeWidth, beforeHeight, traces, holes;
        final ArrayList<Cut> cuts = new ArrayList<Cut>();
        boolean ended;
        int cutCount, removed, afterWidth, afterHeight;
        String outcome, reason;
        Axis(int candidate, int axis, int beforeWidth, int beforeHeight, int traces, int holes) {
            this.candidate = candidate; this.axis = axis; this.beforeWidth = beforeWidth;
            this.beforeHeight = beforeHeight; this.traces = traces; this.holes = holes;
        }
    }

    private static final class Capture implements PcbPlacementCompactor.DiagnosticObserver {
        final Axis[] axes = new Axis[2];
        public void begin(int candidate, int axis, int beforeWidth, int beforeHeight,
                int traces, int holes) {
            check(axis >= 0 && axis < axes.length, "observer axis is within the two axes");
            check(axes[axis] == null, "observer begins each axis once");
            axes[axis] = new Axis(candidate, axis, beforeWidth, beforeHeight, traces, holes);
        }
        public void cut(int candidate, int axis, int index, int coordinate, int amount) {
            Axis run = axes[axis];
            check(run != null && !run.ended && run.candidate == candidate,
                "cut belongs to its active candidate axis");
            check(index == run.cuts.size(), "cut indices are zero-based and ordered");
            run.cuts.add(new Cut(index, coordinate, amount));
        }
        public void end(int candidate, int axis, int cutCount, int removed, String outcome,
                String reason, int afterWidth, int afterHeight) {
            Axis run = axes[axis];
            check(run != null && !run.ended && run.candidate == candidate,
                "end belongs to its active candidate axis exactly once");
            run.ended = true; run.cutCount = cutCount; run.removed = removed;
            run.outcome = outcome; run.reason = reason;
            run.afterWidth = afterWidth; run.afterHeight = afterHeight;
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private PcbCompactorCutObserverContractTest() { }
}
