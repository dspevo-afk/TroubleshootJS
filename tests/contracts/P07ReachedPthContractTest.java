package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/** Real P07 contracts for joining a previously reached plated terminal. */
public final class P07ReachedPthContractTest {
    private static final String BLOCK_NET="00_BLOCK";
    private static final String TREE_NET="10_TREE";
    private static final Rectangle OUTLINE=new Rectangle(100,100,800,700);
    private static int checks;

    private P07ReachedPthContractTest() { }

    public static void main(String[] args) {
        for(PcbTraceWidthProfile profile:new PcbTraceWidthProfile[]{
                PcbTraceWidthProfile.UNIFORM,PcbTraceWidthProfile.ROLE_WIDTHS})
            oppositeFaceTreePadIsARealBranchEndpoint(profile);
        reachedPadEndpointKeepsEscapeAndForeignCopperChecks();
        failedOwnedRequestDoesNotPublishItsPrivatePrefix();
        System.out.println("PASS: P07 reached PTH contracts assertions="+checks);
    }

    private static void oppositeFaceTreePadIsARealBranchEndpoint(PcbTraceWidthProfile profile) {
        Fixture blocking=new Fixture(profile);
        Fixture sliced=new Fixture(profile);
        String blockingInput=blocking.layout.geometryFingerprint();
        String slicedInput=sliced.layout.geometryFingerprint();
        final List<PcbLayerRoutingPrototype.BranchSnapshot> snapshots=
            new ArrayList<PcbLayerRoutingPrototype.BranchSnapshot>();
        PcbLayerRoutingPrototype.Result expected=route(blocking,
            new PcbLayerRoutingPrototype.Diagnostics() {
                public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                    snapshots.add(snapshot);
                }
            },PcbLayerRoutingPrototype.WORK_QUANTUM);
        PcbLayerRoutingPrototype.Result actual=route(sliced,null,7);

        require(expected.accepted() && actual.accepted(),
            "actual zero-via P07 fixture routes both trace profiles: " +
                expected.outcome + "/" + actual.outcome);
        sameResult(expected,actual,"blocking and sliced requests preserve exact P07 output");
        require(blocking.layout.geometryFingerprint().equals(blockingInput) &&
            sliced.layout.geometryFingerprint().equals(slicedInput),
            "both routing modes leave their caller-owned layouts unchanged");
        require(expected.vias==0 && expected.layout.getHoleCount()==0,
            "the tree connects through existing plated barrels without adding a via");

        PcbTraceGeometry wall=null,joined=null;
        for(PcbTraceGeometry trace:expected.layout.getTraces()) {
            if(BLOCK_NET.equals(trace.getNetId()) &&
                    "BLOCK_B.1".equals(trace.getStartPadId()) &&
                    "BLOCK_A.1".equals(trace.getEndPadId())) wall=trace;
            if(TREE_NET.equals(trace.getNetId()) &&
                    "TREE_C.1".equals(trace.getStartPadId()) &&
                    "TREE_B.1".equals(trace.getEndPadId())) joined=trace;
        }
        require(wall!=null && wall.getLayer()==PcbCopperLayer.BOTTOM &&
            wall.getXPoints()[0]==500 && wall.getXPoints()[wall.getXPoints().length-1]==500 &&
            Math.min(wall.getYPoints()[0],wall.getYPoints()[wall.getYPoints().length-1])==110 &&
            Math.max(wall.getYPoints()[0],wall.getYPoints()[wall.getYPoints().length-1])==790,
            "an actual foreign bottom-face trace spans the full routing outline");
        require(joined!=null && joined.getLayer()==PcbCopperLayer.TOP &&
            joined.getStartPadId().equals("TREE_C.1") && joined.getEndPadId().equals("TREE_B.1"),
            "the later tree branch ends at reached B on its opposite copper face");
        PcbPadPlacement start=expected.layout.getPad("TREE_C.1");
        PcbPadPlacement endpoint=expected.layout.getPad("TREE_B.1");
        int[] xs=joined.getXPoints(),ys=joined.getYPoints();
        require(endpoint.getAttachment()==PcbTerminalAttachment.PLATED_THROUGH_HOLE &&
            PcbCopperAccess.hasCopper(endpoint,PcbCopperLayer.TOP) &&
            PcbCopperAccess.hasCopper(endpoint,PcbCopperLayer.BOTTOM) &&
            xs[0]==start.getX() && ys[0]==start.getY() &&
            xs[xs.length-1]==endpoint.getX() && ys[ys.length-1]==endpoint.getY(),
            "published trace endpoint identity matches the actual two-face PTH land");
        require(!crossesVerticalWallOnFace(expected.layout,TREE_NET,500,PcbCopperLayer.BOTTOM),
            "foreign bottom copper remains an impassable route boundary");

        PcbLayerRoutingPrototype.BranchSnapshot treeBranch=null;
        for(PcbLayerRoutingPrototype.BranchSnapshot snapshot:snapshots)
            if(snapshot.pass==0 && TREE_NET.equals(snapshot.netId) &&
                    "TREE_C.1".equals(snapshot.startPadId)) treeBranch=snapshot;
        require(treeBranch!=null && "FOUND".equals(treeBranch.terminalReason) &&
            treeBranch.reachedPadCount==2 && treeBranch.prefixViaCount==0 &&
            treeBranch.goalsTop>=2,
            "C's observed search begins with only reached A/B and both FULLER PTH faces");
        require(Math.abs(760-760)+Math.abs(600-400)<
            Math.abs(760-420)+Math.abs(400-400),
            "unconnected D is geometrically closer to C, but outside its reached-target set");

        expected.layout.validateRoutingGeometry(blocking.board);
        new PcbTwoLayerRules(blocking.board,expected.layout).validate(expected.layout);
        PcbConductorGraph.Snapshot production=expected.layout.captureConductorGraph(blocking.board).pristine();
        production.requirePristineNetConnectivity(blocking.board);
        require(production.padsConnected("TREE_A.1","TREE_C.1") &&
            production.padsConnected("TREE_B.1","TREE_C.1"),
            "production finite-conductor graph joins the reached PTH endpoint");
        PcbConductorGraph.Snapshot reference=
            P08ReferenceConductorBuilder.capture(blocking.board,expected.layout).pristine();
        reference.requirePristineNetConnectivity(blocking.board);
        require(reference.padsConnected("TREE_A.1","TREE_D.1") &&
            !reference.padsConnected("TREE_A.1","BLOCK_A.1"),
            "independent P08 all-pairs conductor reference preserves nets");
        P07LayeredRasterOracle.Metrics raster=
            P07LayeredRasterOracle.verify(expected.layout,blocking.board);
        require(raster.getViaBarrels()==0 && raster.getPlatedPadBarrels()==
            blocking.board.getPadIds().size() && raster.getTraceUnitSteps()>0,
            "independent face raster proves connectivity through pads and no added via");
    }

    private static void reachedPadEndpointKeepsEscapeAndForeignCopperChecks() {
        for(PcbTraceWidthProfile profile:new PcbTraceWidthProfile[]{
                PcbTraceWidthProfile.UNIFORM,PcbTraceWidthProfile.ROLE_WIDTHS}) {
            Fixture fixture=new Fixture(profile);
            PcbPadPlacement start=fixture.layout.getPad("TREE_C.1");
            PcbPadPlacement root=fixture.layout.getPad("TREE_A.1");
            PcbPadPlacement target=fixture.layout.getPad("TREE_B.1");
            require(target.getMountingSide()==PcbBoardSide.TOP &&
                target.getEscapeLength()>=20,
                "negative access canary uses an actual escaped PTH placement");
            PcbPadPlacement[] reached={target};
            int dx=target.getEscapeDx(),dy=target.getEscapeDy();
            int towardX=target.getX()+dx*20,towardY=target.getY()+dy*20;
            int nearX=target.getX()+dx*10,nearY=target.getY()+dy*10;
            PcbNetRouter.Router escapeRouter=router(fixture,PcbCopperLayer.TOP);
            require(escapeRouter.permitsLayerStep(towardX,towardY,nearX,nearY,TREE_NET,
                start,root,reached),
                "a physically legal approach along the reached pad's escape remains open");
            require(!escapeRouter.permitsLayerStep(nearX,nearY,towardX,towardY,TREE_NET,
                start,root,reached),
                "the reached pad's reserved escape cannot be traversed away from its land");

            PcbNetRouter.Router foreignRouter=router(fixture,PcbCopperLayer.TOP);
            Vector<Point> foreignCopper=new Vector<Point>();
            foreignCopper.add(new Point(nearX,nearY));
            foreignRouter.occupyLayerPath(foreignCopper,BLOCK_NET);
            require(!foreignRouter.permitsLayerStep(towardX,towardY,nearX,nearY,TREE_NET,
                start,root,reached),
                "reached-terminal access does not waive occupied foreign copper");
        }
    }

    private static void failedOwnedRequestDoesNotPublishItsPrivatePrefix() {
        final Fixture fixture=new Fixture(PcbTraceWidthProfile.ROLE_WIDTHS);
        final String before=fixture.layout.geometryFingerprint();
        final List<PcbLayerRoutingPrototype.BranchSnapshot> events=
            new ArrayList<PcbLayerRoutingPrototype.BranchSnapshot>();
        final IllegalStateException abort=new IllegalStateException("abort after private prefix");
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(fixture.board,
            fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias,
            new PcbLayerRoutingPrototype.Diagnostics() {
                public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                    events.add(snapshot);
                    if(events.size()==2) throw abort;
                }
            });
        RuntimeException failure=null;
        try {
            int slices=0;
            while(!session.advanceSlice(11))
                if(++slices>1000000) throw new AssertionError("aborting P07 request exceeded its guard");
        } catch(RuntimeException caught) { failure=caught; }
        require(failure==abort && events.size()==2 && BLOCK_NET.equals(events.get(0).netId) &&
            TREE_NET.equals(events.get(1).netId),
            "failure arrives after the first foreign-net route was privately published");
        require(fixture.layout.getTraces().isEmpty() && fixture.layout.getHoles().isEmpty() &&
            fixture.layout.geometryFingerprint().equals(before),
            "a failed request discards its private copper prefix without caller mutation");
        RuntimeException resultFailure=null;
        try { session.result(); } catch(RuntimeException caught) { resultFailure=caught; }
        require(resultFailure==abort,
            "a failed owned request cannot expose a partial route result");
    }

    private static PcbNetRouter.Router router(Fixture fixture,PcbCopperLayer layer) {
        return new PcbNetRouter.Router(fixture.layout,fixture.board,OUTLINE,0,
            P06FactoryLinkFixtures.OBSERVER,layer);
    }

    private static PcbLayerRoutingPrototype.Result route(Fixture fixture,
            PcbLayerRoutingPrototype.Diagnostics diagnostics,int slice) {
        PcbLayerRoutingPrototype.Policy policy=PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER;
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(fixture.board,
            fixture.layout,policy,P06FactoryLinkFixtures.OBSERVER,
            PcbLayerRoutingPrototype.MAX_EXPANSIONS,0,diagnostics);
        int slices=0;
        while(!session.advanceSlice(slice))
            if(++slices>1000000) throw new AssertionError("P07 reached-PTH fixture exceeded its slice guard");
        return session.result();
    }

    private static boolean crossesVerticalWallOnFace(PcbBoardLayout layout,String net,int x,
            PcbCopperLayer layer) {
        for(PcbTraceGeometry trace:layout.getTraces()) if(net.equals(trace.getNetId()) &&
                trace.getLayer()==layer) {
            int[] xs=trace.getXPoints(),ys=trace.getYPoints();
            for(int i=1;i<xs.length;i++) if(xs[i-1]==x && xs[i]==x &&
                    Math.min(ys[i-1],ys[i])<790 && Math.max(ys[i-1],ys[i])>110) return true;
            for(int i=1;i<xs.length;i++) if((xs[i-1]<x && xs[i]>x) || (xs[i]>x && xs[i-1]<x))
                return true;
        }
        return false;
    }

    private static void sameResult(PcbLayerRoutingPrototype.Result expected,
            PcbLayerRoutingPrototype.Result actual,String message) {
        require(expected.accepted()==actual.accepted() &&
            expected.outcome.equals(actual.outcome) && expected.expansions==actual.expansions &&
            expected.orderings==actual.orderings && expected.vias==actual.vias &&
            expected.links==actual.links,message+" result accounting");
        if(expected.accepted()) require(expected.layout.geometryFingerprint().equals(
            actual.layout.geometryFingerprint()),message+" exact physical output");
    }

    private static void require(boolean condition,String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        final TroubleshootBoard board=new TroubleshootBoard("P07_REACHED_PTH_TREE");
        final PcbBoardLayout layout=new PcbBoardLayout(1200,900,OUTLINE,
            new Rectangle(950,120,200,700));

        Fixture(PcbTraceWidthProfile profile) {
            board.addNet(new BoardNet(BLOCK_NET,BoardNet.RoutingRole.RETURN));
            board.addNet(new BoardNet(TREE_NET,BoardNet.RoutingRole.HIGH_CURRENT));
            for(String id:new String[]{"A","B","C","D"})
                board.addNet(new BoardNet("90_AUX_"+id));
            addPoint("BLOCK_A",BLOCK_NET,500,110);
            addPoint("BLOCK_B",BLOCK_NET,500,790);
            addHeader("TREE_A","90_AUX_A",220,400);
            addHeader("TREE_B","90_AUX_B",420,400);
            addHeader("TREE_C","90_AUX_C",760,400);
            addHeader("TREE_D","90_AUX_D",760,600);

            Vector<PcbPlacementConstraints.Part> demands=
                new Vector<PcbPlacementConstraints.Part>();
            for(String id:board.getComponentIds())
                demands.add(new PcbPlacementConstraints.Part(id,"p07","P07 endpoint fixture",
                    "p07",PcbPlacementConstraints.Anchor.NONE,16));
            board.setPlacementConstraints(new PcbPlacementConstraints(demands,
                new Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.BOTTOM,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION,1,false,profile));
            board.validate();
            layout.validateAgainst(board);
        }

        private void addHeader(String componentId,String auxiliaryNet,int padX,int padY) {
            PhysicalPackage physical=PhysicalPackages.THROUGH_HOLE_CONNECTOR_2;
            BoardComponent component=new BoardComponent(componentId,"STRUCTURAL",physical);
            board.addComponent(component);
            board.addPad(new BoardPad(componentId+".1",componentId,"1",TREE_NET));
            board.addPad(new BoardPad(componentId+".2",componentId,"2",auxiliaryNet));
            PcbPackagePose pose=new PcbPackagePose(padX-90,padY-40,PcbRotation.DEG_0,
                PcbBoardSide.TOP);
            PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,pose,
                physical.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
        }

        private void addPoint(String id,String net,int x,int y) {
            PhysicalPackage physical=P06FactoryLinkFixtures.TEST_POINT;
            BoardComponent component=new BoardComponent(id,"STRUCTURAL",physical);
            board.addComponent(component);
            board.addPad(new BoardPad(id+".1",id,"1",net));
            PcbPackagePose reference=new PcbPackagePose(100,100,PcbRotation.DEG_0,
                PcbBoardSide.TOP);
            Point local=physical.getGeometry().placedAt(reference).getPadPoint(0);
            PcbPackagePose pose=new PcbPackagePose(100+x-local.x,100+y-local.y,
                PcbRotation.DEG_0,PcbBoardSide.TOP);
            PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,pose,
                physical.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
        }
    }
}
