package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.TreeMap;
import java.util.Vector;

/** Temp-only structural scaling probe. No CircuitJS behavior or normal admission claim. */
public final class Q30TwoLayerScaling {
    private static final PhysicalPackage TWO=P03StructuralFixtures.shape("scale2",140,150,2);
    private static final PhysicalPackage THREE=P03StructuralFixtures.shape("scale3",140,150,3);
    private static final PhysicalPackage FOUR=P03StructuralFixtures.shape("scale4",140,150,4);
    private static final PhysicalPackage FIVE=P03StructuralFixtures.shape("scale5",140,150,5);
    private static final int PITCH_X=220,PITCH_Y=240;
    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        final TreeMap<String,Integer> packages=new TreeMap<String,Integer>();
        final int channels,extras,maxDegree;
        Fixture(TroubleshootBoard b,PcbBoardLayout l,int c,int e,int d) { board=b;layout=l;channels=c;extras=e;maxDegree=d; }
    }
    private static void net(TroubleshootBoard b,String id,BoardNet.RoutingRole role) {
        if(b.getNet(id)==null)b.addNet(new BoardNet(id,role));
    }
    private static void add(TroubleshootBoard b,PcbBoardLayout l,TreeMap<String,Integer> packages,
            String id,PhysicalPackage p,int col,int row,long seed,String... nets) {
        if(nets.length!=p.getTerminalCount())throw new AssertionError("terminal count");
        BoardComponent part=new BoardComponent(id,"STRUCTURAL",p,"U"+(b.getComponentIds().size()+1));b.addComponent(part);
        Random rng=new Random(seed*1315423911L+id.hashCode());
        int x=110+col*PITCH_X+(rng.nextInt(3)-1)*10;
        int y=110+row*PITCH_Y+(rng.nextInt(3)-1)*10;
        for(int i=0;i<nets.length;i++) {
            net(b,nets[i],nets[i].equals("GND")?BoardNet.RoutingRole.RETURN:
                nets[i].equals("VIN")||nets[i].equals("VRAW")||nets[i].equals("V5")?
                    BoardNet.RoutingRole.SUPPLY:BoardNet.RoutingRole.SIGNAL);
            String pin=p.getTerminalIds().get(i);
            b.addPad(new BoardPad(id+"."+pin,id,pin,nets[i]));
        }
        PcbFootprint fp=PcbFootprint.fromPhysicalPackage(part,x,y);
        l.addComponent(fp.getPlacement());for(PcbPadPlacement pad:fp.getPads())l.addPad(pad);
        String key=p.getId();Integer prior=packages.get(key);packages.put(key,(prior==null?0:prior)+1);
    }
    private static void requireConnectedIncidence(TroubleshootBoard b) {
        ArrayList<String> pending=new ArrayList<String>();
        pending.add(b.getComponentIds().get(0));
        HashSet<String> reachedParts=new HashSet<String>(),reachedNets=new HashSet<String>();
        for(int i=0;i<pending.size();i++) {
            String part=pending.get(i);
            if(!reachedParts.add(part))continue;
            for(String padId:b.getComponent(part).getPadIds()) {
                String net=b.getPad(padId).getNetId();
                if(!reachedNets.add(net))continue;
                if(b.getNet(net).getPadIds().size()<2)throw new AssertionError("singleton net "+net);
                for(String peer:b.getNet(net).getPadIds())pending.add(b.getPad(peer).getComponentId());
            }
        }
        if(reachedParts.size()!=b.getComponentIds().size()||reachedNets.size()!=b.getNetIds().size())
            throw new AssertionError("disconnected component/net incidence");
    }
    private static Fixture fixture(int count,long seed) {
        if(count<20||count>180)throw new IllegalArgumentException("count");
        int channels=(count-5)/7, extras=count-5-7*channels;
        int width=2300,height=340+(channels+1)*PITCH_Y;
        TroubleshootBoard b=new TroubleshootBoard("Q30_SCALE_"+count+"_"+seed);
        PcbBoardLayout l=new PcbBoardLayout(width+330,height+220,new Rectangle(30,30,width,height),
            new Rectangle(width+70,40,200,400));
        TreeMap<String,Integer> packages=new TreeMap<String,Integer>();
        add(b,l,packages,"JIN",TWO,0,0,seed,"VIN","GND");
        add(b,l,packages,"FUSE",TWO,1,0,seed,"VIN","VRAW");
        add(b,l,packages,"REG",FOUR,2,0,seed,"VRAW","V5","GND","VRAW");
        add(b,l,packages,"CIN",TWO,3,0,seed,"VRAW","GND");
        add(b,l,packages,"COUT",TWO,4,0,seed,"V5","GND");
        for(int c=0;c<channels;c++) {
            int row=c+1;String prefix="CH"+c+"_";
            String sns=prefix+"SNS",ref=prefix+"REF",drv=prefix+"DRV",coil=prefix+"COIL",out=prefix+"OUT";
            add(b,l,packages,prefix+"JS",TWO,0,row,seed,sns,"GND");
            add(b,l,packages,prefix+"RREF",TWO,1,row,seed,"V5",ref);
            add(b,l,packages,prefix+"CTRL",FIVE,2,row,seed,sns,ref,"V5",drv,"GND");
            add(b,l,packages,prefix+"Q",THREE,3,row,seed,drv,coil,"GND");
            add(b,l,packages,prefix+"RELAY",FIVE,4,row,seed,coil,"VRAW","VRAW",out,"GND");
            add(b,l,packages,prefix+"JOUT",TWO,5,row,seed,out,"GND");
            add(b,l,packages,prefix+"DIODE",TWO,6,row,seed,coil,"VRAW");
        }
        for(int i=0;i<extras;i++) {
            int channel=i%channels, slot=i/channels;
            String signal="CH"+channel+"_"+(slot%2==0?"REF":"OUT");
            add(b,l,packages,"AUX"+i,TWO,7+slot,channel+1,seed,signal,"GND");
        }
        Vector<PcbPlacementConstraints.Part> parts=new Vector<PcbPlacementConstraints.Part>();
        for(String id:b.getComponentIds()) {
            String region=id.startsWith("CH")?id.substring(0,id.indexOf('_')):id.startsWith("AUX")?
                "CH"+(Integer.parseInt(id.substring(3))%channels):"POWER";
            parts.add(new PcbPlacementConstraints.Part(id,region,region,"SHARED",PcbPlacementConstraints.Anchor.NONE,20));
        }
        b.setPlacementConstraints(new PcbPlacementConstraints(parts,new Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.TOP));
        b.validate();requireConnectedIncidence(b);l.validateAgainst(b);
        int degree=0;for(String net:b.getNetIds())degree=Math.max(degree,b.getNet(net).getPadIds().size());
        Fixture f=new Fixture(b,l,channels,extras,degree);f.packages.putAll(packages);return f;
    }
    private static String quote(String value) {
        return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ")+"\"";
    }
    private static void run(int count,long seed,PcbLayerRoutingPrototype.Policy policy) {
        long setupStart=System.nanoTime();Fixture f=fixture(count,seed);
        long setupMs=(System.nanoTime()-setupStart)/1000000;
        String original=f.layout.geometryFingerprint();
        long start=System.nanoTime();PcbLayerRoutingPrototype.Result r=null;String outcome="UNSET";
        try { r=PcbLayerRoutingPrototype.route(f.board,f.layout,policy,new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        });outcome=r.outcome; }
        catch(Throwable error) { outcome="EXCEPTION:"+error.getClass().getSimpleName()+":"+error.getMessage(); }
        long routeMs=(System.nanoTime()-start)/1000000;
        boolean intact=original.equals(f.layout.geometryFingerprint());
        long validateMs=0,topLength=0,bottomLength=0;
        int topSegments=0,bottomSegments=0,topRoutes=0,bottomRoutes=0,vias=0;
        boolean valid=false;
        if(r!=null && r.accepted()) {
            start=System.nanoTime();
            try {
                ArrayList<String> ids=new ArrayList<String>(f.board.getComponentIds());Collections.sort(ids);
                for(int i=0;i<ids.size();i++)r.layout.addSilkscreenLabel(new PcbSilkscreenLabel(
                    "component:"+ids.get(i),"U"+(i+1),new Rectangle(2250,50+i*20,60,16),10,true,null));
                r.layout.validateGeometry(f.board);
                new PcbTwoLayerRules(f.board,r.layout).validate(r.layout);
                r.layout.captureConductorGraph(f.board).pristine().requirePristineNetConnectivity(f.board);
                for(PcbTraceGeometry trace:r.layout.getTraces()) {
                    int[] x=trace.getXPoints(),y=trace.getYPoints();
                    if(trace.getLayer()==PcbCopperLayer.TOP)topRoutes++;else bottomRoutes++;
                    for(int i=1;i<x.length;i++) {
                        int length=Math.abs(x[i]-x[i-1])+Math.abs(y[i]-y[i-1]);
                        if(trace.getLayer()==PcbCopperLayer.TOP){topLength+=length;topSegments++;}
                        else{bottomLength+=length;bottomSegments++;}
                    }
                }
                vias=r.layout.getHoles().size();
                valid=topSegments>0&&bottomSegments>0&&vias>0;
                if(!valid)outcome="SINGLE_FACE_OR_NO_VIA";
            } catch(Throwable error) { outcome="INVALID:"+error.getClass().getSimpleName()+":"+error.getMessage(); }
            validateMs=(System.nanoTime()-start)/1000000;
        }
        ArrayList<String> nets=new ArrayList<String>(f.board.getNetIds());Collections.sort(nets);
        ArrayList<String> degrees=new ArrayList<String>();for(String n:nets)degrees.add(n+":"+f.board.getNet(n).getPadIds().size());
        Rectangle box=f.layout.getBoardOutline();
        StringBuilder out=new StringBuilder();out.append("SCALE_ROW {");
        out.append("\"count\":").append(count).append(",\"seed\":").append(seed);
        out.append(",\"policy\":").append(quote(policy.toString())).append(",\"outcome\":").append(quote(outcome));
        out.append(",\"validMixedLayer\":").append(valid).append(",\"inputIntact\":").append(intact);
        out.append(",\"channels\":").append(f.channels).append(",\"extras\":").append(f.extras);
        out.append(",\"parts\":").append(f.board.getComponentIds().size()).append(",\"pads\":").append(f.board.getPadIds().size());
        out.append(",\"nets\":").append(nets.size()).append(",\"maxNetDegree\":").append(f.maxDegree);
        out.append(",\"boardWidth\":").append(box.width).append(",\"boardHeight\":").append(box.height);
        out.append(",\"boardArea\":").append((long)box.width*box.height);
        out.append(",\"packageCounts\":").append(quote(f.packages.toString()));
        out.append(",\"netDegrees\":").append(quote(degrees.toString()));
        out.append(",\"expansions\":").append(r==null?-1:r.expansions);
        out.append(",\"orderings\":").append(r==null?-1:r.orderings);
        out.append(",\"vias\":").append(vias).append(",\"topRoutes\":").append(topRoutes).append(",\"bottomRoutes\":").append(bottomRoutes);
        out.append(",\"topSegments\":").append(topSegments).append(",\"bottomSegments\":").append(bottomSegments);
        out.append(",\"topLength\":").append(topLength).append(",\"bottomLength\":").append(bottomLength);
        out.append(",\"setupMs\":").append(setupMs).append(",\"routeMs\":").append(routeMs).append(",\"validateMs\":").append(validateMs).append("}");
        System.out.println(out.toString());
    }
    public static void main(String[] args) {
        if(args.length!=3)throw new IllegalArgumentException("count seed policy");
        run(Integer.parseInt(args[0]),Long.parseLong(args[1]),PcbLayerRoutingPrototype.Policy.valueOf(args[2]));
    }
}
