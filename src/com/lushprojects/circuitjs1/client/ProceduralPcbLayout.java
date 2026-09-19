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
        if (demand.routingLayer == PcbCopperLayer.BOTTOM) return;
        // Retain declared parts, regions, anchors and isolation barriers. No net is
        // merged or invented here. All copper is on the qualified exposed face.
        board.setPlacementConstraints(new PcbPlacementConstraints(
            demand.getParts(), demand.getBarriers(), PcbCopperLayer.BOTTOM));
    }

    static PcbBoardLayout generate(TroubleshootBoard board, long root, String family) {
        declare(board);
        return new SeededPcbLayoutGenerator().generate(board,
            seed(family, root, NamedRandomStreams.Concern.PLACEMENT),
            seed(family, root, NamedRandomStreams.Concern.ROUTING), SupportedEnvelope.current());
    }
}
