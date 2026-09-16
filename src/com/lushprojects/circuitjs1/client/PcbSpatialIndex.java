package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Linear-storage interval broad phase. Exact physical predicates remain with callers. */
final class PcbSpatialIndex {
    static final class Box {
        final long left, top, right, bottom;
        Box(Rectangle r) { this(r.x,r.y,(long)r.x+r.width,(long)r.y+r.height); }
        Box(long left,long top,long right,long bottom) {
            if(left>right || top>bottom || left < -4294967296L || top < -4294967296L ||
                    right>4294967296L || bottom>4294967296L)
                throw new IllegalArgumentException("Invalid broad-phase bounds");
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
        Box expanded(int distance) {
            if(distance<0)throw new IllegalArgumentException("Negative broad-phase margin");
            return new Box(left-distance,top-distance,right+distance,bottom+distance);
        }
        boolean touches(Box b) {
            return right>=b.left && b.right>=left && bottom>=b.top && b.bottom>=top;
        }
    }
    static final class Statistics {
        long boxTests, nodeVisits, returnedCandidates;
        int objects, nodes, references;
        long primitivePayloadBytes() { return 48L*objects; }
    }
    private final Box[] boxes;
    private final int[] masks, matches;
    private final Integer[] order;
    private final long[] maximumEnd;
    private final boolean xAxis;
    private int found;
    final Statistics statistics=new Statistics();
    PcbSpatialIndex(List<Box> input,int[] layers) {
        if(input==null || layers==null || input.size()!=layers.length)
            throw new IllegalArgumentException("Missing broad-phase input");
        boxes=input.toArray(new Box[input.size()]);masks=new int[layers.length];
        System.arraycopy(layers,0,masks,0,layers.length);matches=new int[boxes.length];
        order=new Integer[boxes.length];maximumEnd=new long[boxes.length];
        long minX=Long.MAX_VALUE,maxX=Long.MIN_VALUE,minY=Long.MAX_VALUE,maxY=Long.MIN_VALUE;
        for(int i=0;i<boxes.length;i++) {
            Box b=boxes[i];
            if(b==null || masks[i]<1 || masks[i]>3)throw new IllegalArgumentException("Invalid broad-phase item");
            order[i]=i;minX=Math.min(minX,b.left+b.right);maxX=Math.max(maxX,b.left+b.right);
            minY=Math.min(minY,b.top+b.bottom);maxY=Math.max(maxY,b.top+b.bottom);
        }
        xAxis=boxes.length==0 || maxX-minX>=maxY-minY;
        Arrays.sort(order,new Comparator<Integer>() {
            public int compare(Integer a,Integer b) {
                long first=low(boxes[a]),second=low(boxes[b]);
                return first<second?-1:first>second?1:a.intValue()-b.intValue();
            }
        });
        build(0,order.length-1);
        statistics.objects=statistics.nodes=statistics.references=boxes.length;
    }
    private long low(Box box) { return xAxis?box.left:box.top; }
    private long high(Box box) { return xAxis?box.right:box.bottom; }
    private long build(int left,int right) {
        if(left>right)return Long.MIN_VALUE;
        int middle=left+(right-left)/2;
        long end=Math.max(high(boxes[order[middle]]),Math.max(build(left,middle-1),build(middle+1,right)));
        maximumEnd[middle]=end;return end;
    }
    int[] query(Box area,int layerMask) {
        if(area==null || layerMask<1 || layerMask>3)throw new IllegalArgumentException("Invalid broad-phase query");
        found=0;visit(0,order.length-1,area,layerMask);
        int[] result=new int[found];System.arraycopy(matches,0,result,0,found);
        Arrays.sort(result);statistics.returnedCandidates+=found;
        return result;
    }
    private void visit(int left,int right,Box area,int mask) {
        if(left>right)return;
        int middle=left+(right-left)/2;statistics.nodeVisits++;
        if(maximumEnd[middle]<low(area) || low(boxes[order[left]])>high(area))return;
        visit(left,middle-1,area,mask);
        int id=order[middle];
        if((masks[id]&mask)!=0) {
            statistics.boxTests++;
            if(boxes[id].touches(area))matches[found++]=id;
        }
        visit(middle+1,right,area,mask);
    }
    static int mask(PcbCopperLayer layer) {
        if(layer==null)throw new IllegalArgumentException("Missing physical layer");
        return layer==PcbCopperLayer.TOP?1:2;
    }
}
