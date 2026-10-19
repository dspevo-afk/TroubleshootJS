package com.lushprojects.circuitjs1.client;

import java.util.TreeSet;
import java.util.Vector;

/**
 * Native-only cause diagnostic for frozen saved14 and rejected explicit15.
 * Requires the private reconstructed trial source; this file does not enable15.
 * Retains the archived physical/source/probe/work oracles and exports existing
 * policy statistics. No crop instrumentation or metric acceptance is claimed.
 * All five roots are informed regression data. Root-owned wrappers partition
 * the pair exports within the existing 60-second JVM bound.
 */
public final class PcbRouteRankingCauseContractTest {
    private static final int SAVED_VERSION = 14;
    private static final int TRIAL_VERSION = 15;
    private static final long[] DEFAULT_SEEDS = {-1022L, 7L};
    private static final long MAX_ACCEPTED_PLAN_EVALUATIONS = 6L * 60000L;
    private static int assertions;

    private PcbRouteRankingCauseContractTest() { }

    public static void main(String[] args) {
        long[] seeds = DEFAULT_SEEDS;
        if (args.length > 0) {
            seeds = new long[args.length];
            for (int index = 0; index < args.length; index++)
                seeds[index] = Long.parseLong(args[index]);
        }
        runSeeds(seeds, "PCB route ranking cause contracts ");
    }

    static void runSeeds(long[] seeds, String marker) {
        check(seeds != null && seeds.length > 0, "nonempty declared diagnostic partition");
        TreeSet<Long> unique = new TreeSet<Long>();
        for (long seed : seeds) {
            check(seed == -1022L || seed == 7L || seed == 14L || seed == 4L || seed == 6L,
                "diagnostic root belongs to the fixed five-case scope: " + seed);
            check(unique.add(Long.valueOf(seed)), "diagnostic partition has no duplicate root: " + seed);
        }
        int failures = 0;
        for (long seed : seeds) {
            for (int version : new int[] {SAVED_VERSION, TRIAL_VERSION}) {
                try {
                    exportRoot(seed, version);
                } catch (AssertionError failure) {
                    failures++;
                    reportFailure(seed, version, failure);
                } catch (RuntimeException failure) {
                    failures++;
                    reportFailure(seed, version, failure);
                }
            }
        }
        if (failures != 0)
            throw new AssertionError("PCB route ranking cause group failed cases=" + failures +
                " of " + seeds.length * 2);
        System.out.println("PASS: " + marker + "roots=" + seeds.length +
            " cases=" + seeds.length * 2 + " assertions=" + assertions +
            " diagnosticOnly=true metricAcceptance=NOT_CLAIMED");
    }

    private static void exportRoot(long seed, int version) {
        long started = System.nanoTime();
        check(version == SAVED_VERSION || version == TRIAL_VERSION,
            "diagnostic routes only frozen14 and rejected15");
        Rb56Plan savedPlan = Rb56Plan.resolve(seed, SAVED_VERSION);
        TroubleshootBoard savedBoard = savedPlan.board();
        Rb56Plan trialPlan = Rb56Plan.resolve(seed, TRIAL_VERSION);
        TroubleshootBoard trialBoard = trialPlan.board();
        Rb56Plan routedPlan = version == SAVED_VERSION ? savedPlan : trialPlan;
        TroubleshootBoard routedBoard = version == SAVED_VERSION ? savedBoard : trialBoard;
        String routedLabel = "version" + version;

        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(savedPlan, savedBoard);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(trialPlan, trialBoard);
        String savedSourceBefore = PcbCanvasBoundaryContractTest.sourcePlanSha256(savedPlan);
        String trialSourceBefore = PcbCanvasBoundaryContractTest.sourcePlanSha256(trialPlan);
        String savedGraphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard);
        String trialGraphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard);

        check(savedPlan.seed == seed && savedPlan.placementInterpretationVersion == SAVED_VERSION,
            "explicit saved14 comparison plan: " + seed);
        check(trialPlan.seed == seed && trialPlan.placementInterpretationVersion == TRIAL_VERSION,
            "explicit rejected15 comparison plan: " + seed);
        check(savedBoard.getPlacementConstraints().placementDensityVersion == 13,
            "saved14 retains density13: " + seed);
        check("ROLE_TRACE@1".equals(savedBoard.getPlacementConstraints()
                .getTraceWidthProfile().getIdentity()),
            "saved14 retains its role-width profile: " + seed);
        check(trialBoard.getPlacementConstraints().placementDensityVersion == 14,
            "rejected15 maps to density14: " + seed);
        check("ROLE_TRACE@1".equals(trialBoard.getPlacementConstraints()
                .getTraceWidthProfile().getIdentity()),
            "rejected15 retains ROLE_TRACE@1: " + seed);
        check(savedIntent(savedPlan).equals(savedIntent(trialPlan)),
            "saved14 and rejected15 use identical ordered source intent: " + seed);
        check(savedGraphBefore.equals(trialGraphBefore),
            "saved14 and rejected15 live source graphs match before routing: " + seed);
        verifyPlacementBounds(savedBoard, seed);
        verifyPlacementBounds(trialBoard, seed);

        check(routedBoard.getPlacementConstraints().requiresTwoLayerRouting,
            routedLabel + " retains required P07 routing: " + seed);
        MediumBoardPhysicalPolicy.Result route = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(routedBoard, routedPlan.layoutSeed, routedPlan.routingSeed);
        check(route != null && route.accepted() && route.getFailure() == null &&
                route.getLayout() != null,
            routedLabel + " admits and routes seed " + seed +
                (route == null ? "" : " " + route.toCanonical()));

        PcbBoardLayout layout = route.getLayout();
        GeneratedPhysicalAdmission admission = MediumBoardNormalAdmission.fromAcceptedRb56Route(
            routedBoard, route, routedPlan.layoutSeed, routedPlan.routingSeed);
        admission.requireConstruction(routedBoard, layout);
        verifyExactInventory(routedBoard, layout, seed);
        verifyTerminalSurfaces(routedBoard, layout, seed);
        verifyRoleWidths(routedBoard, layout, seed);

        String geometryBefore = layout.geometryFingerprint();
        layout.validateGeometry(routedBoard);
        layout.validateRoutingGeometry(routedBoard);
        layout.validateRouteQuality();
        new PcbTwoLayerRules(routedBoard, layout).validate(layout);
        PcbTraceClearance.validate(layout.getTraces());
        int largestProtectedEmptyRectangleArea =
            PcbFunctionalGroupingContractTest.reportWhitespace(
                routedBoard, layout, seed, version);

        MediumBoardPhysicalPolicy.Statistics stats = route.getStatistics();
        check(stats != null && stats.placementCandidates == 6,
            routedLabel + " retains exactly six placement attempts: " + seed);
        check(stats.placementRejections >= 0 && stats.placementRejections <= 6,
            routedLabel + " placement rejection count remains bounded: " + seed);
        check(stats.placementEvaluations > 0 &&
                stats.placementEvaluations <= MAX_ACCEPTED_PLAN_EVALUATIONS,
            "accepted placement evaluations remain within six existing 60000 caps: " + seed);
        check(stats.oneFaceAttempts >= 0 && stats.oneFaceAttempts <= 3,
            routedLabel + " retains at most three one-face routes: " + seed);
        check(stats.twoLayerAttempts >= 1 && stats.twoLayerAttempts <= 3,
            routedLabel + " retains at most three P07 routes: " + seed);
        check(stats.routeAttempts == stats.oneFaceAttempts + stats.twoLayerAttempts &&
                stats.routeAttempts <= 6,
            routedLabel + " route attempts remain within the existing 3+3 cap: " + seed);
        check(stats.selectedPlacementAttempt >= 0 && stats.selectedPlacementAttempt < 6,
            "accepted route selects one of six placements: " + seed);
        check(stats.rankedPlacementAttempts != null && stats.rankedPlacementAttempts.size() <= 6,
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
            "validation and whitespace reporting preserve routed geometry: " + seed);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(savedPlan, savedBoard);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(trialPlan, trialBoard);
        String savedSourceAfter = PcbCanvasBoundaryContractTest.sourcePlanSha256(savedPlan);
        String trialSourceAfter = PcbCanvasBoundaryContractTest.sourcePlanSha256(trialPlan);
        String savedGraphAfter = PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard);
        String trialGraphAfter = PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard);
        check(savedSourceBefore.equals(savedSourceAfter), "saved14 plan is unchanged: " + seed);
        check(trialSourceBefore.equals(trialSourceAfter), "rejected15 plan is unchanged: " + seed);
        check(savedGraphBefore.equals(savedGraphAfter), "saved14 board graph is unchanged: " + seed);
        check(trialGraphBefore.equals(trialGraphAfter), "rejected15 board graph is unchanged: " + seed);
        check(savedGraphAfter.equals(trialGraphAfter),
            routedLabel + " routing leaves both source graphs exactly equal: " + seed);

        Rectangle outline = layout.getBoardOutline();
        long area = (long) outline.width * outline.height;
        long copperManhattanLength = copperManhattanLength(layout);
        int vias = 0;
        for (PcbBoardHole hole : layout.getHoles())
            if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;

        StringBuilder json = new StringBuilder(5000);
        json.append("PCB_ROUTE_RANKING_CASE {\"status\":\"PASS\"");
        stringField(json, "seed", Long.toString(seed));
        json.append(",\"version\":").append(version);
        json.append(",\"density\":").append(routedBoard.getPlacementConstraints()
            .placementDensityVersion);
        json.append(",\"parts\":").append(routedBoard.getComponentIds().size());
        json.append(",\"pads\":").append(routedBoard.getPadIds().size());
        json.append(",\"area\":").append(area);
        json.append(",\"width\":").append(outline.width);
        json.append(",\"height\":").append(outline.height);
        json.append(",\"largestProtectedEmptyRectangleArea\":")
            .append(largestProtectedEmptyRectangleArea);
        json.append(",\"copperManhattanLength\":").append(copperManhattanLength);
        json.append(",\"vias\":").append(vias);
        stringField(json, "geometrySha256", PlayerSessionFingerprint.of(geometryAfter));
        stringField(json, "sourcePlanSha256",
            version == SAVED_VERSION ? savedSourceAfter : trialSourceAfter);
        stringField(json, "liveBoardGraphSha256",
            version == SAVED_VERSION ? savedGraphAfter : trialGraphAfter);
        stringField(json, "layoutSeed", Long.toString(routedPlan.layoutSeed));
        stringField(json, "routingSeed", Long.toString(routedPlan.routingSeed));
        json.append(",\"selectedAttempt\":").append(stats.selectedPlacementAttempt);
        integerArrayField(json, "rankedPlacementAttempts", stats.rankedPlacementAttempts);
        json.append(",\"acceptedPlanEvaluations\":").append(stats.placementEvaluations);
        json.append(",\"placementRejections\":").append(stats.placementRejections);
        json.append(",\"oneFaceAttempts\":").append(stats.oneFaceAttempts);
        json.append(",\"twoLayerAttempts\":").append(stats.twoLayerAttempts);
        json.append(",\"routeAttempts\":").append(stats.routeAttempts);
        stringField(json, "statisticsCanonical", stats.toCanonical());
        json.append(",\"elapsedMillis\":").append(elapsedMillis).append('}');
        System.out.println(json.toString());
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
            "both interpretations retain their declared two-layer routing: " + seed);
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

    /** Excludes only placement-interpretation metadata when comparing source intent. */
    static String savedIntent(Rb56Plan plan) {
        StringBuilder value = new StringBuilder();
        atom(value, Long.toString(plan.seed));
        atom(value, Long.toString(plan.layoutSeed));
        atom(value, Long.toString(plan.routingSeed));
        atom(value, plan.topology());
        atom(value, Integer.toString(plan.channelCount));
        atom(value, Integer.toString(plan.sensorInputFilterMask));
        atom(value, Integer.toString(plan.outputIndicatorMask));
        atom(value, plan.referenceArrangement.name());
        atom(value, Boolean.toString(plan.driverABjt));
        atom(value, Boolean.toString(plan.driverBBjt));
        atom(value, Boolean.toString(plan.hasFiveVoltIndicator));
        atom(value, Boolean.toString(plan.hasTwelveVoltIndicator));
        atom(value, Boolean.toString(plan.hasFiveVoltBleeder));
        Vector<Rb56Plan.Part> parts = plan.parts();
        value.append(parts.size()).append('[');
        for (Rb56Plan.Part part : parts) atom(value, part.canonical());
        value.append(']');
        return value.toString();
    }

    private static void atom(StringBuilder value, String text) {
        value.append(text.length()).append(':').append(text).append(';');
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

    private static void reportFailure(long seed, int version, Throwable failure) {
        String message = String.valueOf(failure.getMessage()).replace('\r', ' ').replace('\n', ' ');
        System.out.println("PCB_ROUTE_RANKING_FAILURE seed=" + seed + " version=" + version + " type=" +
            failure.getClass().getName() + " message=" + message);
        failure.printStackTrace(System.out);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
