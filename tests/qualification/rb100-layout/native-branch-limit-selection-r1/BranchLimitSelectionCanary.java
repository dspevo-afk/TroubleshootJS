package com.lushprojects.circuitjs1.client;

import java.lang.reflect.*;
import java.util.*;

/** Native-only canary; every cap frame below is explicitly injected, never a routed workload. */
public final class BranchLimitSelectionCanary {
    static int checks,fixtures,sessions,caseGroups,detachedCopies;
    static final int SETUP_GUARD=1000, DRAIN_GUARD=100000;
    private BranchLimitSelectionCanary() { }

    public static void main(String[] args) {
        try {
        comparatorTuple();
        actualRouteParity();
        selectedAndStaleBoundary();
        capPrecedenceAndEmptyFrontier();
        completedBoundRetainsOldH();
        callbackFailureAndReuse(false);
        callbackFailureAndReuse(true);
        detachedMarker();
        System.out.println("PASS BranchLimitSelectionCanary cases="+caseGroups+" assertions="+checks+
            " fixtures="+fixtures+" beginCalls="+sessions+" detachedCopies="+detachedCopies+
            " boundary=INJECTED_GENERIC_FRAME_ONLY authored=0");
        } catch(RuntimeException failure) {
            failureProgress(); throw failure;
        } catch(Error failure) {
            failureProgress(); throw failure;
        }
    }

    private static void failureProgress() {
        System.err.println("{\"status\":\"FAILED_GENERIC_CANARY\",\"casesStarted\":"+caseGroups+
            ",\"assertions\":"+checks+",\"fixtures\":"+fixtures+",\"beginCalls\":"+sessions+
            ",\"detachedCopies\":"+detachedCopies+",\"authored\":0}");
    }

    private static void comparatorTuple() {
        caseGroups++;
        int cells=1000;
        Object q=construct("NodeQueue");
        Object[] n={
            node(cells,50,0,0,0,5,3,4), // f8,h3,key50,serial4
            node(cells,40,0,0,0,1,6,1), // f7
            node(cells,30,0,0,0,6,2,0), // f8,h2
            node(cells,2,0,0,0,4,4,0),  // f8,h4,key2
            node(cells,50,0,0,0,4,4,9), // same tuple except serial
            node(cells,50,0,0,0,4,4,3)
        };
        for(int i:new int[]{0,4,2,1,3,5}) invoke(q,"add",new Class<?>[]{nodeType()},n[i]);
        int[] order={1,2,0,3,5,4};
        for(int i:order) check(invoke(q,"poll",new Class<?>[0])==n[i],
            "NodeQueue orders by stored f/h/key/serial tuple");
        check(num(q,"size")==0 && num(n[0],"key")==num(n[5],"key"),
            "actual queue drains and serial tie uses the same state key");
    }

    private static void actualRouteParity() {
        caseGroups++;
        PcbLayerRoutingPrototype.Policy p=PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER;
        Run plain=run(fixture(),null,PcbLayerRoutingPrototype.MAX_EXPANSIONS,p);
        Sink oldSink=new Sink(); Run old=run(fixture(),oldSink,PcbLayerRoutingPrototype.MAX_EXPANSIONS,p);
        Marker marker=new Marker(); Run selected=run(fixture(),marker,PcbLayerRoutingPrototype.MAX_EXPANSIONS,p);
        check(plain.result.accepted() && old.result.accepted() && selected.result.accepted(),
            "actual small P06 route succeeds with default, old terminal and new marker modes");
        sameResult(plain.result,old.result); sameResult(plain.result,selected.result);
        sameEvents(oldSink.events,marker.events);
        check(plain.before.equals(plain.fixture.layout.geometryFingerprint()) &&
            old.before.equals(old.fixture.layout.geometryFingerprint()) &&
            selected.before.equals(selected.fixture.layout.geometryFingerprint()),
            "small-route parity preserves caller source geometry");

        Run blocked=run(fixture(),null,1,p);
        Sink oldBlocked=new Sink(); Run oldLimit=run(fixture(),oldBlocked,1,p);
        Marker newBlocked=new Marker(); Run newLimit=run(fixture(),newBlocked,1,p);
        check(!blocked.result.accepted() && blocked.result.expansions==1 &&
            blocked.result.outcome.endsWith("TOTAL_SEARCH_LIMIT"),
            "actual one-expansion Session reaches total-cap blocked result");
        sameResult(blocked.result,oldLimit.result); sameResult(blocked.result,newLimit.result);
        check(oldBlocked.calls==1 && newBlocked.calls==1 && newBlocked.selected==0,
            "total cap remains ordinary terminal callback for the new marker");
        sameEvents(oldBlocked.events,newBlocked.events);
        check(blocked.before.equals(blocked.fixture.layout.geometryFingerprint()) &&
            oldLimit.before.equals(oldLimit.fixture.layout.geometryFingerprint()) &&
            newLimit.before.equals(newLimit.fixture.layout.geometryFingerprint()),
            "blocked parity preserves all caller source geometry");
    }

    private static void selectedAndStaleBoundary() {
        caseGroups++;
        Marker marker=new Marker(); Frame f=frame(PcbLayerRoutingPrototype.MAX_EXPANSIONS,marker);
        int cells=num(f.search,"cells"),layer=num(f.search,"primary");
        Object stale=node(cells,1,layer,0,0,0,0,1);
        Object replacement=node(cells,1,layer,0,0,900,900,2);
        Object chosen=node(cells,2,layer,1,0,3,2,5);
        Object later=node(cells,3,layer,1,0,4,2,6);
        frontier(f.branch,new Object[]{stale,chosen,later},new Object[]{replacement,chosen,later});
        counters(f,PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS,
            PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS);
        Object q=get(f.branch,"queue"),ix=get(f.branch,"best");
        check(best(f.branch,num(stale,"key"))==replacement && best(f.branch,num(chosen,"key"))==chosen,
            "injected best index identifies stale and current records independently");
        sameIdentity(queue(q),new Object[]{stale,chosen,later},"injected pre-poll heap");
        Image beforeIndex=new Image(ix);
        int expanded=num(f.branch,"expanded"),total=num(get(f.session,"budget"),"expanded");

        f.session.advanceSlice(1);
        check(marker.selected==0 && marker.calls==0 && get(f.session,"search")==f.search,
            "stale heap head is rejected before any terminal selection");
        sameIdentity(queue(q),new Object[]{chosen,later},"stale rejection removes only its heap record");
        beforeIndex.same(get(f.branch,"best"),"stale rejection preserves raw index slots");
        check(num(f.branch,"expanded")==expanded && num(get(f.session,"budget"),"expanded")==total,
            "stale rejection does not expand or change shared counts");

        marker.watch=new Watch(f,new Object[]{later},beforeIndex,chosen,
            PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS);
        f.session.advanceSlice(1);
        check(marker.selected==1 && marker.calls==0 && get(f.session,"search")==null,
            "fresh identity-live head alone uses selected callback at branch cap");
        PcbLayerRoutingPrototype.BranchSnapshot e=marker.events.get(0);
        PcbLayerRoutingPrototype.BranchLimitSelection selection=marker.selections.get(0);
        verifySelectionDto(selection);
        verifySelection(selection,f,chosen);
        verifySnapshotContext(e,f,PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS);
        check(e.frontierCount==1 && e.bestStateCount==3,
            "snapshot captures post-pop queue and unchanged index counts");

        Sink oldSink=new Sink(); Frame old=frame(PcbLayerRoutingPrototype.MAX_EXPANSIONS,oldSink);
        int oldCells=num(old.search,"cells"),oldLayer=num(old.search,"primary");
        Object oldStale=node(oldCells,1,oldLayer,0,0,0,0,1);
        Object oldReplacement=node(oldCells,1,oldLayer,0,0,900,900,2);
        Object oldNode=node(oldCells,2,oldLayer,1,0,3,2,5);
        Object oldLater=node(oldCells,3,oldLayer,1,0,4,2,6);
        frontier(old.branch,new Object[]{oldStale,oldNode,oldLater},
            new Object[]{oldReplacement,oldNode,oldLater});
        counters(old,PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS,
            PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS);
        old.session.advanceSlice(1);
        check(oldSink.calls==0,"old terminal frame skips the same stale record first");
        old.session.advanceSlice(1);
        check(oldSink.calls==1,"old terminal marker retains the branch-cap callback");
        sameSnapshot(oldSink.events.get(0),e,
            "selected callback snapshot equals the old terminal context field-for-field");
    }

    private static void capPrecedenceAndEmptyFrontier() {
        caseGroups++;
        Marker total=new Marker();
        Frame f=frame(PcbLayerRoutingPrototype.MAX_EXPANSIONS,total);
        Object current=node(num(f.search,"cells"),4,num(f.search,"primary"),0,0,8,2,11);
        frontier(f.branch,new Object[]{current},new Object[]{current});
        counters(f,PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS,
            PcbLayerRoutingPrototype.MAX_EXPANSIONS);
        f.session.advanceSlice(1);
        check(total.selected==0 && total.calls==1 &&
            "TOTAL_SEARCH_LIMIT".equals(total.events.get(0).terminalReason),
            "total-cap check precedes a simultaneously reached local branch cap");

        Marker empty=new Marker();
        Frame noPath=frame(PcbLayerRoutingPrototype.MAX_EXPANSIONS,empty);
        frontier(noPath.branch,new Object[0],new Object[0]);
        counters(noPath,PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS,
            PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS);
        noPath.session.advanceSlice(1);
        check(empty.selected==0 && empty.calls==1 &&
            "NO_PATH".equals(empty.events.get(0).terminalReason) &&
            empty.events.get(0).frontierCount==0 && empty.events.get(0).bestStateCount==0,
            "empty frontier takes ordinary NO_PATH without inventing a selected node");
    }

    private static void completedBoundRetainsOldH() {
        caseGroups++;
        Marker marker=new Marker(); Frame f=frame(PcbLayerRoutingPrototype.MAX_EXPANSIONS,marker);
        int threshold=PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS-
            PcbLayerRoutingPrototype.WORK_QUANTUM;
        counters(f,threshold,threshold);
        f.session.advanceSlice(1);
        check(bool(f.branch,"relaxedBoundStarted"),"actual branch enters its late bound build");
        int cells=num(f.search,"cells"),calls=0,guard=4*cells+128;
        while(!bool(f.branch,"relaxedBoundComplete")) {
            if(++calls>guard) throw new AssertionError("relaxed-bound fixture exceeded finite guard");
            f.session.advanceSlice(1);
        }
        Object bound=get(f.branch,"relaxedDistanceBound");
        check(bound!=null && num(f.branch,"relaxedBoundWorkUnits")>0,
            "real owner completes a nonempty relaxed-distance field");
        int cell=-1,layer=-1,stronger=-1;
        for(int s=0;s<cells*2 && cell<0;s++) {
            int value=boundValue(bound,s%cells,s/cells);
            if(value>0) { cell=s%cells; layer=s/cells; stronger=value; }
        }
        check(cell>=0 && stronger>0,"completed real field has an independently read positive distance");
        Object staleH=node(cells,cell,layer,4,0,37,0,1234);
        frontier(f.branch,new Object[]{staleH},new Object[]{staleH});
        counters(f,PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS,
            PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS);
        int refreshes=num(f.branch,"relaxedBoundRefreshes");
        f.session.advanceSlice(1);
        PcbLayerRoutingPrototype.BranchSnapshot selected=marker.events.get(0);
        PcbLayerRoutingPrototype.BranchLimitSelection tuple=marker.selections.get(0);
        check(marker.selected==1 && tuple.storedHeuristic==0 &&
            stronger>tuple.storedHeuristic,
            "cap selection preserves injected older h despite the completed real bound");
        check(refreshes==0 && num(f.branch,"relaxedBoundRefreshes")==refreshes &&
            selected.relaxedBoundComplete && selected.relaxedBoundRefreshes==0,
            "branch cap precedes bound refresh without altering the stored tuple");
    }

    private static void callbackFailureAndReuse(boolean error) {
        caseGroups++;
        Throwable sentinel=error?new AssertionError("selected Error sentinel"):
            new IllegalStateException("selected RuntimeException sentinel");
        Marker failing=new Marker(); failing.fail=sentinel;
        Frame f=frame(PcbLayerRoutingPrototype.MAX_EXPANSIONS,failing);
        Object n=node(num(f.search,"cells"),6,num(f.search,"primary"),0,0,13,4,19);
        frontier(f.branch,new Object[]{n},new Object[]{n});
        counters(f,PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS,
            PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS);
        String before=f.fixture.layout.geometryFingerprint();
        check(advanceFailure(f.session)==sentinel && failing.selected==1 && failing.calls==0,
            "RuntimeException/Error callback propagates identical object once");
        check(get(f.session,"search")==null && get(f.session,"candidate")==null &&
            get(f.session,"result")==null && !bool(f.session,"complete") &&
            get(f.session,"diagnosticFailure")==sentinel,
            "selected callback failure clears and latches existing Session state");
        check(advanceFailure(f.session)==sentinel && resultFailure(f.session)==sentinel &&
            failing.selected==1,"retry and result rethrow the original object without redispatch");
        check(before.equals(f.fixture.layout.geometryFingerprint()) &&
            f.fixture.layout.getTraces().isEmpty() && f.fixture.layout.getHoles().isEmpty(),
            "failed callback leaves source geometry untouched");

        Run expected=run(fixture(),null,PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER);
        Run fresh=run(f.fixture,null,PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER);
        check(expected.result.accepted() && fresh.result.accepted(),
            "fresh request after callback failure completes actual small route");
        sameResult(expected.result,fresh.result);
    }

    private static void detachedMarker() {
        caseGroups++;
        Marker caller=new Marker(); Frame source=frame(PcbLayerRoutingPrototype.MAX_EXPANSIONS,caller);
        SeededPcbLayoutGenerator.AttemptObserver observer=new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };
        detachedCopies++;
        PcbLayerRoutingPrototype.DetachedBranchCopyTask copy=
            PcbLayerRoutingPrototype.beginDetachedBranchCopy(source.session,observer);
        PcbLayerRoutingPrototype.DetachedBranchReplay replay=null;
        try {
            int steps=0;
            while(!copy.isComplete()) {
                if(++steps>SETUP_GUARD) throw new AssertionError("detached copy exceeded finite guard");
                copy.advance(PcbLayerRoutingPrototype.WORK_QUANTUM);
            }
            replay=copy.result();
            Object ds=replay.getSearchForDiagnostics(), db=replay.getBranchForDiagnostics();
            Object owner=get(ds,"owner"), diagnostics=get(owner,"diagnostics");
            check(diagnostics instanceof PcbLayerRoutingPrototype.BranchLimitSelectionDiagnostics &&
                diagnostics==staticMarker() && diagnostics!=caller,
                "detached copy uses the stable static selected-mode no-op marker");
            check(get(copy,"source")==null && get(copy,"observer")==null &&
                ds!=source.search && db!=source.branch,
                "detached copy releases borrowed references and has separate search state");
            Object n=node(num(ds,"cells"),7,num(ds,"primary"),0,0,2,1,23);
            frontier(db,new Object[]{n},new Object[]{n});
            setCounters(owner,ds,db,PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS,
                PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS);
            RuntimeException boundary=null;
            try { replay.advance(1); } catch(RuntimeException caught) { boundary=caught; }
            check(boundary!=null && boundary.getMessage().endsWith(":BRANCH_SEARCH_LIMIT") &&
                bool(db,"terminalEmitted"),"detached no-op mode reaches actual selected cap dispatch");
            check(caller.selected==0 && caller.calls==0 && get(owner,"diagnosticFailure")==null,
                "detached callback neither reaches caller nor latches failure");
        } finally {
            if(replay!=null) replay.release();
            copy.cancel();
        }
        check(caller.selected==0 && caller.calls==0,"release preserves caller isolation");
    }

    private static Frame frame(int maximum,PcbLayerRoutingPrototype.Diagnostics sink) {
        P06FactoryLinkFixtures.Fixture fixture=fixture();
        PcbLayerRoutingPrototype.Policy policy=PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER;
        sessions++;
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(fixture.board,
            fixture.layout,policy,P06FactoryLinkFixtures.OBSERVER,maximum,policy.vias,sink);
        Object search=null,branch=null;
        for(int i=0;i<SETUP_GUARD && branch==null;i++) {
            session.advanceSlice(1); search=get(session,"search");
            branch=search==null?null:get(search,"branch");
        }
        check(search!=null && branch!=null && num(branch,"expanded")==0 &&
            num(get(branch,"queue"),"size")==2,"bounded setup reaches untouched real P06 branch");
        return new Frame(fixture,session,search,branch);
    }

    private static Run run(P06FactoryLinkFixtures.Fixture fixture,PcbLayerRoutingPrototype.Diagnostics sink,int maximum,
            PcbLayerRoutingPrototype.Policy policy) {
        String before=fixture.layout.geometryFingerprint();
        sessions++;
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(fixture.board,
            fixture.layout,policy,P06FactoryLinkFixtures.OBSERVER,maximum,policy.vias,sink);
        int slices=0;
        while(true) {
            if(session.advanceSlice(PcbLayerRoutingPrototype.WORK_QUANTUM)) break;
            if(++slices>DRAIN_GUARD) throw new AssertionError("actual Session exceeded finite drain guard");
        }
        PcbLayerRoutingPrototype.Result result=session.result();
        check(result!=null && result.expansions<=maximum && result.orderings<=
            PcbLayerRoutingPrototype.ORDERINGS,"actual Session returns bounded result");
        check(before.equals(fixture.layout.geometryFingerprint()) &&
            fixture.layout.getTraces().isEmpty() && fixture.layout.getHoles().isEmpty(),
            "actual Session does not mutate caller placement");
        return new Run(fixture,result,before);
    }

    private static P06FactoryLinkFixtures.Fixture fixture() {
        fixtures++;
        return new P06FactoryLinkFixtures.Fixture(true,0,0,true);
    }

    private static void counters(Frame f,int local,int total) {
        setCounters(f.session,f.search,f.branch,local,total);
    }

    private static void setCounters(Object session,Object search,Object branch,int local,int total) {
        check(get(session,"budget")==get(search,"budget"),"Session/Search share one budget");
        setInt(branch,"expanded",local);
        int primary=num(search,"primary");
        setInt(branch,"expandedTop",primary==PcbCopperLayer.TOP.ordinal()?local:0);
        setInt(branch,"expandedBottom",primary==PcbCopperLayer.BOTTOM.ordinal()?local:0);
        setInt(get(session,"budget"),"expanded",total);
    }

    private static void frontier(Object branch,Object[] queued,Object[] indexed) {
        Object q=get(branch,"queue"),ix=get(branch,"best");
        Arrays.fill((Object[])get(q,"values"),null); setInt(q,"size",0);
        Arrays.fill((int[])get(ix,"keys"),0); Arrays.fill((Object[])get(ix,"values"),null);
        setInt(ix,"size",0);
        Class<?> nt=nodeType();
        for(Object n:queued) invoke(q,"add",new Class<?>[]{nt},n);
        for(Object n:indexed) invoke(ix,"put",new Class<?>[]{Integer.TYPE,nt},
            Integer.valueOf(num(n,"key")),n);
    }

    private static Object node(int cells,int cell,int layer,int direction,int turns,
            int cost,int h,int serial) {
        try {
            Class<?> t=nodeType(); Constructor<?> c=t.getDeclaredConstructor(Integer.TYPE,
                Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,
                Integer.TYPE,t); c.setAccessible(true);
            return c.newInstance(cell,layer,direction,turns,cost,h,serial,cells,null);
        } catch(Exception e) { throw new AssertionError("cannot inject owner Node",e); }
    }

    private static Class<?> nodeType() {
        try { return Class.forName(PcbLayerRoutingPrototype.class.getName()+"$Node"); }
        catch(Exception e) { throw new AssertionError("owner Node type missing",e); }
    }

    private static Object construct(String nested) {
        try {
            Class<?> t=Class.forName(PcbLayerRoutingPrototype.class.getName()+"$"+nested);
            Constructor<?> c=t.getDeclaredConstructor(); c.setAccessible(true); return c.newInstance();
        } catch(Exception e) { throw new AssertionError("cannot create "+nested,e); }
    }

    private static Object best(Object branch,int key) {
        return invoke(get(branch,"best"),"get",new Class<?>[]{Integer.TYPE},key);
    }

    private static Object[] queue(Object q) {
        return Arrays.copyOf((Object[])get(q,"values"),num(q,"size"));
    }

    private static Object[] indexValues(Object ix) {
        ArrayList<Object> all=new ArrayList<Object>();
        for(Object n:(Object[])get(ix,"values")) if(n!=null) all.add(n);
        return all.toArray(new Object[all.size()]);
    }

    private static int boundValue(Object bound,int cell,int layer) {
        return ((Integer)invoke(bound,"value",new Class<?>[]{Integer.TYPE,Integer.TYPE},
            cell,layer)).intValue();
    }

    private static Object staticMarker() {
        Object found=null;
        try {
            for(Field f:PcbLayerRoutingPrototype.class.getDeclaredFields())
                if(Modifier.isStatic(f.getModifiers()) &&
                        PcbLayerRoutingPrototype.BranchLimitSelectionDiagnostics.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true); Object value=f.get(null);
                    if(value!=null) { check(found==null || found==value,"one stable detached marker"); found=value; }
                }
        } catch(Exception e) { throw new AssertionError("cannot read detached marker",e); }
        check(found!=null,"static selected-mode detached marker exists");
        return found;
    }

    private static void verifySelectionDto(PcbLayerRoutingPrototype.BranchLimitSelection value) {
        check(Modifier.isStatic(PcbLayerRoutingPrototype.BranchLimitSelection.class.getModifiers()),
            "selection DTO has no outer reference");
        TreeSet<String> actual=new TreeSet<String>();
        for(Field f:PcbLayerRoutingPrototype.BranchLimitSelection.class.getDeclaredFields()) {
            if(Modifier.isStatic(f.getModifiers())) continue;
            actual.add(f.getName());
            check(Modifier.isFinal(f.getModifiers()) && f.getType()==Integer.TYPE,
                "selection retains only final primitive ints");
        }
        TreeSet<String> expected=new TreeSet<String>(Arrays.asList(
            "branchOrdinal,cell,layer,direction,transitions,cost,storedHeuristic,serial,key,storedPriority".split(",")));
        check(actual.equals(expected) && value!=null,"selection DTO is exactly ten immutable int fields");
    }

    private static void verifySelection(PcbLayerRoutingPrototype.BranchLimitSelection s,Frame f,Object n) {
        int cell=num(n,"cell"),layer=num(n,"layer"),direction=num(n,"direction"),
            turns=num(n,"transitions"),cost=num(n,"cost"),h=num(n,"heuristic"),serial=num(n,"serial");
        int key=(((turns*2+layer)*5+direction)*num(f.search,"cells"))+cell;
        check(s.branchOrdinal==num(f.branch,"branchOrdinal") && s.cell==cell && s.layer==layer &&
            s.direction==direction && s.transitions==turns && s.cost==cost &&
            s.storedHeuristic==h && s.serial==serial && s.key==key && s.key==num(n,"key") &&
            s.storedPriority==cost+h,"all ten copied scalars equal the selected current Node");
    }

    private static void verifySnapshotContext(PcbLayerRoutingPrototype.BranchSnapshot s,Frame f,int expanded) {
        PcbPadPlacement start=(PcbPadPlacement)get(f.branch,"start");
        PcbPadPlacement root=(PcbPadPlacement)get(f.branch,"root");
        int cell=((Integer)invoke(f.search,"cell",new Class<?>[]{PcbPadPlacement.class},start)).intValue();
        int[] distance=(int[])get(f.branch,"distance");
        check(s.pass==num(f.search,"pass") && s.netId.equals(get(f.branch,"net")) &&
            s.startPadId.equals(start.getPadId()) && s.rootPadId.equals(root.getPadId()) &&
            s.startX==start.getX() && s.startY==start.getY() &&
            s.rootX==root.getX() && s.rootY==root.getY(),
            "snapshot keeps actual branch/pass/endpoints context");
        check(s.gridWidth==num(f.search,"width") && s.gridHeight==num(f.search,"height") &&
            s.goalsTop==num(f.branch,"goalsTop") && s.goalsBottom==num(f.branch,"goalsBottom") &&
            s.prefixViaCount==((Object[])get(f.branch,"holes")).length &&
            s.reachedPadCount==((Vector<?>)get(f.search,"reachedPadIds")).size() &&
            s.initialManhattanDistance==distance[cell],
            "snapshot keeps actual grid/goals/prefix/reached/distance context");
        check(s.branchExpanded==expanded && s.expandedTop+s.expandedBottom==expanded &&
            s.totalExpanded==num(get(f.session,"budget"),"expanded") &&
            s.frontierCount==num(get(f.branch,"queue"),"size") &&
            s.bestStateCount==num(get(f.branch,"best"),"size") &&
            "BRANCH_SEARCH_LIMIT".equals(s.terminalReason),
            "snapshot counters and post-pop state match injected owner frame");
        TreeSet<String> actual=new TreeSet<String>();
        for(Field field:PcbLayerRoutingPrototype.BranchSnapshot.class.getDeclaredFields())
            if(!Modifier.isStatic(field.getModifiers())) {
                actual.add(field.getName());
                check(Modifier.isFinal(field.getModifiers()) &&
                    (field.getType()==Integer.TYPE || field.getType()==Boolean.TYPE ||
                        field.getType()==String.class),"old snapshot remains immutable scalar-only");
            }
        TreeSet<String> schema=new TreeSet<String>(Arrays.asList(
            "pass,netId,startPadId,rootPadId,startX,startY,rootX,rootY,gridWidth,gridHeight,goalsTop,goalsBottom,prefixViaCount,reachedPadCount,initialManhattanDistance,minimumExploredManhattanDistance,branchExpanded,totalExpanded,expandedTop,expandedBottom,frontierCount,bestStateCount,terminalReason,relaxedBoundStarted,relaxedBoundComplete,relaxedBoundObservationChecked,relaxedBoundLiveLayoutStable,relaxedBoundRouterStateStable,relaxedBoundRouterEffectsStable,relaxedBoundWorkUnits,relaxedBoundOrdinaryProbes,relaxedBoundViaProbes,relaxedBoundRefreshes".split(",")));
        check(actual.equals(schema),"BranchSnapshot declared schema remains unchanged");
    }

    private static void sameEvents(ArrayList<PcbLayerRoutingPrototype.BranchSnapshot> a,
            ArrayList<PcbLayerRoutingPrototype.BranchSnapshot> b) {
        check(a.size()==b.size(),"old/new route terminal event counts match");
        for(int i=0;i<a.size();i++) sameSnapshot(a.get(i),b.get(i),"route event "+i);
    }

    private static void sameSnapshot(PcbLayerRoutingPrototype.BranchSnapshot a,
            PcbLayerRoutingPrototype.BranchSnapshot b,String label) {
        try {
            for(Field f:PcbLayerRoutingPrototype.BranchSnapshot.class.getDeclaredFields()) {
                if(Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true); Object x=f.get(a),y=f.get(b);
                check(x==null?y==null:x.equals(y),label+" field "+f.getName());
            }
        } catch(Exception e) { throw new AssertionError("cannot compare "+label,e); }
    }

    private static void sameResult(PcbLayerRoutingPrototype.Result a,PcbLayerRoutingPrototype.Result b) {
        check(a.accepted()==b.accepted() && a.outcome.equals(b.outcome) &&
            a.expansions==b.expansions && a.orderings==b.orderings &&
            a.vias==b.vias && a.links==b.links,"route status and counters match");
        if(a.accepted()) check(a.layout.geometryFingerprint().equals(b.layout.geometryFingerprint()) &&
            a.layout.traceGeometryFingerprint().equals(b.layout.traceGeometryFingerprint()),
            "accepted route geometry matches exactly");
    }

    private static Throwable advanceFailure(PcbLayerRoutingPrototype.Session s) {
        try { s.advanceSlice(1); } catch(RuntimeException e) { return e; } catch(Error e) { return e; }
        throw new AssertionError("callback failure was not propagated");
    }
    private static Throwable resultFailure(PcbLayerRoutingPrototype.Session s) {
        try { s.result(); } catch(RuntimeException e) { return e; } catch(Error e) { return e; }
        throw new AssertionError("aborted session returned a result");
    }

    private static Object invoke(Object o,String name,Class<?>[] types,Object... args) {
        try {
            Method m=o.getClass().getDeclaredMethod(name,types); m.setAccessible(true); return m.invoke(o,args);
        } catch(InvocationTargetException e) {
            Throwable cause=e.getCause();
            if(cause instanceof RuntimeException) throw (RuntimeException)cause;
            if(cause instanceof Error) throw (Error)cause;
            throw new AssertionError("checked owner-helper failure",cause);
        } catch(Exception e) { throw new AssertionError("cannot invoke owner helper "+name,e); }
    }
    private static Object get(Object o,String name) {
        try { Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
        catch(Exception e) { throw new AssertionError("cannot inspect owner field "+name,e); }
    }
    private static int num(Object o,String name) {
        Object value=get(o,name); if(value instanceof Integer) return ((Integer)value).intValue();
        throw new AssertionError("owner field not int: "+name);
    }
    private static boolean bool(Object o,String name) {
        Object value=get(o,name); if(value instanceof Boolean) return ((Boolean)value).booleanValue();
        throw new AssertionError("owner field not boolean: "+name);
    }
    private static void setInt(Object o,String name,int value) {
        try { Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); f.setInt(o,value); }
        catch(Exception e) { throw new AssertionError("cannot inject boundary field "+name,e); }
    }
    private static void sameIdentity(Object[] actual,Object[] expected,String label) {
        check(actual.length==expected.length,label+" size");
        for(Object x:actual) check(contains(expected,x),label+" has no unexpected identity");
        for(Object x:expected) check(contains(actual,x),label+" keeps expected identity");
    }
    private static boolean contains(Object[] a,Object x) { for(Object v:a) if(v==x) return true; return false; }
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }

    private static class Sink implements PcbLayerRoutingPrototype.TerminalOnlyDiagnostics {
        final ArrayList<PcbLayerRoutingPrototype.BranchSnapshot> events=new ArrayList<PcbLayerRoutingPrototype.BranchSnapshot>();
        int calls;
        public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot s) { calls++; events.add(s); }
    }
    private static final class Marker extends Sink implements PcbLayerRoutingPrototype.BranchLimitSelectionDiagnostics {
        int selected; Throwable fail; Watch watch;
        final ArrayList<PcbLayerRoutingPrototype.BranchLimitSelection> selections=
            new ArrayList<PcbLayerRoutingPrototype.BranchLimitSelection>();
        public void onBranchLimitSelected(PcbLayerRoutingPrototype.BranchSnapshot s,
                PcbLayerRoutingPrototype.BranchLimitSelection selection) {
            selected++;
            if(watch!=null) watch.check(s,selection);
            if(fail instanceof RuntimeException) throw (RuntimeException)fail;
            if(fail instanceof Error) throw (Error)fail;
            selections.add(selection);
            events.add(s);
        }
    }
    private static final class Run {
        final P06FactoryLinkFixtures.Fixture fixture;
        final PcbLayerRoutingPrototype.Result result;
        final String before;
        Run(P06FactoryLinkFixtures.Fixture f,PcbLayerRoutingPrototype.Result r,String b) {
            fixture=f; result=r; before=b;
        }
    }
    private static final class Frame {
        final P06FactoryLinkFixtures.Fixture fixture;
        final PcbLayerRoutingPrototype.Session session;
        final Object search,branch;
        Frame(P06FactoryLinkFixtures.Fixture f,PcbLayerRoutingPrototype.Session s,Object q,Object b) {
            fixture=f; session=s; search=q; branch=b;
        }
    }
    private static final class Watch {
        final Frame f; final Object[] queued; final Image index; final Object selected; final int expanded;
        Watch(Frame f,Object[] q,Image ix,Object n,int x) {
            this.f=f; queued=q; index=ix; selected=n; expanded=x;
        }
        void check(PcbLayerRoutingPrototype.BranchSnapshot snapshot,
                PcbLayerRoutingPrototype.BranchLimitSelection selection) {
            BranchLimitSelectionCanary.check(get(f.session,"search")==f.search && get(f.search,"branch")==f.branch &&
                bool(f.branch,"terminalEmitted"),"callback runs inside live owner terminal seam");
            sameIdentity(queue(get(f.branch,"queue")),queued,"post-pop raw queue");
            index.same(get(f.branch,"best"),"raw NodeIndex");
            BranchLimitSelectionCanary.check(best(f.branch,num(selected,"key"))==selected &&
                num(f.branch,"expanded")==expanded &&
                num(get(f.session,"budget"),"expanded")==expanded,
                "no extra state insertion, index mutation or expansion at callback");
            verifySnapshotContext(snapshot,f,expanded);
            verifySelection(selection,f,selected);
        }
    }
    private static final class Image {
        final int[] keys; final Object[] values; final int size;
        Image(Object ix) {
            keys=((int[])get(ix,"keys")).clone(); values=((Object[])get(ix,"values")).clone();
            size=num(ix,"size");
        }
        void same(Object ix,String label) {
            int[] k=(int[])get(ix,"keys"); Object[] v=(Object[])get(ix,"values");
            check(size==num(ix,"size") && keys.length==k.length && values.length==v.length,label+" shape");
            for(int i=0;i<keys.length;i++) check(keys[i]==k[i] && values[i]==v[i],label+" slot "+i);
        }
    }
}
