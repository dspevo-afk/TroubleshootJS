package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Native-only physical-feasibility census for all canonical nonincreasing
 * passive moves after the existing accepted P07 route. Every row starts from
 * the same finalized source; no route, selected output, or live graph changes.
 */
public final class PcbRoutedPassiveFeasibilityCensusContractTest {
    private static final int VERSION = 15;
    private static final String SUMMARY_PREFIX =
        "PCB_ROUTED_PASSIVE_FEASIBILITY_CENSUS ";
    private static final String PRIVATE_GENERATION_METADATA =
        "NOT_ASSIGNED_PRIVATE_CANDIDATE";
    private static final String[] TARGET_PACKAGES = {
        "AXIAL_RESISTOR", "AXIAL_DIODE",
        "RADIAL_ELECTROLYTIC_CAPACITOR", "RADIAL_CERAMIC_CAPACITOR"
    };
    private static final String[] PLACEMENT_REASONS = {
        "ENVELOPE_ACCESS_OR_DOMAIN", "STRANDED_ESCAPE", "DOMAIN_CORRIDOR",
        "DISCONNECTED_ESCAPE_CHANNEL"
    };
    private static final String[] GEOMETRY_PREFIX_KEYS = {
        "COMPONENT_BODY_OVERLAP", "COMPONENT_COURTYARD_OVERLAP", "PAD_OVERLAP",
        "TRACE_OUTSIDE_BOARD", "TRACE_COMPONENT_COURTYARD", "TRACE_SELF_OVERLAP",
        "TRACE_SELF_INTERSECTION", "LABEL_COMPONENT_OVERLAP", "LABEL_PAD_OVERLAP",
        "LABEL_COPPER_OVERLAP", "LABEL_LABEL_OVERLAP", "FOREIGN_NET_CONTACT",
        "FOREIGN_COPPER_CLEARANCE", "PHYSICAL_NET_DISCONNECTED"
    };
    private static final String[] GEOMETRY_PREFIXES = {
        "PCB component bodies overlap: ", "PCB component routing courtyards overlap: ",
        "PCB pads overlap: ", "PCB trace leaves board: ",
        "PCB trace passes through component routing courtyard: ",
        "PCB trace has repeated or overlapping segments: ",
        "PCB trace self-intersects: ", "Silkscreen label overlaps component: ",
        "Silkscreen label overlaps pad: ", "Silkscreen label overlaps copper: ",
        "PCB silkscreen labels overlap: ", "Unrelated PCB conductors share copper: ",
        "PCB traces violate copper clearance: ", "PCB net is electrically disconnected: "
    };
    private static final String[] TWO_LAYER_KEYS = {
        "INSULATING_BODY_BARRIER", "INSULATING_SPAN", "LAND_DOMAIN_BARRIER",
        "LEAD_DOMAIN_BARRIER", "COPPER_DOMAIN_BARRIER", "DRILL_DOMAIN_BARRIER",
        "VIA_DOMAIN_OR_NET", "VIA_PACKAGE_COURTYARD", "VIA_PAD_CLEARANCE",
        "VIA_COPPER_CLEARANCE"
    };
    private static final String[] TWO_LAYER_MESSAGES = {
        "Insulating body crosses an unrelated barrier",
        "Package body crosses outside its declared insulating span",
        "Component land crosses a physical domain barrier",
        "Component lead crosses a physical domain barrier",
        "P07 copper crosses a physical domain barrier",
        "Drill or land crosses a physical domain barrier",
        "P07 via crosses a domain barrier or has an unknown net",
        "P07 via drills through a package courtyard",
        "P07 via overlaps a component pad or its clearance",
        "P07 via violates foreign copper clearance"
    };
    private static int assertions;
    private static int fixtures;

    private PcbRoutedPassiveFeasibilityCensusContractTest() { }

    public static void main(String[] args) {
        endpointStartEndMetadataAndDelta();
        rejectInvalidIncidenceOrEndpoint();
        footprintPadsUnrelatedGeometryAndSourceImmutability();
        observerOwnershipNullAndFailureCleanup();
        courtyardRegression();
        stageClassificationAndFailurePropagation();
        check(fixtures == 6, "exactly six named physical-feasibility fixture groups");
        System.out.println("PASS: PCB routed passive feasibility census contracts assertions=" +
            assertions + " fixtures=" + fixtures +
            " evaluator=physicalFeasibilityCensus");
    }

    /** Root wrapper entrypoint; old census report lines precede this summary. */
    static void observe(long seed, String label) {
        require(label != null && label.length() > 0,
            "physical-feasibility census label is present");
        Recorder recorder = new Recorder();
        PcbRoutedPassiveLegCensusContractTest.FeasibilityObserver previous =
            PcbRoutedPassiveLegCensusContractTest.setFeasibilityObserver(recorder);
        try {
            PcbRoutedPassiveLegCensusContractTest.observe(seed, label);
        } finally {
            PcbRoutedPassiveLegCensusContractTest.FeasibilityObserver displaced =
                PcbRoutedPassiveLegCensusContractTest.setFeasibilityObserver(previous);
            if (displaced != recorder)
                throw new IllegalStateException("Feasibility observer owner changed");
        }
        System.out.println(recorder.toFeasibilityJson(seed, label));
    }

    private static final class Recorder
            implements PcbRoutedPassiveLegCensusContractTest.FeasibilityObserver {
        long callbackStartedNanos;
        long callbackFinishedNanos;
        int callbacks;
        SourceContext source;
        final Vector<FeasibilityRow> rows = new Vector<FeasibilityRow>();
        int physicallyValidated, physicallyRejected;
        int placementRejected, geometryRejected, twoLayerRejected;

        public void onCandidates(TroubleshootBoard board, PcbBoardLayout layout,
                Vector<PcbRoutedPassiveLegCensusContractTest.MoveIntent> moves) {
            callbackStartedNanos = System.nanoTime();
            try {
                if (++callbacks != 1)
                    throw new IllegalStateException("Target feasibility callback duplicated");
                require(board != null && layout != null && moves != null,
                    "feasibility callback has source board, layout and move vector");
                require(moves.size() == 31,
                    "feasibility callback has the complete finite 31-move census");
                source = captureSource(board, layout);
                for (int index = 0; index < moves.size(); index++) {
                    GenerationWorkScope.check();
                    FeasibilityRow row = evaluate(source, moves.get(index));
                    rows.add(row);
                    if ("PHYSICAL_VALIDATED".equals(row.disposition)) physicallyValidated++;
                    if ("PHYSICAL_REJECTED".equals(row.disposition)) physicallyRejected++;
                    if ("PLACEMENT".equals(row.stage)) placementRejected++;
                    if ("GEOMETRY".equals(row.stage)) geometryRejected++;
                    if ("TWO_LAYER".equals(row.stage)) twoLayerRejected++;
                }
                require(rows.size() == moves.size(),
                    "every canonical structural direction has one census row");
                requireSourceUnchanged(source);
            } finally {
                callbackFinishedNanos = System.nanoTime();
            }
        }

        String toFeasibilityJson(long seed, String label) {
            StringBuilder json = new StringBuilder(160000);
            json.append(SUMMARY_PREFIX).append('{');
            json.append("\"seed\":"); stringField(json, Long.toString(seed));
            json.append(",\"label\":"); stringField(json, label);
            json.append(",\"version\":").append(VERSION);
            json.append(",\"phase\":\"FINALIZED_ROUTE_CANDIDATE\"");
            json.append(",\"status\":"); stringField(json,
            callbacks == 0 ? "NO_TARGET" : "CENSUS_COMPLETE");
            json.append(",\"newRouteRequests\":0");
            json.append(",\"selectedMutation\":false");
            json.append(",\"constructionCount\":").append(rows.size());
            json.append(",\"source\":");
            if (source == null) json.append("null");
            else appendFeasibilitySource(json, source.facts);
            json.append(",\"rows\":[");
            for (int index = 0; index < rows.size(); index++) {
                if (index != 0) json.append(',');
                appendFeasibilityRow(json, rows.get(index));
            }
            json.append(']');
            json.append(",\"aggregate\":{");
            json.append("\"structuralDirections\":").append(rows.size());
            json.append(",\"constructionCount\":").append(rows.size());
            json.append(",\"physicallyValidated\":").append(physicallyValidated);
            json.append(",\"physicallyRejected\":").append(physicallyRejected);
            json.append(",\"placementRejected\":").append(placementRejected);
            json.append(",\"geometryRejected\":").append(geometryRejected);
            json.append(",\"twoLayerRejected\":").append(twoLayerRejected);
            json.append('}');
            json.append(",\"elapsedMillis\":").append(callbackStartedNanos == 0L ? 0L :
                elapsedBetween(callbackStartedNanos, callbackFinishedNanos));
            json.append('}');
            return json.toString();
        }
    }

    private static SourceContext captureSource(TroubleshootBoard board,
            PcbBoardLayout source) {
        GenerationWorkScope.check();
        String liveBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        String geometryBefore = PlayerSessionFingerprint.of(source.geometryFingerprint());
        Vector<PcbTraceGeometry> traces = source.getTraces();
        TreeSet<String> routeSources = requireUniqueTraceSources(traces);
        Vector<String> componentIds = new Vector<String>(board.getComponentIds());
        Collections.sort(componentIds);
        TreeMap<String,PcbFootprint> footprints =
            new TreeMap<String,PcbFootprint>();
        for (String componentId : componentIds) {
            GenerationWorkScope.check();
            footprints.put(componentId, footprintFromLayout(board, source, componentId));
        }
        Map<String,Point> padCenters = padCenters(source);
        requireKnownPlatedHoleNets(board, source);
        PcbConductorGraph graph = source.captureConductorGraph(board);
        require(traceGraphSources(graph).equals(routeSources),
            "source graph covers the complete unique route-source set");
        Vector<String> pads = new Vector<String>(board.getPadIds());
        Collections.sort(pads);
        SourceReachability reachability = verifySourceReachability(board, graph, pads);
        String provenance = traceProvenanceHash(traces);
        String liveAfter = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        String geometryAfter = PlayerSessionFingerprint.of(source.geometryFingerprint());
        require(liveBefore.equals(liveAfter) && geometryBefore.equals(geometryAfter),
            "source hashes are unchanged during source snapshot");
        FeasibilitySourceFacts facts = new FeasibilitySourceFacts(liveBefore, liveAfter,
            geometryBefore, geometryAfter, copperLength(traces), area(source),
            source.getComponents().size(), source.getPads().size(), traces.size(),
            provenance, routeSources.size(), reachability.hash, reachability.pairCount);
        return new SourceContext(board, source, traces, routeSources, componentIds,
            footprints, padCenters, graph, reachability, provenance, facts);
    }

    private static FeasibilityRow evaluate(SourceContext source,
            PcbRoutedPassiveLegCensusContractTest.MoveIntent move) {
        long totalStarted = System.nanoTime();
        GenerationWorkScope.check();
        require(move != null && move.componentId != null && move.componentId.length() > 0,
            "census move has a component identity");
        require((move.dx == 0) != (move.dy == 0) &&
                (Math.abs((long)move.dx) == 10L || Math.abs((long)move.dy) == 10L),
            "census move is one canonical ten-unit axis step");
        require(move.analyticCopperDelta <= 0L,
            "census move has a nonincreasing analytic copper delta");
        long constructionStarted = totalStarted;
        FeasibilityCandidate built = buildFeasibilityCandidate(source, move);
        long constructionMillis = elapsedSince(constructionStarted);
        requireSourceUnchanged(source);

        boolean placementValidated = false;
        boolean geometryValidated = false;
        boolean twoLayerValidated = false;
        long placementStarted = System.nanoTime();
        try {
            GenerationWorkScope.check();
            PcbPlacementPlanner.validate(source.board, source.board.getPlacementConstraints(),
                built.candidate.getBoardOutline(), built.footprintVector);
            placementValidated = true;
        } catch (PcbPlacementPlanner.Rejected failure) {
            String reason = classifyPlacementOrThrow(failure);
            requireSourceUnchanged(source);
            return rejectedFeasibilityRow(built, reason, "PLACEMENT", failure.getMessage(),
                Long.valueOf(constructionMillis),
                Long.valueOf(elapsedSince(placementStarted)), null, null,
                elapsedSince(totalStarted), false, false, false);
        }
        long placementMillis = elapsedSince(placementStarted);
        requireSourceUnchanged(source);

        long geometryStarted = System.nanoTime();
        try {
            GenerationWorkScope.check();
            built.candidate.validateGeometry(source.board);
            geometryValidated = true;
        } catch (IllegalStateException failure) {
            StageReason classified = classifyGeometryOrThrow(failure);
            requireSourceUnchanged(source);
            return rejectedFeasibilityRow(built, classified.reason, classified.stage,
                failure.getMessage(), Long.valueOf(constructionMillis),
                Long.valueOf(placementMillis), Long.valueOf(elapsedSince(geometryStarted)),
                null, elapsedSince(totalStarted), true, false, false);
        }
        long geometryMillis = elapsedSince(geometryStarted);
        requireSourceUnchanged(source);

        long twoLayerStarted = System.nanoTime();
        try {
            GenerationWorkScope.check();
            new PcbTwoLayerRules(source.board, built.candidate).validate(built.candidate);
            twoLayerValidated = true;
        } catch (IllegalStateException failure) {
            StageReason classified = classifyTwoLayerOrThrow(failure);
            requireSourceUnchanged(source);
            return rejectedFeasibilityRow(built, classified.reason, "TWO_LAYER",
                failure.getMessage(), Long.valueOf(constructionMillis),
                Long.valueOf(placementMillis), Long.valueOf(geometryMillis),
                Long.valueOf(elapsedSince(twoLayerStarted)), elapsedSince(totalStarted),
                true, true, false);
        }
        long twoLayerMillis = elapsedSince(twoLayerStarted);
        requireSourceUnchanged(source);

        long proofStarted = System.nanoTime();
        GenerationWorkScope.check();
        PcbConductorGraph candidateGraph = built.candidate.captureConductorGraph(source.board);
        ReachabilityEvidence reachability = verifyCandidateReachability(source.board,
            candidateGraph, source.reachability);
        String candidateProvenance = traceProvenanceHash(built.candidate.getTraces());
        require(source.provenance.equals(candidateProvenance),
            "candidate trace metadata provenance remains identical");
        require(traceGraphSources(candidateGraph).equals(source.routeSources),
            "candidate graph provenance covers complete source routes");
        requireSourceUnchanged(source);
        ProofFacts proof = new ProofFacts(reachability, source.routeSources.size(),
            traceGraphSources(candidateGraph).size(), source.provenance, candidateProvenance);
        return new FeasibilityRow(built, "PHYSICAL_VALIDATED", "COMPLETE", null, null,
            new FeasibilityChecks(placementValidated, geometryValidated, twoLayerValidated),
            proof, new FeasibilityTiming(Long.valueOf(constructionMillis),
                Long.valueOf(placementMillis), Long.valueOf(geometryMillis),
                Long.valueOf(twoLayerMillis), Long.valueOf(elapsedSince(proofStarted)),
                Long.valueOf(elapsedSince(totalStarted))));
    }

    private static FeasibilityRow rejectedFeasibilityRow(FeasibilityCandidate built,
            String reason, String stage, String message, Long constructionMillis,
            Long placementMillis, Long geometryMillis, Long twoLayerMillis,
            long totalMillis, boolean placementValidated, boolean geometryValidated,
            boolean twoLayerValidated) {
        return new FeasibilityRow(built, "PHYSICAL_REJECTED", stage, reason, message,
            new FeasibilityChecks(placementValidated, geometryValidated,
                twoLayerValidated), null, new FeasibilityTiming(constructionMillis,
                placementMillis, geometryMillis, twoLayerMillis, null,
                Long.valueOf(totalMillis)));
    }

    private static FeasibilityCandidate buildFeasibilityCandidate(SourceContext source,
            PcbRoutedPassiveLegCensusContractTest.MoveIntent move) {
        GenerationWorkScope.check();
        BoardComponent target = source.board.getComponent(move.componentId);
        PcbComponentPlacement targetPlacement = source.source.getComponent(move.componentId);
        require(target != null && targetPlacement != null,
            "observer target is present in board and finalized layout: " + move.componentId);
        require(isTargetPackage(targetPlacement.getPhysicalPackage().getId()),
            "observer target is a census passive package: " + move.componentId);
        Vector<String> targetPadOrder = target.getPadIds();
        require(targetPadOrder.size() == 2,
            "private target has exactly two board pads: " + move.componentId);
        TreeSet<String> movedPadIds = new TreeSet<String>();
        for (String padId : targetPadOrder) {
            GenerationWorkScope.check();
            require(movedPadIds.add(padId), "target pad IDs are unique: " + padId);
            require(source.source.getPad(padId) != null,
                "target pad has a finalized source placement: " + padId);
        }

        TreeMap<String,PcbFootprint> candidateFootprints =
            new TreeMap<String,PcbFootprint>();
        Vector<PcbFootprint> footprintVector = new Vector<PcbFootprint>();
        for (String componentId : source.componentIds) {
            GenerationWorkScope.check();
            PcbFootprint original = footprintFromLayout(source.board, source.source, componentId);
            PcbFootprint candidate = componentId.equals(move.componentId) ?
                original.translated(targetPlacement.getX() + move.dx,
                    targetPlacement.getY() + move.dy) : original;
            candidateFootprints.put(componentId, candidate);
            footprintVector.add(candidate);
        }

        PcbBoardLayout candidate = new PcbBoardLayout(source.source.getWidth(),
            source.source.getHeight(), source.source.getBoardOutline(),
            source.source.getPartsTray(), source.source.getLayoutAlgorithmVersion());
        for (String componentId : source.componentIds) {
            GenerationWorkScope.check();
            PcbFootprint footprint = candidateFootprints.get(componentId);
            candidate.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads()) candidate.addPad(pad);
        }
        for (PcbLayoutRegion region : source.source.getRegions()) {
            GenerationWorkScope.check(); candidate.addRegion(region);
        }
        for (PcbBoardHole hole : source.source.getHoles()) {
            GenerationWorkScope.check(); candidate.addHole(hole);
        }
        requireKnownPlatedHoleNets(source.board, candidate);
        for (PcbSilkscreenLabel label : source.source.getSilkscreenLabels()) {
            GenerationWorkScope.check();
            candidate.addSilkscreenLabel(copyLabel(label, move.componentId,
                movedPadIds, move.dx, move.dy));
        }

        Map<String,Point> candidatePadCenters = padCenters(candidate);
        TraceRewrite rewrite = rewriteTraces(source.traces, movedPadIds,
            source.padCenters, candidatePadCenters);
        for (PcbTraceGeometry trace : rewrite.traces) {
            GenerationWorkScope.check(); candidate.addTrace(trace);
        }
        candidate.setRoutingStatistics(source.source.getRoutingExpansions(),
            source.source.getRawRoutingSegments(), source.source.getRoutingCongestionRejections());
        if (source.source.getRoutingRecoveryStatistics() != null)
            candidate.setRoutingRecoveryStatistics(source.source.getRoutingRecoveryStatistics());

        requireSourceUnchanged(source);
        require(verifyTraceEndpointOnly(source.traces, candidate.getTraces(), movedPadIds,
            source.padCenters, candidatePadCenters),
            "only explicit moved trace endpoints changed");
        require(verifyUnrelatedGeometry(source.board, source.source, candidate,
            source.footprints, candidateFootprints, move.componentId, movedPadIds,
            move.dx, move.dy),
            "unrelated footprints, pads, labels, holes, regions and envelopes are unchanged");
        long actualCopperDelta = copperLength(candidate.getTraces()) - copperLength(source.traces);
        require(actualCopperDelta == move.analyticCopperDelta,
            "actual total copper delta equals supplied analytic delta");
        require(rewrite.coveredPadIds.equals(movedPadIds) && rewrite.affectedTraceCount > 0 &&
                rewrite.movedPadCount == 2,
            "candidate endpoint coverage includes exactly two moved pads");
        String candidateGeometry = PlayerSessionFingerprint.of(candidate.geometryFingerprint());
        FeasibilityCandidateFacts facts = new FeasibilityCandidateFacts(candidateGeometry,
            candidate.getComponents().size(), candidate.getPads().size(),
            candidate.getTraces().size(), rewrite.affectedTraceCount, rewrite.movedPadCount,
            copperLength(candidate.getTraces()), area(candidate));
        return new FeasibilityCandidate(move.componentId, move.dx, move.dy,
            move.analyticCopperDelta, actualCopperDelta, candidate, footprintVector,
            facts, true, true, true, false, false, false);
    }

    private static long elapsedSince(long started) {
        long value = System.nanoTime() - started;
        return value <= 0L ? 0L : value / 1000000L;
    }

    private static long elapsedBetween(long started, long finished) {
        long end = finished == 0L ? System.nanoTime() : finished;
        long value = end - started;
        return value <= 0L ? 0L : value / 1000000L;
    }

    private static void requireSourceUnchanged(SourceContext source) {
        String graph = PcbCanvasBoundaryContractTest.liveGraphSha256(source.board);
        String geometry = PlayerSessionFingerprint.of(source.source.geometryFingerprint());
        require(source.facts.liveGraphBefore.equals(graph) &&
                source.facts.geometryBefore.equals(geometry),
            "source graph and geometry remain immutable");
    }

    private static void requireKnownPlatedHoleNets(TroubleshootBoard board,
            PcbBoardLayout layout) {
        for (PcbBoardHole hole : layout.getHoles()) {
            GenerationWorkScope.check();
            if (hole.kind != PcbBoardHole.Kind.NON_PLATED) {
                require(hole.netId != null && board.getNet(hole.netId) != null,
                    "every plated source or candidate hole has a known board net: " +
                    hole.id);
            }
        }
    }

    private static String classifyPlacementOrThrow(PcbPlacementPlanner.Rejected failure) {
        for (String reason : PLACEMENT_REASONS)
            if (reason.equals(failure.reason)) return reason;
        throw failure;
    }

    private static StageReason classifyGeometryOrThrow(IllegalStateException failure) {
        if (failure instanceof PcbBoardLayout.RouteQualityRejectedException) {
            PcbBoardLayout.RouteQualityRejectedException quality =
                (PcbBoardLayout.RouteQualityRejectedException)failure;
            if (quality.getKind() == PcbBoardLayout.RouteQualityRejectedException.Kind.BENDS)
                return new StageReason("GEOMETRY", "ROUTE_QUALITY_BENDS");
            if (quality.getKind() == PcbBoardLayout.RouteQualityRejectedException.Kind.DETOUR)
                return new StageReason("GEOMETRY", "ROUTE_QUALITY_DETOUR");
            throw failure;
        }
        if (failure.getClass() != IllegalStateException.class)
            throw failure;
        for (int index = 0; index < GEOMETRY_PREFIXES.length; index++)
            if (hasNonEmptyPrefix(failure.getMessage(), GEOMETRY_PREFIXES[index]))
                return new StageReason("GEOMETRY", GEOMETRY_PREFIX_KEYS[index]);
        for (int index = 0; index < TWO_LAYER_MESSAGES.length; index++)
            if (TWO_LAYER_MESSAGES[index].equals(failure.getMessage()))
                return new StageReason("GEOMETRY", TWO_LAYER_KEYS[index]);
        throw failure;
    }

    private static StageReason classifyTwoLayerOrThrow(IllegalStateException failure) {
        if (failure.getClass() != IllegalStateException.class)
            throw failure;
        for (int index = 0; index < TWO_LAYER_MESSAGES.length; index++)
            if (TWO_LAYER_MESSAGES[index].equals(failure.getMessage()))
                return new StageReason("TWO_LAYER", TWO_LAYER_KEYS[index]);
        throw failure;
    }

    private static boolean hasNonEmptyPrefix(String message, String prefix) {
        return message != null && message.length() > prefix.length() &&
            message.startsWith(prefix);
    }

    private static final class StageReason {
        final String stage, reason;
        StageReason(String stage, String reason) { this.stage = stage; this.reason = reason; }
    }

    private static final class SourceContext {
        final TroubleshootBoard board; final PcbBoardLayout source;
        final Vector<PcbTraceGeometry> traces; final TreeSet<String> routeSources;
        final Vector<String> componentIds; final TreeMap<String,PcbFootprint> footprints;
        final Map<String,Point> padCenters; final PcbConductorGraph graph;
        final SourceReachability reachability; final String provenance;
        final FeasibilitySourceFacts facts;
        SourceContext(TroubleshootBoard board, PcbBoardLayout source,
                Vector<PcbTraceGeometry> traces, TreeSet<String> routeSources,
                Vector<String> componentIds, TreeMap<String,PcbFootprint> footprints,
                Map<String,Point> padCenters, PcbConductorGraph graph,
                SourceReachability reachability, String provenance,
                FeasibilitySourceFacts facts) {
            this.board = board; this.source = source; this.traces = traces;
            this.routeSources = routeSources; this.componentIds = componentIds;
            this.footprints = footprints; this.padCenters = padCenters;
            this.graph = graph; this.reachability = reachability;
            this.provenance = provenance; this.facts = facts;
        }
    }

    private static final class SourceReachability {
        final Vector<String> pads; final PcbConductorGraph.Snapshot snapshot;
        final int pairCount; final String hash;
        SourceReachability(Vector<String> pads, PcbConductorGraph.Snapshot snapshot,
                int pairCount, String hash) {
            this.pads = pads; this.snapshot = snapshot;
            this.pairCount = pairCount; this.hash = hash;
        }
    }

    private static final class FeasibilityCandidate {
        final String componentId; final int dx, dy;
        final long analyticCopperDelta, actualCopperDelta;
        final PcbBoardLayout candidate; final Vector<PcbFootprint> footprintVector;
        final FeasibilityCandidateFacts facts;
        final boolean endpointOnlyVerified, unrelatedGeometryVerified, sourceImmutable;
        final boolean placementValidated, geometryValidated, twoLayerValidated;
        FeasibilityCandidate(String componentId, int dx, int dy,
                long analyticCopperDelta, long actualCopperDelta, PcbBoardLayout candidate,
                Vector<PcbFootprint> footprintVector, FeasibilityCandidateFacts facts,
                boolean endpointOnlyVerified, boolean unrelatedGeometryVerified,
                boolean sourceImmutable, boolean placementValidated,
                boolean geometryValidated, boolean twoLayerValidated) {
            this.componentId = componentId; this.dx = dx; this.dy = dy;
            this.analyticCopperDelta = analyticCopperDelta;
            this.actualCopperDelta = actualCopperDelta; this.candidate = candidate;
            this.footprintVector = footprintVector; this.facts = facts;
            this.endpointOnlyVerified = endpointOnlyVerified;
            this.unrelatedGeometryVerified = unrelatedGeometryVerified;
            this.sourceImmutable = sourceImmutable;
            this.placementValidated = placementValidated;
            this.geometryValidated = geometryValidated;
            this.twoLayerValidated = twoLayerValidated;
        }
    }

    private static final class FeasibilityRow {
        final FeasibilityCandidate candidate;
        final String disposition, stage, reason, message;
        final FeasibilityChecks checks; final ProofFacts proof;
        final FeasibilityTiming timing;
        FeasibilityRow(FeasibilityCandidate candidate, String disposition,
                String stage, String reason, String message, FeasibilityChecks checks,
                ProofFacts proof, FeasibilityTiming timing) {
            this.candidate = candidate; this.disposition = disposition; this.stage = stage;
            this.reason = reason; this.message = message; this.checks = checks;
            this.proof = proof; this.timing = timing;
        }
    }

    private static final class FeasibilityChecks {
        final boolean endpointOnly = true, unrelatedGeometry = true, sourceImmutable = true;
        final boolean placementValidated, geometryValidated, twoLayerValidated;
        FeasibilityChecks(boolean placementValidated, boolean geometryValidated,
                boolean twoLayerValidated) {
            this.placementValidated = placementValidated;
            this.geometryValidated = geometryValidated;
            this.twoLayerValidated = twoLayerValidated;
        }
    }

    private static final class FeasibilityTiming {
        final Long constructionMillis, placementMillis, geometryMillis;
        final Long twoLayerMillis, proofMillis, totalMillis;
        FeasibilityTiming(Long constructionMillis, Long placementMillis, Long geometryMillis,
                Long twoLayerMillis, Long proofMillis, Long totalMillis) {
            this.constructionMillis = constructionMillis; this.placementMillis = placementMillis;
            this.geometryMillis = geometryMillis; this.twoLayerMillis = twoLayerMillis;
            this.proofMillis = proofMillis; this.totalMillis = totalMillis;
        }
    }

    private static final class FeasibilityCandidateFacts {
        final String geometryHash; final int componentCount, padCount, traceCount;
        final int affectedTraceCount, movedPadCount; final long copperLength, area;
        FeasibilityCandidateFacts(String geometryHash, int componentCount, int padCount,
                int traceCount, int affectedTraceCount, int movedPadCount,
                long copperLength, long area) {
            this.geometryHash = geometryHash; this.componentCount = componentCount;
            this.padCount = padCount; this.traceCount = traceCount;
            this.affectedTraceCount = affectedTraceCount; this.movedPadCount = movedPadCount;
            this.copperLength = copperLength; this.area = area;
        }
    }

    private static final class FeasibilitySourceFacts {
        final String liveGraphBefore, liveGraphAfter, geometryBefore, geometryAfter;
        final long copperLength, area; final int componentCount, padCount, traceCount;
        final String traceProvenanceHash; final int sourceRouteSourceCount, padPairCount;
        final String reachabilityHash;
        FeasibilitySourceFacts(String liveGraphBefore, String liveGraphAfter,
                String geometryBefore, String geometryAfter, long copperLength, long area,
                int componentCount, int padCount, int traceCount,
                String traceProvenanceHash, int sourceRouteSourceCount,
                String reachabilityHash, int padPairCount) {
            this.liveGraphBefore = liveGraphBefore; this.liveGraphAfter = liveGraphAfter;
            this.geometryBefore = geometryBefore; this.geometryAfter = geometryAfter;
            this.copperLength = copperLength; this.area = area;
            this.componentCount = componentCount; this.padCount = padCount;
            this.traceCount = traceCount; this.traceProvenanceHash = traceProvenanceHash;
            this.sourceRouteSourceCount = sourceRouteSourceCount;
            this.reachabilityHash = reachabilityHash; this.padPairCount = padPairCount;
        }
    }

    private static SourceReachability verifySourceReachability(TroubleshootBoard board,
            PcbConductorGraph graph, Vector<String> pads) {
        PcbConductorGraph.Snapshot snapshot = graph.pristine();
        StringBuilder bits = new StringBuilder("R1\n");
        int pairCount = 0;
        for (int first = 0; first < pads.size(); first++) {
            for (int second = first + 1; second < pads.size(); second++) {
                GenerationWorkScope.check();
                String a = pads.get(first), b = pads.get(second);
                boolean expected = board.getPad(a).getNetId().equals(board.getPad(b).getNetId());
                boolean connected = snapshot.padsConnected(a, b);
                require(connected == expected,
                    "source conductor reachability matches board net identity: " + a + "/" + b);
                appendReachability(bits, a, b, connected); pairCount++;
            }
        }
        String hash = PlayerSessionFingerprint.of(bits.toString());
        requireHash(hash);
        return new SourceReachability(pads, snapshot, pairCount, hash);
    }

    private static ReachabilityEvidence verifyCandidateReachability(TroubleshootBoard board,
            PcbConductorGraph graph, SourceReachability source) {
        PcbConductorGraph.Snapshot snapshot = graph.pristine();
        StringBuilder bits = new StringBuilder("R1\n");
        int pairCount = 0;
        for (int first = 0; first < source.pads.size(); first++) {
            for (int second = first + 1; second < source.pads.size(); second++) {
                GenerationWorkScope.check();
                String a = source.pads.get(first), b = source.pads.get(second);
                boolean expected = board.getPad(a).getNetId().equals(board.getPad(b).getNetId());
                boolean connected = snapshot.padsConnected(a, b);
                require(connected == expected,
                    "candidate conductor reachability matches board net identity: " + a + "/" + b);
                appendReachability(bits, a, b, connected); pairCount++;
            }
        }
        String hash = PlayerSessionFingerprint.of(bits.toString());
        require(hash.equals(source.hash),
            "source and candidate canonical pad reachability hashes match");
        requireHash(hash); require(pairCount == source.pairCount,
            "candidate reachability covers the same canonical pad pairs");
        return new ReachabilityEvidence(source.pairCount, source.hash, hash);
    }

    private static void appendFeasibilityRow(StringBuilder json, FeasibilityRow row) {
        json.append('{');
        json.append("\"move\":{");
        json.append("\"componentId\":"); stringField(json, row.candidate.componentId);
        json.append(",\"dx\":").append(row.candidate.dx);
        json.append(",\"dy\":").append(row.candidate.dy);
        json.append(",\"analyticCopperDelta\":").append(row.candidate.analyticCopperDelta);
        json.append(",\"actualCopperDelta\":").append(row.candidate.actualCopperDelta);
        json.append('}');
        json.append(",\"candidate\":"); appendFeasibilityCandidate(json, row.candidate.facts);
        json.append(",\"disposition\":"); stringField(json, row.disposition);
        json.append(",\"stage\":"); stringField(json, row.stage);
        json.append(",\"reason\":"); stringField(json, row.reason);
        json.append(",\"message\":"); stringField(json, row.message);
        json.append(",\"checks\":{");
        json.append("\"traceEndpointOnlyVerified\":").append(row.checks.endpointOnly);
        json.append(",\"unrelatedGeometryVerified\":").append(row.checks.unrelatedGeometry);
        json.append(",\"sourceImmutable\":").append(row.checks.sourceImmutable);
        json.append(",\"placementValidated\":").append(row.checks.placementValidated);
        json.append(",\"geometryValidated\":").append(row.checks.geometryValidated);
        json.append(",\"twoLayerValidated\":").append(row.checks.twoLayerValidated);
        json.append('}');
        json.append(",\"proof\":");
        if (row.proof == null) json.append("null"); else appendProof(json, row.proof);
        json.append(",\"timing\":{");
        appendMillis(json, "constructionMillis", row.timing.constructionMillis);
        appendMillis(json, "placementMillis", row.timing.placementMillis);
        appendMillis(json, "geometryMillis", row.timing.geometryMillis);
        appendMillis(json, "twoLayerMillis", row.timing.twoLayerMillis);
        appendMillis(json, "proofMillis", row.timing.proofMillis);
        appendMillis(json, "totalMillis", row.timing.totalMillis);
        json.append('}');
        json.append('}');
    }

    private static void appendMillis(StringBuilder json, String name, Long value) {
        json.append('"').append(name).append("\":");
        if (value == null) json.append("null"); else json.append(value.longValue());
        if (!"totalMillis".equals(name)) json.append(',');
    }

    private static void appendFeasibilitySource(StringBuilder json,
            FeasibilitySourceFacts source) {
        json.append('{');
        json.append("\"liveGraphBefore\":"); stringField(json, source.liveGraphBefore);
        json.append(",\"liveGraphAfter\":"); stringField(json, source.liveGraphAfter);
        json.append(",\"geometryBefore\":"); stringField(json, source.geometryBefore);
        json.append(",\"geometryAfter\":"); stringField(json, source.geometryAfter);
        json.append(",\"copperLength\":").append(source.copperLength);
        json.append(",\"area\":").append(source.area);
        json.append(",\"componentCount\":").append(source.componentCount);
        json.append(",\"padCount\":").append(source.padCount);
        json.append(",\"traceCount\":").append(source.traceCount);
        json.append(",\"traceProvenanceHash\":"); stringField(json, source.traceProvenanceHash);
        json.append(",\"sourceRouteSourceCount\":").append(source.sourceRouteSourceCount);
        json.append(",\"reachabilityHash\":"); stringField(json, source.reachabilityHash);
        json.append(",\"padPairCount\":").append(source.padPairCount);
        json.append('}');
    }

    private static void appendFeasibilityCandidate(StringBuilder json,
            FeasibilityCandidateFacts candidate) {
        json.append('{');
        json.append("\"geometryHash\":"); stringField(json, candidate.geometryHash);
        json.append(",\"componentCount\":").append(candidate.componentCount);
        json.append(",\"padCount\":").append(candidate.padCount);
        json.append(",\"traceCount\":").append(candidate.traceCount);
        json.append(",\"affectedTraceCount\":").append(candidate.affectedTraceCount);
        json.append(",\"movedPadCount\":").append(candidate.movedPadCount);
        json.append(",\"copperLength\":").append(candidate.copperLength);
        json.append(",\"area\":").append(candidate.area);
        json.append(",\"generationMetadata\":");
        stringField(json, PRIVATE_GENERATION_METADATA);
        json.append('}');
    }

    private static void appendReachability(StringBuilder out, String a, String b,
            boolean connected) {
        out.append(PcbConductorGraph.field(a)).append(PcbConductorGraph.field(b))
            .append(connected ? '1' : '0').append('\n');
    }

    private static PcbFootprint footprintFromLayout(TroubleshootBoard board,
            PcbBoardLayout layout, String componentId) {
        BoardComponent component = board.getComponent(componentId);
        PcbComponentPlacement placement = layout.getComponent(componentId);
        require(component != null && placement != null,
            "source footprint has a board component and placement: " + componentId);
        Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
        for (String padId : component.getPadIds()) {
            PcbPadPlacement pad = layout.getPad(padId);
            require(pad != null && padId.equals(pad.getPadId()),
                "source footprint follows board component pad order: " + padId);
            pads.add(pad);
        }
        return new PcbFootprint(placement, pads);
    }

    private static Map<String,Point> padCenters(PcbBoardLayout layout) {
        Map<String,Point> result = new HashMap<String,Point>();
        for (PcbPadPlacement pad : layout.getPads()) {
            if (result.put(pad.getPadId(), new Point(pad.getX(), pad.getY())) != null)
                throw new IllegalStateException("Duplicate pad center: " + pad.getPadId());
        }
        return result;
    }

    private static TreeSet<String> requireUniqueTraceSources(
            Vector<PcbTraceGeometry> traces) {
        TreeSet<String> result = new TreeSet<String>();
        for (PcbTraceGeometry trace : traces) {
            GenerationWorkScope.check();
            if (trace.getSourceId() == null || trace.getSourceId().length() == 0)
                throw new IllegalStateException("Routed trace is missing source identity");
            if (!result.add(trace.getSourceId()))
                throw new IllegalStateException("Duplicate routed trace source identity: " +
                    trace.getSourceId());
        }
        return result;
    }

    private static final class TraceRewrite {
        final Vector<PcbTraceGeometry> traces;
        final Set<String> coveredPadIds;
        final int affectedTraceCount;
        final int movedPadCount;

        TraceRewrite(Vector<PcbTraceGeometry> traces, Set<String> coveredPadIds,
                int affectedTraceCount) {
            this.traces = traces;
            this.coveredPadIds = coveredPadIds;
            this.affectedTraceCount = affectedTraceCount;
            this.movedPadCount = coveredPadIds.size();
        }
    }

    private static TraceRewrite rewriteTraces(Vector<PcbTraceGeometry> source,
            Set<String> movedPadIds, Map<String,Point> sourcePadCenters,
            Map<String,Point> candidatePadCenters) {
        Vector<PcbTraceGeometry> result = new Vector<PcbTraceGeometry>();
        TreeSet<String> sources = new TreeSet<String>();
        TreeSet<String> covered = new TreeSet<String>();
        int affected = 0;
        for (PcbTraceGeometry trace : source) {
            GenerationWorkScope.check();
            String sourceId = trace.getSourceId();
            if (sourceId == null || sourceId.length() == 0)
                throw new IllegalStateException("Routed trace is missing source identity");
            if (!sources.add(sourceId))
                throw new IllegalStateException("Duplicate routed trace source identity: " +
                    sourceId);
            int[] x = trace.getXPoints();
            int[] y = trace.getYPoints();
            boolean changed = false;
            String startPadId = trace.getStartPadId();
            if (startPadId != null) {
                Point sourcePoint = sourcePadCenters.get(startPadId);
                Point candidatePoint = candidatePadCenters.get(startPadId);
                if (sourcePoint == null || candidatePoint == null)
                    throw new IllegalStateException("Trace start endpoint has no pad center: " +
                        startPadId);
                requireEndpoint(x[0], y[0], sourcePoint,
                    "Trace start endpoint disagrees with source pad: " + startPadId);
                if (movedPadIds.contains(startPadId)) {
                    x[0] = candidatePoint.x; y[0] = candidatePoint.y;
                    covered.add(startPadId); changed = true;
                }
            }
            String endPadId = trace.getEndPadId();
            if (endPadId != null) {
                Point sourcePoint = sourcePadCenters.get(endPadId);
                Point candidatePoint = candidatePadCenters.get(endPadId);
                if (sourcePoint == null || candidatePoint == null)
                    throw new IllegalStateException("Trace end endpoint has no pad center: " +
                        endPadId);
                int last = x.length - 1;
                requireEndpoint(x[last], y[last], sourcePoint,
                    "Trace end endpoint disagrees with source pad: " + endPadId);
                if (movedPadIds.contains(endPadId)) {
                    x[last] = candidatePoint.x; y[last] = candidatePoint.y;
                    covered.add(endPadId); changed = true;
                }
            }
            result.add(changed ? trace.withPath(x, y) : trace);
            if (changed) affected++;
        }
        if (!covered.equals(movedPadIds))
            throw new IllegalStateException("Moved pads lack complete explicit endpoint coverage");
        return new TraceRewrite(result, covered, affected);
    }

    private static void requireEndpoint(int x, int y, Point expected, String message) {
        if (x != expected.x || y != expected.y) throw new IllegalStateException(message);
    }

    private static boolean verifyTraceEndpointOnly(Vector<PcbTraceGeometry> source,
            Vector<PcbTraceGeometry> candidate, Set<String> movedPadIds,
            Map<String,Point> sourcePadCenters, Map<String,Point> candidatePadCenters) {
        TreeMap<String,PcbTraceGeometry> sourceById = traceMap(source);
        TreeMap<String,PcbTraceGeometry> candidateById = traceMap(candidate);
        if (!sourceById.keySet().equals(candidateById.keySet())) return false;
        for (String sourceId : sourceById.keySet()) {
            PcbTraceGeometry first = sourceById.get(sourceId);
            PcbTraceGeometry second = candidateById.get(sourceId);
            if (!sameTraceMetadata(first, second)) return false;
            int[] firstX = first.getXPoints(), firstY = first.getYPoints();
            int[] secondX = second.getXPoints(), secondY = second.getYPoints();
            if (firstX.length != secondX.length) return false;
            for (int index = 0; index < firstX.length; index++) {
                boolean endpoint = index == 0 || index == firstX.length - 1;
                if (!endpoint && (firstX[index] != secondX[index] ||
                        firstY[index] != secondY[index])) return false;
            }
            if (!verifyEndpoint(first.getStartPadId(), firstX[0], firstY[0],
                    secondX[0], secondY[0], movedPadIds, sourcePadCenters,
                    candidatePadCenters)) return false;
            int last = firstX.length - 1;
            if (!verifyEndpoint(first.getEndPadId(), firstX[last], firstY[last],
                    secondX[last], secondY[last], movedPadIds, sourcePadCenters,
                    candidatePadCenters)) return false;
        }
        return true;
    }

    private static boolean verifyEndpoint(String padId, int sourceX, int sourceY,
            int candidateX, int candidateY, Set<String> movedPadIds,
            Map<String,Point> sourcePadCenters, Map<String,Point> candidatePadCenters) {
        if (padId == null) return sourceX == candidateX && sourceY == candidateY;
        Point source = sourcePadCenters.get(padId);
        Point candidate = candidatePadCenters.get(padId);
        if (source == null || candidate == null || source.x != sourceX || source.y != sourceY)
            return false;
        if (movedPadIds.contains(padId))
            return candidate.x == candidateX && candidate.y == candidateY;
        return sourceX == candidateX && sourceY == candidateY;
    }

    private static TreeMap<String,PcbTraceGeometry> traceMap(
            Vector<PcbTraceGeometry> traces) {
        TreeMap<String,PcbTraceGeometry> result = new TreeMap<String,PcbTraceGeometry>();
        for (PcbTraceGeometry trace : traces) {
            GenerationWorkScope.check();
            if (trace.getSourceId() == null || result.put(trace.getSourceId(), trace) != null)
                throw new IllegalStateException("Trace source IDs are unique and non-null");
        }
        return result;
    }

    private static boolean sameTraceMetadata(PcbTraceGeometry first,
            PcbTraceGeometry second) {
        return equal(first.getSourceId(), second.getSourceId()) &&
            equal(first.getNetId(), second.getNetId()) &&
            equal(first.getStartPadId(), second.getStartPadId()) &&
            equal(first.getEndPadId(), second.getEndPadId()) &&
            first.getLayer() == second.getLayer() &&
            first.getExposure() == second.getExposure() &&
            first.getWidth() == second.getWidth();
    }

    private static boolean verifyUnrelatedGeometry(TroubleshootBoard board,
            PcbBoardLayout source, PcbBoardLayout candidate,
            Map<String,PcbFootprint> sourceFootprints,
            Map<String,PcbFootprint> candidateFootprints, String componentId,
            Set<String> movedPadIds, int dx, int dy) {
        if (source.getWidth() != candidate.getWidth() || source.getHeight() != candidate.getHeight() ||
                !sameRect(source.getBoardOutline(), candidate.getBoardOutline()) ||
                !sameRect(source.getPartsTray(), candidate.getPartsTray()) ||
                source.getLayoutAlgorithmVersion() != candidate.getLayoutAlgorithmVersion())
            return false;
        Vector<String> ids = new Vector<String>(board.getComponentIds());
        Collections.sort(ids);
        for (String id : ids) {
            GenerationWorkScope.check();
            PcbFootprint first = sourceFootprints.get(id);
            PcbFootprint second = candidateFootprints.get(id);
            if (first == null || second == null) return false;
            if (id.equals(componentId)) {
                PcbComponentPlacement placement = first.getPlacement();
                PcbFootprint expected = first.translated(placement.getX() + dx,
                    placement.getY() + dy);
                if (!expected.geometryFingerprint().equals(second.geometryFingerprint()))
                    return false;
            } else if (!first.geometryFingerprint().equals(second.geometryFingerprint())) {
                return false;
            }
        }
        if (!verifyPads(source, candidate, movedPadIds, dx, dy)) return false;
        if (!verifyLabels(source, candidate, componentId, movedPadIds, dx, dy) ||
                !verifyRegions(source, candidate) || !verifyHoles(source, candidate))
            return false;
        return true;
    }

    private static boolean verifyPads(PcbBoardLayout source, PcbBoardLayout candidate,
            Set<String> movedPadIds, int dx, int dy) {
        TreeMap<String,PcbPadPlacement> first = padsById(source);
        TreeMap<String,PcbPadPlacement> second = padsById(candidate);
        if (!first.keySet().equals(second.keySet())) return false;
        for (String padId : first.keySet()) {
            GenerationWorkScope.check();
            PcbPadPlacement a = first.get(padId), b = second.get(padId);
            if (!equal(a.getPadId(), b.getPadId()) ||
                    a.getEscapeDx() != b.getEscapeDx() ||
                    a.getEscapeDy() != b.getEscapeDy() ||
                    a.getEscapeLength() != b.getEscapeLength() ||
                    a.getAttachment() != b.getAttachment() ||
                    a.getMountingSide() != b.getMountingSide() ||
                    a.getExposure() != b.getExposure()) return false;
            if (movedPadIds.contains(padId)) {
                if (b.getX() != PcbCoordinateSystem.checkedAddBoardCoordinate(a.getX(), dx) ||
                        b.getY() != PcbCoordinateSystem.checkedAddBoardCoordinate(a.getY(), dy) ||
                        !sameRectTranslated(a.getPadBounds(), b.getPadBounds(), dx, dy) ||
                        !sameRectTranslated(a.getProbeBounds(), b.getProbeBounds(), dx, dy))
                    return false;
            } else if (!a.geometryFingerprint().equals(b.geometryFingerprint())) {
                return false;
            }
        }
        return true;
    }

    private static TreeMap<String,PcbPadPlacement> padsById(PcbBoardLayout layout) {
        TreeMap<String,PcbPadPlacement> result = new TreeMap<String,PcbPadPlacement>();
        for (PcbPadPlacement pad : layout.getPads()) {
            GenerationWorkScope.check();
            if (result.put(pad.getPadId(), pad) != null)
                throw new IllegalStateException("Duplicate pad identity");
        }
        return result;
    }

    private static boolean verifyLabels(PcbBoardLayout source, PcbBoardLayout candidate,
            String componentId, Set<String> movedPadIds, int dx, int dy) {
        TreeMap<String,PcbSilkscreenLabel> first = labelsById(source);
        TreeMap<String,PcbSilkscreenLabel> second = labelsById(candidate);
        if (!first.keySet().equals(second.keySet())) return false;
        for (String id : first.keySet()) {
            GenerationWorkScope.check();
            PcbSilkscreenLabel a = first.get(id), b = second.get(id);
            boolean moved = ("component:" + componentId).equals(id) ||
                targetsMovedPad(movedPadIds, a.getTargetPadId());
            if (!equal(a.getId(), b.getId()) || !equal(a.getText(), b.getText()) ||
                    a.getFontSize() != b.getFontSize() || a.isBold() != b.isBold() ||
                    !equal(a.getTargetPadId(), b.getTargetPadId())) return false;
            if (moved) {
                if (!sameRectTranslated(a.getBounds(), b.getBounds(), dx, dy)) return false;
            } else if (!sameRect(a.getBounds(), b.getBounds())) return false;
        }
        return true;
    }

    private static TreeMap<String,PcbSilkscreenLabel> labelsById(PcbBoardLayout layout) {
        TreeMap<String,PcbSilkscreenLabel> result = new TreeMap<String,PcbSilkscreenLabel>();
        for (PcbSilkscreenLabel label : layout.getSilkscreenLabels())
            if (result.put(label.getId(), label) != null)
                throw new IllegalStateException("Duplicate label identity");
        return result;
    }

    private static boolean verifyRegions(PcbBoardLayout source, PcbBoardLayout candidate) {
        Vector<PcbLayoutRegion> first = source.getRegions();
        Vector<PcbLayoutRegion> second = candidate.getRegions();
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            GenerationWorkScope.check();
            PcbLayoutRegion a = first.get(i), b = second.get(i);
            if (!equal(a.id, b.id) || !equal(a.label, b.label) ||
                    !a.getComponentIds().equals(b.getComponentIds())) return false;
        }
        return true;
    }

    private static boolean verifyHoles(PcbBoardLayout source, PcbBoardLayout candidate) {
        Vector<PcbBoardHole> first = source.getHoles();
        Vector<PcbBoardHole> second = candidate.getHoles();
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            GenerationWorkScope.check();
            if (!first.get(i).fingerprint().equals(second.get(i).fingerprint())) return false;
        }
        return true;
    }

    private static PcbSilkscreenLabel copyLabel(PcbSilkscreenLabel source, String componentId,
            Set<String> movedPadIds, int dx, int dy) {
        boolean moved = ("component:" + componentId).equals(source.getId()) ||
            targetsMovedPad(movedPadIds, source.getTargetPadId());
        if (!moved) return source;
        Rectangle bounds = source.getBounds();
        Rectangle translated = new Rectangle(
            PcbCoordinateSystem.checkedAddBoardCoordinate(bounds.x, dx),
            PcbCoordinateSystem.checkedAddBoardCoordinate(bounds.y, dy),
            bounds.width, bounds.height);
        return new PcbSilkscreenLabel(source.getId(), source.getText(), translated,
            source.getFontSize(), source.isBold(), source.getTargetPadId());
    }

    private static boolean targetsMovedPad(Set<String> movedPadIds, String targetPadId) {
        return targetPadId != null && movedPadIds.contains(targetPadId);
    }

    private static Set<String> traceGraphSources(PcbConductorGraph graph) {
        TreeSet<String> result = new TreeSet<String>();
        for (PcbConductorGraph.Edge edge : graph.getEdges().values()) {
            GenerationWorkScope.check();
            if (edge.kind == PcbConductorGraph.Kind.TRACE)
                result.addAll(edge.getSources());
        }
        return result;
    }

    private static String traceProvenanceHash(Vector<PcbTraceGeometry> traces) {
        TreeMap<String,PcbTraceGeometry> byId = traceMap(traces);
        StringBuilder value = new StringBuilder("TRACE_PROVENANCE/1\n");
        for (Map.Entry<String,PcbTraceGeometry> entry : byId.entrySet()) {
            GenerationWorkScope.check();
            PcbTraceGeometry trace = entry.getValue();
            value.append(PcbConductorGraph.field(trace.getSourceId()))
                .append(PcbConductorGraph.field(trace.getNetId()))
                .append(PcbConductorGraph.field(trace.getStartPadId()))
                .append(PcbConductorGraph.field(trace.getEndPadId()))
                .append(PcbConductorGraph.field(trace.getLayer().name()))
                .append(PcbConductorGraph.field(trace.getExposure().name()))
                .append(trace.getWidth()).append('\n');
        }
        String hash = PlayerSessionFingerprint.of(value.toString());
        requireHash(hash);
        return hash;
    }

    private static final class ReachabilityEvidence {
        final int pairCount;
        final String sourceHash, candidateHash;
        ReachabilityEvidence(int pairCount, String sourceHash, String candidateHash) {
            this.pairCount = pairCount;
            this.sourceHash = sourceHash;
            this.candidateHash = candidateHash;
        }
    }

    private static final class ProofFacts {
        final boolean placementValidated = true;
        final boolean geometryValidated = true;
        final boolean twoLayerValidated = true;
        final int padPairCount;
        final String sourceReachabilityHash, candidateReachabilityHash;
        final int sourceRouteSourceCount, candidateRouteSourceCount;
        final String sourceTraceProvenanceHash, candidateTraceProvenanceHash;
        final boolean traceEndpointOnlyVerified = true;
        final boolean unrelatedGeometryVerified = true;
        final boolean conductorReachabilityVerified = true;
        final boolean traceProvenanceVerified = true;
        ProofFacts(ReachabilityEvidence reachability, int sourceRouteSourceCount,
                int candidateRouteSourceCount, String sourceTraceProvenanceHash,
                String candidateTraceProvenanceHash) {
            this.padPairCount = reachability.pairCount;
            this.sourceReachabilityHash = reachability.sourceHash;
            this.candidateReachabilityHash = reachability.candidateHash;
            this.sourceRouteSourceCount = sourceRouteSourceCount;
            this.candidateRouteSourceCount = candidateRouteSourceCount;
            this.sourceTraceProvenanceHash = sourceTraceProvenanceHash;
            this.candidateTraceProvenanceHash = candidateTraceProvenanceHash;
        }
    }

    private static void appendProof(StringBuilder json, ProofFacts proof) {
        json.append('{');
        json.append("\"placementValidated\":").append(proof.placementValidated);
        json.append(",\"geometryValidated\":").append(proof.geometryValidated);
        json.append(",\"twoLayerValidated\":").append(proof.twoLayerValidated);
        json.append(",\"padPairCount\":").append(proof.padPairCount);
        json.append(",\"sourceReachabilityHash\":");
        stringField(json, proof.sourceReachabilityHash);
        json.append(",\"candidateReachabilityHash\":");
        stringField(json, proof.candidateReachabilityHash);
        json.append(",\"sourceRouteSourceCount\":").append(proof.sourceRouteSourceCount);
        json.append(",\"candidateRouteSourceCount\":").append(proof.candidateRouteSourceCount);
        json.append(",\"sourceTraceProvenanceHash\":");
        stringField(json, proof.sourceTraceProvenanceHash);
        json.append(",\"candidateTraceProvenanceHash\":");
        stringField(json, proof.candidateTraceProvenanceHash);
        json.append(",\"traceEndpointOnlyVerified\":").append(proof.traceEndpointOnlyVerified);
        json.append(",\"unrelatedGeometryVerified\":").append(proof.unrelatedGeometryVerified);
        json.append(",\"conductorReachabilityVerified\":").append(proof.conductorReachabilityVerified);
        json.append(",\"traceProvenanceVerified\":").append(proof.traceProvenanceVerified);
        json.append('}');
    }

    private static long copperLength(Vector<PcbTraceGeometry> traces) {
        long result = 0L;
        for (PcbTraceGeometry trace : traces) {
            int[] x = trace.getXPoints(), y = trace.getYPoints();
            for (int index = 1; index < x.length; index++)
                result += Math.abs((long)x[index] - x[index - 1]) +
                    Math.abs((long)y[index] - y[index - 1]);
        }
        return result;
    }

    private static long copperLength(PcbTraceGeometry trace) {
        Vector<PcbTraceGeometry> values = new Vector<PcbTraceGeometry>();
        values.add(trace);
        return copperLength(values);
    }

    private static long area(PcbBoardLayout layout) {
        Rectangle outline = layout.getBoardOutline();
        return (long)outline.width * outline.height;
    }

    private static boolean isTargetPackage(String id) {
        for (String target : TARGET_PACKAGES) if (target.equals(id)) return true;
        return false;
    }

    private static boolean sameRect(Rectangle first, Rectangle second) {
        return first.x == second.x && first.y == second.y &&
            first.width == second.width && first.height == second.height;
    }

    private static boolean sameRectTranslated(Rectangle first, Rectangle second,
            int dx, int dy) {
        return second.x == PcbCoordinateSystem.checkedAddBoardCoordinate(first.x, dx) &&
            second.y == PcbCoordinateSystem.checkedAddBoardCoordinate(first.y, dy) &&
            second.width == first.width && second.height == first.height;
    }

    private static boolean equal(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
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

    private interface FailureAction { void run(); }

    private static void expectFailure(FailureAction action, Class<?> expectedType,
            String expectedMessagePrefix, String message) {
        Throwable failure = null;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            failure = expected;
        } catch (IllegalStateException expected) {
            failure = expected;
        }
        boolean matched = failure != null && expectedType.isInstance(failure) &&
            failure.getMessage() != null &&
            failure.getMessage().startsWith(expectedMessagePrefix);
        check(matched, message + " type/message");
    }

    private static void endpointStartEndMetadataAndDelta() {
        PcbTraceGeometry start = new PcbTraceGeometry("trace/fixture-start", "N1",
            "R1.1", "J1.1", PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
            new int[] {100, 80, 60}, new int[] {100, 100, 100}, PcbTraceRules.TRACE_WIDTH);
        PcbTraceGeometry end = new PcbTraceGeometry("trace/fixture-end", "N2",
            "J2.1", "R1.2", PcbCopperLayer.BOTTOM, PcbCopperAccess.Exposure.EXPOSED,
            new int[] {260, 260, 200}, new int[] {160, 200, 200}, PcbTraceRules.TRACE_WIDTH);
        Map<String,Point> sourcePads = new HashMap<String,Point>();
        sourcePads.put("R1.1", new Point(100, 100));
        sourcePads.put("J1.1", new Point(60, 100));
        sourcePads.put("J2.1", new Point(260, 160));
        sourcePads.put("R1.2", new Point(200, 200));
        Map<String,Point> candidatePads = new HashMap<String,Point>();
        candidatePads.put("R1.1", new Point(110, 100));
        candidatePads.put("J1.1", new Point(60, 100));
        candidatePads.put("J2.1", new Point(260, 160));
        candidatePads.put("R1.2", new Point(210, 200));
        TreeSet<String> movedPadIds = new TreeSet<String>();
        movedPadIds.add("R1.1"); movedPadIds.add("R1.2");
        Vector<PcbTraceGeometry> sourceTraces = new Vector<PcbTraceGeometry>();
        sourceTraces.add(start); sourceTraces.add(end);
        TraceRewrite rewrite = rewriteTraces(sourceTraces, movedPadIds,
            sourcePads, candidatePads);
        PcbTraceGeometry movedStart = rewrite.traces.get(0);
        PcbTraceGeometry movedEnd = rewrite.traces.get(1);
        check(rewrite.affectedTraceCount == 2 && rewrite.movedPadCount == 2 &&
                rewrite.coveredPadIds.equals(movedPadIds),
            "rewrite covers both moved endpoint pads and both incident traces");
        check("R1.1".equals(movedStart.getStartPadId()) &&
                "J1.1".equals(movedStart.getEndPadId()) &&
                movedStart.getLayer() == PcbCopperLayer.TOP &&
                movedStart.getExposure() == PcbCopperAccess.Exposure.EXPOSED &&
                movedStart.getWidth() == PcbTraceRules.TRACE_WIDTH,
            "start endpoint rewrite preserves stable route metadata");
        check("J2.1".equals(movedEnd.getStartPadId()) &&
                "R1.2".equals(movedEnd.getEndPadId()) &&
                movedEnd.getLayer() == PcbCopperLayer.BOTTOM &&
                movedEnd.getExposure() == PcbCopperAccess.Exposure.EXPOSED,
            "end endpoint rewrite preserves endpoint and layer metadata");
        check(movedStart.getXPoints()[0] == 110 && movedStart.getYPoints()[0] == 100 &&
                movedStart.getXPoints()[1] == 80 && movedStart.getYPoints()[1] == 100 &&
                movedStart.getXPoints()[2] == 60 && movedStart.getYPoints()[2] == 100 &&
                movedEnd.getXPoints()[1] == 260 && movedEnd.getYPoints()[1] == 200 &&
                movedEnd.getXPoints()[movedEnd.getXPoints().length - 1] == 210 &&
                movedEnd.getYPoints()[movedEnd.getYPoints().length - 1] == 200,
            "rewrite moves only explicit first and last vertices");
        check(verifyTraceEndpointOnly(sourceTraces, rewrite.traces, movedPadIds,
                sourcePads, candidatePads),
            "endpoint-only verifier accepts the rewritten start and end traces");
        long startDelta = copperLength(movedStart) - copperLength(start);
        long endDelta = copperLength(movedEnd) - copperLength(end);
        long analytic = startDelta + endDelta;
        check(analytic == 0L && startDelta == 10L && endDelta == -10L &&
                copperLength(start) == 40L && copperLength(end) == 100L,
            "literal start/end Manhattan deltas remain independently measurable");
        check(start.getXPoints()[0] == 100 && start.getXPoints()[1] == 80 &&
                end.getXPoints()[end.getXPoints().length - 1] == 200 &&
                end.getXPoints()[1] == 260,
            "withPath leaves source trace coordinate arrays immutable");
        fixture("endpointStartEndMetadataAndDelta");
    }

    private static void rejectInvalidIncidenceOrEndpoint() {
        final Map<String,Point> source = new HashMap<String,Point>();
        final Map<String,Point> candidate = new HashMap<String,Point>();
        source.put("R1.1", new Point(100, 100));
        source.put("J1.1", new Point(60, 100));
        source.put("J2.1", new Point(260, 100));
        candidate.put("R1.1", new Point(110, 100));
        candidate.put("J1.1", new Point(60, 100));
        candidate.put("J2.1", new Point(260, 100));
        final TreeSet<String> moved = new TreeSet<String>(); moved.add("R1.1");
        PcbTraceGeometry valid = new PcbTraceGeometry("trace/valid-incidence", "N1",
            "R1.1", "J1.1", PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
            new int[] {100, 60}, new int[] {100, 100}, PcbTraceRules.TRACE_WIDTH);
        Vector<PcbTraceGeometry> one = new Vector<PcbTraceGeometry>(); one.add(valid);
        TraceRewrite rewritten = rewriteTraces(one, moved, source, candidate);
        check(rewritten.affectedTraceCount == 1 && rewritten.movedPadCount == 1 &&
                rewritten.traces.get(0).getXPoints()[0] == 110,
            "valid explicit incidence rewrites exactly one endpoint");
        PcbTraceGeometry mismatch = new PcbTraceGeometry("trace/mismatch", "N1",
            "R1.1", "J1.1", PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
            new int[] {99, 60}, new int[] {100, 100}, PcbTraceRules.TRACE_WIDTH);
        final Vector<PcbTraceGeometry> mismatchRows = new Vector<PcbTraceGeometry>();
        mismatchRows.add(mismatch);
        expectFailure(new FailureAction() {
            public void run() { rewriteTraces(mismatchRows, moved, source, candidate); }
        }, IllegalStateException.class,
            "Trace start endpoint disagrees with source pad: R1.1",
            "source endpoint coordinate mismatch is rejected");
        PcbTraceGeometry nonincident = new PcbTraceGeometry("trace/nonincident", "N2",
            "J1.1", "J2.1", PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
            new int[] {60, 260}, new int[] {100, 100}, PcbTraceRules.TRACE_WIDTH);
        final Vector<PcbTraceGeometry> nonincidentRows = new Vector<PcbTraceGeometry>();
        nonincidentRows.add(nonincident);
        expectFailure(new FailureAction() {
            public void run() { rewriteTraces(nonincidentRows, moved, source, candidate); }
        }, IllegalStateException.class,
            "Moved pads lack complete explicit endpoint coverage",
            "moved pad without explicit trace endpoint is rejected");
        PcbTraceGeometry duplicate = new PcbTraceGeometry("trace/valid-incidence", "N1",
            "R1.1", "J1.1", PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
            new int[] {100, 60}, new int[] {100, 100}, PcbTraceRules.TRACE_WIDTH);
        final Vector<PcbTraceGeometry> duplicateRows = new Vector<PcbTraceGeometry>();
        duplicateRows.add(valid); duplicateRows.add(duplicate);
        expectFailure(new FailureAction() {
            public void run() { rewriteTraces(duplicateRows, moved, source, candidate); }
        }, IllegalStateException.class,
            "Duplicate routed trace source identity: trace/valid-incidence",
            "duplicate route source identity is rejected");
        fixture("rejectInvalidIncidenceOrEndpoint");
    }

    private static void footprintPadsUnrelatedGeometryAndSourceImmutability() {
        TroubleshootBoard board = new TroubleshootBoard("MOVE_FOOTPRINT_FIXTURE");
        board.addNet(new BoardNet("N1")); board.addNet(new BoardNet("N2"));
        board.addNet(new BoardNet("N3")); board.addNet(new BoardNet("N4"));
        BoardComponent first = new BoardComponent("R1", "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR);
        BoardComponent second = new BoardComponent("R2", "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR);
        board.addComponent(first); board.addComponent(second);
        board.addPad(new BoardPad("R1.1", "R1", "1", "N1"));
        board.addPad(new BoardPad("R1.2", "R1", "2", "N2"));
        board.addPad(new BoardPad("R2.1", "R2", "1", "N3"));
        board.addPad(new BoardPad("R2.2", "R2", "2", "N4"));
        board.validate();
        PcbFootprint source = PcbFootprint.fromPhysicalPackage(first, 300, 200);
        PcbFootprint unrelated = PcbFootprint.fromPhysicalPackage(second, 700, 200);
        PcbFootprint rebuilt = new PcbFootprint(source.getPlacement(), source.getPads());
        String sourceHash = rebuilt.geometryFingerprint();
        String unrelatedHash = unrelated.geometryFingerprint();
        PcbFootprint moved = rebuilt.translated(320, 180);
        check(sourceHash.equals(rebuilt.geometryFingerprint()),
            "reconstructing and translating does not mutate source footprint");
        check(unrelatedHash.equals(unrelated.geometryFingerprint()),
            "unrelated footprint remains byte-for-byte geometrically stable");
        check(moved.getPlacement().getX() == 320 && moved.getPlacement().getY() == 180 &&
                moved.getPlacement().getWidth() == rebuilt.getPlacement().getWidth() &&
                moved.getPlacement().getHeight() == rebuilt.getPlacement().getHeight(),
            "translated footprint takes a new absolute origin and preserves package size");
        for (PcbPadPlacement before : rebuilt.getPads()) {
            PcbPadPlacement after = moved.getPad(before.getPadId());
            check(after.getX() == before.getX() + 20 && after.getY() == before.getY() - 20 &&
                    after.getEscapeDx() == before.getEscapeDx() &&
                    after.getEscapeDy() == before.getEscapeDy() &&
                    after.getEscapeLength() == before.getEscapeLength() &&
                    after.getAttachment() == before.getAttachment() &&
                    after.getMountingSide() == before.getMountingSide() &&
                    after.getExposure() == before.getExposure() &&
                    sameRectTranslated(before.getPadBounds(), after.getPadBounds(), 20, -20) &&
                    sameRectTranslated(before.getProbeBounds(), after.getProbeBounds(), 20, -20),
                "translated target pad preserves identity and rigidly shifts geometry: " +
                    before.getPadId());
        }
        check(rebuilt.getPads().size() == first.getPadIds().size() &&
                rebuilt.getPads().get(0).getPadId().equals(first.getPadIds().get(0)) &&
                rebuilt.getPads().get(1).getPadId().equals(first.getPadIds().get(1)),
            "footprint reconstruction follows authoritative component pad order");
        int dx = 20, dy = -20;
        TreeSet<String> movedPadIds = new TreeSet<String>();
        movedPadIds.add("R1.1"); movedPadIds.add("R1.2");
        PcbSilkscreenLabel sourceAssociated = new PcbSilkscreenLabel("net:R1.1", "R1.1",
            new Rectangle(285, 190, 25, 10), 10, false, "R1.1");
        PcbSilkscreenLabel sourceUnrelated = new PcbSilkscreenLabel("net:R2.1", "R2.1",
            new Rectangle(685, 190, 25, 10), 10, false, "R2.1");
        PcbSilkscreenLabel sourceMovedComponent = new PcbSilkscreenLabel(
            "component:R1", "R1", new Rectangle(280, 150, 40, 10), 10, false, null);
        PcbSilkscreenLabel sourceUnrelatedComponent = new PcbSilkscreenLabel(
            "component:R2", "R2", new Rectangle(680, 150, 40, 10), 10, false, null);
        PcbSilkscreenLabel sourceGeneric = new PcbSilkscreenLabel(
            "board:note", "PCB", new Rectangle(500, 80, 50, 10), 10, false, null);
        PcbSilkscreenLabel candidateAssociated = copyLabel(sourceAssociated, "R1",
            movedPadIds, dx, dy);
        PcbSilkscreenLabel candidateUnrelated = copyLabel(sourceUnrelated, "R1",
            movedPadIds, dx, dy);
        PcbSilkscreenLabel candidateMovedComponent = copyLabel(sourceMovedComponent, "R1",
            movedPadIds, dx, dy);
        PcbSilkscreenLabel candidateUnrelatedComponent = copyLabel(
            sourceUnrelatedComponent, "R1", movedPadIds, dx, dy);
        PcbSilkscreenLabel candidateGeneric = copyLabel(sourceGeneric, "R1",
            movedPadIds, dx, dy);
        Vector<PcbSilkscreenLabel> sourceLabels = new Vector<PcbSilkscreenLabel>();
        sourceLabels.add(sourceAssociated); sourceLabels.add(sourceUnrelated);
        sourceLabels.add(sourceMovedComponent); sourceLabels.add(sourceUnrelatedComponent);
        sourceLabels.add(sourceGeneric);
        Vector<PcbSilkscreenLabel> candidateLabels = new Vector<PcbSilkscreenLabel>();
        candidateLabels.add(candidateAssociated); candidateLabels.add(candidateUnrelated);
        candidateLabels.add(candidateMovedComponent);
        candidateLabels.add(candidateUnrelatedComponent); candidateLabels.add(candidateGeneric);
        PcbBoardLayout sourceLayout = moveFixtureLayout(source, unrelated, sourceLabels);
        PcbBoardLayout candidateLayout = moveFixtureLayout(moved, unrelated, candidateLabels);
        TreeMap<String,PcbFootprint> sourceFootprints = new TreeMap<String,PcbFootprint>();
        sourceFootprints.put("R1", rebuilt); sourceFootprints.put("R2", unrelated);
        TreeMap<String,PcbFootprint> candidateFootprints =
            new TreeMap<String,PcbFootprint>();
        candidateFootprints.put("R1", moved); candidateFootprints.put("R2", unrelated);
        check(sourceMovedComponent.getTargetPadId() == null &&
                sourceUnrelatedComponent.getTargetPadId() == null &&
                sourceGeneric.getTargetPadId() == null &&
                candidateMovedComponent.getTargetPadId() == null &&
                sameRectTranslated(sourceMovedComponent.getBounds(),
                    candidateMovedComponent.getBounds(), dx, dy) &&
                candidateUnrelatedComponent.getTargetPadId() == null &&
                sameRect(sourceUnrelatedComponent.getBounds(),
                    candidateUnrelatedComponent.getBounds()) &&
                candidateGeneric.getTargetPadId() == null &&
                sameRect(sourceGeneric.getBounds(), candidateGeneric.getBounds()) &&
                "R1.1".equals(candidateAssociated.getTargetPadId()) &&
                sameRectTranslated(sourceAssociated.getBounds(),
                    candidateAssociated.getBounds(), dx, dy) &&
                "R2.1".equals(candidateUnrelated.getTargetPadId()) &&
                sameRect(sourceUnrelated.getBounds(), candidateUnrelated.getBounds()),
            "literal null-target, component, generic and pad-target labels preserve association");
        check(verifyUnrelatedGeometry(board, sourceLayout, candidateLayout,
                sourceFootprints, candidateFootprints, "R1", movedPadIds, dx, dy),
            "actual candidate layout preserves unrelated geometry and shifts associated label");
        PcbFootprint corruptedUnrelated = unrelated.translated(720, 180);
        PcbBoardLayout corruptedLayout = moveFixtureLayout(moved, corruptedUnrelated,
            candidateLabels);
        TreeMap<String,PcbFootprint> corruptedFootprints =
            new TreeMap<String,PcbFootprint>();
        corruptedFootprints.put("R1", moved);
        corruptedFootprints.put("R2", corruptedUnrelated);
        check(!verifyUnrelatedGeometry(board, sourceLayout, corruptedLayout,
                sourceFootprints, corruptedFootprints, "R1", movedPadIds, dx, dy),
            "intentional unrelated footprint corruption is rejected");
        fixture("footprintPadsUnrelatedGeometryAndSourceImmutability");
    }

    private static PcbBoardLayout moveFixtureLayout(PcbFootprint first,
            PcbFootprint second, Vector<PcbSilkscreenLabel> labels) {
        PcbBoardLayout layout = new PcbBoardLayout(1200, 600,
            new Rectangle(0, 0, 1200, 600), new Rectangle(0, 0, 1200, 600),
            SeededPcbLayoutGenerator.CURRENT_VERSION);
        addFixtureFootprint(layout, first);
        addFixtureFootprint(layout, second);
        for (PcbSilkscreenLabel label : labels) layout.addSilkscreenLabel(label);
        return layout;
    }

    private static void addFixtureFootprint(PcbBoardLayout layout,
            PcbFootprint footprint) {
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
    }

    private static void observerOwnershipNullAndFailureCleanup() {
        PcbRoutedPassiveLegCensusContractTest.FeasibilityObserver original =
            PcbRoutedPassiveLegCensusContractTest.setFeasibilityObserver(null);
        final int[] calls = {0};
        PcbRoutedPassiveLegCensusContractTest.FeasibilityObserver sentinel =
            new PcbRoutedPassiveLegCensusContractTest.FeasibilityObserver() {
                public void onCandidates(TroubleshootBoard board, PcbBoardLayout layout,
                        Vector<PcbRoutedPassiveLegCensusContractTest.MoveIntent> moves) {
                    calls[0]++;
                    throw new AssertionError("failure fixture observer must not be called");
                }
            };
        PcbRoutedPassiveLegCensusContractTest.FeasibilityObserver displaced =
            PcbRoutedPassiveLegCensusContractTest.setFeasibilityObserver(sentinel);
        check(displaced == null &&
                PcbRoutedPassiveLegCensusContractTest.setFeasibilityObserver(sentinel) == sentinel,
            "observer setter returns and retains its current owner");
        try {
            PcbRoutedPassiveLegCensusContractTest.setFeasibilityObserver(null);
            PcbRoutedPassiveLegCensusContractTest.dispatchFeasibilityObserver(
                null, null, null);
            check(PcbRoutedPassiveLegCensusContractTest.setFeasibilityObserver(sentinel) == null &&
                    calls[0] == 0,
                "null observer short-circuits dispatch without inspecting null inputs");
            boolean failed = false;
            try {
                observe(987654321L, "invalid-root-cleanup");
            } catch (AssertionError expected) {
                failed = expected.getMessage() != null &&
                    expected.getMessage().startsWith("fixed routed-leg census root");
            }
            check(failed, "fixed-root census rejection propagates from observe");
            displaced = PcbRoutedPassiveLegCensusContractTest.setFeasibilityObserver(null);
            check(displaced == sentinel && calls[0] == 0,
                "observe finally restores prior observer and null clears inspection");
        } finally {
            PcbRoutedPassiveLegCensusContractTest.setFeasibilityObserver(original);
        }
        fixture("observerOwnershipNullAndFailureCleanup");
    }

    private static void courtyardRegression() {
        CourtyardFixture valid = new CourtyardFixture(9, 24, 25);
        valid.layout.validateRoutingGeometry(valid.board);
        check(true, "literal package and trace geometry clears the courtyard boundary");
        final CourtyardFixture rejected = new CourtyardFixture(9, 23, 25);
        boolean caught = false;
        try {
            rejected.layout.validateRoutingGeometry(rejected.board);
        } catch (IllegalStateException expected) {
            caught = expected.getMessage() != null &&
                expected.getMessage().startsWith(
                    "PCB trace passes through component routing courtyard: ESCAPE / FORWARD segment 220,130 -> 400,130");
            check(caught, "actual courtyard validator keeps its typed message and segment");
            StageReason classified = classifyGeometryOrThrow(expected);
            check("GEOMETRY".equals(classified.stage) &&
                    "TRACE_COMPONENT_COURTYARD".equals(classified.reason),
                "actual courtyard failure classifies at the geometry stage");
        }
        check(caught, "one-unit courtyard escape regression is rejected");

        LiteralCourtyardFixture literal = new LiteralCourtyardFixture();
        literal.layout.validateRoutingGeometry(literal.board);
        check(literal.layout.getPad("C5.1").getX() == 3186 &&
                literal.layout.getPad("C5.1").getY() == 1134 &&
                literal.layout.getPad("C5.2").getX() == 3226 &&
                literal.layout.getPad("C5.2").getY() == 1134 &&
                literal.layout.getComponent("C5").getRoutingCourtyard().equals(
                    new Rectangle(3171, 1104, 80, 90)),
            "literal C5 package has the frozen source pad and courtyard coordinates");
        final PcbBoardLayout moved = literal.layoutAtC5Y(1094);
        boolean literalRejected = false;
        try {
            moved.validateRoutingGeometry(literal.board);
        } catch (IllegalStateException expected) {
            String expectedMessage =
                "PCB trace passes through component routing courtyard: CTRL_RETURN / C5 " +
                "segment 3256,1094 -> 3226,1094 keepOut=Rect(3171,1094,80,90) " +
                "startPad=U1.RETURN endPad=C5.2";
            literalRejected = expectedMessage.equals(expected.getMessage());
        }
        check(literalRejected,
            "actual C5 courtyard regression rejects the frozen moved route with its exact message");
        fixture("courtyardRegression");
    }

    private static void stageClassificationAndFailurePropagation() {
        check("ENVELOPE_ACCESS_OR_DOMAIN".equals(classifyPlacementOrThrow(
            new PcbPlacementPlanner.Rejected("ENVELOPE_ACCESS_OR_DOMAIN"))),
            "known placement rejection is classified by its exact reason");
        final PcbPlacementPlanner.Rejected budget =
            new PcbPlacementPlanner.Rejected("ACCESS_GRID_BUDGET");
        boolean budgetPropagated = false;
        try {
            classifyPlacementOrThrow(budget);
        } catch (PcbPlacementPlanner.Rejected expected) {
            budgetPropagated = expected == budget;
        }
        check(budgetPropagated, "access-grid budget is not converted to a rejection row");

        PcbTraceGeometry qualityTrace = new PcbTraceGeometry("fixture-quality", "N",
            null, null, PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
            new int[] {10, 20}, new int[] {10, 10}, PcbTraceRules.TRACE_WIDTH);
        StageReason quality = classifyGeometryOrThrow(
            PcbBoardLayout.RouteQualityRejectedException.excessiveBends(qualityTrace, 17));
        check("GEOMETRY".equals(quality.stage) &&
                "ROUTE_QUALITY_BENDS".equals(quality.reason),
            "typed route-quality rejection remains a geometry classification");
        StageReason layer = classifyTwoLayerOrThrow(new IllegalStateException(
            "P07 via drills through a package courtyard"));
        check("TWO_LAYER".equals(layer.stage) &&
                "VIA_PACKAGE_COURTYARD".equals(layer.reason),
            "two-layer exact rejection is classified only at its named stage");
        StageReason nestedLayer = classifyGeometryOrThrow(new IllegalStateException(
            "P07 via drills through a package courtyard"));
        check("GEOMETRY".equals(nestedLayer.stage) &&
                "VIA_PACKAGE_COURTYARD".equals(nestedLayer.reason),
            "nested two-layer failure remains a geometry-stage rejection");
        final IllegalStateException invariantDrillPair = new IllegalStateException(
            "P07 overlapping drill/land clearance");
        boolean drillPairPropagated = false;
        try {
            classifyTwoLayerOrThrow(invariantDrillPair);
        } catch (IllegalStateException expected) {
            drillPairPropagated = expected == invariantDrillPair;
        }
        check(drillPairPropagated,
            "unchanged drill-land clearance invariant is not a physical move rejection");

        final IllegalStateException unknown = new IllegalStateException("unknown physical error");
        boolean unknownPropagated = false;
        try {
            classifyGeometryOrThrow(unknown);
        } catch (IllegalStateException expected) {
            unknownPropagated = expected == unknown;
        }
        check(unknownPropagated, "unknown geometry errors propagate unchanged");
        final GenerationJob.Cancelled typedPhysicalFailure =
            new GenerationJob.Cancelled("PCB trace passes through component routing courtyard: typed");
        boolean typedPhysicalPropagated = false;
        try {
            classifyGeometryOrThrow(typedPhysicalFailure);
        } catch (GenerationJob.Cancelled expected) {
            typedPhysicalPropagated = expected == typedPhysicalFailure;
        }
        check(typedPhysicalPropagated,
            "typed checkpoint failures are not classified by a physical message prefix");

        CancellationServices services = new CancellationServices();
        GenerationJob job = new GenerationJob(services, 100000, 40, 5000);
        GenerationWorkScope.enter(job);
        GenerationJob.Cancelled observed = null;
        try {
            Vector<PcbTraceGeometry> trace = new Vector<PcbTraceGeometry>();
            trace.add(new PcbTraceGeometry("cancel/fixture", "CTRL_RETURN", "U1.RETURN",
                "C5.2", PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
                new int[] {10, 20}, new int[] {10, 10}, PcbTraceRules.TRACE_WIDTH));
            String provenance = traceProvenanceHash(trace);
            check(provenance != null && provenance.length() == 64,
                "cancellation fixture reaches the census provenance helper before cancel");
            job.cancel();
            try {
                traceProvenanceHash(trace);
                throw new AssertionError("cancelled census checkpoint unexpectedly returned");
            } catch (GenerationJob.Cancelled expected) {
                observed = expected;
            }
        } finally {
            GenerationWorkScope.exit(job);
        }
        GenerationWorkScope.check();
        check(observed != null && observed == job.getFailure() &&
                job.getOutcome() == GenerationJob.Outcome.CANCELLED &&
                services.currentChecks > 0 && services.aborts == 1,
            "actual generation cancellation preserves its typed failure and scope cleanup");
        fixture("stageClassificationAndFailurePropagation");
    }

    private static final class CancellationServices implements GenerationJob.Services {
        int currentChecks;
        int aborts;

        public int candidateCount() { return 1; }
        public String manifest(int candidate) { return "feasibility-cancel"; }
        public void beginCandidate(int index) { }
        public String resolve() { return "resolve"; }
        public String healthy() { return "healthy"; }
        public String physical() { return "physical"; }
        public boolean proveNext() { return false; }
        public String symptom() { return "symptom"; }
        public String dependencies() { return "dependencies"; }
        public void publish(GenerationReceipt receipt) { }
        public void abort() { aborts++; }
        public boolean isCurrent() { currentChecks++; return true; }
        public long nowMillis() { return 0L; }
    }

    private static final class LiteralCourtyardFixture {
        final TroubleshootBoard board = new TroubleshootBoard("FEASIBILITY_C5_COURTYARD_FIXTURE");
        final BoardComponent u1;
        final BoardComponent c5;
        final PcbBoardLayout layout;

        LiteralCourtyardFixture() {
            board.addNet(new BoardNet("CTRL_RETURN", BoardNet.RoutingRole.RETURN));
            board.addNet(new BoardNet("AUX", BoardNet.RoutingRole.SIGNAL));
            u1 = new BoardComponent("U1", "REGULATOR", literalU1Package());
            c5 = new BoardComponent("C5", "CAPACITOR",
                PhysicalPackages.RADIAL_CERAMIC_CAPACITOR);
            board.addComponent(u1);
            board.addComponent(c5);
            board.addPad(new BoardPad("U1.RETURN", "U1", "RETURN", "CTRL_RETURN"));
            board.addPad(new BoardPad("C5.1", "C5", "1", "AUX"));
            board.addPad(new BoardPad("C5.2", "C5", "2", "CTRL_RETURN"));
            Vector<PcbPlacementConstraints.Part> parts =
                new Vector<PcbPlacementConstraints.Part>();
            parts.add(new PcbPlacementConstraints.Part("U1", "literal", "Literal region",
                "literal-board", PcbPlacementConstraints.Anchor.NONE, 20));
            parts.add(new PcbPlacementConstraints.Part("C5", "literal", "Literal region",
                "literal-board", PcbPlacementConstraints.Anchor.NONE, 20));
            board.setPlacementConstraints(new PcbPlacementConstraints(parts,
                new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, false,
                PcbTraceWidthProfile.UNIFORM));
            layout = layoutAtC5Y(1104);
        }

        PcbBoardLayout layoutAtC5Y(int c5Y) {
            PcbBoardLayout result = new PcbBoardLayout(3400, 1400,
                new Rectangle(3000, 800, 400, 400), new Rectangle(20, 20, 200, 100));
            addFootprint(result, PcbFootprint.fromPhysicalPackage(u1, 3206, 1064));
            addFootprint(result, PcbFootprint.fromPhysicalPackage(c5, 3166, c5Y));
            result.addTrace(new PcbTraceGeometry("literal/CTRL_RETURN/U1.RETURN/C5.2",
                "CTRL_RETURN", "U1.RETURN", "C5.2", PcbCopperLayer.TOP,
                PcbCopperAccess.Exposure.EXPOSED,
                new int[] {3256, 3226, 3226}, new int[] {1094, 1094, c5Y + 30},
                PcbTraceRules.TRACE_WIDTH));
            return result;
        }

        private static void addFootprint(PcbBoardLayout layout, PcbFootprint footprint) {
            layout.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
        }
    }

    private static PhysicalPackage literalU1Package() {
        Point pad = new Point(50, 30);
        Point body = new Point(50, 10);
        PhysicalPackageGeometry.Lead connected = new PhysicalPackageGeometry.Lead(
            pad, body, new Rectangle(37, 7, 16, 26), body,
            new Rectangle(46, 6, 8, 8));
        PhysicalPackageGeometry.Lead lifted = new PhysicalPackageGeometry.Lead(
            new Point(45, 10), body, new Rectangle(42, 7, 11, 6), body,
            new Rectangle(46, 6, 8, 8));
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(new PhysicalPackageGeometry.Terminal("RETURN", pad,
            new Rectangle(46, 26, 8, 8), pad, new Rectangle(46, 26, 8, 8),
            connected, lifted, -1, 0, 35,
            PcbTerminalAttachment.PLATED_THROUGH_HOLE));
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(60, 40, terminals,
            new Rectangle(45, 5, 10, 15), new Rectangle(45, 5, 10, 15),
            new Rectangle(45, 0, 15, 35), new Rectangle(35, -5, 25, 45),
            new Rectangle(30, -10, 30, 55));
        Vector<PhysicalPackage.GeometryVariant> variants =
            new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT", "IDENTITY", geometry));
        return new PhysicalPackage("LITERAL_COURTYARD_U1", singleton("RETURN"),
            new Vector<String>(), false, geometry, variants, "DEFAULT",
            PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT);
    }

    private static final class CourtyardFixture {
        final TroubleshootBoard board = new TroubleshootBoard("FEASIBILITY_COURTYARD_FIXTURE");
        final PcbBoardLayout layout = new PcbBoardLayout(700, 320,
            new Rectangle(20, 20, 600, 230), new Rectangle(20, 270, 200, 30));

        CourtyardFixture(int width, int forwardReach, int reverseReach) {
            board.addNet(new BoardNet("ESCAPE", BoardNet.RoutingRole.SIGNAL));
            PhysicalPackage forwardPackage = courtyardPointPackage(
                "ESCAPE_FORWARD", false, forwardReach);
            PhysicalPackage reversePackage = courtyardPointPackage(
                "ESCAPE_REVERSE", true, reverseReach);
            BoardComponent forward = new BoardComponent("FORWARD", "point", forwardPackage);
            BoardComponent reverse = new BoardComponent("REVERSE", "point", reversePackage);
            board.addComponent(forward); board.addComponent(reverse);
            board.addPad(new BoardPad("FORWARD.1", "FORWARD", "1", "ESCAPE"));
            board.addPad(new BoardPad("REVERSE.1", "REVERSE", "1", "ESCAPE"));
            Vector<PcbPlacementConstraints.Part> parts =
                new Vector<PcbPlacementConstraints.Part>();
            parts.add(new PcbPlacementConstraints.Part("FORWARD", "route", "Route",
                "board", PcbPlacementConstraints.Anchor.NONE, 20));
            parts.add(new PcbPlacementConstraints.Part("REVERSE", "route", "Route",
                "board", PcbPlacementConstraints.Anchor.NONE, 20));
            board.setPlacementConstraints(new PcbPlacementConstraints(parts,
                new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, false,
                PcbTraceWidthProfile.UNIFORM));
            addFootprint(PcbFootprint.fromPhysicalPackage(forward, 100, 100,
                forwardPackage.getGeometry()));
            addFootprint(PcbFootprint.fromPhysicalPackage(reverse, 370, 100,
                reversePackage.getGeometry()));
            layout.addTrace(new PcbTraceGeometry("component-escape", "ESCAPE",
                "FORWARD.1", "REVERSE.1", PcbCopperLayer.TOP,
                PcbCopperAccess.Exposure.EXPOSED, new int[] {220, 400},
                new int[] {130, 130}, width));
        }

        private void addFootprint(PcbFootprint footprint) {
            layout.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
        }
    }

    private static PhysicalPackage courtyardPointPackage(String id, boolean mirrored,
            int escapeLength) {
        PhysicalPackageGeometry source = PhysicalPackageGeometry.generic(singleton("1"), false);
        if (mirrored) source = source.mirroredHorizontally();
        PhysicalPackageGeometry.Terminal sourceTerminal = source.getTerminal(0);
        PhysicalPackageGeometry.Terminal terminal = new PhysicalPackageGeometry.Terminal(
            sourceTerminal.getTerminalId(), sourceTerminal.getPadCenter(),
            sourceTerminal.getPadBounds(), sourceTerminal.getBoardPadProbeCenter(),
            sourceTerminal.getBoardPadProbeBounds(), sourceTerminal.getConnectedLead(),
            sourceTerminal.getLiftedLead(), sourceTerminal.getEscapeDx(),
            sourceTerminal.getEscapeDy(), escapeLength, sourceTerminal.getAttachment());
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal);
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(source.getWidth(),
            source.getHeight(), terminals, source.getBodyBounds(), source.getBodyKeepOut(),
            source.getRoutingCourtyard(), source.getSelectionEnvelope(),
            source.getDragEnvelope(), source.getGeometryContractVersion());
        Vector<PhysicalPackage.GeometryVariant> variants =
            new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT", "IDENTITY", geometry));
        return new PhysicalPackage(id, singleton("1"), new Vector<String>(), false, geometry,
            variants, "DEFAULT", PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT);
    }

    private static Vector<String> singleton(String value) {
        Vector<String> values = new Vector<String>();
        values.add(value);
        return values;
    }

    private static void stringField(StringBuilder json, String value) {
        if (value == null) { json.append("null"); return; }
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' || ch == '\\') json.append('\\').append(ch);
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void fixture(String name) {
        if (name == null || name.length() == 0)
            throw new AssertionError("Named fixture required");
        fixtures++;
    }
    // BEGIN COPY-ONLY selected routed slide proof adapter.
    /** Opaque source handle for the selected-slide probe; the source is never mutated. */
    static final class SelectedSlideSource {
        private final SourceContext value;
        private SelectedSlideSource(SourceContext value) { this.value = value; }
        TroubleshootBoard board() { return value.board; }
        PcbBoardLayout layout() { return value.source; }
        String sourceJson() {
            StringBuilder json = new StringBuilder(800);
            appendFeasibilitySource(json, value.facts);
            return json.toString();
        }
        String liveGraphBefore() { return value.facts.liveGraphBefore; }
        String geometryBefore() { return value.facts.geometryBefore; }
        String provenance() { return value.provenance; }
        String reachabilityHash() { return value.reachability.hash; }
        int padPairCount() { return value.reachability.pairCount; }
        int routeSourceCount() { return value.routeSources.size(); }
    }

    /** Opaque row handle; its feasibility JSON is the published row contract. */
    static final class SelectedSlideRow {
        private final FeasibilityRow value;
        private SelectedSlideRow(FeasibilityRow value) { this.value = value; }
        PcbBoardLayout candidate() { return value.candidate.candidate; }
        Vector<PcbFootprint> footprints() {
            return new Vector<PcbFootprint>(value.candidate.footprintVector);
        }
        String disposition() { return value.disposition; }
        String stage() { return value.stage; }
        String reason() { return value.reason; }
        String message() { return value.message; }
        String componentId() { return value.candidate.componentId; }
        int dx() { return value.candidate.dx; }
        int dy() { return value.candidate.dy; }
        long analyticCopperDelta() { return value.candidate.analyticCopperDelta; }
        long actualCopperDelta() { return value.candidate.actualCopperDelta; }
        String candidateJson() {
            StringBuilder json = new StringBuilder(700);
            appendFeasibilityCandidate(json, value.candidate.facts);
            return json.toString();
        }
        int affectedTraceCount() { return value.candidate.facts.affectedTraceCount; }
        int movedPadCount() { return value.candidate.facts.movedPadCount; }
        String geometryHash() { return value.candidate.facts.geometryHash; }
    }

    /** Reachability/provenance proof for one final private compacted layout. */
    static final class SelectedSlideProof {
        private final ProofFacts value;
        private SelectedSlideProof(ProofFacts value) { this.value = value; }
        int padPairCount() { return value.padPairCount; }
        String sourceReachabilityHash() { return value.sourceReachabilityHash; }
        String candidateReachabilityHash() { return value.candidateReachabilityHash; }
        int sourceRouteSourceCount() { return value.sourceRouteSourceCount; }
        int candidateRouteSourceCount() { return value.candidateRouteSourceCount; }
        String sourceTraceProvenanceHash() { return value.sourceTraceProvenanceHash; }
        String candidateTraceProvenanceHash() { return value.candidateTraceProvenanceHash; }
    }

    static SelectedSlideSource captureSelectedSlideSource(TroubleshootBoard board,
            PcbBoardLayout source) {
        return new SelectedSlideSource(captureSource(board, source));
    }

    static SelectedSlideRow evaluateSelectedSlide(SelectedSlideSource source,
            PcbRoutedPassiveLegCensusContractTest.MoveIntent move) {
        if (source == null) throw new IllegalArgumentException("Missing selected-slide source");
        return new SelectedSlideRow(evaluate(source.value, move));
    }

    static void appendSelectedSlideFeasibility(StringBuilder json, SelectedSlideRow row) {
        if (row == null) throw new IllegalArgumentException("Missing selected-slide row");
        appendFeasibilityRow(json, row.value);
    }

    static SelectedSlideProof verifySelectedSlideCompaction(SelectedSlideSource source,
            PcbBoardLayout candidate) {
        if (source == null || candidate == null)
            throw new IllegalArgumentException("Missing selected-slide proof input");
        GenerationWorkScope.check();
        requireKnownPlatedHoleNets(source.value.board, candidate);
        PcbConductorGraph graph = candidate.captureConductorGraph(source.value.board);
        ReachabilityEvidence reachability = verifyCandidateReachability(source.value.board,
            graph, source.value.reachability);
        String candidateProvenance = traceProvenanceHash(candidate.getTraces());
        require(source.value.provenance.equals(candidateProvenance),
            "compacted route metadata provenance remains identical");
        require(traceGraphSources(graph).equals(source.value.routeSources),
            "compacted graph provenance covers complete source routes");
        verifyCompactedTraceMetadata(source.value.traces, candidate.getTraces());
        requireSourceUnchanged(source.value);
        return new SelectedSlideProof(new ProofFacts(reachability,
            source.value.routeSources.size(), traceGraphSources(graph).size(),
            source.value.provenance, candidateProvenance));
    }

    private static void verifyCompactedTraceMetadata(Vector<PcbTraceGeometry> source,
            Vector<PcbTraceGeometry> candidate) {
        TreeMap<String,PcbTraceGeometry> sourceById = traceMap(source);
        TreeMap<String,PcbTraceGeometry> candidateById = traceMap(candidate);
        require(sourceById.keySet().equals(candidateById.keySet()),
            "compacted route source identities remain complete and unique");
        for (String sourceId : sourceById.keySet()) {
            GenerationWorkScope.check();
            PcbTraceGeometry first = sourceById.get(sourceId);
            PcbTraceGeometry second = candidateById.get(sourceId);
            require(sameTraceMetadata(first, second),
                "compacted route metadata remains stable: " + sourceId);
            require(first.getXPoints().length == second.getXPoints().length &&
                    first.getYPoints().length == second.getYPoints().length,
                "compacted route preserves its complete vertex sequence: " + sourceId);
        }
    }
    // END COPY-ONLY selected routed slide proof adapter.
}
