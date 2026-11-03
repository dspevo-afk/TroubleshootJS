package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Vector;

/** Literal prerequisite proving a detached owner can replay one real P07 branch. */
public final class P07DetachedBranchReplayContractTest {
    private static final int SOURCE_VIA_CAP=2;
    private static final int MAX_SOURCE_UNITS=2000000;
    private static final int MAX_REPLAY_UNITS=50000;
    private static final int MAX_LITERAL_GRID_CELLS=10000;
    private static final int MAX_ROUTER_OBSERVATION_CALLS=100;
    private static int assertions;

    private P07DetachedBranchReplayContractTest() { }

    public static void main(String[] args) throws Exception {
        if(args!=null && args.length!=0)
            throw new IllegalArgumentException("Expected no arguments");
        long started=System.nanoTime();
        runContracts();
        long caseElapsedMs=(System.nanoTime()-started)/1000000L;
        System.out.println("PASS: P07 detached branch replay contracts assertions="+assertions+
            " caseElapsedMs="+caseElapsedMs);
    }

    static void runContracts() throws Exception {
        LiteralFixture fixture=literalSourceFixture();
        require(fixture.layout.getHoles().isEmpty() && fixture.layout.getTraces().isEmpty(),
            "literal source begins with a virgin copper layout");
        require(fixture.layout.getBoardOutline().width<=600 &&
                fixture.layout.getBoardOutline().height<=500 &&
                ((fixture.layout.getBoardOutline().width/PcbLayerRoutingPrototype.GRID)+1L)*
                    ((fixture.layout.getBoardOutline().height/PcbLayerRoutingPrototype.GRID)+1L)<=
                    MAX_LITERAL_GRID_CELLS,
            "literal fixture is bounded before Search or branch arrays are allocated");

        PcbLayerRoutingPrototype.Session sourceSession=PcbLayerRoutingPrototype.begin(
            fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS,SOURCE_VIA_CAP);
        Source source=captureConsumerEntry(fixture,sourceSession);
        require(source.prefixIds.length==SOURCE_VIA_CAP && source.maximumVias==SOURCE_VIA_CAP,
            "literal consumer begins with its two-VIA prefix at the original saturated cap");

        Replay baseline=runDetached(source,source.maximumVias,source.budgetMaximum);
        verifySourceUnchanged(source,"baseline replay");
        // This P06 source already runs at its two-hole cap; the second owner is an
        // independent saturated-cap reproduction, not a lower-cap comparison.
        Replay saturated=runDetached(source,source.prefixIds.length,source.budgetMaximum);
        verifySourceUnchanged(source,"saturated-cap replay");
        require(saturated.maximumVias==source.prefixIds.length,
            "independent saturated replay uses the actual prefix-hole count as its cap");
        verifyDetachedResults(baseline,saturated);
        Replay entryControl=buildDetached(source,source.maximumVias,source.budgetMaximum);
        verifyNegativeControls(source,entryControl);
        verifySourceUnchanged(source,"negative controls");
        verifyExhaustedBudget(source);
        verifySourceUnchanged(source,"exhausted-budget control");

        Replay original=finishOriginalConsumer(source);
        verifySourcePublishedDelta(source,original);
        verifyDetachedResults(baseline,original);
        verifyBranchPhysicalAndConnectivity(baseline);
        verifyBranchPhysicalAndConnectivity(saturated);
        verifyBranchPhysicalAndConnectivity(original);
        require(fixture.layout.getHoles().isEmpty() && fixture.layout.getTraces().isEmpty(),
            "all routing remained inside the source Session candidate");
        assertions++;
    }

    private static LiteralFixture literalSourceFixture() throws Exception {
        Class<?> owner=Class.forName("com.lushprojects.circuitjs1.client.P07TwoLayerContractTest");
        Method factory=owner.getDeclaredMethod("sameSessionViaTreeJoinFixture");
        factory.setAccessible(true);
        Object fixture=invoke(factory,null);
        return new LiteralFixture((TroubleshootBoard)field(fixture,"board"),
            (PcbBoardLayout)field(fixture,"layout"));
    }

    private static Source captureConsumerEntry(LiteralFixture fixture,
            PcbLayerRoutingPrototype.Session session) throws Exception {
        int units=0;
        while(units<MAX_SOURCE_UNITS) {
            Object search=field(session,"search");
            if(search!=null) {
                Object branch=field(search,"branch");
                if(branch!=null && intField(search,"pass")==0 &&
                        intField(branch,"branchOrdinal")==4 &&
                        "UNDER".equals(stringField(search,"currentNet")) &&
                        "C.1".equals(((PcbPadPlacement)field(branch,"start")).getPadId())) {
                    return new Source(fixture,session,search,branch,units);
                }
            }
            if(session.advanceSlice(1))
                throw new AssertionError("literal source completed before its consumer branch");
            units++;
        }
        throw new AssertionError("literal source exceeded its one-unit capture guard");
    }

    private static Replay runDetached(Source source,int maximumVias,int budgetMaximum)
            throws Exception {
        Replay replay=buildDetached(source,maximumVias,budgetMaximum);
        completeReplay(replay);
        verifySourceUnchanged(source,"detached replay work");
        return replay;
    }

    private static Replay buildDetached(Source source,int maximumVias,int budgetMaximum)
            throws Exception {
        if(source==null || maximumVias<source.prefixIds.length ||
                maximumVias>PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias ||
                budgetMaximum<source.budgetExpanded || budgetMaximum<1)
            throw new IllegalArgumentException("Invalid bounded literal replay request");
        PcbBoardLayout detachedLayout=source.layout.copyForCompaction();
        PcbLayerRoutingPrototype.Session detachedOwner=PcbLayerRoutingPrototype.begin(
            source.fixture.board,source.fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,budgetMaximum,maximumVias);
        Object detachedBudget=field(detachedOwner,"budget");
        setField(detachedBudget,"expanded",Integer.valueOf(source.budgetExpanded));
        Object detachedSearch=newSearch(source,detachedOwner,detachedLayout,maximumVias);
        setField(detachedOwner,"candidate",detachedLayout);
        setField(detachedOwner,"search",detachedSearch);
        setField(detachedOwner,"pass",Integer.valueOf(source.pass));
        int freshFacesDifferent=restorePrefixRouterFaces(source,detachedSearch,detachedLayout);
        require(freshFacesDifferent>0,
            "source prefix carries Router state absent from a Search rebuilt only from layout geometry");
        verifyDetachedSearchEnvelope(source,detachedSearch,detachedLayout);
        verifyDetachedGoalInputs(source,detachedSearch,detachedLayout);
        Object detachedBranch=newBranch(source,detachedSearch,detachedLayout);
        setField(detachedSearch,"branch",detachedBranch);
        Replay replay=new Replay(source,detachedOwner,detachedSearch,detachedBranch,
            detachedLayout,maximumVias);
        verifyDetachedEntry(source,replay,detachedOwner,replay.branch,replay.start,
            detachedLayout.getPad(source.root.getPadId()),source.branchOrdinal,
            source.seedValues,source.goalValues,source.prefixIds,source.prefixFingerprints);
        if(maximumVias==source.prefixIds.length) {
            Object seed=Array.get(field(field(detachedBranch,"queue"),"values"),0);
            Method pathViaFits=detachedSearch.getClass().getDeclaredMethod(
                "pathViaFits",seed.getClass());
            pathViaFits.setAccessible(true);
            require(!((Boolean)invoke(pathViaFits,detachedSearch,seed)).booleanValue(),
                "actual saturated prefix cap disables another transition, including at an existing VIA");
        }
        return replay;
    }

    private static int restorePrefixRouterFaces(Source source,Object search,
            PcbBoardLayout detachedLayout) throws Exception {
        PcbNetRouter.Router[] faces=(PcbNetRouter.Router[])field(search,"faces");
        if(faces.length!=source.faceSnapshots.length || faces.length!=2)
            throw new IllegalArgumentException("Detached Search has incomplete Router faces");
        int freshDifferences=0;
        for(int layer=0;layer<faces.length;layer++) {
            PcbNetRouter.Router expected=source.faceSnapshots[layer].copyForFixedGraphObservation();
            setField(expected,"layout",detachedLayout);
            if(!sameRouterSnapshot(faces[layer],expected)) freshDifferences++;
            PcbNetRouter.Router restored=source.faceSnapshots[layer].copyForFixedGraphObservation();
            if(!sameRouterSnapshot(restored,source.faceSnapshots[layer]))
                throw new IllegalStateException("Detached prefix Router copy differs from retained source face "+layer);
            setField(restored,"layout",detachedLayout);
            requireRouterPadsBound(restored,detachedLayout);
            requireRouterArraysDetached(restored,source.faceSnapshots[layer],source.faceRefs[layer]);
            if(!sameRouterSnapshot(restored,expected))
                throw new IllegalStateException("Detached Router face did not restore exact prefix state "+layer);
            faces[layer]=restored;
            require(restored!=source.faceRefs[layer] && restored!=source.faceSnapshots[layer] &&
                    field(restored,"layout")==detachedLayout,
                "detached Router face owns copied source prefix state and the cloned layout");
        }
        return freshDifferences;
    }

    private static void requireRouterPadsBound(PcbNetRouter.Router router,PcbBoardLayout layout)
            throws Exception {
        PcbPadPlacement[] pads=(PcbPadPlacement[])field(router,"pads");
        PcbComponentPlacement[] components=(PcbComponentPlacement[])field(router,"components");
        for(PcbPadPlacement pad:pads) if(layout.getPad(pad.getPadId())!=pad)
            throw new IllegalStateException("Detached Router pad reference is outside its layout");
        for(PcbComponentPlacement component:components)
            if(layout.getComponent(component.getComponentId())!=component)
                throw new IllegalStateException("Detached Router component reference is outside its layout");
        assertions++;
    }

    private static void requireRouterArraysDetached(PcbNetRouter.Router actual,
            PcbNetRouter.Router snapshot,PcbNetRouter.Router live) throws Exception {
        String[] names={"occupiedNet","clearanceNet","pads","padNets","padWidths","padBounds",
            "escapeCorridors","escapeReservations","components","courtyards",
            "allComponentIndexes","componentCandidatesByColumn","collisionCourtyards",
            "horizontalReservation","verticalReservation","padAt"};
        for(String name:names) {
            Object value=field(actual,name);
            if(value!=null && (value==field(snapshot,name) || value==field(live,name)))
                throw new IllegalStateException("Detached Router reused mutable source array: "+name);
        }
        assertions++;
    }

    private static void verifyDetachedSearchEnvelope(Source source,Object search,
            PcbBoardLayout layout) throws Exception {
        String[] fields={"primary","minX","minY","width","height","cells","pass","maximumVias"};
        for(String name:fields)
            if(intField(search,name)!=intField(source.search,name))
                throw new IllegalArgumentException("Detached Search grid or primary field changed: "+name);
        require(field(search,"board")==source.fixture.board && field(search,"layout")==layout &&
                stringField(search,"currentNet").equals(source.currentNet) &&
                layout.geometryFingerprint().equals(source.layoutFingerprint),
            "detached Search retains the source board, net, grid and prefix geometry");
    }

    private static void verifyDetachedGoalInputs(Source source,Object search,
            PcbBoardLayout layout) throws Exception {
        Class<?> searchClass=search.getClass();
        PcbPadPlacement root=layout.getPad(source.root.getPadId());
        Method reachedMethod=searchClass.getDeclaredMethod("reachedPthTerminals",
            String.class,PcbPadPlacement.class);
        reachedMethod.setAccessible(true);
        PcbPadPlacement[] reached=(PcbPadPlacement[])invoke(reachedMethod,search,source.currentNet,root);
        PcbPadPlacement[] sourceReached=(PcbPadPlacement[])field(source.branch,"reachedTerminals");
        requirePadIds(sourceReached,reached);
        Method goalsMethod=searchClass.getDeclaredMethod("goals",String.class,
            PcbPadPlacement.class,PcbPadPlacement[].class);
        goalsMethod.setAccessible(true);
        boolean[] goals=(boolean[])invoke(goalsMethod,search,source.currentNet,root,reached);
        requireGoalValues(source.goalValues,goals);
        Method distanceMethod=searchClass.getDeclaredMethod("lowerBound",boolean[].class);
        distanceMethod.setAccessible(true);
        int[] distance=(int[])invoke(distanceMethod,search,goals);
        int[] sourceDistance=(int[])field(source.branch,"distance");
        if(!Arrays.equals(sourceDistance,distance))
            throw new IllegalArgumentException("Detached lower-bound distance differs from source goals");
        assertions++;
    }

    private static void requirePadIds(PcbPadPlacement[] expected,PcbPadPlacement[] actual) {
        if(expected==null || actual==null || expected.length!=actual.length)
            throw new IllegalArgumentException("Detached reached-terminal set differs from source");
        for(int i=0;i<expected.length;i++)
            if(!expected[i].getPadId().equals(actual[i].getPadId()))
                throw new IllegalArgumentException("Detached reached-terminal order differs from source");
        assertions++;
    }

    private static Object newSearch(Source source,PcbLayerRoutingPrototype.Session owner,
            PcbBoardLayout layout,int maximumVias) throws Exception {
        Object budget=field(owner,"budget");
        Class<?> budgetClass=Class.forName(PcbLayerRoutingPrototype.class.getName()+"$Budget");
        Constructor<?> constructor=source.search.getClass().getDeclaredConstructor(
            PcbLayerRoutingPrototype.Session.class,TroubleshootBoard.class,PcbBoardLayout.class,
            PcbLayerRoutingPrototype.Policy.class,SeededPcbLayoutGenerator.AttemptObserver.class,
            budgetClass,Integer.TYPE,String.class,Integer.TYPE);
        constructor.setAccessible(true);
        Object search=constructor.newInstance(owner,source.fixture.board,layout,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER,
            budget,Integer.valueOf(source.pass),field(source.search,"promotedNet"),
            Integer.valueOf(maximumVias));
        if(source.serial<source.seedValues.length)
            throw new IllegalArgumentException("Source serial precedes branch seed allocation");
        setField(search,"serial",Integer.valueOf(source.serial-source.seedValues.length));
        setField(search,"branches",Integer.valueOf(source.branches));
        setField(search,"segments",Integer.valueOf(source.segments));
        setField(search,"nets",new Vector<String>(source.netOrder));
        setField(search,"netIndex",Integer.valueOf(source.netIndex));
        setField(search,"currentNet",source.currentNet);
        setField(search,"currentPadIds",copyStrings(source.currentPadIds));
        setField(search,"reachedPadIds",copyStrings(source.reachedPadIds));
        setField(search,"currentRoot",layout.getPad(source.root.getPadId()));
        require(layout.getPad(source.start.getPadId())!=null &&
                layout.getPad(source.root.getPadId())!=null,
            "detached layout retains source branch endpoints");
        require(field(owner,"template") instanceof PcbBoardLayout &&
                ((PcbBoardLayout)field(owner,"template")).getHoleCount()==0,
            "detached Session keeps a virgin, empty-hole template");
        return search;
    }

    private static Object newBranch(Source source,Object search,PcbBoardLayout layout)
            throws Exception {
        Class<?> branchClass=source.branch.getClass();
        Constructor<?> constructor=branchClass.getDeclaredConstructor(search.getClass(),
            String.class,PcbPadPlacement.class,PcbPadPlacement.class);
        constructor.setAccessible(true);
        return constructor.newInstance(search,source.currentNet,
            layout.getPad(source.start.getPadId()),layout.getPad(source.root.getPadId()));
    }

    private static void completeReplay(Replay replay) throws Exception {
        Method advance=replay.search.getClass().getDeclaredMethod("advanceOne");
        advance.setAccessible(true);
        int units=0;
        while(field(replay.search,"branch")!=null || field(replay.search,"publisher")!=null) {
            Object publisher=field(replay.search,"publisher");
            if(publisher!=null && replay.publisher==null) replay.publisher=publisher;
            invoke(advance,replay.search);
            publisher=field(replay.search,"publisher");
            if(publisher!=null && replay.publisher==null) replay.publisher=publisher;
            if(++units>MAX_REPLAY_UNITS)
                throw new AssertionError("detached literal replay exceeded its one-step guard");
        }
        require(replay.publisher!=null && field(replay.search,"branch")==null &&
                field(replay.search,"publisher")==null,
            "normal Search completed the detached branch Publisher");
        require(field(replay.branch,"found")!=null,
            "detached branch found an actual Search goal node before publication");
        replay.pathRows=pathRows(field(replay.publisher,"end"));
        require(replay.pathRows.length>=2 && replay.pathRows[0][3]==0 &&
                replay.pathRows[replay.pathRows.length-1][3]==0,
            "detached literal path has a nontrivial zero-transition route");
        replay.traceDelta=traceSignatures(replay.layout,replay.source.prefixTraceCount);
        replay.postHoleIds=holeIds(replay.layout);
        replay.postHoleFingerprints=holeFingerprints(replay.layout);
        replay.postGeometry=replay.layout.geometryFingerprint();
        replay.postGraph=replay.layout.captureConductorGraph(replay.source.fixture.board).toCanonical();
        bindFoundGoalToPrefixVia(replay);
        require(intField(replay.publisher,"stage")==6,
            "normal detached Publisher reached its terminal stage");
        require(!booleanField(replay.publisher,"transition"),
            "same-net prefix branch publishes no layer transition");
        require(intField(replay.publisher,"segments")>0,
            "normal Publisher emitted planar branch segments");
        require(replay.postHoleIds.length==replay.source.prefixIds.length &&
                replay.traceDelta.length==1,
            "normal Publisher adds one trace and no new via");
    }

    private static Replay finishOriginalConsumer(Source source) throws Exception {
        Replay replay=new Replay(source,source.session,source.search,source.branch,
            source.layout,source.maximumVias);
        int units=0;
        while(units<MAX_REPLAY_UNITS) {
            Object publisher=field(source.search,"publisher");
            if(publisher!=null && replay.publisher==null) replay.publisher=publisher;
            if(replay.publisher!=null && publisher==null && field(source.search,"branch")==null)
                break;
            if(source.session.advanceSlice(1))
                throw new AssertionError("source Session completed before consumer handoff");
            publisher=field(source.search,"publisher");
            if(publisher!=null && replay.publisher==null) replay.publisher=publisher;
            if(++units>MAX_REPLAY_UNITS)
                throw new AssertionError("source consumer exceeded its one-unit handoff guard");
        }
        require(replay.publisher!=null && field(source.search,"publisher")==null &&
                field(source.search,"branch")==null,
            "original Session completed its normal consumer Publisher");
        replay.pathRows=pathRows(field(replay.publisher,"end"));
        replay.traceDelta=traceSignatures(replay.layout,source.prefixTraceCount);
        replay.postHoleIds=holeIds(replay.layout);
        replay.postHoleFingerprints=holeFingerprints(replay.layout);
        replay.postGeometry=replay.layout.geometryFingerprint();
        replay.postGraph=replay.layout.captureConductorGraph(source.fixture.board).toCanonical();
        bindFoundGoalToPrefixVia(replay);
        require(intField(replay.publisher,"stage")==6,
            "original Publisher reached its terminal stage");
        require(!booleanField(replay.publisher,"transition") && replay.traceDelta.length==1 &&
                replay.postHoleIds.length==source.prefixIds.length,
            "original consumer publication adds one planar trace and no VIA");
        return replay;
    }

    private static void verifyDetachedEntry(Source source,Replay replay,Object expectedOwner,
            Object expectedBranch,PcbPadPlacement expectedStart,PcbPadPlacement expectedRoot,
            int expectedOrdinal,int[][] expectedSeeds,
            boolean[] expectedGoals,String[] expectedHoleIds,String[] expectedFingerprints)
            throws Exception {
        if(expectedOwner!=replay.owner || field(replay.search,"owner")!=expectedOwner ||
                field(expectedOwner,"search")!=replay.search)
            throw new IllegalArgumentException("Detached route Session owner changed");
        if(field(replay.search,"branch")!=expectedBranch || expectedBranch!=replay.branch)
            throw new IllegalArgumentException("Detached Search branch identity changed");
        if(field(replay.search,"publisher")!=null)
            throw new IllegalArgumentException("Detached Search branch handoff changed");
        if(field(replay.branch,"start")!=expectedStart)
            throw new IllegalArgumentException("Detached branch start authority changed");
        if(field(replay.branch,"root")!=expectedRoot || replay.root!=expectedRoot ||
                field(replay.search,"currentRoot")!=expectedRoot)
            throw new IllegalArgumentException("Detached branch root authority changed");
        if(!source.currentNet.equals(stringField(replay.branch,"net")))
            throw new IllegalArgumentException("Detached branch net authority changed");
        if(intField(replay.branch,"branchOrdinal")!=expectedOrdinal)
            throw new IllegalArgumentException("Detached branch ordinal changed");
        Object template=field(expectedOwner,"template");
        if(field(expectedOwner,"candidate")!=replay.layout ||
                !(template instanceof PcbBoardLayout) || template==replay.layout ||
                ((PcbBoardLayout)template).getHoleCount()!=0)
            throw new IllegalArgumentException("Detached layout owner authority changed");
        require(replay.owner!=source.owner && replay.search!=source.search &&
                replay.branch!=source.branch && replay.layout!=source.layout,
            "detached replay owns independent Session, Search, BranchSearch and layout containers");
        verifyIndependentResources(source,replay);
        PcbBoardHole[] actualHoles=(PcbBoardHole[])field(replay.branch,"holes");
        verifyPrefix(expectedHoleIds,expectedFingerprints,actualHoles);
        requireGoalValues(expectedGoals,(boolean[])field(replay.branch,"goal"));
        requireSeedValues(expectedSeeds,seedValues(replay.branch));
        require(intField(replay.search,"serial")==source.serial &&
                intField(replay.search,"branches")==source.branches &&
                intField(replay.search,"pass")==source.pass &&
                intField(replay.search,"netIndex")==source.netIndex &&
                stringField(replay.search,"currentNet").equals(source.currentNet) &&
                vectorEquals((Vector<?>)field(replay.search,"nets"),source.netOrder) &&
                vectorEquals((Vector<?>)field(replay.search,"currentPadIds"),source.currentPadIds) &&
                vectorEquals((Vector<?>)field(replay.search,"reachedPadIds"),source.reachedPadIds) &&
                ((PcbPadPlacement)field(replay.search,"currentRoot")).getPadId().equals(
                    source.root.getPadId()),
            "detached entry reconstructs source order, pad state, pass, branch and serial");
        require(intField(field(replay.search,"budget"),"expanded")==source.budgetExpanded &&
                intField(field(replay.search,"budget"),"maximum")==replay.budgetMaximum,
            "detached entry restores the consumed global expansion count and requested limit");
    }

    private static void verifyNegativeControls(final Source source,final Replay replay)
            throws Exception {
        verifyDetachedEntry(source,replay,replay.owner,replay.branch,replay.start,replay.root,
            source.branchOrdinal,
            source.seedValues,source.goalValues,source.prefixIds,source.prefixFingerprints);
        expectRejected(new Checked(){public void run() throws Exception {
            verifyDetachedEntry(source,replay,source.owner,replay.branch,replay.start,replay.root,
                source.branchOrdinal,
                source.seedValues,source.goalValues,source.prefixIds,source.prefixFingerprints);
        }},"Detached route Session owner changed","foreign owner is rejected");
        final PcbPadPlacement wrongStart=replay.layout.getPad("B.1");
        require(wrongStart!=null && wrongStart!=replay.start,"literal wrong-start control is distinct");
        expectRejected(new Checked(){public void run() throws Exception {
            verifyDetachedEntry(source,replay,replay.owner,replay.branch,wrongStart,replay.root,
                source.branchOrdinal,
                source.seedValues,source.goalValues,source.prefixIds,source.prefixFingerprints);
        }},"Detached branch start authority changed","altered start authority is rejected");
        expectRejected(new Checked(){public void run() throws Exception {
            verifyDetachedEntry(source,replay,replay.owner,replay.branch,replay.start,replay.root,
                source.branchOrdinal+1,
                source.seedValues,source.goalValues,source.prefixIds,source.prefixFingerprints);
        }},"Detached branch ordinal changed","altered branch ordinal is rejected");
        expectRejected(new Checked(){public void run() throws Exception {
            verifyDetachedEntry(source,replay,replay.owner,new Object(),replay.start,replay.root,
                source.branchOrdinal,
                source.seedValues,source.goalValues,source.prefixIds,source.prefixFingerprints);
        }},"Detached Search branch identity changed","foreign BranchSearch identity is rejected");
        final PcbPadPlacement wrongRoot=replay.root.getPadId().equals("A.1")?
            replay.layout.getPad("B.1"):replay.layout.getPad("A.1");
        require(wrongRoot!=null && wrongRoot!=replay.root,"literal wrong-root control is distinct");
        expectRejected(new Checked(){public void run() throws Exception {
            verifyDetachedEntry(source,replay,replay.owner,replay.branch,replay.start,wrongRoot,
                source.branchOrdinal,source.seedValues,source.goalValues,source.prefixIds,
                source.prefixFingerprints);
        }},"Detached branch root authority changed","altered root authority is rejected");
        expectRejected(new Checked(){public void run() throws Exception {
            setField(replay.search,"currentRoot",wrongRoot);
            try {
                verifyDetachedEntry(source,replay,replay.owner,replay.branch,replay.start,replay.root,
                    source.branchOrdinal,source.seedValues,source.goalValues,source.prefixIds,
                    source.prefixFingerprints);
            } finally { setField(replay.search,"currentRoot",replay.root); }
        }},"Detached branch root authority changed","altered Search currentRoot is rejected");
        final int[][] wrongSeeds=copy(source.seedValues);
        wrongSeeds[0][0]++;
        expectRejected(new Checked(){public void run() throws Exception {
            verifyDetachedEntry(source,replay,replay.owner,replay.branch,replay.start,replay.root,
                source.branchOrdinal,
                wrongSeeds,source.goalValues,source.prefixIds,source.prefixFingerprints);
        }},seedMismatchMessage(wrongSeeds,source.seedValues),"altered seed tuple is rejected");
        final boolean[] wrongGoals=source.goalValues.clone();
        wrongGoals[0]=!wrongGoals[0];
        expectRejected(new Checked(){public void run() throws Exception {
            verifyDetachedEntry(source,replay,replay.owner,replay.branch,replay.start,replay.root,
                source.branchOrdinal,
                source.seedValues,wrongGoals,source.prefixIds,source.prefixFingerprints);
        }},"Branch goal mask changed","altered goal mask is rejected");
        final String[] wrongPrefix=source.prefixIds.clone();
        wrongPrefix[0]=wrongPrefix[0]+"/changed";
        expectRejected(new Checked(){public void run() throws Exception {
            verifyDetachedEntry(source,replay,replay.owner,replay.branch,replay.start,replay.root,
                source.branchOrdinal,
                source.seedValues,source.goalValues,wrongPrefix,source.prefixFingerprints);
        }},"Prefix VIA authority changed","altered prefix identity is rejected");
    }

    private static void verifyExhaustedBudget(Source source) throws Exception {
        require(source.budgetExpanded>0,
            "literal consumer has prior global expansion work for exhausted-budget control");
        Replay replay=buildDetached(source,source.maximumVias,source.budgetExpanded);
        int before=intField(field(replay.search,"budget"),"expanded");
        Method advance=replay.search.getClass().getDeclaredMethod("advanceOne");
        advance.setAccessible(true);
        boolean rejected=false;
        for(int units=0;units<MAX_REPLAY_UNITS && !rejected;units++) {
            try { invoke(advance,replay.search); }
            catch(RuntimeException expected) {
                String exhaustedName=PcbLayerRoutingPrototype.class.getName()+"$Exhausted";
                if(!exhaustedName.equals(expected.getClass().getName()) ||
                        !(source.currentNet+":TOTAL_SEARCH_LIMIT").equals(expected.getMessage()))
                    throw new AssertionError("Unexpected exhausted-budget failure",expected);
                assertions++;
                rejected=true;
            }
        }
        require(rejected && field(replay.branch,"found")==null &&
                field(replay.search,"publisher")==null &&
                intField(field(replay.search,"budget"),"expanded")==before &&
                replay.layout.geometryFingerprint().equals(source.layoutFingerprint),
            "exhausted replay exposes no success and leaves detached geometry unchanged");
    }

    private static void verifyDetachedResults(Replay first,Replay second) throws Exception {
        require(Arrays.deepEquals(first.pathRows,second.pathRows),
            "detached baseline and comparison reproduce the same real Search path tuples");
        require(first.traceDelta.length==second.traceDelta.length &&
                Arrays.equals(first.traceDelta,second.traceDelta),
            "normal Publisher reproduces the same literal trace publication delta");
        require(Arrays.equals(first.postHoleIds,second.postHoleIds) &&
                Arrays.equals(first.postHoleFingerprints,second.postHoleFingerprints),
            "detached replay preserves every prefix VIA ID and fingerprint");
        require(stringField(first.publisher,"net").equals(stringField(second.publisher,"net")) &&
                ((PcbPadPlacement)field(first.publisher,"start")).getPadId().equals(
                    ((PcbPadPlacement)field(second.publisher,"start")).getPadId()) &&
                sameNullable(padId(field(first.publisher,"endTerminal")),
                    padId(field(second.publisher,"endTerminal"))),
            "detached Publisher retains literal net, start and nullable terminal identity");
        require(first.foundVia==second.foundVia &&
                first.foundViaId.equals(second.foundViaId) &&
                first.foundViaFingerprint.equals(second.foundViaFingerprint) &&
                first.foundX==second.foundX && first.foundY==second.foundY &&
                first.foundLayer==second.foundLayer &&
                first.foundTransitions==second.foundTransitions,
            "detached found nodes bind to the same actual same-net prefix VIA and face");
        require(first.postGraph.equals(second.postGraph),
            "detached replays produce the same literal physical graph after publication");
        require(first.postGeometry.equals(second.postGeometry),
            "detached replays produce the same literal physical layout fingerprint");
        require(intField(first.publisher,"segments")==intField(second.publisher,"segments") &&
                intField(first.publisher,"length")==intField(second.publisher,"length") &&
                intField(first.publisher,"bends")==intField(second.publisher,"bends"),
            "detached Publisher preserves the measured path and route-quality scalars");
    }

    private static void verifySourcePublishedDelta(Source source,Replay original) throws Exception {
        require(original.traceDelta.length==1 && original.postHoleIds.length==source.prefixIds.length,
            "original source consumer adds one trace without creating a VIA");
        require(Arrays.equals(source.prefixIds,original.postHoleIds) &&
                Arrays.equals(source.prefixFingerprints,original.postHoleFingerprints),
            "original source retains its prefix VIA identifiers and fingerprints");
        require(field(source.branch,"found")!=null,
            "original source branch found an actual Search goal node");
        require("C.1".equals(source.start.getPadId()) &&
                source.currentNet.equals(stringField(original.publisher,"net")),
            "original source output remains bound to the captured branch");
    }

    private static void verifyBranchPhysicalAndConnectivity(Replay replay) throws Exception {
        PcbConductorGraph.Snapshot before=replay.source.prefixLayout
            .captureConductorGraph(replay.source.fixture.board).pristine();
        require(!before.padsConnected(replay.source.start.getPadId(),replay.source.root.getPadId()),
            "captured consumer start is an unconnected terminal before this branch");
        PcbConductorGraph.Snapshot after=replay.layout
            .captureConductorGraph(replay.source.fixture.board).pristine();
        require(after.padsConnected(replay.source.start.getPadId(),replay.source.root.getPadId()),
            "the completed branch physically connects its start to the existing same-net tree");
        // This checks geometry and physical placement only; it does not assert that every net is complete.
        replay.layout.validateRoutingGeometry(replay.source.fixture.board);
        new PcbTwoLayerRules(replay.source.fixture.board,replay.layout).validate(replay.layout);
        Vector<PcbTraceGeometry> traces=replay.layout.getTraces();
        int first=replay.source.prefixTraceCount;
        require(first<traces.size(),"branch-only trace is present for physical checks");
        for(int i=first;i<traces.size();i++) {
            PcbTraceGeometry trace=traces.get(i);
            require(replay.source.currentNet.equals(trace.getNetId()) &&
                    trace.getWidth()==replay.source.fixture.board.getPlacementConstraints()
                        .getTraceWidthProfile().widthFor(
                            replay.source.fixture.board.getNet(replay.source.currentNet).getRoutingRole()),
                "published branch trace keeps its actual net and role width");
            require(replay.source.start.getPadId().equals(trace.getStartPadId()),
                "published branch trace is attached to the captured start pad");
            int[] xs=trace.getXPoints(),ys=trace.getYPoints();
            for(int p=1;p<xs.length;p++)
                require(xs[p]==xs[p-1] || ys[p]==ys[p-1],
                    "branch trace uses only orthogonal segments");
        }
    }

    private static void verifySourceUnchanged(Source source,String phase) throws Exception {
        require(field(source.session,"search")==source.search &&
                field(source.search,"owner")==source.owner &&
                field(source.search,"branch")==source.branch &&
                field(source.search,"publisher")==null &&
                field(source.session,"candidate")==source.layout,
            phase+" leaves the retained source owner and branch binding untouched");
        require(source.fixture.layout.getHoles().isEmpty() && source.fixture.layout.getTraces().isEmpty() &&
                ((PcbBoardLayout)field(source.session,"template")).getHoleCount()==0,
            phase+" leaves the original input and Session template empty");
        require(source.layout.geometryFingerprint().equals(source.layoutFingerprint) &&
                source.layout.captureConductorGraph(source.fixture.board).toCanonical().equals(source.graph),
            phase+" leaves source layout geometry and conductor graph unchanged");
        require(Arrays.equals(source.prefixIds,holeIds(source.layout)) &&
                Arrays.equals(source.prefixFingerprints,holeFingerprints(source.layout)) &&
                sameHoleReferences(source.holeRefs,source.layout.getHoles()),
            phase+" leaves every retained source VIA unchanged");
        verifyPrefix(source.prefixIds,source.prefixFingerprints,
            (PcbBoardHole[])field(source.branch,"holes"));
        requireSeedValues(source.seedValues,seedValues(source.branch));
        requireGoalValues(source.goalValues,(boolean[])field(source.branch,"goal"));
        require(Arrays.equals(source.traceSignatures,traceSignatures(source.layout,0)) &&
                intField(source.search,"serial")==source.serial &&
                intField(source.search,"branches")==source.branches &&
                intField(source.search,"netIndex")==source.netIndex &&
                intField(source.search,"pass")==source.pass &&
                intField(source.branch,"branchOrdinal")==source.branchOrdinal &&
                stringField(source.search,"currentNet").equals(source.currentNet) &&
                field(source.search,"nets")==source.netOrderRef &&
                field(source.search,"currentPadIds")==source.currentPadIdsRef &&
                field(source.search,"reachedPadIds")==source.reachedPadIdsRef &&
                vectorEquals((Vector<?>)field(source.search,"nets"),source.netOrder) &&
                vectorEquals((Vector<?>)field(source.search,"currentPadIds"),source.currentPadIds) &&
                vectorEquals((Vector<?>)field(source.search,"reachedPadIds"),source.reachedPadIds) &&
                intField(field(source.search,"budget"),"expanded")==source.budgetExpanded,
            phase+" leaves source route order, serial, geometry and consumed budget unchanged");
        Object sourceQueue=field(source.branch,"queue");
        Object sourceQueueValues=field(sourceQueue,"values");
        require(source.seeds.toJson().length()>0 && field(source.branch,"queue")==source.queueRef &&
                field(source.branch,"goal")==source.goalRef &&
                field(source.branch,"holes")==source.seeds.prefixHoleRefs,
            phase+" leaves the source seed, goal and prefix references retained");
        for(int i=0;i<source.seeds.nodeCount();i++)
            require(source.seeds.containsNodeIdentity(Array.get(sourceQueueValues,i)),
                phase+" leaves source face seed identity "+i+" retained");
        verifySourceRouters(source,phase);
    }

    private static void verifyIndependentResources(Source source,Replay replay) throws Exception {
        Object sourceBudget=field(source.search,"budget");
        Object replayBudget=field(replay.search,"budget");
        Object sourceFaces=field(source.search,"faces");
        Object replayFaces=field(replay.search,"faces");
        Object sourceWorkspace=field(source.owner,"turnBoundWorkspace");
        Object replayWorkspace=field(replay.owner,"turnBoundWorkspace");
        if(sourceBudget==replayBudget || sourceFaces==replayFaces || sourceWorkspace==null ||
                replayWorkspace==null || sourceWorkspace==replayWorkspace)
            throw new IllegalArgumentException("Detached replay shares mutable routing resources");
        PcbNetRouter.Router[] sourceFaceArray=(PcbNetRouter.Router[])sourceFaces;
        PcbNetRouter.Router[] replayFaceArray=(PcbNetRouter.Router[])replayFaces;
        if(sourceFaceArray.length!=2 || replayFaceArray.length!=2)
            throw new IllegalArgumentException("Detached replay has incomplete router faces");
        for(int i=0;i<2;i++) if(sourceFaceArray[i]==replayFaceArray[i])
            throw new IllegalArgumentException("Detached replay shares a Router face");
        for(int i=0;i<2;i++) if(field(replayFaceArray[i],"layout")!=replay.layout)
            throw new IllegalArgumentException("Detached Router face is not bound to its cloned layout");
        assertions++;
    }

    private static void verifySourceRouters(Source source,String phase) throws Exception {
        Object faceArray=field(source.search,"faces");
        if(faceArray!=source.faceArrayRef)
            throw new IllegalStateException(phase+" replaced the source Router face vector");
        PcbNetRouter.Router[] faces=(PcbNetRouter.Router[])faceArray;
        for(int i=0;i<faces.length;i++) {
            if(faces[i]!=source.faceRefs[i] ||
                    !source.faceEffects[i].equals(faces[i].fixedGraphObservationEffects()) ||
                    !sameRouterSnapshot(faces[i],source.faceSnapshots[i]))
                throw new IllegalStateException(phase+" changed source Router face "+i);
            assertions++;
        }
        if(field(source.owner,"turnBoundWorkspace")!=source.workspaceRef)
            throw new IllegalStateException(phase+" changed source turn-bound workspace identity");
        assertions++;
    }

    private static PcbNetRouter.Router snapshotRouter(PcbNetRouter.Router source) {
        PcbNetRouter.Router.FixedGraphCopyTask copy=source.beginFixedGraphObservationCopy();
        for(int calls=0;calls<MAX_ROUTER_OBSERVATION_CALLS;calls++)
            if(copy.advanceOne(32768)) return copy.result();
        throw new IllegalStateException("Bounded source Router copy did not complete");
    }

    private static boolean sameRouterSnapshot(PcbNetRouter.Router source,
            PcbNetRouter.Router snapshot) {
        PcbNetRouter.Router.FixedGraphComparisonTask comparison=
            source.beginFixedGraphComparison(snapshot);
        for(int calls=0;calls<MAX_ROUTER_OBSERVATION_CALLS;calls++)
            if(comparison.advanceOne(32768)) return comparison.equal();
        throw new IllegalStateException("Bounded source Router comparison did not complete");
    }

    private static void verifyPrefix(String[] expectedIds,String[] expectedFingerprints,
            PcbBoardHole[] actual) {
        if(expectedIds==null || expectedFingerprints==null || actual==null ||
                expectedIds.length!=expectedFingerprints.length || expectedIds.length!=actual.length)
            throw new IllegalArgumentException("Prefix inventory cardinality changed");
        for(int i=0;i<actual.length;i++)
            if(actual[i]==null || !expectedIds[i].equals(actual[i].id) ||
                    !expectedFingerprints[i].equals(actual[i].fingerprint()))
                throw new IllegalArgumentException("Prefix VIA authority changed");
        assertions++;
    }

    private static void requireSeedValues(int[][] expected,int[][] actual) {
        String mismatch=seedMismatchMessage(expected,actual);
        if(mismatch!=null) throw new IllegalArgumentException(mismatch);
        assertions++;
    }

    private static String seedMismatchMessage(int[][] expected,int[][] actual) {
        String[] names={"cell","layer","direction","transitions","cost","heuristic","serial","key"};
        if(expected==null || actual==null)
            return "Branch seed tuple changed: missing expected or actual tuple";
        if(expected.length!=actual.length)
            return "Branch seed tuple changed: row count expected="+expected.length+
                " actual="+actual.length;
        for(int row=0;row<expected.length;row++) {
            if(expected[row]==null || actual[row]==null)
                return "Branch seed tuple changed: null row="+row;
            if(expected[row].length!=actual[row].length)
                return "Branch seed tuple changed: row="+row+" field count expected="+
                    expected[row].length+" actual="+actual[row].length;
            for(int field=0;field<expected[row].length;field++)
                if(expected[row][field]!=actual[row][field])
                    return "Branch seed tuple changed: row="+row+" field="+
                        (field<names.length?names[field]:String.valueOf(field))+" expected="+
                        expected[row][field]+" actual="+actual[row][field];
        }
        return null;
    }

    private static void requireGoalValues(boolean[] expected,boolean[] actual) {
        if(expected==null || actual==null || !Arrays.equals(expected,actual))
            throw new IllegalArgumentException("Branch goal mask changed");
        assertions++;
    }

    private static int[][] seedValues(Object branch) throws Exception {
        Object queue=field(branch,"queue");
        int size=intField(queue,"size");
        Object values=field(queue,"values");
        if(size!=2 || values==null || java.lang.reflect.Array.getLength(values)<size)
            throw new IllegalArgumentException("Branch does not retain both face seeds");
        int[][] result=new int[size][8];
        for(int i=0;i<size;i++) {
            Object node=java.lang.reflect.Array.get(values,i);
            String[] names={"cell","layer","direction","transitions","cost","heuristic","serial","key"};
            for(int j=0;j<names.length;j++) result[i][j]=intField(node,names[j]);
        }
        return result;
    }

    private static int[][] pathRows(Object end) throws Exception {
        Vector<int[]> reversed=new Vector<int[]>();
        Object node=end;
        while(node!=null) {
            String[] names={"cell","layer","direction","transitions","cost","heuristic","serial","key"};
            int[] row=new int[names.length];
            for(int i=0;i<names.length;i++) row[i]=intField(node,names[i]);
            reversed.add(row);
            node=field(node,"previous");
            if(reversed.size()>4096) throw new IllegalArgumentException("Literal route path exceeds bound");
        }
        int[][] result=new int[reversed.size()][];
        for(int i=0;i<result.length;i++) result[i]=reversed.get(result.length-1-i);
        return result;
    }

    private static void bindFoundGoalToPrefixVia(Replay replay) throws Exception {
        Object found=field(replay.branch,"found");
        if(found==null) throw new AssertionError("Completed branch has no retained found Node");
        int cell=intField(found,"cell"),layer=intField(found,"layer");
        int transitions=intField(found,"transitions");
        int cells=intField(replay.search,"cells"),width=intField(replay.search,"width");
        int minX=intField(replay.search,"minX"),minY=intField(replay.search,"minY");
        if(cell<0 || cell>=cells || layer<0 || layer>1 || width<1)
            throw new IllegalArgumentException("Found Node is outside the retained Search grid");
        boolean[] goals=(boolean[])field(replay.branch,"goal");
        boolean foundGoal=goals[layer*cells+cell];
        boolean topGoal=goals[cell],bottomGoal=goals[cells+cell];
        if(!foundGoal || !replay.source.goalValues[layer*cells+cell])
            throw new IllegalStateException("Found Node is absent from the retained source goal mask");
        replay.foundX=minX+(cell%width)*PcbLayerRoutingPrototype.GRID;
        replay.foundY=minY+(cell/width)*PcbLayerRoutingPrototype.GRID;
        replay.foundLayer=layer; replay.foundTransitions=transitions;
        PcbBoardHole[] prefix=(PcbBoardHole[])field(replay.branch,"holes");
        PcbBoardHole matched=null; int matches=0,sourceIdentityMatches=0,layoutIdentityMatches=0;
        String creatorPrefix="p07-via/"+replay.source.currentNet+"/3/";
        for(PcbBoardHole hole:prefix) {
            if(hole.x==replay.foundX && hole.y==replay.foundY) {
                if(hole.kind==PcbBoardHole.Kind.VIA &&
                        replay.source.currentNet.equals(hole.netId) && hole.id.startsWith(creatorPrefix)) {
                    matched=hole; matches++;
                    for(PcbBoardHole original:replay.source.holeRefs) if(original==hole) sourceIdentityMatches++;
                }
            }
        }
        if(matches!=1 || sourceIdentityMatches!=1 || !topGoal || !bottomGoal ||
                layer!=PcbCopperLayer.TOP.ordinal() || transitions!=0 || replay.foundX!=310 ||
                replay.foundY!=320)
            throw new IllegalStateException("Found Node is not the unique dual-face creator VIA goal");
        for(PcbBoardHole hole:replay.layout.getHoles()) if(hole==matched) layoutIdentityMatches++;
        if(layoutIdentityMatches!=1)
            throw new IllegalStateException("Found goal does not reuse the actual prefix VIA in replay copper");
        Object terminal=field(replay.branch,"foundTerminal");
        if(terminal!=null || field(replay.publisher,"endTerminal")!=null ||
                field(replay.publisher,"end")!=found)
            throw new IllegalStateException("Tree VIA goal was incorrectly represented as a pad terminal");
        if(replay.pathRows==null || replay.pathRows.length<1) throw new IllegalStateException("Missing found path rows");
        int[] last=replay.pathRows[replay.pathRows.length-1];
        if(last[0]!=cell || last[1]!=layer || last[3]!=transitions)
            throw new IllegalStateException("Published path endpoint differs from retained found Node");
        Vector<PcbTraceGeometry> traces=replay.layout.getTraces();
        if(replay.source.prefixTraceCount>=traces.size() ||
                traces.size()-replay.source.prefixTraceCount!=1)
            throw new IllegalStateException("Tree VIA replay did not publish exactly one branch trace");
        PcbTraceGeometry trace=traces.get(replay.source.prefixTraceCount);
        int[] xs=trace.getXPoints(),ys=trace.getYPoints();
        if(!replay.source.currentNet.equals(trace.getNetId()) ||
                trace.getLayer()!=PcbCopperLayer.TOP ||
                !replay.source.start.getPadId().equals(trace.getStartPadId()) ||
                trace.getEndPadId()!=null || xs[xs.length-1]!=replay.foundX ||
                ys[ys.length-1]!=replay.foundY)
            throw new IllegalStateException("Published trace does not terminate on the same-net creator VIA");
        replay.foundVia=matched;
        replay.foundViaId=matched.id;
        replay.foundViaFingerprint=matched.fingerprint();
        assertions++;
    }

    private static String[] holeIds(PcbBoardLayout layout) {
        Vector<PcbBoardHole> holes=layout.getHoles();
        String[] values=new String[holes.size()];
        for(int i=0;i<values.length;i++) values[i]=holes.get(i).id;
        return values;
    }

    private static String[] holeFingerprints(PcbBoardLayout layout) {
        Vector<PcbBoardHole> holes=layout.getHoles();
        String[] values=new String[holes.size()];
        for(int i=0;i<values.length;i++) values[i]=holes.get(i).fingerprint();
        return values;
    }

    private static boolean sameHoleReferences(PcbBoardHole[] expected,Vector<PcbBoardHole> actual) {
        if(expected.length!=actual.size()) return false;
        for(int i=0;i<expected.length;i++) if(expected[i]!=actual.get(i)) return false;
        return true;
    }

    private static String[] traceSignatures(PcbBoardLayout layout,int from) {
        Vector<PcbTraceGeometry> traces=layout.getTraces();
        if(from<0 || from>traces.size() || traces.size()>100000)
            throw new IllegalArgumentException("Literal trace inventory exceeds bounds");
        String[] result=new String[traces.size()-from];
        for(int i=from;i<traces.size();i++) {
            PcbTraceGeometry trace=traces.get(i);
            StringBuilder row=new StringBuilder();
            row.append(trace.getSourceId()).append('|').append(trace.getNetId()).append('|')
                .append(trace.getLayer()).append('|').append(trace.getExposure()).append('|')
                .append(trace.getStartPadId()).append('|').append(trace.getEndPadId()).append('|')
                .append(trace.getWidth()).append('|');
            int[] xs=trace.getXPoints(),ys=trace.getYPoints();
            for(int p=0;p<xs.length;p++) row.append(xs[p]).append(',').append(ys[p]).append(';');
            result[i-from]=row.toString();
        }
        return result;
    }

    private static String padId(Object pad) {
        return pad==null?null:((PcbPadPlacement)pad).getPadId();
    }

    private static boolean sameNullable(String first,String second) {
        return first==null?second==null:first.equals(second);
    }

    private static int[][] copy(int[][] values) {
        int[][] result=new int[values.length][];
        for(int i=0;i<values.length;i++) result[i]=values[i].clone();
        return result;
    }

    private static Vector<String> copyStrings(Vector<String> source) {
        return source==null?null:new Vector<String>(source);
    }

    private static boolean vectorEquals(Vector<?> actual,Vector<?> expected) {
        if(actual==null || expected==null) return actual==expected;
        if(actual.size()!=expected.size()) return false;
        for(int i=0;i<actual.size();i++) if(!expected.get(i).equals(actual.get(i))) return false;
        return true;
    }

    private static void expectRejected(Checked check,String expectedMessage,String label)
            throws Exception {
        try { check.run(); }
        catch(IllegalArgumentException expected) {
            if(!expectedMessage.equals(expected.getMessage()))
                throw new AssertionError("Wrong rejection for "+label,expected);
            assertions++; return;
        }
        throw new AssertionError("Expected rejection: "+label);
    }

    private static Object field(Object target,String name) throws Exception {
        if(target==null) throw new IllegalArgumentException("Missing reflection target: "+name);
        Field field=target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target,String name,Object value) throws Exception {
        Field field=target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target,value);
    }

    private static int intField(Object target,String name) throws Exception {
        return ((Integer)field(target,name)).intValue();
    }

    private static String stringField(Object target,String name) throws Exception {
        Object value=field(target,name);
        return value==null?null:(String)value;
    }

    private static boolean booleanField(Object target,String name) throws Exception {
        return ((Boolean)field(target,name)).booleanValue();
    }

    private static Object invoke(Method method,Object target,Object... arguments) throws Exception {
        try { return method.invoke(target,arguments); }
        catch(InvocationTargetException failure) {
            Throwable cause=failure.getCause();
            if(cause instanceof Exception) throw (Exception)cause;
            if(cause instanceof Error) throw (Error)cause;
            throw new RuntimeException(cause);
        }
    }

    private static void require(boolean condition,String label) {
        if(!condition) throw new AssertionError(label);
        assertions++;
    }

    private static final class Source {
        final LiteralFixture fixture;
        final PcbLayerRoutingPrototype.Session session,owner;
        final Object search,branch;
        final PcbBoardLayout layout;
        final PcbBoardLayout prefixLayout;
        final PcbPadPlacement start,root;
        final P07AcceptedBranchCaptureDiagnostics.SeedSnapshot seeds;
        final PcbBoardHole[] holeRefs;
        final String[] prefixIds,prefixFingerprints,traceSignatures;
        final String layoutFingerprint,graph;
        final String currentNet;
        final Vector<String> netOrder,currentPadIds,reachedPadIds;
        final Object netOrderRef,currentPadIdsRef,reachedPadIdsRef;
        final Object queueRef,goalRef;
        final Object faceArrayRef,workspaceRef;
        final PcbNetRouter.Router[] faceRefs,faceSnapshots;
        final String[] faceEffects;
        final int pass,branchOrdinal,serial,branches,segments,netIndex,maximumVias;
        final int budgetMaximum,budgetExpanded,prefixTraceCount,sourceUnits;
        final int[][] seedValues;
        final boolean[] goalValues;

        Source(LiteralFixture fixture,
                PcbLayerRoutingPrototype.Session session,Object search,Object branch,int sourceUnits)
                throws Exception {
            this.fixture=fixture; this.session=session; this.search=search; this.branch=branch;
            require(sourceUnits>0 && sourceUnits<MAX_SOURCE_UNITS,
                "actual source branch appears within the bounded literal handoff window");
            this.owner=(PcbLayerRoutingPrototype.Session)field(search,"owner");
            this.layout=(PcbBoardLayout)field(search,"layout");
            this.prefixLayout=layout.copyForCompaction();
            this.start=(PcbPadPlacement)field(branch,"start");
            this.root=(PcbPadPlacement)field(branch,"root");
            this.queueRef=field(branch,"queue");
            this.goalRef=field(branch,"goal");
            this.faceArrayRef=field(search,"faces");
            this.faceRefs=((PcbNetRouter.Router[])faceArrayRef).clone();
            this.faceSnapshots=new PcbNetRouter.Router[faceRefs.length];
            this.faceEffects=new String[faceRefs.length];
            for(int i=0;i<faceRefs.length;i++) {
                this.faceEffects[i]=faceRefs[i].fixedGraphObservationEffects();
                this.faceSnapshots[i]=snapshotRouter(faceRefs[i]);
            }
            this.workspaceRef=field(owner,"turnBoundWorkspace");
            this.currentNet=stringField(search,"currentNet");
            this.netOrderRef=field(search,"nets");
            this.currentPadIdsRef=field(search,"currentPadIds");
            this.reachedPadIdsRef=field(search,"reachedPadIds");
            this.netOrder=new Vector<String>((Vector<String>)netOrderRef);
            this.currentPadIds=copyStrings((Vector<String>)field(search,"currentPadIds"));
            this.reachedPadIds=copyStrings((Vector<String>)field(search,"reachedPadIds"));
            this.pass=intField(search,"pass");
            this.branchOrdinal=intField(branch,"branchOrdinal");
            this.serial=intField(search,"serial"); this.branches=intField(search,"branches");
            this.segments=intField(search,"segments"); this.netIndex=intField(search,"netIndex");
            this.maximumVias=intField(search,"maximumVias"); this.sourceUnits=sourceUnits;
            Object budget=field(search,"budget");
            this.budgetMaximum=intField(budget,"maximum");
            this.budgetExpanded=intField(budget,"expanded");
            this.prefixTraceCount=layout.getTraces().size();
            this.holeRefs=((PcbBoardHole[])field(branch,"holes")).clone();
            this.prefixIds=holeIds(layout); this.prefixFingerprints=holeFingerprints(layout);
            this.traceSignatures=traceSignatures(layout,0);
            this.layoutFingerprint=layout.geometryFingerprint();
            this.graph=layout.captureConductorGraph(fixture.board).toCanonical();
            this.seeds=P07AcceptedBranchCaptureDiagnostics.captureInitialSeeds(search,branch);
            this.seedValues=seedValues(branch);
            this.goalValues=((boolean[])field(branch,"goal")).clone();
            require(seeds.branchOrdinal==branchOrdinal && seeds.pass==pass &&
                    seeds.currentNet.equals(currentNet) && seeds.prefixHoleRefs.length==prefixIds.length,
                "existing immutable source helper binds the actual branch-entry authority");
            require(seeds.nodeCount()==2 && seeds.faceMask==3 && prefixIds.length<=48 &&
                    traceSignatures.length<=100000,
                "literal source snapshot remains within branch, VIA and trace bounds");
            for(int i=0;i<holeRefs.length;i++)
                require(holeRefs[i].kind==PcbBoardHole.Kind.VIA &&
                        currentNet.equals(holeRefs[i].netId) &&
                        holeRefs[i].id.startsWith("p07-via/"+currentNet+"/3/"),
                    "literal prefix hole remains owned by an earlier same-net Publisher");
            require(session==owner && field(session,"search")==search &&
                    field(search,"branch")==branch && field(search,"publisher")==null &&
                    start.getAttachment()==PcbTerminalAttachment.PLATED_THROUGH_HOLE &&
                    faceRefs.length==2 && workspaceRef!=null,
                "source is captured before its first consumer branch work unit");
        }
    }

    private static final class LiteralFixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        LiteralFixture(TroubleshootBoard board,PcbBoardLayout layout) {
            if(board==null || layout==null)
                throw new IllegalArgumentException("Missing literal same-session VIA-tree fixture");
            this.board=board; this.layout=layout;
        }
    }

    private static final class Replay {
        final Source source;
        final PcbLayerRoutingPrototype.Session owner;
        final Object search,branch;
        final PcbBoardLayout layout;
        final PcbPadPlacement start,root;
        final int maximumVias,budgetMaximum;
        Object publisher;
        int[][] pathRows;
        String[] traceDelta,postHoleIds,postHoleFingerprints;
        String postGeometry,postGraph;
        PcbBoardHole foundVia;
        String foundViaId,foundViaFingerprint;
        int foundX,foundY,foundLayer,foundTransitions;

        Replay(Source source,PcbLayerRoutingPrototype.Session owner,Object search,Object branch,
                PcbBoardLayout layout,int maximumVias) throws Exception {
            this.source=source; this.owner=owner; this.search=search; this.branch=branch;
            this.layout=layout; this.maximumVias=maximumVias;
            this.budgetMaximum=intField(field(search,"budget"),"maximum");
            this.start=(PcbPadPlacement)field(branch,"start");
            this.root=(PcbPadPlacement)field(branch,"root");
        }
    }

    private interface Checked { void run() throws Exception; }
}
