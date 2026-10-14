package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

/** Measurement-only branch progress diagnostic for the private RB56 role-width route. */
public final class PcbRoleWidthSearchDiagnosticTest {
    private static final long MAX_DIAGNOSTIC_STEPS=10000000L;
    private static final long PROGRESS_INTERVAL=250000L;
    private static final HashMap<String,Field> FIELDS=new HashMap<String,Field>();

    private PcbRoleWidthSearchDiagnosticTest() { }

    public static void main(String[] args) {
        try {
            measureRoot42Placement3();
            System.out.println("PASS: PCB role width search diagnostic complete");
        } catch (ReflectionDiagnosticFailure failure) {
            System.out.println("PCB_ROLE_WIDTH_SEARCH_DIAGNOSTIC status=MEASUREMENT_FAILURE"+
                " reason=REFLECTION_UNSUPPORTED detail="+safe(failure.getMessage()));
            throw failure;
        } catch (Throwable failure) {
            System.out.println("PCB_ROLE_WIDTH_SEARCH_DIAGNOSTIC status=MEASUREMENT_FAILURE"+
                " reason="+safe(failure.getMessage())+
                " type="+failure.getClass().getName());
            if (failure instanceof Error) throw (Error)failure;
            throw new AssertionError(failure);
        }
    }

    private static void measureRoot42Placement3() {
        Rb56Plan plan=Rb56Plan.resolve(42L,12);
        TroubleshootBoard board=plan.board();
        PcbPlacementConstraints constraints=board.getPlacementConstraints();
        if (constraints.getTraceWidthProfile()!=PcbTraceWidthProfile.ROLE_WIDTHS)
            throw new DiagnosticMeasurementFailure("root42 version12 did not select ROLE_WIDTHS");

        PcbPlacementPlanner planner=new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry());
        PcbPlacementPlanner.Plan placement=planner.plan(board,constraints,plan.layoutSeed,3);
        PcbBoardLayout layout=placement.materialize();
        String inputGeometry=layout.geometryFingerprint();
        String inputGeometryHash=PlayerSessionFingerprint.of(inputGeometry);
        System.out.println("PCB_ROLE_WIDTH_SEARCH_DIAGNOSTIC_BEGIN seed=42 version=12"+
            " candidate="+placement.candidate+" evaluations="+placement.evaluations+
            " profile="+constraints.getTraceWidthProfile().getIdentity()+
            " inputGeometrySha256="+inputGeometryHash);

        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(board,layout,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS);
        Snapshot previous=new Snapshot(session);
        long passStartExpansions=0L,steps=0L;
        String observedFailedOwner=null;
        boolean complete=false;
        BranchMetrics branchMetrics=null;
        while (!complete && steps<MAX_DIAGNOSTIC_STEPS) {
            int beforeExpansions=session.expansions();
            PendingPop pending=branchMetrics==null?null:peekQueueHead(branchMetrics.branch);
            complete=session.advanceSlice(1);
            steps++;
            int afterExpansions=session.expansions();
            int delta=afterExpansions-beforeExpansions;
            if (delta<0 || delta>1)
                throw new DiagnosticMeasurementFailure("one-unit slice changed expansions by "+delta);
            if (afterExpansions>PcbLayerRoutingPrototype.MAX_EXPANSIONS)
                throw new DiagnosticMeasurementFailure("P07 expansion cap exceeded: "+afterExpansions);
            if (pending!=null) branchMetrics.record(pending,afterExpansions-beforeExpansions);

            Snapshot current=new Snapshot(session);
            if (previous.branch!=current.branch) {
                if (previous.branch!=null) logBranchEnd(steps,previous,current,branchMetrics);
                branchMetrics=null;
                if (current.branch!=null) {
                    branchMetrics=new BranchMetrics(current.branch);
                    logBranchStart(steps,current);
                }
            }
            if (!same(previous.currentNet,current.currentNet))
                logNetChange(steps,current,observedFailedOwner);
            if (previous.pass!=current.pass) {
                observedFailedOwner=previous.branch!=null ||
                    read(previous.search,"publisher")!=null?previous.currentNet:null;
                System.out.println("PCB_ROLE_WIDTH_SEARCH_PASS_BOUNDARY step="+steps+
                    " from="+previous.pass+" to="+current.pass+
                    " completedPassExpansions="+(current.expansions-passStartExpansions)+
                    " cumulativeExpansions="+current.expansions+
                    " reason="+safe(current.reason));
                passStartExpansions=current.expansions;
            }
            if (steps%PROGRESS_INTERVAL==0L) logProgress(steps,current);
            previous=current;
        }

        if (!complete)
            throw new DiagnosticMeasurementFailure("raw-step safety bound reached; steps="+steps+
                " expansions="+session.expansions()+" (P07 cap remains unchanged)");

        Snapshot finalState=new Snapshot(session);
        if (finalState.branch!=null)
            logBranchEnd(steps,finalState,finalState,branchMetrics);
        if (finalState.expansions-passStartExpansions>0L)
            System.out.println("PCB_ROLE_WIDTH_SEARCH_PASS_FINAL pass="+finalState.pass+
                " expansions="+(finalState.expansions-passStartExpansions)+
                " cumulativeExpansions="+finalState.expansions+
                " reason="+safe(finalState.reason));

        PcbLayerRoutingPrototype.Result result=session.result();
        PcbLayerRoutingPrototype.Result blocking=PcbLayerRoutingPrototype.route(board,layout,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS);
        if(result.accepted()!=blocking.accepted() || !result.outcome.equals(blocking.outcome) ||
                result.expansions!=blocking.expansions || result.orderings!=blocking.orderings ||
                result.vias!=blocking.vias || result.links!=blocking.links ||
                result.accepted() && !result.layout.geometryFingerprint().equals(
                    blocking.layout.geometryFingerprint()))
            throw new DiagnosticMeasurementFailure("one-unit and blocking routing results differ");
        System.out.println("PCB_ROLE_WIDTH_SEARCH_BLOCKING_PARITY status=PASS"+
            " expansions="+blocking.expansions+" orderings="+blocking.orderings+
            " outcome="+safe(blocking.outcome)+" sourcePlacementShared=true");
        String afterGeometry=layout.geometryFingerprint();
        if (!inputGeometry.equals(afterGeometry))
            throw new DiagnosticMeasurementFailure("P07 mutated input placement geometry: before="+
                inputGeometry+" after="+afterGeometry);
        if (result.expansions>PcbLayerRoutingPrototype.MAX_EXPANSIONS ||
                result.expansions!=session.expansions())
            throw new DiagnosticMeasurementFailure("final P07 expansion accounting is inconsistent");
        System.out.println("PCB_ROLE_WIDTH_SEARCH_FINAL seed=42 version=12 candidate=3"+
            " steps="+steps+" expansions="+result.expansions+
            " cap="+PcbLayerRoutingPrototype.MAX_EXPANSIONS+
            " orderings="+result.orderings+" outcome="+safe(result.outcome)+
            " p07ResultAccepted="+result.accepted()+" candidateAcceptance=NOT_EVALUATED"+
            " inputGeometryUnchanged=true sessionReason="+safe(finalState.reason)+
            " measurementOnly=true");
    }

    private static void logBranchStart(long step,Snapshot state) {
        BranchState branch=new BranchState(state.branch);
        System.out.println("PCB_ROLE_WIDTH_SEARCH_BRANCH_START step="+step+
            " pass="+state.pass+" net="+safe(branch.net)+" start="+branch.start+
            " root="+branch.root+" expanded="+branch.expanded+
            " queue="+branch.queueSize+" best="+branch.bestSize+
            " currentNet="+safe(state.currentNet)+" "+context(state.search));
        PcbPadPlacement start=(PcbPadPlacement)read(state.branch,"start");
        PcbPadPlacement root=(PcbPadPlacement)read(state.branch,"root");
        Vector<?> reached=(Vector<?>)read(state.search,"reachedPadIds");
        if(state.pass==2 && "B_CMD".equals(branch.net) && reached.size()==1 &&
                (!"U2B.OUTPUT".equals(start.getPadId()) || start.getX()!=2370 ||
                    start.getY()!=1360 || !"RDB.1".equals(root.getPadId()) ||
                    root.getX()!=2470 || root.getY()!=440))
            throw new DiagnosticMeasurementFailure("root42 nearest first control branch differs from literal geometry");
        PcbNetRouter.Router[] faces=(PcbNetRouter.Router[])read(state.search,"faces");
        boolean[] goals=(boolean[])read(state.branch,"goal");
        int goalCount=0;
        for(boolean goal:goals) if(goal) goalCount++;
        System.out.println("PCB_ROLE_WIDTH_SEARCH_ENDPOINTS pass="+state.pass+
            " net="+safe(branch.net)+" start="+branch.start+"@"+start.getX()+","+start.getY()+
            " root="+branch.root+"@"+root.getX()+","+root.getY()+" goalCount="+goalCount+
            " startTop="+endpointMask(faces[0],branch.net,start,root,false)+
            " startBottom="+endpointMask(faces[1],branch.net,start,root,false)+
            " rootTop="+endpointMask(faces[0],branch.net,start,root,true)+
            " rootBottom="+endpointMask(faces[1],branch.net,start,root,true));
    }

    /** Read the selected implementation's actual legal steps without marking any copper. */
    private static int endpointMask(PcbNetRouter.Router face,String net,
            PcbPadPlacement start,PcbPadPlacement root,boolean inbound) {
        int[] dx={0,10,0,-10},dy={-10,0,10,0};
        PcbPadPlacement pad=inbound?root:start;
        int mask=0;
        for(int direction=0;direction<4;direction++) {
            int otherX=pad.getX()+dx[direction],otherY=pad.getY()+dy[direction];
            boolean legal=inbound?face.permitsLayerStep(otherX,otherY,pad.getX(),pad.getY(),
                net,start,root):face.permitsLayerStep(pad.getX(),pad.getY(),otherX,otherY,
                net,start,root);
            if(legal) mask|=1<<direction;
        }
        return mask;
    }

    private static void logBranchEnd(long step,Snapshot before,Snapshot after,
            BranchMetrics metrics) {
        BranchState branch=new BranchState(before.branch);
        String outcome=branch.found?"PATH_FOUND":
            after.search==before.search && read(after.search,"publisher")!=null?
                "PATH_FOUND":"SEARCH_ENDED";
        if (metrics==null || metrics.branch!=before.branch)
            throw new DiagnosticMeasurementFailure("branch metrics lost at branch boundary");
        System.out.println("PCB_ROLE_WIDTH_SEARCH_BRANCH_END step="+step+
            " pass="+before.pass+" net="+safe(branch.net)+" start="+branch.start+
            " root="+branch.root+" outcome="+outcome+
            " expanded="+branch.expanded+" queue="+branch.queueSize+
            " best="+branch.bestSize+" cumulativeExpansions="+after.expansions+
            " validQueuePops="+metrics.validQueuePops+
            " staleQueuePops="+metrics.staleQueuePops+
            " distinctExpandedStates="+metrics.expandedKeys.size()+
            " reopenedStateExpansions="+metrics.reopenedStateExpansions+
            " validPopsWithoutExpansion="+metrics.validPopsWithoutExpansion+
            " reason="+safe(after.reason)+" currentNet="+safe(before.currentNet)+
            " "+context(before.search));
    }

    private static void logNetChange(long step,Snapshot state,String expectedFirst) {
        if(state.search!=null && state.currentNet!=null && integer(state.search,"netIndex")==1) {
            Vector<?> order=(Vector<?>)read(state.search,"nets");
            TreeSet<String> members=new TreeSet<String>();
            for(Object member:order) if(!members.add((String)member))
                throw new DiagnosticMeasurementFailure("pass net order contains a duplicate");
            TroubleshootBoard board=(TroubleshootBoard)read(state.search,"board");
            if(!members.equals(new TreeSet<String>(board.getNetIds())) ||
                    !state.currentNet.equals(order.firstElement()) ||
                    expectedFirst!=null && !expectedFirst.equals(state.currentNet))
                throw new DiagnosticMeasurementFailure("pass net membership or failed-owner promotion differs");
            System.out.println("PCB_ROLE_WIDTH_SEARCH_PASS_ORDER pass="+state.pass+
                " expectedFailedOwner="+safe(expectedFirst)+" activeFirstNet="+
                safe(state.currentNet)+" membershipExact=true order="+safe(order.toString()));
        }
        System.out.println("PCB_ROLE_WIDTH_SEARCH_NET step="+step+" pass="+state.pass+
            " net="+safe(state.currentNet)+" cumulativeExpansions="+state.expansions+
            " "+context(state.search));
    }

    private static void logProgress(long step,Snapshot state) {
        String queue="-",best="-";
        if (state.branch!=null) {
            BranchState branch=new BranchState(state.branch);
            queue=Integer.toString(branch.queueSize);
            best=Integer.toString(branch.bestSize);
        }
        System.out.println("PCB_ROLE_WIDTH_SEARCH_PROGRESS step="+step+
            " pass="+state.pass+" net="+safe(state.currentNet)+
            " expansions="+state.expansions+" queue="+queue+" best="+best);
    }

    private static String context(Object search) {
        if (search==null) return "pending=[] reached=[]";
        return "pending="+safe(vectorString(read(search,"currentPadIds")))+
            " reached="+safe(vectorString(read(search,"reachedPadIds")));
    }

    private static PendingPop peekQueueHead(Object branch) {
        Object queue=read(branch,"queue");
        int size=integer(queue,"size");
        if (size==0) return new PendingPop(branch,false,false,null);
        Object[] values=(Object[])read(queue,"values");
        Object node=values[0];
        if (node==null) return new PendingPop(branch,false,false,null);
        Integer key=Integer.valueOf(integer(node,"key"));
        Map<?,?> best=(Map<?,?>)read(branch,"best");
        return new PendingPop(branch,true,best.get(key)!=node,key);
    }

    private static String vectorString(Object value) {
        return value==null?"[]":((Vector<?>)value).toString();
    }

    private static boolean same(Object first,Object second) {
        return first==null?second==null:first.equals(second);
    }

    private static String safe(String value) {
        if (value==null) return "-";
        return value.replace('\n','_').replace('\r','_').replace(' ','_');
    }

    private static Object read(Object target,String name) {
        if (target==null) return null;
        String key=target.getClass().getName()+"#"+name;
        Field field=FIELDS.get(key);
        if (field==null) {
            try {
                field=target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                FIELDS.put(key,field);
            } catch (Exception failure) {
                throw new ReflectionDiagnosticFailure("Cannot inspect "+key+": "+failure);
            }
        }
        try {
            return field.get(target);
        } catch (Exception failure) {
            throw new ReflectionDiagnosticFailure("Cannot read "+key+": "+failure);
        }
    }

    private static int integer(Object target,String name) {
        return ((Number)read(target,name)).intValue();
    }

    private static String string(Object target,String name) {
        Object value=read(target,name);
        return value==null?null:String.valueOf(value);
    }

    private static final class Snapshot {
        final int pass,expansions;
        final Object search,branch;
        final String currentNet,reason;
        Snapshot(PcbLayerRoutingPrototype.Session session) {
            pass=integer(session,"pass");
            search=read(session,"search");
            branch=read(search,"branch");
            currentNet=string(search,"currentNet");
            reason=string(session,"reason");
            expansions=session.expansions();
        }
    }

    private static final class BranchState {
        final String net,start,root;
        final int expanded,queueSize,bestSize;
        final boolean found;
        BranchState(Object branch) {
            net=string(branch,"net");
            PcbPadPlacement startPad=(PcbPadPlacement)read(branch,"start");
            PcbPadPlacement rootPad=(PcbPadPlacement)read(branch,"root");
            start=startPad==null?"-":startPad.getPadId();
            root=rootPad==null?"-":rootPad.getPadId();
            expanded=integer(branch,"expanded");
            Object queue=read(branch,"queue");
            queueSize=integer(queue,"size");
            Map<?,?> best=(Map<?,?>)read(branch,"best");
            bestSize=best.size();
            found=read(branch,"found")!=null;
        }
    }

    private static final class PendingPop {
        final Object branch;
        final boolean hasHead,stale;
        final Integer key;
        PendingPop(Object branch,boolean hasHead,boolean stale,Integer key) {
            this.branch=branch; this.hasHead=hasHead; this.stale=stale; this.key=key;
        }
    }

    private static final class BranchMetrics {
        final Object branch;
        final Set<Integer> expandedKeys=new HashSet<Integer>();
        long validQueuePops,staleQueuePops,reopenedStateExpansions,validPopsWithoutExpansion;
        BranchMetrics(Object branch) { this.branch=branch; }
        void record(PendingPop pending,int expansionDelta) {
            if (pending.branch!=branch)
                throw new DiagnosticMeasurementFailure("queue-head sample crossed branch identity");
            if (!pending.hasHead) return;
            if (pending.stale) {
                if (expansionDelta!=0)
                    throw new DiagnosticMeasurementFailure("stale queue pop counted as an expansion");
                staleQueuePops++;
                return;
            }
            validQueuePops++;
            if (expansionDelta==1) {
                if (!expandedKeys.add(pending.key)) reopenedStateExpansions++;
            } else if (expansionDelta==0) {
                validPopsWithoutExpansion++;
            } else {
                throw new DiagnosticMeasurementFailure("invalid expansion delta for queue pop: "+
                    expansionDelta);
            }
        }
    }

    private static final class ReflectionDiagnosticFailure extends RuntimeException {
        ReflectionDiagnosticFailure(String message) { super(message); }
    }

    private static final class DiagnosticMeasurementFailure extends RuntimeException {
        DiagnosticMeasurementFailure(String message) { super(message); }
    }
}
