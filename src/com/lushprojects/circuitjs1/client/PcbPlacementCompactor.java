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
    /** Contract the already legal route instead of asking the router to find
     * that topology again. Whole package envelopes translate rigidly. Every
     * copper bend/rail is protected, so the monotone maps preserve Manhattan
     * connectivity and routing clearance. Original input remains untouched. */
    static Routed compactRouted(TroubleshootBoard board,PcbPlacementConstraints constraints,
            PcbPlacementPlanner.Plan input,PcbBoardLayout routed) {
        Routed result=new Routed(input,routed);
        for(int axis=0;axis<2;axis++) {
            boolean xAxis=axis==0;
            PcbPlacementPlanner.Plan plan=result.plan;
            Vector<int[]> occupied=new Vector<int[]>();
            for(PcbFootprint f:plan.footprints) {
                Rectangle e=PcbPlacementPlanner.envelope(f,constraints.get(f.getPlacement().getComponentId()).accessMargin);
                occupied.add(new int[]{xAxis?e.x:e.y,xAxis?e.x+e.width:e.y+e.height});
            }
            // Parallel rails and all terminal/branch endpoints keep at least
            // their full centerline clearance, even inside a furniture-sized gap.
            int guard=PcbTraceRules.MIN_CENTERLINE_CLEARANCE;
            for(PcbTraceGeometry trace:result.layout.getTraces()) {
                int[] points=xAxis?trace.getXPoints():trace.getYPoints();
                for(int point:points)occupied.add(new int[]{point-guard,point+guard});
            }
            Collections.sort(occupied,new Comparator<int[]>() {public int compare(int[] a,int[] b){return a[0]-b[0];}});
            Vector<int[]> cuts=new Vector<int[]>();
            int end=occupied.get(0)[1],removed=0;
            for(int i=1;i<occupied.size();i++) {
                int[] next=occupied.get(i);
                int excess=next[0]-end-CHANNEL;
                if(excess>=GRID) {int remove=excess/GRID*GRID;cuts.add(new int[]{next[0],remove});removed+=remove;}
                end=Math.max(end,next[1]);
            }
            if(removed==0)continue;
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
            candidate.setRoutingStatistics(result.layout.getRoutingExpansions(),result.layout.getRawRoutingSegments(),result.layout.getRoutingCongestionRejections());
            try {
                PcbPlacementPlanner.validate(board,constraints,outline,moved);
                // Full routing geometry is validated by the caller after both
                // bounded transforms; retain the per-axis detour rejection here.
                // A rigid monotone compaction can shorten endpoint separation
                // faster than the copper path, exceeding the unchanged detour
                // bound. Retain the original legal route in that case.
                candidate.validateRouteQuality();
                result=new Routed(packed,candidate);
            } catch(PcbPlacementPlanner.Rejected rejected) { /* Keep the legal placement. */ }
            catch(PcbBoardLayout.RouteQualityRejectedException rejected) { /* Keep the legal routed input. */ }
        }
        return result;
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
