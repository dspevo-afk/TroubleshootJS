package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.Vector;

/** Independent P07 checks. Drawing units, not manufacturing safety ratings. */
final class PcbTwoLayerRules {
    static final int VIA_DRILL = 2, VIA_LAND = 6;
    private final TroubleshootBoard board;
    private final TreeMap<String,java.util.TreeSet<String>> netDomains=new TreeMap<String,java.util.TreeSet<String>>();
    private final Vector<Boundary> boundaries = new Vector<Boundary>();
    private static final class Boundary {
        final String low, high;
        final boolean horizontal;
        final int lowLimit, highLimit;
        Boundary(String low, String high, boolean horizontal, int a, int b) {
            this.low=low; this.high=high; this.horizontal=horizontal;
            lowLimit=a; highLimit=b;
        }
    }
    PcbTwoLayerRules(TroubleshootBoard board, PcbBoardLayout layout) {
        this.board=board;
        PcbPlacementConstraints demands=board.getPlacementConstraints();
        demands.validate(board);
        for(String net:board.getNetIds()) {
            java.util.TreeSet<String> domainsForNet=new java.util.TreeSet<String>();
            for(String pad:board.getNet(net).getPadIds())
                domainsForNet.add(demands.get(board.getPad(pad).getComponentId()).domainId);
            netDomains.put(net,domainsForNet);
        }
        TreeMap<String,Rectangle> domains=new TreeMap<String,Rectangle>();
        for(PcbPlacementConstraints.Part part:demands.getParts()) {
            Rectangle r=layout.getComponent(part.componentId).getRoutingCourtyard();
            Rectangle old=domains.get(part.domainId);
            if(old==null) domains.put(part.domainId,r);
            else {
                int right=Math.max(old.x+old.width,r.x+r.width);
                int bottom=Math.max(old.y+old.height,r.y+r.height);
                old.x=Math.min(old.x,r.x); old.y=Math.min(old.y,r.y);
                old.width=right-old.x; old.height=bottom-old.y;
            }
        }
        for(PcbPlacementConstraints.Barrier barrier:demands.getBarriers()) {
            String first=barrier.firstDomain,second=barrier.secondDomain;
            Rectangle a=domains.get(first), b=domains.get(second);
            int gap=barrier.clearance;
            if(a.x+a.width+gap<=b.x) add(first,second,true,a.x+a.width,b.x,gap);
            else if(b.x+b.width+gap<=a.x) add(second,first,true,b.x+b.width,a.x,gap);
            else if(a.y+a.height+gap<=b.y) add(first,second,false,a.y+a.height,b.y,gap);
            else if(b.y+b.height+gap<=a.y) add(second,first,false,b.y+b.height,a.y,gap);
            else throw new IllegalArgumentException("P07 domain barrier has no separated corridor");
        }
    }
    private void add(String low,String high,boolean horizontal,int a,int b,int gap) {
        int middle=a+(b-a)/2;
        boundaries.add(new Boundary(low,high,horizontal,middle-(gap+1)/2,middle+gap/2));
    }
    /** Isolation corridors span the whole board and BOTH copper faces. */
    boolean permits(String net,Rectangle copper) {
        java.util.Set<String> domains=netDomains.get(net);
        if(domains==null) return false;
        for(Boundary boundary:boundaries) {
            int first=boundary.horizontal?copper.x:copper.y;
            int last=first+(boundary.horizontal?copper.width:copper.height);
            if(last>boundary.lowLimit && first<boundary.highLimit) return false;
            if(domains.contains(boundary.low) && last>boundary.lowLimit ||
                    domains.contains(boundary.high) && first<boundary.highLimit) return false;
        }
        return true;
    }
    /** P07 is evidence, not normal customer-challenge admission; policy adoption belongs to P09. */
    static void requireDeveloperAdmission(PcbBoardLayout layout,boolean developerOnly) {
        if(layout==null || developerOnly) return;
        boolean top=false,bottom=false;
        for(PcbTraceGeometry trace:layout.getTraces()) {
            top|=trace.getLayer()==PcbCopperLayer.TOP;
            bottom|=trace.getLayer()==PcbCopperLayer.BOTTOM;
        }
        boolean via=false;
        for(PcbBoardHole hole:layout.getHoles()) via|=hole.kind==PcbBoardHole.Kind.VIA;
        if(top && bottom || via)
            throw new IllegalArgumentException("Two-layer routing requires developer-only construction outside the P09 production envelope");
    }

    static PcbBoardHole via(String id,String net,int x,int y) {
        return new PcbBoardHole(id,PcbBoardHole.Kind.VIA,net,x,y,VIA_DRILL,VIA_LAND,
            PcbCopperLayer.TOP,PcbCopperLayer.BOTTOM,PcbCopperAccess.Exposure.EXPOSED);
    }
    static Rectangle expand(Rectangle r,int d) {
        return new Rectangle(r.x-d,r.y-d,r.width+2*d,r.height+2*d);
    }
    static boolean inside(Rectangle a,Rectangle b) {
        return b.x>=a.x && b.y>=a.y && (long)b.x+b.width<=(long)a.x+a.width &&
            (long)b.y+b.height<=(long)a.y+a.height;
    }
    static boolean contains(Rectangle r,int x,int y) {
        return x>=r.x && y>=r.y && x<=r.x+r.width && y<=r.y+r.height;
    }
    void validate(PcbBoardLayout layout) {
        for(PcbTraceGeometry trace:layout.getTraces()) {
            int[] x=trace.getXPoints(),y=trace.getYPoints();
            for(int i=1;i<x.length;i++) if(!permits(trace.getNetId(),PcbConductorBuilder.stroke(x[i-1],y[i-1],x[i],y[i])))
                throw new IllegalStateException("P07 copper crosses a physical domain barrier");
        }
        for(PcbBoardHole hole:layout.getHoles()) {
            if(hole.kind==PcbBoardHole.Kind.NON_PLATED) continue;
            if(board.getNet(hole.netId)==null || !permits(hole.netId,hole.getBounds()))
                throw new IllegalStateException("P07 via crosses a domain barrier or has an unknown net");
            Rectangle clearance=expand(hole.getBounds(),PcbTraceRules.MIN_VISIBLE_CLEARANCE);
            for(PcbComponentPlacement part:layout.getComponents())
                if(PcbConductorBuilder.touch(hole.getBounds(),part.getRoutingCourtyard()))
                    throw new IllegalStateException("P07 via drills through a package courtyard");
            for(PcbPadPlacement pad:layout.getPads())
                if(PcbConductorBuilder.touch(clearance,pad.getPadBounds()))
                    throw new IllegalStateException("P07 via overlaps a component pad or its clearance");
            for(PcbTraceGeometry trace:layout.getTraces()) {
                if(hole.netId.equals(trace.getNetId())) continue;
                int[] x=trace.getXPoints(),y=trace.getYPoints();
                for(int i=1;i<x.length;i++) if(PcbConductorBuilder.touch(clearance,
                        PcbConductorBuilder.stroke(x[i-1],y[i-1],x[i],y[i])))
                    throw new IllegalStateException("P07 via violates foreign copper clearance");
            }
            for(PcbBoardHole other:layout.getHoles()) if(!hole.id.equals(other.id) &&
                    PcbConductorBuilder.touch(clearance,other.getBounds()))
                throw new IllegalStateException("P07 overlapping drill/land clearance");
        }
    }
}
