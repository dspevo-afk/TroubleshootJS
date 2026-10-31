package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Exact P07 Search admission and required-policy candidate filtering contracts. */
public final class P07GridEligibilityContractTest {
    private static int checks;

    private P07GridEligibilityContractTest() { }

    public static void main(String[] args) {
        exactGridBoundary();
        actualBackendBoundary();
        candidateFilteringAndSkipReceipt();
        noEligibleCandidateRejection();
        optionalRoutePathPreservation();
        selectorsUseEligibleCandidates();
        System.out.println("PASS: P07 grid eligibility contracts assertions=" + checks);
    }

    private static void exactGridBoundary() {
        PcbLayerRoutingPrototype.GridAdmission atLimit =
            PcbLayerRoutingPrototype.gridAdmission(new Rectangle(20,20,5010,5010));
        require(atLimit.width==500 && atLimit.height==500 && atLimit.cells==250000L &&
            atLimit.eligible && atLimit.reason==null,
            "500x500 cells remain admitted at the exact 250,000-cell P07 boundary");

        PcbLayerRoutingPrototype.GridAdmission overLimit =
            PcbLayerRoutingPrototype.gridAdmission(new Rectangle(20,20,540,47180));
        require(overLimit.width==53 && overLimit.height==4717 &&
            overLimit.cells==250001L && !overLimit.eligible &&
            "GRID_LIMIT".equals(overLimit.reason),
            "53x4717 cells are rejected one cell above the P07 grid boundary");
    }

    private static void actualBackendBoundary() {
        P06FactoryLinkFixtures.Fixture positiveFixture =
            new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        PcbBoardLayout positive = rehome(positiveFixture.layout,
            new Rectangle(20,20,5010,5010));
        String positiveInput = positive.geometryFingerprint();
        PcbLayerRoutingPrototype.Result routed = route(positiveFixture.board,positive);
        require(routed.accepted() && routed.expansions<=PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            "actual P07 Search routes an all-PTH fixture at exactly 250,000 cells");
        routed.layout.validateRoutingGeometry(positiveFixture.board);
        new PcbTwoLayerRules(positiveFixture.board,routed.layout).validate(routed.layout);
        require(positive.geometryFingerprint().equals(positiveInput),
            "positive boundary route leaves the source placement unchanged");

        P06FactoryLinkFixtures.Fixture negativeFixture =
            new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        PcbBoardLayout over = rehome(negativeFixture.layout,
            new Rectangle(20,20,540,47180));
        String negativeInput = over.geometryFingerprint();
        PcbLayerRoutingPrototype.Result rejected = route(negativeFixture.board,over);
        require(!rejected.accepted() && "GRID_LIMIT".equals(rejected.outcome) &&
            rejected.expansions==0 && rejected.orderings==PcbLayerRoutingPrototype.ORDERINGS,
            "actual P07 Search returns GRID_LIMIT with no route expansions above 250,000 cells");
        require(over.geometryFingerprint().equals(negativeInput),
            "negative boundary route leaves the source placement unchanged");
    }

    private static PcbLayerRoutingPrototype.Result route(TroubleshootBoard board,
            PcbBoardLayout placement) {
        PcbLayerRoutingPrototype.Session session = PcbLayerRoutingPrototype.begin(board,
            placement,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER);
        int slices=0;
        while(!session.advanceSlice()) {
            if(++slices>1000) throw new AssertionError("P07 boundary fixture exceeded its bounded slice guard");
        }
        return session.result();
    }

    private static PcbBoardLayout rehome(PcbBoardLayout source, Rectangle outline) {
        Rectangle tray = new Rectangle(outline.x+outline.width+40,40,180,300);
        PcbBoardLayout result = new PcbBoardLayout(outline.x+outline.width+250,
            outline.y+outline.height+60,outline,tray);
        for(PcbComponentPlacement component:source.getComponents()) result.addComponent(component);
        for(PcbPadPlacement pad:source.getPads()) result.addPad(pad);
        for(PcbSilkscreenLabel label:source.getSilkscreenLabels()) result.addSilkscreenLabel(label);
        for(PcbLayoutRegion region:source.getRegions()) result.addRegion(region);
        return result;
    }

    private static void candidateFilteringAndSkipReceipt() {
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> ordered = new Vector<MediumBoardPhysicalPolicy.PlacementCandidate>();
        int[] attempts={2,0,4,1,5,3};
        for(int attempt:attempts)
            ordered.add(candidate(attempt,attempt==4 || attempt==5 ?
                new Rectangle(20,20,540,47180) : new Rectangle(20,20,5010,5010)));

        SeededPcbLayoutGenerator.P07GridEligibilitySelection selection =
            SeededPcbLayoutGenerator.filterRequiredP07GridCandidates(ordered);
        require(attempts(selection.eligibleCandidates).equals(vector(2,0,1,3)),
            "grid filtering preserves the existing post-priority relative order");
        require(selection.routeEligibilitySkips.size()==2 &&
            selection.routeEligibilitySkips.get(0).equals(
                "attempt=4;width=53;height=4717;cells=250001;reason=GRID_LIMIT") &&
            selection.routeEligibilitySkips.get(1).equals(
                "attempt=5;width=53;height=4717;cells=250001;reason=GRID_LIMIT"),
            "every grid-ineligible placement has one canonical skip record");
        require(ordered.size()==6 && ordered.get(2).attempt==4 && ordered.get(4).attempt==5,
            "preflight leaves the full ranked/prioritized candidate vector intact");

        Vector<Integer> eligibleAttempts=attempts(selection.eligibleCandidates);
        Vector<String> outcomes=vectorStrings("P07_FULLER_TWO_LAYER@2=REJECTED:SEARCH_LIMIT");
        Vector<String> skips=new Vector<String>(selection.routeEligibilitySkips);
        MediumBoardPhysicalPolicy.Statistics statistics = new MediumBoardPhysicalPolicy.Statistics(
            "REJECTED",MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER,6,0,1,0,0,1,0,-1,
            vector(2,0,4,1,5,3),vectorStrings("score0","score1","score2","score3","score4","score5"),
            outcomes,0L,1,100,-1,-1,null,eligibleAttempts,skips);
        eligibleAttempts.clear(); skips.clear(); outcomes.clear();
        require(statistics.routeEligiblePlacementAttempts.equals(vector(2,0,1,3)) &&
            statistics.routeEligibilitySkips.size()==2 && statistics.routeOutcomes.size()==1 &&
            statistics.toCanonical().contains(";routeEligiblePlacementAttempts=[2, 0, 1, 3]") &&
            statistics.toCanonical().contains(";routeEligibilitySkips=[attempt=4;width=53;height=4717;cells=250001;reason=GRID_LIMIT, attempt=5;width=53;height=4717;cells=250001;reason=GRID_LIMIT]"),
            "copied stats keep eligibility order/skips separate from actual route outcomes");
    }

    private static void noEligibleCandidateRejection() {
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> oversized =
            new Vector<MediumBoardPhysicalPolicy.PlacementCandidate>();
        for(int attempt=0;attempt<6;attempt++)
            oversized.add(candidate(attempt,new Rectangle(20,20,540,47180)));
        SeededPcbLayoutGenerator.P07GridEligibilitySelection selection =
            SeededPcbLayoutGenerator.filterRequiredP07GridCandidates(oversized);
        PcbRoutingRejectedException failure =
            SeededPcbLayoutGenerator.requiredP07GridRejection(selection,5,77L);
        require(selection.eligibleCandidates.isEmpty() && selection.routeEligibilitySkips.size()==6 &&
            failure.getKind()==PcbRoutingRejectedException.Kind.ROUTING &&
            !failure.isExhausted() && failure.getMessage().contains("GRID_LIMIT:noEligibleCandidates"),
            "an empty eligible set yields a normal typed routing rejection with skip evidence");
    }

    private static void optionalRoutePathPreservation() {
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> ranked =
            new Vector<MediumBoardPhysicalPolicy.PlacementCandidate>();
        ranked.add(candidate(0,new Rectangle(20,20,540,47180)));
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> empty =
            new Vector<MediumBoardPhysicalPolicy.PlacementCandidate>();
        require(SeededPcbLayoutGenerator.mediumRouteCandidates(ranked,empty,false,false)==ranked &&
            SeededPcbLayoutGenerator.mediumRouteCandidates(ranked,empty,true,false)==ranked,
            "optional P05 and optional P07 continue to use the unfiltered incumbent vector");
        require(SeededPcbLayoutGenerator.mediumRouteCandidates(ranked,empty,true,true)==empty,
            "only required P07 consumes its separate preflighted vector");
        MediumBoardPhysicalPolicy.Statistics optionalStatistics =
            new MediumBoardPhysicalPolicy.Statistics("REJECTED",
                MediumBoardPhysicalPolicy.P05_ONE_FACE,1,0,0,0,0,0,0,-1,
                vector(0),vectorStrings());
        require(optionalStatistics.routeEligiblePlacementAttempts.isEmpty() &&
            optionalStatistics.routeEligibilitySkips.isEmpty() &&
            optionalStatistics.toCanonical().contains(";routeEligiblePlacementAttempts=[]") &&
            optionalStatistics.toCanonical().contains(";routeEligibilitySkips=[]"),
            "optional route telemetry keeps the required-P07 eligibility receipts empty");
    }

    private static void selectorsUseEligibleCandidates() {
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> guarded=eligibleCandidates(4,2,5,1,0,3);
        int guardIndex=SeededPcbLayoutGenerator.guardedRouteCandidateIndex(guarded,2,1);
        require(guarded.get(guardIndex).attempt==1 && isEligible(guarded.get(guardIndex)),
            "guarded route fallback selects only a grid-eligible vector member");

        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> narrow=eligibleCandidates(4,2,5,0,1,3);
        int narrowIndex=SeededPcbLayoutGenerator.narrowRouteCandidateIndex(
            narrow,1,1,1,false,true);
        require(narrow.get(narrowIndex).attempt==3 && isEligible(narrow.get(narrowIndex)),
            "narrow selector chooses an eligible member from the supplied route vector");

        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> support=eligibleCandidates(4,2,5,0,3,1);
        int supportIndex=SeededPcbLayoutGenerator.supportRouteCandidateIndex(
            support,2,2,1,true,true);
        require(support.get(supportIndex).attempt==1 && isEligible(support.get(supportIndex)),
            "support selector chooses an eligible member from the supplied route vector");
    }

    private static Vector<MediumBoardPhysicalPolicy.PlacementCandidate> eligibleCandidates(int... ids) {
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> result =
            new Vector<MediumBoardPhysicalPolicy.PlacementCandidate>();
        for(int id:ids) result.add(candidate(id,new Rectangle(20,20,5010,5010)));
        return result;
    }

    private static MediumBoardPhysicalPolicy.PlacementCandidate candidate(int attempt,
            Rectangle outline) {
        return new MediumBoardPhysicalPolicy.PlacementCandidate(attempt,
            new PcbPlacementPlanner.Plan(outline,new Vector<PcbFootprint>(),
                new Vector<PcbLayoutRegion>(),0L,0L,attempt,0));
    }

    private static boolean isEligible(MediumBoardPhysicalPolicy.PlacementCandidate candidate) {
        return PcbLayerRoutingPrototype.gridAdmission(candidate.plan.outline).eligible;
    }

    private static Vector<Integer> attempts(
            Vector<MediumBoardPhysicalPolicy.PlacementCandidate> candidates) {
        Vector<Integer> result=new Vector<Integer>();
        for(MediumBoardPhysicalPolicy.PlacementCandidate candidate:candidates)
            result.add(Integer.valueOf(candidate.attempt));
        return result;
    }

    private static Vector<Integer> vector(int... values) {
        Vector<Integer> result=new Vector<Integer>();
        for(int value:values) result.add(Integer.valueOf(value));
        return result;
    }

    private static Vector<String> vectorStrings(String... values) {
        Vector<String> result=new Vector<String>();
        for(String value:values) result.add(value);
        return result;
    }

    private static void require(boolean ok,String message) {
        checks++;
        if(!ok) throw new AssertionError(message);
    }
}
