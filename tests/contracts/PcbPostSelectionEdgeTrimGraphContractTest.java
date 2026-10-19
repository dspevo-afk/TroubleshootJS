package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

/** Standalone graph oracle canaries; not whole-PCB or solver qualification. */
public final class PcbPostSelectionEdgeTrimGraphContractTest {
    private static int assertions;
    public static void main(String[] args) {
        PcbConductorGraph baseline=graph(false,0);
        verify(baseline,graph(false,0),0);
        verify(baseline,graph(true,0),10);
        reject(baseline,graph(true,1),"net");
        reject(baseline,graph(true,2),"layer");
        reject(baseline,graph(true,4),"coordinate");
        reject(baseline,graph(true,8),"provenance");
        reject(baseline,graph(true,16),"missing edge and copper surface");
        reject(baseline,graph(true,32),"terminal binding");
        reject(baseline,graph(true,64),"pad face and land binding");
        reject(baseline,graph(true,128),"copper exposure");
        reject(baseline,graph(true,256),"stable land identity");
        reject(baseline,graph(true,512),"land exposure");
        reject(baseline,graph(true,1024),"trace width");
        reject(baseline,graph(true,2048),"copper surface edge reference");
        System.out.println("PASS: PCB post-selection edge trim graph contracts assertions="+assertions);
    }
    private static void verify(PcbConductorGraph first,PcbConductorGraph second,int delta) {
        PcbPostSelectionEdgeTrimContractTest.verifyTranslatedConductorGraphs(first,second,
            Arrays.asList("R.1","R.2"),delta,"literal");
        assertions++;
    }
    private static void reject(PcbConductorGraph first,PcbConductorGraph second,String cause) {
        try {verify(first,second,10);}
        catch(AssertionError expected){assertions++;return;}
        throw new AssertionError("Graph oracle admitted changed "+cause);
    }
    private static PcbConductorGraph graph(boolean shifted,int fault) {
        int delta=shifted?10:0;
        int ax=100-delta+((fault&4)!=0?1:0),ay=100-delta,bx=200-delta,by=100-delta;
        String a=shifted?"junction/2":"junction/1",b=shifted?"junction/1":"junction/2";
        String net=(fault&1)!=0?"WRONG":"N";
        PcbCopperLayer layer=(fault&2)!=0?PcbCopperLayer.BOTTOM:PcbCopperLayer.TOP;
        String edge=shifted?"edge/9":"edge/1";
        int width=(fault&1024)!=0?11:9;
        ArrayList<PcbConductorGraph.Junction> junctions=new ArrayList<PcbConductorGraph.Junction>();
        junctions.add(new PcbConductorGraph.Junction(a,net,layer,ax,ay));
        junctions.add(new PcbConductorGraph.Junction(b,net,layer,bx,by));
        ArrayList<PcbConductorGraph.Edge> edges=new ArrayList<PcbConductorGraph.Edge>();
        ArrayList<PcbConductorGraph.Surface> surfaces=new ArrayList<PcbConductorGraph.Surface>();
        if((fault&16)==0) {
            // A coordinate-string ordering reversal may choose the opposite first endpoint.
            String first=shifted?b:a,second=shifted?a:b;
            edges.add(new PcbConductorGraph.Edge(edge,first,second,net,PcbConductorGraph.Kind.TRACE,
                Arrays.asList((fault&8)!=0?"trace/WRONG":"trace/T"),width));
            surfaces.add(new PcbConductorGraph.Surface("copper/"+edge,first,(fault&2048)!=0?null:edge,null,layer,
                (fault&128)!=0?PcbCopperAccess.Exposure.COVERED:PcbCopperAccess.Exposure.EXPOSED,
                new Rectangle(ax-width/2,ay-width/2,bx-ax+width,width)));
        }
        TreeMap<String,String> faces=new TreeMap<String,String>();
        faces.put(PcbConductorGraph.faceKey("R.1",layer),(fault&64)!=0?b:a);
        faces.put(PcbConductorGraph.faceKey("R.2",layer),b);
        TreeMap<String,String> terminals=new TreeMap<String,String>();
        terminals.put("R.1",(fault&32)!=0?b:a);terminals.put("R.2",b);
        String land=(fault&256)!=0?"pad/WRONG":"pad/R.1";
        boolean wrongLand=(fault&64)!=0;
        surfaces.add(new PcbConductorGraph.Surface(land,wrongLand?b:a,null,"R.1",layer,
            (fault&512)!=0?PcbCopperAccess.Exposure.COVERED:PcbCopperAccess.Exposure.EXPOSED,
            new Rectangle((wrongLand?bx:ax)-5,ay-5,10,10)));
        surfaces.add(new PcbConductorGraph.Surface("pad/R.2",b,null,"R.2",layer,
            PcbCopperAccess.Exposure.EXPOSED,new Rectangle(bx-5,by-5,10,10)));
        return new PcbConductorGraph(junctions,edges,surfaces,faces,terminals,"");
    }
}
