package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.Vector;

/** Read-only census of explicit routed endpoint legs on accepted15 P07 candidate2. */
public final class PcbRoutedPassiveLegCensusContractTest {
    private static final int VERSION = 15;
    private static final int MAX_PARTS = 128;
    private static final int[][] DIRECTIONS = {
        {-10, 0}, {10, 0}, {0, -10}, {0, 10}
    };
    private static final String[] TARGET_PACKAGES = {
        "AXIAL_RESISTOR", "AXIAL_DIODE",
        "RADIAL_ELECTROLYTIC_CAPACITOR", "RADIAL_CERAMIC_CAPACITOR"
    };
    private static int assertions;
    private static int fixtures;
    // BEGIN COPY-ONLY private feasibility census bridge.
    static final class MoveIntent {
        final String componentId;
        final int dx, dy;
        final long analyticCopperDelta;
        MoveIntent(String componentId, int dx, int dy, long analyticCopperDelta) {
            this.componentId = componentId;
            this.dx = dx; this.dy = dy;
            this.analyticCopperDelta = analyticCopperDelta;
        }
    }
    interface FeasibilityObserver {
        void onCandidates(TroubleshootBoard board, PcbBoardLayout layout,
            Vector<MoveIntent> moves);
    }
    private static FeasibilityObserver feasibilityObserver;
    static FeasibilityObserver setFeasibilityObserver(FeasibilityObserver observer) {
        FeasibilityObserver previous = feasibilityObserver;
        feasibilityObserver = observer;
        return previous;
    }
    static void dispatchFeasibilityObserver(TroubleshootBoard board,
            PcbBoardLayout layout, Vector<PartResult> parts) {
        FeasibilityObserver observer = feasibilityObserver;
        if (observer == null) return;
        Vector<MoveIntent> moves = new Vector<MoveIntent>();
        for (PartResult part : parts)
            for (DirectionResult direction : part.directions)
                if (direction.eligible && direction.copperDelta.longValue() <= 0L)
                    moves.add(new MoveIntent(part.componentId, direction.dx,
                        direction.dy, direction.copperDelta.longValue()));
        observer.onCandidates(board, layout, moves);
    }
    // END COPY-ONLY private feasibility census bridge.

    private PcbRoutedPassiveLegCensusContractTest() { }

    public static void main(String[] args) {
        fixturePositiveStartEndCollinearIndependentDeltas();
        fixtureOrthogonalAndNonincidentRejection();
        fixtureCollapseReversalZeroLengthAndCoordinateMismatch();
        fixtureSameTraceAndExtraCenterlineContactRejection();
        fixturePackageAnchorConnectorAndIsolationEligibility();
        fixtureDeterministicOrderAndReadonlyIdentity();
        check(fixtures == 6, "exactly six named endpoint-leg fixture groups");
        System.out.println("PASS: PCB routed passive leg census contracts assertions=" +
            assertions + " fixtures=" + fixtures +
            " evaluator=endpointLegCensus");
    }

    /** Called by the fixed-root wrapper after the accepted15 helper runs. */
    static void observe(long seed, String label) {
        require(label != null && label.length() > 0, "label is present");
        Recorder recorder = new Recorder();
        SeededPcbLayoutGenerator.SuccessfulRouteObserver previous =
            SeededPcbLayoutGenerator.setSuccessfulRouteObserver(recorder);
        String selectedReceipt;
        try {
            selectedReceipt =
                PcbPostSelectionEdgeTrimContractTest.observeRoutedPassiveLegCensus(seed);
        } finally {
            SeededPcbLayoutGenerator.SuccessfulRouteObserver displaced =
                SeededPcbLayoutGenerator.setSuccessfulRouteObserver(previous);
            if (displaced != recorder)
                throw new IllegalStateException("Successful-route observer owner changed");
        }
        require(selectedReceipt != null &&
                selectedReceipt.startsWith("PCB_EDGE_TRIM_CASE {"),
            "accepted15 helper returns its selected receipt");
        require(!recorder.callbacks.isEmpty(),
            "accepted15 helper emitted successful-route callbacks");
        String graphAfter = PcbCanvasBoundaryContractTest.liveGraphSha256(
            recorder.firstBoard);
        require(recorder.graphHashBefore.equals(graphAfter),
            "source graph is unchanged through selected helper");
        String selectedGraph = jsonStringField(selectedReceipt,
            "liveBoardGraphSha256");
        require(recorder.graphHashBefore.equals(selectedGraph),
            "callback source graph matches accepted15 selected receipt");

        System.out.println(recorder.toJson(seed, label, graphAfter));
        System.out.println("PCB_ROUTED_LEG_CASE " +
            selectedReceipt.substring("PCB_EDGE_TRIM_CASE ".length()));
    }

    private static final class Recorder
            implements SeededPcbLayoutGenerator.SuccessfulRouteObserver {
        final Vector<Callback> callbacks = new Vector<Callback>();
        TroubleshootBoard firstBoard;
        String graphHashBefore;
        int targetCallbackCount;
        int partCount;
        int eligiblePartCount;
        int eligibleDirectionCount;
        int rejectedDirectionCount;

        public void onSuccessfulRoute(TroubleshootBoard board, int attempt,
                long cheapScore, PcbBoardLayout layout, boolean twoLayer,
                double quality, long area, int majorSide, boolean preferCompact,
                boolean preferDensePacking) {
            require(board != null && layout != null,
                "successful callback has a source board and layout");
            if (firstBoard == null) {
                firstBoard = board;
                graphHashBefore =
                    PcbCanvasBoundaryContractTest.liveGraphSha256(board);
            } else {
                require(graphHashBefore.equals(
                        PcbCanvasBoundaryContractTest.liveGraphSha256(board)),
                    "source graph is unchanged between successful callbacks");
            }

            String geometryBefore = PlayerSessionFingerprint.of(
                layout.geometryFingerprint());
            boolean target = twoLayer && attempt == 2;
            Vector<PartResult> parts = target ? analyzeTarget(board, layout) :
                new Vector<PartResult>();
            // BEGIN COPY-ONLY private feasibility census bridge.
            if (target) dispatchFeasibilityObserver(board, layout, parts);
            // END COPY-ONLY private feasibility census bridge.
            String geometryAfter = PlayerSessionFingerprint.of(
                layout.geometryFingerprint());
            require(geometryBefore.equals(geometryAfter),
                "callback analysis preserves the routed geometry fingerprint");

            callbacks.add(new Callback(callbacks.size(),
                twoLayer ? "P07_FULLER_TWO_LAYER" : "P05_ONE_FACE",
                attempt, target, geometryBefore, geometryAfter, parts));
            if (!target) return;

            targetCallbackCount++;
            partCount += parts.size();
            for (PartResult part : parts) {
                if (part.eligiblePackage) eligiblePartCount++;
                for (DirectionResult direction : part.directions) {
                    if (direction.eligible) eligibleDirectionCount++;
                    else rejectedDirectionCount++;
                }
            }
        }

        private static Vector<PartResult> analyzeTarget(TroubleshootBoard board,
                PcbBoardLayout layout) {
            Vector<PcbComponentPlacement> placements = layout.getComponents();
            require(placements.size() <= MAX_PARTS,
                "target component count stays within the diagnostic bound");
            Collections.sort(placements,
                new Comparator<PcbComponentPlacement>() {
                    public int compare(PcbComponentPlacement first,
                            PcbComponentPlacement second) {
                        return first.getComponentId().compareTo(
                            second.getComponentId());
                    }
                });
            Vector<String> sourceComponentIds = board.getComponentIds();
            Collections.sort(sourceComponentIds);
            require(placements.size() == sourceComponentIds.size(),
                "all source components have routed placements");
            for (int index = 0; index < placements.size(); index++)
                require(placements.get(index).getComponentId().equals(
                        sourceComponentIds.get(index)),
                    "routed placements retain canonical source order at " + index);
            PcbPlacementConstraints constraints = board.getPlacementConstraints();
            Vector<TraceInput> traces = snapshotTraces(layout.getTraces());
            Vector<PartResult> result = new Vector<PartResult>();
            for (PcbComponentPlacement placement : placements) {
                GenerationWorkScope.check();
                String componentId = placement.getComponentId();
                BoardComponent component = board.getComponent(componentId);
                require(component != null,
                    "layout component has a source component: " + componentId);
                String packageId = placement.getPhysicalPackage().getId();
                require(packageId.equals(component.getPhysicalPackage().getId()),
                    "layout package identity matches source component: " + componentId);
                PcbPlacementConstraints.Part demand = constraints.get(componentId);
                if (demand != null)
                    require(componentId.equals(demand.componentId),
                        "placement constraint has canonical component ID: " +
                            componentId);
                boolean isolationBody = placement.getPhysicalGeometry()
                    .getIsolationBody() != null;
                Eligibility eligibility = eligibility(packageId,
                    placement.getPhysicalPackage().isConnector(),
                    demand == null ? null : demand.anchor, isolationBody,
                    demand != null);
                Vector<PadInput> pads = snapshotPads(board, layout, component);
                result.add(analyzePart(componentId, packageId, eligibility,
                    pads, traces));
            }
            return result;
        }

        String toJson(long seed, String label, String graphHashAfter) {
            require(graphHashBefore != null && graphHashAfter != null,
                "source graph hashes are captured");
            require(graphHashBefore.equals(graphHashAfter),
                "source graph hash remains identical before and after helper");
            StringBuilder json = new StringBuilder(32000);
            json.append("PCB_ROUTED_LEG_CENSUS {\"seed\":");
            stringField(json, Long.toString(seed));
            json.append(",\"label\":");
            stringField(json, label);
            json.append(",\"version\":").append(VERSION);
            json.append(",\"phase\":\"FINALIZED_ROUTE_CANDIDATE\"");
            json.append(",\"status\":\"OBSERVATION_ONLY\"");
            json.append(",\"physicalFeasibility\":\"NOT_EVALUATED\"");
            json.append(",\"graphHashBefore\":");
            stringField(json, graphHashBefore);
            json.append(",\"graphHashAfter\":");
            stringField(json, graphHashAfter);
            json.append(",\"successfulCallbacks\":[");
            for (int index = 0; index < callbacks.size(); index++) {
                if (index != 0) json.append(',');
                appendCallback(json, callbacks.get(index));
            }
            json.append("],\"aggregate\":{\"callbackCount\":")
                .append(callbacks.size());
            json.append(",\"targetCallbackCount\":")
                .append(targetCallbackCount);
            json.append(",\"partCount\":").append(partCount);
            json.append(",\"eligiblePartCount\":").append(eligiblePartCount);
            json.append(",\"eligibleDirectionCount\":")
                .append(eligibleDirectionCount);
            json.append(",\"rejectedDirectionCount\":")
                .append(rejectedDirectionCount).append("}}");
            return json.toString();
        }
    }

    private static final class Callback {
        final int index;
        final String strategy;
        final int attempt;
        final boolean target;
        final String geometryHashBefore;
        final String geometryHashAfter;
        final Vector<PartResult> parts;

        Callback(int index, String strategy, int attempt, boolean target,
                String geometryHashBefore, String geometryHashAfter,
                Vector<PartResult> parts) {
            this.index = index;
            this.strategy = strategy;
            this.attempt = attempt;
            this.target = target;
            this.geometryHashBefore = geometryHashBefore;
            this.geometryHashAfter = geometryHashAfter;
            this.parts = parts;
        }
    }

    private static final class Eligibility {
        final boolean eligible;
        final String reason;
        Eligibility(boolean eligible, String reason) {
            this.eligible = eligible;
            this.reason = reason;
        }
    }

    private static Eligibility eligibility(String packageId, boolean connector,
            PcbPlacementConstraints.Anchor anchor, boolean isolationBody,
            boolean hasConstraint) {
        if (!hasConstraint)
            return new Eligibility(false, "MISSING_PLACEMENT_CONSTRAINT");
        if (!isTargetPackage(packageId))
            return new Eligibility(false, "UNSUPPORTED_PACKAGE");
        if (anchor != PcbPlacementConstraints.Anchor.NONE)
            return new Eligibility(false, "ANCHOR_NOT_NONE");
        if (connector) return new Eligibility(false, "CONNECTOR");
        if (isolationBody) return new Eligibility(false, "ISOLATION_BODY");
        return new Eligibility(true, "ELIGIBLE");
    }

    private static boolean isTargetPackage(String packageId) {
        for (String target : TARGET_PACKAGES)
            if (target.equals(packageId)) return true;
        return false;
    }

    private static Vector<PadInput> snapshotPads(TroubleshootBoard board,
            PcbBoardLayout layout, BoardComponent component) {
        Vector<String> padIds = component.getPadIds();
        Collections.sort(padIds);
        Vector<PadInput> pads = new Vector<PadInput>();
        for (String padId : padIds) {
            BoardPad source = board.getPad(padId);
            PcbPadPlacement placement = layout.getPad(padId);
            require(source != null && placement != null,
                "source and routed pad exist: " + padId);
            require(component.getId().equals(source.getComponentId()),
                "pad belongs to its canonical component: " + padId);
            pads.add(new PadInput(source.getId(), source.getNetId(),
                placement.getX(), placement.getY()));
        }
        return pads;
    }

    private static Vector<TraceInput> snapshotTraces(
            Vector<PcbTraceGeometry> source) {
        Vector<TraceInput> traces = new Vector<TraceInput>();
        for (int index = 0; index < source.size(); index++)
            traces.add(new TraceInput(index, source.get(index)));
        return traces;
    }

    private static final class PadInput {
        final String padId;
        final String netId;
        final int x;
        final int y;
        PadInput(String padId, String netId, int x, int y) {
            this.padId = padId;
            this.netId = netId;
            this.x = x;
            this.y = y;
        }
    }

    /** Immutable endpoint snapshot retaining trace position in the source layout. */
    private static final class TraceInput {
        final int traceIndex;
        final String sourceId;
        final String netId;
        final String layer;
        final int width;
        final String startPadId;
        final String endPadId;
        final int[] xPoints;
        final int[] yPoints;

        TraceInput(int traceIndex, PcbTraceGeometry trace) {
            this.traceIndex = traceIndex;
            this.sourceId = trace.getSourceId();
            this.netId = trace.getNetId();
            this.layer = trace.getLayer().name();
            this.width = trace.getWidth();
            this.startPadId = trace.getStartPadId();
            this.endPadId = trace.getEndPadId();
            this.xPoints = trace.getXPoints();
            this.yPoints = trace.getYPoints();
        }
        int startX() { return xPoints[0]; }
        int startY() { return yPoints[0]; }
        int endX() { return xPoints[xPoints.length - 1]; }
        int endY() { return yPoints[yPoints.length - 1]; }
    }

    private static final class Incident {
        final TraceInput trace;
        final String endRole;
        final int endpointX;
        final int endpointY;
        final int neighborX;
        final int neighborY;
        Incident(TraceInput trace, String endRole, int endpointX, int endpointY,
                int neighborX, int neighborY) {
            this.trace = trace;
            this.endRole = endRole;
            this.endpointX = endpointX;
            this.endpointY = endpointY;
            this.neighborX = neighborX;
            this.neighborY = neighborY;
        }
    }

    private static final class PadResult {
        final PadInput pad;
        final Vector<Incident> incidents = new Vector<Incident>();
        PadResult(PadInput pad) { this.pad = pad; }
    }

    private static final class DirectionResult {
        final int dx;
        final int dy;
        final boolean eligible;
        final String reason;
        final Long copperDelta;
        DirectionResult(int dx, int dy, boolean eligible, String reason,
                Long copperDelta) {
            this.dx = dx;
            this.dy = dy;
            this.eligible = eligible;
            this.reason = reason;
            this.copperDelta = copperDelta;
        }
    }

    private static final class PartResult {
        final String componentId;
        final String packageId;
        final boolean eligiblePackage;
        final String eligibilityReason;
        final boolean knownExtraCenterlineContact;
        final int extraCenterlineContactCount;
        final Vector<PadResult> pads;
        final Vector<DirectionResult> directions;

        PartResult(String componentId, String packageId,
                boolean eligiblePackage, String eligibilityReason,
                boolean knownExtraCenterlineContact,
                int extraCenterlineContactCount, Vector<PadResult> pads,
                Vector<DirectionResult> directions) {
            this.componentId = componentId;
            this.packageId = packageId;
            this.eligiblePackage = eligiblePackage;
            this.eligibilityReason = eligibilityReason;
            this.knownExtraCenterlineContact = knownExtraCenterlineContact;
            this.extraCenterlineContactCount = extraCenterlineContactCount;
            this.pads = pads;
            this.directions = directions;
        }
    }

    private static PartResult analyzePart(String componentId, String packageId,
            Eligibility eligibility, Vector<PadInput> sourcePads,
            Vector<TraceInput> traces) {
        Vector<PadInput> orderedPads = new Vector<PadInput>(sourcePads);
        Collections.sort(orderedPads, new Comparator<PadInput>() {
            public int compare(PadInput first, PadInput second) {
                return first.padId.compareTo(second.padId);
            }
        });
        Vector<PadResult> pads = new Vector<PadResult>();
        for (PadInput pad : orderedPads) pads.add(new PadResult(pad));
        if (!eligibility.eligible)
            return rejectedPart(componentId, packageId, eligibility, pads,
                false, 0, "PART_INELIGIBLE");

        int extraContacts = findIncidentsAndExtraContacts(pads, traces);
        String failure = structuralFailure(pads, traces, extraContacts);
        Vector<DirectionResult> directions = new Vector<DirectionResult>();
        for (int[] direction : DIRECTIONS) {
            directions.add(failure == null ?
                analyzeDirection(direction[0], direction[1], pads) :
                rejected(direction[0], direction[1], failure));
        }
        return new PartResult(componentId, packageId, true, eligibility.reason,
            extraContacts > 0, extraContacts, pads, directions);
    }

    private static PartResult rejectedPart(String componentId, String packageId,
            Eligibility eligibility, Vector<PadResult> pads,
            boolean extraContact, int extraCount, String reason) {
        Vector<DirectionResult> directions = new Vector<DirectionResult>();
        for (int[] direction : DIRECTIONS)
            directions.add(rejected(direction[0], direction[1], reason));
        return new PartResult(componentId, packageId, false, eligibility.reason,
            extraContact, extraCount, pads, directions);
    }

    private static int findIncidentsAndExtraContacts(Vector<PadResult> pads,
            Vector<TraceInput> traces) {
        int extraContacts = 0;
        for (PadResult pad : pads) {
            for (TraceInput trace : traces) {
                boolean matchingStart = pad.pad.padId.equals(trace.startPadId) &&
                    trace.startX() == pad.pad.x && trace.startY() == pad.pad.y;
                boolean matchingEnd = pad.pad.padId.equals(trace.endPadId) &&
                    trace.endX() == pad.pad.x && trace.endY() == pad.pad.y;
                if (pad.pad.padId.equals(trace.startPadId))
                    pad.incidents.add(new Incident(trace, "START",
                        trace.startX(), trace.startY(),
                        trace.xPoints[1], trace.yPoints[1]));
                if (pad.pad.padId.equals(trace.endPadId)) {
                    int last = trace.xPoints.length - 1;
                    pad.incidents.add(new Incident(trace, "END",
                        trace.endX(), trace.endY(),
                        trace.xPoints[last - 1], trace.yPoints[last - 1]));
                }
                if (containsUnmatchedCenterlineContact(trace, pad.pad.x,
                        pad.pad.y, matchingStart, matchingEnd))
                    extraContacts++;
            }
            Collections.sort(pad.incidents, new Comparator<Incident>() {
                public int compare(Incident first, Incident second) {
                    if (first.trace.traceIndex != second.trace.traceIndex)
                        return first.trace.traceIndex < second.trace.traceIndex ? -1 : 1;
                    return first.endRole.compareTo(second.endRole);
                }
            });
        }
        return extraContacts;
    }

    private static boolean containsUnmatchedCenterlineContact(
            TraceInput trace, int x, int y, boolean matchingStart,
            boolean matchingEnd) {
        int lastSegment = trace.xPoints.length - 1;
        for (int segment = 1; segment < trace.xPoints.length; segment++) {
            if (!pointOnSegment(x, y, trace.xPoints[segment - 1],
                    trace.yPoints[segment - 1], trace.xPoints[segment],
                    trace.yPoints[segment]))
                continue;
            boolean startLeg = matchingStart && segment == 1;
            boolean endLeg = matchingEnd && segment == lastSegment;
            if (!startLeg && !endLeg) return true;
        }
        return false;
    }

    private static boolean pointOnSegment(int x, int y, int x1, int y1,
            int x2, int y2) {
        long dx = (long) x2 - x1;
        long dy = (long) y2 - y1;
        long px = (long) x - x1;
        long py = (long) y - y1;
        if (dx * py - dy * px != 0L) return false;
        return x >= Math.min(x1, x2) && x <= Math.max(x1, x2) &&
            y >= Math.min(y1, y2) && y <= Math.max(y1, y2);
    }

    private static String structuralFailure(Vector<PadResult> pads,
            Vector<TraceInput> traces, int extraContacts) {
        if (pads.size() != 2) return "TERMINAL_COUNT_NOT_TWO";
        for (PadResult pad : pads)
            if (pad.incidents.isEmpty()) {
                if (extraContacts > 0) return "EXTRA_CENTERLINE_CONTACT";
                return "MISSING_EXPLICIT_ENDPOINT";
            }
        for (PadResult pad : pads)
            for (Incident incident : pad.incidents) {
                if (incident.trace.sourceId == null)
                    return "MISSING_TRACE_SOURCE_ID";
                if (!pad.pad.netId.equals(incident.trace.netId))
                    return "TRACE_NET_MISMATCH";
                if (incident.endpointX != pad.pad.x ||
                        incident.endpointY != pad.pad.y)
                    return "ENDPOINT_COORDINATE_MISMATCH";
                if (incident.endpointX == incident.neighborX &&
                        incident.endpointY == incident.neighborY)
                    return "ZERO_LENGTH_LEG";
            }
        TreeSet<String> ownPadIds = new TreeSet<String>();
        for (PadResult pad : pads) ownPadIds.add(pad.pad.padId);
        for (TraceInput trace : traces)
            if (trace.startPadId != null && trace.endPadId != null &&
                    ownPadIds.contains(trace.startPadId) &&
                    ownPadIds.contains(trace.endPadId) &&
                    !trace.startPadId.equals(trace.endPadId))
                return "SAME_TRACE_BOTH_TERMINALS";
        if (extraContacts > 0) return "EXTRA_CENTERLINE_CONTACT";
        return null;
    }

    private static DirectionResult analyzeDirection(int dx, int dy,
            Vector<PadResult> pads) {
        long delta = 0L;
        for (PadResult pad : pads)
            for (Incident incident : pad.incidents) {
                boolean horizontal = incident.endpointY == incident.neighborY &&
                    incident.endpointX != incident.neighborX;
                boolean vertical = incident.endpointX == incident.neighborX &&
                    incident.endpointY != incident.neighborY;
                if ((dx != 0 && !horizontal) || (dy != 0 && !vertical))
                    return rejected(dx, dy, "NON_PARALLEL_SHIFT");
                int shift = dx != 0 ? dx : dy;
                int endpoint = dx != 0 ? incident.endpointX : incident.endpointY;
                int neighbor = dx != 0 ? incident.neighborX : incident.neighborY;
                long oldVector = (long) neighbor - endpoint;
                long newVector = (long) neighbor - ((long) endpoint + shift);
                if (newVector == 0L) return rejected(dx, dy, "COLLAPSE");
                if ((oldVector < 0L && newVector > 0L) ||
                        (oldVector > 0L && newVector < 0L))
                    return rejected(dx, dy, "REVERSE");
                delta += Math.abs(newVector) - Math.abs(oldVector);
            }
        return new DirectionResult(dx, dy, true, "ELIGIBLE",
            Long.valueOf(delta));
    }

    private static DirectionResult rejected(int dx, int dy, String reason) {
        return new DirectionResult(dx, dy, false, reason, null);
    }

    private static void appendCallback(StringBuilder json, Callback callback) {
        json.append("{\"index\":").append(callback.index);
        json.append(",\"strategy\":");
        stringField(json, callback.strategy);
        json.append(",\"attempt\":").append(callback.attempt);
        json.append(",\"target\":").append(callback.target);
        json.append(",\"geometryHashBefore\":");
        stringField(json, callback.geometryHashBefore);
        json.append(",\"geometryHashAfter\":");
        stringField(json, callback.geometryHashAfter);
        json.append(",\"parts\":[");
        for (int index = 0; index < callback.parts.size(); index++) {
            if (index != 0) json.append(',');
            appendPart(json, callback.parts.get(index));
        }
        json.append("]}");
    }

    private static void appendPart(StringBuilder json, PartResult part) {
        json.append("{\"componentId\":");
        stringField(json, part.componentId);
        json.append(",\"packageId\":");
        stringField(json, part.packageId);
        json.append(",\"eligiblePackage\":").append(part.eligiblePackage);
        json.append(",\"eligibilityReason\":");
        stringField(json, part.eligibilityReason);
        json.append(",\"knownExtraCenterlineContact\":")
            .append(part.knownExtraCenterlineContact);
        json.append(",\"extraCenterlineContactCount\":")
            .append(part.extraCenterlineContactCount);
        json.append(",\"pads\":[");
        for (int index = 0; index < part.pads.size(); index++) {
            if (index != 0) json.append(',');
            PadResult result = part.pads.get(index);
            PadInput pad = result.pad;
            json.append("{\"padId\":");
            stringField(json, pad.padId);
            json.append(",\"netId\":");
            stringField(json, pad.netId);
            json.append(",\"x\":").append(pad.x);
            json.append(",\"y\":").append(pad.y);
            json.append(",\"incidents\":[");
            for (int incidentIndex = 0;
                    incidentIndex < result.incidents.size(); incidentIndex++) {
                if (incidentIndex != 0) json.append(',');
                Incident incident = result.incidents.get(incidentIndex);
                json.append("{\"traceIndex\":")
                    .append(incident.trace.traceIndex);
                json.append(",\"traceSourceId\":");
                stringField(json, incident.trace.sourceId);
                json.append(",\"traceNetId\":");
                stringField(json, incident.trace.netId);
                json.append(",\"layer\":");
                stringField(json, incident.trace.layer);
                json.append(",\"width\":").append(incident.trace.width);
                json.append(",\"endRole\":");
                stringField(json, incident.endRole);
                json.append(",\"endpointX\":").append(incident.endpointX);
                json.append(",\"endpointY\":").append(incident.endpointY);
                json.append(",\"neighborX\":").append(incident.neighborX);
                json.append(",\"neighborY\":").append(incident.neighborY);
                json.append('}');
            }
            json.append("]}");
        }
        json.append("],\"directions\":[");
        for (int index = 0; index < part.directions.size(); index++) {
            if (index != 0) json.append(',');
            DirectionResult direction = part.directions.get(index);
            json.append("{\"dx\":").append(direction.dx);
            json.append(",\"dy\":").append(direction.dy);
            json.append(",\"eligible\":").append(direction.eligible);
            json.append(",\"reason\":");
            stringField(json, direction.reason);
            json.append(",\"copperDelta\":");
            if (direction.copperDelta == null) json.append("null");
            else json.append(direction.copperDelta.longValue());
            json.append('}');
        }
        json.append("]}");
    }

    private static String jsonStringField(String source, String name) {
        String key = "\"" + name + "\":\"";
        int keyIndex = source.indexOf(key);
        require(keyIndex >= 0, "selected receipt contains " + name);
        int start = keyIndex + key.length();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int index = start; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (escaped) {
                if (ch == 'n') value.append('\n');
                else if (ch == 'r') value.append('\r');
                else if (ch == 't') value.append('\t');
                else value.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                return value.toString();
            } else {
                value.append(ch);
            }
        }
        throw new AssertionError("Unterminated JSON string field: " + name);
    }

    private static void stringField(StringBuilder json, String value) {
        if (value == null) {
            json.append("null");
            return;
        }
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' || ch == '\\') json.append('\\').append(ch);
            else if (ch == '\n') json.append("\\n");
            else if (ch == '\r') json.append("\\r");
            else if (ch == '\t') json.append("\\t");
            else if (ch < 0x20) {
                json.append("\\u00")
                    .append(Character.forDigit((ch >> 4) & 0xf, 16))
                    .append(Character.forDigit(ch & 0xf, 16));
            } else json.append(ch);
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

    private static TraceInput trace(int index, String sourceId, String netId,
            String startPadId, String endPadId, int[] xs, int[] ys) {
        return new TraceInput(index, new PcbTraceGeometry(sourceId, netId,
            startPadId, endPadId, PcbCopperLayer.TOP,
            PcbCopperAccess.Exposure.EXPOSED, xs, ys, PcbTraceRules.TRACE_WIDTH));
    }

    private static PadInput pad(String id, String net, int x, int y) {
        return new PadInput(id, net, x, y);
    }

    private static Vector<PadInput> pads(PadInput first, PadInput second) {
        Vector<PadInput> result = new Vector<PadInput>();
        result.add(first);
        result.add(second);
        return result;
    }

    private static Vector<TraceInput> traces(TraceInput... values) {
        Vector<TraceInput> result = new Vector<TraceInput>();
        for (TraceInput value : values) result.add(value);
        return result;
    }

    private static Eligibility eligible() {
        return new Eligibility(true, "ELIGIBLE");
    }

    private static PartResult analyzeFixture(Vector<PadInput> sourcePads,
            Vector<TraceInput> traces) {
        return analyzePart("R_FIXTURE", "AXIAL_RESISTOR", eligible(),
            sourcePads, traces);
    }

    private static DirectionResult direction(PartResult result, int dx, int dy) {
        for (DirectionResult direction : result.directions)
            if (direction.dx == dx && direction.dy == dy) return direction;
        throw new AssertionError("Missing direction " + dx + "," + dy);
    }

    private static void fixturePositiveStartEndCollinearIndependentDeltas() {
        PartResult result = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 100)),
            traces(
                trace(0, "trace/start", "N1", "R1.1", "J1.1",
                    new int[] {0, -40}, new int[] {0, 0}),
                trace(1, "trace/end", "N2", "J2.1", "R1.2",
                    new int[] {60, 100}, new int[] {100, 100})));
        DirectionResult positive = direction(result, 10, 0);
        check(positive.eligible, "parallel start and end legs admit +10 X");
        check(Long.valueOf(20L).equals(positive.copperDelta),
            "start and end leg deltas independently sum to +20");
        DirectionResult negative = direction(result, -10, 0);
        check(negative.eligible && Long.valueOf(-20L).equals(
                negative.copperDelta),
            "negative X independently shortens both aligned endpoint legs by 20");
        check(Math.abs(-40L - (0L + 10L)) - Math.abs(-40L - 0L) == 10L,
            "literal START leg grows from 40 to 50");
        check(Math.abs(60L - (100L + 10L)) -
                Math.abs(60L - 100L) == 10L,
            "literal END leg grows from 40 to 50");
        PartResult mixedSameAxis = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 100)),
            traces(
                trace(0, "trace/mixed-start", "N1", "R1.1", "J1.1",
                    new int[] {0, -40}, new int[] {0, 0}),
                trace(1, "trace/mixed-end", "N2", "J2.1", "R1.2",
                    new int[] {140, 100}, new int[] {100, 100})));
        DirectionResult mixedPositive = direction(mixedSameAxis, 10, 0);
        check(mixedPositive.eligible && Long.valueOf(0L).equals(
                mixedPositive.copperDelta),
            "opposed same-axis legs have a zero signed total for +10 X");
        check("START".equals(result.pads.get(0).incidents.get(0).endRole),
            "first explicit leg records its START endpoint");
        check("END".equals(result.pads.get(1).incidents.get(0).endRole),
            "second explicit leg records its END endpoint");
        check(result.pads.get(0).incidents.get(0).trace.traceIndex == 0 &&
                result.pads.get(1).incidents.get(0).trace.traceIndex == 1,
            "separate endpoint legs keep distinct layout trace indices");
        fixture("positiveStartEndCollinearIndependentDeltas");
    }

    private static void fixtureOrthogonalAndNonincidentRejection() {
        PartResult orthogonal = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 0)),
            traces(
                trace(0, "trace/vertical", "N1", "R1.1", "J1.1",
                    new int[] {0, 0}, new int[] {0, 40}),
                trace(1, "trace/horizontal", "N2", "J2.1", "R1.2",
                    new int[] {60, 100}, new int[] {0, 0})));
        check("NON_PARALLEL_SHIFT".equals(
                direction(orthogonal, 10, 0).reason),
            "orthogonal endpoint leg rejects an X shift");

        PartResult nonincident = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 0)),
            traces(
                trace(0, "trace/first", "N1", "R1.1", "J1.1",
                    new int[] {0, -40}, new int[] {0, 0}),
                trace(1, "trace/through", "N2", "J2.1", "J2.2",
                    new int[] {80, 120}, new int[] {0, 0})));
        check(nonincident.knownExtraCenterlineContact &&
                nonincident.extraCenterlineContactCount == 1,
            "unlabelled centerline crossing is reported as extra contact");
        check("EXTRA_CENTERLINE_CONTACT".equals(
                direction(nonincident, 10, 0).reason),
            "centerline contact excludes structural endpoint advice");

        PartResult missingEndpoint = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 0)),
            traces(
                trace(0, "trace/only-explicit", "N1", "R1.1", "J1.1",
                    new int[] {0, -40}, new int[] {0, 0}),
                trace(1, "trace/away", "N3", "J3.1", "J3.2",
                    new int[] {80, 120}, new int[] {40, 40})));
        check(!missingEndpoint.knownExtraCenterlineContact &&
                "MISSING_EXPLICIT_ENDPOINT".equals(
                    direction(missingEndpoint, 10, 0).reason),
            "a pad without an explicit endpoint and without centerline contact is rejected");

        Vector<PadInput> onePad = new Vector<PadInput>();
        onePad.add(pad("R1.1", "N1", 0, 0));
        PartResult oneTerminal = analyzeFixture(onePad, traces(
            trace(0, "trace/one-terminal", "N1", "R1.1", "J1.1",
                new int[] {0, -40}, new int[] {0, 0})));
        check("TERMINAL_COUNT_NOT_TWO".equals(
                direction(oneTerminal, 10, 0).reason),
            "one-pad passive fixture is rejected by the two-terminal gate");
        fixture("orthogonalAndNonincidentRejection");
    }

    private static void fixtureCollapseReversalZeroLengthAndCoordinateMismatch() {
        PartResult collapse = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 100)),
            traces(
                trace(0, "trace/collapse", "N1", "R1.1", "J1.1",
                    new int[] {0, 10}, new int[] {0, 0}),
                trace(1, "trace/other", "N2", "J2.1", "R1.2",
                    new int[] {140, 100}, new int[] {100, 100})));
        check("COLLAPSE".equals(direction(collapse, 10, 0).reason),
            "exactly reaching the neighbor is rejected as collapse");

        PartResult reversal = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 100)),
            traces(
                trace(0, "trace/reversal", "N1", "R1.1", "J1.1",
                    new int[] {0, 5}, new int[] {0, 0}),
                trace(1, "trace/other", "N2", "J2.1", "R1.2",
                    new int[] {140, 100}, new int[] {100, 100})));
        check("REVERSE".equals(direction(reversal, 10, 0).reason),
            "passing beyond the neighbor is rejected as reversal");

        PartResult zeroLength = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 100)),
            traces(
                trace(0, "trace/zero", "N1", "R1.1", "J1.1",
                    new int[] {0, 0, -20}, new int[] {0, 0, 0}),
                trace(1, "trace/other", "N2", "J2.1", "R1.2",
                    new int[] {140, 100}, new int[] {100, 100})));
        check("ZERO_LENGTH_LEG".equals(
                direction(zeroLength, 10, 0).reason),
            "zero-length immediate leg is rejected");

        PartResult mismatch = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 100)),
            traces(
                trace(0, "trace/mismatch", "N1", "R1.1", "J1.1",
                    new int[] {1, 30}, new int[] {0, 0}),
                trace(1, "trace/other", "N2", "J2.1", "R1.2",
                    new int[] {140, 100}, new int[] {100, 100})));
        check("ENDPOINT_COORDINATE_MISMATCH".equals(
                direction(mismatch, 10, 0).reason),
            "declared endpoint must equal the actual pad center");

        PartResult missingSource = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 100)),
            traces(
                trace(0, null, "N1", "R1.1", "J1.1",
                    new int[] {0, -40}, new int[] {0, 0}),
                trace(1, "trace/source-present", "N2", "J2.1", "R1.2",
                    new int[] {140, 100}, new int[] {100, 100})));
        check("MISSING_TRACE_SOURCE_ID".equals(
                direction(missingSource, 10, 0).reason),
            "an incident endpoint with no trace source identity is rejected");

        PartResult wrongNet = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 100)),
            traces(
                trace(0, "trace/wrong-net", "OTHER", "R1.1", "J1.1",
                    new int[] {0, -40}, new int[] {0, 0}),
                trace(1, "trace/right-net", "N2", "J2.1", "R1.2",
                    new int[] {140, 100}, new int[] {100, 100})));
        check("TRACE_NET_MISMATCH".equals(
                direction(wrongNet, 10, 0).reason),
            "an incident trace on another net is rejected");
        fixture("collapseReversalZeroLengthAndCoordinateMismatch");
    }

    private static void fixtureSameTraceAndExtraCenterlineContactRejection() {
        PartResult sameTrace = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N1", 100, 0)),
            traces(trace(0, "trace/coupled", "N1", "R1.1", "R1.2",
                new int[] {0, 100}, new int[] {0, 0})));
        check("SAME_TRACE_BOTH_TERMINALS".equals(
                direction(sameTrace, 10, 0).reason),
            "one trace ending at both own pads is conservatively excluded");

        PartResult extra = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 0)),
            traces(
                trace(0, "trace/first", "N1", "R1.1", "J1.1",
                    new int[] {0, -40}, new int[] {0, 0}),
                trace(1, "trace/second", "N2", "J2.1", "R1.2",
                    new int[] {140, 100}, new int[] {0, 0}),
                trace(2, "trace/crossing", "N3", "J3.1", "J3.2",
                    new int[] {-10, 10}, new int[] {0, 0})));
        check(extra.knownExtraCenterlineContact &&
                "EXTRA_CENTERLINE_CONTACT".equals(
                    direction(extra, 10, 0).reason),
            "an additional trace through a pad center is excluded");

        PartResult returningThroughStart = analyzeFixture(
            pads(pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 0)),
            traces(
                trace(0, "trace/returns-through-start", "N1", "R1.1", "J1.1",
                    new int[] {0, -40, -10, 10, 30},
                    new int[] {0, 0, 0, 0, 0}),
                trace(1, "trace/other-terminal", "N2", "J2.1", "R1.2",
                    new int[] {140, 100}, new int[] {0, 0})));
        check(returningThroughStart.knownExtraCenterlineContact &&
                returningThroughStart.extraCenterlineContactCount == 1 &&
                "EXTRA_CENTERLINE_CONTACT".equals(
                    direction(returningThroughStart, 10, 0).reason),
            "later same-trace return through a matched start pad is still an extra contact");
        fixture("sameTraceAndExtraCenterlineContactRejection");
    }

    private static void fixturePackageAnchorConnectorAndIsolationEligibility() {
        for (String packageId : TARGET_PACKAGES) {
            Eligibility accepted = eligibility(packageId, false,
                PcbPlacementConstraints.Anchor.NONE, false, true);
            check(accepted.eligible && "ELIGIBLE".equals(accepted.reason),
                "target package accepted: " + packageId);
        }
        check("UNSUPPORTED_PACKAGE".equals(eligibility("TO92_NPN", false,
                PcbPlacementConstraints.Anchor.NONE, false, true).reason),
            "non-passive package is excluded");
        check("ANCHOR_NOT_NONE".equals(eligibility("AXIAL_RESISTOR", false,
                PcbPlacementConstraints.Anchor.LEFT, false, true).reason),
            "anchored passive is excluded");
        check("CONNECTOR".equals(eligibility("AXIAL_RESISTOR", true,
                PcbPlacementConstraints.Anchor.NONE, false, true).reason),
            "connector classification excludes the part");
        check("ISOLATION_BODY".equals(eligibility("AXIAL_RESISTOR", false,
                PcbPlacementConstraints.Anchor.NONE, true, true).reason),
            "isolation-body package is excluded");
        check("MISSING_PLACEMENT_CONSTRAINT".equals(eligibility(
                "AXIAL_RESISTOR", false, null, false, false).reason),
            "missing part constraint is excluded");
        fixture("packageAnchorConnectorAndIsolationEligibility");
    }

    private static void fixtureDeterministicOrderAndReadonlyIdentity() {
        int[] inputX = {0, -30};
        int[] inputY = {0, 0};
        PcbTraceGeometry first = new PcbTraceGeometry("trace/duplicate-source",
            "N1", "R1.1", "J1.1", PcbCopperLayer.BOTTOM,
            PcbCopperAccess.Exposure.EXPOSED, inputX, inputY, 11);
        inputX[1] = 900;
        int[] exposedX = first.getXPoints();
        exposedX[0] = 800;
        check(first.getXPoints()[0] == 0 && first.getXPoints()[1] == -30,
            "trace constructor and getter preserve defensive point identity");
        check("trace/duplicate-source".equals(first.getSourceId()) &&
                "N1".equals(first.getNetId()) &&
                first.getLayer() == PcbCopperLayer.BOTTOM && first.getWidth() == 11,
            "trace identity, net, layer, and width are retained");

        Vector<PadInput> orderedPads = pads(
            pad("R1.1", "N1", 0, 0), pad("R1.2", "N2", 100, 100));
        Vector<TraceInput> orderedTraces = traces(
            new TraceInput(0, first),
            trace(1, "trace/duplicate-source", "N2", "J2.1", "R1.2",
                new int[] {140, 100}, new int[] {100, 100}));
        PartResult firstRun = analyzeFixture(orderedPads, orderedTraces);
        PartResult secondRun = analyzeFixture(pads(
            pad("R1.2", "N2", 100, 100), pad("R1.1", "N1", 0, 0)),
            orderedTraces);
        check(partCanonical(firstRun).equals(partCanonical(secondRun)),
            "pad ordering is canonical regardless of input order");
        check(firstRun.directions.get(0).dx == -10 &&
                firstRun.directions.get(0).dy == 0 &&
                firstRun.directions.get(1).dx == 10 &&
                firstRun.directions.get(2).dx == 0 &&
                firstRun.directions.get(2).dy == -10 &&
                firstRun.directions.get(3).dy == 10,
            "four fixed directions retain declared order");
        check(firstRun.pads.get(0).incidents.get(0).trace.traceIndex == 0 &&
                firstRun.pads.get(1).incidents.get(0).trace.traceIndex == 1,
            "duplicate source IDs remain distinguished by layout trace index");
        DirectionResult repeatedSourceDirection = direction(firstRun, 10, 0);
        check(repeatedSourceDirection.eligible &&
                "ELIGIBLE".equals(repeatedSourceDirection.reason),
            "distinct trace indices with duplicate source IDs remain eligible");
        check(Long.valueOf(0L).equals(repeatedSourceDirection.copperDelta) &&
                Math.abs(-30L - (0L + 10L)) - 30L == 10L &&
                Math.abs(140L - (100L + 10L)) - 40L == -10L,
            "repeated-source endpoint legs independently sum literal +10 and -10 deltas");
        fixture("deterministicOrderAndReadonlyIdentity");
    }

    private static String partCanonical(PartResult part) {
        StringBuilder value = new StringBuilder();
        value.append(part.componentId).append('|').append(part.packageId)
            .append('|').append(part.eligiblePackage).append('|')
            .append(part.eligibilityReason).append('|');
        for (PadResult pad : part.pads) {
            value.append(pad.pad.padId).append('@').append(pad.pad.netId)
                .append(':').append(pad.pad.x).append(',').append(pad.pad.y)
                .append('|');
            for (Incident incident : pad.incidents)
                value.append(incident.trace.traceIndex).append(':')
                    .append(incident.trace.sourceId).append(':')
                    .append(incident.endRole).append(':')
                    .append(incident.endpointX).append(',')
                    .append(incident.endpointY).append(':')
                    .append(incident.neighborX).append(',')
                    .append(incident.neighborY).append('|');
        }
        for (DirectionResult direction : part.directions)
            value.append(direction.dx).append(',').append(direction.dy)
                .append(':').append(direction.eligible).append(':')
                .append(direction.reason).append(':')
                .append(direction.copperDelta == null ? "null" :
                    direction.copperDelta.toString()).append('|');
        return value.toString();
    }
}
