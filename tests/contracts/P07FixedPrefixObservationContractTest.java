package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Vector;

/** Literal geometry, ownership and fatal-lifecycle contracts for fixed-prefix capture. */
public final class P07FixedPrefixObservationContractTest {
    private static final String WALL="00_WALL";
    private static final String TREE="10_TREE";
    private static final Rectangle OUTLINE=new Rectangle(100,100,800,700);
    private static final SeededPcbLayoutGenerator.AttemptObserver NOOP=
        new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };
    private static int checks;

    private P07FixedPrefixObservationContractTest() { }

    public static void main(String[] args) {
        CaptureCutoff cutoff=limitAfterFirstTreeBranch();
        selectedFailureCopiesLiteralDirectedGraph(cutoff);
        zeroCapacityNoPathRetainsGeometryPermissiveCaptureVias();
        zeroCapacityNormalOwnerPerformsComparisonsWithoutDiagnostics();
        pressureRelaxedBoundAbortContracts();
        pressureBoundCapacityGuardsPrecedeUnreachablePruning();
        unselectedFailureAndFoundBranchesDoNotCapture(cutoff);
        resumableCopyIsIsolatedAndStalenessIsDetectable();
        sinkFailureAndCallbackCancellationStayLatched(cutoff);
        System.out.println("PASS: P07 fixed-prefix observation contracts assertions="+checks+
            " layoutQualification=false");
    }

    private static CaptureCutoff limitAfterFirstTreeBranch() {
        final Vector<PcbLayerRoutingPrototype.BranchSnapshot> branches=
            new Vector<PcbLayerRoutingPrototype.BranchSnapshot>();
        PcbLayerRoutingPrototype.Diagnostics diagnostics=
            new PcbLayerRoutingPrototype.Diagnostics() {
                public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                    branches.add(snapshot);
                }
            };
        Fixture fixture=new Fixture();
        check(fixture.layout.getTraces().isEmpty() && fixture.layout.getHoles().isEmpty(),
            "capture fixture begins as a valid undrilled and unrouted placement");
        Run baseline=run(fixture,null,NOOP,PcbLayerRoutingPrototype.MAX_EXPANSIONS,diagnostics);
        check(baseline.summary!=null && baseline.firstResult!=null &&
            baseline.firstResult.accepted() && baseline.firstResult.expansions>0 &&
            baseline.firstResult.expansions<PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            "pristine literal fixture supplies a complete first candidate before the bounded failure canary");
        check(!baseline.summary.guidanceStarted && !baseline.summary.comparisonComplete &&
            baseline.summary.orderings==1 && baseline.summary.completedOrderings==1 &&
            baseline.summary.yieldedOrderings==1 &&
            "FIRST_SUCCESS_UNGUIDED".equals(baseline.summary.termination),
            "unguided literal baseline retains the original first-success stream contract");
        int beforeFailure=-1;
        boolean wallRouted=false;
        for(PcbLayerRoutingPrototype.BranchSnapshot branch:branches) {
            if(branch.pass!=0) continue;
            if(WALL.equals(branch.netId) && "FOUND".equals(branch.terminalReason))
                wallRouted=true;
            if(TREE.equals(branch.netId) && "FOUND".equals(branch.terminalReason)) {
                check(wallRouted,
                    "real foreign wall branch is completed before the first tree branch");
                beforeFailure=branch.totalExpanded;
                break;
            }
        }
        check(beforeFailure>0 && beforeFailure<baseline.firstResult.expansions &&
            beforeFailure<PcbLayerRoutingPrototype.MAX_EXPANSIONS-1,
            "actual pass0 wall and first tree branches provide a stable completed foreign-copper prefix");
        int[] bottomGoalPoint=interiorBottomTracePoint(baseline.firstResult.layout,TREE,
            "TREE_A.1","TREE_B.1");
        return new CaptureCutoff(beforeFailure+1,bottomGoalPoint[0],bottomGoalPoint[1]);
    }

    private static void selectedFailureCopiesLiteralDirectedGraph(CaptureCutoff cutoff) {
        final Fixture fixture=new Fixture();
        final String before=fixture.layout.geometryFingerprint();
        final Capture capture=new Capture(true);
        Run run=run(fixture,capture,NOOP,cutoff.expansionLimit);
        PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph=capture.graph;
        PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage=capture.lineage;
        check(graph!=null,"selected terminal failure publishes a complete graph");
        PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=graph.context();
        check(run.summary!=null && !run.summary.comparisonComplete &&
            run.summary.termination.equals("TOTAL_SEARCH_LIMIT") &&
            run.summary.orderings==1 && run.summary.yieldedOrderings==0 &&
            run.summary.expansions==cutoff.expansionLimit,
            "a reduced request cap follows the completed wall/tree prefix before any full ordering is yielded");
        check(capture.selectCalls==1 && capture.graphCalls==1 && graph!=null && lineage!=null,
            "one selected failed branch produces one complete graph");
        check(capture.events.size()==2 && "select".equals(capture.events.get(0)) &&
            "graph".equals(capture.events.get(1)),
            "lineage selection precedes the single complete graph callback");
        check("TOTAL_SEARCH_LIMIT".equals(lineage.terminalReason()) && lineage.pass()==0 &&
            TREE.equals(lineage.netId()) && lineage.branchOrdinal()>=1,
            "captured lineage is the reduced-cap tree branch after a real foreign-net and tree prefix");
        check(context.layoutSeed()==71L && context.routingSeed()==83L &&
            context.routeRequestIndex()==0 && context.selectedPlacementAttempt()==4 &&
            context.selectedPlacementIndex()==0 &&
            equal(context.eligiblePlacementAttempts(),new int[]{4,2,0}),
            "immutable direct fixture context binds the actual request and eligible ordering");
        check(graph.gridWidth()==79 && graph.gridHeight()==69 && graph.cellCount()==5451 &&
            graph.gridPitch()==10 && graph.originX()==110 && graph.originY()==110 &&
            graph.primaryLayer()==PcbCopperLayer.BOTTOM.ordinal() &&
            graph.initialFacesMask()==3,
            "literal outline maps to a 79-by-69 ten-unit two-face grid");
        int open=cell(graph,300,330);
        check(graph.topStepMaskAt(open)==15 && graph.bottomStepMaskAt(open)==15,
            "unobstructed interior exports all four directed steps on both faces");
        int leftOfForeignWall=cell(graph,490,400),rightOfForeignWall=cell(graph,510,400);
        check((graph.bottomStepMaskAt(leftOfForeignWall)&2)==0 &&
            (graph.bottomStepMaskAt(rightOfForeignWall)&8)==0 &&
            (graph.topStepMaskAt(leftOfForeignWall)&2)!=0 &&
            (graph.topStepMaskAt(rightOfForeignWall)&8)!=0,
            "a real earlier foreign-net route blocks bottom-face crossings while top remains open; bottom="+
                graph.bottomStepMaskAt(leftOfForeignWall)+","+graph.bottomStepMaskAt(rightOfForeignWall)+
                "; top="+graph.topStepMaskAt(leftOfForeignWall)+","+graph.topStepMaskAt(rightOfForeignWall)+
                "; prefix="+lineage.prefixGeometryFingerprint());
        int first=cell(graph,110,110),last=cell(graph,890,790);
        check((graph.topStepMaskAt(first)&9)==0 && (graph.bottomStepMaskAt(first)&9)==0 &&
            (graph.topStepMaskAt(last)&6)==0 && (graph.bottomStepMaskAt(last)&6)==0,
            "grid perimeter edges cannot wrap outside the literal rectangle");

        PcbPadPlacement root=fixture.layout.getPad(lineage.rootPadId());
        PcbPadPlacement start=fixture.layout.getPad(lineage.startPadId());
        PcbPadPlacement unconnected=fixture.layout.getPad("TREE_D.1");
        check(root!=null && start!=null && unconnected!=null &&
            start.getPadId().equals("TREE_C.1") && lineage.reachedPadIds().length==2 &&
            contains(lineage.reachedPadIds(),root.getPadId()) &&
            contains(lineage.reachedPadIds(),"TREE_B.1") &&
            lineage.reachedPthPadIds().length==1 &&
            !contains(lineage.reachedPthPadIds(),root.getPadId()) &&
            contains(lineage.reachedPthPadIds(),"TREE_B.1") &&
            !contains(lineage.reachedPthPadIds(),unconnected.getPadId()),
            "the captured later branch includes its reached PTH root/tree endpoint and excludes unconnected D");
        int rootCell=cell(graph,root.getX(),root.getY());
        int reachedCell=cell(graph,fixture.layout.getPad("TREE_B.1").getX(),
            fixture.layout.getPad("TREE_B.1").getY());
        int unconnectedCell=cell(graph,unconnected.getX(),unconnected.getY());
        check(graph.goalAt(PcbCopperLayer.TOP.ordinal(),rootCell) &&
            graph.goalAt(PcbCopperLayer.BOTTOM.ordinal(),rootCell) &&
            graph.goalAt(PcbCopperLayer.TOP.ordinal(),reachedCell) &&
            graph.goalAt(PcbCopperLayer.BOTTOM.ordinal(),reachedCell),
            "reached plated root and tree endpoint are goals on both real copper faces");
        int priorBottomTreeCell=cell(graph,cutoff.bottomTreeGoalX,cutoff.bottomTreeGoalY);
        check(graph.goalAt(PcbCopperLayer.BOTTOM.ordinal(),priorBottomTreeCell) &&
            !graph.goalAt(PcbCopperLayer.TOP.ordinal(),priorBottomTreeCell),
            "an interior cell of actual prior bottom-face tree copper is a bottom-only graph goal");
        check(!graph.goalAt(PcbCopperLayer.TOP.ordinal(),unconnectedCell) &&
            !graph.goalAt(PcbCopperLayer.BOTTOM.ordinal(),unconnectedCell),
            "an unconnected same-net pad does not become a graph goal");

        int[] counts=lineage.prefixHoleCountsByKind();
        check(lineage.prefixHoleCount()==0 && lineage.prefixViaCount()==0 &&
            counts[PcbBoardHole.Kind.VIA.ordinal()]==0 &&
            counts[PcbBoardHole.Kind.PLATED.ordinal()]==0 &&
            counts[PcbBoardHole.Kind.NON_PLATED.ordinal()]==0,
            "real prefix copy preserves its empty via inventory without seeding forbidden initial holes");
        check(graph.viaAllowedAt(open),
            "an unobstructed interior cell is available for a legal face transition");

        int[] eligible=context.eligiblePlacementAttempts();
        eligible[0]=99;
        String[] reachedCopy=lineage.reachedPthPadIds();
        reachedCopy[0]="MUTATED";
        counts[PcbBoardHole.Kind.VIA.ordinal()]=99;
        check(context.eligiblePlacementAttempts()[0]==4 &&
            contains(lineage.reachedPthPadIds(),"TREE_B.1") &&
            contains(lineage.reachedPadIds(),"TREE_A.1") &&
            lineage.prefixHoleCountsByKind()[PcbBoardHole.Kind.VIA.ordinal()]==0 &&
            graph.topStepMaskAt(open)==15,
            "context and lineage arrays are defensive and graph bits remain immutable");
        check(graph.captureWorkUnits()>1 && graph.copyEntries()>0 && graph.goalProbes()>0 &&
            graph.ordinaryEdgeProbes()>0 && graph.viaProbes()==graph.cellCount() &&
            graph.comparisonEntries()>0 && graph.captureActiveElapsedMillis()>=0,
            "capture exposes measured resumable copy, scan and live-state comparison work");
        check(fixture.layout.geometryFingerprint().equals(before),
            "fixed-prefix observation leaves caller-owned layout geometry unchanged");
    }

    private static void unselectedFailureAndFoundBranchesDoNotCapture(CaptureCutoff cutoff) {
        Fixture blocked=new Fixture();
        Capture decline=new Capture(false);
        Run unselected=run(blocked,decline,NOOP,cutoff.expansionLimit);
        check(unselected.summary!=null && "TOTAL_SEARCH_LIMIT".equals(unselected.summary.termination) &&
            decline.selectCalls==1 && decline.graphCalls==0 &&
            decline.graph==null && decline.events.size()==1 &&
            "select".equals(decline.events.get(0)),
            "a selector that declines the reduced-cap failure emits no graph and completes normally");

        Fixture open=new Fixture();
        Capture accept=new Capture(true);
        Run found=run(open,accept,NOOP,PcbLayerRoutingPrototype.MAX_EXPANSIONS);
        check(found.summary!=null && found.summary.yieldedOrderings==1 &&
            !found.summary.guidanceStarted &&
            "FIRST_SUCCESS_UNGUIDED".equals(found.summary.termination) &&
            accept.selectCalls==0 && accept.graphCalls==0 && accept.events.isEmpty(),
            "the small literal fixture preserves first-success ordering below the original pressure threshold");
    }

    private static void zeroCapacityNoPathRetainsGeometryPermissiveCaptureVias() {
        final ZeroCapacityNoPathFixture fixture=new ZeroCapacityNoPathFixture();
        final String before=fixture.layout.geometryFingerprint();
        final Capture capture=new Capture(true);
        final Vector<PcbLayerRoutingPrototype.BranchSnapshot> terminalSnapshots=
            new Vector<PcbLayerRoutingPrototype.BranchSnapshot>();
        PcbLayerRoutingPrototype.Diagnostics diagnostics=
            new PcbLayerRoutingPrototype.Diagnostics() {
                public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                    terminalSnapshots.add(snapshot);
                }
            };
        PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=
            new PcbLayerRoutingPrototype.FixedPrefixCaptureContext(91L,93L,
                new int[]{4,2,0},4,0,before);
        PcbLayerRoutingPrototype.Session session=
            PcbLayerRoutingPrototype.beginOrderingCandidatesWithFixedPrefixObservation(
                fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
                NOOP,PcbLayerRoutingPrototype.MAX_EXPANSIONS,0,diagnostics,context,capture);
        int calls=0,yields=0;
        while(true) {
            PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(256);
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY) {
                PcbLayerRoutingPrototype.Result result=session.takeReadyOrdering();
                check(result!=null && result.accepted() && result.layout!=null,
                    "capacity-bound observation streams only complete accepted route results");
                yields++;
            } else if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE) break;
            if(++calls>100000) throw new AssertionError("zero-capacity fixture exceeded its bounded slice guard");
        }
        PcbLayerRoutingPrototype.OrderingSummary summary=session.orderingSummary();
        check(summary!=null && capture.selectCalls==1 && capture.graphCalls==1 &&
            capture.lineage!=null && capture.graph!=null,
            "a zero-capacity terminal failure publishes one selected fixed-prefix graph");
        check("NO_PATH".equals(capture.lineage.terminalReason()) &&
            capture.lineage.prefixHoleCount()==0 && yields==0,
            "a fully proved zero-via branch drains through the existing NO_PATH terminal");
        PcbLayerRoutingPrototype.BranchSnapshot required=null;
        for(PcbLayerRoutingPrototype.BranchSnapshot snapshot:terminalSnapshots)
            if(snapshot.pass==0 && "00_REQUIRED".equals(snapshot.netId)) {
                required=snapshot;
                break;
            }
        int pressureThreshold=PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS-
            PcbLayerRoutingPrototype.WORK_QUANTUM;
        check(required!=null && "NO_PATH".equals(required.terminalReason) &&
            required.prefixViaCount==0 && required.relaxedBoundStarted &&
            required.relaxedBoundComplete && required.relaxedBoundObservationChecked &&
            required.relaxedBoundLiveLayoutStable && required.relaxedBoundRouterStateStable &&
            required.relaxedBoundRouterEffectsStable && required.branchExpanded==pressureThreshold &&
            required.branchExpanded<fixture.directionalStateLowerBound,
            "original late pressure trigger completes all comparisons and prunes the drained zero-via branch");
        check(summary.guidanceStarted,
            "the original pressure-triggered bound retains its ordering-guidance activation");
        int open=cell(capture.graph,230,220);
        check(capture.graph.gridWidth()==239 && capture.graph.gridHeight()==139 &&
            capture.graph.cellCount()==33221 && capture.graph.viaAllowedAt(open),
            "captured geometry retains its bounded literal grid and permits an open VIA despite zero request capacity");
        check(fixture.layout.geometryFingerprint().equals(before),
            "zero-capacity bound and graph capture leave the caller-owned layout unchanged");
    }

    private static void zeroCapacityNormalOwnerPerformsComparisonsWithoutDiagnostics() {
        final ZeroCapacityNoPathFixture fixture=new ZeroCapacityNoPathFixture();
        final String geometryBefore=fixture.layout.geometryFingerprint();
        final int pressureThreshold=PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS-
            PcbLayerRoutingPrototype.WORK_QUANTUM;
        PressureBoundObserver observer=new PressureBoundObserver(
            PressureBoundObserver.NORMAL_UNDIAGNOSED_COMPLETION,null);
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(
            fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            observer,pressureThreshold+1,0);
        observer.attach(session);

        int slices=0;
        while(!session.advanceSlice(observer.distanceInitializationComplete()?1:256))
            if(++slices>100000)
                throw new AssertionError("normal no-diagnostic pressure canary exceeded its slice guard");
        PcbLayerRoutingPrototype.Result result=session.result();
        check(fieldValue(session,"diagnostics")==null &&
            fieldValue(session,"fixedPrefixGraphObserver")==null &&
            fieldValue(session,"fixedPrefixContext")==null,
            "comparison canary uses the ordinary owner without a diagnostics or capture listener");
        check(result!=null && !result.accepted() &&
            result.outcome.endsWith(":TOTAL_SEARCH_LIMIT"),
            "ordinary no-diagnostics routing retains the existing terminal request-cap result");
        check(observer.sawOriginalPressurePredicate && observer.sawNoDiagnosticChecks &&
            observer.sawCompleteField && observer.sawDistanceFieldComplete &&
            observer.sawCompleteComparisons && observer.sawQueueBeforeFirstPop,
            "ordinary zero-capacity routing records all postchecks before its first post-build pop");
        check(observer.branch!=null && booleanField(observer.branch,"terminalEmitted") &&
            queueSize(fieldValue(observer.branch,"queue"))==0 &&
            intField(observer.branch,"expanded")==observer.preBuildExpanded &&
            observer.workingLayoutUnchanged() && observer.liveFacesUnchanged(),
            "the uninstrumented pass drains only proved-unreachable states without changing route or live graph state");
        check(fixture.layout.geometryFingerprint().equals(geometryBefore),
            "ordinary no-diagnostics comparison leaves the caller-owned layout unchanged");
    }

    private static void pressureRelaxedBoundAbortContracts() {
        final RuntimeException buildCancellation=
            new IllegalStateException("zero-capacity relaxed pressure-build cancellation sentinel");
        PressureBoundObserver incomplete=new PressureBoundObserver(
            PressureBoundObserver.DURING_INCOMPLETE_DISTANCE,buildCancellation);
        assertPressureBoundAbort(new ZeroCapacityNoPathFixture(),incomplete,buildCancellation,
            "cancellation during pressure-triggered incremental distance initialization keeps the field incomplete");
        check(incomplete.sawStartedIncomplete && incomplete.sawPartialDistanceField &&
            !incomplete.sawCompleteField,
            "incomplete-field cancellation is injected only after real pressure and field work begin");

        final Error completedCancellation=
            new AssertionError("zero-capacity completed-field cancellation sentinel");
        PressureBoundObserver completed=new PressureBoundObserver(
            PressureBoundObserver.COMPLETE_BEFORE_FIRST_POP,completedCancellation);
        assertPressureBoundAbort(new ZeroCapacityNoPathFixture(),completed,completedCancellation,
            "observer cancellation at the pressure-built completed-field boundary prevents unreachable pruning");
        check(completed.sawStartedIncomplete && completed.sawCompleteField &&
            completed.sawDistanceFieldComplete &&
            completed.sawCompleteComparisons &&
            completed.sawQueueBeforeFirstPop,
            "completed-field cancellation follows all clone/live comparisons and precedes the first post-build pop");
    }

    private static void pressureBoundCapacityGuardsPrecedeUnreachablePruning() {
        int maximum=PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS-
            PcbLayerRoutingPrototype.WORK_QUANTUM+1;
        assertPressureBoundCapacityGuard(new ZeroCapacityNoPathFixture(),
            PressureBoundObserver.TOTAL_CAP_BEFORE_PRUNE,"TOTAL_SEARCH_LIMIT",maximum);
        assertPressureBoundCapacityGuard(new ZeroCapacityNoPathFixture(),
            PressureBoundObserver.BRANCH_CAP_BEFORE_PRUNE,"BRANCH_SEARCH_LIMIT",maximum);
    }

    private static void assertPressureBoundCapacityGuard(ZeroCapacityNoPathFixture fixture,
            int target,String expectedTerminal,int maximum) {
        final String geometryBefore=fixture.layout.geometryFingerprint();
        final Capture capture=new Capture(true);
        final Vector<PcbLayerRoutingPrototype.BranchSnapshot> terminalSnapshots=
            new Vector<PcbLayerRoutingPrototype.BranchSnapshot>();
        PcbLayerRoutingPrototype.Diagnostics diagnostics=
            new PcbLayerRoutingPrototype.Diagnostics() {
                public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                    terminalSnapshots.add(snapshot);
                }
            };
        PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=
            new PcbLayerRoutingPrototype.FixedPrefixCaptureContext(91L,93L,
                new int[]{4,2,0},4,0,geometryBefore);
        PressureBoundObserver observer=new PressureBoundObserver(target,null);
        PcbLayerRoutingPrototype.Session session=
            PcbLayerRoutingPrototype.beginOrderingCandidatesWithFixedPrefixObservation(
                fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
                observer,maximum,0,diagnostics,context,capture);
        observer.attach(session);

        int slices=0;
        while(true) {
            int work=observer.distanceInitializationComplete()?1:256;
            PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(work);
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY)
                session.takeReadyOrdering();
            else if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE) break;
            if(++slices>100000)
                throw new AssertionError("pressure cap-precedence canary exceeded its bounded slice guard");
        }
        PcbLayerRoutingPrototype.OrderingSummary summary=session.orderingSummary();
        PcbLayerRoutingPrototype.BranchSnapshot required=null;
        for(PcbLayerRoutingPrototype.BranchSnapshot snapshot:terminalSnapshots)
            if(snapshot.pass==0 && "00_REQUIRED".equals(snapshot.netId)) {
                required=snapshot;
                break;
            }
        check(summary!=null && observer.capInjected && observer.sawOriginalPressurePredicate &&
            observer.sawCompleteComparisons && observer.sawQueueBeforeFirstPop,
            "cap canary exhausts the selected budget only after the pressure field and comparisons complete");
        check(required!=null && expectedTerminal.equals(required.terminalReason) &&
            required.relaxedBoundComplete && required.relaxedBoundObservationChecked &&
            required.relaxedBoundLiveLayoutStable && required.relaxedBoundRouterStateStable &&
            required.relaxedBoundRouterEffectsStable && required.frontierCount>0,
            "the exhausted request or branch cap wins before proved-unreachable states can be drained as NO_PATH");
        check(capture.lineage!=null && expectedTerminal.equals(capture.lineage.terminalReason()) &&
            capture.graph!=null && fixture.layout.geometryFingerprint().equals(geometryBefore),
            "cap terminal capture preserves its true failure reason and caller geometry");
    }

    private static void assertPressureBoundAbort(ZeroCapacityNoPathFixture fixture,
            PressureBoundObserver observer,Throwable expected,String message) {
        final String geometryBefore=fixture.layout.geometryFingerprint();
        final Capture capture=new Capture(true);
        final Vector<PcbLayerRoutingPrototype.BranchSnapshot> terminalSnapshots=
            new Vector<PcbLayerRoutingPrototype.BranchSnapshot>();
        PcbLayerRoutingPrototype.Diagnostics diagnostics=
            new PcbLayerRoutingPrototype.Diagnostics() {
                public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                    terminalSnapshots.add(snapshot);
                }
            };
        PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=
            new PcbLayerRoutingPrototype.FixedPrefixCaptureContext(91L,93L,
                new int[]{4,2,0},4,0,geometryBefore);
        PcbLayerRoutingPrototype.Session session=
            PcbLayerRoutingPrototype.beginOrderingCandidatesWithFixedPrefixObservation(
                fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
                observer,PcbLayerRoutingPrototype.MAX_EXPANSIONS,0,diagnostics,context,capture);
        observer.attach(session);

        Throwable caught=null;
        int slices=0;
        try {
            while(true) {
                int work=observer.distanceInitializationComplete()?1:256;
                PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(work);
                if(progress==PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY)
                    session.takeReadyOrdering();
                else if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE) break;
                if(++slices>100000)
                    throw new AssertionError("pressure relaxed-bound cancellation exceeded its bounded slice guard");
            }
        } catch(RuntimeException failure) { caught=failure; }
        catch(Error failure) { caught=failure; }
        check(caught==expected,message+" original observer throwable identity; "+
            throwableDetails(caught));
        check(observer.triggered && observer.sawOriginalPressurePredicate &&
            observer.baselineCaptured && observer.preBuildSnapshotUnchanged(),
            "pressure-field canary records its actual pre-build frontier, cache and layout state");
        check(terminalSnapshots.isEmpty() && capture.selectCalls==0 && capture.graphCalls==0 &&
            capture.graph==null && capture.lineage==null,
            "a pressure-field abort emits no terminal NO_PATH receipt or fixed-prefix capture");
        check(fixture.layout.geometryFingerprint().equals(geometryBefore),
            "a pressure-field abort leaves caller-owned layout geometry unchanged");
        check(observer.workingLayoutUnchanged(),
            "a pressure-field abort leaves the active routing-copy geometry unchanged");
        check(observer.liveFacesUnchanged(),
            "query-field cancellation preserves live fixed-graph state and effects on both faces");
        check(fieldValue(session,"diagnosticFailure")==expected &&
            fieldValue(session,"search")==null && fieldValue(session,"candidate")==null &&
            fieldValue(session,"pendingFixedPrefixCapture")==null &&
            fieldValue(session,"pendingFixedPrefixFailure")==null &&
            fieldValue(session,"readyOrdering")==null && fieldValue(session,"orderingTermination")==null &&
            !booleanField(session,"fixedPrefixCaptureReserved") && !booleanField(session,"complete"),
            "session latches the failure and releases active candidate/search/capture ownership");

        Throwable laterAdvance=null;
        try { session.advanceOrderingSlice(1); }
        catch(RuntimeException failure) { laterAdvance=failure; }
        catch(Error failure) { laterAdvance=failure; }
        Throwable laterSummary=null;
        try { session.orderingSummary(); }
        catch(RuntimeException failure) { laterSummary=failure; }
        catch(Error failure) { laterSummary=failure; }
        check(laterAdvance==expected && laterSummary==expected,
            "pressure-field failures remain latched and cannot revive routing or publish a summary");
    }

    private static void resumableCopyIsIsolatedAndStalenessIsDetectable() {
        Fixture fixture=new Fixture();
        PcbNetRouter.Router live=new PcbNetRouter.Router(fixture.layout,fixture.board,OUTLINE,
            0,NOOP,PcbCopperLayer.TOP,0);
        String effectsBefore=live.fixedGraphObservationEffects();
        PcbNetRouter.Router.FixedGraphCopyTask task=live.beginFixedGraphObservationCopy();
        int previousEntries=0,calls=0;
        boolean complete=false;
        while(!complete) {
            complete=task.advanceOne(73);
            int copied=task.copiedEntries();
            check(copied>=previousEntries && copied-previousEntries<=73,
                "each resumable router-copy call copies at most its requested entry chunk");
            previousEntries=copied;
            calls++;
            if(calls==1) {
                boolean premature=false;
                try { task.result(); } catch(IllegalStateException expected) { premature=true; }
                check(premature,"incomplete router copy cannot publish a result");
            }
            check(calls<10000,"router-copy task yields within its bounded fixture guard");
        }
        PcbNetRouter.Router copy=task.result();
        check(calls>1 && task.copiedEntries()>0 && copy.sameFixedGraphObservation(live) &&
            effectsBefore.equals(live.fixedGraphObservationEffects()),
            "completed chunked copy is an exact isolated fixed-predicate snapshot");
        Vector<Point> mutation=new Vector<Point>();
        mutation.add(new Point(110,110));
        live.occupyLayerPath(mutation,TREE);
        check(!copy.sameFixedGraphObservation(live) &&
            copy.sameFixedGraphObservation(copy),
            "source mutation is detectable against the immutable completed copy");
    }

    private static void sinkFailureAndCallbackCancellationStayLatched(CaptureCutoff cutoff) {
        final RuntimeException sinkFailure=new IllegalStateException("fixed-prefix sink sentinel");
        final Capture sink=new Capture(true) {
            @Override public void onGraph(PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph) {
                graphCalls++;
                throw sinkFailure;
            }
        };
        assertLatchedFailure(new Fixture(),sink,NOOP,sinkFailure,cutoff.expansionLimit,
            "sink failure is rethrown on later session calls");

        final RuntimeException cancelFailure=new IllegalStateException("fixed-prefix callback cancel sentinel");
        final boolean[] armed={false};
        final SeededPcbLayoutGenerator.AttemptObserver cancelObserver=
            new SeededPcbLayoutGenerator.AttemptObserver() {
                public void check(int attempt) { if(armed[0]) throw cancelFailure; }
            };
        final Capture cancel=new Capture(true) {
            @Override public void onGraph(PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph) {
                super.onGraph(graph);
                armed[0]=true;
            }
        };
        assertLatchedFailure(new Fixture(),cancel,cancelObserver,cancelFailure,cutoff.expansionLimit,
            "callback-triggered cancellation is checked immediately and remains latched");
        check(cancel.graphCalls==1 && armed[0],
            "callback cancellation occurs after a complete immutable graph callback");
    }

    private static void assertLatchedFailure(Fixture fixture,Capture capture,
            SeededPcbLayoutGenerator.AttemptObserver observer,RuntimeException expected,
            int expansionLimit,String message) {
        PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=context(fixture);
        PcbLayerRoutingPrototype.Session session=
            PcbLayerRoutingPrototype.beginOrderingCandidatesWithFixedPrefixObservation(
                fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
                observer,expansionLimit,0,null,context,capture);
        RuntimeException caught=null;
        int calls=0;
        try {
            while(true) {
                PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(128);
                if(progress==PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY)
                    session.takeReadyOrdering();
                else if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE) break;
                if(++calls>100000) throw new AssertionError("fatal capture fixture exceeded its slice guard");
            }
        } catch(RuntimeException failure) { caught=failure; }
        check(caught==expected,message+" original throwable identity");
        RuntimeException advanceFailure=null;
        try { session.advanceOrderingSlice(1); }
        catch(RuntimeException failure) { advanceFailure=failure; }
        RuntimeException summaryFailure=null;
        try { session.orderingSummary(); }
        catch(RuntimeException failure) { summaryFailure=failure; }
        check(advanceFailure==expected && summaryFailure==expected,
            "fatal fixed-prefix observer failure prevents route revival and summary publication");
    }

    private static Run run(Fixture fixture,Capture capture,
            SeededPcbLayoutGenerator.AttemptObserver observer,int maximum) {
        return run(fixture,capture,observer,maximum,null);
    }

    private static Run run(Fixture fixture,Capture capture,
            SeededPcbLayoutGenerator.AttemptObserver observer,int maximum,
            PcbLayerRoutingPrototype.Diagnostics diagnostics) {
        PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=context(fixture);
        PcbLayerRoutingPrototype.Session session=capture==null?
            PcbLayerRoutingPrototype.beginOrderingCandidates(fixture.board,fixture.layout,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,observer,maximum,0,diagnostics):
            PcbLayerRoutingPrototype.beginOrderingCandidatesWithFixedPrefixObservation(
                fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
                observer,maximum,0,diagnostics,context,capture);
        int calls=0,ready=0;
        PcbLayerRoutingPrototype.Result firstResult=null;
        while(true) {
            PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(256);
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY) {
                PcbLayerRoutingPrototype.Result result=session.takeReadyOrdering();
                check(result!=null && result.accepted() && result.layout!=null,
                    "ready P07 stream result is a complete routed layout");
                if(firstResult==null) firstResult=result;
                ready++;
            } else if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE) break;
            if(++calls>100000) throw new AssertionError("fixed-prefix fixture exceeded its bounded slice guard");
        }
        PcbLayerRoutingPrototype.OrderingSummary summary=session.orderingSummary();
        return new Run(summary,ready,firstResult);
    }

    private static PcbLayerRoutingPrototype.FixedPrefixCaptureContext context(Fixture fixture) {
        return new PcbLayerRoutingPrototype.FixedPrefixCaptureContext(71L,83L,
            new int[]{4,2,0},4,0,fixture.layout.geometryFingerprint());
    }

    private static int cell(PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph,int x,int y) {
        int dx=x-graph.originX(),dy=y-graph.originY();
        check(dx>=0 && dy>=0 && dx%graph.gridPitch()==0 && dy%graph.gridPitch()==0,
            "literal fixture coordinate maps exactly onto the captured grid");
        int gx=dx/graph.gridPitch(),gy=dy/graph.gridPitch();
        check(gx<graph.gridWidth() && gy<graph.gridHeight(),
            "literal fixture coordinate lies within the captured grid");
        return gy*graph.gridWidth()+gx;
    }

    private static int[] interiorBottomTracePoint(PcbBoardLayout layout,String net,
            String firstPad,String secondPad) {
        for(PcbTraceGeometry trace:layout.getTraces()) {
            if(!net.equals(trace.getNetId())) continue;
            boolean endpoints=(firstPad.equals(trace.getStartPadId()) &&
                    secondPad.equals(trace.getEndPadId())) ||
                (secondPad.equals(trace.getStartPadId()) &&
                    firstPad.equals(trace.getEndPadId()));
            if(!endpoints) continue;
            check(trace.getLayer()==PcbCopperLayer.BOTTOM,
                "primary tree branch is routed on its declared BOTTOM face");
            int[] xs=trace.getXPoints(),ys=trace.getYPoints();
            for(int index=1;index<xs.length;index++) {
                int x0=xs[index-1],x1=xs[index],y0=ys[index-1],y1=ys[index];
                int x,y;
                if(y0==y1 && Math.abs(x1-x0)>=2*PcbLayerRoutingPrototype.GRID) {
                    x=x0+(x1-x0)/2; y=y0;
                } else if(x0==x1 && Math.abs(y1-y0)>=2*PcbLayerRoutingPrototype.GRID) {
                    x=x0; y=y0+(y1-y0)/2;
                } else continue;
                if((x-OUTLINE.x-PcbLayerRoutingPrototype.GRID)%PcbLayerRoutingPrototype.GRID==0 &&
                    (y-OUTLINE.y-PcbLayerRoutingPrototype.GRID)%PcbLayerRoutingPrototype.GRID==0)
                    return new int[]{x,y};
            }
        }
        throw new AssertionError("No interior grid cell on the actual bottom-face TREE_A/TREE_B trace");
    }

    private static boolean contains(String[] values,String expected) {
        for(String value:values) if(expected.equals(value)) return true;
        return false;
    }

    private static boolean equal(int[] first,int[] second) {
        if(first==null || second==null || first.length!=second.length) return false;
        for(int i=0;i<first.length;i++) if(first[i]!=second[i]) return false;
        return true;
    }

    private static void check(boolean condition,String message) {
        checks++;
        if(!condition) throw new AssertionError("P07 fixed-prefix observation: "+message);
    }

    private static String throwableDetails(Throwable failure) {
        if(failure==null) return "caught=null";
        Throwable cause=failure.getCause();
        return "caught="+failure.getClass().getName()+" message="+failure.getMessage()+
            " cause="+(cause==null?"null":cause.getClass().getName()+" message="+cause.getMessage());
    }

    private static Object fieldValue(Object owner,String name) {
        try {
            Field field=owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch(Exception failure) {
            throw new AssertionError("P07 early-bound fixture could not inspect "+name,failure);
        }
    }

    private static boolean booleanField(Object owner,String name) {
        try {
            Field field=owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(owner);
        } catch(Exception failure) {
            throw new AssertionError("P07 early-bound fixture could not inspect "+name,failure);
        }
    }

    private static int intField(Object owner,String name) {
        try {
            Field field=owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(owner);
        } catch(Exception failure) {
            throw new AssertionError("P07 early-bound fixture could not inspect "+name,failure);
        }
    }

    private static void setIntField(Object owner,String name,int value) {
        try {
            Field field=owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(owner,value);
        } catch(Exception failure) {
            throw new AssertionError("P07 pressure-bound fixture could not set "+name,failure);
        }
    }

    private static int queueSize(Object queue) {
        try {
            Method method=queue.getClass().getDeclaredMethod("size");
            method.setAccessible(true);
            return ((Integer)method.invoke(queue)).intValue();
        } catch(Exception failure) {
            throw new AssertionError("P07 early-bound fixture could not inspect the live frontier",failure);
        }
    }

    private static boolean booleanMethod(Object owner,String name) {
        try {
            Method method=owner.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return ((Boolean)method.invoke(owner)).booleanValue();
        } catch(Exception failure) {
            throw new AssertionError("P07 early-bound fixture could not inspect "+name,failure);
        }
    }

    private static int intMethod(Object owner,String name) {
        try {
            Method method=owner.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return ((Integer)method.invoke(owner)).intValue();
        } catch(Exception failure) {
            throw new AssertionError("P07 early-bound fixture could not inspect "+name,failure);
        }
    }

    private static final class PressureBoundObserver implements SeededPcbLayoutGenerator.AttemptObserver {
        static final int DURING_INCOMPLETE_DISTANCE=1,COMPLETE_BEFORE_FIRST_POP=2,
            TOTAL_CAP_BEFORE_PRUNE=3,BRANCH_CAP_BEFORE_PRUNE=4,
            NORMAL_UNDIAGNOSED_COMPLETION=5;
        final int target;
        final Throwable sentinel;
        PcbLayerRoutingPrototype.Session session;
        Object branch;
        Object searchOwner;
        Object preBuildBest;
        Object[] preBuildQueueNodes,preBuildBestNodes;
        int[] preBuildBestKeys;
        PcbBoardLayout workingLayout;
        int preBuildQueueSize,preBuildBestSize,preBuildExpanded,preBuildSerial;
        String preBuildGeometry;
        PcbNetRouter.Router[] liveFaces,pristineFaces;
        String[] liveEffectsBefore;
        boolean capturing,baselineCaptured,triggered;
        boolean sawOriginalPressurePredicate;
        boolean capInjected;
        boolean sawNoDiagnosticChecks;
        boolean sawStartedIncomplete,sawPartialDistanceField,sawCompleteField,
            sawDistanceFieldComplete,sawCompleteComparisons,sawQueueBeforeFirstPop;

        PressureBoundObserver(int target,Throwable sentinel) {
            boolean cancellationTarget=target==DURING_INCOMPLETE_DISTANCE ||
                target==COMPLETE_BEFORE_FIRST_POP;
            boolean capacityTarget=target==TOTAL_CAP_BEFORE_PRUNE || target==BRANCH_CAP_BEFORE_PRUNE;
            boolean normalTarget=target==NORMAL_UNDIAGNOSED_COMPLETION;
            if((!cancellationTarget && !capacityTarget && !normalTarget) ||
                    cancellationTarget && !(sentinel instanceof RuntimeException) &&
                        !(sentinel instanceof Error) || capacityTarget && sentinel!=null)
                throw new IllegalArgumentException("Invalid pressure-bound lifecycle canary");
            this.target=target; this.sentinel=sentinel;
        }
        void attach(PcbLayerRoutingPrototype.Session session) { this.session=session; }
        public void check(int attempt) {
            if(capturing || triggered || session==null) return;
            Object search=fieldValue(session,"search");
            if(search==null) return;
            Object currentBranch=fieldValue(search,"branch");
            if(currentBranch==null) return;
            if(target==NORMAL_UNDIAGNOSED_COMPLETION && sawCompleteField &&
                    branch!=currentBranch) return;
            if(!booleanField(currentBranch,"relaxedBoundStarted")) {
                if(booleanMethod(currentBranch,"shouldStartRelaxedBound")) {
                    sawOriginalPressurePredicate=true;
                    branch=currentBranch;
                    if(!baselineCaptured) capturePreBuild(search);
                }
                return;
            }
            if(branch!=currentBranch)
                throw new AssertionError("Pressure relaxed-bound branch changed before its abort canary");
            boolean complete=booleanField(branch,"relaxedBoundComplete");
            if(!complete) sawStartedIncomplete=true;
            if(!baselineCaptured)
                throw new AssertionError("Pressure relaxed-bound field started without its pre-build snapshot");
            if(target==DURING_INCOMPLETE_DISTANCE && !complete) {
                Object distanceBound=fieldValue(branch,"relaxedDistanceBound");
                if(distanceBound!=null && !booleanMethod(distanceBound,"isComplete") &&
                        intMethod(distanceBound,"getWorkUnits")>=1) {
                    sawPartialDistanceField=true;
                    abort();
                }
            }
            if(complete) {
                sawCompleteField=true;
                Object distanceBound=fieldValue(branch,"relaxedDistanceBound");
                sawDistanceFieldComplete=distanceBound!=null && booleanMethod(distanceBound,"isComplete");
                sawCompleteComparisons=booleanField(branch,"relaxedBoundObservationChecked") &&
                    booleanField(branch,"relaxedBoundLiveLayoutStable") &&
                    booleanField(branch,"relaxedBoundRouterStateStable") &&
                    booleanField(branch,"relaxedBoundRouterEffectsStable");
                if(preBuildSnapshotUnchanged()) sawQueueBeforeFirstPop=true;
                if(target==NORMAL_UNDIAGNOSED_COMPLETION && sawDistanceFieldComplete &&
                        sawCompleteComparisons && sawQueueBeforeFirstPop)
                    sawNoDiagnosticChecks=true;
                if(target==COMPLETE_BEFORE_FIRST_POP && sawQueueBeforeFirstPop) abort();
                if((target==TOTAL_CAP_BEFORE_PRUNE || target==BRANCH_CAP_BEFORE_PRUNE) &&
                        !capInjected && sawDistanceFieldComplete && sawCompleteComparisons &&
                        sawQueueBeforeFirstPop) {
                    if(target==TOTAL_CAP_BEFORE_PRUNE) {
                        Object budget=fieldValue(session,"budget");
                        setIntField(budget,"expanded",intField(budget,"maximum"));
                    } else setIntField(branch,"expanded",
                        PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS);
                    capInjected=true;
                }
            }
        }
        private void capturePreBuild(Object search) {
            Object queue=fieldValue(branch,"queue");
            preBuildBest=fieldValue(branch,"best");
            preBuildQueueSize=queueSize(queue);
            preBuildBestSize=intMethod(preBuildBest,"size");
            preBuildQueueNodes=copyObjects((Object[])fieldValue(queue,"values"));
            preBuildBestNodes=copyObjects((Object[])fieldValue(preBuildBest,"values"));
            preBuildBestKeys=copyInts((int[])fieldValue(preBuildBest,"keys"));
            preBuildExpanded=intField(branch,"expanded");
            searchOwner=search;
            preBuildSerial=intField(searchOwner,"serial");
            workingLayout=(PcbBoardLayout)fieldValue(search,"layout");
            preBuildGeometry=workingLayout.geometryFingerprint();
            captureLiveFaces(search);
        }
        private void captureLiveFaces(Object search) {
            capturing=true;
            try {
                liveFaces=(PcbNetRouter.Router[])fieldValue(search,"faces");
                pristineFaces=new PcbNetRouter.Router[2];
                liveEffectsBefore=new String[2];
                for(int layer=0;layer<2;layer++) {
                    liveEffectsBefore[layer]=liveFaces[layer].fixedGraphObservationEffects();
                    pristineFaces[layer]=liveFaces[layer].copyForFixedGraphObservation();
                    if(!liveFaces[layer].sameFixedGraphObservation(pristineFaces[layer]) ||
                            !liveEffectsBefore[layer].equals(liveFaces[layer].fixedGraphObservationEffects()))
                        throw new AssertionError("Early bound baseline copy changed a live router face");
                }
                baselineCaptured=true;
            } finally { capturing=false; }
        }
        boolean preBuildSnapshotUnchanged() {
            if(!baselineCaptured || branch==null || searchOwner==null || preBuildBest==null ||
                    workingLayout==null)
                return false;
            Object queue=fieldValue(branch,"queue"),best=fieldValue(branch,"best");
            return queueSize(queue)==preBuildQueueSize && intMethod(best,"size")==preBuildBestSize &&
                sameObjects(fieldValue(queue,"values"),preBuildQueueNodes) &&
                sameObjects(fieldValue(best,"values"),preBuildBestNodes) &&
                sameInts(fieldValue(best,"keys"),preBuildBestKeys) &&
                intField(branch,"expanded")==preBuildExpanded &&
                intField(searchOwner,"serial")==preBuildSerial && workingLayoutUnchanged();
        }
        private static Object[] copyObjects(Object[] values) {
            Object[] copy=new Object[values.length];
            System.arraycopy(values,0,copy,0,values.length);
            return copy;
        }
        private static int[] copyInts(int[] values) {
            int[] copy=new int[values.length];
            System.arraycopy(values,0,copy,0,values.length);
            return copy;
        }
        private static boolean sameObjects(Object current,Object[] snapshot) {
            if(!(current instanceof Object[]) || snapshot==null) return false;
            Object[] values=(Object[])current;
            if(values.length!=snapshot.length) return false;
            for(int index=0;index<values.length;index++)
                if(values[index]!=snapshot[index]) return false;
            return true;
        }
        private static boolean sameInts(Object current,int[] snapshot) {
            if(!(current instanceof int[]) || snapshot==null) return false;
            int[] values=(int[])current;
            if(values.length!=snapshot.length) return false;
            for(int index=0;index<values.length;index++)
                if(values[index]!=snapshot[index]) return false;
            return true;
        }
        boolean workingLayoutUnchanged() {
            return preBuildGeometry!=null && workingLayout!=null &&
                preBuildGeometry.equals(workingLayout.geometryFingerprint());
        }
        boolean distanceInitializationComplete() {
            if(branch==null) return false;
            Object distanceBound=fieldValue(branch,"relaxedDistanceBound");
            return distanceBound!=null && intMethod(distanceBound,"getWorkUnits")>=
                intField(distanceBound,"stateCount");
        }
        boolean liveFacesUnchanged() {
            if(!baselineCaptured || liveFaces==null || pristineFaces==null || liveEffectsBefore==null)
                return false;
            for(int layer=0;layer<2;layer++)
                if(!liveFaces[layer].sameFixedGraphObservation(pristineFaces[layer]) ||
                        !liveEffectsBefore[layer].equals(liveFaces[layer].fixedGraphObservationEffects()))
                    return false;
            return true;
        }
        private void abort() {
            triggered=true;
            if(sentinel instanceof RuntimeException) throw (RuntimeException)sentinel;
            throw (Error)sentinel;
        }
    }

    private static class Capture implements PcbLayerRoutingPrototype.FixedPrefixGraphObserver {
        final boolean accept;
        int selectCalls,graphCalls;
        PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage;
        PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph;
        Capture(boolean accept) { this.accept=accept; }
        public boolean select(PcbLayerRoutingPrototype.FixedPrefixBranchLineage candidate) {
            selectCalls++;
            events.add("select");
            if(!accept) return false;
            lineage=candidate;
            return true;
        }
        public void onGraph(PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot snapshot) {
            graphCalls++;
            events.add("graph");
            graph=snapshot;
        }
        final Vector<String> events=new Vector<String>();
    }

    private static final class Run {
        final PcbLayerRoutingPrototype.OrderingSummary summary;
        final int yields;
        final PcbLayerRoutingPrototype.Result firstResult;
        Run(PcbLayerRoutingPrototype.OrderingSummary summary,int yields,
                PcbLayerRoutingPrototype.Result firstResult) {
            this.summary=summary; this.yields=yields; this.firstResult=firstResult;
        }
    }

    private static final class CaptureCutoff {
        final int expansionLimit,bottomTreeGoalX,bottomTreeGoalY;
        CaptureCutoff(int expansionLimit,int bottomTreeGoalX,int bottomTreeGoalY) {
            this.expansionLimit=expansionLimit;
            this.bottomTreeGoalX=bottomTreeGoalX;
            this.bottomTreeGoalY=bottomTreeGoalY;
        }
    }

    private static final class ZeroCapacityNoPathFixture {
        private static final Rectangle OUTLINE=new Rectangle(100,100,2400,1400);
        private static final int TOP_WALL_X=900,BOTTOM_WALL_X=920;
        private static final int TARGET_A_X=400,TARGET_B_X=1800;
        final TroubleshootBoard board=new TroubleshootBoard("P07_ZERO_CAPACITY_NO_PATH");
        final PcbBoardLayout layout=new PcbBoardLayout(2600,1600,OUTLINE,
            new Rectangle(2520,120,60,1400));
        final int directionalStateLowerBound;
        ZeroCapacityNoPathFixture() {
            PcbLayerRoutingPrototype.GridAdmission grid=
                PcbLayerRoutingPrototype.gridAdmission(OUTLINE);
            int rightWallCell=(BOTTOM_WALL_X+40+PcbLayerRoutingPrototype.GRID-
                (OUTLINE.x+PcbLayerRoutingPrototype.GRID))/PcbLayerRoutingPrototype.GRID;
            directionalStateLowerBound=(grid.width-2-rightWallCell+1)*(grid.height-2)*4-256;
            int pressureThreshold=PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS-
                PcbLayerRoutingPrototype.WORK_QUANTUM;
            check(grid.eligible && grid.width==239 && grid.height==139 &&
                grid.cells==33221 && grid.cells<PcbLayerRoutingPrototype.MAX_CELLS &&
                directionalStateLowerBound>pressureThreshold,
                "literal pressure fixture has a bounded grid and sufficient open-side directional states");
            board.addNet(new BoardNet("00_REQUIRED",BoardNet.RoutingRole.HIGH_CURRENT));
            addPoint("TARGET_A","00_REQUIRED",TARGET_A_X,800,PcbBoardSide.TOP);
            addPoint("TARGET_B","00_REQUIRED",TARGET_B_X,800,PcbBoardSide.TOP);
            int ordinal=0;
            for(PcbBoardSide side:new PcbBoardSide[]{PcbBoardSide.TOP,PcbBoardSide.BOTTOM})
                for(int y=120;y<=1480;y+=20) {
                    String component="BARRIER_"+side+"_"+ordinal;
                    String net="99_"+component;
                    board.addNet(new BoardNet(net));
                    addPoint(component,net,
                        side==PcbBoardSide.TOP?TOP_WALL_X:BOTTOM_WALL_X,y,side);
                    ordinal++;
                }
            Vector<PcbPlacementConstraints.Part> demands=
                new Vector<PcbPlacementConstraints.Part>();
            for(String id:board.getComponentIds())
                demands.add(new PcbPlacementConstraints.Part(id,"p07",
                    "zero-capacity observation", "p07",PcbPlacementConstraints.Anchor.NONE,16));
            board.setPlacementConstraints(new PcbPlacementConstraints(demands,
                new Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.BOTTOM,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION,1,false,
                PcbTraceWidthProfile.UNIFORM));
            board.validate();
            layout.validateAgainst(board);
        }
        private void addPoint(String id,String net,int x,int y,PcbBoardSide side) {
            PhysicalPackage physical=P06FactoryLinkFixtures.TEST_POINT;
            BoardComponent component=new BoardComponent(id,"STRUCTURAL",physical);
            board.addComponent(component);
            board.addPad(new BoardPad(id+".1",id,"1",net));
            PcbPackagePose reference=new PcbPackagePose(100,100,PcbRotation.DEG_0,side);
            Point local=physical.getGeometry().placedAt(reference).getPadPoint(0);
            PcbPackagePose pose=new PcbPackagePose(100+x-local.x,100+y-local.y,
                PcbRotation.DEG_0,side);
            PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,pose,
                physical.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
        }
    }

    private static final class Fixture {
        final TroubleshootBoard board=new TroubleshootBoard("P07_FIXED_PREFIX_LITERAL");
        final PcbBoardLayout layout=new PcbBoardLayout(1200,900,OUTLINE,
            new Rectangle(950,120,200,700));

        Fixture() {
            board.addNet(new BoardNet(WALL,BoardNet.RoutingRole.RETURN));
            board.addNet(new BoardNet(TREE,BoardNet.RoutingRole.HIGH_CURRENT));
            for(String id:new String[]{"A","B","C","D"})
                board.addNet(new BoardNet("90_AUX_"+id));
            addPoint("WALL_A",WALL,500,110);
            addPoint("WALL_B",WALL,500,790);
            addHeader("TREE_A","90_AUX_A",220,400);
            addHeader("TREE_B","90_AUX_B",420,400);
            addHeader("TREE_C","90_AUX_C",760,400);
            addHeader("TREE_D","90_AUX_D",760,600);
            Vector<PcbPlacementConstraints.Part> demands=
                new Vector<PcbPlacementConstraints.Part>();
            for(String id:board.getComponentIds())
                demands.add(new PcbPlacementConstraints.Part(id,"p07","fixed-prefix endpoint",
                    "p07",PcbPlacementConstraints.Anchor.NONE,16));
            board.setPlacementConstraints(new PcbPlacementConstraints(demands,
                new Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.BOTTOM,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION,1,false,
                PcbTraceWidthProfile.UNIFORM));
            board.validate();
            layout.validateAgainst(board);
            requirePristineCourtyards();
        }

        private void requirePristineCourtyards() {
            // A pristine input has no copper yet. Completed-net connectivity is
            // proved by the actual routed candidate; check placement here.
            Vector<PcbComponentPlacement> placements=layout.getComponents();
            for(int i=0;i<placements.size();i++) {
                Rectangle a=placements.get(i).getRoutingCourtyard();
                check(a.x>=OUTLINE.x && a.y>=OUTLINE.y &&
                    (long)a.x+a.width<=(long)OUTLINE.x+OUTLINE.width &&
                    (long)a.y+a.height<=(long)OUTLINE.y+OUTLINE.height,
                    "pristine fixture courtyard lies within its routing outline");
                for(int j=0;j<i;j++) {
                    Rectangle b=placements.get(j).getRoutingCourtyard();
                    boolean overlap=a.x<(long)b.x+b.width && b.x<(long)a.x+a.width &&
                        a.y<(long)b.y+b.height && b.y<(long)a.y+a.height;
                    check(!overlap,"pristine fixture peer courtyards do not overlap");
                }
            }
        }

        private void addHeader(String componentId,String auxiliaryNet,int padX,int padY) {
            PhysicalPackage physical=PhysicalPackages.THROUGH_HOLE_CONNECTOR_2;
            BoardComponent component=new BoardComponent(componentId,"STRUCTURAL",physical);
            board.addComponent(component);
            board.addPad(new BoardPad(componentId+".1",componentId,"1",TREE));
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
