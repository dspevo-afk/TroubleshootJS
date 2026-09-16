package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One current copper projection. Occlusion and solver observations are not cached. */
final class PcbCopperViewCache {
    static final int POLICY_VERSION=1;
    static final class Entry {
        final PcbConductorGraph.Surface surface;
        final int firstX,firstY,secondX,secondY;
        private final Rectangle bounds;
        private final Point marker;
        Entry(PcbConductorGraph graph,PcbConductorGraph.Surface surface,PcbViewport.Transform view) {
            this.surface=surface;bounds=view.project(surface.getBounds());
            marker=view.project(PcbCopperProbeAccess.marker(surface));
            PcbConductorGraph.Edge edge=surface.edgeId==null?null:graph.getEdges().get(surface.edgeId);
            PcbConductorGraph.Junction a=graph.getJunctions().get(surface.junctionId);
            PcbConductorGraph.Junction b=edge==null?a:graph.getJunctions().get(edge.second);
            if(edge!=null)a=graph.getJunctions().get(edge.first);
            firstX=(int)Math.round(view.screenX(a.x));firstY=(int)Math.round(view.screenY(a.y));
            secondX=(int)Math.round(view.screenX(b.x));secondY=(int)Math.round(view.screenY(b.y));
        }
        boolean contains(int x,int y) { return bounds.contains(x,y); }
        Rectangle bounds() { return new Rectangle(bounds); }
        Point marker() { return new Point(marker.x,marker.y); }
    }
    private PcbConductorGraph.Snapshot snapshot;
    private PcbViewport.Transform transform;
    private PcbBoardSide face;
    private int policy, builds;
    private List<Entry> entries=Collections.emptyList();
    private Map<String,Entry> byId=Collections.emptyMap();
    int getBuildCount() { return builds; }
    List<Entry> entries(PcbConductorGraph.Snapshot copper,PcbViewport.Transform view,
            PcbBoardSide side,int policyVersion) {
        if(copper==null || view==null || side==null || policyVersion<1)
            throw new IllegalArgumentException("Missing copper projection dependency");
        if(snapshot==copper && face==side && policy==policyVersion && sameView(view))return entries;
        ArrayList<Entry> next=new ArrayList<Entry>();
        HashMap<String,Entry> indexed=new HashMap<String,Entry>();
        for(PcbConductorGraph.Surface surface:copper.getGraph().getSurfaces()) {
            if(!PcbCopperProbeAccess.available(copper,surface,side))continue;
            Entry entry=new Entry(copper.getGraph(),surface,view);
            next.add(entry);indexed.put(surface.id,entry);
        }
        entries=Collections.unmodifiableList(next);byId=Collections.unmodifiableMap(indexed);
        snapshot=copper;transform=view;face=side;policy=policyVersion;builds++;
        return entries;
    }
    Entry get(PcbConductorGraph.Snapshot copper,PcbViewport.Transform view,PcbBoardSide side,int version,String id) {
        entries(copper,view,side,version);return byId.get(id);
    }
    private boolean sameView(PcbViewport.Transform view) {
        return transform!=null && transform.scale==view.scale && transform.x==view.x &&
            transform.y==view.y && transform.mirror==view.mirror && transform.flipped==view.flipped;
    }
}
