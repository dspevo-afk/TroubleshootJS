package com.lushprojects.circuitjs1.client;

import java.util.HashSet;

/** Independent topology/access expectations and frozen development/holdout routing cohort. */
public final class Q15ControlBoardContractTest {
    private static int assertions;
    static final long[] DEVELOPMENT={0,1,2,3};
    static final long[] HOLDOUT={17,42,101,-1,9007199254740993L,Long.MIN_VALUE,Long.MAX_VALUE};
    public static void main(String[] args) throws Exception {
        CirSim sim=new CirSim();sim.gridSize=16;sim.gridMask=~15;sim.gridRound=7;CircuitElm.sim=sim;
        HashSet<String> designs=new HashSet<String>(),geometries=new HashSet<String>();
        for(long[] cohort:new long[][]{DEVELOPMENT,HOLDOUT}) for(long seed:cohort) {
            Rb15Plan plan=Rb15Plan.resolve(seed); TroubleshootBoard board=plan.board();
            require(plan.canonical().equals(Rb15Plan.resolve(seed).canonical()),"current deterministic plan");
            require(QuickPlayFamilyRegistry.selectNormalPlayerSeed(Rb15Plan.FAMILY_ID,seed)==seed,"exact signed seed enters normal population");
            require(board.getComponentIds().size()==16,"sixteen purposeful packages in 5-20 envelope");
            require(board.getPadIds().size()==36,"complete package terminal inventory");
            require(board.getPowerInputIds().size()==2,"separate external power and command sources");
            require(board.getPad("K1.COM").getNetId().equals(board.getPad("K1.A1").getNetId()),"protected 12 V supplies coil and contacts");
            require(!board.getPad("K1.NO").getNetId().equals(board.getPad("K1.COM").getNetId()),"contact switch not visually shorted");
            require(board.getPad("DREV.A").getNetId().equals(board.getPad("F1.2").getNetId()),"fuse feeds actual series protection");
            require(board.getPad("RIN.2").getNetId().equals(board.getPad("RDRIVE.1").getNetId()),"conditioner is in real command path");
            require(board.getComponent(plan.filtered?"C2":"RBIAS")!=null && board.getComponent(plan.filtered?"RBIAS":"C2")==null,"structural support substitution");
            if(plan.filtered) require(board.getPad("C2.1").getNetId().equals(board.getPad("RPD.1").getNetId()) &&
                board.getPad("C2.2").getNetId().equals(board.getPad("RPD.2").getNetId()),
                "filter has an independent pull-down discharge path when drive resistor is open");
            designs.add(plan.topology());
            long start=System.nanoTime();
            GeneratedBoardInstance owner=GenerationRequest.leaf(Rb15Plan.FAMILY_ID,seed,false).resolve(new GenerationRequest.PlanCache()).construct().instance;
            PcbBoardLayout layout=owner.getPcbLayout();
            long millis=(System.nanoTime()-start)/1000000;
            layout.validateGeometry(board);
            require(layout.getComponents().size()==16 && layout.getPads().size()==36,"complete procedural realization");
            geometries.add(layout.geometryFingerprint());
            for(String pad:board.getPadIds()) {
                require(PcbCopperAccess.canProbe(layout.getPad(pad),PcbBoardSide.BOTTOM),"every solder terminal is an exposed probe target");
                require(owner.getSimulationBindings().getEndpoint(pad)!=null,"every target has a live electrical endpoint");
            }
            for(PcbTraceGeometry trace:layout.getTraces()) require(trace.getLayer()==PcbCopperLayer.BOTTOM,"one copper layer, no implicit crossings");
            owner.getTemporalBehavior().getDependency(owner);
            require(owner.getDiagnosticProvider().getCorrectCatalogId(owner,"K1").equals("RELAY_12V"),"answer follows the twelve-volt topology");
            if(seed==0) negatives(sim,owner,plan);
            System.out.println("Q15_ROUTE seed="+Long.toString(seed)+" cohort="+(cohort==DEVELOPMENT?"development":"holdout")+
                " design="+plan.topology()+" millis="+millis+" routeMillis="+layout.getGenerationRoutingMillis()+
                " attempts="+layout.getGenerationRoutingAttempts()+" expansions="+layout.getGenerationRoutingExpansions()+" score="+layout.getRouteQualityScore(board));
            for(CircuitElm element:owner.getSimulationElements()) element.delete();
        }
        require(designs.size()==4,"both drivers and both meaningful support populations");
        require(geometries.size()==DEVELOPMENT.length+HOLDOUT.length,"fresh procedural geometries across cohort");
        System.out.println("PASS: Q15 control board contracts assertions="+assertions+" designs="+designs.size());
    }
    private static void negatives(CirSim sim,GeneratedBoardInstance owner,Rb15Plan plan) throws Exception {
        GeneratedFaultCandidate valid=owner.getFaultCandidates().get(0);
        GeneratedFault f=valid.getFault();
        GeneratedFault[] invalid={
            new GeneratedFault(f.getId(),f.getType(),f.getTargetComponentId(),f.getCircuitFamilyId(),1),
            new GeneratedFault(f.getId(),f.getType(),"DREV",f.getCircuitFamilyId(),0),
            new GeneratedFault("unknown",f.getType(),f.getTargetComponentId(),f.getCircuitFamilyId(),0),
            new GeneratedFault(f.getId(),f.getType(),f.getTargetComponentId(),"RELAY_OUTPUT",0),
            new GeneratedFault(f.getId(),GeneratedFaultType.LED_OPEN,"LED1",f.getCircuitFamilyId(),0),
            new GeneratedFault(f.getId(),f.getType(),f.getTargetComponentId(),f.getCircuitFamilyId(),0,1000,2000)};
        for(GeneratedFault wrong:invalid) rejectedHypothesis(owner,new GeneratedFaultCandidate(new GeneratedFaultBinding(wrong,valid.getBinding().getEffect()),true));
        rejectedHypothesis(owner,new GeneratedFaultCandidate(valid.getBinding(),false));
        GeneratedBoardInstance authored=new RelayOutputGenerator().generate(0);
        try {plan.withRoutedLayout(authored.getPcbLayout());throw new AssertionError("Authored reference masqueraded as a resolved procedural plan");}
        catch(IllegalStateException expected) {assertions++;}
        try {Rb15Plan.resolve(2).withRoutedLayout(owner.getPcbLayout());throw new AssertionError("Foreign seed reused a fixed full-board layout");}
        catch(IllegalStateException expected) {assertions++;}
        try {new RelayOutputGenerator().generateResolved(1,null,plan);throw new AssertionError("Mismatched seed reached materialization");}
        catch(IllegalArgumentException expected) {assertions++;}
        require(authored.getDiagnosticProvider().getCorrectCatalogId(authored,"K1").equals("RELAY_5V"),"same package needs a different coil in isolated five-volt circuit");
        try {authored.getPcbLayout().validateGeometry(owner.getBoard());throw new AssertionError("Authored nine-part reference accepted as RB15");}
        catch(IllegalStateException expected) {assertions++;}
        finally {for(CircuitElm element:authored.getSimulationElements())element.delete();}
        PcbBoardLayout missing=new PcbBoardLayout(owner.getPcbLayout().getWidth(),owner.getPcbLayout().getHeight(),owner.getPcbLayout().getBoardOutline(),new Rectangle(10000,10000,100,100));
        for(PcbComponentPlacement component:owner.getPcbLayout().getComponents())missing.addComponent(component);
        for(PcbPadPlacement pad:owner.getPcbLayout().getPads())if(!pad.getPadId().equals("K1.NC"))missing.addPad(pad);
        for(PcbTraceGeometry trace:owner.getPcbLayout().getTraces())missing.addTrace(trace);
        try {missing.validateGeometry(owner.getBoard());throw new AssertionError("Inaccessible NC terminal accepted");}
        catch(IllegalStateException expected) {assertions++;}
        try {missing.addComponent(owner.getPcbLayout().getComponent("K1"));throw new AssertionError("Copied package accepted as another owner");}
        catch(IllegalArgumentException expected) {assertions++;}
        java.lang.reflect.Method dump=GenerationDependencyContext.class.getDeclaredMethod("stableElementDump",CircuitElm.class);dump.setAccessible(true);
        ProtectionFuseElm fuse=(ProtectionFuseElm)owner.getComponentBindings().getSingleElement("F1");
        String cold=(String)dump.invoke(null,fuse);fuse.heat=.01;
        require(!cold.equals(dump.invoke(null,fuse)),"persistent fuse heating is a consumed dependency");fuse.heat=0;fuse.blown=true;
        require(!cold.equals(dump.invoke(null,fuse)),"blown fuse cannot reuse healthy proof");fuse.blown=false;
        edgeReservationOracle(owner.getBoard(),owner.getPcbLayout());
        GenerationRequest.ConstructionSession session=GenerationRequest.leaf(Rb15Plan.FAMILY_ID,0,false).resolve(new GenerationRequest.PlanCache()).beginConstruction();
        try {session.result();throw new AssertionError("Incomplete construction has a result");}catch(IllegalStateException expected){assertions++;}
        int turns=0;while(!session.advance())turns++;
        GeneratedBoardInstance staged=session.result().instance;
        require(turns>0 && staged.getPcbLayout().geometryFingerprint().equals(owner.getPcbLayout().geometryFingerprint()),"resumable and synchronous planning produce identical geometry");
        require(staged.getPcbLayout().isSealed(),"shared routing plan is immutable");
        try {staged.getPcbLayout().addTrace(owner.getPcbLayout().getTraces().get(0));throw new AssertionError("Shared geometry was mutable");}catch(IllegalStateException expected){assertions++;}
        GeneratedBoardInstance hypothesis=staged.getDiagnosticProvider().generateHypothesis(staged.getFaultCandidates().get(0));
        require(staged.getPcbLayout()!=hypothesis.getPcbLayout() &&
            staged.getPcbLayout().geometryFingerprint().equals(hypothesis.getPcbLayout().geometryFingerprint()),
            "diagnostic owners materialize separate containers from frozen geometry");
        FreshGeneratedRuntimeInstallation.requireDisjoint(staged,hypothesis);
        for(CircuitElm element:staged.getSimulationElements())require(!hypothesis.getSimulationElements().contains(element),"hypotheses have disjoint actual solver graphs");
        for(CircuitElm element:staged.getSimulationElements())element.delete();for(CircuitElm element:hypothesis.getSimulationElements())element.delete();
    }
    private static void edgeReservationOracle(TroubleshootBoard board,PcbBoardLayout layout) throws Exception {
        Class<?> type=Class.forName("com.lushprojects.circuitjs1.client.PcbNetRouter$Router");
        java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        Rectangle outline=layout.getBoardOutline();int minX=outline.x+10,minY=outline.y+10;
        Object router=constructor.newInstance(layout,board,outline,0,new SeededPcbLayoutGenerator.AttemptObserver(){public void check(int attempt){}});
        java.lang.reflect.Method traverse=type.getDeclaredMethod("canTraverse",int.class,int.class,int.class,int.class,PcbPadPlacement.class,PcbPadPlacement.class,String.class);traverse.setAccessible(true);
        PcbPadPlacement start=layout.getPad("J1.1");
        for(PcbPadPlacement center:layout.getPads()) for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++)for(int direction=0;direction<2;direction++) {
            // Compaction translates the finished pads independently of the
            // outline margin. Probe actual router grid edges, not off-grid
            // coordinates later truncated by the reflective call.
            int x=minX+((center.getX()-minX)/10+dx)*10,y=minY+((center.getY()-minY)/10+dy)*10;
            int nx=x+(direction==0?10:0),ny=y+(direction==1?10:0);
            if(x<minX||y<minY||nx>outline.x+outline.width-10||ny>outline.y+outline.height-10)continue;
            Rectangle edge=new Rectangle(x,y,direction==0?10:1,direction==1?10:1);
            for(String net:new String[]{"RAW_INPUT","CTRL_RETURN","NC"}) {
                boolean expected=true;
                for(PcbPadPlacement pad:layout.getPads()) if(!net.equals(board.getPad(pad.getPadId()).getNetId())) {
                    Rectangle reservation=new Rectangle(pad.getX()-14,pad.getY()-14,28,28);
                    if(reservation.intersects(edge))expected=false;
                }
                boolean actual=(Boolean)traverse.invoke(router,(x-minX)/10,(y-minY)/10,(nx-minX)/10,(ny-minY)/10,start,null,net);
                require(actual==expected,"cached edge predicate equals independent pad-clearance rectangle oracle at "+x+","+y+" net="+net);
            }
        }
    }
    private static void rejectedHypothesis(GeneratedBoardInstance owner,GeneratedFaultCandidate wrong) {
        try {owner.getDiagnosticProvider().generateHypothesis(wrong);throw new AssertionError("Unsupported hypothesis accepted");}
        catch(IllegalArgumentException expected) {assertions++;}
    }
    private static void require(boolean value,String message) { assertions++; if(!value)throw new AssertionError("Q15: "+message); }
}
