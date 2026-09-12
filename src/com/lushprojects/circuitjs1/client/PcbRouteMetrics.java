package com.lushprojects.circuitjs1.client;

import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

/** Union length and uniquely shared length. Duplicate provenance cannot multiply reuse. */
final class PcbRouteMetrics {
    static final String SCORE_VERSION="unique-copper-tree-v1";
    long length,uniqueLength,reusedLength;
    int bends,segments;
    private static final class Line {
        final TreeMap<Integer,TreeMap<String,Integer>> events=new TreeMap<Integer,TreeMap<String,Integer>>();
        void event(int coordinate,String source,int delta) {
            TreeMap<String,Integer> at=events.get(coordinate);if(at==null){at=new TreeMap<String,Integer>();events.put(coordinate,at);}
            Integer old=at.get(source);at.put(source,(old==null?0:old)+delta);
        }
    }
    static PcbRouteMetrics measure(Vector<PcbTraceGeometry> traces) {
        PcbRouteMetrics result=new PcbRouteMetrics(); TreeMap<String,Line> lines=new TreeMap<String,Line>(); int ordinal=0;
        for(PcbTraceGeometry trace:traces) {
            int[] x=trace.getXPoints(),y=trace.getYPoints();int pdx=0,pdy=0;
            String source=trace.getSourceId()==null?"statistic-"+ordinal:trace.getSourceId();ordinal++;
            for(int i=1;i<x.length;i++) {
                long dx=(long)x[i]-x[i-1],dy=(long)y[i]-y[i-1];if(dx==0&&dy==0) continue;
                if(dx!=0&&dy!=0) throw new IllegalArgumentException("Non-Manhattan route metrics");
                int sx=dx==0?0:dx<0?-1:1,sy=dy==0?0:dy<0?-1:1;
                if((pdx!=0||pdy!=0)&&(pdx!=sx||pdy!=sy))result.bends++;pdx=sx;pdy=sy;
                result.length+=Math.abs(dx)+Math.abs(dy);result.segments++;
                String key=trace.getNetId().length()+":"+trace.getNetId()+":"+trace.getLayer()+":"+(dy==0?"H:"+y[i]:"V:"+x[i]);
                Line line=lines.get(key);if(line==null){line=new Line();lines.put(key,line);}
                int first=dy==0?Math.min(x[i-1],x[i]):Math.min(y[i-1],y[i]);
                int last=dy==0?Math.max(x[i-1],x[i]):Math.max(y[i-1],y[i]);
                line.event(first,source,1);line.event(last,source,-1);
            }
        }
        for(Line line:lines.values()) {
            TreeMap<String,Integer> active=new TreeMap<String,Integer>();Integer previous=null;
            for(Map.Entry<Integer,TreeMap<String,Integer>> event:line.events.entrySet()) {
                int count=active.size();long distance=previous==null?0:(long)event.getKey()-previous;
                if(count>0)result.uniqueLength+=distance;if(count>1)result.reusedLength+=distance;
                for(Map.Entry<String,Integer> change:event.getValue().entrySet()) {
                    Integer old=active.get(change.getKey());int value=(old==null?0:old)+change.getValue();
                    if(value==0)active.remove(change.getKey());else active.put(change.getKey(),value);
                }
                previous=event.getKey();
            }
        }
        return result;
    }
    private PcbRouteMetrics() { }
}
