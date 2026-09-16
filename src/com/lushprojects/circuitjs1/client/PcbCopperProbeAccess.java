package com.lushprojects.circuitjs1.client;

import java.util.TreeSet;

/** Resolve current physical islands, never logical-net labels, to actual bound pads. */
final class PcbCopperProbeAccess {
    static PcbConductorGraph.Surface surface(PcbConductorGraph.Snapshot copper,String id) {
        for(PcbConductorGraph.Surface surface:copper.getGraph().getSurfaces())
            if(surface.id.equals(id)) return surface;
        return null;
    }
    static boolean available(PcbConductorGraph.Snapshot copper,PcbConductorGraph.Surface surface,PcbBoardSide face) {
        return surface!=null && surface.padId==null && surface.canProbe(face) &&
            (surface.edgeId==null || copper.hasEdge(surface.edgeId));
    }
    static String connectedPad(PcbConductorGraph.Snapshot copper,String surfaceId) {
        PcbConductorGraph.Surface surface=surface(copper,surfaceId);
        if(surface==null || surface.edgeId!=null && !copper.hasEdge(surface.edgeId)) return null;
        for(String pad:new TreeSet<String>(copper.getGraph().getTerminalJunctions().keySet()))
            if(copper.connected(surface.junctionId,copper.getGraph().getTerminalJunctions().get(pad))) return pad;
        return null;
    }
    static Point marker(PcbConductorGraph.Surface surface) {
        Rectangle b=surface.getBounds();
        // The annular land, not the empty drill centre, is the via contact.
        return new Point(surface.edgeId==null?b.x+b.width-1:b.x+b.width/2,b.y+b.height/2);
    }
    private PcbCopperProbeAccess() { }
}
