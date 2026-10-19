package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Native-only measurement of the accepted density14 placement decisions.
 * The planner observer captures already evaluated origins; it never asks the
 * planner to evaluate another candidate. A witness trial moves one component
 * to an origin already captured by that ordinary search and uses the existing
 * full placement validator with every other raw-plan footprint held fixed.
 */
public final class PcbPocketDecisionDiagnosticContractTest {
    private static final int VERSION = 15;
    private static final int DENSITY = 14;
    private static final int GRID = 10;
    private static final int TRACE_CAP = 60000;
    private static final int RASTER_CELL_CAP = 1000000;
    private static final int COMPONENT_SAMPLE_CAP = 4;
    private static final int ORIGIN_SAMPLE_CAP = 32;
    private static final int WHOLE_PLAN_VALIDATION_CAP = 128;
    private static final int RECTANGLE_RECOMPUTE_CAP = 16;
    private static final long[] DEFAULT_ROOTS = {-1022L, 42L};
    private static int assertions;

    private PcbPocketDecisionDiagnosticContractTest() { }

    public static void main(String[] args) {
        long[] roots = DEFAULT_ROOTS;
        if (args.length > 0) {
            roots = new long[args.length];
            for (int index = 0; index < args.length; index++)
                roots[index] = Long.parseLong(args[index]);
        }
        check(roots.length > 0 && roots.length <= DEFAULT_ROOTS.length,
            "one or two fixed informed roots only");
        TreeSet<Long> unique = new TreeSet<Long>();
        for (long seed : roots)
            check(expected(seed) != null && unique.add(Long.valueOf(seed)),
                "root is one unique fixed accepted15 case: " + seed);
        for (long seed : roots) exportRoot(seed);
        System.out.println("PASS: PCB pocket decision diagnostic contracts roots=" + roots.length +
            " assertions=" + assertions + " measurementOnly=true causalClaim=NONE");
    }

    private static void exportRoot(long seed) {
        Expected expected = expected(seed);
        Rb56Plan sourcePlan = Rb56Plan.resolve(seed, VERSION);
        TroubleshootBoard board = sourcePlan.board();
        check(sourcePlan.seed == seed && sourcePlan.placementInterpretationVersion == VERSION,
            "current15 source plan is explicit: " + seed);
        check(board.getPlacementConstraints().placementDensityVersion == DENSITY,
            "current15 maps to density14: " + seed);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(sourcePlan, board);

        String sourceBefore = PcbCanvasBoundaryContractTest.sourcePlanSha256(sourcePlan);
        String graphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        String sourceIntent = PlayerSessionFingerprint.of(sourceIntent(sourcePlan));
        check(expected.sourcePlanSha.equals(sourceBefore),
            "accepted15 source plan receipt is exact: " + seed);
        check(expected.liveBoardGraphSha.equals(graphBefore),
            "accepted15 live source graph receipt is exact: " + seed);
        check(expected.sourceIntentSha.equals(sourceIntent),
            "accepted15 ordered source intent is exact: " + seed);

        Observation observer = new Observation(sourcePlan.layoutSeed, expected.selectedAttempt);
        PcbPlacementPlanner.DiagnosticObserver previous =
            PcbPlacementPlanner.installDiagnosticObserver(observer);
        MediumBoardPhysicalPolicy.Result route = null;
        SeededPcbLayoutGenerator.Session session = null;
        try {
            SeededPcbLayoutGenerator generator = new SeededPcbLayoutGenerator();
            session = generator.begin(board, sourcePlan.layoutSeed, sourcePlan.routingSeed);
            while (!session.advance()) { }
            route = session.mediumResult();
        } finally {
            PcbPlacementPlanner.restoreDiagnosticObserver(observer, previous);
        }

        check(route != null && route.accepted() && route.getFailure() == null &&
                route.getLayout() != null,
            "ordinary current15 route succeeds: " + seed);
        PcbBoardLayout layout = route.getLayout();
        MediumBoardPhysicalPolicy.Statistics stats = route.getStatistics();
        check(stats != null && stats.selectedPlacementAttempt == expected.selectedAttempt,
            "selected attempt matches the corrected fixed case: " + seed);
        check(stats.placementCandidates == 6 && stats.placementRejections == 0 &&
                stats.placementEvaluations > 0 && stats.placementEvaluations <= 6L * TRACE_CAP,
            "existing six candidate and 60000 evaluation bounds remain: " + seed);
        check(stats.routeAttempts == stats.oneFaceAttempts + stats.twoLayerAttempts &&
                stats.oneFaceAttempts <= 3 && stats.twoLayerAttempts <= 3 &&
                stats.routingOrders >= 0 && stats.routingExpansions >= 0,
            "existing three plus three route and routing work bounds remain: " + seed);
        check(stats.rankedPlacementAttempts.equals(expected.rankedAttempts),
            "accepted15 ranked attempts remain exact: " + seed);
        check(stats.placementEvaluations == expected.placementEvaluations &&
                stats.placementRejections == expected.placementRejections &&
                stats.oneFaceAttempts == expected.oneFaceAttempts &&
                stats.twoLayerAttempts == expected.twoLayerAttempts &&
                stats.routeAttempts == expected.routeAttempts &&
                stats.routingOrders == expected.routingOrders &&
                stats.routingExpansions == expected.routingExpansions &&
                stats.selectedRouteQualityMilli == expected.selectedQualityMilli,
            "accepted15 selected and work receipt fields are exact: " + seed);
        check(expected.statisticsCanonical.equals(stats.toCanonical()),
            "accepted15 canonical placement and route receipt is exact: " + seed);

        PcbPlacementPlanner.Plan actualSelectedPlan = selectedPlan(session,
            stats.selectedPlacementAttempt);
        String selectedPlanFingerprint = planFingerprint(actualSelectedPlan);
        String selectedPlanSha = PlayerSessionFingerprint.of(selectedPlanFingerprint);
        String sourceFingerprintBeforeValidation = planFingerprint(actualSelectedPlan);
        String geometryBeforeValidation = layout.geometryFingerprint();
        verifyAcceptedLayout(seed, expected, sourcePlan, board, layout, stats,
            sourceBefore, graphBefore, sourceIntent);
        String sourceAfter = PcbCanvasBoundaryContractTest.sourcePlanSha256(sourcePlan);
        String graphAfter = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        check(sourceBefore.equals(sourceAfter),
            "diagnostic preserves source plan: " + seed);
        check(graphBefore.equals(graphAfter),
            "diagnostic preserves live source graph: " + seed);
        check(sourceFingerprintBeforeValidation.equals(planFingerprint(actualSelectedPlan)),
            "read-only receipt checks preserve actual selected placement plan: " + seed);
        check(geometryBeforeValidation.equals(layout.geometryFingerprint()),
            "read-only receipt checks preserve routed geometry: " + seed);

        DiagnosticResult diagnostic = analyzeDiagnostic(seed, expected, board, observer,
            actualSelectedPlan, selectedPlanSha);
        String sourceAfterAnalysis = PcbCanvasBoundaryContractTest.sourcePlanSha256(sourcePlan);
        String graphAfterAnalysis = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        check(sourceAfter.equals(sourceAfterAnalysis),
            "diagnostic analysis preserves source plan: " + seed);
        check(graphAfter.equals(graphAfterAnalysis),
            "diagnostic analysis preserves live source graph: " + seed);
        check(sourceFingerprintBeforeValidation.equals(planFingerprint(actualSelectedPlan)),
            "diagnostic analysis preserves actual selected placement plan: " + seed);
        check(geometryBeforeValidation.equals(layout.geometryFingerprint()),
            "diagnostic analysis preserves routed geometry: " + seed);
        String json = jsonReceipt(seed, expected, sourcePlan, board, layout, stats,
            sourceIntent, sourceAfterAnalysis, graphAfterAnalysis, selectedPlanSha, diagnostic);
        System.out.println(json);
    }

    private static void verifyAcceptedLayout(long seed, Expected expected, Rb56Plan sourcePlan,
            TroubleshootBoard board, PcbBoardLayout layout,
            MediumBoardPhysicalPolicy.Statistics stats, String sourceBefore,
            String graphBefore, String sourceIntent) {
        Rectangle outline = layout.getBoardOutline();
        Rectangle occupied = layout.getOccupiedContentBounds();
        check(outline != null && outline.x == expected.outlineX &&
                outline.y == expected.outlineY && outline.width == expected.width &&
                outline.height == expected.height,
            "accepted15 final outline is exact: " + seed);
        check((long) outline.width * outline.height == expected.area,
            "accepted15 final area is exact: " + seed);
        check(layout.getComponents().size() == expected.parts &&
                layout.getPads().size() == expected.pads,
            "accepted15 final physical inventory counts are exact: " + seed);

        layout.validateGeometry(board);
        layout.validateRoutingGeometry(board);
        layout.validateRouteQuality();
        new PcbTwoLayerRules(board, layout).validate(layout);
        PcbTraceClearance.validate(layout.getTraces());
        verifyInventory(board, layout, seed);
        verifyRoleWidths(board, layout, seed);

        Vector<PcbFootprint> finalFootprints = reconstructFinalFootprints(board, layout, seed);
        MarginFacts margins = marginFacts(board, layout, finalFootprints, seed);
        check(margins.fixedMarginFloor == expected.fixedMarginFloor &&
                margins.accessEnvelopeMargin == expected.accessEnvelopeMargin &&
                margins.requiredMargin == expected.requiredMargin &&
                margins.legacyRequestedMargin == expected.legacyRequestedMargin &&
                margins.actualMargin == expected.actualMargin,
            "accepted15 final margin facts are exact: " + seed);
        check(occupied.x == expected.occupiedX && occupied.y == expected.occupiedY &&
                occupied.width == expected.occupiedWidth && occupied.height == expected.occupiedHeight,
            "accepted15 final occupied bounds are exact: " + seed);

        int protectedWhitespace = PcbFunctionalGroupingContractTest.reportWhitespace(
            board, layout, seed, VERSION);
        long copperLength = copperManhattanLength(layout);
        int vias = viaCount(layout);
        String geometrySha = PlayerSessionFingerprint.of(layout.geometryFingerprint());
        String sourceSha = PcbCanvasBoundaryContractTest.sourcePlanSha256(sourcePlan);
        String graphSha = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        check(protectedWhitespace == expected.protectedWhitespace &&
                copperLength == expected.copperLength && vias == expected.vias,
            "accepted15 route geometry and copper receipts are exact: " + seed);
        check(expected.geometrySha.equals(geometrySha),
            "accepted15 final geometry fingerprint is exact: " + seed);
        check(expected.sourcePlanSha.equals(sourceSha) && sourceBefore.equals(sourceSha),
            "accepted15 source plan fingerprint remains exact: " + seed);
        check(expected.liveBoardGraphSha.equals(graphSha) && graphBefore.equals(graphSha),
            "accepted15 live board graph fingerprint remains exact: " + seed);
        check(expected.sourceIntentSha.equals(sourceIntent),
            "accepted15 source intent fingerprint remains exact: " + seed);
        check(stats.selectedRoutePolicy.equals(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER),
            "accepted15 selected route policy is P07: " + seed);
    }

    private static void verifyInventory(TroubleshootBoard board, PcbBoardLayout layout,
            long seed) {
        TreeSet<String> components = new TreeSet<String>();
        for (PcbComponentPlacement component : layout.getComponents())
            check(components.add(component.getComponentId()),
                "unique final component id: " + seed + "/" + component.getComponentId());
        check(components.equals(new TreeSet<String>(board.getComponentIds())),
            "final component inventory equals source: " + seed);
        TreeSet<String> pads = new TreeSet<String>();
        for (PcbPadPlacement pad : layout.getPads())
            check(pads.add(pad.getPadId()), "unique final pad id: " + seed + "/" + pad.getPadId());
        check(pads.equals(new TreeSet<String>(board.getPadIds())),
            "final pad inventory equals source: " + seed);
        for (PcbComponentPlacement component : layout.getComponents()) {
            BoardComponent source = board.getComponent(component.getComponentId());
            check(source != null && source.getPhysicalPackage() == component.getPhysicalPackage() &&
                    source.getPhysicalPackage().acceptsGeometry(component.getPhysicalGeometry()),
                "final package and geometry retain source identity: " + seed + "/" +
                    component.getComponentId());
            PhysicalPackageGeometry geometry = component.getPhysicalGeometry();
            PhysicalPackageGeometry.Placement expected = geometry.placedAt(component.getPose());
            Vector<String> padIds = source.getPadIds();
            check(padIds.size() == geometry.getTerminals().size(),
                "source pads cover package terminals: " + seed + "/" + component.getComponentId());
            for (int index = 0; index < padIds.size(); index++) {
                String id = padIds.get(index);
                PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
                PcbPadPlacement pad = layout.getPad(id);
                check(id.equals(component.getComponentId() + "." + terminal.getTerminalId()) &&
                        pad != null && pad.getX() == expected.getPadPoint(index).x &&
                        pad.getY() == expected.getPadPoint(index).y &&
                        pad.getPadBounds().equals(expected.getPadBounds(index)) &&
                        pad.getProbeBounds().equals(expected.getBoardPadProbeBounds(index)) &&
                        pad.getEscapeDx() == expected.getEscapeDx(index) &&
                        pad.getEscapeDy() == expected.getEscapeDy(index) &&
                        pad.getEscapeLength() == expected.getEscapeLength(index),
                    "terminal and probe geometry follows source package pose: " + seed + "/" + id);
            }
        }
    }

    private static void verifyRoleWidths(TroubleshootBoard board,
            PcbBoardLayout layout, long seed) {
        PcbTraceWidthProfile profile = board.getPlacementConstraints().getTraceWidthProfile();
        check("ROLE_TRACE@1".equals(profile.getIdentity()) && profile.maximumWidth() == 13,
            "accepted route retains ROLE_TRACE@1 width profile: " + seed);
        for (PcbTraceGeometry trace : layout.getTraces()) {
            BoardNet net = board.getNet(trace.getNetId());
            check(net != null && trace.getWidth() == literalWidth(net.getRoutingRole()),
                "routed trace width follows its declared net role: " + seed + "/" + trace.getNetId());
        }
    }

    private static int literalWidth(BoardNet.RoutingRole role) {
        switch (role) {
        case HIGH_CURRENT: return 13;
        case SUPPLY:
        case RETURN: return 11;
        case CONTROL:
        case SIGNAL: return 9;
        default: throw new AssertionError("Unknown RB56 routing role: " + role);
        }
    }

    private static Vector<PcbFootprint> reconstructFinalFootprints(
            TroubleshootBoard board, PcbBoardLayout layout, long seed) {
        Vector<PcbFootprint> result = new Vector<PcbFootprint>();
        for (String componentId : board.getComponentIds()) {
            BoardComponent source = board.getComponent(componentId);
            PcbComponentPlacement placed = layout.getComponent(componentId);
            check(source != null && placed != null,
                "final footprint has source and placement: " + seed + "/" + componentId);
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            Vector<String> padIds = source.getPadIds();
            for (int index = 0; index < padIds.size(); index++) {
                String padId = padIds.get(index);
                PhysicalPackageGeometry.Terminal terminal = placed.getPhysicalGeometry().getTerminal(index);
                check(padId.equals(componentId + "." + terminal.getTerminalId()),
                    "final footprint follows terminal order: " + seed + "/" + padId);
                PcbPadPlacement pad = layout.getPad(padId);
                check(pad != null, "final footprint resolves routed pad: " + seed + "/" + padId);
                pads.add(pad);
            }
            result.add(new PcbFootprint(placed, pads));
        }
        check(result.size() == board.getComponentIds().size(),
            "final footprint covers complete component inventory: " + seed);
        return result;
    }

    private static MarginFacts marginFacts(TroubleshootBoard board,
            PcbBoardLayout layout, Vector<PcbFootprint> footprints, long seed) {
        Rectangle outline = layout.getBoardOutline();
        Rectangle occupied = layout.getOccupiedContentBounds();
        int fixed = 26;
        for (PcbPlacementConstraints.Part part : board.getPlacementConstraints().getParts())
            fixed = Math.max(fixed, part.accessMargin + 30);
        int access = 0;
        Rectangle allAccess = null;
        for (PcbFootprint footprint : footprints) {
            String id = footprint.getPlacement().getComponentId();
            PcbPlacementConstraints.Part demand = board.getPlacementConstraints().get(id);
            check(demand != null, "final footprint has demand: " + seed + "/" + id);
            Rectangle envelope = PcbPlacementPlanner.envelope(footprint, demand.accessMargin);
            allAccess = union(allAccess, envelope);
            access = Math.max(access, occupied.x - envelope.x + 10);
            access = Math.max(access, occupied.y - envelope.y + 10);
            access = Math.max(access, envelope.x + envelope.width - occupied.x - occupied.width + 10);
            access = Math.max(access, envelope.y + envelope.height - occupied.y - occupied.height + 10);
        }
        check(allAccess != null && fixed >= 26 && access >= 0,
            "final access margins are independently reconstructed: " + seed);
        long left = (long) occupied.x - outline.x;
        long top = (long) occupied.y - outline.y;
        long right = (long) outline.x + outline.width - occupied.x - occupied.width;
        long bottom = (long) outline.y + outline.height - occupied.y - occupied.height;
        check(left >= 0 && left == top && left == right && left == bottom,
            "final outline retains a uniform margin: " + seed);
        return new MarginFacts(fixed, access, Math.max(26, access),
            Math.max(fixed, access), (int) left);
    }

    private static MediumBoardPhysicalPolicy.PlacementCandidate selectedCandidate(
            SeededPcbLayoutGenerator.Session session, int selectedAttempt) {
        try {
            Field field = session.getClass().getDeclaredField("mediumPlacements");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Vector<MediumBoardPhysicalPolicy.PlacementCandidate> candidates =
                (Vector<MediumBoardPhysicalPolicy.PlacementCandidate>) field.get(session);
            MediumBoardPhysicalPolicy.PlacementCandidate selected = null;
            for (MediumBoardPhysicalPolicy.PlacementCandidate candidate : candidates)
                if (candidate.attempt == selectedAttempt) {
                    check(selected == null, "actual selected attempt has one candidate: " + selectedAttempt);
                    selected = candidate;
                }
            check(selected != null && selected.plan != null &&
                    selected.plan.candidate == selectedAttempt,
                "actual selected PlacementCandidate.plan is available: " + selectedAttempt);
            return selected;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect actual selected placement candidate", failure);
        }
    }

    private static PcbPlacementPlanner.Plan selectedPlan(
            SeededPcbLayoutGenerator.Session session, int selectedAttempt) {
        return selectedCandidate(session, selectedAttempt).plan;
    }

    private static DiagnosticResult analyzeDiagnostic(long seed, Expected expected,
            TroubleshootBoard board, Observation observation,
            PcbPlacementPlanner.Plan selectedPlan, String selectedPlanSha) {
        DiagnosticResult result = new DiagnosticResult();
        result.seed = seed;
        result.traceSeed = observation.seed;
        result.attempt = expected.selectedAttempt;
        result.traceRecords = observation.origins.size();
        result.traceCapped = observation.capped;
        result.chosenCount = observation.choices.size();
        result.rawPlan = observation.rawPlan;
        result.returnedPlan = observation.returnedPlan;
        result.rawPlanSha = result.rawPlan == null ? null :
            PlayerSessionFingerprint.of(planFingerprint(result.rawPlan));
        result.returnedPlanSha = result.returnedPlan == null ? null :
            PlayerSessionFingerprint.of(planFingerprint(result.returnedPlan));
        result.selectedPlanSha = selectedPlanSha;
        result.returnedMatchesSelected = result.returnedPlan != null &&
            planFingerprint(result.returnedPlan).equals(planFingerprint(selectedPlan));
        result.rawMatchesSelected = result.rawPlan != null &&
            planFingerprint(result.rawPlan).equals(planFingerprint(selectedPlan));
        TreeSet<String> rawIds = new TreeSet<String>();
        boolean uniqueRawIds = true;
        if (result.rawPlan != null) {
            for (PcbFootprint footprint : result.rawPlan.footprints) {
                PcbComponentPlacement raw = footprint.getPlacement();
                String componentId = raw.getComponentId();
                uniqueRawIds &= rawIds.add(componentId);
                PcbPlacementPlanner.DiagnosticChoice choice =
                    observation.choices.get(componentId);
                ChosenComparison comparison = new ChosenComparison(componentId, raw, choice);
                result.chosenComparisons.add(comparison);
                if (!comparison.comparable) addReason(result.reasons,
                    "CHOSEN_ORIGIN_MISMATCH:" + componentId);
            }
        }
        boolean planIdentityBound = result.rawPlan != null && result.returnedPlan != null &&
            result.returnedMatchesSelected &&
            result.rawPlan.candidate == expected.selectedAttempt &&
            result.returnedPlan.candidate == expected.selectedAttempt &&
            selectedPlan.candidate == expected.selectedAttempt;
        boolean choicesBound = result.chosenCount == board.getComponentIds().size() &&
            result.chosenComparisons.size() == board.getComponentIds().size() && uniqueRawIds &&
            rawIds.equals(new TreeSet<String>(board.getComponentIds())) &&
            rawIds.equals(observation.choices.keySet());
        for (ChosenComparison comparison : result.chosenComparisons)
            choicesBound &= comparison.comparable;
        boolean traceSequenceBound = true;
        for (int index = 0; index < observation.origins.size(); index++)
            traceSequenceBound &= observation.origins.get(index).sequence == index;
        result.bindingStatus = planIdentityBound && choicesBound &&
            traceSequenceBound && !result.traceCapped && result.traceRecords <= TRACE_CAP ?
            "BOUND" : "INCONCLUSIVE";
        if (result.rawPlan == null) addReason(result.reasons, "RAW_PLAN_SNAPSHOT_MISSING");
        if (result.returnedPlan == null) addReason(result.reasons, "RETURNED_PLAN_SNAPSHOT_MISSING");
        if (result.returnedPlan != null && !result.returnedMatchesSelected)
            addReason(result.reasons, "RETURNED_PLAN_SELECTED_MISMATCH");
        if (result.rawPlan != null && result.rawPlan.candidate != expected.selectedAttempt)
            addReason(result.reasons, "RAW_PLAN_ATTEMPT_MISMATCH");
        if (result.returnedPlan != null && result.returnedPlan.candidate != expected.selectedAttempt)
            addReason(result.reasons, "RETURNED_PLAN_ATTEMPT_MISMATCH");
        if (selectedPlan.candidate != expected.selectedAttempt)
            addReason(result.reasons, "ACTUAL_SELECTED_PLAN_ATTEMPT_MISMATCH");
        if (!choicesBound) addReason(result.reasons, "CHOSEN_PLACEMENT_SET_MISMATCH");
        if (!traceSequenceBound) addReason(result.reasons, "TRACE_SEQUENCE_MISMATCH");
        if (result.traceCapped || result.traceRecords > TRACE_CAP)
            addReason(result.reasons, "TRACE_CAPPED");
        if (!result.bindingStatus.equals("BOUND"))
            addReason(result.reasons, "PLAN_BINDING_INCONCLUSIVE");
        if (result.rawPlan == null) {
            result.rectangle = RectangleFacts.inconclusive("RAW_PLAN_SNAPSHOT_MISSING");
            return result;
        }

        Raster raster = new Raster(result.rawPlan, board.getPlacementConstraints());
        result.raster = raster;
        if (!raster.ready()) {
            result.rectangle = RectangleFacts.inconclusive(raster.failure, raster);
            addReason(result.reasons, raster.failure);
            return result;
        }
        result.rectangle = raster.largest();
        if (result.rectangle == null || !"PASS".equals(result.rectangle.status)) {
            addReason(result.reasons, "RAW_RECTANGLE_INCONCLUSIVE");
            return result;
        }
        if (result.rectangle.area <= 0) {
            addReason(result.reasons, "NO_EMPTY_RECTANGLE");
            return result;
        }
        SampleOutcome sample = sampleAlternatives(seed, board, observation,
            result.rawPlan, raster, result.rectangle);
        result.sample = sample;
        result.witness = sample.witness;
        for (String reason : sample.capReasons) addReason(result.reasons, reason);
        boolean completeBinding = result.bindingStatus.equals("BOUND") &&
            !result.traceCapped && !sample.capped;
        if (sample.witness != null && completeBinding) {
            result.witnessStatus = "WITNESS";
            result.status = "WITNESS";
        } else {
            result.witnessStatus = "INCONCLUSIVE";
            result.status = "INCONCLUSIVE";
            if (sample.witness != null)
                addReason(result.reasons, "WITNESS_NOT_CAUSALLY_BOUND");
            else if (!sample.capped && result.bindingStatus.equals("BOUND")) {
                result.witnessStatus = "NO_WITNESS_IN_DECLARED_SAMPLE";
                addReason(result.reasons, "NO_WITNESS_IN_DECLARED_SAMPLE");
            } else addReason(result.reasons, "DECLARED_SAMPLE_INCONCLUSIVE");
        }
        return result;
    }

    private static SampleOutcome sampleAlternatives(long seed, TroubleshootBoard board,
            Observation observation, PcbPlacementPlanner.Plan rawPlan, Raster raster,
            RectangleFacts baseline) {
        SampleOutcome outcome = new SampleOutcome();
        Vector<EnvelopeAtIndex> nearest = new Vector<EnvelopeAtIndex>();
        for (int index = 0; index < rawPlan.footprints.size(); index++) {
            PcbFootprint footprint = rawPlan.footprints.get(index);
            String id = footprint.getPlacement().getComponentId();
            int margin = board.getPlacementConstraints().get(id).accessMargin;
            Rectangle envelope = PcbPlacementPlanner.envelope(footprint, margin);
            nearest.add(new EnvelopeAtIndex(id, index, envelope,
                rectangleDistance(envelope, baseline.bounds)));
        }
        Collections.sort(nearest, new Comparator<EnvelopeAtIndex>() {
            public int compare(EnvelopeAtIndex first, EnvelopeAtIndex second) {
                if (first.distance != second.distance)
                    return first.distance < second.distance ? -1 : 1;
                return first.componentId.compareTo(second.componentId);
            }
        });
        int componentLimit = Math.min(COMPONENT_SAMPLE_CAP, nearest.size());
        outcome.componentSamples = new Vector<ComponentSample>();
        for (int componentIndex = 0; componentIndex < componentLimit; componentIndex++) {
            EnvelopeAtIndex target = nearest.get(componentIndex);
            PcbFootprint rawFootprint = rawPlan.footprints.get(target.footprintIndex);
            PcbPlacementPlanner.DiagnosticChoice recordedChoice =
                observation.choices.get(target.componentId);
            PcbComponentPlacement rawPlacement = rawFootprint.getPlacement();
            boolean coordinatesMatch = recordedChoice != null && recordedChoice.found &&
                recordedChoice.x == rawPlacement.getX() &&
                recordedChoice.y == rawPlacement.getY();
            boolean poseMatches = recordedChoice != null && recordedChoice.found &&
                rawPlacement.getRotation().name().equals(recordedChoice.rotation) &&
                rawPlacement.getMountingSide().name().equals(recordedChoice.mountingSide);
            boolean comparable = coordinatesMatch && poseMatches &&
                Double.isFinite(recordedChoice.effectiveScore);
            ComponentSample component = new ComponentSample(target.componentId,
                target.distance, rawPlacement.getX(), rawPlacement.getY(),
                rawPlacement.getRotation().name(), rawPlacement.getMountingSide().name(),
                recordedChoice, coordinatesMatch, poseMatches, comparable);
            Vector<PcbPlacementPlanner.DiagnosticOrigin> candidates =
                new Vector<PcbPlacementPlanner.DiagnosticOrigin>();
            String rotation = rawPlacement.getRotation().name();
            String mountingSide = rawPlacement.getMountingSide().name();
            for (PcbPlacementPlanner.DiagnosticOrigin origin : observation.origins)
                if (origin.componentId.equals(target.componentId) && origin.fits &&
                        rotation.equals(origin.rotation) && mountingSide.equals(origin.mountingSide))
                    candidates.add(origin);
            component.recordedSamePoseSuccesses = candidates.size();
            int originLimit = Math.min(ORIGIN_SAMPLE_CAP, candidates.size());
            boolean originSampleTruncated = candidates.size() > ORIGIN_SAMPLE_CAP;
            for (int originIndex = 0; originIndex < originLimit; originIndex++) {
                PcbPlacementPlanner.DiagnosticOrigin origin = candidates.get(originIndex);
                OriginResult attempt = new OriginResult(origin,
                    rotation.equals(origin.rotation) && mountingSide.equals(origin.mountingSide));
                component.origins.add(attempt);
                if (!attempt.poseMatches || (origin.x == component.rawX && origin.y == component.rawY)) {
                    attempt.fullValidation = origin.x == component.rawX && origin.y == component.rawY ?
                        "SKIPPED_BASELINE_ORIGIN" : "SKIPPED_POSE_MISMATCH";
                    continue;
                }
                PcbFootprint movedFootprint = rawFootprint.translated(origin.x, origin.y);
                Rectangle movedEnvelope = PcbPlacementPlanner.envelope(movedFootprint,
                    board.getPlacementConstraints().get(target.componentId).accessMargin);
                attempt.outsideMeasurementDomain = raster.outsideMeasurementDomain(movedEnvelope);
                if (!intersects(movedEnvelope, baseline.bounds)) {
                    // The search domain stays fixed at the raw envelope bounds, so this
                    // original rectangle remains an empty witness for the post-move raster.
                    attempt.fullValidation = "SKIPPED_NO_RECT_INTERSECTION";
                    attempt.rejectionReason = "NO_BASELINE_RECTANGLE_INTERSECTION";
                    continue;
                }
                if (outcome.rectangleRecomputations >= RECTANGLE_RECOMPUTE_CAP) {
                    outcome.cap("RECTANGLE_RECOMPUTE_CAP");
                    attempt.fullValidation = "NOT_CHECKED_RECTANGLE_CAP";
                    break;
                }
                if (outcome.wholePlanValidations >= WHOLE_PLAN_VALIDATION_CAP) {
                    outcome.cap("WHOLE_PLAN_VALIDATION_CAP");
                    attempt.fullValidation = "NOT_CHECKED_VALIDATION_CAP";
                    break;
                }
                Vector<PcbFootprint> moved = copyFootprints(rawPlan.footprints);
                moved.set(target.footprintIndex, movedFootprint);
                outcome.wholePlanValidations++;
                try {
                    PcbPlacementPlanner.validate(board, board.getPlacementConstraints(),
                        new Rectangle(rawPlan.outline), moved);
                    attempt.fullValidation = "PASS";
                    attempt.validationPassed = true;
                } catch (PcbPlacementPlanner.Rejected rejected) {
                    attempt.fullValidation = "REJECTED";
                    attempt.rejectionReason = rejected.reason;
                    continue;
                }
                int envelopeIndex = target.footprintIndex;
                Rectangle oldEnvelope = raster.envelopes.get(envelopeIndex).bounds;
                if (!raster.adjust(oldEnvelope, -1)) {
                    outcome.cap("OLD_ENVELOPE_OUTSIDE_RASTER");
                    attempt.fullValidation = "PASS_RECTANGLE_INCONCLUSIVE";
                    attempt.validationPassed = true;
                    break;
                }
                if (!raster.adjust(movedEnvelope, 1)) {
                    raster.adjust(oldEnvelope, 1);
                    outcome.cap("MOVED_ENVELOPE_OUTSIDE_RASTER");
                    attempt.fullValidation = "PASS_RECTANGLE_INCONCLUSIVE";
                    attempt.validationPassed = true;
                    break;
                }
                RectangleFacts after;
                try {
                    after = raster.largest();
                } finally {
                    raster.adjust(movedEnvelope, -1);
                    raster.adjust(oldEnvelope, 1);
                }
                if (after == null || !"PASS".equals(after.status)) {
                    outcome.cap("POST_MOVE_RECTANGLE_INCONCLUSIVE");
                    attempt.fullValidation = "PASS_RECTANGLE_INCONCLUSIVE";
                    attempt.validationPassed = true;
                    break;
                }
                outcome.rectangleRecomputations++;
                attempt.fullValidation = "PASS";
                attempt.validationPassed = true;
                attempt.postMoveRectangleArea = after == null ? -1L : after.area;
                attempt.strictlyReducesRawPocket = after != null && after.area < baseline.area;
                boolean scoreComparable = comparable && Double.isFinite(origin.effectiveScore) &&
                    Double.isFinite(recordedChoice.effectiveScore);
                attempt.scoreComparable = scoreComparable;
                attempt.strictlyWorseEffectiveScore = scoreComparable &&
                    origin.effectiveScore > recordedChoice.effectiveScore;
                if (attempt.validationPassed && attempt.strictlyReducesRawPocket &&
                        attempt.strictlyWorseEffectiveScore && outcome.witness == null)
                    outcome.witness = new Witness(target.componentId, origin,
                        recordedChoice.effectiveScore, origin.effectiveScore,
                        baseline.area, attempt.postMoveRectangleArea);
            }
            if (originSampleTruncated) outcome.cap("ORIGIN_SAMPLE_CAP:" + target.componentId);
            outcome.componentSamples.add(component);
            if (outcome.capped) break;
        }
        if (outcome.componentSamples.size() < componentLimit && !outcome.capped)
            outcome.cap("COMPONENT_SAMPLE_INCOMPLETE");
        return outcome;
    }

    private static void addReason(Vector<String> reasons, String reason) {
        if (reason != null && !reasons.contains(reason)) reasons.add(reason);
    }

    private static Vector<PcbFootprint> copyFootprints(Vector<PcbFootprint> input) {
        Vector<PcbFootprint> result = new Vector<PcbFootprint>();
        for (PcbFootprint footprint : input) {
            PcbComponentPlacement placement = footprint.getPlacement();
            result.add(footprint.translated(placement.getX(), placement.getY()));
        }
        return result;
    }

    private static long rectangleDistance(Rectangle first, Rectangle second) {
        long dx = first.x + (long) first.width <= second.x ?
            second.x - ((long) first.x + first.width) :
            second.x + (long) second.width <= first.x ?
                first.x - ((long) second.x + second.width) : 0L;
        long dy = first.y + (long) first.height <= second.y ?
            second.y - ((long) first.y + first.height) :
            second.y + (long) second.height <= first.y ?
                first.y - ((long) second.y + second.height) : 0L;
        return dx + dy;
    }

    private static boolean intersects(Rectangle first, Rectangle second) {
        return (long) first.x < (long) second.x + second.width &&
            (long) second.x < (long) first.x + first.width &&
            (long) first.y < (long) second.y + second.height &&
            (long) second.y < (long) first.y + first.height;
    }

    private static String planFingerprint(PcbPlacementPlanner.Plan plan) {
        StringBuilder value = new StringBuilder();
        append(value, plan.outline.x + "," + plan.outline.y + "," +
            plan.outline.width + "," + plan.outline.height);
        append(value, Long.toString(plan.courtyardArea));
        append(value, Long.toString(plan.demandArea));
        append(value, Integer.toString(plan.candidate));
        append(value, Integer.toString(plan.evaluations));
        value.append(plan.footprints.size()).append('[');
        for (PcbFootprint footprint : plan.footprints)
            append(value, footprint.geometryFingerprint());
        value.append(plan.regions.size()).append('[');
        for (PcbLayoutRegion region : plan.regions) {
            append(value, region.id); append(value, region.label);
            Vector<String> ids = region.getComponentIds();
            value.append(ids.size()).append('[');
            for (String id : ids) append(value, id);
            value.append(']');
        }
        value.append(']');
        return value.toString();
    }

    private static String sourceIntent(Rb56Plan plan) {
        StringBuilder value = new StringBuilder();
        append(value, Long.toString(plan.seed));
        append(value, Long.toString(plan.layoutSeed));
        append(value, Long.toString(plan.routingSeed));
        append(value, plan.topology());
        append(value, Integer.toString(plan.channelCount));
        append(value, Integer.toString(plan.sensorInputFilterMask));
        append(value, Integer.toString(plan.outputIndicatorMask));
        append(value, plan.referenceArrangement.name());
        append(value, Boolean.toString(plan.driverABjt));
        append(value, Boolean.toString(plan.driverBBjt));
        append(value, Boolean.toString(plan.hasFiveVoltIndicator));
        append(value, Boolean.toString(plan.hasTwelveVoltIndicator));
        append(value, Boolean.toString(plan.hasFiveVoltBleeder));
        Vector<Rb56Plan.Part> parts = plan.parts();
        value.append(parts.size()).append('[');
        for (Rb56Plan.Part part : parts) append(value, part.canonical());
        value.append(']');
        return value.toString();
    }

    private static void append(StringBuilder value, String text) {
        value.append(text.length()).append(':').append(text).append(';');
    }

    private static long copperManhattanLength(PcbBoardLayout layout) {
        long length = 0;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            int[] x = trace.getXPoints(), y = trace.getYPoints();
            check(x.length == y.length && x.length >= 2,
                "trace has complete route points: " + trace.getSourceId());
            for (int index = 1; index < x.length; index++)
                length += Math.abs((long) x[index] - x[index - 1]) +
                    Math.abs((long) y[index] - y[index - 1]);
        }
        return length;
    }

    private static int viaCount(PcbBoardLayout layout) {
        int count = 0;
        for (PcbBoardHole hole : layout.getHoles())
            if (hole.kind == PcbBoardHole.Kind.VIA) count++;
        return count;
    }

    private static Rectangle union(Rectangle first, Rectangle second) {
        if (first == null) return new Rectangle(second);
        int left = Math.min(first.x, second.x);
        int top = Math.min(first.y, second.y);
        int right = Math.max(first.x + first.width, second.x + second.width);
        int bottom = Math.max(first.y + first.height, second.y + second.height);
        return new Rectangle(left, top, right - left, bottom - top);
    }

    private static String jsonReceipt(long seed, Expected expected, Rb56Plan plan,
            TroubleshootBoard board, PcbBoardLayout layout,
            MediumBoardPhysicalPolicy.Statistics stats, String sourceIntent,
            String sourceAfter, String graphAfter, String selectedPlanSha,
            DiagnosticResult diagnostic) {
        Rectangle outline = layout.getBoardOutline();
        Rectangle occupied = layout.getOccupiedContentBounds();
        Vector<PcbFootprint> footprints = reconstructFinalFootprints(board, layout, seed);
        MarginFacts margins = marginFacts(board, layout, footprints, seed);
        StringBuilder json = new StringBuilder(15000);
        json.append("PCB_POCKET_DECISION_CASE {\"status\":\"PASS\"");
        field(json, "seed", Long.toString(seed));
        json.append(",\"version\":").append(VERSION);
        json.append(",\"density\":").append(board.getPlacementConstraints().placementDensityVersion);
        json.append(",\"parts\":").append(board.getComponentIds().size());
        json.append(",\"pads\":").append(board.getPadIds().size());
        json.append(",\"area\":").append((long) outline.width * outline.height);
        json.append(",\"width\":").append(outline.width);
        json.append(",\"height\":").append(outline.height);
        json.append(",\"largestProtectedEmptyRectangleArea\":").append(expected.protectedWhitespace);
        json.append(",\"copperManhattanLength\":").append(copperManhattanLength(layout));
        json.append(",\"vias\":").append(viaCount(layout));
        field(json, "geometrySha256", PlayerSessionFingerprint.of(layout.geometryFingerprint()));
        field(json, "sourcePlanSha256", sourceAfter);
        field(json, "liveBoardGraphSha256", graphAfter);
        field(json, "sourceIntentSha256", sourceIntent);
        field(json, "layoutSeed", Long.toString(plan.layoutSeed));
        field(json, "routingSeed", Long.toString(plan.routingSeed));
        json.append(",\"selectedAttempt\":").append(stats.selectedPlacementAttempt);
        intArray(json, "rankedPlacementAttempts", stats.rankedPlacementAttempts);
        json.append(",\"acceptedPlanEvaluations\":").append(stats.placementEvaluations);
        json.append(",\"placementRejections\":").append(stats.placementRejections);
        json.append(",\"oneFaceAttempts\":").append(stats.oneFaceAttempts);
        json.append(",\"twoLayerAttempts\":").append(stats.twoLayerAttempts);
        json.append(",\"routeAttempts\":").append(stats.routeAttempts);
        json.append(",\"routingOrders\":").append(stats.routingOrders);
        json.append(",\"routingExpansions\":").append(stats.routingExpansions);
        json.append(",\"selectedQualityMilli\":").append(stats.selectedRouteQualityMilli);
        field(json, "statisticsCanonical", stats.toCanonical());
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
        json.append(",\"selectedPlacementPlanSha256\":\"").append(selectedPlanSha).append('"');
        appendDiagnostic(json, diagnostic);
        json.append('}');
        return json.toString();
    }

    private static void appendDiagnostic(StringBuilder json, DiagnosticResult result) {
        json.append(",\"diagnostic\":{\"coordinateFrame\":\"RAW_PRE_PLACEMENT_COMPACTION\"");
        json.append(",\"seed\":").append(result.seed);
        json.append(",\"traceSeed\":").append(result.traceSeed);
        json.append(",\"selectedAttempt\":").append(result.attempt);
        json.append(",\"density\":").append(DENSITY);
        field(json, "status", result.status);
        field(json, "bindingStatus", result.bindingStatus);
        json.append(",\"trace\":{\"records\":").append(result.traceRecords)
            .append(",\"cap\":").append(TRACE_CAP)
            .append(",\"capped\":").append(result.traceCapped)
            .append(",\"chosenPlacements\":").append(result.chosenCount).append('}');
        nullableField(json, "rawPlanSha256", result.rawPlanSha);
        nullableField(json, "returnedPlanSha256", result.returnedPlanSha);
        nullableField(json, "selectedPlacementPlanSha256", result.selectedPlanSha);
        json.append(",\"returnedPlanMatchesSelected\":").append(result.returnedMatchesSelected);
        json.append(",\"rawPlanFingerprintMatchesSelected\":").append(result.rawMatchesSelected);
        json.append(",\"chosenComparisons\":[");
        for (int index = 0; index < result.chosenComparisons.size(); index++) {
            if (index != 0) json.append(',');
            appendChosenComparison(json, result.chosenComparisons.get(index));
        }
        json.append(']');
        json.append(",\"reasons\":[");
        for (int index = 0; index < result.reasons.size(); index++) {
            if (index != 0) json.append(',');
            json.append('\"').append(escape(result.reasons.get(index))).append('\"');
        }
        json.append(']');
        appendRectangleFacts(json, "rawAccessEnvelopeRectangle", result.rectangle);
        appendSample(json, result.sample);
        appendWitness(json, result.witnessStatus, result.witness);
        json.append('}');
    }

    private static void appendChosenComparison(StringBuilder json, ChosenComparison comparison) {
        json.append("{\"componentId\":\"").append(escape(comparison.componentId)).append('"');
        json.append(",\"rawX\":").append(comparison.rawX);
        json.append(",\"rawY\":").append(comparison.rawY);
        field(json, "rawRotation", comparison.rawRotation);
        field(json, "rawMountingSide", comparison.rawMountingSide);
        nullableInteger(json, "recordedX", comparison.recordedX);
        nullableInteger(json, "recordedY", comparison.recordedY);
        nullableField(json, "recordedRotation", comparison.recordedRotation);
        nullableField(json, "recordedMountingSide", comparison.recordedMountingSide);
        json.append(",\"choiceFound\":").append(comparison.choiceFound);
        json.append(",\"coordinatesMatch\":").append(comparison.coordinatesMatch);
        json.append(",\"poseMatches\":").append(comparison.poseMatches);
        json.append(",\"comparable\":").append(comparison.comparable);
        nullableNumber(json, "chosenScore", comparison.chosenScore);
        json.append('}');
    }

    private static void appendRectangleFacts(StringBuilder json, String name,
            RectangleFacts rectangle) {
        json.append(",\"").append(name).append("\":{\"status\":\"")
            .append(rectangle == null ? "INCONCLUSIVE" : rectangle.status).append('"');
        json.append(",\"grid\":").append(GRID);
        json.append(",\"cells\":").append(rectangle == null ? 0 : rectangle.cells);
        json.append(",\"rasterCells\":").append(rectangle == null ? 0 : rectangle.rasterCells);
        json.append(",\"rasterCellCount\":").append(rectangle == null ? 0L : rectangle.rasterCellCount);
        json.append(",\"emptyRectangleCells\":").append(rectangle == null ? 0 : rectangle.emptyRectangleCells);
        json.append(",\"domainCellCount\":").append(rectangle == null ? 0 : rectangle.domainCellCount);
        json.append(",\"domainPolicy\":\"FIXED_RAW_ENVELOPE_BOUNDS_CLIPPED\"");
        if (rectangle != null && rectangle.domainWidth > 0 && rectangle.domainHeight > 0) {
            json.append(",\"domainX\":").append(rectangle.domainX);
            json.append(",\"domainY\":").append(rectangle.domainY);
            json.append(",\"domainWidth\":").append(rectangle.domainWidth);
            json.append(",\"domainHeight\":").append(rectangle.domainHeight);
        } else json.append(",\"domainX\":null,\"domainY\":null,\"domainWidth\":null,\"domainHeight\":null");
        json.append(",\"area\":").append(rectangle == null ? 0 : rectangle.area);
        if (rectangle != null && rectangle.bounds != null) {
            json.append(",\"x\":").append(rectangle.bounds.x);
            json.append(",\"y\":").append(rectangle.bounds.y);
            json.append(",\"width\":").append(rectangle.bounds.width);
            json.append(",\"height\":").append(rectangle.bounds.height);
        } else json.append(",\"x\":null,\"y\":null,\"width\":null,\"height\":null");
        json.append(",\"occupancy\":\"UNION_OF_FULL_ACCESS_ENVELOPES\"");
        if (rectangle != null && rectangle.reason != null)
            field(json, "reason", rectangle.reason);
        json.append('}');
    }

    private static void appendSample(StringBuilder json, SampleOutcome sample) {
        json.append(",\"sample\":{\"componentLimit\":").append(COMPONENT_SAMPLE_CAP)
            .append(",\"originsPerComponentLimit\":").append(ORIGIN_SAMPLE_CAP)
            .append(",\"wholePlanValidationLimit\":").append(WHOLE_PLAN_VALIDATION_CAP)
            .append(",\"wholePlanValidations\":").append(sample == null ? 0 : sample.wholePlanValidations)
            .append(",\"rectangleRecomputeLimit\":").append(RECTANGLE_RECOMPUTE_CAP)
            .append(",\"rectangleRecomputations\":").append(sample == null ? 0 : sample.rectangleRecomputations)
            .append(",\"capped\":").append(sample != null && sample.capped)
            .append(",\"capReasons\":[");
        if (sample != null) for (int index = 0; index < sample.capReasons.size(); index++) {
            if (index != 0) json.append(',');
            json.append('\"').append(escape(sample.capReasons.get(index))).append('\"');
        }
        json.append(']').append(",\"components\":[");
        if (sample != null) for (int index = 0; index < sample.componentSamples.size(); index++) {
            if (index != 0) json.append(',');
            ComponentSample component = sample.componentSamples.get(index);
            json.append("{\"componentId\":\"").append(escape(component.componentId)).append('"');
            json.append(",\"nearestDistance\":").append(component.nearestDistance);
            json.append(",\"rawChosenX\":").append(component.rawX);
            json.append(",\"rawChosenY\":").append(component.rawY);
            field(json, "rawChosenRotation", component.rawRotation);
            field(json, "rawChosenMountingSide", component.rawMountingSide);
            nullableInteger(json, "recordedChosenX", component.recordedX);
            nullableInteger(json, "recordedChosenY", component.recordedY);
            nullableField(json, "recordedChosenRotation", component.recordedRotation);
            nullableField(json, "recordedChosenMountingSide", component.recordedMountingSide);
            json.append(",\"chosenCoordinatesMatch\":").append(component.coordinatesMatch);
            json.append(",\"chosenPoseMatches\":").append(component.poseMatches);
            json.append(",\"chosenComparable\":").append(component.comparable);
            nullableNumber(json, "chosenScore", component.chosenScore);
            json.append(",\"recordedSamePoseSuccesses\":").append(component.recordedSamePoseSuccesses);
            json.append(",\"sampledOrigins\":").append(component.origins.size());
            json.append(",\"origins\":[");
            for (int originIndex = 0; originIndex < component.origins.size(); originIndex++) {
                if (originIndex != 0) json.append(',');
                appendOrigin(json, component.origins.get(originIndex));
            }
            json.append("]}");
        }
        json.append("]}");
    }

    private static void appendOrigin(StringBuilder json, OriginResult attempt) {
        PcbPlacementPlanner.DiagnosticOrigin origin = attempt.origin;
        json.append("{\"recordedOriginSequence\":").append(origin.sequence);
        json.append(",\"x\":").append(origin.x).append(",\"y\":").append(origin.y);
        json.append(",\"rotation\":\"").append(origin.rotation).append('"');
        json.append(",\"mountingSide\":\"").append(origin.mountingSide).append('"');
        json.append(",\"fit\":").append(origin.fits);
        nullableNumber(json, "loggedEffectiveScore", origin.effectiveScore);
        json.append(",\"poseMatches\":").append(attempt.poseMatches);
        field(json, "fullValidation", attempt.fullValidation);
        json.append(",\"validationPassed\":").append(attempt.validationPassed);
        json.append(",\"outsideMeasurementDomain\":").append(attempt.outsideMeasurementDomain);
        if (attempt.rejectionReason != null) field(json, "rejectionReason", attempt.rejectionReason);
        if (attempt.postMoveRectangleArea >= 0)
            json.append(",\"postMoveRectangleArea\":").append(attempt.postMoveRectangleArea);
        json.append(",\"strictlyReducesRawPocket\":").append(attempt.strictlyReducesRawPocket);
        json.append(",\"scoreComparable\":").append(attempt.scoreComparable);
        json.append(",\"strictlyWorseEffectiveScore\":").append(attempt.strictlyWorseEffectiveScore);
        json.append('}');
    }

    private static void appendWitness(StringBuilder json, String status, Witness witness) {
        json.append(",\"witness\":{\"status\":\"").append(status).append('"');
        if (witness == null) {
            json.append(",\"componentId\":null,\"recordedOriginSequence\":null");
            json.append(",\"x\":null,\"y\":null,\"chosenScore\":null,\"alternativeScore\":null");
            json.append(",\"beforeArea\":null,\"afterArea\":null,\"poseMatches\":false");
            json.append(",\"validationPassed\":false,\"strictlyReducesRawPocket\":false");
            json.append(",\"strictlyWorseEffectiveScore\":false");
        } else {
            json.append(",\"componentId\":\"").append(escape(witness.componentId)).append('"');
            json.append(",\"recordedOriginSequence\":").append(witness.origin.sequence);
            json.append(",\"x\":").append(witness.origin.x).append(",\"y\":").append(witness.origin.y);
            json.append(",\"chosenScore\":").append(witness.chosenScore);
            json.append(",\"alternativeScore\":").append(witness.alternativeScore);
            json.append(",\"beforeArea\":").append(witness.beforeArea);
            json.append(",\"afterArea\":").append(witness.afterArea);
            json.append(",\"poseMatches\":true,\"validationPassed\":true");
            json.append(",\"strictlyReducesRawPocket\":true,\"strictlyWorseEffectiveScore\":true");
        }
        json.append('}');
    }

    private static void intArray(StringBuilder json, String name, Vector<Integer> values) {
        json.append(",\"").append(name).append("\":[");
        for (int index = 0; index < values.size(); index++) {
            if (index != 0) json.append(',');
            json.append(values.get(index).intValue());
        }
        json.append(']');
    }

    private static void field(StringBuilder json, String name, String value) {
        json.append(",\"").append(name).append("\":\"").append(escape(value)).append('"');
    }

    private static void nullableField(StringBuilder json, String name, String value) {
        if (value == null) json.append(",\"").append(name).append("\":null");
        else field(json, name, value);
    }

    private static void nullableNumber(StringBuilder json, String name, double value) {
        json.append(",\"").append(name).append("\":");
        if (Double.isNaN(value) || Double.isInfinite(value)) json.append("null");
        else json.append(Double.toString(value));
    }

    private static void nullableInteger(StringBuilder json, String name, Integer value) {
        json.append(",\"").append(name).append("\":");
        if (value == null) json.append("null");
        else json.append(value.intValue());
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' || ch == '\\') escaped.append('\\').append(ch);
            else if (ch == '\n') escaped.append("\\n");
            else if (ch == '\r') escaped.append("\\r");
            else if (ch == '\t') escaped.append("\\t");
            else if (ch < 0x20) escaped.append(' ');
            else escaped.append(ch);
        }
        return escaped.toString();
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static Expected expected(long seed) {
        if (seed == -1022L) return Expected.ROOT_NEGATIVE_1022;
        if (seed == 42L) return Expected.ROOT_42;
        return null;
    }

    private static final class Observation implements PcbPlacementPlanner.DiagnosticObserver {
        final long seed;
        final int attempt;
        final Vector<PcbPlacementPlanner.DiagnosticOrigin> origins =
            new Vector<PcbPlacementPlanner.DiagnosticOrigin>();
        final TreeMap<String, PcbPlacementPlanner.DiagnosticChoice> choices =
            new TreeMap<String, PcbPlacementPlanner.DiagnosticChoice>();
        PcbPlacementPlanner.Plan rawPlan, returnedPlan;
        boolean capped;
        Observation(long seed, int attempt) { this.seed = seed; this.attempt = attempt; }
        public boolean matches(long candidateSeed, int candidateAttempt, int density) {
            return candidateSeed == seed && candidateAttempt == attempt && density == DENSITY;
        }
        public void origin(PcbPlacementPlanner.DiagnosticOrigin origin) { origins.add(origin); }
        public void chosen(PcbPlacementPlanner.DiagnosticChoice choice) {
            if (choices.put(choice.componentId, choice) != null)
                throw new AssertionError("Duplicate chosen component callback: " + choice.componentId);
        }
        public void rawPlan(PcbPlacementPlanner.Plan snapshot) {
            if (rawPlan != null) throw new AssertionError("Duplicate raw-plan callback");
            rawPlan = snapshot;
        }
        public void returnedPlan(PcbPlacementPlanner.Plan snapshot) {
            if (returnedPlan != null) throw new AssertionError("Duplicate returned-plan callback");
            returnedPlan = snapshot;
        }
        public void traceCapped(long candidateSeed, int candidateAttempt, int cap) {
            if (candidateSeed != seed || candidateAttempt != attempt || cap != TRACE_CAP)
                throw new AssertionError("Unexpected diagnostic trace cap receipt");
            capped = true;
        }
    }

    private static final class DiagnosticResult {
        String status = "INCONCLUSIVE", bindingStatus = "INCONCLUSIVE";
        long seed, traceSeed;
        int attempt, traceRecords, chosenCount;
        boolean traceCapped, returnedMatchesSelected, rawMatchesSelected;
        String rawPlanSha, returnedPlanSha, selectedPlanSha, witnessStatus = "INCONCLUSIVE";
        final Vector<String> reasons = new Vector<String>();
        final Vector<ChosenComparison> chosenComparisons = new Vector<ChosenComparison>();
        PcbPlacementPlanner.Plan rawPlan, returnedPlan;
        RectangleFacts rectangle;
        Raster raster;
        SampleOutcome sample;
        Witness witness;
    }

    private static final class SampleOutcome {
        Vector<ComponentSample> componentSamples = new Vector<ComponentSample>();
        final Vector<String> capReasons = new Vector<String>();
        int wholePlanValidations, rectangleRecomputations;
        boolean capped;
        Witness witness;
        void cap(String reason) {
            capped = true;
            addReason(capReasons, reason);
        }
    }

    private static final class ChosenComparison {
        final String componentId, rawRotation, rawMountingSide, recordedRotation,
            recordedMountingSide;
        final int rawX, rawY;
        final Integer recordedX, recordedY;
        final boolean choiceFound, coordinatesMatch, poseMatches, comparable;
        final double chosenScore;
        ChosenComparison(String componentId, PcbComponentPlacement raw,
                PcbPlacementPlanner.DiagnosticChoice choice) {
            this.componentId = componentId;
            rawX = raw.getX(); rawY = raw.getY();
            rawRotation = raw.getRotation().name();
            rawMountingSide = raw.getMountingSide().name();
            choiceFound = choice != null && choice.found;
            recordedX = choiceFound ? Integer.valueOf(choice.x) : null;
            recordedY = choiceFound ? Integer.valueOf(choice.y) : null;
            recordedRotation = choiceFound ? choice.rotation : null;
            recordedMountingSide = choiceFound ? choice.mountingSide : null;
            coordinatesMatch = choiceFound && rawX == choice.x && rawY == choice.y;
            poseMatches = choiceFound && rawRotation.equals(choice.rotation) &&
                rawMountingSide.equals(choice.mountingSide);
            chosenScore = choiceFound ? choice.effectiveScore : Double.NaN;
            comparable = coordinatesMatch && poseMatches && Double.isFinite(chosenScore);
        }
    }

    private static final class ComponentSample {
        final String componentId;
        final long nearestDistance;
        final int rawX, rawY;
        final String rawRotation, rawMountingSide, recordedRotation, recordedMountingSide;
        final Integer recordedX, recordedY;
        final boolean coordinatesMatch, poseMatches, comparable;
        final double chosenScore;
        int recordedSamePoseSuccesses;
        final Vector<OriginResult> origins = new Vector<OriginResult>();
        ComponentSample(String componentId, long nearestDistance, int rawX, int rawY,
                String rawRotation, String rawMountingSide,
                PcbPlacementPlanner.DiagnosticChoice choice, boolean coordinatesMatch,
                boolean poseMatches, boolean comparable) {
            this.componentId = componentId; this.nearestDistance = nearestDistance;
            this.rawX = rawX; this.rawY = rawY; this.comparable = comparable;
            choiceFound = choice != null && choice.found;
            recordedX = choiceFound ? Integer.valueOf(choice.x) : null;
            recordedY = choiceFound ? Integer.valueOf(choice.y) : null;
            recordedRotation = choiceFound ? choice.rotation : null;
            recordedMountingSide = choiceFound ? choice.mountingSide : null;
            this.coordinatesMatch = coordinatesMatch; this.poseMatches = poseMatches;
            this.chosenScore = choiceFound ? choice.effectiveScore : Double.NaN;
            this.rawRotation = rawRotation; this.rawMountingSide = rawMountingSide;
        }
        final boolean choiceFound;
    }

    private static final class OriginResult {
        final PcbPlacementPlanner.DiagnosticOrigin origin;
        final boolean poseMatches;
        String fullValidation = "NOT_CHECKED";
        boolean validationPassed, strictlyReducesRawPocket, scoreComparable,
            strictlyWorseEffectiveScore, outsideMeasurementDomain;
        long postMoveRectangleArea = -1L;
        String rejectionReason;
        OriginResult(PcbPlacementPlanner.DiagnosticOrigin origin, boolean poseMatches) {
            this.origin = origin; this.poseMatches = poseMatches;
        }
    }

    private static final class Witness {
        final String componentId;
        final PcbPlacementPlanner.DiagnosticOrigin origin;
        final double chosenScore, alternativeScore;
        final long beforeArea, afterArea;
        Witness(String componentId, PcbPlacementPlanner.DiagnosticOrigin origin,
                double chosenScore, double alternativeScore, long beforeArea, long afterArea) {
            this.componentId = componentId; this.origin = origin;
            this.chosenScore = chosenScore; this.alternativeScore = alternativeScore;
            this.beforeArea = beforeArea; this.afterArea = afterArea;
        }
    }

    private static final class EnvelopeAtIndex {
        final String componentId;
        final int footprintIndex;
        final Rectangle bounds;
        final long distance;
        EnvelopeAtIndex(String componentId, int footprintIndex, Rectangle bounds, long distance) {
            this.componentId = componentId; this.footprintIndex = footprintIndex;
            this.bounds = new Rectangle(bounds); this.distance = distance;
        }
    }

    private static final class RectangleFacts {
        final String status, reason;
        final int cells, rasterCells, emptyRectangleCells,
            domainX, domainY, domainWidth, domainHeight;
        final long area, rasterCellCount, domainCellCount;
        final Rectangle bounds;
        RectangleFacts(String status, int rasterCells, int emptyRectangleCells,
                long area, Rectangle bounds, String reason, long rasterCellCount,
                long domainCellCount,
                int domainX, int domainY, int domainWidth, int domainHeight) {
            this.status = status; this.cells = rasterCells; this.rasterCells = rasterCells;
            this.emptyRectangleCells = emptyRectangleCells; this.area = area;
            this.bounds = bounds == null ? null : new Rectangle(bounds); this.reason = reason;
            this.rasterCellCount = rasterCellCount;
            this.domainCellCount = domainCellCount; this.domainX = domainX; this.domainY = domainY;
            this.domainWidth = domainWidth; this.domainHeight = domainHeight;
        }
        static RectangleFacts inconclusive(String reason) {
            return new RectangleFacts("INCONCLUSIVE", 0, 0, 0L, null, reason,
                0L, 0L, 0, 0, 0, 0);
        }
        static RectangleFacts inconclusive(String reason, Raster raster) {
            return new RectangleFacts("INCONCLUSIVE", raster.cells, 0, 0L, null, reason,
                raster.rasterCellCount, raster.domainCellCount, raster.domainX, raster.domainY,
                raster.domainWidth, raster.domainHeight);
        }
    }

    /** Fixed-grid occupancy counts support reversible one-footprint trial updates. */
    private static final class Raster {
        final int originX, originY, columns, rows;
        int cells;
        final int domainStartX, domainStartY, domainWidth, domainHeight,
            domainX, domainY, domainPixelWidth, domainPixelHeight;
        final long rasterCellCount, domainCellCount;
        final int[] occupied;
        final Vector<EnvelopeAtIndex> envelopes = new Vector<EnvelopeAtIndex>();
        final String failure;
        Raster(PcbPlacementPlanner.Plan plan, PcbPlacementConstraints constraints) {
            Rectangle outline = plan.outline;
            originX = floorGrid(outline.x);
            originY = floorGrid(outline.y);
            long endX = ceilGrid((long) outline.x + outline.width);
            long endY = ceilGrid((long) outline.y + outline.height);
            long columnCount = (endX - originX) / GRID;
            long rowCount = (endY - originY) / GRID;
            long cellCount = columnCount * rowCount;
            columns = columnCount > 0 && columnCount <= Integer.MAX_VALUE ? (int) columnCount : 0;
            rows = rowCount > 0 && rowCount <= Integer.MAX_VALUE ? (int) rowCount : 0;
            rasterCellCount = Math.max(0L, cellCount);
            cells = 0;
            long left = Long.MAX_VALUE, top = Long.MAX_VALUE;
            long right = Long.MIN_VALUE, bottom = Long.MIN_VALUE;
            for (int index = 0; index < plan.footprints.size(); index++) {
                PcbFootprint footprint = plan.footprints.get(index);
                String id = footprint.getPlacement().getComponentId();
                Rectangle envelope = PcbPlacementPlanner.envelope(footprint,
                    constraints.get(id).accessMargin);
                EnvelopeAtIndex entry = new EnvelopeAtIndex(id, index, envelope, 0L);
                envelopes.add(entry);
                left = Math.min(left, envelope.x);
                top = Math.min(top, envelope.y);
                right = Math.max(right, (long) envelope.x + envelope.width);
                bottom = Math.max(bottom, (long) envelope.y + envelope.height);
            }
            if (envelopes.isEmpty()) {
                domainStartX = domainStartY = domainWidth = domainHeight = 0;
                domainX = domainY = domainPixelWidth = domainPixelHeight = 0;
                domainCellCount = 0L;
                occupied = null;
                failure = "NO_ACCESS_ENVELOPES";
                return;
            }
            domainX = floorGrid((int) left);
            domainY = floorGrid((int) top);
            long domainRight = ceilGrid(right);
            long domainBottom = ceilGrid(bottom);
            domainPixelWidth = (int) (domainRight - domainX);
            domainPixelHeight = (int) (domainBottom - domainY);
            long startX = Math.floorDiv((long) domainX - originX, GRID);
            long startY = Math.floorDiv((long) domainY - originY, GRID);
            long endDomainX = Math.floorDiv(domainRight - originX, GRID);
            long endDomainY = Math.floorDiv(domainBottom - originY, GRID);
            domainStartX = startX >= Integer.MIN_VALUE && startX <= Integer.MAX_VALUE ? (int) startX : 0;
            domainStartY = startY >= Integer.MIN_VALUE && startY <= Integer.MAX_VALUE ? (int) startY : 0;
            long width = endDomainX - startX, height = endDomainY - startY;
            domainWidth = width > 0 && width <= Integer.MAX_VALUE ? (int) width : 0;
            domainHeight = height > 0 && height <= Integer.MAX_VALUE ? (int) height : 0;
            domainCellCount = width > 0 && height > 0 ? width * height : 0L;
            if (columnCount <= 0 || rowCount <= 0 || columnCount > Integer.MAX_VALUE ||
                    rowCount > Integer.MAX_VALUE || cellCount > RASTER_CELL_CAP) {
                occupied = null;
                failure = "RASTER_CELL_CAP";
                return;
            }
            cells = (int) cellCount;
            occupied = new int[cells];
            String invalid = null;
            for (EnvelopeAtIndex envelope : envelopes)
                if (!adjust(envelope.bounds, 1)) invalid = "ACCESS_ENVELOPE_OUTSIDE_RAW_RASTER";
            if (domainStartX < 0 || domainStartY < 0 ||
                    (long) domainStartX + domainWidth > columns ||
                    (long) domainStartY + domainHeight > rows ||
                    domainWidth <= 0 || domainHeight <= 0)
                invalid = "RAW_ENVELOPE_DOMAIN_OUTSIDE_OUTLINE";
            failure = invalid;
        }
        boolean ready() { return occupied != null && failure == null; }
        boolean adjust(Rectangle rectangle, int delta) {
            long x0 = Math.floorDiv((long) rectangle.x - originX, GRID);
            long y0 = Math.floorDiv((long) rectangle.y - originY, GRID);
            long x1 = ceilDiv((long) rectangle.x + rectangle.width - originX, GRID);
            long y1 = ceilDiv((long) rectangle.y + rectangle.height - originY, GRID);
            if (x0 < 0 || y0 < 0 || x1 > columns || y1 > rows || x1 <= x0 || y1 <= y0)
                return false;
            for (int y = (int) y0; y < (int) y1; y++)
                for (int x = (int) x0; x < (int) x1; x++) {
                    int offset = y * columns + x;
                    occupied[offset] += delta;
                    if (occupied[offset] < 0)
                        throw new IllegalStateException("PCB pocket occupancy count underflow");
                }
            return true;
        }
        boolean outsideMeasurementDomain(Rectangle rectangle) {
            return (long) rectangle.x < domainX || (long) rectangle.y < domainY ||
                (long) rectangle.x + rectangle.width > (long) domainX + domainPixelWidth ||
                (long) rectangle.y + rectangle.height > (long) domainY + domainPixelHeight;
        }
        RectangleFacts largest() {
            if (!ready()) return RectangleFacts.inconclusive(failure, this);
            int[] heights = new int[domainWidth];
            int[] stack = new int[domainWidth + 1];
            int bestCells = 0, bestX = 0, bestY = 0, bestWidth = 0, bestHeight = 0;
            for (int row = 0; row < domainHeight; row++) {
                int absoluteRow = domainStartY + row;
                for (int column = 0; column < domainWidth; column++)
                    heights[column] = occupied[absoluteRow * columns + domainStartX + column] == 0 ?
                        heights[column] + 1 : 0;
                int stackSize = 0;
                for (int column = 0; column <= domainWidth; column++) {
                    int currentHeight = column == domainWidth ? 0 : heights[column];
                    while (stackSize > 0 && heights[stack[stackSize - 1]] > currentHeight) {
                        int rectangleHeight = heights[stack[--stackSize]];
                        int rectangleLeft = stackSize == 0 ? 0 : stack[stackSize - 1] + 1;
                        int rectangleWidth = column - rectangleLeft;
                        int area = rectangleHeight * rectangleWidth;
                        int rectangleY = row - rectangleHeight + 1;
                        if (area > bestCells || area == bestCells && area > 0 &&
                                canonicalBefore(rectangleY, rectangleLeft, rectangleHeight,
                                    rectangleWidth, bestY, bestX, bestHeight, bestWidth)) {
                            bestCells = area; bestX = rectangleLeft; bestY = rectangleY;
                            bestWidth = rectangleWidth; bestHeight = rectangleHeight;
                        }
                    }
                    if (column < domainWidth) stack[stackSize++] = column;
                }
            }
            Rectangle best = bestCells == 0 ? null : new Rectangle(
                originX + (domainStartX + bestX) * GRID,
                originY + (domainStartY + bestY) * GRID,
                bestWidth * GRID, bestHeight * GRID);
            return new RectangleFacts("PASS", cells, bestCells,
                (long) bestCells * GRID * GRID, best, null, rasterCellCount,
                domainCellCount, domainX, domainY, domainPixelWidth, domainPixelHeight);
        }
        private static boolean canonicalBefore(int y, int x, int height, int width,
                int oldY, int oldX, int oldHeight, int oldWidth) {
            if (y != oldY) return y < oldY;
            if (x != oldX) return x < oldX;
            if (height != oldHeight) return height < oldHeight;
            return width < oldWidth;
        }
        private static int floorGrid(int value) {
            return (int) (Math.floorDiv((long) value, GRID) * GRID);
        }
        private static long ceilGrid(long value) { return ceilDiv(value, GRID) * GRID; }
        private static long ceilDiv(long value, long divisor) {
            long quotient = Math.floorDiv(value, divisor);
            return value == quotient * divisor ? quotient : quotient + 1L;
        }
    }

    private static final class Expected {
        final long seed, area, copperLength, placementEvaluations, routingExpansions;
        final int selectedAttempt, parts, pads, width, height, protectedWhitespace, vias,
            placementRejections, oneFaceAttempts, twoLayerAttempts, routeAttempts,
            routingOrders, selectedQualityMilli, fixedMarginFloor, accessEnvelopeMargin,
            requiredMargin, legacyRequestedMargin, actualMargin, occupiedX, occupiedY,
            occupiedWidth, occupiedHeight, outlineX, outlineY;
        final String geometrySha, sourcePlanSha, liveBoardGraphSha, sourceIntentSha,
            layoutSeed, routingSeed, statisticsCanonical;
        final Vector<Integer> rankedAttempts;
        Expected(long seed, int selectedAttempt, int parts, int pads, long area,
                int width, int height, int protectedWhitespace, long copperLength, int vias,
                String geometrySha, String sourcePlanSha, String liveBoardGraphSha,
                String sourceIntentSha, String layoutSeed, String routingSeed,
                int[] rankedAttempts, long placementEvaluations, int placementRejections,
                int oneFaceAttempts, int twoLayerAttempts, int routeAttempts,
                int routingOrders, long routingExpansions, int selectedQualityMilli,
                int fixedMarginFloor, int accessEnvelopeMargin, int requiredMargin,
                int legacyRequestedMargin, int actualMargin, int occupiedX, int occupiedY,
                int occupiedWidth, int occupiedHeight, int outlineX, int outlineY,
                String statisticsCanonical) {
            this.seed = seed; this.selectedAttempt = selectedAttempt; this.parts = parts;
            this.pads = pads; this.area = area; this.width = width; this.height = height;
            this.protectedWhitespace = protectedWhitespace; this.copperLength = copperLength;
            this.vias = vias; this.geometrySha = geometrySha; this.sourcePlanSha = sourcePlanSha;
            this.liveBoardGraphSha = liveBoardGraphSha; this.sourceIntentSha = sourceIntentSha;
            this.layoutSeed = layoutSeed; this.routingSeed = routingSeed;
            this.rankedAttempts = new Vector<Integer>();
            for (int value : rankedAttempts) this.rankedAttempts.add(Integer.valueOf(value));
            this.placementEvaluations = placementEvaluations;
            this.placementRejections = placementRejections; this.oneFaceAttempts = oneFaceAttempts;
            this.twoLayerAttempts = twoLayerAttempts; this.routeAttempts = routeAttempts;
            this.routingOrders = routingOrders; this.routingExpansions = routingExpansions;
            this.selectedQualityMilli = selectedQualityMilli;
            this.fixedMarginFloor = fixedMarginFloor; this.accessEnvelopeMargin = accessEnvelopeMargin;
            this.requiredMargin = requiredMargin; this.legacyRequestedMargin = legacyRequestedMargin;
            this.actualMargin = actualMargin; this.occupiedX = occupiedX; this.occupiedY = occupiedY;
            this.occupiedWidth = occupiedWidth; this.occupiedHeight = occupiedHeight;
            this.outlineX = outlineX; this.outlineY = outlineY;
            this.statisticsCanonical = statisticsCanonical;
        }

        static final Expected ROOT_NEGATIVE_1022 = new Expected(-1022L, 0, 60, 143,
            6722832L, 3632, 1851, 714400, 45670L, 48,
            "0eb6b0649a9eb307e40fa0989c52c8436f2088a23109f4fcf72f574ee2af6b44",
            "bb68d08e1d9074488e051aad5e9a3a41dd46e844713b52c4bbf4d593a8feba77",
            "17552a33d0687f7a126b2be05dce5ec7083d3b48ea0ba4389f226f3968ccc7e4",
            "8869adea6a77467d2cbbbe7d5efafe3b86aa71b687d6bd13d0ca0fdfac86002c",
            "-5634176660316136309", "-3812824227946713686",
            new int[] {4, 0, 5, 2, 1, 3}, 49728L, 0, 3, 3, 6, 10,
            4339166L, 325097562, 50, 30, 30, 50, 30, 100, 80, 3572, 1791, 70, 50,
            "MEDIUM_BOARD@1;outcome=SUCCESS;selected=P07_FULLER_TWO_LAYER;placements=6;placementRejects=0;placementEvaluations=49728;routes=6;routingWork=10/4339166;p05=3/0;p07=3/3;selectedAttempt=0;ranked=[4, 0, 5, 2, 1, 3];placementScores=[4{total=1658498;electrical=189130;region=15930;connector=4350;corridor=3458;outline=9352400;courtyard=1061240;demand=3729940}, 0{total=1817036;electrical=206150;region=14835;connector=5200;corridor=5416;outline=7514400;courtyard=1061240;demand=3729940}, 5{total=1849202;electrical=209290;region=17270;connector=7990;corridor=2648;outline=21296000;courtyard=1061240;demand=3729940}, 2{total=1881200;electrical=213790;region=14645;connector=4260;corridor=5954;outline=10502800;courtyard=1061240;demand=3729940}, 1{total=1961189;electrical=221640;region=16295;connector=4050;corridor=7292;outline=6295200;courtyard=1061240;demand=3729940}, 3{total=1989661;electrical=220950;region=17617;connector=3740;corridor=9839;outline=6295200;courtyard=1061240;demand=3729940}];selectedScore=1817036;selectedQualityMilli=325097562;routeOutcomes=[P05_ONE_FACE@4=REJECTED:TOTAL_SEARCH_WORK_BUDGET, P05_ONE_FACE@2=REJECTED:TOTAL_SEARCH_WORK_BUDGET, P05_ONE_FACE@5=REJECTED:TOTAL_SEARCH_WORK_BUDGET, P07_FULLER_TWO_LAYER@4=SUCCESS, P07_FULLER_TWO_LAYER@3=REJECTED:P07_FULLER_TWO_LAYER:B_DRIVE:BRANCH_SEARCH_LIMIT, P07_FULLER_TWO_LAYER@0=SUCCESS]");

        static final Expected ROOT_42 = new Expected(42L, 1, 54, 131,
            5519502L, 3321, 1662, 178200, 42230L, 45,
            "df556b8d89f1f91e15912f99af9238cd99368ce19e1929e1707b09c97758598a",
            "4280267f0929521ab4be8664f74e841dfef6bef6c8b4a9552c18c621be685f59",
            "840dc1be8cd3ea75d6bfbeb62b9c9e2bf2fa6b718eea2aad557b899a9cacd96a",
            "5be52fd06e5788bbd64793059c26b198d57a4feda89c5c1e8e29a63c110622f7",
            "5526715536693200536", "4493419427469983",
            new int[] {1, 2, 3, 4, 0, 5}, 40913L, 0, 3, 3, 6, 19,
            5301842L, 270256005, 50, 44, 44, 50, 50, 90, 90, 3221, 1562, 40, 40,
            "MEDIUM_BOARD@1;outcome=SUCCESS;selected=P07_FULLER_TWO_LAYER;placements=6;placementRejects=0;placementEvaluations=40913;routes=6;routingWork=19/5301842;p05=3/0;p07=3/3;selectedAttempt=1;ranked=[1, 2, 3, 4, 0, 5];placementScores=[1{total=1603093;electrical=185270;region=13200;connector=3410;corridor=3114;outline=5860800;courtyard=1000808;demand=3445134}, 2{total=1611984;electrical=184100;region=15705;connector=4010;corridor=3185;outline=9639000;courtyard=1000808;demand=3445134}, 3{total=1803017;electrical=207340;region=14040;connector=3200;corridor=4886;outline=5860800;courtyard=1000808;demand=3445134}, 4{total=1840738;electrical=204630;region=15755;connector=5230;corridor=8020;outline=8613000;courtyard=1000808;demand=3445134}, 0{total=1863419;electrical=211590;region=14445;connector=2410;corridor=7284;outline=6606300;courtyard=1000808;demand=3445134}, 5{total=2126509;electrical=235020;region=18130;connector=5670;corridor=9831;outline=17392200;courtyard=1000808;demand=3445134}];selectedScore=1603093;selectedQualityMilli=270256005;routeOutcomes=[P05_ONE_FACE@4=REJECTED:TOTAL_SEARCH_WORK_BUDGET, P05_ONE_FACE@2=REJECTED:TOTAL_SEARCH_WORK_BUDGET, P05_ONE_FACE@5=REJECTED:TOTAL_SEARCH_WORK_BUDGET, P07_FULLER_TWO_LAYER@4=SUCCESS, P07_FULLER_TWO_LAYER@3=SUCCESS, P07_FULLER_TWO_LAYER@1=SUCCESS]");
    }
}
