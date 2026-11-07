package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.Vector;

/** Focused actual-owner contracts for terminal-only P07 branch diagnostics. */
public final class P07TerminalOnlyDiagnosticsContractTest {
    private static final int NULL_MODE=0, FULL_MODE=1, TERMINAL_MODE=2;
    private static final int POSITIVE_VIA_CAPACITY=1, ZERO_VIA_CAPACITY=0;
    private static int checks;

    private P07TerminalOnlyDiagnosticsContractTest() { }

    public static void main(String[] args) {
        verifyActualRouteParity(POSITIVE_VIA_CAPACITY,true);
        verifyActualRouteParity(ZERO_VIA_CAPACITY,false);
        verifyActualBlockedParity(POSITIVE_VIA_CAPACITY);
        verifyActualBlockedParity(ZERO_VIA_CAPACITY);
        verifyForcedBoundParity(POSITIVE_VIA_CAPACITY);
        verifyForcedBoundParity(ZERO_VIA_CAPACITY);
        verifyCallbackFailureAndReuse(false);
        verifyCallbackFailureAndReuse(true);
        verifyDetachedMarkerAndCallerRelease();
        System.out.println("PASS: P07 terminal-only diagnostics contracts assertions="+checks);
    }

    private static class SnapshotCapture implements PcbLayerRoutingPrototype.Diagnostics {
        final Vector<PcbLayerRoutingPrototype.BranchSnapshot> snapshots=
            new Vector<PcbLayerRoutingPrototype.BranchSnapshot>();
        final Throwable failure;
        int calls;
        SnapshotCapture(Throwable failure) { this.failure=failure; }
        public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
            calls++;
            snapshots.add(snapshot);
            if(failure instanceof RuntimeException) throw (RuntimeException)failure;
            if(failure instanceof Error) throw (Error)failure;
        }
    }

    private static final class TerminalCapture extends SnapshotCapture
            implements PcbLayerRoutingPrototype.TerminalOnlyDiagnostics {
        TerminalCapture(Throwable failure) { super(failure); }
    }

    private static SnapshotCapture capture(int mode,Throwable failure) {
        if(mode==NULL_MODE) return null;
        return mode==TERMINAL_MODE?new TerminalCapture(failure):new SnapshotCapture(failure);
    }

    private static final class RouteRun {
        final P06FactoryLinkFixtures.Fixture fixture;
        final SnapshotCapture capture;
        final PcbLayerRoutingPrototype.Result result;
        final String sourceGeometry;
        RouteRun(P06FactoryLinkFixtures.Fixture fixture,SnapshotCapture capture,
                PcbLayerRoutingPrototype.Result result,String sourceGeometry) {
            this.fixture=fixture; this.capture=capture; this.result=result;
            this.sourceGeometry=sourceGeometry;
        }
    }

    private static RouteRun runActualRoute(int mode,int maximum,int maximumVias) {
        P06FactoryLinkFixtures.Fixture fixture=new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        String sourceGeometry=fixture.layout.geometryFingerprint();
        SnapshotCapture capture=capture(mode,null);
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(
            fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,maximum,maximumVias,capture);
        int slices=0;
        while(!session.advanceSlice(PcbLayerRoutingPrototype.WORK_QUANTUM))
            if(++slices>1000) throw new AssertionError("small actual route exceeded its finite slice guard");
        PcbLayerRoutingPrototype.Result result=session.result();
        require(result!=null && result.expansions<=maximum && result.orderings<=PcbLayerRoutingPrototype.ORDERINGS,
            "actual Session publishes a bounded terminal result");
        require(sourceGeometry.equals(fixture.layout.geometryFingerprint()) &&
            fixture.layout.getTraces().isEmpty() && fixture.layout.getHoles().isEmpty(),
            "actual route attempt preserves the source placement");
        if(capture!=null) {
            require(capture.calls==capture.snapshots.size(),"actual terminal callbacks are fully retained");
            for(PcbLayerRoutingPrototype.BranchSnapshot snapshot:capture.snapshots)
                verifySnapshotDto(snapshot);
        }
        return new RouteRun(fixture,capture,result,sourceGeometry);
    }

    private static void verifyActualRouteParity(int maximumVias,boolean requirePositiveSuccess) {
        RouteRun none=runActualRoute(NULL_MODE,PcbLayerRoutingPrototype.MAX_EXPANSIONS,maximumVias);
        RouteRun full=runActualRoute(FULL_MODE,PcbLayerRoutingPrototype.MAX_EXPANSIONS,maximumVias);
        RouteRun terminal=runActualRoute(TERMINAL_MODE,PcbLayerRoutingPrototype.MAX_EXPANSIONS,maximumVias);
        assertResultParity(none.result,full.result,"NULL/FULL actual route parity");
        assertResultParity(none.result,terminal.result,"NULL/TERMINAL actual route parity");
        require(full.capture.calls>0 && full.capture.calls==terminal.capture.calls,
            "full and terminal-only modes retain every actual branch terminal callback");
        requireSnapshotVectorsEqual(full.capture.snapshots,terminal.capture.snapshots,false,
            "ordinary route snapshots are field-for-field equal");
        if(requirePositiveSuccess)
            require(full.result.accepted(),"the small positive-capacity actual fixture has a real accepted route");
        else
            require(full.result.outcome!=null && full.result.outcome.length()>0,
                "zero-capacity actual fixture reaches an explicit accepted or rejected terminal result");
    }

    private static void verifyActualBlockedParity(int maximumVias) {
        RouteRun none=runActualRoute(NULL_MODE,1,maximumVias);
        RouteRun full=runActualRoute(FULL_MODE,1,maximumVias);
        RouteRun terminal=runActualRoute(TERMINAL_MODE,1,maximumVias);
        assertResultParity(none.result,full.result,"NULL/FULL reduced-cap blocked parity");
        assertResultParity(none.result,terminal.result,"NULL/TERMINAL reduced-cap blocked parity");
        require(!full.result.accepted() && full.result.expansions==1 &&
            full.result.outcome.endsWith("TOTAL_SEARCH_LIMIT"),
            "the real one-expansion owner cap terminates with its typed blocked outcome");
        require(full.capture.calls>0 && full.capture.calls==terminal.capture.calls,
            "blocked full and terminal-only sessions retain the same actual terminal callback count");
        requireSnapshotVectorsEqual(full.capture.snapshots,terminal.capture.snapshots,false,
            "unobserved-bound blocked snapshots remain field-for-field equal");
    }

    private static void assertResultParity(PcbLayerRoutingPrototype.Result first,
            PcbLayerRoutingPrototype.Result second,String label) {
        require(first.outcome.equals(second.outcome) && first.accepted()==second.accepted() &&
            first.expansions==second.expansions && first.orderings==second.orderings &&
            first.vias==second.vias && first.links==second.links,
            label+" preserves status and route accounting");
        if(first.accepted())
            require(first.layout.geometryFingerprint().equals(second.layout.geometryFingerprint()) &&
                first.layout.traceGeometryFingerprint().equals(second.layout.traceGeometryFingerprint()),
                label+" preserves the actual copper output");
    }

    /**
     * Test-only finite boundary injection into a genuine first BranchSearch. It validates
     * the late observation-copy seam, not a routed workload or production route outcome.
     */
    private static final class BoundProbe {
        final int mode,maximumVias;
        final P06FactoryLinkFixtures.Fixture fixture;
        final SnapshotCapture capture;
        final String sourceGeometry,reason;
        final PcbLayerRoutingPrototype.BranchSnapshot snapshot;
        final int workUnits,pristineFaces,queryFaces;
        BoundProbe(int mode,int maximumVias,P06FactoryLinkFixtures.Fixture fixture,
                SnapshotCapture capture,String sourceGeometry,String reason,
                PcbLayerRoutingPrototype.BranchSnapshot snapshot,int workUnits,
                int pristineFaces,int queryFaces) {
            this.mode=mode; this.maximumVias=maximumVias; this.fixture=fixture;
            this.capture=capture; this.sourceGeometry=sourceGeometry; this.reason=reason;
            this.snapshot=snapshot; this.workUnits=workUnits;
            this.pristineFaces=pristineFaces; this.queryFaces=queryFaces;
        }
    }

    private static void verifyForcedBoundParity(int maximumVias) {
        BoundProbe none=runForcedBoundProbe(NULL_MODE,maximumVias);
        BoundProbe full=runForcedBoundProbe(FULL_MODE,maximumVias);
        BoundProbe terminal=runForcedBoundProbe(TERMINAL_MODE,maximumVias);
        require(none.reason.equals(full.reason) && none.reason.equals(terminal.reason),
            "forced actual BranchSearch boundary reaches the same blocked terminal reason in every mode");
        require(full.reason.endsWith("BRANCH_SEARCH_LIMIT") || full.reason.endsWith("NO_PATH"),
            "forced boundary emits only a real branch-limit or empty-frontier terminal");
        require(full.pristineFaces==2 && none.pristineFaces==(maximumVias==0?2:0) &&
            terminal.pristineFaces==(maximumVias==0?2:0),
            "actual pristine router copies follow full versus mandatory zero-capacity observation");
        require(full.queryFaces==2 && none.queryFaces==2 && terminal.queryFaces==2,
            "all three actual relaxed-bound builds use exactly two query-face copies");
        if(maximumVias==0) {
            require(none.workUnits==full.workUnits && full.workUnits==terminal.workUnits,
                "zero-capacity mandatory comparisons keep the same actual bound work in all modes");
            requireSnapshotVectorsEqual(full.capture.snapshots,terminal.capture.snapshots,false,
                "zero-capacity terminal-only receipt equals the full guarded receipt");
        } else {
            require(none.workUnits==terminal.workUnits && full.workUnits==terminal.workUnits+2,
                "positive-capacity terminal-only mode omits exactly the two diagnostic-only pristine copies");
            require(none.pristineFaces==0 && none.reason.equals(terminal.reason),
                "positive-capacity NULL owner omits pristine copies and reaches the terminal-only boundary");
            requireSnapshotVectorsEqual(full.capture.snapshots,terminal.capture.snapshots,true,
                "positive-capacity full and terminal-only receipts differ only in observation proof fields and copy work");
        }
        for(BoundProbe probe:new BoundProbe[]{none,full,terminal})
            require(probe.sourceGeometry.equals(probe.fixture.layout.geometryFingerprint()) &&
                probe.fixture.layout.getTraces().isEmpty() && probe.fixture.layout.getHoles().isEmpty(),
                "forced bound canary preserves each actual caller placement");
    }

    private static BoundProbe runForcedBoundProbe(int mode,int maximumVias) {
        P06FactoryLinkFixtures.Fixture fixture=new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        String sourceGeometry=fixture.layout.geometryFingerprint();
        SnapshotCapture capture=capture(mode,null);
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(
            fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS,maximumVias,capture);
        Object search=null,branch=null;
        for(int call=0;call<1000 && branch==null;call++) {
            session.advanceSlice(1);
            search=field(session,"search");
            branch=search==null?null:field(search,"branch");
        }
        require(search!=null && branch!=null && number(branch,"expanded")==0,
            "forced boundary starts from the actual untouched first branch object");
        Object queue=field(branch,"queue");
        int queueSize=number(queue,"size");
        require(queueSize==2,"fresh actual branch has both primary/secondary seed states");
        PcbNetRouter.Router[] liveFaces=(PcbNetRouter.Router[])field(search,"faces");
        PcbNetRouter.Router[] independentLiveCopies=new PcbNetRouter.Router[] {
            liveFaces[0].copyForFixedGraphObservation(),liveFaces[1].copyForFixedGraphObservation()};
        String[] liveEffects=new String[] {
            liveFaces[0].fixedGraphObservationEffects(),liveFaces[1].fixedGraphObservationEffects()};
        PcbBoardLayout candidate=(PcbBoardLayout)field(session,"candidate");
        String candidateGeometry=candidate.geometryFingerprint();
        int primary=number(search,"primary");
        int threshold=PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS-PcbLayerRoutingPrototype.WORK_QUANTUM;
        primeExpansionCounters(session,search,branch,primary,threshold);
        session.advanceSlice(1); // actual late trigger and actual beginRelaxedBound()
        require(bool(branch,"relaxedBoundStarted"),"actual branch crossed the frozen late relaxed-bound trigger");

        boolean observationRequired=mode==FULL_MODE || maximumVias==0;
        boolean[] seenPristine={false,false},seenQuery={false,false};
        int cells=number(search,"cells"),buildCalls=0;
        int maximumBuildCalls=4*cells+128;
        while(!bool(branch,"relaxedBoundComplete")) {
            inspectBoundCopies(branch,liveFaces,independentLiveCopies,liveEffects,
                observationRequired,seenPristine,seenQuery);
            if(++buildCalls>maximumBuildCalls)
                throw new AssertionError("actual small-grid relaxed bound exceeded its finite state-work guard");
            session.advanceSlice(1);
        }
        int pristineCount=(seenPristine[0]?1:0)+(seenPristine[1]?1:0);
        int queryCount=(seenQuery[0]?1:0)+(seenQuery[1]?1:0);
        require(queryCount==2 && pristineCount==(observationRequired?2:0),
            "actual bound build independently records its live query and pristine face copies");
        require(bool(branch,"relaxedBoundObservationChecked")==observationRequired &&
            bool(branch,"relaxedBoundLiveLayoutStable")==observationRequired &&
            bool(branch,"relaxedBoundRouterStateStable")==observationRequired &&
            bool(branch,"relaxedBoundRouterEffectsStable")==observationRequired,
            "fixed-graph observation receipts match the actual capacity requirement");
        require(candidateGeometry.equals(candidate.geometryFingerprint()) &&
            liveEffects[0].equals(liveFaces[0].fixedGraphObservationEffects()) &&
            liveEffects[1].equals(liveFaces[1].fixedGraphObservationEffects()) &&
            liveFaces[0].sameFixedGraphObservation(independentLiveCopies[0]) &&
            liveFaces[1].sameFixedGraphObservation(independentLiveCopies[1]),
            "independent geometry/router snapshots prove the actual bound left live inputs unchanged");
        require(field(branch,"relaxedQueryFaces")==null && field(branch,"relaxedPristineFaces")==null &&
            field(branch,"relaxedLiveEffectsBefore")==null && field(branch,"relaxedLayoutGeometryBefore")==null,
            "completed actual bound releases query, pristine, effect and layout observation aliases");
        int workUnits=number(branch,"relaxedBoundWorkUnits");
        require(workUnits>0 && field(branch,"relaxedDistanceBound")!=null,
            "actual owner completed a nonempty relaxed-distance field");

        // Force only the already-created branch frontier to the existing cap so the same
        // real Session handles its terminal transition. This is not a production route claim.
        primeExpansionCounters(session,search,branch,primary,PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS);
        int terminalCalls=0;
        while(field(session,"search")==search && terminalCalls<16) {
            session.advanceSlice(1);
            terminalCalls++;
        }
        require(field(session,"search")==null && terminalCalls>0 && bool(branch,"terminalEmitted"),
            "real Session consumes the forced live frontier into a branch terminal");
        require(session.expansions()==PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS,
            "forced branch terminal keeps the shared expansion receipt at its injected boundary");
        String reason=(String)field(session,"reason");
        PcbLayerRoutingPrototype.BranchSnapshot snapshot=null;
        if(capture!=null) {
            require(capture.calls==1 && capture.snapshots.size()==1,
                "each diagnostic mode emits one immutable receipt for the forced real branch terminal");
            snapshot=capture.snapshots.firstElement();
            verifySnapshotDto(snapshot);
            require(snapshot.branchExpanded==PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS &&
                snapshot.expandedTop+snapshot.expandedBottom==snapshot.branchExpanded &&
                snapshot.totalExpanded==PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS &&
                snapshot.relaxedBoundStarted && snapshot.relaxedBoundComplete &&
                snapshot.relaxedBoundObservationChecked==(mode==FULL_MODE || maximumVias==0) &&
                snapshot.relaxedBoundLiveLayoutStable==(mode==FULL_MODE || maximumVias==0) &&
                snapshot.relaxedBoundRouterStateStable==(mode==FULL_MODE || maximumVias==0) &&
                snapshot.relaxedBoundRouterEffectsStable==(mode==FULL_MODE || maximumVias==0) &&
                snapshot.relaxedBoundWorkUnits==workUnits &&
                snapshot.terminalReason.equals(reason.substring(reason.indexOf(':')+1)),
                "forced branch snapshot reports the actual cap, completed field and owner terminal reason");
        } else require(reason!=null && reason.indexOf(':')>0,
            "NULL mode retains the same terminal reason on its real Session owner");
        return new BoundProbe(mode,maximumVias,fixture,capture,sourceGeometry,reason,snapshot,
            workUnits,pristineCount,queryCount);
    }

    private static void primeExpansionCounters(Object session,Object search,Object branch,
            int primary,int count) {
        setInt(branch,"expanded",count);
        setInt(branch,"expandedTop",primary==PcbCopperLayer.TOP.ordinal()?count:0);
        setInt(branch,"expandedBottom",primary==PcbCopperLayer.BOTTOM.ordinal()?count:0);
        Object budget=field(session,"budget");
        require(budget==field(search,"budget"),"real Session and Search share one owner budget");
        setInt(budget,"expanded",count);
    }

    private static void inspectBoundCopies(Object branch,PcbNetRouter.Router[] live,
            PcbNetRouter.Router[] independentLiveCopies,String[] liveEffects,
            boolean observationRequired,boolean[] seenPristine,boolean[] seenQuery) {
        Object[] pristine=(Object[])field(branch,"relaxedPristineFaces");
        Object[] query=(Object[])field(branch,"relaxedQueryFaces");
        Object bound=field(branch,"relaxedDistanceBound");
        if(observationRequired)
            require(pristine!=null && pristine.length==2,
                "mandatory/full observation owns an actual two-face pristine comparison array");
        else require(pristine==null,
            "positive-capacity NULL/TERMINAL mode allocates no diagnostic-only pristine array");
        require(query!=null && query.length==2,"actual relaxed bound owns two query faces in every mode");
        for(int face=0;face<2;face++) {
            require(liveEffects[face].equals(live[face].fixedGraphObservationEffects()) &&
                live[face].sameFixedGraphObservation(independentLiveCopies[face]),
                "each live router remains equal to an independently retained fixed-state snapshot");
            if(pristine!=null && pristine[face]!=null) {
                PcbNetRouter.Router copy=(PcbNetRouter.Router)pristine[face];
                require(copy!=live[face] && live[face].sameFixedGraphObservation(copy),
                    "observed pristine face is a distinct exact fixed-graph copy");
                String[] before=(String[])field(branch,"relaxedLiveEffectsBefore");
                require(before!=null && before[face]!=null && before[face].equals(liveEffects[face]),
                    "pristine copy retains the literal pre-scan effects stamp");
                seenPristine[face]=true;
            }
            if(query[face]!=null && !seenQuery[face]) {
                PcbNetRouter.Router copy=(PcbNetRouter.Router)query[face];
                require(copy!=live[face],"bound query face is a distinct actual Router copy");
                if(observationRequired) {
                    require(pristine!=null && pristine[face]!=null &&
                        copy.sameFixedGraphObservation((PcbNetRouter.Router)pristine[face]),
                        "observed query copy independently matches its untouched pristine face");
                } else if(bound==null || number(bound,"workUnits")==0) {
                    require(copy.sameFixedGraphObservation(independentLiveCopies[face]),
                        "unobserved query copy starts equal to the independent pre-scan live state");
                }
                seenQuery[face]=true;
            }
        }
    }

    private static void verifyCallbackFailureAndReuse(boolean useError) {
        Throwable sentinel=useError?new AssertionError("terminal-only Error callback sentinel"):
            new IllegalStateException("terminal-only RuntimeException callback sentinel");
        P06FactoryLinkFixtures.Fixture fixture=new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        String sourceGeometry=fixture.layout.geometryFingerprint();
        TerminalCapture capture=new TerminalCapture(sentinel);
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(
            fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias,capture);
        Throwable observed=null;
        for(int i=0;i<1000 && observed==null;i++) {
            try { session.advanceSlice(PcbLayerRoutingPrototype.WORK_QUANTUM); }
            catch(RuntimeException failure) { observed=failure; }
            catch(Error failure) { observed=failure; }
        }
        require(observed==sentinel && capture.calls==1 && capture.snapshots.size()==1,
            "actual terminal-only callback propagates the exact RuntimeException/Error once");
        require(field(session,"search")==null && field(session,"candidate")==null &&
            field(session,"result")==null && !bool(session,"complete") &&
            field(session,"diagnosticFailure")==sentinel,
            "callback failure aborts and latches the actual Session without a result");
        Throwable advanceRetry=null,resultRetry=null;
        try { session.advanceSlice(1); }
        catch(RuntimeException failure) { advanceRetry=failure; }
        catch(Error failure) { advanceRetry=failure; }
        try { session.result(); }
        catch(RuntimeException failure) { resultRetry=failure; }
        catch(Error failure) { resultRetry=failure; }
        require(advanceRetry==sentinel && resultRetry==sentinel && capture.calls==1,
            "failed terminal-only session rethrows the same object and cannot reuse the callback");
        require(sourceGeometry.equals(fixture.layout.geometryFingerprint()) &&
            fixture.layout.getTraces().isEmpty() && fixture.layout.getHoles().isEmpty(),
            "failed callback leaves the caller placement unchanged");
    }

    private static void verifyDetachedMarkerAndCallerRelease() {
        P06FactoryLinkFixtures.Fixture fixture=new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        SnapshotCapture caller=new TerminalCapture(null);
        PcbLayerRoutingPrototype.Session source=PcbLayerRoutingPrototype.begin(
            fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias,caller);
        Object sourceSearch=null,sourceBranch=null;
        for(int call=0;call<1000 && sourceBranch==null;call++) {
            source.advanceSlice(1);
            sourceSearch=field(source,"search");
            sourceBranch=sourceSearch==null?null:field(sourceSearch,"branch");
        }
        require(sourceSearch!=null && sourceBranch!=null && number(sourceBranch,"expanded")==0,
            "detached canary starts from a genuine initial source branch");
        SeededPcbLayoutGenerator.AttemptObserver detachedObserver=
            new SeededPcbLayoutGenerator.AttemptObserver() { public void check(int attempt) { } };
        PcbLayerRoutingPrototype.DetachedBranchCopyTask copy=
            PcbLayerRoutingPrototype.beginDetachedBranchCopy(source,detachedObserver);
        PcbLayerRoutingPrototype.DetachedBranchReplay replay=null;
        try {
            int calls=0;
            while(!copy.isComplete()) {
                if(++calls>1000) throw new AssertionError("small detached branch copy exceeded its bounded owner guard");
                copy.advance(PcbLayerRoutingPrototype.WORK_QUANTUM);
            }
            replay=copy.result();
            Object detachedSearch=replay.getSearchForDiagnostics();
            Object detachedBranch=replay.getBranchForDiagnostics();
            Object detachedOwner=field(detachedSearch,"owner");
            Object detachedDiagnostics=field(detachedOwner,"diagnostics");
            require(detachedDiagnostics instanceof PcbLayerRoutingPrototype.TerminalOnlyDiagnostics &&
                detachedDiagnostics!=caller && field(copy,"source")==null && field(copy,"observer")==null,
                "detached replay preserves the marker with a no-op sink and drops borrowed caller references");
            require(caller.calls==0 && detachedBranch!=sourceBranch && detachedSearch!=sourceSearch,
                "detached owner has independent branch/search identities and never invokes the caller sink");
            int primary=number(detachedSearch,"primary");
            primeExpansionCounters(detachedOwner,detachedSearch,detachedBranch,primary,
                PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS-PcbLayerRoutingPrototype.WORK_QUANTUM);
            replay.advance(1);
            require(bool(detachedBranch,"relaxedBoundStarted") &&
                field(detachedBranch,"relaxedPristineFaces")==null && caller.calls==0,
                "detached marker retains positive-capacity terminal-only observation mode");
        } finally {
            if(replay!=null) replay.release();
            copy.cancel();
        }
    }

    private static void verifySnapshotDto(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
        require(snapshot!=null,"actual branch receipt is non-null");
        TreeSet<String> actual=new TreeSet<String>();
        for(Field field:PcbLayerRoutingPrototype.BranchSnapshot.class.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers())) continue;
            actual.add(field.getName());
            require(Modifier.isFinal(field.getModifiers()) &&
                (field.getType()==Integer.TYPE || field.getType()==Boolean.TYPE || field.getType()==String.class),
                "BranchSnapshot retains only immutable scalar receipt fields");
        }
        TreeSet<String> expected=new TreeSet<String>(Arrays.asList(new String[] {
            "pass","netId","startPadId","rootPadId","startX","startY","rootX","rootY",
            "gridWidth","gridHeight","goalsTop","goalsBottom","prefixViaCount","reachedPadCount",
            "initialManhattanDistance","minimumExploredManhattanDistance","branchExpanded","totalExpanded",
            "expandedTop","expandedBottom","frontierCount","bestStateCount","terminalReason",
            "relaxedBoundStarted","relaxedBoundComplete","relaxedBoundObservationChecked",
            "relaxedBoundLiveLayoutStable","relaxedBoundRouterStateStable","relaxedBoundRouterEffectsStable",
            "relaxedBoundWorkUnits","relaxedBoundOrdinaryProbes","relaxedBoundViaProbes","relaxedBoundRefreshes"
        }));
        require(actual.equals(expected),"terminal receipt retains the complete frozen scalar field schema");
        require(snapshot.pass>=0 && snapshot.pass<PcbLayerRoutingPrototype.ORDERINGS &&
            snapshot.branchExpanded==snapshot.expandedTop+snapshot.expandedBottom &&
            snapshot.totalExpanded>=snapshot.branchExpanded && snapshot.frontierCount>=0 &&
            snapshot.bestStateCount>=0 && snapshot.terminalReason!=null,
            "terminal snapshot values satisfy independent branch and frontier bounds");
    }

    private static void requireSnapshotVectorsEqual(Vector<PcbLayerRoutingPrototype.BranchSnapshot> first,
            Vector<PcbLayerRoutingPrototype.BranchSnapshot> second,boolean allowObservationDifference,String label) {
        require(first.size()==second.size(),label+" preserves callback cardinality");
        for(int index=0;index<first.size();index++) {
            PcbLayerRoutingPrototype.BranchSnapshot a=first.elementAt(index),b=second.elementAt(index);
            for(Field field:PcbLayerRoutingPrototype.BranchSnapshot.class.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers())) continue;
                if(allowObservationDifference && ("relaxedBoundObservationChecked".equals(field.getName()) ||
                        "relaxedBoundLiveLayoutStable".equals(field.getName()) ||
                        "relaxedBoundRouterStateStable".equals(field.getName()) ||
                        "relaxedBoundRouterEffectsStable".equals(field.getName()) ||
                        "relaxedBoundWorkUnits".equals(field.getName()))) continue;
                try {
                    field.setAccessible(true);
                    Object left=field.get(a),right=field.get(b);
                    require(left==null?right==null:left.equals(right),
                        label+" at callback "+index+" field "+field.getName());
                } catch(IllegalAccessException failure) {
                    throw new AssertionError("cannot compare snapshot field "+field.getName(),failure);
                }
            }
        }
    }

    private static Object field(Object owner,String name) {
        if(owner==null) return null;
        try {
            Field field=owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch(Exception failure) {
            throw new AssertionError("cannot inspect actual owner field "+name,failure);
        }
    }

    private static int number(Object owner,String name) {
        Object value=field(owner,name);
        if(value instanceof Integer) return ((Integer)value).intValue();
        throw new AssertionError("actual owner field is not an integer: "+name);
    }

    private static boolean bool(Object owner,String name) {
        Object value=field(owner,name);
        if(value instanceof Boolean) return ((Boolean)value).booleanValue();
        throw new AssertionError("actual owner field is not boolean: "+name);
    }

    private static void setInt(Object owner,String name,int value) {
        try {
            Field field=owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(owner,value);
        } catch(Exception failure) {
            throw new AssertionError("cannot seed explicitly labeled test boundary field "+name,failure);
        }
    }

    private static void require(boolean condition,String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
}
