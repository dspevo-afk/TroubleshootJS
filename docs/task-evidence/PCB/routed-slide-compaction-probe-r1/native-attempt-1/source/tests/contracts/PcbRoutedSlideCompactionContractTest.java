package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Native-only probe of the selected routed board after the accepted15 border
 * trim.  The probe reports the unchanged compactRouted control and every
 * independent canonical selected-frame slide; it never selects a winner.
 */
public final class PcbRoutedSlideCompactionContractTest {
    private static final int VERSION = 15;
    private static final String PHASE = "SELECTED_AFTER_EDGE_TRIM";
    private static final String SUMMARY_PREFIX =
        "PCB_ROUTED_SLIDE_COMPACTION_CASE ";
    private static final String SOURCE_GENERATION_METADATA = "ACCEPTED_SELECTED";
    private static final String PRIVATE_GENERATION_METADATA =
        "UNASSIGNED_PRIVATE_CANDIDATE";
    private static int assertions;
    private static int fixtures;

    private PcbRoutedSlideCompactionContractTest() { }

    public static void main(String[] args) {
        selectedPlanFrameAndMetadata();
        freshPrivateLayoutAndStableIdentity();
        strictImprovementComparator();
        selectedObserverOwnershipAndFailureCleanup();
        quietWhitespaceOwnerCleanup();
        check(fixtures == 5, "exactly five named selected-slide fixture groups");
        System.out.println("PASS: PCB routed slide compaction contracts assertions=" +
            assertions + " fixtures=" + fixtures +
            " evaluator=selectedRoutedSlideCompaction");
    }

    /** Root wrapper entrypoint.  The accepted15 helper emits the original
     * whitespace report; this method adds exactly the two routed probe rows. */
    static void observe(long seed, String label) {
        require(label != null && label.length() > 0,
            "selected-slide label is present");
        Recorder recorder = new Recorder(seed, label);
        SeededPcbLayoutGenerator.SelectedSlideObserver previous =
            SeededPcbLayoutGenerator.setSelectedSlideObserver(recorder);
        String selectedReceipt;
        try {
            selectedReceipt =
                PcbPostSelectionEdgeTrimContractTest.observeRoutedPassiveLegCensus(seed);
        } finally {
            SeededPcbLayoutGenerator.SelectedSlideObserver displaced =
                SeededPcbLayoutGenerator.setSelectedSlideObserver(previous);
            if (displaced != recorder)
                throw new IllegalStateException("Selected-slide observer owner changed");
        }
        require(recorder.callbacks == 1 && recorder.summary != null,
            "selected root produced exactly one selected-slide callback");
        require(selectedReceipt != null &&
                selectedReceipt.startsWith("PCB_EDGE_TRIM_CASE {"),
            "accepted15 helper returns its selected receipt");
        System.out.println("PCB_ROUTED_LEG_CASE " +
            selectedReceipt.substring("PCB_EDGE_TRIM_CASE ".length()));
        System.out.println(recorder.summaryJson());
    }

    private static final class Recorder
            implements SeededPcbLayoutGenerator.SelectedSlideObserver {
        final long seed;
        final String label;
        int callbacks;
        long startedNanos;
        long finishedNanos;
        PcbRoutedPassiveFeasibilityCensusContractTest.SelectedSlideSource source;
        Metrics sourceMetrics;
        FinalEvaluation control;
        final Vector<RowRecord> rows = new Vector<RowRecord>();
        int validatedMoves;
        int rejectedMoves;
        int compactions;
        int areaReductions;
        int strictImprovements;
        String selectedRoute;
        int selectedAttempt;
        String summary;

        Recorder(long seed, String label) {
            this.seed = seed;
            this.label = label;
        }

        public void onSelected(TroubleshootBoard board, PcbBoardLayout layout,
                PcbPlacementPlanner.Plan plan, String route, int attempt) {
            startedNanos = System.nanoTime();
            try {
                callbacks++;
                require(callbacks == 1 && board != null && layout != null &&
                        plan != null && route != null,
                    "selected-slide callback has one complete source frame");
                require("P07_FULLER_TWO_LAYER".equals(route),
                    "selected slide is the accepted P07 route");
                verifySelectedFrame(seed, board, layout, plan, attempt);
                selectedRoute = route;
                selectedAttempt = attempt;
                source = PcbRoutedPassiveFeasibilityCensusContractTest
                    .captureSelectedSlideSource(board, layout);
                sourceMetrics = metrics(board, layout, seed,
                    SOURCE_GENERATION_METADATA);
                PcbRoutedPassiveFeasibilityCensusContractTest
                    .SelectedSlideSource capturedSource = source;

                GenerationWorkScope.check();
                control = compactAndVerify(board, plan, capturedSource,
                    layout, seed, PRIVATE_GENERATION_METADATA);

                if (seed == 5L) {
                    require(rows.isEmpty(), "seed5 is the control-only root");
                } else if (seed == 2L) {
                    Vector<PcbRoutedPassiveLegCensusContractTest.MoveIntent> moves =
                        PcbRoutedPassiveLegCensusContractTest.selectedSlideMoves(board, layout);
                    require(moves.size() == 31,
                        "seed2 selected frame has the complete 31-move cohort");
                    for (int index = 0; index < moves.size(); index++) {
                        GenerationWorkScope.check();
                        PcbRoutedPassiveLegCensusContractTest.MoveIntent move =
                            moves.get(index);
                        PcbRoutedPassiveFeasibilityCensusContractTest.SelectedSlideRow row =
                            PcbRoutedPassiveFeasibilityCensusContractTest
                                .evaluateSelectedSlide(capturedSource, move);
                        RowRecord record = new RowRecord(row);
                        if (row.disposition().equals("PHYSICAL_VALIDATED")) {
                            validatedMoves++;
                            PcbPlacementPlanner.Plan movedPlan = movedPlan(plan, row);
                            FinalEvaluation compacted = compactAndVerify(board, movedPlan,
                                capturedSource, row.candidate(), seed,
                                PRIVATE_GENERATION_METADATA);
                            record.compaction = compacted;
                            compacted.strictImprovement = strictImprovement(
                                compacted.metrics, sourceMetrics, control.metrics);
                            compactions++;
                            if (areaReduction(compacted.metrics, sourceMetrics,
                                    control.metrics)) areaReductions++;
                            if (compacted.strictImprovement) strictImprovements++;
                        } else if (row.disposition().equals("PHYSICAL_REJECTED")) {
                            rejectedMoves++;
                        } else {
                            throw new IllegalStateException(
                                "Unknown selected-slide feasibility disposition: " +
                                row.disposition());
                        }
                        rows.add(record);
                    }
                    require(rows.size() == 31 && validatedMoves + rejectedMoves == 31,
                        "all 31 selected-frame moves have one independent row");
                } else {
                    throw new IllegalArgumentException("Unexpected selected-slide root: " + seed);
                }
                requireSourceUnchanged(capturedSource);
                summary = "ready";
            } finally {
                finishedNanos = System.nanoTime();
            }
        }

        String summaryJson() {
            require(summary != null && source != null && sourceMetrics != null &&
                    control != null, "selected-slide summary has complete source/control");
            StringBuilder json = new StringBuilder(220000);
            json.append(SUMMARY_PREFIX).append('{');
            json.append("\"seed\":"); stringField(json, Long.toString(seed));
            json.append(",\"label\":"); stringField(json, label);
            json.append(",\"version\":").append(VERSION);
            json.append(",\"phase\":"); stringField(json, PHASE);
            json.append(",\"status\":"); stringField(json,
                seed == 5L ? "CONTROL_ONLY" : "PROBE_COMPLETE");
            json.append(",\"selectedAttempt\":").append(selectedAttempt);
            json.append(",\"selectedRoute\":"); stringField(json, selectedRoute);
            json.append(",\"newRouteRequests\":0");
            json.append(",\"selectedMutation\":false");
            json.append(",\"source\":").append(source.sourceJson());
            json.append(",\"sourceMetrics\":").append(sourceMetrics.json());
            json.append(",\"control\":"); appendCompaction(json, control, false);
            json.append(",\"rows\":[");
            for (int index = 0; index < rows.size(); index++) {
                if (index != 0) json.append(',');
                RowRecord row = rows.get(index);
                json.append('{');
                json.append("\"feasibility\":");
                PcbRoutedPassiveFeasibilityCensusContractTest
                    .appendSelectedSlideFeasibility(json, row.feasibility);
                json.append(",\"compaction\":");
                if (row.compaction == null) json.append("null");
                else appendCompaction(json, row.compaction, true);
                json.append('}');
            }
            json.append(']');
            json.append(",\"aggregate\":{");
            json.append("\"structuralDirections\":").append(rows.size());
            json.append(",\"validatedMoves\":").append(validatedMoves);
            json.append(",\"rejectedMoves\":").append(rejectedMoves);
            json.append(",\"compactions\":").append(compactions);
            json.append(",\"areaReductions\":").append(areaReductions);
            json.append(",\"strictImprovements\":").append(strictImprovements);
            json.append('}');
            json.append(",\"elapsedMillis\":").append(elapsedMillis());
            json.append('}');
            return json.toString();
        }

        private long elapsedMillis() {
            long end = finishedNanos == 0L ? System.nanoTime() : finishedNanos;
            long elapsed = end - startedNanos;
            return elapsed <= 0L ? 0L : elapsed / 1000000L;
        }
    }

    private static final class RowRecord {
        final PcbRoutedPassiveFeasibilityCensusContractTest.SelectedSlideRow feasibility;
        FinalEvaluation compaction;
        RowRecord(PcbRoutedPassiveFeasibilityCensusContractTest.SelectedSlideRow feasibility) {
            this.feasibility = feasibility;
        }
    }

    private static final class FinalEvaluation {
        final boolean compacted;
        final Metrics metrics;
        final FinalProof proof;
        final boolean sourceImmutable;
        boolean strictImprovement;

        FinalEvaluation(boolean compacted, Metrics metrics, FinalProof proof,
                boolean sourceImmutable, boolean strictImprovement) {
            this.compacted = compacted;
            this.metrics = metrics;
            this.proof = proof;
            this.sourceImmutable = sourceImmutable;
            this.strictImprovement = strictImprovement;
        }
    }

    private static final class Metrics {
        final long area;
        final long copperLength;
        final int vias;
        final int largestProtectedEmptyRectangleArea;
        final String geometrySha256;
        final String stableIdentitySha256;
        final String labelIdentitySha256;
        final String generationMetadata;

        Metrics(long area, long copperLength, int vias, int largestVoid,
                String geometrySha256, String stableIdentitySha256,
                String labelIdentitySha256, String generationMetadata) {
            this.area = area;
            this.copperLength = copperLength;
            this.vias = vias;
            this.largestProtectedEmptyRectangleArea = largestVoid;
            this.geometrySha256 = geometrySha256;
            this.stableIdentitySha256 = stableIdentitySha256;
            this.labelIdentitySha256 = labelIdentitySha256;
            this.generationMetadata = generationMetadata;
        }

        String json() {
            StringBuilder json = new StringBuilder(500);
            json.append('{');
            json.append("\"area\":").append(area);
            json.append(",\"copperLength\":").append(copperLength);
            json.append(",\"vias\":").append(vias);
            json.append(",\"largestProtectedEmptyRectangleArea\":")
                .append(largestProtectedEmptyRectangleArea);
            json.append(",\"geometrySha256\":"); stringField(json, geometrySha256);
            json.append(",\"stableIdentitySha256\":");
            stringField(json, stableIdentitySha256);
            json.append(",\"labelIdentitySha256\":");
            stringField(json, labelIdentitySha256);
            json.append(",\"generationMetadata\":");
            stringField(json, generationMetadata);
            json.append('}');
            return json.toString();
        }
    }

    /** The final proof deliberately has no endpoint-only claim: compaction may
     * translate interior vertices. */
    private static final class FinalProof {
        final boolean placementValidated, geometryValidated, routingValidated;
        final boolean routeQualityValidated, traceClearanceValidated, twoLayerValidated;
        final int padPairCount;
        final String sourceReachabilityHash, candidateReachabilityHash;
        final int sourceRouteSourceCount, candidateRouteSourceCount;
        final String sourceTraceProvenanceHash, candidateTraceProvenanceHash;
        final boolean stableIdentityVerified, labelIdentityVerified;
        final boolean conductorReachabilityVerified, traceProvenanceVerified;
        final boolean sourceImmutable;

        FinalProof(boolean placementValidated, boolean geometryValidated,
                boolean routingValidated, boolean routeQualityValidated,
                boolean traceClearanceValidated, boolean twoLayerValidated,
                PcbRoutedPassiveFeasibilityCensusContractTest.SelectedSlideProof proof,
                boolean stableIdentityVerified, boolean labelIdentityVerified,
                boolean sourceImmutable) {
            this.placementValidated = placementValidated;
            this.geometryValidated = geometryValidated;
            this.routingValidated = routingValidated;
            this.routeQualityValidated = routeQualityValidated;
            this.traceClearanceValidated = traceClearanceValidated;
            this.twoLayerValidated = twoLayerValidated;
            this.padPairCount = proof.padPairCount();
            this.sourceReachabilityHash = proof.sourceReachabilityHash();
            this.candidateReachabilityHash = proof.candidateReachabilityHash();
            this.sourceRouteSourceCount = proof.sourceRouteSourceCount();
            this.candidateRouteSourceCount = proof.candidateRouteSourceCount();
            this.sourceTraceProvenanceHash = proof.sourceTraceProvenanceHash();
            this.candidateTraceProvenanceHash = proof.candidateTraceProvenanceHash();
            this.stableIdentityVerified = stableIdentityVerified;
            this.labelIdentityVerified = labelIdentityVerified;
            this.conductorReachabilityVerified = true;
            this.traceProvenanceVerified = true;
            this.sourceImmutable = sourceImmutable;
        }

        String json() {
            StringBuilder json = new StringBuilder(1500);
            json.append('{');
            json.append("\"placementValidated\":").append(placementValidated);
            json.append(",\"geometryValidated\":").append(geometryValidated);
            json.append(",\"routingValidated\":").append(routingValidated);
            json.append(",\"routeQualityValidated\":").append(routeQualityValidated);
            json.append(",\"traceClearanceValidated\":")
                .append(traceClearanceValidated);
            json.append(",\"twoLayerValidated\":").append(twoLayerValidated);
            json.append(",\"padPairCount\":").append(padPairCount);
            json.append(",\"sourceReachabilityHash\":");
            stringField(json, sourceReachabilityHash);
            json.append(",\"candidateReachabilityHash\":");
            stringField(json, candidateReachabilityHash);
            json.append(",\"sourceRouteSourceCount\":")
                .append(sourceRouteSourceCount);
            json.append(",\"candidateRouteSourceCount\":")
                .append(candidateRouteSourceCount);
            json.append(",\"sourceTraceProvenanceHash\":");
            stringField(json, sourceTraceProvenanceHash);
            json.append(",\"candidateTraceProvenanceHash\":");
            stringField(json, candidateTraceProvenanceHash);
            json.append(",\"stableIdentityVerified\":")
                .append(stableIdentityVerified);
            json.append(",\"labelIdentityVerified\":")
                .append(labelIdentityVerified);
            json.append(",\"conductorReachabilityVerified\":")
                .append(conductorReachabilityVerified);
            json.append(",\"traceProvenanceVerified\":")
                .append(traceProvenanceVerified);
            json.append(",\"sourceImmutable\":").append(sourceImmutable);
            json.append('}');
            return json.toString();
        }
    }

    private static FinalEvaluation compactAndVerify(TroubleshootBoard board,
            PcbPlacementPlanner.Plan plan,
            PcbRoutedPassiveFeasibilityCensusContractTest.SelectedSlideSource source,
            PcbBoardLayout routed, long seed, String generationMetadata) {
        GenerationWorkScope.check();
        PcbBoardLayout input = freshUnlabeled(routed);
        require(input != routed && input.getSilkscreenLabels().isEmpty(),
            "compaction input is a fresh unlabeled private layout");
        requireRoutingStatistics(routed, input, "private compaction input");
        PcbPlacementCompactor.Routed result = PcbPlacementCompactor.compactRouted(
            board, board.getPlacementConstraints(), plan, input);
        require(result != null && result.layout != null && result.plan != null,
            "compactor returns a routed layout and matching plan");
        boolean compacted = result.layout != input;
        PcbBoardLayout normalized = freshUnlabeled(result.layout);
        require(normalized != result.layout && normalized.getSilkscreenLabels().isEmpty(),
            "compaction result is normalized into a fresh unlabeled layout");
        restoreRoutingStatistics(input, normalized);
        requireRoutingStatistics(input, normalized, "normalized compaction output");
        SeededPcbLayoutGenerator.restoreSelectedSlideSilkscreen(board, normalized);
        require(!normalized.getSilkscreenLabels().isEmpty(),
            "same generator silkscreen owner restores final labels");
        require(sameRect(result.plan.outline, normalized.getBoardOutline()),
            "compacted plan frame matches normalized output outline");

        boolean placementValidated = false;
        boolean geometryValidated = false;
        boolean routingValidated = false;
        boolean routeQualityValidated = false;
        boolean traceClearanceValidated = false;
        boolean twoLayerValidated = false;
        PcbPlacementPlanner.validate(board, board.getPlacementConstraints(),
            normalized.getBoardOutline(), result.plan.footprints);
        placementValidated = true;
        normalized.validateGeometry(board);
        geometryValidated = true;
        normalized.validateRoutingGeometry(board);
        routingValidated = true;
        normalized.validateRouteQuality();
        routeQualityValidated = true;
        normalized.validateTraceClearance();
        traceClearanceValidated = true;
        new PcbTwoLayerRules(board, normalized).validate(normalized);
        twoLayerValidated = true;

        String sourceIdentity = stableIdentityCanonical(board, source.layout());
        String candidateIdentity = stableIdentityCanonical(board, normalized);
        require(sourceIdentity.equals(candidateIdentity),
            "final compaction preserves component, pad, route, hole and region identity");
        String sourceLabels = labelIdentityCanonical(source.layout());
        String candidateLabels = labelIdentityCanonical(normalized);
        require(sourceLabels.equals(candidateLabels),
            "final compaction preserves label semantic identity and styles");
        String sourceIdentityHash = PlayerSessionFingerprint.of(sourceIdentity);
        String candidateIdentityHash = PlayerSessionFingerprint.of(candidateIdentity);
        String sourceLabelHash = PlayerSessionFingerprint.of(sourceLabels);
        String candidateLabelHash = PlayerSessionFingerprint.of(candidateLabels);
        require(sourceIdentityHash.equals(candidateIdentityHash) &&
                sourceLabelHash.equals(candidateLabelHash),
            "stable and label identity fingerprints agree with canonical metadata");
        String candidateGeometryBeforeProof =
            PlayerSessionFingerprint.of(normalized.geometryFingerprint());
        PcbRoutedPassiveFeasibilityCensusContractTest.SelectedSlideProof proof =
            PcbRoutedPassiveFeasibilityCensusContractTest.verifySelectedSlideCompaction(
                source, normalized);
        GenerationWorkScope.check();
        String sourceIdentityAfterProof = stableIdentityCanonical(board, source.layout());
        String candidateIdentityAfterProof = stableIdentityCanonical(board, normalized);
        String sourceLabelsAfterProof = labelIdentityCanonical(source.layout());
        String candidateLabelsAfterProof = labelIdentityCanonical(normalized);
        String sourceGeometryAfterProof =
            PlayerSessionFingerprint.of(source.layout().geometryFingerprint());
        String candidateGeometryAfterProof =
            PlayerSessionFingerprint.of(normalized.geometryFingerprint());
        require(sourceIdentity.equals(sourceIdentityAfterProof) &&
                candidateIdentity.equals(candidateIdentityAfterProof) &&
                sourceLabels.equals(sourceLabelsAfterProof) &&
                candidateLabels.equals(candidateLabelsAfterProof),
            "identity metadata remains stable after independent conductor proof");
        require(source.geometryBefore().equals(sourceGeometryAfterProof) &&
                candidateGeometryBeforeProof.equals(candidateGeometryAfterProof),
            "source and candidate geometry remain stable after independent proof");
        require(sourceIdentityHash.equals(PlayerSessionFingerprint.of(sourceIdentityAfterProof)) &&
                candidateIdentityHash.equals(PlayerSessionFingerprint.of(candidateIdentityAfterProof)) &&
                sourceLabelHash.equals(PlayerSessionFingerprint.of(sourceLabelsAfterProof)) &&
                candidateLabelHash.equals(PlayerSessionFingerprint.of(candidateLabelsAfterProof)),
            "identity hashes remain stable after independent conductor proof");
        requireRoutingStatistics(input, normalized, "post-proof compaction output");
        requireSourceUnchanged(source);
        Metrics outputMetrics = metrics(board, normalized, seed, generationMetadata);
        FinalProof finalProof = new FinalProof(placementValidated, geometryValidated,
            routingValidated, routeQualityValidated, traceClearanceValidated,
            twoLayerValidated, proof, true, true, true);
        return new FinalEvaluation(compacted, outputMetrics, finalProof, true, false);
    }

    private static PcbPlacementPlanner.Plan movedPlan(PcbPlacementPlanner.Plan source,
            PcbRoutedPassiveFeasibilityCensusContractTest.SelectedSlideRow row) {
        Vector<PcbFootprint> footprints = row.footprints();
        require(footprints.size() == source.footprints.size(),
            "moved plan retains the complete source footprint vector");
        return new PcbPlacementPlanner.Plan(source.outline, footprints, source.regions,
            source.courtyardArea, source.demandArea, source.candidate,
            source.evaluations);
    }

    private static PcbBoardLayout freshUnlabeled(PcbBoardLayout source) {
        if (source == null) throw new IllegalArgumentException("Missing private layout source");
        PcbBoardLayout copy = new PcbBoardLayout(source.getWidth(), source.getHeight(),
            source.getBoardOutline(), source.getPartsTray(),
            source.getLayoutAlgorithmVersion());
        Vector<PcbComponentPlacement> components = source.getComponents();
        for (int index = 0; index < components.size(); index++) {
            GenerationWorkScope.check();
            copy.addComponent(components.get(index));
        }
        Vector<PcbPadPlacement> pads = source.getPads();
        for (int index = 0; index < pads.size(); index++) {
            GenerationWorkScope.check();
            copy.addPad(pads.get(index));
        }
        Vector<PcbLayoutRegion> regions = source.getRegions();
        for (int index = 0; index < regions.size(); index++) {
            GenerationWorkScope.check();
            copy.addRegion(regions.get(index));
        }
        Vector<PcbBoardHole> holes = source.getHoles();
        for (int index = 0; index < holes.size(); index++) {
            GenerationWorkScope.check();
            copy.addHole(holes.get(index));
        }
        Vector<PcbTraceGeometry> traces = source.getTraces();
        for (int index = 0; index < traces.size(); index++) {
            GenerationWorkScope.check();
            copy.addTrace(traces.get(index));
        }
        copy.setRoutingStatistics(source.getRoutingExpansions(),
            source.getRawRoutingSegments(), source.getRoutingCongestionRejections());
        if (source.getRoutingRecoveryStatistics() != null)
            copy.setRoutingRecoveryStatistics(source.getRoutingRecoveryStatistics());
        require(copy.getSilkscreenLabels().isEmpty(),
            "private compaction input does not carry silkscreen labels");
        return copy;
    }

    private static PcbBoardLayout freshUnlabeledWithTrace(PcbBoardLayout source,
            PcbTraceGeometry replacement) {
        if (source == null || replacement == null)
            throw new IllegalArgumentException("Missing trace mutation source");
        PcbBoardLayout copy = new PcbBoardLayout(source.getWidth(), source.getHeight(),
            source.getBoardOutline(), source.getPartsTray(),
            source.getLayoutAlgorithmVersion());
        for (PcbComponentPlacement component : source.getComponents()) {
            GenerationWorkScope.check();
            copy.addComponent(component);
        }
        for (PcbPadPlacement pad : source.getPads()) {
            GenerationWorkScope.check();
            copy.addPad(pad);
        }
        for (PcbLayoutRegion region : source.getRegions()) {
            GenerationWorkScope.check();
            copy.addRegion(region);
        }
        for (PcbBoardHole hole : source.getHoles()) {
            GenerationWorkScope.check();
            copy.addHole(hole);
        }
        for (int index = 0; index < source.getTraces().size(); index++) {
            GenerationWorkScope.check();
            copy.addTrace(index == 0 ? replacement : source.getTraces().get(index));
        }
        copy.setRoutingStatistics(source.getRoutingExpansions(),
            source.getRawRoutingSegments(), source.getRoutingCongestionRejections());
        if (source.getRoutingRecoveryStatistics() != null)
            copy.setRoutingRecoveryStatistics(source.getRoutingRecoveryStatistics());
        require(copy.getSilkscreenLabels().isEmpty(),
            "trace mutation layout remains a fresh unlabeled owner");
        return copy;
    }

    private static void restoreRoutingStatistics(PcbBoardLayout expected,
            PcbBoardLayout actual) {
        actual.setRoutingStatistics(expected.getRoutingExpansions(),
            expected.getRawRoutingSegments(), expected.getRoutingCongestionRejections());
        PcbRoutingWork.Statistics recovery = expected.getRoutingRecoveryStatistics();
        actual.setRoutingRecoveryStatistics(recovery);
    }

    private static void requireRoutingStatistics(PcbBoardLayout expected,
            PcbBoardLayout actual, String context) {
        require(expected.getRoutingExpansions() == actual.getRoutingExpansions() &&
                expected.getRawRoutingSegments() == actual.getRawRoutingSegments() &&
                expected.getRoutingCongestionRejections() ==
                    actual.getRoutingCongestionRejections(),
            context + " preserves routing statistics");
        PcbRoutingWork.Statistics expectedRecovery =
            expected.getRoutingRecoveryStatistics();
        PcbRoutingWork.Statistics actualRecovery = actual.getRoutingRecoveryStatistics();
        String expectedCanonical = expectedRecovery == null ? null :
            expectedRecovery.toCanonical();
        String actualCanonical = actualRecovery == null ? null :
            actualRecovery.toCanonical();
        require(expectedCanonical == null ? actualCanonical == null :
                expectedCanonical.equals(actualCanonical),
            context + " preserves routing recovery statistics");
    }

    private static Metrics metrics(TroubleshootBoard board, PcbBoardLayout layout,
            long seed, String generationMetadata) {
        GenerationWorkScope.check();
        Rectangle outline = layout.getBoardOutline();
        long area = (long) outline.width * outline.height;
        long copper = copperLength(layout.getTraces());
        int vias = 0;
        for (PcbBoardHole hole : layout.getHoles()) {
            GenerationWorkScope.check();
            if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
        }
        int largestVoid = PcbFunctionalGroupingContractTest.measureSelectedSlideWhitespace(
            board, layout, seed, VERSION);
        String geometry = PlayerSessionFingerprint.of(layout.geometryFingerprint());
        String identity = PlayerSessionFingerprint.of(stableIdentityCanonical(board, layout));
        String labels = PlayerSessionFingerprint.of(labelIdentityCanonical(layout));
        requireHash(geometry);
        requireHash(identity);
        requireHash(labels);
        return new Metrics(area, copper, vias, largestVoid, geometry, identity, labels,
            generationMetadata);
    }

    private static long copperLength(Vector<PcbTraceGeometry> traces) {
        long result = 0L;
        for (PcbTraceGeometry trace : traces) {
            GenerationWorkScope.check();
            int[] x = trace.getXPoints();
            int[] y = trace.getYPoints();
            for (int index = 1; index < x.length; index++) {
                GenerationWorkScope.check();
                result += Math.abs((long) x[index] - x[index - 1]) +
                    Math.abs((long) y[index] - y[index - 1]);
            }
        }
        return result;
    }

    private static boolean areaReduction(Metrics candidate, Metrics source,
            Metrics control) {
        return candidate.area < source.area && candidate.area < control.area;
    }

    private static boolean strictImprovement(Metrics candidate, Metrics source,
            Metrics control) {
        return areaReduction(candidate, source, control) &&
            candidate.copperLength <= source.copperLength &&
            candidate.copperLength <= control.copperLength &&
            candidate.vias <= source.vias && candidate.vias <= control.vias &&
            candidate.largestProtectedEmptyRectangleArea <=
                source.largestProtectedEmptyRectangleArea &&
            candidate.largestProtectedEmptyRectangleArea <=
                control.largestProtectedEmptyRectangleArea;
    }

    private static void appendCompaction(StringBuilder json, FinalEvaluation value,
            boolean includeStrict) {
        json.append('{');
        json.append("\"compacted\":").append(value.compacted);
        json.append(",\"metrics\":").append(value.metrics.json());
        json.append(",\"proof\":").append(value.proof.json());
        json.append(",\"sourceImmutable\":").append(value.sourceImmutable);
        if (includeStrict)
            json.append(",\"strictImprovement\":").append(value.strictImprovement);
        json.append('}');
    }

    private static void verifySelectedFrame(long seed, TroubleshootBoard board,
            PcbBoardLayout layout, PcbPlacementPlanner.Plan plan, int attempt) {
        if (seed == 5L) {
            require(sameRect(plan.outline, new Rectangle(60, 40, 4921, 1864)) &&
                    plan.courtyardArea == 999824L && plan.demandArea == 3431527L &&
                    plan.candidate == 5 && plan.footprints.size() == 54 &&
                    plan.regions.size() == 4 && attempt == 5,
                "seed5 selected plan retains its literal frame and metadata");
        } else if (seed == 2L) {
            require(sameRect(plan.outline, new Rectangle(70, 28, 4851, 1892)) &&
                    plan.courtyardArea == 1023344L && plan.demandArea == 3546727L &&
                    plan.candidate == 2 && plan.footprints.size() == 56 &&
                    plan.regions.size() == 4 && attempt == 2,
                "seed2 selected plan retains its literal frame and metadata");
        } else {
            throw new IllegalArgumentException("Unexpected selected frame root: " + seed);
        }
        require(sameRect(plan.outline, layout.getBoardOutline()),
            "selected plan outline is in the actual selected layout frame");
        require(plan.footprints.size() == board.getComponentIds().size() &&
                plan.regions.size() > 0 && plan.evaluations > 0,
            "selected plan retains complete component and region metadata");
    }

    private static String stableIdentityCanonical(TroubleshootBoard board,
            PcbBoardLayout layout) {
        StringBuilder value = new StringBuilder(16000);
        value.append("PCB_STABLE_IDENTITY/1\n");
        TreeMap<String,PcbComponentPlacement> components =
            new TreeMap<String,PcbComponentPlacement>();
        for (PcbComponentPlacement component : layout.getComponents()) {
            GenerationWorkScope.check();
            components.put(component.getComponentId(), component);
        }
        for (Map.Entry<String,PcbComponentPlacement> entry : components.entrySet()) {
            GenerationWorkScope.check();
            PcbComponentPlacement placement = entry.getValue();
            BoardComponent component = board == null ? null : board.getComponent(entry.getKey());
            field(value, entry.getKey());
            field(value, component == null ? null : component.getType());
            field(value, component == null ? null : component.getDisplayName());
            field(value, placement.getPhysicalPackage().getId());
            field(value, placement.getGeometryVariantKey());
            field(value, placement.getGeometryTransformKey());
            field(value, Integer.toString(placement.getGeometryContractVersionValue()));
            field(value, placement.getMountingSide().name());
            field(value, placement.getRotation().name());
            if (component == null) field(value, null);
            else {
                Vector<String> padIds = component.getPadIds();
                value.append(padIds.size()).append('[');
                for (String padId : padIds) field(value, padId);
                value.append(']');
            }
        }

        TreeMap<String,PcbPadPlacement> pads = new TreeMap<String,PcbPadPlacement>();
        for (PcbPadPlacement pad : layout.getPads()) {
            GenerationWorkScope.check();
            pads.put(pad.getPadId(), pad);
        }
        for (Map.Entry<String,PcbPadPlacement> entry : pads.entrySet()) {
            GenerationWorkScope.check();
            PcbPadPlacement pad = entry.getValue();
            BoardPad boardPad = board == null ? null : board.getPad(entry.getKey());
            field(value, entry.getKey());
            field(value, boardPad == null ? null : boardPad.getComponentId());
            field(value, boardPad == null ? null : boardPad.getTerminalId());
            field(value, boardPad == null ? null : boardPad.getNetId());
            field(value, pad.getAttachment().name());
            field(value, pad.getMountingSide().name());
            field(value, pad.getExposure().name());
            value.append(pad.getEscapeDx()).append(',').append(pad.getEscapeDy())
                .append(',').append(pad.getEscapeLength()).append(';');
        }

        TreeMap<String,PcbTraceGeometry> traces = new TreeMap<String,PcbTraceGeometry>();
        for (PcbTraceGeometry trace : layout.getTraces()) {
            GenerationWorkScope.check();
            if (traces.put(trace.getSourceId(), trace) != null)
                throw new IllegalStateException("Duplicate stable trace identity: " +
                    trace.getSourceId());
        }
        for (Map.Entry<String,PcbTraceGeometry> entry : traces.entrySet()) {
            GenerationWorkScope.check();
            PcbTraceGeometry trace = entry.getValue();
            field(value, trace.getSourceId());
            field(value, trace.getNetId());
            field(value, trace.getStartPadId());
            field(value, trace.getEndPadId());
            field(value, trace.getLayer().name());
            field(value, trace.getExposure().name());
            value.append(trace.getWidth()).append(';');
        }

        TreeMap<String,PcbBoardHole> holes = new TreeMap<String,PcbBoardHole>();
        for (PcbBoardHole hole : layout.getHoles()) {
            GenerationWorkScope.check();
            holes.put(hole.id, hole);
        }
        for (Map.Entry<String,PcbBoardHole> entry : holes.entrySet()) {
            GenerationWorkScope.check();
            PcbBoardHole hole = entry.getValue();
            field(value, hole.id);
            field(value, hole.kind.name());
            field(value, hole.netId);
            value.append(hole.drillRadius).append(',').append(hole.landRadius).append(';');
            field(value, hole.exposure.name());
        }

        TreeMap<String,PcbLayoutRegion> regions = new TreeMap<String,PcbLayoutRegion>();
        for (PcbLayoutRegion region : layout.getRegions()) {
            GenerationWorkScope.check();
            regions.put(region.id, region);
        }
        for (Map.Entry<String,PcbLayoutRegion> entry : regions.entrySet()) {
            GenerationWorkScope.check();
            PcbLayoutRegion region = entry.getValue();
            field(value, region.id);
            field(value, region.label);
            Vector<String> members = region.getComponentIds();
            Collections.sort(members);
            value.append(members.size()).append('[');
            for (String member : members) field(value, member);
            value.append(']');
        }
        return value.toString();
    }

    private static String labelIdentityCanonical(PcbBoardLayout layout) {
        StringBuilder value = new StringBuilder(4000);
        value.append("PCB_LABEL_IDENTITY/1\n");
        TreeMap<String,PcbSilkscreenLabel> labels =
            new TreeMap<String,PcbSilkscreenLabel>();
        for (PcbSilkscreenLabel label : layout.getSilkscreenLabels()) {
            GenerationWorkScope.check();
            if (labels.put(label.getId(), label) != null)
                throw new IllegalStateException("Duplicate stable label identity: " +
                    label.getId());
        }
        for (Map.Entry<String,PcbSilkscreenLabel> entry : labels.entrySet()) {
            GenerationWorkScope.check();
            PcbSilkscreenLabel label = entry.getValue();
            field(value, label.getId());
            field(value, label.getText());
            value.append(label.getFontSize()).append(';').append(label.isBold()).append(';');
            field(value, label.getTargetPadId());
        }
        return value.toString();
    }

    private static void field(StringBuilder value, String text) {
        if (text == null) value.append("-1:;");
        else value.append(text.length()).append(':').append(text).append(';');
    }

    private static void requireSourceUnchanged(
            PcbRoutedPassiveFeasibilityCensusContractTest.SelectedSlideSource source) {
        String graph = PcbCanvasBoundaryContractTest.liveGraphSha256(source.board());
        String geometry = PlayerSessionFingerprint.of(source.layout().geometryFingerprint());
        require(source.liveGraphBefore().equals(graph) &&
                source.geometryBefore().equals(geometry),
            "selected source graph and geometry remain immutable");
    }

    private static boolean sameRect(Rectangle first, Rectangle second) {
        return first != null && second != null && first.x == second.x && first.y == second.y &&
            first.width == second.width && first.height == second.height;
    }

    private static void requireHash(String hash) {
        if (hash == null || hash.length() != 64)
            throw new IllegalStateException("Expected 64-character SHA-256 fingerprint");
        for (int index = 0; index < hash.length(); index++) {
            char value = hash.charAt(index);
            if (!((value >= '0' && value <= '9') ||
                    (value >= 'a' && value <= 'f')))
                throw new IllegalStateException("Fingerprint is not lowercase hexadecimal");
        }
    }

    private static PcbFootprint literalLedFootprint(String componentId, int x, int y) {
        PhysicalPackage physicalPackage = PhysicalPackages.THROUGH_HOLE_LED;
        PhysicalPackageGeometry geometry = physicalPackage.getGeometry();
        PcbPackagePose pose = PcbPackagePose.top(x, y);
        PcbComponentPlacement placement = PcbComponentPlacement.fromPhysicalGeometry(
            componentId, pose, physicalPackage, geometry);
        PhysicalPackageGeometry.Placement placed = geometry.placedAt(pose);
        Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
        for (int index = 0; index < geometry.getTerminals().size(); index++) {
            PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
            Point point = placed.getPadPoint(index);
            pads.add(new PcbPadPlacement(componentId + "." + terminal.getTerminalId(),
                point.x, point.y, placed.getEscapeDx(index), placed.getEscapeDy(index),
                placed.getEscapeLength(index), placed.getPadBounds(index),
                placed.getBoardPadProbeBounds(index), terminal.getAttachment(),
                pose.getMountingSide()));
        }
        return new PcbFootprint(placement, pads);
    }

    private static boolean sameFootprint(PcbFootprint footprint, String componentId,
            int x, int y, int firstPadX, int firstPadY, int secondPadX, int secondPadY) {
        if (footprint == null || footprint.getPlacement() == null ||
                !componentId.equals(footprint.getPlacement().getComponentId()) ||
                footprint.getPlacement().getX() != x || footprint.getPlacement().getY() != y)
            return false;
        Vector<PcbPadPlacement> pads = footprint.getPads();
        return pads.size() == 2 &&
            (componentId + ".A").equals(pads.get(0).getPadId()) &&
            pads.get(0).getX() == firstPadX && pads.get(0).getY() == firstPadY &&
            (componentId + ".K").equals(pads.get(1).getPadId()) &&
            pads.get(1).getX() == secondPadX && pads.get(1).getY() == secondPadY;
    }

    private static void selectedPlanFrameAndMetadata() {
        Rectangle originalOutline = new Rectangle(20, 24, 420, 280);
        Rectangle translatedOutline = new Rectangle(70, 46, 560, 360);
        PcbFootprint originalFirst = literalLedFootprint("FRAME_A", 110, 90);
        PcbFootprint originalSecond = literalLedFootprint("FRAME_B", 280, 150);
        PcbFootprint translatedFirst = literalLedFootprint("FRAME_A", 230, 150);
        PcbFootprint translatedSecond = literalLedFootprint("FRAME_B", 400, 210);
        Vector<PcbFootprint> originalFootprints = new Vector<PcbFootprint>();
        originalFootprints.add(originalFirst);
        originalFootprints.add(originalSecond);
        Vector<String> memberIds = new Vector<String>();
        memberIds.add("FRAME_A");
        memberIds.add("FRAME_B");
        PcbLayoutRegion originalRegion = new PcbLayoutRegion("frame-region",
            "Frame region", memberIds);
        Vector<PcbLayoutRegion> originalRegions = new Vector<PcbLayoutRegion>();
        originalRegions.add(originalRegion);
        PcbPlacementPlanner.Plan original = new PcbPlacementPlanner.Plan(originalOutline,
            originalFootprints, originalRegions, 123456L, 789012L, 4, 37);
        PcbBoardLayout actual = new PcbBoardLayout(700, 500, translatedOutline,
            new Rectangle(650, 40, 40, 360), VERSION);
        actual.addComponent(translatedFirst.getPlacement());
        for (PcbPadPlacement pad : translatedFirst.getPads()) actual.addPad(pad);
        actual.addComponent(translatedSecond.getPlacement());
        for (PcbPadPlacement pad : translatedSecond.getPads()) actual.addPad(pad);
        actual.addRegion(new PcbLayoutRegion("frame-region", "Frame region", memberIds));
        String originalGeometry = originalFirst.geometryFingerprint() + originalSecond.geometryFingerprint();
        String actualGeometry = actual.geometryFingerprint();
        PcbPlacementPlanner.Plan reframed = SeededPcbLayoutGenerator
            .reframeSelectedSlidePlan(original, actual);
        check(sameRect(reframed.outline, translatedOutline),
            "real selected-slide reframe adopts the literal actual outline");
        check(reframed.courtyardArea == 123456L && reframed.demandArea == 789012L &&
                reframed.candidate == 4 && reframed.evaluations == 37,
            "real selected-slide reframe preserves every plan metadata field");
        check(reframed.footprints.size() == 2 &&
                sameFootprint(reframed.footprints.get(0), "FRAME_A", 230, 150, 250, 220,
                    290, 220) &&
                sameFootprint(reframed.footprints.get(1), "FRAME_B", 400, 210, 420, 280,
                    460, 280),
            "real selected-slide reframe translates literal component and pad coordinates");
        check(reframed.regions.size() == 1 &&
                "frame-region".equals(reframed.regions.get(0).id) &&
                "Frame region".equals(reframed.regions.get(0).label) &&
                reframed.regions.get(0).getComponentIds().equals(memberIds),
            "real selected-slide reframe preserves literal region memberships");
        check(original.outline.equals(originalOutline) && original.footprints.size() == 2 &&
                sameFootprint(original.footprints.get(0), "FRAME_A", 110, 90, 130, 160,
                    170, 160) &&
                sameFootprint(original.footprints.get(1), "FRAME_B", 280, 150, 300, 220,
                    340, 220) &&
                (original.footprints.get(0).geometryFingerprint() +
                    original.footprints.get(1).geometryFingerprint()).equals(originalGeometry) &&
                actual.geometryFingerprint().equals(actualGeometry),
            "reframing leaves the original plan and actual layout owners unchanged");
        check(!sameRect(reframed.outline, new Rectangle(70, 46, 559, 360)) &&
                reframed.candidate != 3,
            "literal reframe expectations reject outline and candidate mutations");
        fixture("selectedPlanFrameAndMetadata");
    }

    private static void freshPrivateLayoutAndStableIdentity() {
        PcbBoardLayout source = new PcbBoardLayout(600, 500,
            new Rectangle(20, 20, 400, 300), new Rectangle(450, 20, 100, 300),
            SeededPcbLayoutGenerator.CURRENT_VERSION);
        PcbFootprint identityFootprint = literalLedFootprint("IDENTITY_U", 120, 100);
        source.addComponent(identityFootprint.getPlacement());
        for (PcbPadPlacement pad : identityFootprint.getPads()) source.addPad(pad);
        Vector<String> identityMembers = new Vector<String>();
        identityMembers.add("IDENTITY_U");
        source.addRegion(new PcbLayoutRegion("identity-region", "Identity region",
            identityMembers));
        source.addTrace(new PcbTraceGeometry("slide/identity", "N1", "IDENTITY_U.A",
            "IDENTITY_U.K",
            PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
            new int[] {140, 180}, new int[] {170, 170}, PcbTraceRules.TRACE_WIDTH));
        PcbSilkscreenLabel identityLabel = new PcbSilkscreenLabel("identity-label", "U",
            new Rectangle(80, 60, 40, 16), 12, false, "IDENTITY_U.A");
        source.addSilkscreenLabel(identityLabel);
        source.setRoutingStatistics(11, 13, 17);
        PcbRoutingWork recoveryWork = new PcbRoutingWork(PcbRoutingWork.Limits.DEFAULT, 1);
        recoveryWork.beginNet("N1", false);
        recoveryWork.expand("N1");
        source.setRoutingRecoveryStatistics(recoveryWork.snapshot());
        source.setGenerationStatistics(4, 5, 6, 71L, -123456789L, -987654321L);
        String sourceStableBefore = stableIdentityCanonical(null, source);
        String sourceLabelsBefore = labelIdentityCanonical(source);
        String sourceGeometryBefore = PlayerSessionFingerprint.of(source.geometryFingerprint());
        check(source.matchesGenerationSeeds(-123456789L, -987654321L),
            "source retains literal signed generation seeds");
        PcbBoardLayout copy = freshUnlabeled(source);
        check(copy != source && copy.getSilkscreenLabels().isEmpty(),
            "fresh private copy is a separate unlabeled layout owner");
        check(stableIdentityCanonical(null, source).equals(stableIdentityCanonical(null, copy)) &&
                copy.getTraces().get(0).getSourceId().equals("slide/identity") &&
                copy.getComponents().size() == 1 && copy.getPads().size() == 2,
            "real stable identity canonicalizer accepts the copied component, pads and trace");
        check(!copy.matchesGenerationSeeds(-123456789L, -987654321L) &&
                copy.getGenerationPlacementAttempts() == 0 &&
                copy.getGenerationRoutingAttempts() == 0 &&
                copy.getGenerationRoutingExpansions() == 0 &&
                copy.getGenerationRoutingMillis() == 0L,
            "fresh private copy explicitly does not copy signed generation statistics");
        check(copy.getRoutingExpansions() == 11 && copy.getRawRoutingSegments() == 13 &&
                copy.getRoutingCongestionRejections() == 17 &&
                source.getRoutingRecoveryStatistics().toCanonical().equals(
                    copy.getRoutingRecoveryStatistics().toCanonical()),
            "fresh private copy preserves literal routing and recovery statistics");
        PcbBoardLayout labeledCopy = freshUnlabeled(source);
        labeledCopy.addSilkscreenLabel(identityLabel);
        check(sourceLabelsBefore.equals(labelIdentityCanonical(labeledCopy)),
            "label canonicalizer accepts a private clone with the source label semantics");
        PcbBoardLayout traceMutated = freshUnlabeledWithTrace(source,
            new PcbTraceGeometry("slide/identity-mutated", "N1", "IDENTITY_U.A",
                "IDENTITY_U.K", PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
                new int[] {140, 180}, new int[] {170, 170}, PcbTraceRules.TRACE_WIDTH));
        check(!sourceStableBefore.equals(stableIdentityCanonical(null, traceMutated)),
            "real stable identity canonicalizer detects a trace source-ID mutation");
        PcbBoardLayout labelMutated = freshUnlabeled(source);
        labelMutated.addSilkscreenLabel(new PcbSilkscreenLabel("identity-label", "U",
            new Rectangle(80, 60, 40, 16), 12, false, "IDENTITY_U.K"));
        check(!sourceLabelsBefore.equals(labelIdentityCanonical(labelMutated)),
            "real label canonicalizer detects a target-pad mutation");
        check(sourceStableBefore.equals(stableIdentityCanonical(null, source)) &&
                sourceLabelsBefore.equals(labelIdentityCanonical(source)) &&
                sourceGeometryBefore.equals(PlayerSessionFingerprint.of(source.geometryFingerprint())) &&
                source.getSilkscreenLabels().size() == 1,
            "identity and label probes leave the labeled source unchanged");
        fixture("freshPrivateLayoutAndStableIdentity");
    }

    private static void strictImprovementComparator() {
        Metrics source = metricFixture(1000L, 100L, 4, 80);
        Metrics control = metricFixture(900L, 90L, 4, 70);
        Metrics strict = metricFixture(800L, 90L, 4, 70);
        Metrics tie = metricFixture(900L, 90L, 4, 70);
        Metrics copperRegression = metricFixture(800L, 91L, 4, 70);
        Metrics viaRegression = metricFixture(800L, 90L, 5, 70);
        Metrics voidRegression = metricFixture(800L, 90L, 4, 71);
        check(strictImprovement(strict, source, control),
            "strict improvement requires area below source/control and no other regression");
        check(!strictImprovement(tie, source, control),
            "equal control area is not a strict improvement");
        check(!strictImprovement(copperRegression, source, control),
            "copper regression blocks strict improvement");
        check(!strictImprovement(viaRegression, source, control),
            "via regression blocks strict improvement");
        check(!strictImprovement(voidRegression, source, control),
            "protected-empty-rectangle regression blocks strict improvement");
        check(areaReduction(copperRegression, source, control),
            "area reduction is reported independently of copper regression");
        fixture("strictImprovementComparator");
    }

    private static Metrics metricFixture(long area, long copper, int vias, int empty) {
        return new Metrics(area, copper, vias, empty, "0", "0", "0",
            PRIVATE_GENERATION_METADATA);
    }

    private static void selectedObserverOwnershipAndFailureCleanup() {
        final int[] calls = new int[] {0};
        SeededPcbLayoutGenerator.SelectedSlideObserver sentinel =
            new SeededPcbLayoutGenerator.SelectedSlideObserver() {
                public void onSelected(TroubleshootBoard board, PcbBoardLayout layout,
                        PcbPlacementPlanner.Plan plan, String route, int attempt) {
                    calls[0]++;
                    throw new AssertionError("null dispatch inspected its inputs");
                }
            };
        SeededPcbLayoutGenerator.SelectedSlideObserver prior =
            SeededPcbLayoutGenerator.setSelectedSlideObserver(sentinel);
        check(prior == null, "selected-slide observer starts unowned");
        try {
            check(SeededPcbLayoutGenerator.setSelectedSlideObserver(null) == sentinel,
                "selected-slide observer setter returns its owner");
            SeededPcbLayoutGenerator.dispatchSelectedSlide(null, null, null, null, 0);
            check(calls[0] == 0,
                "null selected-slide observer dispatch does not inspect null inputs");
            check(SeededPcbLayoutGenerator.setSelectedSlideObserver(sentinel) == null,
                "selected-slide sentinel is restored before the failing probe");
            boolean failed = false;
            try {
                observe(987654321L, "invalid-selected-slide-root");
            } catch (AssertionError expected) {
                failed = expected.getMessage() != null &&
                    expected.getMessage().startsWith("fixed routed-leg census root");
            }
            check(failed, "invalid selected-slide root failure propagates exactly");
            check(SeededPcbLayoutGenerator.setSelectedSlideObserver(null) == sentinel &&
                    calls[0] == 0,
                "selected-slide observe finally restores the prior owner");
        } finally {
            SeededPcbLayoutGenerator.setSelectedSlideObserver(prior);
        }
        fixture("selectedObserverOwnershipAndFailureCleanup");
    }

    private static void quietWhitespaceOwnerCleanup() {
        check(!PcbFunctionalGroupingContractTest.isSelectedSlideWhitespaceQuiet(),
            "selected-slide whitespace sampler starts unowned");
        TroubleshootBoard validBoard = new TroubleshootBoard("QUIET_LITERAL_FIXTURE");
        validBoard.addNet(new BoardNet("QUIET_A", BoardNet.RoutingRole.SIGNAL));
        validBoard.addNet(new BoardNet("QUIET_K", BoardNet.RoutingRole.SIGNAL));
        validBoard.addComponent(new BoardComponent("QUIET_LED", "LED",
            PhysicalPackages.THROUGH_HOLE_LED));
        validBoard.addPad(new BoardPad("QUIET_LED.A", "QUIET_LED", "A", "QUIET_A"));
        validBoard.addPad(new BoardPad("QUIET_LED.K", "QUIET_LED", "K", "QUIET_K"));
        PcbBoardLayout validLayout = new PcbBoardLayout(600, 500,
            new Rectangle(20, 20, 400, 300), new Rectangle(450, 20, 100, 300), VERSION);
        PcbFootprint footprint = literalLedFootprint("QUIET_LED", 120, 100);
        validLayout.addComponent(footprint.getPlacement());
        for(PcbPadPlacement pad:footprint.getPads())validLayout.addPad(pad);
        int sampledArea = PcbFunctionalGroupingContractTest.measureSelectedSlideWhitespace(
            validBoard, validLayout, 5L, VERSION);
        // Literal envelope106,64,118,171 blocks columns8..21/rows4..22.
        // The largest free rectangle is the right18columns by all30rows.
        check(sampledArea == 54000, "quiet sampler measures the independent literal GRID10 rectangle");
        check(!PcbFunctionalGroupingContractTest.isSelectedSlideWhitespaceQuiet(),
            "quiet sampler restores its owner after a successful measurement");
        boolean failed = false;
        try {
            PcbFunctionalGroupingContractTest.measureSelectedSlideWhitespace(
                null, null, 2L, VERSION);
        } catch (NullPointerException expected) {
            failed = true;
        }
        check(failed, "quiet sampler preserves the original null-input failure");
        check(!PcbFunctionalGroupingContractTest.isSelectedSlideWhitespaceQuiet(),
            "quiet sampler restores its owner after failure");
        fixture("quietWhitespaceOwnerCleanup");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void fixture(String name) {
        if (name == null || name.length() == 0)
            throw new AssertionError("Named fixture required");
        fixtures++;
    }

    private static void stringField(StringBuilder json, String value) {
        if (value == null) {
            json.append("null");
            return;
        }
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"') json.append("\\\"");
            else if (ch == '\\') json.append("\\\\");
            else if (ch == '\b') json.append("\\b");
            else if (ch == '\f') json.append("\\f");
            else if (ch == '\n') json.append("\\n");
            else if (ch == '\r') json.append("\\r");
            else if (ch == '\t') json.append("\\t");
            else if (ch < 0x20) json.append("\\u00")
                .append(Character.forDigit((ch >> 4) & 0xf, 16))
                .append(Character.forDigit(ch & 0xf, 16));
            else json.append(ch);
        }
        json.append('"');
    }
}
