package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Immutable bent-lead appearance in physical board coordinates. This is a
 * bounded 2D pose for an already detached terminal, not a change to its package,
 * pad, conductor, mounting identity or electrical connection. Viewing the other
 * face does not choose a different pose. Render and probe consumers share it.
 */
final class WorkbenchLiftedLeadAppearance {
    private static final int LEAD_HALF_WIDTH = 3;
    private static final int[] STANDOFFS = {12, 20, 28};
    private static final int[] BENDS = {24, 36, 48};
    private final Point body, bend, tip;
    private final Rectangle probe, leadBounds;
    private final int padOverlaps, bodyOverlaps, traceOverlaps;

    private WorkbenchLiftedLeadAppearance(Point body, Point bend, Point tip,
            Rectangle probe, int pads, int bodies, int traces) {
        this.body = new Point(body); this.bend = new Point(bend); this.tip = new Point(tip);
        this.probe = new Rectangle(probe);
        leadBounds = stroke(body, bend).union(stroke(bend, tip)).union(probe);
        padOverlaps = pads; bodyOverlaps = bodies; traceOverlaps = traces;
    }

    /**
     * Candidates leave a package edge, then turn sideways. Nearest edges are
     * considered first; equal edges use their direction toward the original pad,
     * then package-local left/top/right/bottom order. Clearance ties keep the
     * first candidate. At most 72 poses are evaluated, never a routing search.
     * If all poses are obstructed, the least-pad-overlapping pose wins before
     * body/trace overlap. These diagnostics describe appearance only.
     */
    static WorkbenchLiftedLeadAppearance project(PhysicalPackageGeometry geometry,
            PcbPackagePose pose, int terminal, Iterable<Rectangle> padKeepouts,
            Iterable<Rectangle> traceBounds, Iterable<Rectangle> otherBodies) {
        if (geometry == null || pose == null || geometry.getTerminal(terminal) == null ||
                padKeepouts == null || traceBounds == null || otherBodies == null)
            throw new IllegalArgumentException("Incomplete lifted-lead appearance input");
        PhysicalPackageGeometry.Terminal declared = geometry.getTerminal(terminal);
        Point localBody = declared.getConnectedLead().getBodyPoint();
        Point pad = declared.getPadCenter();
        Rectangle bodyBounds = geometry.getBodyBounds();
        Rectangle originalProbe = declared.getComponentLeadProbeBounds(true);
        Vector<Rectangle> pads = copyBounds(padKeepouts), traces = copyBounds(traceBounds),
            bodies = copyBounds(otherBodies);
        int[][] normals = {{-1,0},{0,-1},{1,0},{0,1}};
        // Insertion sort keeps a canonical tie order and needs no hash iteration.
        for (int i = 1; i < normals.length; i++) {
            int[] direction = normals[i]; int j = i;
            while (j > 0 && before(direction, normals[j - 1], localBody, pad, bodyBounds)) {
                normals[j] = normals[j - 1]; j--;
            }
            normals[j] = direction;
        }
        WorkbenchLiftedLeadAppearance best = null;
        Point boardBody = pose.toBoardPoint(localBody, geometry.getWidth(), geometry.getHeight());
        for (int[] normal : normals) for (int standoff : STANDOFFS) for (int length : BENDS)
            for (int sign : new int[] {1, -1}) {
                Point exit = exit(localBody, bodyBounds, normal);
                Point localBend = new Point(exit.x + normal[0] * standoff,
                    exit.y + normal[1] * standoff);
                Point localTip = new Point(localBend.x - normal[1] * sign * length,
                    localBend.y + normal[0] * sign * length);
                Point bend, tip; Rectangle probe;
                try {
                    bend = pose.toBoardPoint(localBend, geometry.getWidth(), geometry.getHeight());
                    tip = pose.toBoardPoint(localTip, geometry.getWidth(), geometry.getHeight());
                    probe = pose.toBoardRectangle(new Rectangle(localTip.x - originalProbe.width / 2,
                        localTip.y - originalProbe.height / 2, originalProbe.width, originalProbe.height),
                        geometry.getWidth(), geometry.getHeight());
                    PcbCoordinateSystem.requireBoardRectangle(stroke(boardBody, bend));
                    PcbCoordinateSystem.requireBoardRectangle(stroke(bend, tip));
                } catch (IllegalArgumentException outsideCoordinateDomain) { continue; }
                Rectangle first = stroke(boardBody, bend), second = stroke(bend, tip);
                WorkbenchLiftedLeadAppearance candidate = new WorkbenchLiftedLeadAppearance(
                    boardBody, bend, tip, probe, overlaps(pads, first, second, probe),
                    overlaps(bodies, first, second, probe), overlaps(traces, first, second, probe));
                if (best == null || candidate.betterThan(best)) best = candidate;
                if (candidate.padOverlaps == 0 && candidate.bodyOverlaps == 0 && candidate.traceOverlaps == 0)
                    return candidate;
            }
        if (best == null) throw new IllegalArgumentException("No lifted-lead pose in board coordinate domain");
        return best;
    }

    Point getBodyPoint() { return new Point(body); }
    Point getBendPoint() { return new Point(bend); }
    Point getTipPoint() { return new Point(tip); }
    Rectangle getProbeBounds() { return new Rectangle(probe); }
    Rectangle getLeadBounds() { return new Rectangle(leadBounds); }
    Vector<Point> getPath() {
        Vector<Point> result = new Vector<Point>();
        result.add(getBodyPoint()); result.add(getBendPoint()); result.add(getTipPoint()); return result;
    }
    Vector<Rectangle> getLeadSegments() {
        Vector<Rectangle> result = new Vector<Rectangle>();
        result.add(stroke(body, bend)); result.add(stroke(bend, tip)); return result;
    }
    int getPadOverlaps() { return padOverlaps; }
    int getBodyOverlaps() { return bodyOverlaps; }
    int getTraceOverlaps() { return traceOverlaps; }

    private boolean betterThan(WorkbenchLiftedLeadAppearance other) {
        if (padOverlaps != other.padOverlaps) return padOverlaps < other.padOverlaps;
        if (bodyOverlaps != other.bodyOverlaps) return bodyOverlaps < other.bodyOverlaps;
        return traceOverlaps < other.traceOverlaps;
    }
    private static boolean before(int[] a, int[] b, Point origin, Point pad, Rectangle body) {
        int da = distance(origin, exit(origin, body, a)), db = distance(origin, exit(origin, body, b));
        if (da != db) return da < db;
        long dotA = (long)a[0] * (pad.x - origin.x) + (long)a[1] * (pad.y - origin.y);
        long dotB = (long)b[0] * (pad.x - origin.x) + (long)b[1] * (pad.y - origin.y);
        return dotA > dotB;
    }
    private static int distance(Point a, Point b) { return Math.abs(a.x - b.x) + Math.abs(a.y - b.y); }
    private static Point exit(Point from, Rectangle body, int[] normal) {
        if (normal[0] < 0) return new Point(Math.min(from.x, body.x), from.y);
        if (normal[0] > 0) return new Point(Math.max(from.x, body.x + body.width), from.y);
        if (normal[1] < 0) return new Point(from.x, Math.min(from.y, body.y));
        return new Point(from.x, Math.max(from.y, body.y + body.height));
    }
    private static Rectangle stroke(Point first, Point second) {
        return new Rectangle(Math.min(first.x, second.x) - LEAD_HALF_WIDTH,
            Math.min(first.y, second.y) - LEAD_HALF_WIDTH,
            Math.abs(first.x - second.x) + 2 * LEAD_HALF_WIDTH,
            Math.abs(first.y - second.y) + 2 * LEAD_HALF_WIDTH);
    }
    private static int overlaps(Vector<Rectangle> obstacles, Rectangle first, Rectangle second, Rectangle tip) {
        int count = 0;
        for (Rectangle obstacle : obstacles)
            if (first.intersects(obstacle) || second.intersects(obstacle) || tip.intersects(obstacle)) count++;
        return count;
    }
    private static Vector<Rectangle> copyBounds(Iterable<Rectangle> input) {
        Vector<Rectangle> result = new Vector<Rectangle>();
        for (Rectangle value : input) {
            PcbCoordinateSystem.requireBoardRectangle(value); result.add(new Rectangle(value));
        }
        return result;
    }
}
