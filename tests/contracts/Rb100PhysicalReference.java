package com.lushprojects.circuitjs1.client;

import java.util.TreeSet;
import java.util.Vector;

/** Independent, test-only physical reference checks; never an admission oracle. */
final class Rb100PhysicalReference {
    private static final int EXPECTED_COMPONENTS = 100;
    private static final int EXPECTED_COURTYARD_PAIRS = 4950;
    private static final int MAX_ACCESS_CELLS = 700000;

    private Rb100PhysicalReference() { }

    static P03Metrics verifyLiteralP03(TroubleshootBoard board,
            PcbBoardLayout layout) {
        Vector<PcbComponentPlacement> components = layout.getComponents();
        Vector<PcbPadPlacement> pads = layout.getPads();
        Rectangle outline = layout.getBoardOutline();
        require(components.size() == EXPECTED_COMPONENTS,
            "literal P03 check receives all 100 package placements");
        int courtyardPairs = 0;
        long escapeTargets = 0;
        for (int i = 0; i < components.size(); i++) {
            PcbComponentPlacement first = components.get(i);
            Rectangle a = first.getRoutingCourtyard();
            require(a.x >= outline.x && a.y >= outline.y &&
                (long)a.x + a.width <= (long)outline.x + outline.width &&
                (long)a.y + a.height <= (long)outline.y + outline.height,
                "literal package routing courtyard is contained by outline: " + first.getComponentId());
            for (PcbPadPlacement pad : pads) {
                BoardPad logical = board.getPad(pad.getPadId());
                if (!first.getComponentId().equals(logical.getComponentId())) continue;
                for (int j = 0; j < components.size(); j++) {
                    if (j == i) continue;
                    Rectangle other = components.get(j).getRoutingCourtyard();
                    int escapeX = pad.getX() + pad.getEscapeDx() * pad.getEscapeLength();
                    int escapeY = pad.getY() + pad.getEscapeDy() * pad.getEscapeLength();
                    require(escapeX < other.x || escapeX > (long)other.x + other.width ||
                        escapeY < other.y || escapeY > (long)other.y + other.height,
                        "literal P03 escape tip is outside every other courtyard for " + pad.getPadId());
                    escapeTargets++;
                }
            }
            for (int j = i + 1; j < components.size(); j++) {
                Rectangle b = components.get(j).getRoutingCourtyard();
                require((long)a.x + a.width <= b.x || (long)b.x + b.width <= a.x ||
                    (long)a.y + a.height <= b.y || (long)b.y + b.height <= a.y,
                    "literal P03 pairwise courtyard disjointness: " + first.getComponentId() +
                        "/" + components.get(j).getComponentId());
                courtyardPairs++;
            }
        }
        long expectedEscapeTargets = (long)pads.size() * (components.size() - 1);
        require(courtyardPairs == EXPECTED_COURTYARD_PAIRS,
            "all 4,950 unordered package-courtyard pairs were checked");
        require(escapeTargets == expectedEscapeTargets,
            "all N by 99 other-courtyard escape targets were checked");
        return new P03Metrics(courtyardPairs, escapeTargets);
    }

    static AccessMetrics verifySingleFaceAccessAndLayer(TroubleshootBoard board,
            PcbBoardLayout layout) {
        PcbCopperLayer routingLayer = board.getPlacementConstraints().routingLayer;
        require(routingLayer != null, "board retains a declared routing layer");
        int secondFaceTraces = 0;
        TreeSet<PcbCopperLayer> traceLayers = new TreeSet<PcbCopperLayer>();
        for (PcbTraceGeometry trace : layout.getTraces()) {
            traceLayers.add(trace.getLayer());
            if (trace.getLayer() != routingLayer) secondFaceTraces++;
        }
        require(!layout.getTraces().isEmpty() && traceLayers.size() == 1 &&
            traceLayers.contains(routingLayer) && secondFaceTraces == 0,
            "all routed copper uses the one declared face");
        int vias = 0;
        int platedHoles = 0;
        for (PcbBoardHole hole : layout.getHoles()) {
            if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
            if (hole.kind != PcbBoardHole.Kind.NON_PLATED) platedHoles++;
        }
        require(vias == 0 && platedHoles == 0,
            "single-face layout contains no vias or plated holes; non-plated mounting holes are allowed");

        for (PcbPadPlacement pad : layout.getPads()) {
            SupportedEnvelope.current().requireAccess(pad);
        }

        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        for (PcbComponentPlacement placement : layout.getComponents()) {
            BoardComponent component = board.getComponent(placement.getComponentId());
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            for (String padId : component.getPadIds()) pads.add(layout.getPad(padId));
            footprints.add(new PcbFootprint(placement, pads));
        }
        int freeCells = PcbAccessPlanner.validate(layout.getBoardOutline(), footprints,
            board.getPlacementConstraints().getTraceWidthProfile().maximumWidth());
        require(freeCells > 0 && freeCells <= MAX_ACCESS_CELLS,
            "actual shared free-channel BFS stays within its 700000 cell cap");
        return new AccessMetrics(freeCells, vias);
    }

    static String requireActualEnvelopeRejection(TroubleshootBoard board,
            PcbBoardLayout layout) {
        try {
            SupportedEnvelope.current().requireBounds(board, layout);
        } catch (SupportedEnvelope.Rejected expected) {
            require(expected.reason == SupportedEnvelope.Reason.BOARD_SIZE ||
                expected.reason == SupportedEnvelope.Reason.POPULATION,
                "actual 100-part result is rejected by the current envelope's size/population boundary");
            return expected.reason.name();
        }
        throw new AssertionError("current P09 envelope admitted the unpublished 100-package result");
    }

    static P08Metrics verifyP08Parity(PcbConductorGraph production,
            PcbConductorGraph reference, PcbConductorBuilder.Statistics productionStats) {
        require(production != null && reference != null,
            "production and all-pairs reference graph builders both accept the actual layout");
        require(production.toCanonical().equals(reference.toCanonical()),
            "production/reference canonical junction, edge, provenance, surface and pad parity");
        long segments = productionStats.segments;
        long lands = productionStats.lands;
        long segmentPairs = segments * (segments - 1) / 2;
        long segmentLandPairs = segments * lands;
        long landPairs = lands * (lands - 1) / 2;
        require(productionStats.referencePairs == segmentPairs + segmentLandPairs + landPairs,
            "production conductor statistics report the exact S*(S-1)/2 + S*L + L*(L-1)/2 work");
        require(P08ReferenceConductorBuilder.segmentPairs == segmentPairs,
            "P08 reference scanned exactly S*(S-1)/2 segment pairs");
        require(P08ReferenceConductorBuilder.segmentLandPairs == segmentLandPairs,
            "P08 reference scanned exactly S*L segment-land pairs");
        require(P08ReferenceConductorBuilder.landPairs == landPairs,
            "P08 reference scanned exactly L*(L-1)/2 land pairs");
        return new P08Metrics(P08ReferenceConductorBuilder.segmentPairs,
            P08ReferenceConductorBuilder.segmentLandPairs,
            P08ReferenceConductorBuilder.landPairs);
    }

    static long verifyClearanceParity(Vector<PcbTraceGeometry> traces) {
        boolean productionAccepted = true;
        RuntimeException productionError = null;
        try { PcbTraceClearance.validate(traces); }
        catch (RuntimeException failure) { productionAccepted = false; productionError = failure; }
        boolean referenceAccepted = true;
        RuntimeException referenceError = null;
        long comparisons = 0;
        try { comparisons = P08ScalabilityContractTest.referenceClearance(traces); }
        catch (RuntimeException failure) { referenceAccepted = false; referenceError = failure; }
        require(productionAccepted == referenceAccepted,
            "production and P08 independent exact-clearance acceptance parity" +
                (productionError == null ? "" : ": " + productionError.getMessage()) +
                (referenceError == null ? "" : " / " + referenceError.getMessage()));
        require(productionAccepted,
            "actual routed layout passes both production and independent clearance oracles");
        return comparisons;
    }


    static final class P03Metrics {
        final int courtyardPairs;
        final long escapeTargets;
        P03Metrics(int courtyardPairs, long escapeTargets) {
            this.courtyardPairs = courtyardPairs; this.escapeTargets = escapeTargets;
        }
    }
    static final class AccessMetrics {
        final int freeCells, vias;
        AccessMetrics(int freeCells, int vias) { this.freeCells = freeCells; this.vias = vias; }
    }
    static final class P08Metrics {
        final long segmentPairs, segmentLandPairs, landPairs;
        P08Metrics(long segmentPairs, long segmentLandPairs, long landPairs) {
            this.segmentPairs = segmentPairs; this.segmentLandPairs = segmentLandPairs;
            this.landPairs = landPairs;
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("RB100 physical reference: " + message);
    }
}
