package com.lushprojects.circuitjs1.client;

public final class P07TwoLayerContractTest {
    private static int checks;
    static void require(boolean ok,String why) { checks++; if(!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        for(boolean linked:new boolean[]{false,true}) {
            P06FactoryLinkFixtures.Fixture fixture=new P06FactoryLinkFixtures.Fixture(true,0,0,!linked);
            String original=fixture.layout.geometryFingerprint();
            for(PcbLayerRoutingPrototype.Policy policy:PcbLayerRoutingPrototype.Policy.values()) {
                PcbLayerRoutingPrototype.Result result=PcbLayerRoutingPrototype.route(fixture.board,fixture.layout,
                    policy,P06FactoryLinkFixtures.OBSERVER);
                require(original.equals(fixture.layout.geometryFingerprint()),"input placement was mutated");
                require(result.expansions<=PcbLayerRoutingPrototype.MAX_EXPANSIONS,"bounded work");
                System.out.println("P07_SMALL linked="+linked+" policy="+policy+" outcome="+result.outcome+
                    " expansions="+result.expansions+" vias="+result.vias+
                    " length="+(result.accepted()?PcbRouteMetrics.measure(result.layout.getTraces()).uniqueLength:0));
                if(result.accepted()) {
                    result.layout.validateRoutingGeometry(fixture.board);
                    new PcbTwoLayerRules(fixture.board,result.layout).validate(result.layout);
                    require(result.vias<=policy.vias,"via cap");
                }
            }
        }
        negatives(); barriers(); surfacePadEnvelope();
        System.out.println("PASS: P07 two-layer contracts assertions="+checks);
    }
    private static void reject(Runnable action,String why) {
        boolean rejected=false;
        try { action.run(); }
        catch(IllegalArgumentException expected) { rejected=true; }
        catch(IllegalStateException expected) { rejected=true; }
        require(rejected,why);
    }
    private static PcbBoardLayout copy(P06FactoryLinkFixtures.Fixture fixture,PcbBoardLayout routed,
            boolean holes,boolean bottom) {
        PcbBoardLayout result=fixture.layout.copyForRouting();
        for(PcbTraceGeometry trace:routed.getTraces())
            if(bottom || trace.getLayer()!=PcbCopperLayer.BOTTOM) result.addTrace(trace);
        if(holes) for(PcbBoardHole hole:routed.getHoles()) result.addHole(hole);
        return result;
    }
    private static void negatives() {
        final P06FactoryLinkFixtures.Fixture f=new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        final PcbLayerRoutingPrototype.Result result=PcbLayerRoutingPrototype.route(f.board,f.layout,
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER);
        require(result.accepted() && result.vias==2,"positive two-via crossing witness");
        PcbLayerRoutingPrototype.Result repeat=PcbLayerRoutingPrototype.route(f.board,f.layout,
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER);
        require(result.layout.geometryFingerprint().equals(repeat.layout.geometryFingerprint()) &&
            result.expansions==repeat.expansions,"deterministic source geometry, ordering and work");
        final PcbBoardLayout noVia=copy(f,result.layout,false,true);
        reject(new Runnable(){public void run(){noVia.validateRoutingGeometry(f.board);}},"unplated projected crossings cannot join layers");
        final PcbBoardLayout wrong=copy(f,result.layout,false,true);
        boolean changed=false;
        for(PcbBoardHole hole:result.layout.getHoles()) {
            wrong.addHole(PcbTwoLayerRules.via(hole.id,changed?hole.netId:
                hole.netId.equals("OVER")?"UNDER":"OVER",hole.x,hole.y)); changed=true;
        }
        reject(new Runnable(){public void run(){wrong.validateRoutingGeometry(f.board);}},"wrong-net via rejected");
        final PcbBoardLayout invisible=copy(f,result.layout,true,false);
        reject(new Runnable(){public void run(){invisible.validateRoutingGeometry(f.board);}},"omitted underside cannot satisfy required connectivity");
        reject(new Runnable(){public void run(){PcbTwoLayerRules.requireDeveloperAdmission(result.layout,false);}},"normal admission rejects prototype copper");
        PcbLayerRoutingPrototype.Result exhausted=PcbLayerRoutingPrototype.route(f.board,f.layout,
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER,1);
        require(!exhausted.accepted() && exhausted.expansions==1 && f.layout.getTraces().isEmpty() &&
            f.layout.getHoles().isEmpty(),"budget exhaustion publishes neither copper nor holes");
        final RuntimeException cancelled=new RuntimeException("P07 cancellation witness");
        boolean propagated=false;
        try { PcbLayerRoutingPrototype.route(f.board,f.layout,PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,
            new SeededPcbLayoutGenerator.AttemptObserver(){int calls;public void check(int attempt){if(++calls==4)throw cancelled;}}); }
        catch(RuntimeException expected) { propagated=expected==cancelled; }
        require(propagated && f.layout.getTraces().isEmpty() && f.layout.getHoles().isEmpty(),"mid-search cancellation propagates unchanged and atomic");
        PcbConductorGraph.Snapshot copper=result.layout.captureConductorGraph(f.board).pristine();
        require(copper.padsConnected("L.1","R.1") && copper.padsConnected("A.1","B.1") &&
            !copper.padsConnected("L.1","A.1"),"two physical islands cross without shorting");
        int bottomTargets=0;
        for(PcbConductorGraph.Surface surface:copper.getGraph().getSurfaces()) if(surface.edgeId!=null && surface.layer==PcbCopperLayer.BOTTOM) {
            require(!PcbCopperProbeAccess.available(copper,surface,PcbBoardSide.TOP) &&
                PcbCopperProbeAccess.available(copper,surface,PcbBoardSide.BOTTOM),"surface access requires its real face"); bottomTargets++;
            require(PcbCopperProbeAccess.connectedPad(copper.withCut(surface.edgeId,true),surface.id)==null,"cut surface has no phantom probe endpoint");
        }
        require(bottomTargets>0,"bottom-face negative cases executed");
        PcbBoardLayout floating=copy(f,result.layout,true,true);
        floating.addTrace(new PcbTraceGeometry("isolated","OVER",null,null,PcbCopperLayer.TOP,
            PcbCopperAccess.Exposure.EXPOSED,new int[]{480,480},new int[]{140,180}));
        PcbConductorGraph.Snapshot islands=floating.captureConductorGraph(f.board).pristine(); int isolated=0;
        for(PcbConductorGraph.Surface surface:islands.getGraph().getSurfaces()) if(surface.edgeId!=null &&
                islands.getGraph().getEdges().get(surface.edgeId).getSources().contains("isolated")) {
            require(PcbCopperProbeAccess.connectedPad(islands,surface.id)==null,"same logical label cannot attach a floating physical island"); isolated++;
        }
        require(isolated>0,"floating island falsifier executed");
    }
    private static void surfacePadEnvelope() {
        for(PcbBoardSide side:PcbBoardSide.values()) {
            TroubleshootBoard board=new TroubleshootBoard("P07_SMD_ENVELOPE");
            board.addNet(new BoardNet("ONE")); board.addNet(new BoardNet("TWO"));
            PhysicalPackage physical=PhysicalPackages.DEV_SMD_0805;
            BoardComponent part=new BoardComponent("SMD","STRUCTURAL",physical); board.addComponent(part);
            board.addPad(new BoardPad("SMD.1","SMD","1","ONE"));
            board.addPad(new BoardPad("SMD.2","SMD","2","TWO")); board.validate();
            PcbBoardLayout layout=new PcbBoardLayout(1100,650,new Rectangle(100,100,600,400),new Rectangle(820,120,200,400));
            PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(part,
                new PcbPackagePose(180,180,PcbRotation.DEG_0,side),physical.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
            layout.validateAgainst(board);
            boolean rejected=false;
            try { PcbLayerRoutingPrototype.route(board,layout,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER); }
            catch(IllegalArgumentException expected) {
                rejected=expected.getMessage().startsWith("P07 routing prototype requires plated-through-hole pads");
            }
            require(rejected,"SMD cannot acquire an implicit opposite-face connection: "+side);
            require(layout.getTraces().isEmpty() && layout.getHoles().isEmpty(),"unsupported package leaves input intact");
        }
    }
    private static void addPoint(TroubleshootBoard board,PcbBoardLayout layout,String id,String net,int x,int y) {
        PhysicalPackage physical=P06FactoryLinkFixtures.TEST_POINT;
        BoardComponent component=new BoardComponent(id,"STRUCTURAL",physical); board.addComponent(component);
        board.addPad(new BoardPad(id+".1",id,"1",net));
        PcbPackagePose reference=new PcbPackagePose(100,100,PcbRotation.DEG_0,PcbBoardSide.BOTTOM);
        Point local=physical.getGeometry().placedAt(reference).getPadPoint(0);
        PcbPackagePose pose=new PcbPackagePose(100+x-local.x,100+y-local.y,PcbRotation.DEG_0,PcbBoardSide.BOTTOM);
        PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,pose,physical.getGeometry());
        layout.addComponent(footprint.getPlacement()); for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
    }
    private static void barriers() {
        final TroubleshootBoard board=new TroubleshootBoard("P07_ISOLATION");
        board.addNet(new BoardNet("PRIMARY")); board.addNet(new BoardNet("SECONDARY"));
        final PcbBoardLayout placement=new PcbBoardLayout(1100,650,new Rectangle(100,100,600,400),new Rectangle(820,120,200,400));
        addPoint(board,placement,"P1","PRIMARY",170,200); addPoint(board,placement,"P2","PRIMARY",270,200);
        addPoint(board,placement,"S1","SECONDARY",530,200); addPoint(board,placement,"S2","SECONDARY",630,200);
        java.util.Vector<PcbPlacementConstraints.Part> parts=new java.util.Vector<PcbPlacementConstraints.Part>();
        for(String id:board.getComponentIds()) {
            String domain=id.startsWith("P")?"PRIMARY":"SECONDARY";
            parts.add(new PcbPlacementConstraints.Part(id,domain,domain,domain,PcbPlacementConstraints.Anchor.NONE,20));
        }
        java.util.Vector<PcbPlacementConstraints.Barrier> barriers=new java.util.Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(new PcbPlacementConstraints.Barrier("PRIMARY","SECONDARY",80));
        board.setPlacementConstraints(new PcbPlacementConstraints(parts,barriers)); board.validate();
        final PcbTwoLayerRules rules=new PcbTwoLayerRules(board,placement);
        PcbLayerRoutingPrototype.Result legal=PcbLayerRoutingPrototype.route(board,placement,
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER);
        require(legal.accepted(),"both domains route legally without crossing the corridor");
        for(PcbCopperLayer layer:PcbCopperLayer.values()) {
            final PcbBoardLayout illegal=placement.copyForRouting();
            illegal.addTrace(new PcbTraceGeometry("forbidden","PRIMARY",null,null,layer,
                PcbCopperAccess.Exposure.EXPOSED,new int[]{270,530},new int[]{200,200}));
            reject(new Runnable(){public void run(){rules.validate(illegal);}},"corridor blocks copper on "+layer);
        }
        final PcbBoardLayout illegalVia=placement.copyForRouting();
        illegalVia.addHole(PcbTwoLayerRules.via("forbidden-via","PRIMARY",400,300));
        reject(new Runnable(){public void run(){rules.validate(illegalVia);}},"via cannot tunnel through an isolation corridor");
        require(!rules.permits("PRIMARY",new Rectangle(530,300,10,10)),"net cannot reappear in the opposite isolated domain");
        final PcbBoardLayout padDrill=placement.copyForRouting();
        padDrill.addHole(PcbTwoLayerRules.via("pad-drill","PRIMARY",170,200));
        reject(new Runnable(){public void run(){rules.validate(padDrill);}},"via cannot drill through a component pad");
        reject(new Runnable(){public void run(){new PcbBoardHole("same-face",PcbBoardHole.Kind.VIA,"PRIMARY",200,300,2,6,
            PcbCopperLayer.TOP,PcbCopperLayer.TOP,PcbCopperAccess.Exposure.EXPOSED);}},"same-face declaration is not plating");
    }
}
