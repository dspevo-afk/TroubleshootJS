package com.lushprojects.circuitjs1.client;

/** Resolve current physical islands, never logical-net labels, to actual bound pads. */
final class PcbCopperProbeAccess {
    static PcbConductorGraph.Surface surface(PcbConductorGraph.Snapshot copper,String id) {
        return copper.getGraph().getSurface(id);
    }
    static boolean available(PcbConductorGraph.Snapshot copper,PcbConductorGraph.Surface surface,PcbBoardSide face) {
        return surface!=null && copper.getGraph().getSurface(surface.id)==surface &&
            surface.padId==null && surface.canProbe(face) &&
            (surface.edgeId==null || copper.hasEdge(surface.edgeId));
    }
    static String connectedPad(PcbConductorGraph.Snapshot copper,String surfaceId) {
        PcbConductorGraph.Surface surface=surface(copper,surfaceId);
        if(surface==null || surface.edgeId!=null && !copper.hasEdge(surface.edgeId)) return null;
        return copper.firstPadAt(surface.junctionId);
    }
    static Point marker(PcbConductorGraph.Surface surface) {
        Rectangle b=surface.getBounds();
        // The annular land, not the empty drill centre, is the via contact.
        return new Point(surface.edgeId==null?b.x+b.width-1:b.x+b.width/2,b.y+b.height/2);
    }
    private PcbCopperProbeAccess() { }
}
