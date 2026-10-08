package com.lushprojects.circuitjs1.client;

public final class P07TwoLayerContractTest {
    private static int checks;
    static void require(boolean ok,String why) { checks++; if(!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        componentFaceEscapesReachFreeCopper();
        terminalViaOutletsRemainReserved();
        holeSnapshotsPreserveOwnerAndOrder();
        closedCopperEdgesMatchLongOracle();
        typedQueueMatchesPriorityQueue();
        nearestBranchesFollowSpatialTree();
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
    /** A declared pad escape must reach open copper through every intervening step. */
    private static void componentFaceEscapesReachFreeCopper() {
        for(PhysicalPackage pkg:new PhysicalPackage[]{PhysicalPackages.TO220_REGULATOR_4,
                PhysicalPackages.E04_DECISION_CONTROL_5}) {
            TroubleshootBoard board=new TroubleshootBoard("P07_COMPONENT_ESCAPE_"+pkg.getId());
            BoardComponent component=new BoardComponent("U","CONTROL",pkg);
            board.addComponent(component);
            for(String terminal:pkg.getTerminalIds()) {
                board.addNet(new BoardNet(terminal));
                board.addPad(new BoardPad("U."+terminal,"U",terminal,terminal));
            }
            board.validate();
            PcbBoardLayout layout=new PcbBoardLayout(1400,700,new Rectangle(20,20,1000,500),
                new Rectangle(1120,120,200,300));
            PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,200,100);
            layout.addComponent(footprint.getPlacement());
            for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
            layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:U","U",
                new Rectangle(50,50,30,20),14,false,null));
            layout.validateGeometry(board);
            PcbNetRouter.Router router=new PcbNetRouter.Router(layout,board,layout.getBoardOutline(),
                0,P06FactoryLinkFixtures.OBSERVER,PcbCopperLayer.TOP);
            for(PcbPadPlacement pad:footprint.getPads()) {
                String net=board.getPad(pad.getPadId()).getNetId();
                require(!router.permitsLayerStep(pad.getX(),pad.getY(),pad.getX()+10,pad.getY(),
                    net,pad,null),"component courtyard still blocks a sideways pad exit");
                // These packages place pads at local y=150 and their courtyard ends at205.
                // Walk past that actual boundary, including the old blocked 200-to210 step.
                for(int distance=0;distance<80;distance+=10)
                    require(router.permitsLayerStep(pad.getX(),pad.getY()+distance,
                        pad.getX(),pad.getY()+distance+10,net,pad,null),
                        pkg.getId()+" "+pad.getPadId()+" must escape its own courtyard at distance "+distance);
                require(router.permitsLayerStep(pad.getX(),pad.getY()+80,pad.getX()+10,pad.getY()+80,
                    net,pad,null),"pad exit reaches a usable free channel beyond its courtyard");
            }
        }
    }
    /** Earlier foreign copper must leave the first legal plated exit of a real controller accessible. */
    private static void terminalViaOutletsRemainReserved() {
        // Independent placed-package coordinates: reference pad, declared escape end, legal via,
        // forbidden earlier crossbar, and the next unobstructed crossbar. No reservation formula oracle.
        int[][] points={
            {480,550,480,610,480,620,470,630,490,630,470,650,490,650},
            {460,480,400,480,390,480,380,470,380,490,360,470,360,490},
            {580,460,580,400,580,390,570,380,590,380,570,360,590,360},
            {550,580,610,580,620,580,630,570,630,590,650,570,650,590}
        };
        PcbRotation[] rotations={PcbRotation.DEG_0,PcbRotation.DEG_90,PcbRotation.DEG_180,PcbRotation.DEG_270};
        PhysicalPackage production=PhysicalPackages.E04_DECISION_CONTROL_5;
        PhysicalPackageGeometry geometry=production.getGeometry();
        java.util.Vector<PhysicalPackage.GeometryVariant> variants=new java.util.Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT","IDENTITY",geometry));
        java.util.Vector<PcbRotation> declaredRotations=new java.util.Vector<PcbRotation>();
        for(PcbRotation rotation:rotations) declaredRotations.add(rotation);
        java.util.Vector<PcbBoardSide> sides=new java.util.Vector<PcbBoardSide>(); sides.add(PcbBoardSide.TOP);
        // Only this structural test declaration admits cardinal poses; production E04 stays DEG_0/TOP.
        PhysicalPackage cardinal=new PhysicalPackage("DEV_P07_CARDINAL_CONTROLLER_5",production.getTerminalIds(),
            new java.util.Vector<String>(),false,geometry,variants,"DEFAULT",
            PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT,declaredRotations,sides);
        for(int index=0;index<rotations.length;index++) {
            int[] p=points[index];
            TroubleshootBoard board=new TroubleshootBoard("P07_VIA_OUTLET_"+rotations[index]);
            PcbPackagePose pose=new PcbPackagePose(400,400,rotations[index],PcbBoardSide.TOP);
            require(production.supportsPose(pose)==(index==0),
                "production E04 admits only DEG_0 and rejects the three test-only cardinal poses");
            PhysicalPackage pkg=index==0?production:cardinal;
            BoardComponent component=new BoardComponent("U","CONTROL",pkg); board.addComponent(component);
            for(String terminal:pkg.getTerminalIds()) {
                board.addNet(new BoardNet(terminal));
                board.addPad(new BoardPad("U."+terminal,"U",terminal,terminal));
            }
            board.addNet(new BoardNet("FOREIGN"));
            PcbBoardLayout layout=new PcbBoardLayout(1400,1000,new Rectangle(20,20,1000,800),
                new Rectangle(1120,120,200,600));
            PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,pose,pkg.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
            int crossDx=(p[8]-p[6])/20,crossDy=(p[9]-p[7])/20;
            addPoint(board,layout,"A","FOREIGN",p[6]-90*crossDx,p[7]-90*crossDy);
            addPoint(board,layout,"B","FOREIGN",p[8]+90*crossDx,p[9]+90*crossDy);
            board.validate(); layout.validateAgainst(board);
            String before=layout.geometryFingerprint();
            PcbPadPlacement reference=layout.getPad("U.REFERENCE"),a=layout.getPad("A.1"),b=layout.getPad("B.1");
            require(reference.getX()==p[0] && reference.getY()==p[1] && reference.getEscapeLength()==60 &&
                reference.getX()+60*reference.getEscapeDx()==p[2] &&
                reference.getY()+60*reference.getEscapeDy()==p[3],"authoritative controller dimensions match independent cardinal fixture coordinates");
            final PcbBoardLayout blockedVia=layout.copyForRouting();
            blockedVia.addHole(PcbTwoLayerRules.via("declared-end","REFERENCE",p[2],p[3]));
            boolean courtyardRejected=false;
            try { new PcbTwoLayerRules(board,blockedVia).validate(blockedVia); }
            catch(IllegalStateException expected) { courtyardRejected=expected.getMessage().contains("courtyard"); }
            require(courtyardRejected,"declared escape endpoint cannot carry a via touching the real courtyard");
            PcbBoardLayout legalVia=layout.copyForRouting();
            legalVia.addHole(PcbTwoLayerRules.via("first-legal-exit","REFERENCE",p[4],p[5]));
            new PcbTwoLayerRules(board,legalVia).validate(legalVia);
            PcbNetRouter.Router ordinary=new PcbNetRouter.Router(layout,board,layout.getBoardOutline(),
                0,P06FactoryLinkFixtures.OBSERVER,PcbCopperLayer.TOP);
            require(ordinary.permitsLayerStep(p[6],p[7],p[8],p[9],"FOREIGN",a,b),
                "ordinary radius-zero routing keeps its previously legal crossbar");
            PcbNetRouter.Router[] fuller=outletFaces(board,layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER);
            require(!fuller[PcbCopperLayer.TOP.ordinal()].permitsLayerStep(p[6],p[7],p[8],p[9],"FOREIGN",a,b),
                "actual transition-capable P07 setup reserves the later terminal's legal via outlet");
            require(fuller[PcbCopperLayer.TOP.ordinal()].permitsLayerStep(p[10],p[11],p[12],p[13],"FOREIGN",a,b),
                "nearby foreign copper remains legal beyond the protected outlet");
            require(!fuller[PcbCopperLayer.TOP.ordinal()].permitsLayerStep(p[0],p[1],p[0]+10*crossDx,p[1]+10*crossDy,
                "REFERENCE",reference,null),"physical courtyard still rejects a premature sideways terminal exit");
            for(PcbLayerRoutingPrototype.Policy policy:new PcbLayerRoutingPrototype.Policy[]{
                    PcbLayerRoutingPrototype.Policy.ONE_LAYER,PcbLayerRoutingPrototype.Policy.SPARSE_LINK})
                require(outletFaces(board,layout,policy)[PcbCopperLayer.TOP.ordinal()].permitsLayerStep(
                    p[6],p[7],p[8],p[9],"FOREIGN",a,b),"actual zero-transition P07 policy retains ordinary reservation behavior");
            PcbNetRouter.Router opposite=new PcbNetRouter.Router(layout,board,layout.getBoardOutline(),
                0,P06FactoryLinkFixtures.OBSERVER,PcbCopperLayer.BOTTOM);
            require(opposite.permitsLayerStep(p[6],p[7],p[8],p[9],"FOREIGN",a,b) &&
                fuller[PcbCopperLayer.BOTTOM.ordinal()].permitsLayerStep(p[6],p[7],p[8],p[9],"FOREIGN",a,b),
                "opposite-face point reservations remain unchanged");
            require(before.equals(layout.geometryFingerprint()) && layout.getTraces().isEmpty() && layout.getHoles().isEmpty(),
                "outlet predicates and independent collision falsifiers preserve the actual input placement");
        }
    }
    private static PcbNetRouter.Router[] outletFaces(TroubleshootBoard board,PcbBoardLayout layout,
            PcbLayerRoutingPrototype.Policy policy) {
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(board,layout,policy,P06FactoryLinkFixtures.OBSERVER);
        require(!session.advanceSlice(1) && session.expansions()==0,"fixture installs actual P07 faces without queue-search work");
        try {
            java.lang.reflect.Field state=PcbLayerRoutingPrototype.Session.class.getDeclaredField("search");
            state.setAccessible(true); Object search=state.get(session);
            java.lang.reflect.Field faces=search.getClass().getDeclaredField("faces");
            faces.setAccessible(true); return (PcbNetRouter.Router[])faces.get(search);
        } catch(Exception failure) { throw new AssertionError("actual P07 face setup must be observable in this native contract",failure); }
    }

    /** Electrical membership is fixed; spatial proximity and canonical ties choose branches. */
    private static void nearestBranchesFollowSpatialTree() {
        TroubleshootBoard board=new TroubleshootBoard("P07_NEAREST_BRANCH");
        board.addNet(new BoardNet("N"));
        PcbBoardLayout placement=new PcbBoardLayout(1100,650,new Rectangle(100,100,600,400),
            new Rectangle(820,120,200,400));
        addPoint(board,placement,"A","N",170,200);
        addPoint(board,placement,"B","N",630,200);
        addPoint(board,placement,"C","N",250,200);
        addPoint(board,placement,"D","N",170,280);
        board.validate();
        String original=placement.geometryFingerprint();
        java.util.Vector<String> remaining=board.getNet("N").getPadIds();
        java.util.Collections.sort(remaining);
        remaining.remove("A.1");
        java.util.Vector<String> reached=new java.util.Vector<String>(); reached.add("A.1");
        require("C.1".equals(PcbNetRouter.chooseNext(placement,remaining,reached)),
            "80-unit nearest branches beat the 460-unit lexical B; canonical C wins C/D tie");
        reached.add("C.1"); remaining.remove("C.1");
        require("D.1".equals(PcbNetRouter.chooseNext(placement,remaining,reached)),
            "next branch minimizes distance to the entire reached tree");
        reached.add("D.1"); remaining.remove("D.1");
        require("B.1".equals(PcbNetRouter.chooseNext(placement,remaining,reached)),
            "the remaining endpoint is eventually routed");
        PcbLayerRoutingPrototype.Result result=PcbLayerRoutingPrototype.route(board,placement,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER);
        require(result.accepted(),"nearest-branch fixture routes with existing bounds");
        String[] expected={"C.1","D.1","B.1"};
        for(int branch=1;branch<=expected.length;branch++) {
            PcbTraceGeometry first=null;
            for(PcbTraceGeometry trace:result.layout.getTraces())
                if(("p07/N/"+branch+"/0").equals(trace.getSourceId())) first=trace;
            require(first!=null && expected[branch-1].equals(first.getStartPadId()),
                "P07 actually publishes the expected spatial branch order "+branch);
        }
        result.layout.validateRoutingGeometry(board);
        new PcbTwoLayerRules(board,result.layout).validate(result.layout);
        require(result.expansions<=PcbLayerRoutingPrototype.MAX_EXPANSIONS &&
            original.equals(placement.geometryFingerprint()),
            "nearest branches preserve the original placement and search bound");
        require(board.getNet("N").getPadIds().size()==4,
            "branch scheduling does not remove authoritative net endpoints");
    }
    private static void holeSnapshotsPreserveOwnerAndOrder() {
        PcbBoardLayout layout=new PcbBoardLayout(1100,650,new Rectangle(100,100,600,400),
            new Rectangle(820,120,200,400));
        require(layout.getHoleCount()==0 && layout.getHoles().isEmpty(),"empty hole owner");
        PcbBoardHole later=PcbTwoLayerRules.via("z-via","N",300,300);
        PcbBoardHole first=PcbTwoLayerRules.via("a-via","N",200,300);
        layout.addHole(later);
        java.util.Vector<PcbBoardHole> snapshot=layout.getHoles();
        layout.addHole(first);
        PcbBoardHole[] refreshed=layout.getHoles().toArray(new PcbBoardHole[0]);
        require(layout.getHoleCount()==2 && refreshed.length==2 &&
            refreshed[0]==first && refreshed[1]==later,"fresh snapshot sees additions in canonical hole-ID order");
        require(snapshot.size()==1 && snapshot.firstElement()==later,"existing hole snapshot remains independent");
        snapshot.clear(); refreshed[0]=null;
        require(layout.getHoleCount()==2 && layout.getHoles().firstElement()==first,
            "mutating snapshots cannot mutate their owner");
        PcbBoardLayout copy=layout.copyForRouting();
        copy.addHole(PcbTwoLayerRules.via("copy-via","N",400,300));
        require(copy.getHoleCount()==3 && layout.getHoleCount()==2,"routing-copy hole owners stay independent");
    }
    private static void closedCopperEdgesMatchLongOracle() {
        int[] boundary={Integer.MIN_VALUE,Integer.MIN_VALUE+1,-1,0,1,
            Integer.MAX_VALUE-1,Integer.MAX_VALUE};
        for(int ax:boundary) for(int aw:boundary) for(int bx:boundary) for(int bw:boundary) {
            Rectangle a=new Rectangle(ax,bx,aw,bw);
            Rectangle b=new Rectangle(bx,ax,bw,aw);
            require(PcbConductorBuilder.touch(a,b)==longTouch(a,b),
                "closed copper overlap matches long oracle at full int boundaries");
        }
        java.util.Random random=new java.util.Random(0x503037L);
        for(int i=0;i<8192;i++) {
            Rectangle a=new Rectangle(random.nextInt(),random.nextInt(),random.nextInt(),random.nextInt());
            Rectangle b=new Rectangle(random.nextInt(),random.nextInt(),random.nextInt(),random.nextInt());
            require(PcbConductorBuilder.touch(a,b)==longTouch(a,b),
                "closed copper overlap matches independent long oracle");
        }
        Rectangle land=new Rectangle(100,100,12,12);
        require(PcbConductorBuilder.touch(land,new Rectangle(112,112,12,12)),
            "closed corners remain conductive contact");
        require(!PcbConductorBuilder.touch(land,new Rectangle(113,112,12,12)),
            "one-unit gap stays separate");
    }
    private static boolean longTouch(Rectangle a,Rectangle b) {
        return (long)a.x+a.width>=b.x && (long)b.x+b.width>=a.x &&
            (long)a.y+a.height>=b.y && (long)b.y+b.height>=a.y;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void typedQueueMatchesPriorityQueue() {
        try {
            Class<?> nodeType=Class.forName(PcbLayerRoutingPrototype.class.getName()+"$Node");
            java.lang.reflect.Constructor<?> nodeConstructor=nodeType.getDeclaredConstructor(
                int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,nodeType);
            nodeConstructor.setAccessible(true);
            Class<?> queueType=Class.forName(PcbLayerRoutingPrototype.class.getName()+"$NodeQueue");
            java.lang.reflect.Constructor<?> queueConstructor=queueType.getDeclaredConstructor();
            queueConstructor.setAccessible(true);
            Object queue=queueConstructor.newInstance();
            java.lang.reflect.Method add=queueType.getDeclaredMethod("add",nodeType);
            java.lang.reflect.Method poll=queueType.getDeclaredMethod("poll");
            add.setAccessible(true); poll.setAccessible(true);
            java.util.PriorityQueue oracle=new java.util.PriorityQueue();
            require(poll.invoke(queue)==oracle.poll(),"empty typed queue matches PriorityQueue");
            for(int i=0;i<1024;i++) {
                int total=i%17,heuristic=(i/3)%(total+1),cost=total-heuristic;
                Object node=nodeConstructor.newInstance((i*11)%31,(i/13)%2,(i/7)%5,(i/37)%5,
                    cost,heuristic,i,31,null);
                add.invoke(queue,node); oracle.add(node);
                if(i%5==2 || i%7==3) require(poll.invoke(queue)==oracle.poll(),
                    "typed queue poll identity matches PriorityQueue during mixed operations");
            }
            Object tiedFirst=nodeConstructor.newInstance(4,1,2,3,20,6,2048,31,null);
            Object tiedSecond=nodeConstructor.newInstance(4,1,2,3,20,6,2049,31,null);
            require(((Comparable)tiedFirst).compareTo(tiedSecond)<0,"serial orders equal A* priority fields");
            add.invoke(queue,tiedFirst); oracle.add(tiedFirst);
            add.invoke(queue,tiedSecond); oracle.add(tiedSecond);
            while(!oracle.isEmpty()) require(poll.invoke(queue)==oracle.poll(),
                "typed queue preserves full PriorityQueue dequeue identity order");
            require(poll.invoke(queue)==null,"typed queue returns null after drain");
        } catch(Exception failure) {
            throw new AssertionError("typed route queue must match PriorityQueue identity order",failure);
        }
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
