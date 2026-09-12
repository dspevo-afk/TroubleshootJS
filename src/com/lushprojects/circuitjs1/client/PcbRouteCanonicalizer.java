package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.Vector;

/** Removes only collinear vertices; physical endpoints, escape tips and tree contacts remain explicit. */
final class PcbRouteCanonicalizer {
    static void canonicalize(PcbBoardLayout layout) {
        Vector<PcbTraceGeometry> traces=layout.getTraces();
        final Vector<Point> witnesses=new Vector<Point>();
        TreeSet<String> required=new TreeSet<String>();
        for(PcbPadPlacement pad:layout.getPads()) {
            witness(witnesses,required,new Point(pad.getX(),pad.getY()));
            witness(witnesses,required,new Point(pad.getX()+pad.getEscapeDx()*pad.getEscapeLength(),pad.getY()+pad.getEscapeDy()*pad.getEscapeLength()));
        }
        for(PcbBoardHole hole:layout.getHoles()) witness(witnesses,required,new Point(hole.x,hole.y));
        for(PcbTraceGeometry trace:traces) {
            int[] x=trace.getXPoints(),y=trace.getYPoints();
            witness(witnesses,required,new Point(x[0],y[0])); witness(witnesses,required,new Point(x[x.length-1],y[y.length-1]));
        }
        Vector<PcbTraceGeometry> result=new Vector<PcbTraceGeometry>();
        for(PcbTraceGeometry trace:traces) {
            int[] x=trace.getXPoints(),y=trace.getYPoints(); Vector<Point> expanded=new Vector<Point>();
            expanded.add(new Point(x[0],y[0]));
            for(int index=1;index<x.length;index++) {
                final int ax=x[index-1],ay=y[index-1],bx=x[index],by=y[index];
                if((ax==bx)==(ay==by)) throw new IllegalArgumentException("Zero-length or non-Manhattan route segment");
                Vector<Point> interior=new Vector<Point>();
                for(Point point:witnesses) if(onSegment(point,ax,ay,bx,by) && !(point.x==ax&&point.y==ay) && !(point.x==bx&&point.y==by)) interior.add(point);
                Collections.sort(interior,new Comparator<Point>() { public int compare(Point a,Point b) {
                    long da=Math.abs((long)a.x-ax)+Math.abs((long)a.y-ay),db=Math.abs((long)b.x-ax)+Math.abs((long)b.y-ay);
                    return da<db?-1:da==db?0:1;
                }});
                expanded.addAll(interior); expanded.add(new Point(bx,by));
            }
            Vector<Point> kept=new Vector<Point>();
            for(Point point:expanded) {
                while(kept.size()>=2) {
                    Point a=kept.get(kept.size()-2),b=kept.get(kept.size()-1);
                    if(required.contains(key(b)) || !sameDirection(a,b,point)) break;
                    kept.remove(kept.size()-1);
                }
                kept.add(point);
            }
            int[] cx=new int[kept.size()],cy=new int[kept.size()];
            for(int i=0;i<kept.size();i++) {cx[i]=kept.get(i).x;cy[i]=kept.get(i).y;}
            result.add(trace.withPath(cx,cy));
        }
        layout.replaceTraces(result);
    }
    private static void witness(Vector<Point> points,TreeSet<String> keys,Point point) { if(keys.add(key(point))) points.add(point); }
    private static String key(Point point) { return point.x+","+point.y; }
    private static boolean onSegment(Point p,int ax,int ay,int bx,int by) {
        return ax==bx?p.x==ax&&p.y>=Math.min(ay,by)&&p.y<=Math.max(ay,by):p.y==ay&&p.x>=Math.min(ax,bx)&&p.x<=Math.max(ax,bx);
    }
    private static boolean sameDirection(Point a,Point b,Point c) {
        return a.x==b.x&&b.x==c.x&&((long)b.y-a.y)*((long)c.y-b.y)>0 ||
            a.y==b.y&&b.y==c.y&&((long)b.x-a.x)*((long)c.x-b.x)>0;
    }
    private PcbRouteCanonicalizer() { }
}
