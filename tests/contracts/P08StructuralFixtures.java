package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;

/** Frozen A01/P03 mixed packages on deliberately roomy validation-only bottom-copper trees.
 * Not a normal routing policy, production envelope or solver-backed playable board. */
final class P08StructuralFixtures {
    final TroubleshootBoard board;
    final PcbBoardLayout layout;
    P08StructuralFixtures(String name) throws Exception {
        final P03StructuralFixtures.Fixture inventory=P03StructuralFixtures.read(name);
        board=inventory.board;
        ArrayList<String> ids=new ArrayList<String>(board.getComponentIds());
        Collections.sort(ids,new Comparator<String>() { public int compare(String a,String b) {
            int domain=inventory.domains.get(a).compareTo(inventory.domains.get(b));
            return domain==0?a.compareTo(b):domain;
        }});
        ArrayList<PcbFootprint> footprints=new ArrayList<PcbFootprint>();
        int cursor=200,maximumY=400;
        for(String id:ids) {
            PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(board.getComponent(id),cursor,200);
            footprints.add(footprint);Rectangle courtyard=footprint.getPlacement().getRoutingCourtyard();
            int right=courtyard.x+courtyard.width;maximumY=Math.max(maximumY,courtyard.y+courtyard.height);
            for(PcbPadPlacement pad:footprint.getPads()) {
                Rectangle bounds=pad.getPadBounds();right=Math.max(right,bounds.x+bounds.width);
                maximumY=Math.max(maximumY,bounds.y+bounds.height);
            }
            cursor=right+180;
        }
        int bottomBus=maximumY+100,height=Math.max(750,bottomBus+250);
        layout=new PcbBoardLayout(cursor+400,height,new Rectangle(100,100,cursor-100,height-200),
            new Rectangle(cursor+100,120,200,400));
        TreeMap<String,Stem> stems=new TreeMap<String,Stem>();int serial=0;
        for(PcbFootprint footprint:footprints) {
            PcbComponentPlacement placed=footprint.getPlacement();layout.addComponent(placed);
            layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:"+placed.getComponentId(),"U"+(++serial),
                new Rectangle(placed.getBodyBounds().x,150,60,16),10,true,null));
            for(PcbPadPlacement pad:footprint.getPads()) {
                layout.addPad(pad);String net=board.getPad(pad.getPadId()).getNetId();
                String key=PcbConductorGraph.field(placed.getComponentId())+PcbConductorGraph.field(net);
                int busX=pad.getX()+(net.endsWith("/0")?-50:50);
                Stem stem=stems.get(key);
                if(stem==null){stem=new Stem(net,busX);stems.put(key,stem);}
                if(stem.x!=busX)throw new AssertionError("Frozen mixed footprint changed its terminal columns");
                stem.minY=Math.min(stem.minY,pad.getY());stem.maxY=Math.max(stem.maxY,pad.getY());
                route("branch/"+pad.getPadId(),net,pad.getPadId(),pad.getX(),pad.getY(),busX,pad.getY());
            }
        }
        TreeMap<String,int[]> buses=new TreeMap<String,int[]>();
        for(String key:stems.keySet()) {
            Stem stem=stems.get(key);boolean upper=stem.net.endsWith("/0");
            int busY=upper?120:bottomBus;
            route("stem/"+key,stem.net,null,stem.x,upper?busY:stem.minY,stem.x,upper?stem.maxY:busY);
            int[] range=buses.get(stem.net);
            if(range==null){range=new int[]{stem.x,stem.x};buses.put(stem.net,range);}
            range[0]=Math.min(range[0],stem.x);range[1]=Math.max(range[1],stem.x);
        }
        for(String net:buses.keySet()) {
            int[] range=buses.get(net);int y=net.endsWith("/0")?120:bottomBus;
            route("bus/"+net,net,null,range[0],y,range[1],y);
        }
        layout.validateGeometry(board);
    }
    private static final class Stem {
        final String net;final int x;
        int minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        Stem(String net,int x){this.net=net;this.x=x;}
    }
    private void route(String id,String net,String pad,int x1,int y1,int x2,int y2) {
        if(x1==x2 && y1==y2)return;
        layout.addTrace(new PcbTraceGeometry("p08/"+id,net,pad,null,PcbCopperLayer.BOTTOM,
            PcbCopperAccess.Exposure.EXPOSED,new int[]{x1,x2},new int[]{y1,y2}));
    }
}
