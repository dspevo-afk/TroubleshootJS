package com.lushprojects.circuitjs1.client;

import java.util.Vector;

public final class P03PlacementContractTest {
    private static int assertions;
    private static void require(boolean ok,String why) { assertions++; if(!ok) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        for(String id:new String[]{"RB15","RB30","RB56","RB100"}) for(long seed:new long[]{0,3,-17}) {
            P03StructuralFixtures.Fixture fixture=P03StructuralFixtures.read(id);
            PcbPlacementConstraints constraints=constraints(fixture);
            authoredBaseline(fixture,constraints,seed);
            PcbPlacementPlanner planner=new PcbPlacementPlanner(fixture.registry);
            long start=System.nanoTime(); PcbPlacementPlanner.Plan best=null; int rejected=0;
            for(int candidate=0;candidate<PcbPlacementPlanner.OUTLINE_CANDIDATES;candidate++) try {
                PcbPlacementPlanner.Plan plan=planner.plan(fixture.board,constraints,seed,candidate);
                independentEnvelopes(fixture,plan);
                if(best==null || (long)plan.outline.width*plan.outline.height<(long)best.outline.width*best.outline.height) best=plan;
            } catch(PcbPlacementPlanner.Rejected e) { rejected++; }
            require(best!=null,"bounded physical plan for "+id+" seed "+seed);
            PcbPlacementPlanner.Plan repeat=planner.plan(fixture.board,constraints,seed,best.candidate);
            require(best.materialize().geometryFingerprint().equals(repeat.materialize().geometryFingerprint()),"deterministic selected geometry");
            System.out.println("P03_CORPUS {\"mode\":\"hierarchical-v1\",\"board\":\""+id+"\",\"seed\":\""+seed+"\",\"placed\":"+best.footprints.size()+
                ",\"courtyardArea\":"+best.courtyardArea+",\"demandArea\":"+best.demandArea+",\"outlineArea\":"+((long)best.outline.width*best.outline.height)+
                ",\"width\":"+best.outline.width+",\"height\":"+best.outline.height+",\"rejectedCandidates\":"+rejected+",\"evaluations\":"+best.evaluations+",\"elapsedMs\":"+((System.nanoTime()-start)/1000000.0)+"}");
        }
        negatives();
        TroubleshootBoard small=TroubleshootBoardFixtures.createLedIndicatorBoard();
        for(long seed:new long[]{0,3,-17}) {
            PcbBoardLayout layout=new SeededPcbLayoutGenerator().generate(small,seed); layout.validateGeometry(small);
            require(layout.getComponents().size()==3,"current playable layout places exact logical parts");
        }
        System.out.println("PASS: P03 placement contracts assertions="+assertions);
    }
    static PcbPlacementConstraints constraints(P03StructuralFixtures.Fixture f) {
        Vector<PcbPlacementConstraints.Part> parts=new Vector<PcbPlacementConstraints.Part>(); boolean anchored=false;
        for(String id:f.regions.keySet()) {
            boolean connector=f.board.getComponent(id).getPhysicalPackage().isConnector();
            PcbPlacementConstraints.Anchor anchor=connector&&!anchored?PcbPlacementConstraints.Anchor.RIGHT:PcbPlacementConstraints.Anchor.NONE;
            anchored|=connector;
            parts.add(new PcbPlacementConstraints.Part(id,f.regions.get(id),f.regions.get(id),f.domains.get(id),anchor,20));
        }
        Vector<PcbPlacementConstraints.Barrier> barriers=new Vector<PcbPlacementConstraints.Barrier>();
        if(f.domains.containsValue("HV_PRIMARY")) barriers.add(new PcbPlacementConstraints.Barrier("HV_PRIMARY","CONTROL_RAIL",120));
        return new PcbPlacementConstraints(parts,barriers);
    }
    private static void authoredBaseline(P03StructuralFixtures.Fixture f,PcbPlacementConstraints constraints,long seed) {
        // Fixed A01 inventory bands: 500x420 cells, one row per region, a spare
        // 200-unit domain gap, and an independent connector strip. No optimization.
        int count=f.board.getComponentIds().size(),columns=count==15?3:count==30?4:count==56?6:8;
        int width=(columns+1)*500+200,row=0,col=0;String previousRegion="",previousDomain="";
        int domainGap=0;long area=0,demand=0,start=System.nanoTime();Vector<PcbFootprint> placed=new Vector<PcbFootprint>();
        java.util.TreeMap<String,PcbPlacementConstraints.Part> ordered=new java.util.TreeMap<String,PcbPlacementConstraints.Part>();
        for(PcbPlacementConstraints.Part p:constraints.getParts())ordered.put(p.domainId+"/"+p.regionId+"/"+p.componentId,p);
        for(PcbPlacementConstraints.Part p:ordered.values()) {
            if(!previousRegion.equals(p.regionId)&&col>0){row++;col=0;}
            if(!previousDomain.equals(p.domainId)&&previousDomain.length()>0)domainGap+=200;
            previousRegion=p.regionId;previousDomain=p.domainId;
            PcbFootprint source=f.registry.create(f.board.getComponent(p.componentId),0,0,new java.util.Random(seed),new Rectangle(0,0,width,16000));
            int x=p.anchor==PcbPlacementConstraints.Anchor.RIGHT?width-200:100+col*500;
            placed.add(source.translated(x,100+row*420+domainGap));
            Rectangle court=source.getPlacement().getRoutingCourtyard(),envelope=PcbPlacementPlanner.envelope(source,20);
            area+=(long)court.width*court.height;demand+=(long)(envelope.width+40)*(envelope.height+40);
            if(p.anchor==PcbPlacementConstraints.Anchor.NONE&&++col==columns){row++;col=0;}
        }
        int height=(row+1)*420+domainGap+150;Rectangle outline=new Rectangle(20,20,width,height);
        String status="PASS",reason="";
        try {
            if(width>16000||height>16000||(long)width*height>64000000L)throw new PcbPlacementPlanner.Rejected("OUTLINE_BUDGET");
            PcbPlacementPlanner.validate(f.board,constraints,outline,placed);
        }catch(PcbPlacementPlanner.Rejected e){status="REJECT";reason=e.reason;}
        System.out.println("P03_CORPUS {\"mode\":\"authored-inventory-grid\",\"board\":\""+f.board.getId()+"\",\"seed\":\""+seed+
            "\",\"status\":\""+status+"\",\"reason\":\""+reason+"\",\"placed\":"+placed.size()+",\"courtyardArea\":"+area+
            ",\"demandArea\":"+demand+",\"outlineArea\":"+((long)width*height)+",\"elapsedMs\":"+((System.nanoTime()-start)/1000000.0)+"}");
    }
    private static void independentEnvelopes(P03StructuralFixtures.Fixture f,PcbPlacementPlanner.Plan p) {
        require(p.footprints.size()==f.board.getComponentIds().size(),"all original component IDs placed");
        for(int i=0;i<p.footprints.size();i++) {
            PcbFootprint a=p.footprints.get(i); PcbComponentPlacement pa=a.getPlacement(); Rectangle box=pa.getRoutingCourtyard();
            require(f.board.getComponent(pa.getComponentId()).getPhysicalPackage()==pa.getPhysicalPackage(),"exact package, no shrink");
            require(box.x>=p.outline.x && box.y>=p.outline.y && box.x+box.width<=p.outline.x+p.outline.width && box.y+box.height<=p.outline.y+p.outline.height,"literal board containment");
            for(int j=i+1;j<p.footprints.size();j++) {
                Rectangle b=p.footprints.get(j).getPlacement().getRoutingCourtyard();
                require(box.x+box.width<=b.x || b.x+b.width<=box.x || box.y+box.height<=b.y || b.y+b.height<=box.y,"literal courtyard disjointness");
            }
            for(PcbPadPlacement pad:a.getPads()) for(PcbFootprint other:p.footprints) if(other!=a) {
                Rectangle b=other.getPlacement().getRoutingCourtyard();
                int ex=pad.getX()+pad.getEscapeDx()*pad.getEscapeLength(),ey=pad.getY()+pad.getEscapeDy()*pad.getEscapeLength();
                require(ex<b.x || ex>b.x+b.width || ey<b.y || ey>b.y+b.height,"literal free escape tip");
            }
        }
    }
    private static void negatives() throws Exception {
        TroubleshootBoard b=TroubleshootBoardFixtures.createLedIndicatorBoard();
        boolean rejected=false;
        Vector<PcbPlacementConstraints.Part> parts=new Vector<PcbPlacementConstraints.Part>();
        parts.add(new PcbPlacementConstraints.Part("J1","input","Input","a",PcbPlacementConstraints.Anchor.RIGHT,20));
        parts.add(new PcbPlacementConstraints.Part("R1","load","Load","b",PcbPlacementConstraints.Anchor.NONE,20));
        parts.add(new PcbPlacementConstraints.Part("LED1","load","Load","b",PcbPlacementConstraints.Anchor.NONE,20));
        Vector<PcbPlacementConstraints.Barrier> barriers=new Vector<PcbPlacementConstraints.Barrier>();barriers.add(new PcbPlacementConstraints.Barrier("a","b",120));
        try {new PcbPlacementConstraints(parts,barriers).validate(b);} catch(IllegalArgumentException e){rejected=true;}
        require(rejected,"same net cannot cross isolated domains");
        rejected=false;try{new PcbPlacementConstraints.Part("R1","x","x","d",PcbPlacementConstraints.Anchor.NONE,0);}catch(IllegalArgumentException e){rejected=true;}
        require(rejected,"insufficient access rejected");
        PcbPlacementPlanner planner=new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry());
        PcbPlacementPlanner.Plan plan=planner.plan(b,b.getPlacementConstraints(),3,0);
        Vector<PcbFootprint> overlap=new Vector<PcbFootprint>(plan.footprints);
        overlap.set(1,overlap.get(1).translated(overlap.get(0).getPlacement().getX(),overlap.get(0).getPlacement().getY()));
        rejected=false;try{PcbPlacementPlanner.validate(b,b.getPlacementConstraints(),plan.outline,overlap);}catch(PcbPlacementPlanner.Rejected e){rejected=true;}
        require(rejected,"area alone cannot admit overlaps or stranded access");
        P03StructuralFixtures.Fixture fixture=P03StructuralFixtures.read("RB15");
        Vector<PcbPlacementConstraints.Part> outward=new Vector<PcbPlacementConstraints.Part>();boolean anchored=false;
        for(String id:fixture.regions.keySet()) {
            boolean anchor=fixture.board.getComponent(id).getPhysicalPackage().isConnector()&&!anchored;
            anchored|=anchor;
            outward.add(new PcbPlacementConstraints.Part(id,"circuit","Circuit","board",
                anchor?PcbPlacementConstraints.Anchor.LEFT:PcbPlacementConstraints.Anchor.NONE,20));
        }
        rejected=false;
        try{new PcbPlacementPlanner(fixture.registry).plan(fixture.board,
            new PcbPlacementConstraints(outward,new Vector<PcbPlacementConstraints.Barrier>()),0,0);}
        catch(PcbPlacementPlanner.Rejected e){rejected=e.reason.equals("CONNECTOR_FACING_OUTWARD");}
        require(rejected,"connector escape must face inward at its declared edge");
        PhysicalPackage huge=P03StructuralFixtures.shape("oversize",20000,100,2);
        TroubleshootBoard oversized=new TroubleshootBoard("oversize");oversized.addNet(new BoardNet("N"));
        oversized.addComponent(new BoardComponent("U1","structural",huge));
        oversized.addPad(new BoardPad("U1.1","U1","1","N"));oversized.addPad(new BoardPad("U1.2","U1","2","N"));
        PcbFootprintRegistry registry=new PcbFootprintRegistry();registry.register(huge,new PcbFootprintProvider(){
            public PcbFootprint create(BoardComponent c,int x,int y,java.util.Random r,Rectangle o){return PcbFootprint.fromPhysicalPackage(c,x,y);}});
        rejected=false;try{new PcbPlacementPlanner(registry).plan(oversized,oversized.getPlacementConstraints(),0,0);}
        catch(PcbPlacementPlanner.Rejected e){rejected=e.reason.equals("WIDTH_LIMIT");}
        require(rejected,"unlimited outline growth is not a placement solution");
        Vector<PcbFootprint> island=new Vector<PcbFootprint>();
        int[][] walls={{100,100,800,90},{100,800,800,90},{100,160,120,700},{780,160,120,700},{430,430,100,110}};
        for(int i=0;i<walls.length;i++) {
            int[] w=walls[i];PhysicalPackage p=P03StructuralFixtures.shape("wall"+i,w[2],w[3],2);
            TroubleshootBoard wallBoard=new TroubleshootBoard("wall"+i);wallBoard.addNet(new BoardNet("N"));
            BoardComponent component=new BoardComponent("W"+i,"structural",p);wallBoard.addComponent(component);
            wallBoard.addPad(new BoardPad("W"+i+".1","W"+i,"1","N"));wallBoard.addPad(new BoardPad("W"+i+".2","W"+i,"2","N"));
            island.add(PcbFootprint.fromPhysicalPackage(component,w[0],w[1]));
        }
        rejected=false;try{PcbAccessPlanner.validate(new Rectangle(0,0,1000,1000),island);}
        catch(PcbPlacementPlanner.Rejected e){rejected=e.reason.equals("DISCONNECTED_ESCAPE_CHANNEL");}
        require(rejected,"an enclosed region with sufficient area still has no shared escape channel");
    }
}
