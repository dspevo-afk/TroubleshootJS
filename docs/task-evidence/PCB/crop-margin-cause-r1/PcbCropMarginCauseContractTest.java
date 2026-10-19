package com.lushprojects.circuitjs1.client;

import java.util.TreeSet;
import java.util.Vector;

/**
 * Native-only crop-margin cause diagnostic for accepted production14/density13.
 * Routes only the frozen accepted interpretation and preserves its independent
 * source, graph, inventory, physical, probe, role, bounds, routing and work checks.
 * Crop telemetry is derived from the final routed layout without changing it.
 * A margin increase is an inferred signal only; this exporter does not attribute
 * it to the validated-outline fallback branch.
 */
public final class PcbCropMarginCauseContractTest {
    private static final int SAVED_VERSION = 14;
    private static final long[] DEFAULT_SEEDS = {-1022L, 7L};
    private static final long MAX_ACCEPTED_PLAN_EVALUATIONS = 6L * 60000L;
    private static int assertions;

    private PcbCropMarginCauseContractTest() { }

    public static void main(String[] args) {
        long[] seeds = DEFAULT_SEEDS;
        if (args.length > 0) {
            seeds = new long[args.length];
            for (int index = 0; index < args.length; index++)
                seeds[index] = Long.parseLong(args[index]);
        }
        runSeeds(seeds, "PCB crop margin cause contracts ");
    }

    static void runSeeds(long[] seeds, String marker) {
        check(seeds != null && seeds.length > 0, "nonempty declared diagnostic partition");
        TreeSet<Long> unique = new TreeSet<Long>();
        for (long seed : seeds) {
            check(seed == -1022L || seed == 7L || seed == 14L || seed == 4L ||
                    seed == 6L || seed == 42L,
                "diagnostic root belongs to the fixed six-case scope: " + seed);
            check(unique.add(Long.valueOf(seed)),
                "diagnostic partition has no duplicate root: " + seed);
        }
        int failures = 0;
        for (long seed : seeds) {
            try {
                exportRoot(seed);
            } catch (AssertionError failure) {
                failures++;
                reportFailure(seed, failure);
            } catch (RuntimeException failure) {
                failures++;
                reportFailure(seed, failure);
            }
        }
        if (failures != 0)
            throw new AssertionError("PCB crop margin cause group failed cases=" + failures +
                " of " + seeds.length);
        System.out.println("PASS: " + marker + "roots=" + seeds.length +
            " cases=" + seeds.length + " assertions=" + assertions +
            " diagnosticOnly=true metricAcceptance=NOT_CLAIMED");
    }

    private static void exportRoot(long seed) {
        long started = System.nanoTime();
        Rb56Plan plan = Rb56Plan.resolve(seed, SAVED_VERSION);
        TroubleshootBoard board = plan.board();
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(plan, board);
        String sourceBefore = PcbCanvasBoundaryContractTest.sourcePlanSha256(plan);
        String graphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(board);

        check(plan.seed == seed &&
                plan.placementInterpretationVersion == SAVED_VERSION,
            "explicit accepted14 plan: " + seed);
        check(board.getPlacementConstraints().placementDensityVersion == 13,
            "accepted14 retains density13: " + seed);
        check("ROLE_TRACE@1".equals(board.getPlacementConstraints()
                .getTraceWidthProfile().getIdentity()),
            "accepted14 retains its role-width profile: " + seed);
        verifyPlacementBounds(board, seed);

        check(board.getPlacementConstraints().requiresTwoLayerRouting,
            "accepted14 retains required P07 routing: " + seed);
        MediumBoardPhysicalPolicy.Result route = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(board, plan.layoutSeed, plan.routingSeed);
        check(route != null && route.accepted() && route.getFailure() == null &&
                route.getLayout() != null,
            "accepted14 admits and routes seed " + seed +
                (route == null ? "" : " " + route.toCanonical()));

        PcbBoardLayout layout = route.getLayout();
        GeneratedPhysicalAdmission admission = MediumBoardNormalAdmission.fromAcceptedRb56Route(
            board, route, plan.layoutSeed, plan.routingSeed);
        admission.requireConstruction(board, layout);
        verifyExactInventory(board, layout, seed);
        verifyTerminalSurfaces(board, layout, seed);
        verifyRoleWidths(board, layout, seed);

        String geometryBefore = layout.geometryFingerprint();
        layout.validateGeometry(board);
        layout.validateRoutingGeometry(board);
        layout.validateRouteQuality();
        new PcbTwoLayerRules(board, layout).validate(layout);
        PcbTraceClearance.validate(layout.getTraces());
        int largestProtectedEmptyRectangleArea =
            PcbFunctionalGroupingContractTest.reportWhitespace(
                board, layout, seed, SAVED_VERSION);
        Vector<PcbFootprint> finalFootprints =
            reconstructFinalFootprints(board, layout, seed);
        PcbPlacementPlanner.validate(board, board.getPlacementConstraints(),
            layout.getBoardOutline(), finalFootprints);

        MediumBoardPhysicalPolicy.Statistics stats = route.getStatistics();
        check(stats != null && stats.placementCandidates == 6,
            "accepted14 retains exactly six placement attempts: " + seed);
        check(stats.placementRejections >= 0 && stats.placementRejections <= 6,
            "accepted14 placement rejection count remains bounded: " + seed);
        check(stats.placementEvaluations > 0 &&
                stats.placementEvaluations <= MAX_ACCEPTED_PLAN_EVALUATIONS,
            "accepted placement evaluations remain within six existing 60000 caps: " + seed);
        check(stats.oneFaceAttempts >= 0 && stats.oneFaceAttempts <= 3,
            "accepted14 retains at most three one-face routes: " + seed);
        check(stats.twoLayerAttempts >= 1 && stats.twoLayerAttempts <= 3,
            "accepted14 retains at most three P07 routes: " + seed);
        check(stats.routeAttempts == stats.oneFaceAttempts + stats.twoLayerAttempts &&
                stats.routeAttempts <= 6,
            "accepted14 route attempts remain within the existing 3+3 cap: " + seed);
        check(stats.selectedPlacementAttempt >= 0 && stats.selectedPlacementAttempt < 6,
            "accepted route selects one of six placements: " + seed);
        check(stats.rankedPlacementAttempts != null &&
                stats.rankedPlacementAttempts.size() <= 6,
            "ranked placement list remains bounded: " + seed);
        TreeSet<Integer> ranked = new TreeSet<Integer>();
        boolean selectedRanked = false;
        for (Integer attempt : stats.rankedPlacementAttempts) {
            check(attempt != null && attempt.intValue() >= 0 && attempt.intValue() < 6,
                "ranked placement index remains in range: " + seed);
            check(ranked.add(attempt), "ranked placement attempts are unique: " + seed);
            selectedRanked |= attempt.intValue() == stats.selectedPlacementAttempt;
        }
        check(selectedRanked, "selected placement is present in the ranked attempts: " + seed);
        check(layout.getGenerationPlacementAttempts() == stats.placementCandidates,
            "layout retains the six-attempt generation receipt: " + seed);

        String geometryAfter = layout.geometryFingerprint();
        check(geometryBefore.equals(geometryAfter),
            "validation and telemetry preparation preserve routed geometry: " + seed);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(plan, board);
        String sourceAfter = PcbCanvasBoundaryContractTest.sourcePlanSha256(plan);
        String graphAfter = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        check(sourceBefore.equals(sourceAfter), "accepted14 plan is unchanged: " + seed);
        check(graphBefore.equals(graphAfter), "accepted14 board graph is unchanged: " + seed);

        Rectangle outline = layout.getBoardOutline();
        Rectangle occupied = layout.getOccupiedContentBounds();
        RequestedMargins margins = requestedMargins(board, occupied, finalFootprints, seed);
        int requestedMargin = margins.requestedMargin;
        int actualMargin = uniformActualMargin(outline, occupied, seed);
        check(actualMargin >= requestedMargin,
            "final outline margin meets the exact requested crop margin: " + seed);
        // This flag reports only that actual geometry exceeds the calculated request.
        // It does not identify which source branch selected the larger margin.
        boolean inferredFallbackUsed = actualMargin > requestedMargin;
        String geometryAfterMarginObservation = layout.geometryFingerprint();
        check(geometryAfter.equals(geometryAfterMarginObservation),
            "margin telemetry preserves routed geometry: " + seed);

        long area = (long) outline.width * outline.height;
        long copperManhattanLength = copperManhattanLength(layout);
        int vias = 0;
        for (PcbBoardHole hole : layout.getHoles())
            if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;

        StringBuilder json = new StringBuilder(5000);
        json.append("PCB_CROP_MARGIN_CASE {\"status\":\"PASS\"");
        stringField(json, "seed", Long.toString(seed));
        json.append(",\"version\":").append(SAVED_VERSION);
        json.append(",\"density\":").append(board.getPlacementConstraints()
            .placementDensityVersion);
        json.append(",\"parts\":").append(board.getComponentIds().size());
        json.append(",\"pads\":").append(board.getPadIds().size());
        json.append(",\"area\":").append(area);
        json.append(",\"width\":").append(outline.width);
        json.append(",\"height\":").append(outline.height);
        json.append(",\"largestProtectedEmptyRectangleArea\":")
            .append(largestProtectedEmptyRectangleArea);
        json.append(",\"copperManhattanLength\":").append(copperManhattanLength);
        json.append(",\"vias\":").append(vias);
        stringField(json, "geometrySha256", PlayerSessionFingerprint.of(geometryAfter));
        stringField(json, "sourcePlanSha256", sourceAfter);
        stringField(json, "liveBoardGraphSha256", graphAfter);
        stringField(json, "layoutSeed", Long.toString(plan.layoutSeed));
        stringField(json, "routingSeed", Long.toString(plan.routingSeed));
        json.append(",\"selectedAttempt\":").append(stats.selectedPlacementAttempt);
        integerArrayField(json, "rankedPlacementAttempts", stats.rankedPlacementAttempts);
        json.append(",\"acceptedPlanEvaluations\":").append(stats.placementEvaluations);
        json.append(",\"placementRejections\":").append(stats.placementRejections);
        json.append(",\"oneFaceAttempts\":").append(stats.oneFaceAttempts);
        json.append(",\"twoLayerAttempts\":").append(stats.twoLayerAttempts);
        json.append(",\"routeAttempts\":").append(stats.routeAttempts);
        stringField(json, "statisticsCanonical", stats.toCanonical());
        json.append(",\"fixedMarginFloor\":").append(margins.fixedMarginFloor);
        json.append(",\"accessEnvelopeMargin\":").append(margins.accessEnvelopeMargin);
        json.append(",\"requestedMargin\":").append(requestedMargin);
        json.append(",\"actualMargin\":").append(actualMargin);
        json.append(",\"occupiedX\":").append(occupied.x);
        json.append(",\"occupiedY\":").append(occupied.y);
        json.append(",\"occupiedWidth\":").append(occupied.width);
        json.append(",\"occupiedHeight\":").append(occupied.height);
        json.append(",\"outlineX\":").append(outline.x);
        json.append(",\"outlineY\":").append(outline.y);
        json.append(",\"inferredFallbackUsed\":").append(inferredFallbackUsed);
        json.append(",\"elapsedMillis\":").append(elapsedMillis).append('}');
        System.out.println(json.toString());
    }

    private static Vector<PcbFootprint> reconstructFinalFootprints(
            TroubleshootBoard board, PcbBoardLayout layout, long seed) {
        Vector<PcbFootprint> result = new Vector<PcbFootprint>();
        for (String componentId : board.getComponentIds()) {
            BoardComponent source = board.getComponent(componentId);
            PcbComponentPlacement placed = layout.getComponent(componentId);
            check(source != null && placed != null,
                "final footprint has its source and routed component: " + seed + "/" + componentId);
            Vector<String> orderedPadIds = source.getPadIds();
            check(orderedPadIds.size() == placed.getPhysicalGeometry().getTerminals().size(),
                "final component pad order has its declared terminal count: " +
                    seed + "/" + componentId);
            Vector<PcbPadPlacement> orderedPads = new Vector<PcbPadPlacement>();
            for (int index = 0; index < orderedPadIds.size(); index++) {
                String padId = orderedPadIds.get(index);
                PhysicalPackageGeometry.Terminal terminal =
                    placed.getPhysicalGeometry().getTerminal(index);
                check(padId.equals(componentId + "." + terminal.getTerminalId()),
                    "final component pad order follows package terminal order: " +
                        seed + "/" + padId);
                PcbPadPlacement pad = layout.getPad(padId);
                check(pad != null,
                    "final footprint resolves its routed pad: " + seed + "/" + padId);
                orderedPads.add(pad);
            }
            // The constructor verifies package ownership, ordered terminal identity,
            // placed pad geometry, and the matching final component pose.
            PcbFootprint footprint = new PcbFootprint(placed, orderedPads);
            check(footprint.getPads().size() == orderedPadIds.size(),
                "reconstructed final footprint retains every ordered pad: " +
                    seed + "/" + componentId);
            result.add(footprint);
        }
        check(result.size() == board.getComponentIds().size(),
            "reconstruction covers the complete routed component inventory: " + seed);
        return result;
    }

    private static final class RequestedMargins {
        final int fixedMarginFloor;
        final int accessEnvelopeMargin;
        final int requestedMargin;

        RequestedMargins(int fixedMarginFloor, int accessEnvelopeMargin) {
            this.fixedMarginFloor = fixedMarginFloor;
            this.accessEnvelopeMargin = accessEnvelopeMargin;
            this.requestedMargin = Math.max(fixedMarginFloor, accessEnvelopeMargin);
        }
    }

    private static RequestedMargins requestedMargins(TroubleshootBoard board,
            Rectangle occupied, Vector<PcbFootprint> finalFootprints, long seed) {
        int fixedMarginFloor = 26;
        for (PcbPlacementConstraints.Part demand :
                board.getPlacementConstraints().getParts())
            fixedMarginFloor = Math.max(fixedMarginFloor, demand.accessMargin + 30);
        int accessEnvelopeMargin = 0;
        for (PcbFootprint footprint : finalFootprints) {
            PcbPlacementConstraints.Part demand =
                board.getPlacementConstraints().get(
                    footprint.getPlacement().getComponentId());
            check(demand != null, "final footprint has its declared crop demand: " +
                seed + "/" + footprint.getPlacement().getComponentId());
            Rectangle access = PcbPlacementPlanner.envelope(footprint, demand.accessMargin);
            accessEnvelopeMargin = Math.max(accessEnvelopeMargin,
                occupied.x - access.x + 10);
            accessEnvelopeMargin = Math.max(accessEnvelopeMargin,
                occupied.y - access.y + 10);
            accessEnvelopeMargin = Math.max(accessEnvelopeMargin,
                access.x + access.width - occupied.x - occupied.width + 10);
            accessEnvelopeMargin = Math.max(accessEnvelopeMargin,
                access.y + access.height - occupied.y - occupied.height + 10);
        }
        check(fixedMarginFloor >= 26,
            "fixed requested margin retains its minimum26 floor: " + seed);
        check(accessEnvelopeMargin >= 0,
            "access envelope margin starts at zero: " + seed);
        RequestedMargins result = new RequestedMargins(fixedMarginFloor,
            accessEnvelopeMargin);
        check(result.requestedMargin == Math.max(fixedMarginFloor, accessEnvelopeMargin),
            "requested margin is the maximum of both source contributions: " + seed);
        return result;
    }
    private static int uniformActualMargin(Rectangle outline, Rectangle occupied, long seed) {
        long left = (long) occupied.x - outline.x;
        long top = (long) occupied.y - outline.y;
        long right = (long) outline.x + outline.width - occupied.x - occupied.width;
        long bottom = (long) outline.y + outline.height - occupied.y - occupied.height;
        check(left >= 0 && top >= 0 && right >= 0 && bottom >= 0,
            "occupied geometry remains inside the final outline: " + seed);
        check(left == top && left == right && left == bottom,
            "final crop uses one equal margin on all four sides: " + seed);
        long widthDifference = (long) outline.width - occupied.width;
        long heightDifference = (long) outline.height - occupied.height;
        check(widthDifference >= 0 && heightDifference >= 0 &&
                (widthDifference & 1L) == 0 && (heightDifference & 1L) == 0,
            "outline and occupied extents imply integral uniform margins: " + seed);
        long widthMargin = widthDifference / 2L;
        long heightMargin = heightDifference / 2L;
        check(widthMargin == heightMargin && widthMargin == left,
            "both final outline extents match their side margins: " + seed);
        check(widthMargin <= Integer.MAX_VALUE,
            "actual crop margin fits the exported integer field: " + seed);
        return (int) widthMargin;
    }
    private static void verifyExactInventory(TroubleshootBoard board,
            PcbBoardLayout layout, long seed) {
        TreeSet<String> components = new TreeSet<String>();
        for (PcbComponentPlacement part : layout.getComponents())
            check(components.add(part.getComponentId()),
                "unique routed component identity: " + seed + "/" + part.getComponentId());
        check(components.equals(new TreeSet<String>(board.getComponentIds())),
            "routed component inventory exactly matches source: " + seed);

        TreeSet<String> pads = new TreeSet<String>();
        for (PcbPadPlacement pad : layout.getPads())
            check(pads.add(pad.getPadId()), "unique routed pad identity: " + seed + "/" + pad.getPadId());
        check(pads.equals(new TreeSet<String>(board.getPadIds())),
            "routed pad inventory exactly matches source: " + seed);
    }

    private static void verifyPlacementBounds(TroubleshootBoard board, long seed) {
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        check(constraints.routingLayer == PcbCopperLayer.BOTTOM &&
                constraints.requiresTwoLayerRouting,
            "accepted14 retains its declared two-layer routing: " + seed);
        check(constraints.getBarriers().size() == 1,
            "exactly one declared isolation barrier: " + seed);
        PcbPlacementConstraints.Barrier barrier = constraints.getBarriers().get(0);
        check("PRIMARY".equals(barrier.firstDomain) && "SECONDARY".equals(barrier.secondDomain) &&
                barrier.clearance == 80,
            "literal declared PRIMARY/SECONDARY isolation remains 80: " + seed);
    }

    /** Check placed terminal/probe surfaces against the declared package, not the advice helper. */
    private static void verifyTerminalSurfaces(TroubleshootBoard board,
            PcbBoardLayout layout, long seed) {
        for (String id : board.getComponentIds()) {
            BoardComponent source = board.getComponent(id);
            PcbComponentPlacement component = layout.getComponent(id);
            check(component != null && source.getPhysicalPackage() == component.getPhysicalPackage(),
                "placed package retains the source owner: " + seed + "/" + id);
            PhysicalPackageGeometry geometry = component.getPhysicalGeometry();
            check(source.getPhysicalPackage().acceptsGeometry(geometry),
                "placed package uses a declared geometry variant: " + seed + "/" + id);
            PhysicalPackageGeometry.Placement expected = geometry.placedAt(component.getPose());
            check(geometry.getTerminals().size() == source.getPadIds().size(),
                "source and physical terminal counts match: " + seed + "/" + id);
            for (int index = 0; index < geometry.getTerminals().size(); index++) {
                PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
                String padId = id + "." + terminal.getTerminalId();
                BoardPad logical = board.getPad(padId);
                PcbPadPlacement pad = layout.getPad(padId);
                check(logical != null && id.equals(logical.getComponentId()) &&
                        terminal.getTerminalId().equals(logical.getTerminalId()) && pad != null,
                    "placed terminal has its exact logical endpoint: " + seed + "/" + padId);
                Point expectedPad = expected.getPadPoint(index);
                check(pad.getX() == expectedPad.x && pad.getY() == expectedPad.y &&
                        pad.getPadBounds().equals(expected.getPadBounds(index)) &&
                        pad.getProbeBounds().equals(expected.getBoardPadProbeBounds(index)),
                    "pad and probe bounds follow the declared terminal pose: " + seed + "/" + padId);
                check(pad.getEscapeDx() == expected.getEscapeDx(index) &&
                        pad.getEscapeDy() == expected.getEscapeDy(index) &&
                        pad.getEscapeLength() == expected.getEscapeLength(index) &&
                        pad.getAttachment() == terminal.getAttachment() &&
                        pad.getMountingSide() == component.getMountingSide(),
                    "terminal escape, attachment and face are preserved: " + seed + "/" + padId);
                check(component.getBoardPadProbeBounds(index)
                            .equals(expected.getBoardPadProbeBounds(index)) &&
                        component.getComponentLeadProbeBounds(index)
                            .equals(expected.getComponentLeadProbeBounds(index)) &&
                        component.getComponentLeadProbeBounds(index, true)
                            .equals(expected.getComponentLeadProbeBounds(index, true)),
                    "board-pad and connected/lifted lead probes retain package geometry: " +
                        seed + "/" + padId);
            }
        }
    }

    private static void verifyRoleWidths(TroubleshootBoard board,
            PcbBoardLayout layout, long seed) {
        PcbTraceWidthProfile profile = board.getPlacementConstraints().getTraceWidthProfile();
        check("ROLE_TRACE@1".equals(profile.getIdentity()),
            "routed interpretation exposes ROLE_TRACE@1: " + seed);
        check(profile.maximumWidth() == 13, "routed interpretation retains width13 maximum: " + seed);
        for (BoardNet.RoutingRole role : BoardNet.RoutingRole.values())
            check(profile.widthFor(role) == literalWidth(role),
                "literal routed interpretation width for role " + role + ": " + seed);
        for (PcbTraceGeometry trace : layout.getTraces()) {
            BoardNet net = board.getNet(trace.getNetId());
            check(net != null, "trace net is declared: " + seed + "/" + trace.getNetId());
            int width = literalWidth(net.getRoutingRole());
            check(trace.getWidth() == width,
                "routed role width is exact: " + seed + "/" + trace.getNetId());
        }
    }

    private static int literalWidth(BoardNet.RoutingRole role) {
        switch (role) {
        case HIGH_CURRENT: return 13;
        case SUPPLY:
        case RETURN: return 11;
        case CONTROL:
        case SIGNAL: return 9;
        default: throw new AssertionError("Unrecognized RB56 routing role: " + role);
        }
    }

    private static long copperManhattanLength(PcbBoardLayout layout) {
        long length = 0;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            int[] x = trace.getXPoints(), y = trace.getYPoints();
            check(x.length == y.length && x.length >= 2,
                "routed trace has matching path points: " + trace.getNetId());
            for (int index = 1; index < x.length; index++)
                length += Math.abs((long) x[index] - x[index - 1]) +
                    Math.abs((long) y[index] - y[index - 1]);
        }
        return length;
    }

    private static void integerArrayField(StringBuilder json, String name,
            Vector<Integer> values) {
        json.append(",\"").append(name).append("\":[");
        for (int index = 0; index < values.size(); index++) {
            if (index != 0) json.append(',');
            json.append(values.get(index).intValue());
        }
        json.append(']');
    }

    private static void stringField(StringBuilder json, String name, String value) {
        json.append(",\"").append(name).append("\":\"");
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' || ch == '\\') json.append('\\').append(ch);
            else if (ch == '\n') json.append("\\n");
            else if (ch == '\r') json.append("\\r");
            else if (ch == '\t') json.append("\\t");
            else if (ch < 0x20) {
                json.append("\\u00").append(Character.forDigit((ch >> 4) & 0xf, 16))
                    .append(Character.forDigit(ch & 0xf, 16));
            }
            else json.append(ch);
        }
        json.append('"');
    }

    private static void reportFailure(long seed, Throwable failure) {
        String message = String.valueOf(failure.getMessage()).replace('\r', ' ').replace('\n', ' ');
        System.out.println("PCB_CROP_MARGIN_FAILURE seed=" + seed + " type=" +
            failure.getClass().getName() + " message=" + message);
        failure.printStackTrace(System.out);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
