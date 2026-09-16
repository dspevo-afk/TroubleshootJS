package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.List;

/** Spatial candidates followed by the existing exact centerline-distance predicate. */
final class PcbTraceClearance {
    static final class Statistics {
        long exactPairs, potentialPairs;
        PcbSpatialIndex.Statistics index;
    }
    private static final class Segment {
        final PcbTraceGeometry trace;
        final int x1,y1,x2,y2;
        final PcbSpatialIndex.Box bounds;
        Segment(PcbTraceGeometry trace,int x1,int y1,int x2,int y2) {
            if(x1!=x2 && y1!=y2)throw new IllegalStateException("PCB trace is not Manhattan routed");
            this.trace=trace;this.x1=x1;this.y1=y1;this.x2=x2;this.y2=y2;
            bounds=new PcbSpatialIndex.Box(Math.min(x1,x2),Math.min(y1,y2),
                Math.max(x1,x2),Math.max(y1,y2));
        }
    }
    static Statistics validate(List<PcbTraceGeometry> traces) {
        ArrayList<Segment> segments=new ArrayList<Segment>();
        ArrayList<PcbSpatialIndex.Box> boxes=new ArrayList<PcbSpatialIndex.Box>();
        for(PcbTraceGeometry trace:traces) {
            int[] x=trace.getXPoints(), y=trace.getYPoints();
            for(int i=1;i<x.length;i++) {
                Segment s=new Segment(trace,x[i-1],y[i-1],x[i],y[i]);
                segments.add(s);boxes.add(s.bounds);
            }
        }
        int[] masks=new int[segments.size()];
        for(int i=0;i<masks.length;i++)masks[i]=PcbSpatialIndex.mask(segments.get(i).trace.getLayer());
        PcbSpatialIndex index=new PcbSpatialIndex(boxes,masks);
        Statistics result=new Statistics();result.index=index.statistics;
        result.potentialPairs=(long)segments.size()*(segments.size()-1)/2;
        int margin=PcbTraceRules.MIN_CENTERLINE_CLEARANCE;
        long minimumSquared=(long)margin*margin;
        for(int i=0;i<segments.size();i++) {
            Segment a=segments.get(i);
            for(int j:index.query(a.bounds.expanded(margin),masks[i])) {
                Segment b=segments.get(j);
                if(j<=i || a.trace==b.trace || a.trace.getNetId().equals(b.trace.getNetId()))continue;
                result.exactPairs++;
                long distance=PcbBoardLayout.segmentDistanceSquared(a.x1,a.y1,a.x2,a.y2,b.x1,b.y1,b.x2,b.y2);
                if(distance<minimumSquared)throw new IllegalStateException(
                    "PCB traces violate copper clearance: "+a.trace.getNetId()+" / "+b.trace.getNetId()+
                    " distanceSquared="+distance+" minimumSquared="+minimumSquared+
                    " firstSegment="+a.x1+","+a.y1+" -> "+a.x2+","+a.y2+
                    " secondSegment="+b.x1+","+b.y1+" -> "+b.x2+","+b.y2);
            }
        }
        return result;
    }
    private PcbTraceClearance() { }
}
