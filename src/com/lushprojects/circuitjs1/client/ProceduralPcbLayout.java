package com.lushprojects.circuitjs1.client;

/** Shared physical planning for every leaf family, independent of fault and difficulty. */
final class ProceduralPcbLayout {
    private ProceduralPcbLayout() { }

    static long seed(String family, long root, NamedRandomStreams.Concern concern) {
        return new NamedRandomStreams(NamedRandomStreams.DERIVATION_VERSION, root, family, 1)
            .deviceSeed(concern, 1, concern == NamedRandomStreams.Concern.PLACEMENT ?
                "pcb-layout" : "pcb-routing");
    }

    static void declare(TroubleshootBoard board) {
        if (board == null) throw new IllegalArgumentException("Missing procedural board");
        PcbPlacementConstraints demand = board.getPlacementConstraints();
        if (demand.routingLayer == PcbCopperLayer.BOTTOM) {
            // A bottom-face declaration has already crossed the board's
            // immutable set-once boundary. Its owner must have selected the
            // normal interpretation before this adapter is entered.
            if (demand.placementPackingVersion !=
                    PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION)
                throw new IllegalStateException(
                    "Normal bottom placement must select connected-pocket packing");
            return;
        }
        // This entry point is the shared normal declaration. Select the
        // interpretation explicitly before converting the exposed face;
        // density alone is intentionally not a signal.
        demand = demand.withConnectedPocketPacking();
        // Retain declared parts, regions, anchors and isolation barriers. No net is
        // merged or invented here. All copper is on the qualified exposed face.
        board.setPlacementConstraints(toBottomRoutingConstraints(demand));
    }

    /** Converts the declared routing face without changing the selected policy. */
    static PcbPlacementConstraints toBottomRoutingConstraints(
            PcbPlacementConstraints demand) {
        if (demand == null) throw new IllegalArgumentException("Missing physical demand");
        return new PcbPlacementConstraints(demand.getParts(), demand.getBarriers(),
            PcbCopperLayer.BOTTOM, demand.getPhysicalPolicyId(),
            demand.getPhysicalPolicyVersion(), demand.placementDensityVersion,
            demand.requiresTwoLayerRouting, demand.getTraceWidthProfile(),
            demand.placementPackingVersion);
    }

    static PcbBoardLayout generate(TroubleshootBoard board, long root, String family) {
        declare(board);
        return new SeededPcbLayoutGenerator().generate(board,
            seed(family, root, NamedRandomStreams.Concern.PLACEMENT),
            seed(family, root, NamedRandomStreams.Concern.ROUTING), SupportedEnvelope.current());
    }
}
