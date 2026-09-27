package com.lushprojects.circuitjs1.client;

public final class PcbLayerRoutingResumptionContractTest {
    private static int checks;

    private PcbLayerRoutingResumptionContractTest() { }

    public static void main(String[] args) {
        slicedRouteMatchesSynchronousDrain();
        boundedExhaustionMatchesSynchronousDrain();
        generationCheckpointCancellationAborts();
        System.out.println("PASS: Pcb layer routing resumption contracts assertions=" + checks);
    }

    private static void slicedRouteMatchesSynchronousDrain() {
        P06FactoryLinkFixtures.Fixture slicedFixture =
            new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        P06FactoryLinkFixtures.Fixture synchronousFixture =
            new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        String original=slicedFixture.layout.geometryFingerprint();
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(
            slicedFixture.board,slicedFixture.layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER);
        int slices=0;
        boolean complete=false;
        while(!complete) {
            int before=session.expansions();
            complete=session.advanceSlice(1);
            require(session.expansions()-before<=1,
                "one-operation resumption slice cannot exceed one route expansion");
            slices++;
            require(slices<2000000,"resumable route makes bounded progress");
        }
        PcbLayerRoutingPrototype.Result sliced=session.result();
        PcbLayerRoutingPrototype.Result synchronous=PcbLayerRoutingPrototype.route(
            synchronousFixture.board,synchronousFixture.layout,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER);
        require(slices>1,"small-work caller genuinely resumes the route session");
        require(slicedFixture.layout.geometryFingerprint().equals(original),
            "sliced routing never mutates its source placement");
        require(synchronousFixture.layout.geometryFingerprint().equals(original),
            "synchronous wrapper never mutates its source placement");
        require(sliced.outcome.equals(synchronous.outcome) &&
            sliced.accepted()==synchronous.accepted() && sliced.expansions==synchronous.expansions &&
            sliced.orderings==synchronous.orderings && sliced.vias==synchronous.vias &&
            sliced.links==synchronous.links,
            "sliced route and same-session synchronous drain preserve result accounting");
        require(sliced.accepted(),"resumable two-layer search finds the fixture route");
        if(sliced.accepted())
            require(sliced.layout.geometryFingerprint().equals(synchronous.layout.geometryFingerprint()),
                "sliced route preserves trace, via, and identity output");
    }

    private static void boundedExhaustionMatchesSynchronousDrain() {
        P06FactoryLinkFixtures.Fixture slicedFixture =
            new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        P06FactoryLinkFixtures.Fixture synchronousFixture =
            new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(
            slicedFixture.board,slicedFixture.layout,PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,1);
        while(!session.advanceSlice(1)) { }
        PcbLayerRoutingPrototype.Result sliced=session.result();
        PcbLayerRoutingPrototype.Result synchronous=PcbLayerRoutingPrototype.route(
            synchronousFixture.board,synchronousFixture.layout,
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER,1);
        require(!sliced.accepted() && sliced.outcome.endsWith("TOTAL_SEARCH_LIMIT") &&
            sliced.expansions==1,"bounded search exhaustion is a normal rejected route result");
        require(sliced.outcome.equals(synchronous.outcome) &&
            sliced.expansions==synchronous.expansions && sliced.orderings==synchronous.orderings,
            "bounded exhaustion matches the synchronous wrapper");
        require(slicedFixture.layout.getTraces().isEmpty() && slicedFixture.layout.getHoles().isEmpty() &&
            synchronousFixture.layout.getTraces().isEmpty() && synchronousFixture.layout.getHoles().isEmpty(),
            "rejected route attempts publish no copper or vias to their source placements");
    }

    private static void generationCheckpointCancellationAborts() {
        final P06FactoryLinkFixtures.Fixture fixture=
            new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        final String original=fixture.layout.geometryFingerprint();
        final PcbLayerRoutingPrototype.Session[] route={null};
        final boolean[] cancelled={false};
        final int[] aborts={0},publications={0};
        GenerationJob.Services services=new GenerationJob.Services() {
            public int candidateCount() { return 1; }
            public String manifest(int candidate) { return "p07-resumption-cancel"; }
            public void beginCandidate(int index) { }
            public String resolve() { return "resolved"; }
            public String healthy() {
                if(route[0]==null)
                    route[0]=PcbLayerRoutingPrototype.begin(fixture.board,fixture.layout,
                        PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
                        new SeededPcbLayoutGenerator.AttemptObserver() {
                            public void check(int attempt) { GenerationWorkScope.check(); }
                        });
                if(route[0].expansions()>0) cancelled[0]=true;
                return route[0].advanceSlice(1)?"healthy":null;
            }
            public String physical() { return "physical"; }
            public boolean proveNext() { return false; }
            public String symptom() { return "symptom"; }
            public String dependencies() { return "dependencies"; }
            public void publish(GenerationReceipt receipt) { publications[0]++; }
            public void abort() { aborts[0]++; }
            public boolean isCurrent() { return !cancelled[0]; }
            public long nowMillis() { return 0L; }
        };
        GenerationJob job=new GenerationJob(services,100000,20,5000);
        advanceTurn(job); // RESOLVE
        int turns=0;
        while(route[0]==null || route[0].expansions()==0) {
            advanceTurn(job);
            require(job.getOutcome()==GenerationJob.Outcome.RUNNING,
                "route search remains live while making its first expansion");
            require(++turns<100,"route session reaches its first expansion in bounded turns");
        }
        int expansionsBeforeCancel=route[0].expansions();
        advanceTurn(job); // The next slice checkpoints and observes stale ownership.
        require(job.getOutcome()==GenerationJob.Outcome.STALE && aborts[0]==1 && publications[0]==0,
            "observer checkpoint cancellation reaches the generation abort path");
        boolean resultUnavailable=false;
        try { route[0].result(); }
        catch(IllegalStateException expected) { resultUnavailable=true; }
        require(route[0]!=null && expansionsBeforeCancel>0 && resultUnavailable &&
            fixture.layout.geometryFingerprint().equals(original) &&
            fixture.layout.getTraces().isEmpty() && fixture.layout.getHoles().isEmpty(),
            "mid-search cancellation has no result and leaves the exact source placement unchanged");
    }

    private static void advanceTurn(GenerationJob job) {
        GenerationWorkScope.enter(job);
        try { job.advance(); }
        finally { GenerationWorkScope.exit(job); }
    }

    private static void require(boolean condition,String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
}
