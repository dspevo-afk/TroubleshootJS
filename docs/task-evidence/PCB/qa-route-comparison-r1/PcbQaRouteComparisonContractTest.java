package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.Vector;

/** Shared fixed-source fixture for the one-plan QA translation route diagnostic. */
final class PcbQaRouteComparisonContractTest {
    static final String ORIGINAL = "ORIGINAL";
    static final String TRANSLATED = "TRANSLATED";
    private static final long ROOT = 42L;
    private static final int VERSION = 15;
    private static final int DENSITY = 14;
    private static final int REQUESTED_PLACEMENT_ATTEMPT = 1;
    private static final int INTERNAL_PLANNER_ALGORITHM = 0;
    private static final int TRANSLATION_X = -300;
    private static final int TRANSLATION_Y = -30;
    private static final String SOURCE_PLAN_SHA =
        "4280267f0929521ab4be8664f74e841dfef6bef6c8b4a9552c18c621be685f59";
    private static final String SOURCE_GRAPH_SHA =
        "840dc1be8cd3ea75d6bfbeb62b9c9e2bf2fa6b718eea2aad557b899a9cacd96a";
    private static final String ACCEPTED_CASE_SOURCE_SHA =
        "fc08639a03ef95080c56d4b40d08fbcf2a14a99bde363693d38bf44bc17ab3d9";
    private static final String ORIGINAL_PLACEMENT_SHA =
        "d88e50e98d6c268d3bb3486f6c7c4f1377a4399d9ecf84dc9cfbe26b3a94b73b";
    private static final String ORIGINAL_GEOMETRY_SHA =
        "df556b8d89f1f91e15912f99af9238cd99368ce19e1929e1707b09c97758598a";
    private static final int EXPECTED_PARTS = 54;
    private static final int EXPECTED_PADS = 131;
    private static final long EXPECTED_AREA = 5519502L;
    private static final int EXPECTED_WIDTH = 3321;
    private static final int EXPECTED_HEIGHT = 1662;
    private static final int EXPECTED_LARGEST_PROTECTED_EMPTY_RECTANGLE = 178200;
    private static final long EXPECTED_COPPER_LENGTH = 42230L;
    private static final int EXPECTED_VIAS = 45;
    private static final long EXPECTED_SELECTED_QUALITY_MILLI = 270256005L;
    private static final int EXPECTED_ORDERINGS = 3;
    private static final int EXPECTED_TOTAL_EXPANSIONS = 1000000;
    private static final int EXPECTED_BRANCH_EXPANSIONS = 100000;
    private static final int EXPECTED_GRID_CELLS = 250000;
    private static final int EXPECTED_WORK_QUANTUM = 32768;
    private static int assertions;

    private PcbQaRouteComparisonContractTest() { }

    static void run(String variant) {
        check(ORIGINAL.equals(variant) || TRANSLATED.equals(variant),
            "recognized one-plan comparison variant");
        boolean translatedVariant = TRANSLATED.equals(variant);

        Rb56Plan sourcePlan = Rb56Plan.resolve(ROOT, VERSION);
        TroubleshootBoard board = sourcePlan.board();
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(sourcePlan, board);
        String sourcePlanBefore = PcbCanvasBoundaryContractTest.sourcePlanSha256(sourcePlan);
        String sourceGraphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        check(sourcePlan.seed == ROOT && sourcePlan.placementInterpretationVersion == VERSION,
            "fixed accepted source root and version");
        check(sourcePlan.layoutSeed == 5526715536693200536L,
            "exact accepted layout stream seed");
        check(sourcePlan.routingSeed == 4493419427463069983L,
            "exact accepted routing stream seed");
        check(sourcePlanBefore.equals(SOURCE_PLAN_SHA), "accepted source plan fingerprint");
        check(sourceGraphBefore.equals(SOURCE_GRAPH_SHA), "accepted live source graph fingerprint");
        check(board.getComponentIds().size() == EXPECTED_PARTS &&
                board.getPadIds().size() == EXPECTED_PADS,
            "accepted source inventory is54 parts and131 pads");
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        check(constraints.placementDensityVersion == DENSITY,
            "accepted15 maps to placement density14");
        check(MediumBoardPhysicalPolicy.selected(constraints),
            "source board selects the existing medium physical policy");
        check(constraints.requiresTwoLayerRouting,
            "source board requires the existing P07 route");
        check(REQUESTED_PLACEMENT_ATTEMPT == 1 &&
                constraints.placementDensityVersion >= 10 &&
                SeededPcbLayoutGenerator.hasRepeatedControlRegions(constraints),
            "existing generic planner predicate maps requested attempt1 to algorithm0");

        // Exactly one ordinary planner call supplies each process's route plan.
        PcbPlacementPlanner.Plan originalPlan = new PcbPlacementPlanner(
            StandardPcbFootprintProviders.createRegistry()).plan(board, constraints,
                sourcePlan.layoutSeed, REQUESTED_PLACEMENT_ATTEMPT);
        int plannerCalls = 1;
        String originalPlanSha = fingerprintSha(originalPlan);
        check(plannerCalls == 1, "one real planner call in this JVM");
        check(originalPlan.candidate == REQUESTED_PLACEMENT_ATTEMPT,
            "the plan retains requested placement attempt1 identity");
        check(originalPlan.evaluations > 0 && originalPlan.evaluations <= 60000,
            "single planner call remains within its existing evaluation cap");
        check(ORIGINAL_PLACEMENT_SHA.equals(originalPlanSha),
            "raw and returned original placement plan match the accepted fingerprint");
        PcbFootprint originalQa = footprint(originalPlan, "QA");
        check(originalQa != null, "accepted original plan includes QA");
        check(originalQa.getPlacement().getX() == 1690 &&
                originalQa.getPlacement().getY() == 1580 &&
                originalQa.getPlacement().getRotation() == PcbRotation.DEG_0 &&
                originalQa.getPlacement().getMountingSide() == PcbBoardSide.TOP,
            "original QA binding matches accepted source coordinates and pose");
        PcbPlacementPlanner.Plan suppliedPlan = translatedVariant ?
            translatedPlan(originalPlan, board) : originalPlan;
        String suppliedPlanShaBefore = fingerprintSha(suppliedPlan);
        PcbFootprint suppliedQa = footprint(suppliedPlan, "QA");
        check(suppliedQa.getPlacement().getX() == (translatedVariant ? 1390 : 1690) &&
                suppliedQa.getPlacement().getY() == (translatedVariant ? 1550 : 1580),
            "supplied QA binding matches the literal selected variant coordinates");
        verifyPlanComparison(originalPlan, suppliedPlan, board, translatedVariant);

        // Invalid translated input is a fixture failure, not a route rejection.
        PcbPlacementPlanner.validate(board, constraints, suppliedPlan.outline,
            suppliedPlan.footprints);
        String graphBeforeRoute = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        check(sourcePlanBefore.equals(PcbCanvasBoundaryContractTest.sourcePlanSha256(sourcePlan)),
            "source plan is unchanged before routing");
        check(sourceGraphBefore.equals(graphBeforeRoute),
            "source graph is unchanged before routing");

        SeededPcbLayoutGenerator.SuppliedMediumRoute route =
            new SeededPcbLayoutGenerator().routeSuppliedMediumTwoLayerPlan(board,
                sourcePlan.layoutSeed, sourcePlan.routingSeed,
                REQUESTED_PLACEMENT_ATTEMPT, suppliedPlan);
        MediumBoardPhysicalPolicy.Result result = route.result;
        MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
        verifyRouteReceipt(statistics, route, suppliedPlan, result.accepted());
        GeometryMetrics geometry = null;
        if (result.accepted()) {
            PcbBoardLayout layout = result.getLayout();
            layout.validateGeometry(board);
            layout.validateRoutingGeometry(board);
            layout.validateRouteQuality();
            new PcbTwoLayerRules(board, layout).validate(layout);
            PcbTraceClearance.validate(layout.getTraces());
            geometry = measureGeometry(board, layout, statistics);
            if (!translatedVariant) verifyAcceptedOriginalGeometry(geometry);
        } else {
            check(translatedVariant,
                "original P07 rejection is a hard qualification failure");
            check(result.getFailure() != null && result.getFailure().length() > 0,
                "translated rejection has a typed route reason");
            check("REJECTED".equals(statistics.outcome),
                "translated physical rejection remains explicitly rejected");
        }

        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(sourcePlan, board);
        String sourcePlanAfter = PcbCanvasBoundaryContractTest.sourcePlanSha256(sourcePlan);
        String sourceGraphAfter = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        String suppliedPlanShaAfter = fingerprintSha(suppliedPlan);
        boolean suppliedPlanUnchanged = suppliedPlanShaBefore.equals(suppliedPlanShaAfter);
        check(sourcePlanBefore.equals(sourcePlanAfter),
            "source plan remains unchanged through route and finalization");
        check(sourceGraphBefore.equals(sourceGraphAfter),
            "source graph remains unchanged through route and finalization");
        check(suppliedPlanUnchanged,
            "supplied placement plan remains unchanged through route and finalization");
        check(ORIGINAL_PLACEMENT_SHA.equals(fingerprintSha(originalPlan)),
            "original placement plan fingerprint remains pinned after route");

        System.out.println(jsonReceipt(variant, sourcePlan, board, sourcePlanBefore, sourceGraphBefore,
            sourcePlanAfter, sourceGraphAfter, originalPlanSha, suppliedPlanShaBefore,
            suppliedPlanShaAfter, originalPlan, suppliedPlan, originalQa, suppliedQa,
            translatedVariant, plannerCalls, suppliedPlanUnchanged, route,
            statistics, geometry, result.getFailure()));
        String variantLabel = translatedVariant ? "translated" : "original";
        System.out.println("PASS: PCB QA route " + variantLabel +
            " contracts: " + assertions + " assertions");
    }

    private static PcbPlacementPlanner.Plan translatedPlan(
            PcbPlacementPlanner.Plan original, TroubleshootBoard board) {
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        boolean replaced = false;
        for (PcbFootprint footprint : original.footprints) {
            if ("QA".equals(footprint.getPlacement().getComponentId())) {
                if (replaced) throw new AssertionError("Duplicate QA footprint in source plan");
                PcbComponentPlacement position = footprint.getPlacement();
                footprints.add(footprint.translated(
                    PcbCoordinateSystem.checkedAddBoardCoordinate(position.getX(), TRANSLATION_X),
                    PcbCoordinateSystem.checkedAddBoardCoordinate(position.getY(), TRANSLATION_Y)));
                replaced = true;
            } else {
                footprints.add(footprint);
            }
        }
        check(replaced, "translation replaced the declared QA footprint");
        return new PcbPlacementPlanner.Plan(original.outline, footprints, original.regions,
            original.courtyardArea, original.demandArea, original.candidate,
            original.evaluations);
    }

    private static void verifyPlanComparison(PcbPlacementPlanner.Plan original,
            PcbPlacementPlanner.Plan supplied, TroubleshootBoard board,
            boolean translatedVariant) {
        check(original.footprints.size() == EXPECTED_PARTS &&
                supplied.footprints.size() == EXPECTED_PARTS,
            "both input plans contain all54 component footprints");
        check(sameRectangle(original.outline, supplied.outline),
            "supplied plan keeps the original outline");
        check(original.courtyardArea == supplied.courtyardArea &&
                original.demandArea == supplied.demandArea &&
                original.candidate == supplied.candidate &&
                original.evaluations == supplied.evaluations,
            "courtyard, demand, planner algorithm and evaluation metadata stay fixed");
        check(sameRegions(original.regions, supplied.regions),
            "region identities, labels and memberships stay fixed");

        TreeMap<String,PcbFootprint> before = footprintMap(original);
        TreeMap<String,PcbFootprint> after = footprintMap(supplied);
        check(before.keySet().equals(after.keySet()),
            "component identity inventory stays exact in the supplied plan");
        int unchangedOthers = 0;
        for (String componentId : before.keySet()) {
            PcbFootprint first = before.get(componentId);
            PcbFootprint second = after.get(componentId);
            if ("QA".equals(componentId)) {
                verifyQaTranslation(first, second, board,
                    translatedVariant ? TRANSLATION_X : 0,
                    translatedVariant ? TRANSLATION_Y : 0);
            } else {
                check(first.geometryFingerprint().equals(second.geometryFingerprint()),
                    "non-QA footprint remains byte-for-byte geometrically equal: " + componentId);
                unchangedOthers++;
            }
        }
        check(unchangedOthers == EXPECTED_PARTS - 1,
            "all53 non-QA footprints are exactly unchanged");
        if (translatedVariant) {
            PcbComponentPlacement first = before.get("QA").getPlacement();
            PcbComponentPlacement second = after.get("QA").getPlacement();
            check(second.getX() == first.getX() + TRANSLATION_X &&
                    second.getY() == first.getY() + TRANSLATION_Y,
                "QA receives the literal recorded translation dx=-300 dy=-30");
        } else {
            check(before.get("QA").geometryFingerprint().equals(
                    after.get("QA").geometryFingerprint()),
                "original QA footprint is unchanged");
        }
    }

    private static void verifyQaTranslation(PcbFootprint first, PcbFootprint second,
            TroubleshootBoard board, int dx, int dy) {
        PcbComponentPlacement a = first.getPlacement();
        PcbComponentPlacement b = second.getPlacement();
        check("QA".equals(a.getComponentId()) && a.getComponentId().equals(b.getComponentId()),
            "QA component identity remains stable");
        check(b.getX() == a.getX() + dx && b.getY() == a.getY() + dy &&
                b.getWidth() == a.getWidth() && b.getHeight() == a.getHeight(),
            "QA component position moves by the literal delta and dimensions stay fixed");
        check(a.getRotation() == b.getRotation() &&
                a.getMountingSide() == b.getMountingSide(),
            "QA rotation and mounting side remain fixed");
        check(a.getPhysicalPackage() == b.getPhysicalPackage() &&
                a.getPhysicalGeometry() == b.getPhysicalGeometry() &&
                a.getGeometryVariantKey().equals(b.getGeometryVariantKey()) &&
                a.getGeometryTransformKey().equals(b.getGeometryTransformKey()) &&
                a.getGeometryContractVersionValue() == b.getGeometryContractVersionValue(),
            "QA physical package, geometry variant and contract remain fixed");
        check(translatedRectangle(a.getBounds(), b.getBounds(), dx, dy) &&
                translatedRectangle(a.getBodyBounds(), b.getBodyBounds(), dx, dy) &&
                translatedRectangle(a.getKeepOut(), b.getKeepOut(), dx, dy) &&
                translatedRectangle(a.getRoutingCourtyard(), b.getRoutingCourtyard(), dx, dy) &&
                translatedRectangle(a.getSelectionEnvelope(), b.getSelectionEnvelope(), dx, dy) &&
                translatedRectangle(a.getDragEnvelope(), b.getDragEnvelope(), dx, dy),
            "QA body, keepout, courtyard and interaction geometry translate as one footprint");

        Vector<PcbPadPlacement> firstPads = first.getPads();
        Vector<PcbPadPlacement> secondPads = second.getPads();
        check(firstPads.size() == secondPads.size() && !firstPads.isEmpty(),
            "QA pad count stays fixed");
        for (int index = 0; index < firstPads.size(); index++) {
            PcbPadPlacement p = firstPads.get(index), q = secondPads.get(index);
            BoardPad logical = board.getPad(p.getPadId());
            check(p.getPadId().equals(q.getPadId()) && logical != null &&
                    "QA".equals(logical.getComponentId()),
                "QA pad and component identities remain stable: " + p.getPadId());
            check(logical.getTerminalId() != null && logical.getNetId() != null,
                "QA source pad retains terminal and net mapping: " + p.getPadId());
            check(q.getX() == p.getX() + dx && q.getY() == p.getY() + dy,
                "QA pad center receives the literal translation: " + p.getPadId());
            check(q.getEscapeDx() == p.getEscapeDx() && q.getEscapeDy() == p.getEscapeDy() &&
                    q.getEscapeLength() == p.getEscapeLength() &&
                    q.getAttachment() == p.getAttachment() &&
                    q.getMountingSide() == p.getMountingSide() &&
                    q.getExposure() == p.getExposure(),
                "QA pad electrical endpoint policy stays fixed: " + p.getPadId());
            check(translatedRectangle(p.getPadBounds(), q.getPadBounds(), dx, dy) &&
                    translatedRectangle(p.getProbeBounds(), q.getProbeBounds(), dx, dy),
                "QA pad and probe bounds translate with its footprint: " + p.getPadId());
        }
    }

    private static boolean sameRegions(Vector<PcbLayoutRegion> first,
            Vector<PcbLayoutRegion> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            PcbLayoutRegion a = first.get(index), b = second.get(index);
            if (!a.id.equals(b.id) || !a.label.equals(b.label) ||
                    !a.getComponentIds().equals(b.getComponentIds())) return false;
        }
        return true;
    }

    private static TreeMap<String,PcbFootprint> footprintMap(
            PcbPlacementPlanner.Plan plan) {
        TreeMap<String,PcbFootprint> result = new TreeMap<String,PcbFootprint>();
        for (PcbFootprint footprint : plan.footprints) {
            String id = footprint.getPlacement().getComponentId();
            if (result.put(id, footprint) != null)
                throw new AssertionError("Duplicate planned footprint: " + id);
        }
        return result;
    }

    private static PcbFootprint footprint(PcbPlacementPlanner.Plan plan,
            String componentId) {
        PcbFootprint result = null;
        for (PcbFootprint footprint : plan.footprints)
            if (componentId.equals(footprint.getPlacement().getComponentId())) {
                if (result != null) throw new AssertionError("Duplicate planned footprint: " + componentId);
                result = footprint;
            }
        return result;
    }

    private static void verifyRouteReceipt(MediumBoardPhysicalPolicy.Statistics stats,
            SeededPcbLayoutGenerator.SuppliedMediumRoute route,
            PcbPlacementPlanner.Plan suppliedPlan, boolean accepted) {
        check(stats != null, "existing medium route statistics are present");
        check(MediumBoardPhysicalPolicy.IDENTITY.equals(stats.policyIdentity),
            "route reports the current medium policy identity");
        check(stats.placementCandidates == 1 && stats.placementRejections == 0,
            "statistics count one supplied plan and no generated placement rejections");
        check(stats.placementEvaluations == suppliedPlan.evaluations,
            "placement work is the supplied plan's single planner evaluation count");
        check(stats.rankedPlacementAttempts.isEmpty(),
            "diagnostic does not claim a historical six-plan ranking");
        check(stats.routeAttempts == 1 && stats.oneFaceAttempts == 0 &&
                stats.oneFaceSuccesses == 0 && stats.twoLayerAttempts == 1,
            "one ordinary P07 route ran and P05 did not run");
        check(stats.twoLayerSuccesses == (accepted ? 1 : 0),
            "P07 success count matches the physical outcome");
        check(stats.routingOrders >= 1 &&
                stats.routingOrders <= PcbLayerRoutingPrototype.ORDERINGS,
            "per-route ordering count remains within the existing cap");
        check(stats.routingExpansions >= 0 &&
                stats.routingExpansions <= PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            "per-route expansions remain within the existing total cap");
        check(route.routePhaseElapsedMillis >= 0,
            "existing route-phase elapsed time is reported independently");
        check(PcbLayerRoutingPrototype.ORDERINGS == EXPECTED_ORDERINGS &&
                PcbLayerRoutingPrototype.MAX_EXPANSIONS == EXPECTED_TOTAL_EXPANSIONS &&
                PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS == EXPECTED_BRANCH_EXPANSIONS &&
                PcbLayerRoutingPrototype.MAX_CELLS == EXPECTED_GRID_CELLS &&
                PcbLayerRoutingPrototype.WORK_QUANTUM == EXPECTED_WORK_QUANTUM,
            "P07 search caps match the unchanged declared limits");
        check(stats.routeOutcomes.size() == 1 &&
                stats.routeOutcomes.get(0).startsWith(
                    MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER + "@1="),
            "the only route outcome is P07 for supplied attempt1");
        check(accepted ? "SUCCESS".equals(stats.outcome) :
                "REJECTED".equals(stats.outcome),
            "existing medium statistics match the physical result");
        if (accepted) {
            check(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
                    stats.selectedRoutePolicy), "selected route is existing P07");
            check(stats.selectedPlacementAttempt == REQUESTED_PLACEMENT_ATTEMPT,
                "selected route retains supplied attempt1 identity");
            check(stats.selectedRouteQualityMilli >= 0,
                "successful route reports its final selected quality");
        } else {
            check(stats.selectedPlacementAttempt == -1,
                "rejected route is not reported as a selected physical route");
        }
    }

    private static GeometryMetrics measureGeometry(TroubleshootBoard board,
            PcbBoardLayout layout, MediumBoardPhysicalPolicy.Statistics stats) {
        Rectangle outline = layout.getBoardOutline();
        int largestVoid = PcbFunctionalGroupingContractTest.reportWhitespace(
            board, layout, ROOT, VERSION);
        long copperLength = copperManhattanLength(layout);
        int vias = 0;
        for (PcbBoardHole hole : layout.getHoles())
            if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
        GeometryMetrics metrics = new GeometryMetrics(layout.getComponents().size(),
            layout.getPads().size(), (long) outline.width * outline.height,
            outline.width, outline.height, largestVoid, copperLength, vias,
            PlayerSessionFingerprint.of(layout.geometryFingerprint()),
            stats.selectedRouteQualityMilli);
        check(metrics.parts == EXPECTED_PARTS && metrics.pads == EXPECTED_PADS,
            "routed geometry retains exact source parts and pads");
        check(metrics.width > 0 && metrics.height > 0 && metrics.area > 0,
            "routed geometry has a positive outline");
        check(metrics.selectedQualityMilli == routeQualityMilli(
                layout.getRouteQualityScore(board)),
            "selected quality is refreshed from final geometry");
        return metrics;
    }

    private static void verifyAcceptedOriginalGeometry(GeometryMetrics metrics) {
        check(metrics.parts == EXPECTED_PARTS, "original final part count is accepted exact54");
        check(metrics.pads == EXPECTED_PADS, "original final pad count is accepted exact131");
        check(metrics.area == EXPECTED_AREA && metrics.width == EXPECTED_WIDTH &&
                metrics.height == EXPECTED_HEIGHT,
            "original final outline matches accepted area and dimensions");
        check(metrics.largestProtectedEmptyRectangleArea ==
                EXPECTED_LARGEST_PROTECTED_EMPTY_RECTANGLE,
            "original final protected empty rectangle matches accepted metric");
        check(metrics.copperManhattanLength == EXPECTED_COPPER_LENGTH,
            "original final copper length matches accepted metric");
        check(metrics.vias == EXPECTED_VIAS,
            "original final via count matches accepted metric");
        check(ORIGINAL_GEOMETRY_SHA.equals(metrics.geometrySha256),
            "original final geometry matches the accepted SHA");
        check(metrics.selectedQualityMilli == EXPECTED_SELECTED_QUALITY_MILLI,
            "original final selected route quality matches the accepted value");
    }

    private static long copperManhattanLength(PcbBoardLayout layout) {
        long length = 0;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            int[] x = trace.getXPoints(), y = trace.getYPoints();
            check(x.length == y.length && x.length >= 2,
                "routed trace has matching path points: " + trace.getNetId());
            for (int index = 1; index < x.length; index++) {
                long dx = Math.abs((long) x[index] - x[index - 1]);
                long dy = Math.abs((long) y[index] - y[index - 1]);
                check(dx == 0 || dy == 0,
                    "routed trace uses Manhattan segments: " + trace.getNetId());
                length += dx + dy;
            }
        }
        return length;
    }

    /** Same canonical helper used by PcbPlacementScoreTermsContractTest. */
    private static String planFingerprint(PcbPlacementPlanner.Plan plan) {
        StringBuilder value = new StringBuilder();
        append(value, plan.outline.x + "," + plan.outline.y + "," + plan.outline.width + "," +
            plan.outline.height);
        append(value, Long.toString(plan.courtyardArea));
        append(value, Long.toString(plan.demandArea));
        append(value, Integer.toString(plan.candidate));
        append(value, Integer.toString(plan.evaluations));
        value.append(plan.footprints.size()).append('[');
        for (PcbFootprint footprint : plan.footprints)
            append(value, footprint.geometryFingerprint());
        value.append(plan.regions.size()).append('[');
        for (PcbLayoutRegion region : plan.regions) {
            append(value, region.id);
            append(value, region.label);
            Vector<String> ids = region.getComponentIds();
            value.append(ids.size()).append('[');
            for (String id : ids) append(value, id);
            value.append(']');
        }
        value.append(']');
        return value.toString();
    }

    private static String fingerprintSha(PcbPlacementPlanner.Plan plan) {
        return PlayerSessionFingerprint.of(planFingerprint(plan));
    }

    private static void append(StringBuilder value, String text) {
        value.append(text.length()).append(':').append(text).append(';');
    }

    private static boolean sameRectangle(Rectangle a, Rectangle b) {
        return a != null && b != null && a.x == b.x && a.y == b.y &&
            a.width == b.width && a.height == b.height;
    }

    private static boolean translatedRectangle(Rectangle a, Rectangle b,
            int dx, int dy) {
        return a != null && b != null && (long) a.x + dx == b.x &&
            (long) a.y + dy == b.y && a.width == b.width && a.height == b.height;
    }

    private static long routeQualityMilli(double quality) {
        if (Double.isNaN(quality) || Double.isInfinite(quality) || quality < 0) return -1;
        double scaled = quality * 1000.0;
        if (scaled >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.round(scaled);
    }

    private static String jsonReceipt(String variant, Rb56Plan sourcePlan,
            TroubleshootBoard board,
            String sourcePlanBefore, String sourceGraphBefore, String sourcePlanAfter,
            String sourceGraphAfter, String originalPlanShaBefore,
            String suppliedPlanShaBefore, String suppliedPlanShaAfter,
            PcbPlacementPlanner.Plan originalPlan, PcbPlacementPlanner.Plan suppliedPlan,
            PcbFootprint originalQa,
            PcbFootprint suppliedQa, boolean translatedVariant, int plannerCalls,
            boolean suppliedPlanUnchanged,
            SeededPcbLayoutGenerator.SuppliedMediumRoute route,
            MediumBoardPhysicalPolicy.Statistics stats, GeometryMetrics geometry,
            String rejectionReason) {
        StringBuilder out = new StringBuilder(7000);
        out.append("PCB_QA_ROUTE_CASE {");
        field(out, "schema", "pcb-qa-route-comparison-r1");
        field(out, "variant", variant);
        field(out, "physicalStatus", geometry == null ? "REJECTED" : "SUCCESS");
        out.append(",\"source\":{");
        stringField(out, "seed", Long.toString(sourcePlan.seed));
        out.append(",\"version\":").append(sourcePlan.placementInterpretationVersion)
            .append(",\"density\":").append(DENSITY);
        stringField(out, "acceptedSourceCase",
            "pocket-decision-cause-r1/accepted15-cases.json");
        stringField(out, "acceptedCaseSourceSha256", ACCEPTED_CASE_SOURCE_SHA);
        stringField(out, "sourcePlanSha256", sourcePlanBefore);
        stringField(out, "sourcePlanSha256After", sourcePlanAfter);
        stringField(out, "sourceGraphSha256", sourceGraphBefore);
        stringField(out, "sourceGraphSha256After", sourceGraphAfter);
        out.append(",\"sourcePlanUnchanged\":")
            .append(sourcePlanBefore.equals(sourcePlanAfter));
        out.append(",\"sourceGraphUnchanged\":")
            .append(sourceGraphBefore.equals(sourceGraphAfter));
        stringField(out, "layoutSeed", Long.toString(sourcePlan.layoutSeed));
        stringField(out, "routingSeed", Long.toString(sourcePlan.routingSeed));
        out.append('}');

        out.append(",\"placement\":{");
        out.append("\"requestedAttempt\":").append(REQUESTED_PLACEMENT_ATTEMPT)
            .append(",\"internalPlannerAlgorithm\":")
            .append(INTERNAL_PLANNER_ALGORITHM).append(",\"plannerCalls\":")
            .append(plannerCalls);
        stringField(out, "originalPlanSha256", originalPlanShaBefore);
        stringField(out, "originalPlanSha256After", fingerprintSha(originalPlan));
        stringField(out, "suppliedPlanSha256", suppliedPlanShaBefore);
        stringField(out, "suppliedPlanSha256After", suppliedPlanShaAfter);
        out.append(",\"suppliedPlanIsOriginal\":")
            .append(originalPlan == suppliedPlan)
            .append(",\"suppliedPlanUnchanged\":").append(suppliedPlanUnchanged);
        out.append(",\"candidateCount\":1,\"candidateAttemptsSupplied\":[1]");
        out.append(",\"recordedExistingOriginSequence\":6297");
        field(out, "candidateOrderMeaning",
            "single supplied fixture plan; not historical rank");
        out.append(",\"qaOnlyTranslated\":").append(translatedVariant)
            .append(",\"onlyQaFootprintTranslated\":").append(translatedVariant)
            .append(",\"allOther53FootprintsExact\":true")
            .append(",\"qaStableComponentPadNetBindings\":true")
            .append(",\"planMetadataUnchanged\":true")
            .append(",\"qaBefore\":");
        appendQaBinding(out, originalQa, board);
        out.append(",\"qaAfter\":");
        appendQaBinding(out, suppliedQa, board);
        out.append('}');

        out.append(",\"route\":{");
        field(out, "policy", MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER);
        out.append(",\"attempt\":1,\"routeAttempts\":").append(stats.routeAttempts)
            .append(",\"routeRequest\":\"P07_FULLER_TWO_LAYER@1\"")
            .append(",\"selectedRoute\":");
        if (geometry == null) out.append("null");
        else quote(out, stats.selectedRoutePolicy);
        out.append(",\"selectedPlacementAttempt\":")
            .append(stats.selectedPlacementAttempt);
        out.append(",\"p05Attempts\":").append(stats.oneFaceAttempts)
            .append(",\"p07Attempts\":").append(stats.twoLayerAttempts)
            .append(",\"policyIdentity\":");
        quote(out, stats.policyIdentity);
        out.append(",\"routeOutcomes\":");
        appendStringArray(out, stats.routeOutcomes);
        out.append(",\"orderings\":").append(stats.routingOrders)
            .append(",\"expansions\":").append(stats.routingExpansions)
            .append(",\"placementEvaluations\":").append(stats.placementEvaluations)
            .append(",\"placementCandidates\":").append(stats.placementCandidates)
            .append(",\"rankedPlacementAttempts\":[]")
            .append(",\"routePhaseElapsedMillis\":")
            .append(route.routePhaseElapsedMillis)
            .append(",\"routePhaseElapsedMeaning\":")
            .append("\"existing P07 begin and slice timing; excludes finalization\"")
            .append(",\"limits\":{\"orderings\":")
            .append(PcbLayerRoutingPrototype.ORDERINGS)
            .append(",\"expansions\":").append(PcbLayerRoutingPrototype.MAX_EXPANSIONS)
            .append(",\"branchExpansions\":")
            .append(PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS)
            .append(",\"gridCells\":").append(PcbLayerRoutingPrototype.MAX_CELLS)
            .append(",\"workQuantum\":").append(PcbLayerRoutingPrototype.WORK_QUANTUM)
            .append("}}");

        out.append(",\"geometry\":");
        if (geometry == null) {
            out.append("null");
        } else {
            out.append('{').append("\"parts\":").append(geometry.parts)
                .append(",\"pads\":").append(geometry.pads)
                .append(",\"area\":").append(geometry.area)
                .append(",\"width\":").append(geometry.width)
                .append(",\"height\":").append(geometry.height)
                .append(",\"largestProtectedEmptyRectangleArea\":")
                .append(geometry.largestProtectedEmptyRectangleArea)
                .append(",\"copperManhattanLength\":")
                .append(geometry.copperManhattanLength)
                .append(",\"vias\":").append(geometry.vias);
            stringField(out, "geometrySha256", geometry.geometrySha256);
            out.append(",\"selectedQualityMilli\":")
                .append(geometry.selectedQualityMilli).append('}');
        }
        out.append(",\"rejectionReason\":");
        if (rejectionReason == null) out.append("null"); else quote(out, rejectionReason);
        out.append(",\"comparisonScope\":");
        quote(out, "one supplied P07@1; no P05; historical six-plan selection not replayed");
        out.append('}');
        return out.toString();
    }

    private static void appendQaBinding(StringBuilder out, PcbFootprint footprint,
            TroubleshootBoard board) {
        PcbComponentPlacement placement = footprint.getPlacement();
        out.append('{');
        field(out, "componentId", placement.getComponentId());
        out.append(",\"x\":").append(placement.getX())
            .append(",\"y\":").append(placement.getY())
            .append(",\"width\":").append(placement.getWidth())
            .append(",\"height\":").append(placement.getHeight());
        field(out, "rotation", placement.getRotation().name());
        field(out, "mountingSide", placement.getMountingSide().name());
        field(out, "packageId", placement.getPhysicalPackage().getId());
        field(out, "geometryVariantKey", placement.getGeometryVariantKey());
        field(out, "geometryTransformKey", placement.getGeometryTransformKey());
        out.append(",\"geometryContractVersion\":")
            .append(placement.getGeometryContractVersionValue())
            .append(",\"pads\":[");
        Vector<PcbPadPlacement> pads = footprint.getPads();
        for (int index = 0; index < pads.size(); index++) {
            if (index > 0) out.append(',');
            PcbPadPlacement pad = pads.get(index);
            BoardPad logical = board.getPad(pad.getPadId());
            out.append('{');
            field(out, "padId", pad.getPadId());
            field(out, "componentId", logical.getComponentId());
            field(out, "terminalId", logical.getTerminalId());
            field(out, "netId", logical.getNetId());
            out.append(",\"x\":").append(pad.getX())
                .append(",\"y\":").append(pad.getY())
                .append(",\"escapeDx\":").append(pad.getEscapeDx())
                .append(",\"escapeDy\":").append(pad.getEscapeDy())
                .append(",\"escapeLength\":").append(pad.getEscapeLength());
            field(out, "attachment", pad.getAttachment().name());
            field(out, "mountingSide", pad.getMountingSide().name());
            field(out, "exposure", pad.getExposure().name());
            out.append(",\"padBounds\":");
            appendRectangle(out, pad.getPadBounds());
            out.append(",\"probeBounds\":");
            appendRectangle(out, pad.getProbeBounds());
            out.append('}');
        }
        out.append("]}");
    }

    private static void appendRectangle(StringBuilder out, Rectangle rectangle) {
        out.append("{\"x\":").append(rectangle.x)
            .append(",\"y\":").append(rectangle.y)
            .append(",\"width\":").append(rectangle.width)
            .append(",\"height\":").append(rectangle.height).append('}');
    }

    private static void appendStringArray(StringBuilder out, Vector<String> values) {
        out.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) out.append(',');
            quote(out, values.get(index));
        }
        out.append(']');
    }

    private static void field(StringBuilder out, String name, String value) {
        if (out.charAt(out.length() - 1) != '{') out.append(',');
        quote(out, name);
        out.append(':');
        quote(out, value);
    }

    private static void stringField(StringBuilder out, String name, String value) {
        if (out.charAt(out.length() - 1) != '{') out.append(',');
        quote(out, name);
        out.append(':');
        quote(out, value);
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');
        if (value != null) {
            for (int index = 0; index < value.length(); index++) {
                char ch = value.charAt(index);
                if (ch == '"' || ch == '\\') out.append('\\').append(ch);
                else if (ch == '\n') out.append("\\n");
                else if (ch == '\r') out.append("\\r");
                else if (ch == '\t') out.append("\\t");
                else out.append(ch);
            }
        }
        out.append('"');
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class GeometryMetrics {
        final int parts, pads, width, height, largestProtectedEmptyRectangleArea, vias;
        final long area, copperManhattanLength, selectedQualityMilli;
        final String geometrySha256;
        GeometryMetrics(int parts, int pads, long area, int width, int height,
                int largestProtectedEmptyRectangleArea, long copperManhattanLength,
                int vias, String geometrySha256, long selectedQualityMilli) {
            this.parts = parts;
            this.pads = pads;
            this.area = area;
            this.width = width;
            this.height = height;
            this.largestProtectedEmptyRectangleArea = largestProtectedEmptyRectangleArea;
            this.copperManhattanLength = copperManhattanLength;
            this.vias = vias;
            this.geometrySha256 = geometrySha256;
            this.selectedQualityMilli = selectedQualityMilli;
        }
    }
}
