package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Native-only proof of one private endpoint-preserving passive move after an
 * existing accepted P07 route.  The observer is diagnostic plumbing only: it
 * never changes the source layout, board graph, route winner, or route count.
 */
public final class PcbRoutedPassiveMoveProofContractTest {
    private static final int VERSION = 15;
    private static final String PRIVATE_GENERATION_METADATA =
        "NOT_ASSIGNED_PRIVATE_CANDIDATE";
    private static final String[] TARGET_PACKAGES = {
        "AXIAL_RESISTOR", "AXIAL_DIODE",
        "RADIAL_ELECTROLYTIC_CAPACITOR", "RADIAL_CERAMIC_CAPACITOR"
    };
    private static int assertions;
    private static int fixtures;

    private PcbRoutedPassiveMoveProofContractTest() { }

    public static void main(String[] args) {
        endpointStartEndMetadataAndDelta();
        rejectInvalidIncidenceOrEndpoint();
        footprintPadsUnrelatedGeometryAndSourceImmutability();
        observerOwnershipNullAndFailureCleanup();
        check(fixtures == 4, "exactly four named move-proof fixture groups");
        System.out.println("PASS: PCB routed passive move proof contracts assertions=" +
            assertions + " fixtures=" + fixtures +
            " evaluator=singlePrivateMove");
    }

    /**
     * Root-owned wrapper entrypoint.  The old census owns routing and its
     * existing three diagnostic lines; this method adds exactly one summary
     * after that owner returns.
     */
    static void observe(long seed, String label) {
        require(label != null && label.length() > 0, "move-proof label is present");
        Recorder recorder = new Recorder();
        PcbRoutedPassiveLegCensusContractTest.MoveProofObserver previous =
            PcbRoutedPassiveLegCensusContractTest.setMoveProofObserver(recorder);
        try {
            PcbRoutedPassiveLegCensusContractTest.observe(seed, label);
        } finally {
            PcbRoutedPassiveLegCensusContractTest.MoveProofObserver displaced =
                PcbRoutedPassiveLegCensusContractTest.setMoveProofObserver(previous);
            if (displaced != recorder)
                throw new IllegalStateException("Move-proof observer owner changed");
        }
        System.out.println(recorder.toJson(seed, label));
    }

    private static final class Recorder
            implements PcbRoutedPassiveLegCensusContractTest.MoveProofObserver {
        int callbacks;
        String status;
        CandidateEvidence evidence;

        public void onCandidate(TroubleshootBoard board, PcbBoardLayout layout,
                String componentId, int dx, int dy, long analyticCopperDelta) {
            if (++callbacks != 1)
                throw new IllegalStateException("More than one private move candidate");
            require(board != null && layout != null,
                "move candidate has a source board and finalized layout");
            if (componentId == null) {
                require(dx == 0 && dy == 0 && analyticCopperDelta == 0L,
                    "null structural candidate carries no fabricated move facts");
                status = "NO_STRUCTURAL_CANDIDATE";
                return;
            }
            evidence = constructCandidate(board, layout, componentId, dx, dy,
                analyticCopperDelta);
            status = "PRIVATE_VALIDATED";
        }

        String toJson(long seed, String label) {
            String effectiveStatus = callbacks == 0 ? "NO_TARGET" : status;
            int constructionCount = evidence == null ? 0 : 1;
            StringBuilder json = new StringBuilder(4096);
            json.append("PCB_ROUTED_PASSIVE_MOVE_PROOF {");
            json.append("\"seed\":"); stringField(json, Long.toString(seed));
            json.append(",\"label\":"); stringField(json, label);
            json.append(",\"version\":").append(VERSION);
            json.append(",\"phase\":\"FINALIZED_ROUTE_CANDIDATE\"");
            json.append(",\"status\":"); stringField(json, effectiveStatus);
            json.append(",\"constructionCount\":").append(constructionCount);
            json.append(",\"newRouteRequests\":0");
            json.append(",\"selectedMutation\":false");
            json.append(",\"move\":");
            if (evidence == null) json.append("null");
            else appendMove(json, evidence);
            json.append(",\"source\":");
            if (evidence == null) json.append("null");
            else appendSource(json, evidence.source);
            json.append(",\"candidate\":");
            if (evidence == null) json.append("null");
            else appendCandidate(json, evidence.candidate);
            json.append(",\"proof\":");
            if (evidence == null) json.append("null");
            else appendProof(json, evidence.proof);
            json.append(",\"physicalFeasibility\":");
            stringField(json, evidence == null ? "NOT_EVALUATED" :
                "VALIDATED_PRIVATE_CANDIDATE");
            json.append('}');
            return json.toString();
        }
    }

    private static CandidateEvidence constructCandidate(TroubleshootBoard board,
            PcbBoardLayout source, String componentId, int dx, int dy,
            long analyticCopperDelta) {
        GenerationWorkScope.check();
        BoardComponent target = board.getComponent(componentId);
        PcbComponentPlacement targetPlacement = source.getComponent(componentId);
        require(target != null && targetPlacement != null,
            "observer target is present in board and finalized layout: " + componentId);
        require(isTargetPackage(targetPlacement.getPhysicalPackage().getId()),
            "observer target is one of the census passive package types: " + componentId);

        Vector<String> targetPadOrder = target.getPadIds();
        require(targetPadOrder.size() == 2,
            "private target has exactly two board pads: " + componentId);
        TreeSet<String> movedPadIds = new TreeSet<String>();
        for (String padId : targetPadOrder) {
            require(movedPadIds.add(padId), "target pad IDs are unique: " + padId);
            require(source.getPad(padId) != null,
                "target pad has a finalized source placement: " + padId);
        }

        String liveGraphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        String geometryBefore = PlayerSessionFingerprint.of(source.geometryFingerprint());
        Vector<PcbTraceGeometry> sourceTraces = source.getTraces();
        TreeSet<String> sourceRouteSources = requireUniqueTraceSources(sourceTraces);
        Map<String,Point> sourcePadCenters = padCenters(source);

        Vector<String> componentIds = new Vector<String>(board.getComponentIds());
        Collections.sort(componentIds);
        TreeMap<String,PcbFootprint> sourceFootprints =
            new TreeMap<String,PcbFootprint>();
        TreeMap<String,PcbFootprint> candidateFootprints =
            new TreeMap<String,PcbFootprint>();
        Vector<PcbFootprint> candidateFootprintVector = new Vector<PcbFootprint>();
        for (String id : componentIds) {
            GenerationWorkScope.check();
            PcbFootprint original = footprintFromLayout(board, source, id);
            PcbFootprint candidate = id.equals(componentId) ?
                original.translated(targetPlacement.getX() + dx,
                    targetPlacement.getY() + dy) : original;
            sourceFootprints.put(id, original);
            candidateFootprints.put(id, candidate);
            candidateFootprintVector.add(candidate);
        }

        PcbBoardLayout candidate = new PcbBoardLayout(source.getWidth(), source.getHeight(),
            source.getBoardOutline(), source.getPartsTray(),
            source.getLayoutAlgorithmVersion());
        for (String id : componentIds) {
            GenerationWorkScope.check();
            PcbFootprint footprint = candidateFootprints.get(id);
            candidate.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads()) candidate.addPad(pad);
        }
        for (PcbLayoutRegion region : source.getRegions()) candidate.addRegion(region);
        for (PcbBoardHole hole : source.getHoles()) candidate.addHole(hole);
        for (PcbSilkscreenLabel label : source.getSilkscreenLabels())
            candidate.addSilkscreenLabel(copyLabel(label, componentId, movedPadIds, dx, dy));

        Map<String,Point> candidatePadCenters = padCenters(candidate);
        TraceRewrite rewrite = rewriteTraces(sourceTraces, movedPadIds,
            sourcePadCenters, candidatePadCenters);
        for (PcbTraceGeometry trace : rewrite.traces) candidate.addTrace(trace);
        candidate.setRoutingStatistics(source.getRoutingExpansions(),
            source.getRawRoutingSegments(), source.getRoutingCongestionRejections());
        if (source.getRoutingRecoveryStatistics() != null)
            candidate.setRoutingRecoveryStatistics(source.getRoutingRecoveryStatistics());

        // These are the only candidate admission calls.  Each rejection or
        // cancellation escapes to the caller as a failed private proof.
        GenerationWorkScope.check();
        PcbPlacementPlanner.validate(board, board.getPlacementConstraints(),
            candidate.getBoardOutline(), candidateFootprintVector);
        GenerationWorkScope.check();
        candidate.validateGeometry(board);
        GenerationWorkScope.check();
        new PcbTwoLayerRules(board, candidate).validate(candidate);

        GenerationWorkScope.check();
        PcbConductorGraph sourceGraph = source.captureConductorGraph(board);
        GenerationWorkScope.check();
        PcbConductorGraph candidateGraph = candidate.captureConductorGraph(board);
        ReachabilityEvidence reachability = verifyReachability(board, sourceGraph,
            candidateGraph);
        String sourceProvenance = traceProvenanceHash(sourceTraces);
        String candidateProvenance = traceProvenanceHash(candidate.getTraces());
        require(sourceProvenance.equals(candidateProvenance),
            "trace metadata provenance remains identical");
        require(traceGraphSources(sourceGraph).equals(sourceRouteSources) &&
                traceGraphSources(candidateGraph).equals(sourceRouteSources),
            "trace graph provenance covers the complete route source set");
        require(verifyTraceEndpointOnly(sourceTraces, candidate.getTraces(),
                movedPadIds, sourcePadCenters, candidatePadCenters),
            "only explicit moved trace endpoints changed");
        require(verifyUnrelatedGeometry(board, source, candidate, sourceFootprints,
                candidateFootprints, componentId, movedPadIds, dx, dy),
            "unrelated footprints, pads, labels, holes, regions and envelopes are unchanged");

        String geometryAfter = PlayerSessionFingerprint.of(source.geometryFingerprint());
        String liveGraphAfter = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        require(geometryBefore.equals(geometryAfter),
            "source geometry hash is unchanged through private construction");
        require(liveGraphBefore.equals(liveGraphAfter),
            "source live graph hash is unchanged through private construction");
        require(rewrite.coveredPadIds.equals(movedPadIds),
            "every moved pad has exact explicit endpoint coverage");

        long sourceCopperLength = copperLength(sourceTraces);
        long candidateCopperLength = copperLength(candidate.getTraces());
        long actualCopperDelta = candidateCopperLength - sourceCopperLength;
        require(actualCopperDelta == analyticCopperDelta,
            "actual total copper delta equals supplied analytic delta");
        require(rewrite.affectedTraceCount > 0 && rewrite.movedPadCount == 2,
            "private move changes incident traces and exactly two pads");
        String candidateGeometry = PlayerSessionFingerprint.of(
            candidate.geometryFingerprint());
        return new CandidateEvidence(componentId, dx, dy, analyticCopperDelta,
            actualCopperDelta,
            new SourceEvidence(liveGraphBefore, liveGraphAfter, geometryBefore,
                geometryAfter, sourceCopperLength, area(source)),
            new CandidateFacts(candidateGeometry, candidate.getComponents().size(),
                candidate.getPads().size(), candidate.getTraces().size(),
                rewrite.affectedTraceCount, rewrite.movedPadCount,
                candidateCopperLength, area(candidate)),
            new ProofFacts(reachability,
                sourceRouteSources.size(), traceGraphSources(candidateGraph).size(),
                sourceProvenance, candidateProvenance));
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
                movedPadIds.contains(a.getTargetPadId());
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
            movedPadIds.contains(source.getTargetPadId());
        if (!moved) return source;
        Rectangle bounds = source.getBounds();
        Rectangle translated = new Rectangle(
            PcbCoordinateSystem.checkedAddBoardCoordinate(bounds.x, dx),
            PcbCoordinateSystem.checkedAddBoardCoordinate(bounds.y, dy),
            bounds.width, bounds.height);
        return new PcbSilkscreenLabel(source.getId(), source.getText(), translated,
            source.getFontSize(), source.isBold(), source.getTargetPadId());
    }

    private static ReachabilityEvidence verifyReachability(TroubleshootBoard board,
            PcbConductorGraph source, PcbConductorGraph candidate) {
        Vector<String> pads = new Vector<String>(board.getPadIds());
        Collections.sort(pads);
        PcbConductorGraph.Snapshot sourceSnapshot = source.pristine();
        PcbConductorGraph.Snapshot candidateSnapshot = candidate.pristine();
        StringBuilder sourceBits = new StringBuilder("R1\n");
        StringBuilder candidateBits = new StringBuilder("R1\n");
        int pairCount = 0;
        for (int first = 0; first < pads.size(); first++) {
            for (int second = first + 1; second < pads.size(); second++) {
                GenerationWorkScope.check();
                String a = pads.get(first), b = pads.get(second);
                boolean expected = board.getPad(a).getNetId().equals(
                    board.getPad(b).getNetId());
                boolean sourceConnected = sourceSnapshot.padsConnected(a, b);
                boolean candidateConnected = candidateSnapshot.padsConnected(a, b);
                require(sourceConnected == expected,
                    "source conductor reachability matches board net identity: " + a + "/" + b);
                require(candidateConnected == expected,
                    "candidate conductor reachability matches board net identity: " + a + "/" + b);
                appendReachability(sourceBits, a, b, sourceConnected);
                appendReachability(candidateBits, a, b, candidateConnected);
                pairCount++;
            }
        }
        String sourceHash = PlayerSessionFingerprint.of(sourceBits.toString());
        String candidateHash = PlayerSessionFingerprint.of(candidateBits.toString());
        require(sourceHash.equals(candidateHash),
            "source and candidate canonical pad reachability hashes match");
        requireHash(sourceHash); requireHash(candidateHash);
        return new ReachabilityEvidence(pairCount, sourceHash, candidateHash);
    }

    private static void appendReachability(StringBuilder out, String a, String b,
            boolean connected) {
        out.append(PcbConductorGraph.field(a)).append(PcbConductorGraph.field(b))
            .append(connected ? '1' : '0').append('\n');
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

    private static final class CandidateEvidence {
        final String componentId;
        final int dx, dy;
        final long analyticCopperDelta, actualCopperDelta;
        final SourceEvidence source;
        final CandidateFacts candidate;
        final ProofFacts proof;
        CandidateEvidence(String componentId, int dx, int dy, long analyticCopperDelta,
                long actualCopperDelta, SourceEvidence source, CandidateFacts candidate,
                ProofFacts proof) {
            this.componentId = componentId; this.dx = dx; this.dy = dy;
            this.analyticCopperDelta = analyticCopperDelta;
            this.actualCopperDelta = actualCopperDelta;
            this.source = source; this.candidate = candidate; this.proof = proof;
        }
    }

    private static final class SourceEvidence {
        final String liveGraphBefore, liveGraphAfter, geometryBefore, geometryAfter;
        final long copperLength, area;
        SourceEvidence(String liveGraphBefore, String liveGraphAfter, String geometryBefore,
                String geometryAfter, long copperLength, long area) {
            this.liveGraphBefore = liveGraphBefore; this.liveGraphAfter = liveGraphAfter;
            this.geometryBefore = geometryBefore; this.geometryAfter = geometryAfter;
            this.copperLength = copperLength; this.area = area;
        }
    }

    private static final class CandidateFacts {
        final String geometryHash;
        final int componentCount, padCount, traceCount, affectedTraceCount, movedPadCount;
        final long copperLength, area;
        CandidateFacts(String geometryHash, int componentCount, int padCount, int traceCount,
                int affectedTraceCount, int movedPadCount, long copperLength, long area) {
            this.geometryHash = geometryHash; this.componentCount = componentCount;
            this.padCount = padCount; this.traceCount = traceCount;
            this.affectedTraceCount = affectedTraceCount; this.movedPadCount = movedPadCount;
            this.copperLength = copperLength; this.area = area;
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

    private static void appendMove(StringBuilder json, CandidateEvidence evidence) {
        json.append('{');
        json.append("\"componentId\":"); stringField(json, evidence.componentId);
        json.append(",\"dx\":").append(evidence.dx);
        json.append(",\"dy\":").append(evidence.dy);
        json.append(",\"analyticCopperDelta\":").append(evidence.analyticCopperDelta);
        json.append(",\"actualCopperDelta\":").append(evidence.actualCopperDelta);
        json.append('}');
    }

    private static void appendSource(StringBuilder json, SourceEvidence source) {
        json.append('{');
        json.append("\"liveGraphBefore\":"); stringField(json, source.liveGraphBefore);
        json.append(",\"liveGraphAfter\":"); stringField(json, source.liveGraphAfter);
        json.append(",\"geometryBefore\":"); stringField(json, source.geometryBefore);
        json.append(",\"geometryAfter\":"); stringField(json, source.geometryAfter);
        json.append(",\"copperLength\":").append(source.copperLength);
        json.append(",\"area\":").append(source.area);
        json.append('}');
    }

    private static void appendCandidate(StringBuilder json, CandidateFacts candidate) {
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
        PcbSilkscreenLabel candidateAssociated = copyLabel(sourceAssociated, "R1",
            movedPadIds, dx, dy);
        PcbSilkscreenLabel candidateUnrelated = copyLabel(sourceUnrelated, "R1",
            movedPadIds, dx, dy);
        PcbBoardLayout sourceLayout = moveFixtureLayout(source, unrelated,
            sourceAssociated, sourceUnrelated);
        PcbBoardLayout candidateLayout = moveFixtureLayout(moved, unrelated,
            candidateAssociated, candidateUnrelated);
        TreeMap<String,PcbFootprint> sourceFootprints = new TreeMap<String,PcbFootprint>();
        sourceFootprints.put("R1", rebuilt); sourceFootprints.put("R2", unrelated);
        TreeMap<String,PcbFootprint> candidateFootprints =
            new TreeMap<String,PcbFootprint>();
        candidateFootprints.put("R1", moved); candidateFootprints.put("R2", unrelated);
        check(verifyUnrelatedGeometry(board, sourceLayout, candidateLayout,
                sourceFootprints, candidateFootprints, "R1", movedPadIds, dx, dy),
            "actual candidate layout preserves unrelated geometry and shifts associated label");
        PcbFootprint corruptedUnrelated = unrelated.translated(720, 180);
        PcbBoardLayout corruptedLayout = moveFixtureLayout(moved, corruptedUnrelated,
            candidateAssociated, candidateUnrelated);
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
            PcbFootprint second, PcbSilkscreenLabel firstLabel,
            PcbSilkscreenLabel secondLabel) {
        PcbBoardLayout layout = new PcbBoardLayout(1200, 600,
            new Rectangle(0, 0, 1200, 600), new Rectangle(0, 0, 1200, 600),
            SeededPcbLayoutGenerator.CURRENT_VERSION);
        addFixtureFootprint(layout, first);
        addFixtureFootprint(layout, second);
        layout.addSilkscreenLabel(firstLabel);
        layout.addSilkscreenLabel(secondLabel);
        return layout;
    }

    private static void addFixtureFootprint(PcbBoardLayout layout,
            PcbFootprint footprint) {
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
    }

    private static void observerOwnershipNullAndFailureCleanup() {
        PcbRoutedPassiveLegCensusContractTest.MoveProofObserver original =
            PcbRoutedPassiveLegCensusContractTest.setMoveProofObserver(null);
        final int[] calls = {0};
        PcbRoutedPassiveLegCensusContractTest.MoveProofObserver sentinel =
            new PcbRoutedPassiveLegCensusContractTest.MoveProofObserver() {
                public void onCandidate(TroubleshootBoard board, PcbBoardLayout layout,
                        String componentId, int dx, int dy, long analyticCopperDelta) {
                    calls[0]++;
                    throw new AssertionError("failure fixture observer must not be called");
                }
            };
        PcbRoutedPassiveLegCensusContractTest.MoveProofObserver displaced =
            PcbRoutedPassiveLegCensusContractTest.setMoveProofObserver(sentinel);
        check(displaced == null &&
                PcbRoutedPassiveLegCensusContractTest.setMoveProofObserver(sentinel) == sentinel,
            "observer setter returns and retains its current owner");
        try {
            PcbRoutedPassiveLegCensusContractTest.setMoveProofObserver(null);
            PcbRoutedPassiveLegCensusContractTest.dispatchMoveProofObserver(
                null, null, null);
            check(PcbRoutedPassiveLegCensusContractTest.setMoveProofObserver(sentinel) == null &&
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
            displaced = PcbRoutedPassiveLegCensusContractTest.setMoveProofObserver(null);
            check(displaced == sentinel && calls[0] == 0,
                "observe finally restores prior observer and null clears inspection");
        } finally {
            PcbRoutedPassiveLegCensusContractTest.setMoveProofObserver(original);
        }
        fixture("observerOwnershipNullAndFailureCleanup");
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
}
