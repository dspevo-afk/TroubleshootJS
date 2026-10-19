package com.lushprojects.circuitjs1.client;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Native-only paired geometry contract for the informed post-selection edge trim.
 * The saved14 route is compared with explicit15 after both routes independently
 * complete. This contract checks source and physical identity, then derives the
 * expected rigid translation with literal coordinate arithmetic; it never calls
 * the compaction or translation helpers to construct expected geometry.
 */
public final class PcbPostSelectionEdgeTrimContractTest {
    private static final int SAVED_VERSION = 14;
    private static final int TRIAL_VERSION = 15;
    private static final long[] DEFAULT_SEEDS = {-1017L, -1004L};
    private static final long MAX_ACCEPTED_PLAN_EVALUATIONS = 6L * 60000L;
    private static final long[] FIXED_ROOTS = {
        -1017L, -1004L, -1022L, 0L, 17L, 42L, 1L, 7L, 5L, 14L, 2L, 3L, 4L, 6L
    };
    private static int assertions;

    private PcbPostSelectionEdgeTrimContractTest() { }

    public static void main(String[] args) {
        long[] seeds = DEFAULT_SEEDS;
        if (args.length > 0) {
            seeds = new long[args.length];
            for (int index = 0; index < args.length; index++)
                seeds[index] = Long.parseLong(args[index]);
        }
        runSeeds(seeds, "PCB post-selection edge trim contracts ");
    }

    /** Root-owned wrappers pass at most two fixed roots per JVM. */
    static void runSeeds(long[] seeds, String marker) {
        check(seeds != null && seeds.length > 0, "nonempty declared root partition");
        TreeSet<Long> unique = new TreeSet<Long>();
        for (long seed : seeds) {
            check(isFixedRoot(seed), "root belongs to the fixed informed14-case scope: " + seed);
            check(unique.add(Long.valueOf(seed)), "partition has no duplicate root: " + seed);
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
            throw new AssertionError("PCB post-selection edge trim group failed cases=" +
                failures + " of " + seeds.length);
        System.out.println("PASS: " + marker + "roots=" + seeds.length +
            " pairs=" + seeds.length + " rows=" + (seeds.length * 2) +
            " assertions=" + assertions +
            " diagnosticOnly=true metricAcceptance=NOT_CLAIMED");
    }

    private static boolean isFixedRoot(long seed) {
        for (long fixed : FIXED_ROOTS)
            if (fixed == seed) return true;
        return false;
    }

    private static void exportRoot(long seed) {
        Rb56Plan savedPlan = Rb56Plan.resolve(seed, SAVED_VERSION);
        Rb56Plan trialPlan = Rb56Plan.resolve(seed, TRIAL_VERSION);
        TroubleshootBoard savedBoard = savedPlan.board();
        TroubleshootBoard trialBoard = trialPlan.board();

        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(savedPlan, savedBoard);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(trialPlan, trialBoard);
        check(savedPlan.seed == seed &&
                savedPlan.placementInterpretationVersion == SAVED_VERSION,
            "explicit saved14 plan: " + seed);
        check(trialPlan.seed == seed &&
                trialPlan.placementInterpretationVersion == TRIAL_VERSION,
            "explicit trial15 plan: " + seed);
        check(savedBoard.getPlacementConstraints().placementDensityVersion == 13,
            "saved14 retains density13: " + seed);
        check(trialBoard.getPlacementConstraints().placementDensityVersion == 14,
            "trial15 maps to density14: " + seed);
        check("ROLE_TRACE@1".equals(savedBoard.getPlacementConstraints()
                .getTraceWidthProfile().getIdentity()) &&
                "ROLE_TRACE@1".equals(trialBoard.getPlacementConstraints()
                .getTraceWidthProfile().getIdentity()),
            "both interpretations retain ROLE_TRACE@1: " + seed);
        check(savedIntent(savedPlan).equals(savedIntent(trialPlan)),
            "saved14 and trial15 have identical ordered source intent: " + seed);
        check(savedPlan.layoutSeed == trialPlan.layoutSeed &&
                savedPlan.routingSeed == trialPlan.routingSeed,
            "paired routes use the same placement and routing seeds: " + seed);

        String sourceIntentSha = PlayerSessionFingerprint.of(savedIntent(savedPlan));
        String savedSourceBefore = PcbCanvasBoundaryContractTest.sourcePlanSha256(savedPlan);
        String trialSourceBefore = PcbCanvasBoundaryContractTest.sourcePlanSha256(trialPlan);
        String savedGraphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard);
        String trialGraphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard);
        check(savedGraphBefore.equals(trialGraphBefore),
            "paired board source graphs match before routing: " + seed);
        verifyPlacementBounds(savedBoard, seed, "saved14");
        verifyPlacementBounds(trialBoard, seed, "trial15");
        verifyDemandEquality(savedBoard, trialBoard, seed);

        Observation saved = routeAndObserve(seed, SAVED_VERSION, savedPlan, savedBoard,
            sourceIntentSha, savedSourceBefore, savedGraphBefore);
        Observation trial = routeAndObserve(seed, TRIAL_VERSION, trialPlan, trialBoard,
            sourceIntentSha, trialSourceBefore, trialGraphBefore);

        check(saved.sourceIntentSha.equals(trial.sourceIntentSha),
            "paired source-intent fingerprints match across interpretations: " + seed);
        check(saved.graphAfter.equals(trial.graphAfter),
            "both route generations leave their equal source graphs identical: " + seed);
        comparePolicyStatistics(saved, trial, seed);
        compareRigidGeometry(saved, trial, seed);

        System.out.println(saved.json);
        System.out.println(trial.json);
    }

    static String savedIntentShaForCurrent15(Rb56Plan plan) {
        if (plan == null || plan.placementInterpretationVersion != TRIAL_VERSION)
            throw new IllegalArgumentException("Current15 source intent requires explicit version15");
        return PlayerSessionFingerprint.of(savedIntent(plan));
    }

    /** Native-only DTO; retains the actual issued admission identity without reissuing it. */
    static final class PosePolicyObservation {
        final String json, admissionIdentity;
        final PcbBoardLayout layout;
        final MediumBoardPhysicalPolicy.Result route;
        PosePolicyObservation(Observation observation) {
            json=observation.json;layout=observation.layout;route=observation.route;
            admissionIdentity=observation.admissionIdentity;
        }
    }
    static String sourceIntentShaForPosePolicy(Rb56Plan plan) {
        if (plan == null || (plan.placementInterpretationVersion != 15 &&
                plan.placementInterpretationVersion != 16 &&
                plan.placementInterpretationVersion != 17))
            throw new IllegalArgumentException("Only explicit15/16/17 source intent is supported");
        return PlayerSessionFingerprint.of(savedIntent(plan));
    }
    static PosePolicyObservation routePosePolicy(long seed, int version, Rb56Plan plan,
            TroubleshootBoard board, String intent, String source, String graph) {
        PcbPassivePoseSourceContractTest.expected(seed); // Fixed14 informed cases only.
        if ((version != 15 && version != 16 && version != 17) || plan == null || board == null ||
                plan.seed != seed || plan.placementInterpretationVersion != version ||
                board.getPlacementConstraints().placementDensityVersion != 14 ||
                board.getPlacementConstraints().passivePosePolicyVersion != (version == 15 ? 0 : version == 16 ? 1 : 2) ||
                !sourceIntentShaForPosePolicy(plan).equals(intent) ||
                !PcbCanvasBoundaryContractTest.sourcePlanSha256(plan).equals(source) ||
                !PcbCanvasBoundaryContractTest.liveGraphSha256(board).equals(graph))
            throw new IllegalArgumentException("Pose observation requires its exact bound15/16/17 source");
        return new PosePolicyObservation(routeAndObserveCore(seed, version, plan, board,
            intent, source, graph, version == 15 ? "saved15" : version == 16 ? "pose16" : "size17", System.nanoTime()));
    }

    private static Observation routeAndObserve(long seed, int version, Rb56Plan plan,
            TroubleshootBoard board, String sourceIntentSha, String sourceBefore,
            String graphBefore) {
        long started = System.nanoTime();
        String label = version == SAVED_VERSION ? "saved14" : "trial15";
        check(version == SAVED_VERSION || version == TRIAL_VERSION,
            "only saved14 and explicit trial15 are routed");
        return routeAndObserveCore(seed, version, plan, board, sourceIntentSha,
            sourceBefore, graphBefore, label, started);
    }

    private static Observation routeAndObserveCore(long seed, int version, Rb56Plan plan,
            TroubleshootBoard board, String sourceIntentSha, String sourceBefore,
            String graphBefore, String label, long started) {
        check(board.getPlacementConstraints().requiresTwoLayerRouting,
            label + " retains required P07 routing: " + seed);

        MediumBoardPhysicalPolicy.Result route = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(board, plan.layoutSeed, plan.routingSeed);
        check(route != null && route.accepted() && route.getFailure() == null &&
                route.getLayout() != null,
            label + " admits and routes seed " + seed +
                (route == null ? "" : " " + route.toCanonical()));
        PcbBoardLayout layout = route.getLayout();
        GeneratedPhysicalAdmission admission = MediumBoardNormalAdmission.fromAcceptedRb56Route(
            board, route, plan.layoutSeed, plan.routingSeed);
        admission.requireConstruction(board, layout);
        verifyExactInventory(board, layout, seed, label);
        verifyTerminalSurfaces(board, layout, seed, label);
        verifyRoleWidths(board, layout, seed, label);

        String geometryBefore = layout.geometryFingerprint();
        layout.validateGeometry(board);
        layout.validateRoutingGeometry(board);
        layout.validateRouteQuality();
        new PcbTwoLayerRules(board, layout).validate(layout);
        PcbTraceClearance.validate(layout.getTraces());
        int largestProtectedEmptyRectangleArea =
            PcbFunctionalGroupingContractTest.reportWhitespace(board, layout, seed, version);
        Vector<PcbFootprint> finalFootprints = reconstructFinalFootprints(board, layout,
            seed, label);
        PcbPlacementPlanner.validate(board, board.getPlacementConstraints(),
            layout.getBoardOutline(), finalFootprints);

        MediumBoardPhysicalPolicy.Statistics stats = route.getStatistics();
        verifyWorkBounds(stats, layout, seed, label);
        long recomputedQuality = routeQualityMilli(layout.getRouteQualityScore(board));
        check(stats.selectedRouteQualityMilli == recomputedQuality,
            label + " selected quality matches the final routed geometry: " + seed +
                " recorded=" + stats.selectedRouteQualityMilli +
                " recomputed=" + recomputedQuality);

        String geometryAfter = layout.geometryFingerprint();
        check(geometryBefore.equals(geometryAfter),
            label + " validation and telemetry preserve routed geometry: " + seed);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(plan, board);
        String sourceAfter = PcbCanvasBoundaryContractTest.sourcePlanSha256(plan);
        String graphAfter = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        check(sourceBefore.equals(sourceAfter), label + " source plan is unchanged: " + seed);
        check(graphBefore.equals(graphAfter), label + " board source graph is unchanged: " + seed);

        Rectangle outline = layout.getBoardOutline();
        Rectangle occupied = layout.getOccupiedContentBounds();
        MarginFacts margins = marginFacts(board, layout, finalFootprints, seed, label);
        long area = (long) outline.width * outline.height;
        long copperLength = copperManhattanLength(layout);
        int vias = viaCount(layout);
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;
        String json = jsonRow(seed, version, board, plan, layout, stats,
            largestProtectedEmptyRectangleArea, area, copperLength, vias,
            margins, sourceIntentSha, sourceAfter, graphAfter, elapsedMillis);
        return new Observation(seed, version, plan, board, route, layout, stats,
            sourceIntentSha, sourceAfter, graphAfter, finalFootprints, margins,
            area, copperLength, vias, json, admission.identity());
    }

    private static void verifyWorkBounds(MediumBoardPhysicalPolicy.Statistics stats,
            PcbBoardLayout layout, long seed, String label) {
        check(stats != null && stats.placementCandidates == 6,
            label + " retains exactly six placement attempts: " + seed);
        check(stats.placementRejections >= 0 && stats.placementRejections <= 6,
            label + " placement rejections remain within six candidates: " + seed);
        check(stats.placementEvaluations > 0 &&
                stats.placementEvaluations <= MAX_ACCEPTED_PLAN_EVALUATIONS,
            label + " placement evaluations remain within six existing 60000 caps: " + seed);
        check(stats.oneFaceAttempts >= 0 && stats.oneFaceAttempts <= 3,
            label + " retains at most three one-face routes: " + seed);
        check(stats.twoLayerAttempts >= 1 && stats.twoLayerAttempts <= 3,
            label + " retains one to three P07 routes: " + seed);
        check(stats.routeAttempts == stats.oneFaceAttempts + stats.twoLayerAttempts &&
                stats.routeAttempts <= 6,
            label + " route attempts remain within the existing 3+3 cap: " + seed);
        check(stats.selectedPlacementAttempt >= 0 && stats.selectedPlacementAttempt < 6,
            label + " selects one of six placements: " + seed);
        check(stats.rankedPlacementAttempts != null &&
                stats.rankedPlacementAttempts.size() <= 6,
            label + " ranked placement list remains bounded: " + seed);
        TreeSet<Integer> ranked = new TreeSet<Integer>();
        boolean selectedRanked = false;
        for (Integer attempt : stats.rankedPlacementAttempts) {
            check(attempt != null && attempt.intValue() >= 0 && attempt.intValue() < 6,
                label + " ranked placement index remains in range: " + seed);
            check(ranked.add(attempt), label + " ranked placement attempts are unique: " + seed);
            selectedRanked |= attempt.intValue() == stats.selectedPlacementAttempt;
        }
        check(selectedRanked, label + " selected placement is in the ranked attempts: " + seed);
        check(layout.getGenerationPlacementAttempts() == stats.placementCandidates,
            label + " layout retains its placement attempt receipt: " + seed);
    }

    private static void comparePolicyStatistics(Observation saved, Observation trial,
            long seed) {
        MediumBoardPhysicalPolicy.Statistics first = saved.stats;
        MediumBoardPhysicalPolicy.Statistics second = trial.stats;
        check(first.outcome.equals(second.outcome) &&
                first.selectedRoutePolicy.equals(second.selectedRoutePolicy),
            "paired routes retain the same successful selected policy: " + seed);
        check(first.placementCandidates == second.placementCandidates &&
                first.placementRejections == second.placementRejections &&
                first.placementEvaluations == second.placementEvaluations,
            "placement candidate and work counts match exactly: " + seed);
        check(first.selectedPlacementAttempt == second.selectedPlacementAttempt &&
                first.selectedPlacementScore == second.selectedPlacementScore,
            "selected placement attempt and score match exactly: " + seed);
        check(first.rankedPlacementAttempts.equals(second.rankedPlacementAttempts),
            "ranked placement order matches exactly: " + seed);
        check(first.placementScores.equals(second.placementScores),
            "all placement scores match exactly: " + seed);
        check(first.routeAttempts == second.routeAttempts &&
                first.routingOrders == second.routingOrders &&
                first.routingExpansions == second.routingExpansions,
            "route attempt and routing work counts match exactly: " + seed);
        check(first.oneFaceAttempts == second.oneFaceAttempts &&
                first.oneFaceSuccesses == second.oneFaceSuccesses &&
                first.twoLayerAttempts == second.twoLayerAttempts &&
                first.twoLayerSuccesses == second.twoLayerSuccesses,
            "per-policy routing attempt and success counts match exactly: " + seed);
        check(first.routeOutcomes.equals(second.routeOutcomes),
            "chronological route outcomes match exactly: " + seed);
        check(second.selectedRouteQualityMilli == routeQualityMilli(
                trial.layout.getRouteQualityScore(trial.board)),
            "trial selected quality is refreshed from post-trim geometry: " + seed);
    }

    private static void compareRigidGeometry(Observation saved, Observation trial,
            long seed) {
        Rectangle firstOutline = saved.layout.getBoardOutline();
        Rectangle secondOutline = trial.layout.getBoardOutline();
        long widthDifference = (long) firstOutline.width - secondOutline.width;
        long heightDifference = (long) firstOutline.height - secondOutline.height;
        check(firstOutline.x == secondOutline.x && firstOutline.y == secondOutline.y,
            "post-selection trim keeps the old board-outline origin: " + seed);
        check(widthDifference >= 0 && heightDifference >= 0 &&
                (widthDifference & 1L) == 0 && (heightDifference & 1L) == 0,
            "outline extents admit nonnegative integral side trim: " + seed);
        long widthDelta = widthDifference / 2L;
        long heightDelta = heightDifference / 2L;
        check(widthDelta == heightDelta && widthDelta <= Integer.MAX_VALUE,
            "outline width and height imply one uniform integer translation: " + seed);
        final int delta = (int) widthDelta;
        check(delta % 10 == 0, "trial crop moves only in whole10-unit steps: " + seed);

        Rectangle firstOccupied = saved.layout.getOccupiedContentBounds();
        Rectangle secondOccupied = trial.layout.getOccupiedContentBounds();
        checkRectangleTranslated(firstOccupied, secondOccupied, delta,
            "occupied content translates rigidly", seed);
        checkRectangleTranslated(saved.margins.requiredAccessBounds,
            trial.margins.requiredAccessBounds, delta,
            "reconstructed access envelope translates rigidly", seed);
        check(saved.margins.requiredMargin == trial.margins.requiredMargin &&
                saved.margins.legacyRequestedMargin == trial.margins.legacyRequestedMargin &&
                saved.margins.fixedMarginFloor == trial.margins.fixedMarginFloor &&
                saved.margins.accessEnvelopeMargin == trial.margins.accessEnvelopeMargin,
            "paired layouts retain the same independent required-margin facts: " + seed);
        check(saved.margins.actualMargin >= saved.margins.legacyRequestedMargin &&
                saved.margins.actualMargin >= saved.margins.requiredMargin &&
                trial.margins.actualMargin >= trial.margins.requiredMargin &&
                trial.margins.actualMargin <= saved.margins.actualMargin,
            "saved14 keeps its legacy margin; trial keeps physical access and never grows: " + seed);
        check(saved.margins.actualMargin - trial.margins.actualMargin == delta,
            "measured side-margin change equals the outline-derived translation: " + seed);

        compareComponents(saved.board, saved.layout, trial.layout, delta, seed);
        comparePads(saved.board, saved.layout, trial.layout, delta, seed);
        compareAccessBounds(saved.board, saved.layout, trial.layout, delta, seed);
        compareTraces(saved.layout, trial.layout, delta, seed);
        compareHoles(saved.layout, trial.layout, delta, seed);
        compareSilkscreen(saved.layout, trial.layout, delta, seed);
        compareRegions(saved.layout, trial.layout, delta, seed);
        compareConductorGraph(saved.board, saved.layout, trial.board, trial.layout,
            delta, seed);

        check(saved.copperLength == trial.copperLength,
            "rigid translation preserves routed copper Manhattan length: " + seed);
        check(saved.vias == trial.vias,
            "rigid translation preserves via count: " + seed);
        check(saved.layout.getGenerationPlacementAttempts() ==
                trial.layout.getGenerationPlacementAttempts() &&
                saved.layout.getGenerationRoutingAttempts() ==
                trial.layout.getGenerationRoutingAttempts(),
            "paired geometry retains generation receipts: " + seed);
        // Canvas dimensions and parts-tray bounds are workbench chrome and are
        // intentionally outside this board-geometry comparison.
    }

    private static void compareComponents(TroubleshootBoard board,
            PcbBoardLayout saved, PcbBoardLayout trial, int delta, long seed) {
        TreeMap<String, PcbComponentPlacement> first = componentsById(saved, seed);
        TreeMap<String, PcbComponentPlacement> second = componentsById(trial, seed);
        check(first.keySet().equals(second.keySet()) &&
                first.keySet().equals(new TreeSet<String>(board.getComponentIds())),
            "component identities match in the paired layouts and source: " + seed);
        for (String id : first.keySet()) {
            PcbComponentPlacement a = first.get(id), b = second.get(id);
            check(a.getComponentId().equals(b.getComponentId()) &&
                    a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight(),
                "component identity and dimensions are stable: " + seed + "/" + id);
            check((long) a.getX() - delta == b.getX() &&
                    (long) a.getY() - delta == b.getY(),
                "component pose translates by the outline-derived delta: " + seed + "/" + id);
            check(a.getPose().getRotation() == b.getPose().getRotation() &&
                    a.getPose().getMountingSide() == b.getPose().getMountingSide(),
                "component rotation and mounting face are stable: " + seed + "/" + id);
            check(a.getPhysicalPackage().isEquivalentTo(b.getPhysicalPackage()) &&
                    a.getPhysicalPackage().getId().equals(b.getPhysicalPackage().getId()) &&
                    a.getPhysicalGeometry().isEquivalentTo(b.getPhysicalGeometry()) &&
                    a.getGeometryVariantKey().equals(b.getGeometryVariantKey()) &&
                    a.getGeometryTransformKey().equals(b.getGeometryTransformKey()) &&
                    a.getGeometryContractVersion().equals(b.getGeometryContractVersion()) &&
                    a.getGeometryRealization().isEquivalentTo(b.getGeometryRealization()),
                "component package realization metadata is stable: " + seed + "/" + id);
            checkRectangleTranslated(a.getBounds(), b.getBounds(), delta,
                "component bounds", seed + "/" + id);
            checkRectangleTranslated(a.getBodyBounds(), b.getBodyBounds(), delta,
                "component body", seed + "/" + id);
            checkRectangleTranslated(a.getKeepOut(), b.getKeepOut(), delta,
                "component keep-out", seed + "/" + id);
            checkRectangleTranslated(a.getRoutingCourtyard(), b.getRoutingCourtyard(), delta,
                "component courtyard", seed + "/" + id);
            checkRectangleTranslated(a.getSelectionEnvelope(), b.getSelectionEnvelope(), delta,
                "component selection envelope", seed + "/" + id);
            checkRectangleTranslated(a.getDragEnvelope(), b.getDragEnvelope(), delta,
                "component drag envelope", seed + "/" + id);
            checkRectangleTranslated(a.getUnderpass(), b.getUnderpass(), delta,
                "component underpass", seed + "/" + id);
            PhysicalPackageGeometry geometry = a.getPhysicalGeometry();
            check(geometry.getTerminals().size() == b.getPhysicalGeometry().getTerminals().size(),
                "component terminal inventory is stable: " + seed + "/" + id);
            for (int index = 0; index < geometry.getTerminals().size(); index++) {
                PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
                PhysicalPackageGeometry.Terminal other =
                    b.getPhysicalGeometry().getTerminal(index);
                String padId = id + "." + terminal.getTerminalId();
                check(terminal.getTerminalId().equals(other.getTerminalId()) &&
                        terminal.getAttachment() == other.getAttachment(),
                    "component terminal identity and attachment are stable: " +
                        seed + "/" + padId);
                checkPointTranslated(a.getPadPoint(index), b.getPadPoint(index), delta,
                    "component terminal pad point", seed + "/" + padId);
                checkRectangleTranslated(a.getPadBounds(index), b.getPadBounds(index), delta,
                    "component terminal pad bounds", seed + "/" + padId);
                checkPointTranslated(a.getBoardPadProbeCenter(index),
                    b.getBoardPadProbeCenter(index), delta,
                    "board-pad probe center", seed + "/" + padId);
                checkRectangleTranslated(a.getBoardPadProbeBounds(index),
                    b.getBoardPadProbeBounds(index), delta,
                    "board-pad probe bounds", seed + "/" + padId);
                checkPointTranslated(a.getComponentLeadProbeCenter(index),
                    b.getComponentLeadProbeCenter(index), delta,
                    "connected component-lead probe center", seed + "/" + padId);
                checkPointTranslated(a.getComponentLeadProbeCenter(index, true),
                    b.getComponentLeadProbeCenter(index, true), delta,
                    "lifted component-lead probe center", seed + "/" + padId);
                checkRectangleTranslated(a.getComponentLeadProbeBounds(index),
                    b.getComponentLeadProbeBounds(index), delta,
                    "connected component-lead probe bounds", seed + "/" + padId);
                checkRectangleTranslated(a.getComponentLeadProbeBounds(index, true),
                    b.getComponentLeadProbeBounds(index, true), delta,
                    "lifted component-lead probe bounds", seed + "/" + padId);
                checkPointTranslated(a.getLeadBodyPoint(index), b.getLeadBodyPoint(index), delta,
                    "connected lead body point", seed + "/" + padId);
                checkPointTranslated(a.getLeadBodyPoint(index, true),
                    b.getLeadBodyPoint(index, true), delta,
                    "lifted lead body point", seed + "/" + padId);
                checkPointTranslated(a.getLeadEndPoint(index), b.getLeadEndPoint(index), delta,
                    "connected lead end point", seed + "/" + padId);
                checkPointTranslated(a.getLeadEndPoint(index, true),
                    b.getLeadEndPoint(index, true), delta,
                    "lifted lead end point", seed + "/" + padId);
                checkRectangleTranslated(a.getLeadBounds(index), b.getLeadBounds(index), delta,
                    "connected lead bounds", seed + "/" + padId);
                checkRectangleTranslated(a.getLeadBounds(index, true),
                    b.getLeadBounds(index, true), delta,
                    "lifted lead bounds", seed + "/" + padId);
                checkPointTranslated(a.getConnectedBoardEndPoint(index),
                    b.getConnectedBoardEndPoint(index), delta,
                    "connected board endpoint", seed + "/" + padId);
                checkPointTranslated(a.getLiftedFreeEndPoint(index),
                    b.getLiftedFreeEndPoint(index), delta,
                    "lifted free endpoint", seed + "/" + padId);
            }
        }
    }

    private static void comparePads(TroubleshootBoard board, PcbBoardLayout saved,
            PcbBoardLayout trial, int delta, long seed) {
        TreeMap<String, PcbPadPlacement> first = padsById(saved, seed);
        TreeMap<String, PcbPadPlacement> second = padsById(trial, seed);
        check(first.keySet().equals(second.keySet()) &&
                first.keySet().equals(new TreeSet<String>(board.getPadIds())),
            "pad identities match in the paired layouts and source: " + seed);
        for (String id : first.keySet()) {
            PcbPadPlacement a = first.get(id), b = second.get(id);
            check(a.getPadId().equals(b.getPadId()) &&
                    a.getEscapeDx() == b.getEscapeDx() &&
                    a.getEscapeDy() == b.getEscapeDy() &&
                    a.getEscapeLength() == b.getEscapeLength() &&
                    a.getAttachment() == b.getAttachment() &&
                    a.getMountingSide() == b.getMountingSide() &&
                    a.getExposure() == b.getExposure(),
                "pad endpoint, escape and copper exposure metadata are stable: " + seed + "/" + id);
            check((long) a.getX() - delta == b.getX() &&
                    (long) a.getY() - delta == b.getY(),
                "pad center translates by the outline-derived delta: " + seed + "/" + id);
            checkRectangleTranslated(a.getPadBounds(), b.getPadBounds(), delta,
                "pad copper bounds", seed + "/" + id);
            checkRectangleTranslated(a.getProbeBounds(), b.getProbeBounds(), delta,
                "pad probe bounds", seed + "/" + id);
            BoardPad firstSource = board.getPad(id);
            check(firstSource != null && id.equals(firstSource.getId()),
                "pad retains its source endpoint identity: " + seed + "/" + id);
        }
    }

    private static void compareAccessBounds(TroubleshootBoard board,
            PcbBoardLayout saved, PcbBoardLayout trial, int delta, long seed) {
        Vector<PcbFootprint> first = reconstructFinalFootprints(board, saved, seed, "saved14");
        Vector<PcbFootprint> second = reconstructFinalFootprints(board, trial, seed, "trial15");
        TreeMap<String, PcbFootprint> firstById = footprintsById(first, seed);
        TreeMap<String, PcbFootprint> secondById = footprintsById(second, seed);
        check(firstById.keySet().equals(secondById.keySet()),
            "access envelopes cover the same footprint IDs: " + seed);
        for (String id : firstById.keySet()) {
            PcbPlacementConstraints.Part demand =
                board.getPlacementConstraints().get(id);
            check(demand != null, "access envelope has a declared demand: " + seed + "/" + id);
            Rectangle firstEnvelope = PcbPlacementPlanner.envelope(firstById.get(id),
                demand.accessMargin);
            Rectangle secondEnvelope = PcbPlacementPlanner.envelope(secondById.get(id),
                demand.accessMargin);
            checkRectangleTranslated(firstEnvelope, secondEnvelope, delta,
                "package access envelope", seed + "/" + id);
        }
    }

    private static void compareTraces(PcbBoardLayout saved, PcbBoardLayout trial,
            int delta, long seed) {
        TreeMap<String, PcbTraceGeometry> first = tracesById(saved, seed);
        TreeMap<String, PcbTraceGeometry> second = tracesById(trial, seed);
        check(first.keySet().equals(second.keySet()),
            "routed copper source identities match exactly: " + seed);
        for (String id : first.keySet()) {
            PcbTraceGeometry a = first.get(id), b = second.get(id);
            check(equal(a.getNetId(), b.getNetId()) &&
                    equal(a.getStartPadId(), b.getStartPadId()) &&
                    equal(a.getEndPadId(), b.getEndPadId()) &&
                    a.getLayer() == b.getLayer() && a.getExposure() == b.getExposure() &&
                    a.getWidth() == b.getWidth(),
                "copper endpoints, net, layer, exposure and width are stable: " + seed + "/" + id);
            int[] ax = a.getXPoints(), ay = a.getYPoints();
            int[] bx = b.getXPoints(), by = b.getYPoints();
            check(ax.length == ay.length && bx.length == by.length &&
                    ax.length == bx.length && ay.length == by.length,
                "routed copper point counts match exactly: " + seed + "/" + id);
            for (int index = 0; index < ax.length; index++)
                check((long) ax[index] - delta == bx[index] &&
                        (long) ay[index] - delta == by[index],
                    "routed copper point translates rigidly: " + seed + "/" + id + "#" + index);
        }
    }

    private static void compareHoles(PcbBoardLayout saved, PcbBoardLayout trial,
            int delta, long seed) {
        TreeMap<String, PcbBoardHole> first = holesById(saved, seed);
        TreeMap<String, PcbBoardHole> second = holesById(trial, seed);
        check(first.keySet().equals(second.keySet()),
            "drill and via identities match exactly: " + seed);
        for (String id : first.keySet()) {
            PcbBoardHole a = first.get(id), b = second.get(id);
            check(a.kind == b.kind && equal(a.netId, b.netId) &&
                    a.drillRadius == b.drillRadius && a.landRadius == b.landRadius &&
                    a.exposure == b.exposure,
                "drill kind, net, radii and copper exposure are stable: " + seed + "/" + id);
            check((long) a.x - delta == b.x && (long) a.y - delta == b.y,
                "hole center translates by the outline-derived delta: " + seed + "/" + id);
            checkRectangleTranslated(a.getBounds(), b.getBounds(), delta,
                "hole copper/drill bounds", seed + "/" + id);
        }
    }

    private static void compareSilkscreen(PcbBoardLayout saved, PcbBoardLayout trial,
            int delta, long seed) {
        TreeMap<String, PcbSilkscreenLabel> first = labelsById(saved, seed);
        TreeMap<String, PcbSilkscreenLabel> second = labelsById(trial, seed);
        check(first.keySet().equals(second.keySet()),
            "silkscreen label IDs match exactly: " + seed);
        for (String id : first.keySet()) {
            PcbSilkscreenLabel a = first.get(id), b = second.get(id);
            check(a.getId().equals(b.getId()) && a.getText().equals(b.getText()) &&
                    a.getFontSize() == b.getFontSize() && a.isBold() == b.isBold() &&
                    equal(a.getTargetPadId(), b.getTargetPadId()),
                "silkscreen text, style and target endpoint are stable: " + seed + "/" + id);
            checkRectangleTranslated(a.getBounds(), b.getBounds(), delta,
                "silkscreen bounds", seed + "/" + id);
            check((long) a.getBaselineX() - delta == b.getBaselineX() &&
                    (long) a.getBaselineY() - delta == b.getBaselineY(),
                "silkscreen baseline translates rigidly: " + seed + "/" + id);
        }
    }

    private static void compareRegions(PcbBoardLayout saved, PcbBoardLayout trial,
            int delta, long seed) {
        TreeMap<String, PcbLayoutRegion> first = regionsById(saved, seed);
        TreeMap<String, PcbLayoutRegion> second = regionsById(trial, seed);
        check(first.keySet().equals(second.keySet()),
            "physical region IDs match exactly: " + seed);
        for (String id : first.keySet()) {
            PcbLayoutRegion a = first.get(id), b = second.get(id);
            check(a.label.equals(b.label) && a.getComponentIds().equals(b.getComponentIds()),
                "physical region labels and membership are stable: " + seed + "/" + id);
            checkRectangleTranslated(a.bounds(saved), b.bounds(trial), delta,
                "physical region bounds", seed + "/" + id);
        }
    }

    private static void compareConductorGraph(TroubleshootBoard firstBoard,
            PcbBoardLayout firstLayout, TroubleshootBoard secondBoard,
            PcbBoardLayout secondLayout, int delta, long seed) {
        PcbConductorGraph first = firstLayout.captureConductorGraph(firstBoard);
        PcbConductorGraph second = secondLayout.captureConductorGraph(secondBoard);
        verifyTranslatedConductorGraphs(first, second, firstBoard.getPadIds(), delta,
            "seed=" + seed);
    }

    /**
     * Independently proves that two conductor graphs are the same physical graph
     * under a literal translation. Builder junction/edge serial IDs are assigned
     * from lexically sorted coordinate keys, so a rigid move across a digit
     * boundary can renumber them; this bijection permits only that derived-ID
     * renumbering. Source-owned pad, terminal, edge provenance and land IDs remain
     * exact, while every junction, edge, trace surface and reference is mapped
     * one-to-one. It therefore preserves connectivity instead of waiving graph
     * identity or comparing the two canonical strings.
     */
    static void verifyTranslatedConductorGraphs(PcbConductorGraph first,
            PcbConductorGraph second, Iterable<String> pads, int delta, String context) {
        String where = context == null ? "translated conductor graph" : context;
        check(first != null && second != null,
            where + ": both conductor graphs are present");
        check(pads != null, where + ": source pad inventory is present");

        check(first.getJunctions().size() == second.getJunctions().size(),
            where + ": physical junction cardinality is unchanged");
        TreeMap<String, PcbConductorGraph.Junction> secondJunctionsByGeometry =
            new TreeMap<String, PcbConductorGraph.Junction>();
        for (PcbConductorGraph.Junction junction : second.getJunctions().values()) {
            String key = junctionGeometryKey(junction.netId, junction.layer,
                junction.x, junction.y);
            check(secondJunctionsByGeometry.put(key, junction) == null,
                where + ": trial graph has unique net/layer/coordinate junctions");
        }
        TreeMap<String, String> junctionMap = new TreeMap<String, String>();
        TreeMap<String, String> reverseJunctionMap = new TreeMap<String, String>();
        TreeSet<String> mappedJunctionIds = new TreeSet<String>();
        for (PcbConductorGraph.Junction source : first.getJunctions().values()) {
            String key = junctionGeometryKey(source.netId, source.layer,
                (long) source.x - delta, (long) source.y - delta);
            PcbConductorGraph.Junction target = secondJunctionsByGeometry.get(key);
            check(target != null,
                where + ": junction has a translated net/layer match " + source.id);
            check(junctionMap.put(source.id, target.id) == null &&
                    reverseJunctionMap.put(target.id, source.id) == null &&
                    mappedJunctionIds.add(target.id),
                where + ": junction translation is one-to-one " + source.id);
        }
        check(junctionMap.size() == first.getJunctions().size() &&
                reverseJunctionMap.size() == second.getJunctions().size() &&
                mappedJunctionIds.equals(new TreeSet<String>(second.getJunctions().keySet())),
            where + ": junction mapping is a complete bijection");

        check(first.getEdges().size() == second.getEdges().size(),
            where + ": physical copper edge cardinality is unchanged");
        TreeMap<String, String> edgeMap = new TreeMap<String, String>();
        TreeMap<String, String> reverseEdgeMap = new TreeMap<String, String>();
        TreeSet<String> mappedEdgeIds = new TreeSet<String>();
        for (PcbConductorGraph.Edge source : first.getEdges().values()) {
            String mappedFirst = junctionMap.get(source.first);
            String mappedSecond = junctionMap.get(source.second);
            check(mappedFirst != null && mappedSecond != null,
                where + ": edge endpoints have mapped junctions " + source.id);
            PcbConductorGraph.Edge match = null;
            int matches = 0;
            for (PcbConductorGraph.Edge candidate : second.getEdges().values()) {
                boolean sameEndpoints =
                    (mappedFirst.equals(candidate.first) && mappedSecond.equals(candidate.second)) ||
                    (mappedFirst.equals(candidate.second) && mappedSecond.equals(candidate.first));
                if (sameEndpoints && source.netId.equals(candidate.netId) &&
                        source.kind == candidate.kind && source.getWidth() == candidate.getWidth() &&
                        source.getSources().equals(candidate.getSources())) {
                    match = candidate;
                    matches++;
                }
            }
            check(matches == 1,
                where + ": edge has exactly one mapped endpoint/net/kind/width/provenance match " +
                    source.id + " matches=" + matches);
            check(edgeMap.put(source.id, match.id) == null &&
                    reverseEdgeMap.put(match.id, source.id) == null &&
                    mappedEdgeIds.add(match.id),
                where + ": edge mapping is one-to-one " + source.id);
        }
        check(edgeMap.size() == first.getEdges().size() &&
                reverseEdgeMap.size() == second.getEdges().size() &&
                mappedEdgeIds.equals(new TreeSet<String>(second.getEdges().keySet())),
            where + ": edge mapping is a complete bijection");

        TreeMap<String, PcbConductorGraph.Surface> firstSurfaces = surfacesById(first);
        TreeMap<String, PcbConductorGraph.Surface> secondSurfaces = surfacesById(second);
        check(firstSurfaces.size() == secondSurfaces.size(),
            where + ": copper/probe surface cardinality is unchanged");
        TreeSet<String> mappedSurfaceIds = new TreeSet<String>();
        for (PcbConductorGraph.Surface source : firstSurfaces.values()) {
            String expectedTargetId;
            if (source.edgeId == null) {
                // Land IDs and pad IDs are source-owned identities, not builder serials.
                expectedTargetId = source.id;
            } else {
                String mappedEdge = edgeMap.get(source.edgeId);
                check(mappedEdge != null,
                    where + ": trace surface has a mapped physical edge " + source.id);
                expectedTargetId = "copper/" + mappedEdge;
                check(source.id.equals("copper/" + source.edgeId),
                    where + ": source trace surface ID names its edge " + source.id);
            }
            PcbConductorGraph.Surface target = secondSurfaces.get(expectedTargetId);
            check(target != null,
                where + ": surface has its mapped stable/edge-derived ID " + source.id);
            check(mappedSurfaceIds.add(target.id),
                where + ": surface mapping is one-to-one " + source.id);
            check(source.layer == target.layer && source.exposure == target.exposure &&
                    equal(source.padId, target.padId),
                where + ": surface layer, exposure and pad identity are stable " + source.id);
            checkRectangleTranslated(source.getBounds(), target.getBounds(), delta,
                "physical copper/probe surface bounds", where + "/" + source.id);

            if (source.edgeId == null) {
                check(target.edgeId == null,
                    where + ": stable land surface remains a land surface " + source.id);
                String mappedAnchor = junctionMap.get(source.junctionId);
                check(mappedAnchor != null && mappedAnchor.equals(target.junctionId),
                    where + ": land surface retains its mapped physical pad junction " +
                        source.id);
            } else {
                check(target.edgeId != null,
                    where + ": mapped trace surface retains its copper edge reference " + source.id);
                PcbConductorGraph.Edge sourceEdge = first.getEdges().get(source.edgeId);
                PcbConductorGraph.Edge targetEdge = second.getEdges().get(target.edgeId);
                check(sourceEdge != null && targetEdge != null &&
                        target.edgeId.equals(edgeMap.get(source.edgeId)) &&
                        sourceEdge.kind == PcbConductorGraph.Kind.TRACE &&
                        targetEdge.kind == PcbConductorGraph.Kind.TRACE,
                    where + ": trace surface refers to trace copper " + source.id);
                boolean sourceAnchorOnEdge = source.junctionId.equals(sourceEdge.first) ||
                    source.junctionId.equals(sourceEdge.second);
                boolean targetAnchorOnMappedEdge = target.junctionId.equals(targetEdge.first) ||
                    target.junctionId.equals(targetEdge.second);
                check(sourceAnchorOnEdge && targetAnchorOnMappedEdge &&
                        (target.junctionId.equals(junctionMap.get(sourceEdge.first)) ||
                         target.junctionId.equals(junctionMap.get(sourceEdge.second))),
                    where + ": trace probe anchor remains on the mapped copper edge " +
                        source.id);
            }
        }
        check(mappedSurfaceIds.equals(secondSurfaces.keySet()),
            where + ": surface mapping is a complete bijection");

        Map<String, String> firstTerminals = first.getTerminalJunctions();
        Map<String, String> secondTerminals = second.getTerminalJunctions();
        check(firstTerminals.keySet().equals(secondTerminals.keySet()),
            where + ": electrical terminal identity keysets match exactly");
        for (String terminalId : firstTerminals.keySet()) {
            String sourceJunction = firstTerminals.get(terminalId);
            String targetJunction = secondTerminals.get(terminalId);
            String mappedJunction = sourceJunction == null ? null : junctionMap.get(sourceJunction);
            check(sourceJunction == null ? targetJunction == null :
                    mappedJunction != null && mappedJunction.equals(targetJunction),
                where + ": terminal reference follows its mapped physical junction " +
                    terminalId);
        }
        for (String padId : pads) {
            check(padId != null,
                where + ": source pad inventory contains only identities");
            for (PcbCopperLayer layer : PcbCopperLayer.values()) {
                String sourceJunction = first.getPadJunction(padId, layer);
                String targetJunction = second.getPadJunction(padId, layer);
                String mappedJunction = sourceJunction == null ? null :
                    junctionMap.get(sourceJunction);
                check(sourceJunction == null ? targetJunction == null :
                        mappedJunction != null && mappedJunction.equals(targetJunction),
                    where + ": pad/layer probe reference follows its mapped junction " +
                        padId + "/" + layer);
            }
        }
    }

    private static String junctionGeometryKey(String netId, PcbCopperLayer layer,
            long x, long y) {
        return netId.length() + ":" + netId + ":" + layer.name() + ":" + x + ":" + y;
    }

    private static TreeMap<String, PcbConductorGraph.Surface> surfacesById(
            PcbConductorGraph graph) {
        TreeMap<String, PcbConductorGraph.Surface> result =
            new TreeMap<String, PcbConductorGraph.Surface>();
        for (PcbConductorGraph.Surface surface : graph.getSurfaces())
            check(result.put(surface.id, surface) == null,
                "conductor surface identity is unique: " + surface.id);
        return result;
    }

    private static TreeMap<String, PcbComponentPlacement> componentsById(
            PcbBoardLayout layout, long seed) {
        TreeMap<String, PcbComponentPlacement> result =
            new TreeMap<String, PcbComponentPlacement>();
        for (PcbComponentPlacement component : layout.getComponents())
            check(result.put(component.getComponentId(), component) == null,
                "component ID is unique: " + seed + "/" + component.getComponentId());
        return result;
    }

    private static TreeMap<String, PcbPadPlacement> padsById(PcbBoardLayout layout, long seed) {
        TreeMap<String, PcbPadPlacement> result = new TreeMap<String, PcbPadPlacement>();
        for (PcbPadPlacement pad : layout.getPads())
            check(result.put(pad.getPadId(), pad) == null,
                "pad ID is unique: " + seed + "/" + pad.getPadId());
        return result;
    }

    private static TreeMap<String, PcbTraceGeometry> tracesById(
            PcbBoardLayout layout, long seed) {
        TreeMap<String, PcbTraceGeometry> result = new TreeMap<String, PcbTraceGeometry>();
        for (PcbTraceGeometry trace : layout.getTraces()) {
            String id = trace.getSourceId();
            check(id != null && id.length() > 0,
                "routed copper has a stable source identity: " + seed);
            check(result.put(id, trace) == null,
                "routed copper source identity is unique: " + seed + "/" + id);
        }
        return result;
    }

    private static TreeMap<String, PcbBoardHole> holesById(PcbBoardLayout layout, long seed) {
        TreeMap<String, PcbBoardHole> result = new TreeMap<String, PcbBoardHole>();
        for (PcbBoardHole hole : layout.getHoles())
            check(result.put(hole.id, hole) == null,
                "drill identity is unique: " + seed + "/" + hole.id);
        return result;
    }

    private static TreeMap<String, PcbSilkscreenLabel> labelsById(
            PcbBoardLayout layout, long seed) {
        TreeMap<String, PcbSilkscreenLabel> result =
            new TreeMap<String, PcbSilkscreenLabel>();
        for (PcbSilkscreenLabel label : layout.getSilkscreenLabels())
            check(result.put(label.getId(), label) == null,
                "silkscreen label identity is unique: " + seed + "/" + label.getId());
        return result;
    }

    private static TreeMap<String, PcbLayoutRegion> regionsById(
            PcbBoardLayout layout, long seed) {
        TreeMap<String, PcbLayoutRegion> result = new TreeMap<String, PcbLayoutRegion>();
        for (PcbLayoutRegion region : layout.getRegions())
            check(result.put(region.id, region) == null,
                "physical region identity is unique: " + seed + "/" + region.id);
        return result;
    }

    private static TreeMap<String, PcbFootprint> footprintsById(
            Vector<PcbFootprint> footprints, long seed) {
        TreeMap<String, PcbFootprint> result = new TreeMap<String, PcbFootprint>();
        for (PcbFootprint footprint : footprints) {
            String id = footprint.getPlacement().getComponentId();
            check(result.put(id, footprint) == null,
                "reconstructed footprint identity is unique: " + seed + "/" + id);
        }
        return result;
    }

    private static void verifyExactInventory(TroubleshootBoard board,
            PcbBoardLayout layout, long seed, String label) {
        TreeSet<String> components = new TreeSet<String>();
        for (PcbComponentPlacement part : layout.getComponents())
            check(components.add(part.getComponentId()),
                label + " unique component identity: " + seed + "/" + part.getComponentId());
        check(components.equals(new TreeSet<String>(board.getComponentIds())),
            label + " component inventory matches source: " + seed);
        TreeSet<String> pads = new TreeSet<String>();
        for (PcbPadPlacement pad : layout.getPads())
            check(pads.add(pad.getPadId()),
                label + " unique pad identity: " + seed + "/" + pad.getPadId());
        check(pads.equals(new TreeSet<String>(board.getPadIds())),
            label + " pad inventory matches source: " + seed);
    }

    private static void verifyPlacementBounds(TroubleshootBoard board,
            long seed, String label) {
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        check(constraints.routingLayer == PcbCopperLayer.BOTTOM &&
                constraints.requiresTwoLayerRouting,
            label + " retains its declared two-layer routing: " + seed);
        check(constraints.getBarriers().size() == 1,
            label + " retains exactly one declared isolation barrier: " + seed);
        PcbPlacementConstraints.Barrier barrier = constraints.getBarriers().get(0);
        check("PRIMARY".equals(barrier.firstDomain) &&
                "SECONDARY".equals(barrier.secondDomain) && barrier.clearance == 80,
            label + " retains the literal PRIMARY/SECONDARY 80-unit barrier: " + seed);
    }

    private static void verifyDemandEquality(TroubleshootBoard saved,
            TroubleshootBoard trial, long seed) {
        PcbPlacementConstraints first = saved.getPlacementConstraints();
        PcbPlacementConstraints second = trial.getPlacementConstraints();
        check(first.routingLayer == second.routingLayer &&
                first.requiresTwoLayerRouting == second.requiresTwoLayerRouting &&
                first.getParts().size() == second.getParts().size() &&
                first.getBarriers().size() == second.getBarriers().size(),
            "paired interpretations retain the same routing and isolation declarations: " + seed);
        for (PcbPlacementConstraints.Part a : first.getParts()) {
            PcbPlacementConstraints.Part b = second.get(a.componentId);
            check(b != null && a.regionId.equals(b.regionId) &&
                    a.regionLabel.equals(b.regionLabel) && a.domainId.equals(b.domainId) &&
                    a.anchor == b.anchor && a.accessMargin == b.accessMargin,
                "paired physical demand metadata is stable: " + seed + "/" + a.componentId);
            Vector<PcbPlacementConstraints.TerminalDomain> firstDomains = a.getTerminalDomains();
            Vector<PcbPlacementConstraints.TerminalDomain> secondDomains = b.getTerminalDomains();
            check(firstDomains.size() == secondDomains.size(),
                "paired terminal-domain counts match: " + seed + "/" + a.componentId);
            for (PcbPlacementConstraints.TerminalDomain domain : firstDomains) {
                PcbPlacementConstraints.TerminalDomain other = null;
                for (PcbPlacementConstraints.TerminalDomain candidate : secondDomains)
                    if (domain.terminalId.equals(candidate.terminalId)) other = candidate;
                check(other != null && domain.domainId.equals(other.domainId),
                    "paired terminal-domain identity matches: " + seed + "/" +
                        a.componentId + "." + domain.terminalId);
            }
        }
        for (int index = 0; index < first.getBarriers().size(); index++) {
            PcbPlacementConstraints.Barrier a = first.getBarriers().get(index);
            PcbPlacementConstraints.Barrier b = second.getBarriers().get(index);
            check(a.firstDomain.equals(b.firstDomain) &&
                    a.secondDomain.equals(b.secondDomain) && a.clearance == b.clearance,
                "paired isolation barrier metadata matches: " + seed);
        }
    }

    private static void verifyTerminalSurfaces(TroubleshootBoard board,
            PcbBoardLayout layout, long seed, String label) {
        for (String id : board.getComponentIds()) {
            BoardComponent source = board.getComponent(id);
            PcbComponentPlacement component = layout.getComponent(id);
            check(component != null && source.getPhysicalPackage() == component.getPhysicalPackage(),
                label + " placed package retains source owner: " + seed + "/" + id);
            PhysicalPackageGeometry geometry = component.getPhysicalGeometry();
            check(source.getPhysicalPackage().acceptsGeometry(geometry),
                label + " package uses a declared geometry variant: " + seed + "/" + id);
            PhysicalPackageGeometry.Placement expected = geometry.placedAt(component.getPose());
            check(geometry.getTerminals().size() == source.getPadIds().size(),
                label + " source and physical terminal counts match: " + seed + "/" + id);
            for (int index = 0; index < geometry.getTerminals().size(); index++) {
                PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
                String padId = id + "." + terminal.getTerminalId();
                BoardPad logical = board.getPad(padId);
                PcbPadPlacement pad = layout.getPad(padId);
                check(logical != null && id.equals(logical.getComponentId()) &&
                        terminal.getTerminalId().equals(logical.getTerminalId()) && pad != null,
                    label + " placed terminal has its exact logical endpoint: " + seed + "/" + padId);
                Point expectedPad = expected.getPadPoint(index);
                check(pad.getX() == expectedPad.x && pad.getY() == expectedPad.y &&
                        pad.getPadBounds().equals(expected.getPadBounds(index)) &&
                        pad.getProbeBounds().equals(expected.getBoardPadProbeBounds(index)),
                    label + " pad and board-probe bounds follow package pose: " + seed + "/" + padId);
                check(pad.getEscapeDx() == expected.getEscapeDx(index) &&
                        pad.getEscapeDy() == expected.getEscapeDy(index) &&
                        pad.getEscapeLength() == expected.getEscapeLength(index) &&
                        pad.getAttachment() == terminal.getAttachment() &&
                        pad.getMountingSide() == component.getMountingSide(),
                    label + " terminal escape, attachment and face are preserved: " +
                        seed + "/" + padId);
                check(component.getBoardPadProbeBounds(index)
                            .equals(expected.getBoardPadProbeBounds(index)) &&
                        component.getComponentLeadProbeBounds(index)
                            .equals(expected.getComponentLeadProbeBounds(index)) &&
                        component.getComponentLeadProbeBounds(index, true)
                            .equals(expected.getComponentLeadProbeBounds(index, true)),
                    label + " board-pad and connected/lifted lead probes follow package geometry: " +
                        seed + "/" + padId);
            }
        }
    }

    private static void verifyRoleWidths(TroubleshootBoard board,
            PcbBoardLayout layout, long seed, String label) {
        PcbTraceWidthProfile profile = board.getPlacementConstraints().getTraceWidthProfile();
        check("ROLE_TRACE@1".equals(profile.getIdentity()),
            label + " exposes ROLE_TRACE@1: " + seed);
        check(profile.maximumWidth() == 13,
            label + " retains the qualified width13 maximum: " + seed);
        for (BoardNet.RoutingRole role : BoardNet.RoutingRole.values())
            check(profile.widthFor(role) == literalWidth(role),
                label + " literal role width for " + role + ": " + seed);
        for (PcbTraceGeometry trace : layout.getTraces()) {
            BoardNet net = board.getNet(trace.getNetId());
            check(net != null, label + " trace net is declared: " + seed + "/" + trace.getNetId());
            check(trace.getWidth() == literalWidth(net.getRoutingRole()),
                label + " trace width matches its declared role: " + seed + "/" + trace.getNetId());
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

    private static Vector<PcbFootprint> reconstructFinalFootprints(
            TroubleshootBoard board, PcbBoardLayout layout, long seed, String label) {
        Vector<PcbFootprint> result = new Vector<PcbFootprint>();
        for (String componentId : board.getComponentIds()) {
            BoardComponent source = board.getComponent(componentId);
            PcbComponentPlacement placed = layout.getComponent(componentId);
            check(source != null && placed != null,
                label + " final footprint has source and routed component: " +
                    seed + "/" + componentId);
            Vector<String> orderedPadIds = source.getPadIds();
            check(orderedPadIds.size() == placed.getPhysicalGeometry().getTerminals().size(),
                label + " final footprint has declared terminal count: " +
                    seed + "/" + componentId);
            Vector<PcbPadPlacement> orderedPads = new Vector<PcbPadPlacement>();
            for (int index = 0; index < orderedPadIds.size(); index++) {
                String padId = orderedPadIds.get(index);
                PhysicalPackageGeometry.Terminal terminal =
                    placed.getPhysicalGeometry().getTerminal(index);
                check(padId.equals(componentId + "." + terminal.getTerminalId()),
                    label + " final footprint follows package terminal order: " +
                        seed + "/" + padId);
                PcbPadPlacement pad = layout.getPad(padId);
                check(pad != null,
                    label + " final footprint resolves its routed pad: " + seed + "/" + padId);
                orderedPads.add(pad);
            }
            PcbFootprint footprint = new PcbFootprint(placed, orderedPads);
            check(footprint.getPads().size() == orderedPadIds.size(),
                label + " final footprint retains every ordered pad: " + seed + "/" + componentId);
            result.add(footprint);
        }
        check(result.size() == board.getComponentIds().size(),
            label + " final footprint covers the complete component inventory: " + seed);
        return result;
    }

    private static final class MarginFacts {
        final int fixedMarginFloor;
        final int accessEnvelopeMargin;
        final int requiredMargin;
        final int legacyRequestedMargin;
        final int actualMargin;
        final Rectangle requiredAccessBounds;
        MarginFacts(int fixed, int access, int actual, Rectangle accessBounds) {
            fixedMarginFloor = fixed;
            accessEnvelopeMargin = access;
            requiredMargin = Math.max(26, access);
            legacyRequestedMargin = Math.max(fixed, access);
            actualMargin = actual;
            requiredAccessBounds = accessBounds;
        }
    }

    private static MarginFacts marginFacts(TroubleshootBoard board,
            PcbBoardLayout layout, Vector<PcbFootprint> finalFootprints,
            long seed, String label) {
        Rectangle outline = layout.getBoardOutline();
        Rectangle occupied = layout.getOccupiedContentBounds();
        int fixedMargin = 26;
        for (PcbPlacementConstraints.Part demand :
                board.getPlacementConstraints().getParts())
            fixedMargin = Math.max(fixedMargin, demand.accessMargin + 30);
        int accessMargin = 0;
        Rectangle allAccess = null;
        for (PcbFootprint footprint : finalFootprints) {
            String id = footprint.getPlacement().getComponentId();
            PcbPlacementConstraints.Part demand = board.getPlacementConstraints().get(id);
            check(demand != null, label + " final footprint has declared demand: " + seed + "/" + id);
            Rectangle access = PcbPlacementPlanner.envelope(footprint, demand.accessMargin);
            allAccess = union(allAccess, access);
            accessMargin = Math.max(accessMargin, occupied.x - access.x + 10);
            accessMargin = Math.max(accessMargin, occupied.y - access.y + 10);
            accessMargin = Math.max(accessMargin,
                access.x + access.width - occupied.x - occupied.width + 10);
            accessMargin = Math.max(accessMargin,
                access.y + access.height - occupied.y - occupied.height + 10);
        }
        check(fixedMargin >= 26,
            label + " independently reconstructed fixed margin retains floor26: " + seed);
        check(accessMargin >= 0,
            label + " independently reconstructed access margin is nonnegative: " + seed);
        int actual = uniformActualMargin(outline, occupied, seed, label);
        int required = Math.max(26, accessMargin);
        check(actual >= required,
            label + " final margin retains physical floor26 and access requirements: " + seed);
        if ("saved14".equals(label))
            check(actual >= Math.max(fixedMargin, accessMargin),
                label + " retains its legacy requested margin: " + seed);
        check(allAccess != null,
            label + " source inventory has an access envelope: " + seed);
        return new MarginFacts(fixedMargin, accessMargin, actual, allAccess);
    }

    private static int uniformActualMargin(Rectangle outline, Rectangle occupied,
            long seed, String label) {
        long left = (long) occupied.x - outline.x;
        long top = (long) occupied.y - outline.y;
        long right = (long) outline.x + outline.width - occupied.x - occupied.width;
        long bottom = (long) outline.y + outline.height - occupied.y - occupied.height;
        check(left >= 0 && top >= 0 && right >= 0 && bottom >= 0,
            label + " occupied geometry stays inside board outline: " + seed);
        check(left == top && left == right && left == bottom,
            label + " board outline has one equal margin on all sides: " + seed);
        long widthDifference = (long) outline.width - occupied.width;
        long heightDifference = (long) outline.height - occupied.height;
        check(widthDifference >= 0 && heightDifference >= 0 &&
                (widthDifference & 1L) == 0 && (heightDifference & 1L) == 0,
            label + " extents imply integral uniform crop margins: " + seed);
        long widthMargin = widthDifference / 2L;
        long heightMargin = heightDifference / 2L;
        check(widthMargin == heightMargin && widthMargin == left &&
                widthMargin <= Integer.MAX_VALUE,
            label + " all four side margins equal extent-derived margin: " + seed);
        return (int) widthMargin;
    }

    private static void checkRectangleTranslated(Rectangle first, Rectangle second,
            int delta, String description, Object context) {
        check(first == null ? second == null : second != null,
            description + " nullability is stable: " + context);
        if (first == null) return;
        check((long) first.x - delta == second.x &&
                (long) first.y - delta == second.y &&
                first.width == second.width && first.height == second.height,
            description + " shifts exactly by -" + delta + " on both axes: " + context +
                " first=" + rectangle(first) + " second=" + rectangle(second));
    }

    private static void checkPointTranslated(Point first, Point second,
            int delta, String description, Object context) {
        check(first == null ? second == null : second != null,
            description + " nullability is stable: " + context);
        if (first == null) return;
        check((long) first.x - delta == second.x &&
                (long) first.y - delta == second.y,
            description + " shifts exactly by -" + delta + " on both axes: " + context);
    }

    private static Rectangle union(Rectangle first, Rectangle second) {
        if (first == null) return new Rectangle(second);
        int left = Math.min(first.x, second.x);
        int top = Math.min(first.y, second.y);
        int right = Math.max(first.x + first.width, second.x + second.width);
        int bottom = Math.max(first.y + first.height, second.y + second.height);
        return new Rectangle(left, top, right - left, bottom - top);
    }

    private static String rectangle(Rectangle value) {
        return value.x + "," + value.y + "," + value.width + "," + value.height;
    }

    private static long copperManhattanLength(PcbBoardLayout layout) {
        long length = 0;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            int[] x = trace.getXPoints(), y = trace.getYPoints();
            check(x.length == y.length && x.length >= 2,
                "routed trace has matching path points: " + trace.getSourceId());
            for (int index = 1; index < x.length; index++)
                length += Math.abs((long) x[index] - x[index - 1]) +
                    Math.abs((long) y[index] - y[index - 1]);
        }
        return length;
    }

    private static int viaCount(PcbBoardLayout layout) {
        int vias = 0;
        for (PcbBoardHole hole : layout.getHoles())
            if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
        return vias;
    }

    private static long routeQualityMilli(double quality) {
        if (Double.isNaN(quality) || Double.isInfinite(quality) || quality < 0) return -1;
        double scaled = quality * 1000.0;
        if (scaled >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.round(scaled);
    }

    /** Same ordered RB56 intent with only placement-interpretation metadata omitted. */
    private static String savedIntent(Rb56Plan plan) {
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

    private static String jsonRow(long seed, int version, TroubleshootBoard board,
            Rb56Plan plan, PcbBoardLayout layout,
            MediumBoardPhysicalPolicy.Statistics stats,
            int largestProtectedEmptyRectangleArea, long area, long copperLength,
            int vias, MarginFacts margins, String sourceIntentSha, String sourcePlanSha,
            String graphSha, long elapsedMillis) {
        Rectangle outline = layout.getBoardOutline();
        Rectangle occupied = layout.getOccupiedContentBounds();
        StringBuilder json = new StringBuilder(5200);
        json.append("PCB_EDGE_TRIM_CASE {\"status\":\"PASS\"");
        stringField(json, "seed", Long.toString(seed));
        json.append(",\"version\":").append(version);
        json.append(",\"density\":").append(board.getPlacementConstraints()
            .placementDensityVersion);
        json.append(",\"parts\":").append(board.getComponentIds().size());
        json.append(",\"pads\":").append(board.getPadIds().size());
        json.append(",\"area\":").append(area);
        json.append(",\"width\":").append(outline.width);
        json.append(",\"height\":").append(outline.height);
        json.append(",\"largestProtectedEmptyRectangleArea\":")
            .append(largestProtectedEmptyRectangleArea);
        json.append(",\"copperManhattanLength\":").append(copperLength);
        json.append(",\"vias\":").append(vias);
        stringField(json, "geometrySha256", PlayerSessionFingerprint.of(layout.geometryFingerprint()));
        stringField(json, "sourcePlanSha256", sourcePlanSha);
        stringField(json, "liveBoardGraphSha256", graphSha);
        stringField(json, "sourceIntentSha256", sourceIntentSha);
        stringField(json, "layoutSeed", Long.toString(plan.layoutSeed));
        stringField(json, "routingSeed", Long.toString(plan.routingSeed));
        json.append(",\"selectedAttempt\":").append(stats.selectedPlacementAttempt);
        integerArrayField(json, "rankedPlacementAttempts", stats.rankedPlacementAttempts);
        json.append(",\"acceptedPlanEvaluations\":").append(stats.placementEvaluations);
        json.append(",\"placementRejections\":").append(stats.placementRejections);
        json.append(",\"oneFaceAttempts\":").append(stats.oneFaceAttempts);
        json.append(",\"twoLayerAttempts\":").append(stats.twoLayerAttempts);
        json.append(",\"routeAttempts\":").append(stats.routeAttempts);
        json.append(",\"routingOrders\":").append(stats.routingOrders);
        json.append(",\"routingExpansions\":").append(stats.routingExpansions);
        json.append(",\"selectedQualityMilli\":").append(stats.selectedRouteQualityMilli);
        stringField(json, "statisticsCanonical", stats.toCanonical());
        json.append(",\"fixedMarginFloor\":").append(margins.fixedMarginFloor);
        json.append(",\"accessEnvelopeMargin\":").append(margins.accessEnvelopeMargin);
        json.append(",\"requiredMargin\":").append(margins.requiredMargin);
        json.append(",\"legacyRequestedMargin\":").append(margins.legacyRequestedMargin);
        json.append(",\"requestedMargin\":").append(margins.legacyRequestedMargin);
        json.append(",\"actualMargin\":").append(margins.actualMargin);
        json.append(",\"occupiedX\":").append(occupied.x);
        json.append(",\"occupiedY\":").append(occupied.y);
        json.append(",\"occupiedWidth\":").append(occupied.width);
        json.append(",\"occupiedHeight\":").append(occupied.height);
        json.append(",\"outlineX\":").append(outline.x);
        json.append(",\"outlineY\":").append(outline.y);
        json.append(",\"excludedWorkbenchChrome\":\"canvasDimensions,partsTray\"");
        json.append(",\"elapsedMillis\":").append(elapsedMillis).append('}');
        return json.toString();
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

    private static boolean equal(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static void reportFailure(long seed, Throwable failure) {
        String message = String.valueOf(failure.getMessage()).replace('\r', ' ').replace('\n', ' ');
        System.out.println("PCB_EDGE_TRIM_FAILURE seed=" + seed + " type=" +
            failure.getClass().getName() + " message=" + message);
        failure.printStackTrace(System.out);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Observation {
        final long seed;
        final int version;
        final Rb56Plan plan;
        final TroubleshootBoard board;
        final MediumBoardPhysicalPolicy.Result route;
        final PcbBoardLayout layout;
        final MediumBoardPhysicalPolicy.Statistics stats;
        final String sourceIntentSha, sourcePlanSha, graphAfter;
        final Vector<PcbFootprint> footprints;
        final MarginFacts margins;
        final long area, copperLength;
        final int vias;
        final String json, admissionIdentity;
        Observation(long seed, int version, Rb56Plan plan, TroubleshootBoard board,
                MediumBoardPhysicalPolicy.Result route, PcbBoardLayout layout,
                MediumBoardPhysicalPolicy.Statistics stats, String sourceIntentSha,
                String sourcePlanSha, String graphAfter, Vector<PcbFootprint> footprints,
                MarginFacts margins, long area, long copperLength, int vias, String json,
                String admissionIdentity) {
            this.seed = seed;
            this.version = version;
            this.plan = plan;
            this.board = board;
            this.route = route;
            this.layout = layout;
            this.stats = stats;
            this.sourceIntentSha = sourceIntentSha;
            this.sourcePlanSha = sourcePlanSha;
            this.graphAfter = graphAfter;
            this.footprints = footprints;
            this.margins = margins;
            this.area = area;
            this.copperLength = copperLength;
            this.vias = vias;
            this.json = json;
            this.admissionIdentity = admissionIdentity;
        }
    }
}
