package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/** Removes empty placement strips without shrinking parts, probe targets or access envelopes. */
final class PcbPlacementCompactor {
    private static final int CHANNEL = 20;
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
