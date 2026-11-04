package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Vector;

/** Literal prerequisite proving a detached owner can replay one real P07 branch. */
public final class P07DetachedBranchReplayContractTest {
    private static final int SOURCE_VIA_CAP=2;
    private static final int POLICY_VIA_CAP=48;
    private static final int REDUCED_VIA_CAP=17;
    private static final int DIAGNOSTIC_VIA_COST=240;
    private static final int MAX_SOURCE_UNITS=2000000;
    private static final int MAX_REPLAY_UNITS=50000;
    private static final int MAX_COPY_WORK_STEPS=2000000;
    private static final int MAX_COPY_QUANTUM=PcbLayerRoutingPrototype.WORK_QUANTUM;
    private static final int SOURCE_PREP_WORK_PER_UNIT=8192;
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
        require(source.viaCost==PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.viaCost &&
                source.viaCost==60,
            "normal FULLER Session/Search inherit the immutable policy VIA cost");

        verifyInvalidRequestedCaps(source);
        verifyInvalidRequestedCosts(source);
        verifyDefaultPolicyCosts(fixture);
        verifyLowerViaCostCounterexample();
        verifyAdvancedFrontierRejectsCostOverride();
        Source higherCapSource=freshSource(POLICY_VIA_CAP);
        require(higherCapSource.maximumVias==POLICY_VIA_CAP && higherCapSource.prefixIds.length==2,
            "higher-cap literal source retains its two-hole initial consumer prefix");
        verifyInvalidRequestedCaps(higherCapSource);
        verifyReducedCapLifecycle(higherCapSource);
        verifyReducedCapCopies(higherCapSource);

        verifyCopyLifecycle(source);
        verifyStaleCopyCanaries();
        verifyChargedRouterCopyFailureCanaries();

        Replay baseline=null,batched=null,entryControl=null,explicitDefault=null,costOverride=null;
        try {
            baseline=runDetached(source,1);
            verifySourceUnchanged(source,"baseline replay");
            batched=runDetached(source,MAX_COPY_QUANTUM);
            verifySourceUnchanged(source,"batched replay");
            explicitDefault=runDetachedAtCapAndCost(source,MAX_COPY_QUANTUM,
                source.maximumVias,source.viaCost);
            verifySourceUnchanged(source,"explicit policy-default cost replay");
            costOverride=runDetachedAtCapAndCost(source,MAX_COPY_QUANTUM,
                source.maximumVias,DIAGNOSTIC_VIA_COST);
            verifySourceUnchanged(source,"diagnostic cost replay");
            require(baseline.maximumVias==source.maximumVias &&
                    batched.maximumVias==source.maximumVias &&
                    explicitDefault.maximumVias==source.maximumVias &&
                    costOverride.maximumVias==source.maximumVias,
                "copy variants preserve the original Session VIA cap");
            verifyDetachedResults(baseline,batched);
            verifyDetachedResults(baseline,explicitDefault);
            require(explicitDefault.viaCost==source.viaCost &&
                    costOverride.viaCost==DIAGNOSTIC_VIA_COST &&
                    intField(costOverride.owner,"viaCost")==DIAGNOSTIC_VIA_COST &&
                    intField(costOverride.search,"viaCost")==DIAGNOSTIC_VIA_COST &&
                    intField(source.owner,"viaCost")==source.viaCost &&
                    intField(source.search,"viaCost")==source.viaCost,
                "cost override is immutable on detached Session/Search and isolated from its source");
            verifyWeightedPathCost(baseline);
            verifyWeightedPathCost(explicitDefault);
            verifyWeightedPathCost(costOverride);
            verifyRelaxedBoundCostPropagation(source);
            verifyCostOverrideUsesViaSearchAndPublisher();
            entryControl=buildDetached(source,MAX_COPY_QUANTUM);
            verifyNegativeControls(source,entryControl);
            verifySourceUnchanged(source,"negative controls");
            verifyExhaustedBudget(source);
            verifySourceUnchanged(source,"exhausted-budget control");

            Replay original=finishOriginalConsumer(source);
            verifySourcePublishedDelta(source,original);
            verifyDetachedResults(baseline,original);
            verifyBranchPhysicalAndConnectivity(baseline);
            verifyBranchPhysicalAndConnectivity(batched);
            verifyBranchPhysicalAndConnectivity(original);
            require(fixture.layout.getHoles().isEmpty() && fixture.layout.getTraces().isEmpty(),
                "all routing remained inside the source Session candidate");
        } finally {
            if(baseline!=null) baseline.handle.release();
            if(batched!=null) batched.handle.release();
            if(entryControl!=null) entryControl.handle.release();
            if(explicitDefault!=null) explicitDefault.handle.release();
            if(costOverride!=null) costOverride.handle.release();
        }
        verifyOrderingContinuationHandoff();
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

    private static Source freshOrderingSource(final ContinuationBudget budget) throws Exception {
        final LiteralFixture fixture=budget.run("source-setup",new Operation<LiteralFixture>() {
            public LiteralFixture run() throws Exception { return literalSourceFixture(); }
        });
        final PcbLayerRoutingPrototype.Session session=budget.run("source-setup",
            new Operation<PcbLayerRoutingPrototype.Session>() {
                public PcbLayerRoutingPrototype.Session run() {
                    return PcbLayerRoutingPrototype.beginOrderingCandidates(
                        fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
                        P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS,
                        POLICY_VIA_CAP,null);
                }
            });
        final int[] steps={0};
        final Object[] capturedSearch={null},capturedBranch={null};
        while(steps[0]<MAX_SOURCE_UNITS && capturedSearch[0]==null) {
            budget.runMeasured("source-prep",new Operation<Void>() {
                public Void run() throws Exception {
                    while(budget.currentUnitWork()<SOURCE_PREP_WORK_PER_UNIT &&
                            steps[0]<MAX_SOURCE_UNITS) {
                        Object search=field(session,"search");
                        if(search!=null) {
                            Object branch=field(search,"branch");
                            if(branch!=null && intField(search,"pass")==0 &&
                                    intField(branch,"branchOrdinal")==4 &&
                                    "UNDER".equals(stringField(search,"currentNet")) &&
                                    "C.1".equals(((PcbPadPlacement)field(branch,"start")).getPadId())) {
                                capturedSearch[0]=search; capturedBranch[0]=branch;
                                return null;
                            }
                        }
                        PcbLayerRoutingPrototype.OrderingProgress progress;
                        try { progress=session.advanceOrderingSlice(1); }
                        finally { budget.recordWork(session.lastWork()); }
                        int used=session.lastWork();
                        require(used>=0 && used<=1,
                            "real ordering source preparation charges at most its one-step request");
                        steps[0]++;
                        if(progress!=PcbLayerRoutingPrototype.OrderingProgress.WORKING)
                            throw new AssertionError("real ordering source ended before its consumer branch");
                        if(used==0) break;
                    }
                    return null;
                }
            });
        }
        if(capturedSearch[0]!=null) {
            final Object search=capturedSearch[0],branch=capturedBranch[0];
            final int sourceUnits=steps[0];
            return budget.run("source-setup",new Operation<Source>() {public Source run()
                    throws Exception {
                return new Source(fixture,session,search,branch,sourceUnits);
            }});
        }
        throw new AssertionError("ordering source exceeded its bounded work-unit capture guard");
    }

    private static void verifyOrderingContinuationHandoff() throws Exception {
        final ContinuationBudget budget=new ContinuationBudget();
        final Source source=freshOrderingSource(budget);
        final Source control=freshOrderingSource(budget);
        require(source.maximumVias==POLICY_VIA_CAP && source.viaCost==
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.viaCost && source.viaCost==60 &&
                source.prefixIds.length==SOURCE_VIA_CAP,
            "real ordering source retains policy cost sixty, cap forty-eight and its two-VIA prefix");

        final Replay early=buildDetached(source,MAX_COPY_QUANTUM,POLICY_VIA_CAP,true,
            source.viaCost,true,null,budget);
        try {
            final Object earlySearch=early.search;
            final PcbBoardLayout earlyLayout=early.layout;
            expectLifecycleRejected(budget,"handoff",new Checked(){public void run() {
                early.handle.takeOrderingContinuation();
            }},"early continuation handoff is rejected before Publisher completion");
            require(early.handle.getSearchForDiagnostics()==earlySearch &&
                    early.handle.getLayout()==earlyLayout && !early.handle.isComplete(),
                "rejected early handoff preserves the live detached branch owner");
        } finally { early.handle.release(); }

        final PcbLayerRoutingPrototype.Result ordinary=finishCurrentOrdering(control,budget);
        budget.run("oracle",new Operation<Void>() {public Void run() {
            verifyFullOrderingLayout(control.fixture.board,ordinary.layout,"ordinary cost-sixty route");
            return null;
        }});
        final GateObserver defaultGate=new GateObserver();
        final Replay defaultCost=buildDetached(source,MAX_COPY_QUANTUM,POLICY_VIA_CAP,true,
            source.viaCost,true,defaultGate,budget);
        final Source raisedSource=freshOrderingSource(budget);
        final Replay raisedCost=buildDetached(raisedSource,MAX_COPY_QUANTUM,POLICY_VIA_CAP,true,
            DIAGNOSTIC_VIA_COST,true,null,budget);
        PcbLayerRoutingPrototype.Result continuedDefault=null,continuedRaised=null;
        try {
            completeReplay(defaultCost,MAX_COPY_QUANTUM,budget);
            completeReplay(raisedCost,MAX_COPY_QUANTUM,budget);
            require(field(defaultCost.publisher,"endTerminal")==null &&
                    field(defaultCost.branch,"foundTerminal")==null &&
                    field(raisedCost.publisher,"endTerminal")==null &&
                    field(raisedCost.branch,"foundTerminal")==null,
                "tree-join goal retains a legitimate null terminal through both Publishers");
            require(defaultCost.maximumVias==POLICY_VIA_CAP &&
                    defaultCost.viaCost==source.viaCost &&
                    raisedCost.maximumVias==POLICY_VIA_CAP &&
                    raisedCost.viaCost==DIAGNOSTIC_VIA_COST &&
                    intField(defaultCost.owner,"maximumVias")==POLICY_VIA_CAP &&
                    intField(defaultCost.owner,"viaCost")==60 &&
                    intField(raisedCost.owner,"maximumVias")==POLICY_VIA_CAP &&
                    intField(raisedCost.owner,"viaCost")==DIAGNOSTIC_VIA_COST &&
                    intField(source.owner,"maximumVias")==POLICY_VIA_CAP &&
                    intField(source.owner,"viaCost")==60,
                "copied ordering remainder preserves cap forty-eight and isolates effective cost");

            PcbLayerRoutingPrototype.Session defaultOwner=defaultCost.owner;
            Object defaultSearch=defaultCost.search;
            PcbBoardLayout defaultLayout=defaultCost.layout;
            int sourcePasses=intField(source.owner,"passes");
            int sourceCompleted=intField(source.owner,"completedOrderings");
            int sourceYielded=intField(source.owner,"yieldedOrderings");
            Object sourceBudget=field(source.owner,"budget");
            Object copiedBudget=field(defaultOwner,"budget");
            final PcbLayerRoutingPrototype.DetachedOrderingContinuation defaultContinuation=
                budget.run("handoff",new Operation<PcbLayerRoutingPrototype.DetachedOrderingContinuation>() {
                    public PcbLayerRoutingPrototype.DetachedOrderingContinuation run() {
                        return defaultCost.handle.takeOrderingContinuation();
                    }
                });
            require(field(defaultCost.handle,"owner")==null && field(defaultCost.handle,"search")==null &&
                    field(defaultCost.handle,"layout")==null &&
                    field(defaultContinuation,"owner")==defaultOwner &&
                    field(defaultContinuation,"search")==defaultSearch &&
                    field(defaultContinuation,"layout")==defaultLayout &&
                    field(defaultOwner,"search")==defaultSearch &&
                    field(defaultOwner,"candidate")==defaultLayout &&
                    field(defaultOwner,"board")==field(source.owner,"board") &&
                    field(defaultOwner,"policy")==field(source.owner,"policy") &&
                    field(defaultOwner,"fixedPrefixContext")==field(source.owner,"fixedPrefixContext") &&
                    field(defaultSearch,"rules")==field(source.search,"rules") &&
                    field(defaultSearch,"board")==field(source.search,"board") &&
                    field(defaultSearch,"policy")==field(source.search,"policy") &&
                    field(defaultSearch,"budget")==copiedBudget && copiedBudget!=sourceBudget &&
                    intField(copiedBudget,"maximum")==intField(sourceBudget,"maximum") &&
                    intField(copiedBudget,"expanded")>=intField(sourceBudget,"expanded") &&
                    intField(copiedBudget,"expanded")<=intField(copiedBudget,"maximum") &&
                    booleanField(defaultOwner,"orderingStream") &&
                    intField(defaultOwner,"passes")==sourcePasses &&
                    intField(defaultOwner,"completedOrderings")==sourceCompleted &&
                    intField(defaultOwner,"yieldedOrderings")==sourceYielded,
                "single-use transfer retains the exact copied Session, Search, layout and inherited counters");
            final PcbLayerRoutingPrototype.DetachedOrderingContinuation finalDefault=defaultContinuation;
            final PcbLayerRoutingPrototype.Session finalDefaultOwner=defaultOwner;
            final int[] checksBeforeInvalid={defaultCost.observerChecks[0]};
            expectIllegalArgument(budget,"lifecycle",new Checked(){public void run() {
                finalDefault.advance(0);
            }},
                "detached ordering rejects zero work before observer callbacks");
            expectIllegalArgument(budget,"lifecycle",new Checked(){public void run() {
                finalDefault.advance(32769);
            }},
                "detached ordering rejects work above thirty-two thousand seven hundred sixty-eight");
            require(defaultCost.observerChecks[0]==checksBeforeInvalid[0] &&
                    finalDefault.lastWork()==0 && field(finalDefaultOwner,"search")==defaultSearch,
                "invalid continuation quanta leave owner, Search and observer untouched");
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                finalDefaultOwner.advanceOrderingSlice(1);
            }},"raw Session cannot advance while the continuation owns its ordering");
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                finalDefaultOwner.takeReadyOrdering();
            }},"raw Session cannot steal a continuation route");
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                finalDefaultOwner.orderingSummary();
            }},"raw Session cannot expose an ordinary summary during continuation ownership");
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                defaultCost.handle.takeOrderingContinuation();
            }},"a transferred replay cannot issue a duplicate continuation");
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                defaultCost.handle.advance(1);
            }},"a transferred replay cannot resume branch work");
            budget.run("lifecycle",new Operation<Void>() {public Void run() {
                defaultCost.handle.release(); defaultCost.handle.cancel(); return null;
            }});
            defaultGate.runOnNextCheck(new Runnable(){public void run() {
                try { defaultContinuation.advance(1); }
                catch(IllegalStateException expected) {
                    defaultGate.rejectedAdvanceReentries++;
                    budget.recordNestedControlUnit();
                }
            }});
            ContinuationRun defaultRun=finishContinuation(defaultContinuation,defaultCost,32768,
                defaultGate,budget);
            continuedDefault=defaultRun.result;
            final PcbLayerRoutingPrototype.Result route60=continuedDefault;
            require(defaultGate.rejectedAdvanceReentries==1 && defaultGate.rejectedTakeReentries==1,
                "reentrant continuation advance and take are rejected without consuming nested work");
            require(continuedDefault.layout==defaultLayout && continuedDefault.accepted() &&
                    continuedDefault.orderings==ordinary.orderings &&
                    continuedDefault.expansions==ordinary.expansions &&
                    continuedDefault.vias==ordinary.vias &&
                    PlayerSessionFingerprint.of(continuedDefault.layout.geometryFingerprint()).equals(
                        PlayerSessionFingerprint.of(ordinary.layout.geometryFingerprint())) &&
                    PlayerSessionFingerprint.of(continuedDefault.layout.traceGeometryFingerprint()).equals(
                        PlayerSessionFingerprint.of(ordinary.layout.traceGeometryFingerprint())) &&
                    continuedDefault.layout.captureConductorGraph(source.fixture.board).toCanonical().equals(
                        ordinary.layout.captureConductorGraph(control.fixture.board).toCanonical()),
                "cost-sixty copied remainder matches the real ordinary current-ordering route exactly");
            budget.run("oracle",new Operation<Void>() {public Void run() {
                verifyFullOrderingLayout(source.fixture.board,route60.layout,
                    "continued cost-sixty route");
                return null;
            }});
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                finalDefault.advance(1);
            }},"consuming the current route ends the continuation lease");
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                finalDefault.takeReadyOrdering();
            }},"a current-ordering result can be taken only once");
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                finalDefaultOwner.orderingSummary();
            }},"consumed current-ordering result does not claim ordinary comparison completion");

            final PcbLayerRoutingPrototype.DetachedOrderingContinuation raisedContinuation=
                budget.run("handoff",new Operation<PcbLayerRoutingPrototype.DetachedOrderingContinuation>() {
                    public PcbLayerRoutingPrototype.DetachedOrderingContinuation run() {
                        return raisedCost.handle.takeOrderingContinuation();
                    }
                });
            PcbLayerRoutingPrototype.Session raisedOwner=raisedCost.owner;
            Object raisedSearch=raisedCost.search;
            PcbBoardLayout raisedLayout=raisedCost.layout;
            require(field(raisedContinuation,"owner")==raisedOwner &&
                    field(raisedContinuation,"search")==raisedSearch &&
                    field(raisedContinuation,"layout")==raisedLayout,
                "cost-two-forty transfer does not rebuild or replace the copied Search");
            ContinuationRun raisedRun=finishContinuation(raisedContinuation,raisedCost,32768,null,budget);
            continuedRaised=raisedRun.result;
            final PcbLayerRoutingPrototype.Result route240=continuedRaised;
            require(continuedRaised.accepted() && continuedRaised.layout==raisedLayout &&
                    continuedRaised.expansions==raisedOwner.expansions() &&
                    intField(raisedOwner,"viaCost")==DIAGNOSTIC_VIA_COST &&
                    intField(raisedOwner,"maximumVias")==POLICY_VIA_CAP,
                "cost-two-forty continuation completes the same ordering under its inherited expansion budget");
            budget.run("oracle",new Operation<Void>() {public Void run() {
                verifyFullOrderingLayout(raisedSource.fixture.board,route240.layout,
                    "continued cost-two-forty route");
                return null;
            }});
            budget.run("lifecycle",new Operation<Void>() {public Void run() throws Exception {
                verifySourceUnchanged(source,"cost-sixty and cost-two-forty ordering continuations");
                verifySourceUnchanged(raisedSource,"cost-two-forty ordering continuation");
                return null;
            }});
        } finally {
            defaultCost.handle.release();
            raisedCost.handle.release();
        }

        verifyContinuationCancellation(source,budget);
        verifyStaleContinuation(source,budget);
        verifyContinuationObserverFailure(source,budget);
        verifyReplayReentrancy(source,budget);
        verifyReplayFailureLifecycle(source,budget);
        budget.run("lifecycle",new Operation<Void>() {public Void run() throws Exception {
            verifySourceUnchanged(source,"ordering continuation lifecycle canaries");
            return null;
        }});
        budget.reportAndCheck();
        assertions++;
    }

    private static PcbLayerRoutingPrototype.Result finishCurrentOrdering(final Source source,
            final ContinuationBudget budget) throws Exception {
        for(int calls=0;calls<MAX_COPY_WORK_STEPS;calls++) {
            final int beforeExpanded=source.session.expansions();
            PcbLayerRoutingPrototype.OrderingProgress progress=budget.runMeasured("control",
                new Operation<PcbLayerRoutingPrototype.OrderingProgress>() {
                    public PcbLayerRoutingPrototype.OrderingProgress run() {
                        try { return source.session.advanceOrderingSlice(
                            budget.remainingBatchWork()); }
                        finally { budget.recordWork(source.session.lastWork()); }
                    }
                });
            int used=source.session.lastWork();
            require(used>=0 && used<=ContinuationBudget.MAX_BATCH_WORK &&
                    source.session.expansions()-beforeExpanded<=used,
                "ordinary control owner reports bounded charged work without invented expansions");
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY)
                return budget.run("handoff",new Operation<PcbLayerRoutingPrototype.Result>() {
                    public PcbLayerRoutingPrototype.Result run() {
                        return source.session.takeReadyOrdering();
                    }
                });
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE)
                throw new AssertionError("ordinary literal ordering ended without a route");
        }
        throw new AssertionError("ordinary literal ordering exceeded its bounded completion guard");
    }

    private static ContinuationRun finishContinuation(
            PcbLayerRoutingPrototype.DetachedOrderingContinuation continuation,Replay replay,int quantum)
            throws Exception {
        return finishContinuation(continuation,replay,quantum,null,null);
    }

    private static ContinuationRun finishContinuation(
            final PcbLayerRoutingPrototype.DetachedOrderingContinuation continuation,Replay replay,int quantum,
            final GateObserver reentrancyGate) throws Exception {
        return finishContinuation(continuation,replay,quantum,reentrancyGate,null);
    }

    private static ContinuationRun finishContinuation(
            final PcbLayerRoutingPrototype.DetachedOrderingContinuation continuation,final Replay replay,
            final int quantum,final GateObserver reentrancyGate,final ContinuationBudget budget) throws Exception {
        final long[] totalWork={0};
        final int[] calls={0};
        while(true) {
            final PcbLayerRoutingPrototype.DetachedOrderingProgress[] progressHolder={null};
            if(budget==null) {
                int beforeExpanded=replay.owner.expansions();
                progressHolder[0]=continuation.advance(quantum);
                int used=continuation.lastWork();
                require(used>=0 && used<=quantum && used==replay.owner.lastWork() &&
                        replay.owner.expansions()-beforeExpanded<=used,
                    "continuation reports the exact bounded owner work without inventing expansions");
                if(progressHolder[0]==PcbLayerRoutingPrototype.DetachedOrderingProgress.WORKING)
                    require(used>0,"working continuation makes charged owner progress");
                totalWork[0]+=used;
                if(++calls[0]>MAX_COPY_WORK_STEPS)
                    throw new AssertionError("detached current ordering exceeded its bounded progress guard");
            } else budget.runMeasured("remainder",new Operation<Void>() {
                public Void run() {
                    while(budget.remainingBatchWork()>0) {
                        final int callQuantum=Math.min(quantum,budget.remainingBatchWork());
                        int beforeExpanded=replay.owner.expansions();
                        try { progressHolder[0]=continuation.advance(callQuantum); }
                        finally { budget.recordWork(continuation.lastWork()); }
                        int used=continuation.lastWork();
                        require(used>=0 && used<=callQuantum && used==replay.owner.lastWork() &&
                                replay.owner.expansions()-beforeExpanded<=used,
                            "continuation reports the exact bounded owner work without inventing expansions");
                        totalWork[0]+=used;
                        if(++calls[0]>MAX_COPY_WORK_STEPS)
                            throw new AssertionError("detached current ordering exceeded its bounded progress guard");
                        if(progressHolder[0]!=PcbLayerRoutingPrototype.DetachedOrderingProgress.WORKING ||
                                used==0) break;
                    }
                    return null;
                }
            });
            PcbLayerRoutingPrototype.DetachedOrderingProgress progress=progressHolder[0];
            int used=continuation.lastWork();
            if(progress==PcbLayerRoutingPrototype.DetachedOrderingProgress.ROUTE_READY) {
                if(reentrancyGate!=null) reentrancyGate.runOnNextCheck(new Runnable(){public void run() {
                    try { continuation.takeReadyOrdering(); }
                    catch(IllegalStateException expected) {
                        reentrancyGate.rejectedTakeReentries++;
                        if(budget!=null) budget.recordNestedControlUnit();
                    }
                }});
                PcbLayerRoutingPrototype.Result result=budget==null?
                    continuation.takeReadyOrdering():budget.run("handoff",
                        new Operation<PcbLayerRoutingPrototype.Result>() {
                            public PcbLayerRoutingPrototype.Result run() {
                                return continuation.takeReadyOrdering();
                            }
                        });
                require(continuation.lastWork()==0 && result!=null && result.accepted() &&
                        result.layout!=null && result.expansions==replay.owner.expansions() &&
                        totalWork[0]>0 && calls[0]>0,
                    "continuation exposes one real current-ordering route after zero-work result transfer");
                return new ContinuationRun(result,totalWork[0],calls[0]);
            }
            if(progress==PcbLayerRoutingPrototype.DetachedOrderingProgress.ORDERING_BOUNDARY)
                throw new AssertionError("literal current ordering stopped at a rejection boundary: "+
                    continuation.boundaryReason());
        }
    }

    private static void verifyContinuationCancellation(final Source source,
            final ContinuationBudget budget) throws Exception {
        verifyContinuationActiveCancellation(source,budget);
        final Replay replay=buildDetached(source,MAX_COPY_QUANTUM,POLICY_VIA_CAP,true,
            source.viaCost,true,null,budget);
        try {
            completeReplay(replay,MAX_COPY_QUANTUM,budget);
            final PcbLayerRoutingPrototype.DetachedOrderingContinuation continuation=budget.run("handoff",
                new Operation<PcbLayerRoutingPrototype.DetachedOrderingContinuation>() {
                    public PcbLayerRoutingPrototype.DetachedOrderingContinuation run() {
                        return replay.handle.takeOrderingContinuation();
                    }
                });
            final PcbLayerRoutingPrototype.Session owner=replay.owner;
            final Object search=replay.search;
            final PcbBoardLayout layout=replay.layout;
            final int passBefore=intField(owner,"pass");
            final int passesBefore=intField(owner,"passes");
            final int completedBefore=intField(owner,"completedOrderings");
            final int yieldedBefore=intField(owner,"yieldedOrderings");
            final int expandedBefore=owner.expansions();
            final int ownerWorkBefore=owner.lastWork();
            PcbLayerRoutingPrototype.DetachedOrderingProgress progress=budget.runMeasured("remainder",
                new Operation<PcbLayerRoutingPrototype.DetachedOrderingProgress>() {
                    public PcbLayerRoutingPrototype.DetachedOrderingProgress run() {
                        try { return continuation.advance(1); }
                        finally { budget.recordWork(continuation.lastWork()); }
                    }
                });
            Object ready=field(owner,"readyOrdering");
            boolean readyMatches=ready!=null && field(ready,"layout")==layout;
            String evidence="progress="+progress+" continuationLastWork="+continuation.lastWork()+
                " ownerLastWorkBefore="+ownerWorkBefore+" ownerLastWork="+owner.lastWork()+
                " searchStillOwned="+(field(owner,"search")==search)+
                " candidateStillOwned="+(field(owner,"candidate")==layout)+" ready="+(ready!=null)+
                " readyLayoutMatches="+readyMatches+" searchComplete="+booleanField(search,"complete")+
                " continuationOwnerPinned="+(field(continuation,"owner")==owner)+
                " ownerLeasePinned="+(field(owner,"detachedOrderingContinuation")==continuation)+
                " ownerPass="+intField(owner,"pass")+" searchPass="+intField(search,"pass")+
                " passes="+intField(owner,"passes")+" completed="+
                intField(owner,"completedOrderings")+" yielded="+intField(owner,"yieldedOrderings")+
                " expansionsDelta="+(owner.expansions()-expandedBefore)+
                " ownerComplete="+booleanField(owner,"complete")+" termination="+
                field(owner,"orderingTermination");
            System.out.println("P07_ORDERING_CANCEL_CANARY "+evidence);
            require(progress==PcbLayerRoutingPrototype.DetachedOrderingProgress.ROUTE_READY &&
                    continuation.lastWork()==1 && owner.lastWork()==1 &&
                    field(owner,"search")==null && field(owner,"candidate")==null &&
                    readyMatches && booleanField(search,"complete") &&
                    field(continuation,"owner")==owner &&
                    field(owner,"detachedOrderingContinuation")==continuation &&
                    owner.expansions()-expandedBefore>=0 &&
                    owner.expansions()-expandedBefore<=continuation.lastWork() &&
                    intField(owner,"pass")==passBefore+1 && intField(owner,"passes")==passesBefore &&
                    intField(owner,"completedOrderings")==completedBefore+1 &&
                    intField(owner,"yieldedOrderings")==yieldedBefore+1,
                "one-unit Search completion publishes the expected ready route before cancellation; "+
                    evidence);
            budget.run("lifecycle",new Operation<Void>() {public Void run() {
                continuation.cancel(); return null;
            }});
            require(continuation.lastWork()==1 && owner.lastWork()==1,
                "cancel retains the completed call's one measured owner operation before any retry");
            final int expansionsBeforeRejectedCalls=owner.expansions();
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                continuation.advance(1);
            }},"canceled continuation cannot resume");
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                continuation.takeReadyOrdering();
            }},"canceled ready result cannot be taken");
            String canceledState="continuationLastWork="+continuation.lastWork()+
                " ownerSearch="+(field(owner,"search")!=null)+" ownerCandidate="+
                (field(owner,"candidate")!=null)+" ready="+(field(owner,"readyOrdering")!=null)+
                " lease="+(field(owner,"detachedOrderingContinuation")!=null)+
                " receiverOwner="+(field(continuation,"owner")!=null)+
                " receiverSearch="+(field(continuation,"search")!=null)+
                " receiverLayout="+(field(continuation,"layout")!=null)+
                " leaseClosed="+booleanField(owner,"orderingLeaseClosed")+
                " pass="+intField(owner,"pass");
            System.out.println("P07_ORDERING_CANCEL_DISCARD "+canceledState);
            require(!continuation.isComplete() && continuation.lastWork()==0 && owner.lastWork()==1 &&
                    owner.expansions()==expansionsBeforeRejectedCalls &&
                    field(owner,"search")==null && field(owner,"candidate")==null &&
                    field(owner,"readyOrdering")==null &&
                    field(owner,"detachedOrderingContinuation")==null &&
                    field(continuation,"owner")==null && field(continuation,"search")==null &&
                    field(continuation,"layout")==null &&
                    booleanField(owner,"orderingLeaseClosed"),
                "canceling the receiver releases its ready result and lease; "+canceledState);
            budget.run("lifecycle",new Operation<Void>() {public Void run() {
                replay.handle.release(); replay.handle.cancel(); return null;
            }});
            budget.run("oracle",new Operation<Void>() {public Void run() throws Exception {
                verifySourceUnchanged(source,"canceled current-ordering continuation"); return null;
            }});
        } finally { replay.handle.release(); }
    }

    private static void verifyContinuationActiveCancellation(final Source source,
            final ContinuationBudget budget) throws Exception {
        final Replay replay=buildDetached(source,MAX_COPY_QUANTUM,POLICY_VIA_CAP,true,
            source.viaCost,true,null,budget);
        try {
            completeReplay(replay,MAX_COPY_QUANTUM,budget);
            final PcbLayerRoutingPrototype.DetachedOrderingContinuation continuation=budget.run("handoff",
                new Operation<PcbLayerRoutingPrototype.DetachedOrderingContinuation>() {
                    public PcbLayerRoutingPrototype.DetachedOrderingContinuation run() {
                        return replay.handle.takeOrderingContinuation();
                    }
                });
            final PcbLayerRoutingPrototype.Session owner=replay.owner;
            final Object search=replay.search;
            final PcbBoardLayout layout=replay.layout;
            final int passBefore=intField(owner,"pass");
            final int ownerWorkBefore=owner.lastWork();
            String evidence="progress="+field(continuation,"progress")+" continuationLastWork="+
                continuation.lastWork()+" ownerLastWorkBefore="+ownerWorkBefore+
                " ownerLastWork="+owner.lastWork()+
                " ownerSearchLive="+(field(owner,"search")==search)+
                " ownerCandidateLive="+(field(owner,"candidate")==layout)+
                " continuationOwnerPinned="+(field(continuation,"owner")==owner)+
                " ownerLeasePinned="+(field(owner,"detachedOrderingContinuation")==continuation)+
                " ready="+(field(owner,"readyOrdering")!=null)+" pass="+intField(owner,"pass")+
                " searchPass="+intField(search,"pass");
            System.out.println("P07_ORDERING_ACTIVE_CANCEL_CANARY "+evidence);
            require(field(continuation,"progress")==
                    PcbLayerRoutingPrototype.DetachedOrderingProgress.WORKING &&
                    continuation.lastWork()==0 && field(owner,"search")==search &&
                    field(owner,"candidate")==layout && field(owner,"readyOrdering")==null &&
                    field(continuation,"owner")==owner &&
                    field(owner,"detachedOrderingContinuation")==continuation &&
                    intField(owner,"pass")==passBefore,
                "post-handoff cancellation starts from a live WORKING Search before any owner step; "+
                    evidence);
            budget.run("lifecycle",new Operation<Void>() {public Void run() {
                continuation.cancel(); return null;
            }});
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                continuation.advance(1);
            }},"canceled active continuation cannot resume");
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                continuation.takeReadyOrdering();
            }},"canceled active continuation cannot take a route");
            String canceledState="continuationLastWork="+continuation.lastWork()+
                " ownerLastWorkBefore="+ownerWorkBefore+" ownerLastWork="+owner.lastWork()+
                " ownerSearch="+(field(owner,"search")!=null)+" ownerCandidate="+
                (field(owner,"candidate")!=null)+" ready="+(field(owner,"readyOrdering")!=null)+
                " lease="+(field(owner,"detachedOrderingContinuation")!=null)+
                " receiverOwner="+(field(continuation,"owner")!=null)+
                " receiverSearch="+(field(continuation,"search")!=null)+
                " receiverLayout="+(field(continuation,"layout")!=null)+
                " leaseClosed="+booleanField(owner,"orderingLeaseClosed")+
                " pass="+intField(owner,"pass");
            System.out.println("P07_ORDERING_ACTIVE_CANCEL_DISCARD "+canceledState);
            require(!continuation.isComplete() && continuation.lastWork()==0 &&
                    field(owner,"search")==null && field(owner,"candidate")==null &&
                    field(owner,"readyOrdering")==null &&
                    field(owner,"detachedOrderingContinuation")==null &&
                    field(continuation,"owner")==null && field(continuation,"search")==null &&
                    field(continuation,"layout")==null &&
                    booleanField(owner,"orderingLeaseClosed") &&
                    intField(owner,"pass")==passBefore && owner.lastWork()==ownerWorkBefore,
                "canceling active ownership releases its copied Search without advancing the pass; "+
                    canceledState);
            budget.run("lifecycle",new Operation<Void>() {public Void run() {
                replay.handle.release(); replay.handle.cancel(); return null;
            }});
            budget.run("oracle",new Operation<Void>() {public Void run() throws Exception {
                verifySourceUnchanged(source,"pre-advance active continuation cancellation");
                return null;
            }});
        } finally { replay.handle.release(); }
    }

    private static void verifyStaleContinuation(final Source source,
            final ContinuationBudget budget) throws Exception {
        final Replay replay=buildDetached(source,MAX_COPY_QUANTUM,POLICY_VIA_CAP,true,
            source.viaCost,true,null,budget);
        try {
            completeReplay(replay,MAX_COPY_QUANTUM,budget);
            final PcbLayerRoutingPrototype.DetachedOrderingContinuation continuation=budget.run("handoff",
                new Operation<PcbLayerRoutingPrototype.DetachedOrderingContinuation>() {
                    public PcbLayerRoutingPrototype.DetachedOrderingContinuation run() {
                        return replay.handle.takeOrderingContinuation();
                    }
                });
            final PcbLayerRoutingPrototype.Session owner=replay.owner;
            final PcbBoardLayout candidate=replay.layout;
            budget.run("lifecycle",new Operation<Void>() {public Void run() throws Exception {
                setField(owner,"search",null); return null;
            }});
            final int checksBefore=replay.observerChecks[0];
            budget.run("lifecycle",new Operation<Void>() {public Void run() throws Exception {
                expectLifecycleRejected(new Checked(){public void run() { continuation.advance(1); }},
                    "stale continuation is rejected before observer or successor work");
                return null;
            }});
            require(replay.observerChecks[0]==checksBefore && field(owner,"candidate")==candidate &&
                    field(owner,"detachedOrderingContinuation")==null &&
                    !booleanField(owner,"orderingLeaseClosed"),
                "stale receiver unlinks only its own lease and leaves the changed owner candidate open");
            budget.run("lifecycle",new Operation<Void>() {public Void run() {
                replay.handle.release(); replay.handle.cancel(); return null;
            }});
            budget.run("oracle",new Operation<Void>() {public Void run() throws Exception {
                verifySourceUnchanged(source,"stale current-ordering continuation"); return null;
            }});
        } finally { replay.handle.release(); }
    }

    private static void verifyContinuationObserverFailure(final Source source,
            final ContinuationBudget budget) throws Exception {
        final GateObserver gate=new GateObserver();
        final Replay replay=buildDetached(source,MAX_COPY_QUANTUM,POLICY_VIA_CAP,true,
            source.viaCost,true,gate,budget);
        try {
            completeReplay(replay,MAX_COPY_QUANTUM,budget);
            final PcbLayerRoutingPrototype.DetachedOrderingContinuation continuation=budget.run("handoff",
                new Operation<PcbLayerRoutingPrototype.DetachedOrderingContinuation>() {
                    public PcbLayerRoutingPrototype.DetachedOrderingContinuation run() {
                        return replay.handle.takeOrderingContinuation();
                    }
                });
            gate.failAfter(1);
            final boolean[] rejected={false};
            budget.runMeasured("remainder",new Operation<Void>() {public Void run() {
                try { continuation.advance(1); }
                catch(IllegalStateException expected) { rejected[0]=true; }
                finally { budget.recordWork(continuation.lastWork()); }
                require(rejected[0],"observer failure inside the copied Search aborts the receiver");
                return null;
            }});
            require(continuation.lastWork()==1 && replay.owner.lastWork()==1 &&
                    !continuation.isComplete() &&
                    booleanField(replay.owner,"orderingLeaseClosed") &&
                    field(replay.owner,"detachedOrderingContinuation")==null,
                "observer failure preserves the one already-entered owner operation and cannot summarize");
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                continuation.advance(1);
            }},"observer-failed continuation cannot resume");
            budget.run("oracle",new Operation<Void>() {public Void run() throws Exception {
                verifySourceUnchanged(source,"observer-failed current-ordering continuation");
                return null;
            }});
        } finally { replay.handle.release(); }
    }

    private static void verifyReplayReentrancy(final Source source,final ContinuationBudget budget)
            throws Exception {
        final GateObserver gate=new GateObserver();
        final Replay replay=buildDetached(source,MAX_COPY_QUANTUM,POLICY_VIA_CAP,true,
            source.viaCost,true,gate,budget);
        try {
            gate.runOnNextCheck(new Runnable(){public void run() {
                try { replay.handle.advance(1); }
                catch(IllegalStateException expected) {
                    gate.rejectedAdvanceReentries++;
                    budget.recordNestedControlUnit();
                }
            }});
            budget.runMeasured("branch-replay",new Operation<Void>() {public Void run() {
                try { replay.handle.advance(1); }
                finally { budget.recordWork(replay.handle.lastWork()); }
                return null;
            }});
            require(gate.rejectedAdvanceReentries==1 && replay.handle.lastWork()==1 &&
                    !replay.handle.isComplete(),
                "reentrant detached branch advance is rejected before a nested work unit");
            completeReplay(replay,MAX_COPY_QUANTUM,budget);
            replay.handle.release(); replay.handle.cancel();
            budget.run("oracle",new Operation<Void>() {public Void run() throws Exception {
                verifySourceUnchanged(source,"reentrant detached branch replay"); return null;
            }});
        } finally { replay.handle.release(); }
    }

    private static void verifyReplayFailureLifecycle(final Source source,
            final ContinuationBudget budget) throws Exception {
        final GateObserver gate=new GateObserver();
        final Replay replay=buildDetached(source,MAX_COPY_QUANTUM,POLICY_VIA_CAP,true,
            source.viaCost,true,gate,budget);
        try {
            gate.failAfter(1);
            final boolean[] rejected={false};
            budget.runMeasured("branch-replay",new Operation<Void>() {public Void run() {
                try { replay.handle.advance(1); }
                catch(IllegalStateException expected) { rejected[0]=true; }
                finally { budget.recordWork(replay.handle.lastWork()); }
                require(rejected[0],"observer failure during branch replay is terminal");
                return null;
            }});
            require(replay.handle.lastWork()==1 && !replay.handle.isComplete() &&
                    booleanField(replay.handle,"failed") && field(replay.handle,"owner")==null,
                "failed branch replay retains its one entered unit and clears copied-owner references");
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                replay.handle.advance(1);
            }},"failed branch replay cannot be retried");
            expectLifecycleRejected(budget,"lifecycle",new Checked(){public void run() {
                replay.handle.takeOrderingContinuation();
            }},"failed branch replay cannot transfer a continuation");
            require(replay.handle.lastWork()==1,
                "rejected retry preserves the original failed-operation work count");
            budget.run("lifecycle",new Operation<Void>() {public Void run() {
                replay.handle.release(); replay.handle.cancel(); return null;
            }});
            budget.run("oracle",new Operation<Void>() {public Void run() throws Exception {
                verifySourceUnchanged(source,"failed detached branch replay"); return null;
            }});
        } finally { replay.handle.release(); }
    }

    private static void verifyFullOrderingLayout(TroubleshootBoard board,PcbBoardLayout layout,String label) {
        layout.validateRoutingGeometry(board);
        new PcbTwoLayerRules(board,layout).validate(layout);
        layout.validateTraceClearance();
        P08ScalabilityContractTest.referenceClearance(layout.getTraces());
        PcbConductorGraph productionGraph=layout.captureConductorGraph(board);
        PcbConductorGraph referenceGraph=P08ReferenceConductorBuilder.capture(board,layout);
        require(productionGraph.toCanonical().equals(referenceGraph.toCanonical()),
            label+" matches the independent P08 conductor graph");
        PcbConductorGraph.Snapshot production=productionGraph.pristine();
        production.requirePristineNetConnectivity(board);
        PcbConductorGraph.Snapshot reference=referenceGraph.pristine();
        reference.requirePristineNetConnectivity(board);
        Vector<String> pads=board.getPadIds();
        for(String first:pads) for(String second:pads) {
            boolean sameNet=board.getPad(first).getNetId().equals(board.getPad(second).getNetId());
            require(reference.padsConnected(first,second)==sameNet,
                label+" preserves full connectivity and foreign-net isolation for "+first+"/"+second);
        }
        P07LayeredRasterOracle.Metrics raster=P07LayeredRasterOracle.verify(layout,board);
        require(raster.getOrderedPadPairs()==(long)pads.size()*pads.size(),
            label+" passes independent layered-raster connectivity for every ordered pad pair");
    }

    private static Source captureTransitionEntry() throws Exception {
        LiteralFixture fixture=literalSourceFixture();
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(
            fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS,POLICY_VIA_CAP);
        int units=0;
        while(units<MAX_SOURCE_UNITS) {
            Object search=field(session,"search");
            if(search!=null) {
                Object branch=field(search,"branch");
                if(branch!=null && intField(search,"pass")==0 &&
                        intField(branch,"branchOrdinal")==3 &&
                        "UNDER".equals(stringField(search,"currentNet")) &&
                        "B.1".equals(((PcbPadPlacement)field(branch,"start")).getPadId()) &&
                        intField(branch,"expanded")==0 && intField(field(branch,"queue"),"size")==2)
                    return new Source(fixture,session,search,branch,units);
            }
            if(session.advanceSlice(1))
                throw new AssertionError("crossing source completed before its initial VIA-producing branch");
            units++;
        }
        throw new AssertionError("crossing source exceeded its one-unit initial-branch guard");
    }

    private static void verifyCostOverrideUsesViaSearchAndPublisher() throws Exception {
        Source source=captureTransitionEntry();
        require("UNDER".equals(source.currentNet) && "B.1".equals(source.start.getPadId()) &&
                source.prefixIds.length==0,
            "crossing canary captures the virgin VIA-producing UNDER branch before its first expansion");
        Replay replay=buildDetachedAtCapAndCost(source,MAX_COPY_QUANTUM,
            source.maximumVias,DIAGNOSTIC_VIA_COST);
        try {
            completeCostReplay(replay,MAX_COPY_QUANTUM);
            require(intField(replay.owner,"viaCost")==DIAGNOSTIC_VIA_COST &&
                    intField(replay.search,"viaCost")==DIAGNOSTIC_VIA_COST &&
                    intField(source.owner,"viaCost")==source.viaCost &&
                    intField(source.search,"viaCost")==source.viaCost,
                "normal Search and Publisher run on the detached cost while the source stays at policy cost");
            int transitions=verifyWeightedPathCost(replay);
            require(transitions>0,
                "constrained crossing is published through actual Search VIA transitions");
            verifyCostOverridePhysicalConnectivity(replay,transitions);
            verifySourceUnchanged(source,"normal cost-override crossing replay");
        } finally { replay.handle.release(); }
    }

    private static void verifyRelaxedBoundCostPropagation(Source source) throws Exception {
        Replay replay=buildDetachedAtCapAndCost(source,MAX_COPY_QUANTUM,
            source.maximumVias,DIAGNOSTIC_VIA_COST);
        try {
            setField(replay.branch,"relaxedBoundStarted",Boolean.TRUE);
            setField(replay.branch,"relaxedCloneFace",Integer.valueOf(2));
            Method build=replay.branch.getClass().getDeclaredMethod("advanceRelaxedBoundBuild");
            build.setAccessible(true);
            invoke(build,replay.branch);
            Object bound=field(replay.branch,"relaxedDistanceBound");
            require(bound!=null && intField(bound,"viaCost")==DIAGNOSTIC_VIA_COST &&
                    intField(replay.search,"viaCost")==DIAGNOSTIC_VIA_COST,
                "production Search passes its effective cost into the relaxed VIA bound");
            verifySourceUnchanged(source,"relaxed-bound cost propagation canary");
        } finally { replay.handle.release(); }
    }

    private static void completeCostReplay(Replay replay,int quantum) throws Exception {
        int calls=0;
        while(!replay.handle.isComplete()) {
            replay.handle.advance(quantum);
            int used=replay.handle.lastWork();
            require(used>0 && used<=quantum,
                "cost-override Search/Publisher makes bounded normal progress");
            if(++calls>MAX_REPLAY_UNITS)
                throw new AssertionError("cost-override literal replay exceeded its bounded progress guard");
        }
        replay.publisher=replay.handle.getPublisherForDiagnostics();
        require(replay.publisher!=null && field(replay.search,"branch")==null &&
                field(replay.search,"publisher")==null && field(replay.branch,"found")!=null,
            "cost-override replay reaches a real Search goal and completes its Publisher");
        replay.pathRows=pathRows(field(replay.publisher,"end"));
        replay.traceDelta=traceSignatures(replay.layout,replay.source.prefixTraceCount);
        replay.postHoleIds=holeIds(replay.layout);
        replay.postHoleFingerprints=holeFingerprints(replay.layout);
        replay.postGeometry=replay.layout.geometryFingerprint();
        replay.postGraph=replay.layout.captureConductorGraph(replay.source.fixture.board).toCanonical();
        require(intField(replay.publisher,"stage")==6,
            "cost-override normal Publisher reaches its terminal stage");
    }

    private static void verifyCostOverridePhysicalConnectivity(Replay replay,int transitions)
            throws Exception {
        PcbConductorGraph.Snapshot before=replay.source.prefixLayout
            .captureConductorGraph(replay.source.fixture.board).pristine();
        PcbConductorGraph.Snapshot after=replay.layout
            .captureConductorGraph(replay.source.fixture.board).pristine();
        require(!before.padsConnected(replay.source.start.getPadId(),replay.source.root.getPadId()) &&
                after.padsConnected(replay.source.start.getPadId(),replay.source.root.getPadId()),
            "normal cost-override publication electrically joins the two actual branch endpoints");
        require(!after.padsConnected("L.1","A.1"),
            "cost-override crossing publication keeps the independent OVER and UNDER nets isolated");
        PcbConductorGraph.Snapshot independent=
            P08ReferenceConductorBuilder.capture(replay.source.fixture.board,replay.layout).pristine();
        require(independent.padsConnected(replay.source.start.getPadId(),replay.source.root.getPadId()) &&
                !independent.padsConnected("L.1","A.1"),
            "independent P08 conductor oracle confirms the partial branch connection and net isolation");
        new PcbTwoLayerRules(replay.source.fixture.board,replay.layout).validate(replay.layout);
        replay.layout.validateTraceClearance();
        int newTraces=0,newHoles=0; boolean startAttached=false;
        Vector<PcbTraceGeometry> traces=replay.layout.getTraces();
        Rectangle outline=replay.layout.getBoardOutline();
        TroubleshootBoard board=replay.source.fixture.board;
        for(int i=replay.source.prefixTraceCount;i<traces.size();i++) {
            PcbTraceGeometry trace=traces.get(i); newTraces++;
            require(replay.source.currentNet.equals(trace.getNetId()) &&
                    trace.getWidth()==replay.source.fixture.board.getPlacementConstraints()
                        .getTraceWidthProfile().widthFor(
                            replay.source.fixture.board.getNet(replay.source.currentNet).getRoutingRole()),
                "cost-override Publisher emits only current-net traces at the declared width");
            if(replay.source.start.getPadId().equals(trace.getStartPadId())) startAttached=true;
            int[] xs=trace.getXPoints(),ys=trace.getYPoints();
            for(int p=1;p<xs.length;p++) {
                require((xs[p]==xs[p-1])!=(ys[p]==ys[p-1]),
                    "cost-override Publisher emits nonzero orthogonal conductive segments");
                Rectangle stroke=PcbConductorBuilder.stroke(xs[p-1],ys[p-1],xs[p],ys[p],
                    trace.getWidth());
                require(PcbTwoLayerRules.inside(outline,stroke),
                    "cost-override branch copper stays inside the board outline");
            }
            validatePublishedTraceCourtyards(replay.layout,board,trace);
            verifyPublishedTraceEndpoint(replay.layout,board,trace,trace.getStartPadId(),
                xs[0],ys[0]);
            verifyPublishedTraceEndpoint(replay.layout,board,trace,trace.getEndPadId(),
                xs[xs.length-1],ys[ys.length-1]);
        }
        for(PcbBoardHole hole:replay.layout.getHoles())
            if(!containsHole(replay.source.prefixIds,hole.id)) {
                newHoles++;
                require(hole.kind==PcbBoardHole.Kind.VIA &&
                        replay.source.currentNet.equals(hole.netId) &&
                        hole.id.startsWith("p07-via/"+replay.source.currentNet+"/"+
                            replay.source.branchOrdinal+"/") &&
                        pathHasViaTransitionAt(replay,hole.x,hole.y) &&
                        PcbTwoLayerRules.inside(outline,hole.getBounds()),
                    "cost-override transition publishes a VIA owned by the routed net");
            }
        int matchedTransitions=0;
        int width=intField(replay.search,"width"),minX=intField(replay.search,"minX"),
            minY=intField(replay.search,"minY");
        for(int i=1;i<replay.pathRows.length;i++) {
            int[] previous=replay.pathRows[i-1],next=replay.pathRows[i];
            if(next[1]==previous[1]) continue;
            int x=minX+(next[0]%width)*PcbLayerRoutingPrototype.GRID;
            int y=minY+(next[0]/width)*PcbLayerRoutingPrototype.GRID;
            int matches=0;
            for(PcbBoardHole hole:replay.layout.getHoles())
                if(!containsHole(replay.source.prefixIds,hole.id) && hole.x==x && hole.y==y)
                    matches++;
            require(matches==1,
                "each cost-override Search face transition retains one Publisher VIA at its node");
            matchedTransitions++;
        }
        require(newTraces>0 && startAttached && newHoles==transitions &&
                matchedTransitions==transitions,
            "normal Publisher emits the branch trace and actual VIA transitions");
    }

    private static boolean pathHasViaTransitionAt(Replay replay,int x,int y) throws Exception {
        int width=intField(replay.search,"width"),minX=intField(replay.search,"minX"),
            minY=intField(replay.search,"minY");
        for(int i=1;i<replay.pathRows.length;i++) {
            int[] previous=replay.pathRows[i-1],next=replay.pathRows[i];
            if(next[1]!=previous[1] &&
                    minX+(next[0]%width)*PcbLayerRoutingPrototype.GRID==x &&
                    minY+(next[0]/width)*PcbLayerRoutingPrototype.GRID==y) return true;
        }
        return false;
    }

    private static void verifyPublishedTraceEndpoint(PcbBoardLayout layout,TroubleshootBoard board,
            PcbTraceGeometry trace,String padId,int x,int y) {
        if(padId==null) return;
        BoardPad logical=board.getPad(padId);
        PcbPadPlacement physical=layout.getPad(padId);
        require(logical!=null && physical!=null && logical.getNetId().equals(trace.getNetId()) &&
                PcbCopperAccess.hasCopper(physical,trace.getLayer()) &&
                physical.getX()==x && physical.getY()==y,
            "cost-override trace endpoint maps to its matching physical and electrical pad");
    }

    private static void validatePublishedTraceCourtyards(PcbBoardLayout layout,
            TroubleshootBoard board,PcbTraceGeometry trace) throws Exception {
        Method validate=PcbBoardLayout.class.getDeclaredMethod("validateTraceCourtyards",
            TroubleshootBoard.class,PcbTraceGeometry.class,BoardPad.class,BoardPad.class);
        validate.setAccessible(true);
        invoke(validate,layout,board,trace,board.getPad(trace.getStartPadId()),
            board.getPad(trace.getEndPadId()));
    }

    private static boolean containsHole(String[] ids,String id) {
        for(String value:ids) if(value.equals(id)) return true;
        return false;
    }

    private static int verifyWeightedPathCost(Replay replay) throws Exception {
        if(replay.pathRows==null || replay.pathRows.length<2)
            throw new IllegalArgumentException("Missing path for independent weighted-cost oracle");
        int primary=intField(replay.search,"primary"),width=intField(replay.search,"width");
        int cells=intField(replay.search,"cells");
        int expected=replay.pathRows[0][1]==primary?0:
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.secondaryCost;
        require(replay.pathRows[0][4]==expected,
            "independent weighted-cost oracle retains the unchanged initial seed charge");
        int transitions=0;
        for(int i=1;i<replay.pathRows.length;i++) {
            int[] previous=replay.pathRows[i-1],next=replay.pathRows[i];
            int delta=next[0]-previous[0],stepDirection=-1,edgeCost;
            if(next[1]!=previous[1]) {
                require(next[0]==previous[0] && next[3]==previous[3]+1 && next[2]==4,
                    "independent path oracle recognizes a same-cell face transition");
                edgeCost=replay.viaCost;
                transitions++;
            } else {
                if(delta==-width) stepDirection=0;
                else if(delta==1 && previous[0]%width+1<width) stepDirection=1;
                else if(delta==width) stepDirection=2;
                else if(delta==-1 && previous[0]%width>0) stepDirection=3;
                require(stepDirection>=0 && next[2]==stepDirection &&
                        next[3]==previous[3],
                    "independent path oracle recognizes a single planar grid edge");
                edgeCost=PcbLayerRoutingPrototype.GRID+
                    (previous[1]==primary?0:
                        PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.secondaryCost)+
                    (previous[2]!=4 && previous[2]!=stepDirection?
                        PcbLayerRoutingPrototype.TURN_COST:0);
            }
            expected+=edgeCost;
            require(next[4]==expected,
                "independent weighted node recurrence matches Search at path index "+i);
        }
        require(replay.pathRows[replay.pathRows.length-1][4]==expected,
            "independent weighted path total matches the published goal node");
        return transitions;
    }

    private static Replay runDetached(Source source,int quantum) throws Exception {
        Replay replay=buildDetached(source,quantum);
        boolean retained=false;
        try {
            completeReplay(replay,quantum);
            verifySourceUnchanged(source,"detached replay work");
            retained=true;
            return replay;
        } finally {
            if(!retained) replay.handle.release();
        }
    }

    private static Replay runDetachedAtCap(Source source,int quantum,int maximumVias)
            throws Exception {
        Replay replay=buildDetachedAtCap(source,quantum,maximumVias);
        boolean retained=false;
        try {
            completeReplay(replay,quantum);
            verifySourceUnchanged(source,"explicit-cap detached replay work");
            retained=true;
            return replay;
        } finally {
            if(!retained) replay.handle.release();
        }
    }

    private static Replay runDetachedAtCapAndCost(Source source,int quantum,int maximumVias,
            int viaCost) throws Exception {
        Replay replay=buildDetachedAtCapAndCost(source,quantum,maximumVias,viaCost);
        boolean retained=false;
        try {
            completeReplay(replay,quantum);
            verifySourceUnchanged(source,"explicit-cap-and-cost detached replay work");
            retained=true;
            return replay;
        } finally {
            if(!retained) replay.handle.release();
        }
    }

    private static void verifyInvalidRequestedCaps(final Source source) throws Exception {
        require(source!=null,"invalid-cap control has a retained source owner");
        expectDetachedCapRejected(source,1,"target VIA cap below the initial prefix");
        expectDetachedCapRejected(source,0,"zero target VIA cap");
        expectDetachedCapRejected(source,-1,"negative target VIA cap");
        int overSourceOrPolicy=source.maximumVias==SOURCE_VIA_CAP?
            SOURCE_VIA_CAP+1:POLICY_VIA_CAP+1;
        expectDetachedCapRejected(source,overSourceOrPolicy,
            source.maximumVias==SOURCE_VIA_CAP?"target VIA cap above the source cap":
                "target VIA cap above the policy cap");
        verifySourceUnchanged(source,"invalid explicit VIA cap requests");
    }

    private static void expectDetachedCapRejected(final Source source,final int maximumVias,
            String label) throws Exception {
        final int[] checks={0};
        final SeededPcbLayoutGenerator.AttemptObserver observer=
            new SeededPcbLayoutGenerator.AttemptObserver() {
                public void check(int attempt) { checks[0]++; }
            };
        PcbLayerRoutingPrototype.DetachedBranchCopyTask task=null;
        boolean rejected=false;
        try {
            task=PcbLayerRoutingPrototype.beginDetachedBranchCopy(
                source.session,observer,maximumVias);
        } catch(IllegalArgumentException expected) { rejected=true; }
        finally { if(task!=null) task.cancel(); }
        require(rejected && task==null && checks[0]==0,
            label+" is rejected before starting an observer or copy task");
    }

    private static void verifyInvalidRequestedCosts(final Source source) throws Exception {
        expectDetachedCostRejected(source,0,"zero diagnostic VIA cost");
        expectDetachedCostRejected(source,-1,"negative diagnostic VIA cost");
        expectDetachedCostRejected(source,1,"VIA cost below the retained turn heuristic floor");
        expectDetachedCostRejected(source,PcbLayerRoutingPrototype.TURN_COST-1,
            "VIA cost below TURN_COST");
        expectDetachedCostRejected(source,source.viaCost-1,
            "VIA cost below the source policy cost");
        expectDetachedCostRejected(source,PcbLayerRoutingPrototype.MAX_DIAGNOSTIC_VIA_COST+1,
            "diagnostic VIA cost above its bounded ceiling");
        verifySourceUnchanged(source,"invalid explicit VIA cost requests");
    }

    private static void expectDetachedCostRejected(final Source source,final int viaCost,
            String label) throws Exception {
        final int[] checks={0};
        final SeededPcbLayoutGenerator.AttemptObserver observer=
            new SeededPcbLayoutGenerator.AttemptObserver() {
                public void check(int attempt) { checks[0]++; }
            };
        PcbLayerRoutingPrototype.DetachedBranchCopyTask task=null;
        boolean rejected=false;
        try {
            task=PcbLayerRoutingPrototype.beginDetachedBranchCopy(
                source.session,observer,source.maximumVias,viaCost);
        } catch(IllegalArgumentException expected) { rejected=true; }
        finally { if(task!=null) task.cancel(); }
        require(rejected && task==null && checks[0]==0,
            label+" is rejected before starting an observer or copy task");
    }

    private static void verifyDefaultPolicyCosts(LiteralFixture fixture) throws Exception {
        PcbLayerRoutingPrototype.Policy[] policies={PcbLayerRoutingPrototype.Policy.ONE_LAYER,
            PcbLayerRoutingPrototype.Policy.SPARSE_LINK};
        for(PcbLayerRoutingPrototype.Policy policy:policies) {
            PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(
                fixture.board,fixture.layout,policy,P06FactoryLinkFixtures.OBSERVER,
                PcbLayerRoutingPrototype.MAX_EXPANSIONS,policy.vias);
            require(intField(session,"viaCost")==policy.viaCost && policy.viaCost==0,
                "ordinary zero-cost policy retains its immutable VIA-cost default: "+policy.name());
        }
    }

    private static void verifyLowerViaCostCounterexample() {
        boolean[] goals=new boolean[8];
        goals[3]=true; goals[7]=true;
        int[] distance={20,10,10,0};
        PcbLayerRoutingPrototype.TurnLowerBound retainedTurnBound=
            new PcbLayerRoutingPrototype.TurnLowerBound(2,2,goals,distance,
                P06FactoryLinkFixtures.OBSERVER,0);
        int retainedSeedHeuristic=retainedTurnBound.value(0,4);
        int cheaperViaRoute=PcbLayerRoutingPrototype.GRID+1+
            PcbLayerRoutingPrototype.GRID+
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.secondaryCost;
        int minimumCopyCost=Math.max(
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.viaCost,
            PcbLayerRoutingPrototype.TURN_COST);
        require(retainedSeedHeuristic==55 && cheaperViaRoute==23 &&
                cheaperViaRoute<retainedSeedHeuristic && minimumCopyCost==60,
            "open 2x2 permits primary-east, VIA, secondary-south at cost 23 below retained turn h=55");
    }

    private static void verifyAdvancedFrontierRejectsCostOverride() throws Exception {
        Source source=freshSource();
        require(intField(source.branch,"expanded")==0,
            "advanced-frontier canary begins at an unexpanded consumer branch");
        source.session.advanceSlice(1);
        int expanded=intField(source.branch,"expanded");
        require(expanded>0,"advanced-frontier canary performs real Search work first");
        final int[] checks={0};
        final SeededPcbLayoutGenerator.AttemptObserver observer=
            new SeededPcbLayoutGenerator.AttemptObserver() {
                public void check(int attempt) { checks[0]++; }
            };
        PcbLayerRoutingPrototype.DetachedBranchCopyTask task=null;
        boolean rejected=false;
        try {
            task=PcbLayerRoutingPrototype.beginDetachedBranchCopy(source.session,observer,
                source.maximumVias,DIAGNOSTIC_VIA_COST);
        } catch(IllegalArgumentException expected) { rejected=true; }
        finally { if(task!=null) task.cancel(); }
        require(rejected && task==null && checks[0]==0 &&
                field(source.session,"search")==source.search &&
                field(source.search,"branch")==source.branch &&
                intField(source.branch,"expanded")==expanded &&
                intField(source.session,"viaCost")==source.viaCost &&
                intField(source.search,"viaCost")==source.viaCost,
            "cost override rejects an advanced source before observer or copy mutation");
    }

    private static void verifyReducedCapLifecycle(final Source source) throws Exception {
        final PcbLayerRoutingPrototype.DetachedBranchCopyTask task=
            PcbLayerRoutingPrototype.beginDetachedBranchCopy(source.session,
                detachedNoopObserver(),REDUCED_VIA_CAP);
        try {
            expectLifecycleRejected(new Checked(){public void run() { task.result(); }},
                "reduced-cap copy exposes no replay before completion");
            task.advance(1);
            require(task.lastWork()==1 && !task.isComplete(),
                "reduced-cap copy begins bounded work before cancellation");
            task.cancel();
            require(!task.isComplete() && task.lastWork()==0,
                "canceled reduced-cap copy reports no partial completion");
            expectLifecycleRejected(new Checked(){public void run() { task.result(); }},
                "canceled reduced-cap copy exposes no replay result");
            expectLifecycleRejected(new Checked(){public void run() { task.advance(1); }},
                "canceled reduced-cap copy cannot resume");
        } finally { task.cancel(); }
        verifySourceUnchanged(source,"canceled reduced-cap copy lifecycle");
    }

    private static void verifyReducedCapCopies(Source source) throws Exception {
        Replay saturated=null;
        try {
            saturated=runDetachedAtCap(source,MAX_COPY_QUANTUM,SOURCE_VIA_CAP);
            require(saturated.maximumVias==SOURCE_VIA_CAP &&
                    intField(saturated.owner,"maximumVias")==SOURCE_VIA_CAP &&
                    intField(saturated.search,"maximumVias")==SOURCE_VIA_CAP,
                "explicit cap two configures both detached owner scalars");
            require(source.maximumVias==POLICY_VIA_CAP &&
                    intField(source.owner,"maximumVias")==POLICY_VIA_CAP &&
                    intField(source.search,"maximumVias")==POLICY_VIA_CAP,
                "explicit cap two leaves the retained source owner at policy capacity");
            require("p07-via/UNDER/3/1".equals(saturated.foundViaId) &&
                    saturated.foundX==310 && saturated.foundY==320 &&
                    saturated.foundLayer==PcbCopperLayer.TOP.ordinal() &&
                    saturated.foundTransitions==0,
                "saturated replay joins the literal prior UNDER creator VIA on TOP with no new transition");
            verifyBranchPhysicalAndConnectivity(saturated);
        } finally {
            if(saturated!=null) saturated.handle.release();
        }

        Replay cap17=null;
        try {
            cap17=buildDetachedAtCap(source,MAX_COPY_QUANTUM,REDUCED_VIA_CAP);
            require(cap17.maximumVias==REDUCED_VIA_CAP &&
                    intField(cap17.owner,"maximumVias")==REDUCED_VIA_CAP &&
                    intField(cap17.search,"maximumVias")==REDUCED_VIA_CAP,
                "explicit cap seventeen configures both detached owner scalars");
            require(!cap17.handle.isComplete() && cap17.handle.lastWork()==0,
                "cap seventeen copy is an untouched normal Search entry before replay");
            verifySourceUnchanged(source,"explicit cap seventeen owner copy");
        } finally {
            if(cap17!=null) cap17.handle.release();
        }
    }

    private static Replay buildDetached(Source source,int quantum) throws Exception {
        return buildDetached(source,quantum,source==null?0:source.maximumVias,false);
    }

    private static Replay buildDetachedAtCap(Source source,int quantum,int maximumVias)
            throws Exception {
        return buildDetached(source,quantum,maximumVias,true);
    }

    private static Replay buildDetachedAtCapAndCost(Source source,int quantum,int maximumVias,
            int viaCost) throws Exception {
        return buildDetached(source,quantum,maximumVias,true,viaCost,true);
    }

    private static Replay buildDetached(Source source,int quantum,final int maximumVias,
            boolean explicitCap) throws Exception {
        return buildDetached(source,quantum,maximumVias,explicitCap,
            source==null?0:source.viaCost,false);
    }

    private static Replay buildDetached(Source source,int quantum,final int maximumVias,
            boolean explicitCap,final int viaCost,boolean explicitCost) throws Exception {
        return buildDetached(source,quantum,maximumVias,explicitCap,viaCost,explicitCost,null);
    }

    private static Replay buildDetached(final Source source,final int quantum,final int maximumVias,
            final boolean explicitCap,final int viaCost,final boolean explicitCost,
            final SeededPcbLayoutGenerator.AttemptObserver suppliedObserver) throws Exception {
        return buildDetached(source,quantum,maximumVias,explicitCap,viaCost,explicitCost,
            suppliedObserver,null);
    }

    private static Replay buildDetached(final Source source,final int quantum,final int maximumVias,
            final boolean explicitCap,final int viaCost,final boolean explicitCost,
            final SeededPcbLayoutGenerator.AttemptObserver suppliedObserver,
            final ContinuationBudget budget) throws Exception {
        if(source==null || quantum<1 || quantum>MAX_COPY_QUANTUM)
            throw new IllegalArgumentException("Invalid bounded literal replay quantum");
        final int[] detachedChecks={0};
        final SeededPcbLayoutGenerator.AttemptObserver detachedObserver=
            new SeededPcbLayoutGenerator.AttemptObserver() {
                public void check(int attempt) {
                    detachedChecks[0]++;
                    if(suppliedObserver!=null) suppliedObserver.check(attempt);
                }
            };
        final Operation<PcbLayerRoutingPrototype.DetachedBranchCopyTask> createCopy=
            new Operation<PcbLayerRoutingPrototype.DetachedBranchCopyTask>() {
                public PcbLayerRoutingPrototype.DetachedBranchCopyTask run() {
                    return explicitCost?PcbLayerRoutingPrototype.beginDetachedBranchCopy(
                        source.session,detachedObserver,maximumVias,viaCost):
                        explicitCap?PcbLayerRoutingPrototype.beginDetachedBranchCopy(
                            source.session,detachedObserver,maximumVias):
                        PcbLayerRoutingPrototype.beginDetachedBranchCopy(source.session,detachedObserver);
                }
            };
        final PcbLayerRoutingPrototype.DetachedBranchCopyTask copy=budget==null?
            createCopy.run():budget.run("copy",createCopy);
        PcbLayerRoutingPrototype.DetachedBranchReplay copiedReplay=null;
        boolean transferred=false;
        try {
        if(budget==null) expectLifecycleRejected(new Checked(){public void run() { copy.result(); }},
            "copy result is unavailable before completion");
        else budget.run("copy",new Operation<Void>() {public Void run() throws Exception {
            expectLifecycleRejected(new Checked(){public void run() { copy.result(); }},
                "copy result is unavailable before completion");
            return null;
        }});
        final long[] previousSteps={0};
        final int[] calls={0};
        while(!copy.isComplete()) {
            if(budget==null) {
                copy.advance(quantum);
                int used=copy.lastWork(); long steps=copy.workSteps();
                require(used>0 && used<=quantum && steps>previousSteps[0],
                    "detached owner copy charges positive work within its caller quantum");
                previousSteps[0]=steps;
                if(++calls[0]>MAX_COPY_WORK_STEPS)
                    throw new AssertionError("detached owner copy exceeded its bounded progress guard");
            } else budget.runMeasured("copy",new Operation<Void>() {public Void run() {
                while(!copy.isComplete() && budget.remainingBatchWork()>0) {
                    final int callQuantum=Math.min(quantum,budget.remainingBatchWork());
                    try { copy.advance(callQuantum); }
                    finally { budget.recordWork(copy.lastWork()); }
                    int used=copy.lastWork(); long steps=copy.workSteps();
                    require(used>=0 && used<=callQuantum &&
                            (used==0?steps==previousSteps[0]:steps>previousSteps[0]),
                        "detached owner copy reports bounded work without inventing steps");
                    previousSteps[0]=steps;
                    if(++calls[0]>MAX_COPY_WORK_STEPS)
                        throw new AssertionError("detached owner copy exceeded its bounded progress guard");
                    if(used==0) break;
                }
                return null;
            }});
        }
        final PcbLayerRoutingPrototype.DetachedBranchReplay handle=budget==null?copy.result():
            budget.run("copy",new Operation<PcbLayerRoutingPrototype.DetachedBranchReplay>() {
                public PcbLayerRoutingPrototype.DetachedBranchReplay run() { return copy.result(); }
            });
        copiedReplay=handle;
        long completeSteps=copy.workSteps();
        if(budget==null) copy.advance(quantum);
        else budget.run("copy",new Operation<Void>() {public Void run() {
            copy.advance(quantum); return null;
        }});
        require(copy.isComplete() && copy.lastWork()==0 && copy.workSteps()==completeSteps &&
                copy.result()==handle,
            "repeated complete-copy calls are idempotent and retain one owner handle");
        final Object detachedSearch=handle.getSearchForDiagnostics();
        Object detachedBranch=handle.getBranchForDiagnostics();
        final PcbBoardLayout detachedLayout=handle.getLayout();
        if(budget==null) {
            expectIllegalArgument(new Checked(){public void run() { handle.advance(0); }},
                "zero replay quantum is rejected");
            expectIllegalArgument(new Checked(){public void run() {
                handle.advance(MAX_COPY_QUANTUM+1);
            }},"over-maximum replay quantum is rejected");
        } else {
            expectIllegalArgument(budget,"lifecycle",new Checked(){public void run() {
                handle.advance(0);
            }},"zero replay quantum is rejected");
            expectIllegalArgument(budget,"lifecycle",new Checked(){public void run() {
                handle.advance(MAX_COPY_QUANTUM+1);
            }},"over-maximum replay quantum is rejected");
        }
        require(!handle.isComplete() && handle.lastWork()==0,
            "invalid replay quanta leave the copied entry ready but unadvanced");
        final PcbLayerRoutingPrototype.Session detachedOwner=
            (PcbLayerRoutingPrototype.Session)field(detachedSearch,"owner");
        require(copy.isComplete() && handle.getLayout()==detachedLayout &&
                handle.getSearchForDiagnostics()==detachedSearch &&
                handle.getBranchForDiagnostics()==detachedBranch,
            "canceling a completed copier releases only task references, not its returned replay");
        final Replay replay=new Replay(source,copy,handle,detachedOwner,detachedSearch,detachedBranch,
            detachedLayout,maximumVias,viaCost,detachedObserver,detachedChecks);
        require(field(source.owner,"observer")==source.observerRef &&
                field(detachedOwner,"observer")==detachedObserver &&
                detachedObserver!=source.observerRef && detachedChecks[0]>0,
            "detached copy uses its own checked observer without forwarding to the source");
        if(budget==null) verifyDetachedEntry(source,replay,detachedOwner,replay.branch,replay.start,
            detachedLayout.getPad(source.root.getPadId()),source.branchOrdinal,
            source.seedValues,source.goalValues,source.prefixIds,source.prefixFingerprints);
        else budget.run("copy",new Operation<Void>() {public Void run() throws Exception {
            verifyDetachedEntry(source,replay,detachedOwner,replay.branch,replay.start,
                detachedLayout.getPad(source.root.getPadId()),source.branchOrdinal,
                source.seedValues,source.goalValues,source.prefixIds,source.prefixFingerprints);
            return null;
        }});
        if(replay.maximumVias==source.prefixIds.length) {
            Object seed=Array.get(field(field(detachedBranch,"queue"),"values"),0);
            Method pathViaFits=detachedSearch.getClass().getDeclaredMethod(
                "pathViaFits",seed.getClass());
            pathViaFits.setAccessible(true);
            require(!((Boolean)invoke(pathViaFits,detachedSearch,seed)).booleanValue(),
                "original literal VIA cap rejects another transition at the existing VIA");
        }
        require(intField(field(replay.search,"budget"),"maximum")==source.budgetMaximum,
            "detached copy preserves the source expansion ceiling");
        transferred=true;
        return replay;
        } finally {
            copy.cancel();
            if(!transferred && copiedReplay!=null) copiedReplay.release();
        }
    }

    private static void verifyCopyLifecycle(Source source) throws Exception {
        final PcbLayerRoutingPrototype.DetachedBranchCopyTask task=
            PcbLayerRoutingPrototype.beginDetachedBranchCopy(source.session,
                detachedNoopObserver());
        try {
        expectIllegalArgument(new Checked(){public void run() { task.advance(0); }},
            "zero copy quantum is rejected");
        expectIllegalArgument(new Checked(){public void run() { task.advance(MAX_COPY_QUANTUM+1); }},
            "over-maximum copy quantum is rejected");
        require(task.workSteps()==0 && !task.isComplete(),
            "invalid quantum requests leave the fresh copy task unchanged");
        task.advance(1);
        require(task.lastWork()==1 && task.workSteps()>0 && !task.isComplete(),
            "one-unit owner-copy advancement makes bounded partial progress");
        task.cancel();
        require(!task.isComplete() && task.lastWork()==0,
            "canceled partial owner copy drops work without reporting completion");
        expectLifecycleRejected(new Checked(){public void run() { task.result(); }},
            "canceled owner copy exposes no partial result");
        expectLifecycleRejected(new Checked(){public void run() { task.advance(1); }},
            "canceled owner copy cannot resume");
        require(task.lastWork()==0,
            "rejected advance after cancellation reports no new work");
        } finally { task.cancel(); }
        verifySourceUnchanged(source,"canceled partial owner copy");
        assertions++;
    }

    private static void verifyStaleCopyCanaries() throws Exception {
        Source changedLayout=freshSource();
        final PcbLayerRoutingPrototype.DetachedBranchCopyTask changedLayoutTask=beginCopy(changedLayout);
        try {
        changedLayoutTask.advance(1);
        require(!changedLayoutTask.isComplete(),
            "layout-mutation canary starts from a partial copy");
        Rectangle outline=(Rectangle)field(changedLayout.layout,"boardOutline");
        outline.x++;
        expectStaleAdvance(changedLayoutTask,"mutated source layout is rejected during owner copy");
        expectLifecycleRejected(new Checked(){public void run() { changedLayoutTask.result(); }},
            "mutated source layout exposes no replay result");
        } finally { changedLayoutTask.cancel(); }

        Source switched=freshSource();
        final PcbLayerRoutingPrototype.DetachedBranchCopyTask switchedTask=beginCopy(switched);
        try {
        switchedTask.advance(1);
        require(!switchedTask.isComplete(),
            "source-switch canary starts from a partial copy");
        Source successor=freshSource();
        setField(switched.session,"search",successor.search);
        setField(switched.session,"candidate",successor.layout);
        setField(switched.session,"pass",Integer.valueOf(successor.pass));
        expectStaleAdvance(switchedTask,"switched source Search is rejected during owner copy");
        expectLifecycleRejected(new Checked(){public void run() { switchedTask.result(); }},
            "switched source exposes no replay result");
        } finally { switchedTask.cancel(); }

        Source changedCopiedRow=freshSource();
        final PcbLayerRoutingPrototype.DetachedBranchCopyTask rowTask=beginCopy(changedCopiedRow);
        try {
        int copySteps=0;
        while(!rowTask.isComplete() &&
                !("COPY_LAYOUT".equals(String.valueOf(rowTask.phase())) &&
                  rowTask.copiedEntries()>0) && copySteps<MAX_COPY_WORK_STEPS) {
            rowTask.advance(1);
            copySteps++;
        }
        require(!rowTask.isComplete() &&
                "COPY_LAYOUT".equals(String.valueOf(rowTask.phase())) &&
                rowTask.copiedEntries()>0,
            "row-mutation canary waits until layout data has been copied");
        @SuppressWarnings("unchecked")
        java.util.HashMap<Object,Object> pads=(java.util.HashMap<Object,Object>)
            field(changedCopiedRow.layout,"pads");
        require(!pads.isEmpty(),"row-mutation canary has a retained source pad row");
        Object firstKey=pads.keySet().iterator().next();
        pads.put(firstKey,null);
        expectStaleBeforeReady(rowTask,
            "changed source map row is rejected after its corresponding row was copied");
        expectLifecycleRejected(new Checked(){public void run() { rowTask.result(); }},
            "changed copied row exposes no replay result");
        } finally { rowTask.cancel(); }

        Source changedGoal=freshSource();
        final PcbLayerRoutingPrototype.DetachedBranchCopyTask goalTask=beginCopy(changedGoal);
        try {
        int verifySteps=0;
        while(!goalTask.isComplete() &&
                !"VERIFY_SOURCE".equals(goalTask.phase()) && verifySteps<MAX_COPY_WORK_STEPS) {
            goalTask.advance(1);
            verifySteps++;
        }
        require(!goalTask.isComplete() && "VERIFY_SOURCE".equals(goalTask.phase()),
            "primitive-mask canary reaches source verification after all copied state is complete");
        boolean[] sourceGoal=(boolean[])field(changedGoal.branch,"goal");
        require(sourceGoal.length>0,"primitive-mask canary has a source goal entry");
        sourceGoal[0]=!sourceGoal[0];
        expectStaleAdvance(goalTask,
            "changed source primitive goal entry is rejected after its copy completed");
        expectLifecycleRejected(new Checked(){public void run() { goalTask.result(); }},
            "changed primitive goal entry exposes no replay result");
        } finally { goalTask.cancel(); }
    }

    private static void verifyChargedRouterCopyFailureCanaries() throws Exception {
        RouterCopyFixture changedSource=routerCopyFixture();
        PcbBoardLayout sourceTarget=changedSource.layout.copyForCompaction();
        PcbNetRouter.Router.ChargedFixedGraphCopyTask sourceTask=
            changedSource.router.beginChargedFixedGraphObservationCopy(sourceTarget,
                detachedNoopObserver());
        advanceRouterUntilCopied(sourceTask,"source-layout revision canary");
        changedSource.layout.setRoutingStatistics(changedSource.layout.getRoutingExpansions()+1,
            changedSource.layout.getRawRoutingSegments(),
            changedSource.layout.getRoutingCongestionRejections());
        expectRouterStale(sourceTask,"source-layout revision change is rejected before completion");
        expectRouterResultUnavailable(sourceTask,"source-layout revision change");

        RouterCopyFixture changedTarget=routerCopyFixture();
        PcbBoardLayout target=changedTarget.layout.copyForCompaction();
        PcbNetRouter.Router.ChargedFixedGraphCopyTask targetTask=
            changedTarget.router.beginChargedFixedGraphObservationCopy(target,
                detachedNoopObserver());
        advanceRouterUntilCopied(targetTask,"target-layout revision canary");
        target.setRoutingStatistics(target.getRoutingExpansions()+1,
            target.getRawRoutingSegments(),target.getRoutingCongestionRejections());
        expectRouterStale(targetTask,"target-layout revision change is rejected before completion");
        expectRouterResultUnavailable(targetTask,"target-layout revision change");

        RouterCopyFixture observerFixture=routerCopyFixture();
        PcbBoardLayout observerTarget=observerFixture.layout.copyForCompaction();
        final ThrowingAttemptObserver observer=new ThrowingAttemptObserver();
        final PcbNetRouter.Router.ChargedFixedGraphCopyTask observerTask=
            observerFixture.router.beginChargedFixedGraphObservationCopy(observerTarget,observer);
        advanceRouterUntilCopied(observerTask,"observer Error cleanup canary");
        require(field(observerTask,"target")!=null && field(observerTask,"copyTask")!=null &&
                intField(observerTask,"copiedEntries")>0 && intField(observerTask,"phase")==1 &&
                !observerTask.isComplete() &&
                observer.checks>0 && field(observerFixture.router,"observer")!=observer,
            "observer Error is armed after detached Router rows and partial arrays exist");
        observer.fail=true;
        boolean observerError=false;
        try { observerTask.advance(1024); }
        catch(AssertionError expected) {
            if(!"charged Router observer failure canary".equals(expected.getMessage()))
                throw new AssertionError("Unexpected charged Router observer Error",expected);
            observerError=true;
            assertions++;
        }
        require(observerError && booleanField(observerTask,"failed") && !observerTask.isComplete(),
            "observer Error fails the charged copy closed before a complete result");
        String[] borrowedFields={"source","target","result","sourceLayoutForCheck",
            "targetLayout","targetObserver","copyTask","comparisonTask"};
        for(String name:borrowedFields)
            require(field(observerTask,name)==null,
                "observer Error releases charged Router task reference "+name);
        expectRouterResultUnavailable(observerTask,"observer Error");
    }

    private static RouterCopyFixture routerCopyFixture() {
        P06FactoryLinkFixtures.Fixture fixture=new P06FactoryLinkFixtures.Fixture(true,0,0);
        PcbNetRouter.Router router=new PcbNetRouter.Router(fixture.layout,fixture.board,
            fixture.layout.getBoardOutline(),0,P06FactoryLinkFixtures.OBSERVER,PcbCopperLayer.TOP);
        return new RouterCopyFixture(fixture.layout,router);
    }

    private static void advanceRouterUntilCopied(
            PcbNetRouter.Router.ChargedFixedGraphCopyTask task,String label) {
        int steps=0;
        while(!task.isComplete() && task.copiedEntries()==0 &&
                steps<MAX_COPY_WORK_STEPS) {
            task.advance(1);
            steps++;
        }
        require(!task.isComplete() && task.copiedEntries()>0 && task.workSteps()>0,
            label+" advances through the real charged copy cursor until a source row is copied");
    }

    private static void expectRouterStale(
            PcbNetRouter.Router.ChargedFixedGraphCopyTask task,String label) {
        try { task.advance(1); }
        catch(IllegalStateException expected) { assertions++; return; }
        throw new AssertionError("Expected charged Router stale-source rejection: "+label);
    }

    private static void expectRouterResultUnavailable(
            final PcbNetRouter.Router.ChargedFixedGraphCopyTask task,final String label)
            throws Exception {
        require(!task.isComplete(),label+" remains incomplete");
        expectLifecycleRejected(new Checked(){public void run() { task.result(); }},
            label+" exposes no partial result");
    }

    private static final class RouterCopyFixture {
        final PcbBoardLayout layout;
        final PcbNetRouter.Router router;
        RouterCopyFixture(PcbBoardLayout layout,PcbNetRouter.Router router) {
            this.layout=layout; this.router=router;
        }
    }

    private static final class ThrowingAttemptObserver
            implements SeededPcbLayoutGenerator.AttemptObserver {
        int checks;
        boolean fail;
        public void check(int attempt) {
            checks++;
            if(fail) throw new AssertionError("charged Router observer failure canary");
        }
    }

    private static Source freshSource() throws Exception {
        return freshSource(SOURCE_VIA_CAP);
    }

    private static Source freshSource(int maximumVias) throws Exception {
        LiteralFixture fixture=literalSourceFixture();
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(
            fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS,maximumVias);
        return captureConsumerEntry(fixture,session);
    }

    private static PcbLayerRoutingPrototype.DetachedBranchCopyTask beginCopy(Source source) {
        return PcbLayerRoutingPrototype.beginDetachedBranchCopy(source.session,
            detachedNoopObserver());
    }

    private static SeededPcbLayoutGenerator.AttemptObserver detachedNoopObserver() {
        return new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };
    }

    private static void expectStaleAdvance(final PcbLayerRoutingPrototype.DetachedBranchCopyTask task,
            String label) throws Exception {
        try { task.advance(1); }
        catch(IllegalStateException expected) { assertions++; return; }
        throw new AssertionError("Expected stale-copy rejection: "+label);
    }

    private static void expectStaleBeforeReady(
            final PcbLayerRoutingPrototype.DetachedBranchCopyTask task,String label) {
        for(int steps=0;steps<MAX_COPY_WORK_STEPS;steps++) {
            try { task.advance(1); }
            catch(IllegalStateException expected) { assertions++; return; }
            if(task.isComplete())
                throw new AssertionError("Stale copy became ready before rejection: "+label);
        }
        throw new AssertionError("Stale-copy verification exceeded its bounded guard: "+label);
    }

    private static void completeReplay(Replay replay,int quantum) throws Exception {
        completeReplay(replay,quantum,null);
    }

    private static void completeReplay(final Replay replay,final int quantum,
            final ContinuationBudget budget) throws Exception {
        final int[] calls={0};
        while(!replay.handle.isComplete()) {
            if(budget==null) {
                int beforeExpanded=replay.owner.expansions();
                replay.handle.advance(quantum);
                int used=replay.handle.lastWork();
                require(used>0 && used<=quantum &&
                        replay.owner.expansions()-beforeExpanded<=used,
                    "detached route replay charges bounded actual work without invented expansions");
                if(++calls[0]>MAX_REPLAY_UNITS)
                    throw new AssertionError("detached literal replay exceeded its bounded progress guard");
            } else budget.runMeasured("branch-replay",new Operation<Void>() {public Void run() {
                while(!replay.handle.isComplete() && budget.remainingBatchWork()>0) {
                    final int callQuantum=Math.min(quantum,budget.remainingBatchWork());
                    int beforeExpanded=replay.owner.expansions();
                    try { replay.handle.advance(callQuantum); }
                    finally { budget.recordWork(replay.handle.lastWork()); }
                    int used=replay.handle.lastWork();
                    require(used>=0 && used<=callQuantum &&
                            replay.owner.expansions()-beforeExpanded<=used,
                        "detached route replay charges bounded actual work without invented expansions");
                    if(++calls[0]>MAX_REPLAY_UNITS)
                        throw new AssertionError("detached literal replay exceeded its bounded progress guard");
                    if(used==0) break;
                }
                return null;
            }});
        }
        if(budget==null) replay.handle.advance(quantum);
        else budget.run("branch-replay",new Operation<Void>() {public Void run() {
            replay.handle.advance(quantum); return null;
        }});
        require(replay.handle.isComplete() && replay.handle.lastWork()==0,
            "repeated complete-replay calls are idempotent");
        if(budget==null) completeReplayEvidence(replay);
        else budget.run("branch-replay",new Operation<Void>() {public Void run() throws Exception {
            completeReplayEvidence(replay); return null;
        }});
    }

    private static void completeReplayEvidence(Replay replay) throws Exception {
        replay.publisher=replay.handle.getPublisherForDiagnostics();
        require(replay.publisher!=null && field(replay.search,"branch")==null &&
                field(replay.search,"publisher")==null,
            "normal Search completed the detached branch Publisher");
        require(replay.handle.isComplete() && replay.handle.lastWork()==0 &&
                replay.handle.getPublisherForDiagnostics()==replay.publisher,
            "repeated complete-replay calls are idempotent");
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
        Replay replay=new Replay(source,null,null,source.owner,source.search,source.branch,
            source.layout,source.maximumVias,source.viaCost,null,null);
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
        if(intField(replay.owner,"maximumVias")!=replay.maximumVias ||
                intField(replay.search,"maximumVias")!=replay.maximumVias ||
                intField(replay.owner,"viaCost")!=replay.viaCost ||
                intField(replay.search,"viaCost")!=replay.viaCost)
            throw new IllegalArgumentException("Detached Session/Search VIA parameters differ from the request");
        if(intField(source.owner,"maximumVias")!=source.maximumVias ||
                intField(source.search,"maximumVias")!=source.maximumVias ||
                intField(source.owner,"viaCost")!=source.viaCost ||
                intField(source.search,"viaCost")!=source.viaCost)
            throw new IllegalArgumentException("Detached copy changed the source Session/Search VIA parameters");
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
        Replay replay=buildDetached(source,MAX_COPY_QUANTUM);
        try {
        Object detachedBudget=field(replay.search,"budget");
        setField(detachedBudget,"maximum",Integer.valueOf(source.budgetExpanded));
        require(intField(detachedBudget,"maximum")==source.budgetExpanded &&
                intField(field(source.search,"budget"),"maximum")==source.budgetMaximum,
            "exhausted-budget control limits only the detached copy after owner cloning");
        int before=intField(field(replay.search,"budget"),"expanded");
        boolean rejected=false;
        for(int calls=0;calls<MAX_REPLAY_UNITS && !rejected;calls++) {
            try { replay.handle.advance(1); }
            catch(RuntimeException expected) {
                String exhaustedName=PcbLayerRoutingPrototype.class.getName()+"$Exhausted";
                if(!exhaustedName.equals(expected.getClass().getName()) ||
                        !(source.currentNet+":TOTAL_SEARCH_LIMIT").equals(expected.getMessage()))
                    throw new AssertionError("Unexpected exhausted-budget failure",expected);
                assertions++;
                rejected=true;
            }
        }
        require(rejected && !replay.handle.isComplete() && field(replay.branch,"found")==null &&
                field(replay.search,"publisher")==null &&
                intField(field(replay.search,"budget"),"expanded")==before &&
                replay.layout.geometryFingerprint().equals(source.layoutFingerprint),
            "exhausted replay exposes no success and leaves detached geometry unchanged");
        } finally { replay.handle.release(); }
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
                intField(first.publisher,"bends")==intField(second.publisher,"bends") &&
                first.viaCost==second.viaCost,
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
                intField(source.session,"maximumVias")==source.maximumVias &&
                intField(source.search,"maximumVias")==source.maximumVias &&
                intField(source.session,"viaCost")==source.viaCost &&
                intField(source.search,"viaCost")==source.viaCost &&
                field(source.search,"branch")==source.branch &&
                field(source.search,"publisher")==null &&
                field(source.session,"candidate")==source.layout &&
                field(source.owner,"observer")==source.observerRef,
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
        String[] layoutContainers={"pads","components","silkscreenLabels","traces","regions","holes"};
        for(String name:layoutContainers)
            if(field(source.layout,name)==field(replay.layout,name))
                throw new IllegalArgumentException("Detached replay shares mutable layout container: "+name);
        if(field(source.search,"nets")==field(replay.search,"nets") ||
                field(source.search,"currentPadIds")==field(replay.search,"currentPadIds") ||
                field(source.search,"reachedPadIds")==field(replay.search,"reachedPadIds") ||
                field(source.branch,"goal")==field(replay.branch,"goal") ||
                field(source.branch,"distance")==field(replay.branch,"distance") ||
                field(source.branch,"reachedTerminals")==field(replay.branch,"reachedTerminals") ||
                field(source.branch,"holes")==field(replay.branch,"holes") ||
                field(source.branch,"queue")==field(replay.branch,"queue") ||
                field(source.search,"faces")==field(replay.search,"faces"))
            throw new IllegalArgumentException("Detached replay shares a mutable branch or Search array");
        Object sourceQueueValues=field(source.queueRef,"values");
        Object replayQueueValues=field(field(replay.branch,"queue"),"values");
        if(sourceQueueValues==replayQueueValues)
            throw new IllegalArgumentException("Detached replay shares its mutable frontier array");
        PcbNetRouter.Router[] sourceFaceArray=(PcbNetRouter.Router[])sourceFaces;
        PcbNetRouter.Router[] replayFaceArray=(PcbNetRouter.Router[])replayFaces;
        if(sourceFaceArray.length!=2 || replayFaceArray.length!=2)
            throw new IllegalArgumentException("Detached replay has incomplete router faces");
        for(int i=0;i<2;i++) if(sourceFaceArray[i]==replayFaceArray[i])
            throw new IllegalArgumentException("Detached replay shares a Router face");
        for(int i=0;i<2;i++) if(field(replayFaceArray[i],"layout")!=replay.layout)
            throw new IllegalArgumentException("Detached Router face is not bound to its cloned layout");
        String[] routerArrays={"occupiedNet","clearanceNet","pads","padNets","padWidths","padBounds",
            "escapeCorridors","escapeReservations","components","courtyards",
            "allComponentIndexes","componentCandidatesByColumn","collisionCourtyards",
            "horizontalReservation","verticalReservation","padAt"};
        for(int i=0;i<2;i++) for(String name:routerArrays) {
            Object array=field(replayFaceArray[i],name);
            requireArrayIsolation(array,field(sourceFaceArray[i],name),
                field(source.faceSnapshots[i],name),"Router."+name);
        }
        require(field(replay.owner,"observer")==replay.observer &&
                replay.observer!=source.observerRef && replay.observerChecks!=null,
            "detached replay retains its private attempt observer");
        assertions++;
    }

    private static void requireArrayIsolation(Object actual,Object live,Object snapshot,
            String name) throws Exception {
        if(actual==null || live==null || snapshot==null) {
            if(actual!=live || actual!=snapshot)
                throw new IllegalArgumentException("Detached array nullness changed: "+name);
            return;
        }
        if(actual==live || actual==snapshot)
            throw new IllegalArgumentException("Detached replay reused mutable array: "+name);
        if(!actual.getClass().isArray() || !live.getClass().isArray() ||
                !snapshot.getClass().isArray()) return;
        int length=Array.getLength(actual);
        if(Array.getLength(live)!=length || Array.getLength(snapshot)!=length)
            throw new IllegalArgumentException("Detached array shape changed: "+name);
        Class<?> component=actual.getClass().getComponentType();
        if(component.isArray()) {
            for(int i=0;i<length;i++)
                requireArrayIsolation(Array.get(actual,i),Array.get(live,i),
                    Array.get(snapshot,i),name+"["+i+"]");
        } else if(component==Rectangle.class) {
            for(int i=0;i<length;i++)
                if(Array.get(actual,i)==Array.get(live,i) ||
                        Array.get(actual,i)==Array.get(snapshot,i))
                    throw new IllegalArgumentException("Detached replay reused mutable rectangle: "+name);
        }
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

    private static void expectIllegalArgument(Checked check,String label) throws Exception {
        try { check.run(); }
        catch(IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError("Expected invalid-quantum rejection: "+label);
    }

    private static void expectLifecycleRejected(Checked check,String label) throws Exception {
        try { check.run(); }
        catch(IllegalStateException expected) { assertions++; return; }
        throw new AssertionError("Expected lifecycle rejection: "+label);
    }

    private static void expectLifecycleRejected(final ContinuationBudget budget,
            final String phase,final Checked check,final String label) throws Exception {
        budget.run(phase,new Operation<Void>() {public Void run() throws Exception {
            expectLifecycleRejected(check,label); return null;
        }});
    }

    private static void expectIllegalArgument(final ContinuationBudget budget,
            final String phase,final Checked check,final String label) throws Exception {
        budget.run(phase,new Operation<Void>() {public Void run() throws Exception {
            expectIllegalArgument(check,label); return null;
        }});
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
        final Object observerRef;
        final Object faceArrayRef,workspaceRef;
        final PcbNetRouter.Router[] faceRefs,faceSnapshots;
        final String[] faceEffects;
        final int pass,branchOrdinal,serial,branches,segments,netIndex,maximumVias,viaCost;
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
            this.observerRef=field(owner,"observer");
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
            this.maximumVias=intField(search,"maximumVias");
            this.viaCost=intField(session,"viaCost"); this.sourceUnits=sourceUnits;
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
                    intField(search,"viaCost")==viaCost &&
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
        final PcbLayerRoutingPrototype.DetachedBranchCopyTask copyTask;
        final PcbLayerRoutingPrototype.DetachedBranchReplay handle;
        final PcbLayerRoutingPrototype.Session owner;
        final Object search,branch;
        final SeededPcbLayoutGenerator.AttemptObserver observer;
        final int[] observerChecks;
        final PcbBoardLayout layout;
        final PcbPadPlacement start,root;
        final int maximumVias,viaCost,budgetMaximum;
        Object publisher;
        int[][] pathRows;
        String[] traceDelta,postHoleIds,postHoleFingerprints;
        String postGeometry,postGraph;
        PcbBoardHole foundVia;
        String foundViaId,foundViaFingerprint;
        int foundX,foundY,foundLayer,foundTransitions;

        Replay(Source source,PcbLayerRoutingPrototype.DetachedBranchCopyTask copyTask,
                PcbLayerRoutingPrototype.DetachedBranchReplay handle,
                PcbLayerRoutingPrototype.Session owner,Object search,Object branch,
                PcbBoardLayout layout,int maximumVias,int viaCost,
                SeededPcbLayoutGenerator.AttemptObserver observer,int[] observerChecks)
                throws Exception {
            this.source=source; this.copyTask=copyTask; this.handle=handle;
            this.owner=owner; this.search=search; this.branch=branch;
            this.observer=observer; this.observerChecks=observerChecks;
            this.layout=layout; this.maximumVias=maximumVias; this.viaCost=viaCost;
            this.budgetMaximum=search==null?source.budgetMaximum:
                intField(field(search,"budget"),"maximum");
            this.start=(PcbPadPlacement)field(branch,"start");
            this.root=(PcbPadPlacement)field(branch,"root");
        }
    }

    private static final class ContinuationRun {
        final PcbLayerRoutingPrototype.Result result;
        final long totalWork;
        final int calls;
        ContinuationRun(PcbLayerRoutingPrototype.Result result,long totalWork,int calls) {
            this.result=result; this.totalWork=totalWork; this.calls=calls;
        }
    }

    private static final class GateObserver implements SeededPcbLayoutGenerator.AttemptObserver {
        int calls,successfulCallsBeforeFailure=-1;
        int rejectedAdvanceReentries,rejectedTakeReentries;
        Runnable nextAction;
        void failAfter(int successfulCalls) {
            if(successfulCalls<0) throw new IllegalArgumentException("Negative observer failure delay");
            successfulCallsBeforeFailure=successfulCalls;
        }
        void runOnNextCheck(Runnable action) {
            if(action==null) throw new IllegalArgumentException("Missing observer canary action");
            nextAction=action;
        }
        public void check(int attempt) {
            calls++;
            Runnable action=nextAction; nextAction=null;
            if(action!=null) action.run();
            if(successfulCallsBeforeFailure==0) {
                successfulCallsBeforeFailure=-1;
                throw new IllegalStateException("detached continuation observer canary");
            }
            if(successfulCallsBeforeFailure>0) successfulCallsBeforeFailure--;
        }
    }

    private interface Operation<T> { T run() throws Exception; }

    /** One uninterrupted ledger for the new source/copy/replay/handoff/remainder fixture. */
    private static final class ContinuationBudget {
        private static final long MAX_ELAPSED_NANOS=90000000000L;
        private static final long MAX_UNIT_NANOS=5000000000L;
        private static final int MAX_UNITS=640;
        private static final int MAX_WORK_PER_UNIT=32768;
        static final int MAX_BATCH_WORK=8192;
        private static final String[] PHASES={"source-setup","source-prep","copy","branch-replay",
            "handoff","remainder","oracle","lifecycle","control"};
        private final long started=System.nanoTime();
        private final int[] phaseUnits=new int[PHASES.length];
        private final long[] phaseWork=new long[PHASES.length];
        private int units;
        private int unitWork,maxUnitWork;
        private long totalWork;
        private String unitPhase;
        private boolean inUnit;
        <T> T run(Operation<T> operation) throws Exception {
            return run("control",operation);
        }
        <T> T run(String phase,Operation<T> operation) throws Exception {
            return execute(phase,operation);
        }
        <T> T runMeasured(String phase,Operation<T> operation) throws Exception {
            return execute(phase,operation);
        }
        private <T> T execute(String phase,Operation<T> operation) throws Exception {
            if(operation==null) throw new IllegalArgumentException("Missing bounded owner operation");
            int index=phaseIndex(phase);
            if(inUnit) throw new IllegalStateException("Q100 controller units cannot nest");
            checkElapsed();
            if(++units>MAX_UNITS)
                throw new AssertionError(summary("exceeded 640 caller units"));
            phaseUnits[index]++;
            unitPhase=phase; unitWork=0; inUnit=true;
            long unitStarted=System.nanoTime();
            T result;
            try {
                result=operation.run();
            } catch(Exception failure) {
                finishUnitWithCheck(unitStarted);
                throw failure;
            } catch(Error failure) {
                finishUnitWithCheck(unitStarted);
                throw failure;
            }
            try { checkUnit(unitStarted); }
            finally { finishUnit(); }
            return result;
        }
        void recordWork(int actualWork) {
            if(!inUnit) throw new IllegalStateException("Owner work reported outside a controller unit");
            if(actualWork<0 || unitWork>MAX_WORK_PER_UNIT-actualWork)
                throw new AssertionError(summary("controller unit exceeded 32768 actual owner steps"));
            if(actualWork==0) {
                if(unitWork>0) recordNestedControlUnit();
                return;
            }
            unitWork+=actualWork; totalWork+=actualWork;
            phaseWork[phaseIndex(unitPhase)]+=actualWork;
            if(unitWork>maxUnitWork) maxUnitWork=unitWork;
        }
        int currentUnitWork() {
            if(!inUnit) throw new IllegalStateException("No active Q100 controller unit");
            return unitWork;
        }
        int remainingUnitWork() { return MAX_WORK_PER_UNIT-currentUnitWork(); }
        int remainingBatchWork() {
            return Math.min(MAX_BATCH_WORK-unitWork,remainingUnitWork());
        }
        void recordNestedControlUnit() {
            if(!inUnit) throw new IllegalStateException("Control work reported outside a controller unit");
            checkElapsed();
            if(++units>MAX_UNITS) throw new AssertionError(summary("exceeded 640 caller units"));
            phaseUnits[phaseIndex(unitPhase)]++;
        }
        void checkElapsed() {
            long elapsed=System.nanoTime()-started;
            if(elapsed>MAX_ELAPSED_NANOS)
                throw new AssertionError(summary("exceeded 90 seconds"));
        }
        private void checkUnit(long unitStarted) {
            long duration=System.nanoTime()-unitStarted;
            checkElapsed();
            if(duration>MAX_UNIT_NANOS)
                throw new AssertionError(summary("controller unit exceeded five seconds; phase="+
                    unitPhase+" unitWork="+unitWork+" durationMillis="+(duration/1000000L)));
            if(unitWork>maxUnitWork) maxUnitWork=unitWork;
        }
        private void finishUnit() { inUnit=false; unitPhase=null; unitWork=0; }
        private void finishUnitWithCheck(long unitStarted) {
            try { checkUnit(unitStarted); }
            finally { finishUnit(); }
        }
        private int phaseIndex(String phase) {
            if(phase==null) throw new IllegalArgumentException("Missing Q100 phase name");
            for(int i=0;i<PHASES.length;i++) if(PHASES[i].equals(phase)) return i;
            throw new IllegalArgumentException("Unknown Q100 phase: "+phase);
        }
        void reportAndCheck() {
            checkElapsed();
            long phaseWorkTotal=0; int phaseUnitTotal=0;
            for(int i=0;i<PHASES.length;i++) {
                phaseWorkTotal+=phaseWork[i]; phaseUnitTotal+=phaseUnits[i];
            }
            if(units>MAX_UNITS || maxUnitWork>MAX_WORK_PER_UNIT ||
                    maxUnitWork>MAX_BATCH_WORK || totalWork!=phaseWorkTotal || units!=phaseUnitTotal)
                throw new AssertionError(summary("Q100 budget accounting is inconsistent"));
            System.out.println(summary("complete"));
        }
        private String summary(String state) {
            StringBuilder phases=new StringBuilder();
            for(int i=0;i<PHASES.length;i++) {
                if(i>0) phases.append(',');
                phases.append(PHASES[i]).append('=')
                    .append(phaseUnits[i]).append('/').append(phaseWork[i]);
            }
            return "Q100_CONTINUATION_BUDGET state="+state+" units="+units+
                " ownerWork="+totalWork+" maxUnitWork="+maxUnitWork+" elapsedMillis="+
                ((System.nanoTime()-started)/1000000L)+" phaseUnits/Work="+phases;
        }
    }

    private interface Checked { void run() throws Exception; }
}
