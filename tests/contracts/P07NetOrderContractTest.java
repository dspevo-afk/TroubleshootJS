package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/** Independent literal-pad contract for private pass-one P07 net ordering. */
public final class P07NetOrderContractTest {
    private static int checks;
    private static final SeededPcbLayoutGenerator.AttemptObserver NOOP_OBSERVER =
        new SeededPcbLayoutGenerator.AttemptObserver() { public void check(int attempt) { } };
    private static final PcbLayerRoutingPrototype.Diagnostics NOOP_DIAGNOSTICS =
        new PcbLayerRoutingPrototype.Diagnostics() {
            public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) { }
        };

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        Fixture(TroubleshootBoard board,PcbBoardLayout layout) {
            this.board=board; this.layout=layout;
        }
    }
    private static final class OracleMetric {
        final String netId;
        final boolean paired;
        final long minimumDistance;
        final String witnessFirst,witnessSecond;
        OracleMetric(String netId,boolean paired,long minimumDistance,
                String witnessFirst,String witnessSecond) {
            this.netId=netId; this.paired=paired; this.minimumDistance=minimumDistance;
            this.witnessFirst=witnessFirst; this.witnessSecond=witnessSecond;
        }
    }
    private static final class OrderCapture {
        final Vector<String> order;
        final int chargedCalls;
        final boolean allMetricsChecked;
        OrderCapture(Vector<String> order,int chargedCalls,boolean allMetricsChecked) {
            this.order=order; this.chargedCalls=chargedCalls;
            this.allMetricsChecked=allMetricsChecked;
        }
    }
    private static final class ArmedObserver implements SeededPcbLayoutGenerator.AttemptObserver {
        boolean armed;
        RuntimeException failure;
        public void check(int attempt) { if(armed && failure!=null) throw failure; }
    }
    private static final class CountingDiagnostics implements PcbLayerRoutingPrototype.Diagnostics {
        int terminalBranches;
        public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
            terminalBranches++;
        }
    }
    private static final class NetOrderHookRecorder implements PcbLayerRoutingPrototype.NetOrderDiagnostics {
        final Fixture fixture;
        final String[] expected;
        final String pristineGeometry;
        final Throwable failure;
        final PcbLayerRoutingPrototype.Session[] owner={null};
        int callbacks,terminalBranches;
        Vector<String> observedOrder;
        NetOrderHookRecorder(Fixture fixture,String[] expected,Throwable failure) {
            this.fixture=fixture; this.expected=expected; this.failure=failure;
            pristineGeometry=fixture.layout.geometryFingerprint();
        }
        public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
            terminalBranches++;
        }
        public void onNetOrderReady(int pass) {
            callbacks++;
            require(owner[0]!=null && pass==1,
                "new net-order callback identifies the current pass-one owner");
            Object search=fieldValue(owner[0],"search");
            Vector<?> nets=(Vector<?>)fieldValue(search,"nets");
            requireOrder(nets,expected,
                "new callback observes the complete post-promotion net vector");
            require(fieldValue(search,"netOrderPreparation")==null &&
                fieldValue(search,"currentNet")==null && intField(search,"netIndex")==0 &&
                fieldValue(search,"branch")==null && fieldValue(search,"publisher")==null,
                "callback runs after atomic vector install and before any net/branch advances");
            PcbBoardLayout ownedLayout=(PcbBoardLayout)fieldValue(search,"layout");
            require(ownedLayout.geometryFingerprint().equals(pristineGeometry) &&
                ownedLayout.getTraces().isEmpty() && ownedLayout.getHoles().isEmpty() &&
                fixture.layout.geometryFingerprint().equals(pristineGeometry) && terminalBranches==0,
                "callback observes pristine owned and supplied placement without terminal-branch activity");
            observedOrder=copyStringVector(nets);
            if(failure instanceof RuntimeException) throw (RuntimeException)failure;
            if(failure instanceof Error) throw (Error)failure;
        }
    }

    private P07NetOrderContractTest() { }

    public static void main(String[] args) throws Exception {
        if(args!=null && args.length!=0)
            throw new IllegalArgumentException("The P07 net-order contract takes no arguments");
        Fixture fixture=fixture(PcbTraceWidthProfile.ROLE_WIDTHS);
        Vector<OracleMetric> oracle=oracleMetrics(fixture.board,fixture.layout);
        verifyLiteralOracle(fixture,oracle);

        String[] treatmentExpected={"A_LONG_ROOT","A_TIE","B_TIE","D_NEXT","WIDE",
            "NC_A","NC_B","NC_C","NC_D"};
        String[] canonicalExpected={"D_NEXT","A_LONG_ROOT","A_TIE","B_TIE","WIDE",
            "NC_A","NC_B","NC_C","NC_D"};
        OrderCapture treatment=captureOrder(fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,1,null,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,NOOP_OBSERVER,true);
        requireOrder(treatment.order,treatmentExpected,
            "pass-one treatment follows every-pair minima, net-ID ties, and lexical no-pair tail");
        require(treatment.allMetricsChecked,
            "the complete prepared metric census matches the independent placed-pad oracle");
        require(treatment.chargedCalls>=countUnorderedPadPairs(fixture.board)+fixture.board.getNetIds().size(),
            "pair metrics and materialized net IDs consume separate bounded owner work");

        OrderCapture implicitCanonical=captureDefaultOrder(fixture,1);
        OrderCapture explicitCanonical=captureOrder(fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy.CANONICAL,1,null,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,NOOP_OBSERVER,false);
        requireOrder(implicitCanonical.order,canonicalExpected,
            "existing ordering-stream overload preserves canonical descending-pad-count order");
        requireOrder(explicitCanonical.order,canonicalExpected,
            "explicit canonical net strategy preserves the full original pass-one vector");
        require(implicitCanonical.order.equals(explicitCanonical.order),
            "normal and explicit-canonical P07 entries have identical actual pass-one vectors");

        String[] promotedExpected={"D_NEXT","A_LONG_ROOT","A_TIE","B_TIE","WIDE",
            "NC_A","NC_B","NC_C","NC_D"};
        OrderCapture promoted=captureOrder(fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,1,"D_NEXT",
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,NOOP_OBSERVER,true);
        requireOrder(promoted.order,promotedExpected,
            "existing failed-net promotion is applied after the complete treatment sort");

        OrderCapture passZero=captureOrder(fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,0,null,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,NOOP_OBSERVER,false);
        requireOrder(passZero.order,new String[]{"A_LONG_ROOT","A_TIE","B_TIE","D_NEXT",
            "NC_A","NC_B","NC_C","NC_D","WIDE"},
            "treatment leaves ROLE_WIDTHS pass zero in its existing role/ID order");
        OrderCapture passTwo=captureOrder(fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,2,null,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,NOOP_OBSERVER,false);
        requireOrder(passTwo.order,new String[]{"WIDE","NC_D","NC_C","NC_B","NC_A",
            "D_NEXT","B_TIE","A_TIE","A_LONG_ROOT"},
            "treatment leaves ROLE_WIDTHS pass two in its existing reversed role/ID order");

        Fixture legacyFixture=fixture(PcbTraceWidthProfile.UNIFORM);
        OrderCapture legacyPassOne=captureOrder(legacyFixture,
            PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,1,null,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,NOOP_OBSERVER,false);
        requireOrder(legacyPassOne.order,canonicalExpected,
            "minimum-pair strategy is guarded off for the legacy uniform profile");

        verifyRequestConfiguration(fixture);
        verifyNetOrderReadyHook(fixture);
        verifyWidePairMetric(fixture);
        verifyDetachedCopyAndCancellation(fixture);
        verifyPreparationErrorKeepsStrategy(fixture);

        System.out.println("PASS: P07 net-order contracts assertions="+checks+
            " fixture=NINE_NET_LITERAL_PAD_CENSUS layoutQualification=false adoption=false");
    }

    private static void verifyLiteralOracle(Fixture fixture,Vector<OracleMetric> oracle) {
        String[] expectedIds={"A_LONG_ROOT","A_TIE","B_TIE","D_NEXT","NC_A","NC_B",
            "NC_C","NC_D","WIDE"};
        require(oracle.size()==expectedIds.length,
            "independent oracle covers every fixture net exactly once");
        for(int i=0;i<expectedIds.length;i++)
            require(expectedIds[i].equals(oracle.get(i).netId),
                "independent oracle net census is lexical at index "+i);
        OracleMetric longRoot=find(oracle,"A_LONG_ROOT");
        OracleMetric tiedA=find(oracle,"A_TIE"),tiedB=find(oracle,"B_TIE");
        OracleMetric next=find(oracle,"D_NEXT");
        require(longRoot.paired && longRoot.minimumDistance==40L &&
            "B.1".equals(longRoot.witnessFirst) && "C.1".equals(longRoot.witnessSecond),
            "multiple-pad minimum is B.1/C.1 at forty despite the distant lexical root A.1");
        require(rootDistance(fixture,"A.1","B.1")==650L && next.minimumDistance==90L &&
            longRoot.minimumDistance<next.minimumDistance,
            "root-distance ordering would put D_NEXT first, contrary to the independent pair minimum");
        require(tiedA.minimumDistance==40L && "E.1".equals(tiedA.witnessFirst) &&
            "F.1".equals(tiedA.witnessSecond) && tiedB.minimumDistance==40L &&
            "H.1".equals(tiedB.witnessFirst) && "I.1".equals(tiedB.witnessSecond),
            "equal metric and equal pair-distance witnesses resolve lexically in the independent oracle");
        for(String id:new String[]{"NC_A","NC_B","NC_C","NC_D"}) {
            OracleMetric singleton=find(oracle,id);
            require(!singleton.paired && singleton.minimumDistance==Long.MAX_VALUE &&
                singleton.witnessFirst==null && singleton.witnessSecond==null,
                id+" is retained as a no-pair singleton, not assigned synthetic zero distance");
        }
        requireOrder(expectedTreatment(oracle,null),new String[]{"A_LONG_ROOT","A_TIE","B_TIE",
            "D_NEXT","WIDE","NC_A","NC_B","NC_C","NC_D"},
            "literal treatment vector is frozen independently of production sorting");
    }

    private static void verifyRequestConfiguration(Fixture fixture) throws Exception {
        PcbLayerRoutingPrototype.BranchOrderStrategy branch=
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST;
        PcbLayerRoutingPrototype.NetOrderStrategy canonical=
            PcbLayerRoutingPrototype.NetOrderStrategy.CANONICAL;
        PcbLayerRoutingPrototype.NetOrderStrategy treatment=
            PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR;
        SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration oldDefault=
            new SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration(NOOP_DIAGNOSTICS);
        SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration branchDefault=
            new SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration(branch,NOOP_DIAGNOSTICS);
        SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration explicitCanonical=
            new SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration(branch,canonical,NOOP_DIAGNOSTICS);
        SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration explicit=
            new SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration(branch,treatment,NOOP_DIAGNOSTICS);
        require(oldDefault.netOrderStrategy==canonical && branchDefault.netOrderStrategy==canonical &&
            explicitCanonical.netOrderStrategy==canonical,
            "existing and explicit-canonical request-boundary configurations keep canonical net ordering");
        require(explicit.branchOrderStrategy==branch && explicit.netOrderStrategy==treatment,
            "request-boundary treatment remains separate from branch ordering");
        require(Modifier.isFinal(oldDefault.getClass().getDeclaredField("netOrderStrategy").getModifiers()),
            "request-boundary net-order strategy remains immutable");

        Fixture mediumFixture=fixture(PcbTraceWidthProfile.ROLE_WIDTHS,true);
        SeededPcbLayoutGenerator generator=new SeededPcbLayoutGenerator();
        SeededPcbLayoutGenerator.Session ordinary=generator.begin(mediumFixture.board,0L,0L);
        SeededPcbLayoutGenerator.Session configured=generator.beginWithP07RequestBoundary(
            mediumFixture.board,0L,0L,explicit);
        require(netStrategyOf(ordinary)==canonical,
            "ordinary generator entrypoint retains the canonical net-order default");
        require(netStrategyOf(configured)==treatment,
            "request-boundary configuration reaches its retained generator Session");
        Field sessionStrategy=configured.getClass().getDeclaredField("netOrderStrategy");
        require(Modifier.isFinal(sessionStrategy.getModifiers()),
            "generator Session stores net-order strategy as immutable request state");
    }

    private static void verifyNetOrderReadyHook(Fixture fixture) throws Exception {
        String[] canonicalPromoted={"NC_C","D_NEXT","A_LONG_ROOT","A_TIE","B_TIE","WIDE",
            "NC_A","NC_B","NC_D"};
        String[] treatmentPromoted={"NC_C","A_LONG_ROOT","A_TIE","B_TIE","D_NEXT","WIDE",
            "NC_A","NC_B","NC_D"};
        NetOrderHookRecorder canonical=verifyHookReady(fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy.CANONICAL,canonicalPromoted);
        NetOrderHookRecorder treatment=verifyHookReady(fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,treatmentPromoted);
        require(canonical.callbacks==1 && treatment.callbacks==1 &&
            canonical.terminalBranches==0 && treatment.terminalBranches==0,
            "canonical and treatment paths each emit one pre-branch pass-one boundary");

        verifyHookGuard(fixture,PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,
            PcbTraceWidthProfile.ROLE_WIDTHS,0,
            new String[]{"A_LONG_ROOT","A_TIE","B_TIE","D_NEXT","NC_A","NC_B","NC_C","NC_D","WIDE"});
        verifyHookGuard(fixture,PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,
            PcbTraceWidthProfile.ROLE_WIDTHS,2,
            new String[]{"WIDE","NC_D","NC_C","NC_B","NC_A","D_NEXT","B_TIE","A_TIE","A_LONG_ROOT"});
        Fixture legacy=fixture(PcbTraceWidthProfile.UNIFORM);
        verifyHookGuard(legacy,PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,
            PcbTraceWidthProfile.UNIFORM,1,
            new String[]{"D_NEXT","A_LONG_ROOT","A_TIE","B_TIE","WIDE","NC_A","NC_B","NC_C","NC_D"});

        CountingDiagnostics ordinary=new CountingDiagnostics();
        PcbLayerRoutingPrototype.Session ordinarySession=newSession(fixture,fixture.layout,
            PcbLayerRoutingPrototype.NetOrderStrategy.CANONICAL,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,ordinary,NOOP_OBSERVER);
        OrderCapture plain= captureExistingOrder(ordinarySession,fixture,1,"NC_C",false);
        PcbLayerRoutingPrototype.Diagnostics plainDiagnostics=ordinary;
        requireOrder(plain.order,canonicalPromoted,
            "ordinary plain Diagnostics retains canonical order and post-sort promotion");
        require(ordinary.terminalBranches==0 &&
            !(plainDiagnostics instanceof PcbLayerRoutingPrototype.NetOrderDiagnostics) &&
            !(NOOP_DIAGNOSTICS instanceof PcbLayerRoutingPrototype.NetOrderDiagnostics),
            "plain and no-op Diagnostics do not receive the new net-order callback");
        PcbLayerRoutingPrototype.Session noopSession=newSession(fixture,fixture.layout,
            PcbLayerRoutingPrototype.NetOrderStrategy.CANONICAL,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,
            NOOP_DIAGNOSTICS,NOOP_OBSERVER);
        requireOrder(captureExistingOrder(noopSession,fixture,1,"NC_C",false).order,canonicalPromoted,
            "existing no-op Diagnostics path retains canonical pass-one order and promotion");

        verifyHookFailure(fixture,PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,false,
            treatmentPromoted);
        verifyHookFailure(fixture,PcbLayerRoutingPrototype.NetOrderStrategy.CANONICAL,true,
            canonicalPromoted);
    }

    private static NetOrderHookRecorder verifyHookReady(Fixture fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy strategy,String[] expected) throws Exception {
        NetOrderHookRecorder recorder=new NetOrderHookRecorder(fixture,expected,null);
        PcbLayerRoutingPrototype.Session session=newSession(fixture,fixture.layout,strategy,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,recorder,NOOP_OBSERVER);
        recorder.owner[0]=session;
        setIntField(session,"pass",1);
        setField(session,"previousFailedNet","NC_C");
        int calls=0;
        while(recorder.callbacks==0 && calls<10000) {
            PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(1);
            require(session.lastWork()==1,
                "successful hook adds no extra P07 work quantum beyond the containing slice");
            calls++;
            Object search=fieldValue(session,"search");
            if(recorder.callbacks==0 && search!=null && fieldValue(search,"nets")!=null)
                throw new AssertionError("complete net vector installed without the expected callback");
            if(recorder.callbacks==0 && progress!=PcbLayerRoutingPrototype.OrderingProgress.WORKING)
                throw new AssertionError("P07 stream ended without its pass-one net-order callback");
        }
        require(calls<10000 && recorder.callbacks==1 && recorder.observedOrder!=null,
            "net-order callback is raised exactly once at the complete installation boundary");
        return recorder;
    }

    private static void verifyHookGuard(Fixture fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy strategy,PcbTraceWidthProfile profile,
            int pass,String[] expected) throws Exception {
        if(fixture.board.getPlacementConstraints().getTraceWidthProfile()!=profile)
            throw new AssertionError("hook guard fixture profile mismatch");
        NetOrderHookRecorder recorder=new NetOrderHookRecorder(fixture,expected,null);
        PcbLayerRoutingPrototype.Session session=newSession(fixture,fixture.layout,strategy,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,recorder,NOOP_OBSERVER);
        recorder.owner[0]=session;
        OrderCapture actual=captureExistingOrder(session,fixture,pass,null,false);
        requireOrder(actual.order,expected,"guarded hook mode preserves its literal existing order");
        require(recorder.callbacks==0 && recorder.terminalBranches==0,
            "net-order callback is absent outside ROLE_WIDTHS pass one");
    }

    private static void verifyHookFailure(Fixture fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy strategy,boolean useError,String[] expected)
            throws Exception {
        Throwable sentinel=useError?new AssertionError("net-order-ready Error sentinel"):
            new IllegalStateException("net-order-ready RuntimeException sentinel");
        NetOrderHookRecorder recorder=new NetOrderHookRecorder(fixture,expected,sentinel);
        PcbLayerRoutingPrototype.Session session=newSession(fixture,fixture.layout,strategy,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,recorder,NOOP_OBSERVER);
        recorder.owner[0]=session;
        setIntField(session,"pass",1);
        setField(session,"previousFailedNet","NC_C");
        String original=fixture.layout.geometryFingerprint();
        boolean exact=false;
        int calls=0;
        try {
            while(calls++<10000) {
                PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(1);
                if(progress!=PcbLayerRoutingPrototype.OrderingProgress.WORKING)
                    throw new AssertionError("P07 stream ended before the throwing net-order callback");
                Object search=fieldValue(session,"search");
                if(search!=null && fieldValue(search,"nets")!=null)
                    throw new AssertionError("complete net vector installed without throwing callback");
            }
        } catch(RuntimeException failure) {
            exact=failure==sentinel;
        } catch(Error failure) {
            exact=failure==sentinel;
        }
        require(exact && recorder.callbacks==1 && recorder.observedOrder!=null,
            "hook RuntimeException/Error propagates as the exact original throwable");
        require(fieldValue(session,"diagnosticFailure")==sentinel &&
            fieldValue(session,"search")==null && fieldValue(session,"candidate")==null &&
            netStrategyOf(session)==strategy && original.equals(fixture.layout.geometryFingerprint()) &&
            fixture.layout.getTraces().isEmpty() && fixture.layout.getHoles().isEmpty(),
            "hook failure latches its exact cause, releases owned Search/candidate, and preserves input");
    }

    private static void verifyWidePairMetric(Fixture fixture) throws Exception {
        PcbBoardLayout wideLayout=wideMetricLayout(fixture);
        PcbLayerRoutingPrototype.Session session=newSession(fixture,wideLayout,
            PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,NOOP_OBSERVER);
        setIntField(session,"pass",1);
        session.advanceOrderingSlice(1); // Construct the real bounded Search; do not route it.
        Object search=fieldValue(session,"search");
        require(search!=null,"wide-arithmetic metric fixture binds the actual P07 Search owner");
        // Metric-only use of the owner's private preparation avoids routing on an oversized
        // pad span. The normal board grid stays small and no grid-cap override is performed.
        Class<?> preparationType=findNested(search.getClass(),"NetOrderPreparation");
        Constructor<?> constructor=preparationType.getDeclaredConstructor(search.getClass());
        constructor.setAccessible(true);
        Object preparation=constructor.newInstance(search);
        Method advance=preparationType.getDeclaredMethod("advanceOne");
        advance.setAccessible(true);
        int work=0;
        while(!((Boolean)advance.invoke(preparation)).booleanValue()) {
            if(++work>2000) throw new AssertionError("wide metric preparation exceeded its small fixture guard");
        }
        Vector<?> metrics=(Vector<?>)fieldValue(preparation,"metrics");
        verifyActualMetrics(metrics,oracleMetrics(fixture.board,wideLayout));
        Object wideMetric=findActualMetric(metrics,"WIDE");
        Field distance=wideMetric.getClass().getDeclaredField("minimumDistance");
        distance.setAccessible(true);
        require(distance.getType()==Long.TYPE && distance.getLong(wideMetric)==399999960L,
            "actual pair preparation retains a near-limit legal-coordinate Manhattan distance as long");
    }

    private static void verifyDetachedCopyAndCancellation(Fixture fixture) throws Exception {
        String original=fixture.layout.geometryFingerprint();
        PcbLayerRoutingPrototype.Session source=newSession(fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,NOOP_OBSERVER);
        setIntField(source,"pass",1);
        Object search=null,branch=null;
        for(int step=0;step<10000;step++) {
            PcbLayerRoutingPrototype.OrderingProgress progress=source.advanceOrderingSlice(1);
            search=fieldValue(source,"search");
            branch=search==null?null:fieldValue(search,"branch");
            if(branch!=null) break;
            if(progress!=PcbLayerRoutingPrototype.OrderingProgress.WORKING)
                throw new AssertionError("literal treatment ended before the first copyable branch");
        }
        require(search!=null && branch!=null,
            "literal treatment reaches its actual first branch after complete metric preparation");
        PcbLayerRoutingPrototype.DetachedBranchCopyTask copy=
            PcbLayerRoutingPrototype.beginDetachedBranchCopy(source,
                new SeededPcbLayoutGenerator.AttemptObserver() {
                    public void check(int attempt) { }
                },0);
        int copyCalls=0;
        while(!copy.isComplete()) {
            copy.advance(32768);
            if(++copyCalls>10000)
                throw new AssertionError("small detached net-order copy exceeded its bounded guard");
        }
        PcbLayerRoutingPrototype.DetachedBranchReplay replay=copy.result();
        Object copiedOwner=fieldValue(replay,"owner");
        Object copiedSearch=fieldValue(replay,"search");
        require(netStrategyOf(source)==PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR &&
            netStrategyOf(copiedOwner)==PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,
            "detached owner copy preserves the immutable treatment strategy");
        requireOrder((Vector<?>)fieldValue(copiedSearch,"nets"),new String[]{"A_LONG_ROOT","A_TIE",
            "B_TIE","D_NEXT","WIDE","NC_A","NC_B","NC_C","NC_D"},
            "detached Search copy retains the complete already-installed treatment vector");
        replay.cancel(); copy.cancel();
        require(fieldValue(replay,"owner")==null &&
            netStrategyOf(source)==PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR &&
            original.equals(fixture.layout.geometryFingerprint()) && fixture.layout.getTraces().isEmpty() &&
            fixture.layout.getHoles().isEmpty(),
            "detached cancellation releases only its copy and leaves source strategy/input unchanged");
    }

    private static void verifyPreparationErrorKeepsStrategy(Fixture fixture) throws Exception {
        ArmedObserver observer=new ArmedObserver();
        PcbLayerRoutingPrototype.Session session=newSession(fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST,observer);
        setIntField(session,"pass",1);
        session.advanceOrderingSlice(1); // Search construction.
        session.advanceOrderingSlice(1); // Begin net-pair preparation.
        Object search=fieldValue(session,"search");
        require(search!=null && fieldValue(search,"netOrderPreparation")!=null &&
            fieldValue(search,"nets")==null,
            "observer error canary is armed before atomic net-vector installation");
        String original=fixture.layout.geometryFingerprint();
        RuntimeException sentinel=new IllegalStateException("net-order preparation observer sentinel");
        observer.failure=sentinel; observer.armed=true;
        boolean exact=false;
        try { session.advanceOrderingSlice(1); }
        catch(RuntimeException failure) { exact=failure==sentinel; }
        require(exact,"preparation-boundary observer failure propagates without reclassification");
        require(fieldValue(session,"search")==null &&
            netStrategyOf(session)==PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR &&
            original.equals(fixture.layout.geometryFingerprint()) && fixture.layout.getTraces().isEmpty() &&
            fixture.layout.getHoles().isEmpty(),
            "failure cleanup preserves immutable treatment identity and supplied placement");
    }

    private static OrderCapture captureDefaultOrder(Fixture fixture,int pass) throws Exception {
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.beginOrderingCandidates(
            fixture.board,fixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,NOOP_OBSERVER);
        return captureExistingOrder(session,fixture,pass,null,false);
    }

    private static OrderCapture captureOrder(Fixture fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy netStrategy,int pass,String failedNet,
            PcbLayerRoutingPrototype.BranchOrderStrategy branchStrategy,
            SeededPcbLayoutGenerator.AttemptObserver observer,boolean requirePreparedMetrics)
            throws Exception {
        PcbLayerRoutingPrototype.Session session=newSession(fixture,netStrategy,branchStrategy,observer);
        return captureExistingOrder(session,fixture,pass,failedNet,requirePreparedMetrics);
    }

    private static OrderCapture captureExistingOrder(PcbLayerRoutingPrototype.Session session,
            Fixture fixture,int pass,String failedNet,boolean requirePreparedMetrics) throws Exception {
        setIntField(session,"pass",pass);
        if(failedNet!=null) setField(session,"previousFailedNet",failedNet);
        boolean treatmentActive=netStrategyOf(session)==
            PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR && pass==1 &&
            fixture.board.getPlacementConstraints().getTraceWidthProfile()==PcbTraceWidthProfile.ROLE_WIDTHS;
        require(treatmentActive==requirePreparedMetrics,
            "fixture expects bounded metric preparation only for the private ROLE_WIDTHS pass-one treatment");
        Vector<OracleMetric> expectedMetrics=oracleMetrics(fixture.board,fixture.layout);
        int expectedPairs=countUnorderedPadPairs(fixture.board),calls=0,lastStage=-1;
        boolean completeMetricsChecked=false;
        while(calls<10000) {
            PcbLayerRoutingPrototype.OrderingProgress progress=session.advanceOrderingSlice(1);
            calls++;
            require(session.lastWork()==1,
                "one-unit P07 net-order slices charge exactly one owner work unit");
            Object search=fieldValue(session,"search");
            if(search!=null) {
                Vector<?> installed=(Vector<?>)fieldValue(search,"nets");
                Object preparation=fieldValue(search,"netOrderPreparation");
                require(preparation==null || treatmentActive,
                    "pass/profile/default guards never create an inapplicable net-order preparation");
                if(installed!=null) {
                    require(preparation==null,
                        "Search publishes no live preparation cursor beside its complete net vector");
                    requireOrder(installed,expectedTreatmentOrCanonical(fixture,expectedMetrics,
                        requirePreparedMetrics,failedNet,pass),
                        "Search installs one complete independent expected net vector");
                    if(requirePreparedMetrics)
                        require(completeMetricsChecked,
                            "all independently recomputed pair metrics were visible before vector install");
                    int charged=calls;
                    if(requirePreparedMetrics)
                        require(charged>=expectedPairs+fixture.board.getNetIds().size(),
                            "pair scan and output materialization remain charged across work slices");
                    return new OrderCapture(copyStringVector(installed),charged,completeMetricsChecked);
                }
                if(preparation!=null) {
                    int stage=intField(preparation,"stage");
                    require(stage>=0 && stage<=3 && stage>=lastStage,
                        "bounded net preparation advances through monotone private stages");
                    lastStage=stage;
                    Vector<?> metrics=(Vector<?>)fieldValue(preparation,"metrics");
                    Vector<?> partialOutput=(Vector<?>)fieldValue(preparation,"preparedNetOrder");
                    require(partialOutput.size()<=fixture.board.getNetIds().size(),
                        "private materialization cursor remains within the authoritative net census");
                    if(metrics.size()==fixture.board.getNetIds().size()) {
                        verifyActualMetrics(metricCensusIncludingHeldSortEntry(preparation,metrics),
                            expectedMetrics);
                        completeMetricsChecked=true;
                    }
                } else {
                    require(calls==1,
                        "only Search construction precedes the first charged preparation step");
                }
            }
            if(progress!=PcbLayerRoutingPrototype.OrderingProgress.WORKING)
                throw new AssertionError("P07 ordering ended before the complete net vector was installed");
        }
        throw new AssertionError("literal P07 net-order preparation exceeded its bounded step guard");
    }

    private static String[] expectedTreatmentOrCanonical(Fixture fixture,Vector<OracleMetric> metrics,
            boolean treatment,String failedNet,int pass) {
        if(treatment && pass==1 && fixture.board.getPlacementConstraints().getTraceWidthProfile()==
                PcbTraceWidthProfile.ROLE_WIDTHS)
            return expectedTreatment(metrics,failedNet);
        if(pass==1) {
            String[] canonical=expectedCanonicalPassOne(fixture.board);
            if(fixture.board.getPlacementConstraints().getTraceWidthProfile()==PcbTraceWidthProfile.ROLE_WIDTHS) {
                Vector<String> promoted=new Vector<String>();
                for(String netId:canonical) promoted.add(netId);
                promote(promoted,failedNet);
                return promoted.toArray(new String[0]);
            }
            return canonical;
        }
        Vector<String> values=sortedNetIds(fixture.board);
        if(pass==0) return values.toArray(new String[0]);
        if(pass==2) {
            Collections.reverse(values);
            return values.toArray(new String[0]);
        }
        throw new AssertionError("unexpected P07 ordering pass "+pass);
    }

    private static PcbLayerRoutingPrototype.Session newSession(Fixture fixture,
            PcbLayerRoutingPrototype.NetOrderStrategy netStrategy,
            PcbLayerRoutingPrototype.BranchOrderStrategy branchStrategy,
            SeededPcbLayoutGenerator.AttemptObserver observer) {
        return newSession(fixture,fixture.layout,netStrategy,branchStrategy,observer);
    }

    private static PcbLayerRoutingPrototype.Session newSession(Fixture fixture,PcbBoardLayout layout,
            PcbLayerRoutingPrototype.NetOrderStrategy netStrategy,
            PcbLayerRoutingPrototype.BranchOrderStrategy branchStrategy,
            SeededPcbLayoutGenerator.AttemptObserver observer) {
        return newSession(fixture,layout,netStrategy,branchStrategy,null,observer);
    }

    private static PcbLayerRoutingPrototype.Session newSession(Fixture fixture,PcbBoardLayout layout,
            PcbLayerRoutingPrototype.NetOrderStrategy netStrategy,
            PcbLayerRoutingPrototype.BranchOrderStrategy branchStrategy,
            PcbLayerRoutingPrototype.Diagnostics diagnostics,
            SeededPcbLayoutGenerator.AttemptObserver observer) {
        return PcbLayerRoutingPrototype.beginOrderingCandidates(fixture.board,layout,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,observer,
            PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias,diagnostics,branchStrategy,netStrategy);
    }

    private static Fixture fixture(PcbTraceWidthProfile profile) {
        return fixture(profile,false);
    }

    private static Fixture fixture(PcbTraceWidthProfile profile,boolean mediumTwoLayer) {
        TroubleshootBoard board=new TroubleshootBoard("P07_NET_ORDER_LITERAL");
        String[] nets={"A_LONG_ROOT","A_TIE","B_TIE","D_NEXT","WIDE",
            "NC_A","NC_B","NC_C","NC_D"};
        for(String net:nets) board.addNet(new BoardNet(net,BoardNet.RoutingRole.SIGNAL));
        PcbBoardLayout layout=new PcbBoardLayout(1100,650,new Rectangle(100,100,600,400),
            new Rectangle(820,120,200,400));
        // A_LONG_ROOT's lexical root is far from both other pads; B/C are the 40-unit minimum.
        addPoint(board,layout,"A","A_LONG_ROOT",110,110);
        addPoint(board,layout,"B","A_LONG_ROOT",480,390);
        addPoint(board,layout,"C","A_LONG_ROOT",520,390);
        // D_NEXT has four pads but a larger 90-unit minimum, so count order differs from treatment.
        addPoint(board,layout,"D1","D_NEXT",110,200);
        addPoint(board,layout,"D2","D_NEXT",200,200);
        addPoint(board,layout,"D3","D_NEXT",290,200);
        addPoint(board,layout,"D4","D_NEXT",380,200);
        // Both ties have two equal shortest witnesses; the oracle chooses E/F and H/I lexically.
        addPoint(board,layout,"E","A_TIE",130,290);
        addPoint(board,layout,"F","A_TIE",170,290);
        addPoint(board,layout,"G","A_TIE",130,330);
        addPoint(board,layout,"H","B_TIE",230,290);
        addPoint(board,layout,"I","B_TIE",270,290);
        addPoint(board,layout,"J","B_TIE",230,330);
        addPoint(board,layout,"W1","WIDE",500,110);
        addPoint(board,layout,"W2","WIDE",500,360);
        addPoint(board,layout,"NA","NC_A",590,120);
        addPoint(board,layout,"NB","NC_B",590,190);
        addPoint(board,layout,"NC","NC_C",590,260);
        addPoint(board,layout,"ND","NC_D",590,330);
        Vector<PcbPlacementConstraints.Part> parts=new Vector<PcbPlacementConstraints.Part>();
        Vector<String> componentIds=board.getComponentIds();
        Collections.sort(componentIds);
        for(String componentId:componentIds)
            parts.add(new PcbPlacementConstraints.Part(componentId,"net-order","Net order",
                "literal",PcbPlacementConstraints.Anchor.NONE,20));
        board.setPlacementConstraints(new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.TOP,
            mediumTwoLayer?MediumBoardPhysicalPolicy.ID:PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
            mediumTwoLayer?MediumBoardPhysicalPolicy.VERSION:PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION,
            mediumTwoLayer?14:1,mediumTwoLayer,profile));
        board.validate(); layout.validateAgainst(board);
        return new Fixture(board,layout);
    }

    private static void addPoint(TroubleshootBoard board,PcbBoardLayout layout,
            String id,String net,int x,int y) {
        PhysicalPackage physical=P06FactoryLinkFixtures.TEST_POINT;
        BoardComponent component=new BoardComponent(id,physical.getId(),physical,id);
        board.addComponent(component);
        board.addPad(new BoardPad(id+".1",id,"1",net));
        PcbPackagePose reference=new PcbPackagePose(100,100,PcbRotation.DEG_0,PcbBoardSide.BOTTOM);
        Point local=physical.getGeometry().placedAt(reference).getPadPoint(0);
        PcbPackagePose pose=new PcbPackagePose(100+x-local.x,100+y-local.y,
            PcbRotation.DEG_0,PcbBoardSide.BOTTOM);
        PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,pose,physical.getGeometry());
        layout.addComponent(footprint.getPlacement());
        for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
    }

    private static PcbBoardLayout wideMetricLayout(Fixture fixture) {
        PcbBoardLayout layout=new PcbBoardLayout(1100,650,new Rectangle(100,100,600,400),
            new Rectangle(820,120,200,400));
        for(PcbComponentPlacement component:fixture.layout.getComponents()) layout.addComponent(component);
        for(String padId:fixture.board.getPadIds()) {
            PcbPadPlacement original=fixture.layout.getPad(padId);
            int x=original.getX(),y=original.getY();
            if("W1.1".equals(padId)) { x=-99999990; y=-99999990; }
            else if("W2.1".equals(padId)) { x=99999990; y=99999990; }
            layout.addPad(new PcbPadPlacement(padId,x,y,0,0,0,
                new Rectangle(x-5,y-5,10,10),new Rectangle(x-6,y-6,12,12)));
        }
        return layout;
    }

    private static Vector<OracleMetric> oracleMetrics(TroubleshootBoard board,PcbBoardLayout layout) {
        Vector<String> netIds=sortedNetIds(board);
        Vector<OracleMetric> result=new Vector<OracleMetric>();
        for(String netId:netIds) {
            Vector<String> padIds=board.getNet(netId).getPadIds();
            Collections.sort(padIds);
            if(padIds.size()<2) {
                result.add(new OracleMetric(netId,false,Long.MAX_VALUE,null,null));
                continue;
            }
            long best=Long.MAX_VALUE;
            String bestFirst=null,bestSecond=null;
            for(int i=0;i<padIds.size();i++) for(int j=i+1;j<padIds.size();j++) {
                String firstId=padIds.get(i),secondId=padIds.get(j);
                PcbPadPlacement first=layout.getPad(firstId),second=layout.getPad(secondId);
                if(first==null || second==null)
                    throw new AssertionError("independent oracle lacks placed pad "+firstId+"/"+secondId);
                long dx=Math.abs((long)first.getX()-(long)second.getX());
                long dy=Math.abs((long)first.getY()-(long)second.getY());
                long distance=dx+dy;
                if(distance<best || distance==best && pairBefore(firstId,secondId,bestFirst,bestSecond)) {
                    best=distance; bestFirst=firstId; bestSecond=secondId;
                }
            }
            result.add(new OracleMetric(netId,true,best,bestFirst,bestSecond));
        }
        return result;
    }

    private static boolean pairBefore(String first,String second,String priorFirst,String priorSecond) {
        if(priorFirst==null) return true;
        int leading=first.compareTo(priorFirst);
        return leading<0 || leading==0 && second.compareTo(priorSecond)<0;
    }

    private static long rootDistance(Fixture fixture,String firstId,String secondId) {
        PcbPadPlacement first=fixture.layout.getPad(firstId),second=fixture.layout.getPad(secondId);
        return Math.abs((long)first.getX()-(long)second.getX())+
            Math.abs((long)first.getY()-(long)second.getY());
    }

    private static Vector<String> sortedNetIds(TroubleshootBoard board) {
        Vector<String> ids=board.getNetIds();
        Collections.sort(ids);
        return ids;
    }

    private static int countUnorderedPadPairs(TroubleshootBoard board) {
        int pairs=0;
        for(String netId:board.getNetIds()) {
            int count=board.getNet(netId).getPadIds().size();
            pairs+=count*(count-1)/2;
        }
        return pairs;
    }

    private static String[] expectedTreatment(Vector<OracleMetric> metrics,String failedNet) {
        Vector<OracleMetric> ordered=new Vector<OracleMetric>(metrics);
        Collections.sort(ordered,new Comparator<OracleMetric>() {
            public int compare(OracleMetric first,OracleMetric second) {
                if(first.paired!=second.paired) return first.paired?-1:1;
                if(first.paired && first.minimumDistance!=second.minimumDistance)
                    return first.minimumDistance<second.minimumDistance?-1:1;
                return first.netId.compareTo(second.netId);
            }
        });
        Vector<String> ids=new Vector<String>();
        for(OracleMetric metric:ordered) ids.add(metric.netId);
        promote(ids,failedNet);
        return ids.toArray(new String[0]);
    }

    private static String[] expectedCanonicalPassOne(final TroubleshootBoard board) {
        Vector<String> ids=board.getNetIds();
        Collections.sort(ids,new Comparator<String>() {
            public int compare(String first,String second) {
                int a=board.getNet(first).getPadIds().size();
                int b=board.getNet(second).getPadIds().size();
                return a==b?first.compareTo(second):(a>b?-1:1);
            }
        });
        return ids.toArray(new String[0]);
    }

    private static void promote(Vector<String> ids,String failedNet) {
        if(failedNet==null) return;
        int index=ids.indexOf(failedNet);
        if(index<0) throw new AssertionError("literal promotion net is absent from complete oracle");
        if(index>0) ids.insertElementAt(ids.remove(index),0);
    }

    private static Vector<Object> metricCensusIncludingHeldSortEntry(Object preparation,
            Vector<?> metrics) throws Exception {
        Object held=fieldValue(preparation,"sortValue");
        int hole=-1;
        if(held!=null) {
            int stage=intField(preparation,"stage");
            int cursor=intField(preparation,"sortCursor");
            int index=intField(preparation,"sortIndex");
            hole=cursor+1;
            require(stage==1 && hole>0 && hole<=index && index<metrics.size(),
                "an in-progress insertion sort has one bounded hole and one held metric");
        }
        Vector<Object> logicalCensus=new Vector<Object>();
        for(int i=0;i<metrics.size();i++) if(i!=hole) logicalCensus.add(metrics.get(i));
        if(held!=null) logicalCensus.add(held);
        require(logicalCensus.size()==metrics.size(),
            "held-entry accounting preserves the full prepared metric census at every sort step");
        return logicalCensus;
    }

    private static void verifyActualMetrics(Vector<?> actual,Vector<OracleMetric> expected) throws Exception {
        require(actual.size()==expected.size(),
            "production metric preparation retains every board net exactly once");
        Map<String,OracleMetric> byId=new HashMap<String,OracleMetric>();
        for(OracleMetric metric:expected) byId.put(metric.netId,metric);
        Map<String,Boolean> seen=new HashMap<String,Boolean>();
        for(Object item:actual) {
            String id=(String)fieldValue(item,"netId");
            OracleMetric oracle=byId.get(id);
            require(oracle!=null && !seen.containsKey(id),
                "production metric census contains one authoritative net ID: "+id);
            boolean paired=((Boolean)fieldValue(item,"paired")).booleanValue();
            long distance=((Long)fieldValue(item,"minimumDistance")).longValue();
            require(paired==oracle.paired && distance==oracle.minimumDistance,
                "production cached metric agrees with independent pad-pair arithmetic for "+id+
                    " expected="+oracle.minimumDistance+" actual="+distance);
            Field distanceField=item.getClass().getDeclaredField("minimumDistance");
            require(distanceField.getType()==Long.TYPE && Modifier.isFinal(distanceField.getModifiers()),
                "cached pair distance is an immutable long for "+id);
            seen.put(id,Boolean.TRUE);
        }
        require(seen.size()==expected.size(),
            "all independent expected net metrics have one production counterpart");
    }

    private static Object findActualMetric(Vector<?> metrics,String netId) {
        for(Object metric:metrics)
            if(netId.equals(fieldValue(metric,"netId"))) return metric;
        throw new AssertionError("production pair metric absent for "+netId);
    }

    private static OracleMetric find(Vector<OracleMetric> metrics,String netId) {
        for(OracleMetric metric:metrics) if(netId.equals(metric.netId)) return metric;
        throw new AssertionError("independent literal metric absent for "+netId);
    }

    private static Class<?> findNested(Class<?> owner,String name) {
        for(Class<?> nested:owner.getDeclaredClasses())
            if(name.equals(nested.getSimpleName())) return nested;
        throw new AssertionError("private preparation class is absent: "+name);
    }

    private static PcbLayerRoutingPrototype.NetOrderStrategy netStrategyOf(Object owner)
            throws Exception {
        Object value=fieldValue(owner,"netOrderStrategy");
        if(!(value instanceof PcbLayerRoutingPrototype.NetOrderStrategy))
            throw new AssertionError("net-order strategy is absent or has an unexpected owner value");
        return (PcbLayerRoutingPrototype.NetOrderStrategy)value;
    }

    private static int intField(Object owner,String name) {
        Object value=fieldValue(owner,name);
        if(!(value instanceof Integer)) throw new AssertionError("expected integer field "+name);
        return ((Integer)value).intValue();
    }

    private static void setIntField(Object owner,String name,int value) throws Exception {
        Field field=findField(owner.getClass(),name);
        field.setAccessible(true); field.setInt(owner,value);
    }

    private static void setField(Object owner,String name,Object value) throws Exception {
        Field field=findField(owner.getClass(),name);
        field.setAccessible(true); field.set(owner,value);
    }

    private static Object fieldValue(Object owner,String name) {
        if(owner==null) throw new AssertionError("cannot inspect null owner field "+name);
        try {
            Field field=findField(owner.getClass(),name);
            field.setAccessible(true);
            return field.get(owner);
        } catch(ReflectiveOperationException failure) {
            throw new AssertionError("cannot read owner field "+name,failure);
        }
    }

    private static Field findField(Class<?> owner,String name) throws NoSuchFieldException {
        Class<?> type=owner;
        while(type!=null) {
            try { return type.getDeclaredField(name); }
            catch(NoSuchFieldException missing) { type=type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static void requireOrder(Vector<?> actual,String[] expected,String message) {
        boolean same=actual.size()==expected.length;
        for(int i=0;same && i<expected.length;i++) same=expected[i].equals(actual.get(i));
        require(same,message+" expected="+join(expected)+" actual="+actual);
    }

    private static void requireOrder(String[] actual,String[] expected,String message) {
        boolean same=actual.length==expected.length;
        for(int i=0;same && i<expected.length;i++) same=expected[i].equals(actual[i]);
        require(same,message+" expected="+join(expected)+" actual="+join(actual));
    }

    private static String join(String[] values) {
        StringBuilder result=new StringBuilder("[");
        for(int i=0;i<values.length;i++) {
            if(i>0) result.append(',');
            result.append(values[i]);
        }
        return result.append(']').toString();
    }

    private static Vector<String> copyStringVector(Vector<?> values) {
        Vector<String> result=new Vector<String>();
        for(Object value:values) {
            if(!(value instanceof String))
                throw new AssertionError("actual P07 net order contains a non-string identity");
            result.add((String)value);
        }
        return result;
    }

    private static void require(boolean condition,String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
}
