package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Focused source-bound contracts for accepted P07 branch capture. */
public final class P07AcceptedBranchCaptureContractTest {
    private static int assertions;
    private static final int ROUTE_SLICE_LIMIT=2000000;

    private P07AcceptedBranchCaptureContractTest() { }

    public static void main(String[] args) throws Exception {
        if(args!=null && args.length!=0) throw new IllegalArgumentException("Expected no arguments");
        fourFlipLiteralOracle();
        actualP06PublisherCapture();
        rejectNonemptySessionInput();
        System.out.println("PASS: P07 accepted branch capture contracts assertions="+assertions);
    }

    private static void fourFlipLiteralOracle() throws Exception {
        // [parent, cell, layer, direction, transitions, key], on a 10x10 grid.
        final int[][] rows={
            {-1,11,0,4,0,411}, {0,12,0,1,0,112}, {1,13,0,1,0,113},
            {2,13,1,4,1,1913}, {3,23,1,2,1,1723}, {4,33,1,2,1,1733},
            {5,33,0,4,2,2433}, {6,34,0,1,2,2134}, {7,35,0,1,2,2135},
            {8,35,1,4,3,3935}, {9,45,1,2,3,3745}, {10,55,1,2,3,3755},
            {11,55,0,4,4,4455}, {12,56,0,1,4,4156}
        };
        LiteralMetrics metrics=validateLiteral(rows,10,10);
        require(rows.length==14 && metrics.transitions==4,"literal path has fourteen nodes and four flips");
        require(metrics.gridSteps==9 && metrics.length==90 && metrics.bends==0,
            "literal path has nine planar steps and resets direction at every transition");
        Method keyMethod=P07AcceptedBranchCaptureDiagnostics.class.getDeclaredMethod(
            "nodeKey",Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE);
        keyMethod.setAccessible(true);
        Method directionMethod=P07AcceptedBranchCaptureDiagnostics.class.getDeclaredMethod(
            "directionBetween",Integer.TYPE,Integer.TYPE,Integer.TYPE);
        directionMethod.setAccessible(true);
        for(int i=0;i<rows.length;i++) {
            int expected=(((rows[i][4]*2+rows[i][2])*5+rows[i][3])*100)+rows[i][1];
            require(expected==rows[i][5],"literal source key matches normative formula at node "+i);
            require(((Integer)keyMethod.invoke(null,rows[i][1],rows[i][2],rows[i][3],rows[i][4],100))
                .intValue()==expected,"helper key agrees with independent literal formula at node "+i);
            if(i>0 && rows[i][2]==rows[i-1][2]) {
                require(((Integer)directionMethod.invoke(null,rows[i-1][1],rows[i][1],10)).intValue()==rows[i][3],
                    "helper direction agrees with literal cardinal edge at node "+i);
            }
        }
        final int[][] wrongParent=copy(rows); wrongParent[5][0]=3;
        final int[][] wrongKey=copy(rows); wrongKey[6][5]++;
        final int[][] wrongDirection=copy(rows); wrongDirection[1][3]=2; wrongDirection[1][5]=212;
        final int[][] outsideGrid=copy(rows); outsideGrid[13][1]=100;
        final int[][] diagonal=copy(rows); diagonal[1][1]=22; diagonal[1][5]=122;
        expectFailure(new Runnable(){public void run(){validateLiteral(wrongParent,10,10);}},
            IllegalArgumentException.class,"literal path rejects a nonadjacent parent index");
        expectFailure(new Runnable(){public void run(){validateLiteral(wrongKey,10,10);}},
            IllegalArgumentException.class,"literal path rejects a wrong Node key");
        expectFailure(new Runnable(){public void run(){validateLiteral(wrongDirection,10,10);}},
            IllegalArgumentException.class,"literal path rejects a direction mismatch");
        expectFailure(new Runnable(){public void run(){validateLiteral(outsideGrid,10,10);}},
            IllegalArgumentException.class,"literal path rejects an out-of-grid cell");
        expectFailure(new Runnable(){public void run(){validateLiteral(diagonal,10,10);}},
            IllegalArgumentException.class,"literal path rejects a diagonal step");
    }

    private static LiteralMetrics validateLiteral(int[][] rows,int width,int height) {
        if(rows==null || rows.length<1 || width<1 || height<1)
            throw new IllegalArgumentException("invalid literal grid");
        LiteralMetrics result=new LiteralMetrics();
        int priorDirection=4;
        for(int i=0;i<rows.length;i++) {
            int[] row=rows[i];
            if(row==null || row.length!=6 || row[1]<0 || row[1]>=width*height ||
                    row[2]<0 || row[2]>1 || row[3]<0 || row[3]>4 || row[4]<0 || row[4]>4 ||
                    row[0]!=(i==0?-1:i-1))
                throw new IllegalArgumentException("malformed literal Node row");
            int key=(((row[4]*2+row[2])*5+row[3])*width*height)+row[1];
            if(key!=row[5]) throw new IllegalArgumentException("literal Node key mismatch");
            if(i==0) {
                if(row[3]!=4 || row[4]!=0) throw new IllegalArgumentException("bad literal seed");
                continue;
            }
            int[] before=rows[i-1];
            if(row[2]!=before[2]) {
                if(row[1]!=before[1] || row[4]!=before[4]+1 || row[3]!=4)
                    throw new IllegalArgumentException("malformed literal face transition");
                result.transitions++; priorDirection=4;
            } else {
                int dx=row[1]%width-before[1]%width;
                int dy=row[1]/width-before[1]/width;
                int direction=dx==0&&dy==-1?0:dx==1&&dy==0?1:dx==0&&dy==1?2:dx==-1&&dy==0?3:-1;
                if(Math.abs(dx)+Math.abs(dy)!=1 || direction!=row[3] || row[4]!=before[4])
                    throw new IllegalArgumentException("malformed literal planar step");
                result.gridSteps++; result.length+=10;
                if(priorDirection!=4 && priorDirection!=direction) result.bends++;
                priorDirection=direction;
            }
        }
        return result;
    }

    private static int[][] copy(int[][] rows) {
        int[][] copy=new int[rows.length][];
        for(int i=0;i<rows.length;i++) copy[i]=rows[i].clone();
        return copy;
    }

    private static void actualP06PublisherCapture() throws Exception {
        final Fixture fixture=new Fixture();
        final String inputGeometry=fixture.layout.geometryFingerprint();
        final String inputGraph=fixture.layout.captureConductorGraph(fixture.board).toCanonical();
        require(fixture.layout.getHoles().isEmpty() && fixture.layout.getTraces().isEmpty(),
            "real P06 source begins from an empty input layout");
        final PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(
            fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS,2);
        Capture creator=null,consumer=null;
        Map<String,PcbBoardHole> creatorHoles=new HashMap<String,PcbBoardHole>();
        Map<String,String> creatorFingerprints=new HashMap<String,String>();
        int slices=0;
        boolean complete=false;
        while(!complete) {
            Object search=field(session,"search");
            if(search!=null) {
                Object branch=field(search,"branch");
                if(branch!=null) {
                    int pass=intField(search,"pass"),ordinal=intField(branch,"branchOrdinal");
                    String net=stringField(search,"currentNet");
                    PcbPadPlacement start=(PcbPadPlacement)field(branch,"start");
                    if(pass==0 && ordinal==3) {
                        require("UNDER".equals(net) && "B.1".equals(start.getPadId()),
                            "actual pass-zero creator is the UNDER B branch");
                        creator=captureBranch(session,search,branch,3,0);
                        PcbBoardLayout routed=(PcbBoardLayout)field(search,"layout");
                        for(int i=0;i<creator.post.createdHoleCount();i++) {
                            String id=creator.post.createdHoleIdAt(i);
                            PcbBoardHole found=findHole(routed,id);
                            require(found.kind==PcbBoardHole.Kind.VIA && "UNDER".equals(found.netId) &&
                                id.startsWith("p07-via/UNDER/3/"),
                                "creator output is an actual branch-three UNDER VIA");
                            creatorHoles.put(id,found); creatorFingerprints.put(id,found.fingerprint());
                        }
                        require(creatorHoles.size()==2,"normal creator publishes two actual vias");
                    } else if(pass==0 && ordinal==4) {
                        require("UNDER".equals(net) && "C.1".equals(start.getPadId()),
                            "actual pass-zero consumer is the UNDER C branch");
                        require(creator!=null && creatorHoles.size()==2,
                            "consumer follows its completed real-Publisher creator");
                        PcbBoardHole[] prefix=(PcbBoardHole[])field(branch,"holes");
                        require(prefix.length==2,"consumer BranchSearch owns both actual prior vias");
                        for(int i=0;i<prefix.length;i++)
                            require(creatorHoles.get(prefix[i].id)==prefix[i],
                                "consumer prefix retains creator VIA object identity");
                        consumer=captureBranch(session,search,branch,4,2);
                        require(consumer.pre.transitionCount()==0 && consumer.post.createdHoleCount()==0,
                            "later branch uses the same-net VIA tree without adding a transition or hole");
                        require(consumer.pre.prefixHoleCount()==2 && consumer.post.postHoleCount()==2,
                            "later branch preserves both prior hole rows");
                        for(int i=0;i<consumer.pre.prefixHoleCount();i++) {
                            String id=consumer.pre.prefixHoleIdAt(i);
                            require(creatorHoles.get(id)!=null && creatorHoles.get(id)==prefixHoleById(prefix,id),
                                "pre prefix row ID maps to the original actual creator VIA");
                            require(creatorFingerprints.get(id).equals(consumer.pre.prefixHoleFingerprintAt(i)),
                                "pre prefix row fingerprint matches the original creator VIA");
                        }
                        require(consumer.post.createdTraceCount()==1,
                            "normal Publisher adds the single later planar trace");
                        PcbBoardLayout liveLayout=(PcbBoardLayout)field(search,"layout");
                        PcbTraceGeometry joinedTrace=null; int joinedTraceIndex=-1;
                        java.util.Vector<PcbTraceGeometry> liveTraces=liveLayout.getTraces();
                        for(int i=0;i<liveTraces.size();i++) {
                            PcbTraceGeometry trace=liveTraces.get(i);
                            if("UNDER".equals(trace.getNetId()) && "C.1".equals(trace.getStartPadId())) {
                                joinedTrace=trace; joinedTraceIndex=i;
                            }
                        }
                        require(joinedTrace!=null && joinedTraceIndex>=0,
                            "consumer trace row maps to the actual normal-Publisher trace");
                        String createdTraceRows=section(consumer.post.toJson(),"createdTraceRows");
                        require(createdTraceRows.contains("\"index\":"+joinedTraceIndex+",\"sourceId\":\""+
                            joinedTrace.getSourceId()+"\""),
                            "created trace row preserves its global post-layout index");
                        require(consumer.post.toJson().contains("\"publisherStage\":6") &&
                            consumer.post.toJson().contains("\"reachedStartCount\":1"),
                            "post capture binds the completed Publisher and single reached-start addition");
                        String bindings=section(consumer.post.toJson(),"retainedHoleBindings");
                        for(String id:creatorHoles.keySet()) {
                            String fp=creatorFingerprints.get(id);
                            require(bindings.contains("\"id\":\""+id+"\",\"preFingerprint\":\""+fp+
                                "\",\"postFingerprint\":\""+fp+"\",\"preIndex\":"),
                                "consumer binding preserves VIA ID and source fingerprint");
                            require(bindings.contains("\"sameObject\":true"),
                                "consumer binding reports retained source object identity");
                        }
                        require(occurrences(bindings,"\"sameObject\":true")==creatorHoles.size(),
                            "both retained-hole bindings preserve actual source object identity");
                    }
                }
            }
            complete=session.advanceSlice(1);
            if(++slices>ROUTE_SLICE_LIMIT)
                throw new AssertionError("P07 source-bound microfixture exceeded its one-unit slice guard");
        }
        PcbLayerRoutingPrototype.Result result=session.result();
        require(result.accepted() && result.vias==2,"real FULLER Session accepts with its two-via cap");
        require(creator!=null && consumer!=null,"both real Publisher boundaries were captured");
        require(creator.pre.prefixHoleCount()==0 && creator.pre.transitionCount()==2 &&
            creator.post.createdHoleCount()==2 && creator.post.postHoleCount()==2,
            "creator capture separates its empty prefix from two naturally published vias");
        String transitionBindings=section(creator.post.toJson(),"transitionBindings");
        require(occurrences(transitionBindings,"\"pathNodeIndex\":")==creator.pre.transitionCount(),
            "creator emits one actual transition binding per face flip");
        require(occurrences(transitionBindings,"\"createdInBranch\":true")==creator.pre.transitionCount(),
            "creator transitions bind only their newly created vias");
        for(String id:creatorHoles.keySet()) {
            String fp=creatorFingerprints.get(id);
            require(transitionBindings.contains("\"viaId\":\""+id+"\",\"viaFingerprint\":\""+fp+"\""+
                ",\"creatorNetPosition\":"),
                "creator transition binding carries actual VIA ID and fingerprint");
            require(transitionBindings.contains("\"createdInBranch\":true"),
                "creator transition binding identifies a newly published VIA");
        }
        require(inputGeometry.equals(fixture.layout.geometryFingerprint()) &&
            inputGraph.equals(fixture.layout.captureConductorGraph(fixture.board).toCanonical()) &&
            fixture.layout.getHoles().isEmpty() && fixture.layout.getTraces().isEmpty(),
            "normal Session and capture preserve the caller's empty input placement");
    }

    private static Capture captureBranch(PcbLayerRoutingPrototype.Session session,Object search,
            Object branch,int ordinal,int expectedPrefix) throws Exception {
        final PcbLayerRoutingPrototype.Session actualSession=session;
        final Object actualSearch=search,actualBranch=branch;
        final P07AcceptedBranchCaptureDiagnostics.SeedSnapshot seeds=
            P07AcceptedBranchCaptureDiagnostics.captureInitialSeeds(search,branch);
        require(seeds.nodeCount()==2,"branch-entry snapshot captures two actual face seeds");
        Object queueValues=field(field(branch,"queue"),"values");
        require(seeds.containsNodeIdentity(Array.get(queueValues,0)) &&
            seeds.containsNodeIdentity(Array.get(queueValues,1)),
            "seed snapshot retains both exact queue Node identities");
        final Object publisher=awaitPrePublisher(actualSession,actualSearch,actualBranch);
        expectFailure(new Runnable(){public void run(){new P07AcceptedBranchCaptureDiagnostics.Context(
            new Object(),actualSearch,actualBranch,publisher,seeds);}},
            IllegalArgumentException.class,"capture rejects a foreign route-session owner");
        expectFailure(new Runnable(){public void run(){new P07AcceptedBranchCaptureDiagnostics.Context(
            actualSession,actualSearch,new Object(),publisher,seeds);}},
            IllegalArgumentException.class,"capture rejects a foreign BranchSearch identity");
        expectFailure(new Runnable(){public void run(){new P07AcceptedBranchCaptureDiagnostics.Context(
            actualSession,actualSearch,actualBranch,new Object(),seeds);}},
            IllegalArgumentException.class,"capture rejects a foreign Publisher identity");
        final P07AcceptedBranchCaptureDiagnostics.Context context=
            new P07AcceptedBranchCaptureDiagnostics.Context(session,search,branch,publisher,seeds);
        final P07AcceptedBranchCaptureDiagnostics.PreCursor preCursor=
            P07AcceptedBranchCaptureDiagnostics.beginPre(context);
        expectFailure(new Runnable(){public void run(){preCursor.result();}},
            IllegalStateException.class,"incomplete pre cursor cannot expose a result");
        expectFailure(new Runnable(){public void run(){preCursor.advance(0);}},
            IllegalArgumentException.class,"pre cursor rejects zero work");
        expectFailure(new Runnable(){public void run(){preCursor.advance(32769);}},
            IllegalArgumentException.class,"pre cursor rejects work above the quantum cap");
        require(!preCursor.advance(1) && preCursor.lastWork()>0 && preCursor.lastWork()<=1,
            "pre cursor makes bounded progress under a one-unit advance");
        int preCalls=finishPreCursor(preCursor,1,ordinal==3);
        require(preCursor.lastWork()>0 && preCursor.lastWork()<=32768,
            "pre cursor preserves the completing call work debit");
        require(preCursor.advance(1) && preCursor.lastWork()==0,
            "already-complete pre cursor reports zero work on a later call");
        final P07AcceptedBranchCaptureDiagnostics.PreResult pre=preCursor.result();
        require(pre.netId().equals(stringField(search,"currentNet")) &&
            pre.branchOrdinal()==intField(branch,"branchOrdinal"),"pre result preserves live branch metadata");
        require(pre.prefixHoleCount()==expectedPrefix,"pre result uses expected actual prefix inventory");
        String preJson=pre.toJson();
        int goalGridCells=intField(search,"cells");
        boolean[] actualGoalMask=(boolean[])field(branch,"goal");
        require(actualGoalMask.length==goalGridCells*2,
            "independent goal count reads the exact two-face BranchSearch mask");
        int actualGoalCellCount=0;
        for(int goalCell=0;goalCell<goalGridCells;goalCell++)
            if(actualGoalMask[goalCell] || actualGoalMask[goalGridCells+goalCell]) actualGoalCellCount++;
        String goalJson=section(preJson,"goal");
        require(goalJson.contains("\"cellCount\":"+actualGoalCellCount+","),
            "goal.cellCount equals unique nonzero cells in the actual BranchSearch goal mask");
        int netIndex=intField(search,"netIndex");
        require(preJson.contains("\"netIndexNextFetch\":"+netIndex+",\"netPosition\":"+(netIndex-1)),
            "pre JSON reports the actual next-fetch index and current net position");
        require(preJson.contains("\"policy\":\"FULLER_TWO_LAYER\",\"templateHoleCount\":0") &&
            preJson.contains("\"startPadAttachment\":\"PLATED_THROUGH_HOLE\"") &&
            preJson.contains("\"maximumTransitions\":4"),
            "pre JSON records actual FULLER policy, empty template, plated start and transition cap");
        require(preJson.contains("\"faceMask\":3") && preJson.contains("\"startCopperFaceMask\":3") &&
            preJson.contains("\"rootCopperFaceMask\":3"),
            "source seed mask and physical pad copper access are recorded independently");
        verifySourcePath(search,branch,publisher,seeds,pre);

        Object layout=field(search,"layout");
        int endCell=intField(field(publisher,"end"),"cell");
        int width=intField(search,"width"),cells=intField(search,"cells");
        int endX=intField(search,"minX")+(endCell%width)*PcbLayerRoutingPrototype.GRID;
        int endY=intField(search,"minY")+(endCell/width)*PcbLayerRoutingPrototype.GRID;
        if(ordinal==4) {
            PcbBoardHole match=null;
            int matches=0;
            for(PcbBoardHole hole:((PcbBoardLayout)layout).getHoles())
                if("UNDER".equals(hole.netId) && hole.x==endX && hole.y==endY) { match=hole; matches++; }
            boolean[] goal=(boolean[])field(branch,"goal");
            require(matches==1 && goal[endCell] && goal[cells+endCell],
                "consumer endpoint is an actual existing same-net via with both source goal-face bits");
            require(intField(field(publisher,"end"),"layer")==PcbCopperLayer.TOP.ordinal() &&
                intField(field(publisher,"end"),"transitions")==0,
                "consumer Publisher reaches the existing barrel on TOP without a new transition");
            require(match!=null && match==findHole((PcbBoardLayout)layout,match.id),
                "consumer endpoint references the retained creator hole object");
        }

        awaitPostPublisher(actualSession,actualSearch,publisher);
        final P07AcceptedBranchCaptureDiagnostics.PostCursor postCursor=
            P07AcceptedBranchCaptureDiagnostics.beginPost(context,pre);
        expectFailure(new Runnable(){public void run(){postCursor.result();}},
            IllegalStateException.class,"incomplete post cursor cannot expose a result");
        expectFailure(new Runnable(){public void run(){postCursor.advance(0);}},
            IllegalArgumentException.class,"post cursor rejects zero work");
        require(!postCursor.advance(1) && postCursor.lastWork()>0 && postCursor.lastWork()<=1,
            "post cursor makes bounded progress under a one-unit advance");
        int postCalls=finishPostCursor(postCursor,1,preCalls,ordinal==3);
        require(postCursor.lastWork()>0 && postCursor.lastWork()<=32768,
            "post cursor preserves the completing call work debit");
        require(postCursor.advance(1) && postCursor.lastWork()==0,
            "already-complete post cursor reports zero work on a later call");
        P07AcceptedBranchCaptureDiagnostics.PostResult post=postCursor.result();
        require(post.postHoleCount()>=post.createdHoleCount(),"post inventory contains every created hole");
        require(section(pre.toJson(),"path").contains("\"tupleSha256\":\""+pre.pathTupleSha256()+"\""),
            "pre JSON serializes the source-bound path tuple digest");
        require(section(post.toJson(),"claims").contains("\"avoidability\":\"UNKNOWN\""),
            "post capture retains the frozen non-avoidability claims");
        return new Capture(pre,post);
    }

    private static Object awaitPrePublisher(PcbLayerRoutingPrototype.Session session,Object search,
            Object branch) {
        for(int step=0;step<ROUTE_SLICE_LIMIT;step++) {
            if(field(session,"search")!=search) throw new AssertionError("Session replaced target Search");
            Object publisher=field(search,"publisher");
            if(publisher!=null && field(search,"branch")==null && field(publisher,"start")==field(branch,"start") &&
                    intField(publisher,"stage")==1 &&
                    intField(publisher,"qualityIndex")==((java.util.Vector<?>)field(publisher,"reversed")).size())
                return publisher;
            if(session.advanceSlice(1)) throw new AssertionError("Session completed before target Publisher boundary");
        }
        throw new AssertionError("Target Publisher did not reach its quiescent stage-one boundary");
    }

    private static void awaitPostPublisher(PcbLayerRoutingPrototype.Session session,Object search,Object publisher) {
        for(int step=0;step<ROUTE_SLICE_LIMIT;step++) {
            if(field(session,"search")!=search) throw new AssertionError("Session replaced target Search before post capture");
            if(field(search,"branch")==null && field(search,"publisher")==null && intField(publisher,"stage")==6)
                return;
            if(session.advanceSlice(1)) throw new AssertionError("Session completed before target publication boundary");
        }
        throw new AssertionError("Target Publisher did not complete normal publication");
    }

    private static int finishPreCursor(P07AcceptedBranchCaptureDiagnostics.PreCursor cursor,
            int calls,boolean requireQuantumOneFinalization) {
        boolean exercised=false;
        while(!cursor.isComplete()) {
            int stage=intField(cursor,"stage");
            int quantum=requireQuantumOneFinalization?256:32768;
            if(stage==8 && requireQuantumOneFinalization && intField(cursor,"serializedUtf16Units")>0) {
                long target=(long)intField(cursor,"serializedUtf16Units")*2L;
                long remaining=target-longField(cursor,"materializationCredits");
                require(remaining>=0,"pre materialization credits remain within their target");
                if(remaining<=32768) {
                    if(remaining>0) {
                        cursor.advance((int)remaining); calls++;
                        require(!cursor.isComplete() && cursor.lastWork()==remaining,
                            "pre cursor can stop exactly after its final materialization credit");
                    }
                    cursor.advance(1); calls++;
                    require(cursor.isComplete() && cursor.lastWork()==1,
                        "pre cursor completes with one charged finalization unit");
                    if(calls>640) throw new AssertionError("pre capture exceeded its advance-call cap");
                    exercised=true;
                    break;
                }
            }
            cursor.advance(quantum); calls++;
            require(cursor.lastWork()<=quantum,"pre cursor respects each quantum");
            if(!cursor.isComplete()) require(cursor.lastWork()>0,"incomplete pre cursor progresses");
            if(calls>640) throw new AssertionError("pre capture exceeded its advance-call cap");
        }
        if(requireQuantumOneFinalization)
            require(exercised,"pre fixture exercises one-unit finalization at a credit boundary");
        return calls;
    }

    private static int finishPostCursor(P07AcceptedBranchCaptureDiagnostics.PostCursor cursor,
            int calls,int preCalls,boolean requireQuantumOneFinalization) {
        boolean exercised=false;
        while(!cursor.isComplete()) {
            int stage=intField(cursor,"stage");
            int quantum=requireQuantumOneFinalization?256:32768;
            if(stage==7 && requireQuantumOneFinalization && intField(cursor,"serializedUtf16Units")>0) {
                long target=(long)intField(cursor,"serializedUtf16Units")*2L;
                long remaining=target-longField(cursor,"materializationCredits");
                require(remaining>=0,"post materialization credits remain within their target");
                if(remaining<=32768) {
                    if(remaining>0) {
                        cursor.advance((int)remaining); calls++;
                        require(!cursor.isComplete() && cursor.lastWork()==remaining,
                            "post cursor can stop exactly after its final materialization credit");
                    }
                    cursor.advance(1); calls++;
                    require(cursor.isComplete() && cursor.lastWork()==1,
                        "post cursor completes with one charged finalization unit");
                    if(preCalls+calls>640) throw new AssertionError("combined capture exceeded its advance-call cap");
                    exercised=true;
                    break;
                }
            }
            cursor.advance(quantum); calls++;
            require(cursor.lastWork()<=quantum,"post cursor respects each quantum");
            if(!cursor.isComplete()) require(cursor.lastWork()>0,"incomplete post cursor progresses");
            if(preCalls+calls>640) throw new AssertionError("combined capture exceeded its advance-call cap");
        }
        if(requireQuantumOneFinalization)
            require(exercised,"post fixture exercises one-unit finalization at a credit boundary");
        return calls;
    }

    private static void verifySourcePath(Object search,Object branch,Object publisher,
            P07AcceptedBranchCaptureDiagnostics.SeedSnapshot seeds,
            P07AcceptedBranchCaptureDiagnostics.PreResult pre) throws Exception {
        Object end=field(publisher,"end");
        List<Object> reverse=new ArrayList<Object>();
        Object node=end;
        while(node!=null) {
            if(reverse.size()>=4096) throw new AssertionError("source path exceeds the frozen node cap");
            reverse.add(node); node=field(node,"previous");
        }
        Collections.reverse(reverse);
        int width=intField(search,"width"),height=intField(search,"height"),cells=intField(search,"cells");
        int minX=intField(search,"minX"),minY=intField(search,"minY");
        int length=0,bends=0,transitions=0,priorDirection=4;
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        PcbPadPlacement startPad=(PcbPadPlacement)field(branch,"start");
        int endCell=intField(end,"cell");
        int endX=minX+(endCell%width)*PcbLayerRoutingPrototype.GRID;
        int endY=minY+(endCell/width)*PcbLayerRoutingPrototype.GRID;
        int directDistance=Math.abs(startPad.getX()-endX)+Math.abs(startPad.getY()-endY);
        for(int i=0;i<reverse.size();i++) {
            Object current=reverse.get(i);
            int cell=intField(current,"cell"),layer=intField(current,"layer"),direction=intField(current,"direction");
            int trans=intField(current,"transitions"),cost=intField(current,"cost"),heuristic=intField(current,"heuristic");
            int serial=intField(current,"serial"),key=intField(current,"key");
            require(cell>=0 && cell<cells && cell%width<width && cell/width<height,
                "source Node stays inside actual route grid");
            int expectedKey=(((trans*2+layer)*5+direction)*cells)+cell;
            require(key==expectedKey,"source Node key follows independent tuple formula");
            int parent=i==0?-1:i-1;
            if(i==0) require(seeds.containsNodeIdentity(current) && field(current,"previous")==null,
                "actual ordered path starts at a retained branch seed Node");
            else require(field(current,"previous")==reverse.get(i-1),"actual previous-Node identity is contiguous");
            int x=minX+(cell%width)*PcbLayerRoutingPrototype.GRID;
            int y=minY+(cell/width)*PcbLayerRoutingPrototype.GRID;
            if(i==0) require(x==startPad.getX() && y==startPad.getY(),
                "actual start seed occupies the exact Search.cell mapping of its physical pad");
            String tuple=i+","+parent+","+cell+","+x+","+y+","+layer+","+direction+","+
                trans+","+cost+","+heuristic+","+serial+","+key;
            if(i>0) digest.update((byte)'\n');
            digest.update(tuple.getBytes("UTF-8"));
            if(i>0) {
                Object previous=reverse.get(i-1);
                int prevCell=intField(previous,"cell"),prevLayer=intField(previous,"layer");
                int prevTrans=intField(previous,"transitions");
                if(layer!=prevLayer) {
                    require(cell==prevCell && trans==prevTrans+1 && direction==4,
                        "source layer flip preserves cell and resets direction");
                    transitions++; priorDirection=4;
                } else {
                    int dx=cell%width-prevCell%width,dy=cell/width-prevCell/width;
                    int actualDirection=dx==0&&dy==-1?0:dx==1&&dy==0?1:dx==0&&dy==1?2:dx==-1&&dy==0?3:-1;
                    require(Math.abs(dx)+Math.abs(dy)==1 && direction==actualDirection && trans==prevTrans,
                        "source planar edge is one cardinal grid step");
                    length+=PcbLayerRoutingPrototype.GRID;
                    if(priorDirection!=4 && priorDirection!=direction) bends++;
                    priorDirection=direction;
                }
            }
        }
        require(pre.pathNodeCount()==reverse.size() && pre.transitionCount()==transitions,
            "pre result node and transition counts match actual Node chain");
        require(length==intField(publisher,"length") && bends==intField(publisher,"bends"),
            "independent path metrics match native Publisher quality values");
        require(directDistance>0 && pre.toJson().contains("\"directDistance\":"+directDistance),
            "pre direct distance is measured from the actual branch start pad");
        require(pre.pathTupleSha256().equals(hex(digest.digest())),
            "pre tuple digest matches independent source Node chain encoding");
    }

    private static void rejectNonemptySessionInput() {
        final Fixture fixture=new Fixture();
        fixture.layout.addHole(PcbTwoLayerRules.via("p07-via/UNDER/1/0","UNDER",300,300));
        final String before=fixture.layout.geometryFingerprint();
        expectFailure(new Runnable(){public void run(){PcbLayerRoutingPrototype.begin(
            fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS,2);}},
            IllegalArgumentException.class,"real Session rejects nonempty input holes");
        require(before.equals(fixture.layout.geometryFingerprint()) && fixture.layout.getHoleCount()==1,
            "rejected Session input remains unchanged");
    }

    private static final class Fixture {
        final TroubleshootBoard board=new TroubleshootBoard("P06_RAISED_CROSSOVER");
        final PcbBoardLayout layout=new PcbBoardLayout(900,650,
            new Rectangle(100,100,420,400),new Rectangle(650,120,200,400));
        Fixture() {
            board.addNet(new BoardNet("OVER")); board.addNet(new BoardNet("UNDER")); board.addNet(new BoardNet("GND"));
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
            layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:L","L",new Rectangle(120,235,28,18),12,true,null));
            layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:R","R",new Rectangle(475,235,28,18),12,true,null));
            layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:A","A",new Rectangle(333,145,28,18),12,true,null));
            layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:B","B",new Rectangle(333,440,28,18),12,true,null));
            layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:G","G",new Rectangle(200,440,28,18),12,true,null));
            layout.validateAgainst(board);
            require(layout.getHoles().isEmpty() && layout.getTraces().isEmpty(),"P06 fixture is undrilled and unrouted");
        }
        private void addPoint(String id,String net,int x,int y,PcbRotation rotation) {
            PhysicalPackage physical=P06FactoryLinkFixtures.TEST_POINT;
            BoardComponent component=new BoardComponent(id,physical.getId(),physical,id);
            board.addComponent(component); board.addPad(new BoardPad(id+".1",id,"1",net));
            PcbPackagePose reference=new PcbPackagePose(100,100,rotation,PcbBoardSide.BOTTOM);
            Point local=physical.getGeometry().placedAt(reference).getPadPoint(0);
            PcbPackagePose pose=new PcbPackagePose(100+x-local.x,100+y-local.y,rotation,PcbBoardSide.BOTTOM);
            PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,pose,physical.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
        }
    }

    private static final class Capture {
        final P07AcceptedBranchCaptureDiagnostics.PreResult pre;
        final P07AcceptedBranchCaptureDiagnostics.PostResult post;
        Capture(P07AcceptedBranchCaptureDiagnostics.PreResult pre,
                P07AcceptedBranchCaptureDiagnostics.PostResult post) { this.pre=pre; this.post=post; }
    }
    private static final class LiteralMetrics { int gridSteps,length,bends,transitions; }

    private static PcbBoardHole findHole(PcbBoardLayout layout,String id) {
        for(PcbBoardHole hole:layout.getHoles()) if(id.equals(hole.id)) return hole;
        throw new AssertionError("missing actual VIA: "+id);
    }
    private static PcbBoardHole prefixHoleById(PcbBoardHole[] holes,String id) {
        for(PcbBoardHole hole:holes) if(id.equals(hole.id)) return hole;
        return null;
    }
    private static int occurrences(String text,String token) {
        int count=0,at=0;
        while((at=text.indexOf(token,at))>=0) { count++; at+=token.length(); }
        return count;
    }
    private static String section(String json,String key) {
        String token="\""+key+"\":";
        int start=json.indexOf(token);
        if(start<0) throw new AssertionError("missing JSON section "+key);
        int value=start+token.length();
        char opening=json.charAt(value);
        if(opening!='[' && opening!='{') throw new AssertionError("JSON section is not a container: "+key);
        char closing=opening=='['?']':'}';
        int depth=0; boolean quoted=false,escaped=false;
        for(int i=value;i<json.length();i++) {
            char ch=json.charAt(i);
            if(quoted) {
                if(escaped) escaped=false;
                else if(ch=='\\') escaped=true;
                else if(ch=='\"') quoted=false;
            } else if(ch=='\"') quoted=true;
            else if(ch==opening) depth++;
            else if(ch==closing && --depth==0) return json.substring(value,i+1);
        }
        throw new AssertionError("unterminated JSON section "+key);
    }
    private static String stringField(Object target,String name) { return (String)field(target,name); }
    private static int intField(Object target,String name) { return ((Integer)field(target,name)).intValue(); }
    private static long longField(Object target,String name) { return ((Long)field(target,name)).longValue(); }
    private static Object field(Object target,String name) {
        if(target==null) throw new AssertionError("missing source while reading "+name);
        Class<?> type=target.getClass();
        while(type!=null) {
            try {
                Field value=type.getDeclaredField(name); value.setAccessible(true); return value.get(target);
            } catch(NoSuchFieldException missing) { type=type.getSuperclass(); }
            catch(Exception failure) { throw new AssertionError("cannot inspect read-only P07 source field "+name,failure); }
        }
        throw new AssertionError("missing read-only P07 source field "+name);
    }
    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder(bytes.length*2);
        final char[] alphabet="0123456789abcdef".toCharArray();
        for(byte value:bytes) { int b=value&255; out.append(alphabet[b>>>4]).append(alphabet[b&15]); }
        return out.toString();
    }
    private static void expectFailure(Runnable action,Class<? extends Throwable> expected,String label) {
        assertions++;
        try { action.run(); }
        catch(Throwable failure) {
            if(expected.isInstance(failure)) return;
            throw new AssertionError(label+" (wrong exception "+failure.getClass().getName()+")",failure);
        }
        throw new AssertionError(label+" (accepted invalid input)");
    }
    private static void require(boolean condition,String label) {
        assertions++;
        if(!condition) throw new AssertionError(label);
    }
}
