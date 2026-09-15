package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.Vector;

/** Independent acceptance and stale-occupancy negatives for P05's real routing owner. */
public final class P05RoutingContractTest {
    private static int assertions;
    private static void require(boolean value,String why) {assertions++;if(!value)throw new AssertionError(why);}
    private static Vector<String> vector(String... values) {return new Vector<String>(Arrays.asList(values));}
    private static final PcbRoutingWork.Limits SEQUENTIAL=new PcbRoutingWork.Limits(1,0,0,0,1000000);
    public static void main(String[] args) throws Exception {
        recoveryAblations(); occupancy(); changedPathAndFailedSearch(); exhaustionAndAbort();
        CirSim sim=new CirSim();sim.gridSize=16;sim.gridMask=~15;sim.gridRound=7;CircuitElm.sim=sim;
        PcbLayoutDeveloperVerifier.verifyBoundedRecovery();
        require(PcbRoutingWork.Limits.DEFAULT.orderings==5 && PcbRoutingWork.Limits.DEFAULT.ripUpPasses==2 &&
            PcbRoutingWork.Limits.DEFAULT.victimsPerPass==2 && PcbRoutingWork.Limits.DEFAULT.reroutedNets==6 &&
            PcbRoutingWork.Limits.DEFAULT.expansions==1000000,"explicit unchanged qualification caps");
        System.out.println("PASS: P05 routing contracts assertions="+assertions);
    }
    private static void recoveryAblations() throws Exception {
        TroubleshootBoard board=P05RoutingCorpus.board("parallel",-17);
        PcbPlacementPlanner.Plan plan=P05RoutingCorpus.plan("parallel",-17,1,board);
        PcbBoardLayout naive=plan.materialize();String identity=P05RoutingCorpus.identity(board,naive);
        PcbNetRouter.Rejected failure=exhaust(naive,board,1,-17,null,SEQUENTIAL);
        require(failure.reason==PcbNetRouter.Reason.NO_PATH && "BRANCH1_NODE".equals(failure.blockedNet),"frozen real order-sensitive failure");
        PcbBoardLayout ordered=plan.materialize();
        // Same root/branch algorithm, same budget, same geometry. Only the failed net moves first.
        PcbNetRouter.routeWithLimits(ordered,board,plan.outline,1,P05RoutingCorpus.OBSERVER,true,-17,0,vector(failure.blockedNet),SEQUENTIAL);
        require(ordered.getRoutingRecoveryStatistics().ripUpPasses==0 && ordered.getRoutingRecoveryStatistics().orderingPasses==1,
            "order-only success cannot be attributed to rip-up or a different tree variant");
        PcbRoutingWork.Limits local=new PcbRoutingWork.Limits(1,2,2,6,1000000);
        PcbBoardLayout recovered=plan.materialize();
        PcbNetRouter.routeWithLimits(recovered,board,plan.outline,1,P05RoutingCorpus.OBSERVER,true,-17,0,null,local);
        PcbRoutingWork.Statistics work=recovered.getRoutingRecoveryStatistics();
        require(work.orderingPasses==1 && work.ripUpPasses==1 && work.rippedNets==2 && work.reroutedNets==3,
            "one actual legal-route rip-up recovers without an alternate pass");
        require(work.decisions.contains("rip[BRANCH1_NODE]=[VIN, GND]"),"actual conflict counts select deterministic victims");
        for(PcbBoardLayout layout:new PcbBoardLayout[]{ordered,recovered}) {
            layout.validateRoutingGeometry(board);
            require(identity.equals(P05RoutingCorpus.identity(board,layout)),"recovery preserves every component/pad/net identity and placement");
        }
        PcbBoardLayout repeated=plan.materialize();
        PcbNetRouter.routeWithLimits(repeated,board,plan.outline,1,P05RoutingCorpus.OBSERVER,true,-17,0,null,local);
        require(recovered.geometryFingerprint().equals(repeated.geometryFingerprint()) &&
            work.toCanonical().equals(repeated.getRoutingRecoveryStatistics().toCanonical()),"same seed gives identical copper and work decisions");
        recovered.seal();require(recovered.copySealed().getRoutingRecoveryStatistics().toCanonical().equals(work.toCanonical()),"sealed copies retain evidence");
        System.out.println("P05_PROOF order-only="+ordered.getRoutingRecoveryStatistics().toCanonical());
        System.out.println("P05_PROOF rip-up="+work.toCanonical());
        TroubleshootBoard easy=P05RoutingCorpus.board("led",0);
        PcbPlacementPlanner.Plan easyPlan=P05RoutingCorpus.plan("led",0,0,easy);PcbBoardLayout control=easyPlan.materialize();
        PcbNetRouter.route(control,easy,easyPlan.outline,0,P05RoutingCorpus.OBSERVER);
        require(control.getRoutingRecoveryStatistics().orderingPasses==1 && control.getRoutingRecoveryStatistics().ripUpPasses==0,"easy control pays no recovery passes");
    }
    private static PcbNetRouter.Rejected exhaust(PcbBoardLayout layout,TroubleshootBoard board,int attempt,long seed,
            Vector<String> blocked,PcbRoutingWork.Limits limits) {
        String before=layout.geometryFingerprint();
        try {PcbNetRouter.routeWithLimits(layout,board,layout.getBoardOutline(),attempt,P05RoutingCorpus.OBSERVER,true,seed,0,blocked,limits);
            throw new AssertionError("Expected deterministic routing exhaustion");}
        catch(PcbNetRouter.Rejected expected) {
            require(layout.getTraces().isEmpty() && before.equals(layout.geometryFingerprint()),"exhaustion publishes no partial candidate");
            require(expected.statistics!=null && expected.statistics.outcome==PcbRoutingWork.Outcome.EXHAUSTED,
                "routing exhaustion carries its classified immutable work receipt");
            require(expected.statistics.expansions<=limits.expansions && expected.statistics.ripUpPasses<=limits.ripUpPasses &&
                expected.statistics.reroutedNets<=limits.reroutedNets,"all deterministic work caps hold");
            return expected;
        }
    }
    private static final class Fixture {
        final TroubleshootBoard board=new TroubleshootBoard("P05_FIXED_GRID");
        final PcbBoardLayout layout=new PcbBoardLayout(800,600,new Rectangle(100,100,400,400),new Rectangle(550,120,180,300));
        final Vector<PcbFootprint> footprints=new Vector<PcbFootprint>();
        Fixture(int[][] positions,String... nets) {
            Vector<PcbPlacementConstraints.Part> demands=new Vector<PcbPlacementConstraints.Part>();
            for(int i=0;i<positions.length;i++) {
                String id="P"+i,net=nets[i];if(board.getNet(net)==null)board.addNet(new BoardNet(net));
                boolean inward=positions[i][0]==490;
                PhysicalPackage physical=pointPackage(inward);
                BoardComponent component=new BoardComponent(id,"TEST_POINT",physical,id);board.addComponent(component);
                board.addPad(new BoardPad(id+".1",id,"1",net));
                PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,positions[i][0]-(inward?30:10),positions[i][1]-10);
                footprints.add(footprint);layout.addComponent(footprint.getPlacement());
                for(PcbPadPlacement pad:footprint.getPads())layout.addPad(pad);
                demands.add(new PcbPlacementConstraints.Part(id,"fixture","Routing fixture","low-voltage",PcbPlacementConstraints.Anchor.NONE,16));
            }
            board.setPlacementConstraints(new PcbPlacementConstraints(demands,new Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.BOTTOM));
            board.validate();
        }
        PcbNetRouter.Router router() {return new PcbNetRouter.Router(layout,board,layout.getBoardOutline(),0,P05RoutingCorpus.OBSERVER);}
    }
    private static PhysicalPackage pointPackage(boolean inward) {
        int padX=inward?30:10,bodyX=inward?8:22;
        Point pad=new Point(padX,10),body=new Point(inward?16:24,10);
        PhysicalPackageGeometry.Lead connected=new PhysicalPackageGeometry.Lead(pad,body,
            new Rectangle(inward?14:8,8,18,4),new Point(inward?16:22,10),new Rectangle(inward?14:20,8,6,4));
        PhysicalPackageGeometry.Lead lifted=new PhysicalPackageGeometry.Lead(new Point(inward?8:32,10),body,
            new Rectangle(inward?6:22,8,12,4),new Point(inward?10:30,10),new Rectangle(inward?8:28,8,6,4));
        Vector<PhysicalPackageGeometry.Terminal> terminals=new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(new PhysicalPackageGeometry.Terminal("1",pad,new Rectangle(padX-4,6,8,8),pad,
            new Rectangle(padX-5,5,10,10),connected,lifted,0,0,0));
        return new PhysicalPackage("P05_TEST_POINT_"+(inward?"IN":"OUT"),vector("1"),new Vector<String>(),false,
            new PhysicalPackageGeometry(40,20,terminals,new Rectangle(bodyX,6,10,8),new Rectangle(inward?4:20,4,16,12),
                new Rectangle(0,0,40,20),new Rectangle(0,0,40,20),new Rectangle(0,0,40,20)));
    }
    private static String[][] cells(PcbNetRouter.Router router,String field) throws Exception {
        Field f=PcbNetRouter.Router.class.getDeclaredField(field);f.setAccessible(true);return (String[][])f.get(router);
    }
    private static String at(String[][] cells,int x,int y) {return cells[(x-110)/10][(y-110)/10];}
    private static PcbTraceGeometry add(PcbNetRouter.Router router,Fixture fixture,String net,int start,int end) {
        PcbTraceGeometry trace=router.route(net,"P"+start+".1","P"+end+".1");fixture.layout.addTrace(trace);return trace;
    }
    private static boolean same(String a,String b) {return a==null?b==null:a.equals(b);}
    /** Independent trace raster, not the production occupancy reconstruction. */
    private static void oracle(Fixture fixture,PcbNetRouter.Router router) throws Exception {
        TreeMap<String,String> expected=new TreeMap<String,String>();
        for(PcbTraceGeometry trace:fixture.layout.getTraces()) {
            int[] xs=trace.getXPoints(),ys=trace.getYPoints();
            for(int i=1;i<xs.length;i++) {
                int length=Math.abs(xs[i]-xs[i-1])+Math.abs(ys[i]-ys[i-1]);
                int dx=Integer.signum(xs[i]-xs[i-1]),dy=Integer.signum(ys[i]-ys[i-1]);
                for(int step=0;step<=length;step++) {
                    int x=xs[i-1]+step*dx,y=ys[i-1]+step*dy;
                    if((x-110)%10!=0 || (y-110)%10!=0)continue;
                    String key=x+","+y,old=expected.put(key,trace.getNetId());
                    require(old==null || old.equals(trace.getNetId()),"independent oracle rejects cross-net overlap");
                }
            }
        }
        String[][] occupied=cells(router,"occupiedNet"),clearance=cells(router,"clearanceNet");
        for(int x=0;x<occupied.length;x++)for(int y=0;y<occupied[x].length;y++) {
            int ax=110+10*x,ay=110+10*y;
            require(same(expected.get(ax+","+ay),occupied[x][y]),"trace raster exactly equals owned occupancy");
            HashSet<String> owners=new HashSet<String>();
            for(int dx=-10;dx<=10;dx+=10)for(int dy=-10;dy<=10;dy+=10) {
                String owner=expected.get((ax+dx)+","+(ay+dy));if(owner!=null)owners.add(owner);
            }
            String wanted=owners.isEmpty()?null:owners.size()>1?"":owners.iterator().next();
            require(same(wanted,clearance[x][y]),"independent shared-clearance oracle");
        }
        router.requireExactOccupancy();
    }
    private static void occupancy() throws Exception {
        Fixture f=new Fixture(new int[][]{{150,250},{450,250},{150,270},{450,270}},"A","A","AB","AB");
        PcbNetRouter.Router router=f.router();add(router,f,"A",0,1);PcbTraceGeometry other=add(router,f,"AB",2,3);
        oracle(f,router);
        require("".equals(at(cells(router,"clearanceNet"),300,260)),"two legal rails share a clearance halo");
        router.removeNets(vector("A"));oracle(f,router);
        require(f.layout.getTraces().size()==1 && f.layout.getTraces().get(0)==other,"rip-up preserves the other owner's exact geometry");
        require(at(cells(router,"occupiedNet"),300,250)==null && "AB".equals(at(cells(router,"occupiedNet"),300,270)),"removed rail released, unrelated rail retained");
        require("AB".equals(at(cells(router,"clearanceNet"),300,260)),"shared halo remains owned by the survivor");
        String state=Arrays.deepToString(cells(router,"occupiedNet"))+Arrays.deepToString(cells(router,"clearanceNet"));
        for(int repeat=0;repeat<8;repeat++)router.removeNets(vector("A"));
        require(state.equals(Arrays.deepToString(cells(router,"occupiedNet"))+Arrays.deepToString(cells(router,"clearanceNet"))),"repeated rip-up is idempotent");
        try {router.removeNets(vector("unknown"));throw new AssertionError("unknown owner accepted");}
        catch(IllegalArgumentException expected) {require(f.layout.getTraces().get(0)==other,"invalid owner cannot remove another net");}
        // This negative fails if cleanup leaves a ghost halo, or the final audit repairs instead of detecting it.
        String[][] halo=cells(router,"clearanceNet");halo[9][9]="A";
        try {router.requireExactOccupancy();throw new AssertionError("stale halo survived publication gate");}
        catch(IllegalStateException expected) {require(expected.getMessage().contains("clearance"),"stale-clearance regression is detected");}
        halo[9][9]=null;
        String[][] occupied=cells(router,"occupiedNet");occupied[9][9]="A";
        try {router.requireExactOccupancy();throw new AssertionError("ghost copper survived publication gate");}
        catch(IllegalStateException expected) {require(expected.getMessage().contains("occupancy"),"ghost-copper regression is detected");}
        occupied[9][9]=null;add(router,f,"A",0,1);oracle(f,router);
        PcbRouteCanonicalizer.canonicalize(f.layout);oracle(f,router);f.layout.validateRoutingGeometry(f.board);
        System.out.println("P05_OCCUPANCY shared-halo ownership, exact survivor, repeated rip-up, ghost-copper and stale-clearance negatives PASS");
    }
    private static void changedPathAndFailedSearch() throws Exception {
        Fixture f=new Fixture(new int[][]{{150,250},{450,250},{150,350},{450,350},{300,220},{300,280}},"A","A","B","B","C","C");
        PcbNetRouter.Router router=f.router();PcbTraceGeometry old=add(router,f,"A",0,1),other=add(router,f,"B",2,3);
        String oldPath=Arrays.toString(old.getXPoints())+Arrays.toString(old.getYPoints());
        router.removeNets(vector("A"));add(router,f,"C",4,5);PcbTraceGeometry changed=add(router,f,"A",0,1);
        require(!oldPath.equals(Arrays.toString(changed.getXPoints())+Arrays.toString(changed.getYPoints())),"victim gets a genuinely different legal path");
        require(f.layout.getTraces().contains(other) && "C".equals(at(cells(router,"occupiedNet"),300,250)),"new constrained route replaces old rail without altering unrelated copper");
        oracle(f,router);PcbRouteCanonicalizer.canonicalize(f.layout);oracle(f,router);f.layout.validateRoutingGeometry(f.board);
        router.removeNets(vector("A"));String remaining=Arrays.deepToString(cells(router,"occupiedNet"))+Arrays.deepToString(cells(router,"clearanceNet"));
        router.work=new PcbRoutingWork(new PcbRoutingWork.Limits(1,0,0,0,1),f.board.getNetIds().size());
        try {router.route("A","P0.1","P1.1");throw new AssertionError("one expansion unexpectedly routed the victim");}
        catch(PcbNetRouter.Rejected expected) {require(expected.reason==PcbNetRouter.Reason.SEARCH_LIMIT,"failed victim has a precise work-limit outcome");}
        require(remaining.equals(Arrays.deepToString(cells(router,"occupiedNet"))+Arrays.deepToString(cells(router,"clearanceNet"))),"failed search leaves no partial copper or halo");
        oracle(f,router);
    }
    private static void exhaustionAndAbort() throws Exception {
        Fixture f=new Fixture(new int[][]{{110,300},{490,300},{300,110},{300,490}},"A","A","B","B");
        // Reduced routing-grid fixture, not a claim of generator placement admission.
        // Opposite boundary pairs alternate around a planar grid: they cannot be joined disjointly on one layer.
        PcbNetRouter.Rejected impossible=exhaust(f.layout,f.board,0,0,null,PcbRoutingWork.Limits.DEFAULT);
        require(impossible.reason==PcbNetRouter.Reason.NO_PATH && impossible.statistics.ripUpPasses>0,"true one-layer crossing exhausts after real failed recovery");
        Fixture repeat=new Fixture(new int[][]{{110,300},{490,300},{300,110},{300,490}},"A","A","B","B");
        PcbNetRouter.Rejected again=exhaust(repeat.layout,repeat.board,0,0,null,PcbRoutingWork.Limits.DEFAULT);
        require(impossible.statistics.toCanonical().equals(again.statistics.toCanonical()),"infeasible exhaustion is deterministic");
        Fixture capped=new Fixture(new int[][]{{110,300},{490,300},{300,110},{300,490}},"A","A","B","B");
        PcbNetRouter.Rejected limit=exhaust(capped.layout,capped.board,0,0,null,new PcbRoutingWork.Limits(1,0,0,0,1));
        require(limit.reason==PcbNetRouter.Reason.SEARCH_LIMIT && limit.statistics.expansions==1,"forced budget exhaustion stops at exactly one expansion");
        PcbRoutingRejectedException outer=PcbRoutingRejectedException.exhausted(0,80,
            PcbRoutingRejectedException.attemptRejected(PcbRoutingRejectedException.Kind.ROUTING,79,0,"fixed-grid",impossible.statistics));
        require(outer.getKind()==PcbRoutingRejectedException.Kind.ROUTING && outer.getRoutingRecoveryStatistics()==impossible.statistics,"outer generation rejection retains the exact routing receipt");
        final Fixture aborted=new Fixture(new int[][]{{110,300},{490,300},{300,110},{300,490}},"A","A","B","B");
        final RuntimeException sentinel=new IllegalStateException("observer invariant sentinel");final int[] checks={0};
        try {PcbNetRouter.route(aborted.layout,aborted.board,aborted.layout.getBoardOutline(),0,
            new SeededPcbLayoutGenerator.AttemptObserver(){public void check(int attempt) {
                require(aborted.layout.getTraces().isEmpty(),"no intermediate route reaches the caller at a checkpoint");
                if(++checks[0]==3)throw sentinel;
            }});throw new AssertionError("observer failure swallowed");}
        catch(RuntimeException expected) {require(expected==sentinel,"programming/observer failures propagate unchanged, not as routing exhaustion");}
        require(aborted.layout.getTraces().isEmpty() && aborted.layout.getRoutingRecoveryStatistics().outcome==PcbRoutingWork.Outcome.ABORTED,"aborted candidate publishes nothing");
        for(final GenerationJob.CheckpointFailure stop:new GenerationJob.CheckpointFailure[]{
                new GenerationJob.Cancelled("P05 cancelled"),new GenerationJob.Stale("P05 stale"),new GenerationJob.Deadline("P05 deadline")}) {
            final Fixture interrupted=new Fixture(new int[][]{{110,300},{490,300},{300,110},{300,490}},"A","A","B","B");
            final int[] calls={0};
            try {PcbNetRouter.route(interrupted.layout,interrupted.board,interrupted.layout.getBoardOutline(),0,
                new SeededPcbLayoutGenerator.AttemptObserver(){public void check(int attempt){if(++calls[0]==3)throw stop;}});
                throw new AssertionError("checkpoint stop was swallowed");}
            catch(GenerationJob.CheckpointFailure expected) {require(expected==stop,"cancellation/stale/deadline classification propagates unchanged");}
            require(interrupted.layout.getTraces().isEmpty() && interrupted.layout.getRoutingRecoveryStatistics().outcome==PcbRoutingWork.Outcome.ABORTED,
                "checkpoint abort discards private partial copper without retrying");
        }
        System.out.println("P05_EXHAUSTION "+impossible.statistics.toCanonical());
        System.out.println("P05_BUDGET "+limit.statistics.toCanonical());
    }
}
