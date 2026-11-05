package com.lushprojects.circuitjs1.client;

public final class P07TwoLayerContractTest {
    private static int checks;
    static void require(boolean ok,String why) { checks++; if(!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        gridOwnershipBoundaries();
        componentFaceEscapesReachFreeCopper();
        terminalViaOutletsRemainReserved();
        holeSnapshotsPreserveOwnerAndOrder();
        closedCopperEdgesMatchLongOracle();
        typedQueueMatchesPriorityQueue();
        primitiveStateIndexMatchesHashMapOracle();
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
        viaCapBounds();
        sameSessionPriorViaTreeJoin();
        negatives(); barriers(); surfacePadEnvelope();
        P07BranchTreeOrderContractTest.run();
        System.out.println("PASS: P07 two-layer contracts assertions="+checks);
    }
    private static void gridOwnershipBoundaries() {
        // Non-square outline, translated off the global 10-unit grid.
        // Both near-limit translations remain inside the +/-100,000,000 domain.
        for (int[] origin : new int[][] {
                {23, -17}, {99999003, -99998997}, {-99998997, 99999003}}) {
            int ox = origin[0], oy = origin[1];
            TroubleshootBoard board = new TroubleshootBoard("P07_GRID_OWNERSHIP");
            BoardComponent component = new BoardComponent("R", "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR);
            board.addComponent(component);
            board.addNet(new BoardNet("A"));
            board.addNet(new BoardNet("B"));
            board.addPad(new BoardPad("R.1", "R", "1", "A"));
            board.addPad(new BoardPad("R.2", "R", "2", "B"));
            board.validate();
            PcbBoardLayout layout = new PcbBoardLayout(1000, 800,
                new Rectangle(ox, oy, 600, 400), new Rectangle(700, 500, 200, 200));
            PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component,
                ox + 100, oy + 100);
            layout.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
            String original = layout.geometryFingerprint();
            require(
                footprint.getPlacement().getMountingSide() == PcbBoardSide.TOP,
                "ownership fixture has its obstructing component on top");

            for (PcbCopperLayer face : new PcbCopperLayer[] {
                    PcbCopperLayer.TOP, PcbCopperLayer.BOTTOM}) {
                PcbNetRouter.Router router = new PcbNetRouter.Router(layout, board,
                    layout.getBoardOutline(), 0, P06FactoryLinkFixtures.OBSERVER, face);
                // Seed isolated occupied points using the maintained physical-coordinate API.
                // The (210,130) point deliberately seeds same-net copper inside R's
                // courtyard: this is a join-predicate fixture, not routing admission.
                java.util.Vector<Point> owned = new java.util.Vector<Point>();
                for (int[] offset : new int[][] {{10,10}, {590,10}, {10,390},
                        {590,390}, {30,40}, {210,130}})
                    owned.add(new Point(ox + offset[0], oy + offset[1]));
                router.occupyLayerPath(owned, "A");
                java.util.Vector<Point> foreign = new java.util.Vector<Point>();
                foreign.add(new Point(ox + 510, oy + 310));
                router.occupyLayerPath(foreign, "B");

                // Literal expected grid indices: 59 columns and 39 rows.
                for (int[] corner : new int[][] {{0,0}, {58,0}, {0,38}, {58,38}})
                    require(
                        router.ownsLayerGridCell(corner[0], corner[1], "A"),
                        "occupied boundary cell is owned on " + face);
                require(router.ownsLayerGridCell(2, 3, new String("A")),
                    "ownership compares net values at the translated asymmetric cell");
                require(!router.ownsLayerGridCell(50, 30, "A") &&
                    router.ownsLayerGridCell(50, 30, "B"), "foreign copper keeps its owner");
                require(!router.ownsLayerGridCell(7, 5, "A"),
                    "empty cell is not owned");
                require(!router.ownsLayerGridCell(3, 3, "A"),
                    "same-net clearance reservation is not occupied copper");
                require(!router.ownsLayerGridCell(3, 2, "A"),
                    "asymmetric grid axes are not transposed");
                require(
                    router.ownsLayerGridCell(20, 12, "A") == (face == PcbCopperLayer.BOTTOM),
                    "same-net copper under top component cannot join on top, but can on bottom");
                for (int[] outside : new int[][] {{-1,0}, {0,-1}, {59,0}, {0,39},
                        {Integer.MIN_VALUE,0}, {0,Integer.MAX_VALUE}})
                    require(
                        !router.ownsLayerGridCell(outside[0], outside[1], "A"),
                        "out-of-grid ownership is false without array access");
                require(original.equals(layout.geometryFingerprint()),
                    "private ownership fixture leaves layout geometry unchanged");
            }
        }
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
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void primitiveStateIndexMatchesHashMapOracle() {
        try {
            Class<?> nodeType=Class.forName(PcbLayerRoutingPrototype.class.getName()+"$Node");
            java.lang.reflect.Constructor<?> nodeConstructor=nodeType.getDeclaredConstructor(
                int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,nodeType);
            nodeConstructor.setAccessible(true);
            Class<?> indexType=Class.forName(PcbLayerRoutingPrototype.class.getName()+"$NodeIndex");
            java.lang.reflect.Constructor<?> indexConstructor=indexType.getDeclaredConstructor();
            indexConstructor.setAccessible(true);
            java.lang.reflect.Method get=indexType.getDeclaredMethod("get",int.class);
            java.lang.reflect.Method put=indexType.getDeclaredMethod("put",int.class,nodeType);
            get.setAccessible(true); put.setAccessible(true);

            Object index=indexConstructor.newInstance();
            java.util.HashMap<Integer,Object> oracle=new java.util.HashMap<Integer,Object>();
            int maximumKey=12_499_999;
            int[] boundaryKeys={0,127,128,maximumKey};
            for(int serial=0;serial<boundaryKeys.length;serial++) {
                int key=boundaryKeys[serial];
                Object node=stateIndexNode(nodeConstructor,key,serial,40,0);
                put.invoke(index,key,node); oracle.put(Integer.valueOf(key),node);
                require(get.invoke(index,key)==oracle.get(Integer.valueOf(key)),
                    "primitive index returns the exact Node reference for key="+key);
            }
            require(indexSize(indexType,index)==4 && indexCapacity(indexType,index)==128,
                "boundary keys fit the small initial primitive table");
            require(get.invoke(index,126)==null && get.invoke(index,maximumKey-1)==null,
                "primitive index misses match HashMap misses");

            Object same=oracle.get(Integer.valueOf(0));
            put.invoke(index,0,same); oracle.put(Integer.valueOf(0),same);
            require(get.invoke(index,0)==same && indexSize(indexType,index)==4 &&
                indexCapacity(indexType,index)==128,
                "putting the same reference replaces no entry and does not grow");
            Object replacement=stateIndexNode(nodeConstructor,127,10,12,0);
            put.invoke(index,127,replacement); oracle.put(Integer.valueOf(127),replacement);
            require(get.invoke(index,127)==replacement && indexSize(indexType,index)==4,
                "same-key replacement publishes the exact new Node without changing size");

            for(int key=1;key<=60;key++) {
                Object node=stateIndexNode(nodeConstructor,key,key,100-key,0);
                put.invoke(index,key,node); oracle.put(Integer.valueOf(key),node);
            }
            require(indexSize(indexType,index)==64 && indexCapacity(indexType,index)==128,
                "primitive index remains at initial capacity through the half-load threshold");
            Object thresholdReplacement=stateIndexNode(nodeConstructor,0,11,11,0);
            put.invoke(index,0,thresholdReplacement); oracle.put(Integer.valueOf(0),thresholdReplacement);
            require(indexSize(indexType,index)==64 && indexCapacity(indexType,index)==128 &&
                get.invoke(index,0)==thresholdReplacement,
                "replacement at half load changes no size or capacity");
            Object growNode=stateIndexNode(nodeConstructor,61,61,39,0);
            put.invoke(index,61,growNode); oracle.put(Integer.valueOf(61),growNode);
            require(indexSize(indexType,index)==65 && indexCapacity(indexType,index)==256,
                "primitive index doubles only when a new key crosses half load");
            for(java.util.Map.Entry<Integer,Object> entry:oracle.entrySet())
                require(get.invoke(index,entry.getKey().intValue())==entry.getValue(),
                    "rehash preserves exact Node reference for key="+entry.getKey());
            require(get.invoke(index,62)==null,
                "post-growth miss still matches the independent HashMap oracle");

            Object growthIndex=indexConstructor.newInstance();
            java.util.HashMap<Integer,Object> growthOracle=
                new java.util.HashMap<Integer,Object>();
            for(int key=0;key<1024;key++) {
                Object node=stateIndexNode(nodeConstructor,key,500+key,100+(key%31),0);
                put.invoke(growthIndex,key,node); growthOracle.put(Integer.valueOf(key),node);
                if(key==63 || key==127 || key==255 || key==511)
                    require(indexCapacity(indexType,growthIndex)==128<<
                        (key==63?0:key==127?1:key==255?2:3),
                        "multi-growth index matches capacity before threshold key="+key);
                if(key==64 || key==128 || key==256 || key==512 || key==1023)
                    require(indexCapacity(indexType,growthIndex)==256<<
                        (key==64?0:key==128?1:key==256?2:3),
                        "multi-growth index doubles at expected threshold key="+key);
                if(key%97==0) {
                    int oldSize=indexSize(indexType,growthIndex);
                    int oldCapacity=indexCapacity(indexType,growthIndex);
                    Object replacementNode=stateIndexNode(nodeConstructor,key,2000+key,5,0);
                    put.invoke(growthIndex,key,replacementNode);
                    growthOracle.put(Integer.valueOf(key),replacementNode);
                    require(indexSize(indexType,growthIndex)==oldSize &&
                        indexCapacity(indexType,growthIndex)==oldCapacity &&
                        get.invoke(growthIndex,key)==replacementNode,
                        "replacement across repeated growths allocates no table entry");
                }
            }
            require(indexSize(indexType,growthIndex)==1024 &&
                indexCapacity(indexType,growthIndex)==2048,
                "1024 entries survive several primitive-table resizes");
            for(java.util.Map.Entry<Integer,Object> entry:growthOracle.entrySet())
                require(get.invoke(growthIndex,entry.getKey().intValue())==entry.getValue(),
                    "multi-growth rehash matches HashMap for key="+entry.getKey());
            require(get.invoke(growthIndex,1024)==null,
                "multi-growth index preserves a missing-key result");

            int[] collision=findInitialCollision(indexConstructor,nodeConstructor,put,indexType);
            Object collisionIndex=indexConstructor.newInstance();
            java.util.HashMap<Integer,Object> collisionOracle=
                new java.util.HashMap<Integer,Object>();
            Object first=stateIndexNode(nodeConstructor,collision[0],200,20,0);
            Object second=stateIndexNode(nodeConstructor,collision[1],201,19,0);
            put.invoke(collisionIndex,collision[0],first);
            collisionOracle.put(Integer.valueOf(collision[0]),first);
            put.invoke(collisionIndex,collision[1],second);
            collisionOracle.put(Integer.valueOf(collision[1]),second);
            int firstSlot=indexSlot(indexType,collisionIndex,collision[0]);
            int secondSlot=indexSlot(indexType,collisionIndex,collision[1]);
            require(secondSlot==((firstSlot+1)&127),
                "two keys with one initial bucket resolve by linear probing");
            require(get.invoke(collisionIndex,collision[0])==collisionOracle.get(
                Integer.valueOf(collision[0])) && get.invoke(collisionIndex,collision[1])==
                collisionOracle.get(Integer.valueOf(collision[1])),
                "colliding keys remain independently addressable");

            Class<?> queueType=Class.forName(PcbLayerRoutingPrototype.class.getName()+"$NodeQueue");
            java.lang.reflect.Constructor<?> queueConstructor=queueType.getDeclaredConstructor();
            queueConstructor.setAccessible(true);
            java.lang.reflect.Method add=queueType.getDeclaredMethod("add",nodeType);
            java.lang.reflect.Method poll=queueType.getDeclaredMethod("poll");
            add.setAccessible(true); poll.setAccessible(true);
            Object routeIndex=indexConstructor.newInstance(),queue=queueConstructor.newInstance();
            java.util.HashMap<Integer,Object> bestOracle=new java.util.HashMap<Integer,Object>();
            java.util.PriorityQueue queueOracle=new java.util.PriorityQueue();
            P06FactoryLinkFixtures.Fixture searchFixture=
                new P06FactoryLinkFixtures.Fixture(true,0,0);
            PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(
                searchFixture.board,searchFixture.layout,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER);
            boolean sessionComplete=session.advanceSlice(1);
            java.lang.reflect.Field searchField=PcbLayerRoutingPrototype.Session.class.getDeclaredField("search");
            searchField.setAccessible(true);
            Object actualSearch=searchField.get(session);
            require(!sessionComplete && actualSearch!=null,
                "one P07 slice initializes a real private Search");
            java.lang.reflect.Method offer=actualSearch.getClass().getDeclaredMethod("offer",
                queueType,indexType,nodeType);
            offer.setAccessible(true);
            Object stale=stateIndexNode(nodeConstructor,128,300,50,0);
            Object equalCost=stateIndexNode(nodeConstructor,128,301,50,0);
            Object current=stateIndexNode(nodeConstructor,128,302,20,0);
            offer.invoke(actualSearch,queue,routeIndex,stale);
            bestOracle.put(Integer.valueOf(128),stale); queueOracle.add(stale);
            require(get.invoke(routeIndex,128)==stale && queueSize(queueType,queue)==1 &&
                bestOracle.get(Integer.valueOf(128))==stale && queueOracle.size()==1,
                "literal cost-50 state is admitted by Search.offer");
            offer.invoke(actualSearch,queue,routeIndex,equalCost);
            require(get.invoke(routeIndex,128)==stale && queueSize(queueType,queue)==1 &&
                bestOracle.get(Integer.valueOf(128))==stale && queueOracle.size()==1 &&
                !queueOracle.contains(equalCost),
                "literal equal-cost-50 state is rejected without replacing the reference");
            offer.invoke(actualSearch,queue,routeIndex,current);
            bestOracle.put(Integer.valueOf(128),current); queueOracle.add(current);
            require(get.invoke(routeIndex,128)==current && queueSize(queueType,queue)==2 &&
                bestOracle.get(Integer.valueOf(128))==current && queueOracle.size()==2,
                "literal lower-cost-20 state replaces key 128");
            int stalePops=0,currentPops=0;
            while(!queueOracle.isEmpty()) {
                Object actual=poll.invoke(queue),expected=queueOracle.poll();
                require(actual==expected,"primitive-index search queue pop matches PriorityQueue identity");
                int key=objectIntField(actual,"key");
                Object actualBest=get.invoke(routeIndex,key);
                Object expectedBest=bestOracle.get(Integer.valueOf(key));
                require(actualBest==expectedBest,"best-state reference matches HashMap oracle after pop");
                if(actual==actualBest) currentPops++; else stalePops++;
            }
            require(currentPops==1 && stalePops==1,
                "replaced queue entry is recognized as stale by reference identity");

            Object serialQueue=queueConstructor.newInstance();
            java.util.PriorityQueue serialOracle=new java.util.PriorityQueue();
            Object serialFirst=stateIndexNode(nodeConstructor,77,401,9,3);
            Object serialSecond=stateIndexNode(nodeConstructor,77,402,9,3);
            add.invoke(serialQueue,serialFirst); serialOracle.add(serialFirst);
            add.invoke(serialQueue,serialSecond); serialOracle.add(serialSecond);
            while(!serialOracle.isEmpty()) require(poll.invoke(serialQueue)==serialOracle.poll(),
                "serial tie-break order matches PriorityQueue");
        } catch(Exception failure) {
            throw new AssertionError("primitive branch-state index must match HashMap semantics",failure);
        }
    }
    private static Object stateIndexNode(java.lang.reflect.Constructor<?> constructor,int key,
            int serial,int cost,int heuristic) throws Exception {
        return constructor.newInstance(key,0,0,0,cost,heuristic,serial,1,null);
    }
    private static int[] findInitialCollision(java.lang.reflect.Constructor<?> indexConstructor,
            java.lang.reflect.Constructor<?> nodeConstructor,java.lang.reflect.Method put,
            Class<?> indexType) throws Exception {
        int[] firstBySlot=new int[128]; java.util.Arrays.fill(firstBySlot,-1);
        for(int key=0;key<129;key++) {
            Object probe=indexConstructor.newInstance();
            put.invoke(probe,key,stateIndexNode(nodeConstructor,key,key,1,0));
            int slot=indexSlot(indexType,probe,key);
            if(firstBySlot[slot]>=0) return new int[] {firstBySlot[slot],key};
            firstBySlot[slot]=key;
        }
        throw new AssertionError("pigeonhole collision was not found in initial table");
    }
    private static int indexSlot(Class<?> indexType,Object index,int key) throws Exception {
        java.lang.reflect.Field keys=indexType.getDeclaredField("keys");
        java.lang.reflect.Field values=indexType.getDeclaredField("values");
        keys.setAccessible(true); values.setAccessible(true);
        int[] storedKeys=(int[])keys.get(index); Object[] storedValues=(Object[])values.get(index);
        for(int slot=0;slot<storedValues.length;slot++)
            if(storedValues[slot]!=null && storedKeys[slot]==key) return slot;
        throw new AssertionError("primitive index is missing key="+key);
    }
    private static int indexSize(Class<?> indexType,Object index) throws Exception {
        java.lang.reflect.Field size=indexType.getDeclaredField("size");
        size.setAccessible(true); return size.getInt(index);
    }
    private static int indexCapacity(Class<?> indexType,Object index) throws Exception {
        java.lang.reflect.Field values=indexType.getDeclaredField("values");
        values.setAccessible(true); return ((Object[])values.get(index)).length;
    }
    private static int objectIntField(Object target,String name) throws Exception {
        java.lang.reflect.Field field=target.getClass().getDeclaredField(name);
        field.setAccessible(true); return field.getInt(target);
    }
    private static int queueSize(Class<?> queueType,Object queue) throws Exception {
        java.lang.reflect.Field size=queueType.getDeclaredField("size");
        size.setAccessible(true); return size.getInt(queue);
    }
    private static void reject(Runnable action,String why) {
        boolean rejected=false;
        try { action.run(); }
        catch(IllegalArgumentException expected) { rejected=true; }
        catch(IllegalStateException expected) { rejected=true; }
        require(rejected,why);
    }
    private static void rejectIllegalArgument(Runnable action,String why) {
        boolean rejected=false;
        try { action.run(); }
        catch(IllegalArgumentException expected) { rejected=true; }
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
    private static void viaCapBounds() {
        final PcbLayerRoutingPrototype.Policy policy=PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER;
        final BoundaryCrossingFixture ordinaryFixture=new BoundaryCrossingFixture();
        final PcbLayerRoutingPrototype.Result ordinary=PcbLayerRoutingPrototype.route(
            ordinaryFixture.board,ordinaryFixture.layout,policy,P06FactoryLinkFixtures.OBSERVER);
        System.out.println("P07_VIA_CAP ordinary outcome="+ordinary.outcome+
            " accepted="+ordinary.accepted()+" expansions="+ordinary.expansions+" vias="+ordinary.vias);
        require(ordinary.accepted() && ordinary.vias==2,
            "ordinary restricted route establishes the constrained two-via witness");
        ordinary.layout.validateRoutingGeometry(ordinaryFixture.board);
        new PcbTwoLayerRules(ordinaryFixture.board,ordinary.layout).validate(ordinary.layout);

        final BoundaryCrossingFixture f=new BoundaryCrossingFixture();
        final String original=f.layout.geometryFingerprint();
        final String originalGraph=f.layout.captureConductorGraph(f.board).toCanonical();
        final PcbLayerRoutingPrototype.Result capped=PcbLayerRoutingPrototype.route(
            f.board,f.layout,policy,P06FactoryLinkFixtures.OBSERVER,
            PcbLayerRoutingPrototype.MAX_EXPANSIONS,2);
        require(capped.accepted() && capped.vias==2 && capped.vias<=2,
            "explicit two-via cap accepts the constrained boundary crossing");
        capped.layout.validateRoutingGeometry(f.board);
        new PcbTwoLayerRules(f.board,capped.layout).validate(capped.layout);
        PcbConductorGraph.Snapshot copper=capped.layout.captureConductorGraph(f.board).pristine();
        require(copper.padsConnected("L.1","R.1") && copper.padsConnected("A.1","B.1") &&
            !copper.padsConnected("L.1","A.1"),
            "capped crossing connects each endpoint pair without a cross-net short");

        final PcbLayerRoutingPrototype.Result repeat=PcbLayerRoutingPrototype.route(
            f.board,f.layout,policy,P06FactoryLinkFixtures.OBSERVER,
            PcbLayerRoutingPrototype.MAX_EXPANSIONS,2);
        require(repeat.accepted() && capped.layout.geometryFingerprint().equals(repeat.layout.geometryFingerprint()) &&
            capped.expansions==repeat.expansions && capped.vias==repeat.vias,
            "repeated capped route is deterministic in geometry and work");
        require(original.equals(f.layout.geometryFingerprint()) &&
            originalGraph.equals(f.layout.captureConductorGraph(f.board).toCanonical()),
            "capped route preserves the input placement and conductor graph");

        final PcbLayerRoutingPrototype.Result cappedOne=PcbLayerRoutingPrototype.route(
            f.board,f.layout,policy,P06FactoryLinkFixtures.OBSERVER,
            PcbLayerRoutingPrototype.MAX_EXPANSIONS,1);
        System.out.println("P07_VIA_CAP boundary cap=1 outcome="+cappedOne.outcome+
            " accepted="+cappedOne.accepted()+" expansions="+cappedOne.expansions+
            " vias="+cappedOne.vias);
        require(!cappedOne.accepted() && cappedOne.layout==null && f.layout.getTraces().isEmpty() &&
            f.layout.getHoles().isEmpty() && original.equals(f.layout.geometryFingerprint()) &&
            originalGraph.equals(f.layout.captureConductorGraph(f.board).toCanonical()),
            "one-via cap rejects the constrained crossing without partial copper or input mutation");

        final PcbLayerRoutingPrototype.Result ordinaryAfter=PcbLayerRoutingPrototype.route(
            f.board,f.layout,policy,P06FactoryLinkFixtures.OBSERVER);
        require(ordinaryAfter.accepted() && ordinary.layout.geometryFingerprint().equals(
            ordinaryAfter.layout.geometryFingerprint()) && ordinary.expansions==ordinaryAfter.expansions &&
            ordinary.vias==ordinaryAfter.vias,
            "ordinary route after capped failure retains geometry and work without cap leakage");
        rejectIllegalArgument(new Runnable(){public void run(){PcbLayerRoutingPrototype.route(
            f.board,f.layout,policy,P06FactoryLinkFixtures.OBSERVER,
            PcbLayerRoutingPrototype.MAX_EXPANSIONS,-1);}},
            "negative explicit via cap is rejected");
        rejectIllegalArgument(new Runnable(){public void run(){PcbLayerRoutingPrototype.route(
            f.board,f.layout,policy,P06FactoryLinkFixtures.OBSERVER,
            PcbLayerRoutingPrototype.MAX_EXPANSIONS,policy.vias+1);}},
            "via cap above policy maximum is rejected");
        require(original.equals(f.layout.geometryFingerprint()) &&
            originalGraph.equals(f.layout.captureConductorGraph(f.board).toCanonical()),
            "invalid via caps leave the input placement and graph unchanged");
    }

    /** Recreates the known FULLER P06 crossing with balanced three-pad OVER and UNDER nets. */
    private static void sameSessionPriorViaTreeJoin() {
        final PriorViaJoinFixture f=sameSessionViaTreeJoinFixture();
        final String original=f.layout.geometryFingerprint();
        final String originalGraph=f.layout.captureConductorGraph(f.board).toCanonical();
        require(f.layout.getHoles().isEmpty() && f.layout.getTraces().isEmpty(),
            "same-session VIA join starts from an empty P06 placement");
        final java.util.List<PcbLayerRoutingPrototype.BranchSnapshot> events=
            new java.util.ArrayList<PcbLayerRoutingPrototype.BranchSnapshot>();
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(f.board,f.layout,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER,
            PcbLayerRoutingPrototype.MAX_EXPANSIONS,2,
            new PcbLayerRoutingPrototype.Diagnostics() {
                public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                    events.add(snapshot);
                }
            });

        PcbBoardHole creatorVia=null;
        boolean bothFaceGoal=false;
        int joinedX=Integer.MIN_VALUE,joinedY=Integer.MIN_VALUE,joinedLayer=-1,transitions=-1;
        int creatorPrefixCoordinateMatches=0;
        int laterBranchOrdinal=-1,laterNetIndex=-1,laterPass=-1;
        String laterRootId=null,laterStartId=null,laterCurrentNet=null,prefixGeometry="none";
        String observedConsumerBranch="none";
        PcbBoardHole[] creatorPrefix=null;
        boolean[] prefixTopGoals=null,prefixBottomGoals=null;
        PcbLayerRoutingPrototype.BranchSnapshot laterSnapshot=null;
        int slices=0;
        boolean complete=false;
        while(!complete) {
            complete=session.advanceSlice(1);
            if(++slices>2000000)
                throw new AssertionError("same-session VIA join exceeded its one-unit slice guard");
            Object search=privateField(session,"search");
            if(search==null) continue;

            Object branch=privateField(search,"branch");
            if(branch!=null) {
                PcbPadPlacement start=(PcbPadPlacement)privateField(branch,"start");
                if("C.1".equals(start.getPadId())) {
                    PcbPadPlacement root=(PcbPadPlacement)privateField(branch,"root");
                    int pass=privateInt(search,"pass");
                    int netIndex=privateInt(search,"netIndex");
                    int branchOrdinal=privateInt(branch,"branchOrdinal");
                    PcbBoardLayout privateLayout=(PcbBoardLayout)privateField(search,"layout");
                    PcbBoardHole[] prefix=(PcbBoardHole[])privateField(branch,"holes");
                    boolean[] goals=(boolean[])privateField(branch,"goal");
                    int cells=privateInt(search,"cells"),width=privateInt(search,"width");
                    int minX=privateInt(search,"minX"),minY=privateInt(search,"minY");
                    String currentNet=(String)privateField(search,"currentNet");
                    if("none".equals(observedConsumerBranch)) {
                        StringBuilder observed=new StringBuilder();
                        observed.append("pass=").append(pass).append(";net=").append(currentNet)
                            .append(";netIndex=").append(netIndex).append(";branch=").append(branchOrdinal)
                            .append(";root=").append(root.getPadId()).append(";start=").append(start.getPadId())
                            .append(";prefix=").append(prefix.length).append(';');
                        for(int i=0;i<prefix.length;i++) {
                            PcbBoardHole hole=prefix[i];
                            int cell=((hole.y-minY)/PcbLayerRoutingPrototype.GRID)*width+
                                (hole.x-minX)/PcbLayerRoutingPrototype.GRID;
                            if(i>0) observed.append(',');
                            observed.append(hole.id).append('@').append(hole.x).append(',').append(hole.y)
                                .append(':').append(goals[cell]?'T':'-').append(goals[cells+cell]?'B':'-');
                        }
                        observedConsumerBranch=observed.toString();
                    }
                    // Bind proof state only to the planned pass-0 UNDER/C branch.
                    if(creatorPrefix==null && pass==0 && "UNDER".equals(currentNet) &&
                            branchOrdinal==4 && netIndex==3) {
                        laterRootId=root.getPadId(); laterStartId=start.getPadId();
                        laterCurrentNet=currentNet;
                        laterBranchOrdinal=branchOrdinal; laterNetIndex=netIndex; laterPass=pass;
                        creatorPrefix=prefix;
                        prefixTopGoals=new boolean[prefix.length];
                        prefixBottomGoals=new boolean[prefix.length];
                        StringBuilder coordinates=new StringBuilder();
                        for(int i=0;i<prefix.length;i++) {
                            PcbBoardHole hole=prefix[i];
                            int cell=((hole.y-minY)/PcbLayerRoutingPrototype.GRID)*width+
                                (hole.x-minX)/PcbLayerRoutingPrototype.GRID;
                            prefixTopGoals[i]=goals[cell];
                            prefixBottomGoals[i]=goals[cells+cell];
                            if(coordinates.length()>0) coordinates.append(';');
                            coordinates.append(hole.id).append('@').append(hole.x).append(',').append(hole.y)
                                .append(':').append(goals[cell]?'T':'-').append(goals[cells+cell]?'B':'-');
                        }
                        prefixGeometry=coordinates.toString();
                    }
                }
            }

            Object publisher=privateField(search,"publisher");
            if(publisher!=null && creatorPrefix!=null && transitions<0) {
                PcbPadPlacement start=(PcbPadPlacement)privateField(publisher,"start");
                if("C.1".equals(start.getPadId())) {
                    Object end=privateField(publisher,"end");
                    int cell=privateInt(end,"cell");
                    int layer=privateInt(end,"layer");
                    int width=privateInt(search,"width");
                    int minX=privateInt(search,"minX"),minY=privateInt(search,"minY");
                    joinedX=minX+(cell%width)*PcbLayerRoutingPrototype.GRID;
                    joinedY=minY+(cell/width)*PcbLayerRoutingPrototype.GRID;
                    joinedLayer=layer;
                    transitions=privateInt(end,"transitions");
                    for(int i=0;i<creatorPrefix.length;i++)
                        if(creatorPrefix[i].x==joinedX && creatorPrefix[i].y==joinedY) {
                            creatorPrefixCoordinateMatches++;
                            creatorVia=creatorPrefix[i];
                            bothFaceGoal=prefixTopGoals[i] && prefixBottomGoals[i];
                        }
                }
            }
        }

        PcbLayerRoutingPrototype.Result result=session.result();
        for(PcbLayerRoutingPrototype.BranchSnapshot snapshot:events)
            if(snapshot.pass==0 && "UNDER".equals(snapshot.netId) &&
                    "C.1".equals(snapshot.startPadId)) laterSnapshot=snapshot;
        String[] expectedNets={"OVER","OVER","UNDER","UNDER"};
        String[] expectedStarts={"M.1","R.1","B.1","C.1"};
        String[] expectedRoots={"L.1","L.1","A.1","A.1"};
        boolean orderedBranches=events.size()==expectedNets.length;
        if(orderedBranches) for(int i=0;i<expectedNets.length;i++) {
            PcbLayerRoutingPrototype.BranchSnapshot snapshot=events.get(i);
            if(snapshot.pass!=0 || !expectedNets[i].equals(snapshot.netId) ||
                    !expectedStarts[i].equals(snapshot.startPadId) ||
                    !expectedRoots[i].equals(snapshot.rootPadId) ||
                    !"FOUND".equals(snapshot.terminalReason)) orderedBranches=false;
        }
        if(!result.accepted() || creatorVia==null || creatorPrefix.length!=2 ||
                creatorPrefixCoordinateMatches!=1 || !bothFaceGoal || transitions!=0 ||
                joinedX!=310 || joinedY<300 || joinedY>340 ||
                joinedLayer!=PcbCopperLayer.TOP.ordinal() ||
                laterBranchOrdinal!=4 || laterNetIndex!=3 || laterPass!=0 ||
                !"UNDER".equals(laterCurrentNet) || laterSnapshot==null ||
                laterSnapshot.prefixViaCount!=2 ||
                !creatorVia.id.startsWith("p07-via/UNDER/3/") || !orderedBranches ||
                events.get(0).prefixViaCount!=0 || events.get(0).reachedPadCount!=1 ||
                events.get(1).prefixViaCount!=0 || events.get(1).reachedPadCount!=2 ||
                events.get(2).prefixViaCount!=0 || events.get(2).reachedPadCount!=1 ||
                events.get(3).prefixViaCount!=2 || events.get(3).reachedPadCount!=2) {
            String joinedFace=joinedLayer==PcbCopperLayer.TOP.ordinal()?"TOP":
                joinedLayer==PcbCopperLayer.BOTTOM.ordinal()?"BOTTOM":"NONE";
            System.out.println("P07_PRIOR_VIA_JOIN_CANDIDATE {\"outcome\":\""+result.outcome+
                "\",\"root\":\""+laterRootId+"\",\"start\":\""+laterStartId+
                "\",\"creatorBranch\":\"UNDER/3\",\"laterBranch\":"+laterBranchOrdinal+
                ",\"net\":\""+laterCurrentNet+"\",\"netIndexNextFetch\":"+laterNetIndex+
                ",\"currentNetPosition\":"+(laterNetIndex<0?-1:laterNetIndex-1)+",\"pass\":"+laterPass+
                ",\"observedConsumerBranch\":\""+observedConsumerBranch+"\""+
                ",\"prefixCount\":"+(creatorPrefix==null?-1:creatorPrefix.length)+
                ",\"prefixCoordinateMatches\":"+creatorPrefixCoordinateMatches+
                ",\"prefix\":\""+prefixGeometry+"\",\"creatorViaID\":\""+
                (creatorVia==null?"NONE":creatorVia.id)+"\",\"endpoint\":["+joinedX+","+joinedY+
                "],\"joinedFace\":\""+joinedFace+"\",\"transitions\":"+transitions+
                ",\"bothGoalBits\":"+bothFaceGoal+",\"orderedBranches\":"+orderedBranches+
                ",\"eventCount\":"+events.size()+",\"accepted\":"+result.accepted()+"}");
            throw new AssertionError("CANDIDATE: same-session P06 consumer branch missed its existing creator barrel");
        }
        require(result.vias==2 && result.layout.getHoleCount()==2,
            "FULLER cap-two P06 route retains exactly its two naturally published vias");
        require(joinedX==creatorVia.x && joinedY==creatorVia.y && bothFaceGoal &&
            creatorVia.id.startsWith("p07-via/UNDER/3/"),
            "the later branch ends on the original dual-face UNDER via without a transition");
        require("A.1".equals(laterRootId) && "C.1".equals(laterStartId) &&
            laterSnapshot!=null && "FOUND".equals(laterSnapshot.terminalReason) &&
            laterSnapshot.prefixViaCount==2 && laterSnapshot.reachedPadCount==2 &&
            "A.1".equals(laterSnapshot.rootPadId) && "C.1".equals(laterSnapshot.startPadId),
            "the later diagnostic records the actual A-to-C tree branch and two-via prefix");
        require(orderedBranches && events.get(0).prefixViaCount==0 && events.get(0).reachedPadCount==1 &&
            events.get(1).prefixViaCount==0 && events.get(1).reachedPadCount==2 &&
            events.get(2).prefixViaCount==0 && events.get(2).reachedPadCount==1 &&
            events.get(3).prefixViaCount==2 && events.get(3).reachedPadCount==2,
            "actual callbacks show both OVER branches, the empty UNDER creator prefix, then its VIA consumer");

        int creatorIdentityMatches=0;
        for(PcbBoardHole hole:result.layout.getHoles()) {
            require(hole.kind==PcbBoardHole.Kind.VIA && "UNDER".equals(hole.netId),
                "accepted output retains only UNDER VIA holes from the crossing branch");
            if(hole==creatorVia) creatorIdentityMatches++;
        }
        require(creatorPrefixCoordinateMatches==1 && creatorIdentityMatches==1 &&
            containsHoleIdentity(result.layout,creatorVia),
            "Publisher preserves the actual creator hole object and ID through the later join");
        java.util.Vector<PcbTraceGeometry> laterTraces=new java.util.Vector<PcbTraceGeometry>();
        for(PcbTraceGeometry trace:result.layout.getTraces())
            if("UNDER".equals(trace.getNetId()) && "C.1".equals(trace.getStartPadId()))
                laterTraces.add(trace);
        require(laterTraces.size()==1 && laterTraces.firstElement().getLayer()==PcbCopperLayer.TOP &&
            "C.1".equals(laterTraces.firstElement().getStartPadId()) &&
            laterTraces.firstElement().getEndPadId()==null,
            "the actual later branch publishes one top-face trace to a nonterminal tree VIA");
        PcbTraceGeometry laterTrace=laterTraces.firstElement();
        int[] traceX=laterTrace.getXPoints(),traceY=laterTrace.getYPoints();
        require(traceX[0]==f.layout.getPad("C.1").getX() &&
            traceY[0]==f.layout.getPad("C.1").getY() &&
            traceX[traceX.length-1]==creatorVia.x && traceY[traceY.length-1]==creatorVia.y &&
            joinedX==traceX[traceX.length-1] && joinedY==traceY[traceY.length-1],
            "published C1 centerline terminates at the original creator VIA barrel");

        result.layout.validateRoutingGeometry(f.board);
        new PcbTwoLayerRules(f.board,result.layout).validate(result.layout);
        PcbConductorGraph.Snapshot production=result.layout.captureConductorGraph(f.board).pristine();
        production.requirePristineNetConnectivity(f.board);
        require(production.padsConnected("A.1","B.1") &&
            production.padsConnected("A.1","C.1") &&
            production.padsConnected("L.1","R.1") &&
            !production.padsConnected("A.1","L.1") &&
            !production.padsConnected("A.1","G.1"),
            "production conductor graph joins UNDER while isolating OVER and GND");
        PcbConductorGraph.Snapshot reference=
            P08ReferenceConductorBuilder.capture(f.board,result.layout).pristine();
        reference.requirePristineNetConnectivity(f.board);
        require(reference.padsConnected("A.1","C.1") &&
            !reference.padsConnected("A.1","L.1") &&
            !reference.padsConnected("A.1","G.1"),
            "independent P08 graph confirms target connectivity and foreign-net isolation");
        P07LayeredRasterOracle.Metrics raster=P07LayeredRasterOracle.verify(result.layout,f.board);
        require(raster.getViaBarrels()==2 && raster.getPlatedPadBarrels()==f.board.getPadIds().size() &&
            raster.getTraceUnitSteps()>0,
            "independent layered raster verifies both via barrels and every face-qualified pad pair");

        final PcbBoardLayout missing=copyRoutingLayout(f.layout,result.layout,creatorVia,null);
        reject(new Runnable(){public void run(){missing.validateRoutingGeometry(f.board);}},
            "removing the reused creator via breaks the intended two-face connectivity");
        final PcbBoardHole wrongVia=PcbTwoLayerRules.via(creatorVia.id,"OVER",creatorVia.x,creatorVia.y);
        final PcbBoardLayout wrong=copyRoutingLayout(f.layout,result.layout,creatorVia,wrongVia);
        reject(new Runnable(){public void run(){wrong.validateRoutingGeometry(f.board);}},
            "a wrong-net via at the reused creator coordinate breaks intended connectivity");
        reject(new Runnable(){public void run(){new PcbTwoLayerRules(f.board,wrong).validate(wrong);}},
            "two-layer rules reject the foreign owner at the same via coordinate");
        require(original.equals(f.layout.geometryFingerprint()) &&
            originalGraph.equals(f.layout.captureConductorGraph(f.board).toCanonical()) &&
            f.layout.getTraces().isEmpty() && f.layout.getHoles().isEmpty(),
            "the Session and negative controls preserve the exact empty input");
        System.out.println("P07_PRIOR_VIA_JOIN {\"case\":\"p06-fuller-crossing-with-balanced-three-pad-nets\",\"root\":\"A.1\",\"start\":\"C.1\",\"creatorBranch\":3,\"laterBranch\":4,\"netIndexNextFetch\":"+laterNetIndex+",\"currentNetPosition\":"+(laterNetIndex-1)+",\"pass\":"+laterPass+",\"creatorViaID\":\""+
            creatorVia.id+"\",\"coords\":["+creatorVia.x+","+creatorVia.y+"],\"prefixCount\":"+
            laterSnapshot.prefixViaCount+",\"joinedFace\":\"TOP\",\"endpoint\":["+joinedX+","+
            joinedY+"],\"transitions\":"+transitions+",\"finalHoleCount\":"+
            result.layout.getHoleCount()+",\"bothGoalBits\":"+bothFaceGoal+
            ",\"controls\":{\"missingVia\":\"REJECTED\",\"wrongNetVia\":\"REJECTED\",\"foreignNets\":\"ISOLATED\",\"input\":\"UNCHANGED\"}}");
    }

    private static PriorViaJoinFixture sameSessionViaTreeJoinFixture() {
        return new PriorViaJoinFixture();
    }
    private static final class PriorViaJoinFixture {
        final TroubleshootBoard board=new TroubleshootBoard("P06_RAISED_CROSSOVER");
        final PcbBoardLayout layout=new PcbBoardLayout(900,650,
            new Rectangle(100,100,420,400),new Rectangle(650,120,200,400));
        PriorViaJoinFixture() {
            board.addNet(new BoardNet("OVER"));
            board.addNet(new BoardNet("UNDER"));
            board.addNet(new BoardNet("GND"));
            addPoint("L","OVER",110,300,PcbRotation.DEG_180);
            addPoint("R","OVER",510,300,PcbRotation.DEG_0);
            addPoint("A","UNDER",310,150,PcbRotation.DEG_0);
            addPoint("B","UNDER",310,450,PcbRotation.DEG_0);
            addPoint("G","GND",180,450,PcbRotation.DEG_0);
            addPoint("M","OVER",210,300,PcbRotation.DEG_0);
            addPoint("C","UNDER",450,320,PcbRotation.DEG_0);

            java.util.Vector<PcbPlacementConstraints.Part> parts=
                new java.util.Vector<PcbPlacementConstraints.Part>();
            for(String id:board.getComponentIds()) parts.add(new PcbPlacementConstraints.Part(
                id,"prototype","Factory-link fixture","low-voltage",
                PcbPlacementConstraints.Anchor.NONE,16));
            board.setPlacementConstraints(new PcbPlacementConstraints(parts,
                new java.util.Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.TOP));
            board.addPowerInput(new ExternalBoardPowerInput("LINK_SUPPLY","L.1","G.1","OVER","GND"));
            board.addPowerInput(new ExternalBoardPowerInput("UNDER_SUPPLY","A.1","G.1","UNDER","GND"));
            board.validate();
            addLabel("L",120,235); addLabel("R",475,235); addLabel("A",333,145);
            addLabel("B",333,440); addLabel("G",200,440);
            layout.validateAgainst(board);

            require(layout.getPad("L.1").getX()==110 && layout.getPad("L.1").getY()==300 &&
                layout.getPad("M.1").getX()==210 && layout.getPad("M.1").getY()==300 &&
                layout.getPad("R.1").getX()==510 && layout.getPad("R.1").getY()==300 &&
                layout.getPad("A.1").getX()==310 && layout.getPad("A.1").getY()==150 &&
                layout.getPad("B.1").getX()==310 && layout.getPad("B.1").getY()==450 &&
                layout.getPad("C.1").getX()==450 && layout.getPad("C.1").getY()==320 &&
                layout.getPad("G.1").getX()==180 && layout.getPad("G.1").getY()==450,
                "literal P06 crossing, return, midpoint, and consumer pad coordinates");
            require(board.getNet("OVER").getPadIds().size()==3 &&
                board.getNet("UNDER").getPadIds().size()==3 && board.getNet("GND").getPadIds().size()==1,
                "the candidate balances OVER and UNDER pad counts while retaining P06 return supply");
            require(manhattan(110,300,210,300)<manhattan(110,300,510,300) &&
                manhattan(210,300,510,300)<manhattan(110,300,510,300),
                "OVER source nearest-tree order is L root, M first, then R");
            require(manhattan(310,150,310,450)<manhattan(310,150,450,320),
                "with only root A reached, B distance 300 precedes C distance 310");
        }
        private void addPoint(String id,String net,int x,int y,PcbRotation rotation) {
            PhysicalPackage physical=P06FactoryLinkFixtures.TEST_POINT;
            BoardComponent component=new BoardComponent(id,physical.getId(),physical,id);
            board.addComponent(component);
            board.addPad(new BoardPad(id+".1",id,"1",net));
            PcbPackagePose reference=new PcbPackagePose(100,100,rotation,PcbBoardSide.BOTTOM);
            Point local=physical.getGeometry().placedAt(reference).getPadPoint(0);
            PcbPackagePose pose=new PcbPackagePose(100+x-local.x,100+y-local.y,
                rotation,PcbBoardSide.BOTTOM);
            PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,pose,physical.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
        }
        private void addLabel(String id,int x,int y) {
            layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:"+id,id,
                new Rectangle(x,y,28,18),12,true,null));
        }
    }
    private static int manhattan(int x1,int y1,int x2,int y2) {
        return Math.abs(x1-x2)+Math.abs(y1-y2);
    }
    private static Object privateField(Object target,String name) {
        if(target==null) throw new AssertionError("cannot read private field from null: "+name);
        try {
            java.lang.reflect.Field field=target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch(Exception failure) {
            throw new AssertionError("cannot read P07 source field "+name,failure);
        }
    }
    private static int privateInt(Object target,String name) {
        if(target==null) throw new AssertionError("cannot read P07 integer field from null: "+name);
        try {
            java.lang.reflect.Field field=target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(target);
        } catch(Exception failure) {
            throw new AssertionError("cannot read P07 source integer field "+name,failure);
        }
    }
    private static boolean containsHoleIdentity(PcbBoardLayout layout,PcbBoardHole expected) {
        for(PcbBoardHole hole:layout.getHoles()) if(hole==expected) return true;
        return false;
    }
    private static PcbBoardLayout copyRoutingLayout(PcbBoardLayout input,PcbBoardLayout routed,
            PcbBoardHole replaced,PcbBoardHole replacement) {
        PcbBoardLayout copy=input.copyForRouting();
        for(PcbTraceGeometry trace:routed.getTraces()) copy.addTrace(trace);
        for(PcbBoardHole hole:routed.getHoles()) {
            if(hole==replaced) {
                if(replacement!=null) copy.addHole(replacement);
            } else copy.addHole(hole);
        }
        return copy;
    }
    /** Alternating endpoint pairs occupy opposite routing-grid boundaries, leaving no planar detour. */
    private static final class BoundaryCrossingFixture {
        final TroubleshootBoard board=new TroubleshootBoard("P07_VIA_CAP_BOUNDARY");
        final PcbBoardLayout layout=new PcbBoardLayout(900,650,
            new Rectangle(100,100,420,400),new Rectangle(650,120,200,400));
        BoundaryCrossingFixture() {
            board.addNet(new BoardNet("OVER")); board.addNet(new BoardNet("UNDER"));
            // The interior grid is 110..510 by 110..490: alternating pairs join
            // opposite boundaries, so one primary-face detour cannot bypass the crossing.
            addPoint("L","OVER",110,300,PcbRotation.DEG_180);
            addPoint("R","OVER",510,300,PcbRotation.DEG_0);
            addPoint("A","UNDER",310,110,PcbRotation.DEG_0);
            addPoint("B","UNDER",310,490,PcbRotation.DEG_0);
            java.util.Vector<PcbPlacementConstraints.Part> demands=
                new java.util.Vector<PcbPlacementConstraints.Part>();
            for(String id:board.getComponentIds()) demands.add(new PcbPlacementConstraints.Part(
                id,"boundary","Via-cap boundary","boundary",PcbPlacementConstraints.Anchor.NONE,16));
            board.setPlacementConstraints(new PcbPlacementConstraints(demands,
                new java.util.Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.TOP));
            board.validate(); layout.validateAgainst(board);
            String[] ids={"L.1","R.1","A.1","B.1"};
            int[][] points={{110,300},{510,300},{310,110},{310,490}};
            for(int i=0;i<ids.length;i++) require(layout.getPad(ids[i]).getX()==points[i][0] &&
                layout.getPad(ids[i]).getY()==points[i][1],"literal alternating boundary pad coordinates");
        }
        private void addPoint(String id,String net,int x,int y,PcbRotation rotation) {
            PhysicalPackage physical=P06FactoryLinkFixtures.TEST_POINT;
            BoardComponent component=new BoardComponent(id,"STRUCTURAL",physical);
            board.addComponent(component); board.addPad(new BoardPad(id+".1",id,"1",net));
            int padOffsetX=rotation==PcbRotation.DEG_180?10:30;
            PcbPackagePose pose=new PcbPackagePose(x-padOffsetX,y-10,rotation,PcbBoardSide.BOTTOM);
            PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,pose,physical.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
        }
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

/** Independent P07 branch-order literals and real ordering-stream guards. */
final class P07BranchTreeOrderContractTest {
    private static int checks;
    private static final SeededPcbLayoutGenerator.AttemptObserver NOOP =
        new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };

    private P07BranchTreeOrderContractTest() { }

    public static void main(String[] args) { run(); }

    static void run() {
        chooserUsesIndependentManhattanLiterals();
        actualRoleWidthOrderingGuards();
        actualLegacyUniformPassTwoGuard();
        invalidAndDefaultEntrypointsPreserveInputs();
        detachedCopyCancelAndErrorKeepStrategy();
        System.out.println("PASS: P07 branch-tree order contracts assertions="+checks+" layoutQualification=false");
    }

    private static void chooserUsesIndependentManhattanLiterals() {
        ChoiceFixture fixture=choiceFixture();
        String before=fixture.layout.geometryFingerprint();
        java.util.Vector<String> reached=ids("A.1","M.1");
        java.util.Vector<String> remaining=ids("X.1","Y.1","Z.1");
        String reachedBefore=reached.toString(),remainingBefore=remaining.toString();

        // Literal minima: X=min(330,10)=10, Y=min(180,160)=160,
        // Z=min(130,210)=130. The independent winners are X and Y.
        eq("X.1",PcbNetRouter.chooseNext(fixture.layout,remaining,reached),
            "nearest is computed against every reached pad");
        eq("Y.1",PcbNetRouter.chooseFarthest(fixture.layout,remaining,reached),
            "farthest is the maximum of each candidate's nearest reached distance");
        eq("X.1",PcbLayerRoutingPrototype.chooseBranchPad(fixture.layout,remaining,reached,0,true,
            PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST),
            "ROLE_WIDTHS pass zero retains nearest");
        eq("Y.1",PcbLayerRoutingPrototype.chooseBranchPad(fixture.layout,remaining,reached,1,true,
            PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST),
            "ROLE_WIDTHS pass one selects the nondegenerate farthest branch");
        eq("X.1",PcbLayerRoutingPrototype.chooseBranchPad(fixture.layout,remaining,reached,2,true,
            PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST),
            "ROLE_WIDTHS pass two retains nearest");
        eq("X.1",PcbLayerRoutingPrototype.chooseBranchPad(fixture.layout,remaining,reached,1,true,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST),
            "explicit canonical strategy retains nearest in pass one");
        for(int pass=0;pass<PcbLayerRoutingPrototype.ORDERINGS;pass++)
            eq("X.1",PcbLayerRoutingPrototype.chooseBranchPad(fixture.layout,remaining,reached,pass,false,
                PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST),
                "non-role-width chooser guard retains nearest in pass "+pass);

        // Both C and D are 80 Manhattan units from A; D is first in this
        // canonical remaining vector and must win the tie.
        eq("D.1",PcbNetRouter.chooseNext(fixture.layout,ids("D.1","C.1"),ids("A.1")),
            "nearest ties preserve canonical remaining order");
        // E=(390,440) and B=(630,200) are each 460 from A; E is first.
        eq("E.1",PcbNetRouter.chooseFarthest(fixture.layout,ids("E.1","B.1"),ids("A.1")),
            "farthest ties preserve canonical remaining order");
        eq("B.1",PcbNetRouter.chooseNext(fixture.layout,ids("B.1"),reached),
            "nearest accepts a single remaining pad");
        eq("B.1",PcbNetRouter.chooseFarthest(fixture.layout,ids("B.1"),reached),
            "farthest accepts a single remaining pad");
        require(before.equals(fixture.layout.geometryFingerprint()) &&
            reachedBefore.equals(reached.toString()) && remainingBefore.equals(remaining.toString()),
            "chooser calls preserve the layout and caller-owned vectors");
    }

    private static void actualRoleWidthOrderingGuards() {
        BranchRun defaultRun=runNoViaStream(PcbTraceWidthProfile.ROLE_WIDTHS,null);
        BranchRun explicitNearest=runNoViaStream(PcbTraceWidthProfile.ROLE_WIDTHS,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST);
        BranchRun treatment=runNoViaStream(PcbTraceWidthProfile.ROLE_WIDTHS,
            PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST);
        sameRun(defaultRun,explicitNearest,
            "default and explicit-nearest P07 owners produce identical actual branch snapshots");
        firstStart(defaultRun,0,"N","C.1");
        firstStart(defaultRun,1,"N","C.1");
        firstStart(defaultRun,2,"N","C.1");
        firstStart(treatment,0,"N","C.1");
        firstStart(treatment,1,"N","D.1");
        firstStart(treatment,2,"N","C.1");
        samePass(defaultRun,treatment,0,"treatment preserves actual pass-zero branch starts");
        samePass(defaultRun,treatment,2,"treatment preserves actual pass-two branch starts");
        require(!sameFirstStart(defaultRun,treatment,1,"N"),
            "actual ROLE_WIDTHS pass one is where the owner first diverges");
    }

    private static void actualLegacyUniformPassTwoGuard() {
        BranchRun uniform=runNoViaStream(PcbTraceWidthProfile.UNIFORM,
            PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST);
        firstStart(uniform,0,"N","C.1");
        firstStart(uniform,1,"N","C.1");
        // Remaining ids are B, C, D: legacy-uniform pass two keeps its
        // historical lexical first-element rule (B), which is neither the
        // nearest C nor the farthest D in this literal fixture.
        firstStart(uniform,2,"N","B.1");
    }

    private static void invalidAndDefaultEntrypointsPreserveInputs() {
        RoutingFixture fixture=chordFixture(PcbTraceWidthProfile.ROLE_WIDTHS);
        String input=fixture.layout.geometryFingerprint();
        String pads=fixture.board.getPadIds().toString();
        boolean rejected=false;
        try {
            PcbLayerRoutingPrototype.beginOrderingCandidates(fixture.board,fixture.layout,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,NOOP,
                PcbLayerRoutingPrototype.MAX_EXPANSIONS,0,null,null);
        } catch(IllegalArgumentException expected) { rejected=true; }
        require(rejected && input.equals(fixture.layout.geometryFingerprint()) &&
            fixture.layout.getTraces().isEmpty() && fixture.layout.getHoles().isEmpty() &&
            pads.equals(fixture.board.getPadIds().toString()),
            "null P07 strategy rejects before changing placement or board identity");

        PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=
            new PcbLayerRoutingPrototype.FixedPrefixCaptureContext(11L,17L,new int[]{0},0,0,input);
        PcbLayerRoutingPrototype.FixedPrefixGraphObserver capture=
            new PcbLayerRoutingPrototype.FixedPrefixGraphObserver() {
                public boolean select(PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage) { return false; }
                public void onGraph(PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot snapshot) { }
            };
        rejected=false;
        try {
            PcbLayerRoutingPrototype.beginOrderingCandidatesWithFixedPrefixObservation(
                fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,NOOP,
                PcbLayerRoutingPrototype.MAX_EXPANSIONS,0,null,context,capture,null);
        } catch(IllegalArgumentException expected) { rejected=true; }
        require(rejected && input.equals(fixture.layout.geometryFingerprint()) &&
            fixture.layout.getTraces().isEmpty() && fixture.layout.getHoles().isEmpty(),
            "null fixed-prefix strategy rejects before mutating the supplied layout");

        SeededPcbLayoutGenerator generator=new SeededPcbLayoutGenerator();
        SeededPcbLayoutGenerator.Session ordinary=generator.begin(fixture.board,11L,17L);
        SeededPcbLayoutGenerator.Session diagnostic=generator.beginWithBranchOrderStrategy(
            fixture.board,11L,17L,PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST);
        eq(PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,
            strategyOf(ordinary),"normal generator entrypoint stores canonical nearest");
        eq(PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST,
            strategyOf(diagnostic),"explicit generator entrypoint stores the requested immutable strategy");
        SeededPcbLayoutGenerator.Session ordinaryCapture=generator.beginWithFixedPrefixObservation(
            fixture.board,11L,17L,capture);
        SeededPcbLayoutGenerator.Session diagnosticCapture=generator.beginWithFixedPrefixObservation(
            fixture.board,11L,17L,capture,
            PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST);
        eq(PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,
            strategyOf(ordinaryCapture),"normal fixed-prefix generator entrypoint remains nearest");
        eq(PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST,
            strategyOf(diagnosticCapture),"strategy-aware fixed-prefix generator entrypoint retains strategy");
        rejected=false;
        try { generator.beginWithBranchOrderStrategy(fixture.board,11L,17L,null); }
        catch(IllegalArgumentException expected) { rejected=true; }
        require(rejected && input.equals(fixture.layout.geometryFingerprint()) &&
            fixture.layout.getTraces().isEmpty() && fixture.layout.getHoles().isEmpty(),
            "null generator strategy rejects without touching the existing candidate");
    }

    private static void detachedCopyCancelAndErrorKeepStrategy() {
        RoutingFixture sourceFixture=chordFixture(PcbTraceWidthProfile.ROLE_WIDTHS);
        String original=sourceFixture.layout.geometryFingerprint();
        final ArmedObserver sourceObserver=new ArmedObserver();
        PcbLayerRoutingPrototype.Session source=PcbLayerRoutingPrototype.beginOrderingCandidates(
            sourceFixture.board,sourceFixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            sourceObserver,PcbLayerRoutingPrototype.MAX_EXPANSIONS,0,null,
            PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST);
        stopAtInitialBranch(source);
        PcbLayerRoutingPrototype.DetachedBranchCopyTask copy=
            PcbLayerRoutingPrototype.beginDetachedBranchCopy(source,NOOP,0,60);
        int copyCalls=0;
        while(!copy.isComplete()) {
            copy.advance(32768);
            if(++copyCalls>10000) throw new AssertionError("small detached strategy copy exceeded its local guard");
        }
        PcbLayerRoutingPrototype.DetachedBranchReplay replay=copy.result();
        Object copiedOwner=fieldValue(replay,"owner");
        eq(PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST,
            strategyOf(copiedOwner),"detached copied Session keeps the source's immutable strategy");
        replay.cancel();
        copy.cancel();
        require(fieldValue(replay,"owner")==null &&
            PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST.equals(strategyOf(source)) &&
            original.equals(sourceFixture.layout.geometryFingerprint()) &&
            sourceFixture.layout.getTraces().isEmpty() && sourceFixture.layout.getHoles().isEmpty(),
            "detached cancellation releases only its copy and leaves the source strategy/input intact");

        RoutingFixture cancelledFixture=chordFixture(PcbTraceWidthProfile.ROLE_WIDTHS);
        String cancelledInput=cancelledFixture.layout.geometryFingerprint();
        PcbLayerRoutingPrototype.Session cancelledSource=PcbLayerRoutingPrototype.beginOrderingCandidates(
            cancelledFixture.board,cancelledFixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            new ArmedObserver(),PcbLayerRoutingPrototype.MAX_EXPANSIONS,0,null,
            PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST);
        stopAtInitialBranch(cancelledSource);
        PcbLayerRoutingPrototype.DetachedBranchCopyTask cancelledCopy=
            PcbLayerRoutingPrototype.beginDetachedBranchCopy(cancelledSource,NOOP,0,60);
        cancelledCopy.advance(1);
        cancelledCopy.cancel();
        require("CANCELLED".equals(cancelledCopy.phase()) &&
            PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST.equals(
                strategyOf(cancelledSource)) &&
            cancelledInput.equals(cancelledFixture.layout.geometryFingerprint()) &&
            cancelledFixture.layout.getTraces().isEmpty() && cancelledFixture.layout.getHoles().isEmpty(),
            "cancelled detached copy preserves source strategy and exact input placement");

        RoutingFixture failedFixture=chordFixture(PcbTraceWidthProfile.ROLE_WIDTHS);
        final RuntimeException sentinel=new IllegalStateException("branch-order observer error sentinel");
        final ArmedObserver errorObserver=new ArmedObserver();
        errorObserver.failure=sentinel;
        PcbLayerRoutingPrototype.Session failed=PcbLayerRoutingPrototype.beginOrderingCandidates(
            failedFixture.board,failedFixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            errorObserver,PcbLayerRoutingPrototype.MAX_EXPANSIONS,0,null,
            PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST);
        String failedInput=failedFixture.layout.geometryFingerprint();
        errorObserver.armed=true;
        boolean propagated=false;
        try { failed.advanceOrderingSlice(1); }
        catch(RuntimeException expected) { propagated=expected==sentinel; }
        require(propagated &&
            PcbLayerRoutingPrototype.BranchOrderStrategy.SECOND_ORDER_FARTHEST.equals(strategyOf(failed)) &&
            failedInput.equals(failedFixture.layout.geometryFingerprint()) &&
            failedFixture.layout.getTraces().isEmpty() && failedFixture.layout.getHoles().isEmpty(),
            "observer error remains exact, latches cleanup, and cannot change the immutable strategy/input");
    }

    private static BranchRun runNoViaStream(PcbTraceWidthProfile profile,
            PcbLayerRoutingPrototype.BranchOrderStrategy strategy) {
        final RoutingFixture fixture=chordFixture(profile);
        final String input=fixture.layout.geometryFingerprint();
        final String boardPads=fixture.board.getPadIds().toString();
        final BranchCollector collector=new BranchCollector();
        PcbLayerRoutingPrototype.Session session;
        if(strategy==null)
            session=PcbLayerRoutingPrototype.beginOrderingCandidates(fixture.board,fixture.layout,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,NOOP,
                PcbLayerRoutingPrototype.MAX_EXPANSIONS,0,collector);
        else
            session=PcbLayerRoutingPrototype.beginOrderingCandidates(fixture.board,fixture.layout,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,NOOP,
                PcbLayerRoutingPrototype.MAX_EXPANSIONS,0,collector,strategy);
        int advances=0;
        while(true) {
            if(++advances>10000) throw new AssertionError("literal three-chord stream exceeded bounded advance guard");
            PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(32768);
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE) break;
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY)
                throw new AssertionError("three pairwise-crossing nets unexpectedly yielded a no-via layout");
        }
        PcbLayerRoutingPrototype.OrderingSummary summary=session.orderingSummary();
        boolean allOrderingsCompleted=summary.orderings==PcbLayerRoutingPrototype.ORDERINGS &&
            summary.completedOrderings==PcbLayerRoutingPrototype.ORDERINGS &&
            summary.yieldedOrderings==0 && summary.expansions<=PcbLayerRoutingPrototype.MAX_EXPANSIONS;
        require(allOrderingsCompleted,allOrderingsCompleted?
            "literal no-via stream actually completes all three P07 orderings":
            "literal no-via stream actually completes all three P07 orderings"+
                runDetails(summary,collector.snapshots));
        require(collector.snapshots.size()>0 && collector.snapshots.size()<=15,
            "actual terminal branch snapshots remain within the three-order literal pad bound");
        require(input.equals(fixture.layout.geometryFingerprint()) &&
            boardPads.equals(fixture.board.getPadIds().toString()) &&
            fixture.layout.getTraces().isEmpty() && fixture.layout.getHoles().isEmpty(),
            "failed actual orderings preserve source board identity, geometry, and copper");
        return new BranchRun(collector.snapshots,summary);
    }

    private static void stopAtInitialBranch(PcbLayerRoutingPrototype.Session session) {
        for(int index=0;index<64;index++) {
            Object search=fieldValue(session,"search");
            if(search!=null && fieldValue(search,"branch")!=null) return;
            PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(1);
            if(progress!=PcbLayerRoutingPrototype.OrderingProgress.WORKING)
                throw new AssertionError("fresh literal stream ended before its first branch-copy boundary");
        }
        throw new AssertionError("fresh literal stream did not reach an initial live P07 branch");
    }

    private static void firstStart(BranchRun run,int pass,String net,String expected) {
        String observed=null;
        for(PcbLayerRoutingPrototype.BranchSnapshot snapshot:run.snapshots)
            if(snapshot.pass==pass && net.equals(snapshot.netId)) { observed=snapshot.startPadId; break; }
        String message="actual Search selected the expected "+net+" branch in pass "+pass+
            " expected="+expected+" actual="+observed;
        require(equal(expected,observed),equal(expected,observed)?message:message+runDetails(run));
    }

    private static boolean sameFirstStart(BranchRun first,BranchRun second,int pass,String net) {
        return equal(firstStartValue(first,pass,net),firstStartValue(second,pass,net));
    }

    private static String firstStartValue(BranchRun run,int pass,String net) {
        for(PcbLayerRoutingPrototype.BranchSnapshot snapshot:run.snapshots)
            if(snapshot.pass==pass && net.equals(snapshot.netId)) return snapshot.startPadId;
        return null;
    }

    private static void samePass(BranchRun first,BranchRun second,int pass,String message) {
        java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot> a=passSnapshots(first,pass);
        java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot> b=passSnapshots(second,pass);
        int firstPrior=expansionsBeforePass(first,pass);
        int secondPrior=expansionsBeforePass(second,pass);
        boolean same=sameSnapshots(a,b,firstPrior,secondPrior);
        require(same,same?message:message+" first"+runDetails(first)+" second"+runDetails(second));
    }

    private static int expansionsBeforePass(BranchRun run,int pass) {
        int cumulative=0,prior=0;
        for(PcbLayerRoutingPrototype.BranchSnapshot snapshot:run.snapshots) {
            cumulative+=snapshot.branchExpanded;
            require(snapshot.totalExpanded==cumulative,
                "literal stream cumulative request budget equals every completed branch expansion sum");
            if(snapshot.pass<pass) prior=cumulative;
        }
        return prior;
    }

    private static void sameRun(BranchRun first,BranchRun second,String message) {
        boolean same=first.summary.expansions==second.summary.expansions &&
            first.summary.orderings==second.summary.orderings &&
            first.summary.completedOrderings==second.summary.completedOrderings &&
            first.summary.yieldedOrderings==second.summary.yieldedOrderings &&
            equal(first.summary.termination,second.summary.termination) &&
            sameSnapshots(first.snapshots,second.snapshots);
        require(same,same?message:message+" first"+runDetails(first)+" second"+runDetails(second));
    }

    private static String runDetails(BranchRun run) {
        return runDetails(run.summary,run.snapshots);
    }

    private static String runDetails(PcbLayerRoutingPrototype.OrderingSummary summary,
            java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot> snapshots) {
        StringBuilder details=new StringBuilder("; summary{expansions=");
        details.append(summary.expansions).append(",orderings=").append(summary.orderings)
            .append(",completed=").append(summary.completedOrderings)
            .append(",yielded=").append(summary.yieldedOrderings)
            .append(",guided=").append(summary.guidanceStarted)
            .append(",comparisonComplete=").append(summary.comparisonComplete)
            .append(",termination=").append(summary.termination).append("}; terminalSnapshots[")
            .append(snapshots.size()).append("]=");
        int limit=Math.min(15,snapshots.size());
        for(int index=0;index<limit;index++) {
            if(index>0) details.append('|');
            PcbLayerRoutingPrototype.BranchSnapshot snapshot=snapshots.get(index);
            details.append(snapshot.pass).append('/').append(snapshot.netId).append('/')
                .append(snapshot.startPadId).append("->").append(snapshot.rootPadId)
                .append('@').append(snapshot.startX).append(',').append(snapshot.startY)
                .append(':').append(snapshot.terminalReason).append("{branch=")
                .append(snapshot.branchExpanded).append(",total=").append(snapshot.totalExpanded)
                .append(",vias=").append(snapshot.prefixViaCount)
                .append(",reached=").append(snapshot.reachedPadCount).append('}');
        }
        if(snapshots.size()>limit) details.append("|...");
        return details.toString();
    }

    private static java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot> passSnapshots(
            BranchRun run,int pass) {
        java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot> result=
            new java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot>();
        for(PcbLayerRoutingPrototype.BranchSnapshot snapshot:run.snapshots)
            if(snapshot.pass==pass) result.add(snapshot);
        return result;
    }

    private static boolean sameSnapshots(
            java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot> first,
            java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot> second) {
        return sameSnapshots(first,second,0,0);
    }

    private static boolean sameSnapshots(
            java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot> first,
            java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot> second,
            int firstPrior,int secondPrior) {
        if(first.size()!=second.size()) return false;
        for(int index=0;index<first.size();index++) {
            PcbLayerRoutingPrototype.BranchSnapshot a=first.get(index),b=second.get(index);
            if(a.pass!=b.pass || !equal(a.netId,b.netId) || !equal(a.startPadId,b.startPadId) ||
                    !equal(a.rootPadId,b.rootPadId) || !equal(a.terminalReason,b.terminalReason) ||
                    a.branchExpanded!=b.branchExpanded || a.totalExpanded-firstPrior!=b.totalExpanded-secondPrior ||
                    a.prefixViaCount!=b.prefixViaCount || a.reachedPadCount!=b.reachedPadCount)
                return false;
        }
        return true;
    }

    private static ChoiceFixture choiceFixture() {
        PcbBoardLayout layout=new PcbBoardLayout(1100,650,new Rectangle(100,100,600,400),
            new Rectangle(820,120,200,400));
        addChoicePad(layout,"A.1",170,200);
        addChoicePad(layout,"M.1",510,200);
        addChoicePad(layout,"X.1",500,200);
        addChoicePad(layout,"Y.1",350,200);
        addChoicePad(layout,"Z.1",300,200);
        addChoicePad(layout,"D.1",170,280);
        addChoicePad(layout,"C.1",250,200);
        addChoicePad(layout,"B.1",630,200);
        addChoicePad(layout,"E.1",390,440);
        return new ChoiceFixture(layout);
    }

    private static void addChoicePad(PcbBoardLayout layout,String id,int x,int y) {
        layout.addPad(new PcbPadPlacement(id,x,y,0,0,0,
            new Rectangle(x-4,y-4,8,8),new Rectangle(x-6,y-6,12,12)));
    }

    private static RoutingFixture chordFixture(PcbTraceWidthProfile profile) {
        TroubleshootBoard board=new TroubleshootBoard("P07_BRANCH_TREE_THREE_CHORDS");
        board.addNet(new BoardNet("M",BoardNet.RoutingRole.HIGH_CURRENT));
        board.addNet(new BoardNet("P",BoardNet.RoutingRole.SUPPLY));
        board.addNet(new BoardNet("N",BoardNet.RoutingRole.SIGNAL));
        PcbBoardLayout layout=new PcbBoardLayout(1100,650,new Rectangle(100,100,600,400),
            new Rectangle(820,120,200,400));
        addPhysicalPoint(board,layout,"M1","M",110,110,PcbRotation.DEG_0,PcbBoardSide.TOP);
        addPhysicalPoint(board,layout,"M2","M",690,490,PcbRotation.DEG_180,PcbBoardSide.TOP);
        addPhysicalPoint(board,layout,"P1","P",510,110,PcbRotation.DEG_0);
        addPhysicalPoint(board,layout,"P2","P",110,490,PcbRotation.DEG_0);
        addPhysicalPoint(board,layout,"A","N",110,300,PcbRotation.DEG_0);
        addPhysicalPoint(board,layout,"B","N",690,240,PcbRotation.DEG_180);
        addPhysicalPoint(board,layout,"C","N",690,300,PcbRotation.DEG_180);
        addPhysicalPoint(board,layout,"D","N",690,110,PcbRotation.DEG_180);
        java.util.Vector<PcbPlacementConstraints.Part> parts=
            new java.util.Vector<PcbPlacementConstraints.Part>();
        java.util.Vector<String> componentIds=board.getComponentIds();
        java.util.Collections.sort(componentIds);
        for(String componentId:componentIds)
            parts.add(new PcbPlacementConstraints.Part(componentId,"branch-order","Branch order",
                "board",PcbPlacementConstraints.Anchor.NONE,20));
        board.setPlacementConstraints(new PcbPlacementConstraints(parts,
            new java.util.Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.TOP,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION,1,false,profile));
        board.validate();
        layout.validateAgainst(board);
        assertPhysicalPad(layout,"M1.1",110,110,PcbBoardSide.TOP);
        assertPhysicalPad(layout,"M2.1",690,490,PcbBoardSide.TOP);
        assertPhysicalPad(layout,"P1.1",510,110,PcbBoardSide.BOTTOM);
        assertPhysicalPad(layout,"P2.1",110,490,PcbBoardSide.BOTTOM);
        assertPhysicalPad(layout,"A.1",110,300,PcbBoardSide.BOTTOM);
        assertPhysicalPad(layout,"B.1",690,240,PcbBoardSide.BOTTOM);
        assertPhysicalPad(layout,"C.1",690,300,PcbBoardSide.BOTTOM);
        assertPhysicalPad(layout,"D.1",690,110,PcbBoardSide.BOTTOM);
        return new RoutingFixture(board,layout);
    }

    private static void addPhysicalPoint(TroubleshootBoard board,PcbBoardLayout layout,
            String id,String net,int x,int y,PcbRotation rotation) {
        addPhysicalPoint(board,layout,id,net,x,y,rotation,PcbBoardSide.BOTTOM);
    }

    private static void addPhysicalPoint(TroubleshootBoard board,PcbBoardLayout layout,
            String id,String net,int x,int y,PcbRotation rotation,PcbBoardSide mountingSide) {
        PhysicalPackage physical=P06FactoryLinkFixtures.TEST_POINT;
        BoardComponent component=new BoardComponent(id,physical.getId(),physical,id);
        board.addComponent(component);
        board.addPad(new BoardPad(id+".1",id,"1",net));
        PcbPackagePose reference=new PcbPackagePose(100,100,rotation,mountingSide);
        Point local=physical.getGeometry().placedAt(reference).getPadPoint(0);
        PcbPackagePose pose=new PcbPackagePose(100+x-local.x,100+y-local.y,rotation,mountingSide);
        PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,pose,physical.getGeometry());
        layout.addComponent(footprint.getPlacement());
        for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
    }

    private static void assertPhysicalPad(PcbBoardLayout layout,String padId,int x,int y,
            PcbBoardSide mountingSide) {
        PcbPadPlacement pad=layout.getPad(padId);
        require(pad!=null && pad.getX()==x && pad.getY()==y &&
            pad.getAttachment()==PcbTerminalAttachment.PLATED_THROUGH_HOLE &&
            pad.getMountingSide()==mountingSide &&
            PcbCopperAccess.hasCopper(pad,PcbCopperLayer.TOP) &&
            PcbCopperAccess.hasCopper(pad,PcbCopperLayer.BOTTOM),
            "literal PTH point "+padId+" keeps its board XY and declared "+mountingSide+
                " mount with copper on both faces");
    }

    private static PcbLayerRoutingPrototype.BranchOrderStrategy strategyOf(Object session) {
        Object value=fieldValue(session,"branchOrderStrategy");
        if(!(value instanceof PcbLayerRoutingPrototype.BranchOrderStrategy))
            throw new AssertionError("strategy field is absent or has an unexpected value owner");
        try {
            java.lang.reflect.Field field=session.getClass().getDeclaredField("branchOrderStrategy");
            require(java.lang.reflect.Modifier.isFinal(field.getModifiers()),
                "stored branch-order strategy is immutable");
        } catch(ReflectiveOperationException failure) {
            throw new AssertionError("branch-order strategy field must be inspectable",failure);
        }
        return (PcbLayerRoutingPrototype.BranchOrderStrategy)value;
    }

    private static Object fieldValue(Object target,String name) {
        if(target==null) throw new AssertionError("cannot inspect null owner field "+name);
        Class<?> type=target.getClass();
        while(type!=null) {
            try {
                java.lang.reflect.Field field=type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch(NoSuchFieldException missing) { type=type.getSuperclass(); }
            catch(ReflectiveOperationException failure) {
                throw new AssertionError("cannot read owner field "+name,failure);
            }
        }
        throw new AssertionError("owner field is absent: "+name);
    }

    private static java.util.Vector<String> ids(String... values) {
        java.util.Vector<String> result=new java.util.Vector<String>();
        for(String value:values) result.add(value);
        return result;
    }

    private static void eq(Object expected,Object actual,String message) {
        require(equal(expected,actual),message+" expected="+expected+" actual="+actual);
    }

    private static boolean equal(Object first,Object second) {
        return first==null?second==null:first.equals(second);
    }

    private static void require(boolean condition,String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }

    private static final class ArmedObserver implements SeededPcbLayoutGenerator.AttemptObserver {
        boolean armed;
        RuntimeException failure;
        public void check(int attempt) { if(armed && failure!=null) throw failure; }
    }

    private static final class BranchCollector implements PcbLayerRoutingPrototype.Diagnostics {
        final java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot> snapshots=
            new java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot>();
        public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
            require(snapshot!=null && snapshot.pass>=0 &&
                snapshot.pass<PcbLayerRoutingPrototype.ORDERINGS && snapshot.startPadId!=null &&
                snapshot.rootPadId!=null && snapshot.terminalReason!=null,
                "actual P07 branch callback carries bounded terminal identity");
            if(snapshots.size()>=15) throw new AssertionError("literal branch snapshot cap exceeded");
            snapshots.add(snapshot);
        }
    }

    private static final class ChoiceFixture {
        final PcbBoardLayout layout;
        ChoiceFixture(PcbBoardLayout layout) { this.layout=layout; }
    }

    private static final class RoutingFixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        RoutingFixture(TroubleshootBoard board,PcbBoardLayout layout) {
            this.board=board; this.layout=layout;
        }
    }

    private static final class BranchRun {
        final java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot> snapshots;
        final PcbLayerRoutingPrototype.OrderingSummary summary;
        BranchRun(java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot> snapshots,
                PcbLayerRoutingPrototype.OrderingSummary summary) {
            this.snapshots=new java.util.Vector<PcbLayerRoutingPrototype.BranchSnapshot>(snapshots);
            this.summary=summary;
        }
    }
}
