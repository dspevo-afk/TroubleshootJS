package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/** Independent unit-centerline connectivity witness for explicitly layered copper. */
final class P07LayeredRasterOracle {
    private P07LayeredRasterOracle() { }

    static Metrics verify(PcbBoardLayout layout, TroubleshootBoard board) {
        if (layout == null || board == null)
            throw new IllegalArgumentException("Missing layered raster input");

        Vector<String> padIds = board.getPadIds();
        Collections.sort(padIds);
        HashMap<String, PcbPadPlacement> placements = new HashMap<String, PcbPadPlacement>();
        for (PcbPadPlacement placement : layout.getPads()) {
            if (placement == null || placements.put(placement.getPadId(), placement) != null)
                throw new IllegalStateException("Duplicate or missing physical pad placement");
        }
        if (placements.size() != padIds.size())
            throw new IllegalStateException("Physical layout and board pad inventories differ");
        for (String padId : padIds)
            if (!placements.containsKey(padId))
                throw new IllegalStateException("Physical layout is missing board pad " + padId);

        DisjointSet copper = new DisjointSet();
        HashMap<FacePoint, String> copperNets = new HashMap<FacePoint, String>();
        HashMap<FacePoint, String> traceNets = new HashMap<FacePoint, String>();
        HashMap<String, Vector<FacePoint>> padCopper = new HashMap<String, Vector<FacePoint>>();
        long platedPadBarrels = 0;

        for (String padId : padIds) {
            BoardPad pad = board.getPad(padId);
            PcbPadPlacement placement = placements.get(padId);
            if (pad == null || board.getNet(pad.getNetId()) == null)
                throw new IllegalStateException("Board pad references an unknown net: " + padId);
            Vector<FacePoint> points = new Vector<FacePoint>();
            FacePoint top = new FacePoint(PcbCopperLayer.TOP, placement.getX(), placement.getY());
            FacePoint bottom = new FacePoint(PcbCopperLayer.BOTTOM, placement.getX(), placement.getY());
            if (placement.getAttachment() == PcbTerminalAttachment.PLATED_THROUGH_HOLE) {
                addCopperPoint(copper, copperNets, top, pad.getNetId(), "pad " + padId);
                addCopperPoint(copper, copperNets, bottom, pad.getNetId(), "pad " + padId);
                copper.union(top, bottom);
                points.add(top);
                points.add(bottom);
                platedPadBarrels++;
            } else {
                FacePoint facePoint = placement.getMountingSide() == PcbBoardSide.TOP ? top : bottom;
                addCopperPoint(copper, copperNets, facePoint, pad.getNetId(), "pad " + padId);
                points.add(facePoint);
            }
            padCopper.put(padId, points);
        }

        long traceUnitSteps = 0;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            if (trace == null || trace.getLayer() == null || board.getNet(trace.getNetId()) == null)
                throw new IllegalStateException("Trace references an unknown net or layer");
            int[] xs = trace.getXPoints();
            int[] ys = trace.getYPoints();
            FacePoint prior = new FacePoint(trace.getLayer(), xs[0], ys[0]);
            addTracePoint(copper, copperNets, traceNets, prior, trace.getNetId());
            for (int segment = 1; segment < xs.length; segment++) {
                long dx = (long) xs[segment] - xs[segment - 1];
                long dy = (long) ys[segment] - ys[segment - 1];
                if (dx != 0 && dy != 0)
                    throw new IllegalStateException("Raster oracle requires Manhattan trace segments");
                long steps = Math.abs(dx) + Math.abs(dy);
                long stepX = dx == 0 ? 0 : (dx > 0 ? 1 : -1);
                long stepY = dy == 0 ? 0 : (dy > 0 ? 1 : -1);
                long x = xs[segment - 1];
                long y = ys[segment - 1];
                for (long step = 0; step < steps; step++) {
                    x += stepX;
                    y += stepY;
                    FacePoint next = new FacePoint(trace.getLayer(), (int) x, (int) y);
                    addTracePoint(copper, copperNets, traceNets, next, trace.getNetId());
                    copper.union(prior, next);
                    prior = next;
                    traceUnitSteps++;
                }
            }
        }

        long viaBarrels = 0;
        long platedHoleBarrels = 0;
        for (PcbBoardHole hole : layout.getHoles()) {
            if (hole.kind == PcbBoardHole.Kind.NON_PLATED) continue;
            if (board.getNet(hole.netId) == null)
                throw new IllegalStateException("Plated hole references an unknown net: " + hole.id);
            FacePoint top = new FacePoint(PcbCopperLayer.TOP, hole.x, hole.y);
            FacePoint bottom = new FacePoint(PcbCopperLayer.BOTTOM, hole.x, hole.y);
            String topNet = traceNets.get(top);
            String bottomNet = traceNets.get(bottom);
            if (topNet == null || bottomNet == null)
                throw new IllegalStateException("Plated hole center does not contact trace copper on both faces: " +
                    hole.id);
            if (!hole.netId.equals(topNet) || !hole.netId.equals(bottomNet))
                throw new IllegalStateException("Plated hole net disagrees with trace copper at its center: " +
                    hole.id);
            addCopperPoint(copper, copperNets, top, hole.netId, "plated hole " + hole.id);
            addCopperPoint(copper, copperNets, bottom, hole.netId, "plated hole " + hole.id);
            copper.union(top, bottom);
            if (hole.kind == PcbBoardHole.Kind.VIA) viaBarrels++;
            else platedHoleBarrels++;
        }

        long packageInternalLinks = joinDeclaredPackageLinks(board, padCopper, copper);
        long orderedPadPairs = 0;
        for (String firstId : padIds) {
            BoardPad first = board.getPad(firstId);
            FacePoint firstPoint = padCopper.get(firstId).firstElement();
            for (String secondId : padIds) {
                BoardPad second = board.getPad(secondId);
                FacePoint secondPoint = padCopper.get(secondId).firstElement();
                boolean physicallyConnected = copper.connected(firstPoint, secondPoint);
                boolean sameBoardNet = first.getNetId().equals(second.getNetId());
                if (physicallyConnected != sameBoardNet)
                    throw new IllegalStateException("Raster connectivity disagrees with board net partition: " +
                        firstId + " -> " + secondId);
                orderedPadPairs++;
            }
        }

        return new Metrics(copper.size(), orderedPadPairs, traceUnitSteps,
            platedPadBarrels, viaBarrels, platedHoleBarrels, packageInternalLinks);
    }

    private static long joinDeclaredPackageLinks(TroubleshootBoard board,
            Map<String, Vector<FacePoint>> padCopper, DisjointSet copper) {
        long links = 0;
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (String componentId : componentIds) {
            BoardComponent component = board.getComponent(componentId);
            PhysicalPackage physicalPackage = component.getPhysicalPackage();
            Vector<String> componentPadIds = component.getPadIds();
            Collections.sort(componentPadIds);
            for (int first = 0; first < componentPadIds.size(); first++) {
                BoardPad firstPad = board.getPad(componentPadIds.get(first));
                for (int second = first + 1; second < componentPadIds.size(); second++) {
                    BoardPad secondPad = board.getPad(componentPadIds.get(second));
                    if (!physicalPackage.isInternallyConnected(firstPad.getTerminalId(),
                            secondPad.getTerminalId())) continue;
                    Vector<FacePoint> firstPoints = padCopper.get(firstPad.getId());
                    Vector<FacePoint> secondPoints = padCopper.get(secondPad.getId());
                    if (firstPoints == null || secondPoints == null)
                        throw new IllegalStateException("Package internal link lacks physical terminal copper");
                    FacePoint anchor = firstPoints.firstElement();
                    for (FacePoint point : firstPoints) copper.union(anchor, point);
                    for (FacePoint point : secondPoints) copper.union(anchor, point);
                    links++;
                }
            }
        }
        return links;
    }

    private static void addTracePoint(DisjointSet copper, Map<FacePoint, String> copperNets,
            Map<FacePoint, String> traceNets, FacePoint point, String netId) {
        addCopperPoint(copper, copperNets, point, netId, "trace");
        String prior = traceNets.get(point);
        if (prior != null && !prior.equals(netId))
            throw new IllegalStateException("Different-net traces overlap on the same copper face");
        traceNets.put(point, netId);
    }

    private static void addCopperPoint(DisjointSet copper, Map<FacePoint, String> copperNets,
            FacePoint point, String netId, String owner) {
        String prior = copperNets.get(point);
        if (prior != null && !prior.equals(netId))
            throw new IllegalStateException("Different-net copper occupies one face point: " + owner);
        copperNets.put(point, netId);
        copper.add(point);
    }

    static final class Metrics {
        private final long rasterPointCount;
        private final long orderedPadPairs;
        private final long traceUnitSteps;
        private final long platedPadBarrels;
        private final long viaBarrels;
        private final long platedHoleBarrels;
        private final long packageInternalLinks;

        private Metrics(long rasterPointCount, long orderedPadPairs, long traceUnitSteps,
                long platedPadBarrels, long viaBarrels, long platedHoleBarrels,
                long packageInternalLinks) {
            this.rasterPointCount = rasterPointCount;
            this.orderedPadPairs = orderedPadPairs;
            this.traceUnitSteps = traceUnitSteps;
            this.platedPadBarrels = platedPadBarrels;
            this.viaBarrels = viaBarrels;
            this.platedHoleBarrels = platedHoleBarrels;
            this.packageInternalLinks = packageInternalLinks;
        }

        long getRasterPointCount() { return rasterPointCount; }
        long getOrderedPadPairs() { return orderedPadPairs; }
        long getTraceUnitSteps() { return traceUnitSteps; }
        long getPlatedPadBarrels() { return platedPadBarrels; }
        long getViaBarrels() { return viaBarrels; }
        long getPlatedHoleBarrels() { return platedHoleBarrels; }
        long getPackageInternalLinks() { return packageInternalLinks; }
    }

    private static final class FacePoint {
        private final PcbCopperLayer layer;
        private final int x;
        private final int y;

        FacePoint(PcbCopperLayer layer, int x, int y) {
            if (layer == null) throw new IllegalArgumentException("Missing raster copper face");
            this.layer = layer;
            this.x = x;
            this.y = y;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof FacePoint)) return false;
            FacePoint point = (FacePoint) other;
            return layer == point.layer && x == point.x && y == point.y;
        }

        @Override public int hashCode() {
            int result = layer.hashCode();
            result = 31 * result + x;
            return 31 * result + y;
        }
    }

    private static final class DisjointSet {
        private final HashMap<FacePoint, FacePoint> parents =
            new HashMap<FacePoint, FacePoint>();
        private final HashMap<FacePoint, Integer> ranks = new HashMap<FacePoint, Integer>();

        void add(FacePoint point) {
            if (parents.containsKey(point)) return;
            parents.put(point, point);
            ranks.put(point, Integer.valueOf(0));
        }

        FacePoint find(FacePoint point) {
            FacePoint parent = parents.get(point);
            if (parent == null) throw new IllegalStateException("Raster point was not registered");
            if (!parent.equals(point)) {
                parent = find(parent);
                parents.put(point, parent);
            }
            return parent;
        }

        void union(FacePoint first, FacePoint second) {
            FacePoint firstRoot = find(first);
            FacePoint secondRoot = find(second);
            if (firstRoot.equals(secondRoot)) return;
            int firstRank = ranks.get(firstRoot).intValue();
            int secondRank = ranks.get(secondRoot).intValue();
            if (firstRank < secondRank) parents.put(firstRoot, secondRoot);
            else if (firstRank > secondRank) parents.put(secondRoot, firstRoot);
            else {
                parents.put(secondRoot, firstRoot);
                ranks.put(firstRoot, Integer.valueOf(firstRank + 1));
            }
        }

        boolean connected(FacePoint first, FacePoint second) {
            return find(first).equals(find(second));
        }

        int size() { return parents.size(); }
    }
}
