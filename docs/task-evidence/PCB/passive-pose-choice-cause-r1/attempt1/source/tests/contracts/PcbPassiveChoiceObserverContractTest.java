package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Pure protocol fixtures for the bounded actual-visit observer. */
public final class PcbPassiveChoiceObserverContractTest {
    private static int assertions;
    private static final long ROOT_SEED = 42L;
    private static final long LAYOUT_SEED = 9001L;
    private static final int X = 100;
    private static final int Y = 200;

    private static final class Fixture {
        final PcbPassiveChoiceObserver.Capture capture;
        final PcbFootprint source;
        final PcbFootprint alternate;
        Fixture(PcbPassiveChoiceObserver.Capture value, PcbFootprint original,
                PcbFootprint turned) {
            capture = value; source = original; alternate = turned;
        }
    }

    private interface Attempt { void run(); }

    private PcbPassiveChoiceObserverContractTest() { }

    public static void main(String[] args) {
        Fixture sample = completeCapture(15, null, false, false, 3, false);
        String sourceFingerprint = sample.source.geometryFingerprint();
        check(sample.capture.complete(), "synthetic protocol sample is complete");
        String sampleJson = sample.capture.json();
        check(sampleJson.startsWith("{\"schema\":\"" +
                PcbPassiveChoiceObserver.SCHEMA + "\",\"marker\":\"" +
                PcbPassiveChoiceObserver.MARKER),
            "capture JSON begins with the versioned schema and marker");
        check(sampleJson.contains("\"compaction\":{\"pre\":") &&
                sampleJson.contains("\"status\":\"COMPLETE\""),
            "sample serializes valid compaction frames and COMPLETE status");
        check(sampleJson.contains("\"minimumVisitKey\":\"ring:0:1\""),
            "strict minimum ties retain the earliest visit");
        System.out.println("PCB_PASSIVE_CHOICE_PROTOCOL_SAMPLE " + sampleJson);

        lifecycleAndMissingFrames();
        fallbackAggregation();
        pairedFirstDivergence();
        scoreAndVisitBounds();
        itemBound();
        exportBound();
        check(sample.source.geometryFingerprint().equals(sourceFingerprint) &&
                sample.source.getPlacement().getX() == X &&
                sample.source.getPlacement().getY() == Y &&
                sample.source.getPlacement().getRotation() == PcbRotation.DEG_0,
            "capture serialization and paired fixtures leave source geometry immutable");
        System.out.println("PASS: PCB passive choice observer contracts assertions=" +
            assertions + " schema=" + PcbPassiveChoiceObserver.SCHEMA +
            " protocolSamples=1 syntheticOnly=true");
    }

    private static void lifecycleAndMissingFrames() {
        final PcbPassiveChoiceObserver.Capture active = PcbPassiveChoiceObserver.start(
            ROOT_SEED, LAYOUT_SEED, 15, null);
        expectFailure(new Attempt() { public void run() {
            PcbPassiveChoiceObserver.start(ROOT_SEED, LAYOUT_SEED, 15, null);
        }}, "a second active capture is rejected");
        PcbPassiveChoiceObserver.stop(active);
        expectFailure(new Attempt() { public void run() {
            PcbPassiveChoiceObserver.stop(active);
        }}, "stale capture handles are rejected");
        expectFailure(new Attempt() { public void run() {
            PcbPassiveChoiceObserver.start(ROOT_SEED, LAYOUT_SEED, 14, null);
        }}, "unknown placement interpretation is rejected");

        Fixture partial = setup(15, null, false);
        PcbPlacementPlanner.Plan plan = plan(partial.source, 0, 0);
        partial.capture.recordPlanSnapshot(plan, true);
        partial.capture.recordPlanSnapshot(plan, false);
        partial.capture.planEnd(plan);
        PcbPassiveChoiceObserver.stop(partial.capture);
        check(!partial.capture.complete() &&
                partial.capture.reasons.contains("PLACED_ITEM_COUNT_MISMATCH") &&
                partial.capture.reasons.contains("ITEM_FRAMES_INCOMPLETE"),
            "a plan with declarations but no item visit/selection frame is inconclusive");
    }

    private static void pairedFirstDivergence() {
        Fixture old = completeCapture(15, null, false, false, 3, false);
        Fixture trial = completeCapture(16, old.capture, true, true, 3, false);
        String json = trial.capture.json();
        check(trial.capture.complete(), "paired synthetic trial obeys capture protocol");
        check(json.contains("\"firstSelectedFootprintDivergence\":{\"componentId\":\"R1\""),
            "first selected-footprint difference is retained");
        check(json.contains("\"oldVisitCount\":3,\"trialVisitCount\":3") &&
                json.contains("\"visitArraysComplete\":true,\"exportOverflow\":false"),
            "divergence stores the actual old and trial visit counts");
        check(json.contains("\"comparable\":true") &&
                json.contains("\"sizingEqual\":false"),
            "local comparison retains distinct sizing and placement-context flags");
        check(json.contains("\"divergenceVisits\":{\"old\":[") &&
                json.contains("\"trial\":["),
            "complete bounded paired traces are exported for the first selected difference");
        check(json.contains("\"trialOldGeometryOriginVisited\":true"),
            "reference origin is checked against actual trial visits");
        check(json.contains("\"firstDivergence\":{\"stage\":\"sizing\""),
            "earliest sizing-context difference precedes local visit attribution");
    }

    private static void fallbackAggregation() {
        Fixture fallback = completeCapture(15, null, false, false, 3, true);
        String json = fallback.capture.json();
        check(fallback.capture.complete() &&
                json.contains("\"firstFallbackLegalKey\":\"fallback:0\""),
            "fallback stats retain the first legal fallback visit");
        check(json.contains("\"fallbackUsed\":true"),
            "selected summary marks the existing fallback path");
    }

    private static void scoreAndVisitBounds() {
        Fixture invalid = setup(15, null, false);
        prepareItem(invalid.capture, invalid.source, false);
        beginItem(invalid.capture, invalid.source);
        int event = invalid.capture.recordVisit("ring:0:0", "ring", 0, 0, 0,
            -1, X, Y, invalid.source, true);
        invalid.capture.recordScore(event, Double.NaN, false);
        invalid.capture.endItem(invalid.source, 0.0, 1);
        PcbPlacementPlanner.Plan invalidPlan = plan(invalid.source, 1, 1);
        finish(invalid.capture, invalidPlan);
        check(!invalid.capture.complete() &&
                invalid.capture.reasons.contains("NONFINITE_ACTUAL_SCORE"),
            "a non-finite score on an actual legal visit invalidates proof");

        Fixture overflow = setup(15, null, false);
        prepareItem(overflow.capture, overflow.source, false);
        beginItem(overflow.capture, overflow.source);
        for (int i = 0; i <= PcbPassiveChoiceObserver.MAX_VISITS; i++)
            overflow.capture.recordVisit("ring:" + i, "ring", i, i / 6,
                i % 6, -1, X, Y, overflow.source, false);
        overflow.capture.endItem(overflow.source, 0.0,
            PcbPassiveChoiceObserver.MAX_VISITS + 1);
        PcbPlacementPlanner.Plan overflowPlan = plan(overflow.source, 0,
            PcbPassiveChoiceObserver.MAX_VISITS + 1);
        finish(overflow.capture, overflowPlan);
        check(overflow.capture.visitOverflow && !overflow.capture.complete(),
            "the actual-visit cap is enforced and cannot be reported complete");
    }

    private static void itemBound() {
        PcbPassiveChoiceObserver.Capture capture = PcbPassiveChoiceObserver.start(
            ROOT_SEED, LAYOUT_SEED, 15, null);
        capture.planBegin(LAYOUT_SEED, 1, PcbPassivePosePolicy.LEGACY, 14, 128);
        capture.planParameters(1, 0, 128);
        PhysicalPackage.GeometryVariant variant =
            PhysicalPackages.AXIAL_RESISTOR.getGeometryVariant("SPAN_220");
        for (int ordinal = 0; ordinal <= PcbPassiveChoiceObserver.MAX_ITEMS; ordinal++) {
            String id = "R" + ordinal;
            BoardComponent component = component(id);
            PcbFootprint source = PcbFootprint.fromPhysicalPackage(component,
                new PcbPackagePose(X, Y, PcbRotation.DEG_0, PcbBoardSide.TOP),
                variant.getGeometry());
            PcbPlacementConstraints.Part part = new PcbPlacementConstraints.Part(
                id, "region", "Region", "domain",
                PcbPlacementConstraints.Anchor.NONE, 20);
            PcbPlacementPlanner.Item item = item(source, part, component, false, false);
            capture.recordDeclaration(ordinal, part, item,
                PcbPlacementConstraints.Anchor.NONE);
        }
        PcbPassiveChoiceObserver.stop(capture);
        check(capture.itemOverflow && !capture.complete(),
            "item metadata cap is enforced and cannot be reported complete");
    }

    private static void exportBound() {
        Fixture old = completeCapture(15, null, false, false, 513, false);
        Fixture trial = completeCapture(16, old.capture, true, true, 512, false);
        check(trial.capture.exportOverflow && !trial.capture.complete() &&
                trial.capture.divergenceOldVisits == null &&
                trial.capture.divergenceTrialVisits == null,
            "paired trace overflow is explicit INCONCLUSIVE and drops no proof as PASS");
        check(trial.capture.json().contains("\"exportOverflow\":true"),
            "export bound is represented in the capture record");
    }

    private static Fixture completeCapture(int version,
            PcbPassiveChoiceObserver.Capture reference, boolean eligible,
            boolean selectDifferent, int visitCount, boolean includeFallback) {
        Fixture fixture = setup(version, reference, eligible);
        PcbPassiveChoiceObserver.Capture capture = fixture.capture;
        PcbFootprint source = fixture.source;
        PcbFootprint selected = selectDifferent && fixture.alternate != null ?
            fixture.alternate.translated(X + 20, Y) : source;
        if (visitCount >= PcbPassiveChoiceObserver.MAX_EXPORT_VISITS) {
            Point selectedOrigin = fallbackPoint(visitCount - 1, 23);
            PcbFootprint selectedPose = selectDifferent ? fixture.alternate : source;
            selected = selectedPose.translated(selectedOrigin.x, selectedOrigin.y);
        }
        capture.planParameters(1, 0, 1);
        PcbPlacementConstraints.Part declaration = declaration();
        PcbPlacementPlanner.Item item = item(source, declaration,
            component(source.getPlacement().getComponentId()), version == 16, eligible);
        capture.recordDeclaration(0, declaration, item,
            PcbPlacementConstraints.Anchor.NONE);
        recordSizingAndOutline(capture, item);
        beginItem(capture, source);

        if (visitCount >= PcbPassiveChoiceObserver.MAX_EXPORT_VISITS) {
            capture.markFallbackSearch();
            capture.recordFallbackBounds(X, X + 22 * 40, Y, Y + 22 * 40,
                23, 23, 529L, 0);
            for (int ordinal = 0; ordinal < visitCount; ordinal++) {
                PcbFootprint pose = version == 16 ?
                    PcbPassivePosePolicy.forVisit(source, fixture.alternate, ordinal) : source;
                Point requested = fallbackPoint(ordinal, 23);
                boolean legal = ordinal == visitCount - 1;
                PcbFootprint trial = pose.translated(requested.x, requested.y);
                int event = capture.recordVisit("fallback:" + ordinal, "fallback",
                    ordinal, -1, -1, ordinal, requested.x, requested.y, trial, legal);
                if (legal) capture.recordScore(event, 1.0, true);
            }
        } else if (version == 15 && includeFallback) {
            capture.recordVisit("ring:0:0", "ring", 0, 0, 0, -1,
                X, Y, source, false);
            capture.recordVisit("ring:0:1", "ring", 1, 0, 1, -1,
                X, Y, source, false);
            capture.recordVisit("ring:0:2", "ring", 2, 0, 2, -1,
                X, Y, source, false);
            capture.markFallbackSearch();
            capture.recordFallbackBounds(100, 300, 200, 400, 6, 6, 36, 0);
            int fallback = capture.recordVisit("fallback:0", "fallback", 0,
                -1, -1, 0, X, Y, source, true);
            capture.recordScore(fallback, 1.25, true);
        } else {
            PcbFootprint[] poses = { source, fixture.alternate, source };
            for (int ordinal = 0; ordinal < visitCount; ordinal++) {
                PcbFootprint pose = version == 16 ? poses[ordinal % poses.length] : source;
                int px = pose == fixture.alternate ? X + 20 : X;
                PcbFootprint trial = pose.translated(px, Y);
                int event = capture.recordVisit("ring:" + (ordinal / 6) + ":" +
                    (ordinal % 6), "ring", ordinal,
                    ordinal / 6, ordinal % 6, -1, px, Y, trial, true);
                capture.recordScore(event, ordinal == 0 ? 2.0 : 1.0,
                    ordinal < 2);
            }
        }

        int actualEvents = visitCount + (includeFallback ? 1 : 0);
        capture.endItem(selected, includeFallback ? 1.25 : 1.0, actualEvents);
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(selected);
        PcbPlacementPlanner.Plan plan = new PcbPlacementPlanner.Plan(
            new Rectangle(100, 200, 2000, 1200), footprints,
            new Vector<PcbLayoutRegion>(), 100000L, item.sizingDemandArea,
            1, actualEvents);
        capture.recordPlanSnapshot(plan, true);
        capture.recordPlanSnapshot(plan, false);
        capture.planEnd(plan);
        PcbPassiveChoiceObserver.stop(capture);
        return fixture;
    }

    private static Fixture setup(int version,
            PcbPassiveChoiceObserver.Capture reference, boolean eligible) {
        BoardComponent component = component("R1");
        PhysicalPackage.GeometryVariant variant =
            PhysicalPackages.AXIAL_RESISTOR.getGeometryVariant("SPAN_220");
        PcbFootprint source = PcbFootprint.fromPhysicalPackage(component,
            new PcbPackagePose(X, Y, PcbRotation.DEG_0, PcbBoardSide.TOP),
            variant.getGeometry());
        PcbFootprint alternate = version == 16 && eligible ?
            PcbPassivePosePolicy.alternativeFor(component, source,
                PcbPassivePosePolicy.ORTHOGONAL, true) : null;
        PcbPassiveChoiceObserver.Capture capture = PcbPassiveChoiceObserver.start(
            ROOT_SEED, LAYOUT_SEED, version, reference);
        capture.planBegin(LAYOUT_SEED, 1,
            version == 16 ? PcbPassivePosePolicy.ORTHOGONAL :
                PcbPassivePosePolicy.LEGACY, 14, 1);
        return new Fixture(capture, source, alternate);
    }

    private static void recordSizingAndOutline(
            PcbPassiveChoiceObserver.Capture capture, PcbPlacementPlanner.Item item) {
        Rectangle court = item.footprint.getPlacement().getRoutingCourtyard();
        capture.recordSizingTotals(1, item.sizingDemandArea,
            (long)court.width * court.height,
            item.sizingEnvelopeWidth + PcbPlacementPlanner.CHANNEL,
            item.sizingEnvelopeHeight + PcbPlacementPlanner.CHANNEL, 0);
        capture.recordOutline(2400000L, 0, 1.55, 1.35, 3.0, 1800, 1200,
            new Rectangle(100, 200, 2000, 1200), -1, 0.0, 0.0, 0.0);
    }

    private static void prepareItem(PcbPassiveChoiceObserver.Capture capture,
            PcbFootprint source, boolean eligible) {
        capture.planParameters(1, 0, 1);
        PcbPlacementConstraints.Part part = declaration();
        PcbPlacementPlanner.Item item = item(source, part,
            component(source.getPlacement().getComponentId()), false, eligible);
        capture.recordDeclaration(0, part, item, PcbPlacementConstraints.Anchor.NONE);
        recordSizingAndOutline(capture, item);
    }

    private static void beginItem(PcbPassiveChoiceObserver.Capture capture,
            PcbFootprint source) {
        capture.beginItem(source, new Vector<PcbFootprint>(), false, null, null,
            new Point(X, Y), X, Y, X, Y, null, 0, X, Y, false, -1, null,
            new Vector<Point>(), null, null, 0);
    }

    private static Point fallbackPoint(int ordinal, int columns) {
        int row = ordinal / columns;
        int column = ordinal % columns;
        if ((row & 1) != 0) column = columns - 1 - column;
        return new Point(X + column * 40, Y + row * 40);
    }

    private static PcbPlacementPlanner.Plan plan(PcbFootprint footprint,
            int evaluations, long demandArea) {
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(footprint);
        return new PcbPlacementPlanner.Plan(new Rectangle(100, 200, 2000, 1200),
            footprints, new Vector<PcbLayoutRegion>(), 100000L, demandArea, 1,
            evaluations);
    }

    private static void finish(PcbPassiveChoiceObserver.Capture capture,
            PcbPlacementPlanner.Plan plan) {
        capture.recordPlanSnapshot(plan, true);
        capture.recordPlanSnapshot(plan, false);
        capture.planEnd(plan);
        PcbPassiveChoiceObserver.stop(capture);
    }

    private static PcbPlacementPlanner.Item item(PcbFootprint source,
            PcbPlacementConstraints.Part part, BoardComponent component,
            boolean policy1, boolean eligible) {
        return new PcbPlacementPlanner.Item(source, part, component,
            policy1 ? PcbPassivePosePolicy.ORTHOGONAL : PcbPassivePosePolicy.LEGACY,
            eligible);
    }

    private static PcbPlacementConstraints.Part declaration() {
        return new PcbPlacementConstraints.Part("R1", "region", "Region",
            "domain", PcbPlacementConstraints.Anchor.NONE, 20);
    }

    private static BoardComponent component(String id) {
        PhysicalPackage physical = PhysicalPackages.AXIAL_RESISTOR;
        BoardComponent component = new BoardComponent(id, physical.getId(), physical, id);
        for (String terminal : physical.getTerminalIds())
            component.addPadId(id + "." + terminal);
        return component;
    }

    private static void expectFailure(Attempt attempt, String message) {
        boolean rejected = false;
        try { attempt.run(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
