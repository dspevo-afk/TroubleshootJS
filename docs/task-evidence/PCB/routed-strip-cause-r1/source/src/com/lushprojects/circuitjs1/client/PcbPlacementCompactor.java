package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/** Removes empty placement strips without shrinking parts, probe targets or access envelopes. */
final class PcbPlacementCompactor {
    private static final int CHANNEL = 20, GRID = 10;
    private PcbPlacementCompactor() { }

    static PcbPlacementPlanner.Plan compact(TroubleshootBoard board,
            PcbPlacementConstraints constraints, PcbPlacementPlanner.Plan input) {
        PcbPlacementPlanner.Plan result = input;
        for (int axis = 0; axis < 2; axis++) {
            GenerationWorkScope.check();
            PcbPlacementPlanner.Plan trial = compactAxis(constraints, result, axis);
            if (trial == result) continue;
            try {
                PcbPlacementPlanner.validate(board, constraints, trial.outline, trial.footprints);
                result = trial;
            } catch (PcbPlacementPlanner.Rejected insufficientAccess) {
                // A barrier or escape channel can require the original space.
                // Keep that axis unchanged; never relax the physical validator.
            }
        }
        return result;
    }
    static final class Routed {
        final PcbPlacementPlanner.Plan plan; final PcbBoardLayout layout;
        Routed(PcbPlacementPlanner.Plan plan,PcbBoardLayout layout) {this.plan=plan;this.layout=layout;}
    }
    /** Present only during an explicitly scoped native diagnostic observation. */
    private static RoutedObservation routedObserver;

    /** Install a native-only observer for one bounded routed generation. */
    static RoutedObservation installRoutedObserver(long seed) {
        if (routedObserver != null)
            throw new IllegalStateException("A routed compactor observer is already installed");
        RoutedObservation observer = new RoutedObservation(Long.toString(seed));
        routedObserver = observer;
        return observer;
    }

    /** Clear only the observer owned by the caller. */
    static boolean clearRoutedObserver(RoutedObservation expected) {
        if (expected == null || routedObserver != expected) return false;
        routedObserver = null;
        return true;
    }

    static boolean isRoutedObserverInstalled(RoutedObservation expected) {
        return expected != null && routedObserver == expected;
    }

    static boolean isRoutedObserverInstalled() {
        return routedObserver != null;
    }

    /** Canonical physical signature used only by the native observation contract. */
    static String routedPhysicalCanonicalSignature(PcbBoardLayout layout) {
        if (layout == null) throw new IllegalArgumentException("Missing routed layout");
        Vector<PcbComponentPlacement> components = layout.getComponents();
        Collections.sort(components, new Comparator<PcbComponentPlacement>() {
            public int compare(PcbComponentPlacement first, PcbComponentPlacement second) {
                return first.getComponentId().compareTo(second.getComponentId());
            }
        });
        String anchorId = components.isEmpty() ? null : components.get(0).getComponentId();
        long anchorX = components.isEmpty() ? 0L : components.get(0).getX();
        long anchorY = components.isEmpty() ? 0L : components.get(0).getY();
        StringBuilder value = new StringBuilder();
        signatureToken(value, "routed-physical-layout@1");
        signatureToken(value, anchorId);
        signatureToken(value, Integer.toString(components.size()));
        for (PcbComponentPlacement component : components) {
            signatureToken(value, component.getComponentId());
            signatureToken(value, Long.toString((long) component.getX() - anchorX));
            signatureToken(value, Long.toString((long) component.getY() - anchorY));
            signatureToken(value, Integer.toString(component.getWidth()));
            signatureToken(value, Integer.toString(component.getHeight()));
            signatureToken(value, component.getRotation().name());
            signatureToken(value, component.getMountingSide().name());
            signatureToken(value, component.getGeometryRealization().fingerprint());
        }

        Vector<PcbPadPlacement> pads = layout.getPads();
        Collections.sort(pads, new Comparator<PcbPadPlacement>() {
            public int compare(PcbPadPlacement first, PcbPadPlacement second) {
                return first.getPadId().compareTo(second.getPadId());
            }
        });
        signatureToken(value, "pads");
        signatureToken(value, Integer.toString(pads.size()));
        for (PcbPadPlacement pad : pads) {
            signatureToken(value, pad.getPadId());
            signatureToken(value, Long.toString((long) pad.getX() - anchorX));
            signatureToken(value, Long.toString((long) pad.getY() - anchorY));
            signatureRectangle(value, pad.getPadBounds(), anchorX, anchorY);
            signatureRectangle(value, pad.getProbeBounds(), anchorX, anchorY);
            signatureToken(value, Integer.toString(pad.getEscapeDx()));
            signatureToken(value, Integer.toString(pad.getEscapeDy()));
            signatureToken(value, Integer.toString(pad.getEscapeLength()));
            signatureToken(value, pad.getAttachment().name());
            signatureToken(value, pad.getMountingSide().name());
            signatureToken(value, pad.getExposure().name());
        }

        Vector<PcbTraceGeometry> traces = layout.getTraces();
        signatureToken(value, "traces");
        signatureToken(value, Integer.toString(traces.size()));
        for (PcbTraceGeometry trace : traces) {
            signatureToken(value, trace.getSourceId());
            signatureToken(value, trace.getNetId());
            signatureToken(value, trace.getLayer().name());
            signatureToken(value, trace.getExposure().name());
            signatureToken(value, Integer.toString(trace.getWidth()));
            signatureToken(value, trace.getStartPadId());
            signatureToken(value, trace.getEndPadId());
            int[] xs = trace.getXPoints();
            int[] ys = trace.getYPoints();
            signatureToken(value, Integer.toString(xs.length));
            for (int index = 0; index < xs.length; index++) {
                signatureToken(value, Long.toString((long) xs[index] - anchorX));
                signatureToken(value, Long.toString((long) ys[index] - anchorY));
            }
        }

        Vector<PcbBoardHole> holes = layout.getHoles();
        Collections.sort(holes, new Comparator<PcbBoardHole>() {
            public int compare(PcbBoardHole first, PcbBoardHole second) {
                return first.id.compareTo(second.id);
            }
        });
        signatureToken(value, "holes");
        signatureToken(value, Integer.toString(holes.size()));
        for (PcbBoardHole hole : holes) {
            signatureToken(value, hole.id);
            signatureToken(value, hole.kind.name());
            signatureToken(value, hole.netId);
            signatureToken(value, Long.toString((long) hole.x - anchorX));
            signatureToken(value, Long.toString((long) hole.y - anchorY));
            signatureToken(value, Integer.toString(hole.drillRadius));
            signatureToken(value, Integer.toString(hole.landRadius));
            signatureToken(value, hole.exposure.name());
            signatureRectangle(value, hole.getBounds(), anchorX, anchorY);
        }
        return value.toString();
    }

    static String routedPhysicalSignature(PcbBoardLayout layout) {
        return PlayerSessionFingerprint.of(routedPhysicalCanonicalSignature(layout));
    }

    private static void signatureRectangle(StringBuilder value, Rectangle rectangle,
            long anchorX, long anchorY) {
        signatureToken(value, Long.toString((long) rectangle.x - anchorX));
        signatureToken(value, Long.toString((long) rectangle.y - anchorY));
        signatureToken(value, Integer.toString(rectangle.width));
        signatureToken(value, Integer.toString(rectangle.height));
    }

    private static void signatureToken(StringBuilder value, String token) {
        if (token == null) {
            value.append("-1:;");
        } else {
            value.append(token.length()).append(':').append(token).append(';');
        }
    }

    /** Contract the already legal route instead of asking the router to find
     * that topology again. Whole package envelopes translate rigidly. Every
     * copper bend/rail is protected, so the monotone maps preserve Manhattan
     * connectivity and routing clearance. Original input remains untouched. */
    static Routed compactRouted(TroubleshootBoard board,PcbPlacementConstraints constraints,
            PcbPlacementPlanner.Plan input,PcbBoardLayout routed) {
        final RoutedObservation observation = routedObserver;
        Routed result=new Routed(input,routed);
        CallRecord callRecord = null;
        if (observation != null) {
            String inputSignature = observedSignature(observation, routed);
            callRecord = safeBeginCall(observation, inputSignature);
        }
        boolean completed = false;
        try {
            for(int axis=0;axis<2;axis++) {
                boolean xAxis=axis==0;
                PcbPlacementPlanner.Plan plan=result.plan;
                String beforeSignature = observation == null ? null :
                    observedSignature(observation, result.layout);
                AxisRecord axisRecord = safeBeginAxis(observation, callRecord, axis,
                    beforeSignature);
                Vector<int[]> occupied=new Vector<int[]>();
                for(PcbFootprint f:plan.footprints) {
                    Rectangle e=PcbPlacementPlanner.envelope(f,constraints.get(f.getPlacement().getComponentId()).accessMargin);
                    occupied.add(new int[]{xAxis?e.x:e.y,xAxis?e.x+e.width:e.y+e.height});
                }
                // Parallel rails and all terminal/branch endpoints keep at least
                // their full centerline clearance, even inside a furniture-sized gap.
                for(PcbTraceGeometry trace:result.layout.getTraces()) {
                    int guard=PcbTraceRules.clearance(trace.getWidth(),trace.getWidth());
                    int[] points=xAxis?trace.getXPoints():trace.getYPoints();
                    for(int point:points)occupied.add(new int[]{point-guard,point+guard});
                }
                for(PcbBoardHole hole:result.layout.getHoles()) {
                    Rectangle bounds=hole.getBounds();
                    int start=xAxis?bounds.x:bounds.y;
                    int end=start+(xAxis?bounds.width:bounds.height);
                    // VIA_LAND and visible-clearance limits are independent of a trace's width.
                    int viaGuard=Math.max(PcbTraceRules.MIN_CENTERLINE_CLEARANCE,
                        PcbTwoLayerRules.VIA_LAND+PcbTraceRules.MIN_VISIBLE_CLEARANCE);
                    occupied.add(new int[]{start-viaGuard,end+viaGuard});
                }
                if (axisRecord != null) safeSetOccupiedCount(observation, axisRecord,
                    occupied.size());
                Collections.sort(occupied,new Comparator<int[]>() {public int compare(int[] a,int[] b){return a[0]-b[0];}});
                Vector<int[]> cuts=new Vector<int[]>();
                int end=occupied.get(0)[1],removed=0;
                for(int i=1;i<occupied.size();i++) {
                    int[] next=occupied.get(i);
                    int excess=next[0]-end-CHANNEL;
                    if(excess>=GRID) {int remove=excess/GRID*GRID;cuts.add(new int[]{next[0],remove});removed+=remove;}
                    end=Math.max(end,next[1]);
                }
                if (axisRecord != null) safeSetCuts(observation, axisRecord, cuts);
                if(removed==0) {
                    if (axisRecord != null) safeFinishAxis(observation, axisRecord,
                        "NO_CUT", null, null, beforeSignature, null);
                    continue;
                }
                Rectangle outline=new Rectangle(plan.outline.x,plan.outline.y,
                    plan.outline.width-(xAxis?removed:0),plan.outline.height-(xAxis?0:removed));
                Vector<PcbFootprint> moved=new Vector<PcbFootprint>();
                for(PcbFootprint f:plan.footprints) {
                    PcbComponentPlacement p=f.getPlacement();
                    Rectangle envelope=PcbPlacementPlanner.envelope(f,constraints.get(p.getComponentId()).accessMargin);
                    int shift=shift(cuts,xAxis?envelope.x:envelope.y);
                    moved.add(f.translated(p.getX()-(xAxis?shift:0),p.getY()-(xAxis?0:shift)));
                }
                PcbPlacementPlanner.Plan packed=new PcbPlacementPlanner.Plan(outline,moved,plan.regions,
                    plan.courtyardArea,plan.demandArea,plan.candidate,plan.evaluations);
                PcbBoardLayout candidate=packed.materialize();
                for(PcbTraceGeometry trace:result.layout.getTraces()) {
                    int[] xs=trace.getXPoints(),ys=trace.getYPoints();
                    for(int i=0;i<xs.length;i++) {if(xAxis)xs[i]-=shift(cuts,xs[i]);else ys[i]-=shift(cuts,ys[i]);}
                    candidate.addTrace(trace.withPath(xs,ys));
                }
                for(PcbBoardHole hole:result.layout.getHoles()) {
                    int holeShift=shift(cuts,xAxis?hole.x:hole.y);
                    candidate.addHole(hole.translatedBy(xAxis?-holeShift:0,xAxis?0:-holeShift));
                }
                candidate.setRoutingStatistics(result.layout.getRoutingExpansions(),result.layout.getRawRoutingSegments(),result.layout.getRoutingCongestionRejections());
                String proposedSignature = observation == null ? null :
                    observedSignature(observation, candidate);
                if (axisRecord != null)
                    safeSetProposedSignature(observation, axisRecord, proposedSignature);
                try {
                    PcbPlacementPlanner.validate(board,constraints,outline,moved);
                    candidate.validateRoutingGeometry(board);
                    // Retain the per-axis detour rejection here.
                    // A rigid monotone compaction can shorten endpoint separation
                    // faster than the copper path, exceeding the unchanged detour
                    // bound. Retain the original legal route in that case.
                    candidate.validateRouteQuality();
                    result=new Routed(packed,candidate);
                    if (axisRecord != null) safeFinishAxis(observation, axisRecord,
                        "APPLIED", null, null, proposedSignature, proposedSignature);
                } catch(PcbPlacementPlanner.Rejected rejected) {
                    if (axisRecord != null) safeFinishAxis(observation, axisRecord,
                        "PLACEMENT_REJECTED", rejected.getClass().getName(), rejected.reason,
                        beforeSignature, proposedSignature);
                    /* Keep the legal placement. */
                }
                catch(PcbBoardLayout.RouteQualityRejectedException rejected) {
                    if (axisRecord != null) safeFinishAxis(observation, axisRecord,
                        "QUALITY_REJECTED", rejected.getClass().getName(),
                        rejected.getMessage(), beforeSignature, proposedSignature);
                    /* Keep the legal routed input. */
                }
            }
            completed = true;
            return result;
        } finally {
            if (callRecord != null) {
                String outputSignature = completed ?
                    observedSignature(observation, result.layout) : null;
                safeFinishCall(observation, callRecord, completed, outputSignature);
            }
        }
    }

    private static String observedSignature(RoutedObservation observation,
            PcbBoardLayout layout) {
        try {
            return routedPhysicalSignature(layout);
        } catch (RuntimeException failure) {
            observation.markTruncated();
            return null;
        }
    }

    private static CallRecord safeBeginCall(RoutedObservation observation,
            String inputSignature) {
        try {
            return observation.beginCall(inputSignature);
        } catch (RuntimeException failure) {
            observation.markTruncated();
            return null;
        }
    }

    private static AxisRecord safeBeginAxis(RoutedObservation observation,
            CallRecord callRecord, int axis, String inputSignature) {
        if (observation == null || callRecord == null) return null;
        try {
            return observation.beginAxis(callRecord, axis, inputSignature);
        } catch (RuntimeException failure) {
            observation.markTruncated();
            return null;
        }
    }

    private static void safeSetOccupiedCount(RoutedObservation observation,
            AxisRecord axisRecord, int count) {
        try {
            observation.setOccupiedCount(axisRecord, count);
        } catch (RuntimeException failure) {
            observation.markTruncated();
        }
    }

    private static void safeSetCuts(RoutedObservation observation,
            AxisRecord axisRecord, Vector<int[]> cuts) {
        try {
            observation.setCuts(axisRecord, cuts);
        } catch (RuntimeException failure) {
            observation.markTruncated();
        }
    }

    private static void safeSetProposedSignature(RoutedObservation observation,
            AxisRecord axisRecord, String signature) {
        try {
            observation.setProposedSignature(axisRecord, signature);
        } catch (RuntimeException failure) {
            observation.markTruncated();
        }
    }

    private static void safeFinishAxis(RoutedObservation observation,
            AxisRecord axisRecord, String status, String caughtType,
            String caughtMessage, String outputSignature, String proposedSignature) {
        try {
            observation.finishAxis(axisRecord, status, caughtType, caughtMessage,
                outputSignature, proposedSignature);
        } catch (RuntimeException failure) {
            observation.markTruncated();
        }
    }

    private static void safeFinishCall(RoutedObservation observation,
            CallRecord callRecord, boolean completed, String outputSignature) {
        try {
            observation.finishCall(callRecord, completed, outputSignature);
        } catch (RuntimeException failure) {
            observation.markTruncated();
        }
    }

    static final class RoutedObservation {
        private static final int MAX_CALLS = 6;
        private static final int MAX_AXES = 12;
        private static final int MAX_CUTS_PER_AXIS = 256;
        private final String seed;
        private final Vector<CallRecord> calls = new Vector<CallRecord>();
        private int attemptedCalls;
        private int recordedAxes;
        private boolean truncated;

        RoutedObservation(String seed) {
            this.seed = seed;
        }

        private CallRecord beginCall(String inputSignature) {
            attemptedCalls++;
            if (attemptedCalls > MAX_CALLS || calls.size() >= MAX_CALLS) {
                truncated = true;
                return null;
            }
            CallRecord call = new CallRecord(calls.size() + 1, inputSignature);
            calls.add(call);
            return call;
        }

        private AxisRecord beginAxis(CallRecord call, int axis, String inputSignature) {
            recordedAxes++;
            if (recordedAxes > MAX_AXES || call.axes.size() >= 2) {
                truncated = true;
                call.truncated = true;
                return null;
            }
            AxisRecord record = new AxisRecord(axis, inputSignature);
            record.call = call;
            call.axes.add(record);
            return record;
        }

        private void setOccupiedCount(AxisRecord axis, int count) {
            axis.occupiedIntervalCount = count;
        }

        private void setCuts(AxisRecord axis, Vector<int[]> cuts) {
            axis.cutCount = cuts.size();
            int copied = Math.min(cuts.size(), MAX_CUTS_PER_AXIS);
            for (int index = 0; index < copied; index++) {
                int[] cut = cuts.get(index);
                axis.cuts.add(new CutRecord(cut[0], cut[1]));
            }
            axis.cutsTruncated = copied < cuts.size();
            if (axis.cutsTruncated) {
                truncated = true;
                axis.call.truncated = true;
            }
        }

        private void setProposedSignature(AxisRecord axis, String signature) {
            axis.proposedSignature = signature;
        }

        private void finishAxis(AxisRecord axis, String status, String caughtType,
                String caughtMessage, String outputSignature, String proposedSignature) {
            axis.status = status;
            axis.caughtType = caughtType;
            axis.caughtMessage = caughtMessage;
            axis.outputSignature = outputSignature;
            if (proposedSignature != null) axis.proposedSignature = proposedSignature;
        }

        private void finishCall(CallRecord call, boolean completed, String outputSignature) {
            call.complete = completed;
            call.outputSignature = completed ? outputSignature : null;
        }

        private void markTruncated() {
            truncated = true;
        }

        String getSeed() { return seed; }
        boolean isTruncated() { return truncated; }
        int getCallCount() { return calls.size(); }

        Vector<Integer> matchingCompletedCallOrdinals(String signature) {
            Vector<Integer> result = new Vector<Integer>();
            if (signature == null) return result;
            for (CallRecord call : calls)
                if (call.complete && signature.equals(call.outputSignature))
                    result.add(Integer.valueOf(call.ordinal));
            return result;
        }

        Vector<Integer> incompleteCallOrdinals() {
            Vector<Integer> result = new Vector<Integer>();
            for (CallRecord call : calls)
                if (!call.complete) result.add(Integer.valueOf(call.ordinal));
            return result;
        }

        Vector<String> getEventJsonLines() {
            Vector<String> result = new Vector<String>();
            for (CallRecord call : calls) {
                result.add(call.toJson(seed, truncated));
                for (AxisRecord axis : call.axes)
                    result.add(axis.toJson(seed, call.ordinal));
            }
            return result;
        }
    }

    private static final class CallRecord {
        final int ordinal;
        final String inputSignature;
        final Vector<AxisRecord> axes = new Vector<AxisRecord>();
        String outputSignature;
        boolean complete;
        boolean truncated;

        CallRecord(int ordinal, String inputSignature) {
            this.ordinal = ordinal;
            this.inputSignature = inputSignature;
        }

        String toJson(String seed, boolean observerTruncated) {
            StringBuilder json = new StringBuilder(220);
            json.append("{\"schema\":\"PCB_ROUTED_STRIP_EVENT@1\",\"kind\":\"CALL\"");
            json.append(",\"seed\":");
            appendJsonString(json, seed);
            json.append(",\"callOrdinal\":").append(ordinal);
            json.append(",\"status\":\"").append(complete ? "COMPLETE" : "INCOMPLETE").append('"');
            json.append(",\"inputSignature\":");
            appendJsonStringOrNull(json, inputSignature);
            json.append(",\"outputSignature\":");
            appendJsonStringOrNull(json, outputSignature);
            json.append(",\"axisCount\":").append(axes.size());
            json.append(",\"truncated\":").append(truncated || observerTruncated).append('}');
            return json.toString();
        }
    }

    private static final class AxisRecord {
        final int axisOrdinal;
        final String axis;
        final String inputSignature;
        final Vector<CutRecord> cuts = new Vector<CutRecord>();
        CallRecord call;
        String status = "PENDING";
        String proposedSignature;
        String outputSignature;
        String caughtType;
        String caughtMessage;
        int occupiedIntervalCount;
        int cutCount;
        boolean cutsTruncated;

        AxisRecord(int axis, String inputSignature) {
            axisOrdinal = axis;
            this.axis = axis == 0 ? "X" : "Y";
            this.inputSignature = inputSignature;
        }

        String toJson(String seed, int callOrdinal) {
            StringBuilder json = new StringBuilder(420);
            json.append("{\"schema\":\"PCB_ROUTED_STRIP_EVENT@1\",\"kind\":\"AXIS\"");
            json.append(",\"seed\":");
            appendJsonString(json, seed);
            json.append(",\"callOrdinal\":").append(callOrdinal);
            json.append(",\"axisOrdinal\":").append(axisOrdinal);
            json.append(",\"axis\":\"").append(axis).append('"');
            json.append(",\"status\":\"").append(status).append('"');
            json.append(",\"occupiedIntervalCount\":").append(occupiedIntervalCount);
            json.append(",\"cutCount\":").append(cutCount);
            json.append(",\"cutsTruncated\":").append(cutsTruncated);
            json.append(",\"cuts\":[");
            for (int index = 0; index < cuts.size(); index++) {
                if (index != 0) json.append(',');
                CutRecord cut = cuts.get(index);
                json.append('[').append(cut.start).append(',').append(cut.amount).append(']');
            }
            json.append("],\"inputSignature\":");
            appendJsonStringOrNull(json, inputSignature);
            json.append(",\"proposedSignature\":");
            appendJsonStringOrNull(json, proposedSignature);
            json.append(",\"outputSignature\":");
            appendJsonStringOrNull(json, outputSignature);
            json.append(",\"caughtType\":");
            appendJsonStringOrNull(json, caughtType);
            json.append(",\"caughtMessage\":");
            appendJsonStringOrNull(json, caughtMessage);
            json.append('}');
            return json.toString();
        }
    }

    private static final class CutRecord {
        final int start;
        final int amount;
        CutRecord(int start, int amount) {
            this.start = start;
            this.amount = amount;
        }
    }

    private static void appendJsonStringOrNull(StringBuilder json, String value) {
        if (value == null) json.append("null");
        else appendJsonString(json, value);
    }

    private static void appendJsonString(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' || ch == '\\') json.append('\\').append(ch);
            else if (ch == '\n') json.append("\\n");
            else if (ch == '\r') json.append("\\r");
            else if (ch == '\t') json.append("\\t");
            else if (ch < 0x20) {
                json.append("\\u00").append(Character.forDigit((ch >> 4) & 0xf, 16))
                    .append(Character.forDigit(ch & 0xf, 16));
            } else json.append(ch);
        }
        json.append('"');
    }

    private static int shift(Vector<int[]> cuts,int coordinate) {
        int result=0;for(int[] cut:cuts)if(coordinate>=cut[0])result+=cut[1];return result;
    }

    private static PcbPlacementPlanner.Plan compactAxis(PcbPlacementConstraints constraints,
            PcbPlacementPlanner.Plan plan, final int axis) {
        Vector<int[]> intervals = new Vector<int[]>();
        for (PcbFootprint part : plan.footprints) {
            Rectangle box = PcbPlacementPlanner.envelope(part,
                constraints.get(part.getPlacement().getComponentId()).accessMargin);
            int start = axis == 0 ? box.x : box.y;
            intervals.add(new int[] { start, start + (axis == 0 ? box.width : box.height) });
        }
        Collections.sort(intervals, new Comparator<int[]>() {
            public int compare(int[] a, int[] b) { return a[0] < b[0] ? -1 : a[0] == b[0] ? 0 : 1; }
        });
        Vector<int[]> cuts = new Vector<int[]>();
        int end = intervals.get(0)[1], removed = 0;
        for (int i = 1; i < intervals.size(); i++) {
            int[] interval = intervals.get(i);
            if (interval[0] > end + CHANNEL) {
                int amount = (interval[0] - end - CHANNEL) / 10 * 10;
                if (amount > 0) { cuts.add(new int[] { interval[0], amount }); removed += amount; }
            }
            end = Math.max(end, interval[1]);
        }
        if (removed == 0) return plan;
        Vector<PcbFootprint> parts = new Vector<PcbFootprint>();
        for (PcbFootprint part : plan.footprints) {
            Rectangle box = PcbPlacementPlanner.envelope(part,
                constraints.get(part.getPlacement().getComponentId()).accessMargin);
            int shift = 0, start = axis == 0 ? box.x : box.y;
            for (int[] cut : cuts) if (start >= cut[0]) shift += cut[1];
            PcbComponentPlacement p = part.getPlacement();
            parts.add(part.translated(p.getX() - (axis == 0 ? shift : 0),
                p.getY() - (axis == 1 ? shift : 0)));
        }
        Rectangle outline = new Rectangle(plan.outline);
        if (axis == 0) outline.width -= removed; else outline.height -= removed;
        return new PcbPlacementPlanner.Plan(outline, parts, plan.regions,
            plan.courtyardArea, plan.demandArea, plan.candidate,
            plan.evaluations + parts.size());
    }
}
