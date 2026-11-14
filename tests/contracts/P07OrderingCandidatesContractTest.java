package com.lushprojects.circuitjs1.client;

import java.util.TreeSet;
import java.util.Vector;
import java.util.UUID;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/** Maintained focused P07 ordering-stream contract and independent physical checks. */
public final class P07OrderingCandidatesContractTest {
    private static int checks;
    private static final SeededPcbLayoutGenerator.AttemptObserver NOOP_OBSERVER =
        new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };
    private static final class OptionalBranchMetrics {
        final boolean supported, started, complete, observationChecked, liveLayoutStable,
            routerStateStable, routerEffectsStable;
        final int workUnits, ordinaryProbes, viaProbes, refreshes;
        OptionalBranchMetrics(boolean supported, boolean started, boolean complete,
                int workUnits, int ordinaryProbes, int viaProbes, int refreshes,
                boolean observationChecked, boolean liveLayoutStable,
                boolean routerStateStable, boolean routerEffectsStable) {
            this.supported=supported; this.started=started; this.complete=complete;
            this.workUnits=workUnits; this.ordinaryProbes=ordinaryProbes;
            this.viaProbes=viaProbes; this.refreshes=refreshes;
            this.observationChecked=observationChecked; this.liveLayoutStable=liveLayoutStable;
            this.routerStateStable=routerStateStable; this.routerEffectsStable=routerEffectsStable;
        }
        static OptionalBranchMetrics unsupported() {
            return new OptionalBranchMetrics(false,false,false,0,0,0,0,false,false,false,false);
        }
    }
    private static final class FinalizedVariant {
        final boolean invoked, accepted, ownerWinner;
        final String status, reason, geometrySha256, layout24PrefixReferenceSha256;
        final int x, y, width, height;
        final long area, qualityMilli;
        final double quality;
        final AcceptedRouteEvidence evidence;
        FinalizedVariant(boolean invoked, boolean accepted, boolean ownerWinner,
                String status, String reason,
                String geometrySha256, String layout24PrefixReferenceSha256,
                int x, int y, int width, int height,
                long area, double quality, long qualityMilli, AcceptedRouteEvidence evidence) {
            this.invoked=invoked; this.accepted=accepted; this.ownerWinner=ownerWinner;
            this.status=status; this.reason=reason;
            this.geometrySha256=geometrySha256;
            this.layout24PrefixReferenceSha256=layout24PrefixReferenceSha256;
            this.x=x; this.y=y; this.width=width;
            this.height=height; this.area=area; this.quality=quality; this.qualityMilli=qualityMilli;
            this.evidence=evidence;
        }
        static FinalizedVariant notRun(String reason) {
            return new FinalizedVariant(false,false,false,"NOT_RUN",reason,null,null,0,0,0,0,0L,0.0,-1L,null);
        }
        static FinalizedVariant rejected(String type, String reason) {
            return new FinalizedVariant(true,false,false,"FINALIZATION_REJECTED:"+type,reason,
                null,null,0,0,0,0,0L,0.0,-1L,null);
        }
    }
    private static final class OwnerAdapter {
        final SeededPcbLayoutGenerator generator;
        final SeededPcbLayoutGenerator.Session session;
        final MediumBoardPhysicalPolicy.PlacementCandidate candidate;
        final String candidateCanonical,candidateCanonicalSha256;
        final Method finalizeRoute, considerLayout, qualityMilli;
        final Field winnerLayout, winnerQuality, winnerArea, winnerAttempt, winnerPlacementScore;
        OwnerAdapter(TroubleshootBoard board, Rb56Plan plan,
                PcbPlacementPlanner.Plan placementPlan,int candidateAttempt) {
            try {
                generator = new SeededPcbLayoutGenerator();
                session = generator.begin(board,plan.layoutSeed,plan.routingSeed);
                candidate = new MediumBoardPhysicalPolicy.PlacementCandidate(board,candidateAttempt,placementPlan);
                require(MediumBoardPhysicalPolicy.selected(board.getPlacementConstraints()),
                    "owner adapter session derives the real RB56 Medium policy from its board constraints");
                require(candidate.attempt==candidateAttempt && candidate.plan==placementPlan,
                    "owner adapter preserves the exact board-aware candidate identity");
                candidateCanonical=candidate.toCanonical();
                candidateCanonicalSha256=PlayerSessionFingerprint.of(candidateCanonical);
                Field mediumPolicy=session.getClass().getDeclaredField("mediumPolicy");
                Field sessionAttempt=session.getClass().getDeclaredField("attempt");
                Field sessionComplete=session.getClass().getDeclaredField("complete");
                mediumPolicy.setAccessible(true); sessionAttempt.setAccessible(true);
                sessionComplete.setAccessible(true);
                require(mediumPolicy.getBoolean(session) && sessionAttempt.getInt(session)==0 &&
                    !sessionComplete.getBoolean(session),
                    "test-only owner session starts from production-derived density-7 flags without advancing generation");
                finalizeRoute = SeededPcbLayoutGenerator.class.getDeclaredMethod(
                    "finishMediumTwoLayerRoute",TroubleshootBoard.class,Long.TYPE,
                    MediumBoardPhysicalPolicy.PlacementCandidate.class,
                    PcbLayerRoutingPrototype.Result.class);
                finalizeRoute.setAccessible(true);
                considerLayout = findConsiderMethod(SeededPcbLayoutGenerator.class);
                considerLayout.setAccessible(true);
                qualityMilli = SeededPcbLayoutGenerator.class.getDeclaredMethod(
                    "routeQualityMilli",Double.TYPE);
                qualityMilli.setAccessible(true);
                winnerLayout = session.getClass().getDeclaredField("mediumTwoLayerLayout");
                winnerQuality = session.getClass().getDeclaredField("mediumTwoLayerQuality");
                winnerArea = session.getClass().getDeclaredField("mediumTwoLayerArea");
                winnerAttempt = session.getClass().getDeclaredField("mediumTwoLayerPlacementAttempt");
                winnerPlacementScore = session.getClass().getDeclaredField("mediumTwoLayerPlacementScore");
                winnerLayout.setAccessible(true); winnerQuality.setAccessible(true);
                winnerArea.setAccessible(true); winnerAttempt.setAccessible(true);
                winnerPlacementScore.setAccessible(true);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not bind the existing RB56 Medium owner adapter",failure);
            }
        }
        FinalizedVariant finalizeAndConsider(TroubleshootBoard board,long seed,
                PcbLayerRoutingPrototype.Result result) {
            final Object routed;
            try {
                routed = finalizeRoute.invoke(generator,board,Long.valueOf(seed),candidate,result);
            } catch (InvocationTargetException wrapped) {
                Throwable cause=wrapped.getCause();
                if (cause==null) throw new IllegalStateException("RB56 finalizer threw without a cause",wrapped);
                if (isExpectedFinalizationRejection(cause))
                    return FinalizedVariant.rejected(cause.getClass().getSimpleName(),cause.getMessage());
                throw rethrowUnexpected("Unexpected RB56 finalizer failure",cause);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Could not invoke the existing RB56 finalizer",failure);
            }
            try {
                Field routedPlan = routed.getClass().getDeclaredField("plan");
                Field routedLayout = routed.getClass().getDeclaredField("layout");
                routedPlan.setAccessible(true); routedLayout.setAccessible(true);
                require(routedPlan.get(routed)!=null,
                    "existing finalizer returns its actual compacted candidate plan");
                PcbBoardLayout finalized=(PcbBoardLayout)routedLayout.get(routed);
                require(finalized!=null,"existing finalizer returns a real finalized layout");
                AcceptedRouteEvidence finalEvidence=validateAcceptedOutput(board,finalized);
                considerLayout.invoke(generator,session,candidate,routed,Boolean.TRUE);
                Rectangle bounds=finalized.getBoardOutline();
                double quality=finalized.getRouteQualityScore(board);
                long milli=((Long)qualityMilli.invoke(null,Double.valueOf(quality))).longValue();
                require(!Double.isNaN(quality) && !Double.isInfinite(quality) && milli>=0,
                    "existing route quality and its existing milli conversion are finite");
                boolean ownerWinner=winnerLayout.get(session)==finalized;
                if (ownerWinner)
                    require(winnerAttempt.getInt(session)==candidate.attempt &&
                        winnerArea.getLong(session)==(long)bounds.width*bounds.height &&
                        Double.compare(winnerQuality.getDouble(session),quality)==0,
                        "actual owner winner fields match the just-considered finalized candidate");
                return new FinalizedVariant(true,true,ownerWinner,"FINALIZED_AND_CONSIDERED_BY_EXISTING_OWNER",
                    null,PlayerSessionFingerprint.of(finalized.geometryFingerprint()),
                    PcbCompactnessContractTest.layout24PrefixReferenceSha256(finalized),
                    bounds.x,bounds.y,bounds.width,bounds.height,
                    (long)bounds.width*bounds.height,quality,milli,finalEvidence);
            } catch (InvocationTargetException wrapped) {
                Throwable cause=wrapped.getCause();
                if (cause==null) throw new IllegalStateException("RB56 owner comparator threw without a cause",wrapped);
                throw rethrowUnexpected("Existing RB56 owner comparator failed",cause);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not read the existing RB56 finalized candidate",failure);
            }
        }
        String winnerJson() {
            try {
                PcbBoardLayout layout=(PcbBoardLayout)winnerLayout.get(session);
                if (layout==null) return "null";
                Rectangle bounds=layout.getBoardOutline();
                double quality=winnerQuality.getDouble(session);
                long milli=((Long)qualityMilli.invoke(null,Double.valueOf(quality))).longValue();
                require(!Double.isNaN(quality) && !Double.isInfinite(quality) && milli>=0,
                    "existing owner winner score and actual milli conversion are finite");
                return "{\"attempt\":"+winnerAttempt.getInt(session)+
                    ",\"placementCheapScore\":"+winnerPlacementScore.getLong(session)+
                    ",\"area\":"+winnerArea.getLong(session)+
                    ",\"quality\":"+Double.toString(quality)+
                    ",\"qualityMilli\":"+milli+
                    ",\"x\":"+bounds.x+",\"y\":"+bounds.y+
                    ",\"width\":"+bounds.width+",\"height\":"+bounds.height+
                    ",\"geometrySha256\":"+quote(PlayerSessionFingerprint.of(layout.geometryFingerprint()))+
                    ",\"historicalLayout24PrefixGeometrySha256\":"+
                        quote(PcbCompactnessContractTest.layout24PrefixReferenceSha256(layout))+"}";
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not read the actual RB56 owner winner",failure);
            }
        }
    }
    private static OptionalBranchMetrics readOptionalBranchMetrics(
            PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
        final Field[] fields = new Field[10];
        try {
            Class<?> type=snapshot.getClass();
            fields[0]=type.getDeclaredField("relaxedBoundStarted");
            fields[1]=type.getDeclaredField("relaxedBoundComplete");
            fields[2]=type.getDeclaredField("relaxedBoundWorkUnits");
            fields[3]=type.getDeclaredField("relaxedBoundOrdinaryProbes");
            fields[4]=type.getDeclaredField("relaxedBoundViaProbes");
            fields[5]=type.getDeclaredField("relaxedBoundRefreshes");
            fields[6]=type.getDeclaredField("relaxedBoundObservationChecked");
            fields[7]=type.getDeclaredField("relaxedBoundLiveLayoutStable");
            fields[8]=type.getDeclaredField("relaxedBoundRouterStateStable");
            fields[9]=type.getDeclaredField("relaxedBoundRouterEffectsStable");
        } catch (NoSuchFieldException unsupported) {
            return OptionalBranchMetrics.unsupported();
        }
        for (int index=0;index<fields.length;index++) fields[index].setAccessible(true);
        if (fields[0].getType()!=Boolean.TYPE || fields[1].getType()!=Boolean.TYPE ||
                fields[2].getType()!=Integer.TYPE || fields[3].getType()!=Integer.TYPE ||
                fields[4].getType()!=Integer.TYPE || fields[5].getType()!=Integer.TYPE ||
                fields[6].getType()!=Boolean.TYPE || fields[7].getType()!=Boolean.TYPE ||
                fields[8].getType()!=Boolean.TYPE || fields[9].getType()!=Boolean.TYPE)
            throw new IllegalStateException("P07 optional branch metrics have unexpected field types");
        try {
            return new OptionalBranchMetrics(true,fields[0].getBoolean(snapshot),
                fields[1].getBoolean(snapshot),fields[2].getInt(snapshot),fields[3].getInt(snapshot),
                fields[4].getInt(snapshot),fields[5].getInt(snapshot),fields[6].getBoolean(snapshot),
                fields[7].getBoolean(snapshot),fields[8].getBoolean(snapshot),fields[9].getBoolean(snapshot));
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not read optional P07 branch metrics",failure);
        }
    }
    private P07OrderingCandidatesContractTest() { }
    public static void main(String[] args) {
        if (args != null && args.length != 0)
            throw new IllegalArgumentException("The fixed P07 ordering stream contract takes no arguments");
        String runId=UUID.randomUUID().toString();
        int before=checks;
        require(PcbLayerRoutingPrototype.MAX_EXPANSIONS==1000000 &&
            PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS==100000 &&
            PcbLayerRoutingPrototype.MAX_CELLS==250000 &&
            PcbLayerRoutingPrototype.ORDERINGS==3 &&
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias==48,
            "AN preserves the existing P07 request envelope");
        verifyWrongModeApiRejection(runId);
        StreamRun amDiagnostic=runFixturePair(runId,AM_STREAM_FIXTURE,true,true);
        assertAmSharedStream(amDiagnostic);
        StreamRun amDisabled=runFixturePair(runId,AM_STREAM_FIXTURE,false,true);
        verifyStreamParity(amDiagnostic,amDisabled,"AM saved7 shared-budget diagnostic parity");
        emitStreamCase(amDiagnostic,"DIAGNOSTICS_ENABLED");
        emitStreamCase(amDisabled,"DIAGNOSTICS_DISABLED");
        verifySeed5Pair(runId);
        verifyUnguidedLegacyFirstSuccess(runId);
        verifyReducedTotalCaps(runId);
        verifyFirstSuccessStop(runId);
        verifyPostYieldObserverFailure(runId);
        verifyPostYieldDiagnosticFailure(runId);
        verifyDetachedOrderingFinalizer(runId);
        verifyDetachedOrderingBoundary(runId);
        verifyActualP07RequestBoundary(runId,amDiagnostic);
        MediumBoardPhysicalPolicy.Result generated=runActualGeneratorReceiptCase(runId);
        int assertions=checks-before;
        System.out.println("P07_ORDERING_STREAM_SUITE {\"protocol\":"+quote(STREAM_PROTOCOL)+
            ",\"runId\":"+quote(runId)+",\"status\":\"PASS_FOCUSED_CONTRACT_ONLY\""+
            ",\"amSharedExpansions\":683850,\"amYieldedOrderings\":3"+
            ",\"seed5Cases\":2,\"unguidedFirstSuccessFixture\":true"+
            ",\"totalCapCases\":2,\"postYieldObserverLatch\":true"+
            ",\"postYieldDiagnosticsLatch\":true,\"generatorStatisticsVerified\":true"+
            ",\"detachedCurrentOrderingFinalizer\":true"+
            ",\"detachedCurrentOrderingBoundary\":true"+
            ",\"generatorAccepted\":"+generated.accepted()+
            ",\"generatedLayoutSha256\":"+(generated.getLayout()==null?"null":
                quote(PlayerSessionFingerprint.of(generated.getLayout().geometryFingerprint())))+
            ",\"assertions\":"+assertions+
            ",\"scope\":"+quote(STREAM_SCOPE)+
            ",\"layoutQualification\":false,\"adoption\":false}");
        System.out.println("PASS: P07 ordering candidates contracts orderingStream=true " +
            "scope="+STREAM_SCOPE+" layoutQualification=false adoption=false assertions="+assertions);
    }
    private static String optionalMetric(boolean supported, long value) {
        return supported ? Long.toString(value) : "null";
    }
    private static String optionalBoolean(boolean supported, boolean value) {
        return supported ? Boolean.toString(value) : "null";
    }

    private static final String STREAM_PROTOCOL="p07-ordering-stream-contract-v1";
    private static final String STREAM_SCOPE="FOCUSED_SHARED_ORDERING_STREAM_CONTRACT_ONLY";
    private static final int STREAM_WORK_QUANTUM=128;
    private static final int STREAM_MAX_ADVANCES=2000000;
    private static final StreamFixture AM_STREAM_FIXTURE=new StreamFixture(1,
        "SAVED7_ROOT42_CANDIDATE4",42L,7,4,5526715536693200536L,4493419427463069983L,
        "RB56_CH2_SHARED_HYSTERETIC_A_NMOS_B_BJT_S5_true_S12_true_F2_O0_B5_false",
        "52c34368cef8518181ea6b338cb1473ad3bfbd76518b730e9cf144ba958f5f13",134269,
        "731fea0da4d7f9fd2404cc2979013ce41062d99d534324220a59ece71afa4b7e");
    private static final StreamFixture[] SEED5_STREAM_FIXTURES=new StreamFixture[] {
        new StreamFixture(2,"SEED5_V10_CANDIDATE1",5L,10,1,-7630701392004902526L,
            -9106650364266936003L,
            "RB56_CH2_SHARED_DIRECT_A_BJT_B_NMOS_S5_true_S12_false_F1_O3_B5_false",
            "1c6136104db257aebfa692c2c6689fe620a658276062d783f36fbef10dd3cc19",133536,
            "6fa70718324689757eb36cec7a3f5afff1cd3e0d96606929d6df9273f6784ff5"),
        new StreamFixture(3,"SEED5_V11_CANDIDATE1",5L,11,1,-7630701392004902526L,
            -9106650364266936003L,
            "RB56_CH2_SHARED_DIRECT_A_BJT_B_NMOS_S5_true_S12_false_F1_O3_B5_false",
            "b21527ea02b45b9fdfb6233fb116d42a32077da84709674efe3b59b36e15c43f",133367,
            "1ffc2a9c6f98ddafa5a6a0cdeb334968281b8bd8910ab80bedf478d56fcd7f57")
    };
    private static final long[] AM_ORDER_EXPANSION_DELTAS={300782L,244723L,138345L};
    private static final int[] AM_ORDER_VIAS={48,46,48};
    private static final long[] AM_ORDER_LAND_COUNTS={358L,354L,358L};
    private static final String[] AM_ORDER_RAW_GEOMETRY={
        "e29cfd686e110d9be45815b10e590a5bb6e8b369c0def3a3ec0b6477dff623c1",
        "995574d95bcad50479868d58c7a021df90cf83df72c4569bae532bcc91cda352",
        "5d414c56407f2b8f572f25f804301cd8438676140bd00dc8c0c303280eb3bc25"};
    private static final String[] AM_ORDER_RAW_TRACE={
        "37aca52815e73724a72100edd91f360da6e9f9a1f9b8d7f394a27935350dacc3",
        "b1b8f59c7edb0c8199d403876f1e354aad4a9b819799432f56c7cfd0007afe7e",
        "52d1daf7c4bab09ecf311f79ab332b4e4e4cdcfcd4f624ac53ec6af33728ba20"};
    private static final String[] AM_ORDER_FINAL_GEOMETRY={
        "d30c178744295381a174cfb58f9e20b22bf5cdd812847c5d24f598b15fb3cdb4",
        "36e8d1a293b850e8533e731028547f273995ff448ba2b08bc61fb4d247739501",
        "4486304d0971a5e8dbf574bb163f3a02c8c33a6151e851a8329ad93bdbc2f83a"};
    private static final long[] AM_ORDER_FINAL_AREA={6524231L,6524231L,6371585L};
    private static final long[] AM_ORDER_FINAL_QUALITY_MILLI={279700731L,279037635L,276930281L};

    private static final class StreamFixture {
        final int ordinal,candidateAttempt,placementVersion;
        final long rootSeed,placementSeed,routingSeed;
        final String caseId,topology,inputGeometrySha256,logicalInventorySha256;
        final int inputGeometryLength;
        StreamFixture(int ordinal,String caseId,long rootSeed,int placementVersion,
                int candidateAttempt,long placementSeed,long routingSeed,String topology,
                String inputGeometrySha256,int inputGeometryLength,String logicalInventorySha256) {
            this.ordinal=ordinal; this.caseId=caseId; this.rootSeed=rootSeed;
            this.placementVersion=placementVersion; this.candidateAttempt=candidateAttempt;
            this.placementSeed=placementSeed; this.routingSeed=routingSeed; this.topology=topology;
            this.inputGeometrySha256=inputGeometrySha256; this.inputGeometryLength=inputGeometryLength;
            this.logicalInventorySha256=logicalInventorySha256;
        }
    }
    private static final class PreparedStreamCase {
        final StreamFixture fixture;
        final Rb56Plan plan;
        final TroubleshootBoard board;
        final PcbPlacementConstraints constraints;
        final PcbPlacementPlanner.Plan placementPlan;
        final PcbBoardLayout placement;
        final String planCanonical,planSha256,boardCanonical,boardSha256,inputGeometry,inputGeometrySha256,
            inputGeometryLayout24PrefixReferenceSha256;
        final PcbLayerRoutingPrototype.GridAdmission grid;
        PreparedStreamCase(StreamFixture fixture,Rb56Plan plan,TroubleshootBoard board,
                PcbPlacementConstraints constraints,PcbPlacementPlanner.Plan placementPlan,
                PcbBoardLayout placement,String planCanonical,String planSha256,
                String boardCanonical,String boardSha256,String inputGeometry,String inputGeometrySha256,
                String inputGeometryLayout24PrefixReferenceSha256,
                PcbLayerRoutingPrototype.GridAdmission grid) {
            this.fixture=fixture; this.plan=plan; this.board=board; this.constraints=constraints;
            this.placementPlan=placementPlan; this.placement=placement;
            this.planCanonical=planCanonical; this.planSha256=planSha256;
            this.boardCanonical=boardCanonical; this.boardSha256=boardSha256;
            this.inputGeometry=inputGeometry; this.inputGeometrySha256=inputGeometrySha256;
            this.inputGeometryLayout24PrefixReferenceSha256=inputGeometryLayout24PrefixReferenceSha256;
            this.grid=grid;
        }
    }
    private static final class StreamRoute {
        final int ordinal,orderingIndex;
        final long expansionDelta;
        final PcbLayerRoutingPrototype.Result result;
        final String rawGeometrySha256,rawGeometryLayout24PrefixReferenceSha256,rawTraceGeometrySha256;
        final RawCopperSummary copper;
        final AcceptedRouteEvidence rawEvidence;
        final FinalizedVariant finalized;
        StreamRoute(int ordinal,int orderingIndex,long expansionDelta,
                PcbLayerRoutingPrototype.Result result,String rawGeometrySha256,
                String rawGeometryLayout24PrefixReferenceSha256,String rawTraceGeometrySha256,
                RawCopperSummary copper,
                AcceptedRouteEvidence rawEvidence,FinalizedVariant finalized) {
            this.ordinal=ordinal; this.orderingIndex=orderingIndex;
            this.expansionDelta=expansionDelta; this.result=result;
            this.rawGeometrySha256=rawGeometrySha256;
            this.rawGeometryLayout24PrefixReferenceSha256=rawGeometryLayout24PrefixReferenceSha256;
            this.rawTraceGeometrySha256=rawTraceGeometrySha256;
            this.copper=copper; this.rawEvidence=rawEvidence; this.finalized=finalized;
        }
    }
    private static final class BranchAudit {
        final String runId,caseId,mode;
        final boolean emit;
        final int[] perPassEvents={0,0,0};
        int events,lastPass=-1,lastTotalExpanded;
        long branchExpandedSum;
        boolean guidanceStarted;
        BranchAudit(String runId,String caseId,String mode,boolean emit) {
            this.runId=runId; this.caseId=caseId; this.mode=mode; this.emit=emit;
        }
        void observe(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
            require(snapshot!=null,"P07 ordering stream branch callback has a non-null terminal snapshot");
            require(snapshot.pass>=0 && snapshot.pass<PcbLayerRoutingPrototype.ORDERINGS &&
                snapshot.pass>=lastPass,"terminal branch census follows the actual pass order");
            require(snapshot.branchExpanded>=0 &&
                snapshot.branchExpanded<=PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS &&
                snapshot.branchExpanded==snapshot.expandedTop+snapshot.expandedBottom &&
                snapshot.totalExpanded>=lastTotalExpanded && snapshot.frontierCount>=0 &&
                snapshot.bestStateCount>=0 && snapshot.terminalReason!=null,
                "terminal branch census preserves its expansion and frontier invariants");
            int count=++events;
            require(count<=Math.max(1,131*PcbLayerRoutingPrototype.ORDERINGS),
                "branch event census remains bounded by the fixed 131-pad, three-order contract");
            perPassEvents[snapshot.pass]++;
            branchExpandedSum+=snapshot.branchExpanded;
            guidanceStarted|=snapshot.relaxedBoundStarted;
            lastPass=snapshot.pass;
            lastTotalExpanded=snapshot.totalExpanded;
            if(emit) emitStreamBranch(runId,caseId,mode,count,snapshot);
        }
    }
    private static final class StreamRun {
        final PreparedStreamCase prepared;
        final String runId,mode;
        final boolean diagnosticsEnabled;
        final Vector<StreamRoute> routes=new Vector<StreamRoute>();
        final BranchAudit branches;
        final OwnerAdapter owner;
        final PcbLayerRoutingPrototype.OrderingSummary summary;
        final int boundedAdvances;
        final long elapsedNanos;
        StreamRun(PreparedStreamCase prepared,String runId,String mode,boolean diagnosticsEnabled,
                Vector<StreamRoute> routes,BranchAudit branches,OwnerAdapter owner,
                PcbLayerRoutingPrototype.OrderingSummary summary,int boundedAdvances,long elapsedNanos) {
            this.prepared=prepared; this.runId=runId; this.mode=mode;
            this.diagnosticsEnabled=diagnosticsEnabled; this.routes.addAll(routes);
            this.branches=branches; this.owner=owner; this.summary=summary;
            this.boundedAdvances=boundedAdvances; this.elapsedNanos=elapsedNanos;
        }
    }
    private static final class CountingObserver implements SeededPcbLayoutGenerator.AttemptObserver {
        int calls;
        int throwAtCall=-1;
        RuntimeException failure;
        void armNext(RuntimeException failure) {
            if(failure==null || this.failure!=null) throw new IllegalStateException("Invalid observer arm");
            this.failure=failure;
            this.throwAtCall=calls+1;
        }
        public void check(int attempt) {
            calls++;
            if(failure!=null && calls>=throwAtCall) throw failure;
        }
    }

    private static final class RequestBoundaryDiagnostics
            implements PcbLayerRoutingPrototype.Diagnostics {
        int calls;
        PcbLayerRoutingPrototype.BranchSnapshot first;
        final RuntimeException failure;
        final Vector<PcbLayerRoutingPrototype.BranchSnapshot> snapshots=
            new Vector<PcbLayerRoutingPrototype.BranchSnapshot>();
        final int maxSnapshots;
        RequestBoundaryDiagnostics(RuntimeException failure,TroubleshootBoard board) {
            this.failure=failure;
            maxSnapshots=PcbLayerRoutingPrototype.ORDERINGS*
                (board.getPadIds().size()-board.getNetIds().size());
            require(maxSnapshots>0,"request-boundary callback cap derives from the actual board");
        }
        public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
            require(snapshot!=null && snapshots.size()<maxSnapshots,
                "request-boundary collector retains every callback inside its physical census");
            calls++;
            snapshots.add(snapshot);
            if(first==null) first=snapshot;
            if(failure!=null) throw failure;
        }
    }

    private static final class ActualRequestBoundaryRun {
        final Rb56Plan plan;
        final TroubleshootBoard board;
        final SeededPcbLayoutGenerator.Session session;
        final SeededPcbLayoutGenerator.P07RequestBoundaryEvent event;
        final RequestBoundaryDiagnostics diagnostics;
        final int[] observerCalls;
        final int advances;
        final String planBefore,boardBefore;
        ActualRequestBoundaryRun(Rb56Plan plan,TroubleshootBoard board,
                SeededPcbLayoutGenerator.Session session,
                SeededPcbLayoutGenerator.P07RequestBoundaryEvent event,
                RequestBoundaryDiagnostics diagnostics,int[] observerCalls,int advances,
                String planBefore,String boardBefore) {
            this.plan=plan; this.board=board; this.session=session; this.event=event;
            this.diagnostics=diagnostics; this.observerCalls=observerCalls; this.advances=advances;
            this.planBefore=planBefore; this.boardBefore=boardBefore;
        }
    }

    private static final class RequestBoundaryState {
        final String canonical;
        RequestBoundaryState(String canonical) { this.canonical=canonical; }
        boolean sameAs(RequestBoundaryState other) {
            return other!=null && canonical.equals(other.canonical);
        }
    }

    private static final class RemainderOperationBudget {
        private static final int MAX_UNITS=640;
        private static final int MAX_WORK_PER_UNIT=32768;
        private static final long MAX_TOTAL_NANOS=90000000000L;
        private static final long MAX_UNIT_NANOS=5000000000L;
        final long started=System.nanoTime();
        int units,stateTransitions,zeroSearchWorkUnits;
        long realOwnerWork,maxUnitNanos;
        private long unitStarted;
        private String active;

        void begin(String operation) {
            if(active!=null) throw new IllegalStateException("Nested remainder operation unit");
            require(units<MAX_UNITS,"copied-ordering operation stays within its shared 640-unit budget");
            active=operation;
            unitStarted=System.nanoTime();
            units++;
            checkTotal(operation);
        }
        void end(int work,int quantum) {
            end(work,quantum,false);
        }
        void end(int work,int quantum,boolean stateTransition) {
            if(active==null) throw new IllegalStateException("Missing remainder operation unit");
            String operation=active;
            long elapsed=System.nanoTime()-unitStarted;
            require(quantum>=0 && quantum<=MAX_WORK_PER_UNIT && work>=0 && work<=quantum,
                operation+" reports only real bounded owner work");
            require(elapsed<=MAX_UNIT_NANOS,
                operation+" finishes within the shared five-second unit allowance");
            realOwnerWork+=work;
            if(work==0) zeroSearchWorkUnits++;
            if(stateTransition) stateTransitions++;
            if(elapsed>maxUnitNanos) maxUnitNanos=elapsed;
            active=null;
            checkTotal(operation);
        }
        void checkTotal(String operation) {
            require(System.nanoTime()-started<=MAX_TOTAL_NANOS,
                operation+" remains inside the shared 90-second operation allowance");
        }
        long elapsedNanos() { return System.nanoTime()-started; }
    }

    private static PreparedStreamCase prepareStreamCase(StreamFixture selected) {
        Rb56Plan plan=Rb56Plan.resolve(selected.rootSeed,selected.placementVersion);
        require(SeededPcbLayoutGenerator.CURRENT_VERSION==26 &&
            "tsj-generation-dependencies-v29".equals(GenerationDependencyContext.INTERPRETATION_EPOCH),
            "AO saved fixture is explicitly pinned to the current layout-26/dependency-29 interpretation");
        require(plan.seed==selected.rootSeed &&
            plan.placementInterpretationVersion==selected.placementVersion &&
            plan.layoutSeed==selected.placementSeed && plan.routingSeed==selected.routingSeed,
            "AN fixture preserves exact signed root, named placement and routing seeds");
        require(plan.topology().equals(selected.topology) &&
            "RB56_CONTROL".equals(Rb56Plan.FAMILY_ID),
            "AN fixture resolves the exact authored RB56 topology and family");
        TroubleshootBoard board=plan.board();
        PcbPlacementConstraints constraints=board.getPlacementConstraints();
        require(constraints!=null && constraints.requiresTwoLayerRouting &&
            MediumBoardPhysicalPolicy.ID.equals(constraints.getPhysicalPolicyId()) &&
            constraints.getPhysicalPolicyVersion()==MediumBoardPhysicalPolicy.VERSION &&
            constraints.placementDensityVersion==selected.placementVersion &&
            constraints.placementPackingVersion==1 &&
            "UNIFORM_TRACE@1".equals(constraints.getTraceWidthProfile().getIdentity()),
            "AN fixture retains its saved uniform-width two-layer Medium placement demand");
        PcbPlacementPlanner.Plan placementPlan=new PcbPlacementPlanner(
            StandardPcbFootprintProviders.createRegistry()).plan(board,constraints,
                plan.layoutSeed,selected.candidateAttempt);
        require(placementPlan!=null && placementPlan.candidate==selected.candidateAttempt,
            "AN materializes exactly the saved placement candidate attempt");
        String planCanonical=plan.canonical();
        String planSha256=PlayerSessionFingerprint.of(planCanonical);
        String boardCanonical=logicalBoardFingerprint(board);
        String boardSha256=PlayerSessionFingerprint.of(boardCanonical);
        require(board.getComponentIds().size()==54 && board.getPadIds().size()==131,
            "AN seed/profile fixture retains its exact 54-package and 131-pad inventory");
        require(PlayerSessionFingerprint.of(planCanonical).equals(selected.logicalInventorySha256),
            "AN fixture matches the independent saved logical inventory fingerprint");
        PcbBoardLayout placement=placementPlan.materialize();
        validateRawInput(plan,board,placementPlan,placement,selected.candidateAttempt);
        String inputGeometry=placement.geometryFingerprint();
        String inputSha=PlayerSessionFingerprint.of(inputGeometry);
        String inputReferenceSha=PcbCompactnessContractTest.layout24PrefixReferenceSha256(placement);
        require(inputReferenceSha.equals(selected.inputGeometrySha256) &&
            inputGeometry.length()==selected.inputGeometryLength,
            "current layout25 raw materialization matches the retained layout24-prefix projection and length");
        PcbLayerRoutingPrototype.GridAdmission grid=PcbLayerRoutingPrototype.gridAdmission(
            placement.getBoardOutline());
        require(grid.eligible && grid.cells<=PcbLayerRoutingPrototype.MAX_CELLS,
            "AN saved candidate satisfies the unchanged P07 grid admission cap");
        return new PreparedStreamCase(selected,plan,board,constraints,placementPlan,placement,
            planCanonical,planSha256,boardCanonical,boardSha256,inputGeometry,inputSha,
            inputReferenceSha,grid);
    }

    private static StreamRun runFixturePair(String runId,StreamFixture fixture,
            boolean diagnosticsEnabled,boolean finalizeOutputs) {
        PreparedStreamCase prepared=prepareStreamCase(fixture);
        OwnerAdapter owner=finalizeOutputs?new OwnerAdapter(prepared.board,prepared.plan,
            prepared.placementPlan,fixture.candidateAttempt):null;
        CountingObserver observer=new CountingObserver();
        emitStreamFrame(runId,prepared,
            diagnosticsEnabled?"DIAGNOSTICS_ENABLED":"DIAGNOSTICS_DISABLED");
        StreamRun run=drainStream(runId,prepared,
            diagnosticsEnabled?"DIAGNOSTICS_ENABLED":"DIAGNOSTICS_DISABLED",
            diagnosticsEnabled,diagnosticsEnabled,owner,PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            observer,true);
        require(prepared.planCanonical.equals(prepared.plan.canonical()) &&
            prepared.boardCanonical.equals(logicalBoardFingerprint(prepared.board)) &&
            prepared.inputGeometry.equals(prepared.placement.geometryFingerprint()),
            "AN route stream preserves its caller plan, logical board and raw placement");
        return run;
    }

    private static StreamRun drainStream(String runId,PreparedStreamCase prepared,String mode,
            boolean diagnosticsEnabled,boolean emitBranches,OwnerAdapter owner,int maximum,
            CountingObserver observer,boolean exercisePendingProtocol) {
        require(maximum>=1 && maximum<=PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            "stream canary never raises the existing total-expansion limit");
        final BranchAudit audit=diagnosticsEnabled?
            new BranchAudit(runId,prepared.fixture.caseId,mode,emitBranches):null;
        final PcbLayerRoutingPrototype.Diagnostics diagnostics=diagnosticsEnabled?
            new PcbLayerRoutingPrototype.Diagnostics() {
                public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                    audit.observe(snapshot);
                }
            }:null;
        final PcbLayerRoutingPrototype.Policy policy=PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER;
        final PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.beginOrderingCandidates(
            prepared.board,prepared.placement,policy,observer,maximum,policy.vias,diagnostics);
        final Vector<StreamRoute> routes=new Vector<StreamRoute>();
        long started=System.nanoTime(),lastYieldExpansion=0L;
        int advances=0;
        boolean pendingCanaryDone=false;
        expectIllegalState(new Runnable(){public void run(){session.orderingSummary();}},
            "stream summary is unavailable before terminal completion");
        expectIllegalState(new Runnable(){public void run(){session.result();}},
            "ordering stream does not expose the legacy all-at-once result");
        expectIllegalState(new Runnable(){public void run(){session.advanceSlice(1);}},
            "ordering stream rejects the legacy advance API");
        while(true) {
            if(++advances>STREAM_MAX_ADVANCES)
                throw new AssertionError("P07 ordering stream exceeded its bounded advance census");
            PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(
                STREAM_WORK_QUANTUM);
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.WORKING) continue;
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE) break;
            if(progress!=PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY)
                throw new AssertionError("P07 ordering stream returned an unknown progress value");
            int beforePoll=session.expansions();
            int observerBeforePoll=observer.calls;
            PcbLayerRoutingPrototype.OrderingProgress poll=session.advanceOrderingSlice(1);
            require(poll==PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY &&
                session.expansions()==beforePoll,
                "polling a pending route does not replace it or perform successor expansion work");
            if(exercisePendingProtocol)
                require(observer.calls>observerBeforePoll,
                    "pending-route poll retains an observer checkpoint without advancing search");
            expectIllegalState(new Runnable(){public void run(){session.orderingSummary();}},
                "pending route blocks publication of a terminal summary");
            int observerBeforeTake=observer.calls;
            PcbLayerRoutingPrototype.Result result=session.takeReadyOrdering();
            require(exercisePendingProtocol?observer.calls>observerBeforeTake:true,
                "taking a ready route retains its observer checkpoint");
            require(result!=null && result.accepted() && result.layout!=null &&
                result.orderings>=1 && result.orderings<=PcbLayerRoutingPrototype.ORDERINGS,
                "each stream yield is a real completed accepted route result");
            int ordinal=routes.size()+1;
            require(result.orderings>=ordinal,
                "yielded route identifies the actual already-started ordering");
            require(result.expansions>=lastYieldExpansion && result.expansions<=maximum,
                "yielded result reports monotone cumulative shared-request expansions");
            long delta=(long)result.expansions-lastYieldExpansion;
            String rawGeometrySha=PlayerSessionFingerprint.of(result.layout.geometryFingerprint());
            String rawGeometryReferenceSha=
                PcbCompactnessContractTest.layout24PrefixReferenceSha256(result.layout);
            String rawTraceSha=PlayerSessionFingerprint.of(result.layout.traceGeometryFingerprint());
            AcceptedRouteEvidence raw=validateAcceptedOutput(prepared.board,result.layout);
            RawCopperSummary copper=rawCopperSummary(result.layout);
            FinalizedVariant finalized=owner==null?FinalizedVariant.notRun("NO_OWNER_ADAPTER"):
                owner.finalizeAndConsider(prepared.board,prepared.plan.layoutSeed,result);
            require(prepared.inputGeometry.equals(prepared.placement.geometryFingerprint()) &&
                prepared.planCanonical.equals(prepared.plan.canonical()) &&
                prepared.boardCanonical.equals(logicalBoardFingerprint(prepared.board)),
                "a yielded route and owner finalizer preserve the caller-owned input");
            StreamRoute record=new StreamRoute(ordinal,result.orderings-1,delta,result,
                rawGeometrySha,rawGeometryReferenceSha,rawTraceSha,copper,raw,finalized);
            routes.add(record);
            emitStreamOrder(runId,prepared.fixture.caseId,mode,record);
            lastYieldExpansion=result.expansions;
            if(exercisePendingProtocol && !pendingCanaryDone) {
                expectIllegalState(new Runnable(){public void run(){session.takeReadyOrdering();}},
                    "a second take cannot replay a previously yielded result");
                pendingCanaryDone=true;
            }
        }
        PcbLayerRoutingPrototype.OrderingSummary summary=session.orderingSummary();
        require(summary!=null && summary.expansions==session.expansions() &&
            summary.expansions<=maximum && summary.orderings<=PcbLayerRoutingPrototype.ORDERINGS &&
            summary.completedOrderings<=summary.orderings &&
            summary.yieldedOrderings==routes.size() &&
            summary.completedOrderings>=summary.yieldedOrderings &&
            summary.termination!=null && summary.termination.length()>0,
            "terminal stream summary is internally bounded and contains only scalar search facts");
        if(summary.comparisonComplete)
            require(summary.orderings==PcbLayerRoutingPrototype.ORDERINGS &&
                summary.completedOrderings==PcbLayerRoutingPrototype.ORDERINGS,
                "complete comparison requires every existing ordering to start and complete");
        else if(summary.completedOrderings==PcbLayerRoutingPrototype.ORDERINGS)
            require("TOTAL_SEARCH_LIMIT".equals(summary.termination),
                "an incomplete three-order summary is allowed only for the actual deterministic total cap");
        if("TOTAL_SEARCH_LIMIT".equals(summary.termination))
            require(!summary.comparisonComplete,
                "total-search-limit never claims comparison completion");
        if("FIRST_SUCCESS_GUIDED".equals(summary.termination))
            require(summary.guidanceStarted && summary.yieldedOrderings==1 &&
                !summary.comparisonComplete && summary.orderings<PcbLayerRoutingPrototype.ORDERINGS,
                "a guided first-success stop yields one route before later orderings");
        if(summary.guidanceStarted)
            require(summary.orderings>=1,
                "guided stream has actually started an existing ordering");
        else if(summary.yieldedOrderings>0) {
            require(summary.yieldedOrderings==1,
                "without guidance the stream yields at most the first accepted route");
            if(summary.orderings<PcbLayerRoutingPrototype.ORDERINGS)
                require("FIRST_SUCCESS_UNGUIDED".equals(summary.termination) &&
                    !summary.comparisonComplete,
                    "an early unguided success stops before later orderings");
            else
                require(summary.orderings==PcbLayerRoutingPrototype.ORDERINGS &&
                    summary.completedOrderings==PcbLayerRoutingPrototype.ORDERINGS &&
                    summary.comparisonComplete &&
                    "ORDERINGS_COMPLETE".equals(summary.termination),
                    "an unguided first success in ordering three completes the existing comparison set");
        }
        if(audit!=null) {
            require(audit.guidanceStarted==summary.guidanceStarted &&
                audit.events>0 && audit.branchExpandedSum==summary.expansions,
                "diagnostic branch census independently totals the shared request and intervention flag");
            for(int pass=0;pass<summary.orderings;pass++)
                require(audit.perPassEvents[pass]>0,
                    "every started ordering has at least one terminal branch event");
        }
        expectIllegalState(new Runnable(){public void run(){session.result();}},
            "terminal ordering stream still rejects the legacy result API");
        return new StreamRun(prepared,runId,mode,diagnosticsEnabled,routes,audit,owner,summary,
            advances,System.nanoTime()-started);
    }

    private static void verifyStreamParity(StreamRun enabled,StreamRun disabled,String label) {
        require(enabled.prepared.fixture.caseId.equals(disabled.prepared.fixture.caseId) &&
            enabled.prepared.inputGeometrySha256.equals(disabled.prepared.inputGeometrySha256) &&
            enabled.prepared.planSha256.equals(disabled.prepared.planSha256) &&
            enabled.prepared.boardSha256.equals(disabled.prepared.boardSha256),
            label+" starts from identical saved logical and raw placement inputs");
        require(enabled.summary.expansions==disabled.summary.expansions &&
            enabled.summary.orderings==disabled.summary.orderings &&
            enabled.summary.completedOrderings==disabled.summary.completedOrderings &&
            enabled.summary.yieldedOrderings==disabled.summary.yieldedOrderings &&
            enabled.summary.guidanceStarted==disabled.summary.guidanceStarted &&
            enabled.summary.comparisonComplete==disabled.summary.comparisonComplete &&
            enabled.summary.termination.equals(disabled.summary.termination) &&
            enabled.routes.size()==disabled.routes.size(),
            label+" preserves terminal status, work and yield census");
        for(int index=0;index<enabled.routes.size();index++) {
            StreamRoute first=enabled.routes.get(index),second=disabled.routes.get(index);
            require(first.orderingIndex==second.orderingIndex &&
                first.result.orderings==second.result.orderings &&
                first.result.expansions==second.result.expansions &&
                first.result.vias==second.result.vias && first.result.links==second.result.links &&
                first.rawGeometrySha256.equals(second.rawGeometrySha256) &&
                first.rawTraceGeometrySha256.equals(second.rawTraceGeometrySha256),
                label+" preserves each actual yielded raw copper result");
        }
        for(int index=0;index<enabled.routes.size();index++) {
            FinalizedVariant first=enabled.routes.get(index).finalized;
            FinalizedVariant second=disabled.routes.get(index).finalized;
            require(first.status.equals(second.status) &&
                ((first.reason==null && second.reason==null) ||
                    (first.reason!=null && first.reason.equals(second.reason))) &&
                first.accepted==second.accepted && first.ownerWinner==second.ownerWinner &&
                ((first.geometrySha256==null && second.geometrySha256==null) ||
                    (first.geometrySha256!=null && first.geometrySha256.equals(second.geometrySha256))) &&
                first.area==second.area && first.qualityMilli==second.qualityMilli,
                label+" preserves existing finalization and owner scoring across diagnostic modes");
        }
        require(enabled.prepared.inputGeometry.equals(enabled.prepared.placement.geometryFingerprint()) &&
            disabled.prepared.inputGeometry.equals(disabled.prepared.placement.geometryFingerprint()),
            label+" leaves both original placements unchanged");
    }

    private static void assertAmSharedStream(StreamRun run) {
        require(run.summary.expansions==683850 && run.summary.orderings==3 &&
            run.summary.completedOrderings==3 && run.summary.yieldedOrderings==3 &&
            run.summary.guidanceStarted && run.summary.comparisonComplete &&
            "ORDERINGS_COMPLETE".equals(run.summary.termination) && run.routes.size()==3,
            "saved7 stream reuses one shared 683850-expansion budget across all three guided yields");
        require(run.branches!=null && run.branches.events==291 &&
            run.branches.perPassEvents[0]==97 && run.branches.perPassEvents[1]==97 &&
            run.branches.perPassEvents[2]==97 &&
            run.branches.branchExpandedSum==683850,
            "saved7 shared-stream branch census retains the independently observed 291 terminal events");
        for(int index=0;index<3;index++) {
            StreamRoute route=run.routes.get(index);
            long cumulative=0L;
            for(int prior=0;prior<=index;prior++) cumulative+=AM_ORDER_EXPANSION_DELTAS[prior];
            require(route.orderingIndex==index && route.result.orderings==index+1 &&
                route.expansionDelta==AM_ORDER_EXPANSION_DELTAS[index] &&
                route.result.expansions==cumulative &&
                route.rawGeometryLayout24PrefixReferenceSha256.equals(AM_ORDER_RAW_GEOMETRY[index]) &&
                route.rawTraceGeometrySha256.equals(AM_ORDER_RAW_TRACE[index]) &&
                route.result.vias==AM_ORDER_VIAS[index] &&
                route.rawEvidence.sourceLandCount==AM_ORDER_LAND_COUNTS[index],
                "shared saved7 stream yields the same actual raw route for existing ordering "+index);
            require(route.finalized.accepted && route.finalized.ownerWinner &&
                route.finalized.layout24PrefixReferenceSha256.equals(AM_ORDER_FINAL_GEOMETRY[index]) &&
                route.finalized.area==AM_ORDER_FINAL_AREA[index] &&
                route.finalized.qualityMilli==AM_ORDER_FINAL_QUALITY_MILLI[index],
                "existing saved7 finalizer and comparator accept the same order "+index);
        }
        try {
            PcbBoardLayout winner=(PcbBoardLayout)runOwnerWinner(run);
            Rectangle bounds=winner.getBoardOutline();
            require((long)bounds.width*bounds.height==6371585L &&
                PcbCompactnessContractTest.layout24PrefixReferenceSha256(winner).equals(
                    "4486304d0971a5e8dbf574bb163f3a02c8c33a6151e851a8329ad93bdbc2f83a"),
                "actual existing Medium owner retains the independently observed order-2 compact winner");
        } catch(ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not inspect actual saved7 owner winner",failure);
        }
    }

    private static Object runOwnerWinner(StreamRun run) throws ReflectiveOperationException {
        require(run.owner!=null && run.routes.lastElement().finalized.ownerWinner,
            "saved7 final yielded order is the actual owner comparator winner");
        return run.owner.winnerLayout.get(run.owner.session);
    }

    private static void verifyWrongModeApiRejection(String runId) {
        final PreparedStreamCase prepared=prepareStreamCase(AM_STREAM_FIXTURE);
        final PcbLayerRoutingPrototype.Policy policy=PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER;
        final PcbLayerRoutingPrototype.Session legacy=PcbLayerRoutingPrototype.begin(
            prepared.board,prepared.placement,policy,NOOP_OBSERVER);
        expectIllegalState(new Runnable(){public void run(){legacy.advanceOrderingSlice(1);}},
            "legacy P07 session rejects ordering-stream advancement");
        expectIllegalState(new Runnable(){public void run(){legacy.takeReadyOrdering();}},
            "legacy P07 session rejects stream result handoff");
        expectIllegalState(new Runnable(){public void run(){legacy.result();}},
            "legacy P07 result rejects access before route completion");
        expectIllegalState(new Runnable(){public void run(){legacy.orderingSummary();}},
            "legacy P07 session rejects stream summary access");
        final PcbLayerRoutingPrototype.Session stream=PcbLayerRoutingPrototype.beginOrderingCandidates(
            prepared.board,prepared.placement,policy,NOOP_OBSERVER,1,policy.vias,null);
        expectIllegalState(new Runnable(){public void run(){stream.advanceSlice(1);}},
            "ordering-stream session rejects legacy advancement");
        expectIllegalState(new Runnable(){public void run(){stream.result();}},
            "ordering-stream session rejects legacy result access");
        expectIllegalState(new Runnable(){public void run(){stream.takeReadyOrdering();}},
            "ordering stream rejects a take before a route is ready");
        expectIllegalState(new Runnable(){public void run(){stream.orderingSummary();}},
            "ordering-stream session rejects a premature summary");
        expectIllegalArgument(new Runnable(){public void run(){
            PcbLayerRoutingPrototype.beginOrderingCandidates(prepared.board,prepared.placement,
                PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,NOOP_OBSERVER);
        }},"ordering stream rejects a policy other than FULLER_TWO_LAYER");
        require(prepared.inputGeometry.equals(prepared.placement.geometryFingerprint()) &&
            prepared.planCanonical.equals(prepared.plan.canonical()) &&
            prepared.boardCanonical.equals(logicalBoardFingerprint(prepared.board)),
            "wrong-mode construction leaves the saved plan, board and placement unchanged");
        emitStreamCanary(runId,"WRONG_MODE_AND_PREMATURE_API",true,
            "{\"legacyStreamApiRejected\":true,\"streamLegacyApiRejected\":true,"+
            "\"wrongPolicyRejected\":true,\"inputsStable\":true}");
    }

    private static void verifySeed5Pair(String runId) {
        for(StreamFixture fixture:SEED5_STREAM_FIXTURES) {
            StreamRun enabled=runFixturePair(runId,fixture,true,true);
            StreamRun disabled=runFixturePair(runId,fixture,false,true);
            verifyStreamParity(enabled,disabled,fixture.caseId+" diagnostics parity");
            require(!enabled.routes.isEmpty() && !disabled.routes.isEmpty(),
                "both retained seed5 profiles have a real accepted route in each diagnostic mode");
            if(fixture.placementVersion==11) {
                StreamRoute first=enabled.routes.firstElement();
                String p08Canonical=first.rawEvidence.graph.toCanonical();
                require(first.result.accepted() && first.result.orderings==1 &&
                    first.result.expansions==371901 &&
                    first.rawGeometryLayout24PrefixReferenceSha256.equals(
                        "da26385cf2e7984293e6fe67c14cf4fedd299de996377d0a916f66d1055c0352") &&
                    first.rawTraceGeometrySha256.equals(
                        "80fc936f50ba09970d3b487f69b2899525e652d7bb15a177b32409f09e70e8ae") &&
                    PlayerSessionFingerprint.of(p08Canonical).equals(
                        "473e2b2430a76a5ae335a8166627ec7ea26daf6c95af97f16dec5ad52ec05a0a"),
                    "saved5/version11 first yield matches the retained legacy field route identity and work");
            }
            if(fixture.placementVersion==10) {
                require(!enabled.routes.isEmpty(),
                    "saved5/version10 has an actual accepted stream yield");
                StreamRoute first=enabled.routes.firstElement();
                require(first.result.orderings==2 && first.result.expansions==523756 &&
                    "e828d0e87587cc1189218dcf6f8677f929e194507b65d40f821787d7fe397255"
                        .equals(first.rawGeometryLayout24PrefixReferenceSha256) &&
                    "7001ea96577815e96e88094d8fa3e8210fab03cabd8aced57d39f0039b074c60"
                        .equals(first.rawTraceGeometrySha256),
                    "saved5/version10 stream preserves the retained first accepted route identity");
            }
            emitStreamCase(enabled,"DIAGNOSTICS_ENABLED");
            emitStreamCase(disabled,"DIAGNOSTICS_DISABLED");
        }
    }

    private static void verifyUnguidedLegacyFirstSuccess(String runId) {
        P06FactoryLinkFixtures.Fixture legacyFixture=
            new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        P06FactoryLinkFixtures.Fixture streamFixture=
            new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        final String legacyInput=legacyFixture.layout.geometryFingerprint();
        final String streamInput=streamFixture.layout.geometryFingerprint();
        final String legacyBoard=logicalBoardFingerprint(legacyFixture.board);
        final String streamBoard=logicalBoardFingerprint(streamFixture.board);
        final PcbLayerRoutingPrototype.Policy policy=PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER;
        PcbLayerRoutingPrototype.Result legacy=PcbLayerRoutingPrototype.route(
            legacyFixture.board,legacyFixture.layout,policy,P06FactoryLinkFixtures.OBSERVER);
        require(legacy.accepted() && legacy.layout!=null,
            "frozen small P07 fixture has a real legacy FULLER_TWO_LAYER success");
        AcceptedRouteEvidence legacyEvidence=validateAcceptedOutput(legacyFixture.board,legacy.layout);
        final PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.beginOrderingCandidates(
            streamFixture.board,streamFixture.layout,policy,P06FactoryLinkFixtures.OBSERVER);
        PcbLayerRoutingPrototype.Result first=null;
        int advances=0;
        while(first==null) {
            if(++advances>STREAM_MAX_ADVANCES)
                throw new AssertionError("small unguided stream exceeded its bounded advance census");
            PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(128);
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY)
                first=session.takeReadyOrdering();
            else if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE)
                break;
        }
        require(first!=null && first.accepted() && first.layout!=null,
            "unguided ordering stream yields the ordinary validated route");
        PcbLayerRoutingPrototype.OrderingSummary summary=session.orderingSummary();
        require(summary.expansions==first.expansions && summary.orderings==1 &&
            summary.completedOrderings==1 && summary.yieldedOrderings==1 &&
            !summary.guidanceStarted && !summary.comparisonComplete &&
            "FIRST_SUCCESS_UNGUIDED".equals(summary.termination),
            "a small first success stops without comparing later orderings when guidance never started");
        require(legacy.expansions==first.expansions && legacy.orderings==first.orderings &&
            legacy.vias==first.vias && legacy.links==first.links &&
            PlayerSessionFingerprint.of(legacy.layout.geometryFingerprint()).equals(
                PlayerSessionFingerprint.of(first.layout.geometryFingerprint())) &&
            PlayerSessionFingerprint.of(legacy.layout.traceGeometryFingerprint()).equals(
                PlayerSessionFingerprint.of(first.layout.traceGeometryFingerprint())),
            "stream unguided first success preserves legacy route work and exact copper");
        AcceptedRouteEvidence streamEvidence=validateAcceptedOutput(streamFixture.board,first.layout);
        require(legacyEvidence.graph.toCanonical().equals(streamEvidence.graph.toCanonical()) &&
            legacyEvidence.orderedPadPairs==((long)legacyFixture.board.getPadIds().size()*legacyFixture.board.getPadIds().size()) &&
            streamEvidence.orderedPadPairs==((long)streamFixture.board.getPadIds().size()*streamFixture.board.getPadIds().size()) &&
            legacyInput.equals(legacyFixture.layout.geometryFingerprint()) &&
            streamInput.equals(streamFixture.layout.geometryFingerprint()) &&
            legacyBoard.equals(logicalBoardFingerprint(legacyFixture.board)) &&
            streamBoard.equals(logicalBoardFingerprint(streamFixture.board)),
            "legacy/stream physical oracles agree and both caller inputs remain unchanged");
        emitStreamCanary(runId,"UNGUIDED_FIRST_SUCCESS",true,
            "{\"legacyExpansions\":"+legacy.expansions+",\"streamExpansions\":"+
            first.expansions+",\"orderings\":"+summary.orderings+
            ",\"guidanceStarted\":false,\"comparisonComplete\":false,"+
            "\"termination\":\"FIRST_SUCCESS_UNGUIDED\",\"orderedPadPairs\":"+((long)streamFixture.board.getPadIds().size()*streamFixture.board.getPadIds().size())+"}");
    }

    private static void verifyReducedTotalCaps(String runId) {
        PreparedStreamCase noRoutePrepared=prepareStreamCase(AM_STREAM_FIXTURE);
        PcbLayerRoutingPrototype.Session noRoute=PcbLayerRoutingPrototype.beginOrderingCandidates(
            noRoutePrepared.board,noRoutePrepared.placement,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,NOOP_OBSERVER,1,48,null);
        int noRouteAdvances=0;
        while(true) {
            if(++noRouteAdvances>STREAM_MAX_ADVANCES)
                throw new AssertionError("one-expansion total cap did not terminate");
            PcbLayerRoutingPrototype.OrderingProgress progress=noRoute.advanceOrderingSlice(1);
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE) break;
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY)
                throw new AssertionError("one-expansion cap unexpectedly produced a route");
        }
        PcbLayerRoutingPrototype.OrderingSummary noRouteSummary=noRoute.orderingSummary();
        require(noRouteSummary.expansions==1 && noRouteSummary.orderings<=1 &&
            noRouteSummary.completedOrderings==noRouteSummary.orderings &&
            noRouteSummary.yieldedOrderings==0 && !noRouteSummary.comparisonComplete &&
            "TOTAL_SEARCH_LIMIT".equals(noRouteSummary.termination),
            "reduced cap without a valid route reports an incomplete typed total-search limit");
        require(noRoutePrepared.inputGeometry.equals(noRoutePrepared.placement.geometryFingerprint()) &&
            noRoutePrepared.placement.getTraces().isEmpty() && noRoutePrepared.placement.getHoles().isEmpty(),
            "cap rejection publishes no partial route into its caller placement");

        PreparedStreamCase retainedPrepared=prepareStreamCase(AM_STREAM_FIXTURE);
        OwnerAdapter owner=new OwnerAdapter(retainedPrepared.board,retainedPrepared.plan,
            retainedPrepared.placementPlan,retainedPrepared.fixture.candidateAttempt);
        emitStreamFrame(runId,retainedPrepared,"TOTAL_CAP_AFTER_VALID_YIELD");
        StreamRun retained=drainStream(runId,retainedPrepared,"TOTAL_CAP_AFTER_VALID_YIELD",
            true,true,owner,300783,new CountingObserver(),true);
        require(retained.routes.size()==1 && retained.routes.firstElement().result.accepted() &&
            retained.routes.firstElement().result.expansions==300782 &&
            retained.routes.firstElement().finalized.accepted &&
            retained.summary.expansions==300783 &&
            "TOTAL_SEARCH_LIMIT".equals(retained.summary.termination) &&
            !retained.summary.comparisonComplete && retained.summary.yieldedOrderings==1 &&
            retained.summary.orderings<=2 &&
            retained.owner!=null && retained.routes.firstElement().finalized.ownerWinner,
            "reduced shared cap retains one real finalized yield while reporting incomplete comparison");
        emitStreamFrame(runId,noRoutePrepared,"TOTAL_CAP_WITHOUT_ROUTE");
        emitStreamCanary(runId,"TOTAL_CAP_WITHOUT_ROUTE",true,
            "{\"expansions\":"+noRouteSummary.expansions+",\"yielded\":0,"+
            "\"termination\":\"TOTAL_SEARCH_LIMIT\",\"comparisonComplete\":false}");
        emitStreamCase(retained,"TOTAL_CAP_AFTER_VALID_YIELD");
        emitStreamCanary(runId,"TOTAL_CAP_AFTER_VALID_YIELD",true,
            "{\"expansions\":"+retained.summary.expansions+",\"yielded\":"+
            retained.summary.yieldedOrderings+",\"finalized\":"+
            retained.routes.firstElement().finalized.accepted+
            ",\"termination\":\"TOTAL_SEARCH_LIMIT\",\"comparisonComplete\":false}");
    }

    /** The Medium generator's opt-in: a guided stream stops at its first accepted route. */
    private static void verifyFirstSuccessStop(String runId) {
        PreparedStreamCase prepared=prepareStreamCase(AM_STREAM_FIXTURE);
        CountingObserver observer=new CountingObserver();
        final PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.beginOrderingCandidates(
            prepared.board,prepared.placement,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,observer)
            .stopAtFirstSuccess();
        PcbLayerRoutingPrototype.Result yielded=advanceUntilFirstYield(session,observer);
        require(yielded!=null && yielded.accepted() && yielded.orderings==1 &&
            yielded.expansions==AM_ORDER_EXPANSION_DELTAS[0] && yielded.vias==AM_ORDER_VIAS[0] &&
            PcbCompactnessContractTest.layout24PrefixReferenceSha256(yielded.layout)
                .equals(AM_ORDER_RAW_GEOMETRY[0]) &&
            PlayerSessionFingerprint.of(yielded.layout.traceGeometryFingerprint())
                .equals(AM_ORDER_RAW_TRACE[0]),
            "first-success stream yields the independently observed saved7 order-0 route");
        require(session.advanceOrderingSlice(1)==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE &&
            session.expansions()==AM_ORDER_EXPANSION_DELTAS[0],
            "first-success stream completes without successor ordering work");
        PcbLayerRoutingPrototype.OrderingSummary summary=session.orderingSummary();
        require(summary.guidanceStarted && !summary.comparisonComplete &&
            summary.orderings==1 && summary.completedOrderings==1 && summary.yieldedOrderings==1 &&
            summary.expansions==AM_ORDER_EXPANSION_DELTAS[0] &&
            "FIRST_SUCCESS_GUIDED".equals(summary.termination),
            "first-success summary records a guided early stop after one ordering");
        expectIllegalState(new Runnable(){public void run(){session.stopAtFirstSuccess();}},
            "a completed stream cannot change its stop rule");
        PreparedStreamCase latePrepared=prepareStreamCase(AM_STREAM_FIXTURE);
        final PcbLayerRoutingPrototype.Session late=PcbLayerRoutingPrototype.beginOrderingCandidates(
            latePrepared.board,latePrepared.placement,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            new CountingObserver());
        int lateAdvances=0;
        while(late.expansions()==0) {
            if(++lateAdvances>STREAM_MAX_ADVANCES)
                throw new AssertionError("late stop-rule fixture made no search progress");
            late.advanceOrderingSlice(128);
        }
        expectIllegalState(new Runnable(){public void run(){late.stopAtFirstSuccess();}},
            "a stream that already searched cannot switch to the first-success stop");
        final PcbLayerRoutingPrototype.Session plain=PcbLayerRoutingPrototype.begin(
            latePrepared.board,latePrepared.placement,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            new CountingObserver(),PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias,null);
        expectIllegalState(new Runnable(){public void run(){plain.stopAtFirstSuccess();}},
            "a non-stream request has no ordering stop rule");
        require(prepared.inputGeometry.equals(prepared.placement.geometryFingerprint()) &&
            prepared.planCanonical.equals(prepared.plan.canonical()) &&
            prepared.boardCanonical.equals(logicalBoardFingerprint(prepared.board)),
            "first-success stream leaves logical and raw caller inputs unchanged");
        emitStreamCanary(runId,"GUIDED_FIRST_SUCCESS_STOP",true,
            "{\"expansions\":"+summary.expansions+",\"yielded\":1,"+
            "\"termination\":\"FIRST_SUCCESS_GUIDED\",\"comparisonComplete\":false}");
    }

    private static void verifyPostYieldObserverFailure(String runId) {
        PreparedStreamCase prepared=prepareStreamCase(AM_STREAM_FIXTURE);
        final CountingObserver observer=new CountingObserver();
        final PcbLayerRoutingPrototype.Session session=
            PcbLayerRoutingPrototype.beginOrderingCandidates(prepared.board,prepared.placement,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,observer);
        PcbLayerRoutingPrototype.Result yielded=advanceUntilFirstYield(session,observer);
        require(yielded!=null && yielded.accepted() && yielded.orderings==1 &&
            validateAcceptedOutput(prepared.board,yielded.layout).orderedPadPairs==17161,
            "observer-latch fixture begins with a fully validated first stream yield");
        boolean madeContinuationWork=false;
        int preContinuationExpansion=session.expansions();
        int continuationOperations=0;
        while(!madeContinuationWork) {
            if(++continuationOperations>STREAM_MAX_ADVANCES)
                throw new AssertionError("observer-latch fixture exceeded its bounded continuation-operation census");
            int before=session.expansions();
            PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(1);
            require(progress==PcbLayerRoutingPrototype.OrderingProgress.WORKING &&
                session.expansions()>=before,
                "observer-latch fixture advances the successor ordering before cancellation");
            madeContinuationWork=session.expansions()>preContinuationExpansion;
        }
        require(madeContinuationWork,"observer failure is injected after successor search made progress");
        RuntimeException sentinel=new IllegalStateException("AN observer sentinel after yield");
        observer.armNext(sentinel);
        int expansionsBeforeFailure=session.expansions();
        RuntimeException observed=runObserverFailure(session);
        require(observed==sentinel,
            "stream observer cancellation propagates its exact exception after a valid yield");
        int expansionsAtFailure=session.expansions();
        require(expansionsAtFailure>=expansionsBeforeFailure,
            "latched observer failure does not roll back real work or mutate input");
        require(runFailureApi(session,0)==sentinel &&
            runFailureApi(session,1)==sentinel &&
            runFailureApi(session,2)==sentinel &&
            runFailureApi(session,3)==sentinel &&
            runFailureApi(session,4)==sentinel &&
            session.expansions()==expansionsAtFailure,
            "all subsequent stream and legacy APIs rethrow the original observer failure without advancing");
        require(prepared.inputGeometry.equals(prepared.placement.geometryFingerprint()) &&
            prepared.planCanonical.equals(prepared.plan.canonical()) &&
            prepared.boardCanonical.equals(logicalBoardFingerprint(prepared.board)),
            "post-yield observer failure leaves logical and raw caller inputs unchanged");
        emitStreamCanary(runId,"POST_YIELD_OBSERVER_FAILURE",true,
            "{\"failureIdentityPreserved\":true,\"yieldedOrderings\":1,"+
            "\"continuationWorkBeforeAbort\":true,\"expansionsAtFailure\":"+
            expansionsAtFailure+",\"inputsStable\":true}");
    }

    private static void verifyPostYieldDiagnosticFailure(String runId) {
        PreparedStreamCase prepared=prepareStreamCase(AM_STREAM_FIXTURE);
        final RuntimeException sentinel=new IllegalStateException("AN diagnostic sentinel after yield");
        final int[] callbacks={0};
        final int[] firstPassCallbacks={0};
        final int[] failingPass={-1};
        PcbLayerRoutingPrototype.Diagnostics diagnostics=new PcbLayerRoutingPrototype.Diagnostics() {
            public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                callbacks[0]++;
                if(snapshot.pass==0) firstPassCallbacks[0]++;
                if(snapshot.pass>=1) {
                    failingPass[0]=snapshot.pass;
                    throw sentinel;
                }
            }
        };
        final PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.beginOrderingCandidates(
            prepared.board,prepared.placement,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            NOOP_OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS,48,diagnostics);
        PcbLayerRoutingPrototype.Result yielded=advanceUntilFirstYield(session,NOOP_OBSERVER);
        require(yielded!=null && yielded.accepted() && yielded.orderings==1 &&
            firstPassCallbacks[0]>0,
            "diagnostic-latch fixture records a real pass-zero yield before its sink is armed by pass");
        require(validateAcceptedOutput(prepared.board,yielded.layout).orderedPadPairs==17161,
            "diagnostic-latch first yield passes the retained independent physical oracles");
        RuntimeException observed=runDiagnosticFailure(session);
        require(observed==sentinel && callbacks[0]>firstPassCallbacks[0] &&
            failingPass[0]==1,
            "diagnostic sink failure occurs in a successor branch after the first route was yielded");
        int expansionAtFailure=session.expansions();
        require(runFailureApi(session,0)==sentinel &&
            runFailureApi(session,1)==sentinel &&
            runFailureApi(session,2)==sentinel &&
            runFailureApi(session,3)==sentinel &&
            runFailureApi(session,4)==sentinel &&
            session.expansions()==expansionAtFailure,
            "diagnostic sink failure is latched and cannot revive a pending successor request");
        require(prepared.inputGeometry.equals(prepared.placement.geometryFingerprint()) &&
            prepared.planCanonical.equals(prepared.plan.canonical()) &&
            prepared.boardCanonical.equals(logicalBoardFingerprint(prepared.board)),
            "post-yield diagnostic failure leaves logical and raw caller inputs unchanged");
        emitStreamCanary(runId,"POST_YIELD_DIAGNOSTIC_FAILURE",true,
            "{\"failureIdentityPreserved\":true,\"yieldedOrderings\":1,"+
            "\"branchCallbacks\":"+callbacks[0]+",\"failingPass\":"+failingPass[0]+
            ",\"expansionsAtFailure\":"+expansionAtFailure+",\"inputsStable\":true}");
    }

    private static void verifyDetachedOrderingFinalizer(String runId) {
        final RemainderOperationBudget budget=new RemainderOperationBudget();
        PcbLayerRoutingPrototype.DetachedBranchCopyTask copy=null;
        PcbLayerRoutingPrototype.DetachedBranchReplay replay=null;
        PcbLayerRoutingPrototype.DetachedOrderingContinuation continuation=null;
        PreparedStreamCase prepared=null;
        PcbLayerRoutingPrototype.Session source=null;
        PcbLayerRoutingPrototype.Session copiedOwner=null;
        Object sourceSearch=null,sourceBranch=null,copiedSearch=null,copiedBranch=null;
        PcbBoardLayout copiedLayout=null;
        try {
            budget.begin("saved7 source preparation");
            prepared=prepareStreamCase(AM_STREAM_FIXTURE);
            budget.end(0,0);
            require(prepared.fixture.rootSeed==42L && prepared.fixture.placementVersion==7 &&
                prepared.fixture.candidateAttempt==4 && prepared.board.getComponentIds().size()==54 &&
                prepared.board.getPadIds().size()==131,
                "detached finalizer fixture is the real saved7/root42/candidate4 RB56 board");

            budget.begin("existing Medium owner and comparator setup");
            final OwnerAdapter owner=new OwnerAdapter(prepared.board,prepared.plan,
                prepared.placementPlan,prepared.fixture.candidateAttempt);
            budget.end(0,0,true);

            final CountingObserver sourceObserver=new CountingObserver();
            final CountingObserver detachedObserver=new CountingObserver();
            budget.begin("FULLER ordering source setup");
            source=PcbLayerRoutingPrototype.beginOrderingCandidates(prepared.board,prepared.placement,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,sourceObserver,
                PcbLayerRoutingPrototype.MAX_EXPANSIONS,48,null);
            budget.end(0,0,true);

            int sourceWork=0,sourceCalls=0;
            budget.begin("capture the real initial pass-zero branch frontier");
            while(sourceCalls<STREAM_MAX_ADVANCES) {
                Object search=observedField(source,"search");
                Object branch=search==null?null:observedField(search,"branch");
                if(branch!=null) {
                    sourceSearch=search;
                    sourceBranch=branch;
                    break;
                }
                if(sourceWork>=RemainderOperationBudget.MAX_WORK_PER_UNIT)
                    throw new AssertionError("initial source capture exhausted its 32768-step controller unit");
                PcbLayerRoutingPrototype.OrderingProgress sourceProgress=source.advanceOrderingSlice(1);
                int used=source.lastWork();
                if(used<0 || used>1 || sourceWork>RemainderOperationBudget.MAX_WORK_PER_UNIT-used)
                    throw new AssertionError("initial source capture exceeded its 32768-step controller unit");
                sourceWork+=used;
                sourceCalls++;
                require(sourceProgress==PcbLayerRoutingPrototype.OrderingProgress.WORKING && used<=1,
                    "source frontier capture advances only one real owner step at a time");
            }
            require(sourceBranch!=null && sourceWork<=RemainderOperationBudget.MAX_WORK_PER_UNIT,
                "saved7 first branch frontier is captured inside one bounded controller unit");
            require(observedInt(sourceSearch,"pass")==0 && observedInt(source,"pass")==0 &&
                observedInt(sourceBranch,"expanded")==0 &&
                observedInt(observedField(sourceBranch,"queue"),"size")==2 &&
                observedInt(observedField(sourceBranch,"best"),"size")==2 &&
                observedField(sourceBranch,"found")==null &&
                observedField(sourceBranch,"foundTerminal")==null &&
                !observedBoolean(sourceBranch,"relaxedBoundStarted") &&
                observedField(sourceSearch,"publisher")==null && source.expansions()==0 &&
                observedInt(source,"maximumVias")==48 && observedInt(source,"viaCost")==60,
                "captured source is the genuine pristine two-seed branch under source cost60/cap48");
            budget.end(sourceWork,RemainderOperationBudget.MAX_WORK_PER_UNIT,true);

            final int inheritedExpansions=source.expansions();
            final int sourceViaCost=observedInt(source,"viaCost");
            final String inheritedNet=(String)observedField(sourceBranch,"net");
            final String inheritedStart=((PcbPadPlacement)observedField(sourceBranch,"start")).getPadId();
            final String inheritedRoot=((PcbPadPlacement)observedField(sourceBranch,"root")).getPadId();
            final int inheritedBranchOrdinal=observedInt(sourceBranch,"branchOrdinal");
            require(sourceObserver!=detachedObserver && sourceObserver.calls>0,
                "detached copy uses an independent observer after real source preparation");

            budget.begin("charged cost240/cap48 owner copy setup");
            copy=PcbLayerRoutingPrototype.beginDetachedBranchCopy(source,detachedObserver,48,240);
            budget.end(0,0,true);
            int copyCalls=0;
            long copyWork=0L;
            while(!copy.isComplete()) {
                if(++copyCalls>RemainderOperationBudget.MAX_UNITS)
                    throw new AssertionError("charged P07 copy exceeded the operation's shared 640-unit guard");
                budget.begin("charged detached owner copy");
                boolean done=copy.advance(RemainderOperationBudget.MAX_WORK_PER_UNIT);
                int used=copy.lastWork();
                budget.end(used,RemainderOperationBudget.MAX_WORK_PER_UNIT,done);
                copyWork+=used;
                require(done==copy.isComplete() && (used>0 || done),
                    "charged detached copy reports bounded progress or its actual completion transition");
            }
            budget.begin("consume the completed charged copy");
            replay=copy.result();
            budget.end(0,0,true);
            require(replay!=null && !replay.isComplete(),
                "charged copy yields the live initial branch replay cursor before its branch work");

            budget.begin("audit copied owner identity and inherited cost state");
            copiedOwner=(PcbLayerRoutingPrototype.Session)observedField(replay,"owner");
            copiedSearch=replay.getSearchForDiagnostics();
            copiedBranch=replay.getBranchForDiagnostics();
            copiedLayout=replay.getLayout();
            Object copiedBudget=observedField(copiedOwner,"budget");
            Object sourceQueue=observedField(sourceBranch,"queue");
            Object copiedQueue=observedField(copiedBranch,"queue");
            require(copiedOwner!=source && copiedSearch!=sourceSearch && copiedBranch!=sourceBranch &&
                copiedLayout==observedField(copiedSearch,"layout") &&
                copiedLayout==observedField(copiedOwner,"candidate") &&
                observedField(copiedSearch,"owner")==copiedOwner &&
                observedField(copiedSearch,"branch")==copiedBranch &&
                observedField(copiedSearch,"budget")==copiedBudget &&
                observedInt(copiedSearch,"pass")==0 && observedInt(copiedOwner,"pass")==0 &&
                observedInt(copiedBudget,"maximum")==PcbLayerRoutingPrototype.MAX_EXPANSIONS &&
                observedInt(copiedBudget,"expanded")==inheritedExpansions &&
                observedInt(copiedOwner,"maximumVias")==48 &&
                observedInt(copiedSearch,"maximumVias")==48 &&
                observedInt(copiedOwner,"viaCost")==240 && observedInt(copiedSearch,"viaCost")==240,
                "copy retains the same ordering Search/layout/pass and inherited request budget with cost240/cap48");
            require(observedInt(copiedBranch,"expanded")==0 &&
                observedInt(copiedQueue,"size")==2 && observedInt(sourceQueue,"size")==2 &&
                inheritedNet.equals(observedField(copiedBranch,"net")) &&
                inheritedStart.equals(((PcbPadPlacement)observedField(copiedBranch,"start")).getPadId()) &&
                inheritedRoot.equals(((PcbPadPlacement)observedField(copiedBranch,"root")).getPadId()) &&
                inheritedBranchOrdinal==observedInt(copiedBranch,"branchOrdinal"),
                "copied branch retains the source's exact pass-zero net, terminals and branch lineage");
            require(source.expansions()==inheritedExpansions && observedField(source,"search")==sourceSearch &&
                observedField(sourceSearch,"branch")==sourceBranch &&
                observedInt(sourceBranch,"expanded")==0 &&
                prepared.inputGeometry.equals(prepared.placement.geometryFingerprint()) &&
                prepared.planCanonical.equals(prepared.plan.canonical()) &&
                prepared.boardCanonical.equals(logicalBoardFingerprint(prepared.board)),
                "copy completion leaves the original search frontier and saved7 source inputs unchanged");
            budget.end(0,0);

            int branchCalls=0;
            long branchWork=0L;
            while(!replay.isComplete()) {
                if(++branchCalls>RemainderOperationBudget.MAX_UNITS)
                    throw new AssertionError("P07 detached branch replay exceeded its shared 640-unit guard");
                budget.begin("copied current-branch replay and Publisher");
                boolean done=replay.advance(RemainderOperationBudget.MAX_WORK_PER_UNIT);
                int used=replay.lastWork();
                budget.end(used,RemainderOperationBudget.MAX_WORK_PER_UNIT,done);
                branchWork+=used;
                require(done==replay.isComplete() && (used>0 || done),
                    "copied branch replay reports bounded real work or its terminal Publisher transition");
            }

            budget.begin("audit completed copied Publisher before handoff");
            Object publisher=replay.getPublisherForDiagnostics();
            require(replay.isComplete() && publisher!=null && observedInt(publisher,"stage")==6 &&
                observedField(copiedSearch,"branch")==null && observedField(copiedSearch,"publisher")==null &&
                observedField(copiedBranch,"found")!=null &&
                observedField(copiedBranch,"foundTerminal")!=null &&
                observedBoolean(copiedBranch,"terminalEmitted") &&
                observedInt(copiedOwner,"viaCost")==240 && observedInt(copiedOwner,"maximumVias")==48 &&
                observedInt(copiedBudget,"expanded")>inheritedExpansions &&
                copiedLayout.getTraces().size()>0 && copiedLayout.getHoleCount()<=48,
                "cost240 replay completes the real copied branch Publisher under inherited search ownership");
            require(source.expansions()==inheritedExpansions && observedField(source,"search")==sourceSearch &&
                observedInt(sourceBranch,"expanded")==0 &&
                prepared.inputGeometry.equals(prepared.placement.geometryFingerprint()),
                "branch replay preserves the source's independent live frontier and raw placement");
            budget.end(0,0);

            budget.begin("single-use copied ordering handoff");
            continuation=replay.takeOrderingContinuation();
            budget.end(0,0,true);
            final PcbLayerRoutingPrototype.DetachedBranchReplay transferredReplay=replay;
            expectIllegalState(new Runnable(){public void run(){transferredReplay.getSearchForDiagnostics();}},
                "successful ordering handoff invalidates the branch sender's Search access");
            expectIllegalState(new Runnable(){public void run(){transferredReplay.getLayout();}},
                "successful ordering handoff invalidates the branch sender's layout access");
            require(continuation!=null && observedField(continuation,"owner")==copiedOwner &&
                observedField(continuation,"search")==copiedSearch &&
                observedField(copiedOwner,"detachedOrderingContinuation")==continuation &&
                observedField(copiedOwner,"search")==copiedSearch &&
                observedField(copiedOwner,"candidate")==copiedLayout &&
                observedBoolean(copiedOwner,"orderingStream") &&
                observedInt(copiedOwner,"viaCost")==240 && observedInt(copiedOwner,"maximumVias")==48,
                "exclusive receiver owns the exact completed copy Search and its cost/cap");

            int remainderCalls=0;
            long remainderWork=0L;
            PcbLayerRoutingPrototype.DetachedOrderingProgress progress;
            while(true) {
                if(++remainderCalls>RemainderOperationBudget.MAX_UNITS)
                    throw new AssertionError("current-order continuation exceeded its shared 640-unit guard");
                budget.begin("copied current-order remainder");
                progress=continuation.advance(RemainderOperationBudget.MAX_WORK_PER_UNIT);
                int used=continuation.lastWork();
                budget.end(used,RemainderOperationBudget.MAX_WORK_PER_UNIT,
                    progress!=PcbLayerRoutingPrototype.DetachedOrderingProgress.WORKING);
                remainderWork+=used;
                if(progress==PcbLayerRoutingPrototype.DetachedOrderingProgress.WORKING) continue;
                if(progress==PcbLayerRoutingPrototype.DetachedOrderingProgress.ORDERING_BOUNDARY)
                    throw new AssertionError("copied current ordering ended before an owner-finalizable route: "+
                        continuation.boundaryReason());
                if(progress!=PcbLayerRoutingPrototype.DetachedOrderingProgress.ROUTE_READY)
                    throw new AssertionError("copied current ordering returned unknown progress");
                break;
            }
            require(remainderWork>0,
                "continuation advances real same-order Search work after the branch handoff");

            budget.begin("audit continuation-owned current-order Result before take");
            final PcbLayerRoutingPrototype.Result ready=
                (PcbLayerRoutingPrototype.Result)observedField(copiedOwner,"readyOrdering");
            Object continuationSearch=observedField(continuation,"search");
            Object continuationBudget=observedField(copiedOwner,"budget");
            require(ready!=null && continuationSearch==copiedSearch &&
                observedField(copiedOwner,"search")==null && observedField(copiedOwner,"candidate")==null &&
                observedBoolean(copiedSearch,"complete") && observedField(copiedSearch,"branch")==null &&
                observedField(copiedSearch,"publisher")==null && observedField(copiedSearch,"budget")==continuationBudget &&
                observedInt(copiedSearch,"viaCost")==240 && observedInt(copiedSearch,"maximumVias")==48,
                "continuation has finished only its current copied Search and retains its own ready Result");
            require(ready.accepted() && ready.orderings==1 && ready.expansions==
                observedInt(continuationBudget,"expanded") && ready.expansions>inheritedExpansions &&
                ready.layout==copiedLayout && ready.vias==ready.layout.getHoleCount() && ready.vias<=48 &&
                !prepared.inputGeometry.equals(ready.layout.geometryFingerprint()),
                "ready Result preserves first-order lineage, cumulative inherited work, cap and copied layout identity");
            require(observedInt(copiedOwner,"pass")==1 && observedInt(copiedOwner,"passes")==1 &&
                observedInt(copiedOwner,"completedOrderings")==1 &&
                observedInt(copiedOwner,"yieldedOrderings")==1 &&
                !observedBoolean(copiedOwner,"comparisonComplete"),
                "continuation stops after exactly one current ordering without claiming comparison completion");
            budget.end(0,0);

            final PcbLayerRoutingPrototype.Result continuationResult;
            budget.begin("consume the continuation's current-order Result");
            continuationResult=continuation.takeReadyOrdering();
            budget.end(0,0,true);
            require(continuationResult==ready && continuation.isComplete() &&
                continuation.boundaryReason()==null &&
                observedBoolean(copiedOwner,"orderingLeaseClosed") &&
                observedField(copiedOwner,"detachedOrderingContinuation")==null &&
                observedField(copiedOwner,"readyOrdering")==null &&
                observedInt(copiedOwner,"viaCost")==240 && observedInt(copiedOwner,"maximumVias")==48,
                "one-time take returns the exact continuation Result and closes this copied lease");
            final PcbLayerRoutingPrototype.Session transferredOwner=copiedOwner;
            expectIllegalState(new Runnable(){public void run(){transferredOwner.orderingSummary();}},
                "single-order handoff cannot publish an ordinary comparison summary");
            expectIllegalState(new Runnable(){public void run(){transferredOwner.advanceOrderingSlice(1);}},
                "single-order handoff cannot advance into a new template or pass");

            budget.begin("existing RB56 finalizer, independent physical checks and comparator");
            FinalizedVariant finalized=owner.finalizeAndConsider(prepared.board,
                prepared.plan.layoutSeed,continuationResult);
            budget.end(0,0,true);
            require(finalized.invoked && finalized.accepted && finalized.ownerWinner &&
                "FINALIZED_AND_CONSIDERED_BY_EXISTING_OWNER".equals(finalized.status) &&
                finalized.geometrySha256!=null && finalized.qualityMilli>=0 &&
                finalized.evidence!=null && finalized.evidence.graph!=null &&
                finalized.evidence.raster!=null && finalized.evidence.orderedPadPairs==17161L &&
                finalized.evidence.referenceClearancePairs>=0,
                "the continuation-owned Result passes production finalization, independent whole-board oracles and the real comparator");
            try {
                PcbBoardLayout winner=(PcbBoardLayout)owner.winnerLayout.get(owner.session);
                require(winner!=null,"existing saved7 owner stores the finalized candidate winner");
                Rectangle winnerBounds=winner.getBoardOutline();
                require(owner.winnerAttempt.getInt(owner.session)==owner.candidate.attempt &&
                    owner.winnerPlacementScore.getLong(owner.session)==owner.candidate.cheapScore &&
                    owner.winnerArea.getLong(owner.session)==(long)winnerBounds.width*winnerBounds.height &&
                    owner.winnerArea.getLong(owner.session)==finalized.area &&
                    Double.compare(owner.winnerQuality.getDouble(owner.session),finalized.quality)==0 &&
                    finalized.geometrySha256.equals(PlayerSessionFingerprint.of(winner.geometryFingerprint())) &&
                    finalized.evidence.orderedPadPairs==
                        ((long)prepared.board.getPadIds().size()*prepared.board.getPadIds().size()),
                    "actual saved7 placement attempt wins with its production score and independently validated finalized geometry");
            } catch(IllegalAccessException failure) {
                throw new IllegalStateException("Could not inspect the actual saved7 owner winner",failure);
            }

            require(source.expansions()==inheritedExpansions && observedField(source,"search")==sourceSearch &&
                observedField(sourceSearch,"branch")==sourceBranch &&
                observedInt(sourceBranch,"expanded")==0 &&
                prepared.planCanonical.equals(prepared.plan.canonical()) &&
                prepared.boardCanonical.equals(logicalBoardFingerprint(prepared.board)) &&
                prepared.inputGeometry.equals(prepared.placement.geometryFingerprint()),
                "branch replay, remainder, finalization and scoring preserve the original source owners and inputs");
            budget.checkTotal("completed copied-order finalizer contract");
            require(budget.units<=RemainderOperationBudget.MAX_UNITS &&
                budget.realOwnerWork>0 && budget.stateTransitions>=6,
                "one shared operation accounts actual work and explicit state transitions without resetting phase counters");
            require(budget.realOwnerWork==(long)sourceWork+copyWork+branchWork+remainderWork,
                "one operation-wide owner-work total reconciles preparation frontier, copy, replay and remainder phases");
            System.out.println("P07_DETACHED_ORDERING_FINALIZER {\"protocol\":\"p07-detached-ordering-finalizer-v1\""+
                ",\"runId\":"+quote(runId)+",\"caseId\":\"SAVED7_ROOT42_DENSITY7_CANDIDATE4\""+
                ",\"sourceViaCost\":"+sourceViaCost+",\"copiedViaCost\":240,\"viaCap\":48"+
                ",\"sourceExpansionsAtFrontier\":"+inheritedExpansions+
                ",\"resultOrderings\":"+continuationResult.orderings+
                ",\"resultExpansions\":"+continuationResult.expansions+
                ",\"resultVias\":"+continuationResult.vias+
                ",\"branchNet\":"+quote(inheritedNet)+",\"branchStart\":"+quote(inheritedStart)+
                ",\"branchRoot\":"+quote(inheritedRoot)+",\"branchOrdinal\":"+inheritedBranchOrdinal+
                ",\"sourceCaptureCalls\":"+sourceCalls+",\"sourceCaptureWork\":"+sourceWork+
                ",\"copyCalls\":"+copyCalls+",\"copyWork\":"+copyWork+
                ",\"branchReplayCalls\":"+branchCalls+",\"branchReplayWork\":"+branchWork+
                ",\"remainderCalls\":"+remainderCalls+
                ",\"remainderWork\":"+remainderWork+
                ",\"finalization\":\"FINALIZED_AND_CONSIDERED_BY_EXISTING_OWNER\""+
                ",\"ownerWinner\":true,\"finalGeometrySha256\":"+quote(finalized.geometrySha256)+
                ",\"units\":"+budget.units+",\"realOwnerWork\":"+budget.realOwnerWork+
                ",\"stateTransitions\":"+budget.stateTransitions+
                ",\"zeroSearchWorkUnits\":"+budget.zeroSearchWorkUnits+
                ",\"maximumUnitElapsedNanos\":"+budget.maxUnitNanos+
                ",\"elapsedNanos\":"+budget.elapsedNanos()+
                ",\"ordinaryComparisonComplete\":false,\"layoutQualification\":false,\"adoption\":false}");
        } finally {
            if(continuation!=null) continuation.release();
            if(replay!=null) replay.release();
            if(copy!=null) copy.cancel();
        }
    }

    private static void verifyDetachedOrderingBoundary(String runId) {
        final RemainderOperationBudget budget=new RemainderOperationBudget();
        PcbLayerRoutingPrototype.DetachedBranchCopyTask copy=null;
        PcbLayerRoutingPrototype.DetachedBranchReplay replay=null;
        PcbLayerRoutingPrototype.DetachedOrderingContinuation continuation=null;
        PreparedStreamCase prepared=null;
        PcbLayerRoutingPrototype.Session source=null,copiedOwner=null;
        Object sourceSearch=null,sourceBranch=null,copiedSearch=null,copiedBranch=null,copiedTemplate=null;
        PcbBoardLayout sourceSearchLayout=null;
        String sourceGeometry=null;
        boolean cleanupComplete=false;
        try {
            budget.begin("select maintained saved5 boundary fixture");
            final StreamFixture fixture=findSaved5Version10Fixture();
            budget.end(0,0);
            budget.begin("prepare saved5 plan and real candidate placement");
            prepared=prepareStreamCase(fixture);
            budget.end(0,0);
            require(fixture.rootSeed==5L && fixture.placementVersion==10 &&
                fixture.candidateAttempt==1 && fixture.placementSeed==-7630701392004902526L &&
                fixture.routingSeed==-9106650364266936003L &&
                prepared.board.getComponentIds().size()==54 && prepared.board.getPadIds().size()==131,
                "boundary fixture is the existing saved5/version10/candidate1 real RB56 placement");

            final CountingObserver sourceObserver=new CountingObserver();
            final CountingObserver detachedObserver=new CountingObserver();
            budget.begin("saved5 FULLER ordering source setup");
            source=PcbLayerRoutingPrototype.beginOrderingCandidates(prepared.board,prepared.placement,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,sourceObserver,
                PcbLayerRoutingPrototype.MAX_EXPANSIONS,48,null);
            budget.end(0,0,true);

            int sourceWork=0,sourceCalls=0;
            budget.begin("capture saved5 pass-zero AC_RETURN branch before expansion");
            try {
                while(sourceCalls<STREAM_MAX_ADVANCES) {
                    Object search=observedField(source,"search");
                    Object branch=search==null?null:observedField(search,"branch");
                    if(branch!=null) {
                        sourceSearch=search;
                        sourceBranch=branch;
                        break;
                    }
                    if(sourceWork>=RemainderOperationBudget.MAX_WORK_PER_UNIT)
                        throw new AssertionError("saved5 initial branch capture exhausted its 32768-step controller unit");
                    PcbLayerRoutingPrototype.OrderingProgress sourceProgress=null;
                    try { sourceProgress=source.advanceOrderingSlice(1); }
                    finally {
                        int used=source.lastWork();
                        if(used<0 || used>1 || sourceWork>RemainderOperationBudget.MAX_WORK_PER_UNIT-used)
                            throw new AssertionError("saved5 initial branch capture exceeded its 32768-step controller unit");
                        sourceWork+=used;
                        sourceCalls++;
                    }
                    require(sourceProgress==PcbLayerRoutingPrototype.OrderingProgress.WORKING,
                        "saved5 source capture stops before the first ordering emits or terminates");
                }
                require(sourceBranch!=null && sourceWork<=RemainderOperationBudget.MAX_WORK_PER_UNIT,
                    "saved5 pass-zero branch frontier is captured inside one shared controller unit");
                sourceSearchLayout=(PcbBoardLayout)observedField(sourceSearch,"layout");
                sourceGeometry=sourceSearchLayout.geometryFingerprint();
                require(observedInt(sourceSearch,"pass")==0 && observedInt(source,"pass")==0 &&
                    "AC_RETURN".equals(observedField(sourceSearch,"currentNet")) &&
                    "AC_RETURN".equals(observedField(sourceBranch,"net")) &&
                    "D4.K".equals(((PcbPadPlacement)observedField(sourceBranch,"start")).getPadId()) &&
                    "D2.A".equals(((PcbPadPlacement)observedField(sourceBranch,"root")).getPadId()) &&
                    observedInt(sourceBranch,"branchOrdinal")==1 &&
                    observedInt(sourceBranch,"expanded")==0 &&
                    observedInt(observedField(sourceBranch,"queue"),"size")==2 &&
                    observedInt(observedField(sourceBranch,"best"),"size")==2 &&
                    ((PcbBoardHole[])observedField(sourceBranch,"holes")).length==0 &&
                    sourceSearchLayout.getHoleCount()==0 && sourceSearchLayout.getTraces().isEmpty() &&
                    observedField(sourceSearch,"publisher")==null && source.expansions()==0 &&
                    observedInt(source,"maximumVias")==48 && observedInt(source,"viaCost")==60,
                    "saved5 source is the exact pristine AC_RETURN D4.K-to-D2.A branch1 with no VIA prefix");
            } finally {
                budget.end(sourceWork,RemainderOperationBudget.MAX_WORK_PER_UNIT,sourceBranch!=null);
            }

            final int inheritedExpansions=source.expansions();
            final String boardCanonical=prepared.boardCanonical;
            final String planCanonical=prepared.planCanonical;
            final String placementGeometry=prepared.inputGeometry;
            final int sourceObserverCalls=sourceObserver.calls;
            budget.begin("charged cost60/cap48 saved5 copy setup");
            copy=PcbLayerRoutingPrototype.beginDetachedBranchCopy(source,detachedObserver,48,60);
            budget.end(0,0,true);
            int copyCalls=0;
            long copyWork=0L;
            while(!copy.isComplete()) {
                if(++copyCalls>RemainderOperationBudget.MAX_UNITS)
                    throw new AssertionError("saved5 detached copy exceeded its shared 640-unit guard");
                budget.begin("charged saved5 owner copy");
                boolean done=false;
                long workBefore=copy.workSteps(),workDelta;
                try { done=copy.advance(RemainderOperationBudget.MAX_WORK_PER_UNIT); }
                finally {
                    workDelta=copy.workSteps()-workBefore;
                    if(workDelta<0 || workDelta>RemainderOperationBudget.MAX_WORK_PER_UNIT)
                        throw new AssertionError("saved5 copy reported work outside its controller quantum");
                    copyWork+=workDelta;
                    budget.end((int)workDelta,RemainderOperationBudget.MAX_WORK_PER_UNIT,done);
                }
                require(copy.lastWork()==workDelta,
                    "saved5 copy cursor's per-call work matches its cumulative charged work");
                require(done==copy.isComplete() && (workDelta>0L || done),
                    "saved5 charged copy reports real bounded progress or completion");
            }
            budget.begin("consume saved5 charged copy result");
            replay=copy.result();
            budget.end(0,0,true);
            require(replay!=null && !replay.isComplete(),
                "saved5 copy result is a live initial branch cursor awaiting its actual replay");

            budget.begin("audit saved5 copied frontier and inherited request state");
            copiedOwner=(PcbLayerRoutingPrototype.Session)observedField(replay,"owner");
            copiedSearch=replay.getSearchForDiagnostics();
            copiedBranch=replay.getBranchForDiagnostics();
            PcbBoardLayout copiedLayout=replay.getLayout();
            copiedTemplate=observedField(copiedOwner,"template");
            Object sourceBudget=observedField(source,"budget");
            Object copiedBudget=observedField(copiedOwner,"budget");
            require(copiedOwner!=source && copiedSearch!=sourceSearch && copiedBranch!=sourceBranch &&
                observedField(copiedSearch,"owner")==copiedOwner &&
                observedField(copiedOwner,"search")==copiedSearch &&
                observedField(copiedSearch,"branch")==copiedBranch &&
                observedField(copiedOwner,"candidate")==copiedLayout &&
                observedField(copiedSearch,"layout")==copiedLayout &&
                observedField(copiedSearch,"budget")==copiedBudget &&
                observedInt(copiedSearch,"pass")==0 && observedInt(copiedOwner,"pass")==0 &&
                observedInt(copiedBudget,"maximum")==PcbLayerRoutingPrototype.MAX_EXPANSIONS &&
                observedInt(copiedBudget,"expanded")==inheritedExpansions &&
                observedInt(sourceBudget,"expanded")==inheritedExpansions &&
                observedInt(copiedOwner,"viaCost")==60 && observedInt(copiedSearch,"viaCost")==60 &&
                observedInt(copiedOwner,"maximumVias")==48 && observedInt(copiedSearch,"maximumVias")==48,
                "saved5 copy retains the same pass-zero Search, layout, source budget, cost60 and cap48");
            require("AC_RETURN".equals(observedField(copiedBranch,"net")) &&
                "D4.K".equals(((PcbPadPlacement)observedField(copiedBranch,"start")).getPadId()) &&
                "D2.A".equals(((PcbPadPlacement)observedField(copiedBranch,"root")).getPadId()) &&
                observedInt(copiedBranch,"branchOrdinal")==1 &&
                observedInt(copiedBranch,"expanded")==0 &&
                ((PcbBoardHole[])observedField(copiedBranch,"holes")).length==0 &&
                source.expansions()==inheritedExpansions && observedField(source,"search")==sourceSearch &&
                observedField(sourceSearch,"branch")==sourceBranch &&
                sourceSearchLayout.geometryFingerprint().equals(sourceGeometry) &&
                prepared.planCanonical.equals(prepared.plan.canonical()) &&
                prepared.boardCanonical.equals(logicalBoardFingerprint(prepared.board)) &&
                placementGeometry.equals(prepared.placement.geometryFingerprint()),
                "saved5 copy preserves the exact initial branch lineage and source graph/layout state");
            budget.end(0,0);

            int branchCalls=0;
            long branchWork=0L;
            while(!replay.isComplete()) {
                if(++branchCalls>RemainderOperationBudget.MAX_UNITS)
                    throw new AssertionError("saved5 detached branch replay exceeded its shared 640-unit guard");
                budget.begin("replay saved5 AC_RETURN branch and actual Publisher");
                boolean done=false;
                int used=0;
                try { done=replay.advance(RemainderOperationBudget.MAX_WORK_PER_UNIT); }
                finally {
                    used=replay.lastWork();
                    branchWork+=used;
                    budget.end(used,RemainderOperationBudget.MAX_WORK_PER_UNIT,done);
                }
                require(done==replay.isComplete() && (used>0 || done),
                    "saved5 branch replay reports bounded real work or its terminal Publisher transition");
            }
            budget.begin("audit completed saved5 AC_RETURN Publisher");
            Object publisher=replay.getPublisherForDiagnostics();
            require(publisher!=null && observedInt(publisher,"stage")==6 &&
                observedField(copiedSearch,"branch")==null && observedField(copiedSearch,"publisher")==null &&
                observedField(copiedBranch,"found")!=null &&
                observedField(copiedBranch,"foundTerminal")!=null &&
                observedBoolean(copiedBranch,"terminalEmitted") &&
                observedInt(copiedBranch,"expanded")==45 &&
                observedInt(copiedBudget,"expanded")==45 &&
                observedInt(copiedOwner,"pass")==0 && observedInt(copiedOwner,"viaCost")==60 &&
                copiedOwner!=source && source.expansions()==inheritedExpansions &&
                observedInt(sourceBranch,"expanded")==0 &&
                sourceSearchLayout.geometryFingerprint().equals(sourceGeometry),
                "saved5 clone replays the recorded 45-expansion first AC_RETURN branch without touching its source");
            budget.end(0,0);

            budget.begin("single-use saved5 current-order handoff");
            continuation=replay.takeOrderingContinuation();
            budget.end(0,0,true);
            require(observedField(continuation,"owner")==copiedOwner &&
                observedField(continuation,"search")==copiedSearch &&
                observedField(copiedOwner,"detachedOrderingContinuation")==continuation &&
                observedField(copiedOwner,"search")==copiedSearch &&
                observedField(continuation,"template")==copiedTemplate &&
                observedBoolean(copiedOwner,"orderingStream") &&
                observedInt(copiedOwner,"viaCost")==60 && observedInt(copiedOwner,"maximumVias")==48,
                "saved5 receiver owns only the exact copied current-order Search");

            budget.begin("release transferred branch sender without affecting receiver");
            replay.release();
            budget.end(0,0,true);
            require(observedField(continuation,"owner")==copiedOwner &&
                observedField(continuation,"search")==copiedSearch &&
                observedField(continuation,"layout")==copiedLayout &&
                observedField(continuation,"template")==copiedTemplate &&
                observedField(copiedOwner,"detachedOrderingContinuation")==continuation,
                "releasing the transferred sender leaves the copied current-order receiver live");

            int remainderCalls=0;
            long remainderWork=0L;
            PcbLayerRoutingPrototype.DetachedOrderingProgress progress;
            while(true) {
                if(++remainderCalls>RemainderOperationBudget.MAX_UNITS)
                    throw new AssertionError("saved5 current-order remainder exceeded its shared 640-unit guard");
                budget.begin("advance saved5 copied pass-zero ordering remainder");
                progress=null;
                try { progress=continuation.advance(RemainderOperationBudget.MAX_WORK_PER_UNIT); }
                finally {
                    int used=continuation.lastWork();
                    remainderWork+=used;
                    budget.end(used,RemainderOperationBudget.MAX_WORK_PER_UNIT,
                        progress!=null && progress!=PcbLayerRoutingPrototype.DetachedOrderingProgress.WORKING);
                }
                if(progress==PcbLayerRoutingPrototype.DetachedOrderingProgress.WORKING) continue;
                if(progress==PcbLayerRoutingPrototype.DetachedOrderingProgress.ROUTE_READY)
                    throw new AssertionError("saved5 detached pass zero unexpectedly produced a route");
                if(progress!=PcbLayerRoutingPrototype.DetachedOrderingProgress.ORDERING_BOUNDARY)
                    throw new AssertionError("saved5 continuation returned an unknown terminal state");
                break;
            }

            budget.begin("audit saved5 typed current-order rejection");
            final String actualReason=continuation.boundaryReason();
            final String boundaryClass=detachedBoundaryClass(actualReason);
            final int boundaryExpansions=observedInt(copiedBudget,"expanded");
            final int remainingBudget=observedInt(copiedBudget,"maximum")-boundaryExpansions;
            require(continuation.isComplete() && boundaryClass!=null &&
                !actualReason.contains("TOTAL_SEARCH_LIMIT") && boundaryExpansions>0 && remainingBudget>0 &&
                boundaryExpansions<PcbLayerRoutingPrototype.MAX_EXPANSIONS,
                "saved5 receiver stops on an actual NO_PATH or QUALITY_LIMIT with request budget remaining");
            require(observedInt(continuation,"pass")==0 &&
                observedBoolean(copiedOwner,"orderingLeaseClosed") &&
                observedField(copiedOwner,"detachedOrderingContinuation")==null &&
                observedField(copiedOwner,"search")==null && observedField(copiedOwner,"candidate")==null &&
                observedField(copiedOwner,"readyOrdering")==null &&
                observedField(copiedOwner,"turnBoundWorkspace")==null &&
                observedField(copiedOwner,"template")==copiedTemplate &&
                observedField(continuation,"owner")==null &&
                observedField(continuation,"search")==null &&
                observedField(continuation,"layout")==null &&
                observedField(continuation,"template")==null &&
                observedField(continuation,"budget")==null &&
                observedInt(copiedOwner,"passes")==1 && observedInt(copiedOwner,"pass")==1 &&
                observedInt(copiedOwner,"completedOrderings")==1 &&
                observedInt(copiedOwner,"yieldedOrderings")==0 &&
                observedField(copiedOwner,"orderingTermination")==null &&
                !observedBoolean(copiedOwner,"comparisonComplete") &&
                !observedBoolean(copiedOwner,"complete"),
                "boundary retires its current pass without rebuilding a next Search or claiming stream completion");
            require(remainderWork>0 && branchWork>0 &&
                source.expansions()==inheritedExpansions && observedField(source,"search")==sourceSearch &&
                observedField(sourceSearch,"branch")==sourceBranch && observedInt(sourceBranch,"expanded")==0 &&
                sourceSearchLayout.geometryFingerprint().equals(sourceGeometry) &&
                placementGeometry.equals(prepared.placement.geometryFingerprint()) &&
                planCanonical.equals(prepared.plan.canonical()) &&
                boardCanonical.equals(logicalBoardFingerprint(prepared.board)),
                "saved5 copy and failed remainder preserve original plan, board, placement and branch source");
            budget.end(0,0);

            final int observerCallsAtBoundary=detachedObserver.calls;
            final int ownerLastWorkAtBoundary=copiedOwner.lastWork();
            budget.begin("poll saved5 terminal boundary without further work");
            PcbLayerRoutingPrototype.DetachedOrderingProgress repeated=continuation.advance(
                RemainderOperationBudget.MAX_WORK_PER_UNIT);
            int repeatedWork=continuation.lastWork();
            budget.end(repeatedWork,RemainderOperationBudget.MAX_WORK_PER_UNIT,false);
            require(repeated==PcbLayerRoutingPrototype.DetachedOrderingProgress.ORDERING_BOUNDARY &&
                repeatedWork==0 && actualReason.equals(continuation.boundaryReason()) &&
                detachedObserver.calls==observerCallsAtBoundary &&
                observedInt(copiedBudget,"expanded")==boundaryExpansions &&
                copiedOwner.lastWork()==ownerLastWorkAtBoundary,
                "repeated saved5 boundary polling returns the same reason with zero work and no observer calls");

            budget.begin("reject result take and raw Session bypasses after boundary");
            final PcbLayerRoutingPrototype.DetachedOrderingContinuation terminalReceiver=continuation;
            final PcbLayerRoutingPrototype.Session terminalOwner=copiedOwner;
            expectIllegalState(new Runnable(){public void run(){terminalReceiver.takeReadyOrdering();}},
                "a no-route ordering boundary exposes no takeable Result");
            expectIllegalState(new Runnable(){public void run(){terminalOwner.advanceOrderingSlice(1);}},
                "raw ordering advancement cannot bypass a transferred terminal boundary");
            expectIllegalState(new Runnable(){public void run(){terminalOwner.advanceSlice(1);}},
                "raw legacy advancement cannot bypass a transferred terminal boundary");
            expectIllegalState(new Runnable(){public void run(){terminalOwner.orderingSummary();}},
                "one failed current ordering cannot publish an ordinary whole-stream summary");
            require(detachedObserver.calls==observerCallsAtBoundary &&
                observedInt(copiedBudget,"expanded")==boundaryExpansions &&
                copiedOwner.lastWork()==ownerLastWorkAtBoundary &&
                observedField(copiedOwner,"search")==null && observedField(copiedOwner,"candidate")==null &&
                observedField(copiedOwner,"readyOrdering")==null,
                "rejected receiver and raw Session APIs leave all boundary counters and ownership unchanged");
            budget.end(0,0);

            budget.begin("release terminal saved5 copied owners");
            continuation.release();
            replay.release();
            copy.cancel();
            budget.end(0,0,true);
            cleanupComplete=true;
            budget.begin("verify source preservation after copied-owner release");
            require(source.expansions()==inheritedExpansions && observedField(source,"search")==sourceSearch &&
                observedField(sourceSearch,"branch")==sourceBranch && observedInt(sourceBranch,"expanded")==0 &&
                sourceSearchLayout.geometryFingerprint().equals(sourceGeometry) &&
                placementGeometry.equals(prepared.placement.geometryFingerprint()) &&
                planCanonical.equals(prepared.plan.canonical()) &&
                boardCanonical.equals(logicalBoardFingerprint(prepared.board)) &&
                sourceObserver.calls==sourceObserverCalls,
                "releasing the copied boundary cannot mutate or observe the original saved5 source");
            require(observedField(continuation,"owner")==null && observedField(continuation,"search")==null &&
                observedField(continuation,"layout")==null && observedField(continuation,"budget")==null &&
                continuation.isComplete() && actualReason.equals(continuation.boundaryReason()),
                "terminal saved5 receiver releases copied references while preserving its scalar boundary receipt");
            budget.end(0,0);

            budget.checkTotal("completed saved5 current-order boundary contract");
            require(budget.units<=RemainderOperationBudget.MAX_UNITS &&
                budget.realOwnerWork==(long)sourceWork+copyWork+branchWork+remainderWork &&
                budget.realOwnerWork>0 && budget.stateTransitions>=6,
                "single uninterrupted saved5 operation reconciles work and explicit state transitions within its limits");
            System.out.println("P07_DETACHED_ORDERING_BOUNDARY {\"protocol\":\"p07-detached-ordering-boundary-v1\""+
                ",\"runId\":"+quote(runId)+",\"caseId\":"+quote(fixture.caseId)+
                ",\"rootSeed\":\"5\",\"placementVersion\":10,\"candidateAttempt\":1"+
                ",\"placementSeed\":\"-7630701392004902526\",\"routingSeed\":\"-9106650364266936003\""+
                ",\"sourceViaCost\":60,\"copiedViaCost\":60,\"viaCap\":48"+
                ",\"branchNet\":\"AC_RETURN\",\"branchStart\":\"D4.K\",\"branchRoot\":\"D2.A\""+
                ",\"branchOrdinal\":1,\"prefixVias\":0,\"branchExpansions\":45"+
                ",\"sourceCaptureCalls\":"+sourceCalls+",\"sourceCaptureWork\":"+sourceWork+
                ",\"copyCalls\":"+copyCalls+",\"copyWork\":"+copyWork+
                ",\"branchReplayCalls\":"+branchCalls+",\"branchReplayWork\":"+branchWork+
                ",\"remainderCalls\":"+remainderCalls+",\"remainderWork\":"+remainderWork+
                ",\"progress\":\"ORDERING_BOUNDARY\",\"boundaryClass\":"+quote(boundaryClass)+
                ",\"actualBoundaryReason\":"+quote(actualReason)+
                ",\"cumulativeExpansions\":"+boundaryExpansions+
                ",\"remainingRequestBudget\":"+remainingBudget+
                ",\"passesStarted\":1,\"completedOrderings\":1,\"yieldedOrderings\":0"+
                ",\"ordinaryComparisonComplete\":false,\"units\":"+budget.units+
                ",\"realOwnerWork\":"+budget.realOwnerWork+
                ",\"stateTransitions\":"+budget.stateTransitions+
                ",\"zeroSearchWorkUnits\":"+budget.zeroSearchWorkUnits+
                ",\"maximumUnitElapsedNanos\":"+budget.maxUnitNanos+
                ",\"elapsedNanos\":"+budget.elapsedNanos()+
                ",\"sourceGeometrySha256\":"+quote(PlayerSessionFingerprint.of(sourceGeometry))+
                ",\"sourceUnchanged\":true,\"layoutQualification\":false,\"adoption\":false}");
        } finally {
            if(!cleanupComplete) {
                if(continuation!=null) continuation.release();
                if(replay!=null) replay.release();
                if(copy!=null) copy.cancel();
            }
        }
    }

    private static StreamFixture findSaved5Version10Fixture() {
        StreamFixture found=null;
        for(StreamFixture candidate:SEED5_STREAM_FIXTURES) {
            if(candidate.rootSeed==5L && candidate.placementVersion==10 &&
                    candidate.candidateAttempt==1 &&
                    candidate.placementSeed==-7630701392004902526L &&
                    candidate.routingSeed==-9106650364266936003L) {
                require(found==null,"saved5/version10 boundary fixture identity is unique");
                found=candidate;
            }
        }
        require(found!=null,"maintained saved5/version10 boundary fixture is present by its signed identity");
        return found;
    }

    private static String detachedBoundaryClass(String actualReason) {
        if(actualReason==null) return null;
        if("NO_PATH".equals(actualReason) || actualReason.endsWith(":NO_PATH")) return "NO_PATH";
        if("QUALITY_LIMIT".equals(actualReason) || actualReason.endsWith(":QUALITY_LIMIT") ||
                actualReason.endsWith(":BRANCH_QUALITY_LIMIT")) return "QUALITY_LIMIT";
        return null;
    }

    private static PcbLayerRoutingPrototype.Result advanceUntilFirstYield(
            PcbLayerRoutingPrototype.Session session,SeededPcbLayoutGenerator.AttemptObserver observer) {
        int advances=0;
        while(true) {
            if(++advances>STREAM_MAX_ADVANCES)
                throw new AssertionError("P07 stream failed to reach a first result or terminal state");
            PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(128);
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY)
                return session.takeReadyOrdering();
            if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE)
                return null;
        }
    }

    private static RuntimeException runObserverFailure(final PcbLayerRoutingPrototype.Session session) {
        try { session.advanceOrderingSlice(1); }
        catch(RuntimeException failure) { return failure; }
        throw new AssertionError("armed P07 attempt observer did not abort continuation");
    }

    private static RuntimeException runDiagnosticFailure(PcbLayerRoutingPrototype.Session session) {
        try {
            int operations=0;
            while(true) {
                if(++operations>STREAM_MAX_ADVANCES)
                    throw new AssertionError("diagnostic sink failure was not reached in successor search");
                PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(128);
                if(progress==PcbLayerRoutingPrototype.OrderingProgress.COMPLETE)
                    throw new AssertionError("diagnostic sink did not fail before all orderings completed");
                if(progress==PcbLayerRoutingPrototype.OrderingProgress.ROUTE_READY)
                    session.takeReadyOrdering();
            }
        } catch(RuntimeException failure) { return failure; }
    }

    private static RuntimeException runFailureApi(final PcbLayerRoutingPrototype.Session session,
            int api) {
        try {
            switch(api) {
                case 0: session.advanceOrderingSlice(1); break;
                case 1: session.takeReadyOrdering(); break;
                case 2: session.orderingSummary(); break;
                case 3: session.result(); break;
                case 4: session.advanceSlice(1); break;
                default: throw new AssertionError("Unknown failure-canary API");
            }
        } catch(RuntimeException failure) { return failure; }
        throw new AssertionError("latched P07 failure allowed a later API call");
    }


    private static void verifyActualP07RequestBoundary(String runId,StreamRun independent) {
        verifyP07RequestBoundaryPreflight();
        ActualRequestBoundaryRun convenience=runActualP07RequestBoundary(false);
        verifyP07RequestBoundaryEvent(convenience);
        ActualRequestBoundaryRun explicitNearest=runActualP07RequestBoundary(true);
        verifyP07RequestBoundaryEvent(explicitNearest);
        verifyP07RequestBoundaryParity(convenience,explicitNearest);
        require(convenience.event.context().selectedPlacementAttempt()==independent.prepared.fixture.candidateAttempt &&
            convenience.event.context().rawPlacementFingerprint().equals(independent.prepared.inputGeometry),
            "request zero selects the independently frozen saved7 candidate4 pristine placement");
        PcbBoardLayout boundaryWinner=(PcbBoardLayout)observedField(convenience.session,"mediumTwoLayerLayout");
        Rectangle boundaryBounds=boundaryWinner==null?null:boundaryWinner.getBoardOutline();
        require(boundaryWinner!=null &&
            (long)boundaryBounds.width*boundaryBounds.height==AM_ORDER_FINAL_AREA[0] &&
            PcbCompactnessContractTest.layout24PrefixReferenceSha256(boundaryWinner).equals(
                AM_ORDER_FINAL_GEOMETRY[0]) &&
            "FIRST_SUCCESS_GUIDED".equals(convenience.event.receipt().termination),
            "request-zero generator stops at the independently observed saved7 order-0 finalized route");
        verifyP07RequestBoundaryRetry(convenience);
        verifyP07RequestBoundaryRetry(explicitNearest);
        verifyP07RequestBoundaryDiagnosticsFailure(runId);
        System.out.println("P07_REQUEST_BOUNDARY_CONTRACT {\"protocol\":\"p07-request-boundary-contract-v1\""+
            ",\"runId\":"+quote(runId)+",\"status\":\"PASS_REQUEST_BOUNDARY_CONTRACT_ONLY\""+
            ",\"rootSeed\":\"42\",\"placementVersion\":7,"+
            "\"requestIndex\":0,\"selectedPlacementAttempt\":"+
            convenience.event.context().selectedPlacementAttempt()+
            ",\"advancesToBoundary\":"+convenience.advances+
            ",\"receiptTermination\":"+quote(convenience.event.receipt().termination)+
            ",\"routeOutcome\":"+quote(convenience.event.routeOutcome())+
            ",\"diagnosticCallbacks\":"+convenience.diagnostics.calls+
            ",\"explicitNearestParity\":true,"+
            "\"diagnosticFailureNoCompletedRequest\":true,"+
            "\"layoutQualification\":false,\"adoption\":false}");
    }

    private static void verifyP07RequestBoundaryPreflight() {
        for(Field field:SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration.class.getDeclaredFields())
            if(!Modifier.isStatic(field.getModifiers()))
                require(Modifier.isFinal(field.getModifiers()),
                    "P07 request-boundary configuration retains immutable instance fields");
        expectIllegalArgument(new Runnable(){public void run(){
            new SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration(
                (PcbLayerRoutingPrototype.BranchOrderStrategy)null,null);
        }},"P07 request-boundary configuration rejects a null branch-order strategy");

        final Rb56Plan plan=requestBoundaryPlan();
        final TroubleshootBoard mediumBoard=plan.board();
        final String planBefore=plan.canonical();
        final String boardBefore=logicalBoardFingerprint(mediumBoard);
        final CountingObserver observer=new CountingObserver();
        final SeededPcbLayoutGenerator generator=new SeededPcbLayoutGenerator(
            StandardPcbFootprintProviders.createRegistry(),observer);
        final SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration configuration=
            new SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration(
                (PcbLayerRoutingPrototype.Diagnostics)null);
        expectIllegalArgument(new Runnable(){public void run(){
            generator.beginWithP07RequestBoundary(mediumBoard,plan.layoutSeed,plan.routingSeed,null);
        }},"P07 request-boundary entry rejects a null configuration before generation work");

        final TroubleshootBoard nonMedium=requestBoundaryPreflightBoard(
            "P07_BOUNDARY_NON_MEDIUM",false);
        final PcbPlacementConstraints nonMediumConstraints=nonMedium.getPlacementConstraints();
        require(nonMediumConstraints.usesDefaultPhysicalPolicy() &&
            !MediumBoardPhysicalPolicy.selected(nonMediumConstraints) &&
            !nonMediumConstraints.requiresTwoLayerRouting,
            "preflight negative uses a valid default single-face board configuration");
        expectIllegalArgument(new Runnable(){public void run(){
            generator.beginWithP07RequestBoundary(nonMedium,0L,0L,configuration);
        }},"P07 request-boundary entry rejects a non-Medium board before generation work");

        final TroubleshootBoard mediumSingleLayer=requestBoundaryPreflightBoard(
            "P07_BOUNDARY_MEDIUM_SINGLE_LAYER",true);
        final PcbPlacementConstraints literalMediumSingleLayer=mediumSingleLayer.getPlacementConstraints();
        require(MediumBoardPhysicalPolicy.selected(mediumSingleLayer.getPlacementConstraints()) &&
            !mediumSingleLayer.getPlacementConstraints().requiresTwoLayerRouting,
            "preflight negative uses valid literal Medium policy constraints without a two-layer requirement");
        expectIllegalArgument(new Runnable(){public void run(){
            generator.beginWithP07RequestBoundary(mediumSingleLayer,0L,0L,configuration);
        }},"P07 request-boundary entry rejects Medium constraints without required two-layer routing");
        require(observer.calls==0 && mediumSingleLayer.getPlacementConstraints()==literalMediumSingleLayer &&
            planBefore.equals(plan.canonical()) && boardBefore.equals(logicalBoardFingerprint(mediumBoard)),
            "null and invalid boundary configurations reject before observer, board or plan mutation");
    }

    private static TroubleshootBoard requestBoundaryPreflightBoard(String id,boolean mediumPolicy) {
        TroubleshootBoard board=new TroubleshootBoard(id);
        BoardComponent resistor=new BoardComponent("R","RESISTOR",PhysicalPackages.AXIAL_RESISTOR);
        board.addComponent(resistor);
        board.addNet(new BoardNet("A"));
        board.addNet(new BoardNet("B"));
        board.addPad(new BoardPad("R.1","R","1","A"));
        board.addPad(new BoardPad("R.2","R","2","B"));
        PcbPlacementConstraints ordinary=PcbPlacementConstraints.standard(board);
        PcbPlacementConstraints constraints=mediumPolicy?
            new PcbPlacementConstraints(ordinary.getParts(),ordinary.getBarriers(),PcbCopperLayer.TOP,
                MediumBoardPhysicalPolicy.ID,MediumBoardPhysicalPolicy.VERSION,1,false):ordinary;
        board.setPlacementConstraints(constraints);
        board.validate();
        return board;
    }

    private static Rb56Plan requestBoundaryPlan() {
        Rb56Plan plan=Rb56Plan.resolve(42L,7);
        require(SeededPcbLayoutGenerator.CURRENT_VERSION==26 &&
            "tsj-generation-dependencies-v29".equals(GenerationDependencyContext.INTERPRETATION_EPOCH),
            "actual request-boundary fixture is pinned to layout-26/dependency-29 interpretation");
        require(plan.seed==42L && plan.placementInterpretationVersion==7 &&
            plan.layoutSeed==5526715536693200536L && plan.routingSeed==4493419427463069983L &&
            "RB56_CH2_SHARED_HYSTERETIC_A_NMOS_B_BJT_S5_true_S12_true_F2_O0_B5_false"
                .equals(plan.topology()),
            "actual request-boundary fixture preserves the saved7/root42 identity and named seeds");
        return plan;
    }

    private static ActualRequestBoundaryRun runActualP07RequestBoundary(boolean explicitNearest) {
        final Rb56Plan plan=requestBoundaryPlan();
        final TroubleshootBoard board=plan.board();
        final String planBefore=plan.canonical();
        final String boardBefore=logicalBoardFingerprint(board);
        require(board.getPlacementConstraints().requiresTwoLayerRouting &&
            MediumBoardPhysicalPolicy.selected(board.getPlacementConstraints()),
            "actual request-boundary fixture uses the real saved Medium two-layer constraints");
        final int[] observerCalls={0};
        final RequestBoundaryDiagnostics diagnostics=new RequestBoundaryDiagnostics(null,board);
        SeededPcbLayoutGenerator.AttemptObserver observer=new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { observerCalls[0]++; }
        };
        SeededPcbLayoutGenerator generator=new SeededPcbLayoutGenerator(
            StandardPcbFootprintProviders.createRegistry(),observer);
        final SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration configuration=explicitNearest?
            new SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration(
                PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,diagnostics):
            new SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration(diagnostics);
        SeededPcbLayoutGenerator.Session session=generator.beginWithP07RequestBoundary(
            board,plan.layoutSeed,plan.routingSeed,configuration);
        require(session.p07RequestBoundaryEvent()==null && !observedBoolean(session,"complete"),
            "actual generator boundary event starts absent on an incomplete session");
        int advances=0;
        while(session.p07RequestBoundaryEvent()==null) {
            if(++advances>640)
                throw new AssertionError("actual request-zero generator boundary exceeded the unchanged 640-advance bound");
            boolean complete=session.advance();
            if(complete)
                throw new AssertionError("request-boundary mode completed the generator before publishing its request event");
        }
        return new ActualRequestBoundaryRun(plan,board,session,
            session.p07RequestBoundaryEvent(),diagnostics,observerCalls,advances,planBefore,boardBefore);
    }

    private static void verifyP07RequestBoundaryEvent(final ActualRequestBoundaryRun run) {
        SeededPcbLayoutGenerator.P07RequestBoundaryEvent event=run.event;
        require(event!=null,"actual request-zero P07 boundary event is present");
        PcbLayerRoutingPrototype.FixedPrefixCaptureContext context=event.context();
        MediumBoardPhysicalPolicy.OrderingSearchReceipt receipt=event.receipt();
        require(context!=null && receipt!=null && event.routeOutcome()!=null &&
            event.routeOutcome().length()>0,
            "completed actual P07 request exposes separate context, receipt and route outcome fields");
        require(context.routeRequestIndex()==0 && context.layoutSeed()==run.plan.layoutSeed &&
            context.routingSeed()==run.plan.routingSeed,
            "actual event records request zero and the exact named placement and routing seeds");
        int[] eligible=context.eligiblePlacementAttempts();
        require(eligible!=null && eligible.length>0 && eligible.length<=6,
            "actual event retains the bounded real route-eligible placement list");
        TreeSet<Integer> uniqueAttempts=new TreeSet<Integer>();
        for(int attempt:eligible)
            require(uniqueAttempts.add(Integer.valueOf(attempt)),
                "actual request event retains unique eligible placement attempts");
        require(context.selectedPlacementIndex()>=0 && context.selectedPlacementIndex()<eligible.length &&
            context.selectedPlacementAttempt()==receipt.attempt &&
            eligible[context.selectedPlacementIndex()]==receipt.attempt,
            "actual selected placement attempt matches both its immutable context and typed receipt");
        require(receipt.attempt>=0 && receipt.startedOrderings>=1 &&
            receipt.startedOrderings<=PcbLayerRoutingPrototype.ORDERINGS &&
            receipt.completedOrderings>=1 && receipt.completedOrderings<=receipt.startedOrderings &&
            receipt.expansions>=0 && receipt.expansions<=PcbLayerRoutingPrototype.MAX_EXPANSIONS &&
            receipt.termination!=null && receipt.termination.length()>0,
            "actual request event retains a bounded typed ordering receipt");
        String rawFingerprint=context.rawPlacementFingerprint();
        require(rawFingerprint!=null && rawFingerprint.length()>0,
            "actual request event retains the pristine chosen-placement fingerprint");
        for(Field field:event.getClass().getDeclaredFields())
            if(!Modifier.isStatic(field.getModifiers()))
                require(Modifier.isFinal(field.getModifiers()),
                    "actual request-boundary event exposes only final instance fields");
        for(Field field:context.getClass().getDeclaredFields())
            if(!Modifier.isStatic(field.getModifiers()))
                require(Modifier.isFinal(field.getModifiers()),
                    "actual request context exposes only final instance fields");
        for(Field field:receipt.getClass().getDeclaredFields())
            if(!Modifier.isStatic(field.getModifiers()))
                require(Modifier.isFinal(field.getModifiers()),
                    "actual ordering receipt exposes only final instance fields");
        int[] preserved=new int[eligible.length];
        System.arraycopy(eligible,0,preserved,0,eligible.length);
        int[] returned=context.eligiblePlacementAttempts();
        returned[0]=returned[0]==Integer.MAX_VALUE?Integer.MIN_VALUE:Integer.MAX_VALUE;
        require(sameRequestBoundaryInts(preserved,context.eligiblePlacementAttempts()),
            "actual event context returns a defensive array copy of eligible attempts");

        @SuppressWarnings("unchecked")
        Vector<MediumBoardPhysicalPolicy.OrderingSearchReceipt> receipts=
            (Vector<MediumBoardPhysicalPolicy.OrderingSearchReceipt>)observedField(
                run.session,"mediumOrderingSearchReceipts");
        @SuppressWarnings("unchecked")
        Vector<String> outcomes=(Vector<String>)observedField(run.session,"mediumRouteOutcomes");
        int matchingOutcomes=0;
        for(String outcome:outcomes) if(event.routeOutcome().equals(outcome)) matchingOutcomes++;
        require(receipts.size()==1 && receipts.firstElement()==receipt &&
            observedInt(run.session,"mediumTwoLayerAttempts")==1 && matchingOutcomes==1,
            "request-zero event binds exactly one stored receipt and its separate actual route outcome");
        require(!observedBoolean(run.session,"complete") && run.session.mediumResult()==null &&
            observedField(run.session,"mediumPolicyResult")==null,
            "request-boundary session stays incomplete without fabricating a Medium result");
        expectIllegalState(new Runnable(){public void run(){run.session.result();}},
            "request-boundary session rejects final generator result access");
        require(run.session.p07RequestBoundaryEvent()==event,
            "request-boundary session retains its same immutable event after result rejection");
        requireP07RequestBoundaryStreamRetired(run.session);
        require(run.diagnostics.calls>0 && run.diagnostics.first!=null &&
            run.diagnostics.first.pass>=0 && run.diagnostics.first.pass<PcbLayerRoutingPrototype.ORDERINGS,
            "configuration diagnostics receive a real terminal-branch callback from the P07 stream");
        require(run.planBefore.equals(run.plan.canonical()) &&
            run.boardBefore.equals(logicalBoardFingerprint(run.board)),
            "actual P07 request-boundary observation leaves the caller plan and logical board unchanged");
    }

    private static void verifyP07RequestBoundaryParity(ActualRequestBoundaryRun first,
            ActualRequestBoundaryRun second) {
        SeededPcbLayoutGenerator.P07RequestBoundaryEvent a=first.event,b=second.event;
        PcbLayerRoutingPrototype.FixedPrefixCaptureContext ac=a.context(),bc=b.context();
        require(sameP07RequestBoundaryReceipt(a.receipt(),b.receipt()),
            "default and explicit canonical-nearest configurations preserve request receipt parity");
        require(a.routeOutcome().equals(b.routeOutcome()),
            "default and explicit canonical-nearest configurations preserve the separate route outcome");
        require(ac.layoutSeed()==bc.layoutSeed() && ac.routingSeed()==bc.routingSeed() &&
            ac.routeRequestIndex()==bc.routeRequestIndex() &&
            ac.selectedPlacementIndex()==bc.selectedPlacementIndex() &&
            ac.selectedPlacementAttempt()==bc.selectedPlacementAttempt() &&
            ac.rawPlacementFingerprint().equals(bc.rawPlacementFingerprint()) &&
            sameRequestBoundaryInts(ac.eligiblePlacementAttempts(),bc.eligiblePlacementAttempts()),
            "default and explicit canonical-nearest configurations preserve actual event context parity");
        require(first.diagnostics.calls==second.diagnostics.calls &&
            first.diagnostics.snapshots.size()==first.diagnostics.calls &&
            second.diagnostics.snapshots.size()==second.diagnostics.calls,
            "default and explicit canonical-nearest configurations retain every bounded terminal callback");
        for(int index=0;index<first.diagnostics.calls;index++) {
            PcbLayerRoutingPrototype.BranchSnapshot one=first.diagnostics.snapshots.get(index);
            PcbLayerRoutingPrototype.BranchSnapshot two=second.diagnostics.snapshots.get(index);
            for(Field field:PcbLayerRoutingPrototype.BranchSnapshot.class.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers())) continue;
                require(Modifier.isFinal(field.getModifiers()) &&
                    (field.getType()==Integer.TYPE || field.getType()==Boolean.TYPE || field.getType()==String.class),
                    "branch parity compares immutable primitive/string snapshot fields only");
                try {
                    field.setAccessible(true);
                    Object left=field.get(one),right=field.get(two);
                    require(left==null?right==null:left.equals(right),
                        "complete request-boundary branch parity at callback "+index+" field "+field.getName());
                } catch(IllegalAccessException failure) {
                    throw new AssertionError("cannot compare terminal branch field "+field.getName(),failure);
                }
            }
        }
        PcbBoardLayout firstLayout=(PcbBoardLayout)observedField(first.session,"mediumTwoLayerLayout");
        PcbBoardLayout secondLayout=(PcbBoardLayout)observedField(second.session,"mediumTwoLayerLayout");
        require(firstLayout==null?secondLayout==null:secondLayout!=null &&
            firstLayout.geometryFingerprint().equals(secondLayout.geometryFingerprint()),
            "default and explicit canonical-nearest preserve the exact finalized physical winner");
    }

    private static boolean sameP07RequestBoundaryReceipt(
            MediumBoardPhysicalPolicy.OrderingSearchReceipt first,
            MediumBoardPhysicalPolicy.OrderingSearchReceipt second) {
        return first.attempt==second.attempt &&
            first.startedOrderings==second.startedOrderings &&
            first.completedOrderings==second.completedOrderings &&
            first.yieldedOrderings==second.yieldedOrderings &&
            first.finalizedOrderings==second.finalizedOrderings &&
            first.selectedOrdering==second.selectedOrdering &&
            first.expansions==second.expansions &&
            first.guidanceStarted==second.guidanceStarted &&
            first.comparisonComplete==second.comparisonComplete &&
            first.termination.equals(second.termination);
    }

    private static boolean sameRequestBoundaryInts(int[] first,int[] second) {
        if(first==null || second==null || first.length!=second.length) return false;
        for(int i=0;i<first.length;i++) if(first[i]!=second[i]) return false;
        return true;
    }

    private static void verifyP07RequestBoundaryRetry(final ActualRequestBoundaryRun run) {
        RequestBoundaryState before=requestBoundaryState(run.session,run.observerCalls[0],run.diagnostics.calls);
        try {
            run.session.advance();
            throw new AssertionError("sealed P07 request-boundary session allowed a successor advance");
        } catch(IllegalStateException expected) {
            require("P07 request-zero diagnostic boundary is sealed".equals(expected.getMessage()),
                "sealed request boundary rejects before successor-attempt mutation");
        }
        require(run.session.p07RequestBoundaryEvent()==run.event &&
            !observedBoolean(run.session,"complete") && run.session.mediumResult()==null,
            "sealed request retry retains the event and does not complete or publish a Medium result");
        require(before.sameAs(requestBoundaryState(run.session,run.observerCalls[0],run.diagnostics.calls)),
            "sealed request retry preserves attempt, cursors, statistics and callback counts");
        expectIllegalState(new Runnable(){public void run(){run.session.result();}},
            "sealed request boundary continues to reject final result access");
        require(before.sameAs(requestBoundaryState(run.session,run.observerCalls[0],run.diagnostics.calls)) &&
            run.planBefore.equals(run.plan.canonical()) &&
            run.boardBefore.equals(logicalBoardFingerprint(run.board)),
            "result rejection leaves the sealed request and caller-owned fixture unchanged");
    }

    private static RequestBoundaryState requestBoundaryState(Object session,int observerCalls,
            int diagnosticCalls) {
        Object statistics=observedField(session,"statistics");
        @SuppressWarnings("unchecked")
        Vector<?> receipts=(Vector<?>)observedField(session,"mediumOrderingSearchReceipts");
        @SuppressWarnings("unchecked")
        Vector<?> outcomes=(Vector<?>)observedField(session,"mediumRouteOutcomes");
        @SuppressWarnings("unchecked")
        Vector<?> ranked=(Vector<?>)observedField(session,"mediumRankedAttempts");
        @SuppressWarnings("unchecked")
        Vector<?> scores=(Vector<?>)observedField(session,"mediumPlacementScores");
        StringBuilder state=new StringBuilder();
        state.append(observedInt(session,"attempt")).append('|')
            .append(observedInt(session,"mediumPlacementAttempt")).append('|')
            .append(observedInt(session,"mediumRouteAttempt")).append('|')
            .append(observedInt(session,"mediumRouteStage")).append('|')
            .append(observedInt(session,"mediumTwoLayerAttempts")).append('|')
            .append(observedInt(session,"mediumTwoLayerSuccesses")).append('|')
            .append(observedInt(session,"mediumTwoLayerCheckpoint")).append('|')
            .append(observedInt(session,"mediumTwoLayerFinalizedOrderings")).append('|')
            .append(observedInt(session,"mediumTwoLayerSelectedOrdering")).append('|')
            .append(observedInt(session,"mediumPlacementRejections")).append('|')
            .append(((Long)observedField(session,"mediumPlacementEvaluations")).longValue()).append('|')
            .append(((Long)observedField(session,"mediumTwoLayerElapsedMillis")).longValue()).append('|')
            .append(observedInt(statistics,"placements")).append('|')
            .append(observedInt(statistics,"routes")).append('|')
            .append(observedInt(statistics,"expansions")).append('|')
            .append(((Long)observedField(statistics,"routeMillis")).longValue()).append('|')
            .append(receipts.size()).append('|').append(outcomes.size()).append('|')
            .append(ranked.size()).append('|').append(scores.size()).append('|')
            .append(observerCalls).append('|').append(diagnosticCalls);
        return new RequestBoundaryState(state.toString());
    }

    private static void requireP07RequestBoundaryStreamRetired(Object session) {
        require(observedField(session,"mediumTwoLayerRoute")==null &&
            observedField(session,"mediumTwoLayerCandidate")==null &&
            observedField(session,"mediumTwoLayerRequestContext")==null,
            "request boundary retires the live P07 stream, placement candidate and request context");
        require(observedInt(session,"mediumTwoLayerCheckpoint")==-1 &&
            observedInt(session,"mediumTwoLayerFinalizedOrderings")==0 &&
            observedInt(session,"mediumTwoLayerSelectedOrdering")==-1 &&
            observedField(session,"mediumTwoLayerLastFailureDetail")==null &&
            ((Long)observedField(session,"mediumTwoLayerElapsedMillis")).longValue()==0L,
            "request boundary clears its live route cursors and transient request accounting");
    }

    private static void verifyP07RequestBoundaryDiagnosticsFailure(String runId) {
        final Rb56Plan plan=requestBoundaryPlan();
        final TroubleshootBoard board=plan.board();
        final RuntimeException expected=new RuntimeException("P07 boundary diagnostics sink sentinel");
        final RequestBoundaryDiagnostics diagnostics=new RequestBoundaryDiagnostics(expected,board);
        final int[] observerCalls={0};
        SeededPcbLayoutGenerator.AttemptObserver observer=new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { observerCalls[0]++; }
        };
        SeededPcbLayoutGenerator generator=new SeededPcbLayoutGenerator(
            StandardPcbFootprintProviders.createRegistry(),observer);
        SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration configuration=
            new SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration(diagnostics);
        SeededPcbLayoutGenerator.Session session=generator.beginWithP07RequestBoundary(
            board,plan.layoutSeed,plan.routingSeed,configuration);
        require(session.p07RequestBoundaryEvent()==null,"diagnostic-failure session starts without an event");
        int advances=0;
        RuntimeException propagated=null;
        while(propagated==null) {
            if(++advances>640)
                throw new AssertionError("P07 diagnostics failure was not reached inside the unchanged 640-advance bound");
            try {
                if(session.advance())
                    throw new AssertionError("diagnostics failure canary completed the generator before the request boundary");
            } catch(RuntimeException failure) { propagated=failure; }
        }
        require(propagated==expected && diagnostics.calls==1,
            "real P07 Diagnostics sink propagates the exact sentinel object at its first callback");
        require(session.p07RequestBoundaryEvent()==null && !observedBoolean(session,"complete") &&
            observedField(session,"mediumPolicyResult")==null &&
            observedField(session,"mediumOrderingFailure")==expected &&
            observedInt(session,"mediumTwoLayerAttempts")==0,
            "diagnostics exception aborts the actual request without inventing completion or a result");
        requireP07RequestBoundaryStreamRetired(session);
        @SuppressWarnings("unchecked")
        Vector<?> receipts=(Vector<?>)observedField(session,"mediumOrderingSearchReceipts");
        require(receipts.isEmpty(),
            "diagnostics exception does not store a completed P07 receipt or request-boundary event");
        RuntimeException resultFailure=null;
        try { session.mediumResult(); }
        catch(RuntimeException failure) { resultFailure=failure; }
        require(resultFailure==expected,
            "Medium result access rethrows the same latched diagnostics exception object");
        RequestBoundaryState beforeRetry=requestBoundaryState(session,observerCalls[0],diagnostics.calls);
        RuntimeException retryFailure=null;
        try { session.advance(); }
        catch(RuntimeException failure) { retryFailure=failure; }
        require(retryFailure==expected && diagnostics.calls==1 && session.p07RequestBoundaryEvent()==null,
            "failed diagnostics session re-advance rethrows the same object without another callback or event");
        require(beforeRetry.sameAs(requestBoundaryState(session,observerCalls[0],diagnostics.calls)),
            "failed diagnostics re-advance preserves attempt, cursors, statistics and callback counts");
        System.out.println("P07_REQUEST_BOUNDARY_CANARY {\"protocol\":\"p07-request-boundary-contract-v1\""+
            ",\"runId\":"+quote(runId)+",\"caseId\":\"SAVED7_ROOT42_DIAGNOSTICS_FAILURE\""+
            ",\"status\":\"PASS\",\"advanceUnits\":"+advances+
            ",\"diagnosticCallbacks\":"+diagnostics.calls+
            ",\"sameExceptionLatched\":true,\"completedRequest\":false"+
            ",\"layoutQualification\":false,\"adoption\":false}");
    }

    private static MediumBoardPhysicalPolicy.Result runActualGeneratorReceiptCase(String runId) {
        final Rb56Plan plan=Rb56Plan.resolve(42L,7);
        require(plan.layoutSeed==5526715536693200536L &&
            plan.routingSeed==4493419427463069983L &&
            "RB56_CH2_SHARED_HYSTERETIC_A_NMOS_B_BJT_S5_true_S12_true_F2_O0_B5_false"
                .equals(plan.topology()),
            "actual generator receipt case binds the saved7/root42 identity and named seeds");
        final TroubleshootBoard board=plan.board();
        final String planBefore=plan.canonical();
        final String boardBefore=logicalBoardFingerprint(board);
        require(board.getPlacementConstraints().requiresTwoLayerRouting,
            "generator receipt case exercises actual required-P07 routing");
        final int[] observerCalls={0};
        SeededPcbLayoutGenerator.AttemptObserver observer=new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { observerCalls[0]++; }
        };
        SeededPcbLayoutGenerator generator=new SeededPcbLayoutGenerator(
            StandardPcbFootprintProviders.createRegistry(),observer);
        SeededPcbLayoutGenerator.Session session=generator.begin(board,plan.layoutSeed,plan.routingSeed);
        int workUnits=0;
        boolean complete=false;
        PcbRoutingRejectedException expectedRejection=null;
        try {
            while(!complete) {
                if(++workUnits>640)
                    throw new AssertionError("actual Medium generator exceeded its unchanged 640-unit native bound");
                complete=session.advance();
            }
        } catch(PcbRoutingRejectedException expected) {
            expectedRejection=expected;
            complete=true;
        }
        MediumBoardPhysicalPolicy.Result result=session.mediumResult();
        require(complete && result!=null &&
            (result.accepted() || expectedRejection!=null),
            "actual Medium Session publishes its typed P07 policy result at termination");
        MediumBoardPhysicalPolicy.Statistics statistics=result.getStatistics();
        verifyOrderingReceiptStatistics(statistics);
        if(result.accepted()) {
            require(session.result()==result.getLayout(),
                "actual Medium session and typed policy result expose the same selected layout");
            AcceptedRouteEvidence finalEvidence=validateAcceptedOutput(board,result.getLayout());
            require(finalEvidence.orderedPadPairs==17161,
                "actual generator selected layout passes the complete 131-pad physical oracle");
        } else {
            require(result.getLayout()==null && statistics.twoLayerAttempts>0,
                "rejected actual Medium result retains its attempted P07 receipt without a fabricated layout");
        }
        require(planBefore.equals(plan.canonical()) && boardBefore.equals(logicalBoardFingerprint(board)),
            "actual generator receipt case preserves the caller-owned plan and logical board");
        emitGeneratorRecord(runId,plan,statistics,workUnits,observerCalls[0],result);
        return result;
    }

    private static void verifyOrderingReceiptStatistics(MediumBoardPhysicalPolicy.Statistics statistics) {
        expectIllegalArgument(new Runnable(){public void run(){
            new MediumBoardPhysicalPolicy.OrderingSearchReceipt(
                0,0,0,0,0,-1,0,false,false,"TOTAL_SEARCH_LIMIT");
        }},"a typed zero-start/zero-completed TOTAL_SEARCH_LIMIT receipt is rejected");
        expectIllegalArgument(new Runnable(){public void run(){
            new MediumBoardPhysicalPolicy.OrderingSearchReceipt(
                0,1,1,1,1,0,10,false,false,"FIRST_SUCCESS_GUIDED");
        }},"an unguided receipt cannot claim the guided first-success stop");

        require(statistics!=null && statistics.twoLayerAttempts>=1 &&
            statistics.twoLayerAttempts<=MediumBoardPhysicalPolicy.ROUTING_CANDIDATES &&
            statistics.twoLayerAttempts<=3 && statistics.placementCandidates==MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES &&
            statistics.routeAttempts==statistics.twoLayerAttempts &&
            statistics.oneFaceAttempts==0,
            "required-two-layer generator records at most three physical P07 requests and no one-face requests");
        require(statistics.orderingSearchReceipts!=null &&
            statistics.orderingSearchReceipts.size()==statistics.twoLayerAttempts &&
            statistics.routeOutcomes.size()==statistics.routeAttempts,
            "one immutable receipt and one route outcome correspond to each P07 placement request");
        long startedOrderings=0L,expansions=0L;
        TreeSet<Integer> receiptAttempts=new TreeSet<Integer>();
        TreeSet<Integer> p07OutcomeAttempts=new TreeSet<Integer>();
        int successfulReceipts=0,p07Outcomes=0;
        for(int index=0;index<statistics.orderingSearchReceipts.size();index++) {
            MediumBoardPhysicalPolicy.OrderingSearchReceipt receipt=
                statistics.orderingSearchReceipts.get(index);
            require(receipt!=null && receiptAttempts.add(receipt.attempt),
                "each P07 ordering receipt has a unique placement-attempt identity");
            require(receipt!=null && receipt.attempt>=0 && receipt.attempt<statistics.placementCandidates &&
                receipt.startedOrderings>=1 && receipt.startedOrderings<=PcbLayerRoutingPrototype.ORDERINGS &&
                receipt.completedOrderings>=1 && receipt.completedOrderings<=receipt.startedOrderings &&
                receipt.yieldedOrderings<=receipt.completedOrderings &&
                receipt.finalizedOrderings<=receipt.yieldedOrderings &&
                receipt.selectedOrdering>=-1 &&
                receipt.selectedOrdering<receipt.startedOrderings &&
                receipt.expansions>=0 && receipt.expansions<=PcbLayerRoutingPrototype.MAX_EXPANSIONS,
                "P07 receipt contains only bounded request-level stream counts");
            for(Field field:receipt.getClass().getDeclaredFields())
                require(Modifier.isFinal(field.getModifiers()),
                    "P07 ordering receipt exposes only immutable scalar state");
            if("ORDERINGS_COMPLETE".equals(receipt.termination))
                require(receipt.comparisonComplete &&
                    receipt.startedOrderings==PcbLayerRoutingPrototype.ORDERINGS &&
                    receipt.completedOrderings==PcbLayerRoutingPrototype.ORDERINGS &&
                    receipt.yieldedOrderings>0,
                    "ORDERINGS_COMPLETE receipt proves all existing orderings completed");
            if("FIRST_SUCCESS_UNGUIDED".equals(receipt.termination))
                require(!receipt.guidanceStarted && !receipt.comparisonComplete &&
                    receipt.yieldedOrderings==1,
                    "unguided first success receipt records exactly its early-stop semantics");
            if("FIRST_SUCCESS_GUIDED".equals(receipt.termination))
                require(receipt.guidanceStarted && !receipt.comparisonComplete &&
                    receipt.yieldedOrderings==1,
                    "guided first success receipt records exactly its early-stop semantics");
            if("TOTAL_SEARCH_LIMIT".equals(receipt.termination))
                require(!receipt.comparisonComplete,
                    "total-search-limit receipt cannot claim complete comparison");
            int outcomesForAttempt=0;
            for(String outcome:statistics.routeOutcomes)
                if(outcome.startsWith(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER+
                        "@"+receipt.attempt+"=")) outcomesForAttempt++;
            require(outcomesForAttempt==1,
                "each typed stream receipt binds to exactly one physical candidate outcome");
            if(receipt.finalizedOrderings>0) successfulReceipts++;
            startedOrderings+=receipt.startedOrderings;
            expansions+=receipt.expansions;
        }
        for(String outcome:statistics.routeOutcomes) {
            require(outcome!=null && outcome.length()>0,
                "P07 route outcome census contains only nonempty physical request entries");
            String p07Prefix=MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER+"@";
            if(outcome.startsWith(p07Prefix)) {
                p07Outcomes++;
                int separator=outcome.indexOf('=');
                require(separator>p07Prefix.length() && outcome.indexOf('=',separator+1)<0,
                    "P07 route outcome has one parseable attempt separator");
                int attempt=Integer.parseInt(outcome.substring(p07Prefix.length(),separator));
                require(attempt>=0 && attempt<statistics.placementCandidates &&
                    p07OutcomeAttempts.add(attempt),
                    "each P07 route outcome has a unique valid placement-attempt identity");
                require(!outcome.contains("ORDERING_") && !outcome.contains("ordering="),
                    "routeOutcomes keeps its existing one-request grammar without per-order rows");
            }
        }
        require(receiptAttempts.equals(p07OutcomeAttempts),
            "P07 receipt and route-outcome attempt sets match exactly");
        if(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(statistics.selectedRoutePolicy)) {
            int selectedReceiptMatches=0;
            for(MediumBoardPhysicalPolicy.OrderingSearchReceipt receipt:statistics.orderingSearchReceipts) {
                if(receipt.attempt==statistics.selectedPlacementAttempt) {
                    selectedReceiptMatches++;
                    require(receipt.selectedOrdering>=0,
                        "the selected P07 owner candidate identifies its winning ordering");
                }
            }
            require(statistics.selectedPlacementAttempt>=0 && selectedReceiptMatches==1,
                "selected P07 owner attempt binds to exactly one receipt with a selected ordering");
        }
        long packingOrders=statistics.packingProbe==null?0L:statistics.packingProbe.orderings;
        long packingExpansions=statistics.packingProbe==null?0L:statistics.packingProbe.expansions;
        require(p07Outcomes==statistics.twoLayerAttempts &&
            successfulReceipts==statistics.twoLayerSuccesses &&
            startedOrderings+packingOrders==statistics.routingOrders &&
            expansions+packingExpansions==statistics.routingExpansions,
            "generator aggregates each stream receipt once and reconciles the separate unchanged packing-probe work");
        String canonical=statistics.toCanonical();
        String key=";orderingSearchReceipts=[";
        int first=canonical.indexOf(key);
        require(first>=0 && first==canonical.lastIndexOf(key),
            "nonempty request receipts add exactly one canonical field");
        Vector<MediumBoardPhysicalPolicy.OrderingSearchReceipt> callerReceipts=
            new Vector<MediumBoardPhysicalPolicy.OrderingSearchReceipt>(
                statistics.orderingSearchReceipts);
        MediumBoardPhysicalPolicy.Statistics copied=new MediumBoardPhysicalPolicy.Statistics(
            statistics.outcome,statistics.selectedRoutePolicy,statistics.placementCandidates,
            statistics.placementRejections,statistics.routeAttempts,statistics.oneFaceAttempts,
            statistics.oneFaceSuccesses,statistics.twoLayerAttempts,statistics.twoLayerSuccesses,
            statistics.selectedPlacementAttempt,statistics.rankedPlacementAttempts,
            statistics.placementScores,statistics.routeOutcomes,statistics.placementEvaluations,
            statistics.routingOrders,statistics.routingExpansions,statistics.selectedPlacementScore,
            statistics.selectedRouteQualityMilli,statistics.packingProbe,
            statistics.routeEligiblePlacementAttempts,statistics.routeEligibilitySkips,callerReceipts);
        String copyCanonical=copied.toCanonical();
        int copiedCount=copied.orderingSearchReceipts.size();
        callerReceipts.clear();
        require(copied.orderingSearchReceipts.size()==copiedCount &&
            copyCanonical.equals(copied.toCanonical()) &&
            copied.orderingSearchReceipts.firstElement()==statistics.orderingSearchReceipts.firstElement(),
            "Statistics defensively copies the caller receipt vector while retaining immutable receipt values");
        MediumBoardPhysicalPolicy.Statistics legacy=new MediumBoardPhysicalPolicy.Statistics(
            "REJECTED",null,0,0,0,0,0,0,0,-1,
            new Vector<Integer>(),new Vector<String>());
        require(legacy.orderingSearchReceipts.isEmpty() &&
            !legacy.toCanonical().contains(";orderingSearchReceipts="),
            "legacy Statistics construction omits the new canonical field when its receipt vector is empty");
    }

    private static void emitStreamFrame(String runId,PreparedStreamCase prepared,String mode) {
        StreamFixture fixture=prepared.fixture;
        System.out.println("P07_ORDERING_STREAM_FRAME {\"protocol\":"+quote(STREAM_PROTOCOL)+
            ",\"runId\":"+quote(runId)+",\"caseId\":"+quote(fixture.caseId)+
            ",\"caseOrdinal\":"+fixture.ordinal+",\"mode\":"+quote(mode)+
            ",\"scope\":"+quote(STREAM_SCOPE)+",\"rootSeed\":"+quote(Long.toString(fixture.rootSeed))+
            ",\"placementSeed\":"+quote(Long.toString(fixture.placementSeed))+
            ",\"routingSeed\":"+quote(Long.toString(fixture.routingSeed))+
            ",\"placementVersion\":"+fixture.placementVersion+
            ",\"layoutVersion\":"+SeededPcbLayoutGenerator.CURRENT_VERSION+
            ",\"dependencyEpoch\":"+quote(GenerationDependencyContext.INTERPRETATION_EPOCH)+
            ",\"topology\":"+quote(fixture.topology)+",\"candidateAttempt\":"+
            fixture.candidateAttempt+",\"packageCount\":"+prepared.board.getComponentIds().size()+
            ",\"padCount\":"+prepared.board.getPadIds().size()+
            ",\"planCanonicalSha256\":"+quote(prepared.planSha256)+
            ",\"logicalBoardSha256\":"+quote(prepared.boardSha256)+
            ",\"inputGeometrySha256\":"+quote(prepared.inputGeometrySha256)+
            ",\"historicalLayout24PrefixInputGeometrySha256\":"+
                quote(prepared.inputGeometryLayout24PrefixReferenceSha256)+
            ",\"inputGeometryLength\":"+prepared.inputGeometry.length()+
            ",\"gridWidth\":"+prepared.grid.width+",\"gridHeight\":"+prepared.grid.height+
            ",\"gridCells\":"+prepared.grid.cells+
            ",\"limits\":{\"totalExpansion\":1000000,\"branchExpansion\":100000,"+
            "\"gridCells\":250000,\"orderings\":3,\"vias\":48},"+
            "\"diagnosticsEnabled\":"+preparedModeEnabled(mode)+
            ",\"layoutQualification\":false,\"adoption\":false}");
    }

    private static boolean preparedModeEnabled(String mode) {
        return "DIAGNOSTICS_ENABLED".equals(mode) || "TOTAL_CAP_AFTER_VALID_YIELD".equals(mode);
    }

    private static void emitStreamBranch(String runId,String caseId,String mode,int event,
            PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
        OptionalBranchMetrics optional=readOptionalBranchMetrics(snapshot);
        System.out.println("P07_ORDERING_STREAM_BRANCH {\"protocol\":"+quote(STREAM_PROTOCOL)+
            ",\"runId\":"+quote(runId)+",\"caseId\":"+quote(caseId)+
            ",\"mode\":"+quote(mode)+",\"eventOrdinal\":"+event+
            ",\"pass\":"+snapshot.pass+",\"netId\":"+quote(snapshot.netId)+
            ",\"startPadId\":"+quote(snapshot.startPadId)+",\"rootPadId\":"+
            quote(snapshot.rootPadId)+",\"prefixViaCount\":"+snapshot.prefixViaCount+
            ",\"reachedPadCount\":"+snapshot.reachedPadCount+
            ",\"gridWidth\":"+snapshot.gridWidth+",\"gridHeight\":"+snapshot.gridHeight+
            ",\"goalsTop\":"+snapshot.goalsTop+",\"goalsBottom\":"+snapshot.goalsBottom+
            ",\"initialManhattanDistance\":"+snapshot.initialManhattanDistance+
            ",\"minimumExploredManhattanDistance\":"+
            snapshot.minimumExploredManhattanDistance+
            ",\"branchExpanded\":"+snapshot.branchExpanded+
            ",\"expandedTop\":"+snapshot.expandedTop+
            ",\"expandedBottom\":"+snapshot.expandedBottom+
            ",\"totalExpanded\":"+snapshot.totalExpanded+
            ",\"frontierCount\":"+snapshot.frontierCount+
            ",\"bestStateCount\":"+snapshot.bestStateCount+
            ",\"terminalReason\":"+quote(snapshot.terminalReason)+
            ",\"lazyBoundStarted\":"+optionalBoolean(optional.supported,optional.started)+
            ",\"lazyBoundComplete\":"+optionalBoolean(optional.supported,optional.complete)+
            ",\"lazyBoundWorkUnits\":"+optionalMetric(optional.supported,optional.workUnits)+
            ",\"lazyBoundOrdinaryProbes\":"+optionalMetric(optional.supported,optional.ordinaryProbes)+
            ",\"lazyBoundViaProbes\":"+optionalMetric(optional.supported,optional.viaProbes)+
            ",\"lazyBoundRefreshes\":"+optionalMetric(optional.supported,optional.refreshes)+
            ",\"lazyBoundObservationChecked\":"+optionalBoolean(optional.supported,optional.observationChecked)+
            ",\"lazyBoundLiveLayoutStable\":"+optionalBoolean(optional.supported,optional.liveLayoutStable)+
            ",\"lazyBoundRouterStateStable\":"+optionalBoolean(optional.supported,optional.routerStateStable)+
            ",\"lazyBoundRouterEffectsStable\":"+optionalBoolean(optional.supported,optional.routerEffectsStable)+
            ",\"layoutQualification\":false,\"adoption\":false}");
    }

    private static void emitStreamOrder(String runId,String caseId,String mode,StreamRoute route) {
        AcceptedRouteEvidence raw=route.rawEvidence;
        FinalizedVariant finalized=route.finalized;
        System.out.println("P07_ORDERING_STREAM_ORDER {\"protocol\":"+quote(STREAM_PROTOCOL)+
            ",\"runId\":"+quote(runId)+",\"caseId\":"+quote(caseId)+
            ",\"mode\":"+quote(mode)+",\"yieldOrdinal\":"+route.ordinal+
            ",\"orderingIndex\":"+route.orderingIndex+
            ",\"reportedOrderings\":"+route.result.orderings+
            ",\"expansionDelta\":"+route.expansionDelta+
            ",\"cumulativeExpansions\":"+route.result.expansions+
            ",\"vias\":"+route.result.vias+",\"links\":"+route.result.links+
            ",\"rawGeometrySha256\":"+quote(route.rawGeometrySha256)+
            ",\"historicalLayout24PrefixRawGeometrySha256\":"+
                quote(route.rawGeometryLayout24PrefixReferenceSha256)+
            ",\"rawTraceGeometrySha256\":"+quote(route.rawTraceGeometrySha256)+
            ",\"traceCount\":"+route.copper.traceCount+
            ",\"traceSegments\":"+route.copper.traceSegments+
            ",\"holeCount\":"+route.copper.holeCount+
            ",\"viaHoleCount\":"+route.copper.viaHoleCount+
            ",\"sourceSegmentCount\":"+raw.sourceSegmentCount+
            ",\"sourceLandCount\":"+raw.sourceLandCount+
            ",\"orderedPadPairs\":"+raw.orderedPadPairs+
            ",\"referenceClearancePairs\":"+raw.referenceClearancePairs+
            ",\"finalizationStatus\":"+quote(finalized.status)+
            ",\"finalizationAccepted\":"+finalized.accepted+
            ",\"finalizationReason\":"+nullableString(finalized.reason)+
            ",\"finalGeometrySha256\":"+nullableString(finalized.geometrySha256)+
            ",\"historicalLayout24PrefixFinalGeometrySha256\":"+
                nullableString(finalized.layout24PrefixReferenceSha256)+
            ",\"finalArea\":"+finalized.area+
            ",\"finalQualityMilli\":"+finalized.qualityMilli+
            ",\"ownerWinner\":"+finalized.ownerWinner+
            ",\"layoutQualification\":false,\"adoption\":false}");
    }

    private static void emitStreamCase(StreamRun run,String mode) {
        StringBuilder out=new StringBuilder("{");
        field(out,"protocol",quote(STREAM_PROTOCOL));
        field(out,"runId",quote(run.runId));
        field(out,"caseId",quote(run.prepared.fixture.caseId));
        field(out,"mode",quote(mode));
        field(out,"scope",quote(STREAM_SCOPE));
        field(out,"inputGeometrySha256",quote(run.prepared.inputGeometrySha256));
        field(out,"historicalLayout24PrefixInputGeometrySha256",
            quote(run.prepared.inputGeometryLayout24PrefixReferenceSha256));
        field(out,"diagnosticsEnabled",Boolean.toString(run.diagnosticsEnabled));
        field(out,"expansions",Integer.toString(run.summary.expansions));
        field(out,"orderings",Integer.toString(run.summary.orderings));
        field(out,"completedOrderings",Integer.toString(run.summary.completedOrderings));
        field(out,"yieldedOrderings",Integer.toString(run.summary.yieldedOrderings));
        field(out,"guidanceStarted",Boolean.toString(run.summary.guidanceStarted));
        field(out,"comparisonComplete",Boolean.toString(run.summary.comparisonComplete));
        field(out,"termination",quote(run.summary.termination));
        field(out,"branchEventCount",Integer.toString(run.branches==null?0:run.branches.events));
        field(out,"branchExpandedSum",Long.toString(run.branches==null?0L:run.branches.branchExpandedSum));
        field(out,"boundedAdvances",Integer.toString(run.boundedAdvances));
        field(out,"elapsedNanos",Long.toString(run.elapsedNanos));
        field(out,"ownerWinner",run.owner==null?"null":run.owner.winnerJson());
        field(out,"layoutQualification","false");
        field(out,"adoption","false");
        System.out.println("P07_ORDERING_STREAM_CASE "+finishObject(out));
    }

    private static void emitGeneratorRecord(String runId,Rb56Plan plan,
            MediumBoardPhysicalPolicy.Statistics statistics,int workUnits,int observerCalls,
            MediumBoardPhysicalPolicy.Result result) {
        StringBuilder receipts=new StringBuilder("[");
        for(int index=0;index<statistics.orderingSearchReceipts.size();index++) {
            if(index>0) receipts.append(',');
            receipts.append(quote(statistics.orderingSearchReceipts.get(index).toCanonical()));
        }
        receipts.append(']');
        StringBuilder outcomes=new StringBuilder("[");
        for(int index=0;index<statistics.routeOutcomes.size();index++) {
            if(index>0) outcomes.append(',');
            outcomes.append(quote(statistics.routeOutcomes.get(index)));
        }
        outcomes.append(']');
        System.out.println("P07_ORDERING_STREAM_GENERATOR {\"protocol\":"+quote(STREAM_PROTOCOL)+
            ",\"runId\":"+quote(runId)+",\"caseId\":\"SAVED7_ROOT42_ACTUAL_GENERATOR\""+
            ",\"scope\":"+quote(STREAM_SCOPE)+",\"layoutSeed\":"+
            quote(Long.toString(plan.layoutSeed))+",\"routingSeed\":"+
            quote(Long.toString(plan.routingSeed))+",\"resultAccepted\":"+result.accepted()+
            ",\"resultFailure\":"+nullableString(result.getFailure())+
            ",\"workUnits\":"+workUnits+",\"observerCalls\":"+observerCalls+
            ",\"twoLayerAttempts\":"+statistics.twoLayerAttempts+
            ",\"twoLayerSuccesses\":"+statistics.twoLayerSuccesses+
            ",\"routeAttempts\":"+statistics.routeAttempts+
            ",\"routeOutcomeCount\":"+statistics.routeOutcomes.size()+
            ",\"routingOrders\":"+statistics.routingOrders+
            ",\"routingExpansions\":"+statistics.routingExpansions+
            ",\"orderingReceipts\":"+receipts+",\"routeOutcomes\":"+outcomes+
            ",\"layoutSha256\":"+(result.getLayout()==null?"null":
                quote(PlayerSessionFingerprint.of(result.getLayout().geometryFingerprint())))+
            ",\"layoutQualification\":false,\"adoption\":false}");
    }

    private static void expectIllegalState(Runnable action,String message) {
        try { action.run(); }
        catch(IllegalStateException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    private static void expectIllegalArgument(Runnable action,String message) {
        try { action.run(); }
        catch(IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    private static Object observedField(Object target,String name) {
        if(target==null) throw new IllegalStateException("Missing P07 observation target: "+name);
        try {
            Field field=target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch(ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not read P07 observation field: "+name,failure);
        }
    }
    private static int observedInt(Object target,String name) {
        if(target==null) throw new IllegalStateException("Missing P07 observation target: "+name);
        try {
            Field field=target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(target);
        } catch(ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not read P07 integer field: "+name,failure);
        }
    }
    private static boolean observedBoolean(Object target,String name) {
        if(target==null) throw new IllegalStateException("Missing P07 observation target: "+name);
        try {
            Field field=target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(target);
        } catch(ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not read P07 boolean field: "+name,failure);
        }
    }

    private static void emitStreamCanary(String runId,String caseId,boolean pass,String detail) {

        System.out.println("P07_ORDERING_STREAM_CANARY {\"protocol\":"+quote(STREAM_PROTOCOL)+
            ",\"runId\":"+quote(runId)+",\"caseId\":"+quote(caseId)+
            ",\"status\":"+quote(pass?"PASS":"FAIL")+",\"detail\":"+detail+
            ",\"scope\":"+quote(STREAM_SCOPE)+",\"layoutQualification\":false,\"adoption\":false}");
    }

    private static Field sessionField(Object session,String name) {
        try {
            Field field=session.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Missing P07 session observation field: "+name,failure);
        }
    }
    private static Method findConsiderMethod(Class<?> owner) throws NoSuchMethodException {
        Method match=null;
        for (Method method : owner.getDeclaredMethods()) {
            Class<?>[] parameters=method.getParameterTypes();
            if ("considerMediumLayout".equals(method.getName()) && parameters.length==4 &&
                    parameters[0]==SeededPcbLayoutGenerator.Session.class &&
                    parameters[1]==MediumBoardPhysicalPolicy.PlacementCandidate.class &&
                    parameters[3]==Boolean.TYPE) {
                if (match!=null) throw new NoSuchMethodException("Ambiguous considerMediumLayout owner method");
                match=method;
            }
        }
        if (match==null) throw new NoSuchMethodException("Missing existing considerMediumLayout owner method");
        return match;
    }
    private static boolean isExpectedFinalizationRejection(Throwable cause) {
        return (SeededPcbLayoutGenerator.class.getName()+"$CandidateRejected").equals(
                cause.getClass().getName()) || cause instanceof PcbBoardLayout.RouteQualityRejectedException;
    }
    private static RuntimeException rethrowUnexpected(String message,Throwable cause) {
        if (cause instanceof RuntimeException) return (RuntimeException)cause;
        if (cause instanceof Error) throw (Error)cause;
        return new IllegalStateException(message,cause);
    }
    private static String logicalBoardFingerprint(TroubleshootBoard board) {
        StringBuilder canonical = new StringBuilder();
        TreeSet<String> componentIds = new TreeSet<String>(board.getComponentIds());
        for (String id : componentIds) {
            BoardComponent component = board.getComponent(id);
            if (component == null) throw new IllegalStateException("Logical board lost a declared component");
            PhysicalPackage physical = component.getPhysicalPackage();
            canonical.append("C:").append(id).append('|').append(component.getType()).append('|')
                .append(physical == null ? "null" : physical.getId()).append(';');
        }
        TreeSet<String> padIds = new TreeSet<String>(board.getPadIds());
        for (String id : padIds) {
            BoardPad pad = board.getPad(id);
            if (pad == null) throw new IllegalStateException("Logical board lost a declared pad");
            canonical.append("P:").append(id).append('|').append(pad.getComponentId()).append('|')
                .append(pad.getTerminalId()).append('|').append(pad.getNetId()).append(';');
        }
        return canonical.toString();
    }
    private static void validateRawInput(Rb56Plan plan, TroubleshootBoard board,
            PcbPlacementPlanner.Plan plannerPlan, PcbBoardLayout placement,int candidateAttempt) {
        board.validate();
        placement.validateAgainst(board);
        PcbFactoryLinkPolicy.validateLayout(placement);
        require(plannerPlan.candidate == candidateAttempt,
            "planner receipt candidate matches the selected attempt");
        require(plan.physicalPackageCount() == plan.parts().size() &&
            plannerPlan.footprints.size() == plan.physicalPackageCount(),
            "raw planner footprint census matches plan-owned package inventory");
        TreeSet<String> declaredComponents = new TreeSet<String>(board.getComponentIds());
        TreeSet<String> placedComponents = new TreeSet<String>();
        for (PcbComponentPlacement placed : placement.getComponents()) {
            require(placed != null && placedComponents.add(placed.getComponentId()),
                "raw placement contains unique non-null component identities");
            BoardComponent declared = board.getComponent(placed.getComponentId());
            require(declared != null && declared.getPhysicalPackage() == placed.getPhysicalPackage(),
                "raw placement retains declared physical-package identity");
        }
        require(placedComponents.equals(declaredComponents),
            "raw placement is an exact board-component identity bijection");
        TreeSet<String> declaredPads = new TreeSet<String>(board.getPadIds());
        TreeSet<String> placedPads = new TreeSet<String>();
        for (PcbPadPlacement placed : placement.getPads()) {
            require(placed != null && placedPads.add(placed.getPadId()),
                "raw placement contains unique non-null pad identities");
            require(board.getPad(placed.getPadId()) != null,
                "raw pad placement maps to a declared logical terminal");
        }
        require(placedPads.equals(declaredPads),
            "raw placement is an exact board-pad identity bijection");
        int expectedPadCount = 0;
        TreeSet<String> planComponents = new TreeSet<String>();
        TreeSet<String> planPads = new TreeSet<String>();
        for (Rb56Plan.Part part : plan.parts()) {
            require(planComponents.add(part.id), "plan package ID is unique");
            BoardComponent declared = board.getComponent(part.id);
            require(declared != null && declared.getPhysicalPackage() == part.physicalPackage,
                "board retains the plan-owned physical package");
            PcbComponentPlacement placed = placement.getComponent(part.id);
            require(placed != null && placed.getPhysicalPackage() == part.physicalPackage,
                "raw planner candidate retains package identity");
            String[] terminals = part.terminalIds();
            String[] nets = part.netIds();
            require(terminals.length == nets.length &&
                terminals.length == part.physicalPackage.getTerminalCount(),
                "plan terminal, net and package declarations align");
            expectedPadCount += terminals.length;
            for (int index = 0; index < terminals.length; index++) {
                String padId = part.id + "." + terminals[index];
                require(planPads.add(padId), "plan pad identity is unique");
                BoardPad logical = board.getPad(padId);
                require(logical != null && logical.getComponentId().equals(part.id) &&
                    logical.getTerminalId().equals(terminals[index]) &&
                    logical.getNetId().equals(nets[index]) && placement.getPad(padId) != null,
                    "plan terminal preserves exact logical component, terminal and net identity");
            }
        }
        require(planComponents.equals(declaredComponents) && planPads.equals(declaredPads) &&
            expectedPadCount == declaredPads.size(),
            "plan package and pad census matches the exact logical board inventory");
        require(placement.getTraces().isEmpty() && placement.getHoles().isEmpty(),
            "raw candidate starts without published traces or vias");
    }
    /** Reuses independent physical subsets for a real authored100 diagnostic result. */
    static void verifyAuthored100PhysicalSubsets(TroubleshootBoard board, PcbBoardLayout routed) {
        Rb100PhysicalReference.verifyLiteralP03(board,routed);
        validateAcceptedOutput(board,routed);
    }
    private static AcceptedRouteEvidence validateAcceptedOutput(TroubleshootBoard board,
            PcbBoardLayout routed) {
        routed.validateRoutingGeometry(board);
        new PcbTwoLayerRules(board, routed).validate(routed);
        PcbTraceClearance.Statistics clearance = PcbTraceClearance.validate(routed.getTraces());
        long referenceClearancePairs = P08ScalabilityContractTest.referenceClearance(routed.getTraces());
        PcbConductorGraph reference = P08ReferenceConductorBuilder.capture(board, routed);
        PcbConductorGraph.Snapshot partition = reference.pristine();
        partition.requirePristineNetConnectivity(board);
        long orderedPadPairs = verifyOrderedPadPairs(board, partition);
        PcbConductorGraph production = routed.captureConductorGraph(board);
        require(reference.toCanonical().equals(production.toCanonical()),
            "accepted raw P07 result matches independent P08 conductor graph");
        P07LayeredRasterOracle.Metrics raster = P07LayeredRasterOracle.verify(routed, board);
        require(raster.getOrderedPadPairs() == (long)board.getPadIds().size() *
                board.getPadIds().size(),
            "accepted raw P07 result raster witness checks every ordered pad pair");
        return new AcceptedRouteEvidence(clearance, reference, orderedPadPairs, raster,
            referenceClearancePairs,P08ReferenceConductorBuilder.sourceSegmentCount,
            P08ReferenceConductorBuilder.sourceLandCount);
    }
    private static long verifyOrderedPadPairs(TroubleshootBoard board,
            PcbConductorGraph.Snapshot partition) {
        TreeSet<String> pads = new TreeSet<String>(board.getPadIds());
        long pairs = 0L;
        for (String firstId : pads) {
            BoardPad first = board.getPad(firstId);
            for (String secondId : pads) {
                BoardPad second = board.getPad(secondId);
                boolean sameNet = first.getNetId().equals(second.getNetId());
                require(partition.padsConnected(firstId, secondId) == sameNet,
                    "accepted raw P07 P08 partition matches declared net equality");
                pairs++;
            }
        }
        require(pairs == (long)pads.size() * pads.size(),
            "accepted raw P07 P08 partition includes all ordered pad pairs");
        return pairs;
    }
    private static final class AcceptedRouteEvidence {
        final PcbTraceClearance.Statistics clearance;
        final PcbConductorGraph graph;
        final long orderedPadPairs;
        final long referenceClearancePairs;
        final long sourceSegmentCount,sourceLandCount;
        final P07LayeredRasterOracle.Metrics raster;
        private AcceptedRouteEvidence(PcbTraceClearance.Statistics clearance,
                PcbConductorGraph graph, long orderedPadPairs,
                P07LayeredRasterOracle.Metrics raster, long referenceClearancePairs,
                long sourceSegmentCount,long sourceLandCount) {
            this.referenceClearancePairs = referenceClearancePairs;
            this.sourceSegmentCount=sourceSegmentCount; this.sourceLandCount=sourceLandCount;
            this.clearance = clearance;
            this.graph = graph;
            this.orderedPadPairs = orderedPadPairs;
            this.raster = raster;
        }
        static AcceptedRouteEvidence empty() {
            return new AcceptedRouteEvidence(null, null, 0L, null, 0L,0L,0L);
        }
    }
    private static final class RawCopperSummary {
        final int traceCount,traceSegments,tracePoints,holeCount,viaHoleCount;
        final Integer minTraceX,minTraceY,maxTraceX,maxTraceY,minHoleX,minHoleY,maxHoleX,maxHoleY;
        final TreeSet<String> traceSourceIds,holeIds;
        RawCopperSummary(int traceCount,int traceSegments,int tracePoints,int holeCount,int viaHoleCount,
                Integer minTraceX,Integer minTraceY,Integer maxTraceX,Integer maxTraceY,
                Integer minHoleX,Integer minHoleY,Integer maxHoleX,Integer maxHoleY,
                TreeSet<String> traceSourceIds,TreeSet<String> holeIds) {
            this.traceCount=traceCount; this.traceSegments=traceSegments; this.tracePoints=tracePoints;
            this.holeCount=holeCount; this.viaHoleCount=viaHoleCount;
            this.minTraceX=minTraceX; this.minTraceY=minTraceY; this.maxTraceX=maxTraceX; this.maxTraceY=maxTraceY;
            this.minHoleX=minHoleX; this.minHoleY=minHoleY; this.maxHoleX=maxHoleX; this.maxHoleY=maxHoleY;
            this.traceSourceIds=traceSourceIds; this.holeIds=holeIds;
        }
        static RawCopperSummary empty() {
            return new RawCopperSummary(0,0,0,0,0,null,null,null,null,null,null,null,null,
                new TreeSet<String>(),new TreeSet<String>());
        }
    }
    private static RawCopperSummary rawCopperSummary(PcbBoardLayout layout) {
        if (layout == null) return RawCopperSummary.empty();
        TreeSet<String> traceIds=new TreeSet<String>(),holeIds=new TreeSet<String>();
        int traceSegments=0,tracePoints=0,holeCount=0,viaHoleCount=0;
        int minTraceX=Integer.MAX_VALUE,minTraceY=Integer.MAX_VALUE,maxTraceX=Integer.MIN_VALUE,maxTraceY=Integer.MIN_VALUE;
        int minHoleX=Integer.MAX_VALUE,minHoleY=Integer.MAX_VALUE,maxHoleX=Integer.MIN_VALUE,maxHoleY=Integer.MIN_VALUE;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            String sourceId=trace.getSourceId();
            require(sourceId!=null && traceIds.add(sourceId),
                "accepted raw P07 traces retain unique physical source IDs");
            int[] xs=trace.getXPoints(),ys=trace.getYPoints();
            require(xs.length==ys.length && xs.length>=2,
                "accepted raw P07 trace coordinates remain paired polylines");
            tracePoints+=xs.length; traceSegments+=xs.length-1;
            for (int point=0;point<xs.length;point++) {
                minTraceX=Math.min(minTraceX,xs[point]); minTraceY=Math.min(minTraceY,ys[point]);
                maxTraceX=Math.max(maxTraceX,xs[point]); maxTraceY=Math.max(maxTraceY,ys[point]);
            }
        }
        for (PcbBoardHole hole : layout.getHoles()) {
            require(hole!=null && holeIds.add(hole.id),
                "accepted raw P07 holes retain unique physical identities");
            holeCount++; if (hole.kind==PcbBoardHole.Kind.VIA) viaHoleCount++;
            minHoleX=Math.min(minHoleX,hole.x); minHoleY=Math.min(minHoleY,hole.y);
            maxHoleX=Math.max(maxHoleX,hole.x); maxHoleY=Math.max(maxHoleY,hole.y);
        }
        return new RawCopperSummary(traceIds.size(),traceSegments,tracePoints,holeCount,viaHoleCount,
            tracePoints==0?null:Integer.valueOf(minTraceX),tracePoints==0?null:Integer.valueOf(minTraceY),
            tracePoints==0?null:Integer.valueOf(maxTraceX),tracePoints==0?null:Integer.valueOf(maxTraceY),
            holeCount==0?null:Integer.valueOf(minHoleX),holeCount==0?null:Integer.valueOf(minHoleY),
            holeCount==0?null:Integer.valueOf(maxHoleX),holeCount==0?null:Integer.valueOf(maxHoleY),
            traceIds,holeIds);
    }
    private static String rectangle(Rectangle rectangle) {
        return "{\"x\":" + rectangle.x + ",\"y\":" + rectangle.y +
            ",\"width\":" + rectangle.width + ",\"height\":" + rectangle.height + "}";
    }
    private static String nullableString(String value) {
        return value == null ? "null" : quote(value);
    }
    private static void field(StringBuilder out, String name, String value) {
        out.append(quote(name)).append(':').append(value).append(',');
    }
    private static String finishObject(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) == ',')
            out.setLength(out.length() - 1);
        out.append('}');
        return out.toString();
    }
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\\' || ch == '\"') out.append('\\').append(ch);
            else if (ch < 0x20) {
                final char[] hex = "0123456789abcdef".toCharArray();
                out.append("\\u00").append(hex[(ch >>> 4) & 15]).append(hex[ch & 15]);
            } else out.append(ch);
        }
        return out.append('\"').toString();
    }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
